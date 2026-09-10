package com.zszl.zszlScriptMod.gui.modern;

import java.util.function.IntConsumer;

/**
 * Shared modern scrollbar: thin at rest, hover/drag enlarges and highlights the thumb.
 */
public final class ModernHoverScrollbar {

    public static final int TRACK_WIDTH = 14;
    /** Horizontal tracks need less vertical breathing room than side rails. */
    public static final int HORIZONTAL_TRACK_WIDTH = 10;
    public static final int CONTENT_GAP = 4;
    public static final int HORIZONTAL_CONTENT_GAP = 2;
    public static final int GUTTER = TRACK_WIDTH + CONTENT_GAP;
    public static final int HORIZONTAL_GUTTER = HORIZONTAL_TRACK_WIDTH + HORIZONTAL_CONTENT_GAP;

    public static int contentWidth(int viewportWidth) {
        return Math.max(1, viewportWidth - GUTTER);
    }

    public static ModernMainLayout.Rect contentBounds(ModernMainLayout.Rect viewport) {
        return new ModernMainLayout.Rect(viewport.x, viewport.y, contentWidth(viewport.width), viewport.height);
    }

    public enum Axis {
        VERTICAL, HORIZONTAL
    }

    private final Axis axis;
    private ModernMainLayout.Rect track;
    private ModernMainLayout.Rect thumb;
    private float hover;
    private boolean dragging;
    private int dragOffset;
    private int maxScroll;
    private int scroll;
    private IntConsumer onScroll;

    public ModernHoverScrollbar() {
        this(Axis.VERTICAL);
    }

    public ModernHoverScrollbar(Axis axis) {
        this.axis = axis == null ? Axis.VERTICAL : axis;
    }

    public boolean isDragging() {
        return dragging;
    }

    public int getScroll() {
        return scroll;
    }

    public boolean contains(int mouseX, int mouseY) {
        return track != null && track.contains(mouseX, mouseY);
    }

    public void idle() {
        if (!dragging) {
            track = null;
            thumb = null;
            onScroll = null;
            maxScroll = 0;
        }
        hover *= 0.7F;
    }

    public void draw(ModernMainLayout.Rect list, int scroll, int maxScroll, int visibleExtent, int totalExtent,
            int mouseX, int mouseY, IntConsumer onScroll) {
        if (list == null) {
            endDrag();
            idle();
            return;
        }
        if (axis == Axis.HORIZONTAL) {
            drawEdge(list.bottom(), list.x, list.width, true, scroll, maxScroll, visibleExtent, totalExtent,
                    mouseX, mouseY, onScroll);
        } else {
            drawEdge(list.right(), list.y, list.height, false, scroll, maxScroll, visibleExtent, totalExtent,
                    mouseX, mouseY, onScroll);
        }
    }

    public void drawAt(int edge, int origin, int length, int scroll, int maxScroll, int visibleExtent, int totalExtent,
            int mouseX, int mouseY, IntConsumer onScroll) {
        drawEdge(edge, origin, length, axis == Axis.HORIZONTAL, scroll, maxScroll, visibleExtent, totalExtent,
                mouseX, mouseY, onScroll);
    }

    public boolean beginDrag(int mouseX, int mouseY) {
        if (track == null || maxScroll <= 0 || !track.contains(mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        if (axis == Axis.HORIZONTAL) {
            dragOffset = thumb != null && thumb.contains(mouseX, mouseY)
                    ? mouseX - thumb.x
                    : thumb == null ? 9 : Math.max(1, thumb.width / 2);
        } else {
            dragOffset = thumb != null && thumb.contains(mouseX, mouseY)
                    ? mouseY - thumb.y
                    : thumb == null ? 9 : Math.max(1, thumb.height / 2);
        }
        applyDrag(mouseX, mouseY);
        return true;
    }

    public boolean applyDrag(int mouseX, int mouseY) {
        if (!dragging || track == null || thumb == null || maxScroll <= 0) {
            return dragging;
        }
        if (axis == Axis.HORIZONTAL) {
            int travel = Math.max(1, track.width - thumb.width);
            int target = clamp(mouseX - dragOffset, track.x, track.x + travel);
            setScroll(Math.round((target - track.x) * maxScroll / (float) travel));
        } else {
            int travel = Math.max(1, track.height - thumb.height);
            int target = clamp(mouseY - dragOffset, track.y, track.y + travel);
            setScroll(Math.round((target - track.y) * maxScroll / (float) travel));
        }
        return true;
    }

    public void endDrag() {
        dragging = false;
    }

    private void drawEdge(int edge, int origin, int length, boolean horizontal, int scroll, int maxScroll,
            int visibleExtent, int totalExtent, int mouseX, int mouseY, IntConsumer onScroll) {
        layout(edge, origin, length, scroll, maxScroll, visibleExtent, totalExtent, onScroll);
        if (track == null) {
            return;
        }
        int thumbSize = horizontal ? thumb.width : thumb.height;
        int thumbPos = horizontal ? thumb.x : thumb.y;
        boolean hovered = dragging || track.contains(mouseX, mouseY);
        float target = hovered ? 1.0F : 0.0F;
        hover += (target - hover) * 0.28F;
        if (Math.abs(target - hover) < 0.01F) {
            hover = target;
        }
        // Keep a compact horizontal thumb inside its smaller reserved band;
        // vertical rails can still use the taller hover treatment.
        int grown = horizontal ? Math.round(4 + 4 * hover) : Math.round(4 + 6 * hover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * hover));
        int thumbColor = hovered ? ModernUiRenderer.ACCENT
                : mix(ModernUiRenderer.SUBTLE_TEXT, ModernUiRenderer.ACCENT, hover);
        if (horizontal) {
            int thumbY = edge - grown - 2;
            int railY = edge - trackWidth - 3;
            ModernUiRenderer.drawRoundedRect(origin, railY, length, trackWidth, 2, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(thumbPos, thumbY, thumbSize, grown, Math.max(2, grown / 2), thumbColor);
            thumb = new ModernMainLayout.Rect(thumbPos, thumbY, thumbSize, grown);
        } else {
            int thumbX = edge - grown - 2;
            int railX = edge - trackWidth - 3;
            ModernUiRenderer.drawRoundedRect(railX, origin, trackWidth, length, 2, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(thumbX, thumbPos, grown, thumbSize, Math.max(2, grown / 2), thumbColor);
            thumb = new ModernMainLayout.Rect(thumbX, thumbPos, grown, thumbSize);
        }
    }

    // Keep hit geometry testable without an OpenGL context.
    void layout(int edge, int origin, int length, int scroll, int maxScroll,
            int visibleExtent, int totalExtent, IntConsumer onScroll) {
        this.onScroll = onScroll;
        this.maxScroll = Math.max(0, maxScroll);
        this.scroll = clamp(scroll, 0, this.maxScroll);
        if (length <= 0 || this.maxScroll <= 0) {
            endDrag();
            idle();
            return;
        }
        int visible = Math.max(1, visibleExtent);
        int total = Math.max(visible, totalExtent);
        int thumbSize = (int) Math.max(18L, (long) length * visible / total);
        thumbSize = Math.min(length, thumbSize);
        int travel = Math.max(0, length - thumbSize);
        int thumbPos = origin + (int) ((long) travel * this.scroll / Math.max(1, this.maxScroll));
        int grown = axis == Axis.HORIZONTAL ? Math.round(4 + 4 * hover) : Math.round(4 + 6 * hover);
        int trackExtent = axis == Axis.HORIZONTAL ? HORIZONTAL_TRACK_WIDTH : TRACK_WIDTH;
        if (axis == Axis.HORIZONTAL) {
            track = new ModernMainLayout.Rect(origin, edge - trackExtent, length, trackExtent);
            thumb = new ModernMainLayout.Rect(thumbPos, edge - grown - 2, thumbSize, grown);
        } else {
            track = new ModernMainLayout.Rect(edge - TRACK_WIDTH, origin, TRACK_WIDTH, length);
            thumb = new ModernMainLayout.Rect(edge - grown - 2, thumbPos, grown, thumbSize);
        }
    }

    private void setScroll(int value) {
        scroll = clamp(value, 0, maxScroll);
        if (onScroll != null) {
            onScroll.accept(scroll);
        }
    }

    private static int mix(int from, int to, float amount) {
        float t = amount < 0 ? 0 : amount > 1 ? 1 : amount;
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
