package dev.mestorage.controller.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import dev.mestorage.controller.MEStorageController;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.IConfigEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Serializes this mod's server config reloads with commands; other Forge watches are untouched. */
@Mod.EventBusSubscriber(modid=MEStorageController.ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ServerConfigWatcher {
    private static volatile Session active;
    private ServerConfigWatcher() {}

    @SubscribeEvent
    public static void loaded(ModConfigEvent.Loading event) {
        var config=event.getConfig(); var server=ServerLifecycleHooks.getCurrentServer();
        if(config.getSpec()!=ControllerConfig.SPEC || config.getType()!=ModConfig.Type.SERVER || server==null
                || !(config.getConfigData() instanceof CommentedFileConfig file)) return;
        // Forge adds its watch before firing Loading. Replace just that path before
        // ConfigTracker's final save, while no player command can yet be executing.
        var session=new Session(config,file,server); active=session;
        try { FileWatcher.defaultInstance().setWatch(file.getNioPath(),session::changed); }
        catch(IOException failure) { active=null; throw new IllegalStateException("Cannot watch ME Storage Controller server config",failure); }
    }

    @SubscribeEvent
    public static void unloaded(ModConfigEvent.Unloading event) {
        var session=active;
        if(session!=null && event.getConfig()==session.config) {
            active=null;
            // Forge already removes this path before Unloading. NightConfig 3.6.4
            // removeWatch is NOT idempotent: repeating it corrupts the directory count.
        }
    }

    @Mod.EventBusSubscriber(modid=MEStorageController.ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class TickFallback {
        private TickFallback() {}
        @SubscribeEvent
        public static void serverTick(TickEvent.ServerTickEvent event) {
            var session=active;
            if(event.phase==TickEvent.Phase.END && session!=null && session.current()
                    && session.server.isSameThread()) session.poll();
        }
    }

    private record FileStamp(FileTime modified,long size) {
        static FileStamp read(Path path) throws IOException {
            var attributes=Files.readAttributes(path,BasicFileAttributes.class);
            return new FileStamp(attributes.lastModifiedTime(),attributes.size());
        }
    }

    private static final class Session {
        final ModConfig config;
        final CommentedFileConfig file;
        final MinecraftServer server;
        final AtomicBoolean dirty=new AtomicBoolean(),queued=new AtomicBoolean();
        FileStamp loadedStamp;
        int pollTicks;
        Session(ModConfig config,CommentedFileConfig file,MinecraftServer server) {
            this.config=config; this.file=file; this.server=server;
            try { loadedStamp=FileStamp.read(file.getNioPath()); }
            catch(IOException ignored) { /* Retry through the server-thread fallback. */ }
        }
        boolean current() {
            return active==this && ServerLifecycleHooks.getCurrentServer()==server
                    && config.getConfigData()==file && ControllerConfig.SPEC.isLoaded();
        }
        void changed() {
            if(!current()) return;
            dirty.set(true);
            if(queued.compareAndSet(false,true)) server.execute(this::reload);
        }
        void poll() {
            if(++pollTicks<20) return;
            pollTicks=0;
            try {
                // NightConfig 3.6.4 drops coalesced MODIFY events (count > 1).
                // One stat per second on this file preserves reliable hot reload
                // without replacing Forge's watchers or creating another thread.
                if(!FileStamp.read(file.getNioPath()).equals(loadedStamp)) changed();
            } catch(IOException ignored) {
                // Editors may briefly replace/remove the file; retry next poll.
            }
        }
        void reload() {
            try {
                if(!current() || !dirty.getAndSet(false) || ControllerConfig.SPEC.isCorrecting()) return;
                var stamp=FileStamp.read(file.getNioPath());
                file.load();
                if(!ControllerConfig.SPEC.isCorrect(file)) {
                    org.slf4j.LoggerFactory.getLogger(ServerConfigWatcher.class).warn("Correcting invalid server config {}",file.getNioPath());
                    ConfigFileTypeHandler.backUpConfig(file);
                    ControllerConfig.SPEC.correct(file);
                    file.save();
                }
                ControllerConfig.SPEC.afterReload();
                ModList.get().getModContainerById(MEStorageController.ID).orElseThrow()
                        .dispatchConfigEvent(IConfigEvent.reloading(config));
                // Keep the pre-load stamp: an external edit that arrives during
                // parsing/event dispatch must still be noticed by the next poll.
                loadedStamp=stamp;
            } catch(IOException | RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(ServerConfigWatcher.class).error("Could not reload server config {}",file.getNioPath(),failure);
            } finally {
                queued.set(false);
                if(dirty.get() && current()) changed();
            }
        }
    }
}
