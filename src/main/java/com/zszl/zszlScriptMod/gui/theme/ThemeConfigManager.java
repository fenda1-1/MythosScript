package com.zszl.zszlScriptMod.gui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.modern.ModernThemeContrast;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ThemeConfigManager {
    private static final String BUILTIN_SCHEME = "builtin:";
    private static final String BUILTIN_SEASIDE_ID = "seaside";
    private static final String LEGACY_BUILTIN_PANEL_IMAGE = BUILTIN_SCHEME + "img/海边少女.jpg";
    // Minecraft 1.12.2 can corrupt non-ASCII ResourceLocation paths. Keep the
    // shipped asset name ASCII so both direct and async texture loading agree.
    private static final String BUILTIN_PANEL_IMAGE = BUILTIN_SCHEME + "img/seaside_girl.jpg";

    private ThemeConfigManager() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();
    private static final List<ThemeProfile> PROFILES = new ArrayList<>();
    private static int activeIndex = 0;
    private static boolean loaded = false;

    public static class ThemeProfile {
        public String name;
        public boolean builtIn = false;
        public String builtInId = "";

        public int panelBorder;
        public int panelBgTop;
        public int panelBgBottom;
        public int titleLeft;
        public int titleRight;
        public int titleText;
        public int labelText;
        public int subText;

        public int stateSuccess;
        public int stateWarning;
        public int stateDanger;
        public int stateDisabled;
        public int stateSelected;

        public int buttonBgNormal;
        public int buttonBgHover;
        public int buttonBgPressed;
        public int buttonBorderNormal;
        public int buttonBorderHover;

        public int inputBg;
        public int inputBorder;
        public int inputBorderHover;
        /** Caret color used by custom-rendered text fields. */
        public Integer inputCursor;

        public int getInputCursorColor() {
            if (inputCursor != null) return inputCursor;
            int background = ModernThemeContrast.composite(
                    inputBg, panelBgTop | 0xFF000000);
            return ModernThemeContrast.readable(
                    inputBorderHover | 0xFF000000, background);
        }

        public Integer cornerRadius = 6;
        public Integer borderWidth = 1;
        public Integer shadowOffset = 0;
        public int shadowColor = 0xFF000000;

        public int panelOpacityPercent = 45;
        public int buttonOpacityPercent = 100;
        public int inputOpacityPercent = 100;
        public int textOpacityPercent = 100;

        public String panelImagePath = "";
        public Boolean panelImageEnabled = Boolean.TRUE;
        @Deprecated public String buttonImagePath = "";
        @Deprecated public String inputImagePath = "";
        public int panelImageScale = 10;
        @Deprecated public int buttonImageScale = 10;
        @Deprecated public int inputImageScale = 10;

        public int panelCropX = 0;
        public int panelCropY = 0;
        @Deprecated public int buttonCropX = 0;
        @Deprecated public int buttonCropY = 0;
        @Deprecated public int inputCropX = 0;
        @Deprecated public int inputCropY = 0;

        public String panelImageQuality = "MEDIUM";
        @Deprecated public String buttonImageQuality = "MEDIUM";
        @Deprecated public String inputImageQuality = "MEDIUM";

        public static ThemeProfile fromCurrent(String name) {
            ThemeProfile p = new ThemeProfile();
            p.name = name;
            p.builtIn = false;
            p.builtInId = "";
            p.panelBorder = GuiTheme.PANEL_BORDER;
            p.panelBgTop = GuiTheme.PANEL_BG_TOP;
            p.panelBgBottom = GuiTheme.PANEL_BG_BOTTOM;
            p.titleLeft = GuiTheme.TITLE_LEFT;
            p.titleRight = GuiTheme.TITLE_RIGHT;
            p.titleText = GuiTheme.TITLE_TEXT;
            p.labelText = GuiTheme.LABEL_TEXT;
            p.subText = GuiTheme.SUB_TEXT;
            p.stateSuccess = GuiTheme.STATE_SUCCESS;
            p.stateWarning = GuiTheme.STATE_WARNING;
            p.stateDanger = GuiTheme.STATE_DANGER;
            p.stateDisabled = GuiTheme.STATE_DISABLED;
            p.stateSelected = GuiTheme.STATE_SELECTED;
            p.buttonBgNormal = GuiTheme.BUTTON_BG_NORMAL;
            p.buttonBgHover = GuiTheme.BUTTON_BG_HOVER;
            p.buttonBgPressed = GuiTheme.BUTTON_BG_PRESSED;
            p.buttonBorderNormal = GuiTheme.BUTTON_BORDER_NORMAL;
            p.buttonBorderHover = GuiTheme.BUTTON_BORDER_HOVER;
            p.inputBg = GuiTheme.INPUT_BG;
            p.inputBorder = GuiTheme.INPUT_BORDER;
            p.inputBorderHover = GuiTheme.INPUT_BORDER_HOVER;
            p.inputCursor = GuiTheme.INPUT_CURSOR;
            p.cornerRadius = GuiTheme.CORNER_RADIUS;
            p.borderWidth = GuiTheme.BORDER_WIDTH;
            p.shadowOffset = GuiTheme.SHADOW_OFFSET;
            p.shadowColor = GuiTheme.SHADOW_COLOR;
            p.panelOpacityPercent = GuiTheme.PANEL_OPACITY_PERCENT;
            p.buttonOpacityPercent = GuiTheme.BUTTON_OPACITY_PERCENT;
            p.inputOpacityPercent = GuiTheme.INPUT_OPACITY_PERCENT;
            p.textOpacityPercent = GuiTheme.TEXT_OPACITY_PERCENT;
            p.panelImagePath = GuiTheme.PANEL_IMAGE_PATH;
            p.panelImageEnabled = GuiTheme.PANEL_IMAGE_ENABLED;
            p.buttonImagePath = GuiTheme.BUTTON_IMAGE_PATH;
            p.inputImagePath = GuiTheme.INPUT_IMAGE_PATH;
            p.panelImageScale = GuiTheme.PANEL_IMAGE_SCALE;
            p.buttonImageScale = GuiTheme.BUTTON_IMAGE_SCALE;
            p.inputImageScale = GuiTheme.INPUT_IMAGE_SCALE;
            p.panelCropX = GuiTheme.PANEL_CROP_X;
            p.panelCropY = GuiTheme.PANEL_CROP_Y;
            p.buttonCropX = GuiTheme.BUTTON_CROP_X;
            p.buttonCropY = GuiTheme.BUTTON_CROP_Y;
            p.inputCropX = GuiTheme.INPUT_CROP_X;
            p.inputCropY = GuiTheme.INPUT_CROP_Y;
            p.panelImageQuality = GuiTheme.PANEL_IMAGE_QUALITY;
            p.buttonImageQuality = GuiTheme.BUTTON_IMAGE_QUALITY;
            p.inputImageQuality = GuiTheme.INPUT_IMAGE_QUALITY;
            return p;
        }

        public ThemeProfile copy() {
            ThemeProfile c = new ThemeProfile();
            c.name = this.name;
            c.builtIn = this.builtIn;
            c.builtInId = this.builtInId;
            c.panelBorder = this.panelBorder;
            c.panelBgTop = this.panelBgTop;
            c.panelBgBottom = this.panelBgBottom;
            c.titleLeft = this.titleLeft;
            c.titleRight = this.titleRight;
            c.titleText = this.titleText;
            c.labelText = this.labelText;
            c.subText = this.subText;
            c.stateSuccess = this.stateSuccess;
            c.stateWarning = this.stateWarning;
            c.stateDanger = this.stateDanger;
            c.stateDisabled = this.stateDisabled;
            c.stateSelected = this.stateSelected;
            c.buttonBgNormal = this.buttonBgNormal;
            c.buttonBgHover = this.buttonBgHover;
            c.buttonBgPressed = this.buttonBgPressed;
            c.buttonBorderNormal = this.buttonBorderNormal;
            c.buttonBorderHover = this.buttonBorderHover;
            c.inputBg = this.inputBg;
            c.inputBorder = this.inputBorder;
            c.inputBorderHover = this.inputBorderHover;
            c.inputCursor = this.inputCursor;
            c.cornerRadius = this.cornerRadius;
            c.borderWidth = this.borderWidth;
            c.shadowOffset = this.shadowOffset;
            c.shadowColor = this.shadowColor;
            c.panelOpacityPercent = this.panelOpacityPercent;
            c.buttonOpacityPercent = this.buttonOpacityPercent;
            c.inputOpacityPercent = this.inputOpacityPercent;
            c.textOpacityPercent = this.textOpacityPercent;
            c.panelImagePath = this.panelImagePath;
            c.panelImageEnabled = isPanelImageEnabled(this);
            c.buttonImagePath = this.buttonImagePath;
            c.inputImagePath = this.inputImagePath;
            c.panelImageScale = this.panelImageScale;
            c.buttonImageScale = this.buttonImageScale;
            c.inputImageScale = this.inputImageScale;
            c.panelCropX = this.panelCropX;
            c.panelCropY = this.panelCropY;
            c.buttonCropX = this.buttonCropX;
            c.buttonCropY = this.buttonCropY;
            c.inputCropX = this.inputCropX;
            c.inputCropY = this.inputCropY;
            c.panelImageQuality = this.panelImageQuality;
            c.buttonImageQuality = this.buttonImageQuality;
            c.inputImageQuality = this.inputImageQuality;
            return c;
        }
    }

    private static class ThemeStore {
        int activeIndex = 0;
        List<ThemeProfile> profiles = new ArrayList<>();
    }

    private static Path getFile() {
        return ProfileManager.getCurrentProfileDir().resolve("gui_themes.json");
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    public static void load() {
        PROFILES.clear();
        Path f = getFile();
        if (Files.exists(f)) {
            try (BufferedReader reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                ThemeStore store = GSON.fromJson(reader, ThemeStore.class);
                if (store != null && store.profiles != null && !store.profiles.isEmpty()) {
                    PROFILES.addAll(store.profiles);
                    activeIndex = Math.max(0, Math.min(store.activeIndex, PROFILES.size() - 1));
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("加载主题配置失败", e);
            }
        }

        if (!PROFILES.isEmpty() && isLegacyPresetPack(PROFILES)) {
            PROFILES.clear();
            PROFILES.addAll(buildDefaultPresets());
            activeIndex = 0;
        }

        if (PROFILES.isEmpty()) {
            PROFILES.addAll(buildDefaultPresets());
            activeIndex = 0;
        }

        if (normalizeProfilesState()) {
            save();
        }

        applyActiveProfile();
    }

    private static ThemeProfile buildPreset(String name, int border, int top, int bottom, int titleL, int titleR) {
        ThemeProfile p = ThemeProfile.fromCurrent(name);
        p.panelBorder = border;
        p.panelBgTop = top;
        p.panelBgBottom = bottom;
        p.titleLeft = titleL;
        p.titleRight = titleR;
        return p;
    }

    // Loaded lazily so the JSON codec and manager do not create an initialization cycle.
    private static final class BuiltInDefaults {
        private static final List<ThemeProfile> PROFILES = loadBuiltInDefaults();
    }

    private static List<ThemeProfile> loadBuiltInDefaults() {
        String resource = "/assets/zszl_script/themes/builtin_themes.json";
        try (java.io.InputStream stream = ThemeConfigManager.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("缺少内置主题资源：" + resource);
            try (java.io.Reader reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
                List<ThemeProfile> result = new ArrayList<>();
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
                    if (!entry.getKey().matches("[a-z0-9_]+")) {
                        throw new IllegalArgumentException("无效的内置主题 ID：" + entry.getKey());
                    }
                    ThemeProfile profile = ThemeJsonCodec.importTheme(entry.getValue().toString());
                    profile.builtIn = true;
                    profile.builtInId = entry.getKey();
                    result.add(profile);
                }
                if (result.isEmpty()) throw new IllegalStateException("内置主题资源不能为空");
                return java.util.Collections.unmodifiableList(result);
            }
        } catch (java.io.IOException | RuntimeException ex) {
            throw new IllegalStateException("加载内置主题 JSON 失败：" + resource, ex);
        }
    }

    private static List<ThemeProfile> buildDefaultPresets() {
        List<ThemeProfile> result = new ArrayList<>();
        for (ThemeProfile profile : BuiltInDefaults.PROFILES) result.add(profile.copy());
        return result;
    }

    private static ThemeProfile buildPresetFromRgb(String name, int r, int g, int b) {
        int base = 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        int dark = darken(base, 42);
        int mid = darken(base, 20);

        ThemeProfile p = ThemeProfile.fromCurrent(name);
        p.panelBorder = base;
        p.panelBgTop = withAlpha(mid, 0xD0);
        p.panelBgBottom = withAlpha(dark, 0xD0);
        p.titleLeft = base;
        p.titleRight = lighten(base, 26);

        p.buttonBgNormal = mid;
        p.buttonBgHover = lighten(mid, 20);
        p.buttonBgPressed = darken(mid, 24);
        p.buttonBorderNormal = lighten(mid, 18);
        p.buttonBorderHover = lighten(mid, 44);

        p.inputBg = darken(base, 52);
        p.inputBorder = darken(base, 16);
        p.inputBorderHover = lighten(base, 28);

        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        if (luminance > 170) {
            p.titleText = 0xFF1E242D;
            p.labelText = 0xFF1E242D;
            p.subText = 0xFF4A5563;
        } else {
            p.titleText = 0xFFF2F8FF;
            p.labelText = 0xFFE6EEF8;
            p.subText = 0xFF9FB2C8;
        }

        return p;
    }

    private static boolean isLegacyPresetPack(List<ThemeProfile> list) {
        if (list.isEmpty()) {
            return false;
        }
        for (ThemeProfile p : list) {
            String n = p == null ? "" : p.name;
            if (n == null) {
                n = "";
            }
            if (!("海蓝".equals(n) || n.matches("预设\\d+"))) {
                return false;
            }
        }
        return list.size() >= 8;
    }

    public static void save() {
        try {
            normalizeProfilesState();
            Path f = getFile();
            Files.createDirectories(f.getParent());
            ThemeStore store = new ThemeStore();
            store.activeIndex = activeIndex;
            store.profiles = new ArrayList<>(PROFILES);
            try (BufferedWriter writer = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存主题配置失败", e);
        }
    }

    public static List<ThemeProfile> getProfiles() {
        ensureLoaded();
        normalizeProfilesState();
        return PROFILES;
    }

    public static int getActiveIndex() {
        ensureLoaded();
        return activeIndex;
    }

    public static void setActiveIndex(int idx) {
        ensureLoaded();
        normalizeProfilesState();
        if (idx < 0 || idx >= PROFILES.size()) {
            return;
        }
        activeIndex = idx;
        applyActiveProfile();
    }

    public static ThemeProfile getActiveProfile() {
        ensureLoaded();
        normalizeProfilesState();
        if (PROFILES.isEmpty()) {
            ThemeProfile p = ThemeProfile.fromCurrent("默认主题");
            PROFILES.add(p);
        }
        activeIndex = Math.max(0, Math.min(activeIndex, PROFILES.size() - 1));
        return PROFILES.get(activeIndex);
    }

    public static void applyActiveProfile() {
        ensureLoaded();
        normalizeProfilesState();
        ThemeProfile p = getActiveProfile();
        GuiTheme.applyProfile(p);
    }

    public static ThemeProfile addRandomTheme(String name) {
        ThemeProfile p = createRandomProfile(name);
        PROFILES.add(p);
        activeIndex = PROFILES.size() - 1;
        applyActiveProfile();
        return p;
    }

    public static ThemeProfile createRandomProfile(String name) {
        ThemeProfile p = ThemeProfile.fromCurrent(name);
        randomizeProfile(p);
        return p;
    }

    public static void addProfile(ThemeProfile profile) {
        if (profile == null) {
            return;
        }
        clearBuiltInMetadata(profile);
        PROFILES.add(profile);
        activeIndex = PROFILES.size() - 1;
        applyActiveProfile();
    }

    public static void deleteIndices(List<Integer> indices) {
        ensureLoaded();
        if (indices == null || indices.isEmpty()) {
            return;
        }
        indices.sort((a, b) -> Integer.compare(b, a));
        for (int idx : indices) {
            if (idx >= 0 && idx < PROFILES.size() && !isBuiltInProfile(PROFILES.get(idx))) {
                PROFILES.remove(idx);
            }
        }
        if (PROFILES.isEmpty()) {
            PROFILES.addAll(buildDefaultPresets());
            activeIndex = 0;
        } else {
            activeIndex = Math.max(0, Math.min(activeIndex, PROFILES.size() - 1));
        }
        applyActiveProfile();
    }

    public static void randomizeProfile(ThemeProfile p) {
        int hueBase = RANDOM.nextInt(360);
        p.panelBgTop = hsvToArgb(200, hueBase, randBetween(0, 100), randBetween(0, 100));
        p.panelBgBottom = hsvToArgb(200, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.panelBorder = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.titleLeft = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.titleRight = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.titleText = 0xFFF4FAFF;
        p.labelText = 0xFFE8F1FF;
        p.subText = 0xFF9FB2C8;

        p.buttonBgNormal = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.buttonBgHover = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.buttonBgPressed = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.buttonBorderNormal = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.buttonBorderHover = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));

        p.stateSuccess = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.stateWarning = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.stateDanger = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.stateDisabled = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.stateSelected = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));

        p.inputBg = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.inputBorder = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.inputBorderHover = hsvToArgb(255, RANDOM.nextInt(360), randBetween(0, 100), randBetween(0, 100));
        p.inputCursor = null;
        p.inputCursor = p.getInputCursorColor();

        p.panelOpacityPercent = 45;
        p.buttonOpacityPercent = randBetween(10, 100);
        p.inputOpacityPercent = randBetween(10, 100);
        p.textOpacityPercent = randBetween(10, 100);

        // 随机抽卡不改图片缩放，保持当前/用户设置值
        p.panelCropX = randBetween(0, 300);
        p.panelCropY = randBetween(0, 300);
        p.buttonCropX = randBetween(0, 300);
        p.buttonCropY = randBetween(0, 300);
        p.inputCropX = randBetween(0, 300);
        p.inputCropY = randBetween(0, 300);
    }

    private static int randBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + RANDOM.nextInt(max - min + 1);
    }

    public static void ensureDefaultPresets() {
        ensureLoaded();
        if (normalizeProfilesState()) {
            save();
        }
    }

    @Deprecated
    public static void ensureTwentyPresets() {
        ensureDefaultPresets();
    }

    private static int lighten(int color, int delta) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >> 8) & 0xFF) + delta);
        int b = Math.min(255, (color & 0xFF) + delta);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int delta) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - delta);
        int g = Math.max(0, ((color >> 8) & 0xFF) - delta);
        int b = Math.max(0, (color & 0xFF) - delta);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    public static boolean isBuiltInProfile(ThemeProfile profile) {
        if (profile == null) {
            return false;
        }
        return profile.builtIn || !safeTrim(profile.builtInId).isEmpty();
    }

    public static boolean isPanelImageEnabled(ThemeProfile profile) {
        return profile == null || !Boolean.FALSE.equals(profile.panelImageEnabled);
    }

    public static ThemeProfile getBuiltInDefault(ThemeProfile profile) {
        int index = builtInIndex(builtInIdFor(profile));
        return index < 0 ? null : BuiltInDefaults.PROFILES.get(index).copy();
    }

    private static boolean normalizeProfilesState() {
        List<ThemeProfile> current = new ArrayList<>(PROFILES);
        ThemeProfile oldActive = (activeIndex >= 0 && activeIndex < current.size()) ? current.get(activeIndex) : null;
        List<ThemeProfile> normalized = new ArrayList<>();
        boolean changed = current.isEmpty();
        List<ThemeProfile> custom = new ArrayList<>();
        ThemeProfile[] builtIns = new ThemeProfile[BuiltInDefaults.PROFILES.size()];
        for (ThemeProfile profile : current) {
            if (profile == null) {
                changed = true;
                continue;
            }
            String builtInId = builtInIdFor(profile);
            int builtInIndex = builtInIndex(builtInId);
            if (builtInIndex >= 0) {
                if (builtIns[builtInIndex] != null) {
                    changed = true;
                    continue;
                }
                if (!profile.builtIn || !builtInId.equals(safeTrim(profile.builtInId))) {
                    profile.builtIn = true;
                    profile.builtInId = builtInId;
                    changed = true;
                }
                if (migrateLegacyBuiltinImagePath(profile)) {
                    changed = true;
                }
                if (clearObsoleteImageFields(profile)) {
                    changed = true;
                }
                if (profile.inputCursor == null) {
                    profile.inputCursor = BuiltInDefaults.PROFILES.get(builtInIndex).getInputCursorColor();
                    changed = true;
                }
                if (normalizeStyleFields(profile)) {
                    changed = true;
                }
                builtIns[builtInIndex] = profile;
                continue;
            }
            if (clearBuiltInMetadata(profile)) {
                changed = true;
            }
            if (migrateLegacyBuiltinImagePath(profile)) {
                changed = true;
            }
            if (clearObsoleteImageFields(profile)) {
                changed = true;
            }
            if (normalizeStyleFields(profile)) {
                changed = true;
            }
            custom.add(profile);
        }

        List<ThemeProfile> defaults = buildDefaultPresets();
        for (int i = 0; i < builtIns.length; i++) {
            ThemeProfile profile = builtIns[i];
            if (profile == null) {
                profile = defaults.get(i);
                changed = true;
            }
            normalized.add(profile);
        }
        normalized.addAll(custom);

        int newActiveIndex = 0;
        if (oldActive != null) {
            int idx = normalized.indexOf(oldActive);
            if (idx >= 0) {
                newActiveIndex = idx;
            }
        }
        newActiveIndex = Math.max(0, Math.min(newActiveIndex, normalized.size() - 1));

        if (!changed && (normalized.size() != PROFILES.size() || newActiveIndex != activeIndex)) {
            changed = true;
        }

        PROFILES.clear();
        PROFILES.addAll(normalized);
        activeIndex = newActiveIndex;
        return changed;
    }

    private static String builtInIdFor(ThemeProfile profile) {
        if (profile == null) {
            return "";
        }
        String builtInId = safeTrim(profile.builtInId).toLowerCase();
        if (builtInIndex(builtInId) >= 0) {
            return builtInId;
        }
        return "海边".equals(safeTrim(profile.name)) ? BUILTIN_SEASIDE_ID : "";
    }

    private static int builtInIndex(String builtInId) {
        for (int i = 0; i < BuiltInDefaults.PROFILES.size(); i++) {
            if (BuiltInDefaults.PROFILES.get(i).builtInId.equals(builtInId)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean normalizeStyleFields(ThemeProfile profile) {
        boolean changed = false;
        if (profile.cornerRadius == null) {
            profile.cornerRadius = 6;
            changed = true;
        }
        if (profile.borderWidth == null) {
            profile.borderWidth = 1;
            changed = true;
        }
        if (profile.shadowOffset == null) {
            profile.shadowOffset = 0;
            changed = true;
        }
        int cornerRadius = Math.max(0, Math.min(12, profile.cornerRadius));
        int borderWidth = Math.max(1, Math.min(4, profile.borderWidth));
        int shadowOffset = Math.max(0, Math.min(8, profile.shadowOffset));
        if (cornerRadius != profile.cornerRadius || borderWidth != profile.borderWidth
                || shadowOffset != profile.shadowOffset) {
            profile.cornerRadius = cornerRadius;
            profile.borderWidth = borderWidth;
            profile.shadowOffset = shadowOffset;
            changed = true;
        }
        if (profile.shadowColor == 0) {
            profile.shadowColor = 0xFF000000;
            changed = true;
        }
        if (profile.inputCursor == null) {
            profile.inputCursor = profile.getInputCursorColor();
            changed = true;
        }
        return changed;
    }

    private static boolean clearBuiltInMetadata(ThemeProfile profile) {
        if (profile == null) {
            return false;
        }
        boolean changed = profile.builtIn || !safeTrim(profile.builtInId).isEmpty();
        profile.builtIn = false;
        profile.builtInId = "";
        return changed;
    }

    private static boolean migrateLegacyBuiltinImagePath(ThemeProfile profile) {
        if (profile == null || !LEGACY_BUILTIN_PANEL_IMAGE.equals(safeTrim(profile.panelImagePath))) {
            return false;
        }
        profile.panelImagePath = BUILTIN_PANEL_IMAGE;
        return true;
    }

    private static boolean clearObsoleteImageFields(ThemeProfile profile) {
        if (profile == null) {
            return false;
        }
        boolean changed = !safeTrim(profile.buttonImagePath).isEmpty()
                || !safeTrim(profile.inputImagePath).isEmpty()
                || profile.buttonImageScale != 100 || profile.inputImageScale != 100
                || profile.buttonCropX != 0 || profile.buttonCropY != 0
                || profile.inputCropX != 0 || profile.inputCropY != 0
                || !"MEDIUM".equalsIgnoreCase(safeTrim(profile.buttonImageQuality))
                || !"MEDIUM".equalsIgnoreCase(safeTrim(profile.inputImageQuality));
        profile.buttonImagePath = "";
        profile.inputImagePath = "";
        profile.buttonImageScale = 100;
        profile.inputImageScale = 100;
        profile.buttonCropX = 0;
        profile.buttonCropY = 0;
        profile.inputCropX = 0;
        profile.inputCropY = 0;
        profile.buttonImageQuality = "MEDIUM";
        profile.inputImageQuality = "MEDIUM";
        return changed;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int hsvToArgb(int a, int h, int s, int v) {
        float hf = (h % 360) / 360f;
        float sf = Math.max(0f, Math.min(1f, s / 100f));
        float vf = Math.max(0f, Math.min(1f, v / 100f));
        int rgb = java.awt.Color.HSBtoRGB(hf, sf, vf);
        return (a & 0xFF) << 24 | (rgb & 0x00FFFFFF);
    }
}
