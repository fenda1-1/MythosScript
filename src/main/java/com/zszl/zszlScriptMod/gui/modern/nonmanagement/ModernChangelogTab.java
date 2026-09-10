package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout.Rect;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.UpdateChecker;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import org.lwjgl.input.Keyboard;

/** Native release reader with independent outline and document scrolling. */
public final class ModernChangelogTab implements ModernSettingsTab {
    private static final class Line {
        final ITextComponent text;
        final ChangelogDocument.Kind kind;
        final int y, height;
        Line(ITextComponent text, ChangelogDocument.Kind kind, int y, int height) {
            this.text = text; this.kind = kind; this.y = y; this.height = height;
        }
    }
    private static final class Heading {
        final String title;
        final int y, level;
        boolean expanded = true;
        Heading(String title, int y, int level) { this.title = title; this.y = y; this.level = level; }
    }
    private static final class Link {
        final Rect bounds;
        final String url;
        Link(Rect bounds, String url) { this.bounds = bounds; this.url = url; }
    }

    private final ModernHoverScrollbar readerBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar outlineBar = new ModernHoverScrollbar();
    private final List<Line> lines = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private Rect bounds, reader, outline, refreshButton, copyButton, outlineButton;
    private FontRenderer font;
    private String source, message = "";
    private long messageUntil;
    private int layoutWidth = -1, fontHeight, scroll, maxScroll, outlineScroll, maxOutlineScroll, totalHeight;
    private boolean initialized, compact, showOutline;

    @Override public void ensureInitialized(FontRenderer fontRenderer) {
        if (font != fontRenderer || fontHeight != fontRenderer.FONT_HEIGHT) layoutWidth = -1;
        font = fontRenderer;
        fontHeight = fontRenderer.FONT_HEIGHT;
        if (!initialized) {
            initialized = true;
            UpdateChecker.fetchVersionAndChangelog();
        }
    }
    @Override public void updateScreen() { }

    @Override public void draw(FontRenderer fontRenderer, Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        bounds = contentBounds;
        compact = bounds.width < 520;
        links.clear();
        ModernUiRenderer.beginClip(bounds);
        try {
            panel(bounds);
            drawHeader(mouseX, mouseY);
            int bodyY = bounds.y + 79;
            int bodyHeight = Math.max(1, bounds.bottom() - bodyY - 29);
            int outlineWidth = compact ? 0 : Math.min(186, bounds.width / 3);
            reader = new Rect(bounds.x + 12 + outlineWidth, bodyY,
                    Math.max(1, bounds.width - 24 - outlineWidth), bodyHeight);
            outline = !compact || showOutline ? new Rect(bounds.x + 12, bodyY,
                    compact ? Math.max(1, bounds.width - 24) : outlineWidth - 10, bodyHeight) : null;
            String current = UpdateChecker.hasChangelog() ? UpdateChecker.changelogContent : "";
            if (!Objects.equals(source, current) || layoutWidth != reader.width) {
                source = current;
                layoutWidth = reader.width;
                rebuild();
            }
            maxScroll = Math.max(0, totalHeight - reader.height);
            scroll = clamp(scroll, maxScroll);
            if (!compact || !showOutline) drawReader(mouseX, mouseY);
            else readerBar.idle();
            if (outline != null) drawOutline(mouseX, mouseY);
            else outlineBar.idle();
            drawFooter();
        } finally {
            ModernUiRenderer.endClip();
        }
    }

    private void drawHeader(int mouseX, int mouseY) {
        text("§l" + tr("title"), bounds.x + 16, bounds.y + 12, ModernUiRenderer.TEXT, bounds.width - 32);
        String version = tr("current", zszlScriptMod.VERSION);
        if (UpdateChecker.hasChangelog()) version += "    ·    " + tr("latest", UpdateChecker.latestVersion);
        text(version, bounds.x + 16, bounds.y + 29, ModernUiRenderer.SUBTLE_TEXT, bounds.width - 32);
        int buttonWidth = Math.min(96, Math.max(1, (bounds.width - 40) / 3));
        refreshButton = new Rect(bounds.x + 12, bounds.y + 47, buttonWidth, 23);
        copyButton = new Rect(refreshButton.right() + 6, refreshButton.y, buttonWidth, 23);
        outlineButton = compact ? new Rect(copyButton.right() + 6, copyButton.y, buttonWidth, 23) : null;
        button(refreshButton, tr(UpdateChecker.isRefreshing() ? "refreshing" : "refresh"),
                !UpdateChecker.isRefreshing(), mouseX, mouseY);
        button(copyButton, tr("copy"), UpdateChecker.hasChangelog(), mouseX, mouseY);
        if (outlineButton != null) button(outlineButton, tr(showOutline ? "read" : "outline"), true, mouseX, mouseY);
    }

    private void rebuild() {
        List<Heading> previousHeadings = new ArrayList<>(headings);
        lines.clear();
        headings.clear();
        int y = 10;
        for (ChangelogDocument.Block block : ChangelogDocument.parse(source)) {
            if (block.kind == ChangelogDocument.Kind.HEADING) {
                y += 10;
                Heading heading = new Heading(block.title(), y, block.level);
                int index = headings.size();
                if (index < previousHeadings.size()) {
                    Heading previous = previousHeadings.get(index);
                    if (previous.title.equals(heading.title) && previous.level == heading.level)
                        heading.expanded = previous.expanded;
                }
                headings.add(heading);
            }
            if (block.kind == ChangelogDocument.Kind.SPACE || block.kind == ChangelogDocument.Kind.RULE) {
                lines.add(new Line(new TextComponentString(""), block.kind, y, 10));
                y += 10;
                continue;
            }
            ITextComponent component = new TextComponentString("");
            for (ChangelogDocument.Span span : block.spans) {
                Style style = new Style().setBold(span.bold || block.kind == ChangelogDocument.Kind.HEADING)
                        .setItalic(span.italic).setUnderlined(!span.url.isEmpty());
                if (!span.url.isEmpty()) style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, span.url));
                component.appendSibling(new TextComponentString(span.text).setStyle(style));
            }
            int lineHeight = fontHeight + (block.kind == ChangelogDocument.Kind.HEADING ? 15 : 7);
            for (ITextComponent wrapped : GuiUtilRenderComponents.splitText(component,
                    Math.max(1, reader.width - 40), font, false, true)) {
                lines.add(new Line(wrapped, block.kind, y, lineHeight));
                y += lineHeight;
            }
            y += 3;
        }
        totalHeight = y + 12;
    }

    private void drawReader(int mouseX, int mouseY) {
        panel(reader);
        ModernUiRenderer.beginClip(reader);
        try {
            if (source == null || source.trim().isEmpty()) {
                String state = UpdateChecker.isRefreshing() ? "loading" : UpdateChecker.didLastRefreshFail() ? "failed" : "empty";
                int y = reader.y + 25;
                for (String line : font.listFormattedStringToWidth(tr(state), Math.max(1, reader.width - 32))) {
                    text(line, reader.x + 16, y, ModernUiRenderer.SUBTLE_TEXT, reader.width - 32);
                    y += fontHeight + 7;
                }
            } else for (Line line : lines) {
                int y = reader.y + line.y - scroll;
                if (y + line.height < reader.y || y >= reader.bottom()) continue;
                int color = ModernUiRenderer.TEXT;
                if (line.kind == ChangelogDocument.Kind.HEADING) {
                    ModernUiRenderer.drawRoundedRect(reader.x + 9, y, reader.width - 27, line.height, 4,
                            ModernUiRenderer.SHELL_RAISED);
                    ModernUiRenderer.drawRoundedRect(reader.x + 9, y + 4, 3, line.height - 8, 1, ModernUiRenderer.ACCENT);
                } else if (line.kind == ChangelogDocument.Kind.QUOTE || line.kind == ChangelogDocument.Kind.CODE) {
                    ModernUiRenderer.drawRoundedRect(reader.x + 9, y, reader.width - 27, line.height, 2,
                            ModernUiRenderer.SHELL_RAISED);
                    color = ModernUiRenderer.SUBTLE_TEXT;
                } else if (line.kind == ChangelogDocument.Kind.RULE) {
                    ModernUiRenderer.drawDivider(reader.x + 16, y + 4, reader.width - 40, ModernUiRenderer.BORDER_SUBTLE);
                }
                int x = reader.x + 18;
                for (ITextComponent part : line.text) {
                    String ownText = part.getUnformattedComponentText();
                    if (ownText.isEmpty()) continue;
                    String formatted = formatOwnText(part);
                    int width = font.getStringWidth(formatted);
                    ClickEvent click = part.getStyle().getClickEvent();
                    int textY = y + (line.height - fontHeight) / 2;
                    text(formatted, x, textY, click == null ? color : ModernUiRenderer.ACCENT, reader.right() - x - 16);
                    if (click != null && width > 0) links.add(new Link(new Rect(x, textY, width, fontHeight + 2), click.getValue()));
                    x += width;
                }
            }
        } finally { ModernUiRenderer.endClip(); }
        readerBar.draw(reader, scroll, maxScroll, reader.height, totalHeight, mouseX, mouseY, value -> scroll = value);
    }

    static String formatOwnText(ITextComponent part) {
        String ownText = part.getUnformattedComponentText();
        return ownText.isEmpty() ? "" : part.getStyle().getFormattingCode() + ownText + "§r";
    }

    private List<Integer> visibleHeadings() {
        List<Integer> visible = new ArrayList<>();
        int hiddenBelow = Integer.MAX_VALUE;
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            if (heading.level > hiddenBelow) continue;
            hiddenBelow = Integer.MAX_VALUE;
            visible.add(i);
            if (!heading.expanded) hiddenBelow = heading.level;
        }
        return visible;
    }

    private boolean hasChildren(int index) {
        return index + 1 < headings.size() && headings.get(index + 1).level > headings.get(index).level;
    }

    private void drawOutline(int mouseX, int mouseY) {
        panel(outline);
        text(tr("outline"), outline.x + 10, outline.y + 11, ModernUiRenderer.SUBTLE_TEXT, outline.width - 24);
        Rect list = outlineList();
        List<Integer> visible = visibleHeadings();
        int active = -1;
        for (int index : visible) if (headings.get(index).y <= scroll + 16) active = index;
        maxOutlineScroll = Math.max(0, visible.size() * 28 - list.height);
        outlineScroll = clamp(outlineScroll, maxOutlineScroll);
        ModernUiRenderer.beginClip(list);
        try {
            if (headings.isEmpty()) text(tr("no_outline"), list.x + 8, list.y + 10,
                    ModernUiRenderer.SUBTLE_TEXT, list.width - 18);
            for (int rowIndex = 0; rowIndex < visible.size(); rowIndex++) {
                int index = visible.get(rowIndex);
                Heading heading = headings.get(index);
                Rect row = new Rect(list.x + 3, list.y + rowIndex * 28 - outlineScroll, list.width - 18, 26);
                if (row.bottom() <= list.y || row.y >= list.bottom()) continue;
                if (index == active || row.contains(mouseX, mouseY)) ModernUiRenderer.drawRoundedRect(
                        row.x, row.y, row.width, row.height, 4, ModernUiRenderer.SURFACE_HOVER);
                int inset = 8 + Math.min(5, heading.level - 1) * 7;
                text(heading.title, row.x + inset, row.y + 8,
                        index == active ? ModernUiRenderer.ACCENT : ModernUiRenderer.TEXT, row.width - inset - 24);
                if (hasChildren(index)) ModernUiRenderer.drawChevron(row.right() - 14, row.y + 8,
                        !heading.expanded, ModernUiRenderer.SUBTLE_TEXT);
            }
        } finally { ModernUiRenderer.endClip(); }
        outlineBar.draw(list, outlineScroll, maxOutlineScroll, list.height, visible.size() * 28,
                mouseX, mouseY, value -> outlineScroll = value);
    }

    private Rect outlineList() { return new Rect(outline.x + 4, outline.y + 30, outline.width - 8, Math.max(1, outline.height - 36)); }

    private void drawFooter() {
        String status = tr(UpdateChecker.isRefreshing() ? "loading" : UpdateChecker.didLastRefreshFail() ? "failed" : "ready");
        if (System.currentTimeMillis() < messageUntil) status = message;
        text(status, bounds.x + 16, bounds.bottom() - 18, ModernUiRenderer.SUBTLE_TEXT, bounds.width - 85);
        text((maxScroll == 0 ? 100 : (int) (scroll * 100L / maxScroll)) + "%", bounds.right() - 53,
                bounds.bottom() - 18, ModernUiRenderer.ACCENT, 40);
    }

    private void button(Rect rect, String label, boolean enabled, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                enabled && rect.contains(mouseX, mouseY) ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER_SUBTLE);
        text(label, rect.x + Math.max(5, (rect.width - font.getStringWidth(label)) / 2), rect.y + 7,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, rect.width - 10);
    }
    private void panel(Rect rect) { ModernUiRenderer.drawPanel(rect.x, rect.y, rect.width, rect.height, 6,
            ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE); }
    private void text(String value, int x, int y, int color, int width) {
        ModernUiRenderer.drawText(font, value, x, y, color, Math.max(1, width));
    }
    private static String tr(String key, Object... args) { return I18n.format("gui.changelog." + key, args); }
    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }

    @Override public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!containsContent(mouseX, mouseY)) return false;
        if (mouseButton != 0) return true;
        if (refreshButton != null && refreshButton.contains(mouseX, mouseY)) {
            if (!UpdateChecker.isRefreshing()) UpdateChecker.forceRefresh();
        } else if (copyButton != null && copyButton.contains(mouseX, mouseY)) {
            if (UpdateChecker.hasChangelog()) {
                GuiScreen.setClipboardString(UpdateChecker.changelogContent);
                message = tr("copied"); messageUntil = System.currentTimeMillis() + 2500;
            }
        } else if (outlineButton != null && outlineButton.contains(mouseX, mouseY)) showOutline = !showOutline;
        else if (outlineBar.beginDrag(mouseX, mouseY) || readerBar.beginDrag(mouseX, mouseY)) return true;
        else if (outline != null && outlineList().contains(mouseX, mouseY)) {
            Rect list = outlineList();
            List<Integer> visible = visibleHeadings();
            int rowIndex = (mouseY - list.y + outlineScroll) / 28;
            if (rowIndex >= 0 && rowIndex < visible.size()) {
                int index = visible.get(rowIndex);
                Rect row = new Rect(list.x + 3, list.y + rowIndex * 28 - outlineScroll, list.width - 18, 26);
                if (!row.contains(mouseX, mouseY)) return true;
                if (hasChildren(index) && mouseX >= row.right() - 24) {
                    headings.get(index).expanded = !headings.get(index).expanded;
                    maxOutlineScroll = Math.max(0, visibleHeadings().size() * 28 - list.height);
                    outlineScroll = clamp(outlineScroll, maxOutlineScroll);
                    outlineBar.endDrag();
                    outlineBar.idle();
                } else {
                    scroll = clamp(headings.get(index).y - 10, maxScroll);
                    if (compact) showOutline = false;
                }
            }
        } else if (reader != null && reader.contains(mouseX, mouseY) && (!compact || !showOutline)) {
            for (Link link : links) if (link.bounds.contains(mouseX, mouseY)) {
                message = ModernNonManagementRoutes.openExternal(link.url);
                messageUntil = System.currentTimeMillis() + 4000;
                break;
            }
        }
        return true;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long time) {
        return button == 0 && (readerBar.applyDrag(x, y) || outlineBar.applyDrag(x, y));
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        boolean captured = readerBar.isDragging() || outlineBar.isDragging();
        if (button == 0) { readerBar.endDrag(); outlineBar.endDrag(); }
        return captured;
    }
    @Override public boolean handleMouseWheel(int wheel, int x, int y) {
        if (wheel == 0 || !containsContent(x, y)) return false;
        if (outline != null && outline.contains(x, y)) outlineScroll = clamp(outlineScroll - Integer.signum(wheel) * 56, maxOutlineScroll);
        else scroll = clamp(scroll - Integer.signum(wheel) * 48, maxScroll);
        return true;
    }
    @Override public boolean handleMouseWheel(int wheel) { return false; }
    @Override public boolean keyTyped(char typedChar, int keyCode) {
        if (reader == null) return false;
        int page = Math.max(16, reader.height - 24);
        if (keyCode == Keyboard.KEY_HOME) scroll = 0;
        else if (keyCode == Keyboard.KEY_END) scroll = maxScroll;
        else if (keyCode == Keyboard.KEY_PRIOR) scroll = clamp(scroll - page, maxScroll);
        else if (keyCode == Keyboard.KEY_NEXT) scroll = clamp(scroll + page, maxScroll);
        else return false;
        return true;
    }
    @Override public boolean handleEscape() { if (compact && showOutline) { showOutline = false; return true; } return false; }
    @Override public boolean containsContent(int x, int y) { return bounds != null && bounds.contains(x, y); }
    @Override public String getHoveredTooltip(int x, int y) {
        if (reader != null && reader.contains(x, y)) for (Link link : links) if (link.bounds.contains(x, y)) return link.url;
        return "";
    }
    @Override public void discardDraft() { readerBar.endDrag(); outlineBar.endDrag(); }
}
