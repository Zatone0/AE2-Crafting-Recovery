package io.github.zatone0.ae2craftingrecovery.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.networking.crafting.ICraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.github.zatone0.ae2craftingrecovery.AE2CraftingRecovery;

/** Optional bridge to ExpandedAE's existing client-side block highlighter. */
public final class ExpandedAeHighlightCompat {
    private static final int MAX_LOCATIONS_PER_PLAYER = 32;
    private static final long HIGHLIGHT_MILLIS = 15_000L;
    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(1);
    private static final Map<UUID, Map<Integer, ProviderLocation>> LOCATIONS = new ConcurrentHashMap<>();

    private ExpandedAeHighlightCompat() {
    }

    public static ProviderLocation locate(ICraftingProvider provider) {
        try {
            Method accessor = provider.getClass().getMethod("eae$getBlockPos");
            Object result = accessor.invoke(provider);
            if (result instanceof BlockEntity blockEntity && blockEntity.getLevel() != null) {
                return new ProviderLocation(blockEntity.getBlockPos().immutable(), blockEntity.getLevel().dimension());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // ExpandedAE is optional, and not every provider is backed by its pattern-provider logic.
        }
        return null;
    }

    public static int remember(ServerPlayer player, ProviderLocation location) {
        var playerLocations = LOCATIONS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        if (playerLocations.size() >= MAX_LOCATIONS_PER_PLAYER) {
            playerLocations.clear();
        }
        int token = NEXT_TOKEN.getAndUpdate(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
        playerLocations.put(token, location);
        return token;
    }

    public static boolean highlight(ServerPlayer player, int token) {
        var playerLocations = LOCATIONS.get(player.getUUID());
        var location = playerLocations == null ? null : playerLocations.get(token);
        if (location == null) {
            return false;
        }
        try {
            Class<?> packetClass = Class.forName("lu.kolja.expandedae.network.implementations.HighlightDataPacket");
            Constructor<?> constructor = packetClass.getConstructor(BlockPos.class, ResourceKey.class, long.class);
            Object packet = constructor.newInstance(
                    location.pos(), location.dimension(), System.currentTimeMillis() + HIGHLIGHT_MILLIS);

            Class<?> handlerClass = Class.forName("lu.kolja.expandedae.network.ExpNetworkHandler");
            Field handlerField = handlerClass.getField("HANDLER");
            Object handler = handlerField.get(null);
            Method send = handler.getClass().getMethod("sendToClient", Object.class, Player.class);
            send.invoke(handler, packet, player);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            AE2CraftingRecovery.LOGGER.warn("Could not invoke ExpandedAE provider highlighting", e);
            return false;
        }
    }

    public record ProviderLocation(BlockPos pos, ResourceKey<Level> dimension) {
        @Override
        public String toString() {
            return dimension.location() + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
    }
}
