package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import net.minecraft.util.math.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class Level70ParkourTest {
    static { ParkourDebugLog.suppressForTests(true); }
    private final CapturedParkourWorld world;
    public Level70ParkourTest() throws Exception { world=new CapturedParkourWorld("level70/ladder-turn.json"); }

    @Test public void capturedWestLadderHasVanillaCollisionAndFootMembership() {
        assertTrue(world.boxes.contains(new AxisAlignedBB(29.8125,28,-3751,30,29,-3750)));
        ParkourTrajectory.Frame f=world.standing(29.5,28.5,-3750.5);
        f.ground=false;f.vy=-.3;
        assertTrue(f.onLadder());
        ParkourTrajectory.Control hold=new ParkourTrajectory.Control(0,false,false,false,true,0);
        for(int tick=0;tick<20;tick++) {
            f=ParkourTrajectory.step(f,hold,world.boxes);
            assertEquals("Sneak must hold actual feet on ladder",28.5,f.y,1e-9);
            assertTrue(f.onLadder());
        }
        f.x=28.99;
        assertFalse("Body touching the cell is not enough",f.onLadder());
        f.x=29.5;f.y=27.9;
        assertFalse("Head touching the ladder is not enough",f.onLadder());
        f.y=28.1;f.vy=.2;
        f=ParkourTrajectory.step(f,hold,world.boxes);
        assertEquals("Sneak cannot erase ascending velocity",28.3,f.y,1e-8);
    }

    @Test public void capturedSceneSupportsAndSearch() throws Exception {
        ParkourTrajectory.Frame start=world.standing(27.949139,27,-3753.038984);
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(start,world.boxes,
                29.5,28,-3750.5,true,.4,100,1_000_000_000L,world::safe);
        System.out.println("right ladder frames="+route.size());
        assertFalse("Search must catch a real ladder, not fly past it",route.isEmpty());
    }

    private ParkourTrajectory.Frame reach(ParkourTrajectory.Frame start,double x,double y,double z) {
        ParkourTrajectory.Frame target=world.standing(x,y,z);
        assertTrue("Goal must be clear",world.safe(target.box()));
        target=ParkourTrajectory.step(target,new ParkourTrajectory.Control(0,false,false,false),world.boxes);
        assertTrue("Goal must have actual support",target.ground);
        long began=System.nanoTime();
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(start.restart(false),world.boxes,
                x,y,z,true,.18,120,3_000_000_000L,world::safe,
                f->f.ground && Math.abs(f.y-y)<.001 && Math.hypot(f.x-x,f.z-z)<.18 && Math.hypot(f.vx,f.vz)<.025);
        System.out.println("reach "+x+","+y+","+z+" frames="+route.size()+" ms="+(System.nanoTime()-began)/1e6);
        assertFalse("No stable route to "+x+","+y+","+z,route.isEmpty());
        ParkourTrajectory.Frame end=route.get(route.size()-1);
        System.out.println("landing="+end.x+","+end.y+","+end.z+" v="+end.vx+","+end.vy+","+end.vz);
        for(int tick=0;tick<20;tick++) {
            end=ParkourTrajectory.step(end,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
            assertTrue(world.safe(end.box()));assertTrue("Must remain on landing",end.ground);
            assertEquals(y,end.y,.001);
        }
        return end;
    }

    @Test public void currentPositionToRightLadderTurn() {
        ParkourTrajectory.Frame f=world.standing(28.218891701110458,26,-3754.0152638385553);
        f=reach(f,28.5,27,-3752.5);
        f=reach(f,29.65,29,-3750.5);
        f=reach(f,30.5,30,-3749.65);
        reach(f,33.5,32,-3749.65);
    }

    @Test public void currentPositionToLeftTurn() {
        ParkourTrajectory.Frame f=world.standing(28.218891701110458,26,-3754.0152638385553);
        f=reach(f,28.5,27,-3756.5);
        reach(f,32.5,26,-3756.5);
    }

    @Test public void liveSneakEdgeKeepsMotionAndCannotJumpAfterLeavingSupport() {
        // Client ticks 14745..14769, parkour-2026-09-25_02-41-46.
        ParkourTrajectory.Frame f=world.standing(29.61103712736199,27,-3756.3066584252197);
        double heading=Math.atan2(-3756.5-f.z,32.5-f.x);
        ParkourTrajectory.Control sneak=new ParkourTrajectory.Control(heading-7*Math.PI/12,true,false,false,true,0);
        for(int i=0;i<21;i++) f=ParkourTrajectory.step(f,sneak,world.boxes);
        assertEquals(29.197351,f.x,.0001);
        assertEquals(-3757.297525,f.z,.0001);
        assertEquals(-.033466,f.vz,.000002);
        ParkourTrajectory.Control run=new ParkourTrajectory.Control(heading,true,true,false);
        f=ParkourTrajectory.step(f,run,world.boxes);
        assertEquals(-3757.339498,f.z,.0001);
        f=ParkourTrajectory.step(f,run,world.boxes);
        assertFalse("An edge-limited sneak cannot erase the momentum which takes us off support",f.ground);
        assertEquals(26.9216,f.y,.00001);
        f=ParkourTrajectory.step(f,new ParkourTrajectory.Control(heading,true,true,true),world.boxes);
        assertEquals("Jump in the air must not invent a takeoff",26.766368,f.y,.00001);
    }

    @Test public void liveLeftTakeoffPositionCanFindAStableTurn() {
        reach(world.standing(29.61103712736199,27,-3756.3066584252197),32.5,26,-3756.5);
    }

    @Test public void liveLadderCatchMustFinishBrakingBeforeNeutralHold() {
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(
                world.standing(29.679725996431372,29,-3750.5217377055433),world.boxes,
                30.5,29,-3749.5,true,.4,120,1_000_000_000L,world::safe);
        assertFalse(route.isEmpty());
        int firstContact=-1;
        for(int i=0;i<route.size();i++) if(route.get(i).onLadder()) {firstContact=i;break;}
        assertTrue(firstContact>=0);
        assertTrue("Contact must not cut off the braking controls",firstContact<route.size()-3);
        ParkourTrajectory.Frame end=route.get(route.size()-1);
        for(int t=0;t<20;t++) {
            end=ParkourTrajectory.step(end,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
            assertTrue("A completed catch must hold on the ladder",end.onLadder());
        }
        // The pillar handoff must keep sneak held until wall contact; a neutral
        // tick here followed by normal forward falls below this single ladder.
        for(int t=0;t<40;t++) end=ParkourTrajectory.step(end,
                new ParkourTrajectory.Control(-Math.PI/2,true,false,false,true,0),world.boxes);
        assertTrue(end.ground);
        assertEquals(30,end.y,.001);
        assertTrue(world.safe(end.box()));
    }

}
