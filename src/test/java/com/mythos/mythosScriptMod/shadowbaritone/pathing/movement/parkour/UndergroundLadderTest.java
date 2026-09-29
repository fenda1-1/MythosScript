package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementAscend;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UndergroundLadderTest {
    @Test public void hangingLadderCannotAscendSideways() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("underground/direct-goal.zip");
        GoalBlock goal = new GoalBlock(75, 32, -1504);
        CalculationContext context = CapturedParkourGraphTest.context(world, goal, new Vec3d(35.5, 19, -1507.5));
        Baritone.settings().parkourInputCache.value = false;
        assertEquals("Hanging on a north-facing ladder cannot ascend sideways onto the next ladder",
                MovementAscend.COST_INF, MovementAscend.cost(context, 37, 19, -1505, 38, 20, -1505), 0);
        assertTrue("Climb south onto the real backing platform",
                MovementAscend.cost(context, 37, 19, -1505, 37, 20, -1504) < MovementAscend.COST_INF);
        assertTrue("The earlier west-facing ladder must still climb east",
                MovementAscend.cost(context, 27, 13, -1507, 28, 14, -1507) < MovementAscend.COST_INF);
        PathCalculationResult result = new AStarPathFinder(35, 19, -1508, goal,
                new Favoring(null, context), context).calculate(1, 1);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL, result.getType());
        System.out.println("Underground ladder route: " + result.getPath().get().positions());
        ParkourTrajectory.Frame frame = world.standing(35.5, 19, -1507.5);
        for (IMovement move : result.getPath().get().movements()) {
            if (move.getDest().x > 42) break;
            Vec3d target = move instanceof MovementParkour
                    ? MovementParkour.standingTarget(context, ((MovementParkour) move).getCandidate(),
                        ParkourSurface.support(context, move.getDest()))
                    : ParkourSurface.standPoint(context, move.getDest(), ParkourSurface.support(context, move.getDest()));
            List<ParkourTrajectory.Frame> frames = ParkourTrajectory.searchWithRunup(frame.restart(false),
                    world.boxes, target.x, target.y, target.z, true, world::safe,
                    ParkourTrajectory.settledArrival(world.boxes, target.x, target.y, target.z, .18, world::safe));
            System.out.println("Ladder leg " + move.getSrc() + " -> " + move.getDest() + " target=" + target + " frames=" + frames.size());
            assertFalse("Selected ladder exit must have a real trajectory", frames.isEmpty());
            for (ParkourTrajectory.Frame f : frames) assertTrue(world.safe(f.box()));
            frame = frames.get(frames.size() - 1);
            assertTrue("Stand above the ladder before the next jump", frame.ground);
            assertEquals(20, frame.y, .001);
        }
        assertTrue("Cross to the opposite platform", frame.x > 42);
    }
}
