package com.zszl.zszlScriptMod.gui;

import java.io.IOException;
import java.util.List;

import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public class GuiInventoryConfirmScreen extends ThemedGuiScreen {

    private final GuiScreen parentScreen;
    private final String title;
    private final String message;
    private final Runnable onConfirm;
    private final String confirmLabel;
    private final String cancelLabel;

    public GuiInventoryConfirmScreen(GuiScreen parentScreen, String title, String message, Runnable onConfirm) {
        this(parentScreen, title, message, onConfirm, "确认", "取消");
    }

    public GuiInventoryConfirmScreen(GuiScreen parentScreen, String title, String message, Runnable onConfirm,
            String confirmLabel, String cancelLabel) {
        this.parentScreen = parentScreen;
        this.title = title == null ? "确认操作" : title;
        this.message = com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(message).replace("\\n", "\n");
        this.onConfirm = onConfirm;
        this.confirmLabel = confirmLabel == null || confirmLabel.trim().isEmpty() ? "确认" : confirmLabel;
        this.cancelLabel = cancelLabel == null || cancelLabel.trim().isEmpty() ? "取消" : cancelLabel;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int buttonWidth = Math.min(132, Math.max(72, (panelWidth - 44) / 2));
        int buttonY = panelY + panelHeight - 34;

        this.buttonList.add(new ThemedButton(0, panelX + 16, buttonY, buttonWidth, 20, confirmLabel));
        this.buttonList.add(new ThemedButton(1, panelX + panelWidth - 16 - buttonWidth, buttonY, buttonWidth, 20,
                cancelLabel));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            GuiScreen screenBeforeCallback = this.mc.currentScreen;
            if (onConfirm != null) {
                onConfirm.run();
            }
            if (this.mc.currentScreen == null || this.mc.currentScreen == this || this.mc.currentScreen == screenBeforeCallback) {
                this.mc.displayGuiScreen(parentScreen);
            }
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(parentScreen);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!this.buttonList.isEmpty()) {
                actionPerformed(this.buttonList.get(0));
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        GuiTheme.drawPanel(panelX, panelY, panelWidth, panelHeight);
        GuiTheme.drawTitleBar(panelX, panelY, panelWidth, title, this.fontRenderer);

        List<String> lines = this.fontRenderer.listFormattedStringToWidth(message, panelWidth - 28);
        int textY = panelY + 34;
        for (String line : lines) {
            this.drawString(this.fontRenderer, line, panelX + 14, textY, 0xFFFFFFFF);
            textY += this.fontRenderer.FONT_HEIGHT + 3;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int getPanelWidth() {
        return Math.min(420, Math.max(240, this.width - 24));
    }

    private int getPanelHeight() {
        int panelWidth = getPanelWidth();
        int lineCount = this.fontRenderer == null ? 2
                : Math.max(1, this.fontRenderer.listFormattedStringToWidth(this.message, panelWidth - 28).size());
        return Math.min(Math.max(128, 86 + lineCount * 13), Math.max(112, this.height - 20));
    }
}
