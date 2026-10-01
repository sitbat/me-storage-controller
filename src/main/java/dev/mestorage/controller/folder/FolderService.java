package dev.mestorage.controller.folder;

import java.util.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.parts.AEBasePart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Each live grid owns an independent book. Moving AE nodes merge persisted records, never inventories. */
public final class FolderService implements IGridService,IGridServiceProvider {
    public static final String NODE_TAG="me_storage_controller:folder_book";
    private final IGrid grid;
    private final FolderSavedData data;
    private final UUID bookId=UUID.randomUUID();
    private final FolderBook book=new FolderBook();
    private final Map<UUID,Long> imported=new HashMap<>();
    private boolean markNodes;

    public FolderService(IGrid grid){
        this.grid=grid;this.data=FolderSavedData.get(grid.getPivot().getLevel().getServer());
        book.setRevision(data.nextRevision());
    }
    public UUID bookId(){return bookId;}
    public FolderBook.View view(){return book.view();}
    public List<FolderBook.MemberRef> members(UUID folder){return book.members(folder);}
    public FolderBook.Result apply(long expectedRevision,FolderBook.Edit edit){
        if(expectedRevision!=book.revision())return new FolderBook.Result("stale",view(),null);
        var result=book.apply(expectedRevision,edit,data.nextRevision());
        if(result.success()){persist();markNodes=true;markCurrentNodes();}
        return result;
    }
    private void persist(){if(!book.isEmpty())data.put(bookId,book);}
    @Override public void addNode(IGridNode node,@Nullable CompoundTag savedData){
        if(!book.isEmpty())markNodes=true;
        if(savedData==null||!savedData.hasUUID(NODE_TAG))return;
        UUID sourceId=savedData.getUUID(NODE_TAG);if(sourceId.equals(bookId))return;
        if(imported.getOrDefault(sourceId,-1L)>=data.revision(sourceId))return;
        var source=data.read(sourceId);if(source==null)return;
        imported.put(sourceId,source.revision());
        long previousRevision=book.revision();
        if(book.merge(source)){book.setRevision(data.nextRevision());persist();markNodes=true;}
        else book.setRevision(previousRevision);
    }
    @Override public void saveNodeData(IGridNode node,CompoundTag tag){
        if(book.isEmpty())return;
        // Called both for chunk saves and before GridNode.setGrid moves a node during merge/split.
        tag.putUUID(NODE_TAG,bookId);
    }
    @Override public void onServerEndTick(){if(markNodes)markCurrentNodes();}
    private void markCurrentNodes(){
        // Never traverse a grid inside addNode/removeNode while AE is restructuring it.
        markNodes=false;
        for(var node:grid.getNodes()){
            var owner=node.getOwner();
            if(owner instanceof BlockEntity entity)entity.setChanged();
            else if(owner instanceof AEBasePart part)part.getBlockEntity().setChanged();
        }
    }
}
