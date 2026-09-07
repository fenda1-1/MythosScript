package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.zszl.zszlScriptMod.system.AutoEscapeRule;

/** Draft and selection state for the native auto-escape workbench. */
public final class AutoEscapeWorkbenchState {
    private static final Gson GSON = new Gson();
    private List<AutoEscapeRule> original;
    private List<String> originalCategories;
    private final List<AutoEscapeRule> rules = new ArrayList<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private AutoEscapeRule selected;
    private boolean masterEnabled;
    private boolean originalMasterEnabled;

    public AutoEscapeWorkbenchState(List<AutoEscapeRule> source, List<String> categories, boolean masterEnabled) {
        this.masterEnabled = masterEnabled;
        this.originalMasterEnabled = masterEnabled;
        if (categories != null) {
            for (String category : categories) addCategory(category);
        }
        rules.addAll(copyRules(source));
        for (AutoEscapeRule rule : rules) addCategory(rule.category);
        if (rules.isEmpty()) rules.add(new AutoEscapeRule());
        selected = rules.get(0);
        original = copyRules(rules);
        originalCategories = new ArrayList<>(categoryOrder);
    }

    public List<AutoEscapeRule> rules() { return Collections.unmodifiableList(rules); }
    public AutoEscapeRule selected() { return selected; }
    public boolean masterEnabled() { return masterEnabled; }
    public void setMasterEnabled(boolean value) { masterEnabled = value; }

    public List<Group> groups() {
        Map<String, List<AutoEscapeRule>> grouped = new LinkedHashMap<>();
        for (String category : categoryOrder) grouped.put(category, new ArrayList<AutoEscapeRule>());
        for (AutoEscapeRule rule : rules) {
            String category = category(rule.category);
            List<AutoEscapeRule> group = grouped.get(category);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(category, group);
            }
            group.add(rule);
        }
        List<Group> result = new ArrayList<>();
        for (Map.Entry<String, List<AutoEscapeRule>> entry : grouped.entrySet()) {
            result.add(new Group(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public void select(AutoEscapeRule rule) {
        if (rules.contains(rule)) selected = rule;
    }

    public void replaceSelected(AutoEscapeRule replacement) {
        if (replacement == null || selected == null) return;
        replacement.normalize();
        int index = rules.indexOf(selected);
        if (index < 0) return;
        rules.set(index, replacement);
        selected = replacement;
        addCategory(replacement.category);
    }

    public void addRule(String category) {
        AutoEscapeRule rule = new AutoEscapeRule();
        rule.category = category(category);
        rule.name = uniqueName("新建逃离规则");
        rules.add(rule);
        selected = rule;
        addCategory(rule.category);
    }

    public void duplicateSelected() {
        if (selected == null) return;
        AutoEscapeRule copy = selected.copy();
        copy.name = uniqueName(selected.name + " 副本");
        rules.add(rules.indexOf(selected) + 1, copy);
        selected = copy;
    }

    public void deleteSelected() {
        if (selected == null) return;
        int index = rules.indexOf(selected);
        rules.remove(selected);
        if (rules.isEmpty()) {
            addRule("默认");
        } else {
            selected = rules.get(Math.min(Math.max(0, index), rules.size() - 1));
        }
    }

    public boolean isDirty() {
        return !categoryOrder.equals(originalCategories) || masterEnabled != originalMasterEnabled || !GSON.toJson(original).equals(GSON.toJson(rules));
    }

    public void markCommitted() {
        original = copyRules(rules);
        originalCategories = new ArrayList<>(categoryOrder);
        originalMasterEnabled = masterEnabled;
    }

    public void discard() {
        String selectedName = selected == null ? "" : selected.name;
        categoryOrder.clear();
        categoryOrder.addAll(originalCategories);
        rules.clear();
        rules.addAll(copyRules(original));
        masterEnabled = originalMasterEnabled;
        selected = findByName(selectedName);
        if (selected == null && !rules.isEmpty()) selected = rules.get(0);
    }

    private AutoEscapeRule findByName(String name) {
        for (AutoEscapeRule rule : rules) if (safe(rule.name).equals(name)) return rule;
        return null;
    }

    private String uniqueName(String base) {
        String candidate = safe(base).isEmpty() ? "逃离规则" : base;
        int suffix = 2;
        while (findByName(candidate) != null) candidate = base + " " + suffix++;
        return candidate;
    }

    public boolean renameCategory(String oldValue, String newValue) {
        String next = newValue.trim();
        if (next.isEmpty()) return false;
        for (String existing : categoryOrder) if (existing.equalsIgnoreCase(next)) return false;
        int index = -1;
        for (int i = 0; i < categoryOrder.size(); i++) if (categoryOrder.get(i).equalsIgnoreCase(oldValue)) index = i;
        if (index < 0) return false;
        categoryOrder.set(index, next);
        for (AutoEscapeRule rule : rules) if (category(rule.category).equalsIgnoreCase(oldValue)) rule.category = next;
        return true;
    }
    public void deleteCategory(String value) {
        if ("默认".equals(value)) return;
        categoryOrder.removeIf(existing -> existing.equalsIgnoreCase(value));
        for (AutoEscapeRule rule : rules) if (category(rule.category).equalsIgnoreCase(value)) rule.category = "默认";
        addCategory("默认");
    }
    public boolean canMoveRule(int delta) {
        int index = rules.indexOf(selected), target = index + delta;
        return index >= 0 && target >= 0 && target < rules.size();
    }
    public void moveRule(int delta) { if (canMoveRule(delta)) Collections.swap(rules, rules.indexOf(selected), rules.indexOf(selected) + delta); }

    public List<String> categories() { return new ArrayList<>(categoryOrder); }

    public void addCategory(String value) {
        String normalized = category(value);
        for (String existing : categoryOrder) if (existing.equalsIgnoreCase(normalized)) return;
        categoryOrder.add(normalized);
    }

    private static List<AutoEscapeRule> copyRules(List<AutoEscapeRule> source) {
        List<AutoEscapeRule> result = new ArrayList<>();
        if (source != null) {
            for (AutoEscapeRule rule : source) if (rule != null) result.add(rule.copy());
        }
        return result;
    }

    private static String category(String value) {
        return safe(value).trim().isEmpty() ? "默认" : value.trim();
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static final class Group {
        private final String name;
        private final List<AutoEscapeRule> rules;
        Group(String name, List<AutoEscapeRule> rules) {
            this.name = name;
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        }
        public String name() { return name; }
        public List<AutoEscapeRule> rules() { return rules; }
    }
}
