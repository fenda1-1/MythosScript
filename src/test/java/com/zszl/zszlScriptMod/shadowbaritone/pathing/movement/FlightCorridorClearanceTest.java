package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import org.junit.Test;
import static org.junit.Assert.*;

public class FlightCorridorClearanceTest {
    private boolean clearExcept(double radius, boolean horizontal, int bx, int by, int bz) {
        return FlightCorridorClearance.isClear(0, 20, 0, radius, horizontal, 0.3D, 1.8D,
                (x, y, z) -> x != bx || y != by || z != bz);
    }

    @Test
    public void wideCorridorIncludesBodyAtBothEdges() {
        assertFalse(clearExcept(1.5D, true, -2, 20, 0));
        assertFalse(clearExcept(1.5D, true, 2, 20, 0));
        assertFalse(clearExcept(1.5D, true, 0, 20, -2));
        assertFalse(clearExcept(1.5D, true, 0, 20, 2));
        assertTrue(clearExcept(1.5D, true, 3, 20, 0));
    }

    @Test
    public void horizontalDriftIncludesFloorAndCeilingObstacles() {
        assertFalse(clearExcept(1.5D, true, 0, 18, 0));
        assertFalse(clearExcept(1.5D, true, 0, 23, 0));
        assertTrue(clearExcept(1.5D, true, 0, 24, 0));
    }

    @Test
    public void evenWidthsAreSymmetric() {
        assertFalse(clearExcept(1.0D, true, -1, 20, 0));
        assertFalse(clearExcept(1.0D, true, 1, 20, 0));
        assertTrue(clearExcept(1.0D, true, -2, 20, 0));
        assertTrue(clearExcept(1.0D, true, 2, 20, 0));
    }

    @Test
    public void narrowPassageRequiresHeadroomButAllowsAdjacentWallsAndFloor() {
        assertFalse(clearExcept(0.0D, true, 0, 20, 0));
        assertFalse(clearExcept(0.0D, true, 0, 21, 0));
        assertTrue(clearExcept(0.0D, true, 1, 20, 0));
        assertTrue(clearExcept(0.0D, true, 0, 19, 0));
        assertTrue(clearExcept(0.0D, true, 0, 22, 0));
    }

    @Test
    public void verticalLaunchDoesNotRequireSpaceBelowFeet() {
        assertTrue(clearExcept(1.5D, false, 0, 19, 0));
        assertFalse(clearExcept(1.5D, false, 2, 21, 2));
    }

    @Test
    public void translatedNegativeCoordinatesAndCustomBodySizeAreCovered() {
        assertFalse(FlightCorridorClearance.isClear(-30, 50, -40, 0.0D, false, 0.6D, 2.5D,
                (x, y, z) -> x != -31 || y != 52 || z != -41));
    }

    @Test
    public void unloadedPartOfVolumeRejectsTheCorridor() {
        assertFalse(FlightCorridorClearance.isClear(15, 20, 15, 1.5D, true, 0.3D, 1.8D,
                (x, y, z) -> x < 16 && z < 16));
    }
}
