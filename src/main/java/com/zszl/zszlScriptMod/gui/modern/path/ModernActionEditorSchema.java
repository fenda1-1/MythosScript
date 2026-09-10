package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The modern action editor's field catalogue. It mirrors the legacy editor's
 * type-specific sections without making the modern screen depend on GuiScreen.
 */
public final class ModernActionEditorSchema {
    enum Kind { TEXT, TOGGLE, CHOICE, KEYBOARD_PICKER, SLOT_GRID, STRUCTURED_LIST, EXPRESSION, EXPRESSION_LIST, TEXT_EXPRESSION_LIST, ITEM_FILTER_LIST, SECTION, MOVE_CANVAS, SPINNER, HOTBAR, KEY_VALUE, HEX, INFO }

    static final class Field {
        final String section;
        final String key;
        final String label;
        final String hint;
        final String defaultValue;
        final Kind kind;
        final List<String> choices;

        Field(String section, String key, String label, String hint, String defaultValue, Kind kind,
                List<String> choices) {
            this.section = section;
            this.key = key;
            this.label = label;
            this.hint = hint;
            this.defaultValue = defaultValue;
            this.kind = kind;
            this.choices = choices == null ? Collections.<String>emptyList() : choices;
        }
    }

    private ModernActionEditorSchema() { }

    /** Stable machine-readable export of the same fields used by the editor. */
    public static com.google.gson.JsonObject describeForMcp(String type) {
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        for (Field field : fields(type)) {
            com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
            entry.addProperty("key", field.key);
            entry.addProperty("kind", field.kind.name());
            entry.addProperty("label", net.minecraft.client.resources.I18n.format(field.label));
            entry.addProperty("help", net.minecraft.client.resources.I18n.format(field.hint));
            entry.addProperty("defaultText", field.defaultValue);
            entry.add("choices", new com.google.gson.Gson().toJsonTree(field.choices));
            entries.add(entry);
        }
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.addProperty("type", type);
        result.add("fields", entries);
        result.add("defaults", defaultsFor(type));
        return result;
    }

    static List<Field> fields(String type) {
        String t = type == null ? "" : type.trim().toLowerCase();
        List<Field> result = new ArrayList<>();
        if ("command".equals(t)) text(result, "gui.modern.path.schema.u001", "command", "gui.modern.path.schema.u002", "gui.modern.path.schema.u003", "");
        else if ("system_message".equals(t)) text(result, "gui.modern.path.schema.u001", "message", "gui.modern.path.schema.u004", "gui.modern.path.schema.u005", "");
        else if ("paste_text".equals(t)) text(result, "gui.modern.path.schema.u001", "text", "gui.modern.path.schema.u006", "gui.modern.path.schema.u007", "");
        else if ("delay".equals(t)) {
            text(result, "gui.modern.path.schema.u008", "ticks", "gui.modern.path.schema.u009", "gui.modern.path.schema.u010", "20");
            toggle(result, "gui.modern.path.schema.u008", "normalizeDelayTo20Tps", "gui.modern.path.schema.u011", "gui.modern.path.schema.u012", "true");
        } else if ("key".equals(t)) {
            keyboard(result, "gui.modern.path.schema.u013", "key", "gui.modern.path.schema.u014", "gui.modern.path.schema.u015", "");
            choice(result, "gui.modern.path.schema.u013", "state", "gui.modern.path.schema.u016", "", "Press", "Down", "Up");
            toggle(result, "gui.modern.path.schema.u013", "customPressDuration", "gui.modern.path.schema.u017",
                    "gui.modern.path.schema.u018", "false");
            text(result, "gui.modern.path.schema.u013", "pressDurationTicks", "gui.modern.path.schema.u019",
                    "gui.modern.path.schema.u020", "10");
        } else if ("jump".equals(t)) {
            text(result, "gui.modern.path.schema.u021", "count", "gui.modern.path.schema.u022", "", "1");
            text(result, "gui.modern.path.schema.u021", "intervalTicks", "gui.modern.path.schema.u023", "", "0");
        } else if ("click".equals(t)) {
            choice(result, "gui.modern.path.schema.u024", "locatorMode", "gui.modern.path.schema.u025", "", "COORDINATE", "BUTTON_TEXT", "SLOT_TEXT", "ELEMENT_PATH");
            text(result, "gui.modern.path.schema.u024", "locatorText", "gui.modern.path.schema.u026", "gui.modern.path.schema.u027", "");
            choice(result, "gui.modern.path.schema.u024", "locatorMatchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
            text(result, "gui.modern.path.schema.u024", "x", "gui.modern.path.schema.u029", "gui.modern.path.schema.u030", "0");
            text(result, "gui.modern.path.schema.u024", "y", "gui.modern.path.schema.u031", "gui.modern.path.schema.u030", "0");
            choice(result, "gui.modern.path.schema.u024", "left", "gui.modern.path.schema.u032", "", "LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "MIDDLE");
        } else if ("setview".equals(t)) {
            text(result, "gui.modern.path.schema.u033", "yaw", "gui.modern.path.schema.u034", "", "0");
            text(result, "gui.modern.path.schema.u033", "pitch", "gui.modern.path.schema.u035", "", "0");
        } else if ("window_click".equals(t) || "conditional_window_click".equals(t)) {
            slotFields(result, "gui.modern.path.schema.u036");
            text(result, "gui.modern.path.schema.u036", "windowId", "gui.modern.path.schema.u037", "gui.modern.path.schema.u038", "-1");
            text(result, "gui.modern.path.schema.u036", "contains", "gui.modern.path.schema.u039", "gui.modern.path.schema.u040", "");
            text(result, "gui.modern.path.schema.u036", "containerTitle", "gui.modern.path.schema.u041", "gui.modern.path.schema.u042", "");
            text(result, "gui.modern.path.schema.u036", "slotCount", "gui.modern.path.schema.u043", "gui.modern.path.schema.u044", "0");
            toggle(result, "gui.modern.path.schema.u036", "onlyOnSlotChange", "gui.modern.path.schema.u045", "gui.modern.path.schema.u046", "false");
            text(result, "gui.modern.path.schema.u036", "button", "gui.modern.path.schema.u047", "gui.modern.path.schema.u048", "0");
            choice(result, "gui.modern.path.schema.u036", "clickType", "gui.modern.path.schema.u049", "", "PICKUP", "QUICK_MOVE", "THROW", "SWAP",
                    "PICKUP_ALL", "QUICK_CRAFT", "CLONE");
        } else if ("rightclickblock".equals(t) || "rightclickentity".equals(t)) {
            choice(result, "gui.modern.path.schema.u050", "locatorMode", "gui.modern.path.schema.u051", "", "POSITION", "NAME");
            text(result, "gui.modern.path.schema.u050", "locatorText", "gui.modern.path.schema.u052", "gui.modern.path.schema.u053", "");
            choice(result, "gui.modern.path.schema.u050", "locatorMatchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
            text(result, "gui.modern.path.schema.u050", "pos", "gui.modern.path.schema.u054", "gui.modern.path.schema.u055", "[0,64,0]");
            text(result, "gui.modern.path.schema.u050", "range", "gui.modern.path.schema.u056", "gui.modern.path.schema.u057",
                    "rightclickblock".equals(t) ? "10" : "3");
            toggle(result, "gui.modern.path.schema.u050", "preserveView", "gui.modern.path.schema.u058", "gui.modern.path.schema.u059", "false");
        } else if ("autochestclick".equals(t)) {
            slotFields(result, "gui.modern.path.schema.u060");
            text(result, "gui.modern.path.schema.u060", "delayTicks", "gui.modern.path.schema.u061", "", "1");
            text(result, "gui.modern.path.schema.u060", "button", "gui.modern.path.schema.u047", "gui.modern.path.schema.u062", "0");
            choice(result, "gui.modern.path.schema.u060", "clickType", "gui.modern.path.schema.u049", "", "PICKUP", "QUICK_MOVE", "THROW", "SWAP",
                    "PICKUP_ALL", "QUICK_CRAFT", "CLONE");
        } else if ("move_inventory_items_to_chest_slots".equals(t)) {
            result.add(new Field("gui.modern.path.schema.u063", "moveChestCanvas", "gui.modern.path.schema.u064",
                    "gui.modern.path.schema.u065", "", Kind.MOVE_CANVAS, null));
            text(result, "gui.modern.path.schema.u063", "delayTicks", "gui.modern.path.schema.u061", "", "2");
            toggle(result, "gui.modern.path.schema.u063", "normalizeDelayTo20Tps", "gui.modern.path.schema.u011", "", "true");
            text(result, "gui.modern.path.schema.u063", "chestRows", "gui.modern.path.schema.u066", "", "6");
            text(result, "gui.modern.path.schema.u063", "chestCols", "gui.modern.path.schema.u067", "", "9");
            text(result, "gui.modern.path.schema.u063", "inventoryRows", "gui.modern.path.schema.u068", "", "4");
            text(result, "gui.modern.path.schema.u063", "inventoryCols", "gui.modern.path.schema.u069", "", "9");
            choice(result, "gui.modern.path.schema.u063", "moveDirection", "gui.modern.path.schema.u070", "", "INVENTORY_TO_CHEST", "CHEST_TO_INVENTORY");
            text(result, "gui.modern.path.schema.u063", "button", "gui.modern.path.schema.u047", "", "0");
            choice(result, "gui.modern.path.schema.u063", "clickType", "gui.modern.path.schema.u049", "", "PICKUP", "QUICK_MOVE", "THROW", "SWAP",
                    "PICKUP_ALL", "QUICK_CRAFT", "CLONE");
            itemFilterList(result, "gui.modern.path.schema.u071", "gui.modern.path.schema.u072", "gui.modern.path.schema.u073");
            text(result, "gui.modern.path.schema.u071", "maxTransferCount", "gui.modern.path.schema.u074", "gui.modern.path.schema.u075", "0");
            grid(result, "gui.modern.path.schema.u076", "inventorySlots", "gui.modern.path.schema.u077", "gui.modern.path.schema.u078", "[]");
            grid(result, "gui.modern.path.schema.u076", "chestSlots", "gui.modern.path.schema.u060", "gui.modern.path.schema.u079", "[]");
        } else if ("blocknextgui".equals(t)) {
            text(result, "gui.modern.path.schema.u080", "count", "gui.modern.path.schema.u081", "", "1");
            toggle(result, "gui.modern.path.schema.u080", "blockCurrentGui", "gui.modern.path.schema.u082", "", "false");
        } else if ("hud_text_check".equals(t)) {
            text(result, "gui.modern.path.schema.u083", "contains", "gui.modern.path.schema.u084", "", "");
            toggle(result, "gui.modern.path.schema.u083", "matchBlock", "gui.modern.path.schema.u085", "", "false");
            text(result, "gui.modern.path.schema.u083", "separator", "gui.modern.path.schema.u086", "", " | ");
        } else if (isConditionOrWait(t)) {
            conditionFields(result, t);
        } else if (t.startsWith("capture_")) {
            captureFields(result, t);
        } else if ("set_var".equals(t)) {
            text(result, "gui.modern.path.schema.u087", "name", "gui.modern.path.schema.u088", "gui.modern.path.schema.u089", "");
            text(result, "gui.modern.path.schema.u087", "value", "gui.modern.path.schema.u090", "", "");
            choice(result, "gui.modern.path.schema.u087", "valueType", "gui.modern.path.schema.u091", "", "", "string", "number", "boolean");
            expression(result, "gui.modern.path.schema.u087", "expression", "gui.modern.path.schema.u092", "gui.modern.path.schema.u093");
            text(result, "gui.modern.path.schema.u087", "fromVar", "gui.modern.path.schema.u094", "gui.modern.path.schema.u095", "");
        } else if ("if_else".equals(t)) {
            textExpressionList(result, "gui.modern.path.schema.u096", "conditionsText", "gui.modern.path.schema.u097", "gui.modern.path.schema.u098");
            text(result, "gui.modern.path.schema.u096", "thenCount", "gui.modern.path.schema.u099", "", "1");
            text(result, "gui.modern.path.schema.u096", "elseCount", "gui.modern.path.schema.u100", "", "0");
        } else if ("switch_var".equals(t)) {
            text(result, "gui.modern.path.schema.u101", "sourceVar", "gui.modern.path.schema.u088", "", "");
            structuredList(result, "gui.modern.path.schema.u101", "casesText", "gui.modern.path.schema.u102", "gui.modern.path.schema.u103");
            text(result, "gui.modern.path.schema.u101", "defaultCount", "gui.modern.path.schema.u104", "", "0");
        } else if ("branch_table".equals(t)) {
            expression(result, "gui.modern.path.schema.u101", "keyExpression", "gui.modern.path.schema.u105", "gui.modern.path.schema.u106");
            structuredList(result, "gui.modern.path.schema.u101", "casesText", "gui.modern.path.schema.u102", "gui.modern.path.schema.u103");
            text(result, "gui.modern.path.schema.u101", "defaultCount", "gui.modern.path.schema.u104", "", "0");
        } else if ("while_condition".equals(t)) {
            textExpressionList(result, "gui.modern.path.schema.u107", "conditionsText", "gui.modern.path.schema.u108", "gui.modern.path.schema.u109");
            text(result, "gui.modern.path.schema.u107", "bodyCount", "gui.modern.path.schema.u110", "", "1");
            text(result, "gui.modern.path.schema.u107", "maxLoops", "gui.modern.path.schema.u111", "", "0");
            text(result, "gui.modern.path.schema.u107", "loopVar", "gui.modern.path.schema.u112", "", "while_index");
        } else if ("retry_block".equals(t)) {
            textExpressionList(result, "gui.modern.path.schema.u113", "conditionsText", "gui.modern.path.schema.u114", "gui.modern.path.schema.u115");
            text(result, "gui.modern.path.schema.u113", "bodyCount", "gui.modern.path.schema.u110", "", "1");
            text(result, "gui.modern.path.schema.u107", "retryCount", "gui.modern.path.schema.u116", "", "3");
            text(result, "gui.modern.path.schema.u107", "retryDelayTicks", "gui.modern.path.schema.u117", "", "0");
            text(result, "gui.modern.path.schema.u113", "attemptVar", "gui.modern.path.schema.u118", "", "retry_block");
        } else if ("for_each_list".equals(t)) {
            text(result, "gui.modern.path.schema.u107", "sourceVar", "gui.modern.path.schema.u094", "gui.modern.path.schema.u119", "");
            text(result, "gui.modern.path.schema.u107", "bodyCount", "gui.modern.path.schema.u110", "", "1");
            text(result, "gui.modern.path.schema.u107", "itemVar", "gui.modern.path.schema.u120", "", "item");
            text(result, "gui.modern.path.schema.u107", "indexVar", "gui.modern.path.schema.u121", "", "item_index");
        } else if ("for_each_point".equals(t)) {
            structuredList(result, "gui.modern.path.schema.u107", "pointsText", "gui.modern.path.schema.u122", "gui.modern.path.schema.u123");
            text(result, "gui.modern.path.schema.u107", "bodyCount", "gui.modern.path.schema.u110", "", "1");
            text(result, "gui.modern.path.schema.u107", "pointVar", "gui.modern.path.schema.u124", "", "point");
            text(result, "gui.modern.path.schema.u107", "indexVar", "gui.modern.path.schema.u121", "", "point_index");
        } else if ("label".equals(t)) {
            text(result, "gui.modern.path.schema.u125", "labelName", "gui.modern.path.schema.u126", "", "label_1");
        } else if ("goto_label".equals(t)) {
            text(result, "gui.modern.path.schema.u125", "targetLabel", "gui.modern.path.schema.u127", "", "label_1");
        } else if ("goto_action".equals(t)) {
            text(result, "gui.modern.path.schema.u125", "targetActionIndex", "gui.modern.path.schema.u128", "", "0");
        } else if ("skip_actions".equals(t) || "skip_steps".equals(t)) {
            text(result, "gui.modern.path.schema.u129", "count", "gui.modern.path.schema.u130", "", "skip_actions".equals(t) ? "1" : "0");
        } else if ("repeat_actions".equals(t)) {
            text(result, "gui.modern.path.schema.u129", "count", "gui.modern.path.schema.u131", "", "2");
            text(result, "gui.modern.path.schema.u129", "bodyCount", "gui.modern.path.schema.u110", "", "1");
            text(result, "gui.modern.path.schema.u129", "loopVar", "gui.modern.path.schema.u132", "", "loop_index");
        } else if ("debug_print_var".equals(t)) {
            text(result, "gui.modern.path.schema.u133", "varName", "gui.modern.path.schema.u088", "", "");
        } else if ("debug_print_nearby_entities".equals(t)) {
            choice(result, "gui.modern.path.schema.u133", "entityType", "gui.modern.path.schema.u134", "", "player", "hostile", "passive", "all");
            text(result, "gui.modern.path.schema.u133", "entityName", "gui.modern.path.schema.u135", "", "");
            text(result, "gui.modern.path.schema.u133", "radius", "gui.modern.path.schema.u056", "", "8");
        } else if ("autoeat".equals(t)) {
            toggle(result, "gui.modern.path.schema.u136", "enabled", "gui.modern.path.schema.u137", "", "true");
            text(result, "gui.modern.path.schema.u136", "foodLevelThreshold", "gui.modern.path.schema.u138", "", "12");
            toggle(result, "gui.modern.path.schema.u136", "autoMoveFoodEnabled", "gui.modern.path.schema.u139", "", "true");
            toggle(result, "gui.modern.path.schema.u136", "eatWithLookDown", "gui.modern.path.schema.u140", "", "false");
            text(result, "gui.modern.path.schema.u136", "targetHotbarSlot", "gui.modern.path.schema.u141", "", "9");
            text(result, "gui.modern.path.schema.u136", "foodKeywordsText", "gui.modern.path.schema.u142", "", "");
        } else if ("autoequip".equals(t)) {
            toggle(result, "gui.modern.path.schema.u143", "enabled", "gui.modern.path.schema.u137", "", "true");
            text(result, "gui.modern.path.schema.u143", "setName", "gui.modern.path.schema.u144", "", "");
            toggle(result, "gui.modern.path.schema.u143", "smartActivation", "gui.modern.path.schema.u145", "", "false");
        } else if ("autopickup".equals(t)) {
            toggle(result, "gui.modern.path.schema.u146", "enabled", "gui.modern.path.schema.u137", "", "true");
        } else if ("dropfiltereditems".equals(t)) {
            itemFilterList(result, "gui.modern.path.schema.u071", "gui.modern.path.schema.u072", "gui.modern.path.schema.u147");
            text(result, "gui.modern.path.schema.u071", "delayTicks", "gui.modern.path.schema.u148", "", "1");
        } else if ("transferitemstowarehouse".equals(t) || "warehouse_auto_deposit".equals(t)) {
            itemFilterList(result, "gui.modern.path.schema.u149", "gui.modern.path.schema.u072", "gui.modern.path.schema.u073");
            text(result, "gui.modern.path.schema.u149", "delayTicks", "gui.modern.path.schema.u148", "", "1");
            toggle(result, "gui.modern.path.schema.u149", "normalizeDelayTo20Tps", "gui.modern.path.schema.u011", "", "true");
        } else if ("toggle_other_feature".equals(t)) {
            text(result, "gui.modern.path.schema.u150", "featureId", "gui.modern.path.schema.u151", "", "");
            toggle(result, "gui.modern.path.schema.u150", "enabled", "gui.modern.path.schema.u137", "", "true");
        } else if (isSimpleToggle(t)) {
            if ("toggle_kill_aura".equals(t)) {
                choice(result, "gui.modern.path.schema.u152", "presetName", "gui.modern.path.schema.kill_aura_preset",
                        "gui.modern.path.schema.kill_aura_preset_hint", "");
            }
            toggle(result, "gui.modern.path.schema.u152", "enabled", "gui.modern.path.schema.u137", "", "true");
        } else if ("takeallitems".equals(t) || "take_all_items_safe".equals(t)) {
            toggle(result, "gui.modern.path.schema.u153", "shiftQuickMove", "gui.modern.path.schema.u154", "gui.modern.path.schema.u155", "true");
        } else if ("hunt".equals(t)) {
            text(result, "gui.modern.path.schema.u156", "radius", "gui.modern.path.schema.u157", "", "3.0");
            text(result, "gui.modern.path.schema.u156", "noTargetSkipCount", "gui.modern.path.schema.u158", "gui.modern.path.schema.u159", "0");
            text(result, "gui.modern.path.schema.u156", "huntUpRange", "gui.modern.path.schema.u160", "", "3.0");
            text(result, "gui.modern.path.schema.u156", "huntDownRange", "gui.modern.path.schema.u161", "", "3.0");
            choice(result, "gui.modern.path.schema.u156", "entityType", "gui.modern.path.schema.u134", "gui.modern.path.schema.u162", "", "player", "hostile", "passive", "all");
            toggle(result, "gui.modern.path.schema.u156", "enableAreaSweep", "gui.modern.path.schema.u163", "", "true");
            text(result, "gui.modern.path.schema.u156", "areaSweepCellSize", "gui.modern.path.schema.u164", "gui.modern.path.schema.u165", "8");
            toggle(result, "gui.modern.path.schema.u156", "enableNameWhitelist", "gui.modern.path.schema.u166", "", "false");
            structuredList(result, "gui.modern.path.schema.u156", "nameWhitelistEntries", "gui.modern.path.schema.u167",
                    "gui.modern.path.schema.u168");
            toggle(result, "gui.modern.path.schema.u169", "waitForWhitelistRespawn", "gui.modern.path.schema.u170",
                    "gui.modern.path.schema.u171", "false");
            text(result, "gui.modern.path.schema.u169", "whitelistRespawnTimeoutSeconds", "gui.modern.path.schema.u172",
                    "gui.modern.path.schema.u173", "120");
            toggle(result, "gui.modern.path.schema.u169", "waitForWhitelistRespawnAfterCompletion", "gui.modern.path.schema.u174",
                    "gui.modern.path.schema.u175", "false");
            toggle(result, "gui.modern.path.schema.u303", "confirmKillCompletion", "gui.modern.path.schema.u304",
                    "gui.modern.path.schema.u305", "false");
            text(result, "gui.modern.path.schema.u303", "killCompletionConfirmSeconds", "gui.modern.path.schema.u306",
                    "gui.modern.path.schema.u307", "3");
            toggle(result, "gui.modern.path.schema.u176", "forceEndHunt", "gui.modern.path.schema.u177",
                    "gui.modern.path.schema.u178", "false");
            text(result, "gui.modern.path.schema.u176", "forceEndHuntTimeoutSeconds", "gui.modern.path.schema.u179",
                    "gui.modern.path.schema.u180", "60");
            toggle(result, "gui.modern.path.schema.u156", "enableNameBlacklist", "gui.modern.path.schema.u181", "", "false");
            structuredList(result, "gui.modern.path.schema.u156", "nameBlacklistEntries", "gui.modern.path.schema.u182",
                    "gui.modern.path.schema.u183");
            text(result, "gui.modern.path.schema.u156", "scanRadius", "gui.modern.path.schema.u184", "gui.modern.path.schema.u185", "10");
        } else if ("follow_entity".equals(t)) {
            choice(result, "gui.modern.path.schema.u186", "entityType", "gui.modern.path.schema.u134", "", "player", "hostile", "passive", "all");
            text(result, "gui.modern.path.schema.u186", "targetName", "gui.modern.path.schema.u187", "", "");
            text(result, "gui.modern.path.schema.u186", "searchRadius", "gui.modern.path.schema.u157", "", "16.0");
            text(result, "gui.modern.path.schema.u186", "followDistance", "gui.modern.path.schema.u188", "", "3.0");
            text(result, "gui.modern.path.schema.u186", "timeout", "gui.modern.path.schema.u189", "", "0");
            toggle(result, "gui.modern.path.schema.u186", "stopOnLost", "gui.modern.path.schema.u190", "", "true");
        } else if ("use_hotbar_item".equals(t)) {
            text(result, "gui.modern.path.schema.u191", "itemName", "gui.modern.path.schema.u192", "", "");
            choice(result, "gui.modern.path.schema.u191", "matchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
            choice(result, "gui.modern.path.schema.u191", "useMode", "gui.modern.path.schema.u193", "", "RIGHT_CLICK", "LEFT_CLICK");
            toggle(result, "gui.modern.path.schema.u191", "changeLocalSlot", "gui.modern.path.schema.u194", "", "false");
            text(result, "gui.modern.path.schema.u191", "count", "gui.modern.path.schema.u195", "", "1");
            text(result, "gui.modern.path.schema.u191", "switchItemDelayTicks", "gui.modern.path.schema.u196", "", "0");
            text(result, "gui.modern.path.schema.u191", "switchDelayTicks", "gui.modern.path.schema.u197", "", "0");
            text(result, "gui.modern.path.schema.u191", "switchBackDelayTicks", "gui.modern.path.schema.u198", "", "0");
            text(result, "gui.modern.path.schema.u191", "intervalTicks", "gui.modern.path.schema.u199", "", "0");
        } else if ("move_inventory_item_to_hotbar".equals(t)) {
            text(result, "gui.modern.path.schema.u200", "itemName", "gui.modern.path.schema.u192", "", "");
            choice(result, "gui.modern.path.schema.u200", "matchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
            text(result, "gui.modern.path.schema.u200", "targetHotbarSlot", "gui.modern.path.schema.u141", "", "1");
            text(result, "gui.modern.path.schema.u200", "count", "gui.modern.path.schema.u201", "", "0");
            toggle(result, "gui.modern.path.schema.u200", "skipIfTargetOccupied", "gui.modern.path.schema.u202", "", "true");
        } else if ("spread_inventory_item".equals(t) || "stack_inventory_item".equals(t)) {
            itemTransferFields(result, "gui.modern.path.schema.u200", "spread_inventory_item".equals(t));
        } else if ("pickup_nearby_items".equals(t)) {
            text(result, "gui.modern.path.schema.u203", "searchRadius", "gui.modern.path.schema.u157", "", "16");
            text(result, "gui.modern.path.schema.u203", "maxItems", "gui.modern.path.schema.u204", "gui.modern.path.schema.u205", "0");
            text(result, "gui.modern.path.schema.u203", "timeoutSeconds", "gui.modern.path.schema.u206", "", "30");
            text(result, "gui.modern.path.schema.u203", "reachDistance", "gui.modern.path.schema.u207", "", "0.5");
        } else if ("silentuse".equals(t)) {
            text(result, "gui.modern.path.schema.u191", "item", "gui.modern.path.schema.u192", "", "");
            text(result, "gui.modern.path.schema.u191", "tempslot", "gui.modern.path.schema.u208", "", "0");
            text(result, "gui.modern.path.schema.u191", "switchDelayTicks", "gui.modern.path.schema.u209", "", "0");
            text(result, "gui.modern.path.schema.u191", "useDelayTicks", "gui.modern.path.schema.u210", "", "1");
            text(result, "gui.modern.path.schema.u191", "switchBackDelayTicks", "gui.modern.path.schema.u211", "", "0");
        } else if ("switch_hotbar_slot".equals(t)) {
            text(result, "gui.modern.path.schema.u212", "targetHotbarSlot", "gui.modern.path.schema.u213", "", "1");
            toggle(result, "gui.modern.path.schema.u212", "useAfterSwitch", "gui.modern.path.schema.u214", "", "false");
            text(result, "gui.modern.path.schema.u212", "useAfterSwitchDelayTicks", "gui.modern.path.schema.u215", "", "0");
        } else if ("use_held_item".equals(t)) {
            text(result, "gui.modern.path.schema.u191", "delayTicks", "gui.modern.path.schema.u215", "", "0");
        } else if ("send_packet".equals(t)) {
            choice(result, "gui.modern.path.schema.u216", "direction", "gui.modern.path.schema.u217", "", "C2S", "S2C");
            text(result, "gui.modern.path.schema.u216", "channel", "gui.modern.path.schema.u218", "", "");
            text(result, "gui.modern.path.schema.u216", "packetId", "gui.modern.path.schema.u219", "", "");
            text(result, "gui.modern.path.schema.u216", "hex", "gui.modern.path.schema.u220", "", "");
        } else if ("run_sequence".equals(t)) {
            text(result, "gui.modern.path.schema.u221", "sequenceName", "gui.modern.path.schema.u222", "", "");
            choice(result, "gui.modern.path.schema.u221", "executeMode", "gui.modern.path.schema.u223", "", "always", "interval");
            text(result, "gui.modern.path.schema.u221", "executeEveryCount", "gui.modern.path.schema.u224", "", "1");
            toggle(result, "gui.modern.path.schema.u221", "backgroundExecution", "gui.modern.path.schema.u225", "", "false");
        } else if ("run_template".equals(t)) {
            text(result, "gui.modern.path.schema.u226", "templateName", "gui.modern.path.schema.u227", "", "");
            text(result, "gui.modern.path.schema.u226", "paramsText", "gui.modern.path.schema.u228", "gui.modern.path.schema.u229", "");
        } else if ("stop_current_sequence".equals(t)) {
            choice(result, "gui.modern.path.schema.u230", "targetScope", "gui.modern.path.schema.u231", "", "foreground", "background");
        } else if ("sequence_control".equals(t)) {
            choice(result, "gui.modern.path.schema.u230", "targetScope", "gui.modern.path.schema.u231", "", "foreground", "background");
            choice(result, "gui.modern.path.schema.u230", "operation", "gui.modern.path.schema.u232", "", "pause", "resume");
        }
        return result;
    }

    static JsonObject defaultsFor(String type) {
        JsonObject result = new JsonObject();
        for (Field field : fields(type)) {
            if (field.kind == Kind.SECTION || field.kind == Kind.EXPRESSION
                    || field.kind == Kind.TEXT_EXPRESSION_LIST || field.kind == Kind.EXPRESSION_LIST
                    || field.kind == Kind.ITEM_FILTER_LIST || field.kind == Kind.MOVE_CANVAS
                    || field.kind == Kind.INFO || field.key == null || result.has(field.key)) continue;
            String value = field.defaultValue == null ? "" : field.defaultValue;
            try {
                result.add(field.key, new JsonParser().parse(value));
            } catch (Exception ignored) {
                result.addProperty(field.key, value);
            }
        }
        return result;
    }

    private static boolean isConditionOrWait(String type) {
        return type.startsWith("condition_") || type.startsWith("wait_until_") || "wait_combined".equals(type);
    }

    private static boolean isSimpleToggle(String type) {
        return type.startsWith("toggle_");
    }

    private static void conditionFields(List<Field> result, String type) {
        String section = type.startsWith("wait_") ? "gui.modern.path.schema.u233" : "gui.modern.path.schema.u234";
        if (type.contains("inventory_item")) {
            itemFilterList(result, section, "gui.modern.path.schema.u072", "gui.modern.path.schema.u235");
            text(result, section, "count", "gui.modern.path.schema.u236", "", "1");
            text(result, section, "inventoryRows", "gui.modern.path.schema.u068", "gui.modern.path.schema.u237", "4");
            text(result, section, "inventoryCols", "gui.modern.path.schema.u069", "gui.modern.path.schema.u237", "9");
            grid(result, section, "inventorySlots", "gui.modern.path.schema.u238", "gui.modern.path.schema.u239", "[]");
        } else if (type.contains("gui_title")) text(result, section, "title", "gui.modern.path.schema.u240", "", "");
        else if (type.contains("scoreboard")) text(result, section, "text", "gui.modern.path.schema.u241", "", "");
        else if (type.contains("packet_field")) {
            choice(result, section, "lookupMode", "gui.modern.path.schema.u242", "", "LATEST_CAPTURE", "VARIABLE");
            text(result, section, "fieldKey", "gui.modern.path.schema.u243", "", "");
            text(result, section, "expectedValue", "gui.modern.path.schema.u244", "", "");
            choice(result, section, "matchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
        } else if (type.contains("packet_text")) text(result, section, "packetText", "gui.modern.path.schema.u245", "", "");
        else if (type.contains("bossbar")) text(result, section, "text", "gui.modern.path.schema.u246", "", "");
        else if (type.contains("player_in_area")) {
            text(result, section, "center", "gui.modern.path.schema.u247", "gui.modern.path.schema.u055", "[0,0,0]");
            text(result, section, "radius", "gui.modern.path.schema.u248", "", "3");
        } else if (type.contains("player_list")) structuredList(result, section, "entries", "gui.modern.path.schema.u249",
                "gui.modern.path.schema.u250");
        else if (type.contains("entity_nearby")) {
            choice(result, section, "entityType", "gui.modern.path.schema.u134", "", "all", "player", "hostile", "passive");
            text(result, section, "entityName", "gui.modern.path.schema.u135", "", "");
            text(result, section, "radius", "gui.modern.path.schema.u056", "", "6");
            text(result, section, "minCount", "gui.modern.path.schema.u251", "", "1");
        } else if (type.contains("expression")) expressionList(result, section, "gui.modern.path.schema.u252", "gui.modern.path.schema.u098");
        else if (type.contains("screen_region")) {
            text(result, section, "regionRect", "gui.modern.path.schema.u253", "[x,y,width,height]", "[0,0,50,50]");
            choice(result, section, "visionCompareMode", "gui.modern.path.schema.u254", "", "AVERAGE_COLOR", "TEMPLATE", "EDGE_DENSITY");
            text(result, section, "targetColor", "gui.modern.path.schema.u255", "", "#FFFFFF");
            text(result, section, "colorTolerance", "gui.modern.path.schema.u256", "", "48");
            text(result, section, "imagePath", "gui.modern.path.schema.u257", "gui.modern.path.schema.u258", "");
            text(result, section, "similarityThreshold", "gui.modern.path.schema.u259", "", "0.92");
            text(result, section, "edgeThreshold", "gui.modern.path.schema.u260", "", "0.12");
        }         else if (type.contains("hud_text")) {
            text(result, section, "contains", "gui.modern.path.schema.u261", "", "");
            toggle(result, section, "matchBlock", "gui.modern.path.schema.u085", "", "false");
            text(result, section, "separator", "gui.modern.path.schema.u086", "", " | ");
        } else if (type.contains("gui_element")) {
            choice(result, section, "elementType", "gui.modern.path.schema.u262", "", "ANY", "TITLE", "BUTTON", "SLOT");
            choice(result, section, "guiElementLocatorMode", "gui.modern.path.schema.u025", "", "TEXT", "PATH");
            text(result, section, "locatorText", "gui.modern.path.schema.u026", "gui.modern.path.schema.u263", "");
            choice(result, section, "locatorMatchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
        }
        else if ("wait_combined".equals(type)) {
            choice(result, section, "combinedMode", "gui.modern.path.schema.u264", "", "ANY", "ALL");
            textExpressionList(result, section, "conditionsText", "gui.modern.path.schema.u265", "gui.modern.path.schema.u266");
            expression(result, section, "cancelExpression", "gui.modern.path.schema.u267", "gui.modern.path.schema.u268");
        }
        if ("wait_until_captured_id".equals(type)) {
            text(result, section, "capturedId", "gui.modern.path.schema.u269", "gui.modern.path.schema.u270", "");
            choice(result, section, "waitMode", "gui.modern.path.schema.u271", "", "update", "recapture");
        }
        if (type.startsWith("condition_")) text(result, "gui.modern.path.schema.u272", "skipCount", "gui.modern.path.schema.u273", "", "1");
        else {
            text(result, "gui.modern.path.schema.u274", "preExecuteCount", "gui.modern.path.schema.u275", "", "0");
            text(result, "gui.modern.path.schema.u274", "timeoutTicks", "gui.modern.path.schema.u276", "", "200");
            text(result, "gui.modern.path.schema.u274", "timeoutSkipCount", "gui.modern.path.schema.u277", "", "0");
        }
    }

    private static void captureFields(List<Field> result, String type) {
        text(result, "gui.modern.path.schema.u278", "varName", "gui.modern.path.schema.u279", "gui.modern.path.schema.u280", captureVariableDefault(type));
        if (type.contains("nearby_entity") || type.contains("entity_list")) {
            choice(result, "gui.modern.path.schema.u278", "entityType", "gui.modern.path.schema.u134", "", "all", "player", "hostile", "passive");
            text(result, "gui.modern.path.schema.u278", "entityName", "gui.modern.path.schema.u135", "", "");
            text(result, "gui.modern.path.schema.u278", "radius", "gui.modern.path.schema.u056", "", type.contains("entity_list") ? "8" : "6");
            if (type.contains("entity_list")) text(result, "gui.modern.path.schema.u278", "maxCount", "gui.modern.path.schema.u281", "", "16");
        } else if (type.contains("inventory_slot")) {
            choice(result, "gui.modern.path.schema.u278", "slotArea", "gui.modern.path.schema.u282", "", "MAIN", "HOTBAR", "ARMOR", "OFFHAND");
            text(result, "gui.modern.path.schema.u278", "slotIndex", "gui.modern.path.schema.u283", "", "0");
            grid(result, "gui.modern.path.schema.u278", "slotIndices", "gui.modern.path.schema.u284", "gui.modern.path.schema.u285", "[]");
        } else if (type.contains("packet_field")) {
            choice(result, "gui.modern.path.schema.u278", "lookupMode", "gui.modern.path.schema.u286", "", "LATEST_CAPTURE", "VARIABLE");
            text(result, "gui.modern.path.schema.u278", "fieldKey", "gui.modern.path.schema.u243", "", "");
            text(result, "gui.modern.path.schema.u278", "fallbackValue", "gui.modern.path.schema.u287", "", "");
        } else if (type.contains("gui_element")) {
            choice(result, "gui.modern.path.schema.u278", "elementType", "gui.modern.path.schema.u262", "", "ANY", "TITLE", "BUTTON", "SLOT");
            choice(result, "gui.modern.path.schema.u278", "guiElementLocatorMode", "gui.modern.path.schema.u025", "", "TEXT", "PATH");
            text(result, "gui.modern.path.schema.u278", "locatorText", "gui.modern.path.schema.u026", "", "");
            choice(result, "gui.modern.path.schema.u278", "locatorMatchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
        } else if (type.contains("scoreboard")) text(result, "gui.modern.path.schema.u278", "lineIndex", "gui.modern.path.schema.u288", "gui.modern.path.schema.u289", "-1");
        else if (type.contains("screen_region")) text(result, "gui.modern.path.schema.u278", "regionRect", "gui.modern.path.schema.u253", "", "[0,0,50,50]");
        else if (type.contains("block_at")) text(result, "gui.modern.path.schema.u278", "pos", "gui.modern.path.schema.u054", "", "[0,0,0]");
    }

    private static void itemTransferFields(List<Field> result, String section, boolean spread) {
        itemFilterList(result, section, "gui.modern.path.schema.u072", "gui.modern.path.schema.u073");
        choice(result, section, "sourceScope", "gui.modern.path.schema.u290", "", "INVENTORY", "MAIN", "HOTBAR", "CONTAINER");
        text(result, section, "sourceSlotsText", "gui.modern.path.schema.u291", "", "");
        choice(result, section, "targetScope", "gui.modern.path.schema.u231", "", "INVENTORY", "MAIN", "HOTBAR");
        text(result, section, "targetSlotsText", "gui.modern.path.schema.u213", "", "");
        text(result, "gui.modern.path.schema.u292", "delayTicks", "gui.modern.path.schema.u148", "", "1");
        toggle(result, "gui.modern.path.schema.u292", "normalizeDelayTo20Tps", "gui.modern.path.schema.u011", "", "true");
        if (spread) {
            choice(result, "gui.modern.path.schema.u292", "spreadMode", "gui.modern.path.schema.u293", "", "ONE_PER_SLOT", "EVEN_SPLIT", "FIXED_PER_SLOT");
            text(result, "gui.modern.path.schema.u292", "perSlotCount", "gui.modern.path.schema.u294", "", "1");
            toggle(result, "gui.modern.path.schema.u292", "onlyEmptySlots", "gui.modern.path.schema.u295", "", "true");
            toggle(result, "gui.modern.path.schema.u292", "preserveSourceSlot", "gui.modern.path.schema.u296", "", "true");
            toggle(result, "gui.modern.path.schema.u292", "continueOnInsufficient", "gui.modern.path.schema.u297", "", "false");
            choice(result, "gui.modern.path.schema.u292", "remainderMode", "gui.modern.path.schema.u298", "", "RETURN_SOURCE", "FIRST_EMPTY", "KEEP_CURSOR");
        }
    }

    private static void slotFields(List<Field> result, String section) {
        choice(result, section, "locatorMode", "gui.modern.path.schema.u299", "", "DIRECT_SLOT", "ITEM_TEXT", "EMPTY_SLOT", "SLOT_PATH");
        text(result, section, "slot", "gui.modern.path.schema.u283", "gui.modern.path.schema.u300", "0");
        choice(result, section, "slotBase", "gui.modern.path.schema.u301", "", "DEC", "HEX");
        text(result, section, "locatorText", "gui.modern.path.schema.u026", "gui.modern.path.schema.u302", "");
        choice(result, section, "locatorMatchMode", "gui.modern.path.schema.u028", "", "CONTAINS", "EXACT");
    }

    private static void text(List<Field> result, String section, String key, String label, String hint, String value) {
        result.add(new Field(section, key, label, hint, value, Kind.TEXT, null));
    }

    private static void toggle(List<Field> result, String section, String key, String label, String hint, String value) {
        result.add(new Field(section, key, label, hint, value, Kind.TOGGLE, null));
    }

    private static void choice(List<Field> result, String section, String key, String label, String hint, String... values) {
        result.add(new Field(section, key, label, hint, values.length == 0 ? "" : values[0], Kind.CHOICE,
                Arrays.asList(values)));
    }

    private static void keyboard(List<Field> result, String section, String key, String label, String hint,
            String value) {
        result.add(new Field(section, key, label, hint, value, Kind.KEYBOARD_PICKER, null));
    }

    private static void grid(List<Field> result, String section, String key, String label, String hint, String value) {
        result.add(new Field(section, key, label, hint, value, Kind.SLOT_GRID, null));
    }

    private static void structuredList(List<Field> result, String section, String key, String label, String hint) {
        result.add(new Field(section, key, label, hint, "[]", Kind.STRUCTURED_LIST, null));
    }

    private static void expressionList(List<Field> result, String section, String label, String hint) {
        result.add(new Field(section, "expressions", label, hint, "", Kind.EXPRESSION_LIST, null));
    }

    private static void expression(List<Field> result, String section, String key, String label, String hint) {
        result.add(new Field(section, key, label, hint, "", Kind.EXPRESSION, null));
    }

    private static void textExpressionList(List<Field> result, String section, String key, String label, String hint) {
        result.add(new Field(section, key, label, hint, "", Kind.TEXT_EXPRESSION_LIST, null));
    }

    private static void itemFilterList(List<Field> result, String section, String label, String hint) {
        result.add(new Field(section, "itemFilterExpressions", label, hint, "", Kind.ITEM_FILTER_LIST, null));
    }

    private static String captureVariableDefault(String type) {
        if (type.contains("nearby_entity")) return "entity";
        if (type.contains("gui_title")) return "gui_title";
        if (type.contains("inventory_slot")) return "slot";
        if (type.contains("hotbar")) return "hotbar";
        if (type.contains("entity_list")) return "entities";
        if (type.contains("packet_field")) return "packet_field";
        if (type.contains("gui_element")) return "gui_element";
        if (type.contains("scoreboard")) return "scoreboard";
        if (type.contains("screen_region")) return "vision_region";
        if (type.contains("block_at")) return "block";
        return "captured";
    }
}
