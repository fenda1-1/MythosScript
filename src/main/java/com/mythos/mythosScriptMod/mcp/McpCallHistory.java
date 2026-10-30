package com.mythos.mythosScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import static com.mythos.mythosScriptMod.mcp.McpJson.*;

/** Thread-safe, immutable snapshots. Bodies are retained in full, never silently truncated. */
public final class McpCallHistory {
    public static final McpCallHistory INSTANCE = new McpCallHistory(100, 16 * 1024 * 1024);
    public static final class Entry {
        public final long sequence, startedAt, finishedAt;
        public final String requestId, tool, arguments, response;
        public final boolean failed;
        /** Wall ms plus game-thread cpu/alloc when the executing client reported them. */
        public final long cpuNanos, allocatedBytes;
        private Entry(long seq, long start, long finish, String id, String tool, String args, String result, boolean failed) {
            this(seq, start, finish, id, tool, args, result, failed, -1L, -1L);
        }
        private Entry(long seq, long start, long finish, String id, String tool, String args, String result,
                boolean failed, long cpuNanos, long allocatedBytes) {
            this.sequence = seq; this.startedAt = start; this.finishedAt = finish;
            this.requestId = id; this.tool = tool; this.arguments = args; this.response = result;
            this.failed = failed; this.cpuNanos = cpuNanos; this.allocatedBytes = allocatedBytes;
        }
        public boolean pending() { return finishedAt == 0; }
        /** Display adapter only; event rows do not count as MCP calls. */
        public static Entry event(com.google.gson.JsonObject event) {
            long time=event.get("timestampMs").getAsLong();
            return new Entry(event.get("id").getAsLong(),time,time,event.get("sessionId").getAsString(),
                    event.get("group").getAsString()+" / "+event.get("type").getAsString(),GSON.toJson(event),GSON.toJson(event.get("data")),false);
        }
        public long durationMillis() { return Math.max(0, (pending() ? System.currentTimeMillis() : finishedAt) - startedAt); }
        public long cpuMillis() { return cpuNanos < 0 ? -1 : cpuNanos / 1000000L; }
        private long bytes() { return 2L * (arguments.length() + response.length()); }
    }

    /** Running per-tool aggregate, sorted by total cpu on export. */
    public static final class ToolStats {
        public long calls, failures, totalMillis, totalCpuNanos, totalAllocated, maxCpuNanos, maxAllocated;
        void add(Entry e) {
            calls++;
            if (e.failed) failures++;
            totalMillis += e.durationMillis();
            if (e.cpuNanos >= 0) { totalCpuNanos += e.cpuNanos; if (e.cpuNanos > maxCpuNanos) maxCpuNanos = e.cpuNanos; }
            if (e.allocatedBytes >= 0) { totalAllocated += e.allocatedBytes; if (e.allocatedBytes > maxAllocated) maxAllocated = e.allocatedBytes; }
        }
        JsonObject json(String tool) {
            return object("tool", tool, "calls", calls, "failures", failures,
                    "totalMillis", totalMillis,
                    "totalCpuMillis", totalCpuNanos / 1000000L,
                    "avgCpuMillis", calls == 0 ? 0 : totalCpuNanos / 1000000L / calls,
                    "maxCpuMillis", maxCpuNanos / 1000000L,
                    "totalAllocatedBytes", totalAllocated,
                    "avgAllocatedBytes", calls == 0 ? 0 : totalAllocated / calls,
                    "maxAllocatedBytes", maxAllocated);
        }
    }

    private final int capacity;
    private final long byteBudget;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
    private final Map<String, ToolStats> stats = new HashMap<>();
    private long nextId, version, total, failures;
    public McpCallHistory(int capacity, long byteBudget) {
        this.capacity = Math.max(1, capacity); this.byteBudget = Math.max(1, byteBudget);
    }
    public synchronized long begin(JsonElement requestId, String tool, JsonElement arguments) {
        long id = ++nextId;
        entries.put(id, new Entry(id, System.currentTimeMillis(), 0, String.valueOf(requestId),
                tool, GSON.toJson(arguments), "", false));
        total++; version++; trim();
        return id;
    }
    public synchronized void finish(long id, JsonElement response, boolean failed) {
        finish(id, response, failed, -1L, -1L);
    }
    public synchronized void finish(long id, JsonElement response, boolean failed, long cpuNanos, long allocatedBytes) {
        Entry prior = entries.get(id);
        if (prior == null) return; // Cleared or evicted while the call was executing.
        Entry done = new Entry(id, prior.startedAt, System.currentTimeMillis(), prior.requestId,
                prior.tool, prior.arguments, GSON.toJson(response), failed, cpuNanos, allocatedBytes);
        entries.put(id, done);
        ToolStats s = stats.get(done.tool);
        if (s == null) stats.put(done.tool, s = new ToolStats());
        s.add(done);
        if (failed) failures++;
        version++; trim();
    }
    private void trim() {
        long bytes = 0;
        for (Entry e : entries.values()) bytes += e.bytes();
        Iterator<Entry> iterator = entries.values().iterator();
        // One oversized newest entry is kept whole so the user can inspect/copy its entire response.
        while (entries.size() > 1 && (entries.size() > capacity || bytes > byteBudget) && iterator.hasNext()) {
            bytes -= iterator.next().bytes(); iterator.remove();
        }
    }
    public synchronized List<Entry> snapshot() {
        List<Entry> result = new ArrayList<>(entries.values()); Collections.reverse(result); return result;
    }
    /** Per-tool aggregates plus live JVM heap, cpu-total first. */
    public synchronized JsonObject statsJson() {
        List<Map.Entry<String, ToolStats>> rows = new ArrayList<>(stats.entrySet());
        Collections.sort(rows, new Comparator<Map.Entry<String, ToolStats>>() {
            @Override public int compare(Map.Entry<String, ToolStats> a, Map.Entry<String, ToolStats> b) {
                return Long.compare(b.getValue().totalCpuNanos, a.getValue().totalCpuNanos);
            }
        });
        JsonArray tools = new JsonArray();
        for (Map.Entry<String, ToolStats> row : rows) tools.add(row.getValue().json(row.getKey()));
        return object("tools", tools, "totals", object("calls", total, "failures", failures),
                "jvm", McpPerf.heap(),
                "note", "cpuMillis is game-thread cpu per executing client; allocatedBytes is heap allocated by the executing thread. mythos_clients/hub calls have no per-call sample.");
    }
    public synchronized void clear() { entries.clear(); stats.clear(); total = failures = 0; version++; }
    public synchronized long version() { return version; }
    public synchronized long total() { return total; }
    public synchronized long failures() { return failures; }
}
