package com.zszl.zszlScriptMod.gui.modern.profile;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;

/** Shared native chrome for the profile workbench and its overlays. */
final class ProfileUi {

    enum Tone {
        DEFAULT,
        PRIMARY,
        DANGER,
        SUCCESS
    }

    private ProfileUi() {
    }

    static String tr(String keyOrLiteral) {
        return ModernFormI18n.tr(keyOrLiteral);
    }

    static void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, Tone tone, boolean enabled,
            boolean hovered) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        int fill;
        int border;
        int color;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            color = ModernUiRenderer.MUTED_TEXT;
        } else if (tone == Tone.PRIMARY) {
            fill = hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT;
            border = hovered ? 0xFFFFB0C4 : ModernUiRenderer.ACCENT;
            color = ModernUiRenderer.SHELL;
        } else if (tone == Tone.DANGER) {
            fill = hovered ? 0xFFF29A78 : ModernUiRenderer.DANGER;
            border = hovered ? 0xFFFFC0A7 : ModernUiRenderer.DANGER;
            color = ModernUiRenderer.SHELL;
        } else if (tone == Tone.SUCCESS) {
            fill = hovered ? 0xFF78E0B0 : ModernUiRenderer.SUCCESS;
            border = hovered ? 0xFFA6F0CC : ModernUiRenderer.SUCCESS;
            color = ModernUiRenderer.SHELL;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
            color = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        if (font != null) {
            ModernUiRenderer.drawText(font, tr(label), rect.x + 6,
                    rect.y + Math.max(2, (rect.height - font.FONT_HEIGHT) / 2), color,
                    Math.max(1, rect.width - 12));
        }
    }

    static void drawRow(ModernMainLayout.Rect rect, boolean selected, boolean hovered, boolean accented) {
        if (rect == null) {
            return;
        }
        int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = selected ? ModernUiRenderer.ACCENT
                : accented ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
    }

    static void drawCheckbox(int x, int y, boolean checked) {
        ModernUiRenderer.drawRoundedRect(x, y, 12, 12, 3,
                checked ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SHELL);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, 10, 10, 2,
                checked ? ModernUiRenderer.SUCCESS : 0xFF101820);
        if (checked) {
            ModernUiRenderer.drawCheckMark(x + 1, y + 1, 10, ModernUiRenderer.SHELL);
        }
    }

    static void drawSection(FontRenderer font, ModernMainLayout.Rect bounds, String title) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        if (font != null) {
            ModernUiRenderer.drawText(font, tr(title), bounds.x + 10, bounds.y + 8, ModernUiRenderer.TEXT,
                    Math.max(20, bounds.width - 28));
        }
        ModernUiRenderer.drawDivider(bounds.x + 8, bounds.y + 22, Math.max(1, bounds.width - 16),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    static void drawInfo(FontRenderer font, int x, int y, String tooltip, int mouseX, int mouseY,
            String[] hovered) {
        boolean hover = mouseX >= x && mouseX < x + 11 && mouseY >= y && mouseY < y + 11;
        ModernUiRenderer.drawInfoIcon(x, y, hover ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        if (hover && hovered != null && hovered.length > 0) {
            hovered[0] = tooltip == null ? "" : tooltip;
        }
    }

    static void drawField(FontRenderer font, ModernTextField field, ModernMainLayout.Rect rect, String placeholder,
            int mouseX, int mouseY) {
        if (field == null || rect == null) {
            return;
        }
        boolean focused = field.isFocused();
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        field.setVisible(true);
        field.setEnabled(true);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.x = rect.x + 6;
        field.y = rect.y + Math.max(1, font == null ? 4 : (rect.height - font.FONT_HEIGHT) / 2);
        field.width = Math.max(1, rect.width - 12);
        field.height = Math.max(2, font == null ? 12 : font.FONT_HEIGHT + 2);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && (field.getText() == null || field.getText().trim().isEmpty()) && placeholder != null
                && font != null) {
            ModernUiRenderer.drawText(font, tr(placeholder), rect.x + 6,
                    rect.y + Math.max(1, (rect.height - font.FONT_HEIGHT) / 2), ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, rect.width - 12));
        }
    }

    static boolean hit(ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        return rect != null && rect.contains(mouseX, mouseY);
    }
}
