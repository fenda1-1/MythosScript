package com.zszl.zszlScriptMod.gui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Mapping from an axis-aligned GUI plane to actual framebuffer pixels. */
final class GuiTextPixelGrid {
    final double scaleX;
    final double scaleY;
    final double originX;
    final double originY;

    private GuiTextPixelGrid(double scaleX, double scaleY, double originX, double originY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.originX = originX;
        this.originY = originY;
    }

    static GuiTextPixelGrid fromMatrices(FloatBuffer model, FloatBuffer projection, IntBuffer viewport) {
        double xx = product(projection, model, 0, 0);
        double xy = product(projection, model, 0, 1);
        double yx = product(projection, model, 1, 0);
        double yy = product(projection, model, 1, 1);
        double w = product(projection, model, 3, 3);
        // Rotated/perspective text needs the original renderer, not pixel snapping.
        if (Math.abs(xy) > 1e-8 || Math.abs(yx) > 1e-8 || Math.abs(w) < 1e-8
                || Math.abs(product(projection, model, 3, 0)) > 1e-8
                || Math.abs(product(projection, model, 3, 1)) > 1e-8) {
            return null;
        }
        double sx = xx / w * viewport.get(2) * 0.5;
        double sy = yy / w * viewport.get(3) * 0.5;
        double ox = viewport.get(0) + (product(projection, model, 0, 3) / w + 1) * viewport.get(2) * 0.5;
        double oy = viewport.get(1) + (product(projection, model, 1, 3) / w + 1) * viewport.get(3) * 0.5;
        if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(ox) || !Double.isFinite(oy)
                || Math.abs(sx) < 1e-8 || Math.abs(sy) < 1e-8) {
            return null;
        }
        return new GuiTextPixelGrid(sx, sy, ox, oy);
    }

    private static double product(FloatBuffer a, FloatBuffer b, int row, int column) {
        double result = 0;
        for (int k = 0; k < 4; k++) {
            result += (double) a.get(k * 4 + row) * b.get(column * 4 + k);
        }
        return result;
    }

    double alignX(double x) {
        return (Math.round(originX + x * scaleX) - originX) / scaleX;
    }

    double alignY(double y) {
        return (Math.round(originY + y * scaleY) - originY) / scaleY;
    }
}
