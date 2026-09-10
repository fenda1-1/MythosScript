package com.zszl.zszlScriptMod.gui.modern.components;

import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/**
 * Modern single-line input component.
 *
 * GuiTextField remains the 1.12.2 keyboard/clipboard adapter, but callers use
 * this class as the component.  The hit rectangle and the native text
 * rectangle are explicit, so an icon or visual padding can never silently
 * change focus behavior.
 */
public class ModernTextField extends GuiTextField implements ModernComponent {

    private ModernMainLayout.Rect hitBounds;
    private ModernMainLayout.Rect contentBounds;
    private String placeholder = "";
    private boolean componentVisible = true;
    private boolean componentEnabled = true;
    private Consumer<String> changeListener;
    private String lastNotifiedText = "";

    public ModernTextField(int id, FontRenderer fontRenderer, int x, int y, int width, int height) {
        super(id, fontRenderer, x, y, width, height);
        setEnableBackgroundDrawing(false);
        setTextColor(ModernUiRenderer.TEXT);
        setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        lastNotifiedText = getText();
    }

    @Override
    public void layout(ModernMainLayout.Rect bounds) {
        layout(bounds, 0, 0);
    }

    /** Lays out a full hit surface with optional left/right text insets. */
    public void layout(ModernMainLayout.Rect bounds, int leftInset, int rightInset) {
        if (bounds == null) {
            hitBounds = null;
            contentBounds = null;
            setVisible(false);
            return;
        }
        hitBounds = bounds;
        int left = Math.max(0, leftInset);
        int right = Math.max(0, rightInset);
        contentBounds = new ModernMainLayout.Rect(bounds.x + left, bounds.y,
                Math.max(1, bounds.width - left - right), Math.max(1, bounds.height));
        x = contentBounds.x;
        y = contentBounds.y;
        width = contentBounds.width;
        height = contentBounds.height;
        setVisible(componentVisible);
        setEnabled(componentEnabled);
    }

    @Override
    public ModernMainLayout.Rect bounds() {
        return hitBounds == null ? new ModernMainLayout.Rect(x, y, width, height) : hitBounds;
    }

    public ModernMainLayout.Rect hitBounds() {
        return bounds();
    }

    public ModernMainLayout.Rect contentBounds() {
        return contentBounds == null ? new ModernMainLayout.Rect(x, y, width, height) : contentBounds;
    }

    public ModernTextField setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    public ModernTextField setChangeListener(Consumer<String> listener) {
        changeListener = listener;
        return this;
    }

    @Override
    public void setVisible(boolean visible) {
        componentVisible = visible;
        super.setVisible(visible);
        if (!visible) {
            super.setFocused(false);
        }
    }

    @Override
    public boolean isVisible() {
        return componentVisible && getVisible();
    }

    @Override
    public void setEnabled(boolean enabled) {
        componentEnabled = enabled;
        super.setEnabled(enabled);
        if (!enabled) {
            super.setFocused(false);
        }
    }

    @Override
    public boolean isEnabled() {
        return componentEnabled;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public boolean isFocused() {
        return super.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused && isVisible() && isEnabled());
    }

    @Override
    public void clearFocus() {
        super.setFocused(false);
    }

    @Override
    public void update() {
        if (isVisible()) {
            updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (!isVisible() || bounds() == null) {
            return;
        }
        ModernMainLayout.Rect rect = bounds();
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                ModernUiRenderer.INPUT_SURFACE,
                isFocused() ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.HOVER_BORDER
                        : ModernUiRenderer.BORDER_SUBTLE);
        drawTextContents(fontRenderer);
    }

    /** Draws text/caret only, for forms that draw their own input surface. */
    public void drawTextContents(FontRenderer fontRenderer) {
        if (!isVisible() || width <= 0 || height <= 0) {
            return;
        }
        setTextColor(ModernUiRenderer.TEXT);
        setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        setEnableBackgroundDrawing(false);
        ModernUiRenderer.reflowTextField(this);
        ModernUiRenderer.drawTextField(this);
        if (!isFocused() && getText() != null && getText().isEmpty() && !placeholder.isEmpty()) {
            ModernMainLayout.Rect rect = contentBounds();
            ModernUiRenderer.drawText(fontRenderer, placeholder, rect.x + 5,
                    rect.y + Math.max(1, (rect.height - fontRenderer.FONT_HEIGHT) / 2),
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, rect.width - 10));
        }
    }

    /** Handles a click against the full visual surface, not only text pixels. */
    @Override
    public boolean click(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || !isVisible() || !isEnabled() || !contains(mouseX, mouseY)) {
            return false;
        }
        ModernMainLayout.Rect content = contentBounds();
        int nativeX = Math.max(content.x, Math.min(content.right() - 1, mouseX));
        int nativeY = Math.max(content.y, Math.min(content.bottom() - 1, mouseY));
        super.mouseClicked(nativeX, nativeY, mouseButton);
        super.setFocused(true);
        ModernUiRenderer.moveTextFieldCursorTo(this, nativeX);
        return true;
    }

    /** Keeps legacy callers safe while the page migrates to click(). */
    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isVisible() && isEnabled()) {
            if (contains(mouseX, mouseY)) {
                return click(mouseX, mouseY, mouseButton);
            } else {
                super.setFocused(false);
            }
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!isVisible() || !isEnabled() || !isFocused()) {
            return false;
        }
        boolean handled = textboxKeyTyped(typedChar, keyCode);
        notifyChangedIfNeeded();
        return handled;
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return isVisible() && isEnabled() && bounds() != null && bounds().contains(mouseX, mouseY);
    }

    private void notifyChangedIfNeeded() {
        String current = getText() == null ? "" : getText();
        if (!current.equals(lastNotifiedText)) {
            lastNotifiedText = current;
            if (changeListener != null) {
                changeListener.accept(current);
            }
        }
    }
}
