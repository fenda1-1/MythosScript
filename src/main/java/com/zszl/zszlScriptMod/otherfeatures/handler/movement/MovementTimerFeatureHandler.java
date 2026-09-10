package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

final class MovementTimerFeatureHandler {

    private MovementTimerFeatureHandler() {
    }

    static void apply(MovementFeatureManager manager) {
        if (!MovementFeatureManager.isEnabled("timer_accel") || SpeedHandler.isTimerManagedBySpeed()) {
            reset(manager, false);
            return;
        }
        float timerSpeed = MovementFeatureManager.getConfiguredValue("timer_accel", MovementFeatureManager.DEFAULT_TIMER_SPEED);
        SpeedHandler.applyTimerBoost(timerSpeed);
        manager.externalTimerApplied = true;
        manager.lastTimerSpeed = timerSpeed;
    }

    static void reset(MovementFeatureManager manager, boolean force) {
        if (!manager.externalTimerApplied && !force) {
            return;
        }
        if (!SpeedHandler.isTimerManagedBySpeed()) {
            SpeedHandler.restoreVanillaTimer();
        }
        manager.externalTimerApplied = false;
        manager.lastTimerSpeed = MovementFeatureManager.DEFAULT_TIMER_SPEED;
    }
}
