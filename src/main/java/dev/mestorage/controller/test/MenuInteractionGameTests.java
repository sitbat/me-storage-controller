package dev.mestorage.controller.test;

import java.util.List;
import java.util.function.Consumer;
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
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
