package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

public final class ParkourLaunchWindow {

    private final double idealProgress;
    private final double minProgress;
    private final double maxProgress;
    private final double maxLateralError;

    public ParkourLaunchWindow(double idealProgress, double minProgress, double maxProgress, double maxLateralError) {
        this.idealProgress = idealProgress;
        this.minProgress = minProgress;
        this.maxProgress = maxProgress;
        this.maxLateralError = maxLateralError;
    }

    public double getIdealProgress() {
        return idealProgress;
    }

    public double getMinProgress() {
        return minProgress;
    }

    public double getMaxProgress() {
        return maxProgress;
    }

    public double getMaxLateralError() {
        return maxLateralError;
    }
}
