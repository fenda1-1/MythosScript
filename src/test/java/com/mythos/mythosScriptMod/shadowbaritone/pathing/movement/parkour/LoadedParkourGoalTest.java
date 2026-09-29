package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoadedParkourGoalTest {
    @Test public void pistonReplanMustRestoreTheRunningJumpApproach() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("swamp-60/direct-goal.zip");
        GoalBlock goal=new GoalBlock(74,25,-3290);
        for(int startX:new int[]{61,66}) {
            CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(startX+.5,startX==61?18:19,-3289.5));
            com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
            PathCalculationResult result=new AStarPathFinder(startX,startX==61?18:19,-3290,goal,new Favoring(null,context),context).calculate(1,1);
            assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
            java.util.List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves=result.getPath().get().movements();
            System.out.println("Piston approach from "+startX+": "+result.getPath().get().positions());
            int launches=0;
            for(int i=0;i<moves.size();i++) {
                if(!(moves.get(i) instanceof MovementPistonLaunch)) continue;
                launches++;
                assertTrue("A replan must return to the run-up before taking control",i>0);
                com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement approach=moves.get(i-1);
                assertTrue("Walking onto the plate bypasses the precise contact/velocity search",
                        approach instanceof com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour
                                || approach instanceof MovementPistonLaunch);
                Vec3d axis=((MovementPistonLaunch)moves.get(i)).launchDirection();
                assertTrue("Approach from behind, not backwards from the front ledge",
                        (approach.getDest().x-approach.getSrc().x)*axis.x+(approach.getDest().z-approach.getSrc().z)*axis.z>0);
            }
            assertEquals("Preserve the already working consecutive launches",2,launches);
        }
    }
    @Test public void launchEnvelopeMustNotSkipTheTriggerApproach() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("swamp-60/direct-goal.zip");
        CalculationContext context=CapturedParkourGraphTest.context(world,new GoalBlock(69,23,-3290),new Vec3d(66.5,19,-3289.5));
        MovementPistonLaunch launch=MovementPistonLaunch.find(context,new BetterBlockPos(64,19,-3290),1,0);
        assertNotNull(launch);
        BetterBlockPos waitingLedge=new BetterBlockPos(66,19,-3290);
        assertTrue("The execution envelope really contains the pre-trigger ledge",launch.getValidPositions().contains(waitingLedge));
        java.lang.reflect.Method match=com.mythos.mythosScriptMod.shadowbaritone.pathing.path.PathExecutor.class
                .getDeclaredMethod("matchesProgressPosition",com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Movement.class,BetterBlockPos.class);
        match.setAccessible(true);
        assertEquals("Must return to the plate before taking control",false,match.invoke(null,launch,waitingLedge));
        assertEquals(true,match.invoke(null,launch,launch.getSrc()));
    }
    @Test public void pistonPadMustNotBecomeAnOrdinaryLanding() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("swamp-60/direct-goal.zip");
        GoalBlock goal=new GoalBlock(69,23,-3290);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(61.5,18,-3289.5));
        com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
        assertTrue(MovementPistonLaunch.isLaunchPad(context,new BetterBlockPos(65,19,-3290)));
        PathCalculationResult result=new AStarPathFinder(61,18,-3290,goal,new Favoring(null,context),context).calculate(1,1);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        assertTrue("Use the trigger controller rather than a static slime landing",result.getPath().get().movements()
                .stream().anyMatch(move -> move instanceof MovementPistonLaunch));
        for(BetterBlockPos pos:result.getPath().get().positions())
            assertFalse("No ordinary landing on the moving pad",MovementPistonLaunch.isLaunchPad(context,pos));
        // This nearer target previously chose 61 -> 65 (moving slime) -> 66,
        // even though the complete high-platform route used the launch controller.
        GoalBlock side=new GoalBlock(66,19,-3290);
        PathCalculationResult bypass=new AStarPathFinder(61,18,-3290,side,new Favoring(null,context),context).calculate(1,1);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,bypass.getType());
        for(BetterBlockPos pos:bypass.getPath().get().positions())
            assertFalse("Take the stationary side ledge instead of the moving pad",MovementPistonLaunch.isLaunchPad(context,pos));
        // The same launcher remains dynamic after it extends.
        world.states.put(new net.minecraft.util.math.BlockPos(65,18,-3290),net.minecraft.init.Blocks.PISTON_HEAD.getDefaultState());
        world.states.put(new net.minecraft.util.math.BlockPos(65,19,-3290),net.minecraft.init.Blocks.SLIME_BLOCK.getDefaultState());
        context=CapturedParkourGraphTest.context(world,goal,new Vec3d(61.5,18,-3289.5));
        assertTrue(MovementPistonLaunch.isLaunchPad(context,new BetterBlockPos(65,20,-3290)));
    }
    @Test public void newlyLoadedGoalMustReplaceLocalExploration() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("snow-10/direct-goal.zip");
        java.util.Map<net.minecraft.util.math.BlockPos,net.minecraft.block.state.IBlockState> full=
                new java.util.HashMap<>(world.states);
        world.states.keySet().removeIf(pos -> pos.getX() >= 80);
        GoalBlock goal=new GoalBlock(100,19,-2068);
        CalculationContext partial=CapturedParkourGraphTest.context(world,goal,new Vec3d(.5,11,-2067.5));
        com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
        assertFalse(partial.bsi.worldContainsLoadedChunk(goal.x,goal.z));
        PathCalculationResult first=new AStarPathFinder(0,11,-2068,goal,new Favoring(null,partial),partial).calculate(500,5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_SEGMENT,first.getType());
        com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos end=first.getPath().get().getDest();
        world.states.putAll(full);
        CalculationContext loaded=CapturedParkourGraphTest.context(world,goal,new Vec3d(end.x+.5,end.y,end.z+.5));
        assertTrue(loaded.bsi.worldContainsLoadedChunk(goal.x,goal.z));
        PathCalculationResult next=new AStarPathFinder(end.x,end.y,end.z,goal,new Favoring(null,loaded),loaded).calculate(1,1);
        assertEquals("Use newly available chunks for the continuation",PathCalculationResult.Type.SUCCESS_TO_GOAL,next.getType());
    }
    @Test public void unloadedSegmentsMustWaitOnSupportAndKeepAdvancing() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("swamp-60/direct-goal.zip");
        world.states.keySet().removeIf(pos -> pos.getX() >= 112);
        GoalBlock goal=new GoalBlock(167,18,-3290);
        Vec3d start=new Vec3d(30.5,18,-3289.5);
        for(int segment=0; segment<8 && start.x<80; segment++) {
            CalculationContext context=CapturedParkourGraphTest.context(world,goal,start);
            com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
            assertFalse(context.bsi.worldContainsLoadedChunk(goal.x,goal.z));
            PathCalculationResult result=new AStarPathFinder((int)Math.floor(start.x),(int)start.y,
                    (int)Math.floor(start.z),goal,new Favoring(null,context),context).calculate(500,5000);
            assertEquals("An exhausted local graph is not proof that an unloaded goal is unreachable",
                    PathCalculationResult.Type.SUCCESS_SEGMENT,result.getType());
            com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos end=result.getPath().get().getDest();
            System.out.println("Unloaded segment endpoint="+end);
            System.out.println("Unloaded route="+result.getPath().get().positions());
            assertNotNull("Waiting at a hanging ladder releases control and falls",ParkourSurface.support(context,end));
            assertFalse("A partial route must retain the trigger/launch handoff",MovementPistonLaunch.hasLaunch(context,end));
            for(BetterBlockPos pos:result.getPath().get().positions())
                assertFalse("A partial route cannot use a moving pad as static support",MovementPistonLaunch.isLaunchPad(context,pos));
            assertTrue("The segment must advance",end.x>start.x);
            start=new Vec3d(end.x+.5,end.y,end.z+.5);
        }
        assertTrue("Continue through the initially loaded course",start.x>=80);
    }

    @Test public void unloadedGoalMustStillReturnAnExplorationSegment() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("snow-10/direct-goal.zip");
        GoalBlock goal=new GoalBlock(300,19,-2068);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(.5,11,-2067.5));
        com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
        assertFalse(context.bsi.worldContainsLoadedChunk(goal.x,goal.z));
        PathCalculationResult result=new AStarPathFinder(0,11,-2068,goal,new Favoring(null,context),context).calculate(500,5000);
        assertEquals(PathCalculationResult.Type.SUCCESS_SEGMENT,result.getType());
        assertTrue(result.getPath().get().getDest().getDistance(0,11,-2068)>5);
    }

    @Test public void loadedGoalMustNotCommitAHeuristicDeadEnd() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("snow-10/direct-goal.zip");
        GoalBlock goal=new GoalBlock(100,19,-2068);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(.5,11,-2067.5));
        com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().parkourInputCache.value=false;
        com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings().pathingMaxChunkBorderFetch.value=1;
        long began=System.nanoTime();
        PathCalculationResult result=new AStarPathFinder(0,11,-2068,goal,new Favoring(null,context),context).calculate(1,1);
        System.out.println("Loaded snow goal ms="+(System.nanoTime()-began)/1e6+" result="+result.getType());
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        assertTrue(goal.isInGoal(result.getPath().get().getDest()));
        GoalBlock impossible=new GoalBlock(100,250,-2068);
        PathCalculationResult failed=new AStarPathFinder(0,11,-2068,impossible,new Favoring(null,context),context).calculate(1,1);
        assertEquals("A loaded unreachable goal must not return a one-way partial route",PathCalculationResult.Type.FAILURE,failed.getType());
    }
}
