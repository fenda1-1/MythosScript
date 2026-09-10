package com.zszl.zszlScriptMod.gui.path.GuiActionEditor.util;

import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.handlers.ItemSpreadHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.locator.ActionTargetLocator;

import net.minecraft.client.resources.I18n;

import java.util.Locale;

public final class ActionEditorDisplayConverters {
    private ActionEditorDisplayConverters() {
    }

    public static String stateToDisplay(String state) {
        if ("Up".equalsIgnoreCase(state) || "RobotUp".equalsIgnoreCase(state)) {
            return "抬起";
        }
        if ("Press".equalsIgnoreCase(state)) {
            return "单击";
        }
        return "按下";
    }

    public static String displayToState(String display) {
        if ("抬起".equals(display)) {
            return "Up";
        }
        if ("单击".equals(display)) {
            return "Press";
        }
        return "Down";
    }

    public static String leftToDisplay(String leftRaw) {
        if ("shift_left".equalsIgnoreCase(leftRaw) || "shift-left".equalsIgnoreCase(leftRaw)) {
            return "Shift左键";
        }
        if ("shift_right".equalsIgnoreCase(leftRaw) || "shift-right".equalsIgnoreCase(leftRaw)) {
            return "Shift右键";
        }
        if ("middle".equalsIgnoreCase(leftRaw)) {
            return "中键";
        }
        if ("right".equalsIgnoreCase(leftRaw)) {
            return "右键";
        }
        return "false".equalsIgnoreCase(leftRaw) ? "右键" : "左键";
    }

    public static String displayToMouseButton(String display) {
        if ("Shift左键".equals(display)) {
            return "shift_left";
        }
        if ("Shift右键".equals(display)) {
            return "shift_right";
        }
        if ("中键".equals(display)) {
            return "middle";
        }
        if ("右键".equals(display)) {
            return "right";
        }
        return "left";
    }

    public static String clickTypeToDisplay(String clickType) {
        return ModUtils.clickTypeToDisplayName(clickType);
    }

    public static String clickTypeToDisplay(String clickType, int button) {
        return com.zszl.zszlScriptMod.gui.modern.path.editor.ClickSemantics.display(clickType, button);
    }

    public static String displayToClickType(String display) {
        return ModUtils.normalizeClickTypeName(display);
    }

    public static boolean displayUsesRightMouseButton(String display) {
        return "右键".equals(display) || "Shift右键".equals(display);
    }

    public static String directionToDisplay(String direction) {
        return "S2C".equalsIgnoreCase(direction)
                ? I18n.format("path.action.desc.send_packet.direction.s2c")
                : I18n.format("path.action.desc.send_packet.direction.c2s");
    }

    public static String displayToDirection(String display) {
        return I18n.format("path.action.desc.send_packet.direction.s2c").equals(display) ? "S2C" : "C2S";
    }

    public static String matchModeToDisplay(String mode) {
        return "EXACT".equalsIgnoreCase(mode)
                ? I18n.format("gui.autouseitem.match.exact")
                : I18n.format("gui.autouseitem.match.contains");
    }

    public static String displayToMatchMode(String display) {
        return I18n.format("gui.autouseitem.match.exact").equals(display) ? "EXACT" : "CONTAINS";
    }

    public static String useModeToDisplay(String mode) {
        return "LEFT_CLICK".equalsIgnoreCase(mode)
                ? I18n.format("gui.autouseitem.mode.left")
                : I18n.format("gui.autouseitem.mode.right");
    }

    public static String displayToUseMode(String display) {
        return I18n.format("gui.autouseitem.mode.left").equals(display) ? "LEFT_CLICK" : "RIGHT_CLICK";
    }

    public static String valueTypeToDisplay(String type) {
        if ("STRING".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.string");
        }
        if ("NUMBER".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.number");
        }
        if ("BOOLEAN".equalsIgnoreCase(type) || "BOOL".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.boolean");
        }
        return I18n.format("gui.path.action_editor.option.auto");
    }

    public static String displayToValueType(String display) {
        if (I18n.format("gui.path.action_editor.option.string").equals(display)) {
            return "STRING";
        }
        if (I18n.format("gui.path.action_editor.option.number").equals(display)) {
            return "NUMBER";
        }
        if (I18n.format("gui.path.action_editor.option.boolean").equals(display)) {
            return "BOOLEAN";
        }
        return "AUTO";
    }

    public static String capturedIdWaitModeToDisplay(String mode) {
        return "recapture".equalsIgnoreCase(mode) ? "等待重新捕获" : "等待更新";
    }

    public static String displayToCapturedIdWaitMode(String display) {
        return "等待重新捕获".equals(display) ? "recapture" : "update";
    }

    public static String waitCombinedModeToDisplay(String mode) {
        return "ALL".equalsIgnoreCase(mode)
                ? I18n.format("gui.path.action_editor.option.wait_combined_mode.all")
                : I18n.format("gui.path.action_editor.option.wait_combined_mode.any");
    }

    public static String displayToWaitCombinedMode(String display) {
        return I18n.format("gui.path.action_editor.option.wait_combined_mode.all").equals(display)
                ? "ALL"
                : "ANY";
    }

    public static String runSequenceExecuteModeToDisplay(String mode) {
        return "interval".equalsIgnoreCase(mode)
                ? I18n.format("gui.path.action_editor.option.run_sequence_execute_interval")
                : I18n.format("gui.path.action_editor.option.run_sequence_execute_always");
    }

    public static String displayToRunSequenceExecuteMode(String display) {
        return I18n.format("gui.path.action_editor.option.run_sequence_execute_interval").equals(display)
                ? "interval"
                : "always";
    }

    public static String stopCurrentSequenceScopeToDisplay(String scope) {
        return "background".equalsIgnoreCase(scope)
                ? I18n.format("gui.path.action_editor.option.stop_sequence_scope_background")
                : I18n.format("gui.path.action_editor.option.stop_sequence_scope_foreground");
    }

    public static String displayToStopCurrentSequenceScope(String display) {
        return I18n.format("gui.path.action_editor.option.stop_sequence_scope_background").equals(display)
                ? "background"
                : "foreground";
    }

    public static String sequenceControlOperationToDisplay(String operation) {
        return "resume".equalsIgnoreCase(operation)
                ? I18n.format("gui.path.action_editor.option.sequence_control_operation.resume")
                : I18n.format("gui.path.action_editor.option.sequence_control_operation.pause");
    }

    public static String displayToSequenceControlOperation(String display) {
        return I18n.format("gui.path.action_editor.option.sequence_control_operation.resume").equals(display)
                ? "resume"
                : "pause";
    }

    public static String moveChestDirectionToDisplay(String direction) {
        return ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(direction)
                ? I18n.format("gui.path.action_editor.option.move_chest_direction.chest_to_inventory")
                : I18n.format("gui.path.action_editor.option.move_chest_direction.inventory_to_chest");
    }

    public static String displayToMoveChestDirection(String display) {
        return I18n.format("gui.path.action_editor.option.move_chest_direction.chest_to_inventory").equals(display)
                ? ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY
                : ItemFilterHandler.MOVE_DIRECTION_INVENTORY_TO_CHEST;
    }

    public static String moveChestNbtModeToDisplay(String mode) {
        return "NOT_CONTAINS".equalsIgnoreCase(mode)
                ? I18n.format("gui.path.action_editor.option.nbt_tag_mode.not_contains")
                : I18n.format("gui.path.action_editor.option.nbt_tag_mode.contains");
    }

    public static String displayToMoveChestNbtMode(String display) {
        return I18n.format("gui.path.action_editor.option.nbt_tag_mode.not_contains").equals(display)
                ? "NOT_CONTAINS"
                : "CONTAINS";
    }

    public static String spreadSourceScopeToDisplay(String scope) {
        if (ItemSpreadHandler.SOURCE_SCOPE_MAIN.equalsIgnoreCase(scope)) {
            return I18n.format("gui.path.action_editor.option.spread_source.main");
        }
        if (ItemSpreadHandler.SOURCE_SCOPE_HOTBAR.equalsIgnoreCase(scope)) {
            return I18n.format("gui.path.action_editor.option.spread_source.hotbar");
        }
        if (ItemSpreadHandler.SOURCE_SCOPE_CONTAINER.equalsIgnoreCase(scope)) {
            return I18n.format("gui.path.action_editor.option.spread_source.container");
        }
        return I18n.format("gui.path.action_editor.option.spread_source.inventory");
    }

    public static String displayToSpreadSourceScope(String display) {
        if (I18n.format("gui.path.action_editor.option.spread_source.main").equals(display)) {
            return ItemSpreadHandler.SOURCE_SCOPE_MAIN;
        }
        if (I18n.format("gui.path.action_editor.option.spread_source.hotbar").equals(display)) {
            return ItemSpreadHandler.SOURCE_SCOPE_HOTBAR;
        }
        if (I18n.format("gui.path.action_editor.option.spread_source.container").equals(display)) {
            return ItemSpreadHandler.SOURCE_SCOPE_CONTAINER;
        }
        return ItemSpreadHandler.SOURCE_SCOPE_INVENTORY;
    }

    public static String spreadTargetScopeToDisplay(String scope) {
        if (ItemSpreadHandler.TARGET_SCOPE_MAIN.equalsIgnoreCase(scope)) {
            return I18n.format("gui.path.action_editor.option.spread_target.main");
        }
        if (ItemSpreadHandler.TARGET_SCOPE_HOTBAR.equalsIgnoreCase(scope)) {
            return I18n.format("gui.path.action_editor.option.spread_target.hotbar");
        }
        return I18n.format("gui.path.action_editor.option.spread_target.inventory");
    }

    public static String displayToSpreadTargetScope(String display) {
        if (I18n.format("gui.path.action_editor.option.spread_target.main").equals(display)) {
            return ItemSpreadHandler.TARGET_SCOPE_MAIN;
        }
        if (I18n.format("gui.path.action_editor.option.spread_target.hotbar").equals(display)) {
            return ItemSpreadHandler.TARGET_SCOPE_HOTBAR;
        }
        return ItemSpreadHandler.TARGET_SCOPE_INVENTORY;
    }

    public static String spreadModeToDisplay(String mode) {
        if (ItemSpreadHandler.MODE_EVEN_SPLIT.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_mode.even");
        }
        if (ItemSpreadHandler.MODE_FIXED_PER_SLOT.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_mode.fixed");
        }
        return I18n.format("gui.path.action_editor.option.spread_mode.one");
    }

    public static String displayToSpreadMode(String display) {
        if (I18n.format("gui.path.action_editor.option.spread_mode.even").equals(display)) {
            return ItemSpreadHandler.MODE_EVEN_SPLIT;
        }
        if (I18n.format("gui.path.action_editor.option.spread_mode.fixed").equals(display)) {
            return ItemSpreadHandler.MODE_FIXED_PER_SLOT;
        }
        return ItemSpreadHandler.MODE_ONE_PER_SLOT;
    }

    public static String spreadRemainderModeToDisplay(String mode) {
        if (ItemSpreadHandler.REMAINDER_FIRST_EMPTY.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_remainder.first_empty");
        }
        if (ItemSpreadHandler.REMAINDER_KEEP_CURSOR.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_remainder.keep_cursor");
        }
        return I18n.format("gui.path.action_editor.option.spread_remainder.source");
    }

    public static String displayToSpreadRemainderMode(String display) {
        if (I18n.format("gui.path.action_editor.option.spread_remainder.first_empty").equals(display)) {
            return ItemSpreadHandler.REMAINDER_FIRST_EMPTY;
        }
        if (I18n.format("gui.path.action_editor.option.spread_remainder.keep_cursor").equals(display)) {
            return ItemSpreadHandler.REMAINDER_KEEP_CURSOR;
        }
        return ItemSpreadHandler.REMAINDER_RETURN_SOURCE;
    }

    public static String captureSlotAreaToDisplay(String area) {
        if ("HOTBAR".equalsIgnoreCase(area)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.hotbar").equals(area)) {
            return I18n.format("gui.path.action_editor.option.capture_slot_area.hotbar");
        }
        if ("ARMOR".equalsIgnoreCase(area)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.armor").equals(area)) {
            return I18n.format("gui.path.action_editor.option.capture_slot_area.armor");
        }
        if ("OFFHAND".equalsIgnoreCase(area)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.offhand").equals(area)) {
            return I18n.format("gui.path.action_editor.option.capture_slot_area.offhand");
        }
        return I18n.format("gui.path.action_editor.option.capture_slot_area.main");
    }

    public static String displayToCaptureSlotArea(String display) {
        if ("HOTBAR".equalsIgnoreCase(display)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.hotbar").equals(display)) {
            return "HOTBAR";
        }
        if ("ARMOR".equalsIgnoreCase(display)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.armor").equals(display)) {
            return "ARMOR";
        }
        if ("OFFHAND".equalsIgnoreCase(display)
                || I18n.format("gui.path.action_editor.option.capture_slot_area.offhand").equals(display)) {
            return "OFFHAND";
        }
        return "MAIN";
    }

    public static String packetFieldLookupModeToDisplay(String mode) {
        if ("VARIABLE".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.capture_packet_field_lookup_mode.variable");
        }
        return I18n.format("gui.path.action_editor.option.capture_packet_field_lookup_mode.latest");
    }

    public static String displayToPacketFieldLookupMode(String display) {
        return I18n.format("gui.path.action_editor.option.capture_packet_field_lookup_mode.variable").equals(display)
                ? "VARIABLE"
                : "LATEST_CAPTURE";
    }

    public static String visionCompareModeToDisplay(String mode) {
        if ("TEMPLATE".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.vision_compare_mode.template");
        }
        if ("EDGE_DENSITY".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.vision_compare_mode.edge");
        }
        return I18n.format("gui.path.action_editor.option.vision_compare_mode.color");
    }

    public static String displayToVisionCompareMode(String display) {
        if (I18n.format("gui.path.action_editor.option.vision_compare_mode.template").equals(display)) {
            return "TEMPLATE";
        }
        if (I18n.format("gui.path.action_editor.option.vision_compare_mode.edge").equals(display)) {
            return "EDGE_DENSITY";
        }
        return "AVERAGE_COLOR";
    }

    public static String clickLocatorModeToDisplay(String mode) {
        if (ActionTargetLocator.CLICK_MODE_BUTTON_TEXT.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.click.button_text");
        }
        if (ActionTargetLocator.CLICK_MODE_SLOT_TEXT.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.click.slot_text");
        }
        if (ActionTargetLocator.CLICK_MODE_ELEMENT_PATH.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.click.element_path");
        }
        return I18n.format("gui.path.action_editor.option.locator.click.coordinate");
    }

    public static String slotLocatorModeToDisplay(String mode) {
        if (ActionTargetLocator.SLOT_MODE_ITEM_TEXT.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.slot.item_text");
        }
        if (ActionTargetLocator.SLOT_MODE_EMPTY.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.slot.empty");
        }
        if (ActionTargetLocator.SLOT_MODE_PATH.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.slot.path");
        }
        return I18n.format("gui.path.action_editor.option.locator.slot.direct");
    }

    public static String worldLocatorModeToDisplay(String mode) {
        if (ActionTargetLocator.TARGET_MODE_NAME.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.locator.world.name");
        }
        return I18n.format("gui.path.action_editor.option.locator.world.position");
    }

    public static String guiElementTypeToDisplay(String type) {
        if ("TITLE".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.gui_element_type.title");
        }
        if ("BUTTON".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.gui_element_type.button");
        }
        if ("SLOT".equalsIgnoreCase(type)) {
            return I18n.format("gui.path.action_editor.option.gui_element_type.slot");
        }
        return I18n.format("gui.path.action_editor.option.gui_element_type.any");
    }

    public static String displayToGuiElementType(String display) {
        if (I18n.format("gui.path.action_editor.option.gui_element_type.title").equals(display)) {
            return "TITLE";
        }
        if (I18n.format("gui.path.action_editor.option.gui_element_type.button").equals(display)) {
            return "BUTTON";
        }
        if (I18n.format("gui.path.action_editor.option.gui_element_type.slot").equals(display)) {
            return "SLOT";
        }
        return "ANY";
    }

    public static String guiElementLocatorModeToDisplay(String mode) {
        if ("PATH".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.gui_element_locator_mode.path");
        }
        return I18n.format("gui.path.action_editor.option.gui_element_locator_mode.text");
    }

    public static String displayToGuiElementLocatorMode(String display) {
        return I18n.format("gui.path.action_editor.option.gui_element_locator_mode.path").equals(display)
                ? "PATH"
                : "TEXT";
    }

    public static String entityTypeToDisplay(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "沿用杀戮光环";
        }
        if ("hostile".equalsIgnoreCase(type) || "monster".equalsIgnoreCase(type)) {
            return "敌对生物";
        }
        if ("passive".equalsIgnoreCase(type) || "animal".equalsIgnoreCase(type)) {
            return "被动生物";
        }
        if ("all".equalsIgnoreCase(type) || "entity".equalsIgnoreCase(type)) {
            return "所有实体";
        }
        return "玩家";
    }

    public static String displayToEntityType(String display) {
        if ("沿用杀戮光环".equals(display)) {
            return "";
        }
        if ("敌对生物".equals(display)) {
            return "hostile";
        }
        if ("被动生物".equals(display)) {
            return "passive";
        }
        if ("所有实体".equals(display)) {
            return "all";
        }
        return "player";
    }

    public static String huntModeToDisplay(String mode) {
        if (KillAuraHandler.HUNT_MODE_APPROACH.equalsIgnoreCase(mode)) {
            return "靠近目标";
        }
        return "固定距离";
    }

    public static String displayToHuntMode(String display) {
        if ("靠近目标".equals(display)) {
            return KillAuraHandler.HUNT_MODE_APPROACH;
        }
        return KillAuraHandler.HUNT_MODE_FIXED_DISTANCE;
    }

    public static String huntAttackModeToDisplay(String mode) {
        if (KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(mode)) {
            return "执行序列攻击";
        }
        return "杀戮当前配置";
    }

    public static String displayToHuntAttackMode(String display) {
        if ("执行序列攻击".equals(display)) {
            return KillAuraHandler.ATTACK_MODE_SEQUENCE;
        }
        return KillAuraHandler.ATTACK_MODE_NORMAL;
    }

    public static String displayToLocatorMode(String display) {
        if (I18n.format("gui.path.action_editor.option.locator.click.button_text").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_BUTTON_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.click.slot_text").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_SLOT_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.click.element_path").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_ELEMENT_PATH;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.item_text").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_ITEM_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.empty").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_EMPTY;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.path").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_PATH;
        }
        if (I18n.format("gui.path.action_editor.option.locator.world.name").equals(display)) {
            return ActionTargetLocator.TARGET_MODE_NAME;
        }
        if (I18n.format("gui.path.action_editor.option.locator.world.position").equals(display)) {
            return ActionTargetLocator.TARGET_MODE_POSITION;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.direct").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_DIRECT;
        }
        return ActionTargetLocator.CLICK_MODE_COORDINATE;
    }

    public static String displayToClickLocatorMode(String display) {
        if (I18n.format("gui.path.action_editor.option.locator.click.button_text").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_BUTTON_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.click.slot_text").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_SLOT_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.click.element_path").equals(display)) {
            return ActionTargetLocator.CLICK_MODE_ELEMENT_PATH;
        }
        return ActionTargetLocator.CLICK_MODE_COORDINATE;
    }

    public static String displayToSlotLocatorMode(String display) {
        if (I18n.format("gui.path.action_editor.option.locator.slot.item_text").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_ITEM_TEXT;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.empty").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_EMPTY;
        }
        if (I18n.format("gui.path.action_editor.option.locator.slot.path").equals(display)) {
            return ActionTargetLocator.SLOT_MODE_PATH;
        }
        return ActionTargetLocator.SLOT_MODE_DIRECT;
    }

    public static String displayToWorldLocatorMode(String display) {
        return I18n.format("gui.path.action_editor.option.locator.world.name").equals(display)
                ? ActionTargetLocator.TARGET_MODE_NAME
                : ActionTargetLocator.TARGET_MODE_POSITION;
    }

    public static String displayToLocatorMode(String display, String actionType) {
        String normalizedActionType = actionType == null
                ? ""
                : actionType.trim().toLowerCase(Locale.ROOT);
        if ("click".equals(normalizedActionType)) {
            return displayToClickLocatorMode(display);
        }
        if ("window_click".equals(normalizedActionType)
                || "conditional_window_click".equals(normalizedActionType)
                || "autochestclick".equals(normalizedActionType)) {
            return displayToSlotLocatorMode(display);
        }
        if ("rightclickblock".equals(normalizedActionType)
                || "rightclickentity".equals(normalizedActionType)) {
            return displayToWorldLocatorMode(display);
        }
        return displayToLocatorMode(display);
    }

    public static String boolToDisplayYesNo(boolean value) {
        return value ? "是" : "否";
    }

    public static boolean displayYesNoToBool(String display) {
        return "是".equals(display);
    }

    public static String boolToDisplayOnOff(boolean value) {
        return value ? I18n.format("path.common.on") : I18n.format("path.common.off");
    }

    public static boolean displayOnOffToBool(String display) {
        return I18n.format("path.common.on").equals(display)
                || "是".equals(display)
                || "true".equalsIgnoreCase(display);
    }
}
