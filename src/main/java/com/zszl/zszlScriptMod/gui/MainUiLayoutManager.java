package com.zszl.zszlScriptMod.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

public final class MainUiLayoutManager {

    public static final String SORT_DEFAULT = "default";
    public static final String SORT_ALPHABETICAL = "alphabetical";
    public static final String SORT_LAST_OPENED = "last_opened";
    public static final String SORT_OPEN_COUNT = "open_count";
    public static final String SORT_ACTIVE_FIRST = "active_first";

    public static final String LAYOUT_TILE = "tile";
    public static final String LAYOUT_LIST = "list";
    public static final String LAYOUT_COMPACT = "compact";
    public static final String LAYOUT_WIDE = "wide";

    public static final String ICON_DEFAULT = "default";
    public static final String ICON_XL = "xl";
    public static final String ICON_LARGE = "large";
    public static final String ICON_MEDIUM = "medium";
    public static final String ICON_SMALL = "small";

    public static final String FLOW_WATERFALL = "waterfall";
    public static final String FLOW_PAGED = "paged";

    /** Card dimensions are independently configurable; zero keeps the responsive default. */
    public static final int DASHBOARD_CARD_DIMENSION_AUTO = 0;
    public static final int MIN_DASHBOARD_CARD_WIDTH = 112;
    public static final int MAX_DASHBOARD_CARD_WIDTH = 420;
    public static final int MIN_DASHBOARD_CARD_HEIGHT = 34;
    public static final int MAX_DASHBOARD_CARD_HEIGHT = 160;

    public static final int DEFAULT_DASHBOARD_TEXT_SCALE_PERCENT = 100;
    public static final int MIN_DASHBOARD_TEXT_SCALE_PERCENT = 50;
    public static final int MAX_DASHBOARD_TEXT_SCALE_PERCENT = 200;
    public static final String DASHBOARD_TEXT_ALIGN_LEFT = "left";
    public static final String DASHBOARD_TEXT_ALIGN_CENTER = "center";
    public static final String DASHBOARD_TEXT_ALIGN_RIGHT = "right";
    public static final String DASHBOARD_TEXT_VERTICAL_TOP = "top";
    public static final String DASHBOARD_TEXT_VERTICAL_CENTER = "center";
    public static final String DASHBOARD_TEXT_VERTICAL_BOTTOM = "bottom";

    public static final String DASHBOARD_INFO_ICON = "icon";
    public static final String DASHBOARD_INFO_INLINE = "inline";
    public static final String DASHBOARD_INFO_HIDDEN = "hidden";

    public static final String DASHBOARD_SETTINGS_FLOATING = "floating";
    public static final String DASHBOARD_SETTINGS_LEFT = "left";
    public static final String DASHBOARD_SETTINGS_RIGHT = "right";
    public static final int DEFAULT_DASHBOARD_SETTINGS_WIDTH = 260;
    public static final int MIN_DASHBOARD_SETTINGS_WIDTH = 180;
    public static final int MAX_DASHBOARD_SETTINGS_WIDTH = 520;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MODERN_UI_SCALE_PERCENT = 100;
    private static final int DEFAULT_DETACHED_UI_SCALE_PERCENT = 200;
    private static final int MIN_UI_SCALE_PERCENT = 100;
    private static final int MAX_UI_SCALE_PERCENT = 300;

    private static final class LayoutData {
        int categoryPanelBaseWidth = -1;
        int modernSidebarWidth = -1;
        double modernWindowXRatio = -1.0D;
        double modernWindowYRatio = -1.0D;
        double modernWindowWidthRatio = -1.0D;
        double modernWindowHeightRatio = -1.0D;
        int modernDetachedWindowX = -1;
        int modernDetachedWindowY = -1;
        int modernDetachedWindowWidth = -1;
        int modernDetachedWindowHeight = -1;
        boolean modernDetachedWindowMaximized;
        int modernUiScalePercent = -1;
        int modernDetachedUiScalePercent = -1;
        boolean modernAutoFocus = true;
        boolean modernAutoPauseOnMenuOpen = true;
        double modernRunningStatusXRatio = -1.0D;
        double modernRunningStatusYRatio = -1.0D;
        double modernRunningStatusWidthRatio = -1.0D;
        double modernRunningStatusHeightRatio = -1.0D;
        boolean modernRunningStatusVisible = false;
        boolean modernDashboardGrouping = true;
        String modernDashboardSortMode = SORT_DEFAULT;
        String modernDashboardIconSize = ICON_MEDIUM;
        boolean modernDashboardShowIcons = true;
        int modernDashboardCardWidth = DASHBOARD_CARD_DIMENSION_AUTO;
        int modernDashboardCardHeight = DASHBOARD_CARD_DIMENSION_AUTO;
        int modernDashboardTextScalePercent = DEFAULT_DASHBOARD_TEXT_SCALE_PERCENT;
        String modernDashboardTextAlign = DASHBOARD_TEXT_ALIGN_CENTER;
        String modernDashboardTextVerticalAlign = DASHBOARD_TEXT_VERTICAL_CENTER;
        String modernDashboardInfoMode = DASHBOARD_INFO_ICON;
        String modernDashboardSettingsPlacement = DASHBOARD_SETTINGS_FLOATING;
        int modernDashboardSettingsWidth = DEFAULT_DASHBOARD_SETTINGS_WIDTH;
        int modernDashboardColumns = 0;
        String modernDashboardFlowMode = FLOW_WATERFALL;
        Set<String> modernDashboardCollapsedGroups = new LinkedHashSet<>();
        Map<String, DashboardViewMeta> modernDashboardViews = new LinkedHashMap<>();
        boolean autoFollowNavigationTop;
        boolean autoFollowNavigationText;
        boolean modernTabStripVertical;
        int modernTabStripWidth = -1;
        int modernTabStripHeight = -1;
        boolean modernHeaderCollapsed;
        Set<String> modernPinnedTools = new LinkedHashSet<>();
        Map<String, Double> modernSplitRatios = new LinkedHashMap<>();
        boolean modernStateRecordingEnabled = true;
        Map<String, Boolean> modernStateRecordingPages = new LinkedHashMap<>();
        Map<String, JsonObject> modernPageStates = new LinkedHashMap<>();
        Map<String, GroupMeta> groupMeta = new LinkedHashMap<>();
        Map<String, List<String>> subCategories = new LinkedHashMap<>();
        Map<String, SequenceOpenStats> sequenceOpenStats = new LinkedHashMap<>();
        Set<String> sequenceFavorites = new LinkedHashSet<>();
    }

    public static final class GroupMeta {
        public boolean pinned;
        public boolean collapsed;
        public String sortMode = SORT_DEFAULT;
        public String layoutMode = LAYOUT_TILE;
        public String iconSize = ICON_DEFAULT;

        public GroupMeta copy() {
            GroupMeta copy = new GroupMeta();
            copy.pinned = pinned;
            copy.collapsed = collapsed;
            copy.sortMode = sortMode;
            copy.layoutMode = layoutMode;
            copy.iconSize = iconSize;
            return copy;
        }
    }

    public static final class DashboardViewMeta {
        public boolean grouping = true;
        public String sortMode = SORT_DEFAULT;
        public String iconSize = ICON_MEDIUM;
        public boolean showIcons = true;
        public int cardWidth = DASHBOARD_CARD_DIMENSION_AUTO;
        public int cardHeight = DASHBOARD_CARD_DIMENSION_AUTO;
        public int textScalePercent = DEFAULT_DASHBOARD_TEXT_SCALE_PERCENT;
        public String textAlign = DASHBOARD_TEXT_ALIGN_CENTER;
        public String textVerticalAlign = DASHBOARD_TEXT_VERTICAL_CENTER;
        public String infoMode = DASHBOARD_INFO_ICON;
        public int columns;
        public String flowMode = FLOW_WATERFALL;
        public Set<String> collapsedGroups = new LinkedHashSet<>();

        private DashboardViewMeta copy() {
            DashboardViewMeta copy = new DashboardViewMeta();
            copy.grouping = grouping;
            copy.sortMode = sortMode;
            copy.iconSize = iconSize;
            copy.showIcons = showIcons;
            copy.cardWidth = cardWidth;
            copy.cardHeight = cardHeight;
            copy.textScalePercent = textScalePercent;
            copy.textAlign = textAlign;
            copy.textVerticalAlign = textVerticalAlign;
            copy.infoMode = infoMode;
            copy.columns = columns;
            copy.flowMode = flowMode;
            copy.collapsedGroups.addAll(collapsedGroups);
            return copy;
        }
    }

    public static final class SequenceOpenStats {
        public long lastOpenedAt;
        public int openCount;

        public SequenceOpenStats copy() {
            SequenceOpenStats copy = new SequenceOpenStats();
            copy.lastOpenedAt = lastOpenedAt;
            copy.openCount = openCount;
            return copy;
        }
    }

    public static final class ModernWindowLayout {
        public final double xRatio;
        public final double yRatio;
        public final double widthRatio;
        public final double heightRatio;

        private ModernWindowLayout(double xRatio, double yRatio, double widthRatio, double heightRatio) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
            this.widthRatio = widthRatio;
            this.heightRatio = heightRatio;
        }

        public boolean isValid() {
            return isFiniteRatio(xRatio) && isFiniteRatio(yRatio) && isFiniteRatio(widthRatio)
                    && isFiniteRatio(heightRatio) && widthRatio > 0.0D && heightRatio > 0.0D;
        }
    }

    public static final class DetachedWindowLayout {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final boolean maximized;
        public final int uiScalePercent;

        private DetachedWindowLayout(int x, int y, int width, int height, boolean maximized, int uiScalePercent) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.maximized = maximized;
            this.uiScalePercent = uiScalePercent;
        }

        public boolean isValid() {
            return width > 0 && height > 0;
        }
    }

    public static final class RunningStatusLayout {
        public final double xRatio;
        public final double yRatio;
        public final double widthRatio;
        public final double heightRatio;

        private RunningStatusLayout(double xRatio, double yRatio, double widthRatio, double heightRatio) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
            this.widthRatio = widthRatio;
            this.heightRatio = heightRatio;
        }

        public boolean isValid() {
            return isFiniteRatio(xRatio) && isFiniteRatio(yRatio) && isFiniteRatio(widthRatio)
                    && isFiniteRatio(heightRatio) && widthRatio > 0.0D && heightRatio > 0.0D;
        }
    }

    private static LayoutData data = new LayoutData();
    private static boolean loaded = false;

    private MainUiLayoutManager() {
    }

    private static Path getLayoutFile() {
        return ProfileManager.getCurrentProfileDir().resolve("inventory_ui_layout.json");
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            synchronizeWithSequences(false);
            return;
        }

        loaded = true;
        data = new LayoutData();
        boolean migratedLegacyModernUiScale = false;
        Path file = getLayoutFile();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    if (obj.has("groupMeta") && obj.get("groupMeta").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, GroupMeta>>() {
                        }.getType();
                        Map<String, GroupMeta> loadedMap = GSON.fromJson(obj.get("groupMeta"), mapType);
                        if (loadedMap != null) {
                            data.groupMeta.putAll(loadedMap);
                        }
                    }

                    if (obj.has("subCategories") && obj.get("subCategories").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, List<String>>>() {
                        }.getType();
                        Map<String, List<String>> loadedMap = GSON.fromJson(obj.get("subCategories"), mapType);
                        if (loadedMap != null) {
                            data.subCategories.putAll(loadedMap);
                        }
                    }

                    if (obj.has("categoryPanelBaseWidth")) {
                        try {
                            data.categoryPanelBaseWidth = obj.get("categoryPanelBaseWidth").getAsInt();
                        } catch (Exception ignored) {
                        }
                    }

                    data.modernSidebarWidth = readInt(obj, "modernSidebarWidth", -1);
                    data.modernWindowXRatio = readDouble(obj, "modernWindowXRatio", -1.0D);
                    data.modernWindowYRatio = readDouble(obj, "modernWindowYRatio", -1.0D);
                    data.modernWindowWidthRatio = readDouble(obj, "modernWindowWidthRatio", -1.0D);
                    data.modernWindowHeightRatio = readDouble(obj, "modernWindowHeightRatio", -1.0D);
                    data.modernDetachedWindowX = readInt(obj, "modernDetachedWindowX", -1);
                    data.modernDetachedWindowY = readInt(obj, "modernDetachedWindowY", -1);
                    data.modernDetachedWindowWidth = readInt(obj, "modernDetachedWindowWidth", -1);
                    data.modernDetachedWindowHeight = readInt(obj, "modernDetachedWindowHeight", -1);
                    data.modernDetachedWindowMaximized = readBoolean(obj, "modernDetachedWindowMaximized", false);
                    boolean hasModernUiScale = obj.has("modernUiScalePercent");
                    data.modernUiScalePercent = readInt(obj, "modernUiScalePercent", -1);
                    data.modernDetachedUiScalePercent = readInt(obj, "modernDetachedUiScalePercent", -1);
                    if (!hasModernUiScale && data.modernDetachedUiScalePercent >= MIN_UI_SCALE_PERCENT) {
                        // Before the split, this field was shared by both UI hosts.
                        // Keep the old value with the detached host and let the in-game
                        // host use its new 100% default.
                        migratedLegacyModernUiScale = true;
                    }
                    data.modernAutoFocus = readBoolean(obj, "modernAutoFocus", true);
                    data.modernAutoPauseOnMenuOpen = readBoolean(obj, "modernAutoPauseOnMenuOpen", true);
                    data.modernRunningStatusXRatio = readDouble(obj, "modernRunningStatusXRatio", -1.0D);
                    data.modernRunningStatusYRatio = readDouble(obj, "modernRunningStatusYRatio", -1.0D);
                    data.modernRunningStatusWidthRatio = readDouble(obj, "modernRunningStatusWidthRatio", -1.0D);
                    data.modernRunningStatusHeightRatio = readDouble(obj, "modernRunningStatusHeightRatio", -1.0D);
                    data.modernRunningStatusVisible = readBoolean(obj, "modernRunningStatusVisible", false);
                    data.modernDashboardGrouping = readBoolean(obj, "modernDashboardGrouping", true);
                    data.modernDashboardSortMode = readString(obj, "modernDashboardSortMode", SORT_DEFAULT);
                    data.modernDashboardIconSize = readString(obj, "modernDashboardIconSize", ICON_MEDIUM);
                    data.modernDashboardShowIcons = readBoolean(obj, "modernDashboardShowIcons", true);
                    data.modernDashboardCardWidth = readInt(obj, "modernDashboardCardWidth",
                            DASHBOARD_CARD_DIMENSION_AUTO);
                    data.modernDashboardCardHeight = readInt(obj, "modernDashboardCardHeight",
                            DASHBOARD_CARD_DIMENSION_AUTO);
                    data.modernDashboardTextScalePercent = readInt(obj, "modernDashboardTextScalePercent",
                            DEFAULT_DASHBOARD_TEXT_SCALE_PERCENT);
                    data.modernDashboardTextAlign = readString(obj, "modernDashboardTextAlign",
                            DASHBOARD_TEXT_ALIGN_CENTER);
                    data.modernDashboardTextVerticalAlign = readString(obj, "modernDashboardTextVerticalAlign",
                            DASHBOARD_TEXT_VERTICAL_CENTER);
                    data.modernDashboardInfoMode = readString(obj, "modernDashboardInfoMode", DASHBOARD_INFO_ICON);
                    data.modernDashboardSettingsPlacement = readString(obj, "modernDashboardSettingsPlacement",
                            DASHBOARD_SETTINGS_FLOATING);
                    data.modernDashboardSettingsWidth = readInt(obj, "modernDashboardSettingsWidth",
                            DEFAULT_DASHBOARD_SETTINGS_WIDTH);
                    data.modernDashboardColumns = readInt(obj, "modernDashboardColumns", 0);
                    data.modernDashboardFlowMode = readString(obj, "modernDashboardFlowMode", FLOW_WATERFALL);
                    if (obj.has("modernDashboardCollapsedGroups")
                            && obj.get("modernDashboardCollapsedGroups").isJsonArray()) {
                        Type setType = new TypeToken<LinkedHashSet<String>>() {
                        }.getType();
                        Set<String> collapsedGroups = GSON.fromJson(obj.get("modernDashboardCollapsedGroups"), setType);
                        if (collapsedGroups != null) {
                            data.modernDashboardCollapsedGroups.addAll(collapsedGroups);
                        }
                    }
                    if (obj.has("modernDashboardViews") && obj.get("modernDashboardViews").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, DashboardViewMeta>>() {
                        }.getType();
                        Map<String, DashboardViewMeta> views = GSON.fromJson(obj.get("modernDashboardViews"), mapType);
                        if (views != null) {
                            data.modernDashboardViews.putAll(views);
                        }
                    }

                    if (obj.has("autoFollowNavigationTop")) data.autoFollowNavigationTop = obj.get("autoFollowNavigationTop").getAsBoolean();
                    if (obj.has("autoFollowNavigationText")) data.autoFollowNavigationText = obj.get("autoFollowNavigationText").getAsBoolean();
                    data.modernTabStripVertical = readBoolean(obj, "modernTabStripVertical", false);
                    data.modernTabStripWidth = readInt(obj, "modernTabStripWidth", -1);
                    data.modernTabStripHeight = readInt(obj, "modernTabStripHeight", -1);
                    data.modernHeaderCollapsed = readBoolean(obj, "modernHeaderCollapsed", false);
                    if (obj.has("modernPinnedTools") && obj.get("modernPinnedTools").isJsonArray()) {
                        Type setType = new TypeToken<LinkedHashSet<String>>() {
                        }.getType();
                        Set<String> loadedTools = GSON.fromJson(obj.get("modernPinnedTools"), setType);
                        if (loadedTools != null) {
                            for (String tool : loadedTools) {
                                String normalizedTool = normalize(tool);
                                if (!normalizedTool.isEmpty()) {
                                    data.modernPinnedTools.add(normalizedTool);
                                }
                            }
                        }
                    }
                    if (obj.has("modernSplitRatios") && obj.get("modernSplitRatios").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, Double>>() {
                        }.getType();
                        Map<String, Double> loadedMap = GSON.fromJson(obj.get("modernSplitRatios"), mapType);
                        if (loadedMap != null) {
                            data.modernSplitRatios.putAll(loadedMap);
                        }
                    }
                    data.modernStateRecordingEnabled = readBoolean(obj, "modernStateRecordingEnabled", true);
                    if (obj.has("modernStateRecordingPages") && obj.get("modernStateRecordingPages").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, Boolean>>() { }.getType();
                        Map<String, Boolean> loadedMap = GSON.fromJson(obj.get("modernStateRecordingPages"), mapType);
                        if (loadedMap != null) data.modernStateRecordingPages.putAll(loadedMap);
                    }
                    if (obj.has("modernPageStates") && obj.get("modernPageStates").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("modernPageStates").entrySet()) {
                            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                                data.modernPageStates.put(entry.getKey(), entry.getValue().getAsJsonObject());
                            }
                        }
                    }

                    if (obj.has("sequenceOpenStats") && obj.get("sequenceOpenStats").isJsonObject()) {
                        Type mapType = new TypeToken<LinkedHashMap<String, SequenceOpenStats>>() {
                        }.getType();
                        Map<String, SequenceOpenStats> loadedMap = GSON.fromJson(obj.get("sequenceOpenStats"), mapType);
                        if (loadedMap != null) {
                            data.sequenceOpenStats.putAll(loadedMap);
                        }
                    }
                    if (obj.has("sequenceFavorites") && obj.get("sequenceFavorites").isJsonArray()) {
                        Type setType = new TypeToken<LinkedHashSet<String>>() {
                        }.getType();
                        Set<String> loadedFavorites = GSON.fromJson(obj.get("sequenceFavorites"), setType);
                        if (loadedFavorites != null) {
                            data.sequenceFavorites.addAll(loadedFavorites);
                        }
                    }
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("[main_ui_layout] 读取主界面布局失败: {}", file, e);
            }
        }

        if (migratedLegacyModernUiScale) {
            save();
        }

        sanitizeModernDashboardPreferences();
        synchronizeWithSequences(true);
        ModConfig.autoPauseOnMenuOpen = data.modernAutoPauseOnMenuOpen;
    }

    public static synchronized void reload() {
        loaded = false;
        ensureLoaded();
    }

    private static synchronized void synchronizeWithSequences(boolean saveAfterSync) {
        boolean changed = false;
        Set<String> customSequenceNames = new LinkedHashSet<>();
        for (PathSequence sequence : PathSequenceManager.getAllSequences()) {
            if (sequence == null || !sequence.isCustom()) {
                continue;
            }
            customSequenceNames.add(normalize(sequence.getName()));

            String category = normalize(sequence.getCategory());
            if (category.isEmpty()) {
                continue;
            }

            GroupMeta meta = data.groupMeta.get(category);
            if (meta == null) {
                data.groupMeta.put(category, new GroupMeta());
                changed = true;
            } else {
                changed |= sanitizeMeta(meta);
            }

            String subCategory = normalize(sequence.getSubCategory());
            if (!subCategory.isEmpty()) {
                changed |= addSubCategoryInternal(category, subCategory, false);
            }
        }

        if (data.sequenceFavorites.removeIf(name -> !customSequenceNames.contains(name))) {
            changed = true;
        }

        if (saveAfterSync && changed) {
            save();
        }
    }

    private static boolean sanitizeMeta(GroupMeta meta) {
        boolean changed = false;
        if (!isSupportedSortMode(meta.sortMode)) {
            meta.sortMode = SORT_DEFAULT;
            changed = true;
        }
        if (!isSupportedLayoutMode(meta.layoutMode)) {
            meta.layoutMode = LAYOUT_TILE;
            changed = true;
        }
        if (!isSupportedIconSize(meta.iconSize)) {
            meta.iconSize = ICON_DEFAULT;
            changed = true;
        }
        return changed;
    }

    private static boolean isSupportedSortMode(String sortMode) {
        return SORT_DEFAULT.equals(sortMode)
                || SORT_ALPHABETICAL.equals(sortMode)
                || SORT_LAST_OPENED.equals(sortMode)
                || SORT_OPEN_COUNT.equals(sortMode);
    }

    private static boolean isSupportedLayoutMode(String layoutMode) {
        return LAYOUT_TILE.equals(layoutMode)
                || LAYOUT_LIST.equals(layoutMode)
                || LAYOUT_COMPACT.equals(layoutMode)
                || LAYOUT_WIDE.equals(layoutMode);
    }

    private static boolean isSupportedIconSize(String iconSize) {
        return ICON_DEFAULT.equals(iconSize)
                || ICON_XL.equals(iconSize)
                || ICON_LARGE.equals(iconSize)
                || ICON_MEDIUM.equals(iconSize)
                || ICON_SMALL.equals(iconSize);
    }

    public static synchronized void save() {
        ensureLoaded();
        Path file = getLayoutFile();
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.addProperty("categoryPanelBaseWidth", data.categoryPanelBaseWidth);
                root.addProperty("modernSidebarWidth", data.modernSidebarWidth);
                root.addProperty("modernWindowXRatio", data.modernWindowXRatio);
                root.addProperty("modernWindowYRatio", data.modernWindowYRatio);
                root.addProperty("modernWindowWidthRatio", data.modernWindowWidthRatio);
                root.addProperty("modernWindowHeightRatio", data.modernWindowHeightRatio);
                root.addProperty("modernDetachedWindowX", data.modernDetachedWindowX);
                root.addProperty("modernDetachedWindowY", data.modernDetachedWindowY);
                root.addProperty("modernDetachedWindowWidth", data.modernDetachedWindowWidth);
                root.addProperty("modernDetachedWindowHeight", data.modernDetachedWindowHeight);
                root.addProperty("modernDetachedWindowMaximized", data.modernDetachedWindowMaximized);
                root.addProperty("modernUiScalePercent", data.modernUiScalePercent);
                root.addProperty("modernDetachedUiScalePercent", data.modernDetachedUiScalePercent);
                root.addProperty("modernAutoFocus", data.modernAutoFocus);
                root.addProperty("modernAutoPauseOnMenuOpen", data.modernAutoPauseOnMenuOpen);
                root.addProperty("modernRunningStatusXRatio", data.modernRunningStatusXRatio);
                root.addProperty("modernRunningStatusYRatio", data.modernRunningStatusYRatio);
                root.addProperty("modernRunningStatusWidthRatio", data.modernRunningStatusWidthRatio);
                root.addProperty("modernRunningStatusHeightRatio", data.modernRunningStatusHeightRatio);
                root.addProperty("modernRunningStatusVisible", data.modernRunningStatusVisible);
                root.addProperty("modernDashboardGrouping", data.modernDashboardGrouping);
                root.addProperty("modernDashboardSortMode", data.modernDashboardSortMode);
                root.addProperty("modernDashboardIconSize", data.modernDashboardIconSize);
                root.addProperty("modernDashboardShowIcons", data.modernDashboardShowIcons);
                root.addProperty("modernDashboardCardWidth", data.modernDashboardCardWidth);
                root.addProperty("modernDashboardCardHeight", data.modernDashboardCardHeight);
                root.addProperty("modernDashboardTextScalePercent", data.modernDashboardTextScalePercent);
                root.addProperty("modernDashboardTextAlign", data.modernDashboardTextAlign);
                root.addProperty("modernDashboardTextVerticalAlign", data.modernDashboardTextVerticalAlign);
                root.addProperty("modernDashboardInfoMode", data.modernDashboardInfoMode);
                root.addProperty("modernDashboardSettingsPlacement", data.modernDashboardSettingsPlacement);
                root.addProperty("modernDashboardSettingsWidth", data.modernDashboardSettingsWidth);
                root.addProperty("modernDashboardColumns", data.modernDashboardColumns);
                root.addProperty("modernDashboardFlowMode", data.modernDashboardFlowMode);
                root.add("modernDashboardCollapsedGroups", GSON.toJsonTree(data.modernDashboardCollapsedGroups));
                root.add("modernDashboardViews", GSON.toJsonTree(data.modernDashboardViews));
                root.addProperty("autoFollowNavigationTop", data.autoFollowNavigationTop);
                root.addProperty("autoFollowNavigationText", data.autoFollowNavigationText);
                root.addProperty("modernTabStripVertical", data.modernTabStripVertical);
                root.addProperty("modernTabStripWidth", data.modernTabStripWidth);
                root.addProperty("modernTabStripHeight", data.modernTabStripHeight);
                root.addProperty("modernHeaderCollapsed", data.modernHeaderCollapsed);
                root.add("modernPinnedTools", GSON.toJsonTree(data.modernPinnedTools));
                root.add("modernSplitRatios", GSON.toJsonTree(data.modernSplitRatios));
                root.addProperty("modernStateRecordingEnabled", data.modernStateRecordingEnabled);
                root.add("modernStateRecordingPages", GSON.toJsonTree(data.modernStateRecordingPages));
                root.add("modernPageStates", GSON.toJsonTree(data.modernPageStates));
                root.add("groupMeta", GSON.toJsonTree(data.groupMeta));
                root.add("subCategories", GSON.toJsonTree(data.subCategories));
                root.add("sequenceOpenStats", GSON.toJsonTree(data.sequenceOpenStats));
                root.add("sequenceFavorites", GSON.toJsonTree(data.sequenceFavorites));
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.warn("[main_ui_layout] 保存主界面布局失败: {}", file, e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        try {
            double value = object.has(key) ? object.get(key).getAsDouble() : fallback;
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            String value = object.has(key) ? object.get(key).getAsString() : fallback;
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void sanitizeModernDashboardPreferences() {
        if (!SORT_DEFAULT.equals(data.modernDashboardSortMode)
                && !SORT_ALPHABETICAL.equals(data.modernDashboardSortMode)
                && !SORT_ACTIVE_FIRST.equals(data.modernDashboardSortMode)) {
            data.modernDashboardSortMode = SORT_DEFAULT;
        }
        if (!isSupportedIconSize(data.modernDashboardIconSize)) {
            data.modernDashboardIconSize = ICON_MEDIUM;
        }
        data.modernDashboardCardWidth = sanitizeDashboardCardWidth(data.modernDashboardCardWidth);
        data.modernDashboardCardHeight = sanitizeDashboardCardHeight(data.modernDashboardCardHeight);
        data.modernDashboardTextScalePercent = sanitizeDashboardTextScalePercent(
                data.modernDashboardTextScalePercent);
        if (!isSupportedDashboardTextAlign(data.modernDashboardTextAlign)) {
            data.modernDashboardTextAlign = DASHBOARD_TEXT_ALIGN_CENTER;
        }
        if (!isSupportedDashboardTextVerticalAlign(data.modernDashboardTextVerticalAlign)) {
            data.modernDashboardTextVerticalAlign = DASHBOARD_TEXT_VERTICAL_CENTER;
        }
        if (!isSupportedDashboardInfoMode(data.modernDashboardInfoMode)) {
            data.modernDashboardInfoMode = DASHBOARD_INFO_ICON;
        }
        if (!isSupportedDashboardSettingsPlacement(data.modernDashboardSettingsPlacement)) {
            data.modernDashboardSettingsPlacement = DASHBOARD_SETTINGS_FLOATING;
        }
        data.modernDashboardSettingsWidth = sanitizeDashboardSettingsWidth(data.modernDashboardSettingsWidth);
        data.modernDashboardColumns = Math.max(0, Math.min(8, data.modernDashboardColumns));
        if (!FLOW_WATERFALL.equals(data.modernDashboardFlowMode)
                && !FLOW_PAGED.equals(data.modernDashboardFlowMode)) {
            data.modernDashboardFlowMode = FLOW_WATERFALL;
        }
        data.modernDashboardCollapsedGroups.removeIf(value -> normalize(value).isEmpty());
        data.modernDashboardViews.entrySet().removeIf(entry -> normalize(entry.getKey()).isEmpty()
                || entry.getValue() == null);
        for (DashboardViewMeta view : data.modernDashboardViews.values()) {
            sanitizeDashboardViewMeta(view);
        }
    }

    private static void sanitizeDashboardViewMeta(DashboardViewMeta view) {
        if (view == null) {
            return;
        }
        if (!SORT_DEFAULT.equals(view.sortMode) && !SORT_ALPHABETICAL.equals(view.sortMode)
                && !SORT_ACTIVE_FIRST.equals(view.sortMode)) {
            view.sortMode = SORT_DEFAULT;
        }
        if (!isSupportedIconSize(view.iconSize)) {
            view.iconSize = ICON_MEDIUM;
        }
        view.cardWidth = sanitizeDashboardCardWidth(view.cardWidth);
        view.cardHeight = sanitizeDashboardCardHeight(view.cardHeight);
        view.textScalePercent = sanitizeDashboardTextScalePercent(view.textScalePercent);
        if (!isSupportedDashboardTextAlign(view.textAlign)) {
            view.textAlign = DASHBOARD_TEXT_ALIGN_CENTER;
        }
        if (!isSupportedDashboardTextVerticalAlign(view.textVerticalAlign)) {
            view.textVerticalAlign = DASHBOARD_TEXT_VERTICAL_CENTER;
        }
        if (!isSupportedDashboardInfoMode(view.infoMode)) {
            view.infoMode = DASHBOARD_INFO_ICON;
        }
        view.columns = Math.max(0, Math.min(8, view.columns));
        if (!FLOW_WATERFALL.equals(view.flowMode) && !FLOW_PAGED.equals(view.flowMode)) {
            view.flowMode = FLOW_WATERFALL;
        }
        if (view.collapsedGroups == null) {
            view.collapsedGroups = new LinkedHashSet<>();
        } else {
            view.collapsedGroups.removeIf(value -> normalize(value).isEmpty());
        }
    }

    private static boolean isFiniteRatio(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0D && value <= 1.0D;
    }

    private static int sanitizeDashboardCardWidth(int width) {
        return width <= DASHBOARD_CARD_DIMENSION_AUTO ? DASHBOARD_CARD_DIMENSION_AUTO
                : Math.max(MIN_DASHBOARD_CARD_WIDTH, Math.min(MAX_DASHBOARD_CARD_WIDTH, width));
    }

    private static int sanitizeDashboardCardHeight(int height) {
        return height <= DASHBOARD_CARD_DIMENSION_AUTO ? DASHBOARD_CARD_DIMENSION_AUTO
                : Math.max(MIN_DASHBOARD_CARD_HEIGHT, Math.min(MAX_DASHBOARD_CARD_HEIGHT, height));
    }

    private static int sanitizeDashboardTextScalePercent(int percent) {
        return Math.max(MIN_DASHBOARD_TEXT_SCALE_PERCENT,
                Math.min(MAX_DASHBOARD_TEXT_SCALE_PERCENT, percent));
    }

    private static boolean isSupportedDashboardTextAlign(String align) {
        return DASHBOARD_TEXT_ALIGN_LEFT.equals(align)
                || DASHBOARD_TEXT_ALIGN_CENTER.equals(align)
                || DASHBOARD_TEXT_ALIGN_RIGHT.equals(align);
    }

    private static boolean isSupportedDashboardTextVerticalAlign(String align) {
        return DASHBOARD_TEXT_VERTICAL_TOP.equals(align)
                || DASHBOARD_TEXT_VERTICAL_CENTER.equals(align)
                || DASHBOARD_TEXT_VERTICAL_BOTTOM.equals(align);
    }

    private static boolean isSupportedDashboardInfoMode(String infoMode) {
        return DASHBOARD_INFO_ICON.equals(infoMode)
                || DASHBOARD_INFO_INLINE.equals(infoMode)
                || DASHBOARD_INFO_HIDDEN.equals(infoMode);
    }

    private static boolean isSupportedDashboardSettingsPlacement(String placement) {
        return DASHBOARD_SETTINGS_FLOATING.equals(placement)
                || DASHBOARD_SETTINGS_LEFT.equals(placement)
                || DASHBOARD_SETTINGS_RIGHT.equals(placement);
    }

    private static int sanitizeDashboardSettingsWidth(int width) {
        return Math.max(MIN_DASHBOARD_SETTINGS_WIDTH,
                Math.min(MAX_DASHBOARD_SETTINGS_WIDTH, width));
    }

    public static synchronized GroupMeta getGroupMeta(String category) {
        ensureLoaded();
        String normalized = normalize(category);
        GroupMeta meta = data.groupMeta.get(normalized);
        if (meta == null) {
            meta = new GroupMeta();
            data.groupMeta.put(normalized, meta);
            save();
        } else {
            sanitizeMeta(meta);
        }
        return meta.copy();
    }

    private static synchronized GroupMeta getMutableGroupMeta(String category) {
        ensureLoaded();
        String normalized = normalize(category);
        GroupMeta meta = data.groupMeta.get(normalized);
        if (meta == null) {
            meta = new GroupMeta();
            data.groupMeta.put(normalized, meta);
        } else {
            sanitizeMeta(meta);
        }
        return meta;
    }

    public static synchronized boolean isPinned(String category) {
        return getGroupMeta(category).pinned;
    }

    public static synchronized boolean isCollapsed(String category) {
        return getGroupMeta(category).collapsed;
    }

    public static synchronized void setPinned(String category, boolean pinned) {
        GroupMeta meta = getMutableGroupMeta(category);
        if (meta.pinned != pinned) {
            meta.pinned = pinned;
            save();
        }
    }

    public static synchronized void togglePinned(String category) {
        GroupMeta meta = getMutableGroupMeta(category);
        meta.pinned = !meta.pinned;
        save();
    }

    public static synchronized void setCollapsed(String category, boolean collapsed) {
        GroupMeta meta = getMutableGroupMeta(category);
        if (meta.collapsed != collapsed) {
            meta.collapsed = collapsed;
            save();
        }
    }

    public static synchronized void toggleCollapsed(String category) {
        GroupMeta meta = getMutableGroupMeta(category);
        meta.collapsed = !meta.collapsed;
        save();
    }

    public static synchronized void setCollapsedForCategories(Iterable<String> categories, boolean collapsed) {
        ensureLoaded();
        boolean changed = false;
        if (categories == null) {
            return;
        }
        for (String category : categories) {
            String normalized = normalize(category);
            if (normalized.isEmpty()) {
                continue;
            }
            GroupMeta meta = getMutableGroupMeta(normalized);
            if (meta.collapsed != collapsed) {
                meta.collapsed = collapsed;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public static synchronized String getSortMode(String category) {
        return getGroupMeta(category).sortMode;
    }

    public static synchronized void setSortMode(String category, String sortMode) {
        if (!isSupportedSortMode(sortMode)) {
            return;
        }
        GroupMeta meta = getMutableGroupMeta(category);
        if (!sortMode.equals(meta.sortMode)) {
            meta.sortMode = sortMode;
            save();
        }
    }

    public static synchronized String getLayoutMode(String category) {
        return getGroupMeta(category).layoutMode;
    }

    public static synchronized void setLayoutMode(String category, String layoutMode) {
        if (!isSupportedLayoutMode(layoutMode)) {
            return;
        }
        GroupMeta meta = getMutableGroupMeta(category);
        if (!layoutMode.equals(meta.layoutMode)) {
            meta.layoutMode = layoutMode;
            save();
        }
    }

    public static synchronized String getIconSize(String category) {
        return getGroupMeta(category).iconSize;
    }

    public static synchronized void setIconSize(String category, String iconSize) {
        if (!isSupportedIconSize(iconSize)) {
            return;
        }
        GroupMeta meta = getMutableGroupMeta(category);
        if (!iconSize.equals(meta.iconSize)) {
            meta.iconSize = iconSize;
            save();
        }
    }

    public static synchronized boolean isModernAutoFocus() {
        ensureLoaded();
        return data.modernAutoFocus;
    }

    public static synchronized void setModernAutoFocus(boolean enabled) {
        ensureLoaded();
        if (data.modernAutoFocus != enabled) {
            data.modernAutoFocus = enabled;
            save();
        }
    }

    public static synchronized boolean isModernAutoPauseOnMenuOpen() {
        ensureLoaded();
        return data.modernAutoPauseOnMenuOpen;
    }

    public static synchronized void setModernAutoPauseOnMenuOpen(boolean enabled) {
        ensureLoaded();
        if (data.modernAutoPauseOnMenuOpen != enabled) {
            data.modernAutoPauseOnMenuOpen = enabled;
            ModConfig.autoPauseOnMenuOpen = enabled;
            save();
        }
    }

    public static synchronized RunningStatusLayout getModernRunningStatusLayout() {
        ensureLoaded();
        return new RunningStatusLayout(data.modernRunningStatusXRatio, data.modernRunningStatusYRatio,
                data.modernRunningStatusWidthRatio, data.modernRunningStatusHeightRatio);
    }

    public static synchronized void setModernRunningStatusLayout(double xRatio, double yRatio, double widthRatio,
            double heightRatio) {
        ensureLoaded();
        if (!isFiniteRatio(xRatio) || !isFiniteRatio(yRatio) || !isFiniteRatio(widthRatio)
                || !isFiniteRatio(heightRatio) || widthRatio <= 0.0D || heightRatio <= 0.0D) {
            return;
        }
        if (Math.abs(data.modernRunningStatusXRatio - xRatio) <= 0.0001D
                && Math.abs(data.modernRunningStatusYRatio - yRatio) <= 0.0001D
                && Math.abs(data.modernRunningStatusWidthRatio - widthRatio) <= 0.0001D
                && Math.abs(data.modernRunningStatusHeightRatio - heightRatio) <= 0.0001D) {
            return;
        }
        data.modernRunningStatusXRatio = xRatio;
        data.modernRunningStatusYRatio = yRatio;
        data.modernRunningStatusWidthRatio = widthRatio;
        data.modernRunningStatusHeightRatio = heightRatio;
        save();
    }

    public static synchronized boolean isModernRunningStatusVisible() {
        ensureLoaded();
        return data.modernRunningStatusVisible;
    }

    public static synchronized void setModernRunningStatusVisible(boolean visible) {
        ensureLoaded();
        if (data.modernRunningStatusVisible != visible) {
            data.modernRunningStatusVisible = visible;
            save();
        }
    }

    public static synchronized boolean isModernDashboardGrouping() {
        ensureLoaded();
        return data.modernDashboardGrouping;
    }

    public static synchronized void setModernDashboardGrouping(boolean enabled) {
        ensureLoaded();
        if (data.modernDashboardGrouping != enabled) {
            data.modernDashboardGrouping = enabled;
            save();
        }
    }

    public static synchronized String getModernDashboardSortMode() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardSortMode;
    }

    public static synchronized void setModernDashboardSortMode(String sortMode) {
        ensureLoaded();
        if ((!SORT_DEFAULT.equals(sortMode) && !SORT_ALPHABETICAL.equals(sortMode)
                && !SORT_ACTIVE_FIRST.equals(sortMode)) || sortMode.equals(data.modernDashboardSortMode)) {
            return;
        }
        data.modernDashboardSortMode = sortMode;
        save();
    }

    public static synchronized String getModernDashboardIconSize() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardIconSize;
    }

    public static synchronized void setModernDashboardIconSize(String iconSize) {
        ensureLoaded();
        if (!isSupportedIconSize(iconSize) || iconSize.equals(data.modernDashboardIconSize)) {
            return;
        }
        data.modernDashboardIconSize = iconSize;
        save();
    }

    public static synchronized boolean isModernDashboardShowIcons() {
        ensureLoaded();
        return data.modernDashboardShowIcons;
    }

    public static synchronized void setModernDashboardShowIcons(boolean enabled) {
        ensureLoaded();
        if (data.modernDashboardShowIcons != enabled) {
            data.modernDashboardShowIcons = enabled;
            save();
        }
    }

    public static synchronized int getModernDashboardCardWidth() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardCardWidth;
    }

    public static synchronized void setModernDashboardCardWidth(int width) {
        ensureLoaded();
        int sanitized = sanitizeDashboardCardWidth(width);
        if (data.modernDashboardCardWidth != sanitized) {
            data.modernDashboardCardWidth = sanitized;
            save();
        }
    }

    public static synchronized int getModernDashboardCardHeight() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardCardHeight;
    }

    public static synchronized void setModernDashboardCardHeight(int height) {
        ensureLoaded();
        int sanitized = sanitizeDashboardCardHeight(height);
        if (data.modernDashboardCardHeight != sanitized) {
            data.modernDashboardCardHeight = sanitized;
            save();
        }
    }

    public static synchronized int getModernDashboardTextScalePercent() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardTextScalePercent;
    }

    public static synchronized void setModernDashboardTextScalePercent(int percent) {
        ensureLoaded();
        int sanitized = sanitizeDashboardTextScalePercent(percent);
        if (data.modernDashboardTextScalePercent != sanitized) {
            data.modernDashboardTextScalePercent = sanitized;
            save();
        }
    }

    public static synchronized String getModernDashboardTextAlign() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardTextAlign;
    }

    public static synchronized void setModernDashboardTextAlign(String align) {
        ensureLoaded();
        if (!isSupportedDashboardTextAlign(align) || align.equals(data.modernDashboardTextAlign)) {
            return;
        }
        data.modernDashboardTextAlign = align;
        save();
    }

    public static synchronized String getModernDashboardTextVerticalAlign() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardTextVerticalAlign;
    }

    public static synchronized void setModernDashboardTextVerticalAlign(String align) {
        ensureLoaded();
        if (!isSupportedDashboardTextVerticalAlign(align)
                || align.equals(data.modernDashboardTextVerticalAlign)) {
            return;
        }
        data.modernDashboardTextVerticalAlign = align;
        save();
    }

    public static synchronized String getModernDashboardInfoMode() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardInfoMode;
    }

    public static synchronized void setModernDashboardInfoMode(String infoMode) {
        ensureLoaded();
        if (!isSupportedDashboardInfoMode(infoMode) || infoMode.equals(data.modernDashboardInfoMode)) {
            return;
        }
        data.modernDashboardInfoMode = infoMode;
        save();
    }

    public static synchronized String getModernDashboardSettingsPlacement() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardSettingsPlacement;
    }

    public static synchronized void setModernDashboardSettingsPlacement(String placement) {
        ensureLoaded();
        if (!isSupportedDashboardSettingsPlacement(placement)
                || placement.equals(data.modernDashboardSettingsPlacement)) {
            return;
        }
        data.modernDashboardSettingsPlacement = placement;
        save();
    }

    public static synchronized int getModernDashboardSettingsWidth() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardSettingsWidth;
    }

    public static synchronized void setModernDashboardSettingsWidth(int width) {
        ensureLoaded();
        int sanitized = sanitizeDashboardSettingsWidth(width);
        if (data.modernDashboardSettingsWidth != sanitized) {
            data.modernDashboardSettingsWidth = sanitized;
            save();
        }
    }

    public static synchronized int getModernDashboardColumns() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardColumns;
    }

    public static synchronized void setModernDashboardColumns(int columns) {
        ensureLoaded();
        int sanitized = Math.max(0, Math.min(8, columns));
        if (data.modernDashboardColumns != sanitized) {
            data.modernDashboardColumns = sanitized;
            save();
        }
    }

    public static synchronized String getModernDashboardFlowMode() {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        return data.modernDashboardFlowMode;
    }

    public static synchronized void setModernDashboardFlowMode(String flowMode) {
        ensureLoaded();
        if ((!FLOW_WATERFALL.equals(flowMode) && !FLOW_PAGED.equals(flowMode))
                || flowMode.equals(data.modernDashboardFlowMode)) {
            return;
        }
        data.modernDashboardFlowMode = flowMode;
        save();
    }

    public static synchronized boolean isModernDashboardGroupCollapsed(String groupKey) {
        ensureLoaded();
        return data.modernDashboardCollapsedGroups.contains(normalize(groupKey));
    }

    public static synchronized void toggleModernDashboardGroupCollapsed(String groupKey) {
        ensureLoaded();
        String normalized = normalize(groupKey);
        if (normalized.isEmpty()) {
            return;
        }
        if (!data.modernDashboardCollapsedGroups.remove(normalized)) {
            data.modernDashboardCollapsedGroups.add(normalized);
        }
        save();
    }

    public static synchronized boolean isModernDashboardUsingGlobal(String selectionKey) {
        ensureLoaded();
        return !data.modernDashboardViews.containsKey(normalize(selectionKey));
    }

    public static synchronized void setModernDashboardUsingGlobal(String selectionKey, boolean useGlobal) {
        ensureLoaded();
        String key = normalize(selectionKey);
        if (key.isEmpty()) {
            return;
        }
        if (useGlobal) {
            if (data.modernDashboardViews.remove(key) != null) {
                save();
            }
            return;
        }
        if (!data.modernDashboardViews.containsKey(key)) {
            data.modernDashboardViews.put(key, globalDashboardView());
            save();
        }
    }

    public static synchronized DashboardViewMeta getModernDashboardView(String selectionKey) {
        ensureLoaded();
        sanitizeModernDashboardPreferences();
        DashboardViewMeta view = data.modernDashboardViews.get(normalize(selectionKey));
        return (view == null ? globalDashboardView() : view).copy();
    }

    public static synchronized void setModernDashboardGrouping(String selectionKey, boolean enabled) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardGrouping(enabled);
        } else if (view.grouping != enabled) {
            view.grouping = enabled;
            save();
        }
    }

    public static synchronized void setModernDashboardSortMode(String selectionKey, String sortMode) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardSortMode(sortMode);
        } else if ((SORT_DEFAULT.equals(sortMode) || SORT_ALPHABETICAL.equals(sortMode)
                || SORT_ACTIVE_FIRST.equals(sortMode)) && !sortMode.equals(view.sortMode)) {
            view.sortMode = sortMode;
            save();
        }
    }

    public static synchronized void setModernDashboardIconSize(String selectionKey, String iconSize) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardIconSize(iconSize);
        } else if (isSupportedIconSize(iconSize) && !iconSize.equals(view.iconSize)) {
            view.iconSize = iconSize;
            save();
        }
    }

    public static synchronized void setModernDashboardShowIcons(String selectionKey, boolean enabled) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardShowIcons(enabled);
        } else if (view.showIcons != enabled) {
            view.showIcons = enabled;
            save();
        }
    }

    public static synchronized void setModernDashboardCardWidth(String selectionKey, int width) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardCardWidth(width);
        } else {
            int sanitized = sanitizeDashboardCardWidth(width);
            if (view.cardWidth != sanitized) {
                view.cardWidth = sanitized;
                save();
            }
        }
    }

    public static synchronized void setModernDashboardCardHeight(String selectionKey, int height) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardCardHeight(height);
        } else {
            int sanitized = sanitizeDashboardCardHeight(height);
            if (view.cardHeight != sanitized) {
                view.cardHeight = sanitized;
                save();
            }
        }
    }

    public static synchronized void setModernDashboardTextScalePercent(String selectionKey, int percent) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardTextScalePercent(percent);
        } else {
            int sanitized = sanitizeDashboardTextScalePercent(percent);
            if (view.textScalePercent != sanitized) {
                view.textScalePercent = sanitized;
                save();
            }
        }
    }

    public static synchronized void setModernDashboardTextAlign(String selectionKey, String align) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardTextAlign(align);
        } else if (isSupportedDashboardTextAlign(align) && !align.equals(view.textAlign)) {
            view.textAlign = align;
            save();
        }
    }

    public static synchronized void setModernDashboardTextVerticalAlign(String selectionKey, String align) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardTextVerticalAlign(align);
        } else if (isSupportedDashboardTextVerticalAlign(align) && !align.equals(view.textVerticalAlign)) {
            view.textVerticalAlign = align;
            save();
        }
    }

    public static synchronized void setModernDashboardInfoMode(String selectionKey, String infoMode) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardInfoMode(infoMode);
        } else if (isSupportedDashboardInfoMode(infoMode) && !infoMode.equals(view.infoMode)) {
            view.infoMode = infoMode;
            save();
        }
    }

    public static synchronized void setModernDashboardColumns(String selectionKey, int columns) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardColumns(columns);
        } else {
            int sanitized = Math.max(0, Math.min(8, columns));
            if (view.columns != sanitized) {
                view.columns = sanitized;
                save();
            }
        }
    }

    public static synchronized void setModernDashboardFlowMode(String selectionKey, String flowMode) {
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        if (view == null) {
            setModernDashboardFlowMode(flowMode);
        } else if ((FLOW_WATERFALL.equals(flowMode) || FLOW_PAGED.equals(flowMode))
                && !flowMode.equals(view.flowMode)) {
            view.flowMode = flowMode;
            save();
        }
    }

    public static synchronized boolean isModernDashboardGroupCollapsed(String selectionKey, String groupKey) {
        return getModernDashboardView(selectionKey).collapsedGroups.contains(normalize(groupKey));
    }

    public static synchronized void toggleModernDashboardGroupCollapsed(String selectionKey, String groupKey) {
        ensureLoaded();
        String normalizedGroup = normalize(groupKey);
        if (normalizedGroup.isEmpty()) {
            return;
        }
        DashboardViewMeta view = mutableIndependentDashboardView(selectionKey);
        Set<String> collapsed = view == null ? data.modernDashboardCollapsedGroups : view.collapsedGroups;
        if (!collapsed.remove(normalizedGroup)) {
            collapsed.add(normalizedGroup);
        }
        save();
    }

    private static DashboardViewMeta mutableIndependentDashboardView(String selectionKey) {
        ensureLoaded();
        DashboardViewMeta view = data.modernDashboardViews.get(normalize(selectionKey));
        if (view != null) {
            sanitizeDashboardViewMeta(view);
        }
        return view;
    }

    private static DashboardViewMeta globalDashboardView() {
        DashboardViewMeta view = new DashboardViewMeta();
        view.grouping = data.modernDashboardGrouping;
        view.sortMode = data.modernDashboardSortMode;
        view.iconSize = data.modernDashboardIconSize;
        view.showIcons = data.modernDashboardShowIcons;
        view.cardWidth = data.modernDashboardCardWidth;
        view.cardHeight = data.modernDashboardCardHeight;
        view.textScalePercent = data.modernDashboardTextScalePercent;
        view.textAlign = data.modernDashboardTextAlign;
        view.textVerticalAlign = data.modernDashboardTextVerticalAlign;
        view.infoMode = data.modernDashboardInfoMode;
        view.columns = data.modernDashboardColumns;
        view.flowMode = data.modernDashboardFlowMode;
        view.collapsedGroups.addAll(data.modernDashboardCollapsedGroups);
        return view;
    }

    public static synchronized List<String> getSubCategories(String category) {
        ensureLoaded();
        String normalizedCategory = normalize(category);
        List<String> subCategories = data.subCategories.get(normalizedCategory);
        if (subCategories == null) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String subCategory : subCategories) {
            String normalized = normalize(subCategory);
            if (!normalized.isEmpty()) {
                dedup.add(normalized);
            }
        }
        return new ArrayList<>(dedup);
    }

    private static boolean addSubCategoryInternal(String category, String subCategory, boolean saveAfter) {
        String normalizedCategory = normalize(category);
        String normalizedSubCategory = normalize(subCategory);
        if (normalizedCategory.isEmpty() || normalizedSubCategory.isEmpty()) {
            return false;
        }

        List<String> list = data.subCategories.get(normalizedCategory);
        if (list == null) {
            list = new ArrayList<>();
            data.subCategories.put(normalizedCategory, list);
        }

        for (String existing : list) {
            if (normalizedSubCategory.equalsIgnoreCase(normalize(existing))) {
                return false;
            }
        }

        list.add(normalizedSubCategory);
        if (saveAfter) {
            save();
        }
        return true;
    }

    public static synchronized boolean addSubCategory(String category, String subCategory) {
        ensureLoaded();
        boolean changed = addSubCategoryInternal(category, subCategory, false);
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized boolean moveSubCategory(String category, String subCategoryToMove, String anchorSubCategory,
            boolean placeAfter) {
        ensureLoaded();
        String normalizedCategory = normalize(category);
        String normalizedMove = normalize(subCategoryToMove);
        String normalizedAnchor = normalize(anchorSubCategory);
        if (normalizedCategory.isEmpty() || normalizedMove.isEmpty() || normalizedAnchor.isEmpty()
                || normalizedMove.equalsIgnoreCase(normalizedAnchor)) {
            return false;
        }

        List<String> list = data.subCategories.get(normalizedCategory);
        if (list == null || list.size() < 2) {
            return false;
        }

        int moveIndex = -1;
        int anchorIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            String item = normalize(list.get(i));
            if (normalizedMove.equalsIgnoreCase(item)) {
                moveIndex = i;
            }
            if (normalizedAnchor.equalsIgnoreCase(item)) {
                anchorIndex = i;
            }
        }

        if (moveIndex < 0 || anchorIndex < 0 || moveIndex == anchorIndex) {
            return false;
        }

        String movingItem = list.remove(moveIndex);
        if (moveIndex < anchorIndex) {
            anchorIndex--;
        }
        int insertIndex = placeAfter ? anchorIndex + 1 : anchorIndex;
        insertIndex = Math.max(0, Math.min(insertIndex, list.size()));
        list.add(insertIndex, movingItem);
        save();
        return true;
    }

    public static synchronized boolean renameSubCategory(String category, String oldSubCategory, String newSubCategory) {
        ensureLoaded();
        String normalizedCategory = normalize(category);
        String normalizedOld = normalize(oldSubCategory);
        String normalizedNew = normalize(newSubCategory);
        if (normalizedCategory.isEmpty() || normalizedOld.isEmpty() || normalizedNew.isEmpty()
                || normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return false;
        }

        List<String> list = data.subCategories.get(normalizedCategory);
        if (list == null) {
            return false;
        }

        boolean exists = false;
        for (String item : list) {
            if (normalizedOld.equalsIgnoreCase(normalize(item))) {
                exists = true;
            }
            if (normalizedNew.equalsIgnoreCase(normalize(item))) {
                return false;
            }
        }
        if (!exists) {
            return false;
        }

        for (int i = 0; i < list.size(); i++) {
            if (normalizedOld.equalsIgnoreCase(normalize(list.get(i)))) {
                list.set(i, normalizedNew);
                break;
            }
        }

        List<PathSequence> allSequences = PathSequenceManager.getAllSequences();
        for (PathSequence sequence : allSequences) {
            if (sequence != null
                    && sequence.isCustom()
                    && normalizedCategory.equals(normalize(sequence.getCategory()))
                    && normalizedOld.equalsIgnoreCase(normalize(sequence.getSubCategory()))) {
                sequence.setSubCategory(normalizedNew);
            }
        }

        PathSequenceManager.saveAllSequences(allSequences);
        save();
        return true;
    }

    public static synchronized int deleteSubCategory(String category, String subCategory, boolean deleteSequences) {
        ensureLoaded();
        String normalizedCategory = normalize(category);
        String normalizedSubCategory = normalize(subCategory);
        if (normalizedCategory.isEmpty() || normalizedSubCategory.isEmpty()) {
            return 0;
        }

        List<String> list = data.subCategories.get(normalizedCategory);
        if (list != null) {
            list.removeIf(item -> normalizedSubCategory.equalsIgnoreCase(normalize(item)));
            if (list.isEmpty()) {
                data.subCategories.remove(normalizedCategory);
            }
        }

        int removedCount = 0;
        if (deleteSequences) {
            removedCount = PathSequenceManager.deleteCustomSequencesInSubCategory(normalizedCategory, normalizedSubCategory);
        }

        save();
        return removedCount;
    }

    public static synchronized void renameCategory(String oldCategory, String newCategory) {
        ensureLoaded();
        String normalizedOld = normalize(oldCategory);
        String normalizedNew = normalize(newCategory);
        if (normalizedOld.isEmpty() || normalizedNew.isEmpty() || normalizedOld.equals(normalizedNew)) {
            return;
        }

        GroupMeta meta = data.groupMeta.remove(normalizedOld);
        if (meta != null) {
            data.groupMeta.put(normalizedNew, meta);
        }

        List<String> subCategories = data.subCategories.remove(normalizedOld);
        if (subCategories != null) {
            data.subCategories.put(normalizedNew, subCategories);
        }

        save();
    }

    public static synchronized void removeCategory(String category) {
        ensureLoaded();
        String normalized = normalize(category);
        data.groupMeta.remove(normalized);
        data.subCategories.remove(normalized);
        save();
    }

    public static synchronized void recordSequenceOpened(String sequenceName) {
        ensureLoaded();
        String normalized = normalize(sequenceName);
        if (normalized.isEmpty()) {
            return;
        }

        SequenceOpenStats stats = data.sequenceOpenStats.get(normalized);
        if (stats == null) {
            stats = new SequenceOpenStats();
            data.sequenceOpenStats.put(normalized, stats);
        }
        stats.openCount++;
        stats.lastOpenedAt = System.currentTimeMillis();
        save();
    }

    public static synchronized SequenceOpenStats getSequenceStats(String sequenceName) {
        ensureLoaded();
        SequenceOpenStats stats = data.sequenceOpenStats.get(normalize(sequenceName));
        return stats == null ? new SequenceOpenStats() : stats.copy();
    }

    public static synchronized List<String> getSequencesByRecentUse() {
        ensureLoaded();
        List<String> names = new ArrayList<>(data.sequenceOpenStats.keySet());
        names.sort((left, right) -> {
            SequenceOpenStats a = data.sequenceOpenStats.get(left);
            SequenceOpenStats b = data.sequenceOpenStats.get(right);
            int byTime = Long.compare(b == null ? 0L : b.lastOpenedAt, a == null ? 0L : a.lastOpenedAt);
            return byTime != 0 ? byTime : left.compareToIgnoreCase(right);
        });
        return names;
    }

    public static synchronized List<String> getSequencesByOpenCount() {
        ensureLoaded();
        List<String> names = new ArrayList<>(data.sequenceOpenStats.keySet());
        names.sort((left, right) -> {
            SequenceOpenStats a = data.sequenceOpenStats.get(left);
            SequenceOpenStats b = data.sequenceOpenStats.get(right);
            int byCount = Integer.compare(b == null ? 0 : b.openCount, a == null ? 0 : a.openCount);
            if (byCount != 0) return byCount;
            int byTime = Long.compare(b == null ? 0L : b.lastOpenedAt, a == null ? 0L : a.lastOpenedAt);
            return byTime != 0 ? byTime : left.compareToIgnoreCase(right);
        });
        return names;
    }

    public static synchronized boolean isSequenceFavorite(String sequenceName) {
        ensureLoaded();
        return data.sequenceFavorites.contains(normalize(sequenceName));
    }

    public static synchronized void toggleSequenceFavorite(String sequenceName) {
        ensureLoaded();
        String normalized = normalize(sequenceName);
        if (normalized.isEmpty()) return;
        if (!data.sequenceFavorites.add(normalized)) {
            data.sequenceFavorites.remove(normalized);
        }
        save();
    }

    public static synchronized List<String> getFavoriteSequences() {
        ensureLoaded();
        List<String> names = new ArrayList<>(data.sequenceFavorites);
        names.sort((left, right) -> {
            SequenceOpenStats a = data.sequenceOpenStats.get(left);
            SequenceOpenStats b = data.sequenceOpenStats.get(right);
            int byCount = Integer.compare(b == null ? 0 : b.openCount, a == null ? 0 : a.openCount);
            if (byCount != 0) return byCount;
            int byTime = Long.compare(b == null ? 0L : b.lastOpenedAt, a == null ? 0L : a.lastOpenedAt);
            return byTime != 0 ? byTime : left.compareToIgnoreCase(right);
        });
        return names;
    }

    public static synchronized void removeSequenceStats(String sequenceName) {
        ensureLoaded();
        String normalized = normalize(sequenceName);
        boolean removed = data.sequenceOpenStats.remove(normalized) != null;
        removed |= data.sequenceFavorites.remove(normalized);
        if (removed) {
            save();
        }
    }

    public static synchronized void renameSequenceStats(String oldName, String newName) {
        ensureLoaded();
        String normalizedOld = normalize(oldName);
        String normalizedNew = normalize(newName);
        if (normalizedOld.isEmpty() || normalizedNew.isEmpty() || normalizedOld.equals(normalizedNew)) {
            return;
        }

        SequenceOpenStats stats = data.sequenceOpenStats.remove(normalizedOld);
        boolean changed = stats != null;
        if (stats != null) {
            data.sequenceOpenStats.put(normalizedNew, stats);
        }
        if (data.sequenceFavorites.remove(normalizedOld)) {
            data.sequenceFavorites.add(normalizedNew);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public static synchronized Set<String> getKnownCategories() {
        ensureLoaded();
        return new LinkedHashSet<>(data.groupMeta.keySet());
    }

    public static synchronized int getCategoryPanelBaseWidth() {
        ensureLoaded();
        return data.categoryPanelBaseWidth;
    }

    public static synchronized void setCategoryPanelBaseWidth(int width) {
        ensureLoaded();
        int normalized = width <= 0 ? -1 : width;
        if (data.categoryPanelBaseWidth != normalized) {
            data.categoryPanelBaseWidth = normalized;
            save();
        }
    }

    public static synchronized int getModernSidebarWidth() {
        ensureLoaded();
        return data.modernSidebarWidth;
    }

    public static synchronized void setModernSidebarWidth(int width) {
        ensureLoaded();
        int normalized = width <= 0 ? -1 : width;
        if (data.modernSidebarWidth != normalized) {
            data.modernSidebarWidth = normalized;
            save();
        }
    }

    public static synchronized ModernWindowLayout getModernWindowLayout() {
        ensureLoaded();
        return new ModernWindowLayout(data.modernWindowXRatio, data.modernWindowYRatio,
                data.modernWindowWidthRatio, data.modernWindowHeightRatio);
    }

    public static synchronized void setModernWindowLayout(double xRatio, double yRatio, double widthRatio,
            double heightRatio) {
        ensureLoaded();
        if (!isFiniteRatio(xRatio) || !isFiniteRatio(yRatio) || !isFiniteRatio(widthRatio)
                || !isFiniteRatio(heightRatio) || widthRatio <= 0.0D || heightRatio <= 0.0D) {
            return;
        }
        data.modernWindowXRatio = xRatio;
        data.modernWindowYRatio = yRatio;
        data.modernWindowWidthRatio = widthRatio;
        data.modernWindowHeightRatio = heightRatio;
        save();
    }

    public static synchronized void resetModernWindowLayout() {
        ensureLoaded();
        data.modernWindowXRatio = -1.0D;
        data.modernWindowYRatio = -1.0D;
        data.modernWindowWidthRatio = -1.0D;
        data.modernWindowHeightRatio = -1.0D;
        data.modernSidebarWidth = -1;
        save();
    }

    public static synchronized DetachedWindowLayout getDetachedWindowLayout() {
        ensureLoaded();
        return new DetachedWindowLayout(data.modernDetachedWindowX, data.modernDetachedWindowY,
                data.modernDetachedWindowWidth, data.modernDetachedWindowHeight,
                data.modernDetachedWindowMaximized, data.modernDetachedUiScalePercent);
    }

    public static synchronized void setDetachedWindowLayout(int x, int y, int width, int height,
            boolean maximized) {
        ensureLoaded();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (data.modernDetachedWindowX == x && data.modernDetachedWindowY == y
                && data.modernDetachedWindowWidth == width && data.modernDetachedWindowHeight == height
                && data.modernDetachedWindowMaximized == maximized) {
            return;
        }
        data.modernDetachedWindowX = x;
        data.modernDetachedWindowY = y;
        data.modernDetachedWindowWidth = width;
        data.modernDetachedWindowHeight = height;
        data.modernDetachedWindowMaximized = maximized;
        save();
    }

    public static synchronized void setDetachedUiScalePercent(int scalePercent) {
        ensureLoaded();
        int normalized = scalePercent < 0 ? -1
                : Math.max(MIN_UI_SCALE_PERCENT, Math.min(MAX_UI_SCALE_PERCENT, scalePercent));
        if (data.modernDetachedUiScalePercent != normalized) {
            data.modernDetachedUiScalePercent = normalized;
            save();
        }
    }

    /** Returns the explicit scale used by the in-game modern UI. */
    public static synchronized int getModernUiScalePercent() {
        ensureLoaded();
        return normalizeStoredUiScale(data.modernUiScalePercent, DEFAULT_MODERN_UI_SCALE_PERCENT);
    }

    /** Persists only the in-game modern UI scale. */
    public static synchronized void setModernUiScalePercent(int scalePercent) {
        ensureLoaded();
        int normalized = Math.max(MIN_UI_SCALE_PERCENT, Math.min(MAX_UI_SCALE_PERCENT, scalePercent));
        if (data.modernUiScalePercent != normalized) {
            data.modernUiScalePercent = normalized;
            save();
        }
    }

    /** Returns the explicit scale used by the detached Swing control center. */
    public static synchronized int getDetachedUiScalePercent() {
        ensureLoaded();
        return normalizeStoredUiScale(data.modernDetachedUiScalePercent, DEFAULT_DETACHED_UI_SCALE_PERCENT);
    }

    private static int normalizeStoredUiScale(int scalePercent, int fallback) {
        return scalePercent < MIN_UI_SCALE_PERCENT
                ? fallback : Math.max(MIN_UI_SCALE_PERCENT, Math.min(MAX_UI_SCALE_PERCENT, scalePercent));
    }

    public static synchronized boolean isAutoFollowNavigationTop() { ensureLoaded(); return data.autoFollowNavigationTop; }
    public static synchronized boolean isAutoFollowNavigationText() { ensureLoaded(); return data.autoFollowNavigationText; }
    public static synchronized void setAutoFollowNavigation(boolean top, boolean text) {
        ensureLoaded(); data.autoFollowNavigationTop = top; data.autoFollowNavigationText = text; save();
    }

    public static synchronized boolean isModernTabStripVertical() {
        ensureLoaded();
        return data.modernTabStripVertical;
    }

    public static synchronized void setModernTabStripVertical(boolean vertical) {
        ensureLoaded();
        if (data.modernTabStripVertical != vertical) {
            data.modernTabStripVertical = vertical;
            save();
        }
    }

    public static synchronized int getModernTabStripWidth() {
        ensureLoaded();
        return data.modernTabStripWidth;
    }

    public static synchronized void setModernTabStripWidth(int width) {
        ensureLoaded();
        int normalized = width <= 0 ? -1 : width;
        if (data.modernTabStripWidth != normalized) {
            data.modernTabStripWidth = normalized;
            save();
        }
    }

    public static synchronized int getModernTabStripHeight() {
        ensureLoaded();
        return data.modernTabStripHeight;
    }

    public static synchronized void setModernTabStripHeight(int height) {
        ensureLoaded();
        int normalized = height <= 0 ? -1 : height;
        if (data.modernTabStripHeight != normalized) {
            data.modernTabStripHeight = normalized;
            save();
        }
    }

    public static synchronized boolean isModernHeaderCollapsed() {
        ensureLoaded();
        return data.modernHeaderCollapsed;
    }

    public static synchronized void setModernHeaderCollapsed(boolean collapsed) {
        ensureLoaded();
        if (data.modernHeaderCollapsed != collapsed) {
            data.modernHeaderCollapsed = collapsed;
            save();
        }
    }

    public static synchronized Set<String> getModernPinnedTools() {
        ensureLoaded();
        return new LinkedHashSet<>(data.modernPinnedTools);
    }

    public static synchronized boolean isModernToolPinned(String action) {
        ensureLoaded();
        String normalized = normalize(action);
        return !normalized.isEmpty() && data.modernPinnedTools.contains(normalized);
    }

    public static synchronized void setModernToolPinned(String action, boolean pinned) {
        ensureLoaded();
        String normalized = normalize(action);
        if (normalized.isEmpty()) {
            return;
        }
        boolean changed = pinned ? data.modernPinnedTools.add(normalized) : data.modernPinnedTools.remove(normalized);
        if (changed) {
            save();
        }
    }

    public static synchronized void toggleModernToolPinned(String action) {
        ensureLoaded();
        String normalized = normalize(action);
        if (normalized.isEmpty()) {
            return;
        }
        if (!data.modernPinnedTools.remove(normalized)) {
            data.modernPinnedTools.add(normalized);
        }
        save();
    }

    public static synchronized double getModernSplitRatio(String key, double fallback) {
        ensureLoaded();
        String normalizedKey = normalize(key);
        double normalizedFallback = clampModernSplitRatio(fallback);
        if (normalizedKey.isEmpty()) {
            return normalizedFallback;
        }
        Double stored = data.modernSplitRatios.get(normalizedKey);
        return stored == null || !isFiniteRatio(stored.doubleValue())
                ? normalizedFallback
                : clampModernSplitRatio(stored.doubleValue());
    }

    public static synchronized void setModernSplitRatio(String key, double ratio) {
        ensureLoaded();
        String normalizedKey = normalize(key);
        if (normalizedKey.isEmpty() || !isFiniteRatio(ratio)) {
            return;
        }
        double normalizedRatio = clampModernSplitRatio(ratio);
        Double previous = data.modernSplitRatios.get(normalizedKey);
        if (previous == null || Math.abs(previous.doubleValue() - normalizedRatio) > 0.0001D) {
            data.modernSplitRatios.put(normalizedKey, normalizedRatio);
            save();
        }
    }

    public static synchronized boolean isModernStateRecordingEnabled() {
        ensureLoaded();
        return data.modernStateRecordingEnabled;
    }

    public static synchronized void setModernStateRecordingEnabled(boolean enabled) {
        ensureLoaded();
        if (data.modernStateRecordingEnabled != enabled) {
            data.modernStateRecordingEnabled = enabled;
            save();
        }
    }

    public static synchronized boolean isModernPageStateRecordingEnabled(String command) {
        ensureLoaded();
        if (!data.modernStateRecordingEnabled) return false;
        String key = normalize(command);
        if (key.isEmpty()) return false;
        Boolean value = data.modernStateRecordingPages.get(key);
        return value == null || value.booleanValue();
    }

    public static synchronized boolean getModernPageStateRecordingPreference(String command) {
        ensureLoaded();
        String key = normalize(command);
        Boolean value = data.modernStateRecordingPages.get(key);
        return value == null || value.booleanValue();
    }

    public static synchronized void setModernPageStateRecordingEnabled(String command, boolean enabled) {
        ensureLoaded();
        String key = normalize(command);
        if (key.isEmpty()) return;
        Boolean previous = data.modernStateRecordingPages.put(key, Boolean.valueOf(enabled));
        if (previous == null || previous.booleanValue() != enabled) save();
    }

    public static synchronized JsonObject getModernPageState(String command) {
        ensureLoaded();
        JsonObject value = data.modernPageStates.get(normalize(command));
        return value == null ? null : copyJsonObject(value);
    }

    public static synchronized void setModernPageState(String command, JsonObject state) {
        ensureLoaded();
        String key = normalize(command);
        if (key.isEmpty() || state == null) return;
        data.modernPageStates.put(key, copyJsonObject(state));
        save();
    }

    /** Removes remembered view state without changing per-page recording preferences. */
    public static synchronized void clearModernPageStates() {
        ensureLoaded();
        if (!data.modernPageStates.isEmpty()) {
            data.modernPageStates.clear();
            save();
        }
    }

    private static JsonObject copyJsonObject(JsonObject value) {
        return value == null ? null : new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    private static double clampModernSplitRatio(double ratio) {
        if (!isFiniteRatio(ratio)) {
            return 0.28D;
        }
        return Math.max(0.05D, Math.min(0.95D, ratio));
    }
}
