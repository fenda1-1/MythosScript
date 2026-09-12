// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/BindableAction.java
package com.zszl.zszlScriptMod.system;

import net.minecraft.client.resources.I18n;

/**
 * 定义所有可以被快捷键绑定的动作。
 * 每个枚举都包含一个显示名称和描述。
 */
public enum BindableAction {
    OPEN_COMMAND_PALETTE("keybind.action.open_command_palette.name", "keybind.action.open_command_palette.desc"),
    CLOSE_ACTIVE_TAB("keybind.action.close_active_tab.name", "keybind.action.close_active_tab.desc"),
    CLOSE_ALL_TABS("keybind.action.close_all_tabs.name", "keybind.action.close_all_tabs.desc"),
    RESTORE_CLOSED_TAB("keybind.action.restore_closed_tab.name", "keybind.action.restore_closed_tab.desc"),
    OPEN_RECENT_COMMAND("keybind.action.open_recent_command.name", "keybind.action.open_recent_command.desc"),
    FOCUS_GLOBAL_SEARCH("keybind.action.focus_global_search.name", "keybind.action.focus_global_search.desc"),
    OPEN_RECENT_SEQUENCE("keybind.action.open_recent_sequence.name", "keybind.action.open_recent_sequence.desc"),
    SAVE_CONFIGURATIONS("keybind.action.save_configurations.name", "keybind.action.save_configurations.desc"),
    EMERGENCY_STOP("keybind.action.emergency_stop.name", "keybind.action.emergency_stop.desc"),
    NEXT_TAB("keybind.action.next_tab.name", "keybind.action.next_tab.desc"),
    PREVIOUS_TAB("keybind.action.previous_tab.name", "keybind.action.previous_tab.desc"),
    TOGGLE_AUTOMATION_PAUSE("keybind.action.toggle_automation_pause.name", "keybind.action.toggle_automation_pause.desc"),
    RESET_WINDOW_LAYOUT("keybind.action.reset_window_layout.name", "keybind.action.reset_window_layout.desc"),
    TOGGLE_TAB_FULLSCREEN("keybind.action.toggle_tab_fullscreen.name", "keybind.action.toggle_tab_fullscreen.desc"),
    INCREASE_MODERN_UI_SCALE("keybind.action.increase_modern_ui_scale.name",
            "keybind.action.increase_modern_ui_scale.desc"),
    DECREASE_MODERN_UI_SCALE("keybind.action.decrease_modern_ui_scale.name",
            "keybind.action.decrease_modern_ui_scale.desc"),
    RESET_MODERN_UI_SCALE("keybind.action.reset_modern_ui_scale.name",
            "keybind.action.reset_modern_ui_scale.desc"),
    DETACH_MAIN_WINDOW("keybind.action.detach_main_window.name", "keybind.action.detach_main_window.desc"),
    SELECT_TAB_SLOT_1("keybind.action.select_tab_slot_1.name", "keybind.action.select_tab_slot_1.desc"),
    SELECT_TAB_SLOT_2("keybind.action.select_tab_slot_2.name", "keybind.action.select_tab_slot_2.desc"),
    SELECT_TAB_SLOT_3("keybind.action.select_tab_slot_3.name", "keybind.action.select_tab_slot_3.desc"),
    SELECT_TAB_SLOT_4("keybind.action.select_tab_slot_4.name", "keybind.action.select_tab_slot_4.desc"),
    SELECT_TAB_SLOT_5("keybind.action.select_tab_slot_5.name", "keybind.action.select_tab_slot_5.desc"),
    SELECT_TAB_SLOT_6("keybind.action.select_tab_slot_6.name", "keybind.action.select_tab_slot_6.desc"),
    SELECT_TAB_SLOT_7("keybind.action.select_tab_slot_7.name", "keybind.action.select_tab_slot_7.desc"),
    SELECT_TAB_SLOT_8("keybind.action.select_tab_slot_8.name", "keybind.action.select_tab_slot_8.desc"),
    SELECT_TAB_SLOT_9("keybind.action.select_tab_slot_9.name", "keybind.action.select_tab_slot_9.desc"),
    RUN_FAVORITE_SEQUENCE_1("keybind.action.run_favorite_sequence_1.name", "keybind.action.run_favorite_sequence_1.desc"),
    RUN_FAVORITE_SEQUENCE_2("keybind.action.run_favorite_sequence_2.name", "keybind.action.run_favorite_sequence_2.desc"),
    RUN_FAVORITE_SEQUENCE_3("keybind.action.run_favorite_sequence_3.name", "keybind.action.run_favorite_sequence_3.desc"),
    RUN_FAVORITE_SEQUENCE_4("keybind.action.run_favorite_sequence_4.name", "keybind.action.run_favorite_sequence_4.desc"),
    RUN_FAVORITE_SEQUENCE_5("keybind.action.run_favorite_sequence_5.name", "keybind.action.run_favorite_sequence_5.desc"),
    RUN_FAVORITE_SEQUENCE_6("keybind.action.run_favorite_sequence_6.name", "keybind.action.run_favorite_sequence_6.desc"),
    RUN_FAVORITE_SEQUENCE_7("keybind.action.run_favorite_sequence_7.name", "keybind.action.run_favorite_sequence_7.desc"),
    RUN_FAVORITE_SEQUENCE_8("keybind.action.run_favorite_sequence_8.name", "keybind.action.run_favorite_sequence_8.desc"),
    RUN_FAVORITE_SEQUENCE_9("keybind.action.run_favorite_sequence_9.name", "keybind.action.run_favorite_sequence_9.desc"),
    STOP_SEQUENCE("keybind.action.stop_sequence.name", "keybind.action.stop_sequence.desc"),
    SET_LOOP_COUNT("keybind.action.set_loop_count.name", "keybind.action.set_loop_count.desc"),
    TOGGLE_MOUSE_DETACH("keybind.action.toggle_mouse_detach.name", "keybind.action.toggle_mouse_detach.desc"),
    OPEN_INVENTORY_VIEWER("keybind.action.open_inventory_viewer.name", "keybind.action.open_inventory_viewer.desc"),
    TOGGLE_AUTO_EAT("keybind.action.toggle_auto_eat.name", "keybind.action.toggle_auto_eat.desc"),
    TOGGLE_AUTO_FISHING("keybind.action.toggle_auto_fishing.name", "keybind.action.toggle_auto_fishing.desc"),
    TOGGLE_FLY("keybind.action.toggle_fly.name", "keybind.action.toggle_fly.desc"),
    TOGGLE_KILL_AURA("keybind.action.toggle_kill_aura.name", "keybind.action.toggle_kill_aura.desc"),
    TOGGLE_AUTO_FOLLOW("keybind.action.toggle_auto_follow.name", "keybind.action.toggle_auto_follow.desc"),
    TOGGLE_AUTO_PICKUP("keybind.action.toggle_auto_pickup.name", "keybind.action.toggle_auto_pickup.desc"),
    TOGGLE_PACKET_CAPTURE("keybind.action.toggle_packet_capture.name", "keybind.action.toggle_packet_capture.desc"),
    TOGGLE_MOVEMENT_SPEED("keybind.action.toggle_movement_speed.name", "keybind.action.toggle_movement_speed.desc",
            FeatureGroup.MOVEMENT, "speed"),
    TOGGLE_MOVEMENT_NO_SLOW("keybind.action.toggle_movement_no_slow.name",
            "keybind.action.toggle_movement_no_slow.desc", FeatureGroup.MOVEMENT, "no_slow"),
    TOGGLE_MOVEMENT_FORCE_SPRINT("keybind.action.toggle_movement_force_sprint.name",
            "keybind.action.toggle_movement_force_sprint.desc", FeatureGroup.MOVEMENT, "force_sprint"),
    TOGGLE_MOVEMENT_GUI_MOVE("keybind.action.toggle_movement_gui_move.name",
            "keybind.action.toggle_movement_gui_move.desc", FeatureGroup.MOVEMENT, "gui_move"),
    TOGGLE_MOVEMENT_AUTO_STEP("keybind.action.toggle_movement_auto_step.name",
            "keybind.action.toggle_movement_auto_step.desc", FeatureGroup.MOVEMENT, "auto_step"),
    TOGGLE_MOVEMENT_WALL_CLIMB("keybind.action.toggle_movement_wall_climb.name",
            "keybind.action.toggle_movement_wall_climb.desc", FeatureGroup.MOVEMENT, "wall_climb"),
    TOGGLE_MOVEMENT_FREECAM("keybind.action.toggle_movement_freecam.name",
            "keybind.action.toggle_movement_freecam.desc", FeatureGroup.MOVEMENT, "freecam"),
    TOGGLE_MOVEMENT_BLOCK_PHASE("keybind.action.toggle_movement_block_phase.name",
            "keybind.action.toggle_movement_block_phase.desc", FeatureGroup.MOVEMENT, "block_phase"),
    TOGGLE_MOVEMENT_LONG_JUMP("keybind.action.toggle_movement_long_jump.name",
            "keybind.action.toggle_movement_long_jump.desc", FeatureGroup.MOVEMENT, "long_jump"),
    TOGGLE_MOVEMENT_TIMER_ACCEL("keybind.action.toggle_movement_timer_accel.name",
            "keybind.action.toggle_movement_timer_accel.desc", FeatureGroup.MOVEMENT, "timer_accel"),
    TOGGLE_MOVEMENT_BLINK_MOVE("keybind.action.toggle_movement_blink_move.name",
            "keybind.action.toggle_movement_blink_move.desc", FeatureGroup.MOVEMENT, "blink_move"),
    TOGGLE_MOVEMENT_SAFE_WALK("keybind.action.toggle_movement_safe_walk.name",
            "keybind.action.toggle_movement_safe_walk.desc", FeatureGroup.MOVEMENT, "safe_walk"),
    TOGGLE_MOVEMENT_SCAFFOLD("keybind.action.toggle_movement_scaffold.name",
            "keybind.action.toggle_movement_scaffold.desc", FeatureGroup.MOVEMENT, "scaffold"),
    TOGGLE_MOVEMENT_LOW_GRAVITY("keybind.action.toggle_movement_low_gravity.name",
            "keybind.action.toggle_movement_low_gravity.desc", FeatureGroup.MOVEMENT, "low_gravity"),
    TOGGLE_MOVEMENT_ICE_BOOST("keybind.action.toggle_movement_ice_boost.name",
            "keybind.action.toggle_movement_ice_boost.desc", FeatureGroup.MOVEMENT, "ice_boost"),
    TOGGLE_MOVEMENT_LAVA_WALK("keybind.action.toggle_movement_lava_walk.name",
            "keybind.action.toggle_movement_lava_walk.desc", FeatureGroup.MOVEMENT, "lava_walk"),
    TOGGLE_MOVEMENT_AUTO_OBSTACLE_AVOID("keybind.action.toggle_movement_auto_obstacle_avoid.name",
            "keybind.action.toggle_movement_auto_obstacle_avoid.desc", FeatureGroup.MOVEMENT,
            "auto_obstacle_avoid"),
    TOGGLE_MOVEMENT_HOVER_MODE("keybind.action.toggle_movement_hover_mode.name",
            "keybind.action.toggle_movement_hover_mode.desc", FeatureGroup.MOVEMENT, "hover_mode"),
    TOGGLE_MOVEMENT_FALL_CUSHION("keybind.action.toggle_movement_fall_cushion.name",
            "keybind.action.toggle_movement_fall_cushion.desc", FeatureGroup.MOVEMENT, "fall_cushion"),
    TOGGLE_MOVEMENT_ANTI_ARROW_KNOCKBACK("keybind.action.toggle_movement_anti_arrow_knockback.name",
            "keybind.action.toggle_movement_anti_arrow_knockback.desc", FeatureGroup.MOVEMENT,
            "anti_arrow_knockback"),
    TOGGLE_BLOCK_AUTO_TOOL("keybind.action.toggle_block_auto_tool.name",
            "keybind.action.toggle_block_auto_tool.desc", FeatureGroup.BLOCK, "auto_tool"),
    TOGGLE_BLOCK_FAST_PLACE("keybind.action.toggle_block_fast_place.name",
            "keybind.action.toggle_block_fast_place.desc", FeatureGroup.BLOCK, "fast_place"),
    TOGGLE_BLOCK_PLACE_ASSIST("keybind.action.toggle_block_place_assist.name",
            "keybind.action.toggle_block_place_assist.desc", FeatureGroup.BLOCK, "place_assist"),
    TOGGLE_BLOCK_FAST_BREAK("keybind.action.toggle_block_fast_break.name",
            "keybind.action.toggle_block_fast_break.desc", FeatureGroup.BLOCK, "fast_break"),
    TOGGLE_BLOCK_BLOCK_SWAP_LOCK("keybind.action.toggle_block_block_swap_lock.name",
            "keybind.action.toggle_block_block_swap_lock.desc", FeatureGroup.BLOCK, "block_swap_lock"),
    TOGGLE_BLOCK_AUTO_LIGHT("keybind.action.toggle_block_auto_light.name",
            "keybind.action.toggle_block_auto_light.desc", FeatureGroup.BLOCK, "auto_light"),
    TOGGLE_BLOCK_BLOCK_REFILL("keybind.action.toggle_block_block_refill.name",
            "keybind.action.toggle_block_block_refill.desc", FeatureGroup.BLOCK, "block_refill"),
    TOGGLE_BLOCK_GHOST_HAND_BLOCK("keybind.action.toggle_block_ghost_hand_block.name",
            "keybind.action.toggle_block_ghost_hand_block.desc", FeatureGroup.BLOCK, "ghost_hand_block"),
    TOGGLE_BLOCK_SURROUND("keybind.action.toggle_block_surround.name",
            "keybind.action.toggle_block_surround.desc", FeatureGroup.BLOCK, "surround"),
    TOGGLE_ITEM_INVENTORY_SORT("keybind.action.toggle_item_inventory_sort.name",
            "keybind.action.toggle_item_inventory_sort.desc", FeatureGroup.ITEM, "inventory_sort"),
    TOGGLE_ITEM_CHEST_STEAL("keybind.action.toggle_item_chest_steal.name",
            "keybind.action.toggle_item_chest_steal.desc", FeatureGroup.ITEM, "chest_steal"),
    TOGGLE_ITEM_AUTO_EQUIP("keybind.action.toggle_item_auto_equip.name",
            "keybind.action.toggle_item_auto_equip.desc", FeatureGroup.ITEM, "auto_equip"),
    TOGGLE_ITEM_ALWAYS_CRITICAL("keybind.action.toggle_item_always_critical.name",
            "keybind.action.toggle_item_always_critical.desc", FeatureGroup.ITEM, "always_critical"),
    TOGGLE_ITEM_DROP_ALL("keybind.action.toggle_item_drop_all.name",
            "keybind.action.toggle_item_drop_all.desc", FeatureGroup.ITEM, "drop_all"),
    TOGGLE_ITEM_SHULKER_PREVIEW("keybind.action.toggle_item_shulker_preview.name",
            "keybind.action.toggle_item_shulker_preview.desc", FeatureGroup.ITEM, "shulker_preview"),
    TOGGLE_RENDER_BRIGHTNESS_BOOST("keybind.action.toggle_render_brightness_boost.name",
            "keybind.action.toggle_render_brightness_boost.desc", FeatureGroup.RENDER, "brightness_boost"),
    TOGGLE_RENDER_NO_FOG("keybind.action.toggle_render_no_fog.name",
            "keybind.action.toggle_render_no_fog.desc", FeatureGroup.RENDER, "no_fog"),
    TOGGLE_RENDER_ENTITY_VISUAL("keybind.action.toggle_render_entity_visual.name",
            "keybind.action.toggle_render_entity_visual.desc", FeatureGroup.RENDER, "entity_visual"),
    TOGGLE_RENDER_TRACER_LINE("keybind.action.toggle_render_tracer_line.name",
            "keybind.action.toggle_render_tracer_line.desc", FeatureGroup.RENDER, "tracer_line"),
    TOGGLE_RENDER_ENTITY_TAGS("keybind.action.toggle_render_entity_tags.name",
            "keybind.action.toggle_render_entity_tags.desc", FeatureGroup.RENDER, "entity_tags"),
    TOGGLE_RENDER_BLOCK_HIGHLIGHT("keybind.action.toggle_render_block_highlight.name",
            "keybind.action.toggle_render_block_highlight.desc", FeatureGroup.RENDER, "block_highlight"),
    TOGGLE_RENDER_ITEM_ESP("keybind.action.toggle_render_item_esp.name",
            "keybind.action.toggle_render_item_esp.desc", FeatureGroup.RENDER, "item_esp"),
    TOGGLE_RENDER_TRAJECTORY_LINE("keybind.action.toggle_render_trajectory_line.name",
            "keybind.action.toggle_render_trajectory_line.desc", FeatureGroup.RENDER, "trajectory_line"),
    TOGGLE_RENDER_CUSTOM_CROSSHAIR("keybind.action.toggle_render_custom_crosshair.name",
            "keybind.action.toggle_render_custom_crosshair.desc", FeatureGroup.RENDER, "custom_crosshair"),
    TOGGLE_RENDER_ANTI_BOB("keybind.action.toggle_render_anti_bob.name",
            "keybind.action.toggle_render_anti_bob.desc", FeatureGroup.RENDER, "anti_bob"),
    TOGGLE_RENDER_RADAR("keybind.action.toggle_render_radar.name",
            "keybind.action.toggle_render_radar.desc", FeatureGroup.RENDER, "radar"),
    TOGGLE_RENDER_PLAYER_SKELETON("keybind.action.toggle_render_player_skeleton.name",
            "keybind.action.toggle_render_player_skeleton.desc", FeatureGroup.RENDER, "player_skeleton"),
    TOGGLE_RENDER_BLOCK_OUTLINE("keybind.action.toggle_render_block_outline.name",
            "keybind.action.toggle_render_block_outline.desc", FeatureGroup.RENDER, "block_outline"),
    TOGGLE_RENDER_ENTITY_INFO("keybind.action.toggle_render_entity_info.name",
            "keybind.action.toggle_render_entity_info.desc", FeatureGroup.RENDER, "entity_info"),
    TOGGLE_WORLD_TIME_MODIFIER("keybind.action.toggle_world_time_modifier.name",
            "keybind.action.toggle_world_time_modifier.desc", FeatureGroup.WORLD, "time_modifier"),
    TOGGLE_WORLD_WEATHER_CONTROL("keybind.action.toggle_world_weather_control.name",
            "keybind.action.toggle_world_weather_control.desc", FeatureGroup.WORLD, "weather_control"),
    TOGGLE_WORLD_COORD_DISPLAY("keybind.action.toggle_world_coord_display.name",
            "keybind.action.toggle_world_coord_display.desc", FeatureGroup.WORLD, "coord_display"),
    TOGGLE_MISC_AUTO_RECONNECT("keybind.action.toggle_misc_auto_reconnect.name",
            "keybind.action.toggle_misc_auto_reconnect.desc", FeatureGroup.MISC, "auto_reconnect"),
    TOGGLE_MISC_AUTO_RESPAWN("keybind.action.toggle_misc_auto_respawn.name",
            "keybind.action.toggle_misc_auto_respawn.desc", FeatureGroup.MISC, "auto_respawn"),
    EXECUTE_SPECIFIC_PACKET_SEQUENCE("keybind.action.execute_specific_packet_sequence.name",
            "keybind.action.execute_specific_packet_sequence.desc");

    public enum FeatureGroup {
        MOVEMENT("gui.keybind.group.movement"),
        BLOCK("gui.keybind.group.block"),
        ITEM("gui.keybind.group.item"),
        RENDER("gui.keybind.group.render"),
        WORLD("gui.keybind.group.world"),
        MISC("gui.keybind.group.misc");

        private final String translationKey;

        FeatureGroup(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    private final String displayNameKey;
    private final String descriptionKey;
    private final FeatureGroup featureGroup;
    private final String featureId;

    BindableAction(String displayNameKey, String descriptionKey) {
        this(displayNameKey, descriptionKey, null, null);
    }

    BindableAction(String displayNameKey, String descriptionKey, FeatureGroup featureGroup, String featureId) {
        this.displayNameKey = displayNameKey;
        this.descriptionKey = descriptionKey;
        this.featureGroup = featureGroup;
        this.featureId = featureId == null ? "" : featureId.trim();
    }

    public String getDisplayName() {
        return I18n.format(displayNameKey);
    }

    public String getDescription() {
        return I18n.format(descriptionKey);
    }

    public FeatureGroup getFeatureGroup() {
        return featureGroup;
    }

    public String getFeatureId() {
        return featureId;
    }

    public boolean isFeatureToggle() {
        return featureGroup != null && !featureId.isEmpty();
    }

    public boolean isMovementFeatureToggle() {
        return featureGroup == FeatureGroup.MOVEMENT;
    }
}
