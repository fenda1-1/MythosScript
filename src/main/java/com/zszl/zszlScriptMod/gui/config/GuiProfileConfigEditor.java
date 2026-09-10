package com.zszl.zszlScriptMod.gui.config;

import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;
import com.zszl.zszlScriptMod.gui.GuiInventoryConfirmScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.otherfeatures.gui.common.ModernFeatureConfigUi;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.io.StringReader;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.stream.JsonReader;

public class GuiProfileConfigEditor extends ThemedGuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {

    private static final Pattern JSON_LOCATION_PATTERN = Pattern.compile("line\\s+(\\d+)\\s+column\\s+(\\d+)");

    private final GuiScreen parentScreen;
    private final String profileName;
    private final String relativePath;

    private final List<String> lines = new ArrayList<>();
    private String originalContent = "";

    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int horizontalScrollColumn = 0;
    private boolean draggingScrollbar;
    private Rectangle scrollbarBounds;
    private Rectangle scrollbarThumbBounds;

    private String statusMessage = "§7Ctrl+S 保存，保存前会自动校验；失败会定位错误行，可一键回滚";
    private int statusColor = 0xFFB8C7D9;
    private boolean dirty = false;
    private int highlightedErrorLine = -1;

    public GuiProfileConfigEditor(GuiScreen parentScreen, String profileName, String relativePath, String content) {
        this.parentScreen = parentScreen;
        this.profileName = profileName == null ? "" : profileName;
        this.relativePath = relativePath == null ? "" : relativePath;
        this.originalContent = content == null ? "" : content;
        setContent(this.originalContent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelWidth = getPanelWidth();
        int bottomY = panelY + getPanelHeight() - 28;
        int footerGap = 5;
        int footerWidth = Math.max(38, (panelWidth - 20 - footerGap * 4) / 5);
        int footerX = panelX + 10;
        this.buttonList.add(new ThemedButton(0, footerX, bottomY, footerWidth, 20, "§a保存当前文件"));
        this.buttonList.add(new ThemedButton(1, footerX + (footerWidth + footerGap), bottomY, footerWidth, 20,
                "§e重新载入"));
        this.buttonList.add(new ThemedButton(2, footerX + (footerWidth + footerGap) * 2, bottomY, footerWidth, 20,
                "§b复制全文"));
        this.buttonList.add(new ThemedButton(3, footerX + (footerWidth + footerGap) * 3, bottomY, footerWidth, 20,
                "§6回滚到已保存"));
        this.buttonList.add(new ThemedButton(4, footerX + (footerWidth + footerGap) * 4, bottomY, footerWidth, 20,
                "§c返回"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(0),
                ModernFeatureConfigUi.tooltip("保存当前文件", "校验通过后写入当前配置文件。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(1),
                ModernFeatureConfigUi.tooltip("重新载入", "从磁盘重新读取文件并放弃未保存修改。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(2),
                ModernFeatureConfigUi.tooltip("复制全文", "将当前编辑器内容复制到剪贴板。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(3),
                ModernFeatureConfigUi.tooltip("回滚", "恢复到最近一次成功保存的内容。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(4),
                ModernFeatureConfigUi.tooltip("返回", "关闭编辑器并返回配置管理。"));
        recalcScrollBounds();
        clampCursor();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                saveCurrentFile();
                break;
            case 1:
                reloadFromDisk();
                break;
            case 2:
                setClipboardString(getContent());
                setStatus("§a已复制当前配置全文到剪贴板", 0xFF8CFF9E);
                break;
            case 3:
                rollbackToLastSaved();
                break;
            case 4:
                requestClose();
                break;
            default:
                break;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            if (isInsideEditor(mouseX, mouseY)) {
                if (wheel > 0) {
                    scrollOffset = Math.max(0, scrollOffset - 3);
                } else {
                    scrollOffset = Math.min(maxScroll, scrollOffset + 3);
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && scrollbarBounds != null && scrollbarBounds.contains(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return;
        }
        if (ModernTooltipSupport.isInfoIconHit(this, this.buttonList, mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && isInsideEditor(mouseX, mouseY)) {
            moveCursorFromMouse(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar && clickedMouseButton == 0) {
            updateScrollFromMouse(mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (GuiScreen.isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_S) {
                saveCurrentFile();
                return;
            }
            if (keyCode == Keyboard.KEY_C) {
                setClipboardString(getContent());
                setStatus("§a已复制当前配置全文到剪贴板", 0xFF8CFF9E);
                return;
            }
            if (keyCode == Keyboard.KEY_V) {
                String text = GuiScreen.getClipboardString();
                if (text != null && !text.isEmpty()) {
                    insertText(text);
                }
                return;
            }
            if (keyCode == Keyboard.KEY_A) {
                cursorLine = lines.size() - 1;
                cursorColumn = lines.isEmpty() ? 0 : lines.get(cursorLine).length();
                ensureCursorVisible();
                return;
            }
        }

        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                requestClose();
                return;
            case Keyboard.KEY_BACK:
                backspace();
                return;
            case Keyboard.KEY_DELETE:
                deleteForward();
                return;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                newline();
                return;
            case Keyboard.KEY_TAB:
                insertText("    ");
                return;
            case Keyboard.KEY_LEFT:
                moveLeft();
                return;
            case Keyboard.KEY_RIGHT:
                moveRight();
                return;
            case Keyboard.KEY_UP:
                moveUp();
                return;
            case Keyboard.KEY_DOWN:
                moveDown();
                return;
            case Keyboard.KEY_HOME:
                cursorColumn = 0;
                ensureCursorVisible();
                return;
            case Keyboard.KEY_END:
                cursorColumn = currentLine().length();
                ensureCursorVisible();
                return;
            case Keyboard.KEY_PRIOR:
                scrollOffset = Math.max(0, scrollOffset - getVisibleLineCount());
                cursorLine = Math.max(0, cursorLine - getVisibleLineCount());
                clampCursor();
                ensureCursorVisible();
                return;
            case Keyboard.KEY_NEXT:
                scrollOffset = Math.min(maxScroll, scrollOffset + getVisibleLineCount());
                cursorLine = Math.min(lines.size() - 1, cursorLine + getVisibleLineCount());
                clampCursor();
                ensureCursorVisible();
                return;
            default:
                break;
        }

        if (typedChar >= 32 && typedChar != 127) {
            insertText(String.valueOf(typedChar));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 8, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawSubtlePanel(panelX + 8, panelY + 7, panelWidth - 16, 34, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, "配置编辑器 - " + profileName + " / " + relativePath,
                panelX + 15, panelY + 12, ModernUiRenderer.TEXT, panelWidth - 30);

        int pathY = panelY + 48;
        ModernUiRenderer.drawText(this.fontRenderer, "文件: " + relativePath, panelX + 10, pathY,
                ModernUiRenderer.SUBTLE_TEXT, panelWidth - 170);
        ModernUiRenderer.drawText(this.fontRenderer, dirty ? "状态: 未保存修改" : "状态: 已保存",
                panelX + panelWidth - 132, pathY,
                dirty ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS, 122);

        int statusY = panelY + 62;
        ModernUiRenderer.drawSubtlePanel(panelX + 10, statusY, panelWidth - 20, 18, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, statusMessage, panelX + 14, statusY + 5, statusColor,
                panelWidth - 28);

        int editorX = getEditorX();
        int editorY = getEditorY();
        int editorW = getEditorWidth();
        int editorH = getEditorHeight();

        ModernUiRenderer.drawSubtlePanel(editorX, editorY, editorW, editorH, 5,
                0xFF101820, ModernUiRenderer.BORDER);

        int lineNumberWidth = 40;
        ModernUiRenderer.drawVerticalDivider(editorX + lineNumberWidth, editorY + 1, editorH - 2,
                ModernUiRenderer.BORDER_SUBTLE);

        int visibleCount = getVisibleLineCount();
        for (int local = 0; local < visibleCount; local++) {
            int lineIndex = scrollOffset + local;
            if (lineIndex >= lines.size()) {
                break;
            }

            int drawY = editorY + 4 + local * getLineHeight();
            String lineNo = String.valueOf(lineIndex + 1);
            String lineText = lines.get(lineIndex);
            boolean errorLine = lineIndex == highlightedErrorLine;

            if (errorLine) {
                ModernUiRenderer.drawRoundedRect(editorX + 1, drawY - 1, editorW - 10, getLineHeight(), 2,
                        0x44D65D6D);
            }

            ModernUiRenderer.drawText(this.fontRenderer, lineNo,
                    editorX + lineNumberWidth - 4 - this.fontRenderer.getStringWidth(lineNo),
                    drawY, ModernUiRenderer.MUTED_TEXT, lineNumberWidth - 6);

            int maxTextWidth = getEditorTextWidth();
            int visibleStart = Math.min(horizontalScrollColumn, lineText.length());
            String clipped = this.fontRenderer.trimStringToWidth(lineText.substring(visibleStart), maxTextWidth);
            ModernUiRenderer.drawText(this.fontRenderer, clipped, editorX + lineNumberWidth + 6, drawY,
                    ModernUiRenderer.TEXT, maxTextWidth);

            if (lineIndex == cursorLine && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int cursorX = editorX + lineNumberWidth + 6
                        + this.fontRenderer.getStringWidth(clampTextToCursor(lineText, cursorColumn, maxTextWidth,
                                visibleStart));
                ModernUiRenderer.drawRoundedRect(cursorX, drawY - 1, 1, 10, 0, ModernUiRenderer.TEXT);
            }
        }

        if (maxScroll > 0) {
            int scrollbarX = editorX + editorW - 8;
            int thumbHeight = Math.max(18, (int) ((visibleCount / (float) Math.max(visibleCount, lines.size())) * editorH));
            int track = Math.max(1, editorH - thumbHeight);
            int thumbY = editorY + (int) ((scrollOffset / (float) Math.max(1, maxScroll)) * track);
            scrollbarBounds = new Rectangle(scrollbarX - 4, editorY, 10, editorH);
            scrollbarThumbBounds = new Rectangle(scrollbarX - 2, thumbY, 6, thumbHeight);
            ModernFeatureConfigUi.drawScrollbar(scrollbarX, editorY, editorH, thumbY, thumbHeight);
        } else {
            scrollbarBounds = null;
            scrollbarThumbBounds = null;
        }

        ModernUiRenderer.drawText(this.fontRenderer,
                "行: " + (cursorLine + 1) + "  列: " + (cursorColumn + 1) + "  总行数: " + lines.size(),
                panelX + 10, panelY + panelHeight - 40, ModernUiRenderer.MUTED_TEXT, panelWidth - 20);
        for (GuiButton button : this.buttonList) {
            ModernFeatureConfigUi.drawButton(this.fontRenderer, button, mouseX, mouseY, button.id == 0,
                    button.id == 3);
            ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                    button.x + button.width - 16, button.y + 5, tooltipForButton(button));
        }
        ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                panelX + panelWidth - 25, panelY + 10,
                ModernFeatureConfigUi.tooltip("配置编辑器", "支持 JSON 与 lang 文件编辑；保存前会执行语法校验。"));
    }

    private void saveCurrentFile() {
        ValidationResult validation = validateCurrentContent();
        if (!validation.valid) {
            applyValidationFailure(validation);
            return;
        }
        try {
            ProfileShareCodeManager.saveProfileFileContent(profileName, relativePath, getContent());
            originalContent = getContent();
            dirty = false;
            highlightedErrorLine = -1;
            setStatus("§a已保存当前配置文件", 0xFF8CFF9E);
        } catch (Exception e) {
            setStatus("§c保存失败: " + e.getMessage(), 0xFFFF8E8E);
        }
    }

    private void reloadFromDisk() {
        if (dirty) {
            this.mc.displayGuiScreen(new GuiInventoryConfirmScreen(this, "重新载入文件",
                    "当前文件有未保存修改，重新载入会丢弃这些内容。", this::reloadFromDiskNow,
                    "重新载入", "继续编辑"));
            return;
        }
        reloadFromDiskNow();
    }

    private void reloadFromDiskNow() {
        try {
            originalContent = ProfileShareCodeManager.loadProfileFileContent(profileName, relativePath);
            setContent(originalContent);
            dirty = false;
            highlightedErrorLine = -1;
            setStatus("§a已重新从磁盘载入", 0xFF8CFF9E);
        } catch (Exception e) {
            setStatus("§c重新载入失败: " + e.getMessage(), 0xFFFF8E8E);
        }
    }

    private void requestClose() {
        if (!dirty) {
            this.mc.displayGuiScreen(parentScreen);
            return;
        }
        this.mc.displayGuiScreen(new GuiInventoryConfirmScreen(this, "放弃未保存修改",
                "当前文件还有未保存内容，返回后这些修改将被丢弃。",
                () -> this.mc.displayGuiScreen(parentScreen), "放弃修改", "继续编辑"));
    }

    private void rollbackToLastSaved() {
        setContent(originalContent);
        dirty = false;
        highlightedErrorLine = -1;
        setStatus("§a已回滚到上次保存内容", 0xFF8CFF9E);
    }

    private void setContent(String content) {
        lines.clear();
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);
        for (String line : split) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        cursorLine = 0;
        cursorColumn = 0;
        scrollOffset = 0;
        horizontalScrollColumn = 0;
        recalcScrollBounds();
        ensureCursorVisible();
        highlightedErrorLine = -1;
    }

    private String getContent() {
        return String.join("\n", lines);
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String current = currentLine();
        String before = current.substring(0, Math.min(cursorColumn, current.length()));
        String after = current.substring(Math.min(cursorColumn, current.length()));

        String[] parts = normalized.split("\n", -1);
        if (parts.length == 1) {
            lines.set(cursorLine, before + parts[0] + after);
            cursorColumn = before.length() + parts[0].length();
        } else {
            lines.set(cursorLine, before + parts[0]);
            int insertLine = cursorLine + 1;
            for (int i = 1; i < parts.length; i++) {
                String segment = parts[i];
                if (i == parts.length - 1) {
                    lines.add(insertLine, segment + after);
                } else {
                    lines.add(insertLine, segment);
                }
                insertLine++;
            }
            cursorLine += parts.length - 1;
            cursorColumn = parts[parts.length - 1].length();
        }
        markDirty();
    }

    private void newline() {
        String current = currentLine();
        String before = current.substring(0, Math.min(cursorColumn, current.length()));
        String after = current.substring(Math.min(cursorColumn, current.length()));
        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, after);
        cursorLine++;
        cursorColumn = 0;
        markDirty();
    }

    private void backspace() {
        if (cursorColumn > 0) {
            String current = currentLine();
            lines.set(cursorLine, current.substring(0, cursorColumn - 1) + current.substring(cursorColumn));
            cursorColumn--;
            markDirty();
            return;
        }
        if (cursorLine > 0) {
            String current = currentLine();
            int previousLength = lines.get(cursorLine - 1).length();
            lines.set(cursorLine - 1, lines.get(cursorLine - 1) + current);
            lines.remove(cursorLine);
            cursorLine--;
            cursorColumn = previousLength;
            markDirty();
        }
    }

    private void deleteForward() {
        String current = currentLine();
        if (cursorColumn < current.length()) {
            lines.set(cursorLine, current.substring(0, cursorColumn) + current.substring(cursorColumn + 1));
            markDirty();
            return;
        }
        if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, current + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
            markDirty();
        }
    }

    private void moveLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = lines.get(cursorLine).length();
        }
        ensureCursorVisible();
    }

    private void moveRight() {
        if (cursorColumn < currentLine().length()) {
            cursorColumn++;
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorColumn = 0;
        }
        ensureCursorVisible();
    }

    private void moveUp() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
            ensureCursorVisible();
        }
    }

    private void moveDown() {
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
            ensureCursorVisible();
        }
    }

    private void moveCursorFromMouse(int mouseX, int mouseY) {
        int localY = mouseY - getEditorY() - 4;
        int lineIndex = scrollOffset + Math.max(0, localY / getLineHeight());
        lineIndex = Math.max(0, Math.min(lines.size() - 1, lineIndex));
        cursorLine = lineIndex;

        String line = lines.get(cursorLine);
        int textX = getEditorX() + 46;
        int relativeX = Math.max(0, mouseX - textX);
        int visibleStart = Math.min(horizontalScrollColumn, line.length());
        int bestColumn = visibleStart;
        for (int i = visibleStart + 1; i <= line.length(); i++) {
            String sub = line.substring(visibleStart, i);
            if (this.fontRenderer.getStringWidth(sub) > relativeX) {
                break;
            }
            bestColumn = i;
        }
        cursorColumn = bestColumn;
        ensureCursorVisible();
    }

    private boolean isInsideEditor(int mouseX, int mouseY) {
        return mouseX >= getEditorX() && mouseX <= getEditorX() + getEditorWidth()
                && mouseY >= getEditorY() && mouseY <= getEditorY() + getEditorHeight();
    }

    private void markDirty() {
        dirty = true;
        recalcScrollBounds();
        clampCursor();
        ensureCursorVisible();
        highlightedErrorLine = -1;
    }

    private void recalcScrollBounds() {
        maxScroll = Math.max(0, lines.size() - getVisibleLineCount());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void ensureCursorVisible() {
        clampCursor();
        if (cursorLine < scrollOffset) {
            scrollOffset = cursorLine;
        }
        int visible = getVisibleLineCount();
        if (cursorLine >= scrollOffset + visible) {
            scrollOffset = cursorLine - visible + 1;
        }
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        String line = currentLine();
        horizontalScrollColumn = Math.max(0, Math.min(horizontalScrollColumn, line.length()));
        if (this.fontRenderer == null) {
            return;
        }
        int maxTextWidth = getEditorTextWidth();
        int cursorWidth = Math.max(8, maxTextWidth - 8);
        while (horizontalScrollColumn < cursorColumn
                && this.fontRenderer.getStringWidth(line.substring(horizontalScrollColumn, cursorColumn)) > cursorWidth) {
            horizontalScrollColumn++;
        }
        while (horizontalScrollColumn > 0
                && this.fontRenderer.getStringWidth(line.substring(horizontalScrollColumn - 1, cursorColumn)) <= cursorWidth) {
            horizontalScrollColumn--;
        }
    }

    private void clampCursor() {
        if (lines.isEmpty()) {
            lines.add("");
        }
        cursorLine = Math.max(0, Math.min(cursorLine, lines.size() - 1));
        cursorColumn = Math.max(0, Math.min(cursorColumn, currentLine().length()));
    }

    private String currentLine() {
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines.get(Math.max(0, Math.min(cursorLine, lines.size() - 1)));
    }

    private String clampTextToCursor(String line, int cursor, int maxTextWidth, int visibleStart) {
        int safeCursor = Math.max(0, Math.min(cursor, line.length()));
        int safeStart = Math.max(0, Math.min(visibleStart, safeCursor));
        return this.fontRenderer.trimStringToWidth(line.substring(safeStart, safeCursor), maxTextWidth);
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message == null ? "" : message;
        this.statusColor = color;
    }

    private int getEditorTextWidth() {
        return Math.max(1, getEditorWidth() - 40 - 24);
    }

    private void updateScrollFromMouse(int mouseY) {
        if (scrollbarBounds == null || scrollbarThumbBounds == null || maxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, scrollbarBounds.height - scrollbarThumbBounds.height);
        int target = Math.max(scrollbarBounds.y,
                Math.min(scrollbarBounds.y + scrollbarBounds.height - scrollbarThumbBounds.height,
                        mouseY - scrollbarThumbBounds.height / 2));
        scrollOffset = Math.round((target - scrollbarBounds.y) * maxScroll / (float) travel);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private String tooltipForButton(GuiButton button) {
        if (button == null) {
            return "";
        }
        switch (button.id) {
            case 0:
                return ModernFeatureConfigUi.tooltip("保存当前文件", "校验通过后写入当前配置文件。");
            case 1:
                return ModernFeatureConfigUi.tooltip("重新载入", "从磁盘重新读取文件并放弃未保存修改。");
            case 2:
                return ModernFeatureConfigUi.tooltip("复制全文", "将当前编辑器内容复制到剪贴板。");
            case 3:
                return ModernFeatureConfigUi.tooltip("回滚", "恢复到最近一次成功保存的内容。");
            default:
                return ModernFeatureConfigUi.tooltip("返回", "关闭编辑器并返回配置管理。");
        }
    }

    private ValidationResult validateCurrentContent() {
        String normalizedPath = this.relativePath == null ? "" : this.relativePath.trim().toLowerCase(Locale.ROOT);
        String content = getContent();
        if (normalizedPath.endsWith(".json")) {
            return validateJsonContent(content);
        }
        if (normalizedPath.endsWith(".lang")) {
            return validateLangContent(content);
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateJsonContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.error("JSON 文件不能为空", 1, 1);
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(content));
            reader.setLenient(false);
            new JsonParser().parse(reader);
            return ValidationResult.ok();
        } catch (Exception e) {
            int line = 1;
            int column = 1;
            Matcher matcher = JSON_LOCATION_PATTERN.matcher(String.valueOf(e.getMessage()));
            if (matcher.find()) {
                try {
                    line = Math.max(1, Integer.parseInt(matcher.group(1)));
                    column = Math.max(1, Integer.parseInt(matcher.group(2)));
                } catch (Exception ignored) {
                }
            }
            return ValidationResult.error("JSON 语法错误: " + safeMessage(e.getMessage()), line, column);
        }
    }

    private ValidationResult validateLangContent(String content) {
        String[] split = (content == null ? "" : content).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < split.length; i++) {
            String rawLine = split[i];
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = rawLine.indexOf('=');
            if (separator < 0) {
                return ValidationResult.error("lang 文件必须使用 key=value 格式", i + 1, 1);
            }
            String key = rawLine.substring(0, separator).trim();
            if (key.isEmpty()) {
                return ValidationResult.error("lang 键名不能为空", i + 1, 1);
            }
            if (!keys.add(key)) {
                return ValidationResult.error("lang 键重复: " + key, i + 1, 1);
            }
        }
        return ValidationResult.ok();
    }

    private void applyValidationFailure(ValidationResult validation) {
        highlightedErrorLine = validation.line - 1;
        cursorLine = Math.max(0, Math.min(lines.size() - 1, highlightedErrorLine));
        cursorColumn = Math.max(0, validation.column - 1);
        ensureCursorVisible();
        setStatus("§c保存已拦截: 第 " + validation.line + " 行，第 " + validation.column + " 列 - " + validation.message,
                0xFFFF8E8E);
    }

    private String safeMessage(String message) {
        return message == null ? "未知错误" : message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final class ValidationResult {
        private final boolean valid;
        private final String message;
        private final int line;
        private final int column;

        private ValidationResult(boolean valid, String message, int line, int column) {
            this.valid = valid;
            this.message = message == null ? "" : message;
            this.line = Math.max(1, line);
            this.column = Math.max(1, column);
        }

        private static ValidationResult ok() {
            return new ValidationResult(true, "", 1, 1);
        }

        private static ValidationResult error(String message, int line, int column) {
            return new ValidationResult(false, message, line, column);
        }
    }

    private int getPanelWidth() {
        return Math.min(920, this.width - 20);
    }

    private int getPanelHeight() {
        return Math.min(560, this.height - 20);
    }

    private int getPanelX() {
        return (this.width - getPanelWidth()) / 2;
    }

    private int getPanelY() {
        return (this.height - getPanelHeight()) / 2;
    }

    private int getEditorX() {
        return getPanelX() + 10;
    }

    private int getEditorY() {
        return getPanelY() + 84;
    }

    private int getEditorWidth() {
        return getPanelWidth() - 20;
    }

    private int getEditorHeight() {
        return getPanelHeight() - 124;
    }

    private int getVisibleLineCount() {
        return Math.max(1, (getEditorHeight() - 8) / getLineHeight());
    }

    private int getLineHeight() {
        return 10;
    }
}
