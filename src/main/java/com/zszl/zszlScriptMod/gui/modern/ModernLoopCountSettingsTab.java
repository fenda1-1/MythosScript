package com.zszl.zszlScriptMod.gui.modern;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.config.LoopExecutionConfig;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;

/**
 * Compact loop-count editor embedded in the modern main window.
 */
public final class ModernLoopCountSettingsTab implements ModernSettingsTab {

    private ModernTextField loopCountField;
    private ModernMainLayout.Rect inputBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect infiniteBounds;
    private ModernMainLayout.Rect contentBounds;
    private String hoveredInfoTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean statusError;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (loopCountField != null) {
            return;
        }
        loopCountField = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        loopCountField.setEnableBackgroundDrawing(false);
        loopCountField.setMaxStringLength(16);
        loopCountField.setTextColor(ModernUiRenderer.TEXT);
        loopCountField.setDisabledTextColour(ModernUiRenderer.SUBTLE_TEXT);
        loopCountField.setText(String.valueOf(currentLoopCount()));
    }

    @Override
    public void updateScreen() {
        if (loopCountField != null) {
            loopCountField.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        hoveredInfoTooltip = "";
        contentBounds = requestedContentBounds;

        ModernMainLayout.Rect panel = createPanel(requestedContentBounds, 162);
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 7, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);

        int contentX = panel.x + 16;
        int availableWidth = Math.max(1, panel.width - 32);
        int statusPillWidth = Math.min(94, Math.max(54, fontRenderer.getStringWidth(formatCurrentValue()) + 16));
        int statusPillX = contentX;
        int statusPillY = panel.bottom() - 70;
        int statusFill = currentLoopCount() < 0 ? 0xFF33425A : ModernUiRenderer.SURFACE;
        int statusBorder = currentLoopCount() < 0 ? 0xFF6AA9FF : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(statusPillX, statusPillY, statusPillWidth, 17, 5, statusFill, statusBorder);
        ModernUiRenderer.drawText(fontRenderer, formatCurrentValue(), statusPillX + 8,
                statusPillY + (17 - fontRenderer.FONT_HEIGHT) / 2,
                currentLoopCount() < 0 ? 0xFF9FC9FF : ModernUiRenderer.SUBTLE_TEXT, statusPillWidth - 15);

        int inputY = panel.y + 20;
        String repeats = t("gui.modern.loop.repeats");
        ModernUiRenderer.drawText(fontRenderer, repeats, contentX, inputY, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(30, availableWidth - 74));
        drawInfoIcon(fontRenderer, contentX + Math.min(Math.max(0, availableWidth - 72),
                fontRenderer.getStringWidth(repeats) + 6), inputY - 1,
                t("gui.modern.loop.repeats.tip"), mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, t("gui.modern.loop.hint"), contentX, inputY + 13,
                ModernUiRenderer.MUTED_TEXT, Math.max(30, availableWidth - 74));

        int inputWidth = Math.max(64, Math.min(112, availableWidth / 3));
        inputBounds = new ModernMainLayout.Rect(panel.right() - 16 - inputWidth, inputY - 3, inputWidth, 24);
        drawTextField(fontRenderer, loopCountField, inputBounds, mouseX, mouseY);

        int buttonY = panel.bottom() - 42;
        int gap = 8;
        int buttonWidth = Math.max(62, (availableWidth - gap) / 2);
        saveBounds = new ModernMainLayout.Rect(contentX, buttonY, buttonWidth, 24);
        infiniteBounds = new ModernMainLayout.Rect(contentX + buttonWidth + gap, buttonY,
                Math.max(1, availableWidth - buttonWidth - gap), 24);
        drawActionButton(fontRenderer, saveBounds, t("gui.modern.loop.save"), true, mouseX, mouseY);
        drawActionButton(fontRenderer, infiniteBounds, t("gui.modern.loop.infinite"), false, mouseX, mouseY);

        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, contentX, panel.bottom() - 17,
                    statusError ? 0xFFFF8E8E : ModernUiRenderer.SUCCESS,
                    Math.max(20, availableWidth - 2));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            saveLoopCount();
            return true;
        }
        if (contains(infiniteBounds, mouseX, mouseY)) {
            applyLoopCount(-1);
            if (loopCountField != null) {
                loopCountField.setText("-1");
                loopCountField.setFocused(false);
            }
            showStatus(t("gui.modern.loop.status.infinite"));
            return true;
        }
        if (contains(inputBounds, mouseX, mouseY) && loopCountField != null) {
            loopCountField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (loopCountField != null) {
            loopCountField.setFocused(false);
        }
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (loopCountField != null && loopCountField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && loopCountField != null && loopCountField.isFocused()) {
            saveLoopCount();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredInfoTooltip;
    }

    @Override
    public void discardDraft() {
        if (loopCountField != null) {
            loopCountField.setText(String.valueOf(currentLoopCount()));
            loopCountField.setFocused(false);
        }
    }

    @Override
    public boolean isDirty() {
        return loopCountField != null
                && !String.valueOf(currentLoopCount()).equals(loopCountField.getText().trim());
    }

    private void saveLoopCount() {
        int parsed;
        boolean invalid = false;
        try {
            parsed = Integer.parseInt(loopCountField == null ? "" : loopCountField.getText().trim());
        } catch (NumberFormatException ignored) {
            parsed = 1;
            invalid = true;
        }
        applyLoopCount(parsed);
        if (loopCountField != null) {
            loopCountField.setText(String.valueOf(parsed));
            loopCountField.setFocused(false);
        }
        showStatus(invalid ? t("gui.modern.loop.status.invalid")
                : parsed < 0 ? t("gui.modern.loop.status.infinite") : t("gui.modern.loop.status.saved"),
                invalid);
    }

    private void applyLoopCount(int value) {
        GuiInventory.loopCount = value;
        if (LoopExecutionConfig.INSTANCE == null) {
            LoopExecutionConfig.INSTANCE = new LoopExecutionConfig();
        }
        LoopExecutionConfig.INSTANCE.loopCount = value;
        LoopExecutionConfig.save();
        GuiInventory.loopCounter = 0;
    }

    private int currentLoopCount() {
        return GuiInventory.loopCount;
    }

    private String formatCurrentValue() {
        return currentLoopCount() < 0 ? t("gui.modern.loop.current.infinite")
                : t("gui.modern.loop.current.value", String.valueOf(currentLoopCount()));
    }

    private ModernMainLayout.Rect createPanel(ModernMainLayout.Rect bounds, int preferredHeight) {
        int panelWidth = Math.max(1, bounds.width - 28);
        int panelHeight = Math.min(Math.max(1, bounds.height - 26), preferredHeight);
        return new ModernMainLayout.Rect(bounds.x + 14, bounds.y + Math.max(12, (bounds.height - panelHeight) / 2),
                panelWidth, panelHeight);
    }

    private void drawTextField(FontRenderer fontRenderer, ModernTextField field, ModernMainLayout.Rect bounds, int mouseX,
            int mouseY) {
        boolean focused = field != null && field.isFocused();
        boolean hovered = contains(bounds, mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (field == null) {
            return;
        }
        field.x = bounds.x + 7;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 14);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean primary,
            int mouseX, int mouseY) {
        boolean hovered = contains(bounds, mouseX, mouseY);
        int fill = primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = primary ? 0xFFFFA4BC : ModernUiRenderer.BORDER;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int textWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(5, (bounds.width - textWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, bounds.width - 10);
    }

    private void drawInfoIcon(FontRenderer fontRenderer, int x, int y, String tooltip, int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredInfoTooltip = tooltip == null ? "" : tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private void showStatus(String message) {
        showStatus(message, false);
    }

    private void showStatus(String message, boolean error) {
        statusMessage = message == null ? "" : message;
        statusError = error;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static String t(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String t(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }
}
