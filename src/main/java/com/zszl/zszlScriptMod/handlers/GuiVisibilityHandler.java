package com.zszl.zszlScriptMod.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Best-effort cache for temporarily hiding and later restoring the current GUI.
 * This preserves the current GuiScreen instance only on the client side.
 */
public final class GuiVisibilityHandler {

    private static GuiScreen hiddenGui;

    private GuiVisibilityHandler() {
    }

    public static boolean hideCurrentGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null) {
            return false;
        }
        hiddenGui = mc.currentScreen;
        mc.displayGuiScreen(null);
        return true;
    }

    public static boolean showHiddenGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || hiddenGui == null) {
            return false;
        }
        GuiScreen guiToRestore = hiddenGui;
        hiddenGui = null;
        mc.displayGuiScreen(guiToRestore);
        return true;
    }

    public static boolean hasHiddenGui() {
        return hiddenGui != null;
    }

    public static void reset() {
        hiddenGui = null;
    }
}
