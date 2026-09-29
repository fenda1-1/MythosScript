package com.mythos.mythosScriptMod.mcp;

import java.util.concurrent.*;

/** Independent of Minecraft's scheduled-task monitor, which mods can hold while waiting for IO. */
public final class ClientRequestQueue {
    private final ArrayBlockingQueue<FutureTask<?>> tasks = new ArrayBlockingQueue<>(64);

    public void submit(FutureTask<?> task) {
        if (!tasks.offer(task)) throw new RejectedExecutionException("Client request queue is full");
    }

    public void remove(FutureTask<?> task) { tasks.remove(task); }

    public void drain() {
        // Bound work per tick even if producers keep submitting.
        for (int i = 0; i < 64; i++) {
            FutureTask<?> task = tasks.poll();
            if (task == null) return;
            task.run();
        }
    }
}
