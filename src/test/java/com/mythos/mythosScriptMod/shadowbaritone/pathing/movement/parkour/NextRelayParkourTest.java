package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Captured failure: the lower exit cannot replace the stair/roof approach. */
public class NextRelayParkourTest {
    @Test public void graphRejectsTheThinFenceBesideTakeoff() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/exit-fence.json");
        net.minecraft.util.math.Vec3d start=new net.minecraft.util.math.Vec3d(43.075,25,-3758.5);
        assertFalse(ParkourSurface.jumpChordClear(start,
                new net.minecraft.util.math.Vec3d(46.5,24,-3758.5),
                body->world.boxes.stream().anyMatch(box->box.intersects(body))));
        assertTrue(ParkourSurface.jumpChordClear(start,
                new net.minecraft.util.math.Vec3d(43.075,25,-3757.5),
                body->world.boxes.stream().anyMatch(box->box.intersects(body))));
    }

    @Test public void fenceBaseNeedsACurvedExitRatherThanStraightDescend() throws Exception {
        ParkourDebugLog.suppressForTests(true);
        CapturedParkourWorld world=new CapturedParkourWorld("level70/exit-fence.json");
        ParkourTrajectory.Frame frame=world.standing(43.07499998807907,25,-3757.500028431933);
        ParkourTrajectory.Frame straight=frame;
        for(int tick=0;tick<30;tick++)
            straight=ParkourTrajectory.step(straight,new ParkourTrajectory.Control(0,true,true,false),world.boxes);
        assertEquals("The centred descend pushes into the post",frame.x,straight.x,.001);
        double gx=44.5,gy=25,gz=-3756.075;
        assertTrue("Target body must be clear",world.safe(world.standing(gx,gy,gz).box()));
        assertTrue("Target must have support",ParkourTrajectory.step(world.standing(gx,gy,gz),new ParkourTrajectory.Control(0,false,false,false),world.boxes).ground);
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(frame,world.boxes,gx,gy,gz,
                true,.18,120,3_000_000_000L,world::safe,
                f->f.ground && Math.abs(f.y-gy)<.001 && Math.hypot(f.x-gx,f.z-gz)<.18
                        && Math.hypot(f.vx,f.vz)<.025);
        assertFalse("The exposed ledge must have a supported exit",route.isEmpty());
        frame=route.get(route.size()-1);
        for(int tick=0;tick<20;tick++) {
            frame=ParkourTrajectory.step(frame,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
            assertTrue(world.safe(frame.box()) && frame.ground);
            assertEquals(gy,frame.y,.001);
            assertTrue(Math.hypot(frame.x-gx,frame.z-gz)<.18);
        }
    }

    @Test public void capturedStairsAndHighTakeoffReachTheFenceLedge() throws Exception {
        ParkourDebugLog.suppressForTests(true);
        CapturedParkourWorld world=new CapturedParkourWorld("level70/exit-fence.json");
        ParkourTrajectory.Frame frame=world.standing(35.648094600998014,26,-3754.481234011597);
        double[][] goals={{35.5,27,-3751.5},{34.5,28,-3751.5},{33.5,29,-3751.5},
                {32.5,30,-3751.5},{31.5,31,-3751.5},{30.5,32,-3751.5},
                {36.5,32,-3751.5},{36.5,32,-3756.5},{43.075,25,-3757.5}};
        for(double[] goal:goals) {
            List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(frame.restart(false),world.boxes,
                    goal[0],goal[1],goal[2],true,.18,120,2_000_000_000L,world::safe,
                    f->f.ground && Math.abs(f.y-goal[1])<.001
                            && Math.hypot(f.x-goal[0],f.z-goal[2])<.18 && Math.hypot(f.vx,f.vz)<.025);
            assertFalse("No stable route to "+java.util.Arrays.toString(goal),route.isEmpty());
            frame=route.get(route.size()-1);
            for(int tick=0;tick<20;tick++) {
                frame=ParkourTrajectory.step(frame,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
                assertTrue(world.safe(frame.box()) && frame.ground);
                assertEquals(goal[1],frame.y,.001);
                assertTrue(Math.hypot(frame.x-goal[0],frame.z-goal[2])<.18);
            }
        }
    }
}
