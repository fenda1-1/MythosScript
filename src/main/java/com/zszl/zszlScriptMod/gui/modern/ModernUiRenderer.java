package com.zszl.zszlScriptMod.gui.modern;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.components.GuiTheme;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.TextFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;

/**
 * Small rendering toolkit for the modern dashboard. The shell stays vector-based;
 * the selected theme may provide one optional full-screen background image.
 */
public final class ModernUiRenderer {

    public enum Icon {
        BRAND,
        HOME,
        SEARCH,
        COMMAND,
        CLOSE,
        MORE,
        STOP,
        BACKGROUND_STOP,
        ROUTE,
        EXTERNAL_WINDOW,
        SETTINGS,
        INFO,
        PAUSE,
        RUNNING_STATUS,
        FOOD,
        FISHING,
        MOUSE,
        FLIGHT,
        FOLLOW,
        COMBAT,
        CONDITIONS,
        ESCAPE,
        KEYBOARD,
        PROFILE,
        CHAT,
        PICKUP,
        AUTO_USE,
        BLOCKS,
        STORAGE,
        LOOP,
        DEBUG,
        DISPLAY,
        RELOAD,
        INVENTORY,
        PACKET,
        INSPECTOR,
        PERFORMANCE,
        TERRAIN,
        MEMORY,
        MOVEMENT,
        MOVE_PANEL,
        RENDER,
        WORLD,
        MISC,
        SORT,
        GROUP,
        SIZE,
        COLUMNS,
        FLOW,
        PIN,
        PINNED,
        GENERIC
    }

    public static int BACKDROP = 0xE80A1016;
    public static int SHELL = 0xFF111921;
    public static int SHELL_RAISED = 0xFF16212B;
    public static int SURFACE = 0xFF19242E;
    public static int SURFACE_HOVER = 0xFF21303B;
    public static int SURFACE_PRESSED = 0xFF293A46;
    public static int BORDER = 0xFF334451;
    public static int BORDER_SUBTLE = 0xFF273641;
    public static int ACCENT = 0xFFF06B92;
    public static int ACCENT_DIM = 0xFF743A51;
    public static int SUCCESS = 0xFF5FD39A;
    public static int WARNING = 0xFFF0B55E;
    public static int TEXT = 0xFFF3F7FA;
    public static int SUBTLE_TEXT = 0xFF9BAAB6;
    public static int MUTED_TEXT = 0xFF71808D;
    public static int INPUT_SURFACE = 0xFF101820;
    private static int INPUT_CURSOR = 0xFFFFFFFF;
    public static int DISABLED_SURFACE = 0xFF141D24;
    public static int SELECTED_SURFACE = 0xFF2A3540;
    public static int SELECTED_TEXT = 0xFFF3F7FA;
    public static int DISABLED_TEXT = 0xFF9BAAB6;
    public static int ICON_SURFACE = 0xFF15212B;
    public static int ICON_TEXT = 0xFF9BAAB6;
    public static int TOOLTIP_SURFACE = 0xFF17222C;
    public static int TOOLTIP_TEXT = 0xFFF3F7FA;
    public static int TOOLTIP_SUBTLE_TEXT = 0xFF9BAAB6;
    public static int DANGER = 0xFFE47A86;
    public static int HOVER_BORDER = 0xFF617581;
    private static int THEME_CORNER_RADIUS = 6;
    private static int THEME_BORDER_WIDTH = 1;
    private static int THEME_SHADOW_OFFSET;
    private static int THEME_SHADOW_COLOR = 0xFF000000;
    private static final ThreadLocal<Deque<ClipState>> CLIP_STATES = new ThreadLocal<Deque<ClipState>>() {
        @Override
        protected Deque<ClipState> initialValue() {
            return new ArrayDeque<>();
        }
    };
    /**
     * Optional logical-to-host transform used while a legacy screen is drawn
     * inside the modern workbench. OpenGL scissor coordinates are framebuffer
     * coordinates and therefore cannot use the nested screen's raw logical
     * coordinates directly.
     */
    private static final ThreadLocal<Deque<ClipTransform>> CLIP_TRANSFORMS = new ThreadLocal<Deque<ClipTransform>>() {
        @Override
        protected Deque<ClipTransform> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private ModernUiRenderer() {
    }

    public static void drawBackdrop(int screenWidth, int screenHeight) {
        GuiTheme.drawBackdrop(screenWidth, screenHeight);
    }

    /** Copies the persisted theme into the palette consumed by modern screens. */
    public static void applyTheme(int panelBorder, int panelBgTop, int panelBgBottom, int titleLeft,
            int titleRight, int titleText, int labelText, int subText, int stateSuccess, int stateWarning,
            int stateDisabled, int stateSelected, int stateDanger, int buttonBgNormal, int buttonBgHover, int buttonBgPressed, int buttonBorderNormal,
            int buttonBorderHover,
            int inputBg, int inputBorder, int inputBorderHover, int inputCursor, int cornerRadius, int borderWidth, int shadowOffset,
            int shadowColor, String panelImagePath, String panelImageQuality, int panelImageScale,
            int panelCropX, int panelCropY) {
        BACKDROP = panelBgBottom;
        SHELL = panelBgBottom;
        SHELL_RAISED = panelBgTop;
        SURFACE = panelBgTop;
        SURFACE_HOVER = buttonBgHover;
        SURFACE_PRESSED = buttonBgPressed;
        BORDER = panelBorder;
        BORDER_SUBTLE = buttonBorderNormal;
        ACCENT = titleRight;
        ACCENT_DIM = buttonBgNormal;
        SUCCESS = stateSuccess;
        WARNING = stateWarning;
        TEXT = titleText;
        SUBTLE_TEXT = subText;
        MUTED_TEXT = subText;
        INPUT_SURFACE = inputBg;
        DISABLED_SURFACE = stateDisabled;
        SELECTED_SURFACE = stateSelected;
        SELECTED_TEXT = readableText(TEXT, SELECTED_SURFACE);
        DISABLED_TEXT = readableText(MUTED_TEXT, DISABLED_SURFACE);
        ICON_SURFACE = buttonBgNormal;
        ICON_TEXT = readableText(labelText, ICON_SURFACE);
        TOOLTIP_SURFACE = panelBgTop | 0xFF000000;
        TOOLTIP_TEXT = readableText(TEXT, TOOLTIP_SURFACE);
        TOOLTIP_SUBTLE_TEXT = readableText(SUBTLE_TEXT, TOOLTIP_SURFACE);
        DANGER = stateDanger;
        HOVER_BORDER = inputBorderHover;
        INPUT_CURSOR = inputCursor;
        THEME_CORNER_RADIUS = clamp(cornerRadius, 0, 12);
        THEME_BORDER_WIDTH = clamp(borderWidth, 1, 4);
        THEME_SHADOW_OFFSET = clamp(shadowOffset, 0, 8);
        THEME_SHADOW_COLOR = shadowColor == 0 ? 0xFF000000 : shadowColor;
    }

    /** Draws a translucent scrim above the current screen for modal panels. */
    public static void drawBackdropOverlay(int screenWidth, int screenHeight, int color) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        Gui.drawRect(0, 0, screenWidth, screenHeight, color);
    }

    /** Draws a scrim in a caller-owned coordinate space, such as an embedded tab. */
    public static void drawBackdropOverlay(ModernMainLayout.Rect bounds, int color) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Gui.drawRect(bounds.x, bounds.y, bounds.right(), bounds.bottom(), color);
    }

    public static void drawPanel(int x, int y, int width, int height, int radius, int fill, int border) {
        drawThemedPanel(x, y, width, height, themedFill(fill), themedBorder(border));
    }

    public static void drawSubtlePanel(int x, int y, int width, int height, int radius, int fill, int border) {
        drawThemedPanel(x, y, width, height, themedFill(fill), themedBorder(border));
    }

    private static void drawThemedPanel(int x, int y, int width, int height, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.min(THEME_CORNER_RADIUS, Math.min(width, height) / 2);
        if (THEME_SHADOW_OFFSET > 0 && (THEME_SHADOW_COLOR >>> 24) != 0) {
            drawRoundedRect(x + THEME_SHADOW_OFFSET, y + THEME_SHADOW_OFFSET, width, height, radius,
                    THEME_SHADOW_COLOR);
        }
        int borderWidth = Math.min(THEME_BORDER_WIDTH, Math.max(1, Math.min(width, height) / 2));
        if ((border >>> 24) != 0) {
            drawRoundedRect(x, y, width, height, radius, border);
            int innerWidth = width - borderWidth * 2;
            int innerHeight = height - borderWidth * 2;
            if (innerWidth > 0 && innerHeight > 0) {
                drawRoundedRect(x + borderWidth, y + borderWidth, innerWidth, innerHeight,
                        Math.max(0, radius - borderWidth), fill);
            }
        } else {
            drawRoundedRect(x, y, width, height, radius, fill);
        }
    }

    private static int themedFill(int color) {
        if (color == 0xFF101820) return INPUT_SURFACE;
        if (color == 0xFF111A22) return INPUT_SURFACE;
        if (color == 0xFF141D24 || color == 0xFF141B22 || color == 0xFF151D24
                || color == 0xFF151E26 || color == 0xFF141D25 || color == 0xFF0D1318
                || color == 0xFF10161B) return DISABLED_SURFACE;
        if (color == 0xFF151F28 || color == 0xFF111B23 || color == 0xFF111B24
                || color == 0xFF15222C) return SURFACE;
        // Legacy selected-row fills. Keep old callers theme-aware without
        // requiring every embedded panel to duplicate the selected token.
        if (color == 0xFF2A3540 || color == 0xFF2B3B47 || color == 0xFF2C3D49
                || color == 0xFF293B46 || color == 0xFF293D48 || color == 0xFF273B45
                || color == 0xFF283C48 || color == 0xFF29404D) return SELECTED_SURFACE;
        if (color == 0xFF293A46) return SURFACE_PRESSED;
        // Older primary-button hover variants represented the same semantic
        // hover state as 0xFFFF82A5.
        if (color == 0xFFFF82A5 || color == 0xFFFF86A7 || color == 0xFFFFA4BC
                || color == 0xFFFFB0C4 || color == 0xFFFFABC0) return SURFACE_HOVER;
        if (color == 0xFFD96A52 || color == 0xFFF29A78 || color == 0xFFE69073
                || color == 0xFFD46B57 || color == 0xFF4A3030) return DANGER;
        if (color == 0xFF78E0B0 || color == 0xFF62CBA0) return SUCCESS;
        return color;
    }

    public static int readableText(int preferred, int fill) {
        int background = ModernThemeContrast.composite(themedFill(fill), SHELL_RAISED | 0xFF000000);
        return ModernThemeContrast.readable(themedSemanticColor(preferred), background);
    }

    /** Maps legacy status colors to the active theme's semantic status colors. */
    private static int themedSemanticColor(int color) {
        if (color == 0xFFE06A78 || color == 0xFFFF7777 || color == 0xFFFF8E8E
                || color == 0xFFB64A4A || color == 0xFFF1A48B || color == 0xFFFFA0A0
                || color == 0xFFE69A9A || color == 0xFFFF9A9A || color == 0xFF9A5361) return DANGER;
        if (color == 0xFFF0B55E || color == 0xFFE0A100 || color == 0xFFFFD68A
                || color == 0xFFFFD27A || color == 0xFF9A7B3E) return WARNING;
        return color;
    }

    /**
     * Derives a status surface from the active theme instead of using a fixed
     * success, warning, or error background. The semantic color remains the
     * theme-provided accent while the surface is softened against the current
     * panel surface for readable contrast on both light and dark themes.
     */
    public static int statusSurface(int semanticColor) {
        return lerpRgb(SURFACE, themedSemanticColor(semanticColor), 0.22F);
    }

    private static int themedBorder(int color) {
        if (color == 0xFF617581 || color == 0xFF637886 || color == 0xFF637783
                || color == 0xFF607783 || color == 0xFF586C79 || color == 0xFFFF9DB7
                || color == 0xFFFFABC0 || color == 0xFFFFA4BC || color == 0xFFFFB0C4
                || color == 0xFFFFC0A7 || color == 0xFFFF9CB8) return HOVER_BORDER;
        return color;
    }

    private static void drawRoundedOutline(int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.max(0, Math.min(Math.min(width, height) / 2, radius));
        boolean cullEnabled = disableCullForVector();
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        applyColor(color);
        // Only the one-pixel ring is filled; nothing is painted under the content.
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int corner = 0; corner <= 4; corner++) {
            int c = corner % 4;
            int cx = (c == 0 || c == 3) ? x + r : x + width - r;
            int cy = c < 2 ? y + r : y + height - r;
            int segments = Math.max(4, r / 2);
            for (int i = 0; i <= (corner == 4 ? 0 : segments); i++) {
                double angle = Math.toRadians(180 + c * 90 + i * 90.0 / segments);
                float dx = (float) Math.cos(angle);
                float dy = (float) Math.sin(angle);
                GL11.glVertex2f(cx + dx * r, cy + dy * r);
                GL11.glVertex2f(cx + dx * (r + 1), cy + dy * (r + 1));
            }
        }
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        if (alphaEnabled) {
            GlStateManager.enableAlpha();
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    public static void drawRoundedRect(int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        color = themedSemanticColor(themedFill(color));
        // Vanilla's alpha test discards low-opacity GUI fragments instead of blending them.
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        int safeRadius = Math.max(0, Math.min(Math.min(width, height) / 2, radius));
        if (safeRadius <= 1) {
            Gui.drawRect(x, y, x + width, y + height, color);
            if (alphaEnabled) {
                GlStateManager.enableAlpha();
            }
            return;
        }

        Gui.drawRect(x + safeRadius, y, x + width - safeRadius, y + safeRadius, color);
        Gui.drawRect(x + safeRadius, y + height - safeRadius, x + width - safeRadius, y + height, color);
        Gui.drawRect(x, y + safeRadius, x + width, y + height - safeRadius, color);

        drawQuarterCircle(x + safeRadius, y + safeRadius, safeRadius, 180, 270, color);
        drawQuarterCircle(x + width - safeRadius, y + safeRadius, safeRadius, 270, 360, color);
        drawQuarterCircle(x + width - safeRadius, y + height - safeRadius, safeRadius, 0, 90, color);
        drawQuarterCircle(x + safeRadius, y + height - safeRadius, safeRadius, 90, 180, color);
        if (alphaEnabled) {
            GlStateManager.enableAlpha();
        }
    }

    private static void drawQuarterCircle(int centerX, int centerY, int radius, int fromDegrees, int toDegrees,
            int color) {
        boolean cullEnabled = disableCullForVector();
        applyColor(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        int segments = Math.max(4, radius / 2);
        for (int i = 0; i <= segments; i++) {
            // Walk the fan in the front-facing order used by the in-game GUI projection.
            float degrees = toDegrees - (toDegrees - fromDegrees) * i / (float) segments;
            double radians = Math.toRadians(degrees);
            GL11.glVertex2f(centerX + (float) Math.cos(radians) * radius,
                    centerY + (float) Math.sin(radians) * radius);
        }
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    public static void drawDivider(int x, int y, int width, int color) {
        Gui.drawRect(x, y, x + Math.max(0, width), y + 1, themedBorder(color));
    }

    public static void drawVerticalDivider(int x, int y, int height, int color) {
        Gui.drawRect(x, y, x + 1, y + Math.max(0, height), themedBorder(color));
    }

    public static void drawToggle(int x, int y, int width, int height, boolean enabled, boolean hovered) {
        int safeHeight = Math.max(10, height);
        int safeWidth = Math.max(safeHeight + 8, width);
        int fill = enabled ? ACCENT : DISABLED_SURFACE;
        int border = enabled ? ACCENT : BORDER;
        drawRoundedRect(x - 1, y - 1, safeWidth + 2, safeHeight + 2, safeHeight / 2 + 1, border);
        drawRoundedRect(x, y, safeWidth, safeHeight, safeHeight / 2, fill);
        int knobSize = Math.max(6, safeHeight - 4);
        int knobX = enabled ? x + safeWidth - knobSize - 2 : x + 2;
        drawRoundedRect(knobX, y + 2, knobSize, knobSize, knobSize / 2,
                hovered ? TEXT : SUBTLE_TEXT);
    }

    public static void drawStatusDot(int x, int y, int color) {
        drawRoundedRect(x, y, 7, 7, 4, themedSemanticColor(color));
    }

    public static void drawIcon(Icon icon, int x, int y, int size, int color) {
        if (icon == null || size <= 0) {
            return;
        }
        color = themedSemanticColor(color);
        float scale = size / 16.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        drawNormalizedIcon(icon, color);
        GlStateManager.popMatrix();
    }

    /** Draws an icon around its center with an arbitrary rotation. */
    public static void drawIconRotated(Icon icon, int x, int y, int size, int color, float degrees) {
        if (icon == null || size <= 0) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + size / 2.0F, y + size / 2.0F, 0.0F);
        GlStateManager.rotate(degrees, 0.0F, 0.0F, 1.0F);
        drawIcon(icon, -size / 2, -size / 2, size, color);
        GlStateManager.popMatrix();
    }

    private static void drawNormalizedIcon(Icon icon, int color) {
        switch (icon) {
            case BRAND:
                drawRoundedRect(1, 1, 14, 14, 4, color);
                drawRoundedRect(5, 5, 6, 6, 2, SHELL);
                return;
            case HOME:
                drawLine(2.0F, 7.0F, 8.0F, 2.0F, 1.5F, color);
                drawLine(8.0F, 2.0F, 14.0F, 7.0F, 1.5F, color);
                drawRectOutline(4.0F, 6.5F, 12.0F, 14.0F, 1.4F, color);
                drawRoundedRect(7, 10, 3, 4, 1, color);
                return;
            case SEARCH:
                drawCircleOutline(7, 7, 4, color);
                drawLine(10.0F, 10.0F, 14.0F, 14.0F, 1.6F, color);
                return;
            case COMMAND:
                drawLine(2.0F, 3.0F, 6.0F, 7.0F, 1.5F, color);
                drawLine(6.0F, 7.0F, 2.0F, 11.0F, 1.5F, color);
                drawLine(8.0F, 12.0F, 14.0F, 12.0F, 1.5F, color);
                return;
            case CLOSE:
                drawLine(3.0F, 3.0F, 13.0F, 13.0F, 1.6F, color);
                drawLine(13.0F, 3.0F, 3.0F, 13.0F, 1.6F, color);
                return;
            case MORE:
                drawFilledCircle(3.0F, 8.0F, 1.35F, color);
                drawFilledCircle(8.0F, 8.0F, 1.35F, color);
                drawFilledCircle(13.0F, 8.0F, 1.35F, color);
                return;
            case STOP:
                drawRoundedRect(4, 4, 8, 8, 2, color);
                return;
            case BACKGROUND_STOP:
                drawRectOutline(2.0F, 3.0F, 10.0F, 11.0F, 1.3F, color);
                drawRoundedRect(7, 7, 7, 7, 2, color);
                return;
            case ROUTE:
                drawFilledCircle(3.0F, 3.0F, 2.0F, color);
                drawLine(4.5F, 4.5F, 11.5F, 11.5F, 1.5F, color);
                drawFilledCircle(13.0F, 13.0F, 2.0F, color);
                return;
            case EXTERNAL_WINDOW:
                drawLine(2.0F, 6.0F, 2.0F, 14.0F, 1.4F, color);
                drawLine(2.0F, 14.0F, 10.0F, 14.0F, 1.4F, color);
                drawLine(7.0F, 2.0F, 14.0F, 2.0F, 1.4F, color);
                drawLine(14.0F, 2.0F, 14.0F, 9.0F, 1.4F, color);
                drawLine(6.0F, 10.0F, 14.0F, 2.0F, 1.5F, color);
                drawLine(10.0F, 2.0F, 14.0F, 2.0F, 1.5F, color);
                drawLine(14.0F, 2.0F, 14.0F, 6.0F, 1.5F, color);
                return;
            case SETTINGS:
                drawCircleOutline(8, 8, 5, color);
                drawFilledCircle(8.0F, 8.0F, 2.0F, color);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2.0D * i / 8.0D;
                    drawLine(8.0F + (float) Math.cos(angle) * 5.2F,
                            8.0F + (float) Math.sin(angle) * 5.2F,
                            8.0F + (float) Math.cos(angle) * 7.0F,
                            8.0F + (float) Math.sin(angle) * 7.0F, 1.5F, color);
                }
                return;
            case INFO:
                drawCircleOutline(8, 8, 6, color);
                drawFilledCircle(8.0F, 5.0F, 1.0F, color);
                drawRoundedRect(7, 7, 2, 5, 1, color);
                return;
            case PAUSE:
                drawRoundedRect(4, 3, 3, 10, 1, color);
                drawRoundedRect(9, 3, 3, 10, 1, color);
                return;
            case RUNNING_STATUS:
                drawLine(2.0F, 13.0F, 2.0F, 8.0F, 1.5F, color);
                drawLine(7.0F, 13.0F, 7.0F, 4.0F, 1.5F, color);
                drawLine(12.0F, 13.0F, 12.0F, 2.0F, 1.5F, color);
                drawFilledCircle(2.0F, 13.0F, 1.4F, color);
                drawFilledCircle(7.0F, 13.0F, 1.4F, color);
                drawFilledCircle(12.0F, 13.0F, 1.4F, color);
                return;
            case FOOD:
                drawLine(3.0F, 8.0F, 13.0F, 8.0F, 1.3F, color);
                drawArc(8.0F, 8.0F, 5.0F, 0, 180, 1.5F, color);
                drawLine(5.0F, 14.0F, 11.0F, 14.0F, 1.4F, color);
                drawLine(12.0F, 2.0F, 12.0F, 7.0F, 1.2F, color);
                return;
            case FISHING:
                drawDiamond(7.0F, 8.0F, 5.0F, 3.2F, 1.3F, color);
                drawLine(12.0F, 8.0F, 15.0F, 5.0F, 1.3F, color);
                drawLine(12.0F, 8.0F, 15.0F, 11.0F, 1.3F, color);
                drawFilledCircle(4.5F, 7.2F, 0.7F, color);
                return;
            case MOUSE:
                drawRoundedRect(4, 1, 8, 14, 4, color);
                drawRoundedRect(7, 3, 2, 4, 1, SHELL_RAISED);
                return;
            case FLIGHT:
                drawLine(2.0F, 9.0F, 8.0F, 5.0F, 1.4F, color);
                drawLine(8.0F, 5.0F, 14.0F, 3.0F, 1.4F, color);
                drawLine(8.0F, 5.0F, 12.0F, 8.0F, 1.4F, color);
                drawLine(8.0F, 5.0F, 9.0F, 13.0F, 1.4F, color);
                drawLine(6.0F, 10.0F, 9.0F, 13.0F, 1.4F, color);
                return;
            case FOLLOW:
                drawFilledCircle(5.0F, 5.0F, 2.2F, color);
                drawArc(5.0F, 12.0F, 4.0F, 200, 340, 1.6F, color);
                drawLine(9.0F, 6.0F, 14.0F, 6.0F, 1.4F, color);
                drawLine(12.0F, 4.0F, 14.0F, 6.0F, 1.4F, color);
                drawLine(14.0F, 6.0F, 12.0F, 8.0F, 1.4F, color);
                return;
            case COMBAT:
                drawLine(3.0F, 2.0F, 13.0F, 12.0F, 1.5F, color);
                drawLine(13.0F, 2.0F, 3.0F, 12.0F, 1.5F, color);
                drawLine(2.0F, 11.0F, 5.0F, 14.0F, 1.8F, color);
                drawLine(14.0F, 11.0F, 11.0F, 14.0F, 1.8F, color);
                return;
            case CONDITIONS:
                drawFilledCircle(3.0F, 4.0F, 1.7F, color);
                drawFilledCircle(3.0F, 12.0F, 1.7F, color);
                drawFilledCircle(13.0F, 8.0F, 1.7F, color);
                drawLine(4.5F, 4.5F, 11.5F, 7.5F, 1.3F, color);
                drawLine(4.5F, 11.5F, 11.5F, 8.5F, 1.3F, color);
                return;
            case ESCAPE:
                drawRectOutline(2.0F, 2.0F, 11.0F, 14.0F, 1.4F, color);
                drawLine(6.0F, 8.0F, 15.0F, 8.0F, 1.5F, color);
                drawLine(12.0F, 5.0F, 15.0F, 8.0F, 1.5F, color);
                drawLine(15.0F, 8.0F, 12.0F, 11.0F, 1.5F, color);
                return;
            case KEYBOARD:
                drawRectOutline(1.5F, 3.0F, 14.5F, 13.0F, 1.3F, color);
                for (int row = 0; row < 2; row++) {
                    for (int column = 0; column < 4; column++) {
                        drawFilledCircle(4.0F + column * 2.7F, 6.0F + row * 2.5F, 0.65F, color);
                    }
                }
                drawLine(5.0F, 11.0F, 11.0F, 11.0F, 1.2F, color);
                return;
            case PROFILE:
                drawFilledCircle(8.0F, 5.0F, 3.0F, color);
                drawArc(8.0F, 15.0F, 6.0F, 205, 335, 2.2F, color);
                return;
            case CHAT:
                drawRectOutline(2.0F, 2.0F, 14.0F, 11.0F, 1.4F, color);
                drawLine(5.0F, 11.0F, 4.0F, 15.0F, 1.5F, color);
                drawLine(4.0F, 15.0F, 8.0F, 11.0F, 1.5F, color);
                return;
            case PICKUP:
                drawLine(8.0F, 1.5F, 8.0F, 10.0F, 1.6F, color);
                drawLine(4.5F, 7.0F, 8.0F, 10.5F, 1.6F, color);
                drawLine(8.0F, 10.5F, 11.5F, 7.0F, 1.6F, color);
                drawLine(2.0F, 13.0F, 14.0F, 13.0F, 1.5F, color);
                return;
            case AUTO_USE:
                drawRoundedRect(5, 2, 6, 12, 2, color);
                drawRoundedRect(7, 0, 2, 3, 1, color);
                drawLine(6.0F, 8.0F, 10.0F, 8.0F, 1.2F, SHELL_RAISED);
                return;
            case BLOCKS:
                drawRectOutline(2.0F, 2.0F, 10.0F, 10.0F, 1.3F, color);
                drawRectOutline(6.0F, 6.0F, 14.0F, 14.0F, 1.3F, color);
                return;
            case STORAGE:
                drawRectOutline(2.0F, 5.0F, 14.0F, 14.0F, 1.4F, color);
                drawLine(1.0F, 5.0F, 15.0F, 5.0F, 2.0F, color);
                drawLine(6.0F, 9.0F, 10.0F, 9.0F, 1.3F, color);
                return;
            case LOOP:
                drawArc(8.0F, 8.0F, 5.5F, 35, 205, 1.5F, color);
                drawArc(8.0F, 8.0F, 5.5F, 215, 385, 1.5F, color);
                drawLine(2.5F, 5.0F, 2.5F, 1.8F, 1.4F, color);
                drawLine(2.5F, 1.8F, 5.5F, 2.5F, 1.4F, color);
                drawLine(13.5F, 11.0F, 13.5F, 14.2F, 1.4F, color);
                drawLine(13.5F, 14.2F, 10.5F, 13.5F, 1.4F, color);
                return;
            case DEBUG:
                drawRoundedRect(4, 4, 8, 9, 3, color);
                drawLine(6.0F, 1.0F, 7.0F, 4.0F, 1.2F, color);
                drawLine(10.0F, 1.0F, 9.0F, 4.0F, 1.2F, color);
                drawLine(1.0F, 7.0F, 4.0F, 7.0F, 1.2F, color);
                drawLine(12.0F, 7.0F, 15.0F, 7.0F, 1.2F, color);
                drawLine(1.0F, 11.0F, 4.0F, 10.0F, 1.2F, color);
                drawLine(12.0F, 10.0F, 15.0F, 11.0F, 1.2F, color);
                return;
            case DISPLAY:
                drawRectOutline(1.5F, 2.0F, 14.5F, 11.5F, 1.3F, color);
                drawLine(8.0F, 11.5F, 8.0F, 14.0F, 1.4F, color);
                drawLine(5.0F, 14.0F, 11.0F, 14.0F, 1.4F, color);
                return;
            case RELOAD:
                drawArc(8.0F, 8.0F, 5.5F, 35, 320, 1.6F, color);
                drawLine(12.0F, 2.5F, 14.0F, 5.5F, 1.5F, color);
                drawLine(14.0F, 5.5F, 10.5F, 5.5F, 1.5F, color);
                return;
            case INVENTORY:
                drawRectOutline(2.0F, 3.0F, 14.0F, 14.0F, 1.3F, color);
                drawLine(5.0F, 3.0F, 6.5F, 1.0F, 1.3F, color);
                drawLine(11.0F, 3.0F, 9.5F, 1.0F, 1.3F, color);
                drawRectOutline(5.0F, 7.0F, 11.0F, 11.0F, 1.1F, color);
                return;
            case PACKET:
                drawDiamond(8.0F, 4.0F, 5.5F, 2.5F, 1.2F, color);
                drawLine(2.5F, 6.0F, 8.0F, 9.0F, 1.2F, color);
                drawLine(13.5F, 6.0F, 8.0F, 9.0F, 1.2F, color);
                drawLine(3.0F, 9.0F, 8.0F, 14.0F, 1.2F, color);
                drawLine(13.0F, 9.0F, 8.0F, 14.0F, 1.2F, color);
                return;
            case INSPECTOR:
                drawCircleOutline(8, 8, 6, color);
                drawCircleOutline(8, 8, 3, color);
                drawFilledCircle(8.0F, 8.0F, 1.2F, color);
                return;
            case PERFORMANCE:
                drawArc(8.0F, 10.0F, 6.0F, 180, 360, 1.5F, color);
                drawLine(8.0F, 10.0F, 12.0F, 6.0F, 1.5F, color);
                drawFilledCircle(8.0F, 10.0F, 1.4F, color);
                drawLine(3.0F, 13.0F, 13.0F, 13.0F, 1.3F, color);
                return;
            case TERRAIN:
                drawLine(1.0F, 13.0F, 6.0F, 5.0F, 1.5F, color);
                drawLine(6.0F, 5.0F, 9.0F, 10.0F, 1.5F, color);
                drawLine(9.0F, 10.0F, 12.0F, 7.0F, 1.5F, color);
                drawLine(12.0F, 7.0F, 15.0F, 13.0F, 1.5F, color);
                drawLine(1.0F, 13.0F, 15.0F, 13.0F, 1.5F, color);
                return;
            case MEMORY:
                drawRectOutline(4.0F, 4.0F, 12.0F, 12.0F, 1.4F, color);
                for (int i = 0; i < 4; i++) {
                    float p = 5.0F + i * 2.0F;
                    drawLine(p, 1.0F, p, 4.0F, 1.0F, color);
                    drawLine(p, 12.0F, p, 15.0F, 1.0F, color);
                    drawLine(1.0F, p, 4.0F, p, 1.0F, color);
                    drawLine(12.0F, p, 15.0F, p, 1.0F, color);
                }
                drawRoundedRect(6, 6, 4, 4, 1, color);
                return;
            case MOVE_PANEL:
                drawLine(2, 8, 14, 8, 1.3F, color);
                drawLine(8, 2, 8, 14, 1.3F, color);
                drawLine(2, 8, 5, 5, 1.3F, color);
                drawLine(2, 8, 5, 11, 1.3F, color);
                drawLine(14, 8, 11, 5, 1.3F, color);
                drawLine(14, 8, 11, 11, 1.3F, color);
                drawLine(8, 2, 5, 5, 1.3F, color);
                drawLine(8, 2, 11, 5, 1.3F, color);
                drawLine(8, 14, 5, 11, 1.3F, color);
                drawLine(8, 14, 11, 11, 1.3F, color);
                return;
            case MOVEMENT:
                drawLine(2.0F, 11.0F, 8.0F, 5.0F, 1.6F, color);
                drawLine(8.0F, 5.0F, 14.0F, 11.0F, 1.6F, color);
                drawLine(8.0F, 5.0F, 8.0F, 14.0F, 1.6F, color);
                return;
            case RENDER:
                drawArc(8.0F, 8.0F, 7.0F, 205, 335, 1.4F, color);
                drawArc(8.0F, 8.0F, 7.0F, 25, 155, 1.4F, color);
                drawCircleOutline(8, 8, 2, color);
                return;
            case WORLD:
                drawCircleOutline(8, 8, 6, color);
                drawArc(8.0F, 8.0F, 3.0F, 90, 270, 1.1F, color);
                drawArc(8.0F, 8.0F, 3.0F, 270, 450, 1.1F, color);
                drawLine(2.0F, 8.0F, 14.0F, 8.0F, 1.1F, color);
                return;
            case MISC:
                drawLine(8.0F, 1.0F, 8.0F, 15.0F, 1.2F, color);
                drawLine(1.0F, 8.0F, 15.0F, 8.0F, 1.2F, color);
                drawLine(3.0F, 3.0F, 13.0F, 13.0F, 1.2F, color);
                drawLine(13.0F, 3.0F, 3.0F, 13.0F, 1.2F, color);
                drawFilledCircle(8.0F, 8.0F, 2.0F, color);
                return;
            case SORT:
                drawLine(3.0F, 3.0F, 13.0F, 3.0F, 1.5F, color);
                drawLine(3.0F, 8.0F, 10.0F, 8.0F, 1.5F, color);
                drawLine(3.0F, 13.0F, 7.0F, 13.0F, 1.5F, color);
                return;
            case GROUP:
                drawRectOutline(2.0F, 2.0F, 7.0F, 7.0F, 1.2F, color);
                drawRectOutline(9.0F, 2.0F, 14.0F, 7.0F, 1.2F, color);
                drawRectOutline(2.0F, 9.0F, 14.0F, 14.0F, 1.2F, color);
                return;
            case SIZE:
                drawRectOutline(3.0F, 3.0F, 13.0F, 13.0F, 1.3F, color);
                drawLine(8.0F, 5.0F, 8.0F, 11.0F, 1.3F, color);
                drawLine(5.0F, 8.0F, 11.0F, 8.0F, 1.3F, color);
                return;
            case COLUMNS:
                drawRoundedRect(2, 2, 3, 12, 1, color);
                drawRoundedRect(7, 2, 3, 12, 1, color);
                drawRoundedRect(12, 2, 3, 12, 1, color);
                return;
            case FLOW:
                drawLine(2.0F, 4.0F, 12.0F, 4.0F, 1.4F, color);
                drawLine(10.0F, 2.0F, 12.0F, 4.0F, 1.4F, color);
                drawLine(12.0F, 4.0F, 10.0F, 6.0F, 1.4F, color);
                drawLine(14.0F, 12.0F, 4.0F, 12.0F, 1.4F, color);
                drawLine(6.0F, 10.0F, 4.0F, 12.0F, 1.4F, color);
                drawLine(4.0F, 12.0F, 6.0F, 14.0F, 1.4F, color);
                return;
            case PIN:
                drawCircleOutline(8, 5, 3, color);
                drawLine(5.0F, 8.4F, 11.0F, 8.4F, 1.3F, color);
                drawLine(8.0F, 8.4F, 8.0F, 14.0F, 1.4F, color);
                return;
            case PINNED:
                drawFilledCircle(8.0F, 5.0F, 3.2F, color);
                drawLine(5.0F, 8.4F, 11.0F, 8.4F, 1.5F, color);
                drawLine(8.0F, 8.4F, 8.0F, 14.0F, 1.6F, color);
                drawFilledCircle(8.0F, 14.0F, 0.9F, color);
                return;
            case GENERIC:
            default:
                drawDiamond(8.0F, 8.0F, 5.0F, 5.0F, 1.4F, color);
                drawFilledCircle(8.0F, 8.0F, 1.5F, color);
        }
    }

    public static void drawSearchIcon(int x, int y, int color) {
        drawIcon(Icon.SEARCH, x - 1, y - 1, 14, color);
    }

    public static void drawCommandIcon(int x, int y, int color) {
        drawIcon(Icon.COMMAND, x - 1, y - 1, 14, color);
    }

    public static void drawCloseIcon(int x, int y, int color) {
        drawIcon(Icon.CLOSE, x - 2, y - 2, 13, color);
    }

    public static void drawMoreIcon(int x, int y, int color) {
        drawIcon(Icon.MORE, x, y, 13, color);
    }

    public static void drawChevron(int x, int y, boolean right, int color) {
        if (right) {
            drawLine(x, y, x + 4, y + 4, 1.5F, color);
            drawLine(x + 4, y + 4, x, y + 8, 1.5F, color);
        } else {
            drawLine(x + 4, y, x, y + 4, 1.5F, color);
            drawLine(x, y + 4, x + 4, y + 8, 1.5F, color);
        }
    }

    public static void drawDropdownChevron(int x, int y, int color) {
        drawLine(x, y, x + 4, y + 4, 1.5F, color);
        drawLine(x + 4, y + 4, x + 8, y, 1.5F, color);
    }

    /** Draws the wide up/down chevron used to collapse a tool strip. */
    public static void drawCollapseChevron(int centerX, int centerY, boolean up, int color) {
        float halfWidth = 7.0F;
        float halfHeight = 4.0F;
        if (up) {
            drawLine(centerX - halfWidth, centerY + halfHeight, centerX, centerY - halfHeight, 2.2F, color);
            drawLine(centerX, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight, 2.2F, color);
        } else {
            drawLine(centerX - halfWidth, centerY - halfHeight, centerX, centerY + halfHeight, 2.2F, color);
            drawLine(centerX, centerY + halfHeight, centerX + halfWidth, centerY - halfHeight, 2.2F, color);
        }
    }

    public static void drawStopIcon(int x, int y, int color) {
        drawIcon(Icon.STOP, x - 2, y - 2, 12, color);
    }

    public static void drawPauseIcon(int x, int y, int color) {
        drawIcon(Icon.PAUSE, x - 2, y - 2, 14, color);
    }

    public static void drawBackgroundStopIcon(int x, int y, int color) {
        drawIcon(Icon.BACKGROUND_STOP, x, y, 13, color);
    }

    public static void drawRouteIcon(int x, int y, int color) {
        drawIcon(Icon.ROUTE, x, y, 13, color);
    }

    /** Draws the native-window detach/dock glyph used by the main header. */
    public static void drawExternalWindowIcon(int x, int y, int color) {
        drawIcon(Icon.EXTERNAL_WINDOW, x, y, 13, color);
    }

    public static void drawSettingsIcon(int x, int y, int color) {
        drawIcon(Icon.SETTINGS, x, y, 12, color);
    }

    public static void drawSettingsIconRotated(int x, int y, int color, float degrees) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 6.0F, y + 6.0F, 0.0F);
        GlStateManager.rotate(degrees, 0.0F, 0.0F, 1.0F);
        drawSettingsIcon(-6, -6, color);
        GlStateManager.popMatrix();
    }

    public static void drawInfoIcon(int x, int y, int color) {
        ModernTooltipSupport.registerInfoIcon(Minecraft.getMinecraft().currentScreen, x, y, 11, 11);
        drawIcon(Icon.INFO, x, y, 11, color);
    }

    public static void drawPinIcon(int x, int y, boolean pinned, int color) {
        drawIcon(pinned ? Icon.PINNED : Icon.PIN, x, y, 12, color);
    }

    /** Font-independent check mark for compact selectable cards. */
    public static void drawCheckMark(int x, int y, int size, int color) {
        float scale = Math.max(0.65F, size / 12.0F);
        drawLine(x + 1.5F * scale, y + 6.0F * scale, x + 4.5F * scale, y + 9.0F * scale,
                Math.max(1.25F, 1.55F * scale), color);
        drawLine(x + 4.2F * scale, y + 9.0F * scale, x + 10.8F * scale, y + 2.3F * scale,
                Math.max(1.25F, 1.55F * scale), color);
    }

    /** Draws the four-corner glyph used by cards that can fill the workspace. */
    public static void drawExpandIcon(int x, int y, boolean expanded, int color) {
        if (expanded) {
            drawLine(x + 2.0F, y + 5.0F, x + 5.0F, y + 2.0F, 1.35F, color);
            drawLine(x + 11.0F, y + 2.0F, x + 14.0F, y + 5.0F, 1.35F, color);
            drawLine(x + 2.0F, y + 11.0F, x + 5.0F, y + 14.0F, 1.35F, color);
            drawLine(x + 11.0F, y + 14.0F, x + 14.0F, y + 11.0F, 1.35F, color);
        } else {
            drawLine(x + 2.0F, y + 2.0F, x + 5.0F, y + 5.0F, 1.35F, color);
            drawLine(x + 11.0F, y + 5.0F, x + 14.0F, y + 2.0F, 1.35F, color);
            drawLine(x + 2.0F, y + 14.0F, x + 5.0F, y + 11.0F, 1.35F, color);
            drawLine(x + 11.0F, y + 11.0F, x + 14.0F, y + 14.0F, 1.35F, color);
        }
    }

    private static void drawCircleOutline(int centerX, int centerY, int radius, int color) {
        drawRing(centerX, centerY, radius, Math.max(0.9F, radius * 0.22F), color);
    }

    private static void drawLine(float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F) {
            drawFilledCircle(x1, y1, width * 0.5F, color);
            return;
        }
        float half = Math.max(0.35F, width * 0.5F);
        float nx = -dy / length * half;
        float ny = dx / length * half;
        boolean cullEnabled = disableCullForVector();
        applyColor(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1 + nx, y1 + ny);
        GL11.glVertex2f(x2 + nx, y2 + ny);
        GL11.glVertex2f(x2 - nx, y2 - ny);
        GL11.glVertex2f(x1 - nx, y1 - ny);
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    private static void drawFilledCircle(float centerX, float centerY, float radius, int color) {
        boolean cullEnabled = disableCullForVector();
        applyColor(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        int segments = Math.max(12, (int) Math.ceil(radius * 6.0F));
        for (int i = 0; i <= segments; i++) {
            // The game GUI can leave face culling enabled; reverse winding keeps the fan visible there.
            double angle = -Math.PI * 2.0D * i / segments;
            GL11.glVertex2f(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    private static void drawRing(float centerX, float centerY, float radius, float width, int color) {
        float inner = Math.max(0.0F, radius - Math.max(0.5F, width));
        boolean cullEnabled = disableCullForVector();
        applyColor(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        int segments = Math.max(16, (int) Math.ceil(radius * 7.0F));
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            GL11.glVertex2f(centerX + cosine * radius, centerY + sine * radius);
            GL11.glVertex2f(centerX + cosine * inner, centerY + sine * inner);
        }
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    private static void drawArc(float centerX, float centerY, float radius, int fromDegrees, int toDegrees,
            float width, int color) {
        int segments = Math.max(6, Math.abs(toDegrees - fromDegrees) / 15);
        float halfWidth = Math.max(0.35F, width * 0.5F);
        float outer = radius + halfWidth;
        float inner = Math.max(0.0F, radius - halfWidth);
        boolean cullEnabled = disableCullForVector();
        applyColor(color);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float degrees = fromDegrees + (toDegrees - fromDegrees) * i / (float) segments;
            double radians = Math.toRadians(degrees);
            float cosine = (float) Math.cos(radians);
            float sine = (float) Math.sin(radians);
            GL11.glVertex2f(centerX + cosine * outer, centerY + sine * outer);
            GL11.glVertex2f(centerX + cosine * inner, centerY + sine * inner);
        }
        GL11.glEnd();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        restoreCullAfterVector(cullEnabled);
    }

    private static boolean disableCullForVector() {
        boolean enabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (enabled) {
            GlStateManager.disableCull();
        }
        return enabled;
    }

    private static void restoreCullAfterVector(boolean enabled) {
        if (enabled) {
            GlStateManager.enableCull();
        }
    }

    private static void drawRectOutline(float left, float top, float right, float bottom, float width, int color) {
        drawLine(left, top, right, top, width, color);
        drawLine(right, top, right, bottom, width, color);
        drawLine(right, bottom, left, bottom, width, color);
        drawLine(left, bottom, left, top, width, color);
    }

    private static void drawDiamond(float centerX, float centerY, float radiusX, float radiusY, float width,
            int color) {
        drawLine(centerX, centerY - radiusY, centerX + radiusX, centerY, width, color);
        drawLine(centerX + radiusX, centerY, centerX, centerY + radiusY, width, color);
        drawLine(centerX, centerY + radiusY, centerX - radiusX, centerY, width, color);
        drawLine(centerX - radiusX, centerY, centerX, centerY - radiusY, width, color);
    }

    public static String ellipsize(FontRenderer fontRenderer, String text, int maxWidth) {
        if (fontRenderer == null || maxWidth <= 0) {
            return "";
        }
        String safeText = TextFormatting.getTextWithoutFormattingCodes(text == null ? "" : text);
        if (fontRenderer.getStringWidth(safeText) <= maxWidth) {
            return safeText;
        }
        int available = Math.max(0, maxWidth - fontRenderer.getStringWidth("..."));
        if (available <= 0) {
            return fontRenderer.trimStringToWidth(safeText, maxWidth);
        }
        return fontRenderer.trimStringToWidth(safeText, available) + "...";
    }

    public static void drawText(FontRenderer fontRenderer, String text, int x, int y, int color, int maxWidth) {
        if (fontRenderer == null) {
            return;
        }
        // Theme profiles may use a light input/surface background. Resolve the
        // requested semantic color against a themed surface instead of assuming
        // the historical dark palette.
        fontRenderer.drawString(ellipsize(fontRenderer, ModernFormI18n.tr(text), maxWidth), x, y,
                readableText(color, 0xFF151F28));
    }

    /**
     * Recalculates the horizontal text viewport after a field receives its
     * final layout width. Fields are often created at width 1 and populated
     * before the responsive layout runs, which otherwise leaves their text
     * scrolled out of view until the user focuses them.
     */
    public static void reflowTextField(GuiTextField field) {
        if (field == null || !field.getVisible()) {
            return;
        }
        int cursorPosition = field.getCursorPosition();
        int selectionEnd = field.getSelectionEnd();
        if (cursorPosition == selectionEnd) {
            field.setCursorPosition(cursorPosition);
        } else {
            // setSelectionPos adjusts lineScrollOffset while preserving the
            // cursor position and the existing selection range.
            field.setSelectionPos(selectionEnd);
        }
    }

    /** Draws a single-line field with text vertically centered and sized to the box. */
    public static void drawTextField(GuiTextField field) {
        if (field == null || !field.getVisible() || field.width <= 0 || field.height <= 0) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            field.drawTextBox();
            return;
        }
        int padX = 5;
        int fontHeight = Math.max(1, font.FONT_HEIGHT);
        float scale = textFieldScale(field.height, fontHeight);
        int maxWidth = Math.max(1, field.width - padX * 2);
        int logicalWidth = Math.max(1, Math.round(maxWidth / scale));
        String text = field.getText() == null ? "" : field.getText();
        field.setTextColor(readableText(TEXT, 0xFF101820));
        field.setDisabledTextColour(readableText(MUTED_TEXT, 0xFF101820));
        int cursor = Math.max(0, Math.min(field.getCursorPosition(), text.length()));
        int selection = Math.max(0, Math.min(field.getSelectionEnd(), text.length()));
        int scroll = 0;
        while (scroll < cursor && font.getStringWidth(text.substring(scroll, cursor)) > logicalWidth) {
            scroll++;
        }
        String visible = font.trimStringToWidth(text.substring(scroll), logicalWidth);
        int textX = field.x + padX;
        int textY = field.y + Math.round((field.height - fontHeight * scale) / 2.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(textX, textY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        int selStart = Math.min(cursor, selection) - scroll;
        int selEnd = Math.max(cursor, selection) - scroll;
        if (field.isFocused() && selStart != selEnd) {
            selStart = Math.max(0, Math.min(visible.length(), selStart));
            selEnd = Math.max(0, Math.min(visible.length(), selEnd));
            int x1 = font.getStringWidth(visible.substring(0, selStart));
            int x2 = font.getStringWidth(visible.substring(0, selEnd));
            Gui.drawRect(x1, -1, x2, fontHeight,
                    (SELECTED_SURFACE & 0x00FFFFFF) | 0x99000000);
        }
        font.drawString(visible, 0, 0, TEXT);
        if (field.isFocused() && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            int caret = Math.max(0, Math.min(visible.length(), cursor - scroll));
            int cx = font.getStringWidth(visible.substring(0, caret));
            Gui.drawRect(cx, -1, cx + 1, fontHeight, INPUT_CURSOR);
        }
        GlStateManager.popMatrix();
    }

    public static void moveTextFieldCursorTo(GuiTextField field, int mouseX) {
        if (field == null) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            field.mouseClicked(mouseX, field.y, 0);
            return;
        }
        float scale = textFieldScale(field.height, Math.max(1, font.FONT_HEIGHT));
        int rel = Math.round((mouseX - field.x - 5) / scale);
        String text = field.getText() == null ? "" : field.getText();
        int pos = font.trimStringToWidth(text, Math.max(0, rel)).length();
        field.setCursorPosition(pos);
        field.setSelectionPos(pos);
    }

    private static float textFieldScale(int height, int fontHeight) {
        return Math.min(2.0F, Math.max(1.0F, (Math.max(1, height) - 6) / (float) Math.max(1, fontHeight)));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Restricts numeric fields before vanilla clipboard input can insert text. */
    public static boolean typeNumericField(GuiTextField field, char typedChar, int keyCode, boolean decimal) {
        if (field == null || !field.isFocused()) {
            return false;
        }
        if (!isNumericCharacter(typedChar, decimal) && !isTextControlKey(typedChar, keyCode)) {
            return true;
        }
        boolean handled = field.textboxKeyTyped(typedChar, keyCode);
        if (handled) {
            sanitizeNumericField(field, decimal);
        }
        return true;
    }

    private static boolean isTextControlKey(char typedChar, int keyCode) {
        if (typedChar == 0 || typedChar == 8 || typedChar == 127) {
            return true;
        }
        if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
                || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
                || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END) {
            return true;
        }
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        return control && (keyCode == Keyboard.KEY_A || keyCode == Keyboard.KEY_C
                || keyCode == Keyboard.KEY_V || keyCode == Keyboard.KEY_X);
    }

    private static boolean isNumericCharacter(char value, boolean decimal) {
        return value >= '0' && value <= '9' || value == '+' || value == '-'
                || decimal && (value == '.' || value == ',');
    }

    private static void sanitizeNumericField(GuiTextField field, boolean decimal) {
        String original = field.getText() == null ? "" : field.getText();
        int cursor = Math.max(0, Math.min(field.getCursorPosition(), original.length()));
        StringBuilder filtered = new StringBuilder(original.length());
        int filteredCursor = 0;
        boolean signAllowed = true;
        boolean decimalAllowed = decimal;
        for (int i = 0; i < original.length(); i++) {
            char value = original.charAt(i);
            boolean keep = value >= '0' && value <= '9';
            if ((value == '+' || value == '-') && signAllowed) {
                keep = true;
                signAllowed = false;
            } else if (decimal && (value == '.' || value == ',') && decimalAllowed && !signAllowed) {
                keep = true;
                decimalAllowed = false;
            } else if (!keep) {
                keep = false;
            }
            if (keep) {
                filtered.append(value == ',' ? '.' : value);
                if (i < cursor) {
                    filteredCursor++;
                }
                if (value >= '0' && value <= '9') {
                    signAllowed = false;
                }
            }
        }
        String result = filtered.toString();
        if (!result.equals(original)) {
            field.setText(result);
            field.setCursorPosition(Math.min(filteredCursor, result.length()));
            field.setSelectionPos(Math.min(filteredCursor, result.length()));
        }
    }

    public static void beginClip(ModernMainLayout.Rect rect) {
        ClipState previous = ClipState.capture();
        CLIP_STATES.get().push(previous);
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            return;
        }
        ClipTransform transform = currentClipTransform();
        int x;
        int y;
        int width;
        int height;
        if (DetachedSwingWindowManager.isRenderingNativeFrame()) {
            double scaleX = DetachedSwingWindowManager.getNativeRenderPixelWidth()
                    / (double) Math.max(1, DetachedSwingWindowManager.getNativeRenderLogicalWidth());
            double scaleY = DetachedSwingWindowManager.getNativeRenderPixelHeight()
                    / (double) Math.max(1, DetachedSwingWindowManager.getNativeRenderLogicalHeight());
            double originX = transform == null ? 0.0D : transform.originX;
            double originY = transform == null ? 0.0D : transform.originY;
            double transformScale = transform == null ? 1.0D : transform.scale;
            double left = (originX + rect.x * transformScale) * scaleX;
            double right = (originX + (rect.x + rect.width) * transformScale) * scaleX;
            double top = (originY + rect.y * transformScale) * scaleY;
            double bottom = (originY + (rect.y + rect.height) * transformScale) * scaleY;
            x = (int) Math.floor(left);
            y = (int) Math.floor(DetachedSwingWindowManager.getNativeRenderPixelHeight() - bottom);
            width = Math.max(0, (int) Math.ceil(right) - x);
            height = Math.max(0,
                    (int) Math.ceil(DetachedSwingWindowManager.getNativeRenderPixelHeight() - top) - y);
        } else {
            Minecraft minecraft = Minecraft.getMinecraft();
            ScaledResolution resolution = new ScaledResolution(minecraft);
            int factor = resolution.getScaleFactor();
            int screenBottom = resolution.getScaledHeight();
            if (transform == null) {
                x = rect.x * factor;
                y = (screenBottom - rect.y - rect.height) * factor;
                width = rect.width * factor;
                height = rect.height * factor;
            } else {
                // Use floor/ceil at the edges so fractional embedded scales do not
                // leave a one-pixel strip of text clipped during a divider drag.
                double left = (transform.originX + rect.x * transform.scale) * factor;
                double right = (transform.originX + (rect.x + rect.width) * transform.scale) * factor;
                double top = (transform.originY + rect.y * transform.scale) * factor;
                double bottom = (transform.originY + (rect.y + rect.height) * transform.scale) * factor;
                x = (int) Math.floor(left);
                y = (int) Math.floor((screenBottom * factor) - bottom);
                width = Math.max(0, (int) Math.ceil(right) - x);
                height = Math.max(0, (int) Math.ceil((screenBottom * factor) - top) - y);
            }
        }
        if (previous.enabled) {
            int right = Math.min(x + width, previous.x + previous.width);
            int top = Math.min(y + height, previous.y + previous.height);
            x = Math.max(x, previous.x);
            y = Math.max(y, previous.y);
            width = Math.max(0, right - x);
            height = Math.max(0, top - y);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    public static void endClip() {
        Deque<ClipState> states = CLIP_STATES.get();
        if (states.isEmpty()) {
            return;
        }
        states.pop().restore();
    }

    /** Begins mapping logical clip rectangles into the current host canvas. */
    public static void pushClipTransform(int originX, int originY, float scale) {
        CLIP_TRANSFORMS.get().push(new ClipTransform(originX, originY,
                Float.isNaN(scale) || Float.isInfinite(scale) ? 1.0D : Math.max(0.0001D, scale)));
    }

    /** Ends the most recent logical-to-host clip mapping. */
    public static void popClipTransform() {
        Deque<ClipTransform> transforms = CLIP_TRANSFORMS.get();
        if (!transforms.isEmpty()) {
            transforms.pop();
        }
    }

    private static ClipTransform currentClipTransform() {
        Deque<ClipTransform> transforms = CLIP_TRANSFORMS.get();
        if (transforms.isEmpty()) {
            return null;
        }

        // Each transform is applied inside the transform that was pushed
        // before it. Scissor rectangles must therefore use the same composed
        // origin and scale as the OpenGL model-view matrix.
        ClipTransform composed = null;
        java.util.Iterator<ClipTransform> iterator = transforms.descendingIterator();
        while (iterator.hasNext()) {
            ClipTransform next = iterator.next();
            if (composed == null) {
                composed = next;
            } else {
                composed = new ClipTransform(
                        composed.originX + composed.scale * next.originX,
                        composed.originY + composed.scale * next.originY,
                        composed.scale * next.scale);
            }
        }
        return composed;
    }

    private static final class ClipState {
        private final boolean enabled;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private ClipState(boolean enabled, int x, int y, int width, int height) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private static ClipState capture() {
            // LWJGL 2 validates glGetInteger buffers against its 16-value maximum.
            IntBuffer box = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
            return new ClipState(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), box.get(0), box.get(1), box.get(2), box.get(3));
        }

        private void restore() {
            if (!enabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                return;
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, width, height);
        }
    }

    private static final class ClipTransform {
        private final double originX;
        private final double originY;
        private final double scale;

        private ClipTransform(int originX, int originY, double scale) {
            this.originX = originX;
            this.originY = originY;
            this.scale = scale;
        }

        private ClipTransform(double originX, double originY, double scale) {
            this.originX = originX;
            this.originY = originY;
            this.scale = scale;
        }
    }

    public static float runningPulse() {
        double phase = (System.currentTimeMillis() % 2000L) / 2000.0D;
        return (float) (0.5D + 0.5D * Math.sin(phase * Math.PI * 2.0D));
    }

    public static int runningAccent(boolean background, float pulse) {
        float amount = 0.42F + 0.58F * clamp01(pulse);
        int red = background ? Math.round(118 + 137 * amount) : Math.round(42 + 48 * amount);
        int green = background ? Math.round(38 + 42 * amount) : Math.round(126 + 114 * amount);
        int blue = background ? Math.round(38 + 32 * amount) : Math.round(58 + 62 * amount);
        return rgb(red, green, blue);
    }

    public static int runningFill(boolean background, float pulse) {
        return background
                ? lerpRgb(0xFF241416, 0xFF3C1F24, 0.28F + 0.72F * clamp01(pulse))
                : lerpRgb(0xFF14241C, 0xFF1F3C2C, 0.28F + 0.72F * clamp01(pulse));
    }

    public static int runningBadge(boolean background, float pulse) {
        return background
                ? lerpRgb(0xFF3A1C20, 0xFF6C2C32, 0.28F + 0.72F * clamp01(pulse))
                : lerpRgb(0xFF1C3A2A, 0xFF2C6C4A, 0.28F + 0.72F * clamp01(pulse));
    }

    public static int lerpRgb(int from, int to, float amount) {
        float t = clamp01(amount);
        int alpha = Math.round(channel(from, 24) + (channel(to, 24) - channel(from, 24)) * t);
        int red = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * t);
        int green = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * t);
        int blue = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * t);
        return rgb(red, green, blue) & 0x00FFFFFF | (clampByte(alpha) << 24);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (clampByte(red) << 16) | (clampByte(green) << 8) | clampByte(blue);
    }

    private static int channel(int color, int shift) {
        return (color >>> shift) & 0xFF;
    }

    private static int clampByte(int value) {
        return value < 0 ? 0 : value > 255 ? 255 : value;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : value > 1.0F ? 1.0F : value;
    }

    private static void applyColor(int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.color(red, green, blue, alpha);
    }
}
