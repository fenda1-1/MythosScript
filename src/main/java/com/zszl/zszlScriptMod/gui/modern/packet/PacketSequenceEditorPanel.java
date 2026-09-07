package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;

/** Sequence editor with packet add/delete/reorder, direction, delay and send. */
final class PacketSequenceEditorPanel extends PacketPanelBase {
    private static final int DEFAULT_DELAY_TICKS = 20;
    private final PacketWorkbenchState.Sequence state;
    private final PacketTextField packetId = new PacketTextField(7801, 128), hex = new PacketTextField(7802, 32767), delay = new PacketTextField(7803, 8);
    private final PacketTextField name = new PacketTextField(7804, 128);
    private final PacketDropdown directionDrop = new PacketDropdown("C2S", "S2C");
    private String direction = "C2S";
    private int selected = -1;
    private boolean saved;
    private String validationError = "";
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar mobileScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect mobileViewport;
    private int mobileScroll, mobileMaxScroll;
    private int listScroll;
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect nameBounds, idBounds, hexBounds, delayBounds, directionBounds, addIdBounds, addChannelBounds, applyBounds, clearAllBounds, listBounds, saveBounds, sendBounds, cancelBounds, selectorBounds;

    PacketSequenceEditorPanel(PacketWorkbenchTab owner, String sequenceName, List<PacketCaptureHandler.CapturedPacketData> initial) {
        this(owner, sequenceName, initial, "C2S");
    }
    PacketSequenceEditorPanel(PacketWorkbenchTab owner, String sequenceName, List<PacketCaptureHandler.CapturedPacketData> initial, String initialDirection) {
        super(owner, "gui.modern.pktseqe.u001");
        PacketSequence loaded = sequenceName == null ? null : PacketSequenceManager.loadSequence(sequenceName);
        state = new PacketWorkbenchState.Sequence(loaded, sequenceName == null ? "new_sequence" : sequenceName);
        direction = PacketSendSupport.normalizeDirection(initialDirection);
        if (initial != null) for (PacketCaptureHandler.CapturedPacketData p : initial) if (p != null) { if (p.isFmlPacket) state.addChannel(p.channel, p.getHexData(), DEFAULT_DELAY_TICKS); else state.addStandard(p.packetId == null ? 0 : p.packetId, p.getHexData(), DEFAULT_DELAY_TICKS); state.value().packets.get(state.value().packets.size() - 1).direction = direction; }
    }
    @Override protected void initializePanel() {
        name.ensure(font); packetId.ensure(font); hex.ensure(font); delay.ensure(font);
        registerDropdown(directionDrop);
        directionDrop.setValue(direction);
        name.setText(state.value().name); delay.setText(String.valueOf(DEFAULT_DELAY_TICKS));
    }
    @Override public void updateScreen() { name.update(); packetId.update(); hex.update(); delay.update(); }
    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        if (area.width < 560 || area.height < 286) {
            drawCompactBody(font, area, mx, my);
            return;
        }
        mobileViewport = null;
        int x = area.x + 12, y = area.y + 56, footer = area.bottom() - 30;
        nameBounds = new ModernMainLayout.Rect(x, y, Math.max(130, Math.min(260, area.width / 3)), 20); labeledField(name, nameBounds, "gui.modern.pktseqe.name", "gui.modern.pktseqe.name_hint");
        direction = directionDrop.value();
        directionBounds = new ModernMainLayout.Rect(nameBounds.right() + 8, y, Math.min(100, Math.max(1, area.right() - nameBounds.right() - 8)), 20); directionDrop.drawButton(font, directionBounds, mx, my); selectorBounds = new ModernMainLayout.Rect(directionBounds.right() + 8, y, Math.max(1, area.right() - directionBounds.right() - 20), 20); drawButton(font, selectorBounds, "gui.modern.pktseqe.u002", mx, my);
        text(font, "gui.modern.pktseqe.direction", directionBounds.x, y - 14, ModernUiRenderer.MUTED_TEXT, directionBounds.width);
        // Reserve the labeled fields, edit actions and footer before sizing the packet list.
        listBounds = new ModernMainLayout.Rect(x, y + 28, Math.max(1, area.width - 24), footer - 82 - (y + 28)); ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int rowY = listBounds.y + 8; int visibleRows = Math.max(1, (listBounds.height - 16) / 42); listScroll = clamp(listScroll, 0, Math.max(0, state.value().packets.size() - visibleRows));
        for (int i = listScroll; i < state.value().packets.size() && i < listScroll + visibleRows; i++, rowY += 42) drawPacket(font, state.value().packets.get(i), i, rowY, mx, my);
        if (state.value().packets.isEmpty()) text(font, "gui.modern.pktseqe.u003", listBounds.x + 12, listBounds.y + 42, ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        int maxListScroll = Math.max(0, state.value().packets.size() - visibleRows);
        if (maxListScroll > 0) {
            listScrollbar.draw(listBounds, listScroll, maxListScroll, visibleRows, state.value().packets.size(), mx, my,
                    value -> listScroll = value);
        } else {
            listScrollbar.idle();
        }
        mobileScrollbar.idle();
        int inputY = listBounds.bottom() + 22, fw = (area.width - 36) / 3; idBounds = new ModernMainLayout.Rect(x, inputY, fw, 20); hexBounds = new ModernMainLayout.Rect(idBounds.right() + 6, inputY, fw, 20); delayBounds = new ModernMainLayout.Rect(hexBounds.right() + 6, inputY, area.right() - hexBounds.right() - 18, 20);
        drawPacketFields();
        addIdBounds = new ModernMainLayout.Rect(x, inputY + 26, 100, 22); addChannelBounds = new ModernMainLayout.Rect(x + 106, inputY + 26, 110, 22); applyBounds = new ModernMainLayout.Rect(x + 222, inputY + 26, 100, 22); clearAllBounds = new ModernMainLayout.Rect(x + 328, inputY + 26, 90, 22); drawButton(font, addIdBounds, "gui.modern.pktseqe.u004", mx, my); drawButton(font, addChannelBounds, "gui.modern.pktseqe.u005", mx, my); drawButton(font, applyBounds, "gui.modern.pktseqe.u006", mx, my, selected >= 0, false); drawButton(font, clearAllBounds, "gui.modern.pktseqe.u007", mx, my, !state.value().packets.isEmpty(), false);
        int gap = 6, half = Math.max(70, (area.width - 24 - gap * 2) / 3); saveBounds = new ModernMainLayout.Rect(x, footer, half, 22); sendBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, footer, half, 22); cancelBounds = new ModernMainLayout.Rect(sendBounds.right() + gap, footer, area.right() - sendBounds.right() - gap - 12, 22); drawButton(font, saveBounds, "gui.modern.pktseqe.u008", mx, my, true, true); drawButton(font, sendBounds, "gui.modern.pktseqe.u009", mx, my, !state.value().packets.isEmpty(), false); drawButton(font, cancelBounds, "gui.modern.pktseqe.u010", mx, my);
    }

    private void drawCompactBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        int x = area.x + 12;
        int width = Math.max(1, area.width - 24);
        int footer = area.bottom() - 30;
        int top = area.y + 42;
        int listHeight = 84;
        int contentHeight = 398;
        mobileViewport = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        mobileMaxScroll = Math.max(0, contentHeight - mobileViewport.height);
        mobileScroll = clamp(mobileScroll, 0, mobileMaxScroll);
        int y = top - mobileScroll + 14;
        ModernUiRenderer.beginClip(mobileViewport);

        nameBounds = new ModernMainLayout.Rect(x, y, width, 20);
        labeledField(name, nameBounds, "gui.modern.pktseqe.name", "gui.modern.pktseqe.name_hint");
        int half = Math.max(1, (width - 6) / 2);
        directionBounds = new ModernMainLayout.Rect(x, y + 26, half, 20);
        selectorBounds = new ModernMainLayout.Rect(directionBounds.right() + 6, y + 26,
                Math.max(1, x + width - directionBounds.right() - 6), 20);
        direction = directionDrop.value();
        directionDrop.drawButton(font, directionBounds, mx, my);
        drawButton(font, selectorBounds, "gui.modern.pktseqe.u002", mx, my);

        listBounds = new ModernMainLayout.Rect(x, y + 54, width, listHeight);
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int visibleRows = Math.max(1, (listBounds.height - 16) / 42);
        listScroll = clamp(listScroll, 0, Math.max(0, state.value().packets.size() - visibleRows));
        int rowY = listBounds.y + 8;
        for (int i = listScroll; i < state.value().packets.size() && i < listScroll + visibleRows; i++, rowY += 42) {
            drawPacket(font, state.value().packets.get(i), i, rowY, mx, my);
        }
        if (state.value().packets.isEmpty()) {
            text(font, "gui.modern.pktseqe.u003", listBounds.x + 12, listBounds.y + 34,
                    ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        }

        y = listBounds.bottom() + 22;
        idBounds = new ModernMainLayout.Rect(x, y, width, 20);
        y += 42;
        hexBounds = new ModernMainLayout.Rect(x, y, width, 20);
        y += 42;
        delayBounds = new ModernMainLayout.Rect(x, y, width, 20);
        drawPacketFields();
        y += 28;

        int actionWidth = Math.max(1, (width - 6) / 2);
        addIdBounds = new ModernMainLayout.Rect(x, y, actionWidth, 22);
        addChannelBounds = new ModernMainLayout.Rect(addIdBounds.right() + 6, y, actionWidth, 22);
        drawButton(font, addIdBounds, "gui.modern.pktseqe.u004", mx, my);
        drawButton(font, addChannelBounds, "gui.modern.pktseqe.u005", mx, my);
        y += 28;
        applyBounds = new ModernMainLayout.Rect(x, y, actionWidth, 22);
        clearAllBounds = new ModernMainLayout.Rect(applyBounds.right() + 6, y, actionWidth, 22);
        drawButton(font, applyBounds, "gui.modern.pktseqe.u006", mx, my, selected >= 0, false);
        drawButton(font, clearAllBounds, "gui.modern.pktseqe.u007", mx, my, !state.value().packets.isEmpty(), false);

        int gap = 6;
        saveBounds = new ModernMainLayout.Rect(x, y + 30, actionWidth, 22);
        sendBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, saveBounds.y, actionWidth, 22);
        cancelBounds = new ModernMainLayout.Rect(x, y + 58, width, 22);
        drawButton(font, saveBounds, "gui.modern.pktseqe.u008", mx, my, true, true);
        drawButton(font, sendBounds, "gui.modern.pktseqe.u009", mx, my, !state.value().packets.isEmpty(), false);
        drawButton(font, cancelBounds, "gui.modern.pktseqe.u010", mx, my);

        ModernUiRenderer.endClip();
        drawMobileScrollbar(mx, my);
    }
    private void drawPacketFields() {
        labeledField(packetId, idBounds, "gui.modern.pktseqe.u011", "gui.modern.pktseqe.id_hint");
        labeledField(hex, hexBounds, "gui.modern.pktseqe.u012", "gui.modern.pktseqe.hex_hint");
        labeledField(delay, delayBounds, "gui.modern.pktseqe.delay_label", "gui.modern.pktseqe.delay_hint");
    }

    private void labeledField(PacketTextField input, ModernMainLayout.Rect bounds, String label, String hint) {
        text(font, label, bounds.x, bounds.y - 14, ModernUiRenderer.SUBTLE_TEXT, bounds.width);
        field(input, bounds, null);
        if (input.text().isEmpty() && !input.focused()) {
            text(font, hint, bounds.x + 4, bounds.y + 5, ModernUiRenderer.MUTED_TEXT, bounds.width - 8);
        }
    }
    private void drawPacket(FontRenderer font, PacketSequence.PacketToSend p, int i, int y, int mx, int my) { boolean hover = listBounds.contains(mx, my) && my >= y && my < y + 38; ModernUiRenderer.drawSubtlePanel(listBounds.x + 7, y, ModernHoverScrollbar.contentWidth(listBounds.width - 7), 38, 3, i == selected ? 0xFF2C3D49 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, i == selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); String id = p.isFmlPacket ? "FML " + safe(p.channel) : "ID " + (p.packetId == null ? "?" : String.format("0x%02X", p.packetId)); text(font, (i + 1) + ". " + id + "  " + PacketSendSupport.normalizeDirection(p.direction) + "  delay=" + p.delayTicks, listBounds.x + 13, y + 6, ModernUiRenderer.TEXT, listBounds.width - 120); text(font, inline(p.hexData, 82), listBounds.x + 13, y + 22, ModernUiRenderer.MUTED_TEXT, listBounds.width - 120); text(font, "↑ ↓ ×", listBounds.right() - ModernHoverScrollbar.GUTTER - 54, y + 13, ModernUiRenderer.SUBTLE_TEXT, 48); }
    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button != 0) return true;
        if (mobileViewport != null && !mobileViewport.contains(x, y)) return true;
        if (mobileScrollbar.beginDrag(x, y) || listScrollbar.beginDrag(x, y)) {
            return true;
        }
        if (fieldClick(name, x, y, button) || fieldClick(packetId, x, y, button) || fieldClick(hex, x, y, button) || fieldClick(delay, x, y, button)) return true;
        if (hit(selectorBounds, x, y)) { syncSelectedPacket(); owner.openSequenceSelector(value -> { PacketSequence loaded = PacketSequenceManager.loadSequence(value); if (loaded != null) { state.value().packets.clear(); state.value().packets.addAll(PacketDraftsCopy.copy(loaded).packets); name.setText(loaded.name); selected = -1; } }); return true; }
         if (listBounds != null && listBounds.contains(x, y) && y >= listBounds.y + 8) { int i = (y - listBounds.y - 8) / 42 + listScroll; if (i >= 0 && i < state.value().packets.size()) { int local = x - (listBounds.right() - ModernHoverScrollbar.GUTTER - 72); if (local > 46) { final int deleteIndex = i; owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktseqe.u014", "将删除第 " + (deleteIndex + 1) + " 条数据包。", "", false, ignored -> state.delete(deleteIndex))); } else if (local > 24) state.move(i, false); else if (local > 0) state.move(i, true); else loadPacket(i); } return true; }
         if (hit(addIdBounds, x, y)) { Integer id = parseId(); Integer ticks = parseDelay(); if (id == null || ticks == null) return true; state.addStandard(id.intValue(), hex.text(), ticks.intValue()); state.value().packets.get(state.value().packets.size() - 1).direction = direction; return true; }
         if (hit(addChannelBounds, x, y)) { Integer ticks = parseDelay(); if (ticks == null || packetId.text().trim().isEmpty()) { validationError = "gui.modern.pktseqe.u015"; owner.status(validationError); return true; } state.addChannel(packetId.text().trim(), hex.text(), ticks.intValue()); state.value().packets.get(state.value().packets.size() - 1).direction = direction; return true; }
         if (hit(applyBounds, x, y) && selected >= 0) { applyPacket(); return true; }
          if (hit(clearAllBounds, x, y)) { if (!state.value().packets.isEmpty()) owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktseqe.u016", "gui.modern.pktseqe.u017", "", false, ignored -> { state.value().packets.clear(); selected = -1; })); return true; }
          if (hit(saveBounds, x, y)) { if (saveDraft()) owner.back(); return true; }
         if (hit(sendBounds, x, y)) { if (!syncSelectedPacket()) return true; state.value().name = name.text().trim(); if (state.value().packets.isEmpty()) { owner.status("gui.modern.pktseqe.u018"); return true; } PacketSendSupport.sendSequence(owner.minecraft(), state.value()); owner.status("gui.modern.pktseqe.u019"); return true; }
         if (hit(cancelBounds, x, y)) { owner.requestBack(); return true; }
        return true;
    }
    @Override public boolean keyTyped(char c, int code) { if (code == Keyboard.KEY_ESCAPE) { owner.requestBack(); return true; } return fieldKey(name, c, code) || fieldKey(packetId, c, code) || fieldKey(hex, c, code) || fieldKey(delay, c, code) || code == Keyboard.KEY_RETURN; }
    @Override public void discardDraft() { PacketTextField.clearActiveFocus(); mobileScrollbar.endDrag(); listScrollbar.endDrag(); mobileScroll = 0; }
    @Override public void save() { saveDraft(); }
    @Override public boolean isDirty() {
        if (state.isDirty() || name.initialized() && !safe(name.text()).equals(safe(state.value().name))) {
            return true;
        }
        if (selected < 0 || selected >= state.value().packets.size()) {
            return packetId.initialized() && (!packetId.text().trim().isEmpty() || !hex.text().trim().isEmpty()
                    || !String.valueOf(DEFAULT_DELAY_TICKS).equals(delay.text().trim()));
        }
        PacketSequence.PacketToSend packet = state.value().packets.get(selected);
        String packetIdValue = packet.isFmlPacket ? safe(packet.channel)
                : packet.packetId == null ? "0" : String.valueOf(packet.packetId);
        return !packetIdValue.equals(packetId.text().trim()) || !safe(packet.hexData).equals(hex.text())
                || !String.valueOf(packet.delayTicks).equals(delay.text().trim())
                || !PacketSendSupport.normalizeDirection(packet.direction).equals(direction);
    }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || mobileViewport != null && !mobileViewport.contains(lastMouseX, lastMouseY)) return false;
        closeDropdowns();
        if (listBounds != null && listBounds.contains(lastMouseX, lastMouseY)) {
            int rows = Math.max(1, (listBounds.height - 16) / 42);
            int before = listScroll;
            listScroll = clamp(listScroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, state.value().packets.size() - rows));
            if (before != listScroll) return true;
        }
        if (mobileViewport != null) {
            int before = mobileScroll;
            mobileScroll = clamp(mobileScroll + (wheel > 0 ? -30 : 30), 0, mobileMaxScroll);
            return before != mobileScroll;
        }
        return false;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) { if (button == 0 && mobileScrollbar.isDragging()) { mobileScrollbar.applyDrag(x, y); return true; } if (button == 0 && listScrollbar.isDragging()) { listScrollbar.applyDrag(x, y); return true; } return false; }
    @Override public boolean mouseReleased(int x, int y, int button) { if (button == 0 && mobileScrollbar.isDragging()) { mobileScrollbar.endDrag(); return true; } if (button == 0 && listScrollbar.isDragging()) { listScrollbar.endDrag(); return true; } return false; }
    private static String inline(String value, int max) { String s = value == null ? "" : value.replace('\n', ' ').trim(); return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "..."; }
    private static String safe(String value) { return value == null ? "" : value; }

    private void loadPacket(int index) {
        selected = index;
        PacketSequence.PacketToSend packet = state.value().packets.get(index);
        packetId.setText(packet.isFmlPacket ? safe(packet.channel) : packet.packetId == null ? "0" : String.valueOf(packet.packetId));
        hex.setText(safe(packet.hexData)); delay.setText(String.valueOf(packet.delayTicks)); direction = PacketSendSupport.normalizeDirection(packet.direction); directionDrop.setValue(direction);
    }

    private boolean applyPacket() {
        if (selected < 0 || selected >= state.value().packets.size()) return true;
        PacketSequence.PacketToSend packet = state.value().packets.get(selected);
        if (packet.isFmlPacket) packet.channel = packetId.text().trim();
        else { Integer id = parseId(); if (id == null) return false; packet.packetId = id; }
        Integer ticks = parseDelay(); if (ticks == null) return false;
        packet.hexData = hex.text(); packet.delayTicks = ticks.intValue(); packet.direction = direction;
        validationError = "";
        return true;
    }

    private boolean syncSelectedPacket() { return selected < 0 || applyPacket(); }
    private boolean saveDraft() {
        if (!syncSelectedPacket()) return false;
        state.value().name = name.text().trim();
        if (state.value().name.isEmpty() || state.value().packets.isEmpty()) {
            owner.status("gui.modern.pktseqe.u020");
            return false;
        }
        boolean ok = PacketSequenceManager.saveSequence(state.value());
        if (ok) state.markSaved();
        saved = ok;
        owner.status(ok ? "gui.modern.pktseqe.u021" : "gui.modern.pktseqe.u022");
        return ok;
    }
    @Override protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("gui.modern.pktseqe.u007", () -> { state.value().packets.clear(); selected = -1; }));
        items.add(new PacketContextMenu.Item("gui.modern.pktseqe.u008", () -> saveDraft()));
        items.add(new PacketContextMenu.Item("gui.modern.pktseqe.u009", () -> { if (!state.value().packets.isEmpty()) PacketSendSupport.sendSequence(owner.minecraft(), state.value()); }));
        return items;
    }
    private Integer parseId() { try { int value = Integer.decode(packetId.text().trim()); if (value < 0 || value > 255) throw new NumberFormatException(); return Integer.valueOf(value); } catch (NumberFormatException ignored) { validationError = "gui.modern.pktseqe.u023"; owner.status(validationError); return null; } }
    private Integer parseDelay() { try { int value = Integer.parseInt(delay.text().trim()); if (value < 0 || value > 100000) throw new NumberFormatException(); return Integer.valueOf(value); } catch (NumberFormatException ignored) { validationError = "gui.modern.pktseqe.u024"; owner.status(validationError); return null; } }
    private void drawMobileScrollbar(int mouseX, int mouseY) {
        listScrollbar.idle();
        if (mobileViewport == null || mobileMaxScroll <= 0) {
            mobileScrollbar.idle();
            return;
        }
        mobileScrollbar.draw(mobileViewport, mobileScroll, mobileMaxScroll, mobileViewport.height,
                mobileViewport.height + mobileMaxScroll, mouseX, mouseY, value -> mobileScroll = value);
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}

final class PacketDraftsCopy {
    private PacketDraftsCopy() { }
    static PacketSequence copy(PacketSequence source) { PacketSequence result = new PacketSequence(source == null ? "" : source.name); if (source != null && source.packets != null) for (PacketSequence.PacketToSend p : source.packets) if (p != null) { PacketSequence.PacketToSend c = p.isFmlPacket ? new PacketSequence.PacketToSend(p.channel, p.hexData, p.delayTicks) : new PacketSequence.PacketToSend(p.packetId == null ? 0 : p.packetId, p.hexData, p.delayTicks); c.direction = p.direction; result.packets.add(c); } return result; }
}
