package dev.mestorage.controller.test;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.config.ControllerConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Isolates file-watcher/persistence checks from energy fixtures that mutate the same config. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid=MEStorageController.ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigurationGameTests {
    private static final java.util.concurrent.atomic.AtomicLong RELOADS=new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean offServerThread;

    @SubscribeEvent
    public static void observeReload(ModConfigEvent.Reloading event) {
        if(event.getConfig().getSpec()!=ControllerConfig.SPEC) return;
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null && !server.isSameThread()) offServerThread=true;
        RELOADS.incrementAndGet();
    }

    @GameTest(template="empty",batch="me_storage_configuration",setupTicks=20,timeoutTicks=12000)
    public static void energyCommandPermissionsPersistenceAndReload(GameTestHelper helper) {
        helper.assertTrue(ControllerConfig.SPEC.isLoaded(),"Server config must be registered and loaded");
        helper.assertTrue(!ControllerConfig.BYPASS_AE_ENERGY_LIMIT.getDefault(),"Energy bypass must default to disabled");
        var config=ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER).stream()
                .filter(value->value.getModId().equals(MEStorageController.ID)
                        && value.getFileName().equals(ControllerConfig.FILE_NAME)).findFirst().orElseThrow();
        helper.assertTrue(config.getSpec()==ControllerConfig.SPEC,"Entry point must register the actual server spec");
        var server=helper.getLevel().getServer();
        var dispatcher=server.getCommands().getDispatcher();
        var admin=server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        boolean previous=ControllerConfig.bypassAeEnergyLimit();
        try {
            ControllerConfig.setBypassAeEnergyLimit(false);
            helper.assertTrue(dispatcher.execute("mestorage energyBypass",admin)==1
                            && !ControllerConfig.bypassAeEnergyLimit(),
                    "No-argument command must query without changing disabled state");
            for(int permission=0;permission<2;permission++) {
                boolean denied=false;
                try { dispatcher.execute("mestorage energyBypass true",admin.withPermission(permission)); }
                catch(CommandSyntaxException expected) { denied=true; }
                helper.assertTrue(denied && !ControllerConfig.bypassAeEnergyLimit(),
                        "Permission levels below two must not access the administrator command");
            }
            helper.assertTrue(dispatcher.execute("mestorage energyBypass true",admin)==1
                            && ControllerConfig.bypassAeEnergyLimit(),"Admin command must enable bypass immediately");
            assertSaved(helper,config,true,"after-enable-command");
            helper.assertTrue(dispatcher.execute("mestorage energyBypass",admin)==1
                            && ControllerConfig.bypassAeEnergyLimit(),"Query must also preserve enabled state");
            helper.assertTrue(dispatcher.execute("mestorage energyBypass false",admin)==1
                            && !ControllerConfig.bypassAeEnergyLimit(),"Admin command must disable bypass immediately");
            assertSaved(helper,config,false,"after-disable-command");
        } catch(CommandSyntaxException failure) {
            throw new AssertionError("Registered energy command did not accept its documented syntax",failure);
        } finally {
            ControllerConfig.setBypassAeEnergyLimit(previous);
        }
        cleanupOnCompletion(helper,()->{
            org.slf4j.LoggerFactory.getLogger(ConfigurationGameTests.class).info("ME_STORAGE_CONFIG_TEST completion: reloads={}, offServerThread={}, {}",
                    RELOADS.get(),offServerThread,diagnostic(config,"before-restore",previous,"see TOML below"));
            ControllerConfig.setBypassAeEnergyLimit(previous);
        });
        long[] baseline={RELOADS.get()}; offServerThread=false;
        long[] quietUntil={0};
        helper.startSequence().thenExecute(()->{
            ControllerConfig.setBypassAeEnergyLimit(false);
            baseline[0]=RELOADS.get();
            externalWrite(config,true);
        }).thenWaitUntil(()->{
            yieldToWatcher();
            helper.assertTrue(RELOADS.get()>baseline[0] && ControllerConfig.bypassAeEnergyLimit(),
                    "Real filesystem watcher must reload external edit and emit Reloading");
        }).thenExecute(()->{
            helper.assertTrue(!offServerThread,"Reloading event must run on the server thread");
            assertSaved(helper,config,true,"external-file-enable");
            baseline[0]=RELOADS.get();
            externalWrite(config,false);
        }).thenWaitUntil(()->{
            yieldToWatcher();
            helper.assertTrue(RELOADS.get()>baseline[0] && !ControllerConfig.bypassAeEnergyLimit(),
                "Real filesystem watcher must also reload external true-to-false edit");
        })
                .thenExecute(()->{
            helper.assertTrue(!offServerThread,"Both valid external reloads must run on server thread");
            assertSaved(helper,config,false,"external-file-disable");
            baseline[0]=RELOADS.get();
            // File events generated during this burst can only queue reloads, not
            // overwrite the map midway through a command's set-and-save operation.
            for(int index=0;index<32;index++) {
                boolean enabled=index%2==1;
                try { helper.assertTrue(dispatcher.execute("mestorage energyBypass "+enabled,admin)==1,
                        "Every rapid command must succeed"); }
                catch(CommandSyntaxException failure) { throw new AssertionError(failure); }
                helper.assertTrue(ControllerConfig.bypassAeEnergyLimit()==enabled,"Rapid command live value changed");
                assertSaved(helper,config,enabled,"burst-"+index);
            }
        }).thenWaitUntil(()->{
            yieldToWatcher();
            helper.assertTrue(RELOADS.get()>baseline[0],"Command-generated file events must emit real Reloading");
        })
                .thenExecute(()->{
                    helper.assertTrue(ControllerConfig.bypassAeEnergyLimit() && !offServerThread,
                            "Queued file events must preserve the last successful command");
                    assertSaved(helper,config,true,"after-burst-watcher");
                    // GameTestServer runs uncapped: 1200 ticks can finish in less
                    // than a second. Separate this edit from the command burst in
                    // real time so OS modify events are not coalesced with it.
                    quietUntil[0]=System.nanoTime()+250_000_000L;
                }).thenWaitUntil(()->{
                    yieldToWatcher();
                    helper.assertTrue(System.nanoTime()>=quietUntil[0],"Waiting for filesystem event queue to settle");
                }).thenExecute(()->{
                    baseline[0]=RELOADS.get();
                    externalWrite(config,"invalid-boolean");
                }).thenWaitUntil(()->{
                    yieldToWatcher();
                    helper.assertTrue(RELOADS.get()>baseline[0] && !ControllerConfig.bypassAeEnergyLimit(),
                        "Real watcher must correct invalid configuration to the declared false default; reloads="
                                +RELOADS.get()+", before="+baseline[0]+", raw="+config.getConfigData().get("energy.bypassAeEnergyLimit"));
                })
                .thenExecute(()->{
                    helper.assertTrue(!offServerThread,"Correction and Reloading must remain on server thread");
                    assertSaved(helper,config,false,"invalid-value-corrected");
                }).thenSucceed();
    }

    private static void yieldToWatcher() {
        // Only this asynchronous development test yields briefly; production ticks
        // and the 32-command same-tick race regression retain their normal speed.
        java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
    }

    private static void assertSaved(GameTestHelper helper,ModConfig config,boolean expected,String stage) {
        try(var saved=CommentedFileConfig.builder(config.getFullPath()).sync().build()) {
            saved.load();
            Object actual=saved.get("energy.bypassAeEnergyLimit");
            var diagnostic=diagnostic(config,stage,expected,actual);
            if(!stage.startsWith("burst-") || !Boolean.valueOf(expected).equals(actual))
                org.slf4j.LoggerFactory.getLogger(ConfigurationGameTests.class).info("ME_STORAGE_CONFIG_TEST {}",diagnostic);
            helper.assertTrue(Boolean.valueOf(expected).equals(actual),
                    "Command must save the actual world serverconfig TOML value: "+diagnostic);
        }
    }

    private static void externalWrite(ModConfig config,Object value) {
        try(var external=CommentedFileConfig.builder(config.getFullPath()).sync().build()) {
            external.load(); external.set("energy.bypassAeEnergyLimit",value); external.save();
        }
    }

    private static void cleanupOnCompletion(GameTestHelper helper,Runnable cleanup) {
        // Development-only listener access; restore persisted config even on timeout/failure.
        try {
            for(var field:GameTestHelper.class.getDeclaredFields()) {
                if(field.getType()!=GameTestInfo.class) continue;
                field.setAccessible(true);
                ((GameTestInfo)field.get(helper)).addListener(new GameTestListener() {
                    private boolean completed;
                    private void finish() { if(!completed) { completed=true; cleanup.run(); } }
                    @Override public void testStructureLoaded(GameTestInfo info) {}
                    @Override public void testPassed(GameTestInfo info) { finish(); }
                    @Override public void testFailed(GameTestInfo info) { finish(); }
                });
                return;
            }
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Cannot install config test cleanup");
    }

    private static String diagnostic(ModConfig config,String stage,boolean expected,Object disk) {
        String file;
        try {
            file="modified="+java.nio.file.Files.getLastModifiedTime(config.getFullPath())
                    +", text="+java.nio.file.Files.readString(config.getFullPath()).replace('\n',' ').replace('\r',' ');
        } catch(java.io.IOException failure) { file="readError="+failure; }
        return "stage="+stage+", expected="+expected+", disk="+disk
                +", cached="+ControllerConfig.bypassAeEnergyLimit()
                +", rawConfig="+config.getConfigData().get("energy.bypassAeEnergyLimit")
                +", configClass="+config.getConfigData().getClass().getName()
                +", path="+config.getFullPath()+", "+file;
    }
}
