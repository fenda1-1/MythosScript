package com.zszl.zszlScriptMod.gui;

import java.awt.Rectangle;
import java.io.IOException;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Transparent screen for positioning the persistent other-features status HUD.
 */
public final class GuiOtherFeaturesHudPosition extends GuiScreen {
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private Rectangle hudBounds;

    /** Opens the HUD positioning interaction and releases the control center. */
    public static void open(Minecraft minecraft) {
        if (minecraft != null) {
            minecraft.displayGuiScreen(new GuiOtherFeaturesHudPosition());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw exactly the same HUD that is shown during normal gameplay.
        // There is deliberately no editor canvas, backdrop, hint, or button.
        hudBounds = OverlayGuiHandler.renderMasterStatusHudForEditor();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 || hudBounds == null || !hudBounds.contains(mouseX, mouseY)) {
            return;
        }
        dragging = true;
        dragOffsetX = mouseX - MovementFeatureManager.getMasterStatusHudX();
        dragOffsetY = mouseY - MovementFeatureManager.getMasterStatusHudY();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && dragging) {
            movePanel(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && dragging) {
            movePanel(mouseX, mouseY);
            dragging = false;
            MovementFeatureManager.persistMasterStatusHudPosition();
        }
    }

    private void movePanel(int mouseX, int mouseY) {
        if (hudBounds == null) {
            return;
        }
        MovementFeatureManager.setMasterStatusHudPositionTransient(
                Math.max(4, Math.min(mouseX - dragOffsetX, width - hudBounds.width + 4)),
                Math.max(4, Math.min(mouseY - dragOffsetY, height - hudBounds.height + 4)));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        dragging = false;
        MovementFeatureManager.persistMasterStatusHudPosition();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
