package com.zszl.zszlScriptMod.gui.modern.packet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.InputTimelineManager;
import com.zszl.zszlScriptMod.gui.packet.PacketSnapshotManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;

/** Sent/received viewer with search, timeline correlation and replay actions. */
final class PacketViewerPanel extends PacketPanelBase {
    private enum Mode { SENT, RECEIVED }
    private enum TimelineFilter { ALL, KEYBOARD, MOUSE, LEFT, RIGHT, MIDDLE }
    private enum Window { ALL, MS50, MS100, MS200, MS500 }
    private enum TextScale { LARGE("gui.modern.pktview.u001", 1.0F), MEDIUM("gui.modern.pktview.u002", 0.85F), SMALL("gui.modern.pktview.u003", 0.72F); final String label; final float scale; TextScale(String label, float scale) { this.label = label; this.scale = scale; } }

    private final PacketTextField search = new PacketTextField(7001, 32767);
    private final List<PacketCaptureHandler.CapturedPacketData> fixedPackets;
    private final String customTitle;
    private final boolean readOnly;
    private final boolean minimalView;
    private final Long focusTimestamp;
    private final Set<Integer> selected = new HashSet<>();
    private List<PacketCaptureHandler.CapturedPacketData> visible = new ArrayList<>();
    private List<InputTimelineManager.InputEventRecord> timeline = new ArrayList<>();
    private Mode mode = Mode.SENT;
    private TimelineFilter timelineFilter = TimelineFilter.ALL;
    private Window window = Window.ALL;
    private boolean freeze;
    private int packetScroll;
    private int timelineScroll;
    private int lastSelected = -1;
    private int selectedTimeline = -1;
    private int focusedPacketIndex = -1;
    private boolean focusScrollPending;
    private boolean focusApplied;
    private long lastClickAt;
    private int packetRowHeight;
    private int packetColumns = 1;
    private TextScale textScale = TextScale.MEDIUM;
    private boolean hideHint = true;
    private ModernMainLayout.Rect compactViewport;
    private int compactScroll, compactMaxScroll;
    private boolean draggingCompactScrollbar;
    private final ModernHoverScrollbar compactScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar timelineScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar packetScrollbar = new ModernHoverScrollbar();
    private final PacketDropdown filterDropdown = new PacketDropdown(
            "gui.modern.pktview.u040", "gui.modern.pktview.u029", "gui.modern.pktview.u030",
            "gui.modern.pktview.u031", "gui.modern.pktview.u032", "gui.modern.pktview.u033");
    private final PacketDropdown windowDropdown = new PacketDropdown(
            "gui.modern.pktview.u040", "±50ms", "±100ms", "±200ms", "±500ms");
    private final PacketDropdown columnsDropdown = new PacketDropdown("1", "2", "3");
    private final PacketDropdown scaleDropdown = new PacketDropdown(
            "gui.modern.pktview.u001", "gui.modern.pktview.u002", "gui.modern.pktview.u003");
    private final PacketDropdown hintDropdown = new PacketDropdown("gui.modern.pktview.u015", "gui.modern.pktview.u014");
    private double splitRatio = 0.32D;
    private boolean draggingSplit, draggingTimelineBar, draggingPacketBar;
    private ModernMainLayout.Rect splitBounds;
    private ModernMainLayout.Rect searchBounds;
    private ModernMainLayout.Rect leftBounds;
    private ModernMainLayout.Rect rightBounds;
    private ModernMainLayout.Rect footerBounds;
    private final ModernMainLayout.Rect[] timelineControls = new ModernMainLayout.Rect[3];
    private final List<ModernMainLayout.Rect> footerActions = new ArrayList<>();
    private final ModernMainLayout.Rect[] optionBounds = new ModernMainLayout.Rect[3];
    private final ModernMainLayout.Rect[] directionBounds = new ModernMainLayout.Rect[2];

    PacketViewerPanel(PacketWorkbenchTab owner) { this(owner, null, "", false, "C2S", false, null); }
    PacketViewerPanel(PacketWorkbenchTab owner, List<PacketCaptureHandler.CapturedPacketData> packets, String title, boolean readOnly) {
        this(owner, packets, title, readOnly, "C2S", false, null);
    }
    PacketViewerPanel(PacketWorkbenchTab owner, List<PacketCaptureHandler.CapturedPacketData> packets, String title, boolean readOnly, String initialDirection) {
        this(owner, packets, title, readOnly, initialDirection, false, null);
    }
    PacketViewerPanel(PacketWorkbenchTab owner, List<PacketCaptureHandler.CapturedPacketData> packets, String title,
            boolean readOnly, String initialDirection, boolean minimalView) {
        this(owner, packets, title, readOnly, initialDirection, minimalView, null);
    }
    PacketViewerPanel(PacketWorkbenchTab owner, List<PacketCaptureHandler.CapturedPacketData> packets, String title,
            boolean readOnly, String initialDirection, boolean minimalView, Long focusTimestamp) {
        super(owner, "gui.modern.pktview.u004", !minimalView, !minimalView);
        fixedPackets = packets == null ? null : new ArrayList<>(packets);
        customTitle = title == null ? "" : title;
        this.readOnly = readOnly;
        this.minimalView = minimalView;
        this.focusTimestamp = focusTimestamp;
        mode = "S2C".equals(PacketSendSupport.normalizeDirection(initialDirection)) ? Mode.RECEIVED : Mode.SENT;
    }

    @Override protected void initializePanel() {
        splitRatio = MainUiLayoutManager.getModernSplitRatio("packet.viewer.details", splitRatio);
        search.ensure(font);
        if (!minimalView) {
            registerDropdown(filterDropdown);
            registerDropdown(windowDropdown);
            registerDropdown(columnsDropdown);
            registerDropdown(scaleDropdown);
            registerDropdown(hintDropdown);
            filterDropdown.setSelected(timelineFilter.ordinal());
            windowDropdown.setSelected(window.ordinal());
            columnsDropdown.setSelected(Math.max(0, packetColumns - 1));
            scaleDropdown.setSelected(textScale.ordinal());
            hintDropdown.setSelected(hideHint ? 1 : 0);
        }
        refresh(false);
    }
    @Override public void updateScreen() { search.update(); if (!freeze) refresh(true); }

    @Override protected void drawBody(FontRenderer renderer, ModernMainLayout.Rect area, int mouseX, int mouseY) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        if (minimalView) {
            drawMinimalBody(renderer, area, mouseX, mouseY);
            return;
        }
        syncDropdowns();
        int pillWidth = 72;
        int searchWidth = Math.max(1, area.width - 36 - pillWidth * 2);
        searchBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 42, searchWidth, 22);
        field(search, searchBounds, null);
        directionBounds[0] = new ModernMainLayout.Rect(searchBounds.right() + 6, area.y + 42, pillWidth, 22);
        directionBounds[1] = new ModernMainLayout.Rect(directionBounds[0].right() + 4, area.y + 42, pillWidth, 22);
        drawButton(renderer, directionBounds[0], "gui.modern.pktview.u005", mouseX, mouseY, true, mode == Mode.SENT);
        drawButton(renderer, directionBounds[1], "gui.modern.pktview.u006", mouseX, mouseY, true, mode == Mode.RECEIVED);
        String heading = customTitle.isEmpty()
                ? tr(mode == Mode.SENT ? "gui.modern.pktview.u005" : "gui.modern.pktview.u006")
                : customTitle.startsWith("gui.") ? tr(customTitle) : customTitle;
        text(renderer, heading + "  (" + visible.size() + ")", area.x + 12, area.y + 70, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(100, area.width - 24));
        int top = area.y + 84;
        int footer = area.bottom() - footerHeight(area);
        boolean compact = area.width < 520;
        if (compact) {
            splitBounds = null;
            int fullWidth = Math.max(1, area.width - 24);
            compactViewport = new ModernMainLayout.Rect(area.x + 12, top, fullWidth,
                    Math.max(1, footer - top - ModernHoverScrollbar.CONTENT_GAP));
            fullWidth = ModernHoverScrollbar.contentWidth(fullWidth);
            int leftHeight = 140;
            int rightHeight = 320;
            compactMaxScroll = Math.max(0, leftHeight + 8 + rightHeight - compactViewport.height);
            compactScroll = Math.max(0, Math.min(compactScroll, compactMaxScroll));
            int contentY = top - compactScroll;
            leftBounds = new ModernMainLayout.Rect(area.x + 12, contentY, fullWidth, leftHeight);
            rightBounds = new ModernMainLayout.Rect(area.x + 12, leftBounds.bottom() + 8, fullWidth, rightHeight);
        } else {
            compactViewport = null;
            compactMaxScroll = 0;
            draggingCompactScrollbar = false;
            compactScrollbar.idle();
            int available = Math.max(2, area.width - 24);
            ModernSplitPane.Split split = ModernSplitPane.calculate(available, splitRatio, 180, 280, 150, 220);
            splitRatio = split.ratio;
            int x = area.x + 12;
            leftBounds = new ModernMainLayout.Rect(x, top, split.firstWidth, Math.max(1, footer - top));
            splitBounds = ModernSplitPane.verticalDividerBounds(leftBounds.x, leftBounds.width, 8, top,
                    Math.max(1, footer - top));
            rightBounds = new ModernMainLayout.Rect(leftBounds.right() + 8, top,
                    Math.max(1, split.secondWidth - 8), Math.max(1, footer - top));
            ModernSplitPane.drawVerticalDivider(splitBounds, mouseX, mouseY, draggingSplit);
        }
        if (compact) ModernUiRenderer.beginClip(compactViewport);
        ModernUiRenderer.drawSubtlePanel(leftBounds.x, leftBounds.y, leftBounds.width, leftBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(rightBounds.x, rightBounds.y, rightBounds.width, rightBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawTimeline(renderer, mouseX, mouseY);
        drawPackets(renderer, mouseX, mouseY);
        if (compact) {
            ModernUiRenderer.endClip();
            drawCompactScrollbar(mouseX, mouseY);
        }
        footerBounds = new ModernMainLayout.Rect(area.x + 12, area.bottom() - footerHeight(area), Math.max(1, area.width - 24), footerHeight(area));
        drawFooter(renderer, mouseX, mouseY);
    }

    private void drawMinimalBody(FontRenderer renderer, ModernMainLayout.Rect area, int mouseX, int mouseY) {
        searchBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 8,
                Math.max(1, area.width - 24), 22);
        field(search, searchBounds, null);
        directionBounds[0] = null;
        directionBounds[1] = null;
        footerBounds = new ModernMainLayout.Rect(area.x + 12, area.bottom() - footerHeight(area),
                Math.max(1, area.width - 24), footerHeight(area));
        int top = searchBounds.bottom() + 8;
        int footer = footerBounds.y - 6;
        rightBounds = new ModernMainLayout.Rect(area.x + 12, top, Math.max(1, area.width - 24),
                Math.max(1, footer - top));
        leftBounds = null;
        splitBounds = null;
        compactViewport = null;
        compactMaxScroll = 0;
        draggingCompactScrollbar = false;
        compactScrollbar.idle();
        ModernUiRenderer.drawSubtlePanel(rightBounds.x, rightBounds.y, rightBounds.width, rightBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawPackets(renderer, mouseX, mouseY);
        drawFooter(renderer, mouseX, mouseY);
    }

    private void drawTimeline(FontRenderer renderer, int mouseX, int mouseY) {
        text(renderer, "gui.modern.pktview.u007", leftBounds.x + 8, leftBounds.y + 8, ModernUiRenderer.TEXT, leftBounds.width - 16);
        int controlGap = 4;
        int controlWidth = Math.max(1, (leftBounds.width - 16 - controlGap * 2) / 3);
        int controlY = leftBounds.y + 30;
        timelineControls[0] = new ModernMainLayout.Rect(leftBounds.x + 8, controlY, controlWidth, 18);
        timelineControls[1] = new ModernMainLayout.Rect(timelineControls[0].right() + controlGap, controlY, controlWidth, 18);
        timelineControls[2] = new ModernMainLayout.Rect(timelineControls[1].right() + controlGap, controlY,
                Math.max(1, leftBounds.right() - timelineControls[1].right() - 8), 18);
        drawButton(renderer, timelineControls[0], freeze ? "gui.modern.pktview.u008" : "gui.modern.pktview.u009", mouseX, mouseY);
        filterDropdown.drawButton(renderer, timelineControls[1], mouseX, mouseY);
        windowDropdown.drawButton(renderer, timelineControls[2], mouseX, mouseY);
        int y = timelineListTop();
        int visibleRows = Math.max(1, (leftBounds.height - (timelineListTop() - leftBounds.y) - 6) / 23);
        List<InputTimelineManager.InputEventRecord> events = filterTimeline();
        timelineScroll = clamp(timelineScroll, 0, timelineScrollMax());
        ModernMainLayout.Rect listClip = new ModernMainLayout.Rect(leftBounds.x + 4, y, leftBounds.width - 8,
                Math.max(1, leftBounds.bottom() - y - 4));
        ModernUiRenderer.beginClip(listClip);
        int rowY = listClip.y;
        for (int i = timelineScroll; i < Math.min(events.size(), timelineScroll + visibleRows); i++) {
            InputTimelineManager.InputEventRecord event = events.get(i);
            boolean hover = mouseX >= listClip.x && mouseX < listClip.right() && mouseY >= rowY && mouseY < rowY + 20;
            ModernUiRenderer.drawSubtlePanel(listClip.x, rowY, ModernHoverScrollbar.contentWidth(listClip.width), 20, 3,
                    i == selectedTimeline ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    i == selectedTimeline ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(renderer, event.getTimestampText() + " " + event.getCategory() + "/" + event.getDetail(), listClip.x + 6,
                    rowY + 6, ModernUiRenderer.SUBTLE_TEXT, ModernHoverScrollbar.contentWidth(listClip.width - 10));
            rowY += 23;
        }
        ModernUiRenderer.endClip();
        if (events.isEmpty()) text(renderer, "gui.modern.pktview.u010", leftBounds.x + 10, timelineListTop(), ModernUiRenderer.MUTED_TEXT, leftBounds.width - 20);
        int timelineMax = timelineScrollMax();
        if (timelineMax > 0) {
            timelineScrollbar.draw(listClip, timelineScroll, timelineMax, visibleRows, events.size(), mouseX, mouseY,
                    value -> timelineScroll = value);
        } else {
            timelineScrollbar.idle();
        }
    }

    private void drawPackets(FontRenderer renderer, int mouseX, int mouseY) {
        int controlGap = 4, controlWidth = Math.max(38, Math.min(64, (rightBounds.width - 20 - controlGap * 2) / 3));
        int controlsWidth = controlWidth * 3 + controlGap * 2;
        int y;
        if (minimalView) {
            y = rightBounds.y + 6;
        } else {
            text(renderer, "gui.modern.pktview.u011", rightBounds.x + 8, rightBounds.y + 8, ModernUiRenderer.TEXT, Math.max(30, rightBounds.width - controlsWidth - 20));
            optionBounds[0] = new ModernMainLayout.Rect(rightBounds.right() - controlsWidth - 6, rightBounds.y + 4, controlWidth, 20);
            optionBounds[1] = new ModernMainLayout.Rect(optionBounds[0].right() + controlGap, rightBounds.y + 4, controlWidth, 20);
            optionBounds[2] = new ModernMainLayout.Rect(optionBounds[1].right() + controlGap, rightBounds.y + 4, controlWidth, 20);
            columnsDropdown.drawButton(renderer, optionBounds[0], mouseX, mouseY);
            scaleDropdown.drawButton(renderer, optionBounds[1], mouseX, mouseY);
            hintDropdown.drawButton(renderer, optionBounds[2], mouseX, mouseY);
            y = rightBounds.y + 28;
        }
        int cardHeight = packetCardHeight();
        packetRowHeight = cardHeight;
        int rows = packetVisibleRows();
        int columns = effectiveColumns();
        applyInitialFocusIfNeeded();
        if (focusScrollPending && focusedPacketIndex >= 0 && focusedPacketIndex < visible.size()) {
            int focusRow = focusedPacketIndex / Math.max(1, columns);
            packetScroll = clamp(focusRow, 0, packetScrollMax());
            focusScrollPending = false;
        }
        int cardGap = 6, cardWidth = Math.max(1, (ModernHoverScrollbar.contentWidth(rightBounds.width - 9) - cardGap * (columns - 1)) / columns);
        for (int row = packetScroll; row < Math.min(packetRowCount(), packetScroll + rows); row++) {
            for (int col = 0; col < columns; col++) {
                int i = row * columns + col; if (i >= visible.size()) break;
                PacketCaptureHandler.CapturedPacketData packet = visible.get(i);
                int rowY = y + (row - packetScroll) * packetRowHeight, cardX = rightBounds.x + 5 + col * (cardWidth + cardGap);
                boolean hover = mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= rowY && mouseY < rowY + cardHeight;
                boolean chosen = selected.contains(i) || i == focusedPacketIndex;
                ModernUiRenderer.drawSubtlePanel(cardX, rowY, cardWidth, cardHeight, 4,
                    chosen ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    chosen ? ModernUiRenderer.ACCENT : hover ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                if (chosen) {
                    ModernUiRenderer.drawRoundedRect(cardX, rowY + 6, 3, cardHeight - 12, 1, ModernUiRenderer.ACCENT);
                }
                int step = packetLineStep(), textX = cardX + 7, textWidth = Math.max(1, cardWidth - 14);
                int previewMax = textScale == TextScale.LARGE ? 58 : textScale == TextScale.SMALL ? 92 : 76;
                String id = packet.isFmlPacket ? "FML " + safe(packet.channel) : "ID " + (packet.packetId == null ? "?" : String.format("0x%02X", packet.packetId));
                text(renderer, "#" + (i + 1) + "  " + safe(packet.packetClassName), textX, rowY + 4, ModernUiRenderer.TEXT, textWidth);
                text(renderer, id, textX, rowY + 4 + step, ModernUiRenderer.SUBTLE_TEXT, textWidth);
                text(renderer, "HEX " + inline(packet.getHexData(), previewMax), textX, rowY + 4 + step * 2, ModernUiRenderer.SUBTLE_TEXT, textWidth);
                text(renderer, tr("gui.modern.pktview.fmt.decode", inline(packet.getDecodedData(), previewMax)), textX, rowY + 4 + step * 3, ModernUiRenderer.SUBTLE_TEXT, textWidth);
                if (!hideHint) text(renderer, "gui.modern.pktview.u016", textX, rowY + 4 + step * 4, ModernUiRenderer.MUTED_TEXT, textWidth);
            }
        }
        if (visible.isEmpty()) text(renderer, "gui.modern.pktview.u017", rightBounds.x + 12,
                rightBounds.y + (minimalView ? 26 : 48), ModernUiRenderer.MUTED_TEXT, rightBounds.width - 24);
        int packetMax = packetScrollMax();
        ModernMainLayout.Rect packetClip = new ModernMainLayout.Rect(rightBounds.x + 4,
                rightBounds.y + (minimalView ? 4 : 28), rightBounds.width - 8,
                Math.max(1, rightBounds.height - (minimalView ? 8 : 32)));
        if (packetMax > 0) {
            packetScrollbar.draw(packetClip, packetScroll, packetMax, packetVisibleRows(), packetRowCount(), mouseX, mouseY,
                    value -> packetScroll = value);
        } else {
            packetScrollbar.idle();
        }
    }

    private void drawFooter(FontRenderer renderer, int mouseX, int mouseY) {
        String[] labels = readOnly && !allowsMockSend()
                ? new String[] { "gui.modern.pktview.u018", "gui.modern.pktview.u021" }
                : readOnly
                ? new String[] { "gui.modern.pktview.u018", "gui.modern.pktview.u021", "gui.modern.pktview.u022" }
                : new String[] { "gui.modern.pktview.u019", "gui.modern.pktview.u020", "gui.modern.pktview.u021", "gui.modern.pktview.u022", "gui.modern.pktview.u023", "gui.modern.pktview.u024", "gui.modern.pktview.u025", "gui.modern.pktview.u026", "gui.modern.pktview.u027" };
        footerActions.clear();
        int columns = footerBounds.height >= 60 ? 3 : labels.length;
        int rows = (labels.length + columns - 1) / columns;
        int gap = 4;
        int width = Math.max(1, (footerBounds.width - gap * Math.max(0, columns - 1)) / Math.max(1, columns));
        int height = Math.max(18, (footerBounds.height - gap * Math.max(0, rows - 1)) / Math.max(1, rows));
        for (int i = 0; i < labels.length; i++) {
            int row = i / columns, column = i % columns;
            ModernMainLayout.Rect r = new ModernMainLayout.Rect(footerBounds.x + column * (width + gap),
                    footerBounds.y + row * (height + gap), width, height);
            footerActions.add(r);
            boolean enabled;
            if (readOnly) {
                enabled = i == 0 || ((i == 1 || i == 2) && !selected.isEmpty());
            } else {
                switch (i) {
                    case 0: enabled = !selected.isEmpty(); break;
                    case 1: enabled = true; break;
                    case 2: case 3: case 4: case 7: enabled = !selected.isEmpty(); break;
                    case 5: case 6: case 8: default: enabled = true; break;
                }
            }
            boolean hover = enabled && r.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3,
                    !enabled ? 0xFF151E26 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    enabled && (i == 3 || i == 0 && !readOnly) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            centeredLabel(renderer, labels[i], r,
                    !enabled ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.TEXT);
        }
    }

    private int footerHeight(ModernMainLayout.Rect area) { return area != null && area.width < 520 ? 70 : 26; }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button == 0 && splitBounds != null && splitBounds.contains(x, y)) {
            draggingSplit = true;
            return true;
        }
        if (button == 0 && timelineScrollbar.beginDrag(x, y)) {
            draggingTimelineBar = true;
            return true;
        }
        if (button == 0 && packetScrollbar.beginDrag(x, y)) {
            draggingPacketBar = true;
            return true;
        }
        if (button == 0 && compactScrollbar.beginDrag(x, y)) {
            draggingCompactScrollbar = true;
            return true;
        }
        if (searchBounds != null && search.click(x, y, button)) { return true; }
        if (button == 0 && directionBounds[0] != null && directionBounds[0].contains(x, y) && !readOnly) {
            mode = Mode.SENT; refresh(false); return true;
        }
        if (button == 0 && directionBounds[1] != null && directionBounds[1].contains(x, y) && !readOnly) {
            mode = Mode.RECEIVED; refresh(false); return true;
        }
        if (button != 0 && button != 1) return true;
        if (leftBounds != null && leftBounds.contains(x, y)) {
            if (timelineControls[0] != null && timelineControls[0].contains(x, y)) {
                freeze = !freeze;
                refresh(true);
                return true;
            }
            if (y < timelineListTop()) {
                refresh(true); return true;
            }
            int index = (y - timelineListTop()) / 23 + timelineScroll;
            if (index >= 0 && index < filterTimeline().size()) {
                selectedTimeline = index;
                refresh(true);
                selectNearestTimelinePacket();
                return true;
            }
        }
        if (rightBounds != null && rightBounds.contains(x, y)) {
            int index = packetHitIndex(x, y);
            if (index >= 0) {
                if (button == 1) {
                    copyPacketRow(index, packetHitOffsetY(y));
                    return true;
                }
                if (System.currentTimeMillis() - lastClickAt < 350L && lastSelected == index) {
                    owner.openDetail(visible.get(index), index, mode == Mode.RECEIVED ? "S2C" : "C2S");
                }
                select(index); lastSelected = index; lastClickAt = System.currentTimeMillis(); return true;
            }
        }
        if (footerBounds != null && footerBounds.contains(x, y)) {
            for (int i = 0; i < footerActions.size(); i++) if (footerActions.get(i).contains(x, y)) return footerAction(i);
            return true;
        }
        return true;
    }

    private boolean footerAction(int index) {
        if (readOnly) {
            if (index == 0) owner.back();
            else if (index == 1 && !selected.isEmpty()) copySelected();
            else if (index == 2 && allowsMockSend() && !selected.isEmpty()) sendSelected();
            return true;
        }
        switch (index) {
            case 0: if (readOnly) owner.back(); else if (!selected.isEmpty()) owner.openSequenceEditor(null, selectedPackets(), mode == Mode.RECEIVED ? "S2C" : "C2S"); return true;
            case 1: if (!readOnly) { mode = mode == Mode.SENT ? Mode.RECEIVED : Mode.SENT; refresh(false); } return true;
            case 2: if (!selected.isEmpty()) copySelected(); return true;
            case 3: if (!selected.isEmpty() && !readOnly) sendSelected(); return true;
            case 4: if (!selected.isEmpty()) saveSnapshot(); return true;
            case 5: owner.openSnapshots(); return true;
            case 6: owner.openIdRecords(); return true;
            case 7: owner.openCapturedIdGenerator(selectedHex()); return true;
            case 8: InputTimelineManager.clear(); timeline.clear(); selectedTimeline = -1; owner.status("gui.modern.pktview.u028"); return true;
            default: return true;
        }
    }

    private boolean allowsMockSend() {
        return focusTimestamp != null;
    }

    private void select(int index) {
        if (index >= 0 && index < visible.size()) owner.rememberSelectedPacket(visible.get(index));
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            if (!selected.add(index)) selected.remove(index);
        } else if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            int from = lastSelected < 0 ? index : Math.min(lastSelected, index), to = lastSelected < 0 ? index : Math.max(lastSelected, index);
            selected.clear(); for (int i = from; i <= to; i++) selected.add(i);
        } else { selected.clear(); selected.add(index); }
    }

    private void refresh(boolean preserve) {
        List<PacketCaptureHandler.CapturedPacketData> source = fixedPackets != null ? fixedPackets
                : (mode == Mode.SENT ? owner.capturedPackets() : owner.receivedPackets());
        String query = search.text().trim();
        visible = new ArrayList<>();
        for (PacketCaptureHandler.CapturedPacketData packet : source) if (matches(packet, query)) visible.add(packet);
        if (!preserve) { selected.clear(); lastSelected = -1; packetScroll = 0; }
        selected.removeIf(i -> i < 0 || i >= visible.size());
        if (!freeze || timeline.isEmpty()) timeline = new ArrayList<>(InputTimelineManager.getEventsSnapshot());
        List<InputTimelineManager.InputEventRecord> events = filterTimeline();
        if (selectedTimeline >= events.size()) selectedTimeline = events.isEmpty() ? -1 : events.size() - 1;
        if (selectedTimeline >= 0 && selectedTimeline < events.size() && window != Window.ALL) {
            long center = events.get(selectedTimeline).getTimestamp();
            List<PacketCaptureHandler.CapturedPacketData> narrowed = new ArrayList<>();
            for (PacketCaptureHandler.CapturedPacketData packet : visible) if (Math.abs(packet.getLastTimestamp() - center) <= windowMillis()) narrowed.add(packet);
            visible = narrowed;
            if (visible.isEmpty()) {
                int nearest = nearestPacketIndex(visibleBeforeWindow(source, query), center);
                if (nearest >= 0) {
                    List<PacketCaptureHandler.CapturedPacketData> fallback = visibleBeforeWindow(source, query);
                    visible.add(fallback.get(nearest));
                }
            }
        }
        applyInitialFocusIfNeeded();
        selected.removeIf(i -> i < 0 || i >= visible.size());
        packetScroll = clamp(packetScroll, 0, packetScrollMax());
        timelineScroll = clamp(timelineScroll, 0, timelineScrollMax());
    }

    private void applyInitialFocusIfNeeded() {
        if (focusApplied || focusTimestamp == null || visible.isEmpty()) {
            return;
        }
        int nearest = nearestPacketIndex(visible, focusTimestamp.longValue());
        if (nearest >= 0) {
            selected.clear();
            selected.add(Integer.valueOf(nearest));
            lastSelected = nearest;
            focusedPacketIndex = nearest;
            focusScrollPending = true;
            focusApplied = true;
        }
    }

    private List<PacketCaptureHandler.CapturedPacketData> visibleBeforeWindow(List<PacketCaptureHandler.CapturedPacketData> source, String query) {
        List<PacketCaptureHandler.CapturedPacketData> result = new ArrayList<>();
        for (PacketCaptureHandler.CapturedPacketData packet : source) if (matches(packet, query)) result.add(packet);
        return result;
    }

    private int nearestPacketIndex(List<PacketCaptureHandler.CapturedPacketData> packets, long timestamp) {
        int best = -1; long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < packets.size(); i++) {
            PacketCaptureHandler.CapturedPacketData packet = packets.get(i); if (packet == null) continue;
            long diff = packetDistance(packet, timestamp);
            if (diff < bestDiff) { best = i; bestDiff = diff; }
        }
        return best;
    }

    private long packetDistance(PacketCaptureHandler.CapturedPacketData packet, long timestamp) {
        if (packet == null) return Long.MAX_VALUE;
        if (timestamp < packet.timestamp) return packet.timestamp - timestamp;
        if (timestamp > packet.getLastTimestamp()) return timestamp - packet.getLastTimestamp();
        return 0L;
    }

    private boolean matches(PacketCaptureHandler.CapturedPacketData packet, String query) {
        return PacketQuerySupport.matches(packet, query);
    }

    private List<InputTimelineManager.InputEventRecord> filterTimeline() {
        List<InputTimelineManager.InputEventRecord> result = new ArrayList<>();
        for (InputTimelineManager.InputEventRecord event : timeline) {
            if (event == null) continue;
            if (timelineFilter == TimelineFilter.KEYBOARD && !"键盘".equals(event.getCategory())) continue;
            if (timelineFilter == TimelineFilter.MOUSE && !"鼠标".equals(event.getCategory())) continue;
            if (timelineFilter == TimelineFilter.LEFT && !("鼠标".equals(event.getCategory()) && "左键".equals(event.getDetail()))) continue;
            if (timelineFilter == TimelineFilter.RIGHT && !("鼠标".equals(event.getCategory()) && "右键".equals(event.getDetail()))) continue;
            if (timelineFilter == TimelineFilter.MIDDLE && !("鼠标".equals(event.getCategory()) && "中键".equals(event.getDetail()))) continue;
            result.add(event);
        }
        return result;
    }

    private void copySelected() {
        StringBuilder out = new StringBuilder();
        for (Integer index : new TreeSet<>(selected)) if (index != null && index >= 0 && index < visible.size()) {
            PacketCaptureHandler.CapturedPacketData p = visible.get(index);
            out.append("--- Packet #").append(index + 1).append(" ---\n")
                    .append("Timestamp: ").append(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(p.timestamp))).append('\n')
                    .append("Class: ").append(safe(p.packetClassName)).append('\n')
                    .append("ID/Channel: ").append(p.isFmlPacket ? safe(p.channel)
                            : p.packetId == null ? "" : String.format("0x%02X (%d)", p.packetId, p.packetId)).append('\n')
                    .append("HEX Data: ").append(safe(p.getHexData())).append('\n')
                    .append("Decoded: ").append(safe(p.getDecodedFullData())).append("\n\n");
        }
        if (out.length() > 0) {
            String target = PacketClipboard.copyOrExport(owner.minecraft(), out.toString());
            owner.status(tr("gui.modern.pktview.fmt.copied", String.valueOf(selected.size())));
        }
    }

    private void copyPacketRow(int index, int rowOffset) {
        if (index < 0 || index >= visible.size()) return;
        PacketCaptureHandler.CapturedPacketData packet = visible.get(index);
        String value;
        int line = Math.max(0, rowOffset - 4) / Math.max(1, packetLineStep());
        if (line <= 0) value = safe(packet.packetClassName);
        else if (line == 1) value = packet.isFmlPacket ? safe(packet.channel) : packet.packetId == null ? "" : String.format("0x%02X (%d)", packet.packetId, packet.packetId);
        else if (line == 2) value = packet.getHexData();
        else value = packet.getDecodedData();
        PacketClipboard.copyOrExport(owner.minecraft(), value);
        owner.status("gui.modern.pktview.u034");
    }

    private void selectNearestTimelinePacket() {
        List<InputTimelineManager.InputEventRecord> events = filterTimeline();
        if (selectedTimeline < 0 || selectedTimeline >= events.size() || visible.isEmpty()) return;
        long timestamp = events.get(selectedTimeline).getTimestamp();
        int best = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < visible.size(); i++) {
            long diff = packetDistance(visible.get(i), timestamp);
            if (diff < bestDiff) { best = i; bestDiff = diff; }
        }
        selected.clear(); selected.add(best); lastSelected = best;
        int columns = effectiveColumns();
        int row = columns <= 0 ? 0 : best / columns;
        packetScroll = clamp(row, 0, packetScrollMax());
    }

    private int packetVisibleRows() {
        return rightBounds == null ? 1 : Math.max(1,
                (rightBounds.height - (minimalView ? 10 : 34)) / Math.max(1, packetCardHeight()));
    }

    private int packetScrollMax() { return Math.max(0, packetRowCount() - packetVisibleRows()); }

    private int effectiveColumns() {
        if (rightBounds == null) return 1;
        int maxByWidth = Math.max(1, Math.min(3, (rightBounds.width + 6) / 156));
        return Math.max(1, Math.min(packetColumns, maxByWidth));
    }

    private int packetRowCount() {
        int columns = effectiveColumns();
        return columns <= 0 ? 0 : (visible.size() + columns - 1) / columns;
    }

    private int packetCardHeight() {
        int lines = hideHint ? 4 : 5;
        return Math.max(52, lines * packetLineStep() + 8);
    }

    private int packetLineStep() {
        switch (textScale) {
            case LARGE: return 15;
            case SMALL: return 10;
            case MEDIUM: default: return 12;
        }
    }

    private TextScale nextTextScale(TextScale value) {
        TextScale[] values = TextScale.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private int packetHitIndex(int x, int y) {
        if (rightBounds == null || y < packetListTop()) return -1;
        int columns = effectiveColumns();
        int cardGap = 6;
        int cardWidth = Math.max(1, (ModernHoverScrollbar.contentWidth(rightBounds.width - 9) - cardGap * (columns - 1)) / columns);
        int relativeY = y - packetListTop();
        int rowStep = Math.max(1, packetRowHeight > 0 ? packetRowHeight : packetCardHeight());
        int row = relativeY / rowStep + packetScroll;
        int rowOffset = relativeY % rowStep;
        if (rowOffset < 0 || rowOffset >= packetCardHeight()) return -1;
        int relativeX = x - (rightBounds.x + 5);
        if (relativeX < 0) return -1;
        int col = relativeX / (cardWidth + cardGap);
        if (col < 0 || col >= columns || relativeX % (cardWidth + cardGap) >= cardWidth) return -1;
        int index = row * columns + col;
        return index >= 0 && index < visible.size() ? index : -1;
    }

    private int packetHitOffsetY(int y) {
        if (rightBounds == null) return 0;
        int rowStep = Math.max(1, packetRowHeight > 0 ? packetRowHeight : packetCardHeight());
        int offset = (y - packetListTop()) % rowStep;
        return offset < 0 ? offset + rowStep : offset;
    }

    private int timelineVisibleRows() {
        return leftBounds == null ? 1 : Math.max(1, (leftBounds.height - (timelineListTop() - leftBounds.y) - 6) / 23);
    }

    private int timelineScrollMax() { return Math.max(0, filterTimeline().size() - timelineVisibleRows()); }

    private int packetListTop() {
        return rightBounds == null ? 0 : rightBounds.y + (minimalView ? 6 : 28);
    }

    private void saveSnapshot() {
        if (selected.isEmpty()) return;
        final List<PacketCaptureHandler.CapturedPacketData> packets = selectedPackets();
        final String captureMode = mode.name();
        owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktview.u023", "gui.modern.pktview.u035", com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr("gui.modern.pktview.u036") + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()), true,
                value -> {
                    boolean ok = PacketSnapshotManager.saveSnapshot(value, packets, captureMode);
                    owner.status(ok ? "gui.modern.pktview.u037" : "gui.modern.pktview.u038");
                }));
    }

    private void sendSelected() { PacketSendSupport.sendPackets(owner.minecraft(), selectedPackets(), 1, mode == Mode.RECEIVED ? "S2C" : "C2S"); owner.status("gui.modern.pktview.u039"); }
    PacketCaptureHandler.CapturedPacketData selectedPacket() {
        List<PacketCaptureHandler.CapturedPacketData> packets = selectedPackets();
        return packets.isEmpty() ? null : packets.get(0);
    }
    private List<PacketCaptureHandler.CapturedPacketData> selectedPackets() { List<PacketCaptureHandler.CapturedPacketData> result = new ArrayList<>(); for (Integer i : new TreeSet<>(selected)) if (i >= 0 && i < visible.size()) result.add(visible.get(i)); return result; }
    private List<String> selectedHex() { List<String> result = new ArrayList<>(); for (PacketCaptureHandler.CapturedPacketData p : selectedPackets()) result.add(p.getHexData()); return result; }

    @Override public boolean keyTyped(char c, int code) {
        if (search.key(c, code)) { refresh(false); return true; }
        if (code == Keyboard.KEY_RETURN) {
            int first = selected.isEmpty() ? -1 : new TreeSet<>(selected).first();
            if (first >= 0 && first < visible.size()) owner.openDetail(visible.get(first), first, mode == Mode.RECEIVED ? "S2C" : "C2S");
            else refresh(false);
            return true;
        }
        return false;
    }
    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0) return false;
        if (rightBounds != null && rightBounds.contains(lastMouseX, lastMouseY)) {
            int before = packetScroll;
            packetScroll = clamp(packetScroll + (wheel > 0 ? -1 : 1), 0, packetScrollMax());
            if (before != packetScroll) return true;
            return compactLayout() && scrollCompact(wheel);
        }
        if (leftBounds != null && leftBounds.contains(lastMouseX, lastMouseY)) {
            int before = timelineScroll;
            timelineScroll = clamp(timelineScroll + (wheel > 0 ? -1 : 1), 0, timelineScrollMax());
            if (before != timelineScroll) return true;
            return compactLayout() && scrollCompact(wheel);
        }
        if (compactViewport != null && compactViewport.contains(lastMouseX, lastMouseY)) return scrollCompact(wheel);
        return false;
    }
    private int lastMouseX, lastMouseY;
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public String getHoveredTooltip(int x, int y) { lastMouseX = x; lastMouseY = y; return ""; }
    @Override public void discardDraft() {
        selected.clear(); PacketTextField.clearActiveFocus(); compactScroll = 0;
        draggingCompactScrollbar = draggingSplit = draggingTimelineBar = draggingPacketBar = false;
        closeDropdowns();
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingSplit && button == 0 && area != null) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, area.width - 24),
                    x - (area.x + 12), 180, 280, 150, 220);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingTimelineBar && button == 0) { timelineScrollbar.applyDrag(x, y); return true; }
        if (draggingPacketBar && button == 0) { packetScrollbar.applyDrag(x, y); return true; }
        if (draggingCompactScrollbar && button == 0) { compactScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        if (draggingSplit) { MainUiLayoutManager.setModernSplitRatio("packet.viewer.details", splitRatio); draggingSplit = false; return true; }
        if (draggingTimelineBar) { draggingTimelineBar = false; timelineScrollbar.endDrag(); return true; }
        if (draggingPacketBar) { draggingPacketBar = false; packetScrollbar.endDrag(); return true; }
        if (draggingCompactScrollbar) { draggingCompactScrollbar = false; compactScrollbar.endDrag(); return true; }
        return false;
    }

    private void syncDropdowns() {
        TimelineFilter nextFilter = TimelineFilter.values()[filterDropdown.selected()];
        Window nextWindow = Window.values()[windowDropdown.selected()];
        int nextColumns = columnsDropdown.selected() + 1;
        TextScale nextScale = TextScale.values()[scaleDropdown.selected()];
        boolean nextHide = hintDropdown.selected() == 1;
        boolean changed = nextFilter != timelineFilter || nextWindow != window || nextColumns != packetColumns
                || nextScale != textScale || nextHide != hideHint;
        timelineFilter = nextFilter;
        window = nextWindow;
        packetColumns = nextColumns;
        textScale = nextScale;
        hideHint = nextHide;
        if (changed) refresh(true);
    }

    private void drawCompactScrollbar(int mouseX, int mouseY) {
        if (compactViewport == null || compactMaxScroll <= 0) {
            compactScrollbar.idle();
            return;
        }
        compactScrollbar.draw(compactViewport, compactScroll, compactMaxScroll, compactViewport.height,
                compactViewport.height + compactMaxScroll, mouseX, mouseY, value -> compactScroll = value);
    }
    private boolean compactLayout() { return compactViewport != null; }
    private boolean scrollCompact(int wheel) { int before = compactScroll; compactScroll = clamp(compactScroll + (wheel > 0 ? -36 : 36), 0, compactMaxScroll); return before != compactScroll; }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String packetText(PacketCaptureHandler.CapturedPacketData p) { return PacketQuerySupport.searchableText(p); }
    private static String normalizeHex(String s) { return PacketQuerySupport.normalizeHex(s); }
    private int timelineListTop() { return leftBounds == null ? 0 : leftBounds.y + 54; }
    private long windowMillis() { switch (window) { case MS50: return 50L; case MS100: return 100L; case MS200: return 200L; case MS500: return 500L; default: return Long.MAX_VALUE; } }
    private static String inline(String s, int max) { String v = safe(s).replace('\n', ' ').replace('\r', ' ').trim(); return v.length() <= max ? v : v.substring(0, Math.max(0, max - 3)) + "..."; }
}
