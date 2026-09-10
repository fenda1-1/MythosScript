package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.zszl.zszlScriptMod.gui.donate.DonationContactCard;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/** Native modern support page. It intentionally has no remote donation data. */
final class ModernDonationSupportTab implements ModernSettingsTab {

    private static final String PAYMENT_QR_RESOURCE = "img/Sponsored.jpg";
    private static final int COMPACT_QR_MIN_HEIGHT = 190;
    private static final int COMPACT_QR_MAX_HEIGHT = 260;
    private static final int COMPACT_DETAILS_MIN_HEIGHT = 150;
    private static final int COMPACT_SCROLL_STEP = 36;

    private final DonationContactCard contactCard = new DonationContactCard();
    private final Minecraft minecraft;
    private ResourceLocation qrTexture;
    private int qrTextureWidth;
    private int qrTextureHeight;
    private boolean initialized;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect compactViewportBounds;
    private boolean compactLayout;
    private int compactScroll;
    private int compactMaxScroll;

    ModernDonationSupportTab(Minecraft minecraft) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        initialized = true;
        loadQrTexture();
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = bounds;
        panelBounds = safePanel(bounds);

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer);

        boolean compact = panelBounds.width < 560;
        compactLayout = compact;
        if (!compact) {
            compactViewportBounds = null;
            compactScroll = 0;
            compactMaxScroll = 0;
        }
        int bodyX = panelBounds.x + 12;
        int bodyY = panelBounds.y + 58;
        int bodyWidth = Math.max(1, panelBounds.width - 24);
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 12);
        if (compact) {
            drawCompact(fontRenderer, bodyX, bodyY, bodyWidth, bodyHeight, mouseX, mouseY);
        } else {
            drawWide(fontRenderer, bodyX, bodyY, bodyWidth, bodyHeight, mouseX, mouseY);
        }
    }

    private void drawHeader(FontRenderer fontRenderer) {
        int x = panelBounds.x + 16;
        ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.donate.title"), x, panelBounds.y + 12,
                ModernUiRenderer.TEXT, Math.max(40, panelBounds.width - 32));
        ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.donate.subtitle"), x, panelBounds.y + 28,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(40, panelBounds.width - 32));
        ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + 49, Math.max(1, panelBounds.width - 24),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawWide(FontRenderer fontRenderer, int x, int y, int width, int height, int mouseX, int mouseY) {
        int gap = 10;
        int imageWidth = Math.max(190, Math.min(280, (width - gap) / 2));
        int detailsWidth = Math.max(1, width - imageWidth - gap);
        ModernMainLayout.Rect imagePanel = new ModernMainLayout.Rect(x, y, imageWidth, height);
        ModernMainLayout.Rect detailsPanel = new ModernMainLayout.Rect(imagePanel.right() + gap, y, detailsWidth, height);
        drawQrPanel(fontRenderer, imagePanel);
        drawDetailsPanel(fontRenderer, detailsPanel, mouseX, mouseY);
    }

    private void drawCompact(FontRenderer fontRenderer, int x, int y, int width, int height, int mouseX, int mouseY) {
        compactViewportBounds = new ModernMainLayout.Rect(x, y, width, height);
        int contentWidth = Math.max(1, width - 8);
        int qrHeight = Math.max(COMPACT_QR_MIN_HEIGHT,
                Math.min(COMPACT_QR_MAX_HEIGHT, Math.max(1, contentWidth - 16)));
        int detailsHeight = Math.max(COMPACT_DETAILS_MIN_HEIGHT, Math.min(height, height * 2 / 3));
        int contentHeight = qrHeight + 8 + detailsHeight;
        compactMaxScroll = Math.max(0, contentHeight - height);
        compactScroll = Math.max(0, Math.min(compactScroll, compactMaxScroll));

        int qrY = y - compactScroll;
        int detailsY = qrY + qrHeight + 8;
        ModernUiRenderer.beginClip(compactViewportBounds);
        try {
            // Keep the donation entry point first on narrow screens. Its fixed
            // height prevents the QR code from collapsing when the viewport is short.
            drawQrPanel(fontRenderer, new ModernMainLayout.Rect(x, qrY, contentWidth, qrHeight));
            drawDetailsPanel(fontRenderer, new ModernMainLayout.Rect(x, detailsY, contentWidth, detailsHeight),
                    mouseX, mouseY);
        } finally {
            ModernUiRenderer.endClip();
        }
        if (compactMaxScroll > 0) {
            int thumb = Math.min(height, Math.max(16, height * height / contentHeight));
            int thumbY = y + (height - thumb) * compactScroll / compactMaxScroll;
            ModernUiRenderer.drawRoundedRect(x + width - 5, y, 2, height, 1, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(x + width - 5, thumbY, 2, thumb, 1, ModernUiRenderer.ACCENT);
        }
    }

    private void drawQrPanel(FontRenderer fontRenderer, ModernMainLayout.Rect bounds) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        String title = I18n.format("gui.donate.qr_title");
        ModernUiRenderer.drawText(fontRenderer, title, bounds.x + 12, bounds.y + 10, ModernUiRenderer.TEXT,
                Math.max(30, bounds.width - 24));

        int availableWidth = Math.max(1, bounds.width - 28);
        int availableHeight = Math.max(1, bounds.height - 52);
        int size = Math.max(1, Math.min(availableWidth, availableHeight));
        int qrX = bounds.x + (bounds.width - size) / 2;
        int qrY = bounds.y + 34 + Math.max(0, (availableHeight - size) / 2);
        ModernUiRenderer.drawRoundedRect(qrX - 5, qrY - 5, size + 10, size + 10, 5, 0xFFFFFFFF);
        ModernUiRenderer.drawRoundedRect(qrX - 6, qrY - 6, size + 12, size + 12, 5,
                ModernUiRenderer.ACCENT_DIM);
        ModernUiRenderer.drawRoundedRect(qrX - 4, qrY - 4, size + 8, size + 8, 4, 0xFFFFFFFF);

        if (qrTexture != null && qrTextureWidth > 0 && qrTextureHeight > 0) {
            Minecraft activeMinecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
            if (activeMinecraft != null) {
                activeMinecraft.getTextureManager().bindTexture(qrTexture);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(qrX, qrY, 0, 0, qrTextureWidth,
                        qrTextureHeight, size, size, qrTextureWidth, qrTextureHeight);
            }
        } else {
            drawCentered(fontRenderer, I18n.format("gui.donate.qr_placeholder"), bounds.x + bounds.width / 2,
                    qrY + size / 2 - fontRenderer.FONT_HEIGHT / 2, ModernUiRenderer.MUTED_TEXT, bounds.width - 24);
        }
    }

    private void drawDetailsPanel(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        contactCard.draw(fontRenderer, bounds.x, bounds.y, bounds.width, bounds.height, mouseX, mouseY);
    }

    private void drawCentered(FontRenderer fontRenderer, String text, int centerX, int y, int color, int maxWidth) {
        String safe = text == null ? "" : text;
        int width = Math.min(Math.max(1, maxWidth), fontRenderer.getStringWidth(safe));
        ModernUiRenderer.drawText(fontRenderer, safe, centerX - width / 2, y, color, Math.max(1, maxWidth));
    }

    private ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        if (bounds == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        int inset = Math.min(12, Math.min(bounds.width, bounds.height) / 8);
        return bounds.inset(Math.max(4, inset));
    }

    private void loadQrTexture() {
        qrTexture = null;
        qrTextureWidth = 0;
        qrTextureHeight = 0;
        Minecraft activeMinecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
        if (activeMinecraft == null) {
            return;
        }

        try (InputStream input = ModernDonationSupportTab.class.getClassLoader()
                .getResourceAsStream(PAYMENT_QR_RESOURCE)) {
            if (input == null) {
                zszlScriptMod.LOGGER.warn("[Donation] 未找到打赏码资源: {}", PAYMENT_QR_RESOURCE);
                return;
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return;
            }
            qrTextureWidth = image.getWidth();
            qrTextureHeight = image.getHeight();
            qrTexture = activeMinecraft.getTextureManager().getDynamicTextureLocation("zszl_donation_qr_modern",
                    new DynamicTexture(image));
        } catch (Exception exception) {
            zszlScriptMod.LOGGER.warn("[Donation] 读取内置打赏码图片失败", exception);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return scrollCompact(wheel) || contactCard.handleMouseWheel(wheel);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (compactLayout && compactViewportBounds != null && compactViewportBounds.contains(mouseX, mouseY)) {
            int before = compactScroll;
            compactScroll = Math.max(0, Math.min(compactMaxScroll,
                    compactScroll + (wheel > 0 ? -COMPACT_SCROLL_STEP : COMPACT_SCROLL_STEP)));
            if (before != compactScroll) {
                return true;
            }
        }
        return contactCard.handleMouseWheel(wheel);
    }

    private boolean scrollCompact(int wheel) {
        if (!compactLayout || compactMaxScroll <= 0 || wheel == 0) {
            return false;
        }
        int before = compactScroll;
        compactScroll = Math.max(0, Math.min(compactMaxScroll,
                compactScroll + (wheel > 0 ? -COMPACT_SCROLL_STEP : COMPACT_SCROLL_STEP)));
        return before != compactScroll;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return "";
    }

    @Override
    public void discardDraft() {
        contentBounds = null;
        panelBounds = null;
        compactViewportBounds = null;
        compactLayout = false;
        compactScroll = 0;
        compactMaxScroll = 0;
    }
}
