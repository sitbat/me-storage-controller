package dev.mestorage.controller.test;

import java.util.List;
import java.util.Comparator;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.storage.StorageScanner;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ContentGridGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void rapidPagingAcknowledgesLatestTargetWithoutMixingSearchOrScope(GameTestHelper helper) {
        var pos=new BlockPos(2,2,2);
        helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos,MEStorageController.CONTROLLER.get());helper.setBlock(pos.east(),AEBlocks.DRIVE.block());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(pos);var drive=(DriveBlockEntity)helper.getBlockEntity(pos.east());
        for(int i=0;i<4;i++)drive.getInternalInventory().setItemDirect(i,AEItems.ITEM_CELL_64K.stack());
        var items=BuiltInRegistries.ITEM.stream().filter(item->item instanceof BlockItem
                && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .sorted(Comparator.comparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).limit(180).toList();
        var player=helper.makeMockSurvivalPlayer();var absolute=helper.absolutePos(pos);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new ControllerMenu(39,player.getInventory(),controller);player.containerMenu=menu;
        var deviceId=new String[1];long[] pagingRevision={9};
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive()&&drive.getMainNode().isActive(),"Paging fixture must boot"))
            .thenExecute(()->{
                helper.assertTrue(items.size()==180,"Paging fixture needs four complete pages");
                for(int i=0;i<items.size();i++)helper.assertTrue(drive.getCellInventory(i/45).insert(AEItemKey.of(items.get(i)),1000+i,
                        Actionable.MODULATE,IActionSource.empty())==1000+i,"Seed each real cell without exceeding its type limit");
                deviceId[0]=StorageScanner.discover(controller.getMainNode().getGrid()).stream().filter(d->d.owner()==drive).findFirst().orElseThrow().id();
                page(menu,deviceId[0],-1,0,"",1);menu.broadcastChanges();
                helper.assertTrue(menu.getSnapshot().contentPages()==4&&menu.getSnapshot().contentCount()==180,"Whole-device fixture must contain four pages");
            }).thenIdle(1).thenExecute(()->{
                page(menu,deviceId[0],-1,1,"",2);page(menu,deviceId[0],-1,2,"",3);page(menu,deviceId[0],-1,3,"",4);menu.broadcastChanges();
                var s=menu.getSnapshot();
                helper.assertTrue(s.revision()==4&&s.contentPage()==3&&s.contents().get(0).amount()==1044,"Rapid requests must acknowledge latest target after one tick, without the old five-tick delay");
                assertWire(helper,s,45);
            }).thenIdle(1).thenExecute(()->{
                page(menu,deviceId[0],-1,2,"",5);menu.broadcastChanges();
                var s=menu.getSnapshot();helper.assertTrue(s.revision()==5&&s.contentPage()==2&&s.contents().get(0).amount()==1089,"Reverse paging must respond on the next tick");
                page(menu,deviceId[0],-1,3,"",6);
                menu.handleContentAction(new Network.ContentAction(menu.containerId,s.revision(),s.selectedDevice(),s.selectedCell(),s.contents().get(0).key(),0,false));
                helper.assertTrue(menu.getCarried().isEmpty(),"A pending page must not transfer an item from the old view");
            }).thenIdle(1).thenExecute(()->{
                page(menu,deviceId[0],-1,0,"",7);menu.broadcastChanges();
                page(menu,deviceId[0],-1,3,"",6);menu.broadcastChanges();
                helper.assertTrue(menu.getSnapshot().revision()==7&&menu.getSnapshot().contentPage()==0,"Latest reverse target must supersede pending and stale requests");
            }).thenIdle(5).thenExecute(()->{
                var search=BuiltInRegistries.ITEM.getKey(items.get(10)).toString();page(menu,deviceId[0],-1,99,search,8);menu.broadcastChanges();
                var s=menu.getSnapshot();helper.assertTrue(s.revision()==8&&s.contents().stream().allMatch(c->c.key().getId().toString().contains(search))
                        &&s.contents().stream().anyMatch(c->c.key().equals(AEItemKey.of(items.get(10))))&&s.contentPage()<s.contentPages(),"Search must filter the full content list before clamping a page");
            }).thenIdle(1).thenExecute(()->{
                page(menu,deviceId[0],1,99,"",9);menu.broadcastChanges();var s=menu.getSnapshot();
                helper.assertTrue(s.revision()==9&&s.selectedCell()==1&&s.contentCount()==45&&s.contentPage()==0
                        &&s.contents().get(0).amount()==1089&&s.contents().get(44).amount()==1045,"Changing scope must not reuse the previous device/search page cache");
                for(int i=0;i<180;i++)helper.assertTrue(StorageScanner.contents(drive.getCellInventory(i/45)).get(AEItemKey.of(items.get(i)))==1000+i,"Paging must preserve every real item count");
                menu.setCarried(AEItemKey.of(items.get(45)).toStack(7));
                menu.handleContentAction(new Network.ContentAction(menu.containerId,s.revision(),s.selectedDevice(),s.selectedCell(),null,0,false));
                helper.assertTrue(menu.getCarried().isEmpty(),"Mutation refresh fixture must insert seven real items");
            }).thenExecuteFor(8,()->{page(menu,deviceId[0],1,0,"",++pagingRevision[0]);menu.broadcastChanges();})
            .thenExecute(()->{
                helper.assertTrue(menu.getSnapshot().contents().stream().anyMatch(c->c.key().equals(AEItemKey.of(items.get(45)))&&c.amount()==1052),"Continuous paging must not postpone the five-tick mutation refresh");
                drive.getCellInventory(1).insert(AEItemKey.of(items.get(45)),6,Actionable.MODULATE,IActionSource.empty());
            }).thenExecuteFor(22,()->{page(menu,deviceId[0],1,0,"",++pagingRevision[0]);menu.broadcastChanges();})
            .thenExecute(()->helper.assertTrue(menu.getSnapshot().contents().stream().anyMatch(c->c.key().equals(AEItemKey.of(items.get(45)))&&c.amount()==1058),"Continuous paging must not starve periodic refresh of external storage changes"))
            .thenSucceed();
    }
    private static void page(ControllerMenu menu,String id,int cell,int page,String search,long revision) {
        menu.handleRequest(new Network.Request(menu.containerId,id,cell,0,page,"",search,true,List.of(),List.of(),revision));
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void fortyFiveCellPagePreservesEveryKeyAndBoundsRequests(GameTestHelper helper) {
        var pos=new BlockPos(2,2,2);
        helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos,MEStorageController.CONTROLLER.get());
        helper.setBlock(pos.east(),AEBlocks.DRIVE.block());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(pos);
        var drive=(DriveBlockEntity)helper.getBlockEntity(pos.east());
        drive.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_64K.stack());
        var items=BuiltInRegistries.ITEM.stream().filter(item->item instanceof BlockItem
                && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .sorted(Comparator.comparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).limit(52).toList();
        var player=helper.makeMockSurvivalPlayer();var absolute=helper.absolutePos(pos);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new ControllerMenu(9,player.getInventory(),controller);
        var deviceId=new String[1];
        helper.startSequence()
            .thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive()&&drive.getMainNode().isActive(),"Grid fixture did not boot"))
            .thenExecute(()->{
                for(int i=0;i<items.size();i++) helper.assertTrue(drive.getCellInventory(0).insert(AEItemKey.of(items.get(i)),1000+i,
                        Actionable.MODULATE,IActionSource.empty())==1000+i,"Could not populate all 52 distinct live keys");
                deviceId[0]=StorageScanner.discover(controller.getMainNode().getGrid()).stream().filter(d->d.owner()==drive).findFirst().orElseThrow().id();
                menu.handleRequest(request(menu,deviceId[0],0,"",1));menu.broadcastChanges();
                var first=menu.getSnapshot();
                helper.assertTrue(first.contentCount()==52&&first.contentPages()==2&&first.contents().size()==45,"First grid page must contain 45 of 52 keys");
                helper.assertTrue(first.contents().get(0).amount()==1051&&first.contents().get(44).amount()==1007,"Amount order or first-page boundary incorrect");
                assertWire(helper,first,45);
            })
            .thenIdle(5)
            .thenExecute(()->{
                menu.handleRequest(request(menu,deviceId[0],Integer.MAX_VALUE,"",2));menu.broadcastChanges();
                var last=menu.getSnapshot();
                helper.assertTrue(last.contentPage()==1&&last.contents().size()==7,"Oversized page request did not clamp to the final seven keys");
                helper.assertTrue(last.contents().get(0).amount()==1006&&last.contents().get(6).amount()==1000,"Page boundary duplicated or omitted a key");
                assertWire(helper,last,7);
            })
            .thenIdle(5)
            .thenExecute(()->{
                menu.handleRequest(request(menu,deviceId[0],0,BuiltInRegistries.ITEM.getKey(items.get(10)).toString(),3));menu.broadcastChanges();
                var filtered=menu.getSnapshot();
                helper.assertTrue(filtered.contentCount()==1&&filtered.contentPages()==1&&filtered.contents().get(0).key().equals(AEItemKey.of(items.get(10)))
                        &&filtered.contents().get(0).amount()==1010,"Search did not filter the full grid before pagination");
                helper.assertTrue(StorageScanner.contents(drive.getCellInventory(0)).size()==52,"Read-only paging/search changed the cell contents");
            }).thenSucceed();
    }
    private static Network.Request request(ControllerMenu menu,String id,int page,String search,long revision) {
        return new Network.Request(menu.containerId,id,0,0,page,"",search,true,List.of(),List.of(),revision);
    }
    private static void assertWire(GameTestHelper helper,Snapshot snapshot,int expected) {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.write(buffer);var decoded=Snapshot.read(buffer);
            helper.assertTrue(decoded.contents().equals(snapshot.contents())&&decoded.contents().size()==expected&&buffer.readableBytes()==0,
                    "45-key grid snapshot failed protocol round-trip");
        } finally {buffer.release();}
    }
}
