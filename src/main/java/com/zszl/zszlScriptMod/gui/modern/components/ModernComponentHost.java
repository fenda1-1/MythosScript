package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;

import net.minecraft.client.gui.FontRenderer;

/**
 * Small event host for modern controls.
 *
 * Children are drawn in insertion order and hit-tested in reverse order, so
 * later controls naturally sit above earlier ones.  Focus and mouse capture
 * are kept here instead of being reimplemented by every workbench.
 */
public final class ModernComponentHost {

    private final List<ModernComponent> children = new ArrayList<>();
    private ModernMainLayout.Rect bounds;
    private ModernComponent focused;
    private ModernComponent captured;

    public ModernComponentHost(ModernMainLayout.Rect bounds) {
        this.bounds = bounds;
    }

    public void setBounds(ModernMainLayout.Rect bounds) {
        this.bounds = bounds;
    }

    public ModernMainLayout.Rect bounds() {
        return bounds;
    }

    public <T extends ModernComponent> T add(T component) {
        if (component != null && !children.contains(component)) {
            children.add(component);
        }
        return component;
    }

    public void remove(ModernComponent component) {
        if (component == null) {
            return;
        }
        if (focused == component) {
            clearFocus();
        }
        if (captured == component) {
            captured = null;
        }
        children.remove(component);
    }

    public void clear() {
        clearFocus();
        captured = null;
        children.clear();
    }

    public List<ModernComponent> children() {
        return Collections.unmodifiableList(children);
    }

    public ModernComponent focused() {
        return focused;
    }

    public boolean hasFocus() {
        return focused != null && focused.isFocused();
    }

    public void update() {
        for (ModernComponent child : children) {
            if (child != null && child.isVisible()) {
                child.update();
            }
        }
    }

    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        for (ModernComponent child : children) {
            if (child != null && child.isVisible()) {
                child.draw(fontRenderer, mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ModernComponent child = children.get(i);
            if (child == null || !child.isVisible() || !child.isEnabled()) {
                continue;
            }
            if (child.click(mouseX, mouseY, mouseButton)) {
                if (child.isFocusable()) {
                    focus(child);
                } else if (mouseButton == 0) {
                    clearFocus();
                }
                return true;
            }
        }
        if (mouseButton == 0) {
            clearFocus();
        }
        return false;
    }

    public boolean mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (captured != null && captured.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceLastClick)) {
            return true;
        }
        if (mouseButton != 0) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            ModernComponent child = children.get(i);
            if (child != null && child.isVisible() && child.mouseClickMove(mouseX, mouseY, mouseButton,
                    timeSinceLastClick)) {
                captured = child;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton) {
        ModernComponent target = captured;
        captured = null;
        if (target != null) {
            return target.mouseReleased(mouseX, mouseY, mouseButton);
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            ModernComponent child = children.get(i);
            if (child != null && child.isVisible() && child.mouseReleased(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (focused != null && focused.isVisible() && focused.isEnabled()
                && focused.keyTyped(typedChar, keyCode)) {
            return true;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            ModernComponent child = children.get(i);
            if (child != null && child != focused && child.isVisible() && child.isEnabled()
                    && child.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ModernComponent child = children.get(i);
            if (child != null && child.isVisible() && child.handleMouseWheel(wheel, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public void clearFocusOutside(int mouseX, int mouseY) {
        if (focused != null && !focused.contains(mouseX, mouseY)) {
            clearFocus();
        }
    }

    public void clearFocus() {
        if (focused != null) {
            focused.clearFocus();
            focused = null;
        }
    }

    private void focus(ModernComponent component) {
        if (focused != component && focused != null) {
            focused.clearFocus();
        }
        focused = component;
        focused.setFocused(true);
    }
}
