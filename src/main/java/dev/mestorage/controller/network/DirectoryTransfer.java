package dev.mestorage.controller.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bounded wire frames; directory size and individual drive slot count have no display cap. */
public final class DirectoryTransfer {
    public static final int MAX_FRAME_BYTES=48*1024;
    private static final int PAYLOAD_BUDGET=MAX_FRAME_BYTES-64;
    private static final int MAX_PARTS=32, MAX_CELLS=128;
    private DirectoryTransfer() {}

    public record Frame(long generation,int index,boolean last,int totalDevices,byte[] payload) {
        public void write(FriendlyByteBuf out) {
            out.writeLong(generation); out.writeVarInt(index); out.writeBoolean(last);
            out.writeVarInt(totalDevices); out.writeByteArray(payload);
        }
        public static Frame read(FriendlyByteBuf in) {
            long generation=in.readLong(); int index=in.readVarInt(); boolean last=in.readBoolean();
            int total=in.readVarInt(); byte[] payload=in.readByteArray(PAYLOAD_BUDGET);
            if(generation<=0 || index<0 || total<0) throw new IllegalArgumentException("directory frame header");
            return new Frame(generation,index,last,total,payload);
        }
    }

    /** Holds one immutable generation until its last frame; later refreshes cannot starve its tail. */
    public static final class Cursor {
        private final long generation;
        private final List<Snapshot.DirectoryEntry> entries;
        private int device,cell,index;
        private boolean finished;
        public Cursor(long generation,List<Snapshot.DirectoryEntry> entries) { this.generation=generation; this.entries=entries; }
        public boolean finished() { return finished; }
        public Frame next() {
            if(finished) throw new IllegalStateException("Directory generation already sent");
            var payload=new FriendlyByteBuf(Unpooled.buffer());
            try {
                int parts=0,children=0;
                while(device<entries.size() && parts<MAX_PARTS && children<MAX_CELLS) {
                    var entry=entries.get(device);
                    int count=Math.min(entry.cells().size()-cell,MAX_CELLS-children);
                    byte[] part=encodePart(entry,cell,count);
                    while(part.length>PAYLOAD_BUDGET && count>1) { count=Math.max(1,count/2); part=encodePart(entry,cell,count); }
                    if(part.length>PAYLOAD_BUDGET) throw new IllegalStateException("Sanitized directory entry exceeds frame budget");
                    if(payload.readableBytes()+part.length>PAYLOAD_BUDGET) break;
                    payload.writeBytes(part); parts++; children+=count; cell+=count;
                    if(cell==entry.cells().size()) { device++; cell=0; }
                }
                finished=device==entries.size();
                byte[] bytes=new byte[payload.readableBytes()]; payload.readBytes(bytes);
                return new Frame(generation,index++,finished,entries.size(),bytes);
            } finally { payload.release(); }
        }
    }

    private static byte[] encodePart(Snapshot.DirectoryEntry entry,int first,int count) {
        var out=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var d=entry.device();
            Snapshot.writeDevice(out,new Snapshot.DeviceInfo(d.id(),display(d.name()),d.kind(),d.dimension(),d.pos(),d.active(),
                    display(d.sourceName()),d.face(),d.adapter(),display(d.icon())));
            out.writeInt(entry.cellSlots()); out.writeVarInt(first); out.writeVarInt(count);
            for(int i=first;i<first+count;i++) {
                var c=entry.cells().get(i);
                Snapshot.writeCell(out,new Snapshot.CellPreview(c.slot(),display(c.icon()),c.usedBytes(),c.totalBytes(),c.readable()));
            }
            byte[] bytes=new byte[out.readableBytes()]; out.readBytes(bytes); return bytes;
        } finally { out.release(); }
    }

    // Resource identities/counts are untouched. Only untrusted display text is bounded.
    private static Component display(Component value) {
        String text=value.getString();
        // Keep translation keys/styles for normal names; flatten only excessive text/NBT.
        if(text.length()<=256 && Component.Serializer.toJson(value).length()<=1024) return value;
        return Component.literal(text.length()<=256?text:text.substring(0,255)+"…");
    }
    private static ItemStack display(ItemStack value) {
        if(value.isEmpty()) return ItemStack.EMPTY;
        var result=new ItemStack(value.getItem());
        if(value.hasCustomHoverName()) result.setHoverName(display(value.getHoverName()));
        return result;
    }

    /** Commit complete generations atomically: refreshes never flicker, duplicate rows or reset scroll. */
    public static final class Assembler {
        private long generation;
        private int nextIndex,total;
        private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
        private static final class Entry {
            final Snapshot.DeviceInfo device; final int slots;
            final List<Snapshot.CellPreview> cells=new ArrayList<>();
            Entry(Snapshot.DeviceInfo device,int slots) { this.device=device; this.slots=slots; }
        }
        public List<Snapshot.DirectoryEntry> accept(Frame frame) {
            if(frame.generation()<generation) return null;
            if(frame.generation()>generation) {
                if(frame.index()!=0) throw new IllegalArgumentException("Directory generation missing first frame");
                generation=frame.generation(); nextIndex=0; total=frame.totalDevices(); entries.clear();
            }
            if(frame.index()!=nextIndex++ || total!=frame.totalDevices() || frame.payload().length>PAYLOAD_BUDGET)
                throw new IllegalArgumentException("Directory frame sequence");
            var in=new FriendlyByteBuf(Unpooled.wrappedBuffer(frame.payload()));
            try {
                int parts=0,children=0;
                while(in.isReadable()) {
                    if(++parts>MAX_PARTS) throw new IllegalArgumentException("Directory frame parts");
                    var d=Snapshot.readDevice(in); int slots=in.readInt(),first=in.readVarInt(),count=in.readVarInt();
                    if(slots< -1 || first<0 || count<0 || count>MAX_CELLS || (children+=count)>MAX_CELLS
                            || (long)first+count>Math.max(0,slots)) throw new IllegalArgumentException("Directory child range");
                    var entry=entries.get(d.id());
                    if(entry==null) {
                        if(first!=0 || entries.size()>=total) throw new IllegalArgumentException("Directory device sequence");
                        entry=new Entry(d,slots); entries.put(d.id(),entry);
                    }
                    if(entry.slots!=slots || entry.cells.size()!=first) throw new IllegalArgumentException("Directory cell sequence");
                    for(int i=0;i<count;i++) {
                        var child=Snapshot.readCell(in);
                        if(child.slot()!=first+i) throw new IllegalArgumentException("Directory cell index");
                        entry.cells.add(child);
                    }
                }
            } finally { in.release(); }
            if(!frame.last()) return null;
            if(entries.size()!=total) throw new IllegalArgumentException("Incomplete directory generation");
            var result=new ArrayList<Snapshot.DirectoryEntry>(total);
            for(var entry:entries.values()) {
                if(entry.cells.size()!=Math.max(0,entry.slots)) throw new IllegalArgumentException("Incomplete directory device");
                result.add(new Snapshot.DirectoryEntry(entry.device,entry.slots,entry.cells));
            }
            entries.clear();
            return List.copyOf(result);
        }
    }
}
