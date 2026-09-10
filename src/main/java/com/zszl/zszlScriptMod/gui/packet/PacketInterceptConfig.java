package com.zszl.zszlScriptMod.gui.packet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PacketInterceptConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static PacketInterceptConfig INSTANCE = new PacketInterceptConfig();

    public boolean inboundInterceptEnabled = false;
    public List<InterceptRule> inboundRules = new ArrayList<>();
    public List<String> categories = new ArrayList<>();

    public static final String BUILTIN_SMART_COPY_RULE_NAME = "聊天run_command动态改copy";

    public static class InterceptRule {
        public String name = "new_rule";
        public String category = "";
        public boolean enabled = true;
        // 支持标准包过滤：可填 ID:0x44 / ID:68 / SPacketTeams / teams（大小写不敏感）
        public String packetFilter = "";
        public String channel = "";
        public String matchHex = "";
        public String replaceHex = "";
        // 开启后，matchHex 按 Java 正则表达式处理，replaceHex 支持 $1/$2 等分组替换
        public boolean regexEnabled = false;
        public boolean replaceAll = true;

        public InterceptRule copy() {
            InterceptRule c = new InterceptRule();
            c.name = this.name;
            c.category = this.category;
            c.enabled = this.enabled;
            c.packetFilter = this.packetFilter;
            c.channel = this.channel;
            c.matchHex = this.matchHex;
            c.replaceHex = this.replaceHex;
            c.regexEnabled = this.regexEnabled;
            c.replaceAll = this.replaceAll;
            return c;
        }
    }

    private PacketInterceptConfig() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("packet_intercept_rules.json");
    }

    public static void save() {
        removeBuiltinSmartCopyRules();
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to save packet intercept config", e);
        }
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, PacketInterceptConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new PacketInterceptConfig();
                }
                if (INSTANCE.inboundRules == null) {
                    INSTANCE.inboundRules = new ArrayList<>();
                }
                if (INSTANCE.categories == null) INSTANCE.categories = new ArrayList<>();
                for (InterceptRule rule : INSTANCE.inboundRules) {
                    normalizeRule(rule);
                }
                removeBuiltinSmartCopyRules();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("Failed to load packet intercept config", e);
                INSTANCE = new PacketInterceptConfig();
                removeBuiltinSmartCopyRules();
            }
        } else {
            INSTANCE = new PacketInterceptConfig();
            removeBuiltinSmartCopyRules();
        }
    }

    private static InterceptRule createBuiltinSmartCopyRule(boolean enabled) {
        InterceptRule rule = new InterceptRule();
        applyBuiltinSmartCopyRule(rule, enabled);
        return rule;
    }

    public static void applyBuiltinSmartCopyRule(InterceptRule rule, boolean enabled) {
        rule.name = BUILTIN_SMART_COPY_RULE_NAME;
        rule.enabled = enabled;
        rule.packetFilter = "0x0F";
        rule.channel = "";
        rule.matchHex = "(?<prefix>2272756E5F636F6D6D616E64222C2276616C7565223A22)(?<cmd>[0-9A-Fa-f]{2,}?)(?<suffix>22)(?=[0-9A-Fa-f]*?E78EA9E5AEB6E5908DE7A7B0C2A7395D20C2A7[0-9A-Fa-f]{2}(?<name>[0-9A-Fa-f]{4,40})5C6E)";
        rule.replaceHex = "{prefix}{copycmd:name:cmd}{suffix}";
        rule.regexEnabled = true;
        rule.replaceAll = true;
    }

    public static boolean isBuiltinSmartCopyRule(InterceptRule rule) {
        return rule != null && BUILTIN_SMART_COPY_RULE_NAME.equals(rule.name);
    }

    public static void ensureBuiltinRules() {
        removeBuiltinSmartCopyRules();
    }

    public static void removeBuiltinSmartCopyRules() {
        if (INSTANCE.inboundRules == null) {
            INSTANCE.inboundRules = new ArrayList<>();
            return;
        }
        for (int i = INSTANCE.inboundRules.size() - 1; i >= 0; i--) {
            if (isBuiltinSmartCopyRule(INSTANCE.inboundRules.get(i))) {
                INSTANCE.inboundRules.remove(i);
            }
        }
    }

    public static boolean addCategory(String category) {
        String value = category == null ? "" : category.trim();
        if (value.isEmpty()) return false;
        if (INSTANCE.categories == null) INSTANCE.categories = new ArrayList<>();
        for (String item : INSTANCE.categories) if (value.equalsIgnoreCase(item)) return true;
        INSTANCE.categories.add(value);
        save();
        return true;
    }

    public static boolean deleteCategory(String category) {
        String value = category == null ? "" : category.trim();
        if (value.isEmpty() || INSTANCE.categories == null) return false;
        boolean changed = false;
        for (int i = INSTANCE.categories.size() - 1; i >= 0; i--) if (value.equalsIgnoreCase(INSTANCE.categories.get(i))) { INSTANCE.categories.remove(i); changed = true; }
        if (INSTANCE.inboundRules != null) for (InterceptRule rule : INSTANCE.inboundRules) if (rule != null && value.equalsIgnoreCase(rule.category)) { rule.category = ""; changed = true; }
        if (changed) save();
        return changed;
    }

    public static boolean renameCategory(String oldCategory, String newCategory) {
        String oldValue = oldCategory == null ? "" : oldCategory.trim(), newValue = newCategory == null ? "" : newCategory.trim();
        if (oldValue.isEmpty() || newValue.isEmpty() || oldValue.equalsIgnoreCase(newValue) || INSTANCE.categories == null) return false;
        for (String item : INSTANCE.categories) if (newValue.equalsIgnoreCase(item)) return false;
        boolean changed = false;
        for (int i = 0; i < INSTANCE.categories.size(); i++) if (oldValue.equalsIgnoreCase(INSTANCE.categories.get(i))) { INSTANCE.categories.set(i, newValue); changed = true; }
        if (INSTANCE.inboundRules != null) for (InterceptRule rule : INSTANCE.inboundRules) if (rule != null && oldValue.equalsIgnoreCase(rule.category)) { rule.category = newValue; changed = true; }
        if (changed) save();
        return changed;
    }

    public static void normalizeRule(InterceptRule rule) {
        if (rule == null) {
            return;
        }
        if (rule.name == null)
            rule.name = "new_rule";
        if (rule.category == null)
            rule.category = "";
        if (rule.packetFilter == null)
            rule.packetFilter = "";
        if (rule.channel == null)
            rule.channel = "";
        if (rule.matchHex == null)
            rule.matchHex = "";
        if (rule.replaceHex == null)
            rule.replaceHex = "";
    }
}
