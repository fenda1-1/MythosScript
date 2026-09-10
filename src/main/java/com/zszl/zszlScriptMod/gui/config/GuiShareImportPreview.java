package com.zszl.zszlScriptMod.gui.config;

import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.otherfeatures.gui.common.ModernFeatureConfigUi;
import com.zszl.zszlScriptMod.system.ProfileShareCodeManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.awt.Rectangle;

public class GuiShareImportPreview extends ThemedGuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {

    private final GuiProfileManager parentScreen;
    private final String targetProfileName;
    private final ProfileShareCodeManager.ImportPreview preview;
    private final List<ProfileShareCodeManager.ImportPreviewEntry> entries;
    private final Set<String> selectedPaths = new LinkedHashSet<>();

    private int listScroll = 0;
    private int listMaxScroll = 0;
    private int detailScroll = 0;
    private int detailMaxScroll = 0;
    private int selectedIndex = 0;
    private Rectangle listScrollbar, listThumb, detailScrollbar, detailThumb;
    private int draggingMode = 0, dragOffset = 0;

    private GuiButton btnImport;
    private GuiButton btnSelectAll;
    private GuiButton btnClearAll;
    private GuiButton btnPathConflictMode;
    private ProfileShareCodeManager.PathConflictMode pathConflictMode =
            ProfileShareCodeManager.PathConflictMode.REPLACE;

    private String statusMessage = "§7请确认每个文件的导入策略：右侧显示本地 → 导入后的差异预览（红删绿增）";
    private int statusColor = 0xFFB8C7D9;

    public GuiShareImportPreview(GuiProfileManager parentScreen, String targetProfileName,
            ProfileShareCodeManager.ImportPreview preview) {
        this.parentScreen = parentScreen;
        this.targetProfileName = targetProfileName == null ? "" : targetProfileName;
        this.preview = preview;
        this.entries = preview == null
                ? new ArrayList<ProfileShareCodeManager.ImportPreviewEntry>()
                : new ArrayList<>(preview.getEntries());

        for (ProfileShareCodeManager.ImportPreviewEntry entry : this.entries) {
            if (entry != null) {
                selectedPaths.add(entry.getRelativePath());
            }
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int panelX = getPanelX();
        int panelY = getPanelY();
        int bottomY = panelY + getPanelHeight() - 28;
        int footerGap = 6;
        int footerWidth = Math.max(48, (getPanelWidth() - 20 - footerGap * 3) / 4);
        int footerX = panelX + 10;
        this.buttonList.add(new ThemedButton(0, footerX, bottomY, footerWidth, 20, "§a确认导入所选"));
        this.buttonList.add(new ThemedButton(1, footerX + footerWidth + footerGap, bottomY, footerWidth, 20,
                "§a全选"));
        this.buttonList.add(new ThemedButton(2, footerX + (footerWidth + footerGap) * 2, bottomY, footerWidth, 20,
                "§7清空"));
        this.buttonList.add(new ThemedButton(3, footerX + (footerWidth + footerGap) * 3, bottomY, footerWidth, 20,
                "§c返回"));
        if (hasPathConflicts()) {
            btnPathConflictMode = new ThemedButton(4, footerX, bottomY - 24, getPanelWidth() - 20, 20, "");
            this.buttonList.add(btnPathConflictMode);
            updatePathConflictButtonText();
        } else {
            btnPathConflictMode = null;
        }

        btnImport = this.buttonList.get(0);
        btnSelectAll = this.buttonList.get(1);
        btnClearAll = this.buttonList.get(2);

        ModernTooltipSupport.registerButton(this, btnImport,
                ModernFeatureConfigUi.tooltip("确认导入", "只导入当前勾选的文件，并应用右侧显示的策略。"));
        ModernTooltipSupport.registerButton(this, btnSelectAll,
                ModernFeatureConfigUi.tooltip("全选", "勾选分享码中的全部配置文件。"));
        ModernTooltipSupport.registerButton(this, btnClearAll,
                ModernFeatureConfigUi.tooltip("清空选择", "取消所有文件的导入勾选。"));
        ModernTooltipSupport.registerButton(this, this.buttonList.get(3),
                ModernFeatureConfigUi.tooltip("返回", "放弃本次导入并返回配置档案。"));
        if (btnPathConflictMode != null) {
            ModernTooltipSupport.registerButton(this, btnPathConflictMode,
                    ModernFeatureConfigUi.tooltip("路径冲突处理",
                            "仅当分组名称和序列名称都相同时生效。替换为默认；追加会把导入步骤接到现有步骤之后。"));
        }

        clampSelection();
        updateButtonStates();
    }

    private void updateButtonStates() {
        btnImport.enabled = !selectedPaths.isEmpty();
        btnSelectAll.enabled = !entries.isEmpty() && selectedPaths.size() < entries.size();
        btnClearAll.enabled = !selectedPaths.isEmpty();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                applySelectedImport();
                break;
            case 1:
                selectedPaths.clear();
                for (ProfileShareCodeManager.ImportPreviewEntry entry : entries) {
                    selectedPaths.add(entry.getRelativePath());
                }
                updateButtonStates();
                setStatus("§a已全选所有导入项", 0xFF8CFF9E);
                break;
            case 2:
                selectedPaths.clear();
                updateButtonStates();
                setStatus("§7已取消全部导入勾选", 0xFFB8C7D9);
                break;
            case 3:
                this.mc.displayGuiScreen(parentScreen.getDialogParent());
                break;
            case 4:
                pathConflictMode = pathConflictMode == ProfileShareCodeManager.PathConflictMode.REPLACE
                        ? ProfileShareCodeManager.PathConflictMode.APPEND
                        : ProfileShareCodeManager.PathConflictMode.REPLACE;
                detailScroll = 0;
                updatePathConflictButtonText();
                setStatus("§b路径冲突将按“" + pathConflictMode.getDisplayName() + "”处理",
                        0xFF8ED8FF);
                break;
            default:
                break;
        }
    }

    private void applySelectedImport() {
        try {
            ProfileShareCodeManager.ImportResult result = ProfileShareCodeManager.applyImportPreview(preview,
                    selectedPaths, pathConflictMode);
            parentScreen.handleImportApplied(result);
            this.mc.displayGuiScreen(parentScreen.getDialogParent());
        } catch (Exception e) {
            setStatus("§c导入失败: " + e.getMessage(), 0xFFFF8E8E);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0) {
            return;
        }

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (isInside(mouseX, mouseY, getListX(), getContentY(), getListWidth(), getContentHeight())) {
            if (dWheel > 0) {
                listScroll = Math.max(0, listScroll - 1);
            } else {
                listScroll = Math.min(listMaxScroll, listScroll + 1);
            }
            return;
        }

        if (isInside(mouseX, mouseY, getDetailX(), getContentY(), getDetailWidth(), getContentHeight())) {
            if (dWheel > 0) {
                detailScroll = Math.max(0, detailScroll - 2);
            } else {
                detailScroll = Math.min(detailMaxScroll, detailScroll + 2);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (ModernTooltipSupport.isInfoIconHit(this, this.buttonList, mouseX, mouseY)
                || isCustomInfoIconHit(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            if (listScrollbar != null && listScrollbar.contains(mouseX, mouseY)) { beginDrag(1, mouseY); return; }
            if (detailScrollbar != null && detailScrollbar.contains(mouseX, mouseY)) { beginDrag(2, mouseY); return; }
        }
        if (mouseButton != 0) {
            return;
        }
        handleListClick(mouseX, mouseY);
    }

    private void beginDrag(int mode, int mouseY) {
        draggingMode = mode;
        Rectangle thumb = mode == 1 ? listThumb : detailThumb;
        dragOffset = thumb == null ? 0 : mouseY - thumb.y;
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingMode != 0 && button == 0) {
            Rectangle bar = draggingMode == 1 ? listScrollbar : detailScrollbar;
            int max = draggingMode == 1 ? listMaxScroll : detailMaxScroll;
            int thumbH = draggingMode == 1 && listThumb != null ? listThumb.height : detailThumb == null ? 1 : detailThumb.height;
            int track = Math.max(1, bar.height - thumbH);
            int pos = Math.max(0, Math.min(track, mouseY - bar.y - dragOffset));
            int value = (int)Math.round(pos * (double)max / track);
            if (draggingMode == 1) listScroll = value; else detailScroll = value;
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, time);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) draggingMode = 0;
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void handleListClick(int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, getListX(), getContentY(), getListWidth(), getContentHeight())) {
            return;
        }

        int localIndex = (mouseY - getContentY()) / getRowHeight();
        int actualIndex = listScroll + localIndex;
        if (actualIndex < 0 || actualIndex >= entries.size()) {
            return;
        }

        ProfileShareCodeManager.ImportPreviewEntry entry = entries.get(actualIndex);
        selectedIndex = actualIndex;
        detailScroll = 0;

        int checkboxX = getListX() + 10;
        if (mouseX >= checkboxX && mouseX <= checkboxX + 12) {
            toggleSelection(entry.getRelativePath());
        } else {
            if (GuiScreen.isCtrlKeyDown()) {
                toggleSelection(entry.getRelativePath());
            }
        }

        updateButtonStates();
    }

    private void toggleSelection(String relativePath) {
        if (relativePath == null) {
            return;
        }
        if (selectedPaths.contains(relativePath)) {
            selectedPaths.remove(relativePath);
        } else {
            selectedPaths.add(relativePath);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        switch (keyCode) {
            case 1:
                this.mc.displayGuiScreen(parentScreen.getDialogParent());
                return;
            case 200:
                if (selectedIndex > 0) {
                    selectedIndex--;
                    ensureSelectionVisible();
                }
                return;
            case 208:
                if (selectedIndex < entries.size() - 1) {
                    selectedIndex++;
                    ensureSelectionVisible();
                }
                return;
            case 28:
            case 156:
                if (btnImport.enabled) {
                    applySelectedImport();
                }
                return;
            case 57:
                if (selectedIndex >= 0 && selectedIndex < entries.size()) {
                    toggleSelection(entries.get(selectedIndex).getRelativePath());
                    updateButtonStates();
                }
                return;
            default:
                break;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelW = getPanelWidth();
        int panelH = getPanelHeight();

        ModernUiRenderer.drawPanel(panelX, panelY, panelW, panelH, 8, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawSubtlePanel(panelX + 8, panelY + 7, panelW - 16, 34, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, "导入分享码预览 - " + targetProfileName, panelX + 15, panelY + 12,
                ModernUiRenderer.TEXT, panelW - 30);

        ModernUiRenderer.drawSubtlePanel(panelX + 10, panelY + 44, panelW - 20, 18, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, statusMessage, panelX + 14, panelY + 49, statusColor,
                panelW - 28);

        drawListPanel(mouseX, mouseY);
        drawDetailPanel(mouseX, mouseY);

        ModernUiRenderer.drawText(this.fontRenderer,
                "共 " + entries.size() + " 项 | 已勾选 " + selectedPaths.size() + " 项 | 有变化 "
                        + (preview == null ? 0 : preview.getChangedCount(pathConflictMode)) + " 项",
                panelX + 10, panelY + panelH - (hasPathConflicts() ? 66 : 42),
                ModernUiRenderer.MUTED_TEXT, panelW - 20);
        for (GuiButton button : this.buttonList) {
            ModernFeatureConfigUi.drawButton(this.fontRenderer, button, mouseX, mouseY, button.id == 0, button.id == 2);
            ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                    button.x + button.width - 16, button.y + 5, tooltipForButton(button));
        }
        ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                panelX + panelW - 25, panelY + 10,
                ModernFeatureConfigUi.tooltip("导入预览", "勾选要导入的文件；右侧查看合并或替换后的差异。"));
    }

    private void drawListPanel(int mouseX, int mouseY) {
        int x = getListX();
        int y = getTopY();
        int width = getListWidth();
        int height = getPanelHeight() - 102 - getConflictFooterHeight();

        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 6, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(this.fontRenderer, "导入项目（勾选=实际导入）", x + 8, y + 8,
                ModernUiRenderer.TEXT, width - 30);
        ModernUiRenderer.drawDivider(x + 8, y + 22, width - 16, ModernUiRenderer.BORDER_SUBTLE);

        int visibleRows = Math.max(1, getContentHeight() / getRowHeight());
        listMaxScroll = Math.max(0, entries.size() - visibleRows);
        listScroll = Math.max(0, Math.min(listScroll, listMaxScroll));

        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(this.fontRenderer, "分享码中没有可导入的配置", x + 12, y + height / 2,
                    ModernUiRenderer.MUTED_TEXT, width - 24);
            drawListInfoIcon(x, y, width, mouseX, mouseY);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            int index = listScroll + i;
            if (index >= entries.size()) {
                break;
            }

            ProfileShareCodeManager.ImportPreviewEntry entry = entries.get(index);
            boolean selected = index == selectedIndex;
            boolean hovered = isInside(mouseX, mouseY, x + 6, getContentY() + i * getRowHeight(), width - 16,
                    getRowHeight() - 2);
            boolean checked = selectedPaths.contains(entry.getRelativePath());

            int rowY = getContentY() + i * getRowHeight();
            ModernUiRenderer.drawSubtlePanel(x + 6, rowY, width - 26, getRowHeight() - 2, 4,
                    selected ? 0xFF283C48 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : checked ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);

            ModernUiRenderer.drawRoundedRect(x + 10, rowY + 5, 12, 12, 3,
                    checked ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SHELL);
            if (checked) {
                ModernUiRenderer.drawText(this.fontRenderer, "✓", x + 12, rowY + 6, ModernUiRenderer.SHELL, 10);
            }

            String strategy = entry.hasPathConflicts()
                    ? (pathConflictMode == ProfileShareCodeManager.PathConflictMode.REPLACE
                            ? "§e替换" : "§a追加")
                    : (entry.getStrategy() == ProfileShareCodeManager.ImportStrategy.MERGE ? "§a合并" : "§e替换");
            String display = this.fontRenderer.trimStringToWidth(getDisplayNameForFile(entry.getRelativePath()),
                    width - 78);
            ModernUiRenderer.drawText(this.fontRenderer, display, x + 28, rowY + 4, ModernUiRenderer.TEXT,
                    Math.max(30, width - 84));
            ModernUiRenderer.drawText(this.fontRenderer, strategy, x + width - 48, rowY + 4,
                    entry.hasPathConflicts()
                            ? (pathConflictMode == ProfileShareCodeManager.PathConflictMode.APPEND
                                    ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING)
                            : entry.getStrategy() == ProfileShareCodeManager.ImportStrategy.MERGE
                            ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING,
                    40);

            String summary = entry.hasChanges(pathConflictMode) ? getEntrySummary(entry)
                    : "无变化，导入后不会修改";
            ModernUiRenderer.drawText(this.fontRenderer, summary, x + 28, rowY + 13, ModernUiRenderer.MUTED_TEXT,
                    Math.max(30, width - 40));
        }

        if (listMaxScroll > 0) {
            int thumbHeight = Math.max(18, (int) ((visibleRows / (float) Math.max(visibleRows, entries.size()))
                    * getContentHeight()));
            int track = Math.max(1, getContentHeight() - thumbHeight);
            int thumbY = getContentY() + (int) ((listScroll / (float) Math.max(1, listMaxScroll)) * track);
            ModernFeatureConfigUi.drawScrollbar(x + width - 10, getContentY(), getContentHeight(), thumbY, thumbHeight);
            listScrollbar = new Rectangle(x + width - 16, getContentY(), 14, getContentHeight());
            listThumb = new Rectangle(x + width - 15, thumbY, 8, thumbHeight);
        } else { listScrollbar = listThumb = null;
        }
        drawListInfoIcon(x, y, width, mouseX, mouseY);
    }

    private void drawDetailPanel(int mouseX, int mouseY) {
        int x = getDetailX();
        int y = getTopY();
        int width = getDetailWidth();
        int height = getPanelHeight() - 102 - getConflictFooterHeight();

        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 6, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(this.fontRenderer, "导入策略详情", x + 8, y + 8, ModernUiRenderer.TEXT,
                width - 30);
        ModernUiRenderer.drawDivider(x + 8, y + 22, width - 16, ModernUiRenderer.BORDER_SUBTLE);

        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(this.fontRenderer, "没有可显示的详情", x + 12, y + height / 2,
                    ModernUiRenderer.MUTED_TEXT, width - 24);
            drawDetailInfoIcon(x, y, width, mouseX, mouseY);
            return;
        }

        clampSelection();
        ProfileShareCodeManager.ImportPreviewEntry entry = entries.get(selectedIndex);

        ModernUiRenderer.drawText(this.fontRenderer,
                "文件: " + getDisplayNameForFile(entry.getRelativePath()) + " (" + entry.getRelativePath() + ")",
                x + 8, y + 28, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        ModernUiRenderer.drawText(this.fontRenderer,
                "模式: " + getEntryMode(entry) + " | "
                        + (entry.hasChanges(pathConflictMode) ? "有变化" : "无变化"),
                x + 8, y + 40, entry.hasChanges(pathConflictMode)
                        ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                width - 16);

        int contentX = x + 8;
        int contentY = y + 54;
        int contentW = width - 18;
        int contentH = height - 56;

        ModernUiRenderer.drawSubtlePanel(contentX, contentY, contentW, contentH, 4, 0xFF101820,
                ModernUiRenderer.BORDER_SUBTLE);

        List<String> lines = buildDetailLines(entry);

        int visibleLines = Math.max(1, (contentH - 8) / 10);
        detailMaxScroll = Math.max(0, lines.size() - visibleLines);
        detailScroll = Math.max(0, Math.min(detailScroll, detailMaxScroll));

        for (int i = 0; i < visibleLines; i++) {
            int index = detailScroll + i;
            if (index >= lines.size()) {
                break;
            }
            String line = this.fontRenderer.trimStringToWidth(lines.get(index), contentW - 10);
            int lineColor = line.contains("§c") ? 0xFFFF8E8E
                    : line.contains("§a") ? ModernUiRenderer.SUCCESS
                            : line.contains("§b") ? 0xFF8ED8FF : ModernUiRenderer.TEXT;
            String plainLine = TextFormatting.getTextWithoutFormattingCodes(line);
            ModernUiRenderer.drawText(this.fontRenderer, plainLine, contentX + 4, contentY + 4 + i * 10,
                    lineColor, contentW - 10);
        }

        if (detailMaxScroll > 0) {
            int thumbHeight = Math.max(18,
                    (int) ((visibleLines / (float) Math.max(visibleLines, lines.size())) * contentH));
            int track = Math.max(1, contentH - thumbHeight);
            int thumbY = contentY + (int) ((detailScroll / (float) Math.max(1, detailMaxScroll)) * track);
            ModernFeatureConfigUi.drawScrollbar(contentX + contentW - 6, contentY, contentH, thumbY, thumbHeight);
            detailScrollbar = new Rectangle(contentX + contentW - 12, contentY, 14, contentH);
            detailThumb = new Rectangle(contentX + contentW - 11, thumbY, 8, thumbHeight);
        } else { detailScrollbar = detailThumb = null;
        }
        drawDetailInfoIcon(x, y, width, mouseX, mouseY);
    }

    private void ensureSelectionVisible() {
        clampSelection();
        int visibleRows = Math.max(1, getContentHeight() / getRowHeight());
        if (selectedIndex < listScroll) {
            listScroll = selectedIndex;
        } else if (selectedIndex >= listScroll + visibleRows) {
            listScroll = selectedIndex - visibleRows + 1;
        }
        listScroll = Math.max(0, Math.min(listScroll, listMaxScroll));
        detailScroll = 0;
    }

    private void clampSelection() {
        if (entries.isEmpty()) {
            selectedIndex = -1;
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message == null ? "" : message;
        this.statusColor = color;
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private List<String> buildDetailLines(ProfileShareCodeManager.ImportPreviewEntry entry) {
        List<String> lines = new ArrayList<>();
        if (entry == null) {
            lines.add("§7没有可显示的详情");
            return lines;
        }

        lines.add("§f" + getEntrySummary(entry));
        lines.add("");
        lines.add("§7策略: " + getEntryMode(entry)
                + " | 状态: " + (entry.hasChanges(pathConflictMode) ? "§a有变化" : "§7无变化"));
        lines.add("§7本地行数: " + splitContentLines(entry.getExistingContent()).size()
                + " | 分享码行数: " + splitContentLines(entry.getImportedContent()).size()
                + " | 导入后行数: " + splitContentLines(entry.getFinalContent(pathConflictMode)).size());

        for (String detail : entry.getDetailLines()) {
            lines.add("§7" + detail);
        }

        lines.add("");
        if (!normalizeContent(entry.getImportedContent())
                .equals(normalizeContent(entry.getFinalContent(pathConflictMode)))) {
            lines.add("§d提示: 当前为智能合并结果，右侧预览的是“最终导入后”的内容，而不是分享码原始内容");
            lines.add("");
        }

        lines.add("§b差异预览（本地 -> 导入后）");
        lines.add("§8红色删除线 = 将被删除；绿色 = 将新增；灰色 = 保留");
        lines.addAll(buildDiffLines(entry.getExistingContent(), entry.getFinalContent(pathConflictMode)));

        return lines;
    }

    private List<String> buildDiffLines(String beforeContent, String afterContent) {
        List<String> beforeLines = splitContentLines(beforeContent);
        List<String> afterLines = splitContentLines(afterContent);

        long complexity = (long) beforeLines.size() * (long) afterLines.size();
        if (complexity > 120000L) {
            return buildSimpleDiffLines(beforeLines, afterLines);
        }

        int n = beforeLines.size();
        int m = afterLines.size();
        int[][] dp = new int[n + 1][m + 1];

        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (beforeLines.get(i).equals(afterLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            String before = beforeLines.get(i);
            String after = afterLines.get(j);
            if (before.equals(after)) {
                result.add("§7  " + formatDiffText(before));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add("§c§m- " + formatDiffText(before));
                i++;
            } else {
                result.add("§a+ " + formatDiffText(after));
                j++;
            }
        }

        while (i < n) {
            result.add("§c§m- " + formatDiffText(beforeLines.get(i++)));
        }
        while (j < m) {
            result.add("§a+ " + formatDiffText(afterLines.get(j++)));
        }

        if (result.isEmpty()) {
            result.add("§7（无可视差异）");
        }
        return result;
    }

    private List<String> buildSimpleDiffLines(List<String> beforeLines, List<String> afterLines) {
        List<String> result = new ArrayList<>();
        int max = Math.max(beforeLines.size(), afterLines.size());
        for (int i = 0; i < max; i++) {
            String before = i < beforeLines.size() ? beforeLines.get(i) : null;
            String after = i < afterLines.size() ? afterLines.get(i) : null;
            if (before != null && after != null && before.equals(after)) {
                result.add("§7  " + formatDiffText(before));
            } else {
                if (before != null) {
                    result.add("§c§m- " + formatDiffText(before));
                }
                if (after != null) {
                    result.add("§a+ " + formatDiffText(after));
                }
            }
        }
        if (result.isEmpty()) {
            result.add("§7（无可视差异）");
        }
        return result;
    }

    private List<String> splitContentLines(String content) {
        List<String> result = new ArrayList<>();
        String normalized = normalizeContent(content);
        String[] lines = normalized.split("\n", -1);
        Collections.addAll(result, lines);
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String formatDiffText(String text) {
        return text == null || text.isEmpty() ? "§8<空行>" : text;
    }

    private String getDisplayNameForFile(String path) {
        return ProfileShareCodeManager.getDisplayNameForPath(path);
    }

    private int getPanelWidth() {
        return Math.max(1, Math.min(980, this.width - 16));
    }

    private int getPanelHeight() {
        return Math.min(620, this.height - 16);
    }

    private int getPanelX() {
        return (this.width - getPanelWidth()) / 2;
    }

    private int getPanelY() {
        return (this.height - getPanelHeight()) / 2;
    }

    private int getTopY() {
        return getPanelY() + 68;
    }

    private int getContentY() {
        return getTopY() + 24;
    }

    private int getContentHeight() {
        return getPanelHeight() - 126 - getConflictFooterHeight();
    }

    private int getListX() {
        return getPanelX() + 10;
    }

    private int getListWidth() {
        return Math.min(340, Math.max(80, (getPanelWidth() - 30) / 3));
    }

    private int getDetailX() {
        return getListX() + getListWidth() + 10;
    }

    private int getDetailWidth() {
        return Math.max(1, getPanelWidth() - 30 - getListWidth());
    }

    private int getRowHeight() {
        return 24;
    }

    private String tooltipForButton(GuiButton button) {
        if (button == null) {
            return "";
        }
        switch (button.id) {
            case 0:
                return ModernFeatureConfigUi.tooltip("确认导入", "只导入当前勾选的文件，并应用右侧显示的策略。");
            case 1:
                return ModernFeatureConfigUi.tooltip("全选", "勾选分享码中的全部配置文件。");
            case 2:
                return ModernFeatureConfigUi.tooltip("清空选择", "取消所有文件的导入勾选。");
            case 4:
                return ModernFeatureConfigUi.tooltip("路径冲突处理",
                        "同分组且同序列名才是冲突。默认替换，也可切换为追加步骤。");
            default:
                return ModernFeatureConfigUi.tooltip("返回", "放弃本次导入并返回配置档案。");
        }
    }

    private boolean hasPathConflicts() {
        return preview != null && preview.getPathConflictCount() > 0;
    }

    private int getConflictFooterHeight() {
        return hasPathConflicts() ? 24 : 0;
    }

    private void updatePathConflictButtonText() {
        if (btnPathConflictMode != null) {
            btnPathConflictMode.displayString = "路径冲突（同分组 + 同序列名）：§b"
                    + pathConflictMode.getDisplayName() + "§7  [点击切换]";
        }
    }

    private String getEntryMode(ProfileShareCodeManager.ImportPreviewEntry entry) {
        if (entry != null && entry.hasPathConflicts()) {
            return "路径冲突" + pathConflictMode.getDisplayName();
        }
        return entry == null ? "" : entry.getStrategy().getDisplayName();
    }

    private String getEntrySummary(ProfileShareCodeManager.ImportPreviewEntry entry) {
        if (entry != null && entry.hasPathConflicts()
                && pathConflictMode == ProfileShareCodeManager.PathConflictMode.APPEND) {
            return "合并路径：同分组同名冲突追加 " + entry.getPathConflictCount();
        }
        return entry == null ? "" : entry.getSummary();
    }

    private boolean isCustomInfoIconHit(int mouseX, int mouseY) {
        int panelX = getPanelX();
        int panelY = getPanelY();
        int panelW = getPanelWidth();
        if (ModernFeatureConfigUi.contains(panelX + panelW - 25, panelY + 10, 11, 11, mouseX, mouseY)) {
            return true;
        }
        int listX = getListX();
        int listY = getTopY();
        int listW = getListWidth();
        if (ModernFeatureConfigUi.contains(getListInfoIconX(listX, listW), listY + 6, 11, 11, mouseX, mouseY)) {
            return true;
        }
        int detailX = getDetailX();
        int detailW = getDetailWidth();
        if (ModernFeatureConfigUi.contains(getDetailInfoIconX(detailX, detailW), listY + 6, 11, 11, mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    private void drawListInfoIcon(int x, int y, int width, int mouseX, int mouseY) {
        ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                getListInfoIconX(x, width), y + 6,
                ModernFeatureConfigUi.tooltip("导入项目", "点击行查看详情；点击左侧复选框切换是否导入。"));
    }

    private void drawDetailInfoIcon(int x, int y, int width, int mouseX, int mouseY) {
        ModernFeatureConfigUi.drawInfoIcon(this.fontRenderer, this.width, this.height, mouseX, mouseY,
                getDetailInfoIconX(x, width), y + 6,
                ModernFeatureConfigUi.tooltip("导入策略详情", "显示当前文件的合并/替换策略和逐行差异。"));
    }

    private int getListInfoIconX(int x, int width) {
        return x + Math.max(2, Math.min(Math.max(2, width - 18), 12 + this.fontRenderer.getStringWidth("导入项目")));
    }

    private int getDetailInfoIconX(int x, int width) {
        return x + Math.max(2,
                Math.min(Math.max(2, width - 18), 12 + this.fontRenderer.getStringWidth("导入策略详情")));
    }
}
