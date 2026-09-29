package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import net.minecraft.util.math.Vec3d;
import com.mythos.mythosScriptMod.handlers.FlyHandler;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import static org.junit.Assert.*;

/** Data-driven captures use the production trajectory search and strict settled landings. */
public class CapturedParkourSuiteTest {
    @Test public void capturedCases() throws Exception {
        String configured=System.getProperty("parkour.suite");
        org.junit.Assume.assumeNotNull(configured);
        ParkourDebugLog.suppressForTests(true);
        Path suite=Paths.get(configured);
        JsonObject index=CapturedParkourWorld.read(suite);
        JsonArray tests=index.getAsJsonArray("tests"),results=new JsonArray();
        assertTrue("Suite has no tests",tests.size()>0);
        int failures=0;
        for(JsonElement entry:tests) {
            JsonObject test=entry.getAsJsonObject(),result=new JsonObject();
            String selected=System.getProperty("parkour.case");
            if(selected!=null && !selected.equals(test.get("name").getAsString())) continue;
            result.addProperty("name",test.get("name").getAsString());
            JsonArray segments=new JsonArray();result.add("segments",segments);
            int verifiedFrames=0;
            long began=System.nanoTime();
            try {
                Set<String> requested=new HashSet<>();
                if(test.has("regions")) for(JsonElement r:test.getAsJsonArray("regions")) requested.add(r.getAsString());
                List<Path> files=new ArrayList<>();
                for(JsonElement capture:index.getAsJsonArray("captures")) {
                    JsonObject c=capture.getAsJsonObject();
                    if(requested.isEmpty() || requested.contains(c.get("region").getAsString()))
                        files.add(suite.getParent().resolve(c.get("file").getAsString()));
                }
                CapturedParkourWorld world=new CapturedParkourWorld(files);
                result.addProperty("inputCacheEnabled",CapturedParkourGraphTest.settings().parkourInputCache.value);
                if(index.has("effectRefresh")) world.effectRefresh=index.getAsJsonArray("effectRefresh");
                ParkourTrajectory.Frame frame;
                if(test.has("start")) {
                    JsonArray p=test.getAsJsonArray("start");
                    frame=world.standing(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
                    result.addProperty("startMode","explicit-standing-walking");
                    assertTrue("Explicit standing start has no support",ParkourTrajectory.step(frame,
                            new ParkourTrajectory.Control(0,false,false,false),world.boxes).ground);
                } else {
                    frame=world.capturedPlayer();
                    result.addProperty("startMode","captured-player-state");
                }
                assertTrue("Start outside capture, hazardous or obstructed",world.safe(frame.box()));
                long budget=(test.has("budgetMillis")?test.get("budgetMillis").getAsLong():3000)*1_000_000L;
                int ticks=test.has("maxTicks")?test.get("maxTicks").getAsInt():120;
                if(budget<=0 || ticks<=0) throw new IllegalArgumentException("Positive budgetMillis/maxTicks required");
                JsonArray finalGoals=test.getAsJsonArray("goals");
                String mode=test.has("mode")?test.get("mode").getAsString():"trajectory";
                if(!mode.equals("graph") && !mode.equals("trajectory") && !mode.equals("flight"))
                    throw new IllegalArgumentException("Unknown test mode: "+mode);
                result.addProperty("mode",mode);
                JsonArray rejected=new JsonArray();result.add("rejectedSearches",rejected);
                while(true) {
                JsonArray goals=finalGoals;
                CalculationContext graphContext=null;
                List<com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement> moves=null;
                List<Integer> chains=new ArrayList<>();
                if(mode.equals("graph")) {
                    assertEquals("Graph cases specify one final goal; intermediate points must be chosen by A*",1,goals.size());
                    JsonArray end=goals.get(0).getAsJsonArray();
                    GoalBlock destination=new GoalBlock(end.get(0).getAsDouble(),end.get(1).getAsDouble(),end.get(2).getAsDouble());
                    CalculationContext c=CapturedParkourGraphTest.context(world,destination,new Vec3d(frame.x,frame.y,frame.z),
                            index.has("allowVines") && index.get("allowVines").getAsBoolean());
                    graphContext=c;
                    BetterBlockPos start=frame.ground ? ParkourSurface.supportedFeet(c,new Vec3d(frame.x,frame.y,frame.z))
                            : new BetterBlockPos(frame.x,frame.y+.1251,frame.z);
                    long graphBudget=test.has("graphBudgetMillis")?test.get("graphBudgetMillis").getAsLong():5000;
                    long graphStart=System.nanoTime();
                    PathCalculationResult planned=new AStarPathFinder(start.x,start.y,start.z,destination,new Favoring(null,c),c)
                            .calculate(graphBudget,graphBudget);
                    result.addProperty("graphResult",planned.getType().name());
                    result.addProperty("graphMillis",(System.nanoTime()-graphStart)/1e6);
                    assertEquals("A* did not reach the requested final goal",PathCalculationResult.Type.SUCCESS_TO_GOAL,planned.getType());
                    JsonArray graph=new JsonArray();result.add("graph",graph);goals=new JsonArray();
                    moves=planned.getPath().get().movements();
                    for(int movementIndex=0;movementIndex<moves.size();movementIndex++) {
                        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement move=moves.get(movementIndex);
                        assertTrue("Execution cost invalidated "+move.getSrc()+" -> "+move.getDest(),
                                ((com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.Movement)move).calculateCost(c)<1e6);
                        Vec3d target;
                        net.minecraft.util.math.AxisAlignedBB support=ParkourSurface.support(c,move.getDest());
                        net.minecraft.block.Block block=world.getBlockState(move.getDest()).getBlock();
                         boolean ladder=block==net.minecraft.init.Blocks.LADDER || block==net.minecraft.init.Blocks.VINE;
                         boolean water=world.getBlockState(move.getDest()).getMaterial()==net.minecraft.block.material.Material.WATER
                                 || world.getBlockState(move.getDest().up()).getMaterial()==net.minecraft.block.material.Material.WATER;
                         assertTrue("No static support, water or ladder for "+move.getClass().getSimpleName()+" "+move.getDest(),support!=null || ladder || water);
                         if(water) target=new Vec3d(move.getDest().x+.5,move.getDest().y,move.getDest().z+.5);
                         else if(ladder && move instanceof MovementParkour) {
                            Vec3d[] points=((MovementParkour)move).getCandidate().getRoutePoints();
                            target=points[points.length-1];
                        } else if(ladder) {
                            net.minecraft.util.EnumFacing face=block==net.minecraft.init.Blocks.LADDER
                                    ? world.getBlockState(move.getDest()).getValue(net.minecraft.block.BlockLadder.FACING):null;
                            target=new Vec3d(move.getDest().x+.5+(face==null?0:face.getFrontOffsetX()*.06),move.getDest().y,
                                    move.getDest().z+.5+(face==null?0:face.getFrontOffsetZ()*.06));
                            if(!world.safe(world.standing(target.x,target.y,target.z).box()))
                                target=new Vec3d(move.getDest().x+.5,move.getDest().y,move.getDest().z+.5);
                        } else if(move instanceof MovementParkour) target=MovementParkour.standingTarget(c,((MovementParkour)move).getCandidate(),support);
                        else target=ParkourSurface.standPoint(c,move.getDest(),support);
                        if(move instanceof MovementParkour && movementIndex+1<moves.size()
                                && moves.get(movementIndex+1) instanceof MovementPistonLaunch)
                            target=((MovementPistonLaunch)moves.get(movementIndex+1)).triggerLanding();
                        assertNotNull("Invalid selected landing "+move.getDest(),target);
                        JsonArray p=new JsonArray();p.add(target.x);p.add(target.y);p.add(target.z);goals.add(p);
                        JsonObject step=new JsonObject();step.addProperty("movement",move.getClass().getSimpleName());
                        step.addProperty("src",move.getSrc().toString());step.addProperty("dest",move.getDest().toString());
                        step.add("stance",p);graph.add(step);
                        int chain=move instanceof MovementParkour ? MovementParkour.trajectoryChain(c,moves,movementIndex).size():1;
                        chains.add(chain);step.addProperty("momentumFallbackLength",chain);
                    }
                    result.addProperty("coverage","production-astar+movement-assembly+execution-cost+chain-policy+runup-search+settled-handoff");
                }
                if(mode.equals("flight")) {
                    assertEquals("Flight cases specify one final goal",1,goals.size());
                    JsonArray end=goals.get(0).getAsJsonArray();
                    GoalBlock destination=new GoalBlock(end.get(0).getAsDouble(),end.get(1).getAsDouble(),end.get(2).getAsDouble());
                    CalculationContext c=CapturedParkourGraphTest.context(world,destination,new Vec3d(frame.x,frame.y,frame.z),
                            index.has("allowVines") && index.get("allowVines").getAsBoolean());
                    CapturedParkourGraphTest.settings().allowFlightPathing.value=true;
                    boolean flyWasEnabled=FlyHandler.enabled;
                    FlyHandler.enabled=true;
                    try {
                        BetterBlockPos start=new BetterBlockPos(frame.x,frame.y,frame.z);
                        long flightBudget=test.has("graphBudgetMillis")?test.get("graphBudgetMillis").getAsLong():30000;
                        long flightStart=System.nanoTime();
                        PathCalculationResult planned=new AStarPathFinder(start.x,start.y,start.z,destination,new Favoring(null,c),c)
                                .calculate(flightBudget,flightBudget);
                        result.addProperty("graphResult",planned.getType().name());
                        result.addProperty("graphMillis",(System.nanoTime()-flightStart)/1e6);
                        assertEquals("Flight A* did not reach the requested final goal",
                                PathCalculationResult.Type.SUCCESS_TO_GOAL,planned.getType());
                        CapturedParkourFlightTest.assertAirPath(world,c,planned.getPath().get());
                        result.addProperty("flightMoves",planned.getPath().get().movements().size());
                        result.addProperty("coverage","production-astar-flight+air-corridor-validation");
                    } finally {
                        FlyHandler.enabled=flyWasEnabled;
                    }
                    break;
                }
                List<ParkourTrajectory.Frame> pending=null;
                List<List<ParkourTrajectory.Frame>> flowPending=new ArrayList<>();
                boolean replan=false;
                for(int goalIndex=0;goalIndex<goals.size();goalIndex++) {
                    world.refreshEffects(frame);
                    JsonArray p=goals.get(goalIndex).getAsJsonArray();
                    double x=p.get(0).getAsDouble(),y=p.get(1).getAsDouble(),z=p.get(2).getAsDouble();
                     ParkourTrajectory.Frame target=world.standing(x,y,z);
                     boolean waterTarget=target.inWater();
                     java.util.function.Predicate<net.minecraft.util.math.AxisAlignedBB> safe=waterTarget || frame.inWater()
                             ?world::swimmable:world::safe;
                    assertTrue("Goal outside capture, hazardous or obstructed: "+p+" feet="
                            +world.getBlockState(new net.minecraft.util.math.BlockPos(x,y,z))+" head="
                             +world.getBlockState(new net.minecraft.util.math.BlockPos(x,y+1,z)),safe.test(target.box()));
                    boolean ladderTarget=target.onLadder();
                     assertTrue("Goal has no support",waterTarget || ladderTarget || ParkourTrajectory.step(target,
                            new ParkourTrajectory.Control(0,false,false,false),world.boxes).ground);
                     long searchStart=System.nanoTime();
                     final boolean swimmingEdge=mode.equals("graph") && moves.get(goalIndex) instanceof
                             com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim;
                    java.util.function.Predicate<ParkourTrajectory.Frame> arrived=
                              waterTarget ? f->f.inWater() && (swimmingEdge
                                      ? Math.hypot(f.x-x,f.z-z)<.3 && Math.abs(f.y-y-.05)<.18
                                      : MovementParkour.waterArrival(f,x,y,z))
                             : ladderTarget ? f->f.onLadder() && Math.floor(f.y)==Math.floor(y)
                                     && Math.floor(f.x)==Math.floor(x) && Math.floor(f.z)==Math.floor(z)
                                     && Math.abs(f.x-x)<.25 && Math.abs(f.z-z)<.25
                                    && f.control!=null && f.control.sneak && Math.abs(f.vy+.0784000015258789)<.001
                                    && Math.hypot(f.vx,f.vz)<.01
                            : ParkourTrajectory.settledArrival(world.boxes,x,y,z,.18,world::safe);
                    boolean pass=false;
                    boolean flow=false;
                    boolean continuousClimb=mode.equals("graph") && frame.onLadder()
                            && MovementParkour.continuousClimb(graphContext,moves.get(goalIndex).getSrc(),moves.get(goalIndex).getDest());
                    MovementPistonLaunch pistonEntry=mode.equals("graph") && moves.get(goalIndex) instanceof MovementParkour
                            && goalIndex+1<moves.size() && moves.get(goalIndex+1) instanceof MovementPistonLaunch
                            ?(MovementPistonLaunch)moves.get(goalIndex+1):null;
                    List<ParkourTrajectory.Frame> route;
                      if(!continuousClimb && pistonEntry==null && !waterTarget && !frame.inWater() && flowPending.isEmpty() && pending==null && mode.equals("graph")
                            && moves.get(goalIndex) instanceof MovementParkour) {
                        int length=MovementParkour.flowChain(graphContext,moves,goalIndex).size();
                        List<Vec3d> flowGoals=new ArrayList<>();
                        for(int next=goalIndex;next<goalIndex+length;next++) {
                            JsonArray g=goals.get(next).getAsJsonArray();
                            flowGoals.add(new Vec3d(g.get(0).getAsDouble(),g.get(1).getAsDouble(),g.get(2).getAsDouble()));
                        }
                        if(!flowGoals.isEmpty()) flowPending.addAll(ParkourTrajectory.searchFlow(
                                frame.restart(false),world.boxes,flowGoals,true,world::safe));
                    }
                      if(!flowPending.isEmpty()) {
                          route=ParkourTrajectory.replaySupported(frame.restart(false),flowPending.remove(0),world.boxes,safe);
                          flow=true;pass=!flowPending.isEmpty();
                      }
                      else if(pending!=null) {route=ParkourTrajectory.replaySupported(frame.restart(false),pending,world.boxes,safe);pending=null;}
                      else if(continuousClimb) {
                          route=new ArrayList<>();
                          ParkourTrajectory.Frame climbing=frame.restart(false);
                          for(int tick=0;tick<100 && climbing.y<y;tick++) {
                              climbing=ParkourTrajectory.step(climbing,ParkourTrajectory.climbControl(climbing,x,z),world.boxes);
                              assertTrue("Climb left its clear corridor",safe.test(climbing.box()) && climbing.onLadder());
                              route.add(climbing);
                          }
                          assertTrue("Continuous climb did not reach next height",climbing.y>=y);
                          pass=true;
                      }
                      else if(pistonEntry!=null) {
                          route=ParkourTrajectory.search(frame.restart(false),world.boxes,x,y,z,true,.18,110,8_000_000_000L,
                                  body->safe.test(body) && pistonEntry.safeApproach(body),pistonEntry::readyToLaunch);
                          pass=true;
                      }
                     else if(mode.equals("graph") && moves.get(goalIndex) instanceof
                             com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch) {
                         assertTrue("Piston movement requires a recorded dynamic environment",test.has("dynamicReplay"));
                         route=CapturedPistonReplay.replay(world,
                                 (com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementPistonLaunch)moves.get(goalIndex),
                                 CapturedParkourWorld.read(suite.getParent().resolve(test.get("dynamicReplay").getAsString())));
                         result.addProperty("dynamicCoverage","recorded-moving-pad-observations+production-controller+independent-airborne-physics");
                         pass=true;
                     }
                     else if(mode.equals("graph") && moves.get(goalIndex) instanceof
                             com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim) {
                         route=new ArrayList<>();
                         ParkourTrajectory.Frame swimming=frame.restart(false);
                         com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement move=moves.get(goalIndex);
                         for(int tick=0;tick<160;tick++) {
                             swimming=ParkourTrajectory.step(swimming,
                                     com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim.control(
                                             swimming,move.getSrc(),move.getDest()),world.boxes);
                             if(!safe.test(swimming.box())) {route.clear();break;}
                             route.add(swimming);
                             if(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSwim.arrived(
                                     swimming,move.getSrc(),move.getDest())) break;
                         }
                         if(!arrived.test(swimming)) {
                             System.out.println("Swim replay failed "+move.getSrc()+" -> "+move.getDest()
                                     +" start="+frame.x+","+frame.y+","+frame.z+" velocity="+frame.vx+","+frame.vy+","+frame.vz
                                     +" last="+swimming.x+","+swimming.y+","+swimming.z);
                             route.clear();
                         }
                     }
                    else if(mode.equals("graph") && moves.get(goalIndex) instanceof
                            com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSlimeBounce) {
                        route=((com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementSlimeBounce)moves.get(goalIndex))
                                .planRoute(frame.restart(false),world.boxes,world::safe);
                    }
                    else if(mode.equals("graph") && moves.get(goalIndex) instanceof MovementParkour
                            && ((MovementParkour)moves.get(goalIndex)).getCandidate().getType()==ParkourJumpType.MOMENTUM_SPRINT) {
                        Vec3d[] points=((MovementParkour)moves.get(goalIndex)).getCandidate().getRoutePoints();
                        route=ParkourTrajectory.searchMomentumRoute(frame.restart(false),world.boxes,points[1],points[2],true,world::safe);
                    }
                     else if(!waterTarget && !frame.inWater() && mode.equals("graph") && chains.get(goalIndex)>1) {
                        JsonArray next=goals.get(goalIndex+1).getAsJsonArray();
                        List<List<ParkourTrajectory.Frame>> linked=ParkourTrajectory.searchConnection(frame.restart(false),world.boxes,
                                new Vec3d(x,y,z),new Vec3d(next.get(0).getAsDouble(),next.get(1).getAsDouble(),next.get(2).getAsDouble()),true,world::safe);
                        route=linked.isEmpty()?Collections.emptyList():linked.get(0);
                        if(linked.size()>1) {pass=true;pending=linked.get(1);}
                    } else route=mode.equals("graph")
                             ? ParkourTrajectory.searchWithRunup(frame.restart(false),world.boxes,x,y,z,true,safe,arrived)
                             : ParkourTrajectory.search(frame.restart(false),world.boxes,x,y,z,true,.18,ticks,budget,safe,arrived);
                     pass|=waterTarget;
                    JsonObject segment=new JsonObject();segment.add("goal",p);
                    segment.addProperty("searchMillis",(System.nanoTime()-searchStart)/1e6);
                    segment.addProperty("frames",route.size());segments.add(segment);
                    segment.addProperty("immediateHandoff",pass);
                    segment.addProperty("flow",flow);
                    int maxReplans=test.has("maxReplans")?test.get("maxReplans").getAsInt():12;
                    if(route.isEmpty() && mode.equals("graph") && moves.get(goalIndex) instanceof MovementParkour && rejected.size()<maxReplans) {
                        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement failed=moves.get(goalIndex);
                        MovementParkour.rejectUnsearchable(graphContext,failed.getSrc(),failed.getDest());
                        JsonObject rejection=new JsonObject();rejection.addProperty("src",failed.getSrc().toString());
                        rejection.addProperty("dest",failed.getDest().toString());rejection.add("goal",p);
                        rejected.add(rejection);segment.addProperty("rejected",true);
                        System.out.println("Offline replan: "+failed.getSrc()+" -> "+failed.getDest());
                        replan=true;break;
                    }
                    assertFalse("No verified route to "+p,route.isEmpty());
                    JsonArray controls=new JsonArray();
                    for(ParkourTrajectory.Frame step:route) {
                        if (!mode.equals("graph") || !(moves.get(goalIndex) instanceof MovementPistonLaunch)) {
                            assertTrue("Unsafe full player box en route to "+p+": "+step.box(),safe.test(step.box()));
                            verifiedFrames++;
                        }
                        JsonObject c=new JsonObject();
                        net.minecraft.util.math.AxisAlignedBB body=step.box();
                        JsonArray bounds=new JsonArray();
                        for(double value:new double[]{body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ}) bounds.add(value);
                        c.add("body",bounds);
                        c.addProperty("yaw",step.control.yaw);c.addProperty("forward",step.control.forward);
                        c.addProperty("sprint",step.control.sprint);c.addProperty("jump",step.control.jump);
                        c.addProperty("sneak",step.control.sneak);c.addProperty("strafe",step.control.strafe);
                        c.addProperty("resetHorizontal",step.control.resetHorizontal);
                        controls.add(c);
                    }
                    segment.add("controls",controls);
                    frame=route.get(route.size()-1);
                    double heldY=ladderTarget?frame.y:y;
                    for(int hold=0;!pass && hold<20;hold++) {
                        frame=ParkourTrajectory.step(frame,new ParkourTrajectory.Control(0,false,false,false,!flow,0,flow),world.boxes);
                        assertTrue("Landing must remain safe and supported for 20 ticks: "+p+" -> "+frame.x+","+frame.y+","+frame.z,
                                world.safe(frame.box()) && (frame.ground || ladderTarget && frame.onLadder()));
                        assertEquals("Landing fell",heldY,frame.y,.001);
                        assertTrue("Landing drifted outside goal",ladderTarget
                                ? Math.floor(frame.x)==Math.floor(x) && Math.floor(frame.z)==Math.floor(z)
                                : Math.hypot(frame.x-x,frame.z-z)<(flow?.65:.18));
                    }
                }
                if(!replan) break;
                }
                result.addProperty("passed",true);
            } catch(Exception | AssertionError e) {
                e.printStackTrace();
                failures++;result.addProperty("passed",false);result.addProperty("error",e.toString());
            }
            result.addProperty("elapsedMillis",(System.nanoTime()-began)/1e6);
            result.addProperty("verifiedStaticFrames",verifiedFrames);
            results.add(result);
            System.out.println(result.get("name")+": passed="+result.get("passed")+" ms="+result.get("elapsedMillis"));
        }
        assertTrue("No matching test case",results.size()>0);
        JsonObject report=new JsonObject();report.add("tests",results);report.addProperty("failures",failures);
        Files.write(suite.resolveSibling("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("See results.json for each failed case",0,failures);
    }
}
