package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.GoalBlock;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.PathCalculationResult;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.calc.AStarPathFinder;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.CalculationContext;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.Favoring;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Local sampling diagnostic; timings are CPU-process measurements, not game completion. */
public class ParkourSearchProfileTest {
    @Test public void sampleCapturedGraphs() throws Exception {
        for(String course:new String[]{"snow-10","swamp-60"}) {
            CapturedParkourWorld world=new CapturedParkourWorld(course+"/direct-goal.zip");
            int z=course.equals("snow-10")?-2068:-3290;
            GoalBlock goal=course.equals("snow-10")?new GoalBlock(100,19,z):new GoalBlock(167,18,z);
            for(int iteration=0;iteration<2;iteration++) {
                CalculationContext context=CapturedParkourGraphTest.context(world,goal,new Vec3d(.5,11,z+.5),true);
                Baritone.settings().parkourInputCache.value=false;
                Thread subject=Thread.currentThread();
                Map<String,Integer> counts=new HashMap<>();
                Thread sampler=new Thread(()->{
                    try { while(!Thread.currentThread().isInterrupted()) {
                        Set<String> seen=new HashSet<>();
                        StackTraceElement[] stack=subject.getStackTrace();
                        if(stack.length>0) counts.merge("SELF "+stack[0],1,Integer::sum);
                        for(StackTraceElement frame:stack) if(frame.getClassName().contains("shadowbaritone")
                                && seen.add(frame.getClassName()+"."+frame.getMethodName()))
                            counts.merge(frame.toString(),1,Integer::sum);
                        Thread.sleep(5);
                    }} catch(InterruptedException done) { Thread.currentThread().interrupt(); }
                },"parkour-local-sampler");
                sampler.setDaemon(true);
                if(iteration==1) sampler.start();
                long start=System.nanoTime();
                PathCalculationResult result;
                try { result=new AStarPathFinder(0,11,z,goal,new Favoring(null,context),context).calculate(1,1); }
                finally { sampler.interrupt();sampler.join(); }
                assertTrue(result.getPath().isPresent());
                System.out.println("PROFILE "+course+" iteration="+iteration+" ms="+(System.nanoTime()-start)/1e6
                        +" result="+result.getType()+" route="+result.getPath().get().positions());
                counts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                        .limit(45).forEach(entry->System.out.println(entry.getValue()+" "+entry.getKey()));
            }
        }
    }
}
