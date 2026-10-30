package com.mythos.mythosScriptMod.config;

import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BaritoneParkourConflictHelper {

    private BaritoneParkourConflictHelper() {
    }

    public static boolean isParkourModeEnabled(Settings settings) {
        return settings != null && settings.parkourMode.value;
    }

    public static boolean isParkourModeEnabled(Map<String, String> editingValues) {
        return parseBoolean(readValue(editingValues, "parkourMode"));
    }



    private static String readValue(Map<String, String> editingValues, String key) {
        if (editingValues == null || key == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : editingValues.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static boolean parseBoolean(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim().toLowerCase(Locale.ROOT));
    }
}
