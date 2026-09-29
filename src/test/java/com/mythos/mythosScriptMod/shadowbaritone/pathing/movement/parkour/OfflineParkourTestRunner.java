package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Allows the fast runner to isolate Class#method without starting Gradle. */
public final class OfflineParkourTestRunner {
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        CapturedParkourGraphTest.settings();
        boolean passed=true;
        for(String target:args) {
            String[] parts=target.split("#",2);
            Class<?> type=Class.forName(parts[0]);
            Request request=parts.length==2?Request.method(type,parts[1]):Request.aClass(type);
            Result result=new JUnitCore().run(request);
            for(Failure failure:result.getFailures()) System.err.println(failure.getTrace());
            System.out.println(target+": tests="+result.getRunCount()+" failures="+result.getFailureCount()+" ms="+result.getRunTime());
            passed &= result.wasSuccessful();
        }
        if(!passed) System.exit(1);
    }
}
