// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/components/GuiTextInput.java
package com.zszl.zszlScriptMod.gui.components;

import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.otherfeatures.gui.common.ModernFeatureConfigUi;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用文本输入GUI
 */
public class GuiTextInput extends ThemedGuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {
    private final GuiScreen parentScreen;
    private final String title;
    private final Consumer<String> callback;
    private final Function<String, String> validator;
    private GuiTextField inputField;
    private String initialText = "";
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private String validationError = "";

    public GuiTextInput(GuiScreen parent, String title, Consumer<String> callback) {
        this(parent, title, "", null, callback);
    }

    public GuiTextInput(GuiScreen parent, String title, String initialText,
            Function<String, String> validator, Consumer<String> callback) {
        this.parentScreen = parent;
        this.title = title;
        this.callback = callback;
        this.validator = validator;
        this.initialText = initialText == null ? "" : initialText;
    }

    public GuiTextInput(GuiScreen parent, String title, String initialText, Consumer<String> callback) {
        this(parent, title, initialText, null, callback);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.panelWidth = Math.min(420, Math.max(260, this.width - 24));
        this.panelHeight = Math.min(190, Math.max(validator == null ? 130 : 150, this.height - 24));
        this.panelX = centerX - this.panelWidth / 2;
        this.panelY = centerY - this.panelHeight / 2;

        this.inputField = new GuiTextField(0, this.fontRenderer, this.panelX + 18, this.panelY + 66,
                this.panelWidth - 36, 22);

        // !! 核心修改：解除输入框的长度限制 !!
        this.inputField.setMaxStringLength(Integer.MAX_VALUE);

        this.inputField.setFocused(true);
        this.inputField.setText(initialText);

        int buttonGap = 8;
        int buttonWidth = (this.panelWidth - 36 - buttonGap) / 2;
        int buttonY = this.panelY + this.panelHeight - 34;
        this.buttonList.add(new GuiButton(0, this.panelX + 18, buttonY, buttonWidth, 22,
                I18n.format("gui.loop.confirm")));
        this.buttonList.add(new GuiButton(1, this.panelX + 18 + buttonWidth + buttonGap, buttonY, buttonWidth, 22,
                I18n.format("gui.common.cancel")));
        ModernTooltipSupport.registerTextField(this, this.inputField);
        ModernTooltipSupport.registerButton(this, this.buttonList.get(0),
                ModernFeatureConfigUi.tooltip("确认", "提交输入内容并返回上一页。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(1),
                ModernFeatureConfigUi.tooltip("取消", "放弃输入并返回上一页。"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) { // 确认
            String value = this.inputField.getText();
            if (this.validator != null) {
                try {
                    this.validationError = normalizeValidationError(this.validator.apply(value));
                } catch (RuntimeException exception) {
                    this.validationError = "输入格式无效";
                }
                if (!this.validationError.isEmpty()) {
                    return;
                }
            }
            GuiScreen screenBeforeCallback = this.mc.currentScreen;
            this.callback.accept(value);
            if (this.mc.currentScreen == null || this.mc.currentScreen == screenBeforeCallback
                    || this.mc.currentScreen == this) {
                this.mc.displayGuiScreen(this.parentScreen);
            }
        } else if (button.id == 1) { // 取消
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        this.inputField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            this.actionPerformed(this.buttonList.get(0));
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            this.actionPerformed(this.buttonList.get(1));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        handleMousePressed(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        int button = Mouse.getEventButton();
        if (button == -1) {
            return;
        }

        if (Mouse.getEventButtonState()) {
            handleMousePressed(mouseX, mouseY, button);
        } else {
            mouseReleased(mouseX, mouseY, button);
        }
    }

    private boolean handleMousePressed(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && handleButtonActivation(mouseX, mouseY)) {
            return true;
        }
        if (isInfoIconHit(mouseX, mouseY)) {
            return true;
        }
        this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
        return false;
    }

    private boolean handleButtonActivation(int mouseX, int mouseY) throws IOException {
        for (GuiButton button : this.buttonList) {
            if (button == null || !button.visible || !button.enabled) {
                continue;
            }
            if (mouseX < button.x || mouseX >= button.x + button.width
                    || mouseY < button.y || mouseY >= button.y + button.height) {
                continue;
            }
            button.playPressSound(this.mc.getSoundHandler());
            actionPerformed(button);
            return true;
        }
        return false;
    }

    private boolean isInfoIconHit(int mouseX, int mouseY) {
        if (this.inputField != null && ModernFeatureConfigUi.contains(this.inputField.x + this.inputField.width - 16,
                this.inputField.y + 5, 11, 11, mouseX, mouseY)) {
            return true;
        }
        return ModernTooltipSupport.isInfoIconHit(this, this.buttonList, mouseX, mouseY);
    }

    @Override
    public void updateScreen() {
        if (this.inputField != null) {
            this.inputField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ModernUiRenderer.drawBackdrop(this.width, this.height);
        ModernFeatureConfigUi.drawShell(this.fontRenderer, this.panelX, this.panelY, this.panelWidth, this.panelHeight,
                this.title, "输入名称或值后确认");
        ModernUiRenderer.drawText(this.fontRenderer, "输入内容", this.panelX + 18, this.panelY + 54,
                ModernUiRenderer.SUBTLE_TEXT, this.panelWidth - 36);
        ModernRuleEditorUi.drawTextField(this, this.inputField);
        ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                this.inputField.x + this.inputField.width - 16, this.inputField.y + 5,
                ModernFeatureConfigUi.tooltip("输入内容", "输入完成后点击确认，或按 Enter 提交。"));
        for (GuiButton button : this.buttonList) {
            ModernFeatureConfigUi.drawButton(this.fontRenderer, button, mouseX, mouseY, button.id == 0, button.id == 1);
            ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                    button.x + button.width - 16, button.y + 5,
                    button.id == 0 ? ModernFeatureConfigUi.tooltip("确认", "提交输入内容并返回上一页。")
                            : ModernFeatureConfigUi.tooltip("取消", "放弃输入并返回上一页。"));
        }
        if (!this.validationError.isEmpty()) {
            ModernUiRenderer.drawText(this.fontRenderer, this.validationError, this.panelX + 18,
                    this.panelY + this.panelHeight - 50, 0xFFFF8E8E, this.panelWidth - 36);
        }
    }

    private String normalizeValidationError(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
