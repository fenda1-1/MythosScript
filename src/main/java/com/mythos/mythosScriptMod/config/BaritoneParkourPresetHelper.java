package com.mythos.mythosScriptMod.config;

import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.SettingsUtil;

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
        values.put("parkourDebugLog", "false");
        values.put("allowParkour", "true");
        values.put("allowParkourAscend", "true");
        values.put("allowParkourPlace", "false");
        values.put("backfill", "false");
        return values;
    }


}
