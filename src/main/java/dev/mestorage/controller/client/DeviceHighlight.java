package dev.mestorage.controller.client;

import java.util.OptionalDouble;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One short lived client-side marker; never requests or loads a chunk. */
@Mod.EventBusSubscriber(modid = "me_storage_controller", value = Dist.CLIENT)
public final class DeviceHighlight {
    private static ResourceLocation dimension;
    private static BlockPos position;
    private static long expiresAt;

    private DeviceHighlight() {}

    public static void show(ResourceLocation targetDimension, BlockPos target) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        String reason = "highlight.started";
        if (!minecraft.level.dimension().location().equals(targetDimension)) {
            reason = "highlight.dimension";
        } else if (minecraft.player.distanceToSqr(target.getCenter()) > 256 * 256
                || !minecraft.level.hasChunkAt(target)) {
            reason = "highlight.unavailable";
        } else {
            dimension = targetDimension;
            position = target.immutable();
            expiresAt = System.currentTimeMillis() + 15_000;
        }
        minecraft.player.displayClientMessage(Component.translatable("gui.me_storage_controller." + reason,
                targetDimension.toString(), target.getX(), target.getY(), target.getZ()), false);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || position == null) return;
        var minecraft = Minecraft.getInstance();
        if (System.currentTimeMillis() > expiresAt || minecraft.level == null || minecraft.player == null) {
            position = null;
            return;
        }
        if (!minecraft.level.dimension().location().equals(dimension)
                || minecraft.player.distanceToSqr(position.getCenter()) > 256 * 256
                || !minecraft.level.hasChunkAt(position)) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(HighlightRenderType.LINES),
                new AABB(position).inflate(0.005), 0.3F, 1F, 0.78F, 1F);
        buffers.endBatch(HighlightRenderType.LINES);
        pose.popPose();
    }

    private static final class HighlightRenderType extends RenderType {
        private static final RenderType LINES = create("me_storage_controller_highlight",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false));

        private HighlightRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                boolean delegate, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, delegate, sort, setup, clear);
        }
    }
}
