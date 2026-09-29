package com.mythos.mythosScriptMod.gui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

/** One logical layout for measurement and rasterization at every GUI scale. */
final class UiTextMetrics {
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private UiTextMetrics() {}

    private static TextLayout layout(Font font, String text) {
        float height = font.getLineMetrics("Ag", CONTEXT).getHeight();
        return new TextLayout(text, font.deriveFont(font.getSize2D() * 9.0F / height), CONTEXT);
    }

    static int width(Font font, String text) {
        return text.isEmpty() ? 0 : (int) Math.ceil(layout(font, text).getAdvance());
    }

    static void draw(Graphics2D graphics, Font font, String text, int width, int height, int padding) {
        if (text.isEmpty()) return;
        TextLayout layout = layout(font, text);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(padding, padding);
            g.scale(width / (double) Math.max(1, width(font, text)), height / 9.0D);
            g.setColor(java.awt.Color.WHITE);
            layout.draw(g, 0, layout.getAscent());
        } finally {
            g.dispose();
        }
    }
}
