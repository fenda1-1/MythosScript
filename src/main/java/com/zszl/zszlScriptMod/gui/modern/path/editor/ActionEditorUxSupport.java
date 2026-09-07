package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.resources.I18n;

/**
 * Specialized-field classification for the modern action editor. JSON keys and
 * values stay unchanged; only the control kind changes.
 */
public final class ActionEditorUxSupport {
    private static final Set<String> EMPTY_PARAM_ACTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "disconnect", "hidecurrentgui", "showhiddengui", "close_container_window", "restart_sequence",
            "no_stop_navigation", "debug_print_gui_summary", "runlastsequence")));

    private static final Set<String> HOTBAR_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "targetHotbarSlot", "tempslot")));

    private static final Set<String> COORD_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "pos", "center", "regionRect", "pointsText")));

    private static final Set<String> KEY_VALUE_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "casesText", "paramsText")));

    private static final Set<String> LINE_LIST_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "foodKeywordsText", "nameBlacklistText", "contains")));

    private static final Set<String> BUTTON_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "button")));

    private ActionEditorUxSupport() {
    }

    public static boolean isEmptyParamAction(String type) {
        return EMPTY_PARAM_ACTIONS.contains(normalize(type));
    }

    public static String emptyParamTitle(String type) {
        return I18n.format("gui.path.action_editor.empty.no_params");
    }

    public static String emptyParamHint(String type) {
        String key = "gui.path.action_editor.empty." + normalize(type);
        String translated = I18n.format(key);
        return translated.equals(key) ? I18n.format("gui.path.action_editor.empty.generic") : translated;
    }

    public static boolean isHotbarKey(String key) {
        return HOTBAR_KEYS.contains(key);
    }

    public static boolean isCoordKey(String key) {
        return COORD_KEYS.contains(key) || "yaw".equals(key) || "pitch".equals(key) || "x".equals(key) || "y".equals(key);
    }

    public static boolean isKeyValueKey(String key) {
        return KEY_VALUE_KEYS.contains(key);
    }

    public static boolean isLineListKey(String key) {
        return LINE_LIST_KEYS.contains(key);
    }

    public static boolean isClickButtonKey(String type, String key) {
        if (!BUTTON_KEYS.contains(key)) {
            return false;
        }
        String t = normalize(type);
        return "window_click".equals(t) || "conditional_window_click".equals(t) || "autochestclick".equals(t)
                || "move_inventory_items_to_chest_slots".equals(t);
    }

    public static boolean isViewCaptureKey(String key) {
        return "yaw".equals(key) || "pitch".equals(key);
    }

    public static boolean isLabelKey(String type, String key) {
        String t = normalize(type);
        return "goto_label".equals(t) && "targetLabel".equals(key);
    }

    public static boolean isActionIndexKey(String type, String key) {
        return "goto_action".equals(normalize(type)) && "targetActionIndex".equals(key);
    }

    public static boolean isVariableKey(String type, String key) {
        if (key == null) {
            return false;
        }
        String t = normalize(type);
        return "name".equals(key) && "set_var".equals(t)
                || "fromVar".equals(key)
                || "sourceVar".equals(key)
                || "varName".equals(key)
                || "loopVar".equals(key)
                || "itemVar".equals(key)
                || "indexVar".equals(key)
                || "pointVar".equals(key)
                || "attemptVar".equals(key);
    }

    public static boolean isTemplateKey(String type, String key) {
        return "run_template".equals(normalize(type)) && "templateName".equals(key);
    }

    public static boolean isEquipSetKey(String type, String key) {
        return "autoequip".equals(normalize(type)) && "setName".equals(key);
    }

    public static boolean isHexKey(String type, String key) {
        return "send_packet".equals(normalize(type)) && "hex".equals(key);
    }

    public static boolean isRangeDelayKey(String type, String key) {
        return "delay".equals(normalize(type)) && "ticks".equals(key);
    }

    public static int spinnerMin(String key) {
        if ("foodLevelThreshold".equals(key)) {
            return 1;
        }
        if ("targetHotbarSlot".equals(key)) {
            return 1;
        }
        if ("windowId".equals(key) || "lineIndex".equals(key)) {
            return -1;
        }
        return 0;
    }

    public static int spinnerMax(String key) {
        if ("foodLevelThreshold".equals(key)) {
            return 20;
        }
        if ("targetHotbarSlot".equals(key) || "tempslot".equals(key)) {
            return 9;
        }
        if ("colorTolerance".equals(key)) {
            return 255;
        }
        return 99999;
    }

    public static boolean isMoveChestAction(String type) {
        return "move_inventory_items_to_chest_slots".equals(normalize(type));
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
