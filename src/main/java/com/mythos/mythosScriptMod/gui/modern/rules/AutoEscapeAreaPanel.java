package com.mythos.mythosScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.lwjgl.input.Keyboard;

import com.mythos.mythosScriptMod.gui.modern.ModernHoverScrollbar;
import com.mythos.mythosScriptMod.gui.modern.ModernMainLayout;
import com.mythos.mythosScriptMod.gui.modern.ModernMainLayout.Rect;
import com.mythos.mythosScriptMod.gui.modern.ModernUiRenderer;
import com.mythos.mythosScriptMod.gui.modern.components.ModernTextField;
import com.mythos.mythosScriptMod.gui.modern.form.ModernFormWidget;
import com.mythos.mythosScriptMod.system.AutoEscapeRule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Card-list editor for area-blacklist rectangles; each card is one picked region. */
final class AutoEscapeAreaPanel implements ModernFormWidget {
    private final Supplier<String> get;
    private final Consumer<String> set;
    private final Runnable pick;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final List<Rect> cards = new ArrayList<>();
    private final java.util.Map<String, Rect> buttons = new java.util.LinkedHashMap<>();
    private final ModernTextField[] fields = new ModernTextField[5];
    private Rect bounds, body, resize, viewport;
    private FontRenderer font;
    private int desiredHeight = 180, scroll, maxScroll, selected = -1;
    private int resizeStartY, resizeStartHeight;
    private boolean resizing, editing;
    private String error = "";

    AutoEscapeAreaPanel(String title, Supplier<String> get, Consumer<String> set, Runnable pick) {
        this.get = get;
        this.set = set;
        this.pick = pick;
        this.title = title;
    }

    private final String title;

    private List<String> values() { return entries(get == null ? "" : get.get()); }
    private void write(List<String> values) { set.accept(String.join("; ", values)); }

    private static List<String> entries(String text) {
        List<String> result = new ArrayList<>();
        for (String item : (text == null ? "" : text).split("[;；\\n]+")) {
            if (!item.trim().isEmpty()) result.add(item.trim());
        }
        return result;
    }

    @Override public void setViewport(Rect viewport) { this.viewport = viewport; }

    @Override public int height(int width, int availableHeight) {
        int minimum = editing && width < 360 ? 190 : 150;
        return Math.max(minimum, Math.min(desiredHeight, Math.max(minimum, availableHeight - 24)));
    }

    @Override public void ensureInitialized(FontRenderer font) {
        this.font = font;
        for (int i = 0; i < fields.length; i++) if (fields[i] == null) {
            fields[i] = new ModernTextField(i, font, 0, 0, 1, 18);
            fields[i].setMaxStringLength(16);
            fields[i].setEnableBackgroundDrawing(false);
        }
    }

    @Override public void draw(FontRenderer font, Rect bounds, int mx, int my) {
        ensureInitialized(font);
        this.bounds = bounds;
        buttons.clear();
        cards.clear();
        List<String> rows = values();
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        text(title + "  ·  " + rows.size(), bounds.x + 8, bounds.y + 8, bounds.width - 16, ModernUiRenderer.TEXT);
        int top = bounds.y + 26;
        int bw = Math.max(40, (bounds.width - 26) / 3);
        button("pick", "框选区域", bounds.x + 6, top, bw, mx, my);
        button("manual", "手动添加", bounds.x + 10 + bw, top, bw, mx, my);
        button("delete", "删除", bounds.x + 14 + 2 * bw, top, bw, mx, my);
        top += 27;
        if (editing) {
            int fw = Math.max(28, (bounds.width - 82) / 5);
            String[] names = {"X1", "Z1", "X2", "Z2", "维度"};
            for (int i = 0; i < 5; i++) {
                field(i, new Rect(bounds.x + 7 + i * (fw + 4), top, fw, 21), names[i]);
            }
            button("apply", "应用", bounds.right() - 60, top, 53, mx, my);
            top += 27;
        }
        String hint = !error.isEmpty() ? error : "左键选点1 · 右键选点2 · 中键确认 · 右键卡片删除 · 拖底边调整高度";
        text(hint, bounds.x + 8, top, bounds.width - 16, error.isEmpty() ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.WARNING);
        body = new Rect(bounds.x + 6, top + 15, Math.max(1, bounds.width - 12), Math.max(12, bounds.bottom() - top - 27));
        int contentWidth = ModernHoverScrollbar.contentWidth(body.width);
        maxScroll = Math.max(0, rows.size() * 46 - body.height);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        ModernUiRenderer.beginClip(body);
        int y = body.y - scroll;
        for (int i = 0; i < rows.size(); i++) {
            Rect card = new Rect(body.x, y, contentWidth, 42);
            cards.add(card);
            if (card.bottom() >= body.y && card.y < body.bottom()) {
                boolean highlight = i == selected;
                ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 4,
                        highlight ? ModernUiRenderer.ACCENT_DIM : card.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        highlight ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                int[] rect = parseRect(rows.get(i));
                text("区域 " + (i + 1), card.x + 8, card.y + 6, card.width - 16, ModernUiRenderer.TEXT);
                text(rect == null ? "坐标不完整，请手动补全"
                                : String.format(Locale.ROOT, "X %d → %d   Z %d → %d   维度 %d", rect[1], rect[3], rect[2], rect[4], rect[0]),
                        card.x + 8, card.y + 23, card.width - 16, ModernUiRenderer.SUBTLE_TEXT);
            }
            y += 46;
        }
        if (rows.isEmpty()) {
            text("列表为空，点击“框选区域”圈出一个矩形。", body.x + 6, body.y + 8, contentWidth - 12, ModernUiRenderer.MUTED_TEXT);
        }
        ModernUiRenderer.endClip();
        scrollbar.draw(body, scroll, maxScroll, body.height, rows.size() * 46, mx, my, v -> scroll = v);
        resize = new Rect(bounds.x + 6, bounds.bottom() - 9, bounds.width - 12, 8);
        ModernUiRenderer.drawRoundedRect(bounds.x + bounds.width / 2 - 18, bounds.bottom() - 5, 36, 2, 1,
                resizing || resize.contains(mx, my) ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
    }

    /** Parses "dim:x1,z1,x2,z2" → [dim, x1, z1, x2, z2] or null. */
    private static int[] parseRect(String value) {
        String v = value == null ? "" : value.trim();
        int colon = v.indexOf(':');
        if (colon <= 0) return null;
        String[] parts = v.substring(colon + 1).split(",");
        if (parts.length != 4) return null;
        try {
            return new int[] {
                    Integer.parseInt(v.substring(0, colon).trim()),
                    Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()) };
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private void text(String s, int x, int y, int w, int color) {
        ModernUiRenderer.drawText(font, s, x, y, color, Math.max(1, w));
    }

    private void button(String key, String label, int x, int y, int w, int mx, int my) {
        Rect r = new Rect(x, y, w, 21);
        buttons.put(key, r);
        ModernUiRenderer.drawSubtlePanel(x, y, w, 21, 4,
                r.contains(mx, my) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER);
        text(label, x + 5, y + 6, w - 10, ModernUiRenderer.TEXT);
    }

    private void field(int i, Rect r, String placeholder) {
        ModernTextField f = fields[i];
        f.layout(r);
        f.setVisible(true);
        f.setEnabled(true);
        f.setPlaceholder(placeholder);
        ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 4,
                ModernUiRenderer.SHELL_RAISED, f.isFocused() ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER);
        f.drawTextContents(font);
    }

    @Override public boolean mouseClicked(int x, int y, int button) {
        if (button == 0 && resize != null && resize.contains(x, y)) {
            resizing = true;
            resizeStartY = y;
            resizeStartHeight = bounds.height;
            return true;
        }
        if (button == 0 && scrollbar.beginDrag(x, y)) return true;
        if (button == 0) {
            for (java.util.Map.Entry<String, Rect> entry : buttons.entrySet()) {
                if (entry.getValue().contains(x, y)) { action(entry.getKey()); return true; }
            }
            for (int i = 0; i < fields.length; i++) {
                fields[i].setFocused(false);
                if (editing) fields[i].mouseClicked(x, y, button);
            }
        }
        if (body != null && body.contains(x, y)) {
            for (int i = 0; i < cards.size(); i++) if (cards.get(i).contains(x, y)) {
                if (button == 1) { remove(i); return true; }
                if (button == 0) { if (!commit()) return true; selected = i; editEntry(i); }
                return true;
            }
            return true;
        }
        return true;
    }

    private void action(String key) {
        if ("apply".equals(key)) { commit(); return; }
        if ("delete".equals(key)) { remove(selected); return; }
        if (!commit()) return;
        if ("manual".equals(key)) {
            selected = -1;
            editing = true;
            for (ModernTextField f : fields) f.setText("");
            if (Minecraft.getMinecraft().player != null) {
                fields[4].setText(String.valueOf(Minecraft.getMinecraft().player.dimension));
            }
            fields[0].setFocused(true);
        }
        if ("pick".equals(key) && pick != null) pick.run();
    }

    private void editEntry(int index) {
        int[] rect = parseRect(values().get(index));
        for (int i = 0; i < fields.length; i++) fields[i].setFocused(false);
        if (rect != null) {
            fields[0].setText(String.valueOf(rect[1]));
            fields[1].setText(String.valueOf(rect[2]));
            fields[2].setText(String.valueOf(rect[3]));
            fields[3].setText(String.valueOf(rect[4]));
            fields[4].setText(String.valueOf(rect[0]));
        }
        editing = true;
        error = "";
    }

    private void remove(int index) {
        List<String> list = values();
        if (index < 0 || index >= list.size()) { error = "请先选择一项"; return; }
        list.remove(index);
        write(list);
        selected = -1;
        editing = false;
        error = "";
    }

    @Override public boolean commit() {
        if (!editing) return true;
        try {
            int dim = fields[4].getText().trim().isEmpty() && Minecraft.getMinecraft().player != null
                    ? Minecraft.getMinecraft().player.dimension
                    : Integer.parseInt(fields[4].getText().trim());
            int x1 = Integer.parseInt(fields[0].getText().trim());
            int z1 = Integer.parseInt(fields[1].getText().trim());
            int x2 = Integer.parseInt(fields[2].getText().trim());
            int z2 = Integer.parseInt(fields[3].getText().trim());
            int ax1 = Math.min(x1, x2), ax2 = Math.max(x1, x2);
            int az1 = Math.min(z1, z2), az2 = Math.max(z1, z2);
            String value = dim + ":" + ax1 + "," + az1 + "," + ax2 + "," + az2;
            List<String> list = values();
            if (selected >= 0 && selected < list.size()) list.set(selected, value);
            else { list.add(value); selected = list.size() - 1; }
            write(list);
            editing = false;
            blur();
            error = "";
            return true;
        } catch (NumberFormatException invalid) {
            error = "坐标必须是整数";
            return false;
        }
    }

    @Override public boolean keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE && handleEscape()) return true;
        if (!isTextInputFocused()) return false;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { commit(); return true; }
        for (ModernTextField f : fields) if (f.isFocused()) return f.textboxKeyTyped(c, key);
        return false;
    }

    @Override public boolean handleEscape() {
        if (editing) { editing = false; error = ""; blur(); return true; }
        if (isTextInputFocused()) { blur(); return true; }
        return false;
    }

    @Override public boolean mouseClickMove(int x, int y, int button, long time) {
        if (button != 0) return false;
        if (resizing) {
            desiredHeight = Math.max(128, Math.min(640, resizeStartHeight + y - resizeStartY));
            return true;
        }
        if (scrollbar.isDragging()) { scrollbar.applyDrag(x, y); return true; }
        return false;
    }

    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        boolean handled = resizing || scrollbar.isDragging();
        resizing = false;
        scrollbar.endDrag();
        return handled;
    }

    @Override public boolean handleMouseWheel(int wheel, int x, int y) {
        if (body == null || !body.contains(x, y)) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll + (wheel > 0 ? -30 : 30)));
        return true;
    }

    @Override public boolean isTextInputFocused() {
        for (ModernTextField f : fields) if (f != null && f.isFocused()) return true;
        return false;
    }

    @Override public Object snapshot() { return get == null ? null : get.get(); }

    @Override public boolean isDirty() {
        if (!editing) return false;
        List<String> current = values();
        return selected < 0 || selected >= current.size() || !current.get(selected).equals(serialize());
    }

    private String serialize() {
        try {
            int dim = Integer.parseInt(fields[4].getText().trim());
            int x1 = Integer.parseInt(fields[0].getText().trim());
            int z1 = Integer.parseInt(fields[1].getText().trim());
            int x2 = Integer.parseInt(fields[2].getText().trim());
            int z2 = Integer.parseInt(fields[3].getText().trim());
            return dim + ":" + Math.min(x1, x2) + "," + Math.min(z1, z2) + "," + Math.max(x1, x2) + "," + Math.max(z1, z2);
        } catch (NumberFormatException invalid) {
            return "";
        }
    }

    @Override public void blur() {
        for (ModernTextField f : fields) if (f != null) f.setFocused(false);
    }

    @Override public String getHoveredTooltip(int x, int y) {
        if (body != null && body.contains(x, y)) {
            for (int i = 0; i < cards.size(); i++) if (cards.get(i).contains(x, y)) return values().get(i);
        }
        return error;
    }
}
