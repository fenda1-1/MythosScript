package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketInterceptConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.PacketFieldRuleManager;

/**
 * Working copies used by packet editors. The managers remain the source of
 * truth; this class only makes cancel/escape deterministic and testable.
 */
final class PacketWorkbenchState {
    private PacketWorkbenchState() {
    }

    static final class Filter {
        private final PacketFilterConfig config;
        private List<String> originalWhite;
        private List<String> originalBlack;
        private int originalMax;
        private PacketCaptureHandler.CaptureMode originalMode;
        private boolean originalBusiness;
        private boolean originalAdaptive;
        private int originalThreshold;
        private int originalModulo;
        private List<String> white;
        private List<String> black;
        private int max;
        private PacketCaptureHandler.CaptureMode mode;
        private boolean business;
        private boolean adaptive;
        private int threshold;
        private int modulo;

        Filter(PacketFilterConfig config) {
            if (config == null) throw new IllegalArgumentException("config must not be null");
            this.config = config;
            originalWhite = copy(config.whitelistFilters);
            originalBlack = copy(config.blacklistFilters);
            originalMax = config.maxCapturedPackets;
            originalMode = config.captureMode == null ? PacketCaptureHandler.CaptureMode.BLACKLIST : config.captureMode;
            originalBusiness = config.enableBusinessPacketProcessing;
            originalAdaptive = config.enableAdaptiveSampling;
            originalThreshold = config.adaptiveSamplingQueueThreshold;
            originalModulo = config.adaptiveSamplingModulo;
            white = copy(originalWhite); black = copy(originalBlack); max = originalMax;
            mode = originalMode; business = originalBusiness; adaptive = originalAdaptive;
            threshold = originalThreshold; modulo = originalModulo;
        }

        void setWhitelist(String value) { white = split(value); }
        void setBlacklist(String value) { black = split(value); }
        void setMax(int value) { max = clamp(value, 100, 10000); }
        void setMode(PacketCaptureHandler.CaptureMode value) { if (value != null) mode = value; }
        void setBusiness(boolean value) { business = value; }
        void setAdaptive(boolean value) { adaptive = value; }
        void setThreshold(int value) { threshold = clamp(value, 200, 10000); }
        void setModulo(int value) { modulo = clamp(value, 2, 64); }
        void cycleMode() { mode = mode.next(); }
        List<String> whitelist() { return Collections.unmodifiableList(white); }
        List<String> blacklist() { return Collections.unmodifiableList(black); }
        int max() { return max; }
        PacketCaptureHandler.CaptureMode mode() { return mode; }
        boolean business() { return business; }
        boolean adaptive() { return adaptive; }
        int threshold() { return threshold; }
        int modulo() { return modulo; }

        void save() {
            config.whitelistFilters = copy(white); config.blacklistFilters = copy(black);
            config.maxCapturedPackets = max; config.captureMode = mode;
            config.enableBusinessPacketProcessing = business; config.enableAdaptiveSampling = adaptive;
            config.adaptiveSamplingQueueThreshold = threshold; config.adaptiveSamplingModulo = modulo;
            PacketFilterConfig.save();
            originalWhite = copy(white); originalBlack = copy(black); originalMax = max;
            originalMode = mode; originalBusiness = business; originalAdaptive = adaptive;
            originalThreshold = threshold; originalModulo = modulo;
        }

        boolean isDirty() {
            return !white.equals(originalWhite) || !black.equals(originalBlack) || max != originalMax
                    || mode != originalMode || business != originalBusiness || adaptive != originalAdaptive
                    || threshold != originalThreshold || modulo != originalModulo;
        }

        void cancel() {
            white = copy(originalWhite); black = copy(originalBlack); max = originalMax;
            mode = originalMode; business = originalBusiness; adaptive = originalAdaptive;
            threshold = originalThreshold; modulo = originalModulo;
            config.whitelistFilters = copy(originalWhite); config.blacklistFilters = copy(originalBlack);
            config.maxCapturedPackets = originalMax; config.captureMode = originalMode;
            config.enableBusinessPacketProcessing = originalBusiness; config.enableAdaptiveSampling = originalAdaptive;
            config.adaptiveSamplingQueueThreshold = originalThreshold; config.adaptiveSamplingModulo = originalModulo;
        }
    }

    static final class FieldRules {
        private List<PacketFieldRuleManager.RuleEditModel> original;
        private final List<PacketFieldRuleManager.RuleEditModel> models;
        FieldRules() { original = copyField(PacketFieldRuleManager.getRuleModels()); models = copyField(original); }
        List<PacketFieldRuleManager.RuleEditModel> models() { return models; }
        void add() {
            PacketFieldRuleManager.RuleEditModel model = new PacketFieldRuleManager.RuleEditModel();
            model.name = "field_rule_" + (models.size() + 1); model.variableName = model.name; models.add(model);
        }
        void remove(int index) { if (valid(index)) models.remove(index); }
        void save() { PacketFieldRuleManager.saveRuleModels(copyField(models)); original = copyField(models); }
        void cancel() { models.clear(); models.addAll(copyField(original)); }
        boolean isDirty() { return !sameFieldModels(original, models); }
        /** Reloads the manager-backed source and resets both working and baseline copies. */
        void reload() {
            PacketFieldRuleManager.reloadRules();
            original = copyField(PacketFieldRuleManager.getRuleModels());
            models.clear();
            models.addAll(copyField(original));
        }
        boolean valid(int index) { return index >= 0 && index < models.size(); }
    }

    static final class InterceptRules {
        private boolean originalEnabled;
        private List<PacketInterceptConfig.InterceptRule> original;
        private final List<PacketInterceptConfig.InterceptRule> rules;
        private boolean enabled;
        private List<String> originalCategories;
        private final List<String> categories;
        InterceptRules() {
            PacketInterceptConfig.ensureBuiltinRules();
            originalEnabled = PacketInterceptConfig.INSTANCE.inboundInterceptEnabled;
            original = copyIntercept(PacketInterceptConfig.INSTANCE.inboundRules);
            originalCategories = copy(PacketInterceptConfig.INSTANCE.categories);
            categories = copy(originalCategories);
            rules = copyIntercept(original); enabled = originalEnabled;
        }
        List<PacketInterceptConfig.InterceptRule> rules() { return rules; }
        List<String> categories() { return categories; }
        void addCategory(String value) { if (value != null && !value.trim().isEmpty() && !containsIgnoreCase(categories, value.trim())) categories.add(value.trim()); }
        void renameCategory(String oldValue, String newValue) { for (int i = 0; i < categories.size(); i++) if (categories.get(i).equalsIgnoreCase(oldValue)) categories.set(i, newValue); for (PacketInterceptConfig.InterceptRule rule : rules) if (rule.category != null && rule.category.equalsIgnoreCase(oldValue)) rule.category = newValue; }
        void deleteCategory(String value) { categories.removeIf(item -> item.equalsIgnoreCase(value)); for (PacketInterceptConfig.InterceptRule rule : rules) if (rule.category != null && rule.category.equalsIgnoreCase(value)) rule.category = ""; }
        boolean enabled() { return enabled; }
        void toggleEnabled() { enabled = !enabled; }
        void add() {
            PacketInterceptConfig.InterceptRule rule = new PacketInterceptConfig.InterceptRule();
            rule.name = "rule_" + (rules.size() + 1);
            rule.channel = "OwlViewChannel";
            rules.add(rule);
        }
        void remove(int index) {
            if (index >= 0 && index < rules.size()) rules.remove(index);
        }
        void save() {
            PacketInterceptConfig.INSTANCE.inboundInterceptEnabled = enabled;
            PacketInterceptConfig.INSTANCE.inboundRules = copyIntercept(rules);
            PacketInterceptConfig.INSTANCE.categories = copy(categories);
            PacketInterceptConfig.ensureBuiltinRules(); PacketInterceptConfig.save();
            originalEnabled = PacketInterceptConfig.INSTANCE.inboundInterceptEnabled;
            original = copyIntercept(PacketInterceptConfig.INSTANCE.inboundRules);
            originalCategories = copy(PacketInterceptConfig.INSTANCE.categories); categories.clear(); categories.addAll(copy(originalCategories));
        }
        void cancel() {
            rules.clear(); rules.addAll(copyIntercept(original)); enabled = originalEnabled;
            PacketInterceptConfig.INSTANCE.inboundInterceptEnabled = originalEnabled;
            PacketInterceptConfig.INSTANCE.inboundRules = copyIntercept(original);
            PacketInterceptConfig.INSTANCE.categories = copy(originalCategories);
        }
        boolean isDirty() { return enabled != originalEnabled || !categories.equals(originalCategories) || !sameInterceptRules(original, rules); }
        /** Reloads the on-disk config, retaining builtin normalization semantics. */
        void reload() {
            PacketInterceptConfig.load();
            PacketInterceptConfig.ensureBuiltinRules();
            originalEnabled = PacketInterceptConfig.INSTANCE.inboundInterceptEnabled;
            original = copyIntercept(PacketInterceptConfig.INSTANCE.inboundRules);
            originalCategories = copy(PacketInterceptConfig.INSTANCE.categories); categories.clear(); categories.addAll(copy(originalCategories));
            rules.clear();
            rules.addAll(copyIntercept(original));
            enabled = originalEnabled;
        }
    }

    static final class Sequence {
        private PacketSequence original;
        private final PacketSequence working;
        Sequence(PacketSequence source, String fallbackName) {
            original = source == null ? null : PacketDrafts.copySequence(source);
            working = source == null ? new PacketSequence(fallbackName == null ? "sequence" : fallbackName)
                    : PacketDrafts.copySequence(source);
        }
        PacketSequence value() { return working; }
        void addStandard(int id, String hex, int delay) { working.packets.add(new PacketSequence.PacketToSend(id, safe(hex), Math.max(0, delay))); }
        void addChannel(String channel, String hex, int delay) { working.packets.add(new PacketSequence.PacketToSend(safe(channel), safe(hex), Math.max(0, delay))); }
        void delete(int index) { if (valid(index)) working.packets.remove(index); }
        void move(int index, boolean up) {
            int target = up ? index - 1 : index + 1;
            if (valid(index) && target >= 0 && target < working.packets.size()) Collections.swap(working.packets, index, target);
        }
        boolean hasOriginal() { return original != null; }
        boolean isDirty() { return original == null ? !working.packets.isEmpty() : !sameSequence(original, working); }
        void markSaved() { original = PacketDrafts.copySequence(working); }
        boolean valid(int index) { return index >= 0 && index < working.packets.size(); }
    }

    private static boolean sameSequence(PacketSequence first, PacketSequence second) {
        if (first == second) return true;
        if (first == null || second == null || !safe(first.name).equals(safe(second.name))) return false;
        if (first.packets == null || second.packets == null) return first.packets == second.packets;
        if (first.packets.size() != second.packets.size()) return false;
        for (int i = 0; i < first.packets.size(); i++) {
            PacketSequence.PacketToSend left = first.packets.get(i);
            PacketSequence.PacketToSend right = second.packets.get(i);
            if (left == right) continue;
            if (left == null || right == null || left.isFmlPacket != right.isFmlPacket) return false;
            if (left.packetId == null ? right.packetId != null : !left.packetId.equals(right.packetId)) return false;
            if (!safe(left.channel).equals(safe(right.channel))
                    || !safe(left.hexData).equals(safe(right.hexData))
                    || left.delayTicks != right.delayTicks
                    || !safe(left.direction).equals(safe(right.direction))) return false;
        }
        return true;
    }

    static List<PacketFieldRuleManager.RuleEditModel> copyField(List<PacketFieldRuleManager.RuleEditModel> source) {
        List<PacketFieldRuleManager.RuleEditModel> result = new ArrayList<>();
        if (source == null) return result;
        for (PacketFieldRuleManager.RuleEditModel value : source) {
            if (value == null) continue;
            PacketFieldRuleManager.RuleEditModel copy = new PacketFieldRuleManager.RuleEditModel();
            copy.name = value.name; copy.category = value.category; copy.enabled = value.enabled; copy.channel = value.channel;
            copy.direction = value.direction; copy.source = value.source; copy.extractMode = value.extractMode;
            copy.pattern = value.pattern; copy.group = value.group; copy.variableName = value.variableName;
            copy.valueType = value.valueType; copy.scope = value.scope; copy.writeDefaultOnFailure = value.writeDefaultOnFailure;
            copy.defaultValue = value.defaultValue; copy.note = value.note; result.add(copy);
        }
        return result;
    }

    static List<PacketInterceptConfig.InterceptRule> copyIntercept(List<PacketInterceptConfig.InterceptRule> source) {
        List<PacketInterceptConfig.InterceptRule> result = new ArrayList<>();
        if (source != null) for (PacketInterceptConfig.InterceptRule rule : source) if (rule != null) result.add(rule.copy());
        return result;
    }

    private static boolean sameFieldModels(List<PacketFieldRuleManager.RuleEditModel> first,
            List<PacketFieldRuleManager.RuleEditModel> second) {
        if (first == second) return true;
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            PacketFieldRuleManager.RuleEditModel left = first.get(i);
            PacketFieldRuleManager.RuleEditModel right = second.get(i);
            if (left == right) continue;
            if (left == null || right == null || left.enabled != right.enabled || left.group != right.group
                    || left.writeDefaultOnFailure != right.writeDefaultOnFailure
                    || !Objects.equals(left.name, right.name) || !Objects.equals(left.category, right.category) || !Objects.equals(left.channel, right.channel)
                    || !Objects.equals(left.direction, right.direction) || !Objects.equals(left.source, right.source)
                    || !Objects.equals(left.extractMode, right.extractMode)
                    || !Objects.equals(left.pattern, right.pattern)
                    || !Objects.equals(left.variableName, right.variableName)
                    || !Objects.equals(left.valueType, right.valueType) || !Objects.equals(left.scope, right.scope)
                    || !Objects.equals(left.defaultValue, right.defaultValue) || !Objects.equals(left.note, right.note)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameInterceptRules(List<PacketInterceptConfig.InterceptRule> first,
            List<PacketInterceptConfig.InterceptRule> second) {
        if (first == second) return true;
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            PacketInterceptConfig.InterceptRule left = first.get(i);
            PacketInterceptConfig.InterceptRule right = second.get(i);
            if (left == right) continue;
            if (left == null || right == null || left.enabled != right.enabled || left.regexEnabled != right.regexEnabled
                    || left.replaceAll != right.replaceAll || !Objects.equals(left.name, right.name)
                     || !Objects.equals(left.category, right.category) || !Objects.equals(left.packetFilter, right.packetFilter)
                    || !Objects.equals(left.channel, right.channel) || !Objects.equals(left.matchHex, right.matchHex)
                    || !Objects.equals(left.replaceHex, right.replaceHex)) {
                return false;
            }
        }
        return true;
    }
    private static List<String> copy(List<String> source) { return source == null ? new ArrayList<String>() : new ArrayList<>(source); }
    private static boolean containsIgnoreCase(List<String> values, String target) { for (String value : values) if (value.equalsIgnoreCase(target)) return true; return false; }
    private static List<String> split(String value) {
        List<String> result = new ArrayList<>();
        if (value != null) for (String part : value.split(",")) if (!part.trim().isEmpty()) result.add(part.trim());
        return result;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
