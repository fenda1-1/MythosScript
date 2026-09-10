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

import com.google.common.collect.ImmutableSet;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.input.Input;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.Movement;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteFollowHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.RouteCollisionSampler;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortal;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortalDetector;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.PortalRoute;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;

public class MovementNarrowGapTraverse extends Movement implements IRoutePointMovement {

    private static final double EXTRA_COST = 0.25D;
    private static final double ROUTE_LOOKAHEAD_DISTANCE = 0.45D;
    private static final double ASCEND_JUMP_TRIGGER_DISTANCE = 0.96D;
    private static final double ASCEND_JUMP_LATERAL_TOLERANCE = 0.24D;
    private static final double DESCEND_DROP_COST = 0.85D;
    private static final double DESCEND_SUCCESS_HORIZONTAL_TOLERANCE = 0.68D;
    private static final double DESCEND_COMMIT_EXTRA_DISTANCE = 0.42D;
    private static final double STEP_COMMIT_STALL_SPEED_SQ = 0.004D;
    private static final double STEP_COMMIT_NEAR_PORTAL_DISTANCE = 1.15D;
    private static final double STEP_COMMIT_DEST_DISTANCE = 1.10D;
    private static final double STEP_COMMIT_POINT_TOLERANCE = 0.19D;
    private static final double STEP_COMMIT_BEHIND_MARGIN = 0.08D;
    private static final double START_ROUTE_RECOVERY_DISTANCE = 1.15D;
    private static final double START_SOURCE_HORIZONTAL_RECOVERY_DISTANCE = 0.92D;

    private final BetterBlockPos barrierA;
    private final BetterBlockPos barrierB;
    private double sourceStandingYReference = Double.NaN;

    public MovementNarrowGapTraverse(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        this(baritone, src, dest, resolveBarrierCells(src, dest));
    }

    public MovementNarrowGapTraverse(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        this(baritone, src, dest, new BetterBlockPos[] { barrierA, barrierB });
    }

    private MovementNarrowGapTraverse(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest,
            BetterBlockPos[] barriers) {
        super(baritone, src, dest, new BetterBlockPos[0]);
        this.barrierA = barriers[0];
        this.barrierB = barriers[1];
    }

    @Override
    public void reset() {
        super.reset();
        sourceStandingYReference = Double.NaN;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.y, dest.z, barrierA, barrierB);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions = ImmutableSet.builder();
        BetterBlockPos portalCell = new BetterBlockPos(
                src.x + Integer.signum(dest.x - src.x),
                src.y,
                src.z + Integer.signum(dest.z - src.z));
        positions.add(src, dest, portalCell, barrierA, barrierB);
        BetterBlockPos recessedFeet = resolveRecessedPortalFeet(portalCell);
        if (recessedFeet != null) {
            positions.add(recessedFeet);
        }
        return positions.build();
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ) {
        if (!isSupportedAutoOffset(destX - x, destZ - z)) {
            return COST_INF;
        }
        BetterBlockPos[] barriers = resolveBarrierCells(x, y, z, destX, destZ);
        return cost(context, x, y, z, destX, destZ, barriers[0], barriers[1]);
    }

    public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ,
            MutableMoveResult result) {
        if (!isSupportedAutoOffset(destX - x, destZ - z)) {
            result.reset();
            return;
        }
        BetterBlockPos[] barriers = resolveBarrierCells(x, y, z, destX, destZ);
        cost(context, x, y, z, destX, destZ, barriers[0], barriers[1], result);
    }

    public static double cost(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, x, y, z, destX, destZ, barrierA, barrierB, result);
        return result.cost;
    }

    public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB, MutableMoveResult result) {
        result.x = destX;
        result.z = destZ;
        result.y = y;
        result.cost = COST_INF;

        double flatCost = cost(context, x, y, z, destX, y, destZ, barrierA, barrierB);
        if (flatCost < result.cost) {
            result.cost = flatCost;
            result.y = y;
        }

        double ascendCost = cost(context, x, y, z, destX, y + 1, destZ, barrierA, barrierB);
        if (ascendCost < result.cost) {
            result.cost = ascendCost;
            result.y = y + 1;
        }

        double descendCost = cost(context, x, y, z, destX, y - 1, destZ, barrierA, barrierB);
        if (descendCost < result.cost) {
            result.cost = descendCost;
            result.y = y - 1;
        }
    }

    private static double cost(CalculationContext context, int x, int y, int z, int destX, int destY, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        if (destY == y) {
            return costFlat(context, x, y, z, destX, destZ, barrierA, barrierB);
        }
        if (destY == y + 1) {
            return costStepUp(context, x, y, z, destX, destZ, barrierA, barrierB);
        }
        if (destY == y - 1) {
            return costStepDown(context, x, y, z, destX, destZ, barrierA, barrierB);
        }
        return COST_INF;
    }

    private static double costFlat(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        if (!MovementHelper.canTraverseSnowTransition(context, x, y, z, destX, y, destZ)) {
            return COST_INF;
        }
        if (!MovementHelper.canWalkOn(context, x, y - 1, z)) {
            return COST_INF;
        }

        IBlockState destInto = context.get(destX, y, destZ);
        IBlockState destHead = context.get(destX, y + 1, destZ);
        IBlockState destOn = context.get(destX, y - 1, destZ);
        if (!MovementHelper.canWalkThrough(context, destX, y, destZ, destInto)
                || !MovementHelper.canWalkThrough(context, destX, y + 1, destZ, destHead)
                || !MovementHelper.canWalkOn(context, destX, y - 1, destZ, destOn)) {
            return COST_INF;
        }

        Block srcDownBlock = context.getBlock(x, y - 1, z);
        Block destDownBlock = destOn.getBlock();
        if (isUnsupportedLandingBlock(srcDownBlock) || isUnsupportedLandingBlock(destDownBlock)) {
            return COST_INF;
        }

        if (barrierA == null || barrierB == null || barrierA.equals(barrierB)) {
            return COST_INF;
        }
        IBlockState barrierStateA = context.get(barrierA.x, barrierA.y, barrierA.z);
        IBlockState barrierStateB = context.get(barrierB.x, barrierB.y, barrierB.z);
        EdgePortal portal = resolvePortal(context, x, y, z, destX, destZ, barrierA, barrierStateA, barrierB, barrierStateB);
        if (portal == null) {
            return COST_INF;
        }
        PortalRoute route = portal.createRoute(getBlockCenter(x, y, z), getBlockCenter(destX, y, destZ));
        if (!MovementHelper.canTraverseSnowRoute(context, y, route.getPoints())) {
            return COST_INF;
        }
        if (!RouteCollisionSampler.isRouteClear(context, route.getPoints(), y, y + 1.799D, true)) {
            return COST_INF;
        }
        return WALK_ONE_BLOCK_COST * route.getLength() + EXTRA_COST;
    }

    private static double costStepUp(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        if (!MovementHelper.canWalkOn(context, x, y - 1, z)) {
            return COST_INF;
        }

        IBlockState landingOn = context.get(destX, y, destZ);
        IBlockState destFeet = context.get(destX, y + 1, destZ);
        IBlockState destHead = context.get(destX, y + 2, destZ);
        if (!MovementHelper.canWalkOn(context, destX, y, destZ, landingOn)
                || !MovementHelper.canWalkThrough(context, destX, y + 1, destZ, destFeet)
                || !MovementHelper.canWalkThrough(context, destX, y + 2, destZ, destHead)) {
            return COST_INF;
        }

        Block srcDownBlock = context.getBlock(x, y - 1, z);
        Block landingBlock = landingOn.getBlock();
        if (isUnsupportedLandingBlock(srcDownBlock) || isUnsupportedLandingBlock(landingBlock)) {
            return COST_INF;
        }

        if (barrierA == null || barrierB == null || barrierA.equals(barrierB)) {
            return COST_INF;
        }
        IBlockState barrierStateA = context.get(barrierA.x, barrierA.y, barrierA.z);
        IBlockState barrierStateB = context.get(barrierB.x, barrierB.y, barrierB.z);
        EdgePortal portal = resolvePortal(context, x, y, z, destX, destZ, barrierA, barrierStateA, barrierB, barrierStateB);
        if (portal == null) {
            return COST_INF;
        }

        PortalRoute route = portal.createRoute(getBlockCenter(x, y, z), getBlockCenter(destX, y, destZ));
        if (!RouteCollisionSampler.isRouteClear(context, routeThroughGapCenter(route), y, y + 1.799D, true)) {
            return COST_INF;
        }

        double totalCost = WALK_ONE_BLOCK_COST * route.getLength() + EXTRA_COST;
        if (requiresJumpForLanding(landingOn)) {
            totalCost += context.jumpPenalty;
        }
        return totalCost;
    }

    private static double costStepDown(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        if (!MovementHelper.canWalkOn(context, x, y - 1, z)) {
            return COST_INF;
        }

        IBlockState landingFeet = context.get(destX, y - 1, destZ);
        IBlockState landingHead = context.get(destX, y, destZ);
        IBlockState landingOn = context.get(destX, y - 2, destZ);
        if (!MovementHelper.canWalkThrough(context, destX, y - 1, destZ, landingFeet)
                || !MovementHelper.canWalkThrough(context, destX, y, destZ, landingHead)
                || !MovementHelper.canWalkOn(context, destX, y - 2, destZ, landingOn)) {
            return COST_INF;
        }

        Block srcDownBlock = context.getBlock(x, y - 1, z);
        Block landingBlock = landingOn.getBlock();
        if (isUnsupportedLandingBlock(srcDownBlock) || isUnsupportedLandingBlock(landingBlock)) {
            return COST_INF;
        }

        if (barrierA == null || barrierB == null || barrierA.equals(barrierB)) {
            return COST_INF;
        }
        IBlockState barrierStateA = context.get(barrierA.x, barrierA.y, barrierA.z);
        IBlockState barrierStateB = context.get(barrierB.x, barrierB.y, barrierB.z);
        EdgePortal portal = resolvePortal(context, x, y, z, destX, destZ, barrierA, barrierStateA, barrierB, barrierStateB);
        if (portal == null) {
            return COST_INF;
        }

        PortalRoute route = portal.createRoute(getBlockCenter(x, y, z), getBlockCenter(destX, y, destZ));
        if (!RouteCollisionSampler.isRouteClear(context, routeThroughGapCenter(route), y, y + 1.799D, true)) {
            return COST_INF;
        }

        return WALK_ONE_BLOCK_COST * route.getLength() + EXTRA_COST + DESCEND_DROP_COST;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        if (ctx.playerFeet().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        }
        if (isLowerLandingReached()) {
            return state.setStatus(MovementStatus.SUCCESS);
        }
        PortalRoute route = resolveWorldRoute();
        if (!isMovementContextRecoverable(route) && !(hasVerticalLandingOffset() && !ctx.player().onGround)) {
            logDebug("Narrow gap movement became unreachable at start/context mismatch. feet=" + ctx.playerFeet()
                    + " src=" + src + " dest=" + dest);
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (isLowerLanding()) {
            updateSourceStandingReference();
            driveDescendingGapTraverse(state, route);
            return state;
        }
        RouteFollowHelper.FollowCommand followCommand = getFollowCommand(route);
        if (shouldUseStepCommitController(route)) {
            driveStepCommitTraverse(state, route, followCommand);
        } else {
            MovementHelper.moveTowards(ctx, state, followCommand.getPreviewPoint());
        }
        if (isElevatedLanding()) {
            maybeJumpOntoLanding(state, route, followCommand);
        }
        return state;
    }

    @Override
    protected boolean safeToCancel(MovementState state) {
        if (state.getStatus() != MovementStatus.RUNNING) {
            return true;
        }
        if (isLowerLanding()) {
            return isLowerLandingReached();
        }
        return ctx.playerFeet().equals(dest);
    }

    private static boolean isSupportedAutoOffset(int dx, int dz) {
        return (Math.abs(dx) == 1 && Math.abs(dz) == 2)
                || (Math.abs(dx) == 2 && Math.abs(dz) == 1);
    }

    private static BetterBlockPos[] resolveBarrierCells(BetterBlockPos src, BetterBlockPos dest) {
        return resolveBarrierCells(src.x, src.y, src.z, dest.x, dest.z);
    }

    private static BetterBlockPos[] resolveBarrierCells(int x, int y, int z, int destX, int destZ) {
        int dx = Integer.signum(destX - x);
        int dz = Integer.signum(destZ - z);
        if (Math.abs(destX - x) == 1 && Math.abs(destZ - z) == 2) {
            int midZ = z + dz;
            return new BetterBlockPos[] {
                    new BetterBlockPos(x, y, midZ),
                    new BetterBlockPos(destX, y, midZ)
            };
        }
        int midX = x + dx;
        return new BetterBlockPos[] {
                new BetterBlockPos(midX, y, z),
                new BetterBlockPos(midX, y, destZ)
        };
    }

    public Vec3d getGapCenter() {
        return resolveWorldRoute().getGapCenter();
    }

    public Vec3d[] getRenderPathPoints() {
        return resolveWorldRoute().getPoints();
    }

    @Override
    public Vec3d[] getRoutePoints() {
        return getRenderPathPoints();
    }

    private RouteFollowHelper.FollowCommand getFollowCommand(PortalRoute route) {
        Vec3d playerPos = new Vec3d(ctx.player().posX, ctx.player().posY + 0.5D, ctx.player().posZ);
        Vec3d playerVelocity = new Vec3d(ctx.player().motionX, 0.0D, ctx.player().motionZ);
        return RouteFollowHelper.getFollowCommand(route.getPoints(), playerPos, playerVelocity,
                ROUTE_LOOKAHEAD_DISTANCE);
    }

    private PortalRoute resolveWorldRoute() {
        EdgePortal portal = EdgePortalDetector.detect(
                ctx.world(),
                barrierA,
                ctx.world().getBlockState(barrierA),
                barrierB,
                ctx.world().getBlockState(barrierB),
                getTravelFacing(src.x, src.z, dest.x, dest.z),
                src.y);
        Vec3d srcCenter = getBlockCenter(src.x, src.y, src.z);
        Vec3d destCenter = getBlockCenter(dest.x, dest.y, dest.z);
        if (portal == null) {
            return new PortalRoute(srcCenter, destCenter, destCenter, srcCenter, destCenter);
        }
        PortalRoute route = portal.createRoute(srcCenter, destCenter);
        BetterBlockPos recessedFeet = resolveRecessedPortalFeet(resolvePortalCell());
        return augmentRouteWithRecess(route, recessedFeet);
    }

    private static EdgePortal resolvePortal(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, IBlockState stateA, BetterBlockPos barrierB, IBlockState stateB) {
        return EdgePortalDetector.detect(context, barrierA, stateA, barrierB, stateB,
                getTravelFacing(x, z, destX, destZ), y);
    }

    private static Vec3d getBlockCenter(int x, int y, int z) {
        return new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D);
    }

    private boolean isElevatedLanding() {
        return dest.y > src.y;
    }

    private boolean isLowerLanding() {
        return dest.y < src.y;
    }

    private boolean hasVerticalLandingOffset() {
        return dest.y != src.y;
    }

    private void driveDescendingGapTraverse(MovementState state, PortalRoute route) {
        Vec3d playerPos = new Vec3d(ctx.player().posX, ctx.player().posY + 0.5D, ctx.player().posZ);
        Vec3d destCenter = getBlockCenter(dest.x, dest.y, dest.z);
        if (!hasDroppedBelowSourceLevel()) {
            Vec3d commitPoint = buildDescendingCommitPoint(route, destCenter);
            MovementHelper.moveTowards(ctx, state, new Vec3d(commitPoint.x, playerPos.y, commitPoint.z));
            return;
        }
        MovementHelper.moveTowards(ctx, state, new Vec3d(destCenter.x, playerPos.y, destCenter.z));
    }

    private void driveStepCommitTraverse(MovementState state, PortalRoute route,
            RouteFollowHelper.FollowCommand followCommand) {
        Vec3d playerPos = new Vec3d(ctx.player().posX, ctx.player().posY + 0.5D, ctx.player().posZ);
        Vec3d nextPoint = resolveStepCommitPoint(route, playerPos);
        if (horizontalDistance(playerPos, nextPoint) <= STEP_COMMIT_POINT_TOLERANCE) {
            MovementHelper.moveTowards(ctx, state, followCommand.getPreviewPoint());
            return;
        }
        moveForwardStrictlyTowards(state, nextPoint);
    }

    private Vec3d buildDescendingCommitPoint(PortalRoute route, Vec3d destCenter) {
        Vec3d exitPoint = route.getExitPoint();
        Vec3d horizontalTowardDest = new Vec3d(destCenter.x - exitPoint.x, 0.0D, destCenter.z - exitPoint.z);
        if (horizontalTowardDest.lengthSquared() <= 1.0E-6D) {
            Vec3d gapCenter = route.getGapCenter();
            horizontalTowardDest = new Vec3d(exitPoint.x - gapCenter.x, 0.0D, exitPoint.z - gapCenter.z);
        }
        if (horizontalTowardDest.lengthSquared() <= 1.0E-6D) {
            horizontalTowardDest = new Vec3d(destCenter.x - ctx.player().posX, 0.0D, destCenter.z - ctx.player().posZ);
        }
        if (horizontalTowardDest.lengthSquared() <= 1.0E-6D) {
            return exitPoint;
        }
        Vec3d direction = horizontalTowardDest.normalize();
        return new Vec3d(
                exitPoint.x + direction.x * DESCEND_COMMIT_EXTRA_DISTANCE,
                exitPoint.y,
                exitPoint.z + direction.z * DESCEND_COMMIT_EXTRA_DISTANCE);
    }

    private boolean hasDroppedBelowSourceLevel() {
        double standingY = Double.isNaN(sourceStandingYReference) ? ctx.player().posY : sourceStandingYReference;
        return ctx.player().posY <= standingY - 0.20D || ctx.playerFeet().y <= dest.y;
    }

    private void updateSourceStandingReference() {
        double playerY = ctx.player().posY;
        if (Double.isNaN(sourceStandingYReference)) {
            sourceStandingYReference = playerY;
            return;
        }
        if (ctx.player().onGround && ctx.playerFeet().equals(src) && playerY > sourceStandingYReference) {
            sourceStandingYReference = playerY;
        }
    }

    private boolean shouldUseStepCommitController(PortalRoute route) {
        if (!involvesStepLikeSurface() || !ctx.player().onGround) {
            return false;
        }
        Vec3d playerPos = new Vec3d(ctx.player().posX, ctx.player().posY + 0.5D, ctx.player().posZ);
        double horizontalSpeedSq = ctx.player().motionX * ctx.player().motionX
                + ctx.player().motionZ * ctx.player().motionZ;
        return horizontalSpeedSq <= STEP_COMMIT_STALL_SPEED_SQ
                || horizontalDistance(playerPos, route.getGapCenter()) <= STEP_COMMIT_NEAR_PORTAL_DISTANCE
                || horizontalDistance(playerPos, getBlockCenter(dest.x, dest.y, dest.z)) <= STEP_COMMIT_DEST_DISTANCE;
    }

    private boolean involvesStepLikeSurface() {
        return isStepLikeSurface(ctx.world().getBlockState(src.down()))
                || isStepLikeSurface(ctx.world().getBlockState(dest.down()));
    }

    private BetterBlockPos resolvePortalCell() {
        return new BetterBlockPos(
                src.x + Integer.signum(dest.x - src.x),
                src.y,
                src.z + Integer.signum(dest.z - src.z));
    }

    private BetterBlockPos resolveRecessedPortalFeet(BetterBlockPos portalCell) {
        if (portalCell == null || !involvesStepLikeSurface()) {
            return null;
        }
        BetterBlockPos lowered = portalCell.down();
        if (lowered.y >= Math.min(src.y, dest.y)) {
            return null;
        }
        if (!canOccupyFeetPosition(lowered)) {
            return null;
        }
        return lowered;
    }

    private boolean canOccupyFeetPosition(BetterBlockPos feetPos) {
        return MovementHelper.canWalkThrough(ctx, feetPos)
                && MovementHelper.canWalkThrough(ctx, feetPos.up())
                && MovementHelper.canWalkOn(ctx, feetPos.down());
    }

    private PortalRoute augmentRouteWithRecess(PortalRoute route, BetterBlockPos recessedFeet) {
        if (route == null || recessedFeet == null) {
            return route;
        }
        Vec3d recessedCenter = getBlockCenter(recessedFeet.x, recessedFeet.y, recessedFeet.z);
        Vec3d[] points = route.getPoints();
        if (points == null || points.length == 0) {
            return route;
        }
        List<Vec3d> adjusted = new ArrayList<>();
        boolean inserted = false;
        for (Vec3d point : points) {
            adjusted.add(point);
            if (!inserted && point.squareDistanceTo(route.getGapCenter()) <= 1.0E-4D) {
                adjusted.add(recessedCenter);
                inserted = true;
            }
        }
        if (!inserted) {
            adjusted.add(recessedCenter);
        }
        return new PortalRoute(route.getEntryPoint(), route.getGapCenter(), route.getExitPoint(),
                adjusted.toArray(new Vec3d[0]));
    }

    private boolean isMovementContextRecoverable(PortalRoute route) {
        if (playerInValidPosition()) {
            return true;
        }
        Vec3d playerPos = ctx.player().getPositionVector();
        if (distanceToRoute(route.getPoints(), playerPos) <= START_ROUTE_RECOVERY_DISTANCE) {
            return true;
        }
        Vec3d srcCenter = getBlockCenter(src.x, src.y, src.z);
        return horizontalDistance(playerPos, srcCenter) <= START_SOURCE_HORIZONTAL_RECOVERY_DISTANCE;
    }

    private Vec3d resolveStepCommitPoint(PortalRoute route, Vec3d playerPos) {
        Vec3d[] points = route.getPoints();
        if (points.length == 0) {
            return getBlockCenter(dest.x, dest.y, dest.z);
        }
        Vec3d fallback = points[points.length - 1];
        for (Vec3d point : points) {
            if (point == null) {
                continue;
            }
            if (isBehindProgress(point, playerPos)) {
                continue;
            }
            if (horizontalDistance(playerPos, point) <= STEP_COMMIT_POINT_TOLERANCE) {
                continue;
            }
            return point;
        }
        return fallback;
    }

    private boolean isBehindProgress(Vec3d point, Vec3d playerPos) {
        int dx = Integer.signum(dest.x - src.x);
        int dz = Integer.signum(dest.z - src.z);
        if (Math.abs(dest.z - src.z) >= Math.abs(dest.x - src.x)) {
            if (dz >= 0) {
                return playerPos.z > point.z + STEP_COMMIT_BEHIND_MARGIN;
            }
            return playerPos.z < point.z - STEP_COMMIT_BEHIND_MARGIN;
        }
        if (dx >= 0) {
            return playerPos.x > point.x + STEP_COMMIT_BEHIND_MARGIN;
        }
        return playerPos.x < point.x - STEP_COMMIT_BEHIND_MARGIN;
    }

    private void moveForwardStrictlyTowards(MovementState state, Vec3d targetPos) {
        Rotation targetRotation = RotationUtils.calcRotationFromVec3d(
                ctx.playerHead(),
                targetPos,
                ctx.playerRotations()
        ).withPitch(ctx.playerRotations().getPitch());
        state.setTarget(new MovementState.MovementTarget(targetRotation, false));
        state.setInput(Input.MOVE_FORWARD, true);
    }

    private boolean isLowerLandingReached() {
        if (!isLowerLanding()) {
            return false;
        }
        if (!ctx.player().onGround) {
            return false;
        }
        double dx = ctx.player().posX - (dest.x + 0.5D);
        double dz = ctx.player().posZ - (dest.z + 0.5D);
        return dx * dx + dz * dz <= DESCEND_SUCCESS_HORIZONTAL_TOLERANCE * DESCEND_SUCCESS_HORIZONTAL_TOLERANCE
                && ctx.player().posY <= dest.y + 0.01D;
    }

    private void maybeJumpOntoLanding(MovementState state, PortalRoute route,
            RouteFollowHelper.FollowCommand followCommand) {
        if (!ctx.player().onGround || ctx.playerFeet().y >= dest.y) {
            return;
        }
        IBlockState landingOn = ctx.world().getBlockState(dest.down());
        if (!requiresJumpForLanding(landingOn)) {
            return;
        }
        Vec3d playerPos = new Vec3d(ctx.player().posX, ctx.player().posY + 0.5D, ctx.player().posZ);
        double distToExit = horizontalDistance(playerPos, route.getExitPoint());
        double distToDest = horizontalDistance(playerPos, getBlockCenter(dest.x, dest.y, dest.z));
        if ((distToExit <= ASCEND_JUMP_TRIGGER_DISTANCE || distToDest <= ASCEND_JUMP_TRIGGER_DISTANCE - 0.12D)
                && Math.abs(followCommand.getLateralError()) <= ASCEND_JUMP_LATERAL_TOLERANCE) {
            state.setInput(Input.JUMP, true);
        }
    }

    private static boolean isUnsupportedLandingBlock(Block block) {
        return block == Blocks.LADDER
                || block == Blocks.VINE
                || MovementHelper.isConfiguredDangerousBlock(block);
    }

    private static boolean requiresJumpForLanding(IBlockState landingOn) {
        return !isStepLikeSurface(landingOn);
    }

    private static boolean isStepLikeSurface(IBlockState landingOn) {
        Block block = landingOn.getBlock();
        if (block instanceof BlockStairs) {
            return true;
        }
        return block instanceof BlockSlab
                && !((BlockSlab) block).isDouble()
                && landingOn.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM;
    }

    private static Vec3d[] routeThroughGapCenter(PortalRoute route) {
        Vec3d[] points = route.getPoints();
        if (points.length <= 2) {
            return points;
        }
        Vec3d gapCenter = route.getGapCenter();
        int lastIndex = points.length - 1;
        for (int i = 0; i < points.length; i++) {
            if (points[i].squareDistanceTo(gapCenter) <= 1.0E-4D) {
                lastIndex = i;
                break;
            }
        }
        Vec3d[] trimmed = new Vec3d[lastIndex + 1];
        System.arraycopy(points, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private static double horizontalDistance(Vec3d first, Vec3d second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distanceToRoute(Vec3d[] points, Vec3d playerPos) {
        if (points == null || points.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d nearest = nearestPointOnSegment(playerPos, points[i], points[i + 1]);
            best = Math.min(best, playerPos.distanceTo(nearest));
        }
        return best == Double.POSITIVE_INFINITY ? playerPos.distanceTo(points[0]) : best;
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

    private static EnumFacing getTravelFacing(int srcX, int srcZ, int destX, int destZ) {
        int dx = destX - srcX;
        int dz = destZ - srcZ;
        if (Math.abs(dz) >= Math.abs(dx)) {
            return dz >= 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
        }
        return dx >= 0 ? EnumFacing.EAST : EnumFacing.WEST;
    }

}
