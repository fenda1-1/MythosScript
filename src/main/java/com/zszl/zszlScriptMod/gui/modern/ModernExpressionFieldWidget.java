package com.zszl.zszlScriptMod.gui.modern;

import java.util.Collections;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormWidget;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ExpressionEditorPreview;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ModernExpressionEditorPanel;
import net.minecraft.client.gui.FontRenderer;

/** Embedded expression editor used by feature settings that store one filter expression. */
public final class ModernExpressionFieldWidget implements ModernFormWidget {
    private final ModernFormSettingsTab.TextValue value;
    private final ExpressionEditorPreview.Mode mode;
    private final ModernExpressionEditorPanel panel = new ModernExpressionEditorPanel();
    private ModernMainLayout.Rect viewport;
    private boolean initialized;

    public ModernExpressionFieldWidget(ModernFormSettingsTab.TextValue value, ExpressionEditorPreview.Mode mode) {
        this.value = value;
        this.mode = mode == null ? ExpressionEditorPreview.Mode.VALUE : mode;
    }

    @Override public void ensureInitialized(FontRenderer font) {
        if (initialized) return;
        initialized = true;
        open(font);
    }
    private void open(FontRenderer font) {
        panel.open(font, value == null ? "" : value.get(), "gui.modern.path.wb.u263", mode,
                Collections.emptyList(), null, -1, -1,
                expression -> { if (value != null) value.set(expression == null ? "" : expression.trim()); },
                () -> { });
    }
    @Override public void updateScreen() { panel.updateScreen(); }
    @Override public void draw(FontRenderer font, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        if (!panel.isOpen()) open(font);
        panel.draw(font, bounds, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(int x, int y, int button) { return panel.mouseClicked(x, y, button); }
    @Override public boolean mouseClickMove(int x, int y, int b, long t) { return panel.mouseClickMove(x, y, b, t); }
    @Override public boolean mouseReleased(int x, int y, int s) { return panel.mouseReleased(x, y, s); }
    @Override public boolean keyTyped(char c, int k) { return panel.keyTyped(c, k); }
    @Override public boolean handleMouseWheel(int wheel) { return panel.handleMouseWheel(wheel, 0, 0); }
    @Override public boolean handleMouseWheel(int wheel, int x, int y) { return panel.handleMouseWheel(wheel, x, y); }
    @Override public boolean handleEscape() { return panel.handleEscape(); }
    @Override public boolean isTextInputFocused() { return panel.isTextInputFocused(); }
    @Override public void clearTextInputFocusOutside(int x, int y) { panel.clearTextInputFocusOutside(x, y); }
    @Override public boolean containsContent(int x, int y) { return viewport != null && viewport.contains(x, y); }
    @Override public String getHoveredTooltip(int x, int y) { return panel.getHoveredTooltip(); }
    @Override public int height(int width, int availableHeight) { return Math.max(420, Math.min(620, availableHeight)); }
    @Override public void setViewport(ModernMainLayout.Rect bounds) { viewport = bounds; }
    @Override public Object snapshot() { return value == null ? "" : value.get(); }
    @Override public boolean commit() { return true; }
    @Override public void blur() { panel.clearTextInputFocusOutside(Integer.MIN_VALUE, Integer.MIN_VALUE); }
    @Override public void discardDraft() { }
}
