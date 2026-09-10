package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import net.minecraft.util.math.Vec3d;

public final class RouteFollowHelper {

    private static final double VANILLA_REFERENCE_HORIZONTAL_SPEED = 0.2873D;
    private static final double MIN_DIRECTION_EPSILON_SQ = 1.0E-6D;

    private RouteFollowHelper() {
    }

    public static double distanceSqToRoute(Vec3d[] routePoints, Vec3d position) {
        RouteProjection projection = project(routePoints, position);
        return projection == null ? Double.POSITIVE_INFINITY : projection.distanceSq;
    }

    public static Vec3d getTargetPoint(Vec3d[] routePoints, Vec3d position, double lookAheadDistance) {
        if (routePoints == null || routePoints.length == 0) {
            return position;
        }
        if (routePoints.length == 1) {
            return routePoints[0];
        }
        RouteProjection projection = project(routePoints, position);
        if (projection == null) {
            return routePoints[routePoints.length - 1];
        }
        double targetDistance = Math.min(projection.totalLength, projection.distanceAlongRoute + lookAheadDistance);
        return pointAtDistance(routePoints, targetDistance, projection.totalLength);
    }

    public static FollowCommand getFollowCommand(Vec3d[] routePoints, Vec3d position, Vec3d velocity,
            double baseLookAheadDistance) {
        return getFollowCommand(routePoints, position, velocity, baseLookAheadDistance,
                baseLookAheadDistance + 0.42D, 1.15D, 0.70D);
    }

    public static FollowCommand getFollowCommand(Vec3d[] routePoints, Vec3d position, Vec3d velocity,
            double baseLookAheadDistance, double maxLookAheadDistance, double lateralErrorGain,
            double lateralVelocityGain) {
        if (routePoints == null || routePoints.length == 0) {
            return FollowCommand.stationary(position);
        }
        if (routePoints.length == 1) {
            Vec3d desiredDirection = horizontalNormalize(routePoints[0].subtract(position));
            if (desiredDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
                desiredDirection = new Vec3d(1.0D, 0.0D, 0.0D);
            }
            return new FollowCommand(routePoints[0], desiredDirection, 0.0D, 0.0D, 0.0D);
        }

        RouteProjection projection = project(routePoints, position);
        if (projection == null) {
            Vec3d fallbackTarget = routePoints[routePoints.length - 1];
            Vec3d desiredDirection = horizontalNormalize(fallbackTarget.subtract(position));
            if (desiredDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
                desiredDirection = horizontalNormalize(routePoints[routePoints.length - 1].subtract(routePoints[0]));
            }
            if (desiredDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
                desiredDirection = new Vec3d(1.0D, 0.0D, 0.0D);
            }
            return new FollowCommand(fallbackTarget, desiredDirection, 0.0D, 0.0D, 0.0D);
        }

        double horizontalSpeed = horizontalLength(velocity);
        double speedRatio = horizontalSpeed / VANILLA_REFERENCE_HORIZONTAL_SPEED;
        double speedFactor = Math.max(1.0D, Math.sqrt(Math.max(1.0D, speedRatio)));
        double dynamicLookAhead = clamp(baseLookAheadDistance * (0.90D + speedFactor * 0.45D),
                baseLookAheadDistance, Math.max(baseLookAheadDistance, maxLookAheadDistance));

        double targetDistance = Math.min(projection.totalLength, projection.distanceAlongRoute + dynamicLookAhead);
        Vec3d previewPoint = pointAtDistance(routePoints, targetDistance, projection.totalLength);

        Vec3d routeDirection = horizontalNormalize(previewPoint.subtract(projection.point));
        if (routeDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
            routeDirection = horizontalNormalize(previewPoint.subtract(position));
        }
        if (routeDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
            routeDirection = projection.segmentDirection;
        }
        if (routeDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
            routeDirection = horizontalNormalize(routePoints[routePoints.length - 1].subtract(routePoints[0]));
        }
        if (routeDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
            routeDirection = new Vec3d(1.0D, 0.0D, 0.0D);
        }

        Vec3d routeNormal = new Vec3d(-routeDirection.z, 0.0D, routeDirection.x);
        Vec3d lateralOffset = horizontalOnly(position.subtract(projection.point));
        Vec3d horizontalVelocity = horizontalOnly(velocity);

        double lateralError = lateralOffset.dotProduct(routeNormal);
        double lateralVelocity = horizontalVelocity.dotProduct(routeNormal);

        double correctionDistance = Math.max(0.30D, dynamicLookAhead * 0.85D);
        double errorCorrection = -lateralError / correctionDistance;
        double velocityCorrection = -lateralVelocity
                / Math.max(VANILLA_REFERENCE_HORIZONTAL_SPEED * 0.75D, horizontalSpeed + 0.02D);
        double lateralComponent = clamp(errorCorrection * lateralErrorGain + velocityCorrection * lateralVelocityGain,
                -0.92D, 0.92D);

        Vec3d desiredDirection = horizontalNormalize(routeDirection.add(routeNormal.scale(lateralComponent)));
        if (desiredDirection.lengthSquared() <= MIN_DIRECTION_EPSILON_SQ) {
            desiredDirection = routeDirection;
        }

        return new FollowCommand(previewPoint, desiredDirection, dynamicLookAhead, lateralError, lateralVelocity);
    }

    private static RouteProjection project(Vec3d[] routePoints, Vec3d position) {
        if (routePoints == null || routePoints.length == 0) {
            return null;
        }
        if (routePoints.length == 1) {
            return new RouteProjection(routePoints[0], position.squareDistanceTo(routePoints[0]), 0.0D, 0.0D,
                    new Vec3d(1.0D, 0.0D, 0.0D));
        }
        double[] segmentLengths = new double[routePoints.length - 1];
        double totalLength = 0.0D;
        for (int i = 0; i < routePoints.length - 1; i++) {
            double segmentLength = routePoints[i].distanceTo(routePoints[i + 1]);
            segmentLengths[i] = segmentLength;
            totalLength += segmentLength;
        }
        double traversed = 0.0D;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        double bestDistanceAlong = 0.0D;
        Vec3d bestPoint = routePoints[0];
        Vec3d bestSegmentDirection = Vec3d.ZERO;
        for (int i = 0; i < routePoints.length - 1; i++) {
            Vec3d start = routePoints[i];
            Vec3d end = routePoints[i + 1];
            double segmentLength = segmentLengths[i];
            Vec3d nearest = nearestPointOnSegment(position, start, end);
            double distanceSq = position.squareDistanceTo(nearest);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestPoint = nearest;
                bestSegmentDirection = horizontalNormalize(end.subtract(start));
                if (segmentLength <= 1.0E-6D) {
                    bestDistanceAlong = traversed;
                } else {
                    bestDistanceAlong = traversed + nearest.distanceTo(start);
                }
            }
            traversed += segmentLength;
        }
        return new RouteProjection(bestPoint, bestDistanceSq, bestDistanceAlong, totalLength, bestSegmentDirection);
    }

    private static Vec3d pointAtDistance(Vec3d[] routePoints, double targetDistance, double totalLength) {
        if (targetDistance <= 0.0D || totalLength <= 1.0E-6D) {
            return routePoints[0];
        }
        double traversed = 0.0D;
        for (int i = 0; i < routePoints.length - 1; i++) {
            Vec3d start = routePoints[i];
            Vec3d end = routePoints[i + 1];
            double segmentLength = start.distanceTo(end);
            if (segmentLength <= 1.0E-6D) {
                continue;
            }
            if (targetDistance <= traversed + segmentLength) {
                double t = (targetDistance - traversed) / segmentLength;
                return start.add(end.subtract(start).scale(t));
            }
            traversed += segmentLength;
        }
        return routePoints[routePoints.length - 1];
    }

    private static Vec3d nearestPointOnSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();
        if (lengthSq <= 1.0E-6D) {
            return start;
        }
        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return start.add(segment.scale(t));
    }

    private static Vec3d horizontalOnly(Vec3d vec) {
        return new Vec3d(vec.x, 0.0D, vec.z);
    }

    private static Vec3d horizontalNormalize(Vec3d vec) {
        Vec3d horizontal = horizontalOnly(vec);
        double lengthSq = horizontal.lengthSquared();
        if (lengthSq <= MIN_DIRECTION_EPSILON_SQ) {
            return Vec3d.ZERO;
        }
        return horizontal.scale(1.0D / Math.sqrt(lengthSq));
    }

    private static double horizontalLength(Vec3d vec) {
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class FollowCommand {

        private final Vec3d previewPoint;
        private final Vec3d desiredDirection;
        private final double lookAheadDistance;
        private final double lateralError;
        private final double lateralVelocity;

        private FollowCommand(Vec3d previewPoint, Vec3d desiredDirection, double lookAheadDistance,
                double lateralError, double lateralVelocity) {
            this.previewPoint = previewPoint;
            this.desiredDirection = desiredDirection;
            this.lookAheadDistance = lookAheadDistance;
            this.lateralError = lateralError;
            this.lateralVelocity = lateralVelocity;
        }

        private static FollowCommand stationary(Vec3d position) {
            return new FollowCommand(position, new Vec3d(1.0D, 0.0D, 0.0D), 0.0D, 0.0D, 0.0D);
        }

        public Vec3d getPreviewPoint() {
            return previewPoint;
        }

        public Vec3d getDesiredDirection() {
            return desiredDirection;
        }

        public double getLookAheadDistance() {
            return lookAheadDistance;
        }

        public double getLateralError() {
            return lateralError;
        }

        public double getLateralVelocity() {
            return lateralVelocity;
        }
    }

    private static final class RouteProjection {
        private final Vec3d point;
        private final double distanceSq;
        private final double distanceAlongRoute;
        private final double totalLength;
        private final Vec3d segmentDirection;

        private RouteProjection(Vec3d point, double distanceSq, double distanceAlongRoute, double totalLength,
                Vec3d segmentDirection) {
            this.point = point;
            this.distanceSq = distanceSq;
            this.distanceAlongRoute = distanceAlongRoute;
            this.totalLength = totalLength;
            this.segmentDirection = segmentDirection;
        }
    }
}
