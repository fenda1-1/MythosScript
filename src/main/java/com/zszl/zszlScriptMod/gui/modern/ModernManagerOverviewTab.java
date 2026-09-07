package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Modern main-window landing page for managers that retain a feature-rich
 * editor behind the overview. It keeps entry navigation consistent without
 * duplicating complex manager state machines in a second screen.
 */
public final class ModernManagerOverviewTab implements ModernSettingsTab {

    public interface ValueProvider {
        String get();
    }

    public interface ScreenFactory {
        GuiScreen create(GuiScreen parent);
    }

    public static final class Metric {
        private final String title;
        private final String tooltip;
        private final ValueProvider value;

        public Metric(String title, String tooltip, ValueProvider value) {
            this.title = safe(title);
            this.tooltip = safe(tooltip);
            this.value = value;
        }
    }

    public static final class Action {
        private final String title;
        private final String tooltip;
        private final String label;
        private final ScreenFactory factory;

        public Action(String title, String tooltip, String label, ScreenFactory factory) {
            this.title = safe(title);
            this.tooltip = safe(tooltip);
            this.label = safe(label);
            this.factory = factory;
        }
    }

    private static final class InfoHit {
        private final ModernMainLayout.Rect bounds;
        private final String tooltip;

        private InfoHit(ModernMainLayout.Rect bounds, String tooltip) {
            this.bounds = bounds;
            this.tooltip = tooltip;
        }
    }

    private static final class ActionHit {
        private final ModernMainLayout.Rect bounds;
        private final Action action;

        private ActionHit(ModernMainLayout.Rect bounds, Action action) {
            this.bounds = bounds;
            this.action = action;
        }
    }

    private final String title;
    private final String subtitle;
    private final String headerTooltip;
    private final List<Metric> metrics;
    private final List<Action> actions;
    private final List<InfoHit> infoHits = new ArrayList<>();
    private final List<ActionHit> actionHits = new ArrayList<>();

    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private int scrollOffset;
    private int maxScrollOffset;
    private String hoveredTooltip = "";

    public ModernManagerOverviewTab(String title, String subtitle, String headerTooltip, List<Metric> metrics,
            List<Action> actions) {
        this.title = safe(title);
        this.subtitle = safe(subtitle);
        this.headerTooltip = safe(headerTooltip);
        this.metrics = metrics == null ? Collections.<Metric>emptyList() : new ArrayList<>(metrics);
        this.actions = actions == null ? Collections.<Action>emptyList() : new ArrayList<>(actions);
    }

    public ModernManagerOverviewTab(String title, String subtitle, String headerTooltip, Metric[] metrics,
            Action[] actions) {
        this(title, subtitle, headerTooltip,
                metrics == null ? Collections.<Metric>emptyList() : Arrays.asList(metrics),
                actions == null ? Collections.<Action>emptyList() : Arrays.asList(actions));
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        // Values are supplied live so opening, returning from, or reloading a
        // detailed manager never leaves this overview stale.
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        hoveredTooltip = "";
        infoHits.clear();
        actionHits.clear();
        panelBounds = buildPanelBounds(requestedBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int headerHeight = subtitle.isEmpty() ? 38 : 44;
        drawHeader(fontRenderer, headerHeight, mouseX, mouseY);
        ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + headerHeight,
                Math.max(1, panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);

        contentClipBounds = new ModernMainLayout.Rect(panelBounds.x + 8, panelBounds.y + headerHeight + 6,
                Math.max(1, panelBounds.width - 16),
                Math.max(1, panelBounds.height - headerHeight - 14));
        int contentWidth = ModernHoverScrollbar.contentWidth(contentClipBounds.width - 7);
        int contentHeight = measureContentHeight(fontRenderer, contentWidth);
        maxScrollOffset = Math.max(0, contentHeight - contentClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);

        ModernUiRenderer.beginClip(contentClipBounds);
        drawContent(fontRenderer, contentClipBounds.x + 7, contentClipBounds.y + 2 - scrollOffset, contentWidth,
                mouseX, mouseY);
        ModernUiRenderer.endClip();
        drawScrollbar(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (scrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        for (InfoHit hit : infoHits) {
            if (contains(hit.bounds, mouseX, mouseY)) {
                return true;
            }
        }
        for (ActionHit hit : actionHits) {
            if (!contains(hit.bounds, mouseX, mouseY) || hit.action == null || hit.action.factory == null) {
                continue;
            }
            openManager(hit.action.factory);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && scrollbar.isDragging()) {
            scrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && scrollbar.isDragging()) {
            scrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || maxScrollOffset <= 0) {
            return false;
        }
        int before = scrollOffset;
        scrollOffset = clamp(scrollOffset + (wheel > 0 ? -30 : 30), 0, maxScrollOffset);
        return before != scrollOffset;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentClipBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        // Detailed managers own persistence. This read-only overview has no
        // draft state that could overwrite changes made after returning.
    }

    public static void openManager(ScreenFactory factory) {
        if (factory == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        GuiScreen child = factory.create(minecraft.currentScreen);
        if (child != null) {
            minecraft.displayGuiScreen(child);
        }
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect requestedBounds) {
        ModernMainLayout.Rect source = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1)
                : requestedBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 16));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private void drawHeader(FontRenderer fontRenderer, int headerHeight, int mouseX, int mouseY) {
        int titleX = panelBounds.x + 15;
        int titleY = subtitle.isEmpty() ? panelBounds.y + (headerHeight - fontRenderer.FONT_HEIGHT) / 2
                : panelBounds.y + 9;
        int titleWidth = Math.max(28, panelBounds.width - 44);
        ModernUiRenderer.drawText(fontRenderer, title, titleX, titleY, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
                titleY - 1, headerTooltip, mouseX, mouseY);
        if (!subtitle.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, titleX, titleY + 13, ModernUiRenderer.MUTED_TEXT,
                    titleWidth);
        }
    }

    private void drawContent(FontRenderer fontRenderer, int x, int y, int width, int mouseX, int mouseY) {
        int currentY = drawSectionTitle(fontRenderer, "概览", "当前状态会在返回此标签页后自动更新。", x, y, width,
                mouseX, mouseY);
        int columns = metricColumns(width);
        int metricGap = 6;
        int metricWidth = Math.max(1, (width - metricGap * (columns - 1)) / columns);
        int metricHeight = 45;
        for (int index = 0; index < metrics.size(); index++) {
            Metric metric = metrics.get(index);
            if (metric == null) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            int cardX = x + column * (metricWidth + metricGap);
            int cardY = currentY + row * (metricHeight + metricGap);
            drawMetric(fontRenderer, metric, new ModernMainLayout.Rect(cardX, cardY, metricWidth, metricHeight), mouseX,
                    mouseY);
        }
        int metricRows = Math.max(1, (metrics.size() + columns - 1) / columns);
        currentY += metricRows * metricHeight + Math.max(0, metricRows - 1) * metricGap + 12;

        currentY = drawSectionTitle(fontRenderer, "管理", "复杂配置会在独立编辑器中打开，返回后仍保留此标签页。", x,
                currentY, width, mouseX, mouseY);
        for (Action action : actions) {
            if (action == null) {
                continue;
            }
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, currentY, width, 39);
            drawAction(fontRenderer, action, row, mouseX, mouseY);
            currentY += 45;
        }
    }

    private int drawSectionTitle(FontRenderer fontRenderer, String title, String tooltip, int x, int y, int width,
            int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, title, x + 2, y + 3, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(20, width - 22));
        drawInfoIcon(x + Math.min(Math.max(12, width - 13), fontRenderer.getStringWidth(title) + 8), y + 2, tooltip,
                mouseX, mouseY);
        return y + 21;
    }

    private void drawMetric(FontRenderer fontRenderer, Metric metric, ModernMainLayout.Rect bounds, int mouseX,
            int mouseY) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, metric.title, bounds.x + 8, bounds.y + 7, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(18, bounds.width - 25));
        drawInfoIcon(bounds.x + Math.min(Math.max(12, bounds.width - 16),
                10 + fontRenderer.getStringWidth(metric.title)), bounds.y + 6, metric.tooltip, mouseX, mouseY);
        String value = metric.value == null ? "-" : safe(metric.value.get());
        ModernUiRenderer.drawText(fontRenderer, value, bounds.x + 8, bounds.y + 23, ModernUiRenderer.TEXT,
                Math.max(18, bounds.width - 16));
    }

    private void drawAction(FontRenderer fontRenderer, Action action, ModernMainLayout.Rect bounds, int mouseX,
            int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, action.title, bounds.x + 8, bounds.y + 7, ModernUiRenderer.TEXT,
                Math.max(20, bounds.width - 120));
        drawInfoIcon(bounds.x + Math.min(Math.max(14, bounds.width - 112),
                10 + fontRenderer.getStringWidth(action.title)), bounds.y + 6, action.tooltip, mouseX, mouseY);

        int buttonWidth = Math.min(104, Math.max(70, bounds.width / 3));
        ModernMainLayout.Rect button = new ModernMainLayout.Rect(bounds.right() - buttonWidth - 7, bounds.y + 8,
                buttonWidth, 22);
        boolean buttonHovered = button.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 4,
                buttonHovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT,
                buttonHovered ? 0xFFFFA4BC : ModernUiRenderer.ACCENT);
        int labelWidth = fontRenderer.getStringWidth(action.label);
        ModernUiRenderer.drawText(fontRenderer, action.label,
                button.x + Math.max(5, (button.width - labelWidth) / 2),
                button.y + (button.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.SHELL,
                Math.max(12, button.width - 10));
        actionHits.add(new ActionHit(button, action));
    }

    private int measureContentHeight(FontRenderer fontRenderer, int width) {
        int columns = metricColumns(width);
        int metricRows = Math.max(1, (metrics.size() + columns - 1) / columns);
        return 21 + metricRows * 45 + Math.max(0, metricRows - 1) * 6 + 12 + 21 + actions.size() * 45 + 4;
    }

    private int metricColumns(int width) {
        if (width >= 600) {
            return 3;
        }
        return width >= 340 ? 2 : 1;
    }

    private void drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY)
                && (contentClipBounds == null || contentClipBounds.contains(mouseX, mouseY)
                        || panelBounds == null || panelBounds.y + 48 > mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        infoHits.add(new InfoHit(bounds, tooltip));
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (maxScrollOffset <= 0 || contentClipBounds == null) {
            scrollbar.idle();
            return;
        }
        scrollbar.draw(contentClipBounds, scrollOffset, maxScrollOffset, contentClipBounds.height,
                contentClipBounds.height + maxScrollOffset, mouseX, mouseY, value -> scrollOffset = value);
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
