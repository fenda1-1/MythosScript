// 文件路径: src/main/java/com/keycommand2/zszlScriptMod/gui/path/GuiKeybindRecorder.java
// (这是一个全新的文件)
package com.zszl.zszlScriptMod.gui.path;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GuiPathingPolicy;

import com.zszl.zszlScriptMod.system.KeybindManager;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 一个用于录制单个或组合按键的GUI界面。
 */
public class GuiKeybindRecorder extends ThemedGuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {

    private final GuiScreen parentScreen;
    private final Consumer<KeybindManager.Keybind> onRecordComplete;
    private KeybindManager.Keybind currentKeybind;
    private boolean isRecording = true; // 默认进入时就是录制状态
    private java.awt.Rectangle panelBounds;
    private java.awt.Rectangle backBounds;
    private java.awt.Rectangle titleInfoBounds;
    private java.awt.Rectangle recordInfoBounds;
    private java.awt.Rectangle confirmInfoBounds;
    private java.awt.Rectangle resetInfoBounds;
    private java.awt.Rectangle cancelInfoBounds;

    public GuiKeybindRecorder(GuiScreen parent, KeybindManager.Keybind existingKeybind,
            Consumer<KeybindManager.Keybind> callback) {
        this.parentScreen = parent;
        this.currentKeybind = copyKeybind(existingKeybind);
        this.onRecordComplete = callback;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int centerX = this.width / 2;
        int panelWidth = Math.max(286, Math.min(390, this.width - 24));
        int panelX = centerX - panelWidth / 2;
        int panelHeight = 158;
        int panelY = this.height / 2 - panelHeight / 2;
        this.panelBounds = new java.awt.Rectangle(panelX, panelY, panelWidth, panelHeight);
        this.backBounds = new java.awt.Rectangle(panelX + 12, panelY + 11, 22, 22);
        int gap = 7;
        int buttonWidth = Math.max(64, (panelWidth - 28 - gap * 2) / 3);
        int buttonY = panelY + panelHeight - 31;

        this.buttonList.add(new ThemedButton(0, panelX + 14, buttonY, buttonWidth, 21,
                I18n.format("gui.loop.confirm")));
        this.buttonList.add(new ThemedButton(1, panelX + 14 + buttonWidth + gap, buttonY, buttonWidth, 21,
                I18n.format("gui.evac_adv.reset")));
        this.buttonList.add(new ThemedButton(2, panelX + 14 + 2 * (buttonWidth + gap), buttonY,
                Math.max(1, panelX + panelWidth - 14 - (panelX + 14 + 2 * (buttonWidth + gap))), 21,
                I18n.format("gui.common.cancel")));
        KeybindManager.setRecording(this.isRecording);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: // 确认
                KeybindManager.setRecording(false);
                onRecordComplete.accept(this.currentKeybind);
                mc.displayGuiScreen(parentScreen);
                break;
            case 1: // 重置
                this.currentKeybind = new KeybindManager.Keybind();
                this.isRecording = true; // 重置后回到录制状态
                KeybindManager.setRecording(true);
                break;
            case 2: // 取消
                KeybindManager.setRecording(false);
                mc.displayGuiScreen(parentScreen);
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // 按下 ESC 总是可以取消
        if (keyCode == Keyboard.KEY_ESCAPE) {
            KeybindManager.setRecording(false);
            mc.displayGuiScreen(parentScreen);
            return;
        }

        if (isRecording) {
            if (keyCode == Keyboard.KEY_NONE) {
                return;
            }

            if (keyCode == Keyboard.KEY_DELETE) {
                this.currentKeybind.clearCombinations();
                return;
            }

            if (keyCode == Keyboard.KEY_BACK) {
                List<KeybindManager.KeyCombination> combinations = this.currentKeybind.getCombinations();
                if (!combinations.isEmpty()) {
                    this.currentKeybind.removeCombination(combinations.size() - 1);
                }
                return;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                this.isRecording = false;
                KeybindManager.setRecording(false);
                onRecordComplete.accept(this.currentKeybind);
                mc.displayGuiScreen(parentScreen);
                return;
            }

            // 单独按下修饰键时，不立即结束录制，继续等待主键
            if (isModifierKey(keyCode)) {
                return;
            }

            this.currentKeybind.addCombination(keyCode, getActiveModifiers());
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && ModernSelectorUi.contains(backBounds, mouseX, mouseY)) {
            KeybindManager.setRecording(false);
            mc.displayGuiScreen(parentScreen);
            return;
        }
        if (ModernSelectorUi.contains(titleInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(recordInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(confirmInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(resetInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(cancelInfoBounds, mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (panelBounds == null) {
            initGui();
        }
        this.drawDefaultBackground();
        int panelX = panelBounds.x;
        int panelY = panelBounds.y;
        int panelWidth = panelBounds.width;
        int panelHeight = panelBounds.height;
        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawSubtlePanel(panelX + 7, panelY + 7, panelWidth - 14, 32, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernRuleEditorUi.drawBackButton(backBounds.x, backBounds.y, mouseX, mouseY);
        String title = I18n.format("gui.path.record_keybind");
        ModernUiRenderer.drawText(fontRenderer, title, panelX + 45, panelY + 13, ModernUiRenderer.TEXT,
                Math.max(80, panelWidth - 70));
        ModernRuleEditorUi.drawInfoIcon(fontRenderer, width, height,
                panelX + 45 + Math.min(panelWidth - 70, fontRenderer.getStringWidth(title) + 7), panelY + 12,
                "可连续录制多个按键组合。按 Enter 保存，Delete 清空，Esc 或返回箭头取消。", mouseX, mouseY);
        titleInfoBounds = new java.awt.Rectangle(panelX + 45
                + Math.min(panelWidth - 70, fontRenderer.getStringWidth(title) + 7), panelY + 12, 11, 11);

        int recordX = panelX + 14;
        int recordY = panelY + 49;
        int recordWidth = panelWidth - 28;
        ModernUiRenderer.drawSubtlePanel(recordX, recordY, recordWidth, 47, 6,
                isRecording ? 0xFF20333D : ModernUiRenderer.SURFACE,
                isRecording ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(recordX + 11, recordY + 12,
                isRecording ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUCCESS);
        ModernUiRenderer.drawText(fontRenderer, isRecording ? "等待按键" : "已记录", recordX + 25, recordY + 8,
                isRecording ? ModernUiRenderer.TEXT : ModernUiRenderer.SUCCESS, recordWidth - 48);
        String displayText = this.currentKeybind.toString();
        if (isRecording) {
            displayText = this.currentKeybind.toString() + "  " + getRecordingDisplayText();
        }
        ModernUiRenderer.drawText(fontRenderer, displayText, recordX + 11, recordY + 24,
                ModernUiRenderer.SUBTLE_TEXT, recordWidth - 30);
        recordInfoBounds = new java.awt.Rectangle(recordX + recordWidth - 19, recordY + 8, 11, 11);
        ModernRuleEditorUi.drawInfoIcon(fontRenderer, width, height, recordInfoBounds.x, recordInfoBounds.y,
                "每次按下主键会加入列表；确认按钮或 Enter 保存，重置按钮清空列表。", mouseX, mouseY);

        GuiButton confirm = this.buttonList.size() > 0 ? this.buttonList.get(0) : null;
        GuiButton reset = this.buttonList.size() > 1 ? this.buttonList.get(1) : null;
        GuiButton cancel = this.buttonList.size() > 2 ? this.buttonList.get(2) : null;
        ModernSelectorUi.drawButton(fontRenderer, confirm, mouseX, mouseY, ModernRuleEditorUi.ButtonTone.PRIMARY);
        ModernSelectorUi.drawButton(fontRenderer, reset, mouseX, mouseY, ModernRuleEditorUi.ButtonTone.DEFAULT);
        ModernSelectorUi.drawButton(fontRenderer, cancel, mouseX, mouseY, ModernRuleEditorUi.ButtonTone.DEFAULT);
        confirmInfoBounds = ModernSelectorUi.infoForButton(confirm);
        resetInfoBounds = ModernSelectorUi.infoForButton(reset);
        cancelInfoBounds = ModernSelectorUi.infoForButton(cancel);
        ModernRuleEditorUi.drawInfoIcon(fontRenderer, width, height, confirmInfoBounds.x, confirmInfoBounds.y,
                "保存当前录制的按键并返回上一页。", mouseX, mouseY);
        ModernRuleEditorUi.drawInfoIcon(fontRenderer, width, height, resetInfoBounds.x, resetInfoBounds.y,
                "清除已录制的按键，重新进入录制状态。", mouseX, mouseY);
        ModernRuleEditorUi.drawInfoIcon(fontRenderer, width, height, cancelInfoBounds.x, cancelInfoBounds.y,
                "放弃本次录制，保留原有绑定。", mouseX, mouseY);
    }

    private boolean isModifierKey(int keyCode) {
        return keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL
                || keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT
                || keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU;
    }

    private Set<Integer> getActiveModifiers() {
        Set<Integer> modifiers = new HashSet<>();
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return modifiers;
    }

    private String getRecordingDisplayText() {
        Set<Integer> modifiers = getActiveModifiers();
        if (modifiers.isEmpty()) {
            return I18n.format("gui.path.press_key");
        }

        List<String> modifierNames = new java.util.ArrayList<>();
        if (modifiers.contains(Keyboard.KEY_LCONTROL)) {
            modifierNames.add("Ctrl");
        }
        if (modifiers.contains(Keyboard.KEY_LSHIFT)) {
            modifierNames.add("Shift");
        }
        if (modifiers.contains(Keyboard.KEY_LMENU)) {
            modifierNames.add("Alt");
        }
        return "§e[" + String.join(" + ", modifierNames) + " + ...]";
    }

    private KeybindManager.Keybind copyKeybind(KeybindManager.Keybind source) {
        if (source == null) {
            return new KeybindManager.Keybind();
        }
        KeybindManager.Keybind copy = new KeybindManager.Keybind();
        copy.setCombinations(source.getCombinations());
        copy.setParameter(source.getParameter());
        return copy;
    }

    @Override
    public void onGuiClosed() {
        KeybindManager.setRecording(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return !GuiPathingPolicy.shouldKeepPathingDuringGui(this.mc);
    }
}
