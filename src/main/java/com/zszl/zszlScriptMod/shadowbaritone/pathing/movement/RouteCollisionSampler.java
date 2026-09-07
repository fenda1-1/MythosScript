package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class RouteCollisionSampler {

    public static final double DEFAULT_PLAYER_HALF_WIDTH = 0.299D;
    public static final int DEFAULT_SAMPLE_COUNT = 36;

    private RouteCollisionSampler() {
    }

    public static boolean isRouteClear(CalculationContext context, Vec3d[] routePoints, double minY, double maxY,
            boolean requireNarrowCollision) {
        return isRouteClear(context, routePoints, minY, maxY, DEFAULT_PLAYER_HALF_WIDTH, DEFAULT_SAMPLE_COUNT,
                requireNarrowCollision);
    }

    public static boolean isRouteClear(CalculationContext context, Vec3d[] routePoints, double minY, double maxY,
            double playerHalfWidth, int sampleCount, boolean requireNarrowCollision) {
        List<AxisAlignedBB> blockingBoxes = collectBlockingBoxes(context, routePoints, minY, maxY, requireNarrowCollision);
        if (blockingBoxes.isEmpty()) {
            return !requireNarrowCollision;
        }
        return isRouteClear(blockingBoxes, routePoints, minY, maxY, playerHalfWidth, sampleCount);
    }

    public static boolean isRouteClear(List<AxisAlignedBB> blockingBoxes, Vec3d[] routePoints, double minY,
            double maxY, double playerHalfWidth, int sampleCount) {
        if (routePoints == null || routePoints.length < 2) {
            return false;
        }
        if (blockingBoxes == null || blockingBoxes.isEmpty()) {
            return true;
        }
        for (int i = 0; i < routePoints.length - 1; i++) {
            Vec3d start = routePoints[i];
            Vec3d end = routePoints[i + 1];
            if (!isSegmentClear(blockingBoxes, start.x, start.z, end.x, end.z, minY, maxY, playerHalfWidth,
                    sampleCount)) {
                return false;
            }
        }
        return true;
    }

    public static List<AxisAlignedBB> collectBlockingBoxes(CalculationContext context, Vec3d[] routePoints, double minY,
            double maxY, boolean requireNarrowCollision) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        if (routePoints == null || routePoints.length == 0) {
            return boxes;
        }
        double minRouteX = routePoints[0].x;
        double maxRouteX = routePoints[0].x;
        double minRouteZ = routePoints[0].z;
        double maxRouteZ = routePoints[0].z;
        for (Vec3d point : routePoints) {
            if (point == null) {
                continue;
            }
            minRouteX = Math.min(minRouteX, point.x);
            maxRouteX = Math.max(maxRouteX, point.x);
            minRouteZ = Math.min(minRouteZ, point.z);
            maxRouteZ = Math.max(maxRouteZ, point.z);
        }
        int minX = MathHelper.floor(minRouteX) - 1;
        int maxX = MathHelper.floor(maxRouteX) + 1;
        int minZ = MathHelper.floor(minRouteZ) - 1;
        int maxZ = MathHelper.floor(maxRouteZ) + 1;
        int minBlockY = MathHelper.floor(minY);
        int maxBlockY = MathHelper.floor(maxY);
        boolean foundNarrowCollision = false;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minBlockY; by <= maxBlockY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    IBlockState state = context.get(bx, by, bz);
                    if (state == null || MovementHelper.canWalkThrough(context, bx, by, bz, state)) {
                        continue;
                    }
                    AxisAlignedBB box = getLocalCollisionBox(context, new BetterBlockPos(bx, by, bz), state);
                    if (box == null
                            || box.maxX - box.minX <= 1.0E-4D
                            || box.maxY - box.minY <= 1.0E-4D
                            || box.maxZ - box.minZ <= 1.0E-4D) {
                        continue;
                    }
                    if ((box.maxX - box.minX) < 0.95D || (box.maxZ - box.minZ) < 0.95D) {
                        foundNarrowCollision = true;
                    }
                    boxes.add(box.offset(bx, by, bz));
                }
            }
        }
        if (requireNarrowCollision && !foundNarrowCollision) {
            return new ArrayList<>();
        }
        return boxes;
    }

    private static boolean isSegmentClear(List<AxisAlignedBB> blockingBoxes, double startX, double startZ, double endX,
            double endZ, double minY, double maxY, double playerHalfWidth, int sampleCount) {
        for (int sample = 0; sample <= sampleCount; sample++) {
            double t = sample / (double) sampleCount;
            double centerX = startX + (endX - startX) * t;
            double centerZ = startZ + (endZ - startZ) * t;
            AxisAlignedBB playerBox = new AxisAlignedBB(
                    centerX - playerHalfWidth, minY, centerZ - playerHalfWidth,
                    centerX + playerHalfWidth, maxY, centerZ + playerHalfWidth);
            for (AxisAlignedBB blockingBox : blockingBoxes) {
                if (playerBox.intersects(blockingBox)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AxisAlignedBB getLocalCollisionBox(CalculationContext context, BetterBlockPos pos, IBlockState state) {
        try {
            return state.getBoundingBox(context.bsi.access, new BlockPos(pos.x, pos.y, pos.z));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
