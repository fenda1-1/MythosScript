package com.zszl.zszlScriptMod.gui.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;

/**
 * Local visual primitives shared by the two advanced rule editors. The
 * editors keep their own state and interaction logic; this class only draws
 * the modern shell around their existing controls.
 */
public final class ModernRuleEditorUi {

    private static java.lang.reflect.Field textFieldEnabledField;
    private static boolean textFieldEnabledFieldResolved;

    public enum ButtonTone {
        DEFAULT,
        PRIMARY,
        DANGER
    }

    private ModernRuleEditorUi() {
    }

    public static void drawTextField(GuiScreen screen, GuiTextField field) {
        if (screen == null || field == null || !field.getVisible() || field.width <= 0 || field.height <= 0) {
            return;
        }

        ModernTooltipSupport.registerTextField(screen, field);
        boolean enabled = !isFieldDisabled(field);
        boolean focused = field.isFocused();
        int fill = enabled ? 0xFF101820 : 0xFF141B22;
        int border = focused ? ModernUiRenderer.ACCENT
                : enabled ? ModernUiRenderer.BORDER_SUBTLE : 0xFF27333D;
        ModernUiRenderer.drawSubtlePanel(field.x - 1, field.y - 1, field.width + 2, field.height + 2, 4, fill, border);
        field.setTextColor(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setEnableBackgroundDrawing(false);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private static boolean isFieldDisabled(GuiTextField field) {
        try {
            if (!textFieldEnabledFieldResolved) {
                textFieldEnabledFieldResolved = true;
                textFieldEnabledField = GuiTextField.class.getDeclaredField("isEnabled");
                textFieldEnabledField.setAccessible(true);
            }
            return textFieldEnabledField != null && !textFieldEnabledField.getBoolean(field);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void drawButton(FontRenderer fontRenderer, GuiButton button, int mouseX, int mouseY,
            ButtonTone tone) {
        if (fontRenderer == null || button == null || !button.visible) {
            return;
        }

        boolean hovered = contains(button.x, button.y, button.width, button.height, mouseX, mouseY);
        boolean pressed = hovered && Mouse.isButtonDown(0);
        boolean enabled = button.enabled;
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.DISABLED_TEXT;
        } else if (tone == ButtonTone.PRIMARY) {
            fill = pressed ? ModernUiRenderer.SURFACE_PRESSED
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        } else if (tone == ButtonTone.DANGER) {
            fill = pressed ? ModernUiRenderer.SURFACE_PRESSED
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.DANGER;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        } else {
            fill = pressed ? ModernUiRenderer.SURFACE_PRESSED
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = pressed ? ModernUiRenderer.ACCENT_DIM : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        }

        ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 4, fill, border);
        String label = TextFormatting.getTextWithoutFormattingCodes(button.displayString == null ? "" : button.displayString);
        if (com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label)) {
            com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, button.x + 6, button.y + (button.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.readableText(text, fill), button.width - 12);
            return;
        }
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        // Reserve a small strip on the right for the screen-owned information
        // label. This keeps long captions readable when the modern editor
        // decorates a control with an explanatory icon.
        int infoInset = button.width >= 38 ? 16 : 0;
        int textAreaWidth = Math.max(0, button.width - infoInset);
        if (textAreaWidth <= 0) {
            return;
        }
        int textPadding = Math.min(6, textAreaWidth / 2);
        int textX = button.x + Math.max(textPadding, (textAreaWidth - labelWidth) / 2);
        int textY = button.y + Math.max(3, (button.height - fontRenderer.FONT_HEIGHT) / 2);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, textX, textY, ModernUiRenderer.readableText(text, fill),
                Math.max(1, textAreaWidth - textPadding));
    }

    public static void drawToggleButton(FontRenderer fontRenderer, GuiButton button, int mouseX, int mouseY,
            boolean selected) {
        if (fontRenderer == null || button == null || !button.visible) {
            return;
        }

        boolean hovered = contains(button.x, button.y, button.width, button.height, mouseX, mouseY);
        boolean enabled = button.enabled;
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = selected && enabled ? ModernUiRenderer.ACCENT
                : hovered && enabled ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 4, fill, border);
        String label = TextFormatting.getTextWithoutFormattingCodes(button.displayString == null ? "" : button.displayString);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, button.x + 7,
                button.y + Math.max(3, (button.height - fontRenderer.FONT_HEIGHT) / 2),
                ModernUiRenderer.readableText(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.DISABLED_TEXT, fill),
                Math.max(18, button.width - 55));
        int toggleX = button.x + Math.max(18, button.width - 50);
        int toggleY = button.y + Math.max(3, (button.height - 14) / 2);
        ModernUiRenderer.drawToggle(toggleX, toggleY, 28, 14, selected, hovered && enabled);
    }

    public static void drawBackButton(int x, int y, int mouseX, int mouseY) {
        boolean hovered = contains(x, y, 22, 22, mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(x, y, 22, 22, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(x + 9, y + 7, false, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
    }

    public static void drawInfoIcon(FontRenderer fontRenderer, int screenWidth, int screenHeight, int x, int y,
            String tooltip, int mouseX, int mouseY) {
        ModernTooltipSupport.registerInfoIcon(Minecraft.getMinecraft().currentScreen, x, y, 11, 11, tooltip);
        boolean hovered = contains(x, y, 11, 11, mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    public static void drawScrollbar(int x, int y, int height, int thumbY, int thumbHeight) {
        drawScrollbar(x, y, height, thumbY, thumbHeight, false);
    }

    public static void drawScrollbar(int x, int y, int height, int thumbY, int thumbHeight, boolean hovered) {
        if (height <= 0) return;
        thumbHeight = Math.min(height, Math.max(8, thumbHeight));
        thumbY = Math.max(y, Math.min(y + height - thumbHeight, thumbY));
        int grown = hovered ? 10 : 4;
        int trackWidth = hovered ? 4 : 2;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT;
        ModernUiRenderer.drawRoundedRect(x + 2 - trackWidth, y, trackWidth, Math.max(0, height), 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(x + 2 - grown, thumbY, grown, thumbHeight,
                Math.max(2, grown / 2), thumbColor);
    }

    /** Draws a non-destructive rounded validation outline around an existing control. */
    public static void drawValidationBorder(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int color = 0xFFE06A78;
        int stroke = 2;
        ModernUiRenderer.drawRoundedRect(x - 2, y - 2, width + 4, stroke, 1, color);
        ModernUiRenderer.drawRoundedRect(x - 2, y + height, width + 4, stroke, 1, color);
        ModernUiRenderer.drawRoundedRect(x - 2, y, stroke, Math.max(1, height), 1, color);
        ModernUiRenderer.drawRoundedRect(x + width, y, stroke, Math.max(1, height), 1, color);
    }

    public static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
