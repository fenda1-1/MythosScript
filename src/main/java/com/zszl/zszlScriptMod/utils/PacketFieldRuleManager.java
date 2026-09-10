package com.zszl.zszlScriptMod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PacketFieldRuleManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RESOURCE_NAME = "packet_field_rules.json";
    private static final String[] REMOVED_DEFAULT_RULES = {
            "quest_accept_command_hex", "quest_reject_command_hex", "quest_accept_command", "quest_reject_command"
    };
    private static final List<FieldRule> RULES = new CopyOnWriteArrayList<>();
    private static final List<String> CATEGORIES = new CopyOnWriteArrayList<>();
    private static final Map<String, CapturedFieldSnapshot> LAST_CAPTURED_FIELDS = new ConcurrentHashMap<>();
    private static volatile CapturedFieldSnapshot lastCapturedField = null;
    private static volatile boolean initialized = false;

    public static final class CapturedFieldSnapshot {
        private final String ruleName;
        private final String variableName;
        private final String scope;
        private final String channel;
        private final String direction;
        private final String source;
        private final String packetClassName;
        private final String rawValue;
        private final Object value;
        private final long timestamp;

        public CapturedFieldSnapshot(String ruleName, String variableName, String scope, String channel,
                String direction, String source, String packetClassName, String rawValue, Object value,
                long timestamp) {
            this.ruleName = safe(ruleName);
            this.variableName = safe(variableName);
            this.scope = safe(scope);
            this.channel = safe(channel);
            this.direction = safe(direction);
            this.source = safe(source);
            this.packetClassName = safe(packetClassName);
            this.rawValue = safe(rawValue);
            this.value = value;
            this.timestamp = timestamp;
        }

        public String getRuleName() {
            return ruleName;
        }

        public String getVariableName() {
            return variableName;
        }

        public String getScope() {
            return scope;
        }

        public String getChannel() {
            return channel;
        }

        public String getDirection() {
            return direction;
        }

        public String getSource() {
            return source;
        }

        public String getPacketClassName() {
            return packetClassName;
        }

        public String getRawValue() {
            return rawValue;
        }

        public Object getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ruleName", ruleName);
            map.put("variableName", variableName);
            map.put("scope", scope);
            map.put("channel", channel);
            map.put("direction", direction);
            map.put("source", source);
            map.put("packetClassName", packetClassName);
            map.put("rawValue", rawValue);
            map.put("value", value);
            map.put("timestamp", timestamp);
            return map;
        }
    }

    public static final class RuleEditModel {
        public String name = "";
        public String category = "";
        public boolean enabled = true;
        public String channel = "";
        public String direction = "both";
        public String source = "decoded";
        public String extractMode = "regex";
        public String pattern = "";
        public int group = 1;
        public String variableName = "";
        public String valueType = "auto";
        public String scope = "global";
        public boolean writeDefaultOnFailure = false;
        public String defaultValue = "";
        public String note = "";
    }

    private static final class ConfigRoot {
        private List<FieldRule> rules = new ArrayList<>();
        private List<String> categories = new ArrayList<>();
    }

    private static final class FieldRule {
        private String name = "";
        private String category = "";
        private boolean enabled = true;
        private String channel = "";
        private String direction = "both";
        private String source = "decoded";
        private String extractMode = "regex";
        private String pattern = "";
        private int group = 1;
        private String variableName = "";
        private String valueType = "auto";
        private String scope = "global";
        private boolean writeDefaultOnFailure = false;
        private String defaultValue = "";
        private String note = "";

        private transient Pattern compiledPattern;
    }

    private PacketFieldRuleManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        reloadRules();
        initialized = true;
    }

    public static synchronized void reloadRules() {
        RULES.clear();
        CATEGORIES.clear();
        LAST_CAPTURED_FIELDS.clear();
        lastCapturedField = null;
        Path path = getConfigPath();
        ensureConfigExists(path);
        loadRulesFromResource();
        loadRulesFromFile(path, true);
        stripRemovedDefaultRules(path);
    }

    private static void stripRemovedDefaultRules(Path path) {
        boolean removed = false;
        for (String name : REMOVED_DEFAULT_RULES) {
            if (hasRuleName(name)) {
                removeRuleByName(name);
                removed = true;
            }
        }
        if (!removed) {
            return;
        }
        ConfigRoot root = new ConfigRoot();
        root.rules = new ArrayList<FieldRule>(RULES);
        root.categories = new ArrayList<String>(CATEGORIES);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 清理默认规则失败", e);
        }
    }

    private static void loadRulesFromResource() {
        try (InputStream in = PacketFieldRuleManager.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                loadRulesFromReader(reader, false);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 加载内置规则失败", e);
        }
    }

    private static void loadRulesFromFile(Path path, boolean overrideExisting) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loadRulesFromReader(reader, overrideExisting);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 加载规则失败", e);
        }
    }

    private static void loadRulesFromReader(Reader reader, boolean overrideExisting) {
        ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
        if (root == null) {
            return;
        }
        if (root.categories != null) for (String category : root.categories) addCategoryIfMissing(CATEGORIES, category);
        if (root.rules == null) return;
        for (FieldRule rule : root.rules) {
            FieldRule prepared = prepareRule(rule);
            if (prepared == null) {
                continue;
            }
            if (overrideExisting && !isBlank(prepared.name)) {
                removeRuleByName(prepared.name);
            } else if (!overrideExisting && hasRuleName(prepared.name)) {
                continue;
            }
            RULES.add(prepared);
        }
    }

    private static FieldRule prepareRule(FieldRule rule) {
        if (rule == null || isBlank(rule.variableName)) {
            return null;
        }
        try {
            rule.direction = normalizeDirection(rule.direction);
            rule.source = normalizeSource(rule.source);
            rule.extractMode = normalizeExtractMode(rule.extractMode);
            rule.valueType = normalizeValueType(rule.valueType);
            rule.scope = normalizeScope(rule.scope);
            rule.group = Math.max(1, rule.group);
            if ("regex".equals(rule.extractMode)) {
                if (isBlank(rule.pattern)) {
                    return null;
                }
                rule.compiledPattern = Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE);
            }
            return rule;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 编译规则失败: {}", rule.name, e);
            return null;
        }
    }

    private static void removeRuleByName(String name) {
        String normalized = normalizeLookupKey(name);
        if (normalized.isEmpty()) {
            return;
        }
        for (FieldRule rule : new ArrayList<>(RULES)) {
            if (rule != null && normalized.equals(normalizeLookupKey(rule.name))) {
                RULES.remove(rule);
            }
        }
    }

    private static boolean hasRuleName(String name) {
        String normalized = normalizeLookupKey(name);
        if (normalized.isEmpty()) {
            return false;
        }
        for (FieldRule rule : RULES) {
            if (rule != null && normalized.equals(normalizeLookupKey(rule.name))) {
                return true;
            }
        }
        return false;
    }

    public static synchronized List<RuleEditModel> getRuleModels() {
        initialize();
        List<RuleEditModel> result = new ArrayList<>();
        for (FieldRule rule : RULES) {
            if (rule == null) {
                continue;
            }
            RuleEditModel model = new RuleEditModel();
            model.name = safe(rule.name);
            model.category = safe(rule.category);
            model.enabled = rule.enabled;
            model.channel = safe(rule.channel);
            model.direction = safe(rule.direction);
            model.source = safe(rule.source);
            model.extractMode = safe(rule.extractMode);
            model.pattern = safe(rule.pattern);
            model.group = Math.max(1, rule.group);
            model.variableName = safe(rule.variableName);
            model.valueType = safe(rule.valueType);
            model.scope = safe(rule.scope);
            model.writeDefaultOnFailure = rule.writeDefaultOnFailure;
            model.defaultValue = safe(rule.defaultValue);
            model.note = safe(rule.note);
            result.add(model);
        }
        return result;
    }

    public static synchronized void saveRuleModels(List<RuleEditModel> models) {
        Path path = getConfigPath();
        ensureConfigExists(path);
        ConfigRoot root = new ConfigRoot();
        root.rules = new ArrayList<>();
        root.categories = new ArrayList<>(CATEGORIES);
        if (models != null) {
            for (RuleEditModel model : models) {
                if (model == null || isBlank(model.variableName)) {
                    continue;
                }
                FieldRule rule = new FieldRule();
                rule.name = safe(model.name).trim();
                rule.category = safe(model.category).trim();
                addCategoryIfMissing(root.categories, rule.category);
                rule.enabled = model.enabled;
                rule.channel = safe(model.channel).trim();
                rule.direction = normalizeDirection(model.direction);
                rule.source = normalizeSource(model.source);
                rule.extractMode = normalizeExtractMode(model.extractMode);
                rule.pattern = safe(model.pattern).trim();
                rule.group = Math.max(1, model.group);
                rule.variableName = safe(model.variableName).trim();
                rule.valueType = normalizeValueType(model.valueType);
                rule.scope = normalizeScope(model.scope);
                rule.writeDefaultOnFailure = model.writeDefaultOnFailure;
                rule.defaultValue = safe(model.defaultValue).trim();
                rule.note = safe(model.note).trim();
                root.rules.add(rule);
            }
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 保存规则失败", e);
        }
        reloadRules();
    }

    private static boolean saveCategories() {
        return saveCurrentRules();
    }

    private static boolean saveCurrentRules() {
        Path path = getConfigPath();
        ensureConfigExists(path);
        ConfigRoot root = new ConfigRoot();
        root.rules = new ArrayList<>(RULES);
        root.categories = new ArrayList<>(CATEGORIES);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
            reloadRules();
            return true;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 保存分类失败", e);
            return false;
        }
    }

    public static synchronized List<String> getCategories() {
        initialize();
        return new ArrayList<>(CATEGORIES);
    }

    public static synchronized boolean addCategory(String category) {
        initialize();
        String value = safe(category).trim();
        if (value.isEmpty()) return false;
        if (!containsCategory(CATEGORIES, value)) CATEGORIES.add(value);
        return saveCategories();
    }

    public static synchronized boolean deleteCategory(String category) {
        initialize();
        String value = safe(category).trim();
        if (value.isEmpty()) return false;
        boolean changed = removeCategory(CATEGORIES, value);
        for (FieldRule rule : RULES) if (rule != null && value.equalsIgnoreCase(safe(rule.category).trim())) {
            rule.category = "";
            changed = true;
        }
        return changed && saveCurrentRules();
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        initialize();
        String oldValue = safe(oldCategory).trim(), newValue = safe(newCategory).trim();
        if (oldValue.isEmpty() || newValue.isEmpty() || containsCategory(CATEGORIES, newValue)) return false;
        boolean changed = false;
        for (int i = 0; i < CATEGORIES.size(); i++) if (oldValue.equalsIgnoreCase(CATEGORIES.get(i))) { CATEGORIES.set(i, newValue); changed = true; }
        for (FieldRule rule : RULES) if (rule != null && oldValue.equalsIgnoreCase(safe(rule.category).trim())) { rule.category = newValue; changed = true; }
        return changed && saveCurrentRules();
    }

    public static void processPacket(String channel, boolean outbound, byte[] rawData, String decodedText,
            String packetClassName) {
        initialize();
        if (rawData == null || rawData.length == 0 || RULES.isEmpty()) {
            return;
        }

        String direction = outbound ? "outbound" : "inbound";
        String hexText = null;
        for (FieldRule rule : RULES) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            if (!matchesChannel(rule.channel, channel)) {
                continue;
            }
            if (!matchesDirection(rule.direction, direction)) {
                continue;
            }

            try {
                ExtractionResult extraction = extractValue(rule, channel, decodedText, packetClassName, rawData, hexText);
                if (extraction == null || !extraction.matched) {
                    if (rule.writeDefaultOnFailure && !isBlank(rule.defaultValue)) {
                        Object parsedDefault = parseCapturedValue(rule.defaultValue, rule.valueType);
                        writeValueToScope(rule.scope, rule.variableName, parsedDefault);
                        recordCapturedValue(rule, rule.defaultValue, parsedDefault, channel, direction, packetClassName);
                    }
                    continue;
                }
                if (extraction.usedHexSource && hexText == null) {
                    hexText = bytesToHex(rawData);
                }
                Object parsedValue = parseCapturedValue(extraction.rawValue, rule.valueType);
                writeValueToScope(rule.scope, rule.variableName, parsedValue);
                recordCapturedValue(rule, extraction.rawValue, parsedValue, channel, direction, packetClassName);
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("[PacketFieldRule] 执行规则失败: {}", rule.name, e);
            }
        }
    }

    public static boolean hasEnabledRulesForChannel(String channel, boolean outbound) {
        initialize();
        if (RULES.isEmpty()) {
            return false;
        }
        String direction = outbound ? "outbound" : "inbound";
        for (FieldRule rule : RULES) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            if (!matchesChannel(rule.channel, channel) || !matchesDirection(rule.direction, direction)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static ExtractionResult extractValue(FieldRule rule, String channel, String decodedText,
            String packetClassName, byte[] rawData, String currentHexText) {
        if (rule == null) {
            return null;
        }
        String mode = normalizeExtractMode(rule.extractMode);
        if ("offset".equals(mode)) {
            return extractByOffset(rule.pattern, rawData);
        }
        if ("key".equals(mode)) {
            String sourceText = resolveSourceText(rule.source, channel, decodedText, packetClassName, rawData);
            return extractByKeyName(rule.pattern, sourceText);
        }
        if ("length_prefixed".equals(mode)) {
            return extractByLengthPrefixed(rule.pattern, rawData);
        }

        String sourceText = resolveSourceText(rule.source, channel, decodedText, packetClassName, rawData);
        boolean usedHexSource = false;
        if (isBlank(sourceText)) {
            if ("hex".equals(rule.source)) {
                sourceText = currentHexText == null ? bytesToHex(rawData) : currentHexText;
                usedHexSource = true;
            } else {
                return null;
            }
        }
        if (rule.compiledPattern == null) {
            return null;
        }
        Matcher matcher = rule.compiledPattern.matcher(sourceText);
        if (!matcher.find() || matcher.groupCount() < rule.group) {
            return new ExtractionResult(false, "", usedHexSource);
        }
        return new ExtractionResult(true, matcher.group(rule.group), usedHexSource);
    }

    private static ExtractionResult extractByOffset(String pattern, byte[] rawData) {
        int[] values = parseIntPair(pattern);
        if (rawData == null || values == null) {
            return null;
        }
        int offset = Math.max(0, values[0]);
        int length = Math.max(0, values[1]);
        if (offset >= rawData.length || length <= 0) {
            return new ExtractionResult(false, "", false);
        }
        int end = Math.min(rawData.length, offset + length);
        byte[] slice = new byte[end - offset];
        System.arraycopy(rawData, offset, slice, 0, slice.length);
        return new ExtractionResult(true, bytesToHex(slice), false);
    }

    private static ExtractionResult extractByLengthPrefixed(String pattern, byte[] rawData) {
        int[] values = parseIntPair(pattern);
        if (rawData == null || values == null) {
            return null;
        }
        int offset = Math.max(0, values[0]);
        int prefixBytes = Math.max(1, values[1]);
        if (offset + prefixBytes > rawData.length) {
            return new ExtractionResult(false, "", false);
        }
        int length = 0;
        for (int i = 0; i < prefixBytes; i++) {
            length = (length << 8) | (rawData[offset + i] & 0xFF);
        }
        int dataStart = offset + prefixBytes;
        if (dataStart >= rawData.length || length <= 0) {
            return new ExtractionResult(false, "", false);
        }
        int end = Math.min(rawData.length, dataStart + length);
        byte[] slice = new byte[end - dataStart];
        System.arraycopy(rawData, dataStart, slice, 0, slice.length);
        return new ExtractionResult(true, bytesToHex(slice), false);
    }

    private static ExtractionResult extractByKeyName(String keyName, String sourceText) {
        String key = safe(keyName).trim();
        String text = safe(sourceText);
        if (key.isEmpty() || text.isEmpty()) {
            return new ExtractionResult(false, "", false);
        }
        Pattern pattern = Pattern.compile("(?:\"?" + Pattern.quote(key) + "\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^,;\\r\\n}]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return new ExtractionResult(false, "", false);
        }
        String raw = safe(matcher.group(1)).trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return new ExtractionResult(true, raw, false);
    }

    private static int[] parseIntPair(String text) {
        if (isBlank(text)) {
            return null;
        }
        String normalized = text.replace("[", "").replace("]", "").trim();
        String[] parts = normalized.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
        } catch (Exception ignored) {
            return null;
        }
    }

    public static CapturedFieldSnapshot getLatestCapturedField(String key) {
        initialize();
        if (isBlank(key)) {
            return lastCapturedField;
        }
        return LAST_CAPTURED_FIELDS.get(normalizeLookupKey(key));
    }

    public static List<String> getLatestCapturedFieldKeysSnapshot() {
        initialize();
        List<String> keys = new ArrayList<>(LAST_CAPTURED_FIELDS.keySet());
        Collections.sort(keys);
        return keys;
    }

    private static void writeValueToScope(String scope, String variableName, Object value) {
        String normalizedScope = normalizeScope(scope);
        String key = safe(variableName).trim();
        if (key.isEmpty()) {
            return;
        }
        if ("global".equals(normalizedScope)) {
            ScopedRuntimeVariables.setGlobalValue(key, value);
            return;
        }
        ScopedRuntimeVariables.setGlobalValue(normalizedScope + "." + key, value);
    }

    private static void recordCapturedValue(FieldRule rule, String rawValue, Object parsedValue, String channel,
            String direction, String packetClassName) {
        if (rule == null) {
            return;
        }
        String variableName = safe(rule.variableName).trim();
        String scope = normalizeScope(rule.scope);
        CapturedFieldSnapshot snapshot = new CapturedFieldSnapshot(rule.name, variableName, scope, channel, direction,
                normalizeSource(rule.source), packetClassName, rawValue, parsedValue, System.currentTimeMillis());
        lastCapturedField = snapshot;
        putCapturedFieldAlias(rule.name, snapshot);
        putCapturedFieldAlias(variableName, snapshot);
        if (!variableName.isEmpty()) {
            String scopedVariable = "global".equals(scope) ? variableName : scope + "." + variableName;
            putCapturedFieldAlias(scopedVariable, snapshot);
        }
    }

    private static void putCapturedFieldAlias(String key, CapturedFieldSnapshot snapshot) {
        String normalized = normalizeLookupKey(key);
        if (normalized.isEmpty() || snapshot == null) {
            return;
        }
        LAST_CAPTURED_FIELDS.put(normalized, snapshot);
    }

    private static String normalizeLookupKey(String key) {
        return safe(key).trim().toLowerCase(Locale.ROOT);
    }

    private static Object parseCapturedValue(String rawValue, String valueType) {
        String safeRaw = safe(rawValue).trim();
        String normalizedType = normalizeValueType(valueType);
        if ("string".equals(normalizedType)) {
            return unescapeCapturedString(safeRaw);
        }
        if ("utf8-string".equals(normalizedType)) {
            byte[] bytes = parseHexBytes(safeRaw);
            if (bytes != null && bytes.length > 0) {
                return unescapeCapturedString(new String(bytes, StandardCharsets.UTF_8).trim());
            }
            return unescapeCapturedString(safeRaw);
        }
        if ("hex".equals(normalizedType)) {
            return safeRaw.replaceAll("\\s+", "");
        }
        if ("boolean".equals(normalizedType)) {
            return "true".equalsIgnoreCase(safeRaw)
                    || "1".equals(safeRaw)
                    || "yes".equalsIgnoreCase(safeRaw)
                    || "on".equalsIgnoreCase(safeRaw);
        }
        if ("number".equals(normalizedType)) {
            try {
                if (safeRaw.matches("[-+]?\\d+")) {
                    long value = Long.parseLong(safeRaw);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        return (int) value;
                    }
                    return value;
                }
                return Double.parseDouble(safeRaw);
            } catch (Exception ignored) {
                return 0;
            }
        }

        if (safeRaw.matches("[-+]?\\d+")) {
            try {
                long value = Long.parseLong(safeRaw);
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return (int) value;
                }
                return value;
            } catch (Exception ignored) {
            }
        }
        if ("true".equalsIgnoreCase(safeRaw) || "false".equalsIgnoreCase(safeRaw)) {
            return Boolean.parseBoolean(safeRaw);
        }
        return safeRaw;
    }

    private static byte[] parseHexBytes(String hexText) {
        if (isBlank(hexText)) {
            return null;
        }
        String normalized = hexText.replaceAll("[^0-9A-Fa-f]", "");
        if (normalized.isEmpty() || normalized.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int idx = i * 2;
            try {
                out[i] = (byte) Integer.parseInt(normalized.substring(idx, idx + 2), 16);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return out;
    }

    private static String unescapeCapturedString(String text) {
        String value = safe(text);
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                builder.append(ch);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'u':
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            builder.append('\\').append(next);
                            break;
                        }
                    }
                    builder.append('\\').append(next);
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case '\\':
                    builder.append('\\');
                    break;
                case '"':
                    builder.append('"');
                    break;
                default:
                    builder.append(next);
                    break;
            }
        }
        return builder.toString();
    }

    private static String resolveSourceText(String source, String channel, String decodedText, String packetClassName,
            byte[] rawData) {
        String normalizedSource = normalizeSource(source);
        switch (normalizedSource) {
            case "channel":
                return safe(channel);
            case "class":
                return safe(packetClassName);
            case "hex":
                return bytesToHex(rawData);
            case "decoded":
            default:
                String decoded = safe(decodedText);
                if (!isBlank(decoded)) {
                    return decoded;
                }
                return rawData == null || rawData.length == 0 ? "" : PacketPayloadDecoder.decodeFull(rawData);
        }
    }

    private static boolean matchesChannel(String expected, String actual) {
        String expectedText = safe(expected).trim();
        String actualText = safe(actual).trim();
        if (isBlank(expectedText)) {
            return true;
        }
        if ("standard".equalsIgnoreCase(expectedText) || "N/A".equalsIgnoreCase(expectedText)) {
            return actualText.isEmpty() || "N/A".equalsIgnoreCase(actualText);
        }
        return expectedText.equalsIgnoreCase(actualText);
    }

    private static boolean matchesDirection(String expected, String actual) {
        String normalized = normalizeDirection(expected);
        return "both".equals(normalized) || normalized.equalsIgnoreCase(safe(actual));
    }

    private static Path getConfigPath() {
        return ProfileManager.getCurrentProfileDir().resolve("packet_field_rules.json");
    }

    private static void ensureConfigExists(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (!Files.exists(path)) {
                ConfigRoot root = new ConfigRoot();
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, writer);
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[PacketFieldRule] 初始化配置失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    private static String normalizeDirection(String direction) {
        String value = safe(direction).trim().toLowerCase(Locale.ROOT);
        if ("inbound".equals(value) || "outbound".equals(value) || "both".equals(value)) {
            return value;
        }
        return "both";
    }

    private static String normalizeSource(String source) {
        String value = safe(source).trim().toLowerCase(Locale.ROOT);
        if ("decoded".equals(value) || "hex".equals(value) || "channel".equals(value) || "class".equals(value)) {
            return value;
        }
        return "decoded";
    }

    private static String normalizeExtractMode(String extractMode) {
        String value = safe(extractMode).trim().toLowerCase(Locale.ROOT);
        if ("offset".equals(value) || "key".equals(value) || "length_prefixed".equals(value) || "regex".equals(value)) {
            return value;
        }
        return "regex";
    }

    private static String normalizeValueType(String valueType) {
        String value = safe(valueType).trim().toLowerCase(Locale.ROOT);
        if ("string".equals(value) || "number".equals(value) || "boolean".equals(value) || "hex".equals(value)
                || "auto".equals(value) || "utf8-string".equals(value) || "utf8".equals(value)) {
            if ("utf8".equals(value)) {
                return "utf8-string";
            }
            return value;
        }
        return "auto";
    }

    private static String normalizeScope(String scope) {
        String value = safe(scope).trim().toLowerCase(Locale.ROOT);
        if ("global".equals(value) || "sequence".equals(value) || "local".equals(value) || "temp".equals(value)) {
            return value;
        }
        return "global";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean containsCategory(List<String> values, String category) {
        if (values == null) return false;
        for (String value : values) if (category.equalsIgnoreCase(safe(value).trim())) return true;
        return false;
    }

    private static boolean removeCategory(List<String> values, String category) {
        if (values == null) return false;
        boolean removed = false;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (category.equalsIgnoreCase(safe(values.get(i)).trim())) {
                values.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private static void addCategoryIfMissing(List<String> values, String category) {
        String normalized = safe(category).trim();
        if (!normalized.isEmpty() && !containsCategory(values, normalized)) values.add(normalized);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ExtractionResult {
        private final boolean matched;
        private final String rawValue;
        private final boolean usedHexSource;

        private ExtractionResult(boolean matched, String rawValue, boolean usedHexSource) {
            this.matched = matched;
            this.rawValue = safe(rawValue);
            this.usedHexSource = usedHexSource;
        }
    }
}
