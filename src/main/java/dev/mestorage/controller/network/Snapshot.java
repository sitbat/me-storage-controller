package dev.mestorage.controller.network;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record Snapshot(boolean online, String error, String selectedDevice, int selectedCell,
        List<DeviceInfo> devices, int devicePage, int devicePages, int deviceCount, Component title,
        Capacity capacity, List<Content> contents, int contentPage, int contentPages, int contentCount, int cellSlots, int editableSlots) {
    public record DeviceInfo(String id, Component name, String kind, ResourceLocation dimension, BlockPos pos, boolean active) {}
    public record Capacity(long usedBytes, long totalBytes, long usedTypes, long totalTypes, int unknownCells,
                           long occupiedSlots, long totalSlots, long fluidAmount, long fluidCapacity) {
        public Capacity(long usedBytes,long totalBytes,long usedTypes,long totalTypes,int unknownCells) {
            this(usedBytes,totalBytes,usedTypes,totalTypes,unknownCells,-1,-1,-1,-1);
        }
    }
    public record Content(AEKey key, long amount) {}
    public static Snapshot empty() {
        return new Snapshot(false,"", "",-1,List.of(),0,1,0,Component.translatable("gui.me_storage_controller.network"),
            new Capacity(-1,-1,-1,-1,0), List.of(),0,1,0,0,0);
    }
    public void write(FriendlyByteBuf b) {
        b.writeBoolean(online); b.writeUtf(error,128); b.writeUtf(selectedDevice,256); b.writeInt(selectedCell);
        b.writeVarInt(devices.size());
        for (DeviceInfo d : devices) {
            b.writeUtf(d.id,256); b.writeComponent(d.name); b.writeUtf(d.kind,32);
            b.writeResourceLocation(d.dimension); b.writeBlockPos(d.pos); b.writeBoolean(d.active);
        }
        b.writeInt(devicePage); b.writeInt(devicePages); b.writeInt(deviceCount); b.writeComponent(title);
        b.writeLong(capacity.usedBytes); b.writeLong(capacity.totalBytes); b.writeLong(capacity.usedTypes); b.writeLong(capacity.totalTypes); b.writeInt(capacity.unknownCells);
        b.writeLong(capacity.occupiedSlots); b.writeLong(capacity.totalSlots); b.writeLong(capacity.fluidAmount); b.writeLong(capacity.fluidCapacity);
        b.writeVarInt(contents.size());
        for (Content c : contents) { AEKey.writeKey(b,c.key); b.writeLong(c.amount); }
        b.writeInt(contentPage); b.writeInt(contentPages); b.writeInt(contentCount); b.writeInt(cellSlots); b.writeInt(editableSlots);
    }
    public static Snapshot read(FriendlyByteBuf b) {
        boolean online=b.readBoolean(); String error=b.readUtf(128),selected=b.readUtf(256); int cell=b.readInt();
        int n=b.readVarInt(); if(n<0 || n>3) throw new IllegalArgumentException("device page");
        var devices=new ArrayList<DeviceInfo>();
        for(int i=0;i<n;i++) devices.add(new DeviceInfo(b.readUtf(256),b.readComponent(),b.readUtf(32),b.readResourceLocation(),b.readBlockPos(),b.readBoolean()));
        int dp=b.readInt(),dps=b.readInt(),dc=b.readInt(); Component title=b.readComponent();
        var capacity=new Capacity(b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readInt(),b.readLong(),b.readLong(),b.readLong(),b.readLong());
        n=b.readVarInt(); if(n<0 || n>6) throw new IllegalArgumentException("content page");
        var contents=new ArrayList<Content>();
        for(int i=0;i<n;i++) { AEKey key=AEKey.readKey(b); long amount=b.readLong(); if(key!=null) contents.add(new Content(key,amount)); }
        return new Snapshot(online,error,selected,cell,List.copyOf(devices),dp,dps,dc,title,capacity,List.copyOf(contents),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt());
    }
}
