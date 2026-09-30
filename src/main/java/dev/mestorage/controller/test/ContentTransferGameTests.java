package dev.mestorage.controller.test;

import java.util.List;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.storagebus.StorageBusPart;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.storage.ContentAccess;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real powered storage and physical player inventory; no replacement storage implementations. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ContentTransferGameTests {
    private static final BlockPos POS=new BlockPos(2,2,2);
    private static final AEItemKey IRON=AEItemKey.of(Items.IRON_INGOT);
    private record Fixture(ControllerBlockEntity controller,DriveBlockEntity drive,DriveBlockEntity other,Player player,ControllerMenu menu) {}

    @GameTest(template="empty",timeoutTicks=300)
    public static void cellTransfersConserveAndRejectStaleOrDeniedActions(GameTestHelper helper) {
        var f=fixture(helper,false);
        helper.startSequence().thenWaitUntil(()->ready(helper,f)).thenExecute(()->{
            populate(f); select(f,id(f,f.drive()),0,1);
            action(f,IRON,0,false); check(helper,f.menu().getCarried().getCount()==64&&count(f.drive(),0)==936,"Left must extract exactly one stack from selected cell");
            action(f,null,0,false); check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"Empty grid space must insert held stack");
            action(f,IRON,1,false); check(helper,f.menu().getCarried().getCount()==32,"Right must take half a stack");
            action(f,null,1,false); check(helper,f.menu().getCarried().getCount()==31&&count(f.drive(),0)==969,"Right with held stack must insert one");
            action(f,null,0,false);
            for(int i=0;i<24;i++) { action(f,IRON,0,false); action(f,IRON,0,false); }
            check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"48 rapid actions must not coalesce, duplicate or lose items");
            f.player().getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,63));
            action(f,IRON,0,true);
            check(helper,count(f.drive(),0)==936&&f.player().getInventory().getItem(0).getCount()==64
                    &&f.player().getInventory().getItem(1).getCount()==63,"Shift must merge a partial stack and use free space for the rest of one stack");
            f.menu().clicked(37,0,ClickType.QUICK_MOVE,f.player());
            check(helper,count(f.drive(),0)==1000&&f.player().getInventory().getItem(0).isEmpty(),"Shift player items must deposit into selected content scope");
            f.player().getAbilities().mayBuild=false; action(f,IRON,0,false);
            check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"Adventure/build restriction must deny extraction");
            f.player().getAbilities().mayBuild=true;
            var guard=new DenyInteraction(f.player(),f.drive().getBlockPos()); MinecraftForge.EVENT_BUS.register(guard);
            try { action(f,IRON,0,false); } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
            check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"Forge protection must deny selected-device access");
            var s=f.menu().getSnapshot();
            f.menu().handleContentAction(new Network.ContentAction(f.menu().containerId,0,s.selectedDevice(),0,IRON,0,false));
            f.menu().handleContentAction(new Network.ContentAction(f.menu().containerId,s.revision(),"",-1,IRON,0,false));
            f.menu().handleContentAction(new Network.ContentAction(f.menu().containerId,s.revision(),s.selectedDevice(),1,IRON,0,false));
            action(f,AEItemKey.of(Items.DIAMOND),0,false);
            check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"Stale revision, changed scope and undisplayed key must be rejected");
            check(helper,count(f.drive(),1)==200&&count(f.other(),0)==2000,"Cell scope must not alter another cell or device");
            // Replacing the physical cell must invalidate the previous detail view immediately.
            f.drive().getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_1K.stack());
            f.menu().setCarried(new ItemStack(Items.IRON_INGOT,7)); action(f,null,0,false);
            check(helper,f.menu().getCarried().getCount()==7&&count(f.drive(),0)==0,"Same-slot replacement must not accept a stale cell action");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void deviceAndNetworkTransfersRefreshGlobalCache(GameTestHelper helper) {
        var f=fixture(helper,false);
        helper.startSequence().thenWaitUntil(()->ready(helper,f)).thenExecute(()->{
            populate(f); select(f,id(f,f.drive()),-1,1);
            check(helper,StorageScanner.gridContents(f.controller().getMainNode().getGrid()).get(IRON)==3200,"Fixture cache baseline incorrect");
            action(f,IRON,0,false);
            check(helper,count(f.drive(),0)+count(f.drive(),1)==1136&&count(f.other(),0)==2000,"Device scope crossed into another drive");
        }).thenIdle(2).thenExecute(()->{
            check(helper,StorageScanner.gridContents(f.controller().getMainNode().getGrid()).get(IRON)==3136,"Scoped mutation must become visible in network cache on next tick");
            action(f,null,0,false);
        }).thenIdle(5).thenExecute(()->{
            select(f,"",-1,2); action(f,IRON,0,false);
            check(helper,f.menu().getCarried().getCount()==64&&total(f)==3136,"Network action must use actual grid inventory");
            action(f,null,0,false);
            for(int i=0;i<36;i++) f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
            action(f,IRON,0,true);
            check(helper,total(f)==3200&&f.menu().getCarried().isEmpty(),"Full backpack must not extract or drop items");
            f.menu().handleRequest(new Network.Request(f.menu().containerId,id(f,f.drive()),0,0,0,"","",true,List.of(),List.of(),3));
            action(f,IRON,0,false);
            check(helper,total(f)==3200&&f.menu().getCarried().isEmpty(),"Pending navigation must reject old-scope mutation");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void busTransfersRespectFiltersAccessAndTargetProtection(GameTestHelper helper) {
        var f=fixture(helper,false);
        var busPos=POS.north();var target=busPos.north();
        helper.setBlock(target,Blocks.BARREL);
        var barrel=(BarrelBlockEntity)helper.getBlockEntity(target);
        barrel.setItem(0,new ItemStack(Items.IRON_INGOT,50));barrel.setItem(1,new ItemStack(Items.GOLD_INGOT,9));
        PartHelper.setPart(helper.getLevel(),helper.absolutePos(busPos),null,null,AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus=(StorageBusPart)PartHelper.setPart(helper.getLevel(),helper.absolutePos(busPos),Direction.NORTH,null,AEParts.STORAGE_BUS.asItem());
        bus.getConfig().setStack(0,new GenericStack(IRON,1));
        bus.getConfigManager().putSetting(Settings.ACCESS,AccessRestriction.READ);
        helper.startSequence().thenWaitUntil(()->{
            ready(helper,f); check(helper,bus.getMainNode().isActive(),"Bus must be online");
            check(helper,StorageScanner.contents(device(f,bus).storage()).get(IRON)==50,"Bus must mount its filtered target");
        }).thenExecute(()->{
            select(f,id(f,bus),-1,1);
            check(helper,f.menu().getSnapshot().contents().size()==1,"Bus detail must exclude filtered gold");
            action(f,IRON,1,false); check(helper,barrel.getItem(0).getCount()==25&&f.menu().getCarried().getCount()==25,"Bus extraction must affect only its real target");
            action(f,null,0,false); check(helper,barrel.getItem(0).getCount()==25&&f.menu().getCarried().getCount()==25,"Read-only storage bus must refuse insertion");
            bus.getConfigManager().putSetting(Settings.ACCESS,AccessRestriction.READ_WRITE);
        }).thenIdle(5).thenExecute(()->{
            var guard=new DenyInteraction(f.player(),helper.absolutePos(target));MinecraftForge.EVENT_BUS.register(guard);
            try { action(f,null,0,false); } finally { MinecraftForge.EVENT_BUS.unregister(guard); }
            check(helper,f.menu().getCarried().getCount()==25&&barrel.getItem(0).getCount()==25,"Target-container protection must block bus insertion");
            action(f,null,0,false);check(helper,f.menu().getCarried().isEmpty()&&barrel.getItem(0).getCount()==50,"Writable bus must accept permitted iron");
            f.menu().setCarried(new ItemStack(Items.GOLD_INGOT,7));action(f,null,0,false);
            check(helper,f.menu().getCarried().getCount()==7&&barrel.getItem(1).getCount()==9,"Bus whitelist must reject held gold without bypassing wrapper");
            bus.getConfigManager().putSetting(Settings.ACCESS,AccessRestriction.WRITE);
        }).thenIdle(5).thenExecute(()->{
            var storage=ContentAccess.resolve(f.controller().getMainNode().getGrid(),device(f,bus),-1);
            check(helper,storage!=null&&storage.extract(IRON,64,Actionable.SIMULATE,IActionSource.ofPlayer(f.player()))==0,"Write-only mounted bus wrapper must refuse extraction");
            f.menu().setCarried(ItemStack.EMPTY);action(f,IRON,0,false);
            check(helper,f.menu().getCarried().isEmpty()&&barrel.getItem(0).getCount()==50,"Content action must retain write-only extraction restriction");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void contentTransfersConsumeEnergyAndStopWhenExhausted(GameTestHelper helper) {
        var f=fixture(helper,true);
        helper.startSequence().thenWaitUntil(()->ready(helper,f)).thenExecute(()->{
            populate(f);select(f,id(f,f.drive()),0,1);
            var energy=f.controller().getMainNode().getGrid().getEnergyService();
            double before=energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE);
            action(f,IRON,0,false);
            double after=energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE);
            check(helper,f.menu().getCarried().getCount()==64&&before>after,"Extraction must consume actual AE energy");
            action(f,null,0,false);
            check(helper,energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE)<after,"Insertion must consume actual AE energy");
            energy.extractAEPower(1000000,Actionable.MODULATE,PowerMultiplier.ONE);
            action(f,IRON,0,false);
            check(helper,f.menu().getCarried().isEmpty()&&count(f.drive(),0)==1000,"Exhausted power must prevent free extraction even before node state catches up");
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper helper,boolean finitePower) {
        helper.setBlock(POS.below(),finitePower ? AEBlocks.ENERGY_CELL.block() : AEBlocks.CREATIVE_ENERGY_CELL.block());
        if(finitePower) ((EnergyCellBlockEntity)helper.getBlockEntity(POS.below())).injectAEPower(10000,Actionable.MODULATE);
        helper.setBlock(POS,MEStorageController.CONTROLLER.get());
        helper.setBlock(POS.east(),AEBlocks.DRIVE.block());helper.setBlock(POS.west(),AEBlocks.DRIVE.block());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(POS);
        var drive=(DriveBlockEntity)helper.getBlockEntity(POS.east());var other=(DriveBlockEntity)helper.getBlockEntity(POS.west());
        drive.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_64K.stack());
        drive.getInternalInventory().setItemDirect(1,AEItems.ITEM_CELL_64K.stack());
        other.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_64K.stack());
        var player=helper.makeMockSurvivalPlayer();var absolute=helper.absolutePos(POS);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        return new Fixture(controller,drive,other,player,new ControllerMenu(19,player.getInventory(),controller));
    }
    private static void ready(GameTestHelper helper,Fixture f) {
        check(helper,f.controller().getMainNode().isActive()&&f.drive().getMainNode().isActive()&&f.other().getMainNode().isActive(),"Transfer fixture must boot");
    }
    private static void populate(Fixture f) {
        f.drive().getCellInventory(0).insert(IRON,1000,Actionable.MODULATE,IActionSource.empty());
        f.drive().getCellInventory(1).insert(IRON,200,Actionable.MODULATE,IActionSource.empty());
        f.other().getCellInventory(0).insert(IRON,2000,Actionable.MODULATE,IActionSource.empty());
    }
    private static long count(DriveBlockEntity drive,int slot) {return StorageScanner.contents(drive.getCellInventory(slot)).get(IRON);}
    private static long total(Fixture f) {return count(f.drive(),0)+count(f.drive(),1)+count(f.other(),0);}
    private static StorageScanner.Device device(Fixture f,Object owner) {return StorageScanner.discover(f.controller().getMainNode().getGrid()).stream().filter(d->d.owner()==owner).findFirst().orElseThrow();}
    private static String id(Fixture f,Object owner) {return device(f,owner).id();}
    private static void select(Fixture f,String id,int cell,long revision) {
        f.menu().handleRequest(new Network.Request(f.menu().containerId,id,cell,0,0,"","",true,List.of(),List.of(),revision));f.menu().broadcastChanges();
    }
    private static void action(Fixture f,appeng.api.stacks.AEKey key,int button,boolean shift) {
        var s=f.menu().getSnapshot();f.menu().handleContentAction(new Network.ContentAction(f.menu().containerId,s.revision(),s.selectedDevice(),s.selectedCell(),key,button,shift));
    }
    private static void check(GameTestHelper helper,boolean condition,String message) {helper.assertTrue(condition,message);}
    public static final class DenyInteraction {
        private final Player player;private final BlockPos pos;
        DenyInteraction(Player player,BlockPos pos) {this.player=player;this.pos=pos;}
        @SubscribeEvent public void deny(PlayerInteractEvent.RightClickBlock event) {
            if(event.getEntity()==player&&event.getPos().equals(pos)) event.setCanceled(true);
        }
    }
}
