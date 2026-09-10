package com.zszl.zszlScriptMod.gui.components;

import com.zszl.zszlScriptMod.config.ChatOptimizationConfig.ImageQuality;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager;
import com.zszl.zszlScriptMod.gui.theme.ThemeConfigManager.ThemeProfile;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.TextureManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class GuiTheme {

    private GuiTheme() {
    }

    public static int PANEL_BORDER = 0xFF3A5A78;
    public static int PANEL_BG_TOP = 0xD01A2532;
    public static int PANEL_BG_BOTTOM = 0xD0121B26;
    public static int TITLE_LEFT = 0xFF2E8BC0;
    public static int TITLE_RIGHT = 0xFF56CCF2;
    public static int TITLE_TEXT = 0xFFF2F8FF;
    public static int LABEL_TEXT = 0xFFE6EEF8;
    public static int SUB_TEXT = 0xFF9FB2C8;

    public static int resolveTextColor(String text, int originalColor) {
        boolean formatted = text != null && text.indexOf('\u00A7') >= 0;
        int plainRgb = originalColor & 0x00FFFFFF;
        boolean defaultWhite = plainRgb == 0x00FFFFFF;
        // Keep explicit status colours (success, warning, error, disabled)
        // intact. Only vanilla white receives the theme's readable label tone.
        int base = formatted || !defaultWhite ? originalColor : LABEL_TEXT;
        return applyOpacity(base, TEXT_OPACITY_PERCENT);
    }

    // Layout tokens
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;

    // State colors
    public static int STATE_SUCCESS = 0xFF2FAF5E;
    public static int STATE_WARNING = 0xFFE0A100;
    public static int STATE_DANGER = 0xFFB64A4A;
    public static int STATE_DISABLED = 0xFF4A4F56;
    public static int STATE_SELECTED = 0xFF3E85B5;

    public static int BUTTON_BG_NORMAL = 0xFF2F475F;
    public static int BUTTON_BG_HOVER = 0xFF385470;
    public static int BUTTON_BG_PRESSED = 0xFF2A4055;
    public static int BUTTON_BORDER_NORMAL = 0xFF466786;
    public static int BUTTON_BORDER_HOVER = 0xFF7ED0FF;

    public static int INPUT_BG = 0xFF1E242D;
    public static int INPUT_BORDER = 0xFF3E6A8D;
    public static int INPUT_BORDER_HOVER = 0xFF68B6E8;
    public static int INPUT_CURSOR = 0xFFFFFFFF;

    public static int CORNER_RADIUS = 6;
    public static int BORDER_WIDTH = 1;
    public static int SHADOW_OFFSET = 0;
    public static int SHADOW_COLOR = 0xFF000000;

    // 0-100, 0=完全透明，100=完全不透明
    public static int PANEL_OPACITY_PERCENT = 45;
    public static int BUTTON_OPACITY_PERCENT = 100;
    public static int INPUT_OPACITY_PERCENT = 100;
    public static int TEXT_OPACITY_PERCENT = 100;

    public static String PANEL_IMAGE_PATH = "";
    public static boolean PANEL_IMAGE_ENABLED = true;
    @Deprecated public static String BUTTON_IMAGE_PATH = "";
    @Deprecated public static String INPUT_IMAGE_PATH = "";
    public static int PANEL_IMAGE_SCALE = 100;
    @Deprecated public static int BUTTON_IMAGE_SCALE = 100;
    @Deprecated public static int INPUT_IMAGE_SCALE = 100;
    public static int PANEL_CROP_X = 0;
    public static int PANEL_CROP_Y = 0;
    @Deprecated public static int BUTTON_CROP_X = 0;
    @Deprecated public static int BUTTON_CROP_Y = 0;
    @Deprecated public static int INPUT_CROP_X = 0;
    @Deprecated public static int INPUT_CROP_Y = 0;
    public static String PANEL_IMAGE_QUALITY = "MEDIUM";
    @Deprecated public static String BUTTON_IMAGE_QUALITY = "MEDIUM";
    @Deprecated public static String INPUT_IMAGE_QUALITY = "MEDIUM";
    // Wallpaper visibility is independent from the panel-opacity slider.
    private static final int MENU_BACKGROUND_IMAGE_OPACITY_PERCENT = 60;

    static {
        ThemeConfigManager.ensureLoaded();
    }

    public enum UiState {
        NORMAL,
        HOVER,
        PRESSED,
        DISABLED,
        SUCCESS,
        WARNING,
        DANGER,
        SELECTED
    }

    public static void drawPanel(int x, int y, int width, int height) {
        drawPanelSegment(x, y, width, height, x, y, width, height);
    }

    /** Draws the configured artwork once behind the complete main menu. */
    public static void drawPanelBackground(int x, int y, int width, int height) {
        if (!PANEL_IMAGE_ENABLED) {
            return;
        }
        drawTextureBackgroundSegment(x, y, width, height, x, y, width, height,
                PANEL_IMAGE_PATH, PANEL_IMAGE_QUALITY, PANEL_IMAGE_SCALE, PANEL_CROP_X, PANEL_CROP_Y,
                MENU_BACKGROUND_IMAGE_OPACITY_PERCENT);
    }

    /** Draws the selected theme background across the complete screen. */
    public static void drawBackdrop(int width, int height) {
        // Leave the Minecraft world untouched. The configured artwork is drawn
        // inside the modern shell by drawPanelBackground().
    }

    /**
     * 绘制可拼接的面板分片。
     * <p>
     * 当多个面板希望看起来像“同一张背景图被切开”时，传入同一组 group 参数，
     * 即可让纹理采样保持连续。
     */
    public static void drawPanelSegment(int x, int y, int width, int height,
            int groupX, int groupY, int groupWidth, int groupHeight) {
        int border = applyOpacity(PANEL_BORDER, PANEL_OPACITY_PERCENT);
        int fill = applyOpacity(PANEL_BG_BOTTOM, PANEL_OPACITY_PERCENT);
        ModernUiRenderer.drawPanel(x, y, width, height, 8, fill, border);
        ModernUiRenderer.drawDivider(x, y, width, border);
    }

    public static void drawSectionTitle(int x, int y, String title, FontRenderer fr) {
        fr.drawStringWithShadow(title, x, y, applyOpacity(LABEL_TEXT, TEXT_OPACITY_PERCENT));
    }

    public static void drawStatusText(int x, int y, String text, UiState state, FontRenderer fr) {
        fr.drawStringWithShadow(text, x, y, applyOpacity(getStateTextColor(state), TEXT_OPACITY_PERCENT));
    }

    public static void drawButtonFrame(int x, int y, int width, int height, UiState state) {
        int borderColor = applyOpacity(getButtonBorderColor(state), BUTTON_OPACITY_PERCENT);
        int bgColor = applyOpacity(getButtonBgColor(state), BUTTON_OPACITY_PERCENT);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 5, bgColor, borderColor);
    }

    // 安全版：永远只绘制纯色，避免外部纹理/裁切影响关键操作控件
    public static void drawButtonFrameSafe(int x, int y, int width, int height, UiState state) {
        int borderColor = applyOpacity(getButtonBorderColor(state), BUTTON_OPACITY_PERCENT);
        int bgColor = applyOpacity(getButtonBgColor(state), BUTTON_OPACITY_PERCENT);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 5, bgColor, borderColor);
    }

    public static void drawToggleFrame(int x, int y, int width, int height, boolean on, UiState state) {
        UiState actualState = state;
        if (on && state != UiState.DISABLED) {
            actualState = (state == UiState.PRESSED) ? UiState.PRESSED : UiState.SUCCESS;
        }
        drawButtonFrame(x, y, width, height, actualState);
    }

    public static void drawInputFrame(int x, int y, int width, int height, boolean focused, boolean enabled) {
        UiState state = !enabled ? UiState.DISABLED : (focused ? UiState.HOVER : UiState.NORMAL);
        int border = applyOpacity(getInputBorderColor(state), INPUT_OPACITY_PERCENT);
        int bg = applyOpacity((state == UiState.DISABLED) ? STATE_DISABLED : INPUT_BG, INPUT_OPACITY_PERCENT);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 4, bg, border);
    }

    // 安全版：永远只绘制纯色，保证输入内容不会被外部纹理遮挡
    public static void drawInputFrameSafe(int x, int y, int width, int height, boolean focused, boolean enabled) {
        UiState state = !enabled ? UiState.DISABLED : (focused ? UiState.HOVER : UiState.NORMAL);
        int border = applyOpacity(getInputBorderColor(state), INPUT_OPACITY_PERCENT);
        int bg = applyOpacity((state == UiState.DISABLED) ? STATE_DISABLED : INPUT_BG, INPUT_OPACITY_PERCENT);
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 4, bg, border);
    }

    public static void applyProfile(ThemeProfile p) {
        if (p == null) {
            return;
        }
        PANEL_BORDER = p.panelBorder;
        PANEL_BG_TOP = p.panelBgTop;
        PANEL_BG_BOTTOM = p.panelBgBottom;
        TITLE_LEFT = p.titleLeft;
        TITLE_RIGHT = p.titleRight;
        TITLE_TEXT = p.titleText;
        LABEL_TEXT = p.labelText;
        SUB_TEXT = p.subText;

        STATE_SUCCESS = p.stateSuccess;
        STATE_WARNING = p.stateWarning;
        STATE_DANGER = p.stateDanger;
        STATE_DISABLED = p.stateDisabled;
        STATE_SELECTED = p.stateSelected;

        BUTTON_BG_NORMAL = p.buttonBgNormal;
        BUTTON_BG_HOVER = p.buttonBgHover;
        BUTTON_BG_PRESSED = p.buttonBgPressed;
        BUTTON_BORDER_NORMAL = p.buttonBorderNormal;
        BUTTON_BORDER_HOVER = p.buttonBorderHover;

        INPUT_BG = p.inputBg;
        INPUT_BORDER = p.inputBorder;
        INPUT_BORDER_HOVER = p.inputBorderHover;
        INPUT_CURSOR = p.getInputCursorColor();
        CORNER_RADIUS = clamp(p.cornerRadius == null ? 6 : p.cornerRadius, 0, 12);
        BORDER_WIDTH = clamp(p.borderWidth == null ? 1 : p.borderWidth, 1, 4);
        SHADOW_OFFSET = clamp(p.shadowOffset == null ? 0 : p.shadowOffset, 0, 8);
        SHADOW_COLOR = p.shadowColor == 0 ? 0xFF000000 : p.shadowColor;
        PANEL_OPACITY_PERCENT = clampPercent(p.panelOpacityPercent);
        BUTTON_OPACITY_PERCENT = clampPercent(p.buttonOpacityPercent);
        INPUT_OPACITY_PERCENT = clampPercent(p.inputOpacityPercent);
        TEXT_OPACITY_PERCENT = clampPercent(p.textOpacityPercent);

        PANEL_IMAGE_PATH = p.panelImagePath == null ? "" : p.panelImagePath;
        PANEL_IMAGE_ENABLED = ThemeConfigManager.isPanelImageEnabled(p);
        BUTTON_IMAGE_PATH = "";
        INPUT_IMAGE_PATH = "";
        PANEL_IMAGE_SCALE = p.panelImageScale;
        BUTTON_IMAGE_SCALE = p.buttonImageScale;
        INPUT_IMAGE_SCALE = p.inputImageScale;
        PANEL_CROP_X = p.panelCropX;
        PANEL_CROP_Y = p.panelCropY;
        BUTTON_CROP_X = p.buttonCropX;
        BUTTON_CROP_Y = p.buttonCropY;
        INPUT_CROP_X = p.inputCropX;
        INPUT_CROP_Y = p.inputCropY;
        PANEL_IMAGE_QUALITY = p.panelImageQuality == null ? "MEDIUM" : p.panelImageQuality;
        BUTTON_IMAGE_QUALITY = p.buttonImageQuality == null ? "MEDIUM" : p.buttonImageQuality;
        INPUT_IMAGE_QUALITY = p.inputImageQuality == null ? "MEDIUM" : p.inputImageQuality;

        ModernUiRenderer.applyTheme(applyOpacity(PANEL_BORDER, PANEL_OPACITY_PERCENT),
                applyOpacity(PANEL_BG_TOP, PANEL_OPACITY_PERCENT),
                applyOpacity(PANEL_BG_BOTTOM, PANEL_OPACITY_PERCENT), TITLE_LEFT,
                applyOpacity(TITLE_RIGHT, BUTTON_OPACITY_PERCENT),
                applyOpacity(TITLE_TEXT, TEXT_OPACITY_PERCENT), applyOpacity(LABEL_TEXT, TEXT_OPACITY_PERCENT),
                applyOpacity(SUB_TEXT, TEXT_OPACITY_PERCENT), applyOpacity(STATE_SUCCESS, TEXT_OPACITY_PERCENT),
                applyOpacity(STATE_WARNING, TEXT_OPACITY_PERCENT),
                applyOpacity(STATE_DISABLED, BUTTON_OPACITY_PERCENT),
                applyOpacity(STATE_SELECTED, BUTTON_OPACITY_PERCENT),
                applyOpacity(STATE_DANGER, BUTTON_OPACITY_PERCENT),
                applyOpacity(BUTTON_BG_NORMAL, BUTTON_OPACITY_PERCENT),
                applyOpacity(BUTTON_BG_HOVER, BUTTON_OPACITY_PERCENT),
                applyOpacity(BUTTON_BG_PRESSED, BUTTON_OPACITY_PERCENT),
                applyOpacity(BUTTON_BORDER_NORMAL, BUTTON_OPACITY_PERCENT),
                applyOpacity(BUTTON_BORDER_HOVER, BUTTON_OPACITY_PERCENT),
                applyOpacity(INPUT_BG, INPUT_OPACITY_PERCENT), applyOpacity(INPUT_BORDER, INPUT_OPACITY_PERCENT),
                applyOpacity(INPUT_BORDER_HOVER, INPUT_OPACITY_PERCENT), INPUT_CURSOR,
                CORNER_RADIUS, BORDER_WIDTH, SHADOW_OFFSET, SHADOW_COLOR,
                PANEL_IMAGE_PATH, PANEL_IMAGE_QUALITY, PANEL_IMAGE_SCALE, PANEL_CROP_X, PANEL_CROP_Y);

        // 预热缓存，避免首次显示时阻塞渲染线程
        if (PANEL_IMAGE_ENABLED) {
            TextureManagerHelper.prefetch(PANEL_IMAGE_PATH, parseQuality(PANEL_IMAGE_QUALITY));
        }
    }

    public static void drawTitleBar(int x, int y, int width, String title, FontRenderer fr) {
        int titleBarHeight = 24;
        ModernUiRenderer.drawRoundedRect(x, y, width, titleBarHeight, 7,
                applyOpacity(TITLE_LEFT, PANEL_OPACITY_PERCENT));
        ModernUiRenderer.drawDivider(x + 9, y + titleBarHeight - 1, Math.max(1, width - 18),
                applyOpacity(TITLE_RIGHT, PANEL_OPACITY_PERCENT));
        if (fr != null) {
            String safeTitle = title == null ? "" : title;
            ModernUiRenderer.drawText(fr, safeTitle, x + Math.max(10, (width - fr.getStringWidth(safeTitle)) / 2), y + 8,
                    applyOpacity(TITLE_TEXT, TEXT_OPACITY_PERCENT), Math.max(1, width - 20));
        }
    }

    public static void drawCardHighlight(int x, int y, int width, int height, boolean hovered) {
        if (!hovered) {
            return;
        }
        ModernUiRenderer.drawRoundedRect(x, y, width, height, 5, 0x293F6576);
        ModernUiRenderer.drawRoundedRect(x, y + 4, 3, Math.max(8, height - 8), 2, ModernUiRenderer.ACCENT);
    }

    public static void drawScrollbar(int x, int y, int width, int height, int thumbY, int thumbHeight) {
        if (height <= 0) return;
        thumbHeight = Math.min(height, Math.max(4, thumbHeight));
        thumbY = Math.max(y, Math.min(y + height - thumbHeight, thumbY));
        ModernUiRenderer.drawRoundedRect(x, y, Math.max(2, width), Math.max(1, height), 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(x, thumbY, Math.max(2, width), thumbHeight, 2,
                ModernUiRenderer.SUBTLE_TEXT);
    }

    public static void drawEmptyState(int centerX, int y, String text, FontRenderer fr) {
        fr.drawStringWithShadow(text, centerX - fr.getStringWidth(text) / 2, y,
                applyOpacity(SUB_TEXT, TEXT_OPACITY_PERCENT));
    }

    public static int getStateTextColor(UiState state) {
        switch (state) {
            case SUCCESS:
                return STATE_SUCCESS;
            case WARNING:
                return STATE_WARNING;
            case DANGER:
                return STATE_DANGER;
            case DISABLED:
                return STATE_DISABLED;
            case SELECTED:
                return STATE_SELECTED;
            case HOVER:
            case PRESSED:
                return TITLE_TEXT;
            default:
                return LABEL_TEXT;
        }
    }

    private static int getButtonBorderColor(UiState state) {
        switch (state) {
            case DISABLED:
                return STATE_DISABLED;
            case HOVER:
            case PRESSED:
                return BUTTON_BORDER_HOVER;
            case SUCCESS:
                return STATE_SUCCESS;
            case WARNING:
                return STATE_WARNING;
            case DANGER:
                return STATE_DANGER;
            case SELECTED:
                return STATE_SELECTED;
            default:
                return BUTTON_BORDER_NORMAL;
        }
    }

    private static int getButtonBgColor(UiState state) {
        switch (state) {
            case DISABLED:
                return STATE_DISABLED;
            case PRESSED:
                return BUTTON_BG_PRESSED;
            case HOVER:
                return BUTTON_BG_HOVER;
            case SUCCESS:
                return STATE_SUCCESS;
            case WARNING:
                return STATE_WARNING;
            case DANGER:
                return STATE_DANGER;
            case SELECTED:
                return STATE_SELECTED;
            default:
                return BUTTON_BG_NORMAL;
        }
    }

    private static int getInputBorderColor(UiState state) {
        switch (state) {
            case DISABLED:
                return STATE_DISABLED;
            case HOVER:
                return INPUT_BORDER_HOVER;
            default:
                return INPUT_BORDER;
        }
    }

    private static boolean drawTextureBackground(int x, int y, int width, int height, String path, String qualityName,
            int scale, int cropX, int cropY, int opacityPercent) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        ImageQuality quality = parseQuality(qualityName);
        ResourceLocation tex = TextureManagerHelper.getResourceLocationForPath(path, quality);
        if (tex == null && path.trim().toLowerCase().startsWith("builtin:")) {
            String resourcePath = path.trim().substring("builtin:".length()).replace('\\', '/');
            while (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            if (!resourcePath.isEmpty()) {
                tex = new ResourceLocation("zszl_script", resourcePath);
            }
        }
        if (tex == null) {
            return false;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        float alpha = clampPercent(opacityPercent) / 100.0F;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

        int safeScale = Math.max(10, Math.min(300, scale));
        float scaleFactor = 100.0F / safeScale;
        int[] texSize = TextureManagerHelper.getTextureSizeForPath(path, quality);
        int texW = (texSize != null) ? texSize[0] : width;
        int texH = (texSize != null) ? texSize[1] : height;

        int sampleW = Math.max(1, Math.min(texW, (int) (width * scaleFactor)));
        int sampleH = Math.max(1, Math.min(texH, (int) (height * scaleFactor)));
        int u = Math.max(0, cropX);
        int v = Math.max(0, cropY);
        if (u + sampleW > texW) {
            u = Math.max(0, texW - sampleW);
        }
        if (v + sampleH > texH) {
            v = Math.max(0, texH - sampleH);
        }

        Gui.drawScaledCustomSizeModalRect(x, y, u, v, sampleW, sampleH, width,
                height, texW, texH);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    /**
     * 与 drawTextureBackground 类似，但按“整组区域”进行统一采样，
     * 用于左右拼接面板保持背景连续。
     */
    private static boolean drawTextureBackgroundSegment(int x, int y, int width, int height,
            int groupX, int groupY, int groupWidth, int groupHeight,
            String path, String qualityName,
            int scale, int cropX, int cropY, int opacityPercent) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        ImageQuality quality = parseQuality(qualityName);
        ResourceLocation tex = TextureManagerHelper.getResourceLocationForPath(path, quality);
        // Built-in artwork is available directly from the mod jar. Use it while the
        // processed dynamic texture is being prepared so the default theme is visible
        // on its first frame instead of falling back to a flat blue panel.
        if (tex == null && path.trim().toLowerCase().startsWith("builtin:")) {
            String resourcePath = path.trim().substring("builtin:".length()).replace('\\', '/');
            while (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            if (!resourcePath.isEmpty()) {
                tex = new ResourceLocation("zszl_script", resourcePath);
            }
        }
        if (tex == null) {
            return false;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        float alpha = clampPercent(opacityPercent) / 100.0F;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

        int safeScale = Math.max(10, Math.min(300, scale));
        float scaleFactor = 100.0F / safeScale;
        int[] texSize = TextureManagerHelper.getTextureSizeForPath(path, quality);
        int texW = (texSize != null) ? texSize[0] : Math.max(1, groupWidth);
        int texH = (texSize != null) ? texSize[1] : Math.max(1, groupHeight);

        int fullSampleW = Math.max(1, Math.min(texW, Math.round(groupWidth * scaleFactor)));
        int fullSampleH = Math.max(1, Math.min(texH, Math.round(groupHeight * scaleFactor)));

        float groupSafeW = Math.max(1.0F, groupWidth);
        float groupSafeH = Math.max(1.0F, groupHeight);
        int segOffsetX = Math.max(0, Math.round(((x - groupX) / groupSafeW) * fullSampleW));
        int segOffsetY = Math.max(0, Math.round(((y - groupY) / groupSafeH) * fullSampleH));
        int segSampleW = Math.max(1, Math.round((width / groupSafeW) * fullSampleW));
        int segSampleH = Math.max(1, Math.round((height / groupSafeH) * fullSampleH));

        int uBase = Math.max(0, cropX);
        int vBase = Math.max(0, cropY);

        if (uBase + fullSampleW > texW) {
            uBase = Math.max(0, texW - fullSampleW);
        }
        if (vBase + fullSampleH > texH) {
            vBase = Math.max(0, texH - fullSampleH);
        }

        int u = uBase + segOffsetX;
        int v = vBase + segOffsetY;

        // 保证分片采样范围不越界
        if (segOffsetX + segSampleW > fullSampleW) {
            segSampleW = Math.max(1, fullSampleW - segOffsetX);
        }
        if (segOffsetY + segSampleH > fullSampleH) {
            segSampleH = Math.max(1, fullSampleH - segOffsetY);
        }
        if (u + segSampleW > texW) {
            segSampleW = Math.max(1, texW - u);
        }
        if (v + segSampleH > texH) {
            segSampleH = Math.max(1, texH - v);
        }

        Gui.drawScaledCustomSizeModalRect(x, y, u, v, segSampleW, segSampleH, width,
                height, texW, texH);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    private static ImageQuality parseQuality(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ImageQuality.MEDIUM;
        }
        try {
            return ImageQuality.valueOf(name.toUpperCase());
        } catch (Exception ignore) {
            return ImageQuality.MEDIUM;
        }
    }

    private static int clampPercent(int p) {
        return Math.max(0, Math.min(100, p));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int applyOpacity(int color, int percent) {
        int p = clampPercent(percent);
        int a = (color >>> 24) & 0xFF;
        int na = (a * p) / 100;
        return (na << 24) | (color & 0x00FFFFFF);
    }

    private static void drawVerticalGradientRect(int left, int top, int right, int bottom, int topColor,
            int bottomColor) {
        int height = Math.max(1, bottom - top);
        for (int i = 0; i < height; i++) {
            float t = i / (float) (height - 1 <= 0 ? 1 : height - 1);
            int c = lerpColor(topColor, bottomColor, t);
            Gui.drawRect(left, top + i, right, top + i + 1, c);
        }
    }

    private static void drawHorizontalGradientRect(int left, int top, int right, int bottom, int leftColor,
            int rightColor) {
        int width = Math.max(1, right - left);
        for (int i = 0; i < width; i++) {
            float t = i / (float) (width - 1 <= 0 ? 1 : width - 1);
            int c = lerpColor(leftColor, rightColor, t);
            Gui.drawRect(left + i, top, left + i + 1, bottom, c);
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
