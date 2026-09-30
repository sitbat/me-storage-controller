package dev.mestorage.controller.test;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class StorageAdapterGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nullableMeCapabilityFallsBackToPhysicalHandler(GameTestHelper helper) {
        var controllerPos = new BlockPos(2, 2, 2);
        var busPos = controllerPos.east();
        var chestPos = busPos.east();
        helper.setBlock(controllerPos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(controllerPos, MEStorageController.CONTROLLER.get());
        helper.setBlock(chestPos, AEBlocks.CHEST.block());
        var controller = (ControllerBlockEntity) helper.getBlockEntity(controllerPos);
        var chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        chest.getInternalInventory().setItemDirect(0, new ItemStack(Items.STONE, 32));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), Direction.EAST, null,
                AEParts.STORAGE_BUS.asItem());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(controller.getMainNode().isActive()
                        && bus.getMainNode().isActive(), "Storage bus must boot"))
                .thenExecute(() -> {
                    var device = StorageScanner.discover(controller.getMainNode().getGrid()).stream()
                            .filter(entry -> entry.owner() == bus).findFirst().orElseThrow();
                    var target = StorageScanner.targetInfo(device);
                    var physical = StorageScanner.externalCapacity(device);
                    // Empty ME chest offers a nonempty LazyOptional whose ME
                    // supplier returns null. AE2 falls back to its input handler.
                    helper.assertTrue(target != null && target.adapter().equals("items"),
                            "Null-resolving ME capability must not override real item handler");
                    helper.assertTrue(physical.totalSlots() == 1 && physical.occupiedSlots() == 1,
                            "Empty ME chest input handler must report its real physical slot");
                    helper.assertTrue(!StorageScanner.isDegraded(device), "Null capability is valid fallback, not scanner failure");
                    chest.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_1K.stack());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var device = StorageScanner.discover(controller.getMainNode().getGrid()).stream()
                            .filter(entry -> entry.owner() == bus).findFirst().orElseThrow();
                    helper.assertTrue(StorageScanner.targetInfo(device).adapter().equals("me"),
                            "A real ME cell inventory must take priority once available");
                    helper.assertTrue(StorageScanner.externalCapacity(device).totalSlots() == -1,
                            "Input-buffer capacity must not be presented as ME network capacity");
                })
                .thenSucceed();
    }
}
