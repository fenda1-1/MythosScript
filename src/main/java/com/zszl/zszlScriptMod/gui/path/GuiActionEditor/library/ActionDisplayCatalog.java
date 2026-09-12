package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionDisplayCatalog {
    private static final Map<String, String> ACTION_DISPLAY_KEYS = new LinkedHashMap<String, String>();

    static {
        ACTION_DISPLAY_KEYS.put("command", "gui.path.action_editor.type.command");
        ACTION_DISPLAY_KEYS.put("system_message", "gui.path.action_editor.type.system_message");
        ACTION_DISPLAY_KEYS.put("paste_text", "gui.path.action_editor.type.paste_text");
        ACTION_DISPLAY_KEYS.put("disconnect", "gui.path.action_editor.type.disconnect");
        ACTION_DISPLAY_KEYS.put("delay", "gui.path.action_editor.type.delay");
        ACTION_DISPLAY_KEYS.put("key", "gui.path.action_editor.type.key");
        ACTION_DISPLAY_KEYS.put("jump", "gui.path.action_editor.type.jump");
        ACTION_DISPLAY_KEYS.put("click", "gui.path.action_editor.type.click");
        ACTION_DISPLAY_KEYS.put("window_click", "gui.path.action_editor.type.window_click");
        ACTION_DISPLAY_KEYS.put("conditional_window_click", "gui.path.action_editor.type.conditional_window_click");
        ACTION_DISPLAY_KEYS.put("setview", "gui.path.action_editor.type.setview");
        ACTION_DISPLAY_KEYS.put("rightclickblock", "gui.path.action_editor.type.right_click_block");
        ACTION_DISPLAY_KEYS.put("rightclickentity", "gui.path.action_editor.type.right_click_entity");
        ACTION_DISPLAY_KEYS.put("takeallitems", "gui.path.action_editor.type.take_all_items");
        ACTION_DISPLAY_KEYS.put("take_all_items_safe", "gui.path.action_editor.type.take_all_items_safe");
        ACTION_DISPLAY_KEYS.put("dropfiltereditems", "gui.path.action_editor.type.drop_filtered_items");
        ACTION_DISPLAY_KEYS.put("move_inventory_items_to_chest_slots",
                "gui.path.action_editor.type.move_inventory_items_to_chest_slots");
        ACTION_DISPLAY_KEYS.put("warehouse_auto_deposit", "gui.path.action_editor.type.warehouse_auto_deposit");
        ACTION_DISPLAY_KEYS.put("autochestclick", "gui.path.action_editor.type.auto_chest_click");
        ACTION_DISPLAY_KEYS.put("blocknextgui", "gui.path.action_editor.type.block_next_gui");
        ACTION_DISPLAY_KEYS.put("hidecurrentgui", "gui.path.action_editor.type.hide_current_gui");
        ACTION_DISPLAY_KEYS.put("showhiddengui", "gui.path.action_editor.type.show_hidden_gui");
        ACTION_DISPLAY_KEYS.put("close_container_window", "gui.path.action_editor.type.close_container_window");
        ACTION_DISPLAY_KEYS.put("hud_text_check", "gui.path.action_editor.type.hud_text_check");
        ACTION_DISPLAY_KEYS.put("condition_inventory_item", "gui.path.action_editor.type.condition_inventory_item");
        ACTION_DISPLAY_KEYS.put("condition_gui_title", "gui.path.action_editor.type.condition_gui_title");
        ACTION_DISPLAY_KEYS.put("condition_player_in_area", "gui.path.action_editor.type.condition_player_in_area");
        ACTION_DISPLAY_KEYS.put("condition_player_list", "gui.path.action_editor.type.condition_player_list");
        ACTION_DISPLAY_KEYS.put("condition_scoreboard", "gui.path.action_editor.type.condition_scoreboard");
        ACTION_DISPLAY_KEYS.put("condition_packet_field", "gui.path.action_editor.type.condition_packet_field");
        ACTION_DISPLAY_KEYS.put("condition_packet_text", "gui.path.action_editor.type.condition_packet_text");
        ACTION_DISPLAY_KEYS.put("condition_bossbar", "gui.path.action_editor.type.condition_bossbar");
        ACTION_DISPLAY_KEYS.put("condition_gui_element", "gui.path.action_editor.type.condition_gui_element");
        ACTION_DISPLAY_KEYS.put("condition_screen_region", "gui.path.action_editor.type.condition_screen_region");
        ACTION_DISPLAY_KEYS.put("condition_entity_nearby", "gui.path.action_editor.type.condition_entity_nearby");
        ACTION_DISPLAY_KEYS.put("condition_expression", "gui.path.action_editor.type.condition_expression");
        ACTION_DISPLAY_KEYS.put("wait_until_inventory_item", "gui.path.action_editor.type.wait_until_inventory_item");
        ACTION_DISPLAY_KEYS.put("wait_until_gui_title", "gui.path.action_editor.type.wait_until_gui_title");
        ACTION_DISPLAY_KEYS.put("wait_until_player_in_area", "gui.path.action_editor.type.wait_until_player_in_area");
        ACTION_DISPLAY_KEYS.put("wait_until_player_list", "gui.path.action_editor.type.wait_until_player_list");
        ACTION_DISPLAY_KEYS.put("wait_until_scoreboard", "gui.path.action_editor.type.wait_until_scoreboard");
        ACTION_DISPLAY_KEYS.put("wait_until_packet_field", "gui.path.action_editor.type.wait_until_packet_field");
        ACTION_DISPLAY_KEYS.put("wait_until_gui_element", "gui.path.action_editor.type.wait_until_gui_element");
        ACTION_DISPLAY_KEYS.put("wait_until_entity_nearby", "gui.path.action_editor.type.wait_until_entity_nearby");
        ACTION_DISPLAY_KEYS.put("wait_until_hud_text", "gui.path.action_editor.type.wait_until_hud_text");
        ACTION_DISPLAY_KEYS.put("wait_until_expression", "gui.path.action_editor.type.wait_until_expression");
        ACTION_DISPLAY_KEYS.put("wait_combined", "gui.path.action_editor.type.wait_combined");
        ACTION_DISPLAY_KEYS.put("wait_until_captured_id", "gui.path.action_editor.type.wait_until_captured_id");
        ACTION_DISPLAY_KEYS.put("wait_until_packet_text", "gui.path.action_editor.type.wait_until_packet_text");
        ACTION_DISPLAY_KEYS.put("wait_until_screen_region", "gui.path.action_editor.type.wait_until_screen_region");
        ACTION_DISPLAY_KEYS.put("set_var", "gui.path.action_editor.type.set_var");
        ACTION_DISPLAY_KEYS.put("capture_nearby_entity", "gui.path.action_editor.type.capture_nearby_entity");
        ACTION_DISPLAY_KEYS.put("capture_gui_title", "gui.path.action_editor.type.capture_gui_title");
        ACTION_DISPLAY_KEYS.put("capture_inventory_slot", "gui.path.action_editor.type.capture_inventory_slot");
        ACTION_DISPLAY_KEYS.put("capture_hotbar", "gui.path.action_editor.type.capture_hotbar");
        ACTION_DISPLAY_KEYS.put("capture_entity_list", "gui.path.action_editor.type.capture_entity_list");
        ACTION_DISPLAY_KEYS.put("capture_packet_field", "gui.path.action_editor.type.capture_packet_field");
        ACTION_DISPLAY_KEYS.put("capture_gui_element", "gui.path.action_editor.type.capture_gui_element");
        ACTION_DISPLAY_KEYS.put("capture_scoreboard", "gui.path.action_editor.type.capture_scoreboard");
        ACTION_DISPLAY_KEYS.put("capture_screen_region", "gui.path.action_editor.type.capture_screen_region");
        ACTION_DISPLAY_KEYS.put("capture_block_at", "gui.path.action_editor.type.capture_block_at");
        ACTION_DISPLAY_KEYS.put("label", "gui.path.action_editor.type.label");
        ACTION_DISPLAY_KEYS.put("goto_action", "gui.path.action_editor.type.goto_action");
        ACTION_DISPLAY_KEYS.put("goto_label", "gui.path.action_editor.type.goto_label");
        ACTION_DISPLAY_KEYS.put("if_else", "gui.path.action_editor.type.if_else");
        ACTION_DISPLAY_KEYS.put("switch_var", "gui.path.action_editor.type.switch_var");
        ACTION_DISPLAY_KEYS.put("branch_table", "gui.path.action_editor.type.branch_table");
        ACTION_DISPLAY_KEYS.put("while_condition", "gui.path.action_editor.type.while_condition");
        ACTION_DISPLAY_KEYS.put("for_each_point", "gui.path.action_editor.type.for_each_point");
        ACTION_DISPLAY_KEYS.put("for_each_list", "gui.path.action_editor.type.for_each_list");
        ACTION_DISPLAY_KEYS.put("retry_block", "gui.path.action_editor.type.retry_block");
        ACTION_DISPLAY_KEYS.put("debug_print_var", "gui.path.action_editor.type.debug_print_var");
        ACTION_DISPLAY_KEYS.put("debug_print_nearby_entities", "gui.path.action_editor.type.debug_print_nearby_entities");
        ACTION_DISPLAY_KEYS.put("debug_print_gui_summary", "gui.path.action_editor.type.debug_print_gui_summary");
        ACTION_DISPLAY_KEYS.put("skip_actions", "gui.path.action_editor.type.skip_actions");
        ACTION_DISPLAY_KEYS.put("skip_steps", "gui.path.action_editor.type.skip_steps");
        ACTION_DISPLAY_KEYS.put("repeat_actions", "gui.path.action_editor.type.repeat_actions");
        ACTION_DISPLAY_KEYS.put("restart_sequence", "gui.path.action_editor.type.restart_sequence");
        ACTION_DISPLAY_KEYS.put("no_stop_navigation", "gui.path.action_editor.type.no_stop_navigation");
        ACTION_DISPLAY_KEYS.put("autoeat", "gui.path.action_editor.type.autoeat");
        ACTION_DISPLAY_KEYS.put("autoequip", "gui.path.action_editor.type.autoequip");
        ACTION_DISPLAY_KEYS.put("autopickup", "gui.path.action_editor.type.autopickup");
        ACTION_DISPLAY_KEYS.put("toggle_autoeat", "gui.path.action_editor.type.toggle_autoeat");
        ACTION_DISPLAY_KEYS.put("toggle_autofishing", "gui.path.action_editor.type.toggle_autofishing");
        ACTION_DISPLAY_KEYS.put("toggle_auto_pickup", "gui.path.action_editor.type.toggle_auto_pickup");
        ACTION_DISPLAY_KEYS.put("toggle_kill_aura", "gui.path.action_editor.type.toggle_kill_aura");
        ACTION_DISPLAY_KEYS.put("toggle_auto_follow", "gui.path.action_editor.type.toggle_auto_follow");
        ACTION_DISPLAY_KEYS.put("toggle_fly", "gui.path.action_editor.type.toggle_fly");
        ACTION_DISPLAY_KEYS.put("toggle_conditional_execution", "gui.path.action_editor.type.toggle_conditional_execution");
        ACTION_DISPLAY_KEYS.put("toggle_auto_escape", "gui.path.action_editor.type.toggle_auto_escape");
        ACTION_DISPLAY_KEYS.put("toggle_baritone_free_look", "gui.path.action_editor.type.toggle_baritone_free_look");
        ACTION_DISPLAY_KEYS.put("toggle_baritone_human_like", "gui.path.action_editor.type.toggle_baritone_human_like");
        ACTION_DISPLAY_KEYS.put("toggle_baritone_flight", "gui.path.action_editor.type.toggle_baritone_flight");
        ACTION_DISPLAY_KEYS.put("toggle_other_feature", "gui.path.action_editor.type.toggle_other_feature");
        ACTION_DISPLAY_KEYS.put("hunt", "gui.path.action_editor.type.hunt");
        ACTION_DISPLAY_KEYS.put("follow_entity", "gui.path.action_editor.type.follow_entity");
        ACTION_DISPLAY_KEYS.put("use_hotbar_item", "gui.path.action_editor.type.use_hotbar_item");
        ACTION_DISPLAY_KEYS.put("use_held_item", "gui.path.action_editor.type.use_held_item");
        ACTION_DISPLAY_KEYS.put("move_inventory_item_to_hotbar",
                "gui.path.action_editor.type.move_inventory_item_to_hotbar");
        ACTION_DISPLAY_KEYS.put("spread_inventory_item", "gui.path.action_editor.type.spread_inventory_item");
        ACTION_DISPLAY_KEYS.put("stack_inventory_item", "gui.path.action_editor.type.stack_inventory_item");
        ACTION_DISPLAY_KEYS.put("pickup_nearby_items", "gui.path.action_editor.type.pickup_nearby_items");
        ACTION_DISPLAY_KEYS.put("switch_hotbar_slot", "gui.path.action_editor.type.switch_hotbar_slot");
        ACTION_DISPLAY_KEYS.put("silentuse", "gui.path.action_editor.type.silentuse");
        ACTION_DISPLAY_KEYS.put("runlastsequence", "gui.path.action_editor.type.runlastsequence");
        ACTION_DISPLAY_KEYS.put("send_packet", "gui.path.action_editor.type.send_packet");
        ACTION_DISPLAY_KEYS.put("run_sequence", "gui.path.action_editor.type.run_sequence");
        ACTION_DISPLAY_KEYS.put("stop_current_sequence", "gui.path.action_editor.type.stop_current_sequence");
        ACTION_DISPLAY_KEYS.put("sequence_control", "gui.path.action_editor.type.sequence_control");
    }

    private ActionDisplayCatalog() {
    }

    public static Map<String, String> getActionDisplayKeys() {
        return Collections.unmodifiableMap(ACTION_DISPLAY_KEYS);
    }

    public static List<String> getOrderedActionTypes() {
        return new ArrayList<String>(ACTION_DISPLAY_KEYS.keySet());
    }
}
