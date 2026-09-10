package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

import net.minecraft.client.gui.FontRenderer;

/** Small panel-local context menu used by packet rule editors. */
final class PacketContextMenu {
    static final class Item {
        final String label;
        final Runnable action;

        Item(String label, Runnable action) {
            this.label = label == null ? "" : label;
            this.action = action;
        }
    }

    private final List<Item> items = new ArrayList<Item>();
    private ModernMainLayout.Rect bounds;

    void open(int x, int y, List<Item> values, ModernMainLayout.Rect host) {
        items.clear();
        if (values != null) items.addAll(values);
        if (items.isEmpty() || host == null) {
            bounds = null;
            return;
        }
        int width = 152;
        int height = items.size() * 23 + 8;
        int px = Math.max(host.x + 3, Math.min(x, host.right() - width - 3));
        int py = Math.max(host.y + 3, Math.min(y, host.bottom() - height - 3));
        bounds = new ModernMainLayout.Rect(px, py, width, height);
    }

    boolean isOpen() { return bounds != null && !items.isEmpty(); }

    void close() { bounds = null; items.clear(); }

    void draw(FontRenderer font, int mouseX, int mouseY) {
        if (!isOpen()) return;
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        for (int i = 0; i < items.size(); i++) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(bounds.x + 4, bounds.y + 4 + i * 23,
                    bounds.width - 8, 21);
            boolean hover = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                    hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, ModernFormI18n.tr(items.get(i).label), row.x + 9, row.y + 6,
                    ModernUiRenderer.TEXT, row.width - 18);
        }
    }

    boolean click(int x, int y, int button) {
        if (!isOpen()) return false;
        if (button != 0 || !bounds.contains(x, y)) {
            close();
            return true;
        }
        int index = (y - bounds.y - 4) / 23;
        if (index >= 0 && index < items.size()) {
            Runnable action = items.get(index).action;
            close();
            if (action != null) action.run();
        } else {
            close();
        }
        return true;
    }
}
