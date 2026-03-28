package me.lightsing.minecraft.ghosty;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class VisibilityChecker {
    public static boolean isVisible(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        if (target == viewer || target.isRemoved()) {
            return true;
        }

        // fast distance check
        if (!isTargetInRange(viewer, target, isCurrentlyKnownVisible)) {
            return false;
        }

        // run raycast checks for very close entities to allow them to be visible even if they are technically outside the frustum
        if (shouldBypassFovCheck(viewer, target)) {
            return isVisible(viewer, target);
        }

        // frustum check
        if (!isBoundingBoxInFov(viewer, target, isCurrentlyKnownVisible)) {
            return false;
        }

        // perform sight check (more strict, if this passes, the entity is definitely visible)
        if (target instanceof LivingEntity livingTarget && viewer.hasLineOfSight(livingTarget)) {
            return true;
        }

        return isVisible(viewer, target);
    }

    // ---- Fast distance checks to avoid expensive frustum checks when entities are far away ----

    private static boolean isTargetInRange(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        GhostyConfig.TrackingParameters.RangeParameters params = GhostyConfig.getInstance().trackingParameters().rangeParameters();
        double maxDistanceSq = isCurrentlyKnownVisible ? params.exitDistanceSq() : params.enterDistanceSq();
        return viewer.distanceToSqr(target) <= maxDistanceSq;
    }

    // --- Very close entities should be visible regardless of frustum checks ---
    private static boolean shouldBypassFovCheck(ServerPlayer viewer, Entity target) {
        return viewer.distanceToSqr(target) <= GhostyConfig.getInstance().trackingParameters().rangeParameters().bypassFovDistanceSq();
    }

    // ---- Frustum angle checks to only display entities that are roughly in front of the player ----
    private static boolean isBoundingBoxInFov(ServerPlayer viewer, Entity target, boolean isCurrentlyKnownVisible) {
        GhostyConfig.TrackingParameters.FovParameters params = GhostyConfig.getInstance().trackingParameters().fovParameters();
        double threshold = isCurrentlyKnownVisible ? params.exitAngleCos() : params.enterAngleCos();

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
        return isAnyCornerInFov(viewer, box, threshold);
    }

    private static boolean isAnyCornerInFov(ServerPlayer viewer, AABB box, double threshold) {
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
    private static boolean isVisible(ServerPlayer viewer, Entity target) {
        ServerLevel level = viewer.serverLevel();

        AABB box = target.getBoundingBox().inflate(0.2);
        Vec3 eyePos = viewer.getEyePosition();
        CollisionContext context = CollisionContext.of(viewer);


        for (Vec3 sample : GhostyConfig.getInstance().trackingParameters().rayCastSamples()) {
            Vec3 samplePoint = new Vec3(
                    box.minX + (box.maxX - box.minX) * sample.x,
                    box.minY + (box.maxY - box.minY) * sample.y,
                    box.minZ + (box.maxZ - box.minZ) * sample.z
            );

            var hitResult = hasLineOfSight(level, eyePos, samplePoint, context);
            if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockHitResult hasLineOfSight(ServerLevel level, Vec3 start, Vec3 end, CollisionContext context) {
        return BlockGetter.traverseBlocks(
                start,
                end,
                context,
                (ctx, pos) -> {
                    BlockState state = level.getBlockState(pos);

                    VoxelShape shape = state.getVisualShape(level, pos, ctx);
                    if (shape.isEmpty()) {
                        return null;
                    }

                    BlockHitResult hit = shape.clip(start, end, pos);
                    if (hit == null || hit.getType() == HitResult.Type.MISS) {
                        return null;
                    }


                    if (state.is(BlockTags.DOORS) ||
                            state.is(BlockTags.FENCES) ||
                            state.is(BlockTags.WALLS) ||
                            state.is(BlockTags.TRAPDOORS) ||
                            state.is(BlockTags.LEAVES)) {
                        return null;
                    }

                    if (state.getLightBlock(level, pos) == 0 && !state.canOcclude()) {
                        return null;
                    }
                    return hit;
                },
                (ctx) -> null
        );
    }
}
