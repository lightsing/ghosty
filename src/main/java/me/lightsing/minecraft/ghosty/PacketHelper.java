package me.lightsing.minecraft.ghosty;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PacketHelper {
    public static void spawnEntity(ServerPlayer player, Entity target) {
        ServerEntity tempServerEntity = new ServerEntity(
                player.serverLevel(),
                target,
                0,
                true,
                (pkt) -> {}
        );
        player.connection.send(new ClientboundAddEntityPacket(target, tempServerEntity), null);

        var data = target.getEntityData().getNonDefaultValues();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(target.getId(), data), null);
        }
        if (target instanceof LivingEntity livingTarget) {
            List<Pair<EquipmentSlot, ItemStack>> equipment = new java.util.ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = livingTarget.getItemBySlot(slot);
                if (!item.isEmpty()) {
                    equipment.add(Pair.of(slot, item));
                }
            }
            if (!equipment.isEmpty()) {
                player.connection.send(new ClientboundSetEquipmentPacket(target.getId(), equipment), null);
            }
        }
    }

    public static void despawnEntity(ServerPlayer player, int entityId) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(entityId), null);
    }
}
