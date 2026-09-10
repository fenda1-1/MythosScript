package com.zszl.zszlScriptMod.gui.modern;

/** MCP console geometry, independent of the renderer and game state. */
final class ModernMcpLayout {
    static final int PADDING = 8;
    static final int SPLIT_GAP = 10;
    static final int SIDEBAR_MIN_WIDTH = 156;
    static final int SIDEBAR_FLOOR_WIDTH = 132;
    static final int INSPECTOR_MIN_WIDTH = 240;
    static final int INSPECTOR_FLOOR_WIDTH = 180;

    final ModernMainLayout.Rect header, controls, feeds, sidebar, inspector, divider;
    final boolean stacked, inlineControls;

    ModernMcpLayout(ModernMainLayout.Rect area) {
        this(area, ModernSplitPane.DEFAULT_RATIO);
    }

    ModernMcpLayout(ModernMainLayout.Rect area, double sidebarRatio) {
        int x = area.x + PADDING, y = area.y + PADDING;
        int width = Math.max(1, area.width - PADDING * 2);
        inlineControls = width >= 680;
        stacked = width < 460;
        int headerHeight = inlineControls ? 60 : 86;
        header = new ModernMainLayout.Rect(x, y, width, headerHeight);
        controls = new ModernMainLayout.Rect(inlineControls ? header.right() - 346 : x + 8,
                inlineControls ? y + 9 : y + 35, Math.min(338, width - 16), 22);
        feeds = new ModernMainLayout.Rect(x, header.bottom() + 8, width, 24);
        int bodyY = feeds.bottom() + 8;
        int bodyHeight = Math.max(1, area.bottom() - PADDING - bodyY);
        if (stacked) {
            int listHeight = Math.min(126, Math.max(58, bodyHeight / 3));
            sidebar = new ModernMainLayout.Rect(x, bodyY, width, listHeight);
            inspector = new ModernMainLayout.Rect(x, sidebar.bottom() + 8, width,
                    Math.max(1, bodyHeight - listHeight - 8));
            divider = null;
        } else {
            int gap = Math.min(SPLIT_GAP, Math.max(0, width - 2));
            int splitWidth = Math.max(2, width - gap);
            ModernSplitPane.Split split = ModernSplitPane.calculate(splitWidth, sidebarRatio,
                    SIDEBAR_MIN_WIDTH, INSPECTOR_MIN_WIDTH, SIDEBAR_FLOOR_WIDTH, INSPECTOR_FLOOR_WIDTH);
            sidebar = new ModernMainLayout.Rect(x, bodyY, split.firstWidth, bodyHeight);
            inspector = new ModernMainLayout.Rect(sidebar.right() + gap, bodyY,
                    Math.max(1, width - split.firstWidth - gap), bodyHeight);
            divider = ModernSplitPane.verticalDividerBounds(sidebar.x, sidebar.width, gap, bodyY, bodyHeight);
        }
    }
}
