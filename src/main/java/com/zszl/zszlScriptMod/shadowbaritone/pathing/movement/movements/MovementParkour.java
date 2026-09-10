/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ParkourProfile;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.VecUtils;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteFollowHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourFailureReason;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpCandidate;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpType;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourLandingWindow;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourLaunchWindow;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MovementParkour extends Movement implements IRoutePointMovement {

    private static final BetterBlockPos[] EMPTY = new BetterBlockPos[0];
    private static final double ROUTE_LOOKAHEAD_GROUND = 0.38D;
    private static final double ROUTE_LOOKAHEAD_AIR = 0.78D;
    private static final double FALL_FAIL_Y_MARGIN = 1.25D;
    private static final double AIRBORNE_Y_DELTA = 0.05D;
    private static final int STRAIGHT_CANDIDATE_BUDGET = 3;

    private final EnumFacing direction;
    private final ParkourJumpCandidate candidate;

    private ParkourExecutionPhase executionPhase;
    private ParkourFailureReason lastFailureReason;
    private boolean jumpTriggered;
    private int jumpHoldTicksRemaining;
    private int activeSegmentIndex;
    private int segmentSettleTicksRemaining;
    private boolean debugBootstrapLogged;
    private int debugTickCounter;

    private MovementParkour(IBaritone baritone, BetterBlockPos src, EnumFacing direction, ParkourJumpCandidate candidate) {
        super(baritone, src, candidate.getDest(), EMPTY, null);
        this.direction = direction;
        this.candidate = candidate;
        this.executionPhase = ParkourExecutionPhase.ALIGN;
        this.lastFailureReason = ParkourFailureReason.NONE;
        this.activeSegmentIndex = 0;
        this.jumpHoldTicksRemaining = 0;
        this.segmentSettleTicksRemaining = 0;
        this.debugBootstrapLogged = false;
        this.debugTickCounter = 0;
    }

    public static MovementParkour cost(CalculationContext context, BetterBlockPos src, EnumFacing direction) {
        ParkourJumpCandidate candidate = resolveBestCandidate(context, src, direction, null);
        if (candidate == null) {
            candidate = unreachableCandidate(src, direction);
        }
        return new MovementParkour(context.getBaritone(), src, direction, candidate);
    }

    public static MovementParkour cost(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            EnumFacing lateralDirection) {
        ParkourJumpCandidate candidate = resolveBestCandidate(context, src, direction, lateralDirection);
        if (candidate == null) {
            candidate = unreachableCandidate(src, direction);
        }
        return new MovementParkour(context.getBaritone(), src, direction, candidate);
    }

    public static void cost(CalculationContext context, int x, int y, int z, EnumFacing dir, MutableMoveResult res) {
        ParkourJumpCandidate candidate = resolveBestCandidate(context, new BetterBlockPos(x, y, z), dir, null);
        if (candidate == null) {
            return;
        }
        res.x = candidate.getDest().x;
        res.y = candidate.getDest().y;
        res.z = candidate.getDest().z;
        res.cost = candidate.getCost();
    }

    public static void cost(CalculationContext context, int x, int y, int z, EnumFacing dir,
            EnumFacing lateralDirection, MutableMoveResult res) {
        ParkourJumpCandidate candidate = resolveBestCandidate(context, new BetterBlockPos(x, y, z), dir,
                lateralDirection);
        if (candidate == null) {
            return;
        }
        res.x = candidate.getDest().x;
        res.y = candidate.getDest().y;
        res.z = candidate.getDest().z;
        res.cost = candidate.getCost();
    }

    @Override
    public double calculateCost(CalculationContext context) {
        ParkourJumpCandidate recalculated = resolveBestCandidate(context, src, direction, candidate.getLateral());
        if (recalculated == null || !candidate.matches(recalculated)) {
            return COST_INF;
        }
        return recalculated.getCost();
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        set.add(src);
        set.add(dest);
        for (Vec3d point : candidate.getRoutePoints()) {
            set.add(new BetterBlockPos(MathHelper.floor(point.x), MathHelper.floor(point.y), MathHelper.floor(point.z)));
        }
        return set;
    }

    @Override
    protected boolean safeToCancel(MovementState state) {
        return state.getStatus() != MovementStatus.RUNNING;
    }

    @Override
    public void reset() {
        super.reset();
        this.executionPhase = ParkourExecutionPhase.ALIGN;
        this.lastFailureReason = ParkourFailureReason.NONE;
        this.jumpTriggered = false;
        this.jumpHoldTicksRemaining = 0;
        this.activeSegmentIndex = 0;
        this.segmentSettleTicksRemaining = 0;
        this.debugBootstrapLogged = false;
        this.debugTickCounter = 0;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        if (!debugBootstrapLogged) {
            debugBootstrapLogged = true;
            debug("start " + summarizeCandidate(candidate));
        }
        debugTickCounter++;
        BetterBlockPos segmentSrc = getCurrentSegmentSrc();
        BetterBlockPos segmentDest = getCurrentSegmentDest();
        if (candidate.getCost() >= COST_INF) {
            debug("candidate_unreachable_before_execution");
            return fail(state, ParkourFailureReason.COLLISION_REJECTED);
        }
        if (hasReachedLandingWindow(segmentDest)) {
            debugTick(segmentSrc, segmentDest, Double.NaN, false, null, "landing_window_reached");
            if (activeSegmentIndex + 1 < candidate.getChainLength()) {
                advanceToNextSegment();
                segmentSrc = getCurrentSegmentSrc();
                segmentDest = getCurrentSegmentDest();
            } else {
                this.executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
                this.lastFailureReason = ParkourFailureReason.NONE;
                return applyPhaseInputPolicy(state.setStatus(MovementStatus.SUCCESS));
            }
        }
        if (ctx.player().posY < Math.min(segmentSrc.y, segmentDest.y) - FALL_FAIL_Y_MARGIN) {
            debugTick(segmentSrc, segmentDest, Double.NaN, true, null, "fell_below_recovery_margin");
            return fail(state, ParkourFailureReason.MISSED_LANDING);
        }
        if (!playerOnRoute()) {
            debugTick(segmentSrc, segmentDest, Double.NaN, isAirborne(), null, "player_left_route");
            return fail(state, jumpTriggered || isAirborne() ? ParkourFailureReason.MISSED_LANDING
                    : ParkourFailureReason.ALIGNMENT_FAILED);
        }

        if (segmentSettleTicksRemaining > 0) {
            this.executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
            segmentSettleTicksRemaining--;
            return applyPhaseInputPolicy(state);
        }

        double progress = getForwardProgress(segmentSrc);
        boolean airborne = isAirborne();
        JumpDecision jumpDecision = evaluateJumpDecision(progress, segmentSrc);
        if (!jumpTriggered && !airborne && progress > candidate.getLaunchWindow().getMaxProgress() + 0.18D) {
            debugTick(segmentSrc, segmentDest, progress, false, jumpDecision, "late_jump_guard");
            return fail(state, ParkourFailureReason.LATE_JUMP);
        }

        if (airborne) {
            this.executionPhase = ParkourExecutionPhase.AIR_CORRECTION;
            this.jumpTriggered = true;
            this.jumpHoldTicksRemaining = 0;
        } else if (jumpHoldTicksRemaining > 0) {
            this.executionPhase = ParkourExecutionPhase.TAKEOFF;
        } else if (candidate.requiresSprint()
                && !jumpTriggered
                && progress >= candidate.getLaunchWindow().getMinProgress() - 0.18D) {
            this.executionPhase = ParkourExecutionPhase.SPRINT_PRIME;
        } else if (!jumpTriggered && jumpDecision.shouldJump) {
            this.executionPhase = ParkourExecutionPhase.TAKEOFF;
        } else if (ctx.playerFeet().equals(src)) {
            this.executionPhase = ParkourExecutionPhase.ALIGN;
        } else {
            this.executionPhase = ParkourExecutionPhase.RUNUP;
        }

        if (candidate.requiresSprint()) {
            state.setInput(Input.SPRINT, true);
        }

        Vec3d currentTarget = getCurrentTargetPoint(airborne);
        moveTowardsTarget(state, currentTarget, executionPhase != ParkourExecutionPhase.AIR_CORRECTION);
        if (executionPhase == ParkourExecutionPhase.AIR_CORRECTION) {
            applyAirBrakingPolicy(state, segmentSrc, segmentDest, progress);
        }

        debugTick(segmentSrc, segmentDest, progress, airborne, jumpDecision, "tick");

        if (!jumpTriggered && (jumpDecision.shouldJump || jumpHoldTicksRemaining > 0)) {
            state.setInput(Input.JUMP, true);
            this.executionPhase = ParkourExecutionPhase.TAKEOFF;
            if (jumpHoldTicksRemaining <= 0 && jumpDecision.shouldJump) {
                jumpHoldTicksRemaining = 3;
                debug("issue_jump progress=" + formatDouble(progress)
                        + " projected=" + formatDouble(jumpDecision.projectedProgress)
                        + " reason=" + jumpDecision.reason);
            } else {
                debug("hold_jump remaining=" + jumpHoldTicksRemaining);
            }
            jumpHoldTicksRemaining = Math.max(0, jumpHoldTicksRemaining - 1);
        }

        if (jumpTriggered && ctx.player().onGround && !ctx.playerFeet().equals(segmentSrc)
                && !hasReachedLandingWindow(segmentDest)) {
            this.executionPhase = ParkourExecutionPhase.RECOVER;
            double overshoot = getForwardProgress(segmentSrc) - candidate.getForwardDistance();
            if (overshoot > candidate.getLandingWindow().getMaxOvershootDistance()) {
                debugTick(segmentSrc, segmentDest, progress, airborne, jumpDecision, "overshot_landing_guard");
                return fail(state, ParkourFailureReason.OVERSHOT_LANDING);
            }
        }

        return applyPhaseInputPolicy(state);
    }

    @Override
    public Vec3d[] getRoutePoints() {
        return candidate.getRoutePoints();
    }

    public ParkourJumpCandidate getCandidate() {
        return candidate;
    }

    public String getExecutionPhaseName() {
        return executionPhase.name();
    }

    public boolean isPrecisionCriticalPhase() {
        switch (executionPhase) {
            case ALIGN:
            case RUNUP:
            case SPRINT_PRIME:
            case TAKEOFF:
            case AIR_CORRECTION:
                return true;
            default:
                return false;
        }
    }

    public int getActiveSegmentIndex() {
        return activeSegmentIndex;
    }

    public ParkourFailureReason getLastFailureReason() {
        return lastFailureReason;
    }

    private MovementState fail(MovementState state, ParkourFailureReason reason) {
        this.lastFailureReason = reason;
        this.executionPhase = ParkourExecutionPhase.RECOVER;
        debug("fail reason=" + reason
                + " feet=" + ctx.playerFeet()
                + " pos=" + formatVec(ctx.player().posX, ctx.player().posY, ctx.player().posZ)
                + " motion=" + formatVec(ctx.player().motionX, ctx.player().motionY, ctx.player().motionZ));
        state.retainInputs(EnumSet.noneOf(Input.class));
        if (ctx != null && ctx.player() != null && ctx.player().onGround) {
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        return state.setStatus(MovementStatus.UNREACHABLE);
    }

    private MovementState applyPhaseInputPolicy(MovementState state) {
        return state.retainInputs(getAllowedInputsForCurrentPhase());
    }

    private Set<Input> getAllowedInputsForCurrentPhase() {
        switch (executionPhase) {
            case ALIGN:
            case RUNUP:
            case SPRINT_PRIME:
                return EnumSet.of(
                        Input.MOVE_FORWARD,
                        Input.MOVE_BACK,
                        Input.MOVE_LEFT,
                        Input.MOVE_RIGHT,
                        Input.SPRINT);
            case TAKEOFF:
                return EnumSet.of(
                        Input.MOVE_FORWARD,
                        Input.MOVE_BACK,
                        Input.MOVE_LEFT,
                        Input.MOVE_RIGHT,
                        Input.SPRINT,
                        Input.JUMP);
            case AIR_CORRECTION:
            case RECOVER:
                return EnumSet.of(
                        Input.MOVE_FORWARD,
                        Input.MOVE_BACK,
                        Input.MOVE_LEFT,
                        Input.MOVE_RIGHT);
            case LAND_CONFIRM:
            default:
                return EnumSet.noneOf(Input.class);
        }
    }

    private boolean hasReachedLandingWindow(BetterBlockPos segmentDest) {
        if (ctx.playerFeet().equals(segmentDest)) {
            Block d = BlockStateInterface.getBlock(ctx, segmentDest);
            if (d == Blocks.VINE || d == Blocks.LADDER) {
                return true;
            }
            // Consecutive jumps need a real landing confirmation here. Entering the block
            // footprint slightly above the surface is not enough, otherwise the next jump
            // can be evaluated before the previous landing is actually able to accept jump input.
            return ctx.player().onGround;
        }
        if (!ctx.player().onGround) {
            return false;
        }
        double flatDistanceSq = VecUtils.entityFlatDistanceToCenter(ctx.player(), segmentDest);
        if (flatDistanceSq > candidate.getLandingWindow().getMaxFlatDistanceSq()) {
            return false;
        }
        return Math.abs(ctx.player().posY - segmentDest.y) <= 1.05D;
    }

    private boolean playerOnRoute() {
        if (getValidPositions().contains(ctx.playerFeet())
                || getValidPositions().contains(((com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior) baritone
                        .getPathingBehavior()).pathStart())) {
            return true;
        }
        Vec3d playerPos = flattenedPlayerPos();
        return RouteFollowHelper.distanceSqToRoute(candidate.getRoutePoints(), playerPos)
                <= candidate.getLandingWindow().getMaxRouteDistanceSq();
    }

    private Vec3d getCurrentTargetPoint(boolean airborne) {
        Vec3d playerPos = flattenedPlayerPos();
        return RouteFollowHelper.getTargetPoint(candidate.getRoutePoints(), playerPos,
                airborne ? ROUTE_LOOKAHEAD_AIR : ROUTE_LOOKAHEAD_GROUND);
    }

    private Vec3d flattenedPlayerPos() {
        double routeY = candidate.getRoutePoints().length == 0 ? src.y + 0.5D : candidate.getRoutePoints()[0].y;
        return new Vec3d(ctx.player().posX, routeY, ctx.player().posZ);
    }

    private JumpDecision evaluateJumpDecision(double progress, BetterBlockPos segmentSrc) {
        double lateralError = Math.abs(getLateralError(segmentSrc));
        double minProgress = candidate.getLaunchWindow().getMinProgress();
        double maxProgress = candidate.getLaunchWindow().getMaxProgress();
        double forwardSpeed = getForwardSpeed();
        double projectedProgress = progress + forwardSpeed * 1.35D;
        if (!ctx.player().onGround) {
            return new JumpDecision(false, "not_on_ground", projectedProgress, forwardSpeed, lateralError);
        }
        if (lateralError > candidate.getLaunchWindow().getMaxLateralError()) {
            return new JumpDecision(false, "lateral_error_exceeded", projectedProgress, forwardSpeed, lateralError);
        }
        if (progress >= minProgress && progress <= maxProgress) {
            return new JumpDecision(true, "within_launch_window", projectedProgress, forwardSpeed, lateralError);
        }
        if (projectedProgress > maxProgress && progress >= minProgress - 0.10D) {
            return new JumpDecision(true, "projected_overshoot_window", projectedProgress, forwardSpeed, lateralError);
        }
        if (candidate.getDest().y < segmentSrc.y && projectedProgress >= Math.min(0.46D, maxProgress)
                && progress >= minProgress - 0.14D) {
            return new JumpDecision(true, "descending_early_takeoff", projectedProgress, forwardSpeed, lateralError);
        }
        if (progress < minProgress) {
            return new JumpDecision(false, "progress_below_window", projectedProgress, forwardSpeed, lateralError);
        }
        return new JumpDecision(false, "progress_past_window", projectedProgress, forwardSpeed, lateralError);
    }

    private boolean isAirborne() {
        return !ctx.player().onGround || ctx.player().posY - src.y > AIRBORNE_Y_DELTA;
    }

    private double getForwardProgress(BetterBlockPos segmentSrc) {
        Vec3d start = VecUtils.getBlockPosCenter(segmentSrc);
        return (ctx.player().posX - start.x) * direction.getFrontOffsetX()
                + (ctx.player().posZ - start.z) * direction.getFrontOffsetZ();
    }

    private double getForwardSpeed() {
        return Math.max(0.0D, ctx.player().motionX * direction.getFrontOffsetX()
                + ctx.player().motionZ * direction.getFrontOffsetZ());
    }

    private double getLateralError(BetterBlockPos segmentSrc) {
        Vec3d start = VecUtils.getBlockPosCenter(segmentSrc);
        return Math.abs((ctx.player().posX - start.x) * direction.getFrontOffsetZ()
                - (ctx.player().posZ - start.z) * direction.getFrontOffsetX());
    }

    private BetterBlockPos getCurrentSegmentSrc() {
        if (activeSegmentIndex <= 0) {
            return src;
        }
        BetterBlockPos[] landings = candidate.getSegmentLandings();
        int index = Math.min(activeSegmentIndex - 1, landings.length - 1);
        return landings[index];
    }

    private BetterBlockPos getCurrentSegmentDest() {
        BetterBlockPos[] landings = candidate.getSegmentLandings();
        int index = Math.min(activeSegmentIndex, landings.length - 1);
        return landings[index];
    }

    private void advanceToNextSegment() {
        this.activeSegmentIndex = Math.min(candidate.getChainLength() - 1, this.activeSegmentIndex + 1);
        this.segmentSettleTicksRemaining = 1;
        this.executionPhase = ParkourExecutionPhase.RUNUP;
        this.jumpTriggered = false;
        this.jumpHoldTicksRemaining = 0;
        this.lastFailureReason = ParkourFailureReason.NONE;
        debug("advance_segment index=" + activeSegmentIndex + "/" + candidate.getChainLength());
    }

    private void moveTowardsTarget(MovementState state, Vec3d targetPos, boolean forceRotations) {
        Rotation targetRotation = RotationUtils.calcRotationFromVec3d(
                ctx.playerHead(),
                targetPos,
                ctx.playerRotations()).withPitch(ctx.playerRotations().getPitch());
        state.setTarget(new MovementState.MovementTarget(targetRotation, forceRotations));

        float yawDiff = MathHelper.wrapDegrees(targetRotation.getYaw() - ctx.playerRotations().getYaw());
        if (!Baritone.settings().freeLook.value) {
            state.setInput(Input.MOVE_FORWARD, true);
            return;
        }
        if (yawDiff >= -22.5F && yawDiff <= 22.5F) {
            state.setInput(Input.MOVE_FORWARD, true);
            return;
        }
        if (yawDiff > 22.5F && yawDiff <= 67.5F) {
            state.setInput(Input.MOVE_FORWARD, true);
            state.setInput(Input.MOVE_LEFT, true);
            return;
        }
        if (yawDiff > 67.5F && yawDiff <= 112.5F) {
            state.setInput(Input.MOVE_LEFT, true);
            return;
        }
        if (yawDiff > 112.5F && yawDiff <= 157.5F) {
            state.setInput(Input.MOVE_BACK, true);
            state.setInput(Input.MOVE_LEFT, true);
            return;
        }
        if (yawDiff < -22.5F && yawDiff >= -67.5F) {
            state.setInput(Input.MOVE_FORWARD, true);
            state.setInput(Input.MOVE_RIGHT, true);
            return;
        }
        if (yawDiff < -67.5F && yawDiff >= -112.5F) {
            state.setInput(Input.MOVE_RIGHT, true);
            return;
        }
        if (yawDiff < -112.5F && yawDiff >= -157.5F) {
            state.setInput(Input.MOVE_BACK, true);
            state.setInput(Input.MOVE_RIGHT, true);
            return;
        }
        state.setInput(Input.MOVE_BACK, true);
    }

    private void applyAirBrakingPolicy(MovementState state, BetterBlockPos segmentSrc, BetterBlockPos segmentDest,
            double progress) {
        double forwardSpeed = getForwardSpeed();
        if (forwardSpeed <= 0.01D) {
            return;
        }

        double landingDistance = candidate.getForwardDistance();
        double projectedProgress = progress + forwardSpeed * 1.6D;
        double remainingDistance = landingDistance - progress;
        double lateralError = getLateralError(segmentSrc);
        double maxLateralError = candidate.getLaunchWindow().getMaxLateralError();

        boolean shortLanding = candidate.getForwardDistance() <= 2;
        boolean narrowLanding = candidate.isNarrowLanding();
        boolean noRecoveryRunout = candidate.getLandingRecoveryDistance() < 1.10D;

        if (!shortLanding && !narrowLanding && !noRecoveryRunout) {
            return;
        }

        boolean softBrake = remainingDistance <= 0.42D
                || projectedProgress >= landingDistance - 0.08D;
        boolean hardBrake = projectedProgress >= landingDistance + 0.12D
                || progress >= landingDistance - 0.04D;

        if (!softBrake && !hardBrake) {
            return;
        }

        // First remove any forward acceleration so short parkour landings can actually settle.
        state.setInput(Input.MOVE_FORWARD, false);

        // Once we're very close to the landing or already projected to overshoot it,
        // actively tap reverse to bleed horizontal speed in the air.
        if (hardBrake || lateralError <= Math.max(0.06D, maxLateralError * 0.72D)) {
            state.setInput(Input.MOVE_BACK, true);
        }

        // When we're already lined up laterally, stop side drift while braking so a single
        // block landing doesn't get sidestepped off the platform.
        if (lateralError <= Math.max(0.04D, maxLateralError * 0.45D)) {
            state.setInput(Input.MOVE_LEFT, false);
            state.setInput(Input.MOVE_RIGHT, false);
        }

        debug("air_brake progress=" + formatDouble(progress)
                + " projected=" + formatDouble(projectedProgress)
                + " speed=" + formatDouble(forwardSpeed)
                + " remaining=" + formatDouble(remainingDistance)
                + " lateral=" + formatDouble(lateralError)
                + " hard=" + hardBrake);
    }

    private static ParkourJumpCandidate resolveBestCandidate(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateralDirection) {
        if (!canEvaluateParkourFrom(context, src, direction, lateralDirection)) {
            return null;
        }

        ParkourProfile profile = ParkourProfile.orDefault(context.parkourProfile);
        List<ParkourJumpCandidate> candidates = new ArrayList<>();
        List<ParkourJumpCandidate> straightCandidates = new ArrayList<>();
        collectStraightCandidates(context, src, direction, profile, straightCandidates);
        if (context.parkourMode) {
            collectDescendingStraightCandidates(context, src, direction, profile, straightCandidates);
        }
        appendBudgetedCandidates(context, candidates, straightCandidates, STRAIGHT_CANDIDATE_BUDGET);
        if (context.parkourMode) {
            List<ParkourJumpCandidate> edgeCandidates = new ArrayList<>();
            collectEdgeLaunchCandidates(context, src, direction, profile, edgeCandidates);
            appendBudgetedCandidates(context, candidates, edgeCandidates, getEdgeCandidateBudget(profile));

            List<ParkourJumpCandidate> chainCandidates = new ArrayList<>();
            collectChainCandidates(context, src, direction, profile, chainCandidates);
            appendBudgetedCandidates(context, candidates, chainCandidates, getChainCandidateBudget(profile));
        }
        if (lateralDirection != null) {
            List<ParkourJumpCandidate> angledCandidates = new ArrayList<>();
            collectAngledCandidates(context, src, direction, lateralDirection, profile, angledCandidates);
            if (context.parkourMode) {
                collectAscendingAngledCandidates(context, src, direction, lateralDirection, profile, angledCandidates);
                collectDescendingAngledCandidates(context, src, direction, lateralDirection, profile, angledCandidates);
            }
            appendBudgetedCandidates(context, candidates, angledCandidates, getAngledCandidateBudget(profile));
        }
        trimCandidateBudget(candidates, getTotalCandidateBudget(profile));
        ParkourJumpCandidate selected = pickLowestCostCandidate(candidates);
        if (selected != null) {
            debugStatic("select src=" + src
                    + " dir=" + direction
                    + " lateral=" + lateralDirection
                    + " count=" + candidates.size()
                    + " chosen=" + summarizeCandidate(selected));
        }
        return selected;
    }

    private static boolean canEvaluateParkourFrom(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            EnumFacing lateralDirection) {
        if (!context.allowParkour) {
            return false;
        }
        if (src.y == 256 && !context.allowJumpAt256) {
            return false;
        }
        if (lateralDirection != null) {
            if (!context.parkourMode || lateralDirection == direction || lateralDirection == direction.getOpposite()
                    || lateralDirection.getAxis() == direction.getAxis()) {
                return false;
            }
        }

        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();

        if (!MovementHelper.fullyPassable(context, x + xDiff, y, z + zDiff)) {
            return false;
        }
        IBlockState adj = context.get(x + xDiff, y - 1, z + zDiff);
        if (MovementHelper.canWalkOn(context, x + xDiff, y - 1, z + zDiff, adj)) {
            return false;
        }
        if (MovementHelper.avoidWalkingInto(adj.getBlock())
                && adj.getBlock() != Blocks.WATER
                && adj.getBlock() != Blocks.FLOWING_WATER) {
            return false;
        }
        if (!MovementHelper.fullyPassable(context, x + xDiff, y + 1, z + zDiff)
                || !MovementHelper.fullyPassable(context, x + xDiff, y + 2, z + zDiff)
                || !MovementHelper.fullyPassable(context, x, y + 2, z)) {
            return false;
        }

        IBlockState standingOn = context.get(x, y - 1, z);
        if (standingOn.getBlock() == Blocks.VINE
                || standingOn.getBlock() == Blocks.LADDER
                || standingOn.getBlock() instanceof BlockStairs
                || MovementHelper.isBottomSlab(standingOn)) {
            return false;
        }
        if (context.assumeWalkOnWater && standingOn.getBlock() instanceof BlockLiquid) {
            return false;
        }
        if (context.getBlock(x, y, z) instanceof BlockLiquid) {
            return false;
        }
        return true;
    }

    private static void collectStraightCandidates(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            ParkourProfile profile, List<ParkourJumpCandidate> candidates) {
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        IBlockState standingOn = context.get(x, y - 1, z);
        int maxJump = standingOn.getBlock() == Blocks.SOUL_SAND ? 2 : (context.canSprint ? 4 : 3);

        for (int i = 2; i <= maxJump; i++) {
            int destX = x + xDiff * i;
            int destZ = z + zDiff * i;

            if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                break;
            }

            IBlockState destInto = context.bsi.get0(destX, y, destZ);
            if (!MovementHelper.fullyPassable(context, destX, y, destZ, destInto)) {
                if (i <= 3 && context.allowParkourAscend && context.canSprint
                        && MovementHelper.canWalkOn(context, destX, y, destZ, destInto)
                        && checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                    BetterBlockPos dest = new BetterBlockPos(destX, y + 1, destZ);
                    if (hasLandingRecoverySpace(context, dest, direction)) {
                        candidates.add(createCandidate(context, src, dest, direction, null, i, true, false, profile,
                                i * SPRINT_ONE_BLOCK_COST + context.jumpPenalty));
                    }
                }
                break;
            }

            IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            if ((landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= i
                            && MovementHelper.canUseFrostWalker(context, landingOn))) {
                if (checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                    BetterBlockPos dest = new BetterBlockPos(destX, y, destZ);
                    if (hasLandingRecoverySpace(context, dest, direction)) {
                        candidates.add(createCandidate(context, src, dest, direction, null, i, false,
                                isNarrowLanding(context, dest), profile, costFromJumpDistance(i) + context.jumpPenalty));
                    }
                }
            }

            if (!MovementHelper.fullyPassable(context, destX, y + 3, destZ)) {
                break;
            }
        }
    }

    private static void collectAngledCandidates(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            EnumFacing lateralDirection, ParkourProfile profile, List<ParkourJumpCandidate> candidates) {
        if (!context.canSprint) {
            return;
        }

        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        int lateralX = lateralDirection.getFrontOffsetX();
        int lateralZ = lateralDirection.getFrontOffsetZ();

        for (int forwardDistance = 2; forwardDistance <= 3; forwardDistance++) {
            int destX = x + xDiff * forwardDistance + lateralX;
            int destZ = z + zDiff * forwardDistance + lateralZ;

            if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }

            IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            boolean canLand = (landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= forwardDistance + 1
                            && MovementHelper.canUseFrostWalker(context, landingOn));
            if (!canLand) {
                continue;
            }

            if (!checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                continue;
            }

            BetterBlockPos dest = new BetterBlockPos(destX, y, destZ);
            if (!hasLandingRecoverySpace(context, dest, direction)) {
                continue;
            }

            double baseCost = SPRINT_ONE_BLOCK_COST * (forwardDistance + 0.75D) + context.jumpPenalty;
            candidates.add(createCandidate(context, src, dest, direction, lateralDirection, forwardDistance, false,
                    isNarrowLanding(context, dest), profile, baseCost));
        }
    }

    private static void collectDescendingStraightCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, ParkourProfile profile, List<ParkourJumpCandidate> candidates) {
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        IBlockState standingOn = context.get(x, y - 1, z);
        int maxJump = standingOn.getBlock() == Blocks.SOUL_SAND ? 2 : (context.canSprint ? 4 : 3);

        // Adjacent one-block drops are already handled more reliably by normal descend/fall
        // movements. Treat parkour descend as a true jump-only option starting from distance 2.
        for (int forwardDistance = 2; forwardDistance <= maxJump; forwardDistance++) {
            int destX = x + xDiff * forwardDistance;
            int destZ = z + zDiff * forwardDistance;
            int destY = y - 1;

            if (!MovementHelper.fullyPassable(context, destX, destY, destZ)
                    || !MovementHelper.fullyPassable(context, destX, destY + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, destY + 2, destZ)) {
                continue;
            }

            IBlockState landingOn = context.bsi.get0(destX, destY - 1, destZ);
            boolean canLand = (landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, destY - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= forwardDistance
                            && MovementHelper.canUseFrostWalker(context, landingOn));
            if (!canLand) {
                continue;
            }

            if (!checkOvershootSafety(context.bsi, destX + xDiff, destY, destZ + zDiff)) {
                continue;
            }

            BetterBlockPos dest = new BetterBlockPos(destX, destY, destZ);
            if (!hasLandingRecoverySpace(context, dest, direction)) {
                continue;
            }

            double descentDiscount = 0.12D;
            candidates.add(createCandidate(context, src, dest, direction, null, forwardDistance, false,
                    isNarrowLanding(context, dest), profile,
                    Math.max(0.0D, costFromJumpDistance(forwardDistance) + context.jumpPenalty - descentDiscount)));
        }
    }

    private static void collectDescendingAngledCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateralDirection, ParkourProfile profile,
            List<ParkourJumpCandidate> candidates) {
        if (!context.canSprint) {
            return;
        }

        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        int lateralX = lateralDirection.getFrontOffsetX();
        int lateralZ = lateralDirection.getFrontOffsetZ();

        // Same rule as straight descend: a one-step diagonal drop should prefer vanilla
        // diagonal descend over parkour, otherwise parkour mode can stall on stair-like terrain.
        for (int forwardDistance = 2; forwardDistance <= 3; forwardDistance++) {
            int destX = x + xDiff * forwardDistance + lateralX;
            int destZ = z + zDiff * forwardDistance + lateralZ;
            int destY = y - 1;

            if (!MovementHelper.fullyPassable(context, destX, destY, destZ)
                    || !MovementHelper.fullyPassable(context, destX, destY + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, destY + 2, destZ)) {
                continue;
            }

            IBlockState landingOn = context.bsi.get0(destX, destY - 1, destZ);
            boolean canLand = (landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, destY - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= forwardDistance + 1
                            && MovementHelper.canUseFrostWalker(context, landingOn));
            if (!canLand) {
                continue;
            }

            if (!checkOvershootSafety(context.bsi, destX + xDiff, destY, destZ + zDiff)) {
                continue;
            }

            BetterBlockPos dest = new BetterBlockPos(destX, destY, destZ);
            if (!hasLandingRecoverySpace(context, dest, direction)) {
                continue;
            }

            double descentDiscount = 0.10D;
            double baseCost = SPRINT_ONE_BLOCK_COST * (forwardDistance + 0.65D) + context.jumpPenalty
                    - descentDiscount;
            candidates.add(createCandidate(context, src, dest, direction, lateralDirection, forwardDistance, false,
                    isNarrowLanding(context, dest), profile, Math.max(0.0D, baseCost)));
        }
    }

    private static void collectAscendingAngledCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateralDirection, ParkourProfile profile,
            List<ParkourJumpCandidate> candidates) {
        if (!context.canSprint || !context.allowParkourAscend) {
            return;
        }

        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        int lateralX = lateralDirection.getFrontOffsetX();
        int lateralZ = lateralDirection.getFrontOffsetZ();

        for (int forwardDistance = 1; forwardDistance <= 2; forwardDistance++) {
            int destX = x + xDiff * forwardDistance + lateralX;
            int destZ = z + zDiff * forwardDistance + lateralZ;

            if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }

            IBlockState landingOn = context.bsi.get0(destX, y, destZ);
            if (landingOn.getBlock() == Blocks.FARMLAND
                    || !MovementHelper.canWalkOn(context, destX, y, destZ, landingOn)) {
                continue;
            }

            if (!checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                continue;
            }

            BetterBlockPos dest = new BetterBlockPos(destX, y + 1, destZ);
            if (!hasLandingRecoverySpace(context, dest, direction)) {
                continue;
            }

            double baseCost = SPRINT_ONE_BLOCK_COST * (forwardDistance + 1.10D) + context.jumpPenalty + 0.18D;
            candidates.add(createCandidate(context, src, dest, direction, lateralDirection, forwardDistance, true,
                    false, profile, baseCost));
        }
    }

    private static void collectEdgeLaunchCandidates(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            ParkourProfile profile, List<ParkourJumpCandidate> candidates) {
        for (EnumFacing lateral : EnumFacing.HORIZONTALS) {
            if (lateral.getAxis() == direction.getAxis()) {
                continue;
            }
            collectSingleEdgeLaunchCandidates(context, src, direction, lateral, profile, candidates);
        }
    }

    private static void collectSingleEdgeLaunchCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateralDirection, ParkourProfile profile,
            List<ParkourJumpCandidate> candidates) {
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        for (int forwardDistance = 2; forwardDistance <= Math.min(3, context.canSprint ? 4 : 3); forwardDistance++) {
            int destX = x + xDiff * forwardDistance;
            int destZ = z + zDiff * forwardDistance;
            if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }
            IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            if (landingOn.getBlock() == Blocks.FARMLAND
                    || !MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn)) {
                continue;
            }
            if (!MovementHelper.fullyPassable(context, x + lateralDirection.getFrontOffsetX(), y, z + lateralDirection.getFrontOffsetZ())
                    || !MovementHelper.fullyPassable(context, x + lateralDirection.getFrontOffsetX(), y + 1,
                            z + lateralDirection.getFrontOffsetZ())) {
                continue;
            }
            BetterBlockPos dest = new BetterBlockPos(destX, y, destZ);
            if (!hasLandingRecoverySpace(context, dest, direction)) {
                continue;
            }
            double edgePenalty = 0.22D + Math.max(0, forwardDistance - 2) * 0.18D;
            candidates.add(createCandidate(context, src, dest, direction, lateralDirection, forwardDistance, false,
                    isNarrowLanding(context, dest), profile, costFromJumpDistance(forwardDistance) + context.jumpPenalty
                            + edgePenalty, true));
        }
    }

    private static void collectChainCandidates(CalculationContext context, BetterBlockPos src, EnumFacing direction,
            ParkourProfile profile, List<ParkourJumpCandidate> candidates) {
        List<ParkourJumpCandidate> firstSegments = new ArrayList<>();
        collectStraightCandidates(context, src, direction, profile, firstSegments);
        for (ParkourJumpCandidate first : firstSegments) {
            if (first.isAscend() || first.isEdgeLaunch() || first.getForwardDistance() > 3) {
                continue;
            }
            ParkourJumpCandidate second = resolveStraightSegmentWithDistance(context, first.getDest(), direction,
                    profile, first.getForwardDistance());
            if (second == null || second.isAscend() || second.isEdgeLaunch()) {
                continue;
            }
            candidates.add(createChainCandidate(context, src, direction, profile, first, second));
        }
    }

    private static ParkourJumpCandidate pickLowestCostCandidate(List<ParkourJumpCandidate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(ParkourJumpCandidate::getCost));
        return candidates.get(0);
    }

    private static void appendBudgetedCandidates(CalculationContext context, List<ParkourJumpCandidate> target,
            List<ParkourJumpCandidate> source, int budget) {
        if (budget <= 0 || source.isEmpty()) {
            return;
        }
        source.removeIf(candidate -> !isRouteClear(context, candidate));
        if (source.isEmpty()) {
            return;
        }
        source.sort(Comparator.comparingDouble(ParkourJumpCandidate::getCost));
        for (int i = 0; i < Math.min(budget, source.size()); i++) {
            target.add(source.get(i));
        }
    }

    private static void trimCandidateBudget(List<ParkourJumpCandidate> candidates, int budget) {
        if (budget <= 0 || candidates.size() <= budget) {
            return;
        }
        candidates.sort(Comparator.comparingDouble(ParkourJumpCandidate::getCost));
        candidates.subList(budget, candidates.size()).clear();
    }

    private static int getTotalCandidateBudget(ParkourProfile profile) {
        switch (profile) {
            case EXTREME:
                return 10;
            case BALANCED:
                return 8;
            case STABLE:
            default:
                return 6;
        }
    }

    private static int getEdgeCandidateBudget(ParkourProfile profile) {
        switch (profile) {
            case EXTREME:
                return 3;
            case BALANCED:
                return 2;
            case STABLE:
            default:
                return 1;
        }
    }

    private static int getChainCandidateBudget(ParkourProfile profile) {
        switch (profile) {
            case EXTREME:
            case BALANCED:
                return 2;
            case STABLE:
            default:
                return 1;
        }
    }

    private static int getAngledCandidateBudget(ParkourProfile profile) {
        switch (profile) {
            case EXTREME:
            case BALANCED:
                return 2;
            case STABLE:
            default:
                return 1;
        }
    }

    private static ParkourJumpCandidate resolveStraightSegmentWithDistance(CalculationContext context,
            BetterBlockPos src, EnumFacing direction, ParkourProfile profile, int forwardDistance) {
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        int destX = x + xDiff * forwardDistance;
        int destZ = z + zDiff * forwardDistance;

        if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
            return null;
        }

        IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
        boolean canLand = (landingOn.getBlock() != Blocks.FARMLAND
                && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                || (Math.min(16, context.frostWalker + 2) >= forwardDistance
                        && MovementHelper.canUseFrostWalker(context, landingOn));
        if (!canLand) {
            return null;
        }
        if (!checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
            return null;
        }

        BetterBlockPos dest = new BetterBlockPos(destX, y, destZ);
        if (!hasLandingRecoverySpace(context, dest, direction)) {
            return null;
        }
        return createCandidate(context, src, dest, direction, null, forwardDistance, false,
                isNarrowLanding(context, dest), profile, costFromJumpDistance(forwardDistance) + context.jumpPenalty);
    }

    private static boolean isRouteClear(CalculationContext context, ParkourJumpCandidate candidate) {
        double minY = candidate.getDest().y < candidate.getSrc().y ? candidate.getSrc().y
                : Math.min(candidate.getSrc().y, candidate.getDest().y);
        double maxY = Math.max(candidate.getSrc().y, candidate.getDest().y) + 1.799D;
        return RouteCollisionSampler.isRouteClear(context, candidate.getRoutePoints(), minY, maxY, false);
    }

    private static ParkourJumpCandidate createCandidate(CalculationContext context, BetterBlockPos src,
            BetterBlockPos dest, EnumFacing direction, EnumFacing lateralDirection, int forwardDistance, boolean ascend,
            boolean narrowLanding, ParkourProfile profile, double baseCost) {
        return createCandidate(context, src, dest, direction, lateralDirection, forwardDistance, ascend, narrowLanding,
                profile, baseCost, false);
    }

    private static ParkourJumpCandidate createCandidate(CalculationContext context, BetterBlockPos src,
            BetterBlockPos dest, EnumFacing direction, EnumFacing lateralDirection, int forwardDistance, boolean ascend,
            boolean narrowLanding, ParkourProfile profile, double baseCost, boolean edgeLaunch) {
        ParkourJumpType type = resolveJumpType(forwardDistance, ascend, narrowLanding, lateralDirection, edgeLaunch);
        double landingRecoveryDistance = measureLandingRecoveryDistance(context, dest, direction);
        int chainPotential = estimateChainPotential(context, dest, direction);
        ParkourLaunchWindow launchWindow = createLaunchWindow(profile, forwardDistance, ascend,
                dest.y < src.y, lateralDirection != null, edgeLaunch);
        ParkourLandingWindow landingWindow = createLandingWindow(profile, narrowLanding, lateralDirection != null,
                edgeLaunch);
        double adjustedCost = adjustCost(baseCost, type, profile, launchWindow, landingRecoveryDistance, chainPotential,
                edgeLaunch);
        return new ParkourJumpCandidate(type, src, dest, direction, lateralDirection, forwardDistance, ascend,
                type.requiresSprint(), narrowLanding, edgeLaunch, new BetterBlockPos[] { dest }, adjustedCost,
                landingRecoveryDistance, chainPotential, launchWindow, landingWindow,
                buildRoutePoints(src, dest, direction, lateralDirection, type, launchWindow));
    }

    private static ParkourJumpCandidate createChainCandidate(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, ParkourProfile profile, ParkourJumpCandidate first, ParkourJumpCandidate second) {
        int forwardDistance = Math.max(first.getForwardDistance(), second.getForwardDistance());
        ParkourJumpType type = forwardDistance >= 3 ? ParkourJumpType.CHAIN_SPRINT : ParkourJumpType.CHAIN;
        ParkourLaunchWindow launchWindow = createLaunchWindow(profile, forwardDistance, false,
                second.getDest().y < src.y, false, false);
        ParkourLandingWindow landingWindow = createLandingWindow(profile,
                first.isNarrowLanding() || second.isNarrowLanding(), false, false);
        double landingRecoveryDistance = measureLandingRecoveryDistance(context, second.getDest(), direction);
        int chainPotential = Math.max(first.getChainPotential(), second.getChainPotential()) + 1;
        double baseCost = first.getCost() + second.getCost();
        double adjustedCost = baseCost - profile.getChainContinuationBonus() * 1.4D
                + profile.getChainComplexityPenalty() * 0.75D;
        return new ParkourJumpCandidate(type, src, second.getDest(), direction, null, forwardDistance, false,
                type.requiresSprint(), first.isNarrowLanding() || second.isNarrowLanding(), false,
                new BetterBlockPos[] { first.getDest(), second.getDest() }, adjustedCost, landingRecoveryDistance,
                chainPotential,
                launchWindow, landingWindow, joinRoutePoints(first.getRoutePoints(), second.getRoutePoints()));
    }

    private static ParkourJumpType resolveJumpType(int forwardDistance, boolean ascend, boolean narrowLanding,
            EnumFacing lateralDirection, boolean edgeLaunch) {
        if (ascend) {
            return lateralDirection != null ? ParkourJumpType.ASCEND_ANGLED : ParkourJumpType.ASCEND;
        }
        if (edgeLaunch) {
            return ParkourJumpType.EDGE;
        }
        if (lateralDirection != null) {
            return forwardDistance >= 3 ? ParkourJumpType.ANGLED_SPRINT : ParkourJumpType.ANGLED;
        }
        if (narrowLanding) {
            return ParkourJumpType.NARROW;
        }
        if (forwardDistance >= 4) {
            return ParkourJumpType.FLAT_SPRINT;
        }
        return ParkourJumpType.FLAT;
    }

    private static double adjustCost(double baseCost, ParkourJumpType type, ParkourProfile profile,
            ParkourLaunchWindow launchWindow, double landingRecoveryDistance, int chainPotential, boolean edgeLaunch) {
        double adjusted = baseCost;
        if (type.requiresSprint()) {
            adjusted += profile.getSprintPenalty();
        }
        if (type.isAngled()) {
            adjusted += profile.getAngledPenalty();
        }
        if (type.isNarrowLanding()) {
            adjusted += profile.getNarrowLandingPenalty();
        }
        if (edgeLaunch) {
            adjusted += profile.getEdgeLaunchPenalty();
        }
        adjusted += Math.max(0.0D, 0.34D - launchWindow.getMaxLateralError()) * profile.getLaunchPrecisionPenalty()
                * 4.0D;
        adjusted += Math.max(0.0D, 2.0D - landingRecoveryDistance) * profile.getRecoveryPenalty();
        if (chainPotential > 0) {
            adjusted -= profile.getChainContinuationBonus() * Math.min(2, chainPotential);
            if (chainPotential > 1) {
                adjusted += profile.getChainComplexityPenalty() * (chainPotential - 1);
            }
        }
        return adjusted;
    }

    private static ParkourLaunchWindow createLaunchWindow(ParkourProfile profile, int forwardDistance, boolean ascend,
            boolean descending, boolean angled, boolean edgeLaunch) {
        double ideal;
        switch (forwardDistance) {
            case 1:
                ideal = 0.18D;
                break;
            case 2:
                ideal = 0.40D;
                break;
            case 3:
                ideal = 0.72D;
                break;
            case 4:
                ideal = 0.94D;
                break;
            default:
                ideal = 0.58D;
                break;
        }
        if (ascend) {
            ideal -= 0.10D;
        }
        if (descending) {
            ideal -= 0.12D;
        }
        if (angled) {
            ideal += 0.04D;
        }
        if (edgeLaunch) {
            ideal += 0.02D;
        }
        double slack = profile.getLaunchWindowSlack() * (descending ? 0.82D : 1.0D) * (angled ? 0.88D : 1.0D)
                * (edgeLaunch ? 0.74D : 1.0D);
        return new ParkourLaunchWindow(
                ideal,
                Math.max(0.16D, ideal - slack),
                ideal + slack,
                (0.28D + Math.max(0, forwardDistance - 2) * 0.04D) * (angled ? 0.82D : 1.0D)
                        * (edgeLaunch ? 0.62D : 1.0D));
    }

    private static ParkourLandingWindow createLandingWindow(ParkourProfile profile, boolean narrowLanding,
            boolean angled, boolean edgeLaunch) {
        double radius = profile.getLandingRadius() * (narrowLanding ? 0.78D : 1.0D) * (angled ? 0.88D : 1.0D)
                * (edgeLaunch ? 0.94D : 1.0D);
        return new ParkourLandingWindow(radius * radius, radius * radius * 2.25D,
                edgeLaunch ? 0.68D : angled ? 0.62D : 0.78D);
    }

    private static Vec3d[] buildRoutePoints(BetterBlockPos src, BetterBlockPos dest, EnumFacing direction,
            EnumFacing lateralDirection, ParkourJumpType type, ParkourLaunchWindow launchWindow) {
        Vec3d srcCenter = VecUtils.getBlockPosCenter(src);
        double lateralScale = type == ParkourJumpType.EDGE ? 0.24D : 0.0D;
        Vec3d launchPoint = srcCenter.addVector(
                direction.getFrontOffsetX() * launchWindow.getIdealProgress(),
                0.0D,
                direction.getFrontOffsetZ() * launchWindow.getIdealProgress());
        if (lateralDirection != null && lateralScale > 0.0D) {
            launchPoint = launchPoint.addVector(lateralDirection.getFrontOffsetX() * lateralScale, 0.0D,
                    lateralDirection.getFrontOffsetZ() * lateralScale);
        }
        Vec3d destCenter = VecUtils.getBlockPosCenter(dest);
        Vec3d landingApproach = destCenter.addVector(
                -direction.getFrontOffsetX() * 0.12D,
                0.0D,
                -direction.getFrontOffsetZ() * 0.12D);
        if (lateralDirection == null) {
            return compact(srcCenter, launchPoint, landingApproach, destCenter);
        }
        if (type == ParkourJumpType.EDGE) {
            Vec3d edgeMidpoint = srcCenter.addVector(
                    direction.getFrontOffsetX() * Math.max(0.80D, launchWindow.getIdealProgress() + 0.38D)
                            + lateralDirection.getFrontOffsetX() * 0.20D,
                    0.0D,
                    direction.getFrontOffsetZ() * Math.max(0.80D, launchWindow.getIdealProgress() + 0.38D)
                            + lateralDirection.getFrontOffsetZ() * 0.20D);
            return compact(srcCenter, launchPoint, edgeMidpoint, landingApproach, destCenter);
        }
        Vec3d curveMidpoint = srcCenter.addVector(
                direction.getFrontOffsetX() * Math.max(0.95D, launchWindow.getIdealProgress() + 0.85D)
                        + lateralDirection.getFrontOffsetX() * 0.52D,
                0.0D,
                direction.getFrontOffsetZ() * Math.max(0.95D, launchWindow.getIdealProgress() + 0.85D)
                        + lateralDirection.getFrontOffsetZ() * 0.52D);
        return compact(srcCenter, launchPoint, curveMidpoint, landingApproach, destCenter);
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

    private static Vec3d[] joinRoutePoints(Vec3d[] first, Vec3d[] second) {
        List<Vec3d> joined = new ArrayList<>();
        appendRoute(joined, first);
        appendRoute(joined, second);
        return joined.toArray(new Vec3d[0]);
    }

    private static void appendRoute(List<Vec3d> target, Vec3d[] route) {
        if (route == null) {
            return;
        }
        for (Vec3d point : route) {
            if (point == null) {
                continue;
            }
            if (target.isEmpty() || target.get(target.size() - 1).squareDistanceTo(point) > 1.0E-4D) {
                target.add(point);
            }
        }
    }

    private static boolean hasLandingRecoverySpace(CalculationContext context, BetterBlockPos dest, EnumFacing direction) {
        if (!MovementHelper.canWalkThrough(context, dest.x, dest.y, dest.z)
                || !MovementHelper.canWalkThrough(context, dest.x, dest.y + 1, dest.z)) {
            return false;
        }
        if (context.isGoal(dest.x, dest.y, dest.z)) {
            return true;
        }
        if (hasRecoveryStep(context, dest, direction)) {
            return true;
        }
        EnumFacing opposite = direction.getOpposite();
        for (EnumFacing lateral : EnumFacing.HORIZONTALS) {
            if (lateral == direction || lateral == opposite) {
                continue;
            }
            if (hasRecoveryStep(context, dest, lateral)) {
                return true;
            }
        }
        return hasRecoveryStep(context, dest, opposite);
    }

    private static boolean hasRecoveryStep(CalculationContext context, BetterBlockPos dest, EnumFacing direction) {
        int aheadX = dest.x + direction.getFrontOffsetX();
        int aheadZ = dest.z + direction.getFrontOffsetZ();
        IBlockState support = context.bsi.get0(aheadX, dest.y - 1, aheadZ);
        if (!MovementHelper.canWalkOn(context, aheadX, dest.y - 1, aheadZ, support)
                && !MovementHelper.canUseFrostWalker(context, support)) {
            return false;
        }
        if (!MovementHelper.canWalkThrough(context, aheadX, dest.y, aheadZ)
                || !MovementHelper.canWalkThrough(context, aheadX, dest.y + 1, aheadZ)) {
            return false;
        }
        Block aheadFeet = context.getBlock(aheadX, dest.y, aheadZ);
        Block aheadHead = context.getBlock(aheadX, dest.y + 1, aheadZ);
        return !MovementHelper.avoidWalkingInto(aheadFeet) && !MovementHelper.avoidWalkingInto(aheadHead);
    }

    private static double measureLandingRecoveryDistance(CalculationContext context, BetterBlockPos dest,
            EnumFacing direction) {
        if (context.isGoal(dest.x, dest.y, dest.z)) {
            return 3.0D;
        }
        double distance = 0.0D;
        for (int step = 1; step <= 3; step++) {
            BetterBlockPos ahead = dest.offset(direction, step);
            IBlockState support = context.bsi.get0(ahead.x, ahead.y - 1, ahead.z);
            if (!MovementHelper.canWalkOn(context, ahead.x, ahead.y - 1, ahead.z, support)
                    || !MovementHelper.canWalkThrough(context, ahead.x, ahead.y, ahead.z)
                    || !MovementHelper.canWalkThrough(context, ahead.x, ahead.y + 1, ahead.z)
                    || MovementHelper.avoidWalkingInto(context.getBlock(ahead.x, ahead.y, ahead.z))
                    || MovementHelper.avoidWalkingInto(context.getBlock(ahead.x, ahead.y + 1, ahead.z))) {
                break;
            }
            distance += 1.0D;
        }
        return distance;
    }

    private static int estimateChainPotential(CalculationContext context, BetterBlockPos dest, EnumFacing direction) {
        int potential = 0;
        if (hasStraightChainContinuation(context, dest, direction)) {
            potential++;
        }
        if (context.parkourMode) {
            for (EnumFacing lateral : EnumFacing.HORIZONTALS) {
                if (lateral.getAxis() == direction.getAxis()) {
                    continue;
                }
                if (hasAngledChainContinuation(context, dest, direction, lateral)) {
                    potential++;
                }
            }
        }
        return potential;
    }

    private static boolean hasStraightChainContinuation(CalculationContext context, BetterBlockPos src,
            EnumFacing direction) {
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        for (int i = 2; i <= Math.min(4, context.canSprint ? 4 : 3); i++) {
            int destX = x + xDiff * i;
            int destZ = z + zDiff * i;
            if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }
            IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            if (landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn)
                    && checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAngledChainContinuation(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateralDirection) {
        if (!context.canSprint) {
            return false;
        }
        int x = src.x;
        int y = src.y;
        int z = src.z;
        int xDiff = direction.getFrontOffsetX();
        int zDiff = direction.getFrontOffsetZ();
        int lateralX = lateralDirection.getFrontOffsetX();
        int lateralZ = lateralDirection.getFrontOffsetZ();
        for (int forwardDistance = 2; forwardDistance <= 3; forwardDistance++) {
            int destX = x + xDiff * forwardDistance + lateralX;
            int destZ = z + zDiff * forwardDistance + lateralZ;
            if (!MovementHelper.fullyPassable(context, destX, y, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 1, destZ)
                    || !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                continue;
            }
            IBlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            if ((landingOn.getBlock() != Blocks.FARMLAND
                    && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                    && checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNarrowLanding(CalculationContext context, BetterBlockPos dest) {
        int blocked = 0;
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            int checkX = dest.x + facing.getFrontOffsetX();
            int checkZ = dest.z + facing.getFrontOffsetZ();
            if (!MovementHelper.canWalkThrough(context, checkX, dest.y, checkZ)
                    || !MovementHelper.canWalkThrough(context, checkX, dest.y + 1, checkZ)) {
                blocked++;
            }
        }
        return blocked >= 2;
    }

    private static boolean checkOvershootSafety(BlockStateInterface bsi, int x, int y, int z) {
        return !MovementHelper.avoidWalkingInto(bsi.get0(x, y, z).getBlock())
                && !MovementHelper.avoidWalkingInto(bsi.get0(x, y + 1, z).getBlock());
    }

    private static double costFromJumpDistance(int dist) {
        switch (dist) {
            case 1:
                return WALK_ONE_BLOCK_COST;
            case 2:
                return WALK_ONE_BLOCK_COST * 2;
            case 3:
                return WALK_ONE_BLOCK_COST * 3;
            case 4:
                return SPRINT_ONE_BLOCK_COST * 4;
            default:
                throw new IllegalStateException("Unsupported parkour distance " + dist);
        }
    }

    private static ParkourJumpCandidate unreachableCandidate(BetterBlockPos src, EnumFacing direction) {
        Vec3d center = VecUtils.getBlockPosCenter(src);
        return new ParkourJumpCandidate(
                ParkourJumpType.FLAT,
                src,
                src,
                direction,
                null,
                0,
                false,
                false,
                false,
                false,
                new BetterBlockPos[] { src },
                COST_INF,
                0.0D,
                0,
                new ParkourLaunchWindow(0.0D, 0.0D, 0.0D, 0.0D),
                new ParkourLandingWindow(0.0D, 0.0D, 0.0D),
                new Vec3d[] { center, center });
    }

    private void debugTick(BetterBlockPos segmentSrc, BetterBlockPos segmentDest, double progress, boolean airborne,
            JumpDecision jumpDecision, String stage) {
        StringBuilder builder = new StringBuilder();
        builder.append("tick=").append(debugTickCounter)
                .append(" stage=").append(stage)
                .append(" phase=").append(executionPhase)
                .append(" segment=").append(activeSegmentIndex + 1).append("/").append(candidate.getChainLength())
                .append(" src=").append(segmentSrc)
                .append(" dest=").append(segmentDest)
                .append(" feet=").append(ctx.playerFeet())
                .append(" pos=").append(formatVec(ctx.player().posX, ctx.player().posY, ctx.player().posZ))
                .append(" motion=").append(formatVec(ctx.player().motionX, ctx.player().motionY, ctx.player().motionZ))
                .append(" onGround=").append(ctx.player().onGround)
                .append(" airborne=").append(airborne)
                .append(" jumpTriggered=").append(jumpTriggered)
                .append(" jumpHold=").append(jumpHoldTicksRemaining);
        if (!Double.isNaN(progress)) {
            builder.append(" progress=").append(formatDouble(progress))
                    .append(" window=[").append(formatDouble(candidate.getLaunchWindow().getMinProgress()))
                    .append(",").append(formatDouble(candidate.getLaunchWindow().getMaxProgress())).append("]");
        }
        if (jumpDecision != null) {
            builder.append(" jumpNow=").append(jumpDecision.shouldJump)
                    .append(" jumpReason=").append(jumpDecision.reason)
                    .append(" projected=").append(formatDouble(jumpDecision.projectedProgress))
                    .append(" forwardSpeed=").append(formatDouble(jumpDecision.forwardSpeed))
                    .append(" lateral=").append(formatDouble(jumpDecision.lateralError))
                    .append(" maxLateral=").append(formatDouble(candidate.getLaunchWindow().getMaxLateralError()));
        }
        debug(builder.toString());
    }

    private void debug(String message) {
        if (!isBaritoneDebugEnabled()) {
            return;
        }
        Helper.HELPER.logDirect("[DBG][parkour] " + message);
    }

    private static void debugStatic(String message) {
        if (!isBaritoneDebugEnabled()) {
            return;
        }
        Helper.HELPER.logDirect("[DBG][parkour] " + message);
    }

    private static boolean isBaritoneDebugEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.BARITONE);
    }

    private static String summarizeCandidate(ParkourJumpCandidate candidate) {
        if (candidate == null) {
            return "<null>";
        }
        return "type=" + candidate.getType()
                + " src=" + candidate.getSrc()
                + " dest=" + candidate.getDest()
                + " forward=" + candidate.getForward()
                + " lateral=" + candidate.getLateral()
                + " dist=" + candidate.getForwardDistance()
                + " ascend=" + candidate.isAscend()
                + " sprint=" + candidate.requiresSprint()
                + " chain=" + candidate.getChainLength()
                + " cost=" + formatDouble(candidate.getCost())
                + " launch=[" + formatDouble(candidate.getLaunchWindow().getMinProgress())
                + "," + formatDouble(candidate.getLaunchWindow().getIdealProgress())
                + "," + formatDouble(candidate.getLaunchWindow().getMaxProgress()) + "]"
                + " landRadius=" + formatDouble(Math.sqrt(candidate.getLandingWindow().getMaxFlatDistanceSq()));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatVec(double x, double y, double z) {
        return "(" + formatDouble(x) + "," + formatDouble(y) + "," + formatDouble(z) + ")";
    }

    private static final class JumpDecision {
        private final boolean shouldJump;
        private final String reason;
        private final double projectedProgress;
        private final double forwardSpeed;
        private final double lateralError;

        private JumpDecision(boolean shouldJump, String reason, double projectedProgress, double forwardSpeed,
                double lateralError) {
            this.shouldJump = shouldJump;
            this.reason = reason;
            this.projectedProgress = projectedProgress;
            this.forwardSpeed = forwardSpeed;
            this.lateralError = lateralError;
        }
    }

    private enum ParkourExecutionPhase {
        ALIGN,
        RUNUP,
        SPRINT_PRIME,
        TAKEOFF,
        AIR_CORRECTION,
        LAND_CONFIRM,
        RECOVER
    }
}
