package dev.mestorage.controller.energy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Uses only energy already delivered to an adjacent Flux Point by Flux's normal scheduler. */
public final class FluxNetworkSupply {
    private static final String POINT = "sonar.fluxnetworks.common.device.TileFluxPoint";
    private record TileApi(Method network, Method handler, Method position, Method dirty) {}
    private record NetworkApi(Method valid, Method connection) {}
    private static final ClassValue<Optional<TileApi>> TILES = new ClassValue<>() {
        @Override protected Optional<TileApi> computeValue(Class<?> type) {
            try {
                return Optional.of(new TileApi(type.getMethod("getNetwork"), type.getMethod("getTransferHandler"),
                        type.getMethod("getGlobalPos"), type.getMethod("markEnergyChanged")));
            } catch (NoSuchMethodException e) { return Optional.empty(); }
        }
    };
    private static final ClassValue<Optional<NetworkApi>> NETWORKS = new ClassValue<>() {
        @Override protected Optional<NetworkApi> computeValue(Class<?> type) {
            try {
                return Optional.of(new NetworkApi(type.getMethod("isValid"),
                        type.getMethod("getConnectionByPos", GlobalPos.class)));
            } catch (NoSuchMethodException e) { return Optional.empty(); }
        }
    };

    private FluxNetworkSupply() {}

    public static @Nullable Input find(BlockEntity source) {
        if (!source.getClass().getName().equals(POINT)) return null;
        var api = TILES.get(source.getClass()).orElse(null);
        if (api == null || !(invoke(api.handler(), source) instanceof FluxPointAccess point)) return null;
        Object network = invoke(api.network(), source);
        if (network == null) return null;
        var networkApi = NETWORKS.get(network.getClass()).orElse(null);
        if (networkApi == null || !Boolean.TRUE.equals(invoke(networkApi.valid(), network))) return null;
        if (invoke(networkApi.connection(), network, invoke(api.position(), source)) != source) return null;
        return new Input(source, api, point, network, networkApi);
    }

    public static final class Input {
        private final BlockEntity tile;
        private final TileApi tileApi;
        private final FluxPointAccess point;
        private final Object network;
        private final NetworkApi networkApi;

        private Input(BlockEntity tile, TileApi tileApi, FluxPointAccess point, Object network, NetworkApi networkApi) {
            this.tile = tile; this.tileApi = tileApi; this.point = point;
            this.network = network; this.networkApi = networkApi;
        }

        public long simulate(long requested) {
            if (!current()) return 0;
            // Advertising demand does not promise energy still in a plug or remote store.
            point.meStorage$request(requested);
            return Math.min(Math.max(0, requested), Math.min(point.meStorage$buffer(), point.meStorage$remainingLimit()));
        }

        public long extract(long requested) {
            if (!current()) return 0;
            long taken = point.meStorage$takeBuffer(requested);
            if (taken > 0) invoke(tileApi.dirty(), tile);
            return taken;
        }

        private boolean current() {
            return !tile.isRemoved() && tile.getLevel() instanceof ServerLevel level
                    && level.hasChunkAt(tile.getBlockPos()) && level.getBlockEntity(tile.getBlockPos()) == tile
                    && invoke(tileApi.network(), tile) == network
                    && Boolean.TRUE.equals(invoke(networkApi.valid(), network))
                    && invoke(networkApi.connection(), network, invoke(tileApi.position(), tile)) == tile;
        }
    }

    private static Object invoke(Method method, Object owner, Object... arguments) {
        try { return method.invoke(owner, arguments); }
        catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Flux Networks public API invocation failed: " + method.getName(), e);
        }
    }
}
