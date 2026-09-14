package io.github.zatone0.ae2craftingrecovery.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PendingPlayerAlerts extends SavedData {
    private static final String DATA_NAME = "ae2_crafting_recovery_alerts";
    private static final int MAX_ALERTS_PER_PLAYER = 32;
    private final Map<UUID, List<String>> alerts = new LinkedHashMap<>();

    public static PendingPlayerAlerts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PendingPlayerAlerts::load, PendingPlayerAlerts::new, DATA_NAME);
    }

    public static PendingPlayerAlerts load(CompoundTag tag) {
        var data = new PendingPlayerAlerts();
        for (var playerTag : tag.getList("players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) playerTag;
            UUID id = player.getUUID("uuid");
            var messages = new ArrayList<String>();
            for (var messageTag : player.getList("messages", Tag.TAG_STRING)) {
                messages.add(messageTag.getAsString());
            }
            if (!messages.isEmpty()) {
                data.alerts.put(id, messages);
            }
        }
        return data;
    }

    public void queue(UUID playerId, Component message) {
        String json = Component.Serializer.toJson(message);
        var messages = alerts.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        if (messages.contains(json)) {
            return;
        }
        if (messages.size() >= MAX_ALERTS_PER_PLAYER) {
            messages.remove(0);
        }
        messages.add(json);
        setDirty();
    }

    public void deliver(ServerPlayer player) {
        var messages = alerts.remove(player.getUUID());
        if (messages == null) {
            return;
        }
        player.sendSystemMessage(Component.literal("AE2 Crafting Recovery: alerts recorded while you were offline:")
                .withStyle(ChatFormatting.GOLD));
        for (var json : messages) {
            Component message = Component.Serializer.fromJson(json);
            if (message != null) {
                player.sendSystemMessage(message);
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var players = new ListTag();
        for (var entry : alerts.entrySet()) {
            var player = new CompoundTag();
            player.putUUID("uuid", entry.getKey());
            var messages = new ListTag();
            for (var json : entry.getValue()) {
                messages.add(StringTag.valueOf(json));
            }
            player.put("messages", messages);
            players.add(player);
        }
        tag.put("players", players);
        return tag;
    }
}
