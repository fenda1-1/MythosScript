package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.handlers.FlightCorridorCollisionHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.FlightDirectPath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.RotationUtils;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.Set;

/**
 * Flies through a wide corridor without chasing its center line. The primary
 * segment direction is retained and perpendicular correction begins only after
 * the player leaves the configured dead zone.
 */
public final class MovementFlightCorridor extends Movement implements IRoutePointMovement {

    private static final double MAX_CORRECTION = 0.45D;
    private static final double MAX_RECOVERY_DISTANCE = 12.0D;
    private static final double MAX_CORNER_RECOVERY_SPEED = 1.25D;
    private static final int STALL_REPLAN_TICKS = 30;

    private final Vec3d startCenter;
    private final Vec3d endCenter;
    private final Vec3d segment;
    private final double segmentLength;
    private final boolean vertical;
    private double bestProgress = Double.NEGATIVE_INFINITY;
    private double bestCorridorDistance = Double.POSITIVE_INFINITY;
    private double bestHeightError = Double.POSITIVE_INFINITY;
    private int stalledTicks;
    private double corridorRadius;
    private int checkedCorridorWidth = -1;
    private Vec3d requestedDirection = Vec3d.ZERO;
    private double horizontalCap;
    private double verticalCap;

    public MovementFlightCorridor(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[0]);
        this.startCenter = center(src);
        this.endCenter = center(dest);
        this.segment = endCenter.subtract(startCenter);
        this.segmentLength = segment.lengthVector();
        this.vertical = src.x == dest.x && src.z == dest.z;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return calculateCost(context, src);
    }

    @Override
    public double recalculateCost(CalculationContext context) {
        if (ctx.player() != null && hasCompletedSegment()) {
            double speed = vertical
                    ? Math.max(0.05D, FlyHandler.verticalSpeed)
                    : Math.max(0.05D, FlyHandler.horizontalSpeed);
            double completedCost = Math.max(1.0D, segmentLength / speed + 20.0D);
            override(completedCost);
            return completedCost;
        }
        double cost = calculateCost(context, validationStart());
        override(cost);
        return cost;
    }

    private double calculateCost(CalculationContext context, BetterBlockPos validationStart) {
        if (!context.getBaritone().getPlayerContext().player().capabilities.allowFlying && !FlyHandler.enabled) {
            return COST_INF;
        }
        if (!FlightDirectPath.isSegmentLoadedAndClear(context, validationStart, dest)) {
            return COST_INF;
        }
        int width = Math.max(1, Math.min(9, Baritone.settings().flightCorridorWidth.value));
        if (checkedCorridorWidth != width) {
            corridorRadius = FlightDirectPath.isSegmentLoadedAndClear(context, validationStart, dest, true)
                    ? width / 2.0D : 0.0D;
            checkedCorridorWidth = width;
        } else if (corridorRadius > 0.0D) {
            // Recheck the approaching volume; scanning a whole wide route every tick
            // becomes expensive at high corridor widths. New obstacles trigger a detour.
            int lookahead = (int) Math.ceil(Math.max(FlyHandler.horizontalSpeed, FlyHandler.verticalSpeed) * 2.0D) + 2;
            BetterBlockPos checkEnd = new BetterBlockPos(
                    validationStart.x + Integer.signum(dest.x - validationStart.x)
                            * Math.min(lookahead, Math.abs(dest.x - validationStart.x)),
                    validationStart.y + Integer.signum(dest.y - validationStart.y)
                            * Math.min(lookahead, Math.abs(dest.y - validationStart.y)),
                    validationStart.z + Integer.signum(dest.z - validationStart.z)
                            * Math.min(lookahead, Math.abs(dest.z - validationStart.z)));
            if (!FlightDirectPath.isSegmentLoadedAndClear(context, validationStart, checkEnd, true)) {
                return COST_INF;
            }
        }
        double speed = vertical
                ? Math.max(0.05D, FlyHandler.verticalSpeed)
                : Math.max(0.05D, FlyHandler.horizontalSpeed);
        return Math.max(1.0D, segmentLength / speed + 20.0D);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    @Override
    protected boolean shouldAutoSwimInLiquid() {
        return false;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            FlightCorridorCollisionHandler.INSTANCE.clear(this);
            return state;
        }
        FlightCorridorCollisionHandler.INSTANCE.activate(this, startCenter, endCenter, getCorridorRadius());
        if (hasCompletedSegment()) {
            FlightCorridorCollisionHandler.INSTANCE.clear(this);
            return state.setStatus(MovementStatus.SUCCESS);
        }
        if (distanceFromCorridor() > getRecoveryDistanceLimit()) {
            FlightCorridorCollisionHandler.INSTANCE.clear(this);
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (updateStallState()) {
            FlightCorridorCollisionHandler.INSTANCE.clear(this);
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (tryOpenInteractionAhead(state)) {
            clearHorizontalInputs(state);
            state.setInput(Input.JUMP, false).setInput(Input.SNEAK, false);
            stopMotion();
            return state;
        }

        if (vertical) {
            applyVerticalCorridor(state);
        } else {
            applyHorizontalCorridor(state);
        }
        applyApproachSpeedCaps();
        if (!isNextStepClear(state)) {
            FlightCorridorCollisionHandler.INSTANCE.clear(this);
            clearHorizontalInputs(state);
            state.setInput(Input.JUMP, false).setInput(Input.SNEAK, false);
            stopMotion();
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        state.setInput(Input.SPRINT, true);
        return state;
    }

    private void applyHorizontalCorridor(MovementState state) {
        Vec3d primary = new Vec3d(segment.x, 0.0D, segment.z).normalize();
        Vec3d offset = ctx.player().getPositionVector().subtract(startCenter);
        Vec3d perpendicular = new Vec3d(-primary.z, 0.0D, primary.x);
        double lateralError = offset.dotProduct(perpendicular);
        double along = offset.dotProduct(primary);
        if (corridorRadius == 0.0D) {
            // Capture the center before advancing into a player-sized opening.
            if (Math.abs(lateralError) > 0.08D) {
                forceForwardDirection(state, perpendicular.scale(-Math.signum(lateralError)));
            } else if (Math.abs(dest.y - ctx.player().posY) > 0.08D) {
                clearHorizontalInputs(state);
            } else {
                forceForwardDirection(state, primary);
            }
            maintainCruiseHeight(state);
            return;
        }
        if (Math.abs(lateralError) > getCorridorRadius() + 0.25D || along < -0.5D) {
            double lookahead = Math.max(2.0D, Math.min(8.0D, FlyHandler.horizontalSpeed * 0.75D));
            double captureAlong = Math.max(0.0D, Math.min(segmentLength, Math.max(0.0D, along) + lookahead));
            Vec3d capturePoint = startCenter.add(primary.scale(captureAlong));
            Vec3d recoveryDirection = new Vec3d(capturePoint.x - ctx.player().posX, 0.0D,
                    capturePoint.z - ctx.player().posZ);
            if (recoveryDirection.lengthSquared() > 1.0E-6D) {
                forceForwardDirection(state, recoveryDirection.normalize());
                maintainCruiseHeight(state);
                return;
            }
        }
        double excess = Math.max(0.0D, Math.abs(lateralError) - getCorridorRadius());
        double correction = Math.min(MAX_CORRECTION, excess / Math.max(1.0D, getCorridorRadius() * 2.0D));
        correction = Math.min(correction,
                (excess + 0.5D) / Math.max(1.0D, FlyHandler.horizontalSpeed));
        Vec3d desired = primary.add(perpendicular.scale(-Math.signum(lateralError) * correction)).normalize();
        forceForwardDirection(state, desired);
        maintainCruiseHeight(state);
    }

    private void applyVerticalCorridor(MovementState state) {
        double dx = startCenter.x - ctx.player().posX;
        double dz = startCenter.z - ctx.player().posZ;
        double horizontalError = Math.sqrt(dx * dx + dz * dz);
        if (horizontalError > Math.max(0.08D, getCorridorRadius())) {
            forceForwardDirection(state, new Vec3d(dx, 0.0D, dz));
        } else {
            clearHorizontalInputs(state);
        }
        boolean aligned = corridorRadius > 0.0D || horizontalError <= 0.08D;
        state.setInput(Input.JUMP, aligned && dest.y > src.y);
        state.setInput(Input.SNEAK, aligned && dest.y < src.y);
    }

    private void maintainCruiseHeight(MovementState state) {
        double yError = dest.y - ctx.player().posY;
        double deadZone = Math.max(0.05D, getCorridorRadius());
        state.setInput(Input.JUMP, yError > deadZone);
        state.setInput(Input.SNEAK, yError < -deadZone);
    }

    private void forceForwardDirection(MovementState state, Vec3d desiredDirection) {
        requestedDirection = desiredDirection.normalize();
        MovementHelper.moveForwardWithRotation(ctx, state, desiredDirection);
        state.setInput(Input.MOVE_FORWARD, true);
        state.setInput(Input.MOVE_BACK, false);
        state.setInput(Input.MOVE_LEFT, false);
        state.setInput(Input.MOVE_RIGHT, false);
    }

    private void clearHorizontalInputs(MovementState state) {
        requestedDirection = Vec3d.ZERO;
        state.setInput(Input.MOVE_FORWARD, false);
        state.setInput(Input.MOVE_BACK, false);
        state.setInput(Input.MOVE_LEFT, false);
        state.setInput(Input.MOVE_RIGHT, false);
    }

    private boolean tryOpenInteractionAhead(MovementState state) {
        if (vertical || ctx.player() == null) {
            return false;
        }
        Vec3d primary = new Vec3d(segment.x, 0.0D, segment.z).normalize();
        for (double distance = 0.4D; distance <= 2.2D; distance += 0.4D) {
            int x = (int) Math.floor(ctx.player().posX + primary.x * distance);
            int z = (int) Math.floor(ctx.player().posZ + primary.z * distance);
            int feetY = (int) Math.floor(ctx.player().posY);
            for (int y = feetY; y <= feetY + 1; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                IBlockState blockState = BlockStateInterface.get(ctx, pos);
                if (!isClosedInteraction(blockState)) {
                    continue;
                }
                if (blockState.getBlock() == Blocks.IRON_DOOR) {
                    Optional<BlockPos> button = MovementHelper.findReachableButtonForIronDoor(ctx, pos);
                    if (button.isPresent()) {
                        MovementHelper.tryRightClickButton(ctx, button.get());
                        return true;
                    }
                    return false;
                }
                Optional<com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation> rotation = RotationUtils.reachable(ctx,
                        pos);
                if (rotation.isPresent()) {
                    state.setTarget(new MovementState.MovementTarget(rotation.get(), true));
                    state.setInput(Input.CLICK_RIGHT, true);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isClosedInteraction(IBlockState state) {
        if (state.getBlock() instanceof BlockDoor) {
            return !state.getValue(BlockDoor.OPEN);
        }
        if (state.getBlock() instanceof BlockFenceGate) {
            return !state.getValue(BlockFenceGate.OPEN);
        }
        return state.getBlock() instanceof BlockTrapDoor && state.getBlock() != Blocks.IRON_TRAPDOOR
                && !state.getValue(BlockTrapDoor.OPEN);
    }

    private boolean updateStallState() {
        double progress = segmentProgress();
        double corridorDistance = distanceFromCorridor();
        double heightError = Math.abs(dest.y - ctx.player().posY);
        if (progress > bestProgress + 0.04D || corridorDistance + 0.04D < bestCorridorDistance
                || heightError + 0.04D < bestHeightError) {
            bestProgress = Math.max(bestProgress, progress);
            bestCorridorDistance = Math.min(bestCorridorDistance, corridorDistance);
            bestHeightError = Math.min(bestHeightError, heightError);
            stalledTicks = 0;
            return false;
        }
        stalledTicks++;
        return stalledTicks >= STALL_REPLAN_TICKS;
    }

    private void applyApproachSpeedCaps() {
        double remaining;
        if (vertical) {
            remaining = Math.abs(dest.y - ctx.player().posY);
            double horizontalError = Math.hypot(startCenter.x - ctx.player().posX, startCenter.z - ctx.player().posZ);
            setSpeedCaps(corridorRadius == 0.0D ? Math.min(0.3D, horizontalError * 0.65D) : FlyHandler.horizontalSpeed,
                    Math.min(FlyHandler.verticalSpeed, Math.max(0.05D, remaining * 0.65D)));
            return;
        }
        Vec3d horizontalSegment = new Vec3d(segment.x, 0.0D, segment.z);
        Vec3d fromStart = ctx.player().getPositionVector().subtract(startCenter);
        Vec3d primary = horizontalSegment.normalize();
        double progress = fromStart.dotProduct(primary);
        double lateralError = Math.abs(fromStart.dotProduct(new Vec3d(-primary.z, 0.0D, primary.x)));
        remaining = Math.max(0.0D, horizontalSegment.lengthVector() - progress);
        double approachCap = Math.min(FlyHandler.horizontalSpeed, Math.max(0.18D, remaining * 0.65D));
        if (lateralError > getCorridorRadius()) {
            double recoveryCap = Math.max(0.25D,
                    Math.min(MAX_CORNER_RECOVERY_SPEED, (lateralError - getCorridorRadius()) * 0.35D + 0.25D));
            approachCap = Math.min(FlyHandler.horizontalSpeed, recoveryCap);
        }
        if (corridorRadius == 0.0D) {
            approachCap = Math.min(approachCap, lateralError > 0.08D ? lateralError * 0.65D : 0.3D);
        }
        setSpeedCaps(approachCap, Math.min(FlyHandler.verticalSpeed,
                Math.max(0.05D, Math.abs(dest.y - ctx.player().posY) * 0.65D)));
    }

    private void setSpeedCaps(double horizontal, double vertical) {
        horizontalCap = Math.max(0.05D, Math.min(FlyHandler.horizontalSpeed, horizontal));
        verticalCap = Math.max(0.05D, Math.min(FlyHandler.verticalSpeed, vertical));
        FlyHandler.INSTANCE.setPathingSpeedCaps(horizontalCap, verticalCap);
    }

    private boolean isNextStepClear(MovementState state) {
        double dy = Boolean.TRUE.equals(state.getInputStates().get(Input.JUMP)) ? verticalCap
                : Boolean.TRUE.equals(state.getInputStates().get(Input.SNEAK)) ? -verticalCap : 0.0D;
        AxisAlignedBB swept = ctx.player().getEntityBoundingBox().expand(
                requestedDirection.x * horizontalCap, dy, requestedDirection.z * horizontalCap);
        for (int x = (int) Math.floor(swept.minX); x <= (int) Math.floor(swept.maxX); x++) {
            for (int z = (int) Math.floor(swept.minZ); z <= (int) Math.floor(swept.maxZ); z++) {
                if (!ctx.world().isBlockLoaded(new BlockPos(x, 0, z))) {
                    return false;
                }
            }
        }
        // Passing no entity excludes the synthetic corridor walls from this probe.
        return ctx.world().getCollisionBoxes(null, swept).isEmpty();
    }

    private void stopMotion() {
        ctx.player().motionX = 0.0D;
        ctx.player().motionY = 0.0D;
        ctx.player().motionZ = 0.0D;
    }

    private double segmentProgress() {
        if (vertical) {
            return dest.y > src.y ? ctx.player().posY - startCenter.y : startCenter.y - ctx.player().posY;
        }
        Vec3d horizontalSegment = new Vec3d(segment.x, 0.0D, segment.z);
        Vec3d fromStart = ctx.player().getPositionVector().subtract(startCenter);
        return fromStart.dotProduct(horizontalSegment.normalize());
    }

    private BetterBlockPos validationStart() {
        if (ctx.player() == null) {
            return src;
        }
        if (vertical) {
            double dx = ctx.player().posX - startCenter.x;
            double dz = ctx.player().posZ - startCenter.z;
            int feetY = ctx.playerFeet().y;
            if (dx * dx + dz * dz <= Math.pow(getCorridorRadius() + 2.0D, 2)
                    && feetY >= Math.min(src.y, dest.y) - 1 && feetY <= Math.max(src.y, dest.y) + 1) {
                return new BetterBlockPos(src.x, Math.max(Math.min(feetY, Math.max(src.y, dest.y)),
                        Math.min(src.y, dest.y)), src.z);
            }
            return src;
        }
        Vec3d horizontalSegment = new Vec3d(segment.x, 0.0D, segment.z);
        Vec3d offset = new Vec3d(ctx.player().posX - startCenter.x, 0.0D, ctx.player().posZ - startCenter.z);
        double projection = offset.dotProduct(horizontalSegment)
                / Math.max(1.0E-6D, horizontalSegment.lengthSquared());
        if (projection < 0.0D || projection > 1.0D || Math.abs(ctx.player().posY - src.y) > getCorridorRadius() + 2.0D) {
            return src;
        }
        Vec3d nearest = startCenter.add(horizontalSegment.scale(projection));
        double dx = ctx.player().posX - nearest.x;
        double dz = ctx.player().posZ - nearest.z;
        if (dx * dx + dz * dz > Math.pow(getCorridorRadius() + 2.0D, 2)) {
            return src;
        }
        return new BetterBlockPos((int) Math.floor(nearest.x), src.y, (int) Math.floor(nearest.z));
    }

    private boolean hasCompletedSegment() {
        if (vertical) {
            double dx = ctx.player().posX - endCenter.x;
            double dz = ctx.player().posZ - endCenter.z;
            boolean reachedY = dest.y > src.y ? ctx.player().posY >= dest.y - 0.05D
                    : ctx.player().posY <= dest.y + 0.05D;
            return reachedY && Math.sqrt(dx * dx + dz * dz) <= getCorridorRadius() + 0.1D;
        }
        Vec3d horizontalSegment = new Vec3d(segment.x, 0.0D, segment.z);
        Vec3d fromStart = ctx.player().getPositionVector().subtract(startCenter);
        Vec3d primary = horizontalSegment.normalize();
        double along = fromStart.dotProduct(primary);
        double remaining = horizontalSegment.lengthVector() - along;
        double lateralError = Math.abs(fromStart.dotProduct(new Vec3d(-primary.z, 0.0D, primary.x)));
        return remaining <= 0.1D && lateralError <= getCorridorRadius() + 0.1D
                && Math.abs(ctx.player().posY - dest.y) <= getCorridorRadius() + 0.1D;
    }

    @Override
    public void reset() {
        super.reset();
        FlightCorridorCollisionHandler.INSTANCE.clear(this);
        bestProgress = Double.NEGATIVE_INFINITY;
        bestCorridorDistance = Double.POSITIVE_INFINITY;
        bestHeightError = Double.POSITIVE_INFINITY;
        checkedCorridorWidth = -1;
        stalledTicks = 0;
    }

    public double getCorridorRadius() {
        return corridorRadius;
    }

    private double getRecoveryDistanceLimit() {
        return getCorridorRadius() + MAX_RECOVERY_DISTANCE
                + Math.min(32.0D, Math.max(0.0D, FlyHandler.horizontalSpeed * 1.5D));
    }

    private double distanceFromCorridor() {
        Vec3d player = ctx.player().getPositionVector();
        if (vertical) {
            double dx = player.x - startCenter.x;
            double dz = player.z - startCenter.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
        Vec3d horizontalSegment = new Vec3d(segment.x, 0.0D, segment.z);
        Vec3d offset = new Vec3d(player.x - startCenter.x, 0.0D, player.z - startCenter.z);
        double projection = offset.dotProduct(horizontalSegment) / Math.max(1.0E-6D, horizontalSegment.lengthSquared());
        Vec3d nearest = new Vec3d(startCenter.x, player.y, startCenter.z)
                .add(horizontalSegment.scale(Math.max(0.0D, Math.min(1.0D, projection))));
        return player.distanceTo(nearest);
    }

    @Override
    public Vec3d[] getRoutePoints() {
        return new Vec3d[] { startCenter, endCenter };
    }

    private static Vec3d center(BetterBlockPos pos) {
        return new Vec3d(pos.x + 0.5D, pos.y, pos.z + 0.5D);
    }
}
