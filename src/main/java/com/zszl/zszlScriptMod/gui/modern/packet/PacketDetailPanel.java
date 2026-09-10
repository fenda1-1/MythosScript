package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.PacketPayloadDecoder;

import net.minecraft.client.gui.FontRenderer;

/** Packet metadata, HEX, decoded text and selectable decode chunks. */
final class PacketDetailPanel extends PacketPanelBase {
    private final PacketDetailState state;
    private final int packetIndex;
    private final String direction;
    private final List<ModernMainLayout.Rect> tabs = new ArrayList<>();
    private ModernMainLayout.Rect copySelectionBounds, copyFullBounds, copyChunksBounds, contentBounds;
    private ModernMainLayout.Rect textBounds, chunkBounds;
    private boolean draggingHex, draggingText, draggingHexBar, draggingTextBar, draggingChunkBar;
    private int textScroll, textMaxScroll, chunkScroll, hexScroll, hexMaxScroll;
    private int lastMouseX, lastMouseY;
    private final ModernHoverScrollbar hexScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar textScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar chunkScrollbar = new ModernHoverScrollbar();

    PacketDetailPanel(PacketWorkbenchTab owner, List<PacketCaptureHandler.CapturedPacketData> packets, int index) {
        this(owner, packets == null || index < 0 || index >= packets.size() ? null : packets.get(index), index, "");
    }
    PacketDetailPanel(PacketWorkbenchTab owner, PacketCaptureHandler.CapturedPacketData packet, int index, String direction) {
        super(owner, "gui.modern.pktdet.u001");
        state = new PacketDetailState(packet); packetIndex = index; this.direction = direction == null ? "" : direction;
    }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mouseX, int mouseY) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        int tabY = area.y + 42, gap = 4;
        tabs.clear();
        String[] labels = { "gui.modern.pktdet.u002", "gui.modern.pktdet.u003", "gui.modern.pktdet.u004", "HEX", "gui.modern.pktdet.u005", "gui.modern.pktdet.u006", "Full" };
        int columns = area.width >= 430 ? labels.length : Math.max(2, Math.min(labels.length, (area.width - 24 + gap) / 64));
        int tabWidth = Math.max(34, (area.width - 24 - gap * Math.max(0, columns - 1)) / columns);
        for (int i = 0; i < labels.length; i++) {
            int row = i / columns, column = i % columns;
            ModernMainLayout.Rect r = new ModernMainLayout.Rect(area.x + 12 + column * (tabWidth + gap), tabY + row * 24, tabWidth, 20);
            tabs.add(r);
            boolean selected = i == state.section().ordinal(), hover = r.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3,
                    selected ? ModernUiRenderer.ACCENT : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, labels[i], r.x + 6, r.y + 6, selected ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, r.width - 12);
        }
        int footerY = area.bottom() - 28;
        int buttonGap = 5, buttonWidth = Math.max(54, (area.width - 24 - buttonGap * 2) / 3);
        copySelectionBounds = new ModernMainLayout.Rect(area.x + 12, footerY, buttonWidth, 22);
        copyFullBounds = new ModernMainLayout.Rect(copySelectionBounds.right() + buttonGap, footerY, buttonWidth, 22);
        copyChunksBounds = new ModernMainLayout.Rect(copyFullBounds.right() + buttonGap, footerY,
                Math.max(1, area.right() - copyFullBounds.right() - buttonGap - 12), 22);
        drawButton(font, copySelectionBounds, "gui.modern.pktdet.u007", mouseX, mouseY);
        drawButton(font, copyFullBounds, "gui.modern.pktdet.u008", mouseX, mouseY);
        drawButton(font, copyChunksBounds, "gui.modern.pktdet.u009", mouseX, mouseY);
        int tabRows = (labels.length + columns - 1) / columns;
        contentBounds = new ModernMainLayout.Rect(area.x + 12, tabY + tabRows * 24 + 4, Math.max(1, area.width - 24),
                Math.max(24, footerY - (tabY + tabRows * 24 + 12)));
        ModernUiRenderer.drawSubtlePanel(contentBounds.x, contentBounds.y, contentBounds.width, contentBounds.height, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawContent(font, mouseX, mouseY);
    }

    private void drawContent(FontRenderer font, int mouseX, int mouseY) {
        PacketCaptureHandler.CapturedPacketData packet = state.packet();
        if (packet == null) {
            text(font, "gui.modern.pktdet.u010", contentBounds.x + 12, contentBounds.y + 14, ModernUiRenderer.MUTED_TEXT, contentBounds.width - 24);
            hexScrollbar.idle(); textScrollbar.idle(); chunkScrollbar.idle();
            return;
        }
        if (state.section() == PacketDetailState.Section.HEX) { drawHex(font); return; }
        String value = state.content();
        boolean withChunks = state.section() == PacketDetailState.Section.DECODED || state.section() == PacketDetailState.Section.DETAIL;
        if (withChunks) {
            int split = Math.max(42, contentBounds.height * 3 / 5);
            textBounds = new ModernMainLayout.Rect(contentBounds.x + 2, contentBounds.y + 2,
                    contentBounds.width - 4, Math.max(24, split));
            chunkBounds = new ModernMainLayout.Rect(contentBounds.x + 2, textBounds.bottom() + 4,
                    contentBounds.width - 4, Math.max(22, contentBounds.bottom() - textBounds.bottom() - 6));
            drawTextLines(font, value, textBounds, mouseX, mouseY);
            drawChunks(font, mouseX, mouseY);
            hexScrollbar.idle();
        } else {
            textBounds = contentBounds; chunkBounds = null;
            drawTextLines(font, value, textBounds, mouseX, mouseY);
            chunkScrollbar.idle();
            hexScrollbar.idle();
        }
    }

    private void drawHex(FontRenderer font) {
        byte[] data = state.packet().rawData == null ? new byte[0] : state.packet().rawData;
        int columns = hexColumns();
        int visibleRows = Math.max(1, (contentBounds.height - 40) / 16);
        int totalRows = (data.length + columns - 1) / columns;
        hexMaxScroll = Math.max(0, totalRows - visibleRows);
        hexScroll = clamp(hexScroll, 0, hexMaxScroll);
        int y = contentBounds.y + 12, rowHeight = 16;
        int firstOffset = hexScroll * columns;
        for (int off = firstOffset, row = 0; off < data.length && row < visibleRows; off += columns, row++, y += rowHeight) {
            int end = Math.min(data.length, off + columns);
            text(font, String.format("%04X", off), contentBounds.x + 10, y, ModernUiRenderer.MUTED_TEXT, 42);
            for (int i = off; i < end; i++) {
                int x = contentBounds.x + (columns == 8 ? 44 : 54) + (i - off) * 22;
                boolean selected = state.hasHexSelection() && i >= Math.min(state.hexStart(), state.hexEnd()) && i <= Math.max(state.hexStart(), state.hexEnd());
                if (selected) ModernUiRenderer.drawSubtlePanel(x - 2, y - 2, 20, 16, 2, ModernUiRenderer.ACCENT_DIM, ModernUiRenderer.ACCENT);
                text(font, String.format("%02X", data[i] & 0xFF), x, y, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, 18);
            }
        }
        text(font, hexMaxScroll > 0 ? tr("gui.modern.pktdet.fmt.hex_scroll", tr("gui.modern.pktdet.u011")) : "gui.modern.pktdet.u011", contentBounds.x + 10,
                contentBounds.bottom() - 16, ModernUiRenderer.MUTED_TEXT, contentBounds.width - 20);
        if (hexMaxScroll > 0) {
            hexScrollbar.draw(contentBounds, hexScroll, hexMaxScroll, visibleRows, Math.max(totalRows, visibleRows),
                    lastMouseX, lastMouseY, value -> hexScroll = value);
        } else {
            hexScrollbar.idle();
        }
        textScrollbar.idle();
        chunkScrollbar.idle();
    }

    private void drawTextLines(FontRenderer font, String value, ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        int visible = Math.max(1, (bounds.height - 8) / 15);
        textMaxScroll = Math.max(0, state.textLineCount() - visible);
        textScroll = clamp(textScroll, 0, textMaxScroll);
        ModernUiRenderer.beginClip(ModernHoverScrollbar.contentBounds(bounds));
        for (int i = textScroll; i < state.textLineCount() && i < textScroll + visible; i++) {
            int y = bounds.y + 4 + (i - textScroll) * 15;
            String line = state.textLine(i);
            int from = selectionStartForLine(i), to = selectionEndForLine(i);
            if (to > from) {
                int left = bounds.x + 10 + (font == null ? 0 : font.getStringWidth(line.substring(0, from)));
                int width = font == null ? 8 * (to - from) : Math.max(2, font.getStringWidth(line.substring(from, to)));
                ModernUiRenderer.drawRoundedRect(left, y - 2, width, 14, 2, ModernUiRenderer.ACCENT_DIM);
            }
            text(font, line, bounds.x + 10, y, ModernUiRenderer.SUBTLE_TEXT, ModernHoverScrollbar.contentWidth(bounds.width - 10));
        }
        ModernUiRenderer.endClip();
        if (textMaxScroll > 0) {
            textScrollbar.draw(bounds, textScroll, textMaxScroll, visible, state.textLineCount(), mouseX, mouseY,
                    next -> textScroll = next);
        } else {
            textScrollbar.idle();
        }
    }

    private void drawChunks(FontRenderer font, int mouseX, int mouseY) {
        if (chunkBounds == null) return;
        int rows = Math.max(1, (chunkBounds.height - 6) / 34);
        chunkScroll = clamp(chunkScroll, 0, Math.max(0, state.report().chunks.size() - rows));
        ModernUiRenderer.beginClip(chunkBounds);
        for (int i = chunkScroll; i < state.report().chunks.size() && i < chunkScroll + rows; i++) {
            PacketPayloadDecoder.DecodedChunk chunk = state.report().chunks.get(i);
            int y = chunkBounds.y + 3 + (i - chunkScroll) * 34;
            ModernMainLayout.Rect r = new ModernMainLayout.Rect(chunkBounds.x + 6, y, ModernHoverScrollbar.contentWidth(chunkBounds.width - 6), 30);
            boolean selected = state.selectedChunks().contains(i), hover = r.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 3,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, "#" + (i + 1) + " [" + chunk.startOffset + "-" + chunk.endOffset + "] " + safe(chunk.label), r.x + 8, y + 5, ModernUiRenderer.TEXT, r.width - 16);
            text(font, inline(chunk.text, 110), r.x + 8, y + 18, ModernUiRenderer.SUBTLE_TEXT, r.width - 16);
        }
        ModernUiRenderer.endClip();
        int chunkMax = Math.max(0, state.report().chunks.size() - rows);
        if (chunkMax > 0) {
            chunkScrollbar.draw(chunkBounds, chunkScroll, chunkMax, rows, state.report().chunks.size(), mouseX, mouseY,
                    value -> chunkScroll = value);
        } else {
            chunkScrollbar.idle();
        }
    }

    private int selectionStartForLine(int line) {
        if (!state.hasTextSelection()) return -1;
        int start = Math.min(state.anchorLine(), state.cursorLine()), end = Math.max(state.anchorLine(), state.cursorLine());
        if (line < start || line > end) return -1;
        if (line == state.anchorLine() && line == state.cursorLine()) return Math.min(state.anchorColumn(), state.cursorColumn());
        if (line == start) return state.anchorLine() == start ? state.anchorColumn() : state.cursorColumn();
        return 0;
    }
    private int selectionEndForLine(int line) {
        if (!state.hasTextSelection()) return -1;
        int start = Math.min(state.anchorLine(), state.cursorLine()), end = Math.max(state.anchorLine(), state.cursorLine());
        if (line < start || line > end) return -1;
        if (line == state.anchorLine() && line == state.cursorLine()) return Math.max(state.anchorColumn(), state.cursorColumn());
        if (line == end) return state.cursorLine() == end ? state.cursorColumn() : state.anchorColumn();
        return state.textLine(line).length();
    }

    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button != 0) return true;
        if (hexScrollbar.beginDrag(x, y)) { draggingHexBar = true; return true; }
        if (textScrollbar.beginDrag(x, y)) { draggingTextBar = true; return true; }
        if (chunkScrollbar.beginDrag(x, y)) { draggingChunkBar = true; return true; }
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).contains(x, y)) { state.section(PacketDetailState.Section.values()[i]); textScroll = chunkScroll = hexScroll = 0; return true; }
        if (hit(copySelectionBounds, x, y)) { copySelection(); return true; }
        if (hit(copyFullBounds, x, y)) { copyText(state.content(), "gui.modern.pktdet.u012"); return true; }
        if (hit(copyChunksBounds, x, y)) {
            String chunks = state.hasSelectedChunks() ? state.selectedChunkText() : state.allChunkText();
            copyText(chunks, state.hasSelectedChunks() ? "gui.modern.pktdet.u013" : "gui.modern.pktdet.u014");
            return true;
        }
        if (state.section() == PacketDetailState.Section.HEX) return clickHex(x, y);
        if (chunkBounds != null && chunkBounds.contains(x, y)) { int i = (y - chunkBounds.y - 3) / 34 + chunkScroll; if (i >= 0 && i < state.report().chunks.size()) state.toggleChunk(i); return true; }
        if (textBounds != null && textBounds.contains(x, y)) { placeTextCursor(x, y, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)); draggingText = true; return true; }
        return true;
    }

    private boolean clickHex(int x, int y) {
        int columns = hexColumns();
        int xOffset = columns == 8 ? 44 : 54;
        int row = (y - contentBounds.y - 12) / 16 + hexScroll, col = (x - contentBounds.x - xOffset) / 22;
        if (row >= 0 && col >= 0 && col < columns) { state.selectHex(row * columns + col, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)); draggingHex = true; }
        return true;
    }
    private void placeTextCursor(int x, int y, boolean extend) {
        int line = clamp(textScroll + Math.max(0, (y - textBounds.y - 4) / 15), 0, Math.max(0, state.textLineCount() - 1));
        String value = state.textLine(line); int relative = Math.max(0, x - textBounds.x - 10), column = 0;
        if (font != null) for (int i = 1; i <= value.length(); i++) { if (font.getStringWidth(value.substring(0, i)) > relative) break; column = i; }
        state.placeTextCursor(line, column, extend); ensureTextCursorVisible();
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (button != 0) return draggingHex || draggingText || draggingHexBar || draggingTextBar || draggingChunkBar;
        if (draggingHexBar) { hexScrollbar.applyDrag(x, y); return true; }
        if (draggingTextBar) { textScrollbar.applyDrag(x, y); return true; }
        if (draggingChunkBar) { chunkScrollbar.applyDrag(x, y); return true; }
        if (draggingHex && contentBounds != null) { int columns = hexColumns(); int xOffset = columns == 8 ? 44 : 54; int row = (y - contentBounds.y - 12) / 16 + hexScroll, col = (x - contentBounds.x - xOffset) / 22; if (row >= 0 && col >= 0 && col < columns) state.selectHex(row * columns + col, true); return true; }
        if (draggingText && textBounds != null) { placeTextCursor(x, y, true); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        boolean consumed = draggingHex || draggingText || draggingHexBar || draggingTextBar || draggingChunkBar;
        if (draggingHexBar) hexScrollbar.endDrag();
        if (draggingTextBar) textScrollbar.endDrag();
        if (draggingChunkBar) chunkScrollbar.endDrag();
        draggingHex = draggingText = draggingHexBar = draggingTextBar = draggingChunkBar = false;
        return consumed;
    }

    private void copySelection() { String value = state.section() == PacketDetailState.Section.HEX && state.hasHexSelection() ? state.selectedHex() : state.selectedText(); if (value.isEmpty()) value = state.content(); copyText(value, "gui.modern.pktdet.u015"); }
    private void copyText(String value, String message) { if (value != null && !value.isEmpty()) { PacketClipboard.copyOrExport(owner.minecraft(), value); owner.status(message); } }

    @Override public boolean keyTyped(char c, int code) {
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (ctrl && code == Keyboard.KEY_A) { if (state.section() == PacketDetailState.Section.HEX) state.selectAllHex(); else state.selectAllText(); ensureTextCursorVisible(); return true; }
        if (ctrl && code == Keyboard.KEY_C) { copySelection(); return true; }
        if (state.section() == PacketDetailState.Section.HEX) {
            if (code == Keyboard.KEY_LEFT) { state.moveHex(-1, shift); ensureHexVisible(); return true; } if (code == Keyboard.KEY_RIGHT) { state.moveHex(1, shift); ensureHexVisible(); return true; }
            if (code == Keyboard.KEY_HOME) { state.moveHexBoundary(false, shift); ensureHexVisible(); return true; } if (code == Keyboard.KEY_END) { state.moveHexBoundary(true, shift); ensureHexVisible(); return true; }
            if (code == Keyboard.KEY_PRIOR) { state.moveHex(-hexColumns(), shift); ensureHexVisible(); return true; } if (code == Keyboard.KEY_NEXT) { state.moveHex(hexColumns(), shift); ensureHexVisible(); return true; }
        } else {
            if (code == Keyboard.KEY_LEFT) { state.moveTextLeft(shift); ensureTextCursorVisible(); return true; } if (code == Keyboard.KEY_RIGHT) { state.moveTextRight(shift); ensureTextCursorVisible(); return true; }
            if (code == Keyboard.KEY_UP) { state.moveTextUp(shift); ensureTextCursorVisible(); return true; } if (code == Keyboard.KEY_DOWN) { state.moveTextDown(shift); ensureTextCursorVisible(); return true; }
            if (code == Keyboard.KEY_HOME) { state.moveTextBoundary(true, shift); ensureTextCursorVisible(); return true; } if (code == Keyboard.KEY_END) { state.moveTextBoundary(false, shift); ensureTextCursorVisible(); return true; }
            if (code == Keyboard.KEY_PRIOR) { state.moveTextPage(-1, visibleTextLines(), shift); ensureTextCursorVisible(); return true; } if (code == Keyboard.KEY_NEXT) { state.moveTextPage(1, visibleTextLines(), shift); ensureTextCursorVisible(); return true; }
        }
        return false;
    }
    private int visibleTextLines() { return textBounds == null ? 1 : Math.max(1, (textBounds.height - 8) / 15); }
    private void ensureTextCursorVisible() { if (textBounds == null) return; int visible = visibleTextLines(); textScroll = clamp(textScroll, 0, Math.max(0, state.textLineCount() - visible)); if (state.cursorLine() < textScroll) textScroll = state.cursorLine(); else if (state.cursorLine() >= textScroll + visible) textScroll = state.cursorLine() - visible + 1; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0) return false;
        if (state.section() == PacketDetailState.Section.HEX) {
            if (contentBounds != null && contentBounds.contains(lastMouseX, lastMouseY)) {
                hexScroll = clamp(hexScroll + (wheel > 0 ? -1 : 1), 0, hexMaxScroll);
                return true;
            }
            return false;
        }
        if (textBounds != null && textBounds.contains(lastMouseX, lastMouseY)) { textScroll = clamp(textScroll + (wheel > 0 ? -1 : 1), 0, textMaxScroll); return true; }
        if (chunkBounds != null && chunkBounds.contains(lastMouseX, lastMouseY)) { int rows = Math.max(1, (chunkBounds.height - 6) / 34); chunkScroll = clamp(chunkScroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, state.report().chunks.size() - rows)); return true; }
        return false;
    }
    @Override public void discardDraft() { PacketTextField.clearActiveFocus(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String s) { return s == null ? "" : s; }
    private void ensureHexVisible() {
        if (contentBounds == null || state.packet() == null || state.packet().rawData == null || !state.hasHexSelection()) return;
        int visible = Math.max(1, (contentBounds.height - 40) / 16);
        int row = Math.max(state.hexStart(), state.hexEnd()) / hexColumns();
        hexScroll = clamp(row, 0, Math.max(0, hexMaxScroll));
    }
    private int hexColumns() { return contentBounds != null && contentBounds.width < 420 ? 8 : 16; }
    private static String inline(String s, int max) { String v = safe(s).replace('\n', ' ').replace('\r', ' ').trim(); return v.length() <= max ? v : v.substring(0, Math.max(0, max - 3)) + "..."; }
}
