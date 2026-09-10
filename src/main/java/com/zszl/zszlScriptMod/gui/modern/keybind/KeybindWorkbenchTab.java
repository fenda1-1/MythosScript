package com.zszl.zszlScriptMod.gui.modern.keybind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;
import com.zszl.zszlScriptMod.system.BindableAction;
import com.zszl.zszlScriptMod.system.KeybindManager;
import com.zszl.zszlScriptMod.system.KeybindManager.Keybind;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.resources.I18n;

/** Embedded keybinding workbench for the modern main-window tab strip. */
public final class KeybindWorkbenchTab implements ModernSettingsTab {

    private static final int SEARCH_HEIGHT = 26;
    private static final int TOOLBAR_HEIGHT = 22;
    private static final int TABLE_HEADER_HEIGHT = 25;
    private static final int ROW_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 30;
    private static final int SCROLLBAR_WIDTH = ModernHoverScrollbar.GUTTER;
    private static final long DOUBLE_CLICK_WINDOW_MS = 300L;

    private static final class RowEntry {
        final boolean actionRow;
        final BindableAction action;
        final String sequenceName;

        RowEntry(BindableAction action) {
            this.actionRow = true;
            this.action = action;
            this.sequenceName = null;
        }

        RowEntry(String sequenceName) {
            this.actionRow = false;
            this.action = null;
            this.sequenceName = sequenceName;
        }

        boolean isPacketAction() {
            return this.actionRow && this.action == BindableAction.EXECUTE_SPECIFIC_PACKET_SEQUENCE;
        }

        String groupLabel() {
            if (!this.actionRow) {
                return I18n.format("gui.keybind.group.scripts");
            }
            if (isPacketAction()) {
                return I18n.format("gui.keybind.group.packet");
            }
            if (this.action.getFeatureGroup() != null) {
                return I18n.format(this.action.getFeatureGroup().getTranslationKey());
            }
            return I18n.format("gui.keybind.group.actions");
        }

        String name() {
            return this.actionRow ? this.action.getDisplayName() : this.sequenceName;
        }

        String description() {
            return this.actionRow ? this.action.getDescription() : I18n.format("gui.keybind.script_desc");
        }

        String sourceLabel() {
            return this.actionRow ? I18n.format("gui.keybind.source.system")
                    : I18n.format("gui.keybind.source.script");
        }
    }

    private final List<RowEntry> rows = new ArrayList<>();
    private final Map<BindableAction, Keybind> actionDraft = new EnumMap<>(BindableAction.class);
    private final Map<String, Keybind> sequenceDraft = new LinkedHashMap<>();

    private ModernTextField searchField;
    private ModernMainLayout.Rect contentBounds;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect clearSearchBounds;
    private ModernMainLayout.Rect tableBounds;
    private ModernMainLayout.Rect listBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private ModernMainLayout.Rect syncBounds;
    private ModernMainLayout.Rect boundFilterBounds;
    private ModernMainLayout.Rect conflictFilterBounds;
    private ModernMainLayout.Rect scriptsFilterBounds;
    private ModernMainLayout.Rect packetModalBounds;
    private ModernMainLayout.Rect packetListBounds;
    private ModernMainLayout.Rect packetCloseBounds;
    private ModernMainLayout.Rect recordingBounds;
    private ModernMainLayout.Rect recordingInputBounds;
    private ModernMainLayout.Rect recordingConfirmBounds;
    private ModernMainLayout.Rect recordingClearBounds;
    private ModernMainLayout.Rect recordingCancelBounds;
    private final List<ModernMainLayout.Rect> recordingRemoveBounds = new ArrayList<>();

    private int scrollOffset;
    private int maxScrollOffset;
    private int packetScrollOffset;
    private int packetMaxScrollOffset;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar packetScrollbar = new ModernHoverScrollbar();
    private boolean initialized;
    private boolean recording;
    private boolean draftDirty;
    private Keybind recordingDraft;
    private boolean packetSelectorOpen;
    private RowEntry recordingRow;
    private RowEntry packetRow;
    private String selectedRowKey;
    private String lastClickedRowKey;
    private long lastClickTime;
    private int lastClickButton = -1;
    private final List<String> packetSequences = new ArrayList<>();
    private boolean filterOnlyBound;
    private boolean filterOnlyConflicts;
    private boolean filterOnlyScripts;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private String pendingFocusTarget;

    private KeybindWorkbenchTab() {
    }

    public static ModernSettingsTab create() {
        return new KeybindWorkbenchTab();
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (this.initialized) {
            return;
        }
        this.searchField = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(100);
        this.searchField.setTextColor(ModernUiRenderer.TEXT);
        this.searchField.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        this.searchField.setFocused(true);
        loadDraft();
        this.initialized = true;
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedContentBounds, int mouseX,
            int mouseY) {
        ensureInitialized(fontRenderer);
        this.contentBounds = requestedContentBounds == null
                ? new ModernMainLayout.Rect(0, 0, 1, 1) : requestedContentBounds;
        this.hoveredTooltip = "";
        refreshPathDraft();
        layout(fontRenderer);
        rebuildRows();
        applyPendingFocus();

        ModernUiRenderer.drawPanel(this.panelBounds.x, this.panelBounds.y, this.panelBounds.width,
                this.panelBounds.height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawSearch(fontRenderer, mouseX, mouseY);
        drawToolbar(fontRenderer, mouseX, mouseY);
        drawTable(fontRenderer, mouseX, mouseY);
        drawFooter(fontRenderer, mouseX, mouseY);
        if (this.recording) {
            this.packetScrollbar.idle();
            drawRecordingDialog(fontRenderer, mouseX, mouseY);
        } else if (this.packetSelectorOpen) {
            drawPacketSelector(fontRenderer, mouseX, mouseY);
        } else {
            this.packetScrollbar.idle();
        }
    }

    private void layout(FontRenderer fontRenderer) {
        int insetX = Math.max(7, Math.min(12, this.contentBounds.width / 18));
        int insetY = Math.max(6, Math.min(9, this.contentBounds.height / 16));
        this.panelBounds = new ModernMainLayout.Rect(this.contentBounds.x + insetX, this.contentBounds.y + insetY,
                Math.max(1, this.contentBounds.width - insetX * 2),
                Math.max(1, this.contentBounds.height - insetY * 2));

        this.searchBounds = new ModernMainLayout.Rect(this.panelBounds.x + 12,
                this.panelBounds.y + 12, Math.max(1, this.panelBounds.width - 24), SEARCH_HEIGHT);
        this.searchField.x = this.searchBounds.x + 23;
        this.searchField.y = this.searchBounds.y + (SEARCH_HEIGHT - fontRenderer.FONT_HEIGHT) / 2;
        this.searchField.width = Math.max(1, this.searchBounds.width - 46);
        this.searchField.height = fontRenderer.FONT_HEIGHT + 2;
        ModernUiRenderer.reflowTextField(this.searchField);
        this.clearSearchBounds = this.searchField.getText().isEmpty()
                ? null
                : new ModernMainLayout.Rect(this.searchBounds.right() - 18, this.searchBounds.y + 5, 13, 16);

        this.recordingBounds = null;
        this.recordingInputBounds = null;
        this.recordingConfirmBounds = null;
        this.recordingClearBounds = null;
        this.recordingCancelBounds = null;
        this.recordingRemoveBounds.clear();

        int toolbarY = this.searchBounds.bottom() + 8;
        int filterWidth = Math.max(78, Math.min(112, (this.panelBounds.width - 48) / 3));
        int filterGap = 6;
        int totalFilterWidth = filterWidth * 3 + filterGap * 2;
        int filterX = Math.max(this.panelBounds.x + 16, this.panelBounds.right() - 16 - totalFilterWidth);
        this.boundFilterBounds = new ModernMainLayout.Rect(filterX, toolbarY, filterWidth, TOOLBAR_HEIGHT);
        this.conflictFilterBounds = new ModernMainLayout.Rect(filterX + filterWidth + filterGap, toolbarY, filterWidth,
                TOOLBAR_HEIGHT);
        this.scriptsFilterBounds = new ModernMainLayout.Rect(filterX + (filterWidth + filterGap) * 2, toolbarY,
                filterWidth, TOOLBAR_HEIGHT);

        int tableY = toolbarY + TOOLBAR_HEIGHT + 8;
        this.tableBounds = new ModernMainLayout.Rect(this.panelBounds.x + 1, tableY,
                Math.max(1, this.panelBounds.width - 2),
                Math.max(TABLE_HEADER_HEIGHT + ROW_HEIGHT,
                        this.panelBounds.bottom() - FOOTER_HEIGHT - tableY - 8));
        this.listBounds = new ModernMainLayout.Rect(this.tableBounds.x + 1,
                this.tableBounds.y + TABLE_HEADER_HEIGHT,
                Math.max(1, this.tableBounds.width - SCROLLBAR_WIDTH - 2),
                Math.max(ROW_HEIGHT, this.tableBounds.height - TABLE_HEADER_HEIGHT - 1));

        int footerY = this.panelBounds.bottom() - FOOTER_HEIGHT;
        int footerGap = 6;
        int buttonWidth = Math.max(56, Math.min(112, (this.panelBounds.width - 28 - footerGap * 2) / 3));
        this.saveBounds = new ModernMainLayout.Rect(this.panelBounds.x + 14, footerY, Math.max(buttonWidth, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(net.minecraft.client.Minecraft.getMinecraft().fontRenderer, "保存", 16)), 22);
        this.revertBounds = new ModernMainLayout.Rect(this.saveBounds.right() + footerGap, footerY, buttonWidth, 22);
        this.syncBounds = new ModernMainLayout.Rect(this.revertBounds.right() + footerGap, footerY,
                Math.max(1, this.panelBounds.right() - 14 - this.revertBounds.right() - footerGap), 22);
    }

    private void drawSearch(FontRenderer fontRenderer, int mouseX, int mouseY) {
        boolean focused = this.searchField != null && this.searchField.isFocused();
        ModernUiRenderer.drawSubtlePanel(this.searchBounds.x, this.searchBounds.y, this.searchBounds.width,
                this.searchBounds.height, 5, ModernUiRenderer.SURFACE,
                focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSearchIcon(this.searchBounds.x + 8, this.searchBounds.y + 7,
                focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
        ModernUiRenderer.drawTextField(this.searchField);
        if (this.searchField.getText().isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.keybind.search_placeholder"),
                    this.searchField.x + 2, this.searchField.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, this.searchField.width - 8));
        }
        if (this.clearSearchBounds != null) {
            boolean hovered = this.clearSearchBounds.contains(mouseX, mouseY);
            if (hovered) {
                ModernUiRenderer.drawRoundedRect(this.clearSearchBounds.x - 2, this.clearSearchBounds.y - 2,
                        this.clearSearchBounds.width + 4, this.clearSearchBounds.height + 4, 4,
                        ModernUiRenderer.SURFACE_HOVER);
                this.hoveredTooltip = I18n.format("gui.keybind.clear_search");
            }
            ModernUiRenderer.drawCloseIcon(this.clearSearchBounds.x + 2, this.clearSearchBounds.y + 3,
                    hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        }
    }

    private void drawToolbar(FontRenderer fontRenderer, int mouseX, int mouseY) {
        String summary = I18n.format("gui.keybind.summary", this.rows.size(), getTotalRowCount());
        int summaryWidth = Math.max(1, this.boundFilterBounds.x - this.panelBounds.x - 30);
        ModernUiRenderer.drawText(fontRenderer, summary, this.panelBounds.x + 16, this.boundFilterBounds.y + 7,
                ModernUiRenderer.SUBTLE_TEXT, summaryWidth);
        drawFilter(fontRenderer, this.boundFilterBounds, I18n.format("gui.keybind.filter.bound"), this.filterOnlyBound,
                mouseX, mouseY, "gui.modern.keybind_wb.u004");
        drawFilter(fontRenderer, this.conflictFilterBounds, I18n.format("gui.keybind.filter.conflict"),
                this.filterOnlyConflicts, mouseX, mouseY, "gui.modern.keybind_wb.u005");
        drawFilter(fontRenderer, this.scriptsFilterBounds, I18n.format("gui.keybind.filter.scripts"),
                this.filterOnlyScripts, mouseX, mouseY, "gui.modern.keybind_wb.u006");
    }

    private void drawFilter(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean selected,
            int mouseX, int mouseY, String tooltip) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, bounds.x + 8, bounds.y + 7,
                ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, selected ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SURFACE),
                Math.max(20, bounds.width - 42));
        ModernUiRenderer.drawToggle(bounds.right() - 35, bounds.y + 4, 27, 14, selected, hovered);
        if (hovered) {
            this.hoveredTooltip = tooltip;
        }
    }

    private void drawTable(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(this.tableBounds.x, this.tableBounds.y, this.tableBounds.width,
                this.tableBounds.height, 2, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int[] columns = getColumnWidths();
        int commandX = this.tableBounds.x;
        int bindingX = commandX + columns[0];
        int sourceX = bindingX + columns[1];

        Gui.drawRect(this.tableBounds.x + 1, this.tableBounds.y + 1, this.tableBounds.right() - 1,
                this.tableBounds.y + TABLE_HEADER_HEIGHT, ModernUiRenderer.SHELL_RAISED);
        Gui.drawRect(this.tableBounds.x + 1, this.tableBounds.y + TABLE_HEADER_HEIGHT - 1,
                this.tableBounds.right() - 1, this.tableBounds.y + TABLE_HEADER_HEIGHT, ModernUiRenderer.BORDER);
        drawHeaderCell(fontRenderer, I18n.format("gui.keybind.column.command"), commandX, columns[0]);
        drawHeaderCell(fontRenderer, I18n.format("gui.keybind.column.binding"), bindingX, columns[1]);
        drawHeaderCell(fontRenderer, I18n.format("gui.keybind.column.source"), sourceX, columns[2]);

        Map<String, Integer> conflictCounts = buildConflictCounts();
        int visibleRows = Math.max(1, this.listBounds.height / ROW_HEIGHT);
        ModernUiRenderer.beginClip(this.listBounds);
        if (this.rows.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, I18n.format("gui.keybind.empty"), this.listBounds.x + 12,
                    this.listBounds.y + 16, ModernUiRenderer.MUTED_TEXT, Math.max(20, this.listBounds.width - 24));
        } else {
            for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
                int rowIndex = this.scrollOffset + visibleIndex;
                if (rowIndex >= this.rows.size()) {
                    break;
                }
                RowEntry row = this.rows.get(rowIndex);
                int rowY = this.listBounds.y + visibleIndex * ROW_HEIGHT;
                drawRow(fontRenderer, row, getDraftKeybind(row), conflictCounts, rowIndex, rowY, commandX,
                        columns[0], bindingX, columns[1], sourceX, columns[2], mouseX, mouseY);
            }
        }
        ModernUiRenderer.endClip();

        if (this.maxScrollOffset > 0) {
            this.listScrollbar.drawAt(this.tableBounds.right(), this.listBounds.y, this.listBounds.height,
                    this.scrollOffset, this.maxScrollOffset, visibleRows, this.rows.size(), mouseX, mouseY,
                    value -> this.scrollOffset = value);
        } else {
            this.listScrollbar.idle();
        }
    }

    private void drawHeaderCell(FontRenderer fontRenderer, String label, int x, int width) {
        ModernUiRenderer.drawText(fontRenderer, label, x + 10, this.tableBounds.y + 8, ModernUiRenderer.TEXT,
                Math.max(1, width - 18));
    }

    private void drawRow(FontRenderer fontRenderer, RowEntry row, Keybind keybind, Map<String, Integer> conflictCounts,
            int rowIndex, int rowY, int commandX, int commandWidth, int bindingX, int bindingWidth, int sourceX,
            int sourceWidth, int mouseX, int mouseY) {
        int rowRight = this.tableBounds.right() - SCROLLBAR_WIDTH - 1;
        int rowBottom = rowY + ROW_HEIGHT - 1;
        boolean hovered = this.listBounds.contains(mouseX, mouseY) && mouseY >= rowY && mouseY < rowBottom + 1;
        boolean selected = rowKey(row).equals(this.selectedRowKey);
        boolean conflict = isConflictingKeybind(keybind, conflictCounts);
        int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : rowIndex % 2 == 0 ? ModernUiRenderer.SURFACE : ModernUiRenderer.SHELL;
        Gui.drawRect(this.tableBounds.x + 1, rowY, rowRight, rowBottom, fill);
        if (hovered) {
            Gui.drawRect(this.tableBounds.x + 1, rowY, rowRight, rowY + 1, ModernUiRenderer.ACCENT);
            this.hoveredTooltip = row.isPacketAction()
                    ? getBindingText(row, keybind, true) + ModernFormI18n.tr("gui.modern.keybind_wb.u007")
                    : row.description() + ModernFormI18n.tr("gui.modern.keybind_wb.u008");
        } else {
            Gui.drawRect(this.tableBounds.x + 1, rowBottom - 1, rowRight, rowBottom, ModernUiRenderer.BORDER_SUBTLE);
        }
        drawCellDivider(commandX + commandWidth, rowY);
        drawCellDivider(bindingX + bindingWidth, rowY);

        drawCellText(fontRenderer, row.name(), commandX + 10, rowY + 5, commandWidth - 20,
                ModernUiRenderer.readableText(ModernUiRenderer.TEXT, fill));
        String commandMeta = row.groupLabel();
        if (row.isPacketAction()) {
            String parameter = keybind != null && keybind.getParameter() != null
                    && !keybind.getParameter().trim().isEmpty() ? keybind.getParameter()
                            : I18n.format("gui.keybind.unselected");
            commandMeta += " - " + I18n.format("gui.keybind.packet_target_short", parameter);
        } else if (!row.actionRow) {
            commandMeta += " - " + I18n.format("gui.keybind.script_detail");
        } else {
            commandMeta += " - " + row.description();
        }
        drawCellText(fontRenderer, commandMeta, commandX + 10, rowY + 18, commandWidth - 20,
                ModernUiRenderer.readableText(ModernUiRenderer.SUBTLE_TEXT, fill));

        drawBinding(fontRenderer, row, keybind, conflict, bindingX, bindingWidth, rowY, mouseX, mouseY, fill);
        drawCellText(fontRenderer, row.sourceLabel(), sourceX + 10, rowY + 8, sourceWidth - 20,
                ModernUiRenderer.readableText(row.actionRow ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.SUCCESS, fill));
    }

    private void drawCellDivider(int x, int rowY) {
        Gui.drawRect(x, rowY + 3, x + 1, rowY + ROW_HEIGHT - 3, ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawCellText(FontRenderer fontRenderer, String text, int x, int y, int width, int color) {
        ModernUiRenderer.drawText(fontRenderer, text, x, y, color, Math.max(1, width));
    }

    private void drawBinding(FontRenderer fontRenderer, RowEntry row, Keybind keybind, boolean conflict, int x, int width,
            int rowY, int mouseX, int mouseY, int rowFill) {
        int metaColor = ModernUiRenderer.readableText(
                conflict ? ModernUiRenderer.DANGER : ModernUiRenderer.MUTED_TEXT, rowFill);
        if (!hasBinding(keybind)) {
            drawCellText(fontRenderer, I18n.format("gui.keybind.unbound"), x + 10, rowY + 9, width - 20,
                    metaColor);
        } else {
            int keyColor = ModernUiRenderer.readableText(
                    conflict ? ModernUiRenderer.DANGER : ModernUiRenderer.TEXT, rowFill);
            if (keybind.getCombinations().size() > 1) {
                drawCellText(fontRenderer, formatKeybind(keybind), x + 10, rowY + 9, width - 20, keyColor);
                drawCellText(fontRenderer, I18n.format("gui.keybind.binding_multiple", keybind.getCombinations().size()),
                        x + 10, rowY + 20, width - 20, metaColor);
                if (conflict) {
                    ModernUiRenderer.drawStatusDot(x + width - 14, rowY + 8, ModernUiRenderer.DANGER);
                }
                return;
            }
            int keyX = x + 10;
            int keyRight = x + width - 8;
            for (String label : getBindingLabels(keybind)) {
                int keyWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label) + 12;
                if (keyX + keyWidth > keyRight) {
                    drawCellText(fontRenderer, formatKeybind(keybind), x + 10, rowY + 9, width - 20, keyColor);
                    break;
                }
                boolean hovered = isHoverRegion(mouseX, mouseY, keyX, rowY + 3, keyWidth, 17);
                int keyFill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ICON_SURFACE;
                ModernUiRenderer.drawSubtlePanel(keyX, rowY + 3, keyWidth, 17, 3,
                        keyFill,
                        conflict ? ModernUiRenderer.DANGER : hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                drawCenteredText(fontRenderer, label, keyX + keyWidth / 2, rowY + 8,
                        ModernUiRenderer.readableText(conflict ? ModernUiRenderer.DANGER : ModernUiRenderer.ICON_TEXT, keyFill));
                keyX += keyWidth + 4;
            }
        }
        String meta = row.isPacketAction() ? getPacketTarget(keybind) : I18n.format("gui.keybind.binding_hint");
        drawCellText(fontRenderer, meta, x + 10, rowY + 20, width - 20,
                metaColor);
        if (conflict) {
            ModernUiRenderer.drawStatusDot(x + width - 14, rowY + 8, ModernUiRenderer.DANGER);
        }
    }

    private void drawCenteredText(FontRenderer fontRenderer, String text, int centerX, int y, int color) {
        String safe = text == null ? "" : text;
        fontRenderer.drawString(safe, centerX - fontRenderer.getStringWidth(safe) / 2, y,
                ModernUiRenderer.readableText(color, ModernUiRenderer.SURFACE));
    }

    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawDivider(this.panelBounds.x + 12, this.saveBounds.y - 7,
                Math.max(1, this.panelBounds.width - 24), ModernUiRenderer.BORDER_SUBTLE);
        drawFooterButton(fontRenderer, this.saveBounds, "gui.modern.keybind_wb.u009", true, mouseX, mouseY,
                "gui.modern.keybind_wb.u010");
        drawFooterButton(fontRenderer, this.revertBounds, "gui.modern.keybind_wb.u011", false, mouseX, mouseY,
                "gui.modern.keybind_wb.u012");
        drawFooterButton(fontRenderer, this.syncBounds, "gui.modern.keybind_wb.u013", false, mouseX, mouseY,
                "gui.modern.keybind_wb.u014");
        if (statusVisible()) {
            int statusX = this.syncBounds.right() + 10;
            ModernUiRenderer.drawText(fontRenderer, this.statusMessage, statusX, this.saveBounds.y + 7,
                    ModernUiRenderer.SUCCESS, Math.max(1, this.panelBounds.right() - statusX - 12));
        }
    }

    private void drawFooterButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label, boolean primary,
            int mouseX, int mouseY, String tooltip) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = primary ? (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT)
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = primary ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        int text = ModernUiRenderer.readableText(primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, fill);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 7,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2, text, Math.max(1, bounds.width - 14));
        if (hovered) {
            this.hoveredTooltip = tooltip;
        }
    }

    private void drawRecordingDialog(FontRenderer fontRenderer, int mouseX, int mouseY) {
        Gui.drawRect(this.panelBounds.x, this.panelBounds.y, this.panelBounds.right(), this.panelBounds.bottom(),
                0xB0081016);
        int modalWidth = Math.min(380, Math.max(260, this.panelBounds.width - 28));
        int modalHeight = Math.min(250, Math.max(214, this.panelBounds.height - 28));
        int modalX = this.panelBounds.x + (this.panelBounds.width - modalWidth) / 2;
        int modalY = this.panelBounds.y + (this.panelBounds.height - modalHeight) / 2;
        this.recordingBounds = new ModernMainLayout.Rect(modalX, modalY, modalWidth, modalHeight);
        ModernUiRenderer.drawPanel(modalX, modalY, modalWidth, modalHeight, 6, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u015", modalX + 14, modalY + 10, ModernUiRenderer.TEXT,
                modalWidth - 28);
        String rowName = this.recordingRow == null ? "" : this.recordingRow.name();
        ModernUiRenderer.drawText(fontRenderer, rowName, modalX + 14, modalY + 24,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, modalWidth - 28));

        this.recordingInputBounds = new ModernMainLayout.Rect(modalX + 14, modalY + 47, modalWidth - 28, 104);
        ModernUiRenderer.drawSubtlePanel(this.recordingInputBounds.x, this.recordingInputBounds.y,
                this.recordingInputBounds.width, this.recordingInputBounds.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.ACCENT);
        this.recordingRemoveBounds.clear();
        List<KeybindManager.KeyCombination> combinations = this.recordingDraft == null
                ? java.util.Collections.<KeybindManager.KeyCombination>emptyList()
                : this.recordingDraft.getCombinations();
        if (combinations.isEmpty()) {
            ModernUiRenderer.drawStatusDot(this.recordingInputBounds.x + 12, this.recordingInputBounds.y + 17,
                    ModernUiRenderer.ACCENT);
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u016", this.recordingInputBounds.x + 27,
                    this.recordingInputBounds.y + 14, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, this.recordingInputBounds.width - 39));
        } else {
            int visible = Math.min(4, combinations.size());
            for (int index = 0; index < visible; index++) {
                int rowY = this.recordingInputBounds.y + 8 + index * 18;
                ModernUiRenderer.drawText(fontRenderer, (index + 1) + ". "
                        + formatCombination(combinations.get(index)), this.recordingInputBounds.x + 12, rowY,
                        ModernUiRenderer.TEXT, Math.max(1, this.recordingInputBounds.width - 44));
                ModernMainLayout.Rect remove = new ModernMainLayout.Rect(
                        this.recordingInputBounds.right() - 25, rowY - 2, 17, 17);
                this.recordingRemoveBounds.add(remove);
                ModernUiRenderer.drawCloseIcon(remove.x + 4, remove.y + 4, ModernUiRenderer.MUTED_TEXT);
            }
            if (combinations.size() > visible) {
                ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr("gui.modern.keybind_wb.fmt.count",
                        "+" + (combinations.size() - visible)),
                        this.recordingInputBounds.x + 12, this.recordingInputBounds.bottom() - 28,
                        ModernUiRenderer.MUTED_TEXT, this.recordingInputBounds.width - 24);
            }
        }
        ModernUiRenderer.drawText(fontRenderer, getRecordingPreview(), this.recordingInputBounds.x + 12,
                this.recordingInputBounds.bottom() - 14, ModernUiRenderer.ACCENT,
                Math.max(1, this.recordingInputBounds.width - 24));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u017",
                modalX + 14, modalY + 158, ModernUiRenderer.MUTED_TEXT, modalWidth - 28);

        int gap = 6;
        int buttonWidth = Math.max(1, (modalWidth - 28 - gap * 2) / 3);
        int buttonY = modalY + modalHeight - 30;
        this.recordingConfirmBounds = new ModernMainLayout.Rect(modalX + 14, buttonY, buttonWidth, 21);
        this.recordingClearBounds = new ModernMainLayout.Rect(this.recordingConfirmBounds.right() + gap, buttonY,
                buttonWidth, 21);
        this.recordingCancelBounds = new ModernMainLayout.Rect(this.recordingClearBounds.right() + gap, buttonY,
                Math.max(1, modalX + modalWidth - 14 - this.recordingClearBounds.right() - gap), 21);
        drawFooterButton(fontRenderer, this.recordingConfirmBounds, "gui.modern.keybind_wb.u018", true, mouseX, mouseY,
                "gui.modern.keybind_wb.u019");
        drawFooterButton(fontRenderer, this.recordingClearBounds, "gui.modern.keybind_wb.u020", false, mouseX, mouseY,
                "gui.modern.keybind_wb.u021");
        drawFooterButton(fontRenderer, this.recordingCancelBounds, "gui.modern.keybind_wb.u022", false, mouseX, mouseY,
                "gui.modern.keybind_wb.u023");
    }

    private String getRecordingPreview() {
        List<String> modifiers = new ArrayList<>();
        Set<Integer> active = getActiveModifiers();
        if (active.contains(Keyboard.KEY_LCONTROL)) {
            modifiers.add("Ctrl");
        }
        if (active.contains(Keyboard.KEY_LSHIFT)) {
            modifiers.add("Shift");
        }
        if (active.contains(Keyboard.KEY_LMENU)) {
            modifiers.add("Alt");
        }
        if (modifiers.isEmpty()) {
            return "gui.modern.keybind_wb.u024";
        }
        return "[" + String.join(" + ", modifiers) + " + ...]";
    }

    private String formatLatestCombination() {
        if (this.recordingDraft == null || this.recordingDraft.getCombinations().isEmpty()) {
            return "gui.modern.keybind_wb.u025";
        }
        List<KeybindManager.KeyCombination> values = this.recordingDraft.getCombinations();
        return formatCombination(values.get(values.size() - 1));
    }

    private String formatCombination(KeybindManager.KeyCombination combination) {
        if (combination == null || combination.getKeyCode() == Keyboard.KEY_NONE) {
            return I18n.format("gui.keybind.unbound");
        }
        List<String> labels = new ArrayList<>();
        Set<Integer> modifiers = combination.getModifiers();
        if (hasModifier(modifiers, Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL)) labels.add("Ctrl");
        if (hasModifier(modifiers, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)) labels.add("Shift");
        if (hasModifier(modifiers, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)) labels.add("Alt");
        String keyName = Keyboard.getKeyName(combination.getKeyCode());
        labels.add(keyName == null || keyName.trim().isEmpty() ? "?" : keyName);
        return "[" + String.join(" + ", labels) + "]";
    }

    private void drawPacketSelector(FontRenderer fontRenderer, int mouseX, int mouseY) {
        Gui.drawRect(this.panelBounds.x, this.panelBounds.y, this.panelBounds.right(), this.panelBounds.bottom(),
                0xB0081016);
        int modalWidth = Math.min(360, Math.max(240, this.panelBounds.width - 28));
        int modalHeight = Math.min(300, Math.max(190, this.panelBounds.height - 28));
        int modalX = this.panelBounds.x + (this.panelBounds.width - modalWidth) / 2;
        int modalY = this.panelBounds.y + (this.panelBounds.height - modalHeight) / 2;
        this.packetModalBounds = new ModernMainLayout.Rect(modalX, modalY, modalWidth, modalHeight);
        ModernUiRenderer.drawPanel(modalX, modalY, modalWidth, modalHeight, 6, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u026", modalX + 14, modalY + 10, ModernUiRenderer.TEXT,
                modalWidth - 28);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u027", modalX + 14, modalY + 24,
                ModernUiRenderer.MUTED_TEXT, modalWidth - 28);

        this.packetCloseBounds = new ModernMainLayout.Rect(modalX + 12, modalY + modalHeight - 30,
                Math.max(80, modalWidth - 24), 21);
        drawFooterButton(fontRenderer, this.packetCloseBounds, "gui.modern.keybind_wb.u028", false, mouseX, mouseY, "gui.modern.keybind_wb.u029");

        int listY = modalY + 45;
        int listHeight = Math.max(26, modalHeight - 82);
        this.packetListBounds = new ModernMainLayout.Rect(modalX + 10, listY, Math.max(1, modalWidth - 20), listHeight);
        int visibleRows = Math.max(1, listHeight / 22);
        this.packetMaxScrollOffset = Math.max(0, this.packetSequences.size() - visibleRows);
        this.packetScrollOffset = clamp(this.packetScrollOffset, 0, this.packetMaxScrollOffset);
        ModernUiRenderer.beginClip(this.packetListBounds);
        if (this.packetSequences.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.keybind_wb.u030", this.packetListBounds.x + 8,
                    this.packetListBounds.y + 10, ModernUiRenderer.MUTED_TEXT, this.packetListBounds.width - 16);
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int index = this.packetScrollOffset + i;
                if (index >= this.packetSequences.size()) {
                    break;
                }
                int rowY = this.packetListBounds.y + i * 22;
                boolean hovered = isHoverRegion(mouseX, mouseY, this.packetListBounds.x, rowY,
                        ModernHoverScrollbar.contentWidth(this.packetListBounds.width - 2), 20);
                ModernUiRenderer.drawSubtlePanel(this.packetListBounds.x, rowY,
                        Math.max(1, ModernHoverScrollbar.contentWidth(this.packetListBounds.width - 2)), 20, 3,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, this.packetSequences.get(index), this.packetListBounds.x + 8,
                        rowY + 6, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(1, this.packetListBounds.width - 20));
            }
        }
        ModernUiRenderer.endClip();
        if (this.packetMaxScrollOffset > 0) {
            this.packetScrollbar.draw(this.packetListBounds, this.packetScrollOffset, this.packetMaxScrollOffset,
                    visibleRows, this.packetSequences.size(), mouseX, mouseY, value -> this.packetScrollOffset = value);
        } else {
            this.packetScrollbar.idle();
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.recording) {
            for (int index = 0; index < this.recordingRemoveBounds.size(); index++) {
                ModernMainLayout.Rect remove = this.recordingRemoveBounds.get(index);
                if (mouseButton == 0 && remove != null && remove.contains(mouseX, mouseY)) {
                    if (this.recordingDraft != null) {
                        this.recordingDraft.removeCombination(index);
                    }
                    return true;
                }
            }
            if (mouseButton == 0 && this.recordingConfirmBounds != null
                    && this.recordingConfirmBounds.contains(mouseX, mouseY)) {
                commitRecording();
                return true;
            }
            if (mouseButton == 0 && this.recordingClearBounds != null
                    && this.recordingClearBounds.contains(mouseX, mouseY)) {
                clearRecording();
            } else if (mouseButton == 0 && this.recordingCancelBounds != null
                    && this.recordingCancelBounds.contains(mouseX, mouseY)) {
                cancelRecording();
            }
            return true;
        }
        if (this.panelBounds == null || !this.panelBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            return false;
        }
        if (this.packetSelectorOpen) {
            return mouseClickedPacketSelector(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0 && this.listScrollbar.beginDrag(mouseX, mouseY)) {
            resetClickSequence();
            return true;
        }
        if (mouseButton == 0 && this.clearSearchBounds != null && this.clearSearchBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            clearSearch();
            return true;
        }
        if (this.searchBounds != null && this.searchBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            if (this.searchField != null) {
                this.searchField.setFocused(true);
                this.searchField.mouseClicked(mouseX, mouseY, 0);
            }
            return true;
        }
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
        if (mouseButton == 0 && this.boundFilterBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            this.filterOnlyBound = !this.filterOnlyBound;
            rebuildRows();
            return true;
        }
        if (mouseButton == 0 && this.conflictFilterBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            this.filterOnlyConflicts = !this.filterOnlyConflicts;
            rebuildRows();
            return true;
        }
        if (mouseButton == 0 && this.scriptsFilterBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            this.filterOnlyScripts = !this.filterOnlyScripts;
            rebuildRows();
            return true;
        }
        if (mouseButton == 0 && this.saveBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            saveDraft();
            return true;
        }
        if (mouseButton == 0 && this.revertBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            revertDraft();
            return true;
        }
        if (mouseButton == 0 && this.syncBounds.contains(mouseX, mouseY)) {
            resetClickSequence();
            syncPaths();
            return true;
        }
        if (this.listBounds != null && this.listBounds.contains(mouseX, mouseY)) {
            int index = this.scrollOffset + (mouseY - this.listBounds.y) / ROW_HEIGHT;
            if (index >= 0 && index < this.rows.size()) {
                RowEntry row = this.rows.get(index);
                this.selectedRowKey = rowKey(row);
                if (mouseButton == 1) {
                    resetClickSequence();
                    if (row.isPacketAction()) {
                        openPacketSelector(row);
                    }
                } else if (mouseButton == 0) {
                    long now = System.currentTimeMillis();
                    boolean doubleClick = rowKey(row).equals(this.lastClickedRowKey)
                            && this.lastClickButton == 0 && now - this.lastClickTime <= DOUBLE_CLICK_WINDOW_MS;
                    this.lastClickedRowKey = doubleClick ? null : rowKey(row);
                    this.lastClickButton = doubleClick ? -1 : 0;
                    this.lastClickTime = now;
                    if (doubleClick) {
                        openKeyRecorder(row);
                    }
                }
                return true;
            }
        }
        if (this.searchField != null) {
            this.searchField.setFocused(false);
        }
        resetClickSequence();
        return true;
    }

    private boolean mouseClickedPacketSelector(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (this.packetCloseBounds != null && this.packetCloseBounds.contains(mouseX, mouseY)) {
            closePacketSelector();
            return true;
        }
        if (this.packetListBounds == null || !this.packetListBounds.contains(mouseX, mouseY)) {
            return true;
        }
        if (this.packetScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        int index = this.packetScrollOffset + (mouseY - this.packetListBounds.y) / 22;
        if (index >= 0 && index < this.packetSequences.size() && this.packetRow != null) {
            Keybind keybind = copyKeybind(getDraftKeybind(this.packetRow));
            keybind.setParameter(this.packetSequences.get(index));
            setDraftKeybind(this.packetRow, keybind);
            closePacketSelector();
            showStatus("gui.modern.keybind_wb.u031");
        }
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.packetScrollbar.isDragging() && clickedMouseButton == 0) {
            this.packetScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        if (this.listScrollbar.isDragging() && clickedMouseButton == 0) {
            this.listScrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && this.packetScrollbar.isDragging()) {
            this.packetScrollbar.endDrag();
            return true;
        }
        if (state == 0 && this.listScrollbar.isDragging()) {
            this.listScrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.recording) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                cancelRecording();
                return true;
            }
            if (keyCode == Keyboard.KEY_DELETE) {
                clearRecording();
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitRecording();
                return true;
            }
            if (keyCode == Keyboard.KEY_NONE || isModifierKey(keyCode)) {
                return true;
            }
            RowEntry target = this.recordingRow;
            if (target != null && this.recordingDraft != null) {
                this.recordingDraft.addCombination(keyCode, getActiveModifiers());
                showStatus(ModernFormI18n.tr("gui.modern.keybind_wb.fmt.added_keep", formatLatestCombination()));
            }
            return true;
        }
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        if (ctrl && keyCode == Keyboard.KEY_S) {
            saveDraft();
            return true;
        }
        if (ctrl && keyCode == Keyboard.KEY_F) {
            if (this.searchField != null) {
                this.searchField.setFocused(true);
            }
            return true;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                this.scrollOffset = 0;
                rebuildRows();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, this.lastMouseX, this.lastMouseY);
    }

    private int lastMouseX;
    private int lastMouseY;

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        if (this.packetSelectorOpen && this.packetListBounds != null && this.packetListBounds.contains(mouseX, mouseY)) {
            if (wheel == 0 || this.packetMaxScrollOffset <= 0) {
                return false;
            }
            int beforePacket = this.packetScrollOffset;
            this.packetScrollOffset = clamp(this.packetScrollOffset + (wheel > 0 ? -1 : 1), 0,
                    this.packetMaxScrollOffset);
            return beforePacket != this.packetScrollOffset;
        }
        if (wheel == 0 || this.listBounds == null || !this.listBounds.contains(mouseX, mouseY)
                || this.maxScrollOffset <= 0) {
            return false;
        }
        int before = this.scrollOffset;
        this.scrollOffset = clamp(this.scrollOffset + (wheel > 0 ? -2 : 2), 0,
                this.maxScrollOffset);
        return before != this.scrollOffset;
    }

    @Override
    public boolean handleEscape() {
        if (this.packetSelectorOpen) {
            closePacketSelector();
            return true;
        }
        if (this.recording) {
            cancelRecording();
            return true;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            this.searchField.setFocused(false);
            return true;
        }
        if (this.searchField != null && !this.searchField.getText().isEmpty()) {
            clearSearch();
            return true;
        }
        if (this.filterOnlyBound || this.filterOnlyConflicts || this.filterOnlyScripts) {
            this.filterOnlyBound = false;
            this.filterOnlyConflicts = false;
            this.filterOnlyScripts = false;
            rebuildRows();
            return true;
        }
        return false;
    }

    @Override
    public void focusCommand(String command) {
        this.pendingFocusTarget = command == null ? "" : command.trim();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return this.panelBounds != null && this.panelBounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return ModernFormI18n.tr(this.hoveredTooltip);
    }

    @Override
    public void discardDraft() {
        if (!this.initialized) {
            KeybindManager.setRecording(false);
            return;
        }
        if (this.searchField != null) {
            this.searchField.setText("");
            this.searchField.setFocused(false);
        }
        this.filterOnlyBound = false;
        this.filterOnlyConflicts = false;
        this.filterOnlyScripts = false;
        this.recording = false;
        this.recordingRow = null;
        this.recordingDraft = null;
        KeybindManager.setRecording(false);
        this.packetSelectorOpen = false;
        this.packetRow = null;
        this.lastClickedRowKey = null;
        this.lastClickButton = -1;
        this.pendingFocusTarget = null;
        this.scrollOffset = 0;
        this.draftDirty = false;
        this.statusMessage = "";
        loadDraft();
    }

    @Override
    public boolean isDirty() {
        return draftDirty || recording || packetSelectorOpen;
    }

    private void loadDraft() {
        KeybindManager.syncPathSequenceKeybinds();
        this.actionDraft.clear();
        for (Map.Entry<BindableAction, Keybind> entry : KeybindManager.keybinds.entrySet()) {
            if (entry.getValue() != null) {
                this.actionDraft.put(entry.getKey(), copyKeybind(entry.getValue()));
            }
        }
        this.sequenceDraft.clear();
        for (Map.Entry<String, Keybind> entry : KeybindManager.pathSequenceKeybinds.entrySet()) {
            this.sequenceDraft.put(entry.getKey(), copyKeybind(entry.getValue()));
        }
        rebuildRows();
        this.draftDirty = false;
    }

    private void refreshPathDraft() {
        KeybindManager.syncPathSequenceKeybinds();
        Map<String, Keybind> visible = new LinkedHashMap<>();
        for (Map.Entry<String, Keybind> entry : KeybindManager.pathSequenceKeybinds.entrySet()) {
            Keybind value = this.sequenceDraft.get(entry.getKey());
            visible.put(entry.getKey(), value == null ? copyKeybind(entry.getValue()) : value);
        }
        this.sequenceDraft.clear();
        this.sequenceDraft.putAll(visible);
    }

    private void rebuildRows() {
        String query = PinyinSearchHelper.normalizeQuery(this.searchField == null ? "" : this.searchField.getText());
        Map<String, Integer> conflictCounts = buildConflictCounts();
        this.rows.clear();
        for (BindableAction action : BindableAction.values()) {
            RowEntry row = new RowEntry(action);
            if (matches(row, query, conflictCounts)) {
                this.rows.add(row);
            }
        }
        List<String> sequences = new ArrayList<>(this.sequenceDraft.keySet());
        sequences.sort(Comparator.naturalOrder());
        for (String sequence : sequences) {
            RowEntry row = new RowEntry(sequence);
            if (matches(row, query, conflictCounts)) {
                this.rows.add(row);
            }
        }
        int visibleRows = this.listBounds == null ? 1 : Math.max(1, this.listBounds.height / ROW_HEIGHT);
        this.maxScrollOffset = Math.max(0, this.rows.size() - visibleRows);
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScrollOffset);
    }

    private void applyPendingFocus() {
        if (this.pendingFocusTarget == null || this.pendingFocusTarget.isEmpty()) {
            return;
        }
        String target = this.pendingFocusTarget;
        this.pendingFocusTarget = null;
        if (this.searchField != null) {
            this.searchField.setText("");
            this.searchField.setFocused(false);
        }
        this.filterOnlyBound = false;
        this.filterOnlyConflicts = false;
        this.filterOnlyScripts = false;
        if ("keybind_conflicts".equals(target)) {
            this.filterOnlyConflicts = true;
            rebuildRows();
            this.scrollOffset = 0;
            showStatus("gui.modern.keybind_wb.u033");
            return;
        }
        if ("keybind_scripts".equals(target)) {
            this.filterOnlyScripts = true;
            rebuildRows();
            this.scrollOffset = 0;
            showStatus("gui.modern.keybind_wb.u034");
            return;
        }
        if ("keybind_recorder".equals(target)) {
            rebuildRows();
            this.scrollOffset = 0;
            showStatus("gui.modern.keybind_wb.u035");
            return;
        }
        if ("keybind_manager".equals(target)) {
            rebuildRows();
            this.scrollOffset = 0;
            return;
        }
        rebuildRows();
        for (int index = 0; index < this.rows.size(); index++) {
            if (!matchesFocusTarget(this.rows.get(index), target)) {
                continue;
            }
            this.selectedRowKey = rowKey(this.rows.get(index));
            int visibleRows = this.listBounds == null ? 1 : Math.max(1, this.listBounds.height / ROW_HEIGHT);
            this.scrollOffset = clamp(index - visibleRows / 2, 0, this.maxScrollOffset);
            showStatus(ModernFormI18n.tr("gui.modern.keybind_wb.fmt.located", this.rows.get(index).name()));
            return;
        }
        showStatus("gui.modern.keybind_wb.u036");
    }

    private boolean matchesFocusTarget(RowEntry row, String target) {
        if (row == null || target == null) {
            return false;
        }
        if (target.startsWith("action:")) {
            if (!row.actionRow) {
                return false;
            }
            return row.action.name().equalsIgnoreCase(target.substring("action:".length()));
        }
        if (target.startsWith("sequence:")) {
            return !row.actionRow && row.sequenceName.equals(target.substring("sequence:".length()));
        }
        return row.actionRow && row.action.name().equalsIgnoreCase(target);
    }

    private boolean matches(RowEntry row, String query, Map<String, Integer> conflictCounts) {
        Keybind keybind = getDraftKeybind(row);
        if (this.filterOnlyScripts && row.actionRow) {
            return false;
        }
        if (this.filterOnlyBound && !hasBinding(keybind)) {
            return false;
        }
        if (this.filterOnlyConflicts && !isConflictingKeybind(keybind, conflictCounts)) {
            return false;
        }
        return query.isEmpty() || PinyinSearchHelper.matchesNormalized(buildSearchText(row, keybind), query);
    }

    private String buildSearchText(RowEntry row, Keybind keybind) {
        StringBuilder text = new StringBuilder();
        text.append(row.name()).append(' ').append(row.groupLabel()).append(' ').append(row.description()).append(' ')
                .append(row.sourceLabel()).append(' ')
                .append(getBindingText(row, keybind, false));
        if (row.isPacketAction() && keybind != null && keybind.getParameter() != null) {
            text.append(' ').append(keybind.getParameter());
        }
        return text.toString();
    }

    private Map<String, Integer> buildConflictCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (BindableAction action : BindableAction.values()) {
            incrementConflict(counts, this.actionDraft.get(action));
        }
        for (Keybind keybind : this.sequenceDraft.values()) {
            incrementConflict(counts, keybind);
        }
        return counts;
    }

    private void incrementConflict(Map<String, Integer> counts, Keybind keybind) {
        if (keybind == null) return;
        for (KeybindManager.KeyCombination combination : keybind.getCombinations()) {
            String signature = getBindingSignature(combination);
            if (!signature.isEmpty()) {
                counts.put(signature, counts.getOrDefault(signature, 0) + 1);
            }
        }
    }

    private String getBindingSignature(Keybind keybind) {
        if (keybind == null || keybind.getCombinations().isEmpty()) {
            return "";
        }
        return getBindingSignature(keybind.getCombinations().get(0));
    }

    private String getBindingSignature(KeybindManager.KeyCombination combination) {
        if (combination == null || combination.getKeyCode() == Keyboard.KEY_NONE) return "";
        StringBuilder modifiers = new StringBuilder();
        if (hasModifier(combination.getModifiers(), Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL)) {
            modifiers.append("CTRL,");
        }
        if (hasModifier(combination.getModifiers(), Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)) {
            modifiers.append("SHIFT,");
        }
        if (hasModifier(combination.getModifiers(), Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)) {
            modifiers.append("ALT,");
        }
        return combination.getKeyCode() + "|" + modifiers;
    }

    private boolean hasModifier(Keybind keybind, int left, int right) {
        return keybind != null && keybind.getModifiers() != null
                && (keybind.getModifiers().contains(left) || keybind.getModifiers().contains(right));
    }

    private boolean hasModifier(Set<Integer> modifiers, int left, int right) {
        return modifiers != null && (modifiers.contains(left) || modifiers.contains(right));
    }

    private boolean isConflictingKeybind(Keybind keybind, Map<String, Integer> conflictCounts) {
        if (keybind == null) return false;
        for (KeybindManager.KeyCombination combination : keybind.getCombinations()) {
            String signature = getBindingSignature(combination);
            if (!signature.isEmpty() && conflictCounts.getOrDefault(signature, 0) > 1) return true;
        }
        return false;
    }

    private Keybind getDraftKeybind(RowEntry row) {
        return row.actionRow ? this.actionDraft.get(row.action) : this.sequenceDraft.get(row.sequenceName);
    }

    private String rowKey(RowEntry row) {
        return row.actionRow ? "action:" + row.action.name() : "sequence:" + row.sequenceName;
    }

    private void resetClickSequence() {
        this.lastClickedRowKey = null;
        this.lastClickButton = -1;
    }

    private void setDraftKeybind(RowEntry row, Keybind keybind) {
        if (row.actionRow) {
            boolean hasPacketTarget = row.isPacketAction() && keybind != null && keybind.getParameter() != null
                    && !keybind.getParameter().trim().isEmpty();
            if (hasBinding(keybind) || hasPacketTarget || row.action == BindableAction.OPEN_COMMAND_PALETTE) {
                this.actionDraft.put(row.action, copyKeybind(keybind));
            } else {
                this.actionDraft.remove(row.action);
            }
        } else {
            this.sequenceDraft.put(row.sequenceName, copyKeybind(keybind));
        }
        this.draftDirty = true;
        rebuildRows();
    }

    private void openKeyRecorder(final RowEntry row) {
        if (row == null) {
            return;
        }
        this.recording = true;
        this.recordingRow = row;
        this.recordingDraft = copyKeybind(getDraftKeybind(row));
        KeybindManager.setRecording(true);
        this.searchField.setFocused(false);
        showStatus("gui.modern.keybind_wb.u037");
    }

    private void cancelRecording() {
        this.recording = false;
        this.recordingRow = null;
        this.recordingDraft = null;
        KeybindManager.setRecording(false);
        showStatus("gui.modern.keybind_wb.u038");
    }

    private void clearRecording() {
        if (this.recordingDraft != null) {
            this.recordingDraft.clearCombinations();
        }
        showStatus("gui.modern.keybind_wb.u039");
    }

    private void commitRecording() {
        RowEntry target = this.recordingRow;
        if (target != null && this.recordingDraft != null) {
            setDraftKeybind(target, copyKeybind(this.recordingDraft));
        }
        this.recording = false;
        this.recordingRow = null;
        this.recordingDraft = null;
        KeybindManager.setRecording(false);
        showStatus("gui.modern.keybind_wb.u040");
    }

    private boolean isModifierKey(int keyCode) {
        return keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL
                || keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT
                || keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU;
    }

    private Set<Integer> getActiveModifiers() {
        Set<Integer> modifiers = new HashSet<>();
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return modifiers;
    }

    private void openPacketSelector(final RowEntry row) {
        if (row == null) {
            return;
        }
        this.packetSequences.clear();
        this.packetSequences.addAll(PacketSequenceManager.getAllSequenceNames());
        this.packetSequences.sort(Comparator.naturalOrder());
        this.packetScrollOffset = 0;
        this.packetRow = row;
        this.packetSelectorOpen = true;
        this.searchField.setFocused(false);
        showStatus("");
    }

    @Override
    public void save() {
        saveDraft();
    }

    private void saveDraft() {
        KeybindManager.keybinds.clear();
        for (BindableAction action : BindableAction.values()) {
            Keybind value = this.actionDraft.get(action);
            if (value == null) {
                value = new Keybind();
            }
            KeybindManager.keybinds.put(action, copyKeybind(value));
        }
        KeybindManager.pathSequenceKeybinds.clear();
        for (Map.Entry<String, Keybind> entry : this.sequenceDraft.entrySet()) {
            KeybindManager.pathSequenceKeybinds.put(entry.getKey(), copyKeybind(entry.getValue()));
        }
        KeybindManager.syncPathSequenceKeybinds();
        KeybindManager.saveConfig();
        this.draftDirty = false;
        showStatus("gui.modern.keybind_wb.u041");
    }

    private void revertDraft() {
        KeybindManager.loadConfig();
        loadDraft();
        showStatus("gui.modern.keybind_wb.u042");
    }

    private void syncPaths() {
        KeybindManager.syncPathSequenceKeybinds();
        refreshPathDraft();
        rebuildRows();
        showStatus("gui.modern.keybind_wb.u043");
    }

    private void clearSearch() {
        if (this.searchField != null) {
            this.searchField.setText("");
            this.searchField.setFocused(true);
        }
        this.scrollOffset = 0;
        rebuildRows();
    }

    private String getBindingText(RowEntry row, Keybind keybind, boolean fullText) {
        String keyName = formatKeybind(keybind);
        if (!row.isPacketAction()) {
            return keyName;
        }
        String parameter = keybind != null && keybind.getParameter() != null
                && !keybind.getParameter().trim().isEmpty() ? keybind.getParameter()
                        : I18n.format("gui.keybind.unselected");
        return keyName + " - " + I18n.format(fullText ? "gui.keybind.packet_target_full"
                : "gui.keybind.packet_target_short", parameter);
    }

    private String getPacketTarget(Keybind keybind) {
        String parameter = keybind != null && keybind.getParameter() != null
                && !keybind.getParameter().trim().isEmpty() ? keybind.getParameter()
                        : I18n.format("gui.keybind.unselected");
        return I18n.format("gui.keybind.packet_target_short", parameter);
    }

    private String formatKeybind(Keybind keybind) {
        if (!hasBinding(keybind)) {
            return I18n.format("gui.keybind.unbound");
        }
        List<String> values = new ArrayList<>();
        for (KeybindManager.KeyCombination combination : keybind.getCombinations()) {
            values.add(formatCombination(combination));
        }
        return String.join(" / ", values);
    }

    private List<String> getBindingLabels(Keybind keybind) {
        List<String> labels = new ArrayList<>();
        if (!hasBinding(keybind)) {
            return labels;
        }
        if (hasModifier(keybind, Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL)) {
            labels.add("Ctrl");
        }
        if (hasModifier(keybind, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)) {
            labels.add("Shift");
        }
        if (hasModifier(keybind, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)) {
            labels.add("Alt");
        }
        String keyName = Keyboard.getKeyName(keybind.getKeyCode());
        labels.add(keyName == null || keyName.trim().isEmpty() ? "?" : keyName);
        return labels;
    }

    private boolean hasBinding(Keybind keybind) {
        return keybind != null && !keybind.getCombinations().isEmpty();
    }

    private int[] getColumnWidths() {
        int available = Math.max(1, this.tableBounds.width - SCROLLBAR_WIDTH);
        if (available < 4) {
            return new int[] { available, 0, 0 };
        }
        if (available < 360) {
            int command = Math.max(1, available * 48 / 100);
            int binding = Math.max(1, available * 30 / 100);
            int source = Math.max(1, available - command - binding);
            return new int[] { command, binding, source };
        }
        int source = Math.max(70, Math.min(170, available / 8));
        int binding = Math.max(120, Math.min(300, available * 26 / 100));
        int command = available - source - binding;
        if (command < 130) {
            int shortage = 130 - command;
            int reduction = Math.min(shortage, Math.max(0, binding - 96));
            binding -= reduction;
            shortage -= reduction;
            reduction = Math.min(shortage, Math.max(0, source - 58));
            source -= reduction;
            command = Math.max(1, available - source - binding);
        }
        return new int[] { command, binding, source };
    }

    private void closePacketSelector() {
        this.packetSelectorOpen = false;
        this.packetRow = null;
        this.packetModalBounds = null;
        this.packetListBounds = null;
        this.packetCloseBounds = null;
        this.packetScrollbar.endDrag();
    }

    private Keybind copyKeybind(Keybind source) {
        if (source == null) {
            return new Keybind();
        }
        Set<Integer> modifiers = source.getModifiers() == null ? new HashSet<Integer>()
                : new HashSet<Integer>(source.getModifiers());
        Keybind copy = new Keybind(source.getKeyCode(), modifiers);
        copy.setCombinations(source.getCombinations());
        copy.setParameter(source.getParameter());
        return copy;
    }

    private boolean statusVisible() {
        return !this.statusMessage.isEmpty() && System.currentTimeMillis() < this.statusMessageUntil;
    }

    private void showStatus(String message) {
        this.statusMessage = message == null ? "" : message;
        this.statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private boolean isHoverRegion(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int getTotalRowCount() {
        return BindableAction.values().length + this.sequenceDraft.size();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
