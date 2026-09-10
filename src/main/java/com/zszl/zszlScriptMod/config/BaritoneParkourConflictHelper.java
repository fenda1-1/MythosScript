package com.zszl.zszlScriptMod.config;

import com.zszl.zszlScriptMod.shadowbaritone.api.Settings;

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

    public static List<String> getConflictWarnings(Map<String, String> editingValues) {
        List<String> warnings = new ArrayList<>();
        if (!isParkourModeEnabled(editingValues)) {
            return warnings;
        }
        if (parseBoolean(readValue(editingValues, "backfill"))) {
            warnings.add("Backfill 会在跑酷模式下被自动关闭");
        }
        if (parseBoolean(readValue(editingValues, "allowParkourPlace"))) {
            warnings.add("跑酷放方块会在跑酷模式下被自动忽略");
        }
        warnings.add("自由视角移动会在跑酷模式下自动锁定关闭");
        if (HumanLikeMovementConfig.INSTANCE != null && HumanLikeMovementConfig.INSTANCE.enabled) {
            warnings.add("模拟真人路线扰动会在跑酷规划时自动停用");
        }
        return warnings;
    }

    public static String buildCompactWarning(Map<String, String> editingValues) {
        List<String> warnings = getConflictWarnings(editingValues);
        if (warnings.isEmpty()) {
            return "";
        }
        return "§6跑酷模式提示: " + String.join("；", warnings);
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
