package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

public final class ParkourLandingWindow {

    private final double maxFlatDistanceSq;
    private final double maxRouteDistanceSq;
    private final double maxOvershootDistance;

    public ParkourLandingWindow(double maxFlatDistanceSq, double maxRouteDistanceSq, double maxOvershootDistance) {
        this.maxFlatDistanceSq = maxFlatDistanceSq;
        this.maxRouteDistanceSq = maxRouteDistanceSq;
        this.maxOvershootDistance = maxOvershootDistance;
    }

    public double getMaxFlatDistanceSq() {
        return maxFlatDistanceSq;
    }

    public double getMaxRouteDistanceSq() {
        return maxRouteDistanceSq;
    }

    public double getMaxOvershootDistance() {
        return maxOvershootDistance;
    }
}
