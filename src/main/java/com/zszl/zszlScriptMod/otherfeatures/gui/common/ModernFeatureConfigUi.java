package com.zszl.zszlScriptMod.otherfeatures.gui.common;

import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.components.ToggleGuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.List;

/**
 * Shared rendering primitives for the compact other-feature configuration pages.
 * Behaviour stays in each screen; this class only gives those screens one visual
 * language and targeted information labels.
 */
public final class ModernFeatureConfigUi {

    private ModernFeatureConfigUi() {
    }

    public static void drawShell(FontRenderer fontRenderer, int panelX, int panelY, int panelWidth, int panelHeight,
            String title, String subtitle) {
        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 8, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(panelX + 1, panelY + 1, Math.max(0, panelWidth - 2), 41, 7,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawRoundedRect(panelX + 10, panelY + 9, 3, 24, 2, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, title, panelX + 15, panelY + 9, ModernUiRenderer.TEXT,
                Math.max(72, panelWidth - 30));
        ModernUiRenderer.drawText(fontRenderer, subtitle, panelX + 15, panelY + 24, ModernUiRenderer.MUTED_TEXT,
                Math.max(72, panelWidth - 30));
        ModernUiRenderer.drawDivider(panelX + 12, panelY + 42, Math.max(0, panelWidth - 24),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    public static void drawInstruction(FontRenderer fontRenderer, List<String> lines, int x, int y, int maxWidth) {
        if (lines == null) {
            return;
        }
        int lineStep = fontRenderer.FONT_HEIGHT + 2;
        int currentY = y;
        for (String line : lines) {
            ModernUiRenderer.drawText(fontRenderer, line, x, currentY, ModernUiRenderer.SUBTLE_TEXT, maxWidth);
            currentY += lineStep;
        }
    }

    public static void drawStatus(FontRenderer fontRenderer, int x, int y, int width, boolean enabled, String text) {
        int fill = enabled ? 0xFF19352B : 0xFF30212A;
        int border = enabled ? 0xFF3B8A68 : 0xFF754254;
        ModernUiRenderer.drawSubtlePanel(x, y, width, 20, 5, fill, border);
        ModernUiRenderer.drawStatusDot(x + 8, y + 7, enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, text, x + 21, y + 6, ModernUiRenderer.TEXT, Math.max(30, width - 28));
    }

    public static void drawSection(FontRenderer fontRenderer, String title, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 6, 0xFF151F28, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, title, x + 8, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, width - 16));
        ModernUiRenderer.drawDivider(x + 8, y + 18, Math.max(0, width - 16), ModernUiRenderer.BORDER_SUBTLE);
    }

    public static void drawButton(FontRenderer fontRenderer, GuiButton button, int mouseX, int mouseY,
            boolean primary, boolean warning) {
        if (button == null || !button.visible) {
            return;
        }
        if (button instanceof ToggleGuiButton) {
            ModernRuleEditorUi.drawToggleButton(fontRenderer, button, mouseX, mouseY,
                    ((ToggleGuiButton) button).getEnabledState());
            return;
        }
        boolean hovered = button.enabled && contains(button.x, button.y, button.width, button.height, mouseX, mouseY);
        boolean pressed = hovered && Mouse.isButtonDown(0);
        int fill;
        int border;
        int textColor;
        if (!button.enabled) {
            fill = 0xFF151D24;
            border = ModernUiRenderer.BORDER_SUBTLE;
            textColor = ModernUiRenderer.MUTED_TEXT;
        } else if (primary) {
            fill = pressed ? 0xFFD9577D : hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT;
            border = pressed ? 0xFFFF82A5 : hovered ? 0xFFFFABC0 : ModernUiRenderer.ACCENT;
            textColor = ModernUiRenderer.SHELL;
        } else if (warning) {
            fill = pressed ? 0xFF654719 : hovered ? 0xFF76582B : 0xFF573F22;
            border = pressed ? 0xFFFFD07A : hovered ? ModernUiRenderer.WARNING : 0xFF8B6937;
            textColor = 0xFFFFE6BA;
        } else {
            fill = pressed ? ModernUiRenderer.SURFACE_PRESSED
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = pressed ? ModernUiRenderer.ACCENT_DIM : hovered ? 0xFF607582 : ModernUiRenderer.BORDER_SUBTLE;
            textColor = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 4, fill, border);
        String label = ModernUiRenderer.ellipsize(fontRenderer, button.displayString, Math.max(18, button.width - 34));
        int contentWidth = Math.max(20, button.width - 16);
        int textX = button.x + Math.max(6, (contentWidth - fontRenderer.getStringWidth(label)) / 2);
        int textY = button.y + Math.max(4, (button.height - fontRenderer.FONT_HEIGHT) / 2);
        fontRenderer.drawString(label, textX, textY, textColor);
    }

    public static void drawScrollbar(int x, int y, int height, int thumbY, int thumbHeight) {
        if (height <= 0) return;
        thumbHeight = Math.min(height, Math.max(8, thumbHeight));
        thumbY = Math.max(y, Math.min(y + height - thumbHeight, thumbY));
        ModernUiRenderer.drawRoundedRect(x, y, 4, Math.max(1, height), 2, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(x, thumbY, 4, thumbHeight, 2, ModernUiRenderer.SUBTLE_TEXT);
    }

    public static void drawInfoIcon(FontRenderer fontRenderer, int screenWidth, int screenHeight, int mouseX,
            int mouseY, int x, int y, String tooltip) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        ModernTooltipSupport.registerInfoIcon(net.minecraft.client.Minecraft.getMinecraft().currentScreen, x, y, 11, 11,
                tooltip);
        boolean hovered = contains(x, y, 11, 11, mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    public static String tooltip(String... lines) {
        StringBuilder result = new StringBuilder();
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(line.trim());
        }
        return result.toString();
    }

    public static String tooltip(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return tooltip(lines.toArray(new String[lines.size()]));
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
