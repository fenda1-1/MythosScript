package com.zszl.zszlScriptMod.path;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InventoryItemFilterExpressionEngine {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00a7[0-9A-FK-ORa-fk-or]");

    private InventoryItemFilterExpressionEngine() {
    }

    public static List<String> readExpressions(JsonObject params) {
        List<String> expressions = new ArrayList<String>();
        if (params == null) {
            return expressions;
        }
        if (params.has("itemFilterExpressions") && params.get("itemFilterExpressions").isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray("itemFilterExpressions")) {
                if (element != null && element.isJsonPrimitive()) {
                    String expression = safe(element.getAsString()).trim();
                    if (!expression.isEmpty()) {
                        expressions.add(expression);
                    }
                }
            }
            if (!expressions.isEmpty()) {
                return expressions;
            }
        }
        if (params.has("itemFilterExpression") && params.get("itemFilterExpression").isJsonPrimitive()) {
            String expression = safe(params.get("itemFilterExpression").getAsString()).trim();
            if (!expression.isEmpty()) {
                expressions.add(expression);
            }
        }
        return expressions;
    }

    public static void writeExpressions(JsonObject target, List<String> expressions) {
        if (target == null) {
            return;
        }
        List<String> normalized = normalizeExpressions(expressions);
        if (normalized.isEmpty()) {
            target.remove("itemFilterExpressions");
            target.remove("itemFilterExpression");
            return;
        }
        JsonArray array = new JsonArray();
        for (String expression : normalized) {
            array.add(expression);
        }
        target.add("itemFilterExpressions", array);
        target.addProperty("itemFilterExpression", buildLegacyExpression(normalized));
    }

    public static String buildLegacyExpression(List<String> expressions) {
        List<String> normalized = normalizeExpressions(expressions);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.size() == 1) {
            return normalized.get(0);
        }
        StringBuilder builder = new StringBuilder();
        for (String expression : normalized) {
            if (builder.length() > 0) {
                builder.append(" || ");
            }
            builder.append('(').append(expression).append(')');
        }
        return builder.toString();
    }

    public static String summarizeExpressions(List<String> expressions) {
        List<String> normalized = normalizeExpressions(expressions);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.size() == 1) {
            return trimSummary(normalized.get(0));
        }
        return trimSummary(normalized.get(0)) + " 等" + normalized.size() + "条";
    }

    public static String buildLegacyCompatibleExpression(String itemName, String matchMode,
            List<String> requiredNbtTags,
            String requiredNbtTagMatchMode) {
        String namePart = "";
        String safeItemName = safe(itemName).trim();
        if (!safeItemName.isEmpty()) {
            if ("EXACT".equalsIgnoreCase(safe(matchMode))) {
                namePart = "name == " + quote(safeItemName);
            } else {
                namePart = "nameContains(" + quote(safeItemName) + ")";
            }
        }

        List<String> nbtParts = new ArrayList<String>();
        if (requiredNbtTags != null) {
            for (String tag : requiredNbtTags) {
                String normalizedTag = safe(tag).trim();
                if (!normalizedTag.isEmpty()) {
                    if (ItemFilterHandler.NBT_TAG_MATCH_MODE_NOT_CONTAINS.equalsIgnoreCase(requiredNbtTagMatchMode)) {
                        nbtParts.add("!NBT(" + quote(normalizedTag) + ")");
                    } else {
                        nbtParts.add("NBT(" + quote(normalizedTag) + ")");
                    }
                }
            }
        }
        String nbtPart = "";
        if (!nbtParts.isEmpty()) {
            String joiner = ItemFilterHandler.NBT_TAG_MATCH_MODE_NOT_CONTAINS.equalsIgnoreCase(requiredNbtTagMatchMode)
                    ? " && "
                    : " || ";
            StringBuilder builder = new StringBuilder();
            for (String part : nbtParts) {
                if (builder.length() > 0) {
                    builder.append(joiner);
                }
                builder.append(part);
            }
            nbtPart = builder.toString();
        }

        if (namePart.isEmpty()) {
            return nbtPart;
        }
        if (nbtPart.isEmpty()) {
            return namePart;
        }
        return "(" + namePart + ") && (" + nbtPart + ")";
    }

    public static void validate(String expression) {
        String text = safe(expression).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("物品过滤表达式不能为空");
        }
        new Parser(text, ItemSnapshot.empty(), true).parseExpression();
    }

    public static boolean matches(ItemStack stack, int slotIndex, String expression) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String text = safe(expression).trim();
        if (text.isEmpty()) {
            return false;
        }
        return new Parser(text, ItemSnapshot.from(stack, slotIndex), false).parseExpression();
    }

    public static boolean matches(ItemStack stack, int slotIndex, String expression, String rarity, double distance) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String text = safe(expression).trim();
        if (text.isEmpty()) {
            return false;
        }
        return new Parser(text, ItemSnapshot.from(stack, slotIndex, rarity, distance), false).parseExpression();
    }

    private static List<String> normalizeExpressions(List<String> expressions) {
        List<String> normalized = new ArrayList<String>();
        if (expressions == null) {
            return normalized;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String expression : expressions) {
            String text = safe(expression).trim();
            if (text.isEmpty()) {
                continue;
            }
            String dedupeKey = text.toLowerCase(Locale.ROOT);
            if (seen.add(dedupeKey)) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private static String trimSummary(String text) {
        String safeText = safe(text).trim();
        return safeText.length() > 42 ? safeText.substring(0, 39) + "..." : safeText;
    }

    private static String quote(String text) {
        return "\"" + safe(text).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ItemSnapshot {
        private final String displayName;
        private final String normalizedDisplayName;
        private final String registryName;
        private final String normalizedRegistryName;
        private final int count;
        private final int slotIndex;
        private final int itemDamage;
        private final boolean hasNbt;
        private final String rawNbt;
        private final String normalizedRawNbt;
        private final String searchableText;
        private final String normalizedSearchableText;
        private final String rarity;
        private final String normalizedRarity;
        private final double distance;
        private final List<String> tooltipLines;
        private final List<String> loreLines;
        private final List<KeyValueEntry> keyValueEntries;
        private final Map<String, List<String>> valuesByKey;

        private ItemSnapshot(String displayName, String registryName, int count, int slotIndex, int itemDamage,
                boolean hasNbt, String rawNbt, String searchableText, String rarity, double distance,
                List<String> tooltipLines, List<String> loreLines,
                List<KeyValueEntry> keyValueEntries, Map<String, List<String>> valuesByKey) {
            this.displayName = safe(displayName);
            this.normalizedDisplayName = normalizeComparableText(this.displayName);
            this.registryName = safe(registryName);
            this.normalizedRegistryName = normalizeComparableText(this.registryName);
            this.count = count;
            this.slotIndex = slotIndex;
            this.itemDamage = itemDamage;
            this.hasNbt = hasNbt;
            this.rawNbt = safe(rawNbt);
            this.normalizedRawNbt = normalizeComparableText(this.rawNbt);
            this.searchableText = safe(searchableText);
            this.normalizedSearchableText = normalizeComparableText(this.searchableText);
            this.rarity = safe(rarity);
            this.normalizedRarity = normalizeComparableText(this.rarity);
            this.distance = distance;
            this.tooltipLines = tooltipLines == null ? Collections.<String>emptyList() : tooltipLines;
            this.loreLines = loreLines == null ? Collections.<String>emptyList() : loreLines;
            this.keyValueEntries = keyValueEntries == null ? Collections.<KeyValueEntry>emptyList() : keyValueEntries;
            this.valuesByKey = valuesByKey == null ? Collections.<String, List<String>>emptyMap() : valuesByKey;
        }

        private static ItemSnapshot empty() {
            return new ItemSnapshot("", "", 0, -1, 0, false, "", "", "", 0.0D,
                    new ArrayList<String>(), new ArrayList<String>(),
                    new ArrayList<KeyValueEntry>(), new LinkedHashMap<String, List<String>>());
        }

        private static ItemSnapshot from(ItemStack stack, int slotIndex) {
            return from(stack, slotIndex, "", 0.0D);
        }

        private static ItemSnapshot from(ItemStack stack, int slotIndex, String rarity, double distance) {
            if (stack == null || stack.isEmpty()) {
                return empty();
            }
            String displayName = stripFormatting(stack.getDisplayName());
            Item item = stack.getItem();
            ResourceLocation registryName = item == null ? null : item.getRegistryName();
            String registryText = registryName == null ? "" : registryName.toString();
            String rawNbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : "";
            String searchableText = ItemFilterHandler.buildItemSearchableText(stack);

            List<String> tooltipLines = new ArrayList<String>();
            List<String> loreLines = new ArrayList<String>();
            List<KeyValueEntry> keyValueEntries = new ArrayList<KeyValueEntry>();
            Map<String, List<String>> valuesByKey = new LinkedHashMap<String, List<String>>();

            try {
                List<String> tooltip = stack.getTooltip(Minecraft.getMinecraft().player,
                        ITooltipFlag.TooltipFlags.NORMAL);
                for (String line : tooltip) {
                    addTextLine(tooltipLines, keyValueEntries, valuesByKey, line, false);
                }
            } catch (Exception ignored) {
            }

            if (stack.hasTagCompound()) {
                collectNbtData("", stack.getTagCompound(), keyValueEntries, valuesByKey, loreLines);
            }

            return new ItemSnapshot(displayName, registryText, stack.getCount(), slotIndex, stack.getItemDamage(),
                    stack.hasTagCompound(), rawNbt, searchableText, rarity, distance, tooltipLines, loreLines, keyValueEntries,
                    valuesByKey);
        }

        private boolean matchesNbtText(String text) {
            String normalized = normalizeComparableText(text);
            if (normalized.isEmpty()) {
                return false;
            }
            if (normalizedSearchableText.contains(normalized) || normalizedRawNbt.contains(normalized)) {
                return true;
            }
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.normalizedJoined.contains(normalized)
                        || entry.normalizedValue.contains(normalized)
                        || entry.normalizedKey.contains(normalized)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesNbtKeyValue(String key, String value) {
            String normalizedKey = normalizeComparableText(key);
            String normalizedValue = normalizeComparableText(value);
            if (normalizedKey.isEmpty()) {
                return matchesNbtText(value);
            }
            if (normalizedValue.isEmpty()) {
                return hasKey(normalizedKey);
            }
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.matchesKey(normalizedKey) && entry.normalizedValue.contains(normalizedValue)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasKey(String normalizedKey) {
            if (normalizedKey == null || normalizedKey.isEmpty()) {
                return false;
            }
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.matchesKey(normalizedKey)) {
                    return true;
                }
            }
            return false;
        }

        private String findFirstValue(String key) {
            String normalizedKey = normalizeComparableText(key);
            if (normalizedKey.isEmpty()) {
                return "";
            }
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.matchesKey(normalizedKey) && !entry.value.isEmpty()) {
                    return entry.value;
                }
            }
            List<String> values = valuesByKey.get(normalizedKey);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
            return "";
        }

        private Double findFirstNumber(String key) {
            List<Double> numbers = findNumbers(key);
            return numbers.isEmpty() ? null : numbers.get(0);
        }

        private List<String> findValues(String key) {
            String normalizedKey = normalizeComparableText(key);
            if (normalizedKey.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> values = new ArrayList<String>();
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.matchesKey(normalizedKey) && !entry.value.isEmpty()) {
                    values.add(entry.value);
                }
            }
            if (!values.isEmpty()) {
                return values;
            }
            List<String> storedValues = valuesByKey.get(normalizedKey);
            if (storedValues == null || storedValues.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<String>(storedValues);
        }

        private List<Double> findNumbers(String key) {
            List<String> values = findValues(key);
            if (values.isEmpty()) {
                return Collections.emptyList();
            }
            List<Double> numbers = new ArrayList<Double>();
            for (String value : values) {
                Double number = extractFirstNumber(value);
                if (number != null) {
                    numbers.add(number);
                }
            }
            return numbers;
        }

        private Double sumNumbers(String key) {
            List<Double> numbers = findNumbers(key);
            if (numbers.isEmpty()) {
                return null;
            }
            double total = 0D;
            for (Double number : numbers) {
                total += number.doubleValue();
            }
            return total;
        }

        private Double averageNumbers(String key) {
            List<Double> numbers = findNumbers(key);
            if (numbers.isEmpty()) {
                return null;
            }
            double total = 0D;
            for (Double number : numbers) {
                total += number.doubleValue();
            }
            return total / numbers.size();
        }

        private Double minNumber(String key) {
            List<Double> numbers = findNumbers(key);
            if (numbers.isEmpty()) {
                return null;
            }
            double min = Double.POSITIVE_INFINITY;
            for (Double number : numbers) {
                min = Math.min(min, number.doubleValue());
            }
            return min;
        }

        private Double maxNumber(String key) {
            List<Double> numbers = findNumbers(key);
            if (numbers.isEmpty()) {
                return null;
            }
            double max = Double.NEGATIVE_INFINITY;
            for (Double number : numbers) {
                max = Math.max(max, number.doubleValue());
            }
            return max;
        }

        private int countDistinctValues(String key) {
            List<String> values = findValues(key);
            if (values.isEmpty()) {
                return 0;
            }
            LinkedHashSet<String> uniqueValues = new LinkedHashSet<String>();
            for (String value : values) {
                String normalizedValue = normalizeComparableText(value);
                if (!normalizedValue.isEmpty()) {
                    uniqueValues.add(normalizedValue);
                }
            }
            return uniqueValues.size();
        }

        private int countNumbersInRange(String key, Interval interval) {
            if (interval == null) {
                return 0;
            }
            int count = 0;
            for (Double number : findNumbers(key)) {
                if (number != null && interval.test(number.doubleValue())) {
                    count++;
                }
            }
            return count;
        }

        private boolean anyNumberInRange(String key, Interval interval) {
            return countNumbersInRange(key, interval) > 0;
        }

        private boolean allNumbersInRange(String key, Interval interval) {
            List<Double> numbers = findNumbers(key);
            if (numbers.isEmpty() || interval == null) {
                return false;
            }
            for (Double number : numbers) {
                if (number == null || !interval.test(number.doubleValue())) {
                    return false;
                }
            }
            return true;
        }

        private boolean containsTooltipText(String text) {
            String normalized = normalizeComparableText(text);
            if (normalized.isEmpty()) {
                return false;
            }
            for (String line : tooltipLines) {
                if (normalizeComparableText(line).contains(normalized)) {
                    return true;
                }
            }
            return false;
        }

        private int countTooltipOccurrences(String text) {
            return countOccurrencesInSource(joinLines(tooltipLines), text);
        }

        private boolean containsLoreText(String text) {
            String normalized = normalizeComparableText(text);
            if (normalized.isEmpty()) {
                return false;
            }
            for (String line : loreLines) {
                if (normalizeComparableText(line).contains(normalized)) {
                    return true;
                }
            }
            return false;
        }

        private int countLoreOccurrences(String text) {
            return countOccurrencesInSource(joinLines(loreLines), text);
        }

        private int countSearchOccurrences(String text) {
            return countOccurrencesInSource(searchableText, text);
        }

        private int countNbtOccurrences(String text) {
            return countOccurrencesInSource(rawNbt, text);
        }

        private int countMatchingEntries(String key, String value) {
            String normalizedKey = normalizeComparableText(key);
            String normalizedValue = normalizeComparableText(value);
            if (normalizedKey.isEmpty() && normalizedValue.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (KeyValueEntry entry : keyValueEntries) {
                boolean keyMatches = normalizedKey.isEmpty() || entry.matchesKey(normalizedKey);
                boolean valueMatches = normalizedValue.isEmpty() || entry.normalizedValue.contains(normalizedValue);
                if (keyMatches && valueMatches) {
                    count++;
                }
            }
            return count;
        }

        private int countMatchingKeys(String key) {
            String normalizedKey = normalizeComparableText(key);
            if (normalizedKey.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (KeyValueEntry entry : keyValueEntries) {
                if (entry.matchesKey(normalizedKey)) {
                    count++;
                }
            }
            return count;
        }

        private static void collectNbtData(String path, NBTBase tag, List<KeyValueEntry> entries,
                Map<String, List<String>> valuesByKey, List<String> loreLines) {
            if (tag == null) {
                return;
            }
            if (tag instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) tag;
                for (String key : compound.getKeySet()) {
                    String childPath = path.isEmpty() ? key : path + "." + key;
                    NBTBase child = compound.getTag(key);
                    if ("display.Lore".equalsIgnoreCase(childPath) && child instanceof NBTTagList) {
                        NBTTagList list = (NBTTagList) child;
                        for (int i = 0; i < list.tagCount(); i++) {
                            addTextLine(loreLines, entries, valuesByKey, list.getStringTagAt(i), true);
                        }
                    }
                    collectNbtData(childPath, child, entries, valuesByKey, loreLines);
                }
                return;
            }
            if (tag instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) tag;
                for (int i = 0; i < list.tagCount(); i++) {
                    collectNbtData(path + "[" + i + "]", list.get(i), entries, valuesByKey, loreLines);
                }
                return;
            }
            if (tag instanceof NBTTagByteArray) {
                NBTTagByteArray byteArray = (NBTTagByteArray) tag;
                addKeyValue(entries, valuesByKey, path, joinBytes(byteArray.getByteArray()));
                return;
            }
            if (tag instanceof NBTTagIntArray) {
                NBTTagIntArray intArray = (NBTTagIntArray) tag;
                addKeyValue(entries, valuesByKey, path, joinInts(intArray.getIntArray()));
                return;
            }
            addKeyValue(entries, valuesByKey, path, nbtValueToString(tag));
        }

        private static void addTextLine(List<String> targetLines, List<KeyValueEntry> entries,
                Map<String, List<String>> valuesByKey, String rawText, boolean lore) {
            String cleaned = stripFormatting(rawText);
            if (cleaned.isEmpty()) {
                return;
            }
            targetLines.add(cleaned);
            int separatorIndex = findKeyValueSeparator(cleaned);
            if (separatorIndex <= 0 || separatorIndex >= cleaned.length() - 1) {
                return;
            }
            String key = cleaned.substring(0, separatorIndex).trim();
            String value = cleaned.substring(separatorIndex + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                return;
            }
            addKeyValue(entries, valuesByKey, lore ? "lore." + key : key, value);
        }

        private static void addKeyValue(List<KeyValueEntry> entries, Map<String, List<String>> valuesByKey, String key,
                String value) {
            String cleanedKey = stripFormatting(key);
            String cleanedValue = stripFormatting(value);
            if (cleanedKey.isEmpty() && cleanedValue.isEmpty()) {
                return;
            }
            KeyValueEntry entry = new KeyValueEntry(cleanedKey, cleanedValue);
            entries.add(entry);
            if (!entry.normalizedKey.isEmpty() && !entry.value.isEmpty()) {
                valuesByKey.computeIfAbsent(entry.normalizedKey, ignored -> new ArrayList<String>()).add(entry.value);
            }
        }
    }

    private static final class KeyValueEntry {
        private final String key;
        private final String value;
        private final String normalizedKey;
        private final String normalizedValue;
        private final String normalizedJoined;

        private KeyValueEntry(String key, String value) {
            this.key = safe(key).trim();
            this.value = safe(value).trim();
            this.normalizedKey = normalizeComparableText(this.key);
            this.normalizedValue = normalizeComparableText(this.value);
            this.normalizedJoined = this.normalizedKey.isEmpty()
                    ? this.normalizedValue
                    : this.normalizedKey + ":" + this.normalizedValue;
        }

        private boolean matchesKey(String normalizedQuery) {
            if (normalizedQuery == null || normalizedQuery.isEmpty() || normalizedKey.isEmpty()) {
                return false;
            }
            return normalizedKey.equals(normalizedQuery)
                    || normalizedKey.endsWith("." + normalizedQuery)
                    || normalizedKey.endsWith("[" + normalizedQuery + "]")
                    || normalizedKey.contains(normalizedQuery)
                    || normalizedQuery.contains(normalizedKey);
        }
    }

    private static final class Interval {
        private final double min;
        private final double max;
        private final boolean includeMin;
        private final boolean includeMax;

        private Interval(double min, double max, boolean includeMin, boolean includeMax) {
            this.min = min;
            this.max = max;
            this.includeMin = includeMin;
            this.includeMax = includeMax;
        }

        private boolean test(double value) {
            boolean leftOk = includeMin ? value >= min : value > min;
            boolean rightOk = includeMax ? value <= max : value < max;
            return leftOk && rightOk;
        }
    }

    private static final class Parser {
        private final String expression;
        private final ItemSnapshot snapshot;
        private final boolean validationMode;
        private int index = 0;

        private Parser(String expression, ItemSnapshot snapshot, boolean validationMode) {
            this.expression = expression == null ? "" : expression;
            this.snapshot = snapshot == null ? ItemSnapshot.empty() : snapshot;
            this.validationMode = validationMode;
        }

        private boolean parseExpression() {
            Object value = parseValueExpression();
            return toBoolean(value);
        }

        private Object parseValueExpression() {
            try {
                Object result = parseOr();
                skipWhitespace();
                if (index < expression.length()) {
                    throw error("表达式存在未解析内容: " + expression.substring(index));
                }
                return result;
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().contains("位置 ")) {
                    throw e;
                }
                throw error(e.getMessage() == null ? "物品过滤表达式解析失败" : e.getMessage());
            }
        }

        private Object parseOr() {
            Object value = parseAnd();
            while (true) {
                skipWhitespace();
                if (matchAny("||", "|", "｜｜", "｜")) {
                    Object right = parseAnd();
                    value = toBoolean(value) || toBoolean(right);
                } else {
                    return value;
                }
            }
        }

        private Object parseAnd() {
            Object value = parseComparison();
            while (true) {
                skipWhitespace();
                if (matchAny("&&", "&", "＆＆", "＆")) {
                    Object right = parseComparison();
                    value = toBoolean(value) && toBoolean(right);
                } else {
                    return value;
                }
            }
        }

        private Object parseComparison() {
            Object value = parseAdditive();
            while (true) {
                skipWhitespace();
                String operator = tryParseOperator();
                if (operator == null) {
                    return value;
                }
                skipWhitespace();
                value = evaluateComparison(value, operator, parseAdditive());
            }
        }

        private Object parseAdditive() {
            Object value = parseMultiplicative();
            while (true) {
                skipWhitespace();
                if (match("+")) {
                    value = applyBinaryArithmetic("+", value, parseMultiplicative());
                } else if (match("-")) {
                    value = applyBinaryArithmetic("-", value, parseMultiplicative());
                } else {
                    return value;
                }
            }
        }

        private Object parseMultiplicative() {
            Object value = parseUnary();
            while (true) {
                skipWhitespace();
                if (match("*")) {
                    value = applyBinaryArithmetic("*", value, parseUnary());
                } else if (match("/")) {
                    value = applyBinaryArithmetic("/", value, parseUnary());
                } else if (match("%")) {
                    value = applyBinaryArithmetic("%", value, parseUnary());
                } else {
                    return value;
                }
            }
        }

        private Object parseUnary() {
            skipWhitespace();
            if (matchAny("!", "！")) {
                return !toBoolean(parseUnary());
            }
            if (match("+")) {
                return requireArithmeticNumber("+", parseUnary());
            }
            if (match("-")) {
                return -requireArithmeticNumber("-", parseUnary());
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            skipWhitespace();
            if (index >= expression.length()) {
                throw error("表达式缺少值");
            }

            if (matchAny("(", "（")) {
                Object value = parseOr();
                skipWhitespace();
                if (!matchAny(")", "）")) {
                    throw error("缺少右括号");
                }
                return value;
            }

            char c = expression.charAt(index);
            if (c == '\'' || c == '"') {
                return parseQuotedString();
            }
            if (Character.isDigit(c) || (c == '.' && index + 1 < expression.length()
                    && Character.isDigit(expression.charAt(index + 1)))) {
                return parseNumber();
            }

            String token = parseToken();
            if (token.isEmpty()) {
                throw error("表达式值为空");
            }

            skipWhitespace();
            if (matchAny("(", "（")) {
                return parseFunctionCall(token);
            }

            if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
                return Boolean.parseBoolean(token);
            }
            if ("null".equalsIgnoreCase(token)) {
                return null;
            }
            return resolveIdentifier(token);
        }

        private Object applyBinaryArithmetic(String operator, Object left, Object right) {
            double leftNumber = requireArithmeticNumber(operator, left);
            double rightNumber = requireArithmeticNumber(operator, right);
            switch (operator) {
                case "+":
                    return leftNumber + rightNumber;
                case "-":
                    return leftNumber - rightNumber;
                case "*":
                    return leftNumber * rightNumber;
                case "/":
                    if (Double.compare(rightNumber, 0D) == 0) {
                        throw error("除法不能除以 0");
                    }
                    return leftNumber / rightNumber;
                case "%":
                    if (Double.compare(rightNumber, 0D) == 0) {
                        throw error("取模不能对 0 运算");
                    }
                    return leftNumber % rightNumber;
                default:
                    throw error("不支持的算术运算符: " + operator);
            }
        }

        private Object parseFunctionCall(String functionName) {
            List<String> rawArgs = parseRawArguments(functionName);
            return evaluateFunction(functionName, rawArgs);
        }

        private List<String> parseRawArguments(String functionName) {
            List<String> args = new ArrayList<String>();
            StringBuilder current = new StringBuilder();
            int nestedLevel = 0;
            boolean inQuote = false;
            char quote = 0;
            while (index < expression.length()) {
                char c = expression.charAt(index++);
                if (inQuote) {
                    current.append(c);
                    if (c == '\\' && index < expression.length()) {
                        current.append(expression.charAt(index++));
                        continue;
                    }
                    if (c == quote) {
                        inQuote = false;
                    }
                    continue;
                }
                if (c == '\'' || c == '"') {
                    inQuote = true;
                    quote = c;
                    current.append(c);
                    continue;
                }
                if (c == '(' || c == '（') {
                    nestedLevel++;
                    current.append(c);
                    continue;
                }
                if (c == ')' || c == '）') {
                    if (nestedLevel == 0) {
                        addRawArg(args, current);
                        return args;
                    }
                    nestedLevel--;
                    current.append(c);
                    continue;
                }
                if ((c == ',' || c == '，') && nestedLevel == 0) {
                    addRawArg(args, current);
                    continue;
                }
                current.append(c);
            }
            throw error("函数调用缺少右括号: " + functionName);
        }

        private void addRawArg(List<String> args, StringBuilder current) {
            String value = current == null ? "" : current.toString().trim();
            if (!value.isEmpty() || !args.isEmpty()) {
                args.add(value);
            }
            if (current != null) {
                current.setLength(0);
            }
        }

        private Object evaluateFunction(String functionName, List<String> rawArgs) {
            String name = normalizeSymbol(functionName);
            switch (name) {
                case "nbt":
                case "tag":
                    if (rawArgs.size() == 1) {
                        return snapshot.matchesNbtText(resolveRawText(rawArgs.get(0)));
                    }
                    if (rawArgs.size() == 2) {
                        return snapshot.matchesNbtKeyValue(resolveRawText(rawArgs.get(0)),
                                resolveRawText(rawArgs.get(1)));
                    }
                    throw error("函数 " + functionName + " 只支持 1 或 2 个参数");
                case "nbtvalue":
                case "tagvalue":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.findFirstValue(resolveRawText(rawArgs.get(0)));
                case "nbtnum":
                case "tagnum":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.findFirstNumber(resolveRawText(rawArgs.get(0)));
                case "nbtsum":
                case "tagsum":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.sumNumbers(resolveRawText(rawArgs.get(0)));
                case "nbtavg":
                case "nbtaverage":
                case "tagavg":
                case "tagaverage":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.averageNumbers(resolveRawText(rawArgs.get(0)));
                case "nbtmin":
                case "tagmin":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.minNumber(resolveRawText(rawArgs.get(0)));
                case "nbtmax":
                case "tagmax":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.maxNumber(resolveRawText(rawArgs.get(0)));
                case "nbtdistinctcount":
                case "nbtdistinctvaluecount":
                case "tagdistinctcount":
                case "tagdistinctvaluecount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countDistinctValues(resolveRawText(rawArgs.get(0)));
                case "nbtrange":
                case "tagrange":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return testRange(snapshot.findFirstNumber(resolveRawText(rawArgs.get(0))),
                            resolveRawText(rawArgs.get(1)), functionName);
                case "nbtrangecount":
                case "tagrangecount":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return snapshot.countNumbersInRange(resolveRawText(rawArgs.get(0)),
                            parseInterval(resolveRawText(rawArgs.get(1)), functionName));
                case "nbtanyrange":
                case "taganyrange":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return snapshot.anyNumberInRange(resolveRawText(rawArgs.get(0)),
                            parseInterval(resolveRawText(rawArgs.get(1)), functionName));
                case "nbtallrange":
                case "tagallrange":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return snapshot.allNumbersInRange(resolveRawText(rawArgs.get(0)),
                            parseInterval(resolveRawText(rawArgs.get(1)), functionName));
                case "range":
                case "inrange":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return testRange(toNumber(evaluateNestedValue(rawArgs.get(0), functionName)),
                            resolveRawText(rawArgs.get(1)), functionName);
                case "sum":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    return sumFunctionNumbers(functionName, rawArgs);
                case "min":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    return minFunctionNumber(functionName, rawArgs);
                case "max":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    return maxFunctionNumber(functionName, rawArgs);
                case "avg":
                case "average":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    return averageFunctionNumbers(functionName, rawArgs);
                case "abs":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return Math.abs(requireArithmeticNumber(functionName,
                            evaluateNestedValue(rawArgs.get(0), functionName)));
                case "round":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return (double) Math.round(requireArithmeticNumber(functionName,
                            evaluateNestedValue(rawArgs.get(0), functionName)));
                case "floor":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return Math.floor(requireArithmeticNumber(functionName,
                            evaluateNestedValue(rawArgs.get(0), functionName)));
                case "ceil":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return Math.ceil(requireArithmeticNumber(functionName,
                            evaluateNestedValue(rawArgs.get(0), functionName)));
                case "clamp":
                    requireExactArgCount(functionName, rawArgs, 3);
                    return clampFunctionValue(functionName, rawArgs);
                case "number":
                case "tonumber":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return optionalNumericValue(functionName,
                            evaluateNestedValue(rawArgs.get(0), functionName));
                case "normalize":
                case "normalized":
                case "norm":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return normalizeComparableText(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)));
                case "namecontains":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return containsNormalized(snapshot.displayName, resolveRawText(rawArgs.get(0)));
                case "idcontains":
                case "registrycontains":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return containsNormalized(snapshot.registryName, resolveRawText(rawArgs.get(0)));
                case "tooltip":
                case "tooltipcontains":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.containsTooltipText(resolveRawText(rawArgs.get(0)));
                case "tooltipcount":
                case "tooltipoccurs":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countTooltipOccurrences(resolveRawText(rawArgs.get(0)));
                case "tooltiplinecount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return countMatchingLines(joinLines(snapshot.tooltipLines), resolveRawText(rawArgs.get(0)));
                case "tooltipregexlinecount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return countRegexMatchingLines(joinLines(snapshot.tooltipLines), resolveRawText(rawArgs.get(0)),
                            functionName);
                case "lore":
                case "lorecontains":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.containsLoreText(resolveRawText(rawArgs.get(0)));
                case "lorecount":
                case "loreoccurs":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countLoreOccurrences(resolveRawText(rawArgs.get(0)));
                case "lorelinecount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return countMatchingLines(joinLines(snapshot.loreLines), resolveRawText(rawArgs.get(0)));
                case "loreregexlinecount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return countRegexMatchingLines(joinLines(snapshot.loreLines), resolveRawText(rawArgs.get(0)),
                            functionName);
                case "text":
                case "anytext":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return containsNormalized(snapshot.searchableText, resolveRawText(rawArgs.get(0)));
                case "textcount":
                case "textoccurs":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countSearchOccurrences(resolveRawText(rawArgs.get(0)));
                case "nbttextcount":
                case "nbtrawcount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countNbtOccurrences(resolveRawText(rawArgs.get(0)));
                case "hasnbt":
                    requireExactArgCount(functionName, rawArgs, 0);
                    return snapshot.hasNbt;
                case "occurs":
                case "countoccurrences":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return countOccurrencesInSource(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                            stringValue(evaluateNestedValue(rawArgs.get(1), functionName)));
                case "linecount":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return countMatchingLines(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                            resolveRawText(rawArgs.get(1)));
                case "lineregexcount":
                case "regexlinecount":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return countRegexMatchingLines(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                            resolveRawText(rawArgs.get(1)), functionName);
                case "distinctcount":
                case "uniquecount":
                    if (rawArgs.size() == 1) {
                        return countDistinctLines(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)));
                    }
                    if (rawArgs.size() == 2) {
                        return countDistinctMatchingLines(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                                resolveRawText(rawArgs.get(1)));
                    }
                    throw error("函数 " + functionName + " 只支持 1 或 2 个参数");
                case "distinctregexcount":
                case "uniqueregexcount":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return countDistinctRegexMatchingLines(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                            resolveRawText(rawArgs.get(1)), functionName);
                case "contains":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return containsNormalized(evaluateNestedValue(rawArgs.get(0), functionName),
                            evaluateNestedValue(rawArgs.get(1), functionName));
                case "startswith":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return normalizeComparableText(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)))
                            .startsWith(normalizeComparableText(resolveRawText(rawArgs.get(1))));
                case "endswith":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return normalizeComparableText(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)))
                            .endsWith(normalizeComparableText(resolveRawText(rawArgs.get(1))));
                case "regex":
                case "matches":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return compileRegex(resolveRawText(rawArgs.get(1)), functionName)
                            .matcher(stringValue(evaluateNestedValue(rawArgs.get(0), functionName))).find();
                case "regexcount":
                case "countmatches":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return countRegexMatches(stringValue(evaluateNestedValue(rawArgs.get(0), functionName)),
                            resolveRawText(rawArgs.get(1)), functionName);
                case "exists":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return !isNullish(evaluateNestedValue(rawArgs.get(0), functionName));
                case "nbtoccurs":
                case "tagoccurs":
                    requireExactArgCount(functionName, rawArgs, 2);
                    return snapshot.countMatchingEntries(resolveRawText(rawArgs.get(0)),
                            resolveRawText(rawArgs.get(1)));
                case "nbtkeycount":
                case "tagkeycount":
                    requireExactArgCount(functionName, rawArgs, 1);
                    return snapshot.countMatchingKeys(resolveRawText(rawArgs.get(0)));
                case "all":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    for (String rawArg : rawArgs) {
                        if (!toBoolean(evaluateNestedValue(rawArg, functionName))) {
                            return false;
                        }
                    }
                    return true;
                case "any":
                    requireArgCountAtLeast(functionName, rawArgs, 1);
                    for (String rawArg : rawArgs) {
                        if (toBoolean(evaluateNestedValue(rawArg, functionName))) {
                            return true;
                        }
                    }
                    return false;
                default:
                    throw error("不支持的物品过滤函数: " + functionName);
            }
        }

        private Object evaluateNestedValue(String rawExpression, String functionName) {
            String text = safe(rawExpression).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return new Parser(text, snapshot, validationMode).parseValueExpression();
            } catch (IllegalArgumentException e) {
                throw error("函数 " + functionName + " 的参数无效: " + safe(e.getMessage()));
            }
        }

        private boolean testRange(Double numericValue, String intervalText, String functionName) {
            if (numericValue == null) {
                return validationMode ? false : false;
            }
            Interval interval = parseInterval(intervalText, functionName);
            return interval.test(numericValue.doubleValue());
        }

        private Interval parseInterval(String text, String functionName) {
            String raw = safe(text).trim();
            if (raw.length() < 5) {
                throw error("函数 " + functionName + " 的区间格式无效，应为 [a,b] / (a,b] 等");
            }
            char left = raw.charAt(0);
            char right = raw.charAt(raw.length() - 1);
            if ((left != '[' && left != '(') || (right != ']' && right != ')')) {
                throw error("函数 " + functionName + " 的区间必须以 [ ( 开头，以 ] ) 结尾");
            }
            String body = raw.substring(1, raw.length() - 1);
            int commaIndex = body.indexOf(',');
            if (commaIndex < 0) {
                throw error("函数 " + functionName + " 的区间缺少逗号");
            }
            double min = parseIntervalEdge(body.substring(0, commaIndex).trim(), true, functionName);
            double max = parseIntervalEdge(body.substring(commaIndex + 1).trim(), false, functionName);
            if (min > max) {
                throw error("函数 " + functionName + " 的区间下限不能大于上限");
            }
            return new Interval(min, max, left == '[', right == ']');
        }

        private double parseIntervalEdge(String text, boolean lower, String functionName) {
            String normalized = safe(text).trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || "*".equals(normalized)
                    || "inf".equals(normalized) || "+inf".equals(normalized) || "infinity".equals(normalized)) {
                return lower ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            if ("-inf".equals(normalized) || "-infinity".equals(normalized)) {
                return Double.NEGATIVE_INFINITY;
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException e) {
                throw error("函数 " + functionName + " 的区间端点无效: " + text);
            }
        }

        private double sumFunctionNumbers(String functionName, List<String> rawArgs) {
            double total = 0D;
            for (Double number : collectFunctionNumbers(functionName, rawArgs)) {
                total += number.doubleValue();
            }
            return total;
        }

        private Double minFunctionNumber(String functionName, List<String> rawArgs) {
            List<Double> numbers = collectFunctionNumbers(functionName, rawArgs);
            if (numbers.isEmpty()) {
                return null;
            }
            double min = Double.POSITIVE_INFINITY;
            for (Double number : numbers) {
                min = Math.min(min, number.doubleValue());
            }
            return min;
        }

        private Double maxFunctionNumber(String functionName, List<String> rawArgs) {
            List<Double> numbers = collectFunctionNumbers(functionName, rawArgs);
            if (numbers.isEmpty()) {
                return null;
            }
            double max = Double.NEGATIVE_INFINITY;
            for (Double number : numbers) {
                max = Math.max(max, number.doubleValue());
            }
            return max;
        }

        private Double averageFunctionNumbers(String functionName, List<String> rawArgs) {
            List<Double> numbers = collectFunctionNumbers(functionName, rawArgs);
            if (numbers.isEmpty()) {
                return null;
            }
            double total = 0D;
            for (Double number : numbers) {
                total += number.doubleValue();
            }
            return total / numbers.size();
        }

        private Object clampFunctionValue(String functionName, List<String> rawArgs) {
            double value = requireArithmeticNumber(functionName, evaluateNestedValue(rawArgs.get(0), functionName));
            double min = requireArithmeticNumber(functionName, evaluateNestedValue(rawArgs.get(1), functionName));
            double max = requireArithmeticNumber(functionName, evaluateNestedValue(rawArgs.get(2), functionName));
            if (min > max) {
                double temp = min;
                min = max;
                max = temp;
            }
            return Math.max(min, Math.min(max, value));
        }

        private List<Double> collectFunctionNumbers(String functionName, List<String> rawArgs) {
            List<Double> numbers = new ArrayList<Double>();
            if (rawArgs == null) {
                return numbers;
            }
            for (String rawArg : rawArgs) {
                Object value = evaluateNestedValue(rawArg, functionName);
                Double number = optionalNumericValue(functionName, value);
                if (number != null) {
                    numbers.add(number);
                }
            }
            return numbers;
        }

        private Double optionalNumericValue(String functionName, Object value) {
            if (value == null) {
                return null;
            }
            Double number = toNumber(value);
            if (number == null) {
                throw error("函数 " + functionName + " 需要数字参数");
            }
            return number;
        }

        private Object resolveIdentifier(String token) {
            String name = normalizeSymbol(token);
            switch (name) {
                case "name":
                    return snapshot.displayName;
                case "id":
                case "registry":
                    return snapshot.registryName;
                case "count":
                case "stacksize":
                    return snapshot.count;
                case "slot":
                    return snapshot.slotIndex;
                case "damage":
                case "meta":
                    return snapshot.itemDamage;
                case "hasnbt":
                    return snapshot.hasNbt;
                case "nbtraw":
                case "rawnbt":
                    return snapshot.rawNbt;
                case "tooltip":
                    return joinLines(snapshot.tooltipLines);
                case "lore":
                    return joinLines(snapshot.loreLines);
                case "rarity":
                    return snapshot.rarity;
                case "distance":
                case "dist":
                    return snapshot.distance;
                case "alltext":
                case "search":
                    return snapshot.searchableText;
                default:
                    throw error("未知字段或函数名: " + token);
            }
        }

        private Object evaluateComparison(Object left, String operator, Object right) {
            Integer numericCompare = null;
            switch (operator) {
                case "==":
                    return compareEquals(left, right);
                case "!=":
                    return !compareEquals(left, right);
                case ">":
                    numericCompare = compareNumbers(operator, left, right);
                    return numericCompare != null && numericCompare.intValue() > 0;
                case "<":
                    numericCompare = compareNumbers(operator, left, right);
                    return numericCompare != null && numericCompare.intValue() < 0;
                case ">=":
                    numericCompare = compareNumbers(operator, left, right);
                    return numericCompare != null && numericCompare.intValue() >= 0;
                case "<=":
                    numericCompare = compareNumbers(operator, left, right);
                    return numericCompare != null && numericCompare.intValue() <= 0;
                default:
                    throw error("不支持的比较操作符: " + operator);
            }
        }

        private Integer compareNumbers(String operator, Object left, Object right) {
            Double leftNumber = optionalNumericValue(operator, left);
            Double rightNumber = optionalNumericValue(operator, right);
            if (leftNumber == null || rightNumber == null) {
                return null;
            }
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }

        private boolean compareEquals(Object left, Object right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            Double leftNumber = toNumber(left);
            Double rightNumber = toNumber(right);
            if (leftNumber != null && rightNumber != null) {
                return Double.compare(leftNumber, rightNumber) == 0;
            }
            if (left instanceof Boolean || right instanceof Boolean) {
                return toBoolean(left) == toBoolean(right);
            }
            return normalizeComparableText(stringValue(left))
                    .equals(normalizeComparableText(stringValue(right)));
        }

        private String parseQuotedString() {
            char quote = expression.charAt(index++);
            StringBuilder builder = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index++);
                if (c == '\\' && index < expression.length()) {
                    char escaped = expression.charAt(index++);
                    switch (escaped) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        default:
                            builder.append(escaped);
                            break;
                    }
                    continue;
                }
                if (c == quote) {
                    return builder.toString();
                }
                builder.append(c);
            }
            throw error("字符串未闭合");
        }

        private Double parseNumber() {
            int start = index;
            boolean seenDot = false;
            if (expression.charAt(index) == '.') {
                seenDot = true;
                index++;
            }
            while (index < expression.length()
                    && (Character.isDigit(expression.charAt(index))
                    || (!seenDot && expression.charAt(index) == '.'))) {
                if (expression.charAt(index) == '.') {
                    seenDot = true;
                }
                index++;
            }
            try {
                return Double.parseDouble(expression.substring(start, index));
            } catch (NumberFormatException e) {
                throw error("数字格式无效");
            }
        }

        private String parseToken() {
            StringBuilder builder = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (Character.isWhitespace(c)
                        || c == '(' || c == ')' || c == '（' || c == '）'
                        || c == '&' || c == '|' || c == '＆' || c == '｜'
                        || c == '=' || c == '!' || c == '>' || c == '<'
                        || c == '＝' || c == '！' || c == '＞' || c == '＜'
                        || c == ',' || c == '，'
                        || c == '+' || c == '-' || c == '*' || c == '/' || c == '%'
                        || c == '＋' || c == '－' || c == '＊' || c == '／' || c == '％') {
                    break;
                }
                builder.append(c);
                index++;
            }
            return builder.toString().trim();
        }

        private String tryParseOperator() {
            String[] operators = new String[] { "==", "!=", ">=", "<=", ">", "<", "＝＝", "！＝", "＞＝", "＜＝",
                    "＞", "＜" };
            for (String operator : operators) {
                if (expression.regionMatches(index, operator, 0, operator.length())) {
                    index += operator.length();
                    return normalizeOperator(operator);
                }
            }
            return null;
        }

        private void requireArgCountAtLeast(String functionName, List<String> rawArgs, int expected) {
            if (rawArgs == null || rawArgs.size() < expected) {
                throw error("函数 " + functionName + " 至少需要 " + expected + " 个参数");
            }
        }

        private void requireExactArgCount(String functionName, List<String> rawArgs, int expected) {
            int size = rawArgs == null ? 0 : rawArgs.size();
            if (size != expected) {
                throw error("函数 " + functionName + " 需要 " + expected + " 个参数");
            }
        }

        private double requireNumber(String name, Object value) {
            Double number = toNumber(value);
            if (number == null) {
                throw error("比较/函数 " + name + " 需要数字参数");
            }
            return number.doubleValue();
        }

        private double requireArithmeticNumber(String name, Object value) {
            if (value == null) {
                return 0D;
            }
            Double number = toNumber(value);
            if (number == null) {
                throw error("运算/函数 " + name + " 需要数字参数");
            }
            return number.doubleValue();
        }

        private Double toNumber(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue() ? 1D : 0D;
            }
            Matcher matcher = NUMBER_PATTERN.matcher(stringValue(value));
            if (!matcher.find()) {
                return null;
            }
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private boolean toBoolean(Object value) {
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }
            if (value instanceof Number) {
                return Double.compare(((Number) value).doubleValue(), 0D) != 0;
            }
            if (value == null) {
                return false;
            }
            String text = stringValue(value).trim();
            if (text.isEmpty()) {
                return false;
            }
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
            return true;
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean match(String token) {
            if (expression.regionMatches(index, token, 0, token.length())) {
                index += token.length();
                return true;
            }
            return false;
        }

        private boolean matchAny(String... tokens) {
            if (tokens == null) {
                return false;
            }
            for (String token : tokens) {
                if (token != null && match(token)) {
                    return true;
                }
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            int safeIndex = Math.max(0, Math.min(index, expression.length()));
            int start = Math.max(0, safeIndex - 12);
            int end = Math.min(expression.length(), safeIndex + 12);
            String snippet = expression.substring(start, end);
            return new IllegalArgumentException((message == null ? "物品过滤表达式解析失败" : message)
                    + " @ 位置 " + safeIndex + " 附近: `" + snippet + "`");
        }
    }

    private static String stripFormatting(String text) {
        String stripped = safe(text);
        String cleaned = TextFormatting.getTextWithoutFormattingCodes(stripped);
        if (cleaned == null) {
            cleaned = COLOR_CODE_PATTERN.matcher(stripped).replaceAll("");
        }
        return cleaned.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String normalizeComparableText(String text) {
        String cleaned = stripFormatting(text)
                .replace('：', ':')
                .replace('，', ',')
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'');
        return cleaned.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean containsNormalized(Object source, Object expected) {
        String normalizedSource = normalizeComparableText(stringValue(source));
        String normalizedExpected = normalizeComparableText(stringValue(expected));
        return !normalizedExpected.isEmpty() && normalizedSource.contains(normalizedExpected);
    }

    private static int countOccurrencesInSource(String source, String expected) {
        String normalizedSource = normalizeComparableText(source);
        String normalizedExpected = normalizeComparableText(expected);
        if (normalizedSource.isEmpty() || normalizedExpected.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while (true) {
            int matchIndex = normalizedSource.indexOf(normalizedExpected, index);
            if (matchIndex < 0) {
                return count;
            }
            count++;
            index = matchIndex + normalizedExpected.length();
        }
    }

    private static int countMatchingLines(String source, String expected) {
        List<String> lines = splitMeaningfulLines(source);
        String normalizedExpected = normalizeComparableText(expected);
        if (lines.isEmpty() || normalizedExpected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : lines) {
            if (normalizeComparableText(line).contains(normalizedExpected)) {
                count++;
            }
        }
        return count;
    }

    private static int countDistinctLines(String source) {
        LinkedHashSet<String> uniqueLines = new LinkedHashSet<String>();
        for (String line : splitMeaningfulLines(source)) {
            String normalizedLine = normalizeComparableText(line);
            if (!normalizedLine.isEmpty()) {
                uniqueLines.add(normalizedLine);
            }
        }
        return uniqueLines.size();
    }

    private static int countDistinctMatchingLines(String source, String expected) {
        List<String> lines = splitMeaningfulLines(source);
        String normalizedExpected = normalizeComparableText(expected);
        if (lines.isEmpty() || normalizedExpected.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> uniqueLines = new LinkedHashSet<String>();
        for (String line : lines) {
            String normalizedLine = normalizeComparableText(line);
            if (!normalizedLine.isEmpty() && normalizedLine.contains(normalizedExpected)) {
                uniqueLines.add(normalizedLine);
            }
        }
        return uniqueLines.size();
    }

    private static int countRegexMatchingLines(String source, String pattern, String functionName) {
        List<String> lines = splitMeaningfulLines(source);
        if (lines.isEmpty()) {
            return 0;
        }
        Pattern compiled = compileRegex(pattern, functionName);
        int count = 0;
        for (String line : lines) {
            if (compiled.matcher(stripFormatting(line)).find()) {
                count++;
            }
        }
        return count;
    }

    private static int countDistinctRegexMatchingLines(String source, String pattern, String functionName) {
        List<String> lines = splitMeaningfulLines(source);
        if (lines.isEmpty()) {
            return 0;
        }
        Pattern compiled = compileRegex(pattern, functionName);
        LinkedHashSet<String> uniqueLines = new LinkedHashSet<String>();
        for (String line : lines) {
            String cleanedLine = stripFormatting(line);
            if (compiled.matcher(cleanedLine).find()) {
                String normalizedLine = normalizeComparableText(cleanedLine);
                if (!normalizedLine.isEmpty()) {
                    uniqueLines.add(normalizedLine);
                }
            }
        }
        return uniqueLines.size();
    }

    private static int countRegexMatches(String source, String pattern, String functionName) {
        String text = stringValue(source);
        String regex = safe(pattern);
        if (text.trim().isEmpty() || regex.trim().isEmpty()) {
            return 0;
        }
        Matcher matcher = compileRegex(regex, functionName).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Pattern compileRegex(String pattern, String functionName) {
        String regex = safe(pattern);
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (Exception e) {
            String label = safe(functionName).trim();
            throw new IllegalArgumentException("函数 " + (label.isEmpty() ? "regex" : label)
                    + " 的正则格式无效: " + safe(e.getMessage()));
        }
    }

    private static Double extractFirstNumber(String value) {
        String text = safe(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> splitMeaningfulLines(String source) {
        String text = stringValue(source).replace('\r', '\n');
        List<String> lines = new ArrayList<String>();
        if (text.trim().isEmpty()) {
            return lines;
        }
        for (String line : text.split("\\n+")) {
            String cleanedLine = stripFormatting(line);
            if (!cleanedLine.isEmpty()) {
                lines.add(cleanedLine);
            }
        }
        return lines;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isNullish(Object value) {
        return value == null || stringValue(value).trim().isEmpty();
    }

    private static String resolveRawText(String raw) {
        String text = safe(raw).trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.length() >= 2 ? text.substring(1, text.length() - 1) : "";
        }
        return text;
    }

    private static String normalizeOperator(String operator) {
        if ("＝＝".equals(operator)) {
            return "==";
        }
        if ("！＝".equals(operator)) {
            return "!=";
        }
        if ("＞＝".equals(operator)) {
            return ">=";
        }
        if ("＜＝".equals(operator)) {
            return "<=";
        }
        if ("＞".equals(operator)) {
            return ">";
        }
        if ("＜".equals(operator)) {
            return "<";
        }
        return operator;
    }

    private static String normalizeSymbol(String raw) {
        return safe(raw).trim().replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String safeLine = safe(line).trim();
            if (safeLine.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(safeLine);
        }
        return builder.toString();
    }

    private static int findKeyValueSeparator(String text) {
        String safeText = safe(text);
        for (int i = 0; i < safeText.length(); i++) {
            char c = safeText.charAt(i);
            if (c == ':' || c == '：' || c == '=') {
                return i;
            }
        }
        return -1;
    }

    private static String joinBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(bytes[i]);
        }
        return builder.toString();
    }

    private static String joinInts(int[] ints) {
        if (ints == null || ints.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ints.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ints[i]);
        }
        return builder.toString();
    }

    private static String nbtValueToString(NBTBase tag) {
        if (tag == null) {
            return "";
        }
        if (tag instanceof NBTTagString) {
            return ((NBTTagString) tag).getString();
        }
        if (tag instanceof NBTTagInt) {
            return Integer.toString(((NBTTagInt) tag).getInt());
        }
        if (tag instanceof NBTTagShort) {
            return Short.toString(((NBTTagShort) tag).getShort());
        }
        if (tag instanceof NBTTagLong) {
            return Long.toString(((NBTTagLong) tag).getLong());
        }
        if (tag instanceof NBTTagFloat) {
            return Float.toString(((NBTTagFloat) tag).getFloat());
        }
        if (tag instanceof NBTTagDouble) {
            return Double.toString(((NBTTagDouble) tag).getDouble());
        }
        if (tag instanceof NBTTagByte) {
            return Byte.toString(((NBTTagByte) tag).getByte());
        }
        return tag.toString();
    }
}
