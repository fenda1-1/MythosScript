package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Compact checkbox for dense toolbars and lists. */
public final class ModernCheckbox implements ModernComponent {

    private ModernMainLayout.Rect bounds;
    private String label = "";
    private boolean checked;
    private boolean visible = true;
    private boolean enabled = true;
    private Consumer<Boolean> onChanged;

    public ModernCheckbox(String label, boolean checked, Consumer<Boolean> onChanged) {
        this.label = label == null ? "" : label;
        this.checked = checked;
        this.onChanged = onChanged;
    }

    public boolean checked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public ModernCheckbox setOnChanged(Consumer<Boolean> onChanged) { this.onChanged = onChanged; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int box = Math.min(16, Math.max(12, bounds.height - 4));
        int boxY = bounds.y + Math.max(0, (bounds.height - box) / 2);
        ModernUiRenderer.drawSubtlePanel(bounds.x, boxY, box, box, 3,
                checked ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SURFACE,
                checked || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if (checked) ModernUiRenderer.drawCheckMark(bounds.x + 3, boxY + 3, box - 6, ModernUiRenderer.TEXT);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + box + 7,
                bounds.y + Math.max(3, (bounds.height - fontRenderer.FONT_HEIGHT) / 2),
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, bounds.width - box - 7));
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || !contains(mouseX, mouseY)) return false;
        checked = !checked;
        if (onChanged != null) onChanged.accept(checked);
        return true;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
