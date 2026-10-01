package dev.mestorage.controller.folder;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** Logical organization only. Every member denotes a device address or a physical slot. */
public final class FolderBook {
    public static final int MAX_FOLDERS=256, MAX_MEMBERS=8192, MAX_DEPTH=32, MAX_NAME=64;
    public record MemberRef(String deviceId,int cell) {
        public MemberRef { Objects.requireNonNull(deviceId); }
        public boolean valid() { return !deviceId.isBlank() && deviceId.length()<=256 && !deviceId.startsWith("virtual:") && cell>=-1 && cell<=65535; }
    }
    public record Folder(UUID id,@Nullable UUID parent,String name,List<MemberRef> members) {
        public Folder { members=List.copyOf(members); }
    }
    public record View(long revision,List<Folder> folders) {
        public View { folders=List.copyOf(folders); }
    }
    public enum Op { CREATE, RENAME, MOVE, DELETE, ASSIGN, UNASSIGN }
    public record Edit(Op op,@Nullable UUID id,@Nullable UUID parent,String name,List<MemberRef> members,List<UUID> folders) {
        public Edit { Objects.requireNonNull(op); name=name==null?"":name; members=List.copyOf(members); folders=List.copyOf(folders); }
    }
    public record Result(String error,View view,@Nullable UUID createdId) { public boolean success(){return error.isEmpty();} }
    private record Entry(long stamp,String name,@Nullable UUID parent,boolean deleted) {}
    private record Link(long stamp,@Nullable UUID folder) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    private final Map<MemberRef,Link> links=new HashMap<>();
    private long revision;
    private View cachedView;

    public long revision(){return revision;}
    public boolean contains(UUID id){return id!=null && entries.containsKey(id) && !entries.get(id).deleted;}
    public boolean isEmpty(){return entries.isEmpty() && links.isEmpty();}
    public FolderBook copy(){var b=new FolderBook();b.entries.putAll(entries);b.links.putAll(links);b.revision=revision;return b;}
    int recordHash(){return Objects.hash(entries,links);}
    boolean sameRecords(FolderBook other){return entries.equals(other.entries)&&links.equals(other.links);}

    private @Nullable UUID liveFolder(@Nullable UUID id){
        var seen=new HashSet<UUID>();
        while(id!=null && seen.add(id)){
            var entry=entries.get(id);if(entry==null)return null;
            if(!entry.deleted)return id;
            id=entry.parent;
        }
        return null;
    }

    /** A deterministic parent projection also breaks cycles created by independent split-network edits. */
    private @Nullable UUID parent(UUID id) {
        UUID direct=rawParent(id);
        if(!contains(direct)) return null;
        var path=new HashSet<UUID>(); UUID cursor=id;
        for(int depth=0;cursor!=null && depth<=MAX_DEPTH;depth++) {
            if(!path.add(cursor)) {
                // Cutting the greatest UUID in the cycle preserves every folder.
                UUID greatest=cursor,next=rawParent(cursor);
                while(next!=null && !next.equals(cursor) && contains(next)) {
                    if(next.compareTo(greatest)>0) greatest=next;
                    next=rawParent(next);
                }
                return id.equals(greatest)?null:direct;
            }
            cursor=rawParent(cursor);
        }
        return path.size()>MAX_DEPTH?null:direct;
    }
    private @Nullable UUID rawParent(UUID id){var entry=entries.get(id);return entry==null?null:liveFolder(entry.parent);}
    public View view(){
        if(cachedView!=null)return cachedView;
        var result=new ArrayList<Folder>();
        entries.forEach((id,e)->{if(!e.deleted){
            var members=new ArrayList<MemberRef>();
            links.forEach((ref,link)->{if(id.equals(liveFolder(link.folder)))members.add(ref);});
            members.sort(MEMBER_ORDER);result.add(new Folder(id,parent(id),e.name,members));
        }});
        result.sort(Comparator.comparing(Folder::id));
        return cachedView=new View(revision,result);
    }
    private static final Comparator<MemberRef> MEMBER_ORDER=Comparator.comparing(MemberRef::deviceId).thenComparingInt(MemberRef::cell);
    public static List<MemberRef> normalize(Collection<MemberRef> members){
        var all=new TreeSet<>(MEMBER_ORDER);all.addAll(members);
        var devices=new HashSet<String>();for(var ref:all)if(ref.cell==-1)devices.add(ref.deviceId);
        all.removeIf(ref->ref.cell>=0 && devices.contains(ref.deviceId));return List.copyOf(all);
    }
    public List<MemberRef> members(UUID folder){
        if(!contains(folder))return List.of();
        var selected=new HashSet<UUID>();selected.add(folder);
        boolean changed;do{changed=false;for(var f:view().folders)if(f.parent!=null && selected.contains(f.parent))changed|=selected.add(f.id);}while(changed);
        var result=new ArrayList<MemberRef>();links.forEach((ref,link)->{if(selected.contains(liveFolder(link.folder)))result.add(ref);});
        return normalize(result);
    }
    public Result apply(long expectedRevision,Edit edit,long stamp){
        if(expectedRevision!=revision)return new Result("stale",view(),null);
        if(stamp<=revision)return new Result("stale",view(),null);
        var next=copy();next.materializeParents(); UUID created=null;
        String error;
        try {created=next.change(edit,stamp);error="";}catch(Invalid invalid){error=invalid.getMessage();}
        if(!error.isEmpty())return new Result(error,view(),null);
        entries.clear();entries.putAll(next.entries);links.clear();links.putAll(next.links);revision=stamp;cachedView=null;
        return new Result("",view(),created);
    }
    private UUID change(Edit edit,long stamp){
        UUID id=edit.id,parent=edit.parent;
        if(edit.members.size()>MAX_MEMBERS || edit.folders.size()>MAX_FOLDERS)throw invalid("limit");
        if(parent!=null && !contains(parent))throw invalid("missing");
        switch(edit.op){
            case CREATE -> {if(view().folders.size()>=MAX_FOLDERS)throw invalid("limit");id=UUID.randomUUID();entries.put(id,new Entry(stamp,name(edit.name),parent,false));
                for(var folder:edit.folders){require(folder);move(folder,id,stamp);}
                for(var ref:edit.members){if(!ref.valid())throw invalid("member");links.put(ref,new Link(stamp,id));}
                if(links.values().stream().filter(link->link.folder!=null).count()>MAX_MEMBERS)throw invalid("limit");
            }
            case RENAME -> {require(id);var e=entries.get(id);entries.put(id,new Entry(stamp,name(edit.name),e.parent,false));}
            case MOVE -> {require(id);move(id,parent,stamp);}
            case DELETE -> {
                require(id);UUID destination=parent(id);var e=entries.get(id);
                for(var f:view().folders)if(id.equals(f.parent))move(f.id,destination,stamp);
                for(var ref:new ArrayList<>(links.keySet()))if(id.equals(links.get(ref).folder))links.put(ref,new Link(stamp,destination));
                entries.put(id,new Entry(stamp,e.name,destination,true));
            }
            case ASSIGN,UNASSIGN -> {
                UUID destination=edit.op==Op.UNASSIGN?null:id;if(destination!=null)require(destination);
                for(var folder:edit.folders){require(folder);move(folder,destination,stamp);}
                for(var ref:edit.members){if(!ref.valid())throw invalid("member");links.put(ref,new Link(stamp,destination));}
                if(links.values().stream().filter(link->link.folder!=null).count()>MAX_MEMBERS)throw invalid("limit");
            }
        }
        cachedView=null;
        // Include descendants in the depth check after a whole subtree move.
        for(var f:view().folders){var seen=new HashSet<UUID>();UUID cursor=f.id;while(cursor!=null){if(!seen.add(cursor)||seen.size()>MAX_DEPTH)throw invalid("cycle");var e=entries.get(cursor);cursor=e==null?null:e.parent;}}
        return edit.op==Op.CREATE?id:null;
    }
    private void materializeParents(){
        var projected=new HashMap<UUID,UUID>();
        entries.forEach((id,e)->{if(!e.deleted)projected.put(id,parent(id));});
        projected.forEach((id,parent)->{var e=entries.get(id);entries.put(id,new Entry(e.stamp,e.name,parent,false));});
        cachedView=null;
    }
    private void move(UUID id,@Nullable UUID parent,long stamp){
        UUID cursor=parent;var seen=new HashSet<UUID>();
        while(cursor!=null){if(cursor.equals(id)||!seen.add(cursor))throw invalid("cycle");cursor=parent(cursor);}
        var e=entries.get(id);entries.put(id,new Entry(stamp,e.name,parent,false));
    }
    private void require(UUID id){if(!contains(id))throw invalid("missing");}
    private static String name(String text){String name=text.strip();if(name.isEmpty()||name.length()>MAX_NAME||name.codePoints().anyMatch(Character::isISOControl))throw invalid("name");return name;}
    private static Invalid invalid(String code){return new Invalid(code);}
    private static final class Invalid extends RuntimeException{Invalid(String message){super(message);}}

    /** Last server-assigned stamp wins per folder/member; tombstones prevent stale nodes resurrecting deletions. */
    public boolean merge(FolderBook source){
        boolean changed=false;
        for(var item:source.entries.entrySet()){
            var old=entries.get(item.getKey());var incoming=item.getValue();
            // A rename on a disconnected branch cannot resurrect an explicitly deleted UUID.
            if(old!=null && old.deleted && !incoming.deleted)continue;
            if(old==null||(!old.deleted && incoming.deleted)||old.stamp<incoming.stamp){entries.put(item.getKey(),incoming);changed=true;}
        }
        for(var item:source.links.entrySet()){var old=links.get(item.getKey());if(old==null||old.stamp<item.getValue().stamp){links.put(item.getKey(),item.getValue());changed=true;}}
        revision=Math.max(revision,source.revision);cachedView=null;return changed;
    }
    void setRevision(long value){revision=value;cachedView=null;}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putLong("revision",revision);var folders=new ListTag();var members=new ListTag();
        entries.forEach((id,e)->{var t=new CompoundTag();t.putUUID("id",id);t.putLong("stamp",e.stamp);t.putString("name",e.name);if(e.parent!=null)t.putUUID("parent",e.parent);t.putBoolean("deleted",e.deleted);folders.add(t);});
        links.forEach((ref,link)->{var t=new CompoundTag();t.putString("device",ref.deviceId);t.putInt("cell",ref.cell);t.putLong("stamp",link.stamp);if(link.folder!=null)t.putUUID("folder",link.folder);members.add(t);});
        tag.put("folders",folders);tag.put("members",members);return tag;
    }
    public static FolderBook load(CompoundTag tag){
        var book=new FolderBook();book.revision=Math.max(0,tag.getLong("revision"));
        for(var value:tag.getList("folders",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;if(!t.hasUUID("id"))continue;var e=new Entry(Math.max(0,t.getLong("stamp")),t.getString("name"),t.hasUUID("parent")?t.getUUID("parent"):null,t.getBoolean("deleted"));book.entries.put(t.getUUID("id"),e);book.revision=Math.max(book.revision,e.stamp);}
        for(var value:tag.getList("members",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;var ref=new MemberRef(t.getString("device"),t.getInt("cell"));if(!ref.valid())continue;var link=new Link(Math.max(0,t.getLong("stamp")),t.hasUUID("folder")?t.getUUID("folder"):null);book.links.put(ref,link);book.revision=Math.max(book.revision,link.stamp);}
        return book;
    }
}
