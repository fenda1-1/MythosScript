package com.zszl.zszlScriptMod.shadowbaritone.pathing.portal;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class EdgePortal {

    public static final double DEFAULT_PLAYER_HALF_WIDTH = 0.299D;
    public static final double DEFAULT_EXTRA_CLEARANCE = 0.04D;

    private final BetterBlockPos barrierA;
    private final BetterBlockPos barrierB;
    private final BetterBlockPos boundaryAnchor;
    private final EnumFacing boundaryFacing;
    private final EnumFacing travelFacing;
    private final AxisAlignedBB barrierBoxA;
    private final AxisAlignedBB barrierBoxB;
    private final double width;
    private final double height;
    private final Vec3d gapCenter;

    public EdgePortal(BetterBlockPos barrierA, BetterBlockPos barrierB, BetterBlockPos boundaryAnchor,
            EnumFacing boundaryFacing, EnumFacing travelFacing, AxisAlignedBB barrierBoxA, AxisAlignedBB barrierBoxB,
            double width, double height, Vec3d gapCenter) {
        this.barrierA = barrierA;
        this.barrierB = barrierB;
        this.boundaryAnchor = boundaryAnchor;
        this.boundaryFacing = boundaryFacing;
        this.travelFacing = travelFacing;
        this.barrierBoxA = barrierBoxA;
        this.barrierBoxB = barrierBoxB;
        this.width = width;
        this.height = height;
        this.gapCenter = gapCenter;
    }

    public BetterBlockPos getBarrierA() {
        return barrierA;
    }

    public BetterBlockPos getBarrierB() {
        return barrierB;
    }

    public BetterBlockPos getBoundaryAnchor() {
        return boundaryAnchor;
    }

    public EnumFacing getBoundaryFacing() {
        return boundaryFacing;
    }

    public EnumFacing getTravelFacing() {
        return travelFacing;
    }

    public AxisAlignedBB getBarrierBoxA() {
        return barrierBoxA;
    }

    public AxisAlignedBB getBarrierBoxB() {
        return barrierBoxB;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public Vec3d getGapCenter() {
        return gapCenter;
    }

    public PortalRoute createRoute(Vec3d srcCenter, Vec3d destCenter) {
        return createRoute(srcCenter, destCenter, DEFAULT_PLAYER_HALF_WIDTH, DEFAULT_EXTRA_CLEARANCE);
    }

    public PortalRoute createRoute(Vec3d srcCenter, Vec3d destCenter, double playerHalfWidth, double extraClearance) {
        Vec3d[] approach = routeFromCenterToGap(srcCenter, playerHalfWidth, extraClearance);
        Vec3d[] exit = routeFromGapToCenter(destCenter, playerHalfWidth, extraClearance);
        Vec3d entryPoint = approach[approach.length - 2];
        Vec3d exitPoint = exit[1];
        return new PortalRoute(entryPoint, gapCenter, exitPoint, concat(approach, exit));
    }

    public Vec3d[] routeFromCenterToGap(Vec3d center) {
        return routeFromCenterToGap(center, DEFAULT_PLAYER_HALF_WIDTH, DEFAULT_EXTRA_CLEARANCE);
    }

    public Vec3d[] routeFromCenterToGap(Vec3d center, double playerHalfWidth, double extraClearance) {
        double approachMargin = playerHalfWidth + extraClearance;
        if (travelFacing.getAxis() == EnumFacing.Axis.Z) {
            double minZ = Math.min(barrierBoxA.minZ, barrierBoxB.minZ);
            double maxZ = Math.max(barrierBoxA.maxZ, barrierBoxB.maxZ);
            Vec3d aligned = new Vec3d(gapCenter.x, gapCenter.y, center.z);
            Vec3d entryPoint = new Vec3d(
                    gapCenter.x,
                    gapCenter.y,
                    center.z < gapCenter.z ? minZ - approachMargin : maxZ + approachMargin);
            return compact(center, aligned, entryPoint, gapCenter);
        }
        double minX = Math.min(barrierBoxA.minX, barrierBoxB.minX);
        double maxX = Math.max(barrierBoxA.maxX, barrierBoxB.maxX);
        Vec3d aligned = new Vec3d(center.x, gapCenter.y, gapCenter.z);
        Vec3d entryPoint = new Vec3d(
                center.x < gapCenter.x ? minX - approachMargin : maxX + approachMargin,
                gapCenter.y,
                gapCenter.z);
        return compact(center, aligned, entryPoint, gapCenter);
    }

    public Vec3d[] routeFromGapToCenter(Vec3d center) {
        return routeFromGapToCenter(center, DEFAULT_PLAYER_HALF_WIDTH, DEFAULT_EXTRA_CLEARANCE);
    }

    public Vec3d[] routeFromGapToCenter(Vec3d center, double playerHalfWidth, double extraClearance) {
        double approachMargin = playerHalfWidth + extraClearance;
        if (travelFacing.getAxis() == EnumFacing.Axis.Z) {
            double minZ = Math.min(barrierBoxA.minZ, barrierBoxB.minZ);
            double maxZ = Math.max(barrierBoxA.maxZ, barrierBoxB.maxZ);
            Vec3d exitPoint = new Vec3d(
                    gapCenter.x,
                    gapCenter.y,
                    center.z < gapCenter.z ? minZ - approachMargin : maxZ + approachMargin);
            Vec3d aligned = new Vec3d(gapCenter.x, gapCenter.y, center.z);
            return compact(gapCenter, exitPoint, aligned, center);
        }
        double minX = Math.min(barrierBoxA.minX, barrierBoxB.minX);
        double maxX = Math.max(barrierBoxA.maxX, barrierBoxB.maxX);
        Vec3d exitPoint = new Vec3d(
                center.x < gapCenter.x ? minX - approachMargin : maxX + approachMargin,
                gapCenter.y,
                gapCenter.z);
        Vec3d aligned = new Vec3d(center.x, gapCenter.y, gapCenter.z);
        return compact(gapCenter, exitPoint, aligned, center);
    }

    private static Vec3d[] concat(Vec3d[] first, Vec3d[] second) {
        List<Vec3d> points = new ArrayList<>();
        append(points, first);
        append(points, second);
        return points.toArray(new Vec3d[0]);
    }

    private static void append(List<Vec3d> points, Vec3d[] segment) {
        if (segment == null) {
            return;
        }
        for (Vec3d point : segment) {
            if (point == null) {
                continue;
            }
            if (points.isEmpty() || points.get(points.size() - 1).squareDistanceTo(point) > 1.0E-4D) {
                points.add(point);
            }
        }
    }

    private static Vec3d[] compact(Vec3d... route) {
        List<Vec3d> compacted = new ArrayList<>();
        append(compacted, route);
        return compacted.toArray(new Vec3d[0]);
    }
}
