package dev.mestorage.controller.network;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record Snapshot(boolean online, String error, String selectedDevice, int selectedCell,
        List<DeviceInfo> devices, int devicePage, int devicePages, int deviceCount, Component title,
        Capacity capacity, List<Content> contents, int contentPage, int contentPages, int contentCount, int cellSlots, int editableSlots,
        List<CellPreview> cells, DeviceInfo selectedInfo, long revision,
        List<DirectoryEntry> directory, int directoryTotalDevices) {
    /** One native-style content grid: nine columns and five rows. */
    public static final int CONTENT_PAGE_SIZE=45;
    public record DirectoryEntry(DeviceInfo device,int cellSlots,List<CellPreview> cells) {
        public DirectoryEntry { cells=List.copyOf(cells); }
        public boolean truncated() { return cellSlots<0 || cellSlots>cells.size(); }
    }
    public boolean directoryTruncated() {
        return directoryTotalDevices>directory.size() || directory.stream().anyMatch(DirectoryEntry::truncated);
    }
    public Snapshot withDirectory(List<DirectoryEntry> entries,int total) {
        return new Snapshot(online,error,selectedDevice,selectedCell,devices,devicePage,devicePages,deviceCount,title,
                capacity,contents,contentPage,contentPages,contentCount,cellSlots,editableSlots,cells,selectedInfo,revision,entries,total);
    }
    public record DeviceInfo(String id, Component name, String kind, ResourceLocation dimension, BlockPos pos, boolean active,
                             Component sourceName, String face, String adapter, ItemStack icon) {}
    public record CellPreview(int slot, ItemStack icon, long usedBytes, long totalBytes, boolean readable) {}
    public record Capacity(long usedBytes, long totalBytes, long usedTypes, long totalTypes, int unknownCells,
                           long occupiedSlots, long totalSlots, long fluidAmount, long fluidCapacity) {
        /** Protocol 7: -2 is an unlimited maximum; -1 still means unknown. */
        public static final long UNLIMITED = -2;
        public Capacity(long usedBytes,long totalBytes,long usedTypes,long totalTypes,int unknownCells) {
            this(usedBytes,totalBytes,usedTypes,totalTypes,unknownCells,-1,-1,-1,-1);
        }
    }
    public record Content(AEKey key, long amount) {}
    public static Snapshot empty() {
        return new Snapshot(false,"", "",-1,List.of(),0,1,0,Component.translatable("gui.me_storage_controller.network"),
            new Capacity(-1,-1,-1,-1,0), List.of(),0,1,0,0,0,List.of(),null,0,List.of(),0);
    }
    public void write(FriendlyByteBuf b) {
        b.writeBoolean(online); b.writeUtf(error,128); b.writeUtf(selectedDevice,256); b.writeInt(selectedCell);
        b.writeVarInt(devices.size());
        for (DeviceInfo d : devices) writeDevice(b,d);
        b.writeInt(devicePage); b.writeInt(devicePages); b.writeInt(deviceCount); b.writeComponent(title);
        b.writeLong(capacity.usedBytes); b.writeLong(capacity.totalBytes); b.writeLong(capacity.usedTypes); b.writeLong(capacity.totalTypes); b.writeInt(capacity.unknownCells);
        b.writeLong(capacity.occupiedSlots); b.writeLong(capacity.totalSlots); b.writeLong(capacity.fluidAmount); b.writeLong(capacity.fluidCapacity);
        b.writeVarInt(contents.size());
        for (Content c : contents) { AEKey.writeKey(b,c.key); b.writeLong(c.amount); }
        b.writeInt(contentPage); b.writeInt(contentPages); b.writeInt(contentCount); b.writeInt(cellSlots); b.writeInt(editableSlots);
        b.writeVarInt(cells.size());
        for(var cell:cells) { b.writeInt(cell.slot); b.writeItem(cell.icon); b.writeLong(cell.usedBytes); b.writeLong(cell.totalBytes); b.writeBoolean(cell.readable); }
        b.writeBoolean(selectedInfo!=null); if(selectedInfo!=null) writeDevice(b,selectedInfo);
        b.writeLong(revision);
        b.writeVarInt(directoryTotalDevices); b.writeVarInt(directory.size());
        for(var entry:directory) {
            writeDevice(b,entry.device); b.writeInt(entry.cellSlots); b.writeVarInt(entry.cells.size());
            for(var child:entry.cells) writeCell(b,child);
        }
    }
    static void writeCell(FriendlyByteBuf b,CellPreview cell) {
        b.writeInt(cell.slot); b.writeItem(cell.icon); b.writeLong(cell.usedBytes); b.writeLong(cell.totalBytes); b.writeBoolean(cell.readable);
    }
    static CellPreview readCell(FriendlyByteBuf b) {
        return new CellPreview(b.readInt(),b.readItem(),b.readLong(),b.readLong(),b.readBoolean());
    }
    static void writeDevice(FriendlyByteBuf b,DeviceInfo d) {
        b.writeUtf(d.id,256); b.writeComponent(d.name); b.writeUtf(d.kind,32);
        b.writeResourceLocation(d.dimension); b.writeBlockPos(d.pos); b.writeBoolean(d.active);
        b.writeComponent(d.sourceName); b.writeUtf(d.face,16); b.writeUtf(d.adapter,32); b.writeItem(d.icon);
    }
    static DeviceInfo readDevice(FriendlyByteBuf b) {
        return new DeviceInfo(b.readUtf(256),b.readComponent(),b.readUtf(32),b.readResourceLocation(),b.readBlockPos(),b.readBoolean(),b.readComponent(),b.readUtf(16),b.readUtf(32),b.readItem());
    }
    public static Snapshot read(FriendlyByteBuf b) {
        boolean online=b.readBoolean(); String error=b.readUtf(128),selected=b.readUtf(256); int cell=b.readInt();
        int n=b.readVarInt(); if(n<0 || n>3) throw new IllegalArgumentException("device page");
        var devices=new ArrayList<DeviceInfo>();
        for(int i=0;i<n;i++) devices.add(readDevice(b));
        int dp=b.readInt(),dps=b.readInt(),dc=b.readInt(); Component title=b.readComponent();
        var capacity=new Capacity(b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readInt(),b.readLong(),b.readLong(),b.readLong(),b.readLong());
        n=b.readVarInt(); if(n<0 || n>CONTENT_PAGE_SIZE) throw new IllegalArgumentException("content page");
        var contents=new ArrayList<Content>();
        for(int i=0;i<n;i++) { AEKey key=AEKey.readKey(b); long amount=b.readLong(); if(key!=null) contents.add(new Content(key,amount)); }
        int cp=b.readInt(),cps=b.readInt(),cc=b.readInt(),slots=b.readInt(),editable=b.readInt();
        n=b.readVarInt(); if(n<0 || n>10) throw new IllegalArgumentException("cell page");
        var previews=new ArrayList<CellPreview>();
        for(int i=0;i<n;i++) previews.add(readCell(b));
        var selectedInfo=b.readBoolean() ? readDevice(b) : null;
        long revision=b.readLong(); int totalDevices=b.readVarInt();
        n=b.readVarInt(); if(totalDevices<0 || n<0 || n>b.readableBytes() || n>totalDevices) throw new IllegalArgumentException("directory size");
        var directory=new ArrayList<DirectoryEntry>(n);
        for(int i=0;i<n;i++) {
            var device=readDevice(b); int cellSlots=b.readInt(),count=b.readVarInt();
            if(cellSlots< -1 || count<0 || count>b.readableBytes()/22 || count>Math.max(0,cellSlots)) throw new IllegalArgumentException("directory children");
            var childList=new ArrayList<CellPreview>(count);
            for(int j=0;j<count;j++) {
                var child=readCell(b);
                if(child.slot()!=j) throw new IllegalArgumentException("directory cell index");
                childList.add(child);
            }
            directory.add(new DirectoryEntry(device,cellSlots,childList));
        }
        return new Snapshot(online,error,selected,cell,List.copyOf(devices),dp,dps,dc,title,capacity,List.copyOf(contents),cp,cps,cc,slots,editable,List.copyOf(previews),selectedInfo,revision,List.copyOf(directory),totalDevices);
    }
}
