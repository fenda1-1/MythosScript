package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import java.util.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

/** Bounded, immutable event timeline. Readers never hold the game thread while waiting. */
public final class McpEventJournal {
    public static final List<String> GROUPS = Collections.unmodifiableList(Arrays.asList(
            "session", "player", "entities", "inventory", "interaction", "input", "gui", "world", "chat"));
    public static final McpEventJournal INSTANCE = new McpEventJournal(12000, 16 * 1024 * 1024);
    private final int capacity;
    private final long budget;
    private final Deque<JsonObject> entries = new ArrayDeque<>();
    private long nextId, evictedThrough, bytes;
    private String sessionId = UUID.randomUUID().toString();
    private boolean connected;
    public McpEventJournal(int capacity, long budget) { this.capacity = capacity; this.budget = budget; }
    public synchronized String sessionId() { return sessionId; }
    public synchronized long version() { return nextId; }
    public synchronized void begin() {
        sessionId = UUID.randomUUID().toString(); connected = true;
        entries.clear(); bytes = 0; evictedThrough = nextId; notifyAll();
    }
    public synchronized void end() { connected = false; notifyAll(); }
    public synchronized void wake() { notifyAll(); }
    public synchronized void append(String group, String type, long tick, JsonObject data) {
        if (!GROUPS.contains(group)) throw new IllegalArgumentException("Unknown event group: " + group);
        JsonObject e = object("id", ++nextId, "timestampMs", System.currentTimeMillis(), "tick", tick,
                "sessionId", sessionId, "group", group, "type", type, "data", copy(data));
        long size = size(e);
        // Never retain a partial event. Oversized events leave an explicit gap.
        if (size > budget) {
            entries.clear(); bytes = 0; evictedThrough = nextId;
        } else {
            entries.addLast(e); bytes += size;
            while (entries.size() > capacity || bytes > budget) {
                JsonObject old = entries.removeFirst(); bytes -= size(old); evictedThrough = old.get("id").getAsLong();
            }
        }
        notifyAll();
    }
    private static long size(JsonObject e) { return 2L * e.toString().length(); }
    public static long number(JsonObject p, String key, long fallback) {
        if (!p.has(key)) return fallback;
        try {
            long v = p.get(key).getAsBigDecimal().longValueExact();
            if (v < 0) throw new IllegalArgumentException();
            return v;
        } catch (Exception e) { throw new IllegalArgumentException(key + " must be a nonnegative integer"); }
    }
    public static Set<String> strings(JsonObject p, String key) {
        Set<String> result = new LinkedHashSet<>();
        if (p.has(key)) {
            if (!p.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
            if (p.getAsJsonArray(key).size() > 256) throw new IllegalArgumentException(key + " has too many entries");
            for (JsonElement v : p.getAsJsonArray(key)) result.add(v.getAsString());
        }
        return result;
    }
    public static void validateFilter(JsonObject p) {
        Set<String> groups = strings(p, "groups");
        if (!GROUPS.containsAll(groups)) throw new IllegalArgumentException("groups must use IDs from groups operation");
        strings(p, "types"); strings(p, "entityIds"); strings(p, "watchIds");
        number(p, "afterId", 0); number(p, "fromMs", 0); number(p, "toMs", Long.MAX_VALUE);
        if (number(p, "fromMs", 0) > number(p, "toMs", Long.MAX_VALUE)) throw new IllegalArgumentException("fromMs exceeds toMs");
        if (p.has("min") != p.has("max")) throw new IllegalArgumentException("min and max must be supplied together");
        if (p.has("min")) {
            double[] lo = vector(p, "min"), hi = vector(p, "max");
            for (int i = 0; i < 3; i++) if (lo[i] > hi[i]) throw new IllegalArgumentException("min exceeds max");
        }
    }
    public static double[] vector(JsonObject p, String key) {
        JsonArray a = p.getAsJsonArray(key);
        if (a.size() != 3) throw new IllegalArgumentException(key + " must contain x,y,z");
        double[] v = new double[3];
        for (int i=0;i<3;i++) { v[i]=a.get(i).getAsDouble(); if (!Double.isFinite(v[i])) throw new IllegalArgumentException("Nonfinite coordinate"); }
        return v;
    }
    public static boolean matches(JsonObject e, JsonObject p) {
        return new Filter(p).matches(e);
    }
    private static final class Filter {
        final Set<String> groups,types,ids,watchIds;
        final long from,to;
        final String query;
        final double[] lo,hi;
        Filter(JsonObject p) {
            groups=strings(p,"groups");types=strings(p,"types");ids=strings(p,"entityIds");watchIds=strings(p,"watchIds");
            from=number(p,"fromMs",0);to=number(p,"toMs",Long.MAX_VALUE);
            query=p.has("query")?string(p,"query").toLowerCase(Locale.ROOT):null;
            lo=p.has("min")?vector(p,"min"):null;hi=p.has("max")?vector(p,"max"):null;
        }
        boolean matches(JsonObject e) {
        if (!groups.isEmpty() && !groups.contains(e.get("group").getAsString())) return false;
        if (!types.isEmpty() && !types.contains(e.get("type").getAsString())) return false;
        long time = e.get("timestampMs").getAsLong();
        if (time < from || time > to) return false;
        JsonObject d = e.getAsJsonObject("data");
        if (!watchIds.isEmpty() && Collections.disjoint(watchIds,strings(d,"watchIds"))) return false;
        if (!ids.isEmpty()) {
            boolean match = false;
            for (String key : Arrays.asList("entityId", "targetId", "attackerId", "collectorId", "itemEntityId", "playerId"))
                if (d.has(key) && !d.get(key).isJsonNull() && ids.contains(d.get(key).getAsString())) match = true;
            if (!match) return false;
        }
        if (query!=null && !d.toString().toLowerCase(Locale.ROOT).contains(query)) return false;
        if (lo!=null) {
            if (!d.has("pos")) return false;
            double[] v = vector(d, "pos");
            for (int i=0;i<3;i++) if (v[i]<lo[i] || v[i]>hi[i]) return false;
        }
        return true;
        }
    }
    public synchronized JsonObject read(JsonObject p) {
        validateFilter(p);
        int limit = integer(p,"limit",100,1,500);
        boolean changed = p.has("sessionId") && !sessionId.equals(string(p,"sessionId"));
        long after = changed ? 0 : number(p,"afterId",0);
        List<JsonObject> found = new ArrayList<>();
        Filter filter=new Filter(p);
        for (JsonObject e : entries) if (e.get("id").getAsLong()>after && filter.matches(e)) found.add(e);
        int start = p.has("afterId") || changed ? 0 : Math.max(0, found.size()-limit);
        int end = Math.min(found.size(),start+limit);
        JsonArray out = new JsonArray();
        for (int i=start;i<end;i++) out.add(copy(found.get(i)));
        boolean more = end < found.size();
        return object("sessionId",sessionId,"connected",connected,"events",out,
                "nextAfterId",more ? found.get(end-1).get("id").getAsLong() : nextId,
                "latestId",nextId,"hasMore",more,"sessionChanged",changed,
                "cursorExpired",p.has("afterId") && after<evictedThrough,
                "cursorAhead",p.has("afterId") && after>nextId,"oldestRetainedId",entries.isEmpty()?null:entries.peekFirst().get("id"),
                "retainedCount",entries.size(),"capacity",capacity,"retainedBytes",bytes);
    }
    public synchronized JsonObject await(JsonObject p, int millis, java.util.function.BooleanSupplier active) throws InterruptedException {
        long deadline = System.nanoTime() + millis * 1000000L;
        for (;;) {
            JsonObject r = read(p);
            if (!active.getAsBoolean()) throw new IllegalStateException("MCP service stopped or restarted");
            if (millis==0 || r.getAsJsonArray("events").size()>0 || !connected || r.get("sessionChanged").getAsBoolean()
                    || r.get("cursorExpired").getAsBoolean() || r.get("cursorAhead").getAsBoolean()) return r;
            long remaining = deadline-System.nanoTime();
            if (remaining<=0) return r;
            wait(Math.max(1,Math.min(remaining/1000000L,250)));
        }
    }
}
