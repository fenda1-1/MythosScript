package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import net.minecraft.util.math.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;

public class SwampNetherRegressionTest {
    static CapturedParkourWorld world(String region) throws Exception {
        Path base=Paths.get("src/test/resources/parkour/swamp-nether69");
        JsonObject index=CapturedParkourWorld.read(base.resolve("suite.json"));
        List<Path> files=new ArrayList<>();
        for(JsonElement e:index.getAsJsonArray("captures")) {
            JsonObject c=e.getAsJsonObject();
            if(c.get("region").getAsString().equals(region)) files.add(base.resolve(c.get("file").getAsString()));
        }
        return new CapturedParkourWorld(files);
    }
    @Test public void slimeLiveLaunch() throws Exception {
        CapturedParkourWorld w=world("swamp57");
        CapturedParkourGraphTest.context(w,new GoalBlock(13,11,-3149),new Vec3d(1.581978,11,-3148.5));
        ParkourTrajectory.Frame start=w.standing(1.581978,11,-3148.5);
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.searchLevelSlimeChain(start,w.boxes,
                new BlockPos(1,11,-3149),new BlockPos(13,11,-3149),w::safe);
        org.junit.Assert.assertFalse(route.isEmpty());
        org.junit.Assert.assertEquals(11.121297,route.get(24).y,.00001);
        // The map command at 10,9,-3129 raises players near the first pad by .25.
        ParkourTrajectory.Frame corrected=route.get(24).restart(false);
        corrected.y+=.25;
        org.junit.Assert.assertFalse("The verified bounce survives the map's vertical correction",
                ParkourTrajectory.replaySlimeRemainder(corrected,route,25,w.boxes,
                        new BlockPos(13,11,-3149),w::safe).isEmpty());
        corrected.z+=2;
        org.junit.Assert.assertTrue("A correction that misses the pad must not resume stale controls",
                ParkourTrajectory.replaySlimeRemainder(corrected,route,25,w.boxes,
                        new BlockPos(13,11,-3149),w::safe).isEmpty());
        corrected=route.get(24).restart(false);
        List<ParkourTrajectory.Frame> remaining=route;
        int index=25, corrections=0;
        for(int tick=0;tick<150 && index<remaining.size();tick++) {
            // The repeating command targets a one-block radius, not a one-shot event.
            double dx=corrected.x-6.5,dy=corrected.y-11.5,dz=corrected.z+3148.5;
            double secondDx=corrected.x-10.5;
            if(Math.min(dx*dx,secondDx*secondDx)+dy*dy+dz*dz<=1) {
                corrected.y+=.25;
                corrections++;
                remaining=ParkourTrajectory.replaySlimeRemainder(corrected,remaining,index,w.boxes,
                        new BlockPos(13,11,-3149),w::safe);
                org.junit.Assert.assertFalse("Repeated correction "+corrections+" at "+corrected.x+","+corrected.y
                        +" velocity="+corrected.vx+","+corrected.vy+" must replan its landing",remaining.isEmpty());
                index=0;
            }
            corrected=ParkourTrajectory.step(corrected,remaining.get(index++).control,w.boxes);
        }
        org.junit.Assert.assertTrue(corrections>=3);
        org.junit.Assert.assertTrue(ParkourTrajectory.settledArrival(w.boxes,13.5,11,-3148.5,.18,w::safe).test(corrected));
    }
    @Test public void swimGap() throws Exception {
        CapturedParkourWorld w=world("swamp60-front");
        BetterBlockPos src=new BetterBlockPos(27,16,-3290),dest=new BetterBlockPos(29,16,-3290);
        CapturedParkourGraphTest.context(w,new GoalBlock(dest),new Vec3d(27.67959575068149,15.880357478798684,-3289.4996824337436),true);
        ParkourTrajectory.Frame f=w.standing(27.67959575068149,15.880357478798684,-3289.4996824337436);
        f.ground=false;f.vx=-.005870955828448587;f.vy=.05817677765141917;
        for(int i=0;i<40;i++) {
            ParkourTrajectory.Control c=com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim.control(f,src,dest);
            f=ParkourTrajectory.step(f,c,w.boxes);
            org.junit.Assert.assertTrue("Swim must not collide or drop below the destination pool",w.swimmable(f.box()) && f.y>15.4);
            if(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim.arrived(f,src,dest)) break;
        }
        org.junit.Assert.assertTrue("Shared live swim controller must reach the next pool",f.inWater()
                && com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim.arrived(f,src,dest));
    }
    @Test public void suspendedPoolCatchSettlesBeforeHandoff() throws Exception {
        CapturedParkourWorld w=world("swamp60-front");
        CapturedParkourGraphTest.context(w,new GoalBlock(26,14,-3290),new Vec3d(23.5,15,-3291.5),true);
        ParkourTrajectory.Frame falling=w.standing(26.5,14.1,-3289.5);
        falling.ground=false;falling.vy=-.58;
        org.junit.Assert.assertFalse("Immersion with excessive fall speed is not a completed catch",
                MovementParkour.waterArrival(falling,26.5,14,-3289.5));
        List<ParkourTrajectory.Frame> route=ParkourTrajectory.searchWithRunup(w.standing(23.5,15,-3291.5),
                w.boxes,26.5,14,-3289.5,true,w::swimmable,
                f->f.inWater() && MovementParkour.waterArrival(f,26.5,14,-3289.5));
        org.junit.Assert.assertFalse("A complete buoyancy/braking route must exist",route.isEmpty());
        for(ParkourTrajectory.Frame f:route) org.junit.Assert.assertTrue(w.swimmable(f.box()));
        org.junit.Assert.assertTrue(MovementParkour.waterArrival(route.get(route.size()-1),26.5,14,-3289.5));
    }
    @Test public void crouchedVineCannotPassLeafCeiling() throws Exception {
        CapturedParkourWorld w=world("swamp60-middle");
        CapturedParkourGraphTest.context(w,new GoalBlock(79,27,-3291),new Vec3d(79.193179,26.35,-3290.7),true);
        ParkourTrajectory.Frame hanging=w.standing(79.193179,28-(double)1.65F,-3290.699999988079);
        hanging.bodyHeight=1.65F;
        hanging.ground=false;hanging.vy=.11760000228881837;
        org.junit.Assert.assertTrue(w.safe(hanging.box()));
        ParkourTrajectory.Frame next=ParkourTrajectory.step(hanging,
                new ParkourTrajectory.Control(Math.toRadians(-134.797+90),true,false,false,true,0),w.boxes);
        org.junit.Assert.assertEquals("The captured solid leaf ceiling stops the upward tick",hanging.y,next.y,1e-8);
        org.junit.Assert.assertTrue(w.safe(next.box()));
        ParkourTrajectory.Frame released=ParkourTrajectory.step(next,
                new ParkourTrajectory.Control(0,false,false,false),w.boxes);
        org.junit.Assert.assertEquals("Releasing sneak cannot expand into the ceiling",(double)1.65F,released.bodyHeight,0);
        org.junit.Assert.assertTrue(w.safe(released.box()));
        hanging.bodyHeight=1.8F;
        org.junit.Assert.assertTrue("An already overlapping standing body is not a valid search start",
                ParkourTrajectory.searchWithRunup(hanging,w.boxes,79.5,27,-3290.5,true,
                        body->true,frame->frame.y>=27).isEmpty());
    }
    @Test public void vineAscentRequiresClearBodyCorridor() throws Exception {
        CapturedParkourWorld w=world("swamp60-middle");
        CalculationContext c=CapturedParkourGraphTest.context(w,new GoalBlock(84,28,-3290),new Vec3d(78.5,26,-3290.5),true);
        Vec3d[] slide=ParkourSurface.climbRoute(c,new Vec3d(78.5,26,-3290.5),new Vec3d(79.5,27,-3290.5));
        org.junit.Assert.assertNotNull("Slide past the low leaf ceiling before climbing",slide);
        org.junit.Assert.assertTrue(ParkourSurface.clearCorridor(c,slide));
        org.junit.Assert.assertNull("A climbing source cannot bypass solid leaves",
                ParkourSurface.climbRoute(c,new Vec3d(79.5,27,-3290.5),new Vec3d(80.5,28,-3291.5)));
    }
}
