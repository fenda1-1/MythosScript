package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

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
        MovementFeatureManager.RuntimeState rt = MovementFeatureManager.rt();
        rt.externalTimerApplied = true;
        rt.lastTimerSpeed = timerSpeed;
    }

    static void reset(MovementFeatureManager manager, boolean force) {
        MovementFeatureManager.RuntimeState rt = MovementFeatureManager.rt();
        if (!rt.externalTimerApplied && !force) {
            return;
        }
        if (!SpeedHandler.isTimerManagedBySpeed()) {
            SpeedHandler.restoreVanillaTimer();
        }
        rt.externalTimerApplied = false;
        rt.lastTimerSpeed = MovementFeatureManager.DEFAULT_TIMER_SPEED;
    }
}
