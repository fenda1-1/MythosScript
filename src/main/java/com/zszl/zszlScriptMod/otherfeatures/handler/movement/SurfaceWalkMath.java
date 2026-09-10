package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;

/** Tangent-frame math, independent of the client and world. */
final class SurfaceWalkMath {
    private SurfaceWalkMath() { }

    static Vec3d cameraHeading(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    static Vec3d ceilingInput(float yaw, float forward, float strafe) {
        // The camera stays upright, even when the supporting normal points down.
        return input(cameraHeading(yaw), new Vec3d(0, 1, 0), forward, strafe);
    }

    static AxisAlignedBB raisedCeilingProbe(AxisAlignedBB body, double contact) {
        return new AxisAlignedBB(body.minX + 0.001D, body.maxY, body.minZ + 0.001D,
                body.maxX - 0.001D, body.maxY + 1.0D + contact, body.maxZ - 0.001D);
    }

    static Vec3d rotate(Vec3d value, Vec3d axis, double angle) {
        double cos = Math.cos(angle);
        return value.scale(cos).add(axis.crossProduct(value).scale(Math.sin(angle)))
                .add(axis.scale(axis.dotProduct(value) * (1.0D - cos)));
    }

    static Vec3d transport(Vec3d heading, Vec3d oldNormal, Vec3d newNormal) {
        double dot = oldNormal.dotProduct(newNormal);
        if (dot > 0.999D) {
            return heading;
        }
        if (dot < -0.999D) {
            return heading; // Floor-to-ceiling catch: keep the horizontal heading.
        }
        return rotate(heading, oldNormal.crossProduct(newNormal).normalize(), Math.acos(dot)).normalize();
    }

    static Vec3d input(Vec3d heading, Vec3d normal, float forward, float strafe) {
        Vec3d result = heading.scale(forward).add(normal.crossProduct(heading).scale(strafe));
        return result.lengthSquared() > 1.0D ? result.normalize() : result;
    }
}
