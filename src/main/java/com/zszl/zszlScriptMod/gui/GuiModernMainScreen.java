package com.zszl.zszlScriptMod.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Deque;
import java.util.Set;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.modern.ModernBaritoneParkourSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernChatOptimizationSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernKillAuraSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernAutoEatSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernAutoFishingSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernDebugSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernFlySettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernLoopCountSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernOtherFeatureSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernResolutionSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernUtilitySettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTabCatalog;
import com.zszl.zszlScriptMod.gui.modern.ModernTextInputFocus;
import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.core.ModernMouseCapture;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabRegistry;
import com.zszl.zszlScriptMod.gui.modern.packet.PacketWorkbenchTab;
import com.zszl.zszlScriptMod.gui.modern.path.ModernPathWorkbenchTab;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.system.BindableAction;
import com.zszl.zszlScriptMod.system.KeybindManager;
import com.zszl.zszlScriptMod.system.KeybindManager.Keybind;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.UpdateChecker;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

/**
 * Modern main dashboard. This screen owns rendering and hit testing directly
 * so it does not inherit the vanilla rectangular button treatment.
 */
public class GuiModernMainScreen extends GuiScreen implements ModernTooltipSupport.OwnsTooltipAnchors {

    private static final int TAB_STRIP_HEIGHT = 35 + ModernHoverScrollbar.GUTTER;
    private static final int RESIZE_ZONE = 5;
    private static final int WINDOW_MARGIN = 6;
    private static final int COMMAND_PALETTE_ROW_HEIGHT = 42;
    private static final int INLINE_POPUP_WIDTH = 286;
    private static final int INLINE_POPUP_HEIGHT = 122;
    private static final int HIDDEN_CATEGORY_ROW_HEIGHT = 24;
    private static final int PATH_DRAG_START_DISTANCE = 4;
    private static final int MIN_MODERN_UI_SCALE_PERCENT = 100;
    private static final int MAX_MODERN_UI_SCALE_PERCENT = 300;
    private static final int MODERN_UI_SCALE_STEP_PERCENT = 25;
    private static final int DEFAULT_MODERN_UI_SCALE_PERCENT = 100;

    private static GuiModernMainScreen persistentScreen;
    private static final LinkedList<String> recentPaletteCommands = new LinkedList<>();
    private DetachedSharpFontRenderer modernFontRenderer;

    FontRenderer replaceDetachedFontRenderer(FontRenderer replacement) {
        FontRenderer previous = this.fontRenderer;
        this.fontRenderer = replacement;
        return previous;
    }

    @Override
    public FontRenderer getTooltipFontRenderer() {
        return this.fontRenderer;
    }

    public static void captureMouseBeforeMenu(Minecraft minecraft) {
        ModernMouseCapture.captureBeforeMenu(minecraft);
    }

    public static ModernMouseCapture.Capture getLastMouseBeforeMenu() {
        return ModernMouseCapture.getLastBeforeMenu();
    }

    private enum TargetType {
        CATEGORY,
        CATEGORY_EXPAND,
        OTHER_FEATURE_GROUP,
        OTHER_FEATURE_HUD_POSITION,
        NAVIGATION_TOGGLE,
        COMMAND,
        UTILITY,
        DETACH,
        CLOSE,
        STOP_FOREGROUND,
        STOP_BACKGROUND,
        PATHS,
        COMMAND_PALETTE,
        TOOLS,
        CLEAR_SEARCH,
        TAB_SELECT,
        TAB_CLOSE,
        AUTO_FOCUS,
        AUTO_PAUSE,
        RUNNING_STATUS_TOGGLE,
        DASHBOARD_SETTINGS,
        GLOBAL_SETTINGS,
        DASHBOARD_TOOL,
        DASHBOARD_TOOL_OPTION,
        DASHBOARD_GROUP,
        DASHBOARD_PAGE,
        INFO
    }

    private enum TabId {
        DASHBOARD,
        AUTO_EAT,
        AUTO_FISHING,
        FLY,
        DEBUG,
        LOOP_COUNT,
        RESOLUTION,
        AUTO_PICKUP,
        AUTO_USE_ITEM,
        BLOCK_REPLACEMENT,
        CHAT_OPTIMIZATION,
        BARITONE_PARKOUR,
        KILL_AURA,
        WAREHOUSE,
        BARITONE,
        PROFILE,
        KEYBINDS,
        OTHER_FEATURE,
        PACKET,
        GUI_INSPECTOR,
        PERFORMANCE,
        TERRAIN_SCANNER,
        MEMORY,
        GENERAL_SETTINGS
    }

    private enum ResizeHandle {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        ResizeHandle(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    private static final class HitTarget {
        private final TargetType type;
        private final ModernMainLayout.Rect bounds;
        private final String value;
        private final GuiInventoryBase.CategoryTreeRow categoryRow;

        private HitTarget(TargetType type, ModernMainLayout.Rect bounds, String value) {
            this(type, bounds, value, null);
        }

        private HitTarget(TargetType type, ModernMainLayout.Rect bounds, GuiInventoryBase.CategoryTreeRow categoryRow) {
            this(type, bounds, null, categoryRow);
        }

        private HitTarget(TargetType type, ModernMainLayout.Rect bounds, String value,
                GuiInventoryBase.CategoryTreeRow categoryRow) {
            this.type = type;
            this.bounds = bounds;
            this.value = value;
            this.categoryRow = categoryRow;
        }
    }

    private static final class DashboardToolOption {
        private final String id;
        private final String label;

        private DashboardToolOption(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final class NavigationRow {
        private final GuiInventoryBase.CategoryTreeRow categoryRow;
        private final boolean otherFeatureGroup;

        private NavigationRow(GuiInventoryBase.CategoryTreeRow categoryRow, boolean otherFeatureGroup) {
            this.categoryRow = categoryRow;
            this.otherFeatureGroup = otherFeatureGroup;
        }
    }

    private static final class MenuCard {
        private final String command;
        private final String title;
        private final String tooltip;
        private final String groupKey;
        private final String sourceCategory;

        private MenuCard(String command, String title, String tooltip, String groupKey, String sourceCategory) {
            this.command = command == null ? "" : command;
            this.title = title == null ? "" : title;
            this.tooltip = tooltip == null ? "" : tooltip;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.sourceCategory = sourceCategory == null ? "" : sourceCategory;
        }
    }

    private static final class DashboardGroup {
        private final String key;
        private final String title;
        private final List<MenuCard> cards = new ArrayList<>();
        private final boolean continuation;
        private final String pathCategory;
        private final String pathSubCategory;

        private DashboardGroup(String key, String title) {
            this(key, title, false, "", "");
        }

        private DashboardGroup(String key, String title, boolean continuation) {
            this(key, title, continuation, "", "");
        }

        private DashboardGroup(String key, String title, String pathCategory, String pathSubCategory) {
            this(key, title, false, pathCategory, pathSubCategory);
        }

        private DashboardGroup(DashboardGroup source, boolean continuation) {
            this(source.key, source.title, continuation, source.pathCategory, source.pathSubCategory);
        }

        private DashboardGroup(String key, String title, boolean continuation, String pathCategory,
                String pathSubCategory) {
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.continuation = continuation;
            this.pathCategory = pathCategory == null ? "" : pathCategory;
            this.pathSubCategory = pathSubCategory == null ? "" : pathSubCategory;
        }

        private boolean acceptsPathDrops() {
            return !pathCategory.isEmpty();
        }
    }

    private static final class DashboardPage {
        private final List<DashboardGroup> groups = new ArrayList<>();
    }

    private static final class PathDropTarget {
        private final String category;
        private final String subCategory;
        private final String label;
        private final ModernMainLayout.Rect bounds;
        private final String sequenceName;
        private final boolean placeAfter;

        private PathDropTarget(String category, String subCategory, String label, ModernMainLayout.Rect bounds) {
            this(category, subCategory, label, bounds, "", false);
        }

        private PathDropTarget(String category, String subCategory, String label, ModernMainLayout.Rect bounds,
                String sequenceName, boolean placeAfter) {
            this.category = category == null ? "" : category;
            this.subCategory = subCategory == null ? "" : subCategory;
            this.label = label == null ? "" : label;
            this.bounds = bounds;
            this.sequenceName = sequenceName;
            this.placeAfter = placeAfter;
        }

        private boolean matches(String otherCategory, String otherSubCategory) {
            return category.equals(otherCategory == null ? "" : otherCategory)
                    && subCategory.equalsIgnoreCase(otherSubCategory == null ? "" : otherSubCategory);
        }
    }

    private static final class ToolEntry {
        private final String action;
        private final String title;

        private ToolEntry(String action, String title) {
            this.action = action;
            this.title = title;
        }
    }

    private static final class CommandPaletteEntry {
        private final String id;
        private final String title;
        private final String description;
        private final String group;
        private final String menuCommand;
        private final String utilityAction;
        private final BindableAction action;
        private final String keybindTarget;

        private CommandPaletteEntry(String id, String title, String description, String group, String menuCommand,
                String utilityAction, BindableAction action, String keybindTarget) {
            this.id = id == null ? "" : id;
            this.title = cleanPaletteText(title, "");
            this.description = cleanPaletteText(description, "");
            this.group = cleanPaletteText(group, "");
            this.menuCommand = menuCommand;
            this.utilityAction = utilityAction;
            this.action = action;
            this.keybindTarget = keybindTarget;
        }
    }

    private static final class ClosedTab {
        private final TabKey key;
        private final String command;

        private ClosedTab(TabKey key, String command) {
            this.key = key;
            this.command = command == null ? "" : command;
        }
    }

    /**
     * Identity of one open tab. Fixed feature tabs map to a TabId slot, while
     * registry-embedded workbenches (path manager, trigger rules, action
     * editor, ...) get one tab per command so opening a new workbench never
     * overwrites another.
     */
    private static final class TabKey {
        final TabId fixedId;
        final String command;

        private TabKey(TabId fixedId, String command) {
            this.fixedId = fixedId;
            this.command = command == null ? "" : command;
        }

        static TabKey dashboard() {
            return new TabKey(TabId.DASHBOARD, "");
        }

        static TabKey fixed(TabId id, String command) {
            return new TabKey(id, command);
        }

        static TabKey ofCommand(String command) {
            return new TabKey(null, command);
        }

        boolean isDashboard() {
            return fixedId == TabId.DASHBOARD;
        }

        boolean isOtherFeature() {
            return fixedId == TabId.OTHER_FEATURE;
        }

        String encode() {
            return fixedId == null ? "cmd:" + command : "fixed:" + fixedId.name();
        }

        static TabKey decode(String value) {
            if (value == null || value.isEmpty()) {
                return TabKey.dashboard();
            }
            if (value.startsWith("cmd:")) {
                return TabKey.ofCommand(value.substring(4));
            }
            if (value.startsWith("fixed:")) {
                return TabKey.fixed(TabId.valueOf(value.substring(6)), "");
            }
            return TabKey.dashboard();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TabKey)) {
                return false;
            }
            TabKey that = (TabKey) other;
            return fixedId == that.fixedId && (fixedId != null || command.equals(that.command));
        }

        @Override
        public int hashCode() {
            return fixedId == null ? 31 + command.hashCode() : fixedId.hashCode();
        }
    }

    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final List<TabKey> openTabs = new ArrayList<>();
    private final Deque<ClosedTab> closedTabs = new ArrayDeque<>();
    private final Map<TabKey, ModernSettingsTab> settingsTabs = new HashMap<>();
    /** Keeps one draft instance per concrete command while users switch tabs. */
    private final Map<String, ModernSettingsTab> settingsTabDraftCache = new HashMap<>();
    private final ModernTabRegistry tabRegistry = ModernTabCatalog.createRegistry();
    private final ModernContextMenu modernContextMenu = new ModernContextMenu();
    private final List<CommandPaletteEntry> commandPaletteEntries = new ArrayList<>();
    private final List<ModernMainLayout.Rect> commandPaletteRowBounds = new ArrayList<>();
    private final List<ModernMainLayout.Rect> commandPaletteSettingsBounds = new ArrayList<>();
    private final List<PathDropTarget> dashboardPathDropTargets = new ArrayList<>();
    private String initiallyOpenSettingsCommand;
    private String activeOtherFeatureCommand = "";
    private GuiTextField searchField;
    private GuiTextField commandPaletteSearchField;
    private ModernMainLayout.Layout lastLayout;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect commandPaletteBounds;
    private ModernMainLayout.Rect commandPaletteSearchBounds;
    private ModernMainLayout.Rect commandPaletteClearBounds;
    private ModernMainLayout.Rect commandPaletteListBounds;
    private ModernMainLayout.Rect tabStripBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private ModernMainLayout.Rect navigationClipBounds;
    private ModernMainLayout.Rect sidebarDividerBounds;
    private ModernMainLayout.Rect navigationScrollbarBounds;
    private ModernMainLayout.Rect navigationScrollbarThumbBounds;
    private ModernMainLayout.Rect tabScrollbarBounds;
    private ModernMainLayout.Rect tabScrollbarThumbBounds;
    private final ModernHoverScrollbar navigationScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar tabScrollbar = new ModernHoverScrollbar(ModernHoverScrollbar.Axis.HORIZONTAL);
    private final ModernHoverScrollbar dashboardScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar commandPaletteScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar hiddenCategoryPickerScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect autoFocusBounds;
    private ModernMainLayout.Rect autoPauseBounds;
    private ModernMainLayout.Rect dashboardSettingsGearBounds;
    private ModernMainLayout.Rect dashboardSettingsPanelBounds;
    private ModernMainLayout.Rect dashboardSettingsSubmenuBounds;
    private String dashboardSettingsHoverTool;
    private ModernMainLayout.Rect dashboardPreviousPageBounds;
    private ModernMainLayout.Rect dashboardNextPageBounds;
    private int cardScrollOffset;
    private int maxCardScrollOffset;
    private int dashboardPage;
    private int dashboardPageCount = 1;
    private boolean dashboardSettingsOpen;
    private boolean dashboardSettingsAnimating;
    private long dashboardSettingsAnimationStartedAt;
    private String headerAlertVersion = "";
    private long headerVersionAnimationStartedAt;
    private float headerVersionHoverProgress;
    private long headerVersionHoverFrameAt;
    private int tabScrollOffset;
    private final Map<TabKey, Integer> tabWidths = new HashMap<>();
    private int maxTabScrollOffset;
    private int navigationScrollOffset;
    private int maxNavigationScrollOffset;
    private boolean toolsOpen;
    private ModernMainLayout.Rect toolsPopoverBounds;
    private int toolsCapturedMouseButton = -1;
    private boolean commandPaletteOpen;
    private boolean commandPaletteRecentOnly;
    private int commandPaletteSelectedIndex;
    private int commandPaletteScrollOffset;
    private int commandPaletteMaxScrollOffset;
    private boolean commandPaletteDraggingScrollbar;
    private boolean inlineTextInputOpen;
    private boolean inlineConfirmationOpen;
    private boolean closeConfirmationOpen;
    private TabKey closeConfirmationTab;
    private boolean closeConfirmationCascade;
    private String pendingSettingsReplacementCommand = "";
    private String inlineTextInputTitle = "";
    private Consumer<String> inlineTextInputCallback;
    private GuiTextField inlineTextInputField;
    private String inlineConfirmationTitle = "";
    private String inlineConfirmationMessage = "";
    private Runnable inlineConfirmationCallback;
    private ModernMainLayout.Rect inlineConfirmationBounds;
    private ModernMainLayout.Rect inlineConfirmationConfirmBounds;
    private ModernMainLayout.Rect inlineConfirmationCancelBounds;
    private ModernMainLayout.Rect inlineTextInputBounds;
    private ModernMainLayout.Rect inlineTextInputFieldBounds;
    private ModernMainLayout.Rect inlineTextInputConfirmBounds;
    private ModernMainLayout.Rect inlineTextInputCancelBounds;
    private ModernMainLayout.Rect closeConfirmationBounds;
    private ModernMainLayout.Rect closeConfirmationConfirmBounds;
    private ModernMainLayout.Rect closeConfirmationCancelBounds;
    private boolean hiddenCategoryPickerOpen;
    private List<String> hiddenCategoryPickerCategories = Collections.emptyList();
    private final List<ModernMainLayout.Rect> hiddenCategoryPickerRowBounds = new ArrayList<>();
    private ModernMainLayout.Rect hiddenCategoryPickerBounds;
    private ModernMainLayout.Rect hiddenCategoryPickerListBounds;
    private int hiddenCategoryPickerScrollOffset;
    private int hiddenCategoryPickerMaxScrollOffset;
    private int contextMenuAnchorX;
    private int contextMenuAnchorY;
    private boolean sidebarCollapsed;
    private boolean otherFeatureGroupsExpanded;
    private int sidebarWidth = -1;
    private TabKey activeTab = TabKey.dashboard();
    private String hoveredTooltip = "";
    private int hoveredTooltipX;
    private int hoveredTooltipY;
    private String draggedPathCommand = "";
    private String draggedPathSequenceName = "";
    private String draggedPathSourceCategory = "";
    private String draggedPathSourceSubCategory = "";
    private int pathDragStartMouseX;
    private int pathDragStartMouseY;
    private boolean pathDragPending;
    private boolean pathDragActive;
    private String pathDragPagerHover = "";
    private PathDropTarget currentPathDropTarget;

    private boolean windowInitialized;
    private boolean closed;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int layoutScreenWidth;
    private int layoutScreenHeight;
    private boolean draggingWindow;
    private int windowDragStartMouseX;
    private int windowDragStartMouseY;
    private int windowDragStartX;
    private int windowDragStartY;
    private boolean draggingSidebar;
    private boolean draggingNavigationScrollbar;
    private boolean draggingTabScrollbar;
    private int sidebarDragStartWidth;
    private long lastSidebarDividerClickAt;
    private int lastSidebarDividerClickX;
    private ResizeHandle resizeHandle = ResizeHandle.NONE;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartWindowX;
    private int resizeStartWindowY;
    private int resizeStartWindowWidth;
    private int resizeStartWindowHeight;

    public GuiModernMainScreen() {
        this("");
    }

    public GuiModernMainScreen(boolean openAutoEatInitially) {
        this(openAutoEatInitially ? "autoeat" : "");
    }

    private GuiModernMainScreen(String initiallyOpenSettingsCommand) {
        this.initiallyOpenSettingsCommand = initiallyOpenSettingsCommand == null ? "" : initiallyOpenSettingsCommand;
    }

    public static void openAutoEatSettings(Minecraft minecraft) {
        openSettingsTab(minecraft, "autoeat");
    }

    public static GuiModernMainScreen getPersistentScreen() {
        if (persistentScreen == null) {
            persistentScreen = new GuiModernMainScreen();
        }
        return persistentScreen;
    }

    public static void openMenu(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        captureMouseBeforeMenu(minecraft);
        GuiModernMainScreen screen = getPersistentScreen();
        GuiInventory.onOpen();
        zszlScriptMod.isGuiVisible = true;
        if (minecraft.currentScreen != screen) {
            minecraft.displayGuiScreen(screen);
        }
    }

    public static void toggleMenu(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (DetachedSwingWindowManager.isDetached()) {
            GuiModernMainScreen detachedScreen = DetachedSwingWindowManager.getActiveScreen();
            if (detachedScreen != null) {
                detachedScreen.requestCloseScreen();
            } else {
                DetachedSwingWindowManager.INSTANCE.closeDetachedMenu();
            }
            return;
        }
        if (minecraft.currentScreen instanceof GuiModernMainScreen) {
            ((GuiModernMainScreen) minecraft.currentScreen).requestCloseScreen();
        } else {
            openMenu(minecraft);
        }
    }

    public static boolean hasTextInputFocus(GuiScreen screen) {
        if (OverlayGuiHandler.isTextInputFocused(screen)) {
            return true;
        }
        if (screen instanceof GuiModernMainScreen) {
            return ((GuiModernMainScreen) screen).isTextInputFocused();
        }
        return ModernTextInputFocus.isFocused(screen);
    }

    public static boolean hasTextInputFocus(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        if (minecraft.currentScreen == null && DetachedSwingWindowManager.isDetached()) {
            GuiModernMainScreen detachedScreen = DetachedSwingWindowManager.getActiveScreen();
            return detachedScreen != null && detachedScreen.isTextInputFocused();
        }
        return hasTextInputFocus(minecraft.currentScreen);
    }

    public static void openSettingsTab(Minecraft minecraft, String command) {
        if (minecraft == null) {
            return;
        }
        if (DetachedSwingWindowManager.isDetached()) {
            GuiModernMainScreen detachedScreen = DetachedSwingWindowManager.getActiveScreen();
            if (detachedScreen != null) {
                detachedScreen.openSettingsTabForCommand(command);
                return;
            }
        }
        if (minecraft.currentScreen instanceof GuiModernMainScreen) {
            ((GuiModernMainScreen) minecraft.currentScreen).openSettingsTabForCommand(command);
            return;
        }
        GuiModernMainScreen screen = getPersistentScreen();
        screen.initiallyOpenSettingsCommand = command == null ? "" : command;
        openMenu(minecraft);
    }

    public static void adjustModernUiScale(Minecraft minecraft, int direction) {
        if (minecraft == null || direction == 0) {
            return;
        }
        if (DetachedSwingWindowManager.isDetached()) {
            DetachedSwingWindowManager.INSTANCE.adjustUiScaleFromAction(direction);
            return;
        }
        GuiModernMainScreen screen = minecraft.currentScreen instanceof GuiModernMainScreen
                ? (GuiModernMainScreen) minecraft.currentScreen : null;
        if (screen != null) {
            screen.setModernUiScale(MainUiLayoutManager.getModernUiScalePercent()
                    + direction * MODERN_UI_SCALE_STEP_PERCENT);
        }
    }

    public static void resetModernUiScale(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (DetachedSwingWindowManager.isDetached()) {
            DetachedSwingWindowManager.INSTANCE.resetUiScaleFromAction();
            return;
        }
        GuiModernMainScreen screen = minecraft.currentScreen instanceof GuiModernMainScreen
                ? (GuiModernMainScreen) minecraft.currentScreen : null;
        if (screen != null) {
            screen.setModernUiScale(DEFAULT_MODERN_UI_SCALE_PERCENT);
        }
    }

    /** Opens or closes the command launcher without changing the current tab. */
    public void toggleCommandPalette() {
        if (this.commandPaletteOpen) {
            closeCommandPalette();
            return;
        }
        openCommandPalette();
    }

    public void openRecentCommandPalette() {
        this.commandPaletteRecentOnly = true;
        openCommandPalette();
    }

    /** Opens a small in-place name editor for actions launched by the modern menu. */
    void openInlineTextInput(String title, String initialText, Consumer<String> callback) {
        if (this.fontRenderer == null) {
            return;
        }
        closeHiddenCategoryPicker();
        closeInlineConfirmation();
        modernContextMenu.close();
        commandPaletteOpen = false;
        toolsOpen = false;
        inlineTextInputTitle = title == null || title.trim().isEmpty() ? "gui.modern.main.u001" : title.trim();
        inlineTextInputCallback = callback;
        if (inlineTextInputField == null) {
            inlineTextInputField = new GuiTextField(0, this.fontRenderer, 0, 0, 1, 22);
            inlineTextInputField.setEnableBackgroundDrawing(false);
            inlineTextInputField.setMaxStringLength(128);
            inlineTextInputField.setTextColor(ModernUiRenderer.TEXT);
            inlineTextInputField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
            inlineTextInputField.setCanLoseFocus(true);
        }
        inlineTextInputField.setText(initialText == null ? "" : initialText);
        inlineTextInputField.setFocused(true);
        inlineTextInputOpen = true;
    }

    /** Opens a confirmation panel without replacing the current modern screen. */
    void openInlineConfirmation(String title, String message, Runnable callback) {
        if (this.fontRenderer == null) {
            return;
        }
        closeHiddenCategoryPicker();
        closeInlineTextInput();
        modernContextMenu.close();
        commandPaletteOpen = false;
        toolsOpen = false;
        inlineConfirmationTitle = title == null || title.trim().isEmpty() ? "gui.modern.main.u001" : title.trim();
        inlineConfirmationMessage = message == null ? "" : message.trim();
        inlineConfirmationCallback = callback;
        inlineConfirmationOpen = true;
    }

    /** Opens the hidden-category restore list at the last navigation context-menu anchor. */
    void openHiddenCategoryPicker() {
        hiddenCategoryPickerCategories = PathSequenceManager.getHiddenCategories();
        if (hiddenCategoryPickerCategories.isEmpty()) {
            closeHiddenCategoryPicker();
            return;
        }
        closeInlineTextInput();
        modernContextMenu.close();
        commandPaletteOpen = false;
        toolsOpen = false;
        hiddenCategoryPickerScrollOffset = 0;
        hiddenCategoryPickerOpen = true;
    }

    public void focusGlobalSearch() {
        if (this.searchField != null) {
            this.commandPaletteOpen = false;
            this.searchField.setFocused(true);
            focusDashboardForSearch();
        }
    }

    private void focusDashboardForSearch() {
        activeTab = TabKey.dashboard();
        ensureActiveTabVisible();
        resetDashboardPosition();
    }

    public boolean isCommandPaletteOpen() {
        return this.commandPaletteOpen;
    }

    public boolean isTextInputFocused() {
        if (this.inlineTextInputOpen) {
            return true;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            return true;
        }
        if (this.commandPaletteSearchField != null && this.commandPaletteSearchField.isFocused()) {
            return true;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        return settingsTab != null && settingsTab.isTextInputFocused();
    }

    private void openCommandPalette() {
        if (this.fontRenderer == null) {
            return;
        }
        if (this.commandPaletteSearchField == null) {
            this.commandPaletteSearchField = new GuiTextField(0, this.fontRenderer, 0, 0, 1, 18);
            this.commandPaletteSearchField.setEnableBackgroundDrawing(false);
            this.commandPaletteSearchField.setMaxStringLength(96);
            this.commandPaletteSearchField.setTextColor(ModernUiRenderer.TEXT);
            this.commandPaletteSearchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        }
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
        this.commandPaletteSearchField.setText("");
        this.commandPaletteSearchField.setFocused(true);
        this.commandPaletteOpen = true;
        this.commandPaletteSelectedIndex = 0;
        this.commandPaletteScrollOffset = 0;
        this.commandPaletteMaxScrollOffset = 0;
        this.commandPaletteDraggingScrollbar = false;
        this.toolsOpen = false;
        this.modernContextMenu.close();
        KeybindManager.syncPathSequenceKeybinds();
        refreshCommandPaletteEntries();
    }

    private void closeCommandPalette() {
        this.commandPaletteOpen = false;
        this.commandPaletteRecentOnly = false;
        this.commandPaletteSelectedIndex = 0;
        this.commandPaletteScrollOffset = 0;
        this.commandPaletteMaxScrollOffset = 0;
        this.commandPaletteDraggingScrollbar = false;
        this.commandPaletteRowBounds.clear();
        this.commandPaletteSettingsBounds.clear();
        if (this.commandPaletteSearchField != null) {
            this.commandPaletteSearchField.setFocused(false);
        }
        this.commandPaletteBounds = null;
        this.commandPaletteSearchBounds = null;
        this.commandPaletteListBounds = null;
    }

    @Override
    public void initGui() {
        closed = false;
        headerAlertVersion = "";
        headerVersionHoverProgress = 0.0F;
        headerVersionHoverFrameAt = 0L;
        UpdateChecker.fetchVersionAndChangelog();
        UpdateChecker.requestRefreshIfDue(60L * 60L * 1000L);
        closeCloseConfirmation();
        Keyboard.enableRepeatEvents(true);
        zszlScriptMod.isGuiVisible = true;
        MainUiLayoutManager.ensureLoaded();
        if (modernFontRenderer == null) {
            modernFontRenderer = new DetachedSharpFontRenderer(this.mc, this.fontRenderer);
        }
        this.fontRenderer = modernFontRenderer;
        if (sidebarWidth <= 0) {
            int storedWidth = MainUiLayoutManager.getModernSidebarWidth();
            sidebarWidth = storedWidth > 0 ? storedWidth : ModernMainLayout.DEFAULT_SIDEBAR_WIDTH;
        }
        String existingSearch = searchField == null ? "" : searchField.getText();
        searchField = new GuiTextField(0, this.fontRenderer, 0, 0, 1, 18);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(96);
        searchField.setTextColor(ModernUiRenderer.TEXT);
        searchField.setDisabledTextColour(ModernUiRenderer.SUBTLE_TEXT);
        searchField.setText(existingSearch);
        if (openTabs.isEmpty()) {
            openTabs.add(TabKey.dashboard());
        }
        Set<ModernSettingsTab> initializedTabs = new HashSet<>();
        for (ModernSettingsTab tab : settingsTabs.values()) {
            if (tab != null && initializedTabs.add(tab)) {
                tab.ensureInitialized(this.fontRenderer);
            }
        }
        GuiInventory.refreshGuiLists();
        if (!initiallyOpenSettingsCommand.isEmpty()) {
            openSettingsTabForCommand(initiallyOpenSettingsCommand);
            initiallyOpenSettingsCommand = "";
        }
    }

    @Override
    public void updateScreen() {
        if (closed) {
            return;
        }
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
        if (this.commandPaletteSearchField != null) {
            this.commandPaletteSearchField.updateCursorCounter();
        }
        if (this.inlineTextInputField != null && this.inlineTextInputOpen) {
            this.inlineTextInputField.updateCursorCounter();
        }
        Set<ModernSettingsTab> updatedTabs = new HashSet<>();
        for (ModernSettingsTab tab : settingsTabs.values()) {
            if (tab != null && updatedTabs.add(tab)) {
                tab.updateScreen();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean detachedScreen = DetachedSwingWindowManager.isDetachedScreen(this);
        if (!detachedScreen) {
            int scalePercent = MainUiLayoutManager.getModernUiScalePercent();
            if (scalePercent != 100) {
                int screenWidth = this.width;
                int screenHeight = this.height;
                int logicalWidth = toLogicalSize(screenWidth, scalePercent);
                int logicalHeight = toLogicalSize(screenHeight, scalePercent);
                float scale = scalePercent / 100.0F;
                int originX = Math.round((screenWidth - logicalWidth * scale) / scale / 2.0F);
                int originY = Math.round((screenHeight - logicalHeight * scale) / scale / 2.0F);
                int logicalMouseX = toLogicalCoordinate(mouseX, screenWidth, logicalWidth, originX, scale);
                int logicalMouseY = toLogicalCoordinate(mouseY, screenHeight, logicalHeight, originY, scale);
                GlStateManager.pushMatrix();
                GlStateManager.translate(originX * scale, originY * scale, 0.0F);
                GlStateManager.scale(scale, scale, 1.0F);
                this.width = logicalWidth;
                this.height = logicalHeight;
                ModernUiRenderer.pushClipTransform(originX, originY, scale);
                try {
                    drawModernScreen(logicalMouseX, logicalMouseY, partialTicks, false);
                } finally {
                    ModernUiRenderer.popClipTransform();
                    this.width = screenWidth;
                    this.height = screenHeight;
                    GlStateManager.popMatrix();
                }
                return;
            }
        }
        drawModernScreen(mouseX, mouseY, partialTicks, detachedScreen);
    }

    private void drawModernScreen(int mouseX, int mouseY, float partialTicks, boolean detachedScreen) {
        // The main screen is cached and resized repeatedly. Drop frame-owned
        // tooltip anchors before rebuilding its hit regions so old positions
        // cannot intercept clicks after a layout change.
        ModernTooltipSupport.clearRegisteredInfoIcons(this);
        if (detachedScreen) {
            mouseX = DetachedSwingWindowManager.getVirtualMouseX();
            mouseY = DetachedSwingWindowManager.getVirtualMouseY();
        }
        if (!detachedScreen) {
            ensureWindowBounds();
        }
        ModernUiRenderer.drawBackdrop(this.width, this.height);
        ModernMainLayout.Rect shellBounds = detachedScreen
                ? new ModernMainLayout.Rect(0, 0, Math.max(1, this.width), Math.max(1, this.height))
                : new ModernMainLayout.Rect(windowX, windowY, windowWidth, windowHeight);
        lastLayout = ModernMainLayout.calculate(this.width, this.height, shellBounds, sidebarCollapsed, sidebarWidth);
        hitTargets.clear();
        dashboardPathDropTargets.clear();
        hoveredTooltip = "";
        navigationScrollbar.idle();
        tabScrollbar.idle();
        dashboardScrollbar.idle();
        commandPaletteScrollbar.idle();
        hiddenCategoryPickerScrollbar.idle();

        drawShell(lastLayout);
        drawHeader(lastLayout, mouseX, mouseY);
        drawNavigation(lastLayout, mouseX, mouseY);
        drawContent(lastLayout, mouseX, mouseY);
        drawSidebarDivider(lastLayout, mouseX, mouseY);
        drawToolsPopover(lastLayout, mouseX, mouseY);
        drawPathDragGhost(mouseX, mouseY);
        modernContextMenu.draw(fontRenderer, this.width, this.height, mouseX, mouseY);
        if (this.inlineTextInputOpen) {
            drawInlineTextInput(mouseX, mouseY);
        } else if (this.inlineConfirmationOpen) {
            drawInlineConfirmation(mouseX, mouseY);
        } else if (this.hiddenCategoryPickerOpen) {
            drawHiddenCategoryPicker(mouseX, mouseY);
        }
        if (!detachedScreen) {
            drawResizeAffordance(mouseX, mouseY);
        }
        if (closeConfirmationOpen) {
            drawCloseConfirmation(mouseX, mouseY);
        }
        if (this.commandPaletteOpen) {
            drawCommandPalette(mouseX, mouseY);
        }
        if (!detachedScreen) {
            RunningStatusHud.render(this.fontRenderer, this.width, this.height, mouseX, mouseY);
        }

        ModernTooltipSupport.setOverlayBounds(this, lastLayout.shell.x, lastLayout.shell.y, lastLayout.shell.width,
                lastLayout.shell.height);
        boolean floatingModalOpen = inlineTextInputOpen || hiddenCategoryPickerOpen || closeConfirmationOpen;
        ModernTooltipSupport.setSuppressed(this,
                modernContextMenu.isOpen() || toolsOpen || isGestureActive() || floatingModalOpen
                        || dashboardSettingsHoverTool != null);
        if (!hoveredTooltip.isEmpty() && !modernContextMenu.isOpen() && !toolsOpen && !floatingModalOpen) {
            ModernTooltipSupport.registerHoveredTooltip(this, hoveredTooltipX, hoveredTooltipY, hoveredTooltip);
        }
    }

    private void setModernUiScale(int scalePercent) {
        int normalized = Math.max(MIN_MODERN_UI_SCALE_PERCENT,
                Math.min(MAX_MODERN_UI_SCALE_PERCENT, scalePercent));
        normalized = Math.round(normalized / (float) MODERN_UI_SCALE_STEP_PERCENT)
                * MODERN_UI_SCALE_STEP_PERCENT;
        if (normalized == MainUiLayoutManager.getModernUiScalePercent()) {
            return;
        }
        cancelDetachedInteractions();
        MainUiLayoutManager.setModernUiScalePercent(normalized);
    }

    private static int toLogicalSize(int screenSize, int scalePercent) {
        return Math.max(1, (int) Math.ceil(screenSize * 100.0D / scalePercent));
    }

    private static int toLogicalCoordinate(int coordinate, int screenSize, int logicalSize, int origin,
            float scale) {
        if (screenSize <= 0 || logicalSize <= 0 || scale <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.min(logicalSize - 1,
                (int) Math.floor((coordinate - origin * scale) / scale)));
    }

    public int toModernMouseX(int mouseX) {
        if (DetachedSwingWindowManager.isDetachedScreen(this)) {
            return mouseX;
        }
        int scalePercent = MainUiLayoutManager.getModernUiScalePercent();
        int logicalWidth = toLogicalSize(this.width, scalePercent);
        float scale = scalePercent / 100.0F;
        int originX = Math.round((this.width - logicalWidth * scale) / scale / 2.0F);
        return toLogicalCoordinate(mouseX, this.width, logicalWidth, originX, scale);
    }

    public int toModernMouseY(int mouseY) {
        if (DetachedSwingWindowManager.isDetachedScreen(this)) {
            return mouseY;
        }
        int scalePercent = MainUiLayoutManager.getModernUiScalePercent();
        int logicalHeight = toLogicalSize(this.height, scalePercent);
        float scale = scalePercent / 100.0F;
        int originY = Math.round((this.height - logicalHeight * scale) / scale / 2.0F);
        return toLogicalCoordinate(mouseY, this.height, logicalHeight, originY, scale);
    }

    /** Draws the deferred tooltip pass in the same logical space as the shell. */
    public void drawModernTooltips(int mouseX, int mouseY) {
        if (DetachedSwingWindowManager.isDetachedScreen(this)) {
            ModernTooltipSupport.draw(this, Collections.emptyList(), mouseX, mouseY);
            return;
        }
        int scalePercent = MainUiLayoutManager.getModernUiScalePercent();
        if (scalePercent == 100) {
            ModernTooltipSupport.draw(this, Collections.emptyList(), mouseX, mouseY);
            return;
        }
        int screenWidth = this.width;
        int screenHeight = this.height;
        int logicalWidth = toLogicalSize(screenWidth, scalePercent);
        int logicalHeight = toLogicalSize(screenHeight, scalePercent);
        float scale = scalePercent / 100.0F;
        int originX = Math.round((screenWidth - logicalWidth * scale) / scale / 2.0F);
        int originY = Math.round((screenHeight - logicalHeight * scale) / scale / 2.0F);
        int logicalMouseX = toLogicalCoordinate(mouseX, screenWidth, logicalWidth, originX, scale);
        int logicalMouseY = toLogicalCoordinate(mouseY, screenHeight, logicalHeight, originY, scale);
        GlStateManager.pushMatrix();
        GlStateManager.translate(originX * scale, originY * scale, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        this.width = logicalWidth;
        this.height = logicalHeight;
        ModernUiRenderer.pushClipTransform(originX, originY, scale);
        try {
            ModernTooltipSupport.draw(this, Collections.emptyList(), logicalMouseX, logicalMouseY);
        } finally {
            ModernUiRenderer.popClipTransform();
            this.width = screenWidth;
            this.height = screenHeight;
            GlStateManager.popMatrix();
        }
    }

    private void drawShell(ModernMainLayout.Layout layout) {
        ModernMainLayout.Rect shell = layout.shell;
        // The artwork is one application-wide background. Child surfaces only add
        // translucent theme colour, so the image and Minecraft remain visible below.
        com.zszl.zszlScriptMod.gui.components.GuiTheme.drawPanelBackground(shell.x + 1, shell.y + 1,
                Math.max(1, shell.width - 2), Math.max(1, shell.height - 2));
        ModernUiRenderer.drawPanel(shell.x, shell.y, shell.width, shell.height, 8, ModernUiRenderer.SHELL,
                ModernUiRenderer.BORDER);
        ModernMainLayout.Rect header = layout.header;
        ModernMainLayout.Rect sidebar = layout.sidebar;
        ModernUiRenderer.drawRoundedRect(header.x + 1, header.y + 1, header.width - 2, header.height - 1, 7,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawDivider(header.x + 1, header.bottom() - 1, header.width - 2, ModernUiRenderer.BORDER);
        Gui.drawRect(sidebar.x + 1, sidebar.y, sidebar.right(), sidebar.bottom() - 1, ModernUiRenderer.SHELL);
        if (layout.sidebarCollapsed) {
            ModernUiRenderer.drawVerticalDivider(sidebar.right() - 1, sidebar.y, sidebar.height,
                    ModernUiRenderer.BORDER);
        }
    }

    private void drawHeaderVersion(String text, boolean mismatch, String latestVersion, int x, int y, int width,
            int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x - 3, y - 2, width,
                fontRenderer.FONT_HEIGHT + 4);
        boolean hovered = bounds.contains(mouseX, mouseY);
        hitTargets.add(new HitTarget(TargetType.UTILITY, bounds, "update"));
        long now = System.nanoTime();
        float step = headerVersionHoverFrameAt == 0L ? 0.0F
                : Math.min(1.0F, (now - headerVersionHoverFrameAt) / 180_000_000.0F);
        headerVersionHoverFrameAt = now;
        headerVersionHoverProgress = hovered ? Math.min(1.0F, headerVersionHoverProgress + step)
                : Math.max(0.0F, headerVersionHoverProgress - step);
        float easedHover = headerVersionHoverProgress * headerVersionHoverProgress
                * (3.0F - 2.0F * headerVersionHoverProgress);
        float scale = 1.0F + 0.12F * easedHover;
        int textWidth = Math.max(1, (int) ((width - 5) / 1.12F));
        int color = ModernUiRenderer.SUBTLE_TEXT;
        int offsetX = 0;
        if (mismatch) {
            // The persistent screen resets this on every open; async results also start a fresh animation.
            if (!latestVersion.equals(headerAlertVersion)) {
                headerAlertVersion = latestVersion;
                headerVersionAnimationStartedAt = System.nanoTime();
            }
            double elapsed = (System.nanoTime() - headerVersionAnimationStartedAt) / 1_000_000.0;
            float pulse = elapsed < 3600.0
                    ? (float) ((1.0 - Math.cos(elapsed * Math.PI * 2.0 / 900.0)) * 0.5) : 0.0F;
            offsetX = elapsed < 3600.0 ? (int) Math.round(Math.sin(elapsed * Math.PI * 2.0 / 300.0) * pulse * 2.0) : 0;
            int fill = ModernUiRenderer.lerpRgb(ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT, 0.22F + 0.60F * pulse);
            ModernUiRenderer.drawRoundedRect(x - 3, y - 2, Math.min(width, fontRenderer.getStringWidth(text) + 8),
                    fontRenderer.FONT_HEIGHT + 4, 3, fill);
            color = ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, fill);
        } else {
            headerAlertVersion = "";
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + offsetX, y - (scale - 1.0F) * fontRenderer.FONT_HEIGHT / 2.0F, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            ModernUiRenderer.drawText(fontRenderer, text, 0, 0, color, textWidth);
            if (mismatch && fontRenderer instanceof DetachedSharpFontRenderer) {
                // The sharp renderer strips formatting codes, so thicken this alert explicitly.
                fontRenderer.drawString(ModernUiRenderer.ellipsize(fontRenderer, text, textWidth),
                        0.65F, 0, color, false);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawHeader(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        ModernMainLayout.Rect header = layout.header;
        int buttonSize = Math.max(18, Math.min(28, header.height - 14));
        int buttonGap = 5;
        int closeX = header.right() - 14 - buttonSize;
        int detachX = closeX - buttonGap - buttonSize;
        int toolsX = detachX - buttonGap - buttonSize;
        int pathsX = toolsX - buttonGap - buttonSize;
        int backgroundStopX = pathsX - buttonGap - buttonSize;
        int foregroundStopX = backgroundStopX - buttonGap - buttonSize;
        int actionY = header.y + (header.height - buttonSize) / 2;

        int labelX = header.x + 16;
        int globalSettingsX = foregroundStopX - buttonGap - buttonSize;
        int searchRight = globalSettingsX - 7;
        int commandButtonSize = 22;
        String currentVersion = zszlScriptMod.VERSION.replaceFirst("^[vV]", "").trim();
        String remoteVersion = UpdateChecker.latestVersion;
        String latestVersion = remoteVersion == null ? "..." : remoteVersion.replaceFirst("^[vV]", "").trim();
        boolean versionMismatch = latestVersion.matches("[0-9]+(?:\\.[0-9]+)*")
                && !currentVersion.equals(latestVersion);
        String versionText = (versionMismatch ? TextFormatting.BOLD.toString() : "")
                + I18n.format("gui.changelog.header_versions", currentVersion, latestVersion);
        int brandWidth = Math.max(85, (int) Math.ceil(fontRenderer.getStringWidth(versionText) * 1.12F) + 10);
        int commandButtonX = Math.min(labelX + 25 + brandWidth + 6, searchRight - commandButtonSize - 35);
        commandButtonX = Math.max(labelX + 54, commandButtonX);
        int searchX = commandButtonX + commandButtonSize + 5;
        int labelWidth = Math.max(24, commandButtonX - (labelX + 25) - 6);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.BRAND, labelX,
                header.y + (header.height - 17) / 2, 17, ModernUiRenderer.ACCENT);
        int brandY = header.y + Math.max(2, (header.height - 33) / 2);
        ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.brand", "MythosScript"), labelX + 25, brandY, ModernUiRenderer.TEXT,
                labelWidth);
        if (labelWidth >= 55) {
            ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.control_center", "gui.modern.main.u002"), labelX + 25, brandY + 11,
                    ModernUiRenderer.SUBTLE_TEXT, labelWidth);
        }
        drawHeaderVersion(versionText, versionMismatch, latestVersion, labelX + 25, brandY + 22, labelWidth,
                mouseX, mouseY);

        drawHeaderIcon(foregroundStopX, actionY, buttonSize, mouseX, mouseY, TargetType.STOP_FOREGROUND, 0);
        drawHeaderIcon(backgroundStopX, actionY, buttonSize, mouseX, mouseY, TargetType.STOP_BACKGROUND, 1);
        drawHeaderIcon(pathsX, actionY, buttonSize, mouseX, mouseY, TargetType.PATHS, 2);
        drawHeaderIcon(toolsX, actionY, buttonSize, mouseX, mouseY, TargetType.TOOLS, 3);
        drawHeaderIcon(detachX, actionY, buttonSize, mouseX, mouseY, TargetType.DETACH, 4);
        drawHeaderIcon(closeX, actionY, buttonSize, mouseX, mouseY, TargetType.CLOSE, 5);
        drawHeaderIcon(globalSettingsX, actionY, buttonSize, mouseX, mouseY, TargetType.GLOBAL_SETTINGS, 6);

        ModernMainLayout.Rect commandBounds = new ModernMainLayout.Rect(commandButtonX,
                header.y + (header.height - commandButtonSize) / 2, commandButtonSize, commandButtonSize);
        boolean commandHovered = commandBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(commandBounds.x, commandBounds.y, commandBounds.width, commandBounds.height,
                5, commandHovered || commandPaletteOpen ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                commandPaletteOpen ? ModernUiRenderer.ACCENT_DIM
                        : commandHovered ? 0xFF637783 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawCommandIcon(commandBounds.x + 5, commandBounds.y + 5,
                commandHovered || commandPaletteOpen ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        hitTargets.add(new HitTarget(TargetType.COMMAND_PALETTE, commandBounds, ""));
        String commandPaletteShortcut = formatKeybindLabel(
                KeybindManager.keybinds.get(BindableAction.OPEN_COMMAND_PALETTE));
        ModernTooltipSupport.registerTooltipAnchor(this, commandBounds.x, commandBounds.y,
                commandBounds.width, commandBounds.height,
                tr("gui.modern.main.palette.title", "打开命令面板") + "\n"
                        + tr("gui.modern.main.palette.desc", "搜索并执行功能、路径和快捷动作。") + "\n"
                        + commandPaletteShortcut);

        int searchWidth = Math.max(1, searchRight - searchX);
        searchBounds = new ModernMainLayout.Rect(searchX, header.y + (header.height - 22) / 2, searchWidth, 22);
        boolean searchHovered = searchBounds.contains(mouseX, mouseY);
        boolean searchFocused = searchField != null && searchField.isFocused();
        int searchBorder = searchFocused ? ModernUiRenderer.ACCENT : searchHovered ? 0xFF586C79 : ModernUiRenderer.BORDER;
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 5,
                0xFF101820, searchBorder);
        if (searchBounds.width >= 23) {
            ModernUiRenderer.drawSearchIcon(searchBounds.x + 7, searchBounds.y + 5, ModernUiRenderer.SUBTLE_TEXT);
        }

        if (searchField != null) {
            int textLeft = searchBounds.x + (searchBounds.width >= 23 ? 23 : 5);
            int textRightPadding = searchField.getText().isEmpty() ? 6 : 19;
            searchField.x = textLeft;
            searchField.y = searchBounds.y + (searchBounds.height - fontRenderer.FONT_HEIGHT) / 2;
            searchField.width = Math.max(1, searchBounds.width - (textLeft - searchBounds.x) - textRightPadding);
            searchField.height = fontRenderer.FONT_HEIGHT + 2;
            if (searchField.getText().isEmpty() && !searchField.isFocused() && searchBounds.width >= 76) {
                ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.search_placeholder", "gui.modern.main.u003"), textLeft, searchField.y,
                        ModernUiRenderer.MUTED_TEXT, searchField.width);
            }
            ModernUiRenderer.reflowTextField(searchField);
            ModernUiRenderer.drawTextField(searchField);
            if (!searchField.getText().isEmpty() && searchBounds.width >= 20) {
                ModernMainLayout.Rect clearBounds = new ModernMainLayout.Rect(searchBounds.right() - 18,
                        searchBounds.y + 5, 13, 13);
                boolean clearHovered = clearBounds.contains(mouseX, mouseY);
                if (clearHovered) {
                    ModernUiRenderer.drawRoundedRect(clearBounds.x - 2, clearBounds.y - 2, clearBounds.width + 4,
                            clearBounds.height + 4, 4, ModernUiRenderer.SURFACE_HOVER);
                }
                ModernUiRenderer.drawCloseIcon(clearBounds.x + 2, clearBounds.y + 2,
                        clearHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
                hitTargets.add(new HitTarget(TargetType.CLEAR_SEARCH, clearBounds, ""));
            }
        }
    }

    private void drawHeaderIcon(int x, int y, int size, int mouseX, int mouseY, TargetType type, int icon) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, size, size);
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(x, y, size, size, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                hovered ? 0xFF637783 : ModernUiRenderer.BORDER_SUBTLE);
        int iconColor = type == TargetType.STOP_FOREGROUND ? ModernUiRenderer.ACCENT
                : type == TargetType.STOP_BACKGROUND ? ModernUiRenderer.WARNING
                        : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT;
        int iconX = x + (size - 13) / 2;
        int iconY = y + (size - 13) / 2;
        if (icon == 0) {
            ModernUiRenderer.drawStopIcon(iconX + 2, iconY + 2, iconColor);
        } else if (icon == 1) {
            ModernUiRenderer.drawBackgroundStopIcon(iconX, iconY, iconColor);
        } else if (icon == 2) {
            ModernUiRenderer.drawRouteIcon(iconX, iconY, iconColor);
        } else if (icon == 3) {
            ModernUiRenderer.drawMoreIcon(iconX + 1, iconY + 1, iconColor);
        } else if (icon == 4) {
            ModernUiRenderer.drawExternalWindowIcon(iconX, iconY, iconColor);
        } else if (icon == 6) {
            ModernUiRenderer.drawSettingsIcon(iconX + 1, iconY + 1, iconColor);
        } else {
            ModernUiRenderer.drawCloseIcon(iconX + 2, iconY + 2, iconColor);
        }
        hitTargets.add(new HitTarget(type, bounds, ""));
        String tooltip = type == TargetType.STOP_FOREGROUND ? "gui.modern.main.u004"
                : type == TargetType.STOP_BACKGROUND ? "gui.modern.main.u005"
                : type == TargetType.PATHS ? "gui.modern.main.u006"
                        : type == TargetType.TOOLS ? "gui.modern.main.u007"
                                : type == TargetType.DETACH
                                        ? (DetachedSwingWindowManager.isDetachedScreen(this)
                                                ? "gui.modern.main.u008" : "gui.modern.main.u009")
                                         : type == TargetType.CLOSE ? "gui.modern.main.u010"
                                                 : type == TargetType.GLOBAL_SETTINGS ? "gui.modern.main.u011" : "";
        ModernTooltipSupport.registerTooltipAnchor(this, bounds.x, bounds.y, bounds.width, bounds.height, tooltip);
    }

    private void drawNavigation(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        ModernMainLayout.Rect sidebar = layout.sidebar;
        boolean collapsed = layout.sidebarCollapsed;
        boolean stackedFooter = !collapsed && sidebar.width < 150;
        int footerHeight = collapsed ? 29 : stackedFooter ? 66 : 45;
        int toggleSize = 20;
        ModernMainLayout.Rect toggleBounds = new ModernMainLayout.Rect(
                collapsed ? sidebar.x + (sidebar.width - toggleSize) / 2 : sidebar.right() - toggleSize - 9,
                sidebar.y + 10, toggleSize, toggleSize);
        boolean toggleHovered = toggleBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(toggleBounds.x, toggleBounds.y, toggleBounds.width, toggleBounds.height, 4,
                toggleHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                toggleHovered ? 0xFF637783 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(toggleBounds.x + (collapsed ? 9 : 7), toggleBounds.y + 6,
                collapsed, toggleHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        hitTargets.add(new HitTarget(TargetType.NAVIGATION_TOGGLE, toggleBounds, ""));
         ModernTooltipSupport.registerTooltipAnchor(this, toggleBounds.x, toggleBounds.y, toggleBounds.width,
                 toggleBounds.height, collapsed ? tr("gui.modern.expand_navigation", "gui.modern.main.u012")
                         : tr("gui.modern.collapse_navigation", "gui.modern.main.u013"));

        int navTop;
        if (collapsed) {
            navTop = sidebar.y + 39;
        } else {
            ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.navigation", "gui.modern.main.u014"), sidebar.x + 16, sidebar.y + 15,
                    ModernUiRenderer.SUBTLE_TEXT, sidebar.width - 48);
            ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.feature_sections", "gui.modern.main.u015"), sidebar.x + 16, sidebar.y + 27,
                    ModernUiRenderer.MUTED_TEXT, sidebar.width - 48);
            navTop = sidebar.y + 43;
        }
        navigationClipBounds = new ModernMainLayout.Rect(sidebar.x + 6, navTop, Math.max(1, sidebar.width - 12),
                Math.max(1, sidebar.height - (navTop - sidebar.y) - footerHeight));
        List<NavigationRow> rows = buildNavigationRows();
        int rowHeight = collapsed ? 30 : 28;
        int rowGap = 4;
        int totalHeight = rows.size() * (rowHeight + rowGap);
        maxNavigationScrollOffset = Math.max(0, totalHeight - navigationClipBounds.height + rowGap);
        navigationScrollOffset = clamp(navigationScrollOffset, 0, maxNavigationScrollOffset);

        ModernUiRenderer.beginClip(navigationClipBounds);
        drawNavigationGroupFrames(rows, rowHeight, rowGap, collapsed);
        for (int i = 0; i < rows.size(); i++) {
            NavigationRow navigationRow = rows.get(i);
            GuiInventoryBase.CategoryTreeRow rowData = navigationRow.categoryRow;
            ModernMainLayout.Rect row = navigationRowBounds(i, rowHeight, rowGap, collapsed);
            if (!intersects(row, navigationClipBounds)) {
                continue;
            }
            boolean selected = navigationRow.otherFeatureGroup ? isOtherFeatureGroupSelected()
                    : isSelectedCategoryRow(rowData);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean pathDropHovered = rowData != null && isCurrentPathDropTarget(rowData.category, rowData.subCategory);
            int fill = pathDropHovered || selected ? ModernUiRenderer.SELECTED_SURFACE
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
            if (selected || hovered || pathDropHovered) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 5, fill);
            }
            if (pathDropHovered) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, 2, 1, ModernUiRenderer.ACCENT);
                ModernUiRenderer.drawRoundedRect(row.x, row.bottom() - 2, row.width, 2, 1, ModernUiRenderer.ACCENT);
            }
            if (selected) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y + 5, 3, row.height - 10, 2, ModernUiRenderer.ACCENT);
            }
            String label = getNavigationRowLabel(navigationRow);
            int itemCount = navigationRow.otherFeatureGroup ? getOtherFeatureItemCount()
                    : getCategoryItemCount(rowData.category, rowData.isSubCategory() ? rowData.subCategory : "");
            int dotColor = selected ? ModernUiRenderer.ACCENT
                    : getCategoryDotColor(navigationRow.otherFeatureGroup ? label : rowData.category);
            if (collapsed) {
                int badgeSize = Math.min(22, Math.max(18, row.height - 8));
                int badgeX = row.x + (row.width - badgeSize) / 2;
                int badgeY = row.y + (row.height - badgeSize) / 2;
                int badgeFill = selected ? ModernUiRenderer.SELECTED_SURFACE
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ICON_SURFACE;
                ModernUiRenderer.drawSubtlePanel(badgeX, badgeY, badgeSize, badgeSize, 5,
                        badgeFill, selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawIcon(getNavigationIcon(navigationRow), badgeX + 3, badgeY + 3,
                        badgeSize - 6, ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : dotColor, badgeFill));
                if (hovered) {
                    setHoveredTooltip(tr("gui.modern.main.fmt.nav_count", "%s\n%s 项", tr(label, label), String.valueOf(itemCount)), mouseX, mouseY);
                }
            } else {
                int textX = row.x + 10;
                ModernMainLayout.Rect rowHitBounds = intersection(row, navigationClipBounds);
                if (navigationRow.otherFeatureGroup) {
                    ModernMainLayout.Rect disclosureBounds = new ModernMainLayout.Rect(row.x + 7,
                            row.y + (row.height - 12) / 2, 12, 12);
                    ModernUiRenderer.drawChevron(disclosureBounds.x + 3, disclosureBounds.y + 2,
                            !otherFeatureGroupsExpanded,
                            ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill));
                    textX = row.x + 25;
                    hitTargets.add(new HitTarget(TargetType.OTHER_FEATURE_GROUP, rowHitBounds, ""));
                } else if (rowData.isCustomCategoryRoot()) {
                    ModernMainLayout.Rect disclosureBounds = new ModernMainLayout.Rect(row.x + 7,
                            row.y + (row.height - 12) / 2, 12, 12);
                    ModernUiRenderer.drawChevron(disclosureBounds.x + 3, disclosureBounds.y + 2,
                            MainUiLayoutManager.isCollapsed(rowData.category),
                            ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill));
                    textX = row.x + 25;
                    hitTargets.add(new HitTarget(TargetType.CATEGORY, rowHitBounds, rowData));
                    // Add the narrow expansion target after the row target so it wins hit testing.
                    hitTargets.add(new HitTarget(TargetType.CATEGORY_EXPAND, disclosureBounds, rowData.category));
                } else if (isOtherFeatureNavigationChild(rowData)) {
                    ModernUiRenderer.drawStatusDot(row.x + 15, row.y + (row.height - 7) / 2, dotColor);
                    textX = row.x + 28;
                    hitTargets.add(new HitTarget(TargetType.CATEGORY, rowHitBounds, rowData));
                } else if (rowData.isSubCategory()) {
                    ModernUiRenderer.drawStatusDot(row.x + 15, row.y + (row.height - 7) / 2, dotColor);
                    textX = row.x + 28;
                    hitTargets.add(new HitTarget(TargetType.CATEGORY, rowHitBounds, rowData));
                } else {
                    ModernUiRenderer.drawStatusDot(row.x + 10, row.y + (row.height - 7) / 2, dotColor);
                    textX = row.x + 23;
                    hitTargets.add(new HitTarget(TargetType.CATEGORY, rowHitBounds, rowData));
                }
                String countText = String.valueOf(itemCount);
                int countWidth = fontRenderer.getStringWidth(countText);
                int hudIconSpace = navigationRow.otherFeatureGroup ? 20 : 0;
                if (navigationRow.otherFeatureGroup) {
                    ModernMainLayout.Rect hudIcon = new ModernMainLayout.Rect(
                            row.right() - countWidth - 30, row.y + (row.height - 16) / 2, 16, 16);
                    ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.MOVE_PANEL, hudIcon.x + 2, hudIcon.y + 2,
                            12, ModernUiRenderer.readableText(ModernUiRenderer.TEXT, fill));
                    hitTargets.add(new HitTarget(TargetType.OTHER_FEATURE_HUD_POSITION,
                            intersection(hudIcon, navigationClipBounds), ""));
                    if (hudIcon.contains(mouseX, mouseY)) {
                        setHoveredTooltip(I18n.format("gui.other_features.hud.adjust"), mouseX, mouseY);
                    }
                }
                ModernUiRenderer.drawText(fontRenderer, label, textX, row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                        ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill),
                        Math.max(20, row.right() - textX - countWidth - 17 - hudIconSpace));
                ModernUiRenderer.drawText(fontRenderer, countText, row.right() - countWidth - 8,
                        row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                        ModernUiRenderer.readableText(ModernUiRenderer.MUTED_TEXT, fill), countWidth);
            }
            if (collapsed) {
                hitTargets.add(new HitTarget(navigationRow.otherFeatureGroup ? TargetType.OTHER_FEATURE_GROUP
                        : TargetType.CATEGORY, intersection(row, navigationClipBounds), rowData));
            }
            if (!navigationRow.otherFeatureGroup && rowData != null && rowData.isDroppableTarget()) {
                ModernMainLayout.Rect dropBounds = intersection(row, navigationClipBounds);
                dashboardPathDropTargets.add(new PathDropTarget(rowData.category, rowData.subCategory,
                        getNavigationRowLabel(navigationRow), dropBounds));
            }
        }
        ModernUiRenderer.endClip();

        if (maxNavigationScrollOffset > 0) {
            navigationScrollbar.drawAt(sidebar.right(), navigationClipBounds.y, navigationClipBounds.height,
                    navigationScrollOffset, maxNavigationScrollOffset, navigationClipBounds.height,
                    Math.max(navigationClipBounds.height, totalHeight), mouseX, mouseY,
                    value -> navigationScrollOffset = value);
            navigationScrollbarBounds = new ModernMainLayout.Rect(sidebar.right() - 14, navigationClipBounds.y, 14,
                    navigationClipBounds.height);
            navigationScrollbarThumbBounds = navigationScrollbarBounds;
        } else {
            navigationScrollbarBounds = null;
            navigationScrollbarThumbBounds = null;
        }

        int footerY = sidebar.bottom() - footerHeight;
        ModernUiRenderer.drawDivider(sidebar.x + 10, footerY, Math.max(1, sidebar.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        boolean running = GuiInventory.isLooping;
        if (collapsed) {
            autoFocusBounds = null;
            ModernUiRenderer.drawStatusDot(sidebar.x + (sidebar.width - 7) / 2, footerY + 12,
                    running ? ModernUiRenderer.SUCCESS : 0xFF536573);
        } else {
            int autoToggleWidth = 28;
            int autoToggleX = sidebar.right() - autoToggleWidth - 11;
            int autoRowY = footerY + (stackedFooter ? 21 : 0);
            autoFocusBounds = new ModernMainLayout.Rect(autoToggleX, autoRowY + 8, autoToggleWidth, 14);
            int autoLabelX = autoToggleX - 45;
            ModernMainLayout.Rect autoHitBounds = new ModernMainLayout.Rect(autoLabelX - 5, autoRowY + 6,
                    sidebar.right() - autoLabelX - 3, 19);
            boolean autoHovered = autoHitBounds.contains(mouseX, mouseY);
            boolean autoFocus = MainUiLayoutManager.isModernAutoFocus();
            ModernUiRenderer.drawStatusDot(sidebar.x + 16, footerY + 14, running ? ModernUiRenderer.SUCCESS : 0xFF536573);
            ModernUiRenderer.drawText(fontRenderer, running ? tr("gui.modern.path_running", "gui.modern.main.u016") : tr("gui.modern.ready", "gui.modern.main.u017"), sidebar.x + 29, footerY + 10,
                    ModernUiRenderer.SUBTLE_TEXT, stackedFooter ? sidebar.width - 40
                            : Math.max(18, autoLabelX - sidebar.x - 34));
            ModernUiRenderer.drawSubtlePanel(autoHitBounds.x, autoHitBounds.y, autoHitBounds.width,
                    autoHitBounds.height, 5, autoHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    autoFocus ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.auto_focus_short", "gui.modern.main.u018"), autoLabelX, autoRowY + 10,
                    ModernUiRenderer.readableText(autoFocus ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                            autoHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE), 42);
            ModernUiRenderer.drawToggle(autoFocusBounds.x, autoFocusBounds.y, autoFocusBounds.width,
                    autoFocusBounds.height, autoFocus, autoHovered);
            hitTargets.add(new HitTarget(TargetType.AUTO_FOCUS, autoHitBounds, ""));
            ModernTooltipSupport.registerTooltipAnchor(this, autoHitBounds.x, autoHitBounds.y, autoHitBounds.width,
                    autoHitBounds.height, "gui.modern.main.u019");
            ModernUiRenderer.drawText(fontRenderer, ProfileManager.getActiveProfileName(), sidebar.x + 16, autoRowY + 25,
                    ModernUiRenderer.MUTED_TEXT, sidebar.width - 32);
        }
    }

    private ModernMainLayout.Rect navigationRowBounds(int index, int rowHeight, int rowGap, boolean collapsed) {
        // Icon-only rows use symmetric insets instead of reserving a text scrollbar gutter.
        int rowWidth = collapsed ? Math.max(1, navigationClipBounds.width - 4)
                : ModernHoverScrollbar.contentWidth(navigationClipBounds.width - 2);
        return new ModernMainLayout.Rect(navigationClipBounds.x + 2,
                navigationClipBounds.y + index * (rowHeight + rowGap) - navigationScrollOffset,
                rowWidth, rowHeight);
    }

    private void drawNavigationGroupFrames(List<NavigationRow> rows, int rowHeight, int rowGap,
            boolean collapsed) {
        for (int start = 0; start < rows.size(); start++) {
            int end = findNavigationGroupEnd(rows, start);
            if (end <= start) {
                continue;
            }
            ModernMainLayout.Rect first = navigationRowBounds(start, rowHeight, rowGap, collapsed);
            ModernMainLayout.Rect last = navigationRowBounds(end, rowHeight, rowGap, collapsed);
            ModernMainLayout.Rect frame = new ModernMainLayout.Rect(navigationClipBounds.x + 1, first.y - 2,
                    first.width + 2, last.bottom() - first.y + 4);
            if (intersects(frame, navigationClipBounds)) {
                boolean selected = false;
                for (int index = start; index <= end; index++) {
                    NavigationRow row = rows.get(index);
                    if (row.otherFeatureGroup ? isOtherFeatureGroupSelected()
                            : isSelectedCategoryRow(row.categoryRow)) {
                        selected = true;
                        break;
                    }
                }
                ModernUiRenderer.drawSubtlePanel(frame.x, frame.y, frame.width, frame.height, 7,
                        ModernUiRenderer.SHELL_RAISED,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                if (!collapsed) {
                    int guideX = frame.x + 9;
                    int guideTop = first.bottom() - 2;
                    int guideBottom = last.y + last.height / 2;
                    ModernUiRenderer.drawVerticalDivider(guideX, guideTop,
                            Math.max(1, guideBottom - guideTop), ModernUiRenderer.BORDER);
                    for (int index = start + 1; index <= end; index++) {
                        ModernMainLayout.Rect child = navigationRowBounds(index, rowHeight, rowGap, collapsed);
                        ModernUiRenderer.drawRoundedRect(guideX, child.y + child.height / 2,
                                8, 1, 1, ModernUiRenderer.BORDER);
                    }
                }
            }
            start = end;
        }
    }

    private int findNavigationGroupEnd(List<NavigationRow> rows, int start) {
        NavigationRow parent = rows.get(start);
        if (parent.otherFeatureGroup) {
            int end = start;
            while (end + 1 < rows.size() && isOtherFeatureNavigationChild(rows.get(end + 1).categoryRow)) {
                end++;
            }
            return end;
        }
        GuiInventoryBase.CategoryTreeRow parentRow = parent.categoryRow;
        if (parentRow == null || !parentRow.isCustomCategoryRoot()) {
            return start;
        }
        int end = start;
        while (end + 1 < rows.size()) {
            GuiInventoryBase.CategoryTreeRow child = rows.get(end + 1).categoryRow;
            if (child == null || !child.isSubCategory() || !parentRow.category.equals(child.category)) {
                break;
            }
            end++;
        }
        return end;
    }

    private ModernUiRenderer.Icon getNavigationIcon(NavigationRow navigationRow) {
        if (navigationRow == null) {
            return ModernUiRenderer.Icon.HOME;
        }
        if (navigationRow.otherFeatureGroup) {
            return ModernUiRenderer.Icon.MISC;
        }
        GuiInventoryBase.CategoryTreeRow row = navigationRow.categoryRow;
        if (row == null) {
            return ModernUiRenderer.Icon.HOME;
        }
        String label = getNavigationRowLabel(navigationRow);
        String value = (row.category + " " + row.subCategory + " " + label).toLowerCase(Locale.ROOT);
        if (value.contains("gui.modern.main.u020") || value.contains("gui.modern.main.u021") || value.contains("path")) {
            return ModernUiRenderer.Icon.ROUTE;
        }
        if (value.contains("gui.modern.main.u022") || value.contains("gui.modern.main.u023") || value.contains("movement")) {
            return ModernUiRenderer.Icon.MOVEMENT;
        }
        if (value.contains("gui.modern.main.u024") || value.contains("block")) {
            return ModernUiRenderer.Icon.BLOCKS;
        }
        if (value.contains("gui.modern.main.u025") || value.contains("item")) {
            return ModernUiRenderer.Icon.INVENTORY;
        }
        if (value.contains("gui.modern.main.u026") || value.contains("gui.modern.main.u027") || value.contains("render")) {
            return ModernUiRenderer.Icon.RENDER;
        }
        if (value.contains("gui.modern.main.u028") || value.contains("world")) {
            return ModernUiRenderer.Icon.WORLD;
        }
        if (value.contains("gui.modern.main.u029") || value.contains("debug")) {
            return ModernUiRenderer.Icon.DEBUG;
        }
        return row.isSubCategory() ? ModernUiRenderer.Icon.GROUP : ModernUiRenderer.Icon.HOME;
    }

    private void drawSidebarDivider(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        if (layout == null || layout.sidebarCollapsed) {
            sidebarDividerBounds = null;
            return;
        }
        sidebarDividerBounds = ModernSplitPane.verticalDividerBounds(layout.sidebar.x, layout.sidebar.width, 0,
                layout.sidebar.y + 5, Math.max(1, layout.sidebar.height - 10));
        ModernSplitPane.drawVerticalDivider(sidebarDividerBounds, mouseX, mouseY, draggingSidebar);
        ModernTooltipSupport.registerTooltipAnchor(this, sidebarDividerBounds.x, sidebarDividerBounds.y,
                    sidebarDividerBounds.width, sidebarDividerBounds.height, tr("gui.modern.resize_navigation", "gui.modern.main.u030"));
    }

    private void drawContent(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        drawTabs(layout.content, mouseX, mouseY);
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null) {
            contentClipBounds = null;
            ModernMainLayout.Rect tabContent = new ModernMainLayout.Rect(layout.content.x,
                    layout.content.y + TAB_STRIP_HEIGHT, layout.content.width,
                    Math.max(1, layout.content.height - TAB_STRIP_HEIGHT));
            settingsTab.draw(fontRenderer, tabContent, mouseX, mouseY);
            String tooltip = settingsTab.getHoveredTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) {
                setHoveredTooltip(tooltip, mouseX, mouseY);
            }
        } else {
            drawDashboardContent(layout, mouseX, mouseY);
        }
        drawDashboardSettingsPopover(layout, mouseX, mouseY);
    }

    private void drawTabs(ModernMainLayout.Rect content, int mouseX, int mouseY) {
        ModernMainLayout.Rect strip = new ModernMainLayout.Rect(content.x + 10, content.y + 6,
                Math.max(1, content.width - 20), 24);
        ModernUiRenderer.drawDivider(content.x + 12, content.y + TAB_STRIP_HEIGHT - 1, Math.max(1, content.width - 24),
                ModernUiRenderer.BORDER_SUBTLE);
        dashboardSettingsGearBounds = new ModernMainLayout.Rect(strip.x, strip.y, 24, 24);
        boolean gearHovered = dashboardSettingsGearBounds.contains(mouseX, mouseY);
        int gearFill = dashboardSettingsOpen ? ModernUiRenderer.SELECTED_SURFACE
                : gearHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        ModernUiRenderer.drawSubtlePanel(dashboardSettingsGearBounds.x, dashboardSettingsGearBounds.y,
                dashboardSettingsGearBounds.width, dashboardSettingsGearBounds.height, 5, gearFill,
                dashboardSettingsOpen ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSettingsIconRotated(dashboardSettingsGearBounds.x + 6,
                dashboardSettingsGearBounds.y + 6, ModernUiRenderer.readableText(gearHovered || dashboardSettingsOpen
                        ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, gearFill),
                updateDashboardSettingsGearRotation());
        hitTargets.add(new HitTarget(TargetType.DASHBOARD_SETTINGS, dashboardSettingsGearBounds, ""));
        ModernTooltipSupport.registerTooltipAnchor(this, dashboardSettingsGearBounds.x, dashboardSettingsGearBounds.y,
                dashboardSettingsGearBounds.width, dashboardSettingsGearBounds.height, "gui.modern.main.u031");

        ModernMainLayout.Rect tabViewport = new ModernMainLayout.Rect(strip.x + 30, strip.y,
                Math.max(1, strip.width - 30), strip.height);
        tabStripBounds = new ModernMainLayout.Rect(tabViewport.x, tabViewport.y, tabViewport.width,
                tabViewport.height + ModernHoverScrollbar.GUTTER);
        updateTabWidths();
        int totalTabWidth = getTotalTabWidth();
        maxTabScrollOffset = Math.max(0, totalTabWidth - tabViewport.width);
        tabScrollOffset = clamp(tabScrollOffset, 0, maxTabScrollOffset);
        int tabX = tabViewport.x - tabScrollOffset;
        Map<TabKey, ModernMainLayout.Rect> renderedTabBounds = new HashMap<>();
        ModernUiRenderer.beginClip(tabViewport);
        for (TabKey tab : openTabs) {
            String title = getTabTitle(tab);
            int tabWidth = getTabWidth(tab);
            ModernMainLayout.Rect tabBounds = new ModernMainLayout.Rect(tabX, strip.y, tabWidth, strip.height);
            renderedTabBounds.put(tab, tabBounds);
            boolean active = tab.equals(activeTab);
            boolean hovered = tabBounds.contains(mouseX, mouseY);
            int tabFill = active ? ModernUiRenderer.SELECTED_SURFACE
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
            ModernUiRenderer.drawSubtlePanel(tabBounds.x, tabBounds.y, tabBounds.width, tabBounds.height, 5,
                    tabFill,
                    active ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            if (active) {
                ModernUiRenderer.drawRoundedRect(tabBounds.x + 6, tabBounds.bottom() - 3, tabBounds.width - 12, 2, 1,
                        ModernUiRenderer.ACCENT);
            }
            int titleWidth = tabWidth - (tab.isDashboard() ? 14 : 28);
            ModernUiRenderer.drawText(fontRenderer, title, tabBounds.x + 8,
                    tabBounds.y + (tabBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                    ModernUiRenderer.readableText(active ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, tabFill),
                    Math.max(20, titleWidth));
            hitTargets.add(new HitTarget(TargetType.TAB_SELECT, intersection(tabBounds, tabViewport), tab.encode()));
            if (!tab.isDashboard()) {
                ModernMainLayout.Rect closeBounds = new ModernMainLayout.Rect(tabBounds.right() - 17,
                        tabBounds.y + (tabBounds.height - 11) / 2, 11, 11);
                ModernUiRenderer.drawCloseIcon(closeBounds.x + 1, closeBounds.y + 1,
                        ModernUiRenderer.readableText(closeBounds.contains(mouseX, mouseY)
                                ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, tabFill));
                hitTargets.add(new HitTarget(TargetType.TAB_CLOSE, intersection(closeBounds, tabViewport), tab.encode()));
            }
            tabX += tabWidth + 5;
        }
        ModernUiRenderer.endClip();
        drawDependencyFrames(tabViewport, renderedTabBounds, mouseX, mouseY);
        if (maxTabScrollOffset > 0) {
            ModernMainLayout.Rect scrollViewport = new ModernMainLayout.Rect(tabViewport.x, tabViewport.y,
                    tabViewport.width, tabViewport.height + ModernHoverScrollbar.GUTTER);
            tabScrollbar.draw(scrollViewport, tabScrollOffset, maxTabScrollOffset, tabViewport.width,
                    Math.max(tabViewport.width, totalTabWidth), mouseX, mouseY, value -> tabScrollOffset = value);
            tabScrollbarBounds = new ModernMainLayout.Rect(tabViewport.x,
                    tabViewport.bottom() + ModernHoverScrollbar.CONTENT_GAP, tabViewport.width,
                    ModernHoverScrollbar.TRACK_WIDTH);
            tabScrollbarThumbBounds = tabScrollbarBounds;
        } else {
            tabScrollbarBounds = null;
            tabScrollbarThumbBounds = null;
        }
    }

    private void drawDependencyFrames(ModernMainLayout.Rect viewport,
            Map<TabKey, ModernMainLayout.Rect> renderedTabBounds, int mouseX, int mouseY) {
        Set<TabKey> drawnRoots = new HashSet<>();
        for (TabKey tab : openTabs) {
            TabKey root = dependencyRoot(tab);
            if (root == null || drawnRoots.contains(root)) {
                continue;
            }
            List<ModernMainLayout.Rect> members = new ArrayList<>();
            for (TabKey candidate : openTabs) {
                if (root.equals(dependencyRoot(candidate)) && renderedTabBounds.containsKey(candidate)) {
                    members.add(renderedTabBounds.get(candidate));
                }
            }
            if (members.size() < 2) {
                continue;
            }
            drawnRoots.add(root);
            int left = members.get(0).x;
            int right = members.get(0).right();
            for (ModernMainLayout.Rect member : members) {
                left = Math.min(left, member.x);
                right = Math.max(right, member.right());
            }
            int x = Math.max(viewport.x, left - 2);
            int end = Math.min(viewport.right(), right + 2);
            int top = Math.max(viewport.y, members.get(0).y - 2);
            int bottom = Math.min(viewport.bottom(), members.get(0).bottom() + 1);
            int color = 0xFF6D9FB0;
            Gui.drawRect(x, top, end, top + 1, color);
            Gui.drawRect(x, bottom - 1, end, bottom, color);
            Gui.drawRect(x, top, x + 1, bottom, color);
            Gui.drawRect(end - 1, top, end, bottom, color);
            for (int i = 0; i + 1 < openTabs.size(); i++) {
                TabKey current = openTabs.get(i);
                TabKey next = openTabs.get(i + 1);
                if (root.equals(dependencyRoot(current)) && root.equals(dependencyRoot(next))) {
                    ModernMainLayout.Rect currentBounds = renderedTabBounds.get(current);
                    ModernMainLayout.Rect nextBounds = renderedTabBounds.get(next);
                    if (currentBounds != null && nextBounds != null) {
                        Gui.drawRect(currentBounds.right(), top + 3, nextBounds.x, bottom - 3, color);
                    }
                }
            }
            if (mouseX >= x && mouseX <= end && mouseY >= top && mouseY <= bottom) {
                hoveredTooltip = "gui.modern.main.u032";
                hoveredTooltipX = mouseX;
                hoveredTooltipY = mouseY;
            }
        }
    }

    private void drawDashboardContent(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        ModernMainLayout.Rect content = layout.content;
        List<MenuCard> cards = getFilteredCards();
        MainUiLayoutManager.DashboardViewMeta view = getDashboardView();
        boolean paged = MainUiLayoutManager.FLOW_PAGED.equals(view.flowMode);
        boolean showAutoPause = shouldShowDashboardAutoPause();
        int cardHeight = getDashboardCardHeight(view);
        int requestedColumns = view.columns;
        ModernMainLayout.Grid grid = layout.createDashboardGrid(TAB_STRIP_HEIGHT + 4,
                paged || showAutoPause ? 27 : 8, 7, requestedColumns, cardHeight);
        contentClipBounds = grid.bounds;
        dashboardPreviousPageBounds = null;
        dashboardNextPageBounds = null;
        autoPauseBounds = null;

        if (cards.isEmpty() && !isCustomRootCategoryGrouping()) {
            maxCardScrollOffset = 0;
            dashboardPage = 0;
            dashboardPageCount = 1;
            drawEmptyState(grid.bounds);
            drawDashboardAutoPauseToggle(content, grid, mouseX, mouseY);
            return;
        }

        boolean grouping = isDashboardGroupingActive(view);
        List<DashboardGroup> groups = buildDashboardGroups(cards, grouping);
        List<DashboardGroup> visibleGroups = groups;
        if (paged) {
            List<DashboardPage> pages = buildDashboardPages(groups, grid, grouping);
            dashboardPageCount = Math.max(1, pages.size());
            dashboardPage = clamp(dashboardPage, 0, dashboardPageCount - 1);
            visibleGroups = pages.isEmpty() ? Collections.<DashboardGroup>emptyList()
                    : pages.get(dashboardPage).groups;
            cardScrollOffset = 0;
            maxCardScrollOffset = 0;
        } else {
            dashboardPage = 0;
            dashboardPageCount = 1;
            maxCardScrollOffset = Math.max(0,
                    getDashboardContentHeight(groups, grid, grouping) - grid.bounds.height);
            cardScrollOffset = clamp(cardScrollOffset, 0, maxCardScrollOffset);
        }

        ModernUiRenderer.beginClip(grid.bounds);
        drawDashboardGroups(visibleGroups, grid, grouping, paged ? 0 : cardScrollOffset, mouseX, mouseY);
        ModernUiRenderer.endClip();

        if (paged) {
            drawDashboardPager(content, grid, mouseX, mouseY);
        } else {
            drawDashboardScrollbar(grid, mouseX, mouseY);
        }
        drawDashboardAutoPauseToggle(content, grid, mouseX, mouseY);
    }

    private void drawDashboardSettingsPopover(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        if (!dashboardSettingsOpen || dashboardSettingsGearBounds == null) {
            dashboardSettingsPanelBounds = null;
            dashboardSettingsSubmenuBounds = null;
            dashboardSettingsHoverTool = null;
            return;
        }
        MainUiLayoutManager.DashboardViewMeta view = getDashboardView();
        boolean global = MainUiLayoutManager.isModernDashboardUsingGlobal(getDashboardViewKey());
        int panelWidth = Math.min(236, Math.max(176, layout.content.width - 16));
        int panelHeight = 190;
        int panelX = clamp(dashboardSettingsGearBounds.x, layout.content.x + 6,
                Math.max(layout.content.x + 6, layout.content.right() - panelWidth - 6));
        int panelY = layout.content.y + TAB_STRIP_HEIGHT + 3;
        dashboardSettingsPanelBounds = new ModernMainLayout.Rect(panelX, panelY, panelWidth, panelHeight);
        ModernUiRenderer.drawPanel(panelX, panelY, panelWidth, panelHeight, 7, ModernUiRenderer.TOOLTIP_SURFACE,
                ModernUiRenderer.ACCENT_DIM);

        int innerX = panelX + 9;
        int innerWidth = panelWidth - 18;
        int segmentGap = 4;
        int segmentWidth = Math.max(1, (innerWidth - segmentGap) / 2);
        ModernMainLayout.Rect globalBounds = new ModernMainLayout.Rect(innerX, panelY + 9, segmentWidth, 22);
        ModernMainLayout.Rect independentBounds = new ModernMainLayout.Rect(innerX + segmentWidth + segmentGap,
                panelY + 9, Math.max(1, innerWidth - segmentWidth - segmentGap), 22);
        drawScopeButton(globalBounds, tr("gui.modern.global_settings", "gui.modern.main.u011"), global, mouseX, mouseY);
        drawScopeButton(independentBounds, tr("gui.modern.independent_settings", "gui.modern.main.u033"), !global, mouseX, mouseY);
        hitTargets.add(new HitTarget(TargetType.DASHBOARD_TOOL, globalBounds, "scope_global"));
        hitTargets.add(new HitTarget(TargetType.DASHBOARD_TOOL, independentBounds, "scope_independent"));
        ModernUiRenderer.drawDivider(innerX, panelY + 38, innerWidth, ModernUiRenderer.BORDER_SUBTLE);

        String[] keys = { "sort", "grouping", "size", "columns", "flow" };
        String[] titles = { tr("gui.modern.sort", "gui.modern.main.u034"), tr("gui.modern.grouping", "gui.modern.main.u035"),
                tr("gui.modern.icon_size", "gui.modern.main.u036"), tr("gui.modern.columns", "gui.modern.main.u037"),
                tr("gui.modern.flow", "gui.modern.main.u038") };
        String[] values = {
                getDashboardSortLabel(view),
                view.grouping ? tr("gui.modern.on", "gui.modern.main.u039") : tr("gui.modern.off", "gui.modern.main.u040"),
                getDashboardIconSizeLabel(view),
                getDashboardColumnsLabel(view),
                MainUiLayoutManager.FLOW_PAGED.equals(view.flowMode) ? tr("gui.modern.paged", "gui.modern.main.u041") : tr("gui.modern.waterfall", "gui.modern.main.u042")
        };
        String[] tooltips = {
                "gui.modern.main.u043",
                "gui.modern.main.u044",
                "gui.modern.main.u045",
                "gui.modern.main.u046",
                "gui.modern.main.u047"
        };
        int rowY = panelY + 44;
        ModernMainLayout.Rect[] rows = new ModernMainLayout.Rect[keys.length];
        for (int i = 0; i < keys.length; i++) {
            rows[i] = new ModernMainLayout.Rect(innerX, rowY + i * 27, innerWidth, 23);
        }
        String hoveredTool = resolveDashboardSettingsHoverTool(keys, rows, mouseX, mouseY);
        dashboardSettingsHoverTool = hoveredTool;
        ModernMainLayout.Rect hoveredRow = null;
        for (int i = 0; i < keys.length; i++) {
            ModernMainLayout.Rect row = rows[i];
            boolean expanded = keys[i].equals(hoveredTool);
            boolean hovered = expanded || row.contains(mouseX, mouseY);
            if (expanded) {
                hoveredRow = row;
            }
            int fill = expanded ? ModernUiRenderer.SELECTED_SURFACE
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4, fill,
                    expanded ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
            drawDashboardToolIcon(keys[i], row.x + 8, row.y + 7,
                    ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT, fill));
            ModernUiRenderer.drawText(fontRenderer, titles[i], row.x + 25,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                    ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill), row.width - 95);
            int valueWidth = fontRenderer.getStringWidth(values[i]);
            ModernUiRenderer.drawText(fontRenderer, values[i], row.right() - valueWidth - 20,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                    ModernUiRenderer.readableText(ModernUiRenderer.TEXT, fill), valueWidth);
            ModernUiRenderer.drawChevron(row.right() - 11, row.y + 8, true,
                    ModernUiRenderer.readableText(expanded ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, fill));
            hitTargets.add(new HitTarget(TargetType.DASHBOARD_TOOL, row, keys[i]));
            if (!expanded) {
                ModernTooltipSupport.registerTooltipAnchor(this, row.x, row.y, row.width, row.height, tooltips[i]);
            }
        }
        if (hoveredTool != null && hoveredRow != null) {
            drawDashboardSettingsSubmenu(hoveredTool, hoveredRow, view, mouseX, mouseY);
        } else {
            dashboardSettingsSubmenuBounds = null;
        }
    }

    private String resolveDashboardSettingsHoverTool(String[] keys, ModernMainLayout.Rect[] rows, int mouseX,
            int mouseY) {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i].contains(mouseX, mouseY)) {
                return keys[i];
            }
        }
        if (dashboardSettingsHoverTool != null && dashboardSettingsSubmenuBounds != null
                && dashboardSettingsSubmenuBounds.contains(mouseX, mouseY)) {
            return dashboardSettingsHoverTool;
        }
        if (dashboardSettingsHoverTool != null && isOverDashboardSettingsSubmenuBridge(mouseX, mouseY)) {
            return dashboardSettingsHoverTool;
        }
        if (dashboardSettingsHoverTool != null && dashboardSettingsSubmenuBounds != null) {
            for (int i = 0; i < rows.length; i++) {
                if (!keys[i].equals(dashboardSettingsHoverTool)) {
                    continue;
                }
                boolean submenuOnRight = dashboardSettingsSubmenuBounds.x >= rows[i].x;
                int left = submenuOnRight
                        ? Math.min(rows[i].right(), dashboardSettingsSubmenuBounds.x)
                        : Math.min(rows[i].x, dashboardSettingsSubmenuBounds.right());
                int right = submenuOnRight
                        ? Math.max(rows[i].right(), dashboardSettingsSubmenuBounds.x)
                        : Math.max(rows[i].x, dashboardSettingsSubmenuBounds.right());
                int top = Math.min(rows[i].y, dashboardSettingsSubmenuBounds.y);
                int bottom = Math.max(rows[i].bottom(), dashboardSettingsSubmenuBounds.bottom());
                if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
                    return dashboardSettingsHoverTool;
                }
            }
        }
        return null;
    }

    private void drawDashboardSettingsSubmenu(String tool, ModernMainLayout.Rect row,
            MainUiLayoutManager.DashboardViewMeta view, int mouseX, int mouseY) {
        List<DashboardToolOption> options = getDashboardToolOptions(tool);
        if (options.isEmpty() || row == null || dashboardSettingsPanelBounds == null) {
            dashboardSettingsSubmenuBounds = null;
            return;
        }
        int itemHeight = 22;
        int padding = 6;
        int maxLabelWidth = 0;
        for (int i = 0; i < options.size(); i++) {
            maxLabelWidth = Math.max(maxLabelWidth, fontRenderer.getStringWidth(options.get(i).label));
        }
        int menuWidth = Math.max(96, Math.min(168, maxLabelWidth + 38));
        int menuHeight = options.size() * itemHeight + padding * 2;
        int submenuX = row.right() - 2;
        if (submenuX + menuWidth > this.width - 6) {
            submenuX = Math.max(6, row.x - menuWidth + 2);
        }
        int submenuY = clamp(row.y - padding, 6, Math.max(6, this.height - menuHeight - 6));
        dashboardSettingsSubmenuBounds = new ModernMainLayout.Rect(submenuX, submenuY, menuWidth, menuHeight);
        ModernUiRenderer.drawPanel(submenuX, submenuY, menuWidth, menuHeight, 6, ModernUiRenderer.TOOLTIP_SURFACE,
                ModernUiRenderer.ACCENT_DIM);

        String selectedId = getDashboardToolSelectedId(tool, view);
        for (int i = 0; i < options.size(); i++) {
            DashboardToolOption option = options.get(i);
            ModernMainLayout.Rect item = new ModernMainLayout.Rect(submenuX + 5,
                    submenuY + padding + i * itemHeight, menuWidth - 10, itemHeight);
            boolean selected = option.id.equals(selectedId);
            boolean hovered = item.contains(mouseX, mouseY);
            int fill = hovered ? ModernUiRenderer.SURFACE_HOVER
                    : selected ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.TOOLTIP_SURFACE;
            if (hovered || selected) {
                ModernUiRenderer.drawRoundedRect(item.x, item.y, item.width, item.height, 3,
                        fill);
            }
            int textLeft = item.x + 8;
            if (selected) {
                ModernUiRenderer.drawStatusDot(textLeft, item.y + (item.height - 7) / 2, ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, fill));
                textLeft += 12;
            }
            ModernUiRenderer.drawText(fontRenderer, option.label, textLeft,
                    item.y + (item.height - fontRenderer.FONT_HEIGHT) / 2,
                    ModernUiRenderer.readableText(selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill),
                    item.right() - textLeft - 6);
            hitTargets.add(new HitTarget(TargetType.DASHBOARD_TOOL_OPTION, item, tool + ":" + option.id));
        }
    }

    private List<DashboardToolOption> getDashboardToolOptions(String tool) {
        List<DashboardToolOption> options = new ArrayList<DashboardToolOption>();
        if ("sort".equals(tool)) {
            options.add(new DashboardToolOption(MainUiLayoutManager.SORT_DEFAULT, "gui.modern.main.u048"));
            options.add(new DashboardToolOption(MainUiLayoutManager.SORT_ACTIVE_FIRST, "gui.modern.main.u049"));
            options.add(new DashboardToolOption(MainUiLayoutManager.SORT_ALPHABETICAL, "gui.modern.main.u050"));
        } else if ("grouping".equals(tool)) {
            options.add(new DashboardToolOption("on", "gui.modern.main.u039"));
            options.add(new DashboardToolOption("off", "gui.modern.main.u040"));
        } else if ("size".equals(tool)) {
            options.add(new DashboardToolOption(MainUiLayoutManager.ICON_SMALL, "gui.modern.main.u051"));
            options.add(new DashboardToolOption(MainUiLayoutManager.ICON_MEDIUM, "gui.modern.main.u052"));
            options.add(new DashboardToolOption(MainUiLayoutManager.ICON_LARGE, "gui.modern.main.u053"));
        } else if ("columns".equals(tool)) {
            options.add(new DashboardToolOption("0", "gui.modern.main.u054"));
            for (int columns = 2; columns <= 8; columns++) {
                options.add(new DashboardToolOption(String.valueOf(columns), String.valueOf(columns)));
            }
        } else if ("flow".equals(tool)) {
            options.add(new DashboardToolOption(MainUiLayoutManager.FLOW_WATERFALL, "gui.modern.main.u042"));
            options.add(new DashboardToolOption(MainUiLayoutManager.FLOW_PAGED, "gui.modern.main.u041"));
        }
        return options;
    }

    private String getDashboardToolSelectedId(String tool, MainUiLayoutManager.DashboardViewMeta view) {
        if (view == null) {
            return "";
        }
        if ("sort".equals(tool)) {
            return view.sortMode;
        }
        if ("grouping".equals(tool)) {
            return view.grouping ? "on" : "off";
        }
        if ("size".equals(tool)) {
            return MainUiLayoutManager.ICON_XL.equals(view.iconSize)
                    ? MainUiLayoutManager.ICON_LARGE : view.iconSize;
        }
        if ("columns".equals(tool)) {
            return String.valueOf(view.columns);
        }
        if ("flow".equals(tool)) {
            return view.flowMode;
        }
        return "";
    }

    private void drawScopeButton(ModernMainLayout.Rect bounds, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int width = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(5, (bounds.width - width) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill),
                Math.max(1, bounds.width - 10));
    }

    private void drawDashboardToolIcon(String key, int x, int y, int color) {
        ModernUiRenderer.Icon icon = "sort".equals(key) ? ModernUiRenderer.Icon.SORT
                : "grouping".equals(key) ? ModernUiRenderer.Icon.GROUP
                        : "size".equals(key) ? ModernUiRenderer.Icon.SIZE
                                : "columns".equals(key) ? ModernUiRenderer.Icon.COLUMNS
                                        : ModernUiRenderer.Icon.FLOW;
        ModernUiRenderer.drawIcon(icon, x - 1, y - 2, 14, color);
    }

    private List<DashboardGroup> buildDashboardGroups(List<MenuCard> cards, boolean grouping) {
        if (!grouping) {
            DashboardGroup group = new DashboardGroup("", "");
            group.cards.addAll(cards);
            return Collections.singletonList(group);
        }

        Map<String, DashboardGroup> grouped = new LinkedHashMap<>();
        if (isCustomRootCategoryGrouping()) {
            String category = GuiInventory.currentCategory == null ? "" : GuiInventory.currentCategory.trim();
            for (String subCategory : MainUiLayoutManager.getSubCategories(category)) {
                String normalizedSubCategory = subCategory == null ? "" : subCategory.trim();
                String key = getPathDashboardGroupKey(category, normalizedSubCategory);
                grouped.put(key, new DashboardGroup(key, normalizedSubCategory, category, normalizedSubCategory));
            }
            String uncategorizedKey = getPathDashboardGroupKey(category, "");
            grouped.put(uncategorizedKey, new DashboardGroup(uncategorizedKey, "gui.modern.main.u055", category, ""));
            for (MenuCard card : cards) {
                PathSequence sequence = getCustomPathSequence(card.command);
                String subCategory = normalizeSequenceSubCategory(sequence);
                DashboardGroup group = grouped.get(getPathDashboardGroupKey(category, subCategory));
                if (group == null) {
                    group = grouped.get(uncategorizedKey);
                }
                group.cards.add(card);
            }
            return new ArrayList<>(grouped.values());
        }

        for (GuiInventoryBase.GroupedItemSection section : GuiInventory.commonItemSections) {
            grouped.put(section.key, new DashboardGroup(section.key, section.title));
        }
        DashboardGroup other = new DashboardGroup("other", "gui.modern.main.u056");
        for (MenuCard card : cards) {
            DashboardGroup group = grouped.get(card.groupKey);
            if (group == null) {
                group = other;
            }
            group.cards.add(card);
        }
        List<DashboardGroup> result = new ArrayList<>();
        for (DashboardGroup group : grouped.values()) {
            if (!group.cards.isEmpty()) {
                result.add(group);
            }
        }
        if (!other.cards.isEmpty()) {
            result.add(other);
        }
        return result;
    }

    private int getDashboardContentHeight(List<DashboardGroup> groups, ModernMainLayout.Grid grid, boolean grouping) {
        int height = 0;
        for (DashboardGroup group : groups) {
            if (grouping) {
                height += 20;
            }
            boolean collapsed = grouping && MainUiLayoutManager.isModernDashboardGroupCollapsed(
                    getDashboardViewKey(), group.key);
            if (!collapsed && !group.cards.isEmpty()) {
                if (grouping) {
                    height += 5;
                }
                int rows = (group.cards.size() + grid.columns - 1) / grid.columns;
                height += rows * grid.cardHeight + Math.max(0, rows - 1) * grid.gap;
            }
            height += 8;
        }
        return Math.max(1, height - (groups.isEmpty() ? 0 : 8));
    }

    private void drawDashboardGroups(List<DashboardGroup> groups, ModernMainLayout.Grid grid, boolean grouping,
            int scrollOffset, int mouseX, int mouseY) {
        int y = grid.bounds.y - Math.max(0, scrollOffset);
        for (DashboardGroup group : groups) {
            boolean collapsed = grouping && MainUiLayoutManager.isModernDashboardGroupCollapsed(
                    getDashboardViewKey(), group.key);
            if (grouping) {
                ModernMainLayout.Rect header = new ModernMainLayout.Rect(grid.bounds.x, y, grid.bounds.width, 20);
                if (intersects(header, grid.bounds)) {
                    boolean hovered = header.contains(mouseX, mouseY) && grid.bounds.contains(mouseX, mouseY);
                    boolean pathDropHovered = group.acceptsPathDrops()
                            && isCurrentPathDropTarget(group.pathCategory, group.pathSubCategory);
                    int headerFill = pathDropHovered ? ModernUiRenderer.SELECTED_SURFACE
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
                    ModernUiRenderer.drawRoundedRect(header.x, header.y, header.width, header.height, 4, headerFill);
                    if (pathDropHovered) {
                        ModernUiRenderer.drawRoundedRect(header.x, header.y, header.width, 2, 1,
                                ModernUiRenderer.ACCENT);
                        ModernUiRenderer.drawRoundedRect(header.x, header.bottom() - 2, header.width, 2, 1,
                                ModernUiRenderer.ACCENT);
                    }
                    ModernUiRenderer.drawChevron(header.x + 8, header.y + 7, collapsed,
                            ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, headerFill));
                    String title = tr(group.title, group.title);
                    if (group.continuation) {
                        title = title + tr("gui.modern.dashboard.continued_suffix", " · 续");
                    }
                    ModernUiRenderer.drawText(fontRenderer, title, header.x + 23,
                            header.y + (header.height - fontRenderer.FONT_HEIGHT) / 2,
                            ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, headerFill), header.width - 32);
                    ModernMainLayout.Rect hit = intersection(header, grid.bounds);
                    hitTargets.add(new HitTarget(TargetType.DASHBOARD_GROUP, hit, group.key));
                    if (group.acceptsPathDrops()) {
                        dashboardPathDropTargets.add(new PathDropTarget(group.pathCategory, group.pathSubCategory,
                                title, hit));
                    }
                    ModernTooltipSupport.registerTooltipAnchor(this, hit.x, hit.y, hit.width, hit.height,
                            tr(collapsed ? "gui.modern.dashboard.expand_group" : "gui.modern.dashboard.collapse_group",
                                    collapsed ? "gui.modern.main.u057" : "gui.modern.main.u058", tr(group.title, group.title)));
                }
                y += 20;
            }
            if (!collapsed && !group.cards.isEmpty()) {
                if (grouping) {
                    y += 5;
                }
                for (int i = 0; i < group.cards.size(); i++) {
                    int column = i % grid.columns;
                    int row = i / grid.columns;
                    ModernMainLayout.Rect cardBounds = new ModernMainLayout.Rect(
                            grid.bounds.x + column * (grid.cardWidth + grid.gap),
                            y + row * (grid.cardHeight + grid.gap), grid.cardWidth, grid.cardHeight);
                    if (intersects(cardBounds, grid.bounds)) {
                        drawFeatureCard(group.cards.get(i), cardBounds, grid.bounds, mouseX, mouseY);
                        if (group.acceptsPathDrops() && getCustomPathSequence(group.cards.get(i).command) == null) {
                            dashboardPathDropTargets.add(new PathDropTarget(group.pathCategory, group.pathSubCategory,
                                    group.title, intersection(cardBounds, grid.bounds)));
                        }
                    }
                }
                int rows = (group.cards.size() + grid.columns - 1) / grid.columns;
                y += rows * grid.cardHeight + Math.max(0, rows - 1) * grid.gap;
            }
            y += 8;
        }
    }

    private List<DashboardPage> buildDashboardPages(List<DashboardGroup> groups, ModernMainLayout.Grid grid,
            boolean grouping) {
        List<DashboardPage> pages = new ArrayList<>();
        DashboardPage current = new DashboardPage();
        for (DashboardGroup source : groups) {
            boolean collapsed = grouping && MainUiLayoutManager.isModernDashboardGroupCollapsed(
                    getDashboardViewKey(), source.key);
            if (collapsed) {
                DashboardGroup header = new DashboardGroup(source, false);
                boolean hadContent = !current.groups.isEmpty();
                current.groups.add(header);
                if (hadContent && getDashboardContentHeight(current.groups, grid, grouping) > grid.bounds.height) {
                    current.groups.remove(current.groups.size() - 1);
                    pages.add(current);
                    current = new DashboardPage();
                    current.groups.add(header);
                }
                continue;
            }
            if (source.cards.isEmpty()) {
                DashboardGroup header = new DashboardGroup(source, false);
                boolean hadContent = !current.groups.isEmpty();
                current.groups.add(header);
                if (hadContent && getDashboardContentHeight(current.groups, grid, grouping) > grid.bounds.height) {
                    current.groups.remove(current.groups.size() - 1);
                    pages.add(current);
                    current = new DashboardPage();
                    current.groups.add(header);
                }
                continue;
            }
            for (int i = 0; i < source.cards.size(); i++) {
                DashboardGroup segment = current.groups.isEmpty() ? null
                        : current.groups.get(current.groups.size() - 1);
                boolean sameSegment = segment != null && segment.key.equals(source.key);
                boolean hadContent = !current.groups.isEmpty() && (!sameSegment || !segment.cards.isEmpty());
                if (!sameSegment) {
                    segment = new DashboardGroup(source, i > 0);
                    current.groups.add(segment);
                }
                segment.cards.add(source.cards.get(i));
                if (hadContent && getDashboardContentHeight(current.groups, grid, grouping) > grid.bounds.height) {
                    segment.cards.remove(segment.cards.size() - 1);
                    if (segment.cards.isEmpty()) {
                        current.groups.remove(current.groups.size() - 1);
                    }
                    pages.add(current);
                    current = new DashboardPage();
                    DashboardGroup next = new DashboardGroup(source, i > 0);
                    next.cards.add(source.cards.get(i));
                    current.groups.add(next);
                }
            }
        }
        if (!current.groups.isEmpty() || pages.isEmpty()) {
            pages.add(current);
        }
        return pages;
    }

    private boolean shouldShowDashboardAutoPause() {
        return GuiInventory.isCustomOverlayCategory(GuiInventory.currentCategory);
    }

    private void drawDashboardAutoPauseToggle(ModernMainLayout.Rect content, ModernMainLayout.Grid grid,
            int mouseX, int mouseY) {
        if (!shouldShowDashboardAutoPause() || content == null) {
            autoPauseBounds = null;
            return;
        }
        int size = 22;
        int y = grid == null ? content.bottom() - 23
                : Math.min(content.bottom() - 23, grid.bounds.bottom() + 5);
        int x = content.right() - size - 10;
        autoPauseBounds = new ModernMainLayout.Rect(x, y, size, size);
        boolean enabled = MainUiLayoutManager.isModernAutoPauseOnMenuOpen();
        boolean hovered = autoPauseBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(x, y, size, size, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                enabled ? ModernUiRenderer.ACCENT_DIM : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawPauseIcon(x + 6, y + 6,
                ModernUiRenderer.readableText(enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE));
        hitTargets.add(new HitTarget(TargetType.AUTO_PAUSE, autoPauseBounds, ""));
        ModernTooltipSupport.registerTooltipAnchor(this, x, y, size, size,
                enabled ? "gui.modern.main.u059"
                        : "gui.modern.main.u060");

        int statusX = x - size - 5;
        ModernMainLayout.Rect statusBounds = new ModernMainLayout.Rect(statusX, y, size, size);
        boolean statusVisible = MainUiLayoutManager.isModernRunningStatusVisible();
        boolean statusHovered = statusBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(statusX, y, size, size, 5,
                statusHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                statusVisible ? ModernUiRenderer.ACCENT_DIM
                        : statusHovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.RUNNING_STATUS, statusX + 5, y + 5, 12,
                ModernUiRenderer.readableText(statusVisible ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT,
                        statusHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE));
        hitTargets.add(new HitTarget(TargetType.RUNNING_STATUS_TOGGLE, statusBounds, ""));
        ModernTooltipSupport.registerTooltipAnchor(this, statusX, y, size, size,
                statusVisible ? "gui.modern.main.u061"
                        : "gui.modern.main.u062");
    }

    private void drawDashboardPager(ModernMainLayout.Rect content, ModernMainLayout.Grid grid, int mouseX, int mouseY) {
        int y = Math.min(content.bottom() - 23, grid.bounds.bottom() + 5);
        int buttonWidth = 27;
        int centerX = content.x + content.width / 2;
        ModernMainLayout.Rect previous = new ModernMainLayout.Rect(centerX - 62, y, buttonWidth, 19);
        ModernMainLayout.Rect next = new ModernMainLayout.Rect(centerX + 35, y, buttonWidth, 19);
        // The page label is included in drag targets so users do not need to
        // place a dragged path exactly over the compact arrow buttons.
        dashboardPreviousPageBounds = new ModernMainLayout.Rect(centerX - 66, y, 64, 19);
        dashboardNextPageBounds = new ModernMainLayout.Rect(centerX + 2, y, 64, 19);
        drawPagerButton(previous, false, dashboardPage > 0, mouseX, mouseY);
        drawPagerButton(next, true, dashboardPage + 1 < dashboardPageCount, mouseX, mouseY);
        String pageText = (dashboardPage + 1) + " / " + dashboardPageCount;
        int textWidth = fontRenderer.getStringWidth(pageText);
        ModernUiRenderer.drawText(fontRenderer, pageText, centerX - textWidth / 2,
                y + (19 - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.SUBTLE_TEXT, textWidth);
        hitTargets.add(new HitTarget(TargetType.DASHBOARD_PAGE, previous, "previous"));
        hitTargets.add(new HitTarget(TargetType.DASHBOARD_PAGE, next, "next"));
    }

    private void drawPagerButton(ModernMainLayout.Rect bounds, boolean right, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = hovered ? (enabled ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE_HOVER)
                : enabled ? ModernUiRenderer.SURFACE : ModernUiRenderer.DISABLED_SURFACE;
        int border = hovered ? (enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.ACCENT_DIM)
                : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                fill, border);
        ModernUiRenderer.drawChevron(bounds.x + (right ? 10 : 11), bounds.y + 6, right,
                ModernUiRenderer.readableText(enabled ? (hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT)
                        : ModernUiRenderer.DISABLED_TEXT, fill));
    }

    private void drawDashboardScrollbar(ModernMainLayout.Grid grid, int mouseX, int mouseY) {
        if (maxCardScrollOffset <= 0 || grid == null) {
            return;
        }
        dashboardScrollbar.drawAt(grid.bounds.right() + ModernHoverScrollbar.GUTTER, grid.bounds.y, grid.bounds.height, cardScrollOffset,
                maxCardScrollOffset, grid.bounds.height, grid.bounds.height + maxCardScrollOffset, mouseX, mouseY,
                value -> cardScrollOffset = value);
    }

    private boolean isDashboardGroupingActive(MainUiLayoutManager.DashboardViewMeta view) {
        if (hasSearchQuery()) {
            return false;
        }
        return I18n.format("gui.inventory.category.common").equals(GuiInventory.currentCategory)
                ? view != null && view.grouping
                : isCustomRootCategoryGrouping();
    }

    private boolean isCustomRootCategoryGrouping() {
        String category = GuiInventory.currentCategory == null ? "" : GuiInventory.currentCategory.trim();
        String subCategory = GuiInventory.currentCustomSubCategory == null ? "" : GuiInventory.currentCustomSubCategory.trim();
        return subCategory.isEmpty() && GuiInventory.isCustomOverlayCategory(category)
                && !MainUiLayoutManager.getSubCategories(category).isEmpty();
    }

    private String getPathDashboardGroupKey(String category, String subCategory) {
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedSubCategory = subCategory == null ? "" : subCategory.trim();
        return "path-group:" + normalizedCategory + "::" + normalizedSubCategory.toLowerCase(Locale.ROOT);
    }

    private PathSequence getCustomPathSequence(String command) {
        PathSequence sequence = PathSequenceManager.getSequence(getPathSequenceCommandName(command));
        return sequence != null && sequence.isCustom() ? sequence : null;
    }

    private String normalizeSequenceSubCategory(PathSequence sequence) {
        return sequence == null || sequence.getSubCategory() == null ? "" : sequence.getSubCategory().trim();
    }

    private MainUiLayoutManager.DashboardViewMeta getDashboardView() {
        return MainUiLayoutManager.getModernDashboardView(getDashboardViewKey());
    }

    private String getDashboardViewKey() {
        String category = GuiInventory.currentCategory == null ? "" : GuiInventory.currentCategory.trim();
        String subCategory = GuiInventory.currentCustomSubCategory == null
                ? "" : GuiInventory.currentCustomSubCategory.trim();
        return subCategory.isEmpty() ? category : category + "::" + subCategory;
    }

    private int getDashboardCardHeight(MainUiLayoutManager.DashboardViewMeta view) {
        String size = view == null ? MainUiLayoutManager.ICON_MEDIUM : view.iconSize;
        if (MainUiLayoutManager.ICON_SMALL.equals(size)) {
            return 38;
        }
        if (MainUiLayoutManager.ICON_LARGE.equals(size) || MainUiLayoutManager.ICON_XL.equals(size)) {
            return 60;
        }
        return 48;
    }

    private String getDashboardSortLabel(MainUiLayoutManager.DashboardViewMeta view) {
        String mode = view == null ? MainUiLayoutManager.SORT_DEFAULT : view.sortMode;
        if (MainUiLayoutManager.SORT_ALPHABETICAL.equals(mode)) {
            return tr("gui.modern.sort_name", "gui.modern.main.u050");
        }
        if (MainUiLayoutManager.SORT_ACTIVE_FIRST.equals(mode)) {
            return tr("gui.modern.sort_active", "gui.modern.main.u049");
        }
        return tr("gui.modern.default", "gui.modern.main.u048");
    }

    private String getDashboardIconSizeLabel(MainUiLayoutManager.DashboardViewMeta view) {
        String size = view == null ? MainUiLayoutManager.ICON_MEDIUM : view.iconSize;
        if (MainUiLayoutManager.ICON_SMALL.equals(size)) {
            return tr("gui.modern.small", "gui.modern.main.u051");
        }
        if (MainUiLayoutManager.ICON_LARGE.equals(size) || MainUiLayoutManager.ICON_XL.equals(size)) {
            return tr("gui.modern.large", "gui.modern.main.u053");
        }
        return tr("gui.modern.medium", "gui.modern.main.u052");
    }

    private String getDashboardColumnsLabel(MainUiLayoutManager.DashboardViewMeta view) {
        int columns = view == null ? 0 : view.columns;
        return columns <= 0 ? tr("gui.modern.auto", "gui.modern.main.u054") : String.valueOf(columns);
    }

    private float updateDashboardSettingsGearRotation() {
        if (!dashboardSettingsAnimating) {
            return 0.0F;
        }
        float progress = Math.min(1.0F,
                (System.currentTimeMillis() - dashboardSettingsAnimationStartedAt) / 260.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        if (progress >= 1.0F) {
            dashboardSettingsAnimating = false;
            dashboardSettingsOpen = true;
            return 0.0F;
        }
        return eased * 360.0F;
    }

    private void drawFeatureCard(MenuCard card, ModernMainLayout.Rect bounds, ModernMainLayout.Rect clipBounds, int mouseX,
            int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY) && clipBounds.contains(mouseX, mouseY);
        boolean toggle = isToggleCommand(card.command);
        boolean backgroundRunning = isPathSequenceRunningBackground(card.command);
        boolean foregroundRunning = !backgroundRunning && isPathSequenceRunningForeground(card.command);
        boolean running = backgroundRunning || foregroundRunning;
        boolean active = running || isCommandActive(card.command);
        boolean dragging = isDraggedPathCommand(card.command);
        float pulse = running ? ModernUiRenderer.runningPulse() : 0.0F;
        int runningAccent = running ? ModernUiRenderer.runningAccent(backgroundRunning, pulse) : 0;
        int fill = dragging ? ModernUiRenderer.SELECTED_SURFACE
                : running ? ModernUiRenderer.runningFill(backgroundRunning, hovered ? Math.min(1.0F, pulse + 0.12F) : pulse)
                        : active ? ModernUiRenderer.SELECTED_SURFACE
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = dragging ? ModernUiRenderer.ACCENT
                : running ? runningAccent
                        : active ? ModernUiRenderer.ACCENT_DIM
                                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6, fill, border);
        if (running) {
            ModernUiRenderer.drawRoundedRect(bounds.x + 1, bounds.y + 1, Math.max(1, bounds.width - 2), 3, 1,
                    runningAccent);
            ModernUiRenderer.drawRoundedRect(bounds.x, bounds.y + 5, 3, Math.max(8, bounds.height - 10), 2,
                    runningAccent);
        } else if (active) {
            ModernUiRenderer.drawRoundedRect(bounds.x, bounds.y + 5, 3, Math.max(8, bounds.height - 10), 2,
                    ModernUiRenderer.ACCENT);
        }

        int badgeSize = Math.max(20, Math.min(34, bounds.height - 12));
        int badgeX = bounds.x + 9;
        int badgeY = bounds.y + (bounds.height - badgeSize) / 2;
        int badgeFill = running ? ModernUiRenderer.runningBadge(backgroundRunning, pulse) : ModernUiRenderer.ICON_SURFACE;
        ModernUiRenderer.drawRoundedRect(badgeX, badgeY, badgeSize, badgeSize, 6, badgeFill);
        ModernUiRenderer.drawIcon(getCardIcon(card), badgeX + 4, badgeY + 4, badgeSize - 8,
                ModernUiRenderer.readableText(running ? runningAccent : ModernUiRenderer.ICON_TEXT, badgeFill));

        int textX = badgeX + badgeSize + 8;
        boolean sequenceCard = isPathSequenceCommand(card.command);
        boolean showInfo = sequenceCard || bounds.width >= 146;
        int actionSpace = toggle ? 49 : 23;
        int infoSpace = showInfo && !sequenceCard ? 19 : 0;
        int textWidth = Math.max(sequenceCard ? 1 : 24, bounds.right() - textX - actionSpace - infoSpace - 7);
        int titleY = bounds.height >= 46 ? bounds.y + 9
                : bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        ModernUiRenderer.drawText(fontRenderer, card.title, textX, titleY,
                ModernUiRenderer.readableText(ModernUiRenderer.TEXT, fill), textWidth);
        if (bounds.height >= 46 && textWidth >= 42) {
            ModernUiRenderer.drawText(fontRenderer, getCardSubtitle(card, active), textX, titleY + 14,
                    ModernUiRenderer.readableText(running ? runningAccent : ModernUiRenderer.SUBTLE_TEXT, fill), textWidth);
        }

        if (toggle) {
            int toggleWidth = bounds.width < 125 ? 26 : 32;
            int toggleX = bounds.right() - toggleWidth - 9;
            int toggleY = bounds.y + (bounds.height - 14) / 2;
            ModernUiRenderer.drawToggle(toggleX, toggleY, toggleWidth, 14, active,
                    hovered && mouseX >= toggleX - 2);
        } else if (!sequenceCard) {
            int chevronX = bounds.right() - 16;
            int chevronY = bounds.y + (bounds.height - 8) / 2;
            ModernUiRenderer.drawChevron(chevronX, chevronY, true,
                    ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill));
        }

        hitTargets.add(new HitTarget(TargetType.COMMAND, intersection(bounds, clipBounds), card.command));
        PathSequence sequence = getCustomPathSequence(card.command);
        if (sequence != null) {
            int halfWidth = bounds.width / 2;
            dashboardPathDropTargets.add(new PathDropTarget(sequence.getCategory(), normalizeSequenceSubCategory(sequence),
                    card.title, intersection(new ModernMainLayout.Rect(bounds.x, bounds.y, halfWidth, bounds.height),
                            clipBounds), sequence.getName(), false));
            dashboardPathDropTargets.add(new PathDropTarget(sequence.getCategory(), normalizeSequenceSubCategory(sequence),
                    card.title, intersection(new ModernMainLayout.Rect(bounds.x + halfWidth, bounds.y,
                            bounds.width - halfWidth, bounds.height), clipBounds), sequence.getName(), true));
            if (pathDragActive && currentPathDropTarget != null
                    && sequence.getName().equals(currentPathDropTarget.sequenceName)) {
                int lineX = currentPathDropTarget.placeAfter ? bounds.right() - 2 : bounds.x;
                ModernUiRenderer.drawRoundedRect(lineX, bounds.y, 2, bounds.height, 1, ModernUiRenderer.ACCENT);
            }
        }
        if (showInfo) {
            int infoX = sequenceCard ? bounds.right() - 22
                    : toggle ? bounds.right() - 57 : bounds.right() - 34;
            ModernMainLayout.Rect infoBounds = new ModernMainLayout.Rect(infoX,
                    bounds.y + (bounds.height - 11) / 2, 11, 11);
            boolean infoHovered = infoBounds.contains(mouseX, mouseY) && clipBounds.contains(mouseX, mouseY);
            if (infoHovered) {
                ModernUiRenderer.drawRoundedRect(infoBounds.x - 2, infoBounds.y - 2, infoBounds.width + 4,
                        infoBounds.height + 4, 4, ModernUiRenderer.SURFACE_HOVER);
            }
            ModernUiRenderer.drawInfoIcon(infoBounds.x, infoBounds.y,
                    ModernUiRenderer.readableText(infoHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                            infoHovered ? ModernUiRenderer.SURFACE_HOVER : fill));
            ModernTooltipSupport.registerDashboardFeatureInfoIcon(this, infoBounds.x, infoBounds.y,
                    infoBounds.width, infoBounds.height, card.title, getCardInfoDescription(card),
                    getCardShortcutLabel(card), hasDashboardMouseActions(card));
            hitTargets.add(new HitTarget(TargetType.INFO, intersection(infoBounds, clipBounds), ""));
        }
    }

    private boolean beginPathDrag(String command, int mouseX, int mouseY) {
        PathSequence sequence = getCustomPathSequence(command);
        if (sequence == null) {
            return false;
        }
        draggedPathCommand = command;
        draggedPathSequenceName = sequence.getName();
        draggedPathSourceCategory = sequence.getCategory() == null ? "" : sequence.getCategory().trim();
        draggedPathSourceSubCategory = normalizeSequenceSubCategory(sequence);
        pathDragStartMouseX = mouseX;
        pathDragStartMouseY = mouseY;
        pathDragPending = true;
        pathDragActive = false;
        pathDragPagerHover = "";
        currentPathDropTarget = null;
        return true;
    }

    private void updatePathDrag(int mouseX, int mouseY) {
        if (!pathDragPending) {
            return;
        }
        if (!pathDragActive) {
            int deltaX = mouseX - pathDragStartMouseX;
            int deltaY = mouseY - pathDragStartMouseY;
            if (deltaX * deltaX + deltaY * deltaY < PATH_DRAG_START_DISTANCE * PATH_DRAG_START_DISTANCE) {
                return;
            }
            pathDragActive = true;
            ModernTooltipSupport.setSuppressed(this, true);
        }

        String pagerHover = getPathDragPagerHover(mouseX, mouseY);
        if (!pagerHover.equals(pathDragPagerHover)) {
            pathDragPagerHover = pagerHover;
            if ("previous".equals(pagerHover) || "next".equals(pagerHover)) {
                int delta = "previous".equals(pagerHover) ? -1 : 1;
                dashboardPage = clamp(dashboardPage + delta, 0, Math.max(0, dashboardPageCount - 1));
            }
        }
        currentPathDropTarget = findPathDropTargetAt(mouseX, mouseY);
    }

    private void finishPathDrag(int mouseX, int mouseY) {
        boolean moved = false;
        boolean wasDragging = pathDragActive;
        if (wasDragging) {
            currentPathDropTarget = findPathDropTargetAt(mouseX, mouseY);
            if (currentPathDropTarget != null) {
                boolean changedGroup = !currentPathDropTarget.matches(draggedPathSourceCategory,
                        draggedPathSourceSubCategory);
                if (changedGroup) {
                    moved = PathSequenceManager.moveCustomSequenceTo(draggedPathSequenceName,
                            currentPathDropTarget.category, currentPathDropTarget.subCategory);
                }
                if (!currentPathDropTarget.sequenceName.isEmpty()) {
                    moved |= PathSequenceManager.moveCustomSequenceRelative(draggedPathSequenceName,
                            currentPathDropTarget.sequenceName, currentPathDropTarget.placeAfter);
                    MainUiLayoutManager.setModernDashboardSortMode(getDashboardViewKey(), MainUiLayoutManager.SORT_DEFAULT);
                }
                if (moved) {
                    GuiInventory.refreshGuiLists();
                    if (changedGroup) {
                        GuiInventory.setCurrentCategorySelection(currentPathDropTarget.category,
                                currentPathDropTarget.subCategory);
                        if (!currentPathDropTarget.sequenceName.isEmpty()) {
                            MainUiLayoutManager.setModernDashboardSortMode(getDashboardViewKey(), MainUiLayoutManager.SORT_DEFAULT);
                        }
                        resetDashboardPosition();
                    }
                }
            }
        }
        String command = draggedPathCommand;
        clearPathDrag();
        if (moved) {
            return;
        }
        if (!wasDragging && !command.isEmpty()) {
            try {
                GuiModernMainActions.handleCommand(command, 0, this.mc);
            } catch (IOException ignored) {
                // Preserve normal screen input when a sequence cannot start.
            }
        }
    }

    private void clearPathDrag() {
        draggedPathCommand = "";
        draggedPathSequenceName = "";
        draggedPathSourceCategory = "";
        draggedPathSourceSubCategory = "";
        pathDragPending = false;
        pathDragActive = false;
        pathDragPagerHover = "";
        currentPathDropTarget = null;
        ModernTooltipSupport.setSuppressed(this, false);
    }

    private PathDropTarget findPathDropTargetAt(int mouseX, int mouseY) {
        if (!pathDragActive) {
            return null;
        }
        for (int i = dashboardPathDropTargets.size() - 1; i >= 0; i--) {
            PathDropTarget target = dashboardPathDropTargets.get(i);
            if (target.bounds != null && target.bounds.contains(mouseX, mouseY)) {
                return target.sequenceName.equals(draggedPathSequenceName) ? null : target;
            }
        }
        return null;
    }

    private String getPathDragPagerHover(int mouseX, int mouseY) {
        if (!pathDragActive || !MainUiLayoutManager.FLOW_PAGED.equals(getDashboardView().flowMode)) {
            return "";
        }
        if (dashboardPreviousPageBounds != null && dashboardPage > 0 && dashboardPreviousPageBounds.contains(mouseX, mouseY)) {
            return "previous";
        }
        if (dashboardNextPageBounds != null && dashboardPage + 1 < dashboardPageCount
                && dashboardNextPageBounds.contains(mouseX, mouseY)) {
            return "next";
        }
        return "";
    }

    private boolean isCurrentPathDropTarget(String category, String subCategory) {
        return pathDragActive && currentPathDropTarget != null
                && currentPathDropTarget.sequenceName.isEmpty()
                && currentPathDropTarget.matches(category, subCategory);
    }

    private boolean isDraggedPathCommand(String command) {
        return pathDragPending && draggedPathCommand.equals(command);
    }

    private void drawPathDragGhost(int mouseX, int mouseY) {
        if (!pathDragActive || draggedPathSequenceName.isEmpty()) {
            return;
        }
        String targetText = currentPathDropTarget == null ? "gui.modern.main.u063"
                : !currentPathDropTarget.sequenceName.isEmpty()
                        ? I18n.format(currentPathDropTarget.placeAfter ? "gui.modern.inv.fmt.sort_after"
                                : "gui.modern.inv.fmt.sort_before", currentPathDropTarget.label)
                        : tr("gui.modern.main.fmt.move_to", "移动到: %s", currentPathDropTarget.label);
        int width = Math.max(148, Math.min(246,
                Math.max(fontRenderer.getStringWidth(draggedPathSequenceName), fontRenderer.getStringWidth(targetText)) + 18));
        int x = Math.min(this.width - width - 6, mouseX + 12);
        int y = Math.min(this.height - 40, mouseY + 12);
        ModernUiRenderer.drawPanel(x, y, width, 36, 6, 0xF0182B38, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(fontRenderer, draggedPathSequenceName, x + 8, y + 6, ModernUiRenderer.TEXT, width - 16);
        ModernUiRenderer.drawText(fontRenderer, targetText, x + 8, y + 20,
                currentPathDropTarget == null ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.ACCENT,
                width - 16);
    }

    private void drawEmptyState(ModernMainLayout.Rect gridBounds) {
        int centerX = gridBounds.x + gridBounds.width / 2;
        int iconY = gridBounds.y + Math.max(14, gridBounds.height / 3);
        ModernUiRenderer.drawSearchIcon(centerX - 7, iconY, ModernUiRenderer.MUTED_TEXT);
        String title = hasSearchQuery() ? "gui.modern.main.u064" : "gui.modern.main.u065";
        int textWidth = fontRenderer.getStringWidth(title);
        ModernUiRenderer.drawText(fontRenderer, title, centerX - textWidth / 2, iconY + 25, ModernUiRenderer.SUBTLE_TEXT,
                gridBounds.width - 20);
    }

    private void drawToolsPopover(ModernMainLayout.Layout layout, int mouseX, int mouseY) {
        toolsPopoverBounds = null;
        if (!toolsOpen) {
            return;
        }
        // Covered information icons must not cancel the popover's input globally.
        ModernTooltipSupport.clearRegisteredInfoIcons(this);
        List<ToolEntry> entries = getToolEntries();
        int rowHeight = 25;
        int menuWidth = Math.min(208, Math.max(155, layout.shell.width - 24));
        int menuHeight = entries.size() * rowHeight + 14;
        int x = clamp(layout.header.right() - menuWidth - 46, layout.shell.x + 8, layout.shell.right() - menuWidth - 8);
        int y = layout.header.bottom() + 8;
        ModernMainLayout.Rect panel = new ModernMainLayout.Rect(x, y, menuWidth, menuHeight);
        toolsPopoverBounds = panel;
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 7,
                ModernUiRenderer.TOOLTIP_SURFACE, ModernUiRenderer.BORDER);

        for (int i = 0; i < entries.size(); i++) {
            ToolEntry entry = entries.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(panel.x + 7, panel.y + 7 + i * rowHeight,
                    panel.width - 14, rowHeight);
            boolean hovered = row.contains(mouseX, mouseY);
            int rowFill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.TOOLTIP_SURFACE;
            if (hovered) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 4,
                        rowFill);
            }
            int rowText = ModernUiRenderer.readableText(ModernUiRenderer.TEXT, rowFill);
            int rowSubtleText = ModernUiRenderer.readableText(ModernUiRenderer.SUBTLE_TEXT, rowFill);
            if ("theme".equals(entry.action)) {
                ModernUiRenderer.drawSettingsIcon(row.x + 8, row.y + 6,
                        hovered ? rowText : rowSubtleText);
            } else {
                ModernUiRenderer.drawStatusDot(row.x + 11, row.y + 9,
                        ModernUiRenderer.readableText("update".equals(entry.action)
                                ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT, rowFill));
            }
            ModernUiRenderer.drawText(fontRenderer, entry.title, row.x + 28,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                    hovered ? rowText : rowSubtleText, row.width - 40);
            ModernUiRenderer.drawChevron(row.right() - 12, row.y + 8, true,
                    ModernUiRenderer.readableText(hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                            rowFill));
            hitTargets.add(new HitTarget(TargetType.UTILITY, row, entry.action));
        }
    }

    private void drawInlineTextInput(int mouseX, int mouseY) {
        ModernMainLayout.Rect popup = positionFloatingPopup(INLINE_POPUP_WIDTH, INLINE_POPUP_HEIGHT);
        inlineTextInputBounds = popup;
        inlineTextInputFieldBounds = new ModernMainLayout.Rect(popup.x + 14, popup.y + 35,
                Math.max(1, popup.width - 28), 22);
        inlineTextInputConfirmBounds = new ModernMainLayout.Rect(popup.x + 14, popup.bottom() - 29, 82, 21);
        inlineTextInputCancelBounds = new ModernMainLayout.Rect(popup.right() - 96, popup.bottom() - 29, 82, 21);

        ModernUiRenderer.drawBackdropOverlay(this.width, this.height, 0x52060B10);
        ModernUiRenderer.drawPanel(popup.x, popup.y, popup.width, popup.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT_DIM);
        ModernUiRenderer.drawText(fontRenderer, inlineTextInputTitle, popup.x + 14, popup.y + 10,
                ModernUiRenderer.TEXT, popup.width - 28);

        inlineTextInputField.x = inlineTextInputFieldBounds.x;
        inlineTextInputField.y = inlineTextInputFieldBounds.y;
        inlineTextInputField.width = inlineTextInputFieldBounds.width;
        inlineTextInputField.height = inlineTextInputFieldBounds.height;
        ModernRuleEditorUi.drawTextField(this, inlineTextInputField);
        drawFloatingButton(inlineTextInputConfirmBounds, "gui.modern.main.u066", true, mouseX, mouseY);
        drawFloatingButton(inlineTextInputCancelBounds, "gui.modern.main.u067", false, mouseX, mouseY);
    }

    private void drawHiddenCategoryPicker(int mouseX, int mouseY) {
        hiddenCategoryPickerCategories = PathSequenceManager.getHiddenCategories();
        if (hiddenCategoryPickerCategories.isEmpty()) {
            closeHiddenCategoryPicker();
            return;
        }

        int availableHeight = lastLayout == null ? Math.max(1, height - 16)
                : Math.max(1, lastLayout.shell.height - 16);
        int maxRowsForViewport = Math.max(1, (availableHeight - 70) / HIDDEN_CATEGORY_ROW_HEIGHT);
        int visibleRows = Math.min(Math.min(7, maxRowsForViewport), hiddenCategoryPickerCategories.size());
        hiddenCategoryPickerMaxScrollOffset = Math.max(0, hiddenCategoryPickerCategories.size() - visibleRows);
        hiddenCategoryPickerScrollOffset = clamp(hiddenCategoryPickerScrollOffset, 0,
                hiddenCategoryPickerMaxScrollOffset);
        int popupHeight = 43 + visibleRows * HIDDEN_CATEGORY_ROW_HEIGHT + 27;
        ModernMainLayout.Rect popup = positionFloatingPopup(Math.min(310, INLINE_POPUP_WIDTH + 24), popupHeight);
        hiddenCategoryPickerBounds = popup;
        hiddenCategoryPickerListBounds = new ModernMainLayout.Rect(popup.x + 10, popup.y + 34,
                Math.max(1, popup.width - 20), visibleRows * HIDDEN_CATEGORY_ROW_HEIGHT);
        hiddenCategoryPickerRowBounds.clear();

        ModernUiRenderer.drawBackdropOverlay(this.width, this.height, 0x52060B10);
        ModernUiRenderer.drawPanel(popup.x, popup.y, popup.width, popup.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT_DIM);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.main.u068", popup.x + 14, popup.y + 10,
                ModernUiRenderer.TEXT, popup.width - 76);
        String count = tr("gui.modern.main.fmt.hidden_count", "%s 个已隐藏", String.valueOf(hiddenCategoryPickerCategories.size()));
        int countWidth = fontRenderer.getStringWidth(count);
        ModernUiRenderer.drawText(fontRenderer, count, popup.right() - countWidth - 14, popup.y + 11,
                ModernUiRenderer.SUBTLE_TEXT, countWidth);

        ModernUiRenderer.drawSubtlePanel(hiddenCategoryPickerListBounds.x, hiddenCategoryPickerListBounds.y,
                hiddenCategoryPickerListBounds.width, hiddenCategoryPickerListBounds.height, 4,
                0x33101820, ModernUiRenderer.BORDER_SUBTLE);
        for (int i = 0; i < visibleRows; i++) {
            int categoryIndex = i + hiddenCategoryPickerScrollOffset;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(hiddenCategoryPickerListBounds.x + 3,
                    hiddenCategoryPickerListBounds.y + i * HIDDEN_CATEGORY_ROW_HEIGHT + 1,
                    ModernHoverScrollbar.contentWidth(hiddenCategoryPickerListBounds.width), HIDDEN_CATEGORY_ROW_HEIGHT - 2);
            hiddenCategoryPickerRowBounds.add(row);
            boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 4,
                        ModernUiRenderer.SURFACE_HOVER);
            }
            ModernUiRenderer.drawStatusDot(row.x + 8, row.y + (row.height - 7) / 2,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.drawText(fontRenderer, hiddenCategoryPickerCategories.get(categoryIndex), row.x + 22,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, hovered ? ModernUiRenderer.TEXT
                            : ModernUiRenderer.SUBTLE_TEXT,
                    row.width - 32);
        }
        hiddenCategoryPickerScrollbar.draw(hiddenCategoryPickerListBounds, hiddenCategoryPickerScrollOffset,
                hiddenCategoryPickerMaxScrollOffset, visibleRows, hiddenCategoryPickerCategories.size(), mouseX,
                mouseY, value -> hiddenCategoryPickerScrollOffset = value);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.main.u069", popup.x + 14, popup.bottom() - 18,
                ModernUiRenderer.MUTED_TEXT, popup.width - 28);
    }

    private void drawFloatingButton(ModernMainLayout.Rect bounds, String label, boolean primary, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = primary ? hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = primary ? 0xFFFFB0C4 : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        int text = primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, text, bounds.width - 14);
    }

    private ModernMainLayout.Rect positionFloatingPopup(int requestedWidth, int requestedHeight) {
        ModernMainLayout.Rect shell = lastLayout == null
                ? new ModernMainLayout.Rect(0, 0, Math.max(1, width), Math.max(1, height))
                : lastLayout.shell;
        int width = Math.min(requestedWidth, Math.max(1, shell.width - 16));
        int height = Math.min(requestedHeight, Math.max(1, shell.height - 16));
        int x = contextMenuAnchorX + 8;
        if (x + width > shell.right() - 8) {
            x = contextMenuAnchorX - width - 8;
        }
        int minX = shell.x + 8;
        int maxX = Math.max(minX, shell.right() - width - 8);
        x = clamp(x, minX, maxX);
        int y = contextMenuAnchorY;
        if (y + height > shell.bottom() - 8) {
            y = contextMenuAnchorY - height;
        }
        int minY = shell.y + 8;
        int maxY = Math.max(minY, shell.bottom() - height - 8);
        y = clamp(y, minY, maxY);
        return new ModernMainLayout.Rect(x, y, width, height);
    }

    private void closeInlineTextInput() {
        inlineTextInputOpen = false;
        inlineTextInputTitle = "";
        inlineTextInputCallback = null;
        inlineTextInputBounds = null;
        inlineTextInputFieldBounds = null;
        inlineTextInputConfirmBounds = null;
        inlineTextInputCancelBounds = null;
        if (inlineTextInputField != null) {
            inlineTextInputField.setFocused(false);
        }
    }

    private void closeInlineConfirmation() {
        inlineConfirmationOpen = false;
        inlineConfirmationTitle = "";
        inlineConfirmationMessage = "";
        inlineConfirmationCallback = null;
        inlineConfirmationBounds = null;
        inlineConfirmationConfirmBounds = null;
        inlineConfirmationCancelBounds = null;
    }

    private void closeHiddenCategoryPicker() {
        hiddenCategoryPickerOpen = false;
        hiddenCategoryPickerCategories = Collections.emptyList();
        hiddenCategoryPickerRowBounds.clear();
        hiddenCategoryPickerBounds = null;
        hiddenCategoryPickerListBounds = null;
        hiddenCategoryPickerScrollOffset = 0;
        hiddenCategoryPickerMaxScrollOffset = 0;
    }

    private void confirmInlineTextInput() {
        String value = inlineTextInputField == null ? "" : inlineTextInputField.getText();
        Consumer<String> callback = inlineTextInputCallback;
        closeInlineTextInput();
        if (callback != null) {
            callback.accept(value);
        }
    }

    private boolean handleInlineTextInputClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            closeInlineTextInput();
            return true;
        }
        if (inlineTextInputBounds == null || !inlineTextInputBounds.contains(mouseX, mouseY)) {
            closeInlineTextInput();
            return true;
        }
        if (inlineTextInputConfirmBounds != null && inlineTextInputConfirmBounds.contains(mouseX, mouseY)) {
            confirmInlineTextInput();
            return true;
        }
        if (inlineTextInputCancelBounds != null && inlineTextInputCancelBounds.contains(mouseX, mouseY)) {
            closeInlineTextInput();
            return true;
        }
        if (inlineTextInputFieldBounds != null && inlineTextInputFieldBounds.contains(mouseX, mouseY)
                && inlineTextInputField != null) {
            inlineTextInputField.mouseClicked(mouseX, mouseY, 0);
        }
        return true;
    }

    private void drawInlineConfirmation(int mouseX, int mouseY) {
        ModernMainLayout.Rect popup = positionFloatingPopup(330, 142);
        inlineConfirmationBounds = popup;
        inlineConfirmationConfirmBounds = new ModernMainLayout.Rect(popup.x + 14, popup.bottom() - 29, 92, 21);
        inlineConfirmationCancelBounds = new ModernMainLayout.Rect(popup.right() - 106, popup.bottom() - 29, 92, 21);
        ModernUiRenderer.drawBackdropOverlay(this.width, this.height, 0x52060B10);
        ModernUiRenderer.drawPanel(popup.x, popup.y, popup.width, popup.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.WARNING);
        ModernUiRenderer.drawText(fontRenderer, inlineConfirmationTitle, popup.x + 14, popup.y + 11,
                ModernUiRenderer.TEXT, popup.width - 28);
        String message = com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(inlineConfirmationMessage)
                .replace("\\n", "\n");
        int messageY = popup.y + 43;
        for (String line : fontRenderer.listFormattedStringToWidth(message, popup.width - 28)) {
            fontRenderer.drawString(line, popup.x + 14, messageY, ModernUiRenderer.SUBTLE_TEXT);
            messageY += fontRenderer.FONT_HEIGHT + 3;
        }
        drawCloseConfirmationButton(inlineConfirmationConfirmBounds, "gui.modern.main.u066", true, mouseX, mouseY);
        drawCloseConfirmationButton(inlineConfirmationCancelBounds, "gui.modern.main.u067", false, mouseX, mouseY);
    }

    private boolean handleInlineConfirmationClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            closeInlineConfirmation();
            return true;
        }
        if (inlineConfirmationConfirmBounds != null && inlineConfirmationConfirmBounds.contains(mouseX, mouseY)) {
            Runnable callback = inlineConfirmationCallback;
            closeInlineConfirmation();
            if (callback != null) {
                callback.run();
            }
            return true;
        }
        if (inlineConfirmationCancelBounds != null && inlineConfirmationCancelBounds.contains(mouseX, mouseY)) {
            closeInlineConfirmation();
            return true;
        }
        if (inlineConfirmationBounds == null || !inlineConfirmationBounds.contains(mouseX, mouseY)) {
            closeInlineConfirmation();
        }
        return true;
    }

    private boolean handleInlineConfirmationKey(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeInlineConfirmation();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            Runnable callback = inlineConfirmationCallback;
            closeInlineConfirmation();
            if (callback != null) {
                callback.run();
            }
        }
        return true;
    }

    private boolean handleHiddenCategoryPickerClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || hiddenCategoryPickerBounds == null
                || !hiddenCategoryPickerBounds.contains(mouseX, mouseY)) {
            closeHiddenCategoryPicker();
            return true;
        }
        if (hiddenCategoryPickerScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        for (int i = 0; i < hiddenCategoryPickerRowBounds.size(); i++) {
            if (!hiddenCategoryPickerRowBounds.get(i).contains(mouseX, mouseY)) {
                continue;
            }
            int categoryIndex = hiddenCategoryPickerScrollOffset + i;
            if (categoryIndex >= 0 && categoryIndex < hiddenCategoryPickerCategories.size()) {
                String category = hiddenCategoryPickerCategories.get(categoryIndex);
                closeHiddenCategoryPicker();
                PathSequenceManager.setCategoryHidden(category, false);
                GuiInventory.setCurrentCategorySelection(category, "");
            }
            return true;
        }
        return true;
    }

    private boolean handleInlineTextInputKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeInlineTextInput();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirmInlineTextInput();
            return true;
        }
        if (inlineTextInputField != null) {
            inlineTextInputField.textboxKeyTyped(typedChar, keyCode);
        }
        return true;
    }

    private void drawCommandPalette(int mouseX, int mouseY) {
        this.hoveredTooltip = "";
        refreshCommandPaletteEntries();
        ModernMainLayout.Rect shell = this.lastLayout == null ? new ModernMainLayout.Rect(0, 0, this.width, this.height)
                : this.lastLayout.shell;
        int paletteWidth = Math.min(620, Math.max(1, shell.width - 24));
        int paletteHeight = Math.min(440, Math.max(180, shell.height - 26));
        paletteHeight = Math.min(paletteHeight, Math.max(1, shell.height - 12));
        int paletteX = shell.x + Math.max(0, (shell.width - paletteWidth) / 2);
        int requestedY = shell.y + Math.max(12, shell.height / 10);
        int paletteY = clamp(requestedY, shell.y + 6, Math.max(shell.y + 6, shell.bottom() - paletteHeight - 6));
        this.commandPaletteBounds = new ModernMainLayout.Rect(paletteX, paletteY, paletteWidth, paletteHeight);

        ModernUiRenderer.drawBackdropOverlay(this.width, this.height, 0x72060B10);
        ModernUiRenderer.drawPanel(paletteX, paletteY, paletteWidth, paletteHeight, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT_DIM);

        int innerX = paletteX + 14;
        int innerWidth = Math.max(1, paletteWidth - 28);
        ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.command_palette.title"), innerX, paletteY + 10,
                ModernUiRenderer.TEXT, Math.max(50, innerWidth - 100));
        String countText = I18n.format("gui.command_palette.results", this.commandPaletteEntries.size());
        int countWidth = fontRenderer.getStringWidth(countText);
        ModernUiRenderer.drawText(fontRenderer, countText, paletteX + paletteWidth - 14 - countWidth, paletteY + 11,
                ModernUiRenderer.SUBTLE_TEXT, countWidth);

        int searchY = paletteY + 32;
        this.commandPaletteSearchBounds = new ModernMainLayout.Rect(innerX, searchY, innerWidth, 27);
        boolean searchFocused = this.commandPaletteSearchField != null
                && this.commandPaletteSearchField.isFocused();
        ModernUiRenderer.drawSubtlePanel(this.commandPaletteSearchBounds.x, this.commandPaletteSearchBounds.y,
                this.commandPaletteSearchBounds.width, this.commandPaletteSearchBounds.height, 5, GuiTheme.INPUT_BG,
                searchFocused ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(this.commandPaletteSearchBounds.x + 8,
                this.commandPaletteSearchBounds.y + 7,
                searchFocused ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
        this.commandPaletteSearchField.x = this.commandPaletteSearchBounds.x + 23;
        this.commandPaletteSearchField.y = this.commandPaletteSearchBounds.y
                + (this.commandPaletteSearchBounds.height - fontRenderer.FONT_HEIGHT) / 2;
        this.commandPaletteSearchField.width = Math.max(1, this.commandPaletteSearchBounds.width - 47);
        this.commandPaletteSearchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(this.commandPaletteSearchField);
        ModernUiRenderer.drawTextField(this.commandPaletteSearchField);
        if (this.commandPaletteSearchField.getText().isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.command_palette.search_placeholder"),
                    this.commandPaletteSearchField.x + 2, this.commandPaletteSearchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, this.commandPaletteSearchField.width - 8));
        }
        if (!this.commandPaletteSearchField.getText().isEmpty()) {
            this.commandPaletteClearBounds = new ModernMainLayout.Rect(
                    this.commandPaletteSearchBounds.right() - 18, this.commandPaletteSearchBounds.y + 7, 13, 13);
            boolean hovered = this.commandPaletteClearBounds.contains(mouseX, mouseY);
            if (hovered) {
                ModernUiRenderer.drawRoundedRect(this.commandPaletteClearBounds.x - 2,
                        this.commandPaletteClearBounds.y - 2, this.commandPaletteClearBounds.width + 4,
                        this.commandPaletteClearBounds.height + 4, 4, ModernUiRenderer.SURFACE_HOVER);
            }
            ModernUiRenderer.drawCloseIcon(this.commandPaletteClearBounds.x + 2,
                    this.commandPaletteClearBounds.y + 2,
                    hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        } else {
            this.commandPaletteClearBounds = null;
        }

        int listY = this.commandPaletteSearchBounds.bottom() + 10;
        int listHeight = Math.max(1, paletteY + paletteHeight - listY - 13);
        this.commandPaletteListBounds = new ModernMainLayout.Rect(innerX, listY, innerWidth, listHeight);
        int visibleRows = Math.max(1, listHeight / COMMAND_PALETTE_ROW_HEIGHT);
        this.commandPaletteMaxScrollOffset = Math.max(0, this.commandPaletteEntries.size() - visibleRows);
        this.commandPaletteSelectedIndex = clamp(this.commandPaletteSelectedIndex, 0,
                Math.max(0, this.commandPaletteEntries.size() - 1));
        this.commandPaletteScrollOffset = clamp(this.commandPaletteScrollOffset, 0,
                this.commandPaletteMaxScrollOffset);
        ensureCommandPaletteSelectionVisible(visibleRows);

        ModernUiRenderer.drawDivider(innerX, listY - 5, innerWidth, ModernUiRenderer.BORDER_SUBTLE);
        this.commandPaletteRowBounds.clear();
        this.commandPaletteSettingsBounds.clear();
        ModernUiRenderer.beginClip(this.commandPaletteListBounds);
        if (this.commandPaletteEntries.isEmpty()) {
            ModernUiRenderer.drawSearchIcon(this.commandPaletteListBounds.x + 12,
                    this.commandPaletteListBounds.y + 16, ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.command_palette.empty"),
                    this.commandPaletteListBounds.x + 32, this.commandPaletteListBounds.y + 18,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, this.commandPaletteListBounds.width - 44));
        } else {
            for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
                int entryIndex = this.commandPaletteScrollOffset + visibleIndex;
                if (entryIndex >= this.commandPaletteEntries.size()) {
                    break;
                }
                CommandPaletteEntry entry = this.commandPaletteEntries.get(entryIndex);
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(this.commandPaletteListBounds.x,
                        this.commandPaletteListBounds.y + visibleIndex * COMMAND_PALETTE_ROW_HEIGHT,
                        ModernHoverScrollbar.contentWidth(this.commandPaletteListBounds.width), COMMAND_PALETTE_ROW_HEIGHT - 2);
                ModernMainLayout.Rect settings = new ModernMainLayout.Rect(row.right() - 22,
                        row.y + (row.height - 14) / 2, 14, 14);
                this.commandPaletteRowBounds.add(row);
                boolean hovered = row.contains(mouseX, mouseY);
                boolean selected = entryIndex == this.commandPaletteSelectedIndex;
                int rowFill = selected ? ModernUiRenderer.SELECTED_SURFACE
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
                if (selected || hovered) {
                    ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 4,
                            rowFill);
                    if (selected) {
                        ModernUiRenderer.drawRoundedRect(row.x, row.y + 6, 2, Math.max(8, row.height - 12), 1,
                                ModernUiRenderer.ACCENT);
                    }
                }

                Keybind paletteKeybind = getPaletteKeybind(entry);
                String binding = getPaletteBindingText(entry);
                int bindingWidth = Math.min(120, Math.max(44, fontRenderer.getStringWidth(binding) + 12));
                int bindingX = settings.x - 8 - bindingWidth;
                ModernMainLayout.Rect bindingAction = new ModernMainLayout.Rect(bindingX, row.y + 9,
                        row.right() - bindingX - 7, 22);
                this.commandPaletteSettingsBounds.add(bindingAction);
                boolean bindingHovered = bindingAction.contains(mouseX, mouseY);
                int textX = row.x + 14;
                int textWidth = Math.max(24, bindingX - textX - 10);
                ModernUiRenderer.drawText(fontRenderer, entry.title, textX, row.y + 7,
                        ModernUiRenderer.readableText(ModernUiRenderer.TEXT, rowFill), textWidth);
                String detail = entry.group + (entry.description.isEmpty() ? "" : "  ·  " + entry.description);
                ModernUiRenderer.drawText(fontRenderer, detail, textX, row.y + 22, ModernUiRenderer.readableText(ModernUiRenderer.SUBTLE_TEXT, rowFill),
                        textWidth);

                int bindingFill = bindingHovered ? ModernUiRenderer.SURFACE_PRESSED : ModernUiRenderer.ICON_SURFACE;
                ModernUiRenderer.drawSubtlePanel(bindingX, row.y + 12, bindingWidth, 17, 4, bindingFill,
                        bindingHovered ? ModernUiRenderer.ACCENT
                                : selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, binding, bindingX + 6, row.y + 17,
                        ModernUiRenderer.readableText(paletteKeybind == null || paletteKeybind.getKeyCode() == Keyboard.KEY_NONE
                                ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.WARNING, bindingFill),
                        Math.max(1, bindingWidth - 12));

                ModernUiRenderer.drawSettingsIcon(settings.x + 1, settings.y + 1,
                        ModernUiRenderer.readableText(bindingHovered ? ModernUiRenderer.TEXT
                                : entry.keybindTarget == null ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUBTLE_TEXT, rowFill));
                if (bindingHovered) {
                    setHoveredTooltip(entry.keybindTarget == null
                            ? I18n.format("gui.command_palette.settings_generic")
                            : I18n.format("gui.command_palette.settings_tooltip"), mouseX, mouseY);
                }
            }
        }
        ModernUiRenderer.endClip();

        commandPaletteScrollbar.draw(this.commandPaletteListBounds, this.commandPaletteScrollOffset,
                this.commandPaletteMaxScrollOffset, visibleRows, Math.max(visibleRows, this.commandPaletteEntries.size()),
                mouseX, mouseY, value -> this.commandPaletteScrollOffset = value);
    }

    private void ensureCommandPaletteSelectionVisible(int visibleRows) {
        if (this.commandPaletteEntries.isEmpty()) {
            this.commandPaletteSelectedIndex = 0;
            this.commandPaletteScrollOffset = 0;
            return;
        }
        int safeVisibleRows = Math.max(1, visibleRows);
        if (this.commandPaletteSelectedIndex < this.commandPaletteScrollOffset) {
            this.commandPaletteScrollOffset = this.commandPaletteSelectedIndex;
        } else if (this.commandPaletteSelectedIndex >= this.commandPaletteScrollOffset + safeVisibleRows) {
            this.commandPaletteScrollOffset = this.commandPaletteSelectedIndex - safeVisibleRows + 1;
        }
        this.commandPaletteScrollOffset = clamp(this.commandPaletteScrollOffset, 0,
                this.commandPaletteMaxScrollOffset);
    }

    private int getCommandPaletteScrollbarThumbHeight(int visibleRows) {
        return Math.min(this.commandPaletteListBounds.height,
                Math.max(18, (int) (visibleRows / (float) Math.max(1, this.commandPaletteEntries.size())
                        * this.commandPaletteListBounds.height)));
    }

    private void updateCommandPaletteScrollFromMouse(int mouseY) {
        if (this.commandPaletteListBounds == null || this.commandPaletteMaxScrollOffset <= 0) {
            return;
        }
        int thumbHeight = getCommandPaletteScrollbarThumbHeight(getCommandPaletteVisibleRows());
        int trackHeight = Math.max(1, this.commandPaletteListBounds.height - thumbHeight);
        float percent = (mouseY - this.commandPaletteListBounds.y - thumbHeight / 2.0F) / trackHeight;
        percent = Math.max(0.0F, Math.min(1.0F, percent));
        this.commandPaletteScrollOffset = Math.round(percent * this.commandPaletteMaxScrollOffset);
        this.commandPaletteSelectedIndex = clamp(this.commandPaletteScrollOffset, 0,
                Math.max(0, this.commandPaletteEntries.size() - 1));
    }

    private void updateTabScrollFromMouse(int mouseX) {
        if (tabScrollbarBounds == null || tabScrollbarThumbBounds == null || maxTabScrollOffset <= 0) {
            return;
        }
        int travel = Math.max(1, tabScrollbarBounds.width - tabScrollbarThumbBounds.width);
        int target = clamp(mouseX - tabScrollbarThumbBounds.width / 2, tabScrollbarBounds.x,
                tabScrollbarBounds.x + travel);
        tabScrollOffset = Math.round((target - tabScrollbarBounds.x) * maxTabScrollOffset / (float) travel);
    }

    private void updateNavigationScrollFromMouse(int mouseY) {
        if (navigationScrollbarBounds == null || navigationScrollbarThumbBounds == null
                || maxNavigationScrollOffset <= 0) {
            return;
        }
        int travel = Math.max(1, navigationScrollbarBounds.height - navigationScrollbarThumbBounds.height);
        int target = clamp(mouseY - navigationScrollbarThumbBounds.height / 2, navigationScrollbarBounds.y,
                navigationScrollbarBounds.y + travel);
        navigationScrollOffset = Math.round(
                (target - navigationScrollbarBounds.y) * maxNavigationScrollOffset / (float) travel);
    }

    private void drawResizeAffordance(int mouseX, int mouseY) {
        if (lastLayout == null) {
            return;
        }
        ResizeHandle hoveredHandle = resizeHandle == ResizeHandle.NONE ? findResizeHandle(mouseX, mouseY) : resizeHandle;
        if (hoveredHandle == ResizeHandle.NONE) {
            return;
        }
        ModernMainLayout.Rect shell = lastLayout.shell;
        int color = resizeHandle == ResizeHandle.NONE ? 0xD08FA4B0 : ModernUiRenderer.ACCENT;
        if (hoveredHandle.left) {
            Gui.drawRect(shell.x - 1, shell.y + 7, shell.x + 1, shell.bottom() - 7, color);
        }
        if (hoveredHandle.right) {
            Gui.drawRect(shell.right() - 1, shell.y + 7, shell.right() + 1, shell.bottom() - 7, color);
        }
        if (hoveredHandle.top) {
            Gui.drawRect(shell.x + 7, shell.y - 1, shell.right() - 7, shell.y + 1, color);
        }
        if (hoveredHandle.bottom) {
            Gui.drawRect(shell.x + 7, shell.bottom() - 1, shell.right() - 7, shell.bottom() + 1, color);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        mouseX = toModernMouseX(mouseX);
        mouseY = toModernMouseY(mouseY);
        boolean detachedScreen = DetachedSwingWindowManager.isDetachedScreen(this);
        if (!detachedScreen && mouseButton == 0
                && RunningStatusHud.mouseClicked(mouseX, mouseY, this.width, this.height)) {
            ModernTooltipSupport.setSuppressed(this, true);
            return;
        }
        if (this.commandPaletteOpen && handleCommandPaletteClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (this.inlineTextInputOpen && handleInlineTextInputClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (this.inlineConfirmationOpen && handleInlineConfirmationClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (this.hiddenCategoryPickerOpen && handleHiddenCategoryPickerClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (modernContextMenu.isOpen() && modernContextMenu.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (closeConfirmationOpen && handleCloseConfirmationClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (handleToolsPopoverClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (!detachedScreen && mouseButton == 0 && beginResize(mouseX, mouseY)) {
            return;
        }
        if (mouseButton == 0 && beginSidebarDrag(mouseX, mouseY)) {
            return;
        }
        if (mouseButton == 0 && tabScrollbar.beginDrag(mouseX, mouseY)) {
            draggingTabScrollbar = true;
            return;
        }
        if (mouseButton == 0 && navigationScrollbar.beginDrag(mouseX, mouseY)) {
            draggingNavigationScrollbar = true;
            return;
        }
        if (mouseButton == 0 && dashboardScrollbar.beginDrag(mouseX, mouseY)) {
            return;
        }
        if (handleDashboardSettingsClick(mouseX, mouseY, mouseButton)) {
            return;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null && settingsTab.mouseClicked(mouseX, mouseY, mouseButton)) {
            settingsTab.clearTextInputFocusOutside(mouseX, mouseY);
            if (searchField != null) {
                searchField.setFocused(false);
            }
            closeReturnedSettingsTab(settingsTab);
            return;
        }
        if (settingsTab != null) {
            settingsTab.clearTextInputFocusOutside(mouseX, mouseY);
        }
        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            if (searchField != null) {
                searchField.mouseClicked(mouseX, mouseY, mouseButton);
                if (mouseButton == 0) {
                    focusDashboardForSearch();
                }
            }
        } else if (searchField != null) {
            searchField.setFocused(false);
        }

        if (dashboardSettingsOpen && !isOverDashboardSettingsUi(mouseX, mouseY)) {
            closeDashboardSettings();
            return;
        }

        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if (target.bounds == null || !target.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (mouseButton == 0 && target.type == TargetType.COMMAND && beginPathDrag(target.value, mouseX, mouseY)) {
                return;
            }
            handleTarget(target, mouseX, mouseY, mouseButton);
            return;
        }

        if (searchBounds != null && searchBounds.contains(mouseX, mouseY)) {
            return;
        }
        if (!detachedScreen && mouseButton == 0 && beginWindowDrag(mouseX, mouseY)) {
            return;
        }

        if (mouseButton == 1 && navigationClipBounds != null && navigationClipBounds.contains(mouseX, mouseY)) {
            openModernContextMenu(mouseX, mouseY, GuiInventory.buildCategoryBlankAreaMenu());
            return;
        }
        if (toolsOpen) {
            toolsOpen = false;
        }
    }

    private boolean handleToolsPopoverClick(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!toolsOpen) {
            return false;
        }
        toolsCapturedMouseButton = mouseButton;
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if ((target.type == TargetType.UTILITY || target.type == TargetType.TOOLS)
                    && target.bounds != null && target.bounds.contains(mouseX, mouseY)) {
                handleTarget(target, mouseX, mouseY, mouseButton);
                return true;
            }
        }
        if (toolsPopoverBounds == null || !toolsPopoverBounds.contains(mouseX, mouseY)) {
            toolsOpen = false;
        }
        // Both panel padding and the click used to dismiss it belong to the popover.
        return true;
    }

    private boolean handleDashboardSettingsClick(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isOverDashboardSettingsUi(mouseX, mouseY)) {
            if (dashboardSettingsOpen) {
                closeDashboardSettings();
                return true;
            }
            return false;
        }
        for (int i = hitTargets.size() - 1; i >= 0; i--) {
            HitTarget target = hitTargets.get(i);
            if ((target.type == TargetType.DASHBOARD_SETTINGS || target.type == TargetType.DASHBOARD_TOOL
                    || target.type == TargetType.DASHBOARD_TOOL_OPTION)
                    && target.bounds != null && target.bounds.contains(mouseX, mouseY)) {
                handleTarget(target, mouseX, mouseY, mouseButton);
                return true;
            }
        }
        return true;
    }

    private boolean isOverDashboardSettingsUi(int mouseX, int mouseY) {
        if (dashboardSettingsGearBounds != null && dashboardSettingsGearBounds.contains(mouseX, mouseY)) {
            return true;
        }
        if (!dashboardSettingsOpen) {
            return false;
        }
        if (dashboardSettingsPanelBounds != null && dashboardSettingsPanelBounds.contains(mouseX, mouseY)) {
            return true;
        }
        if (dashboardSettingsSubmenuBounds != null && dashboardSettingsSubmenuBounds.contains(mouseX, mouseY)) {
            return true;
        }
        return isOverDashboardSettingsSubmenuBridge(mouseX, mouseY);
    }

    private boolean isOverDashboardSettingsSubmenuBridge(int mouseX, int mouseY) {
        if (dashboardSettingsPanelBounds == null || dashboardSettingsSubmenuBounds == null) {
            return false;
        }
        int left = Math.min(dashboardSettingsPanelBounds.right(), dashboardSettingsSubmenuBounds.right());
        int right = Math.max(dashboardSettingsPanelBounds.x, dashboardSettingsSubmenuBounds.x);
        if (right <= left) {
            return false;
        }
        return mouseX >= left && mouseX < right
                && mouseY >= dashboardSettingsSubmenuBounds.y
                && mouseY < dashboardSettingsSubmenuBounds.bottom();
    }

    private void closeDashboardSettings() {
        dashboardSettingsOpen = false;
        dashboardSettingsAnimating = false;
        dashboardSettingsPanelBounds = null;
        dashboardSettingsSubmenuBounds = null;
        dashboardSettingsHoverTool = null;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        mouseX = toModernMouseX(mouseX);
        mouseY = toModernMouseY(mouseY);
        if (toolsCapturedMouseButton == clickedMouseButton) {
            return;
        }
        if (!DetachedSwingWindowManager.isDetachedScreen(this)
                && clickedMouseButton == 0 && RunningStatusHud.isInteractionActive()) {
            RunningStatusHud.mouseDragged(mouseX, mouseY, this.width, this.height);
            return;
        }
        if (this.commandPaletteOpen) {
            if (clickedMouseButton == 0 && this.commandPaletteScrollbar.isDragging()) {
                this.commandPaletteScrollbar.applyDrag(mouseX, mouseY);
            }
            return;
        }
        if (this.hiddenCategoryPickerOpen) {
            if (clickedMouseButton == 0 && this.hiddenCategoryPickerScrollbar.isDragging()) {
                this.hiddenCategoryPickerScrollbar.applyDrag(mouseX, mouseY);
            }
            return;
        }
        if (this.inlineTextInputOpen) {
            return;
        }
        if (this.inlineConfirmationOpen) {
            return;
        }
        if (closeConfirmationOpen) {
            return;
        }
        boolean detachedScreen = DetachedSwingWindowManager.isDetachedScreen(this);
        if (!detachedScreen && clickedMouseButton == 0 && resizeHandle != ResizeHandle.NONE) {
            resizeWindow(mouseX, mouseY);
            return;
        }
        if (clickedMouseButton == 0 && draggingSidebar) {
            resizeSidebar(mouseX);
            return;
        }
        if (clickedMouseButton == 0 && tabScrollbar.isDragging()) {
            tabScrollbar.applyDrag(mouseX, mouseY);
            return;
        }
        if (clickedMouseButton == 0 && navigationScrollbar.isDragging()) {
            navigationScrollbar.applyDrag(mouseX, mouseY);
            return;
        }
        if (clickedMouseButton == 0 && dashboardScrollbar.isDragging()) {
            dashboardScrollbar.applyDrag(mouseX, mouseY);
            return;
        }
        if (!detachedScreen && clickedMouseButton == 0 && draggingWindow) {
            moveWindow(mouseX, mouseY);
            return;
        }
        if (clickedMouseButton == 0 && pathDragPending) {
            updatePathDrag(mouseX, mouseY);
            return;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null && settingsTab.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {
            closeReturnedSettingsTab(settingsTab);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        mouseX = toModernMouseX(mouseX);
        mouseY = toModernMouseY(mouseY);
        if (toolsCapturedMouseButton == state) {
            toolsCapturedMouseButton = -1;
            return;
        }
        if (!DetachedSwingWindowManager.isDetachedScreen(this)
                && state == 0 && RunningStatusHud.isInteractionActive()) {
            RunningStatusHud.mouseReleased();
            ModernTooltipSupport.setSuppressed(this, false);
            return;
        }
        if (this.commandPaletteOpen) {
            if (state == 0) {
                this.commandPaletteDraggingScrollbar = false;
                this.commandPaletteScrollbar.endDrag();
            }
            return;
        }
        if (this.hiddenCategoryPickerOpen) {
            if (state == 0) {
                this.hiddenCategoryPickerScrollbar.endDrag();
            }
            return;
        }
        if (this.inlineTextInputOpen) {
            return;
        }
        if (this.inlineConfirmationOpen) {
            return;
        }
        if (closeConfirmationOpen) {
            return;
        }
        if (state == 0 && pathDragPending) {
            finishPathDrag(mouseX, mouseY);
            return;
        }
        boolean wasResizing = state == 0 && resizeHandle != ResizeHandle.NONE;
        boolean wasMoving = state == 0 && draggingWindow;
        boolean wasDraggingSidebar = state == 0 && draggingSidebar;
        boolean wasDraggingTabScrollbar = state == 0 && (draggingTabScrollbar || tabScrollbar.isDragging());
        boolean wasDraggingNavigationScrollbar = state == 0
                && (draggingNavigationScrollbar || navigationScrollbar.isDragging());
        boolean wasDraggingDashboardScrollbar = state == 0 && dashboardScrollbar.isDragging();
        if (state == 0) {
            resizeHandle = ResizeHandle.NONE;
            draggingWindow = false;
            draggingSidebar = false;
            draggingTabScrollbar = false;
            draggingNavigationScrollbar = false;
            tabScrollbar.endDrag();
            navigationScrollbar.endDrag();
            dashboardScrollbar.endDrag();
        }
        if (wasResizing || wasMoving || wasDraggingSidebar || wasDraggingTabScrollbar
                || wasDraggingNavigationScrollbar || wasDraggingDashboardScrollbar) {
            if (wasDraggingSidebar) {
                MainUiLayoutManager.setModernSidebarWidth(sidebarWidth);
            }
            persistWindowLayout();
            ModernTooltipSupport.setSuppressed(this, false);
            return;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null && settingsTab.mouseReleased(mouseX, mouseY, state)) {
            closeReturnedSettingsTab(settingsTab);
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private boolean handleCommandPaletteClick(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.commandPaletteBounds == null || !this.commandPaletteBounds.contains(mouseX, mouseY)) {
            closeCommandPalette();
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (this.commandPaletteClearBounds != null && this.commandPaletteClearBounds.contains(mouseX, mouseY)) {
            this.commandPaletteSearchField.setText("");
            this.commandPaletteSearchField.setFocused(true);
            this.commandPaletteSelectedIndex = 0;
            this.commandPaletteScrollOffset = 0;
            refreshCommandPaletteEntries();
            return true;
        }
        if (this.commandPaletteSearchBounds != null && this.commandPaletteSearchBounds.contains(mouseX, mouseY)) {
            this.commandPaletteSearchField.setFocused(true);
            this.commandPaletteSearchField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (this.commandPaletteListBounds == null || !this.commandPaletteListBounds.contains(mouseX, mouseY)) {
            return true;
        }
        if (this.commandPaletteScrollbar.beginDrag(mouseX, mouseY)) {
            this.commandPaletteDraggingScrollbar = true;
            return true;
        }
        for (int visibleIndex = 0; visibleIndex < this.commandPaletteRowBounds.size(); visibleIndex++) {
            ModernMainLayout.Rect row = this.commandPaletteRowBounds.get(visibleIndex);
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            int entryIndex = this.commandPaletteScrollOffset + visibleIndex;
            if (entryIndex < 0 || entryIndex >= this.commandPaletteEntries.size()) {
                return true;
            }
            this.commandPaletteSelectedIndex = entryIndex;
            if (this.commandPaletteSettingsBounds.get(visibleIndex).contains(mouseX, mouseY)) {
                openKeybindSettingsForEntry(this.commandPaletteEntries.get(entryIndex));
            } else {
                executeCommandPaletteEntry(this.commandPaletteEntries.get(entryIndex));
            }
            return true;
        }
        return true;
    }

    private boolean handleCommandPaletteKeyTyped(char typedChar, int keyCode) throws IOException {
        if (isCommandPaletteShortcutKey(keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeCommandPalette();
            return true;
        }
        if (keyCode == Keyboard.KEY_UP) {
            moveCommandPaletteSelection(-1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            moveCommandPaletteSelection(1);
            return true;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            moveCommandPaletteSelection(-getCommandPaletteVisibleRows());
            return true;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            moveCommandPaletteSelection(getCommandPaletteVisibleRows());
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            this.commandPaletteSelectedIndex = 0;
            this.commandPaletteScrollOffset = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            if (!this.commandPaletteEntries.isEmpty()) {
                this.commandPaletteSelectedIndex = this.commandPaletteEntries.size() - 1;
                ensureCommandPaletteSelectionVisible(getCommandPaletteVisibleRows());
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!this.commandPaletteEntries.isEmpty()) {
                executeCommandPaletteEntry(this.commandPaletteEntries.get(this.commandPaletteSelectedIndex));
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_F && isControlDown()) {
            this.commandPaletteSearchField.setFocused(true);
            return true;
        }
        if (this.commandPaletteSearchField != null) {
            this.commandPaletteSelectedIndex = 0;
            this.commandPaletteScrollOffset = 0;
            this.commandPaletteSearchField.textboxKeyTyped(typedChar, keyCode);
            refreshCommandPaletteEntries();
        }
        return true;
    }

    private boolean isCommandPaletteShortcutKey(int keyCode) {
        Set<Integer> modifiers = new HashSet<>();
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return KeybindManager.matchesKeybind(BindableAction.OPEN_COMMAND_PALETTE, keyCode, modifiers);
    }

    private void moveCommandPaletteSelection(int delta) {
        if (this.commandPaletteEntries.isEmpty()) {
            return;
        }
        this.commandPaletteSelectedIndex = clamp(this.commandPaletteSelectedIndex + delta, 0,
                this.commandPaletteEntries.size() - 1);
        ensureCommandPaletteSelectionVisible(getCommandPaletteVisibleRows());
    }

    private int getCommandPaletteVisibleRows() {
        return this.commandPaletteListBounds == null ? 1
                : Math.max(1, this.commandPaletteListBounds.height / COMMAND_PALETTE_ROW_HEIGHT);
    }

    private void executeCommandPaletteEntry(CommandPaletteEntry entry) throws IOException {
        if (entry == null) {
            return;
        }
        recentPaletteCommands.remove(entry.id);
        recentPaletteCommands.addFirst(entry.id);
        while (recentPaletteCommands.size() > 12) {
            recentPaletteCommands.removeLast();
        }
        closeCommandPalette();
        if (entry.action != null && entry.menuCommand == null) {
            KeybindManager.executeAction(entry.action);
            return;
        }
        if ("path_manager".equals(entry.menuCommand)) {
            openSettingsTabForCommand("path_manager");
            return;
        }
        if (entry.utilityAction != null) {
            if ("reset_window".equals(entry.utilityAction)) {
                resetWindowLayout();
            } else {
                GuiModernMainActions.handleUtilityAction(entry.utilityAction, this.mc);
            }
            return;
        }
        if (entry.menuCommand != null) {
            GuiModernMainActions.handleCommand(entry.menuCommand, 0, this.mc);
        }
    }

    private void openKeybindSettingsForEntry(CommandPaletteEntry entry) {
        closeCommandPalette();
        if (entry == null || entry.keybindTarget == null || entry.keybindTarget.isEmpty()) {
            openSettingsTabForCommand("keybind_manager");
            return;
        }
        openSettingsTabForCommand("keybind_manager");
        ModernSettingsTab keybindTab = this.settingsTabs.get(tabKeyForCommand("keybind_manager"));
        if (keybindTab != null) {
            keybindTab.focusCommand(entry.keybindTarget);
        }
    }

    private void handleTarget(HitTarget target, int mouseX, int mouseY, int mouseButton) throws IOException {
        switch (target.type) {
            case CATEGORY:
                handleCategoryTarget(target.categoryRow, mouseX, mouseY, mouseButton);
                return;
            case CATEGORY_EXPAND:
                if (mouseButton == 0 && target.value != null) {
                    MainUiLayoutManager.toggleCollapsed(target.value);
                    GuiInventory.refreshGuiLists();
                    navigationScrollOffset = 0;
                }
                return;
            case OTHER_FEATURE_HUD_POSITION:
                if (mouseButton == 0) {
                    GuiOtherFeaturesHudPosition.open(mc);
                }
                return;
            case OTHER_FEATURE_GROUP:
                if (mouseButton == 0) {
                    otherFeatureGroupsExpanded = !otherFeatureGroupsExpanded;
                    navigationScrollOffset = 0;
                }
                return;
            case NAVIGATION_TOGGLE:
                if (mouseButton == 0) {
                    sidebarCollapsed = !sidebarCollapsed;
                    navigationScrollOffset = 0;
                }
                return;
            case AUTO_FOCUS:
                if (mouseButton == 0) {
                    MainUiLayoutManager.setModernAutoFocus(!MainUiLayoutManager.isModernAutoFocus());
                }
                return;
            case AUTO_PAUSE:
                if (mouseButton == 0) {
                    MainUiLayoutManager.setModernAutoPauseOnMenuOpen(
                            !MainUiLayoutManager.isModernAutoPauseOnMenuOpen());
                }
                return;
            case RUNNING_STATUS_TOGGLE:
                if (mouseButton == 0) {
                    MainUiLayoutManager.setModernRunningStatusVisible(
                            !MainUiLayoutManager.isModernRunningStatusVisible());
                }
                return;
            case DASHBOARD_SETTINGS:
                if (mouseButton == 0) {
                    if (dashboardSettingsOpen) {
                        closeDashboardSettings();
                    } else if (!dashboardSettingsAnimating) {
                        dashboardSettingsAnimating = true;
                        dashboardSettingsAnimationStartedAt = System.currentTimeMillis();
                    }
                }
                return;
            case GLOBAL_SETTINGS:
                if (mouseButton == 0) {
                    openSettingsTabForCommand("general_settings");
                }
                return;
            case DASHBOARD_TOOL:
                handleDashboardTool(target.value, mouseButton);
                return;
            case DASHBOARD_TOOL_OPTION:
                handleDashboardToolOption(target.value, mouseButton);
                return;
            case DASHBOARD_GROUP:
                if (mouseButton == 0) {
                    MainUiLayoutManager.toggleModernDashboardGroupCollapsed(getDashboardViewKey(), target.value);
                    resetDashboardPosition();
                }
                return;
            case DASHBOARD_PAGE:
                if (mouseButton == 0) {
                    dashboardPage = clamp(dashboardPage + ("previous".equals(target.value) ? -1 : 1),
                            0, Math.max(0, dashboardPageCount - 1));
                }
                return;
            case COMMAND:
                if (mouseButton == 1 && openCommandContext(target.value, mouseX, mouseY)) {
                    return;
                }
                if (mouseButton == 0 && isPathSequenceRunning(target.value)
                        && openRunningSequenceContext(target.value, mouseX, mouseY)) {
                    return;
                }
                if (GuiModernMainActions.handleCommand(target.value, mouseButton, this.mc)) {
                    cardScrollOffset = 0;
                }
                return;
            case UTILITY:
                if (mouseButton == 0) {
                    toolsOpen = false;
                    if ("reset_window".equals(target.value)) {
                        resetWindowLayout();
                    } else {
                        GuiModernMainActions.handleUtilityAction(target.value, this.mc);
                    }
                }
                return;
            case DETACH:
                if (mouseButton == 0) {
                    if (DetachedSwingWindowManager.isDetachedScreen(this)) {
                        DetachedSwingWindowManager.INSTANCE.dock();
                    } else {
                        DetachedSwingWindowManager.INSTANCE.detach(this);
                    }
                }
                return;
            case CLOSE:
                if (mouseButton == 0) {
                    closeScreen();
                }
                return;
            case STOP_FOREGROUND:
                if (mouseButton == 0) {
                    GuiModernMainActions.handleCommand("stop_foreground", 0, this.mc);
                }
                return;
            case STOP_BACKGROUND:
                if (mouseButton == 0) {
                    GuiModernMainActions.handleCommand("stop_background", 0, this.mc);
                }
                return;
            case PATHS:
                if (mouseButton == 0) {
                    openSettingsTabForCommand("path_manager");
                }
                return;
            case COMMAND_PALETTE:
                if (mouseButton == 0) {
                    toggleCommandPalette();
                }
                return;
            case TOOLS:
                if (mouseButton == 0) {
                    toolsOpen = !toolsOpen;
                }
                return;
            case CLEAR_SEARCH:
                if (mouseButton == 0 && searchField != null) {
                    searchField.setText("");
                    searchField.setFocused(true);
                    focusDashboardForSearch();
                }
                return;
            case TAB_SELECT:
                if (mouseButton == 0) {
                    activateTab(TabKey.decode(target.value));
                    toolsOpen = false;
                    ensureActiveTabVisible();
                    if (!activeTab.isDashboard() && searchField != null) {
                        searchField.setFocused(false);
                    }
                }
                return;
            case TAB_CLOSE:
                if (mouseButton == 0) {
                    requestCloseTab(TabKey.decode(target.value));
                }
                return;
            case INFO:
                return;
            default:
                return;
        }
    }

    private void handleCategoryTarget(GuiInventoryBase.CategoryTreeRow row, int mouseX, int mouseY, int mouseButton) {
        if (row == null) {
            return;
        }
        if (mouseButton == 0) {
            selectCategory(row.category, row.subCategory);
            return;
        }
        if (mouseButton != 1 || (!row.isSubCategory() && row.systemCategory)) {
            return;
        }
        if (row.isSubCategory()) {
            openModernContextMenu(mouseX, mouseY,
                    GuiInventory.buildSubCategoryContextMenu(row.category, row.subCategory));
        } else {
            openModernContextMenu(mouseX, mouseY, GuiInventory.buildCategoryContextMenu(row.category));
        }
    }

    private void handleDashboardTool(String tool, int mouseButton) {
        if (tool == null || (mouseButton != 0 && mouseButton != 1)) {
            return;
        }
        String viewKey = getDashboardViewKey();
        if ("scope_global".equals(tool)) {
            MainUiLayoutManager.setModernDashboardUsingGlobal(viewKey, true);
            resetDashboardPosition();
            return;
        }
        if ("scope_independent".equals(tool)) {
            MainUiLayoutManager.setModernDashboardUsingGlobal(viewKey, false);
            resetDashboardPosition();
            return;
        }

        MainUiLayoutManager.DashboardViewMeta view = getDashboardView();
        int direction = mouseButton == 1 ? -1 : 1;
        if ("sort".equals(tool)) {
            String[] values = { MainUiLayoutManager.SORT_DEFAULT, MainUiLayoutManager.SORT_ACTIVE_FIRST,
                    MainUiLayoutManager.SORT_ALPHABETICAL };
            MainUiLayoutManager.setModernDashboardSortMode(viewKey,
                    cycleValue(values, view.sortMode, direction));
        } else if ("grouping".equals(tool)) {
            MainUiLayoutManager.setModernDashboardGrouping(viewKey, !view.grouping);
        } else if ("size".equals(tool)) {
            String[] values = { MainUiLayoutManager.ICON_SMALL, MainUiLayoutManager.ICON_MEDIUM,
                    MainUiLayoutManager.ICON_LARGE };
            MainUiLayoutManager.setModernDashboardIconSize(viewKey,
                    cycleValue(values, view.iconSize, direction));
        } else if ("columns".equals(tool)) {
            int[] values = { 0, 2, 3, 4, 5, 6, 7, 8 };
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == view.columns) {
                    index = i;
                    break;
                }
            }
            index = (index + direction + values.length) % values.length;
            MainUiLayoutManager.setModernDashboardColumns(viewKey, values[index]);
        } else if ("flow".equals(tool)) {
            MainUiLayoutManager.setModernDashboardFlowMode(viewKey,
                    MainUiLayoutManager.FLOW_PAGED.equals(view.flowMode)
                            ? MainUiLayoutManager.FLOW_WATERFALL : MainUiLayoutManager.FLOW_PAGED);
        }
        resetDashboardPosition();
    }

    private void handleDashboardToolOption(String encoded, int mouseButton) {
        if (encoded == null || (mouseButton != 0 && mouseButton != 1)) {
            return;
        }
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator >= encoded.length() - 1) {
            return;
        }
        String tool = encoded.substring(0, separator);
        String optionId = encoded.substring(separator + 1);
        String viewKey = getDashboardViewKey();
        if ("sort".equals(tool)) {
            MainUiLayoutManager.setModernDashboardSortMode(viewKey, optionId);
        } else if ("grouping".equals(tool)) {
            MainUiLayoutManager.setModernDashboardGrouping(viewKey, "on".equals(optionId));
        } else if ("size".equals(tool)) {
            MainUiLayoutManager.setModernDashboardIconSize(viewKey, optionId);
        } else if ("columns".equals(tool)) {
            try {
                MainUiLayoutManager.setModernDashboardColumns(viewKey, Integer.parseInt(optionId));
            } catch (NumberFormatException ignored) {
                return;
            }
        } else if ("flow".equals(tool)) {
            MainUiLayoutManager.setModernDashboardFlowMode(viewKey, optionId);
        } else {
            return;
        }
        resetDashboardPosition();
    }

    private String cycleValue(String[] values, String current, int direction) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
                break;
            }
        }
        return values[(index + direction + values.length) % values.length];
    }

    private void resetDashboardPosition() {
        cardScrollOffset = 0;
        dashboardPage = 0;
    }

    private void openModernContextMenu(int mouseX, int mouseY,
            List<GuiInventoryBase.ContextMenuItem> items) {
        contextMenuAnchorX = mouseX;
        contextMenuAnchorY = mouseY;
        modernContextMenu.open(mouseX, mouseY, items);
    }

    private boolean openCommandContext(String command, int mouseX, int mouseY) {
        if ("followconfig".equals(command)) {
            openSettingsTabForCommand(command);
            return true;
        }
        if ("conditional_execution".equals(command)) {
            openSettingsTabForCommand(command);
            return true;
        }
        if ("auto_escape".equals(command)) {
            openSettingsTabForCommand(command);
            return true;
        }
        if (tabKeyForCommand(command) != null) {
            openSettingsTabForCommand(command);
            return true;
        }
        if (command != null && (command.startsWith("custom_path:") || command.startsWith("path:"))) {
            String sequenceName = command.substring(command.indexOf(':') + 1).trim();
            PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
            if (sequence == null) {
                return false;
            }
            List<GuiInventoryBase.ContextMenuItem> items = new ArrayList<>();
            if (isPathSequenceRunning(command)) {
                appendRunningSequenceActions(items, command, sequence);
            } else {
                items.add(GuiInventory.buildSequenceJumpExecutionMenu(sequence));
            }
            items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.main.u070", () -> openPathSequenceEditor(command)));
            appendSequenceManagementActions(items, command, sequence);
            openModernContextMenu(mouseX, mouseY, items);
            return true;
        }
        return false;
    }

    private boolean openRunningSequenceContext(String command, int mouseX, int mouseY) {
        String sequenceName = getPathSequenceCommandName(command);
        PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
        if (sequence == null || !isPathSequenceRunning(command)) {
            return false;
        }
        List<GuiInventoryBase.ContextMenuItem> items = new ArrayList<>();
        appendRunningSequenceActions(items, command, sequence);
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.main.u070", () -> openPathSequenceEditor(command)));
        appendSequenceManagementActions(items, command, sequence);
        openModernContextMenu(mouseX, mouseY, items);
        return true;
    }

    private void appendSequenceManagementActions(List<GuiInventoryBase.ContextMenuItem> items, String command,
            PathSequence sequence) {
        String name = sequence.getName();
        boolean editable = sequence.isCustom() && !isPathSequenceRunning(command);
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.path.wb.ctx.rename",
                () -> promptDashboardSequenceRename(command, name)).enabled(editable));
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.main.sequence.copy", () -> {
            if (!canChangeDashboardSequence(command)) return;
            PathSequence current = PathSequenceManager.getSequence(name);
            if (current != null) {
                PathSequenceManager.copyCustomSequenceTo(name, current.getCategory(), current.getSubCategory());
                refreshDashboardSequences();
            }
        }).enabled(sequence.isCustom()));
        items.add(new GuiInventoryBase.ContextMenuItem(MainUiLayoutManager.isSequenceFavorite(name)
                ? "gui.modern.inv.u074" : "gui.modern.inv.u075", () -> {
            MainUiLayoutManager.toggleSequenceFavorite(name);
            GuiInventory.refreshGuiLists();
        }));
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.path.wb.u347", () -> {
            if (!canChangeDashboardSequence(command)) return;
            openInlineConfirmation("gui.modern.inv.u076", name + "\n" + tr("gui.modern.inv.u077", "删除后无法撤销"), () -> {
                if (!canChangeDashboardSequence(command) || isPathSequenceRunning(command)) return;
                PathSequence current = PathSequenceManager.getSequence(name);
                if (current == null || !current.isCustom()) return;
                PathSequenceManager.deleteCustomSequence(name);
                KeybindManager.pathSequenceKeybinds.remove(name);
                KeybindManager.saveConfig();
                refreshDashboardSequences();
            });
        }).enabled(editable));
    }

    private boolean canChangeDashboardSequence(String command) {
        for (ModernSettingsTab tab : settingsTabs.values()) {
            if (tab instanceof ModernPathWorkbenchTab && tab.isDirty()) {
                openInlineConfirmation("gui.modern.main.sequence.pending_title", tr("gui.modern.main.sequence.pending", "路径管理有未保存的修改，请先保存或撤销，再操作控制中心序列。"),
                        () -> openPathSequenceEditor(command));
                return false;
            }
        }
        return true;
    }

    private void promptDashboardSequenceRename(String command, String oldName) {
        if (!canChangeDashboardSequence(command)) return;
        openInlineTextInput("gui.modern.path.wb.u345", oldName, value -> {
            if (!canChangeDashboardSequence(command) || isPathSequenceRunning(command)) return;
            String name = value == null ? "" : value.trim();
            if (!PathSequenceManager.renameCustomSequence(oldName, name)) {
                openInlineConfirmation("gui.modern.path.wb.u345", tr("gui.modern.main.sequence.invalid_name", "名称不能为空、不能与已有序列重复，且序列必须仍然存在。"),
                        () -> promptDashboardSequenceRename(command, oldName));
                return;
            }
            if (!oldName.equals(name) && KeybindManager.pathSequenceKeybinds.containsKey(oldName)) {
                KeybindManager.pathSequenceKeybinds.put(name, KeybindManager.pathSequenceKeybinds.remove(oldName));
                KeybindManager.saveConfig();
            }
            refreshDashboardSequences();
        });
    }

    private void refreshDashboardSequences() {
        GuiInventory.refreshGuiLists();
        for (ModernSettingsTab tab : settingsTabs.values()) {
            if (tab instanceof ModernPathWorkbenchTab) {
                ((ModernPathWorkbenchTab) tab).refreshAfterExternalSequenceChange();
            }
        }
        refreshCommandPaletteEntries();
    }

    private void appendRunningSequenceActions(List<GuiInventoryBase.ContextMenuItem> items, String command,
            PathSequence sequence) {
        boolean foreground = isPathSequenceRunningForeground(command);
        boolean background = isPathSequenceRunningBackground(command);
        String status = foreground && background ? "gui.modern.main.u071" : background ? "gui.modern.main.u072" : "gui.modern.main.u073";
        items.add(new GuiInventoryBase.ContextMenuItem(status, null)
                .enabled(false));
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.main.u074", () -> stopRunningSequence(foreground, background)));
        items.add(new GuiInventoryBase.ContextMenuItem("gui.modern.main.u075", () -> {
            stopRunningSequence(foreground, background);
            PathSequenceManager.runPathSequence(sequence.getName());
        }));
    }

    private void stopRunningSequence(boolean foreground, boolean background) {
        if (background) {
            PathSequenceEventListener.stopAllBackgroundRunners();
        }
        if (foreground) {
            PathSequenceEventListener.stopForegroundSequenceByAction();
        }
    }

    void openPathSequenceEditor(String command) {
        openSettingsTabForCommand("path_manager");
        ModernSettingsTab pathWorkbench = settingsTabs.get(tabKeyForCommand("path_manager"));
        if (pathWorkbench != null) {
            pathWorkbench.focusCommand(command);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || lastLayout == null) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        mouseX = toModernMouseX(mouseX);
        mouseY = toModernMouseY(mouseY);
        handleDetachedMouseWheel(wheel, mouseX, mouseY);
    }

    /** Receives a wheel event from the Swing window in GuiScreen coordinates. */
    public void handleDetachedMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0 || lastLayout == null) {
            return;
        }
        if (this.commandPaletteOpen) {
            if (this.commandPaletteListBounds != null && this.commandPaletteListBounds.contains(mouseX, mouseY)
                    && this.commandPaletteMaxScrollOffset > 0) {
                this.commandPaletteScrollOffset = clamp(this.commandPaletteScrollOffset + (wheel > 0 ? -3 : 3),
                        0, this.commandPaletteMaxScrollOffset);
                this.commandPaletteSelectedIndex = clamp(this.commandPaletteScrollOffset, 0,
                        Math.max(0, this.commandPaletteEntries.size() - 1));
            }
            return;
        }
        if (this.hiddenCategoryPickerOpen && this.hiddenCategoryPickerListBounds != null
                && this.hiddenCategoryPickerListBounds.contains(mouseX, mouseY)
                && this.hiddenCategoryPickerMaxScrollOffset > 0) {
            this.hiddenCategoryPickerScrollOffset = clamp(
                    this.hiddenCategoryPickerScrollOffset + (wheel > 0 ? -1 : 1), 0,
                    this.hiddenCategoryPickerMaxScrollOffset);
            return;
        }
        if (this.inlineTextInputOpen || this.hiddenCategoryPickerOpen) {
            return;
        }
        if (closeConfirmationOpen) {
            return;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null && settingsTab.containsContent(mouseX, mouseY)
                && settingsTab.handleMouseWheel(wheel, mouseX, mouseY)) {
            closeReturnedSettingsTab(settingsTab);
            return;
        }
        if (tabStripBounds != null && tabStripBounds.contains(mouseX, mouseY) && maxTabScrollOffset > 0) {
            int amount = wheel > 0 ? -46 : 46;
            tabScrollOffset = clamp(tabScrollOffset + amount, 0, maxTabScrollOffset);
            return;
        }
        int amount = wheel > 0 ? -38 : 38;
        if (lastLayout.sidebar.contains(mouseX, mouseY)) {
            navigationScrollOffset = clamp(navigationScrollOffset + amount, 0, maxNavigationScrollOffset);
        } else if (contentClipBounds != null && contentClipBounds.contains(mouseX, mouseY)) {
            if (MainUiLayoutManager.FLOW_PAGED.equals(getDashboardView().flowMode)) {
                dashboardPage = clamp(dashboardPage + (wheel > 0 ? -1 : 1), 0,
                        Math.max(0, dashboardPageCount - 1));
            } else {
                cardScrollOffset = clamp(cardScrollOffset + amount, 0, maxCardScrollOffset);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == zszlScriptMod.getGuiToggleKeyCode() && !isTextInputFocused()
                && !KeybindManager.isRecording()) {
            closeScreen();
            return;
        }
        if (this.commandPaletteOpen && handleCommandPaletteKeyTyped(typedChar, keyCode)) {
            return;
        }
        if (this.inlineTextInputOpen && handleInlineTextInputKey(typedChar, keyCode)) {
            return;
        }
        if (this.inlineConfirmationOpen && handleInlineConfirmationKey(keyCode)) {
            return;
        }
        if (this.hiddenCategoryPickerOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeHiddenCategoryPicker();
            } else if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
                hiddenCategoryPickerScrollOffset = clamp(hiddenCategoryPickerScrollOffset
                        + (keyCode == Keyboard.KEY_UP ? -1 : 1), 0, hiddenCategoryPickerMaxScrollOffset);
            }
            return;
        }
        if (closeConfirmationOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeCloseConfirmation();
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                confirmCloseConfirmation();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (cancelActiveGesture()) {
                return;
            }
            ModernSettingsTab settingsTab = getActiveSettingsTab();
            if (settingsTab != null && settingsTab.handleEscape()) {
                closeReturnedSettingsTab(settingsTab);
                return;
            }
            if (modernContextMenu.isOpen()) {
                modernContextMenu.close();
                return;
            }
            if (toolsOpen) {
                toolsOpen = false;
                return;
            }
            if (dashboardSettingsOpen || dashboardSettingsAnimating) {
                closeDashboardSettings();
                return;
            }
            if (!activeTab.isDashboard()) {
                if (DetachedSwingWindowManager.isDetachedScreen(this)) {
                    activateTab(TabKey.dashboard());
                    ensureActiveTabVisible();
                    return;
                }
                // Escape closes the control center, not the tab. Only the tab's
                // explicit x button is allowed to discard a draft.
                closeScreen();
                return;
            }
            if (DetachedSwingWindowManager.isDetachedScreen(this)) {
                return;
            }
            closeScreen();
            return;
        }
        ModernSettingsTab settingsTab = getActiveSettingsTab();
        if (settingsTab != null && settingsTab.keyTyped(typedChar, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_F && isControlDown()) {
            if (searchField != null) {
                searchField.setFocused(true);
                focusDashboardForSearch();
            }
            return;
        }
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            focusDashboardForSearch();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void closeReturnedSettingsTab(ModernSettingsTab settingsTab) {
        if (settingsTab != null && settingsTab == getActiveSettingsTab() && settingsTab.consumeReturnRequest()) {
            suspendTabWithoutPrompt(activeTab);
        }
    }

    @Override
    public void onGuiClosed() {
        if (DetachedSwingWindowManager.isDetachedScreen(this)) {
            return;
        }
        DetachedSwingWindowManager.INSTANCE.onScreenClosed(this);
        closed = true;
        clearPathDrag();
        closeCommandPalette();
        closeInlineTextInput();
        closeHiddenCategoryPicker();
        if (windowInitialized) {
            persistWindowLayout();
        }
        Keyboard.enableRepeatEvents(false);
        // Keep the persistent tab instances and their drafts alive. Drafts are
        // discarded only after the user explicitly closes a tab with its x.
        zszlScriptMod.isGuiVisible = false;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void openSettingsTabForCommand(String command) {
        if ("action_editor".equals(command) || "expression_editor".equals(command)
                || "action_variables".equals(command)) {
            openPathEditorTab(command);
            return;
        }
        if (isPacketChildCommand(command)) {
            openPacketChildTab(command);
            return;
        }
        TabKey key = tabKeyForCommand(command);
        if (key == null) {
            return;
        }
        if (key.isOtherFeature()) {
            String normalizedCommand = ModernOtherFeatureSettingsTab.commandFor(
                    ModernOtherFeatureSettingsTab.featureIdFromCommand(command));
            if (!normalizedCommand.equals(activeOtherFeatureCommand)) {
                ModernSettingsTab previousTab = settingsTabs.remove(key);
                if (previousTab != null) {
                    settingsTabDraftCache.put(settingsTabCacheKey(key, activeOtherFeatureCommand), previousTab);
                }
                activeOtherFeatureCommand = normalizedCommand;
            }
        }
        addOpenTab(key);
        ModernSettingsTab tab = settingsTabs.get(key);
        if (tab == null) {
            String cacheCommand = key.isOtherFeature() ? activeOtherFeatureCommand : command;
            tab = settingsTabDraftCache.get(settingsTabCacheKey(key, cacheCommand));
            if (tab != null) {
                // A suspended draft is becoming the active tab again; it must
                // be reinstalled or the tab strip would show a title with no
                // content behind it.
                settingsTabs.put(key, tab);
            }
        }
        if (tab == null) {
            tab = createSettingsTab(key, command);
            if (tab == null) {
                return;
            }
            settingsTabs.put(key, tab);
        }
        tab.ensureInitialized(this.fontRenderer);
        activateTab(key);
        ensureActiveTabVisible();
        toolsOpen = false;
        if (searchField != null) {
            searchField.setFocused(false);
        }
    }

    private void openPathEditorTab(String command) {
        if ("expression_editor".equals(command)
                && !openTabs.contains(tabKeyForCommand("action_editor"))) {
            openSettingsTabForCommand("action_editor");
        }
        if (!openTabs.contains(tabKeyForCommand("path_manager"))) {
            openSettingsTabForCommand("path_manager");
        }
        TabKey pathKey = tabKeyForCommand("path_manager");
        ModernSettingsTab pathTab = settingsTabs.get(pathKey);
        if (!(pathTab instanceof ModernPathWorkbenchTab)) {
            return;
        }
        TabKey editorKey = tabKeyForCommand(command);
        if (editorKey == null) {
            return;
        }
        ModernPathWorkbenchTab workbench = (ModernPathWorkbenchTab) pathTab;
        workbench.focusCommand(command);
        settingsTabs.put(editorKey, workbench);
        addOpenTab(editorKey);
        activateTab(editorKey);
        ensureActiveTabVisible();
        toolsOpen = false;
        if (searchField != null) {
            searchField.setFocused(false);
        }
    }

    private void activateTab(TabKey key) {
        if (key == null) {
            return;
        }
        activeTab = key;
        ModernSettingsTab tab = settingsTabs.get(key);
        if (tab instanceof ModernPathWorkbenchTab || tab instanceof PacketWorkbenchTab) {
            String command = commandForTabKey(key);
            if (command != null) {
                tab.focusCommand(command);
            }
        }
    }

    private void addOpenTab(TabKey key) {
        if (key == null || openTabs.contains(key)) {
            return;
        }
        String parentCommand = tabParentCommand(key);
        if (parentCommand == null) {
            openTabs.add(key);
            return;
        }
        TabKey parent = tabKeyForCommand(parentCommand);
        int insertAt = openTabs.indexOf(parent);
        if (insertAt < 0) {
            openTabs.add(key);
            return;
        }
        insertAt++;
        while (insertAt < openTabs.size() && isDescendantOf(openTabs.get(insertAt), parent)) {
            insertAt++;
        }
        openTabs.add(insertAt, key);
    }

    private void closeTab(TabKey key) {
        if (key == null || key.isDashboard()) {
            return;
        }
        recordClosedTab(key);
        ModernSettingsTab settingsTab = settingsTabs.remove(key);
        if (settingsTab != null && !isTabInstanceOpenElsewhere(settingsTab, key)) {
            settingsTab.discardDraft();
        }
        if (!isSharedWorkbenchAlias(key)) {
            settingsTabDraftCache.remove(settingsTabCacheKey(key, commandForTabCache(key)));
        }
        if (key.isOtherFeature()) {
            settingsTabs.remove(key);
            activeOtherFeatureCommand = "";
        }
        int closingIndex = openTabs.indexOf(key);
        openTabs.remove(key);
        if (activeTab.equals(key)) {
            int fallbackIndex = Math.max(0, Math.min(closingIndex - 1, openTabs.size() - 1));
            activeTab = openTabs.isEmpty() ? TabKey.dashboard() : openTabs.get(fallbackIndex);
            activateTab(activeTab);
        }
        ensureActiveTabVisible();
        toolsOpen = false;
    }

    private void closeTabCascade(TabKey root) {
        List<TabKey> closing = new ArrayList<>();
        for (TabKey candidate : openTabs) {
            if (candidate.equals(root) || isDescendantOf(candidate, root)) {
                closing.add(candidate);
            }
        }
        for (int i = closing.size() - 1; i >= 0; i--) {
            closeTab(closing.get(i));
        }
    }

    private boolean isTabInstanceOpenElsewhere(ModernSettingsTab tab, TabKey excluded) {
        if (tab == null) {
            return false;
        }
        for (Map.Entry<TabKey, ModernSettingsTab> entry : settingsTabs.entrySet()) {
            if (!entry.getKey().equals(excluded) && entry.getValue() == tab
                    && openTabs.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPathEditorAlias(TabKey key) {
        String command = commandForTabKey(key);
        return "action_editor".equals(command) || "expression_editor".equals(command);
    }

    private boolean isPacketChildCommand(String command) {
        ModernTabDescriptor descriptor = command == null ? null : tabRegistry.find(command);
        return descriptor != null && "packet_handler".equals(descriptor.getParentCommand());
    }

    private boolean isSharedWorkbenchAlias(TabKey key) {
        if (isPathEditorAlias(key)) {
            return true;
        }
        return isPacketChildCommand(commandForTabKey(key));
    }

    private void openPacketChildTab(String command) {
        if (!openTabs.contains(tabKeyForCommand("packet_handler"))) {
            openSettingsTabForCommand("packet_handler");
        }
        TabKey parentKey = tabKeyForCommand("packet_handler");
        ModernSettingsTab parentTab = settingsTabs.get(parentKey);
        if (!(parentTab instanceof PacketWorkbenchTab)) {
            return;
        }
        TabKey childKey = tabKeyForCommand(command);
        if (childKey == null) {
            return;
        }
        PacketWorkbenchTab workbench = (PacketWorkbenchTab) parentTab;
        workbench.focusCommand(command);
        settingsTabs.put(childKey, workbench);
        addOpenTab(childKey);
        activateTab(childKey);
        ensureActiveTabVisible();
        toolsOpen = false;
        if (searchField != null) {
            searchField.setFocused(false);
        }
    }

    private void suspendTabWithoutPrompt(TabKey key) {
        if (key == null || key.isDashboard()) {
            return;
        }
        String command = commandForTabCache(key);
        ModernSettingsTab settingsTab = settingsTabs.remove(key);
        if (settingsTab != null) {
            settingsTabDraftCache.put(settingsTabCacheKey(key, command), settingsTab);
        }
        int closingIndex = openTabs.indexOf(key);
        openTabs.remove(key);
        if (key.isOtherFeature()) {
            activeOtherFeatureCommand = "";
        }
        if (activeTab.equals(key)) {
            int fallbackIndex = Math.max(0, Math.min(closingIndex - 1, openTabs.size() - 1));
            activeTab = openTabs.isEmpty() ? TabKey.dashboard() : openTabs.get(fallbackIndex);
        }
        ensureActiveTabVisible();
        toolsOpen = false;
    }

    private String settingsTabCacheKey(TabKey key, String command) {
        return (key == null ? "" : key.encode()) + "::" + (command == null ? "" : command);
    }

    /** Command snapshot used for draft-cache keys and closed-tab restore. */
    private String commandForTabCache(TabKey key) {
        if (key == null) {
            return "";
        }
        if (key.isOtherFeature()) {
            return activeOtherFeatureCommand;
        }
        return key.command;
    }

    public void closeActiveTab() {
        if (activeTab != null && !activeTab.isDashboard()) {
            requestCloseTab(activeTab);
        }
    }

    public void restoreClosedTab() {
        ClosedTab closedTab = closedTabs.pollFirst();
        if (closedTab == null) {
            return;
        }
        String command = closedTab.command;
        if (command == null || command.isEmpty()) {
            command = commandForTabKey(closedTab.key);
        }
        if (command != null && !command.isEmpty()) {
            openSettingsTabForCommand(command);
        }
    }

    private void recordClosedTab(TabKey key) {
        String command = commandForTabCache(key);
        if (command == null || command.isEmpty()) {
            command = commandForTabKey(key);
        }
        final String commandSnapshot = command == null ? "" : command;
        closedTabs.removeIf(entry -> entry.key != null && entry.key.equals(key) && entry.command.equals(commandSnapshot));
        closedTabs.addFirst(new ClosedTab(key, commandSnapshot));
        while (closedTabs.size() > 12) {
            closedTabs.removeLast();
        }
    }

    private String commandForTabKey(TabKey key) {
        if (key == null) {
            return null;
        }
        if (key.fixedId == null) {
            return key.command;
        }
        for (String command : tabRegistry.commands()) {
            if (key.equals(tabKeyForCommand(command))) {
                return command;
            }
        }
        return null;
    }

    private String tabParentCommand(TabKey key) {
        String command = commandForTabKey(key);
        ModernTabDescriptor descriptor = command == null ? null : tabRegistry.find(command);
        return descriptor == null ? null : descriptor.getParentCommand();
    }

    private TabKey dependencyRoot(TabKey key) {
        if (key == null) {
            return null;
        }
        Set<TabKey> visited = new HashSet<>();
        TabKey current = key;
        while (current != null && visited.add(current)) {
            String parentCommand = tabParentCommand(current);
            TabKey parent = parentCommand == null ? null : tabKeyForCommand(parentCommand);
            if (parent == null || !openTabs.contains(parent)) {
                return current;
            }
            current = parent;
        }
        return key;
    }

    private boolean isDescendantOf(TabKey candidate, TabKey ancestor) {
        if (candidate == null || ancestor == null || candidate.equals(ancestor)) {
            return false;
        }
        Set<TabKey> visited = new HashSet<>();
        TabKey current = candidate;
        while (current != null && visited.add(current)) {
            String parentCommand = tabParentCommand(current);
            TabKey parent = parentCommand == null ? null : tabKeyForCommand(parentCommand);
            if (parent == null) {
                return false;
            }
            if (parent.equals(ancestor)) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    private void selectCategory(String category, String subCategory) {
        if (category == null) {
            return;
        }
        String safeSubCategory = subCategory == null ? "" : subCategory;
        if (MainUiLayoutManager.isModernAutoFocus()) {
            activeTab = TabKey.dashboard();
            ensureActiveTabVisible();
            if (searchField != null) {
                searchField.setFocused(false);
            }
        }
        if (category.equals(GuiInventory.currentCategory) && safeSubCategory.equals(GuiInventory.currentCustomSubCategory)) {
            return;
        }
        GuiInventory.currentCategory = category;
        GuiInventory.currentCustomSubCategory = safeSubCategory;
        GuiInventory.currentPage = 0;
        GuiInventory.refreshGuiLists();
        resetDashboardPosition();
    }

    private void requestCloseTab(TabKey key) {
        if (key == null || key.isDashboard()) {
            return;
        }
        boolean cascade = hasDependentTab(key);
        if (hasDirtyTabInCascade(key)) {
            openCloseConfirmation(key, cascade);
            return;
        }
        if (cascade) {
            closeTabCascade(key);
        } else {
            closeTab(key);
        }
    }

    private boolean hasDirtyTabInCascade(TabKey root) {
        for (TabKey candidate : openTabs) {
            if (candidate.equals(root) || isDescendantOf(candidate, root)) {
                ModernSettingsTab tab = settingsTabs.get(candidate);
                if (tab != null && tab.isDirty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasDependentTab(TabKey root) {
        for (TabKey candidate : openTabs) {
            if (!candidate.equals(root) && isDescendantOf(candidate, root)) {
                return true;
            }
        }
        return false;
    }

    public void requestCloseScreen() {
        // Closing the control center is a temporary hide. Keep all tab drafts
        // in the persistent screen; only an explicit tab x may discard them.
        closeScreenNow();
    }

    private boolean hasDirtySettingsTab() {
        for (ModernSettingsTab tab : settingsTabs.values()) {
            if (tab != null && tab.isDirty()) {
                return true;
            }
        }
        return false;
    }

    private void openCloseConfirmation(TabKey key, boolean cascade) {
        closeConfirmationTab = key;
        closeConfirmationCascade = cascade;
        closeConfirmationOpen = true;
        commandPaletteOpen = false;
        closeInlineTextInput();
        closeHiddenCategoryPicker();
        modernContextMenu.close();
        toolsOpen = false;
    }

    private void closeCloseConfirmation() {
        closeConfirmationOpen = false;
        closeConfirmationTab = null;
        closeConfirmationCascade = false;
        pendingSettingsReplacementCommand = "";
        closeConfirmationBounds = null;
        closeConfirmationConfirmBounds = null;
        closeConfirmationCancelBounds = null;
    }

    private void confirmCloseConfirmation() {
        TabKey key = closeConfirmationTab;
        String replacementCommand = pendingSettingsReplacementCommand;
        boolean cascade = closeConfirmationCascade;
        closeCloseConfirmation();
        if (key == null) {
            closeScreenNow();
        } else if (cascade) {
            closeTabCascade(key);
        } else {
            closeTab(key);
            if (!replacementCommand.isEmpty()) {
                settingsTabs.remove(key);
                openSettingsTabForCommand(replacementCommand);
            }
        }
    }

    private boolean handleCloseConfirmationClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (closeConfirmationCancelBounds != null && closeConfirmationCancelBounds.contains(mouseX, mouseY)) {
            closeCloseConfirmation();
            return true;
        }
        if (closeConfirmationConfirmBounds != null && closeConfirmationConfirmBounds.contains(mouseX, mouseY)) {
            confirmCloseConfirmation();
            return true;
        }
        return true;
    }

    private void drawCloseConfirmation(int mouseX, int mouseY) {
        ModernMainLayout.Rect shell = lastLayout == null
                ? new ModernMainLayout.Rect(0, 0, Math.max(1, width), Math.max(1, height))
                : lastLayout.shell;
        int panelWidth = Math.min(380, Math.max(260, shell.width - 20));
        int panelHeight = Math.min(142, Math.max(120, shell.height - 20));
        int x = shell.x + Math.max(0, (shell.width - panelWidth) / 2);
        int y = shell.y + Math.max(0, (shell.height - panelHeight) / 2);
        closeConfirmationBounds = new ModernMainLayout.Rect(x, y, panelWidth, panelHeight);
        closeConfirmationConfirmBounds = new ModernMainLayout.Rect(x + 14, y + panelHeight - 31, 112, 22);
        closeConfirmationCancelBounds = new ModernMainLayout.Rect(x + panelWidth - 126, y + panelHeight - 31, 112, 22);

        ModernUiRenderer.drawBackdropOverlay(this.width, this.height, 0x82060B10);
        ModernUiRenderer.drawPanel(x, y, panelWidth, panelHeight, 7, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.WARNING);
        ModernUiRenderer.drawText(fontRenderer, closeConfirmationTab == null ? "gui.modern.main.u076" : "gui.modern.main.u077",
                x + 16, y + 12, ModernUiRenderer.TEXT, panelWidth - 32);
        String message = closeConfirmationTab == null
                ? "gui.modern.main.u078"
                : closeConfirmationCascade
                        ? "gui.modern.main.u079"
                        : "gui.modern.main.u080";
        ModernUiRenderer.drawText(fontRenderer, message, x + 16, y + 44, ModernUiRenderer.SUBTLE_TEXT,
                panelWidth - 32);
        drawCloseConfirmationButton(closeConfirmationConfirmBounds, "gui.modern.main.u081", true, mouseX, mouseY);
        drawCloseConfirmationButton(closeConfirmationCancelBounds, "gui.modern.main.u082", false, mouseX, mouseY);
    }

    private void drawCloseConfirmationButton(ModernMainLayout.Rect bounds, String label, boolean danger,
            int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = danger ? hovered ? 0xFFB94E3F : 0xFFD96A52
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = danger ? 0xFFFF8F7D : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                danger ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, bounds.width - 16);
    }

    private void closeScreen() {
        requestCloseScreen();
    }

    private void closeScreenNow() {
        if (DetachedSwingWindowManager.isDetachedScreen(this)) {
            DetachedSwingWindowManager.INSTANCE.closeDetachedMenu();
            return;
        }
        zszlScriptMod.isGuiVisible = false;
        this.mc.displayGuiScreen(null);
    }

    public void switchTabByOffset(int offset) {
        if (openTabs.isEmpty() || offset == 0) return;
        int index = openTabs.indexOf(activeTab);
        if (index < 0) index = 0;
        int next = (index + offset) % openTabs.size();
        if (next < 0) next += openTabs.size();
        activeTab = openTabs.get(next);
        ensureActiveTabVisible();
    }

    public void selectTabSlot(int slot) {
        if (slot <= 0 || slot > openTabs.size()) return;
        activeTab = openTabs.get(slot - 1);
        ensureActiveTabVisible();
    }

    public void saveActiveConfiguration() {
        ModernSettingsTab tab = getActiveSettingsTab();
        if (tab != null) {
            tab.save();
            closeReturnedSettingsTab(tab);
        }
        persistWindowLayout();
        MainUiLayoutManager.save();
        KeybindManager.saveConfig();
    }

    private void ensureWindowBounds() {
        int usableWidth = Math.max(1, this.width - WINDOW_MARGIN * 2);
        int usableHeight = Math.max(1, this.height - WINDOW_MARGIN * 2);
        int minimumWidth = getMinimumWindowWidth();
        int minimumHeight = getMinimumWindowHeight();
        if (!windowInitialized) {
            int defaultWidth = Math.max(minimumWidth, this.width - Math.max(24, this.width / 10));
            int defaultHeight = Math.max(minimumHeight, this.height - Math.max(24, this.height / 10));
            MainUiLayoutManager.ModernWindowLayout stored = MainUiLayoutManager.getModernWindowLayout();
            if (stored.isValid()) {
                windowWidth = clamp((int) Math.round(stored.widthRatio * this.width), minimumWidth, usableWidth);
                windowHeight = clamp((int) Math.round(stored.heightRatio * this.height), minimumHeight, usableHeight);
                windowX = (int) Math.round(stored.xRatio * this.width);
                windowY = (int) Math.round(stored.yRatio * this.height);
            } else {
                windowWidth = clamp(defaultWidth, minimumWidth, usableWidth);
                windowHeight = clamp(defaultHeight, minimumHeight, usableHeight);
                windowX = (this.width - windowWidth) / 2;
                windowY = (this.height - windowHeight) / 2;
            }
            windowInitialized = true;
        } else if (layoutScreenWidth > 0 && layoutScreenHeight > 0
                && (layoutScreenWidth != this.width || layoutScreenHeight != this.height)) {
            windowX = (int) Math.round(windowX / (double) layoutScreenWidth * this.width);
            windowY = (int) Math.round(windowY / (double) layoutScreenHeight * this.height);
            windowWidth = (int) Math.round(windowWidth / (double) layoutScreenWidth * this.width);
            windowHeight = (int) Math.round(windowHeight / (double) layoutScreenHeight * this.height);
        }
        windowWidth = clamp(windowWidth, minimumWidth, usableWidth);
        windowHeight = clamp(windowHeight, minimumHeight, usableHeight);
        windowX = clamp(windowX, WINDOW_MARGIN, Math.max(WINDOW_MARGIN, this.width - WINDOW_MARGIN - windowWidth));
        windowY = clamp(windowY, WINDOW_MARGIN,
                Math.max(WINDOW_MARGIN, this.height - WINDOW_MARGIN - windowHeight));
        layoutScreenWidth = this.width;
        layoutScreenHeight = this.height;
    }

    /**
     * Returns a defensive copy of the current modern window bounds so nested
     * workbenches can render as an in-place overlay over the same shell.
     */
    public java.awt.Rectangle getWindowBounds() {
        ensureWindowBounds();
        return new java.awt.Rectangle(windowX, windowY, windowWidth, windowHeight);
    }

    private boolean beginResize(int mouseX, int mouseY) {
        ResizeHandle handle = findResizeHandle(mouseX, mouseY);
        if (handle == ResizeHandle.NONE) {
            return false;
        }
        resizeHandle = handle;
        resizeStartMouseX = mouseX;
        resizeStartMouseY = mouseY;
        resizeStartWindowX = windowX;
        resizeStartWindowY = windowY;
        resizeStartWindowWidth = windowWidth;
        resizeStartWindowHeight = windowHeight;
        ModernTooltipSupport.setSuppressed(this, true);
        return true;
    }

    private void resizeWindow(int mouseX, int mouseY) {
        int deltaX = mouseX - resizeStartMouseX;
        int deltaY = mouseY - resizeStartMouseY;
        int minimumWidth = getMinimumWindowWidth();
        int minimumHeight = getMinimumWindowHeight();
        int maxRight = this.width - WINDOW_MARGIN;
        int maxBottom = this.height - WINDOW_MARGIN;
        int startRight = resizeStartWindowX + resizeStartWindowWidth;
        int startBottom = resizeStartWindowY + resizeStartWindowHeight;

        if (resizeHandle.left) {
            windowX = clamp(resizeStartWindowX + deltaX, WINDOW_MARGIN, startRight - minimumWidth);
            windowWidth = startRight - windowX;
        } else if (resizeHandle.right) {
            windowX = resizeStartWindowX;
            windowWidth = clamp(resizeStartWindowWidth + deltaX, minimumWidth, maxRight - windowX);
        }
        if (resizeHandle.top) {
            windowY = clamp(resizeStartWindowY + deltaY, WINDOW_MARGIN, startBottom - minimumHeight);
            windowHeight = startBottom - windowY;
        } else if (resizeHandle.bottom) {
            windowY = resizeStartWindowY;
            windowHeight = clamp(resizeStartWindowHeight + deltaY, minimumHeight, maxBottom - windowY);
        }
    }

    private boolean beginWindowDrag(int mouseX, int mouseY) {
        if (lastLayout == null || !lastLayout.header.contains(mouseX, mouseY)
                || searchBounds != null && searchBounds.contains(mouseX, mouseY)
                || searchField != null && searchField.isFocused()) {
            return false;
        }
        for (HitTarget target : hitTargets) {
            if (target.bounds != null && target.bounds.contains(mouseX, mouseY)) {
                return false;
            }
        }
        draggingWindow = true;
        windowDragStartMouseX = mouseX;
        windowDragStartMouseY = mouseY;
        windowDragStartX = windowX;
        windowDragStartY = windowY;
        toolsOpen = false;
        ModernTooltipSupport.setSuppressed(this, true);
        return true;
    }

    private void moveWindow(int mouseX, int mouseY) {
        int maxX = Math.max(WINDOW_MARGIN, this.width - WINDOW_MARGIN - windowWidth);
        int maxY = Math.max(WINDOW_MARGIN, this.height - WINDOW_MARGIN - windowHeight);
        windowX = clamp(windowDragStartX + mouseX - windowDragStartMouseX, WINDOW_MARGIN, maxX);
        windowY = clamp(windowDragStartY + mouseY - windowDragStartMouseY, WINDOW_MARGIN, maxY);
    }

    private boolean beginSidebarDrag(int mouseX, int mouseY) {
        if (sidebarCollapsed || navigationScrollbarBounds != null && navigationScrollbarBounds.contains(mouseX, mouseY)
                || sidebarDividerBounds == null || !sidebarDividerBounds.contains(mouseX, mouseY)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastSidebarDividerClickAt <= 350L && Math.abs(mouseX - lastSidebarDividerClickX) <= 4) {
            sidebarWidth = ModernMainLayout.DEFAULT_SIDEBAR_WIDTH;
            MainUiLayoutManager.setModernSidebarWidth(sidebarWidth);
            lastSidebarDividerClickAt = 0L;
            return true;
        }
        lastSidebarDividerClickAt = now;
        lastSidebarDividerClickX = mouseX;
        draggingSidebar = true;
        sidebarDragStartWidth = sidebarWidth;
        ModernTooltipSupport.setSuppressed(this, true);
        return true;
    }

    private void resizeSidebar(int mouseX) {
        if (lastLayout == null) {
            return;
        }
        int maxWidth = Math.min(ModernMainLayout.MAX_SIDEBAR_WIDTH,
                Math.max(1, lastLayout.shell.width - 96));
        int minWidth = Math.min(ModernMainLayout.MIN_SIDEBAR_WIDTH, maxWidth);
        sidebarWidth = clamp(mouseX - lastLayout.shell.x, minWidth, maxWidth);
    }

    private boolean cancelActiveGesture() {
        if (pathDragPending) {
            clearPathDrag();
        } else if (resizeHandle != ResizeHandle.NONE) {
            windowX = resizeStartWindowX;
            windowY = resizeStartWindowY;
            windowWidth = resizeStartWindowWidth;
            windowHeight = resizeStartWindowHeight;
            resizeHandle = ResizeHandle.NONE;
        } else if (draggingWindow) {
            windowX = windowDragStartX;
            windowY = windowDragStartY;
            draggingWindow = false;
        } else if (draggingSidebar) {
            sidebarWidth = sidebarDragStartWidth;
            draggingSidebar = false;
        } else if (draggingTabScrollbar || draggingNavigationScrollbar) {
            draggingTabScrollbar = false;
            draggingNavigationScrollbar = false;
        } else if (RunningStatusHud.isInteractionActive()) {
            RunningStatusHud.cancelInteraction();
        } else {
            return false;
        }
        ModernTooltipSupport.setSuppressed(this, false);
        return true;
    }

    public void cancelDetachedInteractions() {
        toolsCapturedMouseButton = -1;
        if (pathDragPending) {
            clearPathDrag();
        }
        resizeHandle = ResizeHandle.NONE;
        draggingWindow = false;
        RunningStatusHud.cancelInteraction();
        if (draggingSidebar) {
            MainUiLayoutManager.setModernSidebarWidth(sidebarWidth);
        }
        draggingSidebar = false;
        draggingTabScrollbar = false;
        draggingNavigationScrollbar = false;
        commandPaletteDraggingScrollbar = false;
        tabScrollbar.endDrag();
        navigationScrollbar.endDrag();
        dashboardScrollbar.endDrag();
        commandPaletteScrollbar.endDrag();
        hiddenCategoryPickerScrollbar.endDrag();
        ModernTooltipSupport.setSuppressed(this, false);
    }

    private boolean isGestureActive() {
        return pathDragPending || resizeHandle != ResizeHandle.NONE || draggingWindow || draggingSidebar
                || RunningStatusHud.isInteractionActive();
    }

    private void persistWindowLayout() {
        if (this.width <= 0 || this.height <= 0) {
            return;
        }
        int logicalWidth = toLogicalSize(this.width, MainUiLayoutManager.getModernUiScalePercent());
        int logicalHeight = toLogicalSize(this.height, MainUiLayoutManager.getModernUiScalePercent());
        MainUiLayoutManager.setModernWindowLayout(windowX / (double) logicalWidth,
                windowY / (double) logicalHeight, windowWidth / (double) logicalWidth,
                windowHeight / (double) logicalHeight);
    }

    public void resetWindowLayout() {
        MainUiLayoutManager.resetModernWindowLayout();
        sidebarWidth = ModernMainLayout.DEFAULT_SIDEBAR_WIDTH;
        MainUiLayoutManager.setModernSidebarWidth(sidebarWidth);
        sidebarCollapsed = false;
        windowInitialized = false;
        ensureWindowBounds();
    }

    private ResizeHandle findResizeHandle(int mouseX, int mouseY) {
        if (lastLayout == null) {
            return ResizeHandle.NONE;
        }
        ModernMainLayout.Rect shell = lastLayout.shell;
        boolean nearLeft = Math.abs(mouseX - shell.x) <= RESIZE_ZONE;
        boolean nearRight = Math.abs(mouseX - shell.right()) <= RESIZE_ZONE;
        boolean nearTop = Math.abs(mouseY - shell.y) <= RESIZE_ZONE;
        boolean nearBottom = Math.abs(mouseY - shell.bottom()) <= RESIZE_ZONE;
        boolean withinHorizontal = mouseX >= shell.x - RESIZE_ZONE && mouseX <= shell.right() + RESIZE_ZONE;
        boolean withinVertical = mouseY >= shell.y - RESIZE_ZONE && mouseY <= shell.bottom() + RESIZE_ZONE;
        if (nearLeft && nearTop) {
            return ResizeHandle.TOP_LEFT;
        }
        if (nearRight && nearTop) {
            return ResizeHandle.TOP_RIGHT;
        }
        if (nearLeft && nearBottom) {
            return ResizeHandle.BOTTOM_LEFT;
        }
        if (nearRight && nearBottom) {
            return ResizeHandle.BOTTOM_RIGHT;
        }
        if (nearLeft && withinVertical) {
            return ResizeHandle.LEFT;
        }
        if (nearRight && withinVertical) {
            return ResizeHandle.RIGHT;
        }
        if (nearTop && withinHorizontal) {
            return ResizeHandle.TOP;
        }
        if (nearBottom && withinHorizontal) {
            return ResizeHandle.BOTTOM;
        }
        return ResizeHandle.NONE;
    }

    private int getMinimumWindowWidth() {
        return Math.min(Math.max(1, this.width - WINDOW_MARGIN * 2), 480);
    }

    private int getMinimumWindowHeight() {
        return Math.min(Math.max(1, this.height - WINDOW_MARGIN * 2), 300);
    }

    private boolean isSelectedCategoryRow(GuiInventoryBase.CategoryTreeRow row) {
        if (row == null || !row.category.equals(GuiInventory.currentCategory)) {
            return false;
        }
        if (row.isSubCategory()) {
            return row.subCategory.equals(GuiInventory.currentCustomSubCategory);
        }
        return GuiInventory.currentCustomSubCategory == null || GuiInventory.currentCustomSubCategory.isEmpty();
    }

    private List<NavigationRow> buildNavigationRows() {
        List<NavigationRow> navigationRows = new ArrayList<>();
        boolean addedOtherFeatureGroup = false;
        for (GuiInventoryBase.CategoryTreeRow row : GuiInventory.buildVisibleCategoryTreeRows()) {
            if (isOtherFeatureNavigationChild(row)) {
                if (!addedOtherFeatureGroup) {
                    navigationRows.add(new NavigationRow(null, true));
                    addedOtherFeatureGroup = true;
                }
                if (otherFeatureGroupsExpanded) {
                    navigationRows.add(new NavigationRow(row, false));
                }
            } else {
                navigationRows.add(new NavigationRow(row, false));
            }
        }
        return navigationRows;
    }

    private boolean isOtherFeatureNavigationChild(GuiInventoryBase.CategoryTreeRow row) {
        return row != null && GuiInventory.otherFeatureCategories.contains(row.category);
    }

    private boolean isOtherFeatureGroupSelected() {
        return GuiInventory.otherFeatureCategories.contains(GuiInventory.currentCategory);
    }

    private String getNavigationRowLabel(NavigationRow navigationRow) {
        if (navigationRow == null || navigationRow.otherFeatureGroup) {
            return I18n.format("gui.inventory.other_features");
        }
        GuiInventoryBase.CategoryTreeRow row = navigationRow.categoryRow;
        if (row == null) {
            return "";
        }
        if (isOtherFeatureNavigationChild(row)) {
            String prefix = I18n.format("gui.inventory.other_features") + " · ";
            if (row.category.startsWith(prefix)) {
                return row.category.substring(prefix.length());
            }
        }
        return row.isSubCategory() ? row.subCategory : row.category;
    }

    private int getOtherFeatureItemCount() {
        int count = 0;
        for (String category : GuiInventory.otherFeatureCategories) {
            count += getCategoryItemCount(category);
        }
        return count;
    }

    private int getCategoryDotColor(String category) {
        int[] colors = { 0xFF5FD39A, 0xFF6AA9FF, 0xFFF0B55E, 0xFFC58CFF, 0xFF69D4CC };
        return colors[Math.abs((category == null ? "" : category).hashCode()) % colors.length];
    }

    private ModernSettingsTab getActiveSettingsTab() {
        return activeTab == null || activeTab.isDashboard() ? null : settingsTabs.get(activeTab);
    }

    private TabKey tabKeyForCommand(String command) {
        if (command == null) {
            return null;
        }
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            return TabKey.fixed(TabId.OTHER_FEATURE, "");
        }
        switch (command) {
            case "autoeat":
                return TabKey.fixed(TabId.AUTO_EAT, command);
            case "toggle_auto_fishing":
                return TabKey.fixed(TabId.AUTO_FISHING, command);
            case "toggle_fly":
                return TabKey.fixed(TabId.FLY, command);
            case "debug_settings":
                return TabKey.fixed(TabId.DEBUG, command);
            case "setloop":
                return TabKey.fixed(TabId.LOOP_COUNT, command);
            case "current_resolution_info":
                return TabKey.fixed(TabId.RESOLUTION, command);
            case "toggle_auto_pickup":
                return TabKey.fixed(TabId.AUTO_PICKUP, command);
            case "toggle_auto_use_item":
                return TabKey.fixed(TabId.AUTO_USE_ITEM, command);
            case "block_replacement_config":
                return TabKey.fixed(TabId.BLOCK_REPLACEMENT, command);
            case "chat_optimization":
                return TabKey.fixed(TabId.CHAT_OPTIMIZATION, command);
            case "baritone_parkour":
                return TabKey.fixed(TabId.BARITONE_PARKOUR, command);
            case "toggle_kill_aura":
                return TabKey.fixed(TabId.KILL_AURA, command);
            case "warehouse_manager":
                return TabKey.fixed(TabId.WAREHOUSE, command);
            case "baritone_settings":
                return TabKey.fixed(TabId.BARITONE, command);
            case "profile_manager":
                return TabKey.fixed(TabId.PROFILE, command);
            case "keybind_manager":
                return TabKey.fixed(TabId.KEYBINDS, command);
            case "packet_handler":
                return TabKey.fixed(TabId.PACKET, command);
            case "gui_inspector_manager":
                return TabKey.fixed(TabId.GUI_INSPECTOR, command);
            case "performance_monitor":
                return TabKey.fixed(TabId.PERFORMANCE, command);
            case "terrain_scanner":
                return TabKey.fixed(TabId.TERRAIN_SCANNER, command);
            case "memory_manager":
                return TabKey.fixed(TabId.MEMORY, command);
            case "general_settings":
                return TabKey.fixed(TabId.GENERAL_SETTINGS, command);
            default:
                return tabRegistry.find(command) == null ? null : TabKey.ofCommand(command);
        }
    }

    private ModernSettingsTab createSettingsTab(TabKey key, String command) {
        if (key == null) {
            return null;
        }
        if (key.isOtherFeature()) {
            return ModernOtherFeatureSettingsTab.createFromCommand(activeOtherFeatureCommand);
        }
        String effectiveCommand = command == null || command.isEmpty() ? commandForTabKey(key) : command;
        if (effectiveCommand == null) {
            return null;
        }
        ModernMainLayout.Rect bounds = lastLayout == null ? null : lastLayout.content;
        ModernScreenContext context = new ModernScreenContext(this.mc, this.width, this.height, bounds,
                effectiveCommand, "", this::openSettingsTabForCommand);
        return tabRegistry.create(effectiveCommand, this.mc, context);
    }

    private int getTabWidth(TabKey tab) {
        Integer width = tabWidths.get(tab);
        return width == null ? getMinimumTabWidth(tab) : width;
    }

    private int getMinimumTabWidth(TabKey tab) {
        // Resolve registry titles before measuring, just as ModernUiRenderer.drawText does.
        String title = com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(getTabTitle(tab));
        // Match the title inset and the space reserved for the close button in drawTabs.
        return Math.max(20, fontRenderer.getStringWidth(title)) + (tab.isDashboard() ? 14 : 28);
    }

    private int getPreferredTabWidth(TabKey tab, int minimumWidth) {
        return Math.max(minimumWidth, tab.isDashboard() ? 94 : tab.fixedId == null ? 132 : 122);
    }

    private void updateTabWidths() {
        tabWidths.clear();
        int minimumTotal = Math.max(0, openTabs.size() - 1) * 5;
        int spareTotal = 0;
        for (TabKey tab : openTabs) {
            int minimumWidth = getMinimumTabWidth(tab);
            tabWidths.put(tab, minimumWidth);
            minimumTotal += minimumWidth;
            spareTotal += getPreferredTabWidth(tab, minimumWidth) - minimumWidth;
        }
        int availableSpare = tabStripBounds == null ? spareTotal
                : clamp(tabStripBounds.width - minimumTotal, 0, spareTotal);
        int cumulativeSpare = 0;
        int allocatedSpare = 0;
        for (TabKey tab : openTabs) {
            int minimumWidth = tabWidths.get(tab);
            cumulativeSpare += getPreferredTabWidth(tab, minimumWidth) - minimumWidth;
            int nextAllocatedSpare = spareTotal == 0 ? 0
                    : (int) ((long) availableSpare * cumulativeSpare / spareTotal);
            tabWidths.put(tab, minimumWidth + nextAllocatedSpare - allocatedSpare);
            allocatedSpare = nextAllocatedSpare;
        }
    }

    private int getTotalTabWidth() {
        int total = 0;
        for (TabKey tab : openTabs) {
            total += getTabWidth(tab) + 5;
        }
        return Math.max(0, total - 5);
    }

    private void ensureActiveTabVisible() {
        if (tabStripBounds == null || activeTab == null) {
            return;
        }
        updateTabWidths();
        maxTabScrollOffset = Math.max(0, getTotalTabWidth() - tabStripBounds.width);
        tabScrollOffset = clamp(tabScrollOffset, 0, maxTabScrollOffset);
        int x = tabStripBounds.x - tabScrollOffset;
        for (TabKey tab : openTabs) {
            int width = getTabWidth(tab);
            if (tab.equals(activeTab)) {
                if (x < tabStripBounds.x) {
                    tabScrollOffset = clamp(tabScrollOffset - (tabStripBounds.x - x), 0, maxTabScrollOffset);
                } else if (x + width > tabStripBounds.right()) {
                    tabScrollOffset = clamp(tabScrollOffset + (x + width - tabStripBounds.right()), 0,
                            maxTabScrollOffset);
                }
                return;
            }
            x += width + 5;
        }
    }

    private String getTabTitle(TabKey key) {
        if (key == null) {
            return "gui.modern.main.u002";
        }
        if (key.fixedId == null) {
            ModernTabDescriptor descriptor = tabRegistry.find(key.command);
            return descriptor == null ? "gui.modern.main.u083" : descriptor.getTitle();
        }
        switch (key.fixedId) {
            case DASHBOARD:
                return tr("gui.modern.control_center", "gui.modern.main.u002");
            case AUTO_EAT:
                return tr("gui.modern.tab.auto_eat", "gui.modern.main.u084");
            case AUTO_FISHING:
                return tr("gui.modern.tab.auto_fishing", "gui.modern.main.u085");
            case FLY:
                return tr("gui.modern.tab.fly", "gui.modern.main.u086");
            case DEBUG:
                return tr("gui.modern.tab.debug", "gui.modern.main.u029");
            case LOOP_COUNT:
                return tr("gui.modern.tab.loop_count", "gui.modern.main.u087");
            case RESOLUTION:
                return tr("gui.modern.tab.resolution", "gui.modern.main.u088");
            case AUTO_PICKUP:
                return tr("gui.modern.tab.auto_pickup", "gui.modern.main.u089");
            case AUTO_USE_ITEM:
                return tr("gui.modern.tab.auto_use_item", "gui.modern.main.u090");
            case BLOCK_REPLACEMENT:
                return tr("gui.modern.tab.block_replacement", "gui.modern.main.u091");
            case CHAT_OPTIMIZATION:
                return tr("gui.modern.tab.chat_optimization", "gui.modern.main.u092");
            case BARITONE_PARKOUR:
                return tr("gui.modern.tab.baritone_parkour", "gui.modern.main.u093");
            case KILL_AURA:
                return tr("gui.modern.tab.kill_aura", "gui.modern.main.u094");
            case WAREHOUSE:
                return tr("gui.modern.tab.warehouse", "gui.modern.main.u095");
            case BARITONE:
                return tr("gui.modern.tab.baritone", "gui.modern.main.u096");
            case PROFILE:
                return tr("gui.modern.tab.profile", "gui.modern.main.u097");
            case KEYBINDS:
                return tr("gui.modern.tab.keybinds", "gui.modern.main.u098");
            case OTHER_FEATURE:
                return ModernOtherFeatureSettingsTab.resolveDisplayName(
                        ModernOtherFeatureSettingsTab.featureIdFromCommand(activeOtherFeatureCommand), "gui.modern.main.u099");
            case PACKET:
                return tr("gui.modern.tab.packet", "gui.modern.main.u100");
            case GUI_INSPECTOR:
                return tr("gui.modern.tab.gui_inspector", "gui.modern.main.u101");
            case PERFORMANCE:
                return tr("gui.modern.tab.performance", "gui.modern.main.u102");
            case TERRAIN_SCANNER:
                return tr("gui.modern.tab.terrain_scanner", "gui.modern.main.u103");
            case MEMORY:
                return tr("gui.modern.tab.memory", "gui.modern.main.u104");
            case GENERAL_SETTINGS:
                return tr("gui.general.title", "gui.modern.main.u011");
            default:
                return "gui.modern.main.u002";
        }
    }

    private String tr(String key, String fallback) {
        String value = I18n.format(key);
        return value == null || value.equals(key) ? fallback : value;
    }

    private String tr(String key, String fallback, Object... args) {
        String value = I18n.format(key, args);
        return value == null || value.equals(key) ? String.format(java.util.Locale.ROOT, fallback, args) : value;
    }

    private List<MenuCard> getFilteredCards() {
        if (GuiInventory.categoryItems == null || GuiInventory.categoryItemNames == null) {
            return Collections.emptyList();
        }
        String query = PinyinSearchHelper.normalizeQuery(searchField == null ? "" : searchField.getText());
        List<MenuCard> cards = new ArrayList<>();
        Set<String> seenCommands = new HashSet<>();
        if (query.isEmpty()) {
            addCategoryCards(cards, seenCommands, GuiInventory.currentCategory, query, true);
        } else {
            List<String> categoryOrder = new ArrayList<>(GuiInventory.categories);
            for (String category : GuiInventory.categoryItems.keySet()) {
                if (!categoryOrder.contains(category)) {
                    categoryOrder.add(category);
                }
            }
            for (String category : categoryOrder) {
                addCategoryCards(cards, seenCommands, category, query, false);
            }
        }
        String sortMode = getDashboardView().sortMode;
        if (MainUiLayoutManager.SORT_ALPHABETICAL.equals(sortMode)) {
            cards.sort((left, right) -> left.title.compareToIgnoreCase(right.title));
        } else if (MainUiLayoutManager.SORT_ACTIVE_FIRST.equals(sortMode)) {
            cards.sort((left, right) -> Boolean.compare(isCommandActive(right.command), isCommandActive(left.command)));
        }
        return cards;
    }

    private void addCategoryCards(List<MenuCard> cards, Set<String> seenCommands, String category, String query,
            boolean restrictToCurrentSubCategory) {
        List<String> commands = GuiInventory.categoryItems.get(category);
        List<String> names = GuiInventory.categoryItemNames.get(category);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            if (command == null || seenCommands.contains(command)
                    || restrictToCurrentSubCategory && !matchesCurrentPathSubcategory(command)) {
                continue;
            }
            String title = names != null && i < names.size() ? names.get(i) : command;
            String tooltip = GuiInventory.itemTooltips.get(command);
            GuiInventoryBase.GroupedItemSection section = findCommonSection(command, category);
            MenuCard card = new MenuCard(command, title, tooltip,
                    section == null ? "" : section.key, category);
            if (query.isEmpty() || matchesSearch(card, query)) {
                cards.add(card);
                seenCommands.add(command);
            }
        }
    }

    private GuiInventoryBase.GroupedItemSection findCommonSection(String command, String category) {
        if (!I18n.format("gui.inventory.category.common").equals(category)) {
            return null;
        }
        for (GuiInventoryBase.GroupedItemSection section : GuiInventory.commonItemSections) {
            if (section.commands.contains(command)) {
                return section;
            }
        }
        return null;
    }

    private boolean matchesSearch(MenuCard card, String query) {
        return PinyinSearchHelper.matchesNormalized(card.title + " " + card.command + " " + card.tooltip + " "
                + card.sourceCategory, query);
    }

    private boolean hasSearchQuery() {
        return searchField != null && !normalizeSearch(searchField.getText()).isEmpty();
    }

    private boolean isToggleCommand(String command) {
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            return true;
        }
        return "autoeat".equals(command) || "toggle_auto_fishing".equals(command)
                || "toggle_mouse_detach".equals(command) || "toggle_fly".equals(command)
                || "baritone_flight_pathing".equals(command)
                || "followconfig".equals(command) || "toggle_kill_aura".equals(command)
                || "conditional_execution".equals(command) || "auto_escape".equals(command)
                || "toggle_auto_pickup".equals(command) || "toggle_auto_use_item".equals(command)
                || "baritone_parkour".equals(command);
    }

    private ModernUiRenderer.Icon getCardIcon(MenuCard card) {
        if (card == null) {
            return ModernUiRenderer.Icon.GENERIC;
        }
        String command = card.command;
        if (isPathSequenceCommand(command)) return ModernUiRenderer.Icon.ROUTE;
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            BindableAction action = getCardBindableAction(card);
            if (action != null) {
                switch (action.getFeatureGroup()) {
                    case MOVEMENT: return ModernUiRenderer.Icon.MOVEMENT;
                    case BLOCK: return ModernUiRenderer.Icon.BLOCKS;
                    case ITEM: return ModernUiRenderer.Icon.INVENTORY;
                    case RENDER: return ModernUiRenderer.Icon.RENDER;
                    case WORLD: return ModernUiRenderer.Icon.WORLD;
                    case MISC: return ModernUiRenderer.Icon.MISC;
                    default: break;
                }
            }
            return ModernUiRenderer.Icon.GENERIC;
        }
        if ("autoeat".equals(command)) return ModernUiRenderer.Icon.FOOD;
        if ("toggle_auto_fishing".equals(command)) return ModernUiRenderer.Icon.FISHING;
        if ("toggle_mouse_detach".equals(command)) return ModernUiRenderer.Icon.MOUSE;
        if ("toggle_fly".equals(command) || "baritone_flight_pathing".equals(command)) {
            return ModernUiRenderer.Icon.FLIGHT;
        }
        if ("followconfig".equals(command)) return ModernUiRenderer.Icon.FOLLOW;
        if ("toggle_kill_aura".equals(command)) return ModernUiRenderer.Icon.COMBAT;
        if ("conditional_execution".equals(command)) return ModernUiRenderer.Icon.CONDITIONS;
        if ("auto_escape".equals(command)) return ModernUiRenderer.Icon.ESCAPE;
        if ("keybind_manager".equals(command)) return ModernUiRenderer.Icon.KEYBOARD;
        if ("profile_manager".equals(command)) return ModernUiRenderer.Icon.PROFILE;
        if ("chat_optimization".equals(command)) return ModernUiRenderer.Icon.CHAT;
        if ("toggle_auto_pickup".equals(command)) return ModernUiRenderer.Icon.PICKUP;
        if ("toggle_auto_use_item".equals(command)) return ModernUiRenderer.Icon.AUTO_USE;
        if ("block_replacement_config".equals(command)) return ModernUiRenderer.Icon.BLOCKS;
        if ("warehouse_manager".equals(command)) return ModernUiRenderer.Icon.STORAGE;
        if ("baritone_settings".equals(command)) return ModernUiRenderer.Icon.SETTINGS;
        if ("baritone_command_table".equals(command)) return ModernUiRenderer.Icon.COMMAND;
        if ("baritone_parkour".equals(command)) return ModernUiRenderer.Icon.MOVEMENT;
        if ("setloop".equals(command)) return ModernUiRenderer.Icon.LOOP;
        if ("debug_settings".equals(command)) return ModernUiRenderer.Icon.DEBUG;
        if ("current_resolution_info".equals(command)) return ModernUiRenderer.Icon.DISPLAY;
        if ("reload_paths".equals(command)) return ModernUiRenderer.Icon.RELOAD;
        if ("player_equipment_viewer".equals(command)) return ModernUiRenderer.Icon.INVENTORY;
        if ("packet_handler".equals(command)) return ModernUiRenderer.Icon.PACKET;
        if ("gui_inspector_manager".equals(command)) return ModernUiRenderer.Icon.INSPECTOR;
        if ("performance_monitor".equals(command)) return ModernUiRenderer.Icon.PERFORMANCE;
        if ("terrain_scanner".equals(command)) return ModernUiRenderer.Icon.TERRAIN;
        if ("memory_manager".equals(command)) return ModernUiRenderer.Icon.MEMORY;
        return ModernUiRenderer.Icon.GENERIC;
    }

    private boolean isCommandActive(String command) {
        if (isPathSequenceRunning(command)) {
            return true;
        }
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            return GuiInventory.isOtherFeatureEnabled(
                    ModernOtherFeatureSettingsTab.featureIdFromCommand(command));
        }
        if (GuiInventory.getCommonItemState(command, false) == GuiTheme.UiState.SUCCESS) {
            return true;
        }
        return false;
    }

    private boolean isPathSequenceRunning(String command) {
        return isPathSequenceRunningBackground(command) || isPathSequenceRunningForeground(command);
    }

    private boolean isPathSequenceRunningForeground(String command) {
        String name = getPathSequenceCommandName(command);
        return !name.isEmpty() && PathSequenceEventListener.isSequenceRunningInForeground(name);
    }

    private boolean isPathSequenceRunningBackground(String command) {
        String name = getPathSequenceCommandName(command);
        return !name.isEmpty() && PathSequenceEventListener.isSequenceRunningInBackground(name);
    }

    private String getPathSequenceCommandName(String command) {
        if (!isPathSequenceCommand(command)) {
            return "";
        }
        int separator = command.indexOf(':');
        return separator < 0 ? "" : command.substring(separator + 1).trim();
    }

    private String getCardSubtitle(MenuCard card, boolean active) {
        String detail;
        if (isPathSequenceRunningBackground(card.command)) {
            detail = "gui.modern.main.u072";
        } else if (isPathSequenceRunningForeground(card.command)) {
            detail = "gui.modern.main.u073";
        } else if (active) {
            detail = "gui.modern.main.u105";
        } else if (card.command.startsWith("path:") || card.command.startsWith("custom_path:")) {
            detail = "gui.modern.main.u106";
        } else {
            String tooltip = TextFormatting.getTextWithoutFormattingCodes(
                    card.tooltip.replace("\\n", " ").replace('\n', ' '));
            detail = tooltip == null || tooltip.trim().isEmpty()
                    ? isToggleCommand(card.command) ? "gui.modern.main.u107" : "gui.modern.main.u108"
                    : tooltip.trim();
        }
        String prefix = hasSearchQuery() && !card.sourceCategory.isEmpty() ? card.sourceCategory + " · " : "";
        return ModernUiRenderer.ellipsize(fontRenderer, prefix + detail, 160);
    }

    private String getCardInfoDescription(MenuCard card) {
        if (card != null && isPathSequenceCommand(card.command)) {
            PathSequence sequence = PathSequenceManager.getSequence(getPathSequenceCommandName(card.command));
            if (sequence != null) {
                String note = sequence.getNote();
                return note != null && !note.trim().isEmpty()
                        ? I18n.format("gui.modern.path.wb.fmt.seq_note", note)
                        : tr("gui.modern.main.fmt.card_config", "%s 的配置项。", tr(card.title, card.title));
            }
        }
        String detail = card == null ? "" : card.tooltip;
        if (detail == null || detail.trim().isEmpty()) {
            detail = card == null ? "gui.modern.main.u109" : tr("gui.modern.main.fmt.card_config", "%s 的配置项。", tr(card.title, card.title));
        }
        String plain = TextFormatting.getTextWithoutFormattingCodes(detail.replace("\\n", "\n"));
        if (plain == null || plain.trim().isEmpty()) {
            return "";
        }
        StringBuilder description = new StringBuilder();
        for (String line : plain.split("\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty() || card != null && value.equals(card.title) || isCardMouseHint(value)) {
                continue;
            }
            if (description.length() > 0) {
                description.append('\n');
            }
            description.append(value);
        }
        return description.toString();
    }

    private boolean isCardMouseHint(String line) {
        String compact = line == null ? "" : line.replace(" ", "").toLowerCase(Locale.ROOT);
        return compact.startsWith("gui.modern.main.u110") || compact.startsWith("gui.modern.main.u111") || compact.startsWith("gui.modern.main.u112")
                || compact.startsWith("gui.modern.main.u113") || compact.startsWith("leftclick")
                || compact.startsWith("rightclick") || compact.startsWith("leftbutton")
                || compact.startsWith("rightbutton");
    }

    private boolean hasDashboardMouseActions(MenuCard card) {
        return card != null && isToggleCommand(card.command) && !"toggle_mouse_detach".equals(card.command);
    }

    private String getCardShortcutLabel(MenuCard card) {
        if (card != null && isPathSequenceCommand(card.command)) {
            Keybind pathKeybind = KeybindManager.pathSequenceKeybinds.get(getPathSequenceCommandName(card.command));
            return formatKeybindLabel(pathKeybind);
        }
        BindableAction action = getCardBindableAction(card);
        if (action == null) {
            return "";
        }
        return formatKeybindLabel(KeybindManager.keybinds.get(action));
    }

    private String formatKeybindLabel(Keybind keybind) {
        if (keybind == null || keybind.getCombinations().isEmpty()) {
            return "gui.modern.main.u114";
        }
        KeybindManager.KeyCombination combination = keybind.getCombinations().get(0);
        List<String> parts = new ArrayList<>();
        Set<Integer> modifiers = combination.getModifiers();
        if (modifiers.contains(Keyboard.KEY_LCONTROL) || modifiers.contains(Keyboard.KEY_RCONTROL)) {
            parts.add("Ctrl");
        }
        if (modifiers.contains(Keyboard.KEY_LSHIFT) || modifiers.contains(Keyboard.KEY_RSHIFT)) {
            parts.add("Shift");
        }
        if (modifiers.contains(Keyboard.KEY_LMENU) || modifiers.contains(Keyboard.KEY_RMENU)) {
            parts.add("Alt");
        }
        String keyName = Keyboard.getKeyName(combination.getKeyCode());
        parts.add(keyName == null || keyName.trim().isEmpty() ? "?" : keyName);
        return "【" + String.join("+", parts) + "】";
    }

    private BindableAction getCardBindableAction(MenuCard card) {
        if (card == null) {
            return null;
        }
        String command = card.command;
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            String featureId = ModernOtherFeatureSettingsTab.featureIdFromCommand(command);
            for (BindableAction action : BindableAction.values()) {
                if (action.isFeatureToggle() && featureId.equalsIgnoreCase(action.getFeatureId())) {
                    return action;
                }
            }
            return null;
        }
        if ("autoeat".equals(command)) return BindableAction.TOGGLE_AUTO_EAT;
        if ("toggle_auto_fishing".equals(command)) return BindableAction.TOGGLE_AUTO_FISHING;
        if ("toggle_mouse_detach".equals(command)) return BindableAction.TOGGLE_MOUSE_DETACH;
        if ("toggle_fly".equals(command)) return BindableAction.TOGGLE_FLY;
        if ("toggle_kill_aura".equals(command)) return BindableAction.TOGGLE_KILL_AURA;
        if ("toggle_auto_pickup".equals(command)) return BindableAction.TOGGLE_AUTO_PICKUP;
        if ("player_equipment_viewer".equals(command)) return BindableAction.OPEN_INVENTORY_VIEWER;
        if ("setloop".equals(command)) return BindableAction.SET_LOOP_COUNT;
        return null;
    }

    private List<ToolEntry> getToolEntries() {
        List<ToolEntry> entries = new ArrayList<>();
        entries.add(new ToolEntry("theme", "gui.modern.main.u115"));
        entries.add(new ToolEntry("reset_window", "gui.modern.main.u116"));
        entries.add(new ToolEntry("update", "gui.modern.main.u117"));
        entries.add(new ToolEntry("changelog", "gui.changelog.title"));
        entries.add(new ToolEntry("donate", "gui.modern.main.u118"));
        return entries;
    }

    private void refreshCommandPaletteEntries() {
        String previousId = this.commandPaletteSelectedIndex >= 0
                && this.commandPaletteSelectedIndex < this.commandPaletteEntries.size()
                        ? this.commandPaletteEntries.get(this.commandPaletteSelectedIndex).id
                        : "";
        String query = PinyinSearchHelper.normalizeQuery(
                this.commandPaletteSearchField == null ? "" : this.commandPaletteSearchField.getText());
        List<CommandPaletteEntry> allEntries = buildCommandPaletteEntries();
        List<CommandPaletteEntry> filteredEntries = new ArrayList<>();
        for (CommandPaletteEntry entry : allEntries) {
            if ((!this.commandPaletteRecentOnly || recentPaletteCommands.isEmpty()
                            || recentPaletteCommands.contains(entry.id))
                    && (query.isEmpty() || PinyinSearchHelper.matchesNormalized(buildPaletteSearchText(entry), query))) {
                filteredEntries.add(entry);
            }
        }
        if (this.commandPaletteRecentOnly && !recentPaletteCommands.isEmpty()) {
            filteredEntries.sort((left, right) -> Integer.compare(recentPaletteCommands.indexOf(left.id),
                    recentPaletteCommands.indexOf(right.id)));
        }
        this.commandPaletteEntries.clear();
        this.commandPaletteEntries.addAll(filteredEntries);
        int restoredIndex = -1;
        if (!previousId.isEmpty()) {
            for (int i = 0; i < this.commandPaletteEntries.size(); i++) {
                if (previousId.equals(this.commandPaletteEntries.get(i).id)) {
                    restoredIndex = i;
                    break;
                }
            }
        }
        this.commandPaletteSelectedIndex = restoredIndex >= 0 ? restoredIndex
                : clamp(this.commandPaletteSelectedIndex, 0, Math.max(0, this.commandPaletteEntries.size() - 1));
        if (this.commandPaletteEntries.isEmpty()) {
            this.commandPaletteSelectedIndex = 0;
            this.commandPaletteScrollOffset = 0;
        }
    }

    private List<CommandPaletteEntry> buildCommandPaletteEntries() {
        Map<String, CommandPaletteEntry> entries = new LinkedHashMap<>();
        Set<BindableAction> representedActions = new HashSet<>();

        addCommandPaletteEntry(entries, new CommandPaletteEntry("stop_foreground", "gui.modern.main.u004",
                "gui.modern.main.u119", "gui.modern.main.u120", "stop_foreground", null,
                BindableAction.STOP_SEQUENCE, "action:" + BindableAction.STOP_SEQUENCE.name()));
        representedActions.add(BindableAction.STOP_SEQUENCE);
        addCommandPaletteEntry(entries, new CommandPaletteEntry("stop_background", "gui.modern.main.u005",
                "gui.modern.main.u121", "gui.modern.main.u120", "stop_background", null, null, null));
        addCommandPaletteEntry(entries, new CommandPaletteEntry("path_manager", "gui.modern.main.u122",
                "gui.modern.main.u123", "gui.modern.main.u124", "path_manager", null, null, null));

        List<String> categoryOrder = new ArrayList<>(GuiInventory.categories);
        for (String category : GuiInventory.categoryItems.keySet()) {
            if (!categoryOrder.contains(category)) {
                categoryOrder.add(category);
            }
        }
        for (String category : categoryOrder) {
            List<String> commands = GuiInventory.categoryItems.get(category);
            if (commands == null) {
                continue;
            }
            List<String> names = GuiInventory.categoryItemNames.get(category);
            for (int i = 0; i < commands.size(); i++) {
                String command = commands.get(i);
                if (command == null || command.trim().isEmpty()) {
                    continue;
                }
                String title = names != null && i < names.size() ? names.get(i) : command;
                String description = cleanPaletteText(GuiInventory.itemTooltips.get(command), "gui.modern.main.u125");
                BindableAction action = findBindableAction(command);
                if (action != null) {
                    representedActions.add(action);
                }
                String target = action == null ? keybindTargetForCommand(command)
                        : "action:" + action.name();
                addCommandPaletteEntry(entries, new CommandPaletteEntry(command, title, description,
                        category == null ? "gui.modern.main.u126" : category, command, null, action, target));
            }
        }

        for (ToolEntry tool : getToolEntries()) {
            addCommandPaletteEntry(entries, new CommandPaletteEntry("utility:" + tool.action, tool.title,
                    getToolDescription(tool.action), "gui.modern.main.u007", null, tool.action, null, null));
        }

        for (BindableAction action : BindableAction.values()) {
            if (action == BindableAction.OPEN_COMMAND_PALETTE || representedActions.contains(action)) {
                continue;
            }
            addCommandPaletteEntry(entries, new CommandPaletteEntry("action:" + action.name(),
                    action.getDisplayName(), cleanPaletteText(action.getDescription(), "gui.modern.main.u127"),
                    getBindableActionGroup(action), null, null, action, "action:" + action.name()));
        }
        return new ArrayList<>(entries.values());
    }

    private void addCommandPaletteEntry(Map<String, CommandPaletteEntry> entries, CommandPaletteEntry entry) {
        if (entry != null && !entry.id.isEmpty() && !entries.containsKey(entry.id)) {
            entries.put(entry.id, entry);
        }
    }

    private BindableAction findBindableAction(String command) {
        if (command == null) {
            return null;
        }
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            String featureId = ModernOtherFeatureSettingsTab.featureIdFromCommand(command);
            for (BindableAction action : BindableAction.values()) {
                if (action.isFeatureToggle() && action.getFeatureId().equalsIgnoreCase(featureId)) {
                    return action;
                }
            }
        }
        switch (command) {
            case "autoeat":
                return BindableAction.TOGGLE_AUTO_EAT;
            case "toggle_auto_fishing":
                return BindableAction.TOGGLE_AUTO_FISHING;
            case "toggle_mouse_detach":
                return BindableAction.TOGGLE_MOUSE_DETACH;
            case "toggle_fly":
                return BindableAction.TOGGLE_FLY;
            case "toggle_kill_aura":
                return BindableAction.TOGGLE_KILL_AURA;
            case "toggle_auto_pickup":
                return BindableAction.TOGGLE_AUTO_PICKUP;
            case "setloop":
                return BindableAction.SET_LOOP_COUNT;
            case "player_equipment_viewer":
                return BindableAction.OPEN_INVENTORY_VIEWER;
            default:
                return null;
        }
    }

    private String keybindTargetForCommand(String command) {
        if (command == null || (!command.startsWith("path:") && !command.startsWith("custom_path:"))) {
            return null;
        }
        int split = command.indexOf(':');
        String sequenceName = split < 0 ? "" : command.substring(split + 1).trim();
        return sequenceName.isEmpty() ? null : "sequence:" + sequenceName;
    }

    private String getBindableActionGroup(BindableAction action) {
        if (action != null && action.getFeatureGroup() != null) {
            return I18n.format(action.getFeatureGroup().getTranslationKey());
        }
        return I18n.format("gui.keybind.group.actions");
    }

    private String getToolDescription(String action) {
        if ("changelog".equals(action)) {
            return "gui.changelog.description";
        }
        if ("theme".equals(action)) {
            return "gui.modern.main.u128";
        }
        if ("reset_window".equals(action)) {
            return "gui.modern.main.u129";
        }
        if ("update".equals(action)) {
            return "gui.modern.main.u130";
        }
        return "gui.modern.main.u131";
    }

    private String buildPaletteSearchText(CommandPaletteEntry entry) {
        return entry.title + " " + entry.description + " " + entry.group + " " + entry.id + " "
                + getPaletteBindingText(entry);
    }

    private String getPaletteBindingText(CommandPaletteEntry entry) {
        Keybind keybind = getPaletteKeybind(entry);
        if (keybind == null || keybind.getKeyCode() == Keyboard.KEY_NONE) {
            return cleanPaletteText(I18n.format("gui.keybind.unbound"), "gui.modern.main.u132");
        }
        return cleanPaletteText(keybind.toString(), "gui.modern.main.u132");
    }

    private Keybind getPaletteKeybind(CommandPaletteEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.action != null) {
            return KeybindManager.keybinds.get(entry.action);
        }
        if (entry.keybindTarget != null && entry.keybindTarget.startsWith("sequence:")) {
            return KeybindManager.pathSequenceKeybinds.get(entry.keybindTarget.substring("sequence:".length()));
        }
        return null;
    }

    private static String cleanPaletteText(String value, String fallback) {
        String source = value == null || value.trim().isEmpty() ? fallback : value;
        if (source == null) return "";
        if (I18n.hasKey(source)) source = I18n.format(source);
        String plain = TextFormatting.getTextWithoutFormattingCodes(source);
        return plain == null ? "" : plain.replace("\\r\\n", " ").replace("\\n", " ")
                .replace("\\r", " ").replace("\\t", " ").replaceAll("\\s+", " ").trim();
    }

    private int getCategoryItemCount(String category) {
        return getCategoryItemCount(category, "");
    }

    private int getCategoryItemCount(String category, String subCategory) {
        Map<String, List<String>> items = GuiInventory.categoryItems;
        if (items == null) {
            return 0;
        }
        List<String> values = items.get(category);
        if (values == null || values.isEmpty() || subCategory == null || subCategory.trim().isEmpty()) {
            return values == null ? 0 : values.size();
        }
        int count = 0;
        for (String command : values) {
            if (matchesSequenceSubcategory(command, subCategory)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesCurrentPathSubcategory(String command) {
        String subCategory = GuiInventory.currentCustomSubCategory;
        return subCategory == null || subCategory.trim().isEmpty()
                || !isPathSequenceCommand(command)
                || matchesSequenceSubcategory(command, subCategory);
    }

    private boolean matchesSequenceSubcategory(String command, String subCategory) {
        if (!isPathSequenceCommand(command)) {
            return true;
        }
        int separator = command.indexOf(':');
        String sequenceName = separator < 0 ? "" : command.substring(separator + 1).trim();
        PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
        return sequence != null && subCategory != null
                && subCategory.trim().equalsIgnoreCase(sequence.getSubCategory() == null
                        ? "" : sequence.getSubCategory().trim());
    }

    private boolean isPathSequenceCommand(String command) {
        return command != null && (command.startsWith("path:") || command.startsWith("custom_path:"));
    }

    private void setHoveredTooltip(String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        hoveredTooltip = tooltip;
        hoveredTooltipX = mouseX;
        hoveredTooltipY = mouseY;
    }

    private static String normalizeSearch(String value) {
        String plain = TextFormatting.getTextWithoutFormattingCodes(value == null ? "" : value);
        return plain == null ? "" : plain.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private static ModernMainLayout.Rect intersection(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        if (!intersects(first, second)) {
            return new ModernMainLayout.Rect(0, 0, 0, 0);
        }
        int x = Math.max(first.x, second.x);
        int y = Math.max(first.y, second.y);
        int right = Math.min(first.right(), second.right());
        int bottom = Math.min(first.bottom(), second.bottom());
        return new ModernMainLayout.Rect(x, y, right - x, bottom - y);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }
}
