package com.zszl.zszlScriptMod.gui.components;

import java.util.Arrays;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernTooltipSupport;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public class ThemedGuiScreen extends GuiScreen {
    protected float readableUiScale = 1.0F;
    protected int rawScreenWidth = 0;
    protected int rawScreenHeight = 0;

    protected void applyReadableUiScaleForLargeScreen(int targetWidth, int targetHeight) {
        this.rawScreenWidth = this.width;
        this.rawScreenHeight = this.height;
        int safeTargetWidth = Math.max(640, targetWidth);
        int safeTargetHeight = Math.max(360, targetHeight);
        float widthScale = this.width / (float) safeTargetWidth;
        float heightScale = this.height / (float) safeTargetHeight;
        this.readableUiScale = Math.max(1.0F, Math.min(1.8F, Math.min(widthScale, heightScale)));
        if (this.readableUiScale <= 1.02F) {
            this.readableUiScale = 1.0F;
            return;
        }
        this.width = Math.max(1, Math.round(this.rawScreenWidth / this.readableUiScale));
        this.height = Math.max(1, Math.round(this.rawScreenHeight / this.readableUiScale));
    }

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

    protected void drawThemedTextField(GuiTextField field) {
        if (field == null) {
            return;
        }
        ModernTooltipSupport.registerTextField(this, field);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        field.setEnableBackgroundDrawing(false);
        GuiTheme.drawInputFrame(field.x - 1, field.y - 1, field.width + 2, field.height + 2, field.isFocused(), true);
        ModernUiRenderer.reflowTextField(field);
        field.drawTextBox();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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

    protected boolean isMouseOverButton(int mouseX, int mouseY, GuiButton button) {
        return button != null && button.visible
                && mouseX >= button.x && mouseX <= button.x + button.width
                && mouseY >= button.y && mouseY <= button.y + button.height;
    }

    protected boolean isMouseOverField(int mouseX, int mouseY, GuiTextField field) {
        return field != null && mouseX >= field.x && mouseX <= field.x + field.width
                && mouseY >= field.y && mouseY <= field.y + field.height;
    }

    protected boolean isHoverRegion(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    protected void drawSimpleTooltip(String text, int mouseX, int mouseY) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        ModernTooltipSupport.capture(this, Arrays.asList(text.split("\n")), mouseX, mouseY);
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
