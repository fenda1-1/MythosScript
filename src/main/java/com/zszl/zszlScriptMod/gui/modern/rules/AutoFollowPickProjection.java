package com.zszl.zszlScriptMod.gui.modern.rules;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.util.glu.GLU;

/** Converts the rendered viewport centre into a world-space segment, without entity yaw interpolation. */
final class AutoFollowPickProjection {
    static double[] ray(FloatBuffer model, FloatBuffer projection, IntBuffer viewport,
            FloatBuffer near, FloatBuffer far, double x, double y, double z) {
        float cx = viewport.get(0) + viewport.get(2) * 0.5F;
        float cy = viewport.get(1) + viewport.get(3) * 0.5F;
        if (!GLU.gluUnProject(cx, cy, 0, model, projection, viewport, near)
                || !GLU.gluUnProject(cx, cy, 1, model, projection, viewport, far)) return null;
        double dx = far.get(0) - near.get(0), dy = far.get(1) - near.get(1), dz = far.get(2) - near.get(2);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1.0e-8) return null;
        double sx = near.get(0) + x, sy = near.get(1) + y, sz = near.get(2) + z;
        return new double[] { sx, sy, sz, sx + dx / length * 256, sy + dy / length * 256, sz + dz / length * 256 };
    }
}
