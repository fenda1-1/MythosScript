package com.mythos.mythosScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mythos.mythosScriptMod.utils.guiinspect.GuiElementInspector;
import com.mythos.mythosScriptMod.utils.guiinspect.GuiInspectionManager;
import com.mythos.mythosScriptMod.gui.modern.core.ModernConfirmationState;
import com.mythos.mythosScriptMod.gui.MainUiLayoutManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import com.mythos.mythosScriptMod.gui.modern.form.ModernFormI18n;

/**
 * Embedded GUI inspection workbench for the modern main screen.
 *
 * <p>The capture manager remains the source of truth. This tab only owns the
 * selection, scrolling and presentation state that used to live in the
 * dedicated inspection screen.</p>
 */
public final class ModernGuiInspectorSettingsTab implements ModernSettingsTab {

    private static final int HEADER_HEIGHT = 44;
    private static final int TOOLBAR_HEIGHT = 30;
    private static final int SECTION_GAP = 8;
    private static final int HISTORY_ROW_HEIGHT = 40;
    private static final int ELEMENT_ROW_HEIGHT = 38;
    private static final int FLOAT_HEADER_HEIGHT = 22;
    private static final int FLOAT_LINE_HEIGHT = 10;
    private static final double MAX_LAYOUT_SCALE = 2.0D;

    private static final class Hit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private Hit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private final List<Hit> snapshotHits = new ArrayList<>();
    private final List<Hit> elementHits = new ArrayList<>();
    private final List<Hit> layoutHits = new ArrayList<>();
    private final ModernConfirmationState destructiveConfirmation = new ModernConfirmationState();

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect captureBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect modeBounds;
    private ModernMainLayout.Rect historyBounds;
    private ModernMainLayout.Rect historyClipBounds;
    private ModernMainLayout.Rect elementBounds;
    private ModernMainLayout.Rect elementClipBounds;
    private ModernMainLayout.Rect dividerBounds;
    private final ModernHoverScrollbar historyScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar elementScrollbar = new ModernHoverScrollbar();

    // floating element-info panel (real-layout mode)
    private ModernMainLayout.Rect floatBounds;
    private ModernMainLayout.Rect floatHeaderBounds;
    private ModernMainLayout.Rect floatToggleBounds;
    private ModernMainLayout.Rect floatCopyBounds;
    private ModernMainLayout.Rect floatCloseBounds;
    private int floatX = Integer.MIN_VALUE;
    private int floatY = Integer.MIN_VALUE;
    private boolean floatExpanded = true;
    private boolean draggingFloat;
    private int floatDragOffsetX;
    private int floatDragOffsetY;

    private int historyScroll;
    private int historyMaxScroll;
    private int elementScroll;
    private int elementMaxScroll;
    private int selectedSnapshotIndex = -1;
    private int selectedElementIndex = -1;
    private boolean realLayoutMode;
    private double historyRatio = 0.34D;
    private boolean draggingDivider;

    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private int lastDrawMouseX;
    private int lastDrawMouseY;
    private boolean layoutPreferencesLoaded;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (!layoutPreferencesLoaded) {
            historyRatio = MainUiLayoutManager.getModernSplitRatio("gui_inspector.history", historyRatio);
            layoutPreferencesLoaded = true;
        }
        // All values are live; the capture manager can change while this tab is open.
    }

    @Override
    public void updateScreen() {
        // The history is refreshed by GuiInspectionManager on the client tick.
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX,
            int mouseY) {
        contentBounds = requestedContentBounds;
        lastDrawMouseX = mouseX;
        lastDrawMouseY = mouseY;
        hoveredTooltip = "";
        snapshotHits.clear();
        elementHits.clear();
        layoutHits.clear();
        calculateLayout(requestedContentBounds);
        syncSelectionBounds();
        calculateFloatLayout(fontRenderer);

        // Content under the floating panel must not react to the pointer.
        boolean overFloat = contains(floatBounds, mouseX, mouseY);
        int mx = overFloat ? -10000 : mouseX;
        int my = overFloat ? -10000 : mouseY;

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer, mx, my);
        drawToolbar(fontRenderer, mx, my);
        drawHistory(fontRenderer, mx, my);
        if (realLayoutMode) {
            drawRealLayout(fontRenderer, mx, my);
        } else {
            drawElementList(fontRenderer, mx, my);
        }
        ModernSplitPane.drawVerticalDivider(dividerBounds, mx, my, draggingDivider);
        drawFloatPanel(fontRenderer, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }

        if (contains(floatBounds, mouseX, mouseY)) {
            if (contains(floatCloseBounds, mouseX, mouseY)) {
                selectedElementIndex = -1;
            } else if (contains(floatToggleBounds, mouseX, mouseY)) {
                floatExpanded = !floatExpanded;
            } else if (contains(floatCopyBounds, mouseX, mouseY)) {
                copySelectedPath();
            } else if (contains(floatHeaderBounds, mouseX, mouseY)) {
                draggingFloat = true;
                floatDragOffsetX = mouseX - floatBounds.x;
                floatDragOffsetY = mouseY - floatBounds.y;
            }
            return true;
        }

        if (contains(captureBounds, mouseX, mouseY)) {
            GuiInspectionManager.toggleCaptureEnabled();
            showStatus(GuiInspectionManager.isCaptureEnabled()
                    ? "gui.modern.inspect.u001"
                    : "gui.modern.inspect.u002");
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            if (!destructiveConfirmation.request("clear")) {
                showStatus("gui.modern.inspect.u003");
                return true;
            }
            GuiInspectionManager.clearHistory();
            selectedSnapshotIndex = -1;
            selectedElementIndex = -1;
            historyScroll = 0;
            elementScroll = 0;
            showStatus("gui.modern.inspect.u004");
            return true;
        }
        if (contains(copyBounds, mouseX, mouseY)) {
            copySelectedPath();
            return true;
        }
        if (contains(modeBounds, mouseX, mouseY)) {
            realLayoutMode = !realLayoutMode;
            showStatus(realLayoutMode ? "gui.modern.inspect.u050" : "gui.modern.inspect.u049");
            return true;
        }
        if (historyScrollbar.beginDrag(mouseX, mouseY)
                || (!realLayoutMode && elementScrollbar.beginDrag(mouseX, mouseY))) {
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }

        for (Hit hit : snapshotHits) {
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit.index != selectedSnapshotIndex) {
                selectedSnapshotIndex = hit.index;
                selectedElementIndex = -1;
                elementScroll = 0;
            }
            showStatus("gui.modern.inspect.u007");
            return true;
        }
        if (realLayoutMode) {
            Hit best = null;
            long bestArea = Long.MAX_VALUE;
            for (Hit hit : layoutHits) {
                if (!hit.bounds.contains(mouseX, mouseY)) {
                    continue;
                }
                long area = (long) hit.bounds.width * hit.bounds.height;
                if (area < bestArea) {
                    bestArea = area;
                    best = hit;
                }
            }
            if (best != null) {
                selectedElementIndex = best.index;
                floatExpanded = true;
                showStatus("gui.modern.inspect.u008");
            }
            return true;
        }
        for (Hit hit : elementHits) {
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            selectedElementIndex = hit.index;
            showStatus("gui.modern.inspect.u008");
            return true;
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        // Escape is intentionally handled by GuiModernMainScreen so it closes the active tab.
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && draggingFloat) {
            floatX = mouseX - floatDragOffsetX;
            floatY = mouseY - floatDragOffsetY;
            return true;
        }
        if (clickedMouseButton == 0 && historyScrollbar.isDragging()) {
            historyScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && elementScrollbar.isDragging()) {
            elementScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && draggingDivider && panelBounds != null) {
            int bodyWidth = Math.max(2, panelBounds.width - 24);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(bodyWidth - SECTION_GAP,
                    mouseX - panelBounds.x - 12 - SECTION_GAP / 2, 178, 250, 122, 160);
            historyRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && draggingFloat) {
            draggingFloat = false;
            return true;
        }
        if (state == 0 && (historyScrollbar.isDragging() || elementScrollbar.isDragging())) {
            historyScrollbar.endDrag();
            elementScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("gui_inspector.history", historyRatio);
            draggingDivider = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (historyScrollbar.isDragging() || elementScrollbar.isDragging() || draggingDivider || draggingFloat) {
            historyScrollbar.endDrag();
            elementScrollbar.endDrag();
            draggingDivider = false;
            draggingFloat = false;
            return true;
        }
        if (destructiveConfirmation.isPending("clear")) {
            destructiveConfirmation.clear();
            showStatus("gui.modern.inspect.u009");
            return true;
        }
        if (realLayoutMode && getSelectedElement() != null) {
            selectedElementIndex = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastDrawMouseX, lastDrawMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (contains(floatBounds, mouseX, mouseY)) {
            return true;
        }
        if (contains(historyClipBounds, mouseX, mouseY) && historyMaxScroll > 0) {
            historyScroll = clamp(historyScroll + (wheel > 0 ? -1 : 1), 0, historyMaxScroll);
            return true;
        }
        if (!realLayoutMode && contains(elementClipBounds, mouseX, mouseY) && elementMaxScroll > 0) {
            elementScroll = clamp(elementScroll + (wheel > 0 ? -1 : 1), 0, elementMaxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        // Capture history and selection are live state, not unsaved editor drafts.
        destructiveConfirmation.clear();
        historyScrollbar.endDrag();
        elementScrollbar.endDrag();
        draggingFloat = false;
    }

    private void calculateLayout(ModernMainLayout.Rect requestedBounds) {
        ModernMainLayout.Rect source = requestedBounds == null
                ? new ModernMainLayout.Rect(0, 0, 1, 1)
                : requestedBounds;
        int insetX = Math.max(7, Math.min(12, source.width / 18));
        int insetY = Math.max(6, Math.min(9, source.height / 16));
        panelBounds = new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));

        int bodyY = panelBounds.y + HEADER_HEIGHT + TOOLBAR_HEIGHT + 16;
        int bodyHeight = Math.max(1, panelBounds.bottom() - bodyY - 9);
        int gap = SECTION_GAP;
        int bodyWidth = Math.max(1, panelBounds.width - 24);
        int splitTotal = Math.max(2, bodyWidth - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, historyRatio,
                178, 250, 122, 160);
        int historyWidth = split.firstWidth;

        historyBounds = new ModernMainLayout.Rect(panelBounds.x + 12, bodyY, historyWidth, bodyHeight);
        elementBounds = new ModernMainLayout.Rect(historyBounds.right() + gap, bodyY,
                Math.max(1, panelBounds.right() - 12 - historyBounds.right() - gap), bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(historyBounds.x, historyBounds.width, gap, bodyY,
                bodyHeight);
        historyClipBounds = new ModernMainLayout.Rect(historyBounds.x + 7, historyBounds.y + 31,
                Math.max(1, historyBounds.width - 14), Math.max(1, historyBounds.height - 39));
        elementClipBounds = new ModernMainLayout.Rect(elementBounds.x + 7, elementBounds.y + 31,
                Math.max(1, elementBounds.width - 14), Math.max(1, elementBounds.height - 39));
    }

    private void drawHeader(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int titleX = panelBounds.x + 14;
        int statusWidth = Math.min(148, Math.max(76, panelBounds.width / 3));
        int statusX = panelBounds.right() - 14 - statusWidth;
        int titleWidth = Math.max(30, statusX - titleX - 10);
        String title = "gui.modern.inspect.u010";
        ModernUiRenderer.drawText(fontRenderer, title, titleX, panelBounds.y + 8, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(fontRenderer, titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(tr(title)) + 5),
                panelBounds.y + 7, "gui.modern.inspect.u011", mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.inspect.u012", titleX, panelBounds.y + 22,
                ModernUiRenderer.MUTED_TEXT, titleWidth);

        boolean statusVisible = !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
        String state = statusVisible ? statusMessage
                : GuiInspectionManager.isCaptureEnabled() ? "gui.modern.inspect.u013" : "gui.modern.inspect.u014";
        int stateColor = GuiInspectionManager.isCaptureEnabled() ? ModernUiRenderer.SUCCESS
                : ModernUiRenderer.SUBTLE_TEXT;
        ModernUiRenderer.drawSubtlePanel(statusX, panelBounds.y + 10, statusWidth, 20, 5,
                statusVisible ? 0xFF243B45 : ModernUiRenderer.SURFACE,
                statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, state, statusX + 8, panelBounds.y + 15,
                statusVisible ? ModernUiRenderer.SUCCESS : stateColor, statusWidth - 16);
    }

    private void drawToolbar(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int toolbarY = panelBounds.y + HEADER_HEIGHT + 6;
        ModernUiRenderer.drawDivider(panelBounds.x + 12, toolbarY - 5, Math.max(1, panelBounds.width - 24),
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(panelBounds.x + 12, toolbarY, Math.max(1, panelBounds.width - 24),
                TOOLBAR_HEIGHT, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);

        int left = panelBounds.x + 19;
        int available = Math.max(1, panelBounds.width - 38);
        int gap = 5;
        int maxButtonWidth = Math.max(34, (available - gap * 4 - 24) / 4);
        int captureWidth = Math.min(96, maxButtonWidth);
        int clearWidth = Math.min(76, maxButtonWidth);
        int copyWidth = Math.min(76, maxButtonWidth);
        int modeWidth = Math.min(84, maxButtonWidth);
        captureBounds = new ModernMainLayout.Rect(left, toolbarY + 5, captureWidth, 20);
        clearBounds = new ModernMainLayout.Rect(captureBounds.right() + gap, toolbarY + 5, clearWidth, 20);
        copyBounds = new ModernMainLayout.Rect(clearBounds.right() + gap, toolbarY + 5, copyWidth, 20);
        modeBounds = new ModernMainLayout.Rect(panelBounds.right() - 19 - modeWidth, toolbarY + 5, modeWidth, 20);

        drawActionButton(fontRenderer, captureBounds,
                GuiInspectionManager.isCaptureEnabled() ? "gui.modern.inspect.u015" : "gui.modern.inspect.u016", true, mouseX, mouseY, true);
        drawActionButton(fontRenderer, clearBounds,
                destructiveConfirmation.isPending("clear") ? "gui.modern.inspect.u017" : "gui.modern.inspect.u018", false, mouseX, mouseY, true);
        drawActionButton(fontRenderer, copyBounds, "gui.modern.inspect.u019", false, mouseX, mouseY,
                getSelectedElement() != null);
        // label names the mode the button switches to
        drawActionButton(fontRenderer, modeBounds,
                realLayoutMode ? "gui.modern.inspect.u049" : "gui.modern.inspect.u050", false, mouseX, mouseY, true);
        if (modeBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = realLayoutMode ? "gui.modern.inspect.u052" : "gui.modern.inspect.u051";
        }

        String summary = tr("gui.modern.inspect.fmt.history", String.valueOf(GuiInspectionManager.getHistory().size()));
        ModernUiRenderer.drawText(fontRenderer, summary, copyBounds.right() + 8, toolbarY + 9,
                ModernUiRenderer.MUTED_TEXT, Math.max(20, modeBounds.x - copyBounds.right() - 16));
    }

    private void drawHistory(FontRenderer fontRenderer, int mouseX, int mouseY) {
        drawSectionFrame(fontRenderer, historyBounds, "gui.modern.inspect.u022",
                GuiInspectionManager.isCaptureEnabled() ? "gui.modern.inspect.u023" : "gui.modern.inspect.u024", mouseX, mouseY,
                "gui.modern.inspect.u025", true);

        List<GuiInspectionManager.CapturedGuiSnapshot> snapshots = getSnapshots();
        int visibleRows = Math.max(1, historyClipBounds.height / HISTORY_ROW_HEIGHT);
        historyMaxScroll = Math.max(0, snapshots.size() - visibleRows);
        historyScroll = clamp(historyScroll, 0, historyMaxScroll);

        ModernUiRenderer.beginClip(historyClipBounds);
        if (snapshots.isEmpty()) {
            drawEmptyState(fontRenderer, historyClipBounds, "gui.modern.inspect.u026", "gui.modern.inspect.u027");
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int index = i + historyScroll;
                if (index >= snapshots.size()) {
                    break;
                }
                int rowY = historyClipBounds.y + i * HISTORY_ROW_HEIGHT;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(historyClipBounds.x, rowY,
                        ModernHoverScrollbar.contentWidth(historyClipBounds.width), HISTORY_ROW_HEIGHT - 2);
                GuiInspectionManager.CapturedGuiSnapshot snapshot = snapshots.get(index);
                boolean hovered = row.contains(mouseX, mouseY) && historyClipBounds.contains(mouseX, mouseY);
                boolean selected = index == selectedSnapshotIndex;
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                        selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 9,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
                String line1 = safe(snapshot.getScreenSimpleName()) + " | " + safe(snapshot.getTimestampText());
                String title = safe(snapshot.getTitle()).trim();
                String line2 = tr("gui.modern.inspect.fmt.elements", title.isEmpty() ? tr("gui.modern.inspect.u028") : title,
                        String.valueOf(snapshot.getElements().size()), String.valueOf(snapshot.getWindowId()));
                ModernUiRenderer.drawText(fontRenderer, line1, row.x + 19, row.y + 6,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 25);
                ModernUiRenderer.drawText(fontRenderer, line2, row.x + 19, row.y + 21,
                        ModernUiRenderer.MUTED_TEXT, row.width - 25);
                snapshotHits.add(new Hit(index, row));
                if (hovered) {
                    hoveredTooltip = buildSnapshotTooltip(snapshot);
                }
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(historyScrollbar, historyClipBounds, historyScroll, historyMaxScroll, snapshots.size(),
                visibleRows, mouseX, mouseY, value -> historyScroll = value);
    }

    private void drawElementList(FontRenderer fontRenderer, int mouseX, int mouseY) {
        List<GuiElementInspector.GuiElementInfo> elements = getSelectedElements();
        String subtitle = elements.isEmpty() ? "gui.modern.inspect.u034" : tr("gui.modern.inspect.fmt.click", String.valueOf(elements.size()));
        drawSectionFrame(fontRenderer, elementBounds, "gui.modern.inspect.u035", subtitle, mouseX, mouseY,
                "gui.modern.inspect.u036", false);

        int visibleRows = Math.max(1, elementClipBounds.height / ELEMENT_ROW_HEIGHT);
        elementMaxScroll = Math.max(0, elements.size() - visibleRows);
        elementScroll = clamp(elementScroll, 0, elementMaxScroll);
        ModernUiRenderer.beginClip(elementClipBounds);
        if (elements.isEmpty()) {
            drawEmptyState(fontRenderer, elementClipBounds, "gui.modern.inspect.u037", "gui.modern.inspect.u038");
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int index = i + elementScroll;
                if (index >= elements.size()) {
                    break;
                }
                int rowY = elementClipBounds.y + i * ELEMENT_ROW_HEIGHT;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(elementClipBounds.x, rowY,
                        ModernHoverScrollbar.contentWidth(elementClipBounds.width), ELEMENT_ROW_HEIGHT - 2);
                GuiElementInspector.GuiElementInfo element = elements.get(index);
                boolean hovered = row.contains(mouseX, mouseY) && elementClipBounds.contains(mouseX, mouseY);
                boolean selected = index == selectedElementIndex;
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                        selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 9, getElementColor(element));
                String primary = typeName(element) + " | " + safe(element.getPath());
                String text = safe(element.getText()).trim();
                String secondary = (text.isEmpty() ? tr("gui.modern.inspect.u039") : text) + " | x=" + element.getX() + ", y="
                        + element.getY() + " | " + element.getWidth() + "x" + element.getHeight();
                if (element.getSlotIndex() >= 0) {
                    secondary += " | slot=" + element.getSlotIndex();
                }
                ModernUiRenderer.drawText(fontRenderer, primary, row.x + 19, row.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 25);
                ModernUiRenderer.drawText(fontRenderer, secondary, row.x + 19, row.y + 20,
                        ModernUiRenderer.MUTED_TEXT, row.width - 25);
                elementHits.add(new Hit(index, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(elementScrollbar, elementClipBounds, elementScroll, elementMaxScroll, elements.size(),
                visibleRows, mouseX, mouseY, value -> elementScroll = value);
    }

    /** Replicates the captured screen by drawing every element at its original coordinates, scaled to fit. */
    private void drawRealLayout(FontRenderer fontRenderer, int mouseX, int mouseY) {
        List<GuiElementInspector.GuiElementInfo> elements = getSelectedElements();
        List<Integer> placed = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < elements.size(); i++) {
            GuiElementInspector.GuiElementInfo e = elements.get(i);
            if (e == null || !e.isVisible() || e.getWidth() <= 0 || e.getHeight() <= 0) {
                continue;
            }
            placed.add(i);
            minX = Math.min(minX, e.getX());
            minY = Math.min(minY, e.getY());
            maxX = Math.max(maxX, e.getX() + e.getWidth());
            maxY = Math.max(maxY, e.getY() + e.getHeight());
        }
        String subtitle = placed.isEmpty() ? "gui.modern.inspect.u056"
                : tr("gui.modern.inspect.fmt.layout", String.valueOf(placed.size()),
                        String.valueOf(elements.size() - placed.size()));
        drawSectionFrame(fontRenderer, elementBounds, "gui.modern.inspect.u050", subtitle, mouseX, mouseY,
                "gui.modern.inspect.u057", false);
        elementScrollbar.idle();
        elementMaxScroll = 0;
        if (placed.isEmpty()) {
            drawEmptyState(fontRenderer, elementClipBounds, "gui.modern.inspect.u037", "gui.modern.inspect.u056");
            return;
        }

        int spanW = Math.max(1, maxX - minX);
        int spanH = Math.max(1, maxY - minY);
        int pad = 4;
        int availW = Math.max(1, elementClipBounds.width - pad * 2);
        int availH = Math.max(1, elementClipBounds.height - pad * 2);
        double scale = Math.min(MAX_LAYOUT_SCALE, Math.min(availW / (double) spanW, availH / (double) spanH));
        int originX = elementClipBounds.x + pad + (int) Math.round((availW - spanW * scale) / 2.0D);
        int originY = elementClipBounds.y + pad + (int) Math.round((availH - spanH * scale) / 2.0D);

        // Large containers first so slots and buttons stay on top and win hit tests.
        final List<GuiElementInspector.GuiElementInfo> source = elements;
        Collections.sort(placed, (a, b) -> Long.compare(area(source.get(b)), area(source.get(a))));

        ModernUiRenderer.beginClip(elementClipBounds);
        ModernUiRenderer.drawSubtlePanel(originX - 2, originY - 2, (int) Math.round(spanW * scale) + 4,
                (int) Math.round(spanH * scale) + 4, 4, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        int hoveredIndex = -1;
        long hoveredArea = Long.MAX_VALUE;
        List<Hit> drawn = new ArrayList<>();
        for (int index : placed) {
            GuiElementInspector.GuiElementInfo e = elements.get(index);
            int x = originX + (int) Math.round((e.getX() - minX) * scale);
            int y = originY + (int) Math.round((e.getY() - minY) * scale);
            int w = Math.max(2, (int) Math.round(e.getWidth() * scale));
            int h = Math.max(2, (int) Math.round(e.getHeight() * scale));
            ModernMainLayout.Rect r = new ModernMainLayout.Rect(x, y, w, h);
            drawn.add(new Hit(index, r));
            if (r.contains(mouseX, mouseY) && elementClipBounds.contains(mouseX, mouseY)
                    && (long) w * h < hoveredArea) {
                hoveredArea = (long) w * h;
                hoveredIndex = index;
            }
        }
        for (Hit hit : drawn) {
            GuiElementInspector.GuiElementInfo e = elements.get(hit.index);
            ModernMainLayout.Rect r = hit.bounds;
            boolean selected = hit.index == selectedElementIndex;
            boolean hovered = hit.index == hoveredIndex;
            int radius = Math.min(3, Math.min(r.width, r.height) / 3);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, radius,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : dim(getElementColor(e)));
            String text = safe(e.getText()).trim();
            if (!text.isEmpty() && r.width >= 10 && r.height >= fontRenderer.FONT_HEIGHT) {
                int maxW = r.width - 4;
                String shown = fontRenderer.trimStringToWidth(text, maxW);
                int tw = fontRenderer.getStringWidth(shown);
                ModernUiRenderer.drawText(fontRenderer, shown, r.x + Math.max(2, (r.width - tw) / 2),
                        r.y + (r.height - fontRenderer.FONT_HEIGHT) / 2 + 1,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, maxW);
            }
            layoutHits.add(hit);
        }
        ModernUiRenderer.endClip();
        if (hoveredIndex >= 0 && hoveredIndex != selectedElementIndex) {
            GuiElementInspector.GuiElementInfo e = elements.get(hoveredIndex);
            String text = safe(e.getText()).trim();
            hoveredTooltip = typeName(e) + (text.isEmpty() ? "" : " | " + text)
                    + (e.getSlotIndex() >= 0 ? " | slot=" + e.getSlotIndex() : "");
        }
    }

    private void calculateFloatLayout(FontRenderer fontRenderer) {
        floatBounds = null;
        floatHeaderBounds = null;
        floatToggleBounds = null;
        floatCopyBounds = null;
        floatCloseBounds = null;
        GuiElementInspector.GuiElementInfo element = getSelectedElement();
        if (!realLayoutMode || element == null || panelBounds == null) {
            return;
        }
        int width = clamp(panelBounds.width / 3, 170, 260);
        width = Math.min(width, Math.max(60, panelBounds.width - 8));
        int height = FLOAT_HEADER_HEIGHT;
        if (floatExpanded) {
            int lines = wrapLines(fontRenderer, buildElementLines(element), width - 16).size();
            height += 6 + lines * FLOAT_LINE_HEIGHT + 4;
        }
        height = Math.min(height, Math.max(FLOAT_HEADER_HEIGHT, panelBounds.height - 8));
        if (floatX == Integer.MIN_VALUE || floatY == Integer.MIN_VALUE) {
            floatX = elementBounds.right() - width - 8;
            floatY = elementBounds.y + 34;
        }
        floatX = clamp(floatX, panelBounds.x + 2, Math.max(panelBounds.x + 2, panelBounds.right() - width - 2));
        floatY = clamp(floatY, panelBounds.y + 2, Math.max(panelBounds.y + 2, panelBounds.bottom() - height - 2));
        floatBounds = new ModernMainLayout.Rect(floatX, floatY, width, height);
        floatHeaderBounds = new ModernMainLayout.Rect(floatX, floatY, width, FLOAT_HEADER_HEIGHT);
        floatCloseBounds = new ModernMainLayout.Rect(floatBounds.right() - 18, floatY + 4, 14, 14);
        floatToggleBounds = new ModernMainLayout.Rect(floatCloseBounds.x - 16, floatY + 4, 14, 14);
        int copyWidth = fontRenderer.getStringWidth(tr("gui.modern.inspect.u054")) + 10;
        floatCopyBounds = new ModernMainLayout.Rect(floatToggleBounds.x - 4 - copyWidth, floatY + 4, copyWidth, 14);
    }

    private void drawFloatPanel(FontRenderer fontRenderer, int mouseX, int mouseY) {
        GuiElementInspector.GuiElementInfo element = getSelectedElement();
        if (floatBounds == null || element == null) {
            return;
        }
        ModernMainLayout.Rect b = floatBounds;
        ModernUiRenderer.drawPanel(b.x, b.y, b.width, b.height, 6, ModernUiRenderer.SHELL_RAISED,
                draggingFloat ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
        ModernUiRenderer.drawStatusDot(b.x + 9, b.y + 9, getElementColor(element));
        int titleMax = Math.max(20, floatCopyBounds.x - b.x - 24);
        ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.inspect.u053") + " · " + typeName(element),
                b.x + 18, b.y + 7, ModernUiRenderer.TEXT, titleMax);

        boolean copyHovered = floatCopyBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(floatCopyBounds.x, floatCopyBounds.y, floatCopyBounds.width,
                floatCopyBounds.height, 3, copyHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                copyHovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.inspect.u054", floatCopyBounds.x + 5,
                floatCopyBounds.y + 3, copyHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                floatCopyBounds.width - 6);

        boolean toggleHovered = floatToggleBounds.contains(mouseX, mouseY);
        String toggle = floatExpanded ? "-" : "+";
        ModernUiRenderer.drawSubtlePanel(floatToggleBounds.x, floatToggleBounds.y, floatToggleBounds.width,
                floatToggleBounds.height, 3, toggleHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                toggleHovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, toggle,
                floatToggleBounds.x + (floatToggleBounds.width - fontRenderer.getStringWidth(toggle)) / 2,
                floatToggleBounds.y + 3, toggleHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, 10);

        boolean closeHovered = floatCloseBounds.contains(mouseX, mouseY);
        if (closeHovered) {
            ModernUiRenderer.drawRoundedRect(floatCloseBounds.x, floatCloseBounds.y, floatCloseBounds.width,
                    floatCloseBounds.height, 3, ModernUiRenderer.SURFACE_HOVER);
        }
        ModernUiRenderer.drawCloseIcon(floatCloseBounds.x + 3, floatCloseBounds.y + 3,
                closeHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);

        if (copyHovered) {
            hoveredTooltip = "gui.modern.inspect.u019";
        } else if (toggleHovered) {
            hoveredTooltip = floatExpanded ? "gui.modern.inspect.u006" : "gui.modern.inspect.u005";
        } else if (floatHeaderBounds.contains(mouseX, mouseY) && !closeHovered) {
            hoveredTooltip = "gui.modern.inspect.u055";
        }

        if (!floatExpanded) {
            return;
        }
        ModernUiRenderer.drawDivider(b.x + 8, b.y + FLOAT_HEADER_HEIGHT, Math.max(1, b.width - 16),
                ModernUiRenderer.BORDER_SUBTLE);
        List<String> lines = wrapLines(fontRenderer, buildElementLines(element), b.width - 16);
        int maxLines = Math.max(0, (b.height - FLOAT_HEADER_HEIGHT - 10) / FLOAT_LINE_HEIGHT);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            ModernUiRenderer.drawText(fontRenderer, lines.get(i), b.x + 8,
                    b.y + FLOAT_HEADER_HEIGHT + 6 + i * FLOAT_LINE_HEIGHT, ModernUiRenderer.TEXT, b.width - 16);
        }
    }

    private List<String> buildElementLines(GuiElementInspector.GuiElementInfo element) {
        List<String> lines = new ArrayList<>();
        String text = safe(element.getText()).trim();
        lines.add(tr("gui.modern.inspect.fmt.text", text.isEmpty() ? tr("gui.modern.inspect.u046") : text));
        lines.add(tr("gui.modern.inspect.fmt.path", safe(element.getPath())));
        lines.add(tr("gui.modern.inspect.fmt.type_pos", typeName(element),
                String.valueOf(element.getX()), String.valueOf(element.getY()),
                String.valueOf(element.getWidth()), String.valueOf(element.getHeight())));
        if (element.getSlotIndex() >= 0) {
            lines.add(tr("gui.modern.inspect.fmt.slot", String.valueOf(element.getSlotIndex())));
        }
        if (element.getButtonId() != Integer.MIN_VALUE) {
            lines.add(tr("gui.modern.inspect.fmt.button", String.valueOf(element.getButtonId())));
        }
        String value = safe(element.getValue()).trim();
        if (!value.isEmpty()) {
            lines.add(tr("gui.modern.inspect.fmt.value", value));
        }
        return lines;
    }

    private String buildSnapshotTooltip(GuiInspectionManager.CapturedGuiSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(tr("gui.modern.inspect.fmt.screen", safe(snapshot.getScreenSimpleName())));
        lines.add(tr("gui.modern.inspect.fmt.class", safe(snapshot.getScreenClassName())));
        String title = safe(snapshot.getTitle()).trim();
        lines.add(title.isEmpty() ? tr("gui.modern.inspect.u032") : tr("gui.modern.inspect.fmt.title", title));
        lines.add(tr("gui.modern.inspect.u033") + snapshot.getWindowId() + " | total=" + snapshot.getTotalSlots()
                + " | container=" + snapshot.getContainerSlots() + " | player=" + snapshot.getPlayerInventorySlots());
        lines.add(tr("gui.modern.inspect.fmt.total", String.valueOf(snapshot.getElements().size())));
        lines.add(safe(snapshot.getTimestampText()));
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.replace('\n', ' '));
        }
        return sb.toString();
    }

    private void drawSectionFrame(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String title,
            String subtitle, int mouseX, int mouseY, String tooltip, boolean history) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int titleX = bounds.x + 9;
        int titleY = bounds.y + 7;
        int titleWidth = Math.max(24, bounds.width - 22);
        ModernUiRenderer.drawText(fontRenderer, title, titleX, titleY, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(fontRenderer, titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(tr(title)) + 5),
                titleY - 1, tooltip, mouseX, mouseY);
        if (subtitle != null && !subtitle.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, subtitle, titleX, bounds.y + 20,
                    history && GuiInspectionManager.isCaptureEnabled() ? ModernUiRenderer.SUCCESS
                            : ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, bounds.width - 18));
        }
        ModernUiRenderer.drawDivider(bounds.x + 8, bounds.y + 29, Math.max(1, bounds.width - 16),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label,
            boolean primary, int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? ModernUiRenderer.SHELL_RAISED
                : primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE
                : primary ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.mythos.mythosScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.bindingText() : tr(label));
        com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label,
                bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT,
                Math.max(12, bounds.width - 8));
    }

    private void drawInfoIcon(FontRenderer fontRenderer, int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = mouseX >= 0 && mouseY >= 0 && bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private void drawEmptyState(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String title, String subtitle) {
        int centerX = bounds.x + bounds.width / 2;
        int iconY = bounds.y + Math.max(10, bounds.height / 3);
        ModernUiRenderer.drawSearchIcon(centerX - 7, iconY, ModernUiRenderer.MUTED_TEXT);
        int titleWidth = fontRenderer.getStringWidth(tr(title));
        ModernUiRenderer.drawText(fontRenderer, title, centerX - titleWidth / 2, iconY + 23,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(20, bounds.width - 10));
        int subtitleWidth = Math.min(bounds.width - 10, fontRenderer.getStringWidth(tr(subtitle)));
        ModernUiRenderer.drawText(fontRenderer, subtitle, centerX - subtitleWidth / 2, iconY + 37,
                ModernUiRenderer.MUTED_TEXT, Math.max(20, bounds.width - 10));
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clipBounds, int scroll, int maxScroll,
            int totalItems, int visibleItems, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (clipBounds == null || maxScroll <= 0 || totalItems <= 0) {
            bar.idle();
            return;
        }
        bar.draw(clipBounds, scroll, maxScroll, Math.max(1, visibleItems), totalItems, mouseX, mouseY, setter);
    }

    private List<String> wrapLines(FontRenderer fontRenderer, List<String> lines, int maxWidth) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            List<String> split = fontRenderer.listFormattedStringToWidth(safe(line), Math.max(10, maxWidth));
            if (split == null || split.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(split);
            }
        }
        return wrapped;
    }

    private void copySelectedPath() {
        GuiElementInspector.GuiElementInfo element = getSelectedElement();
        if (element == null) {
            showStatus("gui.modern.inspect.u047");
            return;
        }
        String path = safe(element.getPath());
        GuiScreen.setClipboardString(path);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendMessage(
                    new TextComponentString(TextFormatting.GREEN + tr("gui.modern.inspect.fmt.copied", path)));
        }
        showStatus("gui.modern.inspect.u048");
    }

    private List<GuiInspectionManager.CapturedGuiSnapshot> getSnapshots() {
        return GuiInspectionManager.getHistory();
    }

    private GuiInspectionManager.CapturedGuiSnapshot getSelectedSnapshot() {
        List<GuiInspectionManager.CapturedGuiSnapshot> snapshots = getSnapshots();
        if (selectedSnapshotIndex < 0 || selectedSnapshotIndex >= snapshots.size()) {
            return null;
        }
        return snapshots.get(selectedSnapshotIndex);
    }

    private List<GuiElementInspector.GuiElementInfo> getSelectedElements() {
        GuiInspectionManager.CapturedGuiSnapshot snapshot = getSelectedSnapshot();
        return snapshot == null ? Collections.<GuiElementInspector.GuiElementInfo>emptyList() : snapshot.getElements();
    }

    private GuiElementInspector.GuiElementInfo getSelectedElement() {
        List<GuiElementInspector.GuiElementInfo> elements = getSelectedElements();
        if (selectedElementIndex < 0 || selectedElementIndex >= elements.size()) {
            return null;
        }
        return elements.get(selectedElementIndex);
    }

    private void syncSelectionBounds() {
        List<GuiInspectionManager.CapturedGuiSnapshot> snapshots = getSnapshots();
        if (snapshots.isEmpty()) {
            selectedSnapshotIndex = -1;
            selectedElementIndex = -1;
            historyScroll = 0;
            elementScroll = 0;
            return;
        }
        if (selectedSnapshotIndex < 0 || selectedSnapshotIndex >= snapshots.size()) {
            selectedSnapshotIndex = 0;
            selectedElementIndex = -1;
        }
        List<GuiElementInspector.GuiElementInfo> elements = getSelectedElements();
        if (elements.isEmpty()) {
            selectedElementIndex = -1;
            elementScroll = 0;
        } else if (selectedElementIndex >= elements.size()) {
            selectedElementIndex = -1;
        }
    }

    private static long area(GuiElementInspector.GuiElementInfo e) {
        return (long) Math.max(0, e.getWidth()) * Math.max(0, e.getHeight());
    }

    private static int dim(int color) {
        return (color & 0x00FFFFFF) | 0x88000000;
    }

    private static String typeName(GuiElementInspector.GuiElementInfo element) {
        return element == null || element.getType() == null ? "ELEMENT" : element.getType().name();
    }

    private int getElementColor(GuiElementInspector.GuiElementInfo element) {
        if (element == null || element.getType() == null) {
            return ModernUiRenderer.SUBTLE_TEXT;
        }
        switch (element.getType()) {
            case TITLE:
                return ModernUiRenderer.WARNING;
            case BUTTON:
                return ModernUiRenderer.ACCENT;
            case SLOT:
                return ModernUiRenderer.SUCCESS;
            case CUSTOM:
                return 0xFFC58CFF;
            default:
                return ModernUiRenderer.SUBTLE_TEXT;
        }
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
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
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
