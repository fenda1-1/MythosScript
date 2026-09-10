package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

public final class ParkourJumpCandidate {

    private final ParkourJumpType type;
    private final BetterBlockPos src;
    private final BetterBlockPos dest;
    private final EnumFacing forward;
    private final EnumFacing lateral;
    private final int forwardDistance;
    private final boolean ascend;
    private final boolean requiresSprint;
    private final boolean narrowLanding;
    private final boolean edgeLaunch;
    private final BetterBlockPos[] segmentLandings;
    private final double cost;
    private final double landingRecoveryDistance;
    private final int chainPotential;
    private final ParkourLaunchWindow launchWindow;
    private final ParkourLandingWindow landingWindow;
    private final Vec3d[] routePoints;

    public ParkourJumpCandidate(ParkourJumpType type, BetterBlockPos src, BetterBlockPos dest, EnumFacing forward,
            EnumFacing lateral,
            int forwardDistance, boolean ascend, boolean requiresSprint, boolean narrowLanding, boolean edgeLaunch,
            BetterBlockPos[] segmentLandings, double cost, double landingRecoveryDistance, int chainPotential,
            ParkourLaunchWindow launchWindow, ParkourLandingWindow landingWindow, Vec3d[] routePoints) {
        this.type = type;
        this.src = src;
        this.dest = dest;
        this.forward = forward;
        this.lateral = lateral;
        this.forwardDistance = forwardDistance;
        this.ascend = ascend;
        this.requiresSprint = requiresSprint;
        this.narrowLanding = narrowLanding;
        this.edgeLaunch = edgeLaunch;
        this.segmentLandings = segmentLandings == null ? new BetterBlockPos[] { dest } : segmentLandings.clone();
        this.cost = cost;
        this.landingRecoveryDistance = landingRecoveryDistance;
        this.chainPotential = chainPotential;
        this.launchWindow = launchWindow;
        this.landingWindow = landingWindow;
        this.routePoints = routePoints == null ? new Vec3d[0] : routePoints.clone();
    }

    public ParkourJumpType getType() {
        return type;
    }

    public BetterBlockPos getSrc() {
        return src;
    }

    public BetterBlockPos getDest() {
        return dest;
    }

    public EnumFacing getForward() {
        return forward;
    }

    public EnumFacing getLateral() {
        return lateral;
    }

    public int getForwardDistance() {
        return forwardDistance;
    }

    public boolean isAscend() {
        return ascend;
    }

    public boolean requiresSprint() {
        return requiresSprint;
    }

    public boolean isNarrowLanding() {
        return narrowLanding;
    }

    public boolean isEdgeLaunch() {
        return edgeLaunch;
    }

    public BetterBlockPos[] getSegmentLandings() {
        return segmentLandings.clone();
    }

    public int getChainLength() {
        return segmentLandings.length;
    }

    public double getCost() {
        return cost;
    }

    public double getLandingRecoveryDistance() {
        return landingRecoveryDistance;
    }

    public int getChainPotential() {
        return chainPotential;
    }

    public ParkourLaunchWindow getLaunchWindow() {
        return launchWindow;
    }

    public ParkourLandingWindow getLandingWindow() {
        return landingWindow;
    }

    public Vec3d[] getRoutePoints() {
        return routePoints.clone();
    }

    public boolean matches(ParkourJumpCandidate other) {
        return other != null
                && type == other.type
                && src.equals(other.src)
                && dest.equals(other.dest)
                && forward == other.forward
                && lateral == other.lateral
                && forwardDistance == other.forwardDistance
                && ascend == other.ascend
                && requiresSprint == other.requiresSprint
                && narrowLanding == other.narrowLanding
                && edgeLaunch == other.edgeLaunch
                && chainPotential == other.chainPotential
                && haveSameLandings(other)
                && Math.abs(landingRecoveryDistance - other.landingRecoveryDistance) < 1.0E-4D;
    }

    private boolean haveSameLandings(ParkourJumpCandidate other) {
        if (segmentLandings.length != other.segmentLandings.length) {
            return false;
        }
        for (int i = 0; i < segmentLandings.length; i++) {
            if (!segmentLandings[i].equals(other.segmentLandings[i])) {
                return false;
            }
        }
        return true;
    }
}
