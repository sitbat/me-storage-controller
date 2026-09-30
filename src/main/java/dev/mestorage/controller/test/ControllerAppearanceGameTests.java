package dev.mestorage.controller.test;

import appeng.api.networking.IGridNode;
import appeng.core.definitions.AEBlocks;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class ControllerAppearanceGameTests {
    @GameTest(template="empty",timeoutTicks=300)
    public static void statusLightTracksPowerWithoutReplacingNode(GameTestHelper helper) {
        var pos=new BlockPos(2,2,2);
        helper.setBlock(pos,MEStorageController.CONTROLLER.get());
        helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
        var controller=(ControllerBlockEntity)helper.getBlockEntity(pos);
        var original=new IGridNode[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(controller.getMainNode().isActive()
                && controller.getBlockState().getValue(BlockStateProperties.LIT),"Powered controller did not illuminate"))
            .thenExecute(() -> {
                original[0]=controller.getMainNode().getNode();
                helper.setBlock(pos.below(),Blocks.AIR);
            })
            .thenWaitUntil(() -> helper.assertTrue(!controller.getMainNode().isActive()
                && !controller.getBlockState().getValue(BlockStateProperties.LIT),"Unpowered controller stayed illuminated"))
            .thenExecute(() -> {
                helper.assertTrue(controller.getMainNode().getNode()==original[0],"Appearance change recreated the grid node");
                helper.setBlock(pos.below(),AEBlocks.CREATIVE_ENERGY_CELL.block());
            })
            .thenWaitUntil(() -> helper.assertTrue(controller.getMainNode().isActive()
                && controller.getBlockState().getValue(BlockStateProperties.LIT),"Reconnected controller did not illuminate"))
            .thenExecute(() -> helper.assertTrue(controller.getMainNode().getNode()==original[0],"Reconnect appearance replaced the node"))
            .thenSucceed();
    }
}
