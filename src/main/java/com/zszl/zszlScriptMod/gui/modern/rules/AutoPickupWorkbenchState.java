package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.system.AutoPickupRule;

/** Draft state for the native auto-pickup workbench. */
public final class AutoPickupWorkbenchState {
    private static final Gson GSON = new Gson();
    private static final String CATEGORY_DEFAULT = "默认";

    private final List<AutoPickupRule> rules = new ArrayList<>();
    private final List<String> categoryOrder = new ArrayList<>();
    private List<AutoPickupRule> originalRules = new ArrayList<>();
    private List<String> originalCategoryOrder = new ArrayList<>();
    private AutoPickupRule selected;
    private String selectedCategory = CATEGORY_DEFAULT;
    private boolean masterEnabled;
    private boolean originalMasterEnabled;

    public AutoPickupWorkbenchState(List<AutoPickupRule> source, List<String> categories, boolean masterEnabled) {
        this.masterEnabled = masterEnabled;
        this.originalMasterEnabled = masterEnabled;
        if (categories != null) {
            for (String category : categories) {
                addCategory(category);
            }
        }
        if (source != null) {
            for (AutoPickupRule rule : source) {
                if (rule != null) {
                    AutoPickupRule copied = copyRule(rule);
                    rules.add(copied);
                    ensureCategoryFor(copied);
                }
            }
        }
        if (categoryOrder.isEmpty()) {
            categoryOrder.add(CATEGORY_DEFAULT);
        }
        if (!rules.isEmpty()) {
            selected = rules.get(0);
            selectedCategory = normalizeCategory(selected.category);
        }
        originalRules = copyRules(rules);
        originalCategoryOrder = new ArrayList<>(categoryOrder);
    }

    public List<AutoPickupRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    public List<String> categories() {
        return Collections.unmodifiableList(categoryOrder);
    }

    public AutoPickupRule selected() {
        return selected;
    }

    public String selectedCategory() {
        return normalizeCategory(selectedCategory);
    }

    public boolean masterEnabled() {
        return masterEnabled;
    }

    public void setMasterEnabled(boolean enabled) {
        masterEnabled = enabled;
    }

    public List<Group> groups() {
        for (AutoPickupRule rule : rules) {
            ensureCategoryFor(rule);
        }
        Map<String, List<AutoPickupRule>> grouped = new LinkedHashMap<>();
        for (String category : categoryOrder) {
            grouped.put(category, new ArrayList<AutoPickupRule>());
        }
        for (AutoPickupRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String category = normalizeCategory(rule.category);
            List<AutoPickupRule> group = grouped.get(category);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(category, group);
            }
            group.add(rule);
        }
        List<Group> result = new ArrayList<>();
        for (Map.Entry<String, List<AutoPickupRule>> entry : grouped.entrySet()) {
            result.add(new Group(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public void select(AutoPickupRule rule) {
        if (rules.contains(rule)) {
            selected = rule;
            selectedCategory = normalizeCategory(rule.category);
        }
    }

    public void selectCategory(String category) {
        String normalized = normalizeCategory(category);
        for (String existing : categoryOrder) {
            if (existing.equalsIgnoreCase(normalized)) {
                selectedCategory = existing;
                return;
            }
        }
        categoryOrder.add(normalized);
        selectedCategory = normalized;
    }

    public AutoPickupRule addRule(String category) {
        AutoPickupRule rule = new AutoPickupRule();
        rule.category = normalizeCategory(category);
        rule.name = uniqueName("新建自动拾取规则");
        normalizeRule(rule);
        rules.add(rule);
        ensureCategoryFor(rule);
        selected = rule;
        selectedCategory = normalizeCategory(rule.category);
        return rule;
    }

    public AutoPickupRule duplicateSelected() {
        if (selected == null) {
            return null;
        }
        AutoPickupRule copy = copyRule(selected);
        copy.name = uniqueName(safe(selected.name) + " 副本");
        int index = rules.indexOf(selected);
        rules.add(Math.max(0, index + 1), copy);
        selected = copy;
        ensureCategoryFor(copy);
        selectedCategory = normalizeCategory(copy.category);
        return copy;
    }

    public boolean deleteSelected() {
        if (selected == null) {
            return false;
        }
        int index = rules.indexOf(selected);
        if (!rules.remove(selected)) {
            return false;
        }
        if (rules.isEmpty()) {
            selected = null;
            selectedCategory = categoryOrder.isEmpty() ? CATEGORY_DEFAULT : categoryOrder.get(0);
        } else {
            int nextIndex = Math.min(rules.size() - 1, Math.max(0, index));
            selected = rules.get(nextIndex);
            selectedCategory = normalizeCategory(selected.category);
        }
        return true;
    }

    public boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        for (String existing : categoryOrder) {
            if (existing.equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        categoryOrder.add(normalized);
        selectedCategory = normalized;
        return true;
    }

    public boolean renameCategory(String oldCategory, String newCategory) {
        String oldValue = normalizeCategory(oldCategory);
        String newValue = normalizeCategory(newCategory);
        if (oldValue.equalsIgnoreCase(newValue)) {
            return true;
        }
        for (String existing : categoryOrder) {
            if (existing.equalsIgnoreCase(newValue)) {
                return false;
            }
        }
        int index = findCategoryIndex(oldValue);
        if (index < 0) {
            return false;
        }
        categoryOrder.set(index, newValue);
        for (AutoPickupRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(oldValue)) {
                rule.category = newValue;
            }
        }
        if (selectedCategory.equalsIgnoreCase(oldValue)) {
            selectedCategory = newValue;
        }
        return true;
    }

    public boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        if (CATEGORY_DEFAULT.equalsIgnoreCase(normalized)) {
            return false;
        }
        int index = findCategoryIndex(normalized);
        if (index < 0) {
            return false;
        }
        categoryOrder.remove(index);
        for (AutoPickupRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                rule.category = CATEGORY_DEFAULT;
            }
        }
        if (findCategoryIndex(CATEGORY_DEFAULT) < 0) {
            categoryOrder.add(0, CATEGORY_DEFAULT);
        }
        selectedCategory = CATEGORY_DEFAULT;
        return true;
    }

    public boolean moveCategory(String category, int targetIndex) {
        int fromIndex = findCategoryIndex(category);
        if (fromIndex < 0 || categoryOrder.size() < 2 || targetIndex < 0
                || targetIndex >= categoryOrder.size() || targetIndex == fromIndex) {
            return false;
        }
        String moved = categoryOrder.remove(fromIndex);
        int insertIndex = Math.max(0, Math.min(targetIndex, categoryOrder.size()));
        categoryOrder.add(insertIndex, moved);
        selectedCategory = moved;
        return true;
    }

    public boolean moveSelectedRule(int delta) {
        if (selected == null || delta == 0) {
            return false;
        }
        String category = normalizeCategory(selected.category);
        int index = rules.indexOf(selected);
        int target = index + (delta < 0 ? -1 : 1);
        while (target >= 0 && target < rules.size()) {
            if (normalizeCategory(rules.get(target).category).equalsIgnoreCase(category)) {
                Collections.swap(rules, index, target);
                return true;
            }
            target += delta < 0 ? -1 : 1;
        }
        return false;
    }

    public boolean canMoveSelectedRule(int delta) {
        if (selected == null || delta == 0) {
            return false;
        }
        String category = normalizeCategory(selected.category);
        int index = rules.indexOf(selected);
        int target = index + (delta < 0 ? -1 : 1);
        while (target >= 0 && target < rules.size()) {
            if (normalizeCategory(rules.get(target).category).equalsIgnoreCase(category)) {
                return true;
            }
            target += delta < 0 ? -1 : 1;
        }
        return false;
    }

    public boolean isDirty() {
        return masterEnabled != originalMasterEnabled
                || !GSON.toJson(originalRules).equals(GSON.toJson(rules))
                || !originalCategoryOrder.equals(categoryOrder);
    }

    public void markCommitted() {
        originalRules = copyRules(rules);
        originalCategoryOrder = new ArrayList<>(categoryOrder);
        originalMasterEnabled = masterEnabled;
    }

    public void discard() {
        String selectedName = selected == null ? "" : safe(selected.name);
        String selectedCategory = selected == null ? "" : normalizeCategory(selected.category);
        int selectedIndex = selected == null ? -1 : rules.indexOf(selected);
        rules.clear();
        rules.addAll(copyRules(originalRules));
        categoryOrder.clear();
        categoryOrder.addAll(originalCategoryOrder);
        masterEnabled = originalMasterEnabled;
        selected = findByNameAndCategory(selectedName, selectedCategory);
        if (selected == null && selectedIndex >= 0 && selectedIndex < rules.size()) {
            selected = rules.get(selectedIndex);
        }
        if (selected == null && !rules.isEmpty()) {
            selected = rules.get(0);
        }
        selectedCategory = selected == null
                ? (categoryOrder.isEmpty() ? CATEGORY_DEFAULT : categoryOrder.get(0))
                : normalizeCategory(selected.category);
    }

    public static AutoPickupRule copyRule(AutoPickupRule source) {
        AutoPickupRule target = new AutoPickupRule();
        if (source == null) {
            return target;
        }
        target.name = safe(source.name);
        target.category = normalizeCategory(source.category);
        target.enabled = source.enabled;
        target.centerX = source.centerX;
        target.centerY = source.centerY;
        target.centerZ = source.centerZ;
        target.radius = source.radius;
        target.targetReachDistance = source.targetReachDistance;
        target.maxPickupAttempts = source.maxPickupAttempts;
        target.visualizeRange = source.visualizeRange;
        target.enableItemWhitelist = source.enableItemWhitelist;
        target.enableItemBlacklist = source.enableItemBlacklist;
        target.itemWhitelistEntries = copyItemEntries(source.itemWhitelistEntries, source.itemWhitelist);
        target.itemBlacklistEntries = copyItemEntries(source.itemBlacklistEntries, source.itemBlacklist);
        target.pickupActionEntries = copyActionEntries(source.pickupActionEntries);
        target.inventoryDetectionSlots = copyInventorySlots(source.inventoryDetectionSlots);
        target.itemWhitelist = flattenKeywords(target.itemWhitelistEntries);
        target.itemBlacklist = flattenKeywords(target.itemBlacklistEntries);
        target.postPickupSequence = safe(source.postPickupSequence);
        target.postPickupDelaySeconds = Math.max(0, source.postPickupDelaySeconds);
        target.stopOnExit = source.stopOnExit;
        target.antiStuckEnabled = source.antiStuckEnabled;
        target.antiStuckTimeoutSeconds = Math.max(1, source.antiStuckTimeoutSeconds);
        target.antiStuckRestartSequence = safe(source.antiStuckRestartSequence);
        return target;
    }

    public static void normalizeRule(AutoPickupRule rule) {
        if (rule == null) {
            return;
        }
        rule.name = safe(rule.name).trim();
        rule.category = normalizeCategory(rule.category);
        if (rule.targetReachDistance <= 0.0D || Double.isNaN(rule.targetReachDistance)
                || Double.isInfinite(rule.targetReachDistance)) {
            rule.targetReachDistance = AutoPickupRule.DEFAULT_TARGET_REACH_DISTANCE;
        }
        if (rule.maxPickupAttempts < 1) {
            rule.maxPickupAttempts = AutoPickupRule.DEFAULT_MAX_PICKUP_ATTEMPTS;
        }
        rule.itemWhitelistEntries = copyItemEntries(rule.itemWhitelistEntries, rule.itemWhitelist);
        rule.itemBlacklistEntries = copyItemEntries(rule.itemBlacklistEntries, rule.itemBlacklist);
        rule.pickupActionEntries = copyActionEntries(rule.pickupActionEntries);
        rule.inventoryDetectionSlots = copyInventorySlots(rule.inventoryDetectionSlots);
        rule.itemWhitelist = flattenKeywords(rule.itemWhitelistEntries);
        rule.itemBlacklist = flattenKeywords(rule.itemBlacklistEntries);
        rule.postPickupSequence = safe(rule.postPickupSequence).trim();
        rule.postPickupDelaySeconds = Math.max(0, rule.postPickupDelaySeconds);
        rule.antiStuckTimeoutSeconds = Math.max(1, rule.antiStuckTimeoutSeconds);
        rule.antiStuckRestartSequence = safe(rule.antiStuckRestartSequence).trim();
    }

    private AutoPickupRule findByNameAndCategory(String name, String category) {
        for (AutoPickupRule rule : rules) {
            if (safe(rule.name).equals(name) && normalizeCategory(rule.category).equalsIgnoreCase(category)) {
                return rule;
            }
        }
        return null;
    }

    private String uniqueName(String base) {
        String normalizedBase = safe(base).trim();
        if (normalizedBase.isEmpty()) {
            normalizedBase = "自动拾取规则";
        }
        String candidate = normalizedBase;
        int suffix = 2;
        while (containsName(candidate)) {
            candidate = normalizedBase + " " + suffix++;
        }
        return candidate;
    }

    private boolean containsName(String name) {
        for (AutoPickupRule rule : rules) {
            if (rule != null && safe(rule.name).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private int findCategoryIndex(String category) {
        String normalized = normalizeCategory(category);
        for (int i = 0; i < categoryOrder.size(); i++) {
            if (categoryOrder.get(i).equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    public String ensureCategoryFor(AutoPickupRule rule) {
        if (rule == null) {
            return selectedCategory();
        }
        String normalized = normalizeCategory(rule.category);
        String actual = normalized;
        for (String existing : categoryOrder) {
            if (existing.equalsIgnoreCase(normalized)) {
                actual = existing;
                break;
            }
        }
        if (!categoryOrder.contains(actual)) {
            categoryOrder.add(actual);
        }
        rule.category = actual;
        return actual;
    }

    private static List<AutoPickupRule> copyRules(List<AutoPickupRule> source) {
        List<AutoPickupRule> result = new ArrayList<>();
        if (source != null) {
            for (AutoPickupRule rule : source) {
                if (rule != null) {
                    result.add(copyRule(rule));
                }
            }
        }
        return result;
    }

    private static List<AutoPickupRule.ItemMatchEntry> copyItemEntries(
            List<AutoPickupRule.ItemMatchEntry> source, List<String> legacyKeywords) {
        List<AutoPickupRule.ItemMatchEntry> result = new ArrayList<>();
        if (source != null) {
            for (AutoPickupRule.ItemMatchEntry entry : source) {
                AutoPickupRule.ItemMatchEntry copied = normalizeItemEntry(entry);
                if (copied != null) {
                    result.add(copied);
                }
            }
        }
        if (result.isEmpty() && legacyKeywords != null) {
            for (String keyword : normalizeKeywordList(legacyKeywords)) {
                AutoPickupRule.ItemMatchEntry entry = new AutoPickupRule.ItemMatchEntry();
                entry.keyword = keyword;
                result.add(entry);
            }
        }
        return result;
    }

    private static List<AutoPickupRule.PickupActionEntry> copyActionEntries(
            List<AutoPickupRule.PickupActionEntry> source) {
        List<AutoPickupRule.PickupActionEntry> result = new ArrayList<>();
        if (source != null) {
            for (AutoPickupRule.PickupActionEntry entry : source) {
                AutoPickupRule.PickupActionEntry copied = normalizeActionEntry(entry);
                if (copied != null) {
                    result.add(copied);
                }
            }
        }
        return result;
    }

    private static AutoPickupRule.ItemMatchEntry normalizeItemEntry(AutoPickupRule.ItemMatchEntry source) {
        if (source == null) {
            return null;
        }
        String keyword = normalizeToken(source.keyword);
        List<String> tags = normalizeKeywordList(source.requiredNbtTags);
        if (keyword.isEmpty() && tags.isEmpty()) {
            return null;
        }
        AutoPickupRule.ItemMatchEntry result = new AutoPickupRule.ItemMatchEntry();
        result.keyword = keyword;
        result.requiredNbtTags = tags;
        return result;
    }

    private static AutoPickupRule.PickupActionEntry normalizeActionEntry(AutoPickupRule.PickupActionEntry source) {
        if (source == null) {
            return null;
        }
        String sequenceName = safe(source.sequenceName).trim();
        if (sequenceName.isEmpty()) {
            return null;
        }
        AutoPickupRule.PickupActionEntry result = new AutoPickupRule.PickupActionEntry();
        result.keyword = normalizeToken(source.keyword);
        result.requiredNbtTags = normalizeKeywordList(source.requiredNbtTags);
        result.sequenceName = sequenceName;
        result.executeDelaySeconds = Math.max(0, source.executeDelaySeconds);
        return result;
    }

    private static List<Integer> copyInventorySlots(Iterable<Integer> source) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (source != null) {
            for (Integer slot : source) {
                if (slot != null && slot >= 0 && slot < AutoPickupRule.INVENTORY_SLOT_COUNT) {
                    result.add(slot);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static List<String> flattenKeywords(List<AutoPickupRule.ItemMatchEntry> entries) {
        List<String> result = new ArrayList<>();
        if (entries != null) {
            for (AutoPickupRule.ItemMatchEntry entry : entries) {
                if (entry != null) {
                    String keyword = normalizeToken(entry.keyword);
                    if (!keyword.isEmpty()) {
                        result.add(keyword);
                    }
                }
            }
        }
        return normalizeKeywordList(result);
    }

    private static List<String> normalizeKeywordList(List<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                String normalized = normalizeToken(value);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static String normalizeToken(String value) {
        return KillAuraHandler.normalizeFilterName(value);
    }

    private static String normalizeCategory(String value) {
        String normalized = safe(value).trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Group {
        private final String name;
        private final List<AutoPickupRule> rules;

        private Group(String name, List<AutoPickupRule> rules) {
            this.name = name;
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        }

        public String name() {
            return name;
        }

        public List<AutoPickupRule> rules() {
            return rules;
        }
    }
}
