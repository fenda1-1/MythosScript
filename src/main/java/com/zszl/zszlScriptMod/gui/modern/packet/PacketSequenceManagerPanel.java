package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;

import net.minecraft.client.gui.FontRenderer;

/** Saved sequence management: load, send, rename, delete and create. */
final class PacketSequenceManagerPanel extends PacketPanelBase {
    private List<String> names = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private int lastMouseX, lastMouseY;
    private ModernMainLayout.Rect listBounds, newBounds, loadBounds, sendBounds, renameBounds, deleteBounds, refreshBounds;
    private long lastClick;
    private boolean draggingListBar;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();

    PacketSequenceManagerPanel(PacketWorkbenchTab owner) { super(owner, "gui.modern.pktseqm.u001"); }
    @Override protected void initializePanel() { reload(); }
    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        boolean compact = area.width < 560;
        int footerHeight = compact ? 54 : 30;
        int footer = area.bottom() - footerHeight;
        listBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 42, Math.max(1, area.width - 24),
                Math.max(1, footer - area.y - 42 - 8));
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, tr("gui.modern.pktseqm.fmt.saved", String.valueOf(names.size())), listBounds.x + 10, listBounds.y + 9, ModernUiRenderer.TEXT, listBounds.width - 20);
        ModernUiRenderer.beginClip(ModernHoverScrollbar.contentBounds(listBounds));
        int visibleRows = Math.max(1, (listBounds.height - 40) / 30); scroll = clamp(scroll, 0, Math.max(0, names.size() - visibleRows));
        int y = listBounds.y + 32; for (int i = scroll; i < names.size() && i < scroll + visibleRows; i++, y += 30) { boolean hover = listBounds.contains(mx, my) && my >= y && my < y + 26; ModernUiRenderer.drawSubtlePanel(listBounds.x + 7, y, ModernHoverScrollbar.contentWidth(listBounds.width - 7), 26, 3, i == selected ? ModernUiRenderer.SELECTED_SURFACE : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, i == selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE); text(font, (i + 1) + ". " + names.get(i), listBounds.x + 14, y + 8, ModernUiRenderer.TEXT, ModernHoverScrollbar.contentWidth(listBounds.width - 14)); }
        if (names.isEmpty()) text(font, "gui.modern.pktseqm.u002", listBounds.x + 12, listBounds.y + 48, ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        ModernUiRenderer.endClip();
        int listMax = Math.max(0, names.size() - visibleRows);
        if (listMax > 0) listScrollbar.draw(listBounds, scroll, listMax, visibleRows, names.size(), mx, my, value -> scroll = value);
        else listScrollbar.idle();
        int gap = 5;
        int bw = compact ? Math.max(1, (area.width - 24 - gap * 2) / 3)
                : Math.max(52, (area.width - 24 - gap * 5) / 6);
        if (compact) {
            newBounds = new ModernMainLayout.Rect(area.x + 12, footer, bw, 22);
            loadBounds = new ModernMainLayout.Rect(newBounds.right() + gap, footer, bw, 22);
            sendBounds = new ModernMainLayout.Rect(loadBounds.right() + gap, footer, bw, 22);
            renameBounds = new ModernMainLayout.Rect(area.x + 12, footer + 28, bw, 22);
            deleteBounds = new ModernMainLayout.Rect(renameBounds.right() + gap, footer + 28, bw, 22);
            refreshBounds = new ModernMainLayout.Rect(deleteBounds.right() + gap, footer + 28, bw, 22);
        } else {
            newBounds = new ModernMainLayout.Rect(area.x + 12, footer, bw, 22); loadBounds = new ModernMainLayout.Rect(newBounds.right() + gap, footer, bw, 22); sendBounds = new ModernMainLayout.Rect(loadBounds.right() + gap, footer, bw, 22); renameBounds = new ModernMainLayout.Rect(sendBounds.right() + gap, footer, bw, 22); deleteBounds = new ModernMainLayout.Rect(renameBounds.right() + gap, footer, bw, 22); refreshBounds = new ModernMainLayout.Rect(deleteBounds.right() + gap, footer, area.right() - deleteBounds.right() - 12, 22);
        }
        drawButton(font, newBounds, "gui.modern.pktseqm.u003", mx, my, true, true); drawButton(font, loadBounds, "gui.modern.pktseqm.u004", mx, my, selected >= 0, false); drawButton(font, sendBounds, "gui.modern.pktseqm.u005", mx, my, selected >= 0, false); drawButton(font, renameBounds, "gui.modern.pktseqm.u006", mx, my, selected >= 0, false); drawButton(font, deleteBounds, "gui.modern.pktseqm.u007", mx, my, selected >= 0, false); drawButton(font, refreshBounds, "gui.modern.pktseqm.u008", mx, my);
    }
    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button != 0) return true;
        if (listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; }
        if (listBounds != null && listBounds.contains(x, y) && y >= listBounds.y + 32) { int i = (y - listBounds.y - 32) / 30 + scroll; if (i >= 0 && i < names.size()) { if (selected == i && System.currentTimeMillis() - lastClick < 350L) owner.openSequenceEditor(names.get(i)); selected = i; lastClick = System.currentTimeMillis(); } return true; }
        if (hit(newBounds, x, y)) { owner.openSequenceEditor(null); return true; }
        if (selected < 0 || selected >= names.size()) { if (hit(refreshBounds, x, y)) reload(); return true; }
        String name = names.get(selected);
        if (hit(loadBounds, x, y)) owner.openSequenceEditor(name); else if (hit(sendBounds, x, y)) { PacketSequence seq = PacketSequenceManager.loadSequence(name); if (seq != null && seq.packets != null && !seq.packets.isEmpty()) { PacketSendSupport.sendSequence(owner.minecraft(), seq); owner.status("gui.modern.pktseqm.u009"); } else owner.status("gui.modern.pktseqm.u010"); } else if (hit(renameBounds, x, y)) owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktseqm.u011", "gui.modern.pktseqm.u012", name, true, value -> { String next = value == null ? "" : value.trim(); if (!next.isEmpty() && PacketSequenceManager.renameSequence(name, next)) { reload(); owner.status("gui.modern.pktseqm.u013"); } else owner.status("gui.modern.pktseqm.u014"); })); else if (hit(deleteBounds, x, y)) owner.panels().push(new PacketModalPanel(owner, "gui.modern.pktseqm.u015", "输入序列名确认删除: " + name, "", true, value -> { if (name.equals(value == null ? "" : value.trim())) { owner.status(PacketSequenceManager.deleteSequence(name) ? "gui.modern.pktseqm.u016" : "gui.modern.pktseqm.u017"); reload(); } else owner.status("gui.modern.pktseqm.u018"); })); else if (hit(refreshBounds, x, y)) reload(); return true;
    }
    private void reload() { names = new ArrayList<>(owner.sequences()); selected = -1; scroll = 0; }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (draggingListBar && button == 0) { draggingListBar = false; listScrollbar.endDrag(); return true; }
        return false;
    }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) { if (wheel == 0 || listBounds == null || !listBounds.contains(lastMouseX, lastMouseY)) return false; int rows = Math.max(1, (listBounds.height - 40) / 30); scroll = clamp(scroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, names.size() - rows)); return true; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
