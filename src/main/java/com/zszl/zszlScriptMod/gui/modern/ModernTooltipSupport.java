package com.zszl.zszlScriptMod.gui.modern;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.util.text.TextFormatting;

/** Collects contextual help while controls draw and renders one top-level overlay. */
public final class ModernTooltipSupport {

    /** Screens with screen-owned information icons are not given generic button icons. */
    public interface OwnsTooltipAnchors {
        default FontRenderer getTooltipFontRenderer() {
            return null;
        }
    }

    private static final int INFO_ICON_SIZE = 11;
    private static final int TOOLTIP_DELAY_MS = 0;
    private static final int MOVE_RESET_DISTANCE = 4;
    private static final Map<GuiScreen, List<TooltipAnchor>> ANCHORS = new WeakHashMap<>();
    private static final Map<GuiScreen, List<IconAnchor>> CUSTOM_ICONS = new WeakHashMap<>();
    private static final Map<GuiScreen, ModernMainLayout.Rect> OVERLAY_BOUNDS = new WeakHashMap<>();
    private static final Map<GuiScreen, HoverState> HOVER_STATES = new WeakHashMap<>();
    private static final Map<GuiScreen, CompatibilityHoverState> COMPATIBILITY_HOVER_STATES = new WeakHashMap<>();
    private static final Map<GuiScreen, Boolean> SUPPRESSED = new WeakHashMap<>();
    private static Field guiButtonListField;

    private ModernTooltipSupport() {
    }

    public static void capture(GuiScreen screen, List<String> lines, int mouseX, int mouseY) {
        if (screen == null || lines == null || lines.isEmpty()) {
            return;
        }
        String tooltip = joinLines(lines);
        Object control = tooltip.isEmpty() ? null : findControl(screen, mouseX, mouseY);
        if (control == null) {
            // Several legacy workbenches describe cards, rows, and other
            // custom-painted regions that do not have a GuiButton or
            // GuiTextField underneath the pointer. Keep those explanations on
            // the shared tooltip path instead of silently dropping
            // them.
            if (!tooltip.isEmpty()) {
                registerHoveredTooltip(screen, mouseX, mouseY, tooltip);
            }
            return;
        }
        registerControl(screen, control, tooltip);
    }

    public static void registerButton(GuiScreen screen, GuiButton button, String tooltip) {
        if (screen == null || button == null) {
            return;
        }
        String normalized = normalizeTooltip(tooltip);
        if (!normalized.isEmpty()) {
            registerControl(screen, button, normalized);
        }
    }

    /** Registers the same concise fallback explanation used by legacy fields. */
    public static void registerTextField(GuiScreen screen, GuiTextField field) {
        if (screen == null || field == null) {
            return;
        }
        registerControl(screen, field, "gui.modern.tooltip.field_default");
    }

    public static void registerTextField(GuiScreen screen, GuiTextField field, String tooltip) {
        if (screen == null || field == null) {
            return;
        }
        String normalized = normalizeTooltip(tooltip);
        if (!normalized.isEmpty()) {
            registerControl(screen, field, normalized);
        }
    }

    /** Registers only a passive hit region for a custom information icon. */
    public static void registerInfoIcon(GuiScreen screen, int x, int y, int width, int height) {
        registerInfoIcon(screen, x, y, width, height, "");
    }

    /** Registers a custom information icon and its top-level overlay content. */
    public static void registerInfoIcon(GuiScreen screen, int x, int y, int width, int height, String tooltip) {
        registerCustomAnchor(screen, x, y, width, height, tooltip, true);
    }

    /** Registers the richer explanation used only by main-dashboard feature cards. */
    public static void registerDashboardFeatureInfoIcon(GuiScreen screen, int x, int y, int width, int height,
            String featureTitle, String description, String shortcut, boolean showMouseActions) {
        if (screen == null || width <= 0 || height <= 0) {
            return;
        }
        List<IconAnchor> icons = iconsFor(screen);
        String normalizedTitle = normalizeTooltip(featureTitle);
        String normalizedDescription = normalizeTooltip(description);
        String normalizedShortcut = shortcut == null ? "" : shortcut.trim();
        String key = "dashboard:" + x + ':' + y + ':' + width + ':' + height + ':' + normalizedTitle + ':'
                + normalizedDescription + ':' + normalizedShortcut + ':' + showMouseActions;
        for (IconAnchor icon : icons) {
            if (icon.x == x && icon.y == y && icon.width == width && icon.height == height && icon.passive) {
                icon.tooltip = normalizedDescription;
                icon.key = key;
                icon.dashboardFeature = true;
                icon.featureTitle = normalizedTitle;
                icon.shortcut = normalizedShortcut;
                icon.showMouseActions = showMouseActions;
                return;
            }
        }
        IconAnchor icon = new IconAnchor(x, y, width, height, normalizedDescription, key, true);
        icon.dashboardFeature = true;
        icon.featureTitle = normalizedTitle;
        icon.shortcut = normalizedShortcut;
        icon.showMouseActions = showMouseActions;
        icons.add(icon);
    }

    /** Registers explanatory hover content without consuming clicks on the control. */
    public static void registerTooltipAnchor(GuiScreen screen, int x, int y, int width, int height, String tooltip) {
        registerCustomAnchor(screen, x, y, width, height, tooltip, false);
    }

    private static void registerCustomAnchor(GuiScreen screen, int x, int y, int width, int height, String tooltip,
            boolean passive) {
        if (screen == null || width <= 0 || height <= 0) {
            return;
        }
        List<IconAnchor> icons = iconsFor(screen);
        String normalized = normalizeTooltip(tooltip);
        for (IconAnchor icon : icons) {
            if (icon.x == x && icon.y == y && icon.width == width && icon.height == height
                    && icon.passive == passive) {
                if (!normalized.isEmpty() || icon.tooltip.isEmpty()) {
                    icon.tooltip = normalized;
                    icon.key = "icon:" + x + ':' + y + ':' + width + ':' + height + ':' + normalized;
                }
                return;
            }
        }
        icons.add(new IconAnchor(x, y, width, height, normalized,
                "icon:" + x + ':' + y + ':' + width + ':' + height + ':' + normalized, passive));
    }

    /** Bridges panels that expose only their currently hovered explanation. */
    public static void registerHoveredTooltip(GuiScreen screen, int mouseX, int mouseY, String tooltip) {
        if (screen == null || tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        String normalized = normalizeTooltip(tooltip);
        registerCustomAnchor(screen, mouseX - 5, mouseY - 5, 11, 11, normalized, false);
    }

    /** Restricts placement to the active shell instead of the whole screen. */
    public static void setOverlayBounds(GuiScreen screen, int x, int y, int width, int height) {
        if (screen != null && width > 0 && height > 0) {
            OVERLAY_BOUNDS.put(screen, new ModernMainLayout.Rect(x, y, width, height));
        }
    }

    public static void setSuppressed(GuiScreen screen, boolean suppressed) {
        if (screen == null) {
            return;
        }
        if (suppressed) {
            SUPPRESSED.put(screen, Boolean.TRUE);
            resetHover(screen);
        } else {
            // Suppression is frame/gesture scoped. Leaving the marker in the
            // weak map makes a screen permanently mute its information labels
            // after the first drag or context-menu interaction.
            SUPPRESSED.remove(screen);
        }
    }

    /** Clears frame-owned anchors before the screen begins drawing its next frame. */
    public static void clearRegisteredInfoIcons(GuiScreen screen) {
        if (screen != null) {
            CUSTOM_ICONS.remove(screen);
            OVERLAY_BOUNDS.remove(screen);
            SUPPRESSED.remove(screen);
            HOVER_STATES.remove(screen);
            COMPATIBILITY_HOVER_STATES.remove(screen);
        }
    }

    /** Returns the currently registered explanation at a screen-local point.
     *
     * Embedded legacy screens are rendered by a modern host, so the normal
     * post-draw pass cannot inspect their local coordinates directly. The host
     * uses this read-only lookup and registers the result in its own space.
     */
    public static String getTooltipAt(GuiScreen screen, int mouseX, int mouseY) {
        if (screen == null || Boolean.TRUE.equals(SUPPRESSED.get(screen)) || Mouse.isButtonDown(0)) {
            return "";
        }
        HoverCandidate custom = findCustomCandidate(screen, mouseX, mouseY);
        if (custom != null && !custom.tooltip.isEmpty()) {
            return custom.tooltip;
        }

        List<TooltipAnchor> anchors = anchorsFor(screen);
        List<GuiButton> buttons = getButtonList(screen);
        pruneInvalidButtonAnchors(anchors, buttons);
        if (!(screen instanceof OwnsTooltipAnchors)) {
            for (GuiButton button : buttons) {
                if (button == null || !button.visible || button.width < 38 || button.height < INFO_ICON_SIZE) {
                    continue;
                }
                TooltipAnchor registered = findAnchor(anchors, button);
                String tooltip = registered == null ? defaultTooltip(button.displayString) : registered.tooltip;
                if (!tooltip.isEmpty()
                        && contains(iconX(button.x, button.width), iconY(button.y, button.height), INFO_ICON_SIZE,
                                INFO_ICON_SIZE, mouseX, mouseY)) {
                    return tooltip;
                }
            }
            for (GuiTextField field : getTextFields(screen)) {
                if (field == null || !field.getVisible() || field.width < 38 || field.height < INFO_ICON_SIZE) {
                    continue;
                }
                TooltipAnchor registered = findAnchor(anchors, field);
                String tooltip = registered == null ? ModernFormI18n.tr("gui.modern.tooltip.field_default")
                        : registered.tooltip;
                if (!tooltip.isEmpty()
                        && contains(iconX(field.x, field.width), iconY(field.y, field.height), INFO_ICON_SIZE,
                                INFO_ICON_SIZE, mouseX, mouseY)) {
                    return tooltip;
                }
            }
        }
        for (TooltipAnchor anchor : anchors) {
            if (anchor == null || anchor.tooltip == null || anchor.tooltip.isEmpty()) {
                continue;
            }
            if (anchor.control instanceof GuiButton) {
                GuiButton button = (GuiButton) anchor.control;
                if (button.visible && button.width >= 38 && button.height >= INFO_ICON_SIZE
                        && contains(iconX(button.x, button.width), iconY(button.y, button.height), INFO_ICON_SIZE,
                                INFO_ICON_SIZE, mouseX, mouseY)) {
                    return anchor.tooltip;
                }
            } else if (anchor.control instanceof GuiTextField) {
                GuiTextField field = (GuiTextField) anchor.control;
                if (field.getVisible() && field.width >= 38 && field.height >= INFO_ICON_SIZE
                        && contains(iconX(field.x, field.width), iconY(field.y, field.height), INFO_ICON_SIZE,
                                INFO_ICON_SIZE, mouseX, mouseY)) {
                    return anchor.tooltip;
                }
            }
        }
        return "";
    }

    public static boolean isSuppressed(GuiScreen screen) {
        return screen == null || Boolean.TRUE.equals(SUPPRESSED.get(screen));
    }

    public static boolean hasRegisteredInfoIcons(GuiScreen screen) {
        List<IconAnchor> icons = screen == null ? null : CUSTOM_ICONS.get(screen);
        return icons != null && !icons.isEmpty();
    }

    public static boolean isRegisteredInfoIconHit(GuiScreen screen, int mouseX, int mouseY) {
        List<IconAnchor> icons = screen == null ? null : CUSTOM_ICONS.get(screen);
        if (icons == null) {
            return false;
        }
        for (IconAnchor icon : icons) {
            if (icon.passive && contains(icon.x, icon.y, icon.width, icon.height, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** Draws generic icons, resolves the hovered anchor, and finally draws one overlay. */
    public static void draw(GuiScreen screen, List<GuiButton> buttons, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer fontRenderer = screen instanceof OwnsTooltipAnchors
                ? ((OwnsTooltipAnchors) screen).getTooltipFontRenderer() : null;
        if (fontRenderer == null) {
            fontRenderer = minecraft == null ? null : minecraft.fontRenderer;
        }
        if (screen == null || fontRenderer == null) {
            return;
        }

        List<TooltipAnchor> anchors = anchorsFor(screen);
        pruneInvalidButtonAnchors(anchors, buttons);
        // Some legacy screens draw their fields directly and never pass through
        // ThemedGuiScreen's helper. Discover those fields before laying out the
        // generic information labels so they retain the shared tooltip path.
        for (GuiTextField field : getTextFields(screen)) {
            registerTextField(screen, field);
        }
        HoverCandidate candidate = findCustomCandidate(screen, mouseX, mouseY);
        boolean drawGenericIcons = !(screen instanceof OwnsTooltipAnchors)
                && screen.getClass().getName().startsWith("com.zszl.zszlScriptMod.");
        if (drawGenericIcons) {
            candidate = drawGenericControlIcons(screen, buttons, anchors, mouseX, mouseY, candidate);
        }

        if (Boolean.TRUE.equals(SUPPRESSED.get(screen)) || Mouse.isButtonDown(0)) {
            resetHover(screen);
            return;
        }
        if (candidate == null || candidate.tooltip.isEmpty()) {
            resetHover(screen);
            return;
        }

        HoverState state = HOVER_STATES.get(screen);
        long now = System.currentTimeMillis();
        if (state == null || !candidate.key.equals(state.key)
                || distanceSquared(mouseX, mouseY, state.originMouseX, state.originMouseY)
                        > MOVE_RESET_DISTANCE * MOVE_RESET_DISTANCE) {
            state = new HoverState(candidate.key, now, mouseX, mouseY);
            HOVER_STATES.put(screen, state);
            if (TOOLTIP_DELAY_MS > 0) {
                return;
            }
        }
        if (TOOLTIP_DELAY_MS > 0 && now - state.hoverStartedAt < TOOLTIP_DELAY_MS) {
            return;
        }

        if (candidate.dashboardFeature) {
            drawDashboardFeatureTooltipPanelAt(fontRenderer, screen.width, screen.height, candidate.bounds,
                    OVERLAY_BOUNDS.get(screen), candidate.featureTitle, candidate.tooltip, candidate.shortcut,
                    candidate.showMouseActions);
        } else {
            drawTooltipPanelAt(fontRenderer, screen.width, screen.height, candidate.bounds,
                    OVERLAY_BOUNDS.get(screen), candidate.tooltip);
        }
    }

    /**
     * Draws only the generic control information icons for an embedded legacy
     * screen. The enclosing modern host renders the tooltip panel in host
     * coordinates after querying {@link #getTooltipAt(GuiScreen, int, int)}.
     */
    public static void drawEmbeddedControlIcons(GuiScreen screen, int mouseX, int mouseY) {
        if (screen == null || screen instanceof OwnsTooltipAnchors) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        List<GuiButton> buttons = getButtonList(screen);
        List<TooltipAnchor> anchors = anchorsFor(screen);
        pruneInvalidButtonAnchors(anchors, buttons);
        for (GuiTextField field : getTextFields(screen)) {
            registerTextField(screen, field);
        }
        drawGenericControlIcons(screen, buttons, anchors, mouseX, mouseY, null);
    }

    public static boolean isInfoIconHit(GuiScreen screen, List<GuiButton> buttons, int mouseX, int mouseY) {
        if (screen == null) {
            return false;
        }
        if (buttons != null) {
            for (GuiButton button : buttons) {
                if (button != null && button.visible && button.width >= 38 && button.height >= INFO_ICON_SIZE
                        && contains(iconX(button.x, button.width), iconY(button.y, button.height), INFO_ICON_SIZE,
                                INFO_ICON_SIZE, mouseX, mouseY)) {
                    return true;
                }
            }
        }
        for (TooltipAnchor anchor : anchorsFor(screen)) {
            if (!(anchor.control instanceof GuiTextField)) {
                continue;
            }
            GuiTextField field = (GuiTextField) anchor.control;
            if (field.getVisible() && field.width >= 38 && field.height >= INFO_ICON_SIZE
                    && contains(iconX(field.x, field.width), iconY(field.y, field.height), INFO_ICON_SIZE,
                            INFO_ICON_SIZE, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** Compatibility renderer for callers that cannot register a stable anchor.
     * It deliberately uses the same immediate hover behavior as the normal renderer. */
    public static void drawTooltipPanel(FontRenderer fontRenderer, int screenWidth, int screenHeight, int mouseX,
            int mouseY, String tooltip) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        String normalized = normalizeTooltip(tooltip);
        if (fontRenderer == null || screen == null || normalized.isEmpty()) {
            if (screen != null) {
                COMPATIBILITY_HOVER_STATES.remove(screen);
            }
            return;
        }
        long now = System.currentTimeMillis();
        String key = "compat:" + normalized;
        CompatibilityHoverState state = COMPATIBILITY_HOVER_STATES.get(screen);
        if (state == null || !key.equals(state.key)
                || now - state.lastSeenAt > 120L
                || distanceSquared(mouseX, mouseY, state.originMouseX, state.originMouseY)
                        > MOVE_RESET_DISTANCE * MOVE_RESET_DISTANCE) {
            COMPATIBILITY_HOVER_STATES.put(screen, new CompatibilityHoverState(key, now, now, mouseX, mouseY));
            if (TOOLTIP_DELAY_MS > 0) {
                return;
            }
        }
        state.lastSeenAt = now;
        if (TOOLTIP_DELAY_MS > 0 && now - state.hoverStartedAt < TOOLTIP_DELAY_MS) {
            return;
        }
        drawTooltipPanelAt(fontRenderer, screenWidth, screenHeight,
                new ModernMainLayout.Rect(mouseX - 1, mouseY - 1, 3, 3), null, normalized);
    }

    public static ModernMainLayout.Rect calculateTooltipBounds(int screenWidth, int screenHeight,
            ModernMainLayout.Rect shellBounds, ModernMainLayout.Rect anchorBounds, int panelWidth, int panelHeight) {
        ModernMainLayout.Rect bounds = resolvePlacementBounds(screenWidth, screenHeight, shellBounds);
        int width = Math.min(Math.max(1, panelWidth), Math.max(1, bounds.width));
        int height = Math.min(Math.max(1, panelHeight), Math.max(1, bounds.height));
        ModernMainLayout.Rect anchor = anchorBounds == null
                ? new ModernMainLayout.Rect(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2, 1, 1)
                : anchorBounds;
        int gapX = 10;
        int gapY = 8;
        int[][] candidates = {
                { anchor.right() + gapX, anchor.bottom() + gapY },
                { anchor.x - width - gapX, anchor.bottom() + gapY },
                { anchor.right() + gapX, anchor.y - height - gapY },
                { anchor.x - width - gapX, anchor.y - height - gapY },
                { anchor.x + (anchor.width - width) / 2, anchor.bottom() + gapY },
                { anchor.x + (anchor.width - width) / 2, anchor.y - height - gapY }
        };
        ModernMainLayout.Rect best = null;
        int bestOverlap = Integer.MAX_VALUE;
        for (int[] candidate : candidates) {
            ModernMainLayout.Rect raw = new ModernMainLayout.Rect(candidate[0], candidate[1], width, height);
            if (contains(bounds, raw) && !intersects(raw, anchor)) {
                return raw;
            }
            ModernMainLayout.Rect clamped = new ModernMainLayout.Rect(
                    clamp(raw.x, bounds.x, Math.max(bounds.x, bounds.right() - width)),
                    clamp(raw.y, bounds.y, Math.max(bounds.y, bounds.bottom() - height)), width, height);
            int overlap = overlapArea(clamped, anchor);
            if (overlap < bestOverlap) {
                best = clamped;
                bestOverlap = overlap;
            }
        }
        return best == null ? new ModernMainLayout.Rect(bounds.x, bounds.y, width, height) : best;
    }

    private static HoverCandidate drawGenericControlIcons(GuiScreen screen, List<GuiButton> buttons,
            List<TooltipAnchor> anchors, int mouseX, int mouseY, HoverCandidate current) {
        HoverCandidate result = current;
        for (GuiButton button : buttons == null ? Collections.<GuiButton>emptyList() : buttons) {
            if (button == null || !button.visible || button.width < 38 || button.height < INFO_ICON_SIZE) {
                continue;
            }
            TooltipAnchor registered = findAnchor(anchors, button);
            String tooltip = registered == null ? defaultTooltip(button.displayString) : registered.tooltip;
            HoverCandidate candidate = drawControlIcon(button, button.x, button.y, button.width, button.height,
                    tooltip, mouseX, mouseY);
            if (candidate != null) {
                result = candidate;
            }
        }
        for (TooltipAnchor anchor : anchors) {
            if (!(anchor.control instanceof GuiTextField)) {
                continue;
            }
            GuiTextField field = (GuiTextField) anchor.control;
            if (!field.getVisible()) {
                continue;
            }
            HoverCandidate candidate = drawControlIcon(field, field.x, field.y, field.width, field.height,
                    anchor.tooltip, mouseX, mouseY);
            if (candidate != null) {
                result = candidate;
            }
        }
        return result;
    }

    private static HoverCandidate drawControlIcon(Object control, int x, int y, int width, int height, String tooltip,
            int mouseX, int mouseY) {
        if (width < 38 || height < INFO_ICON_SIZE) {
            return null;
        }
        int infoX = iconX(x, width);
        int infoY = iconY(y, height);
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(infoX, infoY, INFO_ICON_SIZE, INFO_ICON_SIZE);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(infoX - 2, infoY - 2, INFO_ICON_SIZE + 4, INFO_ICON_SIZE + 4, 4,
                    ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawInfoIcon(infoX, infoY,
                hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        return hovered ? new HoverCandidate(bounds, tooltip,
                "control:" + System.identityHashCode(control) + ':' + tooltip) : null;
    }

    private static HoverCandidate findCustomCandidate(GuiScreen screen, int mouseX, int mouseY) {
        List<IconAnchor> icons = CUSTOM_ICONS.get(screen);
        if (icons == null) {
            return null;
        }
        for (int i = icons.size() - 1; i >= 0; i--) {
            IconAnchor icon = icons.get(i);
            if ((icon.dashboardFeature || !icon.tooltip.isEmpty())
                    && contains(icon.x, icon.y, icon.width, icon.height, mouseX, mouseY)) {
                HoverCandidate candidate = new HoverCandidate(
                        new ModernMainLayout.Rect(icon.x, icon.y, icon.width, icon.height), icon.tooltip, icon.key);
                candidate.dashboardFeature = icon.dashboardFeature;
                candidate.featureTitle = icon.featureTitle;
                candidate.shortcut = icon.shortcut;
                candidate.showMouseActions = icon.showMouseActions;
                return candidate;
            }
        }
        return null;
    }

    private static void drawDashboardFeatureTooltipPanelAt(FontRenderer fontRenderer, int screenWidth,
            int screenHeight, ModernMainLayout.Rect anchorBounds, ModernMainLayout.Rect shellBounds,
            String featureTitle, String description, String shortcut, boolean showMouseActions) {
        ModernMainLayout.Rect placementBounds = resolvePlacementBounds(screenWidth, screenHeight, shellBounds);
        int maxWidth = Math.max(1, Math.min(300, placementBounds.width));
        int bodyWidth = Math.max(1, maxWidth - 20);
        List<String> descriptionLines = wrapLines(fontRenderer, description, bodyWidth);
        String safeTitle = featureTitle == null ? "" : featureTitle.trim();
        String safeShortcut = shortcut == null ? "" : ModernFormI18n.tr(shortcut.trim());
        String infoLabel = ModernFormI18n.tr("gui.modern.tooltip.info");
        String shortcutLabel = safeShortcut.isEmpty() ? ""
                : ModernFormI18n.tr("gui.modern.tooltip.shortcut", safeShortcut);
        String leftAction = ModernFormI18n.tr("gui.modern.tooltip.left_toggle");
        String rightAction = ModernFormI18n.tr("gui.modern.tooltip.right_config");
        int textWidth = Math.max(fontRenderer.getStringWidth(safeTitle), fontRenderer.getStringWidth(shortcutLabel));
        if (!shortcutLabel.isEmpty()) {
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(infoLabel)
                    + fontRenderer.getStringWidth(shortcutLabel) + 30);
        }
        for (String line : descriptionLines) {
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(line));
        }
        if (showMouseActions) {
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(leftAction));
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(rightAction));
        }
        int panelWidth = Math.min(maxWidth, Math.max(Math.min(150, maxWidth), textWidth + 20));
        int lineHeight = Math.max(1, fontRenderer.FONT_HEIGHT);
        int contentLines = (safeTitle.isEmpty() ? 0 : 1) + descriptionLines.size();
        int actionHeight = showMouseActions ? lineHeight * 3 : 0;
        int panelHeight = Math.max(35, 27 + contentLines * lineHeight + actionHeight + 6);
        ModernMainLayout.Rect panel = calculateTooltipBounds(screenWidth, screenHeight, shellBounds, anchorBounds,
                panelWidth, panelHeight);
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 6, ModernUiRenderer.TOOLTIP_SURFACE,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawInfoIcon(panel.x + 9, panel.y + 7,
                ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, ModernUiRenderer.TOOLTIP_SURFACE));
        fontRenderer.drawString(infoLabel, panel.x + 25, panel.y + 8, ModernUiRenderer.TOOLTIP_TEXT);
        if (!shortcutLabel.isEmpty()) {
            int shortcutWidth = fontRenderer.getStringWidth(shortcutLabel);
            fontRenderer.drawString(shortcutLabel, panel.right() - shortcutWidth - 10, panel.y + 8,
                    ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, ModernUiRenderer.TOOLTIP_SURFACE));
        }
        ModernUiRenderer.drawDivider(panel.x + 9, panel.y + 20, panel.width - 18,
                ModernUiRenderer.BORDER_SUBTLE);

        int y = panel.y + 25;
        if (!safeTitle.isEmpty()) {
            fontRenderer.drawString(safeTitle, panel.x + 10, y, ModernUiRenderer.TOOLTIP_TEXT);
            y += lineHeight;
        }
        for (String line : descriptionLines) {
            fontRenderer.drawString(line, panel.x + 10, y, ModernUiRenderer.TOOLTIP_SUBTLE_TEXT);
            y += lineHeight;
        }
        if (showMouseActions) {
            y += lineHeight;
            fontRenderer.drawString(leftAction, panel.x + 10, y,
                    ModernUiRenderer.readableText(ModernUiRenderer.SUCCESS, ModernUiRenderer.TOOLTIP_SURFACE));
            y += lineHeight;
            fontRenderer.drawString(rightAction, panel.x + 10, y,
                    ModernUiRenderer.readableText(ModernUiRenderer.WARNING, ModernUiRenderer.TOOLTIP_SURFACE));
        }
    }

    private static void drawTooltipPanelAt(FontRenderer fontRenderer, int screenWidth, int screenHeight,
            ModernMainLayout.Rect anchorBounds, ModernMainLayout.Rect shellBounds, String tooltip) {
        if (fontRenderer == null || tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        ModernMainLayout.Rect placementBounds = resolvePlacementBounds(screenWidth, screenHeight, shellBounds);
        int maxWidth = Math.max(1, Math.min(280, placementBounds.width));
        int minimumWidth = Math.min(108, maxWidth);
        List<String> lines = wrapLines(fontRenderer, tooltip, Math.max(1, maxWidth - 20));
        if (lines.isEmpty()) {
            return;
        }
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, fontRenderer.getStringWidth(line));
        }
        int panelWidth = Math.min(maxWidth, Math.max(minimumWidth, textWidth + 20));
        int panelHeight = Math.max(35, lines.size() * fontRenderer.FONT_HEIGHT + 28);
        ModernMainLayout.Rect panel = calculateTooltipBounds(screenWidth, screenHeight, shellBounds, anchorBounds,
                panelWidth, panelHeight);
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 6, ModernUiRenderer.TOOLTIP_SURFACE,
                ModernUiRenderer.BORDER);
        if (panel.width >= 45 && panel.height >= 22) {
            ModernUiRenderer.drawInfoIcon(panel.x + 9, panel.y + 7,
                    ModernUiRenderer.readableText(ModernUiRenderer.ACCENT, ModernUiRenderer.TOOLTIP_SURFACE));
            fontRenderer.drawString(ModernFormI18n.tr("gui.modern.tooltip.info"), panel.x + 25, panel.y + 8,
                    ModernUiRenderer.TOOLTIP_TEXT);
            ModernUiRenderer.drawDivider(panel.x + 9, panel.y + 20, panel.width - 18,
                    ModernUiRenderer.BORDER_SUBTLE);
        }

        List<String> visibleLines = wrapLines(fontRenderer, tooltip, Math.max(1, panel.width - 20));
        int visibleLineCount = Math.max(0, (panel.height - 28) / Math.max(1, fontRenderer.FONT_HEIGHT));
        visibleLines = limitLines(fontRenderer, visibleLines, visibleLineCount, Math.max(1, panel.width - 20));
        for (int i = 0; i < visibleLines.size(); i++) {
            fontRenderer.drawString(visibleLines.get(i), panel.x + 10,
                    panel.y + 24 + i * fontRenderer.FONT_HEIGHT,
                    ModernUiRenderer.TOOLTIP_TEXT);
        }
    }

    private static List<String> limitLines(FontRenderer fontRenderer, List<String> lines, int maxLines,
            int maxWidth) {
        if (lines == null || lines.isEmpty() || maxLines <= 0) {
            return Collections.emptyList();
        }
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> result = new ArrayList<>(lines.subList(0, maxLines));
        String suffix = "...";
        int suffixWidth = fontRenderer.getStringWidth(suffix);
        int textWidth = Math.max(0, maxWidth - suffixWidth);
        String lastLine = fontRenderer.trimStringToWidth(result.get(result.size() - 1), textWidth).trim();
        result.set(result.size() - 1, fontRenderer.trimStringToWidth(lastLine + suffix, maxWidth));
        return result;
    }

    private static ModernMainLayout.Rect resolvePlacementBounds(int screenWidth, int screenHeight,
            ModernMainLayout.Rect shellBounds) {
        ModernMainLayout.Rect screen = new ModernMainLayout.Rect(6, 6, Math.max(1, screenWidth - 12),
                Math.max(1, screenHeight - 12));
        return shellBounds == null ? screen : intersectOrFallback(shellBounds.inset(6), screen);
    }

    private static void registerControl(GuiScreen screen, Object control, String tooltip) {
        List<TooltipAnchor> anchors = anchorsFor(screen);
        TooltipAnchor anchor = findAnchor(anchors, control);
        if (anchor == null) {
            anchors.add(new TooltipAnchor(control, tooltip));
        } else {
            anchor.tooltip = tooltip;
        }
    }

    private static List<TooltipAnchor> anchorsFor(GuiScreen screen) {
        List<TooltipAnchor> anchors = ANCHORS.get(screen);
        if (anchors == null) {
            anchors = new ArrayList<>();
            ANCHORS.put(screen, anchors);
        }
        return anchors;
    }

    private static List<IconAnchor> iconsFor(GuiScreen screen) {
        List<IconAnchor> icons = CUSTOM_ICONS.get(screen);
        if (icons == null) {
            icons = new ArrayList<>();
            CUSTOM_ICONS.put(screen, icons);
        }
        return icons;
    }

    private static TooltipAnchor findAnchor(List<TooltipAnchor> anchors, Object control) {
        for (TooltipAnchor anchor : anchors) {
            if (anchor.control == control) {
                return anchor;
            }
        }
        return null;
    }

    private static Object findControl(GuiScreen screen, int mouseX, int mouseY) {
        for (GuiButton button : getButtonList(screen)) {
            if (button != null && button.visible
                    && contains(button.x, button.y, button.width, button.height, mouseX, mouseY)) {
                return button;
            }
        }
        for (GuiTextField field : getTextFields(screen)) {
            if (field.getVisible() && contains(field.x, field.y, field.width, field.height, mouseX, mouseY)) {
                return field;
            }
        }
        return null;
    }

    /** Finds fields used by legacy screens without registering generic help. */
    private static List<GuiTextField> getTextFields(GuiScreen screen) {
        if (screen == null) {
            return Collections.emptyList();
        }
        Map<GuiTextField, Boolean> seen = new java.util.IdentityHashMap<>();
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    if (value instanceof GuiTextField) {
                        seen.put((GuiTextField) value, Boolean.TRUE);
                    } else if (value instanceof Iterable) {
                        for (Object item : (Iterable<?>) value) {
                            if (item instanceof GuiTextField) {
                                seen.put((GuiTextField) item, Boolean.TRUE);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // Optional reflection; a field that cannot be read simply
                    // does not participate in the compatibility tooltip path.
                }
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen screen) {
        if (screen == null) {
            return Collections.emptyList();
        }
        try {
            if (guiButtonListField == null) {
                guiButtonListField = GuiScreen.class.getDeclaredField("buttonList");
                guiButtonListField.setAccessible(true);
            }
            Object value = guiButtonListField.get(screen);
            return value instanceof List ? (List<GuiButton>) value : Collections.<GuiButton>emptyList();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static void pruneInvalidButtonAnchors(List<TooltipAnchor> anchors, List<GuiButton> buttons) {
        Iterator<TooltipAnchor> iterator = anchors.iterator();
        while (iterator.hasNext()) {
            TooltipAnchor anchor = iterator.next();
            if (anchor.control instanceof GuiButton && (buttons == null || !buttons.contains(anchor.control))) {
                iterator.remove();
            }
        }
    }

    private static List<String> wrapLines(FontRenderer fontRenderer, String tooltip, int maxWidth) {
        List<String> result = new ArrayList<>();
        String normalized = tooltip.replace("\\n", "\n");
        for (String line : normalized.split("\n")) {
            String safeLine = line == null ? "" : line.trim();
            if (safeLine.isEmpty()) {
                continue;
            }
            List<String> wrapped = fontRenderer.listFormattedStringToWidth(safeLine, maxWidth);
            if (wrapped == null || wrapped.isEmpty()) {
                result.add(safeLine);
            } else {
                result.addAll(wrapped);
            }
        }
        return result;
    }

    private static String normalizeTooltip(String tooltip) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return "";
        }
        String[] lines = tooltip.replace("\\n", "\n").split("\n");
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            String translated = ModernFormI18n.tr(line == null ? "" : line.trim());
            if (!translated.isEmpty()) {
                values.add(translated);
            }
        }
        return joinLines(values);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String normalized = TextFormatting.getTextWithoutFormattingCodes(line == null ? "" : line);
            if (normalized == null || normalized.trim().isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(normalized.trim());
        }
        return result.toString();
    }

    private static String defaultTooltip(String label) {
        String plain = TextFormatting.getTextWithoutFormattingCodes(label == null ? "" : label);
        return plain == null || plain.trim().isEmpty() ? ModernFormI18n.tr("gui.modern.tooltip.control_default")
                : plain.trim() + "。";
    }

    private static ModernMainLayout.Rect intersectOrFallback(ModernMainLayout.Rect first,
            ModernMainLayout.Rect fallback) {
        int x = Math.max(first.x, fallback.x);
        int y = Math.max(first.y, fallback.y);
        int right = Math.min(first.right(), fallback.right());
        int bottom = Math.min(first.bottom(), fallback.bottom());
        return right > x && bottom > y ? new ModernMainLayout.Rect(x, y, right - x, bottom - y) : fallback;
    }

    private static boolean contains(ModernMainLayout.Rect outer, ModernMainLayout.Rect inner) {
        return inner.x >= outer.x && inner.y >= outer.y && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom();
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first.x < second.right() && first.right() > second.x && first.y < second.bottom()
                && first.bottom() > second.y;
    }

    private static int overlapArea(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        int width = Math.max(0, Math.min(first.right(), second.right()) - Math.max(first.x, second.x));
        int height = Math.max(0, Math.min(first.bottom(), second.bottom()) - Math.max(first.y, second.y));
        return width * height;
    }

    private static int distanceSquared(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private static int iconX(int x, int width) {
        return x + width - INFO_ICON_SIZE - 5;
    }

    private static int iconY(int y, int height) {
        return y + Math.max(0, (height - INFO_ICON_SIZE) / 2);
    }

    private static boolean contains(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void resetHover(GuiScreen screen) {
        HOVER_STATES.remove(screen);
    }

    private static final class TooltipAnchor {
        private final Object control;
        private String tooltip;

        private TooltipAnchor(Object control, String tooltip) {
            this.control = control;
            this.tooltip = tooltip;
        }
    }

    private static final class IconAnchor {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private String key;
        private final boolean passive;
        private String tooltip;
        private boolean dashboardFeature;
        private String featureTitle = "";
        private String shortcut = "";
        private boolean showMouseActions;

        private IconAnchor(int x, int y, int width, int height, String tooltip, String key, boolean passive) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tooltip = tooltip;
            this.key = key;
            this.passive = passive;
        }
    }

    private static final class HoverCandidate {
        private final ModernMainLayout.Rect bounds;
        private final String tooltip;
        private final String key;
        private boolean dashboardFeature;
        private String featureTitle = "";
        private String shortcut = "";
        private boolean showMouseActions;

        private HoverCandidate(ModernMainLayout.Rect bounds, String tooltip, String key) {
            this.bounds = bounds;
            this.tooltip = tooltip;
            this.key = key;
        }
    }

    private static final class HoverState {
        private final String key;
        private final long hoverStartedAt;
        private final int originMouseX;
        private final int originMouseY;

        private HoverState(String key, long hoverStartedAt, int originMouseX, int originMouseY) {
            this.key = key;
            this.hoverStartedAt = hoverStartedAt;
            this.originMouseX = originMouseX;
            this.originMouseY = originMouseY;
        }
    }

    private static final class CompatibilityHoverState {
        private final String key;
        private final long hoverStartedAt;
        private long lastSeenAt;
        private final int originMouseX;
        private final int originMouseY;

        private CompatibilityHoverState(String key, long hoverStartedAt, long lastSeenAt, int originMouseX,
                int originMouseY) {
            this.key = key;
            this.hoverStartedAt = hoverStartedAt;
            this.lastSeenAt = lastSeenAt;
            this.originMouseX = originMouseX;
            this.originMouseY = originMouseY;
        }
    }
}
