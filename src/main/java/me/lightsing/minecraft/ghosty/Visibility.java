package me.lightsing.minecraft.ghosty;

import me.lightsing.minecraft.ghosty.api.EntityVisibilityCheckCallback;
import me.lightsing.minecraft.ghosty.api.EntityVisibilityPreCheckCallback;
import me.lightsing.minecraft.ghosty.api.event.impl.EntityVisibilityContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Visibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ghosty.MOD_ID);

    private static final int TICK_THRESHOLD = 5;
    private static final float YAW_THRESHOLD = 10.0f;
    private static final float PITCH_THRESHOLD = 10.0f;
    private static final double POSITION_MOVE_THRESHOLD_SQ = 1.0; // 1 block movement

    private int lastCheckTick;
    private float lastYaw;
    private float lastPitch;

    private int currentRefreshTick = -1;

    private final Map<Integer, Boolean> entityVisibility = new HashMap<>();
    private final Map<Integer, Vec3> entityLastKnownPositions = new HashMap<>();

    public static class VisibilityResult {
        public boolean isVisibleNow;
        public final boolean wasPreviouslyKnownVisible;

        public VisibilityResult(boolean isVisibleNow, boolean wasPreviouslyKnownVisible) {
            this.isVisibleNow = isVisibleNow;
            this.wasPreviouslyKnownVisible = wasPreviouslyKnownVisible;
        }

        public boolean shouldSpawn() {
            return isVisibleNow && !wasPreviouslyKnownVisible;
        }

        public boolean shouldDespawn() {
            return !isVisibleNow && wasPreviouslyKnownVisible;
        }
    }

    public Visibility(float yaw, float pitch) {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    public Visibility(ServerPlayer player) {
        this(player.getYRot(), player.getXRot());
    }

    public void checkPlayerStateAndRefresh(ServerPlayer player) {
        if (player.tickCount - lastCheckTick > TICK_THRESHOLD) {
            refreshAll(player);
            return;
        }

        float yawDiff = Math.abs(Math.round(player.getYRot()) - Math.round(lastYaw));
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        float pitchDiff = Math.abs(Math.round(player.getXRot()) - Math.round(lastPitch));

        if (yawDiff > YAW_THRESHOLD || pitchDiff > PITCH_THRESHOLD) {
            refreshAll(player);
        }
    }

    public boolean handleEntity(ServerPlayer player, int entityId) {
        Entity target = player.level().getEntity(entityId);
        if (target == null) {
            return false;
        }

        Visibility.VisibilityResult result = this.isEntityVisible(player, target);

        if (result.shouldDespawn()) {
            if (target instanceof ServerPlayer) LOGGER.debug("Entity {} should despawn for player {}, sending despawn packet", entityId, player.getName().getString());
            PacketHelper.despawnEntity(player, entityId);
            return true;
        }

        if (result.shouldSpawn()) {
            if (target instanceof ServerPlayer) LOGGER.debug("Entity {} should spawn for player {}, sending spawn packet", entityId, player.getName().getString());
            PacketHelper.spawnEntity(player, target);
            return true;
        }

        return !result.isVisibleNow;
    }


    public VisibilityResult isEntityVisible(ServerPlayer player, Entity target) {
        return isEntityVisible(player, target, true);
    }

    public VisibilityResult isEntityVisible(ServerPlayer player, Entity target, boolean initialVisibility) {
        InteractionResult shouldCheck = EntityVisibilityPreCheckCallback.EVENT.invoker().shouldCheck(player, target);
        if (shouldCheck == InteractionResult.FAIL) {
            return new VisibilityResult(true, true);
        }

        boolean shouldRecalculateVisibility = shouldRecalculateVisibility(player, target);

        boolean isCurrentlyKnownVisible = entityVisibility.getOrDefault(target.getId(), initialVisibility);
        VisibilityResult result;
        if (target instanceof ServerPlayer) {
            Ghosty.LOGGER.debug("Checking visibility for player {} and target {}: shouldRecalculate={}, currentlyKnownVisible={}", player.getName().getString(), target.getName().getString(), shouldRecalculateVisibility, isCurrentlyKnownVisible);
        }
        if (shouldRecalculateVisibility) {
            boolean currentlyVisible = VisibilityChecker.isVisible(player, target, isCurrentlyKnownVisible);
            entityVisibility.put(target.getId(), currentlyVisible);
            entityLastKnownPositions.put(target.getId(), target.position());
            result = new VisibilityResult(currentlyVisible, isCurrentlyKnownVisible);
            EntityVisibilityCheckCallback.EVENT.invoker().onCheckVisibility(new EntityVisibilityContext(player, target, result));
        } else {
            result = new VisibilityResult(isCurrentlyKnownVisible, isCurrentlyKnownVisible);
        }

        entityVisibility.put(target.getId(), result.isVisibleNow);
        return result;
    }

    public void onOtherPlayerLogout(ServerPlayer other) {
        entityVisibility.remove(other.getId());
        entityLastKnownPositions.remove(other.getId());
    }

    private void refreshAll(ServerPlayer player) {
        this.lastCheckTick = player.tickCount;
        this.lastYaw = player.getYRot();
        this.lastPitch = player.getXRot();
        this.currentRefreshTick = player.tickCount;

        for (int entityId : entityVisibility.keySet()) {
            handleEntity(player, entityId);
        }
    }

    private boolean shouldRecalculateVisibility(ServerPlayer player, Entity target) {
        if (player.tickCount == currentRefreshTick) {
            return true;
        }

        if (!entityVisibility.containsKey(target.getId())) {
            return true;
        }

        // If the target entity was not previously visible, we should check if it has moved significantly to
        // potentially become visible
        boolean visibilityStatus = entityVisibility.getOrDefault(target.getId(), false);
        Vec3 targetPos = target.position();
        Vec3 lastPos = entityLastKnownPositions.computeIfAbsent(target.getId(), id -> targetPos);
        if (!visibilityStatus) {
            Vec3 currentPos = target.position();
            double distanceMovedSq = lastPos.distanceToSqr(currentPos);
            return distanceMovedSq > POSITION_MOVE_THRESHOLD_SQ;
        }
        return false;
    }
}
