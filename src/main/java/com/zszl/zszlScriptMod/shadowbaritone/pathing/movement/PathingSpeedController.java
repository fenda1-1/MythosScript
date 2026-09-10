package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.path.IPathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.client.entity.EntityPlayerSP;

import java.util.List;

/**
 * Calculates a forward-only speed limit for normal Baritone paths. The limit is
 * applied before a corner, so movement does not need to reverse after passing a
 * block-center target at high speed.
 */
public final class PathingSpeedController {

    private static final double DIRECTION_EPSILON = 1.0E-4D;
    private static final double STRAIGHT_DOT_THRESHOLD = 0.985D;
    private static final double MIN_SAFE_SPEED = 0.06D;
    private static final double CORNER_MARGIN_BASE = 0.32D;
    private static final double CORNER_MARGIN_SPEED_FACTOR = 0.45D;

    private PathingSpeedController() {
    }

    public static MotionPlan plan(EntityPlayerSP player, double requestedSpeed) {
        if (player == null || !BaritoneAPI.getSettings().adaptivePathingSpeedControl.value) {
            return MotionPlan.inactive(requestedSpeed);
        }

        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(player);
        if (baritone == null || !baritone.getPathingBehavior().isPathing()) {
            return MotionPlan.inactive(requestedSpeed);
        }

        IPathExecutor executor = baritone.getPathingBehavior().getCurrent();
        if (executor == null || executor.getPath() == null) {
            return MotionPlan.inactive(requestedSpeed);
        }

        IPath path = executor.getPath();
        List<IMovement> movements = path.movements();
        int currentIndex = executor.getPosition();
        if (currentIndex < 0 || currentIndex >= movements.size()) {
            return MotionPlan.inactive(requestedSpeed);
        }

        IMovement current = movements.get(currentIndex);
        Direction currentDirection = Direction.between(current.getSrc(), current.getDest());
        if (currentDirection == null) {
            return MotionPlan.inactive(requestedSpeed);
        }

        Corner corner = findNextCorner(movements, currentIndex, currentDirection);
        if (corner == null) {
            return MotionPlan.activeWithoutCorner(requestedSpeed);
        }

        double horizontalSpeed = horizontalSpeed(player);
        double distanceToCorner = forwardDistance(player, corner.position, currentDirection);
        double turnSeverity = Math.max(0.0D, Math.min(1.0D, 1.0D - currentDirection.dot(corner.nextDirection)));
        if (turnSeverity <= 0.0D) {
            return MotionPlan.activeWithoutCorner(requestedSpeed);
        }

        double brakingAcceleration = clamp(BaritoneAPI.getSettings().adaptivePathingBrakeAcceleration.value,
                0.03D, 1.50D);
        double cornerSpeed = clamp(BaritoneAPI.getSettings().adaptivePathingCornerSpeed.value,
                MIN_SAFE_SPEED, Math.max(MIN_SAFE_SPEED, requestedSpeed));
        double margin = CORNER_MARGIN_BASE + horizontalSpeed * CORNER_MARGIN_SPEED_FACTOR;
        double stoppingDistance = horizontalSpeed * horizontalSpeed / (2.0D * brakingAcceleration);
        double usableDistance = Math.max(0.0D, distanceToCorner - margin);
        double brakingSpeed = Math.sqrt(2.0D * brakingAcceleration * usableDistance);
        double severityAdjustedSpeed = requestedSpeed + (Math.max(cornerSpeed, brakingSpeed) - requestedSpeed)
                * turnSeverity;
        double limitedSpeed = Math.max(MIN_SAFE_SPEED, Math.min(requestedSpeed, severityAdjustedSpeed));
        boolean pastCorner = distanceToCorner < -0.08D;
        boolean braking = pastCorner || distanceToCorner <= stoppingDistance + margin + 0.25D;
        boolean hardBrake = pastCorner || horizontalSpeed > limitedSpeed + 0.08D;
        return new MotionPlan(true, braking, hardBrake, limitedSpeed, distanceToCorner, turnSeverity);
    }

    public static boolean shouldHoldSprint(EntityPlayerSP player) {
        double requestedSpeed = Math.max(0.45D, horizontalSpeed(player));
        return plan(player, requestedSpeed).isBraking();
    }

    private static Corner findNextCorner(List<IMovement> movements, int startIndex, Direction initialDirection) {
        BetterBlockPos lastForwardPosition = movements.get(startIndex).getDest();
        for (int index = startIndex + 1; index < movements.size(); index++) {
            IMovement movement = movements.get(index);
            Direction direction = Direction.between(movement.getSrc(), movement.getDest());
            if (direction == null) {
                continue;
            }
            if (initialDirection.dot(direction) >= STRAIGHT_DOT_THRESHOLD) {
                lastForwardPosition = movement.getDest();
                continue;
            }
            return new Corner(lastForwardPosition, direction);
        }
        return null;
    }

    private static double forwardDistance(EntityPlayerSP player, BetterBlockPos target, Direction direction) {
        double dx = target.getX() + 0.5D - player.posX;
        double dz = target.getZ() + 0.5D - player.posZ;
        return dx * direction.x + dz * direction.z;
    }

    private static double horizontalSpeed(EntityPlayerSP player) {
        if (player == null) {
            return 0.0D;
        }
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Corner {
        private final BetterBlockPos position;
        private final Direction nextDirection;

        private Corner(BetterBlockPos position, Direction nextDirection) {
            this.position = position;
            this.nextDirection = nextDirection;
        }
    }

    private static final class Direction {
        private final double x;
        private final double z;

        private Direction(double x, double z) {
            this.x = x;
            this.z = z;
        }

        private static Direction between(BetterBlockPos src, BetterBlockPos dest) {
            if (src == null || dest == null) {
                return null;
            }
            double dx = dest.getX() - src.getX();
            double dz = dest.getZ() - src.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length <= DIRECTION_EPSILON) {
                return null;
            }
            return new Direction(dx / length, dz / length);
        }

        private double dot(Direction other) {
            return other == null ? 1.0D : x * other.x + z * other.z;
        }
    }

    public static final class MotionPlan {
        private final boolean active;
        private final boolean braking;
        private final boolean hardBrake;
        private final double targetSpeed;
        private final double distanceToCorner;
        private final double turnSeverity;

        private MotionPlan(boolean active, boolean braking, boolean hardBrake, double targetSpeed,
                double distanceToCorner, double turnSeverity) {
            this.active = active;
            this.braking = braking;
            this.hardBrake = hardBrake;
            this.targetSpeed = targetSpeed;
            this.distanceToCorner = distanceToCorner;
            this.turnSeverity = turnSeverity;
        }

        private static MotionPlan inactive(double requestedSpeed) {
            return new MotionPlan(false, false, false, requestedSpeed, Double.POSITIVE_INFINITY, 0.0D);
        }

        private static MotionPlan activeWithoutCorner(double requestedSpeed) {
            return new MotionPlan(true, false, false, requestedSpeed, Double.POSITIVE_INFINITY, 0.0D);
        }

        public boolean isActive() {
            return active;
        }

        public boolean isBraking() {
            return braking;
        }

        public boolean isHardBrake() {
            return hardBrake;
        }

        public double getTargetSpeed() {
            return targetSpeed;
        }

        public double getDistanceToCorner() {
            return distanceToCorner;
        }

        public double getTurnSeverity() {
            return turnSeverity;
        }
    }
}
