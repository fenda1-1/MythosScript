package com.zszl.zszlScriptMod.gui.modern;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;

/** Shared, bounded navigation footer and modal action menus for rule workbenches. */
public final class ModernNavigationActions {
    private final String key;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Boolean> pins = new LinkedHashMap<>();
    private final List<Hit> footerHits = new ArrayList<>();
    private final List<Hit> menuHits = new ArrayList<>();
    private final List<Action> menu = new ArrayList<>();
    private ModernMainLayout.Rect bounds, footer, more, customize, popup, inputBounds, acceptBounds;
    private FontRenderer font;
    private boolean loaded, open, customizing;
    private int menuX, menuY, scroll;
    private GuiTextField input;
    private Consumer<String> accept;
    private String inputTitle;

    public ModernNavigationActions(String key) { this.key = key; }

    public void action(String id, String label, boolean pinned, boolean danger,
            BooleanSupplier enabled, Runnable run) {
        actions.put(id, new Action(id, label, danger, enabled, run));
        if (!pins.containsKey(id)) pins.put(id, pinned);
    }

    public void begin(FontRenderer font, ModernMainLayout.Rect bounds) {
        this.font = font;
        this.bounds = bounds;
        if (!loaded) { load(); loaded = true; }
        footerHits.clear();
        more = new ModernMainLayout.Rect(bounds.right() - 26, bounds.y + 5, 20, 20);
        int available = Math.max(0, (bounds.height - 100) / 24);
        List<Action> fixed = new ArrayList<>();
        for (Action a : actions.values()) if (Boolean.TRUE.equals(pins.get(a.id)) && !a.danger) fixed.add(a);
        fixed.sort((a, b) -> Integer.compare(defaultOrder(a.id), defaultOrder(b.id)));
        for (Action a : actions.values()) if (Boolean.TRUE.equals(pins.get(a.id)) && a.danger) fixed.add(a);
        int innerWidth = Math.max(1, bounds.width - 22);
        int[] widths = new int[fixed.size()];
        for (int i = 0; i < fixed.size(); i++) widths[i] = textWidth(fixed.get(i).label) + 24;
        List<ModernMainLayout.Rect> positions = flowLayout(innerWidth, available, widths);
        int rows = positions.isEmpty() ? 0 : positions.get(positions.size() - 1).y / 24 + 1;
        int height = Math.min(bounds.height, 30 + rows * 24);
        footer = new ModernMainLayout.Rect(bounds.x + 5, bounds.bottom() - height - 5,
                Math.max(1, bounds.width - 10), height);
        customize = new ModernMainLayout.Rect(footer.right() - 23, footer.y + 3, 20, 20);
        for (int i = 0; i < positions.size(); i++) {
            ModernMainLayout.Rect position = positions.get(i);
            footerHits.add(new Hit(fixed.get(i), new ModernMainLayout.Rect(
                    footer.x + 6 + position.x, footer.y + 26 + position.y, position.width, 19)));
        }
    }

    /** Wrap at measured label widths, then share the spare width within each row. */
    static List<ModernMainLayout.Rect> flowLayout(int width, int maxRows, int[] preferredWidths) {
        List<ModernMainLayout.Rect> result = new ArrayList<>();
        width = Math.max(1, width);
        int start = 0;
        for (int row = 0; row < maxRows && start < preferredWidths.length; row++) {
            int end = start, used = 0;
            while (end < preferredWidths.length) {
                int next = Math.min(width, Math.max(1, preferredWidths[end]));
                if (end > start && used + 6 + next > width) break;
                used += (end > start ? 6 : 0) + next;
                end++;
            }
            int spare = width - used, count = end - start, x = 0;
            for (int i = start; i < end; i++) {
                int itemWidth = Math.min(width, Math.max(1, preferredWidths[i]))
                        + spare / count + (i - start < spare % count ? 1 : 0);
                result.add(new ModernMainLayout.Rect(x, row * 24, itemWidth, 19));
                x += itemWidth + 6;
            }
            start = end;
        }
        return result;
    }

    private int textWidth(String label) {
        String translated = ModernFormI18n.tr(label);
        return font == null ? translated.length() * 6 : font.getStringWidth(translated);
    }

    static int fittedMenuWidth(int available, int titleWidth, int[] labelWidths) {
        int width = titleWidth + 16;
        for (int labelWidth : labelWidths) width = Math.max(width, labelWidth + 28);
        return Math.max(1, Math.min(available, width));
    }

    public int contentBottom() { return footer.y - 5; }

    private static int defaultOrder(String id) {
        switch (id) {
            case "category_add": return 0;
            case "category_child": return 1;
            case "category_manage": return 2;
            case "add": return 3;
            case "copy": return 4;
            case "rename": return 5;
            default: return 6;
        }
    }
    public int treeHeight() { return bounds == null ? 1 : Math.max(1, contentBottom() - bounds.y - 58); }
    public void manageCategories() {
        context(bounds.right() - 6, bounds.y + 30, "category_add", "category_child", "category_rename",
                "category_up", "category_down", "category_copy", "expand", "collapse", "category_delete");
    }
    public boolean inTree(int x, int y) {
        return bounds != null && x >= bounds.x + 5 && x < bounds.right() - 5
                && y >= bounds.y + 56 && y < contentBottom();
    }

    public void draw(int x, int y) {
        ModernUiRenderer.beginClip(bounds);
        ModernUiRenderer.drawText(font, "gui.modern.nav.title", bounds.x + 9, bounds.y + 10,
                ModernUiRenderer.TEXT, Math.max(1, bounds.width - 40));
        button(more, "…", false, true, false, x, y);
        ModernUiRenderer.drawSubtlePanel(footer.x, footer.y, footer.width, footer.height, 7,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "gui.modern.nav.actions", footer.x + 8, footer.y + 9,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, footer.width - 36));
        button(customize, "", false, true, false, x, y);
        ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.SETTINGS, customize.x + 3, customize.y + 3, 14,
                ModernUiRenderer.SUBTLE_TEXT);
        for (Hit h : footerHits) button(h.rect, h.action.label, h.action.danger,
                h.action.enabled.getAsBoolean(), "add".equals(h.action.id), x, y);
        ModernUiRenderer.endClip();
    }

    public void context(int x, int y, String... ids) {
        menu.clear();
        for (String id : ids) if (actions.containsKey(id)) menu.add(actions.get(id));
        open = true; customizing = false; scroll = 0; menuX = x; menuY = y;
    }

    public void prompt(String title, String value, Consumer<String> accept) {
        close();
        this.inputTitle = title;
        this.accept = accept;
        input = new GuiTextField(0, font, 0, 0, 1, 18);
        input.setMaxStringLength(96);
        input.setText(value == null ? "" : value);
        input.setFocused(true);
        input.setCursorPositionEnd();
        input.setSelectionPos(0);
    }

    public void confirm(String title, Runnable run) {
        close();
        menu.clear();
        menu.add(new Action("confirm", "gui.modern.nav.confirm_delete", true, () -> true, run));
        open = true; customizing = false; scroll = 0;
        menuX = bounds.x + 6; menuY = bounds.y + 30;
    }

    public void drawOverlay(int x, int y) {
        if (bounds == null || (!open && input == null)) return;
        menuHits.clear();
        int[] labelWidths = new int[menu.size()];
        for (int i = 0; i < menu.size(); i++) labelWidths[i] = textWidth(menu.get(i).label);
        String title = input != null ? inputTitle
                : customizing ? "gui.modern.nav.customize" : "gui.modern.nav.actions";
        int width = input != null ? Math.max(1, bounds.width - 12)
                : fittedMenuWidth(bounds.width - 12, textWidth(title), labelWidths);
        int height = input != null ? Math.min(90, bounds.height - 12)
                : Math.min(Math.max(32, bounds.height - 12), 28 + menu.size() * 23);
        int px = Math.max(bounds.x + 6, Math.min(menuX, bounds.right() - width - 6));
        int py = Math.max(bounds.y + 6, Math.min(menuY, bounds.bottom() - height - 6));
        popup = new ModernMainLayout.Rect(px, py, width, height);
        ModernUiRenderer.drawSubtlePanel(px, py, width, height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        ModernUiRenderer.beginClip(popup);
        ModernUiRenderer.drawText(font, title, px + 8, py + 8,
                ModernUiRenderer.TEXT, Math.max(1, width - 16));
        if (input != null) {
            inputBounds = new ModernMainLayout.Rect(px + 7, py + 26, Math.max(1, width - 14), 20);
            input.x = inputBounds.x + 4; input.y = inputBounds.y + 5;
            input.width = Math.max(1, inputBounds.width - 8); input.height = 12;
            ModernUiRenderer.drawSubtlePanel(inputBounds.x, inputBounds.y, inputBounds.width, 20, 4,
                    ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER);
            input.setEnableBackgroundDrawing(false);
            ModernUiRenderer.drawTextField(input);
            acceptBounds = new ModernMainLayout.Rect(px + 7, py + 54, Math.max(1, width - 14), 20);
            button(acceptBounds, "gui.modern.nav.accept", false, !input.getText().trim().isEmpty(), true, x, y);
        } else {
            int count = Math.max(0, (height - 28) / 23);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, menu.size() - count)));
            for (int i = scroll; i < Math.min(menu.size(), scroll + count); i++) {
                Action a = menu.get(i);
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(px + 5, py + 25 + (i - scroll) * 23,
                        Math.max(1, width - 10), 20);
                drawMenuAction(row, a, x, y);
                menuHits.add(new Hit(a, row));
            }
        }
        ModernUiRenderer.endClip();
    }

    private void drawMenuAction(ModernMainLayout.Rect row, Action action, int x, int y) {
        boolean selected = Boolean.TRUE.equals(pins.get(action.id));
        boolean enabled = customizing || action.enabled.getAsBoolean();
        boolean hovered = row.contains(x, y);
        int fill = selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SURFACE;
        int border = selected ? ModernUiRenderer.ACCENT
                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4, fill, border);
        int color = !enabled ? ModernUiRenderer.DISABLED_TEXT
                : !customizing && action.danger ? ModernUiRenderer.DANGER : ModernUiRenderer.TEXT;
        ModernUiRenderer.drawText(font, action.label, row.x + 9, row.y + 6,
                ModernUiRenderer.readableText(color, fill), Math.max(1, row.width - 18));
    }

    public boolean mouseClicked(int x, int y, int button) {
        if (input != null) {
            if (button == 0 && contains(acceptBounds, x, y)) submit();
            else if (button == 0 && contains(inputBounds, x, y)) input.mouseClicked(x, y, button);
            else if (!contains(popup, x, y)) close();
            return true;
        }
        if (open) {
            if (button == 0) for (Hit h : menuHits) if (h.rect.contains(x, y)) {
                if (customizing) { pins.put(h.action.id, !Boolean.TRUE.equals(pins.get(h.action.id))); save(); }
                else if (h.action.enabled.getAsBoolean()) { close(); h.action.run.run(); }
                return true;
            }
            close(); return true;
        }
        if (button != 0) return false;
        if (contains(more, x, y) || contains(customize, x, y)) {
            customizing = contains(customize, x, y);
            menu.clear();
            for (Action a : actions.values()) {
                boolean visible = false;
                for (Hit h : footerHits) if (h.action.id.equals(a.id)) visible = true;
                if (customizing || !visible) menu.add(a);
            }
            open = true; scroll = 0; menuX = bounds.right(); menuY = more.bottom() + 3;
            return true;
        }
        for (Hit h : footerHits) if (h.rect.contains(x, y)) {
            if (h.action.enabled.getAsBoolean()) h.action.run.run();
            return true;
        }
        return contains(footer, x, y);
    }

    public boolean keyTyped(char character, int key) {
        if (input == null && !open) return false;
        if (key == Keyboard.KEY_ESCAPE) close();
        else if (input != null) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) submit();
            else input.textboxKeyTyped(character, key);
        }
        return true;
    }
    public boolean wheel(int delta) {
        if (!open && input == null) return false;
        if (delta != 0) scroll = Math.max(0, scroll + (delta > 0 ? -1 : 1));
        return true;
    }
    public boolean isOpen() { return open || input != null; }
    public void close() { open = false; input = null; accept = null; popup = null; menuHits.clear(); }
    private void submit() {
        String value = input.getText().trim();
        if (value.isEmpty()) return;
        Consumer<String> callback = accept;
        close(); callback.accept(value);
    }
    private void button(ModernMainLayout.Rect r, String label, boolean danger, boolean enabled,
            boolean primary, int x, int y) {
        GuiButton b = new GuiButton(0, r.x, r.y, r.width, r.height, ModernFormI18n.tr(label));
        b.enabled = enabled;
        ModernRuleEditorUi.drawButton(font, b, x, y, danger ? ModernRuleEditorUi.ButtonTone.DANGER
                : primary ? ModernRuleEditorUi.ButtonTone.PRIMARY : ModernRuleEditorUi.ButtonTone.DEFAULT);
    }
    private static boolean contains(ModernMainLayout.Rect r, int x, int y) { return r != null && r.contains(x, y); }
    private Path file() { return Paths.get(ModConfig.CONFIG_DIR, "navigation_" + key + ".json"); }
    private void load() {
        if (!Files.exists(file())) return;
        try (Reader reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);
            if (root != null) for (String id : new ArrayList<>(pins.keySet()))
                if (root.has(id) && root.get(id).isJsonPrimitive()) pins.put(id, root.get(id).getAsBoolean());
        } catch (Exception e) { zszlScriptMod.LOGGER.warn("Failed to load navigation preferences: " + key, e); }
    }
    private void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { new Gson().toJson(pins, writer); }
        } catch (Exception e) { zszlScriptMod.LOGGER.warn("Failed to save navigation preferences: " + key, e); }
    }
    private static final class Action {
        final String id, label; final boolean danger; final BooleanSupplier enabled; final Runnable run;
        Action(String id, String label, boolean danger, BooleanSupplier enabled, Runnable run) {
            this.id = id; this.label = label; this.danger = danger; this.enabled = enabled; this.run = run;
        }
    }
    private static final class Hit {
        final Action action; final ModernMainLayout.Rect rect;
        Hit(Action action, ModernMainLayout.Rect rect) { this.action = action; this.rect = rect; }
    }
}
