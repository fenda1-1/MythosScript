package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class AdditionalParkourRegressionTest {
    @Test public void poisonLaunchWaitAvoidsVanillaDamageTicks() {
        net.minecraft.init.Bootstrap.register();
        assertTrue("Recorded 14-frame handoff crosses duration 25", MovementParkour.poisonMayInterrupt(32,0,14));
        assertTrue("Allow the just-issued packet to arrive", MovementParkour.poisonMayInterrupt(24,0,14));
        assertFalse("Launch after the packet window, before expiry", MovementParkour.poisonMayInterrupt(21,0,14));
        assertFalse(MovementParkour.poisonMayInterrupt(0,0,14));
        for(int amplifier=0;amplifier<=5;amplifier++) for(int duration=1;duration<=70;duration++) {
            if(MovementParkour.poisonMayInterrupt(duration,amplifier,14)) continue;
            for(int future=0;future<=17 && duration-future>0;future++) {
                assertFalse("Released flight must not cross a vanilla poison tick",
                        net.minecraft.init.MobEffects.POISON.isReady(duration-future,amplifier));
            }
        }
    }
    @Test public void damagingSnowDropMustNotImmediatelyRelaunch() throws Exception {
        CapturedParkourWorld w=new CapturedParkourWorld("snow-10/direct-goal.zip");
        CalculationContext c=CapturedParkourGraphTest.context(w,new GoalBlock(47,11,-2075),new Vec3d(39.5,15,-2074.5),true);
        List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves=new ArrayList<>();
        BetterBlockPos[] points={new BetterBlockPos(39,15,-2075),new BetterBlockPos(43,11,-2074),new BetterBlockPos(47,11,-2075)};
        for(int i=0;i<2;i++) {
            final BetterBlockPos target=points[i+1];
            ParkourJumpCandidate edge=MovementParkour.surfaceCandidates(c,points[i]).stream()
                    .filter(candidate->candidate.getDest().equals(target)).findFirst().orElse(null);
            assertNotNull("Captured snow drop and next jump must remain available",edge);
            moves.add(MovementParkour.fromCandidate(c,edge));
        }
        assertTrue("Fall-damage landing needs a settled standalone search",MovementParkour.flowChain(c,moves,0).isEmpty());
        assertEquals("Ice handoff must also stop before the late server impulse",1,MovementParkour.trajectoryChain(c,moves,0).size());
        assertEquals("Ordinary jumps still use flow planning",1,MovementParkour.flowChain(c,moves,1).size());
    }
    static CapturedParkourWorld world(String region) throws Exception {
        Path base=Paths.get("src/test/resources/parkour/additional-courses");
        JsonObject index=CapturedParkourWorld.read(base.resolve("suite.json"));
        List<Path> files=new ArrayList<>();
        for(JsonElement e:index.getAsJsonArray("captures")) {
            JsonObject c=e.getAsJsonObject();
            if(c.get("region").getAsString().equals(region)) files.add(base.resolve(c.get("file").getAsString()));
        }
        return new CapturedParkourWorld(files);
    }
    @Test public void pistonEntryMustLandOnPlateBeforeSlimeBounce() throws Exception {
        CapturedParkourWorld w=world("swamp58");
        CalculationContext c=CapturedParkourGraphTest.context(w,new GoalBlock(19,23,-3196),new Vec3d(.5,11,-3195.5),true);
        MovementPistonLaunch piston=MovementPistonLaunch.find(c,new BetterBlockPos(5,10,-3196),1,0);
        assertNotNull(piston);
        // Failed live tick 1173: overlapping the trigger in midair is too early.
        ParkourTrajectory.Frame premature=w.standing(5.97063,10.162141,-3195.5);
        premature.ground=false;premature.vy=-.582443;premature.vx=.17;
        assertTrue(premature.box().intersects(piston.triggerVolume()));
        assertFalse(piston.safeApproach(premature.box()));
        assertFalse(piston.readyToLaunch(premature));
        ParkourTrajectory.Frame slime=w.standing(6.130252,10,-3195.5);
        slime.vy=.492394;slime.vx=.17;
        assertFalse("An ordinary slime rebound must not count as a piston-ready landing",piston.readyToLaunch(slime));
        Vec3d target=piston.triggerLanding();
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(w.standing(1.581978,11,-3195.5),w.boxes,
                target.x,target.y,target.z,true,.18,110,8_000_000_000L,
                body->w.safe(body) && piston.safeApproach(body),piston::readyToLaunch);
        assertFalse("A physically simulated late plate landing must exist",route.isEmpty());
        for(int i=0;i<route.size()-1;i++)
            assertFalse("Do not arm the plate before the final landing",route.get(i).box().intersects(piston.triggerVolume()));
        ParkourTrajectory.Frame landing=route.get(route.size()-1);
        assertTrue(piston.readyToLaunch(landing));
        assertTrue("Feet must land on the plate's block, not the adjacent slime",landing.x<6);
    }
    @Test public void cachedSnowHandoffRejectsSmallSupportChangingError() throws Exception {
        CapturedParkourWorld w=world("snow03");
        CapturedParkourGraphTest.context(w,new GoalBlock(8,14,-1737),new Vec3d(4.947107,13,-1736.87019),true);
        ParkourTrajectory.Frame expected=w.standing(4.947107,13,-1736.87019);
        expected.vx=.025386;expected.vy=-.0784;expected.vz=.255236;
        ParkourTrajectory.Frame actual=expected.restart(false);actual.x+=.075;
        List<ParkourTrajectory.Frame> prepared=new ArrayList<>();
        ParkourTrajectory.Frame planned=expected;
        // Controls from the failed chain: three run-up ticks followed by jump.
        for(int tick=0;tick<4;tick++) {
            double yaw=tick<2?-91.270:tick==2?-91.302:-91.357;
            planned=ParkourTrajectory.step(planned,new ParkourTrajectory.Control(Math.toRadians(yaw+90),
                    true,true,tick==3,false,0,tick==0),w.boxes);
            prepared.add(planned);
        }
        assertTrue("The old nominal chain still has support on its last run-up tick",prepared.get(2).ground);
        assertTrue(prepared.get(3).y>13.3);
        assertEquals(4,ParkourTrajectory.replaySupported(expected,prepared,w.boxes,w::safe).size());
        assertTrue("A .075-block error must invalidate inputs that walk off before jumping",
                ParkourTrajectory.replaySupported(actual,prepared,w.boxes,w::safe).isEmpty());
    }
    @Test public void adjacentVineCellsKeepFacingTheirBackingWall() throws Exception {
        CapturedParkourWorld w=SwampNetherRegressionTest.world("swamp60-front");
        double contactZ=-3290-(double).6F/2;
        CalculationContext c=CapturedParkourGraphTest.context(w,new GoalBlock(11,13,-3291),new Vec3d(11.49,11.8,contactZ),true);
        assertTrue(MovementParkour.continuousClimb(c,new BetterBlockPos(11,11,-3291),new BetterBlockPos(11,12,-3291)));
        assertTrue(MovementParkour.continuousClimb(c,new BetterBlockPos(11,12,-3291),new BetterBlockPos(11,13,-3291)));
        ParkourTrajectory.Frame frame=w.standing(11.49,11.8,contactZ);
        assertTrue("Start exactly at the wall's float-width contact",w.safe(frame.box()));
        frame.ground=false;frame.vy=.11760000228881837;
        for(int tick=0;tick<30 && frame.y<13.8;tick++) {
            ParkourTrajectory.Control input=ParkourTrajectory.climbControl(frame,11.5,-3290.5);
            assertNotNull(input);
            assertTrue("Do not reverse towards the cell centre at each integer height",Math.abs(input.yaw)<5);
            double y=frame.y;
            frame=ParkourTrajectory.step(frame,input,w.boxes);
            assertTrue("Collision-free climb tick "+tick,w.safe(frame.box()));
            assertTrue("Keep climbing through the cell handoff",frame.y>y);
        }
        assertTrue(frame.y>13.8);
    }
    @Test public void risingPoolsPreserveMomentumThroughFirstCatch() throws Exception {
        CapturedParkourWorld w=world("swamp52");
        CalculationContext c=CapturedParkourGraphTest.context(w,new GoalBlock(5,14,-2914),new Vec3d(.5,11,-2913.5),true);
        assertTrue("A* must expose a continuous water-relay edge",
                MovementParkour.surfaceCandidates(c,new BetterBlockPos(1,11,-2914),EnumFacing.EAST,null)
                        .stream().anyMatch(edge->edge.getDest().equals(new BetterBlockPos(5,14,-2914))));
        List<ParkourTrajectory.Frame> chain=ParkourTrajectory.searchWithRunup(w.standing(.5,11,-2913.5),w.boxes,
                5.5,14,-2913.5,true,w::swimmable,f->f.inWater() && MovementParkour.waterArrival(f,5.5,14,-2913.5));
        assertFalse(chain.isEmpty());
        boolean firstPool=false;
        for(ParkourTrajectory.Frame frame:chain) {
            assertTrue(w.swimmable(frame.box()));
            firstPool|=frame.inWater() && frame.x<4;
        }
        assertTrue("The route uses the lower pool instead of inventing a three-block air jump",firstPool);
        assertTrue(MovementParkour.waterArrival(chain.get(chain.size()-1),5.5,14,-2913.5));
    }
}
