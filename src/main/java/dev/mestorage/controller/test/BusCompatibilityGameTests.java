package dev.mestorage.controller.test;

import appeng.api.networking.IGrid;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real powered AE storage buses against vanilla capability providers. No fake inventories. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class BusCompatibilityGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 2, 2);
    private static final BlockPos BUS = new BlockPos(3, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void doubleChestIncludesFarHalf(GameTestHelper helper) {
        var controller = poweredController(helper);
        var near = BUS.east();
        var far = near.south();
        var state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        helper.setBlock(near, state.setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(far, state.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        // Both entities now exist; notify the pair with its explicit joined states.
        helper.getLevel().setBlock(helper.absolutePos(near), state.setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
        helper.getLevel().setBlock(helper.absolutePos(far), state.setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
        ((ChestBlockEntity) helper.getBlockEntity(near)).setItem(0, new ItemStack(Items.GOLD_INGOT, 13));
        ((ChestBlockEntity) helper.getBlockEntity(far)).setItem(26, new ItemStack(Items.DIAMOND, 41));
        var bus = placeBus(helper, Direction.EAST);
        helper.startSequence().thenWaitUntil(() -> {
            var grid = activeGrid(helper, controller);
            check(helper, bus.getMainNode().isActive(), "Double-chest bus must be active");
            check(helper, StorageScanner.gridContents(grid).get(AEItemKey.of(Items.DIAMOND)) == 41,
                    "Real AE bus has not discovered the far half's 41 diamonds");
        }).thenExecute(() -> {
            var grid = activeGrid(helper, controller);
            var device = device(grid, bus);
            var capacity = StorageScanner.externalCapacity(device);
            check(helper, capacity.totalSlots() == 54 && capacity.occupiedSlots() == 2,
                    "Double chest must expose both occupied slots and all 54 slots");
            var contents = StorageScanner.contents(device.storage());
            check(helper, contents.get(AEItemKey.of(Items.GOLD_INGOT)) == 13
                    && contents.get(AEItemKey.of(Items.DIAMOND)) == 41,
                    "Device contents must include near and far chest halves without duplication");
            check(helper, capacity.fluidCapacity() == -1, "Chest must not invent fluid capacity");
            check(helper, device.cells() == null, "Chest bus must never offer remote cell slots");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void hopperCapacityAndContents(GameTestHelper helper) {
        var controller = poweredController(helper);
        var target = BUS.east();
        helper.setBlock(target, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED, false));
        var hopper = (HopperBlockEntity) helper.getBlockEntity(target);
        hopper.setItem(0, new ItemStack(Items.COPPER_INGOT, 29));
        hopper.setItem(4, new ItemStack(Items.EMERALD, 3));
        var bus = placeBus(helper, Direction.EAST);
        helper.startSequence().thenWaitUntil(() -> {
            var grid = activeGrid(helper, controller);
            check(helper, bus.getMainNode().isActive(), "Hopper bus must be active");
            check(helper, StorageScanner.gridContents(grid).get(AEItemKey.of(Items.COPPER_INGOT)) == 29,
                    "Real AE bus has not mounted hopper contents");
        }).thenExecute(() -> {
            var device = device(activeGrid(helper, controller), bus);
            var capacity = StorageScanner.externalCapacity(device);
            check(helper, capacity.totalSlots() == 5 && capacity.occupiedSlots() == 2,
                    "Hopper must expose two occupied slots out of five");
            check(helper, StorageScanner.contents(device.storage()).get(AEItemKey.of(Items.EMERALD)) == 3,
                    "Hopper's final slot must remain visible with exact quantity");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void furnaceTopUsesInputFace(GameTestHelper helper) {
        furnaceFace(helper, Direction.DOWN, Items.DIAMOND, 7, 1, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void furnaceBottomUsesOutputFace(GameTestHelper helper) {
        furnaceFace(helper, Direction.UP, Items.IRON_INGOT, 11, 2, 2);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void furnaceSideUsesFuelFace(GameTestHelper helper) {
        furnaceFace(helper, Direction.EAST, Items.COAL, 6, 1, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extendedAeFluidBufferThroughRealBus(GameTestHelper helper) {
        var id = new ResourceLocation("expatternprovider", "ingredient_buffer");
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            org.slf4j.LoggerFactory.getLogger(BusCompatibilityGameTests.class).info(
                    "SKIPPED extendedAeFluidBufferThroughRealBus: ExtendedAE ingredient_buffer is not installed");
            helper.succeed();
            return;
        }
        var controller = poweredController(helper);
        var target = BUS.east();
        helper.setBlock(target, ForgeRegistries.BLOCKS.getValue(id));
        var entity = helper.getBlockEntity(target);
        check(helper, entity != null, "Actual ExtendedAE ingredient buffer must have a block entity");
        var handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).resolve().orElse(null);
        check(helper, handler != null, "Actual ExtendedAE buffer must expose its sided fluid handler");
        check(helper, handler.fill(new FluidStack(Fluids.WATER, 23456), IFluidHandler.FluidAction.EXECUTE) == 23456,
                "Actual ExtendedAE buffer must accept all 23456 mB through its fluid capability");
        long physicalCapacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) physicalCapacity += handler.getTankCapacity(tank);
        final long expectedCapacity = physicalCapacity;
        check(helper, expectedCapacity >= 23456, "Actual fluid handler must expose usable capacity");
        var bus = placeBus(helper, Direction.EAST);
        var water = AEFluidKey.of(Fluids.WATER);
        helper.startSequence().thenWaitUntil(() -> {
            var grid = activeGrid(helper, controller);
            check(helper, bus.getMainNode().isActive(), "Real fluid-buffer storage bus must be active");
            check(helper, StorageScanner.gridContents(grid).get(water) == 23456,
                    "Actual AE grid must expose the buffer's exact 23456 mB through its real storage bus");
        }).thenExecute(() -> {
            var device = device(activeGrid(helper, controller), bus);
            var capacity = StorageScanner.externalCapacity(device);
            check(helper, StorageScanner.contents(device.storage()).get(water) == 23456,
                    "Per-device fluid quantity must match the actual AE bus inventory");
            check(helper, capacity.fluidAmount() == 23456 && capacity.fluidCapacity() == expectedCapacity,
                    "Scanner must read actual fluid amount and summed tank capacity from the bus target");
            check(helper, device.cells() == null, "External fluid buffer must not expose cell-transfer slots");
        }).thenSucceed();
    }

    private static void furnaceFace(GameTestHelper helper, Direction busSide,
                                    net.minecraft.world.item.Item expectedItem, int quantity,
                                    int slots, int occupied) {
        var controller = poweredController(helper);
        var target = BUS.relative(busSide);
        helper.setBlock(target, Blocks.FURNACE);
        var furnace = (FurnaceBlockEntity) helper.getBlockEntity(target);
        // Diamonds cannot smelt, so the fuel and all quantities remain stable.
        furnace.setItem(0, new ItemStack(Items.DIAMOND, 7));
        furnace.setItem(1, new ItemStack(Items.COAL, 6));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 11));
        var bus = placeBus(helper, busSide);
        var expectedKey = AEItemKey.of(expectedItem);
        helper.startSequence().thenWaitUntil(() -> {
            var grid = activeGrid(helper, controller);
            check(helper, bus.getMainNode().isActive(), "Sided furnace bus must be active");
            check(helper, StorageScanner.gridContents(grid).get(expectedKey) == quantity,
                    "Real AE bus did not expose the expected sided furnace inventory");
        }).thenExecute(() -> {
            var device = device(activeGrid(helper, controller), bus);
            var capacity = StorageScanner.externalCapacity(device);
            check(helper, capacity.totalSlots() == slots && capacity.occupiedSlots() == occupied,
                    "Capacity must use the exact face opposite the storage bus, not all three furnace slots");
            var contents = StorageScanner.contents(device.storage());
            check(helper, contents.get(expectedKey) == quantity, "Furnace face quantity differs from live AE inventory");
            if (busSide != Direction.DOWN) check(helper, contents.get(AEItemKey.of(Items.DIAMOND)) == 0,
                    "Non-input faces must not expose input slot contents");
            if (busSide != Direction.UP) check(helper, contents.get(AEItemKey.of(Items.IRON_INGOT)) == 0,
                    "Non-output faces must not expose output slot contents");
        }).thenSucceed();
    }

    private static appeng.parts.storagebus.StorageBusPart placeBus(GameTestHelper helper, Direction side) {
        var absolute = helper.absolutePos(BUS);
        PartHelper.setPart(helper.getLevel(), absolute, null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(helper.getLevel(), absolute, side, null, AEParts.STORAGE_BUS.asItem());
        check(helper, bus instanceof appeng.parts.storagebus.StorageBusPart, "Real AE storage bus placement failed");
        return (appeng.parts.storagebus.StorageBusPart) bus;
    }

    private static ControllerBlockEntity poweredController(GameTestHelper helper) {
        helper.setBlock(CONTROLLER.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CONTROLLER, MEStorageController.CONTROLLER.get());
        return (ControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static IGrid activeGrid(GameTestHelper helper, ControllerBlockEntity controller) {
        var node = controller.getMainNode().getNode();
        check(helper, node != null && node.isActive(), "Controller must have power and a channel");
        return node.getGrid();
    }

    private static StorageScanner.Device device(IGrid grid, Object owner) {
        return StorageScanner.discover(grid).stream().filter(device -> device.owner() == owner)
                .findFirst().orElseThrow(() -> new AssertionError("Live storage bus missing from scanner"));
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
