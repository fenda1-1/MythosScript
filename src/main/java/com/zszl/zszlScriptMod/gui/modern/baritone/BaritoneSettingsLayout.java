package com.zszl.zszlScriptMod.gui.modern.baritone;

/** Card sizing shared by rendering and pagination, independent of Minecraft. */
final class BaritoneSettingsLayout {
    static final int CARD_HEIGHT = 66;
    static final int CARD_GAP = 6;

    private BaritoneSettingsLayout() {
    }

    static int chooseColumns(int width, int preferred) {
        if (preferred > 0) {
            int fitting = Math.max(1, (width - 12 + CARD_GAP) / (150 + CARD_GAP));
            return Math.min(Math.min(preferred, 4), fitting);
        }
        if (width >= 1050) return 4;
        if (width >= 760) return 3;
        return width >= 470 ? 2 : 1;
    }

    static int visibleRows(int height) {
        // Include padding above and below the last complete row.
        return Math.max(1, Math.min(8, (height - 10 + CARD_GAP) / (CARD_HEIGHT + CARD_GAP)));
    }
}
