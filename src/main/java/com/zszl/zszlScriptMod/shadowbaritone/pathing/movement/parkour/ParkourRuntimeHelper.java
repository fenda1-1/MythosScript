package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.path.IPathExecutor;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;

public final class ParkourRuntimeHelper {

    private ParkourRuntimeHelper() {
    }

    public static MovementParkour getActiveParkourMovement(IBaritone baritone) {
        if (baritone == null || baritone.getPathingBehavior() == null) {
            return null;
        }
        IPathExecutor currentExecutor = baritone.getPathingBehavior().getCurrent();
        if (currentExecutor == null) {
            return null;
        }
        IPath path = currentExecutor.getPath();
        if (path == null) {
            return null;
        }
        int position = currentExecutor.getPosition();
        if (position < 0 || position >= path.movements().size()) {
            return null;
        }
        IMovement currentMovement = path.movements().get(position);
        return currentMovement instanceof MovementParkour ? (MovementParkour) currentMovement : null;
    }

    public static boolean isPrecisionCriticalParkourPhase(IBaritone baritone) {
        MovementParkour parkour = getActiveParkourMovement(baritone);
        return parkour != null && parkour.isPrecisionCriticalPhase();
    }
}
