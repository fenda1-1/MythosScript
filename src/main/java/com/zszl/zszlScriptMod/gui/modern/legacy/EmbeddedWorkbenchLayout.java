package com.zszl.zszlScriptMod.gui.modern.legacy;

/**
 * Pure geometry for a compatibility workbench hosted by the modern window.
 * The legacy screen receives logical coordinates while the host draws it in
 * the physical canvas returned here.
 */
public final class EmbeddedWorkbenchLayout {

    public static final int DEFAULT_MIN_LOGICAL_WIDTH = 760;
    public static final int DEFAULT_MIN_LOGICAL_HEIGHT = 460;
    public static final int DEFAULT_MAX_LOGICAL_WIDTH = 1360;
    public static final int DEFAULT_MAX_LOGICAL_HEIGHT = 820;

    private EmbeddedWorkbenchLayout() {
    }

    public static Layout calculate(Rect content) {
        return calculate(content, Config.standard());
    }

    /**
     * Calculates the compact layout used when a workbench is hosted below the
     * modern tab strip. The host owns the title and status chrome in this mode,
     * so the legacy screen receives one uninterrupted viewport.
     */
    public static Layout calculateEmbedded(Rect content) {
        return calculate(content, Config.embedded());
    }

    public static Layout calculate(Rect content, Config config) {
        Rect safeContent = content == null ? new Rect(0, 0, 1, 1) : content.atLeast(1, 1);
        Config safeConfig = config == null ? Config.standard() : config;
        int frameInset = Math.min(safeConfig.frameInset, Math.min(safeContent.width, safeContent.height) / 6);
        Rect frame = safeContent.inset(frameInset).atLeast(1, 1);
        int innerInset = Math.min(safeConfig.innerInset, Math.min(frame.width, frame.height) / 6);
        Rect inner = frame.inset(innerInset).atLeast(1, 1);

        int headerHeight = Math.min(safeConfig.headerHeight, Math.max(0, inner.height / 3));
        int statusHeight = Math.min(safeConfig.statusHeight, Math.max(0, (inner.height - headerHeight) / 3));
        int headerGap = headerHeight == 0 ? 0 : Math.min(safeConfig.sectionGap, inner.height - headerHeight);
        int statusGap = statusHeight == 0 ? 0
                : Math.min(safeConfig.sectionGap, Math.max(0, inner.height - headerHeight - headerGap - statusHeight));
        int viewportHeight = Math.max(1, inner.height - headerHeight - statusHeight - headerGap - statusGap);

        Rect header = new Rect(inner.x, inner.y, inner.width, headerHeight);
        Rect viewport = new Rect(inner.x, header.bottom() + headerGap, inner.width, viewportHeight);
        Rect status = new Rect(inner.x, viewport.bottom() + statusGap, inner.width,
                Math.max(0, inner.bottom() - viewport.bottom() - statusGap));
        int returnWidth = Math.min(Math.max(0, header.width), Math.max(0, Math.min(76, header.height * 3)));
        Rect returnBounds = new Rect(header.x, header.y, returnWidth, header.height);

        int logicalWidth = clamp(viewport.width, safeConfig.minLogicalWidth, safeConfig.maxLogicalWidth);
        int logicalHeight = clamp(viewport.height, safeConfig.minLogicalHeight, safeConfig.maxLogicalHeight);
        float scale = Math.min(1.0F, Math.min(viewport.width / (float) logicalWidth,
                viewport.height / (float) logicalHeight));
        scale = Math.max(0.0001F, scale);
        int canvasWidth = Math.min(viewport.width, Math.max(1, Math.round(logicalWidth * scale)));
        int canvasHeight = Math.min(viewport.height, Math.max(1, Math.round(logicalHeight * scale)));
        Rect canvas = new Rect(viewport.x + (viewport.width - canvasWidth) / 2,
                viewport.y + (viewport.height - canvasHeight) / 2, canvasWidth, canvasHeight);
        return new Layout(frame, header, returnBounds, viewport, status, canvas, logicalWidth, logicalHeight, scale);
    }

    public static final class Config {
        public final int minLogicalWidth;
        public final int minLogicalHeight;
        public final int maxLogicalWidth;
        public final int maxLogicalHeight;
        public final int frameInset;
        public final int innerInset;
        public final int headerHeight;
        public final int statusHeight;
        public final int sectionGap;

        public Config(int minLogicalWidth, int minLogicalHeight, int maxLogicalWidth, int maxLogicalHeight,
                int frameInset, int innerInset, int headerHeight, int statusHeight, int sectionGap) {
            this.minLogicalWidth = Math.max(1, minLogicalWidth);
            this.minLogicalHeight = Math.max(1, minLogicalHeight);
            this.maxLogicalWidth = Math.max(this.minLogicalWidth, maxLogicalWidth);
            this.maxLogicalHeight = Math.max(this.minLogicalHeight, maxLogicalHeight);
            this.frameInset = Math.max(0, frameInset);
            this.innerInset = Math.max(0, innerInset);
            this.headerHeight = Math.max(0, headerHeight);
            this.statusHeight = Math.max(0, statusHeight);
            this.sectionGap = Math.max(0, sectionGap);
        }

        public static Config standard() {
            return new Config(DEFAULT_MIN_LOGICAL_WIDTH, DEFAULT_MIN_LOGICAL_HEIGHT,
                    DEFAULT_MAX_LOGICAL_WIDTH, DEFAULT_MAX_LOGICAL_HEIGHT, 10, 10, 30, 22, 6);
        }

        public static Config embedded() {
            return new Config(DEFAULT_MIN_LOGICAL_WIDTH, DEFAULT_MIN_LOGICAL_HEIGHT,
                    DEFAULT_MAX_LOGICAL_WIDTH, DEFAULT_MAX_LOGICAL_HEIGHT, 6, 8, 0, 0, 0);
        }
    }

    public static final class Layout {
        public final Rect frame;
        public final Rect header;
        public final Rect returnBounds;
        public final Rect viewport;
        public final Rect status;
        public final Rect canvas;
        public final int logicalWidth;
        public final int logicalHeight;
        public final float scale;

        private Layout(Rect frame, Rect header, Rect returnBounds, Rect viewport, Rect status, Rect canvas,
                int logicalWidth, int logicalHeight, float scale) {
            this.frame = frame;
            this.header = header;
            this.returnBounds = returnBounds;
            this.viewport = viewport;
            this.status = status;
            this.canvas = canvas;
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.scale = scale;
        }

        public boolean containsCanvas(int mouseX, int mouseY) {
            return canvas.contains(mouseX, mouseY);
        }

        public int toLogicalX(int mouseX) {
            return mapCoordinate(mouseX - canvas.x, canvas.width, logicalWidth);
        }

        public int toLogicalY(int mouseY) {
            return mapCoordinate(mouseY - canvas.y, canvas.height, logicalHeight);
        }

        private static int mapCoordinate(int physicalOffset, int physicalSize, int logicalSize) {
            if (physicalSize <= 1 || logicalSize <= 1) {
                return 0;
            }
            float progress = physicalOffset / (float) (physicalSize - 1);
            return clamp(Math.round(progress * (logicalSize - 1)), 0, logicalSize - 1);
        }
    }

    public static final class Rect {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int pointX, int pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        public Rect inset(int amount) {
            int safeAmount = Math.max(0, amount);
            return new Rect(x + safeAmount, y + safeAmount, Math.max(0, width - safeAmount * 2),
                    Math.max(0, height - safeAmount * 2));
        }

        private Rect atLeast(int minimumWidth, int minimumHeight) {
            return new Rect(x, y, Math.max(minimumWidth, width), Math.max(minimumHeight, height));
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
