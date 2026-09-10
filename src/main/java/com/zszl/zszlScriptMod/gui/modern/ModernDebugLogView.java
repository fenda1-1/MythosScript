package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.config.DebugLogManager;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.modern.core.ModernConfirmationState;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/**
 * Terminal-style debug console and module navigator for the modern shell.
 *
 * <p>The view deliberately uses a virtual row layout: the retained log count
 * can be 10,000, but only rows intersecting the current viewport are drawn.
 * Filtering and row-height calculation happen only when the log version,
 * search query, level, module filter, or available width changes.</p>
 */
final class ModernDebugLogView implements ModernSettingsTab {

    private static final int SIDEBAR_MIN_WIDTH = 174;
    private static final int SIDEBAR_FLOOR_WIDTH = 132;
    private static final int TERMINAL_MIN_WIDTH = 260;
    private static final int TERMINAL_FLOOR_WIDTH = 180;
    private static final int MODULE_PANE_GAP = 8;
    private static final int LOG_COLLAPSED_HEIGHT = 48;
    private static final int LOG_LINE_HEIGHT = 12;
    private static final int MAX_EXPANDED_LINES = 10;
    private static final int SEARCH_DEBOUNCE_MS = 120;
    private static final int MAX_COPY_CHARACTERS = 2 * 1024 * 1024;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private enum Group {
        ALL("gui.modern.debuglog.u001", "gui.modern.debuglog.u002"),
        RUNTIME("gui.modern.debuglog.u003", "gui.modern.debuglog.u003"),
        AUTOMATION("gui.modern.debuglog.u004", "gui.modern.debuglog.u004"),
        INTERACTION("gui.modern.debuglog.u005", "gui.modern.debuglog.u006");

        private final String shortLabel;
        private final String label;

        Group(String shortLabel, String label) {
            this.shortLabel = shortLabel;
            this.label = label;
        }
    }

    private enum LevelFilter {
        ALL("gui.modern.debuglog.u001"),
        DEBUG("DEBUG"),
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR");

        private final String label;

        LevelFilter(String label) {
            this.label = label;
        }

        private LevelFilter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final class ModuleHit {
        private final DebugModule module;
        private final ModernMainLayout.Rect rowBounds;
        private final ModernMainLayout.Rect toggleBounds;

        private ModuleHit(DebugModule module, ModernMainLayout.Rect rowBounds,
                ModernMainLayout.Rect toggleBounds) {
            this.module = module;
            this.rowBounds = rowBounds;
            this.toggleBounds = toggleBounds;
        }
    }

    private static final class GroupHit {
        private final Group group;
        private final ModernMainLayout.Rect bounds;

        private GroupHit(Group group, ModernMainLayout.Rect bounds) {
            this.group = group;
            this.bounds = bounds;
        }
    }

    private static final class LogHit {
        private final DebugLogManager.LogEntry entry;
        private final ModernMainLayout.Rect rowBounds;
        private final ModernMainLayout.Rect copyBounds;
        private final ModernMainLayout.Rect expandBounds;

        private LogHit(DebugLogManager.LogEntry entry, ModernMainLayout.Rect rowBounds,
                ModernMainLayout.Rect copyBounds, ModernMainLayout.Rect expandBounds) {
            this.entry = entry;
            this.rowBounds = rowBounds;
            this.copyBounds = copyBounds;
            this.expandBounds = expandBounds;
        }
    }

    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final List<LogHit> logHits = new ArrayList<>();
    private final Set<Long> expandedLogIds = new HashSet<>();
    private final ModernConfirmationState destructiveConfirmation = new ModernConfirmationState();
    private final Map<DebugModule, Integer> moduleCounts = new EnumMap<>(DebugModule.class);

    private FontRenderer fontRenderer;
    private GuiTextField searchField;
    private GuiTextField retentionField;

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect sidebarBounds;
    private ModernMainLayout.Rect sidebarClipBounds;
    private ModernMainLayout.Rect terminalBounds;
    private ModernMainLayout.Rect moduleDividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect retentionBounds;
    private ModernMainLayout.Rect retentionApplyBounds;
    private ModernMainLayout.Rect levelBounds;
    private ModernMainLayout.Rect pauseBounds;
    private ModernMainLayout.Rect followBounds;
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect logClipBounds;
    private ModernMainLayout.Rect compactModeBounds;
    private ModernMainLayout.Rect detailedModeBounds;

    private List<DebugLogManager.LogEntry> logSnapshot = Collections.emptyList();
    private List<DebugLogManager.LogEntry> visibleLogs = Collections.emptyList();
    private int[] rowOffsets = new int[0];
    private long cachedVersion = -1L;
    private int cachedLogWidth = -1;
    private String activeQuery = "";
    private String pendingQuery = "";
    private long queryRefreshAtNanos;
    private boolean cacheDirty = true;
    private boolean initialized;

    private Group selectedGroup = Group.ALL;
    private DebugModule selectedModule;
    private LevelFilter levelFilter = LevelFilter.ALL;
    private boolean detailedMode;
    private boolean paused;
    private boolean followTail = true;
    private double moduleSidebarRatio = ModernSplitPane.DEFAULT_RATIO;
    private boolean draggingModuleSidebar;
    private final ModernHoverScrollbar sidebarScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar logScrollbar = new ModernHoverScrollbar();
    private int logScrollOffset;
    private int logMaxScrollOffset;
    private int sidebarScrollOffset;
    private int sidebarMaxScrollOffset;
    private int lastMouseX;
    private int lastMouseY;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = fontRenderer;
        moduleSidebarRatio = MainUiLayoutManager.getModernSplitRatio("debug_log.module_sidebar", moduleSidebarRatio);
        DebugLogManager.ensureLoaded();
        searchField = createField(96);
        retentionField = createField(6);
        retentionField.setText(String.valueOf(DebugLogManager.getRetainedLogCount()));
        initialized = true;
    }

    private GuiTextField createField(int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setMaxStringLength(maxLength);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    @Override
    public void updateScreen() {
        if (!initialized) {
            return;
        }
        if (searchField != null) {
            searchField.updateCursorCounter();
            if (!pendingQuery.equals(searchField.getText())) {
                pendingQuery = searchField.getText();
                queryRefreshAtNanos = System.nanoTime() + SEARCH_DEBOUNCE_MS * 1_000_000L;
            }
        }
        if (retentionField != null) {
            retentionField.updateCursorCounter();
        }
        if (!pendingQuery.equals(activeQuery) && System.nanoTime() >= queryRefreshAtNanos) {
            activeQuery = pendingQuery;
            cacheDirty = true;
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        this.fontRenderer = fontRenderer;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        applyPendingQueryIfDue();
        contentBounds = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedBounds;
        panelBounds = safePanel(contentBounds);
        hoveredTooltip = "";

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(mouseX, mouseY);

        int bodyY = panelBounds.y + 52;
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 10);
        int paneX = panelBounds.x + 10;
        int paneWidth = Math.max(2, panelBounds.width - 20);
        int gap = Math.min(MODULE_PANE_GAP, Math.max(0, paneWidth - 2));
        int splitWidth = Math.max(2, paneWidth - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitWidth, moduleSidebarRatio,
                SIDEBAR_MIN_WIDTH, TERMINAL_MIN_WIDTH, SIDEBAR_FLOOR_WIDTH, TERMINAL_FLOOR_WIDTH);
        sidebarBounds = new ModernMainLayout.Rect(paneX, bodyY, split.firstWidth, bodyHeight);
        terminalBounds = new ModernMainLayout.Rect(sidebarBounds.right() + gap, bodyY, split.secondWidth, bodyHeight);
        moduleDividerBounds = ModernSplitPane.verticalDividerBounds(sidebarBounds.x, sidebarBounds.width, gap, bodyY,
                bodyHeight);

        drawSidebar(mouseX, mouseY);
        drawTerminal(mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(moduleDividerBounds, mouseX, mouseY, draggingModuleSidebar);
        if (contains(moduleDividerBounds, mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.debuglog.u007";
        }
    }

    private void applyPendingQueryIfDue() {
        if (searchField == null) {
            return;
        }
        String fieldQuery = safe(searchField.getText());
        if (!fieldQuery.equals(pendingQuery)) {
            pendingQuery = fieldQuery;
            queryRefreshAtNanos = System.nanoTime() + SEARCH_DEBOUNCE_MS * 1_000_000L;
        }
        if (!pendingQuery.equals(activeQuery) && System.nanoTime() >= queryRefreshAtNanos) {
            activeQuery = pendingQuery;
            cacheDirty = true;
        }
    }

    private void drawHeader(int mouseX, int mouseY) {
        int titleX = panelBounds.x + 15;
        int controlWidth = Math.min(194, Math.max(150, panelBounds.width / 3));
        int controlX = panelBounds.right() - controlWidth - 14;
        int titleWidth = Math.max(42, controlX - titleX - 10);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u008", titleX, panelBounds.y + 10, ModernUiRenderer.TEXT,
                titleWidth);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u009", titleX, panelBounds.y + 26,
                ModernUiRenderer.SUBTLE_TEXT, titleWidth);

        int applyWidth = 44;
        int fieldWidth = 62;
        retentionApplyBounds = new ModernMainLayout.Rect(panelBounds.right() - 14 - applyWidth,
                panelBounds.y + 10, applyWidth, 21);
        retentionBounds = new ModernMainLayout.Rect(retentionApplyBounds.x - 5 - fieldWidth,
                panelBounds.y + 10, fieldWidth, 21);
        int labelX = Math.max(titleX + 40, retentionBounds.x - 52);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u010", labelX, panelBounds.y + 16, ModernUiRenderer.MUTED_TEXT,
                Math.max(35, retentionBounds.x - labelX - 4));
        drawInputField(retentionField, retentionBounds, "3000", mouseX, mouseY);
        drawButton(retentionApplyBounds, "gui.modern.debuglog.u011", true, true, mouseX, mouseY);
        ModernUiRenderer.drawDivider(panelBounds.x + 10, panelBounds.y + 43, Math.max(1, panelBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawSidebar(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(sidebarBounds.x, sidebarBounds.y, sidebarBounds.width, sidebarBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = sidebarBounds.x + 10;
        int width = Math.max(1, sidebarBounds.width - 20);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u012", x, sidebarBounds.y + 9, ModernUiRenderer.TEXT,
                Math.max(36, width - 72));
        String bufferText = DebugLogManager.size() + "/" + DebugLogManager.getRetainedLogCount();
        ModernUiRenderer.drawText(fontRenderer, bufferText, sidebarBounds.right() - 52, sidebarBounds.y + 9,
                ModernUiRenderer.MUTED_TEXT, 46);

        int modeY = sidebarBounds.y + 28;
        int modeGap = 4;
        int modeWidth = Math.max(1, (width - modeGap) / 2);
        compactModeBounds = new ModernMainLayout.Rect(x, modeY, modeWidth, 22);
        detailedModeBounds = new ModernMainLayout.Rect(compactModeBounds.right() + modeGap, modeY, modeWidth, 22);
        drawModeButton(compactModeBounds, "gui.modern.debuglog.u013", !detailedMode, mouseX, mouseY);
        drawModeButton(detailedModeBounds, "gui.modern.debuglog.u014", detailedMode, mouseX, mouseY);

        int groupY = modeY + 28;
        groupHits.clear();
        int groupGap = 4;
        int groupWidth = Math.max(1, (width - groupGap * 3) / 4);
        int groupIndex = 0;
        for (Group group : Group.values()) {
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x + groupIndex * (groupWidth + groupGap), groupY,
                    groupIndex == 3 ? Math.max(1, x + width - (x + groupIndex * (groupWidth + groupGap))) : groupWidth,
                    21);
            groupHits.add(new GroupHit(group, bounds));
            drawGroupButton(bounds, group.shortLabel, selectedGroup == group && selectedModule == null, mouseX, mouseY);
            groupIndex++;
        }

        sidebarClipBounds = new ModernMainLayout.Rect(sidebarBounds.x + 7, groupY + 29,
                Math.max(1, sidebarBounds.width - 14), Math.max(1, sidebarBounds.bottom() - groupY - 36));
        List<Group> visibleGroups = groupsForSidebar();
        int compactRowHeight = detailedMode ? 48 : 29;
        int totalHeight = 29;
        for (Group group : visibleGroups) {
            totalHeight += 23 + groupModules(group).length * (compactRowHeight + 4);
        }
        sidebarMaxScrollOffset = Math.max(0, totalHeight - sidebarClipBounds.height);
        sidebarScrollOffset = clamp(sidebarScrollOffset, 0, sidebarMaxScrollOffset);
        moduleHits.clear();

        ModernUiRenderer.beginClip(sidebarClipBounds);
        int rowY = sidebarClipBounds.y - sidebarScrollOffset;
        ModernMainLayout.Rect allRow = new ModernMainLayout.Rect(sidebarClipBounds.x, rowY, ModernHoverScrollbar.contentWidth(sidebarClipBounds.width),
                25);
        boolean allSelected = selectedModule == null && selectedGroup == Group.ALL;
        drawModuleRow(null, "gui.modern.debuglog.u002", "gui.modern.debuglog.u015", allRow, allSelected,
                mouseX, mouseY);
        rowY += 29;
        for (Group group : visibleGroups) {
            String groupTitle = group.label;
            ModernUiRenderer.drawText(fontRenderer, groupTitle, sidebarClipBounds.x + 3, rowY + 4,
                    ModernUiRenderer.MUTED_TEXT, Math.max(24, sidebarClipBounds.width - 8));
            ModernUiRenderer.drawDivider(sidebarClipBounds.x + 3, rowY + 20,
                    Math.max(1, sidebarClipBounds.width - 8), ModernUiRenderer.BORDER_SUBTLE);
            rowY += 23;
            for (DebugModule module : groupModules(group)) {
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(sidebarClipBounds.x, rowY,
                        ModernHoverScrollbar.contentWidth(sidebarClipBounds.width), compactRowHeight);
                boolean selected = selectedModule == module;
                drawModuleRow(module, module.getDisplayName(), getModuleDescription(module), row, selected, mouseX,
                        mouseY);
                rowY += compactRowHeight + 4;
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(sidebarScrollbar, sidebarClipBounds, totalHeight, sidebarScrollOffset, sidebarMaxScrollOffset,
                mouseX, mouseY, value -> sidebarScrollOffset = value);
    }

    private List<Group> groupsForSidebar() {
        if (selectedGroup == Group.ALL) {
            return Arrays.asList(Group.RUNTIME, Group.AUTOMATION, Group.INTERACTION);
        }
        return Collections.singletonList(selectedGroup);
    }

    private void drawModuleRow(DebugModule module, String title, String description, ModernMainLayout.Rect row,
            boolean selected, int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY) && sidebarClipBounds.contains(mouseX, mouseY);
        int fill = selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        if (selected || hovered) {
            ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 5, fill);
        }
        if (selected) {
            ModernUiRenderer.drawRoundedRect(row.x, row.y + 4, 3, Math.max(8, row.height - 8), 2,
                    ModernUiRenderer.ACCENT);
        }
        int dotColor = module == null ? ModernUiRenderer.ACCENT : moduleColor(module);
        ModernUiRenderer.drawStatusDot(row.x + 9, row.y + 9, dotColor);

        int toggleWidth = module == null ? 0 : 38;
        ModernMainLayout.Rect toggleBounds = module == null ? null
                : new ModernMainLayout.Rect(row.right() - toggleWidth - 8, row.y + (row.height - 16) / 2,
                        toggleWidth, 16);
        int textWidth = module == null ? row.width - 30 : Math.max(28, toggleBounds.x - row.x - 28);
        ModernUiRenderer.drawText(fontRenderer, title, row.x + 22, row.y + 5,
                ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, ModernUiRenderer.SURFACE), textWidth);
        if (detailedMode && row.height > 35) {
            ModernUiRenderer.drawText(fontRenderer, description, row.x + 22, row.y + 21,
                    ModernUiRenderer.MUTED_TEXT, Math.max(28, row.width - 31));
        } else if (!detailedMode && hovered && module != null) {
            hoveredTooltip = description;
        }
        if (module != null) {
            boolean enabled = ModConfig.debugFlags.getOrDefault(module, false);
            ModernUiRenderer.drawToggle(toggleBounds.x + 1, toggleBounds.y + 1, 34, 14, enabled,
                    hovered && toggleBounds.contains(mouseX, mouseY));
        }
        // Keep the synthetic "gui.modern.debuglog.u002" row in the same hit-test list as
        // real modules. Its null module is intentional and maps back to the
        // ALL group when selected.
        moduleHits.add(new ModuleHit(module, row, toggleBounds));
    }

    private void drawTerminal(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(terminalBounds.x, terminalBounds.y, terminalBounds.width, terminalBounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = terminalBounds.x + 10;
        int width = Math.max(1, terminalBounds.width - 20);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u016", x, terminalBounds.y + 9, ModernUiRenderer.TEXT,
                Math.max(32, width - 92));
        String sourceText = paused ? "gui.modern.debuglog.u017" : followTail ? "gui.modern.debuglog.u018" : "gui.modern.debuglog.u019";
        ModernUiRenderer.drawText(fontRenderer, sourceText, terminalBounds.right() - 92, terminalBounds.y + 9,
                paused ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT, 84);

        int toolbarY = terminalBounds.y + 29;
        int toolbarHeight = drawToolbar(x, width, toolbarY, mouseX, mouseY);

        // The clip must be known before rebuilding the virtual row cache. This
        // also keeps the summary line in sync with the rows drawn this frame.
        int metaY = toolbarY + toolbarHeight + 9;
        logClipBounds = new ModernMainLayout.Rect(terminalBounds.x + 7, metaY + 17,
                Math.max(1, terminalBounds.width - 14), Math.max(1, terminalBounds.bottom() - metaY - 45));
        refreshLogCacheIfNeeded();

        String meta = tr("gui.modern.debuglog.fmt.stream", String.valueOf(visibleLogs.size()),
                String.valueOf(DebugLogManager.size()), String.valueOf(DebugLogManager.getDroppedCount()));
        if (statusVisible()) {
            meta += "  ·  " + statusMessage;
        }
        ModernUiRenderer.drawText(fontRenderer, meta, x + 3, metaY, ModernUiRenderer.MUTED_TEXT,
                Math.max(40, width - 6));
        drawLogRows(mouseX, mouseY);
    }

    /**
     * Lays out the search field and actions without allowing the fixed action
     * buttons to escape the terminal on a narrow embedded window. The normal
     * layout remains a single compact row; narrow windows use a search row and
     * a two-row action strip.
     */
    private int drawToolbar(int x, int width, int toolbarY, int mouseX, int mouseY) {
        int rowHeight = 30;
        int gap = 5;
        int clearWidth = 43;
        int copyWidth = 43;
        int followWidth = 52;
        int pauseWidth = 52;
        int levelWidth = 72;
        int actionWidth = clearWidth + copyWidth + followWidth + pauseWidth + levelWidth + gap * 5;

        // Keep at least 100px for a useful search field in the one-row mode.
        if (width >= actionWidth + 100) {
            int searchWidth = Math.max(1, width - actionWidth);
            searchBounds = new ModernMainLayout.Rect(x, toolbarY, searchWidth, rowHeight);
            int nextX = searchBounds.right() + gap;
            levelBounds = new ModernMainLayout.Rect(nextX, toolbarY, levelWidth, rowHeight);
            nextX = levelBounds.right() + gap;
            pauseBounds = new ModernMainLayout.Rect(nextX, toolbarY, pauseWidth, rowHeight);
            nextX = pauseBounds.right() + gap;
            followBounds = new ModernMainLayout.Rect(nextX, toolbarY, followWidth, rowHeight);
            nextX = followBounds.right() + gap;
            copyBounds = new ModernMainLayout.Rect(nextX, toolbarY, copyWidth, rowHeight);
            nextX = copyBounds.right() + gap;
            clearBounds = new ModernMainLayout.Rect(nextX, toolbarY, Math.max(1, x + width - nextX), rowHeight);
        } else {
            searchBounds = new ModernMainLayout.Rect(x, toolbarY, width, rowHeight);

            int actionY = toolbarY + rowHeight + gap;
            int topGap = width >= 6 ? Math.min(4, Math.max(1, (width - 3) / 2)) : 0;
            int topAvailable = Math.max(1, width - topGap * 2);
            int topWidth = Math.max(1, topAvailable / 3);
            int topX = x;
            levelBounds = new ModernMainLayout.Rect(topX, actionY, topWidth, rowHeight);
            topX = levelBounds.right() + topGap;
            pauseBounds = new ModernMainLayout.Rect(topX, actionY, topWidth, rowHeight);
            topX = pauseBounds.right() + topGap;
            followBounds = new ModernMainLayout.Rect(topX, actionY, Math.max(1, x + width - topX), rowHeight);

            int bottomY = actionY + rowHeight + gap;
            if (width < 2) {
                copyBounds = new ModernMainLayout.Rect(x, bottomY, width, rowHeight);
                clearBounds = copyBounds;
            } else {
                int bottomGap = Math.min(5, Math.max(0, (width - 2) / 3));
                int bottomWidth = Math.max(1, (width - bottomGap) / 2);
                copyBounds = new ModernMainLayout.Rect(x, bottomY, bottomWidth, rowHeight);
                int clearX = copyBounds.right() + bottomGap;
                clearBounds = new ModernMainLayout.Rect(clearX, bottomY, Math.max(1, x + width - clearX),
                        rowHeight);
            }
        }

        drawSearchField(mouseX, mouseY);
        drawButton(levelBounds, tr("gui.modern.debuglog.fmt.level", tr(levelFilter.label)), false, true, mouseX, mouseY);
        drawButton(pauseBounds, paused ? "gui.modern.debuglog.u020" : "gui.modern.debuglog.u021", false, true, mouseX, mouseY);
        drawButton(followBounds, followTail ? "gui.modern.debuglog.u022" : "gui.modern.debuglog.u023", followTail, true, mouseX, mouseY);
        drawButton(copyBounds, "gui.modern.debuglog.u024", false, true, mouseX, mouseY);
        drawButton(clearBounds, destructiveConfirmation.isPending("clear") ? "gui.modern.debuglog.u025" : "gui.modern.debuglog.u026", false, true,
                mouseX, mouseY);
        return width >= actionWidth + 100 ? rowHeight : rowHeight * 2 + gap * 2;
    }

    private void drawSearchField(int mouseX, int mouseY) {
        boolean focused = searchField != null && searchField.isFocused();
        boolean hovered = searchBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(searchBounds.x, searchBounds.y, searchBounds.width, searchBounds.height, 4,
                ModernUiRenderer.SURFACE, focused ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        if (searchBounds.width >= 23) {
            ModernUiRenderer.drawSearchIcon(searchBounds.x + 7, searchBounds.y + 7, ModernUiRenderer.SUBTLE_TEXT);
        }
        if (searchField == null) {
            return;
        }
        searchField.setVisible(true);
        searchField.setEnabled(true);
        searchField.x = searchBounds.x + 23;
        searchField.y = searchBounds.y + (searchBounds.height - fontRenderer.FONT_HEIGHT) / 2;
        searchField.width = Math.max(1, searchBounds.width - 30);
        searchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(searchField);
        ModernUiRenderer.drawTextField(searchField);
        if (searchField.getText().trim().isEmpty() && !focused) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u027", searchField.x, searchField.y,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, searchField.width));
        }
    }

    private void refreshLogCacheIfNeeded() {
        long currentVersion = DebugLogManager.getVersion();
        if (!paused && currentVersion != cachedVersion) {
            cacheDirty = true;
        }
        if (logClipBounds == null) {
            return;
        }
        if (cachedLogWidth != logClipBounds.width) {
            cacheDirty = true;
        }
        if (!cacheDirty) {
            return;
        }

        boolean hadCache = cachedVersion >= 0L;
        logSnapshot = DebugLogManager.snapshot();
        visibleLogs = new ArrayList<>();
        moduleCounts.clear();
        for (DebugLogManager.LogEntry entry : logSnapshot) {
            if (entry.getModule() != null) {
                moduleCounts.put(entry.getModule(), moduleCounts.containsKey(entry.getModule())
                        ? moduleCounts.get(entry.getModule()) + 1 : 1);
            }
            if (matchesFilter(entry)) {
                visibleLogs.add(entry);
            }
        }

        rowOffsets = new int[visibleLogs.size() + 1];
        int totalHeight = 0;
        int textWidth = Math.max(1, ModernHoverScrollbar.contentWidth(logClipBounds.width) - 18);
        for (int i = 0; i < visibleLogs.size(); i++) {
            rowOffsets[i] = totalHeight;
            totalHeight += rowHeightFor(visibleLogs.get(i), textWidth);
        }
        rowOffsets[visibleLogs.size()] = totalHeight;
        logMaxScrollOffset = Math.max(0, totalHeight - logClipBounds.height);
        if (followTail || !hadCache) {
            logScrollOffset = logMaxScrollOffset;
        } else {
            logScrollOffset = clamp(logScrollOffset, 0, logMaxScrollOffset);
        }
        cachedVersion = currentVersion;
        cachedLogWidth = logClipBounds.width;
        cacheDirty = false;
    }

    private boolean matchesFilter(DebugLogManager.LogEntry entry) {
        if (entry == null) {
            return false;
        }
        if (selectedModule != null && entry.getModule() != selectedModule) {
            return false;
        }
        if (selectedModule == null && selectedGroup != Group.ALL && groupFor(entry.getModule()) != selectedGroup) {
            return false;
        }
        if (levelFilter != LevelFilter.ALL && !levelMatches(entry.getLevel())) {
            return false;
        }
        if (activeQuery.isEmpty()) {
            return true;
        }
        String searchable = normalize(entry.getMessage()) + " "
                + normalize(entry.getModule() == null ? "" : entry.getModule().getDisplayName()) + " "
                + normalize(entry.getModule() == null ? "" : entry.getModule().name());
        return searchable.contains(normalize(activeQuery));
    }

    private boolean levelMatches(DebugLogManager.Level level) {
        if (level == null) {
            return false;
        }
        switch (levelFilter) {
        case DEBUG: return level == DebugLogManager.Level.DEBUG;
        case INFO: return level == DebugLogManager.Level.INFO;
        case WARN: return level == DebugLogManager.Level.WARN;
        case ERROR: return level == DebugLogManager.Level.ERROR;
        default: return true;
        }
    }

    private int rowHeightFor(DebugLogManager.LogEntry entry, int textWidth) {
        if (!expandedLogIds.contains(entry.getId())) {
            return LOG_COLLAPSED_HEIGHT;
        }
        int lineCount = wrapLines(entry.getMessage(), textWidth).size();
        int visibleLines = Math.min(MAX_EXPANDED_LINES, Math.max(1, lineCount));
        boolean clipped = entry.isTruncated() || lineCount > visibleLines;
        return 27 + visibleLines * LOG_LINE_HEIGHT + (clipped ? LOG_LINE_HEIGHT + 2 : 0) + 8;
    }

    private void drawLogRows(int mouseX, int mouseY) {
        logHits.clear();
        if (visibleLogs.isEmpty()) {
            ModernUiRenderer.beginClip(logClipBounds);
            ModernUiRenderer.drawText(fontRenderer, activeQuery.isEmpty() ? "gui.modern.debuglog.u028"
                    : "gui.modern.debuglog.u029", logClipBounds.x + 12, logClipBounds.y + 14, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, logClipBounds.width - 24));
            ModernUiRenderer.endClip();
            logScrollbar.idle();
            return;
        }

        int startIndex = findRowIndexAtScroll(logScrollOffset);
        ModernUiRenderer.beginClip(logClipBounds);
        for (int index = Math.max(0, startIndex - 1); index < visibleLogs.size(); index++) {
            int y = logClipBounds.y + rowOffsets[index] - logScrollOffset;
            if (y > logClipBounds.bottom()) {
                break;
            }
            int height = rowOffsets[index + 1] - rowOffsets[index];
            if (y + height < logClipBounds.y) {
                continue;
            }
            drawLogRow(visibleLogs.get(index), index, new ModernMainLayout.Rect(logClipBounds.x, y,
                    ModernHoverScrollbar.contentWidth(logClipBounds.width), height), mouseX, mouseY);
        }
        ModernUiRenderer.endClip();
        drawScrollbar(logScrollbar, logClipBounds, rowOffsets[rowOffsets.length - 1], logScrollOffset,
                logMaxScrollOffset, mouseX, mouseY, value -> logScrollOffset = value);
    }

    private int findRowIndexAtScroll(int scroll) {
        int low = 0;
        int high = Math.max(0, visibleLogs.size() - 1);
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (rowOffsets[middle] <= scroll) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private void drawLogRow(DebugLogManager.LogEntry entry, int index, ModernMainLayout.Rect row, int mouseX,
            int mouseY) {
        boolean expanded = expandedLogIds.contains(entry.getId());
        boolean hovered = row.contains(mouseX, mouseY) && logClipBounds.contains(mouseX, mouseY);
        int levelColor = levelColor(entry.getLevel());
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                expanded ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered || expanded ? levelColor : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(row.x, row.y, 3, row.height, 2, levelColor);

        int topY = row.y + 5;
        String number = "#" + String.valueOf(entry.getId());
        ModernUiRenderer.drawText(fontRenderer, number, row.x + 9, topY, ModernUiRenderer.MUTED_TEXT, 42);
        ModernUiRenderer.drawText(fontRenderer, formatTime(entry.getTimestamp()), row.x + 51, topY,
                ModernUiRenderer.SUBTLE_TEXT, 52);
        String level = entry.getLevel().name() + (entry.isForced() ? "!" : "");
        ModernUiRenderer.drawText(fontRenderer, level, row.x + 105, topY, levelColor, 47);
        String module = entry.getModule() == null ? "gui.modern.debuglog.u030" : entry.getModule().getDisplayName();
        ModernUiRenderer.drawText(fontRenderer, module, row.x + 151, topY, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(32, row.width - 245));

        boolean longText = isLongText(entry);
        int buttonRight = row.right() - 8;
        ModernMainLayout.Rect copy = new ModernMainLayout.Rect(Math.max(row.x + 12, buttonRight - 42), row.y + 4, 38, 18);
        drawSmallButton(copy, "gui.modern.debuglog.u024", hovered, mouseX, mouseY);
        ModernMainLayout.Rect expand = null;
        if (longText) {
            expand = new ModernMainLayout.Rect(Math.max(row.x + 12, copy.x - 47), row.y + 4, 43, 18);
            drawSmallButton(expand, expanded ? "gui.modern.debuglog.u031" : "gui.modern.debuglog.u032", hovered, mouseX, mouseY);
        }

        int textWidth = Math.max(30, row.width - 18);
        List<String> lines = wrapLines(entry.getMessage(), textWidth);
        int maxLines = expanded ? Math.min(MAX_EXPANDED_LINES, Math.max(1, lines.size())) : Math.min(2, Math.max(1, lines.size()));
        int messageY = row.y + 21;
        for (int lineIndex = 0; lineIndex < maxLines; lineIndex++) {
            ModernUiRenderer.drawText(fontRenderer, lines.get(lineIndex), row.x + 9, messageY + lineIndex * LOG_LINE_HEIGHT,
                    lineIndex == 0 ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, textWidth);
        }
        if (expanded && (entry.isTruncated() || lines.size() > maxLines)) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u033", row.x + 9,
                    messageY + maxLines * LOG_LINE_HEIGHT, ModernUiRenderer.WARNING, textWidth);
        } else if (!expanded && longText) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.debuglog.u034", row.x + 9, row.bottom() - 12,
                    ModernUiRenderer.MUTED_TEXT, textWidth);
        }
        logHits.add(new LogHit(entry, row, copy, expand));
    }

    private void drawModeButton(ModernMainLayout.Rect bounds, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 8,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(12, bounds.width - 16));
    }

    private void drawGroupButton(ModernMainLayout.Rect bounds, String label, boolean selected, int mouseX,
            int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : 0x00111111,
                selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 5,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(12, bounds.width - 10));
    }

    private void drawButton(ModernMainLayout.Rect bounds, String label, boolean primary, boolean enabled, int mouseX,
            int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D24 : primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : primary ? ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT,
                Math.max(10, bounds.width - 12));
    }

    private void drawSmallButton(ModernMainLayout.Rect bounds, String label, boolean rowHovered, int mouseX,
            int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered ? ModernUiRenderer.SURFACE_PRESSED : rowHovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SHELL_RAISED,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 6,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(10, bounds.width - 12));
    }

    private void drawInputField(GuiTextField field, ModernMainLayout.Rect bounds, String placeholder, int mouseX,
            int mouseY) {
        if (field == null || bounds == null) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        boolean focused = field.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && field.getText().trim().isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, field.x, field.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, field.width));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (sidebarScrollbar.beginDrag(mouseX, mouseY) || logScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (contains(moduleDividerBounds, mouseX, mouseY)) {
            draggingModuleSidebar = true;
            clearFieldFocus();
            return true;
        }
        if (contains(searchBounds, mouseX, mouseY)) {
            clearFieldFocus();
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(retentionBounds, mouseX, mouseY)) {
            clearFieldFocus();
            retentionField.setFocused(true);
            retentionField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(retentionApplyBounds, mouseX, mouseY)) {
            applyRetentionCount();
            return true;
        }
        if (contains(levelBounds, mouseX, mouseY)) {
            levelFilter = levelFilter.next();
            cacheDirty = true;
            return true;
        }
        if (contains(pauseBounds, mouseX, mouseY)) {
            paused = !paused;
            if (!paused) {
                cacheDirty = true;
            }
            showStatus(paused ? "gui.modern.debuglog.u035" : "gui.modern.debuglog.u036");
            return true;
        }
        if (contains(followBounds, mouseX, mouseY)) {
            followTail = !followTail;
            if (followTail) {
                cacheDirty = true;
            }
            showStatus(followTail ? "gui.modern.debuglog.u037" : "gui.modern.debuglog.u038");
            return true;
        }
        if (contains(copyBounds, mouseX, mouseY)) {
            copyVisibleLogs();
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            if (!destructiveConfirmation.request("clear")) {
                showStatus("gui.modern.debuglog.u039");
                return true;
            }
            DebugLogManager.clear();
            cacheDirty = true;
            showStatus("gui.modern.debuglog.u040");
            return true;
        }
        if (contains(compactModeBounds, mouseX, mouseY)) {
            detailedMode = false;
            return true;
        }
        if (contains(detailedModeBounds, mouseX, mouseY)) {
            detailedMode = true;
            return true;
        }
        for (GroupHit hit : groupHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                selectedGroup = hit.group;
                selectedModule = null;
                sidebarScrollOffset = 0;
                cacheDirty = true;
                return true;
            }
        }
        for (ModuleHit hit : moduleHits) {
            if (hit.toggleBounds != null && contains(sidebarClipBounds, mouseX, mouseY)
                    && hit.toggleBounds.contains(mouseX, mouseY)) {
                toggleModule(hit.module);
                return true;
            }
            if (contains(sidebarClipBounds, mouseX, mouseY) && hit.rowBounds.contains(mouseX, mouseY)) {
                selectedModule = hit.module;
                selectedGroup = groupFor(hit.module);
                sidebarScrollOffset = 0;
                cacheDirty = true;
                return true;
            }
        }
        for (int i = logHits.size() - 1; i >= 0; i--) {
            LogHit hit = logHits.get(i);
            if (!hit.rowBounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit.copyBounds.contains(mouseX, mouseY)) {
                copyEntry(hit.entry);
                return true;
            }
            if (hit.expandBounds != null && hit.expandBounds.contains(mouseX, mouseY)) {
                toggleExpanded(hit.entry);
                return true;
            }
            if (isLongText(hit.entry)) {
                toggleExpanded(hit.entry);
                return true;
            }
            return true;
        }
        clearFieldFocus();
        return true;
    }

    private void toggleExpanded(DebugLogManager.LogEntry entry) {
        if (entry == null) {
            return;
        }
        if (!expandedLogIds.add(entry.getId())) {
            expandedLogIds.remove(entry.getId());
        }
        cacheDirty = true;
    }

    private void toggleModule(DebugModule module) {
        boolean enabled = !ModConfig.debugFlags.getOrDefault(module, false);
        ModConfig.debugFlags.put(module, enabled);
        ModConfig.isDebugModeEnabled = hasEnabledModule();
        showStatus(tr(enabled ? "gui.modern.debuglog.fmt.enabled" : "gui.modern.debuglog.fmt.disabled", module.getDisplayName()));
    }

    private boolean hasEnabledModule() {
        for (DebugModule module : DebugModule.values()) {
            if (ModConfig.debugFlags.getOrDefault(module, false)) {
                return true;
            }
        }
        return false;
    }

    private void applyRetentionCount() {
        int requested;
        try {
            requested = Integer.parseInt(retentionField == null ? "" : retentionField.getText().trim());
        } catch (Exception ignored) {
            requested = DebugLogManager.DEFAULT_RETAINED_LOG_COUNT;
        }
        int normalized = DebugLogManager.normalizeRetainedLogCount(requested);
        DebugLogManager.setRetainedLogCount(normalized);
        if (retentionField != null) {
            retentionField.setText(String.valueOf(normalized));
            retentionField.setFocused(false);
        }
        cacheDirty = true;
        showStatus(tr("gui.modern.debuglog.fmt.retention", String.valueOf(normalized), tr("gui.modern.debuglog.u041")));
    }

    private void copyVisibleLogs() {
        StringBuilder builder = new StringBuilder();
        for (DebugLogManager.LogEntry entry : visibleLogs) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            appendEntryText(builder, entry);
            if (builder.length() >= MAX_COPY_CHARACTERS) {
                builder.setLength(MAX_COPY_CHARACTERS);
                builder.append("\n").append(tr("gui.modern.debuglog.fmt.copy_limit"));
                break;
            }
        }
        GuiScreen.setClipboardString(builder.toString());
        showStatus("gui.modern.debuglog.u042");
    }

    private void copyEntry(DebugLogManager.LogEntry entry) {
        StringBuilder builder = new StringBuilder();
        appendEntryText(builder, entry);
        GuiScreen.setClipboardString(builder.toString());
        showStatus(tr("gui.modern.debuglog.u043") + entry.getId());
    }

    private void appendEntryText(StringBuilder builder, DebugLogManager.LogEntry entry) {
        builder.append('[').append(formatTime(entry.getTimestamp())).append("] [")
                .append(entry.getLevel().name()).append("] [")
                .append(entry.getModule() == null ? "gui.modern.debuglog.u030" : entry.getModule().getDisplayName()).append("] ")
                .append(entry.getMessage());
    }

    private void clearFieldFocus() {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (retentionField != null) {
            retentionField.setFocused(false);
        }
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && sidebarScrollbar.isDragging()) {
            sidebarScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && logScrollbar.isDragging()) {
            logScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && draggingModuleSidebar && panelBounds != null) {
            resizeModuleSidebar(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (sidebarScrollbar.isDragging() || logScrollbar.isDragging())) {
            sidebarScrollbar.endDrag();
            logScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingModuleSidebar) {
            draggingModuleSidebar = false;
            MainUiLayoutManager.setModernSplitRatio("debug_log.module_sidebar", moduleSidebarRatio);
            return true;
        }
        return false;
    }

    private void resizeModuleSidebar(int mouseX) {
        int paneX = panelBounds.x + 10;
        int paneWidth = Math.max(2, panelBounds.width - 20);
        int gap = Math.min(MODULE_PANE_GAP, Math.max(0, paneWidth - 2));
        int splitWidth = Math.max(2, paneWidth - gap);
        int pointerOffset = mouseX - paneX - gap / 2;
        ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitWidth, pointerOffset,
                SIDEBAR_MIN_WIDTH, TERMINAL_MIN_WIDTH, SIDEBAR_FLOOR_WIDTH, TERMINAL_FLOOR_WIDTH);
        moduleSidebarRatio = split.ratio;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused()
                && searchField.textboxKeyTyped(typedChar, keyCode)) {
            pendingQuery = searchField.getText();
            queryRefreshAtNanos = System.nanoTime() + SEARCH_DEBOUNCE_MS * 1_000_000L;
            return true;
        }
        if (retentionField != null && retentionField.isFocused()
                && retentionField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && retentionField != null && retentionField.isFocused()) {
            applyRetentionCount();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (logClipBounds != null && logClipBounds.contains(mouseX, mouseY) && logMaxScrollOffset > 0) {
            int previous = logScrollOffset;
            logScrollOffset = clamp(logScrollOffset + (wheel > 0 ? -42 : 42), 0, logMaxScrollOffset);
            if (logScrollOffset != logMaxScrollOffset) {
                followTail = false;
            }
            return previous != logScrollOffset;
        }
        if (sidebarBounds != null && sidebarBounds.contains(mouseX, mouseY) && sidebarMaxScrollOffset > 0) {
            int previous = sidebarScrollOffset;
            sidebarScrollOffset = clamp(sidebarScrollOffset + (wheel > 0 ? -32 : 32), 0,
                    sidebarMaxScrollOffset);
            return previous != sidebarScrollOffset;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (destructiveConfirmation.isPending("clear")) {
            destructiveConfirmation.clear();
            showStatus("gui.modern.debuglog.u044");
            return true;
        }
        if (searchField != null && (!safe(searchField.getText()).isEmpty() || !activeQuery.isEmpty())) {
            setSearchText("");
            return true;
        }
        if (selectedModule != null || selectedGroup != Group.ALL || levelFilter != LevelFilter.ALL) {
            selectedModule = null;
            selectedGroup = Group.ALL;
            levelFilter = LevelFilter.ALL;
            cacheDirty = true;
            return true;
        }
        if (paused) {
            paused = false;
            cacheDirty = true;
            return true;
        }
        return false;
    }

    private void setSearchText(String value) {
        String safeValue = safe(value);
        if (searchField != null) {
            searchField.setText(safeValue);
            searchField.setFocused(false);
        }
        pendingQuery = safeValue;
        activeQuery = safeValue;
        cacheDirty = true;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contentBounds != null && contentBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return detailedMode ? "" : hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        clearFieldFocus();
        setSearchText("");
        if (retentionField != null) {
            retentionField.setText(String.valueOf(DebugLogManager.getRetainedLogCount()));
        }
        selectedModule = null;
        selectedGroup = Group.ALL;
        levelFilter = LevelFilter.ALL;
        detailedMode = false;
        paused = false;
        followTail = true;
        draggingModuleSidebar = false;
        expandedLogIds.clear();
        destructiveConfirmation.clear();
        logScrollOffset = 0;
        sidebarScrollOffset = 0;
        statusMessage = "";
        cacheDirty = true;
    }

    @Override
    public boolean isDirty() {
        return retentionField != null
                && !String.valueOf(DebugLogManager.getRetainedLogCount()).equals(retentionField.getText().trim());
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2200L;
    }

    private boolean statusVisible() {
        return statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil;
    }

    private Group groupFor(DebugModule module) {
        if (module == null) {
            return Group.ALL;
        }
        switch (module) {
        case PATH_SEQUENCE:
        case BARITONE:
        case KILL_AURA_TELEPORT:
        case KILL_AURA_ORBIT:
        case KILL_AURA_ORBIT_TRACE:
        case TRIGGER_RULES:
        case EVACUATION:
            return Group.RUNTIME;
        case AUTO_EAT:
        case ITEM_FILTER:
        case CONDITIONAL_EXECUTION:
        case AUTO_PICKUP:
        case AUTO_EQUIP:
            return Group.AUTOMATION;
        case AHK_EXECUTION:
        case ARENA_HANDLER:
        case CHEST_ANALYSIS:
        case WAREHOUSE_ANALYSIS:
        default:
            return Group.INTERACTION;
        }
    }

    private DebugModule[] groupModules(Group group) {
        switch (group) {
        case RUNTIME:
            return new DebugModule[] { DebugModule.PATH_SEQUENCE, DebugModule.BARITONE,
                    DebugModule.TRIGGER_RULES, DebugModule.EVACUATION, DebugModule.KILL_AURA_TELEPORT,
                    DebugModule.KILL_AURA_ORBIT,
                    DebugModule.KILL_AURA_ORBIT_TRACE };
        case AUTOMATION:
            return new DebugModule[] { DebugModule.AUTO_EAT, DebugModule.ITEM_FILTER,
                    DebugModule.CONDITIONAL_EXECUTION, DebugModule.AUTO_PICKUP, DebugModule.AUTO_EQUIP };
        case INTERACTION:
            return new DebugModule[] { DebugModule.AHK_EXECUTION, DebugModule.ARENA_HANDLER,
                    DebugModule.CHEST_ANALYSIS, DebugModule.WAREHOUSE_ANALYSIS };
        default:
            return new DebugModule[0];
        }
    }

    private int getModuleCount(DebugModule module) {
        Integer count = moduleCounts.get(module);
        return count == null ? 0 : count;
    }

    private int moduleColor(DebugModule module) {
        Group group = groupFor(module);
        if (group == Group.RUNTIME) {
            return 0xFF6AA9FF;
        }
        if (group == Group.AUTOMATION) {
            return ModernUiRenderer.SUCCESS;
        }
        return ModernUiRenderer.WARNING;
    }

    private int levelColor(DebugLogManager.Level level) {
        if (level == DebugLogManager.Level.ERROR) {
            return 0xFFE06A78;
        }
        if (level == DebugLogManager.Level.WARN) {
            return ModernUiRenderer.WARNING;
        }
        if (level == DebugLogManager.Level.DEBUG) {
            return 0xFF69D4CC;
        }
        return 0xFF6AA9FF;
    }

    private String getModuleDescription(DebugModule module) {
        switch (module) {
        case PATH_SEQUENCE: return "gui.modern.debuglog.u045";
        case EVACUATION: return "gui.modern.debuglog.u046";
        case AUTO_EAT: return "gui.modern.debuglog.u047";
        case ITEM_FILTER: return "gui.modern.debuglog.u048";
        case AHK_EXECUTION: return "gui.modern.debuglog.u049";
        case ARENA_HANDLER: return "gui.modern.debuglog.u050";
        case CHEST_ANALYSIS: return "gui.modern.debuglog.u051";
        case WAREHOUSE_ANALYSIS: return "gui.modern.debuglog.u052";
        case CONDITIONAL_EXECUTION: return "gui.modern.debuglog.u053";
        case AUTO_PICKUP: return "gui.modern.debuglog.u054";
        case AUTO_EQUIP: return "gui.modern.debuglog.u055";
        case TRIGGER_RULES: return "gui.modern.debuglog.u056";
        case BARITONE: return "gui.modern.debuglog.u057";
        case KILL_AURA_TELEPORT: return "gui.modern.debuglog.u058";
        case KILL_AURA_ORBIT: return "gui.modern.debuglog.u059";
        case KILL_AURA_ORBIT_TRACE: return "gui.modern.debuglog.u060";
        default: return "gui.modern.debuglog.u061";
        }
    }

    private List<String> wrapLines(String value, int width) {
        String plain = TextFormatting.getTextWithoutFormattingCodes(value == null ? "" : value);
        if (plain == null) {
            plain = value == null ? "" : value;
        }
        List<String> result = new ArrayList<>();
        String normalized = plain.replace("\r", "");
        for (String line : normalized.split("\n", -1)) {
            List<String> wrapped = fontRenderer.listFormattedStringToWidth(line, Math.max(20, width));
            if (wrapped == null || wrapped.isEmpty()) {
                result.add("");
            } else {
                result.addAll(wrapped);
            }
        }
        return result.isEmpty() ? Collections.singletonList("") : result;
    }

    private boolean isLongText(DebugLogManager.LogEntry entry) {
        String message = entry == null ? "" : safe(entry.getMessage());
        return entry != null && (entry.isTruncated() || message.length() > 150 || message.indexOf('\n') >= 0);
    }

    private String formatTime(long timestamp) {
        synchronized (TIME_FORMAT) {
            return TIME_FORMAT.format(new Date(timestamp));
        }
    }

    private static ModernMainLayout.Rect safePanel(ModernMainLayout.Rect bounds) {
        int inset = Math.min(12, Math.max(4, Math.min(bounds.width, bounds.height) / 10));
        return new ModernMainLayout.Rect(bounds.x + inset, bounds.y + inset,
                Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
    }

    private static void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clipBounds, int contentHeight,
            int scrollOffset, int maxScroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clipBounds == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clipBounds, scrollOffset, maxScroll, clipBounds.height, Math.max(clipBounds.height, contentHeight),
                mouseX, mouseY, setter);
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int x, int y) {
        return bounds != null && bounds.contains(x, y);
    }

    private static String normalize(String value) {
        String plain = TextFormatting.getTextWithoutFormattingCodes(value == null ? "" : value);
        return (plain == null ? value : plain).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
