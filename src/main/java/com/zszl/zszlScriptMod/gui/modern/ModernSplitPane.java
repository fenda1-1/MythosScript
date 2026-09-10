package com.zszl.zszlScriptMod.gui.modern;

/** Shared geometry and rendering rules for draggable modern split panes. */
public final class ModernSplitPane {

    public static final int VISUAL_WIDTH = 10;
    public static final int HIT_WIDTH = 24;
    public static final int HORIZONTAL_HIT_HEIGHT = 12;
    public static final double DEFAULT_RATIO = 0.28D;

    private ModernSplitPane() {
    }

    public static Split calculate(int totalWidth, double requestedRatio, int minimumFirst, int minimumSecond,
            int floorFirst, int floorSecond) {
        int safeTotal = Math.max(2, totalWidth);
        int safeFloorFirst = Math.max(1, Math.min(floorFirst, safeTotal - 1));
        int safeFloorSecond = Math.max(1, Math.min(floorSecond, safeTotal - 1));
        if (safeFloorFirst + safeFloorSecond > safeTotal) {
            int first = Math.max(1, (int) Math.round(safeTotal
                    * (safeFloorFirst / (double) Math.max(1, safeFloorFirst + safeFloorSecond))));
            first = Math.min(safeTotal - 1, first);
            return new Split(first, safeTotal - first, first / (double) safeTotal);
        }

        int preferredFirst = Math.max(safeFloorFirst, minimumFirst);
        int preferredSecond = Math.max(safeFloorSecond, minimumSecond);
        int lower = preferredFirst + preferredSecond <= safeTotal ? preferredFirst : safeFloorFirst;
        int upper = safeTotal - (preferredFirst + preferredSecond <= safeTotal ? preferredSecond : safeFloorSecond);
        int requestedFirst = (int) Math.round(safeTotal * clampRatio(requestedRatio));
        int first = clamp(requestedFirst, lower, Math.max(lower, upper));
        return new Split(first, safeTotal - first, first / (double) safeTotal);
    }

    public static Split calculateFromPointer(int totalWidth, int pointerOffset, int minimumFirst, int minimumSecond,
            int floorFirst, int floorSecond) {
        return calculate(totalWidth, pointerOffset / (double) Math.max(1, totalWidth), minimumFirst, minimumSecond,
                floorFirst, floorSecond);
    }

    public static ModernMainLayout.Rect verticalDividerBounds(int firstPaneX, int firstPaneWidth, int gap, int y,
            int height) {
        int centerX = firstPaneX + firstPaneWidth + Math.max(0, gap) / 2;
        return new ModernMainLayout.Rect(centerX - HIT_WIDTH / 2, y, HIT_WIDTH, Math.max(1, height));
    }

    public static ModernMainLayout.Rect horizontalDividerBounds(int firstPaneX, int firstPaneWidth, int firstPaneY,
            int firstPaneHeight, int gap) {
        int bottom = firstPaneY + firstPaneHeight + Math.max(0, gap);
        return new ModernMainLayout.Rect(firstPaneX, bottom - HORIZONTAL_HIT_HEIGHT,
                Math.max(1, firstPaneWidth),
                HORIZONTAL_HIT_HEIGHT);
    }

    public static void drawVerticalDivider(ModernMainLayout.Rect bounds, int mouseX, int mouseY, boolean dragging) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        boolean hovered = bounds.contains(mouseX, mouseY);
        int visualX = bounds.x + Math.max(0, (bounds.width - VISUAL_WIDTH) / 2);
        int accent = dragging ? ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.BORDER_SUBTLE;
        if (hovered || dragging) {
            ModernUiRenderer.drawRoundedRect(bounds.x + 3, bounds.y, Math.max(2, bounds.width - 6), bounds.height,
                    3, 0x443A5262);
        }
        ModernUiRenderer.drawRoundedRect(visualX + VISUAL_WIDTH / 2 - 1, bounds.y + 12, 2,
                Math.max(1, bounds.height - 24), 1, accent);
        int centerY = bounds.y + bounds.height / 2 - 8;
        for (int i = 0; i < 3; i++) {
            ModernUiRenderer.drawRoundedRect(visualX + 2, centerY + i * 7, Math.max(2, VISUAL_WIDTH - 4), 2, 1,
                    accent);
        }
    }

    public static void drawHorizontalDivider(ModernMainLayout.Rect bounds, int mouseX, int mouseY,
            boolean dragging) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        boolean hovered = bounds.contains(mouseX, mouseY);
        int visualY = bounds.y + Math.max(0, (bounds.height - VISUAL_WIDTH) / 2);
        int accent = dragging ? ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.BORDER_SUBTLE;
        if (hovered || dragging) {
            ModernUiRenderer.drawRoundedRect(bounds.x + 3, bounds.y, Math.max(2, bounds.width - 6), bounds.height,
                    3, 0x443A5262);
        }
        ModernUiRenderer.drawRoundedRect(bounds.x + 12, visualY + VISUAL_WIDTH / 2 - 1,
                Math.max(2, bounds.width - 24), 2, 1, accent);
        int centerX = bounds.x + bounds.width / 2 - 8;
        for (int i = 0; i < 3; i++) {
            ModernUiRenderer.drawRoundedRect(centerX + i * 7, bounds.y + 2, 2,
                    Math.max(1, bounds.height - 4), 1, accent);
        }
    }

    public static double clampRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return DEFAULT_RATIO;
        }
        return Math.max(0.05D, Math.min(0.95D, ratio));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Split {
        public final int firstWidth;
        public final int secondWidth;
        public final double ratio;

        private Split(int firstWidth, int secondWidth, double ratio) {
            this.firstWidth = firstWidth;
            this.secondWidth = secondWidth;
            this.ratio = ratio;
        }
    }
}
