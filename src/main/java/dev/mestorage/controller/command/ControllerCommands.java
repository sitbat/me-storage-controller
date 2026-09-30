package dev.mestorage.controller.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.config.ControllerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = MEStorageController.ID)
public final class ControllerCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ControllerCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mestorage")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("energyBypass")
                        .executes(context -> report(context.getSource(), false))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    try {
                                        ControllerConfig.setBypassAeEnergyLimit(BoolArgumentType.getBool(context, "enabled"));
                                    } catch (RuntimeException failure) {
                                        LOGGER.error("Could not save ME Storage Controller server configuration", failure);
                                        context.getSource().sendFailure(Component.translatable("command.me_storage_controller.config_save_failed"));
                                        return 0;
                                    }
                                    return report(context.getSource(), true);
                                }))));
    }

    private static int report(CommandSourceStack source, boolean changed) {
        boolean enabled = ControllerConfig.bypassAeEnergyLimit();
        source.sendSuccess(() -> Component.translatable("command.me_storage_controller.energy_bypass",
                Component.translatable(enabled ? "options.on" : "options.off")), changed);
        return 1;
    }
}
