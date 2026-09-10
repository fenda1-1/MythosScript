package com.zszl.zszlScriptMod.gui.modern.profile;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/** Line-oriented editor buffer used by the native profile file overlay. */
public final class ProfileFileEditorBuffer {

    private static final Pattern JSON_LOCATION_PATTERN = Pattern.compile("line\\s+(\\d+)\\s+column\\s+(\\d+)");

    public static final class ValidationResult {
        public final boolean valid;
        public final String message;
        public final int line;
        public final int column;

        private ValidationResult(boolean valid, String message, int line, int column) {
            this.valid = valid;
            this.message = message == null ? "" : message;
            this.line = Math.max(1, line);
            this.column = Math.max(1, column);
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, "", 1, 1);
        }

        public static ValidationResult error(String message, int line, int column) {
            return new ValidationResult(false, message, line, column);
        }
    }

    private final List<String> lines = new ArrayList<String>();
    private String originalContent = "";
    private int cursorLine;
    private int cursorColumn;
    private boolean dirty;

    public ProfileFileEditorBuffer() {
        setContent("");
        dirty = false;
    }

    public void setContent(String content) {
        lines.clear();
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);
        for (String line : split) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        originalContent = getContent();
        cursorLine = 0;
        cursorColumn = 0;
        dirty = false;
        clampCursor();
    }

    public String getContent() {
        return join(lines);
    }

    public String originalContent() {
        return originalContent;
    }

    public void markSaved(String content) {
        originalContent = content == null ? "" : content;
        dirty = !originalContent.equals(getContent());
    }

    public boolean isDirty() {
        return dirty;
    }

    public int cursorLine() {
        return cursorLine;
    }

    public int cursorColumn() {
        return cursorColumn;
    }

    public int lineCount() {
        return lines.size();
    }

    public String line(int index) {
        if (index < 0 || index >= lines.size()) {
            return "";
        }
        return lines.get(index);
    }

    public List<String> lines() {
        return new ArrayList<String>(lines);
    }

    public void moveTo(int line, int column) {
        cursorLine = line;
        cursorColumn = column;
        clampCursor();
    }

    public void moveEnd() {
        clampCursor();
        cursorLine = lines.size() - 1;
        cursorColumn = currentLine().length();
    }

    public void moveLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = currentLine().length();
        }
    }

    public void moveRight() {
        if (cursorColumn < currentLine().length()) {
            cursorColumn++;
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorColumn = 0;
        }
    }

    public void moveUp() {
        if (cursorLine > 0) {
            cursorLine--;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
        }
    }

    public void moveDown() {
        if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorColumn = Math.min(cursorColumn, currentLine().length());
        }
    }

    public void moveHome() {
        cursorColumn = 0;
    }

    public void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String current = currentLine();
        String before = current.substring(0, Math.min(cursorColumn, current.length()));
        String after = current.substring(Math.min(cursorColumn, current.length()));
        String[] parts = normalized.split("\n", -1);
        if (parts.length == 1) {
            lines.set(cursorLine, before + parts[0] + after);
            cursorColumn = before.length() + parts[0].length();
        } else {
            lines.set(cursorLine, before + parts[0]);
            int insertLine = cursorLine + 1;
            for (int i = 1; i < parts.length; i++) {
                String segment = parts[i];
                if (i == parts.length - 1) {
                    lines.add(insertLine, segment + after);
                } else {
                    lines.add(insertLine, segment);
                }
                insertLine++;
            }
            cursorLine += parts.length - 1;
            cursorColumn = parts[parts.length - 1].length();
        }
        markDirty();
    }

    public void newline() {
        String current = currentLine();
        String before = current.substring(0, Math.min(cursorColumn, current.length()));
        String after = current.substring(Math.min(cursorColumn, current.length()));
        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, after);
        cursorLine++;
        cursorColumn = 0;
        markDirty();
    }

    public void backspace() {
        if (cursorColumn > 0) {
            String current = currentLine();
            lines.set(cursorLine, current.substring(0, cursorColumn - 1) + current.substring(cursorColumn));
            cursorColumn--;
            markDirty();
            return;
        }
        if (cursorLine > 0) {
            String current = currentLine();
            int previousLength = lines.get(cursorLine - 1).length();
            lines.set(cursorLine - 1, lines.get(cursorLine - 1) + current);
            lines.remove(cursorLine);
            cursorLine--;
            cursorColumn = previousLength;
            markDirty();
        }
    }

    public void deleteForward() {
        String current = currentLine();
        if (cursorColumn < current.length()) {
            lines.set(cursorLine, current.substring(0, cursorColumn) + current.substring(cursorColumn + 1));
            markDirty();
            return;
        }
        if (cursorLine < lines.size() - 1) {
            lines.set(cursorLine, current + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
            markDirty();
        }
    }

    public void rollback() {
        setContent(originalContent);
    }

    public ValidationResult validate(String relativePath) {
        String normalizedPath = relativePath == null ? "" : relativePath.trim().toLowerCase(Locale.ROOT);
        String content = getContent();
        if (normalizedPath.endsWith(".json")) {
            return validateJson(content);
        }
        if (normalizedPath.endsWith(".lang")) {
            return validateLang(content);
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.error("JSON 文件不能为空", 1, 1);
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(content));
            reader.setLenient(false);
            new JsonParser().parse(reader);
            return ValidationResult.ok();
        } catch (Exception error) {
            int line = 1;
            int column = 1;
            Matcher matcher = JSON_LOCATION_PATTERN.matcher(String.valueOf(error.getMessage()));
            if (matcher.find()) {
                try {
                    line = Math.max(1, Integer.parseInt(matcher.group(1)));
                    column = Math.max(1, Integer.parseInt(matcher.group(2)));
                } catch (Exception ignored) {
                }
            }
            return ValidationResult.error("JSON 语法错误: " + safeMessage(error.getMessage()), line, column);
        }
    }

    private ValidationResult validateLang(String content) {
        String[] split = (content == null ? "" : content).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        Set<String> keys = new LinkedHashSet<String>();
        for (int i = 0; i < split.length; i++) {
            String rawLine = split[i] == null ? "" : split[i];
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = rawLine.indexOf('=');
            if (separator < 0) {
                return ValidationResult.error("lang 文件必须使用 key=value 格式", i + 1, 1);
            }
            String key = rawLine.substring(0, separator).trim();
            if (key.isEmpty()) {
                return ValidationResult.error("lang 键名不能为空", i + 1, 1);
            }
            if (!keys.add(key)) {
                return ValidationResult.error("lang 键重复: " + key, i + 1, 1);
            }
        }
        return ValidationResult.ok();
    }

    private void markDirty() {
        dirty = true;
        clampCursor();
    }

    private void clampCursor() {
        if (lines.isEmpty()) {
            lines.add("");
        }
        cursorLine = Math.max(0, Math.min(cursorLine, lines.size() - 1));
        cursorColumn = Math.max(0, Math.min(cursorColumn, lineAt(cursorLine).length()));
    }

    private String currentLine() {
        return lineAt(cursorLine);
    }

    private String lineAt(int index) {
        if (lines.isEmpty()) {
            lines.add("");
        }
        int safe = Math.max(0, Math.min(index, lines.size() - 1));
        return lines.get(safe);
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String safeMessage(String message) {
        return message == null ? "未知错误" : message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
