package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;

public class GuiBlockerHandler {
    private static int remainingBlockCount = 0;

    public static void blockNextGui(int count) {
        blockGui(count, false);
    }

    public static void blockGui(int count, boolean blockCurrentGui) {
        int effectiveCount = Math.max(1, count);
        remainingBlockCount += effectiveCount;

        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("msg.gui_blocker.enabled", remainingBlockCount)));
        }
        zszlScriptMod.LOGGER.info(I18n.format("log.gui_blocker.enabled"), effectiveCount, remainingBlockCount);

        if (blockCurrentGui) {
            blockCurrentGuiNow();
        }
    }

    public static boolean shouldBlockAndConsume(GuiScreen gui) {
        if (gui == null || remainingBlockCount <= 0) {
            return false;
        }

        remainingBlockCount--;
        zszlScriptMod.LOGGER.info(I18n.format("log.gui_blocker.blocked"), gui.getClass().getName(),
                remainingBlockCount);

        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(
                    new TextComponentString(I18n.format("msg.gui_blocker.blocked", remainingBlockCount)));
        }

        return true;
    }

    public static int getRemainingBlockCount() {
        return remainingBlockCount;
    }

    private static void blockCurrentGuiNow() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentGui = mc.currentScreen;
        if (currentGui != null && shouldBlockAndConsume(currentGui)) {
            mc.displayGuiScreen(null);
        }
    }

    public static void reset() {
        remainingBlockCount = 0;
    }
}
