package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiInspectionManager;
import com.zszl.zszlScriptMod.gui.modern.core.ModernConfirmationState;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

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

    private static final class SnapshotHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private SnapshotHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class ElementHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private ElementHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private final List<SnapshotHit> snapshotHits = new ArrayList<>();
    private final List<ElementHit> elementHits = new ArrayList<>();
    private final ModernConfirmationState destructiveConfirmation = new ModernConfirmationState();

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect captureBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect infoToggleBounds;
    private ModernMainLayout.Rect historyBounds;
    private ModernMainLayout.Rect historyClipBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect infoBounds;
    private ModernMainLayout.Rect elementBounds;
    private ModernMainLayout.Rect elementClipBounds;
    private ModernMainLayout.Rect selectedElementBounds;
    private ModernMainLayout.Rect dividerBounds;
    private final ModernHoverScrollbar historyScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar elementScrollbar = new ModernHoverScrollbar();

    private int historyScroll;
    private int historyMaxScroll;
    private int elementScroll;
    private int elementMaxScroll;
    private int selectedSnapshotIndex = -1;
    private int selectedElementIndex = -1;
    private boolean detailInfoExpanded = true;
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
        calculateLayout(requestedContentBounds);
        syncSelectionBounds();

        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer, mouseX, mouseY);
        drawToolbar(fontRenderer, mouseX, mouseY);
        drawHistory(fontRenderer, mouseX, mouseY);
        drawDetails(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (contentBounds == null || !contentBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
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
        if (historyScrollbar.beginDrag(mouseX, mouseY) || elementScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (contains(infoToggleBounds, mouseX, mouseY) && getSelectedSnapshot() != null) {
            detailInfoExpanded = !detailInfoExpanded;
            showStatus(detailInfoExpanded ? "gui.modern.inspect.u005" : "gui.modern.inspect.u006");
            return true;
        }

        for (SnapshotHit hit : snapshotHits) {
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            selectedSnapshotIndex = hit.index;
            selectedElementIndex = getSelectedElements().isEmpty() ? -1 : 0;
            elementScroll = 0;
            showStatus("gui.modern.inspect.u007");
            return true;
        }
        for (ElementHit hit : elementHits) {
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
        if (historyScrollbar.isDragging() || elementScrollbar.isDragging() || draggingDivider) {
            historyScrollbar.endDrag();
            elementScrollbar.endDrag();
            draggingDivider = false;
            return true;
        }
        if (destructiveConfirmation.isPending("clear")) {
            destructiveConfirmation.clear();
            showStatus("gui.modern.inspect.u009");
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
        if (contains(historyClipBounds, mouseX, mouseY) && historyMaxScroll > 0) {
            historyScroll = clamp(historyScroll + (wheel > 0 ? -1 : 1), 0, historyMaxScroll);
            return true;
        }
        if (contains(elementClipBounds, mouseX, mouseY) && elementMaxScroll > 0) {
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
        detailBounds = new ModernMainLayout.Rect(historyBounds.right() + gap, bodyY,
                Math.max(1, panelBounds.right() - 12 - historyBounds.right() - gap), bodyHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(historyBounds.x, historyBounds.width, gap, bodyY,
                bodyHeight);
        historyClipBounds = new ModernMainLayout.Rect(historyBounds.x + 7, historyBounds.y + 31,
                Math.max(1, historyBounds.width - 14), Math.max(1, historyBounds.height - 39));

        int infoHeight = detailInfoExpanded ? clamp(detailBounds.height / 4, 58, 104) : 32;
        int footerHeight = clamp(detailBounds.height / 4, 62, 108);
        int minimumElementHeight = 46;
        int availableSections = Math.max(1, detailBounds.height - SECTION_GAP * 2);
        while (infoHeight + footerHeight + minimumElementHeight > availableSections && footerHeight > 52) {
            footerHeight--;
        }
        while (infoHeight + footerHeight + minimumElementHeight > availableSections
                && detailInfoExpanded && infoHeight > 52) {
            infoHeight--;
        }
        int elementHeight = Math.max(1, detailBounds.height - infoHeight - footerHeight - SECTION_GAP * 2);
        infoBounds = new ModernMainLayout.Rect(detailBounds.x, detailBounds.y, detailBounds.width, infoHeight);
        elementBounds = new ModernMainLayout.Rect(detailBounds.x, infoBounds.bottom() + SECTION_GAP,
                detailBounds.width, elementHeight);
        selectedElementBounds = new ModernMainLayout.Rect(detailBounds.x, elementBounds.bottom() + SECTION_GAP,
                detailBounds.width, Math.max(1, detailBounds.bottom() - elementBounds.bottom() - SECTION_GAP));
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
        drawInfoIcon(fontRenderer, titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
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
        int maxButtonWidth = Math.max(34, (available - gap * 3 - 24) / 3);
        int captureWidth = Math.min(96, maxButtonWidth);
        int clearWidth = Math.min(76, maxButtonWidth);
        int copyWidth = Math.min(76, maxButtonWidth);
        captureBounds = new ModernMainLayout.Rect(left, toolbarY + 5, captureWidth, 20);
        clearBounds = new ModernMainLayout.Rect(captureBounds.right() + gap, toolbarY + 5, clearWidth, 20);
        copyBounds = new ModernMainLayout.Rect(clearBounds.right() + gap, toolbarY + 5, copyWidth, 20);
        int infoX = panelBounds.right() - 19 - 20;
        infoToggleBounds = new ModernMainLayout.Rect(infoX, toolbarY + 6, 20, 18);

        drawActionButton(fontRenderer, captureBounds,
                GuiInspectionManager.isCaptureEnabled() ? "gui.modern.inspect.u015" : "gui.modern.inspect.u016", true, mouseX, mouseY, true);
        drawActionButton(fontRenderer, clearBounds,
                destructiveConfirmation.isPending("clear") ? "gui.modern.inspect.u017" : "gui.modern.inspect.u018", false, mouseX, mouseY, true);
        drawActionButton(fontRenderer, copyBounds, "gui.modern.inspect.u019", false, mouseX, mouseY,
                getSelectedElement() != null);

        String summary = tr("gui.modern.inspect.fmt.history", String.valueOf(GuiInspectionManager.getHistory().size()));
        ModernUiRenderer.drawText(fontRenderer, summary, copyBounds.right() + 8, toolbarY + 9,
                ModernUiRenderer.MUTED_TEXT, Math.max(20, infoX - copyBounds.right() - 16));
        boolean infoEnabled = getSelectedSnapshot() != null;
        boolean infoHovered = infoToggleBounds.contains(mouseX, mouseY) && infoEnabled;
        ModernUiRenderer.drawSubtlePanel(infoToggleBounds.x, infoToggleBounds.y, infoToggleBounds.width,
                infoToggleBounds.height, 4, infoHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                infoEnabled ? (infoHovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE)
                        : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(infoToggleBounds.x + 8, infoToggleBounds.y + 5, detailInfoExpanded,
                infoEnabled ? (infoHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT)
                        : ModernUiRenderer.MUTED_TEXT);
        if (infoHovered) {
            hoveredTooltip = detailInfoExpanded ? "gui.modern.inspect.u020" : "gui.modern.inspect.u021";
        }
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
                snapshotHits.add(new SnapshotHit(index, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(historyScrollbar, historyClipBounds, historyScroll, historyMaxScroll, snapshots.size(),
                visibleRows, mouseX, mouseY, value -> historyScroll = value);
    }

    private void drawDetails(FontRenderer fontRenderer, int mouseX, int mouseY) {
        drawSnapshotInfo(fontRenderer, mouseX, mouseY);
        drawElements(fontRenderer, mouseX, mouseY);
        drawSelectedElement(fontRenderer);
    }

    private void drawSnapshotInfo(FontRenderer fontRenderer, int mouseX, int mouseY) {
        GuiInspectionManager.CapturedGuiSnapshot snapshot = getSelectedSnapshot();
        String subtitle = snapshot == null ? "gui.modern.inspect.u029"
                : detailInfoExpanded ? safe(snapshot.getScreenSimpleName()) + " | " + safe(snapshot.getTimestampText())
                        : buildCollapsedSnapshotSummary(snapshot);
        drawSectionFrame(fontRenderer, infoBounds, "gui.modern.inspect.u030", subtitle, mouseX, mouseY,
                "gui.modern.inspect.u031", false);
        if (snapshot == null || !detailInfoExpanded) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(tr("gui.modern.inspect.fmt.screen", safe(snapshot.getScreenSimpleName())));
        lines.add(tr("gui.modern.inspect.fmt.class", safe(snapshot.getScreenClassName())));
        lines.add(safe(snapshot.getTitle()).trim().isEmpty() ? "gui.modern.inspect.u032"
                : tr("gui.modern.inspect.fmt.title", safe(snapshot.getTitle())));
        lines.add(tr("gui.modern.inspect.u033") + snapshot.getWindowId() + " | total=" + snapshot.getTotalSlots()
                + " | container=" + snapshot.getContainerSlots() + " | player=" + snapshot.getPlayerInventorySlots());
        lines.add(tr("gui.modern.inspect.fmt.total", String.valueOf(snapshot.getElements().size())));
        drawWrappedTextLines(fontRenderer, lines, infoBounds.x + 9, infoBounds.y + 31,
                infoBounds.width - 18, infoBounds.height - 36, ModernUiRenderer.TEXT);
    }

    private void drawElements(FontRenderer fontRenderer, int mouseX, int mouseY) {
        GuiInspectionManager.CapturedGuiSnapshot snapshot = getSelectedSnapshot();
        List<GuiElementInspector.GuiElementInfo> elements = snapshot == null
                ? Collections.<GuiElementInspector.GuiElementInfo>emptyList()
                : snapshot.getElements();
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
                String primary = safe(element.getType() == null ? "ELEMENT" : element.getType().name()) + " | "
                        + safe(element.getPath());
                String text = safe(element.getText()).trim();
                String secondary = (text.isEmpty() ? tr("gui.modern.inspect.u039") : text) + " | x=" + element.getX() + ", y="
                        + element.getY() + " | " + element.getWidth() + "x" + element.getHeight();
                ModernUiRenderer.drawText(fontRenderer, primary, row.x + 19, row.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 25);
                ModernUiRenderer.drawText(fontRenderer, secondary, row.x + 19, row.y + 20,
                        ModernUiRenderer.MUTED_TEXT, row.width - 25);
                elementHits.add(new ElementHit(index, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(elementScrollbar, elementClipBounds, elementScroll, elementMaxScroll, elements.size(),
                visibleRows, mouseX, mouseY, value -> elementScroll = value);
    }

    private void drawSelectedElement(FontRenderer fontRenderer) {
        GuiElementInspector.GuiElementInfo element = getSelectedElement();
        String subtitle = element == null ? "gui.modern.inspect.u040" : "gui.modern.inspect.u041";
        drawSectionFrame(fontRenderer, selectedElementBounds, "gui.modern.inspect.u042", subtitle, -1, -1,
                "gui.modern.inspect.u043", false);
        List<String> lines = new ArrayList<>();
        if (element == null) {
            lines.add("gui.modern.inspect.u044");
            lines.add("gui.modern.inspect.u045");
        } else {
            String text = safe(element.getText()).trim();
            lines.add(tr("gui.modern.inspect.fmt.path", safe(element.getPath())));
            lines.add(tr("gui.modern.inspect.fmt.text", text.isEmpty() ? tr("gui.modern.inspect.u046") : text));
            lines.add(tr("gui.modern.inspect.fmt.type_pos", safe(element.getType() == null ? "" : element.getType().name()),
                    String.valueOf(element.getX()), String.valueOf(element.getY()),
                    String.valueOf(element.getWidth()), String.valueOf(element.getHeight())));
            if (element.getButtonId() != Integer.MIN_VALUE) {
                lines.add(tr("gui.modern.inspect.fmt.button", String.valueOf(element.getButtonId())));
            }
            if (element.getSlotIndex() >= 0) {
                lines.add(tr("gui.modern.inspect.fmt.slot", String.valueOf(element.getSlotIndex())));
            }
        }
        drawWrappedTextLines(fontRenderer, lines, selectedElementBounds.x + 9, selectedElementBounds.y + 31,
                selectedElementBounds.width - 18, selectedElementBounds.height - 36, ModernUiRenderer.TEXT);
    }

    private void drawSectionFrame(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String title,
            String subtitle, int mouseX, int mouseY, String tooltip, boolean history) {
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int titleX = bounds.x + 9;
        int titleY = bounds.y + 7;
        int titleWidth = Math.max(24, bounds.width - 22);
        ModernUiRenderer.drawText(fontRenderer, title, titleX, titleY, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(fontRenderer, titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
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
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label,
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
        int titleWidth = fontRenderer.getStringWidth(title);
        ModernUiRenderer.drawText(fontRenderer, title, centerX - titleWidth / 2, iconY + 23,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(20, bounds.width - 10));
        int subtitleWidth = Math.min(bounds.width - 10, fontRenderer.getStringWidth(subtitle));
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

    private void drawWrappedTextLines(FontRenderer fontRenderer, List<String> lines, int x, int y, int maxWidth,
            int maxHeight, int color) {
        if (lines == null || lines.isEmpty() || maxWidth <= 0 || maxHeight <= 0) {
            return;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            List<String> split = fontRenderer.listFormattedStringToWidth(safe(line), maxWidth);
            if (split == null || split.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(split);
            }
        }
        int lineHeight = fontRenderer.FONT_HEIGHT + 2;
        int maxLines = Math.max(1, maxHeight / lineHeight);
        int drawCount = Math.min(maxLines, wrapped.size());
        for (int i = 0; i < drawCount; i++) {
            String line = wrapped.get(i);
            if (i == drawCount - 1 && wrapped.size() > maxLines) {
                line = fontRenderer.trimStringToWidth(line + " ...", maxWidth);
            }
            ModernUiRenderer.drawText(fontRenderer, line, x, y + i * lineHeight, color, maxWidth);
        }
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
        } else if (selectedElementIndex < 0 || selectedElementIndex >= elements.size()) {
            selectedElementIndex = 0;
        }
    }

    private String buildCollapsedSnapshotSummary(GuiInspectionManager.CapturedGuiSnapshot snapshot) {
        String title = safe(snapshot.getTitle()).trim();
        return tr("gui.modern.inspect.fmt.collapsed", safe(snapshot.getScreenSimpleName()),
                title.isEmpty() ? tr("gui.modern.inspect.u028") : title, String.valueOf(snapshot.getElements().size()));
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
