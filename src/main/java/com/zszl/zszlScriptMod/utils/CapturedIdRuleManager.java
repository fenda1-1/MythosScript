package com.zszl.zszlScriptMod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class CapturedIdRuleManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Path CUSTOM_CONFIG_PATH = Paths.get(ModConfig.CONFIG_DIR, "captured_ids_custom.json");
    private static final String RULE_SHARE_PREFIX = "CIDR1.";
    private static final String CATEGORY_UNGROUPED = "未分组";

    private static volatile List<CaptureRule> rules = Collections.emptyList();
    private static final Map<String, byte[]> capturedValues = new ConcurrentHashMap<>();
    private static final Map<String, Long> capturedUpdateVersions = new ConcurrentHashMap<>();
    private static final Map<String, Long> capturedRecaptureVersions = new ConcurrentHashMap<>();
    private static final Map<String, RuntimeStatus> runtimeStatus = new ConcurrentHashMap<>();

    private static final class RuntimeStatus {
        final long matchedAt, matches;
        final String result;
        RuntimeStatus(long matchedAt, long matches, String result) {
            this.matchedAt = matchedAt; this.matches = matches; this.result = result;
        }
    }

    public static String getRuntimeStatus(String key) {
        RuntimeStatus status = runtimeStatus.get(canonicalKey(key));
        if (status == null) return "尚无实时命中";
        String time = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(status.matchedAt));
        return "实时命中 " + time + "（" + status.matches + " 次） | " + status.result;
    }

    private static void recordRuntimeStatus(String key, String message, boolean newMatch) {
        runtimeStatus.compute(canonicalKey(key), (name, old) -> new RuntimeStatus(
                newMatch || old == null ? System.currentTimeMillis() : old.matchedAt,
                (old == null ? 0 : old.matches) + (newMatch ? 1 : 0), message));
    }
    private static final List<String> customCategories = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized = false;

    public static class DisplayEntry {
        public final String key;
        public final String displayName;
        public final String hex;

        public DisplayEntry(String key, String displayName, String hex) {
            this.key = key;
            this.displayName = displayName;
            this.hex = hex;
        }
    }

    public static class RuleEditModel {
        public String name;
        public String displayName;
        public String note;
        public String aliasesCsv;
        public boolean enabled = true;
        public String channel;
        public String direction = "both";
        public String target = "hex";
        public String pattern;
        public String offset;
        public int group = 1;
        public String category;
        public String valueType = "hex";
        public int byteLength = 4;
        public String updateSequenceName;
        public String updateSequenceMode = "always";
        public int updateSequenceCooldownMs = 1000;
    }

    public static class RuleCard {
        public final int index;
        public final RuleEditModel model;
        public final String capturedHex;

        public RuleCard(int index, RuleEditModel model, String capturedHex) {
            this.index = index;
            this.model = model;
            this.capturedHex = capturedHex;
        }
    }

    private static class ConfigRoot {
        List<CaptureRule> rules = new ArrayList<>();
        List<String> categories = new ArrayList<>();
    }

    private static class CaptureRule {
        String name;
        String displayName;
        String note;
        List<String> aliases;
        boolean enabled = true;
        String channel;
        String direction = "both";
        String target = "hex";
        String pattern;
        String offset;
        int group = 1;
        String category;
        String valueType = "hex";
        int byteLength = 4;
        String updateSequenceName;
        String updateSequenceMode = "always";
        int updateSequenceCooldownMs = 1000;

        transient Pattern compiledPattern;
        transient int customIndex = -1;
        transient boolean updateSequenceTriggered = false;
        transient long nextUpdateSequenceAllowedAt = 0L;
    }

    private static class RuleSharePayload {
        int version = 1;
        RuleEditModel rule;
        String capturedHex;
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        ensureCustomConfigExists();
        reloadRules();
        initialized = true;
    }

    public static synchronized void reloadRules() {
        ensureCustomConfigExists();

        customCategories.clear();

        ConfigRoot customRoot = loadCustomConfigRoot();
        if (customRoot.categories != null) {
            for (String category : customRoot.categories) {
                String normalized = normalizeCategory(category);
                if (!isBlank(normalized) && !customCategories.contains(normalized)) {
                    customCategories.add(normalized);
                }
            }
        }
        rules = loadRulesFromRoot(customRoot);
        resetUpdateSequenceRuntimeState();
        clearStaleCapturedValues();
    }

    public static void processPacket(String channel, boolean outbound, byte[] rawData, String decodedText) {
        initialize();
        if (rawData == null || rawData.length == 0 || rules.isEmpty()) {
            return;
        }

        String hexText = bytesToHex(rawData);
        String direction = outbound ? "outbound" : "inbound";

        for (CaptureRule rule : rules) {
            if (rule == null || !rule.enabled || rule.compiledPattern == null) {
                continue;
            }
            if (!matchesChannel(rule.channel, channel)) {
                continue;
            }
            if (!matchesDirection(rule.direction, direction)) {
                continue;
            }

            String sourceText = "decoded".equalsIgnoreCase(rule.target) ? decodedText : hexText;
            if (isBlank(sourceText)) {
                continue;
            }

            try {
                Matcher matcher = rule.compiledPattern.matcher(sourceText);
                if (!matcher.find()) {
                    continue;
                }

                markRecaptured(rule.name);
                recordRuntimeStatus(rule.name, "已匹配", true);

                String sequenceMode = normalizeUpdateSequenceMode(rule.updateSequenceMode);
                if ("recapture".equals(sequenceMode)) {
                    triggerUpdateSequenceForRule(rule, true);
                    continue;
                }

                if (matcher.groupCount() < rule.group) {
                    recordRuntimeStatus(rule.name, "捕获分组不存在：" + rule.group, false);
                    continue;
                }

                String rawValue = matcher.group(rule.group);
                byte[] parsed = parseValue(rawValue, rule.valueType, rule.byteLength);
                if (parsed != null && parsed.length > 0) {
                    if ("hex".equalsIgnoreCase(rule.valueType)) {
                        parsed = applyRuleOffset(parsed, rule.offset);
                    }
                    boolean changed = storeCapturedId(rule.name, parsed);
                    recordRuntimeStatus(rule.name, changed ? "数值已保存" : "数值相同，更新模式不触发", false);
                    if (changed) triggerUpdateSequenceForRule(rule, false);
                } else {
                    recordRuntimeStatus(rule.name, "捕获组无法转换为数值", false);
                }
            } catch (Exception e) {
                recordRuntimeStatus(rule.name, "处理失败：" + e.getMessage(), false);
                zszlScriptMod.LOGGER.error("[CapturedId] 规则执行失败: {}", rule.name, e);
            }
        }
    }

    public static boolean hasEnabledRulesForChannel(String channel, boolean outbound) {
        initialize();
        if (rules.isEmpty()) {
            return false;
        }
        String direction = outbound ? "outbound" : "inbound";
        for (CaptureRule rule : rules) {
            if (rule == null || !rule.enabled || rule.compiledPattern == null) {
                continue;
            }
            if (!matchesChannel(rule.channel, channel) || !matchesDirection(rule.direction, direction)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static void setCapturedId(String key, byte[] value) {
        initialize();
        if (storeCapturedId(key, value)) triggerUpdateSequenceForRule(canonicalKey(resolveRuleName(key)));
    }

    private static boolean storeCapturedId(String key, byte[] value) {
        if (isBlank(key)) {
            return false;
        }
        String resolvedKey = canonicalKey(resolveRuleName(key));
        if (value == null || value.length == 0) {
            capturedValues.remove(resolvedKey);
            return false;
        }
        byte[] oldValue = capturedValues.get(resolvedKey);
        byte[] newValue = Arrays.copyOf(value, value.length);
        capturedValues.put(resolvedKey, newValue);
        if (!Arrays.equals(oldValue, newValue)) {
            markUpdated(resolvedKey);
            return true;
        }
        return false;
    }

    public static byte[] getCapturedIdBytes(String key) {
        initialize();
        if (isBlank(key)) {
            return null;
        }
        String ruleName = resolveRuleName(key);
        byte[] value = capturedValues.get(canonicalKey(ruleName));
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    public static String getCapturedIdHex(String key) {
        byte[] value = getCapturedIdBytes(key);
        return value == null ? null : bytesToHex(value);
    }

    public static void clearAllCapturedIds() {
        initialize();
        capturedValues.clear();
        capturedUpdateVersions.clear();
        capturedRecaptureVersions.clear();
        runtimeStatus.clear();
        resetUpdateSequenceRuntimeState();
    }

    public static List<String> getAllCategories() {
        initialize();
        List<String> result = new ArrayList<>();
        for (String category : customCategories) {
            addCategoryIfMissing(result, category);
        }

        boolean hasUngrouped = false;
        for (CaptureRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String category = normalizeCategory(rule.category);
            if (isBlank(category)) {
                hasUngrouped = true;
            } else {
                addCategoryIfMissing(result, category);
            }
        }

        if (hasUngrouped) {
            addCategoryIfMissing(result, CATEGORY_UNGROUPED);
        }

        return result;
    }

    public static synchronized boolean addCategory(String category) {
        initialize();
        String normalized = normalizeCategory(category);
        if (isBlank(normalized) || CATEGORY_UNGROUPED.equals(normalized)) {
            return false;
        }

        ConfigRoot root = loadCustomConfigRoot();
        if (root.categories == null) {
            root.categories = new ArrayList<>();
        }
        for (String existing : root.categories) {
            if (normalized.equalsIgnoreCase(normalizeCategory(existing))) {
                return true;
            }
        }
        root.categories.add(normalized);
        return writeCustomConfigRoot(root);
    }

    public static synchronized boolean deleteCategory(String category) {
        initialize();
        String normalized = normalizeCategory(category);
        if (isBlank(normalized) || CATEGORY_UNGROUPED.equals(normalized)) {
            return false;
        }

        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);

        boolean removed = root.categories.removeIf(existing ->
                normalized.equalsIgnoreCase(normalizeCategory(existing)));

        for (CaptureRule rule : root.rules) {
            if (rule != null && normalized.equalsIgnoreCase(normalizeCategory(rule.category))) {
                rule.category = "";
                removed = true;
            }
        }

        if (!removed) {
            return false;
        }
        return writeCustomConfigRoot(root);
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        initialize();
        String normalizedOld = normalizeCategory(oldCategory);
        String normalizedNew = normalizeCategory(newCategory);

        if (isBlank(normalizedOld) || CATEGORY_UNGROUPED.equals(normalizedOld)) {
            return false;
        }

        if (isBlank(normalizedNew) || CATEGORY_UNGROUPED.equals(normalizedNew)) {
            return false;
        }

        if (normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return true;
        }

        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);

        for (String existing : root.categories) {
            String normalizedExisting = normalizeCategory(existing);
            if (normalizedNew.equalsIgnoreCase(normalizedExisting)
                    && !normalizedOld.equalsIgnoreCase(normalizedExisting)) {
                return false;
            }
        }

        boolean changed = false;
        for (int i = 0; i < root.categories.size(); i++) {
            String existing = normalizeCategory(root.categories.get(i));
            if (normalizedOld.equalsIgnoreCase(existing)) {
                root.categories.set(i, normalizedNew);
                changed = true;
            }
        }

        for (CaptureRule rule : root.rules) {
            if (rule != null && normalizedOld.equalsIgnoreCase(normalizeCategory(rule.category))) {
                rule.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        List<String> deduplicated = new ArrayList<>();
        for (String existing : root.categories) {
            addCategoryIfMissing(deduplicated, existing);
        }
        root.categories = deduplicated;

        return writeCustomConfigRoot(root);
    }

    public static long getCapturedUpdateVersion(String key) {
        initialize();
        if (isBlank(key)) {
            return 0L;
        }
        String resolvedKey = canonicalKey(resolveRuleName(key));
        return capturedUpdateVersions.getOrDefault(resolvedKey, 0L);
    }

    public static long getCapturedRecaptureVersion(String key) {
        initialize();
        if (isBlank(key)) {
            return 0L;
        }
        String resolvedKey = canonicalKey(resolveRuleName(key));
        return capturedRecaptureVersions.getOrDefault(resolvedKey, 0L);
    }

    public static List<DisplayEntry> getDisplayEntries() {
        initialize();
        List<DisplayEntry> result = new ArrayList<>();
        Set<String> rendered = new LinkedHashSet<>();

        for (CaptureRule rule : rules) {
            if (rule == null || isBlank(rule.name)) {
                continue;
            }
            String key = canonicalKey(rule.name);
            if (!rendered.add(key)) {
                continue;
            }
            String displayName = isBlank(rule.displayName) ? rule.name : rule.displayName;
            result.add(new DisplayEntry(key, displayName, getCapturedIdHex(key)));
        }

        for (String key : capturedValues.keySet()) {
            if (!rendered.contains(key)) {
                result.add(new DisplayEntry(key, key, getCapturedIdHex(key)));
            }
        }

        return result;
    }

    public static List<RuleCard> getRuleCards() {
        initialize();
        List<RuleCard> result = new ArrayList<>();
        for (CaptureRule rule : rules) {
            if (rule == null) {
                continue;
            }
            RuleEditModel model = toEditModel(rule);
            String hex = null;
            if (!isBlank(rule.name)) {
                hex = getCapturedIdHex(rule.name);
            }
            result.add(new RuleCard(rule.customIndex, model, hex));
        }
        return result;
    }

    public static synchronized boolean addRule(RuleEditModel model) {
        initialize();
        if (model == null || isBlank(model.name) || isBlank(model.pattern)) {
            return false;
        }
        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);

        root.rules.add(fromEditModel(model, root.rules.size()));
        addCategoryToRootIfNeeded(root, model.category);
        return writeCustomConfigRoot(root);
    }

    public static synchronized boolean updateRule(int index, RuleEditModel model) {
        initialize();
        if (model == null || isBlank(model.name) || isBlank(model.pattern)) {
            return false;
        }
        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);
        if (index < 0 || index >= root.rules.size()) {
            return false;
        }
        root.rules.set(index, fromEditModel(model, index));
        addCategoryToRootIfNeeded(root, model.category);
        return writeCustomConfigRoot(root);
    }

    public static synchronized boolean deleteRule(int index) {
        initialize();
        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);
        if (index < 0 || index >= root.rules.size()) {
            return false;
        }
        root.rules.remove(index);
        return writeCustomConfigRoot(root);
    }

    public static synchronized boolean moveRuleToCategory(int index, String category) {
        initialize();
        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);
        if (index < 0 || index >= root.rules.size()) {
            return false;
        }

        CaptureRule rule = root.rules.get(index);
        if (rule == null) {
            return false;
        }
        rule.category = normalizeCategory(category);
        addCategoryToRootIfNeeded(root, rule.category);
        return writeCustomConfigRoot(root);
    }

    public static String exportRuleShareCode(RuleEditModel model, String capturedHex) {
        initialize();
        if (model == null || isBlank(model.name)) {
            throw new IllegalArgumentException("规则为空，无法导出");
        }

        RuleSharePayload payload = new RuleSharePayload();
        payload.rule = cloneRuleEditModel(model);
        payload.capturedHex = nullToEmpty(capturedHex).trim();

        byte[] rawBytes = COMPACT_GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(rawBytes);
        return RULE_SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
    }

    public static synchronized String importRuleShareCode(String shareCode, String fallbackCategory) {
        initialize();
        RuleSharePayload payload = decodeRuleSharePayload(shareCode);
        if (payload == null || payload.rule == null || isBlank(payload.rule.name) || isBlank(payload.rule.pattern)) {
            throw new IllegalArgumentException("规则分享码无效");
        }

        RuleEditModel imported = cloneRuleEditModel(payload.rule);
        if (isBlank(imported.category)) {
            imported.category = normalizeCategory(fallbackCategory);
        }
        imported.name = makeUniqueRuleName(imported.name);

        ConfigRoot root = loadCustomConfigRoot();
        ensureRootLists(root);
        root.rules.add(fromEditModel(imported, root.rules.size()));
        addCategoryToRootIfNeeded(root, imported.category);

        if (!writeCustomConfigRoot(root)) {
            throw new IllegalStateException("导入规则失败");
        }

        if (!isBlank(payload.capturedHex)) {
            byte[] bytes = parseHexBytes(payload.capturedHex);
            if (bytes != null && bytes.length > 0) {
                setCapturedId(imported.name, bytes);
            }
        }

        return imported.name;
    }

    private static RuleEditModel cloneRuleEditModel(RuleEditModel model) {
        RuleEditModel copy = new RuleEditModel();
        copy.name = nullToEmpty(model.name);
        copy.displayName = nullToEmpty(model.displayName);
        copy.note = nullToEmpty(model.note);
        copy.aliasesCsv = nullToEmpty(model.aliasesCsv);
        copy.enabled = model.enabled;
        copy.channel = nullToEmpty(model.channel);
        copy.direction = nullToEmpty(model.direction);
        copy.target = nullToEmpty(model.target);
        copy.pattern = nullToEmpty(model.pattern);
        copy.offset = nullToEmpty(model.offset);
        copy.group = model.group;
        copy.category = normalizeCategory(model.category);
        copy.valueType = nullToEmpty(model.valueType);
        copy.byteLength = model.byteLength;
        copy.updateSequenceName = nullToEmpty(model.updateSequenceName);
        copy.updateSequenceMode = nullToEmpty(model.updateSequenceMode);
        copy.updateSequenceCooldownMs = model.updateSequenceCooldownMs;
        return copy;
    }

    static byte[] parseValue(String rawValue, String valueType, int byteLength) {
        if (isBlank(rawValue)) {
            return null;
        }

        if ("decimal-int".equalsIgnoreCase(valueType)) {
            int length = Math.max(1, byteLength);
            long value = Long.parseLong(rawValue.trim());
            byte[] full = ByteBuffer.allocate(8).putLong(value).array();
            byte[] out = new byte[length];
            System.arraycopy(full, full.length - length, out, 0, length);
            return out;
        }

        String normalized = rawValue.replaceAll("[^0-9A-Fa-f]", "");
        if (normalized.isEmpty() || normalized.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int idx = i * 2;
            out[i] = (byte) Integer.parseInt(normalized.substring(idx, idx + 2), 16);
        }
        return out;
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
            out[i] = (byte) Integer.parseInt(normalized.substring(idx, idx + 2), 16);
        }
        return out;
    }

    static byte[] applyRuleOffset(byte[] baseValue, String offsetText) {
        if (baseValue == null || baseValue.length == 0 || isBlank(offsetText)) {
            return baseValue;
        }

        String text = offsetText.trim();
        boolean minus = text.startsWith("-");
        if (text.startsWith("+") || text.startsWith("-")) {
            text = text.substring(1).trim();
        }

        String normalizedHex = text.replaceAll("[^0-9A-Fa-f]", "");
        if (normalizedHex.isEmpty()) {
            return baseValue;
        }

        BigInteger base = new BigInteger(1, baseValue);
        BigInteger delta = new BigInteger(normalizedHex, 16);
        BigInteger mod = BigInteger.ONE.shiftLeft(baseValue.length * 8);

        BigInteger result = minus ? base.subtract(delta) : base.add(delta);
        result = result.mod(mod);

        byte[] raw = result.toByteArray();
        byte[] fixed = new byte[baseValue.length];
        int copyLength = Math.min(raw.length, fixed.length);
        System.arraycopy(raw, raw.length - copyLength, fixed, fixed.length - copyLength, copyLength);
        return fixed;
    }

    private static RuleEditModel toEditModel(CaptureRule rule) {
        RuleEditModel model = new RuleEditModel();
        model.name = rule.name;
        model.displayName = rule.displayName;
        model.note = rule.note;
        model.aliasesCsv = joinAliases(rule.aliases);
        model.enabled = rule.enabled;
        model.channel = rule.channel;
        model.direction = isBlank(rule.direction) ? "both" : rule.direction;
        model.target = isBlank(rule.target) ? "hex" : rule.target;
        model.pattern = rule.pattern;
        model.offset = nullToEmpty(rule.offset);
        model.group = rule.group <= 0 ? 1 : rule.group;
        model.category = normalizeCategory(rule.category);
        model.valueType = isBlank(rule.valueType) ? "hex" : rule.valueType;
        model.byteLength = rule.byteLength <= 0 ? 4 : rule.byteLength;
        model.updateSequenceName = nullToEmpty(rule.updateSequenceName);
        model.updateSequenceMode = isBlank(rule.updateSequenceMode) ? "always" : rule.updateSequenceMode;
        model.updateSequenceCooldownMs = rule.updateSequenceCooldownMs <= 0 ? 1000 : rule.updateSequenceCooldownMs;
        return model;
    }

    private static CaptureRule fromEditModel(RuleEditModel model, int customIndex) {
        CaptureRule rule = new CaptureRule();
        rule.name = nullToEmpty(model.name).trim();
        rule.displayName = nullToEmpty(model.displayName).trim();
        rule.note = nullToEmpty(model.note).trim();
        rule.aliases = parseAliasesCsv(model.aliasesCsv);
        rule.enabled = model.enabled;
        rule.channel = nullToEmpty(model.channel).trim();
        rule.direction = normalizeDirection(model.direction);
        rule.target = normalizeTarget(model.target);
        rule.pattern = nullToEmpty(model.pattern).trim();
        rule.offset = nullToEmpty(model.offset).trim();
        rule.group = Math.max(1, model.group);
        rule.category = normalizeCategory(model.category);
        rule.valueType = normalizeValueType(model.valueType);
        rule.byteLength = Math.max(1, model.byteLength);
        rule.updateSequenceName = nullToEmpty(model.updateSequenceName).trim();
        rule.updateSequenceMode = normalizeUpdateSequenceMode(model.updateSequenceMode);
        rule.updateSequenceCooldownMs = Math.max(1, model.updateSequenceCooldownMs);
        rule.customIndex = customIndex;
        return rule;
    }

    private static void resetUpdateSequenceRuntimeState() {
        for (CaptureRule rule : rules) {
            if (rule == null) {
                continue;
            }
            rule.updateSequenceTriggered = false;
            rule.nextUpdateSequenceAllowedAt = 0L;
        }
    }

    private static void triggerUpdateSequenceForRule(String canonicalRuleName) {
        for (CaptureRule rule : rules) {
            if (canonicalKey(rule.name).equals(canonicalRuleName)) {
                triggerUpdateSequenceForRule(rule, false);
                return;
            }
        }
    }

    private static void triggerUpdateSequenceForRule(CaptureRule rule, boolean forceRecapture) {
        if (isBlank(rule.updateSequenceName)) {
            recordRuntimeStatus(rule.name, "未绑定更新序列", false);
            return;
        }
        final String sequenceName = rule.updateSequenceName.trim();
        final String mode = normalizeUpdateSequenceMode(rule.updateSequenceMode);
        if (forceRecapture != "recapture".equals(mode)) return;
        recordRuntimeStatus(rule.name, "已排队启动：" + sequenceName, false);
        Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    if (!rules.contains(rule) || !rule.enabled) {
                        recordRuntimeStatus(rule.name, "规则已重新加载或停用，旧触发取消", false);
                        return;
                    }
                    PathSequenceManager.PathSequence sequence = PathSequenceManager.getSequence(sequenceName);
                    if (sequence == null || sequence.getSteps().isEmpty()) {
                        recordRuntimeStatus(rule.name, "启动失败：序列不存在或没有步骤：" + sequenceName, false);
                        zszlScriptMod.LOGGER.warn("[CapturedId] 序列不存在或没有步骤: {} -> {}", rule.name, sequenceName);
                        return;
                    }
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.player == null || mc.world == null) {
                        recordRuntimeStatus(rule.name, "启动失败：当前不在游戏世界", false);
                        return;
                    }
                    synchronized (rule) {
                        long now = System.currentTimeMillis();
                        if ("first".equals(mode)) {
                            if (rule.updateSequenceTriggered) {
                                recordRuntimeStatus(rule.name, "首次更新模式已触发过", false);
                                return;
                            }
                            rule.updateSequenceTriggered = true;
                        } else if ("cooldown".equals(mode)) {
                            if (now < rule.nextUpdateSequenceAllowedAt) {
                                recordRuntimeStatus(rule.name, "冷却中", false);
                                return;
                            }
                            rule.nextUpdateSequenceAllowedAt = now + Math.max(1, rule.updateSequenceCooldownMs);
                        }
                    }
                    PathSequenceManager.runPathSequenceOnce(sequenceName);
                    recordRuntimeStatus(rule.name, "已调用序列启动（1次）：" + sequenceName, false);
                    zszlScriptMod.LOGGER.info("[CapturedId] 实时匹配后启动一次序列: {} -> {}", rule.name, sequenceName);
                } catch (Exception e) {
                    recordRuntimeStatus(rule.name, "启动异常：" + e.getMessage(), false);
                    zszlScriptMod.LOGGER.error("[CapturedId] 更新值后执行序列失败: {} -> {}", rule.name,
                            rule.updateSequenceName, e);
                }
            });
    }

    private static ConfigRoot loadConfigRoot(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
            if (root == null) {
                root = new ConfigRoot();
            }
            ensureRootLists(root);
            return root;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[CapturedId] 读取规则文件失败: {}", path, e);
            return new ConfigRoot();
        }
    }

    private static ConfigRoot loadCustomConfigRoot() {
        ensureCustomConfigExists();
        return loadConfigRoot(CUSTOM_CONFIG_PATH);
    }

    private static void ensureRootLists(ConfigRoot root) {
        if (root.rules == null) {
            root.rules = new ArrayList<>();
        }
        if (root.categories == null) {
            root.categories = new ArrayList<>();
        }
    }

    private static boolean writeCustomConfigRoot(ConfigRoot root) {
        try {
            if (root == null) {
                root = new ConfigRoot();
            }
            ensureRootLists(root);

            String json = GSON.toJson(root);
            Files.write(CUSTOM_CONFIG_PATH, Collections.singletonList(json), StandardCharsets.UTF_8);
            reloadRules();
            clearStaleCapturedValues();
            return true;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[CapturedId] 保存规则文件失败: {}", CUSTOM_CONFIG_PATH, e);
            return false;
        }
    }

    private static void addCategoryToRootIfNeeded(ConfigRoot root, String category) {
        String normalized = normalizeCategory(category);
        if (isBlank(normalized) || CATEGORY_UNGROUPED.equals(normalized)) {
            return;
        }
        ensureRootLists(root);
        for (String existing : root.categories) {
            if (normalized.equalsIgnoreCase(normalizeCategory(existing))) {
                return;
            }
        }
        root.categories.add(normalized);
    }

    private static void clearStaleCapturedValues() {
        Set<String> validKeys = new LinkedHashSet<>();
        for (CaptureRule rule : rules) {
            if (rule == null || isBlank(rule.name)) {
                continue;
            }
            validKeys.add(canonicalKey(rule.name));
            if (rule.aliases != null) {
                for (String alias : rule.aliases) {
                    if (!isBlank(alias)) {
                        validKeys.add(canonicalKey(alias));
                    }
                }
            }
        }
        capturedValues.keySet().removeIf(key -> !validKeys.contains(key));
        capturedUpdateVersions.keySet().removeIf(key -> !validKeys.contains(key));
        capturedRecaptureVersions.keySet().removeIf(key -> !validKeys.contains(key));
        runtimeStatus.keySet().removeIf(key -> !validKeys.contains(key));
    }

    private static List<String> parseAliasesCsv(String csv) {
        if (isBlank(csv)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        String[] parts = csv.split(",");
        for (String part : parts) {
            String alias = nullToEmpty(part).trim();
            if (!alias.isEmpty()) {
                result.add(alias);
            }
        }
        return result;
    }

    private static String joinAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "";
        }
        return String.join(",", aliases);
    }

    private static void markUpdated(String key) {
        String canonical = canonicalKey(resolveRuleName(key));
        if (canonical.isEmpty()) {
            return;
        }
        capturedUpdateVersions.merge(canonical, 1L, Long::sum);
    }

    private static void markRecaptured(String key) {
        String canonical = canonicalKey(resolveRuleName(key));
        if (canonical.isEmpty()) {
            return;
        }
        capturedRecaptureVersions.merge(canonical, 1L, Long::sum);
    }

    private static String normalizeDirection(String direction) {
        String value = canonicalKey(direction);
        if ("inbound".equals(value) || "outbound".equals(value) || "both".equals(value)) {
            return value;
        }
        return "both";
    }

    private static String normalizeTarget(String target) {
        String value = canonicalKey(target);
        if ("decoded".equals(value) || "hex".equals(value)) {
            return value;
        }
        return "hex";
    }

    private static String normalizeValueType(String valueType) {
        String value = canonicalKey(valueType);
        if ("decimal-int".equals(value) || "hex".equals(value)) {
            return value;
        }
        return "hex";
    }

    private static String normalizeUpdateSequenceMode(String mode) {
        String value = canonicalKey(mode);
        if ("first".equals(value) || "always".equals(value) || "cooldown".equals(value)
                || "recapture".equals(value)) {
            return value;
        }
        return "always";
    }

    private static String normalizeCategory(String category) {
        return category == null ? "" : category.trim();
    }

    private static String resolveRuleName(String key) {
        String normalized = canonicalKey(key);
        for (CaptureRule rule : rules) {
            if (rule == null || isBlank(rule.name)) {
                continue;
            }
            if (normalized.equals(canonicalKey(rule.name))) {
                return rule.name;
            }
            if (rule.aliases != null) {
                for (String alias : rule.aliases) {
                    if (normalized.equals(canonicalKey(alias))) {
                        return rule.name;
                    }
                }
            }
        }
        return normalized;
    }

    static boolean matchesChannel(String expected, String actual) {
        if (isBlank(expected)) {
            return true;
        }
        return expected.equalsIgnoreCase(actual);
    }

    static boolean matchesDirection(String expected, String actual) {
        if (isBlank(expected) || "both".equalsIgnoreCase(expected)) {
            return true;
        }
        return expected.equalsIgnoreCase(actual);
    }

    private static List<CaptureRule> loadRulesFromRoot(ConfigRoot root) {
        List<CaptureRule> loaded = new ArrayList<>();
        if (root == null || root.rules == null) {
            return loaded;
        }

        int customOrder = 0;
        for (CaptureRule rule : root.rules) {
            if (rule == null || isBlank(rule.name) || isBlank(rule.pattern)) {
                continue;
            }

            try {
                rule.compiledPattern = Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE);
                rule.customIndex = customOrder++;
                loaded.add(rule);
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("[CapturedId] 编译规则失败: {}", rule.name, e);
            }
        }
        return Collections.unmodifiableList(loaded);
    }

    private static void ensureCustomConfigExists() {
        try {
            if (CUSTOM_CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CUSTOM_CONFIG_PATH.getParent());
            }
            if (Files.exists(CUSTOM_CONFIG_PATH)) {
                return;
            }

            Files.write(CUSTOM_CONFIG_PATH,
                    Collections.singletonList("{\"rules\":[],\"categories\":[]}"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("[CapturedId] 创建自定义规则文件失败: {}", CUSTOM_CONFIG_PATH, e);
        }
    }

    private static void addCategoryIfMissing(List<String> categories, String category) {
        String normalized = normalizeCategory(category);
        if (isBlank(normalized)) {
            return;
        }
        for (String existing : categories) {
            if (normalized.equalsIgnoreCase(normalizeCategory(existing))) {
                return;
            }
        }
        categories.add(normalized);
    }

    private static String makeUniqueRuleName(String desiredName) {
        String base = nullToEmpty(desiredName).trim();
        if (base.isEmpty()) {
            base = "imported_rule";
        }

        String candidate = base;
        int suffix = 1;
        while (hasRuleName(candidate)) {
            suffix++;
            candidate = base + "_imported_" + suffix;
        }
        return candidate;
    }

    private static boolean hasRuleName(String name) {
        String canonical = canonicalKey(name);
        for (CaptureRule rule : rules) {
            if (rule != null && canonical.equals(canonicalKey(rule.name))) {
                return true;
            }
        }
        return false;
    }

    private static RuleSharePayload decodeRuleSharePayload(String shareCode) {
        if (isBlank(shareCode)) {
            throw new IllegalArgumentException("规则分享码不能为空");
        }

        String normalized = shareCode.replaceAll("\\s+", "").trim();
        if (normalized.startsWith(RULE_SHARE_PREFIX)) {
            normalized = normalized.substring(RULE_SHARE_PREFIX.length());
        }

        String json;
        if (normalized.startsWith("{")) {
            json = normalized;
        } else {
            byte[] decoded = decodeBase64Lenient(normalized);
            byte[] inflated = tryInflateLenient(decoded);
            json = new String(inflated, StandardCharsets.UTF_8);
        }

        RuleSharePayload payload = GSON.fromJson(json, RuleSharePayload.class);
        if (payload == null) {
            throw new IllegalArgumentException("规则分享码内容为空");
        }
        return payload;
    }

    private static byte[] deflate(byte[] rawBytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
            try (DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater)) {
                stream.write(rawBytes);
            } finally {
                deflater.end();
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("压缩规则失败", e);
        }
    }

    private static byte[] decodeBase64Lenient(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("规则分享码不是有效的 Base64 数据", e);
        }
    }

    private static byte[] tryInflateLenient(byte[] compressed) {
        try {
            return inflateBytes(compressed, true);
        } catch (Exception ignored) {
        }
        try {
            return inflateBytes(compressed, false);
        } catch (Exception ignored) {
        }
        return compressed;
    }

    private static byte[] inflateBytes(byte[] compressed, boolean nowrap) throws IOException {
        Inflater inflater = new Inflater(nowrap);
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed), inflater)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private static String canonicalKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

