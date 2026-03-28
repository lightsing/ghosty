package me.lightsing.minecraft.ghosty;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisibilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ghosty.MOD_ID);

    public static boolean isVisible(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        if (target instanceof ServerPlayer)LOGGER.debug("Checking visibility of entity {} for player {} (currently known visible: {})", target.getId(), viewer.getName().getString(), isCurrentlyKnownVisible);
        if (target == viewer || target.isRemoved()) {
            return true;
        }

        // fast distance check
        if (!isTargetInRange(viewer, target, isCurrentlyKnownVisible)) {
            if (target instanceof ServerPlayer)LOGGER.debug("Entity {} is out of range for player {}", target.getId(), viewer.getName().getString());
            return false;
        }

        // run raycast checks for very close entities to allow them to be visible even if they are technically outside the frustum
        if (isVeryClose(viewer, target)) {
            return isPartiallyVisibleViaRaycast(viewer, target);
        }

        // frustum check
        if (!isBoundingBoxInFrustum(viewer, target, isCurrentlyKnownVisible)) {
            if (target instanceof ServerPlayer)LOGGER.debug("Entity {} is outside of frustum for player {}", target.getId(), viewer.getName().getString());
            return false;
        }

        // perform sight check (more strict, if this passes, the entity is definitely visible)
        if (target instanceof LivingEntity livingTarget && viewer.hasLineOfSight(livingTarget)) {
            if (target instanceof ServerPlayer)LOGGER.debug("Entity {} is directly visible to player {}", target.getId(), viewer.getName().getString());
            return true;
        }

        return isPartiallyVisibleViaRaycast(viewer, target);
    }

    // ---- Fast distance checks to avoid expensive frustum checks when entities are far away ----
    private static final double ENTER_TRACK_DISTANCE = 8.0 * 16.0; // 8 chunks
    private static final double ENTER_TRACK_DISTANCE_SQ = ENTER_TRACK_DISTANCE * ENTER_TRACK_DISTANCE;
    private static final double EXIT_TRACK_DISTANCE = 12.0 * 16.0; // 12 chunks
    private static final double EXIT_TRACK_DISTANCE_SQ = EXIT_TRACK_DISTANCE * EXIT_TRACK_DISTANCE;

    private static boolean isTargetInRange(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        double maxDistanceSq = isCurrentlyKnownVisible ? EXIT_TRACK_DISTANCE_SQ : ENTER_TRACK_DISTANCE_SQ;
        return viewer.distanceToSqr(target) <= maxDistanceSq;
    }

    // --- Very close entities should be visible regardless of frustum checks ---
    private static final double PROXIMITY_THRESHOLD_SQ = 36.0;

    private static boolean isVeryClose(ServerPlayer viewer, Entity target) {
        return viewer.distanceToSqr(target) <= PROXIMITY_THRESHOLD_SQ;
    }

    // ---- Frustum angle checks to only display entities that are roughly in front of the player ----
    private static final double ENTER_ANGLE_THRESHOLD = Math.cos(Math.toRadians(45.0));
    private static final double EXIT_ANGLE_THRESHOLD = Math.cos(Math.toRadians(65.0));

    private static boolean isBoundingBoxInFrustum(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        double threshold = isCurrentlyKnownVisible ? EXIT_ANGLE_THRESHOLD : ENTER_ANGLE_THRESHOLD;

        AABB box = target.getBoundingBox();
        Vec3 eyePos = viewer.getEyePosition();
        Vec3 lookDir = viewer.getLookAngle();
        Vec3 boxCenter = box.getCenter();

        Vec3 dirToBox = boxCenter.subtract(eyePos).normalize();
        double dot = lookDir.dot(dirToBox);

        // If the center of the bounding box is within the angle threshold, consider it visible
        if (dot >= threshold) {
            return true;
        }

        // If not, check the corners of the bounding box for a more lenient visibility check
        return isAnyCornerInFrustum(viewer, box, threshold);
    }

    private static boolean isAnyCornerInFrustum(ServerPlayer viewer, AABB box, double threshold) {
        Vec3 eyePos = viewer.getEyePosition();
        Vec3 lookDir = viewer.getLookAngle();

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};

        // Check all 8 corners of the bounding box
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 corner = new Vec3(x, y, z);
                    Vec3 dirToCorner = corner.subtract(eyePos).normalize();
                    if (lookDir.dot(dirToCorner) >= threshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- Raycast checks to allow entities to be visible if they are partially visible around corners or through gaps ----
    private static final double[] RAYCAST_SAMPLE_X = {0.5, 0.1, 0.9, 0.1, 0.9, 0.5, 0.5, 0.5, 0.5};
    private static final double[] RAYCAST_SAMPLE_Y = {0.5, 0.1, 0.1, 0.9, 0.9, 1.0, 0.0, 0.5, 0.5};
    private static final double[] RAYCAST_SAMPLE_Z = {0.5, 0.1, 0.1, 0.9, 0.9, 0.5, 0.5, 0.1, 0.9};

    private static boolean isPartiallyVisibleViaRaycast(ServerPlayer viewer, Entity target) {
        ServerLevel level = viewer.serverLevel();

        AABB box = target.getBoundingBox();
        Vec3 eyePos = viewer.getEyePosition();
        CollisionContext context = CollisionContext.of(viewer);

        for (int i = 0; i < RAYCAST_SAMPLE_X.length; i++) {
            Vec3 samplePoint = new Vec3(
                    box.minX + (box.maxX - box.minX) * RAYCAST_SAMPLE_X[i],
                    box.minY + (box.maxY - box.minY) * RAYCAST_SAMPLE_Y[i],
                    box.minZ + (box.maxZ - box.minZ) * RAYCAST_SAMPLE_Z[i]
            );

            var hitResult = level.clip(new ClipContext(
                    eyePos,
                    samplePoint,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    context
            ));

            if (hitResult.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }
}
