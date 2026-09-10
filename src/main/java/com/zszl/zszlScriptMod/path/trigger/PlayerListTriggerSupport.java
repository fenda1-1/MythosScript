package com.zszl.zszlScriptMod.path.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerListTriggerSupport {
    public static final String PARAM_ENTRIES = "entries";
    public static final String MODE_EXACT = "exact";
    public static final String MODE_CONTAINS = "contains";

    public static final class RuleEntry {
        public String name = "";
        public String mode = MODE_EXACT;

        public RuleEntry() {
        }

        public RuleEntry(String name, String mode) {
            this.name = normalizeName(name);
            this.mode = normalizeMode(mode);
        }
    }

    public static final class PlayerRecord {
        public final String profileName;
        public final String displayName;

        public PlayerRecord(String profileName, String displayName) {
            this.profileName = safe(profileName).trim();
            this.displayName = safe(displayName).trim();
        }

        public String getSuggestedEntryName() {
            return !profileName.isEmpty() ? profileName : displayName;
        }

        public String getLabel() {
            if (profileName.isEmpty()) {
                return displayName;
            }
            if (displayName.isEmpty() || displayName.equalsIgnoreCase(profileName)) {
                return profileName;
            }
            return displayName + " (" + profileName + ")";
        }

        public String getMatchText() {
            if (displayName.isEmpty() || displayName.equalsIgnoreCase(profileName)) {
                return profileName.toLowerCase(Locale.ROOT);
            }
            return (profileName + " | " + displayName).toLowerCase(Locale.ROOT);
        }

        public String getIdentityKey() {
            return getMatchText();
        }
    }

    public static final class PlayerSnapshot {
        public final List<PlayerRecord> players;
        public final String signature;

        public PlayerSnapshot(List<PlayerRecord> players, String signature) {
            this.players = players == null ? Collections.<PlayerRecord>emptyList() : players;
            this.signature = signature == null ? "" : signature;
        }
    }

    private PlayerListTriggerSupport() {
    }

    public static List<RuleEntry> copyEntries(List<RuleEntry> source) {
        List<RuleEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (RuleEntry entry : source) {
            if (entry == null) {
                continue;
            }
            String normalizedName = normalizeName(entry.name);
            if (normalizedName.isEmpty()) {
                continue;
            }
            copy.add(new RuleEntry(normalizedName, normalizeMode(entry.mode)));
        }
        return dedupeEntries(copy);
    }

    public static List<RuleEntry> readEntries(JsonObject params) {
        List<RuleEntry> entries = new ArrayList<>();
        if (params == null || !params.has(PARAM_ENTRIES) || !params.get(PARAM_ENTRIES).isJsonArray()) {
            return entries;
        }
        JsonArray array = params.getAsJsonArray(PARAM_ENTRIES);
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String name = normalizeName(getString(object, "name"));
            if (name.isEmpty()) {
                continue;
            }
            entries.add(new RuleEntry(name, normalizeMode(getString(object, "mode"))));
        }
        return dedupeEntries(entries);
    }

    public static void writeEntries(JsonObject params, List<RuleEntry> entries) {
        if (params == null) {
            return;
        }
        JsonArray array = new JsonArray();
        for (RuleEntry entry : copyEntries(entries)) {
            JsonObject object = new JsonObject();
            object.addProperty("name", entry.name);
            object.addProperty("mode", entry.mode);
            array.add(object);
        }
        if (array.size() <= 0) {
            params.remove(PARAM_ENTRIES);
        } else {
            params.add(PARAM_ENTRIES, array);
        }
    }

    public static void sanitizeParams(JsonObject params) {
        if (params == null) {
            return;
        }
        writeEntries(params, readEntries(params));
    }

    public static String buildEntriesSummary(List<RuleEntry> entries) {
        List<RuleEntry> normalized = copyEntries(entries);
        if (normalized.isEmpty()) {
            return "未配置名称卡片时，只要当前 Tab 玩家列表可见就会按冷却触发。";
        }
        List<String> labels = new ArrayList<>();
        for (RuleEntry entry : normalized) {
            labels.add(entry.name + (MODE_CONTAINS.equals(entry.mode) ? " [包含]" : " [全匹配]"));
        }
        String summary = String.join(" / ", labels);
        return summary.length() <= 120 ? summary : summary.substring(0, 117) + "...";
    }

    public static PlayerSnapshot captureSnapshot(Minecraft mc) {
        if (mc == null) {
            return new PlayerSnapshot(Collections.<PlayerRecord>emptyList(), "");
        }
        NetHandlerPlayClient connection = mc.getConnection();
        if (connection == null) {
            return new PlayerSnapshot(Collections.<PlayerRecord>emptyList(), "");
        }
        Collection<NetworkPlayerInfo> onlinePlayers = connection.getPlayerInfoMap();
        if (onlinePlayers == null || onlinePlayers.isEmpty()) {
            return new PlayerSnapshot(Collections.<PlayerRecord>emptyList(), "");
        }
        List<PlayerRecord> players = new ArrayList<>();
        for (NetworkPlayerInfo info : onlinePlayers) {
            if (info == null) {
                continue;
            }
            GameProfile profile = info.getGameProfile();
            String profileName = profile == null ? "" : safe(profile.getName());
            ITextComponent displayComponent = info.getDisplayName();
            String displayName = displayComponent == null ? "" : safe(displayComponent.getUnformattedText());
            PlayerRecord record = new PlayerRecord(profileName, displayName);
            if (!record.profileName.isEmpty() || !record.displayName.isEmpty()) {
                players.add(record);
            }
        }
        players.sort(Comparator.comparing(PlayerRecord::getLabel, String.CASE_INSENSITIVE_ORDER));
        return new PlayerSnapshot(players, buildSignature(players));
    }

    public static JsonObject buildTriggerEvent(PlayerSnapshot before, PlayerSnapshot after) {
        PlayerSnapshot safeBefore = before == null ? new PlayerSnapshot(Collections.<PlayerRecord>emptyList(), "") : before;
        PlayerSnapshot safeAfter = after == null ? new PlayerSnapshot(Collections.<PlayerRecord>emptyList(), "") : after;
        List<PlayerRecord> joined = diffPlayers(safeBefore.players, safeAfter.players);
        List<PlayerRecord> left = diffPlayers(safeAfter.players, safeBefore.players);

        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("before", joinLabels(safeBefore.players));
        triggerData.addProperty("after", joinLabels(safeAfter.players));
        triggerData.addProperty("playersText", joinLabels(safeAfter.players));
        triggerData.addProperty("joined", joinLabels(joined));
        triggerData.addProperty("left", joinLabels(left));
        triggerData.addProperty("count", safeAfter.players.size());
        triggerData.addProperty("joinedCount", joined.size());
        triggerData.addProperty("leftCount", left.size());
        triggerData.add("players", toJsonArray(safeAfter.players));
        triggerData.add("joinedPlayers", toJsonArray(joined));
        triggerData.add("leftPlayers", toJsonArray(left));
        return triggerData;
    }

    public static boolean matchesConfiguredPlayers(JsonObject params, JsonObject eventData) {
        List<RuleEntry> entries = readEntries(params);
        if (entries.isEmpty()) {
            return true;
        }

        List<PlayerRecord> candidates = new ArrayList<>();
        candidates.addAll(readPlayers(eventData, "joinedPlayers"));
        candidates.addAll(readPlayers(eventData, "leftPlayers"));
        candidates.addAll(readPlayers(eventData, "players"));
        if (candidates.isEmpty()) {
            return false;
        }

        for (RuleEntry entry : entries) {
            for (PlayerRecord player : candidates) {
                if (matchesEntry(entry, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesEntry(RuleEntry entry, PlayerRecord player) {
        if (entry == null || player == null) {
            return false;
        }
        String expected = normalizeName(entry.name).toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return false;
        }
        String profile = safe(player.profileName).toLowerCase(Locale.ROOT);
        String display = safe(player.displayName).toLowerCase(Locale.ROOT);
        if (MODE_CONTAINS.equals(normalizeMode(entry.mode))) {
            return profile.contains(expected) || display.contains(expected) || player.getMatchText().contains(expected);
        }
        return expected.equals(profile) || (!display.isEmpty() && expected.equals(display));
    }

    private static List<PlayerRecord> readPlayers(JsonObject eventData, String key) {
        List<PlayerRecord> players = new ArrayList<>();
        if (eventData == null || key == null || !eventData.has(key) || !eventData.get(key).isJsonArray()) {
            return players;
        }
        for (JsonElement element : eventData.getAsJsonArray(key)) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            PlayerRecord player = new PlayerRecord(getString(object, "name"), getString(object, "displayName"));
            if (!player.profileName.isEmpty() || !player.displayName.isEmpty()) {
                players.add(player);
            }
        }
        return players;
    }

    private static JsonArray toJsonArray(List<PlayerRecord> players) {
        JsonArray array = new JsonArray();
        if (players == null) {
            return array;
        }
        for (PlayerRecord player : players) {
            if (player == null) {
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("name", player.profileName);
            object.addProperty("displayName", player.displayName);
            object.addProperty("label", player.getLabel());
            array.add(object);
        }
        return array;
    }

    private static List<PlayerRecord> diffPlayers(List<PlayerRecord> base, List<PlayerRecord> target) {
        Map<String, PlayerRecord> baseMap = toPlayerMap(base);
        List<PlayerRecord> diff = new ArrayList<>();
        if (target == null) {
            return diff;
        }
        for (PlayerRecord player : target) {
            if (player == null) {
                continue;
            }
            if (!baseMap.containsKey(player.getIdentityKey())) {
                diff.add(player);
            }
        }
        return diff;
    }

    private static Map<String, PlayerRecord> toPlayerMap(List<PlayerRecord> players) {
        Map<String, PlayerRecord> map = new LinkedHashMap<>();
        if (players == null) {
            return map;
        }
        for (PlayerRecord player : players) {
            if (player == null) {
                continue;
            }
            map.put(player.getIdentityKey(), player);
        }
        return map;
    }

    private static String buildSignature(List<PlayerRecord> players) {
        if (players == null || players.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<>();
        for (PlayerRecord player : players) {
            if (player != null) {
                keys.add(player.getIdentityKey());
            }
        }
        Collections.sort(keys);
        return String.join("\n", keys);
    }

    private static String joinLabels(List<PlayerRecord> players) {
        if (players == null || players.isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (PlayerRecord player : players) {
            if (player != null) {
                labels.add(player.getLabel());
            }
        }
        return String.join(" | ", labels);
    }

    private static List<RuleEntry> dedupeEntries(List<RuleEntry> entries) {
        List<RuleEntry> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (entries == null) {
            return result;
        }
        for (RuleEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String key = normalizeName(entry.name).toLowerCase(Locale.ROOT) + "|" + normalizeMode(entry.mode);
            if (seen.add(key)) {
                result.add(new RuleEntry(entry.name, entry.mode));
            }
        }
        return result;
    }

    public static String normalizeName(String value) {
        return safe(value).trim();
    }

    public static String normalizeMode(String value) {
        return MODE_CONTAINS.equalsIgnoreCase(safe(value).trim()) ? MODE_CONTAINS : MODE_EXACT;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return "";
        }
        try {
            return safe(object.get(key).getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
