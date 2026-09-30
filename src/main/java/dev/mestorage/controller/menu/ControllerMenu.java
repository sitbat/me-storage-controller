package dev.mestorage.controller.menu;

import appeng.api.networking.IGrid;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.stacks.KeyCounter;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.storage.StorageScanner;
import dev.mestorage.controller.storage.StorageScanner.Device;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** The server owns selection, device inventory and every transfer. No client supplies ItemStacks. */
public final class ControllerMenu extends AbstractContainerMenu {
    private final Player player;
    private final Inventory playerInventory;
    private final ControllerBlockEntity controller;
    private final RemoteCells remote = new RemoteCells();
    private final ItemStackHandler clientCells = new ItemStackHandler(10);
    private Snapshot snapshot = Snapshot.empty();
    private Device selected;
    private String selectedId="",deviceQuery="",contentQuery="";
    private int selectedCell=-1,devicePage=0,contentPage=0;
    private boolean sortByAmount=true;
    private long nextRefresh=0,lastRequest=-100;
    private Network.Request pending;
    private Set<ResourceLocation> deviceMatches=Set.of(),contentMatches=Set.of();
    private final Map<String,List<ResourceLocation>> localizedSearchCache=new HashMap<>();
    private boolean selectionPending;
    private long requestedRevision;
    private long acknowledgedRevision;
    private boolean reportedStorageError;

    public ControllerMenu(int id,Inventory inventory,FriendlyByteBuf data) {
        this(id,inventory,inventory.player.level().getBlockEntity(data.readBlockPos()) instanceof ControllerBlockEntity be ? be : null);
    }
    public ControllerMenu(int id,Inventory inventory,ControllerBlockEntity controller) {
        super(MEStorageController.MENU.get(),id); this.player=inventory.player; this.playerInventory=inventory; this.controller=controller;
        for(int i=0;i<10;i++) addSlot(remoteSlot(i,117));
        for(int y=0;y<3;y++) for(int x=0;x<9;x++) addSlot(new Slot(inventory,9+y*9+x,8+18*x,151+18*y));
        for(int x=0;x<9;x++) addSlot(new Slot(inventory,x,8+18*x,209));
    }
    private Slot remoteSlot(int index,int y) {
        return new SlotItemHandler(remote,index,8+18*index,y) {
            @Override public boolean mayPlace(ItemStack stack) { return canEdit() && remote.isItemValid(index,stack); }
            @Override public boolean mayPickup(Player p) { return canEdit(); }
            @Override public boolean isActive() { return player.level().isClientSide ? snapshot.editableSlots()>index : selected!=null && selected.cells()!=null && selected.cells().getSlots()>index+cellOffset(); }
        };
    }
    /** Only presentation coordinates change. Slot identity, ordering and backing inventories remain unchanged. */
    public void layoutSlots(int cellY,int inventoryY,int hotbarY) {
        if(!player.level().isClientSide) return;
        for(int i=0;i<10;i++) replaceSlot(i,remoteSlot(i,cellY));
        for(int row=0;row<3;row++) for(int col=0;col<9;col++)
            replaceSlot(10+row*9+col,new Slot(playerInventory,9+row*9+col,8+18*col,inventoryY+18*row));
        for(int col=0;col<9;col++) replaceSlot(37+col,new Slot(playerInventory,col,8+18*col,hotbarY));
    }
    private void replaceSlot(int index,Slot slot) { slot.index=index; slots.set(index,slot); }
    public Snapshot getSnapshot() { return snapshot; }
    public void setSnapshot(Snapshot value) {
        if(value.revision()<snapshot.revision()) return;
        snapshot=value;
        if(value.revision()>=requestedRevision) selectionPending=false;
    }
    public void request(String deviceId,int cell,int dp,int cp,String dq,String cq,boolean amount) {
        if(!deviceId.equals(snapshot.selectedDevice()) || cell!=snapshot.selectedCell()) {
            selectionPending=true;
        }
        Network.request(new Network.Request(containerId,deviceId,cell,dp,cp,dq,cq,amount,localizedMatches(dq),localizedMatches(cq),++requestedRevision));
    }
    /** Resolve translated registry names on the client; the server only uses these IDs as read-only filters. */
    private List<ResourceLocation> localizedMatches(String query) {
        if(query.isBlank()) return List.of();
        if(localizedSearchCache.size()>32) localizedSearchCache.clear();
        return localizedSearchCache.computeIfAbsent(query, q -> {
            var ids=new LinkedHashSet<ResourceLocation>();
            for(var item:BuiltInRegistries.ITEM) if(matches(item.getDescription().getString(),q) && ids.size()<512) ids.add(BuiltInRegistries.ITEM.getKey(item));
            for(var fluid:BuiltInRegistries.FLUID) if(matches(fluid.getFluidType().getDescription().getString(),q) && ids.size()<512) ids.add(BuiltInRegistries.FLUID.getKey(fluid));
            return List.copyOf(ids);
        });
    }
    public void handleRequest(Network.Request request) {
        if(!stillValid(player)) return;
        if(request.revision()<=acknowledgedRevision || pending!=null && request.revision()<=pending.revision()) return;
        // Coalesce rapid typing/navigation; never do an unbounded scan per incoming packet.
        pending=request;
    }
    @Override public boolean stillValid(Player p) { return controller!=null && controller.canUse(p); }
    private IGrid grid() { return controller==null || !controller.getMainNode().isActive() ? null : controller.getMainNode().getGrid(); }
    private boolean current() {
        var grid=grid();
        return grid!=null && selected!=null && StorageScanner.isCurrent(selected,grid) && selected.node().isActive();
    }
    private boolean canEdit() {
        if(player.level().isClientSide) return !selectionPending && snapshot.online() && snapshot.editableSlots()>0;
        if(!stillValid(player) || !player.mayBuild() || !current() || selected.location()==null || selected.cells()==null) return false;
        // Protection hooks operate in the player's dimension. Cross-dimensional inventory mutation is disabled.
        return player.level().dimension().equals(selected.location().dimension())
            && player.level().mayInteract(player,selected.location().pos());
    }
    private boolean protectionAllows() {
        if(!canEdit()) return false;
        BlockPos pos=selected.location().pos();
        var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,
            new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        return !MinecraftForge.EVENT_BUS.post(event) && event.getUseBlock()!=Event.Result.DENY;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p) {
        boolean usesRemote=slot>=0 && slot<10 || type==ClickType.QUICK_MOVE && slot>=10;
        if(p.level().isClientSide) { if(!canSendClick(slot,type)) return; super.clicked(slot,button,type,p); return; }
        if(!stillValid(p)) return;
        if(type==ClickType.QUICK_CRAFT || type==ClickType.PICKUP_ALL) { broadcastFullState(); return; }
        if(usesRemote && (pending!=null && (!pending.deviceId().equals(selectedId) || pending.cell()!=selectedCell) || !protectionAllows())) { broadcastFullState(); return; }
        super.clicked(slot,button,type,p); nextRefresh=0;
    }
    public boolean canSendClick(int slot,ClickType type) {
        if(type==ClickType.QUICK_CRAFT || type==ClickType.PICKUP_ALL) return false;
        return !selectionPending || !(slot>=0 && slot<10 || type==ClickType.QUICK_MOVE && slot>=10);
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index<0 || index>=slots.size() || !canEdit()) return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),copy=source.copy();
        if(index<10) {
            if(!moveItemStackTo(source,10,46,true)) return ItemStack.EMPTY;
        } else if(!moveItemStackTo(source,0,10,false)) return ItemStack.EMPTY;
        slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        slot.onTake(p,source); nextRefresh=0;
        return copy;
    }
    @Override public void broadcastChanges() {
        if(!player.level().isClientSide) {
            long now=player.level().getGameTime();
            if(pending!=null && now-lastRequest>=5) {
                var request=pending; pending=null; lastRequest=now;
                acknowledgedRevision=request.revision();
                selectedId=request.deviceId(); selectedCell=Math.max(-1,request.cell());
                devicePage=Math.max(0,request.devicePage()); contentPage=Math.max(0,request.contentPage());
                deviceQuery=request.deviceQuery(); contentQuery=request.contentQuery(); sortByAmount=request.sortByAmount(); nextRefresh=0;
                deviceMatches=Set.copyOf(request.deviceMatches()); contentMatches=Set.copyOf(request.contentMatches());
            }
            if(now>=nextRefresh) { refresh(); nextRefresh=now+20; }
        }
        super.broadcastChanges();
    }
    private void refresh() {
        var grid=grid();
        if(grid==null || !stillValid(player)) {
            selected=null; snapshot=new Snapshot(false,"gui.me_storage_controller.offline","",-1,List.of(),0,1,0,
                controller==null ? Component.empty() : controller.getDisplayName(),new Snapshot.Capacity(-1,-1,-1,-1,0),List.of(),0,1,0,0,0,List.of(),null,acknowledgedRevision);
            send(); return;
        }
        List<Device> devices=StorageScanner.discover(grid);
        selected=devices.stream().filter(d->d.id().equals(selectedId)).findFirst().orElse(null);
        if(selected==null) { selectedId=""; selectedCell=-1; }
        Map<String,Snapshot.DeviceInfo> deviceInfos=new HashMap<>();
        for(var device:devices) deviceInfos.put(device.id(),deviceInfo(device));
        var filtered=devices.stream().filter(d -> {
            var info=deviceInfos.get(d.id());
            return matches(info.name().getString()+" "+info.sourceName().getString()+" "+d.id()+" "+BuiltInRegistries.ITEM.getKey(info.icon().getItem())
                +" "+info.dimension()+" "+info.pos().getX()+", "+info.pos().getY()+", "+info.pos().getZ()+" "+info.face()+" "+info.adapter(),deviceQuery)
                || deviceMatches.contains(BuiltInRegistries.ITEM.getKey(info.icon().getItem()))
                || d.node().getVisualRepresentation()!=null && deviceMatches.contains(d.node().getVisualRepresentation().getId());
        }).toList();
        int devicePages=pages(filtered.size(),3); devicePage=Math.min(devicePage,devicePages-1);
        var infos=filtered.stream().skip((long)devicePage*3).limit(3).map(d->deviceInfos.get(d.id())).toList();
        var selectedInfo=selected==null ? null : deviceInfos.get(selected.id());
        List<StorageScanner.CellInfo> selectedCells=List.of();
        KeyCounter keys;
        Snapshot.Capacity capacity;
        Component title;
        String error="";
        if(selected==null) {
            try { keys=StorageScanner.gridContents(grid); }
            catch(RuntimeException failure) {
                keys=new KeyCounter(); error="gui.me_storage_controller.unreadable";
                if(!reportedStorageError) {
                    org.slf4j.LoggerFactory.getLogger(ControllerMenu.class).warn("Unable to read AE network storage cache; showing unavailable contents",failure);
                    reportedStorageError=true;
                }
            }
            title=Component.translatable("gui.me_storage_controller.network");
            long used=0,total=0,types=0,typeTotal=0; int unknown=0;
            for(var device:devices) {
                if(!device.active()) continue;
                var cap=StorageScanner.capacity(device); unknown+=cap.unknownCells();
                if(cap.known()) { used=StorageScanner.saturatedAdd(used,cap.usedBytes()); total=StorageScanner.saturatedAdd(total,cap.totalBytes());
                    types=StorageScanner.saturatedAdd(types,cap.usedTypes()); typeTotal=StorageScanner.saturatedAdd(typeTotal,cap.totalTypes()); }
            }
            capacity=new Snapshot.Capacity(used,total,types,typeTotal,unknown);
        } else {
            title=selectedInfo.name(); var cap=StorageScanner.capacity(selected);
            keys=selected.active() ? StorageScanner.contents(selected.storage()) : new KeyCounter();
            var cells=StorageScanner.readCells(selected);
            selectedCells=cells;
            if(selectedCell>=cells.size()) selectedCell=-1;
            if(selectedCell>=0) {
                var cell=cells.get(selectedCell); cap=cell.capacity();
                keys=StorageScanner.contents(cell.storage());
                title=Component.translatable("gui.me_storage_controller.cell_title",selectedInfo.name(),selectedCell+1);
                if(cell.stack().isEmpty() && cell.storage()==null) {
                    cap=new StorageScanner.Capacity(0,0,0,0,false,0); error="gui.me_storage_controller.empty";
                } else if(!cell.readable() || StorageScanner.hasReadError(cell.storage())) error="gui.me_storage_controller.unreadable";
            }
            var external=StorageScanner.externalCapacity(selected);
            capacity=new Snapshot.Capacity(cap.usedBytes(),cap.totalBytes(),cap.usedTypes(),cap.totalTypes(),cap.unknownCells(),
                external.occupiedSlots(),external.totalSlots(),external.fluidAmount(),external.fluidCapacity());
            if(StorageScanner.isDegraded(selected)) error="gui.me_storage_controller.unreadable";
            else if(!selected.active()) error="gui.me_storage_controller.offline";
        }
        var contents=new ArrayList<Snapshot.Content>();
        for(var entry:keys) if(entry.getLongValue()>0 && (matches(entry.getKey().getDisplayName().getString()+" "+entry.getKey().getId(),contentQuery)
            || contentMatches.contains(entry.getKey().getId()))) contents.add(new Snapshot.Content(entry.getKey(),entry.getLongValue()));
        Comparator<Snapshot.Content> byName=Comparator.comparing(c->c.key().getDisplayName().getString(),String.CASE_INSENSITIVE_ORDER);
        contents.sort(sortByAmount ? Comparator.comparingLong(Snapshot.Content::amount).reversed().thenComparing(byName) : byName.thenComparing(c->c.key().getId().toString()));
        int contentPages=pages(contents.size(),6); contentPage=Math.min(contentPage,contentPages-1);
        int from=contentPage*6,to=Math.min(from+6,contents.size());
        var previews=selectedCells.stream().skip(cellOffset()).limit(10).map(cell -> new Snapshot.CellPreview(cell.slot(),cell.stack(),cell.capacity().usedBytes(),cell.capacity().totalBytes(),cell.readable())).toList();
        snapshot=new Snapshot(true,error,selectedId,selectedCell,infos,devicePage,devicePages,filtered.size(),title,capacity,
            List.copyOf(contents.subList(from,to)),contentPage,contentPages,contents.size(),
            selected!=null ? Math.max(0,StorageScanner.cellCount(selected)) : 0,
            canEdit() ? Math.max(0,Math.min(10,selected.cells().getSlots()-cellOffset())) : 0,previews,selectedInfo,acknowledgedRevision);
        send();
    }
    private void send() { if(player instanceof ServerPlayer server) Network.send(server,containerId,snapshot); }
    private static boolean matches(String value,String query) { return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)); }
    private static int pages(int size,int limit) { return Math.max(1,(size+limit-1)/limit); }
    private int cellOffset() { return Math.max(0,selectedCell)/10*10; }
    private Snapshot.DeviceInfo deviceInfo(Device device) {
        var target=StorageScanner.targetInfo(device);
        var location=target==null ? device.location() : target.location();
        Component name=target==null ? device.name() : target.name();
        ItemStack icon=ItemStack.EMPTY;
        if(target!=null && device.node().getLevel().hasChunkAt(target.location().pos())) {
            icon=new ItemStack(device.node().getLevel().getBlockState(target.location().pos()).getBlock());
        } else if(device.node().getVisualRepresentation()!=null) icon=new ItemStack(device.node().getVisualRepresentation().getItem());
        return new Snapshot.DeviceInfo(device.id(),name,device.kind().name().toLowerCase(Locale.ROOT),
            location==null ? new ResourceLocation(MEStorageController.ID,"unknown") : location.dimension().location(),
            location==null ? BlockPos.ZERO : location.pos(),device.active(),device.name(),target==null ? "" : target.face().getName(),
            target==null ? (StorageScanner.cellCount(device)>0 ? "cells" : "unknown") : target.adapter(),icon);
    }

    private final class RemoteCells implements IItemHandlerModifiable {
        private IItemHandlerModifiable handler() { return player.level().isClientSide ? clientCells : current() && selected.cells() instanceof IItemHandlerModifiable h ? h : null; }
        @Override public int getSlots() { return 10; }
        @Override public ItemStack getStackInSlot(int slot) { var h=handler(); int actual=player.level().isClientSide ? slot : slot+cellOffset(); return h!=null && actual<h.getSlots() ? h.getStackInSlot(actual) : ItemStack.EMPTY; }
        @Override public void setStackInSlot(int slot,ItemStack stack) {
            var h=handler(); if(player.level().isClientSide) { clientCells.setStackInSlot(slot,stack); return; }
            slot+=cellOffset();
            if(h!=null && slot<h.getSlots() && canEdit() && (stack.isEmpty() || h.isItemValid(slot,stack)) && stack.getCount()<=1) h.setStackInSlot(slot,stack);
        }
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) { var h=handler(); if(!player.level().isClientSide) slot+=cellOffset(); return h!=null && slot<h.getSlots() && canEdit() ? h.insertItem(slot,stack,simulate) : stack; }
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { var h=handler(); if(!player.level().isClientSide) slot+=cellOffset(); return h!=null && slot<h.getSlots() && canEdit() ? h.extractItem(slot,amount,simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot,ItemStack stack) { var h=handler(); if(!player.level().isClientSide) slot+=cellOffset(); return h!=null && slot<h.getSlots() && h.isItemValid(slot,stack); }
    }
}
