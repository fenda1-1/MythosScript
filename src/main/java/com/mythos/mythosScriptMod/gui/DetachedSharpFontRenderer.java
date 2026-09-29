package com.mythos.mythosScriptMod.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

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
    // All AWT operations, including glyph metrics and rasterization, run here.
    // No render-thread wait: a blocked native font call cannot freeze Minecraft.
    private static final ThreadPoolExecutor FONT_WORKER = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(128), runnable -> {
                Thread thread = new Thread(runnable, "MythosScript-FontRasterizer");
                thread.setDaemon(true);
                return thread;
            });
    private static Font workerFont;
    private static boolean fontLoadAttempted;
    private static final Map<String, Integer> NATIVE_WIDTHS = new LinkedHashMap<>(128, 0.75F, true);
    private final FontRenderer metricsSource;
    private final Map<String, PendingTexture> pendingTextures = new LinkedHashMap<>(128, 0.75F, true);
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
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int legacy = metricsSource == null ? super.getStringWidth(text) : metricsSource.getStringWidth(text);
        String visible = TextFormatting.getTextWithoutFormattingCodes(text);
        int measured = nativeWidth(visible == null ? "" : visible);
        return measured > 0 ? measured : legacy;
    }

    @Override
    public String trimStringToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        int kept = 0;
        while (kept < text.length() && getStringWidth(text.substring(0, kept + 1)) <= width) {
            kept++;
        }
        return text.substring(0, kept);
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
                com.mythos.mythosScriptMod.mythosScriptMod.LOGGER.warn("现代界面高清字体渲染失败，已回退原版字体", throwable);
            }
            return super.drawString(text, x, y, color, dropShadow);
        }
    }

    void release() {
        for (PendingTexture pending : pendingTextures.values()) pending.image.cancel(false);
        pendingTextures.clear();
        FONT_WORKER.purge();
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
        PendingTexture pending = pendingTextures.get(text);
        if (pending != null && (pending.width != contentPixelWidth || pending.height != contentPixelHeight)) {
            pending.image.cancel(false);
            pendingTextures.remove(text);
            pending = null;
        }
        if (pending == null) {
            if (pendingTextures.size() >= MAX_CACHED_STRINGS) {
                Iterator<PendingTexture> oldest = pendingTextures.values().iterator();
                oldest.next().image.cancel(false);
                oldest.remove();
                FONT_WORKER.purge();
            }
            try {
                Future<BufferedImage> image = FONT_WORKER.submit(
                        () -> rasterize(text, contentPixelWidth, contentPixelHeight));
                pendingTextures.put(text, new PendingTexture(contentPixelWidth, contentPixelHeight, image));
            } catch (RejectedExecutionException busy) {
                // Bounded queue; retry on a later frame without blocking the game.
            }
            return null;
        }
        if (!pending.image.isDone()) return null;
        BufferedImage image;
        try {
            image = pending.image.get(); // isDone() above guarantees no wait.
        } catch (Exception failure) {
            return null;
        }
        if (image == null) return null;
        pendingTextures.remove(text);
        TextTexture created = uploadTexture(image, contentPixelWidth, contentPixelHeight);
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

    private static BufferedImage rasterize(String text, int contentPixelWidth, int contentPixelHeight) {
        Font awtFont = getWorkerFont();
        if (awtFont == null || awtFont.canDisplayUpTo(text) >= 0) return null;
        BufferedImage measurement = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = measurement.createGraphics();
        Font targetFont;
        FontMetrics metrics;
        try {
            configureGraphics(measureGraphics, awtFont);
            targetFont = fitFont(measureGraphics, awtFont, text, contentPixelWidth, contentPixelHeight);
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
            UiTextMetrics.draw(graphics, awtFont, text, contentPixelWidth, contentPixelHeight, IMAGE_PADDING);
        } finally {
            graphics.dispose();
        }

        return image;
    }

    private TextTexture uploadTexture(BufferedImage image, int contentPixelWidth, int contentPixelHeight) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
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

    private static Font fitFont(Graphics2D graphics, Font awtFont, String text, int targetWidth, int targetHeight) {
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

    private static int nativeWidth(String text) {
        Font font = getWorkerFont();
        if (font == null || text == null || text.isEmpty() || font.canDisplayUpTo(text) >= 0) {
            return 0;
        }
        Integer cached = NATIVE_WIDTHS.get(text);
        if (cached != null) {
            return cached;
        }
        BufferedImage measurement = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = measurement.createGraphics();
        try {
            configureGraphics(graphics, font);
            FontMetrics source = graphics.getFontMetrics(font);
            int size = Math.max(1, Math.round(font.getSize2D() * 9.0F / Math.max(1, source.getHeight())));
            Font fitted = font.deriveFont((float) size);
            int width = UiTextMetrics.width(font, text);
            if (NATIVE_WIDTHS.size() >= MAX_CACHED_STRINGS) {
                NATIVE_WIDTHS.clear();
            }
            NATIVE_WIDTHS.put(text, width);
            return width;
        } finally {
            graphics.dispose();
        }
    }

    private static Font getWorkerFont() {
        if (!fontLoadAttempted) {
            fontLoadAttempted = true;
            try {
                workerFont = BundledUiFont.load().deriveFont((float) SOURCE_FONT_SIZE);
            } catch (Exception failure) {
                com.mythos.mythosScriptMod.mythosScriptMod.LOGGER.warn("内置高清字体加载失败", failure);
            }
        }
        return workerFont;
    }

    private static final class PendingTexture {
        final int width;
        final int height;
        final Future<BufferedImage> image;
        PendingTexture(int width, int height, Future<BufferedImage> image) {
            this.width = width;
            this.height = height;
            this.image = image;
        }
    }

    private GuiTextPixelGrid readPixelGrid() {
        // Read the real transform, including GUI scale, embedded panels and
        // translations. currentScreen.width is rounded and can change mid-frame.
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelMatrix);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionMatrix);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        return GuiTextPixelGrid.fromMatrices(modelMatrix, projectionMatrix, viewport);
    }

    private static void configureGraphics(Graphics2D graphics, Font font) {
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
