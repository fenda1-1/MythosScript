package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.GuiOtherFeaturesHudPosition;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabRegistry;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager.ThemeProfile;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.FeatureDef;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.GroupDef;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ClientTranslationInjector;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

/** Compact, cross-cutting preferences page for the modern control center. */
public final class ModernGeneralSettingsTab implements ModernSettingsTab {

    private static final int STATE_CARD_HEIGHT = 234;
    private static final int STATE_PAGE_ROW_HEIGHT = 29;
    private static final double DEFAULT_APPEARANCE_RATIO = 0.30D;
    private static final int SETTINGS_ROW_HEIGHT = 25;
    private static final int SETTINGS_GAP = 6;
    private static final int SETTINGS_SECTION_HEIGHT = 20;

    private static final String[] LANGUAGE_CODES = { "zh_cn", "en_us" };
    private static final String[] LANGUAGE_NAMES = { "简体中文", "English" };
    private static final String[] SCALE_VALUES = { "100", "125", "150", "175", "200", "225", "250", "275", "300" };
    private static final String[] SCALE_LABELS = { "100%", "125%", "150%", "175%", "200%", "225%", "250%", "275%", "300%" };
    private static final String[] TAB_ORIENTATION_VALUES = { "horizontal", "vertical" };
    private static final String[] TAB_ORIENTATION_LABELS = { "gui.general.tabs_horizontal", "gui.general.tabs_vertical" };
    private static final String[] RULE_NAVIGATION_POSITION_VALUES = { "side", "top" };
    private static final String[] RULE_NAVIGATION_POSITION_LABELS = { "gui.general.rule_nav_side", "gui.general.rule_nav_top" };
    private static final String[] RULE_NAVIGATION_DETAIL_VALUES = { "icon", "text" };
    private static final String[] RULE_NAVIGATION_DETAIL_LABELS = { "gui.general.rule_nav_icon", "gui.general.rule_nav_text" };

    private final Minecraft minecraft;
    private final java.util.function.Consumer<String> routeRequest;
    private final List<ModernMainLayout.Rect> themeBounds = new ArrayList<>();
    private final List<Integer> themeIndices = new ArrayList<>();
    private final ModernMainLayout.Rect[] themeGroupBounds = new ModernMainLayout.Rect[2];
    private final boolean[] themeGroupCollapsed = new boolean[2];
    private final List<ModernMainLayout.Rect> languageBounds = new ArrayList<>();
    private final List<ModernMainLayout.Rect> scaleBounds = new ArrayList<>();
    private final ModernDropdown languageDropdown = new ModernDropdown(LANGUAGE_CODES, LANGUAGE_NAMES);
    private final ModernDropdown scaleDropdown = new ModernDropdown(SCALE_VALUES, SCALE_LABELS);
    private final ModernDropdown tabOrientationDropdown = new ModernDropdown(TAB_ORIENTATION_VALUES,
            TAB_ORIENTATION_LABELS);
    private final ModernDropdown ruleNavigationPositionDropdown = new ModernDropdown(RULE_NAVIGATION_POSITION_VALUES,
            RULE_NAVIGATION_POSITION_LABELS);
    private final ModernDropdown ruleNavigationDetailDropdown = new ModernDropdown(RULE_NAVIGATION_DETAIL_VALUES,
            RULE_NAVIGATION_DETAIL_LABELS);
    private final ModernHoverScrollbar appearanceScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar behaviorScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar stateRecordingScrollbar = new ModernHoverScrollbar();
    private final ModernTabRegistry stateTabRegistry = ModernTabCatalog.createRegistry();
    private final List<ModernMainLayout.Rect> statePageBounds = new ArrayList<>();
    private final List<String> statePageCommands = new ArrayList<>();
    private final List<ModernTabDescriptor> statePages = new ArrayList<>();
    private GuiTextField stateRecordingSearch;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect appearanceBounds;
    private ModernMainLayout.Rect behaviorBounds;
    private ModernMainLayout.Rect appearanceClipBounds;
    private ModernMainLayout.Rect behaviorClipBounds;
    private ModernMainLayout.Rect compactContentBounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect editThemeBounds;
    private ModernMainLayout.Rect autoFocusBounds;
    private ModernMainLayout.Rect autoPauseBounds;
    private ModernMainLayout.Rect pathStatusHudBounds;
    private ModernMainLayout.Rect featureStatusHudBounds;
    private ModernMainLayout.Rect editFeatureHudBounds;
    private ModernMainLayout.Rect resetLayoutBounds;
    private ModernMainLayout.Rect stateRecordingBounds;
    private ModernMainLayout.Rect stateRecordingClipBounds;
    private ModernMainLayout.Rect stateRecordingZoomBounds;
    private ModernMainLayout.Rect stateRecordingMasterBounds;
    private ModernMainLayout.Rect stateRecordingClearBounds;
    private ModernMainLayout.Rect stateRecordingSearchBounds;
    private ModernMainLayout.Rect expandedStateRecordingBounds;
    private double appearanceRatio = DEFAULT_APPEARANCE_RATIO;
    private boolean draggingDivider;
    private int appearanceScroll;
    private int appearanceMaxScroll;
    private int behaviorScroll;
    private int behaviorMaxScroll;
    private int compactScroll;
    private int compactMaxScroll;
    private int stateRecordingScroll;
    private int stateRecordingMaxScroll;
    private int lastMouseX;
    private int lastMouseY;
    private boolean compactMode;
    private boolean stateRecordingExpanded;
    private boolean layoutPreferencesLoaded;
    private boolean statePagesLoaded;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private String pendingLanguage;
    private String stateRecordingLastQuery = "";
    private long statusUntil;

    public ModernGeneralSettingsTab(ModernScreenContext context) {
        this.minecraft = context == null ? Minecraft.getMinecraft() : context.getMinecraft();
        this.routeRequest = context == null ? null : context.getRouteRequest();
    }

    public static ModernSettingsTab create(ModernScreenContext context) {
        return new ModernGeneralSettingsTab(context);
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            appearanceRatio = MainUiLayoutManager.getModernSplitRatio("general.appearance", appearanceRatio);
            layoutPreferencesLoaded = true;
        }
        ThemeConfigManager.ensureLoaded();
        ThemeConfigManager.ensureDefaultPresets();
        if (!statePagesLoaded) {
            refreshStatePages();
            statePagesLoaded = true;
        }
        if (stateRecordingSearch == null) {
            stateRecordingSearch = new GuiTextField(0, fontRenderer, 0, 0, 1, 12);
            stateRecordingSearch.setEnableBackgroundDrawing(false);
            stateRecordingSearch.setMaxStringLength(96);
            stateRecordingSearch.setTextColor(ModernUiRenderer.TEXT);
            stateRecordingSearch.setDisabledTextColour(ModernUiRenderer.SUBTLE_TEXT);
        }
    }

    @Override
    public void updateScreen() {
        if (pendingLanguage != null && ClientTranslationInjector.INSTANCE.applyLanguage(pendingLanguage)) {
            GuiInventory.refreshGuiLists();
            refreshStatePages();
            statePagesLoaded = true;
            pendingLanguage = null;
        }
        if (stateRecordingSearch != null) {
            stateRecordingSearch.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        hoveredTooltip = "";
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        panelBounds = safePanel(bounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        int x = panelBounds.x + 16;
        int width = Math.max(1, panelBounds.width - 32);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.title", "通用设置"), x, panelBounds.y + 12, ModernUiRenderer.TEXT,
                Math.max(30, width));
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.subtitle", "控制整个脚本界面的外观、语言与交互体验"), x, panelBounds.y + 27,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(30, width));
        ModernUiRenderer.drawDivider(x, panelBounds.y + 47, width, ModernUiRenderer.BORDER_SUBTLE);

        compactMode = panelBounds.width < 500 || panelBounds.height < 280;
        if (compactMode) {
            drawCompact(fontRenderer, x, panelBounds.y + 58, width, mouseX, mouseY);
            return;
        }
        int bodyY = panelBounds.y + 59;
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 9);
        int gap = 10;
        int splitTotal = Math.max(2, width - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, appearanceRatio,
                178, 250, 122, 160);
        appearanceBounds = new ModernMainLayout.Rect(x, bodyY, split.firstWidth, bodyHeight);
        behaviorBounds = new ModernMainLayout.Rect(appearanceBounds.right() + gap, bodyY, split.secondWidth, bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(appearanceBounds.x, appearanceBounds.width, gap, bodyY,
                bodyHeight);
        drawAppearance(fontRenderer, appearanceBounds, mouseX, mouseY);
        drawBehavior(fontRenderer, behaviorBounds, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
        drawDropdownMenus(fontRenderer, behaviorBounds, mouseX, mouseY);
        if (stateRecordingExpanded) {
            drawExpandedStateRecording(fontRenderer, mouseX, mouseY);
        }
    }

    private void drawCompact(FontRenderer fontRenderer, int x, int y, int width, int mouseX, int mouseY) {
        themeBounds.clear();
        languageBounds.clear();
        scaleBounds.clear();
        appearanceBounds = null;
        behaviorBounds = null;
        dividerBounds = null;
        appearanceScrollbar.idle();
        behaviorScrollbar.idle();
        if (!stateRecordingExpanded) stateRecordingScrollbar.idle();
        int contentHeight = themeContentHeight() + behaviorContentHeight() + 30;
        compactContentBounds = new ModernMainLayout.Rect(x, y, Math.max(1, width),
                Math.max(1, panelBounds.bottom() - y - 8));
        compactMaxScroll = Math.max(0, contentHeight - compactContentBounds.height);
        compactScroll = clamp(compactScroll, 0, compactMaxScroll);
        ModernUiRenderer.beginClip(compactContentBounds);
        int contentY = compactContentBounds.y + 2 - compactScroll;
        int sectionWidth = ModernHoverScrollbar.contentWidth(compactContentBounds.width);
        int appearanceHeight = themeContentHeight();
        ModernUiRenderer.drawSubtlePanel(compactContentBounds.x, contentY, sectionWidth, appearanceHeight, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawCompactAppearance(fontRenderer, new ModernMainLayout.Rect(compactContentBounds.x + 8, contentY + 8,
                Math.max(1, sectionWidth - 16), Math.max(1, appearanceHeight - 16)), mouseX, mouseY);
        int behaviorY = contentY + appearanceHeight + 10;
        int behaviorHeight = Math.max(1, contentHeight - appearanceHeight - 10);
        ModernUiRenderer.drawSubtlePanel(compactContentBounds.x, behaviorY, sectionWidth, behaviorHeight, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawCompactBehavior(fontRenderer, new ModernMainLayout.Rect(compactContentBounds.x + 8, behaviorY + 8,
                Math.max(1, sectionWidth - 16), Math.max(1, behaviorHeight - 16)), mouseX, mouseY);
        ModernUiRenderer.endClip();
        compactScrollbar.draw(compactContentBounds, compactScroll, compactMaxScroll, compactContentBounds.height,
                contentHeight, mouseX, mouseY, value -> compactScroll = value);
        drawDropdownMenus(fontRenderer, compactContentBounds, mouseX, mouseY);
        if (stateRecordingExpanded) {
            drawExpandedStateRecording(fontRenderer, mouseX, mouseY);
        }
    }

    private void drawAppearance(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        int x = bounds.x + 11;
        int width = Math.max(1, bounds.width - 22);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.appearance", "外观"), x, bounds.y + 10, ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.theme_profile", "主题方案"), x, bounds.y + 31, ModernUiRenderer.SUBTLE_TEXT, width);
        themeBounds.clear();
        themeIndices.clear();
        appearanceClipBounds = new ModernMainLayout.Rect(x, bounds.y + 48, Math.max(1, width),
                Math.max(1, bounds.height - 84));
        int contentHeight = themeListHeight();
        appearanceMaxScroll = Math.max(0, contentHeight - appearanceClipBounds.height);
        appearanceScroll = clamp(appearanceScroll, 0, appearanceMaxScroll);
        ModernUiRenderer.beginClip(appearanceClipBounds);
        int y = appearanceClipBounds.y - appearanceScroll;
        drawThemeGroups(fontRenderer, appearanceClipBounds, appearanceClipBounds, y, mouseX, mouseY);
        ModernUiRenderer.endClip();
        appearanceScrollbar.draw(appearanceClipBounds, appearanceScroll, appearanceMaxScroll,
                appearanceClipBounds.height, contentHeight, mouseX, mouseY, value -> appearanceScroll = value);
        editThemeBounds = new ModernMainLayout.Rect(x, bounds.bottom() - 28, width, 22);
        drawButton(fontRenderer, editThemeBounds, text("gui.general.manage_themes", "管理主题方案"), false, editThemeBounds.contains(mouseX, mouseY));
    }

    private void drawBehavior(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        int x = bounds.x + 12;
        int width = Math.max(1, bounds.width - 24);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.interface", "界面与交互"), x, bounds.y + 10, ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(fontRenderer,
                text("gui.general.profile_hint", "当前配置档案：") + getActiveProfileName(), x, bounds.y + 25,
                ModernUiRenderer.MUTED_TEXT, width);
        behaviorClipBounds = new ModernMainLayout.Rect(x, bounds.y + 43, Math.max(1, width),
                Math.max(1, bounds.height - 52));
        int contentHeight = behaviorContentHeight();
        behaviorMaxScroll = Math.max(0, contentHeight - behaviorClipBounds.height);
        behaviorScroll = clamp(behaviorScroll, 0, behaviorMaxScroll);
        ModernUiRenderer.beginClip(behaviorClipBounds);
        drawBehaviorContent(fontRenderer, behaviorClipBounds, behaviorClipBounds.y + 3 - behaviorScroll,
                mouseX, mouseY);
        ModernUiRenderer.endClip();
        behaviorScrollbar.draw(behaviorClipBounds, behaviorScroll, behaviorMaxScroll,
                behaviorClipBounds.height, contentHeight, mouseX, mouseY, value -> behaviorScroll = value);
    }

    private void refreshStatePages() {
        statePages.clear();
        OtherFeatureGroupManager.reload();
        java.util.HashSet<String> commands = new java.util.HashSet<String>();
        for (String command : stateTabRegistry.commands()) {
            if (command == null || command.trim().isEmpty() || command.endsWith(":")) {
                continue;
            }
            // These are legacy command aliases for the same modern workbench.
            if ("autopickup".equals(command) || "warehouse".equals(command)
                    || "auto_use_item".equals(command)) {
                continue;
            }
            ModernTabDescriptor descriptor = stateTabRegistry.find(command);
            if (descriptor != null) {
                statePages.add(descriptor);
                commands.add(command);
            }
        }
        // The additional-feature catalog is dynamic and therefore cannot be
        // registered in the static route table. Include its concrete pages too.
        for (GroupDef group : OtherFeatureGroupManager.getGroups()) {
            if (group == null || group.features == null) continue;
            for (FeatureDef feature : group.features) {
                if (feature == null || feature.id == null || feature.id.trim().isEmpty()) continue;
                final String command = ModernOtherFeatureSettingsTab.commandFor(feature.id);
                if (!commands.add(command)) continue;
                String groupName = group.name == null ? "" : group.name.trim();
                String featureName = feature.name == null || feature.name.trim().isEmpty()
                        ? feature.id.trim() : feature.name.trim();
                String title = groupName.isEmpty() ? featureName : groupName + " · " + featureName;
                statePages.add(new ModernTabDescriptor(command, title,
                        (minecraft, context) -> ModernOtherFeatureSettingsTab.createFromCommand(command)));
            }
        }
    }

    private boolean matchesStatePage(ModernTabDescriptor descriptor, String normalizedQuery) {
        if (descriptor == null || normalizedQuery == null || normalizedQuery.isEmpty()) {
            return true;
        }
        String label = ModernFormI18n.tr(descriptor.getTitle());
        if (label == null || label.trim().isEmpty() || ModernFormI18n.isKey(label)) {
            label = descriptor.getCommand();
        }
        return PinyinSearchHelper.matchesNormalized(label + " " + descriptor.getCommand(), normalizedQuery);
    }

    private void drawStateRecordingCard(FontRenderer fontRenderer, ModernMainLayout.Rect card,
            int mouseX, int mouseY, boolean expanded) {
        if (card == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.state_recording", "设置页状态记录"),
                card.x + 10, card.y + 9, ModernUiRenderer.TEXT, Math.max(20, card.width - 112));
        ModernUiRenderer.drawText(fontRenderer,
                text("gui.general.state_recording_hint", "记录滚动位置和分组子页面状态"),
                card.x + 10, card.y + 24, ModernUiRenderer.SUBTLE_TEXT, Math.max(20, card.width - 112));

        if (card.width >= 132) {
            stateRecordingClearBounds = new ModernMainLayout.Rect(card.right() - 86, card.y + 6, 52, 20);
            drawButton(fontRenderer, stateRecordingClearBounds,
                    text("gui.general.state_recording_clear_short", "清空"), false,
                    stateRecordingClearBounds.contains(mouseX, mouseY));
        } else {
            stateRecordingClearBounds = null;
        }

        stateRecordingZoomBounds = new ModernMainLayout.Rect(Math.max(card.x + 4, card.right() - 29),
                card.y + 6, 22, 20);
        boolean zoomHovered = stateRecordingZoomBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(stateRecordingZoomBounds.x, stateRecordingZoomBounds.y,
                stateRecordingZoomBounds.width, stateRecordingZoomBounds.height, 4,
                zoomHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                zoomHovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawExpandIcon(stateRecordingZoomBounds.x + 3, stateRecordingZoomBounds.y + 3,
                expanded, zoomHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);

        stateRecordingMasterBounds = new ModernMainLayout.Rect(card.x + 8, card.y + 43,
                Math.max(1, card.width - 16), 23);
        drawToggleRow(fontRenderer, stateRecordingMasterBounds,
                text("gui.general.state_recording_master", "启用状态记录"),
                MainUiLayoutManager.isModernStateRecordingEnabled(), mouseX, mouseY);

        stateRecordingSearchBounds = new ModernMainLayout.Rect(card.x + 8, card.y + 72,
                Math.max(1, card.width - 16), 22);
        boolean searchHovered = stateRecordingSearchBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(stateRecordingSearchBounds.x, stateRecordingSearchBounds.y,
                stateRecordingSearchBounds.width, stateRecordingSearchBounds.height, 4,
                ModernUiRenderer.INPUT_SURFACE,
                stateRecordingSearch != null && stateRecordingSearch.isFocused() ? ModernUiRenderer.ACCENT
                        : searchHovered ? ModernUiRenderer.HOVER_BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(stateRecordingSearchBounds.x + 6,
                stateRecordingSearchBounds.y + 4, ModernUiRenderer.MUTED_TEXT);
        if (stateRecordingSearch != null) {
            stateRecordingSearch.x = stateRecordingSearchBounds.x + 22;
            stateRecordingSearch.y = stateRecordingSearchBounds.y + 5;
            stateRecordingSearch.width = Math.max(1, stateRecordingSearchBounds.width - 28);
            stateRecordingSearch.height = 12;
            ModernUiRenderer.reflowTextField(stateRecordingSearch);
            if (stateRecordingSearch.getText().isEmpty() && !stateRecordingSearch.isFocused()) {
                ModernUiRenderer.drawText(fontRenderer,
                        text("gui.general.state_recording_search", "搜索设置页或拼音"),
                        stateRecordingSearch.x, stateRecordingSearch.y,
                        ModernUiRenderer.MUTED_TEXT, stateRecordingSearch.width);
            }
            ModernUiRenderer.drawTextField(stateRecordingSearch);
        }

        stateRecordingClipBounds = new ModernMainLayout.Rect(card.x + 8, card.y + 101,
                Math.max(1, card.width - 16), Math.max(1, card.height - 109));
        ModernUiRenderer.drawSubtlePanel(stateRecordingClipBounds.x, stateRecordingClipBounds.y,
                stateRecordingClipBounds.width, stateRecordingClipBounds.height, 4,
                ModernUiRenderer.INPUT_SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        statePageBounds.clear();
        statePageCommands.clear();
        String query = PinyinSearchHelper.normalizeQuery(stateRecordingSearch == null
                ? "" : stateRecordingSearch.getText());
        if (!query.equals(stateRecordingLastQuery)) {
            stateRecordingLastQuery = query;
            stateRecordingScroll = 0;
        }
        int contentWidth = ModernHoverScrollbar.contentWidth(stateRecordingClipBounds.width);
        int visiblePageCount = 0;
        for (ModernTabDescriptor descriptor : statePages) {
            if (matchesStatePage(descriptor, query)) {
                visiblePageCount++;
            }
        }
        int contentHeight = Math.max(1, visiblePageCount * STATE_PAGE_ROW_HEIGHT);
        stateRecordingMaxScroll = Math.max(0, contentHeight - stateRecordingClipBounds.height);
        stateRecordingScroll = clamp(stateRecordingScroll, 0, stateRecordingMaxScroll);
        boolean masterEnabled = MainUiLayoutManager.isModernStateRecordingEnabled();
        ModernUiRenderer.beginClip(stateRecordingClipBounds);
        int visibleIndex = 0;
        for (int i = 0; i < statePages.size(); i++) {
            ModernTabDescriptor descriptor = statePages.get(i);
            if (!matchesStatePage(descriptor, query)) {
                continue;
            }
            int rowY = stateRecordingClipBounds.y + 3 + visibleIndex * STATE_PAGE_ROW_HEIGHT - stateRecordingScroll;
            visibleIndex++;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(stateRecordingClipBounds.x + 3, rowY,
                    Math.max(1, contentWidth - 6), STATE_PAGE_ROW_HEIGHT - 4);
            boolean hovered = stateRecordingClipBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
            boolean pageEnabled = MainUiLayoutManager.getModernPageStateRecordingPreference(descriptor.getCommand());
            int fill = !masterEnabled ? ModernUiRenderer.DISABLED_SURFACE
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3, fill,
                    hovered && masterEnabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 9,
                    masterEnabled && pageEnabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
            String label = ModernFormI18n.tr(descriptor.getTitle());
            if (label == null || label.trim().isEmpty() || ModernFormI18n.isKey(label)) {
                label = descriptor.getCommand();
            }
            ModernUiRenderer.drawText(fontRenderer, label, row.x + 20, row.y + 7,
                    masterEnabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.DISABLED_TEXT,
                    Math.max(20, row.width - 58));
            ModernUiRenderer.drawToggle(row.right() - 31, row.y + 5, 24, 14,
                    masterEnabled && pageEnabled, hovered && masterEnabled);
            if (intersects(row, stateRecordingClipBounds)) {
                statePageBounds.add(row);
                statePageCommands.add(descriptor.getCommand());
            }
        }
        ModernUiRenderer.endClip();
        stateRecordingScrollbar.draw(stateRecordingClipBounds, stateRecordingScroll, stateRecordingMaxScroll,
                stateRecordingClipBounds.height, contentHeight, mouseX, mouseY,
                value -> stateRecordingScroll = value);
    }

    private void drawExpandedStateRecording(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (panelBounds == null) {
            return;
        }
        expandedStateRecordingBounds = new ModernMainLayout.Rect(panelBounds.x + 8, panelBounds.y + 8,
                Math.max(1, panelBounds.width - 16), Math.max(1, panelBounds.height - 16));
        ModernUiRenderer.drawBackdropOverlay(panelBounds, 0xB0101820);
        drawStateRecordingCard(fontRenderer, expandedStateRecordingBounds, mouseX, mouseY, true);
    }

    private void drawToggleRow(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean enabled,
            int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF151F28, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, bounds.width - 48));
        ModernUiRenderer.drawToggle(bounds.right() - 34, bounds.y + 5, 26, 15, enabled, hovered);
    }

    private void drawButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean selected,
            boolean hovered) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(12, bounds.width - 12));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Let the shell handle header, sidebar, and tab-strip clicks.
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) return false;
        if (mouseButton != 0) return true;
        int previousLanguage = languageDropdown.selectedIndex();
        int previousScale = scaleDropdown.selectedIndex();
        int previousTabOrientation = tabOrientationDropdown.selectedIndex();
        int previousRulePosition = ruleNavigationPositionDropdown.selectedIndex();
        int previousRuleDetail = ruleNavigationDetailDropdown.selectedIndex();
        boolean dropdownHandled = false;
        dropdownHandled |= languageDropdown.mouseClicked(mouseX, mouseY);
        dropdownHandled |= scaleDropdown.mouseClicked(mouseX, mouseY);
        dropdownHandled |= tabOrientationDropdown.mouseClicked(mouseX, mouseY);
        dropdownHandled |= ruleNavigationPositionDropdown.mouseClicked(mouseX, mouseY);
        dropdownHandled |= ruleNavigationDetailDropdown.mouseClicked(mouseX, mouseY);
        if (dropdownHandled) {
            applyDropdownChanges(previousLanguage, previousScale, previousTabOrientation,
                    previousRulePosition, previousRuleDetail);
            return true;
        }
        if (stateRecordingClearBounds != null && stateRecordingClearBounds.contains(mouseX, mouseY)) {
            clearStateMemory();
            return true;
        }
        if (stateRecordingSearchBounds != null && stateRecordingSearchBounds.contains(mouseX, mouseY)
                && stateRecordingSearch != null) {
            stateRecordingSearch.mouseClicked(mouseX, mouseY, mouseButton);
            stateRecordingSearch.setFocused(true);
            stateRecordingScroll = 0;
            return true;
        }
        if (stateRecordingExpanded) {
            if (stateRecordingZoomBounds != null && stateRecordingZoomBounds.contains(mouseX, mouseY)) {
                stateRecordingExpanded = false;
                stateRecordingScrollbar.endDrag();
                return true;
            }
            if (stateRecordingMasterBounds != null && stateRecordingMasterBounds.contains(mouseX, mouseY)) {
                toggleStateRecordingMaster();
                return true;
            }
            if (stateRecordingScrollbar.beginDrag(mouseX, mouseY)) {
                return true;
            }
            if (toggleStateRecordingPage(mouseX, mouseY)) {
                return true;
            }
            return true;
        }
        if (stateRecordingBounds != null && isStateCardVisibleAt(mouseX, mouseY)
                && stateRecordingZoomBounds != null && stateRecordingZoomBounds.contains(mouseX, mouseY)) {
            stateRecordingExpanded = true;
            return true;
        }
        if (stateRecordingBounds != null && isStateCardVisibleAt(mouseX, mouseY)
                && stateRecordingMasterBounds != null && stateRecordingMasterBounds.contains(mouseX, mouseY)) {
            toggleStateRecordingMaster();
            return true;
        }
        if (stateRecordingBounds != null && isStateCardVisibleAt(mouseX, mouseY)
                && stateRecordingScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (toggleStateRecordingPage(mouseX, mouseY)) {
            return true;
        }
        if (compactMode && compactContentBounds != null && compactContentBounds.contains(mouseX, mouseY)
                && compactScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (!compactMode && dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (appearanceScrollbar.beginDrag(mouseX, mouseY) || behaviorScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        ModernMainLayout.Rect themeClip = compactMode ? compactContentBounds : appearanceClipBounds;
        for (int group = 0; group < themeGroupBounds.length; group++) {
            if (contains(themeClip, mouseX, mouseY) && contains(themeGroupBounds[group], mouseX, mouseY)) {
                themeGroupCollapsed[group] = !themeGroupCollapsed[group];
                themeBounds.clear();
                themeIndices.clear();
                return true;
            }
        }
        for (int i = 0; i < themeBounds.size(); i++) {
            if (contains(themeClip, mouseX, mouseY) && themeBounds.get(i).contains(mouseX, mouseY)) {
                ThemeConfigManager.setActiveIndex(themeIndices.get(i));
                ThemeConfigManager.save();
                showStatus(text("gui.general.theme_changed", "主题已切换"));
                return true;
            }
        }
        if (editThemeBounds != null && editThemeBounds.contains(mouseX, mouseY) && routeRequest != null) {
            routeRequest.accept("theme");
            return true;
        }
        if (autoFocusBounds != null && autoFocusBounds.contains(mouseX, mouseY)) {
            MainUiLayoutManager.setModernAutoFocus(!MainUiLayoutManager.isModernAutoFocus());
            return true;
        }
        if (autoPauseBounds != null && autoPauseBounds.contains(mouseX, mouseY)) {
            MainUiLayoutManager.setModernAutoPauseOnMenuOpen(!MainUiLayoutManager.isModernAutoPauseOnMenuOpen());
            return true;
        }
        if (pathStatusHudBounds != null && pathStatusHudBounds.contains(mouseX, mouseY)) {
            MainUiLayoutManager.setModernRunningStatusVisible(!MainUiLayoutManager.isModernRunningStatusVisible());
            return true;
        }
        if (featureStatusHudBounds != null && featureStatusHudBounds.contains(mouseX, mouseY)) {
            MovementFeatureManager.setMasterStatusHudEnabled(!MovementFeatureManager.isMasterStatusHudEnabled());
            return true;
        }
        if (editFeatureHudBounds != null && editFeatureHudBounds.contains(mouseX, mouseY)) {
            GuiOtherFeaturesHudPosition.open(minecraft);
            return true;
        }
        if (resetLayoutBounds != null && resetLayoutBounds.contains(mouseX, mouseY)) {
            resetLayout();
            return true;
        }
        return true;
    }

    private boolean toggleStateRecordingPage(int mouseX, int mouseY) {
        if (!MainUiLayoutManager.isModernStateRecordingEnabled()) {
            return false;
        }
        for (int i = 0; i < statePageBounds.size(); i++) {
            if (statePageBounds.get(i).contains(mouseX, mouseY)) {
                String command = statePageCommands.get(i);
                boolean enabled = MainUiLayoutManager.getModernPageStateRecordingPreference(command);
                MainUiLayoutManager.setModernPageStateRecordingEnabled(command, !enabled);
                return true;
            }
        }
        return false;
    }

    private void toggleStateRecordingMaster() {
        boolean enabled = !MainUiLayoutManager.isModernStateRecordingEnabled();
        MainUiLayoutManager.setModernStateRecordingEnabled(enabled);
        showStatus(text(enabled ? "gui.general.state_recording_enabled" : "gui.general.state_recording_disabled",
                enabled ? "状态记录已启用" : "状态记录已关闭"));
    }

    private boolean isStateCardVisibleAt(int mouseX, int mouseY) {
        if (stateRecordingBounds == null || !stateRecordingBounds.contains(mouseX, mouseY)) {
            return false;
        }
        return compactMode ? contains(compactContentBounds, mouseX, mouseY)
                : contains(behaviorClipBounds, mouseX, mouseY);
    }

    private void applyDropdownChanges(int previousLanguage, int previousScale, int previousTabOrientation,
            int previousRulePosition, int previousRuleDetail) {
        if (languageDropdown.selectedIndex() != previousLanguage) {
            applyLanguage(languageDropdown.value());
        }
        if (scaleDropdown.selectedIndex() != previousScale) {
            try {
                setUiScalePercent(Integer.parseInt(scaleDropdown.value()));
                showStatus(text("gui.general.ui_scale_applied", "界面缩放已应用"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (tabOrientationDropdown.selectedIndex() != previousTabOrientation) {
            MainUiLayoutManager.setModernTabStripVertical("vertical".equals(tabOrientationDropdown.value()));
            showStatus(text("gui.general.tab_orientation_changed", "标签页方向已更新"));
        }
        if (ruleNavigationPositionDropdown.selectedIndex() != previousRulePosition
                || ruleNavigationDetailDropdown.selectedIndex() != previousRuleDetail) {
            MainUiLayoutManager.setAutoFollowNavigation("top".equals(ruleNavigationPositionDropdown.value()),
                    "text".equals(ruleNavigationDetailDropdown.value()));
            showStatus(text("gui.general.rule_navigation_changed", "规则页导航偏好已更新"));
        }
    }

    private void resetLayout() {
        appearanceRatio = DEFAULT_APPEARANCE_RATIO;
        MainUiLayoutManager.setModernSplitRatio("general.appearance", appearanceRatio);
        if (routeRequest != null) {
            routeRequest.accept("reset_window");
        } else {
            MainUiLayoutManager.resetModernWindowLayout();
            MainUiLayoutManager.setModernSidebarWidth(-1);
            MainUiLayoutManager.setModernTabStripWidth(-1);
            MainUiLayoutManager.setModernTabStripHeight(-1);
            MainUiLayoutManager.setModernHeaderCollapsed(false);
        }
        showStatus(text("gui.general.layout_reset", "窗口布局已恢复默认"));
    }

    private void clearStateMemory() {
        MainUiLayoutManager.clearModernPageStates();
        stateRecordingScroll = 0;
        showStatus(text("gui.general.state_memory_cleared", "设置页状态记忆已清空"));
    }

    private void applyLanguage(String language) {
        if (minecraft == null || minecraft.gameSettings == null) return;
        GuiInventory.prepareForLanguageChange();
        minecraft.gameSettings.language = language;
        boolean applied = ClientTranslationInjector.INSTANCE.applyLanguage(language);
        try {
            minecraft.gameSettings.saveOptions();
        } catch (Throwable ignored) {
        }
        if (applied) {
            GuiInventory.refreshGuiLists();
            pendingLanguage = null;
        } else {
            pendingLanguage = language;
        }
        showStatus(applied ? text("gui.general.language_changed", "语言已切换，布局保持不变") : text("gui.general.language_loading", "语言正在后台加载"));
    }

    private int getUiScalePercent() {
        return DetachedSwingWindowManager.isDetached()
                ? MainUiLayoutManager.getDetachedUiScalePercent()
                : MainUiLayoutManager.getModernUiScalePercent();
    }

    private void setUiScalePercent(int scalePercent) {
        if (DetachedSwingWindowManager.isDetached()) {
            DetachedSwingWindowManager.INSTANCE.setUiScaleFromAction(scalePercent);
        } else {
            MainUiLayoutManager.setModernUiScalePercent(scalePercent);
        }
    }

    private String text(String key, String fallback) {
        String value = I18n.format(key);
        return value == null || value.equals(key) ? fallback : value;
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusUntil = System.currentTimeMillis() + 1800L;
    }

    private void drawCompactAppearance(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.appearance", "外观"), bounds.x, bounds.y,
                ModernUiRenderer.TEXT, bounds.width);
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.theme_profile", "主题方案"), bounds.x,
                bounds.y + 19, ModernUiRenderer.SUBTLE_TEXT, bounds.width);
        themeBounds.clear();
        themeIndices.clear();
        int y = bounds.y + 34;
        y = drawThemeGroups(fontRenderer, bounds, compactContentBounds, y, mouseX, mouseY);
        editThemeBounds = new ModernMainLayout.Rect(bounds.x, y + 2, bounds.width, 22);
        drawButton(fontRenderer, editThemeBounds, text("gui.general.manage_themes", "管理主题方案"), false,
                editThemeBounds.contains(mouseX, mouseY));
    }

    private void drawCompactBehavior(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        int y = bounds.y;
        ModernUiRenderer.drawText(fontRenderer, text("gui.general.interface", "界面与交互"), bounds.x, y,
                ModernUiRenderer.TEXT, bounds.width);
        ModernUiRenderer.drawText(fontRenderer,
                text("gui.general.profile_hint", "当前配置档案：") + getActiveProfileName(), bounds.x, y + 16,
                ModernUiRenderer.MUTED_TEXT, bounds.width);
        drawBehaviorContent(fontRenderer, bounds, y + 34, mouseX, mouseY);
    }

    private void drawBehaviorContent(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int y,
            int mouseX, int mouseY) {
        int labelWidth = Math.min(82, Math.max(48, bounds.width / 4));
        int fullWidth = ModernHoverScrollbar.contentWidth(bounds.width);
        String currentLanguage = minecraft == null || minecraft.gameSettings == null
                ? "zh_cn" : minecraft.gameSettings.language;

        y = drawSectionHeader(fontRenderer, bounds, y, text("gui.general.basic", "基本行为"));
        languageDropdown.setValue(currentLanguage);
        scaleDropdown.setValue(String.valueOf(getUiScalePercent()));
        languageBounds.clear();
        scaleBounds.clear();
        y = drawLabeledDropdown(fontRenderer, bounds, y, labelWidth,
                text("gui.general.language", "语言"), languageDropdown, languageBounds, mouseX, mouseY);
        y = drawLabeledDropdown(fontRenderer, bounds, y, labelWidth,
                text("gui.general.ui_scale", "界面缩放"), scaleDropdown, scaleBounds, mouseX, mouseY);

        autoFocusBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawToggleRow(fontRenderer, autoFocusBounds, text("gui.general.auto_focus", "自动聚焦搜索框"),
                MainUiLayoutManager.isModernAutoFocus(), mouseX, mouseY);
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;
        autoPauseBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawToggleRow(fontRenderer, autoPauseBounds, text("gui.general.auto_pause", "打开菜单时自动暂停脚本"),
                MainUiLayoutManager.isModernAutoPauseOnMenuOpen(), mouseX, mouseY);
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;

        y += 4;
        y = drawSectionHeader(fontRenderer, bounds, y, text("gui.general.status_feedback", "状态反馈"));
        pathStatusHudBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawToggleRow(fontRenderer, pathStatusHudBounds,
                text("gui.general.path_status_hud", "路径运行状态 HUD"),
                MainUiLayoutManager.isModernRunningStatusVisible(), mouseX, mouseY);
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;
        featureStatusHudBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawToggleRow(fontRenderer, featureStatusHudBounds,
                text("gui.general.feature_status_hud", "其他功能状态 HUD"),
                MovementFeatureManager.isMasterStatusHudEnabled(), mouseX, mouseY);
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;
        editFeatureHudBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawButton(fontRenderer, editFeatureHudBounds,
                text("gui.general.edit_feature_hud", "调整其他功能 HUD 位置"), false,
                editFeatureHudBounds.contains(mouseX, mouseY));
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;

        y += 4;
        y = drawSectionHeader(fontRenderer, bounds, y, text("gui.general.layout_navigation", "布局与导航"));
        tabOrientationDropdown.setValue(MainUiLayoutManager.isModernTabStripVertical() ? "vertical" : "horizontal");
        ruleNavigationPositionDropdown.setValue(MainUiLayoutManager.isAutoFollowNavigationTop() ? "top" : "side");
        ruleNavigationDetailDropdown.setValue(MainUiLayoutManager.isAutoFollowNavigationText() ? "text" : "icon");
        y = drawLabeledDropdown(fontRenderer, bounds, y, labelWidth,
                text("gui.general.tab_orientation", "标签页方向"), tabOrientationDropdown, null, mouseX, mouseY);
        y = drawLabeledDropdown(fontRenderer, bounds, y, labelWidth,
                text("gui.general.rule_nav_position", "规则导航位置"), ruleNavigationPositionDropdown, null, mouseX, mouseY);
        y = drawLabeledDropdown(fontRenderer, bounds, y, labelWidth,
                text("gui.general.rule_nav_display", "规则导航显示"), ruleNavigationDetailDropdown, null, mouseX, mouseY);
        resetLayoutBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, SETTINGS_ROW_HEIGHT);
        drawButton(fontRenderer, resetLayoutBounds, text("gui.general.reset_layout", "恢复窗口布局"), false,
                resetLayoutBounds.contains(mouseX, mouseY));
        y += SETTINGS_ROW_HEIGHT + SETTINGS_GAP;

        if (System.currentTimeMillis() < statusUntil && !statusMessage.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, bounds.x, y + 1, ModernUiRenderer.SUCCESS,
                    Math.max(20, bounds.width));
        }
        y += 22 + 4;

        if (!stateRecordingExpanded) {
            stateRecordingBounds = new ModernMainLayout.Rect(bounds.x, y, fullWidth, STATE_CARD_HEIGHT);
            drawStateRecordingCard(fontRenderer, stateRecordingBounds, mouseX, mouseY, false);
        } else {
            stateRecordingBounds = null;
            stateRecordingClipBounds = null;
            stateRecordingZoomBounds = null;
            stateRecordingMasterBounds = null;
            stateRecordingClearBounds = null;
            stateRecordingSearchBounds = null;
        }
    }

    private int drawSectionHeader(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int y, String label) {
        ModernUiRenderer.drawRoundedRect(bounds.x, y + 4, 3, 12, 1, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 9, y + 2, ModernUiRenderer.TEXT,
                Math.max(20, bounds.width - 9));
        return y + SETTINGS_SECTION_HEIGHT;
    }

    private int drawLabeledDropdown(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int y, int labelWidth,
            String label, ModernDropdown dropdown, List<ModernMainLayout.Rect> exposedBounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, labelWidth - 6));
        int controlX = bounds.x + labelWidth;
        int controlWidth = ModernHoverScrollbar.contentWidth(Math.max(1, bounds.width - labelWidth));
        ModernMainLayout.Rect control = new ModernMainLayout.Rect(controlX, y, controlWidth, SETTINGS_ROW_HEIGHT);
        if (exposedBounds != null) {
            exposedBounds.add(control);
        }
        dropdown.drawButton(fontRenderer, control, mouseX, mouseY);
        return y + SETTINGS_ROW_HEIGHT + SETTINGS_GAP;
    }

    private int behaviorContentHeight() {
        int row = SETTINGS_ROW_HEIGHT + SETTINGS_GAP;
        return 3 + SETTINGS_SECTION_HEIGHT + row * 4 + 4
                + SETTINGS_SECTION_HEIGHT + row * 3 + 4
                + SETTINGS_SECTION_HEIGHT + row * 3 + SETTINGS_ROW_HEIGHT + SETTINGS_GAP
                + 22 + 4 + STATE_CARD_HEIGHT;
    }

    private String getActiveProfileName() {
        String name = ProfileManager.getActiveProfileName();
        return name == null || name.trim().isEmpty() ? text("gui.general.unnamed_profile", "未命名档案") : name;
    }

    private void drawDropdownMenus(FontRenderer fontRenderer, ModernMainLayout.Rect host, int mouseX, int mouseY) {
        languageDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
        scaleDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
        tabOrientationDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
        ruleNavigationPositionDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
        ruleNavigationDetailDropdown.drawMenu(fontRenderer, host, mouseX, mouseY);
    }

    @Override public boolean keyTyped(char typedChar, int keyCode) {
        if (stateRecordingSearch != null && stateRecordingSearch.isFocused()) {
            if (stateRecordingSearch.textboxKeyTyped(typedChar, keyCode)) {
                stateRecordingScroll = 0;
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0) return false;
        if (compactScrollbar.isDragging()) {
            compactScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (stateRecordingScrollbar.isDragging()) {
            stateRecordingScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (appearanceScrollbar.isDragging()) {
            appearanceScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (behaviorScrollbar.isDragging()) {
            behaviorScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (draggingDivider && !compactMode && panelBounds != null) {
            int total = Math.max(2, panelBounds.width - 32 - 10);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - panelBounds.x - 16 - 5, 178, 250, 122, 160);
            appearanceRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state != 0) return false;
        boolean handled = compactScrollbar.isDragging() || stateRecordingScrollbar.isDragging()
                || appearanceScrollbar.isDragging() || behaviorScrollbar.isDragging() || draggingDivider;
        compactScrollbar.endDrag();
        stateRecordingScrollbar.endDrag();
        appearanceScrollbar.endDrag();
        behaviorScrollbar.endDrag();
        if (draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("general.appearance", appearanceRatio);
        }
        draggingDivider = false;
        return handled;
    }

    @Override public boolean handleEscape() {
        if (stateRecordingExpanded) {
            stateRecordingExpanded = false;
            stateRecordingScrollbar.endDrag();
            return true;
        }
        if (languageDropdown.isOpen() || scaleDropdown.isOpen() || tabOrientationDropdown.isOpen()
                || ruleNavigationPositionDropdown.isOpen() || ruleNavigationDetailDropdown.isOpen()) {
            languageDropdown.close();
            scaleDropdown.close();
            tabOrientationDropdown.close();
            ruleNavigationPositionDropdown.close();
            ruleNavigationDetailDropdown.close();
            return true;
        }
        if (compactScrollbar.isDragging() || stateRecordingScrollbar.isDragging()
                || appearanceScrollbar.isDragging() || behaviorScrollbar.isDragging()
                || draggingDivider) {
            compactScrollbar.endDrag();
            stateRecordingScrollbar.endDrag();
            appearanceScrollbar.endDrag();
            behaviorScrollbar.endDrag();
            draggingDivider = false;
            return true;
        }
        return false;
    }

    @Override public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) return false;
        int amount = wheel > 0 ? -34 : 34;
        if (contains(stateRecordingClipBounds, mouseX, mouseY) && stateRecordingMaxScroll > 0) {
            int previous = stateRecordingScroll;
            stateRecordingScroll = clamp(stateRecordingScroll + amount, 0, stateRecordingMaxScroll);
            return previous != stateRecordingScroll;
        }
        if (stateRecordingExpanded) {
            return true;
        }
        if (compactMode && contains(compactContentBounds, mouseX, mouseY) && compactMaxScroll > 0) {
            int previous = compactScroll;
            compactScroll = clamp(compactScroll + amount, 0, compactMaxScroll);
            return previous != compactScroll;
        }
        if (contains(appearanceClipBounds, mouseX, mouseY) && appearanceMaxScroll > 0) {
            int previous = appearanceScroll;
            appearanceScroll = clamp(appearanceScroll + amount, 0, appearanceMaxScroll);
            return previous != appearanceScroll;
        }
        if (contains(behaviorClipBounds, mouseX, mouseY) && behaviorMaxScroll > 0) {
            int previous = behaviorScroll;
            behaviorScroll = clamp(behaviorScroll + amount, 0, behaviorMaxScroll);
            return previous != behaviorScroll;
        }
        return false;
    }

    @Override public boolean containsContent(int mouseX, int mouseY) { return panelBounds != null && panelBounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return hoveredTooltip; }
    @Override public void discardDraft() { }

    @Override
    public List<GuiElementInspector.GuiElementInfo> getMcpGuiElements(String pathPrefix) {
        List<GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        List<GuiElementInspector.GuiElementInfo> reflected = ModernSettingsTab.super.getMcpGuiElements();
        String prefix = pathPrefix == null ? "" : pathPrefix;
        for (GuiElementInspector.GuiElementInfo element : reflected) {
            if (element == null) continue;
            String path = element.getPath();
            while (path.startsWith("/")) path = path.substring(1);
            if (!path.startsWith("screen/")) path = prefix + path;
            GuiElementInspector.GuiElementInfo normalized = element.withPath(path);
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith("/autofocusbounds")) {
                normalized = normalized.withSemantics("toggle", String.valueOf(MainUiLayoutManager.isModernAutoFocus()),
                        true, true, java.util.Arrays.asList("click", "set"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/autopausebounds")) {
                normalized = normalized.withSemantics("toggle",
                        String.valueOf(MainUiLayoutManager.isModernAutoPauseOnMenuOpen()), true, true,
                        java.util.Arrays.asList("click", "set"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/staterecordingmasterbounds")) {
                normalized = normalized.withSemantics("toggle",
                        String.valueOf(MainUiLayoutManager.isModernStateRecordingEnabled()), true, true,
                        java.util.Arrays.asList("click", "set"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/pathstatushudbounds")) {
                normalized = normalized.withSemantics("toggle",
                        String.valueOf(MainUiLayoutManager.isModernRunningStatusVisible()), true, true,
                        java.util.Arrays.asList("click", "set"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/featurestatushudbounds")) {
                normalized = normalized.withSemantics("toggle",
                        String.valueOf(MovementFeatureManager.isMasterStatusHudEnabled()), true, true,
                        java.util.Arrays.asList("click", "set"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/editfeaturehudbounds") || lower.endsWith("/resetlayoutbounds")
                    || lower.endsWith("/staterecordingclearbounds")) {
                normalized = normalized.withSemantics("action", "", true, false,
                        java.util.Collections.singletonList("click"), java.util.Collections.<String>emptyList());
            } else if (lower.endsWith("/themegroupbounds/0") || lower.endsWith("/themegroupbounds/1")) {
                normalized = normalized.withSemantics("action", "", true, false,
                        java.util.Collections.singletonList("click"), java.util.Collections.<String>emptyList());
            }
            result.add(normalized);
        }
        return result;
    }

    @Override
    public boolean setMcpValue(String target, String value) {
        String normalized = target == null ? "" : target.toLowerCase(java.util.Locale.ROOT);
        Boolean requested = parseMcpBoolean(value);
        if (requested != null && (normalized.contains("/autofocus") || normalized.contains("autofocusbounds"))) {
            MainUiLayoutManager.setModernAutoFocus(requested.booleanValue());
            return true;
        }
        if (requested != null && (normalized.contains("/autopause") || normalized.contains("autopausebounds"))) {
            MainUiLayoutManager.setModernAutoPauseOnMenuOpen(requested.booleanValue());
            return true;
        }
        if (requested != null && (normalized.contains("/staterecordingmaster")
                || normalized.contains("running_status"))) {
            if (normalized.contains("running_status")) {
                MainUiLayoutManager.setModernRunningStatusVisible(requested.booleanValue());
            } else {
                MainUiLayoutManager.setModernStateRecordingEnabled(requested.booleanValue());
            }
            return true;
        }
        if (requested != null && (normalized.contains("/pathstatushud")
                || normalized.contains("path_status_hud"))) {
            MainUiLayoutManager.setModernRunningStatusVisible(requested.booleanValue());
            return true;
        }
        if (requested != null && (normalized.contains("/featurestatushud")
                || normalized.contains("feature_status_hud"))) {
            MovementFeatureManager.setMasterStatusHudEnabled(requested.booleanValue());
            return true;
        }
        if (normalized.endsWith("/languagedropdown") || normalized.equals("languagedropdown")) {
            languageDropdown.setValue(value);
            boolean matched = value != null && value.equalsIgnoreCase(languageDropdown.value());
            if (matched) applyLanguage(languageDropdown.value());
            return matched;
        }
        if (normalized.endsWith("/scaledropdown") || normalized.equals("scaledropdown")) {
            scaleDropdown.setValue(value);
            boolean matched = value != null && value.equalsIgnoreCase(scaleDropdown.value());
            if (matched) {
                try { setUiScalePercent(Integer.parseInt(scaleDropdown.value())); }
                catch (NumberFormatException ignored) { return false; }
            }
            return matched;
        }
        if (normalized.endsWith("/taborientationdropdown") || normalized.equals("taborientationdropdown")) {
            tabOrientationDropdown.setValue(value);
            boolean matched = value != null && value.equalsIgnoreCase(tabOrientationDropdown.value());
            if (matched) {
                MainUiLayoutManager.setModernTabStripVertical("vertical".equals(tabOrientationDropdown.value()));
            }
            return matched;
        }
        if (normalized.endsWith("/rulenavigationpositiondropdown")
                || normalized.equals("rulenavigationpositiondropdown")) {
            ruleNavigationPositionDropdown.setValue(value);
            boolean matched = value != null && value.equalsIgnoreCase(ruleNavigationPositionDropdown.value());
            if (matched) {
                MainUiLayoutManager.setAutoFollowNavigation("top".equals(ruleNavigationPositionDropdown.value()),
                        "text".equals(ruleNavigationDetailDropdown.value()));
            }
            return matched;
        }
        if (normalized.endsWith("/rulenavigationdetaildropdown")
                || normalized.equals("rulenavigationdetaildropdown")) {
            ruleNavigationDetailDropdown.setValue(value);
            boolean matched = value != null && value.equalsIgnoreCase(ruleNavigationDetailDropdown.value());
            if (matched) {
                MainUiLayoutManager.setAutoFollowNavigation("top".equals(ruleNavigationPositionDropdown.value()),
                        "text".equals(ruleNavigationDetailDropdown.value()));
            }
            return matched;
        }
        return ModernSettingsTab.super.setMcpValue(target, value);
    }

    private static Boolean parseMcpBoolean(String value) {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }

    private ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        if (bounds == null) return new ModernMainLayout.Rect(0, 0, 1, 1);
        return new ModernMainLayout.Rect(bounds.x + 10, bounds.y + 8, Math.max(1, bounds.width - 20),
                Math.max(1, bounds.height - 16));
    }

    private int themeListHeight() {
        int height = 2 * (ModernTreeGuide.GROUP_HEIGHT + ModernTreeGuide.GAP);
        for (ThemeProfile profile : ThemeConfigManager.getProfiles()) {
            if (profile != null && !themeGroupCollapsed[ThemeConfigManager.isBuiltInProfile(profile) ? 0 : 1]) {
                height += ModernTreeGuide.ITEM_HEIGHT + ModernTreeGuide.GAP;
            }
        }
        return height;
    }

    private int drawThemeGroups(FontRenderer fontRenderer, ModernMainLayout.Rect list,
            ModernMainLayout.Rect clip, int y, int mouseX, int mouseY) {
        List<ThemeProfile> profiles = ThemeConfigManager.getProfiles();
        int active = ThemeConfigManager.getActiveIndex();
        for (int groupIndex = 0; groupIndex < 2; groupIndex++) {
            boolean builtIn = groupIndex == 0;
            int count = 0;
            for (ThemeProfile profile : profiles) {
                if (profile != null && ThemeConfigManager.isBuiltInProfile(profile) == builtIn) count++;
            }
            ModernMainLayout.Rect group = ModernTreeGuide.groupRow(list, y);
            themeGroupBounds[groupIndex] = group;
            boolean hovered = clip.contains(mouseX, mouseY) && group.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(group.x, group.y, group.width, group.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawChevron(group.x + 8, group.y + 7, themeGroupCollapsed[groupIndex], ModernUiRenderer.SUBTLE_TEXT);
            ModernUiRenderer.drawText(fontRenderer, builtIn ? text("gui.general.builtin_themes", "内置主题")
                    : text("gui.general.custom_themes", "自定义主题"), group.x + 20, group.y + 7,
                    ModernUiRenderer.TEXT, Math.max(1, group.width - 48));
            ModernUiRenderer.drawText(fontRenderer, String.valueOf(count), group.right() - 23, group.y + 7,
                    ModernUiRenderer.MUTED_TEXT, 18);
            y = ModernTreeGuide.nextY(y, ModernTreeGuide.GROUP_HEIGHT);
            if (themeGroupCollapsed[groupIndex]) continue;
            for (int i = 0; i < profiles.size(); i++) {
                ThemeProfile profile = profiles.get(i);
                if (profile == null || ThemeConfigManager.isBuiltInProfile(profile) != builtIn) continue;
                ModernMainLayout.Rect row = ModernTreeGuide.itemRow(list, y);
                ModernTreeGuide.drawChild(list.x, 0, group, row);
                boolean selected = i == active;
                drawButton(fontRenderer, row, "", selected, clip.contains(mouseX, mouseY) && row.contains(mouseX, mouseY));
                ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 12,
                        selected ? ModernUiRenderer.ACCENT : builtIn ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT);
                ModernUiRenderer.drawText(fontRenderer, profile.name == null
                        ? text("gui.general.unnamed_theme", "未命名主题") : profile.name, row.x + 20, row.y + 11,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, row.width - 25));
                if (intersects(row, clip)) {
                    themeBounds.add(row);
                    themeIndices.add(i);
                }
                y = ModernTreeGuide.nextY(y, ModernTreeGuide.ITEM_HEIGHT);
            }
        }
        return y;
    }

    private int themeContentHeight() {
        return 34 + themeListHeight() + 40;
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
