package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.PerformanceMonitor;
import com.mythos.mythosScriptMod.gui.GuiInventory;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.system.AutoEscapeRule;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.mythosScriptMod;
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

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        private final List<AutoEscapeRule> rules = new CopyOnWriteArrayList<>();
        private final List<String> categories = new CopyOnWriteArrayList<>();
    }

    /** 当前线程所属会话的配置；bot 线程取自己的，客户端线程取挂载中的会话。 */
    public static OwnerConfig cfg() {
        String owner = com.mythos.mythosScriptMod.system.ProfileManager.currentConfigOwner();
        OwnerConfig config = CONFIGS.get(owner);
        return config != null ? config : loadOwnerConfig(owner);
    }

    // 不能用 computeIfAbsent：读盘过程中会反复调用 cfg()，ConcurrentHashMap 不允许递归更新。
    private static OwnerConfig loadOwnerConfig(String owner) {
        synchronized (com.mythos.mythosScriptMod.system.ProfileManager.CONFIG_LOAD_LOCK) {
            OwnerConfig existing = CONFIGS.get(owner);
            if (existing != null) {
                return existing;
            }
            OwnerConfig config = new OwnerConfig();
            CONFIGS.put(owner, config);
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) AutoEscapeHandler::loadConfig);
            return config;
        }
    }

    public static final AutoEscapeHandler INSTANCE = new AutoEscapeHandler();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY_DEFAULT = "默认";

    private enum RuntimeState {
        IDLE,
        ESCAPING,
        WAITING_RESTART,
        RESTARTING
    }

    // --- 运行时状态（per-session，按 NexusSessions.currentTickOwner() 键控）---
    private static final java.util.concurrent.ConcurrentHashMap<String, SessionRuntime> STATES =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static SessionRuntime rt() {
        return STATES.computeIfAbsent(
            com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
            k -> new SessionRuntime());
    }

    private static final class SessionRuntime {
        boolean masterEnabled = true;
        RuntimeState runtimeState = RuntimeState.IDLE;
        AutoEscapeRule activeRule = null;
        String activeEscapeSequenceName = "";
        String pendingRestartSequenceName = "";
        long restartExecuteAtMs = 0L;
        long lastNotifyAtMs = 0L;
    }

    private AutoEscapeHandler() {
    }

    public static synchronized void loadConfig() {
        cfg().rules.clear();
        cfg().categories.clear();
        rt().masterEnabled = true;

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
                Path runtimeFile = getRuntimeConfigFile();
                if (Files.exists(runtimeFile)) {
                    try (BufferedReader rr = Files.newBufferedReader(runtimeFile, StandardCharsets.UTF_8)) {
                        JsonElement rp = new JsonParser().parse(rr);
                        if (rp != null && rp.isJsonObject()) {
                            JsonObject rj = rp.getAsJsonObject();
                            if (rj.has("masterEnabled") && rj.get("masterEnabled").isJsonPrimitive()) {
                                rt().masterEnabled = rj.get("masterEnabled").getAsBoolean();
                            }
                        }
                    }
                } else if (root.has("masterEnabled") && root.get("masterEnabled").isJsonPrimitive()) {
                    rt().masterEnabled = root.get("masterEnabled").getAsBoolean();
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

            cfg().categories.addAll(loadedCategories);
            if (loadedRules != null) {
                for (AutoEscapeRule rule : loadedRules) {
                    if (rule == null) {
                        continue;
                    }
                    rule.normalize();
                    rule.resetRuntimeState();
                    cfg().rules.add(rule);
                }
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载自动逃离规则失败", e);
            cfg().rules.clear();
            cfg().categories.clear();
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
            root.add("categories", GSON.toJsonTree(new ArrayList<>(cfg().categories)));
            root.add("rules", GSON.toJsonTree(snapshotRules()));

            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            Path runtimeFile = getRuntimeConfigFile();
            Files.createDirectories(runtimeFile.getParent());
            JsonObject rjson = new JsonObject();
            rjson.addProperty("masterEnabled", rt().masterEnabled);
            try (BufferedWriter rw = Files.newBufferedWriter(runtimeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(rjson, rw);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存自动逃离规则失败", e);
        }
    }

    public static synchronized List<AutoEscapeRule> getRulesSnapshot() {
        return new ArrayList<>(cfg().rules);
    }

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureCategoriesSynced();
        return new ArrayList<>(cfg().categories);
    }

    public static boolean masterEnabled() { return rt().masterEnabled; }

    public static synchronized boolean isMasterEnabled() {
        return rt().masterEnabled;
    }

    public static synchronized void setMasterEnabled(boolean enabled) {
        rt().masterEnabled = enabled;
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
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
        saveConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        cfg().categories.add(normalized);
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
        for (int i = 0; i < cfg().categories.size(); i++) {
            if (normalizeCategory(cfg().categories.get(i)).equalsIgnoreCase(normalizedOld)) {
                cfg().categories.set(i, normalizedNew);
                changed = true;
                break;
            }
        }

        for (AutoEscapeRule rule : cfg().rules) {
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
        for (AutoEscapeRule rule : cfg().rules) {
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
        cfg().rules.clear();
        if (newRules != null) {
            for (AutoEscapeRule rule : newRules) {
                if (rule == null) {
                    continue;
                }
                rule.normalize();
                rule.resetRuntimeState();
                cfg().rules.add(rule);
            }
        }
        ensureCategoriesSynced();
        saveConfig();
        resetRuntimeState();
    }

    public static boolean isEmergencyLockActive() {
        return rt().runtimeState != RuntimeState.IDLE;
    }

    public static boolean isEscapeSequenceRunning() {
        return rt().runtimeState == RuntimeState.ESCAPING;
    }

    public static void resetRuntimeState() {
        SessionRuntime rt = rt();
        rt.runtimeState = RuntimeState.IDLE;
        rt.activeRule = null;
        rt.activeEscapeSequenceName = "";
        rt.pendingRestartSequenceName = "";
        rt.restartExecuteAtMs = 0L;
        rt.lastNotifyAtMs = 0L;
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
        if (event.phase != TickEvent.Phase.START || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null || event.player != com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer()) {
            return;
        }

        SessionRuntime rt = rt();

        if (!rt.masterEnabled) {
            if (rt.runtimeState != RuntimeState.IDLE) {
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
            boolean matched = rule.enabled && hasMatchingThreatNearby(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer(), rule);
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

        if (rt.runtimeState == RuntimeState.ESCAPING) {
            if (!PathSequenceEventListener.foregroundRunner().isTracking()) {
                onEscapeSequenceCompleted();
            }
            return;
        }

        if (rt.runtimeState == RuntimeState.RESTARTING) {
            if (!PathSequenceEventListener.foregroundRunner().isTracking()) {
                onRestartSequenceCompleted();
            }
            return;
        }

        if (rt.runtimeState == RuntimeState.WAITING_RESTART && shouldIgnoreTargetsUntilRestartComplete()) {
            if (rt.pendingRestartSequenceName == null || rt.pendingRestartSequenceName.trim().isEmpty()) {
                resetRuntimeState();
                return;
            }

            if (anyThreatPresent) {
                // 威胁仍在范围内：顺延重启计时，等目标离开后才开始倒计时
                rt.restartExecuteAtMs = System.currentTimeMillis()
                        + Math.max(0, rt.activeRule.restartDelaySeconds) * 1000L;
                return;
            }

            if (System.currentTimeMillis() >= rt.restartExecuteAtMs) {
                runPendingRestartSequence();
            }
            return;
        }

        if (candidateRule != null) {
            triggerEscape(candidateRule);
            return;
        }

        if (rt.runtimeState == RuntimeState.WAITING_RESTART) {
            if (rt.pendingRestartSequenceName == null || rt.pendingRestartSequenceName.trim().isEmpty()) {
                resetRuntimeState();
                return;
            }

            if (anyThreatPresent) {
                // 威胁仍在范围内：顺延重启计时，等目标离开后才开始倒计时
                rt.restartExecuteAtMs = System.currentTimeMillis()
                        + Math.max(0, rt.activeRule == null ? 0 : rt.activeRule.restartDelaySeconds) * 1000L;
                return;
            }

            if (System.currentTimeMillis() >= rt.restartExecuteAtMs) {
                runPendingRestartSequence();
            }
        }
        } finally {
            timer.stop();
        }
    }

    private static void onEscapeSequenceCompleted() {
        SessionRuntime rt = rt();
        if (rt.activeRule == null) {
            resetRuntimeState();
            return;
        }

        if (rt.activeRule.restartEnabled) {
            // Empty restart sequence means "restart the current (escape) sequence".
            String restartName = rt.activeRule.restartSequenceName == null
                    ? "" : rt.activeRule.restartSequenceName.trim();
            if (restartName.isEmpty() && rt.activeRule.escapeSequenceName != null) {
                restartName = rt.activeRule.escapeSequenceName.trim();
            }
            if (restartName.isEmpty()) {
                notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.GREEN + "逃离序列已完成。");
                resetRuntimeState();
                return;
            }
            rt.pendingRestartSequenceName = restartName;
            rt.restartExecuteAtMs = System.currentTimeMillis() + Math.max(0, rt.activeRule.restartDelaySeconds) * 1000L;
            rt.runtimeState = RuntimeState.WAITING_RESTART;
            notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.GREEN
                    + "逃离序列已完成，将在 "
                    + TextFormatting.YELLOW + Math.max(0, rt.activeRule.restartDelaySeconds)
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
        SessionRuntime rt = rt();
        String sequenceName = rt.pendingRestartSequenceName == null ? "" : rt.pendingRestartSequenceName.trim();
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
            rt.pendingRestartSequenceName = "";
            rt.restartExecuteAtMs = 0L;
            rt.runtimeState = RuntimeState.RESTARTING;
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

        SessionRuntime rt = rt();
        rt.activeRule = rule;
        rt.activeEscapeSequenceName = sequenceName;
        rt.pendingRestartSequenceName = "";
        rt.restartExecuteAtMs = 0L;
        rt.runtimeState = RuntimeState.ESCAPING;

        stopCurrentSequenceImmediately();
        PathSequenceManager.runPathSequenceOnce(sequenceName);

        notifyPlayer(TextFormatting.AQUA + "[自动逃离] " + TextFormatting.YELLOW
                + "检测到附近目标，开始执行逃离序列: " + TextFormatting.WHITE + sequenceName);
    }

    private static void stopCurrentSequenceImmediately() {
        EmbeddedNavigationHandler.INSTANCE.stop();
        PathSequenceManager.clearRunSequenceCallStack();
        if (PathSequenceEventListener.foregroundRunner().isTracking()) {
            PathSequenceEventListener.foregroundRunner().stopTracking();
        }
        GuiInventory.isLooping = false;
    }

    private static boolean hasMatchingThreatNearby(EntityPlayerSP player, AutoEscapeRule rule) {
        if (player == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld() == null || rule == null) {
            return false;
        }
        if (isBlockedByAreaBlacklist(player, rule)) {
            return false;
        }

        double range = rule.detectionRange <= 0 ? AutoEscapeRule.DEFAULT_DETECTION_RANGE : rule.detectionRange;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(range);

        List<Entity> entities = com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld().getEntitiesWithinAABB(Entity.class, box);
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
        if (mc == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() == null) {
            return "";
        }
        return buildAreaKey(com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().dimension, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().chunkCoordX, com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().chunkCoordZ);
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
        double currentX = player.posX;
        double currentZ = player.posZ;

        for (AutoEscapeRule.AreaBlacklistEntry entry : rule.areaBlacklist) {
            if (entry != null && entry.contains(currentDimension, currentX, currentZ)) {
                return true;
            }
        }
        return false;
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
                return ModUtils.isRealPlayer(entity);
            case "npc":
                return ModUtils.isNpcPlayer(entity);
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
        SessionRuntime rt = rt();
        return rt.activeRule != null
                && rt.activeRule.restartEnabled
                && rt.activeRule.ignoreTargetsUntilRestartComplete;
    }

    private static void notifyPlayer(String message) {
        SessionRuntime rt = rt();
        long now = System.currentTimeMillis();
        if (now - rt.lastNotifyAtMs < 300L) {
            return;
        }
        rt.lastNotifyAtMs = now;
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() != null) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(message));
        }
    }

    private static Path getConfigFile() {
        return ProfileManager.currentConfigFile("auto_escape_rules.json");
    }

    private static Path getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("auto_escape_rules.json");
    }

    private static synchronized List<AutoEscapeRule> snapshotRules() {
        return new ArrayList<>(cfg().rules);
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        for (AutoEscapeRule rule : cfg().rules) {
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
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private static boolean containsCategoryIgnoreCase(String category) {
        for (String existing : cfg().categories) {
            if (normalizeCategory(existing).equalsIgnoreCase(normalizeCategory(category))) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeCategoryIgnoreCase(String category) {
        for (int i = 0; i < cfg().categories.size(); i++) {
            if (normalizeCategory(cfg().categories.get(i)).equalsIgnoreCase(normalizeCategory(category))) {
                cfg().categories.remove(i);
                return true;
            }
        }
        return false;
    }
}
