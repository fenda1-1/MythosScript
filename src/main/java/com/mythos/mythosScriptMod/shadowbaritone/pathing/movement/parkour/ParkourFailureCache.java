package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Short-lived, world-scoped rejection of jumps whose input search failed. */
public final class ParkourFailureCache {
    private static final class Rejection {
        final long expiry;
        final int attempts;
        Rejection(long expiry, int attempts) {
            this.expiry = expiry;
            this.attempts = attempts;
        }
    }
    private final Map<Object, Map<String, Rejection>> worlds = new WeakHashMap<>();

    public synchronized void reject(Object world, long from, long to) {
        Map<String, Rejection> entries = worlds.computeIfAbsent(world, key -> new LinkedHashMap<>());
        String key = from + ":" + to;
        Rejection old = entries.get(key);
        int attempts = old == null ? 1 : Math.min(4, old.attempts + 1);
        // Exploring several alternative edges can itself take over 30 seconds.
        // Keep failed edges excluded long enough to finish that exploration,
        // with bounded backoff rather than indefinitely repeating the same ring.
        long delay = Math.min(600, 120L << (attempts - 1)) * 1_000_000_000L;
        entries.remove(key);
        entries.put(key, new Rejection(System.nanoTime() + delay, attempts));
        while (entries.size() > 256) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized boolean contains(Object world, long from, long to) {
        Map<String, Rejection> entries = worlds.get(world);
        if (entries == null) return false;
        String key = from + ":" + to;
        Rejection rejection = entries.get(key);
        return rejection != null && System.nanoTime() - rejection.expiry < 0;
    }
}
