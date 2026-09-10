package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

public final class RuleSectionNavigation {
    private static final int GAP = 4;
    private static final int ICON_ONLY = 34;
    private static final int TOP_CHIP_HEIGHT = 26;
    private static final int SIDE_ROW_HEIGHT = 34;
    /** left 6 + icon 12 + gap 5 + right 8, matching draw(). */
    private static final int TEXT_PAD = 31;
    private static final int CHIP_CAP = 168;

    private final String[] labels;
    private final ModernUiRenderer.Icon[] icons;
    private boolean top = MainUiLayoutManager.isAutoFollowNavigationTop();
    private boolean detailed = MainUiLayoutManager.isAutoFollowNavigationText();
    private final ModernHoverScrollbar vertical = new ModernHoverScrollbar();
    private final ModernHoverScrollbar horizontal = new ModernHoverScrollbar(ModernHoverScrollbar.Axis.HORIZONTAL);
    private final List<Rect> hits = new ArrayList<>();
    private Rect rail, positionButton, detailButton;
    private int scroll, maxScroll;

    public RuleSectionNavigation(String[] labels, ModernUiRenderer.Icon[] icons) {
        this.labels = labels;
        this.icons = icons == null ? defaultIcons(labels.length) : icons;
    }

    private static ModernUiRenderer.Icon[] defaultIcons(int count) {
        ModernUiRenderer.Icon[] available = {ModernUiRenderer.Icon.SETTINGS, ModernUiRenderer.Icon.SEARCH,
                ModernUiRenderer.Icon.CONDITIONS, ModernUiRenderer.Icon.ROUTE, ModernUiRenderer.Icon.PROFILE,
                ModernUiRenderer.Icon.COMMAND, ModernUiRenderer.Icon.INFO};
        ModernUiRenderer.Icon[] result = new ModernUiRenderer.Icon[count];
        for (int i = 0; i < count; i++) {
            result[i] = available[i % available.length];
        }
        return result;
    }

    private ModernHoverScrollbar bar() {
        return top ? horizontal : vertical;
    }

    public Rect layout(FontRenderer font, Rect b) {
        boolean savedTop = MainUiLayoutManager.isAutoFollowNavigationTop();
        boolean savedDetailed = MainUiLayoutManager.isAutoFollowNavigationText();
        if (top != savedTop || detailed != savedDetailed) {
            top = savedTop;
            detailed = savedDetailed;
            scroll = 0;
            vertical.endDrag();
            horizontal.endDrag();
        }
        positionButton = new Rect(b.x + 8, b.y + 3, 76, 21);
        detailButton = new Rect(b.x + 89, b.y + 3, 84, 21);
        if (top) {
            rail = new Rect(b.x + 8, b.y + 30, Math.max(1, b.width - 16), 42);
            return new Rect(b.x, b.y + 76, b.width, Math.max(1, b.height - 76));
        }
        int railHeight = Math.max(1, b.height - 36);
        int contentHeight = SIDE_ROW_HEIGHT * labels.length + GAP * Math.max(0, labels.length - 1);
        boolean overflow = contentHeight > railHeight;
        int column = detailed ? maxDetailedChip(font) : ICON_ONLY;
        int width = column + (overflow ? ModernHoverScrollbar.GUTTER : 0);
        rail = new Rect(b.x + 4, b.y + 30, width, railHeight);
        return new Rect(rail.right() + 2, b.y + 26, Math.max(1, b.right() - rail.right() - 2),
                Math.max(1, b.height - 26));
    }

    /** Retained for callers that do not yet have a renderer at layout time. */
    public Rect layout(Rect b) {
        return layout(null, b);
    }

    public void draw(FontRenderer font, int selected, int mx, int my) {
        drawButton(font, positionButton, top ? "位置：顶部" : "位置：左侧", mx, my);
        drawButton(font, detailButton, detailed ? "显示：图文" : "显示：图标", mx, my);
        int extent = top ? rail.width : rail.height;
        int[] widths = top && detailed ? itemWidths(font) : null;
        int content = top
                ? (detailed ? total(widths, GAP) : ICON_ONLY * labels.length + GAP * Math.max(0, labels.length - 1))
                : SIDE_ROW_HEIGHT * labels.length + GAP * Math.max(0, labels.length - 1);
        maxScroll = Math.max(0, content - extent);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (selected >= 0 && selected < labels.length) {
            int start = top
                    ? (detailed ? offset(widths, selected, GAP) : selected * (ICON_ONLY + GAP))
                    : selected * (SIDE_ROW_HEIGHT + GAP);
            int size = top ? (detailed ? widths[selected] : ICON_ONLY) : SIDE_ROW_HEIGHT;
            if (start < scroll) {
                scroll = start;
            } else if (start + size > scroll + extent) {
                scroll = start + size - extent;
            }
            scroll = Math.max(0, Math.min(maxScroll, scroll));
        }
        int gutter = !top && maxScroll > 0 ? ModernHoverScrollbar.GUTTER : 0;
        hits.clear();
        ModernUiRenderer.beginClip(rail);
        for (int i = 0; i < labels.length; i++) {
            int rowWidth = top
                    ? (detailed ? widths[i] : ICON_ONLY)
                    : Math.max(1, rail.width - gutter);
            int offset = top
                    ? (detailed ? offset(widths, i, GAP) : i * (ICON_ONLY + GAP)) - scroll
                    : i * (SIDE_ROW_HEIGHT + GAP) - scroll;
            Rect r = top
                    ? new Rect(rail.x + offset, rail.y, rowWidth, TOP_CHIP_HEIGHT)
                    : new Rect(rail.x, rail.y + offset, rowWidth, SIDE_ROW_HEIGHT);
            hits.add(r);
            boolean hovered = rail.contains(mx, my) && r.contains(mx, my);
            int color = i == selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER;
            if (detailed) {
                ModernUiRenderer.drawSubtlePanel(r.x, r.y, r.width, r.height, 5,
                        i == selected ? ModernUiRenderer.ACCENT_DIM
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                        color);
                ModernUiRenderer.drawIcon(icons[i], r.x + 6, r.y + r.height / 2 - 6, 12, ModernUiRenderer.TEXT);
                ModernUiRenderer.drawText(font, ModernFormI18n.tr(labels[i]), r.x + 23, r.y + r.height / 2 - 4,
                        ModernUiRenderer.TEXT, r.width - TEXT_PAD);
            } else {
                int cx = r.x + r.width / 2, cy = r.y + r.height / 2, radius = hovered ? 12 : 10;
                ModernUiRenderer.drawRoundedRect(cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
                ModernUiRenderer.drawRoundedRect(cx - radius + 1, cy - radius + 1, radius * 2 - 2, radius * 2 - 2,
                        radius - 1, i == selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SHELL);
                int size = hovered ? 15 : 12;
                ModernUiRenderer.drawIcon(icons[i], cx - size / 2, cy - size / 2, size, ModernUiRenderer.TEXT);
            }
        }
        ModernUiRenderer.endClip();
        if (maxScroll > 0) {
            bar().draw(rail, scroll, maxScroll, extent, content, mx, my, v -> scroll = v);
        } else {
            bar().idle();
        }
    }

    /** Publishes the visible section/navigation targets for MCP callers. */
    public List<GuiElementInspector.GuiElementInfo> getMcpGuiElements(String pathPrefix, int selected) {
        List<GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        String base = normalizePrefix(pathPrefix);
        addMcpElement(result, base + "navigation/position", "位置：" + (top ? "顶部" : "左侧"),
                positionButton, "action", "", Collections.singletonList("click"), false);
        addMcpElement(result, base + "navigation/detail", "显示：" + (detailed ? "图文" : "图标"),
                detailButton, "action", "", Collections.singletonList("click"), false);
        for (int i = 0; i < hits.size(); i++) {
            Rect hit = hits.get(i);
            if (hit == null || rail == null || hit.right() <= rail.x || hit.x >= rail.right()
                    || hit.bottom() <= rail.y || hit.y >= rail.bottom()) continue;
            String label = i < labels.length ? ModernFormI18n.tr(labels[i]) : String.valueOf(i);
            addMcpElement(result, base + "navigation/section/" + i, label, hit,
                    "section", String.valueOf(i), Collections.singletonList("click"), i == selected);
        }
        return result;
    }

    private static void addMcpElement(List<GuiElementInspector.GuiElementInfo> result, String path, String text,
            Rect bounds, String controlType, String value, List<String> actions, boolean selected) {
        if (result == null || bounds == null || bounds.width <= 0 || bounds.height <= 0) return;
        result.add(new GuiElementInspector.GuiElementInfo(GuiElementInspector.ElementType.CUSTOM, path, text,
                bounds.x, bounds.y, bounds.width, bounds.height, Integer.MIN_VALUE, -1, controlType, value,
                true, "section".equals(controlType), actions, Collections.<String>emptyList()));
    }

    private static String normalizePrefix(String value) {
        String result = value == null ? "" : value.replace('\\', '/').trim();
        while (result.startsWith("/")) result = result.substring(1);
        return result.isEmpty() || result.endsWith("/") ? result : result + "/";
    }

    static int detailedChipWidth(FontRenderer font, String label) {
        return Math.max(ICON_ONLY, Math.min(CHIP_CAP, textWidth(font, visibleLabel(label)) + TEXT_PAD));
    }

    static int textWidth(FontRenderer font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (font != null) {
            int measured = font.getStringWidth(text);
            if (measured > 0) {
                return measured;
            }
        }
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 256 ? 6 : 9;
        }
        return width;
    }

    static String visibleLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if (!ModernFormI18n.isKey(label)) {
            return label;
        }
        try {
            String translated = ModernFormI18n.tr(label);
            if (translated == null || translated.isEmpty() || translated.equals(label)) {
                return "";
            }
            return translated;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private int maxDetailedChip(FontRenderer font) {
        int widest = ICON_ONLY;
        for (int i = 0; i < labels.length; i++) {
            widest = Math.max(widest, detailedChipWidth(font, labels[i]));
        }
        return widest;
    }

    private int[] itemWidths(FontRenderer font) {
        int[] result = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            result[i] = detailedChipWidth(font, labels[i]);
        }
        return result;
    }

    private static int total(int[] widths, int gap) {
        int result = gap * Math.max(0, widths.length - 1);
        for (int i = 0; i < widths.length; i++) {
            result += widths[i];
        }
        return result;
    }

    private static int offset(int[] widths, int index, int gap) {
        int result = 0;
        for (int i = 0; i < index; i++) {
            result += widths[i] + gap;
        }
        return result;
    }

    private void drawButton(FontRenderer font, Rect b, String label, int x, int y) {
        ModernUiRenderer.drawSubtlePanel(b.x, b.y, b.width, b.height, 4,
                b.contains(x, y) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, label, b.x + 6, b.y + 6, ModernUiRenderer.TEXT, b.width - 12);
    }

    public boolean contains(int x, int y) {
        return rail != null && (rail.contains(x, y) || positionButton.contains(x, y) || detailButton.contains(x, y));
    }

    public int click(int x, int y) {
        if (rail == null) {
            return -1;
        }
        if (positionButton.contains(x, y) || detailButton.contains(x, y)) {
            if (positionButton.contains(x, y)) {
                top = !top;
            } else {
                detailed = !detailed;
            }
            vertical.endDrag();
            horizontal.endDrag();
            scroll = 0;
            MainUiLayoutManager.setAutoFollowNavigation(top, detailed);
            return -1;
        }
        if (bar().beginDrag(x, y)) {
            return -1;
        }
        if (rail.contains(x, y)) {
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).contains(x, y)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean drag(int x, int y) {
        if (bar().isDragging()) {
            bar().applyDrag(x, y);
            return true;
        }
        return false;
    }

    public void release() {
        vertical.endDrag();
        horizontal.endDrag();
    }

    public boolean wheel(int wheel, int x, int y) {
        if (wheel == 0 || rail == null || !rail.contains(x, y)) {
            return false;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll + (wheel > 0 ? -32 : 32)));
        return true;
    }

    public String tooltip(int x, int y) {
        if (rail != null && rail.contains(x, y)) {
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).contains(x, y)) {
                    return ModernFormI18n.tr(labels[i]);
                }
            }
        }
        return "";
    }
}
