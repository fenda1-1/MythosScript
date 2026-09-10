package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.List;

/** Transactional return-point preview; existing text is preserved until confirmation. */
final class AutoFollowReturnSelection {
    static final class Point {
        final double x, y, z;
        final String encoded;
        Point(double x, double y, double z, String encoded) {
            this.x = x; this.y = y; this.z = z; this.encoded = encoded;
        }
    }

    final List<Point> points = new ArrayList<>();
    final double x1, z1, x2, z2;
    private int replacement;

    AutoFollowReturnSelection(double[] area, List<String> existing, double fallbackY, int replacement) {
        if (area == null || area.length != 4) throw new IllegalArgumentException("请先设置范围点1和点2");
        for (double value : area) if (!Double.isFinite(value)) throw new IllegalArgumentException("范围坐标必须是有效数字");
        x1 = area[0]; z1 = area[1]; x2 = area[2]; z2 = area[3];
        for (String value : existing) {
            String[] xyz = AutoFollowUiLists.coordinates(value);
            AutoFollowUiLists.point(xyz[0], xyz[1], xyz[2]);
            points.add(new Point(Double.parseDouble(xyz[0]), xyz[1].isEmpty() ? fallbackY : Double.parseDouble(xyz[1]),
                    Double.parseDouble(xyz[2]), value));
        }
        this.replacement = replacement >= 0 && replacement < points.size() ? replacement : -1;
    }

    boolean contains(double x, double z) {
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    boolean add(double x, double y, double z) {
        if (!contains(x, z)) return false;
        Point point = new Point(x, y, z, AutoFollowUiLists.point(Double.toString(x), Double.toString(y), Double.toString(z)));
        if (replacement >= 0) {
            points.set(replacement, point);
            replacement = -1;
        } else {
            for (Point existing : points) if (existing.x == x && existing.y == y && existing.z == z) return false;
            points.add(point);
        }
        return true;
    }

    void remove(int index) {
        if (index < 0 || index >= points.size()) return;
        points.remove(index);
        if (replacement == index) replacement = -1;
        else if (replacement > index) replacement--;
    }

    /** Pick the nearest 1 x 2 x 1 marker intersected by the displayed camera ray, including through walls. */
    int hit(double[] ray) {
        if (ray == null) return -1;
        int result = -1;
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            double[] min = {p.x - 0.5, p.y, p.z - 0.5};
            double[] max = {p.x + 0.5, p.y + 2, p.z + 0.5};
            double enter = 0, exit = 1;
            for (int axis = 0; axis < 3; axis++) {
                double delta = ray[axis + 3] - ray[axis];
                if (Math.abs(delta) < 1.0e-10) {
                    if (ray[axis] < min[axis] || ray[axis] > max[axis]) { exit = -1; break; }
                } else {
                    double a = (min[axis] - ray[axis]) / delta, b = (max[axis] - ray[axis]) / delta;
                    enter = Math.max(enter, Math.min(a, b));
                    exit = Math.min(exit, Math.max(a, b));
                }
            }
            if (enter <= exit && enter < closest) { closest = enter; result = i; }
        }
        return result;
    }

    List<String> result() {
        List<String> result = new ArrayList<>();
        for (Point p : points) result.add(p.encoded);
        return result;
    }
}
