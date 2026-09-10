package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernScrollableDropdown;

import net.minecraft.client.gui.FontRenderer;

/** Component adapter for the shared modern dropdown menu. */
public final class ModernSelect implements ModernComponent {

    private final ModernScrollableDropdown dropdown = new ModernScrollableDropdown();
    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect hostBounds;
    private boolean visible = true;
    private boolean enabled = true;
    private Consumer<String> onChanged;
    private String lastValue = "";

    public ModernSelect(String[] values, String[] labels, Consumer<String> onChanged) {
        this.onChanged = onChanged;
        setOptions(values, labels);
    }

    public void setOptions(String[] values, String[] labels) {
        dropdown.setOptions(values, labels);
        lastValue = dropdown.value();
    }

    public String value() { return dropdown.value(); }
    public void setValue(String value) { dropdown.setValue(value); lastValue = dropdown.value(); }
    public boolean isOpen() { return dropdown.isOpen(); }
    public ModernSelect setHostBounds(ModernMainLayout.Rect hostBounds) { this.hostBounds = hostBounds; return this; }
    public ModernSelect setOnChanged(Consumer<String> onChanged) { this.onChanged = onChanged; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        dropdown.drawButton(fontRenderer, bounds, mouseX, mouseY);
        dropdown.drawMenu(fontRenderer, hostBounds == null ? bounds : hostBounds, mouseX, mouseY);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled) return false;
        boolean handled = dropdown.mouseClicked(mouseX, mouseY);
        if (handled && !lastValue.equals(dropdown.value())) {
            lastValue = dropdown.value();
            if (onChanged != null) onChanged.accept(lastValue);
        }
        return handled;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        return visible && enabled && dropdown.wheel(wheel, mouseX, mouseY);
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; if (!visible) dropdown.close(); }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) dropdown.close(); }
}
