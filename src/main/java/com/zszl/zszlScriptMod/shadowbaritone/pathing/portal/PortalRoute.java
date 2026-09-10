package com.zszl.zszlScriptMod.shadowbaritone.pathing.portal;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class PortalRoute {

    private final Vec3d entryPoint;
    private final Vec3d gapCenter;
    private final Vec3d exitPoint;
    private final Vec3d[] points;
    private final double length;
    private final double costToGapCenter;
    private final double costFromGapCenterToEnd;

    public PortalRoute(Vec3d entryPoint, Vec3d gapCenter, Vec3d exitPoint, Vec3d... points) {
        this.entryPoint = entryPoint;
        this.gapCenter = gapCenter;
        this.exitPoint = exitPoint;
        this.points = compact(points);
        double total = 0.0D;
        double beforeGap = 0.0D;
        boolean passedGap = false;
        for (int i = 0; i < this.points.length - 1; i++) {
            double segment = this.points[i].distanceTo(this.points[i + 1]);
            total += segment;
            if (!passedGap) {
                beforeGap += segment;
            }
            if (this.points[i + 1].squareDistanceTo(gapCenter) <= 1.0E-4D) {
                passedGap = true;
            }
        }
        this.length = total;
        this.costToGapCenter = beforeGap;
        this.costFromGapCenterToEnd = total - beforeGap;
    }

    public Vec3d getEntryPoint() {
        return entryPoint;
    }

    public Vec3d getGapCenter() {
        return gapCenter;
    }

    public Vec3d getExitPoint() {
        return exitPoint;
    }

    public Vec3d[] getPoints() {
        return points.clone();
    }

    public double getLength() {
        return length;
    }

    public double getCostToGapCenter() {
        return costToGapCenter;
    }

    public double getCostFromGapCenterToEnd() {
        return costFromGapCenterToEnd;
    }

    private static Vec3d[] compact(Vec3d... route) {
        List<Vec3d> compacted = new ArrayList<>();
        if (route == null) {
            return new Vec3d[0];
        }
        for (Vec3d point : route) {
            if (point == null) {
                continue;
            }
            if (compacted.isEmpty() || compacted.get(compacted.size() - 1).squareDistanceTo(point) > 1.0E-4D) {
                compacted.add(point);
            }
        }
        return compacted.toArray(new Vec3d[0]);
    }
}
