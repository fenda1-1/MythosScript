package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteFollowHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.OrbitRoutePath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.PathExecutor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MovementRouteTraverse extends Movement implements IRoutePointMovement {

    private static final double EXTRA_COST = 0.25D;
    private static final double ROUTE_LOOKAHEAD_DISTANCE = 0.20D;
    private static final double ROUTE_MAX_LOOKAHEAD_DISTANCE = 0.62D;
    private static final double ROUTE_BASE_VALID_DISTANCE = 1.10D;
    private static final double ROUTE_VALID_DISTANCE_SPEED_FACTOR = 0.32D;
    private static final double ROUTE_MAX_VALID_DISTANCE = 1.75D;

    private final Vec3d[] routePoints;
    private final boolean requireNarrowCollision;
    private final double routeLength;
    private boolean loggedOrbitGeometry;
    private int lastOrbitDebugTick = Integer.MIN_VALUE;

    public MovementRouteTraverse(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest,
            boolean requireNarrowCollision, Vec3d... routePoints) {
        super(baritone, src, dest, new BetterBlockPos[0]);
        this.routePoints = compact(routePoints);
        this.requireNarrowCollision = requireNarrowCollision;
        this.routeLength = calculateRouteLength(this.routePoints);
    }

    @Override
    public double calculateCost(CalculationContext context) {
        if (!MovementHelper.canTraverseSnowTransition(context, src.x, src.y, src.z, dest.x, dest.y, dest.z)) {
            return COST_INF;
        }
        if (!MovementHelper.canTraverseSnowRoute(context, src.y, routePoints)) {
            return COST_INF;
        }
        if (!MovementHelper.canWalkOn(context, src.x, src.y - 1, src.z)
                || !MovementHelper.canWalkOn(context, dest.x, dest.y - 1, dest.z)) {
            return COST_INF;
        }
        if (!MovementHelper.canWalkThrough(context, dest.x, dest.y, dest.z)
                || !MovementHelper.canWalkThrough(context, dest.x, dest.y + 1, dest.z)) {
            return COST_INF;
        }
        double minY = Math.min(src.y, dest.y);
        double maxY = Math.max(src.y, dest.y) + 1.799D;
        if (!RouteCollisionSampler.isRouteClear(context, routePoints, minY, maxY, requireNarrowCollision)) {
            return COST_INF;
        }
        return WALK_ONE_BLOCK_COST * routeLength + EXTRA_COST;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions = ImmutableSet.builder();
        positions.add(src);
        positions.add(dest);
        for (Vec3d point : routePoints) {
            positions.add(new BetterBlockPos(MathHelper.floor(point.x), src.y, MathHelper.floor(point.z)));
        }
        return positions.build();
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        OrbitDebugContext debugContext = getOrbitDebugContext();
        logOrbitGeometry(debugContext);
        if (ctx.playerFeet().equals(dest)) {
            logOrbitTick(debugContext, "success", null, 0.0D);
            return state.setStatus(MovementStatus.SUCCESS);
        }
        double routeDistanceSq = currentRouteDistanceSq();
        if (routeDistanceSq > getAllowedRouteDistanceSq()) {
            logOrbitTick(debugContext, "unreachable", null, routeDistanceSq);
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        RouteFollowHelper.FollowCommand followCommand = getFollowCommand();
        logOrbitTick(debugContext, "tick", followCommand, routeDistanceSq);
        MovementHelper.moveForwardWithRotation(ctx, state, followCommand.getDesiredDirection());
        state.setInput(Input.SPRINT, true);
        return state;
    }

    @Override
    protected boolean safeToCancel(MovementState state) {
        return ctx.playerFeet().equals(src) || ctx.playerFeet().equals(dest);
    }

    @Override
    public Vec3d[] getRoutePoints() {
        return routePoints.clone();
    }

    private double currentRouteDistanceSq() {
        if (getValidPositions().contains(ctx.playerFeet())
                || getValidPositions().contains(((com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior) baritone
                        .getPathingBehavior()).pathStart())) {
            return 0.0D;
        }
        Vec3d playerPos = new Vec3d(ctx.player().posX, src.y + 0.5D, ctx.player().posZ);
        return RouteFollowHelper.distanceSqToRoute(routePoints, playerPos);
    }

    private RouteFollowHelper.FollowCommand getFollowCommand() {
        Vec3d playerPos = new Vec3d(ctx.player().posX, src.y + 0.5D, ctx.player().posZ);
        Vec3d playerVelocity = new Vec3d(ctx.player().motionX, 0.0D, ctx.player().motionZ);
        // Keep orbit traversal glued to the sampled curve instead of pure-pursuit
        // shortcutting across it as speed rises, while still looking slightly ahead
        // at higher speed to avoid stop-go correction on every tiny segment.
        return RouteFollowHelper.getFollowCommand(routePoints, playerPos, playerVelocity, ROUTE_LOOKAHEAD_DISTANCE,
                ROUTE_MAX_LOOKAHEAD_DISTANCE, 1.28D, 0.92D);
    }

    private double getAllowedRouteDistanceSq() {
        double horizontalSpeed = Math.sqrt(ctx.player().motionX * ctx.player().motionX + ctx.player().motionZ * ctx.player().motionZ);
        double allowedDistance = ROUTE_BASE_VALID_DISTANCE + Math.min(ROUTE_MAX_VALID_DISTANCE - ROUTE_BASE_VALID_DISTANCE,
                horizontalSpeed * ROUTE_VALID_DISTANCE_SPEED_FACTOR);
        return allowedDistance * allowedDistance;
    }

    private OrbitDebugContext getOrbitDebugContext() {
        if (!ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_ORBIT_TRACE)) {
            return null;
        }
        PathingBehavior pathingBehavior = (PathingBehavior) baritone.getPathingBehavior();
        PathExecutor currentExecutor = pathingBehavior.getCurrent();
        if (currentExecutor == null || !(currentExecutor.getPath() instanceof OrbitRoutePath)) {
            return null;
        }
        OrbitRoutePath orbitPath = (OrbitRoutePath) currentExecutor.getPath();
        if (currentExecutor.getPosition() < 0 || currentExecutor.getPosition() >= orbitPath.movements().size()
                || orbitPath.movements().get(currentExecutor.getPosition()) != this) {
            return null;
        }
        return new OrbitDebugContext(currentExecutor, orbitPath);
    }

    private void logOrbitGeometry(OrbitDebugContext debugContext) {
        if (debugContext == null || this.loggedOrbitGeometry) {
            return;
        }
        RouteGeometryStats stats = analyzeRoute(routePoints);
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT_TRACE,
                String.format(Locale.ROOT,
                        "routeTraverse geometry routeKey=%s pathPos=%d src=%s dest=%s points=%d length=%.3f maxStep=%.3f maxStepIndex=%d stepStart=%s stepEnd=%s",
                        debugContext.path.getRouteKey(), debugContext.executor.getPosition(), formatPos(src),
                        formatPos(dest), routePoints.length, stats.length, stats.maxStep, stats.maxStepIndex,
                        formatVec2(stats.maxStepStart), formatVec2(stats.maxStepEnd)));
        this.loggedOrbitGeometry = true;
    }

    private void logOrbitTick(OrbitDebugContext debugContext, String event,
            RouteFollowHelper.FollowCommand followCommand, double routeDistanceSq) {
        if (debugContext == null || ctx.player() == null) {
            return;
        }
        int tick = ctx.player().ticksExisted;
        boolean shouldLog = !"tick".equals(event) || this.lastOrbitDebugTick == Integer.MIN_VALUE
                || tick - this.lastOrbitDebugTick >= 4 || routeDistanceSq > 0.16D;
        if (!shouldLog) {
            return;
        }
        this.lastOrbitDebugTick = tick;
        String preview = followCommand == null ? "null" : formatVec2(followCommand.getPreviewPoint());
        String desired = followCommand == null ? "null" : formatVec2(followCommand.getDesiredDirection());
        double lookAhead = followCommand == null ? 0.0D : followCommand.getLookAheadDistance();
        double lateralError = followCommand == null ? 0.0D : followCommand.getLateralError();
        double lateralVelocity = followCommand == null ? 0.0D : followCommand.getLateralVelocity();
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT_TRACE,
                String.format(Locale.ROOT,
                        "routeTraverse %s routeKey=%s pathPos=%d src=%s dest=%s feet=%s player=%s routeDist=%.3f preview=%s desired=%s lookAhead=%.3f lateralError=%.3f lateralVelocity=%.3f",
                        event, debugContext.path.getRouteKey(), debugContext.executor.getPosition(), formatPos(src),
                        formatPos(dest), formatPos(ctx.playerFeet()), formatPlayerXZ(), Math.sqrt(routeDistanceSq), preview,
                        desired, lookAhead, lateralError, lateralVelocity));
    }

    private static double calculateRouteLength(Vec3d[] points) {
        double length = 0.0D;
        for (int i = 0; i < points.length - 1; i++) {
            length += points[i].distanceTo(points[i + 1]);
        }
        return length;
    }

    private static RouteGeometryStats analyzeRoute(Vec3d[] points) {
        if (points == null || points.length < 2) {
            return RouteGeometryStats.EMPTY;
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
        return new RouteGeometryStats(length, maxStep, maxStepIndex, maxStepStart, maxStepEnd);
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

    private String formatPlayerXZ() {
        return String.format(Locale.ROOT, "(%.3f, %.3f)", ctx.player().posX, ctx.player().posZ);
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

    private static final class OrbitDebugContext {
        private final PathExecutor executor;
        private final OrbitRoutePath path;

        private OrbitDebugContext(PathExecutor executor, OrbitRoutePath path) {
            this.executor = executor;
            this.path = path;
        }
    }

    private static final class RouteGeometryStats {
        private static final RouteGeometryStats EMPTY = new RouteGeometryStats(0.0D, 0.0D, -1, null, null);

        private final double length;
        private final double maxStep;
        private final int maxStepIndex;
        private final Vec3d maxStepStart;
        private final Vec3d maxStepEnd;

        private RouteGeometryStats(double length, double maxStep, int maxStepIndex, Vec3d maxStepStart,
                Vec3d maxStepEnd) {
            this.length = length;
            this.maxStep = maxStep;
            this.maxStepIndex = maxStepIndex;
            this.maxStepStart = maxStepStart;
            this.maxStepEnd = maxStepEnd;
        }
    }
}
