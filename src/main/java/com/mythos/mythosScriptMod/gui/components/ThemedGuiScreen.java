package com.mythos.mythosScriptMod.gui.components;

import java.util.Arrays;
import java.util.List;

import com.mythos.mythosScriptMod.gui.modern.ModernTooltipSupport;
import com.mythos.mythosScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class ThemedGuiScreen extends GuiScreen {
    protected float readableUiScale = 1.0F;
    protected int rawScreenWidth = 0;
    protected int rawScreenHeight = 0;


    protected int toReadableMouseX(int mouseX) {
        // Minecraft calculates callbacks from this.width/height. Once the
        // readable layout has reduced those values, callback coordinates are
        // already in logical space and must not be scaled a second time.
        return mouseX;
    }

    protected int toReadableMouseY(int mouseY) {
        return mouseY;
    }

    protected void pushReadableUiScale() {
        GlStateManager.pushMatrix();
        if (this.readableUiScale > 1.0F) {
            GlStateManager.scale(this.readableUiScale, this.readableUiScale, 1.0F);
        }
    }

    protected void popReadableUiScale() {
        GlStateManager.popMatrix();
    }


    @Override
    public void drawString(FontRenderer fontRendererIn, String text, int x, int y, int color) {
        FontRenderer renderer = fontRendererIn != null ? fontRendererIn : this.fontRenderer;
        if (renderer == null) {
            return;
        }
        String safeText = text == null ? "" : text;
        super.drawString(renderer, safeText, x, y, GuiTheme.resolveTextColor(safeText, color));
    }

    @Override
    public void drawCenteredString(FontRenderer fontRendererIn, String text, int x, int y, int color) {
        FontRenderer renderer = fontRendererIn != null ? fontRendererIn : this.fontRenderer;
        if (renderer == null) {
            return;
        }
        String safeText = text == null ? "" : text;
        super.drawCenteredString(renderer, safeText, x, y, GuiTheme.resolveTextColor(safeText, color));
    }



    protected boolean isHoverRegion(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }


    @Override
    public void drawDefaultBackground() {
        ModernUiRenderer.drawBackdrop(this.width, this.height);
    }

    @Override
    protected void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        ModernTooltipSupport.capture(this, textLines, x, y);
    }
}
