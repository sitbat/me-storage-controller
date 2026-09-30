package dev.mestorage.controller.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A slow status pulse; the storage screen artwork itself stays still. */
public final class ControllerBlockRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    private static final ResourceLocation ATLAS=new ResourceLocation(MEStorageController.ID,"textures/block/controller_atlas.png");
    public ControllerBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(ControllerBlockEntity entity,float partialTick,PoseStack pose,
            MultiBufferSource buffers,int packedLight,int packedOverlay) {
        if (entity.getLevel()==null || !entity.getBlockState().getValue(BlockStateProperties.LIT)) return;
        float pulse=.70F+.18F*(float)Math.sin((entity.getLevel().getGameTime()+partialTick)*Math.PI/40);
        pose.pushPose(); pose.translate(.5,0,.5);
        float angle=switch(entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle)); pose.translate(-.5,0,-.5);
        var consumer=buffers.getBuffer(RenderType.entityTranslucent(ATLAS));
        var matrix=pose.last().pose(); var normal=pose.last().normal();
        // Sample the atlas's solid cyan status pixel rather than the whole image.
        float u=.406F,v=.375F;
        consumer.vertex(matrix,5.5F/16,.48F/16,-.0008F).color(1F,1F,1F,pulse).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
        consumer.vertex(matrix,5.5F/16,.78F/16,-.0008F).color(1F,1F,1F,pulse).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
        consumer.vertex(matrix,10.5F/16,.78F/16,-.0008F).color(1F,1F,1F,pulse).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
        consumer.vertex(matrix,10.5F/16,.48F/16,-.0008F).color(1F,1F,1F,pulse).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
        pose.popPose();
    }
}
