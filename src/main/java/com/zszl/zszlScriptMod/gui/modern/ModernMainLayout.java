package com.zszl.zszlScriptMod.gui.modern;

/**
 * Geometry for the modern main menu. This stays independent from Minecraft so
 * responsive behavior can be covered by plain JVM tests.
 */
public final class ModernMainLayout {

    public static final int MIN_SIDEBAR_WIDTH = 110;
    public static final int COLLAPSED_SIDEBAR_WIDTH = 46;
    public static final int DEFAULT_SIDEBAR_WIDTH = 184;
    public static final int MAX_SIDEBAR_WIDTH = 320;
    private static final int MIN_CONTENT_WIDTH = 96;
    private static final int COMPACT_SHELL_WIDTH = 480;
    private static final int MIN_HEADER_HEIGHT = 38;
    private static final int MAX_HEADER_HEIGHT = 52;

    private ModernMainLayout() {
    }

    public static Layout calculate(int screenWidth, int screenHeight) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        int margin = Math.max(6, Math.min(18, Math.min(safeScreenWidth, safeScreenHeight) / 24));
        int shellWidth = Math.max(1, safeScreenWidth - margin * 2);
        int shellHeight = Math.max(1, safeScreenHeight - margin * 2);
        return calculate(safeScreenWidth, safeScreenHeight, new Rect(margin, margin, shellWidth, shellHeight), false);
    }

    /**
     * Builds a layout inside a caller-owned shell. The modern main screen uses
     * this overload while the default overload remains useful for full-screen
     * callers and layout tests.
     */
    public static Layout calculate(int screenWidth, int screenHeight, Rect requestedShell, boolean sidebarCollapsed) {
        return calculate(screenWidth, screenHeight, requestedShell, sidebarCollapsed, -1);
    }

    public static Layout calculate(int screenWidth, int screenHeight, Rect requestedShell, boolean sidebarCollapsed,
            int requestedSidebarWidth) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        Rect fallback = new Rect(0, 0, safeScreenWidth, safeScreenHeight);
        Rect source = requestedShell == null ? fallback : requestedShell;
        int shellX = clamp(source.x, 0, safeScreenWidth - 1);
        int shellY = clamp(source.y, 0, safeScreenHeight - 1);
        int shellWidth = Math.max(1, Math.min(source.width, safeScreenWidth - shellX));
        int shellHeight = Math.max(1, Math.min(source.height, safeScreenHeight - shellY));
        Rect shell = new Rect(shellX, shellY, shellWidth, shellHeight);

        int headerHeight = Math.max(MIN_HEADER_HEIGHT, Math.min(MAX_HEADER_HEIGHT, shellHeight / 6));
        headerHeight = Math.min(headerHeight, Math.max(1, shellHeight - 1));

        boolean compactShell = shellWidth < COMPACT_SHELL_WIDTH;
        boolean effectiveSidebarCollapsed = sidebarCollapsed || compactShell;
        int automaticSidebarWidth = Math.max(MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, shellWidth / 5));
        int desiredSidebarWidth = effectiveSidebarCollapsed ? COLLAPSED_SIDEBAR_WIDTH
                : requestedSidebarWidth > 0
                        ? Math.max(MIN_SIDEBAR_WIDTH, Math.min(MAX_SIDEBAR_WIDTH, requestedSidebarWidth))
                        : automaticSidebarWidth;
        int minimumContentWidth = Math.min(MIN_CONTENT_WIDTH, Math.max(1, shellWidth - 1));
        int sidebarWidth = Math.min(desiredSidebarWidth, Math.max(1, shellWidth - minimumContentWidth));

        Rect header = new Rect(shell.x, shell.y, shell.width, headerHeight);
        Rect sidebar = new Rect(shell.x, header.bottom(), sidebarWidth, shell.bottom() - header.bottom());
        Rect content = new Rect(sidebar.right(), header.bottom(), Math.max(1, shell.right() - sidebar.right()),
                Math.max(1, shell.bottom() - header.bottom()));
        return new Layout(shell, header, sidebar, content, effectiveSidebarCollapsed);
    }

    public static final class Layout {
        public final Rect shell;
        public final Rect header;
        public final Rect sidebar;
        public final Rect content;
        public final boolean sidebarCollapsed;

        private Layout(Rect shell, Rect header, Rect sidebar, Rect content, boolean sidebarCollapsed) {
            this.shell = shell;
            this.header = header;
            this.sidebar = sidebar;
            this.content = content;
            this.sidebarCollapsed = sidebarCollapsed;
        }

        public Grid createGrid(int reservedTop, int reservedBottom, int gap) {
            return ModernMainLayout.createGrid(content, reservedTop, reservedBottom, gap);
        }

        public Grid createDashboardGrid(int reservedTop, int reservedBottom, int gap, int requestedColumns,
                int cardHeight) {
            return ModernMainLayout.createDashboardGrid(content, reservedTop, reservedBottom, gap, requestedColumns,
                    cardHeight);
        }
    }

    public static Grid createGrid(Rect content, int reservedTop, int reservedBottom, int gap) {
            Rect safeContent = content == null ? new Rect(0, 0, 1, 1) : content;
            int safeGap = Math.max(4, gap);
            int horizontalPadding = Math.max(10, Math.min(22, safeContent.width / 16));
            int verticalPadding = Math.max(10, Math.min(22, safeContent.height / 16));
            int gridX = safeContent.x + horizontalPadding;
            int gridY = safeContent.y + Math.max(0, reservedTop) + verticalPadding;
            int gridWidth = Math.max(1, safeContent.width - horizontalPadding * 2);
            int gridHeight = Math.max(1, safeContent.height - Math.max(0, reservedTop) - Math.max(0, reservedBottom)
                    - verticalPadding * 2);

            int desiredCardWidth = Math.max(132, Math.min(220, gridWidth / 2));
            int columns = Math.max(1, (gridWidth + safeGap) / (desiredCardWidth + safeGap));
            columns = Math.min(4, columns);
            int cardWidth = Math.max(1, (gridWidth - safeGap * Math.max(0, columns - 1)) / columns);
            int cardHeight = Math.max(48, Math.min(78, Math.max(48, gridHeight / 4)));
            return new Grid(new Rect(gridX, gridY, gridWidth, gridHeight), columns, cardWidth, cardHeight, safeGap);
    }

    public static Grid createDashboardGrid(Rect content, int reservedTop, int reservedBottom, int gap,
            int requestedColumns, int requestedCardHeight) {
        Rect safeContent = content == null ? new Rect(0, 0, 1, 1) : content;
        int safeGap = Math.max(4, gap);
        int horizontalPadding = Math.max(8, Math.min(16, safeContent.width / 20));
        int verticalPadding = Math.max(6, Math.min(12, safeContent.height / 24));
        int gridX = safeContent.x + horizontalPadding;
        int gridY = safeContent.y + Math.max(0, reservedTop) + verticalPadding;
        int gridWidth = Math.max(1, safeContent.width - horizontalPadding * 2 - ModernHoverScrollbar.GUTTER);
        int gridHeight = Math.max(1, safeContent.height - Math.max(0, reservedTop) - Math.max(0, reservedBottom)
                - verticalPadding * 2);

        int cardHeight = Math.max(34, Math.min(64, requestedCardHeight));
        int maxResponsiveColumns = Math.max(1, (gridWidth + safeGap) / (88 + safeGap));
        int columns;
        if (requestedColumns > 0) {
            columns = Math.min(Math.min(8, requestedColumns), maxResponsiveColumns);
        } else {
            int desiredCardWidth = cardHeight <= 40 ? 138 : cardHeight >= 58 ? 204 : 168;
            columns = Math.max(1, (gridWidth + safeGap) / (desiredCardWidth + safeGap));
            columns = Math.min(Math.min(8, columns), maxResponsiveColumns);
        }
        int cardWidth = Math.max(1, (gridWidth - safeGap * Math.max(0, columns - 1)) / columns);
        return new Grid(new Rect(gridX, gridY, gridWidth, gridHeight), columns, cardWidth, cardHeight, safeGap);
    }

    public static final class Grid {
        public final Rect bounds;
        public final int columns;
        public final int cardWidth;
        public final int cardHeight;
        public final int gap;

        private Grid(Rect bounds, int columns, int cardWidth, int cardHeight, int gap) {
            this.bounds = bounds;
            this.columns = columns;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
            this.gap = gap;
        }

        public Rect cardAt(int index, int scrollOffset) {
            int safeIndex = Math.max(0, index);
            int column = safeIndex % columns;
            int row = safeIndex / columns;
            return new Rect(bounds.x + column * (cardWidth + gap),
                    bounds.y + row * (cardHeight + gap) - Math.max(0, scrollOffset), cardWidth, cardHeight);
        }

        public int contentHeight(int itemCount) {
            int rows = Math.max(1, (Math.max(0, itemCount) + columns - 1) / columns);
            return rows * cardHeight + Math.max(0, rows - 1) * gap;
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

        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        public Rect inset(int amount) {
            int safeAmount = Math.max(0, amount);
            return new Rect(x + safeAmount, y + safeAmount, Math.max(0, width - safeAmount * 2),
                    Math.max(0, height - safeAmount * 2));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
