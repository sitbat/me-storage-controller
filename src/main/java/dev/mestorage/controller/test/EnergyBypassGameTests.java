package dev.mestorage.controller.test;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.energy.IEnergyService;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.config.ControllerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Real AE grids and acceptors connected to a finite, pull-only Forge capability fixture. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class EnergyBypassGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final double EPSILON = 1.0e-6;
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MEStorageController.ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MEStorageController.ID);
    private static final RegistryObject<Block> SOURCE_BLOCK = BLOCKS.register("test_finite_fe_source", SourceBlock::new);
    private static final RegistryObject<BlockEntityType<SourceBlockEntity>> SOURCE_ENTITY = ENTITIES.register("test_finite_fe_source",
            () -> BlockEntityType.Builder.of(SourceBlockEntity::new, SOURCE_BLOCK.get()).build(null));

    /** Called reflectively by the mod entry point in development only; test classes are excluded from the jar. */
    public static void registerFixtures(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); }
    static Block sourceBlock() { return SOURCE_BLOCK.get(); }

    private record Fixture(ControllerBlockEntity controller, EnergyCellBlockEntity battery,
                           EnergyAcceptorBlockEntity acceptor, SourceBlockEntity source) {
        IEnergyService energy() { return controller.getMainNode().getGrid().getEnergyService(); }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void disabledPreservesNativeReceiverBufferAndEnablingSuppliesLargerDemand(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> withRestoredConfig(() -> {
            check(h, !ControllerConfig.bypassAeEnergyLimit(), "Fresh test server must default to bypass disabled");
            setEnabled(false); drain(f);
            f.source.energy.configure(4_000_000, Integer.MAX_VALUE, true);
            near(h, extract(f, 100, Actionable.MODULATE), 0, "Disabled mode cannot pull FE");
            check(h, f.source.energy.stored == 4_000_000, "Disabled mode changed source FE");
            var receiver = f.acceptor.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).orElseThrow(IllegalStateException::new);
            int offered = 2_000_000;
            int accepted = receiver.receiveEnergy(offered, false);
            check(h, accepted > 0 && accepted < offered, "Native receiver must still reject energy beyond real AE buffer capacity");
            near(h, extract(f, feToAe(offered), Actionable.MODULATE), feToAe(accepted), "Native extraction must be limited to accepted cached energy");
            setEnabled(true);
            double request = f.energy().getMaxStoredPower() + 1000;
            double cached = storedWithoutPull(f);
            int before = f.source.energy.stored;
            near(h, extract(f, request, Actionable.SIMULATE), request, "Enabled simulation must see demand beyond AE buffer capacity");
            check(h, f.source.energy.stored == before, "SIMULATE consumed FE");
            near(h, storedWithoutPull(f), cached, "SIMULATE changed cached AE");
            double delivered = extract(f, request, Actionable.MODULATE);
            near(h, delivered, request, "Enabled mode failed to satisfy request larger than AE buffer");
            near(h, feToAe(before - f.source.energy.stored) + cached, delivered + storedWithoutPull(f), "Large request violated FE/AE conservation");
        })).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void existingBatteryFeedsFirstAndLiveDisableStopsExternalPull(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> withRestoredConfig(() -> {
            setEnabled(false); drain(f);
            near(h, f.energy().injectPower(100, Actionable.MODULATE), 0, "Fixture could not charge existing AE cache");
            f.source.energy.configure(10000, Integer.MAX_VALUE, true);
            setEnabled(true);
            near(h, extract(f, 40, Actionable.MODULATE), 40, "Battery must supply first 40 AE");
            check(h, f.source.energy.stored == 10000 && f.source.energy.actualCalls == 0, "Bypass pulled FE while battery was sufficient");
            near(h, storedWithoutPull(f), 60, "Battery cache did not lose precisely 40 AE");
            near(h, extract(f, 100, Actionable.MODULATE), 100, "Mixed battery and FE request failed");
            near(h, feToAe(10000 - f.source.energy.stored), 40 + storedWithoutPull(f), "Mixed battery/FE request violated conservation");
            setEnabled(false); drain(f);
            int before = f.source.energy.stored;
            near(h, extract(f, 100, Actionable.MODULATE), 0, "Live disable must immediately stop pulling");
            check(h, before == f.source.energy.stored, "Live disable drained FE");
            setEnabled(true);
            near(h, extract(f, 100, Actionable.MODULATE), 100, "Live re-enable did not resume pulling");
        })).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sharedSourceIsCountedOnceAndHonorsItsExtractionLimit(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(POS.east().above(), AEBlocks.ENERGY_ACCEPTOR.block());
        h.setBlock(POS.east(2).above(), AEBlocks.ENERGY_ACCEPTOR.block());
        var second = (EnergyAcceptorBlockEntity) h.getBlockEntity(POS.east(2).above());
        h.startSequence().thenWaitUntil(() -> {
            ready(h, f);
            check(h, second.getMainNode().getGrid() == f.controller.getMainNode().getGrid(), "Both real receivers must share the same AE grid");
        }).thenExecute(() -> withRestoredConfig(() -> {
            setEnabled(false); drain(f); f.source.energy.configure(10000, 2000, true); setEnabled(true);
            near(h, extract(f, feToAe(20000), Actionable.SIMULATE), feToAe(2000), "Two receivers double-counted one rate-limited FE capability");
            check(h, f.source.energy.stored == 10000 && f.source.energy.simulateCalls == 1, "Simulation must probe shared source exactly once without draining it");
            near(h, extract(f, feToAe(20000), Actionable.MODULATE), feToAe(2000), "Modulation bypassed shared source extraction limit");
            check(h, f.source.energy.stored == 8000 && f.source.energy.actualCalls == 1, "Shared source must be drained only once per AE request");
            near(h, extract(f, feToAe(20000), Actionable.MODULATE), feToAe(2000), "Separate request must be allowed to use source again");
            check(h, f.source.energy.stored == 6000, "Independent requests must debit actual finite source storage");
        })).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fractionalFeAndPowerMultiplierConserveEnergy(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> withRestoredConfig(() -> {
            setEnabled(false); drain(f); f.source.energy.configure(10000, Integer.MAX_VALUE, true); setEnabled(true);
            double halfFe = feToAe(1) / 2;
            near(h, extract(f, halfFe, Actionable.SIMULATE), halfFe, "Fractional FE simulation failed");
            check(h, f.source.energy.stored == 10000, "Fractional simulation consumed FE");
            near(h, extract(f, halfFe, Actionable.MODULATE), halfFe, "Fractional FE request failed");
            check(h, f.source.energy.stored == 9999, "Fractional FE request must debit exactly one integer FE");
            near(h, storedWithoutPull(f), halfFe, "Rounding remainder must be retained in existing AE cache");
            near(h, extract(f, halfFe, Actionable.MODULATE), halfFe, "Next fractional request must reuse the cached remainder");
            check(h, f.source.energy.stored == 9999, "Cached fractional remainder was lost and FE was charged twice");
            double previousMultiplier = PowerMultiplier.CONFIG.multiplier;
            try {
                PowerMultiplier.CONFIG.multiplier = 2;
                int before = f.source.energy.stored;
                near(h, f.energy().extractAEPower(100, Actionable.SIMULATE, PowerMultiplier.CONFIG), 100, "Configured multiplier simulation returned wrong consumer units");
                check(h, before == f.source.energy.stored, "Configured multiplier simulation consumed FE");
                near(h, f.energy().extractAEPower(100, Actionable.MODULATE, PowerMultiplier.CONFIG), 100, "Configured multiplier must return consumer units once");
                near(h, feToAe(before - f.source.energy.stored), 200 + storedWithoutPull(f), "Configured multiplier must debit exactly twice the raw AE, not apply twice");
            } finally { PowerMultiplier.CONFIG.multiplier = previousMultiplier; }
        })).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void receiveOnlyEmptyAndDetachedSourcesCannotSupplyPower(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> withRestoredConfig(() -> {
            setEnabled(false); drain(f); f.source.energy.configure(10000, Integer.MAX_VALUE, false); setEnabled(true);
            near(h, extract(f, 100, Actionable.MODULATE), 0, "A receive-only FE capability cannot be pulled");
            check(h, f.source.energy.stored == 10000, "Receive-only source was changed");
            f.source.energy.configure(0, Integer.MAX_VALUE, true);
            near(h, extract(f, 100, Actionable.MODULATE), 0, "Empty source invented energy");
            f.source.energy.configure(10000, Integer.MAX_VALUE, true);
            h.setBlock(POS.east(), Blocks.AIR);
            near(h, extract(f, 100, Actionable.MODULATE), 0, "Removed energy acceptor must no longer bridge adjacent FE");
            check(h, f.source.energy.stored == 10000, "Detached source was consumed");
        })).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void disconnectedAcceptorCannotSupplyAnotherGrid(GameTestHelper h) {
        var f = fixture(h);
        h.setBlock(POS.south(2), AEBlocks.ENERGY_ACCEPTOR.block());
        h.setBlock(POS.south(3), SOURCE_BLOCK.get());
        var other = (EnergyAcceptorBlockEntity) h.getBlockEntity(POS.south(2));
        var otherSource = (SourceBlockEntity) h.getBlockEntity(POS.south(3));
        otherSource.energy.configure(10000, Integer.MAX_VALUE, true);
        h.startSequence().thenWaitUntil(() -> {
            ready(h, f);
            check(h, other.getMainNode().getGrid() != null && other.getMainNode().getGrid() != f.controller.getMainNode().getGrid(), "Fixture requires a second disconnected real AE grid");
        }).thenExecute(() -> withRestoredConfig(() -> {
            setEnabled(false); drain(f); f.source.energy.configure(0, Integer.MAX_VALUE, true); setEnabled(true);
            near(h, extract(f, 100, Actionable.MODULATE), 0, "A nearby receiver from another grid must not supply the controller grid");
            check(h, otherSource.energy.stored == 10000, "Unrelated grid's FE storage was drained");
            near(h, other.getMainNode().getGrid().getEnergyService().extractAEPower(100, Actionable.MODULATE, PowerMultiplier.ONE), 100, "The other receiver must actually work for its own grid");
            near(h, feToAe(10000 - otherSource.energy.stored), 100, "Other grid consumed an incorrect amount of FE");
        })).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(POS.below(), AEBlocks.ENERGY_CELL.block());
        var battery = (EnergyCellBlockEntity) h.getBlockEntity(POS.below());
        battery.injectAEPower(10000, Actionable.MODULATE);
        h.setBlock(POS, MEStorageController.CONTROLLER.get());
        h.setBlock(POS.east(), AEBlocks.ENERGY_ACCEPTOR.block());
        h.setBlock(POS.east(2), SOURCE_BLOCK.get());
        return new Fixture((ControllerBlockEntity) h.getBlockEntity(POS), battery,
                (EnergyAcceptorBlockEntity) h.getBlockEntity(POS.east()), (SourceBlockEntity) h.getBlockEntity(POS.east(2)));
    }
    private static void ready(GameTestHelper h, Fixture f) {
        check(h, f.controller.getMainNode().isActive() && f.acceptor.getMainNode().getGrid() == f.controller.getMainNode().getGrid(), "Finite AE fixture must boot and connect");
    }
    private static double feToAe(int fe) { return PowerUnits.FE.convertTo(PowerUnits.AE, fe); }
    private static double extract(Fixture f, double ae, Actionable mode) { return f.energy().extractAEPower(ae, mode, PowerMultiplier.ONE); }
    private static void drain(Fixture f) { extract(f, 1.0e12, Actionable.MODULATE); }
    private static double storedWithoutPull(Fixture f) {
        boolean previous = ControllerConfig.bypassAeEnergyLimit();
        try { setEnabled(false); return extract(f, 1.0e12, Actionable.SIMULATE); }
        finally { setEnabled(previous); }
    }
    private static void withRestoredConfig(Runnable action) {
        boolean previous = ControllerConfig.bypassAeEnergyLimit();
        try { action.run(); } finally { setEnabled(previous); }
    }
    private static void setEnabled(boolean enabled) { ControllerConfig.BYPASS_AE_ENERGY_LIMIT.set(enabled); }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        check(h, Math.abs(actual - expected) <= EPSILON, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void check(GameTestHelper h, boolean result, String message) { h.assertTrue(result, message); }

    private static final class SourceBlock extends Block implements EntityBlock {
        SourceBlock() { super(BlockBehaviour.Properties.of().strength(1)); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SourceBlockEntity(pos, state); }
        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide && type == SOURCE_ENTITY.get()
                    ? (world, pos, blockState, entity) -> ((SourceBlockEntity) entity).pushTick() : null;
        }
    }
    public static final class SourceBlockEntity extends BlockEntity {
        final FiniteEnergy energy = new FiniteEnergy();
        private final LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> energy);
        private Direction pushDirection;
        private int pushLimit;
        SourceBlockEntity(BlockPos pos, BlockState state) { super(SOURCE_ENTITY.get(), pos, state); }
        void configureEnergy(int stored, int limit) { energy.configure(stored, limit, true); }
        int storedEnergy() { return energy.stored; }
        void pushTo(Direction direction, int limit) { pushDirection = direction; pushLimit = limit; }
        private void pushTick() {
            if (pushDirection == null || level == null || energy.stored == 0) return;
            var target = level.getBlockEntity(worldPosition.relative(pushDirection));
            if (target == null) return;
            var sink = target.getCapability(ForgeCapabilities.ENERGY, pushDirection.getOpposite()).orElse(null);
            if (sink == null || !sink.canReceive()) return;
            int possible = energy.extractEnergy(pushLimit, true);
            int accepted = sink.receiveEnergy(possible, true);
            int extracted = energy.extractEnergy(accepted, false);
            int inserted = sink.receiveEnergy(extracted, false);
            energy.stored += extracted - inserted;
        }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == ForgeCapabilities.ENERGY ? capability.cast() : super.getCapability(cap, side);
        }
        @Override public void invalidateCaps() { super.invalidateCaps(); capability.invalidate(); }
    }
    private static final class FiniteEnergy implements IEnergyStorage {
        int stored, limit = Integer.MAX_VALUE, actualCalls, simulateCalls;
        boolean extractable = true;
        void configure(int stored, int limit, boolean extractable) {
            this.stored = stored; this.limit = limit; this.extractable = extractable; actualCalls = 0; simulateCalls = 0;
        }
        @Override public int extractEnergy(int requested, boolean simulate) {
            if (simulate) simulateCalls++; else actualCalls++;
            int extracted = extractable ? Math.min(stored, Math.min(limit, Math.max(0, requested))) : 0;
            if (!simulate) stored -= extracted;
            return extracted;
        }
        @Override public int receiveEnergy(int requested, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return 4_000_000; }
        @Override public boolean canExtract() { return extractable; }
        @Override public boolean canReceive() { return !extractable; }
    }
}
