package com.zszl.zszlScriptMod.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.zszl.zszlScriptMod.gui.MainUiLayoutManager.RunningStatusLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;

/**
 * Bottom-left HUD reporting foreground/background path-sequence progress.
 * Each status field is rendered in its own color; while the modern control
 * center is open the panel can be dragged anywhere on screen and resized by
 * its highlighted edges. Position, size and visibility persist per profile.
 */
public final class RunningStatusHud {

    private static final int PADDING = 7;
    private static final int LINE_GAP = 3;
    private static final int SCREEN_MARGIN = 4;
    private static final int MIN_WIDTH = 110;
    private static final int MIN_HEIGHT = 32;
    private static final int EDGE_ZONE = 5;

    private static final int LABEL_COLOR = ModernUiRenderer.ACCENT;
    private static final int IDLE_COLOR = ModernUiRenderer.MUTED_TEXT;
    private static final int STATUS_COLOR = ModernUiRenderer.TEXT;
    private static final int COORD_COLOR = ModernUiRenderer.WARNING;
    private static final int DETAIL_COLOR = ModernUiRenderer.SUBTLE_TEXT;
    private static final int NEXT_COLOR = ModernUiRenderer.MUTED_TEXT;

    private static final int PANEL_FILL = 0xE0101922;
    private static final int PANEL_BORDER = ModernUiRenderer.BORDER_SUBTLE;
    private static final int PANEL_BORDER_ACTIVE = ModernUiRenderer.ACCENT;

    private static final class Segment {
        private final String text;
        private final int color;

        private Segment(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }

    private static int panelX;
    private static int panelY;
    private static int panelWidth;
    private static int panelHeight;
    private static int lastScreenWidth;
    private static int lastScreenHeight;

    private static boolean dragging;
    private static int dragStartMouseX;
    private static int dragStartMouseY;
    private static int dragStartPanelX;
    private static int dragStartPanelY;

    private static boolean resizing;
    private static boolean resizeLeft;
    private static boolean resizeRight;
    private static boolean resizeTop;
    private static boolean resizeBottom;
    private static int resizeStartMouseX;
    private static int resizeStartMouseY;
    private static int resizeStartX;
    private static int resizeStartY;
    private static int resizeStartWidth;
    private static int resizeStartHeight;

    private static boolean hoverLeft;
    private static boolean hoverRight;
    private static boolean hoverTop;
    private static boolean hoverBottom;

    private RunningStatusHud() {
    }

    public static boolean isInteractionActive() {
        return dragging || resizing;
    }

    public static void cancelInteraction() {
        if (dragging) {
            panelX = dragStartPanelX;
            panelY = dragStartPanelY;
            dragging = false;
        }
        if (resizing) {
            panelX = resizeStartX;
            panelY = resizeStartY;
            panelWidth = resizeStartWidth;
            panelHeight = resizeStartHeight;
            resizing = false;
        }
        resizeLeft = false;
        resizeRight = false;
        resizeTop = false;
        resizeBottom = false;
        clearHoverEdges();
    }

    /** Begins a drag/resize gesture; returns true when the click was consumed. */
    public static boolean mouseClicked(int mouseX, int mouseY, int screenW, int screenH) {
        if (!isGameWorldReady() || !MainUiLayoutManager.isModernRunningStatusVisible()) {
            return false;
        }
        refreshMetrics(screenW, screenH);
        if (mouseX < panelX || mouseX >= panelX + panelWidth || mouseY < panelY || mouseY >= panelY + panelHeight) {
            return false;
        }
        clearHoverEdges();
        if (mouseY < panelY + EDGE_ZONE) {
            resizeTop = true;
        }
        if (mouseY >= panelY + panelHeight - EDGE_ZONE) {
            resizeBottom = true;
        }
        if (mouseX < panelX + EDGE_ZONE) {
            resizeLeft = true;
        }
        if (mouseX >= panelX + panelWidth - EDGE_ZONE) {
            resizeRight = true;
        }
        if (resizeLeft || resizeRight || resizeTop || resizeBottom) {
            resizing = true;
            resizeStartMouseX = mouseX;
            resizeStartMouseY = mouseY;
            resizeStartX = panelX;
            resizeStartY = panelY;
            resizeStartWidth = panelWidth;
            resizeStartHeight = panelHeight;
        } else {
            dragging = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartPanelX = panelX;
            dragStartPanelY = panelY;
        }
        return true;
    }

    public static void mouseDragged(int mouseX, int mouseY, int screenW, int screenH) {
        if (dragging) {
            panelX = clamp(dragStartPanelX + mouseX - dragStartMouseX, SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenW - SCREEN_MARGIN - panelWidth));
            panelY = clamp(dragStartPanelY + mouseY - dragStartMouseY, SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenH - SCREEN_MARGIN - panelHeight));
        } else if (resizing) {
            if (resizeRight) {
                int right = clamp(resizeStartX + resizeStartWidth + mouseX - resizeStartMouseX,
                        resizeStartX + MIN_WIDTH, screenW - SCREEN_MARGIN);
                panelWidth = right - resizeStartX;
            }
            if (resizeBottom) {
                int bottom = clamp(resizeStartY + resizeStartHeight + mouseY - resizeStartMouseY,
                        resizeStartY + MIN_HEIGHT, screenH - SCREEN_MARGIN);
                panelHeight = bottom - resizeStartY;
            }
            if (resizeLeft) {
                int left = clamp(resizeStartX + mouseX - resizeStartMouseX, SCREEN_MARGIN,
                        resizeStartX + resizeStartWidth - MIN_WIDTH);
                panelX = left;
                panelWidth = resizeStartX + resizeStartWidth - left;
            }
            if (resizeTop) {
                int top = clamp(resizeStartY + mouseY - resizeStartMouseY, SCREEN_MARGIN,
                        resizeStartY + resizeStartHeight - MIN_HEIGHT);
                panelY = top;
                panelHeight = resizeStartY + resizeStartHeight - top;
            }
        }
    }

    public static boolean mouseReleased() {
        if (!isInteractionActive()) {
            return false;
        }
        dragging = false;
        resizing = false;
        resizeLeft = false;
        resizeRight = false;
        resizeTop = false;
        resizeBottom = false;
        clearHoverEdges();
        persistLayout();
        return true;
    }

    public static void render(FontRenderer fontRenderer, int screenW, int screenH, int mouseX, int mouseY) {
        if (!isGameWorldReady() || fontRenderer == null || screenW < 40 || screenH < 40
                || !MainUiLayoutManager.isModernRunningStatusVisible()) {
            return;
        }
        refreshMetrics(screenW, screenH);
        List<List<Segment>> lines = buildLines();
        int contentWidth = 0;
        for (List<Segment> line : lines) {
            contentWidth = Math.max(contentWidth, segmentsWidth(fontRenderer, line));
        }
        int lineHeight = fontRenderer.FONT_HEIGHT;
        int contentHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * LINE_GAP;
        if (!MainUiLayoutManager.getModernRunningStatusLayout().isValid() && !isInteractionActive()) {
            panelWidth = clamp(contentWidth + PADDING * 2, MIN_WIDTH,
                    Math.max(MIN_WIDTH, screenW - SCREEN_MARGIN * 2));
            panelHeight = clamp(contentHeight + PADDING * 2, MIN_HEIGHT,
                    Math.max(MIN_HEIGHT, screenH - SCREEN_MARGIN * 2));
            panelX = SCREEN_MARGIN;
            panelY = screenH - SCREEN_MARGIN - panelHeight;
        }

        boolean interacting = isInteractionActive();
        updateHoverEdges(mouseX, mouseY);
        boolean edgeHighlighted = hoverLeft || hoverRight || hoverTop || hoverBottom;
        int border = interacting || edgeHighlighted ? PANEL_BORDER_ACTIVE : PANEL_BORDER;
        ModernUiRenderer.drawSubtlePanel(panelX, panelY, panelWidth, panelHeight, 5, PANEL_FILL, border);
        if (hoverTop || resizeTop) {
            Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 2, PANEL_BORDER_ACTIVE);
        }
        if (hoverBottom || resizeBottom) {
            Gui.drawRect(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight,
                    PANEL_BORDER_ACTIVE);
        }
        if (hoverLeft || resizeLeft) {
            Gui.drawRect(panelX, panelY, panelX + 2, panelY + panelHeight, PANEL_BORDER_ACTIVE);
        }
        if (hoverRight || resizeRight) {
            Gui.drawRect(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelY + panelHeight,
                    PANEL_BORDER_ACTIVE);
        }

        int textX = panelX + PADDING;
        int textY = panelY + PADDING;
        int innerWidth = Math.max(1, panelWidth - PADDING * 2);
        for (List<Segment> line : lines) {
            drawSegments(fontRenderer, line, textX, textY, innerWidth);
            textY += lineHeight + LINE_GAP;
        }
    }

    private static void updateHoverEdges(int mouseX, int mouseY) {
        boolean inside = mouseX >= panelX && mouseX < panelX + panelWidth
                && mouseY >= panelY && mouseY < panelY + panelHeight;
        if (!inside || isInteractionActive()) {
            if (!isInteractionActive()) {
                clearHoverEdges();
            }
            return;
        }
        hoverTop = mouseY < panelY + EDGE_ZONE;
        hoverBottom = mouseY >= panelY + panelHeight - EDGE_ZONE;
        hoverLeft = mouseX < panelX + EDGE_ZONE;
        hoverRight = mouseX >= panelX + panelWidth - EDGE_ZONE;
    }

    private static void clearHoverEdges() {
        hoverLeft = false;
        hoverRight = false;
        hoverTop = false;
        hoverBottom = false;
    }

    private static boolean isGameWorldReady() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.world != null && minecraft.player != null;
    }

    private static void refreshMetrics(int screenW, int screenH) {
        if (screenW != lastScreenWidth || screenH != lastScreenHeight) {
            resolveStoredLayout(screenW, screenH);
        }
        panelWidth = clamp(panelWidth, MIN_WIDTH, Math.max(MIN_WIDTH, screenW - SCREEN_MARGIN * 2));
        panelHeight = clamp(panelHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenH - SCREEN_MARGIN * 2));
        panelX = clamp(panelX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenW - SCREEN_MARGIN - panelWidth));
        panelY = clamp(panelY, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, screenH - SCREEN_MARGIN - panelHeight));
        lastScreenWidth = screenW;
        lastScreenHeight = screenH;
    }

    private static void resolveStoredLayout(int screenW, int screenH) {
        RunningStatusLayout layout = MainUiLayoutManager.getModernRunningStatusLayout();
        if (layout.isValid()) {
            panelWidth = clamp((int) Math.round(layout.widthRatio * screenW), MIN_WIDTH,
                    Math.max(MIN_WIDTH, screenW - SCREEN_MARGIN * 2));
            panelHeight = clamp((int) Math.round(layout.heightRatio * screenH), MIN_HEIGHT,
                    Math.max(MIN_HEIGHT, screenH - SCREEN_MARGIN * 2));
            panelX = clamp((int) Math.round(layout.xRatio * screenW), SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenW - SCREEN_MARGIN - panelWidth));
            panelY = clamp((int) Math.round(layout.yRatio * screenH), SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenH - SCREEN_MARGIN - panelHeight));
        } else if (panelWidth <= 0) {
            panelWidth = MIN_WIDTH;
            panelHeight = MIN_HEIGHT;
            panelX = SCREEN_MARGIN;
            panelY = screenH - SCREEN_MARGIN - panelHeight;
        }
    }

    private static void persistLayout() {
        int screenW = lastScreenWidth;
        int screenH = lastScreenHeight;
        if (screenW <= 0 || screenH <= 0 || panelWidth <= 0 || panelHeight <= 0) {
            return;
        }
        MainUiLayoutManager.setModernRunningStatusLayout(
                clampRatio(panelX / (double) screenW),
                clampRatio(panelY / (double) screenH),
                clampRatio(panelWidth / (double) screenW),
                clampRatio(panelHeight / (double) screenH));
    }

    private static double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static List<List<Segment>> buildLines() {
        PathSequenceEventListener.ProgressSnapshot foreground = null;
        PathSequenceEventListener.ProgressSnapshot background = null;
        for (PathSequenceEventListener.ProgressSnapshot snapshot
                : PathSequenceEventListener.getActiveProgressSnapshots()) {
            if (snapshot == null) {
                continue;
            }
            if (snapshot.isBackgroundRunner()) {
                if (background == null) {
                    background = snapshot;
                }
            } else if (foreground == null) {
                foreground = snapshot;
            }
        }
        List<List<Segment>> lines = new ArrayList<>();
        lines.add(buildRunnerLine("前台：", foreground));
        lines.add(buildRunnerLine("后台：", background));
        return lines;
    }

    private static List<Segment> buildRunnerLine(String label, PathSequenceEventListener.ProgressSnapshot snapshot) {
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment(label, LABEL_COLOR));
        if (snapshot == null) {
            segments.add(new Segment("未运行", IDLE_COLOR));
            return segments;
        }
        appendRunStatus(segments, snapshot);
        return segments;
    }

    private static void appendRunStatus(List<Segment> segments, PathSequenceEventListener.ProgressSnapshot snapshot) {
        PathSequence sequence = PathSequenceManager.getSequence(snapshot.getSequenceName());
        int stepIndex = snapshot.getStepIndex();
        PathStep step = sequence == null || stepIndex < 0 || stepIndex >= sequence.getSteps().size()
                ? null : sequence.getSteps().get(stepIndex);
        if (step == null) {
            segments.add(new Segment(snapshot.getSequenceName(), STATUS_COLOR));
            return;
        }
        double[] target = step.getGotoPoint();
        boolean pathing = target != null && target.length >= 3 && !Double.isNaN(target[0])
                && !snapshot.isAtTarget();
        if (pathing) {
            segments.add(new Segment("正在前往步骤", STATUS_COLOR));
            segments.add(new Segment(String.valueOf(stepIndex + 1), STATUS_COLOR));
            segments.add(new Segment(",", DETAIL_COLOR));
            segments.add(new Segment(formatTarget(target), COORD_COLOR));
            return;
        }
        List<ActionData> actions = step.getActions();
        int actionIndex = Math.max(0, snapshot.getActionIndex());
        String currentDesc = actions != null && actionIndex < actions.size()
                ? safeDescription(actions.get(actionIndex)) : "";
        String nextDesc = actions != null && actionIndex + 1 < actions.size()
                ? safeDescription(actions.get(actionIndex + 1)) : "无";
        segments.add(new Segment("正在执行步骤", STATUS_COLOR));
        segments.add(new Segment(String.valueOf(stepIndex + 1), STATUS_COLOR));
        segments.add(new Segment("·动作", DETAIL_COLOR));
        segments.add(new Segment(String.valueOf(actionIndex + 1), STATUS_COLOR));
        segments.add(new Segment("：", DETAIL_COLOR));
        if (!currentDesc.isEmpty()) {
            segments.add(new Segment(currentDesc, DETAIL_COLOR));
        }
        segments.add(new Segment("，下一步：", DETAIL_COLOR));
        segments.add(new Segment(nextDesc, NEXT_COLOR));
    }

    private static String safeDescription(ActionData action) {
        if (action == null) {
            return "";
        }
        try {
            String description = action.getDescription();
            return description == null ? "" : description.replace('\n', ' ').trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatTarget(double[] target) {
        if (target.length < 3 || Double.isNaN(target[1])) {
            return "[" + formatCoordinate(target[0]) + "," + formatCoordinate(target[2]) + "]";
        }
        return "[" + formatCoordinate(target[0]) + "," + formatCoordinate(target[1]) + ","
                + formatCoordinate(target[2]) + "]";
    }

    private static String formatCoordinate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "?";
        }
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int segmentsWidth(FontRenderer fontRenderer, List<Segment> segments) {
        int width = 0;
        for (Segment segment : segments) {
            width += fontRenderer.getStringWidth(segment.text);
        }
        return width;
    }

    private static void drawSegments(FontRenderer fontRenderer, List<Segment> segments, int x, int y,
            int maxWidth) {
        int cursor = x;
        int remaining = maxWidth;
        for (Segment segment : segments) {
            if (remaining <= 0) {
                return;
            }
            String text = segment.text;
            if (text.isEmpty()) {
                continue;
            }
            int width = fontRenderer.getStringWidth(text);
            if (width > remaining) {
                text = fontRenderer.trimStringToWidth(text, remaining);
                if (text.isEmpty()) {
                    return;
                }
                fontRenderer.drawStringWithShadow(text, cursor, y, segment.color);
                return;
            }
            fontRenderer.drawStringWithShadow(text, cursor, y, segment.color);
            cursor += width;
            remaining -= width;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
