package com.mythos.mythosScriptMod.gui.path.GuiActionEditor.util;

import com.mythos.mythosScriptMod.handlers.ItemFilterHandler;
import com.mythos.mythosScriptMod.handlers.ItemSpreadHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.utils.locator.ActionTargetLocator;

import net.minecraft.client.resources.I18n;

import java.util.Locale;

public final class ActionEditorDisplayConverters {
    private ActionEditorDisplayConverters() {
    }









    public static String directionToDisplay(String direction) {
        return "S2C".equalsIgnoreCase(direction)
                ? I18n.format("path.action.desc.send_packet.direction.s2c")
                : I18n.format("path.action.desc.send_packet.direction.c2s");
    }


    public static String matchModeToDisplay(String mode) {
        return "EXACT".equalsIgnoreCase(mode)
                ? I18n.format("gui.autouseitem.match.exact")
                : I18n.format("gui.autouseitem.match.contains");
    }


    public static String useModeToDisplay(String mode) {
        return "LEFT_CLICK".equalsIgnoreCase(mode)
                ? I18n.format("gui.autouseitem.mode.left")
                : I18n.format("gui.autouseitem.mode.right");
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




    public static String waitCombinedModeToDisplay(String mode) {
        return "ALL".equalsIgnoreCase(mode)
                ? I18n.format("gui.path.action_editor.option.wait_combined_mode.all")
                : I18n.format("gui.path.action_editor.option.wait_combined_mode.any");
    }


    public static String runSequenceExecuteModeToDisplay(String mode) {
        return "interval".equalsIgnoreCase(mode)
                ? I18n.format("gui.path.action_editor.option.run_sequence_execute_interval")
                : I18n.format("gui.path.action_editor.option.run_sequence_execute_always");
    }




    public static String sequenceControlOperationToDisplay(String operation) {
        return "resume".equalsIgnoreCase(operation)
                ? I18n.format("gui.path.action_editor.option.sequence_control_operation.resume")
                : I18n.format("gui.path.action_editor.option.sequence_control_operation.pause");
    }


    public static String moveChestDirectionToDisplay(String direction) {
        return ItemFilterHandler.MOVE_DIRECTION_CHEST_TO_INVENTORY.equalsIgnoreCase(direction)
                ? I18n.format("gui.path.action_editor.option.move_chest_direction.chest_to_inventory")
                : I18n.format("gui.path.action_editor.option.move_chest_direction.inventory_to_chest");
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


    public static String spreadRemainderModeToDisplay(String mode) {
        if (ItemSpreadHandler.REMAINDER_FIRST_EMPTY.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_remainder.first_empty");
        }
        if (ItemSpreadHandler.REMAINDER_KEEP_CURSOR.equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.spread_remainder.keep_cursor");
        }
        return I18n.format("gui.path.action_editor.option.spread_remainder.source");
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


    public static String packetFieldLookupModeToDisplay(String mode) {
        if ("VARIABLE".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.capture_packet_field_lookup_mode.variable");
        }
        return I18n.format("gui.path.action_editor.option.capture_packet_field_lookup_mode.latest");
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


    public static String guiElementLocatorModeToDisplay(String mode) {
        if ("PATH".equalsIgnoreCase(mode)) {
            return I18n.format("gui.path.action_editor.option.gui_element_locator_mode.path");
        }
        return I18n.format("gui.path.action_editor.option.gui_element_locator_mode.text");
    }
















}
