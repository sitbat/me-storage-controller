package dev.mestorage.controller.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import appeng.parts.networking.EnergyAcceptorPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Demand-driven FE conversion through real, currently attached AE energy acceptors. */
public final class DirectEnergySupply {
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

    private static final class Request {
        final Set<BlockEntity> sources = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<IEnergyStorage> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean pulling;
        int depth = 1;
        final int tick = serverTick();
    }

    @FunctionalInterface
    public interface SurplusSink {
        /** Same contract as AE injection: returns the unaccepted amount. */
        double inject(double amount, Actionable mode);
    }

    private record Receiver(IGridNode node, Object owner, ServerLevel level, BlockPos pos, Direction side) {}

    private DirectEnergySupply() {}

    /** One context spans all AE overlay-connected services participating in an extraction. */
    public static void beginRequest(boolean enabled) {
        var current = REQUEST.get();
        if (current != null && current.tick == serverTick()) current.depth++;
        else if (enabled) REQUEST.set(new Request());
        else REQUEST.remove();
    }

    public static void endRequest() {
        var current = REQUEST.get();
        if (current != null && --current.depth == 0) REQUEST.remove();
    }

    private static int serverTick() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Integer.MIN_VALUE : server.getTickCount();
    }

    public static double extract(IGrid grid, double requested, Actionable mode, SurplusSink surplus) {
        if (!(requested > 0) || !Double.isFinite(requested)) return 0;
        var request = REQUEST.get();
        if (request != null && request.pulling) return 0; // FE-to-AE bridges must not recurse into this path.
        boolean standalone = request == null;
        if (standalone) { request = new Request(); REQUEST.set(request); }
        request.pulling = true;
        try {
            double supplied = 0;
            // Snapshot the acceptor endpoints before invoking any third-party capability.
            // No all-node scan or retained references to a grid that might later split.
            for (var receiver : receivers(grid)) {
                if (supplied >= requested) break;
                if (!current(receiver, grid)) continue;
                for (var side : receiver.side() == null ? Direction.values() : new Direction[] { receiver.side() }) {
                    if (supplied >= requested || !current(receiver, grid)) break;
                    var pos = receiver.pos().relative(side);
                    if (!receiver.level().hasChunkAt(pos)) continue;
                    var source = receiver.level().getBlockEntity(pos);
                    if (source == null || source.isRemoved() || request.sources.contains(source)) continue;
                    var flux = FluxNetworkSupply.find(source);
                    var storage = flux == null ? source.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).orElse(null) : null;
                    if (flux == null && (storage == null || request.handlers.contains(storage) || !storage.canExtract())) continue;
                    double remaining = requested - supplied;
                    double fe = PowerUnits.AE.convertTo(PowerUnits.FE, remaining);
                    if (!(fe > 0) || !Double.isFinite(fe)) continue;
                    long apiLimit = flux == null ? Integer.MAX_VALUE : Long.MAX_VALUE;
                    long wanted = (long) Math.min(apiLimit, Math.ceil(fe));
                    double rounded = PowerUnits.FE.convertTo(PowerUnits.AE, wanted);
                    double extra = Math.max(0, rounded - remaining);
                    // FE is integral. Any rounding remainder must fit in the existing AE
                    // buffer; never invent an unlimited battery or discard accepted FE.
                    if (extra > 0 && surplus.inject(extra, Actionable.SIMULATE) > 0) {
                        wanted = (long) Math.min(apiLimit, Math.floor(fe));
                    }
                    if (wanted <= 0) continue;
                    long available = Math.max(0, Math.min(wanted, flux != null
                            ? flux.simulate(wanted) : storage.extractEnergy((int) wanted, true)));
                    if (available == 0 || !current(receiver, grid) || source.isRemoved()
                            || receiver.level().getBlockEntity(pos) != source) continue;
                    // Different sided wrappers of one block can expose the same battery.
                    // Conservatively visit that block only once per full AE extraction.
                    request.sources.add(source);
                    if (storage != null) request.handlers.add(storage);
                    long extracted = mode == Actionable.SIMULATE ? available
                            : Math.max(0, Math.min(available, flux != null ? flux.extract(available)
                            : storage.extractEnergy((int) available, false)));
                    double converted = PowerUnits.FE.convertTo(PowerUnits.AE, extracted);
                    double delivered = Math.min(remaining, converted);
                    double remainder = Math.max(0, converted - delivered);
                    if (mode == Actionable.MODULATE && remainder > 0) {
                        double rejected = surplus.inject(remainder, Actionable.MODULATE);
                        if (rejected > 1.0e-9) {
                            throw new IllegalStateException("AE buffer changed during FE conversion; unaccepted surplus: " + rejected);
                        }
                    }
                    supplied += delivered;
                }
            }
            return supplied;
        } finally {
            request.pulling = false;
            if (standalone) REQUEST.remove();
        }
    }

    private static ArrayList<Receiver> receivers(IGrid grid) {
        var result = new ArrayList<Receiver>();
        for (var type : grid.getMachineClasses()) {
            if (!EnergyAcceptorBlockEntity.class.isAssignableFrom(type)
                    && !EnergyAcceptorPart.class.isAssignableFrom(type)) continue;
            for (var node : grid.getMachineNodes(type)) {
                var owner = node.getOwner();
                if (owner instanceof EnergyAcceptorBlockEntity block && block.getLevel() instanceof ServerLevel level) {
                    result.add(new Receiver(node, block, level, block.getBlockPos(), null));
                } else if (owner instanceof EnergyAcceptorPart part && part.getBlockEntity().getLevel() instanceof ServerLevel level) {
                    result.add(new Receiver(node, part, level, part.getBlockEntity().getBlockPos(), part.getSide()));
                }
            }
        }
        return result;
    }

    private static boolean current(Receiver receiver, IGrid grid) {
        try {
            if (receiver.node().getGrid() != grid || !receiver.level().hasChunkAt(receiver.pos())) return false;
            if (receiver.owner() instanceof EnergyAcceptorBlockEntity block) {
                return !block.isRemoved() && receiver.level().getBlockEntity(receiver.pos()) == block;
            }
            var part = (EnergyAcceptorPart) receiver.owner();
            return !part.getBlockEntity().isRemoved()
                    && receiver.level().getBlockEntity(receiver.pos()) == part.getBlockEntity()
                    && part.getHost().getPart(part.getSide()) == part;
        } catch (IllegalStateException removedNode) {
            return false;
        }
    }
}
