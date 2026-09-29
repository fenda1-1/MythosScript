package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch;
import net.minecraft.init.Blocks;
import java.util.*;
import static org.junit.Assert.*;

/** Moving-pad contacts are recorded environment observations, not static jumps.
 * Controller decisions and the subsequent airborne physics are checked independently. */
final class CapturedPistonReplay {
    static List<ParkourTrajectory.Frame> replay(CapturedParkourWorld world,
            MovementPistonLaunch movement, JsonObject recording) {
        JsonObject group=null;
        for(JsonElement item:recording.getAsJsonArray("groups")) {
            JsonObject candidate=item.getAsJsonObject();
            JsonArray edge=candidate.getAsJsonArray("edge");
            JsonArray from=edge.get(0).getAsJsonArray(),to=edge.get(1).getAsJsonArray();
            if(from.get(0).getAsInt()==movement.getSrc().x && from.get(1).getAsInt()==movement.getSrc().y
                    && from.get(2).getAsInt()==movement.getSrc().z && to.get(0).getAsInt()==movement.getDest().x
                    && to.get(1).getAsInt()==movement.getDest().y && to.get(2).getAsInt()==movement.getDest().z) {
                assertNull("Ambiguous recorded edge",group);
                group=candidate;
            }
        }
        assertNotNull("No recorded dynamic environment for "+movement.getSrc()+" -> "+movement.getDest(),group);
        JsonObject previous=group.getAsJsonObject("before");
        boolean trigger=world.getBlockState(movement.getDest()).getBlock()==Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE;
        List<ParkourTrajectory.Frame> route=new ArrayList<>();
        int airborne=0,pulses=0;
        for(JsonElement entry:group.getAsJsonArray("ticks")) {
            JsonObject actual=entry.getAsJsonObject();
            assertEquals("Discontinuous observation",previous.get("clientTick").getAsInt()+1,actual.get("clientTick").getAsInt());
            ParkourTrajectory.Frame before=frame(world,previous),after=frame(world,actual);
            assertFalse("Recorded test must use walking physics",actual.get("flying").getAsBoolean());
            assertEquals(MovementStatus.RUNNING,movement.observe(before,trigger));
            ParkourTrajectory.Control input=movement.control(before);
            JsonObject applied=actual.getAsJsonObject("input");
            assertEquals(input.forward?1:0,applied.get("forward").getAsDouble(),.00001);
            assertEquals(input.jump,applied.get("jump").getAsBoolean());
            assertEquals(input.sneak,applied.get("sneak").getAsBoolean());
            assertEquals(input.yaw,actual.getAsJsonArray("rotation").get(0).getAsDouble(),.01);
            if(before.y>movement.getSrc().y+1.1 && !before.ground) {
                ParkourTrajectory.Frame predicted=ParkourTrajectory.step(before,input,world.boxes);
                assertEquals("Airborne X tick="+actual.get("clientTick")+" before="+before.x+","+before.y+","+before.z,
                        after.x,predicted.x,.00002);
                assertEquals("Airborne Y",after.y,predicted.y,.00002);
                assertEquals("Airborne Z",after.z,predicted.z,.00002);
                assertEquals("Airborne vertical velocity",after.vy,predicted.vy,.00002);
                assertTrue("Airborne body intersects a solid or hazard",world.safe(predicted.box()));
                airborne++;
            } else if(after.vy>.6 && after.y>movement.getSrc().y+.1) pulses++;
            after.control=input;
            route.add(after);
            previous=actual;
        }
        assertTrue("The recording never observed the moving pad's impulse",pulses>0);
        assertTrue("Insufficient independent airborne physics coverage",airborne>=15);
        assertEquals("Recorded launcher did not land",MovementStatus.SUCCESS,
                movement.observe(route.get(route.size()-1),trigger));
        return route;
    }

    private static ParkourTrajectory.Frame frame(CapturedParkourWorld world,JsonObject row) {
        JsonArray pos=row.getAsJsonArray("positionExact"),motion=row.getAsJsonArray("motion");
        ParkourTrajectory.Frame frame=world.standing(pos.get(0).getAsDouble(),pos.get(1).getAsDouble(),pos.get(2).getAsDouble());
        frame.vx=motion.get(0).getAsDouble();frame.vy=motion.get(1).getAsDouble();frame.vz=motion.get(2).getAsDouble();
        frame.ground=row.get("onGround").getAsBoolean();
        frame.collidedHorizontally=row.get("collidedHorizontally").getAsBoolean();
        if(frame.collidedHorizontally) {
            // The debug log rounds to six decimals. Restore exact full-block
            // contact within that rounding interval, rather than start inside it.
            frame.x=restoreContact(frame.x);frame.z=restoreContact(frame.z);
        }
        frame.bodyHeight=row.get("sneaking").getAsBoolean()?(double)1.65F:(double)1.8F;
        boolean sprint=row.get("sprinting").getAsBoolean();
        for(JsonElement item:row.getAsJsonArray("effects")) {
            assertFalse("This recording has no jump-boost physics",item.getAsString().contains("jump"));
        }
        frame.snapshotMovement(row.get("movementSpeedAttribute").getAsDouble(),sprint,sprint?.026F:.02F,-1,0);
        return frame;
    }

    private static double restoreContact(double coordinate) {
        double half=(double).6F/2;
        double upper=Math.rint(coordinate+half)-half;
        if(Math.abs(upper-coordinate)<.0000005)return upper;
        double lower=Math.rint(coordinate-half)+half;
        return Math.abs(lower-coordinate)<.0000005?lower:coordinate;
    }
}
