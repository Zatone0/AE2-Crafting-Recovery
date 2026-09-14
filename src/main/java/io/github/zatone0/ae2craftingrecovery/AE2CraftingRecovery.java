package io.github.zatone0.ae2craftingrecovery;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import io.github.zatone0.ae2craftingrecovery.compat.ExpandedAeHighlightCompat;
import io.github.zatone0.ae2craftingrecovery.config.RecoveryConfig;
import io.github.zatone0.ae2craftingrecovery.notification.PendingPlayerAlerts;

@Mod(AE2CraftingRecovery.MOD_ID)
public final class AE2CraftingRecovery {
    public static final String MOD_ID = "ae2_crafting_recovery";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2CraftingRecovery() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RecoveryConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);
        LOGGER.info("AE2 Crafting Recovery diagnostic detector enabled");
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PendingPlayerAlerts.get(player.getServer()).deliver(player);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2cr")
                .then(Commands.literal("highlight")
                        .then(Commands.argument("token", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    int token = IntegerArgumentType.getInteger(context, "token");
                                    if (ExpandedAeHighlightCompat.highlight(player, token)) {
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Highlighted the stalled provider for 15 seconds")
                                                        .withStyle(ChatFormatting.GREEN),
                                                false);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal(
                                            "That provider highlight is unavailable or expired; wait for a new stall alert"));
                                    return 0;
                                }))));
    }
}
