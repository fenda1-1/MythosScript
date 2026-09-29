package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.*;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class VineExitTest {
    @Test public void risingExitMustReleaseTheWallAtLandingHeight() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-60/direct-goal.zip");
        CapturedParkourGraphTest.context(world, new GoalBlock(81, 27, -3289),
                new Vec3d(80.499744, 26.617055, -3288.506160), true);
        Baritone.settings().parkourInputCache.value = false;
        ParkourTrajectory.Frame start = world.standing(80.499744, 26.617055, -3288.506160);
        start.ground = false;
        start.bodyHeight = (double) 1.65F;
        assertTrue(start.onLadder());
        List<ParkourTrajectory.Frame> plan = ParkourTrajectory.searchWithRunup(start, world.boxes,
                81.5, 27, -3288.5, false, world::safe,
                ParkourTrajectory.settledArrival(world.boxes, 81.5, 27, -3288.5, .18, world::safe));
        assertFalse("The recorded hanging start must reach the leaf platform", plan.isEmpty());
        System.out.println("Rising vine exit frames=" + plan.size());
        assertTrue("Do not circle the top vine until the executor times out", plan.size() < 80);
    }

    @Test public void ordinarySlimePhysicsMustMatchRecordedGroundAlternation() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-60/direct-goal.zip");
        ParkourTrajectory.Frame frame = world.standing(161.370912, 18, -3292.502925);
        ParkourTrajectory.Control idle = new ParkourTrajectory.Control(0, false, false, false);
        // perf-direct-after, client ticks 5045/5046: an unsneaked tiny rebound
        // is clamped to zero next tick, temporarily clearing onGround.
        frame = ParkourTrajectory.step(frame, idle, world.boxes);
        assertTrue(frame.ground);
        assertEquals(-.001568, frame.vy, 1e-6);
        frame = ParkourTrajectory.step(frame, idle, world.boxes);
        assertFalse(frame.ground);
        assertEquals(-.078400, frame.vy, 1e-6);
        frame = ParkourTrajectory.step(frame, new ParkourTrajectory.Control(0, true, true, true), world.boxes);
        assertEquals("A jump request in this airborne phase cannot take off", 18, frame.y, 1e-6);
    }

    @Test public void hangingExitMustReachSolidSupportBeforeWalking() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-60/direct-goal.zip");
        GoalBlock goal = new GoalBlock(81, 27, -3289);
        CalculationContext context = CapturedParkourGraphTest.context(world, goal,
                new Vec3d(80.5, 27, -3288.5), true);
        Baritone.settings().parkourInputCache.value = false;
        assertTrue("Walking releases sneak before reaching the ledge",
                MovementTraverse.cost(context, 80, 27, -3289, 81, -3289) >= 1e6);
        PathCalculationResult result = new AStarPathFinder(80, 27, -3289, goal,
                new Favoring(null, context), context).calculate(500, 5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL, result.getType());
        assertEquals(1, result.getPath().get().movements().size());
        assertTrue(result.getPath().get().movements().get(0) instanceof MovementParkour);
        ParkourTrajectory.Frame start = world.standing(80.5, 27, -3288.5);
        start.ground = false;
        assertTrue(start.onLadder());
        List<ParkourTrajectory.Frame> plan = ParkourTrajectory.search(start, world.boxes,
                81.5, 27, -3288.5, false, .18, 120, 3_000_000_000L, world::safe,
                f -> f.ground && Math.abs(f.y - 27) < .03 && Math.hypot(f.x - 81.5, f.z + 3288.5) < .18
                        && Math.hypot(f.vx, f.vz) < .04);
        assertFalse("The replacement exit must physically reach the leaf platform", plan.isEmpty());
    }
}
