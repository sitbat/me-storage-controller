package dev.mestorage.controller.client;

import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MEStorageController.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MEStorageController.ENTITY.get(),ControllerBlockRenderer::new);
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, index) -> terminalColor(index,
                state.getValue(BlockStateProperties.LIT)), MEStorageController.CONTROLLER.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, index) -> terminalColor(index, true), MEStorageController.CONTROLLER_ITEM.get());
    }

    /** AE2 1.21.1's neutral Fluix terminal tint, applied to its unchanged monochrome masks. */
    static int terminalColor(int index, boolean online) {
        if (index == 7) return 0x211c30;
        int color = switch (index) {
            case 1 -> 0x5a479e;
            case 2 -> 0x915dcd;
            case 3 -> 0xe2a3e3;
            default -> 0xffffff;
        };
        if (online || index < 1 || index > 3) return color;
        // Preserve the glyph while making an unpowered monitor visibly dim.
        return ((color >> 16 & 255) * 2 / 5 << 16)
                | ((color >> 8 & 255) * 2 / 5 << 8) | (color & 255) * 2 / 5;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(MEStorageController.MENU.get(), ControllerScreen::new);
            Network.clientReceiver = packet -> {
                var player = Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof ControllerMenu menu
                        && menu.containerId == packet.containerId()) menu.setSnapshot(packet.snapshot());
            };
            Network.directoryReceiver = packet -> {
                var player = Minecraft.getInstance().player;
                if (player != null && player.containerMenu instanceof ControllerMenu menu
                        && menu.containerId == packet.containerId()) menu.acceptDirectoryFrame(packet.frame());
            };
            Network.folderReceiver = packet -> {
                var player=Minecraft.getInstance().player;
                if(player!=null&&player.containerMenu instanceof ControllerMenu menu&&menu.containerId==packet.containerId())menu.acceptFolderFrame(packet);
            };
            Network.folderFeedbackReceiver = packet -> {
                var player=Minecraft.getInstance().player;
                if(player!=null&&player.containerMenu instanceof ControllerMenu menu&&menu.containerId==packet.containerId())menu.acceptFolderFeedback(packet);
            };
        });
    }
}
