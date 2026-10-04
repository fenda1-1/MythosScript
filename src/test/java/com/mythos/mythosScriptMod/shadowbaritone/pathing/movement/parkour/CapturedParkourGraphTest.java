package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.*;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.*;
import net.minecraft.world.border.WorldBorder;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Production A*, candidates and movement assembly against a recording; no game process. */
public class CapturedParkourGraphTest {
    private static <T> T unavailable(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getName().equals("toString")) return "headless-"+type.getSimpleName();
            throw new AssertionError("Unexpected live-client dependency: "+m);
        }));
    }
    static CalculationContext context(CapturedParkourWorld world, GoalBlock goal) throws Exception {
        return context(world,goal,new Vec3d(43.07499998807907,25,-3757.7024973181838));
    }
    static CalculationContext context(CapturedParkourWorld world, GoalBlock goal, Vec3d start) throws Exception {
        return context(world,goal,start,false);
    }
    static Settings settings() throws Exception {
        ParkourDebugLog.suppressForTests(true);
        Constructor<Settings> constructor=Settings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Settings settings=constructor.newInstance();
        settings.chatDebug.value=false;
        settings.parkourMode.value=true;
        settings.parkourInputCache.value=false;
        settings.allowFlightPathing.value=false;
        settings.allowBreak.value=false;
        settings.allowPlace.value=false;
        Field configured=BaritoneAPI.class.getDeclaredField("settings");
        configured.setAccessible(true);configured.set(null,settings);
        Field provider=BaritoneAPI.class.getDeclaredField("provider");
        provider.setAccessible(true);provider.set(null,unavailable(IBaritoneProvider.class));
        return settings;
    }
    static CalculationContext context(CapturedParkourWorld world, GoalBlock goal, Vec3d start, boolean vines) throws Exception {
        return context(world,goal,start,vines,true);
    }
    static CalculationContext context(CapturedParkourWorld world, GoalBlock goal, Vec3d start, boolean vines,
            boolean parkour) throws Exception {
        Settings s=settings();
        s.allowVines.value=vines;
        s.parkourMode.value=parkour;
        IPlayerContext player=unavailable(IPlayerContext.class);
        IBaritone owner=(IBaritone)Proxy.newProxyInstance(IBaritone.class.getClassLoader(),
                new Class<?>[]{IBaritone.class},(p,m,a)->{
                    if(m.getName().equals("getPlayerContext")) return player;
                    throw new AssertionError("Unexpected live owner call: "+m);
                });
        Set<Long> columns=new HashSet<>();
        Map<BlockPos,List<AxisAlignedBB>> shapes=new HashMap<>();
        for(BlockPos pos:world.states.keySet()) columns.add(((long)pos.getX()<<32) ^ (pos.getZ()&0xffffffffL));
        for(AxisAlignedBB box:world.boxes) shapes.computeIfAbsent(new BlockPos(box.minX,box.minY,box.minZ),
                ignored->new ArrayList<>()).add(box);
        BlockStateInterface blocks=new BlockStateInterface(world,(x,z)->columns.contains(((long)x<<32)^(z&0xffffffffL)),new WorldBorder());
        return new CalculationContext(owner,goal,blocks,(pos,area)->{
            List<AxisAlignedBB> result=new ArrayList<>();
            for(AxisAlignedBB box:shapes.getOrDefault(pos,Collections.emptyList()))
                if(box.intersects(area)) result.add(box);
            return result;
        },world.standing(start.x,start.y,start.z).jumpHeight(),start,world.allowFireContact,world);
    }

    @Test public void actualPlannerCanLeaveTheCapturedOuterFence() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/exit-fence.json");
        GoalBlock goal=new GoalBlock(44,25,-3757);
        CalculationContext c=context(world,goal);
        BetterBlockPos start=new BetterBlockPos(43,25,-3758);
        PathCalculationResult result=new AStarPathFinder(start.x,start.y,start.z,goal,new Favoring(null,c),c).calculate(2000,5000);
        System.out.println("A* result "+result.getType()+" "+result.getPath().map(p->p.positions().toString()).orElse("no path"));
        assertEquals("A* must connect the outer rim without walking through the fence",PathCalculationResult.Type.SUCCESS_TO_GOAL,result.getType());
        ParkourTrajectory.Frame frame=world.standing(c.playerPosition.x,c.playerPosition.y,c.playerPosition.z);
        for(com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement movement:result.getPath().get().movements()) {
            assertTrue("This exposed rim needs a simulated parkour movement, not centre-directed walking",movement instanceof MovementParkour);
            MovementParkour move=(MovementParkour)movement;
            assertTrue("Execution-time cost refresh must preserve the selected side",move.calculateCost(c)<1e6);
            ParkourJumpCandidate candidate=move.getCandidate();
            Vec3d target=MovementParkour.standingTarget(c,candidate,ParkourSurface.support(c,move.getDest()));
            assertNotNull("Executor must preserve a physically valid selected stance",target);
            assertEquals(candidate.getRoutePoints()[candidate.getRoutePoints().length-1],target);
            List<ParkourTrajectory.Frame> route=ParkourTrajectory.search(frame,world.boxes,target.x,target.y,target.z,
                    true,.18,120,3_000_000_000L,world::safe,f->f.ground && Math.abs(f.y-target.y)<.001
                            && Math.hypot(f.x-target.x,f.z-target.z)<.18 && Math.hypot(f.vx,f.vz)<.025);
            assertFalse("A* selected an unexecutable edge "+move.getSrc()+" -> "+target,route.isEmpty());
            frame=route.get(route.size()-1);
            for(int tick=0;tick<20;tick++) {
                frame=ParkourTrajectory.step(frame,new ParkourTrajectory.Control(0,false,false,false,true,0),world.boxes);
                assertTrue("Handoff must remain supported without crossing the fence",world.safe(frame.box()) && frame.ground
                        && Math.abs(frame.y-target.y)<.001 && Math.hypot(frame.x-target.x,frame.z-target.z)<.18);
            }
            System.out.println("executed "+move.getSrc()+" -> "+target+" frames="+route.size());
        }
    }

    @Test public void cachedStancesMustPreserveEveryExposedPoseAndRefreshGeometry() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/exit-fence.json");
        GoalBlock goal=new GoalBlock(44,25,-3757);
        CalculationContext c=context(world,goal);
        BetterBlockPos feet=new BetterBlockPos(43,25,-3758);
        AxisAlignedBB support=ParkourSurface.support(c,feet);
        assertNotNull(support);
        List<Vec3d> cached=ParkourSurface.standPoints(c,feet,support);
        java.lang.reflect.Method fresh=ParkourSurface.class.getDeclaredMethod("findStandPoints",
                CalculationContext.class,BetterBlockPos.class,AxisAlignedBB.class);
        fresh.setAccessible(true);
        assertFalse(cached.isEmpty());
        assertEquals(fresh.invoke(null,c,feet,support),cached);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++)
            world.boxes.add(new AxisAlignedBB(feet.add(dx,0,dz)));
        assertTrue("Execution's fresh context must reject geometry that changed after planning",
                ParkourSurface.standPoints(context(world,goal),feet,support).isEmpty());
    }
}
