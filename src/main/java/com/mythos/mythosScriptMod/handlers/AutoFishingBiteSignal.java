package com.mythos.mythosScriptMod.handlers;

/** Immutable network evidence; independent of client physics and audio playback. */
final class AutoFishingBiteSignal {
    private final int hookId;
    private final long receivedMillis;
    private final double x, y, z;

    AutoFishingBiteSignal(int hookId, long receivedMillis, double x, double y, double z) {
        this.hookId = hookId;
        this.receivedMillis = receivedMillis;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Vanilla bite bursts, not the count=0 approach wakes or count=2 splashes. */
    static boolean isBiteBurst(int count, float xOffset, float yOffset,
                              float zOffset, float speed) {
        return count == 6 && Math.abs(xOffset - 0.25F) < 0.0001F
                && Math.abs(yOffset) < 0.0001F
                && Math.abs(zOffset - 0.25F) < 0.0001F
                && Math.abs(speed - 0.2F) < 0.0001F;
    }

    /** Bubble and wake bursts originate at the same hook in the same server tick. */
    boolean pairsWith(AutoFishingBiteSignal other) {
        return other != null && hookId >= 0 && hookId == other.hookId
                && other.receivedMillis >= receivedMillis
                && other.receivedMillis - receivedMillis <= 100L
                && Math.abs(x - other.x) <= 0.125D
                && Math.abs(y - other.y) <= 0.125D
                && Math.abs(z - other.z) <= 0.125D;
    }

    boolean matches(int currentHookId, long castStartedMillis, long nowMillis,
                    long serverPosX, long serverPosY, long serverPosZ) {
        if (hookId < 0 || hookId != currentHookId
                || receivedMillis < castStartedMillis || receivedMillis > nowMillis
                || nowMillis - receivedMillis > 1000L) return false;
        double dx = x - serverPosX / 4096.0D;
        double dy = y - serverPosY / 4096.0D;
        double dz = z - serverPosZ / 4096.0D;
        // Sound positions are quantized to 1/8 block. Live background captures
        // had a 1.1-block offset even in raw spawn + relative movement tracking.
        // A bounded 2-block radius accepts those cues without a motion fallback.
        return dx * dx + dz * dz <= 4.0D && Math.abs(dy) <= 2.0D;
    }
}
