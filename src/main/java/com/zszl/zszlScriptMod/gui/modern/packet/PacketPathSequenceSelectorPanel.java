package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.path.PathSequenceManager;

import net.minecraft.client.gui.FontRenderer;

/** Modern selector for path sequences referenced by captured-ID update rules. */
final class PacketPathSequenceSelectorPanel extends PacketPanelBase {
    interface Selection { void accept(String name); }
    private final Selection selection;
    private final List<String> names = new ArrayList<>();
    private ModernMainLayout.Rect listBounds;
    private int scroll, lastMouseX, lastMouseY;
    private boolean draggingListBar;
    private final ModernHoverScrollbar listScrollbar = new ModernHoverScrollbar();

    PacketPathSequenceSelectorPanel(PacketWorkbenchTab owner, Selection selection) {
        super(owner, "gui.modern.pktpath.u001"); this.selection = selection;
    }
    @Override protected void initializePanel() {
        names.clear();
        for (PathSequenceManager.PathSequence sequence : PathSequenceManager.getAllVisibleSequences()) {
            if (sequence != null && sequence.getName() != null && !sequence.getName().trim().isEmpty()) names.add(sequence.getName());
        }
    }
    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        lastMouseX = mx; lastMouseY = my;
        listBounds = new ModernMainLayout.Rect(area.x + 12, area.y + 42, Math.max(1, area.width - 24), Math.max(1, area.height - 78));
        ModernUiRenderer.drawSubtlePanel(listBounds.x, listBounds.y, listBounds.width, listBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(ModernHoverScrollbar.contentBounds(listBounds));
        int rows = Math.max(1, (listBounds.height - 20) / 30);
        scroll = clamp(scroll, 0, Math.max(0, names.size() - rows));
        ModernUiRenderer.beginClip(listBounds);
        for (int i = scroll; i < names.size() && i < scroll + rows; i++) {
            int y = listBounds.y + 10 + (i - scroll) * 30;
            boolean hover = listBounds.contains(mx, my) && my >= y && my < y + 26;
            ModernUiRenderer.drawSubtlePanel(listBounds.x + 7, y, ModernHoverScrollbar.contentWidth(listBounds.width - 7), 26, 3,
                    hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
            text(font, names.get(i), listBounds.x + 14, y + 8, ModernUiRenderer.TEXT, ModernHoverScrollbar.contentWidth(listBounds.width - 14));
        }
        ModernUiRenderer.endClip();
        if (names.isEmpty()) text(font, "gui.modern.pktpath.u002", listBounds.x + 12, listBounds.y + 42, ModernUiRenderer.MUTED_TEXT, listBounds.width - 24);
        ModernUiRenderer.endClip();
        int listMax = Math.max(0, names.size() - rows);
        if (listMax > 0) listScrollbar.draw(listBounds, scroll, listMax, rows, names.size(), mx, my, value -> scroll = value);
        else listScrollbar.idle();
    }
    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button == 0 && listScrollbar.beginDrag(x, y)) { draggingListBar = true; return true; }
        if (button == 0 && listBounds != null && listBounds.contains(x, y) && y >= listBounds.y + 10) {
            int index = (y - listBounds.y - 10) / 30 + scroll;
            if (index >= 0 && index < names.size()) { if (selection != null) selection.accept(names.get(index)); owner.back(); }
        }
        return true;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingListBar && button == 0) { listScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (draggingListBar && button == 0) { draggingListBar = false; listScrollbar.endDrag(); return true; }
        return false;
    }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || listBounds == null || !listBounds.contains(lastMouseX, lastMouseY)) return false;
        int rows = Math.max(1, (listBounds.height - 20) / 30);
        scroll = clamp(scroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, names.size() - rows)); return true;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
