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
 * along with Baritone. If not, see https://www.gnu.org/licenses/.
 */

package com.zszl.zszlScriptMod.shadowbaritone.utils.pathing;

import com.zszl.zszlScriptMod.config.HumanLikeMovementConfig;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.IPlayerContext;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.interfaces.IGoalRenderPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Favoring {

    private static final int MAX_ROUTE_ANCHORS = 6;

    private final Long2DoubleOpenHashMap favorings;
    private final boolean enableHumanLikeRouteFavoring;
    private final long humanLikeRouteSeed;
    private final double humanLikeRouteNoiseStrength;
    private final int humanLikeRouteNoiseScale;

    private final boolean routeAxisEnabled;
    private final double routeStartX;
    private final double routeStartZ;
    private final double routeDeltaX;
    private final double routeDeltaZ;
    private final double routeEndX;
    private final double routeEndZ;
    private final double routeLengthSq;
    private final int routeSidePreference;

    private final int routeAnchorCount;
    private final double[] routeAnchorX;
    private final double[] routeAnchorZ;
    private final double[] routeAnchorRadius;
    private final double[] routeAnchorInfluenceRadius;
    private final double[] routeAnchorStrength;
    private final double[] routeAnchorAlong;

    public Favoring(IPlayerContext ctx, IPath previous, CalculationContext context) {
        this((BetterBlockPos) null, null, previous, context);
        for (Avoidance avoid : Avoidance.create(ctx)) {
            avoid.applySpherical(favorings);
        }
        Helper.HELPER.logDebug("Favoring size: " + favorings.size());
    }

    public Favoring(IPlayerContext ctx, BetterBlockPos start, Goal goal, IPath previous, CalculationContext context) {
        this(start, goal, previous, context);
        for (Avoidance avoid : Avoidance.create(ctx)) {
            avoid.applySpherical(favorings);
        }
        Helper.HELPER.logDebug("Favoring size: " + favorings.size());
    }

    public Favoring(IPath previous, CalculationContext context) {
        this((BetterBlockPos) null, null, previous, context);
    }

    public Favoring(BetterBlockPos start, Goal goal, IPath previous, CalculationContext context) {
        favorings = new Long2DoubleOpenHashMap();
        favorings.defaultReturnValue(1.0D);
        double coeff = context.backtrackCostFavoringCoefficient;
        if (coeff != 1D && previous != null) {
            previous.positions().forEach(pos -> favorings.put(BetterBlockPos.longHash(pos), coeff));
        }

        HumanLikeMovementConfig config = HumanLikeMovementConfig.INSTANCE;
        boolean enabled = config != null && config.enabled && !context.parkourMode;
        this.enableHumanLikeRouteFavoring = enabled;
        this.humanLikeRouteSeed = ThreadLocalRandom.current().nextLong();
        this.humanLikeRouteNoiseStrength = enabled ? Math.max(0.0D, config.routeNoiseStrength) : 0.0D;
        this.humanLikeRouteNoiseScale = enabled ? Math.max(2, config.routeNoiseScale) : 5;

        boolean axisEnabled = false;
        double startX = 0.0D;
        double startZ = 0.0D;
        double deltaX = 0.0D;
        double deltaZ = 0.0D;
        double endX = 0.0D;
        double endZ = 0.0D;
        double lengthSq = 1.0D;
        int sidePreference = 0;

        int anchorCount = 0;
        double[] anchorX = new double[MAX_ROUTE_ANCHORS];
        double[] anchorZ = new double[MAX_ROUTE_ANCHORS];
        double[] anchorRadius = new double[MAX_ROUTE_ANCHORS];
        double[] anchorInfluenceRadius = new double[MAX_ROUTE_ANCHORS];
        double[] anchorStrength = new double[MAX_ROUTE_ANCHORS];
        double[] anchorAlong = new double[MAX_ROUTE_ANCHORS];

        HumanLikeRouteTemplateManager.clearLastPlannedRoute();

        if (enabled && humanLikeRouteNoiseStrength > 0.0001D && start != null && goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            if (goalPos != null) {
                double sx = start.x + 0.5D;
                double sz = start.z + 0.5D;
                double ex = goalPos.getX() + 0.5D;
                double ez = goalPos.getZ() + 0.5D;
                double dx = ex - sx;
                double dz = ez - sz;
                double lenSq = dx * dx + dz * dz;
                if (lenSq >= 36.0D) {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    double len = Math.sqrt(lenSq);

                    axisEnabled = true;
                    startX = sx;
                    startZ = sz;
                    deltaX = dx;
                    deltaZ = dz;
                    endX = ex;
                    endZ = ez;
                    lengthSq = lenSq;
                    sidePreference = random.nextBoolean() ? 1 : -1;

                    Optional<HumanLikeRouteTemplateManager.PlannedRoute> plannedRoute = HumanLikeRouteTemplateManager
                            .buildRoute(start, goalPos);
                    boolean templateAnchorsApplied = false;
                    if (plannedRoute.isPresent()) {
                        List<HumanLikeRouteTemplateManager.PlannedAnchor> sortedAnchors = new ArrayList<>(
                                plannedRoute.get().anchors);
                        sortedAnchors.sort(Comparator.comparingDouble(plannedAnchor -> clamp(
                                ((plannedAnchor.x - sx) * dx + (plannedAnchor.z - sz) * dz) / lenSq,
                                0.0D,
                                1.0D)));

                        double previousRouteX = sx;
                        double previousRouteY = start.y;
                        double previousRouteZ = sz;

                        for (HumanLikeRouteTemplateManager.PlannedAnchor plannedAnchor : sortedAnchors) {
                            if (plannedAnchor == null || anchorCount >= MAX_ROUTE_ANCHORS) {
                                continue;
                            }

                            double candidateAnchorX = plannedAnchor.x;
                            double candidateAnchorZ = plannedAnchor.z;
                            int candidateAnchorY = (int) Math.round(plannedAnchor.y);

                            double rawAlong =
                                    ((candidateAnchorX - sx) * dx + (candidateAnchorZ - sz) * dz) / lenSq;
                            if (rawAlong < -0.12D || rawAlong > 1.12D) {
                                continue;
                            }

                            if (!plannedAnchor.required) {
                                double keepChance = 0.93D - Math.max(0, sortedAnchors.size() - 3) * 0.06D;
                                if (random.nextDouble() > clamp(keepChance, 0.55D, 0.93D)) {
                                    continue;
                                }
                            }

                            double minSpacing = Math.max(1.0D, plannedAnchor.radius * 0.6D);
                            double spacingDx = candidateAnchorX - previousRouteX;
                            double spacingDz = candidateAnchorZ - previousRouteZ;
                            if (!plannedAnchor.required
                                    && spacingDx * spacingDx + spacingDz * spacingDz < minSpacing * minSpacing) {
                                continue;
                            }

                            double remainingDirectDistance = Math.hypot(ex - previousRouteX, ez - previousRouteZ);
                            double candidateLegDistance = Math.hypot(candidateAnchorX - previousRouteX,
                                    candidateAnchorZ - previousRouteZ);
                            double candidateToGoalDistance = Math.hypot(ex - candidateAnchorX, ez - candidateAnchorZ);
                            double maxDetourScale = plannedAnchor.required ? 1.9D : 1.45D;
                            double allowedDetourDistance = remainingDirectDistance * maxDetourScale
                                    + Math.max(1.5D, plannedAnchor.radius * 1.35D);
                            if (candidateLegDistance + candidateToGoalDistance > allowedDetourDistance) {
                                continue;
                            }

                            double walkability = sampleAnchorWalkability(
                                    context,
                                    candidateAnchorX,
                                    candidateAnchorY,
                                    candidateAnchorZ
                            );
                            double corridorClearance = Math.min(
                                    sampleAnchorCorridorClearance(
                                            context,
                                            previousRouteX,
                                            (int) Math.round(previousRouteY),
                                            previousRouteZ,
                                            candidateAnchorX,
                                            candidateAnchorY,
                                            candidateAnchorZ),
                                    sampleAnchorCorridorClearance(
                                            context,
                                            candidateAnchorX,
                                            candidateAnchorY,
                                            candidateAnchorZ,
                                            ex,
                                            goalPos.getY(),
                                            ez));
                            double anchorQuality = walkability * corridorClearance;
                            if (anchorQuality < 0.18D) {
                                continue;
                            }

                            double along = clamp(rawAlong, 0.0D, 1.0D);
                            double preferenceBoost = 1.0D;
                            if (plannedAnchor.preferEarly) {
                                preferenceBoost += (1.0D - along) * 0.18D;
                            }
                            if (plannedAnchor.preferLate) {
                                preferenceBoost += along * 0.18D;
                            }
                            if (plannedAnchor.required) {
                                preferenceBoost += 0.18D;
                            }

                            double plannedRadius = Math.max(0.75D, plannedAnchor.radius);
                            double influenceVariance = 0.94D + random.nextDouble() * 0.14D;
                            double strengthVariance = 0.93D + random.nextDouble() * 0.14D;

                            anchorX[anchorCount] = candidateAnchorX;
                            anchorZ[anchorCount] = candidateAnchorZ;
                            anchorRadius[anchorCount] = plannedRadius;
                            anchorInfluenceRadius[anchorCount] = Math.max(
                                    2.25D,
                                    plannedRadius * 1.9D + humanLikeRouteNoiseScale * 0.75D) * influenceVariance;
                            anchorStrength[anchorCount] = clamp(
                                    (0.72D + Math.min(plannedAnchor.weight, 2.5D) * 0.18D)
                                            * preferenceBoost
                                            * anchorQuality
                                            * strengthVariance,
                                    0.48D,
                                    1.22D);
                            anchorAlong[anchorCount] = along;
                            anchorCount++;

                            templateAnchorsApplied = true;
                            previousRouteX = candidateAnchorX;
                            previousRouteY = candidateAnchorY;
                            previousRouteZ = candidateAnchorZ;
                        }
                    }

                    double configuredAnchorRadius = Math.max(0.0D, config.routeAnchorRadius);
                    if (!templateAnchorsApplied && configuredAnchorRadius > 0.05D) {
                        int candidateCount = lenSq >= 196.0D ? 3 : 2;
                        int desiredCount = lenSq >= 484.0D && random.nextFloat() < 0.55F ? 2 : 1;

                        for (int i = 0; i < candidateCount && anchorCount < desiredCount; i++) {
                            double minAlong = 0.22D + i * 0.18D;
                            double maxAlong = Math.min(0.78D, minAlong + 0.22D);
                            double along = minAlong + random.nextDouble() * Math.max(0.04D, maxAlong - minAlong);

                            double baseAnchorX = sx + dx * along;
                            double baseAnchorZ = sz + dz * along;

                            double normalX = -dz / len;
                            double normalZ = dx / len;

                            int branchSide;
                            if (desiredCount >= 2) {
                                branchSide = i % 2 == 0 ? sidePreference : -sidePreference;
                            } else {
                                branchSide = sidePreference;
                            }

                            double branchScale = 0.45D + random.nextDouble() * 0.55D;
                            if (candidateCount >= 3 && i == 1 && desiredCount == 1 && random.nextFloat() < 0.4F) {
                                branchScale *= 0.65D;
                            }

                            double sideOffset = branchSide * configuredAnchorRadius * branchScale;
                            double noiseOffset = (random.nextDouble() * 2.0D - 1.0D) * configuredAnchorRadius * 0.28D;
                            double totalOffset = clamp(sideOffset + noiseOffset,
                                    -configuredAnchorRadius, configuredAnchorRadius);

                            double candidateAnchorX = baseAnchorX + normalX * totalOffset;
                            double candidateAnchorZ = baseAnchorZ + normalZ * totalOffset;
                            int candidateAnchorY = (int) Math.round(start.y + (goalPos.getY() - start.y) * along);

                            double walkability = sampleAnchorWalkability(
                                    context,
                                    candidateAnchorX,
                                    candidateAnchorY,
                                    candidateAnchorZ
                            );
                            double corridorClearance = Math.min(
                                    sampleAnchorCorridorClearance(
                                            context,
                                            sx,
                                            start.y,
                                            sz,
                                            candidateAnchorX,
                                            candidateAnchorY,
                                            candidateAnchorZ),
                                    sampleAnchorCorridorClearance(
                                            context,
                                            candidateAnchorX,
                                            candidateAnchorY,
                                            candidateAnchorZ,
                                            ex,
                                            goalPos.getY(),
                                            ez));
                            double anchorQuality = walkability * corridorClearance;
                            if (anchorQuality < 0.18D) {
                                continue;
                            }

                            anchorX[anchorCount] = candidateAnchorX;
                            anchorZ[anchorCount] = candidateAnchorZ;
                            anchorRadius[anchorCount] = configuredAnchorRadius;
                            anchorInfluenceRadius[anchorCount] = Math.max(2.0D,
                                    configuredAnchorRadius * 1.8D + humanLikeRouteNoiseScale * 0.65D);
                            anchorStrength[anchorCount] = clamp(
                                    (0.78D + (1.0D - along) * 0.22D + random.nextDouble() * 0.18D) * anchorQuality,
                                    0.45D, 1.15D);
                            anchorAlong[anchorCount] = along;
                            anchorCount++;
                        }
                    }
                }
            }
        }

        this.routeAxisEnabled = axisEnabled;
        this.routeStartX = startX;
        this.routeStartZ = startZ;
        this.routeDeltaX = deltaX;
        this.routeDeltaZ = deltaZ;
        this.routeEndX = endX;
        this.routeEndZ = endZ;
        this.routeLengthSq = lengthSq;
        this.routeSidePreference = sidePreference;

        this.routeAnchorCount = anchorCount;
        this.routeAnchorX = anchorX;
        this.routeAnchorZ = anchorZ;
        this.routeAnchorRadius = anchorRadius;
        this.routeAnchorInfluenceRadius = anchorInfluenceRadius;
        this.routeAnchorStrength = anchorStrength;
        this.routeAnchorAlong = anchorAlong;
    }

    public boolean isEmpty() {
        return favorings.isEmpty() && !enableHumanLikeRouteFavoring;
    }

    public double calculate(long hash) {
        return favorings.get(hash);
    }

    public double calculate(int x, int y, int z, long hash) {
        double result = favorings.get(hash);
        if (!enableHumanLikeRouteFavoring || humanLikeRouteNoiseStrength <= 0.0001D) {
            return result;
        }

        double convergenceFactor = routeAxisEnabled ? sampleRouteConvergenceFactor(x, z) : 1.0D;
        double noiseMultiplier = 1.0D + sampleRouteNoise(x, y, z) * humanLikeRouteNoiseStrength * convergenceFactor;
        double axisMultiplier = routeAxisEnabled ? sampleRouteAxisMultiplier(x, z, convergenceFactor) : 1.0D;
        double anchorMultiplier = sampleRouteAnchorsMultiplier(x, z, convergenceFactor);
        return result * clamp(noiseMultiplier * axisMultiplier * anchorMultiplier, 0.76D, 1.22D);
    }

    private double sampleRouteNoise(int x, int y, int z) {
        int cellX = floorDiv(x, humanLikeRouteNoiseScale);
        int cellZ = floorDiv(z, humanLikeRouteNoiseScale);

        double center = hashToUnit(cellX, cellZ, 0);
        double north = hashToUnit(cellX, cellZ - 1, 1);
        double south = hashToUnit(cellX, cellZ + 1, 2);
        double east = hashToUnit(cellX + 1, cellZ, 3);
        double west = hashToUnit(cellX - 1, cellZ, 4);

        double blended = center * 0.50D
                + north * 0.125D
                + south * 0.125D
                + east * 0.125D
                + west * 0.125D;

        double yBias = ((y & 3) - 1.5D) * 0.015D;
        return clamp((blended * 2.0D - 1.0D) + yBias, -1.0D, 1.0D);
    }

    private double sampleRouteAxisMultiplier(int x, int z, double convergenceFactor) {
        double px = x + 0.5D - routeStartX;
        double pz = z + 0.5D - routeStartZ;
        double projection = (px * routeDeltaX + pz * routeDeltaZ) / routeLengthSq;
        double clampedProjection = clamp(projection, 0.0D, 1.0D);

        double distanceToLine = (routeDeltaX * pz - routeDeltaZ * px) / Math.sqrt(routeLengthSq);
        double sideScore = clamp(distanceToLine / Math.max(1.0D, humanLikeRouteNoiseScale * 1.35D), -1.0D, 1.0D);

        double middleWeight = 1.0D - Math.abs(clampedProjection * 2.0D - 1.0D);
        double preferred = routeSidePreference * sideScore * middleWeight;
        return clamp(1.0D - preferred * humanLikeRouteNoiseStrength * 0.55D * convergenceFactor, 0.84D, 1.16D);
    }

    private double sampleRouteAnchorsMultiplier(int x, int z, double convergenceFactor) {
        if (routeAnchorCount <= 0 || convergenceFactor <= 0.001D) {
            return 1.0D;
        }

        double px = x + 0.5D - routeStartX;
        double pz = z + 0.5D - routeStartZ;
        double projection = clamp((px * routeDeltaX + pz * routeDeltaZ) / routeLengthSq, 0.0D, 1.0D);

        double multiplier = 1.0D;
        for (int i = 0; i < routeAnchorCount; i++) {
            multiplier *= sampleRouteAnchorMultiplier(x, z, i, projection, convergenceFactor);
        }
        return clamp(multiplier, 0.86D, 1.10D);
    }

    private double sampleRouteAnchorMultiplier(int x, int z, int index, double projection, double convergenceFactor) {
        double alongDelta = Math.abs(projection - routeAnchorAlong[index]);
        double alongWindow = routeAnchorCount >= 5 ? 0.16D : routeAnchorCount >= 3 ? 0.21D : routeAnchorCount >= 2 ? 0.24D : 0.34D;
        if (alongDelta >= alongWindow) {
            return 1.0D;
        }

        double alongWeight = 1.0D - alongDelta / alongWindow;
        alongWeight = alongWeight * alongWeight * (3.0D - 2.0D * alongWeight);

        double dx = x + 0.5D - routeAnchorX[index];
        double dz = z + 0.5D - routeAnchorZ[index];
        double distanceSq = dx * dx + dz * dz;
        double influenceRadius = routeAnchorInfluenceRadius[index];
        double influenceRadiusSq = influenceRadius * influenceRadius;
        if (distanceSq >= influenceRadiusSq) {
            return 1.0D;
        }

        double normalized = Math.sqrt(distanceSq) / influenceRadius;
        double closeness = 1.0D - normalized;
        double falloff = closeness * closeness * (3.0D - 2.0D * closeness);
        double radiusFactor = clamp(routeAnchorRadius[index] / Math.max(1.0D, influenceRadius), 0.0D, 0.45D);
        double densityCompensation = 1.0D / Math.sqrt(Math.max(1, routeAnchorCount));
        double attraction = humanLikeRouteNoiseStrength
                * (0.42D + radiusFactor + (routeAnchorStrength[index] - 0.7D) * 0.55D)
                * falloff
                * alongWeight
                * convergenceFactor
                * densityCompensation;
        return clamp(1.0D - attraction, 0.86D, 1.08D);
    }

    private double sampleRouteConvergenceFactor(int x, int z) {
        double sampleX = x + 0.5D;
        double sampleZ = z + 0.5D;

        double px = sampleX - routeStartX;
        double pz = sampleZ - routeStartZ;
        double projection = (px * routeDeltaX + pz * routeDeltaZ) / routeLengthSq;
        double clampedProjection = clamp(projection, 0.0D, 1.0D);
        double remainingProjection = 1.0D - clampedProjection;
        double projectionFactor = clamp(remainingProjection / 0.28D, 0.0D, 1.0D);

        double goalDx = sampleX - routeEndX;
        double goalDz = sampleZ - routeEndZ;
        double goalDistance = Math.sqrt(goalDx * goalDx + goalDz * goalDz);
        double goalDistanceFactor = clamp((goalDistance - 1.25D) / 4.75D, 0.0D, 1.0D);

        return Math.min(projectionFactor, goalDistanceFactor);
    }

    private double sampleAnchorWalkability(CalculationContext context, double anchorX, int anchorY, double anchorZ) {
        int blockX = (int) Math.floor(anchorX);
        int blockZ = (int) Math.floor(anchorZ);

        double score = 0.0D;
        int samples = 0;
        double dangerPenalty = 0.0D;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = blockX + dx;
                int z = blockZ + dz;
                if (!context.isLoaded(x, z)) {
                    dangerPenalty += 0.18D;
                    samples++;
                    continue;
                }
                if (isAnchorStandable(context, x, anchorY, z)) {
                    score += dx == 0 && dz == 0 ? 1.35D : 0.7D;
                }
                dangerPenalty += sampleAnchorDangerPenalty(context, x, anchorY, z);
                samples++;
            }
        }

        double openness = score / (samples * 1.35D);
        double safety = clamp(1.0D - dangerPenalty / Math.max(1.0D, samples), 0.15D, 1.0D);
        return clamp(openness * safety, 0.0D, 1.0D);
    }

    private boolean isAnchorStandable(CalculationContext context, int x, int y, int z) {
        return MovementHelper.canWalkOn(context, x, y - 1, z)
                && MovementHelper.canWalkThrough(context, x, y, z)
                && MovementHelper.canWalkThrough(context, x, y + 1, z);
    }

    private double sampleAnchorDangerPenalty(CalculationContext context, int x, int y, int z) {
        double penalty = 0.0D;
        penalty += sampleHazardPenalty(context.getBlock(x, y - 1, z), 0.55D);
        penalty += sampleHazardPenalty(context.getBlock(x, y, z), 0.75D);
        penalty += sampleHazardPenalty(context.getBlock(x, y + 1, z), 0.35D);

        penalty += sampleHazardPenalty(context.getBlock(x + 1, y, z), 0.18D);
        penalty += sampleHazardPenalty(context.getBlock(x - 1, y, z), 0.18D);
        penalty += sampleHazardPenalty(context.getBlock(x, y, z + 1), 0.18D);
        penalty += sampleHazardPenalty(context.getBlock(x, y, z - 1), 0.18D);

        return penalty;
    }

    private double sampleHazardPenalty(Block block, double weight) {
        return MovementHelper.avoidWalkingInto(block) ? weight : 0.0D;
    }

    private double sampleAnchorCorridorClearance(CalculationContext context, double fromX, int fromY, double fromZ,
            double toX, int toY, double toZ) {
        int steps = 4;
        double score = 0.0D;
        for (int i = 1; i <= steps; i++) {
            double t = i / (steps + 1.0D);
            double sampleX = lerp(fromX, toX, t);
            double sampleZ = lerp(fromZ, toZ, t);
            int sampleY = (int) Math.round(lerp(fromY, toY, t));

            double walkability = sampleAnchorWalkability(context, sampleX, sampleY, sampleZ);
            if (walkability >= 0.32D) {
                score += walkability;
            }
        }
        return clamp(score / steps, 0.0D, 1.0D);
    }

    private double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private double hashToUnit(int x, int z, int salt) {
        long value = humanLikeRouteSeed;
        value ^= 0x9E3779B97F4A7C15L * (x + 0x632BE59BD9B4E019L);
        value ^= 0xC2B2AE3D27D4EB4FL * (z + 0x85157AF5L);
        value ^= 0x165667B19E3779F9L * salt;
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);

        long mantissa = value & ((1L << 53) - 1);
        return mantissa / (double) (1L << 53);
    }

    private int floorDiv(int value, int scale) {
        int div = value / scale;
        int rem = value % scale;
        if (rem != 0 && ((value ^ scale) < 0)) {
            div--;
        }
        return div;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
