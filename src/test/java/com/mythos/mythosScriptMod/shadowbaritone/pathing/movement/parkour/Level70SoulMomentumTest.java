package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class Level70SoulMomentumTest {
    @Test public void replayManualRunupAndImmediateSoulJump() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("level70/soul-runway.json");
        JsonArray samples;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/parkour/level70/soul-runway-hand-replay.json"), StandardCharsets.UTF_8)) {
            samples = new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("samples");
        }
        JsonObject first = samples.get(0).getAsJsonObject();
        JsonArray pos = first.getAsJsonArray("positionExact");
        ParkourTrajectory.Frame frame = world.standing(pos.get(0).getAsDouble(),pos.get(1).getAsDouble(),pos.get(2).getAsDouble());
        JsonArray motion = first.getAsJsonArray("motion");
        frame.vx=motion.get(0).getAsDouble(); frame.vy=motion.get(1).getAsDouble(); frame.vz=motion.get(2).getAsDouble();
        frame.airAcceleration=first.get("sprinting").getAsBoolean()?.026F:.02F;
        for(int i=1;i<samples.size();i++) {
            JsonObject sample=samples.get(i).getAsJsonObject(), input=sample.getAsJsonObject("input");
            ParkourTrajectory.Control control=new ParkourTrajectory.Control(
                    Math.toRadians(sample.getAsJsonArray("rotation").get(0).getAsDouble()+90),
                    input.get("forward").getAsDouble()>0, sample.get("sprinting").getAsBoolean(),
                    input.get("jump").getAsBoolean(), input.get("sneak").getAsBoolean(), input.get("strafe").getAsInt());
            frame=ParkourTrajectory.step(frame,control,world.boxes);
            pos=sample.getAsJsonArray("positionExact"); motion=sample.getAsJsonArray("motion");
            String tick="tick "+sample.get("clientTick");
            // Log yaw is rounded to .01 degree and coordinates to six decimals.
            assertEquals(tick+" x",pos.get(0).getAsDouble(),frame.x,.003);
            assertEquals(tick+" y",pos.get(1).getAsDouble(),frame.y,.00001);
            assertEquals(tick+" z",pos.get(2).getAsDouble(),frame.z,.003);
            assertEquals(tick+" vx",motion.get(0).getAsDouble(),frame.vx,.001);
            assertEquals(tick+" vy",motion.get(1).getAsDouble(),frame.vy,.00001);
            assertEquals(tick+" vz",motion.get(2).getAsDouble(),frame.vz,.001);
            assertEquals(tick+" grounded",sample.get("onGround").getAsBoolean(),frame.ground);
        }
        assertTrue(frame.ground);
        assertEquals(13,frame.y,.00001);
        assertTrue(frame.x>130);
    }

    @Test public void searchKeepsMomentumFromOrdinaryRunwayAcrossSoulEdge() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/soul-runway.json");
        CapturedParkourGraphTest.context(world,new GoalBlock(130,13,-3756));
        List<List<ParkourTrajectory.Frame>> connection=ParkourTrajectory.searchMomentumConnection(
                world.standing(120.447119,13,-3755.3),world.boxes,new Vec3d(126.5,11.875,-3756.5),
                new Vec3d(130.5,13,-3755.5),true,world::safe);
        assertEquals("Both jumps must be planned together",2,connection.size());
        ParkourTrajectory.Frame handoff=connection.get(0).get(connection.get(0).size()-1);
        assertTrue(handoff.ground);
        assertTrue("Preserve momentum instead of stopping on soul sand",Math.hypot(handoff.vx,handoff.vz)>.2);
        assertTrue(connection.get(1).get(0).control.jump);
        ParkourTrajectory.Frame finish=connection.get(1).get(connection.get(1).size()-1);
        assertTrue(ParkourTrajectory.settledArrival(world.boxes,130.5,13,-3755.5,.18,world::safe).test(finish));
    }

    @Test public void graphSelectsRunwayAndSoulContactAsOneMovement() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/soul-runway.json");
        GoalBlock goal=new GoalBlock(130,13,-3756);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(120.447119,13,-3755.3));
        PathCalculationResult result=new AStarPathFinder(120,13,-3756,goal,new Favoring(null,context),context)
                .calculate(10000,10000);
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        assertTrue(result.getPath().get().movements().stream().anyMatch(move -> move instanceof MovementParkour
                && ((MovementParkour)move).getCandidate().getType()==ParkourJumpType.MOMENTUM_SPRINT));
    }
}
