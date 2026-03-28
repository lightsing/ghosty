package me.lightsing.minecraft.ghosty;

import me.lightsing.minecraft.ghosty.api.EntityVisibilityEvents;
import me.lightsing.minecraft.ghosty.api.event.impl.EntityVisibilityContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class Visibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ghosty.MOD_ID);
    private final Map<Integer, Boolean> entityVisibility = new HashMap<>();
    private final Map<Integer, Vec3> entityLastKnownPositions = new HashMap<>();
    private int lastCheckTick;
    private float lastYaw;
    private float lastPitch;
    private int currentRefreshTick = -1;

    public Visibility(float yaw, float pitch) {
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    public Visibility(ServerPlayer player) {
        this(player.getYRot(), player.getXRot());
    }

    public void checkPlayerStateAndRefresh(ServerPlayer player) {
        GhostyConfig.CacheSettings settings = GhostyConfig.getInstance().cacheSettings();

        if (player.tickCount - lastCheckTick > settings.forceRefreshNTicks()) {
            refreshAll(player);
            return;
        }

        float yawDiff = Math.abs(Math.round(player.getYRot()) - Math.round(lastYaw));
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        float pitchDiff = Math.abs(Math.round(player.getXRot()) - Math.round(lastPitch));

        if (yawDiff > settings.byPlayer().yawChangedThreshold() || pitchDiff > settings.byPlayer().pitchChangedThreshold()) {
            refreshAll(player);
        }
    }

    public boolean handleEntity(ServerPlayer player, int entityId) {
        Entity target = player.level().getEntity(entityId);
        if (target == null) {
            return false;
        }

        Visibility.VisibilityResult result = this.isEntityVisible(player, target);

        if (result.shouldDespawn() && GhostyConfig.getInstance().isShouldDespawn()) {
            PacketHelper.despawnEntity(player, entityId);
            return true;
        }

        if (result.shouldSpawn()) {
            if (target instanceof ServerPlayer)
                LOGGER.debug("Entity {} should spawn for player {}, sending spawn packet", entityId, player.getName().getString());
            PacketHelper.updateEntity(player, target);
            return true;
        }

        return !result.isVisibleNow;
    }

    public VisibilityResult isEntityVisible(ServerPlayer player, Entity target) {
        return isEntityVisible(player, target, true);
    }

    public VisibilityResult isEntityVisible(ServerPlayer player, Entity target, boolean initialVisibility) {
        InteractionResult shouldCheck = EntityVisibilityEvents.PRE.invoker().shouldCheck(player, target);
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
            EntityVisibilityEvents.POST.invoker().onCheckVisibility(new EntityVisibilityContext(player, target, result));
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
            return distanceMovedSq > GhostyConfig.getInstance().cacheSettings().byTarget().positionMovedThresholdSq();
        }
        return false;
    }

    public static class VisibilityResult {
        public final boolean wasPreviouslyKnownVisible;
        public boolean isVisibleNow;

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
}
