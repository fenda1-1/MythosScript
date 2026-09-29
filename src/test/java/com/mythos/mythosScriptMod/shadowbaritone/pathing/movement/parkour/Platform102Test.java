package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class Platform102Test {
    @Test public void raisedAndDescendingLowCeilingLandingsHaveRealTrajectories() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("platform-102/platform-102.zip");
        for(Vec3d[] edge:new Vec3d[][]{
                {new Vec3d(11.5,103,-128.5),new Vec3d(11.5,104,-132.5)},
                {new Vec3d(11.5,104,-132.5),new Vec3d(11.5,103,-137.5)}}) {
            Vec3d start=edge[0],end=edge[1];
            CalculationContext context=CapturedParkourGraphTest.context(world,new GoalBlock(end.x,end.y,end.z),start);
            assertTrue(ParkourSurface.lowCeilingCorridor(context,start,end)
                    || ParkourSurface.turnRoute(context,start,end)!=null);
            assertFalse(ParkourSurface.lowCeilingCorridor(context,start,new Vec3d(end.x,start.y+context.maxJumpHeight+.01,end.z)));
            List<ParkourTrajectory.Frame> frames=ParkourTrajectory.searchWithRunup(world.standing(start.x,start.y,start.z),
                    world.boxes,end.x,end.y,end.z,true,world::safe,
                    ParkourTrajectory.settledArrival(world.boxes,end.x,end.y,end.z,.18,world::safe));
            assertFalse("Recorded raised low ceiling landing needs a physical trajectory",frames.isEmpty());
            for(ParkourTrajectory.Frame f:frames) assertTrue(world.safe(f.box()));
            ParkourTrajectory.Frame last=frames.get(frames.size()-1);
            for(int i=0;i<20;i++) {
                last=ParkourTrajectory.step(last,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
                assertTrue(world.safe(last.box()) && last.ground);
                assertEquals(end.y,last.y,.001);
                assertTrue(Math.hypot(last.x-end.x,last.z-end.z)<.18);
            }
            System.out.println("Low ceiling trajectory "+start+" -> "+end+" frames="+frames.size());
        }
    }

    @Test public void completeCapturedCourseNeedsNoFutureAbility() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("platform-102/platform-102.zip");
        GoalBlock goal=new GoalBlock(11,102,-149);
        CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(11.5,102,-118));
        PathCalculationResult result=new AStarPathFinder(11,102,-118,goal,new Favoring(null,context),context)
                .calculate(2000,5000);
        System.out.println("Platform 102 graph="+result.getType()+" "+result.getPath().map(p->p.positions().toString()));
        assertEquals(PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
    }
}
