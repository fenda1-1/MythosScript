package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketIdRecordManager;

import net.minecraft.client.gui.FontRenderer;

/** Filterable Packet ID history with modifier multi-select, copy and clear. */
final class PacketIdRecordPanel extends PacketPanelBase {
    private final PacketTextField filter = new PacketTextField(7701, 256);
    private List<PacketIdRecordManager.PacketIdRecord> records = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private int scroll;
    private int lastSelected = -1;
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect filterBounds, listBounds, refreshBounds, clearBounds, copyBounds;
    private boolean draggingListBar;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();

    PacketIdRecordPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktrec.u001"); }
    @Override protected void initializePanel() { filter.ensure(font); reload(); }
    @Override public void updateScreen() { filter.update(); }
    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        filterBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 42, Math.max(120, area.width - 24), 20); field(filter, filterBounds, null);
        listBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 70, Math.max(1, area.width - 24), Math.max(1, area.height - 108)); ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(ModernHoverScrollbar.contentBounds(listBounds));
        int visibleRows = Math.max(1, (listBounds.height - 16) / 30); scroll = clamp(scroll, 0, Math.max(0, records.size() - visibleRows));
        int y = listBounds.y + 8; for (int i = scroll; i < records.size() && i < scroll + visibleRows; i++, y += 30) { PacketIdRecordManager.PacketIdRecord r = records.get(i); boolean hover = listBounds.contains(mx, my) && my >= y && my < y + 26; ModernUiRenderer.drawSubtlePanel(listBounds.x + 7, y, ModernHoverScrollbar.contentWidth(listBounds.width - 7), 26, 3, selected.contains(i) ? 0xFF2C3D49 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, selected.contains(i) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); text(font, r.getDisplayDirection() + "  " + safe(r.packetClassName), listBounds.x + 13, y + 5, ModernUiRenderer.TEXT, listBounds.width - 220); text(font, r.getIdOrChannelText() + " · " + Math.max(1, r.occurrenceCount), listBounds.x + listBounds.width - ModernHoverScrollbar.GUTTER - 200, y + 5, ModernUiRenderer.SUBTLE_TEXT, 188); }
        if (records.isEmpty()) text(font, "gui.modern.pktrec.u002", listBounds.x + 12, listBounds.y + 42, ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        ModernUiRenderer.endClip();
        int listMax = Math.max(0, records.size() - visibleRows);
        if (listMax > 0) listScrollbar.draw(listBounds, scroll, listMax, visibleRows, records.size(), mx, my, value -> scroll = value);
        else listScrollbar.idle();
        int footer = area.bottom() - 30, gap = 6, bw = Math.max(70, (area.width - 24 - gap * 2) / 3); refreshBounds = new ModernMainLayout.Rect(area.x + 12, footer, bw, 22); clearBounds = new ModernMainLayout.Rect(refreshBounds.right() + gap, footer, bw, 22); copyBounds = new ModernMainLayout.Rect(clearBounds.right() + gap, footer, area.right() - clearBounds.right() - 12, 22); drawButton(font, refreshBounds, "gui.modern.pktrec.u003", mx, my); drawButton(font, clearBounds, "gui.modern.pktrec.u004", mx, my); drawButton(font, copyBounds, "复制所选 (" + selected.size() + ")", mx, my, !selected.isEmpty(), true);
    }
    @Override protected boolean handleBodyClick(int x, int y, int button) { if (filter.click(x, y, button)) { reload(); return true; } if (button != 0) return true; if (listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; } if (listBounds != null && listBounds.contains(x, y) && y >= listBounds.y + 8) { int i = (y - listBounds.y - 8) / 30 + scroll; if (i >= 0 && i < records.size()) select(i); return true; } if (hit(refreshBounds, x, y)) reload(); else if (hit(clearBounds, x, y)) { PacketIdRecordManager.clearRecords(); reload(); } else if (hit(copyBounds, x, y)) copy(); return true; }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) { if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; } return false; }
    @Override public boolean mouseReleased(int x, int y, int button) { if (draggingListBar && button == 0) { draggingListBar = false; listScrollbar.endDrag(); return true; } return false; }
    private void select(int index) { boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL); boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT); if (shift && lastSelected >= 0) { selected.clear(); for (int i = Math.min(lastSelected, index); i <= Math.max(lastSelected, index); i++) selected.add(i); } else { if (!ctrl) selected.clear(); if (!selected.add(index)) selected.remove(index); lastSelected = index; } }
    private void copy() { StringBuilder b = new StringBuilder(); SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"); for (Integer i : new TreeSet<>(selected)) if (i >= 0 && i < records.size()) { PacketIdRecordManager.PacketIdRecord r = records.get(i); b.append(r.getDisplayDirection()).append(" | ").append(safe(r.packetClassName)).append(" | ").append(r.getIdOrChannelText()).append(" | 次数=").append(Math.max(1, r.occurrenceCount)).append(" | 首次=").append(format.format(new Date(r.firstSeenAt))).append(" | 最近=").append(format.format(new Date(r.lastSeenAt))).append('\n'); } if (b.length() > 0) { PacketClipboard.copyOrExport(owner.minecraft(), b.toString().trim()); owner.status("gui.modern.pktrec.u005"); } }
    private void reload() { String q = filter.text().trim().toLowerCase(); records.clear(); for (PacketIdRecordManager.PacketIdRecord r : PacketIdRecordManager.listRecords()) if (q.isEmpty() || safe(r.packetClassName).toLowerCase().contains(q) || safe(r.direction).toLowerCase().contains(q) || safe(r.channel).toLowerCase().contains(q) || safe(r.getIdOrChannelText()).toLowerCase().contains(q)) records.add(r); selected.clear(); lastSelected = -1; scroll = 0; }
    @Override public boolean keyTyped(char c, int code) { if (filter.key(c, code)) { reload(); return true; } return code == Keyboard.KEY_RETURN; }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) { if (wheel == 0 || listBounds == null || !listBounds.contains(lastMouseX, lastMouseY)) return false; int rows = Math.max(1, (listBounds.height - 16) / 30); scroll = clamp(scroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, records.size() - rows)); return true; }
    private static String safe(String s) { return s == null ? "" : s; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
