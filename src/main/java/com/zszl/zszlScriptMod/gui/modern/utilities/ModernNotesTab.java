package com.zszl.zszlScriptMod.gui.modern.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ModernExpressionCodeEditor;
import com.zszl.zszlScriptMod.mcp.McpJson;
import com.zszl.zszlScriptMod.mcp.McpNotes;

import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.resources.I18n;

/** Full in-game Markdown notebook with searchable server groups. */
public final class ModernNotesTab implements ModernSettingsTab {
    private static final int ROW_HEIGHT = 38;
    private static final int MAX_GROUP_NAME = 64;

    private enum ViewMode { EDIT, SPLIT, PREVIEW }
    private enum GroupDialog { NONE, RENAME, DELETE }

    private static final class NotebookEntry {
        final String server;
        final long bytes;
        NotebookEntry(String server, long bytes) { this.server = server; this.bytes = bytes; }
    }

    private static final class FormatAction {
        final String label;
        final String prefix;
        final String suffix;
        FormatAction(String label, String prefix, String suffix) {
            this.label = label; this.prefix = prefix; this.suffix = suffix;
        }
    }

    private static final FormatAction[] FORMAT_ACTIONS = {
            new FormatAction("H1", "# ", ""), new FormatAction("B", "**", "**"),
            new FormatAction("I", "*", "*"), new FormatAction("代码", "`", "`"),
            new FormatAction("链接", "[", "](https://)"), new FormatAction("列表", "- ", ""),
            new FormatAction("代码块", "```\n", "\n```")
    };

    private final ModernExpressionCodeEditor editor = new ModernExpressionCodeEditor();
    private final ModernMarkdownPreview preview = new ModernMarkdownPreview();
    private final ModernHoverScrollbar groupScrollbar = new ModernHoverScrollbar();
    private final List<NotebookEntry> entries = new ArrayList<NotebookEntry>();
    private final List<NotebookEntry> filteredEntries = new ArrayList<NotebookEntry>();
    private final McpNotes.Live live = new McpNotes.Live() {
        @Override public String serverKey() { return key; }
        @Override public String text() { return editor.getText(); }
        @Override public void replace(String value) {
            editor.setText(value == null ? "" : value);
            lastSaved = editor.getText();
        }
    };

    private boolean initialized;
    private boolean newGroupOpen;
    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect sidebarBounds;
    private ModernMainLayout.Rect groupListBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect previewBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect newGroupBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect editModeBounds;
    private ModernMainLayout.Rect splitModeBounds;
    private ModernMainLayout.Rect previewModeBounds;
    private ModernMainLayout.Rect renameBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect sidebarDividerBounds;
    private ModernMainLayout.Rect sidebarCollapseBounds;
    private ModernMainLayout.Rect contextMenuBounds;
    private ModernMainLayout.Rect contextRenameBounds;
    private ModernMainLayout.Rect contextDeleteBounds;
    private ModernMainLayout.Rect dialogBounds;
    private ModernMainLayout.Rect dialogInputBounds;
    private ModernMainLayout.Rect dialogConfirmBounds;
    private ModernMainLayout.Rect dialogCancelBounds;
    private final ModernMainLayout.Rect[] formatBounds = new ModernMainLayout.Rect[FORMAT_ACTIONS.length];

    private ModernTextField searchField;
    private ModernTextField newGroupField;
    private ModernTextField renameField;
    private String searchText = "";
    private String currentServerKey = McpNotes.SINGLEPLAYER;
    private String key = McpNotes.SINGLEPLAYER;
    private String lastSaved = "";
    private String statusMessage = "";
    private long statusUntil;
    private long nextRefresh;
    private long nextDiskCheck;
    private int groupScroll;
    private double sidebarRatio = ModernSplitPane.DEFAULT_RATIO;
    private boolean sidebarCollapsed;
    private boolean draggingSidebarDivider;
    private boolean contextMenuOpen;
    private String contextServer = "";
    private GroupDialog groupDialog = GroupDialog.NONE;
    private ViewMode viewMode = ViewMode.SPLIT;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        McpNotes.INSTANCE.attach(live);
        if (initialized) return;
        searchField = createField(fontRenderer, 96);
        newGroupField = createField(fontRenderer, MAX_GROUP_NAME);
        renameField = createField(fontRenderer, MAX_GROUP_NAME);
        initialized = true;
        sidebarRatio = MainUiLayoutManager.getModernSplitRatio("notes.groups", sidebarRatio);
        sidebarCollapsed = MainUiLayoutManager.isModernDashboardGroupCollapsed("notes.groups");
        currentServerKey = McpNotes.sanitizeServerKey(McpNotes.detectCurrentServer());
        refreshEntries();
        load(currentServerKey);
    }

    private ModernTextField createField(FontRenderer fontRenderer, int maxLength) {
        ModernTextField field = new ModernTextField(0, fontRenderer, 0, 0, 1, 20);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    @Override public void updateScreen() { }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        syncWithServerAndDisk();
        bounds = contentBounds;
        if (contentBounds == null || contentBounds.width <= 0 || contentBounds.height <= 0) return;
        ModernUiRenderer.drawRoundedRect(contentBounds.x, contentBounds.y, contentBounds.width,
                contentBounds.height, 8, ModernUiRenderer.SHELL);

        int splitTotal = Math.max(2, contentBounds.width - 18);
        int sidebarWidth = sidebarCollapsed ? 44 : ModernSplitPane.calculate(splitTotal, sidebarRatio,
                150, 280, 130, 180).firstWidth;
        sidebarBounds = new ModernMainLayout.Rect(contentBounds.x, contentBounds.y, sidebarWidth, contentBounds.height);
        ModernUiRenderer.drawRoundedRect(sidebarBounds.x, sidebarBounds.y, sidebarBounds.width,
                sidebarBounds.height, 8, ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawVerticalDivider(sidebarBounds.right(), sidebarBounds.y + 12,
                Math.max(0, sidebarBounds.height - 24), ModernUiRenderer.BORDER_SUBTLE);
        sidebarDividerBounds = sidebarCollapsed ? null : ModernSplitPane.verticalDividerBounds(
                contentBounds.x, sidebarWidth, 18, contentBounds.y, contentBounds.height);
        drawSidebar(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(sidebarDividerBounds, mouseX, mouseY, draggingSidebarDivider);
        drawNotebook(fontRenderer, contentBounds, sidebarWidth, mouseX, mouseY);
        drawContextMenu(fontRenderer, mouseX, mouseY);
        drawGroupDialog(fontRenderer, mouseX, mouseY);
    }

    private void drawSidebar(FontRenderer font, int mouseX, int mouseY) {
        if (sidebarCollapsed) {
            sidebarCollapseBounds = new ModernMainLayout.Rect(sidebarBounds.x + 8, sidebarBounds.y + 14,
                    Math.max(1, sidebarBounds.width - 16), 30);
            ModernUiRenderer.drawRoundedRect(sidebarCollapseBounds.x, sidebarCollapseBounds.y,
                    sidebarCollapseBounds.width, sidebarCollapseBounds.height, 5,
                    sidebarCollapseBounds.contains(mouseX, mouseY) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE);
            ModernUiRenderer.drawText(font, "›", sidebarCollapseBounds.x + 5,
                    sidebarCollapseBounds.y + 10, ModernUiRenderer.TEXT, sidebarCollapseBounds.width - 5);
            searchBounds = null;
            groupListBounds = null;
            newGroupBounds = null;
            return;
        }
        sidebarCollapseBounds = new ModernMainLayout.Rect(sidebarBounds.right() - 30, sidebarBounds.y + 12,
                18, 18);
        ModernUiRenderer.drawText(font, "‹", sidebarCollapseBounds.x + 5,
                sidebarCollapseBounds.y + 4, ModernUiRenderer.MUTED_TEXT, sidebarCollapseBounds.width - 5);
        int x = sidebarBounds.x + 12;
        int width = sidebarBounds.width - 24;
        ModernUiRenderer.drawText(font, "gui.modern.notes.groups", x, sidebarBounds.y + 14,
                ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(font, "gui.modern.notes.groups_hint", x, sidebarBounds.y + 28,
                ModernUiRenderer.MUTED_TEXT, width);

        searchBounds = new ModernMainLayout.Rect(x, sidebarBounds.y + 47, width, 24);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height,
                4, 0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(searchBounds.x + 7, searchBounds.y + 6, ModernUiRenderer.MUTED_TEXT);
        searchField.x = searchBounds.x + 20;
        searchField.y = searchBounds.y + 3;
        searchField.width = Math.max(1, searchBounds.width - 25);
        searchField.height = 18;
        searchField.setVisible(true);
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (searchText.isEmpty() && !searchField.isFocused()) {
            ModernUiRenderer.drawText(font, "gui.modern.notes.filter_placeholder", searchField.x,
                    searchField.y + 2, ModernUiRenderer.MUTED_TEXT, searchField.width - 4);
        }

        int footerHeight = newGroupOpen ? 58 : 38;
        int listY = searchBounds.bottom() + 10;
        groupListBounds = new ModernMainLayout.Rect(x, listY, width,
                Math.max(1, sidebarBounds.bottom() - listY - footerHeight));
        ModernUiRenderer.beginClip(groupListBounds);
        try {
            for (int i = 0; i < filteredEntries.size(); i++) {
                NotebookEntry entry = filteredEntries.get(i);
                int y = groupListBounds.y + i * ROW_HEIGHT - groupScroll;
                if (y + ROW_HEIGHT < groupListBounds.y || y > groupListBounds.bottom()) continue;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(groupListBounds.x, y,
                        groupListBounds.width - ModernHoverScrollbar.GUTTER, ROW_HEIGHT - 4);
                boolean selected = entry.server.equals(key);
                boolean hovered = row.contains(mouseX, mouseY);
                int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 5, fill);
                if (selected) ModernUiRenderer.drawRoundedRect(row.x, row.y + 5, 3, row.height - 10, 2,
                        ModernUiRenderer.ACCENT);
                ModernUiRenderer.drawStatusDot(row.x + 13, row.y + 12,
                        entry.server.equals(currentServerKey) ? ModernUiRenderer.SUCCESS : ModernUiRenderer.ACCENT_DIM);
                ModernUiRenderer.drawText(font, displayServer(entry.server), row.x + 24, row.y + 7,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 34);
                ModernUiRenderer.drawText(font, entry.bytes <= 0 ? "新建" : formatBytes(entry.bytes), row.x + 24,
                        row.y + 21, ModernUiRenderer.MUTED_TEXT, row.width - 34);
            }
        } finally { ModernUiRenderer.endClip(); }
        int maxScroll = Math.max(0, filteredEntries.size() * ROW_HEIGHT - groupListBounds.height);
        groupScroll = Math.max(0, Math.min(groupScroll, maxScroll));
        groupScrollbar.draw(groupListBounds, groupScroll, maxScroll, groupListBounds.height,
                Math.max(groupListBounds.height, filteredEntries.size() * ROW_HEIGHT), mouseX, mouseY,
                value -> groupScroll = value);

        int footerY = sidebarBounds.bottom() - footerHeight + 7;
        ModernUiRenderer.drawDivider(x, footerY - 5, width, ModernUiRenderer.BORDER_SUBTLE);
        if (newGroupOpen) {
            newGroupBounds = new ModernMainLayout.Rect(x, footerY + 4, width, 22);
            newGroupField.x = newGroupBounds.x + 4;
            newGroupField.y = newGroupBounds.y + 2;
            newGroupField.width = Math.max(1, newGroupBounds.width - 50);
            newGroupField.height = 18;
            newGroupField.setVisible(true);
            ModernUiRenderer.drawSubtlePanel(newGroupBounds.x, newGroupBounds.y, newGroupBounds.width - 44,
                    22, 3, 0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawTextField(newGroupField);
            drawButton(font, new ModernMainLayout.Rect(newGroupBounds.right() - 40, newGroupBounds.y,
                    40, 22), "gui.modern.notes.create_short", true, mouseX, mouseY);
        } else {
            newGroupBounds = new ModernMainLayout.Rect(x, footerY + 4, width, 24);
            newGroupField.setVisible(false);
            drawButton(font, newGroupBounds, "gui.modern.notes.create", false, mouseX, mouseY);
        }
    }

    private void drawNotebook(FontRenderer font, ModernMainLayout.Rect content, int sidebarWidth,
            int mouseX, int mouseY) {
        int x = content.x + sidebarWidth + 18;
        int width = Math.max(1, content.right() - x - 18);
        int top = content.y + 12;
        int toolbarHeight = 70;
        ModernUiRenderer.drawText(font, "gui.modern.notes.title", x, top + 3, ModernUiRenderer.TEXT, width - 330);
        ModernUiRenderer.drawText(font, displayServer(key), x, top + 19, ModernUiRenderer.MUTED_TEXT, width - 330);

        int buttonY = top + 4;
        int buttonWidth = 47;
        previewModeBounds = new ModernMainLayout.Rect(content.right() - 47, buttonY, buttonWidth, 25);
        splitModeBounds = new ModernMainLayout.Rect(previewModeBounds.x - 52, buttonY, buttonWidth, 25);
        editModeBounds = new ModernMainLayout.Rect(splitModeBounds.x - 52, buttonY, buttonWidth, 25);
        saveBounds = new ModernMainLayout.Rect(editModeBounds.x - 62, buttonY, 57, 25);
        renameBounds = saveBounds.x - 58 >= x
                ? new ModernMainLayout.Rect(saveBounds.x - 58, buttonY, 54, 25) : null;
        deleteBounds = renameBounds != null && renameBounds.x - 44 >= x
                ? new ModernMainLayout.Rect(renameBounds.x - 44, buttonY, 40, 25) : null;
        drawButton(font, saveBounds, "gui.modern.notes.save", true, mouseX, mouseY);
        if (renameBounds != null) drawButton(font, renameBounds, "gui.modern.notes.rename", false, mouseX, mouseY);
        if (deleteBounds != null) drawButton(font, deleteBounds, "gui.modern.notes.delete", false, mouseX, mouseY);
        drawButton(font, editModeBounds, "gui.modern.notes.edit", viewMode == ViewMode.EDIT, mouseX, mouseY);
        drawButton(font, splitModeBounds, "gui.modern.notes.split", viewMode == ViewMode.SPLIT, mouseX, mouseY);
        drawButton(font, previewModeBounds, "gui.modern.notes.preview", viewMode == ViewMode.PREVIEW, mouseX, mouseY);

        int formatX = x;
        int formatY = top + 38;
        for (int i = 0; i < FORMAT_ACTIONS.length; i++) {
            int actionWidth = i == 4 || i == 6 ? 42 : 31;
            if (formatX + actionWidth > content.right() - 8) {
                formatBounds[i] = null;
                continue;
            }
            formatBounds[i] = new ModernMainLayout.Rect(formatX, formatY, actionWidth, 22);
            drawButton(font, formatBounds[i], FORMAT_ACTIONS[i].label, false, mouseX, mouseY);
            formatX += actionWidth + 4;
        }

        ModernUiRenderer.drawDivider(x, top + toolbarHeight, width, ModernUiRenderer.BORDER_SUBTLE);
        int bodyY = top + toolbarHeight + 10;
        int bodyHeight = Math.max(1, content.bottom() - bodyY - 30);
        ModernMainLayout.Rect body = new ModernMainLayout.Rect(x, bodyY, width, bodyHeight);
        editorBounds = null; previewBounds = null;
        if (viewMode == ViewMode.EDIT) editorBounds = body;
        else if (viewMode == ViewMode.PREVIEW) previewBounds = body;
        else {
            int gap = 8;
            int leftWidth = Math.max(1, (body.width - gap) / 2);
            editorBounds = new ModernMainLayout.Rect(body.x, body.y, leftWidth, body.height);
            previewBounds = new ModernMainLayout.Rect(body.x + leftWidth + gap, body.y,
                    Math.max(1, body.width - leftWidth - gap), body.height);
        }
        if (editorBounds != null) editor.draw(font, editorBounds, true, mouseX, mouseY);
        if (previewBounds != null) preview.draw(font, previewBounds, editor.getText(), mouseX, mouseY);

        String status = statusUntil > System.currentTimeMillis() ? statusMessage
                : (isDirty() ? "gui.modern.notes.unsaved" : "gui.modern.notes.saved");
        ModernUiRenderer.drawStatusDot(x + 3, content.bottom() - 16,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS);
        ModernUiRenderer.drawText(font, status, x + 12, content.bottom() - 20,
                isDirty() ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT, width - 100);
        ModernUiRenderer.drawText(font, editor.getText().length() + "/" + McpNotes.MAX_TEXT_LENGTH,
                content.right() - 82, content.bottom() - 20, ModernUiRenderer.MUTED_TEXT, 76);
    }

    private void drawContextMenu(FontRenderer font, int mouseX, int mouseY) {
        if (!contextMenuOpen || bounds == null) return;
        int menuWidth = 156;
        int menuHeight = 58;
        int menuX = Math.max(bounds.x + 4, Math.min(contextMenuBounds == null ? bounds.x : contextMenuBounds.x,
                bounds.right() - menuWidth - 4));
        int menuY = Math.max(bounds.y + 4, Math.min(contextMenuBounds == null ? bounds.y : contextMenuBounds.y,
                bounds.bottom() - menuHeight - 4));
        contextMenuBounds = new ModernMainLayout.Rect(menuX, menuY, menuWidth, menuHeight);
        ModernUiRenderer.drawPanel(menuX, menuY, menuWidth, menuHeight, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        contextRenameBounds = new ModernMainLayout.Rect(menuX + 4, menuY + 4, menuWidth - 8, 23);
        contextDeleteBounds = new ModernMainLayout.Rect(menuX + 4, menuY + 31, menuWidth - 8, 23);
        drawMenuItem(font, contextRenameBounds, "gui.modern.notes.rename", false, mouseX, mouseY);
        drawMenuItem(font, contextDeleteBounds, "gui.modern.notes.delete", true, mouseX, mouseY);
    }

    private void drawMenuItem(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean danger,
            int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        if (hovered) ModernUiRenderer.drawRoundedRect(rect.x, rect.y, rect.width, rect.height, 3,
                danger ? 0x443F2028 : ModernUiRenderer.SURFACE_HOVER);
        ModernUiRenderer.drawText(font, label, rect.x + 8, rect.y + 7,
                danger ? ModernUiRenderer.DANGER : ModernUiRenderer.TEXT, rect.width - 16);
    }

    private void drawGroupDialog(FontRenderer font, int mouseX, int mouseY) {
        if (groupDialog == GroupDialog.NONE || bounds == null) {
            if (renameField != null) renameField.setVisible(false);
            return;
        }
        int width = Math.min(420, Math.max(220, bounds.width - 24));
        int height = groupDialog == GroupDialog.RENAME ? 132 : 116;
        int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
        dialogBounds = new ModernMainLayout.Rect(x, y, width, height);
        com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer.drawRoundedRect(bounds.x, bounds.y,
                bounds.width, bounds.height, 0, 0x88000000);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        String title = groupDialog == GroupDialog.RENAME ? "gui.modern.notes.rename_title" : "gui.modern.notes.delete_title";
        String message = groupDialog == GroupDialog.RENAME ? "gui.modern.notes.rename_hint" : "gui.modern.notes.delete_hint";
        ModernUiRenderer.drawText(font, title, x + 16, y + 14, ModernUiRenderer.TEXT, width - 32);
        ModernUiRenderer.drawText(font, message, x + 16, y + 32,
                groupDialog == GroupDialog.DELETE ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT, width - 32);
        if (groupDialog == GroupDialog.RENAME) {
            dialogInputBounds = new ModernMainLayout.Rect(x + 16, y + 52, width - 32, 24);
            renameField.x = dialogInputBounds.x + 5;
            renameField.y = dialogInputBounds.y + 3;
            renameField.width = dialogInputBounds.width - 10;
            renameField.height = 18;
            renameField.setVisible(true);
            ModernUiRenderer.drawSubtlePanel(dialogInputBounds.x, dialogInputBounds.y,
                    dialogInputBounds.width, dialogInputBounds.height, 4, 0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawTextField(renameField);
            dialogConfirmBounds = new ModernMainLayout.Rect(x + width - 142, y + 96, 58, 24);
            dialogCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + 96, 62, 24);
        } else {
            renameField.setVisible(false);
            dialogInputBounds = null;
            dialogConfirmBounds = new ModernMainLayout.Rect(x + width - 142, y + 80, 58, 24);
            dialogCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + 80, 62, 24);
        }
        drawButton(font, dialogConfirmBounds, groupDialog == GroupDialog.DELETE
                ? "gui.modern.notes.delete_confirm" : "gui.modern.notes.confirm", groupDialog == GroupDialog.DELETE, mouseX, mouseY);
        drawButton(font, dialogCancelBounds, "gui.modern.notes.cancel", false, mouseX, mouseY);
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean selected,
            int mouseX, int mouseY) {
        if (rect == null) return;
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawRoundedRect(rect.x, rect.y, rect.width, rect.height, 4, fill);
        String value = I18n.format(label);
        String visible = ModernUiRenderer.ellipsize(font, value, Math.max(1, rect.width - 8));
        font.drawString(visible, rect.x + Math.max(4, (rect.width - font.getStringWidth(visible)) / 2),
                rect.y + (rect.height - 8) / 2, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!initialized) return false;
        if (groupDialog != GroupDialog.NONE) {
            if (mouseButton != 0) return true;
            if (dialogInputBounds != null && dialogInputBounds.contains(mouseX, mouseY)) {
                renameField.mouseClicked(mouseX, mouseY, mouseButton);
            } else if (dialogConfirmBounds != null && dialogConfirmBounds.contains(mouseX, mouseY)) {
                if (groupDialog == GroupDialog.RENAME) renameGroup(); else deleteGroup();
            } else if (dialogCancelBounds != null && dialogCancelBounds.contains(mouseX, mouseY)) {
                closeGroupDialog();
            }
            return true;
        }
        if (mouseButton == 1) {
            if (contextMenuOpen) { contextMenuOpen = false; return true; }
            int index = groupIndexAt(mouseX, mouseY);
            if (index >= 0) {
                selectGroup(filteredEntries.get(index).server);
                contextServer = key;
                contextMenuOpen = true;
                contextMenuBounds = new ModernMainLayout.Rect(mouseX, mouseY, 156, 58);
                return true;
            }
            return bounds != null && bounds.contains(mouseX, mouseY);
        }
        if (mouseButton != 0) return false;
        if (contextMenuOpen) {
            if (contextRenameBounds != null && contextRenameBounds.contains(mouseX, mouseY)) {
                openRename(contextServer); return true;
            }
            if (contextDeleteBounds != null && contextDeleteBounds.contains(mouseX, mouseY)) {
                openDelete(contextServer); return true;
            }
            contextMenuOpen = false;
            return true;
        }
        if (sidebarCollapseBounds != null && sidebarCollapseBounds.contains(mouseX, mouseY)) {
            sidebarCollapsed = !sidebarCollapsed;
            MainUiLayoutManager.toggleModernDashboardGroupCollapsed("notes.groups");
            return true;
        }
        if (sidebarDividerBounds != null && sidebarDividerBounds.contains(mouseX, mouseY)) {
            draggingSidebarDivider = true;
            return true;
        }
        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton); return true;
        }
        if (newGroupOpen && newGroupBounds != null && newGroupBounds.contains(mouseX, mouseY)) {
            if (mouseX < newGroupBounds.right() - 44) newGroupField.mouseClicked(mouseX, mouseY, mouseButton);
            else createGroup();
            return true;
        }
        if (groupListBounds != null && groupListBounds.contains(mouseX, mouseY)) {
            if (groupScrollbar.beginDrag(mouseX, mouseY)) return true;
            int index = (mouseY - groupListBounds.y + groupScroll) / ROW_HEIGHT;
            if (index >= 0 && index < filteredEntries.size()) { selectGroup(filteredEntries.get(index).server); return true; }
        }
        if (newGroupBounds != null && newGroupBounds.contains(mouseX, mouseY)) {
            newGroupOpen = true; newGroupField.setVisible(true); newGroupField.setFocused(true); return true;
        }
        if (saveBounds != null && saveBounds.contains(mouseX, mouseY)) { save(); showStatus("gui.modern.notes.saved_now"); return true; }
        if (renameBounds != null && renameBounds.contains(mouseX, mouseY)) { openRename(key); return true; }
        if (deleteBounds != null && deleteBounds.contains(mouseX, mouseY)) { openDelete(key); return true; }
        if (editModeBounds != null && editModeBounds.contains(mouseX, mouseY)) { viewMode = ViewMode.EDIT; return true; }
        if (splitModeBounds != null && splitModeBounds.contains(mouseX, mouseY)) { viewMode = ViewMode.SPLIT; return true; }
        if (previewModeBounds != null && previewModeBounds.contains(mouseX, mouseY)) { viewMode = ViewMode.PREVIEW; return true; }
        for (int i = 0; i < formatBounds.length; i++) {
            if (formatBounds[i] != null && formatBounds[i].contains(mouseX, mouseY)) {
                editor.setFocused(true);
                editor.wrapSelection(FORMAT_ACTIONS[i].prefix, FORMAT_ACTIONS[i].suffix);
                return true;
            }
        }
        if (editorBounds != null && editorBounds.contains(mouseX, mouseY) && editor.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (previewBounds != null && preview.mouseClicked(mouseX, mouseY)) return true;
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && draggingSidebarDivider && bounds != null) {
            int total = Math.max(2, bounds.width - 18);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - bounds.x, 150, 280, 130, 180);
            sidebarRatio = split.ratio;
            return true;
        }
        return editor.mouseClickMove(mouseX, mouseY, clickedMouseButton) || preview.mouseClickMove(mouseX, mouseY)
                || groupScrollbar.applyDrag(mouseX, mouseY);
    }

    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean groupDragging = groupScrollbar.isDragging();
        groupScrollbar.endDrag();
        boolean dividerDragging = draggingSidebarDivider;
        if (dividerDragging) MainUiLayoutManager.setModernSplitRatio("notes.groups", sidebarRatio);
        draggingSidebarDivider = false;
        return editor.mouseReleased(mouseX, mouseY) || preview.mouseReleased() || groupDragging || dividerDragging;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (groupDialog == GroupDialog.RENAME && renameField != null && renameField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) { closeGroupDialog(); return true; }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { renameGroup(); return true; }
            renameField.textboxKeyTyped(typedChar, keyCode); return true;
        }
        if (groupDialog == GroupDialog.DELETE) {
            if (keyCode == Keyboard.KEY_ESCAPE) { closeGroupDialog(); return true; }
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) { searchField.setFocused(false); return true; }
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (!searchText.equals(searchField.getText())) { searchText = searchField.getText(); refreshFilteredEntries(); groupScroll = 0; }
            return true;
        }
        if (newGroupOpen && newGroupField != null && newGroupField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) { newGroupOpen = false; newGroupField.setFocused(false); return true; }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { createGroup(); return true; }
            newGroupField.textboxKeyTyped(typedChar, keyCode); return true;
        }
        return editor.keyTyped(typedChar, keyCode);
    }

    @Override public boolean handleMouseWheel(int wheel) { return handleMouseWheel(wheel, 0, 0); }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (groupListBounds != null && groupListBounds.contains(mouseX, mouseY)) {
            int max = Math.max(0, filteredEntries.size() * ROW_HEIGHT - groupListBounds.height);
            int notches = Math.max(1, Math.abs(wheel) >= 120 ? Math.abs(wheel) / 120 : Math.abs(wheel));
            groupScroll = Math.max(0, Math.min(max, groupScroll + (wheel > 0 ? -notches * 2 : notches * 2))); return true;
        }
        if (editorBounds != null && editorBounds.contains(mouseX, mouseY) && editor.mouseWheel(mouseX, mouseY, wheel)) return true;
        return preview.mouseWheel(wheel, mouseX, mouseY);
    }

    @Override public boolean containsContent(int mouseX, int mouseY) { return bounds != null && bounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return ""; }

    @Override public boolean isTextInputFocused() {
        return searchField != null && searchField.isFocused() || newGroupField != null && newGroupField.isFocused()
                || renameField != null && renameField.isFocused() || editor.isFocused();
    }

    @Override public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        if (searchBounds == null || !searchBounds.contains(mouseX, mouseY)) searchField.setFocused(false);
        if (newGroupBounds == null || !newGroupBounds.contains(mouseX, mouseY)) newGroupField.setFocused(false);
        if (dialogInputBounds == null || !dialogInputBounds.contains(mouseX, mouseY)) renameField.setFocused(false);
        if (editorBounds == null || !editorBounds.contains(mouseX, mouseY)) editor.setFocused(false);
    }

    @Override public boolean handleEscape() {
        if (groupDialog != GroupDialog.NONE) { closeGroupDialog(); return true; }
        if (contextMenuOpen) { contextMenuOpen = false; return true; }
        if (newGroupOpen) { newGroupOpen = false; newGroupField.setFocused(false); return true; }
        if (searchField != null && searchField.isFocused()) { searchField.setFocused(false); return true; }
        if (editor.isFocused()) { editor.setFocused(false); return true; }
        return false;
    }

    @Override public void discardDraft() { McpNotes.INSTANCE.detach(live); load(key); }
    @Override public boolean isDirty() { return initialized && !editor.getText().equals(lastSaved); }
    @Override public void save() { persist(key, editor.getText()); refreshEntries(); }

    private void selectGroup(String server) {
        String selected = McpNotes.sanitizeServerKey(server);
        if (selected.equals(key)) return;
        if (isDirty()) persist(key, editor.getText());
        load(selected); showStatus("gui.modern.notes.switched");
    }

    private void load(String server) {
        key = McpNotes.sanitizeServerKey(server);
        String text = "";
        try { text = McpNotes.INSTANCE.diskText(key); } catch (Exception ignored) { }
        editor.setText(text); lastSaved = editor.getText();
    }

    private void persist(String server, String text) {
        try {
            McpNotes.INSTANCE.call(McpJson.object("operation", "write", "server", server, "text", text == null ? "" : text));
            if (server.equals(key)) lastSaved = editor.getText();
        } catch (Exception ignored) { showStatus("gui.modern.notes.error"); }
    }

    private void createGroup() {
        String raw = newGroupField.getText() == null ? "" : newGroupField.getText().trim();
        String group = McpNotes.sanitizeServerKey(raw);
        if (raw.isEmpty() || McpNotes.SINGLEPLAYER.equals(group) && !McpNotes.SINGLEPLAYER.equals(raw)) return;
        try {
            McpNotes.INSTANCE.call(McpJson.object("operation", "write", "server", group, "text", ""));
            newGroupField.setText(""); newGroupOpen = false; refreshEntries(); selectGroup(group); showStatus("gui.modern.notes.created");
        } catch (Exception ignored) { showStatus("gui.modern.notes.error"); }
    }

    private int groupIndexAt(int mouseX, int mouseY) {
        if (groupListBounds == null || !groupListBounds.contains(mouseX, mouseY)
                || mouseX >= groupListBounds.right() - ModernHoverScrollbar.GUTTER) return -1;
        int index = (mouseY - groupListBounds.y + groupScroll) / ROW_HEIGHT;
        if (index < 0 || index >= filteredEntries.size()) return -1;
        int rowY = groupListBounds.y + index * ROW_HEIGHT - groupScroll;
        return mouseY < rowY + ROW_HEIGHT - 4 ? index : -1;
    }

    private void openRename(String server) {
        if (server == null || server.trim().isEmpty()) return;
        contextMenuOpen = false;
        contextServer = McpNotes.sanitizeServerKey(server);
        groupDialog = GroupDialog.RENAME;
        renameField.setText(contextServer);
        renameField.setCursorPositionEnd();
        renameField.setFocused(true);
    }

    private void openDelete(String server) {
        if (server == null || server.trim().isEmpty()) return;
        contextMenuOpen = false;
        contextServer = McpNotes.sanitizeServerKey(server);
        groupDialog = GroupDialog.DELETE;
        renameField.setFocused(false);
    }

    private void closeGroupDialog() {
        groupDialog = GroupDialog.NONE;
        renameField.setFocused(false);
        renameField.setVisible(false);
    }

    private void renameGroup() {
        String raw = renameField.getText() == null ? "" : renameField.getText().trim();
        String target = McpNotes.sanitizeServerKey(raw);
        if (raw.isEmpty() || (McpNotes.SINGLEPLAYER.equals(target) && !McpNotes.SINGLEPLAYER.equals(raw))) {
            showStatus("gui.modern.notes.invalid_name");
            return;
        }
        String old = contextServer;
        try {
            McpNotes.INSTANCE.call(McpJson.object("operation", "rename", "server", old, "newServer", target));
            if (key.equals(old)) {
                key = target;
                lastSaved = editor.getText();
            }
            closeGroupDialog();
            refreshEntries();
            showStatus("gui.modern.notes.renamed");
        } catch (Exception ignored) { showStatus("gui.modern.notes.rename_error"); }
    }

    private void deleteGroup() {
        String target = contextServer;
        if (target == null || target.isEmpty()) { closeGroupDialog(); return; }
        try {
            McpNotes.INSTANCE.call(McpJson.object("operation", "delete", "server", target));
            if (key.equals(target)) {
                load(target);
            }
            closeGroupDialog();
            refreshEntries();
            showStatus("gui.modern.notes.deleted");
        } catch (Exception ignored) { showStatus("gui.modern.notes.delete_error"); }
    }

    private void refreshEntries() {
        try {
            JsonObject result = McpNotes.INSTANCE.call(McpJson.object("operation", "list"));
            entries.clear(); JsonArray notes = result.getAsJsonArray("notes");
            for (JsonElement element : notes) {
                JsonObject note = element.getAsJsonObject();
                entries.add(new NotebookEntry(note.get("server").getAsString(), note.get("bytes").getAsLong()));
            }
            Collections.sort(entries, Comparator.comparing(entry -> entry.server.toLowerCase(Locale.ROOT)));
            refreshFilteredEntries(); nextRefresh = System.currentTimeMillis() + 1200L;
        } catch (Exception ignored) { }
    }

    private void refreshFilteredEntries() {
        filteredEntries.clear(); String query = searchText.trim().toLowerCase(Locale.ROOT);
        for (NotebookEntry entry : entries) if (query.isEmpty() || entry.server.toLowerCase(Locale.ROOT).contains(query)) filteredEntries.add(entry);
    }

    private void syncWithServerAndDisk() {
        String detected = McpNotes.sanitizeServerKey(McpNotes.detectCurrentServer());
        if (!detected.equals(currentServerKey)) {
            String previousCurrent = currentServerKey;
            boolean followingCurrent = key.equals(previousCurrent);
            if (followingCurrent && isDirty()) persist(key, editor.getText());
            currentServerKey = detected;
            if (followingCurrent) load(detected);
            refreshEntries();
        }
        long now = System.currentTimeMillis();
        if (now >= nextRefresh) refreshEntries();
        if (now >= nextDiskCheck && !isDirty()) {
            nextDiskCheck = now + 1000L;
            try { String disk = McpNotes.INSTANCE.diskText(key); if (!disk.equals(editor.getText())) { editor.setText(disk); lastSaved = disk; } }
            catch (Exception ignored) { }
        }
    }

    private void showStatus(String key) { statusMessage = I18n.format(key); statusUntil = System.currentTimeMillis() + 1800L; }
    private static String displayServer(String server) {
        if (McpNotes.SINGLEPLAYER.equals(server)) return I18n.format("gui.modern.notes.singleplayer");
        return server == null || server.isEmpty() ? I18n.format("gui.modern.notes.unknown") : server;
    }
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
