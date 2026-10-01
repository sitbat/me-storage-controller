package dev.mestorage.controller.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;

/** Resolves screen ownership without depending on the asynchronously received storage directory. */
final class ClientTreeScope {
    private ClientTreeScope() {}
    static String resolve(Minecraft client,BlockPos controller) {
        if(controller==null||client.player==null||client.level==null) return null;
        String world;
        var integrated=client.getSingleplayerServer();
        if(integrated!=null) {
            world="local:"+integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    +"\nseed:"+integrated.getWorldData().worldGenOptions().seed();
        } else {
            var server=client.getCurrentServer();
            if(server==null||server.ip.isBlank()) return null; // No global fallback that can mix unrelated sessions.
            world="server:"+server.ip.strip().toLowerCase(Locale.ROOT);
        }
        return TreeStateStore.scope(world,client.player.getUUID().toString(),
                client.level.dimension().location().toString(),controller.asLong());
    }
}
