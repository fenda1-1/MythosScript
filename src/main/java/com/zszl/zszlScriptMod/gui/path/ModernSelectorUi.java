package com.zszl.zszlScriptMod.gui.path;

import java.awt.Rectangle;

import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.Minecraft;

/** Small shared visual language for the path editor's selector dialogs. */
final class ModernSelectorUi {

    private ModernSelectorUi() {
    }

    static void drawShell(FontRenderer fontRenderer, Rectangle panel, Rectangle back, String title, String subtitle,
            String tooltip, int mouseX, int mouseY) {
        if (panel == null) {
            return;
        }
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawSubtlePanel(panel.x + 7, panel.y + 7, Math.max(1, panel.width - 14), 32, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        if (back != null) {
            ModernRuleEditorUi.drawBackButton(back.x, back.y, mouseX, mouseY);
        }
        int titleX = panel.x + 45;
        ModernUiRenderer.drawText(fontRenderer, title, titleX, panel.y + 12, ModernUiRenderer.TEXT,
                Math.max(48, panel.width - 68));
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, titleX, panel.y + 26, ModernUiRenderer.MUTED_TEXT,
                    Math.max(48, panel.width - 68));
        }
        if (tooltip != null && !tooltip.trim().isEmpty()) {
            int iconX = titleX + Math.min(Math.max(0, panel.width - 70), fontRenderer.getStringWidth(title) + 7);
            ModernRuleEditorUi.drawInfoIcon(fontRenderer, screenWidth(), screenHeight(), iconX, panel.y + 11,
                    tooltip, mouseX, mouseY);
        }
    }

    static void drawPane(FontRenderer fontRenderer, Rectangle bounds, String title, String tooltip, int mouseX,
            int mouseY) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, title, bounds.x + 9, bounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(28, bounds.width - 28));
        if (tooltip != null && !tooltip.trim().isEmpty()) {
            int iconX = bounds.x + Math.min(Math.max(12, bounds.width - 16), fontRenderer.getStringWidth(title) + 12);
            ModernRuleEditorUi.drawInfoIcon(fontRenderer, screenWidth(), screenHeight(), iconX, bounds.y + 7,
                    tooltip, mouseX, mouseY);
        }
        ModernUiRenderer.drawDivider(bounds.x + 8, bounds.y + 24, Math.max(1, bounds.width - 16),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    static void drawRow(FontRenderer fontRenderer, Rectangle bounds, String title, String detail, boolean selected,
            boolean hovered, String tooltip, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? 0xFF283C48 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : hovered ? 0xFF607582 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(bounds.x, bounds.y, 3, bounds.height, 2,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
        int textX = bounds.x + 10;
        int infoReserve = tooltip == null || tooltip.trim().isEmpty() ? 0 : 20;
        ModernUiRenderer.drawText(fontRenderer, title, textX, bounds.y + 5, selected ? ModernUiRenderer.TEXT
                : ModernUiRenderer.SUBTLE_TEXT, Math.max(20, bounds.width - 18 - infoReserve));
        if (detail != null && !detail.trim().isEmpty() && bounds.height >= 22) {
            ModernUiRenderer.drawText(fontRenderer, detail, textX, bounds.y + 18, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, bounds.width - 18 - infoReserve));
        }
        if (tooltip != null && !tooltip.trim().isEmpty()) {
            int iconX = bounds.x + bounds.width - 17;
            ModernRuleEditorUi.drawInfoIcon(fontRenderer, screenWidth(), screenHeight(), iconX,
                    bounds.y + (bounds.height - 11) / 2, tooltip, mouseX, mouseY);
        }
    }

    static void drawButton(FontRenderer fontRenderer, GuiButton button, int mouseX, int mouseY,
            ModernRuleEditorUi.ButtonTone tone) {
        ModernRuleEditorUi.drawButton(fontRenderer, button, mouseX, mouseY, tone);
    }

    static Rectangle infoForButton(GuiButton button) {
        return button == null ? null
                : new Rectangle(button.x + button.width - 16, button.y + (button.height - 11) / 2, 11, 11);
    }

    static Rectangle infoForPane(FontRenderer fontRenderer, Rectangle bounds, String title) {
        if (fontRenderer == null || bounds == null) {
            return null;
        }
        int iconX = bounds.x + Math.min(Math.max(12, bounds.width - 16), fontRenderer.getStringWidth(title == null ? "" : title) + 12);
        return new Rectangle(iconX, bounds.y + 7, 11, 11);
    }

    static boolean contains(Rectangle bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static int screenWidth() {
        return Minecraft.getMinecraft().currentScreen == null ? 320 : Minecraft.getMinecraft().currentScreen.width;
    }

    private static int screenHeight() {
        return Minecraft.getMinecraft().currentScreen == null ? 200 : Minecraft.getMinecraft().currentScreen.height;
    }
}
