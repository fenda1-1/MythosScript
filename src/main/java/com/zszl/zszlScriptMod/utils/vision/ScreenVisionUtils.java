package com.zszl.zszlScriptMod.utils.vision;

import com.zszl.zszlScriptMod.system.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

import javax.imageio.ImageIO;

public final class ScreenVisionUtils {

    public static final class RegionMetrics {
        private final boolean found;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int averageR;
        private final int averageG;
        private final int averageB;
        private final int centerR;
        private final int centerG;
        private final int centerB;
        private final double brightness;
        private final double edgeDensity;

        public RegionMetrics(boolean found, int x, int y, int width, int height, int averageR, int averageG, int averageB,
                int centerR, int centerG, int centerB, double brightness, double edgeDensity) {
            this.found = found;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.averageR = averageR;
            this.averageG = averageG;
            this.averageB = averageB;
            this.centerR = centerR;
            this.centerG = centerG;
            this.centerB = centerB;
            this.brightness = brightness;
            this.edgeDensity = edgeDensity;
        }

        public boolean isFound() {
            return found;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getAverageR() {
            return averageR;
        }

        public int getAverageG() {
            return averageG;
        }

        public int getAverageB() {
            return averageB;
        }

        public int getCenterR() {
            return centerR;
        }

        public int getCenterG() {
            return centerG;
        }

        public int getCenterB() {
            return centerB;
        }

        public double getBrightness() {
            return brightness;
        }

        public double getEdgeDensity() {
            return edgeDensity;
        }

        public String getAverageHex() {
            return formatHex(averageR, averageG, averageB);
        }

        public String getCenterHex() {
            return formatHex(centerR, centerG, centerB);
        }
    }

    public static final class TemplateMatchResult {
        private final boolean found;
        private final double similarity;
        private final int width;
        private final int height;

        public TemplateMatchResult(boolean found, double similarity, int width, int height) {
            this.found = found;
            this.similarity = similarity;
            this.width = width;
            this.height = height;
        }

        public boolean isFound() {
            return found;
        }

        public double getSimilarity() {
            return similarity;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    private ScreenVisionUtils() {
    }

    public static RegionMetrics analyzeRegion(int x, int y, int width, int height) {
        BufferedImage screenshot = captureCurrentScreenshot();
        if (screenshot == null) {
            return new RegionMetrics(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0D, 0D);
        }

        RegionRect rect = resolveRegionRect(screenshot, x, y, width, height);
        if (rect == null) {
            return new RegionMetrics(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0D, 0D);
        }

        BufferedImage sub = screenshot.getSubimage(rect.x, rect.y, rect.width, rect.height);
        int sampleStep = Math.max(1, (int) Math.sqrt((rect.width * rect.height) / 4096.0D));
        long sumR = 0L;
        long sumG = 0L;
        long sumB = 0L;
        long count = 0L;
        long edgeCount = 0L;
        long edgeTotal = 0L;

        for (int py = 0; py < rect.height; py += sampleStep) {
            for (int px = 0; px < rect.width; px += sampleStep) {
                int rgb = sub.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                sumR += r;
                sumG += g;
                sumB += b;
                count++;

                if (px + sampleStep < rect.width) {
                    edgeTotal++;
                    int next = sub.getRGB(px + sampleStep, py);
                    if (colorDistance(rgb, next) >= 48.0D) {
                        edgeCount++;
                    }
                }
                if (py + sampleStep < rect.height) {
                    edgeTotal++;
                    int next = sub.getRGB(px, py + sampleStep);
                    if (colorDistance(rgb, next) >= 48.0D) {
                        edgeCount++;
                    }
                }
            }
        }

        if (count <= 0L) {
            return new RegionMetrics(false, rect.x, rect.y, rect.width, rect.height, 0, 0, 0, 0, 0, 0, 0D, 0D);
        }

        int averageR = (int) Math.round(sumR / (double) count);
        int averageG = (int) Math.round(sumG / (double) count);
        int averageB = (int) Math.round(sumB / (double) count);
        int centerRgb = sub.getRGB(Math.max(0, rect.width / 2), Math.max(0, rect.height / 2));
        int centerR = (centerRgb >> 16) & 0xFF;
        int centerG = (centerRgb >> 8) & 0xFF;
        int centerB = centerRgb & 0xFF;
        double brightness = (0.2126D * averageR + 0.7152D * averageG + 0.0722D * averageB) / 255.0D;
        double edgeDensity = edgeTotal <= 0L ? 0D : edgeCount / (double) edgeTotal;

        return new RegionMetrics(true, rect.guiX, rect.guiY, rect.guiWidth, rect.guiHeight, averageR, averageG, averageB,
                centerR, centerG, centerB, brightness, edgeDensity);
    }

    public static TemplateMatchResult compareRegionToTemplate(int x, int y, int width, int height, String imagePath) {
        BufferedImage screenshot = captureCurrentScreenshot();
        if (screenshot == null) {
            return new TemplateMatchResult(false, 0D, 0, 0);
        }

        RegionRect rect = resolveRegionRect(screenshot, x, y, width, height);
        if (rect == null) {
            return new TemplateMatchResult(false, 0D, 0, 0);
        }

        BufferedImage current = screenshot.getSubimage(rect.x, rect.y, rect.width, rect.height);
        BufferedImage template = loadTemplate(imagePath);
        if (template == null) {
            return new TemplateMatchResult(false, 0D, rect.guiWidth, rect.guiHeight);
        }

        BufferedImage scaledTemplate = template;
        if (template.getWidth() != current.getWidth() || template.getHeight() != current.getHeight()) {
            scaledTemplate = new BufferedImage(current.getWidth(), current.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaledTemplate.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(template, 0, 0, current.getWidth(), current.getHeight(), null);
            graphics.dispose();
        }

        int step = Math.max(1, (int) Math.sqrt((current.getWidth() * current.getHeight()) / 8192.0D));
        double diff = 0D;
        int sampleCount = 0;
        for (int py = 0; py < current.getHeight(); py += step) {
            for (int px = 0; px < current.getWidth(); px += step) {
                diff += colorDistance(current.getRGB(px, py), scaledTemplate.getRGB(px, py));
                sampleCount++;
            }
        }
        if (sampleCount <= 0) {
            return new TemplateMatchResult(false, 0D, rect.guiWidth, rect.guiHeight);
        }

        double averageDiff = diff / sampleCount;
        double similarity = Math.max(0D, 1.0D - (averageDiff / 441.67295593D));
        return new TemplateMatchResult(true, similarity, rect.guiWidth, rect.guiHeight);
    }

    public static int parseColor(String colorText, int fallback) {
        String normalized = colorText == null ? "" : colorText.trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 3) {
            normalized = "" + normalized.charAt(0) + normalized.charAt(0)
                    + normalized.charAt(1) + normalized.charAt(1)
                    + normalized.charAt(2) + normalized.charAt(2);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static double colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    public static String formatHex(int r, int g, int b) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", clampColor(r), clampColor(g), clampColor(b));
    }

    private static BufferedImage captureCurrentScreenshot() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                return null;
            }
            Framebuffer framebuffer = mc.getFramebuffer();
            return ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, framebuffer);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage loadTemplate(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        try {
            File file = new File(imagePath.trim());
            if (!file.isAbsolute()) {
                file = ProfileManager.getCurrentProfileDir().resolve(imagePath.trim()).toFile();
            }
            if (!file.exists() || !file.isFile()) {
                return null;
            }
            return ImageIO.read(file);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static RegionRect resolveRegionRect(BufferedImage screenshot, int guiX, int guiY, int guiWidth, int guiHeight) {
        if (screenshot == null) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(mc);
        int scaledWidth = Math.max(1, scaled.getScaledWidth());
        int scaledHeight = Math.max(1, scaled.getScaledHeight());
        int width = Math.max(1, guiWidth);
        int height = Math.max(1, guiHeight);
        int x = Math.max(0, guiX);
        int y = Math.max(0, guiY);
        if (x >= scaledWidth || y >= scaledHeight) {
            return null;
        }
        width = Math.min(width, scaledWidth - x);
        height = Math.min(height, scaledHeight - y);

        double scaleX = screenshot.getWidth() / (double) scaledWidth;
        double scaleY = screenshot.getHeight() / (double) scaledHeight;
        int px = Math.max(0, (int) Math.round(x * scaleX));
        int py = Math.max(0, (int) Math.round(y * scaleY));
        int pw = Math.max(1, (int) Math.round(width * scaleX));
        int ph = Math.max(1, (int) Math.round(height * scaleY));
        if (px >= screenshot.getWidth() || py >= screenshot.getHeight()) {
            return null;
        }
        pw = Math.min(pw, screenshot.getWidth() - px);
        ph = Math.min(ph, screenshot.getHeight() - py);
        return new RegionRect(px, py, pw, ph, x, y, width, height);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class RegionRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int guiX;
        private final int guiY;
        private final int guiWidth;
        private final int guiHeight;

        private RegionRect(int x, int y, int width, int height, int guiX, int guiY, int guiWidth, int guiHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.guiX = guiX;
            this.guiY = guiY;
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
        }
    }
}

