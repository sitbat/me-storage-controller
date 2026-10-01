package dev.mestorage.controller.storage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.*;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.jetbrains.annotations.Nullable;
import dev.mestorage.controller.folder.FolderBook;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Live mutation targets, retaining the exact wrappers mounted by each provider. */
public final class ContentAccess {
    private ContentAccess() {}

    public static @Nullable MEStorage resolve(IGrid grid, StorageScanner.Device device, int cell) {
        if (!StorageScanner.isCurrent(device, grid) || !device.node().isActive()) return null;
        var provider = device.node().getService(IStorageProvider.class);
        if (provider == null) return null;
        var scoped = new NetworkStorage();
        Set<MEStorage> mounted = Collections.newSetFromMap(new IdentityHashMap<>());
        // Ask the current provider again: cached discovery wrappers may outlive a changed bus target or cell.
        provider.mountInventories((storage, priority) -> {
            if (storage != null && mounted.add(storage)) scoped.mount(priority, storage);
        });
        if (cell < 0) return mounted.isEmpty() ? null : scoped;
        if (!(device.owner() instanceof IChestOrDrive host) || cell >= StorageScanner.cellCount(device)) return null;
        var inventory = host.getCellInventory(cell);
        // Never unwrap an original storage cell or bypass a provider's decision not to mount it.
        return inventory != null && mounted.contains(inventory) ? inventory : null;
    }

    /** All identities are live provider wrappers, never re-created cell inventories or unwrapped delegates. */
    public record Resolved(@Nullable MEStorage storage,List<FolderBook.MemberRef> members,
                           List<StorageScanner.Device> devices,List<MEStorage> identities,String error) {
        public Resolved {members=List.copyOf(members);devices=List.copyOf(devices);identities=List.copyOf(identities);}
        public boolean success(){return error.isEmpty() && storage!=null;}
        public boolean sameTargets(Resolved other){
            if(!success()||!other.success()||!members.equals(other.members)||devices.size()!=other.devices.size()||identities.size()!=other.identities.size())return false;
            for(int i=0;i<devices.size();i++)if(devices.get(i).owner()!=other.devices.get(i).owner()||devices.get(i).node()!=other.devices.get(i).node())return false;
            for(int i=0;i<identities.size();i++)if(identities.get(i)!=other.identities.get(i))return false;
            return true;
        }
    }
    private record Mount(MEStorage storage,int priority) {}
    public static Resolved resolveMembers(IGrid grid,Collection<FolderBook.MemberRef> requested){
        return resolveMembers(grid,requested,StorageScanner.discover(grid));
    }
    /** Menus pass their latest device discovery to avoid scanning every provider on each click. */
    public static Resolved resolveMembers(IGrid grid,Collection<FolderBook.MemberRef> requested,Collection<StorageScanner.Device> knownDevices){
        var members=FolderBook.normalize(requested);
        var selected=new LinkedHashMap<String,StorageScanner.Device>();
        var identities=new ArrayList<MEStorage>();
        try {
            var available=new HashMap<String,StorageScanner.Device>();
            for(var device:knownDevices)available.put(device.id(),device);
            for(var ref:members){
                if(!ref.valid())return failed(members,selected,identities,"member");
                var device=available.get(ref.deviceId());
                if(device==null||!StorageScanner.isCurrent(device,grid)||!device.node().isActive())return failed(members,selected,identities,"unavailable");
                selected.put(device.id(),device);
            }
            // Two separate bus wrappers can alias one physical inventory. Do not pretend
            // identity de-duplication proves them disjoint or bypass their different filters.
            var physicalTargets=new HashSet<GlobalPos>();
            for(var device:selected.values()){
                var target=StorageScanner.targetInfo(device);if(target==null)continue;
                if(!device.node().getLevel().hasChunkAt(target.location().pos()))return failed(members,selected,identities,"unavailable");
                if(target.adapter().equals("me") && selected.size()>1)return failed(members,selected,identities,"overlap");
                var positions=new HashSet<GlobalPos>();positions.add(target.location());
                var state=device.node().getLevel().getBlockState(target.location().pos());
                if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
                    positions.add(GlobalPos.of(target.location().dimension(),target.location().pos().relative(ChestBlock.getConnectedDirection(state))));
                }
                for(var pos:positions){
                    if(!physicalTargets.add(pos))return failed(members,selected,identities,"overlap");
                    for(var other:selected.values())if(other!=device && pos.equals(other.location()))return failed(members,selected,identities,"overlap");
                }
            }
            var mounts=new HashMap<String,List<Mount>>();
            for(var device:selected.values()){
                var provider=device.node().getService(IStorageProvider.class);
                if(provider==null)return failed(members,selected,identities,"unavailable");
                var list=new ArrayList<Mount>();
                provider.mountInventories((storage,priority)->{if(storage!=null)list.add(new Mount(storage,priority));});
                mounts.put(device.id(),list);
            }
            Set<MEStorage> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            var scoped=new NetworkStorage();
            for(var ref:members){
                var device=selected.get(ref.deviceId());var list=mounts.get(ref.deviceId());
                if(ref.cell()>=0){
                    if(!(device.owner() instanceof IChestOrDrive host)||ref.cell()>=StorageScanner.cellCount(device))return failed(members,selected,identities,"unavailable");
                    var storage=host.getCellInventory(ref.cell());
                    if(storage==null){
                        if(device.cells()!=null && !device.cells().getStackInSlot(ref.cell()).isEmpty())return failed(members,selected,identities,"unavailable");
                        continue; // An empty member slot stays assigned and currently contributes nothing.
                    }
                    list=list.stream().filter(mount->mount.storage==storage).toList();
                    if(list.isEmpty())return failed(members,selected,identities,"unavailable");
                }
                for(var mount:list)if(seen.add(mount.storage)){scoped.mount(mount.priority,mount.storage);identities.add(mount.storage);}
            }
            for(var device:selected.values())if(!StorageScanner.isCurrent(device,grid)||!device.node().isActive())return failed(members,selected,identities,"unavailable");
            return new Resolved(scoped,members,new ArrayList<>(selected.values()),identities,"");
        }catch(RuntimeException failure){
            return failed(members,selected,identities,"unavailable");
        }
    }
    private static Resolved failed(List<FolderBook.MemberRef> members,Map<String,StorageScanner.Device> devices,List<MEStorage> identities,String error){
        return new Resolved(null,members,new ArrayList<>(devices.values()),identities,error);
    }
}
