package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/**
 * Per-call resource sampler. Thread cpu time plus HotSpot thread allocation
 * counters; degrades silently on JVMs without com.sun.management.
 */
public final class McpPerf {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final Method ALLOCATED_BYTES = findAllocatedBytes();

    private static Method findAllocatedBytes() {
        try {
            Method m = THREADS.getClass().getMethod("getThreadAllocatedBytes", long.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static McpPerf start() { return new McpPerf(); }

    private final long threadId = Thread.currentThread().getId();
    private final long cpuStart = safeCpu(threadId);
    private final long allocStart = safeAlloc(threadId);
    private final long wallStart = System.nanoTime();

    private static long safeCpu(long id) {
        try { return THREADS.isThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled()
                ? THREADS.getThreadCpuTime(id) : -1L; }
        catch (Throwable t) { return -1L; }
    }
    private static long safeAlloc(long id) {
        if (ALLOCATED_BYTES == null) return -1L;
        try { Object v = ALLOCATED_BYTES.invoke(THREADS, id); return v instanceof Long ? (Long) v : -1L; }
        catch (Throwable t) { return -1L; }
    }

    /** Snapshot of this sample's deltas plus live JVM heap usage. */
    public JsonObject finish() {
        long cpu = safeCpu(threadId), alloc = safeAlloc(threadId);
        JsonObject out = object("wallNanos", Math.max(0L, System.nanoTime() - wallStart));
        if (cpuStart >= 0 && cpu >= cpuStart) out.addProperty("cpuNanos", cpu - cpuStart);
        if (allocStart >= 0 && alloc >= allocStart) out.addProperty("allocatedBytes", alloc - allocStart);
        out.add("heap", heap());
        return out;
    }

    public static JsonObject heap() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        return object("heapUsedBytes", heap.getUsed(), "heapCommittedBytes", heap.getCommitted(),
                "heapMaxBytes", heap.getMax(), "nonHeapUsedBytes", nonHeap.getUsed());
    }
}
