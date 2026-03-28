package me.lightsing.minecraft.ghosty.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

public interface EntityVisibilityPreCheckCallback {


    /**
     * Called before the visibility check is performed. If any listener returns FAIL, the visibility check will be
     * skipped and the entity will be treated as visible.
     * If all listeners return PASS, the visibility check will proceed as normal.
     *
     * @param player the player for whom the visibility check is being performed
     * @param target the entity being checked for visibility
     * @return FAIL to skip the visibility check and treat the entity as visible, PASS to leave the decision to the next
     * listener, or SUCCESS to proceed with the visibility check as normal.
     */
    InteractionResult shouldCheck(ServerPlayer player, Entity target);
}
