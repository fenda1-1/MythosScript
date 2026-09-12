package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;

/**
 * Shared JSON helpers for the modern action editor. Unknown params are never
 * removed here; canonicalize only sorts known arrays and writes slotLimits.
 */
public final class ActionEditorJson {
    private ActionEditorJson() {
    }

    // Keep incomplete edits as strings; validation runs on the completed action.
    public static JsonElement numericDraft(String raw) {
        String text = raw == null ? "" : raw;
        String trimmed = text.trim();
        try {
            if (trimmed.matches("[-+]?\\d+")) {
                return new JsonPrimitive(Long.parseLong(trimmed));
            }
            if (trimmed.matches("[-+]?(?:\\d+\\.\\d+|\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?")) {
                double number = Double.parseDouble(trimmed);
                if (Double.isFinite(number)) {
                    return new JsonPrimitive(number);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return new JsonPrimitive(text);
    }

    public static int readInt(JsonObject params, String key, int fallback, int min, int max) {
        if (params == null || !params.has(key) || !params.get(key).isJsonPrimitive()) {
            return clamp(fallback, min, max);
        }
        try {
            return clamp(params.get(key).getAsInt(), min, max);
        } catch (Exception ignored) {
            return clamp(fallback, min, max);
        }
    }

    public static Integer readOptionalInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            JsonPrimitive primitive = object.get(key).getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return Integer.valueOf(primitive.getAsInt());
            }
            String text = primitive.getAsString();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            return Integer.valueOf(Integer.parseInt(text.trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void writeInt(JsonObject params, String key, int value) {
        if (params == null || key == null) {
            return;
        }
        params.addProperty(key, value);
    }

    public static String readString(JsonObject params, String key, String fallback) {
        if (params == null || !params.has(key) || !params.get(key).isJsonPrimitive()) {
            return fallback == null ? "" : fallback;
        }
        try {
            String value = params.get(key).getAsString();
            return value == null ? (fallback == null ? "" : fallback) : value;
        } catch (Exception ignored) {
            return fallback == null ? "" : fallback;
        }
    }

    public static boolean readBoolean(JsonObject params, String key, boolean fallback) {
        if (params == null || !params.has(key) || !params.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static List<Integer> readSortedSlots(JsonObject params, String key) {
        Set<Integer> unique = new LinkedHashSet<Integer>();
        if (params == null || !params.has(key) || !params.get(key).isJsonArray()) {
            return new ArrayList<Integer>();
        }
        for (JsonElement element : params.get(key).getAsJsonArray()) {
            try {
                int value = element.getAsInt();
                if (value >= 0) {
                    unique.add(Integer.valueOf(value));
                }
            } catch (Exception ignored) {
            }
        }
        List<Integer> result = new ArrayList<Integer>(unique);
        Collections.sort(result);
        return result;
    }

    public static void writeSortedSlots(JsonObject params, String key, Set<Integer> slots, int maxIndexExclusive) {
        if (params == null || key == null) {
            return;
        }
        Set<Integer> unique = new LinkedHashSet<Integer>();
        if (slots != null) {
            for (Integer slot : slots) {
                if (slot != null && slot.intValue() >= 0 && slot.intValue() < maxIndexExclusive) {
                    unique.add(slot);
                }
            }
        }
        List<Integer> sorted = new ArrayList<Integer>(unique);
        Collections.sort(sorted);
        JsonArray array = new JsonArray();
        for (Integer slot : sorted) {
            array.add(slot);
        }
        params.add(key, array);
    }

    public static JsonObject regionLimits(JsonObject params, String region) {
        if (params == null || !params.has("slotLimits") || !params.get("slotLimits").isJsonObject()) {
            return null;
        }
        JsonObject slotLimits = params.getAsJsonObject("slotLimits");
        if (slotLimits == null || !slotLimits.has(region) || !slotLimits.get(region).isJsonObject()) {
            return null;
        }
        return slotLimits.getAsJsonObject(region);
    }

    public static Integer maxTake(JsonObject params, String region, int index) {
        return limitValue(params, region, index, "maxTake");
    }

    public static Integer maxPut(JsonObject params, String region, int index) {
        return limitValue(params, region, index, "maxPut");
    }

    private static Integer limitValue(JsonObject params, String region, int index, String property) {
        JsonObject regionObject = regionLimits(params, region);
        if (regionObject == null) {
            return null;
        }
        String key = String.valueOf(index);
        if (!regionObject.has(key) || !regionObject.get(key).isJsonObject()) {
            return null;
        }
        return readOptionalInt(regionObject.getAsJsonObject(key), property);
    }

    public static void applyLimits(JsonObject params, String region, Set<Integer> slots, Integer maxTake,
            Integer maxPut) {
        if (params == null || region == null || slots == null || slots.isEmpty()) {
            return;
        }
        JsonObject slotLimits = params.has("slotLimits") && params.get("slotLimits").isJsonObject()
                ? params.getAsJsonObject("slotLimits") : new JsonObject();
        JsonObject regionObject = slotLimits.has(region) && slotLimits.get(region).isJsonObject()
                ? slotLimits.getAsJsonObject(region) : new JsonObject();
        for (Integer slot : slots) {
            if (slot == null || slot.intValue() < 0) {
                continue;
            }
            String key = String.valueOf(slot);
            JsonObject entry = regionObject.has(key) && regionObject.get(key).isJsonObject()
                    ? regionObject.getAsJsonObject(key) : new JsonObject();
            writeLimitProperty(entry, "maxTake", maxTake);
            writeLimitProperty(entry, "maxPut", maxPut);
            if (entry.entrySet().isEmpty()) {
                regionObject.remove(key);
            } else {
                regionObject.add(key, entry);
            }
        }
        if (regionObject.entrySet().isEmpty()) {
            slotLimits.remove(region);
        } else {
            slotLimits.add(region, regionObject);
        }
        if (slotLimits.entrySet().isEmpty()) {
            params.remove("slotLimits");
        } else {
            params.add("slotLimits", slotLimits);
        }
    }

    private static void writeLimitProperty(JsonObject entry, String key, Integer value) {
        if (value == null) {
            entry.remove(key);
            return;
        }
        if (value.intValue() < 0) {
            return;
        }
        entry.addProperty(key, value);
    }

    public static String validateSlotLimits(JsonObject params) {
        if (params == null || !params.has("slotLimits")) {
            return "";
        }
        if (!params.get("slotLimits").isJsonObject()) {
            return "gui.modern.path.json.u001";
        }
        JsonObject slotLimits = params.getAsJsonObject("slotLimits");
        for (String region : new String[] { "inventory", "chest" }) {
            if (!slotLimits.has(region)) {
                continue;
            }
            if (!slotLimits.get(region).isJsonObject()) {
                return tr("gui.modern.path.json.fmt.region_obj", region);
            }
            JsonObject regionObject = slotLimits.getAsJsonObject(region);
            for (java.util.Map.Entry<String, JsonElement> entry : regionObject.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    return tr("gui.modern.path.json.fmt.entry_obj", region, entry.getKey());
                }
                JsonObject limits = entry.getValue().getAsJsonObject();
                String takeError = validateLimitValue(limits, "maxTake");
                if (!takeError.isEmpty()) {
                    return takeError;
                }
                String putError = validateLimitValue(limits, "maxPut");
                if (!putError.isEmpty()) {
                    return putError;
                }
            }
        }
        return "";
    }

    private static String validateLimitValue(JsonObject limits, String key) {
        if (limits == null || !limits.has(key) || limits.get(key).isJsonNull()) {
            return "";
        }
        if (!limits.get(key).isJsonPrimitive()) {
            return tr("gui.modern.path.json.fmt.int", key);
        }
        try {
            int value = limits.get(key).getAsInt();
            if (value < 0) {
                return tr("gui.modern.path.json.fmt.negative", key);
            }
        } catch (Exception ignored) {
            return tr("gui.modern.path.json.fmt.int", key);
        }
        return "";
    }

    public static void canonicalizeMoveChestActions(List<PathSequence> sequences) {
        if (sequences == null) {
            return;
        }
        for (PathSequence sequence : sequences) {
            if (sequence == null) {
                continue;
            }
            for (PathStep step : sequence.getSteps()) {
                if (step == null) {
                    continue;
                }
                for (ActionData action : step.getActions()) {
                    canonicalizeMoveChest(action);
                }
            }
        }
    }

    public static void canonicalizeMoveChest(ActionData action) {
        if (action == null || action.params == null) {
            return;
        }
        if (!"move_inventory_items_to_chest_slots".equalsIgnoreCase(safe(action.type))) {
            return;
        }
        JsonObject params = action.params;
        writeSortedSlots(params, "inventorySlots",
                new LinkedHashSet<Integer>(readSortedSlots(params, "inventorySlots")), Integer.MAX_VALUE);
        writeSortedSlots(params, "chestSlots",
                new LinkedHashSet<Integer>(readSortedSlots(params, "chestSlots")), Integer.MAX_VALUE);
        if (!params.has("moveDirection") || !params.get("moveDirection").isJsonPrimitive()) {
            params.addProperty("moveDirection", ItemFilterHandler.MOVE_DIRECTION_INVENTORY_TO_CHEST);
        }
        if (!params.has("clickType") || !params.get("clickType").isJsonPrimitive()) {
            params.addProperty("clickType", "PICKUP");
        }
        if (!params.has("button") || !params.get("button").isJsonPrimitive()) {
            params.addProperty("button", 0);
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static String hexError(String hex) {
        String text = safe(hex).trim().replace(" ", "").replace("\r", "").replace("\n", "");
        if (text.isEmpty()) {
            return "";
        }
        if ((text.length() & 1) == 1) {
            return "gui.modern.path.json.u002";
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean hexChar = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            if (!hexChar) {
                return "gui.modern.path.json.u003";
            }
        }
        return "";
    }

    public static List<String> readLines(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] tokens = text.split("\\r?\\n", -1);
        for (String token : tokens) {
            lines.add(token);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    public static String writeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i) == null ? "" : lines.get(i));
        }
        return builder.toString();
    }

    public static boolean isMoveChestOwnedKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim();
        return "delayTicks".equals(normalized)
                || "chestRows".equals(normalized)
                || "chestCols".equals(normalized)
                || "inventoryRows".equals(normalized)
                || "inventoryCols".equals(normalized)
                || "moveDirection".equals(normalized)
                || "button".equals(normalized)
                || "clickType".equals(normalized)
                || "inventorySlots".equals(normalized)
                || "chestSlots".equals(normalized)
                || "moveChestCanvas".equals(normalized);
    }

    public static boolean isSpinnerKey(String type, String key) {
        if (key == null) {
            return false;
        }
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if ("delay".equals(t) && "ticks".equals(key)) {
            return false;
        }
        return "count".equals(key)
                || "intervalTicks".equals(key)
                || "delayTicks".equals(key)
                || "pressDurationTicks".equals(key)
                || "retryCount".equals(key)
                || "retryDelayTicks".equals(key)
                || "maxLoops".equals(key)
                || "bodyCount".equals(key)
                || "thenCount".equals(key)
                || "elseCount".equals(key)
                || "defaultCount".equals(key)
                || "preExecuteCount".equals(key)
                || "timeoutTicks".equals(key)
                || "timeoutSkipCount".equals(key)
                || "skipCount".equals(key)
                || "minCount".equals(key)
                || "maxCount".equals(key)
                || "maxItems".equals(key)
                || "timeoutSeconds".equals(key)
                || "timeout".equals(key)
                || "foodLevelThreshold".equals(key)
                || "targetHotbarSlot".equals(key)
                || "tempslot".equals(key)
                || "switchItemDelayTicks".equals(key)
                || "switchDelayTicks".equals(key)
                || "switchBackDelayTicks".equals(key)
                || "useDelayTicks".equals(key)
                || "useAfterSwitchDelayTicks".equals(key)
                || "executeEveryCount".equals(key)
                || "perSlotCount".equals(key)
                || "colorTolerance".equals(key)
                || "lineIndex".equals(key)
                || "slotIndex".equals(key)
                || "slot".equals(key)
                || "windowId".equals(key)
                || "noTargetSkipCount".equals(key);
    }

    public static boolean isNumericKey(String type, String key) {
        if (isSpinnerKey(type, key)) {
            return true;
        }
        return "x".equals(key) || "y".equals(key) || "yaw".equals(key) || "pitch".equals(key)
                || "range".equals(key) || "searchRadius".equals(key) || "followDistance".equals(key)
                || "reachDistance".equals(key) || "huntUpRange".equals(key) || "huntDownRange".equals(key)
                || "areaSweepCellSize".equals(key) || "scanRadius".equals(key)
                || "similarityThreshold".equals(key) || "edgeThreshold".equals(key);
    }
    private static String tr(String key) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key, args);
    }

}
