package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class CocoaRunupTest {
    @Test public void pausedBeamKeepsItsPartiallyExpandedFrontier() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-nether69/swamp55.zip");
        Vec3d position = new Vec3d(13.48564995999734,22.75,-3058.662564690525);
        CapturedParkourGraphTest.context(world,new GoalBlock(14,24,-3055),position,true);
        Baritone.settings().parkourInputCache.value=false;
        ParkourTrajectory.Frame start=world.standing(position.x,position.y,position.z);
        List<net.minecraft.util.math.AxisAlignedBB> boxes=new ParkourTrajectory.CollisionIndex(world.boxes);
        java.util.function.Predicate<ParkourTrajectory.Frame> arrived=ParkourTrajectory.settledArrival(
                boxes,14.5,24,-3054.5,.18,world::safe);
        ParkourTrajectory.BeamSearch pending=new ParkourTrajectory.BeamSearch();
        assertTrue(ParkourTrajectory.search(start,boxes,14.5,24,-3054.5,true,.18,150,0,
                world::safe,arrived,pending).isEmpty());
        assertNotNull(pending.beam);
        // Interrupt after the first expansion, deterministically exercising the same
        // checkpoint as a deadline without relying on this machine's CPU speed.
        try {
            assertTrue(ParkourTrajectory.resumeBeam(start,boxes,14.5,24,-3054.5,true,150,
                    System.nanoTime()+8_000_000_000L,0,body->{
                        Thread.currentThread().interrupt(); return world.safe(body);
                    },arrived,pending).isEmpty());
            assertEquals(1,pending.nextFrame);
            assertFalse(pending.unique.isEmpty());
        } finally { Thread.interrupted(); }
        List<ParkourTrajectory.Frame> resumed=ParkourTrajectory.resumeBeam(start,boxes,14.5,24,-3054.5,true,150,
                System.nanoTime()+8_000_000_000L,0,world::safe,arrived,pending);
        assertFalse(resumed.isEmpty());
        assertTrue(arrived.test(resumed.get(resumed.size()-1)));
        assertEquals(resumed.size(),ParkourTrajectory.replaySupported(start,resumed,boxes,world::safe).size());
    }

    @Test public void recordedPrefetchStartReachesUpperPlatform() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-nether69/swamp55.zip");
        Vec3d position = new Vec3d(13.48564995999734, 22.75, -3058.662564690525);
        CapturedParkourGraphTest.context(world, new GoalBlock(14,24,-3055), position, true);
        Baritone.settings().parkourInputCache.value = false;
        ParkourTrajectory.Frame start = world.standing(position.x,position.y,position.z);
        java.util.function.Predicate<ParkourTrajectory.Frame> arrived = ParkourTrajectory.settledArrival(
                world.boxes,14.5,24,-3054.5,.18,world::safe);
        long before = System.nanoTime();
        List<ParkourTrajectory.Frame> frames = ParkourTrajectory.searchWithRunup(start,world.boxes,
                14.5,24,-3054.5,true,world::safe,arrived);
        System.out.println("cocoa prefetch frames="+frames.size()+" searchMillis="+(System.nanoTime()-before)/1_000_000);
        assertFalse("Recorded upper platform must be reachable with cache disabled",frames.isEmpty());
        assertTrue(arrived.test(frames.get(frames.size()-1)));
        assertEquals(frames.size(),ParkourTrajectory.replaySupported(start,frames,world.boxes,world::safe).size());
    }
}
