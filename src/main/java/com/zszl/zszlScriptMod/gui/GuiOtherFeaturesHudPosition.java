package com.zszl.zszlScriptMod.gui;

import java.awt.Rectangle;
import java.io.IOException;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Transparent screen that releases the cursor while positioning the other-features HUD. */
public final class GuiOtherFeaturesHudPosition extends GuiScreen {
    private boolean dragging;
    private int offsetX;
    private int offsetY;

    /** Open the HUD positioning interaction without exposing its construction to the main shell. */
    public static void open(Minecraft minecraft) {
        if (minecraft != null) {
            minecraft.displayGuiScreen(new GuiOtherFeaturesHudPosition());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (dragging) {
            movePanel(mouseX, mouseY);
        }
        OverlayGuiHandler.renderMasterStatusHudPreview();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            return;
        }
        Rectangle exit = GuiInventory.masterStatusHudExitButtonBounds;
        if (exit != null && exit.contains(mouseX, mouseY)) {
            mc.displayGuiScreen(null);
            return;
        }
        Rectangle bounds = GuiInventory.masterStatusHudEditorBounds;
        if (bounds != null && bounds.contains(mouseX, mouseY)) {
            dragging = true;
            offsetX = mouseX - MovementFeatureManager.getMasterStatusHudX();
            offsetY = mouseY - MovementFeatureManager.getMasterStatusHudY();
        }
    }

    private void movePanel(int mouseX, int mouseY) {
        Rectangle bounds = GuiInventory.masterStatusHudEditorBounds;
        if (bounds == null) {
            return;
        }
        MovementFeatureManager.setMasterStatusHudPositionTransient(
                Math.max(4, Math.min(mouseX - offsetX, width - bounds.width + 4)),
                Math.max(4, Math.min(mouseY - offsetY, height - bounds.height + 4)));
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && dragging) {
            movePanel(mouseX, mouseY);
            dragging = false;
            MovementFeatureManager.persistMasterStatusHudPosition();
        }
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
        GuiInventory.updateMasterStatusHudEditorBounds(null, null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
