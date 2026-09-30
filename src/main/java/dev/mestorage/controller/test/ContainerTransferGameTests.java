package dev.mestorage.controller.test;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real AE fluid cells and vanilla bucket capabilities exercise the registered AE container strategy. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ContainerTransferGameTests {
    private static final BlockPos POS=new BlockPos(2,2,2);
    private static final AEFluidKey WATER=AEFluidKey.of(Fluids.WATER);
    private static final AEItemKey BUCKET=AEItemKey.of(Items.BUCKET);
    private record Fixture(ControllerBlockEntity controller,DriveBlockEntity drive,Player player,ControllerMenu menu) {}

    @GameTest(template="empty",timeoutTicks=300)
    public static void bucketClicksRespectNativeButtonsAndSelectedCell(GameTestHelper h) {
        var f=fixture(h);
        h.startSequence().thenWaitUntil(()->ready(h,f)).thenExecute(()->{
            insert(f,0,WATER,2500);insert(f,1,WATER,7000);select(f,false,0,1);
            f.menu().setCarried(new ItemStack(Items.BUCKET));action(f,WATER,0,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,0,WATER)==1500,"Left empty bucket must take exactly 1000mB");
            action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==2500,"Right full bucket must return exactly 1000mB");
            action(f,WATER,1,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==2500,"Right empty bucket must not fill");
            action(f,WATER,0,true);
            check(h,f.menu().getCarried().isEmpty()&&playerCount(f,Items.WATER_BUCKET)==1&&amount(f,0,WATER)==1500,"Shift-left must fill and move bucket to backpack");
            f.menu().clicked(37,0,ClickType.PICKUP,f.player());
            action(f,null,1,true);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,0,WATER)==1500,"Shift-right must not empty fluid containers");
            action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==2500&&amount(f,1,WATER)==7000,"Bucket round trip must preserve both selected and neighboring fluid cells");
            check(h,playerCount(f,Items.BUCKET)==0&&playerCount(f,Items.WATER_BUCKET)==0,"Round trip must leave only the carried empty bucket");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void subBucketSourceAndPartialDestinationPreserveContainers(GameTestHelper h) {
        var f=fixture(h);long[] nearFull={0};
        h.startSequence().thenWaitUntil(()->ready(h,f)).thenExecute(()->{
            insert(f,0,WATER,999);insert(f,1,WATER,5000);select(f,false,0,1);
            f.menu().setCarried(new ItemStack(Items.BUCKET));action(f,WATER,0,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==999&&amount(f,1,WATER)==5000,"999mB selected cell cannot borrow missing water from neighbor or consume bucket");
            insert(f,0,WATER,Long.MAX_VALUE);
            f.drive().getCellInventory(0).extract(WATER,500,Actionable.MODULATE,IActionSource.empty());
            nearFull[0]=amount(f,0,WATER);
            f.menu().setCarried(new ItemStack(Items.WATER_BUCKET));action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,0,WATER)==nearFull[0],"Only 500mB free capacity must reject an indivisible water bucket without loss");
            insert(f,0,WATER,500);action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,0,WATER)==nearFull[0]+500,"Completely full cell must preserve carried full bucket");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void networkBorrowedBucketsReturnOnFailureAndFullBackpackKeepsResult(GameTestHelper h) {
        var f=fixture(h);
        h.startSequence().thenWaitUntil(()->ready(h,f)).thenExecute(()->{
            insert(f,0,WATER,999);insert(f,2,BUCKET,2);select(f,true,-1,1);
            action(f,WATER,0,false);
            check(h,f.menu().getCarried().isEmpty()&&amount(f,2,BUCKET)==2&&amount(f,0,WATER)==999,"Auto-borrowed bucket must be returned when fill is impossible");
            insert(f,0,WATER,1);
        }).thenIdle(5).thenExecute(()->{
            select(f,true,-1,2);action(f,WATER,0,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,2,BUCKET)==1&&amount(f,0,WATER)==0,"Empty cursor must borrow one actual network bucket and fill it");
            action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==1000,"Right emptying must retain the empty bucket");
            action(f,null,0,false);
            check(h,f.menu().getCarried().isEmpty()&&amount(f,2,BUCKET)==2,"Ordinary left on empty grid must store the empty bucket item");
            for(int i=0;i<36;i++)f.player().getInventory().setItem(i,new ItemStack(Items.STONE,64));
        }).thenIdle(5).thenExecute(()->{
            select(f,true,-1,3);action(f,WATER,0,true);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,2,BUCKET)==1&&amount(f,0,WATER)==0,"Full backpack Shift-fill must keep result on cursor, never discard it");
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(f.controller().getBlockPos()).inflate(4));
            check(h,drops.isEmpty(),"Container transfer must not drop items when backpack is full");
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=300)
    public static void finitePowerBucketsRemainAtomicAndConsumeEnergy(GameTestHelper h) {
        var f=fixture(h,true);
        h.startSequence().thenWaitUntil(()->ready(h,f)).thenExecute(()->{
            insert(f,0,WATER,2500);insert(f,1,WATER,7000);select(f,false,0,1);
            var energy=f.controller().getMainNode().getGrid().getEnergyService();
            var cell=(EnergyCellBlockEntity)h.getBlockEntity(POS.below());
            var storage=f.drive().getCellInventory(0);var source=IActionSource.ofPlayer(f.player());
            energy.extractAEPower(1000000,Actionable.MODULATE,PowerMultiplier.ONE);
            double halfBucketPower=PowerMultiplier.CONFIG.multiply(500.0/Math.max(1.0,WATER.getAmountPerOperation()));
            cell.injectAEPower(halfBucketPower,Actionable.MODULATE);
            long extractable=StorageHelper.poweredExtraction(energy,storage,WATER,1000,source,Actionable.SIMULATE);
            long insertable=StorageHelper.poweredInsert(energy,storage,WATER,1000,source,Actionable.SIMULATE);
            check(h,extractable>0&&extractable<1000&&insertable>0&&insertable<1000,"Real energy cell must permit some fluid but less than one bucket in both directions");
            double limited=energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE);
            f.menu().setCarried(new ItemStack(Items.BUCKET));action(f,WATER,0,false);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&f.menu().getCarried().getCount()==1&&amount(f,0,WATER)==2500,"Insufficient bucket energy must not partially extract fluid or change empty bucket");
            f.menu().setCarried(new ItemStack(Items.WATER_BUCKET));action(f,null,1,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&f.menu().getCarried().getCount()==1&&amount(f,0,WATER)==2500,"Insufficient bucket energy must not partially insert fluid or change full bucket");
            check(h,Math.abs(energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE)-limited)<0.000001,"Rejected indivisible bucket operations must not consume transfer energy");
            cell.injectAEPower(10000,Actionable.MODULATE);
            double charged=energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE);
            action(f,null,1,false);
            double afterInsert=energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE);
            check(h,f.menu().getCarried().is(Items.BUCKET)&&amount(f,0,WATER)==3500&&afterInsert<charged,"Charged bucket insertion must transfer 1000mB and consume real AE power");
            action(f,WATER,0,false);
            check(h,f.menu().getCarried().is(Items.WATER_BUCKET)&&amount(f,0,WATER)==2500&&energy.extractAEPower(1000000,Actionable.SIMULATE,PowerMultiplier.ONE)<afterInsert,"Charged bucket extraction must transfer 1000mB and consume real AE power");
            check(h,amount(f,1,WATER)==7000&&playerCount(f,Items.BUCKET)==0&&playerCount(f,Items.WATER_BUCKET)==0,"Finite-power operations must preserve neighboring cell and bucket uniqueness");
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper h) {return fixture(h,false);}
    private static Fixture fixture(GameTestHelper h,boolean finitePower) {
        h.setBlock(POS.below(),finitePower?AEBlocks.ENERGY_CELL.block():AEBlocks.CREATIVE_ENERGY_CELL.block());
        if(finitePower)((EnergyCellBlockEntity)h.getBlockEntity(POS.below())).injectAEPower(10000,Actionable.MODULATE);
        h.setBlock(POS,MEStorageController.CONTROLLER.get());h.setBlock(POS.east(),AEBlocks.DRIVE.block());
        var c=(ControllerBlockEntity)h.getBlockEntity(POS);var d=(DriveBlockEntity)h.getBlockEntity(POS.east());
        d.getInternalInventory().setItemDirect(0,AEItems.FLUID_CELL_1K.stack());d.getInternalInventory().setItemDirect(1,AEItems.FLUID_CELL_1K.stack());d.getInternalInventory().setItemDirect(2,AEItems.ITEM_CELL_1K.stack());
        var p=h.makeMockSurvivalPlayer();var pos=h.absolutePos(POS);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        var m=new ControllerMenu(31,p.getInventory(),c);p.containerMenu=m;return new Fixture(c,d,p,m);
    }
    private static void ready(GameTestHelper h,Fixture f){check(h,f.controller().getMainNode().isActive()&&f.drive().getMainNode().isActive(),"Fluid fixture must boot");}
    private static void insert(Fixture f,int slot,AEKey key,long amount){f.drive().getCellInventory(slot).insert(key,amount,Actionable.MODULATE,IActionSource.empty());}
    private static long amount(Fixture f,int slot,AEKey key){return StorageScanner.contents(f.drive().getCellInventory(slot)).get(key);}
    private static long playerCount(Fixture f,net.minecraft.world.item.Item item){long n=0;for(int i=0;i<36;i++){var s=f.player().getInventory().getItem(i);if(s.is(item))n+=s.getCount();}return n;}
    private static void select(Fixture f,boolean network,int cell,long rev){var id=network?"":StorageScanner.discover(f.controller().getMainNode().getGrid()).stream().filter(d->d.owner()==f.drive()).findFirst().orElseThrow().id();f.menu().handleRequest(new Network.Request(f.menu().containerId,id,cell,0,0,"","",true,List.of(),List.of(),rev));f.menu().broadcastChanges();}
    private static void action(Fixture f,AEKey key,int button,boolean shift){var s=f.menu().getSnapshot();f.menu().handleContentAction(new Network.ContentAction(f.menu().containerId,s.revision(),s.selectedDevice(),s.selectedCell(),key,button,shift));}
    private static void check(GameTestHelper h,boolean value,String message){h.assertTrue(value,message);}
}
