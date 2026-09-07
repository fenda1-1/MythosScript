package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, rendering-independent Markdown model for the existing release feed. */
final class ChangelogDocument {
    enum Kind { TEXT, HEADING, QUOTE, LIST, CODE, RULE, SPACE }

    static final class Span {
        final String text, url;
        final boolean bold, italic, code;
        Span(String text, String url, boolean bold, boolean italic, boolean code) {
            this.text = text;
            this.url = url;
            this.bold = bold;
            this.italic = italic;
            this.code = code;
        }
    }

    static final class Block {
        final Kind kind;
        final int level;
        final List<Span> spans;
        Block(Kind kind, int level, List<Span> spans) {
            this.kind = kind;
            this.level = level;
            this.spans = Collections.unmodifiableList(spans);
        }
        String title() {
            StringBuilder result = new StringBuilder();
            for (Span span : spans) result.append(span.text);
            return result.toString();
        }
    }

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)(?:\\s+#+)?$");
    private static final Pattern INLINE = Pattern.compile(
            "\\[([^\\]]+)\\]\\(([^\\s)]+)\\)|\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*");

    static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        boolean fenced = false;
        for (String raw : (markdown == null ? "" : markdown).replace("\r", "").split("\n", -1)) {
            String line = raw.trim();
            if (line.startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            Kind kind = Kind.TEXT;
            int level = 0;
            Matcher heading = HEADING.matcher(line);
            if (fenced) {
                blocks.add(new Block(Kind.CODE, 0, Collections.singletonList(
                        new Span(raw, "", false, false, true))));
                continue;
            } else if (line.isEmpty()) kind = Kind.SPACE;
            else if (heading.matches()) {
                kind = Kind.HEADING;
                level = heading.group(1).length();
                line = heading.group(2);
            } else if (line.matches("(?:-{3,}|\\*{3,}|_{3,})")) kind = Kind.RULE;
            else if (line.startsWith(">")) {
                kind = Kind.QUOTE;
                line = line.substring(1).trim();
            } else if (line.matches("^(?:[-*+] |\\d+[.)] ).*")) {
                kind = Kind.LIST;
                line = line.replaceFirst("^[-*+] ", "• ");
            }
            blocks.add(new Block(kind, level, inline(line)));
        }
        return Collections.unmodifiableList(blocks);
    }

    static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = INLINE.matcher(text);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() > end) spans.add(new Span(text.substring(end, matcher.start()), "", false, false, false));
            if (matcher.group(1) != null) {
                spans.add(new Span(matcher.group(1), safeUrl(matcher.group(2)), false, false, false));
            } else {
                String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4) != null
                        ? matcher.group(4) : matcher.group(5) != null ? matcher.group(5) : matcher.group(6);
                spans.add(new Span(value, "", matcher.group(3) != null || matcher.group(4) != null,
                        matcher.group(6) != null, matcher.group(5) != null));
            }
            end = matcher.end();
        }
        if (end < text.length()) spans.add(new Span(text.substring(end), "", false, false, false));
        return spans;
    }

    private static String safeUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? value : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
