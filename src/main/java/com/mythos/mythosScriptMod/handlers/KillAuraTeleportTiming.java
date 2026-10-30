package com.mythos.mythosScriptMod.handlers;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owner-thread TP timing. Server combat time must not stretch with bot tick throttling. */
final class KillAuraTeleportTiming {
    private static final long TICK_NANOS = 50_000_000L;
    private static final int MAX_TARGETS = 256;
    private long lastAttackNanos;
    private boolean routeRejected;
    private boolean attacked;
    private final Map<Integer, TargetWindow> targets = new LinkedHashMap<>();

    private static final class TargetWindow {
        int lastHurt;
        long observedUntil;
        long sentUntil;
        boolean observed;
        boolean sent;
    }

    float cooledStrength(float vanilla, float periodTicks, long now, boolean sameWeapon) {
        if (!attacked || !sameWeapon || !(periodTicks > 0F) || !Float.isFinite(periodTicks)) {
            return vanilla;
        }
        return (float) Math.max(0D, Math.min(1D, (now - lastAttackNanos) / (periodTicks * TICK_NANOS)));
    }

    void recordAttack(long now) {
        lastAttackNanos = now;
        attacked = true;
    }

    /** Accept server authority; a rejected route requires an explicit runtime reset. */
    void recordCorrection() {
        routeRejected = true;
    }

    boolean isRouteRejected() {
        return routeRejected;
    }

    boolean targetReady(int entityId, int hurtTicks, int maxHurtTicks, long now) {
        if (isRouteRejected()) {
            return false;
        }
        TargetWindow window = targets.get(entityId);
        if (window == null) {
            window = new TargetWindow();
            targets.put(entityId, window);
            trim();
        }
        // A falling/stale client counter must not restart the same server window.
        if (hurtTicks > maxHurtTicks / 2F) {
            if (!window.observed || hurtTicks > window.lastHurt) {
                window.observedUntil = now + (long) Math.ceil(hurtTicks - maxHurtTicks / 2D) * TICK_NANOS;
            }
            window.observed = true;
        } else {
            window.observed = false;
        }
        window.lastHurt = hurtTicks;
        return (!window.observed || now - window.observedUntil >= 0L)
                && (!window.sent || now - window.sentUntil >= 0L);
    }

    long sentWaitMillis(int entityId, long now) {
        TargetWindow window = targets.get(entityId);
        return window == null || !window.sent ? 0L : Math.max(0L, (window.sentUntil - now) / 1_000_000L);
    }

    long observedWaitMillis(int entityId, long now) {
        TargetWindow window = targets.get(entityId);
        return window == null || !window.observed ? 0L
                : Math.max(0L, (window.observedUntil - now) / 1_000_000L);
    }

    void recordTarget(int entityId, int maxHurtTicks, long now) {
        TargetWindow window = targets.get(entityId);
        if (window == null) {
            window = new TargetWindow();
            targets.put(entityId, window);
            trim();
        }
        window.sent = true;
        window.sentUntil = now + (long) Math.ceil(Math.max(0, maxHurtTicks) / 2D) * TICK_NANOS;
    }

    private void trim() {
        while (targets.size() > MAX_TARGETS) {
            Iterator<Integer> iterator = targets.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    void reset() {
        attacked = false;
        routeRejected = false;
        targets.clear();
    }
}
