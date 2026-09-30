package dev.mestorage.controller.network;

import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.menu.ControllerMenu;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import appeng.api.stacks.AEKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    // Version 8 streams the complete directory separately in bounded frames.
    private static final String VERSION="8";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(MEStorageController.ID,"main"),()->VERSION,VERSION::equals,VERSION::equals);
    public static Consumer<SnapshotMessage> clientReceiver = message -> {};
    public static Consumer<DirectoryMessage> directoryReceiver = message -> {};
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
        void write(FriendlyByteBuf b) { b.writeVarInt(containerId); snapshot.withDirectory(List.of(),snapshot.directoryTotalDevices()).write(b); }
        static SnapshotMessage read(FriendlyByteBuf b) { return new SnapshotMessage(b.readVarInt(),Snapshot.read(b)); }
    }
    public record DirectoryMessage(int containerId,DirectoryTransfer.Frame frame) {
        void write(FriendlyByteBuf b) { b.writeVarInt(containerId); frame.write(b); }
        static DirectoryMessage read(FriendlyByteBuf b) { return new DirectoryMessage(b.readVarInt(),DirectoryTransfer.Frame.read(b)); }
    }
    /** Keys identify displayed entries; quantities and carried stacks are never client supplied. */
    public record ContentAction(int containerId,long revision,String deviceId,int cell,AEKey key,int button,boolean shift) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(containerId); b.writeLong(revision); b.writeUtf(deviceId,256); b.writeInt(cell);
            AEKey.writeOptionalKey(b,key); b.writeByte(button); b.writeBoolean(shift);
        }
        static ContentAction read(FriendlyByteBuf b) {
            return new ContentAction(b.readVarInt(),b.readLong(),b.readUtf(256),b.readInt(),AEKey.readOptionalKey(b),b.readUnsignedByte(),b.readBoolean());
        }
    }
    public static void register() {
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::write).decoder(Request::read)
            .consumerMainThread((message,context)-> {
                ServerPlayer player=context.get().getSender();
                if(player!=null && player.containerMenu instanceof ControllerMenu menu && menu.containerId==message.containerId()) menu.handleRequest(message);
            }).add();
        CHANNEL.messageBuilder(SnapshotMessage.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(SnapshotMessage::write).decoder(SnapshotMessage::read)
            .consumerMainThread((message,context)->clientReceiver.accept(message)).add();
        CHANNEL.messageBuilder(ContentAction.class,2,NetworkDirection.PLAY_TO_SERVER).encoder(ContentAction::write).decoder(ContentAction::read)
            .consumerMainThread((message,context)-> {
                ServerPlayer player=context.get().getSender();
                if(player!=null && player.containerMenu instanceof ControllerMenu menu && menu.containerId==message.containerId()) menu.handleContentAction(message);
            }).add();
        CHANNEL.messageBuilder(DirectoryMessage.class,3,NetworkDirection.PLAY_TO_CLIENT).encoder(DirectoryMessage::write).decoder(DirectoryMessage::read)
            .consumerMainThread((message,context)->directoryReceiver.accept(message)).add();
    }
    public static void request(Request request) { CHANNEL.sendToServer(request); }
    public static void contentAction(ContentAction action) { CHANNEL.sendToServer(action); }
    public static void send(ServerPlayer player,int containerId,Snapshot snapshot) { CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new SnapshotMessage(containerId,snapshot)); }
    public static void sendDirectory(ServerPlayer player,int containerId,DirectoryTransfer.Frame frame) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new DirectoryMessage(containerId,frame));
    }
}
