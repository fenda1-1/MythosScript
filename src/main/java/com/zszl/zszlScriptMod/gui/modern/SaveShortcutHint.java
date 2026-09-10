package com.zszl.zszlScriptMod.gui.modern;

import java.util.Locale;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.system.BindableAction;
import com.zszl.zszlScriptMod.system.KeybindManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

/** Live binding hint, used only by button renderers, never general text. */
public final class SaveShortcutHint {
    private SaveShortcutHint() { }

    public static boolean isSaveLabel(String label) {
        String text = TextFormatting.getTextWithoutFormattingCodes(ModernFormI18n.tr(label == null ? "" : label));
        if (text == null) return false;
        text = text.replace("*", "").trim().toLowerCase(Locale.ROOT);
        return text.equals("保存") || text.equals("保存配置") || text.equals("保存修改")
                || text.equals("保存更改") || text.equals("保存全部") || text.equals("全部保存")
                || text.equals("save") || text.equals("save changes") || text.equals("save configuration")
                || text.equals("save configurations") || text.equals("save config") || text.equals("save all")
                || text.equals("save settings");
    }

    public static String bindingText() {
        KeybindManager.Keybind binding = KeybindManager.keybinds.get(BindableAction.SAVE_CONFIGURATIONS);
        return binding == null ? I18n.format("gui.keybind.unbound") : binding.toString();
    }

    public static int preferredWidth(FontRenderer font, String label, int padding) {
        if (font == null || !isSaveLabel(label)) return 0;
        return font.getStringWidth(ModernFormI18n.tr(label)) + 6 + font.getStringWidth(bindingText()) + padding;
    }

    public static void drawText(FontRenderer font, String label, int x, int y, int color, int width) {
        if (!isSaveLabel(label)) {
            ModernUiRenderer.drawText(font, label, x, y, color, width);
            return;
        }
        if (font == null || width <= 0) return;
        String caption = ModernFormI18n.tr(label);
        String hint = bindingText();
        int captionWidth = font.getStringWidth(caption);
        int total = captionWidth + 6 + font.getStringWidth(hint);
        // Layout callers reserve preferredWidth; retain the normal font size.
        float scale = 1.0F;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y + (font.FONT_HEIGHT * (1 - scale)) / 2, 0);
            GlStateManager.scale(scale, scale, 1);
            font.drawString(caption, 0, 0, color);
            font.drawString(hint, captionWidth + 6, 0, color);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
