package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;

/** Numeric slider with click and drag support. */
public final class ModernSlider implements ModernComponent {

    private ModernMainLayout.Rect bounds;
    private final double minimum;
    private final double maximum;
    private final double step;
    private double value;
    private boolean dragging;
    private boolean visible = true;
    private boolean enabled = true;
    private Consumer<Double> onChanged;

    public ModernSlider(double minimum, double maximum, double value, double step, Consumer<Double> onChanged) {
        this.minimum = Math.min(minimum, maximum);
        this.maximum = Math.max(minimum, maximum);
        this.step = step <= 0.0D ? 0.0D : step;
        this.value = normalize(value);
        this.onChanged = onChanged;
    }

    public double value() { return value; }
    public void setValue(double value) { this.value = normalize(value); }
    public ModernSlider setOnChanged(Consumer<Double> onChanged) { this.onChanged = onChanged; return this; }

    @Override public void layout(ModernMainLayout.Rect bounds) { this.bounds = bounds; }
    @Override public ModernMainLayout.Rect bounds() { return bounds; }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!visible || bounds == null) return;
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int centerY = bounds.y + bounds.height / 2;
        int left = bounds.x + 8;
        int right = Math.max(left + 1, bounds.right() - 8);
        ModernUiRenderer.drawRoundedRect(left, centerY - 2, right - left, 4, 2,
                hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        int thumbX = left + (int) Math.round((right - left) * fraction());
        ModernUiRenderer.drawRoundedRect(thumbX - 5, centerY - 6, 10, 12, 5,
                enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !visible || !enabled || !contains(mouseX, mouseY)) return false;
        dragging = true;
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (mouseButton != 0 || !dragging) return false;
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !dragging) return false;
        dragging = false;
        return true;
    }

    private void updateFromMouse(int mouseX) {
        int left = bounds.x + 8;
        int right = Math.max(left + 1, bounds.right() - 8);
        double next = minimum + (maximum - minimum) * clamp((mouseX - left) / (double) (right - left), 0.0D, 1.0D);
        next = normalize(next);
        if (next != value) {
            value = next;
            if (onChanged != null) onChanged.accept(value);
        }
    }

    private double normalize(double raw) {
        double next = Math.max(minimum, Math.min(maximum, raw));
        if (step > 0.0D) next = minimum + Math.round((next - minimum) / step) * step;
        return Math.max(minimum, Math.min(maximum, next));
    }

    private double fraction() {
        return maximum <= minimum ? 0.0D : (value - minimum) / (maximum - minimum);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; if (!visible) dragging = false; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) dragging = false; }
}
