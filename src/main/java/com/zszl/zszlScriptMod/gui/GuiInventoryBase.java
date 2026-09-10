// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/GuiInventory.java
package com.zszl.zszlScriptMod.gui;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.components.GuiTextInput;
import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.modern.ModernOtherFeatureSettingsTab;

import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.FeatureDef;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.GroupDef;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.UpdateChecker;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

abstract class GuiInventoryBase {

    protected static final int BASE_SIDE_BUTTON_COLUMN_WIDTH = 80;
    protected static final int BASE_TOTAL_WIDTH = 420;
    protected static final int BASE_TOTAL_HEIGHT = 280;
    protected static final int BASE_GAP = 10;
    protected static final int BASE_PADDING = 5;
    protected static final int BASE_CATEGORY_PANEL_WIDTH = 110;
    protected static final int BASE_CATEGORY_BUTTON_WIDTH = 96;
    protected static final int BASE_CATEGORY_BUTTON_HEIGHT = 22;
    protected static final int BASE_ITEM_BUTTON_WIDTH = 84;
    protected static final int BASE_ITEM_BUTTON_HEIGHT = 22;
    protected static final int BASE_TOP_BUTTON_WIDTH = 60;
    protected static final int BASE_TOP_BUTTON_HEIGHT = 16;
    protected static final int BASE_CONTENT_X_OFFSET = 122;
    protected static final int CATEGORY_PANEL_MIN_BASE_WIDTH = 92;
    protected static final int CATEGORY_PANEL_MAX_BASE_WIDTH = 180;
    protected static final String UNCATEGORIZED_SECTION_TITLE = "gui.modern.invbase.u001";
    protected static final String ILLEGAL_CATEGORY_NAME_CHARS = "\\/:*?\"<>|";
    protected static final long RECENT_OPEN_HIGHLIGHT_WINDOW_MS = 10L * 60L * 1000L;
    protected static final String SEARCH_SCOPE_CURRENT_SUBCATEGORY = "current_subcategory";
    protected static final String SEARCH_SCOPE_CURRENT_CATEGORY = "current_category";
    protected static final String SEARCH_SCOPE_ALL_CATEGORIES = "all_categories";

    protected static class OverlayMetrics {
        float scale;
        int sideButtonColumnWidth;
        int totalWidth;
        int totalHeight;
        int x;
        int y;

        int gap;
        int padding;

        int pathManagerButtonWidth;
        int stopButtonWidth;
        int topButtonHeight;

        int topBarHeight;
        int contentStartY;

        int categoryPanelWidth;
        int categoryButtonWidth;
        int categoryButtonHeight;
        int categoryItemHeight;

        int contentPanelX;
        int contentPanelRight;
        int categoryDividerX;
        int categoryDividerWidth;

        int itemButtonWidth;
        int itemButtonHeight;

        int pageButtonWidth;
        int autoPauseButtonWidth;

        int sideButtonWidth;
        int sideButtonHeight;
    }

    protected static int scaleUi(int base, float scale) {
        return Math.max(1, Math.round(base * scale));
    }

    protected static float computeUiScale(int screenWidth, int screenHeight) {
        float sx = screenWidth / 460.0f;
        float sy = screenHeight / 300.0f;
        float s = Math.min(1.0f, Math.min(sx, sy));
        return MathHelper.clamp(s, 0.72f, 1.0f);
    }

    protected static String buildOverlayTitle() {
        if (otherFeaturesScreenActive) {
            return I18n.format("gui.inventory.other_features.title");
        }
        return "";
    }

    protected static List<String> buildOverlayHeaderLines() {
        List<String> lines = new ArrayList<>();
        if (otherFeaturesScreenActive) {
            lines.add(I18n.format("gui.inventory.other_features.title"));
            return lines;
        }

        lines.add(I18n.format("gui.inventory.profile", ProfileManager.getActiveProfileName()));
        lines.add(I18n.format("gui.inventory.loop.progress", Math.max(0, loopCounter), formatLoopTargetForHeader()));
        return lines;
    }

    protected static String formatLoopTargetForHeader() {
        if (loopCount < 0) {
            return "∞";
        }
        return String.valueOf(Math.max(0, loopCount));
    }

    protected static OverlayMetrics computeOverlayMetrics(int screenWidth, int screenHeight, FontRenderer fontRenderer,
            String title) {
        OverlayMetrics m = new OverlayMetrics();
        m.scale = computeUiScale(screenWidth, screenHeight);
        m.gap = scaleUi(BASE_GAP, m.scale);
        m.padding = scaleUi(BASE_PADDING, m.scale);

        m.sideButtonColumnWidth = scaleUi(BASE_SIDE_BUTTON_COLUMN_WIDTH, m.scale);
        m.totalWidth = scaleUi(BASE_TOTAL_WIDTH, m.scale);
        m.totalHeight = scaleUi(BASE_TOTAL_HEIGHT, m.scale);

        int maxUsableWidth = screenWidth - m.padding * 2;
        int maxPanelWidth = maxUsableWidth - m.sideButtonColumnWidth - m.gap;
        m.totalWidth = Math.max(260, Math.min(m.totalWidth, maxPanelWidth));
        m.totalHeight = Math.max(190, Math.min(m.totalHeight, screenHeight - m.padding * 2));

        m.x = (screenWidth - m.totalWidth) / 2 + (m.sideButtonColumnWidth / 2);
        m.y = (screenHeight - m.totalHeight) / 2;

        m.pathManagerButtonWidth = scaleUi(BASE_TOP_BUTTON_WIDTH, m.scale);
        m.stopButtonWidth = scaleUi(BASE_TOP_BUTTON_WIDTH, m.scale);
        m.topButtonHeight = scaleUi(BASE_TOP_BUTTON_HEIGHT, m.scale);

        if (!otherFeaturesScreenActive) {
            int lineHeight = fontRenderer.FONT_HEIGHT + scaleUi(2, m.scale);
            m.topBarHeight = Math.max(scaleUi(30, m.scale), lineHeight * 2);
            if (GuiInventory.isCustomCategorySelection() && GuiInventory.isCustomSearchExpanded()) {
                m.topBarHeight = Math.max(m.topBarHeight, m.topButtonHeight * 2 + scaleUi(10, m.scale));
            }
        } else {
            int pathManagerButtonX = m.x + m.totalWidth - m.pathManagerButtonWidth - m.padding;
            int stopButtonX = pathManagerButtonX - m.stopButtonWidth - m.padding;
            int titleAreaStartX = m.x + scaleUi(8, m.scale);
            int titleAreaEndX = stopButtonX - scaleUi(8, m.scale)
                    - GuiInventory.getCustomSearchHeaderReservedWidth(fontRenderer, m.scale);
            int titleAreaWidth = Math.max(80, titleAreaEndX - titleAreaStartX);
            List<String> titleLines = fontRenderer.listFormattedStringToWidth(title, titleAreaWidth);
            int titleTotalHeight = titleLines.size() * (fontRenderer.FONT_HEIGHT + scaleUi(2, m.scale));
            m.topBarHeight = Math.max(scaleUi(24, m.scale), titleTotalHeight);
        }
        m.contentStartY = m.y + m.topBarHeight + scaleUi(6, m.scale);

        int storedCategoryBaseWidth = MainUiLayoutManager.getCategoryPanelBaseWidth();
        int categoryBaseWidth = storedCategoryBaseWidth > 0 ? clampCategoryPanelBaseWidth(storedCategoryBaseWidth)
                : computeAutoCategoryPanelBaseWidth(fontRenderer);
        m.categoryPanelWidth = scaleUi(categoryBaseWidth, m.scale);
        int categoryButtonMaxWidth = Math.max(scaleUi(40, m.scale),
                m.categoryPanelWidth - m.padding * 2 - scaleUi(8, m.scale));
        m.categoryButtonWidth = Math.min(Math.max(scaleUi(BASE_CATEGORY_BUTTON_WIDTH, m.scale),
                m.categoryPanelWidth - m.padding * 2 - scaleUi(12, m.scale)), categoryButtonMaxWidth);
        m.categoryButtonHeight = scaleUi(BASE_CATEGORY_BUTTON_HEIGHT, m.scale);
        m.categoryItemHeight = m.categoryButtonHeight + m.padding;

        m.categoryDividerWidth = Math.max(5, scaleUi(6, m.scale));
        m.contentPanelX = m.x + m.padding + m.categoryPanelWidth + m.gap;
        m.categoryDividerX = m.contentPanelX - (m.gap / 2) - (m.categoryDividerWidth / 2);
        m.contentPanelRight = m.x + m.totalWidth - m.padding;

        m.itemButtonWidth = scaleUi(BASE_ITEM_BUTTON_WIDTH, m.scale);
        m.itemButtonHeight = scaleUi(BASE_ITEM_BUTTON_HEIGHT, m.scale);

        m.pageButtonWidth = scaleUi(60, m.scale);
        m.autoPauseButtonWidth = scaleUi(80, m.scale);

        m.sideButtonWidth = scaleUi(70, m.scale);
        m.sideButtonHeight = scaleUi(20, m.scale);

        int sideButtonLeft = m.x - m.sideButtonColumnWidth - m.gap;
        int minX = m.padding + m.sideButtonColumnWidth + m.gap;
        if (sideButtonLeft < m.padding) {
            m.x = Math.max(minX, m.x + (m.padding - sideButtonLeft));
        }
        if (m.x + m.totalWidth > screenWidth - m.padding) {
            m.x = Math.max(minX, screenWidth - m.padding - m.totalWidth);
        }

        return m;
    }

    protected static int colorChangeTicker = 0;
    protected static final List<TextFormatting> RAINBOW_COLORS = Arrays.asList(TextFormatting.RED, TextFormatting.GOLD,
            TextFormatting.YELLOW, TextFormatting.GREEN, TextFormatting.AQUA, TextFormatting.BLUE,
            TextFormatting.LIGHT_PURPLE);

    protected static String sLastCategory = "gui.inventory.category.common";
    protected static int sLastPage = 0;
    protected static final Map<String, Integer> CATEGORY_PAGE_MAP = new HashMap<>();
    protected static int currentPage = sLastPage;
    protected static String currentCategory = sLastCategory;
    protected static List<String> categories = new ArrayList<>();
    protected static final Map<String, List<String>> categoryItems = new HashMap<>();
    protected static final Map<String, List<String>> categoryItemNames = new HashMap<>();
    protected static final Map<String, String> itemTooltips = new HashMap<>();
    protected static final Set<String> otherFeatureCategories = new HashSet<>();
    protected static final int COMMON_ROWS_PER_PAGE = 6;
    private static String pendingLanguageCategoryKey;
    private static String pendingLanguageSubCategory;

    protected static class GroupedItemSection {
        final String key;
        final String title;
        final List<String> commands;

        GroupedItemSection(String key, String title, List<String> commands) {
            this.key = key;
            this.title = title;
            this.commands = new ArrayList<>(commands);
        }
    }

    protected static class CommonContentRow {
        final boolean header;
        final String sectionKey;
        final String title;
        final boolean expanded;
        final List<String> commands;

        protected CommonContentRow(boolean header, String sectionKey, String title, boolean expanded,
                List<String> commands) {
            this.header = header;
            this.sectionKey = sectionKey;
            this.title = title;
            this.expanded = expanded;
            this.commands = commands == null ? Collections.emptyList() : new ArrayList<>(commands);
        }

        static CommonContentRow header(String sectionKey, String title, boolean expanded) {
            return new CommonContentRow(true, sectionKey, title, expanded, Collections.emptyList());
        }

        static CommonContentRow items(String sectionKey, List<String> commands) {
            return new CommonContentRow(false, sectionKey, "", false, commands);
        }
    }

    protected static class CategoryTreeRow {
        final String category;
        final String subCategory;
        final boolean systemCategory;
        Rectangle bounds;

        protected CategoryTreeRow(String category, String subCategory, boolean systemCategory) {
            this.category = category;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.systemCategory = systemCategory;
        }

        boolean isSubCategory() {
            return !subCategory.isEmpty();
        }

        boolean isCustomCategoryRoot() {
            return !systemCategory && subCategory.isEmpty();
        }

        boolean isDroppableTarget() {
            return !systemCategory;
        }

        String getPageKey() {
            return isSubCategory() ? category + "::" + subCategory : category;
        }
    }

    protected static class SequenceCardRenderInfo {
        final PathSequence sequence;
        final Rectangle bounds;
        final String displayName;
        final String secondaryText;
        final String tooltip;

        protected SequenceCardRenderInfo(PathSequence sequence, Rectangle bounds, String displayName,
                String secondaryText, String tooltip) {
            this.sequence = sequence;
            this.bounds = bounds;
            this.displayName = displayName;
            this.secondaryText = secondaryText;
            this.tooltip = tooltip;
        }
    }

    protected static class CustomSectionRenderInfo {
        final String key;
        final String title;
        final String subCategory;
        final Rectangle bounds;

        protected CustomSectionRenderInfo(String key, String title, String subCategory, Rectangle bounds) {
            this.key = key;
            this.title = title;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.bounds = bounds;
        }
    }

    protected static class CustomSequenceDropTarget {
        final String category;
        final String subCategory;
        final Rectangle bounds;

        protected CustomSequenceDropTarget(String category, String subCategory, Rectangle bounds) {
            this.category = category == null ? "" : category;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.bounds = bounds;
        }

        boolean isSubCategory() {
            return !normalizeText(subCategory).isEmpty();
        }

        boolean matches(String targetCategory, String targetSubCategory) {
            return normalizeText(category).equals(normalizeText(targetCategory))
                    && normalizeText(subCategory).equalsIgnoreCase(normalizeText(targetSubCategory));
        }
    }

    protected static class CustomButtonRenderInfo {
        final String action;
        final String category;
        final String subCategory;
        final Rectangle bounds;

        protected CustomButtonRenderInfo(String action, String category, String subCategory, Rectangle bounds) {
            this.action = action;
            this.category = category == null ? "" : category;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.bounds = bounds;
        }
    }

    protected static class CustomSectionModel {
        final String key;
        final String category;
        final String title;
        final String subCategory;
        final List<PathSequence> sequences;
        final String statsLabel;

        protected CustomSectionModel(String key, String category, String title, String subCategory,
                List<PathSequence> sequences) {
            this.key = key;
            this.category = category == null ? "" : category;
            this.title = title;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.sequences = sequences == null ? Collections.emptyList() : new ArrayList<>(sequences);
            this.statsLabel = GuiInventory.buildCustomSectionStatsLabel(this.sequences);
        }
    }

    protected static class CustomSectionChunk {
        final CustomSectionModel model;
        final List<PathSequence> pageSequences;
        final boolean continuation;

        protected CustomSectionChunk(CustomSectionModel model, List<PathSequence> pageSequences, boolean continuation) {
            this.model = model;
            this.pageSequences = pageSequences == null ? Collections.emptyList() : new ArrayList<>(pageSequences);
            this.continuation = continuation;
        }
    }

    protected static class CustomGridMetrics {
        int startX;
        int startY;
        int width;
        int height;
        int gap;
        int columns;
        int rowsPerPage;
        int cardWidth;
        int cardHeight;
        int pageSize;
    }

    protected static class CustomPageLayout {
        final CustomGridMetrics grid;
        final List<CustomSectionChunk> sections;
        final int totalPages;
        final int searchToggleX;
        final int searchToggleY;
        final int searchToggleWidth;
        final int searchToggleHeight;
        final int searchFieldX;
        final int searchFieldY;
        final int searchFieldWidth;
        final int searchFieldHeight;
        final int searchScopeX;
        final int searchScopeY;
        final int searchScopeWidth;
        final int searchScopeHeight;
        final int toolbarY;
        final int toolbarHeight;

        protected CustomPageLayout(CustomGridMetrics grid, List<CustomSectionChunk> sections, int totalPages,
                int searchToggleX, int searchToggleY, int searchToggleWidth, int searchToggleHeight, int searchFieldX,
                int searchFieldY, int searchFieldWidth, int searchFieldHeight, int searchScopeX, int searchScopeY,
                int searchScopeWidth, int searchScopeHeight, int toolbarY, int toolbarHeight) {
            this.grid = grid;
            this.sections = sections == null ? Collections.emptyList() : new ArrayList<>(sections);
            this.totalPages = Math.max(1, totalPages);
            this.searchToggleX = searchToggleX;
            this.searchToggleY = searchToggleY;
            this.searchToggleWidth = searchToggleWidth;
            this.searchToggleHeight = searchToggleHeight;
            this.searchFieldX = searchFieldX;
            this.searchFieldY = searchFieldY;
            this.searchFieldWidth = searchFieldWidth;
            this.searchFieldHeight = searchFieldHeight;
            this.searchScopeX = searchScopeX;
            this.searchScopeY = searchScopeY;
            this.searchScopeWidth = searchScopeWidth;
            this.searchScopeHeight = searchScopeHeight;
            this.toolbarY = toolbarY;
            this.toolbarHeight = toolbarHeight;
        }
    }

    protected static class ContextMenuItem {
        final String label;
        final Runnable action;
        final List<ContextMenuItem> children = new ArrayList<>();
        boolean enabled = true;
        boolean selected = false;
        String shortcut = "";
        Runnable secondaryAction;
        String hoverTitle = "";
        String hoverDescription = "";
        String hoverLeftAction = "";
        String hoverRightAction = "";
        boolean separator;

        protected ContextMenuItem(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        static ContextMenuItem separator() {
            ContextMenuItem item = new ContextMenuItem("", null);
            item.separator = true;
            item.enabled = false;
            return item;
        }

        ContextMenuItem child(ContextMenuItem item) {
            if (item != null) {
                children.add(item);
            }
            return this;
        }

        ContextMenuItem enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        ContextMenuItem selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        ContextMenuItem shortcut(String shortcut) {
            this.shortcut = shortcut == null ? "" : shortcut;
            return this;
        }

        ContextMenuItem secondaryAction(Runnable action) {
            this.secondaryAction = action;
            return this;
        }

        ContextMenuItem hoverPanel(String title, String description, String leftAction, String rightAction) {
            this.hoverTitle = title == null ? "" : title;
            this.hoverDescription = description == null ? "" : description;
            this.hoverLeftAction = leftAction == null ? "" : leftAction;
            this.hoverRightAction = rightAction == null ? "" : rightAction;
            return this;
        }

        boolean hasHoverPanel() {
            return !hoverTitle.isEmpty() || !hoverDescription.isEmpty()
                    || !hoverLeftAction.isEmpty() || !hoverRightAction.isEmpty();
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    protected static class ContextMenuLayer {
        final List<ContextMenuItem> items;
        final List<Rectangle> itemBounds = new ArrayList<>();
        Rectangle bounds;
        int x;
        int y;
        int width;

        protected ContextMenuLayer(List<ContextMenuItem> items) {
            this.items = items;
        }
    }

    protected static final List<GroupedItemSection> commonItemSections = new ArrayList<>();
    protected static final Map<String, Boolean> commonSectionExpanded = new HashMap<>();
    protected static final List<CategoryTreeRow> visibleCategoryRows = new ArrayList<>();
    protected static final List<SequenceCardRenderInfo> visibleCustomSequenceCards = new ArrayList<>();
    protected static final List<CustomSectionRenderInfo> visibleCustomSectionHeaders = new ArrayList<>();
    protected static final List<CustomSequenceDropTarget> visibleCustomSectionDropTargets = new ArrayList<>();
    protected static final List<CustomButtonRenderInfo> visibleCustomSearchScopeButtons = new ArrayList<>();
    protected static final List<CustomButtonRenderInfo> visibleCustomToolbarButtons = new ArrayList<>();
    protected static final List<CustomButtonRenderInfo> visibleCustomEmptySectionButtons = new ArrayList<>();
    protected static final Map<String, Boolean> customSectionExpanded = new HashMap<>();
    protected static String currentCustomSubCategory = "";

    /** Shared category scope used by the modern dashboard and path editor. */
    public static String getCurrentCategory() {
        return currentCategory == null ? "" : currentCategory;
    }

    public static String getCurrentCustomSubCategory() {
        return currentCustomSubCategory == null ? "" : currentCustomSubCategory;
    }

    public static void setCurrentCategorySelection(String category, String subCategory) {
        if (category == null || category.trim().isEmpty()) {
            return;
        }
        currentCategory = category.trim();
        currentCustomSubCategory = subCategory == null ? "" : subCategory.trim();
        currentPage = 0;
        refreshGuiLists();
    }

    public static int loopCount = 1;
    public static int loopCounter = 0;
    public static boolean isLooping = false;

    public static boolean lockGameInteraction = false;

    protected static boolean isDebugRecordingMenuVisible = false;
    protected static int debugCategoryRightClickCounter = 0;
    protected static long lastDebugCategoryRightClickTime = 0;

    protected static int categoryScrollOffset = 0;
    protected static int maxCategoryScroll = 0;
    public static boolean isDraggingCategoryScrollbar = false;
    protected static int categoryScrollClickY = 0;
    protected static int initialCategoryScrollOffset = 0;
    protected static boolean isDraggingCategoryRow = false;
    protected static CategoryTreeRow pressedCategoryRow = null;
    protected static Rectangle pressedCategoryRowRect = null;
    protected static int pressedCategoryRowMouseX = 0;
    protected static int pressedCategoryRowMouseY = 0;
    protected static int draggingCategoryRowMouseX = 0;
    protected static int draggingCategoryRowMouseY = 0;
    protected static CategoryTreeRow currentCategorySortDropTarget = null;
    protected static boolean currentCategorySortDropAfter = false;
    protected static boolean isDraggingCustomSequenceCard = false;
    protected static PathSequence pressedCustomSequence = null;
    protected static Rectangle pressedCustomSequenceRect = null;
    protected static int pressedCustomSequenceMouseX = 0;
    protected static int pressedCustomSequenceMouseY = 0;
    protected static int draggingCustomSequenceMouseX = 0;
    protected static int draggingCustomSequenceMouseY = 0;
    protected static CustomSequenceDropTarget currentSequenceDropTarget = null;
    protected static String currentCustomSequenceSortTargetName = "";
    protected static boolean currentCustomSequenceSortAfter = false;
    protected static boolean contextMenuVisible = false;
    protected static int contextMenuAnchorX = 0;
    protected static int contextMenuAnchorY = 0;
    protected static final List<ContextMenuItem> contextMenuRootItems = new ArrayList<>();
    protected static final List<ContextMenuLayer> contextMenuLayers = new ArrayList<>();
    protected static final List<Integer> contextMenuOpenPath = new ArrayList<>();
    protected static final List<Integer> contextMenuKeyboardSelectionPath = new ArrayList<>();
    protected static GuiTextField customSequenceSearchField;
    protected static String customSequenceSearchQuery = "";
    protected static String customSequenceSearchScope = SEARCH_SCOPE_CURRENT_CATEGORY;
    protected static boolean customSequenceSearchExpanded = false;
    protected static boolean customSequenceSearchFocusPending = false;
    protected static final Set<String> selectedCustomSequenceNames = new LinkedHashSet<>();
    protected static Rectangle customSearchClearButtonBounds;
    protected static Rectangle customSearchToggleButtonBounds;
    protected static boolean isDraggingCategoryDivider = false;
    protected static int categoryDividerMouseOffsetX = 0;
    protected static long customSequencePageTurnLockUntil = 0L;
    protected static Rectangle categoryDividerBounds;
    protected static Rectangle versionClickArea;
    protected static Rectangle authorClickArea;

    // --- 新增：左侧功能按钮列表 ---
    protected static final List<GuiButton> sideButtons = new ArrayList<>();
    protected static final int BTN_ID_THEME_CONFIG = 1000;
    protected static final int BTN_ID_UPDATE = 1001;
    protected static final int BTN_ID_DONATE = 1006;
    protected static final int BTN_ID_PERFORMANCE_MONITOR = 1008;
    // --- 新增结束 ---

    protected static boolean otherFeaturesScreenActive = false;
    protected static int selectedOtherFeatureGroupIndex = -1;
    protected static int otherFeatureGroupScrollOffset = 0;
    protected static int maxOtherFeatureGroupScroll = 0;
    protected static boolean isDraggingOtherFeatureGroupScrollbar = false;
    protected static int otherFeatureScreenPage = 0;

    protected static final class OtherFeatureCardLayout {
        protected final FeatureDef feature;
        protected final Rectangle bounds;

        protected OtherFeatureCardLayout(FeatureDef feature, Rectangle bounds) {
            this.feature = feature;
            this.bounds = bounds;
        }
    }

    protected static final class OtherFeaturePageControlBounds {
        protected final Rectangle containerBounds;
        protected final Rectangle prevButtonBounds;
        protected final Rectangle nextButtonBounds;
        protected final Rectangle pageInfoBounds;

        protected OtherFeaturePageControlBounds(Rectangle containerBounds, Rectangle prevButtonBounds,
                Rectangle nextButtonBounds, Rectangle pageInfoBounds) {
            this.containerBounds = containerBounds;
            this.prevButtonBounds = prevButtonBounds;
            this.nextButtonBounds = nextButtonBounds;
            this.pageInfoBounds = pageInfoBounds;
        }
    }

    protected static final class OtherFeaturePageLayout {
        protected final List<OtherFeatureCardLayout> cards;
        protected final Rectangle cardAreaBounds;
        protected final int currentPage;
        protected final int totalPages;
        protected final OtherFeaturePageControlBounds pageControls;

        protected OtherFeaturePageLayout(List<OtherFeatureCardLayout> cards, Rectangle cardAreaBounds, int currentPage,
                int totalPages, OtherFeaturePageControlBounds pageControls) {
            this.cards = cards;
            this.cardAreaBounds = cardAreaBounds;
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.pageControls = pageControls;
        }
    }

    public static void onOpen() {
        maxOtherFeatureGroupScroll = 0;
        isDraggingOtherFeatureGroupScrollbar = false;
        otherFeatureScreenPage = 0;
        isDraggingCategoryDivider = false;
        isDraggingCategoryRow = false;
        pressedCategoryRow = null;
        pressedCategoryRowRect = null;
        currentCategorySortDropTarget = null;
        pressedCustomSequence = null;
        pressedCustomSequenceRect = null;
        isDraggingCustomSequenceCard = false;
        currentSequenceDropTarget = null;
        currentCustomSequenceSortTargetName = "";
        currentCustomSequenceSortAfter = false;
        categoryDividerBounds = null;
        customSequenceSearchQuery = "";
        customSequenceSearchScope = SEARCH_SCOPE_CURRENT_CATEGORY;
        customSequenceSearchExpanded = false;
        customSequenceSearchFocusPending = false;
        customSequenceSearchField = null;
        customSearchClearButtonBounds = null;
        customSearchToggleButtonBounds = null;
        selectedCustomSequenceNames.clear();
        visibleCustomSearchScopeButtons.clear();
        visibleCustomToolbarButtons.clear();
        visibleCustomEmptySectionButtons.clear();
        closeContextMenu();
        normalizeCategoryState();
        PathSequenceManager.initializePathSequences();
        MainUiLayoutManager.ensureLoaded();
        refreshGuiLists();
        isDebugRecordingMenuVisible = false;
        UpdateChecker.fetchVersionAndChangelog();
        UpdateChecker.notifyIfNewVersion();
        rebuildSideButtons();
    }

    public static void openOverlayScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        GuiModernMainScreen.openMenu(mc);
    }

    protected static void rebuildSideButtons() {
        sideButtons.clear();
        // 始终显示：主题配置、更新脚本、打赏
        sideButtons.add(new ThemedButton(BTN_ID_THEME_CONFIG, 0, 0, 70, 20, I18n.format("gui.inventory.theme_config")));
        sideButtons.add(new ThemedButton(BTN_ID_UPDATE, 0, 0, 70, 20, I18n.format("gui.inventory.update_script")));
        sideButtons.add(new ThemedButton(BTN_ID_DONATE, 0, 0, 70, 20, I18n.format("gui.inventory.donate")));

    }

    protected static void updateButtonPositions(int screenWidth, int screenHeight) {
        if (sideButtons.isEmpty()) {
            return;
        }

        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        String title = buildOverlayTitle();
        OverlayMetrics m = computeOverlayMetrics(screenWidth, screenHeight, fontRenderer, title);

        int sideButtonCount = sideButtons.size();
        int sidePanelX = m.x - m.sideButtonColumnWidth - m.gap;
        int sidePanelY = m.y;

        for (GuiButton button : sideButtons) {
            button.width = m.sideButtonWidth;
            button.height = m.sideButtonHeight;
            button.x = sidePanelX + (m.sideButtonColumnWidth - button.width) / 2;
        }

        int topY = sidePanelY + m.padding;
        int bottomY = sidePanelY + m.totalHeight - m.padding - m.sideButtonHeight;
        for (int i = 0; i < sideButtonCount; i++) {
            GuiButton button = sideButtons.get(i);
            if (sideButtonCount == 1) {
                button.y = topY;
            } else {
                float t = (float) i / (float) (sideButtonCount - 1);
                button.y = Math.round(topY + t * (bottomY - topY));
            }
        }

        // 更新版本和作者点击区域
        int topInfoY = m.y - fontRenderer.FONT_HEIGHT - m.padding;
        String versionText = I18n.format("gui.inventory.version", zszlScriptMod.VERSION, UpdateChecker.latestVersion);
        int versionX = m.x;
        versionClickArea = new Rectangle(versionX, topInfoY, fontRenderer.getStringWidth(versionText), 10);

        String authorText = I18n.format("gui.inventory.author");
        int authorX = m.x + m.totalWidth - fontRenderer.getStringWidth(authorText);
        authorClickArea = new Rectangle(authorX, topInfoY, fontRenderer.getStringWidth(authorText), 10);
    }

    protected static OverlayMetrics getCurrentOverlayMetrics(int screenWidth, int screenHeight) {
        return computeOverlayMetrics(screenWidth, screenHeight, Minecraft.getMinecraft().fontRenderer,
                buildOverlayTitle());
    }

    protected static int scaleRawMouseX(int rawMouseX, int screenWidth) {
        return rawMouseX * screenWidth / Minecraft.getMinecraft().displayWidth;
    }

    protected static int scaleRawMouseY(int rawMouseY, int screenHeight) {
        return screenHeight - rawMouseY * screenHeight / Minecraft.getMinecraft().displayHeight - 1;
    }

    protected static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    protected static Rectangle getMainRightPanelBounds(OverlayMetrics m) {
        int x = m.contentPanelX;
        int y = m.contentStartY;
        int width = Math.max(0, m.contentPanelRight - m.contentPanelX);
        int bottom = m.y + m.totalHeight - m.padding;
        return new Rectangle(x, y, width, Math.max(0, bottom - y));
    }

    public static boolean isAnyScrollbarDragging() {
        return isDraggingCategoryScrollbar || isDraggingOtherFeatureGroupScrollbar
                || isDraggingCategoryDivider;
    }

    public static boolean isAnyDragActive() {
        return isAnyScrollbarDragging() || pressedCustomSequence != null || isDraggingCustomSequenceCard
                || pressedCategoryRow != null || isDraggingCategoryRow;
    }

    protected static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    protected static boolean isBlank(String value) {
        return normalizeText(value).isEmpty();
    }

    protected static boolean containsIllegalNameChars(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (ILLEGAL_CATEGORY_NAME_CHARS.indexOf(value.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    protected static void showOverlayMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null && message != null && !message.isEmpty()) {
            mc.player.sendMessage(new TextComponentString(message));
        }
    }

    protected static int clampCategoryPanelBaseWidth(int baseWidth) {
        return MathHelper.clamp(baseWidth, CATEGORY_PANEL_MIN_BASE_WIDTH, CATEGORY_PANEL_MAX_BASE_WIDTH);
    }

    protected static String getCategoryRowDisplayLabel(CategoryTreeRow row) {
        return row == null ? "" : (row.isSubCategory() ? row.subCategory : row.category);
    }

    protected static String getCategoryRowStorageKey(CategoryTreeRow row) {
        if (row == null) {
            return "";
        }
        return row.isSubCategory() ? row.category + "::" + row.subCategory : row.category;
    }

    protected static int getStableAccentColor(String key) {
        int hash = normalizeText(key).hashCode();
        int red = 80 + Math.abs(hash & 0x3F);
        int green = 120 + Math.abs((hash >> 8) & 0x5F);
        int blue = 145 + Math.abs((hash >> 16) & 0x5F);
        return 0xFF000000 | (Math.min(255, red) << 16) | (Math.min(255, green) << 8) | Math.min(255, blue);
    }

    protected static int computeAutoCategoryPanelBaseWidth(FontRenderer fontRenderer) {
        int longestText = fontRenderer.getStringWidth(I18n.format("gui.modern.invbase.u002"));
        for (CategoryTreeRow row : buildVisibleCategoryTreeRows()) {
            int labelWidth = fontRenderer.getStringWidth(getCategoryRowDisplayLabel(row));
            int reserved = row.isSubCategory() ? 26 : 34;
            longestText = Math.max(longestText, labelWidth + reserved);
        }
        return clampCategoryPanelBaseWidth(longestText + 12);
    }

    protected static String findCategoryIgnoreCase(String categoryName) {
        String normalizedTarget = normalizeText(categoryName);
        for (String category : categories) {
            if (normalizedTarget.equalsIgnoreCase(normalizeText(category))) {
                return category;
            }
        }
        return "";
    }

    protected static boolean validateCategoryNameInput(String value, String originalName) {
        String normalizedValue = normalizeText(value);
        String normalizedOriginal = normalizeText(originalName);
        if (normalizedValue.isEmpty()) {
            showOverlayMessage("gui.modern.invbase.u003");
            return false;
        }
        if (containsIllegalNameChars(normalizedValue)) {
            showOverlayMessage(I18n.format("gui.modern.invbase.fmt.illegal_chars", ILLEGAL_CATEGORY_NAME_CHARS));
            return false;
        }
        String existing = findCategoryIgnoreCase(normalizedValue);
        if (!existing.isEmpty() && !normalizedValue.equalsIgnoreCase(normalizedOriginal)) {
            showOverlayMessage(I18n.format("gui.modern.invbase.fmt.cat_exists", existing));
            return false;
        }
        return true;
    }

    protected static boolean validateSubCategoryNameInput(String category, String value, String originalName) {
        String normalizedValue = normalizeText(value);
        String normalizedOriginal = normalizeText(originalName);
        if (normalizedValue.isEmpty()) {
            showOverlayMessage("gui.modern.invbase.u004");
            return false;
        }
        if (containsIllegalNameChars(normalizedValue)) {
            showOverlayMessage(I18n.format("gui.modern.invbase.fmt.sub_illegal", ILLEGAL_CATEGORY_NAME_CHARS));
            return false;
        }
        for (String subCategory : MainUiLayoutManager.getSubCategories(category)) {
            if (normalizedValue.equalsIgnoreCase(normalizeText(subCategory))
                    && !normalizedValue.equalsIgnoreCase(normalizedOriginal)) {
                showOverlayMessage(I18n.format("gui.modern.invbase.fmt.sub_exists", subCategory));
                return false;
            }
        }
        return true;
    }

    protected static String findSubCategoryIgnoreCase(String category, String subCategoryName) {
        String normalizedTarget = normalizeText(subCategoryName);
        for (String subCategory : MainUiLayoutManager.getSubCategories(category)) {
            if (normalizedTarget.equalsIgnoreCase(normalizeText(subCategory))) {
                return subCategory;
            }
        }
        return "";
    }

    protected static int findFirstEnabledMenuItem(List<ContextMenuItem> items) {
        if (items == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).enabled) {
                return i;
            }
        }
        return items.isEmpty() ? -1 : 0;
    }

    protected static int getKeyboardMenuSelection(int depth, List<ContextMenuItem> items) {
        int fallback = findFirstEnabledMenuItem(items);
        if (depth < 0 || items == null || items.isEmpty()) {
            return fallback;
        }
        if (depth >= contextMenuKeyboardSelectionPath.size()) {
            while (contextMenuKeyboardSelectionPath.size() <= depth) {
                contextMenuKeyboardSelectionPath.add(fallback);
            }
            return fallback;
        }
        int selected = contextMenuKeyboardSelectionPath.get(depth);
        if (selected < 0 || selected >= items.size()) {
            contextMenuKeyboardSelectionPath.set(depth, fallback);
            return fallback;
        }
        return selected;
    }

    protected static void setKeyboardMenuSelection(int depth, int selectedIndex) {
        while (contextMenuKeyboardSelectionPath.size() <= depth) {
            contextMenuKeyboardSelectionPath.add(-1);
        }
        contextMenuKeyboardSelectionPath.set(depth, selectedIndex);
        while (contextMenuKeyboardSelectionPath.size() > depth + 1) {
            contextMenuKeyboardSelectionPath.remove(contextMenuKeyboardSelectionPath.size() - 1);
        }
    }

    protected static int moveKeyboardMenuSelection(List<ContextMenuItem> items, int currentIndex, int step) {
        if (items == null || items.isEmpty()) {
            return -1;
        }
        int size = items.size();
        int index = currentIndex < 0 ? findFirstEnabledMenuItem(items) : currentIndex;
        if (index < 0) {
            return -1;
        }
        for (int i = 0; i < size; i++) {
            index = (index + step + size) % size;
            if (items.get(index).enabled) {
                return index;
            }
        }
        return currentIndex;
    }

    protected static void clearSelectedCustomSequences() {
        selectedCustomSequenceNames.clear();
    }

    protected static void pruneSelectedCustomSequences() {
        Iterator<String> iterator = selectedCustomSequenceNames.iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();
            PathSequence sequence = PathSequenceManager.getSequence(name);
            if (sequence == null || !sequence.isCustom()
                    || !normalizeText(sequence.getCategory()).equals(normalizeText(currentCategory))) {
                iterator.remove();
            }
        }
    }

    protected static List<String> getSelectedCustomSequenceNames() {
        pruneSelectedCustomSequences();
        return new ArrayList<>(selectedCustomSequenceNames);
    }

    protected static boolean isCustomSequenceSelected(PathSequence sequence) {
        return sequence != null && selectedCustomSequenceNames.contains(sequence.getName());
    }

    protected static void toggleCustomSequenceSelection(PathSequence sequence) {
        if (sequence == null) {
            return;
        }
        String name = sequence.getName();
        if (selectedCustomSequenceNames.contains(name)) {
            selectedCustomSequenceNames.remove(name);
        } else {
            selectedCustomSequenceNames.add(name);
        }
    }

    protected static boolean isControlDown() {
        return SimulatedKeyInputManager.isEitherKeyDown(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL);
    }

    protected static boolean canReorderCategoryRows(CategoryTreeRow source, CategoryTreeRow target) {
        if (source == null || target == null || source == target || source.systemCategory || target.systemCategory) {
            return false;
        }
        if (source.isCustomCategoryRoot() && target.isCustomCategoryRoot()) {
            return true;
        }
        return source.isSubCategory() && target.isSubCategory()
                && normalizeText(source.category).equals(normalizeText(target.category));
    }

    protected static CategoryTreeRow findSortableCategoryRowAt(int mouseX, int mouseY, CategoryTreeRow source) {
        for (CategoryTreeRow row : visibleCategoryRows) {
            if (row.bounds != null && row.bounds.contains(mouseX, mouseY) && canReorderCategoryRows(source, row)) {
                return row;
            }
        }
        return null;
    }

    protected static CustomButtonRenderInfo findCustomToolbarButtonAt(int mouseX, int mouseY) {
        for (CustomButtonRenderInfo info : visibleCustomToolbarButtons) {
            if (info.bounds != null && info.bounds.contains(mouseX, mouseY)) {
                return info;
            }
        }
        return null;
    }

    protected static CustomButtonRenderInfo findCustomSearchScopeButtonAt(int mouseX, int mouseY) {
        for (CustomButtonRenderInfo info : visibleCustomSearchScopeButtons) {
            if (info.bounds != null && info.bounds.contains(mouseX, mouseY)) {
                return info;
            }
        }
        return null;
    }

    protected static CustomButtonRenderInfo findCustomEmptySectionButtonAt(int mouseX, int mouseY) {
        for (CustomButtonRenderInfo info : visibleCustomEmptySectionButtons) {
            if (info.bounds != null && info.bounds.contains(mouseX, mouseY)) {
                return info;
            }
        }
        return null;
    }

    protected static void promptCreateCustomSequence(String category, String subCategory) {
        final String normalizedCategory = normalizeText(category);
        final String normalizedSubCategory = normalizeText(subCategory);
        if (normalizedCategory.isEmpty()) {
            return;
        }
        openOverlayTextInput("gui.modern.invbase.u005", value -> {
            String name = normalizeText(value);
            if (name.isEmpty()) {
                showOverlayMessage("gui.modern.invbase.u006");
                return;
            }
            if (PathSequenceManager.hasSequence(name)) {
                showOverlayMessage(I18n.format("gui.modern.invbase.fmt.seq_exists", name));
                return;
            }
            if (!normalizedSubCategory.isEmpty()) {
                MainUiLayoutManager.addSubCategory(normalizedCategory, normalizedSubCategory);
            }
            if (!PathSequenceManager.createEmptyCustomSequence(name, normalizedCategory, normalizedSubCategory)) {
                showOverlayMessage("gui.modern.invbase.u007");
                return;
            }
            currentCategory = normalizedCategory;
            currentCustomSubCategory = normalizedSubCategory;
            clearSelectedCustomSequences();
            refreshGuiLists();
            openModernPathSequenceEditor(name, true);
        });
    }

    protected static void promptCreateSubCategory(String category) {
        final String normalizedCategory = normalizeText(category);
        if (normalizedCategory.isEmpty()) {
            return;
        }
        openOverlayTextInput("gui.modern.invbase.u008", value -> {
            String name = normalizeText(value);
            if (!validateSubCategoryNameInput(normalizedCategory, name, "")) {
                return;
            }
            MainUiLayoutManager.addSubCategory(normalizedCategory, name);
            currentCategory = normalizedCategory;
            currentCustomSubCategory = name;
            currentPage = 0;
            refreshGuiLists();
        });
    }

    protected static void promptBatchSubCategoryUpdate(List<String> sequenceNames) {
        if (sequenceNames == null || sequenceNames.isEmpty()) {
            return;
        }
        openOverlayTextInput("gui.modern.invbase.u009", currentCustomSubCategory, value -> {
            String newSubCategory = normalizeText(value);
            if (!newSubCategory.isEmpty() && containsIllegalNameChars(newSubCategory)) {
                showOverlayMessage(I18n.format("gui.modern.invbase.fmt.sub_illegal", ILLEGAL_CATEGORY_NAME_CHARS));
                return;
            }
            String resolvedSubCategory = newSubCategory;
            String existingSubCategory = findSubCategoryIgnoreCase(currentCategory, newSubCategory);
            if (!newSubCategory.isEmpty() && existingSubCategory.isEmpty()) {
                MainUiLayoutManager.addSubCategory(currentCategory, newSubCategory);
            } else if (!existingSubCategory.isEmpty()) {
                resolvedSubCategory = existingSubCategory;
            }
            for (String sequenceName : sequenceNames) {
                PathSequenceManager.moveCustomSequenceTo(sequenceName, currentCategory, resolvedSubCategory);
            }
            clearSelectedCustomSequences();
            currentCustomSubCategory = resolvedSubCategory;
            currentPage = 0;
            refreshGuiLists();
        });
    }

    protected static void promptBatchNoteUpdate(List<String> sequenceNames) {
        if (sequenceNames == null || sequenceNames.isEmpty()) {
            return;
        }
        openOverlayTextInput("gui.modern.invbase.u010", "", value -> {
            String note = value == null ? "" : value.trim();
            boolean changed = false;
            List<PathSequence> allSequences = PathSequenceManager.getAllSequences();
            for (PathSequence sequence : allSequences) {
                if (sequence != null && sequence.isCustom() && sequenceNames.contains(sequence.getName())) {
                    sequence.setNote(note);
                    changed = true;
                }
            }
            if (changed) {
                PathSequenceManager.saveAllSequences(allSequences);
                refreshGuiLists();
            }
        });
    }

    protected static boolean applyBatchSequenceChange(List<String> sequenceNames, Consumer<PathSequence> updater) {
        if (sequenceNames == null || sequenceNames.isEmpty() || updater == null) {
            return false;
        }
        Set<String> targets = new HashSet<>(sequenceNames);
        boolean changed = false;
        List<PathSequence> allSequences = PathSequenceManager.getAllSequences();
        for (PathSequence sequence : allSequences) {
            if (sequence != null && sequence.isCustom() && targets.contains(sequence.getName())) {
                updater.accept(sequence);
                changed = true;
            }
        }
        if (changed) {
            PathSequenceManager.saveAllSequences(allSequences);
            refreshGuiLists();
        }
        return changed;
    }

    protected static void moveCustomSequencesToSubCategory(List<String> sequenceNames, String category,
            String subCategory, boolean keepSelection) {
        if (sequenceNames == null || sequenceNames.isEmpty()) {
            return;
        }
        String normalizedCategory = normalizeText(category);
        String normalizedSubCategory = normalizeText(subCategory);
        if (normalizedCategory.isEmpty()) {
            return;
        }
        if (!normalizedSubCategory.isEmpty()) {
            MainUiLayoutManager.addSubCategory(normalizedCategory, normalizedSubCategory);
        }
        for (String sequenceName : sequenceNames) {
            PathSequenceManager.moveCustomSequenceTo(sequenceName, normalizedCategory, normalizedSubCategory);
        }
        if (!keepSelection) {
            clearSelectedCustomSequences();
        }
        currentCategory = normalizedCategory;
        currentCustomSubCategory = normalizedSubCategory;
        currentPage = 0;
        refreshGuiLists();
    }

    protected static List<PathSequence> getMovableSequencesForSubCategory(String category, String targetSubCategory) {
        String normalizedCategory = normalizeText(category);
        String normalizedTargetSubCategory = normalizeText(targetSubCategory);
        List<PathSequence> result = new ArrayList<>();
        for (PathSequence sequence : PathSequenceManager.getAllSequences()) {
            if (sequence == null || !sequence.isCustom()) {
                continue;
            }
            if (!normalizedCategory.equals(normalizeText(sequence.getCategory()))) {
                continue;
            }
            if (normalizedTargetSubCategory.equalsIgnoreCase(GuiInventory.normalizeSequenceSubCategory(sequence))) {
                continue;
            }
            result.add(sequence);
        }
        result.sort((left, right) -> {
            int compare = GuiInventory.normalizeSequenceSubCategory(left)
                    .compareToIgnoreCase(GuiInventory.normalizeSequenceSubCategory(right));
            return compare != 0 ? compare : left.getName().compareToIgnoreCase(right.getName());
        });
        return result;
    }

    protected static List<ContextMenuItem> buildMoveExistingIntoSectionMenu(String category, String targetSubCategory) {
        String normalizedCategory = normalizeText(category);
        String normalizedTargetSubCategory = normalizeText(targetSubCategory);
        List<ContextMenuItem> items = new ArrayList<>();
        List<String> selectedNames = getSelectedCustomSequenceNames();
        if (!selectedNames.isEmpty()) {
            List<String> movableSelected = new ArrayList<>();
            for (String sequenceName : selectedNames) {
                PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
                if (sequence != null && sequence.isCustom()
                        && normalizedCategory.equals(normalizeText(sequence.getCategory()))
                        && !normalizedTargetSubCategory.equalsIgnoreCase(
                                GuiInventory.normalizeSequenceSubCategory(sequence))) {
                    movableSelected.add(sequenceName);
                }
            }
            if (!movableSelected.isEmpty()) {
                items.add(menuItem(I18n.format("gui.modern.invbase.fmt.move_n", String.valueOf(movableSelected.size())), () -> {
                    moveCustomSequencesToSubCategory(movableSelected, normalizedCategory, normalizedTargetSubCategory,
                            false);
                }));
            }
        }

        Map<String, List<PathSequence>> grouped = new LinkedHashMap<>();
        for (PathSequence sequence : getMovableSequencesForSubCategory(normalizedCategory,
                normalizedTargetSubCategory)) {
            String sourceSubCategory = GuiInventory.normalizeSequenceSubCategory(sequence);
            String groupKey = sourceSubCategory.isEmpty() ? "gui.modern.invbase.u011" : sourceSubCategory;
            grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(sequence);
        }

        for (Map.Entry<String, List<PathSequence>> entry : grouped.entrySet()) {
            ContextMenuItem sourceMenu = menuItem(entry.getKey(), null);
            for (PathSequence sequence : entry.getValue()) {
                sourceMenu.child(menuItem(sequence.getName(), () -> {
                    moveCustomSequencesToSubCategory(Collections.singletonList(sequence.getName()), normalizedCategory,
                            normalizedTargetSubCategory, false);
                }));
            }
            items.add(sourceMenu);
        }

        return items;
    }

    protected static boolean isSystemOverlayCategory(String category) {
        return I18n.format("gui.inventory.category.common").equals(category)
                || I18n.format("gui.inventory.category.debug").equals(category)
                || otherFeatureCategories.contains(category);
    }

    protected static boolean isCustomOverlayCategory(String category) {
        return category != null && categories.contains(category) && !isSystemOverlayCategory(category);
    }

    protected static String getPageKeyForSelection(String category, String subCategory) {
        String normalizedSubCategory = normalizeText(subCategory);
        return normalizedSubCategory.isEmpty() ? category : category + "::" + normalizedSubCategory;
    }

    protected static String getCurrentPageKey() {
        if (isCustomOverlayCategory(currentCategory)) {
            return getPageKeyForSelection(currentCategory, currentCustomSubCategory);
        }
        return currentCategory;
    }

    protected static void syncCurrentCustomCategoryState() {
        if (!isCustomOverlayCategory(currentCategory)) {
            currentCustomSubCategory = "";
            return;
        }

        List<String> subCategories = MainUiLayoutManager.getSubCategories(currentCategory);
        if (!normalizeText(currentCustomSubCategory).isEmpty() && !subCategories.contains(currentCustomSubCategory)) {
            currentCustomSubCategory = "";
        }
    }

    protected static void closeContextMenu() {
        contextMenuVisible = false;
        contextMenuRootItems.clear();
        contextMenuLayers.clear();
        contextMenuOpenPath.clear();
        contextMenuKeyboardSelectionPath.clear();
    }

    protected static ContextMenuItem menuItem(String label, Runnable action) {
        return new ContextMenuItem(label, action);
    }

    protected static void openContextMenu(int mouseX, int mouseY, List<ContextMenuItem> rootItems) {
        closeContextMenu();
        if (rootItems == null || rootItems.isEmpty()) {
            return;
        }
        contextMenuVisible = true;
        contextMenuAnchorX = mouseX;
        contextMenuAnchorY = mouseY;
        contextMenuRootItems.addAll(rootItems);
        contextMenuKeyboardSelectionPath.add(findFirstEnabledMenuItem(rootItems));
    }

    protected static void reopenOverlayScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        GuiModernMainScreen.openMenu(mc);
    }

    protected static GuiScreen createModernReturnScreen() {
        return GuiModernMainScreen.getPersistentScreen();
    }

    protected static void openModernPathSequenceEditor(String sequenceName, boolean custom) {
        String name = normalizeText(sequenceName);
        if (name.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        String command = (custom ? "custom_path:" : "path:") + name;
        GuiModernMainScreen modernScreen = getActiveModernScreen(mc);
        if (modernScreen != null) {
            modernScreen.openPathSequenceEditor(command);
            return;
        }
        GuiModernMainScreen.openSettingsTab(mc, "path_manager");
    }

    protected static void openOverlayTextInput(String title, Consumer<String> callback) {
        openOverlayTextInput(title, "", callback);
    }

    protected static void openOverlayTextInput(String title, String initialText, Consumer<String> callback) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        GuiModernMainScreen modernScreen = getActiveModernScreen(mc);
        if (modernScreen != null) {
            modernScreen.openInlineTextInput(title, initialText, callback);
            return;
        }
        closeContextMenu();
        zszlScriptMod.isGuiVisible = true;
        mc.displayGuiScreen(new GuiTextInput(GuiModernMainScreen.getPersistentScreen(), title,
                initialText == null ? "" : initialText, value -> {
                    if (callback != null) {
                        callback.accept(value);
                    }
                }));
    }

    protected static void openHiddenCategoryPicker() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        GuiModernMainScreen modernScreen = getActiveModernScreen(mc);
        if (modernScreen != null) {
            modernScreen.openHiddenCategoryPicker();
        }
    }

    private static GuiModernMainScreen getActiveModernScreen(Minecraft mc) {
        if (mc.currentScreen instanceof GuiModernMainScreen) {
            return (GuiModernMainScreen) mc.currentScreen;
        }
        return DetachedSwingWindowManager.isDetached() ? DetachedSwingWindowManager.getActiveScreen() : null;
    }

    protected static void openOverlayConfirm(String title, String message, Runnable onConfirm) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        GuiModernMainScreen modernScreen = getActiveModernScreen(mc);
        if (modernScreen != null) {
            modernScreen.openInlineConfirmation(title, message, onConfirm);
            return;
        }
        closeContextMenu();
        zszlScriptMod.isGuiVisible = true;
        mc.displayGuiScreen(new GuiInventoryConfirmScreen(GuiModernMainScreen.getPersistentScreen(), title, message, onConfirm));
    }

    protected static List<CategoryTreeRow> buildVisibleCategoryTreeRows() {
        List<CategoryTreeRow> rows = new ArrayList<>();
        List<String> customCategories = new ArrayList<>();
        for (String category : categories) {
            if (isSystemOverlayCategory(category)) {
                rows.add(new CategoryTreeRow(category, "", true));
            } else if (isCustomOverlayCategory(category)) {
                customCategories.add(category);
            }
        }

        customCategories.sort((left, right) -> {
            boolean leftPinned = MainUiLayoutManager.isPinned(left);
            boolean rightPinned = MainUiLayoutManager.isPinned(right);
            if (leftPinned != rightPinned) {
                return Boolean.compare(rightPinned, leftPinned);
            }
            return Integer.compare(categories.indexOf(left), categories.indexOf(right));
        });

        for (String category : customCategories) {
            rows.add(new CategoryTreeRow(category, "", false));
            if (!MainUiLayoutManager.isCollapsed(category)) {
                for (String subCategory : MainUiLayoutManager.getSubCategories(category)) {
                    rows.add(new CategoryTreeRow(category, subCategory, false));
                }
            }
        }

        return rows;
    }

    protected static CategoryTreeRow findCategoryRowAt(int mouseX, int mouseY) {
        for (CategoryTreeRow row : visibleCategoryRows) {
            if (row.bounds != null && row.bounds.contains(mouseX, mouseY)) {
                return row;
            }
        }
        return null;
    }

    protected static SequenceCardRenderInfo findCustomSequenceCardAt(int mouseX, int mouseY) {
        for (SequenceCardRenderInfo card : visibleCustomSequenceCards) {
            if (card.bounds != null && card.bounds.contains(mouseX, mouseY)) {
                return card;
            }
        }
        return null;
    }

    protected static boolean canReorderCustomSequenceCards(PathSequence source, PathSequence target) {
        if (source == null || target == null || source == target) {
            return false;
        }
        if (!source.isCustom() || !target.isCustom()) {
            return false;
        }
        if (!normalizeText(source.getCategory()).equals(normalizeText(target.getCategory()))) {
            return false;
        }
        return GuiInventory.normalizeSequenceSubCategory(source)
                .equalsIgnoreCase(GuiInventory.normalizeSequenceSubCategory(target));
    }

    protected static SequenceCardRenderInfo findSortableCustomSequenceCardAt(int mouseX, int mouseY,
            PathSequence source) {
        for (SequenceCardRenderInfo card : visibleCustomSequenceCards) {
            if (card.bounds != null && card.bounds.contains(mouseX, mouseY)
                    && canReorderCustomSequenceCards(source, card.sequence)) {
                return card;
            }
        }
        return null;
    }

    protected static boolean shouldUseHorizontalCustomSequenceSplit(SequenceCardRenderInfo targetCard) {
        if (targetCard == null || targetCard.bounds == null) {
            return false;
        }
        for (SequenceCardRenderInfo other : visibleCustomSequenceCards) {
            if (other == null || other == targetCard || other.bounds == null) {
                continue;
            }
            if (!canReorderCustomSequenceCards(targetCard.sequence, other.sequence)) {
                continue;
            }
            if (Math.abs(other.bounds.y - targetCard.bounds.y) <= Math.max(2, targetCard.bounds.height / 3)) {
                return true;
            }
        }
        return false;
    }

    protected static SequenceCardRenderInfo findVisibleCustomSequenceCardByName(String sequenceName) {
        String normalizedName = normalizeText(sequenceName);
        if (normalizedName.isEmpty()) {
            return null;
        }
        for (SequenceCardRenderInfo card : visibleCustomSequenceCards) {
            if (card != null && card.sequence != null
                    && normalizedName.equalsIgnoreCase(normalizeText(card.sequence.getName()))) {
                return card;
            }
        }
        return null;
    }

    protected static CustomSequenceDropTarget findDroppableCategoryRowAt(int mouseX, int mouseY) {
        for (CategoryTreeRow row : visibleCategoryRows) {
            if (row.bounds != null && row.bounds.contains(mouseX, mouseY) && row.isDroppableTarget()) {
                return new CustomSequenceDropTarget(row.category, row.subCategory, row.bounds);
            }
        }
        return null;
    }

    protected static CustomSequenceDropTarget findCustomSectionDropTargetAt(int mouseX, int mouseY,
            PathSequence source) {
        for (CustomSequenceDropTarget target : visibleCustomSectionDropTargets) {
            if (target.bounds == null || !target.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (source != null && target.matches(source.getCategory(), source.getSubCategory())) {
                continue;
            }
            return target;
        }
        return null;
    }

    protected static void normalizeCategoryState() {
        if ("gui.inventory.category.common".equals(sLastCategory)) {
            sLastCategory = I18n.format("gui.inventory.category.common");
        } else if ("gui.inventory.category.debug".equals(sLastCategory)) {
            sLastCategory = I18n.format("gui.inventory.category.debug");
        }

        if ("gui.inventory.category.common".equals(currentCategory)) {
            currentCategory = I18n.format("gui.inventory.category.common");
        } else if ("gui.inventory.category.debug".equals(currentCategory)) {
            currentCategory = I18n.format("gui.inventory.category.debug");
        }
    }

    /** Captures semantic navigation state before localized labels are replaced. */
    public static void prepareForLanguageChange() {
        pendingLanguageCategoryKey = categoryIdentity(currentCategory);
        pendingLanguageSubCategory = currentCustomSubCategory == null ? "" : currentCustomSubCategory;
    }

    private static String categoryIdentity(String category) {
        if (category == null || category.trim().isEmpty()
                || "gui.inventory.category.common".equals(category)
                || I18n.format("gui.inventory.category.common").equals(category)) {
            return "system:common";
        }
        if ("gui.inventory.category.debug".equals(category)
                || I18n.format("gui.inventory.category.debug").equals(category)) {
            return "system:debug";
        }
        String prefix = I18n.format("gui.inventory.other_features") + " · ";
        if (category.startsWith(prefix)) {
            return "other:" + category.substring(prefix.length());
        }
        return "custom:" + category;
    }

    private static String categoryFromIdentity(String identity) {
        if ("system:common".equals(identity)) {
            return I18n.format("gui.inventory.category.common");
        }
        if ("system:debug".equals(identity)) {
            return I18n.format("gui.inventory.category.debug");
        }
        if (identity != null && identity.startsWith("other:")) {
            String category = I18n.format("gui.inventory.other_features") + " · " + identity.substring(6);
            return categories.contains(category) ? category : null;
        }
        if (identity != null && identity.startsWith("custom:")) {
            String category = identity.substring(7);
            return categories.contains(category) ? category : null;
        }
        return null;
    }

    public static void refreshGuiLists() {
        String preservedCategoryKey = pendingLanguageCategoryKey == null
                ? categoryIdentity(currentCategory) : pendingLanguageCategoryKey;
        String preservedSubCategory = pendingLanguageSubCategory == null ? currentCustomSubCategory
                : pendingLanguageSubCategory;
        pendingLanguageCategoryKey = null;
        pendingLanguageSubCategory = null;
        MainUiLayoutManager.ensureLoaded();
        categories.clear();
        categoryItems.clear();
        categoryItemNames.clear();
        itemTooltips.clear();
        otherFeatureCategories.clear();

        categories.add(I18n.format("gui.inventory.category.common"));
        categories.add(I18n.format("gui.inventory.category.debug"));

        categories.addAll(PathSequenceManager.getVisibleCategories());

        OtherFeatureGroupManager.reload();
        List<GroupDef> otherFeatureGroups = OtherFeatureGroupManager.getGroups();
        String otherFeaturePrefix = I18n.format("gui.inventory.other_features") + " · ";
        for (GroupDef group : otherFeatureGroups) {
            if (group == null || group.name == null || group.name.trim().isEmpty()) {
                continue;
            }
            String categoryName = otherFeaturePrefix + group.name.trim();
            if (!otherFeatureCategories.add(categoryName)) {
                continue;
            }
            if (!categories.contains(categoryName)) {
                categories.add(categoryName);
            }
        }

        String restoredCategory = categoryFromIdentity(preservedCategoryKey);
        if (restoredCategory != null) {
            currentCategory = restoredCategory;
            currentCustomSubCategory = preservedSubCategory == null ? "" : preservedSubCategory;
        } else {
            currentCategory = I18n.format("gui.inventory.category.common");
            currentCustomSubCategory = "";
        }

        syncCurrentCustomCategoryState();
        pruneSelectedCustomSequences();

        String currentPageKey = getCurrentPageKey();
        if (CATEGORY_PAGE_MAP.containsKey(currentPageKey)) {
            currentPage = CATEGORY_PAGE_MAP.get(currentPageKey);
        } else {
            currentPage = 0;
        }

        List<String> setItems = new ArrayList<>();
        List<String> setItemNames = new ArrayList<>();
        setItems.add("autoeat");
        setItemNames.add(I18n.format("gui.inventory.item.autoeat.name"));
        itemTooltips.put("autoeat", I18n.format("gui.inventory.item.autoeat.tooltip"));
        setItems.add("toggle_auto_fishing");
        setItemNames.add(I18n.format("gui.inventory.item.auto_fishing.name"));
        itemTooltips.put("toggle_auto_fishing", I18n.format("gui.inventory.item.auto_fishing.tooltip"));
        setItems.add("toggle_mouse_detach");
        setItemNames.add(I18n.format("gui.inventory.item.mouse_detach.name"));
        itemTooltips.put("toggle_mouse_detach", I18n.format("gui.inventory.item.mouse_detach.tooltip"));
        setItems.add("toggle_fly");
        setItemNames.add(I18n.format("gui.inventory.item.fly.name"));
        itemTooltips.put("toggle_fly", I18n.format("gui.inventory.item.fly.tooltip"));
        setItems.add("baritone_flight_pathing");
        setItemNames.add(I18n.format("gui.inventory.item.baritone_flight_pathing.name"));
        itemTooltips.put("baritone_flight_pathing", I18n.format("gui.inventory.item.baritone_flight_pathing.tooltip"));
        setItems.add("followconfig");
        setItemNames.add(I18n.format("gui.inventory.item.autofollow.name"));
        itemTooltips.put("followconfig", I18n.format("gui.inventory.item.autofollow.tooltip"));

        setItems.add("toggle_kill_aura");
        setItemNames.add(I18n.format("gui.inventory.item.kill_aura.name"));
        itemTooltips.put("toggle_kill_aura", I18n.format("gui.inventory.item.kill_aura.tooltip"));
        setItems.add("conditional_execution");
        setItemNames.add(I18n.format("gui.inventory.item.conditional_execution.name"));
        itemTooltips.put("conditional_execution", I18n.format("gui.inventory.item.conditional_execution.tooltip"));
        setItems.add("auto_escape");
        setItemNames.add(I18n.format("gui.inventory.item.auto_escape.name"));
        itemTooltips.put("auto_escape", I18n.format("gui.inventory.item.auto_escape.tooltip"));
        setItems.add("keybind_manager");
        setItemNames.add(I18n.format("gui.inventory.item.keybind_manager.name"));
        itemTooltips.put("keybind_manager", I18n.format("gui.inventory.item.keybind_manager.tooltip"));
        setItems.add("profile_manager");
        setItemNames.add(I18n.format("gui.inventory.item.profile_manager.name"));
        itemTooltips.put("profile_manager", I18n.format("gui.inventory.item.profile_manager.tooltip"));
        setItems.add("chat_optimization");
        setItemNames.add(I18n.format("gui.inventory.item.chat_optimization.name"));
        itemTooltips.put("chat_optimization", I18n.format("gui.inventory.item.chat_optimization.tooltip"));

        setItems.add("toggle_auto_pickup");
        setItemNames.add(I18n.format("gui.inventory.item.auto_pickup.name"));
        itemTooltips.put("toggle_auto_pickup", I18n.format("gui.inventory.item.auto_pickup.tooltip"));

        setItems.add("toggle_auto_use_item");
        setItemNames.add(I18n.format("gui.inventory.item.auto_use_item.name"));
        itemTooltips.put("toggle_auto_use_item", I18n.format("gui.inventory.item.auto_use_item.tooltip"));

        setItems.add("block_replacement_config");
        setItemNames.add(I18n.format("gui.inventory.item.block_replacement.name"));
        itemTooltips.put("block_replacement_config", I18n.format("gui.inventory.item.block_replacement.tooltip"));

        setItems.add("warehouse_manager");
        setItemNames.add(I18n.format("gui.inventory.item.warehouse_manager.name"));
        itemTooltips.put("warehouse_manager", I18n.format("gui.inventory.item.warehouse_manager.tooltip"));

        setItems.add("baritone_settings");
        setItemNames.add(I18n.format("gui.inventory.item.baritone_settings.name"));
        itemTooltips.put("baritone_settings", I18n.format("gui.inventory.item.baritone_settings.tooltip"));

        setItems.add("baritone_command_table");
        setItemNames.add(I18n.format("gui.inventory.item.baritone_command_table.name"));
        itemTooltips.put("baritone_command_table", I18n.format("gui.inventory.item.baritone_command_table.tooltip"));

        setItems.add("baritone_parkour");
        setItemNames.add(I18n.format("gui.inventory.item.baritone_parkour.name"));
        itemTooltips.put("baritone_parkour", I18n.format("gui.inventory.item.baritone_parkour.tooltip"));

        setItems.add("setloop");
        setItemNames.add(I18n.format("gui.inventory.item.setloop.name"));
        itemTooltips.put("setloop", I18n.format("gui.inventory.item.setloop.tooltip"));

        categoryItems.put(I18n.format("gui.inventory.category.common"), setItems);
        categoryItemNames.put(I18n.format("gui.inventory.category.common"), setItemNames);
        GuiInventory.rebuildCommonSections(setItems);

        List<String> debugItems = new ArrayList<>();
        List<String> debugItemNames = new ArrayList<>();
        debugItems.add("debug_settings");
        debugItemNames.add(I18n.format("gui.inventory.item.debug_settings.name"));
        itemTooltips.put("debug_settings", I18n.format("gui.inventory.item.debug_settings.tooltip"));
        debugItems.add("current_resolution_info");
        debugItemNames.add(I18n.format("gui.inventory.item.resolution_info.name"));
        itemTooltips.put("current_resolution_info", I18n.format("gui.inventory.item.resolution_info.tooltip"));
        debugItems.add("reload_paths");
        debugItemNames.add(I18n.format("gui.inventory.item.reload_paths.name"));
        itemTooltips.put("reload_paths", I18n.format("gui.inventory.item.reload_paths.tooltip"));
        debugItems.add("player_equipment_viewer");
        debugItemNames.add(I18n.format("gui.inventory.item.player_equipment.name"));
        itemTooltips.put("player_equipment_viewer", I18n.format("gui.inventory.item.player_equipment.tooltip"));
        debugItems.add("packet_handler");
        debugItemNames.add(I18n.format("gui.inventory.item.packet_handler.name"));
        itemTooltips.put("packet_handler", I18n.format("gui.inventory.item.packet_handler.tooltip"));
        debugItems.add("gui_inspector_manager");
        debugItemNames.add(I18n.format("gui.inventory.item.gui_inspector_manager.name"));
        itemTooltips.put("gui_inspector_manager", I18n.format("gui.inventory.item.gui_inspector_manager.tooltip"));
        debugItems.add("performance_monitor");
        debugItemNames.add(I18n.format("gui.inventory.item.performance_monitor.name"));
        itemTooltips.put("performance_monitor", I18n.format("gui.inventory.item.performance_monitor.tooltip"));
        debugItems.add("terrain_scanner");
        debugItemNames.add(I18n.format("gui.inventory.item.terrain_scanner.name"));
        itemTooltips.put("terrain_scanner", I18n.format("gui.inventory.item.terrain_scanner.tooltip"));

        debugItems.add("memory_manager");
        debugItemNames.add(I18n.format("gui.inventory.item.memory_manager.name"));
        itemTooltips.put("memory_manager", I18n.format("gui.inventory.item.memory_manager.tooltip"));

        categoryItems.put(I18n.format("gui.inventory.category.debug"), debugItems);
        categoryItemNames.put(I18n.format("gui.inventory.category.debug"), debugItemNames);

        for (GroupDef group : otherFeatureGroups) {
            if (group == null || group.name == null || group.name.trim().isEmpty()) {
                continue;
            }
            String categoryName = otherFeaturePrefix + group.name.trim();
            if (!otherFeatureCategories.contains(categoryName)) {
                continue;
            }
            List<String> featureItems = new ArrayList<>();
            List<String> featureItemNames = new ArrayList<>();
            if (group.features != null) {
                for (FeatureDef feature : group.features) {
                    if (feature == null || feature.id == null || feature.id.trim().isEmpty()) {
                        continue;
                    }
                    String command = ModernOtherFeatureSettingsTab.commandFor(feature.id);
                    String displayName = feature.name == null || feature.name.trim().isEmpty()
                            ? feature.id.trim()
                            : feature.name.trim();
                    featureItems.add(command);
                    featureItemNames.add(displayName);
                    String description = feature.description == null ? "" : feature.description.trim();
                    itemTooltips.put(command, description.isEmpty()
                            ? I18n.format("gui.modern.invbase.fmt.feature_tip", displayName)
                            : description);
                }
            }
            categoryItems.put(categoryName, featureItems);
            categoryItemNames.put(categoryName, featureItemNames);
        }

        for (String categoryName : categories) {
            if (categoryName.equals(I18n.format("gui.inventory.category.common"))
                    || categoryName.equals(I18n.format("gui.inventory.category.debug"))
                    || otherFeatureCategories.contains(categoryName))
                continue;

            List<String> pathItems = new ArrayList<>();
            List<String> pathItemNames = new ArrayList<>();

            List<PathSequence> categorySequences = new ArrayList<>();

            for (PathSequence sequence : PathSequenceManager.getAllSequences()) {
                String seqCategory = sequence.getCategory();
                if (categoryName.equals(seqCategory)) {
                    categorySequences.add(sequence);
                }
            }

            for (PathSequence sequence : categorySequences) {
                String command = sequence.isCustom() ? "custom_path:" : "path:";
                command += sequence.getName();
                pathItems.add(command);

                String displayName = sequence.getName();
                pathItemNames.add(displayName);
                String typeName = sequence.isCustom() ? I18n.format("gui.inventory.path_type.custom")
                        : I18n.format("gui.inventory.path_type.builtin");
                String baseTooltip = I18n.format("gui.inventory.path.tooltip", displayName, typeName);
                String note = sequence.getNote();
                if (note != null) {
                    note = note.trim();
                }
                if (note != null && !note.isEmpty()) {
                    itemTooltips.put(command, I18n.format("gui.modern.invbase.fmt.note_tip", baseTooltip, note));
                } else {
                    itemTooltips.put(command, baseTooltip);
                }
            }
            categoryItems.put(categoryName, pathItems);
            categoryItemNames.put(categoryName, pathItemNames);
        }

        GuiInventory.clampCurrentPageToCategoryBounds();
    }

    protected static void closeOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        if (DetachedSwingWindowManager.isDetached()) {
            return;
        }
        zszlScriptMod.isGuiVisible = false;
        if (mc.currentScreen instanceof GuiModernMainScreen) {
            mc.displayGuiScreen(null);
        } else if (mc.currentScreen == null) {
            mc.mouseHelper.grabMouseCursor();
        }
    }

    protected static void drawRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
    }

    protected static void drawHorizontalLine(int startX, int endX, int y, int color) {
        if (endX < startX) {
            int i = startX;
            startX = endX;
            endX = i;
        }
        drawRect(startX, y, endX + 1, y + 1, color);
    }

    protected static void drawVerticalLine(int x, int startY, int endY, int color) {
        if (endY < startY) {
            int i = startY;
            startY = endY;
            endY = i;
        }
        drawRect(x, startY + 1, x + 1, endY, color);
    }

    protected static void drawCenteredString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        int resolved = GuiTheme.resolveTextColor(text, color);
        fontRenderer.drawStringWithShadow(text, (float) (x - fontRenderer.getStringWidth(text) / 2), (float) y,
                resolved);
    }

    protected static void drawString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        int resolved = GuiTheme.resolveTextColor(text, color);
        fontRenderer.drawStringWithShadow(text, (float) x, (float) y, resolved);
    }


}
