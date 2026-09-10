package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.path.ActionVariableRegistry;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.ExecutionEvent;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.SessionSnapshot;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Native action-variable inspector for the path workbench. Groups variables by
 * scope, shows live/log values, and preserves the legacy source-card workflow.
 */
final class ModernActionVariablePanel {

    private static final int GROUP_ROW_HEIGHT = 26;
    private static final int VARIABLE_ROW_HEIGHT = 34;
    private static final int SOURCE_CARD_HEIGHT = 84;
    private static final int SOURCE_CARD_GAP = 8;
    private static final int POPUP_SCROLL_STEP = 18;
    private static final String[] SCOPE_ORDER = new String[] { "global", "sequence", "local", "temp" };

    interface Host {
        List<PathSequence> sequences();

        void drawField(FontRenderer font, String key, ModernMainLayout.Rect rect, boolean enabled, String hint,
                int mouseX, int mouseY);

        void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary, boolean danger,
                boolean enabled, int mouseX, int mouseY);

        String fieldText(String key);

        void setFieldTextIfUnchanged(String key, String value);

        void clearFocus();

        void status(String message);

        void pushHistory(String reason);

        void markDirty();

        void refreshEditor();

        void setTooltip(String text);

        boolean isControlDown();
    }

    private static final class TreeRow {
        private final boolean groupRow;
        private final String scopeKey;
        private final String label;
        private final int variableIndex;
        private final int count;

        private TreeRow(boolean groupRow, String scopeKey, String label, int variableIndex, int count) {
            this.groupRow = groupRow;
            this.scopeKey = scopeKey == null ? "sequence" : scopeKey;
            this.label = label == null ? "" : label;
            this.variableIndex = variableIndex;
            this.count = count;
        }
    }

    private static final class SourcePopupVariable {
        private final String name;
        private final String value;
        private final boolean hasValue;

        private SourcePopupVariable(String name, String value, boolean hasValue) {
            this.name = name == null ? "" : name;
            this.value = value == null ? "" : value;
            this.hasValue = hasValue;
        }
    }

    private static final class SourcePopupState {
        private final ActionVariableRegistry.VariableSource source;
        private final String variablePrefix;
        private final String sessionSummary;
        private final String eventSummary;
        private final boolean hasMatchedEvent;
        private final boolean hasMatchedValues;
        private final List<SourcePopupVariable> variables;

        private SourcePopupState(ActionVariableRegistry.VariableSource source, String variablePrefix,
                String sessionSummary, String eventSummary, boolean hasMatchedEvent, boolean hasMatchedValues,
                List<SourcePopupVariable> variables) {
            this.source = source;
            this.variablePrefix = variablePrefix == null ? "" : variablePrefix;
            this.sessionSummary = sessionSummary == null ? "" : sessionSummary;
            this.eventSummary = eventSummary == null ? "" : eventSummary;
            this.hasMatchedEvent = hasMatchedEvent;
            this.hasMatchedValues = hasMatchedValues;
            this.variables = variables == null ? new ArrayList<SourcePopupVariable>() : variables;
        }
    }

    private static final class SourcePopupRow {
        private final SourcePopupVariable variable;
        private final List<String> valueLines;
        private final int offsetY;
        private final int height;

        private SourcePopupRow(SourcePopupVariable variable, List<String> valueLines, int offsetY, int height) {
            this.variable = variable;
            this.valueLines = valueLines == null ? new ArrayList<String>() : valueLines;
            this.offsetY = offsetY;
            this.height = height;
        }
    }

    private static final class SourcePopupLayout {
        private final List<String> noteLines;
        private final List<SourcePopupRow> rows;
        private final int contentHeight;

        private SourcePopupLayout(List<String> noteLines, List<SourcePopupRow> rows, int contentHeight) {
            this.noteLines = noteLines == null ? new ArrayList<String>() : noteLines;
            this.rows = rows == null ? new ArrayList<SourcePopupRow>() : rows;
            this.contentHeight = contentHeight;
        }
    }

    private static final class RowHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private RowHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private Host host;
    private List<ActionVariableRegistry.VariableEntry> variables = new ArrayList<ActionVariableRegistry.VariableEntry>();
    private final List<TreeRow> treeRows = new ArrayList<TreeRow>();
    private final Set<Integer> selectedIndices = new LinkedHashSet<Integer>();
    private final Set<String> collapsedScopeKeys = new LinkedHashSet<String>();
    private final List<RowHit> treeHits = new ArrayList<RowHit>();
    private final List<RowHit> sourceHits = new ArrayList<RowHit>();
    private Map<String, Object> globalValues = Collections.emptyMap();
    private Map<String, String> latestPreview = Collections.emptyMap();
    private int focusedIndex = -1;
    private double splitRatio = 0.34D;
    private boolean layoutPreferencesLoaded;
    private boolean draggingDivider;
    private final ModernHoverScrollbar treeScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar sourceScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar sourcePopupScrollbar = new ModernHoverScrollbar();
    private int treeScroll;
    private int treeMaxScroll;
    private int sourceScroll;
    private int sourceMaxScroll;
    private String lastSearch = "";
    private long variableModelSignature = Long.MIN_VALUE;
    private ModernMainLayout.Rect overlayBounds;
    private ModernMainLayout.Rect treeBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect renameBounds;
    private ModernMainLayout.Rect sourceViewport;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect renameApplyBounds;
    private ModernMainLayout.Rect clearBounds;
    private SourcePopupState sourcePopupState;
    private int sourcePopupScroll;
    private int maxSourcePopupScroll;
    private ModernMainLayout.Rect sourcePopupBounds;
    private ModernMainLayout.Rect sourcePopupViewport;
    private ModernMainLayout.Rect sourcePopupCloseBounds;
    private SourcePopupLayout sourcePopupLayout;

    void bind(Host host) {
        this.host = host;
    }

    void open() {
        sourcePopupState = null;
        sourcePopupScroll = 0;
        treeScroll = 0;
        sourceScroll = 0;
        lastSearch = "";
        variableModelSignature = Long.MIN_VALUE;
        if (host != null) {
            host.setFieldTextIfUnchanged("variable.search", "");
        }
        reload(true);
    }

    boolean isPopupOpen() {
        return sourcePopupState != null;
    }

    boolean handleEscape() {
        if (sourcePopupState != null) {
            closeSourcePopup();
            return true;
        }
        return false;
    }

    boolean handleEnter() {
        if (sourcePopupState != null) {
            return true;
        }
        applyRename();
        return true;
    }

    void draw(FontRenderer font, ModernMainLayout.Rect content, ModernMainLayout.Rect overlay, int mouseX, int mouseY) {
        if (!layoutPreferencesLoaded) {
            splitRatio = MainUiLayoutManager.getModernSplitRatio("path.action_variables.tree", splitRatio);
            layoutPreferencesLoaded = true;
        }
        overlayBounds = overlay;
        if (host == null || content == null || font == null) {
            return;
        }
        refreshSnapshots();
        String search = host.fieldText("variable.search");
        long currentSignature = variableModelSignature(host.sequences());
        if (!safe(search).equals(lastSearch) || currentSignature != variableModelSignature) {
            lastSearch = safe(search);
            treeScroll = 0;
            rebuildFromCurrent(true);
            variableModelSignature = currentSignature;
        }

        int gap = 8;
        int footerHeight = 22;
        int headerHeight = 18;
        int bodyTop = content.y + headerHeight;
        int bodyHeight = Math.max(1, content.height - headerHeight - footerHeight - 8);
        int total = Math.max(2, content.width - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(total, splitRatio, 190, 260, 150, 180);
        splitRatio = split.ratio;
        treeBounds = new ModernMainLayout.Rect(content.x, bodyTop, split.firstWidth, bodyHeight);
        detailBounds = new ModernMainLayout.Rect(treeBounds.right() + gap, bodyTop, split.secondWidth, bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(treeBounds.x, treeBounds.width, gap, bodyTop, bodyHeight);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
        if (dividerBounds.contains(mouseX, mouseY)) {
            host.setTooltip("gui.modern.path.var.u001");
        }

        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.tree", String.valueOf(variables.size())), treeBounds.x, content.y + 2,
                ModernUiRenderer.SUBTLE_TEXT, treeBounds.width);
        ActionVariableRegistry.VariableEntry focused = getFocusedEntry();
        ModernUiRenderer.drawText(font, focused == null ? "gui.modern.path.var.u002" : tr("gui.modern.path.var.fmt.source_file", focused.getVariableName()),
                detailBounds.x, content.y + 2, ModernUiRenderer.SUBTLE_TEXT, detailBounds.width);

        drawTreePane(font, mouseX, mouseY);
        drawDetailPane(font, mouseX, mouseY);

        int buttonY = content.bottom() - footerHeight;
        int buttonWidth = Math.max(56, Math.min(92, (content.width - 10) / 3));
        reloadBounds = new ModernMainLayout.Rect(content.x, buttonY, buttonWidth, 18);
        renameApplyBounds = new ModernMainLayout.Rect(reloadBounds.right() + 5, buttonY, buttonWidth, 18);
        clearBounds = new ModernMainLayout.Rect(renameApplyBounds.right() + 5, buttonY, buttonWidth, 18);
        host.drawButton(font, reloadBounds, "gui.modern.path.var.u003", false, false, true, mouseX, mouseY);
        host.drawButton(font, renameApplyBounds, "gui.modern.path.var.u004", true, false, focused != null, mouseX, mouseY);
        host.drawButton(font, clearBounds, "gui.modern.path.var.u005", false, true, !selectedIndices.isEmpty(), mouseX, mouseY);

        if (sourcePopupState != null) {
            drawSourcePopup(font, mouseX, mouseY);
        } else {
            sourcePopupScrollbar.idle();
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (sourcePopupState != null) {
            if (sourcePopupScrollbar.beginDrag(mouseX, mouseY)) {
                return true;
            }
            return handlePopupClick(mouseX, mouseY);
        }
        if (treeScrollbar.beginDrag(mouseX, mouseY) || sourceScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        for (RowHit hit : treeHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                handleTreeClick(hit.index);
                return true;
            }
        }
        for (RowHit hit : sourceHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                ActionVariableRegistry.VariableEntry entry = getFocusedEntry();
                if (entry != null && hit.index >= 0 && hit.index < entry.getSources().size()) {
                    openSourcePopup(entry.getSources().get(hit.index));
                }
                return true;
            }
        }
        if (reloadBounds != null && reloadBounds.contains(mouseX, mouseY)) {
            reload(true);
            host.status(tr("gui.modern.path.var.fmt.refreshed", String.valueOf(variables.size())));
            return true;
        }
        if (renameApplyBounds != null && renameApplyBounds.contains(mouseX, mouseY)) {
            applyRename();
            return true;
        }
        if (clearBounds != null && clearBounds.contains(mouseX, mouseY)) {
            clearSelected();
            return true;
        }
        return true;
    }

    boolean mouseClickMove(int mouseX, int mouseY) {
        if (treeScrollbar.isDragging()) {
            treeScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (sourceScrollbar.isDragging()) {
            sourceScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (sourcePopupScrollbar.isDragging()) {
            sourcePopupScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (!draggingDivider || treeBounds == null || detailBounds == null || overlayBounds == null) {
            return draggingDivider;
        }
        int total = Math.max(2, treeBounds.width + 8 + detailBounds.width);
        ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total, mouseX - treeBounds.x, 190, 260,
                150, 180);
        splitRatio = split.ratio;
        return true;
    }

    boolean mouseReleased() {
        boolean handled = draggingDivider || treeScrollbar.isDragging() || sourceScrollbar.isDragging()
                || sourcePopupScrollbar.isDragging();
        if (draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("path.action_variables.tree", splitRatio);
        }
        draggingDivider = false;
        treeScrollbar.endDrag();
        sourceScrollbar.endDrag();
        sourcePopupScrollbar.endDrag();
        return handled;
    }

    boolean mouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        int delta = wheel > 0 ? -VARIABLE_ROW_HEIGHT : VARIABLE_ROW_HEIGHT;
        if (sourcePopupState != null && sourcePopupViewport != null && sourcePopupViewport.contains(mouseX, mouseY)) {
            sourcePopupScroll = clamp(sourcePopupScroll + (wheel > 0 ? -POPUP_SCROLL_STEP : POPUP_SCROLL_STEP), 0,
                    maxSourcePopupScroll);
            return true;
        }
        if (sourcePopupState != null) {
            return true;
        }
        if (treeBounds != null && treeBounds.contains(mouseX, mouseY)) {
            treeScroll = clamp(treeScroll + delta, 0, treeMaxScroll);
            return true;
        }
        if (detailBounds != null && detailBounds.contains(mouseX, mouseY)) {
            sourceScroll = clamp(sourceScroll + (wheel > 0 ? -(SOURCE_CARD_HEIGHT + SOURCE_CARD_GAP)
                    : SOURCE_CARD_HEIGHT + SOURCE_CARD_GAP), 0, sourceMaxScroll);
            return true;
        }
        return false;
    }

    private void drawTreePane(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(treeBounds.x, treeBounds.y, treeBounds.width, treeBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        searchBounds = new ModernMainLayout.Rect(treeBounds.x + 8, treeBounds.y + 8, Math.max(1, treeBounds.width - 16),
                18);
        host.drawField(font, "variable.search", searchBounds, true, "gui.modern.path.var.u006", mouseX, mouseY);

        ModernMainLayout.Rect list = new ModernMainLayout.Rect(treeBounds.x + 6, treeBounds.y + 32,
                Math.max(1, treeBounds.width - 12), Math.max(1, treeBounds.height - 40));
        treeHits.clear();
        int contentHeight = 0;
        for (TreeRow row : treeRows) {
            contentHeight += (row.groupRow ? GROUP_ROW_HEIGHT : VARIABLE_ROW_HEIGHT) + ModernTreeGuide.GAP;
        }
        treeMaxScroll = Math.max(0, contentHeight - list.height);
        treeScroll = clamp(treeScroll, 0, treeMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y - treeScroll;
        ModernMainLayout.Rect groupBounds = null;
        for (int i = 0; i < treeRows.size(); i++) {
            TreeRow row = treeRows.get(i);
            int height = row.groupRow ? GROUP_ROW_HEIGHT : VARIABLE_ROW_HEIGHT;
            ModernMainLayout.Rect bounds = ModernTreeGuide.row(list.x, ModernHoverScrollbar.contentWidth(list.width), y, height - 2, row.groupRow ? 0 : 1);
            if (bounds.bottom() > list.y && bounds.y < list.bottom()) {
                if (!row.groupRow) ModernTreeGuide.drawChild(list.x, 0, groupBounds, bounds);
                drawTreeRow(font, row, bounds, mouseX, mouseY);
                treeHits.add(new RowHit(i, bounds));
            }
            if (row.groupRow) groupBounds = bounds;
            y = ModernTreeGuide.nextY(y, height);
        }
        ModernUiRenderer.endClip();
        if (treeRows.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.var.u007", list.x + 8, list.y + 18, ModernUiRenderer.MUTED_TEXT,
                    Math.max(40, list.width - 16));
            ModernUiRenderer.drawText(font, "gui.modern.path.var.u008", list.x + 8, list.y + 34,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, list.width - 16));
        }
        drawScrollBar(treeScrollbar, list, treeScroll, treeMaxScroll, contentHeight, mouseX, mouseY,
                value -> treeScroll = value);
    }

    private void drawTreeRow(FontRenderer font, TreeRow row, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (row.groupRow) {
            boolean collapsed = collapsedScopeKeys.contains(row.scopeKey);
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, collapsed ? "▶ " + row.label : "▼ " + row.label, bounds.x + 8, bounds.y + 7,
                    ModernUiRenderer.TEXT, Math.max(40, bounds.width - 40));
            String count = String.valueOf(row.count);
            ModernUiRenderer.drawText(font, count, bounds.right() - 10 - font.getStringWidth(count), bounds.y + 7,
                    ModernUiRenderer.ACCENT, 24);
            return;
        }
        ActionVariableRegistry.VariableEntry entry = row.variableIndex >= 0 && row.variableIndex < variables.size()
                ? variables.get(row.variableIndex) : null;
        if (entry == null) {
            return;
        }
        boolean focused = row.variableIndex == focusedIndex;
        boolean selected = selectedIndices.contains(Integer.valueOf(row.variableIndex));
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                focused || selected ? ModernUiRenderer.SURFACE_PRESSED
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                focused ? ModernUiRenderer.ACCENT
                        : selected ? ModernUiRenderer.ACCENT_DIM
                                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        boolean hasValue = hasDisplayValue(entry);
        ModernUiRenderer.drawRoundedRect(bounds.x + 8, bounds.y + 8, 6, 6, 3,
                hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING);
        String name = ActionVariableRegistry.extractBaseName(entry.getVariableName());
        ModernUiRenderer.drawText(font, name, bounds.x + 18, bounds.y + 4, ModernUiRenderer.TEXT,
                Math.max(40, bounds.width - 40));
        String preview = displayValuePreview(entry);
        ModernUiRenderer.drawText(font, preview, bounds.x + 18, bounds.y + 17,
                hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, Math.max(40, bounds.width - 40));
        String count = String.valueOf(entry.getSources().size());
        ModernUiRenderer.drawText(font, count, bounds.right() - 10 - font.getStringWidth(count), bounds.y + 4,
                ModernUiRenderer.SUBTLE_TEXT, 24);
        if (hovered) {
            host.setTooltip(tr("gui.modern.path.var.fmt.tooltip", entry.getVariableName(),
                    ActionVariableRegistry.scopeKeyToDisplay(entry.getScopeKey()),
                    String.valueOf(entry.getSources().size()), preview));
        }
    }

    private void drawDetailPane(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(detailBounds.x, detailBounds.y, detailBounds.width, detailBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ActionVariableRegistry.VariableEntry entry = getFocusedEntry();
        if (entry == null) {
            ModernUiRenderer.drawText(font, "gui.modern.path.var.u009", detailBounds.x + 12,
                    detailBounds.y + 16, ModernUiRenderer.MUTED_TEXT, Math.max(60, detailBounds.width - 24));
            sourceHits.clear();
            sourceMaxScroll = 0;
            host.setFieldTextIfUnchanged("variable.rename", "");
            return;
        }

        int x = detailBounds.x + 10;
        int width = Math.max(1, detailBounds.width - 20);
        ModernUiRenderer.drawText(font, entry.getVariableName(), x, detailBounds.y + 10, ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.detail_meta",
                ActionVariableRegistry.scopeKeyToDisplay(entry.getScopeKey()),
                String.valueOf(entry.getSources().size()), String.valueOf(entry.getCustomSourceCount())), x, detailBounds.y + 24,
                ModernUiRenderer.SUBTLE_TEXT, width);

        boolean hasValue = hasDisplayValue(entry);
        ModernMainLayout.Rect valueCard = new ModernMainLayout.Rect(x, detailBounds.y + 42, width, 38);
        ModernUiRenderer.drawSubtlePanel(valueCard.x, valueCard.y, valueCard.width, valueCard.height, 4,
                ModernUiRenderer.SHELL, hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(valueCard.x, valueCard.y, 3, valueCard.height, 2,
                hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING);
        ModernUiRenderer.drawText(font, "gui.modern.path.var.u010", valueCard.x + 10, valueCard.y + 5, ModernUiRenderer.MUTED_TEXT,
                width - 16);
        ModernUiRenderer.drawText(font, displayValuePreview(entry), valueCard.x + 10, valueCard.y + 18,
                hasValue ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, width - 16);
        if (valueCard.contains(mouseX, mouseY)) {
            host.setTooltip(valueHint(entry));
        }

        host.setFieldTextIfUnchanged("variable.rename", ActionVariableRegistry.extractBaseName(entry.getVariableName()));
        renameBounds = new ModernMainLayout.Rect(x, valueCard.bottom() + 8, width, 18);
        host.drawField(font, "variable.rename", renameBounds, true, "gui.modern.path.var.u011", mouseX, mouseY);

        ModernUiRenderer.drawText(font, "gui.modern.path.var.u012", x, renameBounds.bottom() + 8,
                ModernUiRenderer.SUBTLE_TEXT, width);
        sourceViewport = new ModernMainLayout.Rect(x, renameBounds.bottom() + 22, width,
                Math.max(1, detailBounds.bottom() - (renameBounds.bottom() + 30)));
        drawSourceCards(font, entry, mouseX, mouseY);
    }

    private void drawSourceCards(FontRenderer font, ActionVariableRegistry.VariableEntry entry, int mouseX, int mouseY) {
        sourceHits.clear();
        List<ActionVariableRegistry.VariableSource> sources = entry.getSources();
        if (sources.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.var.u013", sourceViewport.x + 4, sourceViewport.y + 8,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, sourceViewport.width - 8));
            sourceMaxScroll = 0;
            return;
        }
        int cols = sourceViewport.width >= 520 ? 2 : 1;
        int cardWidth = cols <= 1 ? ModernHoverScrollbar.contentWidth(sourceViewport.width)
                : (ModernHoverScrollbar.contentWidth(sourceViewport.width) - SOURCE_CARD_GAP) / cols;
        int rows = (sources.size() + cols - 1) / cols;
        int contentHeight = rows * (SOURCE_CARD_HEIGHT + SOURCE_CARD_GAP) - SOURCE_CARD_GAP;
        sourceMaxScroll = Math.max(0, contentHeight - sourceViewport.height);
        sourceScroll = clamp(sourceScroll, 0, sourceMaxScroll);
        ModernUiRenderer.beginClip(sourceViewport);
        for (int i = 0; i < sources.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int cardX = sourceViewport.x + col * (cardWidth + SOURCE_CARD_GAP);
            int cardY = sourceViewport.y + row * (SOURCE_CARD_HEIGHT + SOURCE_CARD_GAP) - sourceScroll;
            ModernMainLayout.Rect card = new ModernMainLayout.Rect(cardX, cardY, cardWidth, SOURCE_CARD_HEIGHT);
            if (card.bottom() > sourceViewport.y && card.y < sourceViewport.bottom()) {
                drawSourceCard(font, sources.get(i), i, card, mouseX, mouseY);
                sourceHits.add(new RowHit(i, card));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollBar(sourceScrollbar, sourceViewport, sourceScroll, sourceMaxScroll, contentHeight, mouseX, mouseY,
                value -> sourceScroll = value);
    }

    private void drawSourceCard(FontRenderer font, ActionVariableRegistry.VariableSource source, int index,
            ModernMainLayout.Rect card, int mouseX, int mouseY) {
        boolean hovered = card.contains(mouseX, mouseY);
        boolean editable = source != null && source.isCustomSequence();
        int accent = editable ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING;
        ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                hovered ? accent : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(card.x, card.y, 3, card.height, 2, accent);
        String seqName = safe(source.getSequenceName()).trim();
        if (seqName.isEmpty()) {
            seqName = "gui.modern.path.var.u014";
        }
        ModernUiRenderer.drawText(font, seqName, card.x + 10, card.y + 7, ModernUiRenderer.TEXT,
                Math.max(40, card.width - 90));
        String type = safe(source.getActionType());
        ModernUiRenderer.drawText(font, type, card.right() - 10 - font.getStringWidth(font.trimStringToWidth(type, 70)),
                card.y + 7, accent, 70);
        String category = safe(source.getCategory());
        String sub = safe(source.getSubCategory());
        String categoryLine = category + (sub.trim().isEmpty() ? "" : " / " + sub);
        ModernUiRenderer.drawText(font, categoryLine, card.x + 10, card.y + 22, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(40, card.width - 20));
        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.step_action_field",
                String.valueOf(source.getStepIndex() + 1), String.valueOf(source.getActionIndex() + 1),
                safe(source.getParamKey())), card.x + 10, card.y + 36,
                ModernUiRenderer.MUTED_TEXT, Math.max(40, card.width - 20));
        String canonical = ActionVariableRegistry.buildCanonicalVariableName(
                ActionVariableRegistry.extractScopeKey(source.getVariableName()),
                ActionVariableRegistry.extractBaseName(source.getVariableName()));
        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.var_name", canonical), card.x + 10, card.y + 50, ModernUiRenderer.TEXT,
                Math.max(40, card.width - 20));
        String footer = editable ? "gui.modern.path.var.u015" : "gui.modern.path.var.u016";
        ModernUiRenderer.drawText(font, footer, card.x + 10, card.y + card.height - 14,
                editable ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING, Math.max(40, card.width - 36));
        String indexLabel = "#" + (index + 1);
        ModernUiRenderer.drawText(font, indexLabel, card.right() - 10 - font.getStringWidth(indexLabel),
                card.y + card.height - 14, ModernUiRenderer.MUTED_TEXT, 24);
        if (hovered) {
            host.setTooltip(footer);
        }
    }

    private void drawSourcePopup(FontRenderer font, int mouseX, int mouseY) {
        if (overlayBounds == null || sourcePopupState == null) {
            return;
        }
        ModernUiRenderer.drawBackdropOverlay(overlayBounds, 0xB80A1016);
        int width = Math.min(520, Math.max(280, overlayBounds.width - 72));
        int height = Math.min(420, Math.max(220, overlayBounds.height - 48));
        sourcePopupBounds = new ModernMainLayout.Rect(overlayBounds.x + (overlayBounds.width - width) / 2,
                overlayBounds.y + (overlayBounds.height - height) / 2, width, height);
        ModernUiRenderer.drawPanel(sourcePopupBounds.x, sourcePopupBounds.y, width, height, 8,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.child_view", sourcePopupState.variablePrefix), sourcePopupBounds.x + 14,
                sourcePopupBounds.y + 10, ModernUiRenderer.TEXT, width - 48);
        sourcePopupCloseBounds = new ModernMainLayout.Rect(sourcePopupBounds.right() - 26, sourcePopupBounds.y + 8, 16,
                16);
        boolean closeHovered = sourcePopupCloseBounds.contains(mouseX, mouseY);
        if (closeHovered) {
            ModernUiRenderer.drawRoundedRect(sourcePopupCloseBounds.x - 2, sourcePopupCloseBounds.y - 2, 18, 18, 4,
                    ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawCloseIcon(sourcePopupCloseBounds.x + 3, sourcePopupCloseBounds.y + 3,
                closeHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);

        ActionVariableRegistry.VariableSource source = sourcePopupState.source;
        ModernUiRenderer.drawText(font, tr("gui.modern.path.var.fmt.source_line", safe(source.getSequenceName()),
                String.valueOf(source.getStepIndex() + 1), String.valueOf(source.getActionIndex() + 1),
                safe(source.getActionType())), sourcePopupBounds.x + 14, sourcePopupBounds.y + 28,
                ModernUiRenderer.SUBTLE_TEXT, width - 28);
        ModernUiRenderer.drawText(font, sourcePopupState.sessionSummary, sourcePopupBounds.x + 14,
                sourcePopupBounds.y + 42, ModernUiRenderer.MUTED_TEXT, width - 28);
        ModernUiRenderer.drawText(font, sourcePopupState.eventSummary, sourcePopupBounds.x + 14,
                sourcePopupBounds.y + 54, ModernUiRenderer.MUTED_TEXT, width - 28);

        sourcePopupViewport = new ModernMainLayout.Rect(sourcePopupBounds.x + 12, sourcePopupBounds.y + 72,
                width - 24, Math.max(40, height - 102));
        ModernUiRenderer.drawSubtlePanel(sourcePopupViewport.x, sourcePopupViewport.y, sourcePopupViewport.width,
                sourcePopupViewport.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        sourcePopupLayout = buildSourcePopupLayout(font, Math.max(120, ModernHoverScrollbar.contentWidth(sourcePopupViewport.width - 8)));
        maxSourcePopupScroll = Math.max(0, sourcePopupLayout.contentHeight - Math.max(1, sourcePopupViewport.height - 8));
        sourcePopupScroll = clamp(sourcePopupScroll, 0, maxSourcePopupScroll);

        ModernUiRenderer.beginClip(sourcePopupViewport);
        int contentX = sourcePopupViewport.x + 6;
        int contentY = sourcePopupViewport.y + 6 - sourcePopupScroll;
        int noteY = contentY;
        for (String line : sourcePopupLayout.noteLines) {
            ModernUiRenderer.drawText(font, line, contentX, noteY, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(40, ModernHoverScrollbar.contentWidth(sourcePopupViewport.width - 8)));
            noteY += 11;
        }
        for (SourcePopupRow row : sourcePopupLayout.rows) {
            int rowY = contentY + row.offsetY;
            if (rowY + row.height < sourcePopupViewport.y || rowY > sourcePopupViewport.bottom()) {
                continue;
            }
            drawSourcePopupRow(font, row, contentX, rowY, ModernHoverScrollbar.contentWidth(sourcePopupViewport.width - 8));
        }
        ModernUiRenderer.endClip();
        drawScrollBar(sourcePopupScrollbar, sourcePopupViewport, sourcePopupScroll, maxSourcePopupScroll,
                sourcePopupLayout.contentHeight, mouseX, mouseY, value -> sourcePopupScroll = value);
        ModernUiRenderer.drawText(font, "gui.modern.path.var.u017",
                sourcePopupBounds.x + 14, sourcePopupBounds.bottom() - 16, ModernUiRenderer.MUTED_TEXT, width - 28);
    }

    private void drawSourcePopupRow(FontRenderer font, SourcePopupRow row, int x, int y, int width) {
        int accent = row.variable.hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER;
        ModernUiRenderer.drawSubtlePanel(x, y, width, row.height, 5, ModernUiRenderer.SHELL, accent);
        ModernUiRenderer.drawRoundedRect(x, y, 3, row.height, 2, accent);
        ModernUiRenderer.drawText(font, row.variable.name, x + 10, y + 6, ModernUiRenderer.TEXT,
                Math.max(60, width - 70));
        String state = row.variable.hasValue ? "gui.modern.path.var.u018" : "gui.modern.path.var.u019";
        ModernUiRenderer.drawText(font, state, x + width - 10 - font.getStringWidth(tr(state)), y + 6,
                row.variable.hasValue ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT, 48);
        int lineY = y + 20;
        for (String line : row.valueLines) {
            ModernUiRenderer.drawText(font, line, x + 10, lineY,
                    row.variable.hasValue ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                    Math.max(40, width - 20));
            lineY += 11;
        }
    }

    private SourcePopupLayout buildSourcePopupLayout(FontRenderer font, int wrapWidth) {
        if (sourcePopupState == null) {
            return new SourcePopupLayout(new ArrayList<String>(), new ArrayList<SourcePopupRow>(), 0);
        }
        String note;
        if (!sourcePopupState.hasMatchedEvent) {
            note = "gui.modern.path.var.u020";
        } else if (sourcePopupState.hasMatchedValues) {
            note = "gui.modern.path.var.u021";
        } else {
            note = "gui.modern.path.var.u022";
        }
        List<String> noteLines = wrapText(font, note, Math.max(120, wrapWidth), 3);
        List<SourcePopupRow> rows = new ArrayList<SourcePopupRow>();
        int offsetY = noteLines.isEmpty() ? 0 : noteLines.size() * 11 + 10;
        for (SourcePopupVariable variable : sourcePopupState.variables) {
            String displayValue = variable.hasValue
                    ? (safe(variable.value).trim().isEmpty() ? "gui.modern.path.var.u023" : variable.value)
                    : "gui.modern.path.var.u024";
            List<String> valueLines = wrapText(font, tr("gui.modern.path.var.fmt.value", displayValue), Math.max(100, wrapWidth - 18), 8);
            int height = Math.max(42, 22 + valueLines.size() * 11 + 8);
            rows.add(new SourcePopupRow(variable, valueLines, offsetY, height));
            offsetY += height + 8;
        }
        int contentHeight = offsetY;
        if (!rows.isEmpty()) {
            contentHeight -= 8;
        }
        return new SourcePopupLayout(noteLines, rows, Math.max(contentHeight, noteLines.size() * 11));
    }

    private boolean handlePopupClick(int mouseX, int mouseY) {
        if (sourcePopupCloseBounds != null && sourcePopupCloseBounds.contains(mouseX, mouseY)) {
            closeSourcePopup();
            return true;
        }
        if (sourcePopupBounds != null && sourcePopupBounds.contains(mouseX, mouseY)) {
            return handleSourcePopupCopyClick(mouseX, mouseY);
        }
        closeSourcePopup();
        return true;
    }

    private boolean handleSourcePopupCopyClick(int mouseX, int mouseY) {
        if (sourcePopupState == null || sourcePopupViewport == null || sourcePopupLayout == null) {
            return true;
        }
        if (!sourcePopupViewport.contains(mouseX, mouseY)) {
            return true;
        }
        int contentX = sourcePopupViewport.x + 6;
        int contentY = sourcePopupViewport.y + 6 - sourcePopupScroll;
        int contentWidth = ModernHoverScrollbar.contentWidth(sourcePopupViewport.width - 8);
        for (SourcePopupRow row : sourcePopupLayout.rows) {
            int rowY = contentY + row.offsetY;
            ModernMainLayout.Rect rowBounds = new ModernMainLayout.Rect(contentX, rowY, contentWidth, row.height);
            if (!rowBounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (mouseY <= rowY + 18) {
                GuiScreen.setClipboardString(row.variable.name);
                host.status(tr("gui.modern.path.var.fmt.copied_name", row.variable.name));
                return true;
            }
            if (!row.variable.hasValue) {
                host.status("gui.modern.path.var.u025");
                return true;
            }
            GuiScreen.setClipboardString(safe(row.variable.value));
            host.status(safe(row.variable.value).trim().isEmpty() ? "gui.modern.path.var.u026"
                    : tr("gui.modern.path.var.fmt.copied_value", row.variable.value));
            return true;
        }
        return true;
    }

    private void handleTreeClick(int treeIndex) {
        if (treeIndex < 0 || treeIndex >= treeRows.size()) {
            return;
        }
        TreeRow row = treeRows.get(treeIndex);
        if (row.groupRow) {
            if (collapsedScopeKeys.contains(row.scopeKey)) {
                collapsedScopeKeys.remove(row.scopeKey);
            } else {
                collapsedScopeKeys.add(row.scopeKey);
            }
            rebuildTreeRows();
            return;
        }
        if (row.variableIndex < 0 || row.variableIndex >= variables.size()) {
            return;
        }
        if (host.isControlDown()) {
            if (selectedIndices.contains(Integer.valueOf(row.variableIndex))) {
                selectedIndices.remove(Integer.valueOf(row.variableIndex));
            } else {
                selectedIndices.add(Integer.valueOf(row.variableIndex));
            }
        } else {
            selectedIndices.clear();
            selectedIndices.add(Integer.valueOf(row.variableIndex));
        }
        focusedIndex = row.variableIndex;
        selectedIndices.add(Integer.valueOf(row.variableIndex));
        sourceScroll = 0;
        if (host != null) {
            host.setFieldTextIfUnchanged("variable.rename",
                    ActionVariableRegistry.extractBaseName(variables.get(focusedIndex).getVariableName()));
        }
    }

    private void applyRename() {
        ActionVariableRegistry.VariableEntry target = getFocusedEntry();
        if (target == null) {
            host.status("gui.modern.path.var.u027");
            return;
        }
        String typed = host.fieldText("variable.rename").trim();
        String base = ActionVariableRegistry.extractBaseName(typed);
        if (base.isEmpty()) {
            base = typed;
        }
        if (base.isEmpty()) {
            host.status("gui.modern.path.var.u028");
            return;
        }
        String renamed = ActionVariableRegistry.buildScopedVariableName(target.getScopeKey(), base);
        if (renamed.equals(target.getVariableName())) {
            host.status("gui.modern.path.var.u029");
            return;
        }
        host.pushHistory("rename-variable");
        int changed = ActionVariableRegistry.renameVariable(host.sequences(), target.getVariableName(), renamed);
        if (changed <= 0) {
            host.status("gui.modern.path.var.u030");
            return;
        }
        host.markDirty();
        host.refreshEditor();
        host.clearFocus();
        reload(false);
        focusVariableByName(renamed);
        host.status(tr("gui.modern.path.var.fmt.renamed", String.valueOf(changed)));
    }

    private void clearSelected() {
        if (selectedIndices.isEmpty()) {
            host.status("gui.modern.path.var.u031");
            return;
        }
        List<String> names = new ArrayList<String>();
        for (Integer index : selectedIndices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < variables.size()) {
                names.add(variables.get(index.intValue()).getVariableName());
            }
        }
        host.pushHistory("clear-variable");
        int removed = ActionVariableRegistry.clearVariables(host.sequences(), names);
        host.markDirty();
        host.refreshEditor();
        host.clearFocus();
        reload(false);
        host.status(tr("gui.modern.path.var.fmt.cleared", String.valueOf(removed)));
    }

    private void reload(boolean preserveSelection) {
        refreshSnapshots();
        rebuildFromCurrent(preserveSelection);
    }

    private void rebuildFromCurrent(boolean preserveSelection) {
        Set<String> selectedNames = preserveSelection ? getSelectedVariableNames() : Collections.<String>emptySet();
        String focusedName = preserveSelection ? getFocusedVariableName() : "";
        List<ActionVariableRegistry.VariableEntry> all = ActionVariableRegistry.collectVariables(
                host == null ? Collections.<PathSequence>emptyList() : host.sequences());
        all.sort(Comparator
                .comparing((ActionVariableRegistry.VariableEntry entry) -> ActionVariableRegistry
                        .extractScopeKey(entry.getVariableName()))
                .thenComparing(entry -> ActionVariableRegistry.extractBaseName(entry.getVariableName()).toLowerCase()));
        String keyword = PinyinSearchHelper.normalizeQuery(host == null ? "" : host.fieldText("variable.search"));
        variables = new ArrayList<ActionVariableRegistry.VariableEntry>();
        for (ActionVariableRegistry.VariableEntry entry : all) {
            String full = safe(entry.getVariableName());
            String base = safe(ActionVariableRegistry.extractBaseName(entry.getVariableName()));
            if (keyword.isEmpty() || PinyinSearchHelper.matchesNormalized(full + " " + base, keyword)) {
                variables.add(entry);
            }
        }
        selectedIndices.clear();
        focusedIndex = -1;
        for (int i = 0; i < variables.size(); i++) {
            String name = safe(variables.get(i).getVariableName());
            if (preserveSelection && selectedNames.contains(name)) {
                selectedIndices.add(Integer.valueOf(i));
            }
            if (focusedIndex < 0 && preserveSelection && !focusedName.isEmpty() && focusedName.equalsIgnoreCase(name)) {
                focusedIndex = i;
            }
        }
        if (focusedIndex < 0 && !variables.isEmpty()) {
            focusedIndex = 0;
        }
        if (focusedIndex >= 0 && focusedIndex < variables.size()) {
            selectedIndices.add(Integer.valueOf(focusedIndex));
        }
        rebuildTreeRows();
    }

    private long variableModelSignature(List<PathSequence> sequences) {
        long signature = 17L;
        if (sequences == null) {
            return signature;
        }
        for (PathSequence sequence : sequences) {
            if (sequence == null) {
                continue;
            }
            signature = signature * 31L + safe(sequence.getName()).hashCode();
            List<PathStep> steps = sequence.getSteps();
            if (steps == null) {
                continue;
            }
            for (PathStep step : steps) {
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (ActionData action : step.getActions()) {
                    if (action == null) {
                        continue;
                    }
                    signature = signature * 31L + safe(action.type).hashCode();
                    signature = signature * 31L + (action.params == null ? 0 : action.params.toString().hashCode());
                }
            }
        }
        return signature;
    }

    private void rebuildTreeRows() {
        treeRows.clear();
        Map<String, List<Integer>> grouped = new LinkedHashMap<String, List<Integer>>();
        for (String scope : SCOPE_ORDER) {
            grouped.put(scope, new ArrayList<Integer>());
        }
        for (int i = 0; i < variables.size(); i++) {
            String scope = ActionVariableRegistry.extractScopeKey(variables.get(i).getVariableName());
            if (!grouped.containsKey(scope)) {
                grouped.put(scope, new ArrayList<Integer>());
            }
            grouped.get(scope).add(Integer.valueOf(i));
        }
        for (String scope : SCOPE_ORDER) {
            appendScopeRows(scope, grouped.get(scope));
        }
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            if (!isKnownScope(entry.getKey())) {
                appendScopeRows(entry.getKey(), entry.getValue());
            }
        }
    }

    private void appendScopeRows(String scopeKey, List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return;
        }
        treeRows.add(new TreeRow(true, scopeKey, ActionVariableRegistry.scopeKeyToDisplay(scopeKey), -1,
                indexes.size()));
        if (collapsedScopeKeys.contains(scopeKey)) {
            return;
        }
        for (Integer index : indexes) {
            if (index != null && index.intValue() >= 0 && index.intValue() < variables.size()) {
                treeRows.add(new TreeRow(false, scopeKey,
                        ActionVariableRegistry.extractBaseName(variables.get(index.intValue()).getVariableName()),
                        index.intValue(), 0));
            }
        }
    }

    private void openSourcePopup(ActionVariableRegistry.VariableSource source) {
        if (source == null) {
            return;
        }
        sourcePopupState = buildSourcePopupState(source);
        sourcePopupScroll = 0;
        maxSourcePopupScroll = 0;
    }

    private void closeSourcePopup() {
        sourcePopupState = null;
        sourcePopupScroll = 0;
        maxSourcePopupScroll = 0;
        sourcePopupLayout = null;
    }

    private SourcePopupState buildSourcePopupState(ActionVariableRegistry.VariableSource source) {
        String prefix = ActionVariableRegistry.buildCanonicalVariableName(
                ActionVariableRegistry.extractScopeKey(source == null ? "" : source.getVariableName()),
                ActionVariableRegistry.extractBaseName(source == null ? "" : source.getVariableName()));
        LinkedHashSet<String> variableNames = new LinkedHashSet<String>(
                ActionVariableRegistry.collectProducedVariableNames(source));
        SessionSnapshot matchedSession = null;
        ExecutionEvent matchedEvent = null;
        List<SessionSnapshot> sessions = ExecutionLogManager.getSessionsSnapshot();
        for (int sessionIndex = sessions.size() - 1; sessionIndex >= 0; sessionIndex--) {
            SessionSnapshot session = sessions.get(sessionIndex);
            if (session == null || !safe(session.getSequenceName()).equalsIgnoreCase(safe(source.getSequenceName()))) {
                continue;
            }
            List<ExecutionEvent> events = session.getEvents();
            for (int i = events.size() - 1; i >= 0; i--) {
                ExecutionEvent event = events.get(i);
                if (event != null && event.getStepIndex() == source.getStepIndex()
                        && event.getActionIndex() == source.getActionIndex()) {
                    matchedSession = session;
                    matchedEvent = event;
                    break;
                }
            }
            if (matchedEvent != null) {
                break;
            }
        }
        LinkedHashMap<String, String> matchedValues = new LinkedHashMap<String, String>();
        if (matchedEvent != null) {
            for (Map.Entry<String, String> entry : matchedEvent.getVariablePreview().entrySet()) {
                if (matchesSourceVariableKey(entry.getKey(), prefix)) {
                    variableNames.add(entry.getKey());
                    matchedValues.put(entry.getKey(), safe(entry.getValue()));
                }
            }
        }
        if (variableNames.isEmpty() && !prefix.isEmpty()) {
            variableNames.add(prefix);
        }
        List<SourcePopupVariable> popupVariables = new ArrayList<SourcePopupVariable>();
        for (String variableName : variableNames) {
            boolean hasValue = matchedValues.containsKey(variableName);
            popupVariables.add(new SourcePopupVariable(variableName, hasValue ? matchedValues.get(variableName) : "",
                    hasValue));
        }
        String sessionSummary = matchedSession == null ? "gui.modern.path.var.u032"
                : tr("gui.modern.path.var.fmt.matched_log", matchedSession.buildSummary());
        String eventSummary = matchedEvent == null ? "gui.modern.path.var.u033"
                : tr("gui.modern.path.var.fmt.matched_event", String.valueOf(matchedEvent.getStepIndex() + 1),
                        String.valueOf(matchedEvent.getActionIndex() + 1), safe(matchedEvent.getType()).toUpperCase());
        return new SourcePopupState(source, prefix, sessionSummary, eventSummary, matchedEvent != null,
                !matchedValues.isEmpty(), popupVariables);
    }

    private void focusVariableByName(String variableName) {
        String normalized = safe(variableName).trim();
        if (normalized.isEmpty()) {
            return;
        }
        for (int i = 0; i < variables.size(); i++) {
            if (normalized.equalsIgnoreCase(safe(variables.get(i).getVariableName()).trim())) {
                collapsedScopeKeys.remove(ActionVariableRegistry.extractScopeKey(normalized));
                focusedIndex = i;
                selectedIndices.clear();
                selectedIndices.add(Integer.valueOf(i));
                sourceScroll = 0;
                rebuildTreeRows();
                return;
            }
        }
    }

    private void refreshSnapshots() {
        globalValues = ScopedRuntimeVariables.getGlobalScopeSnapshot();
        LinkedHashMap<String, String> preview = new LinkedHashMap<String, String>();
        for (SessionSnapshot session : ExecutionLogManager.getSessionsSnapshot()) {
            if (session == null) {
                continue;
            }
            for (ExecutionEvent event : session.getEvents()) {
                if (event == null) {
                    continue;
                }
                preview.putAll(event.getVariablePreview());
            }
        }
        latestPreview = preview;
    }

    private ActionVariableRegistry.VariableEntry getFocusedEntry() {
        if (focusedIndex < 0 || focusedIndex >= variables.size()) {
            return null;
        }
        return variables.get(focusedIndex);
    }

    private String getFocusedVariableName() {
        ActionVariableRegistry.VariableEntry entry = getFocusedEntry();
        return entry == null ? "" : safe(entry.getVariableName());
    }

    private Set<String> getSelectedVariableNames() {
        Set<String> names = new LinkedHashSet<String>();
        for (Integer index : selectedIndices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < variables.size()) {
                names.add(safe(variables.get(index.intValue()).getVariableName()));
            }
        }
        return names;
    }

    private boolean hasDisplayValue(ActionVariableRegistry.VariableEntry entry) {
        return !lookupDisplayValue(entry).isEmpty();
    }

    private String displayValuePreview(ActionVariableRegistry.VariableEntry entry) {
        String value = lookupDisplayValue(entry);
        return value.isEmpty() ? "gui.modern.path.var.u034" : value;
    }

    private String lookupDisplayValue(ActionVariableRegistry.VariableEntry entry) {
        if (entry == null) {
            return "";
        }
        String live = liveGlobalValue(entry.getVariableName());
        if (!live.isEmpty()) {
            return live;
        }
        String canonical = ActionVariableRegistry.buildCanonicalVariableName(entry.getScopeKey(),
                ActionVariableRegistry.extractBaseName(entry.getVariableName()));
        String exact = latestPreview.get(canonical);
        if (exact != null && !exact.trim().isEmpty()) {
            return exact;
        }
        String base = latestPreview.get(ActionVariableRegistry.extractBaseName(entry.getVariableName()));
        if (base != null && !base.trim().isEmpty()) {
            return base;
        }
        String prefix = canonical + "_";
        for (Map.Entry<String, String> preview : latestPreview.entrySet()) {
            if (safe(preview.getKey()).startsWith(prefix) && preview.getValue() != null
                    && !preview.getValue().trim().isEmpty()) {
                return preview.getKey() + " = " + preview.getValue();
            }
        }
        return "";
    }

    private String liveGlobalValue(String variableName) {
        String scopeKey = ActionVariableRegistry.extractScopeKey(variableName);
        if (!"global".equals(scopeKey) || globalValues == null) {
            return "";
        }
        Object value = globalValues.get(ActionVariableRegistry.extractBaseName(variableName));
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return text == null ? "" : text;
    }

    private String valueHint(ActionVariableRegistry.VariableEntry entry) {
        if (entry == null) {
            return "";
        }
        String live = liveGlobalValue(entry.getVariableName());
        if (!live.isEmpty()) {
            return tr("gui.modern.path.var.fmt.live", live);
        }
        String preview = lookupDisplayValue(entry);
        if (!preview.isEmpty()) {
            return tr("gui.modern.path.var.fmt.preview", preview);
        }
        String scope = ActionVariableRegistry.extractScopeKey(entry.getVariableName());
        if ("global".equals(scope)) {
            return "gui.modern.path.var.u037";
        }
        return tr("gui.modern.path.var.fmt.runtime_scope", ActionVariableRegistry.scopeKeyToDisplay(scope));
    }

    private boolean matchesSourceVariableKey(String key, String prefix) {
        String normalizedKey = safe(key).trim();
        String normalizedPrefix = safe(prefix).trim();
        if (normalizedKey.isEmpty() || normalizedPrefix.isEmpty()) {
            return false;
        }
        return normalizedKey.equals(normalizedPrefix) || normalizedKey.startsWith(normalizedPrefix + "_");
    }

    private boolean isKnownScope(String scopeKey) {
        for (String scope : SCOPE_ORDER) {
            if (scope.equalsIgnoreCase(safe(scopeKey))) {
                return true;
            }
        }
        return false;
    }

    private void drawScrollBar(ModernHoverScrollbar bar, ModernMainLayout.Rect rect, int scroll, int maxScroll,
            int contentHeight, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (rect == null || maxScroll <= 0 || rect.height <= 0 || contentHeight <= 0) {
            bar.idle();
            return;
        }
        bar.draw(rect, scroll, maxScroll, rect.height, Math.max(rect.height, contentHeight), mouseX, mouseY, setter);
    }

    private List<String> wrapText(FontRenderer font, String text, int width, int maxLines) {
        List<String> result = new ArrayList<String>();
        if (font == null) {
            result.add(safe(text));
            return result;
        }
        List<String> raw = font.listFormattedStringToWidth(safe(text), Math.max(20, width));
        if (raw == null || raw.isEmpty()) {
            result.add("");
            return result;
        }
        int limit = Math.max(1, maxLines);
        for (int i = 0; i < raw.size() && i < limit; i++) {
            String line = raw.get(i);
            if (i == limit - 1 && raw.size() > limit) {
                line = font.trimStringToWidth(line, Math.max(20, width - 6)) + "...";
            }
            result.add(line);
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
