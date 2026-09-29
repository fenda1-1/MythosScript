package com.mythos.mythosScriptMod.mcp;

/** Client-thread only. A short, ordered lease prevents delayed requests from reviving held keys. */
final class WorldInputLease {
    static final long DURATION_NANOS = 750_000_000L;
    private String owner = "";
    private long sequence = -1, expires;
    private int keys;

    boolean active(long now) { if (!owner.isEmpty() && now >= expires) clear(); return !owner.isEmpty(); }
    boolean owned(String candidate, long now) { return active(now) && owner.equals(candidate); }
    boolean acquire(String candidate, long serial, long now) {
        if (candidate.isEmpty() || candidate.length()>128 || serial<0 || active(now)) return false;
        owner=candidate; sequence=serial; expires=now+DURATION_NANOS;keys=0;return true;
    }
    boolean update(String candidate,long serial,int mask,long now) {
        if(mask<0 || mask>127 || !owned(candidate,now) || serial<=sequence) return false;
        sequence=serial;keys=mask;expires=now+DURATION_NANOS;return true;
    }
    boolean release(String candidate,long serial,long now) {
        if(!owned(candidate,now) || serial<=sequence) return false;
        clear();return true;
    }
    int keys(long now) { return active(now)?keys:0; }
    void clear() { owner="";keys=0;sequence=-1;expires=0; }
    static float forward(int mask) { return axis(mask,1,2); }
    static float strafe(int mask) { return axis(mask,4,8); }
    private static float axis(int mask,int positive,int negative) {
        float value=((mask&positive)!=0?1:0)-((mask&negative)!=0?1:0);
        // Vanilla physics normalizes diagonals. Preserve full forward input for sprint eligibility.
        return (mask&32)!=0 ? value*0.3f:value;
    }
}
