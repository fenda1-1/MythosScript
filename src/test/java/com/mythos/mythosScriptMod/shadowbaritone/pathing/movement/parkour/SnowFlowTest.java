package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.JsonElement;
import net.minecraft.util.math.*;
import net.minecraft.init.Blocks;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class SnowFlowTest {
    @Test public void boundedFlowMustKeepDisappearingIceConnected() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("snow-10/direct-goal.zip");
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock goal=
                new com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock(100,19,-2068);
        for(Vec3d from:Arrays.asList(new Vec3d(44.5,14,-2061.5),new Vec3d(84.072,19,-2062.501))) {
            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext c=
                    CapturedParkourGraphTest.context(world,goal,from);
            com.mythos.mythosScriptMod.shadowbaritone.api.Settings settings=
                    com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings();
            settings.parkourInputCache.value=false;
            com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult result=
                    new com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder(
                    (int)Math.floor(from.x),(int)from.y,(int)Math.floor(from.z),goal,
                    new com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring(null,c),c).calculate(1,1);
            assertEquals(com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
            List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves=result.getPath().get().movements();
            settings.planningTickLookahead.value=from.x<50?150:1;
            List<com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour> chain=
                    com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour.flowChain(c,moves,0);
            assertFalse(chain.isEmpty());
            if(from.x<50) assertTrue("Do not solve the entire 32-edge tail before moving",chain.size()<32);
            else assertTrue("Even a one-tick horizon must cross the disappearing pads",chain.size()>1);
            BlockPos end=chain.get(chain.size()-1).getDest();
            AxisAlignedBB support=ParkourSurface.support(c,chain.get(chain.size()-1).getDest());
            assertNotSame("The independent stretch must stop on permanent support",Blocks.ICE,
                    c.getBlock(end.getX(),MathHelper.floor(support.maxY-.001),end.getZ()));
            List<Vec3d> goals=new ArrayList<>();
            for(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour move:chain)
                goals.add(ParkourSurface.standPoint(c,move.getDest(),ParkourSurface.support(c,move.getDest())));
            List<List<ParkourTrajectory.Frame>> flow=ParkourTrajectory.searchFlow(world.standing(from.x,from.y,from.z),
                    world.boxes,goals,true,world::safe);
            assertEquals("The bounded stretch must still be physically executable",goals.size(),flow.size());
            System.out.println("SNOW bounded chain="+chain.size()+" / "+moves.size()+" end="+end);
        }
    }

    @Test public void uncachedFlowMustSurviveDisappearingIce() throws Exception {
        com.mythos.mythosScriptMod.shadowbaritone.api.Settings settings=
                com.mythos.mythosScriptMod.shadowbaritone.Baritone.settings();
        boolean cached=settings.parkourInputCache.value;
        try {
            settings.parkourInputCache.value=false;
            disappearingIceMustRelaunchWithoutAPlanningTick();
            finalIceMustInheritVelocityAcrossWalkingEdges();
        } finally {
            settings.parkourInputCache.value=cached;
        }
    }

    private CapturedParkourWorld world() throws Exception {
        Path suite=Paths.get("build/parkour-captures/snow-10-20260925/suite.json");
        org.junit.Assume.assumeTrue("Capture snow-10 first",Files.isRegularFile(suite));
        List<Path> files=new ArrayList<>();
        for(JsonElement e:CapturedParkourWorld.read(suite).getAsJsonArray("captures"))
            files.add(suite.getParent().resolve(e.getAsJsonObject().get("file").getAsString()));
        ParkourDebugLog.suppressForTests(true);
        return new CapturedParkourWorld(files);
    }

    @Test public void disappearingIceMustRelaunchWithoutAPlanningTick() throws Exception {
        CapturedParkourWorld world=world();
        ParkourTrajectory.Frame start=world.standing(39.5,15,-2074.5);
        List<Vec3d> goals=Arrays.asList(new Vec3d(43.5,11,-2073.5),new Vec3d(47.5,11,-2074.5),
                new Vec3d(51.5,11,-2075.5),new Vec3d(55.5,11,-2075.5),new Vec3d(58.5,12,-2073.5),
                new Vec3d(60.5,13,-2070.5),new Vec3d(60.5,14,-2067.5),new Vec3d(59.5,14,-2063.5),
                new Vec3d(56.5,13.875,-2060.5));
        List<List<ParkourTrajectory.Frame>> flow=ParkourTrajectory.searchFlow(start,world.boxes,goals,true,world::safe);
        assertEquals("Must plan beyond every fragile intermediate landing",goals.size(),flow.size());
        List<AxisAlignedBB> dynamic=new ArrayList<>(world.boxes);
        Map<BlockPos,Integer> disappearAt=new HashMap<>();
        ParkourTrajectory.Frame actual=start;
        int ticks=0;
        for(int i=0;i<flow.size();i++) {
            if(i>0 && world.getBlockState(new BlockPos(actual.x,actual.y-.01,actual.z)).getBlock()==Blocks.ICE)
                assertTrue("The next tick must jump, not wait for a worker",flow.get(i).get(0).control.jump);
            for(ParkourTrajectory.Frame expected:flow.get(i)) {
                for(Map.Entry<BlockPos,Integer> pad:disappearAt.entrySet())
                    if(pad.getValue()==ticks) dynamic.removeIf(box->box.intersects(new AxisAlignedBB(pad.getKey())));
                actual=ParkourTrajectory.step(actual,expected.control,dynamic);ticks++;
                assertTrue("Flow may not sneak to wait/brake",!expected.control.sneak);
                assertTrue("Dynamic replay must remain safe",world.safe(actual.box()));
                assertEquals(expected.x,actual.x,1e-6);assertEquals(expected.y,actual.y,1e-6);assertEquals(expected.z,actual.z,1e-6);
            }
            assertTrue(actual.ground);
            // Live ice disappears ~7 ticks after contact. Remove it one tick
            // earlier, while preserving its actual takeoff friction.
            final BlockPos pad=new BlockPos(actual.x,actual.y-.01,actual.z);
            if(world.getBlockState(pad).getBlock()==Blocks.ICE)
                disappearAt.put(pad,ticks+6);
        }
        System.out.println("SNOW continuous ice frames="+ticks+" seconds="+ticks/20.0);
        List<List<ParkourTrajectory.Frame>> reused=ParkourTrajectory.searchFlow(start,world.boxes,goals,true,world::safe);
        assertEquals("A reusable flow must preserve all physical landings",goals.size(),reused.size());
        List<AxisAlignedBB> missingFirstPad=new ArrayList<>(world.boxes);
        missingFirstPad.removeIf(box->box.intersects(new AxisAlignedBB(new BlockPos(43,10,-2074))));
        assertTrue("Cached inputs must not invent an ice pad that has disappeared",
                ParkourTrajectory.searchFlow(start,missingFirstPad,goals,true,world::safe).size()<goals.size());
    }

    @Test public void resetDoesNotCancelGravityOrInventSupport() {
        ParkourTrajectory.Frame start=new ParkourTrajectory.Frame();
        start.y=10;start.vx=.7;start.vy=-.3;start.vz=-.4;
        ParkourTrajectory.Frame next=ParkourTrajectory.step(start,
                new ParkourTrajectory.Control(0,false,false,false,false,0,true),Collections.emptyList());
        assertEquals(0,next.x,0);assertEquals(0,next.z,0);
        assertEquals(9.7,next.y,1e-6);assertFalse(next.ground);assertTrue(next.vy<start.vy);
    }

    @Test public void finalIceMustInheritVelocityAcrossWalkingEdges() throws Exception {
        CapturedParkourWorld world=world();
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock goal=
                new com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock(100.5,19,-2067.5);
        Vec3d from=new Vec3d(84.072,19,-2062.501);
        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext c=
                CapturedParkourGraphTest.context(world,goal,from);
        com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult result=
                new com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder(84,19,-2063,goal,
                new com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring(null,c),c).calculate(10000,10000);
        assertEquals(com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        List<com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour> chain=
                com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour.flowChain(c,result.getPath().get().movements(),0);
        assertEquals("Walking edges may not split the ice plan",result.getPath().get().movements().size(),chain.size());
        List<Vec3d> goals=new ArrayList<>();
        for(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour move:chain)
            goals.add(ParkourSurface.standPoint(c,move.getDest(),ParkourSurface.support(c,move.getDest())));
        ParkourTrajectory.Frame actual=world.standing(from.x,from.y,from.z);
        List<List<ParkourTrajectory.Frame>> flow=ParkourTrajectory.searchFlow(actual,world.boxes,goals,true,world::safe);
        assertEquals("Search beyond the last disappearing pad",goals.size(),flow.size());
        List<AxisAlignedBB> dynamic=new ArrayList<>(world.boxes);
        Map<BlockPos,Integer> disappearAt=new HashMap<>();int ticks=0;
        for(int i=0;i<flow.size();i++) {
            if(world.getBlockState(new BlockPos(actual.x,actual.y-.01,actual.z)).getBlock()==Blocks.ICE)
                assertTrue("Do not brake or run up on a disappearing landing",flow.get(i).get(0).control.jump);
            for(ParkourTrajectory.Frame expected:flow.get(i)) {
                for(Map.Entry<BlockPos,Integer> pad:disappearAt.entrySet())
                    if(pad.getValue()==ticks) dynamic.removeIf(box->box.intersects(new AxisAlignedBB(pad.getKey())));
                ParkourTrajectory.Frame previous=actual;
                actual=ParkourTrajectory.step(actual,expected.control,dynamic);ticks++;
                assertFalse(expected.control.sneak);
                if(previous.ground && actual.ground) {
                    Vec3d target=goals.get(i);
                    assertTrue("No backwards ground runup",(actual.x-previous.x)*(target.x-previous.x)
                            +(actual.z-previous.z)*(target.z-previous.z)>=-.001);
                }
                assertTrue(world.safe(actual.box()));
                assertEquals(expected.x,actual.x,1e-6);assertEquals(expected.y,actual.y,1e-6);assertEquals(expected.z,actual.z,1e-6);
            }
            BlockPos pad=new BlockPos(actual.x,actual.y-.01,actual.z);
            if(world.getBlockState(pad).getBlock()==Blocks.ICE) disappearAt.put(pad,ticks+6);
        }
        System.out.println("SNOW final continuous frames="+ticks);
    }

    @Test public void lowCeilingTunnelMustConnectToLastRelay() throws Exception {
        CapturedParkourWorld world=world();
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock goal=
                new com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock(66.5,19,-2067.5);
        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext c=
                CapturedParkourGraphTest.context(world,goal,new Vec3d(43.5,14,-2062.5));
        com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult result=
                new com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder(43,14,-2063,goal,
                new com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring(null,c),c).calculate(10000,10000);
        assertEquals(com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult.Type.SUCCESS_TO_GOAL,
                result.getType());
        result.getPath().get().sanityCheck();
        // An arch clipping the jump apex must not be mistaken for a solid wall.
        assertTrue(ParkourSurface.lowCeilingCorridor(c,new Vec3d(50.5,18.875,-2067.5),new Vec3d(52.5,19,-2067.5)));
        assertFalse(ParkourSurface.lowCeilingCorridor(c,new Vec3d(50.5,20,-2067.5),new Vec3d(52.5,21,-2067.5)));
    }

    @Test public void towerShouldPreferSupportedRouteOverIsolatedLongShortcuts() throws Exception {
        CapturedParkourWorld world=world();
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock goal=
                new com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock(30.5,19,-2071.5);
        com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext c=
                CapturedParkourGraphTest.context(world,goal,new Vec3d(27.5,9,-2071.5));
        com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult result=
                new com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder(27,9,-2072,goal,
                new com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring(null,c),c).calculate(10000,10000);
        assertEquals(com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        for(com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement move:result.getPath().get().movements()) {
            // Both shortcuts repeatedly exhaust the physical search in the real
            // captured tower. The longer supported route is already verified.
            assertFalse("Do not spend two failed searches before taking the supported route",
                    move.getSrc().equals(new BlockPos(29,17,-2071)) && move.getDest().getZ()==-2066
                    && (move.getDest().getX()==28 || move.getDest().getX()==27));
        }
    }
}
