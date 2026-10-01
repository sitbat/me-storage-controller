package dev.mestorage.controller.network;

import dev.mestorage.controller.folder.FolderBook;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Atomic folder documents and edits over bounded packets, including large multi-selections. */
public final class FolderTransfer {
    public static final int FRAME_BYTES=16*1024;
    public static final int MAX_EDIT_BYTES=4*1024*1024;
    public static final int MAX_VIEW_BYTES=32*1024*1024;
    private FolderTransfer() {}

    public record Frame(int totalBytes,int index,byte[] payload) {
        public void write(FriendlyByteBuf out) {
            out.writeVarInt(totalBytes);out.writeVarInt(index);out.writeByteArray(payload);
        }
        public static Frame read(FriendlyByteBuf in) {
            int total=in.readVarInt(),index=in.readVarInt();
            byte[] payload=in.readByteArray(FRAME_BYTES);
            if(total<=0||total>MAX_VIEW_BYTES||index<0||payload.length==0)
                throw new IllegalArgumentException("Invalid folder frame");
            return new Frame(total,index,payload);
        }
    }

    public static final class Cursor {
        private final byte[] data;
        private int position,index;
        public Cursor(byte[] data) { this.data=data; }
        public boolean finished(){return position==data.length;}
        public Frame next() {
            if(finished())throw new IllegalStateException("Folder transfer finished");
            int end=Math.min(data.length,position+FRAME_BYTES);
            var frame=new Frame(data.length,index++,Arrays.copyOfRange(data,position,end));position=end;
            return frame;
        }
    }

    public static final class Assembler {
        private final int limit;
        private ByteArrayOutputStream output;
        private int total,index;
        public Assembler(int limit){this.limit=limit;}
        public byte[] accept(Frame frame) {
            if(frame.totalBytes<=0||frame.totalBytes>limit||frame.index!=index
                    ||frame.payload.length==0||frame.payload.length>FRAME_BYTES)
                throw new IllegalArgumentException("Folder transfer sequence or size");
            if(output==null){total=frame.totalBytes;output=new ByteArrayOutputStream(Math.min(total,FRAME_BYTES));}
            if(total!=frame.totalBytes||output.size()+frame.payload.length>total)
                throw new IllegalArgumentException("Folder transfer length");
            output.writeBytes(frame.payload);index++;
            return output.size()==total?output.toByteArray():null;
        }
    }

    public static byte[] encodeView(FolderBook.View view) {
        var out=new FriendlyByteBuf(Unpooled.buffer());
        try {
            out.writeLong(view.revision());out.writeVarInt(view.folders().size());
            for(var folder:view.folders()) {
                out.writeUUID(folder.id());uuid(out,folder.parent());out.writeUtf(folder.name(),128);
                members(out,folder.members());
            }
            return bytes(out,MAX_VIEW_BYTES);
        } finally {out.release();}
    }
    public static FolderBook.View decodeView(byte[] data) {
        var in=new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            long revision=in.readLong();int count=count(in,18);
            var folders=new ArrayList<FolderBook.Folder>(count);
            for(int i=0;i<count;i++) folders.add(new FolderBook.Folder(in.readUUID(),uuid(in),in.readUtf(128),members(in)));
            if(in.isReadable())throw new IllegalArgumentException("Trailing folder document");
            return new FolderBook.View(revision,List.copyOf(folders));
        } finally {in.release();}
    }
    public static byte[] encodeEdit(FolderBook.Edit edit) {
        var out=new FriendlyByteBuf(Unpooled.buffer());
        try {
            out.writeEnum(edit.op());uuid(out,edit.id());uuid(out,edit.parent());out.writeUtf(edit.name(),128);
            members(out,edit.members());out.writeVarInt(edit.folders().size());
            for(var id:edit.folders())out.writeUUID(id);
            return bytes(out,MAX_EDIT_BYTES);
        } finally {out.release();}
    }
    public static FolderBook.Edit decodeEdit(byte[] data) {
        var in=new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            var op=in.readEnum(FolderBook.Op.class);var id=uuid(in);var parent=uuid(in);String name=in.readUtf(128);
            var members=members(in);int count=count(in,16);var folders=new ArrayList<UUID>(count);
            for(int i=0;i<count;i++)folders.add(in.readUUID());
            if(in.isReadable())throw new IllegalArgumentException("Trailing folder edit");
            return new FolderBook.Edit(op,id,parent,name,members,List.copyOf(folders));
        } finally {in.release();}
    }
    private static void members(FriendlyByteBuf out,List<FolderBook.MemberRef> members) {
        out.writeVarInt(members.size());
        for(var member:members){out.writeUtf(member.deviceId(),256);out.writeInt(member.cell());}
    }
    private static List<FolderBook.MemberRef> members(FriendlyByteBuf in) {
        int count=count(in,5);var members=new ArrayList<FolderBook.MemberRef>(count);
        for(int i=0;i<count;i++)members.add(new FolderBook.MemberRef(in.readUtf(256),in.readInt()));
        return List.copyOf(members);
    }
    private static int count(FriendlyByteBuf in,int minimumBytes) {
        int count=in.readVarInt();
        if(count<0||count>in.readableBytes()/minimumBytes)throw new IllegalArgumentException("Folder collection size");
        return count;
    }
    private static void uuid(FriendlyByteBuf out,UUID id){out.writeBoolean(id!=null);if(id!=null)out.writeUUID(id);}
    private static UUID uuid(FriendlyByteBuf in){return in.readBoolean()?in.readUUID():null;}
    private static byte[] bytes(FriendlyByteBuf out,int maximum) {
        if(out.readableBytes()>maximum)throw new IllegalArgumentException("Folder document too large");
        byte[] bytes=new byte[out.readableBytes()];out.readBytes(bytes);return bytes;
    }
}
