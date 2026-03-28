package me.lightsing.minecraft.ghosty;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Ghosty implements ModInitializer {
    public static final String MOD_ID = "ghosty";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        GhostyConfig.getInstance(); // load config on startup

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();

            VisibilityManager.cleanup(player);
            VisibilityManager.notifyOtherPlayersLogout(player);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Visibility visibility = VisibilityManager.getOrCreate(player);
                visibility.checkPlayerStateAndRefresh(player);
            }
        });
    }
}