package com.mythos.mythosScriptMod.system;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Disconnect callbacks must not acquire Minecraft's task lock from the Netty thread. */
public final class DragonDisconnectTasks {
    private static final ConcurrentLinkedQueue<ListenableFutureTask<?>> TASKS = new ConcurrentLinkedQueue<>();
    static { MinecraftForge.EVENT_BUS.register(new DragonDisconnectTasks()); }

    public static ListenableFuture<?> submit(Runnable action) {
        ListenableFutureTask<?> task = ListenableFutureTask.create(action, null);
        TASKS.add(task);
        return task;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (int i = 0; i < 64; i++) {
            ListenableFutureTask<?> task = TASKS.poll();
            if (task == null) return;
            task.run();
        }
    }
}
