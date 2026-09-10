package com.zszl.zszlScriptMod.shadowbaritone.pathing.portal;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import net.minecraft.util.EnumFacing;

public final class PortalNodeRef {

    private final BetterBlockPos boundaryAnchor;
    private final EnumFacing boundaryFacing;
    private final EnumFacing travelFacing;
    private final BetterBlockPos barrierA;
    private final BetterBlockPos barrierB;
    private final int entrySourceY;

    public PortalNodeRef(BetterBlockPos boundaryAnchor, EnumFacing boundaryFacing, EnumFacing travelFacing,
            BetterBlockPos barrierA, BetterBlockPos barrierB, int entrySourceY) {
        this.boundaryAnchor = boundaryAnchor;
        this.boundaryFacing = boundaryFacing;
        this.travelFacing = travelFacing;
        this.barrierA = barrierA;
        this.barrierB = barrierB;
        this.entrySourceY = entrySourceY;
    }

    public BetterBlockPos getBoundaryAnchor() {
        return boundaryAnchor;
    }

    public EnumFacing getBoundaryFacing() {
        return boundaryFacing;
    }

    public EnumFacing getTravelFacing() {
        return travelFacing;
    }

    public BetterBlockPos getBarrierA() {
        return barrierA;
    }

    public BetterBlockPos getBarrierB() {
        return barrierB;
    }

    public int getEntrySourceY() {
        return entrySourceY;
    }

    public long longHash() {
        long hash = 1469598103934665603L;
        hash = 1099511628211L * hash + BetterBlockPos.serializeToLong(boundaryAnchor.x, boundaryAnchor.y, boundaryAnchor.z);
        hash = 1099511628211L * hash + boundaryFacing.ordinal();
        hash = 1099511628211L * hash + travelFacing.ordinal();
        hash = 1099511628211L * hash + BetterBlockPos.serializeToLong(barrierA.x, barrierA.y, barrierA.z);
        hash = 1099511628211L * hash + BetterBlockPos.serializeToLong(barrierB.x, barrierB.y, barrierB.z);
        hash = 1099511628211L * hash + entrySourceY;
        return hash;
    }

    @Override
    public int hashCode() {
        return (int) longHash();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PortalNodeRef)) {
            return false;
        }
        PortalNodeRef other = (PortalNodeRef) obj;
        return boundaryAnchor.equals(other.boundaryAnchor)
                && boundaryFacing == other.boundaryFacing
                && travelFacing == other.travelFacing
                && barrierA.equals(other.barrierA)
                && barrierB.equals(other.barrierB)
                && entrySourceY == other.entrySourceY;
    }
}
