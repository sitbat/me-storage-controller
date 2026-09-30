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
    private static int treePhase;
    private static int treeWait;
    private static boolean treeVerified;
    private static boolean treeTransitionCapturePending;
    private static String normalTreeId;
    private static String secondTreeId;
    private static double previousTreeScroll;
    private static boolean inventoryPrepared;
    private static boolean captureViewPrepared;
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
    private static final java.util.Set<appeng.api.stacks.AEKey> gridKeys=new java.util.HashSet<>();
    private static final int RAPID_PAIRS=12;
    private static int synchronizedAcks;
    private static int navigationSyncStep;
    private static int navigationSyncWait;
    private static boolean navigationSyncRequested;
    private static Field captureMouseX;
    private static Field captureMouseY;
    private static PendingExtraCapture pendingExtraCapture;
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
        drive.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_16K.stack());
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
        player.getInventory().add(AEItems.ITEM_CELL_1K.stack());
        player.getInventory().add(new ItemStack(Items.DIAMOND, 32));
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
        // Real distinct content exceeds the 45-slot page, so pagination is exercised.
        var gridItems=net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .filter(item->item instanceof net.minecraft.world.item.BlockItem
                        && ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("minecraft")
                        && item!=Items.COBBLESTONE && item!=Items.OAK_LOG)
                .sorted(java.util.Comparator.comparing(item->ForgeRegistries.ITEMS.getKey(item).toString())).limit(52).toList();
        for(int i=0;i<gridItems.size();i++) {
            long accepted=drive.getCellInventory(1).insert(AEItemKey.of(gridItems.get(i)),i+1,Actionable.MODULATE,source);
            if(accepted!=i+1)throw new IllegalStateException("Failed to populate 52-key real content-grid fixture");
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
        System.out.println("ME_STORAGE_SMOKE_DROP_BASELINE PASS removedOldFixtureItems=" + oldDrops.size()
                + "; dropped64k=0 before opening the menu or sending any mouse events");
        NetworkHooks.openScreen(player, controller, controllerPos);
    }

    private static void driveCapture(Minecraft mc, ControllerMenu menu) {
        // Synchronize the development harness cursor without changing production tooltip rendering.
        clearCaptureHover(mc);
        if (drainExtraCapture(mc)) return;
        Snapshot s = menu.getSnapshot();
        if (!treeVerified && !verifyStorageTree(mc, menu)) return;
        if(view==0 && !verifyNavigationSync(menu))return;
        if(view==0 && !verifyContentGrid(mc,menu)) return;
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
        if (++stableTicks < 20) return;
        if ((view == 1 || view == 2 || view == 3 || view == 18 || view == 21) && !inventoryPrepared) {
            var inventoryTab = ((ControllerScreen) mc.screen).smokeInventoryTabRect();
            if (inventoryTab != null) clickUi((ControllerScreen) mc.screen, inventoryTab);
            inventoryPrepared = true;
            return;
        }
        if (autoView) {
            if (mc.options.guiScale().get() != 0) throw new IllegalStateException("Controller changed the Auto GUI option");
            double expectedScale = view >= 20 ? 3 : 2;
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
        if (view == 3) {
            if (s.cellSlots() != 20 || s.editableSlots() != 10) throw new IllegalStateException("Expanded page-two slot counts incorrect");
            if (!verifyRapidMouseClicks(mc, menu, 9, 19, Items.COPPER_INGOT, 98765)) return;
            if (!verifyTransfers(mc, menu, 9, Items.COPPER_INGOT, 98765)) return;
            if (!verifyLocalizedSearch(menu)) return;
        }
        if (expandedView && transferPhase == 0 && s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.COPPER_INGOT)) && c.amount() == 98765))
            throw new IllegalStateException("Expanded slot20 lost exact copper count");
        if (view == 4 && s.contents().stream().noneMatch(c -> c.key().equals(AEFluidKey.of(Fluids.WATER)) && c.amount() == 23456)) {
            throw new IllegalStateException("Cell detail lost exact water amount");
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
                if(snapshot.contentCount()<=Snapshot.CONTENT_PAGE_SIZE || snapshot.contents().size()!=45 || snapshot.contentPages()!=2)
                    throw new IllegalStateException("Grid smoke fixture must contain two pages with 45 visible keys");
                gridKeys.clear();snapshot.contents().forEach(entry->gridKeys.add(entry.key()));
                assertAmountOrder(snapshot);
                clickUi(screen,screen.smokeContentRect(0));
                if(!menu.getCarried().isEmpty())throw new IllegalStateException("Read-only content grid extracted a real item");
                clickUi(screen,screen.smokeContentNextRect());
            }
            case 1 -> {
                if(snapshot.contentPage()!=1||snapshot.contents().size()!=snapshot.contentCount()-45)
                    throw new IllegalStateException("Real Next control did not show the final content page");
                for(var entry:snapshot.contents())if(!gridKeys.add(entry.key()))throw new IllegalStateException("Grid pages duplicate a key");
                if(gridKeys.size()!=snapshot.contentCount())throw new IllegalStateException("Grid pages omitted stored keys");
                assertAmountOrder(snapshot);
                captureExtra(mc,"smoke-grid-page-two.png", () -> clickUi(screen,screen.smokeContentPreviousRect()));
            }
            case 2 -> {
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
                var cursor=menu.getCarried().copy();clickUi(screen,screen.smokeContentRect(44));
                if(!ItemStack.matches(cursor,menu.getCarried()))throw new IllegalStateException("Last read-only grid cell changed the cursor");
            }
            case 5 -> System.out.println("ME_STORAGE_SMOKE_CONTENT_GRID PASS 45-slot page, 52+ types, full unique-key coverage, real Next/Previous/name/amount controls, grid remains read-only");
        }
        contentGridPhase++;contentGridWait=25;return contentGridPhase>=6;
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
        double safeX = 2.0, safeY = 2.0;
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

    private static void verifyAutoRestore(Minecraft mc) {
        if (mc.options.guiScale().get() != 0) throw new IllegalStateException("Auto option changed during close/reopen");
        if (autoRestoreStage == 0) {
            mc.player.closeContainer();
            int vanillaScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
            if (mc.getWindow().getGuiScale() != vanillaScale)
                throw new IllegalStateException("Closing controller did not restore vanilla Auto scale: actual="
                        + mc.getWindow().getGuiScale() + " expected=" + vanillaScale);
            System.out.println("ME_STORAGE_SMOKE_AUTO_RESTORE PASS closed controller restored Window scale=" + vanillaScale + "; option=0");
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
            if (mc.getWindow().getGuiScale() != 2)
                throw new IllegalStateException("Reopened Auto controller did not reapply local scale limit");
            System.out.println("ME_STORAGE_SMOKE_AUTO_REOPEN PASS local scale=2; option=0");
            phase = 2;
        }
    }

    private static void verifyVisibleBounds(Minecraft mc, ControllerScreen screen, ControllerMenu menu, boolean inventory) {
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        var panel = screen.smokePanelRect();
        checkBounds("panel", panel, width, height);
        checkBounds("tree viewport", screen.smokeTreeViewport(), width, height);
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
        if (visibleSlots < 36) throw new IllegalStateException("Unified terminal hides the player inventory");
        for(int index=0;index<Snapshot.CONTENT_PAGE_SIZE;index++)
            checkBounds("read-only content cell "+index,screen.smokeContentRect(index),width,height);
        if(menu.getSnapshot().editableSlots()>0 && visibleSlots<36+menu.getSnapshot().editableSlots())
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
        File directory = output(mc);
        directory.mkdirs();
        Screenshot.grab(directory, pending.name(), mc.getMainRenderTarget(), result -> {
            System.out.println("ME_STORAGE_SMOKE_TREE_SCREENSHOT " + result.getString());
            if (!new File(directory, "screenshots/" + pending.name()).isFile()) failure = "Tree screenshot failed: " + pending.name();
        });
        pendingExtraCapture = null;
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
            finish(mc, "PASS: 45-slot read-only grid, real next/previous paging of 52+ types, amount/name sorting; real storage-tree expansion/collapse, scrolling, cell selection and root/back navigation; real text search; unified grid/cells/player inventory and placed-block captures. Eight facing/lit block models and the item model have nonempty quads with no missing sprites. Fixed scale 2 plus 1280x720 and 1920x1080 Auto have bounded controls and slots; closing restores vanilla Auto and reopening reapplies the local limit without changing option 0. Normal/Shift transfers, " + (expanded ? 8*RAPID_PAIRS : 6*RAPID_PAIRS) + " rapid left clicks across fixed/Auto cases and held-control outside releases preserve unique cells and exact contents. ExtendedAE cell twenty: " + (expanded ? "PASS" : "SKIPPED (addon absent)"));
        }
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
