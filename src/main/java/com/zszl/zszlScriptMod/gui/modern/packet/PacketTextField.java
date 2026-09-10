package com.zszl.zszlScriptMod.gui.modern.packet;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Small embedded wrapper around Forge's text field; it never owns a screen. */
final class PacketTextField {
    private static PacketTextField activeField;
    private final int id;
    private final int maxLength;
    private GuiTextField field;

    PacketTextField(int id, int maxLength) {
        this.id = id;
        this.maxLength = maxLength <= 0 ? 32767 : maxLength;
    }

    void ensure(FontRenderer fontRenderer) {
        if (field != null || fontRenderer == null) return;
        field = new GuiTextField(id, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
    }

    void setBounds(ModernMainLayout.Rect bounds) {
        if (field == null || bounds == null) return;
        field.x = bounds.x; field.y = bounds.y; field.width = Math.max(1, bounds.width); field.height = Math.max(12, bounds.height);
    }

    void setText(String text) { if (field != null) field.setText(text == null ? "" : text); }
    String text() { return field == null || field.getText() == null ? "" : field.getText(); }
    boolean initialized() { return field != null; }
    boolean focused() { return field != null && field.isFocused(); }
    void focus(boolean value) {
        if (field == null) return;
        if (value) {
            if (activeField != null && activeField != this && activeField.field != null) activeField.field.setFocused(false);
            activeField = this;
        } else if (activeField == this) {
            activeField = null;
        }
        field.setFocused(value);
    }

    /** Clears whichever embedded field last owned keyboard focus. */
    static void clearActiveFocus() {
        if (activeField != null && activeField.field != null) {
            activeField.field.setFocused(false);
        }
        activeField = null;
    }
    static boolean hasActiveFocus() { return activeField != null && activeField.field != null && activeField.field.isFocused(); }
    static boolean activeContains(int mouseX, int mouseY) { return activeField != null && activeField.hit(mouseX, mouseY); }
    boolean hit(int mouseX, int mouseY) { return field != null && mouseX >= field.x && mouseX < field.x + field.width
            && mouseY >= field.y && mouseY < field.y + field.height; }
    boolean click(int mouseX, int mouseY, int button) {
        if (field == null) return false;
        boolean hit = hit(mouseX, mouseY);
        field.mouseClicked(mouseX, mouseY, button);
        if (!hit && button == 0 && activeField != null) activeField.focus(false);
        focus(hit && button == 0);
        return hit;
    }
    boolean key(char typedChar, int keyCode) { return field != null && field.isFocused() && field.textboxKeyTyped(typedChar, keyCode); }
    void update() { if (field != null) field.updateCursorCounter(); }
    void draw() {
        if (field == null || !field.getVisible()) return;
        ModernUiRenderer.drawSubtlePanel(field.x - 1, field.y - 1, field.width + 2, field.height + 2, 3,
                ModernUiRenderer.SURFACE, field.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }
}
