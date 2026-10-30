package com.mythos.mythosScriptMod.gui.modern.packet;

import com.mythos.mythosScriptMod.gui.GuiModernMainScreen;
import com.mythos.mythosScriptMod.gui.modern.path.ModernPathWorkbenchTab;
import com.mythos.mythosScriptMod.path.RecordingPacketSupport;
import com.mythos.mythosScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.Minecraft;

/**
 * Bridge from the packet browser to the path action editor. Holds the packet the
 * user chose to send and performs the append into the currently-selected step.
 */
public final class PacketToEditorBridge {

    private PacketToEditorBridge() {
    }

    /** Result of trying to push a packet into the path action editor. */
    public enum SendResult {
        /** No editable step is currently selected in the path editor. */
        NO_STEP,
        /** The action was appended to the selected step. */
        APPENDED
    }

    /**
     * Builds a send_packet action from {@code packet} and appends it to the step
     * currently selected in the open path workbench.
     */
    public static SendResult sendToActionEditor(PacketCaptureHandler.CapturedPacketData packet, String direction) {
        if (packet == null) {
            return SendResult.NO_STEP;
        }
        ModernPathWorkbenchTab workbench = openPathWorkbench();
        if (workbench == null || !workbench.hasEditableStepSelection()) {
            return SendResult.NO_STEP;
        }
        return workbench.appendSendPacketAction(RecordingPacketSupport.sendPacketParams(packet, direction))
                ? SendResult.APPENDED
                : SendResult.NO_STEP;
    }

    /** Opens the packet browser from a send_packet action's picker button. */
    public static void openPacketPicker(Minecraft minecraft) {
        GuiModernMainScreen screen = activeScreen(minecraft);
        if (screen == null) {
            return;
        }
        screen.showPacketViewer();
    }

    private static ModernPathWorkbenchTab openPathWorkbench() {
        GuiModernMainScreen screen = activeScreen(Minecraft.getMinecraft());
        return screen == null ? null : screen.pathWorkbenchTab();
    }

    private static GuiModernMainScreen activeScreen(Minecraft minecraft) {
        if (minecraft != null && minecraft.currentScreen instanceof GuiModernMainScreen) {
            return (GuiModernMainScreen) minecraft.currentScreen;
        }
        return GuiModernMainScreen.getPersistentScreen();
    }
}
