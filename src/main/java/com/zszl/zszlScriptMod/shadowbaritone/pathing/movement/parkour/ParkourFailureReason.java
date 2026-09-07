package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

public enum ParkourFailureReason {
    NONE,
    COLLISION_REJECTED,
    ALIGNMENT_FAILED,
    LATE_JUMP,
    OVERSHOT_LANDING,
    MISSED_LANDING,
    STUCK_IN_EXECUTION,
    REPLAN_TRIGGERED
}
