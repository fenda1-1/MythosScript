package com.zszl.zszlScriptMod.gui.path;

import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.system.KeybindManager;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Modern editor for the workflow/node-editor shortcut map. */
public class GuiNodeEditorHotkeyManager extends ThemedGuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {

    private static final int BTN_SAVE = 1000;
    private static final int BTN_DISCARD = 1001;
    private static final int ROW_HEIGHT = 30;

    private final GuiScreen parentScreen;
    private GuiButton[] changeButtons = new GuiButton[0];
    private GuiButton saveButton;
    private GuiButton discardButton;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int rowViewportY;
    private int rowViewportHeight;
    private int footerY;
    private int actionScroll;
    private int maxActionScroll;
    private boolean loaded;

    private Rectangle backBounds;
    private Rectangle titleInfoBounds;
    private Rectangle listInfoBounds;
    private Rectangle saveInfoBounds;
    private Rectangle discardInfoBounds;
    private final List<Rectangle> actionInfoBounds = new ArrayList<>();

    public GuiNodeEditorHotkeyManager(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        if (!loaded) {
            NodeEditorHotkeyManager.load();
            loaded = true;
        }
        computeLayout();

        NodeEditorHotkeyManager.Action[] actions = NodeEditorHotkeyManager.Action.values();
        changeButtons = new GuiButton[actions.length];
        for (int i = 0; i < actions.length; i++) {
            changeButtons[i] = new ThemedButton(i, 0, 0, 82, 20, I18n.format("gui.debug_keybind.change"));
            this.buttonList.add(changeButtons[i]);
        }
        saveButton = new ThemedButton(BTN_SAVE, 0, 0, 122, 20, I18n.format("gui.debug_keybind.save_close"));
        discardButton = new ThemedButton(BTN_DISCARD, 0, 0, 122, 20,
                I18n.format("gui.debug_keybind.back_no_save"));
        this.buttonList.add(saveButton);
        this.buttonList.add(discardButton);
        layoutButtons();
    }

    private void computeLayout() {
        panelWidth = Math.max(1, Math.min(620, this.width - 24));
        panelHeight = Math.max(1, Math.min(520, this.height - 24));
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        contentX = panelX + 12;
        contentY = panelY + 48;
        contentWidth = Math.max(1, panelWidth - 24);
        footerY = panelY + panelHeight - 32;
        contentHeight = Math.max(34, footerY - contentY - 10);
        rowViewportY = contentY + 28;
        rowViewportHeight = Math.max(28, contentHeight - 32);
    }

    private void layoutButtons() {
        NodeEditorHotkeyManager.Action[] actions = NodeEditorHotkeyManager.Action.values();
        int visibleRows = Math.max(1, rowViewportHeight / ROW_HEIGHT);
        maxActionScroll = Math.max(0, actions.length - visibleRows);
        actionScroll = clamp(actionScroll, 0, maxActionScroll);
        int changeWidth = Math.max(48, Math.min(82, contentWidth / 3));
        int buttonX = contentX + contentWidth - changeWidth - 18;
        for (int i = 0; i < changeButtons.length; i++) {
            GuiButton button = changeButtons[i];
            boolean visible = i >= actionScroll && i < actionScroll + visibleRows;
            button.visible = visible;
            if (visible) {
                int rowY = rowViewportY + (i - actionScroll) * ROW_HEIGHT;
                button.x = buttonX;
                button.y = rowY + 3;
                button.width = changeWidth;
                button.height = 20;
            }
        }

        int footerGap = Math.max(2, Math.min(8, contentWidth / 8));
        int availableFooterWidth = Math.max(2, contentWidth - footerGap);
        int saveWidth = Math.max(1, availableFooterWidth / 2);
        int discardWidth = Math.max(1, availableFooterWidth - saveWidth);
        saveButton.x = contentX;
        saveButton.y = footerY;
        saveButton.width = saveWidth;
        saveButton.height = 20;
        discardButton.x = contentX + saveWidth + footerGap;
        discardButton.y = footerY;
        discardButton.width = discardWidth;
        discardButton.height = 20;
    }

    private void returnWithoutSaving() {
        NodeEditorHotkeyManager.load();
        loaded = true;
        this.mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        NodeEditorHotkeyManager.Action[] actions = NodeEditorHotkeyManager.Action.values();
        if (button.id >= 0 && button.id < actions.length) {
            final NodeEditorHotkeyManager.Action action = actions[button.id];
            mc.displayGuiScreen(new GuiKeybindRecorder(this, NodeEditorHotkeyManager.get(action), newKeybind -> {
                if (newKeybind != null && newKeybind.getKeyCode() != Keyboard.KEY_NONE) {
                    NodeEditorHotkeyManager.set(action, newKeybind);
                } else {
                    NodeEditorHotkeyManager.set(action, new KeybindManager.Keybind());
                }
                mc.displayGuiScreen(this);
            }));
            return;
        }
        if (button.id == BTN_SAVE) {
            NodeEditorHotkeyManager.save();
            mc.displayGuiScreen(parentScreen);
            return;
        }
        if (button.id == BTN_DISCARD) {
            returnWithoutSaving();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        computeLayout();
        layoutButtons();
        actionInfoBounds.clear();

        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawSubtlePanel(panelX + 7, panelY + 7, panelWidth - 14, 32, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);

        backBounds = new Rectangle(panelX + 12, panelY + 12, 22, 22);
        ModernRuleEditorUi.drawBackButton(backBounds.x, backBounds.y, mouseX, mouseY);
        String title = "节点编辑器快捷键";
        ModernUiRenderer.drawText(this.fontRenderer, title, panelX + 45, panelY + 13, ModernUiRenderer.TEXT,
                Math.max(90, panelWidth - 98));
        titleInfoBounds = new Rectangle(panelX + panelWidth - 28, panelY + 12, 11, 11);
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, titleInfoBounds.x,
                titleInfoBounds.y, "为节点编辑器中的常用操作配置快捷键。修改后点击保存并关闭才会写入当前档案。", mouseX,
                mouseY);

        ModernUiRenderer.drawSubtlePanel(contentX, contentY, contentWidth, contentHeight, 6,
                0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, "操作", contentX + 10, contentY + 8, ModernUiRenderer.TEXT,
                Math.max(60, contentWidth / 2 - 30));
        ModernUiRenderer.drawText(this.fontRenderer, "当前快捷键", contentX + contentWidth / 2 - 42, contentY + 8,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(80, contentWidth / 2 - 108));
        listInfoBounds = new Rectangle(contentX + contentWidth - 22, contentY + 7, 11, 11);
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, listInfoBounds.x, listInfoBounds.y,
                "点击右侧“更改”录制新的按键或组合键。滚轮可查看全部操作。", mouseX, mouseY);
        ModernUiRenderer.drawDivider(contentX + 8, contentY + 24, contentWidth - 16, ModernUiRenderer.BORDER_SUBTLE);

        NodeEditorHotkeyManager.Action[] actions = NodeEditorHotkeyManager.Action.values();
        int visibleRows = Math.max(1, rowViewportHeight / ROW_HEIGHT);
        int end = Math.min(actions.length, actionScroll + visibleRows);
        for (int i = actionScroll; i < end; i++) {
            int rowY = rowViewportY + (i - actionScroll) * ROW_HEIGHT;
            boolean hovered = ModernRuleEditorUi.contains(contentX + 4, rowY, contentWidth - 18, ROW_HEIGHT - 2,
                    mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(contentX + 4, rowY, contentWidth - 18, ROW_HEIGHT - 2, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(contentX + 4, rowY, 3, ROW_HEIGHT - 2, 2,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.ACCENT_DIM);

            NodeEditorHotkeyManager.Action action = actions[i];
            ModernUiRenderer.drawText(this.fontRenderer, action.getDisplayName(), contentX + 13, rowY + 6,
                    ModernUiRenderer.TEXT, Math.max(60, contentWidth / 2 - 26));
            KeybindManager.Keybind keybind = NodeEditorHotkeyManager.get(action);
            String keyName = keybind == null || keybind.getKeyCode() == Keyboard.KEY_NONE
                    ? I18n.format("gui.debug_keybind.unbound")
                    : keybind.toString();
            ModernUiRenderer.drawText(this.fontRenderer, keyName, contentX + contentWidth / 2 - 42, rowY + 6,
                    keybind == null || keybind.getKeyCode() == Keyboard.KEY_NONE
                            ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUCCESS,
                    Math.max(54, contentWidth / 2 - 150));

            Rectangle info = new Rectangle(changeButtons[i].x + changeButtons[i].width - 16, rowY + 8, 11, 11);
            actionInfoBounds.add(info);
        }

        if (maxActionScroll > 0) {
            int trackHeight = Math.max(12, rowViewportHeight - 4);
            int thumbHeight = Math.max(12, (int) ((float) visibleRows / actions.length * trackHeight));
            int thumbY = rowViewportY + (int) ((float) actionScroll / maxActionScroll * (trackHeight - thumbHeight));
            ModernRuleEditorUi.drawScrollbar(contentX + contentWidth - 6, rowViewportY, trackHeight, thumbY,
                    thumbHeight);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        ModernRuleEditorUi.drawButton(this.fontRenderer, saveButton, mouseX, mouseY,
                ModernRuleEditorUi.ButtonTone.PRIMARY);
        ModernRuleEditorUi.drawButton(this.fontRenderer, discardButton, mouseX, mouseY,
                ModernRuleEditorUi.ButtonTone.DEFAULT);
        for (GuiButton button : changeButtons) {
            if (button != null && button.visible) {
                ModernRuleEditorUi.drawButton(this.fontRenderer, button, mouseX, mouseY,
                        ModernRuleEditorUi.ButtonTone.DEFAULT);
            }
        }
        for (int i = actionScroll; i < end; i++) {
            Rectangle info = actionInfoBounds.get(i - actionScroll);
            ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, info.x, info.y,
                    "更改“" + actions[i].getDisplayName() + "”的快捷键。组合键可使用 Ctrl、Shift 或 Alt。", mouseX,
                    mouseY);
        }
        saveInfoBounds = new Rectangle(saveButton.x + saveButton.width - 16,
                saveButton.y + (saveButton.height - 11) / 2, 11, 11);
        discardInfoBounds = new Rectangle(discardButton.x + discardButton.width - 16,
                discardButton.y + (discardButton.height - 11) / 2, 11, 11);
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, saveInfoBounds.x, saveInfoBounds.y,
                "保存当前快捷键映射并返回上一级。", mouseX, mouseY);
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, discardInfoBounds.x,
                discardInfoBounds.y, "放弃本次未保存的修改并返回上一级。", mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && ModernSelectorUi.contains(backBounds, mouseX, mouseY)) {
            returnWithoutSaving();
            return;
        }
        if (isInfoIconHit(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isInfoIconHit(int mouseX, int mouseY) {
        if (ModernSelectorUi.contains(titleInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(listInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(saveInfoBounds, mouseX, mouseY)
                || ModernSelectorUi.contains(discardInfoBounds, mouseX, mouseY)) {
            return true;
        }
        for (Rectangle bounds : actionInfoBounds) {
            if (ModernSelectorUi.contains(bounds, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            returnWithoutSaving();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0 || maxActionScroll <= 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (ModernRuleEditorUi.contains(contentX, contentY, contentWidth, contentHeight, mouseX, mouseY)) {
            actionScroll = clamp(actionScroll - Integer.signum(dWheel), 0, maxActionScroll);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
