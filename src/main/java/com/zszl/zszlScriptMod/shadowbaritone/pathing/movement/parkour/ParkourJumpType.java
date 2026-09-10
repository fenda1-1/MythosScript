package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.parkour;

public enum ParkourJumpType {
    FLAT(false, false, false),
    FLAT_SPRINT(true, false, false),
    EDGE(false, false, false),
    CHAIN(false, false, false),
    CHAIN_SPRINT(true, false, false),
    ASCEND(true, false, false),
    ASCEND_ANGLED(true, true, false),
    ANGLED(false, true, false),
    ANGLED_SPRINT(true, true, false),
    NARROW(false, false, true);

    private final boolean requiresSprint;
    private final boolean angled;
    private final boolean narrowLanding;

    ParkourJumpType(boolean requiresSprint, boolean angled, boolean narrowLanding) {
        this.requiresSprint = requiresSprint;
        this.angled = angled;
        this.narrowLanding = narrowLanding;
    }

    public boolean requiresSprint() {
        return requiresSprint;
    }

    public boolean isAngled() {
        return angled;
    }

    public boolean isNarrowLanding() {
        return narrowLanding;
    }
}
