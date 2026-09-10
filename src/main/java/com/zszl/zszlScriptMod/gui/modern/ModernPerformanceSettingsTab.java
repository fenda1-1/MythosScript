package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/**
 * Embedded performance workbench for the modern main-window tab strip.
 *
 * <p>The monitor is intentionally self-contained here instead of exposing a
 * landing page with a second-screen action. The card list is the primary view,
 * while protection controls stay pinned above it so they remain available as
 * the list is scrolled.</p>
 */
public final class ModernPerformanceSettingsTab implements ModernSettingsTab {

    private static final int HEADER_HEIGHT = 46;
    private static final int CONTROL_HEIGHT_WIDE = 68;
    private static final int CONTROL_HEIGHT_COMPACT = 98;
    private static final int CARD_HEIGHT = 132;
    private static final int CARD_GAP = 8;
    private static final int MIN_CARD_WIDTH = 250;

    private static final List<String> FEATURE_ORDER = Collections.unmodifiableList(Arrays.asList(
            "auto_equip",
            "auto_pickup",
            "auto_follow",
            "auto_escape",
            "kill_aura",
            "path_sequence",
            "conditional_execution",
            "debuff_detector",
            "goto_open",
            "warehouse",
            "block_replacement",
            "render_features_tick",
            "render_features_world",
            "render_features_overlay",
            "trigger_system",
            "auto_eat",
            "auto_use_item",
            "timed_message",
            "packet_capture_inbound",
            "packet_capture_outbound",
            "packet_intercept",
            "packet_send_fml",
            "packet_send_standard"));

    private static final class CardHit {
        private final String featureName;
        private final ModernMainLayout.Rect cardBounds;
        private final ModernMainLayout.Rect toggleBounds;
        private final ModernMainLayout.Rect infoBounds;

        private CardHit(String featureName, ModernMainLayout.Rect cardBounds,
                ModernMainLayout.Rect toggleBounds, ModernMainLayout.Rect infoBounds) {
            this.featureName = featureName;
            this.cardBounds = cardBounds;
            this.toggleBounds = toggleBounds;
            this.infoBounds = infoBounds;
        }
    }

    private final List<CardHit> cardHits = new ArrayList<>();
    private List<String> featureNames = Collections.emptyList();

    private GuiTextField thresholdField;
    private GuiTextField disableDurationField;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect controlPanelBounds;
    private ModernMainLayout.Rect scrollClipBounds;
    private ModernMainLayout.Rect guardBounds;
    private ModernMainLayout.Rect resetBounds;
    private ModernMainLayout.Rect applyBounds;
    private ModernMainLayout.Rect thresholdBounds;
    private ModernMainLayout.Rect disableDurationBounds;
    private ModernMainLayout.Rect guardInfoBounds;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private int scrollOffset;
    private int maxScrollOffset;
    private int contentHeight;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean statusError;
    private PacketCaptureHandler.PacketCaptureUiSnapshot packetSnapshot;
    private boolean initialized;
    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        thresholdField = createTextField(fontRenderer, 16);
        thresholdField.setText(formatThreshold(PerformanceMonitor.getSpikeThresholdMillis()));
        disableDurationField = createTextField(fontRenderer, 16);
        disableDurationField.setText(String.valueOf(PerformanceMonitor.getSpikeDisableDurationMs()));
        initialized = true;
    }

    @Override
    public void updateScreen() {
        if (thresholdField != null) {
            thresholdField.updateCursorCounter();
        }
        if (disableDurationField != null) {
            disableDurationField.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        contentBounds = requestedContentBounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredTooltip = "";
        cardHits.clear();
        refreshFeatureOrder();
        packetSnapshot = PacketCaptureHandler.getUiSnapshot();

        panelBounds = buildPanelBounds(requestedContentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(fontRenderer, mouseX, mouseY);
        ModernUiRenderer.drawDivider(panelBounds.x + 12, panelBounds.y + HEADER_HEIGHT,
                Math.max(1, panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);

        boolean compact = panelBounds.width < 500;
        int controlHeight = compact ? CONTROL_HEIGHT_COMPACT : CONTROL_HEIGHT_WIDE;
        int controlY = panelBounds.y + HEADER_HEIGHT + 8;
        controlPanelBounds = new ModernMainLayout.Rect(panelBounds.x + 10, controlY,
                Math.max(1, panelBounds.width - 20), controlHeight);
        drawProtectionControls(fontRenderer, compact, mouseX, mouseY);

        int scrollY = controlPanelBounds.bottom() + 8;
        int scrollBottom = panelBounds.bottom() - 8;
        scrollClipBounds = new ModernMainLayout.Rect(panelBounds.x + 8, scrollY,
                Math.max(1, panelBounds.width - 16), Math.max(1, scrollBottom - scrollY));

        int cardAreaWidth = ModernHoverScrollbar.contentWidth(scrollClipBounds.width - 7);
        int columns = getColumnCount(cardAreaWidth);
        int cardWidth = getCardWidth(cardAreaWidth, columns);
        int rows = Math.max(1, (featureNames.size() + columns - 1) / columns);
        contentHeight = rows * CARD_HEIGHT + Math.max(0, rows - 1) * CARD_GAP;
        maxScrollOffset = Math.max(0, contentHeight - scrollClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);

        ModernUiRenderer.beginClip(scrollClipBounds);
        if (featureNames.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u001", scrollClipBounds.x + 12,
                    scrollClipBounds.y + 12, ModernUiRenderer.MUTED_TEXT,
                    Math.max(20, scrollClipBounds.width - 24));
        } else {
            for (int index = 0; index < featureNames.size(); index++) {
                int row = index / columns;
                int column = index % columns;
                int x = scrollClipBounds.x + 7 + column * (cardWidth + CARD_GAP);
                int y = scrollClipBounds.y + 2 + row * (CARD_HEIGHT + CARD_GAP) - scrollOffset;
                if (y + CARD_HEIGHT < scrollClipBounds.y || y > scrollClipBounds.bottom()) {
                    continue;
                }
                ModernMainLayout.Rect cardBounds = new ModernMainLayout.Rect(x, y, cardWidth, CARD_HEIGHT);
                drawFeatureCard(fontRenderer, cardBounds, featureNames.get(index), mouseX, mouseY);
            }
        }
        ModernUiRenderer.endClip();
        drawScrollbar(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }

        if (contains(guardInfoBounds, mouseX, mouseY)) {
            return true;
        }
        for (CardHit hit : cardHits) {
            if (contains(hit.infoBounds, mouseX, mouseY)) {
                return true;
            }
        }
        if (contains(resetBounds, mouseX, mouseY)) {
            PerformanceMonitor.resetAllStats();
            showStatus("gui.modern.perf.u002");
            return true;
        }
        if (contains(guardBounds, mouseX, mouseY)) {
            PerformanceMonitor.setSpikeGuardEnabled(!PerformanceMonitor.isSpikeGuardEnabled());
            showStatus(PerformanceMonitor.isSpikeGuardEnabled() ? "gui.modern.perf.u003" : "gui.modern.perf.u004");
            return true;
        }
        if (contains(applyBounds, mouseX, mouseY)) {
            applyGuardSettings();
            return true;
        }
        if (contains(thresholdBounds, mouseX, mouseY)) {
            focusField(thresholdField, disableDurationField);
            thresholdField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (contains(disableDurationBounds, mouseX, mouseY)) {
            focusField(disableDurationField, thresholdField);
            disableDurationField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }

        if (scrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }

        clearFieldFocus();
        for (CardHit hit : cardHits) {
            if (!contains(hit.cardBounds, mouseX, mouseY)) {
                continue;
            }
            if (contains(hit.toggleBounds, mouseX, mouseY)) {
                PerformanceMonitor.setFeatureEnabled(hit.featureName,
                        !PerformanceMonitor.isFeatureManuallyEnabled(hit.featureName));
                showStatus(PerformanceMonitor.isFeatureManuallyEnabled(hit.featureName)
                        ? tr("gui.modern.perf.fmt.enabled", getDisplayName(hit.featureName))
                        : tr("gui.modern.perf.fmt.disabled", getDisplayName(hit.featureName)));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (thresholdField != null && thresholdField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (disableDurationField != null && disableDurationField.textboxKeyTyped(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && hasFocusedField()) {
            applyGuardSettings();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && scrollbar.isDragging()) {
            scrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && scrollbar.isDragging()) {
            scrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (scrollbar.isDragging()) {
            scrollbar.endDrag();
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
        if (wheel == 0 || maxScrollOffset <= 0) {
            return false;
        }
        if (scrollClipBounds == null || !scrollClipBounds.contains(mouseX, mouseY)) {
            return false;
        }
        int previous = scrollOffset;
        scrollOffset = clamp(scrollOffset + (wheel > 0 ? -32 : 32), 0, maxScrollOffset);
        return previous != scrollOffset;
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
        clearFieldFocus();
        syncInputFields();
        scrollOffset = 0;
        scrollbar.endDrag();
    }

    @Override
    public boolean isDirty() {
        if (!initialized || thresholdField == null || disableDurationField == null) {
            return false;
        }
        return !formatThreshold(PerformanceMonitor.getSpikeThresholdMillis())
                .equals(thresholdField.getText().trim())
                || !String.valueOf(PerformanceMonitor.getSpikeDisableDurationMs())
                        .equals(disableDurationField.getText().trim());
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

    private void drawHeader(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int rightWidth = Math.min(164, Math.max(84, panelBounds.width / 3));
        int rightX = panelBounds.right() - rightWidth - 14;
        int titleX = panelBounds.x + 15;
        int titleWidth = Math.max(32, rightX - titleX - 10);
        String title = "gui.modern.perf.u005";
        ModernUiRenderer.drawText(fontRenderer, title, titleX, panelBounds.y + 9, ModernUiRenderer.TEXT, titleWidth);
        drawInfoIcon(titleX + Math.min(Math.max(0, titleWidth - 11), fontRenderer.getStringWidth(title) + 5),
                panelBounds.y + 8, "gui.modern.perf.u006", mouseX, mouseY);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u007", titleX, panelBounds.y + 23,
                ModernUiRenderer.MUTED_TEXT, titleWidth);

        ModernMainLayout.Rect summaryBounds = new ModernMainLayout.Rect(rightX, panelBounds.y + 10, rightWidth, 20);
        boolean statusVisible = statusMessage != null && !statusMessage.isEmpty()
                && System.currentTimeMillis() < statusMessageUntil;
        ModernUiRenderer.drawSubtlePanel(summaryBounds.x, summaryBounds.y, summaryBounds.width, summaryBounds.height, 5,
                statusVisible ? 0xFF243B45 : ModernUiRenderer.SURFACE,
                statusVisible ? ModernUiRenderer.SUCCESS : ModernUiRenderer.BORDER_SUBTLE);
        String summary = statusVisible ? statusMessage : buildHotspotSummary();
        ModernUiRenderer.drawText(fontRenderer, summary, summaryBounds.x + 7,
                summaryBounds.y + (summaryBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                statusVisible ? statusError ? 0xFFFF8E8E : ModernUiRenderer.SUCCESS : getHotspotColor(),
                summaryBounds.width - 14);
    }

    private void drawProtectionControls(FontRenderer fontRenderer, boolean compact, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(controlPanelBounds.x, controlPanelBounds.y, controlPanelBounds.width,
                controlPanelBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int x = controlPanelBounds.x;
        int y = controlPanelBounds.y;
        int width = controlPanelBounds.width;

        if (compact) {
            guardBounds = new ModernMainLayout.Rect(x + 8, y + 8, 96, 20);
            drawGuardControl(fontRenderer, guardBounds, mouseX, mouseY);
            resetBounds = new ModernMainLayout.Rect(controlPanelBounds.right() - 76, y + 8, 68, 20);
            drawActionButton(fontRenderer, resetBounds, "gui.modern.perf.u008", false, mouseX, mouseY);

            int groupWidth = Math.max(70, (width - 24) / 2);
            int fieldY = y + 35;
            int thresholdLabelX = x + 8;
            int thresholdFieldX = thresholdLabelX + 40;
            int thresholdWidth = Math.max(30, groupWidth - 40);
            thresholdBounds = new ModernMainLayout.Rect(thresholdFieldX, fieldY, thresholdWidth, 20);
            drawInputField(fontRenderer, thresholdField, thresholdBounds, mouseX, mouseY);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u009", thresholdLabelX, fieldY + 6, ModernUiRenderer.SUBTLE_TEXT, 35);

            int durationX = x + 12 + groupWidth;
            int durationFieldX = durationX + 48;
            int durationWidth = Math.max(30, controlPanelBounds.right() - 8 - durationFieldX);
            disableDurationBounds = new ModernMainLayout.Rect(durationFieldX, fieldY, durationWidth, 20);
            drawInputField(fontRenderer, disableDurationField, disableDurationBounds, mouseX, mouseY);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u010", durationX, fieldY + 6, ModernUiRenderer.SUBTLE_TEXT, 45);

            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u011", x + 8, y + 69,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, width - 88));
            applyBounds = new ModernMainLayout.Rect(controlPanelBounds.right() - 76, y + 63, 68, 20);
            drawActionButton(fontRenderer, applyBounds, "gui.modern.perf.u012", true, mouseX, mouseY);
        } else {
            guardBounds = new ModernMainLayout.Rect(x + 8, y + 8, 86, 20);
            drawGuardControl(fontRenderer, guardBounds, mouseX, mouseY);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u009", x + 103, y + 14, ModernUiRenderer.SUBTLE_TEXT, 28);
            thresholdBounds = new ModernMainLayout.Rect(x + 134, y + 8, 62, 20);
            drawInputField(fontRenderer, thresholdField, thresholdBounds, mouseX, mouseY);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u010", x + 205, y + 14, ModernUiRenderer.SUBTLE_TEXT, 46);
            disableDurationBounds = new ModernMainLayout.Rect(x + 254, y + 8, 62, 20);
            drawInputField(fontRenderer, disableDurationField, disableDurationBounds, mouseX, mouseY);

            applyBounds = new ModernMainLayout.Rect(controlPanelBounds.right() - 144, y + 8, 64, 20);
            resetBounds = new ModernMainLayout.Rect(controlPanelBounds.right() - 76, y + 8, 68, 20);
            drawActionButton(fontRenderer, applyBounds, "gui.modern.perf.u012", true, mouseX, mouseY);
            drawActionButton(fontRenderer, resetBounds, "gui.modern.perf.u008", false, mouseX, mouseY);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u013", x + 8, y + 43,
                    ModernUiRenderer.MUTED_TEXT, Math.max(20, width - 16));
        }
    }

    private void drawGuardControl(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        boolean enabled = PerformanceMonitor.isSpikeGuardEnabled();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawToggle(bounds.x + 2, bounds.y + 2, 36, 16, enabled, hovered);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u014", bounds.x + 44, bounds.y + 5,
                enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT, bounds.width - 48);
        guardInfoBounds = drawInfoIcon(bounds.x + bounds.width - 12, bounds.y + 4,
                "gui.modern.perf.u015", mouseX, mouseY);
    }

    private void drawActionButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label,
            boolean primary, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = primary ? (hovered ? 0xFFFFA4BC : ModernUiRenderer.ACCENT)
                : (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED);
        int border = primary ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label,
                bounds.x + Math.max(5, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(12, bounds.width - 10));
    }

    private void drawInputField(FontRenderer fontRenderer, GuiTextField field, ModernMainLayout.Rect bounds,
            int mouseX, int mouseY) {
        if (field == null || bounds == null) {
            return;
        }
        boolean focused = field.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        field.setVisible(true);
        field.setEnabled(true);
        field.x = bounds.x + 6;
        field.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        field.width = Math.max(1, bounds.width - 12);
        field.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawFeatureCard(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String featureName,
            int mouseX, int mouseY) {
        boolean manuallyEnabled = PerformanceMonitor.isFeatureManuallyEnabled(featureName);
        long temporaryRemaining = PerformanceMonitor.getTemporaryDisableRemainingMs(featureName);
        PerformanceMonitor.PerformanceStats stats = PerformanceMonitor.getPerformanceStats(featureName);
        boolean cardHovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                cardHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                cardHovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);

        String displayName = getDisplayName(featureName);
        ModernUiRenderer.drawStatusDot(bounds.x + 10, bounds.y + 12,
                manuallyEnabled ? ModernUiRenderer.SUCCESS : 0xFFE06A78);
        ModernUiRenderer.drawText(fontRenderer, displayName, bounds.x + 22, bounds.y + 7, ModernUiRenderer.TEXT,
                Math.max(50, bounds.width - 100));
        ModernMainLayout.Rect infoBounds = drawInfoIcon(bounds.right() - 24, bounds.y + 7,
                displayName + "\n" + PerformanceMonitor.getFeatureDescription(featureName), mouseX, mouseY);

        ModernMainLayout.Rect toggleBounds = new ModernMainLayout.Rect(bounds.right() - 59, bounds.y + 8, 42, 16);
        ModernUiRenderer.drawToggle(toggleBounds.x, toggleBounds.y, toggleBounds.width, toggleBounds.height,
                manuallyEnabled, cardHovered && toggleBounds.contains(mouseX, mouseY));
        String status;
        int statusColor;
        if (!manuallyEnabled) {
            status = "gui.modern.perf.u016";
            statusColor = 0xFFE06A78;
        } else if (temporaryRemaining > 0L) {
            status = tr("gui.modern.perf.fmt.temp", String.valueOf(temporaryRemaining));
            statusColor = ModernUiRenderer.WARNING;
        } else {
            status = "gui.modern.perf.u017";
            statusColor = ModernUiRenderer.SUCCESS;
        }
        ModernUiRenderer.drawText(fontRenderer, status, bounds.x + 10, bounds.y + 25, statusColor,
                Math.max(80, bounds.width - 20));

        boolean hasStats = stats != null && stats.getMeasurementCount() > 0L;
        if (hasStats) {
            double cpu = stats.getCpuUsagePercent();
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u018", bounds.x + 10, bounds.y + 42,
                    ModernUiRenderer.SUBTLE_TEXT, bounds.width - 20);
            ModernUiRenderer.drawText(fontRenderer, tr("gui.modern.perf.u019", Double.valueOf(cpu)),
                    bounds.x + 10, bounds.y + 55, getCpuColor(cpu), bounds.width - 20);
            ModernUiRenderer.drawText(fontRenderer,
                    tr("gui.modern.perf.u020", Double.valueOf(stats.getAverageTimeMillis()),
                            Double.valueOf(stats.getMaxTimeMillis())), bounds.x + 10, bounds.y + 68,
                    ModernUiRenderer.TEXT, bounds.width - 20);
            ModernUiRenderer.drawText(fontRenderer,
                    tr("gui.modern.perf.u021", Double.valueOf(stats.getMinTimeMillis()),
                            Double.valueOf(stats.getLastMeasurementMillis())), bounds.x + 10, bounds.y + 81,
                    ModernUiRenderer.TEXT, bounds.width - 20);
            ModernUiRenderer.drawText(fontRenderer,
                    tr("gui.modern.perf.u022", Long.valueOf(stats.getMeasurementCount()),
                            Double.valueOf(stats.getTotalTimeNanos() / 1_000_000.0D)), bounds.x + 10, bounds.y + 94,
                    ModernUiRenderer.MUTED_TEXT, bounds.width - 20);
            drawPacketSummary(fontRenderer, bounds, featureName, bounds.y + 107);
        } else {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.perf.u023", bounds.x + 10, bounds.y + 55,
                    ModernUiRenderer.MUTED_TEXT, bounds.width - 20);
            drawPacketSummary(fontRenderer, bounds, featureName, bounds.y + 72);
        }
        cardHits.add(new CardHit(featureName, bounds, toggleBounds, infoBounds));
    }

    private void drawPacketSummary(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String featureName, int y) {
        if (packetSnapshot == null || featureName == null || !featureName.startsWith("packet_")) {
            return;
        }
        String text = tr("gui.modern.perf.fmt.queue", String.valueOf(packetSnapshot.queueSize),
                String.valueOf(packetSnapshot.droppedCount), String.valueOf(Math.max(1, packetSnapshot.samplingModulo)));
        ModernUiRenderer.drawText(fontRenderer, text, bounds.x + 10, y, ModernUiRenderer.MUTED_TEXT,
                bounds.width - 20);
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (scrollClipBounds == null || maxScrollOffset <= 0) {
            scrollbar.idle();
            return;
        }
        scrollbar.draw(scrollClipBounds, scrollOffset, maxScrollOffset, scrollClipBounds.height,
                Math.max(scrollClipBounds.height, contentHeight), mouseX, mouseY, value -> scrollOffset = value);
    }

    private ModernMainLayout.Rect drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return null;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        return bounds;
    }

    private void refreshFeatureOrder() {
        Map<String, Boolean> states = PerformanceMonitor.getAllFeatureStates();
        List<String> ordered = new ArrayList<>();
        for (String feature : FEATURE_ORDER) {
            if (states.containsKey(feature)) {
                ordered.add(feature);
            }
        }
        for (String feature : states.keySet()) {
            if (!ordered.contains(feature)) {
                ordered.add(feature);
            }
        }
        ordered.sort(new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                PerformanceMonitor.PerformanceStats leftStats = PerformanceMonitor.getPerformanceStats(left);
                PerformanceMonitor.PerformanceStats rightStats = PerformanceMonitor.getPerformanceStats(right);
                double leftCpu = leftStats == null || leftStats.getMeasurementCount() <= 0L
                        ? -1.0D : leftStats.getCpuUsagePercent();
                double rightCpu = rightStats == null || rightStats.getMeasurementCount() <= 0L
                        ? -1.0D : rightStats.getCpuUsagePercent();
                int cpuCompare = Double.compare(rightCpu, leftCpu);
                if (cpuCompare != 0) {
                    return cpuCompare;
                }
                int orderCompare = Integer.compare(getFeatureOrder(left), getFeatureOrder(right));
                return orderCompare != 0 ? orderCompare : left.compareTo(right);
            }
        });
        featureNames = ordered;
    }

    private String buildHotspotSummary() {
        String topFeature = "";
        double topCpu = 0.0D;
        for (String feature : featureNames) {
            PerformanceMonitor.PerformanceStats stats = PerformanceMonitor.getPerformanceStats(feature);
            if (stats == null || stats.getMeasurementCount() <= 0L) {
                continue;
            }
            double cpu = stats.getCpuUsagePercent();
            if (cpu > topCpu) {
                topCpu = cpu;
                topFeature = feature;
            }
        }
        return topFeature.isEmpty() ? "gui.modern.perf.u024" : tr("gui.modern.perf.u025", Double.valueOf(topCpu));
    }

    private int getHotspotColor() {
        double topCpu = 0.0D;
        boolean hasData = false;
        for (String feature : featureNames) {
            PerformanceMonitor.PerformanceStats stats = PerformanceMonitor.getPerformanceStats(feature);
            if (stats == null || stats.getMeasurementCount() <= 0L) {
                continue;
            }
            hasData = true;
            topCpu = Math.max(topCpu, stats.getCpuUsagePercent());
        }
        return hasData ? getCpuColor(topCpu) : ModernUiRenderer.MUTED_TEXT;
    }

    private void applyGuardSettings() {
        if (thresholdField == null || disableDurationField == null) {
            return;
        }
        try {
            double threshold = Double.parseDouble(thresholdField.getText().trim());
            long duration = Long.parseLong(disableDurationField.getText().trim());
            PerformanceMonitor.setSpikeThresholdMillis(threshold);
            PerformanceMonitor.setSpikeDisableDurationMs(duration);
            syncInputFields();
            clearFieldFocus();
            showStatus("gui.modern.perf.u026");
        } catch (NumberFormatException ignored) {
            showStatus("gui.modern.perf.u027", true);
        }
    }

    private void syncInputFields() {
        thresholdField.setText(formatThreshold(PerformanceMonitor.getSpikeThresholdMillis()));
        disableDurationField.setText(String.valueOf(PerformanceMonitor.getSpikeDisableDurationMs()));
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
        if (thresholdField != null) {
            thresholdField.setFocused(false);
        }
        if (disableDurationField != null) {
            disableDurationField.setFocused(false);
        }
    }

    private boolean hasFocusedField() {
        return thresholdField != null && thresholdField.isFocused()
                || disableDurationField != null && disableDurationField.isFocused();
    }

    private int getColumnCount(int cardAreaWidth) {
        return cardAreaWidth >= MIN_CARD_WIDTH * 2 + CARD_GAP ? 2 : 1;
    }

    private int getCardWidth(int cardAreaWidth, int columns) {
        return Math.max(1, (cardAreaWidth - CARD_GAP * Math.max(0, columns - 1)) / columns);
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect requestedContentBounds) {
        ModernMainLayout.Rect source = requestedContentBounds == null
                ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedContentBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 16));
        int insetY = Math.max(6, Math.min(9, source.height / 14));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private int getFeatureOrder(String featureName) {
        int index = FEATURE_ORDER.indexOf(featureName);
        return index >= 0 ? index : FEATURE_ORDER.size();
    }

    private int getCpuColor(double cpuUsagePercent) {
        if (cpuUsagePercent >= 20.0D) {
            return 0xFFE06A78;
        }
        if (cpuUsagePercent >= 5.0D) {
            return ModernUiRenderer.WARNING;
        }
        return ModernUiRenderer.SUCCESS;
    }

    private String getDisplayName(String featureName) {
        switch (featureName) {
            case "auto_equip":
                return "gui.modern.perf.u028";
            case "auto_pickup":
                return "gui.modern.perf.u029";
            case "auto_follow":
                return "gui.modern.perf.u030";
            case "auto_escape":
                return "gui.modern.perf.u031";
            case "kill_aura":
                return "gui.modern.perf.u032";
            case "path_sequence":
                return "gui.modern.perf.u033";
            case "conditional_execution":
                return "gui.modern.perf.u034";
            case "debuff_detector":
                return "gui.modern.perf.u035";
            case "goto_open":
                return "gui.modern.perf.u036";
            case "warehouse":
                return "gui.modern.perf.u037";
            case "block_replacement":
                return "gui.modern.perf.u038";
            case "render_features_tick":
                return "gui.modern.perf.u039";
            case "render_features_world":
                return "gui.modern.perf.u040";
            case "render_features_overlay":
                return "gui.modern.perf.u041";
            case "trigger_system":
                return "gui.modern.perf.u042";
            case "auto_eat":
                return "gui.modern.perf.u043";
            case "auto_use_item":
                return "gui.modern.perf.u044";
            case "timed_message":
                return "gui.modern.perf.u045";
            case "packet_capture_inbound":
                return "gui.modern.perf.u046";
            case "packet_capture_outbound":
                return "gui.modern.perf.u047";
            case "packet_intercept":
                return "gui.modern.perf.u048";
            case "packet_send_fml":
                return "gui.modern.perf.u049";
            case "packet_send_standard":
                return "gui.modern.perf.u050";
            default:
                return featureName;
        }
    }

    private String formatThreshold(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void showStatus(String message) {
        showStatus(message, false);
    }

    private void showStatus(String message, boolean error) {
        statusMessage = message == null ? "" : message;
        statusError = error;
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private static boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
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
