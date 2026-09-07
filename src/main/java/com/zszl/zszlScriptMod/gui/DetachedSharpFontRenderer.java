package com.zszl.zszlScriptMod.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

/** Device-pixel text renderer shared by the in-game and detached modern UI. */
final class DetachedSharpFontRenderer extends FontRenderer {

    private static final int SOURCE_FONT_SIZE = 64;
    private static final int MAX_CACHED_STRINGS = 512;
    private static final int MAX_TEXTURE_WIDTH = 4096;
    private static final int IMAGE_PADDING = 1;
    private static final String CJK_SAMPLE = "控制中心路径动作参数搜索默认分类";
    private static final String[] UI_FONT_CANDIDATES = {
            "Microsoft YaHei UI", "Microsoft YaHei", "DengXian",
            "Noto Sans CJK SC", "Source Han Sans SC", "PingFang SC",
            "Microsoft JhengHei UI", "Microsoft JhengHei"
    };

    private final FontRenderer metricsSource;
    private final Font awtFont = createUiFont();
    private final Map<String, TextTexture> textures = new LinkedHashMap<>(128, 0.75F, true);
    private boolean loggedFailure;
    private final FloatBuffer modelMatrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);

    DetachedSharpFontRenderer(Minecraft minecraft, FontRenderer metricsSource) {
        super(minecraft.gameSettings, new ResourceLocation("textures/font/ascii.png"),
                minecraft.getTextureManager(), false);
        this.metricsSource = metricsSource;
        if (metricsSource != null) {
            setUnicodeFlag(metricsSource.getUnicodeFlag());
        }
    }

    @Override
    public int getStringWidth(String text) {
        return metricsSource == null ? super.getStringWidth(text) : metricsSource.getStringWidth(text);
    }

    @Override
    public int getCharWidth(char character) {
        return metricsSource == null ? super.getCharWidth(character) : metricsSource.getCharWidth(character);
    }

    @Override
    public int drawString(String text, float x, float y, int color, boolean dropShadow) {
        if (text == null || text.isEmpty()) {
            return Math.round(x);
        }
        String visible = TextFormatting.getTextWithoutFormattingCodes(text);
        int logicalWidth = Math.max(0, getStringWidth(text));
        if (visible == null || visible.isEmpty() || logicalWidth <= 0) {
            return Math.round(x) + logicalWidth;
        }
        try {
            GuiTextPixelGrid grid = readPixelGrid();
            if (grid == null) {
                return super.drawString(text, x, y, color, dropShadow);
            }
            TextTexture texture = getOrCreateTexture(visible, logicalWidth, grid);
            if (texture == null) {
                return super.drawString(text, x, y, color, dropShadow);
            }
            int opaqueColor = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
            // Coverage at glyph edges must not depend on the alpha-test
            // threshold left by whichever widget happened to render first.
            boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            GlStateManager.disableAlpha();
            try {
                if (dropShadow) {
                    int shadowColor = (opaqueColor & 0xFCFCFC) >> 2 | opaqueColor & 0xFF000000;
                    drawTexture(texture, x + 1.0F, y + 1.0F, grid, shadowColor);
                }
                drawTexture(texture, x, y, grid, opaqueColor);
            } finally {
                if (alphaTest) {
                    GlStateManager.enableAlpha();
                }
            }
            return Math.round(x) + logicalWidth;
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                com.zszl.zszlScriptMod.zszlScriptMod.LOGGER.warn("现代界面高清字体渲染失败，已回退原版字体", throwable);
            }
            return super.drawString(text, x, y, color, dropShadow);
        }
    }

    void release() {
        for (TextTexture texture : textures.values()) {
            GlStateManager.deleteTexture(texture.textureId);
        }
        textures.clear();
    }

    private TextTexture getOrCreateTexture(String text, int logicalWidth, GuiTextPixelGrid grid) {
        double scaleX = Math.abs(grid.scaleX);
        double scaleY = Math.abs(grid.scaleY);
        int contentPixelWidth = Math.max(1, Math.min(MAX_TEXTURE_WIDTH - IMAGE_PADDING * 2,
                (int) Math.ceil(logicalWidth * scaleX - 1e-4)));
        int contentPixelHeight = Math.max(1, (int) Math.ceil(FONT_HEIGHT * scaleY - 1e-4));
        TextTexture cached = textures.get(text);
        if (cached != null && cached.contentPixelWidth == contentPixelWidth
                && cached.contentPixelHeight == contentPixelHeight) {
            return cached;
        }
        if (cached != null) {
            GlStateManager.deleteTexture(cached.textureId);
            textures.remove(text);
        }
        TextTexture created = createTexture(text, contentPixelWidth, contentPixelHeight);
        if (created == null) {
            return null;
        }
        while (textures.size() >= MAX_CACHED_STRINGS) {
            Iterator<TextTexture> iterator = textures.values().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            GlStateManager.deleteTexture(iterator.next().textureId);
            iterator.remove();
        }
        textures.put(text, created);
        return created;
    }

    private TextTexture createTexture(String text, int contentPixelWidth, int contentPixelHeight) {
        BufferedImage measurement = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measurement.createGraphics();
        Font targetFont;
        FontMetrics metrics;
        try {
            configureGraphics(measureGraphics, awtFont);
            targetFont = fitFont(measureGraphics, text, contentPixelWidth, contentPixelHeight);
            metrics = measureGraphics.getFontMetrics(targetFont);
        } finally {
            measureGraphics.dispose();
        }

        int imageWidth = contentPixelWidth + IMAGE_PADDING * 2;
        int imageHeight = contentPixelHeight + IMAGE_PADDING * 2;
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configureGraphics(graphics, targetFont);
            graphics.setColor(java.awt.Color.WHITE);
            int x = IMAGE_PADDING;
            // Keep the baseline on a device pixel; half-pixel centering softens
            // small horizontal strokes even when the texture itself is aligned.
            int y = IMAGE_PADDING + Math.round((contentPixelHeight - metrics.getHeight()) * 0.5F)
                    + metrics.getAscent();
            graphics.drawString(text, x, y);
        } finally {
            graphics.dispose();
        }

        ByteBuffer pixels = BufferUtils.createByteBuffer(imageWidth * imageHeight * 4);
        int[] argb = image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);
        for (int value : argb) {
            pixels.put((byte) 0xFF);
            pixels.put((byte) 0xFF);
            pixels.put((byte) 0xFF);
            pixels.put((byte) ((value >>> 24) & 0xFF));
        }
        // Compile against Buffer's Java 8 descriptor. Java 9 narrowed the
        // return type of ByteBuffer.flip(), which causes NoSuchMethodError
        // when a newer compiler's bytecode runs in Minecraft's Java 8 JVM.
        ((Buffer) pixels).flip();

        int textureId = GL11.glGenTextures();
        GlStateManager.bindTexture(textureId);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, imageWidth, imageHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return new TextTexture(textureId, contentPixelWidth, contentPixelHeight, imageWidth, imageHeight);
    }

    private Font fitFont(Graphics2D graphics, String text, int targetWidth, int targetHeight) {
        FontMetrics sourceMetrics = graphics.getFontMetrics(awtFont);
        int size = Math.max(1, Math.round(awtFont.getSize2D()
                * targetHeight / Math.max(1.0F, sourceMetrics.getHeight())));
        Font fitted = awtFont.deriveFont((float) size);
        // Measure with the same pixel-rounded advances used for rasterization.
        // Recheck both bounds after rounding so labels cannot overflow their
        // existing Minecraft layout or lose their descenders.
        while (size > 1) {
            FontMetrics fittedMetrics = graphics.getFontMetrics(fitted);
            if (fittedMetrics.stringWidth(text) <= targetWidth && fittedMetrics.getHeight() <= targetHeight) {
                break;
            }
            fitted = awtFont.deriveFont((float) --size);
        }
        return fitted;
    }

    private static Font createUiFont() {
        String family = Font.SANS_SERIF;
        try {
            Set<String> available = new HashSet<>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                available.add(name.toLowerCase(Locale.ROOT));
            }
            for (String candidate : UI_FONT_CANDIDATES) {
                if (!available.contains(candidate.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Font font = new Font(candidate, Font.PLAIN, SOURCE_FONT_SIZE);
                if (font.canDisplayUpTo(CJK_SAMPLE) < 0) {
                    family = candidate;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        Font base = new Font(family, Font.PLAIN, SOURCE_FONT_SIZE);
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.putAll(base.getAttributes());
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD);
        return base.deriveFont(attributes);
    }

    private GuiTextPixelGrid readPixelGrid() {
        // Read the real transform, including GUI scale, embedded panels and
        // translations. currentScreen.width is rounded and can change mid-frame.
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionMatrix);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        return GuiTextPixelGrid.fromMatrices(modelMatrix, projectionMatrix, viewport);
    }

    private void configureGraphics(Graphics2D graphics, Font font) {
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void drawTexture(TextTexture texture, float x, float y, GuiTextPixelGrid grid, int color) {
        double scaleX = Math.abs(grid.scaleX);
        double scaleY = Math.abs(grid.scaleY);
        float left = (float) (grid.alignX(x) - IMAGE_PADDING / scaleX);
        float top = (float) (grid.alignY(y) - IMAGE_PADDING / scaleY);
        float right = left + (float) (texture.imagePixelWidth / scaleX);
        float bottom = top + (float) (texture.imagePixelHeight / scaleY);
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(texture.textureId);
        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex3f(left, top, 0.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex3f(left, bottom, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex3f(right, bottom, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex3f(right, top, 0.0F);
        GL11.glEnd();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static final class TextTexture {
        private final int textureId;
        private final int contentPixelWidth;
        private final int contentPixelHeight;
        private final int imagePixelWidth;
        private final int imagePixelHeight;

        private TextTexture(int textureId, int contentPixelWidth, int contentPixelHeight,
                int imagePixelWidth, int imagePixelHeight) {
            this.textureId = textureId;
            this.contentPixelWidth = contentPixelWidth;
            this.contentPixelHeight = contentPixelHeight;
            this.imagePixelWidth = imagePixelWidth;
            this.imagePixelHeight = imagePixelHeight;
        }
    }
}
