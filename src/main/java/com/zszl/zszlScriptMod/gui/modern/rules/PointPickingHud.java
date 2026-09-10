package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

/**
 * Persistent in-world guidance panel used while a point picker owns the mouse.
 * Messages are kept as a short log so each new instruction can scroll the old
 * instruction upward instead of opening a chat or modal window.
 */
final class PointPickingHud {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 112;
    private static final int SCREEN_MARGIN = 12;
    private static final int HOTBAR_GAP = 42;
    private static final int PADDING = 8;
    private static final int TITLE_HEIGHT = 25;
    private static final int LINE_GAP = 2;
    private static final int MAX_MESSAGES = 64;
    private static final float SCROLL_PIXELS_PER_MILLISECOND = 0.055F;

    private final Minecraft mc;
    private final String title;
    private final List<String> messages = new ArrayList<>();
    private float scrollOffset;
    private long lastRenderAt;

    PointPickingHud(Minecraft mc, String title) {
        this.mc = mc;
        this.title = title == null ? "" : title;
    }

    void addMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        messages.add(message);
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    void render(RenderGameOverlayEvent.Post event) {
        if (event == null || mc == null || mc.fontRenderer == null || mc.world == null || mc.player == null
                || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        if (screenWidth < 80 || screenHeight < 80) {
            return;
        }

        int panelWidth = Math.min(PANEL_WIDTH, Math.max(160, screenWidth - SCREEN_MARGIN * 2));
        int panelHeight = Math.min(PANEL_HEIGHT, Math.max(72, screenHeight - HOTBAR_GAP - 8));
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = Math.max(4, screenHeight - panelHeight - HOTBAR_GAP);
        int contentX = panelX + PADDING;
        int contentTop = panelY + TITLE_HEIGHT;
        int contentBottom = panelY + panelHeight - PADDING;
        int contentWidth = Math.max(1, panelWidth - PADDING * 2);
        int lineHeight = Math.max(1, mc.fontRenderer.FONT_HEIGHT + LINE_GAP);
        int visibleHeight = Math.max(lineHeight, contentBottom - contentTop);

        List<String> lines = flattenMessages(mc.fontRenderer, contentWidth);
        float targetOffset = Math.max(0.0F, lines.size() * lineHeight - visibleHeight);
        advanceScroll(targetOffset);

        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean lighting = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LIGHTING);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean texture = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GuiTheme.drawPanel(panelX, panelY, panelWidth, panelHeight);
            GuiTheme.drawTitleBar(panelX + 1, panelY + 1, Math.max(1, panelWidth - 2), title, mc.fontRenderer);

            ModernUiRenderer.beginClip(new ModernMainLayout.Rect(contentX, contentTop, contentWidth, visibleHeight));
            try {
                int lineY = contentTop - Math.round(scrollOffset);
                for (Iterator<String> it = lines.iterator(); it.hasNext();) {
                    String line = it.next();
                    if (lineY + mc.fontRenderer.FONT_HEIGHT >= contentTop
                            && lineY <= contentBottom) {
                        int color = it.hasNext() ? GuiTheme.LABEL_TEXT : GuiTheme.TITLE_TEXT;
                        mc.fontRenderer.drawStringWithShadow(line, contentX, lineY,
                                GuiTheme.resolveTextColor("", color));
                    }
                    lineY += lineHeight;
                }
            } finally {
                ModernUiRenderer.endClip();
            }
        } finally {
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private List<String> flattenMessages(FontRenderer fontRenderer, int width) {
        List<String> lines = new ArrayList<>();
        for (String message : messages) {
            String[] paragraphs = message.split("\\n", -1);
            for (String paragraph : paragraphs) {
                List<String> wrapped = fontRenderer.listFormattedStringToWidth(paragraph, width);
                if (wrapped == null || wrapped.isEmpty()) {
                    lines.add("");
                } else {
                    lines.addAll(wrapped);
                }
            }
        }
        return lines;
    }

    private void advanceScroll(float targetOffset) {
        long now = System.currentTimeMillis();
        if (lastRenderAt == 0L) {
            lastRenderAt = now;
        }
        long elapsed = Math.max(0L, Math.min(100L, now - lastRenderAt));
        lastRenderAt = now;
        float distance = elapsed * SCROLL_PIXELS_PER_MILLISECOND;
        if (scrollOffset < targetOffset) {
            scrollOffset = Math.min(targetOffset, scrollOffset + distance);
        } else if (scrollOffset > targetOffset) {
            scrollOffset = Math.max(targetOffset, scrollOffset - distance);
        }
    }
}
