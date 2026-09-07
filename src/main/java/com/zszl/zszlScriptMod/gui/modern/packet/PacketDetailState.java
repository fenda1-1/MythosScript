package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.PacketPayloadDecoder;

/** Pure selection/view state for packet detail and HEX inspection. */
final class PacketDetailState {
    enum Section { OVERVIEW, CLASS_NAME, CHANNEL_OR_ID, HEX, DECODED, DETAIL, FULL }
    private final PacketCaptureHandler.CapturedPacketData packet;
    private final PacketPayloadDecoder.AnnotatedDecodeReport report;
    private Section section = Section.OVERVIEW;
    private int hexStart = -1;
    private int hexEnd = -1;
    private final List<Integer> selectedChunks = new ArrayList<>();
    private final List<String> textLines = new ArrayList<>();
    private int cursorLine;
    private int cursorColumn;
    private int anchorLine;
    private int anchorColumn;

    PacketDetailState(PacketCaptureHandler.CapturedPacketData packet) {
        this.packet = packet;
        PacketPayloadDecoder.AnnotatedDecodeReport inspected = packet == null ? null
                : PacketPayloadDecoder.inspectAnnotated(packet.rawData == null ? new byte[0] : packet.rawData);
        report = inspected == null ? new PacketPayloadDecoder.AnnotatedDecodeReport("", "",
                Collections.<PacketPayloadDecoder.DecodedChunk>emptyList()) : inspected;
    }
    PacketCaptureHandler.CapturedPacketData packet() { return packet; }
    PacketPayloadDecoder.AnnotatedDecodeReport report() { return report; }
    Section section() { return section; }
    void section(Section value) {
        if (value == null) return;
        section = value;
        resetTextSelection();
    }
    void selectHex(int index, boolean extend) {
        if (packet == null || packet.rawData == null || index < 0 || index >= packet.rawData.length) return;
        if (!extend || hexStart < 0) hexStart = index;
        hexEnd = index;
    }
    void selectAllHex() { if (packet != null && packet.rawData != null && packet.rawData.length > 0) { hexStart = 0; hexEnd = packet.rawData.length - 1; } }
    void moveHex(int delta, boolean extend) {
        if (packet == null || packet.rawData == null || packet.rawData.length == 0) return;
        boolean hadSelection = hasHexSelection();
        int current = hadSelection ? hexEnd : 0;
        int next = Math.max(0, Math.min(packet.rawData.length - 1, current + delta));
        if (!extend) hexStart = next;
        else if (!hadSelection) hexStart = current;
        hexEnd = next;
    }
    void moveHexBoundary(boolean end, boolean extend) {
        if (packet == null || packet.rawData == null || packet.rawData.length == 0) return;
        int next = end ? packet.rawData.length - 1 : 0;
        boolean hadSelection = hasHexSelection();
        if (!extend) hexStart = next;
        else if (!hadSelection) hexStart = 0;
        hexEnd = next;
    }
    boolean hasHexSelection() { return hexStart >= 0 && hexEnd >= 0; }
    int hexStart() { return hexStart; }
    int hexEnd() { return hexEnd; }
    String selectedHex() {
        if (!hasHexSelection() || packet == null || packet.rawData == null || packet.rawData.length == 0) return "";
        int from = Math.max(0, Math.min(hexStart, hexEnd)), to = Math.min(packet.rawData.length - 1, Math.max(hexStart, hexEnd));
        StringBuilder value = new StringBuilder();
        for (int i = from; i <= to; i++) { if (value.length() > 0) value.append(' '); value.append(String.format("%02X", packet.rawData[i] & 0xFF)); }
        return value.toString();
    }
    void toggleChunk(int index) { if (selectedChunks.contains(index)) selectedChunks.remove((Integer) index); else selectedChunks.add(index); }
    List<Integer> selectedChunks() { return Collections.unmodifiableList(selectedChunks); }
    int textLineCount() { ensureTextLines(); return textLines.size(); }
    String textLine(int index) { ensureTextLines(); return index < 0 || index >= textLines.size() ? "" : textLines.get(index); }
    int cursorLine() { return cursorLine; }
    int cursorColumn() { return cursorColumn; }
    int anchorLine() { return anchorLine; }
    int anchorColumn() { return anchorColumn; }
    boolean hasTextSelection() { return cursorLine != anchorLine || cursorColumn != anchorColumn; }
    void placeTextCursor(int line, int column, boolean extend) {
        ensureTextLines();
        if (!extend) { anchorLine = cursorLine; anchorColumn = cursorColumn; }
        cursorLine = clamp(line, 0, textLines.size() - 1);
        cursorColumn = clamp(column, 0, textLines.get(cursorLine).length());
        if (!extend) { anchorLine = cursorLine; anchorColumn = cursorColumn; }
    }
    void selectAllText() {
        ensureTextLines();
        anchorLine = 0; anchorColumn = 0;
        cursorLine = textLines.size() - 1; cursorColumn = textLines.get(cursorLine).length();
    }
    void moveTextLeft(boolean extend) { ensureTextLines(); if (cursorColumn > 0) cursorColumn--; else if (cursorLine > 0) { cursorLine--; cursorColumn = textLines.get(cursorLine).length(); } finishTextMove(extend); }
    void moveTextRight(boolean extend) { ensureTextLines(); if (cursorColumn < textLines.get(cursorLine).length()) cursorColumn++; else if (cursorLine + 1 < textLines.size()) { cursorLine++; cursorColumn = 0; } finishTextMove(extend); }
    void moveTextUp(boolean extend) { ensureTextLines(); if (cursorLine > 0) cursorLine--; cursorColumn = Math.min(cursorColumn, textLines.get(cursorLine).length()); finishTextMove(extend); }
    void moveTextDown(boolean extend) { ensureTextLines(); if (cursorLine + 1 < textLines.size()) cursorLine++; cursorColumn = Math.min(cursorColumn, textLines.get(cursorLine).length()); finishTextMove(extend); }
    void moveTextBoundary(boolean start, boolean extend) { ensureTextLines(); cursorColumn = start ? 0 : textLines.get(cursorLine).length(); finishTextMove(extend); }
    void moveTextPage(int delta, int visibleLines, boolean extend) { ensureTextLines(); cursorLine = clamp(cursorLine + delta * Math.max(1, visibleLines), 0, textLines.size() - 1); cursorColumn = Math.min(cursorColumn, textLines.get(cursorLine).length()); finishTextMove(extend); }
    String selectedText() {
        ensureTextLines();
        if (!hasTextSelection()) return "";
        int sl = anchorLine, sc = anchorColumn, el = cursorLine, ec = cursorColumn;
        if (sl > el || (sl == el && sc > ec)) { sl = cursorLine; sc = cursorColumn; el = anchorLine; ec = anchorColumn; }
        StringBuilder out = new StringBuilder();
        for (int i = sl; i <= el; i++) {
            String line = textLines.get(i); int from = i == sl ? clamp(sc, 0, line.length()) : 0; int to = i == el ? clamp(ec, 0, line.length()) : line.length();
            if (to > from) out.append(line.substring(from, to));
            if (i < el) out.append('\n');
        }
        return out.toString();
    }
    String content() {
        if (packet == null) return "";
        switch (section) {
            case CLASS_NAME: return packet.packetClassName == null ? "" : packet.packetClassName;
            case CHANNEL_OR_ID: return packet.isFmlPacket ? (packet.channel == null ? "N/A" : packet.channel)
                    : packet.packetId == null ? "N/A" : String.format("0x%02X (%d)", packet.packetId, packet.packetId);
            case HEX: return hexDump(packet.rawData);
            case DECODED: return packet.getDecodedData();
            case DETAIL: return packet.getDecodedDetailData();
            case FULL: return packet.getDecodedFullData();
            default: return "class=" + safe(packet.packetClassName) + "\nchannel=" + safe(packet.channel) + "\nid=" + (packet.packetId == null ? "" : packet.packetId)
                    + "\noccurrences=" + packet.getOccurrenceCount() + "\nbytes=" + packet.getPayloadSize();
        }
    }
    String selectedChunkText() {
        StringBuilder value = new StringBuilder();
        for (Integer i : selectedChunks) if (i != null && i >= 0 && i < report.chunks.size()) {
            PacketPayloadDecoder.DecodedChunk c = report.chunks.get(i); if (value.length() > 0) value.append("\n\n"); value.append(c.text == null ? "" : c.text);
        }
        return value.toString();
    }

    boolean hasSelectedChunks() { return !selectedChunks.isEmpty(); }
    String allChunkText() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < report.chunks.size(); i++) {
            PacketPayloadDecoder.DecodedChunk c = report.chunks.get(i);
            if (c == null) continue;
            if (value.length() > 0) value.append("\n\n");
            value.append('#').append(i + 1).append(" [").append(c.startOffset).append('-').append(c.endOffset).append("] ")
                    .append(c.label == null ? "" : c.label).append('\n').append(c.text == null ? "" : c.text);
        }
        return value.toString().trim();
    }
    private void resetTextSelection() {
        textLines.clear();
        String value = content();
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0) textLines.add(""); else for (String line : lines) textLines.add(line == null ? "" : line);
        cursorLine = cursorColumn = anchorLine = anchorColumn = 0;
    }
    private void ensureTextLines() { if (textLines.isEmpty()) resetTextSelection(); }
    private void finishTextMove(boolean extend) {
        cursorLine = clamp(cursorLine, 0, textLines.size() - 1); cursorColumn = clamp(cursorColumn, 0, textLines.get(cursorLine).length());
        if (!extend) { anchorLine = cursorLine; anchorColumn = cursorColumn; }
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String hexDump(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder b = new StringBuilder();
        for (int off = 0; off < data.length; off += 16) {
            int end = Math.min(data.length, off + 16); b.append(String.format("%04X  ", off));
            for (int i = off; i < off + 16; i++) b.append(i < end ? String.format("%02X ", data[i] & 0xFF) : "   ");
            b.append(" |"); for (int i = off; i < end; i++) { int v = data[i] & 0xFF; b.append(v >= 32 && v <= 126 ? (char) v : '.'); }
            b.append('|'); if (end < data.length) b.append('\n');
        }
        return b.toString();
    }
}
