package com.zszl.zszlScriptMod.gui.modern.profile;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/** Native JSON/lang editor overlay for a single profile file. */
final class ProfileFileEditorPanel extends ModernEmbeddedPanel {

    private final ProfileWorkbenchTab owner;
    private final String profileName;
    private final String relativePath;
    private final ProfileFileEditorBuffer buffer = new ProfileFileEditorBuffer();
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final String[] hovered = new String[] { "" };

    private int scrollOffset;
    private int horizontalScroll;
    private int highlightedErrorLine = -1;
    private String status = "Ctrl+S 保存，保存前会自动校验；失败会定位错误行，可一键回滚";
    private int statusColor = ModernUiRenderer.SUBTLE_TEXT;
    private ModernMainLayout.Rect area = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect rollbackBounds;
    private ModernMainLayout.Rect backBounds;
    private int lastMouseX;
    private int lastMouseY;
    private FontRenderer font;

    ProfileFileEditorPanel(ProfileWorkbenchTab owner, String profileName, String relativePath, String content) {
        super("文件编辑器");
        this.owner = owner;
        this.profileName = profileName == null ? "" : profileName;
        this.relativePath = relativePath == null ? "" : relativePath;
        buffer.setContent(content);
    }

    @Override
    public boolean isDirty() {
        return buffer.isDirty();
    }

    @Override
    public void save() {
        saveCurrentFile();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        font = fontRenderer;
        area = bounds == null ? area : bounds;
        setBounds(area);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hovered[0] = "";
        layout();
        ensureCursorVisible();

        ModernUiRenderer.drawPanel(area.x, area.y, area.width, area.height, 7, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "配置编辑器 - " + profileName + " / " + relativePath,
                area.x + 14, area.y + 10, ModernUiRenderer.TEXT, area.width - 40);
        ProfileUi.drawInfo(fontRenderer, area.right() - 22, area.y + 9,
                "支持 JSON 与 lang 文件编辑；保存前会执行语法校验。", mouseX, mouseY, hovered);
        ModernUiRenderer.drawText(fontRenderer, "文件: " + relativePath, area.x + 14, area.y + 28,
                ModernUiRenderer.SUBTLE_TEXT, area.width - 160);
        ModernUiRenderer.drawText(fontRenderer, buffer.isDirty() ? "状态: 未保存修改" : "状态: 已保存",
                area.right() - 132, area.y + 28,
                buffer.isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS, 118);
        ModernUiRenderer.drawSubtlePanel(area.x + 10, area.y + 44, area.width - 20, 18, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(area.x + 16, area.y + 49, statusColor);
        ModernUiRenderer.drawText(fontRenderer, status, area.x + 28, area.y + 48, statusColor, area.width - 48);

        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 5,
                0xFF101820, ModernUiRenderer.BORDER);
        int lineNumberWidth = 40;
        ModernUiRenderer.drawVerticalDivider(editorBounds.x + lineNumberWidth, editorBounds.y + 1,
                editorBounds.height - 2, ModernUiRenderer.BORDER_SUBTLE);

        int lineHeight = lineHeight();
        int visible = visibleLineCount();
        int maxScroll = Math.max(0, buffer.lineCount() - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (int i = 0; i < visible; i++) {
            int lineIndex = scrollOffset + i;
            if (lineIndex >= buffer.lineCount()) {
                break;
            }
            int drawY = editorBounds.y + 4 + i * lineHeight;
            String lineText = buffer.line(lineIndex);
            if (lineIndex == highlightedErrorLine) {
                ModernUiRenderer.drawRoundedRect(editorBounds.x + 1, drawY - 1, editorBounds.width - 10, lineHeight, 2,
                        0x44D65D6D);
            }
            String lineNo = String.valueOf(lineIndex + 1);
            int numberX = editorBounds.x + lineNumberWidth - 6
                    - (fontRenderer == null ? 0 : fontRenderer.getStringWidth(lineNo));
            ModernUiRenderer.drawText(fontRenderer, lineNo, numberX, drawY, ModernUiRenderer.MUTED_TEXT,
                    lineNumberWidth - 8);
            int visibleStart = Math.min(horizontalScroll, lineText.length());
            String clipped = fontRenderer == null ? lineText.substring(visibleStart)
                    : fontRenderer.trimStringToWidth(lineText.substring(visibleStart), editorTextWidth());
            ModernUiRenderer.drawText(fontRenderer, clipped, editorBounds.x + lineNumberWidth + 6, drawY,
                    ModernUiRenderer.TEXT, editorTextWidth());
            if (lineIndex == buffer.cursorLine() && (System.currentTimeMillis() / 500L) % 2L == 0L
                    && fontRenderer != null) {
                int cursorX = editorBounds.x + lineNumberWidth + 6 + fontRenderer.getStringWidth(
                        fontRenderer.trimStringToWidth(lineText.substring(visibleStart,
                                Math.max(visibleStart, Math.min(buffer.cursorColumn(), lineText.length()))),
                                editorTextWidth()));
                ModernUiRenderer.drawRoundedRect(cursorX, drawY - 1, 1, Math.max(8, lineHeight - 2), 0,
                        ModernUiRenderer.TEXT);
            }
        }

        scrollbar.draw(editorBounds, scrollOffset, maxScroll, visible, buffer.lineCount(), mouseX, mouseY,
                new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int value) {
                        scrollOffset = value;
                    }
                });

        ModernUiRenderer.drawText(fontRenderer,
                "行: " + (buffer.cursorLine() + 1) + "  列: " + (buffer.cursorColumn() + 1)
                        + "  总行数: " + buffer.lineCount(),
                area.x + 12, area.bottom() - 40, ModernUiRenderer.MUTED_TEXT, area.width - 24);
        ProfileUi.drawButton(fontRenderer, saveBounds, "保存当前文件", ProfileUi.Tone.PRIMARY, true,
                ProfileUi.hit(saveBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, reloadBounds, "重新载入", ProfileUi.Tone.DEFAULT, true,
                ProfileUi.hit(reloadBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, copyBounds, "复制全文", ProfileUi.Tone.DEFAULT, true,
                ProfileUi.hit(copyBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, rollbackBounds, "回滚到已保存", ProfileUi.Tone.DEFAULT, buffer.isDirty(),
                ProfileUi.hit(rollbackBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, backBounds, "返回", ProfileUi.Tone.DANGER, true,
                ProfileUi.hit(backBounds, mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (scrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (ProfileUi.hit(saveBounds, mouseX, mouseY)) {
            saveCurrentFile();
            return true;
        }
        if (ProfileUi.hit(reloadBounds, mouseX, mouseY)) {
            reloadFromDisk();
            return true;
        }
        if (ProfileUi.hit(copyBounds, mouseX, mouseY)) {
            GuiScreen.setClipboardString(buffer.getContent());
            status("已复制当前配置全文到剪贴板", ModernUiRenderer.SUCCESS);
            return true;
        }
        if (ProfileUi.hit(rollbackBounds, mouseX, mouseY) && buffer.isDirty()) {
            buffer.rollback();
            highlightedErrorLine = -1;
            status("已回滚到上次保存内容", ModernUiRenderer.SUCCESS);
            return true;
        }
        if (ProfileUi.hit(backBounds, mouseX, mouseY)) {
            requestClose();
            return true;
        }
        if (editorBounds != null && editorBounds.contains(mouseX, mouseY)) {
            moveCursorFromMouse(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return scrollbar.applyDrag(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        scrollbar.endDrag();
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (GuiScreen.isCtrlKeyDown()) {
            if (keyCode == Keyboard.KEY_S) {
                saveCurrentFile();
                return true;
            }
            if (keyCode == Keyboard.KEY_C) {
                GuiScreen.setClipboardString(buffer.getContent());
                status("已复制当前配置全文到剪贴板", ModernUiRenderer.SUCCESS);
                return true;
            }
            if (keyCode == Keyboard.KEY_V) {
                String text = GuiScreen.getClipboardString();
                if (text != null && !text.isEmpty()) {
                    buffer.insert(text);
                    highlightedErrorLine = -1;
                }
                return true;
            }
            if (keyCode == Keyboard.KEY_A) {
                buffer.moveEnd();
                ensureCursorVisible();
                return true;
            }
        }
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
                requestClose();
                return true;
            case Keyboard.KEY_BACK:
                buffer.backspace();
                highlightedErrorLine = -1;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_DELETE:
                buffer.deleteForward();
                highlightedErrorLine = -1;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                buffer.newline();
                highlightedErrorLine = -1;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_TAB:
                buffer.insert("    ");
                highlightedErrorLine = -1;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_LEFT:
                buffer.moveLeft();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_RIGHT:
                buffer.moveRight();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_UP:
                buffer.moveUp();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_DOWN:
                buffer.moveDown();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_HOME:
                buffer.moveHome();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_END:
                buffer.moveTo(buffer.cursorLine(), buffer.line(buffer.cursorLine()).length());
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_PRIOR:
                scrollOffset = Math.max(0, scrollOffset - visibleLineCount());
                buffer.moveTo(Math.max(0, buffer.cursorLine() - visibleLineCount()), buffer.cursorColumn());
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_NEXT:
                buffer.moveTo(Math.min(buffer.lineCount() - 1, buffer.cursorLine() + visibleLineCount()),
                        buffer.cursorColumn());
                ensureCursorVisible();
                return true;
            default:
                break;
        }
        if (typedChar >= 32 && typedChar != 127) {
            buffer.insert(String.valueOf(typedChar));
            highlightedErrorLine = -1;
            ensureCursorVisible();
        }
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || editorBounds == null || !editorBounds.contains(lastMouseX, lastMouseY)) {
            return true;
        }
        scrollOffset = Math.max(0, scrollOffset + (wheel > 0 ? -3 : 3));
        return true;
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hovered[0] == null ? "" : hovered[0];
    }

    @Override
    public void discardDraft() {
        scrollbar.endDrag();
    }

    private void layout() {
        editorBounds = new ModernMainLayout.Rect(area.x + 10, area.y + 68, Math.max(1, area.width - 20),
                Math.max(40, area.height - 118));
        int buttonY = area.bottom() - 32;
        int gap = 6;
        int buttonWidth = Math.max(72, (area.width - 24 - gap * 4) / 5);
        saveBounds = new ModernMainLayout.Rect(area.x + 12, buttonY, buttonWidth, 22);
        reloadBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, buttonY, buttonWidth, 22);
        copyBounds = new ModernMainLayout.Rect(reloadBounds.right() + gap, buttonY, buttonWidth, 22);
        rollbackBounds = new ModernMainLayout.Rect(copyBounds.right() + gap, buttonY, buttonWidth, 22);
        backBounds = new ModernMainLayout.Rect(rollbackBounds.right() + gap, buttonY, buttonWidth, 22);
    }

    private void saveCurrentFile() {
        ProfileFileEditorBuffer.ValidationResult validation = buffer.validate(relativePath);
        if (!validation.valid) {
            highlightedErrorLine = validation.line - 1;
            buffer.moveTo(highlightedErrorLine, validation.column - 1);
            ensureCursorVisible();
            status("保存已拦截: 第 " + validation.line + " 行，第 " + validation.column + " 列 - " + validation.message,
                    ModernUiRenderer.DANGER);
            return;
        }
        try {
            String content = buffer.getContent();
            ProfileShareCodeManager.saveProfileFileContent(profileName, relativePath, content);
            buffer.markSaved(content);
            highlightedErrorLine = -1;
            status("已保存当前配置文件", ModernUiRenderer.SUCCESS);
            owner.refreshAfterEditorSave();
        } catch (Exception error) {
            status("保存失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
    }

    private void reloadFromDisk() {
        if (buffer.isDirty()) {
            owner.pushModal("重新载入文件", "当前文件有未保存修改，重新载入会丢弃这些内容。", false, true,
                    ignored -> reloadFromDiskNow());
            return;
        }
        reloadFromDiskNow();
    }

    private void reloadFromDiskNow() {
        try {
            buffer.setContent(ProfileShareCodeManager.loadProfileFileContent(profileName, relativePath));
            highlightedErrorLine = -1;
            status("已重新从磁盘载入", ModernUiRenderer.SUCCESS);
        } catch (Exception error) {
            status("重新载入失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
    }

    private void requestClose() {
        if (!buffer.isDirty()) {
            owner.back();
            return;
        }
        owner.pushModal("放弃未保存修改", "当前文件还有未保存内容，返回后这些修改将被丢弃。", false, true,
                ignored -> owner.back());
    }

    private void moveCursorFromMouse(int mouseX, int mouseY) {
        int localY = mouseY - editorBounds.y - 4;
        int lineIndex = scrollOffset + Math.max(0, localY / lineHeight());
        lineIndex = Math.max(0, Math.min(buffer.lineCount() - 1, lineIndex));
        String line = buffer.line(lineIndex);
        int textX = editorBounds.x + 46;
        int relativeX = Math.max(0, mouseX - textX);
        int visibleStart = Math.min(horizontalScroll, line.length());
        int bestColumn = visibleStart;
        if (font != null) {
            for (int i = visibleStart + 1; i <= line.length(); i++) {
                if (font.getStringWidth(line.substring(visibleStart, i)) > relativeX) {
                    break;
                }
                bestColumn = i;
            }
        }
        buffer.moveTo(lineIndex, bestColumn);
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        int visible = visibleLineCount();
        if (buffer.cursorLine() < scrollOffset) {
            scrollOffset = buffer.cursorLine();
        }
        if (buffer.cursorLine() >= scrollOffset + visible) {
            scrollOffset = buffer.cursorLine() - visible + 1;
        }
        scrollOffset = Math.max(0, scrollOffset);
        if (font == null) {
            return;
        }
        String line = buffer.line(buffer.cursorLine());
        int cursor = Math.max(0, Math.min(buffer.cursorColumn(), line.length()));
        int cursorWidth = Math.max(8, editorTextWidth() - 8);
        while (horizontalScroll < cursor
                && font.getStringWidth(line.substring(horizontalScroll, cursor)) > cursorWidth) {
            horizontalScroll++;
        }
        while (horizontalScroll > 0
                && font.getStringWidth(line.substring(horizontalScroll - 1, cursor)) <= cursorWidth) {
            horizontalScroll--;
        }
    }

    private int visibleLineCount() {
        return Math.max(1, (Math.max(1, editorBounds == null ? 40 : editorBounds.height) - 8) / lineHeight());
    }

    private int lineHeight() {
        return font == null ? 12 : Math.max(12, font.FONT_HEIGHT + 3);
    }

    private int editorTextWidth() {
        return Math.max(1, (editorBounds == null ? 40 : editorBounds.width) - 40 - 24);
    }

    private void status(String message, int color) {
        this.status = message == null ? "" : message;
        this.statusColor = color;
    }
}
