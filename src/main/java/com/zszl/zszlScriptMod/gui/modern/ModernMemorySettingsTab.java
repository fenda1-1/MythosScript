package com.zszl.zszlScriptMod.gui.modern;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.system.MemoryManager;
import com.zszl.zszlScriptMod.system.MemorySnapshot;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.core.ModernConfirmationState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/**
 * Embedded memory workbench for the modern main-window tab strip.
 *
 * <p>The legacy manager split snapshot selection, comparison and maintenance
 * actions across three screens. This tab keeps the same MemoryManager data and
 * operations in one panel, so a comparison is another view of this tab rather
 * than a new GUI screen.</p>
 */
public final class ModernMemorySettingsTab implements ModernSettingsTab {

    private static final int HEADER_HEIGHT = 40;
    private static final int WIDE_CONTROL_HEIGHT = 58;
    private static final int COMPACT_CONTROL_HEIGHT = 84;
    private static final int SNAPSHOT_ROW_HEIGHT = 35;
    private static final int SNAPSHOT_ROW_GAP = 4;
    private static final int COMPARISON_LINE_HEIGHT = 15;
    private static final int SECTION_GAP = 8;

    private static final class SnapshotHit {
        private final String name;
        private final ModernMainLayout.Rect bounds;

        private SnapshotHit(String name, ModernMainLayout.Rect bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    private static final class ComparisonLine {
        private final String text;
        private final int color;

        private ComparisonLine(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }

    private final Set<String> selectedSnapshotNames = new LinkedHashSet<>();
    private final List<SnapshotHit> snapshotHits = new ArrayList<>();
    private final ModernConfirmationState destructiveConfirmation = new ModernConfirmationState();

    private GuiTextField snapshotNameField;
    private List<MemorySnapshot> snapshots = Collections.emptyList();
    private List<ComparisonLine> comparisonLines = Collections.emptyList();
    private MemoryManager.ComparisonResult comparisonResult;

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect controlBounds;
    private ModernMainLayout.Rect nameFieldBounds;
    private ModernMainLayout.Rect createBounds;
    private ModernMainLayout.Rect compareBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect gcBounds;
    private ModernMainLayout.Rect reloadBounds;
    private ModernMainLayout.Rect snapshotListBounds;
    private ModernMainLayout.Rect snapshotClipBounds;
    private ModernMainLayout.Rect detailBounds;
    private ModernMainLayout.Rect dividerBounds;
    private ModernMainLayout.Rect comparisonClipBounds;
    private ModernMainLayout.Rect comparisonBackBounds;
    private ModernMainLayout.Rect comparisonCopyBounds;
    private final ModernHoverScrollbar snapshotScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar comparisonScrollbar = new ModernHoverScrollbar();

    private int snapshotScrollOffset;
    private int snapshotMaxScrollOffset;
    private int comparisonScrollOffset;
    private int comparisonMaxScrollOffset;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private int lastMouseX;
    private int lastMouseY;
    private double snapshotRatio = 0.43D;
    private boolean draggingDivider;

    public static ModernSettingsTab create() {
        return new ModernMemorySettingsTab();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (snapshotNameField != null) {
            return;
        }
        snapshotRatio = MainUiLayoutManager.getModernSplitRatio("memory.snapshot_list", snapshotRatio);
        snapshotNameField = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        snapshotNameField.setEnableBackgroundDrawing(false);
        snapshotNameField.setMaxStringLength(96);
        snapshotNameField.setTextColor(ModernUiRenderer.TEXT);
        snapshotNameField.setDisabledTextColour(ModernUiRenderer.SUBTLE_TEXT);
        snapshotNameField.setText(defaultSnapshotName());
    }

    @Override
    public void updateScreen() {
        if (snapshotNameField != null) {
            snapshotNameField.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = requestedContentBounds;
        hoveredTooltip = "";
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        snapshots = getSnapshots();
        selectedSnapshotNames.retainAll(MemoryManager.snapshots.keySet());

        panelBounds = buildPanelBounds(requestedContentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer, mouseX, mouseY);

        boolean compact = panelBounds.width < 520;
        int controlHeight = compact ? COMPACT_CONTROL_HEIGHT : WIDE_CONTROL_HEIGHT;
        int controlY = panelBounds.y + HEADER_HEIGHT + 6;
        controlBounds = new ModernMainLayout.Rect(panelBounds.x + 10, controlY,
                Math.max(1, panelBounds.width - 20), controlHeight);
        drawControls(fontRenderer, compact, mouseX, mouseY);

        int bodyY = controlBounds.bottom() + 8;
        ModernMainLayout.Rect bodyBounds = new ModernMainLayout.Rect(panelBounds.x + 10, bodyY,
                Math.max(1, panelBounds.width - 20), Math.max(1, panelBounds.bottom() - bodyY - 8));
        drawWorkbench(fontRenderer, bodyBounds, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }

        if (contains(nameFieldBounds, mouseX, mouseY) && snapshotNameField != null) {
            snapshotNameField.setFocused(true);
            snapshotNameField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(createBounds, mouseX, mouseY)) {
            createSnapshot();
            return true;
        }
        if (contains(compareBounds, mouseX, mouseY)) {
            destructiveConfirmation.clear();
            compareSelectedSnapshots();
            return true;
        }
        if (contains(deleteBounds, mouseX, mouseY)) {
            if (selectedSnapshotNames.isEmpty()) {
                showStatus("gui.modern.mem.u001");
                return true;
            }
            if (!destructiveConfirmation.request("delete")) {
                showStatus("gui.modern.mem.u002");
                return true;
            }
            deleteSelectedSnapshots();
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            if (snapshots.isEmpty()) {
                showStatus("gui.modern.mem.u003");
                return true;
            }
            if (!destructiveConfirmation.request("clear")) {
                showStatus("gui.modern.mem.u004");
                return true;
            }
            clearAllSnapshots();
            return true;
        }
        if (contains(gcBounds, mouseX, mouseY)) {
            destructiveConfirmation.clear();
            forceGarbageCollection();
            return true;
        }
        if (contains(reloadBounds, mouseX, mouseY)) {
            destructiveConfirmation.clear();
            reloadRenderers();
            return true;
        }
        if (contains(comparisonBackBounds, mouseX, mouseY)) {
            comparisonResult = null;
            comparisonLines = Collections.emptyList();
            comparisonScrollOffset = 0;
            return true;
        }
        if (contains(comparisonCopyBounds, mouseX, mouseY)) {
            copyComparisonReport();
            return true;
        }
        if (snapshotScrollbar.beginDrag(mouseX, mouseY) || comparisonScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (comparisonResult != null && contains(comparisonClipBounds, mouseX, mouseY)) {
            return true;
        }
        if (contains(snapshotClipBounds, mouseX, mouseY)) {
            destructiveConfirmation.clear();
            for (SnapshotHit hit : snapshotHits) {
                if (!hit.bounds.contains(mouseX, mouseY)) {
                    continue;
                }
                if (isCtrlDown()) {
                    if (!selectedSnapshotNames.add(hit.name)) {
                        selectedSnapshotNames.remove(hit.name);
                    }
                } else {
                    selectedSnapshotNames.clear();
                    selectedSnapshotNames.add(hit.name);
                }
                comparisonResult = null;
                comparisonLines = Collections.emptyList();
                comparisonScrollOffset = 0;
                return true;
            }
            return true;
        }
        if (snapshotNameField != null) {
            snapshotNameField.setFocused(false);
        }
        return contains(contentBounds, mouseX, mouseY);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (snapshotNameField != null && snapshotNameField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && snapshotNameField != null && snapshotNameField.isFocused()) {
            createSnapshot();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && snapshotScrollbar.isDragging()) {
            snapshotScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && comparisonScrollbar.isDragging()) {
            comparisonScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && draggingDivider && dividerBounds != null && snapshotListBounds != null) {
            int total = Math.max(2, snapshotListBounds.width + detailBounds.width);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - snapshotListBounds.x - SECTION_GAP / 2, 190, 260, 150, 180);
            snapshotRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (snapshotScrollbar.isDragging() || comparisonScrollbar.isDragging())) {
            snapshotScrollbar.endDrag();
            comparisonScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("memory.snapshot_list", snapshotRatio);
            draggingDivider = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (snapshotScrollbar.isDragging() || comparisonScrollbar.isDragging() || draggingDivider) {
            snapshotScrollbar.endDrag();
            comparisonScrollbar.endDrag();
            draggingDivider = false;
            return true;
        }
        if (destructiveConfirmation.isPending("delete") || destructiveConfirmation.isPending("clear")) {
            destructiveConfirmation.clear();
            showStatus("gui.modern.mem.u005");
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, currentMouseX(), currentMouseY());
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) {
            return false;
        }
        if (comparisonResult != null && contains(comparisonClipBounds, mouseX, mouseY)
                && comparisonMaxScrollOffset > 0) {
            int previous = comparisonScrollOffset;
            comparisonScrollOffset = clamp(comparisonScrollOffset + (wheel > 0 ? -COMPARISON_LINE_HEIGHT * 2
                    : COMPARISON_LINE_HEIGHT * 2), 0, comparisonMaxScrollOffset);
            return previous != comparisonScrollOffset;
        }
        if (contains(snapshotClipBounds, mouseX, mouseY) && snapshotMaxScrollOffset > 0) {
            int previous = snapshotScrollOffset;
            snapshotScrollOffset = clamp(snapshotScrollOffset + (wheel > 0 ? -SNAPSHOT_ROW_HEIGHT
                    : SNAPSHOT_ROW_HEIGHT), 0, snapshotMaxScrollOffset);
            return previous != snapshotScrollOffset;
        }
        return false;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(panelBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        if (snapshotNameField != null) {
            snapshotNameField.setText(defaultSnapshotName());
            snapshotNameField.setFocused(false);
        }
        selectedSnapshotNames.clear();
        destructiveConfirmation.clear();
        comparisonResult = null;
        comparisonLines = Collections.emptyList();
        snapshotScrollOffset = 0;
        comparisonScrollOffset = 0;
        snapshotScrollbar.endDrag();
        comparisonScrollbar.endDrag();
        statusMessage = "";
    }

    private void drawHeader(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int titleX = panelBounds.x + 15;
        int rightWidth = Math.min(174, Math.max(86, panelBounds.width / 3));
        int rightX = panelBounds.right() - rightWidth - 14;
        int titleWidth = Math.max(28, rightX - titleX - 10);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u006", titleX, panelBounds.y + 8, ModernUiRenderer.TEXT,
                titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(tr("gui.modern.mem.u006")) + 5),
                panelBounds.y + 7, "gui.modern.mem.u007",
                mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u008", titleX, panelBounds.y + 22,
                ModernUiRenderer.MUTED_TEXT, titleWidth);

        boolean statusVisible = !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
        String summary = statusVisible ? statusMessage
                : tr("gui.modern.mem.fmt.summary", String.valueOf(snapshots.size()), String.valueOf(selectedSnapshotNames.size()));
        ModernMainLayout.Rect summaryBounds = new ModernMainLayout.Rect(rightX, panelBounds.y + 10, rightWidth, 20);
        ModernUiRenderer.drawSubtlePanel(summaryBounds.x, summaryBounds.y, summaryBounds.width, summaryBounds.height, 5,
                statusVisible ? 0xFF243B45 : ModernUiRenderer.SURFACE,
                statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, summary, summaryBounds.x + 7,
                summaryBounds.y + (summaryBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT, summaryBounds.width - 14);
    }

    private void drawControls(FontRenderer fontRenderer, boolean compact, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(controlBounds.x, controlBounds.y, controlBounds.width, controlBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = controlBounds.x + 8;
        int width = Math.max(1, controlBounds.width - 16);
        int nameY = controlBounds.y + 7;
        int createWidth = Math.min(88, Math.max(66, width / 4));
        nameFieldBounds = new ModernMainLayout.Rect(x, nameY, Math.max(32, width - createWidth - 8), 20);
        createBounds = new ModernMainLayout.Rect(nameFieldBounds.right() + 8, nameY, createWidth, 20);
        drawTextField(fontRenderer, nameFieldBounds, mouseX, mouseY);
        drawActionButton(fontRenderer, createBounds, "gui.modern.mem.u009", true, true, mouseX, mouseY);

        if (compact) {
            int actionY = controlBounds.y + 35;
            int actionGap = 5;
            int actionWidth = Math.max(1, (width - actionGap * 2) / 3);
            compareBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
            deleteBounds = new ModernMainLayout.Rect(compareBounds.right() + actionGap, actionY, actionWidth, 20);
            clearBounds = new ModernMainLayout.Rect(deleteBounds.right() + actionGap, actionY,
                    Math.max(1, x + width - deleteBounds.right() - actionGap), 20);
            drawActionButton(fontRenderer, compareBounds, "gui.modern.mem.u010", false, selectedSnapshotNames.size() == 2, mouseX,
                    mouseY);
            drawActionButton(fontRenderer, deleteBounds,
                    destructiveConfirmation.isPending("delete") ? "gui.modern.mem.u011" : "gui.modern.mem.u012", false,
                    !selectedSnapshotNames.isEmpty(), mouseX,
                    mouseY);
            drawActionButton(fontRenderer, clearBounds,
                    destructiveConfirmation.isPending("clear") ? "gui.modern.mem.u011" : "gui.modern.mem.u013", false,
                    !snapshots.isEmpty(), mouseX, mouseY);

            int maintenanceY = controlBounds.y + 59;
            int maintenanceWidth = Math.max(1, (width - actionGap) / 2);
            gcBounds = new ModernMainLayout.Rect(x, maintenanceY, maintenanceWidth, 20);
            reloadBounds = new ModernMainLayout.Rect(gcBounds.right() + actionGap, maintenanceY,
                    Math.max(1, x + width - gcBounds.right() - actionGap), 20);
            drawActionButton(fontRenderer, gcBounds, "gui.modern.mem.u014", false, true, mouseX, mouseY);
            drawActionButton(fontRenderer, reloadBounds, "gui.modern.mem.u015", false, true, mouseX, mouseY);
            return;
        }

        int actionY = controlBounds.y + 34;
        int actionGap = 5;
        int actionWidth = Math.max(1, (width - actionGap * 4) / 5);
        compareBounds = new ModernMainLayout.Rect(x, actionY, actionWidth, 20);
        deleteBounds = new ModernMainLayout.Rect(compareBounds.right() + actionGap, actionY, actionWidth, 20);
        clearBounds = new ModernMainLayout.Rect(deleteBounds.right() + actionGap, actionY, actionWidth, 20);
        gcBounds = new ModernMainLayout.Rect(clearBounds.right() + actionGap, actionY, actionWidth, 20);
        reloadBounds = new ModernMainLayout.Rect(gcBounds.right() + actionGap, actionY,
                Math.max(1, x + width - gcBounds.right() - actionGap), 20);
        drawActionButton(fontRenderer, compareBounds, "gui.modern.mem.u010", false, selectedSnapshotNames.size() == 2, mouseX,
                mouseY);
        drawActionButton(fontRenderer, deleteBounds,
                destructiveConfirmation.isPending("delete") ? "gui.modern.mem.u011" : "gui.modern.mem.u012", false,
                !selectedSnapshotNames.isEmpty(), mouseX, mouseY);
        drawActionButton(fontRenderer, clearBounds,
                destructiveConfirmation.isPending("clear") ? "gui.modern.mem.u011" : "gui.modern.mem.u013", false,
                !snapshots.isEmpty(), mouseX, mouseY);
        drawActionButton(fontRenderer, gcBounds, "gui.modern.mem.u014", false, true, mouseX, mouseY);
        drawActionButton(fontRenderer, reloadBounds, "gui.modern.mem.u015", false, true, mouseX, mouseY);
    }

    private void drawWorkbench(FontRenderer fontRenderer, ModernMainLayout.Rect bodyBounds, int mouseX, int mouseY) {
        comparisonBackBounds = null;
        comparisonCopyBounds = null;
        comparisonClipBounds = null;
        comparisonScrollbar.idle();
        boolean splitHorizontally = bodyBounds.width >= 500 && bodyBounds.height >= 110;
        if (splitHorizontally) {
            int gap = SECTION_GAP;
            ModernSplitPane.Split split = ModernSplitPane.calculate(bodyBounds.width - gap, snapshotRatio,
                    190, 260, 150, 180);
            int listWidth = split.firstWidth;
            snapshotListBounds = new ModernMainLayout.Rect(bodyBounds.x, bodyBounds.y, listWidth, bodyBounds.height);
            detailBounds = new ModernMainLayout.Rect(snapshotListBounds.right() + gap, bodyBounds.y,
                    Math.max(1, bodyBounds.right() - snapshotListBounds.right() - gap), bodyBounds.height);
            dividerBounds = ModernSplitPane.verticalDividerBounds(snapshotListBounds.x, snapshotListBounds.width, gap,
                    bodyBounds.y, bodyBounds.height);
        } else if (bodyBounds.height >= 155) {
            dividerBounds = null;
            int gap = SECTION_GAP;
            int listHeight = Math.max(86, Math.min(190, Math.round((bodyBounds.height - gap) * 0.58f)));
            listHeight = Math.min(listHeight, bodyBounds.height);
            snapshotListBounds = new ModernMainLayout.Rect(bodyBounds.x, bodyBounds.y, bodyBounds.width, listHeight);
            int detailY = snapshotListBounds.bottom() + gap;
            detailBounds = new ModernMainLayout.Rect(bodyBounds.x, detailY, bodyBounds.width,
                    Math.max(1, bodyBounds.bottom() - detailY));
        } else {
            dividerBounds = null;
            snapshotListBounds = bodyBounds;
            detailBounds = null;
        }

        drawSnapshotList(fontRenderer, mouseX, mouseY);
        if (detailBounds != null) {
            drawDetails(fontRenderer, mouseX, mouseY);
        } else if (comparisonResult != null) {
            detailBounds = snapshotListBounds;
            drawDetails(fontRenderer, mouseX, mouseY);
        }
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
    }

    private void drawSnapshotList(FontRenderer fontRenderer, int mouseX, int mouseY) {
        snapshotHits.clear();
        ModernUiRenderer.drawSubtlePanel(snapshotListBounds.x, snapshotListBounds.y, snapshotListBounds.width,
                snapshotListBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int titleX = snapshotListBounds.x + 9;
        String selectedLabel = selectedSnapshotNames.isEmpty() ? "gui.modern.mem.u016" : tr("gui.modern.mem.fmt.selected", String.valueOf(selectedSnapshotNames.size()));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u017", titleX, snapshotListBounds.y + 7,
                ModernUiRenderer.TEXT, Math.max(38, snapshotListBounds.width - 96));
        ModernUiRenderer.drawText(fontRenderer, selectedLabel, snapshotListBounds.right() - 80,
                snapshotListBounds.y + 7, selectedSnapshotNames.size() == 2 ? ModernUiRenderer.SUCCESS
                        : ModernUiRenderer.SUBTLE_TEXT, 70);
        ModernUiRenderer.drawDivider(snapshotListBounds.x + 8, snapshotListBounds.y + 25,
                Math.max(1, snapshotListBounds.width - 16), ModernUiRenderer.BORDER_SUBTLE);

        snapshotClipBounds = new ModernMainLayout.Rect(snapshotListBounds.x + 7, snapshotListBounds.y + 29,
                Math.max(1, snapshotListBounds.width - 14), Math.max(1, snapshotListBounds.height - 36));
        int contentHeight = snapshots.isEmpty() ? 0
                : snapshots.size() * SNAPSHOT_ROW_HEIGHT + Math.max(0, snapshots.size() - 1) * SNAPSHOT_ROW_GAP;
        snapshotMaxScrollOffset = Math.max(0, contentHeight - snapshotClipBounds.height);
        snapshotScrollOffset = clamp(snapshotScrollOffset, 0, snapshotMaxScrollOffset);

        ModernUiRenderer.beginClip(snapshotClipBounds);
        if (snapshots.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u018", snapshotClipBounds.x + 7, snapshotClipBounds.y + 12,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, snapshotClipBounds.width - 14));
        } else {
            for (int index = 0; index < snapshots.size(); index++) {
                MemorySnapshot snapshot = snapshots.get(index);
                if (snapshot == null) {
                    continue;
                }
                int y = snapshotClipBounds.y + index * (SNAPSHOT_ROW_HEIGHT + SNAPSHOT_ROW_GAP)
                        - snapshotScrollOffset;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(snapshotClipBounds.x, y,
                        ModernHoverScrollbar.contentWidth(snapshotClipBounds.width), SNAPSHOT_ROW_HEIGHT);
                if (y + SNAPSHOT_ROW_HEIGHT < snapshotClipBounds.y || y > snapshotClipBounds.bottom()) {
                    continue;
                }
                boolean selected = selectedSnapshotNames.contains(snapshot.name);
                boolean hovered = row.contains(mouseX, mouseY) && snapshotClipBounds.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(row.x + 8, row.y + 8,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
                ModernUiRenderer.drawText(fontRenderer, snapshot.name, row.x + 20, row.y + 5,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(28, row.width - 28));
                String stats = tr("gui.modern.mem.fmt.used_at", snapshot.getFormattedTimestamp(), formatMegabytes(snapshot.usedMemory));
                ModernUiRenderer.drawText(fontRenderer, stats, row.x + 20, row.y + 19,
                        ModernUiRenderer.MUTED_TEXT, Math.max(28, row.width - 28));
                snapshotHits.add(new SnapshotHit(snapshot.name, row));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(snapshotScrollbar, snapshotClipBounds, contentHeight, snapshotScrollOffset,
                snapshotMaxScrollOffset, mouseX, mouseY, value -> snapshotScrollOffset = value);
    }

    private void drawDetails(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(detailBounds.x, detailBounds.y, detailBounds.width, detailBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (comparisonResult != null) {
            drawComparison(fontRenderer, mouseX, mouseY);
            return;
        }
        comparisonScrollbar.idle();

        int x = detailBounds.x + 10;
        int width = Math.max(1, detailBounds.width - 20);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u019", x, detailBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(30, width - 20));
        drawInfoIcon(x + Math.min(Math.max(12, width - 12), fontRenderer.getStringWidth(tr("gui.modern.mem.u019")) + 7),
                detailBounds.y + 7, "gui.modern.mem.u020", mouseX, mouseY);
        ModernUiRenderer.drawDivider(x, detailBounds.y + 25, width, ModernUiRenderer.BORDER_SUBTLE);

        List<MemorySnapshot> selected = getSelectedSnapshots();
        if (selected.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.mem.u021", x, detailBounds.y + 39,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, width));
            return;
        }
        int currentY = detailBounds.y + 34;
        for (MemorySnapshot snapshot : selected) {
            if (snapshot == null || currentY + 42 > detailBounds.bottom()) {
                break;
            }
            ModernUiRenderer.drawText(fontRenderer, snapshot.name, x, currentY, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(24, width));
            String usage = tr("gui.modern.mem.fmt.usage", formatMegabytes(snapshot.usedMemory),
                    formatMegabytes(snapshot.totalMemory), formatMegabytes(snapshot.freeMemory));
            ModernUiRenderer.drawText(fontRenderer, usage, x, currentY + 14, ModernUiRenderer.TEXT,
                    Math.max(24, width));
            String counts = tr("gui.modern.mem.fmt.counts", String.valueOf(sumValues(snapshot.entityCounts)),
                    String.valueOf(sumValues(snapshot.tileEntityCounts)), String.valueOf(sumValues(snapshot.chunkCounts)),
                    String.valueOf(sumValues(snapshot.renderChunkCounts)));
            ModernUiRenderer.drawText(fontRenderer, counts, x, currentY + 28, ModernUiRenderer.MUTED_TEXT,
                    Math.max(24, width));
            currentY += 46;
        }
    }

    private void drawComparison(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int x = detailBounds.x + 9;
        int width = Math.max(1, detailBounds.width - 18);
        int actionGap = 5;
        int copyWidth = Math.min(70, Math.max(52, width / 4));
        int backWidth = Math.min(70, Math.max(52, width / 4));
        comparisonCopyBounds = new ModernMainLayout.Rect(detailBounds.right() - 9 - copyWidth, detailBounds.y + 5,
                copyWidth, 20);
        comparisonBackBounds = new ModernMainLayout.Rect(comparisonCopyBounds.x - actionGap - backWidth,
                detailBounds.y + 5, backWidth, 20);
        String title = comparisonResult.before.name + " -> " + comparisonResult.after.name;
        int titleWidth = Math.max(20, comparisonBackBounds.x - x - 7);
        ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.mem.fmt.compare", title), x, detailBounds.y + 9, ModernUiRenderer.TEXT,
                titleWidth);
        drawActionButton(fontRenderer, comparisonBackBounds, "gui.modern.mem.u022", false, true, mouseX, mouseY);
        drawActionButton(fontRenderer, comparisonCopyBounds, "gui.modern.mem.u023", false, true, mouseX, mouseY);

        String summary = tr("gui.modern.mem.fmt.jvm", formatMegabytes(comparisonResult.before.usedMemory),
                formatMegabytes(comparisonResult.after.usedMemory),
                formatSignedMegabytes(comparisonResult.after.usedMemory - comparisonResult.before.usedMemory));
        ModernUiRenderer.drawText(fontRenderer, summary, x, detailBounds.y + 29, getDeltaColor(
                comparisonResult.after.usedMemory - comparisonResult.before.usedMemory), width);
        comparisonClipBounds = new ModernMainLayout.Rect(detailBounds.x + 7, detailBounds.y + 47,
                Math.max(1, detailBounds.width - 14), Math.max(1, detailBounds.height - 54));
        int contentHeight = comparisonLines.size() * COMPARISON_LINE_HEIGHT;
        comparisonMaxScrollOffset = Math.max(0, contentHeight - comparisonClipBounds.height);
        comparisonScrollOffset = clamp(comparisonScrollOffset, 0, comparisonMaxScrollOffset);

        ModernUiRenderer.beginClip(comparisonClipBounds);
        for (int index = 0; index < comparisonLines.size(); index++) {
            ComparisonLine line = comparisonLines.get(index);
            int y = comparisonClipBounds.y + index * COMPARISON_LINE_HEIGHT - comparisonScrollOffset;
            if (y + COMPARISON_LINE_HEIGHT < comparisonClipBounds.y || y > comparisonClipBounds.bottom()) {
                continue;
            }
            ModernUiRenderer.drawText(fontRenderer, line.text, comparisonClipBounds.x + 3, y, line.color,
                    ModernHoverScrollbar.contentWidth(comparisonClipBounds.width - 6));
        }
        ModernUiRenderer.endClip();
        drawScrollbar(comparisonScrollbar, comparisonClipBounds, contentHeight, comparisonScrollOffset,
                comparisonMaxScrollOffset, mouseX, mouseY, value -> comparisonScrollOffset = value);
    }

    private void drawTextField(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean focused = snapshotNameField != null && snapshotNameField.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (snapshotNameField == null) {
            return;
        }
        snapshotNameField.x = bounds.x + 7;
        snapshotNameField.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        snapshotNameField.width = Math.max(1, bounds.width - 14);
        snapshotNameField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(snapshotNameField);
        ModernUiRenderer.drawTextField(snapshotNameField);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean primary,
            boolean enabled, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D24 : primary ? hovered ? 0xFFFF82A5 : ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE : primary ? ModernUiRenderer.ACCENT
                : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label,
                bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                !enabled ? ModernUiRenderer.MUTED_TEXT : primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT,
                Math.max(12, bounds.width - 8));
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect clipBounds, int contentHeight,
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

    private void createSnapshot() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null) {
            showStatus("gui.modern.mem.u024");
            return;
        }
        String requestedName = snapshotNameField == null ? "" : snapshotNameField.getText().trim();
        String name = requestedName.isEmpty() ? defaultSnapshotName() : requestedName;
        boolean replaced = MemoryManager.snapshots.containsKey(name);
        try {
            MemoryManager.takeSnapshot(name);
            selectedSnapshotNames.clear();
            comparisonResult = null;
            comparisonLines = Collections.emptyList();
            comparisonScrollOffset = 0;
            if (snapshotNameField != null) {
                snapshotNameField.setText(defaultSnapshotName());
                snapshotNameField.setFocused(false);
            }
            showStatus(tr(replaced ? "gui.modern.mem.fmt.updated" : "gui.modern.mem.fmt.created", name));
        } catch (RuntimeException ignored) {
            showStatus("gui.modern.mem.u025");
        }
    }

    private void compareSelectedSnapshots() {
        List<MemorySnapshot> selected = getSelectedSnapshots();
        if (selected.size() != 2) {
            showStatus("gui.modern.mem.u026");
            return;
        }
        MemorySnapshot before = selected.get(0);
        MemorySnapshot after = selected.get(1);
        if (before.timestamp > after.timestamp) {
            MemorySnapshot temporary = before;
            before = after;
            after = temporary;
        }
        comparisonResult = MemoryManager.compare(before, after);
        comparisonLines = buildComparisonLines(comparisonResult);
        comparisonScrollOffset = 0;
        showStatus("gui.modern.mem.u027");
    }

    private void deleteSelectedSnapshots() {
        if (selectedSnapshotNames.isEmpty()) {
            showStatus("gui.modern.mem.u028");
            return;
        }
        int deleted = 0;
        for (String name : new ArrayList<>(selectedSnapshotNames)) {
            if (MemoryManager.snapshots.containsKey(name)) {
                MemoryManager.deleteSnapshot(name);
                deleted++;
            }
        }
        selectedSnapshotNames.clear();
        comparisonResult = null;
        comparisonLines = Collections.emptyList();
        comparisonScrollOffset = 0;
        showStatus(tr("gui.modern.mem.fmt.deleted", String.valueOf(deleted)));
    }

    private void clearAllSnapshots() {
        int count = MemoryManager.snapshots.size();
        MemoryManager.clearSnapshots();
        selectedSnapshotNames.clear();
        comparisonResult = null;
        comparisonLines = Collections.emptyList();
        comparisonScrollOffset = 0;
        showStatus(count == 0 ? "gui.modern.mem.u003" : tr("gui.modern.mem.fmt.cleared", String.valueOf(count)));
    }

    private void forceGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        System.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();
        showStatus(tr("gui.modern.mem.fmt.gc", formatMegabytes(before), formatMegabytes(after)));
    }

    private void reloadRenderers() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.renderGlobal == null) {
            showStatus("gui.modern.mem.u029");
            return;
        }
        try {
            minecraft.renderGlobal.loadRenderers();
            showStatus("gui.modern.mem.u030");
        } catch (RuntimeException ignored) {
            showStatus("gui.modern.mem.u031");
        }
    }

    private void copyComparisonReport() {
        if (comparisonResult == null) {
            return;
        }
        GuiScreen.setClipboardString(generateReport());
        showStatus("gui.modern.mem.u032");
    }

    private List<ComparisonLine> buildComparisonLines(MemoryManager.ComparisonResult result) {
        List<ComparisonLine> lines = new ArrayList<>();
        if (result == null) {
            return lines;
        }
        addComparisonSection(lines, "gui.modern.mem.u033", result.before.entityMemoryUsage, result.after.entityMemoryUsage,
                result.before.entityCounts, result.after.entityCounts);
        addComparisonSection(lines, "gui.modern.mem.u034", result.before.tileEntityMemoryUsage, result.after.tileEntityMemoryUsage,
                result.before.tileEntityCounts, result.after.tileEntityCounts);
        addComparisonSection(lines, "gui.modern.mem.u035", result.before.chunkMemoryUsage, result.after.chunkMemoryUsage,
                result.before.chunkCounts, result.after.chunkCounts);
        addComparisonSection(lines, "gui.modern.mem.u036", result.before.renderChunkMemoryUsage,
                result.after.renderChunkMemoryUsage, result.before.renderChunkCounts, result.after.renderChunkCounts);
        return lines;
    }

    private void addComparisonSection(List<ComparisonLine> lines, String title, Map<String, Long> beforeMemory,
            Map<String, Long> afterMemory, Map<String, Integer> beforeCounts, Map<String, Integer> afterCounts) {
        lines.add(new ComparisonLine(title, ModernUiRenderer.ACCENT));
        Set<String> allKeys = new HashSet<>();
        addKeys(allKeys, beforeMemory);
        addKeys(allKeys, afterMemory);
        addKeys(allKeys, beforeCounts);
        addKeys(allKeys, afterCounts);
        List<String> changedKeys = new ArrayList<>();
        for (String key : allKeys) {
            long memoryDelta = getLong(beforeMemory, key) == 0L && getLong(afterMemory, key) == 0L
                    ? 0L : getLong(afterMemory, key) - getLong(beforeMemory, key);
            int countDelta = getInt(afterCounts, key) - getInt(beforeCounts, key);
            if (memoryDelta != 0L || countDelta != 0) {
                changedKeys.add(key);
            }
        }
        Collections.sort(changedKeys, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                long leftDelta = Math.abs(getLong(afterMemory, left) - getLong(beforeMemory, left));
                long rightDelta = Math.abs(getLong(afterMemory, right) - getLong(beforeMemory, right));
                int memoryCompare = Long.compare(rightDelta, leftDelta);
                if (memoryCompare != 0) {
                    return memoryCompare;
                }
                return left.compareTo(right);
            }
        });
        if (changedKeys.isEmpty()) {
            lines.add(new ComparisonLine("gui.modern.mem.u037", ModernUiRenderer.MUTED_TEXT));
            return;
        }
        for (String key : changedKeys) {
            long memoryDelta = getLong(afterMemory, key) - getLong(beforeMemory, key);
            int countDelta = getInt(afterCounts, key) - getInt(beforeCounts, key);
            String text = tr("gui.modern.mem.fmt.delta", formatSignedBytes(memoryDelta), formatSignedCount(countDelta), key);
            lines.add(new ComparisonLine(text, getDeltaColor(memoryDelta != 0L ? memoryDelta : countDelta)));
        }
    }

    private String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append(tr("gui.modern.mem.fmt.report", comparisonResult.before.name,
                comparisonResult.before.getFormattedTimestamp(), comparisonResult.after.name,
                comparisonResult.after.getFormattedTimestamp())).append("\n");
        report.append(tr("gui.modern.mem.fmt.jvm_used", formatMegabytes(comparisonResult.before.usedMemory),
                formatMegabytes(comparisonResult.after.usedMemory),
                formatSignedMegabytes(comparisonResult.after.usedMemory - comparisonResult.before.usedMemory))).append("\n");
        for (ComparisonLine line : comparisonLines) {
            report.append(line.text).append('\n');
        }
        return report.toString();
    }

    private List<MemorySnapshot> getSnapshots() {
        List<MemorySnapshot> current = new ArrayList<>();
        for (MemorySnapshot snapshot : MemoryManager.snapshots.values()) {
            if (snapshot != null) {
                current.add(snapshot);
            }
        }
        return current;
    }

    private List<MemorySnapshot> getSelectedSnapshots() {
        List<MemorySnapshot> selected = new ArrayList<>();
        for (String name : selectedSnapshotNames) {
            MemorySnapshot snapshot = MemoryManager.snapshots.get(name);
            if (snapshot != null) {
                selected.add(snapshot);
            }
        }
        return selected;
    }

    private void addKeys(Set<String> keys, Map<?, ?> values) {
        if (values != null) {
            keys.addAll(stringKeys(values));
        }
    }

    private Set<String> stringKeys(Map<?, ?> values) {
        Set<String> keys = new HashSet<>();
        for (Object key : values.keySet()) {
            if (key != null) {
                keys.add(String.valueOf(key));
            }
        }
        return keys;
    }

    private long getLong(Map<String, Long> values, String key) {
        if (values == null) {
            return 0L;
        }
        Long value = values.get(key);
        return value == null ? 0L : value.longValue();
    }

    private int getInt(Map<String, Integer> values, String key) {
        if (values == null) {
            return 0;
        }
        Integer value = values.get(key);
        return value == null ? 0 : value.intValue();
    }

    private long sumValues(Map<String, Integer> values) {
        long total = 0L;
        if (values != null) {
            for (Integer value : values.values()) {
                if (value != null) {
                    total += value.intValue();
                }
            }
        }
        return total;
    }

    private int getDeltaColor(long delta) {
        return delta > 0L ? 0xFFE06A78 : delta < 0L ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT;
    }

    private String formatSignedCount(int value) {
        return value > 0 ? "+" + value : value < 0 ? String.valueOf(value) : "±0";
    }

    private String formatSignedMegabytes(long bytes) {
        return bytes > 0L ? "+" + formatMegabytes(bytes) : bytes < 0L ? "-" + formatMegabytes(-bytes) : "±0";
    }

    private String formatSignedBytes(long bytes) {
        return bytes > 0L ? "+" + formatBytes(bytes) : bytes < 0L ? "-" + formatBytes(-bytes) : "±0 B";
    }

    private String formatMegabytes(long bytes) {
        return String.valueOf(bytes / (1024L * 1024L));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        int unit = 0;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private String defaultSnapshotName() {
        return tr("gui.modern.mem.u038") + new SimpleDateFormat("HH-mm-ss").format(new Date());
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private void drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip == null ? "" : tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect requestedBounds) {
        ModernMainLayout.Rect source = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1)
                : requestedBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 16));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private int currentMouseX() {
        return lastMouseX;
    }

    private int currentMouseY() {
        return lastMouseY;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
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
