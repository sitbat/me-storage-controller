package dev.mestorage.controller.test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.energy.IEnergyService;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.config.ControllerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional real Flux Networks integration. No Flux classes are required on the baseline classpath. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class FluxEnergyGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final BlockPos POINT_POS = POS.east(2);
    private record Fixture(ControllerBlockEntity controller, BlockEntity point, BlockEntity provider,
                           Object network, Object networkData, EnergyBypassGameTests.SourceBlockEntity source) {
        IEnergyService energy() { return controller.getMainNode().getGrid().getEnergyService(); }
        Object pointHandler() { return invoke(point, "getTransferHandler"); }
        Object providerHandler() { return invoke(provider, "getTransferHandler"); }
        long buffers() { return number(invoke(pointHandler(), "getBuffer")) + number(invoke(providerHandler(), "getBuffer")); }
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "me_storage_flux_storage")
    public static void realFluxStorageSimulationRateLimitAndConservation(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h, false);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            removeBatteryAndDrain(h, f);
            invoke(f.pointHandler(), "setLimit", 200_000L);
            invoke(f.providerHandler(), "setLimit", 300_000L);
            invoke(f.providerHandler(), "addToBuffer", 500_000L);
            var cap = f.point.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElseThrow(IllegalStateException::new);
            check(h, !cap.canExtract() && cap.extractEnergy(1000, false) == 0, "Fixture must exercise a real push-only Flux Point, not a fake FE provider");
            // Local native-method comparison before any AE bypass demand exists. The
            // actual adjacent receiver supplies mDesired; no field or buffer is fabricated.
            invoke(f.pointHandler(), "onCycleStart");
            invoke(f.pointHandler(), "onCycleEnd");
            long nativeRequest = number(invoke(f.pointHandler(), "getRequest"));
            check(h, nativeRequest > 0, "Ordinary Point regression requires positive native receiver demand");
            setEnabled(true);
            check(h, number(invoke(f.pointHandler(), "getRequest")) == nativeRequest,
                    "Merely enabling bypass must not change a Point with no direct AE demand");
            double request = ae(250_000);
            check(h, request > f.energy().getMaxStoredPower(), "Flux test request must exceed all remaining AE cache capacity");
            long before = f.buffers();
            near(h, extract(f, request, Actionable.SIMULATE), 0, "Demand must not borrow storage energy before native Flux allocation");
            check(h, f.buffers() == before, "Flux demand simulation consumed paid network energy");
        }).thenIdle(2).thenExecute(() -> {
            long pointBefore = number(invoke(f.pointHandler(), "getBuffer"));
            check(h, pointBefore > 100_000, "Native Flux cycles must move storage energy into the actual Point buffer");
            invoke(f.pointHandler(), "setLimit", 100_000L);
            double cachedBefore = cached(f);
            double expected = cachedBefore + ae(100_000);
            double before = ae(f.buffers()) + cachedBefore;
            near(h, extract(f, ae(500_000), Actionable.SIMULATE), expected, "Simulation must respect paid Point energy and its remaining limit");
            near(h, ae(f.buffers()) + cached(f), before, "Flux simulation consumed paid network energy");
            near(h, extract(f, ae(500_000), Actionable.MODULATE), expected, "Paid Point buffer must satisfy demand up to its limit");
            near(h, before - ae(f.buffers()) - cached(f), expected, "Flux storage/Point/AE cache conservation failed");
            check(h, number(invoke(f.pointHandler(), "getBuffer")) > 0, "Rate-limit regression requires leftover paid Point energy");
            near(h, extract(f, ae(1000), Actionable.MODULATE), 0, "Repeated same-tick request bypassed Point transfer budget");
            setEnabled(false);
            long nativeRequest = number(invoke(f.pointHandler(), "getRequest"));
            setEnabled(true);
            check(h, number(invoke(f.pointHandler(), "getRequest")) > nativeRequest,
                    "Expiry regression requires a real active direct-demand reserve before disconnecting");
            // Stop new AE requests, then let the native world scheduler retire current
            // demand and all four history entries. Keep the option enabled throughout.
            h.setBlock(POS.east(), Blocks.AIR);
        }).thenIdle(5).thenExecute(() -> {
            long expiredRequest = number(invoke(f.pointHandler(), "getRequest"));
            setEnabled(false);
            long nativeRequest = number(invoke(f.pointHandler(), "getRequest"));
            setEnabled(true);
            check(h, expiredRequest == nativeRequest && number(invoke(f.pointHandler(), "getRequest")) == nativeRequest,
                    "Direct demand must naturally expire after four history cycles and restore native Point requests: native="
                            + nativeRequest + ", enabled=" + expiredRequest);
            setEnabled(false);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "me_storage_flux_limits")
    public static void realFluxProviderLimitAndDisconnectAreRespected(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h, false);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            removeBatteryAndDrain(h, f);
            invoke(f.pointHandler(), "setLimit", 200_000L);
            invoke(f.providerHandler(), "setLimit", 4000L);
            invoke(f.providerHandler(), "addToBuffer", 100_000L);
            f.energy().injectPower(f.energy().getMaxStoredPower(), Actionable.MODULATE);
            double nativeCache = cached(f);
            setEnabled(true);
            near(h, extract(f, ae(20_000), Actionable.SIMULATE), nativeCache, "Only existing AE cache is available before native Flux allocation");
            check(h, f.buffers() == 100_000L, "Limited simulation changed real storage");
        }).thenIdle(1).thenExecute(() -> {
            long moved = 100_000 - number(invoke(f.providerHandler(), "getBuffer"));
            check(h, moved > 0 && moved <= 4000, "One native Flux cycle must obey real source output limit: moved=" + moved);
            check(h, number(invoke(f.pointHandler(), "getBuffer")) > 0, "Disconnect regression needs real leftover paid Point energy");
            double paid = cached(f) + ae(number(invoke(f.pointHandler(), "getBuffer")));
            near(h, extract(f, ae(20_000), Actionable.SIMULATE), paid, "Only actually allocated Point energy may be offered");
            invoke(f.point, "disconnect");
            long before = f.buffers();
            double nativeCache = cached(f);
            near(h, extract(f, ae(20_000), Actionable.MODULATE), nativeCache, "Disconnected Flux Point supplied stale buffered energy");
            check(h, before == f.buffers(), "Disconnect consumed Flux storage");
            setEnabled(false);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "me_storage_flux_long")
    public static void nativeFluxLongBufferSuppliesMoreThanIntegerMaxFe(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h, false);
        final long requestFe = 3_000_000_000L;
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            // Flux officially supports long-valued configurable storage capacities. Increase
            // that real capacity for this isolated test, then restore it on pass or failure.
            long oldCapacity = configLong("basicCapacity");
            cleanupOnCompletion(h, () -> setConfigLong("basicCapacity", oldCapacity));
            setConfigLong("basicCapacity", 10_000_000_000L);
            removeBatteryAndDrain(h, f);
            invoke(f.pointHandler(), "setLimit", 5_000_000_000L);
            invoke(f.providerHandler(), "setLimit", 10_000_000_000L);
            check(h, number(invoke(f.providerHandler(), "getMaxEnergyStorage")) >= 10_000_000_000L, "Actual Flux storage must support the configured long capacity");
            invoke(f.providerHandler(), "addToBuffer", 10_000_000_000L);
            setEnabled(true);
            near(h, extract(f, ae(requestFe), Actionable.SIMULATE), 0, "Long demand must wait for native Flux allocation");
        }).thenIdle(2).thenExecute(() -> {
            check(h, number(invoke(f.pointHandler(), "getBuffer")) > Integer.MAX_VALUE, "Native Flux allocation must produce a real Point buffer above int FE");
            double before = ae(f.buffers()) + cached(f);
            near(h, extract(f, ae(requestFe), Actionable.SIMULATE), ae(requestFe), "Long Flux simulation was truncated to int FE");
            near(h, ae(f.buffers()) + cached(f), before, "Long simulation consumed energy");
            near(h, extract(f, ae(requestFe), Actionable.MODULATE), ae(requestFe), "One extraction must support more than Integer.MAX_VALUE FE");
            near(h, before - ae(f.buffers()) - cached(f), ae(requestFe), "Long Flux extraction violated exact FE/AE conservation");
            setEnabled(false);
        }).thenSucceed();
    }

    // This test deliberately spans world ticks with the server option enabled. A separate
    // vanilla GameTest batch prevents concurrent default-off cases from sharing that setting.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "me_storage_flux_async")
    public static void actualFeSourcePushesThroughPlugAndPointBeyondAeCache(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h, true);
        final double request = ae(200_000);
        long[] lastConsumerTick = {-1};
        var sequence = h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            removeBatteryAndDrain(h, f);
            // Player demand is 200000 FE/tick. Reserve 128 FE for the actual AE grid's
            // controller/channel idle cost; that cost must still count against Point output.
            invoke(f.pointHandler(), "setLimit", 200_128L);
            invoke(f.providerHandler(), "setLimit", 400_000L);
            f.source.configureEnergy(4_000_000, 500_000);
            f.source.pushTo(Direction.WEST, 500_000);
            setEnabled(true);
            check(h, request > f.energy().getMaxStoredPower() * 4, "Wireless request must substantially exceed AE cache capacity");
            traceSupply(h, f, "before-warmup-simulate");
            near(h, extract(f, request, Actionable.SIMULATE), 0, "Initial demand cannot claim unpaid energy still in the upstream FE source");
            traceSupply(h, f, "after-warmup-simulate");
            check(h, f.source.storedEnergy() == 4_000_000 && f.buffers() == 0, "Demand advertisement must not create or debit energy");
        }).thenIdle(3).thenExecute(() -> {
            check(h, f.source.storedEnergy() < 4_000_000, "Real source ticker must push finite FE into the real Flux Plug");
            double before = ae((long) f.source.storedEnergy() + f.buffers()) + cached(f);
            int sourceBefore = f.source.storedEnergy();
            long buffersBefore = f.buffers();
            double cacheBefore = cached(f);
            traceSupply(h, f, "before-first-simulate");
            near(h, extract(f, request, Actionable.SIMULATE), request, "Demand window did not let native Flux cycles replenish beyond the AE small buffer");
            check(h, sourceBefore == f.source.storedEnergy() && buffersBefore == f.buffers(), "Wireless simulation consumed upstream or Flux energy");
            near(h, cached(f), cacheBefore, "Wireless simulation consumed AE cache");
            traceSupply(h, f, "before-first-modulate");
            near(h, extract(f, request, Actionable.MODULATE), request, "Paid energy from the real Plug/Point chain must satisfy the large AE request");
            traceSupply(h, f, "after-first-modulate");
            double after = ae((long) f.source.storedEnergy() + f.buffers()) + cached(f);
            near(h, before - after, request, "Wireless FE source + Plug + Point + AE storage must conserve energy");
            lastConsumerTick[0] = h.getLevel().getGameTime();
        });
        // Actual consecutive world ticks exercise steady-state refill. Each synchronous
        // before/after pair excludes AE's legitimate idle draw between those ticks.
        for (int i = 0; i < 8; i++) {
            final int iteration = i + 1;
            sequence.thenIdle(1).thenExecute(() -> {
                long tick = h.getLevel().getGameTime();
                check(h, tick == lastConsumerTick[0] + 1, "Sustained demand must occur on consecutive real ticks: iteration=" + iteration);
                lastConsumerTick[0] = tick;
                long point = number(invoke(f.pointHandler(), "getBuffer"));
                long plug = number(invoke(f.providerHandler(), "getBuffer"));
                double before = ae((long) f.source.storedEnergy() + f.buffers()) + cached(f);
                traceSupply(h, f, "before-sustained-" + iteration);
                double actual = extract(f, request, Actionable.MODULATE);
                traceSupply(h, f, "after-sustained-" + iteration + "-deliveredAE=" + actual);
                near(h, actual, request, "Sustained Flux supply skipped or underfilled a tick: iteration=" + iteration
                        + ", sourceFE=" + f.source.storedEnergy() + ", plugFE=" + plug + ", pointFE=" + point);
                double after = ae((long) f.source.storedEnergy() + f.buffers()) + cached(f);
                near(h, before - after, request, "Sustained synchronous energy conservation failed: iteration=" + iteration);
            });
        }
        sequence.thenExecute(() -> {
            check(h, f.source.storedEnergy() > 0, "Sustained supply fixture must retain source energy, so source exhaustion cannot hide a refill regression");
            setEnabled(false);
            double nativeCache = cached(f);
            long buffersBefore = f.buffers();
            int sourceBefore = f.source.storedEnergy();
            near(h, extract(f, request, Actionable.MODULATE), nativeCache, "Live disable must immediately return to native cached supply");
            check(h, sourceBefore == f.source.storedEnergy() && buffersBefore == f.buffers(), "Disabled extraction still drained Flux external energy");
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper h, boolean plug) {
        h.setBlock(POS.below(), AEBlocks.ENERGY_CELL.block());
        ((EnergyCellBlockEntity) h.getBlockEntity(POS.below())).injectAEPower(10000, Actionable.MODULATE);
        h.setBlock(POS, MEStorageController.CONTROLLER.get());
        h.setBlock(POS.east(), AEBlocks.ENERGY_ACCEPTOR.block());
        h.setBlock(POINT_POS, block("flux_point"));
        var providerPos = new BlockPos(4, 2, 4);
        h.setBlock(providerPos, block(plug ? "flux_plug" : "basic_flux_storage"));
        var point = h.getBlockEntity(POINT_POS);
        var provider = h.getBlockEntity(providerPos);
        Object data = invoke(type("sonar.fluxnetworks.common.connection.FluxNetworkData"), "getInstance");
        Object security = type("sonar.fluxnetworks.api.network.SecurityLevel").getEnumConstants()[0];
        Object network = invoke(data, "createNetwork", h.makeMockSurvivalPlayer(), "ME energy test", 0x34c8ef, security, "");
        check(h, network != null, "Flux failed to create an actual test network");
        boolean previous = ControllerConfig.bypassAeEnergyLimit();
        cleanupOnCompletion(h, () -> {
            setEnabled(previous);
            invoke(data, "deleteNetwork", network);
        });
        check(h, Boolean.TRUE.equals(invoke(point, "connect", network)) && Boolean.TRUE.equals(invoke(provider, "connect", network)), "Actual Flux devices failed to connect");
        EnergyBypassGameTests.SourceBlockEntity source = null;
        if (plug) {
            h.setBlock(providerPos.east(), EnergyBypassGameTests.sourceBlock());
            source = (EnergyBypassGameTests.SourceBlockEntity) h.getBlockEntity(providerPos.east());
        }
        return new Fixture((ControllerBlockEntity) h.getBlockEntity(POS), point, provider, network, data, source);
    }
    private static void ready(GameTestHelper h, Fixture f) {
        check(h, f.controller.getMainNode().isActive(), "AE fixture must boot");
        var devices = (List<?>) invoke(f.network, "getLogicalDevices", 0);
        check(h, devices.contains(f.point) && devices.contains(f.provider), "Native Flux cycle must process actual connection queue");
    }
    private static void removeBatteryAndDrain(GameTestHelper h, Fixture f) {
        setEnabled(false);
        h.setBlock(POS.below(), Blocks.AIR);
        extract(f, 1.0e12, Actionable.MODULATE);
    }
    private static Block block(String path) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("fluxnetworks", path));
        if (block == null || block == Blocks.AIR) throw new IllegalStateException("Missing real Flux block: " + path);
        return block;
    }
    private static boolean skip(GameTestHelper h) {
        if (ModList.get().isLoaded("fluxnetworks")) return false;
        System.out.println("ME_STORAGE_FLUX_TEST_SKIPPED: Flux Networks is not installed"); h.succeed(); return true;
    }
    private static double ae(long fe) { return PowerUnits.FE.convertTo(PowerUnits.AE, fe); }
    private static void traceSupply(GameTestHelper h, Fixture f, String phase) {
        System.out.println("ME_STORAGE_FLUX_SUPPLY_TRACE phase=" + phase
                + " gameTick=" + h.getLevel().getGameTime()
                + " nativeCachedAE=" + cached(f)
                + " pointBufferFE=" + number(invoke(f.pointHandler(), "getBuffer"))
                + " pointLimitFE=" + number(invoke(f.pointHandler(), "getLimit"))
                + " plugBufferFE=" + number(invoke(f.providerHandler(), "getBuffer"))
                + " sourceFE=" + f.source.storedEnergy());
    }
    private static double extract(Fixture f, double amount, Actionable mode) { return f.energy().extractAEPower(amount, mode, PowerMultiplier.ONE); }
    private static double cached(Fixture f) {
        boolean previous = ControllerConfig.bypassAeEnergyLimit();
        try { setEnabled(false); return extract(f, 1.0e12, Actionable.SIMULATE); }
        finally { setEnabled(previous); }
    }
    private static long configLong(String name) {
        try { return type("sonar.fluxnetworks.FluxConfig").getField(name).getLong(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void setConfigLong(String name, long value) {
        try { type("sonar.fluxnetworks.FluxConfig").getField(name).setLong(null, value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void setEnabled(boolean enabled) { ControllerConfig.BYPASS_AE_ENERGY_LIMIT.set(enabled); }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        check(h, Math.abs(actual - expected) < 1.0e-6, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }
    private static long number(Object value) { return ((Number) value).longValue(); }
    private static Class<?> type(String name) {
        try { return Class.forName(name); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(name, failure); }
    }
    private static Object invoke(Object target, String name, Object... arguments) {
        Class<?> owner = target instanceof Class<?> clazz ? clazz : target.getClass();
        for (var method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            boolean compatible = true;
            for (int i = 0; i < arguments.length; i++) {
                Class<?> parameter = method.getParameterTypes()[i];
                if (parameter == long.class) parameter = Long.class;
                else if (parameter == int.class) parameter = Integer.class;
                else if (parameter == boolean.class) parameter = Boolean.class;
                if (arguments[i] != null && !parameter.isInstance(arguments[i])) compatible = false;
            }
            if (!compatible) continue;
            try { return method.invoke(target instanceof Class<?> ? null : target, arguments); }
            catch (InvocationTargetException failure) { throw new IllegalStateException(owner.getName() + "." + name, failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(owner.getName() + "." + name, failure); }
        }
        throw new IllegalStateException("Missing actual Flux API: " + owner.getName() + "." + name);
    }
    private static void cleanupOnCompletion(GameTestHelper helper, Runnable cleanup) {
        // GameTestHelper exposes no completion listener; development-only reflection obtains
        // its actual test info so timeout/failure restores the global server setting as well.
        try {
            for (var field : GameTestHelper.class.getDeclaredFields()) {
                if (field.getType() != GameTestInfo.class) continue;
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                    private boolean completed;
                    private void finish() { if (!completed) { completed = true; cleanup.run(); } }
                    @Override public void testStructureLoaded(GameTestInfo info) {}
                    @Override public void testPassed(GameTestInfo info) { finish(); }
                    @Override public void testFailed(GameTestInfo info) { finish(); }
                });
                return;
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Could not install test completion cleanup");
    }
}
