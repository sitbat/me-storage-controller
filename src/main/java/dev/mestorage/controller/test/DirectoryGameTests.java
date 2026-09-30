package dev.mestorage.controller.test;

import java.util.ArrayList;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.DirectoryTransfer;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.Snapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class DirectoryGameTests {
    @GameTest(template="empty",timeoutTicks=400)
    public static void actualGridDirectoryExceedsEveryFormerDisplayLimit(GameTestHelper helper) {
        var pos=new BlockPos(2,2,2);
        helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos,MEStorageController.CONTROLLER.get());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(pos);
        var player=helper.makeMockSurvivalPlayer(); var absolute=helper.absolutePos(pos);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new ControllerMenu(43,player.getInventory(),controller); player.containerMenu=menu;
        var hosts=new ArrayList<DirectoryHost>();
        var targetId=new String[1];
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive(),"Directory controller must boot"))
                .thenExecute(()->{
                    // Public addon-host API on real AE managed nodes. No enormous physical
                    // structure, altered channel config or overlapping GameTest worlds.
                    for(int index=0;index<260;index++) {
                        var host=new DirectoryHost(index==0?512:16); hosts.add(host);
                        host.node.create(helper.getLevel(),null);
                        GridHelper.createConnection(controller.getMainNode().getNode(),host.node.getNode());
                    }
                }).thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive()
                        && hosts.stream().allMatch(host->host.node.isActive()),"Large real AE grid must finish pathing"))
                .thenExecute(()->{
                    menu.broadcastChanges(); var snapshot=menu.getSnapshot();
                    helper.assertTrue(snapshot.directory().size()==260 && snapshot.directoryTotalDevices()==260,
                            "Devices beyond the former 256-device cap must remain visible");
                    helper.assertTrue(snapshot.directory().stream().mapToInt(entry->entry.cells().size()).sum()==4656,
                            "Children beyond the former 4096-cell cap must remain visible");
                    var target=snapshot.directory().stream().filter(entry->entry.cellSlots()==512).findFirst().orElseThrow();
                    targetId[0]=target.device().id();
                    helper.assertTrue(target.cells().size()==512 && !target.cells().get(511).icon().isEmpty()
                            && !snapshot.directoryTruncated(),"Single addon host must expose every slot beyond 256");
                    var decoded=roundtrip(helper,snapshot.directory(),1);
                    helper.assertTrue(decoded.size()==260 && decoded.stream().mapToInt(entry->entry.cells().size()).sum()==4656,
                            "Bounded frames must deliver the entire real grid");
                    menu.handleRequest(new Network.Request(menu.containerId,targetId[0],511,0,0,"","",true,List.of(),List.of(),1));
                }).thenWaitUntil(()->{
                    menu.broadcastChanges();
                    helper.assertTrue(menu.getSnapshot().revision()==1,"Late-slot selection must acknowledge");
                }).thenExecute(()->{
                    try {
                        var selected=menu.getSnapshot();
                        helper.assertTrue(selected.selectedDevice().equals(targetId[0]) && selected.selectedCell()==511
                                && selected.cellSlots()==512 && selected.cells().stream().anyMatch(cell->cell.slot()==511),
                                "Directory late leaf must retain its actual device and absolute slot");
                        helper.assertTrue(selected.contents().stream().anyMatch(content->content.key().equals(AEItemKey.of(Items.IRON_INGOT))
                                && content.amount()==1234),"Late leaf must read the real cell contents");
                    } finally { hosts.forEach(host->host.node.destroy()); player.containerMenu=player.inventoryMenu; }
                }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void directoryFramesBoundUtf8AndReplaceWholeGenerations(GameTestHelper helper) {
        String huge="目录😀".repeat(4000);
        var cells=new ArrayList<Snapshot.CellPreview>();
        for(int slot=0;slot<600;slot++) {
            var icon=AEItems.ITEM_CELL_1K.stack(); icon.setHoverName(Component.literal(huge));
            cells.add(new Snapshot.CellPreview(slot,icon,slot,1024,true));
        }
        var info=new Snapshot.DeviceInfo("test:oversized-name",Component.literal(huge),"drive",
                new ResourceLocation("minecraft","overworld"),BlockPos.ZERO,true,Component.literal(huge),"","cells",ItemStack.EMPTY);
        var entries=List.of(new Snapshot.DirectoryEntry(info,600,cells));
        var assembler=new DirectoryTransfer.Assembler();
        var cursor=new DirectoryTransfer.Cursor(1,entries); int frames=0;
        List<Snapshot.DirectoryEntry> completed=null;
        while(!cursor.finished()) {
            var frame=wire(helper,cursor.next()); frames++;
            completed=assembler.accept(frame);
            helper.assertTrue(frame.last() || completed==null,"Incomplete refresh must not replace a visible generation");
        }
        helper.assertTrue(frames>1 && completed!=null && completed.get(0).cells().size()==600,
                "Oversized UTF8 display names must be shortened without dropping any child");
        helper.assertTrue(completed.get(0).cells().get(599).slot()==599 && completed.get(0).cells().get(599).usedBytes()==599,
                "Fragmented child indices and capacity must stay exact");
        var replacement=new DirectoryTransfer.Cursor(2,List.of());
        var cleared=assembler.accept(wire(helper,replacement.next()));
        helper.assertTrue(cleared!=null && cleared.isEmpty(),"Completed empty generation must remove stale devices");
        helper.assertTrue(assembler.accept(new DirectoryTransfer.Cursor(1,entries).next())==null,
                "Old in-flight generation must not resurrect removed devices");
        var sequence=new DirectoryTransfer.Cursor(3,entries);
        var first=sequence.next(); var second=sequence.next();
        boolean reorderedRejected=false;
        try { new DirectoryTransfer.Assembler().accept(second); }
        catch(IllegalArgumentException expected) { reorderedRejected=true; }
        helper.assertTrue(reorderedRejected,"Out-of-order frame must not create a partial directory");
        var duplicateCheck=new DirectoryTransfer.Assembler(); duplicateCheck.accept(first);
        boolean duplicateRejected=false;
        try { duplicateCheck.accept(first); }
        catch(IllegalArgumentException expected) { duplicateRejected=true; }
        helper.assertTrue(duplicateRejected,"Duplicate frame must not append duplicate children");
        var nextGeneration=new DirectoryTransfer.Cursor(4,List.of());
        var interruptionCheck=new DirectoryTransfer.Assembler(); interruptionCheck.accept(first);
        helper.assertTrue(interruptionCheck.accept(nextGeneration.next()).isEmpty()
                        && interruptionCheck.accept(second)==null,
                "Replacing an incomplete generation must discard its tail");
        helper.succeed();
    }

    private static List<Snapshot.DirectoryEntry> roundtrip(GameTestHelper helper,List<Snapshot.DirectoryEntry> entries,long generation) {
        var cursor=new DirectoryTransfer.Cursor(generation,entries); var assembler=new DirectoryTransfer.Assembler();
        List<Snapshot.DirectoryEntry> complete=null;
        while(!cursor.finished()) complete=assembler.accept(wire(helper,cursor.next()));
        helper.assertTrue(complete!=null,"Directory generation must complete");
        return complete;
    }
    private static DirectoryTransfer.Frame wire(GameTestHelper helper,DirectoryTransfer.Frame frame) {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            frame.write(buffer);
            helper.assertTrue(buffer.readableBytes()+5<=DirectoryTransfer.MAX_FRAME_BYTES,"Encoded frame including container id exceeded 48 KiB");
            var decoded=DirectoryTransfer.Frame.read(buffer);
            helper.assertTrue(!buffer.isReadable(),"Directory frame decoder left unread bytes");
            return decoded;
        } finally { buffer.release(); }
    }

    private static final class DirectoryHost implements IChestOrDrive {
        final int slots;
        final StorageCell inventory=StorageCells.getCellInventory(AEItems.ITEM_CELL_1K.stack(),null);
        final IManagedGridNode node;
        DirectoryHost(int slots) {
            this.slots=slots;
            inventory.insert(AEItemKey.of(Items.IRON_INGOT),1234,Actionable.MODULATE,IActionSource.empty());
            node=GridHelper.createManagedNode(this,(owner,gridNode)->{})
                    .setInWorldNode(false).setIdlePowerUsage(0).setVisualRepresentation(AEBlocks.DRIVE)
                    .addService(IStorageProvider.class,mounts->mounts.mount(inventory,0));
        }
        @Override public int getCellCount() { return slots; }
        @Override public CellState getCellStatus(int slot) { return slot==slots-1?CellState.NOT_EMPTY:CellState.ABSENT; }
        @Override public boolean isPowered() { return node.isPowered(); }
        @Override public boolean isCellBlinking(int slot) { return false; }
        @Override public Item getCellItem(int slot) { return slot==slots-1?AEItems.ITEM_CELL_1K.asItem():null; }
        @Override public MEStorage getCellInventory(int slot) { return slot==slots-1?inventory:null; }
        @Override public StorageCell getOriginalCellInventory(int slot) { return slot==slots-1?inventory:null; }
        @Override public IGridNode getActionableNode() { return node.getNode(); }
    }
}
