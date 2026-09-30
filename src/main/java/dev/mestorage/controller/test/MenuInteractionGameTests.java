package dev.mestorage.controller.test;

import java.util.List;
import java.util.function.Consumer;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.Snapshot;
import io.netty.buffer.Unpooled;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class MenuInteractionGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void directoryIncludesEveryDriveAndReusesUnchangedBranches(GameTestHelper helper) {
        var controllerPos=new BlockPos(2,2,2);
        helper.setBlock(controllerPos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(controllerPos,MEStorageController.CONTROLLER.get());
        var directions=List.of(Direction.EAST,Direction.WEST,Direction.NORTH,Direction.SOUTH);
        var drives=new java.util.ArrayList<DriveBlockEntity>();
        for(var direction:directions) {
            var position=controllerPos.relative(direction);
            // AE drives cannot connect through their front. Point each front
            // away from the controller so all four rear faces join this grid.
            var state=AEBlocks.DRIVE.block().defaultBlockState();
            helper.setBlock(position,IOrientationStrategy.get(state).setFacing(state,direction));
            var drive=(DriveBlockEntity)helper.getBlockEntity(position);
            drive.getInternalInventory().setItemDirect(9,AEItems.ITEM_CELL_64K.stack());
            drives.add(drive);
        }
        var controller=(ControllerBlockEntity)helper.getBlockEntity(controllerPos);
        var player=helper.makeMockSurvivalPlayer(); var absolute=helper.absolutePos(controllerPos);
        player.setPos(absolute.getX()+.5,absolute.getY(),absolute.getZ()+.5);
        var menu=new ControllerMenu(8,player.getInventory(),controller);
        final Snapshot[] initial={null};
        helper.startSequence()
            .thenWaitUntil(()->helper.assertTrue(controller.getMainNode().isActive()
                    && drives.stream().allMatch(d->d.getMainNode().isActive()),"Directory fixture did not boot"))
            .thenExecute(()->{
                drives.get(0).getCellInventory(9).insert(AEItemKey.of(Items.IRON_INGOT),12345,Actionable.MODULATE,IActionSource.empty());
                player.containerMenu=menu; menu.broadcastChanges(); initial[0]=menu.getSnapshot();
                helper.assertTrue(initial[0].devices().size()==3 && initial[0].directory().size()==4,
                        "Tree was incorrectly limited to the legacy three-row device page");
                helper.assertTrue(initial[0].directoryTotalDevices()==4 && !initial[0].directoryTruncated(),"Incorrect directory totals");
                for(var entry:initial[0].directory()) {
                    helper.assertTrue(entry.cellSlots()==10 && entry.cells().size()==10,"Unselected drive children missing");
                    helper.assertTrue(entry.cells().get(0).icon().isEmpty() && !entry.cells().get(9).icon().isEmpty(),"Empty and occupied children mixed up");
                    helper.assertTrue(!entry.cells().get(9).icon().hasTag(),"Directory leaked full storage-cell NBT");
                }
                var buffer=new FriendlyByteBuf(Unpooled.buffer());
                try {
                    initial[0].write(buffer); var decoded=Snapshot.read(buffer);
                    helper.assertTrue(decoded.directory().size()==4 && decoded.directory().get(3).cells().size()==10
                            && buffer.readableBytes()==0,"Directory protocol did not round-trip");
                    helper.assertTrue(decoded.directory().stream().flatMap(d->d.cells().stream()).anyMatch(c->c.usedBytes()>0),
                            "Directory omitted the live cell's capacity data");
                } finally { buffer.release(); }
            })
            .thenIdle(1)
            .thenExecute(()->{
                var first=initial[0].directory().get(0);
                menu.handleRequest(new Network.Request(menu.containerId,first.device().id(),9,0,0,"impossible_filter_040","",true,List.of(),List.of(),1));
                menu.broadcastChanges(); var selected=menu.getSnapshot();
                helper.assertTrue(selected.devices().isEmpty() && selected.directory().size()==4,
                        "Search/page filtering removed actual tree branches");
                helper.assertTrue(selected.directory().get(1).cells()==initial[0].directory().get(1).cells(),
                        "Selecting a cell rescanned an unrelated cached branch");
                helper.assertTrue(selected.selectedCell()==9 && selected.editableSlots()==10,"Tree selection changed remote slot semantics");
                player.containerMenu=player.inventoryMenu;
            })
            .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rapidCellClicksKeepContentsAndRespectProtection(GameTestHelper helper) {
        var controllerPos = new BlockPos(2, 2, 2);
        var drivePos = controllerPos.east();
        helper.setBlock(controllerPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(controllerPos, MEStorageController.CONTROLLER.get());
        helper.setBlock(drivePos, AEBlocks.DRIVE.block());
        var controller = (ControllerBlockEntity) helper.getBlockEntity(controllerPos);
        var drive = (DriveBlockEntity) helper.getBlockEntity(drivePos);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        drive.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_64K.stack());
        var player = helper.makeMockSurvivalPlayer();
        var absoluteController = helper.absolutePos(controllerPos);
        player.setPos(absoluteController.getX() + .5, absoluteController.getY(), absoluteController.getZ() + .5);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.getMainNode().isActive()
                        && drive.getMainNode().isActive(), "Test network did not boot"))
                .thenExecute(() -> {
                    drive.getCellInventory(0).insert(iron, 12345, Actionable.MODULATE, IActionSource.empty());
                    var device = StorageScanner.discover(controller.getMainNode().getGrid()).stream()
                            .filter(entry -> entry.owner() == drive).findFirst().orElseThrow();
                    var menu = new ControllerMenu(7, player.getInventory(), controller);
                    player.containerMenu = menu;
                    menu.handleRequest(new Network.Request(menu.containerId, device.id(), 0, 0, 0, "", "", true,
                            List.of(), List.of(), 1));
                    menu.broadcastChanges();
                    helper.assertTrue(menu.getSnapshot().editableSlots() == 10, "Remote slots did not become editable");
                    var baseline = menu.getSnapshot();
                    for (int i = 0; i < 30; i++) {
                        menu.clicked(0, 0, ClickType.PICKUP, player);
                        helper.assertTrue(!menu.getCarried().isEmpty() && drive.getInternalInventory().getStackInSlot(0).isEmpty(),
                                "Rapid pickup was ignored");
                        // This is exactly the action AbstractContainerScreen emits
                        // on the second left release within its 250ms window.
                        menu.clicked(0, 0, ClickType.PICKUP_ALL, player);
                        helper.assertTrue(menu.getCarried().isEmpty() && !drive.getInternalInventory().getStackInSlot(0).isEmpty(),
                                "Rapid second click was swallowed instead of reinserting");
                        menu.broadcastChanges();
                        helper.assertTrue(menu.getSnapshot() == baseline,
                                "Rapid clicks triggered redundant full storage scans in the same tick");
                    }
                    helper.assertTrue(drive.getCellInventory(0).getAvailableStacks().get(iron) == 12345,
                            "Repeated pickup/reinsert changed the stored item count");
                    helper.assertTrue(!drive.getInternalInventory().getStackInSlot(1).isEmpty(), "Double-click collected a neighboring cell");
                    helper.assertTrue(menu.canTakeItemForPickAll(ItemStack.EMPTY, menu.getSlot(0)),
                            "Vanilla screen's empty-stack gesture probe must remain enabled");
                    helper.assertTrue(!menu.canTakeItemForPickAll(AEItems.ITEM_CELL_64K.stack(), menu.getSlot(0)),
                            "Actual backpack gather-all must exclude remote slots");

                    Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
                        if (event.getEntity() == player && event.getPos().equals(helper.absolutePos(drivePos))) event.setCanceled(true);
                    };
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, deny);
                    try {
                        menu.clicked(0, 0, ClickType.PICKUP_ALL, player);
                        helper.assertTrue(menu.getCarried().isEmpty() && !drive.getInternalInventory().getStackInSlot(0).isEmpty(),
                                "Normalized rapid click bypassed the remote protection hook");
                    } finally {
                        MinecraftForge.EVENT_BUS.unregister(deny);
                    }

                    helper.setBlock(drivePos, Blocks.AIR);
                    menu.clicked(0, 0, ClickType.PICKUP_ALL, player);
                    helper.assertTrue(menu.getCarried().isEmpty(), "Rapid click accessed a removed drive");
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }
}
