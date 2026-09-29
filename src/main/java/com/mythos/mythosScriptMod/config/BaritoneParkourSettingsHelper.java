package com.mythos.mythosScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.SettingsUtil;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class BaritoneParkourSettingsHelper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> PARKOUR_SETTING_KEYS = new LinkedHashSet<>(Arrays.asList(
            "parkourMode",
            "parkourProfile",
            "parkourDebugRender",
            "parkourDebugLog",
            "allowParkour",
            "allowParkourPlace",
            "allowParkourAscend"
    ));
    private static final String FREE_LOOK_KEY = "freelook";
    private static ParkourRuntimeState runtimeState;

    private BaritoneParkourSettingsHelper() {
    }

    public static boolean isParkourSettingKey(String key) {
        if (key == null) {
            return false;
        }
        for (String parkourKey : PARKOUR_SETTING_KEYS) {
            if (parkourKey.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isParkourModeEnabled() {
        Settings settings = BaritoneAPI.getSettings();
        return settings != null && settings.parkourMode.value;
    }

    public static synchronized void setParkourModeEnabled(boolean enabled) {
        Settings settings = BaritoneAPI.getSettings();
        if (settings == null) {
            return;
        }
        settings.parkourMode.value = enabled;
        onSettingApplied("parkourMode", String.valueOf(enabled));
        SettingsUtil.save(settings);
    }

    public static void toggleParkourMode() {
        setParkourModeEnabled(!isParkourModeEnabled());
    }

    public static Set<String> getParkourSettingKeys() {
        return new LinkedHashSet<>(PARKOUR_SETTING_KEYS);
    }

    public static String buildStatusText() {
        return isParkourModeEnabled() ? "§aBaritone跑酷: 开" : "§cBaritone跑酷: 关";
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    public static synchronized void onSettingApplied(String key, String rawValue) {
        applyRuntimeOverrides(BaritoneAPI.getSettings(), normalizeKey(key), rawValue);
    }

    public static synchronized void syncRuntimeOverrides() {
        applyRuntimeOverrides(BaritoneAPI.getSettings(), "", null);
    }

    private static void applyRuntimeOverrides(Settings settings, String changedKey, String rawValue) {
        if (settings == null) {
            return;
        }

        ParkourRuntimeState state = getRuntimeState();
        Boolean requestedFreeLook = FREE_LOOK_KEY.equals(changedKey) ? parseNullableBoolean(rawValue) : null;
        boolean stateChanged = false;

        {
            boolean restoreFreeLook = settings.freeLook.value;
            // Migrate the old lock once; an explicit user choice always wins.
            if (requestedFreeLook != null) {
                restoreFreeLook = requestedFreeLook;
            } else if (state.freeLookLockedByParkour && state.hasPreferredFreeLook) {
                restoreFreeLook = state.preferredFreeLook;
            }

            if (state.freeLookLockedByParkour && settings.freeLook.value != restoreFreeLook) {
                settings.freeLook.value = restoreFreeLook;
            }
            if (state.freeLookLockedByParkour) {
                state.freeLookLockedByParkour = false;
                stateChanged = true;
            }
            if (!state.hasPreferredFreeLook || state.preferredFreeLook != restoreFreeLook) {
                state.preferredFreeLook = restoreFreeLook;
                state.hasPreferredFreeLook = true;
                stateChanged = true;
            }
        }

        if (stateChanged) {
            saveRuntimeState(state);
        }
    }

    private static Boolean parseNullableBoolean(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        return "true".equalsIgnoreCase(rawValue.trim());
    }

    private static ParkourRuntimeState getRuntimeState() {
        if (runtimeState != null) {
            return runtimeState;
        }
        runtimeState = loadRuntimeState();
        return runtimeState;
    }

    private static Path getRuntimeStateFile() {
        return ProfileManager.getCurrentProfileDir().resolve("baritone_parkour_runtime.json");
    }

    private static ParkourRuntimeState loadRuntimeState() {
        Path path = getRuntimeStateFile();
        if (!Files.exists(path)) {
            return new ParkourRuntimeState();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ParkourRuntimeState loaded = GSON.fromJson(reader, ParkourRuntimeState.class);
            return loaded == null ? new ParkourRuntimeState() : loaded;
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("读取 Baritone 跑酷运行时状态失败", e);
            return new ParkourRuntimeState();
        }
    }

    private static void saveRuntimeState(ParkourRuntimeState state) {
        Path path = getRuntimeStateFile();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存 Baritone 跑酷运行时状态失败", e);
        }
    }

    private static final class ParkourRuntimeState {
        private boolean hasPreferredFreeLook;
        private boolean preferredFreeLook = true;
        private boolean freeLookLockedByParkour;
    }
}
