package com.zszl.zszlScriptMod.config;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BaritoneParkourPresetHelper {

    private BaritoneParkourPresetHelper() {
    }

    public static Map<String, String> buildStablePresetValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("parkourMode", "true");
        values.put("parkourProfile", "STABLE");
        values.put("parkourDebugRender", "false");
        values.put("allowParkour", "true");
        values.put("allowParkourAscend", "true");
        values.put("allowParkourPlace", "false");
        values.put("backfill", "false");
        return values;
    }

    public static void applyStablePresetToEditingValues(Map<String, String> editingValues) {
        if (editingValues == null) {
            return;
        }
        editingValues.putAll(buildStablePresetValues());
    }

    public static void applyStablePresetToRuntimeSettings() {
        Settings settings = BaritoneAPI.getSettings();
        for (Map.Entry<String, String> entry : buildStablePresetValues().entrySet()) {
            SettingsUtil.parseAndApply(settings, entry.getKey().toLowerCase(), entry.getValue());
        }
        SettingsUtil.save(settings);
    }
}
