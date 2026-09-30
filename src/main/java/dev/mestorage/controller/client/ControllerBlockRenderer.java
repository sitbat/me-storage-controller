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

/** Full-bright Fluix terminal masks with a subtle online pulse, using AE2's actual pixel artwork. */
public final class ControllerBlockRenderer implements BlockEntityRenderer<ControllerBlockEntity> {
    private static final ResourceLocation[] LAYERS = {
            texture("dark"), texture("medium"), texture("bright")
    };
    private static ResourceLocation texture(String shade) {
        return new ResourceLocation(MEStorageController.ID,
                "textures/ae2_1_21/part/terminal_" + shade + ".png");
    }
    public ControllerBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(ControllerBlockEntity entity,float partialTick,PoseStack pose,
            MultiBufferSource buffers,int packedLight,int packedOverlay) {
        if (entity.getLevel()==null || !entity.getBlockState().getValue(BlockStateProperties.LIT)) return;
        float pulse=.72F+.10F*(float)Math.sin((entity.getLevel().getGameTime()+partialTick)*Math.PI/50);
        pose.pushPose(); pose.translate(.5,0,.5);
        float angle=switch(entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle)); pose.translate(-.5,0,-.5);
        var matrix=pose.last().pose(); var normal=pose.last().normal();
        for (int layer = 0; layer < LAYERS.length; layer++) {
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(LAYERS[layer]));
            int tint = ClientSetup.terminalColor(layer + 1, true);
            float red=(tint >> 16 & 255)/255F, green=(tint >> 8 & 255)/255F, blue=(tint & 255)/255F;
            float z=.795F/16;
            // North-face model UVs increase toward decreasing world X; align the glow with the baked mask.
            consumer.vertex(matrix,2F/16,2F/16,z).color(red,green,blue,pulse).uv(14F/16,14F/16).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
            consumer.vertex(matrix,2F/16,14F/16,z).color(red,green,blue,pulse).uv(14F/16,2F/16).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
            consumer.vertex(matrix,14F/16,14F/16,z).color(red,green,blue,pulse).uv(2F/16,2F/16).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
            consumer.vertex(matrix,14F/16,2F/16,z).color(red,green,blue,pulse).uv(2F/16,14F/16).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal,0,0,-1).endVertex();
        }
        pose.popPose();
    }
}
