package com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement;

public enum ParkourProfile {
    STABLE(0.65D, 1.40D, 1.85D, 0.16D, 0.30D, 0.85D, 0.55D, 0.48D, 0.42D, 0.18D),
    BALANCED(0.35D, 0.90D, 1.20D, 0.20D, 0.34D, 0.55D, 0.36D, 0.30D, 0.26D, 0.12D),
    EXTREME(0.10D, 0.45D, 0.65D, 0.24D, 0.40D, 0.28D, 0.20D, 0.18D, 0.12D, 0.08D);

    private final double sprintPenalty;
    private final double angledPenalty;
    private final double narrowLandingPenalty;
    private final double launchWindowSlack;
    private final double landingRadius;
    private final double edgeLaunchPenalty;
    private final double launchPrecisionPenalty;
    private final double recoveryPenalty;
    private final double chainContinuationBonus;
    private final double chainComplexityPenalty;

    ParkourProfile(double sprintPenalty, double angledPenalty, double narrowLandingPenalty,
            double launchWindowSlack, double landingRadius, double edgeLaunchPenalty,
            double launchPrecisionPenalty, double recoveryPenalty, double chainContinuationBonus,
            double chainComplexityPenalty) {
        this.sprintPenalty = sprintPenalty;
        this.angledPenalty = angledPenalty;
        this.narrowLandingPenalty = narrowLandingPenalty;
        this.launchWindowSlack = launchWindowSlack;
        this.landingRadius = landingRadius;
        this.edgeLaunchPenalty = edgeLaunchPenalty;
        this.launchPrecisionPenalty = launchPrecisionPenalty;
        this.recoveryPenalty = recoveryPenalty;
        this.chainContinuationBonus = chainContinuationBonus;
        this.chainComplexityPenalty = chainComplexityPenalty;
    }

    public double getSprintPenalty() {
        return sprintPenalty;
    }

    public double getAngledPenalty() {
        return angledPenalty;
    }

    public double getNarrowLandingPenalty() {
        return narrowLandingPenalty;
    }

    public double getLaunchWindowSlack() {
        return launchWindowSlack;
    }

    public double getLandingRadius() {
        return landingRadius;
    }

    public double getEdgeLaunchPenalty() {
        return edgeLaunchPenalty;
    }

    public double getLaunchPrecisionPenalty() {
        return launchPrecisionPenalty;
    }

    public double getRecoveryPenalty() {
        return recoveryPenalty;
    }

    public double getChainContinuationBonus() {
        return chainContinuationBonus;
    }

    public double getChainComplexityPenalty() {
        return chainComplexityPenalty;
    }

    public static ParkourProfile orDefault(ParkourProfile profile) {
        return profile == null ? STABLE : profile;
    }
}
