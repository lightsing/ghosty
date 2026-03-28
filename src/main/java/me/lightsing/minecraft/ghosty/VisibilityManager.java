package me.lightsing.minecraft.ghosty;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VisibilityManager {

    private static final Map<UUID, Visibility> CACHE = new HashMap<>();

    public static Visibility getOrCreate(ServerPlayer player) {
        return CACHE.computeIfAbsent(player.getUUID(), k -> new Visibility(player));
    }

    public static void cleanup(@NotNull ServerPlayer player) {
        CACHE.remove(player.getUUID());
    }

    public static void notifyOtherPlayersLogout(ServerPlayer leavingPlayer) {
        for (Visibility vis : CACHE.values()) {
            vis.onOtherPlayerLogout(leavingPlayer);
        }
    }

    private @NotNull Visibility getOrCreateVisibility(@NotNull ServerPlayer player) {
        UUID uuid = player.getUUID();
        return CACHE.computeIfAbsent(uuid, k -> new Visibility(player));
    }
}
