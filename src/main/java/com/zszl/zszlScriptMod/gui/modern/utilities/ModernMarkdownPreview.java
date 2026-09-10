package com.zszl.zszlScriptMod.gui.modern.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Lightweight, safe Markdown presentation for the in-game notebook.
 *
 * <p>The editor keeps the original Markdown source intact. This class only
 * produces a visual projection, so malformed or partially typed Markdown can
 * never break the screen.</p>
 */
public final class ModernMarkdownPreview {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$", Pattern.UNICODE_CASE);
    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern EMPHASIS = Pattern.compile("(`+|\\*\\*|__|~~|\\*|_)(.+?)\\1");
    private static final int LINE_HEIGHT = 12;

    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private final List<PreviewLine> lines = new ArrayList<PreviewLine>();
    private ModernMainLayout.Rect bounds;
    private String cachedMarkdown = "";
    private int cachedWidth = -1;
    private int scroll;
    private int maxScroll;

    private static final class PreviewLine {
        final String text;
        final int color;
        final boolean code;

        PreviewLine(String text, int color, boolean code) {
            this.text = text;
            this.color = color;
            this.code = code;
        }
    }

    public void draw(FontRenderer font, ModernMainLayout.Rect target, String markdown, int mouseX, int mouseY) {
        bounds = target;
        if (target == null || target.width <= 0 || target.height <= 0 || font == null) {
            return;
        }
        String source = markdown == null ? "" : markdown;
        int textWidth = Math.max(20, target.width - 24);
        if (!source.equals(cachedMarkdown) || textWidth != cachedWidth) {
            cachedMarkdown = source;
            cachedWidth = textWidth;
            rebuild(font, source, textWidth);
        }
        maxScroll = Math.max(0, lines.size() - Math.max(1, (target.height - 16) / LINE_HEIGHT));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ModernUiRenderer.drawSubtlePanel(target.x, target.y, target.width, target.height, 5,
                0xFF151E26, ModernUiRenderer.BORDER_SUBTLE);
        ModernMainLayout.Rect clip = new ModernMainLayout.Rect(target.x + 10, target.y + 8,
                Math.max(1, target.width - 20 - ModernHoverScrollbar.GUTTER), Math.max(1, target.height - 16));
        ModernUiRenderer.beginClip(clip);
        try {
            if (lines.isEmpty()) {
                font.drawString("开始编写 Markdown…", clip.x, clip.y,
                        ModernUiRenderer.readableText(ModernUiRenderer.MUTED_TEXT, 0xFF151F28));
            } else {
                int first = Math.min(scroll, Math.max(0, lines.size() - 1));
                int last = Math.min(lines.size(), first + Math.max(1, clip.height / LINE_HEIGHT) + 1);
                for (int index = first; index < last; index++) {
                    PreviewLine line = lines.get(index);
                    int y = clip.y + (index - scroll) * LINE_HEIGHT;
                    if (line.code) {
                        Gui.drawRect(clip.x - 4, y - 2, clip.right(), y + LINE_HEIGHT - 1, ModernUiRenderer.SURFACE);
                    }
                    font.drawString(line.text, clip.x, y,
                            ModernUiRenderer.readableText(line.color, 0xFF151F28));
                }
            }
        } finally {
            ModernUiRenderer.endClip();
        }
        scrollbar.draw(target, scroll, maxScroll, Math.max(1, target.height - 16),
                Math.max(1, lines.size() * LINE_HEIGHT), mouseX, mouseY, value -> scroll = value);
    }

    public boolean mouseWheel(int wheel, int mouseX, int mouseY) {
        if (bounds == null || !bounds.contains(mouseX, mouseY) || wheel == 0) {
            return false;
        }
        int notches = Math.max(1, Math.abs(wheel) >= 120 ? Math.abs(wheel) / 120 : Math.abs(wheel));
        scroll = Math.max(0, Math.min(maxScroll, scroll + (wheel > 0 ? -notches * 2 : notches * 2)));
        return true;
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY) && scrollbar.beginDrag(mouseX, mouseY);
    }

    public boolean mouseClickMove(int mouseX, int mouseY) {
        return scrollbar.isDragging() && scrollbar.applyDrag(mouseX, mouseY);
    }

    public boolean mouseReleased() {
        boolean handled = scrollbar.isDragging();
        scrollbar.endDrag();
        return handled;
    }

    private void rebuild(FontRenderer font, String markdown, int width) {
        lines.clear();
        boolean fenced = false;
        String[] sourceLines = markdown.replace("\r", "").split("\n", -1);
        for (int i = 0; i < sourceLines.length; i++) {
            String raw = sourceLines[i];
            String trimmed = raw.trim();
            if (trimmed.startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                addWrapped(font, "  " + raw, width, ModernUiRenderer.SUBTLE_TEXT, true);
                continue;
            }
            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                addWrapped(font, "◆ " + inline(heading.group(2)), width, ModernUiRenderer.TEXT, false);
                continue;
            }
            if (trimmed.matches("(?:-{3,}|\\*{3,}|_{3,})")) {
                addWrapped(font, "────────────────────────", width, ModernUiRenderer.ACCENT_DIM, false);
                continue;
            }
            if (isTableRow(trimmed)) {
                if (isTableSeparator(trimmed)) {
                    continue;
                }
                addWrapped(font, "│ " + tableRow(trimmed) + " │", width, ModernUiRenderer.SUBTLE_TEXT, false);
                continue;
            }
            if (trimmed.isEmpty()) {
                lines.add(new PreviewLine("", ModernUiRenderer.SUBTLE_TEXT, false));
                continue;
            }
            int color = ModernUiRenderer.TEXT;
            String display = inline(trimmed);
            if (display.startsWith(">")) {
                display = "│ " + inline(display.substring(1).trim());
                color = ModernUiRenderer.SUBTLE_TEXT;
            } else if (display.matches("^[-*+]\\s+.*") || display.matches("^\\d+[.)]\\s+.*")) {
                display = "• " + display.replaceFirst("^(?:[-*+]\\s+|\\d+[.)]\\s+)", "");
                color = ModernUiRenderer.TEXT;
            }
            addWrapped(font, display, width, color, false);
        }
        if (lines.size() == 1 && lines.get(0).text.isEmpty()) {
            lines.clear();
        }
        scroll = Math.min(scroll, Math.max(0, lines.size() - 1));
    }

    private void addWrapped(FontRenderer font, String text, int width, int color, boolean code) {
        String safe = text == null ? "" : text.replace('\u00A7', ' ');
        if (safe.isEmpty()) {
            lines.add(new PreviewLine("", color, code));
            return;
        }
        String remaining = safe;
        while (!remaining.isEmpty()) {
            String part = font.trimStringToWidth(remaining, Math.max(12, width));
            if (part == null || part.isEmpty()) {
                part = remaining.substring(0, 1);
            }
            lines.add(new PreviewLine(part, color, code));
            remaining = remaining.substring(Math.min(part.length(), remaining.length()));
        }
    }

    private static boolean isTableRow(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.length() > 2
                && line.indexOf('|', 1) > 0;
    }

    private static boolean isTableSeparator(String line) {
        String value = line.replace("|", "").replace(":", "").replace("-", "").replace(" ", "");
        return value.isEmpty() && line.replace("|", "").trim().length() > 0;
    }

    private static String tableRow(String line) {
        String value = line.substring(1, line.length() - 1);
        String[] cells = value.split("\\|", -1);
        StringBuilder result = new StringBuilder();
        for (String cell : cells) {
            if (result.length() > 0) result.append("  │  ");
            result.append(inline(cell.trim()));
        }
        return result.toString();
    }

    private static String inline(String value) {
        String result = value == null ? "" : value;
        result = LINK.matcher(result).replaceAll("$1");
        Matcher emphasis = EMPHASIS.matcher(result);
        String previous;
        do {
            previous = result;
            result = emphasis.reset(result).replaceAll("$2");
        } while (!previous.equals(result));
        return result.replace("\\\\", "\\").replace("  ", " ");
    }
}
