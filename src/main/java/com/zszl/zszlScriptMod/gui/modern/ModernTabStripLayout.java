package com.zszl.zszlScriptMod.gui.modern;

/**
 * Geometry for the modern shell tab strip. Kept independent from Minecraft so
 * horizontal/vertical placement and title-hugging widths can be covered by
 * plain JVM tests.
 */
public final class ModernTabStripLayout {

    public enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    public static final int TAB_HEIGHT = 20;
    public static final int TAB_GAP = 4;
    public static final int GEAR_SIZE = 24;
    public static final int STRIP_INSET = 10;
    public static final int STRIP_TOP = 6;
    public static final int TITLE_INSET = 6;
    public static final int DASHBOARD_CHROME = 12;
    public static final int CLOSE_CHROME = 24;
    public static final int CLOSE_BUTTON_SLOT = 17;
    public static final int MIN_TITLE_WIDTH = 20;
    public static final int DEFAULT_FONT_HEIGHT = 9;
    public static final int HORIZONTAL_BODY_GAP = 2;
    public static final int HORIZONTAL_RESIZE_GAP = 10;
    public static final int HORIZONTAL_BAND = STRIP_TOP + TAB_HEIGHT + HORIZONTAL_BODY_GAP
            + HORIZONTAL_RESIZE_GAP;
    public static final int HORIZONTAL_STRIP_HEIGHT = HORIZONTAL_BAND + ModernHoverScrollbar.HORIZONTAL_GUTTER;
    public static final int MIN_HORIZONTAL_TAB_HEIGHT = 16;
    public static final int MAX_HORIZONTAL_TAB_HEIGHT = 72;
    public static final int MIN_HORIZONTAL_BODY_HEIGHT = 80;
    public static final int MIN_VERTICAL_TAB_WIDTH = minVerticalInnerWidth(DEFAULT_FONT_HEIGHT);
    public static final int MAX_VERTICAL_AUTO_WIDTH = 156;
    public static final int MIN_BODY_WIDTH = 80;
    public static final int BODY_GAP = 8;
    public static final int GEAR_TO_TABS = 6;

    private ModernTabStripLayout() {
    }

    public static int tabWidthForTitle(int titleWidth, boolean dashboard) {
        return Math.max(MIN_TITLE_WIDTH, titleWidth) + (dashboard ? DASHBOARD_CHROME : CLOSE_CHROME);
    }

    public static int minVerticalInnerWidth(int fontSize) {
        int size = Math.max(1, fontSize);
        int textMin = Math.max(1, (int) Math.round(size * 1.5D));
        return textMin + CLOSE_CHROME;
    }

    public static int maxVerticalInnerWidth(ModernMainLayout.Rect content) {
        ModernMainLayout.Rect safeContent = content == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : content;
        int maxStrip = Math.max(1, safeContent.width - MIN_BODY_WIDTH - BODY_GAP - STRIP_INSET);
        return Math.max(1, maxStrip - ModernHoverScrollbar.GUTTER);
    }

    public static Layout calculate(ModernMainLayout.Rect content, Orientation orientation, int[] tabWidths) {
        return calculate(content, orientation, tabWidths, -1, MIN_VERTICAL_TAB_WIDTH);
    }

    public static Layout calculate(ModernMainLayout.Rect content, Orientation orientation, int[] tabWidths,
            int requestedInnerWidth, int minInnerWidth) {
        return calculate(content, orientation, tabWidths, requestedInnerWidth, minInnerWidth, -1);
    }

    public static Layout calculate(ModernMainLayout.Rect content, Orientation orientation, int[] tabWidths,
            int requestedInnerWidth, int minInnerWidth, int requestedHorizontalTabHeight) {
        ModernMainLayout.Rect safeContent = content == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : content;
        int[] extents = copyExtents(tabWidths);
        if (orientation == Orientation.VERTICAL) {
            return vertical(safeContent, extents, requestedInnerWidth, minInnerWidth);
        }
        return horizontal(safeContent, extents, requestedHorizontalTabHeight);
    }

    public static int maxHorizontalTabHeight(ModernMainLayout.Rect content) {
        ModernMainLayout.Rect safeContent = content == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : content;
        int roomForTab = safeContent.height - STRIP_TOP - HORIZONTAL_BODY_GAP - HORIZONTAL_RESIZE_GAP
                - ModernHoverScrollbar.HORIZONTAL_GUTTER - MIN_HORIZONTAL_BODY_HEIGHT;
        return Math.max(MIN_HORIZONTAL_TAB_HEIGHT, Math.min(MAX_HORIZONTAL_TAB_HEIGHT, roomForTab));
    }

    public static int horizontalTabHeight(ModernMainLayout.Rect content, int requestedHeight) {
        int maxHeight = maxHorizontalTabHeight(content);
        if (requestedHeight <= 0) {
            return TAB_HEIGHT;
        }
        return clamp(requestedHeight, Math.min(MIN_HORIZONTAL_TAB_HEIGHT, maxHeight), maxHeight);
    }

    private static Layout horizontal(ModernMainLayout.Rect content, int[] extents, int requestedHeight) {
        int tabHeight = horizontalTabHeight(content, requestedHeight);
        ModernMainLayout.Rect strip = new ModernMainLayout.Rect(content.x + STRIP_INSET, content.y + STRIP_TOP,
                Math.max(1, content.width - STRIP_INSET * 2), tabHeight);
        int gearSize = Math.min(GEAR_SIZE, tabHeight);
        ModernMainLayout.Rect gear = new ModernMainLayout.Rect(strip.x, strip.y, gearSize, gearSize);
        int viewportX = strip.x + gearSize + GEAR_TO_TABS;
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(viewportX, strip.y,
                Math.max(1, strip.right() - viewportX), tabHeight);
        int bandHeight = STRIP_TOP + tabHeight + HORIZONTAL_BODY_GAP + HORIZONTAL_RESIZE_GAP
                + ModernHoverScrollbar.HORIZONTAL_GUTTER;
        ModernMainLayout.Rect body = new ModernMainLayout.Rect(content.x, content.y + bandHeight,
                content.width, Math.max(1, content.height - bandHeight));
        return new Layout(Orientation.HORIZONTAL, strip, gear, viewport, body, extents, totalExtent(extents),
                viewport.width, tabHeight);
    }

    private static Layout vertical(ModernMainLayout.Rect content, int[] extents, int requestedInnerWidth,
            int minInnerWidth) {
        int innerWidth = verticalInnerWidth(content, extents, requestedInnerWidth, minInnerWidth);
        int stripWidth = innerWidth + ModernHoverScrollbar.GUTTER;
        ModernMainLayout.Rect strip = new ModernMainLayout.Rect(content.x + STRIP_INSET, content.y + STRIP_TOP,
                stripWidth, Math.max(1, content.height - STRIP_TOP * 2));
        ModernMainLayout.Rect gear = new ModernMainLayout.Rect(strip.x, strip.y, innerWidth, GEAR_SIZE);
        int viewportY = gear.bottom() + TAB_GAP;
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(strip.x, viewportY, innerWidth,
                Math.max(1, strip.bottom() - viewportY));
        int bodyX = strip.right() + BODY_GAP;
        ModernMainLayout.Rect body = new ModernMainLayout.Rect(bodyX, content.y,
                Math.max(1, content.right() - bodyX), content.height);
        int[] verticalExtents = new int[extents.length];
        for (int i = 0; i < verticalExtents.length; i++) {
            verticalExtents[i] = TAB_HEIGHT;
        }
        return new Layout(Orientation.VERTICAL, strip, gear, viewport, body, verticalExtents,
                totalExtent(verticalExtents), innerWidth, TAB_HEIGHT);
    }

    private static int verticalInnerWidth(ModernMainLayout.Rect content, int[] extents, int requestedInnerWidth,
            int minInnerWidth) {
        int maxInner = maxVerticalInnerWidth(content);
        int minInner = Math.max(1, Math.min(minInnerWidth, maxInner));
        if (requestedInnerWidth > 0) {
            return clamp(requestedInnerWidth, minInner, maxInner);
        }
        int maxTab = minInner;
        for (int i = 0; i < extents.length; i++) {
            if (extents[i] > maxTab) {
                maxTab = extents[i];
            }
        }
        int autoCap = Math.min(MAX_VERTICAL_AUTO_WIDTH, maxInner);
        return clamp(maxTab, minInner, Math.max(minInner, autoCap));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int[] copyExtents(int[] tabWidths) {
        if (tabWidths == null || tabWidths.length == 0) {
            return new int[0];
        }
        int[] extents = new int[tabWidths.length];
        for (int i = 0; i < tabWidths.length; i++) {
            extents[i] = Math.max(1, tabWidths[i]);
        }
        return extents;
    }

    private static int totalExtent(int[] extents) {
        if (extents.length == 0) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < extents.length; i++) {
            total += extents[i];
            if (i + 1 < extents.length) {
                total += TAB_GAP;
            }
        }
        return total;
    }

    public static final class Layout {
        public final Orientation orientation;
        public final ModernMainLayout.Rect strip;
        public final ModernMainLayout.Rect gear;
        public final ModernMainLayout.Rect viewport;
        public final ModernMainLayout.Rect body;
        public final int[] tabExtents;
        public final int totalExtent;
        public final int innerWidth;
        public final int tabHeight;

        private Layout(Orientation orientation, ModernMainLayout.Rect strip, ModernMainLayout.Rect gear,
                ModernMainLayout.Rect viewport, ModernMainLayout.Rect body, int[] tabExtents, int totalExtent,
                int innerWidth, int tabHeight) {
            this.orientation = orientation;
            this.strip = strip;
            this.gear = gear;
            this.viewport = viewport;
            this.body = body;
            this.tabExtents = tabExtents;
            this.totalExtent = totalExtent;
            this.innerWidth = innerWidth;
            this.tabHeight = tabHeight;
        }

        public boolean vertical() {
            return orientation == Orientation.VERTICAL;
        }

        public int visibleExtent() {
            return vertical() ? viewport.height : viewport.width;
        }

        public int maxScroll() {
            return Math.max(0, totalExtent - visibleExtent());
        }

        public ModernMainLayout.Rect scrollViewport() {
            if (vertical()) {
                return new ModernMainLayout.Rect(viewport.x, viewport.y,
                        viewport.width + ModernHoverScrollbar.GUTTER, viewport.height);
            }
            return new ModernMainLayout.Rect(viewport.x, viewport.y, viewport.width,
                    viewport.height + ModernHoverScrollbar.HORIZONTAL_GUTTER);
        }

        public ModernMainLayout.Rect tabAt(int index, int scrollOffset) {
            if (index < 0 || index >= tabExtents.length) {
                return new ModernMainLayout.Rect(viewport.x, viewport.y, 1, tabHeight);
            }
            int scroll = Math.max(0, scrollOffset);
            if (vertical()) {
                int y = viewport.y - scroll;
                for (int i = 0; i < index; i++) {
                    y += tabExtents[i] + TAB_GAP;
                }
                return new ModernMainLayout.Rect(viewport.x, y, viewport.width, tabExtents[index]);
            }
            int x = viewport.x - scroll;
            for (int i = 0; i < index; i++) {
                x += tabExtents[i] + TAB_GAP;
            }
            return new ModernMainLayout.Rect(x, viewport.y, tabExtents[index], viewport.height);
        }
    }
}
