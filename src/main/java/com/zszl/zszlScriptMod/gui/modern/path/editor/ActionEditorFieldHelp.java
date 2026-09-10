package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.resources.I18n;

/**
 * Resolves hover-help text for modern action-editor fields from existing i18n
 * keys. JSON values and field keys stay unchanged.
 */
public final class ActionEditorFieldHelp {
    private static final String PREFIX = "gui.path.action_editor.help.";
    private static final Map<String, String> ALIASES = aliases();

    private ActionEditorFieldHelp() {
    }

    public static String tooltip(String actionType, String fieldKey, String hint, Object... formatArgs) {
        String translated = translate(candidates(actionType, fieldKey), formatArgs);
        if (!translated.isEmpty()) {
            return translated;
        }
        return hint == null ? "" : hint.trim();
    }

    private static String translate(String[] suffixes, Object... formatArgs) {
        if (suffixes == null) {
            return "";
        }
        for (String suffix : suffixes) {
            if (suffix == null || suffix.isEmpty()) {
                continue;
            }
            String key = PREFIX + suffix;
            String text = formatArgs != null && formatArgs.length > 0
                    ? I18n.format(key, formatArgs) : I18n.format(key);
            if (text != null && !text.isEmpty() && !text.equals(key)) {
                return text;
            }
        }
        return "";
    }

    private static String[] candidates(String actionType, String fieldKey) {
        if (fieldKey == null || fieldKey.isEmpty()) {
            return new String[0];
        }
        String typeSpecific = typeSpecific(actionType, fieldKey);
        String alias = ALIASES.get(fieldKey);
        String snake = snake(fieldKey);
        return new String[] { typeSpecific, alias, snake };
    }

    private static String typeSpecific(String actionType, String fieldKey) {
        String type = actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
        if ("locatorMode".equals(fieldKey)) {
            if ("click".equals(type)) {
                return "screen_locator_mode";
            }
            if ("rightclickblock".equals(type) || "rightclickentity".equals(type)) {
                return "target_locator_mode";
            }
            return "slot_locator_mode";
        }
        if ("locatorText".equals(fieldKey)) {
            if ("rightclickblock".equals(type) || "rightclickentity".equals(type)) {
                return "world_locator_text";
            }
            if (type.contains("gui_element")) {
                return "capture_gui_element_locator_text";
            }
            return "locator_text";
        }
        if ("ticks".equals(fieldKey) && "delay".equals(type)) {
            return "delay_ticks";
        }
        if ("delayTicks".equals(fieldKey)) {
            if ("autochestclick".equals(type)) {
                return "chest_click_delay_ticks";
            }
            if ("move_inventory_items_to_chest_slots".equals(type)) {
                return "move_chest_delay_ticks";
            }
            return "spread_delay_ticks";
        }
        if ("count".equals(fieldKey)) {
            if ("jump".equals(type)) {
                return "jump_count";
            }
            if ("blocknextgui".equals(type)) {
                return "block_count";
            }
        }
        if ("intervalTicks".equals(fieldKey) && "jump".equals(type)) {
            return "jump_interval_ticks";
        }
        if ("contains".equals(fieldKey) && type.contains("gui_title")) {
            return "gui_title_contains";
        }
        if ("center".equals(fieldKey)) {
            return "area_center";
        }
        if ("range".equals(fieldKey) && (type.contains("player_in_area") || type.contains("area"))) {
            return "area_radius";
        }
        if ("expression".equals(fieldKey) && "set_var".equals(type)) {
            return "set_var_expression";
        }
        if ("name".equals(fieldKey) && "set_var".equals(type)) {
            return "variable_name";
        }
        if ("slot".equals(fieldKey) && type.startsWith("capture")) {
            return "capture_slot_index";
        }
        if ("varName".equals(fieldKey)) {
            return "capture_var_name";
        }
        return null;
    }

    private static Map<String, String> aliases() {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put("message", "system_message");
        aliases.put("key", "key_name");
        aliases.put("state", "key_state");
        aliases.put("left", "left_click");
        aliases.put("windowId", "window_id_optional");
        aliases.put("contains", "contains_text");
        aliases.put("onlyOnSlotChange", "window_click_only_on_slot_change");
        aliases.put("pos", "target_pos");
        aliases.put("fromVar", "source_var");
        aliases.put("sourceVar", "source_var");
        aliases.put("itemFilterExpressions", "item_filter_expression_cards");
        aliases.put("expressions", "boolean_expression_cards");
        aliases.put("conditionsText", "wait_combined_expressions");
        aliases.put("combinedMode", "wait_combined_mode");
        aliases.put("minCount", "condition_item_count");
        aliases.put("slotArea", "capture_slot_area");
        aliases.put("maxCount", "capture_max_count");
        aliases.put("lookupMode", "capture_packet_field_lookup_mode");
        aliases.put("fieldKey", "capture_packet_field_key");
        aliases.put("fallback", "capture_packet_field_fallback");
        aliases.put("regionRect", "vision_region_rect");
        aliases.put("visionCompareMode", "vision_compare_mode");
        aliases.put("targetColor", "vision_target_color");
        aliases.put("colorTolerance", "vision_color_tolerance");
        aliases.put("templatePath", "vision_template_path");
        aliases.put("similarityThreshold", "vision_similarity_threshold");
        aliases.put("edgeThreshold", "vision_edge_threshold");
        aliases.put("elementType", "gui_element_type");
        aliases.put("guiElementLocatorMode", "gui_element_locator_mode");
        aliases.put("lineIndex", "capture_scoreboard_line_index");
        aliases.put("tempslot", "temp_hotbar_slot");
        aliases.put("moveDirection", "move_chest_direction");
        aliases.put("itemName", "item_name");
        aliases.put("matchMode", "match_mode");
        aliases.put("useMode", "use_mode");
        aliases.put("useCount", "use_count");
        aliases.put("labelName", "label_name");
        aliases.put("targetActionIndex", "target_action_index");
        aliases.put("targetLabel", "target_label");
        aliases.put("skipCount", "skip_count");
        aliases.put("timeoutTicks", "timeout_ticks");
        aliases.put("timeoutSkipCount", "timeout_skip_count");
        aliases.put("foodKeywordsText", "food_keywords_text");
        aliases.put("setName", "set_name");
        aliases.put("sourceScope", "spread_source_scope");
        aliases.put("sourceSlotsText", "spread_source_slots");
        aliases.put("targetScope", "spread_target_scope");
        aliases.put("targetSlotsText", "spread_target_slots");
        aliases.put("spreadMode", "spread_mode");
        aliases.put("perSlotCount", "spread_per_slot_count");
        aliases.put("onlyEmptySlots", "spread_only_empty_slots");
        aliases.put("preserveSourceSlot", "spread_preserve_source_slot");
        aliases.put("continueOnInsufficient", "spread_continue_insufficient");
        aliases.put("remainderMode", "spread_remainder_mode");
        aliases.put("requiredNbtTagsMode", "required_nbt_tags_mode");
        aliases.put("itemDisplayName", "move_chest_item_name");
        aliases.put("requiredNbtTags", "move_chest_required_nbt_tags");
        return aliases;
    }

    private static String snake(String key) {
        StringBuilder out = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++) {
            char value = key.charAt(i);
            if (value == '.') {
                out.append('_');
            } else if (Character.isUpperCase(value)) {
                if (i > 0) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(value));
            } else {
                out.append(value);
            }
        }
        return out.toString();
    }
}
