package com.zszl.zszlScriptMod.gui;

import java.io.IOException;

import com.zszl.zszlScriptMod.gui.modern.ModernOtherFeatureSettingsTab;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.inventory.InventoryViewerManager;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.utils.UpdateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

final class GuiModernMainActions {

    private GuiModernMainActions() {
    }

    static boolean handleCommand(String command, int mouseButton, Minecraft mc) throws IOException {
        if (command == null || command.trim().isEmpty() || mc == null) {
            return false;
        }

        if ("stop".equals(command) || "stop_foreground".equals(command)) {
            EmbeddedNavigationHandler.INSTANCE.stop();
            PathSequenceEventListener.instance.stopTracking();
            GuiInventory.isLooping = false;
            return true;
        }
        if ("stop_background".equals(command)) {
            PathSequenceEventListener.stopAllBackgroundRunners();
            return true;
        }
        if (ModernOtherFeatureSettingsTab.isCommand(command)) {
            if (mouseButton == 0) {
                boolean handled = GuiInventory.toggleOtherFeature(
                        ModernOtherFeatureSettingsTab.featureIdFromCommand(command));
                if (handled) {
                    GuiInventory.refreshGuiLists();
                }
                return handled;
            }
            if (mouseButton == 1) {
                GuiModernMainScreen.openSettingsTab(mc, command);
                return true;
            }
            return false;
        }
        if ("chat_optimization".equals(command) || "block_replacement_config".equals(command)) {
            GuiModernMainScreen.openSettingsTab(mc, command);
            return true;
        }
        if (GuiInventory.handleCommonCommandClick(command, mouseButton, mc)) {
            GuiInventory.refreshGuiLists();
            return true;
        }

        if ("debug_settings".equals(command)) {
            if (mouseButton == 0 || mouseButton == 1) {
                GuiModernMainScreen.openSettingsTab(mc, "debug_settings");
            }
        } else if ("memory_manager".equals(command) || "terrain_scanner".equals(command)) {
            if (!(mc.currentScreen instanceof GuiModernMainScreen)) {
                GuiInventory.closeOverlay();
            }
            GuiModernMainScreen.openSettingsTab(mc, command);
        } else if ("player_equipment_viewer".equals(command)) {
            if (mouseButton == 0) {
                InventoryViewerManager.copyInventoryFromTarget();
            }
            GuiModernMainScreen.openSettingsTab(mc, command);
        } else if ("packet_handler".equals(command)) {
            GuiModernMainScreen.openSettingsTab(mc, command);
            return true;
        } else if ("gui_inspector_manager".equals(command)) {
            GuiModernMainScreen.openSettingsTab(mc, command);
        } else if ("performance_monitor".equals(command)) {
            GuiModernMainScreen.openSettingsTab(mc, command);
        } else if ("current_resolution_info".equals(command)) {
            GuiModernMainScreen.openSettingsTab(mc, "current_resolution_info");
        } else if ("reload_paths".equals(command)) {
            PathSequenceManager.initializePathSequences();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + I18n.format("msg.inventory.paths_reloaded")));
            }
        } else if (command.startsWith("path:") || command.startsWith("custom_path:")) {
            String sequenceName = command.substring(command.indexOf(':') + 1);
            PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
            if (sequence == null) {
                return false;
            }
            if (PathSequenceEventListener.isSequenceRunningInForeground(sequenceName)
                    || PathSequenceEventListener.isSequenceRunningInBackground(sequenceName)) {
                return true;
            }
            if (sequence.isCustom()) {
                MainUiLayoutManager.recordSequenceOpened(sequenceName);
            }
            if (sequence.shouldCloseGuiAfterStart()) {
                GuiInventory.closeOverlay();
            }
            PathSequenceManager.runPathSequence(sequenceName);
        } else {
            return false;
        }

        GuiInventory.refreshGuiLists();
        return true;
    }

    static boolean handleUtilityAction(String action, Minecraft mc) {
        if (action == null || mc == null) {
            return false;
        }
        if ("theme".equals(action)) {
            GuiModernMainScreen.openSettingsTab(mc, action);
        } else if ("update".equals(action)) {
            UpdateManager.fetchUpdateLinkAndOpen();
        } else if ("donate".equals(action) || "changelog".equals(action) || "mcp_settings".equals(action) || "notes".equals(action)) {
            GuiModernMainScreen.openSettingsTab(mc, action);
        } else {
            return false;
        }
        return true;
    }
}
