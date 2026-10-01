package dev.mestorage.controller.folder;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Immutable record snapshots are shared; each historical network UUID keeps its own revision and head. */
public final class FolderSavedData extends SavedData {
    private record Head(UUID snapshot,long revision) {}
    // Stored books are private copies and are never mutated or returned to a caller.
    private final Map<UUID,FolderBook> versions=new HashMap<>();
    private final Map<UUID,Head> heads=new HashMap<>();
    private final Map<Integer,Set<UUID>> byHash=new HashMap<>();
    private long clock;
    public FolderSavedData() {}
    public static FolderSavedData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FolderSavedData::load,FolderSavedData::new,"me_storage_controller_folders");
    }
    public long nextRevision(){
        if(clock==Long.MAX_VALUE)throw new IllegalStateException("Folder revision exhausted");
        setDirty();return ++clock;
    }
    public FolderBook read(UUID id){
        var head=heads.get(id);if(head==null)return null;
        var stored=versions.get(head.snapshot);if(stored==null)return null;
        var copy=stored.copy();copy.setRevision(head.revision);return copy;
    }
    public long revision(UUID id){var head=heads.get(id);return head==null?-1:head.revision;}
    public void put(UUID id,FolderBook book){
        var previous=heads.get(id);
        if(previous!=null && previous.revision==book.revision())return;
        UUID snapshot=intern(book);
        heads.put(id,new Head(snapshot,book.revision()));
        clock=Math.max(clock,book.revision());setDirty();
    }
    private UUID intern(FolderBook book){return intern(book,null);}
    private UUID intern(FolderBook book,UUID persistedId){
        int hash=book.recordHash();
        var matches=byHash.computeIfAbsent(hash,ignored->new HashSet<>());
        for(var candidate:matches)if(versions.get(candidate).sameRecords(book))return candidate;
        UUID id=persistedId==null?UUID.randomUUID():persistedId;var frozen=book.copy();frozen.setRevision(0);
        versions.put(id,frozen);matches.add(id);return id;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putInt("schema",2);tag.putLong("clock",clock);
        var referenced=new HashSet<UUID>();for(var head:heads.values())referenced.add(head.snapshot);
        // A historical head may still be referenced by an unloaded node. Keep all heads;
        // only snapshots with no head at all can be discarded safely.
        versions.keySet().removeIf(id->!referenced.contains(id));
        byHash.clear();versions.forEach((id,book)->byHash.computeIfAbsent(book.recordHash(),ignored->new HashSet<>()).add(id));
        var snapshots=new ListTag();var networkHeads=new ListTag();
        versions.forEach((id,book)->{var entry=book.save();entry.putUUID("snapshotId",id);snapshots.add(entry);});
        heads.forEach((id,head)->{var entry=new CompoundTag();entry.putUUID("bookId",id);entry.putUUID("snapshotId",head.snapshot);entry.putLong("revision",head.revision);networkHeads.add(entry);});
        tag.put("versions",snapshots);tag.put("heads",networkHeads);tag.remove("books");return tag;
    }
    public static FolderSavedData load(CompoundTag tag){
        var data=new FolderSavedData();data.clock=Math.max(0,tag.getLong("clock"));
        if(tag.contains("heads",Tag.TAG_LIST)){
            var canonical=new HashMap<UUID,UUID>();
            var contentRevisions=new HashMap<UUID,Long>();
            for(var value:tag.getList("versions",Tag.TAG_COMPOUND)){
                var entry=(CompoundTag)value;if(!entry.hasUUID("snapshotId"))continue;
                UUID oldId=entry.getUUID("snapshotId");var book=FolderBook.load(entry);
                canonical.put(oldId,data.intern(book,oldId));contentRevisions.put(oldId,book.revision());
            }
            for(var value:tag.getList("heads",Tag.TAG_COMPOUND)){
                var entry=(CompoundTag)value;if(!entry.hasUUID("bookId")||!entry.hasUUID("snapshotId"))continue;
                UUID oldSnapshot=entry.getUUID("snapshotId"),snapshot=canonical.get(oldSnapshot);if(snapshot==null)continue;
                long revision=Math.max(Math.max(0,entry.getLong("revision")),contentRevisions.get(oldSnapshot));
                data.heads.put(entry.getUUID("bookId"),new Head(snapshot,revision));data.clock=Math.max(data.clock,revision);
            }
        }else{
            // Compatibility with the initial unsplit-book format and saved development fixtures.
            for(var value:tag.getList("books",Tag.TAG_COMPOUND)){
                var entry=(CompoundTag)value;if(!entry.hasUUID("bookId"))continue;
                var book=FolderBook.load(entry);data.heads.put(entry.getUUID("bookId"),new Head(data.intern(book),book.revision()));
                data.clock=Math.max(data.clock,book.revision());
            }
        }
        return data;
    }
}
