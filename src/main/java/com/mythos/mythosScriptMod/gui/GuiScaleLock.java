package com.mythos.mythosScriptMod.gui;

import com.mythos.mythosScriptMod.gui.components.ThemedGuiScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Locks GUI scale to 2 ("中") while a MythosScript screen is open, independent
 * of the game's own GUI-scale option. Done by swapping gameSettings.guiScale
 * because every ScaledResolution in the screen code (layout, scissor, mouse,
 * tooltips) then agrees without per-call changes. GuiOpenEvent fires before
 * displayGuiScreen builds the ScaledResolution for the new screen.
 */
public final class GuiScaleLock {

    public static final int LOCKED_SCALE = 2;
    public static final GuiScaleLock INSTANCE = new GuiScaleLock();

    private static boolean locked;
    private static int userScale;

    private GuiScaleLock() {}

    /** HUD-position editors are excluded: they must match the real HUD scale. */
    static boolean isLockedScreen(GuiScreen screen) {
        return (screen instanceof GuiModernMainScreen || screen instanceof ThemedGuiScreen)
                && !(screen instanceof GuiOtherFeaturesHudPosition);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.isCanceled()) {
            return;
        }
        GameSettings gs = Minecraft.getMinecraft().gameSettings;
        if (gs == null) {
            return;
        }
        if (isLockedScreen(event.getGui())) {
            if (!locked) {
                userScale = gs.guiScale;
                locked = true;
            }
            gs.guiScale = LOCKED_SCALE;
        } else if (locked) {
            gs.guiScale = userScale;
            locked = false;
        }
    }


    /** ScaledResolution as the in-game HUD sees it (the player's own scale). */
    public static ScaledResolution userScaledResolution(Minecraft mc) {
        if (!locked || mc.gameSettings == null) {
            return new ScaledResolution(mc);
        }
        int saved = mc.gameSettings.guiScale;
        mc.gameSettings.guiScale = userScale;
        try {
            return new ScaledResolution(mc);
        } finally {
            mc.gameSettings.guiScale = saved;
        }
    }

    /** gameSettings.saveOptions() that never persists the locked scale. */
    public static void saveOptions(GameSettings gs) {
        if (!locked) {
            gs.saveOptions();
            return;
        }
        int saved = gs.guiScale;
        gs.guiScale = userScale;
        try {
            gs.saveOptions();
        } finally {
            gs.guiScale = saved;
        }
    }
}
