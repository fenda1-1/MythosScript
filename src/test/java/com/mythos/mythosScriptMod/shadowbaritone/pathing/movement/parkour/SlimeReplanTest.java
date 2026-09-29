package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSlimeBounce;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlimeReplanTest {
    @Test public void failedSlimeEdgeMustNotBeSelectedAgain() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("swamp-60/direct-goal.zip");
        GoalBlock goal=new GoalBlock(167,18,-3290);
        BetterBlockPos from=new BetterBlockPos(147,24,-3289),to=new BetterBlockPos(160,18,-3292);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(147.5104253317793,24,-3288.4985604548747),true);
        Baritone.settings().parkourInputCache.value=false;
        MovementSlimeBounce bad=new MovementSlimeBounce(context.getBaritone(),from,to,new BetterBlockPos(155,14,-3290));
        assertTrue("Recorded starting stance cannot execute this bounce",bad.planRoute(
                world.standing(context.playerPosition.x,24,context.playerPosition.z),world.boxes,world::safe).isEmpty());
        assertNull("A blocked departure is not an open platform edge",MovementSlimeBounce.find(context,from,1,0));
        PathCalculationResult result=new AStarPathFinder(from.x,from.y,from.z,goal,new Favoring(null,context),context).calculate(500,5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        MovementSlimeBounce bounce=(MovementSlimeBounce)result.getPath().get().movements().stream()
                .filter(m -> m instanceof MovementSlimeBounce).findFirst().get();
        assertFalse(bounce.getSrc().equals(from));
        assertFalse("Climb onto the open launch lip before bouncing",bounce.planRoute(world.standing(
                bounce.getSrc().x+.5,bounce.getSrc().y,bounce.getSrc().z+.5),world.boxes,world::safe).isEmpty());
        MovementSlimeBounce.rejectUnsearchable(context,bounce.getSrc(),bounce.getDest());
        result=new AStarPathFinder(from.x,from.y,from.z,goal,new Favoring(null,context),context).calculate(500,5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        assertFalse(result.getPath().get().movements().stream().anyMatch(m ->
                m instanceof MovementSlimeBounce && m.getSrc().equals(bounce.getSrc()) && m.getDest().equals(bounce.getDest())));
        System.out.println("Slime alternative="+result.getPath().get().positions());
    }
}
