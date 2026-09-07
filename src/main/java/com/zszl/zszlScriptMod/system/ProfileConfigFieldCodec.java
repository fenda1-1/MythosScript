package com.zszl.zszlScriptMod.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Converts profile JSON into selectable, human-readable rows without a fixed schema. */
public final class ProfileConfigFieldCodec {
    private static final Gson COMPACT_GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final int MAX_ROWS = 1200;
    private static final int MAX_VALUE_LENGTH = 96;
    private static final Map<String, String> FIELD_LABELS = buildFieldLabels();

    private ProfileConfigFieldCodec() {
    }

    public static List<ConfigField> describe(String relativePath, String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            return Collections.singletonList(ConfigField.message("文件内容", "暂无配置内容"));
        }
        if (relativePath == null || !relativePath.toLowerCase().endsWith(".json")) {
            return Collections.singletonList(ConfigField.message("文本配置", summarizeText(normalized)));
        }

        try {
            JsonElement root = new JsonParser().parse(normalized);
            List<ConfigField> rows = new ArrayList<>();
            appendRoot(rows, root);
            if (rows.isEmpty()) {
                rows.add(ConfigField.message("配置内容", "空配置"));
            }
            return Collections.unmodifiableList(rows);
        } catch (Exception ignored) {
            return Collections.singletonList(ConfigField.message("文本配置", "无法按结构化配置解析"));
        }
    }

    /** An empty selection means all fields; stale explicit selections must fail closed. */
    public static String select(String relativePath, String content, Collection<String> selectors) {
        if (selectors == null || selectors.isEmpty() || relativePath == null
                || !relativePath.toLowerCase().endsWith(".json")) {
            return content == null ? "" : content;
        }
        try {
            JsonElement root = new JsonParser().parse(content == null ? "" : content);
            Set<String> valid = new HashSet<>();
            for (ConfigField field : describe(relativePath, content)) {
                if (field.isSelectable()) {
                    valid.add(field.getSelector());
                }
            }

            SelectionNode selectionRoot = new SelectionNode();
            int accepted = 0;
            for (String selector : selectors) {
                if (selector == null || !valid.contains(selector)) {
                    throw new IllegalArgumentException("选中的配置字段已变化，请刷新后重新选择");
                }
                addSelector(selectionRoot, selector);
                accepted++;
            }
            if (accepted == 0) {
                throw new IllegalArgumentException("没有有效的已选字段");
            }

            JsonElement selected = project(root, selectionRoot);
            if (selected == null) throw new IllegalArgumentException("无法导出已选字段");
            return COMPACT_GSON.toJson(selected);
        } catch (Exception error) {
            throw new IllegalArgumentException("字段选择无法导出，请刷新配置后重试", error);
        }
    }

    private static void appendRoot(List<ConfigField> rows, JsonElement root) {
        if (root == null || root.isJsonNull()) {
            rows.add(new ConfigField("/", "配置值", "未设置", 0, true));
        } else if (root.isJsonObject()) {
            appendObject(rows, root.getAsJsonObject(), "", 0);
        } else if (root.isJsonArray()) {
            appendArray(rows, root.getAsJsonArray(), "", "配置项", 0);
        } else {
            rows.add(new ConfigField("/", "配置值", formatPrimitive(root.getAsJsonPrimitive()), 0, true));
        }
    }

    private static void appendObject(List<ConfigField> rows, JsonObject object, String pointer, int depth) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (rows.size() >= MAX_ROWS) {
                appendLimitMessage(rows);
                return;
            }
            String key = entry.getKey();
            String childPointer = pointer + "/" + escapePointer(key);
            JsonElement value = entry.getValue();
            String label = labelForKey(key);
            if (value == null || value.isJsonNull()) {
                rows.add(new ConfigField(childPointer, label, "未设置", depth, true));
            } else if (value.isJsonPrimitive()) {
                rows.add(new ConfigField(childPointer, label, formatPrimitive(value.getAsJsonPrimitive()), depth, true));
            } else if (value.isJsonArray()) {
                appendArray(rows, value.getAsJsonArray(), childPointer, label, depth);
            } else {
                JsonObject child = value.getAsJsonObject();
                rows.add(ConfigField.group(childPointer, label, child.size() + " 个字段", depth));
                appendObject(rows, child, childPointer, depth + 1);
            }
        }
    }

    private static void appendArray(List<ConfigField> rows, JsonArray array, String pointer, String label, int depth) {
        rows.add(ConfigField.group(pointer.isEmpty() ? "/" : pointer, label, array.size() + " 项", depth));
        for (int i = 0; i < array.size(); i++) {
            if (rows.size() >= MAX_ROWS) {
                appendLimitMessage(rows);
                return;
            }
            JsonElement item = array.get(i);
            String selector = pointer + "/" + i;
            if (item == null || item.isJsonNull()) {
                rows.add(new ConfigField(selector, label + " " + (i + 1), "未设置", depth + 1, true));
            } else if (item.isJsonPrimitive()) {
                rows.add(new ConfigField(selector, label + " " + (i + 1),
                        formatPrimitive(item.getAsJsonPrimitive()), depth + 1, true));
            } else if (item.isJsonObject()) {
                JsonObject object = item.getAsJsonObject();
                rows.add(new ConfigField(selector, itemLabel(label, i, object), itemSummary(object), depth + 1, true));
            } else {
                rows.add(new ConfigField(selector, label + " " + (i + 1),
                        item.getAsJsonArray().size() + " 项", depth + 1, true));
            }
        }
    }

    private static String itemLabel(String parentLabel, int index, JsonObject object) {
        String identity = firstString(object, "name", "displayName", "title", "label", "id", "key", "pathName");
        if (!identity.isEmpty()) {
            return parentLabel + "：" + shorten(identity);
        }
        return parentLabel + " " + (index + 1);
    }

    private static String itemSummary(JsonObject object) {
        List<String> parts = new ArrayList<>();
        addSummary(parts, object, "category", "分类");
        addSummary(parts, object, "enabled", "状态");
        addSummary(parts, object, "command", "指令");
        addSummary(parts, object, "type", "类型");
        addArrayCount(parts, object, "nodes", "节点");
        addArrayCount(parts, object, "actions", "动作");
        addArrayCount(parts, object, "steps", "步骤");
        addArrayCount(parts, object, "waypoints", "路点");
        if (parts.isEmpty()) {
            parts.add(object.size() + " 个字段");
        }
        return join(parts, " · ");
    }

    private static void addSummary(List<String> parts, JsonObject object, String key, String label) {
        if (!object.has(key) || object.get(key) == null || !object.get(key).isJsonPrimitive()) {
            return;
        }
        JsonPrimitive primitive = object.getAsJsonPrimitive(key);
        String value = formatPrimitive(primitive);
        if (!value.isEmpty()) {
            parts.add(label + "：" + shorten(value));
        }
    }

    private static void addArrayCount(List<String> parts, JsonObject object, String key, String label) {
        if (object.has(key) && object.get(key) != null && object.get(key).isJsonArray()) {
            parts.add(label + " " + object.getAsJsonArray(key).size());
        }
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key) != null && object.get(key).isJsonPrimitive()) {
                try {
                    String value = object.get(key).getAsString();
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private static String formatPrimitive(JsonPrimitive primitive) {
        if (primitive == null) {
            return "未设置";
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? "开启" : "关闭";
        }
        try {
            return shorten(primitive.getAsString());
        } catch (Exception ignored) {
            return shorten(String.valueOf(primitive));
        }
    }

    private static String summarizeText(String text) {
        String singleLine = text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return singleLine.isEmpty() ? "暂无配置内容" : shorten(singleLine);
    }

    private static String shorten(String value) {
        String normalized = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= MAX_VALUE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_VALUE_LENGTH - 1) + "…";
    }

    private static String labelForKey(String key) {
        String suffix = camelToSnake(key == null ? "" : key);
        String[] i18nKeys = {"gui.path.action_editor.label." + suffix,
                "gui.chatopt." + suffix, "gui.modern.chatopt." + suffix};
        try {
            for (String i18nKey : i18nKeys) {
                String translated = ModernFormI18n.tr(i18nKey);
                if (!i18nKey.equals(translated)) return translated;
            }
        } catch (RuntimeException ignored) {
            // Profile sharing also runs in tests and early startup, where the GUI
            // localization layer may not be initialized. A readable fallback label
            // must not make an otherwise valid configuration unselectable.
        }
        String known = FIELD_LABELS.get(key == null ? "" : key.toLowerCase());
        if (known != null) {
            return known;
        }
        if (key == null || key.trim().isEmpty()) {
            return "未命名字段";
        }
        String spaced = key.replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
        return spaced.isEmpty() ? key : spaced;
    }

    private static String camelToSnake(String value) {
        return value == null ? "" : value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_').toLowerCase();
    }

    private static Map<String, String> buildFieldLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("name", "名称");
        labels.put("enabled", "启用");
        labels.put("category", "分类");
        labels.put("categories", "路径分类");
        labels.put("hiddencategories", "隐藏分类");
        labels.put("sequences", "路径");
        labels.put("rules", "规则");
        labels.put("profiles", "方案");
        labels.put("warehouses", "仓库");
        labels.put("nodes", "节点");
        labels.put("actions", "动作");
        labels.put("steps", "步骤");
        labels.put("waypoints", "路点");
        labels.put("command", "指令");
        labels.put("type", "类型");
        labels.put("key", "键");
        labels.put("keycode", "按键");
        labels.put("description", "说明");
        labels.put("timeout", "超时时间");
        labels.put("delay", "延迟");
        labels.put("radius", "范围");
        labels.put("distance", "距离");
        labels.put("speed", "速度");
        labels.put("mode", "模式");
        labels.put("target", "目标");
        labels.put("targets", "目标");
        labels.put("filters", "过滤条件");
        labels.put("settings", "设置");
        labels.put("item_name", "物品名称");
        labels.put("leave_one", "保留一个");
        labels.put("sequential_equip", "顺序装备");
        labels.put("smart_activation_enabled", "智能激活");
        labels.put("smart_activation_range", "智能激活范围");
        labels.put("equip_interval_ticks", "装备间隔刻");
        return Collections.unmodifiableMap(labels);
    }

    private static void appendLimitMessage(List<ConfigField> rows) {
        if (rows.isEmpty() || !"显示限制".equals(rows.get(rows.size() - 1).getLabel())) {
            rows.add(ConfigField.message("显示限制", "配置项较多，仅显示前 " + MAX_ROWS + " 项"));
        }
    }

    private static void addSelector(SelectionNode root, String selector) {
        if ("/".equals(selector)) {
            root.terminal = true;
            return;
        }
        SelectionNode current = root;
        String[] segments = selector.substring(1).split("/", -1);
        for (String encoded : segments) {
            String segment = unescapePointer(encoded);
            SelectionNode next = current.children.get(segment);
            if (next == null) {
                next = new SelectionNode();
                current.children.put(segment, next);
            }
            current = next;
        }
        current.terminal = true;
    }

    private static JsonElement project(JsonElement source, SelectionNode selection) {
        if (selection.terminal) {
            return cloneElement(source);
        }
        if (source == null || source.isJsonNull()) {
            return null;
        }
        if (source.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, SelectionNode> selected : selection.children.entrySet()) {
                if (!source.getAsJsonObject().has(selected.getKey())) {
                    continue;
                }
                JsonElement child = project(source.getAsJsonObject().get(selected.getKey()), selected.getValue());
                if (child != null) {
                    result.add(selected.getKey(), child);
                }
            }
            return result.size() == 0 ? null : result;
        }
        if (source.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (int i = 0; i < source.getAsJsonArray().size(); i++) {
                SelectionNode childSelection = selection.children.get(String.valueOf(i));
                if (childSelection == null) {
                    continue;
                }
                JsonElement child = project(source.getAsJsonArray().get(i), childSelection);
                if (child != null) {
                    result.add(child);
                }
            }
            return result.size() == 0 ? null : result;
        }
        return null;
    }

    private static JsonElement cloneElement(JsonElement source) {
        if (source == null || source.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        return new JsonParser().parse(COMPACT_GSON.toJson(source));
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static String unescapePointer(String value) {
        return value.replace("~1", "/").replace("~0", "~");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static final class SelectionNode {
        private final Map<String, SelectionNode> children = new LinkedHashMap<>();
        private boolean terminal;
    }

    public static final class ConfigField {
        private final String selector;
        private final String label;
        private final String value;
        private final int depth;
        private final boolean selectable;

        private ConfigField(String selector, String label, String value, int depth, boolean selectable) {
            this.selector = selector == null ? "" : selector;
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.depth = Math.max(0, depth);
            this.selectable = selectable;
        }

        private static ConfigField group(String selector, String label, String value, int depth) {
            return new ConfigField(selector, label, value, depth, false);
        }

        private static ConfigField message(String label, String value) {
            return new ConfigField("", label, value, 0, false);
        }

        public String getSelector() {
            return selector;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public int getDepth() {
            return depth;
        }

        public boolean isSelectable() {
            return selectable;
        }
    }
}
