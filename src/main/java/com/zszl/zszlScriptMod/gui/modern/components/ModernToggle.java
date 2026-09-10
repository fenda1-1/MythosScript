package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Full-row boolean switch. */
public final class ModernToggle implements ModernComponent {

    private ModernMainLayout.Rect bounds;
    private String label = "";
    private boolean value;
    private boolean visible = true;
    private boolean enabled = true;
    private Consumer<Boolean> onChanged;

    public ModernToggle(String label, boolean value, Consumer<Boolean> onChanged) {
        this.label = label == null ? "" : label;
        this.value = value;
        this.onChanged = onChanged;
    }

    public boolean value() { return value; }
    public void setValue(boolean value) { this.value = value; }
    public ModernToggle setLabel(String label) { this.label = label == null ? "" : label; return this; }
    public ModernToggle setOnChanged(Consumer<Boolean> onChanged) { this.onChanged = onChanged; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + Math.max(3, (bounds.height - fontRenderer.FONT_HEIGHT) / 2),
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, bounds.width - 52));
        ModernUiRenderer.drawToggle(bounds.right() - 38, bounds.y + Math.max(2, (bounds.height - 16) / 2),
                32, 16, enabled && value, hovered);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || !contains(mouseX, mouseY)) return false;
        value = !value;
        if (onChanged != null) onChanged.accept(value);
        return true;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
