package com.zszl.zszlScriptMod.gui.modern.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTextInputFocus;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;
import com.zszl.zszlScriptMod.gui.modern.core.ModernPanel;
import com.zszl.zszlScriptMod.gui.modern.core.ModernPanelStack;
import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import com.zszl.zszlScriptMod.system.ProfileConfigFieldCodec;
import com.zszl.zszlScriptMod.system.ProfileConfigFieldCodec.ConfigField;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native three-column profile workbench drawn with the modern theme toolkit. */
public final class ProfileWorkbenchTab implements ModernSettingsTab {

    private static final String PREFERENCES_FILE_NAME = "gui_profile_manager_layout.json";
    private static final int FOOTER_HEIGHT = 72;
    private static final int ROW_HEIGHT = 22;
    private static final int FIELD_ROW_HEIGHT = 28;
    private static boolean preferencesLoaded;
    private static double savedProfileColumnRatio = 0.18D;
    private static double savedFileColumnRatio = 0.28D;

    private final ModernPanelStack overlays = new ModernPanelStack();
    private final ModernHoverScrollbar profileBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar fileBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar previewBar = new ModernHoverScrollbar();
    private final List<String> profiles = new ArrayList<String>();
    private final List<String> shareableFiles = new ArrayList<String>();
    private final List<String> filteredShareableFiles = new ArrayList<String>();
    private final Set<String> checkedFiles = new LinkedHashSet<String>();
    private final Set<String> collapsedFieldGroups = new LinkedHashSet<String>();
    private final Map<String, Set<String>> checkedFieldsByFile = new LinkedHashMap<String, Set<String>>();
    private final Map<String, ModernMainLayout.Rect> actionBounds = new LinkedHashMap<String, ModernMainLayout.Rect>();
    private final String[] hovered = new String[] { "" };

    private List<ConfigField> allFields = new ArrayList<ConfigField>();
    private List<ConfigField> visibleFields = new ArrayList<ConfigField>();
    private GuiTextField searchField;
    private FontRenderer font;
    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect profileColumn;
    private ModernMainLayout.Rect fileColumn;
    private ModernMainLayout.Rect previewColumn;
    private ModernMainLayout.Rect profileDivider;
    private ModernMainLayout.Rect fileDivider;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect profileListBounds;
    private ModernMainLayout.Rect fileListBounds;
    private ModernMainLayout.Rect fieldListBounds;

    private int selectedProfileIndex = -1;
    private int selectedFileIndex = -1;
    private int lastFileAnchorIndex = -1;
    private int profileScroll;
    private int fileScroll;
    private int previewScroll;
    private String fileSearchQuery = "";
    private String statusMessage = "准备就绪";
    private int statusColor = ModernUiRenderer.SUBTLE_TEXT;
    private boolean exporting;
    private boolean initialized;
    private boolean returnRequested;
    private boolean draggingProfileDivider;
    private boolean draggingFileDivider;
    private int lastMouseX;
    private int lastMouseY;

    public static ModernSettingsTab create() {
        return new ProfileWorkbenchTab();
    }

    public static ModernSettingsTab create(String command) {
        return create();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        font = fontRenderer;
        if (initialized || fontRenderer == null) {
            return;
        }
        ensurePreferencesLoaded();
        searchField = new GuiTextField(100, fontRenderer, 0, 0, 1, 18);
        searchField.setMaxStringLength(128);
        searchField.setCanLoseFocus(true);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setText(fileSearchQuery);
        refreshProfiles();
        refreshFilesForSelectedProfile();
        initialized = true;
    }

    @Override
    public void updateScreen() {
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            overlay.updateScreen();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        font = fontRenderer;
        ensureInitialized(fontRenderer);
        bounds = contentBounds == null ? bounds : contentBounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hovered[0] = "";
        layout();
        drawWorkbench(fontRenderer, mouseX, mouseY);
        ModernPanel overlay = overlays.current();
        if (overlay instanceof ModernEmbeddedPanel) {
            ModernEmbeddedPanel panel = (ModernEmbeddedPanel) overlay;
            panel.ensureInitialized(fontRenderer);
            panel.setBounds(bounds);
            panel.draw(fontRenderer, bounds, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            return overlay.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return containsContent(mouseX, mouseY);
        }
        if (profileBar.beginDrag(mouseX, mouseY) || fileBar.beginDrag(mouseX, mouseY)
                || previewBar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (ProfileUi.hit(profileDivider, mouseX, mouseY)) {
            draggingProfileDivider = true;
            return true;
        }
        if (ProfileUi.hit(fileDivider, mouseX, mouseY)) {
            draggingFileDivider = true;
            return true;
        }
        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, button);
            if (ProfileUi.hit(searchBounds, mouseX, mouseY)) {
                return true;
            }
        }
        if (handleActionClick(mouseX, mouseY)) {
            return true;
        }
        handleProfileClick(mouseX, mouseY);
        handleFileClick(mouseX, mouseY);
        handleFieldClick(mouseX, mouseY);
        return containsContent(mouseX, mouseY);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            return overlay.mouseClickMove(mouseX, mouseY, button, elapsed);
        }
        if (profileBar.applyDrag(mouseX, mouseY) || fileBar.applyDrag(mouseX, mouseY)
                || previewBar.applyDrag(mouseX, mouseY)) {
            return true;
        }
        if (draggingProfileDivider) {
            applyProfileDivider(mouseX);
            return true;
        }
        if (draggingFileDivider) {
            applyFileDivider(mouseX);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            overlay.mouseReleased(mouseX, mouseY, button);
        }
        profileBar.endDrag();
        fileBar.endDrag();
        previewBar.endDrag();
        if (draggingProfileDivider || draggingFileDivider) {
            draggingProfileDivider = false;
            draggingFileDivider = false;
            persistLayout();
        }
        return true;
    }

    @Override
    public boolean keyTyped(char character, int key) {
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            return overlay.keyTyped(character, key);
        }
        if (key == Keyboard.KEY_F && GuiScreen.isCtrlKeyDown() && searchField != null) {
            searchField.setFocused(true);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (key == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false);
                return true;
            }
            String before = searchField.getText();
            if (searchField.textboxKeyTyped(character, key)) {
                fileSearchQuery = searchField.getText();
                if (!before.equals(fileSearchQuery)) {
                    applyFileFilter(selectedFilePath());
                }
                return true;
            }
        }
        if (key == Keyboard.KEY_ESCAPE) {
            returnRequested = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean handleEscape() {
        return keyTyped('\0', Keyboard.KEY_ESCAPE);
    }

    @Override
    public boolean isTextInputFocused() {
        if (overlays.depth() > 0) {
            return true;
        }
        return searchField != null && searchField.isFocused();
    }

    @Override
    public void clearTextInputFocusOutside(int x, int y) {
        if (overlays.depth() > 0) {
            ModernTextInputFocus.clearFocusOutside(overlays.current(), x, y);
            return;
        }
        if (searchField != null && !ProfileUi.hit(searchBounds, x, y)) {
            searchField.setFocused(false);
        }
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int x, int y) {
        if (wheel == 0) {
            return false;
        }
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            return overlay.handleMouseWheel(wheel);
        }
        int delta = wheel > 0 ? -1 : 1;
        if (profileColumn != null && profileColumn.contains(x, y)) {
            profileScroll = Math.max(0, profileScroll + delta);
            return true;
        }
        if (fileColumn != null && fileColumn.contains(x, y)) {
            fileScroll = Math.max(0, fileScroll + delta);
            return true;
        }
        if (previewColumn != null && previewColumn.contains(x, y)) {
            previewScroll = Math.max(0, previewScroll + delta * 2);
            return true;
        }
        return containsContent(x, y);
    }

    @Override
    public boolean containsContent(int x, int y) {
        return bounds.contains(x, y);
    }

    @Override
    public String getHoveredTooltip(int x, int y) {
        ModernPanel overlay = overlays.current();
        if (overlay != null) {
            String tooltip = overlay.getHoveredTooltip(x, y);
            if (tooltip != null && !tooltip.isEmpty()) {
                return tooltip;
            }
        }
        return hovered[0] == null ? "" : hovered[0];
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean result = returnRequested;
        returnRequested = false;
        return result;
    }

    @Override
    public void discardDraft() {
        overlays.clearAndDiscard();
        initialized = false;
        returnRequested = false;
        exporting = false;
    }

    void back() {
        if (overlays.depth() > 0) {
            overlays.pop();
        }
    }

    void pushModal(String title, String message, boolean input, boolean danger, ProfileModalPanel.Result result) {
        overlays.push(new ProfileModalPanel(this, title, message, "", input, danger, result));
    }

    void handleImportApplied(ProfileShareCodeManager.ImportResult result) {
        refreshFilesForSelectedProfile();
        if (result == null) {
            status("导入已完成", ModernUiRenderer.SUCCESS);
            return;
        }
        status("导入完成：写入 " + result.getImportedCount() + " 项，替换 " + result.getReplacedFileCount()
                + " 项，合并 " + result.getMergedFileCount() + " 项，跳过 " + result.getUnchangedFileCount() + " 项",
                ModernUiRenderer.SUCCESS);
    }

    void refreshAfterEditorSave() {
        refreshFilesForSelectedProfile();
    }

    private void layout() {
        actionBounds.clear();
        int pad = 8;
        int gap = 10;
        int listBottom = bounds.bottom() - FOOTER_HEIGHT - 8;
        int listTop = bounds.y + pad;
        int listHeight = Math.max(80, listBottom - listTop);
        int available = Math.max(3, bounds.width - pad * 2 - gap * 2);
        int[] widths = ProfileWorkbenchLogic.columnWidths(available, savedProfileColumnRatio, savedFileColumnRatio);
        int x = bounds.x + pad;
        profileColumn = new ModernMainLayout.Rect(x, listTop, widths[0], listHeight);
        x += widths[0] + gap;
        fileColumn = new ModernMainLayout.Rect(x, listTop, widths[1], listHeight);
        x += widths[1] + gap;
        previewColumn = new ModernMainLayout.Rect(x, listTop, widths[2], listHeight);
        profileDivider = ModernSplitPane.verticalDividerBounds(profileColumn.x, profileColumn.width, gap,
                listTop + 4, listHeight - 8);
        fileDivider = ModernSplitPane.verticalDividerBounds(fileColumn.x, fileColumn.width, gap, listTop + 4,
                listHeight - 8);
        searchBounds = new ModernMainLayout.Rect(fileColumn.x + 8, fileColumn.y + 26, fileColumn.width - 16, 20);
        profileListBounds = new ModernMainLayout.Rect(profileColumn.x + 6, profileColumn.y + 48,
                profileColumn.width - 12, Math.max(1, profileColumn.height - 56));
        fileListBounds = new ModernMainLayout.Rect(fileColumn.x + 6, fileColumn.y + 50,
                fileColumn.width - 12, Math.max(1, fileColumn.height - 58));
        fieldListBounds = new ModernMainLayout.Rect(previewColumn.x + 8, previewColumn.y + 52,
                previewColumn.width - 16, Math.max(1, previewColumn.height - 60));

        int actionY1 = bounds.bottom() - 52;
        int actionY2 = bounds.bottom() - 28;
        int cols = 5;
        int actionGap = 8;
        int actionWidth = Math.max(56, (bounds.width - 20 - actionGap * (cols - 1)) / cols);
        String[] row1 = { "select", "create", "delete", "editor", "refresh" };
        String[] row2 = { "export", "import", "selectAll", "clear", "done" };
        for (int i = 0; i < cols; i++) {
            int ax = bounds.x + 10 + i * (actionWidth + actionGap);
            actionBounds.put(row1[i], new ModernMainLayout.Rect(ax, actionY1, actionWidth, 20));
            actionBounds.put(row2[i], new ModernMainLayout.Rect(ax, actionY2, actionWidth, 20));
        }
    }

    private void drawWorkbench(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 8, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER);
        drawProfileColumn(fontRenderer, mouseX, mouseY);
        drawFileColumn(fontRenderer, mouseX, mouseY);
        drawPreviewColumn(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(profileDivider, mouseX, mouseY, draggingProfileDivider);
        ModernSplitPane.drawVerticalDivider(fileDivider, mouseX, mouseY, draggingFileDivider);
        drawFooter(fontRenderer, mouseX, mouseY);
    }

    private void drawProfileColumn(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ProfileUi.drawSection(fontRenderer, profileColumn, "配置方案");
        ProfileUi.drawInfo(fontRenderer, profileColumn.x + 12 + fontRenderer.getStringWidth("配置方案"), profileColumn.y + 7,
                "选择要查看或切换的配置档。当前使用中的档案会被标记；默认档案不能删除。",
                mouseX, mouseY, hovered);
        ModernMainLayout.Rect group = new ModernMainLayout.Rect(profileColumn.x + 6, profileColumn.y + 26,
                profileColumn.width - 18, ROW_HEIGHT - 2);
        ProfileUi.drawRow(group, false, false, false);
        ModernUiRenderer.drawText(fontRenderer, "配置档案", group.x + 8, group.y + 6, ModernUiRenderer.SUBTLE_TEXT,
                group.width - 12);

        int visible = Math.max(1, profileListBounds.height / ROW_HEIGHT);
        int maxScroll = Math.max(0, profiles.size() - visible);
        profileScroll = Math.max(0, Math.min(profileScroll, maxScroll));
        if (profiles.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.profile.empty"),
                    profileListBounds.x + 6, profileListBounds.y + profileListBounds.height / 2,
                    ModernUiRenderer.MUTED_TEXT, profileListBounds.width - 12);
            profileBar.idle();
            return;
        }
        for (int i = 0; i < visible; i++) {
            int index = profileScroll + i;
            if (index >= profiles.size()) {
                break;
            }
            String profile = profiles.get(index);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(profileListBounds.x, profileListBounds.y + i * ROW_HEIGHT,
                    profileListBounds.width - 8, ROW_HEIGHT - 2);
            ProfileUi.drawRow(row, index == selectedProfileIndex, row.contains(mouseX, mouseY), false);
            ProfileWorkbenchLogic.ProfileBadge badge = ProfileWorkbenchLogic.badge(profile,
                    ProfileManager.getActiveProfileName(), ProfileManager.DEFAULT_PROFILE_NAME);
            int color = badge == ProfileWorkbenchLogic.ProfileBadge.CURRENT ? ModernUiRenderer.SUCCESS
                    : badge == ProfileWorkbenchLogic.ProfileBadge.DEFAULT ? ModernUiRenderer.WARNING
                            : ModernUiRenderer.TEXT;
            String label = profile;
            if (badge == ProfileWorkbenchLogic.ProfileBadge.CURRENT) {
                label = profile + " " + I18n.format("gui.profile.current");
            } else if (badge == ProfileWorkbenchLogic.ProfileBadge.DEFAULT) {
                label = profile + " " + I18n.format("gui.profile.default");
            }
            ModernUiRenderer.drawText(fontRenderer, label, row.x + 10, row.y + 6, color, row.width - 16);
        }
        profileBar.draw(profileListBounds, profileScroll, maxScroll, visible, profiles.size(), mouseX, mouseY,
                new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int value) {
                        profileScroll = value;
                    }
                });
    }

    private void drawFileColumn(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ProfileUi.drawSection(fontRenderer, fileColumn, "配置项目");
        ProfileUi.drawInfo(fontRenderer, fileColumn.x + 12 + fontRenderer.getStringWidth("配置项目"), fileColumn.y + 7,
                "单击查看一项配置；复选框决定哪些配置进入分享码。Ctrl 可逐项增减，Shift 可选择连续范围。",
                mouseX, mouseY, hovered);
        ModernUiRenderer.drawText(fontRenderer,
                "显示 " + filteredShareableFiles.size() + " / " + shareableFiles.size(),
                fileColumn.right() - 86, fileColumn.y + 8, ModernUiRenderer.MUTED_TEXT, 74);
        ProfileUi.drawField(fontRenderer, searchField, searchBounds, "搜索文件", mouseX, mouseY);
        ProfileUi.drawInfo(fontRenderer, searchBounds.right() - 14, searchBounds.y + 4,
                "支持按文件名、中文名称或相对路径筛选。按 Ctrl+F 可以直接聚焦此搜索框。",
                mouseX, mouseY, hovered);

        int visible = Math.max(1, fileListBounds.height / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredShareableFiles.size() - visible);
        fileScroll = Math.max(0, Math.min(fileScroll, maxScroll));
        if (filteredShareableFiles.isEmpty()) {
            String empty = shareableFiles.isEmpty() ? "该档案下暂无可分享配置" : "没有匹配当前搜索条件的配置项";
            ModernUiRenderer.drawText(fontRenderer, empty, fileListBounds.x + 6,
                    fileListBounds.y + fileListBounds.height / 2, ModernUiRenderer.MUTED_TEXT,
                    fileListBounds.width - 12);
            fileBar.idle();
            return;
        }
        for (int i = 0; i < visible; i++) {
            int index = fileScroll + i;
            if (index >= filteredShareableFiles.size()) {
                break;
            }
            String path = filteredShareableFiles.get(index);
            boolean checked = checkedFiles.contains(path);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(fileListBounds.x, fileListBounds.y + i * ROW_HEIGHT,
                    fileListBounds.width - 8, ROW_HEIGHT - 2);
            ProfileUi.drawRow(row, index == selectedFileIndex, row.contains(mouseX, mouseY), checked);
            ProfileUi.drawCheckbox(row.x + 6, row.y + 5, checked);
            ModernUiRenderer.drawText(fontRenderer,
                    ProfileWorkbenchLogic.fileLabel(ProfileShareCodeManager.getDisplayNameForPath(path), path),
                    row.x + 22, row.y + 6, ModernUiRenderer.TEXT, row.width - 28);
        }
        fileBar.draw(fileListBounds, fileScroll, maxScroll, visible, filteredShareableFiles.size(), mouseX, mouseY,
                new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int value) {
                        fileScroll = value;
                    }
                });
    }

    private void drawPreviewColumn(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ProfileUi.drawSection(fontRenderer, previewColumn, "可分享字段");
        ProfileUi.drawInfo(fontRenderer, previewColumn.x + 12 + fontRenderer.getStringWidth("可分享字段"), previewColumn.y + 7,
                "勾选的字段会被裁剪后分享；一项都不勾选时，默认分享当前配置的全部字段。",
                mouseX, mouseY, hovered);
        String selectedPath = selectedFilePath();
        if (selectedPath == null) {
            ModernUiRenderer.drawText(fontRenderer, "请先在中间选择一项配置", previewColumn.x + 10,
                    previewColumn.y + 30, ModernUiRenderer.MUTED_TEXT, previewColumn.width - 20);
        } else {
            Set<String> checked = checkedFieldsByFile.get(selectedPath);
            String selection = checked == null || checked.isEmpty() ? "未勾选字段：默认全部分享"
                    : "已选 " + checked.size() + " 个字段";
            int selectionColor = checked == null || checked.isEmpty() ? ModernUiRenderer.SUCCESS
                    : ModernUiRenderer.ACCENT;
            ModernUiRenderer.drawText(fontRenderer,
                    ProfileShareCodeManager.getDisplayNameForPath(selectedPath) + "  |  " + selection,
                    previewColumn.x + 10, previewColumn.y + 28, selectionColor, previewColumn.width - 20);
            ModernUiRenderer.drawText(fontRenderer, "存储路径：" + selectedPath, previewColumn.x + 10,
                    previewColumn.y + 40, ModernUiRenderer.MUTED_TEXT, previewColumn.width - 20);
        }

        ModernUiRenderer.drawSubtlePanel(fieldListBounds.x, fieldListBounds.y, fieldListBounds.width,
                fieldListBounds.height, 4, 0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        int visible = Math.max(1, fieldListBounds.height / FIELD_ROW_HEIGHT);
        int maxScroll = Math.max(0, visibleFields.size() - visible);
        previewScroll = Math.max(0, Math.min(previewScroll, maxScroll));
        Set<String> checked = selectedPath == null ? null : checkedFieldsByFile.get(selectedPath);
        for (int i = 0; i < visible; i++) {
            int index = previewScroll + i;
            if (index >= visibleFields.size()) {
                break;
            }
            ConfigField field = visibleFields.get(index);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(fieldListBounds.x + 4,
                    fieldListBounds.y + 4 + i * FIELD_ROW_HEIGHT, fieldListBounds.width - 8, FIELD_ROW_HEIGHT - 3);
            boolean selected = field.isSelectable() && checked != null && checked.contains(field.getSelector());
            ProfileUi.drawRow(row, false, row.contains(mouseX, mouseY) && field.isSelectable(), selected);
            int indent = Math.min(28, field.getDepth() * 8);
            int textX = row.x + 6 + indent;
            if (field.isSelectable()) {
                ProfileUi.drawCheckbox(textX, row.y + 8, selected);
                textX += 16;
            } else {
                boolean collapsed = collapsedFieldGroups.contains(field.getSelector());
                if (collapsed) {
                    ModernUiRenderer.drawChevron(textX + 1, row.y + 8, true, ModernUiRenderer.ACCENT);
                } else {
                    ModernUiRenderer.drawDropdownChevron(textX, row.y + 8, ModernUiRenderer.ACCENT);
                }
                textX += 12;
            }
            int available = Math.max(20, row.right() - textX - 8);
            ModernUiRenderer.drawText(fontRenderer, field.getLabel(), textX, row.y + 3,
                    field.isSelectable() ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT, available);
            ModernUiRenderer.drawText(fontRenderer, field.getValue(), textX, row.y + 14, ModernUiRenderer.MUTED_TEXT,
                    available);
        }
        previewBar.draw(fieldListBounds, previewScroll, maxScroll, visible, visibleFields.size(), mouseX, mouseY,
                new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int value) {
                        previewScroll = value;
                    }
                });
    }

    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        String filterHint = fileSearchQuery == null || fileSearchQuery.trim().isEmpty()
                ? "未过滤" : "筛选: " + filteredShareableFiles.size() + "/" + shareableFiles.size();
        String selected = selectedProfileName() == null ? "-" : selectedProfileName();
        ModernUiRenderer.drawStatusDot(bounds.x + 14, bounds.bottom() - 68, statusColor);
        ModernUiRenderer.drawText(fontRenderer,
                statusMessage + "  |  已勾选 " + checkedFiles.size() + "  |  当前配置: " + selected
                        + "  |  " + filterHint,
                bounds.x + 26, bounds.bottom() - 70, statusColor, bounds.width - 40);

        boolean hasProfile = selectedProfileName() != null;
        boolean isActive = hasProfile && selectedProfileName().equals(ProfileManager.getActiveProfileName());
        boolean isDefault = hasProfile && ProfileManager.DEFAULT_PROFILE_NAME.equals(selectedProfileName());
        boolean hasFile = selectedFilePath() != null;
        drawAction(fontRenderer, "select", I18n.format("gui.profile.select"), ProfileUi.Tone.SUCCESS,
                hasProfile && !isActive, "切换到选中的配置档并重新加载设置。", mouseX, mouseY);
        drawAction(fontRenderer, "create", I18n.format("gui.profile.create"), ProfileUi.Tone.PRIMARY, true,
                "从默认配置复制一份新的配置档。", mouseX, mouseY);
        drawAction(fontRenderer, "delete", I18n.format("gui.profile.delete"), ProfileUi.Tone.DANGER,
                hasProfile && !isDefault, "删除选中的配置档及其文件。默认档不能删除。", mouseX, mouseY);
        drawAction(fontRenderer, "editor", "高级编辑", ProfileUi.Tone.DEFAULT, hasProfile && hasFile,
                "打开当前配置文件的原生编辑器。", mouseX, mouseY);
        drawAction(fontRenderer, "refresh", "刷新", ProfileUi.Tone.DEFAULT, hasProfile, "重新读取档案和可分享文件。",
                mouseX, mouseY);
        drawAction(fontRenderer, "export", "复制分享码", ProfileUi.Tone.SUCCESS,
                hasProfile && !checkedFiles.isEmpty() && !exporting, "把勾选的配置压缩成分享码并复制到剪贴板。",
                mouseX, mouseY);
        drawAction(fontRenderer, "import", "导入分享码", ProfileUi.Tone.PRIMARY, hasProfile,
                "粘贴分享码并预览后导入到当前配置档。", mouseX, mouseY);
        drawAction(fontRenderer, "selectAll", "全选配置", ProfileUi.Tone.DEFAULT,
                hasProfile && !filteredShareableFiles.isEmpty(), "勾选当前列表中的全部配置文件。", mouseX, mouseY);
        drawAction(fontRenderer, "clear", "清空勾选", ProfileUi.Tone.DEFAULT, !checkedFiles.isEmpty(),
                "取消所有分享勾选。", mouseX, mouseY);
        drawAction(fontRenderer, "done", I18n.format("gui.common.done"), ProfileUi.Tone.DEFAULT, true,
                "关闭配置档案页。", mouseX, mouseY);
    }

    private void drawAction(FontRenderer fontRenderer, String key, String label, ProfileUi.Tone tone, boolean enabled,
            String tooltip, int mouseX, int mouseY) {
        ModernMainLayout.Rect rect = actionBounds.get(key);
        if (rect == null) {
            return;
        }
        boolean hoveredButton = enabled && rect.contains(mouseX, mouseY);
        ProfileUi.drawButton(fontRenderer, rect, label, tone, enabled, hoveredButton);
        if (hoveredButton) {
            this.hovered[0] = tooltip;
        }
    }

    private boolean handleActionClick(int mouseX, int mouseY) {
        if (hitAction("select", mouseX, mouseY) && canSelect()) {
            ProfileManager.setActiveProfile(selectedProfileName());
            reloadOtherFeatureConfigsForActiveProfile();
            status("已切换到配置: " + selectedProfileName(), ModernUiRenderer.SUCCESS);
            refreshProfiles();
            refreshFilesForSelectedProfile();
            return true;
        }
        if (hitAction("create", mouseX, mouseY)) {
            pushModal(I18n.format("gui.profile.input_new"), "将从默认配置复制一份新档案。", true, false,
                    new ProfileModalPanel.Result() {
                        @Override
                        public void accept(String value) {
                            createProfile(value);
                        }
                    });
            return true;
        }
        if (hitAction("delete", mouseX, mouseY) && canDelete()) {
            final String profile = selectedProfileName();
            pushModal("删除配置档", "将删除配置档“" + profile + "”及其文件，操作不可恢复。", false, true,
                    new ProfileModalPanel.Result() {
                        @Override
                        public void accept(String value) {
                            deleteProfile(profile);
                        }
                    });
            return true;
        }
        if (hitAction("editor", mouseX, mouseY)) {
            openEditor();
            return true;
        }
        if (hitAction("refresh", mouseX, mouseY) && selectedProfileName() != null) {
            refreshProfiles();
            refreshFilesForSelectedProfile();
            status("已刷新配置视图", ModernUiRenderer.SUCCESS);
            return true;
        }
        if (hitAction("export", mouseX, mouseY) && selectedProfileName() != null && !checkedFiles.isEmpty()
                && !exporting) {
            exportShareCode();
            return true;
        }
        if (hitAction("import", mouseX, mouseY) && selectedProfileName() != null) {
            final String target = selectedProfileName();
            pushModal("导入分享码", "粘贴分享码到当前配置: " + target, true, false, new ProfileModalPanel.Result() {
                @Override
                public void accept(String value) {
                    importShareCode(target, value);
                }
            });
            return true;
        }
        if (hitAction("selectAll", mouseX, mouseY) && !filteredShareableFiles.isEmpty()) {
            checkedFiles.clear();
            checkedFiles.addAll(filteredShareableFiles);
            status("已选择当前列表中的配置文件，共 " + checkedFiles.size() + " 项", ModernUiRenderer.SUCCESS);
            return true;
        }
        if (hitAction("clear", mouseX, mouseY) && !checkedFiles.isEmpty()) {
            checkedFiles.clear();
            status("已清空分享勾选", ModernUiRenderer.SUBTLE_TEXT);
            return true;
        }
        if (hitAction("done", mouseX, mouseY)) {
            returnRequested = true;
            return true;
        }
        return false;
    }

    private boolean hitAction(String key, int mouseX, int mouseY) {
        return ProfileUi.hit(actionBounds.get(key), mouseX, mouseY);
    }

    private boolean canSelect() {
        return selectedProfileName() != null
                && !selectedProfileName().equals(ProfileManager.getActiveProfileName());
    }

    private boolean canDelete() {
        return selectedProfileName() != null
                && !ProfileManager.DEFAULT_PROFILE_NAME.equals(selectedProfileName());
    }

    private void handleProfileClick(int mouseX, int mouseY) {
        if (profileListBounds == null || !profileListBounds.contains(mouseX, mouseY)) {
            return;
        }
        int index = profileScroll + (mouseY - profileListBounds.y) / ROW_HEIGHT;
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        selectedProfileIndex = index;
        checkedFiles.clear();
        checkedFieldsByFile.clear();
        selectedFileIndex = 0;
        lastFileAnchorIndex = 0;
        refreshFilesForSelectedProfile();
    }

    private void handleFileClick(int mouseX, int mouseY) {
        if (fileListBounds == null || !fileListBounds.contains(mouseX, mouseY)
                || filteredShareableFiles.isEmpty()) {
            return;
        }
        int index = fileScroll + (mouseY - fileListBounds.y) / ROW_HEIGHT;
        if (index < 0 || index >= filteredShareableFiles.size()) {
            return;
        }
        boolean checkbox = mouseX <= fileListBounds.x + 20;
        ProfileWorkbenchLogic.FileClickResult result = ProfileWorkbenchLogic.applyFileClick(filteredShareableFiles,
                checkedFiles, selectedFileIndex, lastFileAnchorIndex, index, checkbox, GuiScreen.isShiftKeyDown(),
                GuiScreen.isCtrlKeyDown());
        checkedFiles.clear();
        checkedFiles.addAll(result.checked);
        selectedFileIndex = result.selectedIndex;
        lastFileAnchorIndex = result.lastAnchor;
        loadPreviewForSelectedFile();
    }

    private void handleFieldClick(int mouseX, int mouseY) {
        if (fieldListBounds == null || !fieldListBounds.contains(mouseX, mouseY) || visibleFields.isEmpty()) {
            return;
        }
        int index = previewScroll + (mouseY - fieldListBounds.y) / FIELD_ROW_HEIGHT;
        if (index < 0 || index >= visibleFields.size()) {
            return;
        }
        ConfigField field = visibleFields.get(index);
        if (!field.isSelectable()) {
            if (field.getSelector() != null && !field.getSelector().isEmpty()) {
                if (!collapsedFieldGroups.add(field.getSelector())) {
                    collapsedFieldGroups.remove(field.getSelector());
                }
                visibleFields = ProfileWorkbenchLogic.visibleFields(allFields, collapsedFieldGroups);
                previewScroll = 0;
            }
            return;
        }
        String file = selectedFilePath();
        if (file == null) {
            return;
        }
        Set<String> checked = checkedFieldsByFile.get(file);
        if (checked == null) {
            checked = new LinkedHashSet<String>();
            checkedFieldsByFile.put(file, checked);
        }
        if (!checked.add(field.getSelector())) {
            checked.remove(field.getSelector());
        }
        if (checked.isEmpty()) {
            checkedFieldsByFile.remove(file);
        }
        checkedFiles.add(file);
    }

    private void refreshProfiles() {
        profiles.clear();
        profiles.addAll(ProfileManager.getAllProfileNames());
        if (profiles.isEmpty()) {
            selectedProfileIndex = -1;
            profileScroll = 0;
            return;
        }
        if (selectedProfileIndex < 0 || selectedProfileIndex >= profiles.size()) {
            selectedProfileIndex = profiles.indexOf(ProfileManager.getActiveProfileName());
            if (selectedProfileIndex < 0) {
                selectedProfileIndex = 0;
            }
        }
    }

    private void refreshFilesForSelectedProfile() {
        String preferredPath = selectedFilePath();
        String profileName = selectedProfileName();
        shareableFiles.clear();
        if (profileName == null) {
            filteredShareableFiles.clear();
            selectedFileIndex = -1;
            visibleFields = new ArrayList<ConfigField>();
            checkedFiles.clear();
            checkedFieldsByFile.clear();
            lastFileAnchorIndex = -1;
            return;
        }
        shareableFiles.addAll(ProfileShareCodeManager.listShareableFiles(profileName));
        checkedFiles.retainAll(shareableFiles);
        checkedFieldsByFile.keySet().retainAll(new LinkedHashSet<String>(shareableFiles));
        applyFileFilter(preferredPath);
    }

    private void applyFileFilter(String preferredPath) {
        filteredShareableFiles.clear();
        for (String path : shareableFiles) {
            if (ProfileWorkbenchLogic.matchesFileFilter(path, ProfileShareCodeManager.getDisplayNameForPath(path),
                    fileSearchQuery)) {
                filteredShareableFiles.add(path);
            }
        }
        if (filteredShareableFiles.isEmpty()) {
            selectedFileIndex = -1;
            lastFileAnchorIndex = -1;
            visibleFields = new ArrayList<ConfigField>();
            previewScroll = 0;
            return;
        }
        int newIndex = preferredPath == null ? -1 : filteredShareableFiles.indexOf(preferredPath);
        if (newIndex < 0 && selectedFileIndex >= 0) {
            newIndex = Math.min(selectedFileIndex, filteredShareableFiles.size() - 1);
        }
        if (newIndex < 0) {
            newIndex = 0;
        }
        selectedFileIndex = newIndex;
        if (lastFileAnchorIndex < 0 || lastFileAnchorIndex >= filteredShareableFiles.size()) {
            lastFileAnchorIndex = selectedFileIndex;
        }
        loadPreviewForSelectedFile();
    }

    private void loadPreviewForSelectedFile() {
        String profileName = selectedProfileName();
        String relativePath = selectedFilePath();
        if (profileName == null || relativePath == null) {
            visibleFields = new ArrayList<ConfigField>();
            return;
        }
        try {
            String content = ProfileShareCodeManager.loadProfileFileContent(profileName, relativePath);
            allFields = new ArrayList<ConfigField>(ProfileConfigFieldCodec.describe(relativePath, content));
            collapsedFieldGroups.clear();
            visibleFields = ProfileWorkbenchLogic.visibleFields(allFields, collapsedFieldGroups);
        } catch (Exception error) {
            visibleFields = new ArrayList<ConfigField>(ProfileConfigFieldCodec.describe(relativePath, ""));
            status("读取配置字段失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
        previewScroll = 0;
    }

    private void createProfile(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            status("已取消创建", ModernUiRenderer.SUBTLE_TEXT);
            return;
        }
        String name = rawName.trim();
        if (ProfileManager.createProfile(name)) {
            refreshProfiles();
            selectedProfileIndex = profiles.indexOf(name);
            checkedFiles.clear();
            checkedFieldsByFile.clear();
            selectedFileIndex = 0;
            lastFileAnchorIndex = 0;
            refreshFilesForSelectedProfile();
            status("已创建配置: " + name, ModernUiRenderer.SUCCESS);
        } else {
            status("创建失败，名称可能重复或无效", ModernUiRenderer.DANGER);
        }
    }

    private void deleteProfile(String profile) {
        if (ProfileManager.deleteProfile(profile)) {
            status("已删除配置: " + profile, ModernUiRenderer.SUCCESS);
            refreshProfiles();
            checkedFiles.clear();
            checkedFieldsByFile.clear();
            selectedFileIndex = 0;
            lastFileAnchorIndex = 0;
            refreshFilesForSelectedProfile();
        } else {
            status("删除失败: " + profile, ModernUiRenderer.DANGER);
        }
    }

    private void openEditor() {
        String profileName = selectedProfileName();
        String relativePath = selectedFilePath();
        if (profileName == null || relativePath == null) {
            return;
        }
        try {
            String content = ProfileShareCodeManager.loadProfileFileContent(profileName, relativePath);
            overlays.push(new ProfileFileEditorPanel(this, profileName, relativePath, content));
        } catch (Exception error) {
            status("打开编辑器失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
    }

    private void exportShareCode() {
        final String profileName = selectedProfileName();
        if (profileName == null || exporting || checkedFiles.isEmpty()) {
            return;
        }
        final List<String> exportFiles = new ArrayList<String>(checkedFiles);
        final Map<String, Collection<String>> selectedFields = new LinkedHashMap<String, Collection<String>>();
        for (String file : exportFiles) {
            Set<String> fields = checkedFieldsByFile.get(file);
            if (fields != null && !fields.isEmpty()) {
                selectedFields.put(file, new ArrayList<String>(fields));
            }
        }
        exporting = true;
        status("正在压缩分享码...", ModernUiRenderer.ACCENT);
        final Minecraft minecraft = Minecraft.getMinecraft();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String shareCode = ProfileShareCodeManager.generateShareCode(profileName, exportFiles,
                            selectedFields);
                    minecraft.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            exporting = false;
                            GuiScreen.setClipboardString(shareCode);
                            status("分享码已复制：" + profileName + "，" + exportFiles.size() + " 项配置，"
                                    + shareCode.length() + " 字符", ModernUiRenderer.SUCCESS);
                        }
                    });
                } catch (Exception error) {
                    final String message = error.getMessage();
                    minecraft.addScheduledTask(new Runnable() {
                        @Override
                        public void run() {
                            exporting = false;
                            status("生成分享码失败: " + message, ModernUiRenderer.DANGER);
                        }
                    });
                }
            }
        }, "profile-share-compression");
        worker.setDaemon(true);
        worker.start();
    }

    private void importShareCode(String targetProfile, String code) {
        if (code == null || code.trim().isEmpty()) {
            status("已取消导入", ModernUiRenderer.SUBTLE_TEXT);
            return;
        }
        try {
            ProfileShareCodeManager.ImportPreview preview = ProfileShareCodeManager.previewImport(code, targetProfile);
            overlays.push(new ProfileImportPreviewPanel(this, targetProfile, preview));
        } catch (Exception error) {
            status("导入失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
    }

    private void applyProfileDivider(int mouseX) {
        int total = Math.max(1, bounds.width - 16 - 20);
        savedProfileColumnRatio = ProfileWorkbenchLogic.clampProfileRatio(
                (mouseX - bounds.x - 8) / (double) total);
        layout();
    }

    private void applyFileDivider(int mouseX) {
        int[] widths = ProfileWorkbenchLogic.columnWidths(Math.max(1, bounds.width - 16 - 20),
                savedProfileColumnRatio, savedFileColumnRatio);
        int remaining = Math.max(1, widths[1] + widths[2]);
        savedFileColumnRatio = ProfileWorkbenchLogic.clampFileRatio(
                (mouseX - fileColumn.x) / (double) remaining);
        layout();
    }

    private String selectedProfileName() {
        if (selectedProfileIndex < 0 || selectedProfileIndex >= profiles.size()) {
            return null;
        }
        return profiles.get(selectedProfileIndex);
    }

    private String selectedFilePath() {
        if (selectedFileIndex < 0 || selectedFileIndex >= filteredShareableFiles.size()) {
            return null;
        }
        return filteredShareableFiles.get(selectedFileIndex);
    }

    private void status(String message, int color) {
        statusMessage = message == null ? "" : message;
        statusColor = color;
    }

    private void reloadOtherFeatureConfigsForActiveProfile() {
        FlightPathingConfig.load();
        SpeedHandler.loadConfig();
        MovementFeatureManager.loadConfig();
        BlockFeatureManager.loadConfig();
        WorldFeatureManager.loadConfig();
        RenderFeatureManager.loadConfig();
        ItemFeatureManager.loadConfig();
        MiscFeatureManager.loadConfig();
    }

    private static synchronized void ensurePreferencesLoaded() {
        if (preferencesLoaded) {
            return;
        }
        preferencesLoaded = true;
        savedProfileColumnRatio = 0.18D;
        savedFileColumnRatio = 0.28D;
        Path path = preferencesPath();
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (root.has("profileColumnRatio")) {
                savedProfileColumnRatio = root.get("profileColumnRatio").getAsDouble();
            }
            if (root.has("fileColumnRatio")) {
                savedFileColumnRatio = root.get("fileColumnRatio").getAsDouble();
            }
        } catch (Exception ignored) {
            savedProfileColumnRatio = 0.18D;
            savedFileColumnRatio = 0.28D;
        }
        savedProfileColumnRatio = ProfileWorkbenchLogic.clampProfileRatio(savedProfileColumnRatio);
        savedFileColumnRatio = ProfileWorkbenchLogic.clampFileRatio(savedFileColumnRatio);
    }

    private static synchronized void persistLayout() {
        Path path = preferencesPath();
        if (path == null) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            JsonObject root = new JsonObject();
            root.addProperty("profileColumnRatio", ProfileWorkbenchLogic.clampProfileRatio(savedProfileColumnRatio));
            root.addProperty("fileColumnRatio", ProfileWorkbenchLogic.clampFileRatio(savedFileColumnRatio));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(root.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private static Path preferencesPath() {
        try {
            return ProfileManager.getCurrentProfileDir().resolve(PREFERENCES_FILE_NAME);
        } catch (Exception ignored) {
            return null;
        }
    }
}
