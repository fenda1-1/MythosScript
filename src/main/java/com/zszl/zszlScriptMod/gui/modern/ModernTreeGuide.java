package com.zszl.zszlScriptMod.gui.modern;

/**
 * Shared tree-guide geometry and rendering for nested modern lists.
 */
public final class ModernTreeGuide {

    public static final int GAP = 6;
    public static final int INDENT = 16;
    public static final int GROUP_HEIGHT = 24;
    public static final int ITEM_HEIGHT = 32;
    public static final int LINE_COLOR = 0xFF8AA0AE;
    public static final int NODE_COLOR = 0xFFD4B45A;

    private static final int STEM_INSET = 8;
    private static final int LINE_THICKNESS = 2;
    private static final int NODE_SIZE = 4;

    private ModernTreeGuide() {
    }

    public static int indent(int depth) {
        return Math.max(0, depth) * INDENT;
    }

    public static int stemX(int originX, int parentDepth) {
        return originX + indent(parentDepth) + STEM_INSET;
    }

    public static ModernMainLayout.Rect row(int originX, int width, int y, int height, int depth) {
        int inset = indent(depth);
        return new ModernMainLayout.Rect(originX + inset, y, Math.max(1, width - inset), Math.max(1, height));
    }

    public static int nextY(int y, int rowHeight) {
        return y + Math.max(1, rowHeight) + GAP;
    }

    public static ModernMainLayout.Rect itemRow(ModernMainLayout.Rect list, int y) {
        return row(list.x, ModernHoverScrollbar.contentWidth(list.width), y, ITEM_HEIGHT, 1);
    }

    public static ModernMainLayout.Rect groupRow(ModernMainLayout.Rect list, int y) {
        return row(list.x, ModernHoverScrollbar.contentWidth(list.width), y, GROUP_HEIGHT, 0);
    }

    public static int stemFromY(ModernMainLayout.Rect parent) {
        return parent == null ? 0 : parent.bottom();
    }

    public static void drawChild(int originX, int parentDepth, ModernMainLayout.Rect parent,
            ModernMainLayout.Rect child) {
        if (parent == null || child == null) {
            return;
        }
        drawElbow(stemX(originX, parentDepth), stemFromY(parent), child.y + child.height / 2, child.x);
    }

    public static void drawElbow(int stemX, int fromY, int childMidY, int childLeft) {
        int top = Math.min(fromY, childMidY);
        int height = Math.max(1, Math.abs(childMidY - fromY));
        ModernUiRenderer.drawRoundedRect(stemX, top, LINE_THICKNESS, height, 1, LINE_COLOR);
        int arm = Math.max(LINE_THICKNESS, childLeft - stemX - 2);
        ModernUiRenderer.drawRoundedRect(stemX, childMidY, arm, LINE_THICKNESS, 1, LINE_COLOR);
        ModernUiRenderer.drawRoundedRect(stemX - NODE_SIZE / 2, childMidY - NODE_SIZE / 2, NODE_SIZE, NODE_SIZE,
                NODE_SIZE / 2, NODE_COLOR);
    }
}
