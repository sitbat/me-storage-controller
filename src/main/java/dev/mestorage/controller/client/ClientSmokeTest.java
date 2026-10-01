package dev.mestorage.controller.client;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import dev.mestorage.controller.storage.StorageScanner;

/** Opt-in development-only integration capture. Run exclusively in a disposable smoke-test world. */
@Mod.EventBusSubscriber(modid = MEStorageController.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final boolean DEMO = !FMLEnvironment.production && Boolean.getBoolean("mestorage.clientDemo");
    private static final boolean ENABLED = DEMO || !FMLEnvironment.production && Boolean.getBoolean("mestorage.clientSmokeTest");
    private static int phase;
    private static int ticks;
    private static int view;
    private static int stableTicks;
    private static boolean requested;
    private static boolean capturing;
    private static volatile boolean captureDone;
    private static volatile String failure;
    private static BlockPos controllerPos;
    private static BlockPos galleryPos;
    private static String targetId = "";
    private static int transferPhase;
    private static int transferTicks;
    private static volatile boolean expanded;
    private static int awaitingPage = -1;
    private static int searchedPages;
    private static int searchPhase;
    private static int searchTicks;
    private static int navigationRaceTicks = -1;
    private static int rapidPairs;
    private static int rapidSettleTicks;
    private static boolean rapidVerificationRequested;
    private static volatile boolean rapidServerVerified;
    private static boolean rapidComplete;
    private static int heldToolbarPhase;
    private static int blockCaptureTicks;
    private static int blockCaptureStage;
    private static long highlightFrameBaseline;
    private static int treePhase;
    private static int treeWait;
    private static boolean treeVerified;
    private static boolean treeTransitionCapturePending;
    private static String normalTreeId;
    private static String secondTreeId;
    private static double previousTreeScroll;
    private static boolean inventoryPrepared;
    private static boolean captureViewPrepared;
    private static boolean textureSourcesChecked;
    private static int captureWait;
    private static volatile boolean failureCaptureDone;
    private static int failureCaptureTicks;
    private static String failureResult;
    private static int autoRestoreStage;
    private static int autoRestoreTicks;
    private static boolean resolutionRequested;
    private static int resolutionWait;
    private static int contentGridPhase;
    private static int contentGridWait;
    private static int gridCoveragePage;
    private static int wheelPhase;
    private static int wheelWait;
    private static int wheelExpected;
    private static int wheelView=-1;
    private static final java.util.Set<appeng.api.stacks.AEKey> gridKeys=new java.util.HashSet<>();
    private static final int RAPID_PAIRS=12;
    private static int synchronizedAcks;
    private static int navigationSyncStep;
    private static int navigationSyncWait;
    private static boolean navigationSyncRequested;
    private static Field captureMouseX;
    private static Field captureMouseY;
    private static PendingExtraCapture pendingExtraCapture;
    private static ControllerScreen.UiRect hoverCaptureRect;
    private static boolean cellTooltipCaptured;
    private static boolean fluidTooltipCaptured;
    private static int contentTransferScope;
    private static int contentTransferStep;
    private static int contentTransferWait;
    private static boolean contentTransferVerified;
    private static volatile boolean contentServerCheckDone;
    private static boolean contentServerCheckRequested;
    private static int contentInventorySlot;
    private static int expandedContentStep;
    private static int expandedContentWait;
    private static boolean expandedContentCheckRequested;
    private static volatile boolean expandedContentCheckDone;
    private static int bucketStep;
    private static int bucketWait;
    private static int bucketInventorySlot;
    private static boolean bucketServerRequested;
    private static volatile boolean bucketServerDone;
    private static boolean guideVerified;
    private static boolean treeMemoryVerified;
    private static int treeMemoryStep, treeMemoryWait;
    private static String treeMemoryScope;
    private static ControllerScreen treeMemoryOldScreen;
    private static boolean foldersVerified;
    private static int folderStep,folderWait,folderDiamondSlot;
    private static int folderShiftStep,folderGroupingStep,folderSelectionStep;
    private static java.util.UUID smokeGroupedFolder;
    private static java.util.UUID smokeParentFolder,smokeChildFolder;
    private static volatile boolean folderCheckDone;
    private static java.util.Map<String,net.minecraft.nbt.CompoundTag> folderPhysicalBaseline;
    private static int guideStep,guideWait,guideTooltipTries,guideDiamondSlot;
    private static ControllerScreen guideReturnScreen;
    private static ControllerMenu guideReturnMenu;
    private static String guideScopeDevice;
    private static int guideScopeCell;
    private record PendingExtraCapture(String name, int readyTick, Runnable afterCapture) {}
    private static final String[] NAMES = { "dark-network", "dark-drive", "dark-cell", "dark-expanded-cell20",
            "dark-fluid", "dark-external", "light-network", "light-expanded-cell20", "scale2-grid-light", "scale2-grid-dark",
            "light-drive", "light-cell", "light-fluid", "light-external", "scale2-inventory-light", "scale2-inventory-dark",
            "auto1280-network", "auto1280-cell20", "auto1280-inventory-light", "auto1280-inventory-dark",
            "auto1920-network", "auto1920-inventory" };

    private ClientSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == 9) return;
        var mc = Minecraft.getInstance();
        if (phase == 8) {
            if (failureCaptureDone || ++failureCaptureTicks > 100) finish(mc, failureResult);
            return;
        }
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (++ticks > 6000) throw new IllegalStateException("Client smoke timed out in phase " + phase + " view " + view);
            if (DEMO) {
                openDemo(mc);
                return;
            }
            if (phase == 4) {
                verifyAutoRestore(mc);
                return;
            }
            if(phase==5){verifyInGameGuide(mc);return;}
            if(phase==6){verifyTreeMemory(mc);return;}
            if(phase==7){verifyFolders(mc);return;}
            if (phase == 0) {
                var worldFolder = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath().normalize().getFileName().toString();
                if (!worldFolder.equals("SmokeTest")) {
                    phase = 9;
                    System.err.println("ME_STORAGE_SMOKE_REFUSED: destructive test fixture requires the SmokeTest world; current world=" + worldFolder);
                    return;
                }
                mc.options.guiScale().set(2);
                verifyModelSprites(mc);
                observeSelectionAcknowledgements(mc);
                mc.getTutorial().setStep(TutorialSteps.NONE);
                mc.resizeDisplay();
                var id = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try { setup(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
                    catch (Throwable problem) { failure = problem.toString(); }
                });
                phase = 1;
                ticks = 0;
            } else if (phase == 1 && ticks > 100) {
                var id = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try { populateAndOpen(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
                    catch (Throwable problem) { failure = problem.toString(); }
                });
                phase = 2;
            } else if (phase == 2 && mc.player.containerMenu instanceof ControllerMenu menu
                    && mc.screen instanceof ControllerScreen && menu.getSnapshot().online()) {
                driveCapture(mc, menu);
            } else if (phase == 3) {
                captureBlockAppearance(mc);
            }
        } catch (Throwable problem) {
            problem.printStackTrace();
            if (DEMO) {
                System.err.println("ME_STORAGE_DEMO_UNAVAILABLE: " + problem
                        + "; automatic opening stopped; the game remains available for manual play");
                phase = 9;
                return;
            }
            captureFailureThenFinish(mc, problem);
        }
    }

    /** Open the existing demo fixture once, then leave all interaction to the player. */
    private static void openDemo(Minecraft mc) {
        if (phase == 0 && ticks >= 100) {
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.getToasts().clear();
            mc.resizeDisplay();
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    if (player == null) throw new IllegalStateException("Missing demo player");
                    var pos = new BlockPos(8, 100, 8);
                    var controller = player.serverLevel().getBlockEntity(pos);
                    if (!(controller instanceof ControllerBlockEntity block) || !block.getMainNode().isActive())
                        throw new IllegalStateException("Prepared demo controller is missing or offline");
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.serverLevel(), 8.5, 100, 11.5, 180, 20);
                    NetworkHooks.openScreen(player, block, pos);
                } catch (Throwable problem) { failure = problem.toString(); }
            });
            phase = 1;
        } else if (phase == 1 && mc.screen instanceof ControllerScreen
                && mc.player.containerMenu instanceof ControllerMenu menu && menu.getSnapshot().online()) {
            System.out.println("ME_STORAGE_DEMO_READY: dashboard open; interactive instance remains running");
            phase = 9;
        }
    }

    private static void setup(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("Missing server player");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying=true;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        var level = player.serverLevel();
        controllerPos = new BlockPos(8, 100, 8);
        // The explicitly disposable fixture is reset on reruns, including all old cells.
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -2; z <= 1; z++)
            level.setBlockAndUpdate(controllerPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(controllerPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(controllerPos, MEStorageController.CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(controllerPos.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(controllerPos.west(), AEBlocks.CHEST.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(controllerPos.east());
        var chest = (ChestBlockEntity) level.getBlockEntity(controllerPos.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        for(int slot=1;slot<=5;slot++)drive.getInternalInventory().setItemDirect(slot, AEItems.ITEM_CELL_16K.stack());
        chest.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_64K.stack());
        var expandedId = new ResourceLocation("expatternprovider", "ex_drive");
        expanded = ForgeRegistries.BLOCKS.containsKey(expandedId);
        if (expanded) {
            level.setBlockAndUpdate(controllerPos.above(), ForgeRegistries.BLOCKS.getValue(expandedId).defaultBlockState());
            var expandedDrive = (DriveBlockEntity) level.getBlockEntity(controllerPos.above());
            if (expandedDrive.getInternalInventory().size() != 20) throw new IllegalStateException("Expected real 20-slot ExtendedAE drive");
            // Fill page-two preceding slots so Shift-return has exactly one free destination.
            for (int slot = 10; slot < 19; slot++) expandedDrive.getInternalInventory().setItemDirect(slot, AEItems.ITEM_CELL_1K.stack());
            expandedDrive.getInternalInventory().setItemDirect(19, AEItems.ITEM_CELL_64K.stack());
        } else System.out.println("ME_STORAGE_SMOKE_SKIPPED ExtendedAE page-two checks: addon absent");
        level.setBlockAndUpdate(controllerPos.north(2), Blocks.BARREL.defaultBlockState());
        var barrel = (BarrelBlockEntity) level.getBlockEntity(controllerPos.north(2));
        barrel.setItem(0, new ItemStack(Items.EMERALD, 37));
        PartHelper.setPart(level, controllerPos.north(), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(level, controllerPos.north(), Direction.NORTH, null, AEParts.STORAGE_BUS.asItem());
        player.teleportTo(controllerPos.getX() - 2.5, controllerPos.getY(), controllerPos.getZ() + 2.5);
    }

    private static void populateAndOpen(ServerPlayer player) {
        var level = player.serverLevel();
        var controller = (ControllerBlockEntity) level.getBlockEntity(controllerPos);
        if (!controller.getMainNode().isActive()) throw new IllegalStateException("Smoke network inactive");
        var drive = (DriveBlockEntity) level.getBlockEntity(controllerPos.east());
        var chest = (ChestBlockEntity) level.getBlockEntity(controllerPos.west());
        var inventory = drive.getCellInventory(0);
        var source = IActionSource.empty();
        inventory.insert(AEItemKey.of(Items.IRON_INGOT), 12345, Actionable.MODULATE, source);
        inventory.insert(AEItemKey.of(Items.GOLD_INGOT), 982, Actionable.MODULATE, source);
        inventory.insert(AEItemKey.of(Items.DIAMOND), 64, Actionable.MODULATE, source);
        inventory.insert(AEItemKey.of(Items.COBBLESTONE), 123456, Actionable.MODULATE, source);
        inventory.insert(AEItemKey.of(Items.OAK_LOG), 23000, Actionable.MODULATE, source);
        // Five real cells avoid AE's per-cell type limit while exercising more than five grid pages.
        var gridItems=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(item->item instanceof net.minecraft.world.item.BlockItem
                        && ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("minecraft")
                        && item!=Items.COBBLESTONE && item!=Items.OAK_LOG)
                .sorted(java.util.Comparator.comparing(item->ForgeRegistries.ITEMS.getKey(item).toString())).limit(260).toList();
        if(gridItems.size()!=260)throw new IllegalStateException("Multi-page fixture requires 260 distinct vanilla block items");
        for(int i=0;i<gridItems.size();i++) {
            long accepted=drive.getCellInventory(1+i/52).insert(AEItemKey.of(gridItems.get(i)),i+1,Actionable.MODULATE,source);
            if(accepted!=i+1)throw new IllegalStateException("Failed to populate 260-key real content-grid fixture");
        }
        chest.getCellInventory(0).insert(AEFluidKey.of(Fluids.WATER), 23456, Actionable.MODULATE, source);
        chest.getCellInventory(0).insert(AEFluidKey.of(Fluids.LAVA), 777000, Actionable.MODULATE, source);
        if (expanded) {
            var expandedDrive = (DriveBlockEntity) level.getBlockEntity(controllerPos.above());
            if (!expandedDrive.getMainNode().isActive()) throw new IllegalStateException("Expanded-drive fixture is not connected to the network");
            long inserted = expandedDrive.getCellInventory(19).insert(AEItemKey.of(Items.COPPER_INGOT), 98765,
                    Actionable.MODULATE, source);
            if (inserted != 98765) throw new IllegalStateException("Expanded-drive fixture did not accept exact copper amount");
        }
        // AE2 ejects old cells when setup replaces copied fixture blocks with AIR.
        // Remove only pre-interaction item entities in this disposable fixture;
        // the strict post-gesture zero-drop assertion below remains unchanged.
        var worldFolder = player.serverLevel().getServer().getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize().getFileName().toString();
        if (!worldFolder.equals("SmokeTest")) throw new IllegalStateException("Refusing fixture item cleanup outside SmokeTest");
        var fixtureBounds = new net.minecraft.world.phys.AABB(controllerPos).inflate(8);
        var oldDrops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fixtureBounds);
        for (var entity : oldDrops) {
            System.out.println("ME_STORAGE_SMOKE_FIXTURE_OLD_ITEM uuid=" + entity.getUUID()
                    + " item=" + ForgeRegistries.ITEMS.getKey(entity.getItem().getItem())
                    + " count=" + entity.getItem().getCount());
            entity.discard();
        }
        var baseline = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fixtureBounds,
                entity -> entity.isAlive() && entity.getItem().is(AEItems.ITEM_CELL_64K.asItem()));
        if (!baseline.isEmpty()) throw new IllegalStateException("Disposable fixture did not reach zero dropped-cell baseline");
        // Old block drops may already have been picked up during the network startup wait.
        // Normalize only this pre-interaction disposable fixture, after all old drops are removed.
        System.out.println("ME_STORAGE_SMOKE_PLAYER_FIXTURE_RESET collectedOld64k="
                +playerAmount(player,AEItems.ITEM_CELL_64K.asItem())+" oldCursor="+player.containerMenu.getCarried());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().add(AEItems.ITEM_CELL_1K.stack());
        player.getInventory().add(new ItemStack(Items.DIAMOND,32));
        player.getInventory().add(new ItemStack(Items.BUCKET));
        if(playerAmount(player,AEItems.ITEM_CELL_64K.asItem())!=0
                ||playerAmount(player,AEItems.ITEM_CELL_1K.asItem())!=1||playerAmount(player,Items.DIAMOND)!=32
                ||playerAmount(player,Items.BUCKET)!=1
                ||!player.containerMenu.getCarried().isEmpty())
            throw new IllegalStateException("Pre-open disposable player inventory baseline is invalid");
        System.out.println("ME_STORAGE_SMOKE_PLAYER_BASELINE PASS extra64k=0, cell1k=1, diamonds=32, bucket=1, cursor empty before any interaction");
        System.out.println("ME_STORAGE_SMOKE_DROP_BASELINE PASS removedOldFixtureItems=" + oldDrops.size()
                + "; dropped64k=0 before opening the menu or sending any mouse events");
        NetworkHooks.openScreen(player, controller, controllerPos);
    }

    private static void driveCapture(Minecraft mc, ControllerMenu menu) {
        if(!textureSourcesChecked&&mc.screen instanceof ControllerScreen screen) {
            var actual=screen.smokeGuiTextureSources();
            for(String name:new String[]{"terminal","states","text_field","background"}) {
                var location=new ResourceLocation("ae2","textures/guis/"+name+".png");
                var source=mc.getResourceManager().getResource(location).map(r->r.sourcePackId()).orElse("");
                String chosen=actual.get(name.equals("states")?"icons":name);
                if(source.startsWith("file/")?!chosen.equals(location+" | "+source):!chosen.startsWith("me_storage_controller:"))
                    throw new IllegalStateException("Wrong GUI texture source for "+name+": "+chosen+"; available="+source);
            }
            if(!actual.get("button_background").startsWith("me_storage_controller:"))
                throw new IllegalStateException("Toolbar background must retain its modern atlas UVs");
            System.out.println("ME_STORAGE_SMOKE_TEXTURE_SOURCES PASS "+actual);
            textureSourcesChecked=true;
        }
        // Synchronize the development harness cursor without changing production tooltip rendering.
        clearCaptureHover(mc);
        if (drainExtraCapture(mc)) return;
        Snapshot s = menu.getSnapshot();
        if (!treeVerified && !verifyStorageTree(mc, menu)) return;
        if(!guideVerified) {
            guideReturnScreen=(ControllerScreen)mc.screen;guideReturnMenu=menu;
            guideScopeDevice=menu.getSnapshot().selectedDevice();guideScopeCell=menu.getSnapshot().selectedCell();
            phase=5;return;
        }
        if(!treeMemoryVerified){phase=6;return;}
        if(!foldersVerified){phase=7;return;}
        if(view==0 && !verifyNavigationSync(menu))return;
        if(view==0 && !verifyContentGrid(mc,menu)) return;
        if(view==0 && !verifyContentWheel(mc,menu)) return;
        if(view==0 && !verifyContentTransfers(mc,menu)) return;
        if (capturing) {
            if (!captureDone) return;
            capturing = false;
            captureDone = false;
            requested = false;
            stableTicks = 0;
            transferPhase = 0;
            transferTicks = 0;
            searchPhase = 0;
            searchTicks = 0;
            awaitingPage = -1;
            searchedPages = 0;
            rapidPairs = 0;
            rapidSettleTicks = 0;
            rapidVerificationRequested = false;
            rapidServerVerified = false;
            rapidComplete = false;
            heldToolbarPhase = 0;
            inventoryPrepared = false;
            captureViewPrepared = false;
            captureWait = 0;
            resolutionRequested = false;
            resolutionWait = 0;
            if (++view == NAMES.length) {
                phase = 3;
                blockCaptureTicks = 0;
                blockCaptureStage = 0;
                return;
            }
            if (view == 19) {
                phase = 4;
                autoRestoreStage = 0;
                autoRestoreTicks = 0;
                return;
            }
        }
        if (!expanded && (view == 3 || view == 7)) { view++; requested = false; }
        boolean inventoryCapture = view == 14 || view == 15 || view == 18 || view == 19 || view == 21;
        boolean autoView = view >= 16;
        boolean expandedView = view == 3 || view == 7 || (inventoryCapture || view == 17) && expanded;
        String registry = switch (view) { case 1, 2, 10, 11 -> "ae2:drive"; case 3, 7 -> "expatternprovider:ex_drive";
            case 14, 15, 17, 18, 19, 21 -> expanded ? "expatternprovider:ex_drive" : "ae2:drive";
            case 4, 12 -> "ae2:chest"; default -> ""; };
        int cell = expandedView ? 19 : inventoryCapture || view == 2 || view == 4 || view == 11 || view == 12 ? 0 : -1;
        if (!requested) {
            int expectedWidth = view >= 20 ? 1920 : 1280;
            int expectedHeight = view >= 20 ? 1080 : 720;
            if (!resolutionRequested) {
                resolutionRequested = true;
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), expectedWidth, expectedHeight);
                return;
            }
            if (mc.getWindow().getScreenWidth() != expectedWidth || mc.getWindow().getScreenHeight() != expectedHeight) {
                resolutionWait++;
                String dimensions = "requested=" + expectedWidth + "x" + expectedHeight + " actualWindow="
                        + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight()
                        + " framebuffer=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight();
                if (resolutionWait == 20) {
                    System.out.println("ME_STORAGE_SMOKE_RESIZE_WAIT " + dimensions);
                    if (view >= 20) {
                        // Only this disposable test window: remove OS borders before retrying exact full-HD client area.
                        long window = mc.getWindow().getWindow();
                        org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
                        org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_DECORATED, org.lwjgl.glfw.GLFW.GLFW_FALSE);
                        org.lwjgl.glfw.GLFW.glfwSetWindowPos(window, 0, 0);
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window, expectedWidth, expectedHeight);
                        System.out.println("ME_STORAGE_SMOKE_RESIZE_RETRY borderless " + dimensions);
                    }
                }
                if (resolutionWait >= 100) throw new IllegalStateException("Exact smoke resolution unavailable: " + dimensions);
                return;
            }
            mc.getToasts().clear();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1 + (ticks & 1), 1 + (ticks & 1));
            int scale = autoView ? 0 : 2;
            if (mc.options.guiScale().get() != scale) { mc.options.guiScale().set(scale); mc.resizeDisplay(); }
            var screen = (ControllerScreen) mc.screen;
            screen.setDarkThemeForTest(view < 6 || view == 9 || view == 15 || view == 19);
            if (awaitingPage >= 0 && s.devicePage() != awaitingPage) return;
            awaitingPage = -1;
            var wanted = s.directory().stream().map(Snapshot.DirectoryEntry::device).filter(d -> view == 5 || view == 13 ? d.kind().equalsIgnoreCase("external")
                    : !registry.isEmpty() && registry.equals(ForgeRegistries.ITEMS.getKey(d.icon().getItem()).toString())).findFirst();
            if ((!registry.isEmpty() || view == 5 || view == 13) && wanted.isEmpty()) {
                if (++searchedPages > s.devicePages()) throw new IllegalStateException("Missing smoke device " + registry);
                awaitingPage = (s.devicePage() + 1) % Math.max(1, s.devicePages());
                menu.request("", -1, awaitingPage, 0, "", "", true);
                return;
            }
            targetId = wanted.map(Snapshot.DeviceInfo::id).orElse("");
            menu.request(targetId, cell, s.devicePage(), 0, "", "", true);
            requested = true;
            return;
        }
        if (!s.selectedDevice().equals(targetId) || s.selectedCell() != cell) return;
        if((view==5||view==13)&&(s.selectedInfo()==null||!s.selectedInfo().pos().equals(controllerPos.north(2))))
            throw new IllegalStateException("External storage location must identify the barrel, not the intervening storage bus");
        if (++stableTicks < 20) return;
        if(view==16&&!verifyContentWheel(mc,menu))return;
        if ((view == 1 || view == 2 || view == 3 || view == 18 || view == 21) && !inventoryPrepared) {
            var inventoryTab = ((ControllerScreen) mc.screen).smokeInventoryTabRect();
            if (inventoryTab != null) clickUi((ControllerScreen) mc.screen, inventoryTab);
            inventoryPrepared = true;
            return;
        }
        if (autoView) {
            if (mc.options.guiScale().get() != 0) throw new IllegalStateException("Controller changed the Auto GUI option");
            double expectedScale = mc.getWindow().calculateScale(0,mc.isEnforceUnicode());
            if (mc.getWindow().getGuiScale() != expectedScale)
                throw new IllegalStateException("Auto controller scale incorrect: " + mc.getWindow().getGuiScale());
        }
        if ((view == 18 || view == 21) && !verifyRapidMouseClicks(mc, menu, expanded ? 9 : 0,
                expanded ? 19 : 0, expanded ? Items.COPPER_INGOT : Items.IRON_INGOT, expanded ? 98765 : 12345)) return;
        if (view == 1 && navigationRaceTicks != -2) {
            if (navigationRaceTicks == -1) {
                menu.request("", -1, s.devicePage(), 0, "", "", true);
                menu.request(targetId, -1, s.devicePage(), 0, "", "", true);
                if (menu.canSendClick(0, ClickType.PICKUP)) throw new IllegalStateException("Pending navigation did not protect remote slots");
                navigationRaceTicks = 30;
                return;
            }
            if (--navigationRaceTicks > 0) return;
            if (!menu.canSendClick(0, ClickType.PICKUP)) throw new IllegalStateException("A-B-A navigation left remote slots locked");
            if (menu.canSendClick(0, ClickType.QUICK_CRAFT))
                throw new IllegalStateException("Unsupported drag distribution was not gated");
            navigationRaceTicks = -2;
            System.out.println("ME_STORAGE_SMOKE_NAVIGATION PASS rapid A-B-A locks until latest acknowledgement and then unlocks");
        }
        if (view == 1 && !menu.getSlot(0).getItem().is(AEItems.ITEM_CELL_64K.asItem())) {
            throw new IllegalStateException("Client remote cell slot did not synchronize");
        }
        if (view == 2 && !verifyRapidMouseClicks(mc, menu, 0, 0, Items.IRON_INGOT, 12345)) return;
        if (view == 2 && s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.IRON_INGOT)) && c.amount() == 12345)) {
            if (transferPhase == 0 || transferPhase >= 5) throw new IllegalStateException("Cell detail lost exact iron count");
        }
        if (view == 2 && !verifyTransfers(mc, menu, 0, Items.IRON_INGOT, 12345)) return;
        if (view == 2 && !cellTooltipCaptured) {
            hoverCaptureRect=((ControllerScreen)mc.screen).smokeSlotRect(0);
            if(hoverCaptureRect==null)throw new IllegalStateException("Tooltip fixture cell is not visible");
            cellTooltipCaptured=true;
            captureExtra(mc,"smoke-cell-tooltip.png");
            return;
        }
        if (view == 3) {
            if (s.cellSlots() != 20 || s.editableSlots() != 10) throw new IllegalStateException("Expanded page-two slot counts incorrect");
            if (!verifyRapidMouseClicks(mc, menu, 9, 19, Items.COPPER_INGOT, 98765)) return;
            if (!verifyTransfers(mc, menu, 9, Items.COPPER_INGOT, 98765)) return;
            if (!verifyExpandedContentTransfer(mc,menu)) return;
            if (!verifyLocalizedSearch(menu)) return;
        }
        if (expandedView && transferPhase == 0 && s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.COPPER_INGOT)) && c.amount() == 98765))
            throw new IllegalStateException("Expanded slot20 lost exact copper count");
        if (view == 4 && s.contents().stream().noneMatch(c -> c.key().equals(AEFluidKey.of(Fluids.WATER)) && c.amount() == 23456)) {
            if(bucketStep==0||bucketStep>=8)throw new IllegalStateException("Cell detail lost exact water amount");
        }
        if(view==4&&!verifyBucketClient(mc,menu))return;
        if(view==4&&!fluidTooltipCaptured) {
            hoverCaptureRect=((ControllerScreen)mc.screen).smokeSlotRect(0);
            if(hoverCaptureRect==null)throw new IllegalStateException("Fluid tooltip fixture slot is not visible");
            fluidTooltipCaptured=true;
            captureExtra(mc,"smoke-fluid-cell-tooltip.png");
            return;
        }
        if (view == 5 && (s.capacity().occupiedSlots() != 1 || s.capacity().totalSlots() != 27
                || s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.EMERALD)) && c.amount() == 37)))
            throw new IllegalStateException("External barrel lost exact 37 emeralds or 1/27 capacity");
        if (!captureViewPrepared) {
            var captureTab = inventoryCapture ? ((ControllerScreen) mc.screen).smokeInventoryTabRect()
                    : ((ControllerScreen) mc.screen).smokeContentsTabRect();
            if (captureTab != null) clickUi((ControllerScreen) mc.screen, captureTab);
            clearCaptureHover(mc);
            captureViewPrepared = true;
            return;
        }
        if (++captureWait < 4) return;
        verifyVisibleBounds(mc, (ControllerScreen) mc.screen, menu, inventoryCapture);
        if (inventoryCapture && (!menu.getSlot(expanded ? 9 : 0).isActive()
                || !menu.getSlot(expanded ? 9 : 0).getItem().is(AEItems.ITEM_CELL_64K.asItem())))
            throw new IllegalStateException("Compact inventory tab did not expose the selected physical cell");
        capturing = true;
        File directory = output(mc);
        directory.mkdirs();
        String screenshotName = "smoke-" + NAMES[view] + ".png";
        Screenshot.grab(directory, screenshotName, mc.getMainRenderTarget(), result -> {
            System.out.println("ME_STORAGE_SMOKE_SCREENSHOT " + result.getString());
            if (!new File(directory, "screenshots/" + screenshotName).isFile()) failure = "Screenshot save failed: " + result.getString();
            captureDone = true;
        });
    }

    private static void clickUi(ControllerScreen screen, ControllerScreen.UiRect rect) {
        if (rect == null) throw new IllegalStateException("Requested tree/control row is not currently visible");
        screen.mouseClicked(rect.centerX(), rect.centerY(), 0);
        screen.mouseReleased(rect.centerX(), rect.centerY(), 0);
    }

    private static boolean verifyContentGrid(Minecraft mc,ControllerMenu menu) {
        if(contentGridPhase>=6)return true;
        if(contentGridWait>0 && --contentGridWait>0)return false;
        var screen=(ControllerScreen)mc.screen;var snapshot=menu.getSnapshot();
        switch(contentGridPhase) {
            case 0 -> {
                if(!snapshot.selectedDevice().isEmpty())return false;
                if(snapshot.contentCount()<260 || snapshot.contents().size()!=45 || snapshot.contentPages()<5)
                    throw new IllegalStateException("Grid smoke fixture must contain at least five pages with 45 keys per full page");
                gridKeys.clear();snapshot.contents().forEach(entry->gridKeys.add(entry.key()));
                gridCoveragePage=1;
                assertAmountOrder(snapshot);
                clickUi(screen,screen.smokeContentNextRect());
            }
            case 1 -> {
                if(snapshot.contentPage()!=gridCoveragePage)
                    throw new IllegalStateException("Real Next control did not show requested content page "+gridCoveragePage);
                for(var entry:snapshot.contents())if(!gridKeys.add(entry.key()))throw new IllegalStateException("Grid pages duplicate a key");
                if(gridCoveragePage+1<snapshot.contentPages()) {
                    gridCoveragePage++;clickUi(screen,screen.smokeContentNextRect());contentGridWait=10;return false;
                }
                if(gridKeys.size()!=snapshot.contentCount())throw new IllegalStateException("Grid pages omitted stored keys");
                assertAmountOrder(snapshot);
                captureExtra(mc,"smoke-grid-page-two.png", () -> clickUi(screen,screen.smokeContentPreviousRect()));
            }
            case 2 -> {
                if(snapshot.contentPage()>0){clickUi(screen,screen.smokeContentPreviousRect());contentGridWait=10;return false;}
                if(snapshot.contentPage()!=0||snapshot.contents().size()!=45)throw new IllegalStateException("Previous control did not restore page zero");
                clickUi(screen,screen.smokeSortRect());
            }
            case 3 -> {
                for(int i=1;i<snapshot.contents().size();i++) {
                    String before=snapshot.contents().get(i-1).key().getDisplayName().getString();
                    String after=snapshot.contents().get(i).key().getDisplayName().getString();
                    if(String.CASE_INSENSITIVE_ORDER.compare(before,after)>0)throw new IllegalStateException("Name-sort control did not sort grid contents");
                }
                captureExtra(mc,"smoke-grid-name-sort.png", () -> clickUi(screen,screen.smokeSortRect()));
            }
            case 4 -> {
                assertAmountOrder(snapshot);
                if(snapshot.contents().get(0).amount()!=777000 || !snapshot.contents().get(0).key().equals(AEFluidKey.of(Fluids.LAVA)))
                    throw new IllegalStateException("Amount sort did not restore the 777000 mB lava entry");
                contentClick(screen,0,0);
            }
            case 5 -> {
                if(!menu.getCarried().isEmpty())throw new IllegalStateException("Empty-cursor fluid inspection changed inventory");
                System.out.println("ME_STORAGE_SMOKE_CONTENT_GRID PASS 45-slot page, 260+ types, full unique-key coverage, real Next/Previous/name/amount controls; fluid inspection preserves empty cursor");
            }
        }
        contentGridPhase++;contentGridWait=25;return contentGridPhase>=6;
    }

    /** Actual mouse wheel events, including multiple events before the server can acknowledge any of them. */
    private static boolean verifyContentWheel(Minecraft mc,ControllerMenu menu) {
        var screen=(ControllerScreen)mc.screen;
        if(wheelView!=view){wheelView=view;wheelPhase=0;wheelWait=0;wheelExpected=0;}
        if(wheelPhase>=8)return true;
        if(screen.smokeContentScrollPosition()!=wheelExpected)
            throw new IllegalStateException("Wheel target moved backward during acknowledgement: phase="+wheelPhase+" expected="+wheelExpected+" actual="+screen.smokeContentScrollPosition());
        if(wheelWait>0&&--wheelWait>0)return false;
        if(screen.smokeContentPending())throw new IllegalStateException("Wheel target not acknowledged within 25 ticks");
        if(screen.smokeContentAcknowledgedPage()!=screen.smokeContentTargetPage())
            throw new IllegalStateException("Wheel acknowledgement disagrees with latest requested page");
        var grid=screen.smokeContentScrollRect();var scrollbar=screen.smokeScrollbarRect();
        if(grid==null||scrollbar==null||screen.smokeContentScrollMax()<5)
            throw new IllegalStateException("Wheel smoke requires a visible grid, scrollbar and at least five scroll positions");
        switch(wheelPhase) {
            case 0 -> {
                for(int i=0;i<3;i++)wheel(screen,grid,-1);
                wheelExpected=3;
            }
            case 1 -> {wheel(screen,grid,-3);wheelExpected=Math.min(6,screen.smokeContentScrollMax());}
            case 2 -> {
                wheel(screen,scrollbar,100);wheelExpected=0;
                for(int i=0;i<3;i++) {
                    wheel(screen,scrollbar,-.25);
                    if(screen.smokeContentScrollPosition()!=0)throw new IllegalStateException("Fractional wheel moved before one full step");
                }
                wheel(screen,scrollbar,-.25);wheelExpected=1;
            }
            case 3 -> {
                wheel(screen,scrollbar,-100);wheel(screen,scrollbar,1);
                wheelExpected=screen.smokeContentScrollMax()-1;
            }
            case 4 -> {
                wheel(screen,grid,100);wheel(screen,grid,-1);wheelExpected=1;
            }
            case 5 -> {
                // New requests arrive while previous requests are still pending; the last target must win.
                wheel(screen,scrollbar,-3);wheel(screen,scrollbar,2);wheelExpected=2;
            }
            case 6 -> {
                wheel(screen,grid,100);wheelExpected=0;
            }
            case 7 -> {
                System.out.println("ME_STORAGE_SMOKE_WHEEL PASS nativeScale="+mc.getWindow().getGuiScale()+" visibleRows="+screen.smokeContentVisibleRows()
                        +" rapid steps/magnitude/fractional scrollbar hover/boundary reversal/latest acknowledgement; empty cursor="+menu.getCarried().isEmpty());
                if(!menu.getCarried().isEmpty())throw new IllegalStateException("Scrolling changed cursor inventory");
            }
        }
        if(screen.smokeContentScrollPosition()!=wheelExpected)
            throw new IllegalStateException("Real wheel lost increments: phase="+wheelPhase+" expected="+wheelExpected+" actual="+screen.smokeContentScrollPosition());
        wheelPhase++;wheelWait=25;return wheelPhase>=8;
    }

    private static void wheel(ControllerScreen screen,ControllerScreen.UiRect rect,double amount) {
        if(!screen.mouseScrolled(rect.centerX(),rect.centerY(),amount))throw new IllegalStateException("Real wheel gesture was not consumed");
    }

    /** Real grid gestures with authoritative round-trip checks; each scope restores the original fixture. */
    private static boolean verifyContentTransfers(Minecraft mc,ControllerMenu menu) {
        if(contentTransferVerified)return true;
        if(contentTransferWait>0&&--contentTransferWait>0)return false;
        var screen=(ControllerScreen)mc.screen;var s=menu.getSnapshot();
        String driveId=s.directory().stream().map(Snapshot.DirectoryEntry::device)
                .filter(d->d.pos().equals(controllerPos.east())).findFirst().orElseThrow().id();
        if(contentTransferScope==3)return verifyCellInsertionScope(mc,menu,screen,driveId);
        String id=contentTransferScope==0?"":driveId;
        int cell=contentTransferScope==2?0:-1;
        switch(contentTransferStep) {
            case 0 -> {
                if(!screen.smokeRootLabel().equals("ME网络"))
                    throw new IllegalStateException("Chinese network root label is not ME网络");
                menu.request(id,cell,0,0,"","minecraft:iron_ingot",true);
            }
            case 1 -> {
                assertTreeSelection(s,id,cell);assertContentState(menu,Items.IRON_INGOT,12345,0,0);
                contentClick(screen,0,0);
            }
            case 2 -> {assertContentState(menu,Items.IRON_INGOT,12281,64,0);contentClick(screen,emptyContentIndex(screen,menu),0);}
            case 3 -> {assertContentState(menu,Items.IRON_INGOT,12345,0,0);contentClick(screen,0,1);}
            case 4 -> {assertContentState(menu,Items.IRON_INGOT,12313,32,0);contentClick(screen,emptyContentIndex(screen,menu),1);}
            case 5 -> {assertContentState(menu,Items.IRON_INGOT,12314,31,0);contentClick(screen,emptyContentIndex(screen,menu),0);}
            case 6 -> {assertContentState(menu,Items.IRON_INGOT,12345,0,0);screen.smokeContentClick(0,0,true);}
            case 7 -> {
                assertContentState(menu,Items.IRON_INGOT,12281,0,64);
                contentInventorySlot=findPlayerItemSlot(menu,Items.IRON_INGOT);
                clickUi(screen,screen.smokeSlotRect(contentInventorySlot));
            }
            case 8 -> {assertContentState(menu,Items.IRON_INGOT,12281,64,0);screen.smokeContentClick(emptyContentIndex(screen,menu),0,true);}
            case 9 -> {
                assertContentState(menu,Items.IRON_INGOT,12345,0,0);
                // No acknowledgement delay between presses: server must alternate extract/insert in arrival order.
                for(int click=0;click<8;click++)contentClick(screen,0,0);
            }
            case 10 -> {
                assertContentState(menu,Items.IRON_INGOT,12345,0,0);
                if(!verifyContentServer(mc,12345,64,0,32))return false;
                System.out.println("ME_STORAGE_SMOKE_CONTENT_TRANSFER PASS scope="+contentTransferScope
                        +" left-stack/right-half/insert-one/empty-tile/Shift-to-inventory/8-rapid-clicks; exact iron=12345");
                contentTransferScope++;contentTransferStep=0;contentServerCheckRequested=false;contentServerCheckDone=false;
                contentTransferWait=2;return false;
            }
            default -> throw new IllegalStateException("Unknown content-transfer step "+contentTransferStep);
        }
        contentTransferStep++;contentTransferWait=25;return false;
    }

    private static boolean verifyCellInsertionScope(Minecraft mc,ControllerMenu menu,ControllerScreen screen,String driveId) {
        switch(contentTransferStep) {
            case 0 -> menu.request(driveId,1,0,0,"","minecraft:diamond",true);
            case 1 -> {
                assertTreeSelection(menu.getSnapshot(),driveId,1);
                assertContentState(menu,Items.DIAMOND,0,0,32);
                contentInventorySlot=findPlayerItemSlot(menu,Items.DIAMOND);
                clickUi(screen,screen.smokeSlotRect(contentInventorySlot));
            }
            case 2 -> {assertContentState(menu,Items.DIAMOND,0,32,0);contentClick(screen,0,0);}
            case 3 -> {
                assertContentState(menu,Items.DIAMOND,32,0,0);
                if(!verifyContentServer(mc,12345,64,32,0))return false;
                contentServerCheckRequested=false;contentServerCheckDone=false;
                contentClick(screen,0,0);
            }
            case 4 -> {
                assertContentState(menu,Items.DIAMOND,0,32,0);
                clickUi(screen,screen.smokeSlotRect(contentInventorySlot));
            }
            case 5 -> {
                assertContentState(menu,Items.DIAMOND,0,0,32);
                if(!verifyContentServer(mc,12345,64,0,32))return false;
                System.out.println("ME_STORAGE_SMOKE_CONTENT_SCOPE PASS selected cell2 insert32 diamonds stays separate from cell1 existing64; withdrawal restored player32");
                menu.request("",-1,0,0,"","",true);
            }
            case 6 -> {
                if(!menu.getSnapshot().selectedDevice().isEmpty()||menu.getSnapshot().contentCount()<=45)return false;
                contentTransferVerified=true;
                System.out.println("ME_STORAGE_SMOKE_ROOT_LABEL PASS ME网络");return true;
            }
            default -> throw new IllegalStateException("Unknown cell-scope step");
        }
        contentTransferStep++;contentTransferWait=25;return false;
    }

    private static void contentClick(ControllerScreen screen,int index,int button) {
        var rect=screen.smokeContentRect(index);
        if(rect==null)throw new IllegalStateException("Content tile not visible: "+index);
        screen.mouseClicked(rect.centerX(),rect.centerY(),button);
        screen.mouseReleased(rect.centerX(),rect.centerY(),button);
    }

    private static int emptyContentIndex(ControllerScreen screen,ControllerMenu menu) {
        for(int index=menu.getSnapshot().contents().size();index<Snapshot.CONTENT_PAGE_SIZE;index++)
            if(screen.smokeContentRect(index)!=null)return index;
        throw new IllegalStateException("Selected sparse test scope has no visible empty content tile");
    }

    private static boolean verifyExpandedContentTransfer(Minecraft mc,ControllerMenu menu) {
        if(expandedContentStep>=3)return true;
        if(expandedContentWait>0&&--expandedContentWait>0)return false;
        if(menu.getSnapshot().selectedCell()!=19)throw new IllegalStateException("Expanded content test did not select actual cell19");
        var screen=(ControllerScreen)mc.screen;
        if(expandedContentStep==0) {
            assertContentState(menu,Items.COPPER_INGOT,98765,0,0);
            contentClick(screen,0,0);
        } else if(expandedContentStep==1) {
            assertContentState(menu,Items.COPPER_INGOT,98701,64,0);
            contentClick(screen,emptyContentIndex(screen,menu),0);
        } else {
            assertContentState(menu,Items.COPPER_INGOT,98765,0,0);
            if(!expandedContentCheckRequested) {
                expandedContentCheckRequested=true;var id=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var drive=(DriveBlockEntity)player.serverLevel().getBlockEntity(controllerPos.above());
                        long amount=StorageScanner.contents(drive.getCellInventory(19)).get(AEItemKey.of(Items.COPPER_INGOT));
                        if(amount!=98765||playerAmount(player,Items.COPPER_INGOT)!=0||!player.containerMenu.getCarried().isEmpty())
                            throw new IllegalStateException("Expanded actual19 content transfer lost copper: "+amount);
                        expandedContentCheckDone=true;
                    } catch(Throwable problem){failure=problem.toString();}
                });
            }
            if(!expandedContentCheckDone)return false;
            System.out.println("ME_STORAGE_SMOKE_EAE_CONTENT PASS actual19 copper98765 -> cursor64 -> actual19 copper98765 via real content-grid clicks");
        }
        expandedContentStep++;expandedContentWait=25;return expandedContentStep>=3;
    }

    /** Use the actual carried vanilla bucket capability through real content-grid clicks. */
    private static boolean verifyBucketClient(Minecraft mc,ControllerMenu menu) {
        if(bucketStep>=8)return true;
        if(bucketWait>0&&--bucketWait>0)return false;
        var screen=(ControllerScreen)mc.screen;var snapshot=menu.getSnapshot();
        int waterIndex=-1;
        for(int i=0;i<snapshot.contents().size();i++)if(snapshot.contents().get(i).key().equals(AEFluidKey.of(Fluids.WATER)))waterIndex=i;
        if(waterIndex<0)throw new IllegalStateException("Water bucket smoke requires visible water in selected fluid cell");
        switch(bucketStep) {
            case 0 -> {
                assertBucketState(menu,23456,null,1,0);
                bucketInventorySlot=findPlayerItemSlot(menu,Items.BUCKET);
                clickUi(screen,screen.smokeSlotRect(bucketInventorySlot));
            }
            case 1 -> {
                assertBucketState(menu,23456,Items.BUCKET,0,0);
                contentClick(screen,waterIndex,0);
            }
            case 2 -> {
                assertBucketState(menu,22456,Items.WATER_BUCKET,0,0);
                captureExtra(mc,"smoke-water-bucket-filled.png",()->contentClick((ControllerScreen)mc.screen,emptyContentIndex((ControllerScreen)mc.screen,menu),1));
            }
            case 3 -> {
                assertBucketState(menu,23456,Items.BUCKET,0,0);
                screen.smokeContentClick(waterIndex,0,true);
            }
            case 4 -> {
                assertBucketState(menu,22456,null,0,1);
                clickUi(screen,screen.smokeSlotRect(findPlayerItemSlot(menu,Items.WATER_BUCKET)));
            }
            case 5 -> {
                assertBucketState(menu,22456,Items.WATER_BUCKET,0,0);
                contentClick(screen,emptyContentIndex(screen,menu),1);
            }
            case 6 -> {
                assertBucketState(menu,23456,Items.BUCKET,0,0);
                clickUi(screen,screen.smokeSlotRect(bucketInventorySlot));
            }
            case 7 -> {
                assertBucketState(menu,23456,null,1,0);
                if(!bucketServerRequested) {
                    bucketServerRequested=true;var id=mc.player.getUUID();
                    mc.getSingleplayerServer().execute(()->{
                        try {
                            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                            var chest=(ChestBlockEntity)player.serverLevel().getBlockEntity(controllerPos.west());
                            var contents=StorageScanner.contents(chest.getCellInventory(0));
                            if(contents.get(AEFluidKey.of(Fluids.WATER))!=23456||contents.get(AEFluidKey.of(Fluids.LAVA))!=777000
                                    ||playerAmount(player,Items.BUCKET)!=1||playerAmount(player,Items.WATER_BUCKET)!=0
                                    ||!player.containerMenu.getCarried().isEmpty())
                                throw new IllegalStateException("Authoritative fluid-cell bucket round trip did not conserve contents/containers");
                            var drops=player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                    new net.minecraft.world.phys.AABB(controllerPos).inflate(8),e->e.getItem().is(Items.BUCKET)||e.getItem().is(Items.WATER_BUCKET));
                            if(!drops.isEmpty())throw new IllegalStateException("Bucket interactions dropped a container");
                            bucketServerDone=true;
                        }catch(Throwable problem){failure=problem.toString();}
                    });
                }
                if(!bucketServerDone)return false;
                System.out.println("ME_STORAGE_SMOKE_BUCKET PASS real left fill/right empty/Shift-left backpack, water23456/lava777000 and exactly one empty bucket restored");
            }
            default -> throw new IllegalStateException("Unknown bucket smoke step");
        }
        bucketStep++;bucketWait=25;return bucketStep>=8;
    }

    private static void assertBucketState(ControllerMenu menu,long water,net.minecraft.world.item.Item carried,long emptyBuckets,long fullBuckets) {
        long actual=menu.getSnapshot().contents().stream().filter(c->c.key().equals(AEFluidKey.of(Fluids.WATER)))
                .mapToLong(Snapshot.Content::amount).sum();
        var cursor=menu.getCarried();
        boolean validCursor=carried==null?cursor.isEmpty():cursor.is(carried)&&cursor.getCount()==1;
        var player=Minecraft.getInstance().player;
        if(actual!=water||!validCursor||playerAmount(player,Items.BUCKET)!=emptyBuckets||playerAmount(player,Items.WATER_BUCKET)!=fullBuckets)
            throw new IllegalStateException("Bucket transfer mismatch step="+bucketStep+" water="+actual+" cursor="+cursor);
    }

    private static int findPlayerItemSlot(ControllerMenu menu,net.minecraft.world.item.Item item) {
        for(int index=10;index<menu.slots.size();index++)if(menu.getSlot(index).getItem().is(item))return index;
        throw new IllegalStateException("Player inventory has no expected item "+ForgeRegistries.ITEMS.getKey(item));
    }

    private static long playerAmount(Player player,net.minecraft.world.item.Item item) {
        long amount=0;for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var stack=player.getInventory().getItem(i);if(stack.is(item))amount+=stack.getCount();
        }return amount;
    }

    private static void assertContentState(ControllerMenu menu,net.minecraft.world.item.Item item,long stored,int carried,long inventory) {
        long actual=menu.getSnapshot().contents().stream().filter(c->c.key().equals(AEItemKey.of(item))).mapToLong(Snapshot.Content::amount).sum();
        var cursor=menu.getCarried();boolean cursorMatches=carried==0?cursor.isEmpty():cursor.is(item)&&cursor.getCount()==carried;
        long actualInventory=playerAmount(Minecraft.getInstance().player,item);
        long player64k=playerAmount(Minecraft.getInstance().player,AEItems.ITEM_CELL_64K.asItem());
        if(player64k!=0)throw new IllegalStateException("Content interaction introduced player64k="+player64k
                +" scope="+contentTransferScope+" step="+contentTransferStep);
        if(actual!=stored||!cursorMatches||actualInventory!=inventory)
            throw new IllegalStateException("Content transfer mismatch scope="+contentTransferScope+" step="+contentTransferStep
                    +" stored="+actual+"/"+stored+" cursor="+cursor+"/"+carried+" inventory="+actualInventory+"/"+inventory);
    }

    private static boolean verifyContentServer(Minecraft mc,long iron,long firstDiamonds,long secondDiamonds,long playerDiamonds) {
        if(!contentServerCheckRequested) {
            contentServerCheckRequested=true;var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                try {
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var drive=(DriveBlockEntity)player.serverLevel().getBlockEntity(controllerPos.east());
                    long actualIron=StorageScanner.contents(drive.getCellInventory(0)).get(AEItemKey.of(Items.IRON_INGOT));
                    long first=StorageScanner.contents(drive.getCellInventory(0)).get(AEItemKey.of(Items.DIAMOND));
                    long second=StorageScanner.contents(drive.getCellInventory(1)).get(AEItemKey.of(Items.DIAMOND));
                    if(actualIron!=iron||first!=firstDiamonds||second!=secondDiamonds||playerAmount(player,Items.IRON_INGOT)!=0
                            ||playerAmount(player,Items.DIAMOND)!=playerDiamonds||!player.containerMenu.getCarried().isEmpty()
                            ||playerAmount(player,AEItems.ITEM_CELL_64K.asItem())!=0
                            ||!drive.getInternalInventory().getStackInSlot(0).is(AEItems.ITEM_CELL_64K.asItem())
                            ||!drive.getInternalInventory().getStackInSlot(1).is(AEItems.ITEM_CELL_16K.asItem()))
                        throw new IllegalStateException("Server content conservation failed: iron="+actualIron+" diamonds="+first+"/"+second);
                    var drops=player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(controllerPos).inflate(8),e->e.getItem().is(Items.IRON_INGOT)||e.getItem().is(Items.DIAMOND));
                    if(!drops.isEmpty())throw new IllegalStateException("Content interactions spawned dropped items");
                    contentServerCheckDone=true;
                } catch(Throwable problem){failure=problem.toString();}
            });
        }
        return contentServerCheckDone;
    }

    /** Observe the exact custom-packet callback, before it can unlock client slots. */
    private static void observeSelectionAcknowledgements(Minecraft mc) {
        var receiver=Network.clientReceiver;
        Network.clientReceiver=message->{
            try {
                if(mc.player!=null && mc.player.containerMenu instanceof ControllerMenu menu && menu.containerId==message.containerId()) {
                    var incoming=message.snapshot();var previous=menu.getSnapshot();
                    boolean changed=!incoming.selectedDevice().equals(previous.selectedDevice())||incoming.selectedCell()!=previous.selectedCell();
                    if(changed && incoming.online() && incoming.editableSlots()>0 && incoming.selectedInfo()!=null && controllerPos!=null) {
                        var position=incoming.selectedInfo().pos();
                        if(position.equals(controllerPos.east())) {
                            requireAcknowledgedSlot(menu,0,AEItems.ITEM_CELL_64K.asItem());
                            requireAcknowledgedSlot(menu,1,AEItems.ITEM_CELL_16K.asItem());
                        } else if(position.equals(controllerPos.above()) && expanded) {
                            boolean secondPage=incoming.selectedCell()>=10;
                            for(int slot=0;slot<10;slot++)requireAcknowledgedSlot(menu,slot,
                                    secondPage?(slot==9?AEItems.ITEM_CELL_64K.asItem():AEItems.ITEM_CELL_1K.asItem()):null);
                        } else if(position.equals(controllerPos.west())) {
                            requireAcknowledgedSlot(menu,0,AEItems.FLUID_CELL_64K.asItem());
                        }
                        synchronizedAcks++;
                        System.out.println("ME_STORAGE_SMOKE_ACK_ORDER revision="+incoming.revision()+" cell="+incoming.selectedCell()+" physical slots current before selection unlock");
                    }
                }
            } catch(Throwable problem) {failure=problem.toString();}
            receiver.accept(message);
        };
    }

    private static void requireAcknowledgedSlot(ControllerMenu menu,int slot,net.minecraft.world.item.Item expected) {
        var actual=menu.getSlot(slot).getItem();
        if(expected==null?!actual.isEmpty():!actual.is(expected))
            throw new IllegalStateException("Selection acknowledgement arrived before physical slot "+slot+" update: "+actual);
    }

    private static boolean verifyNavigationSync(ControllerMenu menu) {
        if(navigationSyncStep>=9)return true;
        if(navigationSyncWait>0&&--navigationSyncWait>0)return false;
        String target=navigationSyncStep==8?"":expanded?secondTreeId:(navigationSyncStep%2==0?normalTreeId:secondTreeId);
        int cell=navigationSyncStep==8?-1:expanded?(navigationSyncStep%2==0?19:0):0;
        if(!navigationSyncRequested) {
            menu.request(target,cell,0,0,"","",true);navigationSyncRequested=true;navigationSyncWait=15;return false;
        }
        assertTreeSelection(menu.getSnapshot(),target,cell);
        navigationSyncRequested=false;navigationSyncStep++;
        if(navigationSyncStep==9) {
            if(synchronizedAcks<8)throw new IllegalStateException("Page cycling did not exercise ordered physical-slot acknowledgements");
            System.out.println("ME_STORAGE_SMOKE_ACK_ORDER PASS repeated real page/device changes; slot state checked inside custom snapshot receiver before unlock");
        }
        return navigationSyncStep>=9;
    }

    private static void assertAmountOrder(Snapshot snapshot) {
        for(int i=1;i<snapshot.contents().size();i++)if(snapshot.contents().get(i-1).amount()<snapshot.contents().get(i).amount())
            throw new IllegalStateException("Content grid amount order is not descending");
    }

    /** Check actual baked atlas sprites, not just the existence of loose PNG files. */
    private static void verifyModelSprites(Minecraft mc) {
        int states=0,total=0;var sprites=new java.util.HashSet<ResourceLocation>();
        for(var direction:java.util.List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            for(boolean lit:new boolean[]{false,true}) {
                var state=MEStorageController.CONTROLLER.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,direction)
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,lit);
                total+=inspectModel(mc.getBlockRenderer().getBlockModel(state),state,"block "+direction+" lit="+lit,sprites);states++;
            }
        }
        var item=new ItemStack(MEStorageController.CONTROLLER_ITEM.get());
        total+=inspectModel(mc.getItemRenderer().getModel(item,mc.level,mc.player,0),null,"item",sprites);
        System.out.println("ME_STORAGE_SMOKE_MODEL_ATLAS PASS blockStates="+states+" itemModels=1 quads="+total+" distinctSprites="+sprites.size()+" no missingno");
    }

    private static int inspectModel(net.minecraft.client.resources.model.BakedModel model,
                                    net.minecraft.world.level.block.state.BlockState state,String label,
                                    java.util.Set<ResourceLocation> sprites) {
        int count=0;
        for(int side=0;side<7;side++) {
            Direction direction=side==6?null:Direction.values()[side];
            for(var quad:model.getQuads(state,direction,net.minecraft.util.RandomSource.create(42))) {
                var name=quad.getSprite().contents().name();
                if(name.equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                    throw new IllegalStateException("Controller model uses missing atlas sprite: "+label+" face="+direction);
                sprites.add(name);count++;
            }
        }
        if(count==0)throw new IllegalStateException("Controller model has no baked quads: "+label);
        return count;
    }

    private static void clearCaptureHover(Minecraft mc) {
        if (FMLEnvironment.production || !ENABLED || DEMO)
            throw new IllegalStateException("Capture cursor synchronization is restricted to development smoke tests");
        var window = mc.getWindow();
        double safeX = hoverCaptureRect==null?2.0:hoverCaptureRect.centerX()*window.getScreenWidth()/window.getGuiScaledWidth();
        double safeY = hoverCaptureRect==null?2.0:hoverCaptureRect.centerY()*window.getScreenHeight()/window.getGuiScaledHeight();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(window.getWindow(), safeX, safeY);
        // Background GLFW windows may not dispatch a cursor callback. Named fields are available in
        // the development runtime; bypassing onMove also avoids camera/drag side effects.
        try {
            if (captureMouseX == null) {
                captureMouseX = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                captureMouseY = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                captureMouseX.setAccessible(true);
                captureMouseY.setAccessible(true);
                System.out.println("ME_STORAGE_SMOKE_CAPTURE_CURSOR using cached development MouseHandler coordinates");
            }
            captureMouseX.setDouble(mc.mouseHandler, safeX);
            captureMouseY.setDouble(mc.mouseHandler, safeY);
        } catch (ReflectiveOperationException problem) {
            throw new IllegalStateException("Unable to synchronize development capture cursor", problem);
        }
        if(mc.screen!=null) {
            // Match MouseHandler/GameRenderer: GLFW positions use window dimensions, not framebuffer dimensions.
            mc.screen.mouseMoved(safeX * window.getGuiScaledWidth() / window.getScreenWidth(),
                    safeY * window.getGuiScaledHeight() / window.getScreenHeight());
            // Remove only button focus; a live search field retains its editing state.
            if(mc.screen.getFocused() instanceof net.minecraft.client.gui.components.Button)mc.screen.setFocused(null);
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button)button.setFocused(false);
        }
        if (mc.mouseHandler.xpos() != safeX || mc.mouseHandler.ypos() != safeY)
            throw new IllegalStateException("Capture cursor did not reach the safe window position");
    }

    /** Native GuideME indexing/tooltip plus actual guide-button navigation while carrying a real stack. */
    private static void verifyInGameGuide(Minecraft mc) {
        clearCaptureHover(mc);if(drainExtraCapture(mc))return;
        if(guideWait>0&&--guideWait>0)return;
        if(mc.player.containerMenu!=guideReturnMenu)throw new IllegalStateException("Opening the guide replaced the live controller menu");
        var menu=guideReturnMenu;
        switch(guideStep) {
            case 0 -> {
                var guide=guideme.Guides.getById(ControllerGuide.GUIDE_ID);
                if(guide==null)throw new IllegalStateException("AE2 guide is not registered");
                var anchor=guide.getIndex(guideme.indices.ItemIndex.class).get(ForgeRegistries.ITEMS.getKey(MEStorageController.CONTROLLER_ITEM.get()));
                if(anchor==null||!anchor.pageId().equals(ControllerGuide.PAGE_ID))throw new IllegalStateException("GuideME item index does not resolve the controller article");
                var parsed=guide.getParsedPage(ControllerGuide.PAGE_ID);var page=guide.getPage(ControllerGuide.PAGE_ID);
                if(parsed==null||!"zh_cn".equals(parsed.getLanguage())||page==null||page.document()==null||page.document().getChildren().isEmpty())
                    throw new IllegalStateException("Simplified Chinese guide page did not parse and compile: language="+(parsed==null?"missing":parsed.getLanguage()));
                String body=page.document().getTextContent();
                if(!body.contains("ME存储控制器")||!body.contains("1000")||!body.contains("256"))
                    throw new IllegalStateException("Compiled controller guide is missing its title or later instructions");
                String expected=guideme.internal.GuidebookText.HoldToShow.text(guideme.internal.hotkey.OpenGuideHotkey.getHotkey().getTranslatedKeyMessage()).getString();
                var tooltip=new ItemStack(MEStorageController.CONTROLLER_ITEM.get()).getTooltipLines(mc.player,net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                if(tooltip.stream().map(net.minecraft.network.chat.Component::getString).noneMatch(line->line.contains(expected))) {
                    if(++guideTooltipTries<20)return;
                    throw new IllegalStateException("Native GuideME hold-key tooltip missing: expected="+expected+" actual="+tooltip);
                }
                System.out.println("ME_STORAGE_SMOKE_GUIDE_INDEX PASS native item index, parsed zh_cn and compiled document; native tooltip="+expected+" (physical G key not injected)");
                verifySidebarBounds(guideReturnScreen);
                guideDiamondSlot=findPlayerItemSlot(menu,Items.DIAMOND);
                if(menu.getSlot(guideDiamondSlot).getItem().getCount()!=32||!menu.getCarried().isEmpty())throw new IllegalStateException("Guide round trip requires original 32-diamond fixture");
                hoverCaptureRect=guideReturnScreen.smokeGuideRect();
                captureExtra(mc,"smoke-sidebar-hover.png",()->clickUi(guideReturnScreen,guideReturnScreen.smokeSlotRect(guideDiamondSlot)));
            }
            case 1 -> {
                if(!menu.getCarried().is(Items.DIAMOND)||menu.getCarried().getCount()!=32||menu.getSlot(guideDiamondSlot).hasItem())
                    throw new IllegalStateException("Guide test did not pick up the real 32-diamond stack");
                clickUi(guideReturnScreen,guideReturnScreen.smokeGuideRect());
            }
            case 2 -> {
                requireGuideScreen(mc);captureExtra(mc,"smoke-guide-zh_cn.png");
            }
            case 3 -> {
                var screen=requireGuideScreen(mc);
                if(!screen.mouseScrolled(mc.getWindow().getGuiScaledWidth()*.65,mc.getWindow().getGuiScaledHeight()*.5,-12))
                    throw new IllegalStateException("Guide article did not accept actual wheel input");
            }
            case 4 -> {
                requireGuideScreen(mc);
                captureExtra(mc,"smoke-guide-zh_cn-scrolled.png",()->{
                    var screen=requireGuideScreen(mc);
                    if(!screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0))throw new IllegalStateException("Guide did not accept Escape");
                });
            }
            case 5 -> {
                if(mc.screen!=guideReturnScreen)throw new IllegalStateException("Escape did not restore the original controller screen");
                assertTreeSelection(menu.getSnapshot(),guideScopeDevice,guideScopeCell);
                if(!menu.getCarried().is(Items.DIAMOND)||menu.getCarried().getCount()!=32)throw new IllegalStateException("Guide return changed the carried diamond stack");
                clickUi(guideReturnScreen,guideReturnScreen.smokeSlotRect(guideDiamondSlot));
            }
            case 6 -> {
                var stack=menu.getSlot(guideDiamondSlot).getItem();
                if(!menu.getCarried().isEmpty()||!stack.is(Items.DIAMOND)||stack.getCount()!=32||playerAmount(mc.player,Items.DIAMOND)!=32)
                    throw new IllegalStateException("Guide round trip did not restore exactly 32 diamonds to the original inventory slot");
                assertTreeSelection(menu.getSnapshot(),guideScopeDevice,guideScopeCell);
                System.out.println("ME_STORAGE_SMOKE_GUIDE_RETURN PASS actual guide-button/Escape round trip preserves original screen, menu, scope and 32 carried diamonds; two real article screenshots");
                guideVerified=true;phase=2;return;
            }
        }
        guideStep++;guideWait=20;
    }

    private static guideme.internal.screen.GuideScreen requireGuideScreen(Minecraft mc) {
        if(!(mc.screen instanceof guideme.internal.screen.GuideScreen screen)
                ||!ControllerGuide.PAGE_ID.equals(screen.getCurrentPageId())||screen.getReturnToOnClose()!=guideReturnScreen)
            throw new IllegalStateException("Guide button opened the wrong page or lost its return screen");
        if(mc.player.containerMenu!=guideReturnMenu||!guideReturnMenu.getCarried().is(Items.DIAMOND)||guideReturnMenu.getCarried().getCount()!=32)
            throw new IllegalStateException("Guide screen changed the active menu or carried stack");
        return screen;
    }

    private static void verifyTreeMemory(Minecraft mc) {
        clearCaptureHover(mc);
        if(drainExtraCapture(mc))return;
        if(treeMemoryWait>0&&--treeMemoryWait>0)return;
        ControllerScreen screen=mc.screen instanceof ControllerScreen value?value:null;
        String dimension="dim:minecraft:overworld",device="dev:"+normalTreeId;
        switch(treeMemoryStep) {
            case 0 -> {if(screen==null)return;screen.smokeRevealDevice(normalTreeId);}
            case 1 -> {
                if(screen.smokeTreeBranchOpen(device))clickUi(screen,screen.smokeDeviceRect(normalTreeId,true));
            }
            case 2 -> {
                var viewport=screen.smokeTreeViewport();screen.mouseScrolled(viewport.centerX(),viewport.centerY(),1000);
            }
            case 3 -> {if(screen.smokeTreeBranchOpen(dimension))clickUi(screen,screen.smokeTreeBranchRect(dimension));}
            case 4 -> {if(screen.smokeTreeBranchOpen("root"))clickUi(screen,screen.smokeTreeBranchRect("root"));}
            case 5 -> {
                assertRememberedBranches(screen);
                screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            }
            case 6 -> {
                assertRememberedBranches(screen);treeMemoryScope=screen.smokeTreeStateScope();treeMemoryOldScreen=screen;
                if(treeMemoryScope==null||treeMemoryScope.isBlank())throw new IllegalStateException("Tree memory scope was empty");
                captureExtra(mc,"smoke-tree-before-close.png");
            }
            case 7 -> {
                if(!screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0))throw new IllegalStateException("Tree memory close did not handle Escape");
            }
            case 8 -> {
                if(mc.screen instanceof ControllerScreen||mc.player.containerMenu instanceof ControllerMenu)return;
                var uuid=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    try {
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                        var block=(ControllerBlockEntity)player.serverLevel().getBlockEntity(controllerPos);
                        NetworkHooks.openScreen(player,block,controllerPos);
                    }catch(Throwable problem){failure=problem.toString();}
                });
            }
            case 9 -> {
                if(screen==null||!(mc.player.containerMenu instanceof ControllerMenu menu)||!menu.getSnapshot().online())return;
                if(screen==treeMemoryOldScreen||!treeMemoryScope.equals(screen.smokeTreeStateScope()))throw new IllegalStateException("Tree reopen did not create a fresh screen in the same persistent scope");
                assertRememberedBranches(screen);
                captureExtra(mc,"smoke-tree-restored-collapse.png");
            }
            case 10 -> {assertRememberedBranches(screen);clickUi(screen,screen.smokeTreeBranchRect("root"));}
            case 11 -> {
                if(screen.smokeTreeBranchOpen(dimension)||screen.smokeTreeBranchOpen(device))throw new IllegalStateException("Opening root erased independently remembered child folds");
                clickUi(screen,screen.smokeTreeBranchRect(dimension));screen.smokeRevealDevice(normalTreeId);
            }
            case 12 -> {
                if(screen.smokeTreeBranchOpen(device))throw new IllegalStateException("Opening dimension erased remembered device fold");
                clickUi(screen,screen.smokeDeviceRect(normalTreeId,true));
            }
            case 13 -> {
                if(!screen.smokeTreeBranchOpen(device))throw new IllegalStateException("Remembered branch cannot be expanded by a new real click");
                screen.smokeExpandDevice(secondTreeId);
                System.out.println("ME_STORAGE_SMOKE_TREE_MEMORY PASS real root/dimension/device collapse survives resize and close/new-screen reopen; independent children remain folded until clicked");
                treeMemoryVerified=true;phase=2;return;
            }
            default -> throw new IllegalStateException("Unknown tree memory stage "+treeMemoryStep);
        }
        treeMemoryStep++;treeMemoryWait=treeMemoryStep==9?30:10;
    }
    private static void assertRememberedBranches(ControllerScreen screen) {
        for(String branch:new String[]{"root","dim:minecraft:overworld","dev:"+normalTreeId})
            if(screen.smokeTreeBranchOpen(branch))throw new IllegalStateException("Tree memory lost collapsed branch "+branch+": "+screen.smokeTreeExpansionState());
    }

    private static void verifyFolders(Minecraft mc) {
        clearCaptureHover(mc);if(drainExtraCapture(mc))return;
        if(folderWait>0&&--folderWait>0)return;
        if(!(mc.screen instanceof ControllerScreen screen)||!(mc.player.containerMenu instanceof ControllerMenu menu)||!menu.getSnapshot().online())return;
        String cell="cell:"+normalTreeId+":0",next="cell:"+normalTreeId+":1";
        switch(folderStep) {
            case 0 -> {folderPhysicalCheck(mc,true);screen.smokeRevealCell(normalTreeId,0);}
            case 1 -> {
                if(!folderCheckDone||!verifyFolderTreeSelection(screen))return;
            }
            case 2 -> {
                folderDiamondSlot=findPlayerItemSlot(menu,Items.DIAMOND);
                if(menu.getSlot(folderDiamondSlot).getItem().getCount()!=32||!menu.getCarried().isEmpty())throw new IllegalStateException("Folder modal test requires original 32 diamonds");
                clickUi(screen,screen.smokeSlotRect(folderDiamondSlot));
            }
            case 3 -> {
                requireFolderDiamonds(menu);screen.smokeTreeClick(cell,1,false,false);
                captureExtra(mc,"smoke-folder-context.png");
            }
            case 4 -> clickUi(screen,screen.smokeFolderActionRect("group_new"));
            case 5 -> {typeFolderName(screen,"Smoke Main");captureExtra(mc,"smoke-folder-create.png",()->clickUi(screen,screen.smokeFolderSubmitRect()));}
            case 6 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                smokeParentFolder=folderNamed(menu,"Smoke Main");requireFolderDiamonds(menu);
                clickUi(screen,screen.smokeSlotRect(folderDiamondSlot));screen.smokeRevealFolder(smokeParentFolder);
            }
            case 7 -> {
                if(!menu.getCarried().isEmpty()||menu.getSlot(folderDiamondSlot).getItem().getCount()!=32)throw new IllegalStateException("Folder creation changed carried/player diamonds");
                screen.smokeTreeClick("folder:"+smokeParentFolder,1,false,false);
            }
            case 8 -> clickUi(screen,screen.smokeFolderActionRect("new_child"));
            case 9 -> {typeFolderName(screen,"Smoke Child");clickUi(screen,screen.smokeFolderSubmitRect());}
            case 10 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                smokeChildFolder=folderNamed(menu,"Smoke Child");screen.smokeRevealFolder(smokeChildFolder);
            }
            case 11 -> screen.smokeTreeClick("folder:"+smokeChildFolder,1,false,false);
            case 12 -> clickUi(screen,screen.smokeFolderActionRect("rename"));
            case 13 -> {typeFolderName(screen,"Smoke Renamed");clickUi(screen,screen.smokeFolderSubmitRect());}
            case 14 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                if(!folderNamed(menu,"Smoke Renamed").equals(smokeChildFolder))throw new IllegalStateException("Rename replaced folder identity");
                screen.smokeRevealCell(normalTreeId,0);
            }
            case 15 -> screen.smokeTreeClick(cell,1,false,false);
            case 16 -> {clickUi(screen,screen.smokeFolderActionRect("move"));screen.smokeRevealFolderTarget(smokeChildFolder);}
            case 17 -> {clickUi(screen,screen.smokeFolderTargetRect(smokeChildFolder));captureExtra(mc,"smoke-folder-move.png",()->clickUi(screen,screen.smokeFolderSubmitRect()));}
            case 18 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                var child=menu.getFolderView().folders().stream().filter(f->f.id().equals(smokeChildFolder)).findFirst().orElseThrow();
                if(!smokeParentFolder.equals(child.parent())||child.members().stream().noneMatch(m->m.deviceId().equals(normalTreeId)&&m.cell()==0))throw new IllegalStateException("Nested folder move did not persist actual slot membership");
                folderPhysicalCheck(mc,false);screen.smokeRevealFolder(smokeParentFolder);
            }
            case 19 -> {if(!folderCheckDone)return;clickUi(screen,screen.smokeFolderRect(smokeParentFolder,false));}
            case 20 -> {
                if(!menu.getSnapshot().selectedDevice().equals("folder:"+smokeParentFolder))return;
                long iron=menu.getSnapshot().contents().stream().filter(c->c.key().equals(AEItemKey.of(Items.IRON_INGOT))).mapToLong(Snapshot.Content::amount).sum();
                var expected=java.util.Set.of(AEItemKey.of(Items.IRON_INGOT),AEItemKey.of(Items.GOLD_INGOT),AEItemKey.of(Items.DIAMOND),AEItemKey.of(Items.COBBLESTONE),AEItemKey.of(Items.OAK_LOG));
                if(iron!=12345||menu.getSnapshot().contents().size()!=5||menu.getSnapshot().contents().stream().anyMatch(c->!expected.contains(c.key())))throw new IllegalStateException("Parent folder aggregation includes nonmember storage or lost nested cell contents");
                captureExtra(mc,"smoke-folder-nested-aggregate.png",()->screen.smokeContentClick(folderIronIndex(menu),0,false));
            }
            case 21 -> {
                if(!menu.getCarried().is(Items.IRON_INGOT)||menu.getCarried().getCount()!=64)throw new IllegalStateException("Actual folder content click did not extract exactly 64 iron");
                screen.smokeContentClick(folderIronIndex(menu),0,false);
            }
            case 22 -> {
                if(!menu.getCarried().isEmpty())throw new IllegalStateException("Folder insertion did not return held iron");
                folderPhysicalCheck(mc,false);screen.smokeRevealFolder(smokeChildFolder);
            }
            case 23 -> {
                if(!folderCheckDone||!verifyFolderBackpackShift(mc,screen,menu))return;
                screen.smokeTreeClick("folder:"+smokeChildFolder,1,false,false);
            }
            case 24 -> {clickUi(screen,screen.smokeFolderActionRect("delete"));captureExtra(mc,"smoke-folder-delete-confirm.png");}
            case 25 -> clickUi(screen,screen.smokeFolderSubmitRect());
            case 26 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                if(menu.getFolderView().folders().stream().anyMatch(f->f.id().equals(smokeChildFolder)))throw new IllegalStateException("Deleted child remains in shared view");
                var parent=menu.getFolderView().folders().stream().filter(f->f.id().equals(smokeParentFolder)).findFirst().orElseThrow();
                if(parent.members().stream().noneMatch(m->m.deviceId().equals(normalTreeId)&&m.cell()==0))throw new IllegalStateException("Deleting child failed to promote members");
                screen.smokeRevealFolder(smokeParentFolder);
            }
            case 27 -> {
                if(!verifyFolderWholeAndSeparateSlot(mc,screen,menu))return;
                screen.smokeTreeClick("folder:"+smokeParentFolder,1,false,false);
            }
            case 28 -> clickUi(screen,screen.smokeFolderActionRect("delete"));
            case 29 -> clickUi(screen,screen.smokeFolderSubmitRect());
            case 30 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return;
                if(menu.getFolderView().folders().stream().anyMatch(f->f.id().equals(smokeParentFolder)||f.id().equals(smokeChildFolder)))throw new IllegalStateException("Folder cleanup did not remove both test folders");
                folderPhysicalCheck(mc,false);clickUi(screen,screen.smokeRootRect());
            }
            case 31 -> {
                if(!folderCheckDone||!menu.getSnapshot().selectedDevice().isEmpty())return;
                System.out.println("ME_STORAGE_SMOKE_FOLDERS PASS real Ctrl/Shift selection, carried-diamond modal CRUD, nested move/rename/delete, whole-device plus separately grouped slot reassignment, parent scoped iron roundtrip and acknowledged backpack QUICK_MOVE; every physical cell NBT unchanged before transfers and after roundtrip/cleanup");
                foldersVerified=true;phase=2;return;
            }
            default -> throw new IllegalStateException("Unknown folder UI stage "+folderStep);
        }
        folderStep++;folderWait=20;
    }
    private static boolean verifyFolderTreeSelection(ControllerScreen screen) {
        String first="cell:"+normalTreeId+":0",second="cell:"+normalTreeId+":1";
        switch(folderSelectionStep) {
            case 0 -> screen.smokeTreeClick(first,0,false,false);
            // Let the selection acknowledgement reveal its own row before scrolling to the next target.
            case 1 -> screen.smokeRevealCell(normalTreeId,1);
            case 2 -> {
                screen.smokeTreeClick(second,0,true,false);
                if(!screen.smokeFolderSelectionKeys().containsAll(java.util.Set.of(first,second)))throw new IllegalStateException("Real Ctrl-click did not multi-select tree slots");
            }
            case 3 -> screen.smokeRevealCell(normalTreeId,0);
            case 4 -> screen.smokeTreeClick(first,0,false,false);
            case 5 -> screen.smokeRevealCell(normalTreeId,1);
            case 6 -> {
                screen.smokeTreeClick(second,0,false,true);
                if(!screen.smokeFolderSelectionKeys().containsAll(java.util.Set.of(first,second)))throw new IllegalStateException("Real Shift-click did not range-select tree slots");
            }
            case 7 -> screen.smokeRevealCell(normalTreeId,0);
            case 8 -> screen.smokeTreeClick(first,0,false,false);
            case 9 -> {return true;}
        }
        folderSelectionStep++;folderWait=20;return false;
    }
    private static boolean verifyFolderBackpackShift(Minecraft mc,ControllerScreen screen,ControllerMenu menu) {
        switch(folderShiftStep) {
            case 0 -> screen.smokeContentClick(folderIronIndex(menu),0,true);
            case 1 -> {
                int slot=findPlayerItemSlot(menu,Items.IRON_INGOT);
                if(menu.getSlot(slot).getItem().getCount()!=64||!menu.getCarried().isEmpty())throw new IllegalStateException("Folder Shift extraction did not place exactly 64 iron in backpack");
                mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,0,ClickType.QUICK_MOVE,mc.player);
            }
            case 2 -> {
                for(int slot=10;slot<menu.slots.size();slot++)if(menu.getSlot(slot).getItem().is(Items.IRON_INGOT))throw new IllegalStateException("Acknowledged folder rejected backpack QUICK_MOVE insertion");
                if(!menu.getCarried().isEmpty())throw new IllegalStateException("Folder backpack insertion changed carried stack");
                folderPhysicalCheck(mc,false);
            }
            case 3 -> {return folderCheckDone;}
        }
        folderShiftStep++;folderWait=20;return false;
    }
    private static boolean verifyFolderWholeAndSeparateSlot(Minecraft mc,ControllerScreen screen,ControllerMenu menu) {
        String device="dev:"+normalTreeId,cell="cell:"+normalTreeId+":0";
        switch(folderGroupingStep) {
            case 0 -> screen.smokeRevealDevice(normalTreeId);
            case 1 -> screen.smokeTreeClick(device,0,false,false);
            case 2 -> screen.smokeRevealCell(normalTreeId,0);
            case 3 -> {
                screen.smokeTreeClick(cell,0,true,false);
                if(!screen.smokeFolderSelectionKeys().containsAll(java.util.Set.of(device,cell)))throw new IllegalStateException("Whole drive and separately grouped slot were not both selected");
                screen.smokeTreeClick(cell,1,false,false);
            }
            case 4 -> clickUi(screen,screen.smokeFolderActionRect("group_new"));
            case 5 -> {typeFolderName(screen,"Smoke Whole And Slot");clickUi(screen,screen.smokeFolderSubmitRect());}
            case 6 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return false;
                smokeGroupedFolder=folderNamed(menu,"Smoke Whole And Slot");
                var target=menu.getFolderView().folders().stream().filter(f->f.id().equals(smokeGroupedFolder)).findFirst().orElseThrow();
                var origin=menu.getFolderView().folders().stream().filter(f->f.id().equals(smokeParentFolder)).findFirst().orElseThrow();
                var whole=new dev.mestorage.controller.folder.FolderBook.MemberRef(normalTreeId,-1);
                var single=new dev.mestorage.controller.folder.FolderBook.MemberRef(normalTreeId,0);
                if(!target.members().containsAll(java.util.List.of(whole,single))||origin.members().contains(single))throw new IllegalStateException("Grouping whole drive discarded explicitly selected slot from a different folder");
                folderPhysicalCheck(mc,false);screen.smokeRevealFolder(smokeGroupedFolder);
            }
            case 7 -> {if(!folderCheckDone)return false;screen.smokeTreeClick("folder:"+smokeGroupedFolder,1,false,false);}
            case 8 -> clickUi(screen,screen.smokeFolderActionRect("delete"));
            case 9 -> clickUi(screen,screen.smokeFolderSubmitRect());
            case 10 -> {
                if(menu.isFolderPending()||screen.smokeFolderOverlayOpen())return false;
                if(menu.getFolderView().folders().stream().anyMatch(f->f.id().equals(smokeGroupedFolder)))throw new IllegalStateException("Whole-plus-slot test folder cleanup failed");
                folderPhysicalCheck(mc,false);screen.smokeRevealFolder(smokeParentFolder);
            }
            case 11 -> {return folderCheckDone;}
        }
        folderGroupingStep++;folderWait=20;return false;
    }
    private static void requireFolderDiamonds(ControllerMenu menu) {
        if(!menu.getCarried().is(Items.DIAMOND)||menu.getCarried().getCount()!=32)throw new IllegalStateException("Folder overlay lost or changed held 32 diamonds");
    }
    private static void typeFolderName(ControllerScreen screen,String name) {
        if(screen.smokeFolderNameRect()==null)throw new IllegalStateException("Folder name dialog did not open");
        for(char c:name.toCharArray())if(!screen.charTyped(c,0))throw new IllegalStateException("Folder name rejected actual character input");
    }
    private static java.util.UUID folderNamed(ControllerMenu menu,String name) {
        var matches=menu.getFolderView().folders().stream().filter(f->f.name().equals(name)).toList();
        if(matches.size()!=1)throw new IllegalStateException("Expected one shared folder named "+name+", got "+matches.size());
        return matches.get(0).id();
    }
    private static int folderIronIndex(ControllerMenu menu) {
        for(int i=0;i<menu.getSnapshot().contents().size();i++)if(menu.getSnapshot().contents().get(i).key().equals(AEItemKey.of(Items.IRON_INGOT)))return i;
        throw new IllegalStateException("Folder has no visible iron item tile");
    }
    private static void folderPhysicalCheck(Minecraft mc,boolean baseline) {
        folderCheckDone=false;var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                var controller=(ControllerBlockEntity)player.serverLevel().getBlockEntity(controllerPos);
                var cells=new java.util.TreeMap<String,net.minecraft.nbt.CompoundTag>();
                for(var device:StorageScanner.discover(controller.getMainNode().getGrid()))if(device.cells()!=null)
                    for(int slot=0;slot<device.cells().getSlots();slot++)cells.put(device.id()+"/"+slot,device.cells().getStackInSlot(slot).save(new net.minecraft.nbt.CompoundTag()));
                if(baseline)folderPhysicalBaseline=java.util.Map.copyOf(cells);
                else if(!cells.equals(folderPhysicalBaseline))throw new IllegalStateException("Folder CRUD or roundtrip changed physical cell inventory/content NBT");
                folderCheckDone=true;
            }catch(Throwable problem){failure=problem.toString();}
        });
    }

    private static void verifySidebarBounds(ControllerScreen screen) {
        var controls=screen.smokeSidebarRects();var panel=screen.smokePanelRect();
        if(controls.size()!=7)throw new IllegalStateException("Controller sidebar must contain seven controls");
        for(int i=0;i<controls.size();i++) {
            var rect=controls.get(i);
            if(rect.width()!=16||rect.height()!=16||rect.x()!=panel.x()+3||rect.y()!=panel.y()+9+20*i
                    ||rect.x()<panel.x()||rect.y()<panel.y()||rect.x()+16>panel.x()+panel.width()||rect.y()+16>panel.y()+panel.height())
                throw new IllegalStateException("Sidebar control spacing or panel bounds incorrect at "+i+": "+rect);
        }
    }

    private static void verifyAutoRestore(Minecraft mc) {
        clearCaptureHover(mc);
        if(drainExtraCapture(mc))return;
        if (mc.options.guiScale().get() != 0) throw new IllegalStateException("Auto option changed during close/reopen");
        if (autoRestoreStage == 0) {
            int beforeClose=mc.getWindow().calculateScale(0,mc.isEnforceUnicode());
            if(mc.getWindow().getGuiScale()!=beforeClose)throw new IllegalStateException("Open controller changed native Auto scale before close");
            mc.player.closeContainer();
            int vanillaScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
            if (mc.getWindow().getGuiScale() != vanillaScale)
                throw new IllegalStateException("Closing controller changed vanilla Auto scale: actual="
                        + mc.getWindow().getGuiScale() + " expected=" + vanillaScale);
            System.out.println("ME_STORAGE_SMOKE_AUTO_RESTORE PASS open/closed controller preserved native Window scale=" + vanillaScale + "; option=0");
            autoRestoreStage = 1;
            return;
        }
        if (autoRestoreStage == 1 && ++autoRestoreTicks >= 8) {
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    var block = (ControllerBlockEntity) player.serverLevel().getBlockEntity(controllerPos);
                    NetworkHooks.openScreen(player, block, controllerPos);
                } catch (Throwable problem) { failure = problem.toString(); }
            });
            autoRestoreStage = 2;
            return;
        }
        if (autoRestoreStage == 2 && mc.screen instanceof ControllerScreen
                && mc.player.containerMenu instanceof ControllerMenu menu && menu.getSnapshot().online()) {
            int vanillaScale=mc.getWindow().calculateScale(0,mc.isEnforceUnicode());
            if (mc.getWindow().getGuiScale() != vanillaScale)
                throw new IllegalStateException("Reopened controller changed native Auto GUI scale");
            System.out.println("ME_STORAGE_SMOKE_AUTO_REOPEN PASS native scale="+vanillaScale+"; option=0");
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);
            autoRestoreStage=3;autoRestoreTicks=20;return;
        }
        if(autoRestoreStage<3||!(mc.screen instanceof ControllerScreen screen)
                ||!(mc.player.containerMenu instanceof ControllerMenu menu))return;
        if(autoRestoreTicks>0&&--autoRestoreTicks>0)return;
        if(mc.getWindow().getGuiScale()!=mc.getWindow().calculateScale(0,mc.isEnforceUnicode()))
            throw new IllegalStateException("Native GUI scale changed during narrow-window tests");
        var snapshot=menu.getSnapshot();int visibleSlot=expanded?9:0;
        String device=treeDeviceId(snapshot,expanded?"expatternprovider:ex_drive":"ae2:drive");
        switch(autoRestoreStage) {
            case 3 -> {
                if(mc.getWindow().getScreenWidth()!=960||mc.getWindow().getScreenHeight()!=720
                        ||mc.getWindow().getGuiScaledWidth()!=320||mc.getWindow().getGuiScaledHeight()!=240)
                    throw new IllegalStateException("Narrow test requires real 960x720 Auto=3 window");
                verifyVisibleBounds(mc,screen,menu,true);
                if(screen.smokeTreeViewport()!=null||screen.smokeContentScrollRect()==null)
                    throw new IllegalStateException("Narrow terminal must initially show contents");
                captureExtra(mc,"smoke-auto960-contents.png",()->dragScrollbar((ControllerScreen)mc.screen,true));
            }
            case 4 -> {
                if(screen.smokeContentPending()||screen.smokeContentScrollPosition()!=screen.smokeContentScrollMax())
                    throw new IllegalStateException("Real scrollbar drag did not reach acknowledged last content position");
                dragScrollbar(screen,false);
            }
            case 5 -> {
                if(screen.smokeContentPending()||screen.smokeContentScrollPosition()!=0)
                    throw new IllegalStateException("Real scrollbar drag did not return to acknowledged top");
                clickUi(screen,screen.smokeCollapseRect());screen.smokeRevealCell(device,expanded?19:0);
            }
            case 6 -> {
                if(screen.smokeTreeViewport()==null||screen.smokeContentScrollRect()!=null)
                    throw new IllegalStateException("Narrow tree toggle did not switch visible panes");
                verifyVisibleBounds(mc,screen,menu,false);
                clickUi(screen,screen.smokeCellRect(device,expanded?19:0));
            }
            case 7 -> {
                assertTreeSelection(snapshot,device,expanded?19:0);
                if(!menu.canSendClick(visibleSlot,ClickType.PICKUP))throw new IllegalStateException("Narrow tree remote cell is not editable");
                verifyVisibleBounds(mc,screen,menu,false);
                captureExtra(mc,"smoke-auto960-tree-cell.png",()->clickUi((ControllerScreen)mc.screen,((ControllerScreen)mc.screen).smokeSlotRect(visibleSlot)));
            }
            case 8 -> {
                if(!menu.getCarried().is(AEItems.ITEM_CELL_64K.asItem())||!menu.getSlot(visibleSlot).getItem().isEmpty()||clientCellCount(menu)!=1)
                    throw new IllegalStateException("Narrow tree real cell pickup did not conserve one cell");
                clickUi(screen,screen.smokeSlotRect(visibleSlot));
            }
            case 9 -> {
                var expected=AEItemKey.of(expanded?Items.COPPER_INGOT:Items.IRON_INGOT);long amount=expanded?98765:12345;
                if(!menu.getCarried().isEmpty()||!menu.getSlot(visibleSlot).getItem().is(AEItems.ITEM_CELL_64K.asItem())||clientCellCount(menu)!=1
                        ||snapshot.contents().stream().noneMatch(c->c.key().equals(expected)&&c.amount()==amount))
                    throw new IllegalStateException("Narrow tree cell reinsertion lost cell or stored contents");
                clickUi(screen,screen.smokeCollapseRect());clickUi(screen,screen.smokeContentSearchRect());
                String query=expanded?"铜锭":"铁锭";for(char c:query.toCharArray())screen.charTyped(c,0);
            }
            case 10 -> {
                String query=expanded?"铜锭":"铁锭";
                if(!(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox field)
                        ||!field.getValue().equals(query)||snapshot.contents().size()!=1)
                    throw new IllegalStateException("Narrow search did not retain typed query/focus: "+searchFocusState(screen));
                field.moveCursorTo(1);
                String beforeResize=searchFocusState(screen);
                screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                if(mc.screen!=screen||!(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox rebuilt)
                        ||!rebuilt.isFocused()||!rebuilt.getValue().equals(query)||rebuilt.getCursorPosition()!=1)
                    throw new IllegalStateException("Same-screen resize lost search text, focus or cursor position: expectedText="+query
                            +" expectedCursor=1 sameScreen="+(mc.screen==screen)+" before={"+beforeResize+"} after={"+searchFocusState(screen)+"}");
                assertTreeSelection(menu.getSnapshot(),device,expanded?19:0);
                verifyVisibleBounds(mc,screen,menu,true);
                captureExtra(mc,"smoke-auto960-search-rebuild.png");
            }
            case 11 -> {
                assertTreeSelection(snapshot,device,expanded?19:0);
                if(mc.options.guiScale().get()!=0||mc.getWindow().getGuiScale()!=3)
                    throw new IllegalStateException("Narrow rebuild changed user GUI scale");
                System.out.println("ME_STORAGE_SMOKE_NARROW PASS 960x720 Auto3=320x240; bounded panes, real scrollbar ends, tree cell pickup/reinsert and search query/focus/cursor/scope survive same-screen resize");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,720);
            }
            case 12 -> {
                if(mc.getWindow().getScreenWidth()!=1280||mc.getWindow().getScreenHeight()!=720)
                    throw new IllegalStateException("Narrow test did not restore 1280x720 capture window");
                // Restore only the test query through actual edit-box input before ordinary capture flow resumes.
                clickUi(screen,screen.smokeContentSearchRect());screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);
                for(int i=0;i<64;i++)screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
            }
            case 13 -> {phase=2;return;}
        }
        autoRestoreStage++;autoRestoreTicks=25;
    }

    private static void dragScrollbar(ControllerScreen screen,boolean bottom) {
        var track=screen.smokeScrollbarRect();if(track==null)throw new IllegalStateException("Scrollbar is not visible");
        double x=track.centerX(),start=bottom?track.y()+1:track.y()+track.height()-1;
        double end=bottom?track.y()+track.height()+8:track.y()-8;
        if(!screen.mouseClicked(x,start,0)||!screen.mouseDragged(x,end,0,0,end-start))
            throw new IllegalStateException("Real scrollbar drag was not consumed");
        screen.mouseReleased(x,end,0);
    }

    private static String searchFocusState(ControllerScreen screen) {
        var focused=screen.getFocused();
        var fields=screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.EditBox)
                .map(child->{var field=(net.minecraft.client.gui.components.EditBox)child;
                    return "[text="+field.getValue()+",cursor="+field.getCursorPosition()+",isFocused="+field.isFocused()
                            +",currentFocus="+(focused==field)+",visible="+field.visible+"]";}).toList();
        return "currentFocus="+(focused==null?"null":focused.getClass().getName())+" fields="+fields;
    }

    private static void verifyVisibleBounds(Minecraft mc, ControllerScreen screen, ControllerMenu menu, boolean inventory) {
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        var panel = screen.smokePanelRect();
        verifySidebarBounds(screen);
        checkBounds("panel", panel, width, height);
        if(screen.smokeTreeViewport()!=null)checkBounds("tree viewport", screen.smokeTreeViewport(), width, height);
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible)
                checkBounds("control " + widget.getMessage().getString(),
                        new ControllerScreen.UiRect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()), width, height);
        }
        int visibleSlots = 0;
        for (int index = 0; index < menu.slots.size(); index++) {
            var rect = screen.smokeSlotRect(index);
            if (rect == null) continue;
            visibleSlots++;
            checkBounds("slot " + index, rect, width, height);
            if (rect.x() < panel.x() || rect.y() < panel.y() || rect.x() + rect.width() > panel.x() + panel.width()
                    || rect.y() + rect.height() > panel.y() + panel.height())
                throw new IllegalStateException("Slot clipped outside terminal panel: " + index);
        }
        boolean contentsVisible=screen.smokeContentScrollRect()!=null;
        if (contentsVisible&&visibleSlots < 36) throw new IllegalStateException("Visible content pane hides the player inventory");
        int contentTiles=0;
        for(int index=0;index<Snapshot.CONTENT_PAGE_SIZE;index++) {
            var tile=screen.smokeContentRect(index);if(tile==null)continue;
            checkBounds("content tile "+index,tile,width,height);contentTiles++;
        }
        if(contentsVisible&&contentTiles<36)throw new IllegalStateException("Native Auto terminal must expose at least four content rows");
        int option=mc.options.guiScale().get();
        if(mc.getWindow().getGuiScale()!=mc.getWindow().calculateScale(option,mc.isEnforceUnicode()))
            throw new IllegalStateException("Controller changed actual GUI scale at option="+option);
        if(screen.smokeTreeViewport()!=null&&menu.getSnapshot().editableSlots()>0 && visibleSlots<(contentsVisible?36:0)+menu.getSnapshot().editableSlots())
            throw new IllegalStateException("Unified terminal hides editable physical cells");
        System.out.println("ME_STORAGE_SMOKE_LAYOUT PASS " + NAMES[view] + " pixels=" + mc.getWindow().getWidth()
                + "x" + mc.getWindow().getHeight() + " gui=" + width + "x" + height + " option="
                + mc.options.guiScale().get() + " actualScale=" + mc.getWindow().getGuiScale() + " slots=" + visibleSlots);
    }

    private static void checkBounds(String name, ControllerScreen.UiRect rect, int width, int height) {
        if (rect == null || rect.width() <= 0 || rect.height() <= 0 || rect.x() < 0 || rect.y() < 0
                || rect.x() + rect.width() > width || rect.y() + rect.height() > height)
            throw new IllegalStateException("Visible " + name + " exceeds GUI bounds " + width + "x" + height + ": " + rect);
    }

    /** Real tree gestures; reveal helpers move the viewport but never change menu selection. */
    private static boolean verifyStorageTree(Minecraft mc, ControllerMenu menu) {
        var screen = (ControllerScreen) mc.screen;
        var s = menu.getSnapshot();
        if (treeTransitionCapturePending && treeWait == 6) {
            treeTransitionCapturePending = false;
            captureExtra(mc, "smoke-tree-transition.png");
        }
        if (treeWait > 0 && --treeWait > 0) return false;
        int targetCell = expanded ? 19 : 0;
        switch (treePhase) {
            case 0 -> {
                normalTreeId = treeDeviceId(s, "ae2:drive");
                secondTreeId = treeDeviceId(s, expanded ? "expatternprovider:ex_drive" : "ae2:chest");
                captureExtra(mc, "smoke-tree-initial.png", () -> {
                    screen.smokeExpandDevice(normalTreeId);
                    screen.smokeExpandDevice(secondTreeId);
                    screen.smokeRevealDevice(normalTreeId);
                });
            }
            case 1 -> {
                requireTreeBranches(screen, true, true);
                clickUi(screen, screen.smokeDeviceRect(normalTreeId, true));
            }
            case 2 -> {
                requireTreeBranches(screen, false, true);
                clickUi(screen, screen.smokeDeviceRect(normalTreeId, true));
                treeTransitionCapturePending = true;
            }
            case 3 -> {
                requireTreeBranches(screen, true, true);
                screen.smokeRevealDevice(secondTreeId);
            }
            case 4 -> clickUi(screen, screen.smokeDeviceRect(secondTreeId, true));
            case 5 -> {
                requireTreeBranches(screen, true, false);
                clickUi(screen, screen.smokeDeviceRect(secondTreeId, true));
            }
            case 6 -> {
                requireTreeBranches(screen, true, true);
                captureExtra(mc, "smoke-tree-expanded.png", () -> screen.smokeRevealCell(secondTreeId, targetCell));
            }
            case 7 -> clickUi(screen, screen.smokeCellRect(secondTreeId, targetCell));
            case 8 -> {
                assertTreeSelection(s, secondTreeId, targetCell);
                var expected = expanded ? AEItemKey.of(Items.COPPER_INGOT) : AEFluidKey.of(Fluids.WATER);
                long amount = expanded ? 98765 : 23456;
                if (s.contents().stream().noneMatch(c -> c.key().equals(expected) && c.amount() == amount))
                    throw new IllegalStateException("Actual tree cell selection returned wrong contents");
                previousTreeScroll = screen.smokeTreeScrollOffset();
                var viewport = screen.smokeTreeViewport();
                screen.mouseScrolled(viewport.centerX(), viewport.centerY(), 12);
            }
            case 9 -> {
                assertTreeSelection(s, secondTreeId, targetCell);
                if (expanded && Math.abs(screen.smokeTreeScrollOffset() - previousTreeScroll) < 1)
                    throw new IllegalStateException("Expanded tree did not actually scroll away from cell twenty");
                var viewport = screen.smokeTreeViewport();
                screen.mouseScrolled(viewport.centerX(), viewport.centerY(), -12);
            }
            case 10 -> {
                assertTreeSelection(s, secondTreeId, targetCell);
                requireTreeBranches(screen, true, true);
                screen.smokeRevealCell(secondTreeId, targetCell);
            }
            case 11 -> {
                assertTreeSelection(s, secondTreeId, targetCell);
                if (screen.smokeCellRect(secondTreeId, targetCell) == null)
                    throw new IllegalStateException("Selected cell did not return into view after scrolling");
                clickUi(screen, screen.smokeBackRect());
            }
            case 12 -> {
                assertTreeSelection(s, secondTreeId, -1);
                clickUi(screen, screen.smokeRootRect());
            }
            case 13 -> {
                assertTreeSelection(s, "", -1);
                treeVerified = true;
                System.out.println("ME_STORAGE_SMOKE_TREE PASS independent real chevron collapse/expand, actual cell selection, scroll retains selection, Back and network root");
                return true;
            }
            default -> throw new IllegalStateException("Unknown tree test phase " + treePhase);
        }
        treePhase++;
        treeWait = treePhase == 8 || treePhase == 12 || treePhase == 13 ? 25 : 8;
        return false;
    }

    private static String treeDeviceId(Snapshot snapshot, String itemId) {
        return snapshot.directory().stream().map(Snapshot.DirectoryEntry::device)
                .filter(device -> itemId.equals(ForgeRegistries.ITEMS.getKey(device.icon().getItem()).toString()))
                .map(Snapshot.DeviceInfo::id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Directory omitted test device " + itemId));
    }

    private static void requireTreeBranches(ControllerScreen screen, boolean normal, boolean second) {
        if (screen.smokeDeviceExpanded(normalTreeId) != normal || screen.smokeDeviceExpanded(secondTreeId) != second)
            throw new IllegalStateException("Device expansion states are not independent: expected " + normal + "," + second);
    }

    private static void assertTreeSelection(Snapshot snapshot, String device, int cell) {
        if (!snapshot.selectedDevice().equals(device) || snapshot.selectedCell() != cell)
            throw new IllegalStateException("Tree navigation selection mismatch: expected " + device + "/" + cell
                    + " actual=" + snapshot.selectedDevice() + "/" + snapshot.selectedCell());
    }

    private static void captureExtra(Minecraft mc, String name) {
        captureExtra(mc, name, () -> {});
    }

    private static void captureExtra(Minecraft mc, String name, Runnable afterCapture) {
        if (pendingExtraCapture != null) throw new IllegalStateException("An extra capture is already pending");
        clearCaptureHover(mc);
        pendingExtraCapture = new PendingExtraCapture(name, ticks + 2, afterCapture);
    }

    /** Pause test gestures while a clean frame renders; independent of the main capture completion flags. */
    private static boolean drainExtraCapture(Minecraft mc) {
        var pending = pendingExtraCapture;
        if (pending == null) return false;
        if (ticks < pending.readyTick()) return true;
        if(hoverCaptureRect!=null) {
            var screen=(ControllerScreen)mc.screen;
            String expectedKind=hoverCaptureRect.equals(screen.smokeGuideRect())?"widget":"cell";
            if(screen.smokeTooltipRenderCount()!=1 || !screen.smokeTooltipKind().equals(expectedKind))
                throw new IllegalStateException("Hover must render one "+expectedKind+" tooltip: count="
                        +screen.smokeTooltipRenderCount()+" kind="+screen.smokeTooltipKind());
            System.out.println("ME_STORAGE_SMOKE_HOVER PASS one "+expectedKind+" tooltip: "+screen.smokeTooltipText());
        }
        File directory = output(mc);
        directory.mkdirs();
        Screenshot.grab(directory, pending.name(), mc.getMainRenderTarget(), result -> {
            System.out.println("ME_STORAGE_SMOKE_TREE_SCREENSHOT " + result.getString());
            if (!new File(directory, "screenshots/" + pending.name()).isFile()) failure = "Tree screenshot failed: " + pending.name();
        });
        pendingExtraCapture = null;
        hoverCaptureRect=null;
        pending.afterCapture().run();
        return true;
    }

    /** Use the vanilla client packet route, including its local prediction and server correction. */
    private static boolean verifyTransfers(Minecraft mc, ControllerMenu menu, int remoteSlot,
                                           net.minecraft.world.item.Item contentItem, long expectedAmount) {
        if (transferPhase >= 5) return true;
        if (transferTicks > 0 && --transferTicks > 0) return false;
        var cellItem = AEItems.ITEM_CELL_64K.asItem();
        if (transferPhase == 0) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, remoteSlot, 0, ClickType.PICKUP, mc.player);
        } else if (transferPhase == 1) {
            if (!menu.getCarried().is(cellItem) || menu.getSlot(remoteSlot).hasItem()) throw new IllegalStateException("Remote pickup transport failed");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, remoteSlot, 0, ClickType.PICKUP, mc.player);
        } else if (transferPhase == 2) {
            if (!menu.getCarried().isEmpty() || !menu.getSlot(remoteSlot).getItem().is(cellItem)) throw new IllegalStateException("Remote reinsert transport failed");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, remoteSlot, 0, ClickType.QUICK_MOVE, mc.player);
        } else if (transferPhase == 3) {
            if (menu.getSlot(remoteSlot).hasItem() || !menu.getCarried().isEmpty()) throw new IllegalStateException("Remote shift-extract failed");
            int found = -1, count = 0;
            for (int i = 10; i < 46; i++) if (menu.getSlot(i).getItem().is(cellItem)) {
                found = i; count += menu.getSlot(i).getItem().getCount();
            }
            if (found < 0 || count != 1) throw new IllegalStateException("Shift-extract lost or duplicated cell: " + count);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, found, 0, ClickType.QUICK_MOVE, mc.player);
        } else if (transferPhase == 4) {
            int count = 0;
            for (int i = 0; i < 46; i++) if (menu.getSlot(i).getItem().is(cellItem)) count += menu.getSlot(i).getItem().getCount();
            if (count != 1 || !menu.getSlot(remoteSlot).getItem().is(cellItem) || !menu.getCarried().isEmpty()) {
                throw new IllegalStateException("Shift-reinsert lost or duplicated cell: " + count);
            }
            boolean exact = menu.getSnapshot().contents().stream().anyMatch(c -> c.key().equals(AEItemKey.of(contentItem)) && c.amount() == expectedAmount);
            if (!exact) throw new IllegalStateException("Cell transfer changed exact stored resource count");
            System.out.println("ME_STORAGE_SMOKE_TRANSFERS PASS visible slot " + remoteSlot + "; pickup, reinsert, shift-extract, shift-reinsert; one cell and exactly " + expectedAmount + " " + ForgeRegistries.ITEMS.getKey(contentItem) + " preserved");
        }
        transferPhase++;
        transferTicks = 30;
        return transferPhase >= 5;
    }

    /** Exercise the real screen's double-click state, rather than sending handcrafted pickup packets. */
    private static boolean verifyRapidMouseClicks(Minecraft mc, ControllerMenu menu, int visibleSlot, int actualSlot,
                                                  net.minecraft.world.item.Item contentItem, long expectedAmount) {
        if (rapidComplete) return true;
        if (rapidPairs < RAPID_PAIRS) {
            if (!menu.canSendClick(visibleSlot, ClickType.PICKUP))
                throw new IllegalStateException("Rapid left-click fixture is not editable after selection acknowledgement");
            var screen = (ControllerScreen) mc.screen;
            var slotRect = screen.smokeSlotRect(visibleSlot);
            checkBounds("rapid-click slot", slotRect, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            double mouseX = slotRect.centerX();
            double mouseY = slotRect.centerY();
            long started = System.nanoTime();
            // No Shift, intervening navigation request, or packet-level click bypass.
            for (int click = 0; click < 2; click++) {
                boolean pressed = screen.mouseClicked(mouseX, mouseY, 0);
                boolean released = screen.mouseReleased(mouseX, mouseY, 0);
                boolean shouldCarry = click == 0;
                boolean carriesCell = menu.getCarried().is(AEItems.ITEM_CELL_64K.asItem());
                boolean slotHasCell = menu.getSlot(visibleSlot).getItem().is(AEItems.ITEM_CELL_64K.asItem());
                int count = clientCellCount(menu);
                if (carriesCell != shouldCarry || slotHasCell == shouldCarry || count != 1) {
                    throw new IllegalStateException("Rapid real left click ignored or corrupted state: pair=" + rapidPairs
                            + " click=" + click + " visibleSlot=" + visibleSlot + " actualSlot=" + actualSlot
                            + " pressed=" + pressed + " released=" + released + " cursor=" + menu.getCarried()
                            + " slot=" + menu.getSlot(visibleSlot).getItem() + " total64k=" + count);
                }
            }
            long pairMillis = (System.nanoTime() - started) / 1_000_000;
            if (pairMillis >= 250) throw new IllegalStateException("Rapid mouse pair exceeded double-click interval: " + pairMillis + "ms");
            System.out.println("ME_STORAGE_SMOKE_RAPID_PAIR " + (rapidPairs + 1) + "/"+RAPID_PAIRS+" visible=" + visibleSlot
                    + " actual=" + actualSlot + " durationMs=" + pairMillis + " cursorEmpty=" + menu.getCarried().isEmpty());
            rapidPairs++;
            rapidSettleTicks = 30;
            return false;
        }
        if (heldToolbarPhase < 4) {
            var screen = (ControllerScreen) mc.screen;
            var slotRect = screen.smokeSlotRect(visibleSlot);
            checkBounds("held-control slot", slotRect, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            double slotX = slotRect.centerX();
            double slotY = slotRect.centerY();
            var themeRect = screen.smokeThemeRect();
            if (themeRect == null) throw new IllegalStateException("Theme control is not visible");
            double themeX = themeRect.centerX();
            double themeY = themeRect.centerY();
            if (heldToolbarPhase == 0 || heldToolbarPhase == 3) {
                screen.mouseClicked(slotX, slotY, 0);
                screen.mouseReleased(slotX, slotY, 0);
            } else {
                boolean wasDark = ClientAppearance.isDark();
                screen.mouseClicked(themeX, themeY, 0);
                // Second toolbar gesture deliberately releases outside the entire menu.
                screen.mouseReleased(heldToolbarPhase == 1 ? themeX : 0,
                        heldToolbarPhase == 1 ? themeY : 0, 0);
                if (ClientAppearance.isDark() == wasDark)
                    throw new IllegalStateException("Held-cell regression did not activate the actual theme button");
            }
            boolean shouldCarry = heldToolbarPhase < 3;
            if (menu.getCarried().is(AEItems.ITEM_CELL_64K.asItem()) != shouldCarry
                    || menu.getSlot(visibleSlot).hasItem() == shouldCarry || clientCellCount(menu) != 1)
                throw new IllegalStateException("Held-cell toolbar gesture dropped or changed the cell: phase="
                        + heldToolbarPhase + " slot=" + visibleSlot + " cursor=" + menu.getCarried()
                        + " cellCount=" + clientCellCount(menu));
            heldToolbarPhase++;
            rapidSettleTicks = 30;
            if (heldToolbarPhase == 4) System.out.println("ME_STORAGE_SMOKE_HELD_TOOLBAR PASS normal sidebar release and outside drag-release preserve carried cell; reinserted visible=" + visibleSlot);
            return false;
        }
        if (rapidSettleTicks > 0 && --rapidSettleTicks > 0) return false;
        if (!rapidVerificationRequested) {
            rapidVerificationRequested = true;
            var playerId = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    if (player == null || !(player.containerMenu instanceof ControllerMenu serverMenu))
                        throw new IllegalStateException("Rapid-click server menu closed before integrity verification");
                    var drivePos = actualSlot == 19 ? controllerPos.above() : controllerPos.east();
                    var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(drivePos);
                    if (drive == null || !drive.getInternalInventory().getStackInSlot(actualSlot).is(AEItems.ITEM_CELL_64K.asItem()))
                        throw new IllegalStateException("Rapid left clicks did not reinsert into actual slot " + actualSlot);
                    int count = 0;
                    for (int slot = 0; slot < drive.getInternalInventory().size(); slot++) {
                        var stack = drive.getInternalInventory().getStackInSlot(slot);
                        if (stack.is(AEItems.ITEM_CELL_64K.asItem())) count += stack.getCount();
                    }
                    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                        var stack = player.getInventory().getItem(slot);
                        if (stack.is(AEItems.ITEM_CELL_64K.asItem())) count += stack.getCount();
                    }
                    if (serverMenu.getCarried().is(AEItems.ITEM_CELL_64K.asItem())) count += serverMenu.getCarried().getCount();
                    if (count != 1 || !serverMenu.getCarried().isEmpty())
                        throw new IllegalStateException("Rapid-click server cell count/cursor mismatch: count=" + count);
                    var drops = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(controllerPos).inflate(8),
                            entity -> entity.getItem().is(AEItems.ITEM_CELL_64K.asItem()));
                    if (!drops.isEmpty()) throw new IllegalStateException("Held-cell toolbar gestures spawned dropped storage cells: " + drops.size());
                    long amount = StorageScanner.contents(drive.getCellInventory(actualSlot)).get(AEItemKey.of(contentItem));
                    if (amount != expectedAmount) throw new IllegalStateException("Rapid-click server contents changed: " + amount);
                    rapidServerVerified = true;
                } catch (Throwable problem) { failure = problem.toString(); }
            });
            return false;
        }
        if (!rapidServerVerified) return false;
        if (clientCellCount(menu) != 1 || !menu.getCarried().isEmpty()
                || !menu.getSlot(visibleSlot).getItem().is(AEItems.ITEM_CELL_64K.asItem()))
            throw new IllegalStateException("Rapid-click client inventory diverged after server synchronization");
        if (menu.getSnapshot().contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(contentItem)) && c.amount() == expectedAmount))
            throw new IllegalStateException("Rapid-click client contents diverged after server synchronization");
        System.out.println("ME_STORAGE_SMOKE_RAPID_LEFT_CLICK PASS "+RAPID_PAIRS+" pairs / "+(2*RAPID_PAIRS)+" screen press-release events; visible="
                + visibleSlot + " actual=" + actualSlot + "; client/server agree one cell and " + expectedAmount
                + " " + ForgeRegistries.ITEMS.getKey(contentItem));
        rapidComplete = true;
        return true;
    }

    private static int clientCellCount(ControllerMenu menu) {
        int count = menu.getCarried().is(AEItems.ITEM_CELL_64K.asItem()) ? menu.getCarried().getCount() : 0;
        for (int slot = 0; slot < 46; slot++) {
            var stack = menu.getSlot(slot).getItem();
            if (stack.is(AEItems.ITEM_CELL_64K.asItem())) count += stack.getCount();
        }
        return count;
    }

    private static boolean verifyLocalizedSearch(ControllerMenu menu) {
        if (searchPhase >= 4) return true;
        if (searchTicks > 0 && --searchTicks > 0) return false;
        var s = menu.getSnapshot();
        var screen = (ControllerScreen) Minecraft.getInstance().screen;
        if (searchPhase == 0) {
            var tab = screen.smokeContentsTabRect();
            if (tab != null) clickUi(screen, tab);
        } else if (searchPhase == 1) {
            clickUi(screen, screen.smokeContentSearchRect());
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
            for (int i = 0; i < 64; i++) screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0);
            screen.charTyped('铜', 0);
            screen.charTyped('锭', 0);
        } else if (searchPhase == 2) {
            if (s.contentCount() != 1 || s.contents().size() != 1 || !s.contents().get(0).key().equals(AEItemKey.of(Items.COPPER_INGOT))
                    || s.contents().get(0).amount() != 98765) throw new IllegalStateException("Localized copper search did not preserve exact 98765 count");
            captureExtra(Minecraft.getInstance(), "smoke-localized-search.png", () -> {
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME, 0, 0);
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0);
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0);
            });
        } else {
            if (s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.COPPER_INGOT)) && c.amount() == 98765))
                throw new IllegalStateException("Clearing localized search lost copper contents");
            System.out.println("ME_STORAGE_SMOKE_LOCALIZED_SEARCH PASS real focused text input 铜锭 = 98765; Home/Delete clearing restored contents");
        }
        searchPhase++;
        searchTicks = 25;
        return searchPhase >= 4;
    }

    private static File output(Minecraft mc) {
        return new File(System.getProperty("mestorage.smokeOutput", mc.gameDirectory.getAbsolutePath()));
    }

    /** Capture the real placed model after all GUI checks, only in the disposable smoke world. */
    private static void captureBlockAppearance(Minecraft mc) {
        blockCaptureTicks++;
        if (blockCaptureStage == 0 && blockCaptureTicks == 1) {
            if(!net.minecraft.network.chat.Component.translatable("block.me_storage_controller.controller").getString().equals("ME存储控制器")
                    ||!net.minecraft.network.chat.Component.translatable("gui.me_storage_controller.terminal_title").getString().equals("ME存储"))
                throw new IllegalStateException("Simplified Chinese controller and terminal labels retain unwanted ME spacing");
            mc.player.closeContainer();
            mc.options.hideGui = true;
            captureDone = false;
            var playerId = mc.player.getUUID();
            galleryPos = controllerPos.east(6);
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    if (player == null) throw new IllegalStateException("Missing block-capture player");
                    player.serverLevel().setDayTime(6000);
                    player.serverLevel().setWeatherParameters(6000, 0, false, false);
                    // The isolated gallery leaves the front and sides unobstructed by test storage devices.
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                        player.serverLevel().setBlockAndUpdate(galleryPos.offset(x, -2, z), Blocks.SMOOTH_STONE.defaultBlockState());
                        for (int y = -1; y <= 2; y++)
                            player.serverLevel().setBlockAndUpdate(galleryPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    player.serverLevel().setBlockAndUpdate(galleryPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    player.serverLevel().setBlockAndUpdate(galleryPos, MEStorageController.CONTROLLER.get().defaultBlockState());
                    player.teleportTo(player.serverLevel(), galleryPos.getX() + 2.8, galleryPos.getY(),
                            galleryPos.getZ() - 2.2, 40, 18);
                } catch (Throwable problem) { failure = problem.toString(); }
            });
            return;
        }
        if (!mc.level.getBlockState(galleryPos).is(MEStorageController.CONTROLLER.get())) return;
        boolean lit = mc.level.getBlockState(galleryPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        if (blockCaptureStage == 0 && blockCaptureTicks >= 60) {
            if (!lit) throw new IllegalStateException("Powered block appearance did not report online status");
            if (!capturing) { capturing = true; grabBlockScreenshot(mc, "smoke-block-online.png"); return; }
            if (!captureDone) return;
            blockCaptureStage = 1;
            blockCaptureTicks = 0;
            capturing = false;
            captureDone = false;
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                    .setBlockAndUpdate(galleryPos.below(), Blocks.AIR.defaultBlockState()));
        } else if (blockCaptureStage == 1 && blockCaptureTicks >= 40) {
            if (lit) {
                if (blockCaptureTicks > 300) throw new IllegalStateException("Unpowered block appearance did not turn offline");
                return;
            }
            if (!capturing) { capturing = true; grabBlockScreenshot(mc, "smoke-block-offline.png"); return; }
            if (!captureDone) return;
            blockCaptureStage = 2;
            blockCaptureTicks = 0;
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld()
                    .setBlockAndUpdate(galleryPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState()));
        } else if (blockCaptureStage == 2 && blockCaptureTicks >= 30) {
            if (!lit) {
                if (blockCaptureTicks > 300) throw new IllegalStateException("Block did not illuminate after power restoration");
                return;
            }
            System.out.println("ME_STORAGE_SMOKE_BLOCK_STATUS PASS online/offline captures and restored online state");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            DeviceHighlight.show(mc.level.dimension().location(),galleryPos);
            highlightFrameBaseline=highlightDrawCount();
            moveHighlightCamera(mc,false);
            blockCaptureStage=3;blockCaptureTicks=0;capturing=false;captureDone=false;
        } else if((blockCaptureStage==3||blockCaptureStage==4)&&blockCaptureTicks>=30) {
            if(!capturing) {
                long drawn=highlightDrawCount()-highlightFrameBaseline;
                if(drawn<=0)throw new IllegalStateException("Highlight render stage did not submit any real line batches");
                var camera=mc.gameRenderer.getMainCamera();
                System.out.println("ME_STORAGE_SMOKE_HIGHLIGHT_CAPTURE target="+galleryPos+" dimension="+mc.level.dimension().location()
                        +" camera="+camera.getPosition()+" yaw="+camera.getYRot()+" pitch="+camera.getXRot()+" firstPerson="+mc.options.getCameraType().isFirstPerson()+" submittedFrames="+drawn);
                capturing=true;grabBlockScreenshot(mc,blockCaptureStage==3?"smoke-highlight-front.png":"smoke-highlight-oblique.png");return;
            }
            if(!captureDone)return;
            if(blockCaptureStage==3) {
                highlightFrameBaseline=highlightDrawCount();
                moveHighlightCamera(mc,true);blockCaptureStage=4;blockCaptureTicks=0;capturing=false;captureDone=false;return;
            }
            if(!verifyOmniClient(mc))return;
            System.out.println("ME_STORAGE_SMOKE_HIGHLIGHT_CAPTURED first-person front and moved oblique view of the same target; screenshots require alignment review");
            if(!treeMemoryVerified||!foldersVerified)throw new IllegalStateException("Final acceptance requires completed tree-memory and shared-folder phases: memory="+treeMemoryVerified+", folders="+foldersVerified);
            finish(mc, "PASS: personal tree collapse memory and shared-folder UI VERIFIED, including Ctrl/Shift selection, nested metadata-only CRUD, whole-device plus separately grouped slot reassignment, scoped content roundtrip and acknowledged backpack insertion; 45-slot item-transfer grid, network/device/cell left/right/Shift and empty-tile insertions, 24 rapid content clicks, selected-cell isolation, one combined cell tooltip and ME网络 root label; real next/previous paging of 260+ types, amount/name sorting; real storage-tree expansion/collapse, scrolling, cell selection and root/back navigation; real text search; unified grid/cells/player inventory and placed-block captures. Eight facing/lit block models and the item model have nonempty quads with no missing sprites. Fixed scale 2 plus 1280x720 and 1920x1080 Auto have bounded controls and slots; opening/closing/resizing retain native vanilla Auto scale without changing option 0. Normal/Shift transfers, " + (expanded ? 8*RAPID_PAIRS : 6*RAPID_PAIRS) + " rapid left clicks across fixed/Auto cases and held-control outside releases preserve unique cells and exact contents. ExtendedAE cell twenty: " + (expanded ? "PASS" : "SKIPPED (addon absent)"));
        }
    }

    private static int omniStage,omniWait;
    private static volatile boolean omniSetupDone;
    private static boolean omniRequested;

    /** Final optional fixture checks the real packet, formatting and native Auto layout. */
    private static boolean verifyOmniClient(Minecraft mc) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("ae2omnicells"))return true;
        if(drainExtraCapture(mc))return false;
        if(omniWait>0){omniWait--;return false;}
        var pos=controllerPos.south(12);
        if(omniStage==0) {
            omniStage=1;omniWait=80;var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                try {
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var level=player.serverLevel();
                    player.closeContainer();player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);
                    for(var p:java.util.List.of(pos,pos.east(),pos.below()))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    level.setBlockAndUpdate(pos,MEStorageController.CONTROLLER.get().defaultBlockState());
                    level.setBlockAndUpdate(pos.east(),AEBlocks.DRIVE.block().defaultBlockState());
                    var drive=(DriveBlockEntity)level.getBlockEntity(pos.east());
                    var ids=new String[]{"complex_omni_cell_256m","quantum_omni_cell_1k","creative_ae_cell_biginteger"};
                    for(int i=0;i<ids.length;i++) {
                        var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2omnicells",ids[i]));
                        if(item==null||item==Items.AIR)throw new IllegalStateException("Missing Omni fixture cell "+ids[i]);
                        drive.getInternalInventory().setItemDirect(i,new ItemStack(item));
                    }
                    omniSetupDone=true;
                }catch(Throwable error){failure=error.toString();}
            });return false;
        }
        if(omniStage==1) {
            if(!omniSetupDone)return false;
            omniStage=2;omniWait=30;var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                try {
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var level=player.serverLevel();
                    var drive=(DriveBlockEntity)level.getBlockEntity(pos.east());
                    long[] iron={123456789,128,8192},water={123456,23456,8000};
                    for(int i=0;i<3;i++) {
                        var storage=drive.getCellInventory(i);
                        if(storage==null||storage.insert(AEItemKey.of(Items.IRON_INGOT),iron[i],Actionable.MODULATE,IActionSource.empty())!=iron[i]
                                ||storage.insert(AEFluidKey.of(Fluids.WATER),water[i],Actionable.MODULATE,IActionSource.empty())!=water[i])
                            throw new IllegalStateException("Omni mixed fixture rejected contents "+i);
                    }
                    NetworkHooks.openScreen(player,(ControllerBlockEntity)level.getBlockEntity(pos),pos);
                }catch(Throwable error){failure=error.toString();}
            });return false;
        }
        if(!(mc.player.containerMenu instanceof ControllerMenu menu)||!(mc.screen instanceof ControllerScreen screen))return false;
        var snapshot=menu.getSnapshot();if(!snapshot.online())return false;
        if(omniStage>=6){System.out.println("ME_STORAGE_SMOKE_OMNI PASS actual network packets: complex256M, quantum unlimited types, BigInteger unlimited bytes/types, mixed item/fluid entries and native Auto screenshots");return true;}
        int cell=omniStage==2?-1:omniStage-3;
        String device=cell<0?"":snapshot.directory().stream().map(Snapshot.DirectoryEntry::device)
                .filter(d->d.pos().equals(pos.east())).findFirst().orElseThrow().id();
        if(!omniRequested) {
            if(cell>=0)screen.smokeRevealCell(device,cell);
            screen.setDarkThemeForTest(false);menu.request(device,cell,0,0,"","",true);
            omniRequested=true;omniWait=20;return false;
        }
        if(!snapshot.selectedDevice().equals(device)||snapshot.selectedCell()!=cell)return false;
        var cap=snapshot.capacity();
        long expectedTotal=cell==0?268435456:cell==1?1024:Snapshot.Capacity.UNLIMITED;
        long expectedTypes=cell==0?6400:Snapshot.Capacity.UNLIMITED;
        if(cap.totalBytes()!=expectedTotal||cap.totalTypes()!=expectedTypes||cap.usedBytes()<0||cap.unknownCells()!=0)
            throw new IllegalStateException("Omni client capacity mismatch cell="+cell+" "+cap);
        if(snapshot.contents().stream().noneMatch(c->c.key() instanceof AEItemKey)
                ||snapshot.contents().stream().noneMatch(c->c.key() instanceof AEFluidKey))
            throw new IllegalStateException("Omni client mixed contents missing");
        if(mc.options.guiScale().get()!=0||mc.getWindow().getGuiScale()!=mc.getWindow().calculateScale(0,mc.isEnforceUnicode()))
            throw new IllegalStateException("Omni fixture changed native Auto scale");
        if(!ControllerScreen.number(Snapshot.Capacity.UNLIMITED).equals("∞"))throw new IllegalStateException("Unlimited formatting is not distinct from unknown");
        clearCaptureHover(mc);
        String name=switch(cell){case 0->"complex256m";case 1->"quantum";case 2->"biginteger";default->"network";};
        captureExtra(mc,"smoke-omni-"+name+".png");omniStage++;omniRequested=false;omniWait=20;
        return false;
    }

    private static void moveHighlightCamera(Minecraft mc,boolean oblique) {
        var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            try {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if(player==null)throw new IllegalStateException("Highlight capture player missing");
                double x=galleryPos.getX()+(oblique?2.2:.5),z=galleryPos.getZ()+(oblique?-1.5:-1.8);
                double dx=galleryPos.getX()+.5-x,dz=galleryPos.getZ()+.5-z;
                float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                double eyeY=galleryPos.getY()-1+player.getEyeHeight();
                float pitch=(float)Math.toDegrees(Math.atan2(eyeY-(galleryPos.getY()+.5),Math.sqrt(dx*dx+dz*dz)));
                player.teleportTo(player.serverLevel(),x,galleryPos.getY()-1,z,yaw,pitch);
            }catch(Throwable problem){failure=problem.toString();}
        });
    }

    private static long highlightDrawCount() {
        try {
            var field=DeviceHighlight.class.getDeclaredField("renderedFrames");field.setAccessible(true);return field.getLong(null);
        }catch(ReflectiveOperationException problem){throw new IllegalStateException("Development highlight draw counter unavailable",problem);}
    }

    private static void grabBlockScreenshot(Minecraft mc, String name) {
        File directory = output(mc);
        directory.mkdirs();
        Screenshot.grab(directory, name, mc.getMainRenderTarget(), result -> {
            System.out.println("ME_STORAGE_SMOKE_BLOCK_SCREENSHOT " + result.getString());
            if (!new File(directory, "screenshots/" + name).isFile()) failure = "Block screenshot save failed: " + name;
            captureDone = true;
        });
    }

    private static void finish(Minecraft mc, String result) {
        phase = 9;
        try {
            Path directory = output(mc).toPath();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("client-smoke-result.txt"), result + System.lineSeparator());
        } catch (Exception problem) { problem.printStackTrace(); }
        System.out.println("ME_STORAGE_SMOKE_RESULT " + result);
        mc.stop();
    }

    private static void captureFailureThenFinish(Minecraft mc, Throwable problem) {
        failureResult = "FAILED: " + problem;
        int failedPhase = phase;
        phase = 8;
        failureCaptureTicks = 0;
        failureCaptureDone = false;
        try {
            File directory = output(mc);
            directory.mkdirs();
            String state = "phase=" + failedPhase + " view=" + view + " treePhase=" + treePhase
                    + " treeWait=" + treeWait + " target=" + targetId + " failure=" + problem;
            if (mc.screen instanceof ControllerScreen screen) state += " treeScroll=" + screen.smokeTreeScrollOffset();
            Files.writeString(directory.toPath().resolve("client-smoke-failure-state.txt"), state + System.lineSeparator());
            System.err.println("ME_STORAGE_SMOKE_FAILURE_STATE " + state);
            Screenshot.grab(directory, "smoke-failure.png", mc.getMainRenderTarget(), result -> {
                System.out.println("ME_STORAGE_SMOKE_FAILURE_SCREENSHOT " + result.getString());
                failureCaptureDone = true;
            });
        } catch (Throwable captureProblem) {
            System.err.println("ME_STORAGE_SMOKE_FAILURE_CAPTURE_ERROR " + captureProblem);
            failureCaptureDone = true;
        }
    }
}
