package dev.mestorage.controller.test;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real optional OmniCells inventories, never linked against addon implementation classes. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class OmniCellsGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final AEFluidKey WATER = AEFluidKey.of(Fluids.WATER);
    private static final AEFluidKey LAVA = AEFluidKey.of(Fluids.LAVA);
    private record Fixture(ControllerBlockEntity controller, DriveBlockEntity drive) {}

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void omniFamiliesUseSharedBucketsWithoutTypeOverhead(GameTestHelper helper) {
        if (skip(helper, false)) return;
        var fixture = fixture(helper, "omni_cell_1k", "complex_omni_cell_1k", "quantum_omni_cell_1k");
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture)).thenExecute(() -> {
            for (int slot = 0; slot < 3; slot++) {
                var storage = fixture.drive().getCellInventory(slot);
                // Both item keys share one byte; both fluid keys share one byte. Per-key
                // rounding or AE2's ordinary type overhead would give a different result.
                insert(helper, storage, IRON, 1);
                insert(helper, storage, GOLD, 7);
                insert(helper, storage, WATER, 1);
                insert(helper, storage, LAVA, WATER.getAmountPerByte() - 1L);
            }
        }).thenIdle(2).thenExecute(() -> {
            var cells = StorageScanner.readCells(device(fixture));
            long[] limits = {63, 12, -2};
            for (int slot = 0; slot < 3; slot++) {
                var cell = cells.get(slot);
                var capacity = cell.capacity();
                helper.assertTrue(cell.readable() && capacity.known() && !capacity.partial(), "Omni cell must expose known readable capacity");
                helper.assertTrue(capacity.totalBytes() == 1024 && capacity.usedBytes() == 2
                        && capacity.usedTypes() == 4 && capacity.totalTypes() == limits[slot],
                        "Wrong Omni family capacity: slot=" + slot + " capacity=" + capacity);
                helper.assertTrue(StorageScanner.contents(cell.storage()).get(GOLD) == 7
                        && StorageScanner.contents(cell.storage()).get(LAVA) == WATER.getAmountPerByte() - 1L,
                        "Mixed resources must retain exact independent quantities");
                int additional = slot == 2 ? 70 : (int) limits[slot] - 4;
                var mutable = fixture.drive().getCellInventory(slot);
                for (int type = 0; type < additional; type++) insert(helper, mutable, namedStone(type), 1);
                if (slot != 2) helper.assertTrue(mutable.insert(namedStone(999), 1,
                        Actionable.MODULATE, IActionSource.empty()) == 0, "Finite family must enforce its real type limit");
            }
        }).thenIdle(2).thenExecute(() -> {
            var cells = StorageScanner.readCells(device(fixture));
            helper.assertTrue(cells.get(0).capacity().usedTypes() == 63 && cells.get(1).capacity().usedTypes() == 12
                    && cells.get(2).capacity().usedTypes() == 74 && cells.get(2).capacity().totalTypes() == -2,
                    "Quantum must exceed ordinary type limits without becoming unknown");
            var aggregate = StorageScanner.capacity(device(fixture));
            helper.assertTrue(aggregate.totalBytes() == 3072 && aggregate.totalTypes() == -2 && !aggregate.partial(),
                    "Mixed finite/unlimited drive summary must preserve unlimited type semantics");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void omniHighTierAndCreativeMetricsRetainLongPrecision(GameTestHelper helper) {
        if (skip(helper, false)) return;
        var fixture = fixture(helper, "omni_cell_256m", "creative_ae_cell_long", "creative_ae_cell_biginteger",
                "creative_ae_cell_biginteger");
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture)).thenExecute(() -> {
            insert(helper, fixture.drive().getCellInventory(0), IRON, 2_147_483_648L);
            helper.assertTrue(fixture.drive().getCellInventory(0).insert(IRON, 1, Actionable.MODULATE,
                    IActionSource.empty()) == 0, "256M finite cell must be truly full");
            insert(helper, fixture.drive().getCellInventory(1), IRON, 24_000_000_001L);
            insert(helper, fixture.drive().getCellInventory(2), IRON, 24_000_000_001L);
            insert(helper, fixture.drive().getCellInventory(3), IRON, Long.MAX_VALUE);
            insert(helper, fixture.drive().getCellInventory(3), IRON, 123);
        }).thenIdle(2).thenExecute(() -> {
            var cells = StorageScanner.readCells(device(fixture));
            helper.assertTrue(cells.get(0).capacity().totalBytes() == 268_435_456L
                    && cells.get(0).capacity().usedBytes() == 268_435_456L
                    && StorageScanner.contents(cells.get(0).storage()).get(IRON) == 2_147_483_648L,
                    "High-tier quantity must not overflow signed int");
            for (int slot = 1; slot < 3; slot++) {
                var capacity = cells.get(slot).capacity();
                helper.assertTrue(capacity.usedBytes() == 3_000_000_001L && capacity.totalBytes() == -2
                        && capacity.usedTypes() == 1 && capacity.totalTypes() == -2 && !capacity.partial(),
                        "Creative long/BigInteger metrics must remain exact and explicitly unlimited: " + capacity);
                helper.assertTrue(StorageScanner.contents(cells.get(slot).storage()).get(IRON) == 24_000_000_001L,
                        "Creative content listing truncated a long amount");
            }
            var beyondLong = cells.get(3).capacity();
            helper.assertTrue(beyondLong.usedBytes() == -1 && beyondLong.partial()
                    && beyondLong.totalBytes() == -2 && beyondLong.totalTypes() == -2 && beyondLong.usedTypes() == 1,
                    "BigInteger content beyond public long API must report unknown used bytes, never false exactness: " + beyondLong);
            helper.assertTrue(StorageScanner.contents(cells.get(3).storage()).get(IRON) == Long.MAX_VALUE,
                    "Beyond-long content must saturate safely, never wrap negative");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void omniCellScopeTransfersAndPhysicalReinsertionConserveData(GameTestHelper helper) {
        if (skip(helper, false)) return;
        var fixture = fixture(helper, "omni_cell_1k", "complex_omni_cell_1k");
        var player = helper.makeMockSurvivalPlayer();
        var absolute = helper.absolutePos(POS);
        player.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        var menu = new ControllerMenu(39, player.getInventory(), fixture.controller());
        player.containerMenu = menu;
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture)).thenExecute(() -> {
            insert(helper, fixture.drive().getCellInventory(0), IRON, 1000);
            insert(helper, fixture.drive().getCellInventory(0), WATER, 12345);
            insert(helper, fixture.drive().getCellInventory(1), IRON, 200);
            select(menu, device(fixture).id(), 1);
            for (int repeat = 0; repeat < 24; repeat++) {
                action(menu, IRON, 0);
                helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 64,
                        "Selected Omni cell must supply a real stack");
                action(menu, null, 0);
                helper.assertTrue(menu.getCarried().isEmpty(), "Empty grid tile must insert the held stack");
            }
            helper.assertTrue(amount(fixture, 0, IRON) == 1000 && amount(fixture, 1, IRON) == 200,
                    "Rapid scoped actions lost items or touched the neighbouring cell");
            var slots = device(fixture).cells();
            var removed = slots.extractItem(0, 1, false);
            helper.assertTrue(!removed.isEmpty() && fixture.drive().getInternalInventory().getStackInSlot(0).isEmpty(),
                    "Physical Omni cell removal must empty actual drive slot");
            menu.setCarried(new ItemStack(Items.GOLD_INGOT, 7));
            action(menu, null, 0);
            helper.assertTrue(menu.getCarried().getCount() == 7 && amount(fixture, 1, GOLD) == 0,
                    "Removed selected cell must never redirect deposit into another scope");
            menu.setCarried(ItemStack.EMPTY);
            helper.assertTrue(slots.insertItem(0, removed, false).isEmpty(), "Physical Omni cell must reinsert without remainder");
            helper.assertTrue(amount(fixture, 0, IRON) == 1000 && amount(fixture, 0, WATER) == 12345,
                    "Omni saved-data identity or mixed contents lost across physical removal/reinsertion");
            select(menu, device(fixture).id(), 2);
        }).thenWaitUntil(() -> {
            // The second request was deliberately sent in the first request's tick.
            // Real clients must wait for the next-tick selection acknowledgement too.
            menu.broadcastChanges();
            helper.assertTrue(menu.getSnapshot().revision() == 2 && menu.getSnapshot().selectedCell() == 0,
                    "Reinserted cell selection must acknowledge before accepting a new transfer");
        }).thenExecute(() -> {
            action(menu, IRON, 1);
            helper.assertTrue(menu.getCarried().getCount() == 32 && amount(fixture, 0, IRON) == 968,
                    "Reinserted cell must remain writable with native right-click quantity");
            action(menu, null, 0);
            helper.assertTrue(amount(fixture, 0, IRON) == 1000 && amount(fixture, 1, IRON) == 200,
                    "Reinserted-cell roundtrip must conserve all scopes");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void omniMixedMekanismFormsShareTheirAmountPerByteBucket(GameTestHelper helper) {
        if (skip(helper, true)) return;
        var fixture = fixture(helper, "omni_cell_1k");
        var keys = new AEKey[4];
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture)).thenExecute(() -> {
            String[] fields = {"gasName", "infuseTypeName", "pigmentName", "slurryName"};
            String[] names = {"hydrogen", "redstone", "red", "dirty_iron"};
            for (int form = 0; form < keys.length; form++) {
                var tag = new CompoundTag();
                tag.putString("#c", "appmek:chemical");
                tag.putByte("t", (byte) form);
                tag.putString(fields[form], "mekanism:" + names[form]);
                tag.putLong("amount", 1);
                keys[form] = AEKey.fromTagGeneric(tag);
                helper.assertTrue(keys[form] != null && keys[form].getAmountPerByte() == WATER.getAmountPerByte(),
                        "Verified AppMek chemical and fluid bucket sizes must match");
            }
            var storage = fixture.drive().getCellInventory(0);
            insert(helper, storage, IRON, 8);
            insert(helper, storage, WATER, WATER.getAmountPerByte() - 4L);
            for (var key : keys) insert(helper, storage, key, 1);
        }).thenIdle(2).thenExecute(() -> {
            var cell = StorageScanner.readCells(device(fixture)).get(0);
            helper.assertTrue(cell.capacity().usedBytes() == 2 && cell.capacity().usedTypes() == 6,
                    "Items, fluid and four chemical forms must occupy two shared value bytes without type overhead");
            for (var key : keys) {
                helper.assertTrue(StorageScanner.contents(cell.storage()).get(key) == 1,
                        "All four real chemical forms must remain separately visible");
                helper.assertTrue(fixture.drive().getCellInventory(0).extract(key, 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                        "Chemical key must support real extraction");
                insert(helper, fixture.drive().getCellInventory(0), key, 1);
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void omniMixedCellMenuContainerRoundtripsPreserveScope(GameTestHelper helper) {
        if (skip(helper, true)) return;
        var fixture = fixture(helper, "omni_cell_1k", "omni_cell_1k");
        var player = helper.makeMockSurvivalPlayer();
        var absolute = helper.absolutePos(POS);
        player.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        var menu = new ControllerMenu(40, player.getInventory(), fixture.controller());
        player.containerMenu = menu;
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture)).thenExecute(() -> {
            var tag = new CompoundTag();
            tag.putString("#c", "appmek:chemical");
            tag.putByte("t", (byte) 0);
            tag.putString("gasName", "mekanism:hydrogen");
            tag.putLong("amount", 1);
            var hydrogen = AEKey.fromTagGeneric(tag);
            var tank = BuiltInRegistries.ITEM.get(new ResourceLocation("mekanism", "basic_chemical_tank"));
            helper.assertTrue(hydrogen != null && ContainerItemStrategies.isKeySupported(hydrogen) && tank != Items.AIR,
                    "Real AppMek hydrogen strategy and Mekanism tank must be registered");
            insert(helper, fixture.drive().getCellInventory(0), WATER, 23456);
            insert(helper, fixture.drive().getCellInventory(0), hydrogen, 2345);
            insert(helper, fixture.drive().getCellInventory(0), IRON, 1000);
            insert(helper, fixture.drive().getCellInventory(1), WATER, 7890);
            insert(helper, fixture.drive().getCellInventory(1), hydrogen, 321);
            insert(helper, fixture.drive().getCellInventory(1), IRON, 200);
            select(menu, device(fixture).id(), 1);

            menu.setCarried(new ItemStack(Items.BUCKET));
            action(menu, WATER, 0);
            var bucketContents = ContainerItemStrategies.getContainedStack(menu.getCarried());
            helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET) && bucketContents != null
                    && bucketContents.what().equals(WATER) && bucketContents.amount() == 1000
                    && amount(fixture, 0, WATER) == 22456,
                    "Selected mixed Omni cell must fill a real bucket with exactly 1000 mB");
            action(menu, null, 1);
            helper.assertTrue(menu.getCarried().is(Items.BUCKET) && menu.getCarried().getCount() == 1
                    && ContainerItemStrategies.getContainedStack(menu.getCarried()) == null
                    && amount(fixture, 0, WATER) == 23456,
                    "Right-click empty tile must empty the bucket into the selected mixed cell");

            menu.setCarried(new ItemStack(tank));
            action(menu, hydrogen, 0);
            var tankContents = ContainerItemStrategies.getContainedStack(menu.getCarried());
            helper.assertTrue(menu.getCarried().is(tank) && menu.getCarried().getCount() == 1
                    && tankContents != null && tankContents.what().equals(hydrogen) && tankContents.amount() == 1000
                    && amount(fixture, 0, hydrogen) == 1345,
                    "Omni cell must fill the real basic tank at its native 1000-unit rate");
            action(menu, null, 1);
            helper.assertTrue(menu.getCarried().is(tank) && menu.getCarried().getCount() == 1
                    && ContainerItemStrategies.getContainedStack(menu.getCarried()) == null
                    && amount(fixture, 0, hydrogen) == 2345,
                    "Right-click empty tile must return all tank chemicals to the same mixed cell");
            helper.assertTrue(amount(fixture, 0, WATER) == 23456 && amount(fixture, 0, IRON) == 1000
                    && amount(fixture, 1, WATER) == 7890 && amount(fixture, 1, hydrogen) == 321
                    && amount(fixture, 1, IRON) == 200,
                    "Container transfers must preserve co-stored resources and every neighbouring-cell quantity");
        }).thenSucceed();
    }

    private static boolean skip(GameTestHelper helper, boolean chemicals) {
        if (ModList.get().isLoaded("ae2omnicells") && (!chemicals
                || ModList.get().isLoaded("appmek") && ModList.get().isLoaded("mekanism"))) return false;
        org.slf4j.LoggerFactory.getLogger(OmniCellsGameTests.class).info(
                "ME_STORAGE_OMNI_TEST SKIP: optional OmniCells{} integration is not loaded", chemicals ? "+AppMek" : "");
        helper.succeed();
        return true;
    }

    private static Fixture fixture(GameTestHelper helper, String... names) {
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, MEStorageController.CONTROLLER.get());
        helper.setBlock(POS.east(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(POS.east());
        for (int slot = 0; slot < names.length; slot++) {
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation("ae2omnicells", names[slot]));
            helper.assertTrue(item != Items.AIR, "Missing real OmniCells item: " + names[slot]);
            drive.getInternalInventory().setItemDirect(slot, new ItemStack(item));
        }
        return new Fixture((ControllerBlockEntity) helper.getBlockEntity(POS), drive);
    }

    private static void ready(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.controller().getMainNode().isActive() && fixture.drive().getMainNode().isActive(),
                "Real OmniCells drive must boot");
    }

    private static StorageScanner.Device device(Fixture fixture) {
        return StorageScanner.discover(fixture.controller().getMainNode().getGrid()).stream()
                .filter(device -> device.owner() == fixture.drive()).findFirst().orElseThrow();
    }

    private static AEItemKey namedStone(int index) {
        var stack = new ItemStack(Items.STONE);
        stack.setHoverName(Component.literal("Omni type " + index));
        return AEItemKey.of(stack);
    }

    private static void insert(GameTestHelper helper, MEStorage storage, AEKey key, long amount) {
        helper.assertTrue(storage != null && storage.insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount,
                "Real Omni cell rejected exact seed amount " + amount + " for " + key);
    }

    private static long amount(Fixture fixture, int slot, AEKey key) {
        return StorageScanner.contents(fixture.drive().getCellInventory(slot)).get(key);
    }

    private static void select(ControllerMenu menu, String device, long revision) {
        menu.handleRequest(new Network.Request(menu.containerId, device, 0, 0, 0, "", "", true, List.of(), List.of(), revision));
        menu.broadcastChanges();
    }

    private static void action(ControllerMenu menu, AEKey key, int button) {
        var snapshot = menu.getSnapshot();
        menu.handleContentAction(new Network.ContentAction(menu.containerId, snapshot.revision(), snapshot.selectedDevice(),
                snapshot.selectedCell(), key, button, false));
    }
}
