package com.zszl.zszlScriptMod.gui.modern.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager.ImportPreview;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager.ImportPreviewEntry;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager.PathConflictMode;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/** Native share-code import preview that stays inside the modern shell. */
final class ProfileImportPreviewPanel extends ModernEmbeddedPanel {

    private final ProfileWorkbenchTab owner;
    private final String targetProfileName;
    private final ImportPreview preview;
    private final List<ImportPreviewEntry> entries;
    private final Set<String> selectedPaths = new LinkedHashSet<String>();
    private final ModernHoverScrollbar listBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar detailBar = new ModernHoverScrollbar();
    private final String[] hovered = new String[] { "" };

    private int listScroll;
    private int detailScroll;
    private int selectedIndex;
    private PathConflictMode pathConflictMode = PathConflictMode.REPLACE;
    private String status = "请确认每个文件的导入策略：右侧显示本地到导入后的差异";
    private int statusColor = ModernUiRenderer.SUBTLE_TEXT;
    private ModernMainLayout.Rect area = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect listBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect importBounds;
    private ModernMainLayout.Rect selectAllBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect conflictBounds;
    private ModernMainLayout.Rect backBounds;
    private int lastMouseX;
    private int lastMouseY;

    ProfileImportPreviewPanel(ProfileWorkbenchTab owner, String targetProfileName, ImportPreview preview) {
        super("导入分享码预览");
        this.owner = owner;
        this.targetProfileName = targetProfileName == null ? "" : targetProfileName;
        this.preview = preview;
        this.entries = preview == null ? new ArrayList<ImportPreviewEntry>()
                : new ArrayList<ImportPreviewEntry>(preview.getEntries());
        for (ImportPreviewEntry entry : entries) {
            if (entry != null) {
                selectedPaths.add(entry.getRelativePath());
            }
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        area = bounds == null ? area : bounds;
        setBounds(area);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hovered[0] = "";
        layout();

        ModernUiRenderer.drawPanel(area.x, area.y, area.width, area.height, 7, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "导入分享码预览 - " + targetProfileName, area.x + 14, area.y + 10,
                ModernUiRenderer.TEXT, area.width - 40);
        ProfileUi.drawInfo(fontRenderer, area.right() - 22, area.y + 9,
                "勾选要导入的文件；右侧查看合并或替换后的差异。", mouseX, mouseY, hovered);
        ModernUiRenderer.drawSubtlePanel(area.x + 10, area.y + 30, area.width - 20, 18, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(area.x + 16, area.y + 35, statusColor);
        ModernUiRenderer.drawText(fontRenderer, status, area.x + 28, area.y + 34, statusColor, area.width - 48);

        drawList(fontRenderer, mouseX, mouseY);
        drawDetail(fontRenderer, mouseX, mouseY);

        ModernUiRenderer.drawText(fontRenderer,
                "共 " + entries.size() + " 项 | 已勾选 " + selectedPaths.size() + " 项 | 有变化 "
                        + (preview == null ? 0 : preview.getChangedCount(pathConflictMode)) + " 项",
                area.x + 12, area.bottom() - 58, ModernUiRenderer.MUTED_TEXT, area.width - 24);

        if (hasPathConflicts()) {
            ProfileUi.drawButton(fontRenderer, conflictBounds,
                    "路径冲突（同分组 + 同序列名）：" + pathConflictMode.getDisplayName() + "  [点击切换]",
                    ProfileUi.Tone.DEFAULT, true, ProfileUi.hit(conflictBounds, mouseX, mouseY));
        }
        boolean canImport = !selectedPaths.isEmpty();
        ProfileUi.drawButton(fontRenderer, importBounds, "确认导入所选", ProfileUi.Tone.PRIMARY, canImport,
                canImport && ProfileUi.hit(importBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, selectAllBounds, "全选", ProfileUi.Tone.SUCCESS,
                !entries.isEmpty() && selectedPaths.size() < entries.size(),
                ProfileUi.hit(selectAllBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, clearBounds, "清空", ProfileUi.Tone.DEFAULT, !selectedPaths.isEmpty(),
                ProfileUi.hit(clearBounds, mouseX, mouseY));
        ProfileUi.drawButton(fontRenderer, backBounds, "返回", ProfileUi.Tone.DANGER, true,
                ProfileUi.hit(backBounds, mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (listBar.beginDrag(mouseX, mouseY) || detailBar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (ProfileUi.hit(importBounds, mouseX, mouseY) && !selectedPaths.isEmpty()) {
            applyImport();
            return true;
        }
        if (ProfileUi.hit(selectAllBounds, mouseX, mouseY)) {
            selectedPaths.clear();
            for (ImportPreviewEntry entry : entries) {
                selectedPaths.add(entry.getRelativePath());
            }
            status("已全选所有导入项", ModernUiRenderer.SUCCESS);
            return true;
        }
        if (ProfileUi.hit(clearBounds, mouseX, mouseY)) {
            selectedPaths.clear();
            status("已取消全部导入勾选", ModernUiRenderer.SUBTLE_TEXT);
            return true;
        }
        if (ProfileUi.hit(conflictBounds, mouseX, mouseY) && hasPathConflicts()) {
            pathConflictMode = pathConflictMode == PathConflictMode.REPLACE
                    ? PathConflictMode.APPEND : PathConflictMode.REPLACE;
            detailScroll = 0;
            status("路径冲突将按“" + pathConflictMode.getDisplayName() + "”处理", ModernUiRenderer.ACCENT);
            return true;
        }
        if (ProfileUi.hit(backBounds, mouseX, mouseY)) {
            owner.back();
            return true;
        }
        handleListClick(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return listBar.applyDrag(mouseX, mouseY) || detailBar.applyDrag(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        listBar.endDrag();
        detailBar.endDrag();
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            owner.back();
            return true;
        }
        if (keyCode == Keyboard.KEY_UP && selectedIndex > 0) {
            selectedIndex--;
            detailScroll = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN && selectedIndex < entries.size() - 1) {
            selectedIndex++;
            detailScroll = 0;
            return true;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && !selectedPaths.isEmpty()) {
            applyImport();
            return true;
        }
        if (keyCode == Keyboard.KEY_SPACE && selectedIndex >= 0 && selectedIndex < entries.size()) {
            toggle(entries.get(selectedIndex).getRelativePath());
            return true;
        }
        return true;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (wheel == 0) {
            return true;
        }
        int delta = wheel > 0 ? -1 : 1;
        if (listBounds != null && listBounds.contains(lastMouseX, lastMouseY)) {
            listScroll = Math.max(0, listScroll + delta);
        } else if (detailBounds != null && detailBounds.contains(lastMouseX, lastMouseY)) {
            detailScroll = Math.max(0, detailScroll + delta * 2);
        }
        return true;
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hovered[0] == null ? "" : hovered[0];
    }

    @Override
    public void discardDraft() {
        listBar.endDrag();
        detailBar.endDrag();
    }

    private void layout() {
        int footer = hasPathConflicts() ? 78 : 54;
        int top = area.y + 54;
        int height = Math.max(80, area.height - footer - 58);
        int listWidth = Math.min(340, Math.max(140, (area.width - 30) / 3));
        listBounds = new ModernMainLayout.Rect(area.x + 10, top, listWidth, height);
        detailBounds = new ModernMainLayout.Rect(listBounds.right() + 10, top,
                Math.max(1, area.right() - 10 - listBounds.right() - 10), height);
        int buttonY = area.bottom() - 32;
        int gap = 6;
        int buttonWidth = Math.max(64, (area.width - 24 - gap * 3) / 4);
        importBounds = new ModernMainLayout.Rect(area.x + 12, buttonY, buttonWidth, 22);
        selectAllBounds = new ModernMainLayout.Rect(importBounds.right() + gap, buttonY, buttonWidth, 22);
        clearBounds = new ModernMainLayout.Rect(selectAllBounds.right() + gap, buttonY, buttonWidth, 22);
        backBounds = new ModernMainLayout.Rect(clearBounds.right() + gap, buttonY, buttonWidth, 22);
        conflictBounds = hasPathConflicts()
                ? new ModernMainLayout.Rect(area.x + 12, buttonY - 26, area.width - 24, 22) : null;
    }

    private void drawList(FontRenderer font, int mouseX, int mouseY) {
        ProfileUi.drawSection(font, listBounds, "导入项目（勾选=实际导入）");
        ProfileUi.drawInfo(font, listBounds.x + 118, listBounds.y + 7,
                "点击行查看详情；点击左侧复选框切换是否导入。", mouseX, mouseY, hovered);
        int rowHeight = 26;
        int contentY = listBounds.y + 26;
        int contentH = Math.max(1, listBounds.height - 32);
        int visible = Math.max(1, contentH / rowHeight);
        int maxScroll = Math.max(0, entries.size() - visible);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(listBounds.x + 6, contentY,
                Math.max(1, listBounds.width - 12), contentH);
        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(font, "分享码中没有可导入的配置", list.x + 6, list.y + list.height / 2,
                    ModernUiRenderer.MUTED_TEXT, list.width - 12);
            listBar.idle();
            return;
        }
        for (int i = 0; i < visible; i++) {
            int index = listScroll + i;
            if (index >= entries.size()) {
                break;
            }
            ImportPreviewEntry entry = entries.get(index);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x, list.y + i * rowHeight, list.width - 8,
                    rowHeight - 2);
            boolean checked = selectedPaths.contains(entry.getRelativePath());
            ProfileUi.drawRow(row, index == selectedIndex, row.contains(mouseX, mouseY), checked);
            ProfileUi.drawCheckbox(row.x + 6, row.y + 7, checked);
            String strategy = entry.hasPathConflicts() ? pathConflictMode.getDisplayName()
                    : entry.getStrategy().getDisplayName();
            int strategyColor = entry.hasPathConflicts()
                    ? (pathConflictMode == PathConflictMode.APPEND ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING)
                    : entry.getStrategy() == ProfileShareCodeManager.ImportStrategy.MERGE
                            ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING;
            ModernUiRenderer.drawText(font, ProfileShareCodeManager.getDisplayNameForPath(entry.getRelativePath()),
                    row.x + 24, row.y + 3, ModernUiRenderer.TEXT, Math.max(20, row.width - 70));
            ModernUiRenderer.drawText(font, strategy, row.right() - 42, row.y + 3, strategyColor, 38);
            String summary = entry.hasChanges(pathConflictMode) ? entry.getSummary() : "无变化，导入后不会修改";
            ModernUiRenderer.drawText(font, summary, row.x + 24, row.y + 13, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, row.width - 32));
        }
        listBar.draw(list, listScroll, maxScroll, visible, entries.size(), mouseX, mouseY, new java.util.function.IntConsumer() {
            @Override
            public void accept(int value) {
                listScroll = value;
            }
        });
    }

    private void drawDetail(FontRenderer font, int mouseX, int mouseY) {
        ProfileUi.drawSection(font, detailBounds, "导入策略详情");
        ProfileUi.drawInfo(font, detailBounds.x + 88, detailBounds.y + 7,
                "显示当前文件的合并/替换策略和逐行差异。", mouseX, mouseY, hovered);
        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(font, "没有可显示的详情", detailBounds.x + 12,
                    detailBounds.y + detailBounds.height / 2, ModernUiRenderer.MUTED_TEXT, detailBounds.width - 24);
            detailBar.idle();
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
        ImportPreviewEntry entry = entries.get(selectedIndex);
        ModernUiRenderer.drawText(font,
                "文件: " + ProfileShareCodeManager.getDisplayNameForPath(entry.getRelativePath())
                        + " (" + entry.getRelativePath() + ")",
                detailBounds.x + 10, detailBounds.y + 28, ModernUiRenderer.SUBTLE_TEXT, detailBounds.width - 20);
        boolean changed = entry.hasChanges(pathConflictMode);
        ModernUiRenderer.drawText(font,
                "模式: " + strategyLabel(entry) + " | " + (changed ? "有变化" : "无变化"),
                detailBounds.x + 10, detailBounds.y + 40,
                changed ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, detailBounds.width - 20);

        ModernMainLayout.Rect content = new ModernMainLayout.Rect(detailBounds.x + 8, detailBounds.y + 54,
                Math.max(1, detailBounds.width - 16), Math.max(1, detailBounds.height - 62));
        ModernUiRenderer.drawSubtlePanel(content.x, content.y, content.width, content.height, 4, 0xFF101820,
                ModernUiRenderer.BORDER_SUBTLE);
        List<DetailLine> lines = buildDetailLines(entry);
        int lineHeight = Math.max(10, font == null ? 10 : font.FONT_HEIGHT + 2);
        int visible = Math.max(1, (content.height - 8) / lineHeight);
        int maxScroll = Math.max(0, lines.size() - visible);
        detailScroll = Math.max(0, Math.min(detailScroll, maxScroll));
        for (int i = 0; i < visible; i++) {
            int index = detailScroll + i;
            if (index >= lines.size()) {
                break;
            }
            DetailLine line = lines.get(index);
            ModernUiRenderer.drawText(font, line.text, content.x + 6, content.y + 5 + i * lineHeight, line.color,
                    content.width - 14);
        }
        detailBar.draw(content, detailScroll, maxScroll, visible, lines.size(), mouseX, mouseY,
                new java.util.function.IntConsumer() {
                    @Override
                    public void accept(int value) {
                        detailScroll = value;
                    }
                });
    }

    private void handleListClick(int mouseX, int mouseY) {
        if (listBounds == null) {
            return;
        }
        int contentY = listBounds.y + 26;
        int rowHeight = 26;
        if (mouseY < contentY || mouseY >= listBounds.bottom() - 4) {
            return;
        }
        int index = listScroll + (mouseY - contentY) / rowHeight;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        selectedIndex = index;
        detailScroll = 0;
        ImportPreviewEntry entry = entries.get(index);
        if (mouseX <= listBounds.x + 24 || GuiScreen.isCtrlKeyDown()) {
            toggle(entry.getRelativePath());
        }
    }

    private void toggle(String path) {
        if (path == null) {
            return;
        }
        if (!selectedPaths.add(path)) {
            selectedPaths.remove(path);
        }
    }

    private void applyImport() {
        try {
            owner.handleImportApplied(ProfileShareCodeManager.applyImportPreview(preview, selectedPaths,
                    pathConflictMode));
            owner.back();
        } catch (Exception error) {
            status("导入失败: " + error.getMessage(), ModernUiRenderer.DANGER);
        }
    }

    private boolean hasPathConflicts() {
        return preview != null && preview.getPathConflictCount() > 0;
    }

    private String strategyLabel(ImportPreviewEntry entry) {
        if (entry != null && entry.hasPathConflicts()) {
            return "路径冲突" + pathConflictMode.getDisplayName();
        }
        return entry == null ? "" : entry.getStrategy().getDisplayName();
    }

    private List<DetailLine> buildDetailLines(ImportPreviewEntry entry) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        if (entry == null) {
            lines.add(new DetailLine("没有可显示的详情", ModernUiRenderer.MUTED_TEXT));
            return lines;
        }
        lines.add(new DetailLine(entry.getSummary(), ModernUiRenderer.TEXT));
        lines.add(new DetailLine("", ModernUiRenderer.TEXT));
        lines.add(new DetailLine("策略: " + strategyLabel(entry) + " | 状态: "
                + (entry.hasChanges(pathConflictMode) ? "有变化" : "无变化"), ModernUiRenderer.SUBTLE_TEXT));
        for (String detail : entry.getDetailLines()) {
            lines.add(new DetailLine(detail, ModernUiRenderer.MUTED_TEXT));
        }
        lines.add(new DetailLine("", ModernUiRenderer.TEXT));
        lines.add(new DetailLine("差异预览（本地 -> 导入后）", ModernUiRenderer.ACCENT));
        lines.add(new DetailLine("红色 = 将被删除；绿色 = 将新增；灰色 = 保留", ModernUiRenderer.MUTED_TEXT));
        for (ProfileImportDiff.Line line : ProfileImportDiff.diff(entry.getExistingContent(),
                entry.getFinalContent(pathConflictMode))) {
            int color = line.kind == ProfileImportDiff.Kind.ADD ? ModernUiRenderer.SUCCESS
                    : line.kind == ProfileImportDiff.Kind.REMOVE ? ModernUiRenderer.DANGER
                            : ModernUiRenderer.MUTED_TEXT;
            String prefix = line.kind == ProfileImportDiff.Kind.ADD ? "+ "
                    : line.kind == ProfileImportDiff.Kind.REMOVE ? "- " : "  ";
            String text = line.text.isEmpty() ? "<空行>" : line.text;
            lines.add(new DetailLine(prefix + text, color));
        }
        return lines;
    }

    private void status(String message, int color) {
        this.status = message == null ? "" : message;
        this.statusColor = color;
    }

    private static final class DetailLine {
        final String text;
        final int color;

        DetailLine(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }
}
