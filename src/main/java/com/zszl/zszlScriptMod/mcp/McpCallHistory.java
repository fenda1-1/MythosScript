package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonElement;
import java.util.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Thread-safe, immutable snapshots. Bodies are retained in full, never silently truncated. */
public final class McpCallHistory {
    public static final McpCallHistory INSTANCE = new McpCallHistory(100, 16 * 1024 * 1024);
    public static final class Entry {
        public final long sequence, startedAt, finishedAt;
        public final String requestId, tool, arguments, response;
        public final boolean failed;
        private Entry(long seq, long start, long finish, String id, String tool, String args, String result, boolean failed) {
            this.sequence = seq; this.startedAt = start; this.finishedAt = finish;
            this.requestId = id; this.tool = tool; this.arguments = args; this.response = result; this.failed = failed;
        }
        public boolean pending() { return finishedAt == 0; }
        /** Display adapter only; event rows do not count as MCP calls. */
        public static Entry event(com.google.gson.JsonObject event) {
            long time=event.get("timestampMs").getAsLong();
            return new Entry(event.get("id").getAsLong(),time,time,event.get("sessionId").getAsString(),
                    event.get("group").getAsString()+" / "+event.get("type").getAsString(),GSON.toJson(event),GSON.toJson(event.get("data")),false);
        }
        public long durationMillis() { return Math.max(0, (pending() ? System.currentTimeMillis() : finishedAt) - startedAt); }
        private long bytes() { return 2L * (arguments.length() + response.length()); }
    }
    private final int capacity;
    private final long byteBudget;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
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
        Entry prior = entries.get(id);
        if (prior == null) return; // Cleared or evicted while the call was executing.
        entries.put(id, new Entry(id, prior.startedAt, System.currentTimeMillis(), prior.requestId,
                prior.tool, prior.arguments, GSON.toJson(response), failed));
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
    public synchronized void clear() { entries.clear(); total = failures = 0; version++; }
    public synchronized long version() { return version; }
    public synchronized long total() { return total; }
    public synchronized long failures() { return failures; }
}
