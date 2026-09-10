package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.util.math.Vec3d;

final class FreecamMovementMath {
    private FreecamMovementMath() { }

    static Vec3d motion(float yawDegrees, float pitchDegrees, float forward, float strafe,
                        float vertical, double speed) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        Vec3d direction = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).scale(forward)
                .add(new Vec3d(Math.cos(yaw), 0, Math.sin(yaw)).scale(strafe))
                .add(new Vec3d(0, vertical, 0));
        double length = Math.sqrt(direction.lengthSquared());
        if (length > 1.0D) {
            direction = direction.scale(1.0D / length);
        }
        return direction.scale(speed);
    }
}
