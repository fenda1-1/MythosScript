package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParkourCapabilityFrontierTest {
    @Test public void unknownFutureEffectMustOnlyReturnCurrentAbilityPrefix() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-60/direct-goal.zip");
        GoalBlock goal = new GoalBlock(167, 18, -3290);
        CalculationContext normal = CapturedParkourGraphTest.context(world, goal, new Vec3d(.5, 11, -3289.5), true);
        Baritone.settings().parkourInputCache.value = false;
        PathCalculationResult prefix = new AStarPathFinder(0, 11, -3290, goal, new Favoring(null, normal), normal).calculate(500, 5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_SEGMENT, prefix.getType());
        net.minecraft.util.math.BlockPos end = prefix.getPath().get().getDest();
        System.out.println("Capability prefix endpoint=" + end);
        assertTrue("Approach the high tower entrance, not the low dead end", end.getX() >= 98 && end.getX() <= 104 && end.getY() >= 24 && end.getY() <= 26);
        assertFalse(goal.isInGoal(end));
        assertTrue("Unobserved boosted jumps must never escape into the executable path",
                prefix.getPath().get().positions().stream().noneMatch(p -> p.getY() >= 30));
        CalculationContext atEntrance = new CalculationContext(normal.getBaritone(), goal, normal.bsi, normal::collisionBoxes,
                normal.maxJumpHeight, new Vec3d(end.getX()+.5, end.getY(), end.getZ()+.5), false, normal.failureScope);
        PathCalculationResult waiting = new AStarPathFinder(end.getX(), end.getY(), end.getZ(), goal,
                new Favoring(null, atEntrance), atEntrance).calculate(500, 5000);
        assertEquals("No effect observed: don't issue a guessed boosted jump", PathCalculationResult.Type.FAILURE, waiting.getType());
        CalculationContext boosted = new CalculationContext(normal.getBaritone(), goal, normal.bsi, normal::collisionBoxes,
                6, atEntrance.playerPosition, false, normal.failureScope);
        PathCalculationResult continuation = new AStarPathFinder(end.getX(), end.getY(), end.getZ(), goal,
                new Favoring(null, boosted), boosted).calculate(500, 5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL, continuation.getType());
    }
}
