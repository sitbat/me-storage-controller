package dev.mestorage.controller.test;

import java.util.ArrayList;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.IConfigInvHost;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.LoggerFactory;

/** Optional runtime integration tests; the distributed mod has no EAE linkage. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ExtendedDriveGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extendedDriveTwentiethCell(GameTestHelper helper) {
        if (skipWithoutEae(helper)) return;
        var controller = controller(helper);
        var drivePos = CONTROLLER.east();
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("expatternprovider", "ex_drive"));
        helper.assertTrue(block != null && block != Blocks.AIR, "EAE ex_drive block is missing");
        helper.setBlock(drivePos, block);
        var drive = (DriveBlockEntity) helper.getBlockEntity(drivePos);
        helper.assertTrue(drive.getCellCount() == 20, "Expected the verified 20-slot EAE drive");
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(19, AEItems.ITEM_CELL_64K.stack());
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    grid(helper, controller);
                    helper.assertTrue(drive.getMainNode().isActive(), "Extended drive is not online");
                })
                .thenExecute(() -> helper.assertTrue(drive.getCellInventory(19).insert(iron, 12345,
                        Actionable.MODULATE, IActionSource.empty()) == 12345, "Last cell could not receive exact contents"))
                .thenIdle(2)
                .thenExecute(() -> {
                    var grid = grid(helper, controller);
                    var device = device(grid, drive);
                    helper.assertTrue(device.kind() == StorageScanner.Kind.DRIVE, "Extended drive not classified as drive");
                    helper.assertTrue(StorageScanner.cellCount(device) == 20 && device.cells().getSlots() == 20,
                            "Scanner must expose every extended drive slot");
                    var cells = StorageScanner.readCells(device);
                    helper.assertTrue(cells.size() == 20 && cells.get(19).slot() == 19, "Last cell is not addressable");
                    helper.assertTrue(StorageScanner.contents(cells.get(19).storage()).get(iron) == 12345,
                            "Last cell exact contents were truncated or read from the wrong slot");
                    helper.assertTrue(cells.get(19).capacity().usedBytes() == 2056
                                    && cells.get(19).capacity().totalBytes() == 65536
                                    && cells.get(19).capacity().usedTypes() == 1,
                            "Last cell byte/type capacity is incorrect");
                    helper.assertTrue(StorageScanner.capacity(device).totalBytes() == 66560,
                            "Drive summary must include first and twentieth cells");
                    var extracted = device.cells().extractItem(19, 1, false);
                    helper.assertTrue(!extracted.isEmpty() && drive.getInternalInventory().getStackInSlot(19).isEmpty(),
                            "Extracting remote slot 19 must modify actual slot 19");
                    helper.assertTrue(device.cells().insertItem(19, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
                            "Empty last slot did not accept replacement cell");
                    var replacement = device.cells().extractItem(19, 1, false);
                    helper.assertTrue(replacement.is(AEItems.ITEM_CELL_1K.asItem()), "Swap extracted the wrong replacement");
                    helper.assertTrue(device.cells().insertItem(19, extracted, false).isEmpty(), "Original last cell was lost");
                    var refreshed = device(grid, drive);
                    helper.assertTrue(StorageScanner.contents(StorageScanner.readCells(refreshed).get(19).storage()).get(iron) == 12345,
                            "Last-cell swap changed stored content");
                    helper.assertTrue(drive.getInternalInventory().getStackInSlot(0).is(AEItems.ITEM_CELL_1K.asItem()),
                            "Last-cell actions modified the first cell");
                    helper.setBlock(drivePos, Blocks.AIR);
                    helper.assertTrue(!StorageScanner.isCurrent(device, grid), "Removed EAE drive retained remote access");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extendedStorageBusKindsAndFilters(GameTestHelper helper) {
        if (skipWithoutEae(helper)) return;
        var controller = controller(helper);
        var names = new String[]{"tag_storage_bus", "mod_storage_bus", "precise_storage_bus"};
        var sides = new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH};
        var buses = new ArrayList<AEBasePart>();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        for (int index = 0; index < names.length; index++) {
            var side = sides[index];
            var busPos = CONTROLLER.relative(side);
            var targetPos = busPos.relative(side);
            helper.setBlock(targetPos, Blocks.BARREL);
            var barrel = (BarrelBlockEntity) helper.getBlockEntity(targetPos);
            barrel.setCustomName(Component.literal("EAE target " + index));
            barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 37));
            barrel.setItem(1, index == 1 ? AEItems.ITEM_CELL_1K.stack() : new ItemStack(Items.GOLD_INGOT, 11));
            var partItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("expatternprovider", names[index]));
            helper.assertTrue(partItem instanceof IPartItem<?>, "EAE storage bus item is missing: " + names[index]);
            PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
            var part = (AEBasePart) PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), side, null,
                    (IPartItem<?>) partItem);
            helper.assertTrue(part != null, "EAE storage bus could not be placed");
            var settings = new CompoundTag();
            if (index == 0) settings.putString("ore_dict_exp", "forge:ingots/iron");
            if (index == 1) settings.putString("mod_name_exp", "minecraft");
            part.importSettings(SettingsFrom.MEMORY_CARD, settings, null);
            if (part instanceof IConfigInvHost host) host.getConfig().setStack(0, new GenericStack(iron, 64));
            buses.add(part);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var grid = grid(helper, controller);
                    for (var bus : buses) helper.assertTrue(bus.getMainNode().isActive(), "EAE bus is not online");
                    helper.assertTrue(StorageScanner.gridContents(grid).get(iron) == 111, "EAE filtered inventories have not mounted");
                })
                .thenExecute(() -> {
                    var grid = grid(helper, controller);
                    for (int index = 0; index < buses.size(); index++) {
                        var device = device(grid, buses.get(index));
                        helper.assertTrue(device.kind() == StorageScanner.Kind.EXTERNAL, "EAE bus was classified as generic storage");
                        var contents = StorageScanner.contents(device.storage());
                        helper.assertTrue(contents.get(iron) == 37, "EAE bus lost exact visible iron amount");
                        helper.assertTrue(contents.get(AEItemKey.of(Items.GOLD_INGOT)) == 0
                                        && contents.get(AEItemKey.of(AEItems.ITEM_CELL_1K.asItem())) == 0,
                                "Scanner bypassed the EAE bus extraction filter");
                        var capacity = StorageScanner.externalCapacity(device);
                        helper.assertTrue(capacity.totalSlots() == 27 && capacity.occupiedSlots() == 2,
                                "Physical capacity must include filtered-out occupied slots");
                        var target = StorageScanner.targetInfo(device);
                        helper.assertTrue(target != null && target.name().getString().equals("EAE target " + index),
                                "EAE target custom name was not preserved");
                        helper.assertTrue(target.face() == sides[index].getOpposite()
                                        && target.location().pos().equals(helper.absolutePos(CONTROLLER.relative(sides[index], 2))),
                                "EAE target location/access face is wrong");
                        helper.assertTrue(target.adapter().equals("items"), "EAE barrel adapter must be item storage");
                    }
                }).thenSucceed();
    }

    private static boolean skipWithoutEae(GameTestHelper helper) {
        if (ModList.get().isLoaded("expatternprovider")) return false;
        LoggerFactory.getLogger(ExtendedDriveGameTests.class).info("Optional ExtendedAE test skipped: expatternprovider is not loaded");
        helper.succeed();
        return true;
    }

    private static ControllerBlockEntity controller(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CONTROLLER, MEStorageController.CONTROLLER.get());
        return (ControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static IGrid grid(GameTestHelper helper, ControllerBlockEntity controller) {
        var node = controller.getMainNode().getNode();
        helper.assertTrue(node != null && node.isActive(), "Controller must be online");
        return node.getGrid();
    }

    private static StorageScanner.Device device(IGrid grid, Object owner) {
        return StorageScanner.discover(grid).stream().filter(device -> device.owner() == owner)
                .findFirst().orElseThrow(() -> new AssertionError("Expected storage device not discovered"));
    }
}
