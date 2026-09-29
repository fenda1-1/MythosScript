package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ParkourTrajectoryTest {
    static { ParkourDebugLog.suppressForTests(true); }

    @Test public void slabTakeoffClearsLavaWithoutTreatingItsSweptUnionAsOccupancy() {
        List<AxisAlignedBB> boxes = Arrays.asList(
                new AxisAlignedBB(9, 9, -1503, 11, 10.5, -1501),
                new AxisAlignedBB(13, 9, -1505, 15, 10.5, -1504),
                new AxisAlignedBB(13, 9, -1504, 14, 10.5, -1503));
        java.util.function.Predicate<AxisAlignedBB> safe = body -> {
            body = body.grow(-.1, -.4, -.1);
            if (body.minY >= 11) return true;
            for (int x = (int)Math.floor(body.minX + .001); x <= Math.floor(body.maxX - .001); x++) {
                for (int z = (int)Math.floor(body.minZ + .001); z <= Math.floor(body.maxZ - .001); z++) {
                    boolean slab = (x >= 9 && x <= 10 && z >= -1503 && z <= -1502)
                            || (x >= 13 && x <= 14 && z == -1505) || (x == 13 && z == -1504);
                    if (!slab) return false;
                }
            }
            return true;
        };
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(10.594761290382872, 10.5, -1502.4959255330862), boxes,
                13.5, 10.5, -1503.5, true, .18, 150, 10_000_000_000L, safe);
        assertFalse("Must jump between slabs above lava", route.isEmpty());
        for (ParkourTrajectory.Frame frame : route) assertTrue("Entered lava", safe.test(frame.box()));
    }

    @Test public void failedEdgesAreDirectionalAndWorldScoped() {
        ParkourFailureCache cache = new ParkourFailureCache();
        Object world = new Object();
        cache.reject(world, 1, 2);
        assertTrue(cache.contains(world, 1, 2));
        assertFalse(cache.contains(world, 2, 1));
        assertFalse(cache.contains(world, 1, 3));
        assertFalse(cache.contains(new Object(), 1, 2));
    }

    @Test public void unreachableSearchHonorsTimeBudget() {
        long started = System.nanoTime();
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(0, 1, 0), Collections.singletonList(new AxisAlignedBB(-2, 0, -2, 2, 1, 2)),
                8, 1, 0, true, .18, 150, 1_000_000L);
        assertTrue(route.isEmpty());
        assertTrue("Search exceeded bounded work budget", System.nanoTime() - started < 500_000_000L);
    }

    private static List<AxisAlignedBB> load(String name) throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get("src/test/resources/parkour/" + name);
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(" ");
            if (p.length < 6) continue;
            boxes.add(new AxisAlignedBB(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        }
        return boxes;
    }

    private static ParkourTrajectory.Frame standing(double x, double y, double z) {
        ParkourTrajectory.Frame frame = new ParkourTrajectory.Frame();
        frame.x = x; frame.y = y; frame.z = z;
        frame.vy = -.0784;
        frame.ground = true;
        return frame;
    }

    @Test public void airborneAccelerationUsesPreviousTickSprint() {
        ParkourTrajectory.Frame frame = standing(0, 10, 0);
        frame.ground = false;
        frame.airAcceleration = .026F;
        ParkourTrajectory.Frame next = ParkourTrajectory.step(frame,
                new ParkourTrajectory.Control(0, true, false, false), Collections.emptyList());
        assertEquals(.026F * .98, next.x, 1E-7);
        assertEquals(.02F, next.airAcceleration, 0);
    }

    @Test public void fenceJumpLandsAndBrakesInBothDirections() {
        List<AxisAlignedBB> boxes = Arrays.asList(
                new AxisAlignedBB(-.125, 0, -.125, .125, 1.5, .125),
                new AxisAlignedBB(2.875, 0, -.125, 3.125, 1.5, .125));
        for (int direction : new int[] {0, 1}) {
            double from = direction == 0 ? 0 : 3;
            double target = 3 - from;
            List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                    standing(from, 1.5, 0), boxes, target, 1.5, 0, true, .24, 80);
            assertFalse("No fence route in direction " + direction, route.isEmpty());
            ParkourTrajectory.Frame end = route.get(route.size()-1);
            assertTrue(end.ground);
            assertEquals(1.5, end.y, 1E-6);
            assertTrue(Math.hypot(end.x-target, end.z) < .24);
            assertTrue(Math.hypot(end.vx, end.vz) < .04);
            for (int tick = 0; tick < 12; tick++) {
                end = ParkourTrajectory.step(end,
                        new ParkourTrajectory.Control(0, false, false, false), boxes);
                assertTrue("Must remain on fence after navigation releases keys", end.ground);
            }
        }
    }

    @Test public void curvedAscentClearsPillarAndOverheadPlatform() {
        List<AxisAlignedBB> boxes = Arrays.asList(
                new AxisAlignedBB(-9, 9, -7, -8, 10, -6),
                new AxisAlignedBB(-10, 9, -7, -9, 16, -6),
                new AxisAlignedBB(-10, 10, -8, -9, 11, -7),
                new AxisAlignedBB(-9, 13, -7, -8, 14, -6),
                new AxisAlignedBB(-11, 11, -7, -10, 12, -6));
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(-8.5, 10, -6.5), boxes, -9.5, 11, -7.5, true, .24, 100);
        assertFalse("No curved ascent around pillar", route.isEmpty());
        for (ParkourTrajectory.Frame frame : route) {
            for (AxisAlignedBB box : boxes) {
                assertFalse("Trajectory intersects an obstacle", frame.box().intersects(box));
            }
        }
        ParkourTrajectory.Frame end = route.get(route.size()-1);
        assertTrue(end.ground);
        assertEquals(11, end.y, 1E-6);
    }

    @Test public void longGapCanBackOffForSprintHopRunup() {
        List<AxisAlignedBB> boxes = Arrays.asList(
                new AxisAlignedBB(-2, 8, -2, -1, 9, 2),
                new AxisAlignedBB(-5, 8, -7, -2, 9, -6));
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(-1.5, 9, -1.0206896739050721), boxes, -2.5, 9, -6.5, true, .24, 150);
        assertFalse("Must find acceleration runway before long jump", route.isEmpty());
        ParkourTrajectory.Frame end = route.get(route.size()-1);
        assertTrue(end.ground);
        assertEquals(9, end.y, 1E-6);
    }

    @Test public void liveCapturedLavaGapReachesTheWestLedge() throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get(
                "E:/我的世界脚本/MythosTests/instances/1.12.2/inject/game/logs/parkour/live-search-195_-37613-to-125_-37585.txt");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
        String[] head = lines.get(0).split(" ");
        double sx = Double.parseDouble(head[0]), sy = Double.parseDouble(head[1]), sz = Double.parseDouble(head[2]);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(" ");
            if (p.length < 6) continue;
            boxes.add(new AxisAlignedBB(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        }
        double gx = 9.5, gy = 27, gz = -3759.5;
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(sx, sy, sz), boxes, gx, gy, gz, true, .55, 80, 4_000_000_000L, box -> true,
                frame -> frame.ground && Math.abs(frame.y - gy) < .03 && Math.hypot(frame.x - gx, frame.z - gz) < .55);
        assertFalse("Live collision set has no route across the lava gap", route.isEmpty());
    }

    // Level70ParkourTest replaces the old hand-drawn ladder boxes/fly-by assertions
    // with real block states, automatic routes and twenty-tick settled landings.

    @Test public void liveLevel70RejectsALandingInsideTheWall() throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get(
                "E:/我的世界脚本/MythosTests/instances/1.12.2/inject/game/logs/parkour/live-search-290_-37538-to-315_-37535.txt");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
        String[] s = lines.get(0).split(" ");
        double sx = Double.parseDouble(s[0]), sy = Double.parseDouble(s[1]), sz = Double.parseDouble(s[2]);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(" ");
            if (p.length < 6) continue;
            boxes.add(new AxisAlignedBB(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        }
        double gx = 31.5, gy = 26, gz = -3753.5;
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(sx, sy, sz), boxes, gx, gy, gz, true, .55, 80, 1_000_000_000L, box -> true,
                frame -> frame.ground && Math.abs(frame.y - gy) < .03 && Math.hypot(frame.x - gx, frame.z - gz) < .55);
        assertTrue("A route into the solid wall must not exist", route.isEmpty());
    }

    @Test public void liveCapturedFenceSearchReachesTheLedge() throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get(
                "E:/我的世界脚本/MythosTests/instances/1.12.2/inject/game/logs/parkour/live-search-55_-37151-to-85_-37151.txt");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
        String[] head = lines.get(0).split(" ");
        double sx = Double.parseDouble(head[0]), sy = Double.parseDouble(head[1]), sz = Double.parseDouble(head[2]);
        double gx = Double.parseDouble(head[head.length-3]), gy = Double.parseDouble(head[head.length-2]), gz = Double.parseDouble(head[head.length-1]);
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(" ");
            if (p.length < 6) continue;
            boxes.add(new AxisAlignedBB(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        }
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(sx, sy, sz), boxes, gx, gy, gz, true, .55, 80, 4_000_000_000L, box -> true,
                frame -> frame.ground && Math.abs(frame.y - gy) < .03 && Math.hypot(frame.x - gx, frame.z - gz) < .55);
        assertFalse("Live collision set has no route onto the next fence ledge", route.isEmpty());
    }

    @Test public void level69FenceTurnaroundLeavesTheOpenSide() {
        // Live scan: pillars occupy z=-3717, isolated fence posts sit on z=-3716.
        // The open side is z=-3715. A 0.25 post at (5,26,-3716) leaves the
        // recorded stance on its north edge, not inside the post.
        List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        for (int x : new int[]{3, 6, 9}) {
            boxes.add(new AxisAlignedBB(x, 25, -3717, x + 1, 28, -3716));
            boxes.add(new AxisAlignedBB(x, 25, -3716, x + 1, 29, -3715));
        }
        for (int x : new int[]{4, 5, 7, 8}) {
            boxes.add(new AxisAlignedBB(x, 25, -3717, x + 1, 28, -3716));
            boxes.add(new AxisAlignedBB(x + .375, 26, -3715.625, x + .625, 27.5, -3715.375));
        }
        boxes.add(new AxisAlignedBB(5, 25.5, -3716, 6, 26, -3715));
        boxes.add(new AxisAlignedBB(7, 25.5, -3716, 8, 26, -3715));
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(5.48, 26, -3715.06), boxes, 8.5, 26, -3715.5, true, .55, 80,
                4_000_000_000L, box -> true, frame -> frame.ground && Math.abs(frame.y - 26) < .03
                        && Math.hypot(frame.x - 8.5, frame.z + 3715.5) < .55);
        assertFalse("Must reverse off the fence edge around the pillar", route.isEmpty());
        ParkourTrajectory.Frame end = route.get(route.size() - 1);
        assertTrue(end.ground);
        assertEquals(26, end.y, .03);
        assertTrue(Math.hypot(end.x - 8.5, end.z + 3715.5) < .55);
        for (ParkourTrajectory.Frame frame : route) {
            for (AxisAlignedBB box : boxes) {
                assertFalse("Turnaround hits the fence pillar", frame.box().intersects(box));
            }
        }
    }

    @Test public void reverseLongGapUsesSidewaysRunway() {
        List<AxisAlignedBB> boxes = Arrays.asList(
                new AxisAlignedBB(-2, 8, -2, -1, 9, 2),
                new AxisAlignedBB(-5, 8, -7, -2, 9, -6));
        List<ParkourTrajectory.Frame> route = ParkourTrajectory.search(
                standing(-3.2260851574354565, 9, -6.462978735241104), boxes, -1.5, 9, -1.5, true, .18, 150);
        assertFalse("Reverse gap needs curved takeoff from the sideways runway", route.isEmpty());
    }
}
