package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

/** Block coverage of the player's body plus the permitted corridor drift. */
public final class FlightCorridorClearance {
    private FlightCorridorClearance() {
    }

    @FunctionalInterface
    public interface ClearBlock {
        boolean test(int x, int y, int z);
    }

    public static boolean isClear(int x, int y, int z, double radius, boolean horizontal,
            double halfWidth, double height, ClearBlock clear) {
        int minSide = (int) Math.floor(0.5D - radius - halfWidth);
        int maxSide = (int) Math.ceil(0.5D + radius + halfWidth) - 1;
        double verticalDrift = horizontal ? radius : 0.0D;
        int minY = (int) Math.floor(-verticalDrift);
        int maxY = (int) Math.ceil(height + verticalDrift) - 1;
        for (int dx = minSide; dx <= maxSide; dx++) {
            for (int dz = minSide; dz <= maxSide; dz++) {
                for (int dy = minY; dy <= maxY; dy++) {
                    if (!clear.test(x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
