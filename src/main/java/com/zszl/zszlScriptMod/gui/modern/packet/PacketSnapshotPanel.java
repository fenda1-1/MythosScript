package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketSnapshotManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.gui.FontRenderer;

/** Snapshot listing, filtering, sorting, opening, renaming and deletion. */
final class PacketSnapshotPanel extends PacketPanelBase {
    private final PacketTextField filter = new PacketTextField(7601, 256);
    private List<PacketSnapshotManager.SnapshotMeta> snapshots = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private int lastMouseX, lastMouseY;
    private boolean newestFirst = true;
    private ModernMainLayout.Rect filterBounds, listBounds, openBounds, renameBounds, deleteBounds, sortBounds, refreshBounds;
    private boolean draggingListBar;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();

    PacketSnapshotPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktsnap.u001"); }
    @Override protected void initializePanel() { filter.ensure(font); reload(); }
    @Override public void updateScreen() { filter.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        filterBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 42, Math.max(120, area.width - 24), 20); field(filter, filterBounds, null);
        boolean compact = area.width < 560;
        int footerHeight = compact ? 54 : 30;
        int footer = area.bottom() - footerHeight;
        listBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 70, Math.max(1, area.width - 24),
                Math.max(1, footer - area.y - 70 - 8));
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(ModernHoverScrollbar.contentBounds(listBounds));
        List<PacketSnapshotManager.SnapshotMeta> visible = visible(); int visibleRows = Math.max(1, (listBounds.height - 16) / 32); scroll = clamp(scroll, 0, Math.max(0, visible.size() - visibleRows)); int y = listBounds.y + 8;
        for (int i = scroll; i < visible.size() && i < scroll + visibleRows; i++, y += 32) { PacketSnapshotManager.SnapshotMeta m = visible.get(i); boolean hover = listBounds.contains(mx, my) && my >= y && my < y + 28; ModernUiRenderer.drawSubtlePanel(listBounds.x + 7, y, ModernHoverScrollbar.contentWidth(listBounds.width - 7), 28, 3, i == selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, i == selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); text(font, safe(m.name), listBounds.x + 14, y + 5, ModernUiRenderer.TEXT, listBounds.width - 180); text(font, m.packetCount + " 包 · " + safe(m.captureMode), listBounds.x + listBounds.width - ModernHoverScrollbar.GUTTER - 160, y + 5, ModernUiRenderer.SUBTLE_TEXT, 145); text(font, safe(m.getDisplayTime()), listBounds.x + 14, y + 17, ModernUiRenderer.MUTED_TEXT, ModernHoverScrollbar.contentWidth(listBounds.width - 14)); }
        if (visible.isEmpty()) text(font, "gui.modern.pktsnap.u002", listBounds.x + 12, listBounds.y + 42, ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        ModernUiRenderer.endClip();
        int listMax = Math.max(0, visible.size() - visibleRows);
        if (listMax > 0) listScrollbar.draw(listBounds, scroll, listMax, visibleRows, visible.size(), mx, my, value -> scroll = value);
        else listScrollbar.idle();
        int gap = 6;
        if (compact) {
            int bw = Math.max(1, (area.width - 24 - gap * 2) / 3);
            refreshBounds = new ModernMainLayout.Rect(area.x + 12, footer, bw, 22);
            openBounds = new ModernMainLayout.Rect(refreshBounds.right() + gap, footer, bw, 22);
            renameBounds = new ModernMainLayout.Rect(openBounds.right() + gap, footer, bw, 22);
            int half = Math.max(1, (area.width - 24 - gap) / 2);
            deleteBounds = new ModernMainLayout.Rect(area.x + 12, footer + 28, half, 22);
            sortBounds = new ModernMainLayout.Rect(deleteBounds.right() + gap, footer + 28, half, 22);
        } else {
            int bw = Math.max(60, (area.width - 24 - gap * 4) / 5);
            refreshBounds = new ModernMainLayout.Rect(area.x + 12, footer, bw, 22); openBounds = new ModernMainLayout.Rect(refreshBounds.right() + gap, footer, bw, 22); renameBounds = new ModernMainLayout.Rect(openBounds.right() + gap, footer, bw, 22); deleteBounds = new ModernMainLayout.Rect(renameBounds.right() + gap, footer, bw, 22); sortBounds = new ModernMainLayout.Rect(deleteBounds.right() + gap, footer, area.right() - deleteBounds.right() - 12, 22);
        }
        drawButton(font, refreshBounds, "gui.modern.pktsnap.u003", mx, my); drawButton(font, openBounds, "gui.modern.pktsnap.u004", mx, my, selected >= 0, true); drawButton(font, renameBounds, "gui.modern.pktsnap.u005", mx, my, selected >= 0, false); drawButton(font, deleteBounds, "gui.modern.pktsnap.u006", mx, my, selected >= 0, false); drawButton(font, sortBounds, newestFirst ? "gui.modern.pktsnap.u007" : "gui.modern.pktsnap.u008", mx, my);
    }
    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (filter.click(x, y, button)) { if (button == 0) reload(); return true; }
        if (button != 0) return true;
        if (listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; }
        if (listBounds != null && listBounds.contains(x, y) && y >= listBounds.y + 8) { int i = (y - listBounds.y - 8) / 32 + scroll; if (i >= 0 && i < visible().size()) { if (selected == i && System.currentTimeMillis() - lastClick < 350L) openSelected(); selected = i; lastClick = System.currentTimeMillis(); } return true; }
        if (hit(refreshBounds, x, y)) { reload(); return true; } if (hit(openBounds, x, y)) { openSelected(); return true; }
        if (hit(renameBounds, x, y) && selected >= 0) { final String old = visible().get(selected).name; owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktsnap.u009", "gui.modern.pktsnap.u010", old, true, value -> { String next = value == null ? "" : value.trim(); if (!next.isEmpty() && PacketSnapshotManager.renameSnapshot(old, next)) { reload(); owner.status("gui.modern.pktsnap.u011"); } else owner.status("gui.modern.pktsnap.u012"); })); return true; }
        if (hit(deleteBounds, x, y) && selected >= 0) { final String name = visible().get(selected).name; owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktsnap.u013", "再次输入名称以确认删除: " + name, "", true, value -> { if (name.equals(value == null ? "" : value.trim())) { owner.status(PacketSnapshotManager.deleteSnapshot(name) ? "gui.modern.pktsnap.u014" : "gui.modern.pktsnap.u015"); reload(); } else owner.status("gui.modern.pktsnap.u016"); })); return true; }
        if (hit(sortBounds, x, y)) { newestFirst = !newestFirst; reload(); return true; } return true;
    }
    private long lastClick;
    private void openSelected() { if (selected < 0 || selected >= visible().size()) return; PacketSnapshotManager.SavedSnapshot snapshot = PacketSnapshotManager.loadSnapshot(visible().get(selected).name); if (snapshot == null) { owner.status("gui.modern.pktsnap.u017"); return; } List<PacketCaptureHandler.CapturedPacketData> packets = new ArrayList<>(); if (snapshot.packets != null) for (PacketSnapshotManager.SavedPacket p : snapshot.packets) if (p != null) packets.add(p.toCaptured()); owner.openViewer(packets, snapshot.name, true, snapshot.captureMode); }
    private void reload() { snapshots = new ArrayList<>(owner.snapshots()); if (!newestFirst) Collections.sort(snapshots, Comparator.comparingLong(m -> m.createdAt)); selected = -1; scroll = 0; }
    private List<PacketSnapshotManager.SnapshotMeta> visible() { String q = filter.text().trim().toLowerCase(); List<PacketSnapshotManager.SnapshotMeta> result = new ArrayList<>(); for (PacketSnapshotManager.SnapshotMeta m : snapshots) if (q.isEmpty() || safe(m.name).toLowerCase().contains(q) || safe(m.captureMode).toLowerCase().contains(q) || safe(m.getDisplayTime()).toLowerCase().contains(q)) result.add(m); return result; }
    @Override public boolean keyTyped(char c, int code) { if (filter.key(c, code)) { reload(); return true; } return code == Keyboard.KEY_RETURN; }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (draggingListBar && button == 0) { draggingListBar = false; listScrollbar.endDrag(); return true; }
        return false;
    }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) { if (wheel == 0 || listBounds == null || !listBounds.contains(lastMouseX, lastMouseY)) return false; int rows = Math.max(1, (listBounds.height - 16) / 32); List<PacketSnapshotManager.SnapshotMeta> visible = visible(); scroll = clamp(scroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, visible.size() - rows)); return true; }
    private static String safe(String s) { return s == null ? "" : s; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
