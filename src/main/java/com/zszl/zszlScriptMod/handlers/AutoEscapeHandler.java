package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.system.AutoEscapeRule;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoEscapeHandler {
    public static final AutoEscapeHandler INSTANCE = new AutoEscapeHandler();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<AutoEscapeRule> rules = new CopyOnWriteArrayList<>();
    private static final List<String> categories = new CopyOnWriteArrayList<>();
    private static final String CATEGORY_DEFAULT = "默认";
    private static boolean masterEnabled = true;

    private enum RuntimeState {
        IDLE,
        ESCAPING,
        WAITING_RESTART,
        RESTARTING
    }

    private static RuntimeState runtimeState = RuntimeState.IDLE;
    private static AutoEscapeRule activeRule = null;
    private static String activeEscapeSequenceName = "";
    private static String pendingRestartSequenceName = "";
    private static long restartExecuteAtMs = 0L;
    private static long lastNotifyAtMs = 0L;

    private AutoEscapeHandler() {
    }

    public static synchronized void loadConfig() {
        rules.clear();
        categories.clear();
        masterEnabled = true;

        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) {
            ensureCategoriesSynced();
            resetRuntimeState();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            List<AutoEscapeRule> loadedRules = null;
            List<String> loadedCategories = new ArrayList<>();

            if (parsed != null && parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (root.has("masterEnabled") && root.get("masterEnabled").isJsonPrimitive()) {
                    masterEnabled = root.get("masterEnabled").getAsBoolean();
                }
                if (root.has("categories") && root.get("categories").isJsonArray()) {
                    JsonArray categoryArray = root.getAsJsonArray("categories");
                    for (JsonElement element : categoryArray) {
                        if (element != null && element.isJsonPrimitive()) {
                            loadedCategories.add(element.getAsString());
                        }
                    }
                }
                if (root.has("rules") && root.get("rules").isJsonArray()) {
                    Type listType = new TypeToken<ArrayList<AutoEscapeRule>>() {
                    }.getType();
                    loadedRules = GSON.fromJson(root.get("rules"), listType);
                }
            } else if (parsed != null && parsed.isJsonArray()) {
                Type listType = new TypeToken<ArrayList<AutoEscapeRule>>() {
                }.getType();
                loadedRules = GSON.fromJson(parsed, listType);
            }

            categories.addAll(loadedCategories);
            if (loadedRules != null) {
                for (AutoEscapeRule rule : loadedRules) {
                    if (rule == null) {
                        continue;
                    }
                    rule.normalize();
                    rule.resetRuntimeState();
                    rules.add(rule);
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载自动逃离规则失败", e);
            rules.clear();
            categories.clear();
        }

        ensureCategoriesSynced();
        resetRuntimeState();
    }

    public static synchronized void saveConfig() {
        try {
            ensureCategoriesSynced();

            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("masterEnabled", masterEnabled);
            root.add("categories", GSON.toJsonTree(new ArrayList<>(categories)));
            root.add("rules", GSON.toJsonTree(snapshotRules()));

            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存自动逃离规则失败", e);
        }
    }

    public static synchronized List<AutoEscapeRule> getRulesSnapshot() {
        return new ArrayList<>(rules);
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureCategoriesSynced();
        return new ArrayList<>(categories);
    }

    public static synchronized boolean isMasterEnabled() {
        return masterEnabled;
    }

    public static synchronized void setMasterEnabled(boolean enabled) {
        masterEnabled = enabled;
        if (!enabled) {
            resetRuntimeState();
        }
        saveConfig();
        notifyPlayer(TextFormatting.AQUA + "[自动逃离] "
                + (enabled ? TextFormatting.GREEN + "总开关已开启。" : TextFormatting.RED + "总开关已关闭。"));
    }

    public static synchronized void replaceCategoryOrder(List<String> orderedCategories) {
        ensureCategoriesSynced();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (orderedCategories != null) {
            for (String category : orderedCategories) {
                normalized.add(normalizeCategory(category));
            }
        }
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        categories.clear();
        categories.addAll(normalized);
        saveConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        categories.add(normalized);
        saveConfig();
        return true;
    }

    public static synchronized boolean renameCategory(String oldCategory, String newCategory) {
        String normalizedOld = normalizeCategory(oldCategory);
        String normalizedNew = normalizeCategory(newCategory);
        ensureCategoriesSynced();

        if (normalizedOld.equalsIgnoreCase(normalizedNew)) {
            return true;
        }
        if (containsCategoryIgnoreCase(normalizedNew)) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (AutoEscapeRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalizedOld)) {
                rule.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static synchronized boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (AutoEscapeRule rule : rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                rule.category = CATEGORY_DEFAULT;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveConfig();
        return true;
    }

    public static synchronized void replaceAllRules(List<AutoEscapeRule> newRules) {
        rules.clear();
        if (newRules != null) {
            for (AutoEscapeRule rule : newRules) {
                if (rule == null) {
                    continue;
                }
                rule.normalize();
                rule.resetRuntimeState();
                rules.add(rule);
            }
        }
        ensureCategoriesSynced();
        saveConfig();
        resetRuntimeState();
    }

    public static boolean isEmergencyLockActive() {
        return runtimeState != RuntimeState.IDLE;
    }

    public static boolean isEscapeSequenceRunning() {
        return runtimeState == RuntimeState.ESCAPING;
    }

    public static void resetRuntimeState() {
        runtimeState = RuntimeState.IDLE;
        activeRule = null;
        activeEscapeSequenceName = "";
        pendingRestartSequenceName = "";
        restartExecuteAtMs = 0L;
        lastNotifyAtMs = 0L;
        for (AutoEscapeRule rule : snapshotRules()) {
            rule.resetRuntimeState();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("auto_escape")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("auto_escape");
        try {
        if (event.phase != TickEvent.Phase.START || mc.player == null || mc.world == null || event.player != mc.player) {
            return;
        }

        if (!isMasterEnabled()) {
            if (runtimeState != RuntimeState.IDLE) {
                resetRuntimeState();
            }
            return;
        }

        List<AutoEscapeRule> snapshot = snapshotRules();
        AutoEscapeRule candidateRule = null;
        boolean anyThreatPresent = false;

        for (AutoEscapeRule rule : snapshot) {
            if (rule == null) {
                continue;
            }
            rule.normalize();
            boolean matched = rule.enabled && hasMatchingThreatNearby(mc.player, rule);
            if (matched) {
                anyThreatPresent = true;
                if (candidateRule == null && !rule.triggerLatched && hasValidEscapeSequence(rule)) {
                    candidateRule = rule;
                }
                rule.triggerLatched = true;
            } else {
                rule.triggerLatched = false;
            }
        }

        if (runtimeState == RuntimeState.ESCAPING) {
            if (!PathSequenceEventListener.instance.isTracking()) {
                onEscapeSequenceCompleted();
            }
            return;
        }

        if (runtimeState == RuntimeState.RESTARTING) {
            if (!PathSequenceEventListener.instance.isTracking()) {
                onRestartSequenceCompleted();
            }
            return;
        }

        if (runtimeState == RuntimeState.WAITING_RESTART && shouldIgnoreTargetsUntilRestartComplete()) {
            if (pendingRestartSequenceName == null || pendingRestartSequenceName.trim().isEmpty()) {
                resetRuntimeState();
                return;
            }

            if (System.currentTimeMillis() >= restartExecuteAtMs) {
                runPendingRestartSequence();
            }
            return;
        }

        if (candidateRule != null) {
            triggerEscape(candidateRule);
            return;
        }

        if (runtimeState == RuntimeState.WAITING_RESTART) {
            if (pendingRestartSequenceName == null || pendingRestartSequenceName.trim().isEmpty()) {
                resetRuntimeState();
                return;
            }

            if (System.currentTimeMillis() >= restartExecuteAtMs && !anyThreatPresent) {
                runPendingRestartSequence();
            }
        }
        } finally {
            timer.stop();
        }
    }

    private static void onEscapeSequenceCompleted() {
        if (activeRule == null) {
            resetRuntimeState();
            return;
        }

        if (activeRule.restartEnabled
                && activeRule.restartSequenceName != null
                && !activeRule.restartSequenceName.trim().isEmpty()) {
            pendingRestartSequenceName = activeRule.restartSequenceName.trim();
            restartExecuteAtMs = System.currentTimeMillis() + Math.max(0, activeRule.restartDelaySeconds) * 1000L;
            runtimeState = RuntimeState.WAITING_RESTART;
            notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.GREEN
                    + "逃离序列已完成，将在 "
                    + TextFormatting.YELLOW + Math.max(0, activeRule.restartDelaySeconds)
                    + TextFormatting.GREEN + " 秒后执行后续序列。");
            return;
        }

        notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.GREEN + "逃离序列已完成。");
        resetRuntimeState();
    }

    private static void onRestartSequenceCompleted() {
        resetRuntimeState();
    }

    private static void runPendingRestartSequence() {
        String sequenceName = pendingRestartSequenceName == null ? "" : pendingRestartSequenceName.trim();
        if (sequenceName.isEmpty()) {
            resetRuntimeState();
            return;
        }

        if (!PathSequenceManager.hasSequence(sequenceName)) {
            notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.RED
                    + "后续序列不存在，无法执行: " + TextFormatting.WHITE + sequenceName);
            resetRuntimeState();
            return;
        }

        stopCurrentSequenceImmediately();
        PathSequenceManager.runPathSequenceOnce(sequenceName);
        notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.GREEN
                + "开始执行后续序列: " + TextFormatting.WHITE + sequenceName);
        if (shouldIgnoreTargetsUntilRestartComplete()) {
            pendingRestartSequenceName = "";
            restartExecuteAtMs = 0L;
            runtimeState = RuntimeState.RESTARTING;
            return;
        }
        resetRuntimeState();
    }

    private static void triggerEscape(AutoEscapeRule rule) {
        if (rule == null) {
            return;
        }

        String sequenceName = rule.escapeSequenceName == null ? "" : rule.escapeSequenceName.trim();
        if (sequenceName.isEmpty()) {
            return;
        }
        if (!PathSequenceManager.hasSequence(sequenceName)) {
            notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.RED
                    + "逃离序列不存在，无法执行: " + TextFormatting.WHITE + sequenceName);
            return;
        }

        activeRule = rule;
        activeEscapeSequenceName = sequenceName;
        pendingRestartSequenceName = "";
        restartExecuteAtMs = 0L;
        runtimeState = RuntimeState.ESCAPING;

        stopCurrentSequenceImmediately();
        PathSequenceManager.runPathSequenceOnce(sequenceName);

        notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.YELLOW
                + "检测到附近目标，开始执行逃离序列: " + TextFormatting.WHITE + sequenceName);
    }

    private static void stopCurrentSequenceImmediately() {
        EmbeddedNavigationHandler.INSTANCE.stop();
        PathSequenceManager.clearRunSequenceCallStack();
        if (PathSequenceEventListener.instance.isTracking()) {
            PathSequenceEventListener.instance.stopTracking();
        }
        GuiInventory.isLooping = false;
    }

    private static boolean hasMatchingThreatNearby(EntityPlayerSP player, AutoEscapeRule rule) {
        if (player == null || mc.world == null || rule == null) {
            return false;
        }
        if (isBlockedByAreaBlacklist(player, rule)) {
            return false;
        }

        double range = rule.detectionRange <= 0 ? AutoEscapeRule.DEFAULT_DETECTION_RANGE : rule.detectionRange;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(range);

        List<Entity> entities = mc.world.getEntitiesWithinAABB(Entity.class, box);
        for (Entity entity : entities) {
            if (entity == null || entity == player) {
                continue;
            }
            if (!isEntityAliveForDetection(entity)) {
                continue;
            }
            if (player.getDistanceSq(entity) > range * range) {
                continue;
            }
            if (!matchesEntityType(entity, rule)) {
                continue;
            }
            if (!matchesPlayerGameModeFilter(entity, rule)) {
                continue;
            }
            if (!matchesNameFilters(entity, rule)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static String getCurrentAreaKey() {
        if (mc == null || mc.player == null) {
            return "";
        }
        return buildAreaKey(mc.player.dimension, mc.player.chunkCoordX, mc.player.chunkCoordZ);
    }

    private static boolean isBlockedByAreaBlacklist(EntityPlayerSP player, AutoEscapeRule rule) {
        if (player == null
                || rule == null
                || !rule.enableAreaBlacklist
                || rule.areaBlacklist == null
                || rule.areaBlacklist.isEmpty()) {
            return false;
        }

        int currentDimension = player.dimension;
        int currentChunkX = player.chunkCoordX;
        int currentChunkZ = player.chunkCoordZ;

        for (AutoEscapeRule.AreaBlacklistEntry entry : rule.areaBlacklist) {
            int[] parsed = parseAreaKey(entry == null ? "" : entry.areaKey);
            if (parsed == null || parsed[0] != currentDimension) {
                continue;
            }
            int radius = entry == null ? 0 : Math.max(0, entry.chunkRadius);
            if (Math.abs(currentChunkX - parsed[1]) <= radius
                    && Math.abs(currentChunkZ - parsed[2]) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static int[] parseAreaKey(String areaKey) {
        String normalized = AutoEscapeRule.normalizeAreaKey(areaKey);
        if (normalized.isEmpty()) {
            return null;
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= normalized.length() - 1) {
            return null;
        }

        int commaIndex = normalized.indexOf(',', colonIndex + 1);
        if (commaIndex <= colonIndex + 1 || commaIndex >= normalized.length() - 1) {
            return null;
        }

        try {
            int dimension = Integer.parseInt(normalized.substring(0, colonIndex));
            int chunkX = Integer.parseInt(normalized.substring(colonIndex + 1, commaIndex));
            int chunkZ = Integer.parseInt(normalized.substring(commaIndex + 1));
            return new int[] { dimension, chunkX, chunkZ };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String buildAreaKey(int dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private static boolean isEntityAliveForDetection(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            return ((EntityLivingBase) entity).isEntityAlive();
        }
        return !entity.isDead;
    }

    private static boolean matchesNameFilters(Entity entity, AutoEscapeRule rule) {
        String name = entity == null || entity.getName() == null ? "" : entity.getName().trim();
        String lowered = name.toLowerCase(Locale.ROOT);

        if (rule.enableNameWhitelist && rule.nameWhitelist != null && !rule.nameWhitelist.isEmpty()) {
            boolean matchedWhitelist = false;
            for (String keyword : rule.nameWhitelist) {
                if (keyword != null && !keyword.trim().isEmpty()
                        && lowered.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    matchedWhitelist = true;
                    break;
                }
            }
            if (!matchedWhitelist) {
                return false;
            }
        }

        if (rule.enableNameBlacklist && rule.nameBlacklist != null && !rule.nameBlacklist.isEmpty()) {
            for (String keyword : rule.nameBlacklist) {
                if (keyword != null && !keyword.trim().isEmpty()
                        && lowered.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean matchesPlayerGameModeFilter(Entity entity, AutoEscapeRule rule) {
        if (rule == null || !rule.enablePlayerGameModeFilter) {
            return true;
        }
        if (!(entity instanceof EntityPlayer)) {
            return true;
        }

        String filter = AutoEscapeRule.normalizePlayerGameModeFilter(rule.playerGameModeFilter);
        if (AutoEscapeRule.PLAYER_GAME_MODE_ALL.equals(filter)) {
            return true;
        }

        String detected = getPlayerGameModeToken((EntityPlayer) entity);
        return filter.equals(detected);
    }

    private static String getPlayerGameModeToken(EntityPlayer player) {
        GameType gameType = getNetworkPlayerGameType(player);
        if (gameType == GameType.CREATIVE) {
            return AutoEscapeRule.PLAYER_GAME_MODE_CREATIVE;
        }
        if (gameType == GameType.SPECTATOR) {
            return AutoEscapeRule.PLAYER_GAME_MODE_SPECTATOR;
        }
        if (gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE) {
            return AutoEscapeRule.PLAYER_GAME_MODE_SURVIVAL;
        }
        return AutoEscapeRule.PLAYER_GAME_MODE_UNKNOWN;
    }

    private static GameType getNetworkPlayerGameType(EntityPlayer player) {
        if (player == null || mc.getConnection() == null) {
            return null;
        }
        try {
            NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(player.getUniqueID());
            if (info == null) {
                return null;
            }
            return info.getGameType();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean matchesEntityType(Entity entity, AutoEscapeRule rule) {
        if (entity == null) {
            return false;
        }

        List<String> types = rule.entityTypes;
        if (types == null || types.isEmpty()) {
            return entity instanceof EntityLivingBase;
        }

        for (String rawType : types) {
            if (matchesEntityTypeToken(entity, rawType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesEntityTypeToken(Entity entity, String rawType) {
        String token = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty()) {
            return false;
        }

        switch (token) {
            case "任意":
            case "any":
                return true;
            case "生物":
            case "living":
                return entity instanceof EntityLivingBase;
            case "玩家":
            case "player":
                return entity instanceof EntityPlayer;
            case "怪物":
            case "monster":
            case "mob":
            case "hostile":
                return entity instanceof IMob;
            case "中立":
            case "中立生物":
            case "neutral":
                return entity instanceof EntityLivingBase
                        && !(entity instanceof EntityPlayer)
                        && !(entity instanceof IMob);
            case "动物":
            case "animal":
            case "passive":
                return entity instanceof EntityAnimal || entity instanceof EntityAgeable;
            case "水生":
            case "water":
                return entity instanceof EntityWaterMob;
            case "环境":
            case "ambient":
                return entity instanceof EntityAmbientCreature;
            case "村民":
            case "villager":
            case "npc":
                return entity instanceof EntityVillager;
            case "傀儡":
            case "golem":
                return entity instanceof EntityGolem;
            case "驯服":
            case "宠物":
            case "tameable":
                return entity instanceof EntityTameable;
            case "首领":
            case "boss":
                return entity instanceof EntityLivingBase
                        && !((EntityLivingBase) entity).isNonBoss();
            default:
                return false;
        }
    }

    private static boolean hasValidEscapeSequence(AutoEscapeRule rule) {
        return rule != null
                && rule.escapeSequenceName != null
                && !rule.escapeSequenceName.trim().isEmpty()
                && PathSequenceManager.hasSequence(rule.escapeSequenceName.trim());
    }

    private static boolean shouldIgnoreTargetsUntilRestartComplete() {
        return activeRule != null
                && activeRule.restartEnabled
                && activeRule.ignoreTargetsUntilRestartComplete;
    }

    private static void notifyPlayer(String message) {
        long now = System.currentTimeMillis();
        if (now - lastNotifyAtMs < 300L) {
            return;
        }
        lastNotifyAtMs = now;
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(message));
        }
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("auto_escape_rules.json");
    }

    private static synchronized List<AutoEscapeRule> snapshotRules() {
        return new ArrayList<>(rules);
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : categories) {
            normalized.add(normalizeCategory(category));
        }
        for (AutoEscapeRule rule : rules) {
            if (rule == null) {
                continue;
            }
            rule.normalize();
            rule.category = normalizeCategory(rule.category);
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        categories.clear();
        categories.addAll(normalized);
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static boolean containsCategoryIgnoreCase(String category) {
        for (String existing : categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < categories.size(); i++) {
            if (normalizeCategory(categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                categories.remove(i);
                return true;
            }
        }
        return false;
    }
}
