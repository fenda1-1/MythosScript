package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.Assert.*;

public class ParkourSnapshotTest {
    @Test public void hazardSnapshotKeepsLiquidMarginsAndExceptions() throws Exception {
        CapturedParkourWorld world = new CapturedParkourWorld("swamp-nether69/swamp55.zip");
        AxisAlignedBB region = new AxisAlignedBB(8,28,-3057,14,35,-3051);
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(8,28,-3057),new BlockPos(14,35,-3051)))
            world.states.put(p,Blocks.AIR.getDefaultState());
        CalculationContext context = CapturedParkourGraphTest.context(world,new GoalBlock(10,30,-3055),
                new Vec3d(10.5,30,-3054.5),true);
        AxisAlignedBB body = new AxisAlignedBB(10.2,29,-3054.8,10.8,30.8,-3054.2);
        Predicate<AxisAlignedBB> empty = ParkourSurface.safetySnapshot(context,region,null);
        assertTrue(empty.test(body));
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        for (int i=0;i<20_000;i++) assertTrue(empty.test(body));
        long bytes=bean.getThreadAllocatedBytes(id), started=System.nanoTime();
        for (int i=0;i<100_000;i++) assertTrue(empty.test(body));
        System.out.println("empty safety checks=100000 bytes="+(bean.getThreadAllocatedBytes(id)-bytes)
                +" nanos="+(System.nanoTime()-started));

        BlockPos wet = new BlockPos(10,29,-3055);
        world.states.put(wet,Blocks.WATER.getDefaultState());
        Predicate<AxisAlignedBB> water = ParkourSurface.safetySnapshot(context,region,null);
        assertFalse("Liquid must reject full body overlap",water.test(body));
        assertFalse("Liquid must reject edge contact",water.test(body.offset(0,.7,0)));
        assertTrue("Feet above the water plane are safe",water.test(body.offset(0,1.1,0)));
        assertTrue(ParkourSurface.safetySnapshot(context,region,wet).test(body));
        assertTrue(ParkourSurface.safetySnapshot(context,region,null,true).test(body));
        world.states.put(wet,Blocks.LAVA.getDefaultState());
        assertFalse(ParkourSurface.safetySnapshot(context,region,wet,true).test(body));
        // This part of the capture has no solid boxes: isolate liquid contact
        // from the independent solid-collision check in the offline oracle.
        world.states.put(new BlockPos(10,31,-3055),Blocks.LAVA.getDefaultState());
        Predicate<AxisAlignedBB> lava = ParkourSurface.safetySnapshot(context,region,null);
        AxisAlignedBB adjacent = new AxisAlignedBB(9.4,31,-3054.8,10,32.8,-3054.2);
        for (double overlap : new double[]{-.1,0,.00001,.001,.05,.099,.1001,.2}) {
            AxisAlignedBB edge = adjacent.offset(overlap,0,0);
            boolean safe = overlap < .1;
            assertEquals("Lava immersion vs rim damage, overlap="+overlap,safe,ParkourSurface.safeBody(context,edge));
            assertEquals("Snapshot vs lava, overlap="+overlap,safe,lava.test(edge));
            assertEquals("Offline vs lava, overlap="+overlap,safe,world.safe(edge));
        }
        // x=9,y=33 contains a captured solid, so check the upper rim at x=10.
        AxisAlignedBB aboveSurface=body.offset(0,2.92,0);
        assertTrue("Fire damage above the visible surface is allowed without lava immersion",
                ParkourSurface.safeBody(context,aboveSurface));
        assertTrue(lava.test(aboveSurface));
        assertTrue(world.safe(aboveSurface));
        assertFalse("Actual immersion must still be rejected",
                lava.test(adjacent.offset(.2,.5,0)));
        world.states.put(wet,Blocks.FIRE.getDefaultState());
        assertEquals(context.allowFireContact,ParkourSurface.safetySnapshot(context,region,null).test(body));
        world.states.put(wet,Blocks.WEB.getDefaultState());
        assertFalse(ParkourSurface.safetySnapshot(context,region,null).test(body));
        assertTrue("Snapshot must not observe subsequent world edits",empty.test(body));
    }

    @Test public void indexedSneakingKeepsOrderedCollisionPhysics() {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (int x=-8;x<9;x++) for (int z=-8;z<9;z++) {
            if ((x+z)%5!=0) boxes.add(new AxisAlignedBB(x,0,z,x+1,1,z+1));
            if ((x-z)%7==0) boxes.add(new AxisAlignedBB(x,1,z,x+.375,1.5,z+.375));
        }
        List<AxisAlignedBB> indexed = new ParkourTrajectory.CollisionIndex(boxes);
        Random random = new Random(6);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id=Thread.currentThread().getId(), allocated=0, nanos=0;
        for (int i=0;i<20_000;i++) {
            ParkourTrajectory.Frame start = new ParkourTrajectory.Frame();
            start.x=random.nextDouble()*16-8; start.y=1; start.z=random.nextDouble()*16-8;
            start.vy=-.0784; start.ground=true;
            start.vx=random.nextDouble()*.6-.3; start.vz=random.nextDouble()*.6-.3;
            ParkourTrajectory.Control control = new ParkourTrajectory.Control(random.nextDouble()*Math.PI*2,true,false,false,true,0);
            ParkourTrajectory.Frame expected = ParkourTrajectory.step(start,control,boxes);
            long bytes=bean.getThreadAllocatedBytes(id), before=System.nanoTime();
            ParkourTrajectory.Frame actual = ParkourTrajectory.step(start,control,indexed);
            allocated+=bean.getThreadAllocatedBytes(id)-bytes; nanos+=System.nanoTime()-before;
            assertEquals(expected.x,actual.x,0); assertEquals(expected.y,actual.y,0); assertEquals(expected.z,actual.z,0);
            assertEquals(expected.vx,actual.vx,0); assertEquals(expected.vy,actual.vy,0); assertEquals(expected.vz,actual.vz,0);
            assertEquals(expected.ground,actual.ground); assertEquals(expected.collidedHorizontally,actual.collidedHorizontally);
        }
        System.out.println("indexed sneak steps=20000 bytes="+allocated+" nanos="+nanos);
    }
}
