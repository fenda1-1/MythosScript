package com.zszl.zszlScriptMod.system;

import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

public class BlockReplacementRule {

    public static class BlockReplacementEntry {
        public String sourceBlockId;
        public String targetBlockId;
        public boolean enabled;

        public BlockReplacementEntry() {
            this.sourceBlockId = "";
            this.targetBlockId = "";
            this.enabled = true;
        }
    }

    public String name;
    public String category;
    public boolean enabled;
    public boolean highlightReplacedBlocks;
    public boolean useSolidCollision;
    public Integer corner1X;
    public Integer corner1Y;
    public Integer corner1Z;
    public Integer corner2X;
    public Integer corner2Y;
    public Integer corner2Z;
    public List<BlockReplacementEntry> replacements;

    public transient boolean dirty = true;

    public BlockReplacementRule() {
        this.name = I18n.format("gui.blockreplace.rule.default_name");
        this.category = "默认";
        this.enabled = true;
        this.highlightReplacedBlocks = true;
        this.useSolidCollision = false;
        this.replacements = new ArrayList<>();
    }

    public boolean hasCorner1() {
        return corner1X != null && corner1Y != null && corner1Z != null;
    }

    public boolean hasCorner2() {
        return corner2X != null && corner2Y != null && corner2Z != null;
    }

    public boolean hasValidRegion() {
        return hasCorner1() && hasCorner2();
    }

    public int getMinX() {
        return Math.min(corner1X, corner2X);
    }

    public int getMinY() {
        return Math.min(corner1Y, corner2Y);
    }

    public int getMinZ() {
        return Math.min(corner1Z, corner2Z);
    }

    public int getMaxX() {
        return Math.max(corner1X, corner2X);
    }

    public int getMaxY() {
        return Math.max(corner1Y, corner2Y);
    }

    public int getMaxZ() {
        return Math.max(corner1Z, corner2Z);
    }

    public int getRegionBlockCount() {
        if (!hasValidRegion()) {
            return 0;
        }
        long sizeX = (long) getMaxX() - getMinX() + 1L;
        long sizeY = (long) getMaxY() - getMinY() + 1L;
        long sizeZ = (long) getMaxZ() - getMinZ() + 1L;
        long total = sizeX * sizeY * sizeZ;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public void setCorner1(int x, int y, int z) {
        this.corner1X = x;
        this.corner1Y = y;
        this.corner1Z = z;
        this.dirty = true;
    }

    public void setCorner2(int x, int y, int z) {
        this.corner2X = x;
        this.corner2Y = y;
        this.corner2Z = z;
        this.dirty = true;
    }
}