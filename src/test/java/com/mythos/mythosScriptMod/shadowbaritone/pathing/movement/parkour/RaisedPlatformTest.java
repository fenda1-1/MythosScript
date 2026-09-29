package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Live capture: leave the low glass ceiling before jumping onto the raised wool. */
public class RaisedPlatformTest {
    @Test public void overhangingAnvilStanceKeepsItsSupportedGraphCell() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("raised-platform/continuous-jumps.json");
        Vec3d position = new Vec3d(12.729228144683603,131,-75.395675125828);
        CalculationContext context = CapturedParkourGraphTest.context(world,new GoalBlock(11,131,-61),position);
        assertEquals(new BetterBlockPos(13,131,-76), ParkourSurface.supportedFeet(context,position));
        assertEquals(new BetterBlockPos(11,131,-76),
                ParkourSurface.supportedFeet(context,new Vec3d(11.5,131,-75.5)));
        BetterBlockPos opposite=new BetterBlockPos(9,131,-74);
        List<Vec3d> stances=ParkourSurface.standPoints(context,opposite,ParkourSurface.support(context,opposite));
        assertTrue("Both disconnected anvil ledges must remain available", stances.contains(new Vec3d(8.735,131,-73.5))
                && stances.contains(new Vec3d(10.265,131,-73.5)));
    }

    @Test public void plannerConnectsRaisedPlatformBelowOverhang() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("raised-platform/scene.json");
        GoalBlock goal = new GoalBlock(8, 31, -3);
        CalculationContext context = CapturedParkourGraphTest.context(world, goal, new Vec3d(8.5, 30, -.5));
        PathCalculationResult result = new AStarPathFinder(8, 30, -1, goal,
                new Favoring(null, context), context).calculate(2000, 5000);
        System.out.println("Raised platform graph: " + result.getType() + " " + result.getPath().map(p -> p.positions().toString()));
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL, result.getType());
    }

    @Test public void realPhysicsReachesAndSettlesOnRaisedPlatform() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("raised-platform/scene.json");
        CapturedParkourGraphTest.context(world, new GoalBlock(8, 31, -3), new Vec3d(8.5, 30, -.5));
        for (ParkourTrajectory.Frame start : new ParkourTrajectory.Frame[]{world.standing(8.5, 30, -.5), world.capturedPlayer()}) {
            List<ParkourTrajectory.Frame> frames = ParkourTrajectory.searchWithRunup(start, world.boxes,
                    8.5, 31, -2.5, true, world::safe,
                    ParkourTrajectory.settledArrival(world.boxes, 8.5, 31, -2.5, .18, world::safe));
            assertFalse("The real low-ceiling ascent must have a physical trajectory", frames.isEmpty());
            boolean jumped = false;
            for (ParkourTrajectory.Frame frame : frames) {
                assertTrue("No solid intersections or uncaptured space", world.safe(frame.box()));
                jumped |= frame.control.jump;
            }
            assertTrue(jumped);
            ParkourTrajectory.Frame last = frames.get(frames.size() - 1);
            for (int tick = 0; tick < 20; tick++) {
                last = ParkourTrajectory.step(last, new ParkourTrajectory.Control(0, false, false, false, true, 0), world.boxes);
                assertTrue(world.safe(last.box()) && last.ground);
                assertEquals(31, last.y, .001);
                assertTrue(Math.hypot(last.x - 8.5, last.z + 2.5) < .18);
            }
            System.out.println("Raised platform trajectory: start=" + start.x + "," + start.z + " frames=" + frames.size());
        }
    }

    @Test public void plannerConnectsDistantLowDoorway() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("raised-platform/high-platform.json");
        GoalBlock goal = new GoalBlock(12, 97, -103);
        CalculationContext context = CapturedParkourGraphTest.context(world, goal,
                new Vec3d(world.player[0], world.player[1], world.player[2]));
        PathCalculationResult result = new AStarPathFinder(12, 97, -98, goal,
                new Favoring(null, context), context).calculate(2000, 5000);
        System.out.println("High doorway graph: " + result.getType() + " " + result.getPath().map(p -> p.positions().toString()));
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL, result.getType());
    }

    @Test public void realPhysicsReachesDistantLowDoorway() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("raised-platform/high-platform.json");
        CapturedParkourGraphTest.context(world, new GoalBlock(12, 97, -103),
                new Vec3d(world.player[0], world.player[1], world.player[2]));
        ParkourTrajectory.Frame start = world.capturedPlayer();
        List<ParkourTrajectory.Frame> frames = ParkourTrajectory.searchWithRunup(start, world.boxes,
                12.5, 97, -102.5, true, world::safe,
                ParkourTrajectory.settledArrival(world.boxes, 12.5, 97, -102.5, .18, world::safe));
        assertFalse("The captured distant doorway needs a real runup and landing", frames.isEmpty());
        for (ParkourTrajectory.Frame frame : frames) assertTrue(world.safe(frame.box()));
        ParkourTrajectory.Frame last = frames.get(frames.size() - 1);
        for (int tick = 0; tick < 20; tick++) {
            last = ParkourTrajectory.step(last, new ParkourTrajectory.Control(0, false, false, false, true, 0), world.boxes);
            assertTrue(world.safe(last.box()) && last.ground);
            assertEquals(97, last.y, .001);
            assertTrue(Math.hypot(last.x - 12.5, last.z + 102.5) < .18);
        }
        System.out.println("High doorway trajectory frames=" + frames.size());
    }
}
