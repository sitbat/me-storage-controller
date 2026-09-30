package dev.mestorage.controller.client;

import java.io.File;
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
    private static String targetId = "";
    private static int transferPhase;
    private static int transferTicks;
    private static volatile boolean expanded;
    private static int awaitingPage = -1;
    private static int searchedPages;
    private static int searchPhase;
    private static int searchTicks;
    private static int navigationRaceTicks = -1;
    private static final String[] NAMES = { "dark-network", "dark-drive", "dark-cell", "dark-expanded-cell20",
            "dark-fluid", "dark-external", "light-network", "light-expanded-cell20", "compact-light", "compact-dark" };

    private ClientSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == 9) return;
        var mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (++ticks > 6000) throw new IllegalStateException("Client smoke timed out in phase " + phase + " view " + view);
            if (DEMO) {
                openDemo(mc);
                return;
            }
            if (phase == 0) {
                mc.options.guiScale().set(2);
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
            }
        } catch (Throwable problem) {
            problem.printStackTrace();
            finish(mc, "FAILED: " + problem);
        }
    }

    /** Open the existing demo fixture once, then leave all interaction to the player. */
    private static void openDemo(Minecraft mc) {
        if (phase == 0 && ticks >= 100) {
            mc.options.guiScale().set(2);
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
        chest.getCellInventory(0).insert(AEFluidKey.of(Fluids.WATER), 23456, Actionable.MODULATE, source);
        chest.getCellInventory(0).insert(AEFluidKey.of(Fluids.LAVA), 777000, Actionable.MODULATE, source);
        if (expanded) {
            var expandedDrive = (DriveBlockEntity) level.getBlockEntity(controllerPos.above());
            if (!expandedDrive.getMainNode().isActive()) throw new IllegalStateException("Expanded-drive fixture is not connected to the network");
            long inserted = expandedDrive.getCellInventory(19).insert(AEItemKey.of(Items.COPPER_INGOT), 98765,
                    Actionable.MODULATE, source);
            if (inserted != 98765) throw new IllegalStateException("Expanded-drive fixture did not accept exact copper amount");
        }
        NetworkHooks.openScreen(player, controller, controllerPos);
    }

    private static void driveCapture(Minecraft mc, ControllerMenu menu) {
        Snapshot s = menu.getSnapshot();
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
            if (++view == NAMES.length) {
                finish(mc, "PASS: real client dark/light dashboard and compact captures; exact item/fluid counts; vanilla packet pickup/reinsert/Shift transfers preserved one cell and all contents. ExtendedAE page-two and localized copper search: " + (expanded ? "PASS" : "SKIPPED (addon absent)"));
                return;
            }
        }
        if (!expanded && (view == 3 || view == 7)) { view++; requested = false; }
        boolean expandedView = view == 3 || view == 7;
        String registry = switch (view) { case 1, 2 -> "ae2:drive"; case 3, 7 -> "expatternprovider:ex_drive";
            case 4 -> "ae2:chest"; default -> ""; };
        int cell = expandedView ? 19 : view == 2 || view == 4 ? 0 : -1;
        if (!requested) {
            mc.getToasts().clear();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            int scale = view >= 8 ? 3 : 2;
            if (mc.options.guiScale().get() != scale) { mc.options.guiScale().set(scale); mc.resizeDisplay(); }
            var screen = (ControllerScreen) mc.screen;
            screen.setDarkThemeForTest(view < 6 || view == 9);
            if (awaitingPage >= 0 && s.devicePage() != awaitingPage) return;
            awaitingPage = -1;
            var wanted = s.devices().stream().filter(d -> view == 5 ? d.kind().equalsIgnoreCase("external")
                    : !registry.isEmpty() && registry.equals(ForgeRegistries.ITEMS.getKey(d.icon().getItem()).toString())).findFirst();
            if ((!registry.isEmpty() || view == 5) && wanted.isEmpty()) {
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
            if (menu.canSendClick(0, ClickType.QUICK_CRAFT) || menu.canSendClick(0, ClickType.PICKUP_ALL))
                throw new IllegalStateException("Unsupported predicted clicks were not gated");
            navigationRaceTicks = -2;
            System.out.println("ME_STORAGE_SMOKE_NAVIGATION PASS rapid A-B-A locks until latest acknowledgement and then unlocks");
        }
        if (view == 1 && !menu.getSlot(0).getItem().is(AEItems.ITEM_CELL_64K.asItem())) {
            throw new IllegalStateException("Client remote cell slot did not synchronize");
        }
        if (view == 2 && s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.IRON_INGOT)) && c.amount() == 12345)) {
            if (transferPhase == 0 || transferPhase >= 5) throw new IllegalStateException("Cell detail lost exact iron count");
        }
        if (view == 2 && !verifyTransfers(mc, menu, 0, Items.IRON_INGOT, 12345)) return;
        if (view == 3) {
            if (s.cellSlots() != 20 || s.editableSlots() != 10) throw new IllegalStateException("Expanded page-two slot counts incorrect");
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

    private static boolean verifyLocalizedSearch(ControllerMenu menu) {
        if (searchPhase >= 3) return true;
        if (searchTicks > 0 && --searchTicks > 0) return false;
        var s = menu.getSnapshot();
        if (searchPhase == 0) {
            menu.request(targetId, 19, s.devicePage(), 0, "", "铜锭", true);
        } else if (searchPhase == 1) {
            if (s.contentCount() != 1 || s.contents().size() != 1 || !s.contents().get(0).key().equals(AEItemKey.of(Items.COPPER_INGOT))
                    || s.contents().get(0).amount() != 98765) throw new IllegalStateException("Localized copper search did not preserve exact 98765 count");
            menu.request(targetId, 19, s.devicePage(), 0, "", "", true);
        } else {
            if (s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.COPPER_INGOT)) && c.amount() == 98765))
                throw new IllegalStateException("Clearing localized search lost copper contents");
            System.out.println("ME_STORAGE_SMOKE_LOCALIZED_SEARCH PASS 铜锭 = 98765; clearing restored contents");
        }
        searchPhase++;
        searchTicks = 25;
        return searchPhase >= 3;
    }

    private static File output(Minecraft mc) {
        return new File(System.getProperty("mestorage.smokeOutput", mc.gameDirectory.getAbsolutePath()));
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
}
