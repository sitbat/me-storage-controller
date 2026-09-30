package dev.mestorage.controller.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
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
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Loaded by Forge only in an enabled development GameTest environment. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class StorageGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CHEST = new BlockPos(1, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void liveCellsAndRemoteSlots(GameTestHelper helper) {
        var controller = poweredController(helper);
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        helper.setBlock(CHEST, AEBlocks.CHEST.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(DRIVE);
        var chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        chest.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_64K.stack());
        // A fluid cell cannot consume this buffer item. The remote cell adapter
        // must never expose or overwrite this separate input-buffer slot.
        chest.getInternalInventory().setItemDirect(0, new ItemStack(Items.DIAMOND, 3));
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    activeGrid(helper, controller);
                    check(helper, drive.getMainNode().isActive(), "ME drive is not active");
                    check(helper, chest.getMainNode().isActive(), "ME chest is not active");
                })
                .thenExecute(() -> {
                    var source = IActionSource.empty();
                    check(helper, drive.getCellInventory(0) != null, "Drive did not mount its cell");
                    check(helper, chest.getCellInventory(0) != null, "Chest did not mount its cell");
                    check(helper, drive.getCellInventory(0).insert(iron, 12345, Actionable.MODULATE, source) == 12345,
                            "Live drive insertion must accept all 12345 items");
                    check(helper, chest.getCellInventory(0).insert(water, 23456, Actionable.MODULATE, source) == 23456,
                            "Live chest insertion must accept all 23456 mB");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var grid = activeGrid(helper, controller);
                    var driveDevice = device(grid, drive);
                    var chestDevice = device(grid, chest);
                    var cell = StorageScanner.readCells(driveDevice).get(0);
                    check(helper, StorageScanner.contents(cell.storage()).get(iron) == 12345,
                            "Cell view lost exact live quantity");
                    check(helper, cell.capacity().usedBytes() == 512 + (12345 + 7) / 8,
                            "Byte accounting must include type overhead and rounded data bytes");
                    check(helper, cell.capacity().totalBytes() == 65536 && cell.capacity().usedTypes() == 1,
                            "64k capacity or type accounting is incorrect");
                    check(helper, StorageScanner.gridContents(grid).get(iron) == 12345,
                            "Network overview must not double-count items");
                    check(helper, StorageScanner.gridContents(grid).get(water) == 23456,
                            "Network overview must preserve exact fluid quantity");
                    check(helper, driveDevice.cells().getSlots() == 10, "Drive must expose ten cell slots");
                    check(helper, chestDevice.cells().getSlots() == 1, "Chest must expose only its cell slot");
                    check(helper, chestDevice.cells().getStackInSlot(0).is(AEItems.FLUID_CELL_64K.asItem()),
                            "Chest cell adapter accidentally exposes its input buffer");
                    check(helper, driveDevice.cells() instanceof IItemHandlerModifiable,
                            "Remote slot handler must support vanilla menu synchronization");

                    var simulated = driveDevice.cells().extractItem(0, 1, true);
                    check(helper, !simulated.isEmpty() && !drive.getInternalInventory().getStackInSlot(0).isEmpty(),
                            "Simulation must not remove the physical cell");
                    var extracted = driveDevice.cells().extractItem(0, 1, false);
                    check(helper, drive.getInternalInventory().getStackInSlot(0).isEmpty(),
                            "Remote extraction must change the actual drive slot");
                    check(helper, driveDevice.cells().insertItem(0, extracted, false).isEmpty(),
                            "Remote reinsertion must accept the original cell");
                    var refreshed = device(grid, drive);
                    check(helper, StorageScanner.contents(StorageScanner.readCells(refreshed).get(0).storage()).get(iron) == 12345,
                            "Removing and reinserting a cell must preserve every stored item");
                    check(helper, chest.getInternalInventory().getStackInSlot(0).getCount() == 3,
                            "Remote cell operations touched the ME chest input buffer");
                    helper.setBlock(DRIVE, Blocks.AIR);
                    check(helper, !StorageScanner.isCurrent(driveDevice, grid),
                            "Removed drive must immediately fail remote-access validation");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void externalBarrelAndDetachedPart(GameTestHelper helper) {
        var controller = poweredController(helper);
        var busPos = helper.absolutePos(DRIVE);
        var barrelPos = new BlockPos(4, 2, 2);
        helper.setBlock(barrelPos, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) helper.getBlockEntity(barrelPos);
        barrel.setItem(0, new ItemStack(Items.GOLD_INGOT, 37));
        PartHelper.setPart(helper.getLevel(), busPos, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(helper.getLevel(), busPos, Direction.EAST, null, AEParts.STORAGE_BUS.asItem());
        check(helper, bus != null, "Storage bus placement failed");
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var grid = activeGrid(helper, controller);
                    check(helper, bus.getMainNode().isActive(), "External storage bus is not active");
                    check(helper, StorageScanner.gridContents(grid).get(gold) == 37, "Barrel contents not yet available");
                })
                .thenExecute(() -> {
                    var grid = activeGrid(helper, controller);
                    var device = device(grid, bus);
                    var capacity = StorageScanner.externalCapacity(device);
                    check(helper, device.kind() == StorageScanner.Kind.EXTERNAL, "Bus must be classified as external storage");
                    check(helper, capacity.occupiedSlots() == 1 && capacity.totalSlots() == 27,
                            "Barrel must report one occupied slot out of 27");
                    check(helper, capacity.fluidCapacity() == -1, "Absent fluid capacity must be unknown, not zero");
                    check(helper, StorageScanner.contents(device.storage()).get(gold) == 37,
                            "Per-device contents must reflect the storage bus inventory");
                    check(helper, device.cells() == null, "External containers must not allow remote cell operations");
                    helper.setBlock(DRIVE, Blocks.AIR);
                    check(helper, !StorageScanner.isCurrent(device, grid), "Detached part must fail stale-device validation");
                })
                .thenSucceed();
    }

    private static ControllerBlockEntity poweredController(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CONTROLLER, MEStorageController.CONTROLLER.get());
        return (ControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static IGrid activeGrid(GameTestHelper helper, ControllerBlockEntity controller) {
        var node = controller.getMainNode().getNode();
        check(helper, node != null && node.isActive(), "Controller must boot with power and an available channel");
        return node.getGrid();
    }

    private static StorageScanner.Device device(IGrid grid, Object owner) {
        return StorageScanner.discover(grid).stream().filter(device -> device.owner() == owner)
                .findFirst().orElseThrow(() -> new AssertionError("Storage device not discovered"));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
