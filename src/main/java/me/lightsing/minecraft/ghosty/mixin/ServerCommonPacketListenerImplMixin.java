package me.lightsing.minecraft.ghosty.mixin;

import me.lightsing.minecraft.ghosty.Ghosty;
import me.lightsing.minecraft.ghosty.Visibility;
import me.lightsing.minecraft.ghosty.VisibilityManager;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(Ghosty.MOD_ID);

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings("ConstantValue")
    private void ghosty$onSend(Packet<?> packet, @Nullable PacketSendListener ignoredListener, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) {
            return;
        }

        ServerPlayer player = gameListener.player;

        // sanity check
        if (player == null) return;

        if (packet instanceof ClientboundAddEntityPacket spawnPacket) {
            int entityId = spawnPacket.getId();
            handleEntitySpawn(player, entityId, ci);
            return;
        }

        if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            Entity target = movePacket.getEntity(player.level());
            if (target == null) {
                return;
            }
            int entityId = target.getId();
            handleEntity(player, entityId, ci);
        }

        if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            int entityId = teleportPacket.getId();
            handleEntity(player, entityId, ci);
        }

        if (packet instanceof ClientboundSetEntityDataPacket dataPacket) {
            int entityId = dataPacket.id();
            handleEntity(player, entityId, ci);
        }

        if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            int entityId = equipmentPacket.getEntity();
            handleEntity(player, entityId, ci);
        }
    }

    @Unique
    private void handleEntitySpawn(ServerPlayer player, int entityId, CallbackInfo ci) {
        Entity target = player.serverLevel().getEntity(entityId);
        if (target == null || target == player) return;

        Visibility visibility = VisibilityManager.getOrCreate(player);
        Visibility.VisibilityResult result = visibility.isEntityVisible(player, target, true);

        if (!result.isVisibleNow) {
            ci.cancel();
        }
    }

    @Unique
    private void handleEntity(ServerPlayer player, int entityId, CallbackInfo ci) {
        Visibility visibility = VisibilityManager.getOrCreate(player);

        if (visibility.handleEntity(player, entityId)) {
            ci.cancel();
        }
    }
}
