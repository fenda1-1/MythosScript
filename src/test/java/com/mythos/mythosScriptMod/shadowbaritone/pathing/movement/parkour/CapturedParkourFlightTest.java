package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.handlers.FlyHandler;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.calc.IPath;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementHelper;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementFly;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** A* flight pathing through the captured 10-layer maze tower; no game process. */
public class CapturedParkourFlightTest {

    /** The recorded tower: glass shell x[-38,-16] z[-12,2], slab layers every 4 y, maze baffles. */
    private CapturedParkourWorld tower() throws Exception {
        return new CapturedParkourWorld(Collections.singletonList(
                Paths.get("src/test/resources/parkour/flight-tower/tower.zip")));
    }

    static void assertAirPath(CapturedParkourWorld world, CalculationContext c, IPath path) {
        List<BetterBlockPos> positions = path.positions();
        List<IMovement> moves = path.movements();
        assertEquals(positions.size(), moves.size() + 1);
        int clearance = Math.max(0, com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().flightClearance.value);
        for (int i = 0; i < moves.size(); i++) {
            assertTrue("non-fly movement at " + i, moves.get(i) instanceof MovementFly);
            BetterBlockPos dest = moves.get(i).getDest();
            for (int cy = 0; cy <= clearance; cy++) {
                assertTrue("path cell blocked " + dest + " +" + cy,
                        MovementHelper.canFlyThrough(c, dest.x, dest.y + cy, dest.z));
            }
        }
        // Consecutive positions must be axis-adjacent (flight moves are 1-cell axis steps)
        for (int i = 0; i + 1 < positions.size(); i++) {
            BetterBlockPos a = positions.get(i), b = positions.get(i + 1);
            int manhattan = Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
            assertEquals("flight step not unit-axis " + a + " -> " + b, 1, manhattan);
        }
    }

    @Test public void flightFromLobbyToTopGoal() throws Exception {
        CapturedParkourWorld world = tower();
        // Air cell above the gold marker platform (gold itself is solid, can't be a fly dest)
        GoalBlock goal = new GoalBlock(-35, 42, -2);
        CalculationContext c = CapturedParkourGraphTest.context(world, goal, new Vec3d(-36.5, 5.0, -9.5));
        CapturedParkourGraphTest.settings().allowFlightPathing.value = true;
        boolean wasEnabled = FlyHandler.enabled;
        FlyHandler.enabled = true;
        try {
            BetterBlockPos start = new BetterBlockPos(-37, 5, -10);
            PathCalculationResult result = new AStarPathFinder(start.x, start.y, start.z, goal,
                    new Favoring(null, c), c).calculate(30000, 30000);
            System.out.println("flight A* result " + result.getType()
                    + " path=" + result.getPath().map(p -> p.positions().toString()).orElse("none"));
            assertEquals("flight A* must reach the top goal", PathCalculationResult.Type.SUCCESS_TO_GOAL,
                    result.getType());
            IPath path = result.getPath().get();
            assertAirPath(world, c, path);
        } finally {
            FlyHandler.enabled = wasEnabled;
        }
    }
}
