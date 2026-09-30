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

/** Opt-in development-only integration capture. Run exclusively in a disposable smoke-test world. */
@Mod.EventBusSubscriber(modid = MEStorageController.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final boolean ENABLED = !FMLEnvironment.production && Boolean.getBoolean("mestorage.clientSmokeTest");
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
    private static final String[] NAMES = { "network", "drive", "cell", "fluid", "external" };

    private ClientSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == 9) return;
        var mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException(failure);
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (++ticks > 2400) throw new IllegalStateException("Client smoke timed out in phase " + phase + " view " + view);
            if (phase == 0) {
                mc.options.guiScale().set(2);
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

    private static void setup(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("Missing server player");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying=true;
        player.onUpdateAbilities();
        var level = player.serverLevel();
        controllerPos = player.blockPosition().offset(3, 0, 0);
        level.setBlockAndUpdate(controllerPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(controllerPos, MEStorageController.CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(controllerPos.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(controllerPos.west(), AEBlocks.CHEST.block().defaultBlockState());
        var drive = (DriveBlockEntity) level.getBlockEntity(controllerPos.east());
        var chest = (ChestBlockEntity) level.getBlockEntity(controllerPos.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_16K.stack());
        chest.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_64K.stack());
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
            if (++view == NAMES.length) {
                finish(mc, "PASS: real client opened server menu; network, drive, cell, fluid, external screenshots captured; exact counts and synchronized cell slot verified.");
                return;
            }
        }
        String kind = switch (view) { case 1, 2 -> "drive"; case 3 -> "chest"; case 4 -> "external"; default -> ""; };
        int cell = view == 2 || view == 3 ? 0 : -1;
        if (!requested) {
            targetId = kind.isEmpty() ? "" : s.devices().stream().filter(d -> d.kind().equalsIgnoreCase(kind))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing smoke device " + kind)).id();
            menu.request(targetId, cell, 0, 0, "", "", true);
            requested = true;
            return;
        }
        if (!s.selectedDevice().equals(targetId) || s.selectedCell() != cell) return;
        if (++stableTicks < 20) return;
        if (view == 1 && !menu.getSlot(0).getItem().is(AEItems.ITEM_CELL_64K.asItem())) {
            throw new IllegalStateException("Client remote cell slot did not synchronize");
        }
        if (view == 2 && s.contents().stream().noneMatch(c -> c.key().equals(AEItemKey.of(Items.IRON_INGOT)) && c.amount() == 12345)) {
            if (transferPhase == 0 || transferPhase >= 5) throw new IllegalStateException("Cell detail lost exact iron count");
        }
        if (view == 2 && !verifyTransfers(mc, menu)) return;
        if (view == 3 && s.contents().stream().noneMatch(c -> c.key().equals(AEFluidKey.of(Fluids.WATER)) && c.amount() == 23456)) {
            throw new IllegalStateException("Cell detail lost exact water amount");
        }
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
    private static boolean verifyTransfers(Minecraft mc, ControllerMenu menu) {
        if (transferPhase >= 5) return true;
        if (transferTicks > 0 && --transferTicks > 0) return false;
        var cellItem = AEItems.ITEM_CELL_64K.asItem();
        if (transferPhase == 0) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.PICKUP, mc.player);
        } else if (transferPhase == 1) {
            if (!menu.getCarried().is(cellItem) || menu.getSlot(0).hasItem()) throw new IllegalStateException("Remote pickup transport failed");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.PICKUP, mc.player);
        } else if (transferPhase == 2) {
            if (!menu.getCarried().isEmpty() || !menu.getSlot(0).getItem().is(cellItem)) throw new IllegalStateException("Remote reinsert transport failed");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, mc.player);
        } else if (transferPhase == 3) {
            if (menu.getSlot(0).hasItem() || !menu.getCarried().isEmpty()) throw new IllegalStateException("Remote shift-extract failed");
            int found = -1, count = 0;
            for (int i = 10; i < 46; i++) if (menu.getSlot(i).getItem().is(cellItem)) {
                found = i; count += menu.getSlot(i).getItem().getCount();
            }
            if (found < 0 || count != 1) throw new IllegalStateException("Shift-extract lost or duplicated cell: " + count);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, found, 0, ClickType.QUICK_MOVE, mc.player);
        } else if (transferPhase == 4) {
            int count = 0;
            for (int i = 0; i < 46; i++) if (menu.getSlot(i).getItem().is(cellItem)) count += menu.getSlot(i).getItem().getCount();
            if (count != 1 || !menu.getSlot(0).getItem().is(cellItem) || !menu.getCarried().isEmpty()) {
                throw new IllegalStateException("Shift-reinsert lost or duplicated cell: " + count);
            }
            boolean exact = menu.getSnapshot().contents().stream().anyMatch(c -> c.key().equals(AEItemKey.of(Items.IRON_INGOT)) && c.amount() == 12345);
            if (!exact) throw new IllegalStateException("Cell transfer changed exact stored iron count");
            System.out.println("ME_STORAGE_SMOKE_TRANSFERS PASS pickup, reinsert, shift-extract, shift-reinsert; one cell and exactly 12345 iron preserved");
        }
        transferPhase++;
        transferTicks = 30;
        return transferPhase >= 5;
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
