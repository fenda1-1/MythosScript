package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.utils.TerrainScanManager;
import com.zszl.zszlScriptMod.utils.TerrainScannerHandler;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/**
 * Complete terrain scanner workbench rendered inside the modern main window.
 * The old manager remains available as a compatibility screen, while this tab
 * keeps its record and preview workflows in one place.
 */
public final class ModernTerrainScannerSettingsTab implements ModernSettingsTab {

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 50;
    private static final int ROW_HEIGHT = 24;
    private static final int PANEL_HEADER_HEIGHT = 44;
    private static final int CONTROL_HEIGHT_WIDE = 54;
    private static final int CONTROL_HEIGHT_COMPACT = 78;
    private static final int CONTENT_LINE_HEIGHT = 10;

    private enum Confirmation {
        NONE,
        DELETE,
        CLEAR
    }

    private static final class RecordHit {
        private final String fileName;
        private final ModernMainLayout.Rect bounds;

        private RecordHit(String fileName, ModernMainLayout.Rect bounds) {
            this.fileName = fileName;
            this.bounds = bounds;
        }
    }

    private final List<RecordHit> recordHits = new ArrayList<>();

    private GuiTextField radiusField;
    private GuiTextField renameField;
    private List<String> scanFiles = Collections.emptyList();
    private List<String> rawContentLines = Collections.emptyList();
    private List<String> wrappedContentLines = Collections.emptyList();
    private String fullContent = "";
    private String selectedFileName;
    private String pendingFileName;
    private int wrappedContentWidth = -1;

    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect controlPanelBounds;
    private ModernMainLayout.Rect radiusBounds;
    private ModernMainLayout.Rect scanBounds;
    private ModernMainLayout.Rect refreshBounds;
    private ModernMainLayout.Rect clearBounds;
    private ModernMainLayout.Rect recordsPanelBounds;
    private ModernMainLayout.Rect recordsViewportBounds;
    private ModernMainLayout.Rect dividerBounds;
    private final ModernHoverScrollbar recordsScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect detailsPanelBounds;
    private ModernMainLayout.Rect detailsViewportBounds;
    private final ModernHoverScrollbar detailsScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect copyBounds;
    private ModernMainLayout.Rect renameBounds;
    private ModernMainLayout.Rect renameSaveBounds;
    private ModernMainLayout.Rect deleteBounds;
    private ModernMainLayout.Rect confirmationYesBounds;
    private ModernMainLayout.Rect confirmationNoBounds;

    private int recordsScrollOffset;
    private int recordsMaxScroll;
    private int detailsScrollOffset;
    private int detailsMaxScroll;
    private int recordsContentHeight;
    private int detailsContentHeight;
    private int lastMouseX;
    private int lastMouseY;
    private long lastSeenFinishedAt;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private Confirmation confirmation = Confirmation.NONE;
    private boolean initialized;
    private double recordsRatio = 0.34D;
    private boolean draggingDivider;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        recordsRatio = MainUiLayoutManager.getModernSplitRatio("terrain_scanner.records", recordsRatio);
        radiusField = createTextField(fontRenderer, 2);
        radiusField.setText(String.valueOf(clamp(TerrainScanManager.loadLastRadius(), MIN_RADIUS, MAX_RADIUS)));
        renameField = createTextField(fontRenderer, 96);
        refreshRecords();
        initialized = true;
    }

    @Override
    public void updateScreen() {
        if (radiusField != null) {
            radiusField.updateCursorCounter();
        }
        if (renameField != null) {
            renameField.updateCursorCounter();
        }
        pollScanStatus();
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        pollScanStatus();
        contentBounds = requestedContentBounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredTooltip = "";
        recordHits.clear();

        panelBounds = buildPanelBounds(requestedContentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer, mouseX, mouseY);
        ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + PANEL_HEADER_HEIGHT,
                Math.max(1, panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);

        boolean compact = panelBounds.width < 500;
        boolean stackedPanels = compact && panelBounds.height >= 280;
        int controlHeight = compact && stackedPanels ? CONTROL_HEIGHT_COMPACT : CONTROL_HEIGHT_WIDE;
        controlPanelBounds = new ModernMainLayout.Rect(panelBounds.x + 9, panelBounds.y + PANEL_HEADER_HEIGHT + 8,
                Math.max(1, panelBounds.width - 18), controlHeight);
        drawControls(fontRenderer, compact, mouseX, mouseY);

        int mainY = controlPanelBounds.bottom() + 8;
        int mainHeight = Math.max(1, panelBounds.bottom() - mainY - 8);
        if (compact && stackedPanels) {
            drawCompactPanels(fontRenderer, mainY, mainHeight, mouseX, mouseY);
        } else {
            drawWidePanels(fontRenderer, mainY, mainHeight, mouseX, mouseY);
        }

        if (confirmation != Confirmation.NONE) {
            drawConfirmation(fontRenderer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (confirmation != Confirmation.NONE) {
            if (contains(confirmationYesBounds, mouseX, mouseY)) {
                confirmPendingAction();
            } else if (contains(confirmationNoBounds, mouseX, mouseY)) {
                clearConfirmation();
            }
            return true;
        }

        if (contains(scanBounds, mouseX, mouseY)) {
            startScan();
            return true;
        }
        if (contains(refreshBounds, mouseX, mouseY)) {
            refreshRecords();
            showStatus("gui.modern.scan.u001");
            return true;
        }
        if (contains(clearBounds, mouseX, mouseY)) {
            requestClearAll();
            return true;
        }
        if (contains(radiusBounds, mouseX, mouseY) && radiusField != null) {
            focusField(radiusField, renameField);
            radiusField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (recordsScrollbar.beginDrag(mouseX, mouseY) || detailsScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (dividerBounds != null && dividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (contains(copyBounds, mouseX, mouseY)) {
            copySelectedContent();
            return true;
        }
        if (contains(renameSaveBounds, mouseX, mouseY)) {
            renameSelectedFile();
            return true;
        }
        if (contains(deleteBounds, mouseX, mouseY)) {
            requestDeleteSelected();
            return true;
        }
        if (contains(renameBounds, mouseX, mouseY) && renameField != null) {
            focusField(renameField, radiusField);
            renameField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }

        for (int i = recordHits.size() - 1; i >= 0; i--) {
            RecordHit hit = recordHits.get(i);
            if (contains(hit.bounds, mouseX, mouseY) && contains(recordsViewportBounds, mouseX, mouseY)) {
                selectFile(hit.fileName);
                return true;
            }
        }
        if (contains(recordsViewportBounds, mouseX, mouseY) || contains(detailsViewportBounds, mouseX, mouseY)) {
            clearFieldFocus();
            return true;
        }
        clearFieldFocus();
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && (recordsScrollbar.isDragging() || detailsScrollbar.isDragging())) {
            recordsScrollbar.endDrag();
            detailsScrollbar.endDrag();
            return true;
        }
        if (radiusField != null && radiusField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (renameField != null && renameField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            if (radiusField != null && radiusField.isFocused()) {
                startScan();
                return true;
            }
            if (renameField != null && renameField.isFocused()) {
                renameSelectedFile();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (confirmation != Confirmation.NONE) {
            clearConfirmation();
            return true;
        }
        if (recordsScrollbar.isDragging() || detailsScrollbar.isDragging() || draggingDivider) {
            recordsScrollbar.endDrag();
            detailsScrollbar.endDrag();
            draggingDivider = false;
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
        if (contains(recordsViewportBounds, mouseX, mouseY) && recordsMaxScroll > 0) {
            int previous = recordsScrollOffset;
            recordsScrollOffset = clamp(recordsScrollOffset + (wheel > 0 ? -32 : 32), 0, recordsMaxScroll);
            return previous != recordsScrollOffset;
        }
        if (contains(detailsViewportBounds, mouseX, mouseY) && detailsMaxScroll > 0) {
            int previous = detailsScrollOffset;
            detailsScrollOffset = clamp(detailsScrollOffset + (wheel > 0 ? -32 : 32), 0, detailsMaxScroll);
            return previous != detailsScrollOffset;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && recordsScrollbar.isDragging()) {
            recordsScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && detailsScrollbar.isDragging()) {
            detailsScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (clickedMouseButton == 0 && draggingDivider && panelBounds != null && dividerBounds != null) {
            int availableWidth = Math.max(2, panelBounds.width - 18 - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(availableWidth,
                    mouseX - panelBounds.x - 9 - 4, 178, 250, 122, 160);
            recordsRatio = split.ratio;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && (recordsScrollbar.isDragging() || detailsScrollbar.isDragging())) {
            recordsScrollbar.endDrag();
            detailsScrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingDivider) {
            MainUiLayoutManager.setModernSplitRatio("terrain_scanner.records", recordsRatio);
            draggingDivider = false;
            return true;
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
        if (!initialized) {
            return;
        }
        radiusField.setText(String.valueOf(clamp(TerrainScanManager.loadLastRadius(), MIN_RADIUS, MAX_RADIUS)));
        clearFieldFocus();
        clearConfirmation();
        recordsScrollbar.endDrag();
        detailsScrollbar.endDrag();
        clearSelectedFile();
        recordsScrollOffset = 0;
        detailsScrollOffset = 0;
        refreshRecords();
    }

    @Override
    public boolean isDirty() {
        if (!initialized || radiusField == null) {
            return false;
        }
        String savedRadius = String.valueOf(clamp(TerrainScanManager.loadLastRadius(), MIN_RADIUS, MAX_RADIUS));
        if (!savedRadius.equals(radiusField.getText().trim())) {
            return true;
        }
        return selectedFileName != null && renameField != null
                && !selectedFileName.equals(renameField.getText().trim());
    }

    private void drawHeader(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int titleX = panelBounds.x + 15;
        int statusWidth = Math.min(150, Math.max(72, panelBounds.width / 3));
        int statusX = panelBounds.right() - statusWidth - 14;
        int titleWidth = Math.max(28, statusX - titleX - 12);
        String title = "gui.modern.scan.u002";
        ModernUiRenderer.drawText(fontRenderer, title, titleX, panelBounds.y + 9, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
                panelBounds.y + 8, "gui.modern.scan.u003", mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u004", titleX, panelBounds.y + 23,
                ModernUiRenderer.MUTED_TEXT, titleWidth);

        ModernMainLayout.Rect statusBounds = new ModernMainLayout.Rect(statusX, panelBounds.y + 11, statusWidth, 19);
        TerrainScannerHandler.ScanStatus status = TerrainScannerHandler.getScanStatus();
        int statusColor = getStatusColor(status);
        ModernUiRenderer.drawSubtlePanel(statusBounds.x, statusBounds.y, statusBounds.width, statusBounds.height, 5,
                status == null || (!status.isScanning() && status.getErrorMessage().isEmpty())
                        ? ModernUiRenderer.SURFACE : 0xFF243B45,
                statusColor == ModernUiRenderer.MUTED_TEXT ? ModernUiRenderer.BORDER_SUBTLE : statusColor);
        ModernUiRenderer.drawText(fontRenderer, buildStatusLabel(status), statusBounds.x + 7,
                statusBounds.y + (statusBounds.height - fontRenderer.FONT_HEIGHT) / 2, statusColor,
                statusBounds.width - 14);
    }

    private void drawControls(FontRenderer fontRenderer, boolean compact, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(controlPanelBounds.x, controlPanelBounds.y, controlPanelBounds.width,
                controlPanelBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (compact) {
            drawCompactControls(fontRenderer, mouseX, mouseY);
        } else {
            drawWideControls(fontRenderer, mouseX, mouseY);
        }
    }

    private void drawWideControls(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int x = controlPanelBounds.x + 8;
        int y = controlPanelBounds.y + 8;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u005", x, y + 6, ModernUiRenderer.SUBTLE_TEXT, 30);
        radiusBounds = new ModernMainLayout.Rect(x + 34, y, 58, 21);
        drawTextField(fontRenderer, radiusField, radiusBounds, "1-50", mouseX, mouseY);

        scanBounds = new ModernMainLayout.Rect(radiusBounds.right() + 6, y, 82, 21);
        refreshBounds = new ModernMainLayout.Rect(scanBounds.right() + 6, y, 68, 21);
        clearBounds = new ModernMainLayout.Rect(refreshBounds.right() + 6, y, 78, 21);
        drawActionButton(fontRenderer, scanBounds, "gui.modern.scan.u006", !isScanning(), true, false, mouseX, mouseY);
        drawActionButton(fontRenderer, refreshBounds, "gui.modern.scan.u007", true, false, false, mouseX, mouseY);
        drawActionButton(fontRenderer, clearBounds, "gui.modern.scan.u008", !scanFiles.isEmpty(), false, true, mouseX, mouseY);

        String feedback = getActiveFeedback();
        ModernUiRenderer.drawText(fontRenderer, feedback, x + 8, y + 34, getFeedbackColor(),
                Math.max(20, controlPanelBounds.width - 24));
    }

    private void drawCompactControls(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int x = controlPanelBounds.x + 8;
        int y = controlPanelBounds.y + 8;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u005", x, y + 6, ModernUiRenderer.SUBTLE_TEXT, 30);
        radiusBounds = new ModernMainLayout.Rect(x + 34, y, 58, 21);
        drawTextField(fontRenderer, radiusField, radiusBounds, "1-50", mouseX, mouseY);
        scanBounds = new ModernMainLayout.Rect(radiusBounds.right() + 6, y, 82, 21);
        drawActionButton(fontRenderer, scanBounds, "gui.modern.scan.u006", !isScanning(), true, false, mouseX, mouseY);

        int secondY = y + 31;
        int halfWidth = Math.max(1, (controlPanelBounds.width - 22) / 2);
        refreshBounds = new ModernMainLayout.Rect(x, secondY, halfWidth, 21);
        clearBounds = new ModernMainLayout.Rect(refreshBounds.right() + 6, secondY,
                Math.max(1, controlPanelBounds.right() - 8 - refreshBounds.right()), 21);
        drawActionButton(fontRenderer, refreshBounds, "gui.modern.scan.u009", true, false, false, mouseX, mouseY);
        drawActionButton(fontRenderer, clearBounds, "gui.modern.scan.u008", !scanFiles.isEmpty(), false, true, mouseX, mouseY);
        if (controlPanelBounds.height >= CONTROL_HEIGHT_COMPACT) {
            ModernUiRenderer.drawText(fontRenderer, getActiveFeedback(), x, secondY + 28, getFeedbackColor(),
                    Math.max(20, controlPanelBounds.width - 16));
        }
    }

    private void drawWidePanels(FontRenderer fontRenderer, int mainY, int mainHeight, int mouseX, int mouseY) {
        int x = panelBounds.x + 9;
        int availableWidth = Math.max(1, panelBounds.width - 18);
        int gap = 8;
        ModernSplitPane.Split split = ModernSplitPane.calculate(Math.max(2, availableWidth - gap), recordsRatio,
                178, 250, 122, 160);
        int listWidth = split.firstWidth;
        recordsPanelBounds = new ModernMainLayout.Rect(x, mainY, listWidth, mainHeight);
        detailsPanelBounds = new ModernMainLayout.Rect(recordsPanelBounds.right() + gap, mainY,
                Math.max(1, panelBounds.right() - 9 - recordsPanelBounds.right() - gap), mainHeight);
        dividerBounds = ModernSplitPane.verticalDividerBounds(recordsPanelBounds.x, recordsPanelBounds.width, gap,
                mainY, mainHeight);
        drawRecordsPanel(fontRenderer, mouseX, mouseY);
        drawDetailsPanel(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(dividerBounds, mouseX, mouseY, draggingDivider);
    }

    private void drawCompactPanels(FontRenderer fontRenderer, int mainY, int mainHeight, int mouseX, int mouseY) {
        int x = panelBounds.x + 9;
        int width = Math.max(1, panelBounds.width - 18);
        dividerBounds = null;
        int gap = 7;
        int recordsHeight = Math.max(48, Math.min(116, (mainHeight - gap) / 2));
        if (mainHeight < 104) {
            recordsHeight = Math.max(1, (mainHeight - gap) / 2);
        }
        recordsPanelBounds = new ModernMainLayout.Rect(x, mainY, width, recordsHeight);
        detailsPanelBounds = new ModernMainLayout.Rect(x, recordsPanelBounds.bottom() + gap, width,
                Math.max(1, mainHeight - recordsHeight - gap));
        drawRecordsPanel(fontRenderer, mouseX, mouseY);
        drawDetailsPanel(fontRenderer, mouseX, mouseY);
    }

    private void drawRecordsPanel(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(recordsPanelBounds.x, recordsPanelBounds.y, recordsPanelBounds.width,
                recordsPanelBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int titleX = recordsPanelBounds.x + 9;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u010", titleX, recordsPanelBounds.y + 7,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(32, recordsPanelBounds.width - 64));
        String count = tr("gui.modern.scan.fmt.count", String.valueOf(scanFiles.size()));
        int countWidth = fontRenderer.getStringWidth(count);
        ModernUiRenderer.drawText(fontRenderer, count, recordsPanelBounds.right() - countWidth - 9,
                recordsPanelBounds.y + 7, ModernUiRenderer.MUTED_TEXT, countWidth + 2);
        ModernUiRenderer.drawDivider(recordsPanelBounds.x + 8, recordsPanelBounds.y + 23,
                Math.max(1, recordsPanelBounds.width - 16), ModernUiRenderer.BORDER_SUBTLE);

        recordsViewportBounds = new ModernMainLayout.Rect(recordsPanelBounds.x + 8, recordsPanelBounds.y + 27,
                Math.max(1, recordsPanelBounds.width - 16), Math.max(1, recordsPanelBounds.height - 34));
        recordsContentHeight = scanFiles.size() * ROW_HEIGHT;
        recordsMaxScroll = Math.max(0, recordsContentHeight - recordsViewportBounds.height);
        recordsScrollOffset = clamp(recordsScrollOffset, 0, recordsMaxScroll);

        ModernUiRenderer.beginClip(recordsViewportBounds);
        if (scanFiles.isEmpty()) {
            ModernUiRenderer.drawSearchIcon(recordsViewportBounds.x + recordsViewportBounds.width / 2 - 6,
                    recordsViewportBounds.y + Math.max(4, recordsViewportBounds.height / 2 - 14),
                    ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u011", recordsViewportBounds.x + 8,
                    recordsViewportBounds.y + Math.max(22, recordsViewportBounds.height / 2 + 4),
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, recordsViewportBounds.width - 16));
        } else {
            int rowWidth = ModernHoverScrollbar.contentWidth(recordsViewportBounds.width);
            for (int i = 0; i < scanFiles.size(); i++) {
                String fileName = scanFiles.get(i);
                int rowY = recordsViewportBounds.y + i * ROW_HEIGHT - recordsScrollOffset;
                ModernMainLayout.Rect rowBounds = new ModernMainLayout.Rect(recordsViewportBounds.x, rowY, rowWidth,
                        ROW_HEIGHT - 2);
                if (!intersects(rowBounds, recordsViewportBounds)) {
                    continue;
                }
                boolean selected = fileName.equals(selectedFileName);
                boolean hovered = recordsViewportBounds.contains(mouseX, mouseY) && rowBounds.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, 4,
                        selected ? 0xFF2C3D49 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(rowBounds.x + 8, rowBounds.y + 8,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
                ModernUiRenderer.drawText(fontRenderer, fileName, rowBounds.x + 21, rowBounds.y + 6,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(20, rowBounds.width - 27));
                recordHits.add(new RecordHit(fileName, rowBounds));
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(recordsScrollbar, recordsViewportBounds, recordsMaxScroll, recordsContentHeight,
                recordsScrollOffset, mouseX, mouseY, value -> recordsScrollOffset = value);
    }

    private void drawDetailsPanel(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(detailsPanelBounds.x, detailsPanelBounds.y, detailsPanelBounds.width,
                detailsPanelBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (selectedFileName == null) {
            copyBounds = null;
            renameBounds = null;
            renameSaveBounds = null;
            deleteBounds = null;
            detailsViewportBounds = new ModernMainLayout.Rect(detailsPanelBounds.x + 8, detailsPanelBounds.y + 27,
                    Math.max(1, detailsPanelBounds.width - 16), Math.max(1, detailsPanelBounds.height - 35));
            detailsMaxScroll = 0;
            detailsContentHeight = 0;
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u012", detailsPanelBounds.x + 10,
                    detailsPanelBounds.y + Math.max(31, detailsPanelBounds.height / 2), ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, detailsPanelBounds.width - 20));
            detailsScrollbar.idle();
            return;
        }

        int titleX = detailsPanelBounds.x + 9;
        int copyWidth = Math.min(56, Math.max(42, detailsPanelBounds.width / 4));
        copyBounds = new ModernMainLayout.Rect(detailsPanelBounds.right() - copyWidth - 8, detailsPanelBounds.y + 5,
                copyWidth, 20);
        ModernUiRenderer.drawText(fontRenderer, selectedFileName, titleX, detailsPanelBounds.y + 8,
                ModernUiRenderer.TEXT, Math.max(24, copyBounds.x - titleX - 7));
        drawActionButton(fontRenderer, copyBounds, "gui.modern.scan.u013", true, false, false, mouseX, mouseY);
        ModernUiRenderer.drawDivider(detailsPanelBounds.x + 8, detailsPanelBounds.y + 29,
                Math.max(1, detailsPanelBounds.width - 16), ModernUiRenderer.BORDER_SUBTLE);

        int footerHeight = 31;
        detailsViewportBounds = new ModernMainLayout.Rect(detailsPanelBounds.x + 8, detailsPanelBounds.y + 34,
                Math.max(1, detailsPanelBounds.width - 16),
                Math.max(1, detailsPanelBounds.height - 34 - footerHeight));
        int contentWidth = ModernHoverScrollbar.contentWidth(detailsViewportBounds.width - 4);
        prepareWrappedContent(fontRenderer, contentWidth);
        detailsContentHeight = wrappedContentLines.size() * CONTENT_LINE_HEIGHT;
        detailsMaxScroll = Math.max(0, detailsContentHeight - detailsViewportBounds.height);
        detailsScrollOffset = clamp(detailsScrollOffset, 0, detailsMaxScroll);

        ModernUiRenderer.beginClip(detailsViewportBounds);
        if (wrappedContentLines.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.scan.u014", detailsViewportBounds.x + 5,
                    detailsViewportBounds.y + 6, ModernUiRenderer.MUTED_TEXT, contentWidth);
        } else {
            int textY = detailsViewportBounds.y + 5 - detailsScrollOffset;
            for (String line : wrappedContentLines) {
                if (textY + CONTENT_LINE_HEIGHT >= detailsViewportBounds.y
                        && textY <= detailsViewportBounds.bottom()) {
                    fontRenderer.drawString(line, detailsViewportBounds.x + 5, textY, ModernUiRenderer.TEXT);
                }
                textY += CONTENT_LINE_HEIGHT;
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(detailsScrollbar, detailsViewportBounds, detailsMaxScroll, detailsContentHeight,
                detailsScrollOffset, mouseX, mouseY, value -> detailsScrollOffset = value);

        int footerY = detailsPanelBounds.bottom() - footerHeight;
        ModernUiRenderer.drawDivider(detailsPanelBounds.x + 8, footerY, Math.max(1, detailsPanelBounds.width - 16),
                ModernUiRenderer.BORDER_SUBTLE);
        int gap = 4;
        int buttonWidth = Math.min(54, Math.max(42, detailsPanelBounds.width / 5));
        deleteBounds = new ModernMainLayout.Rect(detailsPanelBounds.right() - buttonWidth - 8, footerY + 5, buttonWidth, 21);
        renameSaveBounds = new ModernMainLayout.Rect(deleteBounds.x - gap - buttonWidth, footerY + 5, buttonWidth, 21);
        int inputRight = renameSaveBounds.x - gap;
        renameBounds = new ModernMainLayout.Rect(detailsPanelBounds.x + 8, footerY + 5,
                Math.max(1, inputRight - detailsPanelBounds.x - 8), 21);
        drawTextField(fontRenderer, renameField, renameBounds, "gui.modern.scan.u015", mouseX, mouseY);
        drawActionButton(fontRenderer, renameSaveBounds, "gui.modern.scan.u016", true, false, false, mouseX, mouseY);
        drawActionButton(fontRenderer, deleteBounds, "gui.modern.scan.u017", true, false, true, mouseX, mouseY);
    }

    private void drawConfirmation(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int width = Math.min(360, Math.max(190, panelBounds.width - 24));
        int height = 72;
        int x = panelBounds.x + (panelBounds.width - width) / 2;
        int y = panelBounds.y + (panelBounds.height - height) / 2;
        ModernUiRenderer.drawPanel(x, y, width, height, 6, 0xFF18232D, ModernUiRenderer.BORDER);
        String title = confirmation == Confirmation.DELETE ? "gui.modern.scan.u018" : "gui.modern.scan.u019";
        String message = confirmation == Confirmation.DELETE
                ? tr("gui.modern.scan.fmt.delete", safe(pendingFileName))
                : "gui.modern.scan.u020";
        ModernUiRenderer.drawText(fontRenderer, title, x + 10, y + 9, ModernUiRenderer.TEXT, width - 20);
        ModernUiRenderer.drawText(fontRenderer, message, x + 10, y + 23, ModernUiRenderer.SUBTLE_TEXT, width - 20);

        int buttonWidth = Math.min(88, Math.max(60, (width - 30) / 2));
        confirmationNoBounds = new ModernMainLayout.Rect(x + width - buttonWidth * 2 - 14, y + 45, buttonWidth, 20);
        confirmationYesBounds = new ModernMainLayout.Rect(x + width - buttonWidth - 7, y + 45, buttonWidth, 20);
        drawActionButton(fontRenderer, confirmationNoBounds, "gui.modern.scan.u021", true, false, false, mouseX, mouseY);
        drawActionButton(fontRenderer, confirmationYesBounds, "gui.modern.scan.u022", true, true, confirmation == Confirmation.DELETE,
                mouseX, mouseY);
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect viewport, int maxScroll,
            int contentHeight, int scroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (viewport == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(viewport, scroll, maxScroll, viewport.height, Math.max(viewport.height, contentHeight), mouseX, mouseY,
                setter);
    }

    private void drawTextField(FontRenderer fontRenderer, GuiTextField field, ModernMainLayout.Rect bounds,
            String placeholder, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean focused = field != null && field.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        if (field == null) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        if (field.getText().isEmpty() && !focused) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, field.x, field.y, ModernUiRenderer.MUTED_TEXT,
                    field.width);
        }
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean enabled,
            boolean primary, boolean danger, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill;
        int border;
        int textColor;
        if (!enabled) {
            fill = 0xFF202A32;
            border = ModernUiRenderer.BORDER_SUBTLE;
            textColor = ModernUiRenderer.MUTED_TEXT;
        } else if (danger) {
            fill = hovered ? 0xFF6A3544 : 0xFF4A2935;
            border = hovered ? 0xFFE06A78 : 0xFF9C5266;
            textColor = ModernUiRenderer.TEXT;
        } else if (primary) {
            fill = hovered ? 0xFFFFA4BC : ModernUiRenderer.ACCENT;
            border = ModernUiRenderer.ACCENT;
            textColor = ModernUiRenderer.SHELL;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED;
            border = hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
            textColor = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, textColor, Math.max(10, bounds.width - 8));
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

    private void startScan() {
        if (isScanning()) {
            showStatus("gui.modern.scan.u023");
            return;
        }
        int radius;
        try {
            radius = Integer.parseInt(radiusField == null ? "" : radiusField.getText().trim());
        } catch (NumberFormatException ignored) {
            showStatus("gui.modern.scan.u024");
            return;
        }
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            showStatus("gui.modern.scan.u024");
            return;
        }
        if (radiusField != null) {
            radiusField.setText(String.valueOf(radius));
        }
        clearFieldFocus();
        TerrainScanManager.saveLastRadius(radius);
        TerrainScannerHandler.scanAndSaveTerrain(radius);
        if (isScanning()) {
            showStatus("gui.modern.scan.u025");
        } else {
            showStatus("gui.modern.scan.u026");
        }
    }

    private void refreshRecords() {
        String previousSelection = selectedFileName;
        scanFiles = new ArrayList<>(TerrainScanManager.getAllScanNames());
        if (previousSelection != null && scanFiles.contains(previousSelection)) {
            selectedFileName = previousSelection;
        } else if (previousSelection != null) {
            clearSelectedFile();
        }
        recordsScrollOffset = clamp(recordsScrollOffset, 0, Math.max(0, scanFiles.size() * ROW_HEIGHT));
        if (renameField != null && selectedFileName != null && !renameField.isFocused()) {
            renameField.setText(selectedFileName);
        }
    }

    private void selectFile(String fileName) {
        if (fileName == null || fileName.equals(selectedFileName)) {
            return;
        }
        selectedFileName = fileName;
        fullContent = TerrainScanManager.readScanContent(fileName);
        rawContentLines = splitLines(fullContent);
        wrappedContentLines = Collections.emptyList();
        wrappedContentWidth = -1;
        detailsScrollOffset = 0;
        if (renameField != null) {
            renameField.setText(fileName);
        }
        clearFieldFocus();
    }

    private void clearSelectedFile() {
        selectedFileName = null;
        fullContent = "";
        rawContentLines = Collections.emptyList();
        wrappedContentLines = Collections.emptyList();
        wrappedContentWidth = -1;
        detailsScrollOffset = 0;
        if (renameField != null) {
            renameField.setText("");
        }
    }

    private void copySelectedContent() {
        if (selectedFileName == null) {
            showStatus("gui.modern.scan.u027");
            return;
        }
        GuiScreen.setClipboardString(fullContent);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendMessage(new TextComponentString(I18n.format("msg.scan_viewer.copy_success")));
        }
        showStatus("gui.modern.scan.u028");
    }

    private void renameSelectedFile() {
        if (selectedFileName == null) {
            showStatus("gui.modern.scan.u027");
            return;
        }
        String newName = renameField == null ? "" : renameField.getText().trim();
        if (newName.isEmpty()) {
            showStatus("gui.modern.scan.u029");
            return;
        }
        if (!newName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            newName += ".json";
        }
        if (newName.equals(selectedFileName)) {
            showStatus("gui.modern.scan.u030");
            clearFieldFocus();
            return;
        }
        String oldName = selectedFileName;
        if (TerrainScanManager.renameScan(oldName, newName)) {
            selectedFileName = newName;
            refreshRecords();
            fullContent = TerrainScanManager.readScanContent(selectedFileName);
            rawContentLines = splitLines(fullContent);
            wrappedContentWidth = -1;
            if (renameField != null) {
                renameField.setText(selectedFileName);
            }
            showStatus("gui.modern.scan.u031");
        } else {
            showStatus("gui.modern.scan.u032");
        }
        clearFieldFocus();
    }

    private void requestDeleteSelected() {
        if (selectedFileName == null) {
            showStatus("gui.modern.scan.u027");
            return;
        }
        pendingFileName = selectedFileName;
        confirmation = Confirmation.DELETE;
    }

    private void requestClearAll() {
        if (scanFiles.isEmpty()) {
            showStatus("gui.modern.scan.u033");
            return;
        }
        pendingFileName = null;
        confirmation = Confirmation.CLEAR;
    }

    private void confirmPendingAction() {
        if (confirmation == Confirmation.DELETE && pendingFileName != null) {
            boolean deleted = TerrainScanManager.deleteScan(pendingFileName);
            if (pendingFileName.equals(selectedFileName)) {
                clearSelectedFile();
            }
            refreshRecords();
            showStatus(deleted ? "gui.modern.scan.u034" : "gui.modern.scan.u035");
        } else if (confirmation == Confirmation.CLEAR) {
            TerrainScanManager.deleteAllScans();
            clearSelectedFile();
            refreshRecords();
            showStatus("gui.modern.scan.u036");
        }
        clearConfirmation();
    }

    private void clearConfirmation() {
        confirmation = Confirmation.NONE;
        pendingFileName = null;
        confirmationYesBounds = null;
        confirmationNoBounds = null;
    }

    private void pollScanStatus() {
        TerrainScannerHandler.ScanStatus status = TerrainScannerHandler.getScanStatus();
        if (status == null || status.isScanning() || status.getFinishedAt() <= 0L
                || status.getFinishedAt() == lastSeenFinishedAt) {
            return;
        }
        lastSeenFinishedAt = status.getFinishedAt();
        refreshRecords();
        if (!status.getErrorMessage().isEmpty()) {
            showStatus("gui.modern.scan.u037");
        } else {
            showStatus(tr("gui.modern.scan.fmt.done", String.valueOf(status.getBlockCount())));
        }
    }

    private void prepareWrappedContent(FontRenderer fontRenderer, int width) {
        if (wrappedContentWidth == width) {
            return;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : rawContentLines) {
            if (line == null || line.isEmpty()) {
                wrapped.add("");
                continue;
            }
            List<String> parts = fontRenderer.listFormattedStringToWidth(line, width);
            if (parts == null || parts.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(parts);
            }
        }
        wrappedContentLines = wrapped;
        wrappedContentWidth = width;
    }

    private void focusField(GuiTextField focused, GuiTextField other) {
        if (other != null) {
            other.setFocused(false);
        }
        if (focused != null) {
            focused.setFocused(true);
        }
    }

    private void clearFieldFocus() {
        if (radiusField != null) {
            radiusField.setFocused(false);
        }
        if (renameField != null) {
            renameField.setFocused(false);
        }
    }

    private GuiTextField createTextField(FontRenderer fontRenderer, int maxLength) {
        GuiTextField field = new GuiTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(maxLength);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.SUBTLE_TEXT);
        field.setVisible(true);
        field.setEnabled(true);
        return field;
    }

    private String buildStatusLabel(TerrainScannerHandler.ScanStatus status) {
        if (status == null) {
            return tr("gui.modern.scan.fmt.records", String.valueOf(scanFiles.size()));
        }
        if (status.isScanning()) {
            return tr("gui.modern.scan.fmt.progress", String.valueOf(status.getProcessedBlocks()), String.valueOf(status.getTotalBlocks()));
        }
        if (!status.getErrorMessage().isEmpty()) {
            return "gui.modern.scan.u038";
        }
        if (status.getFinishedAt() > 0L) {
            return tr("gui.modern.scan.fmt.finished", String.valueOf(status.getBlockCount()));
        }
        return tr("gui.modern.scan.fmt.records", String.valueOf(scanFiles.size()));
    }

    private String getActiveFeedback() {
        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil) {
            return statusMessage;
        }
        TerrainScannerHandler.ScanStatus status = TerrainScannerHandler.getScanStatus();
        if (status != null && status.isScanning()) {
            return "gui.modern.scan.u039";
        }
        return "gui.modern.scan.u040";
    }

    private int getFeedbackColor() {
        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil) {
            return statusMessage.contains("gui.modern.scan.u041") || statusMessage.contains("gui.modern.scan.u042") || statusMessage.contains("gui.modern.scan.u043")
                    ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS;
        }
        return ModernUiRenderer.MUTED_TEXT;
    }

    private void showStatus(String message) {
        statusMessage = safe(message);
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private int getStatusColor(TerrainScannerHandler.ScanStatus status) {
        if (status == null || (!status.isScanning() && status.getFinishedAt() <= 0L
                && status.getErrorMessage().isEmpty())) {
            return ModernUiRenderer.MUTED_TEXT;
        }
        if (status.isScanning()) {
            return ModernUiRenderer.WARNING;
        }
        if (!status.getErrorMessage().isEmpty()) {
            return 0xFFE06A78;
        }
        return ModernUiRenderer.SUCCESS;
    }

    private boolean isScanning() {
        TerrainScannerHandler.ScanStatus status = TerrainScannerHandler.getScanStatus();
        return status != null && status.isScanning();
    }

    private static List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(content.replace("\r", "").split("\n", -1));
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect requestedBounds) {
        ModernMainLayout.Rect source = requestedBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1)
                : requestedBounds;
        int insetX = Math.max(6, Math.min(12, source.width / 18));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
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
