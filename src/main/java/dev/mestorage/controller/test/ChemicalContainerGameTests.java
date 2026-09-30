package dev.mestorage.controller.test;

import java.util.List;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Real optional addon integration through registry IDs and AE's public API, without a hard dependency. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ChemicalContainerGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void realMekanismContainersTransferAllFourChemicalForms(GameTestHelper helper) {
        if(!ModList.get().isLoaded("appmek") || !ModList.get().isLoaded("mekanism")) {
            org.slf4j.LoggerFactory.getLogger(ChemicalContainerGameTests.class).info("ME_STORAGE_CHEMICAL_TEST SKIP: use -PmekTest");
            helper.succeed(); return;
        }
        var pos=new BlockPos(2,2,2);
        helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos,MEStorageController.CONTROLLER.get());
        helper.setBlock(pos.east(),AEBlocks.DRIVE.block());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(pos);
        var drive=(DriveBlockEntity)helper.getBlockEntity(pos.east());
        var cell=ForgeRegistries.ITEMS.getValue(new ResourceLocation("appmek","chemical_storage_cell_64k"));
        var tank=ForgeRegistries.ITEMS.getValue(new ResourceLocation("mekanism","basic_chemical_tank"));
        helper.assertTrue(cell!=null && cell!=Items.AIR && tank!=null && tank!=Items.AIR,"Real chemical cell and tank must be registered");
        drive.getInternalInventory().setItemDirect(0,new ItemStack(cell));
        drive.getInternalInventory().setItemDirect(1,new ItemStack(cell));
        var player=helper.makeMockSurvivalPlayer(); var absolute=helper.absolutePos(pos);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new ControllerMenu(29,player.getInventory(),controller);
        player.containerMenu=menu;
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive() && drive.getMainNode().isActive(),"Chemical fixture must boot"))
            .thenExecute(()->{
                String[] fields={"gasName","infuseTypeName","pigmentName","slurryName"};
                String[] names={"hydrogen","redstone","red","dirty_iron"};
                AEKey[] keys=new AEKey[4];
                for(int form=0;form<4;form++) {
                    var tag=new CompoundTag();tag.putString("#c","appmek:chemical");tag.putByte("t",(byte)form);
                    tag.putString(fields[form],"mekanism:"+names[form]);tag.putLong("amount",1);
                    keys[form]=AEKey.fromTagGeneric(tag);
                    helper.assertTrue(keys[form]!=null && ContainerItemStrategies.isKeySupported(keys[form]),"Addon must expose a real chemical key and strategy: "+names[form]);
                    helper.assertTrue(drive.getCellInventory(0).insert(keys[form],16000,Actionable.MODULATE,IActionSource.empty())==16000,"Seed selected chemical cell");
                    drive.getCellInventory(1).insert(keys[form],500,Actionable.MODULATE,IActionSource.empty());
                }
                var device=StorageScanner.discover(controller.getMainNode().getGrid()).stream().filter(d->d.owner()==drive).findFirst().orElseThrow();
                menu.handleRequest(new Network.Request(menu.containerId,device.id(),0,0,0,"","",true,List.of(),List.of(),1));menu.broadcastChanges();
                for(var key:keys) {
                    menu.setCarried(new ItemStack(tank));
                    for(int repeat=0;repeat<10;repeat++) {
                        click(menu,key,0,false);
                        var held=ContainerItemStrategies.getContainedStack(menu.getCarried());
                        // Mekanism's basic tank capability limits each operation to 1000, even with more free capacity.
                        helper.assertTrue(held!=null && held.what().equals(key) && held.amount()==1000,"Left click must respect real basic-tank transfer rate: "+key.getId()+" held="+held);
                        helper.assertTrue(StorageScanner.contents(drive.getCellInventory(0)).get(key)==15000,"Filling must debit selected cell by the tank's exact accepted amount");
                        click(menu,null,1,false);
                        helper.assertTrue(ContainerItemStrategies.getContainedStack(menu.getCarried())==null,"Right click on empty tile must empty chemical container");
                        helper.assertTrue(StorageScanner.contents(drive.getCellInventory(0)).get(key)==16000,"Chemical roundtrip must conserve exact amount");
                    }
                    helper.assertTrue(StorageScanner.contents(drive.getCellInventory(1)).get(key)==500,"Chemical action must not touch a different cell");
                }
                var hydrogen=keys[0];
                menu.setCarried(new ItemStack(Items.BUCKET));click(menu,hydrogen,0,false);
                helper.assertTrue(menu.getCarried().is(Items.BUCKET) && StorageScanner.contents(drive.getCellInventory(0)).get(hydrogen)==16000,"Unsupported container must leave chemicals untouched");
                menu.setCarried(new ItemStack(tank));player.getAbilities().mayBuild=false;click(menu,hydrogen,0,false);player.getAbilities().mayBuild=true;
                helper.assertTrue(ContainerItemStrategies.getContainedStack(menu.getCarried())==null && StorageScanner.contents(drive.getCellInventory(0)).get(hydrogen)==16000,"Build protection must deny chemical filling");
                click(menu,hydrogen,0,true);
                helper.assertTrue(menu.getCarried().isEmpty(),"Shift filling should move the filled tank to the backpack");
                var filled=player.getInventory().getItem(0);var held=ContainerItemStrategies.getContainedStack(filled);
                helper.assertTrue(held!=null && held.what().equals(hydrogen) && held.amount()==1000 && StorageScanner.contents(drive.getCellInventory(0)).get(hydrogen)==15000,"Shift fill must preserve real chemical data and the basic tank transfer rate");
                menu.setCarried(filled);player.getInventory().setItem(0,ItemStack.EMPTY);click(menu,null,1,false);
                helper.assertTrue(StorageScanner.contents(drive.getCellInventory(0)).get(hydrogen)==16000,"Shift-filled tank must return its contents exactly");
                org.slf4j.LoggerFactory.getLogger(ChemicalContainerGameTests.class).info("ME_STORAGE_CHEMICAL_TEST PASS: actual gas, infusion, pigment and slurry tanks; 80 rapid transfers, selected-cell isolation, unsupported container, protection and Shift filling");
            }).thenSucceed();
    }
    private static void click(ControllerMenu menu,AEKey key,int button,boolean shift) {
        var s=menu.getSnapshot();menu.handleContentAction(new Network.ContentAction(menu.containerId,s.revision(),s.selectedDevice(),s.selectedCell(),key,button,shift));
    }
}
