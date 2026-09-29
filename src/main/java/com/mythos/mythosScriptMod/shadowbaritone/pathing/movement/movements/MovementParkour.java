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

package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourTrajectory;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourFailureCache;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ParkourProfile;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.VecUtils;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Movement;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.RouteFollowHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourFailureReason;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpCandidate;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpType;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourLandingWindow;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourLaunchWindow;
import com.mythos.mythosScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

public class MovementParkour extends Movement implements IRoutePointMovement {

    private static final BetterBlockPos[] EMPTY = new BetterBlockPos[0];
    private static final double ROUTE_LOOKAHEAD_GROUND = 0.38D;
    private static final double ROUTE_LOOKAHEAD_AIR = 0.78D;
    private static final double FALL_FAIL_Y_MARGIN = 1.25D;
    private static final double AIRBORNE_Y_DELTA = 0.05D;
    private static final int STRAIGHT_CANDIDATE_BUDGET = 3;
    private static final ParkourFailureCache REJECTED_JUMPS = new ParkourFailureCache();
    private static final ExecutorService TRAJECTORY_SEARCH = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "parkour-trajectory-search");
        worker.setDaemon(true);
        return worker;
    });

    private final EnumFacing direction;
    private final ParkourJumpCandidate candidate;
    private Movement walkingEdge;

    /** Keep ground transitions in the same physics plan as the jumps they join. */
    public static Movement continuous(CalculationContext context, Movement original) {
        if (!context.parkourMode || !(original instanceof MovementTraverse || original instanceof MovementAscend))
            return original;
        BetterBlockPos source=original.getSrc(), target=original.getDest();
        for (BetterBlockPos pos : new BetterBlockPos[]{source,target}) {
            Block block=context.getBlock(pos.x,pos.y,pos.z);
            Block below=context.getBlock(pos.x,pos.y-1,pos.z);
            if (block==Blocks.LADDER || block==Blocks.VINE || MovementHelper.isWater(block)
                    || below==Blocks.LADDER || below==Blocks.VINE) return original;
        }
        AxisAlignedBB a=ParkourSurface.support(context,source), b=ParkourSurface.support(context,target);
        if (a==null || b==null) return original;
        Vec3d from=ParkourSurface.standPoint(context,source,a), to=ParkourSurface.standPoint(context,target,b);
        if (from==null || to==null) return original;
        EnumFacing forward=EnumFacing.getFacingFromVector(target.x-source.x,0,target.z-source.z);
        ParkourJumpCandidate candidate=new ParkourJumpCandidate(ParkourJumpType.FLAT_SPRINT,source,target,
                forward,null,1,b.maxY>a.maxY,true,false,false,null,original.getCost(),0,1,
                new ParkourLaunchWindow(.35,.05,.65,.5),new ParkourLandingWindow(.16,25,.6),new Vec3d[]{from,to});
        MovementParkour movement=new MovementParkour(context.getBaritone(),source,forward,candidate);
        movement.walkingEdge=original;
        movement.override(original.getCost());
        return movement;
    }

    private ParkourExecutionPhase executionPhase;
    private ParkourFailureReason lastFailureReason;
    private boolean jumpTriggered;
    private int jumpHoldTicksRemaining;
    private int activeSegmentIndex;
    private int segmentSettleTicksRemaining;
    private boolean debugBootstrapLogged;
    private int debugTickCounter;
    private List<ParkourTrajectory.Frame> trajectory;
    private ParkourTrajectory.Frame expectedFrame;
    private int trajectoryIndex;
    private int landingStableTicks;
    private boolean damageLandingPending;
    private int damageLandingTicks;
    private int lastPoisonDuration = -1;
    private int poisonWaitFrames;
    private Future<List<ParkourTrajectory.Frame>> pendingTrajectory;
    private int searchSequence;
    private String searchLabel;
    private ParkourTrajectory.Frame planningStart;
    private BetterBlockPos climbColumn;

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
        this.trajectory = null;
        this.expectedFrame = null;
        this.trajectoryIndex = 0;
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
        if (walkingEdge!=null) return walkingEdge.calculateCost(context);
        if (context.parkourMode) {
            Vec3d[] points=candidate.getRoutePoints();
            for (ParkourJumpCandidate option : surfaceCandidates(context, src, direction, candidate.getLateral(),
                    points.length==0?null:points[0])) {
                if (candidate.matches(option)) return option.getCost();
            }
            return COST_INF;
        }
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
    protected List<BetterBlockPos> preparationBreakPositions() {
        // A rising parkour jump may leave the ceiling footprint before taking
        // off. The trajectory checks the actual body against collision boxes;
        // requiring src.up(2) to be empty rejects these routes before planning.
        return Baritone.settings().parkourMode.value
                ? java.util.Collections.emptyList() : super.preparationBreakPositions();
    }

    @Override
    public void reset() {
        super.reset();
        preparedJump = null;
        passThroughLanding = false;
        flowLanding = false;
        chainPlans = null;
        plannedAhead = false;
        if (pendingTrajectory != null) {
            debug("search_result " + searchLabel + " outcome=reset");
            pendingTrajectory.cancel(true);
        }
        pendingTrajectory = null;
        this.landingStableTicks = 0;
        damageLandingPending = false;
        damageLandingTicks = 0;
        lastPoisonDuration = -1;
        poisonWaitFrames = 0;
        this.climbColumn = null;
        this.executionPhase = ParkourExecutionPhase.ALIGN;
        this.lastFailureReason = ParkourFailureReason.NONE;
        this.jumpTriggered = false;
        this.jumpHoldTicksRemaining = 0;
        this.activeSegmentIndex = 0;
        this.segmentSettleTicksRemaining = 0;
        this.debugBootstrapLogged = false;
        this.debugTickCounter = 0;
        this.trajectory = null;
        this.expectedFrame = null;
        this.trajectoryIndex = 0;
    }

    @Override
    public MovementState updateState(MovementState state) {
        try {
            return updateParkourState(state);
        } finally {
            ParkourDebugLog.INSTANCE.decision(this, state);
        }
    }

    private MovementState updateParkourState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        if (Baritone.settings().parkourMode.value) {
            return updateTrajectory(state);
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

    private Vec3d[] previewCorridor;

    /** Render verified controls, or an actually clear geometric corridor. */
    public Vec3d[] getPreviewPath() {
        List<ParkourTrajectory.Frame> frames=trajectory;
        ParkourTrajectory.Frame start=null;
        if(frames==null && preparedJump!=null) {
            frames=preparedJump.frames;
            start=preparedJump.start;
        }
        if(frames!=null && !frames.isEmpty()) {
            Vec3d[] points=new Vec3d[frames.size()+(start==null?0:1)];
            int index=0;
            if(start!=null) points[index++]=new Vec3d(start.x,start.y,start.z);
            for(ParkourTrajectory.Frame frame:frames) points[index++]=new Vec3d(frame.x,frame.y,frame.z);
            return points;
        }
        if(previewCorridor==null) {
            Vec3d[] points=candidate.getRoutePoints();
            previewCorridor=points!=null && points.length>=2
                    && ParkourSurface.clearCorridor(new CalculationContext(baritone,false),points)
                    ?points:new Vec3d[0];
        }
        return previewCorridor;
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
                || getValidPositions().contains(((com.mythos.mythosScriptMod.shadowbaritone.behavior.PathingBehavior) baritone
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
        // The surface-mode fast path must obey the same liquid exclusion as
        // ordinary parkour. A submerged pane is not a dry jump launch surface.
        if (context == null || context.getBlock(src.x, src.y, src.z) instanceof BlockLiquid
                || context.getBlock(src.x, src.y + 1, src.z) instanceof BlockLiquid) {
            return null;
        }
        if (context.parkourMode) {
            return surfaceCandidate(context, src, direction, lateralDirection);
        }
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
        if (selected != null && isBaritoneDebugEnabled()) {
            debugStatic("select src=" + src
                    + " dir=" + direction
                    + " lateral=" + lateralDirection
                    + " count=" + candidates.size()
                    + " chosen=" + summarizeCandidate(selected));
        }
        return selected;
    }

    @Override
    protected boolean shouldAutoSwimInLiquid() {
        // Logical feet can point into adjacent lava while the physical body
        // remains supported by a slab. Do not overwrite the simulated controls.
        return !Baritone.settings().parkourMode.value;
    }

    public boolean isPlanningTrajectory() {
        return pendingTrajectory != null && !pendingTrajectory.isDone();
    }
    public boolean hasImmediateContinuation() {
        return passThroughLanding || flowLanding;
    }

    public static boolean waterArrival(ParkourTrajectory.Frame frame, double x, double y, double z) {
        return Math.hypot(frame.x-x,frame.z-z)<.3 && Math.abs(frame.y-y-.05)<.18
                && Math.abs(frame.vy)<.08 && Math.hypot(frame.vx,frame.vz)<.03;
    }

    /** A predicted runup may intentionally revisit earlier path cells. */
    public boolean isFollowingTrajectory() {
        // The contact frame still belongs to this controller. Advancing by
        // block occupancy before it consumes that frame loses prepared chains.
        return trajectory != null && expectedFrame != null && expectedFrame.error(ctx.player()) <= .18;
    }

    private static final class PreparedJump {
        final ParkourTrajectory.Frame start;
        final List<ParkourTrajectory.Frame> frames;
        final boolean passThrough;
        PreparedJump(ParkourTrajectory.Frame start, List<ParkourTrajectory.Frame> frames, boolean passThrough) {
            this.start=start; this.frames=frames; this.passThrough=passThrough;
        }
    }
    private PreparedJump preparedJump;
    private boolean passThroughLanding;
    private boolean flowLanding;
    private java.util.Map<MovementParkour, PreparedJump> chainPlans;
    private boolean plannedAhead;

    /** Search the next independent stretch while the already validated inputs play. */
    private void prepareAhead(CalculationContext context) {
        if (plannedAhead || trajectory == null || trajectory.isEmpty()) return;
        plannedAhead = true;
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor executor =
                baritone.getPathingBehavior().getCurrent();
        if (executor == null) return;
        List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves=executor.getPath().movements();
        int last=moves.indexOf(this);
        if(last<0) return;
        ParkourTrajectory.Frame end=trajectory.get(trajectory.size()-1);
        if(chainPlans!=null) for(int i=last+1;i<moves.size();i++) {
            PreparedJump plan=chainPlans.get(moves.get(i));
            if(plan==null) break;
            last=i;end=plan.frames.get(plan.frames.size()-1);
        }
        if(!end.ground || end.onLadder() || last+1>=moves.size()
                || last+2<moves.size() && moves.get(last+2) instanceof MovementPistonLaunch) return;
        List<MovementParkour> chain=flowChain(context,moves,last+1);
        if(chain.isEmpty()) return;
        MovementParkour next=chain.get(0);
        if(next.pendingTrajectory!=null || next.preparedJump!=null) return;
        long snapshotStarted = System.nanoTime();
        List<Vec3d> targets=new ArrayList<>();
        AxisAlignedBB region=new AxisAlignedBB(next.src).grow(12,4,12);
        for(MovementParkour jump:chain) {
            AxisAlignedBB support=ParkourSurface.support(context,jump.dest);
            Vec3d point=support==null?null:standingTarget(context,jump.candidate,support);
            if(point==null) return;
            targets.add(point);region=region.union(new AxisAlignedBB(jump.dest).grow(12,4,12));
        }
        // Refresh the environment for the future region, but use the predicted
        // real terminal contact. The normal pending-plan check rejects any drift.
        ParkourTrajectory.Frame start=new ParkourTrajectory.Frame(ctx.player(),region);
        start.x=end.x;start.y=end.y;start.z=end.z;start.vx=0;start.vy=end.vy;start.vz=0;start.ground=true;
        List<AxisAlignedBB> boxes=new ArrayList<>(ctx.world().getCollisionBoxes(ctx.player(),region));
        java.util.function.Predicate<AxisAlignedBB> safe=ParkourSurface.safetySnapshot(context,region,null);
        Vec3d goal=targets.get(0);
        java.util.function.Predicate<ParkourTrajectory.Frame> arrived=
                ParkourTrajectory.settledArrival(boxes,goal.x,goal.y,goal.z,.18,safe);
        java.util.Map<MovementParkour,PreparedJump> plans=new java.util.HashMap<>();
        next.planningStart=start;next.chainPlans=plans;
        next.pendingTrajectory=next.submitTrajectory("prefetch", snapshotStarted, () -> {
            List<List<ParkourTrajectory.Frame>> connected=ParkourTrajectory.searchFlow(start,boxes,targets,context.canSprint,safe);
            if(connected.isEmpty()) return ParkourTrajectory.searchWithRunup(start,boxes,
                    goal.x,goal.y,goal.z,context.canSprint,safe,arrived);
            ParkourTrajectory.Frame from=start;
            for(int i=0;i<connected.size();i++) {
                List<ParkourTrajectory.Frame> frames=connected.get(i);
                boolean pass=i+1<connected.size();
                plans.put(chain.get(i),new PreparedJump(from,frames,pass));
                from=frames.get(frames.size()-1).restart(pass);
            }
            return connected.get(0);
        });
        debug("trajectory_prefetch source="+next.src+" destination="+next.dest);
    }

    private Future<List<ParkourTrajectory.Frame>> submitTrajectory(String kind, long snapshotStarted,
            java.util.function.Supplier<List<ParkourTrajectory.Frame>> work) {
        long submitted = System.nanoTime();
        String label = "kind=" + kind + " id=" + Integer.toHexString(System.identityHashCode(this))
                + "-" + (++searchSequence);
        searchLabel = label;
        debug("search_submitted " + label + " snapshotNanos=" + (submitted - snapshotStarted)
                + " source=" + src + " destination=" + dest);
        return TRAJECTORY_SEARCH.submit(() -> ParkourDebugLog.profile(label, submitted, work));
    }

    private MovementPistonLaunch pistonContinuation() {
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor executor =
                baritone.getPathingBehavior().getCurrent();
        if (executor == null) return null;
        List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves = executor.getPath().movements();
        int next = executor.getPosition() + 1;
        if (next >= moves.size() || !(moves.get(next) instanceof MovementPistonLaunch)) return null;
        MovementPistonLaunch launch = (MovementPistonLaunch) moves.get(next);
        return launch.getSrc().equals(dest) ? launch : null;
    }

    private List<MovementParkour> iceContinuation(CalculationContext context) {
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor executor =
                baritone.getPathingBehavior().getCurrent();
        return executor == null ? java.util.Collections.singletonList(this)
                : trajectoryChain(context, executor.getPath().movements(), executor.getPosition());
    }

    public static List<MovementParkour> flowChain(CalculationContext context,
            List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves, int first) {
        List<MovementParkour> result=new ArrayList<>();
        double ticks=0;
        for(int i=first;i<moves.size() && result.size()<32;i++) {
            if(!(moves.get(i) instanceof MovementParkour)) break;
            // Trigger entry has its own first-contact constraint. A generic
            // flow landing would press the plate early and bypass that search.
            if(i+1<moves.size() && moves.get(i+1) instanceof MovementPistonLaunch) break;
            MovementParkour jump=(MovementParkour)moves.get(i);
            // A descending jump can incur fall damage. Land and acknowledge
            // the server's velocity update before scheduling another takeoff.
            if(jump.dest.y < jump.src.y-1) break;
            Block target=context.getBlock(jump.dest.x,jump.dest.y,jump.dest.z);
            if(jump.candidate.getType()==ParkourJumpType.MOMENTUM_SPRINT || target==Blocks.LADDER
                    || target==Blocks.VINE || MovementHelper.isWater(target)
                    || ParkourSurface.support(context,jump.dest)==null) break;
            result.add(jump);
            ticks+=jump.candidate.getCost();
            // Reuse the normal planning horizon; prepareAhead overlaps the next
            // stretch with execution. Never cut a chain on disappearing ice.
            AxisAlignedBB support=ParkourSurface.support(context,jump.dest);
            if(ticks>=Baritone.settings().planningTickLookahead.value
                    && context.getBlock(jump.dest.x,MathHelper.floor(support.maxY-.001),jump.dest.z)!=Blocks.ICE) break;
        }
        return result;
    }

    private List<MovementParkour> flowContinuation(CalculationContext context) {
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor executor=
                baritone.getPathingBehavior().getCurrent();
        return executor==null?java.util.Collections.singletonList(this)
                :flowChain(context,executor.getPath().movements(),executor.getPosition());
    }

    /** Shared planning policy: offline checks must use the same handoff mode as execution. */
    public static List<MovementParkour> trajectoryChain(CalculationContext context,
            List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves, int first) {
        List<MovementParkour> result = new ArrayList<>();
        result.add((MovementParkour)moves.get(first));
        for (int i=first+1; i<moves.size() && result.size()<2; i++) {
            MovementParkour last = result.get(result.size()-1);
            if(last.dest.y < last.src.y-1) break;
            AxisAlignedBB support = ParkourSurface.support(context, last.dest);
            if (support == null) break;
            // Fractional blocks (notably soul sand) occupy the logical feet
            // cell. Classify the actual landing surface, not the ice below it.
            net.minecraft.block.Block surface = context.getBlock(last.dest.x,
                    (int) Math.floor(support.maxY-.001),last.dest.z);
            if (!(moves.get(i) instanceof MovementParkour)) break;
            MovementParkour next = (MovementParkour) moves.get(i);
            if(last.candidate.getType()==ParkourJumpType.MOMENTUM_SPRINT
                    || next.candidate.getType()==ParkourJumpType.MOMENTUM_SPRINT) break;
            // Ordinary platforms can brake and launch independently. Treating
            // every gap over three blocks as a mandatory momentum chain can
            // skip the intermediate pad and reject two individually valid jumps.
            boolean longExit=Math.hypot(next.dest.x-last.dest.x,next.dest.z-last.dest.z)>4.5;
            if (surface != Blocks.ICE && surface != Blocks.PACKED_ICE && surface != Blocks.SOUL_SAND && !longExit) break;
            // Packed-ice stair/tower routes require landing alignment. Only
            // flat chains use the mandatory immediate relaunch used on ice pads.
            if (surface == Blocks.PACKED_ICE
                    && last.dest.y != next.dest.y) break;
            result.add((MovementParkour) moves.get(i));
        }
        return result;
    }

    public static void rejectUnsearchable(CalculationContext context, BetterBlockPos from, BetterBlockPos to) {
        REJECTED_JUMPS.reject(context.failureScope,from.toLong(),to.toLong());
    }

    public boolean isStandingOnSourceSupport() {
        if (!ctx.player().onGround) return false;
        AxisAlignedBB support = ParkourSurface.support(new CalculationContext(baritone, false), src);
        if (support == null || Math.abs(ctx.player().posY - support.maxY) > .02) return false;
        AxisAlignedBB body = ctx.player().getEntityBoundingBox();
        return Math.min(body.maxX, support.maxX) - Math.max(body.minX, support.minX) > .025
                && Math.min(body.maxZ, support.maxZ) - Math.max(body.minZ, support.minZ) > .025;
    }

    public static boolean poisonMayInterrupt(int duration, int amplifier, int frames) {
        int interval = amplifier >= 5 ? 0 : 25 >> amplifier;
        if (duration <= 0) return false;
        if (interval <= 3) return true;
        int untilDamage = duration % interval;
        // Include local packet transit both before and after the scheduled tick.
        return duration >= interval && untilDamage <= frames + 3
                || untilDamage == 0 || untilDamage >= interval - 3;
    }

    private MovementState holdForDamage(MovementState state) {
        executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
        trajectory = null;
        expectedFrame = null;
        preparedJump = null;
        if (pendingTrajectory != null) {
            debug("search_result " + searchLabel + " outcome=damage");
            pendingTrajectory.cancel(true);
        }
        pendingTrajectory = null;
        chainPlans = null;
        plannedAhead = false;
        passThroughLanding = false;
        flowLanding = false;
        ctx.player().motionX = 0;
        ctx.player().motionZ = 0;
        return state.setInput(Input.SNEAK, true).setInput(Input.JUMP, false).setInput(Input.SPRINT, false);
    }

    private MovementState updateTrajectory(MovementState state) {
        state.retainInputs(EnumSet.noneOf(Input.class));
        net.minecraft.potion.PotionEffect poison = ctx.player().getActivePotionEffect(net.minecraft.init.MobEffects.POISON);
        int poisonDuration = poison == null ? 0 : poison.getDuration();
        boolean poisonCountingDown = lastPoisonDuration < 0 || poisonDuration < lastPoisonDuration;
        lastPoisonDuration = poisonDuration;
        if (ctx.player().onGround && poisonWaitFrames > 0) {
            if (poison != null && poisonCountingDown
                    && poisonMayInterrupt(poisonDuration, poison.getAmplifier(), poisonWaitFrames)) {
                debug("poison_launch_wait duration=" + poisonDuration);
                return holdForDamage(state);
            }
            poisonWaitFrames = 0;
        }
        net.minecraft.potion.PotionEffect jumpBoost = ctx.player().getActivePotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST);
        double safeFall = 3 + (jumpBoost == null ? 0 : jumpBoost.getAmplifier() + 1);
        if (!damageLandingPending && !ctx.player().capabilities.isCreativeMode && ctx.player().fallDistance > safeFall) {
            damageLandingPending = true;
            damageLandingTicks = 0;
        }
        if (damageLandingPending && ctx.player().onGround) {
            // A fall-damage velocity packet can arrive after local contact.
            // Never let that packet overwrite the next jump in midair.
            // ponytail: three grounded ticks cover local-server acknowledgement;
            // use packet acknowledgement if remote latency requires longer.
            if (++damageLandingTicks >= 3) damageLandingPending = false;
            debug("damage_landing_confirm ticks=" + damageLandingTicks + " hurtTime=" + ctx.player().hurtTime);
            return holdForDamage(state);
        }
        if (preparedJump != null) {
            PreparedJump prepared = preparedJump;
            preparedJump = null;
            if (prepared.start.error(ctx.player()) <= .12) {
                AxisAlignedBB region=new AxisAlignedBB(src).union(new AxisAlignedBB(dest)).grow(12,8,12);
                trajectory=ParkourTrajectory.replaySupported(new ParkourTrajectory.Frame(ctx.player(),region),
                        prepared.frames,ctx.world().getCollisionBoxes(ctx.player(),region),
                        ParkourSurface.safetySnapshot(new CalculationContext(baritone,false),region,null));
                if(trajectory.isEmpty()) trajectory=null;
                trajectoryIndex = 0;
                expectedFrame = null;
                passThroughLanding = trajectory!=null && prepared.passThrough;
                flowLanding = trajectory!=null;
                debug("trajectory_chain_handoff frames=" + (trajectory==null?0:trajectory.size()));
            }
        }
        if ((passThroughLanding || flowLanding) && trajectory != null && trajectoryIndex >= trajectory.size()
                && expectedFrame != null && expectedFrame.error(ctx.player()) <= .12 && ctx.player().onGround) {
            if(!passThroughLanding) {ctx.player().motionX=0;ctx.player().motionZ=0;}
            return state.setStatus(MovementStatus.SUCCESS);
        }
        CalculationContext context = new CalculationContext(baritone, false);
        boolean vineTarget = context.getBlock(dest.x, dest.y, dest.z) == Blocks.VINE;
        boolean waterTarget = MovementHelper.isWater(context.getBlock(dest.x, dest.y, dest.z))
                || MovementHelper.isWater(context.getBlock(dest.x, dest.y + 1, dest.z));
        boolean ladderTarget = context.getBlock(dest.x, dest.y, dest.z) == Blocks.LADDER || vineTarget;
        if(ladderTarget && continuousClimb(context,src,dest) && ctx.player().isOnLadder()
                && ctx.player().posY>=src.y-.15) {
            ParkourTrajectory.Frame current=new ParkourTrajectory.Frame(ctx.player(),new AxisAlignedBB(src).grow(2,3,2));
            ParkourTrajectory.Control climb=ParkourTrajectory.climbControl(current,dest.x+.5,dest.z+.5);
            if(climb!=null) {
                state.setInput(Input.SNEAK,true).setInput(Input.SPRINT,false).setInput(Input.JUMP,false)
                        .setInput(Input.MOVE_FORWARD,true);
                state.setTarget(new MovementState.MovementTarget(new Rotation(climb.yaw,ctx.player().rotationPitch),true));
                if(ctx.player().posY>=dest.y && ctx.playerFeet().x==dest.x && ctx.playerFeet().z==dest.z) {
                    passThroughLanding=true;
                    return state.setStatus(MovementStatus.SUCCESS);
                }
                return state;
            }
        }
        AxisAlignedBB landing = ParkourSurface.support(context, dest);
        if (ladderTarget || waterTarget) landing = new AxisAlignedBB(dest.x, dest.y-1, dest.z, dest.x+1, dest.y, dest.z+1);
        if (landing == null) {
            debug("trajectory_missing_support destination="+dest+" block="+context.get(dest.x,dest.y,dest.z));
            return fail(state, ParkourFailureReason.COLLISION_REJECTED);
        }
        final double landingY = landing.maxY;
        Vec3d stand = ladderTarget || waterTarget ? new Vec3d(dest.x + .5, landingY, dest.z + .5)
                : standingTarget(context,candidate,landing);
        if (stand == null) {
            debug("trajectory_missing_stance destination="+dest+" support="+landing);
            return fail(state, ParkourFailureReason.COLLISION_REJECTED);
        }
        double gx = stand.x, gz = stand.z;
        if (waterTarget && ctx.player().isInWater()
                && waterArrival(new ParkourTrajectory.Frame(ctx.player()), gx, landingY, gz)) {
            // Immersion alone is not a safe handoff: a fast downward catch can
            // cross the entire suspended pool before buoyancy arrests the fall.
            // Keep executing the verified water/braking inputs until settled.
            trajectory = null;
            expectedFrame = null;
            passThroughLanding = true;
            return state.setStatus(MovementStatus.SUCCESS);
        }
        // Keep the verified catch/braking controls through vine contact. Replacing
        // them with unsneaked counter-steering drops a high catch below its rung.
        // Contact is not arrival: the searched sequence still has to brake the
        // horizontal momentum before neutral sneak can safely hold this cell.
        if (ladderTarget && ctx.player().isOnLadder() && new BlockPos(ctx.player().posX, ctx.player().posY, ctx.player().posZ).equals(dest)
                && Math.abs(ctx.player().posX-gx)<.25 && Math.abs(ctx.player().posZ-gz)<.25
                && Math.hypot(ctx.player().motionX, ctx.player().motionZ) < .01
                && Math.abs(ctx.player().motionY + .0784000015258789) < .001) {
            executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
            expectedFrame = null;
            trajectory = null;
            landingStableTicks++;
            state.setInput(Input.SNEAK, true);
            state.setInput(Input.SPRINT, false);
            state.setInput(Input.JUMP, false);
            // This edge ends at the ladder, not inside its backing block.
            // Pillar owns the subsequent ascent; the wall can continue above
            // the ladder, leaving only its thin top as the next launch ledge.
            state.setInput(Input.MOVE_FORWARD, false);
            if (landingStableTicks >= 3 && Math.hypot(ctx.player().motionX, ctx.player().motionZ) < .01
                    && Math.abs(ctx.player().motionY + .0784000015258789) < .001) {
                // A neutral handoff tick releases sneak and can drop the feet
                // out of an isolated ladder before the climb controller runs.
                passThroughLanding = true;
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }
        boolean narrowLanding = landing.maxX-landing.minX < .8 || landing.maxZ-landing.minZ < .8;
        boolean ladderTopLanding = context.getBlock(dest.x,dest.y-1,dest.z) == Blocks.LADDER;
        MovementPistonLaunch pistonNext = pistonContinuation();
        final Vec3d launchDirection = pistonNext == null ? null : pistonNext.launchDirection();
        final Vec3d triggerLanding = pistonNext == null ? null : pistonNext.triggerLanding();
        final boolean ascendingTrigger = pistonNext != null;
        // Plan the first trigger contact, not a grounded landing after the
        // launcher has already moved. The trigger has a nonzero height.
        final double launchGx = triggerLanding == null ? gx
                : triggerLanding.x;
        final double launchGz = triggerLanding == null ? gz
                : triggerLanding.z;
        final AxisAlignedBB triggerVolume = new AxisAlignedBB(dest.x+.125, landingY, dest.z+.125,
                dest.x+.875, landingY+.25, dest.z+.875);
        final java.util.function.Predicate<ParkourTrajectory.Frame> timelyTriggerContact = f -> ascendingTrigger
                && pistonNext.readyToLaunch(f);
        java.util.function.Predicate<ParkourTrajectory.Frame> launchContact = f -> launchDirection != null
                && f.ground && Math.abs(f.y-landingY)<.03
                && Math.hypot(f.x-launchGx,f.z-launchGz)<.035;
        // A rising approach contacts the pressure plate before its final
        // grounded frame. Hand off at that actual trigger volume, rather than
        // braking/replanning when the moving piston invalidates the snapshot.
        boolean triggerContact = ascendingTrigger && ctx.player().motionY <= 0
                && ctx.player().posY >= landingY - .03 && ctx.player().posY < landingY + .25
                && ctx.player().getEntityBoundingBox().intersects(new AxisAlignedBB(
                        dest.x+.125, landingY, dest.z+.125,
                        dest.x+.875, landingY+.25, dest.z+.875));
        boolean pistonImpulse = pistonNext != null && ctx.player().motionY > .6
                && ctx.player().posY > landingY + .1
                && Math.hypot(ctx.player().posX-launchGx,ctx.player().posZ-launchGz)<.8;
        if (pistonNext != null && (triggerContact || pistonImpulse || launchContact.test(new ParkourTrajectory.Frame(ctx.player())))) {
            // PathExecutor must run the launcher in this same tick: a neutral
            // handoff frame sheds ground speed and changes the piston phase.
            passThroughLanding = true;
            return state.setStatus(MovementStatus.SUCCESS);
        }
        java.util.function.Predicate<ParkourTrajectory.Frame> arrived = f -> waterTarget
                ? f.inWater() && waterArrival(f, gx, landingY, gz)
                : ladderTarget
                ? Math.floor(f.x) == dest.x && Math.floor(f.y) == dest.y && Math.floor(f.z) == dest.z
                    && (!ladderTarget || f.onLadder() && f.control != null && f.control.sneak
                        && Math.abs(f.x-gx)<.25 && Math.abs(f.z-gz)<.25
                        && Math.abs(f.vy + .0784000015258789) < .001 && Math.hypot(f.vx, f.vz) < .01)
                : ascendingTrigger ? timelyTriggerContact.test(f)
                : pistonNext != null ? launchContact.test(f)
                : ladderTopLanding ? f.ground && Math.abs(f.y-landingY)<.001
                    && Math.hypot(f.x-gx,f.z-gz)<.18 && Math.hypot(f.vx,f.vz)<.025
                : f.ground && Math.abs(f.y-landingY)<.03 && Math.hypot(f.x-gx,f.z-gz)<(narrowLanding?.3:.18)
                    && (narrowLanding || Math.hypot(f.vx,f.vz)<.04);
        double distance = Math.hypot(ctx.player().posX - gx, ctx.player().posZ - gz);
        if (context.getBlock(dest.x, dest.y - 1, dest.z) == Blocks.SLIME_BLOCK
                && distance < .85 && ctx.player().posY >= landingY - .03
                && ctx.player().posY < landingY + .65 && ctx.player().motionY <= 0) {
            // Keep the contact/braking phase under one controller. Replanning
            // after a single sneaking tick releases the brake on slippery slime.
            executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
            expectedFrame = null;
            trajectory = null;
            state.setInput(Input.SNEAK, true);
            state.setInput(Input.SPRINT, false);
            state.setInput(Input.JUMP, false);
            double speed = Math.hypot(ctx.player().motionX, ctx.player().motionZ);
            MovementHelper.moveForwardWithRotation(ctx, state, new Vec3d(
                    gx - ctx.player().posX - ctx.player().motionX * 5, 0,
                    gz - ctx.player().posZ - ctx.player().motionZ * 5));
            state.setInput(Input.MOVE_FORWARD, distance > .12 || speed > .03);
            if (ctx.player().onGround && distance < .2 && speed < .03) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }
        boolean overhang = gx < landing.minX || gx > landing.maxX
                || gz < landing.minZ || gz > landing.maxZ;
        boolean narrow = overhang || landing.maxX - landing.minX < .8 || landing.maxZ - landing.minZ < .8;
        AxisAlignedBB feet = ctx.player().getEntityBoundingBox();
        if (narrow && !passThroughLanding && !flowLanding && ctx.player().onGround && Math.abs(ctx.player().posY - landing.maxY) < .02
                && feet.maxX > landing.minX && feet.minX < landing.maxX
                && feet.maxZ > landing.minZ && feet.minZ < landing.maxZ) {
            // Sneaking applies vanilla edge protection on the landing tick.
            // Brake first, then make small grounded corrections to the post's
            // centre. Do not surrender control just because the goal cell was
            // touched while there is still enough momentum to slide off.
            executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
            expectedFrame = null;
            trajectory = null;
            double speed = Math.hypot(ctx.player().motionX, ctx.player().motionZ);
            state.setInput(Input.SNEAK, true);
            state.setInput(Input.SPRINT, false);
            state.setInput(Input.JUMP, false);
            float yaw = (float) Math.toDegrees(Math.atan2(gz - ctx.player().posZ,
                    gx - ctx.player().posX)) - 90;
            state.setTarget(new MovementState.MovementTarget(new Rotation(yaw,
                    ctx.playerRotations().getPitch()), true));
            state.setInput(Input.MOVE_FORWARD, distance > (overhang ? .025 : .10) && speed < .06);
            landingStableTicks = distance < (overhang ? .03 : .13)
                    && speed < (overhang ? .002 : .025) ? landingStableTicks + 1 : 0;
            debug("landing_sneak distance=" + formatDouble(distance) + " speed=" + formatDouble(speed)
                    + " stableTicks=" + landingStableTicks);
            if (landingStableTicks >= 3) return state.setStatus(MovementStatus.SUCCESS);
            return state;
        }
        landingStableTicks = 0;
        if (!flowLanding && pistonNext == null && ctx.player().onGround && Math.abs(ctx.player().posY - landing.maxY) < .02
                && distance < .2 && Math.hypot(ctx.player().motionX, ctx.player().motionZ) < .04) {
            executionPhase = ParkourExecutionPhase.LAND_CONFIRM;
            // Match prepareAhead's stopped contact before the neutral handoff tick.
            ctx.player().motionX = 0;
            ctx.player().motionZ = 0;
            return state.setStatus(MovementStatus.SUCCESS);
        }
        if (ctx.player().posY < Math.min(src.y, dest.y) - 1.3) {
            return fail(state, ParkourFailureReason.MISSED_LANDING);
        }
        // Client/server tick alignment can move an airborne player by several
        // centimetres while the same forward input is still correct.  A small
        // prediction error must not clear W in midair; that was the cause of
        // otherwise reachable gaps being abandoned at their apex.
        if (expectedFrame != null && (expectedFrame.error(ctx.player()) > .18
                || expectedFrame.motionError(ctx.player().motionX,ctx.player().motionY,ctx.player().motionZ) > .003)) {
            debug("trajectory_replan error=" + expectedFrame.error(ctx.player())
                    + " motionError=" + expectedFrame.motionError(ctx.player().motionX,ctx.player().motionY,ctx.player().motionZ));
            trajectory = null;
            expectedFrame = null;
            plannedAhead = false;
        }
        if (trajectory == null || trajectoryIndex >= trajectory.size()) {
            boolean grounded = ctx.player().onGround;
            boolean hanging = ctx.player().isOnLadder();
            // A settled ladder/vine is a supported planning state, even though
            // onGround is false. Hold it while the worker searches a corner;
            // the short airborne correction budget cannot plan a whole climb.
            if (hanging) {
                state.setInput(Input.SNEAK, true);
                state.setInput(Input.MOVE_FORWARD, false);
                state.setInput(Input.SPRINT, false);
                state.setInput(Input.JUMP, false);
            }
            // Preserve support while an ice runway is still carrying momentum
            // into the asynchronous planner. Releasing forward alone cannot
            // stop the player before the edge; vanilla sneak clamps that drift.
            if (grounded) {
                ctx.player().motionX=0;ctx.player().motionZ=0;
                // Hold the launch state while the worker runs. Unsneaked slime
                // alternates grounded/airborne even at the same position.
                if (context.getBlock(MathHelper.floor(ctx.player().posX),
                        MathHelper.floor(ctx.player().posY - .2), MathHelper.floor(ctx.player().posZ)) == Blocks.SLIME_BLOCK)
                    state.setInput(Input.SNEAK, true);
            }
            AxisAlignedBB currentSupport = grounded ? ParkourSurface.support(context, ctx.playerFeet()) : null;
            if (currentSupport != null && (currentSupport.maxX - currentSupport.minX < .8
                    || currentSupport.maxZ - currentSupport.minZ < .8)) {
                ctx.player().motionX=0;ctx.player().motionZ=0;
            }
            if (pendingTrajectory != null) {
                if (!pendingTrajectory.isDone()) return state;
                try {
                    trajectory = pendingTrajectory.get();
                } catch (InterruptedException exception) {
                    debug("search_result " + searchLabel + " outcome=interrupted");
                    Thread.currentThread().interrupt();
                    return fail(state, ParkourFailureReason.COLLISION_REJECTED);
                } catch (ExecutionException exception) {
                    debug("search_result " + searchLabel + " outcome=error");
                    debug("trajectory_search_error " + exception.getCause());
                    return fail(state, ParkourFailureReason.COLLISION_REJECTED);
                } finally {
                    pendingTrajectory = null;
                }
                // The worker only reads an immutable physics snapshot. Never
                // apply a route after a teleport or drift changed its start.
                if (planningStart.error(ctx.player()) > .005
                        || planningStart.ground != ctx.player().onGround
                        || planningStart.motionError(ctx.player().motionX,ctx.player().motionY,ctx.player().motionZ) > .003) {
                    debug("search_result " + searchLabel + " outcome=drift error=" + planningStart.error(ctx.player()));
                    trajectory = null;
                    chainPlans = null;
                    plannedAhead = false;
                    return state;
                }
                debug("trajectory_plan frames=" + trajectory.size() + " worker=true");
                debug("search_result " + searchLabel + " outcome=" + (trajectory.isEmpty() ? "empty" : "applied")
                        + " frames=" + trajectory.size());
                if (chainPlans != null && !trajectory.isEmpty()) {
                    for (java.util.Map.Entry<MovementParkour, PreparedJump> entry : chainPlans.entrySet()) {
                        if (entry.getKey() == this) {
                            passThroughLanding = entry.getValue().passThrough;
                            flowLanding = true;
                        }
                        else entry.getKey().preparedJump = entry.getValue();
                    }
                }
            } else {
                // Vanilla zeros each motion component below .003 before travel.
                // Plan only after that clamp, not while an ice runway still drifts.
                if ((grounded || hanging) && Math.hypot(ctx.player().motionX, ctx.player().motionZ) > .002) return state;
                int runway = BlockStateInterface.get(ctx, src.down()).getBlock().slipperiness > .9F ? 10 : 4;
                long snapshotStarted = System.nanoTime();
                AxisAlignedBB region = new AxisAlignedBB(Math.min(src.x,dest.x)-runway, Math.min(src.y,dest.y)-3,
                    Math.min(src.z,dest.z)-runway, Math.max(src.x,dest.x)+runway+1,
                    Math.max(src.y,dest.y)+Math.max(4,context.maxJumpHeight+3),
                    Math.max(src.z,dest.z)+runway+1);
                List<MovementParkour> chain = grounded && !waterTarget && pistonNext==null ? flowContinuation(context) : java.util.Collections.emptyList();
                List<Vec3d> targets = new ArrayList<>();
                for (MovementParkour jump : chain) {
                    AxisAlignedBB support = ParkourSurface.support(context, jump.dest);
                    Vec3d target = support == null ? null : standingTarget(context, jump.candidate, support);
                    if (target == null) { targets.clear(); break; }
                    targets.add(target);
                    region = region.union(new AxisAlignedBB(jump.dest).grow(12, 4, 12));
                }
                List<AxisAlignedBB> boxes = new ArrayList<>(ctx.world().getCollisionBoxes(ctx.player(), region));
                java.util.function.Predicate<AxisAlignedBB> safe = ParkourSurface.safetySnapshot(context, region,
                        waterTarget ? dest : null,waterTarget || ctx.player().isInWater());
                java.util.function.Predicate<ParkourTrajectory.Frame> stableArrival =
                        ladderTarget || waterTarget || pistonNext != null || narrowLanding ? arrived
                        : ParkourTrajectory.settledArrival(boxes,gx,landingY,gz,.18,safe);
                ParkourTrajectory.Frame start = new ParkourTrajectory.Frame(ctx.player(), region);
                if ((grounded || hanging) && !start.inLava()) {
                    planningStart = start;
                    if(candidate.getType()==ParkourJumpType.MOMENTUM_SPRINT) {
                        chainPlans=null;
                        Vec3d[] points=candidate.getRoutePoints();
                        pendingTrajectory=submitTrajectory("momentum", snapshotStarted, () -> ParkourTrajectory.searchMomentumRoute(
                                start,boxes,points[1],points[2],context.canSprint,safe));
                        return state;
                    }
                     if (!chain.isEmpty() && targets.size() == chain.size()) {
                         boolean momentumFallback=iceContinuation(context).size()>1 && chain.size()>1;
                        java.util.Map<MovementParkour, PreparedJump> plans = new java.util.HashMap<>();
                         chainPlans = plans;
                         pendingTrajectory = submitTrajectory("flow", snapshotStarted, () -> {
                              List<List<ParkourTrajectory.Frame>> connected=ParkourTrajectory.searchFlow(
                                      start,boxes,targets,context.canSprint,safe);
                              if(connected.isEmpty() && momentumFallback) connected=ParkourTrajectory.searchConnection(
                                      start,boxes,targets.get(0),targets.get(1),context.canSprint,safe);
                              if(connected.isEmpty()) return ParkourTrajectory.searchWithRunup(start,
                                      boxes,gx,landingY,gz,context.canSprint,safe,stableArrival);
                             ParkourTrajectory.Frame from=start;
                             for(int i=0;i<connected.size();i++) {
                                 List<ParkourTrajectory.Frame> frames=connected.get(i);
                                 boolean pass=i<connected.size()-1;
                                 plans.put(chain.get(i),new PreparedJump(from,frames,pass));
                                 from=frames.get(frames.size()-1).restart(pass);
                             }
                             return connected.get(0);
                        });
                        return state;
                    }
                    chainPlans = null;
                    // An early overlap fires the moving pad before the predicted
                    // landing. Exclude it during search; a side approach may
                    // then enter the far edge with uninterrupted momentum.
                    java.util.function.Predicate<AxisAlignedBB> triggerSafe = box -> safe.test(box)
                            && (!ascendingTrigger || pistonNext.safeApproach(box));
                    pendingTrajectory = submitTrajectory("single", snapshotStarted, () -> ascendingTrigger
                            ? ParkourTrajectory.search(start, boxes, launchGx, landingY, launchGz,
                                    context.canSprint, .2, 100, 8_000_000_000L, triggerSafe, arrived)
                            : pistonNext != null
                            ? ParkourTrajectory.searchSprintLanding(start, boxes, launchGx, landingY, launchGz, safe, arrived,
                                    Double.NaN)
                            : ParkourTrajectory.searchWithRunup(start,
                                    boxes, gx, landingY, gz, context.canSprint, safe, stableArrival));
                    return state;
                }
                // Airborne corrections cannot wait several ticks. Bound the
                // synchronous search to one client tick instead.
                long correctionDeadline=System.nanoTime()+40_000_000L;
                trajectory = start.inLava() ? java.util.Collections.emptyList()
                        : ParkourTrajectory.search(start, boxes, gx, landingY, gz,
                                context.canSprint, .18, 150, 40_000_000L, safe, stableArrival);
                if(trajectory.isEmpty() && !ladderTarget && !waterTarget && pistonNext==null) {
                    trajectory=ParkourTrajectory.searchSupportedRecovery(start,boxes,landing,gx,gz,
                            ParkourSurface.safetySnapshot(context,region,null,false,landing),safe,correctionDeadline);
                    if(!trajectory.isEmpty()) {
                        chainPlans=null;
                        flowLanding=false;
                        passThroughLanding=false;
                        debug("trajectory_supported_recovery frames="+trajectory.size());
                    }
                }
                debug("trajectory_plan frames=" + trajectory.size() + " airborne=true");
            }
            trajectoryIndex = 0;
            if (trajectory.isEmpty()) {
                expectedFrame = null;
                if (grounded || ctx.player().isOnLadder()) {
                    // A held vine is a stable planning origin too. Repeating
                    // a fully rejected hanging edge otherwise never replans.
                    REJECTED_JUMPS.reject(ctx.world(), src.toLong(), dest.toLong());
                    debug("trajectory_rejected_edge source=" + src + " destination=" + dest);
                }
                return fail(state, ParkourFailureReason.COLLISION_REJECTED);
            }
        }
        if (ctx.player().onGround && !ctx.player().capabilities.isCreativeMode
                && ctx.player().getHealth() > 1 && poison != null && poisonCountingDown
                && poisonMayInterrupt(poisonDuration, poison.getAmplifier(), trajectory.size() - trajectoryIndex)) {
            // A continuously refreshed effect is not counting down to a damage
            // tick. Once it starts expiring, launch between its velocity packets.
            // ponytail: three-tick packet margin targets the local server;
            // remote high-latency servers need explicit packet acknowledgement.
            poisonWaitFrames = trajectory.size() - trajectoryIndex;
            debug("poison_launch_wait duration=" + poisonDuration + " frames=" + poisonWaitFrames);
            return holdForDamage(state);
        }
        expectedFrame = trajectory.get(trajectoryIndex++);
        prepareAhead(context);
        ParkourTrajectory.Control control = expectedFrame.control;
        if(control.resetHorizontal) {ctx.player().motionX=0;ctx.player().motionZ=0;}
        state.setInput(Input.SNEAK, control.sneak);
        executionPhase = ctx.player().onGround ? (control.jump ? ParkourExecutionPhase.TAKEOFF
                : ParkourExecutionPhase.RUNUP) : ParkourExecutionPhase.AIR_CORRECTION;
        state.setTarget(new MovementState.MovementTarget(new Rotation(control.yaw,ctx.playerRotations().getPitch()),true));
        state.setInput(Input.MOVE_FORWARD,control.forward);
        state.setInput(Input.MOVE_LEFT,control.strafe > 0);
        state.setInput(Input.MOVE_RIGHT,control.strafe < 0);
        state.setInput(Input.SPRINT,control.sprint);
        state.setInput(Input.JUMP,control.jump);
        // Ordinary jumps finish on slime; only MovementSlimeBounce relies on
        // its rebound. Suppress the bounce on the predicted contact tick.
        if (expectedFrame.ground && !ctx.player().onGround
                && Math.abs(expectedFrame.y - landingY) < .03
                && Math.abs(expectedFrame.x - gx) < .8 && Math.abs(expectedFrame.z - gz) < .8
                && (context.getBlock(dest.x, dest.y - 1, dest.z) == Blocks.SLIME_BLOCK
                     || pistonNext != null && !ascendingTrigger)
                && ctx.player().motionY < 0) {
            state.setInput(Input.SNEAK, true);
            state.setInput(Input.JUMP, false);
        }
        debug("traj_apply idx=" + trajectoryIndex + "/" + trajectory.size()
                + " yaw=" + formatDouble(control.yaw) + " fwd=" + control.forward
                + " sprint=" + control.sprint + " jump=" + control.jump
                + " pos=" + formatVec(ctx.player().posX, ctx.player().posY, ctx.player().posZ)
                + " expected=" + formatVec(expectedFrame.x, expectedFrame.y, expectedFrame.z));
        return state;
    }

    private static ParkourJumpCandidate surfaceCandidate(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateral) {
        return pickLowestCostCandidate(surfaceCandidates(context, src, direction, lateral));
    }

    public static List<ParkourJumpCandidate> surfaceCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateral) {
        return surfaceCandidates(context,src,direction,lateral,null);
    }

    public static boolean continuousClimb(CalculationContext context, BetterBlockPos src, BetterBlockPos dest) {
        Block source=context.getBlock(src.x,src.y,src.z),target=context.getBlock(dest.x,dest.y,dest.z);
        return src.x==dest.x && src.z==dest.z && dest.y==src.y+1
                && (source==Blocks.LADDER || source==Blocks.VINE)
                && (target==Blocks.LADDER || target==Blocks.VINE)
                && ParkourSurface.climbRoute(context,new Vec3d(src.x+.5,src.y,src.z+.5),
                        new Vec3d(dest.x+.5,dest.y,dest.z+.5))!=null;
    }

    private static List<ParkourJumpCandidate> surfaceCandidates(CalculationContext context, BetterBlockPos src,
            EnumFacing direction, EnumFacing lateral, Vec3d sourceStance) {
        if (!context.allowParkour || src.y >= 256 && !context.allowJumpAt256) return java.util.Collections.emptyList();
        if (MovementPistonLaunch.hasLaunch(context, src)) return java.util.Collections.emptyList();
        AxisAlignedBB support = ParkourSurface.support(context, src);
        boolean vineSource=context.getBlock(src.x,src.y,src.z)==Blocks.VINE && Baritone.settings().allowVines.value;
        boolean waterSource=MovementHelper.isWater(context.getBlock(src.x,src.y,src.z))
                || MovementHelper.isWater(context.getBlock(src.x,src.y+1,src.z));
        if(support==null && (vineSource || waterSource)) support=new AxisAlignedBB(src.x,src.y-1,src.z,src.x+1,src.y,src.z+1);
        if (support == null) return java.util.Collections.emptyList();
        Vec3d launchStand = sourceStance!=null ? sourceStance : waterSource ? new Vec3d(src.x+.5,src.y,src.z+.5)
                : ParkourSurface.standPoint(context, src, support);
        if (launchStand == null) return java.util.Collections.emptyList();
        List<ParkourJumpCandidate> result = new ArrayList<>();
        int maxDistance = context.canSprint ? 5 : 3;
        if (context.maxJumpHeight > 1.3) maxDistance += Math.min(4, (int)context.maxJumpHeight);
        // A longer ordinary runway reaches the same sprint terminal speed;
        // parkour mode itself supplies no extra jump range. Speculative eight-
        // block edges otherwise make A* descend into an impossible low exit
        // instead of climbing to the actual high takeoff. Ice/boosts and extra
        // descending airtime retain their separate allowances below.
        boolean slowLaunch=context.getBlock(src.x,MathHelper.floor(support.maxY),src.z)==Blocks.SOUL_SAND;
        if (context.canSprint && !slowLaunch && context.getBlock(src.x, (int)Math.floor(support.maxY-1), src.z).slipperiness > .9F) {
            maxDistance = 8;
        }
        int maxForward = maxDistance + 2;
        for (int forward = lateral==null ? 0 : 1; forward <= maxForward; forward++) {
            for (int side = lateral == null ? 0 : 1; side <= (lateral == null ? 0 : Math.min(maxDistance,forward)); side++) {
                // These bounded integer offsets cannot overflow/underflow.
                double gridDistance = Math.sqrt(forward*forward+side*side);
                if (gridDistance > maxDistance + 2.5) continue;
                // Tall takeoffs can descend past the six-cell scan while still
                // having a fully simulated, safe landing on the next platform.
                for (int dy = -8; dy <= Math.max(3, (int)Math.ceil(context.maxJumpHeight)+1); dy++) {
                    BetterBlockPos target = new BetterBlockPos(src.x + direction.getFrontOffsetX()*forward
                            + (lateral == null ? 0 : lateral.getFrontOffsetX()*side),src.y+dy,
                            src.z + direction.getFrontOffsetZ()*forward
                            + (lateral == null ? 0 : lateral.getFrontOffsetZ()*side));
                    if (REJECTED_JUMPS.contains(context.failureScope, src.toLong(), target.toLong())) continue;
                    AxisAlignedBB landing = ParkourSurface.support(context,target);
                    boolean ladderTarget = context.getBlock(target.x, target.y, target.z) == Blocks.LADDER
                            || context.getBlock(target.x, target.y, target.z) == Blocks.VINE
                                && Baritone.settings().allowVines.value;
                    // Fractional supports (for example cocoa) can jump directly
                    // into a ladder above the same column. Pillaring requires
                    // the source itself to be climbable and cannot express it.
                    if(forward==0 && !((ladderTarget && dy>0) || (context.maxJumpHeight>1.25 && dy>1))) continue;
                    boolean waterTarget = MovementHelper.isWater(context.getBlock(target.x, target.y, target.z))
                            || MovementHelper.isWater(context.getBlock(target.x, target.y + 1, target.z));
                    // A hanging player can release a vine and catch a lower
                    // column across a short gap. It cannot launch a normal
                    // ground jump, so reserve the wider range for descents.
                    boolean vineDrop=ladderTarget && dy<0 && dy>=-4 && gridDistance<=3;
                    if (vineSource && !vineDrop && (gridDistance>1.5 || Math.abs(dy)>1)) continue;
                    if (vineSource && dy>0 && !MovementPillar.hasAgainst(context,src.x,src.y,src.z)) continue;
                    // A lower ladder can be caught after the ordinary landing
                    // window. Keep ordinary jumps bounded, but let the physics
                    // search validate descending catches across wider gaps.
                    // Ice sections intentionally leave five-block straight gaps.
                    // The trajectory simulator validates the full sprint launch;
                    // do not reject those candidates merely because they are
                    // longer than the old four-block straight-jump heuristic.
                    // Descending jumps have additional airtime. Keep level
                    // jumps bounded; the trajectory solver validates the
                    // extra range rather than treating it as guaranteed.
                    double descentRange = context.canSprint ? (dy <= -4 ? 2 : dy < 0 ? 1 : 0) : 0;
                    if (!ladderTarget && gridDistance > maxDistance + .5 + descentRange) continue;
                    if (ladderTarget && gridDistance > maxDistance + .5 && dy >= 0) continue;
                    if (ladderTarget || waterTarget) {
                        Block above=context.getBlock(target.x,target.y+1,target.z);
                        // A continuing ladder occupies the head cell without
                        // blocking the body. Rejecting it hides every catch
                        // except the top of a ladder column.
                        if (!MovementHelper.fullyPassable(context, target.x, target.y + 1, target.z)
                                && !(ladderTarget && (above==Blocks.LADDER || above==Blocks.VINE))
                                && !(waterTarget && MovementHelper.isWater(above))) continue;
                        landing = new AxisAlignedBB(target.x,target.y-1,target.z,target.x+1,target.y,target.z+1);
                    }
                    EnumFacing ladderFace = null;
                    if (ladderTarget && context.getBlock(target.x, target.y, target.z) == Blocks.LADDER) {
                        ladderFace = context.get(target.x, target.y, target.z)
                                .getValue(net.minecraft.block.BlockLadder.FACING);
                        landing = new AxisAlignedBB(target.x,target.y-1,target.z,target.x+1,target.y,target.z+1)
                                .offset(ladderFace.getFrontOffsetX()*.06,0,ladderFace.getFrontOffsetZ()*.06);
                    }
                    if (landing == null) continue;
                    if (!MovementPistonLaunch.canApproach(context,src,target)) continue;
                    boolean pistonTarget=MovementPistonLaunch.hasLaunch(context,target);
                    double rise = landing.maxY - support.maxY;
                    // Fractional snow surfaces can be reached with a sprinting
                    // run-up even beyond the old 3.2-block ascent cutoff. The
                    // trajectory solver validates actual reach and collisions.
                    boolean ladderTop = context.getBlock(target.x,target.y-1,target.z) == Blocks.LADDER;
                    boolean waterRelay=waterTarget && !waterSource && rise>context.maxJumpHeight
                            && rise<=3 && gridDistance<=5.5
                            && waterRelayCorridor(context,launchStand,new Vec3d(target.x+.5,landing.maxY,target.z+.5));
                    if (rise > context.maxJumpHeight + (ladderTop ? 1 : 0) && !waterRelay) continue;
                    // Ordinary adjacent walking remains the cheaper, dedicated movement.
                    if (!pistonTarget && forward == 1 && side == 0 && Math.abs(rise) < .01
                            && ParkourSurface.clearWalkingSource(context,src.x,src.y,src.z)
                            && ParkourSurface.clearWalkingSource(context,target.x,target.y,target.z)) continue;
                    if (!pistonTarget && side == 0 && Math.abs(rise) < .01 && Math.abs(support.maxY-src.y) < .01) {
                        boolean walkable = true;
                        for (int step = 0; step <= forward; step++) {
                            int wx = src.x+direction.getFrontOffsetX()*step;
                            int wz = src.z+direction.getFrontOffsetZ()*step;
                            if (!context.get(wx,src.y-1,wz).isFullCube()
                                    || context.getBlock(wx,src.y-1,wz).slipperiness > .6F
                                    || !MovementHelper.canWalkOn(context,wx,src.y-1,wz)
                                    || !MovementHelper.canWalkThrough(context,wx,src.y,wz)
                                    || !MovementHelper.canWalkThrough(context,wx,src.y+1,wz)) {
                                walkable = false;
                                break;
                            }
                        }
                        // Traversal preserves sprint momentum along an ordinary
                        // corridor; a synthetic jump would stop to plan and brake.
                        if (walkable) continue;
                    }
                    if (!context.allowParkourAscend && rise > .6) continue;
                    Vec3d specialStand = ladderFace != null
                            ? new Vec3d(target.x+.5+ladderFace.getFrontOffsetX()*.06,landing.maxY,
                                    target.z+.5+ladderFace.getFrontOffsetZ()*.06)
                            : ladderTarget || waterTarget ? new Vec3d(target.x+.5,landing.maxY,target.z+.5)
                            : null;
                    List<Vec3d> stands=specialStand==null ? ParkourSurface.standPoints(context,target,landing)
                            : java.util.Collections.singletonList(specialStand);
                    for(Vec3d stand:stands) {
                    // Fence edges stand outside their cell. Price the actual
                    // landing, or a nearer cell centre hides the real gap and
                    // the route walks away from the ledge before jumping back.
                    double distance = Math.hypot(stand.x - launchStand.x, stand.z - launchStand.z);
                    boolean narrow = landing.maxX-landing.minX < .8 || landing.maxZ-landing.minZ < .8;
                    boolean sprint = distance >= 3 || distance > 2 && rise > .1;
                    ParkourJumpType type = narrow ? ParkourJumpType.NARROW : rise > .1 ? ParkourJumpType.ASCEND
                            : sprint ? ParkourJumpType.FLAT_SPRINT : ParkourJumpType.FLAT;
                    double cost = distance * (sprint ? SPRINT_ONE_BLOCK_COST : WALK_ONE_BLOCK_COST)
                            + context.jumpPenalty + 2 + Math.max(0,rise)*2;
                    if(slowLaunch) {
                        // Ice below soul sand changes acceleration, but does not
                        // remove its per-tick damping. Price the supported setup
                        // at that terminal speed instead of treating it as ice.
                        double drag=context.getBlock(src.x,MathHelper.floor(support.maxY-1),src.z).slipperiness*.91F;
                        double acceleration=.13*.98*.16277136/(drag*drag*drag);
                        double terminal=acceleration*drag*.4/(1-drag*.4);
                        cost+=.5/terminal;
                    }
                    // A blocked chord is not a proof that a turn-jump is impossible.
                    // Only expose it with a clear detour corridor, and draw that
                    // corridor rather than a line through the wall. Timed physics
                    // validation still happens before any movement is executed.
                    Vec3d[] routePoints = new Vec3d[]{launchStand,stand};
                    boolean throughWall = !ParkourSurface.jumpChordClear(launchStand,stand,
                            body->ParkourSurface.hasCollisionIgnoringNetherWart(context,body)
                                    || !ParkourSurface.safeBody(context,body,waterSource || waterTarget));
                    if(vineSource && rise>0) {
                        routePoints=ParkourSurface.climbRoute(context,launchStand,stand);
                        if(routePoints==null) continue;
                    } else if (throughWall && !waterRelay && !ParkourSurface.lowCeilingCorridor(context,launchStand,stand,waterSource || waterTarget)) {
                        routePoints=ParkourSurface.jumpClearanceRoute(context,launchStand,stand);
                        if(routePoints==null) {
                            routePoints=ParkourSurface.turnRoute(context,launchStand,stand);
                            if(routePoints==null) continue;
                            double detour=0;
                            for(int i=1;i<routePoints.length;i++) detour+=routePoints[i].distanceTo(routePoints[i-1]);
                            cost+=12+Math.max(0,detour-distance)*WALK_ONE_BLOCK_COST;
                        }
                    }
                    // A deep drop discards height that ordinary jumps cannot
                    // recover. Prefer a higher landing when both routes exist,
                    // rather than treating a four-block fall like a level hop.
                    double drop = Math.max(0, -rise);
                    // Price a descent in approximate falling ticks. A quadratic
                    // height penalty makes a valid tall drop more expensive than
                    // an entire detour and biases the graph toward low dead ends.
                    cost += Math.sqrt(2 * drop / .08);
                    // Near-range-limit diagonal leaps require considerably
                    // more alignment/runup. Prefer walking to the end of an
                    // available runway or using an intermediate landing.
                    double extremeRange = Math.max(0, distance - 4);
                    double rangePenalty = 16;
                    if (distance > 4.75 && rise > -2 && !slowLaunch) {
                        // Slipperiness alone does not provide long-jump speed.
                        // A tiny ice post lacks the backwards runway used by the
                        // trajectory search. Keep momentum-assisted edges legal,
                        // but prefer a complete supported detour over repeatedly
                        // trying speculative long leaps from an isolated post.
                        double ux = (target.x-src.x)/distance, uz = (target.z-src.z)/distance;
                        for (int back=1; back<=2; back++) {
                            BetterBlockPos runway = new BetterBlockPos(
                                    MathHelper.floor(src.x+.5-ux*back),src.y,
                                    MathHelper.floor(src.z+.5-uz*back));
                            AxisAlignedBB rear = ParkourSurface.support(context,runway);
                            if (rear == null || Math.abs(rear.maxY-support.maxY) > .03) {
                                rangePenalty = 256;
                                break;
                            }
                        }
                    }
                    cost += extremeRange * extremeRange * rangePenalty;
                    if (ladderTarget) {
                        // Catching a high rung has less airtime than a lower
                        // one. Prefer the reachable lower catch/intermediate
                        // pad over repeatedly testing the highest remote rung.
                        double ordinaryReach=4+Math.max(0,-rise)*.5-Math.max(0,rise)*.5;
                        cost+=Math.max(0,distance-ordinaryReach)*64;
                    }
                    ParkourLaunchWindow launch = new ParkourLaunchWindow(.35,.05,.65,.5);
                    ParkourLandingWindow land = new ParkourLandingWindow(.16,25,.6);
                    result.add(new ParkourJumpCandidate(type,src,target,direction,lateral,forward,rise>.1,sprint,
                            narrow,false,new BetterBlockPos[]{target},cost,0,0,launch,land,
                             routePoints));
                    }
                }
            }
        }
        if(context.canSprint && !slowLaunch) addMomentumExits(context,src,launchStand,result);
        return result;
    }

    /** A suspended pool can relay an incoming jump into a higher pool. Treat
     * the whole crossing as one simulated edge: stopping in the first pool
     * discards the horizontal momentum needed to cross the dry interval. */
    private static boolean waterRelayCorridor(CalculationContext context,Vec3d from,Vec3d to) {
        boolean intermediateWater=false;
        for(int i=0;i<=40;i++) {
            double t=i/40.0,x=from.x+(to.x-from.x)*t,y=from.y+(to.y-from.y)*t,z=from.z+(to.z-from.z)*t;
            AxisAlignedBB body=new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);
            if(ParkourSurface.hasCollisionIgnoringNetherWart(context,body) || !ParkourSurface.safeBody(context,body,true)) return false;
            if(t>.15 && t<.75 && MovementHelper.isWater(context.getBlock(MathHelper.floor(x),MathHelper.floor(y),MathHelper.floor(z))))
                intermediateWater=true;
        }
        return intermediateWater;
    }

    /** Expose a runway, last-edge contact and exit as one edge, so A* cannot
     * replace the useful incoming velocity with a cheaper walk onto soul sand. */
    private static void addMomentumExits(CalculationContext context, BetterBlockPos src, Vec3d start,
            List<ParkourJumpCandidate> candidates) {
        List<ParkourJumpCandidate> extra=new ArrayList<>();
        for(ParkourJumpCandidate first:candidates) {
            Vec3d[] points=first.getRoutePoints();
            if(points.length!=2) continue;
            Vec3d middle=points[1];
            if(start.y-middle.y<.5 || start.y-middle.y>2
                    || context.getBlock(MathHelper.floor(middle.x),MathHelper.floor(middle.y),MathHelper.floor(middle.z))!=Blocks.SOUL_SAND)
                continue;
            double length=Math.hypot(middle.x-start.x,middle.z-start.z);
            if(length<2.5 || length>6.5) continue;
            double ux=(middle.x-start.x)/length, uz=(middle.z-start.z)/length;
            // Only the outer edge can release soul-sand damping on the landing tick.
            if(context.getBlock(MathHelper.floor(middle.x+ux),MathHelper.floor(middle.y),MathHelper.floor(middle.z+uz))==Blocks.SOUL_SAND)
                continue;
            for(int dx=-5;dx<=5;dx++) for(int dz=-5;dz<=5;dz++) {
                double distance=Math.hypot(dx,dz);
                if(distance<3 || distance>5 || (dx*ux+dz*uz)/distance<.8) continue;
                for(int dy=0;dy<=2;dy++) {
                    BetterBlockPos end=new BetterBlockPos(first.getDest().add(dx,dy,dz));
                    if(REJECTED_JUMPS.contains(context.failureScope,src.toLong(),end.toLong())) continue;
                    AxisAlignedBB support=ParkourSurface.support(context,end);
                    if(support==null || support.maxY<=middle.y+.5 || support.maxY>middle.y+context.maxJumpHeight) continue;
                    if(context.getBlock(end.x,MathHelper.floor(support.maxY),end.z)==Blocks.SOUL_SAND) continue;
                    Vec3d target=ParkourSurface.standPoint(context,end,support);
                    if(target==null || !ParkourSurface.jumpChordClear(middle,target,
                            body->ParkourSurface.hasCollisionIgnoringNetherWart(context,body) || !ParkourSurface.safeBody(context,body))) continue;
                    double cost=first.getCost()+distance*SPRINT_ONE_BLOCK_COST+context.jumpPenalty+4;
                    extra.add(new ParkourJumpCandidate(ParkourJumpType.MOMENTUM_SPRINT,src,end,
                            first.getForward(),first.getLateral(),first.getForwardDistance(),target.y>start.y,true,false,false,
                            new BetterBlockPos[]{first.getDest(),end},cost,0,0,first.getLaunchWindow(),first.getLandingWindow(),
                            new Vec3d[]{start,middle,target}));
                }
            }
        }
        candidates.addAll(extra);
    }

    public static List<ParkourJumpCandidate> surfaceCandidates(CalculationContext context, BetterBlockPos src) {
        return surfaceCandidates(context,src,(Vec3d)null);
    }

    public static List<ParkourJumpCandidate> surfaceCandidates(CalculationContext context, BetterBlockPos src, Vec3d stance) {
        List<ParkourJumpCandidate> result = new ArrayList<>();
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            result.addAll(surfaceCandidates(context, src, facing, null,stance));
            result.addAll(surfaceCandidates(context, src, facing, facing.rotateY(),stance));
            result.addAll(surfaceCandidates(context, src, facing, facing.rotateYCCW(),stance));
        }
        return result;
    }

    public static MovementParkour toDestination(CalculationContext context, BetterBlockPos src, BetterBlockPos dest) {
        for (ParkourJumpCandidate option : surfaceCandidates(context, src)) {
            if (option.getDest().equals(dest)) {
                return new MovementParkour(context.getBaritone(), src, option.getForward(), option);
            }
        }
        return null;
    }

    public static MovementParkour fromCandidate(CalculationContext context,ParkourJumpCandidate candidate) {
        return new MovementParkour(context.getBaritone(),candidate.getSrc(),candidate.getForward(),candidate);
    }

    /** Preserve the side selected by A*, including through path assembly and execution. */
    public static Vec3d standingTarget(CalculationContext context,ParkourJumpCandidate candidate,AxisAlignedBB support) {
        Vec3d[] points=candidate.getRoutePoints();
        if(points.length==0) return ParkourSurface.standPoint(context,candidate.getDest(),support);
        Vec3d p=points[points.length-1];
        if(Math.abs(p.y-support.maxY)>.03 || p.x+.3<=support.minX || p.x-.3>=support.maxX
                || p.z+.3<=support.minZ || p.z-.3>=support.maxZ) return null;
        AxisAlignedBB body=new AxisAlignedBB(p.x-.3,p.y+.001,p.z-.3,p.x+.3,p.y+1.8,p.z+.3);
        return ParkourSurface.hasCollisionIgnoringNetherWart(context,body) || !ParkourSurface.safeBody(context,body) ? null : p;
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
        if (!isBaritoneDebugEnabled()) return;
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
        ParkourDebugLog.INSTANCE.event(message);
    }

    private static void debugStatic(String message) {
        if (!isBaritoneDebugEnabled()) {
            return;
        }
        ParkourDebugLog.INSTANCE.event(message);
    }

    private static boolean isBaritoneDebugEnabled() {
        return ParkourDebugLog.enabled();
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
