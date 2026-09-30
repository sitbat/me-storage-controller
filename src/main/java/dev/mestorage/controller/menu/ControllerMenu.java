package dev.mestorage.controller.menu;

import appeng.api.networking.IGrid;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.network.DirectoryTransfer;
import dev.mestorage.controller.storage.StorageScanner;
import dev.mestorage.controller.storage.ContentAccess;
import dev.mestorage.controller.storage.ContainerTransfers;
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
    private static final int MUTATION_REFRESH_INTERVAL=5;
    private long nextRefresh=0,lastRequest=-100,lastRefresh=-100;
    private Network.Request pending;
    private Set<ResourceLocation> deviceMatches=Set.of(),contentMatches=Set.of();
    private final Map<String,List<ResourceLocation>> localizedSearchCache=new HashMap<>();
    private boolean selectionPending;
    private long requestedRevision;
    private long acknowledgedRevision;
    private boolean reportedStorageError;
    private List<Snapshot.DirectoryEntry> directory=List.of();
    private long nextDirectoryRefresh;
    private DirectoryTransfer.Assembler directoryAssembler=new DirectoryTransfer.Assembler();
    private DirectoryTransfer.Cursor directoryCursor;
    private List<Snapshot.DirectoryEntry> queuedDirectory;
    private List<Snapshot.DirectoryEntry> lastOfferedDirectory;
    private long directoryGeneration,lastDirectorySend=Long.MIN_VALUE;
    private boolean clientSlotsVisible=true;
    private boolean clientCellsVisible=true;
    private MEStorage observedCellStorage;
    private long contentActionTick=Long.MIN_VALUE;
    private int contentActionsThisTick;
    private List<Snapshot.Content> scannedContents=List.of();
    private IGrid scannedGrid;

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
        return remoteSlot(index,8+18*index,y);
    }
    private Slot remoteSlot(int index,int x,int y) {
        return new SlotItemHandler(remote,index,x,y) {
            @Override public boolean mayPlace(ItemStack stack) { return canEdit() && remote.isItemValid(index,stack); }
            @Override public boolean mayPickup(Player p) { return canEdit(); }
            @Override public boolean isActive() { return player.level().isClientSide ? clientCellsVisible && snapshot.editableSlots()>index : selected!=null && selected.cells()!=null && selected.cells().getSlots()>index+cellOffset(); }
        };
    }
    /** Only presentation coordinates change. Slot identity, ordering and backing inventories remain unchanged. */
    public void layoutSlots(int cellY,int inventoryY,int hotbarY) {
        layoutSlots(8,cellY,8,inventoryY,hotbarY,true);
    }
    public void layoutSlots(int cellX,int cellY,int inventoryX,int inventoryY,int hotbarY,boolean visible) {
        layoutSlots(cellX,cellY,10,inventoryX,inventoryY,hotbarY,visible,visible);
    }
    public void layoutSlots(int cellX,int cellY,int cellColumns,int inventoryX,int inventoryY,int hotbarY,boolean cellsVisible,boolean inventoryVisible) {
        if(!player.level().isClientSide) return;
        clientSlotsVisible=inventoryVisible;clientCellsVisible=cellsVisible;
        int columns=Math.max(1,Math.min(10,cellColumns));
        for(int i=0;i<10;i++) replaceSlot(i,remoteSlot(i,cellX+18*(i%columns),cellY+18*(i/columns)));
        for(int row=0;row<3;row++) for(int col=0;col<9;col++)
            replaceSlot(10+row*9+col,playerSlot(9+row*9+col,inventoryX+18*col,inventoryY+18*row));
        for(int col=0;col<9;col++) replaceSlot(37+col,playerSlot(col,inventoryX+18*col,hotbarY));
    }
    private Slot playerSlot(int index,int x,int y) {
        return new Slot(playerInventory,index,x,y) {
            @Override public boolean isActive() { return clientSlotsVisible; }
        };
    }
    private void replaceSlot(int index,Slot slot) { slot.index=index; slots.set(index,slot); }
    public Snapshot getSnapshot() { return snapshot; }
    public long getRequestedRevision() { return requestedRevision; }
    public void setSnapshot(Snapshot value) {
        if(value.revision()<snapshot.revision()) return;
        if(!value.online()) {
            directory=List.of(); directoryAssembler=new DirectoryTransfer.Assembler();
        }
        snapshot=value.withDirectory(directory,value.directoryTotalDevices());
        if(value.revision()>=requestedRevision) selectionPending=false;
    }
    public void acceptDirectoryFrame(DirectoryTransfer.Frame frame) {
        if(!player.level().isClientSide || !snapshot.online()) return;
        var complete=directoryAssembler.accept(frame);
        if(complete!=null) {
            directory=complete;
            snapshot=snapshot.withDirectory(complete,complete.size());
        }
    }
    public void request(String deviceId,int cell,int dp,int cp,String dq,String cq,boolean amount) {
        if(!deviceId.equals(snapshot.selectedDevice()) || cell!=snapshot.selectedCell()) {
            selectionPending=true;
        }
        Network.request(new Network.Request(containerId,deviceId,cell,dp,cp,dq,cq,amount,localizedMatches(dq),localizedMatches(cq),++requestedRevision));
    }
    /** The screen consumes these clicks; vanilla slot prediction must not run for the virtual grid. */
    public void requestContent(int contentIndex,int button,boolean shift) {
        if(!player.level().isClientSide || selectionPending || requestedRevision!=snapshot.revision()
                || !snapshot.online() || contentIndex<0 || contentIndex>=Snapshot.CONTENT_PAGE_SIZE || button<0 || button>1) return;
        var key=contentIndex<snapshot.contents().size() ? snapshot.contents().get(contentIndex).key() : null;
        Network.contentAction(new Network.ContentAction(containerId,snapshot.revision(),snapshot.selectedDevice(),snapshot.selectedCell(),key,button,shift));
    }
    public void handleContentAction(Network.ContentAction action) {
        if(player.level().isClientSide) return;
        long now=player.level().getGameTime();
        if(contentActionTick!=now) { contentActionTick=now; contentActionsThisTick=0; }
        // Far above real input rates; bounded work for a malicious stream without coalescing normal clicks.
        if(++contentActionsThisTick>128) return;
        if(action.containerId()!=containerId || action.button()<0 || action.button()>1 || pending!=null
                || action.revision()!=acknowledgedRevision || !action.deviceId().equals(selectedId) || action.cell()!=selectedCell
                || !snapshot.online() || !contentProtectionAllows()) { broadcastFullState(); return; }
        try {
            var grid=grid();
            var storage=contentStorage(grid);
            if(storage==null) return;
            var source=IActionSource.ofPlayer(player,()->controller.getMainNode().getNode());
            // A client key is only a reference to a displayed entry, never an arbitrary extraction request.
            boolean visibleKey=action.key()!=null && snapshot.contents().stream().anyMatch(entry->entry.key().equals(action.key()));
            if(action.key()!=null && !(action.key() instanceof AEItemKey) && !visibleKey) return;
            var containerResult=ContainerTransfers.handle(this,player,storage,grid.getEnergyService(),source,
                    visibleKey ? action.key() : null,action.button(),action.shift());
            if(containerResult!=ContainerTransfers.Result.UNHANDLED) {
                if(containerResult==ContainerTransfers.Result.CHANGED) scheduleMutationRefresh();
                return;
            }
            var carried=getCarried();
            if(!carried.isEmpty()) {
                var key=AEItemKey.of(carried);
                int amount=action.button()==1 ? 1 : carried.getCount();
                int inserted=(int)StorageHelper.poweredInsert(grid.getEnergyService(),storage,key,amount,source);
                if(inserted>0) { var remainder=carried.copy(); remainder.shrink(inserted); setCarried(remainder); scheduleMutationRefresh(); }
            } else if(action.key() instanceof AEItemKey key
                    && snapshot.contents().stream().anyMatch(entry->entry.key().equals(key))) {
                boolean toBackpack=action.shift() && action.button()==0;
                int amount=key.getMaxStackSize();
                if(toBackpack) {
                    amount=backpackSpace(key,amount);
                } else if(action.button()==1) {
                    long available=storage.extract(key,amount,Actionable.SIMULATE,source);
                    amount=(int)((available+1)/2);
                }
                if(amount<=0) return;
                int extracted=(int)StorageHelper.poweredExtraction(grid.getEnergyService(),storage,key,amount,source);
                if(extracted>0) {
                    if(!toBackpack) setCarried(key.toStack(extracted));
                    else placeInBackpack(key,extracted);
                    scheduleMutationRefresh();
                }
            }
        } finally {
            // Send cursor and physical slots immediately, without triggering a discovery/content rescan.
            super.broadcastChanges();
        }
    }
    private MEStorage contentStorage(IGrid grid) {
        if(grid==null || !stillValid(player)) return null;
        if(selectedId.isEmpty()) return selectedCell<0 ? grid.getStorageService().getInventory() : null;
        if(!current()) return null;
        try {
            var storage=ContentAccess.resolve(grid,selected,selectedCell);
            return selectedCell<0 || storage==observedCellStorage ? storage : null;
        } catch(RuntimeException problem) {
            if(!reportedStorageError) {
                org.slf4j.LoggerFactory.getLogger(ControllerMenu.class).warn("Unable to resolve live content target; transfer rejected",problem);
                reportedStorageError=true;
            }
            return null;
        }
    }
    private int backpackSpace(AEItemKey key,int limit) {
        int space=0,max=Math.min(key.getMaxStackSize(),playerInventory.getMaxStackSize());
        for(var stack:playerInventory.items) {
            if(stack.isEmpty()) space+=max;
            else if(key.matches(stack)) space+=Math.max(0,max-stack.getCount());
            if(space>=limit) return limit;
        }
        return space;
    }
    private void placeInBackpack(AEItemKey key,int amount) {
        int max=Math.min(key.getMaxStackSize(),playerInventory.getMaxStackSize());
        // Merge existing stacks before consuming an empty slot; capacity was checked before extraction.
        for(int pass=0;pass<2 && amount>0;pass++) for(int slot=0;slot<playerInventory.items.size() && amount>0;slot++) {
            var stack=playerInventory.getItem(slot);
            if(pass==0 ? stack.isEmpty() || !key.matches(stack) : !stack.isEmpty()) continue;
            int moved=Math.min(amount,Math.max(0,max-stack.getCount()));
            if(moved>0) { playerInventory.setItem(slot,key.toStack(stack.getCount()+moved)); amount-=moved; }
        }
        // Defensive conservation if an addon changes player inventory inside its extract callback.
        if(amount>0) setCarried(key.toStack(amount));
        playerInventory.setChanged();
    }
    private boolean contentProtectionAllows() {
        if(!stillValid(player) || !player.mayBuild() || grid()==null || !allowsAt(controller.getBlockPos(),Direction.UP)) return false;
        if(selectedId.isEmpty()) return selectedCell<0;
        if(!current() || selected.location()==null || !player.level().dimension().equals(selected.location().dimension())
                || !allowsAt(selected.location().pos(),selected.side()==null ? Direction.UP : selected.side())) return false;
        var target=StorageScanner.targetInfo(selected);
        return target==null || player.level().dimension().equals(target.location().dimension())
                && player.level().hasChunkAt(target.location().pos()) && allowsAt(target.location().pos(),target.face());
    }
    private boolean allowsAt(BlockPos pos,Direction face) {
        if(!player.level().hasChunkAt(pos) || !player.level().mayInteract(player,pos)) return false;
        var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,
                new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false));
        return !MinecraftForge.EVENT_BUS.post(event) && event.getUseBlock()!=Event.Result.DENY;
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
        // Vanilla turns the second rapid left-click into PICKUP_ALL on release.
        // Cells are individual containers, so this gesture means placing the
        // carried cell back, not collecting a stack of identical containers.
        if(type==ClickType.PICKUP_ALL && (slot>=0 && slot<10 || StorageCells.isCellHandled(getCarried()))) type=ClickType.PICKUP;
        boolean usesRemote=slot>=0 && slot<10 || type==ClickType.QUICK_MOVE && slot>=10 && slot<slots.size()
                && StorageCells.isCellHandled(slots.get(slot).getItem()) && (p.level().isClientSide ? snapshot.editableSlots()>0 : selected!=null && selected.cells()!=null);
        if(p.level().isClientSide) { if(!canSendClick(slot,type)) return; super.clicked(slot,button,type,p); return; }
        if(!stillValid(p)) return;
        if(type==ClickType.QUICK_CRAFT) { broadcastFullState(); return; }
        if((usesRemote || type==ClickType.QUICK_MOVE) && pending!=null || usesRemote && !protectionAllows()) { broadcastFullState(); return; }
        super.clicked(slot,button,type,p);
        if(usesRemote) scheduleMutationRefresh();
    }
    public boolean canSendClick(int slot,ClickType type) {
        if(type==ClickType.QUICK_CRAFT) return false;
        return !selectionPending || !(slot>=0 && slot<10 || type==ClickType.QUICK_MOVE && slot>=10);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) {
        // AbstractContainerScreen uses EMPTY only to test double-click eligibility.
        // The actual inventory gather loop passes a nonempty stack and must never
        // silently collect cells from a remote drive through a backpack click.
        return stack.isEmpty() || slot.index>=10;
    }
    private void scheduleMutationRefresh() {
        long now=player.level().getGameTime();
        nextRefresh=Math.min(nextRefresh,Math.max(now+1,lastRefresh+MUTATION_REFRESH_INTERVAL));
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),copy=source.copy();
        boolean cellManagement=index<10 || StorageCells.isCellHandled(source)
                && (p.level().isClientSide ? snapshot.editableSlots()>0 : selected!=null && selected.cells()!=null);
        if(!cellManagement) {
            if(p.level().isClientSide || pending!=null || !contentProtectionAllows()) return ItemStack.EMPTY;
            var grid=grid(); var storage=contentStorage(grid);
            if(storage==null) return ItemStack.EMPTY;
            int inserted=(int)StorageHelper.poweredInsert(grid.getEnergyService(),storage,AEItemKey.of(source),source.getCount(),
                    IActionSource.ofPlayer(player,()->controller.getMainNode().getNode()));
            if(inserted<=0) return ItemStack.EMPTY;
            source.shrink(inserted); slot.set(source.isEmpty() ? ItemStack.EMPTY : source); slot.setChanged();
            scheduleMutationRefresh(); return copy;
        }
        if(!canEdit()) return ItemStack.EMPTY;
        if(index<10) {
            if(!moveItemStackTo(source,10,46,true)) return ItemStack.EMPTY;
        } else if(!moveItemStackTo(source,0,10,false)) return ItemStack.EMPTY;
        slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        slot.onTake(p,source); scheduleMutationRefresh();
        return copy;
    }
    @Override public void broadcastChanges() {
        boolean refreshed=false;
        if(!player.level().isClientSide) {
            long now=player.level().getGameTime();
            boolean navigating=pending!=null && (!pending.deviceId().equals(selectedId) || pending.cell()!=selectedCell);
            boolean paging=pending!=null && isContentPageRequest(pending);
            // Navigation and pure pagination acknowledge next tick. Search/filter
            // changes retain their debounce; vanilla slot synchronization never waits.
            if(pending!=null && now-lastRequest>=(navigating || paging ? 1 : 5)) {
                var request=pending; pending=null; lastRequest=now;
                acknowledgedRevision=request.revision();
                if(paging && canReuseContentScan(now)) {
                    contentPage=Math.min(Math.max(0,request.contentPage()),pages(scannedContents.size(),Snapshot.CONTENT_PAGE_SIZE)-1);
                    snapshot=contentPageSnapshot(); refreshed=true;
                } else {
                    clearContentScan();
                    selectedId=request.deviceId(); selectedCell=Math.max(-1,request.cell());
                    devicePage=Math.max(0,request.devicePage()); contentPage=Math.max(0,request.contentPage());
                    deviceQuery=request.deviceQuery(); contentQuery=request.contentQuery(); sortByAmount=request.sortByAmount(); nextRefresh=0;
                    deviceMatches=Set.copyOf(request.deviceMatches()); contentMatches=Set.copyOf(request.contentMatches());
                }
            }
            if(now>=nextRefresh) { refresh(); lastRefresh=now; nextRefresh=now+20; refreshed=true; }
        }
        super.broadcastChanges();
        // Vanilla slot packets must precede the selection acknowledgement. The
        // client stays locked until both labels and their real cells are current.
        if(refreshed) send();
        flushDirectory();
    }
    private boolean isContentPageRequest(Network.Request request) {
        return request.deviceId().equals(selectedId) && request.cell()==selectedCell && request.devicePage()==devicePage
                && request.deviceQuery().equals(deviceQuery) && request.contentQuery().equals(contentQuery)
                && request.sortByAmount()==sortByAmount && Set.copyOf(request.deviceMatches()).equals(deviceMatches)
                && Set.copyOf(request.contentMatches()).equals(contentMatches);
    }
    private boolean canReuseContentScan(long now) {
        // Pagination does not extend either the regular refresh deadline or a mutation's earlier deadline.
        if(now>=nextRefresh || scannedGrid==null || scannedGrid!=grid() || !snapshot.online() || !stillValid(player)) return false;
        if(selectedId.isEmpty()) return selectedCell<0;
        if(!current()) return false;
        if(selectedCell<0) return true;
        try {
            return selected.owner() instanceof IChestOrDrive host && selectedCell<StorageScanner.cellCount(selected)
                    && host.getCellInventory(selectedCell)==observedCellStorage;
        } catch(RuntimeException staleInventory) {
            return false;
        }
    }
    private Snapshot contentPageSnapshot() {
        int from=contentPage*Snapshot.CONTENT_PAGE_SIZE;
        int to=Math.min(from+Snapshot.CONTENT_PAGE_SIZE,scannedContents.size());
        var previous=snapshot;
        return new Snapshot(previous.online(),previous.error(),previous.selectedDevice(),previous.selectedCell(),
                previous.devices(),previous.devicePage(),previous.devicePages(),previous.deviceCount(),previous.title(),previous.capacity(),
                List.copyOf(scannedContents.subList(from,to)),contentPage,pages(scannedContents.size(),Snapshot.CONTENT_PAGE_SIZE),scannedContents.size(),
                previous.cellSlots(),previous.editableSlots(),previous.cells(),previous.selectedInfo(),acknowledgedRevision,
                previous.directory(),previous.directoryTotalDevices());
    }
    private void clearContentScan() { scannedContents=List.of(); scannedGrid=null; }
    private void refresh() {
        clearContentScan();
        observedCellStorage=null;
        var grid=grid();
        if(grid==null || !stillValid(player)) {
            directory=List.of(); nextDirectoryRefresh=0;
            selected=null; snapshot=new Snapshot(false,"gui.me_storage_controller.offline","",-1,List.of(),0,1,0,
                controller==null ? Component.empty() : controller.getDisplayName(),new Snapshot.Capacity(-1,-1,-1,-1,0),List.of(),0,1,0,0,0,List.of(),null,acknowledgedRevision,List.of(),0);
            return;
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
        Map<String,List<StorageScanner.CellInfo>> cellReads=new HashMap<>();
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
                var cap=StorageScanner.Capacity.UNKNOWN;
                if(device.owner() instanceof IChestOrDrive && !StorageScanner.isDegraded(device)) {
                    var cells=StorageScanner.readCells(device); cellReads.put(device.id(),cells);
                    cap=StorageScanner.aggregateCapacity(cells);
                }
                unknown+=cap.unknownCells();
                if(cap.totalBytes()>=0 || cap.totalBytes()==StorageScanner.Capacity.UNLIMITED) {
                    used=StorageScanner.saturatedAdd(used,cap.usedBytes()); total=StorageScanner.sumLimits(total,cap.totalBytes());
                    types=StorageScanner.saturatedAdd(types,cap.usedTypes()); typeTotal=StorageScanner.sumLimits(typeTotal,cap.totalTypes()); }
            }
            capacity=new Snapshot.Capacity(used,total,types,typeTotal,unknown);
        } else {
            title=selectedInfo.name();
            var cells=StorageScanner.readCells(selected);
            cellReads.put(selected.id(),cells);
            selectedCells=cells;
            var cap=selected.owner() instanceof IChestOrDrive && !StorageScanner.isDegraded(selected)
                ? StorageScanner.aggregateCapacity(cells) : StorageScanner.Capacity.UNKNOWN;
            if(selectedCell>=cells.size()) selectedCell=-1;
            keys=new KeyCounter();
            if(selectedCell>=0) {
                if(selected.owner() instanceof IChestOrDrive host) {
                    try { observedCellStorage=host.getCellInventory(selectedCell); }
                    catch(RuntimeException problem) {
                        // A broken addon getter must not turn a degraded read-only view into a menu crash.
                        observedCellStorage=null;
                        error="gui.me_storage_controller.unreadable";
                    }
                }
                var cell=cells.get(selectedCell); cap=cell.capacity();
                keys=StorageScanner.contents(cell.storage());
                title=Component.translatable("gui.me_storage_controller.cell_title",selectedInfo.name(),selectedCell+1);
                if(cell.stack().isEmpty() && cell.storage()==null) {
                    cap=new StorageScanner.Capacity(0,0,0,0,false,0); error="gui.me_storage_controller.empty";
                } else if(!cell.readable() || StorageScanner.hasReadError(cell.storage())) error="gui.me_storage_controller.unreadable";
            } else if(selected.active()) keys=StorageScanner.contents(selected.storage());
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
        int contentPages=pages(contents.size(),Snapshot.CONTENT_PAGE_SIZE); contentPage=Math.min(contentPage,contentPages-1);
        int from=contentPage*Snapshot.CONTENT_PAGE_SIZE,to=Math.min(from+Snapshot.CONTENT_PAGE_SIZE,contents.size());
        var previews=selectedCells.stream().skip(cellOffset()).limit(10).map(cell -> new Snapshot.CellPreview(cell.slot(),cell.stack(),cell.capacity().usedBytes(),cell.capacity().totalBytes(),cell.readable())).toList();
        refreshDirectory(devices,deviceInfos,cellReads);
        snapshot=new Snapshot(true,error,selectedId,selectedCell,infos,devicePage,devicePages,filtered.size(),title,capacity,
            List.copyOf(contents.subList(from,to)),contentPage,contentPages,contents.size(),
            selected!=null ? Math.max(0,StorageScanner.cellCount(selected)) : 0,
            canEdit() ? Math.max(0,Math.min(10,selected.cells().getSlots()-cellOffset())) : 0,previews,selectedInfo,acknowledgedRevision,directory,devices.size());
        scannedContents=List.copyOf(contents); scannedGrid=grid;
    }
    /** Reuse reads already needed for detail/capacity. Other branches scan at most once per second. */
    private void refreshDirectory(List<Device> devices,Map<String,Snapshot.DeviceInfo> infos,
                                  Map<String,List<StorageScanner.CellInfo>> cellReads) {
        long now=player.level().getGameTime(); boolean refreshAll=now>=nextDirectoryRefresh;
        var old=new HashMap<String,Snapshot.DirectoryEntry>();
        for(var entry:directory) old.put(entry.device().id(),entry);
        var entries=new ArrayList<Snapshot.DirectoryEntry>();
        for(var device:devices) {
            int count=StorageScanner.cellCount(device);
            int limit=Math.max(0,count);
            var cached=old.get(device.id()); var read=cellReads.get(device.id());
            List<Snapshot.CellPreview> children;
            if(read!=null || refreshAll || cached==null || cached.cellSlots()!=count || cached.cells().size()!=limit) {
                if(read==null) read=StorageScanner.readCells(device,limit);
                children=read.stream().limit(limit).map(cell -> new Snapshot.CellPreview(cell.slot(),cell.stack(),
                        cell.capacity().usedBytes(),cell.capacity().totalBytes(),cell.readable())).toList();
            } else children=cached.cells();
            entries.add(new Snapshot.DirectoryEntry(infos.get(device.id()),count,children));
        }
        directory=List.copyOf(entries);
        if(refreshAll) nextDirectoryRefresh=now+20;
    }
    private void send() {
        if(player instanceof ServerPlayer server) {
            Network.send(server,containerId,snapshot);
            if(snapshot.online()) {
                if(directory!=lastOfferedDirectory) { queuedDirectory=directory; lastOfferedDirectory=directory; }
            } else { queuedDirectory=null; directoryCursor=null; lastOfferedDirectory=null; }
        }
    }
    /** No more than 96 KiB/tick; a refresh queues the latest generation without cancelling an active stream. */
    private void flushDirectory() {
        if(!(player instanceof ServerPlayer server) || !snapshot.online()) return;
        long now=player.level().getGameTime();
        if(now==lastDirectorySend) return;
        lastDirectorySend=now;
        for(int sent=0;sent<2;sent++) {
            if(directoryCursor==null) {
                if(queuedDirectory==null) return;
                directoryCursor=new DirectoryTransfer.Cursor(++directoryGeneration,queuedDirectory);
                queuedDirectory=null;
            }
            var frame=directoryCursor.next();
            Network.sendDirectory(server,containerId,frame);
            if(directoryCursor.finished()) directoryCursor=null;
        }
    }
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
