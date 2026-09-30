package dev.mestorage.controller.network;

import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.menu.ControllerMenu;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    // Click interpretation changed in 0.3; both sides must agree on inventory behavior.
    private static final String VERSION="3";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(MEStorageController.ID,"main"),()->VERSION,VERSION::equals,VERSION::equals);
    public static Consumer<SnapshotMessage> clientReceiver = message -> {};
    public record Request(int containerId,String deviceId,int cell,int devicePage,int contentPage,String deviceQuery,String contentQuery,boolean sortByAmount,
                          List<ResourceLocation> deviceMatches,List<ResourceLocation> contentMatches,long revision) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(containerId); b.writeUtf(deviceId,256); b.writeInt(cell); b.writeInt(devicePage); b.writeInt(contentPage);
            b.writeUtf(deviceQuery,64); b.writeUtf(contentQuery,64); b.writeBoolean(sortByAmount);
            writeIds(b,deviceMatches); writeIds(b,contentMatches);
            b.writeLong(revision);
        }
        static Request read(FriendlyByteBuf b) { return new Request(b.readVarInt(),b.readUtf(256),b.readInt(),b.readInt(),b.readInt(),b.readUtf(64),b.readUtf(64),b.readBoolean(),readIds(b),readIds(b),b.readLong()); }
        private static void writeIds(FriendlyByteBuf b,List<ResourceLocation> ids) { b.writeVarInt(ids.size()); for(var id:ids) b.writeResourceLocation(id); }
        private static List<ResourceLocation> readIds(FriendlyByteBuf b) {
            int size=b.readVarInt(); if(size<0 || size>512) throw new IllegalArgumentException("search size");
            var ids=new ArrayList<ResourceLocation>(size); for(int i=0;i<size;i++) ids.add(b.readResourceLocation()); return List.copyOf(ids);
        }
    }
    public record SnapshotMessage(int containerId,Snapshot snapshot) {
        void write(FriendlyByteBuf b) { b.writeVarInt(containerId); snapshot.write(b); }
        static SnapshotMessage read(FriendlyByteBuf b) { return new SnapshotMessage(b.readVarInt(),Snapshot.read(b)); }
    }
    public static void register() {
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::write).decoder(Request::read)
            .consumerMainThread((message,context)-> {
                ServerPlayer player=context.get().getSender();
                if(player!=null && player.containerMenu instanceof ControllerMenu menu && menu.containerId==message.containerId()) menu.handleRequest(message);
            }).add();
        CHANNEL.messageBuilder(SnapshotMessage.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(SnapshotMessage::write).decoder(SnapshotMessage::read)
            .consumerMainThread((message,context)->clientReceiver.accept(message)).add();
    }
    public static void request(Request request) { CHANNEL.sendToServer(request); }
    public static void send(ServerPlayer player,int containerId,Snapshot snapshot) { CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new SnapshotMessage(containerId,snapshot)); }
}
