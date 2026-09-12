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

package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.RenderEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.*;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IRoutePointMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.IPlayerContext;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.VecUtils;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.FlightDirectPath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourFailureReason;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour.ParkourJumpCandidate;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.OrbitRoutePath;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.path.PathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementFlightCorridor;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortal;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.portal.EdgePortalDetector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Brady
 * @since 8/9/2018
 */
public final class PathRenderer implements IRenderer {

    private PathRenderer() {
    }

    public static void render(RenderEvent event, PathingBehavior behavior) {
        final IPlayerContext ctx = behavior.ctx;
        if (ctx.world() == null) {
            return;
        }
        if (ctx.minecraft().currentScreen instanceof GuiClick) {
            ((GuiClick) ctx.minecraft().currentScreen).onRender();
        }

        final float partialTicks = event.getPartialTicks();
        final Goal goal = behavior.getGoal();

        final int thisPlayerDimension = ctx.world().provider.getDimensionType().getId();
        final int currentRenderViewDimension = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()
                .world().provider.getDimensionType().getId();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        if (goal != null && settings.renderGoal.value) {
            drawGoal(ctx.player(), goal, partialTicks, settings.colorGoalBox.value);
        }

        if (settings.renderDetectedEdgePortals.value) {
            drawDetectedEdgePortals(ctx);
        }

        if (shouldSuppressPathRenderingForKillAuraOrbit(behavior)) {
            return;
        }

        if (!settings.renderPath.value) {
            return;
        }

        PathExecutor current = behavior.getCurrent(); // this should prevent most race conditions?
        PathExecutor next = behavior.getNext(); // like, now it's not possible for current!=null to be true, then
                                                 // suddenly false because of another thread
        IPath retainedFlightPath = behavior.getRetainedFlightPath();
        if (current != null && settings.renderSelectionBoxes.value) {
            drawManySelectionBoxes(ctx.player(), current.toBreak(), settings.colorBlocksToBreak.value);
            drawManySelectionBoxes(ctx.player(), current.toPlace(), settings.colorBlocksToPlace.value);
            drawManySelectionBoxes(ctx.player(), current.toWalkInto(), settings.colorBlocksToWalkInto.value);
        }

        // drawManySelectionBoxes(player,
        // Collections.singletonList(behavior.pathStart()), partialTicks, Color.WHITE);

        // Render the current path, if there is one
        if (current != null && current.getPath() != null
                && (retainedFlightPath == null || current.getPath() instanceof FlightDirectPath)) {
            int renderBegin = Math.max(current.getPosition() - 3, 0);
            if (current.getPath() instanceof FlightDirectPath) {
                drawFlightCorridor((FlightDirectPath) current.getPath(), 0, settings.colorCurrentPath.value,
                        settings.fadePath.value, 10, 20, ctx.player().getPositionVector());
            } else {
                drawPath(current.getPath(), renderBegin, settings.colorCurrentPath.value,
                        settings.fadePath.value, 10, 20);
            }
            if (settings.parkourDebugRender.value) {
                renderCurrentParkourDebug(ctx.player(), current, behavior.getPathReplanTriggerCount());
            }
        }

        if ((current == null || !(current.getPath() instanceof FlightDirectPath)) && retainedFlightPath != null) {
            drawFlightCorridor((FlightDirectPath) retainedFlightPath, 0, settings.colorCurrentPath.value, false, 10, 20,
                    ctx.player().getPositionVector());
        }

        if (next != null && next.getPath() != null) {
            drawPath(next.getPath(), 0, settings.colorNextPath.value, settings.fadePath.value, 10, 20);
        }

        // If there is a path calculation currently running, render the path calculation
        // process
        if (retainedFlightPath == null) {
            behavior.getInProgress().ifPresent(currentlyRunning -> {
            currentlyRunning.bestPathSoFar().ifPresent(p -> {
                drawPath(p, 0, settings.colorBestPathSoFar.value, settings.fadePath.value, 10, 20);
            });

            currentlyRunning.pathToMostRecentNodeConsidered().ifPresent(mr -> {
                drawPath(mr, 0, settings.colorMostRecentConsidered.value, settings.fadePath.value, 10, 20);
                drawManySelectionBoxes(ctx.player(), Collections.singletonList(mr.getDest()),
                        settings.colorMostRecentConsidered.value);
            });
            });
        }
    }

    public static void drawPath(IPath path, int startIndex, Color color, boolean fadeOut,
            int fadeStart0, int fadeEnd0) {
        if (path instanceof FlightDirectPath) {
            drawFlightCorridor((FlightDirectPath) path, startIndex, color, fadeOut, fadeStart0, fadeEnd0, null);
            return;
        }
        if (path instanceof OrbitRoutePath) {
            // KillAura orbit already renders its guide loop explicitly. Suppress the
            // default Baritone segment render so the player only sees the sampled
            // circle that the orbit route actually follows.
            return;
        }
        IPath renderPath = path;
        List<IMovement> movements;
        try {
            movements = renderPath.movements();
        } catch (IllegalStateException ignored) {
            try {
                renderPath = path.postProcess();
                movements = renderPath.movements();
            } catch (Throwable ignoredAgain) {
                drawPath(path.positions(), startIndex, color, fadeOut, fadeStart0, fadeEnd0);
                return;
            }
        }
        if (movements.isEmpty()) {
            drawPath(renderPath.positions(), startIndex, color, fadeOut, fadeStart0, fadeEnd0);
            return;
        }
        drawMovementPath(movements, startIndex, color, fadeOut, fadeStart0, fadeEnd0);
    }

    private static void drawFlightCorridor(FlightDirectPath path, int startIndex, Color color, boolean fadeOut,
            int fadeStart0, int fadeEnd0, Vec3d playerPosition) {
        List<BetterBlockPos> positions = path.positions();
        if (positions == null || positions.size() < 2) {
            return;
        }
        int segmentStart = Math.max(0, Math.min(startIndex, positions.size() - 2));
        double trimDistance = playerPosition == null ? 0.0D
                : Math.max(0.0D, closestDistanceAlongPath(positions, playerPosition) - 2.0D);
        double cumulativeDistance = 0.0D;
        int fadeStart = fadeStart0 + segmentStart;
        int fadeEnd = fadeEnd0 + segmentStart;
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);
        for (int index = segmentStart; index < positions.size() - 1; index++) {
            if (fadeOut) {
                if (index > fadeEnd) {
                    break;
                }
                float alpha = index <= fadeStart ? 0.4F
                        : 0.4F * (1.0F - (float) (index - fadeStart) / (float) Math.max(1, fadeEnd - fadeStart));
                IRenderer.glColor(color, alpha);
            }
            Vec3d segmentFrom = blockCenter(positions.get(index));
            Vec3d segmentTo = blockCenter(positions.get(index + 1));
            double segmentLength = segmentFrom.distanceTo(segmentTo);
            double segmentEndDistance = cumulativeDistance + segmentLength;
            if (segmentEndDistance <= trimDistance) {
                cumulativeDistance = segmentEndDistance;
                continue;
            }
            if (trimDistance > cumulativeDistance && segmentLength > 1.0E-6D) {
                double fraction = (trimDistance - cumulativeDistance) / segmentLength;
                segmentFrom = segmentFrom.add(segmentTo.subtract(segmentFrom).scale(fraction));
            }
            double radius = ((MovementFlightCorridor)
                    path.movements().get(index)).getCorridorRadius();
            emitFlightCorridorSegment(segmentFrom, segmentTo, radius);
            cumulativeDistance = segmentEndDistance;
        }
        IRenderer.endLines(settings.renderPathIgnoreDepth.value);
    }

    private static double closestDistanceAlongPath(List<BetterBlockPos> positions, Vec3d playerPosition) {
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        double bestAlong = 0.0D;
        double cumulative = 0.0D;
        for (int index = 0; index < positions.size() - 1; index++) {
            Vec3d start = blockCenter(positions.get(index));
            Vec3d end = blockCenter(positions.get(index + 1));
            Vec3d segment = end.subtract(start);
            double lengthSq = segment.lengthSquared();
            double length = Math.sqrt(lengthSq);
            double fraction = lengthSq <= 1.0E-6D ? 0.0D
                    : Math.max(0.0D, Math.min(1.0D,
                            playerPosition.subtract(start).dotProduct(segment) / lengthSq));
            Vec3d nearest = start.add(segment.scale(fraction));
            double distanceSq = playerPosition.squareDistanceTo(nearest);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestAlong = cumulative + length * fraction;
            }
            cumulative += length;
        }
        return bestAlong;
    }

    private static void emitFlightCorridorSegment(Vec3d start, Vec3d end, double radius) {
        Vec3d direction = end.subtract(start);
        if (direction.lengthSquared() <= 1.0E-6D) {
            return;
        }
        Vec3d normalized = direction.normalize();
        Vec3d axisA;
        Vec3d axisB;
        if (Math.abs(normalized.y) > 0.9D) {
            axisA = new Vec3d(1.0D, 0.0D, 0.0D);
            axisB = new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            axisA = new Vec3d(-normalized.z, 0.0D, normalized.x).normalize();
            axisB = new Vec3d(0.0D, 1.0D, 0.0D);
        }

        double halfWidth = radius + 0.3D;
        double halfHeight = Math.abs(normalized.y) > 0.9D ? halfWidth : radius + 0.9D;
        if (Math.abs(normalized.y) <= 0.9D) {
            start = start.addVector(0.0D, 0.4D, 0.0D);
            end = end.addVector(0.0D, 0.4D, 0.0D);
        }
        double extension = Math.max(0.0D,
                Math.min(halfWidth, settings.flightCorridorCornerExtension.value));
        for (int signA : new int[] { -1, 1 }) {
            for (int signB : new int[] { -1, 1 }) {
                Vec3d cornerOffset = axisA.scale(signA * halfWidth).add(axisB.scale(signB * halfHeight));
                Vec3d cornerStart = start.add(cornerOffset);
                Vec3d cornerEnd = end.add(cornerOffset);
                IRenderer.emitLine(cornerStart, cornerEnd);
                if (extension <= 1.0E-4D) {
                    continue;
                }

                Vec3d innerAOffset = cornerOffset.subtract(axisA.scale(signA * extension));
                Vec3d innerBOffset = cornerOffset.subtract(axisB.scale(signB * extension));
                Vec3d innerAStart = start.add(innerAOffset);
                Vec3d innerAEnd = end.add(innerAOffset);
                Vec3d innerBStart = start.add(innerBOffset);
                Vec3d innerBEnd = end.add(innerBOffset);

                // Each corner is a wireframe angle profile: the outer rail plus
                // two inward wing edges, connected on both channel end faces.
                IRenderer.emitLine(innerAStart, innerAEnd);
                IRenderer.emitLine(innerBStart, innerBEnd);
                IRenderer.emitLine(cornerStart, innerAStart);
                IRenderer.emitLine(cornerStart, innerBStart);
                IRenderer.emitLine(cornerEnd, innerAEnd);
                IRenderer.emitLine(cornerEnd, innerBEnd);
            }
        }
    }

    private static Vec3d blockCenter(BetterBlockPos pos) {
        return new Vec3d(pos.x + 0.5D, pos.y + 0.5D, pos.z + 0.5D);
    }

    private static boolean shouldSuppressPathRenderingForKillAuraOrbit(PathingBehavior behavior) {
        try {
            Object primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (!(primary instanceof Baritone)) {
                return false;
            }
            Baritone baritone = (Baritone) primary;
            return baritone.getPathingBehavior() == behavior
                    && baritone.getKillAuraOrbitProcess() != null
                    && baritone.getKillAuraOrbitProcess().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void drawPath(List<BetterBlockPos> positions, int startIndex, Color color, boolean fadeOut,
            int fadeStart0, int fadeEnd0) {
        drawPath(positions, startIndex, color, fadeOut, fadeStart0, fadeEnd0, 0.5D);
    }

    public static void drawPath(List<BetterBlockPos> positions, int startIndex, Color color, boolean fadeOut,
            int fadeStart0, int fadeEnd0, double offset) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;

        for (int i = startIndex, next; i < positions.size() - 1; i = next) {
            BetterBlockPos start = positions.get(i);
            BetterBlockPos end = positions.get(next = i + 1);

            int dirX = end.x - start.x;
            int dirY = end.y - start.y;
            int dirZ = end.z - start.z;

            while (next + 1 < positions.size() && (!fadeOut || next + 1 < fadeStart) &&
                    (dirX == positions.get(next + 1).x - end.x &&
                            dirY == positions.get(next + 1).y - end.y &&
                            dirZ == positions.get(next + 1).z - end.z)) {
                end = positions.get(++next);
            }

            if (fadeOut) {
                float alpha;

                if (i <= fadeStart) {
                    alpha = 0.4F;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha = 0.4F * (1.0F - (float) (i - fadeStart) / (float) (fadeEnd - fadeStart));
                }

                IRenderer.glColor(color, alpha);
            }

            emitPathLine(start.x, start.y, start.z, end.x, end.y, end.z, offset);
        }

        IRenderer.endLines(settings.renderPathIgnoreDepth.value);
    }

    public static void drawPolyline(List<Vec3d> positions, Color color, float alpha, float lineWidth,
            boolean ignoreDepth) {
        if (positions == null || positions.size() < 2) {
            return;
        }

        IRenderer.startLines(color, alpha, lineWidth, ignoreDepth);
        Vec3d previous = positions.get(0);
        for (int i = 1; i < positions.size(); i++) {
            Vec3d next = positions.get(i);
            IRenderer.emitLine(previous, next);
            previous = next;
        }
        IRenderer.endLines(ignoreDepth);
    }

    private static void drawMovementPath(List<IMovement> movements, int startIndex, Color color, boolean fadeOut,
            int fadeStart0, int fadeEnd0) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value, settings.renderPathIgnoreDepth.value);

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;
        for (int i = startIndex; i < movements.size(); i++) {
            if (fadeOut) {
                float alpha;
                if (i <= fadeStart) {
                    alpha = 0.4F;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha = 0.4F * (1.0F - (float) (i - fadeStart) / (float) (fadeEnd - fadeStart));
                }
                IRenderer.glColor(color, alpha);
            }
            emitMovementPath(movements.get(i));
        }
        IRenderer.endLines(settings.renderPathIgnoreDepth.value);
    }

    private static void drawDetectedEdgePortals(IPlayerContext ctx) {
        int radius = 6;
        int baseX = MathHelper.floor(ctx.player().posX);
        int baseY = MathHelper.floor(ctx.player().posY);
        int baseZ = MathHelper.floor(ctx.player().posZ);

        IRenderer.startLines(settings.colorDetectedEdgePortals.value, settings.pathRenderLineWidthPixels.value,
                settings.renderPathIgnoreDepth.value);
        for (int y = baseY - 1; y <= baseY + 1; y++) {
            for (int x = baseX - radius; x <= baseX + radius; x++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    emitDetectedPortal(ctx, new BetterBlockPos(x, y, z), new BetterBlockPos(x + 1, y, z),
                            EnumFacing.SOUTH);
                    emitDetectedPortal(ctx, new BetterBlockPos(x, y, z), new BetterBlockPos(x, y, z + 1),
                            EnumFacing.EAST);
                }
            }
        }
        IRenderer.endLines(settings.renderPathIgnoreDepth.value);
    }

    private static void emitDetectedPortal(IPlayerContext ctx, BetterBlockPos barrierA, BetterBlockPos barrierB,
            EnumFacing travelFacing) {
        EdgePortal portal = EdgePortalDetector.detect(ctx.world(), barrierA, ctx.world().getBlockState(barrierA),
                barrierB, ctx.world().getBlockState(barrierB), travelFacing, barrierA.y);
        if (portal == null) {
            return;
        }
        Vec3d gapCenter = portal.getGapCenter();
        Vec3d src;
        Vec3d dest;
        if (travelFacing.getAxis() == EnumFacing.Axis.Z) {
            src = gapCenter.addVector(0.0D, 0.0D, -1.0D);
            dest = gapCenter.addVector(0.0D, 0.0D, 1.0D);
        } else {
            src = gapCenter.addVector(-1.0D, 0.0D, 0.0D);
            dest = gapCenter.addVector(1.0D, 0.0D, 0.0D);
        }
        Vec3d[] points = portal.createRoute(src, dest).getPoints();
        for (int i = 0; i < points.length - 1; i++) {
            emitWorldPathLine(points[i], points[i + 1]);
        }
        emitWorldPathLine(gapCenter.x, gapCenter.y - 0.25D, gapCenter.z,
                gapCenter.x, gapCenter.y + 0.25D, gapCenter.z);
    }

    private static void emitMovementPath(IMovement movement) {
        if (movement instanceof MovementParkour) {
            emitRoute(buildParkourPreviewPath(((MovementParkour) movement).getCandidate()));
            return;
        }
        if (movement instanceof IRoutePointMovement) {
            emitRoute(((IRoutePointMovement) movement).getRoutePoints());
            return;
        }
        emitWorldPathLine(VecUtils.getBlockPosCenter(movement.getSrc()), VecUtils.getBlockPosCenter(movement.getDest()));
    }

    private static void renderCurrentParkourDebug(Entity player, PathExecutor current, int replanTriggerCount) {
        if (current == null || current.getPath() == null) {
            return;
        }
        int position = current.getPosition();
        List<IMovement> movements = current.getPath().movements();
        if (position < 0 || position >= movements.size()) {
            return;
        }
        IMovement movement = movements.get(position);
        if (!(movement instanceof MovementParkour)) {
            return;
        }

        MovementParkour parkour = (MovementParkour) movement;
        ParkourJumpCandidate candidate = parkour.getCandidate();
        if (candidate == null) {
            return;
        }

        AxisAlignedBB launchAabb = buildLaunchWindowAabb(candidate);
        AxisAlignedBB landingAabb = buildLandingWindowAabb(candidate);
        Color phaseColor = resolveParkourPhaseColor(parkour.getExecutionPhaseName(), parkour.getLastFailureReason());
        Color landingColor = parkour.getLastFailureReason() == ParkourFailureReason.NONE ? new Color(80, 255, 190)
                : new Color(255, 110, 110);

        IRenderer.startLines(phaseColor, settings.pathRenderLineWidthPixels.value + 0.75F,
                settings.renderPathIgnoreDepth.value);
        IRenderer.emitAABB(launchAabb, 0.01D);
        IRenderer.emitLine(player.posX, player.posY + 0.1D, player.posZ, player.posX, player.posY + 1.55D, player.posZ);
        IRenderer.endLines(settings.renderPathIgnoreDepth.value);

        IRenderer.startLines(landingColor, settings.pathRenderLineWidthPixels.value + 0.5F,
                settings.renderPathIgnoreDepth.value);
        IRenderer.emitAABB(landingAabb, 0.01D);
        emitRoute(buildParkourPreviewPath(candidate));
        IRenderer.endLines(settings.renderPathIgnoreDepth.value);

        renderParkourDebugLabels(player, parkour, candidate, launchAabb, landingAabb, phaseColor, landingColor,
                replanTriggerCount);
    }

    private static AxisAlignedBB buildLaunchWindowAabb(ParkourJumpCandidate candidate) {
        Vec3d srcCenter = VecUtils.getBlockPosCenter(candidate.getSrc());
        double minForward = candidate.getLaunchWindow().getMinProgress();
        double maxForward = candidate.getLaunchWindow().getMaxProgress();
        double forwardMid = (minForward + maxForward) * 0.5D;
        double forwardHalf = Math.max(0.08D, (maxForward - minForward) * 0.5D);
        double lateralError = Math.max(0.08D, candidate.getLaunchWindow().getMaxLateralError());

        double lateralOffset = 0.0D;
        if (candidate.getType().name().contains("EDGE") && candidate.getLateral() != null) {
            lateralOffset = 0.24D;
        }

        double centerX = srcCenter.x + candidate.getForward().getFrontOffsetX() * forwardMid
                + (candidate.getLateral() == null ? 0.0D : candidate.getLateral().getFrontOffsetX() * lateralOffset);
        double centerZ = srcCenter.z + candidate.getForward().getFrontOffsetZ() * forwardMid
                + (candidate.getLateral() == null ? 0.0D : candidate.getLateral().getFrontOffsetZ() * lateralOffset);

        double xExtent = 0.16D
                + Math.abs(candidate.getForward().getFrontOffsetX()) * forwardHalf
                + (candidate.getLateral() == null ? 0.0D : Math.abs(candidate.getLateral().getFrontOffsetX()) * lateralError)
                + (candidate.getForward().getAxis() == EnumFacing.Axis.Z ? lateralError : 0.0D);
        double zExtent = 0.16D
                + Math.abs(candidate.getForward().getFrontOffsetZ()) * forwardHalf
                + (candidate.getLateral() == null ? 0.0D : Math.abs(candidate.getLateral().getFrontOffsetZ()) * lateralError)
                + (candidate.getForward().getAxis() == EnumFacing.Axis.X ? lateralError : 0.0D);

        return new AxisAlignedBB(
                centerX - xExtent,
                candidate.getSrc().y + 0.02D,
                centerZ - zExtent,
                centerX + xExtent,
                candidate.getSrc().y + 1.42D,
                centerZ + zExtent);
    }

    private static AxisAlignedBB buildLandingWindowAabb(ParkourJumpCandidate candidate) {
        Vec3d destCenter = VecUtils.getBlockPosCenter(candidate.getDest());
        double radius = Math.sqrt(Math.max(0.0D, candidate.getLandingWindow().getMaxFlatDistanceSq()));
        return new AxisAlignedBB(
                destCenter.x - radius,
                candidate.getDest().y + 0.02D,
                destCenter.z - radius,
                destCenter.x + radius,
                candidate.getDest().y + 1.82D,
                destCenter.z + radius);
    }

    private static Color resolveParkourPhaseColor(String phaseName, ParkourFailureReason reason) {
        if (reason != null && reason != ParkourFailureReason.NONE) {
            return new Color(255, 110, 110);
        }
        if ("TAKEOFF".equalsIgnoreCase(phaseName)) {
            return new Color(255, 214, 102);
        }
        if ("AIR_CORRECTION".equalsIgnoreCase(phaseName)) {
            return new Color(115, 203, 255);
        }
        if ("LAND_CONFIRM".equalsIgnoreCase(phaseName)) {
            return new Color(120, 255, 182);
        }
        if ("SPRINT_PRIME".equalsIgnoreCase(phaseName)) {
            return new Color(215, 148, 255);
        }
        if ("RECOVER".equalsIgnoreCase(phaseName)) {
            return new Color(255, 164, 96);
        }
        return new Color(255, 235, 150);
    }

    private static void renderParkourDebugLabels(Entity player, MovementParkour parkour, ParkourJumpCandidate candidate,
            AxisAlignedBB launchAabb, AxisAlignedBB landingAabb, Color phaseColor, Color landingColor,
            int replanTriggerCount) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null) {
            return;
        }

        List<String> launchLines = new ArrayList<>();
        launchLines.add("§d" + safeLabel(candidate.getType().name()));
        launchLines.add("§f阶段: §e" + safeLabel(parkour.getExecutionPhaseName()));
        if (candidate.getChainLength() > 1) {
            launchLines.add("§7段数: §f" + (parkour.getActiveSegmentIndex() + 1) + "/" + candidate.getChainLength());
        }
        if (candidate.getLateral() != null) {
            launchLines.add("§7侧偏: §b" + safeLabel(candidate.getLateral().name()));
        }
        launchLines.add("§7链式潜力: §f" + candidate.getChainPotential());
        launchLines.add("§7重规划: §f" + replanTriggerCount);

        List<String> landingLines = new ArrayList<>();
        landingLines.add("§a落点");
        landingLines.add("§7类型: §f" + safeLabel(candidate.getType().name()));
        landingLines.add("§7恢复距离: §f" + String.format(java.util.Locale.ROOT, "%.1f", candidate.getLandingRecoveryDistance()));
        ParkourFailureReason failureReason = parkour.getLastFailureReason();
        if (failureReason != null && failureReason != ParkourFailureReason.NONE) {
            landingLines.add("§c失败: §f" + safeLabel(failureReason.name()));
        } else {
            landingLines.add("§7状态: §aREADY");
        }

        drawWorldLabel(mc, getAabbTopCenter(launchAabb).addVector(0.0D, 0.12D, 0.0D), launchLines,
                phaseColor.getRGB(), settings.renderPathIgnoreDepth.value);
        drawWorldLabel(mc, getAabbTopCenter(landingAabb).addVector(0.0D, 0.12D, 0.0D), landingLines,
                landingColor.getRGB(), settings.renderPathIgnoreDepth.value);
    }

    private static Vec3d getAabbTopCenter(AxisAlignedBB aabb) {
        return new Vec3d((aabb.minX + aabb.maxX) * 0.5D, aabb.maxY, (aabb.minZ + aabb.maxZ) * 0.5D);
    }

    private static String safeLabel(String value) {
        return value == null ? "" : value;
    }

    private static void drawWorldLabel(Minecraft mc, Vec3d worldPos, List<String> lines, int textColor,
            boolean throughWalls) {
        if (mc.fontRenderer == null || renderManager == null || lines == null || lines.isEmpty()) {
            return;
        }

        double x = worldPos.x - renderManager.viewerPosX;
        double y = worldPos.y - renderManager.viewerPosY;
        double z = worldPos.z - renderManager.viewerPosZ;
        float scale = 0.026F;
        int lineHeight = mc.fontRenderer.FONT_HEIGHT + 1;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        float pitchFactor = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
        GlStateManager.rotate(renderManager.playerViewX * pitchFactor, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        if (throughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.depthMask(false);

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }

        int totalHeight = lines.size() * lineHeight;
        net.minecraft.client.gui.Gui.drawRect(-maxWidth / 2 - 3, -2, maxWidth / 2 + 3, totalHeight + 1, 0x66101010);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            mc.fontRenderer.drawStringWithShadow(line, -mc.fontRenderer.getStringWidth(line) / 2.0F,
                    i * lineHeight, textColor);
        }

        GlStateManager.depthMask(true);
        if (throughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static void emitWorldPathLine(Vec3d start, Vec3d end) {
        emitWorldPathLine(start.x, start.y, start.z, end.x, end.y, end.z);
    }

    private static void emitRoute(Vec3d[] points) {
        if (points == null || points.length < 2) {
            return;
        }
        for (int i = 0; i < points.length - 1; i++) {
            emitWorldPathLine(points[i], points[i + 1]);
        }
    }

    private static Vec3d[] buildParkourPreviewPath(ParkourJumpCandidate candidate) {
        if (candidate == null) {
            return new Vec3d[0];
        }
        Vec3d[] basePoints = candidate.getRoutePoints();
        if (basePoints == null || basePoints.length < 2) {
            basePoints = new Vec3d[] {
                    VecUtils.getBlockPosCenter(candidate.getSrc()),
                    VecUtils.getBlockPosCenter(candidate.getDest())
            };
        }

        double totalLength = getHorizontalRouteLength(basePoints);
        if (totalLength <= 1.0E-4D) {
            return basePoints;
        }

        int sampleCount = Math.max(8, candidate.getForwardDistance() * 4 + candidate.getChainLength() * 2);
        double apexLift = getPreviewApexLift(candidate);
        double startY = basePoints[0].y;
        double endY = basePoints[basePoints.length - 1].y;
        Vec3d[] preview = new Vec3d[sampleCount + 1];
        for (int i = 0; i <= sampleCount; i++) {
            double t = i / (double) sampleCount;
            Vec3d basePoint = pointAlongHorizontalRoute(basePoints, totalLength * t);
            double groundY = startY + (endY - startY) * t;
            double arcY = groundY + 4.0D * t * (1.0D - t) * apexLift;
            preview[i] = new Vec3d(basePoint.x, arcY, basePoint.z);
        }
        return preview;
    }

    private static double getHorizontalRouteLength(Vec3d[] points) {
        double total = 0.0D;
        for (int i = 0; i < points.length - 1; i++) {
            double dx = points[i + 1].x - points[i].x;
            double dz = points[i + 1].z - points[i].z;
            total += Math.sqrt(dx * dx + dz * dz);
        }
        return total;
    }

    private static Vec3d pointAlongHorizontalRoute(Vec3d[] points, double targetDistance) {
        if (points == null || points.length == 0) {
            return Vec3d.ZERO;
        }
        if (points.length == 1 || targetDistance <= 0.0D) {
            return points[0];
        }
        double traversed = 0.0D;
        for (int i = 0; i < points.length - 1; i++) {
            Vec3d start = points[i];
            Vec3d end = points[i + 1];
            double dx = end.x - start.x;
            double dz = end.z - start.z;
            double segmentLength = Math.sqrt(dx * dx + dz * dz);
            if (segmentLength <= 1.0E-6D) {
                continue;
            }
            if (targetDistance <= traversed + segmentLength) {
                double t = (targetDistance - traversed) / segmentLength;
                return new Vec3d(
                        start.x + (end.x - start.x) * t,
                        start.y + (end.y - start.y) * t,
                        start.z + (end.z - start.z) * t);
            }
            traversed += segmentLength;
        }
        return points[points.length - 1];
    }

    private static double getPreviewApexLift(ParkourJumpCandidate candidate) {
        double lift = 0.58D;
        if (candidate.requiresSprint()) {
            lift += 0.10D;
        }
        if (candidate.isAscend()) {
            lift += 0.24D;
        }
        if (candidate.getLateral() != null) {
            lift += 0.05D;
        }
        if (candidate.isEdgeLaunch()) {
            lift += 0.04D;
        }
        if (candidate.getForwardDistance() >= 3) {
            lift += 0.08D;
        }
        if (candidate.getDest().y < candidate.getSrc().y) {
            lift += 0.10D;
        }
        if (candidate.getChainLength() > 1) {
            lift += 0.05D;
        }
        return lift;
    }

    private static void emitWorldPathLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        final double extraOffset = 0.03D;
        double vpX = renderManager.viewerPosX;
        double vpY = renderManager.viewerPosY;
        double vpZ = renderManager.viewerPosZ;
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;

        buffer.pos(x1 - vpX, y1 - vpY, z1 - vpZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(x2 - vpX, y2 - vpY, z2 - vpZ).color(color[0], color[1], color[2], color[3]).endVertex();

        if (renderPathAsFrickinThingy) {
            buffer.pos(x2 - vpX, y2 - vpY, z2 - vpZ).color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(x2 - vpX, y2 + extraOffset - vpY, z2 - vpZ).color(color[0], color[1], color[2], color[3])
                    .endVertex();

            buffer.pos(x2 - vpX, y2 + extraOffset - vpY, z2 - vpZ).color(color[0], color[1], color[2], color[3])
                    .endVertex();
            buffer.pos(x1 - vpX, y1 + extraOffset - vpY, z1 - vpZ).color(color[0], color[1], color[2], color[3])
                    .endVertex();

            buffer.pos(x1 - vpX, y1 + extraOffset - vpY, z1 - vpZ).color(color[0], color[1], color[2], color[3])
                    .endVertex();
            buffer.pos(x1 - vpX, y1 - vpY, z1 - vpZ).color(color[0], color[1], color[2], color[3]).endVertex();
        }
    }

    private static void emitPathLine(double x1, double y1, double z1, double x2, double y2, double z2, double offset) {
        final double extraOffset = offset + 0.03D;
        double vpX = renderManager.viewerPosX;
        double vpY = renderManager.viewerPosY;
        double vpZ = renderManager.viewerPosZ;
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;

        buffer.pos(x1 + offset - vpX, y1 + offset - vpY, z1 + offset - vpZ)
                .color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(x2 + offset - vpX, y2 + offset - vpY, z2 + offset - vpZ)
                .color(color[0], color[1], color[2], color[3]).endVertex();

        if (renderPathAsFrickinThingy) {
            buffer.pos(x2 + offset - vpX, y2 + offset - vpY, z2 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(x2 + offset - vpX, y2 + extraOffset - vpY, z2 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();

            buffer.pos(x2 + offset - vpX, y2 + extraOffset - vpY, z2 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(x1 + offset - vpX, y1 + extraOffset - vpY, z1 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();

            buffer.pos(x1 + offset - vpX, y1 + extraOffset - vpY, z1 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(x1 + offset - vpX, y1 + offset - vpY, z1 + offset - vpZ)
                    .color(color[0], color[1], color[2], color[3]).endVertex();
        }
    }

    public static void drawManySelectionBoxes(Entity player, Collection<BlockPos> positions, Color color) {
        IRenderer.startLines(color, settings.pathRenderLineWidthPixels.value,
                settings.renderSelectionBoxesIgnoreDepth.value);

        // BlockPos blockpos = movingObjectPositionIn.getBlockPos();
        BlockStateInterface bsi = new BlockStateInterface(
                BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext()); // TODO this assumes same dimension
                                                                                    // between primary baritone and
                                                                                    // render view? is this safe?

        positions.forEach(pos -> {
            IBlockState state = bsi.get0(pos);
            AxisAlignedBB toDraw;

            if (state.getBlock().equals(Blocks.AIR)) {
                toDraw = Blocks.DIRT.getDefaultState().getSelectedBoundingBox(player.world, pos);
            } else {
                toDraw = state.getSelectedBoundingBox(player.world, pos);
            }

            IRenderer.emitAABB(toDraw, .002D);
        });

        IRenderer.endLines(settings.renderSelectionBoxesIgnoreDepth.value);
    }

    public static void drawGoal(Entity player, Goal goal, float partialTicks, Color color) {
        drawGoal(player, goal, partialTicks, color, true);
    }

    private static void drawGoal(Entity player, Goal goal, float partialTicks, Color color, boolean setupRender) {
        double renderPosX = renderManager.viewerPosX;
        double renderPosY = renderManager.viewerPosY;
        double renderPosZ = renderManager.viewerPosZ;
        double minX, maxX;
        double minZ, maxZ;
        double minY, maxY;
        double y, y1, y2;
        if (!settings.renderGoalAnimated.value) {
            // y = 1 causes rendering issues when the player is at the same y as the top of
            // a block for some reason
            y = 0.999F;
        } else {
            y = MathHelper.cos((float) (((float) ((System.nanoTime() / 100000L) % 20000L)) / 20000F * Math.PI * 2));
        }
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y /= 2;
            }
            y1 = 1 + y + goalPos.getY() - renderPosY;
            y2 = 1 - y + goalPos.getY() - renderPosY;
            minY = goalPos.getY() - renderPosY;
            maxY = minY + 2;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y1 -= 0.5;
                y2 -= 0.5;
                maxY--;
            }
            drawDankLitGoalBox(color, minX, maxX, minZ, maxZ, minY, maxY, y1, y2, setupRender);
        } else if (goal instanceof GoalXZ) {
            GoalXZ goalPos = (GoalXZ) goal;

            if (goal instanceof GoalFlightXZ || settings.renderGoalXZBeacon.value) {
                textureManager.bindTexture(TileEntityBeaconRenderer.TEXTURE_BEACON_BEAM);

                if (settings.renderGoalIgnoreDepth.value) {
                    GlStateManager.disableDepth();
                }

                TileEntityBeaconRenderer.renderBeamSegment(
                        goalPos.getX() - renderPosX,
                        -renderPosY,
                        goalPos.getZ() - renderPosZ,
                        settings.renderGoalAnimated.value ? partialTicks : 0,
                        1.0,
                        settings.renderGoalAnimated.value ? player.world.getTotalWorldTime() : 0,
                        0,
                        256,
                        color.getColorComponents(null));

                if (settings.renderGoalIgnoreDepth.value) {
                    GlStateManager.enableDepth();
                }
                return;
            }

            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;

            y1 = 0;
            y2 = 0;
            minY = 0 - renderPosY;
            maxY = 256 - renderPosY;
            drawDankLitGoalBox(color, minX, maxX, minZ, maxZ, minY, maxY, y1, y2, setupRender);
        } else if (goal instanceof GoalComposite) {
            // Simple way to determine if goals can be batched, without having some sort of
            // GoalRenderer
            boolean batch = Arrays.stream(((GoalComposite) goal).goals()).allMatch(IGoalRenderPos.class::isInstance);

            if (batch) {
                IRenderer.startLines(color, settings.goalRenderLineWidthPixels.value,
                        settings.renderGoalIgnoreDepth.value);
            }
            for (Goal g : ((GoalComposite) goal).goals()) {
                drawGoal(player, g, partialTicks, color, !batch);
            }
            if (batch) {
                IRenderer.endLines(settings.renderGoalIgnoreDepth.value);
            }
        } else if (goal instanceof GoalInverted) {
            drawGoal(player, ((GoalInverted) goal).origin, partialTicks, settings.colorInvertedGoalBox.value);
        } else if (goal instanceof GoalYLevel) {
            GoalYLevel goalpos = (GoalYLevel) goal;
            minX = player.posX - settings.yLevelBoxSize.value - renderPosX;
            minZ = player.posZ - settings.yLevelBoxSize.value - renderPosZ;
            maxX = player.posX + settings.yLevelBoxSize.value - renderPosX;
            maxZ = player.posZ + settings.yLevelBoxSize.value - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
            drawDankLitGoalBox(color, minX, maxX, minZ, maxZ, minY, maxY, y1, y2, setupRender);
        }
    }

    private static void drawDankLitGoalBox(Color colorIn, double minX, double maxX, double minZ, double maxZ,
            double minY, double maxY, double y1, double y2, boolean setupRender) {
        if (setupRender) {
            IRenderer.startLines(colorIn, settings.goalRenderLineWidthPixels.value,
                    settings.renderGoalIgnoreDepth.value);
        }

        renderHorizontalQuad(minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(minX, maxX, minZ, maxZ, y2);

        buffer.pos(minX, minY, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(minX, maxY, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(maxX, minY, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(maxX, maxY, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(maxX, minY, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(minX, minY, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.pos(minX, maxY, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();

        if (setupRender) {
            IRenderer.endLines(settings.renderGoalIgnoreDepth.value);
        }
    }

    private static void renderHorizontalQuad(double minX, double maxX, double minZ, double maxZ, double y) {
        if (y != 0) {
            buffer.pos(minX, y, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(maxX, y, minZ).color(color[0], color[1], color[2], color[3]).endVertex();

            buffer.pos(maxX, y, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(maxX, y, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();

            buffer.pos(maxX, y, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(minX, y, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();

            buffer.pos(minX, y, maxZ).color(color[0], color[1], color[2], color[3]).endVertex();
            buffer.pos(minX, y, minZ).color(color[0], color[1], color[2], color[3]).endVertex();
        }
    }
}
