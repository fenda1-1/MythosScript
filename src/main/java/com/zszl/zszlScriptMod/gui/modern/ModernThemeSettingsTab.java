package com.zszl.zszlScriptMod.gui.modern;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.config.ChatOptimizationConfig.ImageQuality;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager;
import com.zszl.zszlScriptMod.gui.theme.ThemeJsonCodec;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager.ThemeProfile;
import com.zszl.zszlScriptMod.utils.TextureManagerHelper;
import com.zszl.zszlScriptMod.gui.modern.core.ModernConfirmationState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.gui.GuiScreen;

/** Native modern editor for the persisted GUI theme profiles. */
public final class ModernThemeSettingsTab implements ModernSettingsTab {

    private static final int ASSET_COUNT = 1;
    private static final int CHANNEL_COUNT = 3;
    private static final int COLOR_COUNT = 23;
    private static final int OPACITY_COUNT = 4;
    private static final int STYLE_COUNT = 3;
    private static final int PROFILE_ROW_HEIGHT = ModernTreeGuide.ITEM_HEIGHT;
    private static final int PROFILE_ROW_GAP = ModernTreeGuide.GAP;
    private static final double DEFAULT_PROFILE_RATIO = 0.25D;

    private static final String[] ASSET_LABELS = { "面板背景" };
    private static final String[] COLOR_LABELS = {
            "面板边框", "面板顶部", "面板底部", "标题左侧", "标题右侧",
            "标题文字", "正文文字", "次要文字",
            "成功状态", "警告状态", "危险状态", "禁用状态", "选中状态",
            "按钮常态", "按钮悬停", "按钮按下", "按钮边框", "按钮悬停边框",
            "输入框背景", "输入框边框", "输入框悬停边框", "输入框光标", "硬阴影"
    };
    private static final String[] COLOR_GROUP_LABELS = { "面板与标题", "文字", "状态", "按钮", "输入框", "阴影" };
    private static final int[] COLOR_GROUP_ENDS = { 5, 8, 13, 18, 22, 23 };
    private static final String[] CHANNEL_LABELS = { "R", "G", "B" };
    private static final String[] OPACITY_LABELS = { "面板透明度", "按钮透明度", "输入框透明度", "文字透明度" };
    private static final String[] STYLE_LABELS = { "圆角", "边框粗细", "硬阴影偏移" };
    private static final int[] STYLE_MINIMUMS = { 0, 1, 0 };
    private static final int[] STYLE_MAXIMUMS = { 12, 4, 8 };

    private static final class ProfileHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private ProfileHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class SliderHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;
        private final int minimum;
        private final int maximum;

        private SliderHit(int index, ModernMainLayout.Rect bounds, int minimum, int maximum) {
            this.index = index;
            this.bounds = bounds;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static final class GroupHit {
        private final boolean builtIn;
        private final ModernMainLayout.Rect bounds;

        private GroupHit(boolean builtIn, ModernMainLayout.Rect bounds) {
            this.builtIn = builtIn;
            this.bounds = bounds;
        }
    }

    private final List<ProfileHit> profileHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final List<SliderHit> sliderHits = new ArrayList<>();
    private final ModernTextField[] imageFields = new ModernTextField[ASSET_COUNT];
    private final ModernTextField[] scaleFields = new ModernTextField[ASSET_COUNT];
    private final ModernTextField[][] cropFields = new ModernTextField[ASSET_COUNT][2];
    private final ModernMainLayout.Rect[] imageBounds = new ModernMainLayout.Rect[ASSET_COUNT];
    private final ModernMainLayout.Rect[] imageToggleBounds = new ModernMainLayout.Rect[ASSET_COUNT];
    private final ModernMainLayout.Rect[] qualityBounds = new ModernMainLayout.Rect[ASSET_COUNT];
    private final ModernMainLayout.Rect[] scaleBounds = new ModernMainLayout.Rect[ASSET_COUNT];
    private final ModernMainLayout.Rect[][] cropBounds = new ModernMainLayout.Rect[ASSET_COUNT][2];

    private ModernTextField nameField;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect profileBounds;
    private ModernMainLayout.Rect profileClipBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect editorClipBounds;
    private ModernMainLayout.Rect nameBounds;
    private ModernMainLayout.Rect applyBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect resetBounds;
    private boolean themeToolsExpanded;
    private ModernMainLayout.Rect themeToolsBounds;
    private ModernMainLayout.Rect themeToolsPanelBounds;
    private ModernMainLayout.Rect importJsonBounds;
    private ModernMainLayout.Rect exportJsonBounds;
    private ModernMainLayout.Rect aiPromptBounds;
    private ModernMainLayout.Rect randomBounds;
    private ModernMainLayout.Rect newBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect dividerBounds;
    private final ModernHoverScrollbar profileScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar editorScrollbar = new ModernHoverScrollbar();

    private List<ThemeProfile> profiles = Collections.emptyList();
    private ThemeProfile editingProfile;
    private int selectedIndex;
    private boolean randomDraft;
    private boolean draftDirty;
    private final ModernConfirmationState destructiveConfirmation = new ModernConfirmationState();
    private boolean initialized;
    private boolean layoutPreferencesLoaded;
    private boolean draggingDivider;
    private boolean builtInCollapsed;
    private boolean customCollapsed;
    private double profileRatio = DEFAULT_PROFILE_RATIO;
    private double dividerDragStartRatio = DEFAULT_PROFILE_RATIO;
    private long lastDividerClickAt;
    private int lastDividerClickX;
    private int profileScrollOffset;
    private int profileMaxScrollOffset;
    private int editorScrollOffset;
    private int editorMaxScrollOffset;
    private int draggingSlider = -1;
    private int lastMouseX;
    private int lastMouseY;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    /** The field focused before the render-only visibility reset. */
    private ModernTextField focusedFieldBeforeLayoutReset;

    public static ModernSettingsTab create() {
        return new ModernThemeSettingsTab();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        ThemeConfigManager.ensureLoaded();
        ThemeConfigManager.ensureDefaultPresets();
        if (!layoutPreferencesLoaded) {
            profileRatio = MainUiLayoutManager.getModernSplitRatio("theme.profile_list", DEFAULT_PROFILE_RATIO);
            layoutPreferencesLoaded = true;
        }
        profiles = ThemeConfigManager.getProfiles();
        selectedIndex = clamp(ThemeConfigManager.getActiveIndex(), 0, Math.max(0, profiles.size() - 1));
        createFields(fontRenderer);
        loadProfile(selectedIndex);
        initialized = true;
    }

    private void createFields(FontRenderer fontRenderer) {
        nameField = createField(fontRenderer, 96);
        for (int i = 0; i < ASSET_COUNT; i++) {
            imageFields[i] = createField(fontRenderer, 1024);
            scaleFields[i] = createField(fontRenderer, 4);
            cropFields[i][0] = createField(fontRenderer, 5);
            cropFields[i][1] = createField(fontRenderer, 5);
        }
    }

    private ModernTextField createField(FontRenderer fontRenderer, int maxLength) {
        ModernTextField field = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    @Override
    public void updateScreen() {
        if (!initialized) {
            return;
        }
        if (nameField != null && nameField.getVisible()) {
            nameField.updateCursorCounter();
        }
        for (int i = 0; i < ASSET_COUNT; i++) {
            updateField(imageFields[i]);
            updateField(scaleFields[i]);
            updateField(cropFields[i][0]);
            updateField(cropFields[i][1]);
        }
    }

    private void updateField(ModernTextField field) {
        if (field != null && field.getVisible()) {
            field.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = bounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredTooltip = "";
        profiles = ThemeConfigManager.getProfiles();
        normalizeSelection();

        panelBounds = safePanel(bounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer);

        int bodyY = panelBounds.y + 49;
        int bodyBottom = Math.max(bodyY + 1, panelBounds.bottom() - 10);
        int gap = 8;
        int bodyHeight = Math.max(1, bodyBottom - bodyY);
        if (panelBounds.width < 620) {
            dividerBounds = null;
            int listHeight = Math.min(148, Math.max(104, bodyHeight / 3));
            listHeight = Math.min(listHeight, Math.max(1, bodyHeight - 1));
            profileBounds = new ModernMainLayout.Rect(panelBounds.x + 10, bodyY,
                    Math.max(1, panelBounds.width - 20), listHeight);
            editorBounds = new ModernMainLayout.Rect(panelBounds.x + 10, profileBounds.bottom() + gap,
                    Math.max(1, panelBounds.width - 20), Math.max(1, bodyBottom - profileBounds.bottom() - gap));
        } else {
            int splitTotal = Math.max(2, panelBounds.width - 20 - gap);
            ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, profileRatio, 180, 320, 140, 180);
            profileRatio = split.ratio;
            profileBounds = new ModernMainLayout.Rect(panelBounds.x + 10, bodyY, split.firstWidth, bodyHeight);
            editorBounds = new ModernMainLayout.Rect(profileBounds.right() + gap, bodyY,
                    split.secondWidth, profileBounds.height);
            dividerBounds = ModernSplitPane.verticalDividerBounds(profileBounds.x, profileBounds.width, gap,
                    bodyY, bodyHeight);
        }

        drawProfiles(fontRenderer, mouseX, mouseY);
        drawEditor(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
        drawThemeTools(fontRenderer, mouseX, mouseY);
        hoveredTooltip = tooltipAt(mouseX, mouseY);
    }

    private void normalizeSelection() {
        if (profiles.isEmpty()) {
            selectedIndex = 0;
            editingProfile = ThemeProfile.fromCurrent("默认主题");
            randomDraft = false;
            return;
        }
        if (!randomDraft && (selectedIndex < 0 || selectedIndex >= profiles.size())) {
            selectedIndex = clamp(ThemeConfigManager.getActiveIndex(), 0, profiles.size() - 1);
            loadProfile(selectedIndex);
        }
        if (editingProfile == null) {
            loadProfile(selectedIndex);
        }
    }

    private void drawHeader(FontRenderer fontRenderer) {
        int x = panelBounds.x + 15;
        int toolsWidth = Math.min(108, Math.max(70, panelBounds.width / 4));
        themeToolsBounds = new ModernMainLayout.Rect(panelBounds.right() - toolsWidth - 14,
                panelBounds.y + 10, toolsWidth, 24);
        int rightWidth = panelBounds.width >= 460 ? Math.min(180, panelBounds.width / 4) : 0;
        int rightX = themeToolsBounds.x - rightWidth - 8;
        int titleWidth = Math.max(30, rightX - x - 12);
        ModernUiRenderer.drawText(fontRenderer, "界面与主题", x, panelBounds.y + 10, ModernUiRenderer.TEXT,
                titleWidth);
        boolean hasStatus = statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil;
        ModernUiRenderer.drawText(fontRenderer, rightWidth == 0 && hasStatus ? statusMessage
                : "选择方案并实时预览视觉效果", x, panelBounds.y + 26,
                ModernUiRenderer.SUBTLE_TEXT, titleWidth);

        boolean statusVisible = statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil;
        String summary = statusVisible ? statusMessage : editingProfile == null ? "" : displayName(editingProfile);
        if (rightWidth > 0) {
            ModernMainLayout.Rect summaryBounds = new ModernMainLayout.Rect(rightX, panelBounds.y + 12, rightWidth, 20);
            ModernUiRenderer.drawSubtlePanel(summaryBounds.x, summaryBounds.y, summaryBounds.width, summaryBounds.height, 5,
                    statusVisible ? 0xFF243B45 : ModernUiRenderer.SURFACE,
                    statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, summary, summaryBounds.x + 7,
                    summaryBounds.y + (summaryBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                    statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT, summaryBounds.width - 14);
        }
        ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + 41, Math.max(1, panelBounds.width - 24),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawThemeTools(FontRenderer fontRenderer, int mouseX, int mouseY) {
        drawActionButton(fontRenderer, themeToolsBounds, themeToolsExpanded ? "主题工具 −" : "主题工具 +",
                themeToolsExpanded, true, mouseX, mouseY);
        if (!themeToolsExpanded) {
            themeToolsPanelBounds = null;
            importJsonBounds = exportJsonBounds = aiPromptBounds = null;
            return;
        }
        int width = Math.min(222, Math.max(1, panelBounds.width - 28));
        themeToolsPanelBounds = new ModernMainLayout.Rect(themeToolsBounds.right() - width,
                themeToolsBounds.bottom() + 5, width, 98);
        ModernUiRenderer.drawPanel(themeToolsPanelBounds.x, themeToolsPanelBounds.y, width, 98, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int x = themeToolsPanelBounds.x + 8;
        int y = themeToolsPanelBounds.y + 8;
        int buttonWidth = Math.max(1, width - 16);
        importJsonBounds = new ModernMainLayout.Rect(x, y, buttonWidth, 22);
        exportJsonBounds = new ModernMainLayout.Rect(x, y + 30, buttonWidth, 22);
        aiPromptBounds = new ModernMainLayout.Rect(x, y + 60, buttonWidth, 22);
        drawActionButton(fontRenderer, importJsonBounds, "导入主题 JSON（剪贴板）", false, true, mouseX, mouseY);
        drawActionButton(fontRenderer, exportJsonBounds, "导出主题 JSON（复制）", false, editingProfile != null, mouseX, mouseY);
        drawActionButton(fontRenderer, aiPromptBounds, "AI 生成提示词（复制）", false, true, mouseX, mouseY);
    }

    private void drawProfiles(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(profileBounds.x, profileBounds.y, profileBounds.width, profileBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = profileBounds.x + 10;
        ModernUiRenderer.drawText(fontRenderer, "主题方案", x, profileBounds.y + 9, ModernUiRenderer.TEXT,
                Math.max(28, profileBounds.width - 20));
        ModernUiRenderer.drawText(fontRenderer, profiles.size() + " 套", profileBounds.right() - 40,
                profileBounds.y + 9, ModernUiRenderer.MUTED_TEXT, 30);
        ModernUiRenderer.drawDivider(x, profileBounds.y + 27, Math.max(1, profileBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);

        int actionHeight = 22;
        int actionY = profileBounds.bottom() - actionHeight - 9;
        profileClipBounds = new ModernMainLayout.Rect(profileBounds.x + 7, profileBounds.y + 33,
                Math.max(1, profileBounds.width - 14), Math.max(1, actionY - profileBounds.y - 39));
        int totalHeight = profileGroupHeight(true) + profileGroupHeight(false);
        profileMaxScrollOffset = Math.max(0, totalHeight - profileClipBounds.height);
        profileScrollOffset = clamp(profileScrollOffset, 0, profileMaxScrollOffset);
        profileHits.clear();
        groupHits.clear();

        ModernUiRenderer.beginClip(profileClipBounds);
        int y = profileClipBounds.y - profileScrollOffset;
        y = drawProfileGroup(fontRenderer, true, y, mouseX, mouseY);
        drawProfileGroup(fontRenderer, false, y, mouseX, mouseY);
        ModernUiRenderer.endClip();
        drawScrollbar(profileScrollbar, profileClipBounds, totalHeight, profileScrollOffset, profileMaxScrollOffset,
                mouseX, mouseY, value -> profileScrollOffset = value);

        int buttonGap = 4;
        int buttonWidth = Math.max(1, (profileBounds.width - 20 - buttonGap * 2) / 3);
        randomBounds = new ModernMainLayout.Rect(x, actionY, buttonWidth, actionHeight);
        newBounds = new ModernMainLayout.Rect(randomBounds.right() + buttonGap, actionY, buttonWidth, actionHeight);
        deleteBounds = new ModernMainLayout.Rect(newBounds.right() + buttonGap, actionY,
                Math.max(1, profileBounds.right() - 10 - newBounds.right() - buttonGap), actionHeight);
        drawActionButton(fontRenderer, randomBounds, "随机", false, true, mouseX, mouseY);
        drawActionButton(fontRenderer, newBounds, "复制", false, editingProfile != null, mouseX, mouseY);
        drawActionButton(fontRenderer, deleteBounds,
                destructiveConfirmation.isPending(deleteConfirmationKey()) ? "再次确认" : "删除", false, canDelete(),
                mouseX, mouseY);
    }

    private int profileGroupHeight(boolean builtIn) {
        int height = ModernTreeGuide.GROUP_HEIGHT + ModernTreeGuide.GAP;
        if (!(builtIn ? builtInCollapsed : customCollapsed)) {
            height += countProfiles(builtIn) * (PROFILE_ROW_HEIGHT + PROFILE_ROW_GAP);
        }
        return height;
    }

    private int countProfiles(boolean builtIn) {
        int count = 0;
        for (ThemeProfile profile : profiles) {
            if (profile != null && ThemeConfigManager.isBuiltInProfile(profile) == builtIn) {
                count++;
            }
        }
        return count;
    }

    private int drawProfileGroup(FontRenderer fontRenderer, boolean builtIn, int y, int mouseX, int mouseY) {
        int count = countProfiles(builtIn);
        boolean collapsed = builtIn ? builtInCollapsed : customCollapsed;
        ModernMainLayout.Rect group = ModernTreeGuide.groupRow(profileClipBounds, y);
        boolean hovered = profileClipBounds.contains(mouseX, mouseY) && group.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(group.x, group.y, group.width, group.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(group.x + 8, group.y + 7, collapsed, ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawText(fontRenderer, builtIn ? "内置主题" : "自定义主题", group.x + 20, group.y + 7,
                ModernUiRenderer.TEXT, Math.max(1, group.width - 54));
        ModernUiRenderer.drawText(fontRenderer, String.valueOf(count), group.right() - 23, group.y + 7,
                ModernUiRenderer.MUTED_TEXT, 18);
        if (intersects(group, profileClipBounds)) {
            groupHits.add(new GroupHit(builtIn, group));
        }
        y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
        if (collapsed) {
            return y;
        }
        for (int i = 0; i < profiles.size(); i++) {
            ThemeProfile profile = profiles.get(i);
            if (profile == null || ThemeConfigManager.isBuiltInProfile(profile) != builtIn) {
                continue;
            }
            ModernMainLayout.Rect row = ModernTreeGuide.itemRow(profileClipBounds, y);
            ModernTreeGuide.drawChild(profileClipBounds.x, 0, group, row);
            boolean selected = !randomDraft && i == selectedIndex;
            boolean rowHovered = profileClipBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                    selected ? ModernUiRenderer.SURFACE_PRESSED
                            : rowHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT
                            : rowHovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawStatusDot(row.x + 9, row.y + 8,
                    selected ? ModernUiRenderer.ACCENT
                            : builtIn ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.drawText(fontRenderer, displayName(profile), row.x + 21, row.y + 5,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(24, row.width - 30));
            ModernUiRenderer.drawText(fontRenderer, builtIn ? "内置 · 可编辑" : "自定义主题", row.x + 21,
                    row.y + 18, ModernUiRenderer.MUTED_TEXT, Math.max(24, row.width - 30));
            if (intersects(row, profileClipBounds)) {
                profileHits.add(new ProfileHit(i, row));
            }
            y = ModernTreeGuide.nextY(y, PROFILE_ROW_HEIGHT);
        }
        return y;
    }

    private void drawEditor(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = editorBounds.x + 12;
        int width = Math.max(20, editorBounds.width - 24);
        String meta = editingProfile != null && ThemeConfigManager.isBuiltInProfile(editingProfile)
                ? "内置主题 · 可编辑、可保存、可恢复" : randomDraft ? "随机预览草稿 · 保存后加入方案" : "自定义主题 · 可编辑";
        ModernUiRenderer.drawText(fontRenderer, "主题编辑", x, editorBounds.y + 9, ModernUiRenderer.TEXT,
                Math.max(30, width - 4));
        ModernUiRenderer.drawText(fontRenderer, meta, x, editorBounds.y + 23, ModernUiRenderer.MUTED_TEXT,
                Math.max(30, width - 4));

        int footerY = editorBounds.bottom() - 31;
        editorClipBounds = new ModernMainLayout.Rect(editorBounds.x + 7, editorBounds.y + 34,
                Math.max(1, editorBounds.width - 14), Math.max(1, footerY - editorBounds.y - 39));
        int contentWidth = Math.max(20, editorClipBounds.width - 24);
        int contentHeight = calculateEditorContentHeight(contentWidth);
        editorMaxScrollOffset = Math.max(0, contentHeight - editorClipBounds.height);
        editorScrollOffset = clamp(editorScrollOffset, 0, editorMaxScrollOffset);

        int footerGap = Math.min(6, Math.max(1, width / 12));
        int saveWidth = Math.max((width - footerGap * 2) / 3, SaveShortcutHint.preferredWidth(fontRenderer, "保存", 16));
        int footerButtonWidth = Math.max(1, (width - saveWidth - footerGap * 2) / 2);
        applyBounds = new ModernMainLayout.Rect(editorBounds.x + 12, footerY, footerButtonWidth, 22);
        saveBounds = new ModernMainLayout.Rect(applyBounds.right() + footerGap, footerY, saveWidth, 22);
        resetBounds = new ModernMainLayout.Rect(saveBounds.right() + footerGap, footerY,
                Math.max(1, editorBounds.right() - 12 - saveBounds.right() - footerGap), 22);
        drawActionButton(fontRenderer, applyBounds, "应用预览", true, isEditable(), mouseX, mouseY);
        drawActionButton(fontRenderer, saveBounds, "保存", false, isEditable(), mouseX, mouseY);
        drawActionButton(fontRenderer, resetBounds, "恢复默认", false,
                editingProfile != null && ThemeConfigManager.isBuiltInProfile(editingProfile), mouseX, mouseY);

        focusedFieldBeforeLayoutReset = findFocusedField();
        hideFields();
        sliderHits.clear();
        ModernUiRenderer.beginClip(editorClipBounds);
        int base = editorClipBounds.y - editorScrollOffset;
        int y = base + 8;
        ModernUiRenderer.drawText(fontRenderer, "方案信息", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        ModernUiRenderer.drawText(fontRenderer, "方案名称", editorClipBounds.x + 5, y, ModernUiRenderer.SUBTLE_TEXT,
                contentWidth);
        y += 12;
        nameBounds = new ModernMainLayout.Rect(editorClipBounds.x + 5, y, contentWidth, 20);
        drawTextField(fontRenderer, nameField, nameBounds, "例如：清爽蓝", mouseX, mouseY);
        y += 28;

        ModernUiRenderer.drawText(fontRenderer, "背景素材", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        for (int i = 0; i < ASSET_COUNT; i++) {
            ModernUiRenderer.drawText(fontRenderer, ASSET_LABELS[i], editorClipBounds.x + 5, y,
                    ModernUiRenderer.SUBTLE_TEXT, contentWidth);
            y += 12;
            int toggleWidth = 38;
            int qualityWidth = contentWidth >= 240 ? Math.min(84, Math.max(58, contentWidth / 4)) : 0;
            int controlGap = 6;
            int pathWidth = Math.max(20, contentWidth - toggleWidth - controlGap
                    - (qualityWidth == 0 ? 0 : qualityWidth + controlGap));
            imageBounds[i] = new ModernMainLayout.Rect(editorClipBounds.x + 5, y, pathWidth, 20);
            imageToggleBounds[i] = new ModernMainLayout.Rect(imageBounds[i].right() + controlGap, y + 2,
                    toggleWidth, 16);
            qualityBounds[i] = qualityWidth == 0 ? null
                    : new ModernMainLayout.Rect(imageToggleBounds[i].right() + controlGap, y, qualityWidth, 20);
            drawTextField(fontRenderer, imageFields[i], imageBounds[i], "图片路径", mouseX, mouseY);
            ModernUiRenderer.drawToggle(imageToggleBounds[i].x, imageToggleBounds[i].y,
                    imageToggleBounds[i].width, imageToggleBounds[i].height, assetEnabled(i),
                    imageToggleBounds[i].contains(mouseX, mouseY));
            if (qualityBounds[i] != null) {
                drawActionButton(fontRenderer, qualityBounds[i], qualityText(assetQuality(i)), false, isEditable(),
                        mouseX, mouseY);
            }
            y += 32;
        }
        y += 6;

        ModernUiRenderer.drawText(fontRenderer, "图像调整", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        for (int i = 0; i < ASSET_COUNT; i++) {
            ModernUiRenderer.drawText(fontRenderer, ASSET_LABELS[i] + "  缩放 / 裁剪 X / Y", editorClipBounds.x + 5, y,
                    ModernUiRenderer.SUBTLE_TEXT, contentWidth);
            y += 12;
            int fieldGap = 5;
            int fieldWidth = Math.max(20, (contentWidth - fieldGap * 2) / 3);
            int fieldX = editorClipBounds.x + 5;
            scaleBounds[i] = new ModernMainLayout.Rect(fieldX, y, fieldWidth, 20);
            cropBounds[i][0] = new ModernMainLayout.Rect(scaleBounds[i].right() + fieldGap, y, fieldWidth, 20);
            cropBounds[i][1] = new ModernMainLayout.Rect(cropBounds[i][0].right() + fieldGap, y, fieldWidth, 20);
            drawTextField(fontRenderer, scaleFields[i], scaleBounds[i], "缩放", mouseX, mouseY);
            drawTextField(fontRenderer, cropFields[i][0], cropBounds[i][0], "X", mouseX, mouseY);
            drawTextField(fontRenderer, cropFields[i][1], cropBounds[i][1], "Y", mouseX, mouseY);
            y += 32;
        }
        y += 6;

        ModernUiRenderer.drawText(fontRenderer, "颜色参数", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        int colorIndex = 0;
        for (int groupIndex = 0; groupIndex < COLOR_GROUP_LABELS.length; groupIndex++) {
            ModernUiRenderer.drawText(fontRenderer, COLOR_GROUP_LABELS[groupIndex], editorClipBounds.x + 5, y,
                    ModernUiRenderer.MUTED_TEXT, contentWidth);
            y += 16;
            while (colorIndex < COLOR_GROUP_ENDS[groupIndex]) {
                drawColorRow(fontRenderer, editorClipBounds.x + 5, y, contentWidth, colorIndex++);
                y += 28;
            }
        }
        y += 6;

        ModernUiRenderer.drawText(fontRenderer, "透明度", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        for (int i = 0; i < OPACITY_COUNT; i++) {
            drawOpacityRow(fontRenderer, editorClipBounds.x + 5, y, contentWidth, i);
            y += 25;
        }
        y += 7;

        ModernUiRenderer.drawText(fontRenderer, "形状与阴影", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        for (int i = 0; i < STYLE_COUNT; i++) {
            drawStyleRow(fontRenderer, editorClipBounds.x + 5, y, contentWidth, i);
            y += 25;
        }
        y += 7;

        ModernUiRenderer.drawText(fontRenderer, "实时预览", editorClipBounds.x + 5, y, ModernUiRenderer.TEXT,
                contentWidth);
        y += 18;
        drawPreview(fontRenderer, editorClipBounds.x + 5, y, contentWidth, 100);
        ModernUiRenderer.endClip();
        drawScrollbar(editorScrollbar, editorClipBounds, contentHeight, editorScrollOffset, editorMaxScrollOffset,
                mouseX, mouseY, value -> editorScrollOffset = value);
        focusedFieldBeforeLayoutReset = null;
    }

    private int calculateEditorContentHeight(int contentWidth) {
        int height = 8;
        height += 18 + 12 + 28;
        height += 18 + ASSET_COUNT * (12 + 32) + 6;
        height += 18 + ASSET_COUNT * (12 + 32) + 6;
        height += 18 + COLOR_GROUP_LABELS.length * 16 + COLOR_COUNT * 28 + 6;
        height += 18 + OPACITY_COUNT * 25 + 7;
        height += 18 + STYLE_COUNT * 25 + 7;
        height += 18 + 100 + 8;
        return height;
    }

    private void drawColorRow(FontRenderer fontRenderer, int x, int y, int width, int colorIndex) {
        int color = getColor(colorIndex);
        if (width < 120) {
            ModernUiRenderer.drawText(fontRenderer, COLOR_LABELS[colorIndex], x, y + 5, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(20, width));
            ModernUiRenderer.drawRoundedRect(x + Math.max(0, width - 18), y + 5, 18, 18, 4, color);
            return;
        }
        int labelWidth = width >= 220 ? 78 : width >= 170 ? 62 : 42;
        int swatchSize = width >= 170 ? 18 : 0;
        ModernUiRenderer.drawText(fontRenderer, COLOR_LABELS[colorIndex], x, y + 5, ModernUiRenderer.SUBTLE_TEXT,
                labelWidth);
        if (swatchSize > 0) {
            ModernUiRenderer.drawRoundedRect(x + labelWidth + 4, y + 5, swatchSize, swatchSize, 4, color);
        }
        int sliderX = x + labelWidth + 4 + swatchSize + (swatchSize > 0 ? 6 : 0);
        int gap = 5;
        int sliderWidth = Math.max(12, (width - (sliderX - x) - gap * 2) / CHANNEL_COUNT);
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            int value = channelValue(color, channel);
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(sliderX + channel * (sliderWidth + gap), y + 4,
                    sliderWidth, 20);
            drawSlider(fontRenderer, bounds, CHANNEL_LABELS[channel] + " " + value, value, 0, 255,
                    isEditable());
            sliderHits.add(new SliderHit(colorIndex * CHANNEL_COUNT + channel, bounds, 0, 255));
        }
    }

    private void drawOpacityRow(FontRenderer fontRenderer, int x, int y, int width, int opacityIndex) {
        int value = getOpacity(opacityIndex);
        if (width < 70) {
            ModernUiRenderer.drawText(fontRenderer, OPACITY_LABELS[opacityIndex] + " " + value + "%", x, y + 5,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(20, width));
            return;
        }
        int labelWidth = width >= 120 ? 76 : Math.max(30, width / 3);
        ModernUiRenderer.drawText(fontRenderer, OPACITY_LABELS[opacityIndex], x, y + 5, ModernUiRenderer.SUBTLE_TEXT,
                labelWidth);
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x + labelWidth + 4, y + 3,
                Math.max(12, width - labelWidth - 4), 20);
        drawSlider(fontRenderer, bounds, value + "%", value, 0, 100, isEditable());
        sliderHits.add(new SliderHit(COLOR_COUNT * CHANNEL_COUNT + opacityIndex, bounds, 0, 100));
    }

    private void drawStyleRow(FontRenderer fontRenderer, int x, int y, int width, int styleIndex) {
        int value = getStyleValue(styleIndex);
        int labelWidth = width >= 150 ? 86 : Math.max(42, width / 3);
        ModernUiRenderer.drawText(fontRenderer, STYLE_LABELS[styleIndex], x, y + 5,
                ModernUiRenderer.SUBTLE_TEXT, labelWidth);
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x + labelWidth + 4, y + 3,
                Math.max(12, width - labelWidth - 4), 20);
        drawSlider(fontRenderer, bounds, String.valueOf(value), value, STYLE_MINIMUMS[styleIndex],
                STYLE_MAXIMUMS[styleIndex], isEditable());
        sliderHits.add(new SliderHit(COLOR_COUNT * CHANNEL_COUNT + OPACITY_COUNT + styleIndex, bounds,
                STYLE_MINIMUMS[styleIndex], STYLE_MAXIMUMS[styleIndex]));
    }

    private void drawSlider(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String text, int value, int minimum,
            int maximum, boolean enabled) {
        int trackColor = enabled ? 0xFF101820 : 0xFF151D24;
        int border = enabled ? ModernUiRenderer.BORDER_SUBTLE : 0xFF202A32;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, trackColor, border);
        float progress = (value - minimum) / (float) Math.max(1, maximum - minimum);
        int fillWidth = Math.max(3, Math.round((bounds.width - 2) * clampFloat(progress, 0.0F, 1.0F)));
        ModernUiRenderer.drawRoundedRect(bounds.x + 1, bounds.y + 1, fillWidth, bounds.height - 2, 3,
                enabled ? ModernUiRenderer.ACCENT_DIM : 0xFF28343D);
        ModernUiRenderer.drawText(fontRenderer, text, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(12, bounds.width - 12));
    }

    private void drawPreview(FontRenderer fontRenderer, int x, int y, int width, int height) {
        int panelOpacity = editingProfile == null ? 100 : editingProfile.panelOpacityPercent;
        int buttonOpacity = editingProfile == null ? 100 : editingProfile.buttonOpacityPercent;
        int inputOpacity = editingProfile == null ? 100 : editingProfile.inputOpacityPercent;
        int textOpacity = editingProfile == null ? 100 : editingProfile.textOpacityPercent;
        int panelColor = applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.panelBgBottom,
                ModernUiRenderer.SHELL_RAISED), panelOpacity);
        int buttonColor = applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.buttonBgNormal,
                ModernUiRenderer.ACCENT_DIM), buttonOpacity);
        int inputColor = applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.inputBg,
                0xFF101820), inputOpacity);
        int textColor = applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.labelText,
                ModernUiRenderer.TEXT), textOpacity);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 5, panelColor,
                applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.panelBorder,
                        ModernUiRenderer.BORDER), panelOpacity));
        ModernUiRenderer.drawText(fontRenderer, "示例面板", x + 10, y + 9, textColor, Math.max(28, width - 20));
        int innerWidth = Math.max(1, width - 20);
        int buttonWidth = Math.min(106, innerWidth);
        ModernUiRenderer.drawSubtlePanel(x + 10, y + 31, buttonWidth, 20, 4, buttonColor,
                applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.buttonBorderNormal,
                        ModernUiRenderer.ACCENT), buttonOpacity));
        ModernUiRenderer.drawText(fontRenderer, "示例按钮", x + 20, y + 37, textColor, buttonWidth - 20);
        int inputWidth = Math.min(190, innerWidth);
        ModernUiRenderer.drawSubtlePanel(x + 10, y + 61, inputWidth, 20, 4, inputColor,
                applyOpacity(colorWithFallback(editingProfile == null ? 0 : editingProfile.inputBorder,
                        ModernUiRenderer.BORDER), inputOpacity));
        ModernUiRenderer.drawText(fontRenderer, "输入框预览", x + 17, y + 67, textColor, inputWidth - 14);
    }

    private void drawTextField(FontRenderer fontRenderer, ModernTextField field, ModernMainLayout.Rect bounds,
            String placeholder, int mouseX, int mouseY) {
        if (field == null || bounds == null) {
            return;
        }
        boolean visible = intersects(bounds, editorClipBounds);
        field.setVisible(visible);
        field.setEnabled(isEditable());
        if (!visible) {
            return;
        }
        if (field == focusedFieldBeforeLayoutReset) {
            field.setFocused(true);
        }
        boolean focused = field.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                !isEditable() ? ModernUiRenderer.BORDER_SUBTLE
                        : focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && field.getText().trim().isEmpty() && placeholder != null) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, field.x, field.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, field.width));
        }
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean primary,
            boolean enabled, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE
                : primary ? hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT_DIM;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : primary ? ModernUiRenderer.ACCENT
                : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label,
                bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(!enabled ? ModernUiRenderer.DISABLED_TEXT : ModernUiRenderer.TEXT, fill),
                Math.max(12, bounds.width - 8));
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clipBounds, int contentHeight,
            int scrollOffset, int maxScroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clipBounds == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clipBounds, scrollOffset, maxScroll, clipBounds.height, Math.max(clipBounds.height, contentHeight),
                mouseX, mouseY, setter);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            themeToolsExpanded = false;
            return false;
        }
        if (mouseButton == 0 && contains(themeToolsBounds, mouseX, mouseY)) {
            themeToolsExpanded = !themeToolsExpanded;
            clearFieldFocus();
            return true;
        }
        if (themeToolsExpanded && mouseButton != 0 && contains(themeToolsPanelBounds, mouseX, mouseY)) return true;
        if (mouseButton == 0 && themeToolsExpanded && contains(themeToolsPanelBounds, mouseX, mouseY)) {
            if (contains(importJsonBounds, mouseX, mouseY)) {
                importThemeJson();
                return true;
            }
            if (contains(exportJsonBounds, mouseX, mouseY) && editingProfile != null) {
                applyEditorToDraft(true);
                copyThemeText(ThemeJsonCodec.exportTheme(editingProfile), "主题 JSON 已复制");
                return true;
            }
            if (contains(aiPromptBounds, mouseX, mouseY)) {
                copyThemeText(ThemeJsonCodec.aiPrompt(), "AI 提示词已复制，粘贴后填写主题描述");
                return true;
            }
            return true;
        }

        if (themeToolsExpanded) {
            themeToolsExpanded = false;
            return true;
        }
        if (mouseButton == 1) {
            for (int i = 0; i < ASSET_COUNT; i++) {
                if (isVisibleFieldHit(imageFields[i], imageBounds[i], mouseX, mouseY)) {
                    chooseImage(i);
                    return true;
                }
            }
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }

        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            if (now - lastDividerClickAt <= 350L && Math.abs(mouseX - lastDividerClickX) <= 4) {
                profileRatio = DEFAULT_PROFILE_RATIO;
                MainUiLayoutManager.setModernSplitRatio("theme.profile_list", profileRatio);
                lastDividerClickAt = 0L;
                draggingDivider = false;
                showStatus("主题方案宽度已恢复默认");
                return true;
            }
            lastDividerClickAt = now;
            lastDividerClickX = mouseX;
            dividerDragStartRatio = profileRatio;
            draggingDivider = true;
            return true;
        }

        if (profileScrollbar.beginDrag(mouseX, mouseY) || editorScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }

        for (int i = sliderHits.size() - 1; i >= 0; i--) {
            SliderHit hit = sliderHits.get(i);
            if (contains(editorClipBounds, mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY) && isEditable()) {
                draggingSlider = hit.index;
                updateSlider(hit, mouseX);
                return true;
            }
        }
        if (clickField(nameField, nameBounds, mouseX, mouseY, mouseButton)) {
            return true;
        }
        for (int i = 0; i < ASSET_COUNT; i++) {
            if (clickField(imageFields[i], imageBounds[i], mouseX, mouseY, mouseButton)
                    || clickField(scaleFields[i], scaleBounds[i], mouseX, mouseY, mouseButton)
                    || clickField(cropFields[i][0], cropBounds[i][0], mouseX, mouseY, mouseButton)
                    || clickField(cropFields[i][1], cropBounds[i][1], mouseX, mouseY, mouseButton)) {
                return true;
            }
        }

        for (int i = profileHits.size() - 1; i >= 0; i--) {
            ProfileHit hit = profileHits.get(i);
            if (contains(profileClipBounds, mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                selectProfile(hit.index);
                return true;
            }
        }
        for (int i = groupHits.size() - 1; i >= 0; i--) {
            GroupHit hit = groupHits.get(i);
            if (contains(profileClipBounds, mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                if (hit.builtIn) builtInCollapsed = !builtInCollapsed;
                else customCollapsed = !customCollapsed;
                return true;
            }
        }
        if (contains(randomBounds, mouseX, mouseY)) {
            createRandomDraft();
            return true;
        }
        if (contains(newBounds, mouseX, mouseY)) {
            duplicateProfile();
            return true;
        }
        if (contains(deleteBounds, mouseX, mouseY)) {
            if (!canDelete()) {
                showStatus("内置主题不可删除");
                return true;
            }
            if (!destructiveConfirmation.request(deleteConfirmationKey())) {
                showStatus("再次点击“删除”确认");
                return true;
            }
            deleteProfile();
            return true;
        }
        for (int i = 0; i < ASSET_COUNT; i++) {
            if (contains(editorClipBounds, mouseX, mouseY) && contains(imageToggleBounds[i], mouseX, mouseY)) {
                toggleAsset(i);
                return true;
            }
            if (contains(editorClipBounds, mouseX, mouseY)
                    && contains(qualityBounds[i], mouseX, mouseY) && isEditable()) {
                cycleQuality(i);
                return true;
            }
        }
        if (contains(applyBounds, mouseX, mouseY)) {
            applyPreview();
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            saveProfile();
            return true;
        }
        if (contains(resetBounds, mouseX, mouseY)) {
            restoreBuiltInDefault();
            return true;
        }
        clearFieldFocus();
        return true;
    }

    private boolean clickField(ModernTextField field, ModernMainLayout.Rect bounds, int mouseX, int mouseY,
            int mouseButton) {
        if (!isVisibleFieldHit(field, bounds, mouseX, mouseY) || !isEditable()) {
            return false;
        }
        clearFieldFocus();
        field.setFocused(true);
        field.mouseClicked(mouseX, mouseY, mouseButton);
        return true;
    }

    private boolean isVisibleFieldHit(ModernTextField field, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return field != null && field.getVisible() && bounds != null && editorClipBounds != null
                && editorClipBounds.contains(mouseX, mouseY) && bounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingDivider && clickedMouseButton == 0 && panelBounds != null) {
            int gap = 8;
            int splitTotal = Math.max(2, panelBounds.width - 20 - gap);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - panelBounds.x - 10, 180, 320, 140, 180);
            profileRatio = split.ratio;
            return true;
        }
        if (clickedMouseButton == 0 && profileScrollbar.isDragging()) {
            profileScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && editorScrollbar.isDragging()) {
            editorScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (draggingSlider >= 0) {
            SliderHit hit = findSlider(draggingSlider);
            if (hit != null) {
                updateSlider(hit, mouseX);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("theme.profile_list", profileRatio);
            draggingDivider = false;
            return true;
        }
        if (state == 0 && (profileScrollbar.isDragging() || editorScrollbar.isDragging())) {
            profileScrollbar.endDrag();
            editorScrollbar.endDrag();
            return true;
        }
        if (draggingSlider >= 0) {
            draggingSlider = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!initialized) {
            return false;
        }
        for (ModernTextField field : allFields()) {
            if (field != null && field.getVisible() && field.isFocused() && field.textboxKeyTyped(typedChar, keyCode)) {
                applyPreview();
                return true;
            }
        }
        if (keyCode == Keyboard.KEY_RETURN && hasFocusedField()) {
            saveProfile();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (themeToolsExpanded) {
            themeToolsExpanded = false;
            return true;
        }
        if (draggingDivider) {
            profileRatio = dividerDragStartRatio;
            draggingDivider = false;
            return true;
        }
        if (profileScrollbar.isDragging() || editorScrollbar.isDragging() || draggingSlider >= 0) {
            profileScrollbar.endDrag();
            editorScrollbar.endDrag();
            draggingSlider = -1;
            return true;
        }
        if (destructiveConfirmation.isPending(deleteConfirmationKey())) {
            destructiveConfirmation.clear();
            showStatus("已取消确认");
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (themeToolsExpanded && contains(themeToolsPanelBounds, mouseX, mouseY)) return true;
        if (wheel == 0) {
            return false;
        }
        if (editorClipBounds != null && editorClipBounds.contains(mouseX, mouseY) && editorMaxScrollOffset > 0) {
            int previous = editorScrollOffset;
            editorScrollOffset = clamp(editorScrollOffset + (wheel > 0 ? -34 : 34), 0, editorMaxScrollOffset);
            return previous != editorScrollOffset;
        }
        if (profileClipBounds != null && profileClipBounds.contains(mouseX, mouseY) && profileMaxScrollOffset > 0) {
            int previous = profileScrollOffset;
            profileScrollOffset = clamp(profileScrollOffset + (wheel > 0 ? -PROFILE_ROW_HEIGHT : PROFILE_ROW_HEIGHT),
                    0, profileMaxScrollOffset);
            return previous != profileScrollOffset;
        }
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return panelBounds != null && panelBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        draggingSlider = -1;
        draggingDivider = false;
        profileScrollbar.endDrag();
        editorScrollbar.endDrag();
        clearFieldFocus();
        ThemeConfigManager.applyActiveProfile();
        profiles = ThemeConfigManager.getProfiles();
        randomDraft = false;
        draftDirty = false;
        destructiveConfirmation.clear();
        selectedIndex = clamp(ThemeConfigManager.getActiveIndex(), 0, Math.max(0, profiles.size() - 1));
        loadProfile(selectedIndex);
        statusMessage = "";
    }

    private void selectProfile(int index) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        if (draftDirty) {
            showStatus("请先保存当前主题，或关闭标签页放弃修改");
            return;
        }
        destructiveConfirmation.clear();
        clearFieldFocus();
        randomDraft = false;
        selectedIndex = index;
        ThemeConfigManager.setActiveIndex(index);
        loadProfile(index);
        showStatus("已切换到 " + displayName(editingProfile));
    }

    private void loadProfile(int index) {
        if (profiles.isEmpty()) {
            editingProfile = ThemeProfile.fromCurrent("默认主题");
            bindFields();
            return;
        }
        int safeIndex = clamp(index, 0, profiles.size() - 1);
        selectedIndex = safeIndex;
        ThemeProfile profile = profiles.get(safeIndex);
        editingProfile = profile == null ? ThemeProfile.fromCurrent("默认主题") : profile.copy();
        bindFields();
    }

    private void bindFields() {
        if (editingProfile == null || nameField == null) {
            return;
        }
        nameField.setText(safe(editingProfile.name));
        for (int i = 0; i < ASSET_COUNT; i++) {
            imageFields[i].setText(assetPath(i));
            scaleFields[i].setText(String.valueOf(assetScale(i)));
            cropFields[i][0].setText(String.valueOf(assetCrop(i, 0)));
            cropFields[i][1].setText(String.valueOf(assetCrop(i, 1)));
        }
        clearFieldFocus();
    }

    private void createRandomDraft() {
        clearFieldFocus();
        randomDraft = true;
        draftDirty = true;
        editingProfile = ThemeConfigManager.createRandomProfile("随机主题 " + (profiles.size() + 1));
        bindFields();
        applyPreview();
        showStatus("已生成随机预览，保存后加入方案");
    }

    private void duplicateProfile() {
        if (editingProfile == null) {
            return;
        }
        applyEditorToDraft(true);
        ThemeProfile copy = editingProfile.copy();
        copy.name = nextThemeName(displayName(copy));
        copy.builtIn = false;
        copy.builtInId = "";
        ThemeConfigManager.addProfile(copy);
        ThemeConfigManager.save();
        profiles = ThemeConfigManager.getProfiles();
        randomDraft = false;
        selectedIndex = ThemeConfigManager.getActiveIndex();
        loadProfile(selectedIndex);
        TextureManagerHelper.clearCache();
        draftDirty = false;
        showStatus("已复制主题");
    }

    private void copyThemeText(String value, String success) {
        try {
            GuiScreen.setClipboardString(value);
            showStatus(value.equals(GuiScreen.getClipboardString()) ? success : "复制失败，请检查剪贴板后重试");
        } catch (RuntimeException ex) {
            showStatus("无法访问剪贴板，请重试");
        }
    }

    private void importThemeJson() {
        try {
            ThemeProfile imported = ThemeJsonCodec.importTheme(GuiScreen.getClipboardString());
            if (draftDirty && !destructiveConfirmation.request("import-theme-json")) {
                showStatus("当前有未保存修改，再次点击导入确认替换编辑内容");
                return;
            }
            ThemeConfigManager.addProfile(imported);
            ThemeConfigManager.save();
            profiles = ThemeConfigManager.getProfiles();
            selectedIndex = ThemeConfigManager.getActiveIndex();
            randomDraft = false;
            draftDirty = false;
            customCollapsed = false;
            loadProfile(selectedIndex);
            profileScrollOffset = Integer.MAX_VALUE;
            TextureManagerHelper.clearCache();
            showStatus("已导入并应用自定义主题：" + imported.name);
        } catch (IllegalArgumentException ex) {
            showStatus("导入失败：" + ex.getMessage());
            statusMessageUntil = System.currentTimeMillis() + 6000L;
        } catch (RuntimeException ex) {
            showStatus("导入失败，请检查剪贴板后重试");
        }
    }

    private void deleteProfile() {
        if (!canDelete()) {
            showStatus("内置主题不可删除");
            return;
        }
        if (randomDraft) {
            randomDraft = false;
            draftDirty = false;
            loadProfile(ThemeConfigManager.getActiveIndex());
            showStatus("已放弃随机草稿");
            return;
        }
        ThemeConfigManager.deleteIndices(Collections.singletonList(selectedIndex));
        ThemeConfigManager.save();
        profiles = ThemeConfigManager.getProfiles();
        selectedIndex = ThemeConfigManager.getActiveIndex();
        loadProfile(selectedIndex);
        TextureManagerHelper.clearCache();
        draftDirty = false;
        showStatus("已删除自定义主题");
    }

    private void saveProfile() {
        if (!isEditable() || editingProfile == null) {
            showStatus("当前没有可保存的主题");
            return;
        }
        applyEditorToDraft(true);
        editingProfile.name = safe(editingProfile.name).isEmpty() ? "未命名主题" : safe(editingProfile.name);
        if (randomDraft) {
            ThemeConfigManager.addProfile(editingProfile.copy());
            profiles = ThemeConfigManager.getProfiles();
            selectedIndex = ThemeConfigManager.getActiveIndex();
            randomDraft = false;
        } else if (selectedIndex >= 0 && selectedIndex < profiles.size()) {
            profiles.set(selectedIndex, editingProfile.copy());
            ThemeConfigManager.setActiveIndex(selectedIndex);
        }
        GuiTheme.applyProfile(editingProfile);
        ThemeConfigManager.save();
        TextureManagerHelper.clearCache();
        loadProfile(selectedIndex);
        draftDirty = false;
        showStatus("主题已保存");
    }

    private void applyPreview() {
        if (!isEditable() || editingProfile == null) {
            return;
        }
        applyEditorToDraft(false);
        draftDirty = true;
        GuiTheme.applyProfile(editingProfile);
    }

    private void restoreBuiltInDefault() {
        if (editingProfile == null || !ThemeConfigManager.isBuiltInProfile(editingProfile)) {
            showStatus("仅内置主题支持恢复默认");
            return;
        }
        ThemeProfile defaultProfile = ThemeConfigManager.getBuiltInDefault(editingProfile);
        if (defaultProfile == null) {
            showStatus("未找到内置主题默认值");
            return;
        }
        editingProfile = defaultProfile;
        bindFields();
        draftDirty = true;
        GuiTheme.applyProfile(editingProfile);
        showStatus("已恢复默认值，保存后生效");
    }

    @Override
    public boolean isDirty() {
        return draftDirty || randomDraft;
    }

    private void applyEditorToDraft(boolean normalizePaths) {
        if (editingProfile == null) {
            return;
        }
        String name = nameField == null ? "" : nameField.getText().trim();
        if (!name.isEmpty()) {
            editingProfile.name = name;
        }
        for (int i = 0; i < ASSET_COUNT; i++) {
            String path = imageFields[i] == null ? "" : imageFields[i].getText().trim();
            if (normalizePaths) {
                path = TextureManagerHelper.canonicalizeImagePath(path);
                imageFields[i].setText(path);
            }
            setAssetPath(i, path);
            setAssetScale(i, clamp(parseInt(scaleFields[i].getText(), assetScale(i)), 10, 300));
            setAssetCrop(i, 0, Math.max(0, parseInt(cropFields[i][0].getText(), assetCrop(i, 0))));
            setAssetCrop(i, 1, Math.max(0, parseInt(cropFields[i][1].getText(), assetCrop(i, 1))));
        }
    }

    private void cycleQuality(int assetIndex) {
        if (!isEditable() || editingProfile == null) {
            return;
        }
        String quality = assetQuality(assetIndex);
        ImageQuality current;
        try {
            current = ImageQuality.valueOf(quality);
        } catch (Exception ignored) {
            current = ImageQuality.MEDIUM;
        }
        String next = current.next().name();
        editingProfile.panelImageQuality = next;
        applyPreview();
    }

    private void chooseImage(int assetIndex) {
        if (!isEditable()) {
            return;
        }
        try {
            JFileChooser chooser = new JFileChooser();
            File directory = TextureManagerHelper.getThemeImageCacheDir().toFile();
            if (directory.exists() && directory.isDirectory()) {
                chooser.setCurrentDirectory(directory);
            }
            chooser.setDialogTitle("选择图片");
            chooser.setFileFilter(new FileNameExtensionFilter("图片文件", "png", "jpg", "jpeg", "bmp", "gif", "webp"));
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                imageFields[assetIndex].setText(chooser.getSelectedFile().getAbsolutePath());
                applyPreview();
            }
        } catch (Exception ignored) {
        }
    }

    private void updateSlider(SliderHit hit, int mouseX) {
        if (hit == null || hit.bounds.width <= 0 || !isEditable()) {
            return;
        }
        float progress = (mouseX - hit.bounds.x) / (float) Math.max(1, hit.bounds.width - 1);
        int value = Math.round(hit.minimum + clampFloat(progress, 0.0F, 1.0F) * (hit.maximum - hit.minimum));
        setSliderValue(hit.index, value);
        applyPreview();
    }

    private SliderHit findSlider(int index) {
        for (SliderHit hit : sliderHits) {
            if (hit.index == index) {
                return hit;
            }
        }
        return null;
    }

    private void setSliderValue(int index, int value) {
        if (index < COLOR_COUNT * CHANNEL_COUNT) {
            int colorIndex = index / CHANNEL_COUNT;
            int channel = index % CHANNEL_COUNT;
            int color = getColor(colorIndex);
            int mask = 0xFF << ((2 - channel) * 8);
            int updated = (color & ~mask) | ((value & 0xFF) << ((2 - channel) * 8));
            setColor(colorIndex, updated | 0xFF000000);
            return;
        }
        if (editingProfile == null) {
            return;
        }
        int relativeIndex = index - COLOR_COUNT * CHANNEL_COUNT;
        if (relativeIndex < OPACITY_COUNT) {
            if (relativeIndex == 0) editingProfile.panelOpacityPercent = value;
            if (relativeIndex == 1) editingProfile.buttonOpacityPercent = value;
            if (relativeIndex == 2) editingProfile.inputOpacityPercent = value;
            if (relativeIndex == 3) editingProfile.textOpacityPercent = value;
            return;
        }
        setStyleValue(relativeIndex - OPACITY_COUNT, value);
    }

    private int getColor(int colorIndex) {
        if (editingProfile == null) {
            return ModernUiRenderer.ACCENT;
        }
        switch (colorIndex) {
            case 0: return editingProfile.panelBorder;
            case 1: return editingProfile.panelBgTop;
            case 2: return editingProfile.panelBgBottom;
            case 3: return editingProfile.titleLeft;
            case 4: return editingProfile.titleRight;
            case 5: return editingProfile.titleText;
            case 6: return editingProfile.labelText;
            case 7: return editingProfile.subText;
            case 8: return editingProfile.stateSuccess;
            case 9: return editingProfile.stateWarning;
            case 10: return editingProfile.stateDanger;
            case 11: return editingProfile.stateDisabled;
            case 12: return editingProfile.stateSelected;
            case 13: return editingProfile.buttonBgNormal;
            case 14: return editingProfile.buttonBgHover;
            case 15: return editingProfile.buttonBgPressed;
            case 16: return editingProfile.buttonBorderNormal;
            case 17: return editingProfile.buttonBorderHover;
            case 18: return editingProfile.inputBg;
            case 19: return editingProfile.inputBorder;
            case 20: return editingProfile.inputBorderHover;
            case 21: return editingProfile.getInputCursorColor();
            case 22: return colorOr(editingProfile.shadowColor, 0xFF000000);
            default: return ModernUiRenderer.ACCENT;
        }
    }

    private void setColor(int colorIndex, int color) {
        if (editingProfile == null) return;
        switch (colorIndex) {
            case 0: editingProfile.panelBorder = color; break;
            case 1: editingProfile.panelBgTop = color; break;
            case 2: editingProfile.panelBgBottom = color; break;
            case 3: editingProfile.titleLeft = color; break;
            case 4: editingProfile.titleRight = color; break;
            case 5: editingProfile.titleText = color; break;
            case 6: editingProfile.labelText = color; break;
            case 7: editingProfile.subText = color; break;
            case 8: editingProfile.stateSuccess = color; break;
            case 9: editingProfile.stateWarning = color; break;
            case 10: editingProfile.stateDanger = color; break;
            case 11: editingProfile.stateDisabled = color; break;
            case 12: editingProfile.stateSelected = color; break;
            case 13: editingProfile.buttonBgNormal = color; break;
            case 14: editingProfile.buttonBgHover = color; break;
            case 15: editingProfile.buttonBgPressed = color; break;
            case 16: editingProfile.buttonBorderNormal = color; break;
            case 17: editingProfile.buttonBorderHover = color; break;
            case 18: editingProfile.inputBg = color; break;
            case 19: editingProfile.inputBorder = color; break;
            case 20: editingProfile.inputBorderHover = color; break;
            case 21: editingProfile.inputCursor = color; break;
            case 22: editingProfile.shadowColor = color; break;
            default: break;
        }
    }

    private int getOpacity(int opacityIndex) {
        if (editingProfile == null) return 100;
        if (opacityIndex == 0) return clamp(editingProfile.panelOpacityPercent, 0, 100);
        if (opacityIndex == 1) return clamp(editingProfile.buttonOpacityPercent, 0, 100);
        if (opacityIndex == 2) return clamp(editingProfile.inputOpacityPercent, 0, 100);
        return clamp(editingProfile.textOpacityPercent, 0, 100);
    }

    private int getStyleValue(int styleIndex) {
        if (editingProfile == null) return STYLE_MINIMUMS[styleIndex];
        if (styleIndex == 0) return clamp(editingProfile.cornerRadius == null ? 6 : editingProfile.cornerRadius, 0, 12);
        if (styleIndex == 1) return clamp(editingProfile.borderWidth == null ? 1 : editingProfile.borderWidth, 1, 4);
        return clamp(editingProfile.shadowOffset == null ? 0 : editingProfile.shadowOffset, 0, 8);
    }

    private void setStyleValue(int styleIndex, int value) {
        if (editingProfile == null || styleIndex < 0 || styleIndex >= STYLE_COUNT) return;
        int normalized = clamp(value, STYLE_MINIMUMS[styleIndex], STYLE_MAXIMUMS[styleIndex]);
        if (styleIndex == 0) editingProfile.cornerRadius = normalized;
        if (styleIndex == 1) editingProfile.borderWidth = normalized;
        if (styleIndex == 2) editingProfile.shadowOffset = normalized;
    }

    private String assetPath(int assetIndex) {
        if (editingProfile == null) return "";
        return safe(editingProfile.panelImagePath);
    }

    private void setAssetPath(int assetIndex, String value) {
        editingProfile.panelImagePath = value;
    }

    private boolean assetEnabled(int assetIndex) {
        return ThemeConfigManager.isPanelImageEnabled(editingProfile);
    }

    private void toggleAsset(int assetIndex) {
        if (editingProfile == null) {
            return;
        }
        editingProfile.panelImageEnabled = !assetEnabled(assetIndex);
        applyPreview();
        GuiTheme.applyProfile(editingProfile);
        showStatus(assetEnabled(assetIndex) ? "面板背景已开启" : "面板背景已关闭");
    }

    private int assetScale(int assetIndex) {
        if (editingProfile == null) return 100;
        return editingProfile.panelImageScale;
    }

    private void setAssetScale(int assetIndex, int value) {
        editingProfile.panelImageScale = value;
    }

    private int assetCrop(int assetIndex, int axis) {
        if (editingProfile == null) return 0;
        return axis == 0 ? editingProfile.panelCropX : editingProfile.panelCropY;
    }

    private void setAssetCrop(int assetIndex, int axis, int value) {
        if (axis == 0) editingProfile.panelCropX = value;
        else editingProfile.panelCropY = value;
    }

    private String assetQuality(int assetIndex) {
        if (editingProfile == null) return ImageQuality.MEDIUM.name();
        return safe(editingProfile.panelImageQuality).toUpperCase();
    }

    private String qualityText(String quality) {
        try {
            return ImageQuality.valueOf(quality).getDisplayName();
        } catch (Exception ignored) {
            return quality == null || quality.isEmpty() ? "中" : quality;
        }
    }

    private boolean canDelete() {
        return randomDraft || (selectedIndex >= 0 && selectedIndex < profiles.size()
                && !ThemeConfigManager.isBuiltInProfile(profiles.get(selectedIndex)));
    }

    private boolean isEditable() {
        return editingProfile != null;
    }

    private boolean hasFocusedField() {
        for (ModernTextField field : allFields()) {
            if (field != null && field.isFocused()) return true;
        }
        return false;
    }

    private ModernTextField findFocusedField() {
        for (ModernTextField field : allFields()) {
            if (field != null && field.isVisible() && field.isFocused()) {
                return field;
            }
        }
        return null;
    }

    private void clearFieldFocus() {
        for (ModernTextField field : allFields()) {
            if (field != null) field.setFocused(false);
        }
    }

    private List<ModernTextField> allFields() {
        List<ModernTextField> fields = new ArrayList<>();
        if (nameField != null) fields.add(nameField);
        for (int i = 0; i < ASSET_COUNT; i++) {
            fields.add(imageFields[i]);
            fields.add(scaleFields[i]);
            fields.add(cropFields[i][0]);
            fields.add(cropFields[i][1]);
        }
        return fields;
    }

    private void hideFields() {
        for (ModernTextField field : allFields()) {
            if (field != null) field.setVisible(false);
        }
    }

    private String tooltipAt(int mouseX, int mouseY) {
        if (contains(themeToolsBounds, mouseX, mouseY)) return "展开或收起主题 JSON 导入、导出与 AI 提示词";
        if (themeToolsExpanded && contains(themeToolsPanelBounds, mouseX, mouseY)) {
            if (contains(importJsonBounds, mouseX, mouseY)) return "先复制完整主题 JSON，再点击导入。\n校验通过后新增为自定义主题并应用。";
            if (contains(exportJsonBounds, mouseX, mouseY)) return "复制当前编辑中的主题 JSON，包含尚未保存的调整。\n图片仅保存路径，不包含图片文件。";
            if (contains(aiPromptBounds, mouseX, mouseY)) return "复制给 AI 的格式说明与完整示例。\n粘贴到 AI 后填写想要的主题，复制返回的纯 JSON 即可导入。";
        }
        if (contains(dividerBounds, mouseX, mouseY)) {
            return "拖动调整主题方案宽度\n双击恢复默认宽度，Escape 取消本次拖动。";
        }
        for (int i = 0; i < ASSET_COUNT; i++) {
            if (contains(imageBounds[i], mouseX, mouseY)) return "右键选择图片文件";
            if (contains(imageToggleBounds[i], mouseX, mouseY)) return assetEnabled(i) ? "关闭面板背景" : "开启面板背景";
            if (contains(qualityBounds[i], mouseX, mouseY)) return "点击切换图片质量";
        }
        return "";
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2200L;
    }

    private String deleteConfirmationKey() {
        return "delete:" + (randomDraft ? "random" : String.valueOf(selectedIndex));
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        if (bounds == null) return new ModernMainLayout.Rect(0, 0, 1, 1);
        int inset = Math.min(12, Math.max(3, Math.min(bounds.width, bounds.height) / 10));
        return new ModernMainLayout.Rect(bounds.x + inset, bounds.y + inset,
                Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private static int channelValue(int color, int channel) {
        return (color >> ((2 - channel) * 8)) & 0xFF;
    }

    private static int colorOr(int color, int fallback) {
        return color == 0 ? fallback : color;
    }

    private static int colorWithFallback(int color, int fallback) {
        return colorOr(color, fallback);
    }

    private static int applyOpacity(int color, int percent) {
        int alpha = (color >>> 24) & 0xFF;
        int normalized = clamp(percent, 0, 100);
        return ((alpha * normalized / 100) << 24) | (color & 0x00FFFFFF);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String displayName(ThemeProfile profile) {
        String name = profile == null ? "" : safe(profile.name);
        return name.isEmpty() ? "未命名主题" : name;
    }

    private String nextThemeName(String base) {
        String candidate = safe(base);
        if (candidate.isEmpty()) candidate = "未命名主题";
        String result = candidate + " 副本";
        int suffix = 2;
        while (containsThemeName(result)) {
            result = candidate + " 副本 " + suffix++;
        }
        return result;
    }

    private boolean containsThemeName(String name) {
        for (ThemeProfile profile : profiles) {
            if (profile != null && safe(profile.name).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clampFloat(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
