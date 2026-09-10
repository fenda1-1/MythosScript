package com.zszl.zszlScriptMod.gui.donate;

import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

/** Shared, theme-aware contact cards for both support page entry points. */
public final class DonationContactCard {
    private int scroll;
    private int maxScroll;
    private boolean hovered;

    public void draw(FontRenderer font, int x, int y, int width, int height, int mouseX, int mouseY) {
        hovered = new ModernMainLayout.Rect(x, y, width, height).contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int innerWidth = Math.max(1, width - 24);
        int viewport = Math.max(1, height - 20);
        int contentHeight = content(font, x + 10, 0, innerWidth, false);
        maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.min(scroll, maxScroll);
        ModernUiRenderer.beginClip(new ModernMainLayout.Rect(x + 8, y + 10, Math.max(1, width - 16), viewport));
        try {
            content(font, x + 10, y + 10 - scroll, innerWidth, true);
        } finally {
            ModernUiRenderer.endClip();
        }
        if (maxScroll > 0) {
            int thumb = Math.min(viewport, Math.max(16, viewport * viewport / contentHeight));
            int thumbY = y + 10 + (viewport - thumb) * scroll / maxScroll;
            ModernUiRenderer.drawRoundedRect(x + width - 5, y + 10, 2, viewport, 1, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(x + width - 5, thumbY, 2, thumb, 1, ModernUiRenderer.ACCENT);
        }
    }

    private int content(FontRenderer font, int x, int y, int width, boolean paint) {
        int start = y;
        y = card(font, x, y, width, "contact", true, paint) + 9;
        y = card(font, x, y, width, "opensource", false, paint) + 9;
        y = card(font, x, y, width, "community", false, paint) + 9;
        y = card(font, x, y, width, "thanks", false, paint);
        return y - start;
    }

    private int card(FontRenderer font, int x, int y, int width, String key, boolean hero, boolean paint) {
        int textWidth = Math.max(1, width - 24);
        List<String> title = font.listFormattedStringToWidth(I18n.format("gui.donate.card." + key + ".title"), textWidth);
        List<String> body = font.listFormattedStringToWidth(I18n.format("gui.donate.card." + key + ".body").replace("\\n", "\n"), textWidth);
        int lineHeight = font.FONT_HEIGHT + 4;
        int height = 24 + (title.size() + body.size()) * lineHeight + 5;
        if (paint) {
            ModernUiRenderer.drawSubtlePanel(x, y, width, height, 5,
                    hero ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SHELL_RAISED,
                    hero ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(x + 1, y + 12, 3, height - 24, 1, ModernUiRenderer.ACCENT);
            int textY = y + 12;
            for (String line : title) {
                ModernUiRenderer.drawText(font, line, x + 12, textY, ModernUiRenderer.TEXT, textWidth);
                textY += lineHeight;
            }
            textY += 5;
            for (String line : body) {
                ModernUiRenderer.drawText(font, line, x + 12, textY,
                        hero ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, textWidth);
                textY += lineHeight;
            }
        }
        return y + height;
    }

    public boolean handleMouseWheel(int wheel) {
        if (!hovered || maxScroll == 0 || wheel == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - Integer.signum(wheel) * 28));
        return true;
    }
}
