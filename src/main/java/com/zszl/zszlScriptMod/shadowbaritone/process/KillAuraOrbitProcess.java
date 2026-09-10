package com.zszl.zszlScriptMod.shadowbaritone.process;

import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementRouteTraverse;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.OrbitRoutePath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.PathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BaritoneProcessHelper;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathingCommandPath;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KillAuraOrbitProcess extends BaritoneProcessHelper {

    private static final int DEFAULT_MIN_LOOP_POINTS = 12;
    private static final int MIN_GUIDE_POINTS = KillAuraHandler.MIN_HUNT_ORBIT_SAMPLE_POINTS;
    private static final int MAX_GUIDE_POINTS = 360;
    private static final int MAX_SEARCH_RADIUS = 2;
    private static final double MAX_TARGET_MOVE_BEFORE_REPLAN = 0.65D;
    private static final double MAX_RADIUS_DELTA_BEFORE_REPLAN = 0.15D;
    private static final int MAX_PLAYER_FEET_Y_DELTA_BEFORE_REPLAN = 2;
    private static final int REQUEST_STALE_TICKS = 8;
    private static final int MIN_REBUILD_INTERVAL_TICKS = 6;
    private static final int PREBUILD_NEXT_ROUTE_REMAINING_MOVEMENTS = 2;
    private static final int FAILED_REBUILD_COOLDOWN_TICKS = 30;
    private static final double FAILED_REBUILD_TARGET_MOVE_TOLERANCE = 1.25D;
    private static final double GUIDE_ROUTE_Y_OFFSET = 0.5D;
    private static final double GUIDE_RENDER_Y_OFFSET = 0.08D;
    private static final double SUSPICIOUS_ROUTE_STEP = 0.35D;
    private static final double MAX_LOOP_ENTRY_DISTANCE = 1.60D;
    private static final double ORBIT_PLAYER_HALF_WIDTH = 0.299D;
    private static final double ORBIT_CLEARANCE_MARGIN = 0.045D;
    private static final double GUIDE_RADIUS_SHRINK_STEP = 0.18D;
    private static final double GUIDE_MAX_RADIUS_SHRINK = 2.4D;
    private static final int GUIDE_ANGLE_ADJUST_STEPS = 3;
    private static final double ROUTE_SAMPLE_STEP = 0.16D;
    private static final double MAX_VERTICAL_STEP_BETWEEN_NODES = 1.25D;
    private static final long REBUILD_BUDGET_NANOS = 2_000_000L;
    private static final double CACHE_POSITION_QUANTIZATION = 0.0625D;
    private static final double CACHE_SCAN_BOUNDS_QUANTIZATION = 0.25D;

    private State state = State.IDLE;
    private int targetEntityId = Integer.MIN_VALUE;
    private double desiredRadius = 4.2D;
    private double lastPlannedRadius = 4.2D;
    private double lastPlannedTargetX = 0.0D;
    private double lastPlannedTargetZ = 0.0D;
    private int lastPlannedFeetY = Integer.MIN_VALUE;
    private int lastPlanTick = Integer.MIN_VALUE;
    private int lastRequestTick = Integer.MIN_VALUE;
    private int lastRebuildTick = Integer.MIN_VALUE;
    private int lastFailedRebuildTick = Integer.MIN_VALUE;
    private int lastFailedTargetEntityId = Integer.MIN_VALUE;
    private int lastFailedFeetY = Integer.MIN_VALUE;
    private double lastFailedRadius = Double.NaN;
    private double lastFailedTargetX = Double.NaN;
    private double lastFailedTargetZ = Double.NaN;
    private long planRevision = 0L;
    private Goal currentGoal = null;
    private List<BetterBlockPos> orbitLoop = Collections.emptyList();
    private List<OrbitArcPlan> orbitArcs = Collections.emptyList();
    private List<Vec3d> renderLoop = Collections.emptyList();
    private RebuildCache rebuildCache = null;
    private PendingRebuild pendingRebuild = null;
    private RebuildCache retainedRebuildCache = null;
    private CollisionCacheKey retainedRebuildCacheKey = null;

    public KillAuraOrbitProcess(Baritone baritone) {
        super(baritone);
    }

    public boolean requestOrbit(EntityLivingBase target, double radius) {
        if (ctx.player() == null || ctx.world() == null || target == null || target.isDead) {
            orbitDebug("requestOrbit rejected state=%s target=%s radius=%.3f", this.state,
                    target == null ? "null" : Integer.toString(target.getEntityId()), radius);
            requestStop();
            return false;
        }
        if (shouldRejectAirborneOrbit()) {
            orbitDebug("requestOrbit rejected_hard_airborne state=%s target=%s radius=%.3f playerFeet=%s", this.state,
                    Integer.toString(target.getEntityId()), radius, formatPos(ctx.playerFeet()));
            requestStop();
            return false;
        }

        int nowTick = ctx.player().ticksExisted;
        double clampedRadius = Math.max(0.5D, radius);
        boolean sameTarget = this.state == State.ACTIVE && this.targetEntityId == target.getEntityId();
        boolean sameRadius = Math.abs(this.desiredRadius - clampedRadius) <= 0.001D;

        if (!sameTarget) {
            clearPublishedLoop();
            this.pendingRebuild = null;
        }

        this.state = State.ACTIVE;
        this.targetEntityId = target.getEntityId();
        this.desiredRadius = clampedRadius;
        this.lastRequestTick = nowTick;

        if (sameTarget && sameRadius && hasUsableLoop() && this.pendingRebuild == null) {
            return true;
        }
        if (isRecentFailedRebuild(target, clampedRadius, nowTick) && !hasUsableLoop()) {
            orbitDebug("requestOrbit deferred_recent_failure target=%d radius=%.3f playerFeet=%s", target.getEntityId(),
                    clampedRadius, formatPos(ctx.playerFeet()));
            return false;
        }

        String rebuildReason = getRebuildReason(target);
        orbitDebug("requestOrbit target=%d targetPos=%s radius=%.3f playerFeet=%s rebuildReason=%s",
                target.getEntityId(), formatEntityXZ(target), this.desiredRadius, formatPos(ctx.playerFeet()),
                rebuildReason == null ? "none" : rebuildReason);
        if (rebuildReason != null && shouldRebuildNow(nowTick, true)) {
            queueRebuild(target, rebuildReason);
            processPendingRebuild(target);
        }
        return hasUsableLoop();
    }

    public void requestStop() {
        if (this.state != State.IDLE) {
            this.state = State.STOPPING;
        }
    }

    public boolean hasUsableLoop() {
        return this.orbitLoop.size() >= getRequiredLoopPointCount() && this.orbitArcs.size() == this.orbitLoop.size();
    }

    public List<BetterBlockPos> getNavigationLoopSnapshot() {
        if (this.state != State.ACTIVE || !hasUsableLoop()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(this.orbitLoop);
    }

    public List<Vec3d> getRenderedLoopSnapshot() {
        if (this.state != State.ACTIVE || !hasUsableLoop() || this.renderLoop.size() < 2) {
            return Collections.emptyList();
        }
        return new ArrayList<>(this.renderLoop);
    }

    public List<Vec3d> getRenderedLoopView() {
        if (this.state != State.ACTIVE || !hasUsableLoop() || this.renderLoop.size() < 2) {
            return Collections.emptyList();
        }
        return this.renderLoop;
    }

    @Override
    public boolean isActive() {
        return this.state != State.IDLE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (this.state == State.STOPPING) {
            clearRuntime();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        EntityLivingBase target = getTrackedTarget();
        if (target == null || isRequestStale()) {
            orbitDebug("tick cancel targetValid=%s requestStale=%s state=%s", target != null, isRequestStale(), this.state);
            clearRuntime();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (shouldRejectAirborneOrbit()) {
            orbitDebug("tick cancel hard_airborne state=%s playerFeet=%s", this.state, formatPos(ctx.playerFeet()));
            clearRuntime();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        processPendingRebuild(target);

        boolean rebuilt = false;
        int nowTick = ctx.player() == null ? Integer.MIN_VALUE : ctx.player().ticksExisted;
        String rebuildReason = calcFailed
                ? (shouldDeferRebuildWhileAirborne() ? null : "calc_failed")
                : getRebuildReason(target);
        boolean recentFailedRebuild = isRecentFailedRebuild(target, this.desiredRadius, nowTick);
        if (rebuildReason != null && !recentFailedRebuild && shouldRebuildNow(nowTick, !hasUsableLoop())) {
            orbitDebug("tick rebuild target=%d reason=%s currentGoal=%s", target.getEntityId(), rebuildReason,
                    this.currentGoal);
            queueRebuild(target, rebuildReason);
            processPendingRebuild(target);
            rebuilt = this.pendingRebuild == null;
        } else if (rebuildReason != null && recentFailedRebuild && isOrbitDebugEnabled()) {
            orbitDebug("tick deferRebuildRecentFailure target=%d reason=%s currentGoal=%s", target.getEntityId(),
                    rebuildReason, this.currentGoal);
        } else if (rebuildReason != null && isOrbitDebugEnabled()) {
            orbitDebug("tick deferRebuild target=%d reason=%s currentGoal=%s", target.getEntityId(), rebuildReason,
                    this.currentGoal);
        } else if (this.pendingRebuild != null && shouldDeferRebuildWhileAirborne() && isOrbitDebugEnabled()) {
            orbitDebug("tick deferPendingAirborne target=%d reason=%s playerFeet=%s", target.getEntityId(),
                    this.pendingRebuild.reason, formatPos(ctx.playerFeet()));
        }
        if (!hasUsableLoop()) {
            if (this.pendingRebuild != null) {
                orbitTrace("tick defer pendingRebuild reason=%s phase=%s", this.pendingRebuild.reason,
                        this.pendingRebuild.phase);
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            orbitDebug("tick cancel unusableLoop nodes=%d arcs=%d render=%d pending=%s", this.orbitLoop.size(),
                    this.orbitArcs.size(), this.renderLoop.size(), this.pendingRebuild != null);
            clearRuntime();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        PathingCommand currentRouteCommand = tryContinueCurrentOrbitPath();
        if (currentRouteCommand != null) {
            return currentRouteCommand;
        }

        OrbitRoutePath desiredPath = determineDesiredRoutePath();
        if (desiredPath != null) {
            Goal commandGoal = getActiveOrbitCommandGoal(desiredPath);
            this.currentGoal = commandGoal;
            orbitTrace("tick route goal=%s pathGoal=%s routeKey=%s startNode=%d endNode=%d movements=%d", commandGoal,
                    desiredPath.getGoal(),
                    desiredPath.getRouteKey(), desiredPath.getStartNodeIndex(), desiredPath.getEndNodeIndex(),
                    desiredPath.movements().size());
            return new PathingCommandPath(commandGoal, PathingCommandType.SET_GOAL_AND_PATH, desiredPath);
        }

        BetterBlockPos fallbackNode = chooseNearestLoopNode(ctx.playerFeet());
        if (fallbackNode == null) {
            orbitDebug("tick cancel fallbackNode=null playerFeet=%s", formatPos(ctx.playerFeet()));
            clearRuntime();
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        Goal fallbackGoal = new GoalBlock(fallbackNode);
        PathingCommandType commandType = rebuilt || !fallbackGoal.equals(this.currentGoal)
                ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH
                : PathingCommandType.SET_GOAL_AND_PATH;
        this.currentGoal = fallbackGoal;
        orbitTrace("tick fallback goal=%s node=%s command=%s", fallbackGoal, formatPos(fallbackNode), commandType);
        return new PathingCommand(fallbackGoal, commandType);
    }

    @Override
    public void onLostControl() {
        this.currentGoal = null;
        if (this.state == State.STOPPING) {
            clearRuntime();
        }
    }

    @Override
    public String displayName0() {
        return "KillAura Orbit";
    }

    @Override
    public double priority() {
        return 4.2D;
    }

    private EntityLivingBase getTrackedTarget() {
        if (ctx.world() == null || this.targetEntityId == Integer.MIN_VALUE) {
            return null;
        }
        Entity entity = ctx.world().getEntityByID(this.targetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        if (living.isDead || living.getHealth() <= 0.0F) {
            return null;
        }
        return living;
    }

    private boolean isRequestStale() {
        return ctx.player() == null || ctx.player().ticksExisted - this.lastRequestTick > REQUEST_STALE_TICKS;
    }

    private boolean shouldRebuildNow(int nowTick, boolean force) {
        if (force || nowTick == Integer.MIN_VALUE || this.lastRebuildTick == Integer.MIN_VALUE) {
            return true;
        }
        return nowTick - this.lastRebuildTick >= MIN_REBUILD_INTERVAL_TICKS;
    }

    private String getRebuildReason(EntityLivingBase target) {
        if (target == null || ctx.player() == null) {
            return "missing_target_or_player";
        }
        if (!hasUsableLoop()) {
            if (shouldDeferRebuildWhileAirborne()) {
                return null;
            }
            return "missing_usable_loop";
        }
        if (shouldDeferRebuildWhileAirborne()) {
            return null;
        }
        double radiusDelta = Math.abs(this.lastPlannedRadius - this.desiredRadius);
        if (radiusDelta > MAX_RADIUS_DELTA_BEFORE_REPLAN) {
            return String.format(Locale.ROOT, "radius_delta=%.3f", radiusDelta);
        }
        double dx = target.posX - this.lastPlannedTargetX;
        double dz = target.posZ - this.lastPlannedTargetZ;
        double targetMoveSq = dx * dx + dz * dz;
        if (targetMoveSq > MAX_TARGET_MOVE_BEFORE_REPLAN * MAX_TARGET_MOVE_BEFORE_REPLAN) {
            return String.format(Locale.ROOT, "target_move=%.3f", Math.sqrt(targetMoveSq));
        }
        int currentFeetY = ctx.playerFeet() == null ? Integer.MIN_VALUE : ctx.playerFeet().getY();
        if (currentFeetY != Integer.MIN_VALUE
                && this.lastPlannedFeetY != Integer.MIN_VALUE
                && Math.abs(currentFeetY - this.lastPlannedFeetY) > MAX_PLAYER_FEET_Y_DELTA_BEFORE_REPLAN) {
            return "player_feet_y_shift=" + (currentFeetY - this.lastPlannedFeetY);
        }
        return null;
    }

    private boolean shouldRejectAirborneOrbit() {
        if (ctx.player() == null) {
            return true;
        }
        if (ctx.player().capabilities != null && ctx.player().capabilities.isFlying) {
            return true;
        }
        if (ctx.player().isElytraFlying()) {
            return true;
        }
        return false;
    }

    private boolean shouldDeferRebuildWhileAirborne() {
        return ctx.player() != null && !ctx.player().onGround && !shouldRejectAirborneOrbit();
    }

    private boolean isRecentFailedRebuild(EntityLivingBase target, double radius, int nowTick) {
        if (target == null || ctx.player() == null || ctx.playerFeet() == null) {
            return false;
        }
        if (this.lastFailedRebuildTick == Integer.MIN_VALUE || nowTick == Integer.MIN_VALUE) {
            return false;
        }
        if (nowTick - this.lastFailedRebuildTick > FAILED_REBUILD_COOLDOWN_TICKS) {
            return false;
        }
        if (target.getEntityId() != this.lastFailedTargetEntityId) {
            return false;
        }
        if (Math.abs(radius - this.lastFailedRadius) > MAX_RADIUS_DELTA_BEFORE_REPLAN) {
            return false;
        }
        if (ctx.playerFeet().getY() != this.lastFailedFeetY) {
            return false;
        }
        double dx = target.posX - this.lastFailedTargetX;
        double dz = target.posZ - this.lastFailedTargetZ;
        return dx * dx + dz * dz <= FAILED_REBUILD_TARGET_MOVE_TOLERANCE * FAILED_REBUILD_TARGET_MOVE_TOLERANCE;
    }

    private void recordFailedRebuild(EntityLivingBase target) {
        this.lastFailedRebuildTick = ctx.player() == null ? Integer.MIN_VALUE : ctx.player().ticksExisted;
        this.lastFailedTargetEntityId = target == null ? Integer.MIN_VALUE : target.getEntityId();
        this.lastFailedFeetY = ctx.playerFeet() == null ? Integer.MIN_VALUE : ctx.playerFeet().getY();
        this.lastFailedRadius = this.desiredRadius;
        this.lastFailedTargetX = target == null ? Double.NaN : target.posX;
        this.lastFailedTargetZ = target == null ? Double.NaN : target.posZ;
    }

    private void clearFailedRebuild() {
        this.lastFailedRebuildTick = Integer.MIN_VALUE;
        this.lastFailedTargetEntityId = Integer.MIN_VALUE;
        this.lastFailedFeetY = Integer.MIN_VALUE;
        this.lastFailedRadius = Double.NaN;
        this.lastFailedTargetX = Double.NaN;
        this.lastFailedTargetZ = Double.NaN;
    }

    private boolean isUsableLoop(List<BetterBlockPos> loop, List<OrbitArcPlan> arcs) {
        return loop != null && arcs != null && loop.size() >= getRequiredLoopPointCount() && arcs.size() == loop.size();
    }

    private void queueRebuild(EntityLivingBase target, String reason) {
        if (ctx.player() == null || target == null) {
            return;
        }
        int playerFeetY = ctx.playerFeet() == null ? MathHelper.floor(ctx.player().posY) : ctx.playerFeet().getY();
        if (this.pendingRebuild != null
                && this.pendingRebuild.matches(target.getEntityId(), this.desiredRadius, playerFeetY, target.posX, target.posZ)) {
            return;
        }
        RebuildCache cache = prepareRebuildCache(target, playerFeetY);
        this.pendingRebuild = new PendingRebuild(target.getEntityId(), target.posX, target.posZ, this.desiredRadius,
                playerFeetY, reason, hasUsableLoop(), ctx.player().ticksExisted, getGuidePointCount(),
                Math.atan2(ctx.player().posZ - target.posZ, ctx.player().posX - target.posX), cache);
        this.lastRebuildTick = ctx.player().ticksExisted;
        orbitDebug("rebuildQueued target=%d targetPos=%s radius=%.3f playerFeet=%s reason=%s reusedCollisionIndex=%s",
                target.getEntityId(), formatEntityXZ(target), this.desiredRadius, formatPos(ctx.playerFeet()), reason,
                cache != null && cache.reusedFromSession);
    }

    private void processPendingRebuild(EntityLivingBase target) {
        PendingRebuild pending = this.pendingRebuild;
        if (pending == null || target == null || ctx.player() == null) {
            return;
        }
        if (pending.targetEntityId != target.getEntityId()) {
            this.pendingRebuild = null;
            return;
        }
        if (shouldDeferRebuildWhileAirborne()) {
            return;
        }

        long budgetEnd = System.nanoTime() + REBUILD_BUDGET_NANOS;
        this.rebuildCache = pending.cache;
        try {
            boolean progressed = false;
            while (!pending.isFinished() && (System.nanoTime() < budgetEnd || !progressed)) {
                progressed = true;
                switch (pending.phase) {
                    case COLLECT_COLLISIONS:
                        continueCollisionCollection(pending, budgetEnd);
                        break;
                    case BUILD_GUIDE:
                        continueGuideLoopBuild(pending, target, budgetEnd);
                        break;
                    case BUILD_NODES:
                        continueOrbitNodeBuild(pending, target, budgetEnd);
                        break;
                    case BUILD_ARCS:
                        continueOrbitArcBuild(pending, target, budgetEnd);
                        break;
                    case FINALIZE:
                        finalizePendingRebuild(pending, target);
                        break;
                    case COMPLETE:
                    case FAILED:
                    default:
                        break;
                }
            }
        } finally {
            this.rebuildCache = null;
        }

        if (pending.isFinished()) {
            this.pendingRebuild = null;
        }
    }

    private RebuildCache prepareRebuildCache(EntityLivingBase target, int playerFeetY) {
        if (ctx.world() == null || target == null) {
            return new RebuildCache(null, null);
        }
        double scanRadius = Math.max(1.25D, this.desiredRadius + GUIDE_MAX_RADIUS_SHRINK + MAX_SEARCH_RADIUS + 1.25D);
        AxisAlignedBB scanBounds = new AxisAlignedBB(
                target.posX - scanRadius, playerFeetY - 4.0D, target.posZ - scanRadius,
                target.posX + scanRadius, playerFeetY + 2.85D, target.posZ + scanRadius);
        CollisionCacheKey cacheKey = CollisionCacheKey.of(target, this.desiredRadius, playerFeetY, scanBounds);
        if (cacheKey.equals(this.retainedRebuildCacheKey)
                && this.retainedRebuildCache != null
                && this.retainedRebuildCache.isCollisionCollectionComplete()) {
            this.retainedRebuildCache.resetPlanningSession(true);
            return this.retainedRebuildCache;
        }
        return new RebuildCache(scanBounds, cacheKey);
    }

    private void continueCollisionCollection(PendingRebuild pending, long budgetEnd) {
        RebuildCache cache = pending.cache;
        if (cache == null || cache.isCollisionCollectionComplete() || ctx.world() == null) {
            pending.phase = RebuildPhase.BUILD_GUIDE;
            return;
        }
        long collectStartNanos = System.nanoTime();
        boolean processed = false;
        while (!cache.isCollisionCollectionComplete() && (System.nanoTime() < budgetEnd || !processed)) {
            processed = true;
            cache.mutablePos.setPos(cache.cursorX, cache.cursorY, cache.cursorZ);
            IBlockState state = ctx.world().getBlockState(cache.mutablePos);
            if (state != null) {
                Block block = state.getBlock();
                if (block != null
                        && block != Blocks.CARPET
                        && !state.getMaterial().isReplaceable()
                        && state.getCollisionBoundingBox(ctx.world(), cache.mutablePos) != Block.NULL_AABB) {
                    cache.collisionScratch.clear();
                    state.addCollisionBoxToList(ctx.world(), cache.mutablePos, cache.scanBounds, cache.collisionScratch,
                            null, false);
                    for (AxisAlignedBB box : cache.collisionScratch) {
                        cache.addBlockingBox(box);
                    }
                }
            }
            cache.advanceCollisionCursor();
        }
        cache.collisionCollectNanos += System.nanoTime() - collectStartNanos;
        if (cache.isCollisionCollectionComplete()) {
            if (cache.cacheKey != null) {
                this.retainedRebuildCache = cache;
                this.retainedRebuildCacheKey = cache.cacheKey;
            }
            pending.phase = RebuildPhase.BUILD_GUIDE;
        }
    }

    private void continueGuideLoopBuild(PendingRebuild pending, EntityLivingBase target, long budgetEnd) {
        boolean processed = false;
        while (pending.guideIndex < pending.sampleCount && (System.nanoTime() < budgetEnd || !processed)) {
            processed = true;
            double angle = wrapRadians(pending.startAngle + pending.guideIndex * pending.step);
            GuidePointPlan plan = resolveGuidePoint(target, angle, pending.step, pending.routeY, pending.playerFeetY,
                    pending.previousGuidePoint);
            Vec3d guidePoint;
            if (plan == null) {
                double[] fallbackPoint = getClippedGuidePoint(target.posX, target.posZ, this.desiredRadius, angle);
                guidePoint = new Vec3d(fallbackPoint[0], pending.routeY, fallbackPoint[1]);
            } else {
                guidePoint = plan.point;
            }
            double sampledRadius = Math.sqrt(horizontalDistSq(guidePoint.x, guidePoint.z, target.posX, target.posZ));
            double clipLoss = Math.max(0.0D, this.desiredRadius - sampledRadius);
            if (clipLoss > 1.0E-3D) {
                pending.clippedCount++;
            }
            if (plan != null && plan.obstacleAdjusted) {
                pending.obstacleAdjustedCount++;
            }
            if (clipLoss > pending.maxClipLoss) {
                pending.maxClipLoss = clipLoss;
                pending.maxClipLossIndex = pending.guideIndex;
            }
            pending.minRadius = Math.min(pending.minRadius, sampledRadius);
            pending.maxRadius = Math.max(pending.maxRadius, sampledRadius);
            pending.guideLoop.add(guidePoint);
            pending.previousGuidePoint = guidePoint;
            pending.guideIndex++;
        }
        if (pending.guideIndex >= pending.sampleCount) {
            orbitDebug(
                    "guideLoop samples=%d startAngleDeg=%.2f stepDeg=%.2f radius=%.3f clipped=%d obstacleAdjusted=%d minRadius=%.3f maxRadius=%.3f maxClipLoss=%.3f@%d",
                    pending.sampleCount, Math.toDegrees(pending.startAngle), Math.toDegrees(pending.step), this.desiredRadius,
                    pending.clippedCount, pending.obstacleAdjustedCount,
                    pending.minRadius == Double.POSITIVE_INFINITY ? 0.0D : pending.minRadius,
                    pending.maxRadius, pending.maxClipLoss, pending.maxClipLossIndex);
            pending.renderLoop = buildRenderLoop(pending.guideLoop);
            pending.phase = RebuildPhase.BUILD_NODES;
        }
    }

    private void continueOrbitNodeBuild(PendingRebuild pending, EntityLivingBase target, long budgetEnd) {
        boolean processed = false;
        while (pending.nodeGuideIndex < pending.guideLoop.size() && (System.nanoTime() < budgetEnd || !processed)) {
            processed = true;
            Vec3d guidePoint = pending.guideLoop.get(pending.nodeGuideIndex);
            BetterBlockPos candidate = findNodeForGuidePoint(target, guidePoint, pending.previousNode, pending.playerFeetY);
            if (candidate == null) {
                pending.nullGuideCount++;
                if (pending.currentNullStreak == 0) {
                    pending.nullRunStart = pending.nodeGuideIndex;
                }
                pending.currentNullStreak++;
                if (pending.nodePlans.isEmpty()) {
                    pending.deferredLeadingGuidePoints.add(guidePoint);
                } else {
                    pending.nodePlans.get(pending.nodePlans.size() - 1).appendGuidePoint(guidePoint);
                }
                pending.nodeGuideIndex++;
                continue;
            }
            if (pending.currentNullStreak > 0) {
                pending.maxNullStreak = Math.max(pending.maxNullStreak, pending.currentNullStreak);
                orbitTrace("guideNullRun start=%d end=%d count=%d previousNode=%s resumedNode=%s", pending.nullRunStart,
                        pending.nodeGuideIndex - 1, pending.currentNullStreak, formatPos(pending.previousNode),
                        formatPos(candidate));
                pending.currentNullStreak = 0;
                pending.nullRunStart = -1;
            }
            if (pending.previousNode != null) {
                double nodeJump = Math.sqrt(horizontalDistSq(candidate.x + 0.5D, candidate.z + 0.5D,
                        pending.previousNode.x + 0.5D, pending.previousNode.z + 0.5D));
                if (nodeJump > 1.5D) {
                    pending.largeNodeJumpCount++;
                    orbitTrace("guideNodeJump index=%d jump=%.3f previous=%s candidate=%s guide=%s",
                            pending.nodeGuideIndex, nodeJump, formatPos(pending.previousNode),
                            formatPos(candidate), formatVec2(guidePoint));
                }
            }
            if (!pending.nodePlans.isEmpty() && candidate.equals(pending.nodePlans.get(pending.nodePlans.size() - 1).position)) {
                pending.nodePlans.get(pending.nodePlans.size() - 1).appendGuidePoint(guidePoint);
            } else {
                OrbitNodePlan nodePlan = new OrbitNodePlan(candidate);
                nodePlan.appendGuidePoint(guidePoint);
                pending.nodePlans.add(nodePlan);
            }
            pending.previousNode = candidate;
            pending.nodeGuideIndex++;
        }
        if (pending.nodeGuideIndex >= pending.guideLoop.size()) {
            finalizePendingNodeBuild(pending);
        }
    }

    private void finalizePendingNodeBuild(PendingRebuild pending) {
        if (pending.currentNullStreak > 0) {
            pending.maxNullStreak = Math.max(pending.maxNullStreak, pending.currentNullStreak);
            orbitTrace("guideNullRun start=%d end=%d count=%d previousNode=%s resumedNode=end_of_loop",
                    pending.nullRunStart, pending.guideLoop.size() - 1, pending.currentNullStreak,
                    formatPos(pending.previousNode));
            pending.currentNullStreak = 0;
        }

        if (!pending.nodePlans.isEmpty() && !pending.deferredLeadingGuidePoints.isEmpty()) {
            pending.nodePlans.get(pending.nodePlans.size() - 1).appendGuidePoints(pending.deferredLeadingGuidePoints);
        }

        if (pending.nodePlans.size() > 1
                && pending.nodePlans.get(0).position.equals(pending.nodePlans.get(pending.nodePlans.size() - 1).position)) {
            OrbitNodePlan tail = pending.nodePlans.get(pending.nodePlans.size() - 1);
            tail.appendGuidePoints(pending.nodePlans.get(0).guidePoints);
            pending.nodePlans.remove(0);
            pending.mergedEndpoints = true;
        }
        orbitDebug(
                "orbitLoopSummary guide=%d plannedNodes=%d deferredLeading=%d nullGuides=%d maxNullStreak=%d largeNodeJumps=%d mergedEndpoints=%s",
                pending.guideLoop.size(), pending.nodePlans.size(), pending.deferredLeadingGuidePoints.size(),
                pending.nullGuideCount, pending.maxNullStreak, pending.largeNodeJumpCount, pending.mergedEndpoints);
        if (pending.nodePlans.size() < getRequiredLoopPointCount()) {
            orbitDebug("orbitLoopRejected plannedNodes=%d minRequired=%d", pending.nodePlans.size(),
                    getRequiredLoopPointCount());
            pending.phase = RebuildPhase.FINALIZE;
            return;
        }
        pending.phase = RebuildPhase.BUILD_ARCS;
    }

    private void continueOrbitArcBuild(PendingRebuild pending, EntityLivingBase target, long budgetEnd) {
        boolean processed = false;
        while (pending.arcIndex < pending.nodePlans.size() && (System.nanoTime() < budgetEnd || !processed)) {
            processed = true;
            int index = pending.arcIndex++;
            OrbitNodePlan current = pending.nodePlans.get(index);
            OrbitNodePlan next = pending.nodePlans.get((index + 1) % pending.nodePlans.size());
            if (current.position.equals(next.position)) {
                continue;
            }

            List<Vec3d> routePoints = new ArrayList<>();
            appendRoutePoints(routePoints, current.guidePoints);
            appendRoutePoint(routePoints, getFirstGuidePoint(next));
            routePoints = sanitizeArcRoutePoints(current.position, next.position, routePoints);
            if (routePoints.size() < 2) {
                orbitTrace("buildArc skipped index=%d src=%s dest=%s routePoints=%d", index, formatPos(current.position),
                        formatPos(next.position), routePoints.size());
                continue;
            }
            pending.arcs.add(new OrbitArcPlan(current.position, next.position, routePoints.toArray(new Vec3d[0])));
        }
        if (pending.arcIndex >= pending.nodePlans.size()) {
            if (pending.arcs.size() != pending.nodePlans.size()) {
                orbitDebug("buildArcs rejected arcCount=%d nodePlans=%d", pending.arcs.size(), pending.nodePlans.size());
            }
            pending.phase = RebuildPhase.FINALIZE;
        }
    }

    private void finalizePendingRebuild(PendingRebuild pending, EntityLivingBase target) {
        List<BetterBlockPos> rebuiltLoop = extractOrbitPositions(pending.nodePlans);
        List<OrbitArcPlan> rebuiltArcs = pending.arcs.size() == pending.nodePlans.size() ? pending.arcs
                : Collections.emptyList();
        List<Vec3d> rebuiltRenderLoop = pending.renderLoop == null ? Collections.emptyList() : pending.renderLoop;
        boolean rebuiltUsable = isUsableLoop(rebuiltLoop, rebuiltArcs);

        if (rebuiltUsable) {
            this.orbitLoop = rebuiltLoop;
            this.orbitArcs = rebuiltArcs;
            this.renderLoop = rebuiltRenderLoop;
            this.currentGoal = null;
            this.lastPlannedRadius = this.desiredRadius;
            this.lastPlannedTargetX = target.posX;
            this.lastPlannedTargetZ = target.posZ;
            this.lastPlannedFeetY = ctx.playerFeet() == null ? Integer.MIN_VALUE : ctx.playerFeet().getY();
            this.lastPlanTick = ctx.player() == null ? Integer.MIN_VALUE : ctx.player().ticksExisted;
            this.lastRebuildTick = this.lastPlanTick;
            this.planRevision++;
            clearFailedRebuild();
        } else if (!pending.hadUsableLoop) {
            clearPublishedLoop();
            recordFailedRebuild(target);
        } else {
            recordFailedRebuild(target);
        }

        orbitDebug(
                "rebuildLoop target=%d targetPos=%s radius=%.3f guide=%d nodes=%d arcs=%d render=%d usable=%s retainedPrevious=%s revision=%d reason=%s reusedCollisionIndex=%s",
                target.getEntityId(), formatEntityXZ(target), this.desiredRadius, pending.guideLoop.size(),
                rebuiltLoop.size(), rebuiltArcs.size(), rebuiltRenderLoop.size(), rebuiltUsable,
                pending.hadUsableLoop && !rebuiltUsable, this.planRevision, pending.reason,
                pending.cache != null && pending.cache.reusedFromSession);
        logOrbitNodePlans(target, pending.nodePlans);
        logOrbitArcs(target, rebuiltArcs);
        logRebuildPerf(pending);
        pending.phase = rebuiltUsable ? RebuildPhase.COMPLETE : RebuildPhase.FAILED;
    }

    private void logRebuildPerf(PendingRebuild pending) {
        RebuildCache cache = pending == null ? null : pending.cache;
        if (!isOrbitDebugEnabled() || cache == null) {
            return;
        }
        orbitDebug(
                "rebuildPerf durationMs=%.2f collectMs=%.2f boxes=%d boxChecks=%d boxHits=%d segmentChecks=%d segmentHits=%d baseStandChecks=%d baseStandHits=%d losChecks=%d losHits=%d reusedCollisionIndex=%s",
                pending.getDurationNanos() / 1_000_000.0D,
                cache.collisionCollectNanos / 1_000_000.0D,
                cache.blockingBoxes.size(),
                cache.boxClearChecks,
                cache.boxClearHits,
                cache.segmentChecks,
                cache.segmentHits,
                cache.baseStandableChecks,
                cache.baseStandableHits,
                cache.lineOfSightChecks,
                cache.lineOfSightHits,
                cache.reusedFromSession);
    }

    private List<Vec3d> buildGuideLoop(EntityLivingBase target, int playerFeetY) {
        if (ctx.player() == null || ctx.world() == null || target == null) {
            return Collections.emptyList();
        }

        int samples = getGuidePointCount();
        double startAngle = Math.atan2(ctx.player().posZ - target.posZ, ctx.player().posX - target.posX);
        // Keep the generated orbit loop in the same counterclockwise order as the
        // fallback orbit destination logic so the runtime never flips direction.
        double step = (Math.PI * 2.0D) / samples;
        double routeY = playerFeetY + GUIDE_ROUTE_Y_OFFSET;
        int clippedCount = 0;
        int obstacleAdjustedCount = 0;
        double minRadius = Double.POSITIVE_INFINITY;
        double maxRadius = 0.0D;
        double maxClipLoss = 0.0D;
        int maxClipLossIndex = -1;

        List<Vec3d> guide = new ArrayList<>(samples);
        Vec3d previousGuidePoint = null;
        for (int i = 0; i < samples; i++) {
            double angle = wrapRadians(startAngle + i * step);
            GuidePointPlan plan = resolveGuidePoint(target, angle, step, routeY, playerFeetY, previousGuidePoint);
            Vec3d guidePoint;
            if (plan == null) {
                double[] fallbackPoint = getClippedGuidePoint(target.posX, target.posZ, this.desiredRadius, angle);
                guidePoint = new Vec3d(fallbackPoint[0], routeY, fallbackPoint[1]);
            } else {
                guidePoint = plan.point;
            }
            double sampledRadius = Math.sqrt(horizontalDistSq(guidePoint.x, guidePoint.z, target.posX, target.posZ));
            double clipLoss = Math.max(0.0D, this.desiredRadius - sampledRadius);
            if (clipLoss > 1.0E-3D) {
                clippedCount++;
            }
            if (plan != null && plan.obstacleAdjusted) {
                obstacleAdjustedCount++;
            }
            if (clipLoss > maxClipLoss) {
                maxClipLoss = clipLoss;
                maxClipLossIndex = i;
            }
            minRadius = Math.min(minRadius, sampledRadius);
            maxRadius = Math.max(maxRadius, sampledRadius);
            guide.add(guidePoint);
            previousGuidePoint = guidePoint;
        }
        orbitDebug(
                "guideLoop samples=%d startAngleDeg=%.2f stepDeg=%.2f radius=%.3f clipped=%d obstacleAdjusted=%d minRadius=%.3f maxRadius=%.3f maxClipLoss=%.3f@%d",
                samples, Math.toDegrees(startAngle), Math.toDegrees(step), this.desiredRadius, clippedCount,
                obstacleAdjustedCount,
                minRadius == Double.POSITIVE_INFINITY ? 0.0D : minRadius, maxRadius, maxClipLoss, maxClipLossIndex);
        return guide;
    }

    private GuidePointPlan resolveGuidePoint(EntityLivingBase target, double desiredAngle, double guideAngleStep,
            double routeY, int playerFeetY, Vec3d previousGuidePoint) {
        if (target == null) {
            return null;
        }

        double bestScore = Double.MAX_VALUE;
        GuidePointPlan bestPlan = null;
        double safeAngleStep = Math.max(Math.abs(guideAngleStep), Math.toRadians(1.25D));

        for (int angleStepIndex = 0; angleStepIndex <= GUIDE_ANGLE_ADJUST_STEPS; angleStepIndex++) {
            for (int direction = -1; direction <= 1; direction++) {
                if (angleStepIndex == 0 && direction != 0) {
                    continue;
                }
                if (angleStepIndex > 0 && direction == 0) {
                    continue;
                }

                double angleOffset = angleStepIndex == 0 ? 0.0D : direction * safeAngleStep * angleStepIndex;
                double trialAngle = wrapRadians(desiredAngle + angleOffset);
                double[] clippedPoint = getClippedGuidePoint(target.posX, target.posZ, this.desiredRadius, trialAngle);
                double clippedRadius = Math.sqrt(horizontalDistSq(clippedPoint[0], clippedPoint[1], target.posX, target.posZ));
                double maxShrink = Math.min(GUIDE_MAX_RADIUS_SHRINK, Math.max(0.0D, clippedRadius - 0.85D));
                int shrinkSteps = Math.max(0, (int) Math.ceil(maxShrink / GUIDE_RADIUS_SHRINK_STEP));

                for (int shrinkIndex = 0; shrinkIndex <= shrinkSteps; shrinkIndex++) {
                    double radiusLoss = shrinkIndex * GUIDE_RADIUS_SHRINK_STEP;
                    double trialRadius = Math.max(0.85D, clippedRadius - radiusLoss);
                    double pointX = target.posX + Math.cos(trialAngle) * trialRadius;
                    double pointZ = target.posZ + Math.sin(trialAngle) * trialRadius;
                    Vec3d trialPoint = new Vec3d(pointX, routeY, pointZ);

                    if (!isGuidePointClear(trialPoint)) {
                        continue;
                    }
                    if (previousGuidePoint != null && !isRouteSegmentClear(previousGuidePoint, trialPoint)) {
                        continue;
                    }
                    if (!hasNearbyStandableGuideCandidate(trialPoint, playerFeetY)) {
                        continue;
                    }

                    double score = Math.abs(angleOffset) * 18.0D
                            + radiusLoss * 12.0D
                            + horizontalDistSq(pointX, pointZ, clippedPoint[0], clippedPoint[1]) * 4.5D;
                    if (score < bestScore) {
                        bestScore = score;
                        bestPlan = new GuidePointPlan(trialPoint,
                                Math.abs(angleOffset) > 1.0E-4D || radiusLoss > 1.0E-4D);
                    }
                    if (angleStepIndex == 0 && shrinkIndex == 0) {
                        return bestPlan;
                    }
                }
            }
        }

        if (bestPlan != null) {
            return bestPlan;
        }

        double[] fallback = getClippedGuidePoint(target.posX, target.posZ, this.desiredRadius, desiredAngle);
        return new GuidePointPlan(new Vec3d(fallback[0], routeY, fallback[1]), true);
    }

    private List<OrbitNodePlan> buildOrbitLoop(EntityLivingBase target, List<Vec3d> guideLoop, int playerFeetY) {
        if (ctx.player() == null || ctx.world() == null || target == null || guideLoop == null || guideLoop.isEmpty()) {
            return Collections.emptyList();
        }

        List<OrbitNodePlan> planned = new ArrayList<>();
        List<Vec3d> deferredLeadingGuidePoints = new ArrayList<>();
        int nullGuideCount = 0;
        int currentNullStreak = 0;
        int maxNullStreak = 0;
        int nullRunStart = -1;
        int largeNodeJumpCount = 0;
        BetterBlockPos previous = null;
        for (int guideIndex = 0; guideIndex < guideLoop.size(); guideIndex++) {
            Vec3d guidePoint = guideLoop.get(guideIndex);
            BetterBlockPos candidate = findNodeForGuidePoint(target, guidePoint, previous, playerFeetY);
            if (candidate == null) {
                nullGuideCount++;
                if (currentNullStreak == 0) {
                    nullRunStart = guideIndex;
                }
                currentNullStreak++;
                if (planned.isEmpty()) {
                    deferredLeadingGuidePoints.add(guidePoint);
                } else {
                    planned.get(planned.size() - 1).appendGuidePoint(guidePoint);
                }
                continue;
            }
            if (currentNullStreak > 0) {
                maxNullStreak = Math.max(maxNullStreak, currentNullStreak);
                orbitDebug("guideNullRun start=%d end=%d count=%d previousNode=%s resumedNode=%s", nullRunStart,
                        guideIndex - 1, currentNullStreak, formatPos(previous), formatPos(candidate));
                currentNullStreak = 0;
                nullRunStart = -1;
            }
            if (previous != null) {
                double nodeJump = Math.sqrt(horizontalDistSq(candidate.x + 0.5D, candidate.z + 0.5D, previous.x + 0.5D,
                        previous.z + 0.5D));
                if (nodeJump > 1.5D) {
                    largeNodeJumpCount++;
                    orbitDebug("guideNodeJump index=%d jump=%.3f previous=%s candidate=%s guide=%s", guideIndex, nodeJump,
                            formatPos(previous), formatPos(candidate), formatVec2(guidePoint));
                }
            }
            if (!planned.isEmpty() && candidate.equals(planned.get(planned.size() - 1).position)) {
                planned.get(planned.size() - 1).appendGuidePoint(guidePoint);
            } else {
                OrbitNodePlan nodePlan = new OrbitNodePlan(candidate);
                nodePlan.appendGuidePoint(guidePoint);
                planned.add(nodePlan);
            }
            previous = candidate;
        }
        if (currentNullStreak > 0) {
            maxNullStreak = Math.max(maxNullStreak, currentNullStreak);
            orbitDebug("guideNullRun start=%d end=%d count=%d previousNode=%s resumedNode=end_of_loop", nullRunStart,
                    guideLoop.size() - 1, currentNullStreak, formatPos(previous));
        }

        if (!planned.isEmpty() && !deferredLeadingGuidePoints.isEmpty()) {
            planned.get(planned.size() - 1).appendGuidePoints(deferredLeadingGuidePoints);
        }

        boolean mergedEndpoints = false;
        if (planned.size() > 1 && planned.get(0).position.equals(planned.get(planned.size() - 1).position)) {
            OrbitNodePlan tail = planned.get(planned.size() - 1);
            tail.appendGuidePoints(planned.get(0).guidePoints);
            planned.remove(0);
            mergedEndpoints = true;
        }
        orbitDebug(
                "orbitLoopSummary guide=%d plannedNodes=%d deferredLeading=%d nullGuides=%d maxNullStreak=%d largeNodeJumps=%d mergedEndpoints=%s",
                guideLoop.size(), planned.size(), deferredLeadingGuidePoints.size(), nullGuideCount, maxNullStreak,
                largeNodeJumpCount, mergedEndpoints);
        int requiredLoopPoints = getRequiredLoopPointCount();
        if (planned.size() < requiredLoopPoints) {
            orbitDebug("orbitLoopRejected plannedNodes=%d minRequired=%d", planned.size(), requiredLoopPoints);
            return Collections.emptyList();
        }
        return planned;
    }

    private BetterBlockPos findNodeForGuidePoint(EntityLivingBase target, Vec3d guidePoint, BetterBlockPos previous,
            int playerFeetY) {
        if (target == null || guidePoint == null) {
            return null;
        }
        double desiredX = guidePoint.x;
        double desiredZ = guidePoint.z;
        double desiredRadius = Math.sqrt(horizontalDistSq(desiredX, desiredZ, target.posX, target.posZ));
        double desiredAngle = Math.atan2(desiredZ - target.posZ, desiredX - target.posX);
        int baseX = MathHelper.floor(desiredX);
        int baseZ = MathHelper.floor(desiredZ);
        int minFeetY = playerFeetY;
        int maxFeetY = playerFeetY + 1;
        Vec3d previousCenter = previous == null ? null : nodeCenter(previous);
        BetterBlockPos best = null;

        // Evaluate all nearby X/Z positions on the current layer before
        // considering the next layer above it.
        for (int y = minFeetY; y <= maxFeetY && best == null; y++) {
            double bestLayerScore = Double.MAX_VALUE;
            for (int searchRadius = 0; searchRadius <= MAX_SEARCH_RADIUS; searchRadius++) {
                for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                    for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                        if (searchRadius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != searchRadius) {
                            continue;
                        }
                        BetterBlockPos candidate = new BetterBlockPos(baseX + dx, y, baseZ + dz);
                        if (!isStandableOrbitFeet(candidate, target, maxFeetY)) {
                            continue;
                        }

                        double centerX = candidate.x + 0.5D;
                        double centerZ = candidate.z + 0.5D;
                        if (previousCenter != null) {
                            Vec3d candidateCenter = new Vec3d(centerX, candidate.y + GUIDE_ROUTE_Y_OFFSET, centerZ);
                            if (Math.abs(candidateCenter.y - previousCenter.y) > MAX_VERTICAL_STEP_BETWEEN_NODES
                                    || !isRouteSegmentClear(previousCenter, candidateCenter)) {
                                continue;
                            }
                        }
                        double actualRadius = Math.sqrt((centerX - target.posX) * (centerX - target.posX)
                                + (centerZ - target.posZ) * (centerZ - target.posZ));
                        double actualAngle = Math.atan2(centerZ - target.posZ, centerX - target.posX);
                        double radiusPenalty = Math.abs(actualRadius - desiredRadius) * 6.0D;
                        double anglePenalty = Math.abs(wrapRadians(actualAngle - desiredAngle)) * 8.0D;
                        double desiredPenalty = horizontalDistSq(centerX, centerZ, desiredX, desiredZ) * 2.0D;
                        double continuityPenalty = 0.0D;
                        if (previous != null) {
                            double stepDistanceSq = horizontalDistSq(centerX, centerZ, previous.x + 0.5D,
                                    previous.z + 0.5D);
                            double stepDistance = Math.sqrt(stepDistanceSq);
                            continuityPenalty = stepDistanceSq * 0.45D
                                    + Math.max(0.0D, stepDistance - 1.35D) * 8.0D;
                        }
                        double heightPenalty = Math.abs(candidate.y - playerFeetY) * 0.9D;
                        double score = radiusPenalty + anglePenalty + desiredPenalty + continuityPenalty + heightPenalty;

                        if (score < bestLayerScore) {
                            bestLayerScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean hasNearbyStandableGuideCandidate(Vec3d guidePoint, int playerFeetY) {
        if (guidePoint == null) {
            return false;
        }
        int baseX = MathHelper.floor(guidePoint.x);
        int baseZ = MathHelper.floor(guidePoint.z);
        int minFeetY = playerFeetY;
        int maxFeetY = playerFeetY + 1;
        for (int y = minFeetY; y <= maxFeetY; y++) {
            for (int searchRadius = 0; searchRadius <= 1; searchRadius++) {
                for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                    for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                        if (searchRadius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != searchRadius) {
                            continue;
                        }
                        if (isBaseStandableOrbitFeet(new BetterBlockPos(baseX + dx, y, baseZ + dz), maxFeetY)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isStandableOrbitFeet(BetterBlockPos standPos, EntityLivingBase target, int maxFeetY) {
        if (target == null || !isBaseStandableOrbitFeet(standPos, maxFeetY)) {
            return false;
        }
        return !KillAuraHandler.requireLineOfSight || hasLineOfSightFromStandPosCached(standPos, target);
    }

    private boolean isBaseStandableOrbitFeet(BetterBlockPos standPos, int maxFeetY) {
        if (ctx.world() == null || standPos == null || standPos.y > maxFeetY) {
            return false;
        }
        RebuildCache cache = this.rebuildCache;
        long key = BetterBlockPos.longHash(standPos);
        if (cache != null) {
            Boolean cached = cache.baseStandableCache.get(key);
            if (cached != null) {
                cache.baseStandableHits++;
                return cached;
            }
            cache.baseStandableChecks++;
        }

        IBlockState feetState = ctx.world().getBlockState(standPos);
        IBlockState headState = ctx.world().getBlockState(standPos.up());
        IBlockState supportState = ctx.world().getBlockState(standPos.down());
        boolean standable = !feetState.getMaterial().blocksMovement()
                && !headState.getMaterial().blocksMovement()
                && hasStandableTopSurface(standPos.down(), supportState)
                && isOrbitPlayerBoxClear(standPos.x + 0.5D, standPos.y, standPos.z + 0.5D,
                        ORBIT_PLAYER_HALF_WIDTH, ORBIT_CLEARANCE_MARGIN);
        if (standable && AutoFollowHandler.hasActiveLockChaseRestriction()) {
            standable = AutoFollowHandler.isPositionWithinActiveLockChaseBounds(standPos.x + 0.5D, standPos.z + 0.5D);
        }
        if (cache != null) {
            cache.baseStandableCache.put(key, standable);
        }
        return standable;
    }

    private boolean hasStandableTopSurface(BetterBlockPos supportPos, IBlockState supportState) {
        if (ctx.world() == null || supportPos == null || supportState == null || !supportState.getMaterial().blocksMovement()) {
            return false;
        }
        BlockPos blockPos = new BlockPos(supportPos.x, supportPos.y, supportPos.z);
        try {
            if (supportState.isSideSolid(ctx.world(), blockPos, EnumFacing.UP)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        AxisAlignedBB collisionBox = supportState.getCollisionBoundingBox(ctx.world(), blockPos);
        return collisionBox != null
                && collisionBox != Block.NULL_AABB
                && collisionBox.maxY >= 1.0D - 1.0E-4D;
    }

    private boolean hasLineOfSightFromStandPosCached(BetterBlockPos standPos, EntityLivingBase target) {
        RebuildCache cache = this.rebuildCache;
        LineOfSightKey key = cache == null ? null : LineOfSightKey.of(standPos, target);
        if (cache != null) {
            Boolean cached = cache.lineOfSightCache.get(key);
            if (cached != null) {
                cache.lineOfSightHits++;
                return cached;
            }
            cache.lineOfSightChecks++;
        }
        boolean clear = hasLineOfSightFromStandPos(standPos, target);
        if (cache != null) {
            cache.lineOfSightCache.put(key, clear);
        }
        return clear;
    }

    private boolean hasLineOfSightFromStandPos(BetterBlockPos standPos, EntityLivingBase target) {
        if (ctx.world() == null || standPos == null || target == null) {
            return false;
        }
        Vec3d eyePos = new Vec3d(standPos.x + 0.5D, standPos.y + 1.62D, standPos.z + 0.5D);
        Vec3d targetEye = new Vec3d(target.posX, target.posY + target.getEyeHeight() * 0.85D, target.posZ);
        RayTraceResult hit = ctx.world().rayTraceBlocks(eyePos, targetEye, false, true, false);
        return hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    private boolean isGuidePointClear(Vec3d guidePoint) {
        if (guidePoint == null) {
            return false;
        }
        return isOrbitPlayerBoxClear(guidePoint.x, guidePoint.y - GUIDE_ROUTE_Y_OFFSET, guidePoint.z,
                ORBIT_PLAYER_HALF_WIDTH, ORBIT_CLEARANCE_MARGIN);
    }

    private boolean isRouteSegmentClear(Vec3d start, Vec3d end) {
        if (ctx.world() == null || start == null || end == null) {
            return false;
        }
        RebuildCache cache = this.rebuildCache;
        if (cache != null) {
            SegmentKey cacheKey = SegmentKey.of(start, end);
            Boolean cached = cache.segmentClearCache.get(cacheKey);
            if (cached != null) {
                cache.segmentHits++;
                return cached;
            }
            cache.segmentChecks++;
            boolean clear = isRouteSegmentClearUncached(start, end);
            cache.segmentClearCache.put(cacheKey, clear);
            return clear;
        }
        return isRouteSegmentClearUncached(start, end);
    }

    private boolean isRouteSegmentClearUncached(Vec3d start, Vec3d end) {
        double segmentLength = start.distanceTo(end);
        int sampleCount = Math.max(2, (int) Math.ceil(segmentLength / ROUTE_SAMPLE_STEP));
        for (int sample = 0; sample <= sampleCount; sample++) {
            double t = sample / (double) sampleCount;
            double centerX = start.x + (end.x - start.x) * t;
            double routeY = start.y + (end.y - start.y) * t;
            double centerZ = start.z + (end.z - start.z) * t;
            if (!isOrbitPlayerBoxClear(centerX, routeY - GUIDE_ROUTE_Y_OFFSET, centerZ,
                    ORBIT_PLAYER_HALF_WIDTH, ORBIT_CLEARANCE_MARGIN)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOrbitPlayerBoxClear(double centerX, double feetY, double centerZ, double halfWidth,
            double extraMargin) {
        if (ctx.world() == null) {
            return false;
        }
        RebuildCache cache = this.rebuildCache;
        BoxKey cacheKey = cache == null ? null : new BoxKey(centerX, feetY, centerZ, halfWidth, extraMargin);
        if (cache != null) {
            Boolean cached = cache.boxClearCache.get(cacheKey);
            if (cached != null) {
                cache.boxClearHits++;
                return cached;
            }
            cache.boxClearChecks++;
        }
        AxisAlignedBB playerBox = new AxisAlignedBB(
                centerX - halfWidth, feetY, centerZ - halfWidth,
                centerX + halfWidth, feetY + 1.799D, centerZ + halfWidth)
                .grow(extraMargin, 0.0D, extraMargin);
        boolean clear;
        if (cache != null && cache.canQueryCollisionIndex(playerBox)) {
            clear = true;
            for (AxisAlignedBB blockingBox : cache.collectCandidateBoxes(playerBox)) {
                if (playerBox.intersects(blockingBox)) {
                    clear = false;
                    break;
                }
            }
        } else {
            clear = ctx.world().getCollisionBoxes(null, playerBox).isEmpty();
        }
        if (cache != null) {
            cache.boxClearCache.put(cacheKey, clear);
        }
        return clear;
    }

    private List<Vec3d> buildRenderLoop(List<Vec3d> guideLoop) {
        if (guideLoop == null || guideLoop.size() < 2) {
            return Collections.emptyList();
        }
        List<Vec3d> render = new ArrayList<>(guideLoop.size() + 1);
        for (Vec3d point : guideLoop) {
            render.add(new Vec3d(point.x, point.y - GUIDE_ROUTE_Y_OFFSET + GUIDE_RENDER_Y_OFFSET, point.z));
        }
        Vec3d first = render.get(0);
        render.add(new Vec3d(first.x, first.y, first.z));
        return render;
    }

    private int getGuidePointCount() {
        return MathHelper.clamp(KillAuraHandler.getConfiguredHuntOrbitSamplePoints(), MIN_GUIDE_POINTS, MAX_GUIDE_POINTS);
    }

    private int getRequiredLoopPointCount() {
        return Math.max(KillAuraHandler.MIN_HUNT_ORBIT_SAMPLE_POINTS,
                Math.min(DEFAULT_MIN_LOOP_POINTS, getGuidePointCount()));
    }

    private double[] getClippedGuidePoint(double centerX, double centerZ, double radius, double angle) {
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double endX = centerX + dirX * radius;
        double endZ = centerZ + dirZ * radius;

        if (!AutoFollowHandler.hasActiveLockChaseRestriction()
                || AutoFollowHandler.isPositionWithinActiveLockChaseBounds(endX, endZ)) {
            return new double[] { endX, endZ };
        }

        double low = 0.0D;
        double high = radius;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) * 0.5D;
            double testX = centerX + dirX * mid;
            double testZ = centerZ + dirZ * mid;
            if (AutoFollowHandler.isPositionWithinActiveLockChaseBounds(testX, testZ)) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return new double[] { centerX + dirX * low, centerZ + dirZ * low };
    }

    private PathingCommand tryContinueCurrentOrbitPath() {
        PathingBehavior pathingBehavior = baritone.getPathingBehavior();
        PathExecutor currentExecutor = pathingBehavior.getCurrent();
        boolean currentRouteActive = false;
        if (currentExecutor != null && currentExecutor.getPath() instanceof OrbitRoutePath) {
            OrbitRoutePath currentRoute = (OrbitRoutePath) currentExecutor.getPath();
            if (isRouteFromCurrentPlan(currentRoute)) {
                currentRouteActive = true;
                this.currentGoal = currentRoute.getGoal();
                int currentPosition = Math.max(0, currentExecutor.getPosition());
                int remainingMovements = currentRoute.movements().size() - currentPosition;
                if (remainingMovements > PREBUILD_NEXT_ROUTE_REMAINING_MOVEMENTS) {
                    return new PathingCommand(currentRoute.getGoal(), PathingCommandType.SET_GOAL_AND_PATH);
                }
            }
        }

        PathExecutor nextExecutor = pathingBehavior.getNext();
        if (nextExecutor != null && nextExecutor.getPath() instanceof OrbitRoutePath) {
            OrbitRoutePath nextRoute = (OrbitRoutePath) nextExecutor.getPath();
            if (isRouteFromCurrentPlan(nextRoute)) {
                if (currentRouteActive && this.currentGoal != null) {
                    return new PathingCommand(this.currentGoal, PathingCommandType.SET_GOAL_AND_PATH);
                }
                this.currentGoal = nextRoute.getGoal();
                return new PathingCommand(nextRoute.getGoal(), PathingCommandType.SET_GOAL_AND_PATH);
            }
        }
        return null;
    }

    private boolean isRouteFromCurrentPlan(OrbitRoutePath route) {
        if (route == null) {
            return false;
        }
        String routeKey = route.getRouteKey();
        String prefix = this.planRevision + ":";
        return routeKey != null && routeKey.startsWith(prefix);
    }

    private OrbitRoutePath determineDesiredRoutePath() {
        int startIndex = chooseNearestLoopNodeIndex(ctx.playerFeet());
        if (startIndex < 0) {
            orbitTrace("determineRoute source=nearest failed playerFeet=%s", formatPos(ctx.playerFeet()));
            return null;
        }
        double loopDistance = getPlayerDistanceToLoop();
        if (loopDistance > MAX_LOOP_ENTRY_DISTANCE) {
            orbitTrace(
                    "determineRoute source=approach startIndex=%d playerFeet=%s loopDistance=%.3f threshold=%.3f",
                    startIndex, formatPos(ctx.playerFeet()), loopDistance, MAX_LOOP_ENTRY_DISTANCE);
            return null;
        }

        PathingBehavior pathingBehavior = baritone.getPathingBehavior();
        PathExecutor currentExecutor = pathingBehavior.getCurrent();
        if (currentExecutor != null && currentExecutor.getPath() instanceof OrbitRoutePath) {
            OrbitRoutePath currentRoute = (OrbitRoutePath) currentExecutor.getPath();
            OrbitRoutePath desiredNext = buildOrbitRoutePath(currentRoute.getEndNodeIndex());
            if (desiredNext != null) {
                orbitTrace("determineRoute source=current currentKey=%s currentEndNode=%d nextKey=%s",
                        currentRoute.getRouteKey(), currentRoute.getEndNodeIndex(), desiredNext.getRouteKey());
                return desiredNext;
            }
        }

        PathExecutor nextExecutor = pathingBehavior.getNext();
        if (nextExecutor != null && nextExecutor.getPath() instanceof OrbitRoutePath) {
            OrbitRoutePath nextRoute = (OrbitRoutePath) nextExecutor.getPath();
            orbitTrace("determineRoute source=next nextKey=%s startNode=%d endNode=%d", nextRoute.getRouteKey(),
                    nextRoute.getStartNodeIndex(), nextRoute.getEndNodeIndex());
            return nextRoute;
        }

        orbitTrace("determineRoute source=nearest startIndex=%d playerFeet=%s", startIndex, formatPos(ctx.playerFeet()));
        return buildOrbitRoutePath(startIndex);
    }

    private Goal getActiveOrbitCommandGoal(OrbitRoutePath desiredPath) {
        if (desiredPath == null) {
            return this.currentGoal;
        }
        PathExecutor currentExecutor = baritone.getPathingBehavior().getCurrent();
        if (currentExecutor != null && currentExecutor.getPath() instanceof OrbitRoutePath) {
            OrbitRoutePath currentRoute = (OrbitRoutePath) currentExecutor.getPath();
            if (isRouteFromCurrentPlan(currentRoute)) {
                return currentRoute.getGoal();
            }
        }
        return desiredPath.getGoal();
    }

    private OrbitRoutePath buildOrbitRoutePath(int startIndex) {
        if (startIndex < 0 || startIndex >= this.orbitLoop.size() || this.orbitArcs.size() != this.orbitLoop.size()) {
            orbitTrace("buildRoute rejected startIndex=%d nodes=%d arcs=%d", startIndex, this.orbitLoop.size(),
                    this.orbitArcs.size());
            return null;
        }

        CalculationContext context = new CalculationContext(baritone, false);
        List<BetterBlockPos> positions = new ArrayList<>();
        List<IMovement> movements = new ArrayList<>();
        positions.add(this.orbitLoop.get(startIndex));
        String breakReason = "completed";

        int maxArcCount = Math.min(getOrbitSegmentArcCount(), this.orbitArcs.size() - 1);
        for (int offset = 0; offset < maxArcCount; offset++) {
            int arcIndex = (startIndex + offset) % this.orbitArcs.size();
            OrbitArcPlan arc = this.orbitArcs.get(arcIndex);
            if (arc == null || arc.routePoints.length < 2) {
                breakReason = "missing_arc@" + arcIndex;
                break;
            }

            BetterBlockPos currentPos = positions.get(positions.size() - 1);
            if (!currentPos.equals(arc.src) || positions.contains(arc.dest)) {
                breakReason = String.format(Locale.ROOT, "arc_mismatch@%d current=%s src=%s duplicateDest=%s", arcIndex,
                        formatPos(currentPos), formatPos(arc.src), positions.contains(arc.dest));
                break;
            }

            MovementRouteTraverse movement = new MovementRouteTraverse(baritone, arc.src, arc.dest, false,
                    arc.routePoints);
            double cost = movement.calculateCost(context);
            if (cost >= ActionCosts.COST_INF) {
                breakReason = "cost_inf@" + arcIndex;
                break;
            }
            movement.override(cost);
            movement.checkLoadedChunk(context);
            movements.add(movement);
            positions.add(arc.dest);
        }

        if (movements.isEmpty()) {
            orbitTrace("buildRoute failed startIndex=%d maxArcCount=%d breakReason=%s", startIndex, maxArcCount,
                    breakReason);
            return null;
        }

        BetterBlockPos dest = positions.get(positions.size() - 1);
        int endNodeIndex = (startIndex + movements.size()) % this.orbitLoop.size();
        String routeKey = this.planRevision + ":" + startIndex + ":" + endNodeIndex + ":" + movements.size();
        Goal goal = new GoalBlock(dest);
        orbitTrace("buildRoute startIndex=%d endNodeIndex=%d positions=%d movements=%d dest=%s routeKey=%s breakReason=%s",
                startIndex, endNodeIndex, positions.size(), movements.size(), formatPos(dest), routeKey, breakReason);
        return new OrbitRoutePath(goal, positions, movements, 0, routeKey, startIndex, endNodeIndex);
    }

    private List<BetterBlockPos> extractOrbitPositions(List<OrbitNodePlan> nodePlans) {
        if (nodePlans == null || nodePlans.isEmpty()) {
            return Collections.emptyList();
        }
        List<BetterBlockPos> positions = new ArrayList<>(nodePlans.size());
        for (OrbitNodePlan nodePlan : nodePlans) {
            positions.add(nodePlan.position);
        }
        return positions;
    }

    private List<OrbitArcPlan> buildOrbitArcs(List<OrbitNodePlan> nodePlans) {
        if (nodePlans == null || nodePlans.size() < getRequiredLoopPointCount()) {
            return Collections.emptyList();
        }
        List<OrbitArcPlan> arcs = new ArrayList<>(nodePlans.size());
        for (int i = 0; i < nodePlans.size(); i++) {
            OrbitNodePlan current = nodePlans.get(i);
            OrbitNodePlan next = nodePlans.get((i + 1) % nodePlans.size());
            if (current.position.equals(next.position)) {
                continue;
            }

            List<Vec3d> routePoints = new ArrayList<>();
            appendRoutePoints(routePoints, current.guidePoints);
            appendRoutePoint(routePoints, getFirstGuidePoint(next));
            routePoints = sanitizeArcRoutePoints(current.position, next.position, routePoints);
            if (routePoints.size() < 2) {
                orbitDebug("buildArc skipped index=%d src=%s dest=%s routePoints=%d", i, formatPos(current.position),
                        formatPos(next.position), routePoints.size());
                continue;
            }
            arcs.add(new OrbitArcPlan(current.position, next.position, routePoints.toArray(new Vec3d[0])));
        }
        if (arcs.size() != nodePlans.size()) {
            orbitDebug("buildArcs rejected arcCount=%d nodePlans=%d", arcs.size(), nodePlans.size());
        }
        return arcs.size() == nodePlans.size() ? arcs : Collections.emptyList();
    }

    private List<Vec3d> sanitizeArcRoutePoints(BetterBlockPos src, BetterBlockPos dest, List<Vec3d> rawRoutePoints) {
        if (src == null || dest == null) {
            return Collections.emptyList();
        }

        Vec3d srcCenter = nodeCenter(src);
        Vec3d destCenter = nodeCenter(dest);
        List<Vec3d> sanitized = new ArrayList<>();
        sanitized.add(srcCenter);

        if (rawRoutePoints != null) {
            Vec3d previous = srcCenter;
            for (Vec3d point : rawRoutePoints) {
                if (point == null || !isGuidePointClear(point) || !isRouteSegmentClear(previous, point)) {
                    continue;
                }
                appendRoutePoint(sanitized, point);
                previous = point;
            }
        }

        Vec3d lastPoint = sanitized.get(sanitized.size() - 1);
        if (!isRouteSegmentClear(lastPoint, destCenter)) {
            List<Vec3d> fallback = buildFallbackArcRoute(srcCenter, destCenter);
            return fallback.isEmpty() ? Collections.emptyList() : fallback;
        }

        appendRoutePoint(sanitized, destCenter);
        return sanitized;
    }

    private List<Vec3d> buildFallbackArcRoute(Vec3d srcCenter, Vec3d destCenter) {
        if (srcCenter == null || destCenter == null) {
            return Collections.emptyList();
        }
        if (isRouteSegmentClear(srcCenter, destCenter)) {
            List<Vec3d> direct = new ArrayList<>();
            direct.add(srcCenter);
            direct.add(destCenter);
            return direct;
        }

        double midY = Math.min(srcCenter.y, destCenter.y);
        Vec3d viaX = new Vec3d(destCenter.x, midY, srcCenter.z);
        if (isGuidePointClear(viaX)
                && isRouteSegmentClear(srcCenter, viaX)
                && isRouteSegmentClear(viaX, destCenter)) {
            List<Vec3d> route = new ArrayList<>();
            route.add(srcCenter);
            route.add(viaX);
            route.add(destCenter);
            return route;
        }

        Vec3d viaZ = new Vec3d(srcCenter.x, midY, destCenter.z);
        if (isGuidePointClear(viaZ)
                && isRouteSegmentClear(srcCenter, viaZ)
                && isRouteSegmentClear(viaZ, destCenter)) {
            List<Vec3d> route = new ArrayList<>();
            route.add(srcCenter);
            route.add(viaZ);
            route.add(destCenter);
            return route;
        }
        return Collections.emptyList();
    }

    private Vec3d getFirstGuidePoint(OrbitNodePlan nodePlan) {
        if (nodePlan == null || nodePlan.guidePoints.isEmpty()) {
            return null;
        }
        return nodePlan.guidePoints.get(0);
    }

    private void appendRoutePoint(List<Vec3d> routePoints, Vec3d point) {
        if (routePoints == null || point == null) {
            return;
        }
        if (routePoints.isEmpty() || routePoints.get(routePoints.size() - 1).squareDistanceTo(point) > 1.0E-4D) {
            routePoints.add(point);
        }
    }

    private void appendRoutePoints(List<Vec3d> routePoints, List<Vec3d> points) {
        if (points == null) {
            return;
        }
        for (Vec3d point : points) {
            appendRoutePoint(routePoints, point);
        }
    }

    private int chooseNearestLoopNodeIndex(BetterBlockPos playerFeet) {
        if (playerFeet == null || this.orbitLoop.isEmpty()) {
            return -1;
        }
        int nearestIndex = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < this.orbitLoop.size(); i++) {
            BetterBlockPos node = this.orbitLoop.get(i);
            double distSq = playerFeet.distanceSq(node);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearestIndex = i;
            }
        }
        if (nearestIndex >= 0) {
            orbitTrace("nearestLoopNode playerFeet=%s index=%d dist=%.3f node=%s", formatPos(playerFeet), nearestIndex,
                    Math.sqrt(bestDistSq), formatPos(this.orbitLoop.get(nearestIndex)));
        }
        return nearestIndex;
    }

    private BetterBlockPos chooseNearestLoopNode(BetterBlockPos playerFeet) {
        int index = chooseNearestLoopNodeIndex(playerFeet);
        if (index < 0 || index >= this.orbitLoop.size()) {
            return null;
        }
        return this.orbitLoop.get(index);
    }

    private int getOrbitSegmentArcCount() {
        // Keep orbit routing single-step so the active goal always stays on the
        // immediate next loop node instead of jumping far ahead around the ring.
        return this.orbitArcs.size() > 1 ? 1 : 0;
    }

    private void clearPublishedLoop() {
        this.currentGoal = null;
        this.orbitLoop = Collections.emptyList();
        this.orbitArcs = Collections.emptyList();
        this.renderLoop = Collections.emptyList();
        this.lastPlanTick = Integer.MIN_VALUE;
    }

    private void clearRuntime() {
        orbitDebug("clearRuntime state=%s targetEntityId=%d nodes=%d arcs=%d render=%d", this.state, this.targetEntityId,
                this.orbitLoop.size(), this.orbitArcs.size(), this.renderLoop.size());
        this.state = State.IDLE;
        this.targetEntityId = Integer.MIN_VALUE;
        this.pendingRebuild = null;
        this.retainedRebuildCache = null;
        this.retainedRebuildCacheKey = null;
        this.rebuildCache = null;
        clearPublishedLoop();
        this.lastPlanTick = Integer.MIN_VALUE;
        this.lastRequestTick = Integer.MIN_VALUE;
        this.lastRebuildTick = Integer.MIN_VALUE;
        clearFailedRebuild();
    }

    private double horizontalDistSq(double leftX, double leftZ, double rightX, double rightZ) {
        double dx = leftX - rightX;
        double dz = leftZ - rightZ;
        return dx * dx + dz * dz;
    }

    private double wrapRadians(double radians) {
        double wrapped = radians;
        while (wrapped <= -Math.PI) {
            wrapped += Math.PI * 2.0D;
        }
        while (wrapped > Math.PI) {
            wrapped -= Math.PI * 2.0D;
        }
        return wrapped;
    }

    private double getPlayerDistanceToLoop() {
        if (ctx.player() == null || this.renderLoop.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3d playerPos = new Vec3d(ctx.player().posX, 0.0D, ctx.player().posZ);
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < this.renderLoop.size() - 1; i++) {
            Vec3d start = horizontalOnly(this.renderLoop.get(i));
            Vec3d end = horizontalOnly(this.renderLoop.get(i + 1));
            Vec3d nearest = nearestPointOnSegment(playerPos, start, end);
            bestDistanceSq = Math.min(bestDistanceSq, playerPos.squareDistanceTo(nearest));
        }
        return bestDistanceSq == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : Math.sqrt(bestDistanceSq);
    }

    private Vec3d nearestPointOnSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();
        if (lengthSq <= 1.0E-6D) {
            return start;
        }
        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return start.add(segment.scale(t));
    }

    private Vec3d horizontalOnly(Vec3d vec) {
        return new Vec3d(vec.x, 0.0D, vec.z);
    }

    private void logOrbitNodePlans(EntityLivingBase target, List<OrbitNodePlan> nodePlans) {
        if (!isOrbitTraceEnabled() || target == null || nodePlans == null || nodePlans.isEmpty()) {
            return;
        }
        for (int i = 0; i < nodePlans.size(); i++) {
            OrbitNodePlan nodePlan = nodePlans.get(i);
            RouteStats guideStats = analyzeRoute(nodePlan.guidePoints);
            Vec3d firstGuide = nodePlan.guidePoints.isEmpty() ? null : nodePlan.guidePoints.get(0);
            Vec3d lastGuide = nodePlan.guidePoints.isEmpty() ? null : nodePlan.guidePoints.get(nodePlan.guidePoints.size() - 1);
            orbitTrace(
                    "nodePlan idx=%d node=%s nodeAngleDeg=%.2f guideCount=%d guideLength=%.3f maxGuideStep=%.3f maxGuideStepIndex=%d first=%s last=%s",
                    i, formatPos(nodePlan.position), angleDegFromTarget(target, nodeCenter(nodePlan.position)),
                    nodePlan.guidePoints.size(), guideStats.length, guideStats.maxStep, guideStats.maxStepIndex,
                    formatVec2(firstGuide), formatVec2(lastGuide));
        }
    }

    private void logOrbitArcs(EntityLivingBase target, List<OrbitArcPlan> arcs) {
        if (!isOrbitTraceEnabled() || target == null || arcs == null || arcs.isEmpty()) {
            return;
        }
        for (int i = 0; i < arcs.size(); i++) {
            OrbitArcPlan arc = arcs.get(i);
            RouteStats stats = analyzeRoute(arc.routePoints);
            orbitTrace(
                    "arcPlan idx=%d src=%s srcAngleDeg=%.2f dest=%s destAngleDeg=%.2f routePoints=%d length=%.3f maxStep=%.3f maxStepIndex=%d suspicious=%s start=%s end=%s",
                    i, formatPos(arc.src), angleDegFromTarget(target, nodeCenter(arc.src)), formatPos(arc.dest),
                    angleDegFromTarget(target, nodeCenter(arc.dest)), arc.routePoints.length, stats.length, stats.maxStep,
                    stats.maxStepIndex, stats.maxStep > SUSPICIOUS_ROUTE_STEP, formatVec2(stats.maxStepStart),
                    formatVec2(stats.maxStepEnd));
        }
    }

    private RouteStats analyzeRoute(List<Vec3d> points) {
        if (points == null || points.isEmpty()) {
            return RouteStats.EMPTY;
        }
        return analyzeRoute(points.toArray(new Vec3d[0]));
    }

    private RouteStats analyzeRoute(Vec3d[] points) {
        if (points == null || points.length < 2) {
            return RouteStats.EMPTY;
        }
        double length = 0.0D;
        double maxStep = 0.0D;
        int maxStepIndex = -1;
        Vec3d maxStepStart = null;
        Vec3d maxStepEnd = null;
        for (int i = 0; i < points.length - 1; i++) {
            double step = points[i].distanceTo(points[i + 1]);
            length += step;
            if (step > maxStep) {
                maxStep = step;
                maxStepIndex = i;
                maxStepStart = points[i];
                maxStepEnd = points[i + 1];
            }
        }
        return new RouteStats(length, maxStep, maxStepIndex, maxStepStart, maxStepEnd);
    }

    private boolean isOrbitDebugEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_ORBIT);
    }

    private boolean isOrbitTraceEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_ORBIT_TRACE);
    }

    private void orbitDebug(String format, Object... args) {
        if (!isOrbitDebugEnabled()) {
            return;
        }
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT, String.format(Locale.ROOT, format, args));
    }

    private void orbitTrace(String format, Object... args) {
        if (!isOrbitTraceEnabled()) {
            return;
        }
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT_TRACE, String.format(Locale.ROOT, format, args));
    }

    private Vec3d nodeCenter(BetterBlockPos pos) {
        if (pos == null) {
            return Vec3d.ZERO;
        }
        return new Vec3d(pos.x + 0.5D, pos.y + GUIDE_ROUTE_Y_OFFSET, pos.z + 0.5D);
    }

    private double angleDegFromTarget(EntityLivingBase target, Vec3d point) {
        if (target == null || point == null) {
            return 0.0D;
        }
        return Math.toDegrees(Math.atan2(point.z - target.posZ, point.x - target.posX));
    }

    private String formatEntityXZ(EntityLivingBase entity) {
        if (entity == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.3f, %.3f)", entity.posX, entity.posZ);
    }

    private String formatPos(BetterBlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%d,%d,%d)", pos.x, pos.y, pos.z);
    }

    private String formatVec2(Vec3d vec) {
        if (vec == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.3f, %.3f)", vec.x, vec.z);
    }

    private static final class RebuildCache {
        private final AxisAlignedBB scanBounds;
        private final CollisionCacheKey cacheKey;
        private final List<AxisAlignedBB> blockingBoxes = new ArrayList<>();
        private final Map<Long, List<AxisAlignedBB>> blockingBuckets = new HashMap<>();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final List<AxisAlignedBB> collisionScratch = new ArrayList<>(4);
        private final List<AxisAlignedBB> candidateScratch = new ArrayList<>();
        private final IdentityHashMap<AxisAlignedBB, Boolean> candidateSeen = new IdentityHashMap<>();
        private final Map<Long, Boolean> baseStandableCache = new HashMap<>();
        private final Map<LineOfSightKey, Boolean> lineOfSightCache = new HashMap<>();
        private final Map<BoxKey, Boolean> boxClearCache = new HashMap<>();
        private final Map<SegmentKey, Boolean> segmentClearCache = new HashMap<>();
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private int cursorX;
        private int cursorY;
        private int cursorZ;
        private boolean collisionCollectionComplete;
        private boolean reusedFromSession;
        private long collisionCollectNanos;
        private int baseStandableChecks;
        private int baseStandableHits;
        private int lineOfSightChecks;
        private int lineOfSightHits;
        private int boxClearChecks;
        private int boxClearHits;
        private int segmentChecks;
        private int segmentHits;

        private RebuildCache(AxisAlignedBB scanBounds, CollisionCacheKey cacheKey) {
            this.scanBounds = scanBounds;
            this.cacheKey = cacheKey;
            if (scanBounds == null) {
                this.minX = 0;
                this.maxX = 0;
                this.minY = 0;
                this.maxY = 0;
                this.minZ = 0;
                this.maxZ = 0;
                this.cursorX = 0;
                this.cursorY = 0;
                this.cursorZ = 0;
                this.collisionCollectionComplete = true;
            } else {
                this.minX = MathHelper.floor(scanBounds.minX) - 1;
                this.maxX = MathHelper.floor(scanBounds.maxX) + 1;
                this.minY = MathHelper.floor(scanBounds.minY) - 1;
                this.maxY = MathHelper.floor(scanBounds.maxY) + 1;
                this.minZ = MathHelper.floor(scanBounds.minZ) - 1;
                this.maxZ = MathHelper.floor(scanBounds.maxZ) + 1;
                this.cursorX = this.minX;
                this.cursorY = this.minY;
                this.cursorZ = this.minZ;
                this.collisionCollectionComplete = false;
            }
            resetPlanningSession(false);
        }

        private void resetPlanningSession(boolean reusedFromSession) {
            this.reusedFromSession = reusedFromSession;
            this.collisionCollectNanos = 0L;
            this.baseStandableChecks = 0;
            this.baseStandableHits = 0;
            this.lineOfSightChecks = 0;
            this.lineOfSightHits = 0;
            this.boxClearChecks = 0;
            this.boxClearHits = 0;
            this.segmentChecks = 0;
            this.segmentHits = 0;
            this.candidateScratch.clear();
            this.candidateSeen.clear();
        }

        private boolean isCollisionCollectionComplete() {
            return this.collisionCollectionComplete;
        }

        private void advanceCollisionCursor() {
            if (this.collisionCollectionComplete) {
                return;
            }
            if (this.cursorZ < this.maxZ) {
                this.cursorZ++;
                return;
            }
            this.cursorZ = this.minZ;
            if (this.cursorY < this.maxY) {
                this.cursorY++;
                return;
            }
            this.cursorY = this.minY;
            if (this.cursorX < this.maxX) {
                this.cursorX++;
                return;
            }
            this.collisionCollectionComplete = true;
        }

        private void addBlockingBox(AxisAlignedBB box) {
            if (box == null) {
                return;
            }
            this.blockingBoxes.add(box);
            int bucketMinX = MathHelper.floor(box.minX - 1.0E-4D);
            int bucketMaxX = MathHelper.floor(box.maxX + 1.0E-4D);
            int bucketMinY = MathHelper.floor(box.minY - 1.0E-4D);
            int bucketMaxY = MathHelper.floor(box.maxY + 1.0E-4D);
            int bucketMinZ = MathHelper.floor(box.minZ - 1.0E-4D);
            int bucketMaxZ = MathHelper.floor(box.maxZ + 1.0E-4D);
            for (int x = bucketMinX; x <= bucketMaxX; x++) {
                for (int y = bucketMinY; y <= bucketMaxY; y++) {
                    for (int z = bucketMinZ; z <= bucketMaxZ; z++) {
                        this.blockingBuckets.computeIfAbsent(bucketKey(x, y, z), ignored -> new ArrayList<>()).add(box);
                    }
                }
            }
        }

        private boolean canQueryCollisionIndex(AxisAlignedBB queryBox) {
            return this.collisionCollectionComplete
                    && this.scanBounds != null
                    && queryBox != null
                    && this.scanBounds.minX <= queryBox.minX && this.scanBounds.maxX >= queryBox.maxX
                    && this.scanBounds.minY <= queryBox.minY && this.scanBounds.maxY >= queryBox.maxY
                    && this.scanBounds.minZ <= queryBox.minZ && this.scanBounds.maxZ >= queryBox.maxZ;
        }

        private List<AxisAlignedBB> collectCandidateBoxes(AxisAlignedBB queryBox) {
            this.candidateScratch.clear();
            this.candidateSeen.clear();
            if (queryBox == null) {
                return this.candidateScratch;
            }
            int bucketMinX = MathHelper.floor(queryBox.minX - 1.0E-4D);
            int bucketMaxX = MathHelper.floor(queryBox.maxX + 1.0E-4D);
            int bucketMinY = MathHelper.floor(queryBox.minY - 1.0E-4D);
            int bucketMaxY = MathHelper.floor(queryBox.maxY + 1.0E-4D);
            int bucketMinZ = MathHelper.floor(queryBox.minZ - 1.0E-4D);
            int bucketMaxZ = MathHelper.floor(queryBox.maxZ + 1.0E-4D);
            for (int x = bucketMinX; x <= bucketMaxX; x++) {
                for (int y = bucketMinY; y <= bucketMaxY; y++) {
                    for (int z = bucketMinZ; z <= bucketMaxZ; z++) {
                        List<AxisAlignedBB> bucket = this.blockingBuckets.get(bucketKey(x, y, z));
                        if (bucket == null || bucket.isEmpty()) {
                            continue;
                        }
                        for (AxisAlignedBB candidate : bucket) {
                            if (this.candidateSeen.put(candidate, Boolean.TRUE) == null) {
                                this.candidateScratch.add(candidate);
                            }
                        }
                    }
                }
            }
            return this.candidateScratch;
        }

        private static long bucketKey(int x, int y, int z) {
            long hash = 1469598103934665603L;
            hash = (hash ^ x) * 1099511628211L;
            hash = (hash ^ y) * 1099511628211L;
            hash = (hash ^ z) * 1099511628211L;
            return hash;
        }
    }

    private static final class PendingRebuild {
        private final int targetEntityId;
        private final double targetX;
        private final double targetZ;
        private final double radius;
        private final int playerFeetY;
        private final String reason;
        private final boolean hadUsableLoop;
        private final int requestTick;
        private final int sampleCount;
        private final double startAngle;
        private final double step;
        private final double routeY;
        private final RebuildCache cache;
        private final long startNanos = System.nanoTime();
        private final List<Vec3d> guideLoop = new ArrayList<>();
        private final List<OrbitNodePlan> nodePlans = new ArrayList<>();
        private final List<Vec3d> deferredLeadingGuidePoints = new ArrayList<>();
        private final List<OrbitArcPlan> arcs = new ArrayList<>();
        private List<Vec3d> renderLoop = Collections.emptyList();
        private RebuildPhase phase;
        private Vec3d previousGuidePoint;
        private int guideIndex;
        private int clippedCount;
        private int obstacleAdjustedCount;
        private double minRadius = Double.POSITIVE_INFINITY;
        private double maxRadius;
        private double maxClipLoss;
        private int maxClipLossIndex = -1;
        private BetterBlockPos previousNode;
        private int nodeGuideIndex;
        private int nullGuideCount;
        private int currentNullStreak;
        private int maxNullStreak;
        private int nullRunStart = -1;
        private int largeNodeJumpCount;
        private boolean mergedEndpoints;
        private int arcIndex;

        private PendingRebuild(int targetEntityId, double targetX, double targetZ, double radius, int playerFeetY,
                String reason, boolean hadUsableLoop, int requestTick, int sampleCount, double startAngle,
                RebuildCache cache) {
            this.targetEntityId = targetEntityId;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.radius = radius;
            this.playerFeetY = playerFeetY;
            this.reason = reason == null ? "unknown" : reason;
            this.hadUsableLoop = hadUsableLoop;
            this.requestTick = requestTick;
            this.sampleCount = Math.max(MIN_GUIDE_POINTS, sampleCount);
            this.startAngle = startAngle;
            this.step = (Math.PI * 2.0D) / this.sampleCount;
            this.routeY = playerFeetY + GUIDE_ROUTE_Y_OFFSET;
            this.cache = cache;
            this.phase = cache == null || cache.isCollisionCollectionComplete()
                    ? RebuildPhase.BUILD_GUIDE
                    : RebuildPhase.COLLECT_COLLISIONS;
        }

        private boolean matches(int entityId, double desiredRadius, int feetY, double targetX, double targetZ) {
            if (this.targetEntityId != entityId) {
                return false;
            }
            if (Math.abs(this.radius - desiredRadius) > MAX_RADIUS_DELTA_BEFORE_REPLAN) {
                return false;
            }
            if (Math.abs(this.playerFeetY - feetY) > MAX_PLAYER_FEET_Y_DELTA_BEFORE_REPLAN) {
                return false;
            }
            double dx = this.targetX - targetX;
            double dz = this.targetZ - targetZ;
            return dx * dx + dz * dz <= MAX_TARGET_MOVE_BEFORE_REPLAN * MAX_TARGET_MOVE_BEFORE_REPLAN;
        }

        private boolean isFinished() {
            return this.phase == RebuildPhase.COMPLETE || this.phase == RebuildPhase.FAILED;
        }

        private long getDurationNanos() {
            return System.nanoTime() - this.startNanos;
        }
    }

    private static final class CollisionCacheKey {
        private final int targetBlockX;
        private final int targetBlockZ;
        private final long radiusBand;
        private final int feetYBand;
        private final long minX;
        private final long minY;
        private final long minZ;
        private final long maxX;
        private final long maxY;
        private final long maxZ;

        private CollisionCacheKey(int targetBlockX, int targetBlockZ, long radiusBand, int feetYBand,
                long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {
            this.targetBlockX = targetBlockX;
            this.targetBlockZ = targetBlockZ;
            this.radiusBand = radiusBand;
            this.feetYBand = feetYBand;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static CollisionCacheKey of(EntityLivingBase target, double radius, int playerFeetY, AxisAlignedBB scanBounds) {
            return new CollisionCacheKey(MathHelper.floor(target.posX), MathHelper.floor(target.posZ),
                    quantize(radius, CACHE_SCAN_BOUNDS_QUANTIZATION), playerFeetY,
                    quantize(scanBounds.minX, CACHE_SCAN_BOUNDS_QUANTIZATION),
                    quantize(scanBounds.minY, CACHE_SCAN_BOUNDS_QUANTIZATION),
                    quantize(scanBounds.minZ, CACHE_SCAN_BOUNDS_QUANTIZATION),
                    quantize(scanBounds.maxX, CACHE_SCAN_BOUNDS_QUANTIZATION),
                    quantize(scanBounds.maxY, CACHE_SCAN_BOUNDS_QUANTIZATION),
                    quantize(scanBounds.maxZ, CACHE_SCAN_BOUNDS_QUANTIZATION));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CollisionCacheKey)) {
                return false;
            }
            CollisionCacheKey other = (CollisionCacheKey) obj;
            return this.targetBlockX == other.targetBlockX
                    && this.targetBlockZ == other.targetBlockZ
                    && this.radiusBand == other.radiusBand
                    && this.feetYBand == other.feetYBand
                    && this.minX == other.minX
                    && this.minY == other.minY
                    && this.minZ == other.minZ
                    && this.maxX == other.maxX
                    && this.maxY == other.maxY
                    && this.maxZ == other.maxZ;
        }

        @Override
        public int hashCode() {
            long hash = this.targetBlockX;
            hash = 31L * hash + this.targetBlockZ;
            hash = 31L * hash + this.radiusBand;
            hash = 31L * hash + this.feetYBand;
            hash = 31L * hash + this.minX;
            hash = 31L * hash + this.minY;
            hash = 31L * hash + this.minZ;
            hash = 31L * hash + this.maxX;
            hash = 31L * hash + this.maxY;
            hash = 31L * hash + this.maxZ;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    private static final class BoxKey {
        private final long x;
        private final long y;
        private final long z;
        private final long halfWidth;
        private final long margin;

        private BoxKey(double centerX, double feetY, double centerZ, double halfWidth, double margin) {
            this.x = quantize(centerX, CACHE_POSITION_QUANTIZATION);
            this.y = quantize(feetY, CACHE_POSITION_QUANTIZATION);
            this.z = quantize(centerZ, CACHE_POSITION_QUANTIZATION);
            this.halfWidth = quantize(halfWidth, CACHE_POSITION_QUANTIZATION);
            this.margin = quantize(margin, CACHE_POSITION_QUANTIZATION);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BoxKey)) {
                return false;
            }
            BoxKey other = (BoxKey) obj;
            return this.x == other.x
                    && this.y == other.y
                    && this.z == other.z
                    && this.halfWidth == other.halfWidth
                    && this.margin == other.margin;
        }

        @Override
        public int hashCode() {
            long hash = this.x;
            hash = 31L * hash + this.y;
            hash = 31L * hash + this.z;
            hash = 31L * hash + this.halfWidth;
            hash = 31L * hash + this.margin;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    private static final class SegmentKey {
        private final long ax;
        private final long ay;
        private final long az;
        private final long bx;
        private final long by;
        private final long bz;

        private SegmentKey(Vec3d first, Vec3d second) {
            this.ax = quantize(first.x, CACHE_POSITION_QUANTIZATION);
            this.ay = quantize(first.y, CACHE_POSITION_QUANTIZATION);
            this.az = quantize(first.z, CACHE_POSITION_QUANTIZATION);
            this.bx = quantize(second.x, CACHE_POSITION_QUANTIZATION);
            this.by = quantize(second.y, CACHE_POSITION_QUANTIZATION);
            this.bz = quantize(second.z, CACHE_POSITION_QUANTIZATION);
        }

        private static SegmentKey of(Vec3d start, Vec3d end) {
            return compare(start, end) <= 0 ? new SegmentKey(start, end) : new SegmentKey(end, start);
        }

        private static int compare(Vec3d left, Vec3d right) {
            int cmp = Long.compare(quantize(left.x, CACHE_POSITION_QUANTIZATION),
                    quantize(right.x, CACHE_POSITION_QUANTIZATION));
            if (cmp != 0) {
                return cmp;
            }
            cmp = Long.compare(quantize(left.y, CACHE_POSITION_QUANTIZATION),
                    quantize(right.y, CACHE_POSITION_QUANTIZATION));
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(quantize(left.z, CACHE_POSITION_QUANTIZATION),
                    quantize(right.z, CACHE_POSITION_QUANTIZATION));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SegmentKey)) {
                return false;
            }
            SegmentKey other = (SegmentKey) obj;
            return this.ax == other.ax
                    && this.ay == other.ay
                    && this.az == other.az
                    && this.bx == other.bx
                    && this.by == other.by
                    && this.bz == other.bz;
        }

        @Override
        public int hashCode() {
            long hash = this.ax;
            hash = 31L * hash + this.ay;
            hash = 31L * hash + this.az;
            hash = 31L * hash + this.bx;
            hash = 31L * hash + this.by;
            hash = 31L * hash + this.bz;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    private static final class LineOfSightKey {
        private final long standPos;
        private final int targetEntityId;
        private final long targetX;
        private final long targetY;
        private final long targetZ;

        private LineOfSightKey(long standPos, int targetEntityId, long targetX, long targetY, long targetZ) {
            this.standPos = standPos;
            this.targetEntityId = targetEntityId;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }

        private static LineOfSightKey of(BetterBlockPos standPos, EntityLivingBase target) {
            return new LineOfSightKey(BetterBlockPos.longHash(standPos), target == null ? Integer.MIN_VALUE : target.getEntityId(),
                    quantize(target == null ? 0.0D : target.posX, CACHE_POSITION_QUANTIZATION),
                    quantize(target == null ? 0.0D : target.posY, CACHE_POSITION_QUANTIZATION),
                    quantize(target == null ? 0.0D : target.posZ, CACHE_POSITION_QUANTIZATION));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LineOfSightKey)) {
                return false;
            }
            LineOfSightKey other = (LineOfSightKey) obj;
            return this.standPos == other.standPos
                    && this.targetEntityId == other.targetEntityId
                    && this.targetX == other.targetX
                    && this.targetY == other.targetY
                    && this.targetZ == other.targetZ;
        }

        @Override
        public int hashCode() {
            long hash = this.standPos;
            hash = 31L * hash + this.targetEntityId;
            hash = 31L * hash + this.targetX;
            hash = 31L * hash + this.targetY;
            hash = 31L * hash + this.targetZ;
            return (int) (hash ^ (hash >>> 32));
        }
    }

    private static long quantize(double value, double step) {
        return Math.round(value / step);
    }

    private enum State {
        IDLE,
        ACTIVE,
        STOPPING
    }

    private enum RebuildPhase {
        COLLECT_COLLISIONS,
        BUILD_GUIDE,
        BUILD_NODES,
        BUILD_ARCS,
        FINALIZE,
        COMPLETE,
        FAILED
    }

    private static final class GuidePointPlan {
        private final Vec3d point;
        private final boolean obstacleAdjusted;

        private GuidePointPlan(Vec3d point, boolean obstacleAdjusted) {
            this.point = point;
            this.obstacleAdjusted = obstacleAdjusted;
        }
    }

    private static final class OrbitNodePlan {
        private final BetterBlockPos position;
        private final List<Vec3d> guidePoints = new ArrayList<>();

        private OrbitNodePlan(BetterBlockPos position) {
            this.position = position;
        }

        private void appendGuidePoint(Vec3d point) {
            if (point != null) {
                this.guidePoints.add(point);
            }
        }

        private void appendGuidePoints(List<Vec3d> points) {
            if (points != null && !points.isEmpty()) {
                this.guidePoints.addAll(points);
            }
        }
    }

    private static final class OrbitArcPlan {
        private final BetterBlockPos src;
        private final BetterBlockPos dest;
        private final Vec3d[] routePoints;

        private OrbitArcPlan(BetterBlockPos src, BetterBlockPos dest, Vec3d[] routePoints) {
            this.src = src;
            this.dest = dest;
            this.routePoints = routePoints == null ? new Vec3d[0] : routePoints;
        }
    }

    private static final class RouteStats {
        private static final RouteStats EMPTY = new RouteStats(0.0D, 0.0D, -1, null, null);

        private final double length;
        private final double maxStep;
        private final int maxStepIndex;
        private final Vec3d maxStepStart;
        private final Vec3d maxStepEnd;

        private RouteStats(double length, double maxStep, int maxStepIndex, Vec3d maxStepStart, Vec3d maxStepEnd) {
            this.length = length;
            this.maxStep = maxStep;
            this.maxStepIndex = maxStepIndex;
            this.maxStepStart = maxStepStart;
            this.maxStepEnd = maxStepEnd;
        }
    }
}
