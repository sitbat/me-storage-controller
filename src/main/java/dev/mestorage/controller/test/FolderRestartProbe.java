package dev.mestorage.controller.test;

import java.util.List;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.folder.FolderBook;
import dev.mestorage.controller.folder.FolderService;
import dev.mestorage.controller.storage.ContentAccess;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Explicit two-process integration probe; excluded from release jars with the other test classes. */
@Mod.EventBusSubscriber(modid=MEStorageController.ID)
public final class FolderRestartProbe {
    private static final BlockPos POS=new BlockPos(4096,160,4096);
    private static final String PROPERTY="mestorage.folderRestart",MARKER="me_storage_folder_restart_probe";
    private static final AEItemKey IRON=AEItemKey.of(Items.IRON_INGOT),GOLD=AEItemKey.of(Items.GOLD_INGOT);
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(FolderRestartProbe.class);
    private static boolean initialized,finished;
    private static int ticks;
    private FolderRestartProbe() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        String phase=System.getProperty(PROPERTY,"");
        if(FMLEnvironment.production||phase.isEmpty()||finished||event.phase!=TickEvent.Phase.END)return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        try {
            check(phase.equals("seed")||phase.equals("verify"),"Unknown restart probe phase "+phase);
            ServerLevel level=server.overworld();var marker=marker(server);ticks++;
            if(!initialized){
                initialized=true;
                if(phase.equals("seed")){
                    check(!marker.tag.getBoolean("seeded"),"Seed marker already exists; use a fresh isolated run directory");
                    level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);
                    // Drive is north of the controller, outside this chunk along z.
                    level.setChunkForced(POS.north().getX()>>4,POS.north().getZ()>>4,true);
                    level.setBlock(POS.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),3);
                    level.setBlock(POS,MEStorageController.CONTROLLER.get().defaultBlockState(),3);
                    var driveState=AEBlocks.DRIVE.block().defaultBlockState();
                    level.setBlock(POS.north(),IOrientationStrategy.get(driveState).setFacing(driveState,Direction.NORTH),3);
                    var drive=(DriveBlockEntity)level.getBlockEntity(POS.north());
                    check(drive!=null,"Could not create seed drive");
                    var cell=AEItems.ITEM_CELL_64K.stack();cell.setHoverName(Component.literal("Restart: original cell NBT"));
                    drive.getInternalInventory().setItemDirect(0,cell);
                    drive.getInternalInventory().setItemDirect(1,AEItems.ITEM_CELL_1K.stack());
                }else{
                    check(marker.tag.getBoolean("seeded"),"No persisted seed marker was loaded from disk");
                    // Verification deliberately never places blocks or supplies inventory.
                    level.getChunk(POS);level.getChunk(POS.north());
                    check(level.getBlockEntity(POS) instanceof ControllerBlockEntity,"Saved controller missing");
                    check(level.getBlockEntity(POS.north()) instanceof DriveBlockEntity,"Saved drive missing");
                }
            }
            check(ticks<=600,"AE fixture did not become active within 600 ticks");
            var controller=(ControllerBlockEntity)level.getBlockEntity(POS);
            var drive=(DriveBlockEntity)level.getBlockEntity(POS.north());
            if(controller==null||drive==null||!controller.getMainNode().isActive()||!drive.getMainNode().isActive())return;
            check(controller.getMainNode().getGrid()==drive.getMainNode().getGrid(),"Persisted devices did not reconnect to one grid");
            var grid=controller.getMainNode().getGrid();var service=grid.getService(FolderService.class);
            var device=StorageScanner.discover(grid).stream().filter(d->d.owner()==drive).findFirst().orElseThrow();
            if(phase.equals("seed")){
                check(drive.getCellInventory(0).insert(IRON,12345,Actionable.MODULATE,IActionSource.empty())==12345,"Seed iron insertion failed");
                check(drive.getCellInventory(1).insert(GOLD,77,Actionable.MODULATE,IActionSource.empty())==77,"Seed excluded-slot insertion failed");
                drive.getOriginalCellInventory(0).persist();drive.getOriginalCellInventory(1).persist();
                ListTag original=cells(drive);
                var root=service.apply(service.view().revision(),edit(null,"重启验证 / Restart",List.of()));
                check(root.success(),"Seed root creation failed: "+root.error());
                var ref=new FolderBook.MemberRef(device.id(),0);
                var child=service.apply(service.view().revision(),edit(root.createdId(),"固定槽位 / Slot 1",List.of(ref)));
                check(child.success(),"Seed child creation failed: "+child.error());
                check(cells(drive).equals(original),"Folder creation changed complete physical cell NBT");
                marker.tag.putBoolean("seeded",true);marker.tag.putUUID("root",root.createdId());marker.tag.putUUID("child",child.createdId());
                marker.tag.putString("device",device.id());marker.tag.putLong("revision",service.view().revision());
                marker.tag.put("cells",original);marker.setDirty();
            }else{
                UUID root=marker.tag.getUUID("root"),child=marker.tag.getUUID("child");
                var view=service.view();
                var restored=view.folders().stream().filter(f->f.id().equals(child)).findFirst().orElseThrow();
                check(root.equals(restored.parent()),"Nested folder parent UUID was not restored");
                check(view.folders().stream().anyMatch(f->f.id().equals(root)&&f.name().equals("重启验证 / Restart")),"Root folder/name missing after actual restart");
                check(restored.name().equals("固定槽位 / Slot 1"),"Child name changed across restart");
                var ref=new FolderBook.MemberRef(marker.tag.getString("device"),0);
                check(device.id().equals(ref.deviceId())&&service.members(root).equals(List.of(ref)),"Device address/slot membership changed across restart");
                check(view.revision()>marker.tag.getLong("revision"),"New live grid must issue a newer revision than the saved process");
                check(cells(drive).equals(marker.tag.getList("cells",10)),"Original complete cell item NBT changed across process restart");
                var resolved=ContentAccess.resolveMembers(grid,service.members(root));
                check(resolved.success(),"Restored folder could not resolve its actual live slot: "+resolved.error());
                var contents=StorageScanner.contents(resolved.storage());
                check(contents.get(IRON)==12345&&contents.get(GOLD)==0,"Restored folder contents lost items or escaped the selected slot");
                check(StorageScanner.contents(drive.getCellInventory(1)).get(GOLD)==77,"Excluded physical cell changed");
            }
            finished=true;
            check(server.saveEverything(true,true,true),"Explicit world save reported failure");
            LOGGER.info("ME_STORAGE_FOLDER_RESTART_PASS phase={} revision={} book={} ticks={}",phase,service.view().revision(),service.bookId(),ticks);
            server.halt(false);
        }catch(Throwable failure){
            finished=true;LOGGER.error("ME_STORAGE_FOLDER_RESTART_FAIL phase="+phase,failure);server.halt(false);
        }
    }
    private static FolderBook.Edit edit(UUID parent,String name,List<FolderBook.MemberRef> members){
        return new FolderBook.Edit(FolderBook.Op.CREATE,null,parent,name,members,List.of());
    }
    private static ListTag cells(DriveBlockEntity drive){var result=new ListTag();for(int slot=0;slot<10;slot++)result.add(drive.getInternalInventory().getStackInSlot(slot).save(new CompoundTag()));return result;}
    private static Marker marker(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(Marker::load,Marker::new,MARKER);}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    public static final class Marker extends SavedData {
        private CompoundTag tag=new CompoundTag();
        public Marker() {}
        static Marker load(CompoundTag tag){var result=new Marker();result.tag=tag.copy();return result;}
        @Override public CompoundTag save(CompoundTag output){return tag.copy();}
    }
}
