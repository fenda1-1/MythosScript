// [V4.8 算法升级] 引入路径清空检查，杜绝穿墙路径
package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.system.AutoFollowRule;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AutoFollowHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public final List<AutoFollowRule> rules = new CopyOnWriteArrayList<>();
        private final List<String> categories = new CopyOnWriteArrayList<>();
        public boolean antiStuckEnabled = false;
        public boolean avoidVinesProactively = false;
        public double vineAvoidanceDistance = 2.0;
        public boolean timeoutReloadEnabled = false;
        public int timeoutReloadSeconds = 60;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) AutoFollowHandler::loadFollowConfig);
            return config;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CATEGORY_DEFAULT = "默认";
    private static final String LEGACY_DEFAULT_CATEGORY_KEY = "gui.modern.autofollow.u046";
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();


    private static final int STUCK_CHECK_INTERVAL = 40;
    private static final double STUCK_DISTANCE_THRESHOLD_SQ = 0.01;

    private static final double AVOIDANCE_SIDESTEP_SAFETY_MARGIN = 1.5;
    private static final double AVOIDANCE_FORWARD_SAFETY_MARGIN = 1.5;
    private static final double ESCAPE_DISTANCE = 3.5;
    private static final long AVOIDANCE_COOLDOWN_MS = 2500;

    public static final double DETECTION_RADIUS = 5.0;
    private static final double HUNT_GOTO_MOVE_THRESHOLD_SQ = 1.0; // 目标移动超过1格时重发goto
    private static final int HUNT_GOTO_INTERVAL_TICKS = 20; // 增加到20 ticks (1秒) 间隔
    private static final double HUNT_FIXED_DISTANCE_TOLERANCE = 0.35D;
    private static final int MONSTER_SCORE_SCAN_INTERVAL_TICKS = 4;
    private static final int HUNT_DESTINATION_HORIZONTAL_SEARCH_RADIUS = 2;

    private static final int COMMAND_DELAY_TICKS = 3;
    private static final long OUT_OF_BOUNDS_NOTIFY_COOLDOWN_MS = 2000L;

    // 跟随/狩猎/巡逻三个子状态机的运行时状态全部按会话装箱：
    // 多 bot 并行时各自持有独立 RuntimeState，不再共享 static 字段。
    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static RuntimeState rtFor(String owner) {
        return STATES.computeIfAbsent(owner == null ? "" : owner, k -> new RuntimeState());
    }

    private static final class RuntimeState {
        private AutoFollowRule activeRule = null;
        private String lastQuickToggleRuleName = "";
        private int timeoutTicksCounter = 0;
        private Vec3d lastTimeoutCheckPosition = null;
        private int stuckCheckCounter = 0;
        private Vec3d lastStuckCheckPosition = null;
        private Vec3d lastTickPlayerPos = null;
        private long lastAvoidanceTime = 0;
        private boolean isMovingToPoint = false;
        private Vec3d escapeDestination = null;
        private volatile List<ScoredMonsterInfo> lastScoredMonsters = Collections.emptyList();
        private int lastMonsterScoreScanTick = -99999;
        private Entity huntTargetEntity = null;
        private int lastHuntGotoTick = -99999;
        private int lastHuntTargetEntityId = Integer.MIN_VALUE;
        private Vec3d lastHuntTargetPos = null;
        private boolean huntMovementStopped = false;
        private boolean centerReturnCommandIssued = false;
        private int currentReturnPointIndex = 0;
        private boolean patrolWaitingAtPoint = false;
        private long patrolWaitStartMs = 0L;
        private Vec3d lastPatrolProgressPosition = null;
        private long patrolStoppedSinceMs = 0L;
        private Point randomPatrolPoint = null;
        private Point lastPlayerPosition = null;
        private boolean isSuspendedDueToDistance = false;
        private boolean returningToCenterFromOutOfBounds = false;
        private boolean outOfRecoveryRangeSequenceTriggered = false;
        private long lastOutOfBoundsNotifyMs = 0L;
    }

    static {
        loadFollowConfig();
    }

    public static class Point {
        public double x;
        /**
         * Optional fixed Y coordinate. Older rule files only contain X/Z and
         * intentionally keep this value unset so they retain their old behavior.
         */
        public Double y;
        public double z;

        public Point(double x, double z) {
            this(x, null, z);
        }

        public Point(double x, double y, double z) {
            this(x, Double.valueOf(y), z);
        }

        public Point(double x, Double y, double z) {
            this.x = x;
            this.y = isFinite(y) ? y : null;
            this.z = z;
        }

        public boolean hasY() {
            return isFinite(y);
        }

        private static boolean isFinite(Double value) {
            return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    public static class ScoredMonsterInfo {
        public int entityId;
        public String name;
        public double totalScore;
        public double distanceScore;
        public double visibilityScore;
        public double reachabilityScore;
        public double verticalScore;
        public double lockBonusScore;
        public double distance;
        public double verticalDiff;
        public boolean visible;
        public boolean reachable;
    }

    private static Path getConfigFile() {
        return ProfileManager.currentConfigFile("autofollow_rules.json");
    }

    public static synchronized void loadFollowConfig() {
        cfg().rules.clear();
        cfg().categories.clear();
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);

                cfg().antiStuckEnabled = false;
                cfg().avoidVinesProactively = false;
                cfg().vineAvoidanceDistance = 2.0;
                cfg().timeoutReloadEnabled = false;
                cfg().timeoutReloadSeconds = 60;

                JsonArray ruleArray = null;

                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject root = parsed.getAsJsonObject();
                    cfg().antiStuckEnabled = root.has("antiStuckEnabled") ? root.get("antiStuckEnabled").getAsBoolean()
                            : false;
                    cfg().avoidVinesProactively = root.has("avoidVinesProactively")
                            ? root.get("avoidVinesProactively").getAsBoolean()
                            : false;
                    cfg().vineAvoidanceDistance = root.has("vineAvoidanceDistance")
                            ? root.get("vineAvoidanceDistance").getAsDouble()
                            : 2.0;
                    cfg().timeoutReloadEnabled = root.has("timeoutReloadEnabled")
                            ? root.get("timeoutReloadEnabled").getAsBoolean()
                            : false;
                    cfg().timeoutReloadSeconds = root.has("timeoutReloadSeconds")
                            ? root.get("timeoutReloadSeconds").getAsInt()
                            : 60;

                    if (root.has("categories") && root.get("categories").isJsonArray()) {
                        JsonArray categoryArray = root.getAsJsonArray("categories");
                        for (JsonElement element : categoryArray) {
                            if (element != null && element.isJsonPrimitive()) {
                                cfg().categories.add(element.getAsString());
                            }
                        }
                    }
                    if (root.has("rules") && root.get("rules").isJsonArray()) {
                        ruleArray = root.getAsJsonArray("rules");
                    }
                } else if (parsed != null && parsed.isJsonArray()) {
                    // 兼容旧版：文件根节点直接就是规则数组
                    ruleArray = parsed.getAsJsonArray();
                }

                if (ruleArray != null) {
                    Type listType = new TypeToken<List<AutoFollowRule>>() {
                    }.getType();
                    List<AutoFollowRule> loadedRules = GSON.fromJson(ruleArray, listType);
                    if (loadedRules != null) {
                        for (AutoFollowRule rule : loadedRules) {
                            if (rule == null) {
                                continue;
                            }
                            if (rule.point1 == null) {
                                rule.point1 = new Point(0, 0);
                            }
                            if (rule.point2 == null) {
                                rule.point2 = new Point(0, 0);
                            }
                            if (rule.point3 == null) {
                                rule.point3 = new Point(0, 0);
                            }
                            if (rule.name == null || rule.name.trim().isEmpty()) {
                                rule.name = "规则";
                            }
                            rule.category = normalizeCategory(rule.category);
                            rule.updateBounds();
                            rule.ensureReturnPoints();
                            cfg().rules.add(rule);
                        }
                    }
                }
            } catch (Exception e) {
                mythosScriptMod.LOGGER.error("加载自动追怪规则失败", e);
            }
        }
        ensureCategoriesSynced();
        rtFor("").activeRule = null;
        for (AutoFollowRule rule : snapshotRules()) {
            rule.updateBounds();
            rule.ensureReturnPoints();
            if (rule.enabled) {
                rtFor("").activeRule = rule;
                rtFor("").lastQuickToggleRuleName = rule.name == null ? "" : rule.name.trim();
                break;
            }
        }
        resetPatrolState();
    }

    public static synchronized void saveFollowConfig() {
        try {
            ensureCategoriesSynced();

            Path configFile = getConfigFile();
            Files.createDirectories(configFile.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("antiStuckEnabled", cfg().antiStuckEnabled);
            root.addProperty("avoidVinesProactively", cfg().avoidVinesProactively);
            root.addProperty("vineAvoidanceDistance", cfg().vineAvoidanceDistance);
            root.addProperty("timeoutReloadEnabled", cfg().timeoutReloadEnabled);
            root.addProperty("timeoutReloadSeconds", cfg().timeoutReloadSeconds);
            root.add("categories", GSON.toJsonTree(new ArrayList<>(cfg().categories)));
            root.add("rules", GSON.toJsonTree(snapshotRules()));

            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存自动追怪规则失败", e);
        }
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() || LEGACY_DEFAULT_CATEGORY_KEY.equalsIgnoreCase(normalized)
                ? CATEGORY_DEFAULT : normalized;
    }

    private static void ensureCategoriesSynced() {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String category : cfg().categories) {
            normalized.add(normalizeCategory(category));
        }
        for (AutoFollowRule rule : cfg().rules) {
            if (rule == null) {
                continue;
            }
            rule.category = normalizeCategory(rule.category);
            rule.updateBounds();
            rule.ensureReturnPoints();
            normalized.add(rule.category);
        }
        if (normalized.isEmpty()) {
            normalized.add(CATEGORY_DEFAULT);
        }
        cfg().categories.clear();
        cfg().categories.addAll(normalized);
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

    public static synchronized List<String> getCategoriesSnapshot() {
        ensureCategoriesSynced();
        return new ArrayList<>(cfg().categories);
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
        saveFollowConfig();
    }

    public static synchronized boolean addCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();
        if (containsCategoryIgnoreCase(normalized)) {
            return false;
        }
        cfg().categories.add(normalized);
        saveFollowConfig();
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

        for (AutoFollowRule rule : cfg().rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalizedOld)) {
                rule.category = normalizedNew;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveFollowConfig();
        return true;
    }

    public static synchronized boolean deleteCategory(String category) {
        String normalized = normalizeCategory(category);
        ensureCategoriesSynced();

        boolean changed = removeCategoryIgnoreCase(normalized);
        for (AutoFollowRule rule : cfg().rules) {
            if (rule != null && normalizeCategory(rule.category).equalsIgnoreCase(normalized)) {
                rule.category = CATEGORY_DEFAULT;
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }

        ensureCategoriesSynced();
        saveFollowConfig();
        return true;
    }

    public static AutoFollowRule getActiveRule() {
        return rt().activeRule;
    }

    public static boolean hasAnyRuleConfigured() {
        return !cfg().rules.isEmpty();
    }

    public static AutoFollowRule toggleEnabledFromQuickSwitch() {
        if (rt().activeRule != null && rt().activeRule.enabled) {
            rt().lastQuickToggleRuleName = rt().activeRule.name == null ? "" : rt().activeRule.name.trim();
            setActiveRule(null);
            return null;
        }

        AutoFollowRule preferred = findRuleByName(rt().lastQuickToggleRuleName);
        if (preferred == null) {
            List<AutoFollowRule> snapshot = snapshotRules();
            preferred = snapshot.isEmpty() ? null : snapshot.get(0);
        }
        if (preferred == null) {
            return null;
        }
        setActiveRule(preferred);
        return preferred;
    }

    public static void setEnabled(boolean enabled) {
        boolean currentlyEnabled = getActiveRule() != null;
        if (enabled == currentlyEnabled) {
            return;
        }
        if (enabled && !hasAnyRuleConfigured()) {
            return;
        }
        toggleEnabledFromQuickSwitch();
    }

    public static void toggleEnabledFromHotkey() {
        if (getActiveRule() != null) {
            toggleEnabledFromQuickSwitch();
            sendHotkeyMessage(I18n.format("gui.modern.inv.u084"));
            return;
        }
        if (!hasAnyRuleConfigured()) {
            sendHotkeyMessage(I18n.format("gui.modern.inv.u085"));
            return;
        }
        AutoFollowRule activatedRule = toggleEnabledFromQuickSwitch();
        String extra = activatedRule != null && activatedRule.name != null && !activatedRule.name.trim().isEmpty()
                ? I18n.format("gui.modern.inv.fmt.rule", activatedRule.name.trim())
                : "";
        sendHotkeyMessage(I18n.format("gui.modern.inv.u086") + extra);
    }

    private static void sendHotkeyMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(message));
        }
    }

    public static boolean hasActiveLockChaseRestriction() {
        return rt().activeRule != null && rt().activeRule.enabled;
    }

    public static boolean isPositionWithinActiveLockChaseBounds(double x, double z) {
        if (!hasActiveLockChaseRestriction()) {
            return true;
        }
        rt().activeRule.updateBounds();
        return isPositionWithinLockChaseBounds(rt().activeRule, x, z);
    }

    private static boolean isPositionWithinLockChaseBounds(AutoFollowRule rule, double x, double z) {
        if (rule == null) {
            return false;
        }

        double dx = 0.0;
        if (x < rule.minX) {
            dx = rule.minX - x;
        } else if (x > rule.maxX) {
            dx = x - rule.maxX;
        }

        double dz = 0.0;
        if (z < rule.minZ) {
            dz = rule.minZ - z;
        } else if (z > rule.maxZ) {
            dz = z - rule.maxZ;
        }

        double outDistance = Math.sqrt(dx * dx + dz * dz);
        double allowed = rule.lockChaseOutOfBoundsDistance > 0
                ? rule.lockChaseOutOfBoundsDistance
                : AutoFollowRule.DEFAULT_LOCK_CHASE_OUT_OF_BOUNDS_DISTANCE;
        return outDistance <= allowed;
    }

    public static synchronized List<ScoredMonsterInfo> getLastScoredMonstersSnapshot() {
        if (rt().lastScoredMonsters.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rt().lastScoredMonsters);
    }

    private static synchronized void replaceLastScoredMonsters(List<ScoredMonsterInfo> scoredMonsters) {
        rt().lastScoredMonsters = scoredMonsters == null || scoredMonsters.isEmpty()
                ? Collections.<ScoredMonsterInfo>emptyList()
                : new ArrayList<>(scoredMonsters);
    }

    public static void setActiveRule(AutoFollowRule ruleToActivate) {
        for (AutoFollowRule rule : snapshotRules()) {
            rule.enabled = false;
        }
        if (ruleToActivate != null) {
            ruleToActivate.updateBounds();
            ruleToActivate.ensureReturnPoints();
            ruleToActivate.enabled = true;
            rt().activeRule = ruleToActivate;
            rt().lastQuickToggleRuleName = ruleToActivate.name == null ? "" : ruleToActivate.name.trim();
            reloadState(false);
        } else {
            rt().activeRule = null;
            resetPatrolState();
        }
        saveFollowConfig();
    }

    private static AutoFollowRule findRuleByName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (AutoFollowRule rule : snapshotRules()) {
            if (rule != null && rule.name != null && normalized.equalsIgnoreCase(rule.name.trim())) {
                return rule;
            }
        }
        return null;
    }

    public static void reloadState(boolean showMessage) {
        if (rt().activeRule == null) {
            return;
        }
        rt().activeRule.updateBounds();
        rt().activeRule.ensureReturnPoints();

        rt().isMovingToPoint = true;
        rt().returningToCenterFromOutOfBounds = false;
        rt().escapeDestination = null;
        rt().isSuspendedDueToDistance = false;
        rt().lastPlayerPosition = null;
        rt().stuckCheckCounter = 0;
        rt().lastStuckCheckPosition = null;
        rt().timeoutTicksCounter = 0;
        rt().lastTimeoutCheckPosition = null;
        rt().huntTargetEntity = null;
        rt().lastHuntGotoTick = -99999;
        rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
        rt().lastHuntTargetPos = null;
        rt().huntMovementStopped = false;
        rt().centerReturnCommandIssued = false;
        rt().outOfRecoveryRangeSequenceTriggered = false;
        rt().patrolWaitingAtPoint = false;
        rt().patrolWaitStartMs = 0L;
        resetPatrolStuckMonitor();
        rt().randomPatrolPoint = null;
        rt().lastMonsterScoreScanTick = -99999;
        replaceLastScoredMonsters(Collections.<ScoredMonsterInfo>emptyList());

        if (mc.player != null) {
            rt().currentReturnPointIndex = getNearestReturnPointIndex(mc.player.posX, mc.player.posY, mc.player.posZ);
        } else {
            rt().currentReturnPointIndex = selectInitialReturnPointIndex();
        }

        EmbeddedNavigationHandler.INSTANCE.stop();
        startMoveToCurrentReturnPoint();

        if (showMessage && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.AQUA + "[自动追怪] " + TextFormatting.GREEN + "已重载！正在返回回归点并重新开始追怪..."));
        }
    }

    // 原 public static isMovingToPoint 的对外兼容访问器：语义为「当前会话的
    // 正在 goto」；多 bot 场景由调用线程的 tick owner 解析，行为与装箱前一致。
    public static boolean isMovingToPoint() {
        return rt().isMovingToPoint;
    }

    public static void setMovingToPoint(boolean moving) {
        rt().isMovingToPoint = moving;
    }

    private static void resetPatrolState() {
        rt().currentReturnPointIndex = 0;
        rt().patrolWaitingAtPoint = false;
        rt().patrolWaitStartMs = 0L;
        resetPatrolStuckMonitor();
        rt().randomPatrolPoint = null;
        rt().centerReturnCommandIssued = false;
    }

    private static List<AutoFollowRule> snapshotRules() {
        if (cfg().rules.isEmpty()) {
            return Collections.emptyList();
        }
        return cfg().rules;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player == null
                || event.phase != TickEvent.Phase.END
                || !event.player.world.isRemote
                || !(event.player instanceof EntityPlayerSP)
                || !event.player.equals(mc.player)) {
            return;
        }

        EntityPlayerSP player = (EntityPlayerSP) event.player;
        Vec3d currentPos = player.getPositionVector();

        if (rt().activeRule == null || !rt().activeRule.enabled
                || PathSequenceEventListener.foregroundRunner().isTracking()
                || AutoEscapeHandler.isEmergencyLockActive()) {
            rt().lastTickPlayerPos = currentPos;
            rt().stuckCheckCounter = 0;
            rt().lastStuckCheckPosition = null;
            rt().timeoutTicksCounter = 0;
            rt().lastTimeoutCheckPosition = null;
            rt().returningToCenterFromOutOfBounds = false;
            rt().huntTargetEntity = null;
            rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
            rt().lastHuntTargetPos = null;
            rt().huntMovementStopped = false;
            rt().centerReturnCommandIssued = false;
            rt().outOfRecoveryRangeSequenceTriggered = false;
            rt().patrolWaitingAtPoint = false;
            rt().patrolWaitStartMs = 0L;
            resetPatrolStuckMonitor();
            replaceLastScoredMonsters(Collections.<ScoredMonsterInfo>emptyList());
            return;
        }

        rt().activeRule.updateBounds();
        rt().activeRule.ensureReturnPoints();

        double playerX = player.posX;
        double playerZ = player.posZ;

        rt().lastPlayerPosition = new Point(playerX, playerZ);

        if (rt().isMovingToPoint) {
            rt().timeoutTicksCounter = 0;
            rt().lastTimeoutCheckPosition = null;

            // 回归途中支持抢怪：若范围内刷出怪，立即中断回归并转为追怪
            if (rt().escapeDestination == null && !shouldYieldChaseToKillAura(player)) {
                Entity candidate = findNearestMonsterInRule(player);
                if (candidate != null) {
                    rt().isMovingToPoint = false;
                    rt().returningToCenterFromOutOfBounds = false;
                    rt().centerReturnCommandIssued = false;
                    rt().patrolWaitingAtPoint = false;
                    rt().patrolWaitStartMs = 0L;
                    resetPatrolStuckMonitor();
                    rt().huntTargetEntity = candidate;
                    rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                    rt().lastHuntTargetPos = null;
                    rt().huntMovementStopped = false;
                    handleBoundedMonsterChase(player);
                    rt().lastTickPlayerPos = currentPos;
                    return;
                }
            }

            Point unfinishedReturnPoint = getCurrentReturnPoint();
            if (rt().escapeDestination == null
                    && !isAtReturnPoint(unfinishedReturnPoint, getReturnArriveDistance())
                    && handleInterruptedPatrolRoute(player, currentPos)) {
                rt().lastTickPlayerPos = currentPos;
                return;
            }

            boolean arrived;
            if (rt().escapeDestination != null) {
                arrived = mythosScriptMod.ArriveAt(rt().escapeDestination.x, Double.NaN, rt().escapeDestination.z, 1.5);
                if (arrived) {
                    rt().isMovingToPoint = false;
                    rt().returningToCenterFromOutOfBounds = false;
                    rt().escapeDestination = null;
                    EmbeddedNavigationHandler.INSTANCE.stop();
                    rt().huntTargetEntity = null;
                    rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                    rt().lastHuntTargetPos = null;
                    rt().huntMovementStopped = false;
                    rt().centerReturnCommandIssued = false;
                }
            } else {
                Point returnPoint = getCurrentReturnPoint();
                double tolerance = getReturnArriveDistance();
                arrived = isAtReturnPoint(returnPoint, tolerance);
                if (arrived) {
                    rt().isMovingToPoint = false;
                    rt().returningToCenterFromOutOfBounds = false;
                    EmbeddedNavigationHandler.INSTANCE.stop();
                    rt().huntTargetEntity = null;
                    rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                    rt().lastHuntTargetPos = null;
                    rt().huntMovementStopped = false;
                    rt().centerReturnCommandIssued = false;
                    rt().patrolWaitingAtPoint = true;
                    rt().patrolWaitStartMs = System.currentTimeMillis();
                    resetPatrolStuckMonitor();
                    player.sendMessage(new TextComponentString(
                            TextFormatting.AQUA + "[自动追怪] " + TextFormatting.GREEN + "已到达回归点，开始停留。"));
                }
            }
        } else {
            boolean isWithinBounds = playerX >= rt().activeRule.minX && playerX <= rt().activeRule.maxX
                    && playerZ >= rt().activeRule.minZ && playerZ <= rt().activeRule.maxZ;

            if (isWithinBounds) {
                if (handleTimeoutReload(player)) {
                    return;
                }

                Point returnPoint = getCurrentReturnPoint();
                double distToCenter = distanceToPoint(playerX, playerZ, returnPoint);
                if (distToCenter > rt().activeRule.maxRecoveryDistance) {
                    if (!rt().isSuspendedDueToDistance) {
                        rt().isSuspendedDueToDistance = true;
                        EmbeddedNavigationHandler.INSTANCE.stop();
                        player.sendMessage(
                                new TextComponentString(TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW
                                        + "距离当前回归点过远，逻辑已暂停。进入 "
                                        + (int) rt().activeRule.maxRecoveryDistance + " 格范围后将自动恢复。"));
                    }
                } else {
                    if (rt().isSuspendedDueToDistance) {
                        rt().isSuspendedDueToDistance = false;
                        player.sendMessage(new TextComponentString(
                                TextFormatting.AQUA + "[自动追怪] " + TextFormatting.GREEN + "已返回有效范围，恢复追怪。"));
                        rt().huntTargetEntity = null;
                        rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                        rt().lastHuntTargetPos = null;
                        rt().huntMovementStopped = false;
                    }

                    if (cfg().antiStuckEnabled && System.currentTimeMillis() - rt().lastAvoidanceTime > AVOIDANCE_COOLDOWN_MS) {
                        if (handleGeneralStuckCondition(player)) {
                            // 卡住处理成功
                        } else if (cfg().avoidVinesProactively) {
                            Vec3d travelVec = null;
                            if (rt().lastTickPlayerPos != null && currentPos.squareDistanceTo(rt().lastTickPlayerPos) > 0.001) {
                                travelVec = currentPos.subtract(rt().lastTickPlayerPos).normalize();
                            }

                            if (travelVec != null
                                    && isNearHazard(player, player.getPosition(), cfg().vineAvoidanceDistance) != null) {
                                resolveHazardZoneAvoidance(player, travelVec);
                            }
                        }
                    }

                    // 核心逻辑：只在规则范围内选怪，并动态重发 goto，始终保持约1格
                    handleBoundedMonsterChase(player);
                }
            } else {
                if (rt().huntTargetEntity != null && rt().huntTargetEntity.isEntityAlive()
                        && isEntityWithinLockChaseBounds(rt().huntTargetEntity)) {
                    rt().centerReturnCommandIssued = false;
                    rt().patrolWaitingAtPoint = false;
                    rt().patrolWaitStartMs = 0L;
                    handleBoundedMonsterChase(player);
                    rt().lastTickPlayerPos = currentPos;
                    return;
                }

                rt().currentReturnPointIndex = getNearestReturnPointIndex(playerX, player.posY, playerZ);
                Point returnPoint = getCurrentReturnPoint();
                double distToCenter = distanceToPoint(playerX, playerZ, returnPoint);

                rt().huntTargetEntity = null;
                rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                rt().lastHuntTargetPos = null;
                rt().huntMovementStopped = false;
                rt().escapeDestination = null;
                rt().patrolWaitingAtPoint = false;
                rt().patrolWaitStartMs = 0L;

                if (distToCenter > rt().activeRule.maxRecoveryDistance) {
                    rt().isMovingToPoint = false;
                    rt().returningToCenterFromOutOfBounds = false;
                    rt().centerReturnCommandIssued = false;
                    EmbeddedNavigationHandler.INSTANCE.stop();

                    boolean shouldRunSequence = rt().activeRule.runSequenceWhenOutOfRecoveryRange
                            && rt().activeRule.outOfRangeSequenceName != null
                            && !rt().activeRule.outOfRangeSequenceName.trim().isEmpty();

                    if (shouldRunSequence && !rt().outOfRecoveryRangeSequenceTriggered) {
                        String sequenceName = rt().activeRule.outOfRangeSequenceName.trim();
                        if (PathSequenceManager.hasSequence(sequenceName)) {
                            rt().outOfRecoveryRangeSequenceTriggered = true;
                            PathSequenceManager.runPathSequence(sequenceName);
                            long now = System.currentTimeMillis();
                            if (now - rt().lastOutOfBoundsNotifyMs >= OUT_OF_BOUNDS_NOTIFY_COOLDOWN_MS) {
                                rt().lastOutOfBoundsNotifyMs = now;
                                player.sendMessage(new TextComponentString(
                                        TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW
                                                + "超出巡逻范围且超过最大恢复距离，正在执行序列: "
                                                + TextFormatting.WHITE + sequenceName));
                            }
                        } else {
                            long now = System.currentTimeMillis();
                            if (now - rt().lastOutOfBoundsNotifyMs >= OUT_OF_BOUNDS_NOTIFY_COOLDOWN_MS) {
                                rt().lastOutOfBoundsNotifyMs = now;
                                player.sendMessage(new TextComponentString(
                                        TextFormatting.AQUA + "[自动追怪] " + TextFormatting.RED
                                                + "超距序列不存在，无法执行: "
                                                + TextFormatting.WHITE + sequenceName));
                            }
                        }
                    } else {
                        long now = System.currentTimeMillis();
                        if (now - rt().lastOutOfBoundsNotifyMs >= OUT_OF_BOUNDS_NOTIFY_COOLDOWN_MS) {
                            rt().lastOutOfBoundsNotifyMs = now;
                            player.sendMessage(new TextComponentString(
                                    TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW
                                            + "超出巡逻范围，且距离回归点超过最大恢复距离，已停止自动返回。"));
                        }
                    }
                } else {
                    rt().outOfRecoveryRangeSequenceTriggered = false;
                    rt().returningToCenterFromOutOfBounds = true;
                    EmbeddedNavigationHandler.INSTANCE.stop();
                    startMoveToCurrentReturnPoint();
                    long now = System.currentTimeMillis();
                    if (now - rt().lastOutOfBoundsNotifyMs >= OUT_OF_BOUNDS_NOTIFY_COOLDOWN_MS) {
                        rt().lastOutOfBoundsNotifyMs = now;
                        player.sendMessage(new TextComponentString(
                                TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW + "超出巡逻范围，正在返回回归点..."));
                    }
                }
            }
        }

        rt().lastTickPlayerPos = currentPos;
    }

    private void handleBoundedMonsterChase(EntityPlayerSP player) {
        if (rt().activeRule == null || mc.world == null || player == null) {
            return;
        }
        if (shouldYieldChaseToKillAura(player)) {
            rt().huntTargetEntity = null;
            rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
            rt().lastHuntTargetPos = null;
            rt().huntMovementStopped = false;
            return;
        }

        if (rt().huntTargetEntity != null) {
            boolean invalid = !rt().huntTargetEntity.isEntityAlive() || !isEntityWithinLockChaseBounds(rt().huntTargetEntity);
            if (invalid) {
                rt().huntTargetEntity = null;
                rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
                rt().lastHuntTargetPos = null;
                rt().huntMovementStopped = false;
            }
        }

        Entity latestBestCandidate = findNearestMonsterInRule(player);

        if (rt().huntTargetEntity == null) {
            rt().huntTargetEntity = latestBestCandidate;
            if (rt().huntTargetEntity == null) {
                handlePatrolWhenNoMonster();
                return;
            }
            rt().centerReturnCommandIssued = false;
            rt().patrolWaitingAtPoint = false;
            rt().patrolWaitStartMs = 0L;
            rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
            rt().lastHuntTargetPos = null;
            rt().huntMovementStopped = false;
        }

        boolean fixedDistanceMode = isFixedDistanceHuntMode(rt().activeRule);
        double keepDistance = getHuntKeepDistance(rt().activeRule);
        double keepDistSq = keepDistance * keepDistance;
        double distanceSq = player.getDistanceSq(rt().huntTargetEntity);
        double distance = Math.sqrt(distanceSq);
        boolean withinDesiredDistance = fixedDistanceMode
                ? Math.abs(distance - keepDistance) <= HUNT_FIXED_DISTANCE_TOLERANCE
                : distanceSq <= keepDistSq;
        if (withinDesiredDistance) {
            if (!rt().huntMovementStopped) {
                EmbeddedNavigationHandler.INSTANCE.stop();
                rt().huntMovementStopped = true;
            }
            return;
        }

        rt().huntMovementStopped = false;

        Vec3d targetPos = rt().huntTargetEntity.getPositionVector();
        int nowTick = player.ticksExisted;
        int targetId = rt().huntTargetEntity.getEntityId();

        boolean needSendGoto = targetId != rt().lastHuntTargetEntityId
                || rt().lastHuntTargetPos == null
                || targetPos.squareDistanceTo(rt().lastHuntTargetPos) >= HUNT_GOTO_MOVE_THRESHOLD_SQ
                || (nowTick - rt().lastHuntGotoTick) >= HUNT_GOTO_INTERVAL_TICKS;

        // 只有当距离大于保持距离+额外缓冲时才发送goto命令
        double minGotoDistance = keepDistance + 0.5; // 额外0.5格缓冲
        boolean shouldSendGoto = needSendGoto && (fixedDistanceMode
                ? Math.abs(distance - keepDistance) > HUNT_FIXED_DISTANCE_TOLERANCE
                : (distanceSq > minGotoDistance * minGotoDistance));

        if (shouldSendGoto) {
            double destinationX = targetPos.x;
            double destinationZ = targetPos.z;
            if (fixedDistanceMode) {
                double[] destination = computeFixedDistanceHuntDestination(player, rt().huntTargetEntity, keepDistance);
                destinationX = destination[0];
                destinationZ = destination[1];
            }

            BlockPos destination = findBoundedHuntDestination(rt().huntTargetEntity, destinationX, destinationZ);
            if (destination != null) {
                EmbeddedNavigationHandler.INSTANCE.startGoto(destination.getX() + 0.5D, destination.getY(),
                        destination.getZ() + 0.5D);
            } else {
                EmbeddedNavigationHandler.INSTANCE.stop();
                rt().huntMovementStopped = true;
            }
            rt().lastHuntGotoTick = nowTick;
            rt().lastHuntTargetEntityId = targetId;
            rt().lastHuntTargetPos = targetPos;
        }
    }

    private double getHuntKeepDistance(AutoFollowRule rule) {
        if (rule == null) {
            return AutoFollowRule.DEFAULT_MONSTER_STOP_DISTANCE;
        }
        if (isFixedDistanceHuntMode(rule) && rule.monsterFixedDistance > 0) {
            return rule.monsterFixedDistance;
        }
        if (rule.monsterStopDistance > 0) {
            return rule.monsterStopDistance;
        }
        return AutoFollowRule.DEFAULT_MONSTER_STOP_DISTANCE;
    }

    private boolean isFixedDistanceHuntMode(AutoFollowRule rule) {
        return rule != null
                && AutoFollowRule.MONSTER_CHASE_MODE_FIXED_DISTANCE.equalsIgnoreCase(rule.monsterChaseMode);
    }

    private double[] computeFixedDistanceHuntDestination(EntityPlayerSP player, Entity target, double desiredDistance) {
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            distance = 1.0D;
        }

        double scale = desiredDistance / distance;
        double destinationX = target.posX + dx * scale;
        double destinationZ = target.posZ + dz * scale;
        return clipFixedDistanceDestination(rt().activeRule, target.posX, target.posZ, destinationX, destinationZ);
    }

    private BlockPos findBoundedHuntDestination(Entity target, double desiredX, double desiredZ) {
        if (target == null || mc.world == null) {
            return null;
        }

        int targetFeetY = MathHelper.floor(target.posY);
        int maxYDistance = rt().activeRule == null
                ? AutoFollowRule.DEFAULT_MONSTER_CHASE_Y_LIMIT
                : Math.max(0, rt().activeRule.monsterChaseYLimit == null
                        ? AutoFollowRule.DEFAULT_MONSTER_CHASE_Y_LIMIT
                        : rt().activeRule.monsterChaseYLimit);

        BlockPos sameLevel = findStandableHuntDestinationAtY(targetFeetY, desiredX, desiredZ);
        if (sameLevel != null) {
            return sameLevel;
        }

        for (int offset = 1; offset <= maxYDistance; offset++) {
            BlockPos above = findStandableHuntDestinationAtY(targetFeetY + offset, desiredX, desiredZ);
            if (above != null) {
                return above;
            }
        }
        return null;
    }

    private BlockPos findStandableHuntDestinationAtY(int feetY, double desiredX, double desiredZ) {
        if (feetY <= 0 || feetY >= 255) {
            return null;
        }

        int baseX = MathHelper.floor(desiredX);
        int baseZ = MathHelper.floor(desiredZ);
        BlockPos best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int radius = 0; radius <= HUNT_DESTINATION_HORIZONTAL_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = new BlockPos(baseX + dx, feetY, baseZ + dz);
                    if (!isStandableHuntDestination(candidate)
                            || !isPositionWithinActiveLockChaseBounds(candidate.getX() + 0.5D,
                                    candidate.getZ() + 0.5D)) {
                        continue;
                    }

                    double candidateX = candidate.getX() + 0.5D;
                    double candidateZ = candidate.getZ() + 0.5D;
                    double distanceSq = (candidateX - desiredX) * (candidateX - desiredX)
                            + (candidateZ - desiredZ) * (candidateZ - desiredZ);
                    if (best == null || distanceSq < bestDistanceSq) {
                        best = candidate;
                        bestDistanceSq = distanceSq;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private boolean isStandableHuntDestination(BlockPos feetPos) {
        if (feetPos == null || mc.world == null) {
            return false;
        }

        IBlockState feetState = mc.world.getBlockState(feetPos);
        IBlockState headState = mc.world.getBlockState(feetPos.up());
        if (feetState.getMaterial().blocksMovement() || headState.getMaterial().blocksMovement()) {
            return false;
        }

        IBlockState supportState = mc.world.getBlockState(feetPos.down());
        if (!supportState.getMaterial().blocksMovement()) {
            return false;
        }
        try {
            if (supportState.isSideSolid(mc.world, feetPos.down(), EnumFacing.UP)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        AxisAlignedBB collisionBox = supportState.getCollisionBoundingBox(mc.world, feetPos.down());
        return collisionBox != null
                && collisionBox != Block.NULL_AABB
                && collisionBox.maxY >= 1.0D - 1.0E-4D;
    }

    private double[] clipFixedDistanceDestination(AutoFollowRule rule, double centerX, double centerZ,
            double destinationX, double destinationZ) {
        if (rule == null || isPositionWithinLockChaseBounds(rule, destinationX, destinationZ)) {
            return new double[] { destinationX, destinationZ };
        }

        double dx = destinationX - centerX;
        double dz = destinationZ - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-4D) {
            return new double[] { centerX, centerZ };
        }

        double dirX = dx / distance;
        double dirZ = dz / distance;
        double low = 0.0D;
        double high = distance;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) * 0.5D;
            double testX = centerX + dirX * mid;
            double testZ = centerZ + dirZ * mid;
            if (isPositionWithinLockChaseBounds(rule, testX, testZ)) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return new double[] { centerX + dirX * low, centerZ + dirZ * low };
    }

    private void handlePatrolWhenNoMonster() {
        if (rt().activeRule == null) {
            return;
        }
        rt().activeRule.ensureReturnPoints();

        if (rt().activeRule.returnPoints == null || rt().activeRule.returnPoints.isEmpty()) {
            handleRandomPatrolWithoutReturnPoints();
            return;
        }

        Point returnPoint = getCurrentReturnPoint();

        if (rt().patrolWaitingAtPoint) {
            EmbeddedNavigationHandler.INSTANCE.stop();
            int stayMillis = Math.max(1, rt().activeRule.returnStayMillis);
            if (System.currentTimeMillis() - rt().patrolWaitStartMs >= stayMillis) {
                if (rt().activeRule.returnPoints != null && rt().activeRule.returnPoints.size() > 1) {
                    rt().patrolWaitingAtPoint = false;
                    rt().patrolWaitStartMs = 0L;
                    advanceToNextReturnPoint();
                    startMoveToCurrentReturnPoint();
                } else {
                    rt().patrolWaitStartMs = System.currentTimeMillis();
                }
            }
            return;
        }

        boolean atPoint = isAtReturnPoint(returnPoint, getReturnArriveDistance());
        if (!atPoint) {
            if (!rt().isMovingToPoint || !rt().centerReturnCommandIssued) {
                startMoveToCurrentReturnPoint();
            }
            return;
        }

        rt().centerReturnCommandIssued = false;
        rt().patrolWaitingAtPoint = true;
        rt().patrolWaitStartMs = System.currentTimeMillis();
    }

    private void handleRandomPatrolWithoutReturnPoints() {
        if (rt().activeRule == null) {
            return;
        }

        if (rt().patrolWaitingAtPoint) {
            EmbeddedNavigationHandler.INSTANCE.stop();
            int stayMillis = Math.max(1, rt().activeRule.returnStayMillis);
            if (System.currentTimeMillis() - rt().patrolWaitStartMs >= stayMillis) {
                rt().patrolWaitingAtPoint = false;
                rt().patrolWaitStartMs = 0L;
                rt().randomPatrolPoint = getRandomPatrolPointWithinBounds();
                startMoveToPoint(rt().randomPatrolPoint);
            }
            return;
        }

        if (rt().randomPatrolPoint == null) {
            rt().randomPatrolPoint = getRandomPatrolPointWithinBounds();
            startMoveToPoint(rt().randomPatrolPoint);
            return;
        }

        boolean atPoint = mythosScriptMod.ArriveAt(rt().randomPatrolPoint.x, Double.NaN, rt().randomPatrolPoint.z, getReturnArriveDistance());
        if (!atPoint) {
            if (!rt().isMovingToPoint || !rt().centerReturnCommandIssued) {
                startMoveToPoint(rt().randomPatrolPoint);
            }
            return;
        }

        rt().centerReturnCommandIssued = false;
        rt().patrolWaitingAtPoint = true;
        rt().patrolWaitStartMs = System.currentTimeMillis();
    }

    private static void startMoveToCurrentReturnPoint() {
        if (rt().activeRule == null) {
            return;
        }
        startMoveToPoint(getCurrentReturnPoint());
    }

    private static void startMoveToPoint(Point targetPoint) {
        if (targetPoint == null) {
            return;
        }
        rt().isMovingToPoint = true;
        rt().patrolWaitingAtPoint = false;
        rt().patrolWaitStartMs = 0L;
        resetPatrolStuckMonitor();
        rt().centerReturnCommandIssued = true;
        ModUtils.DelayScheduler.instance.schedule(() -> {
            startGotoPoint(targetPoint, false);
        }, COMMAND_DELAY_TICKS);
    }

    private static boolean handleInterruptedPatrolRoute(EntityPlayerSP player, Vec3d currentPos) {
        if (rt().activeRule == null || player == null || currentPos == null) {
            resetPatrolStuckMonitor();
            return false;
        }

        long now = System.currentTimeMillis();
        if (rt().lastPatrolProgressPosition == null
                || currentPos.squareDistanceTo(rt().lastPatrolProgressPosition) >= STUCK_DISTANCE_THRESHOLD_SQ) {
            rt().lastPatrolProgressPosition = currentPos;
            rt().patrolStoppedSinceMs = now;
            return false;
        }
        if (rt().patrolStoppedSinceMs <= 0L) {
            rt().patrolStoppedSinceMs = now;
            return false;
        }

        long timeoutMs = Math.max(1, rt().activeRule.patrolStuckRestartSeconds) * 1000L;
        if (now - rt().patrolStoppedSinceMs < timeoutMs) {
            return false;
        }

        boolean killAuraOwnsMovement = shouldYieldPatrolMovementToKillAura(player)
                || KillAuraHandler.INSTANCE.isHuntPickupNavigationActive();
        if (killAuraOwnsMovement) {
            return false;
        }

        Point unfinishedPoint = getCurrentReturnPoint();
        resetPatrolStuckMonitor();
        rt().centerReturnCommandIssued = true;
        startGotoPoint(unfinishedPoint, true);
        player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW
                        + "巡逻寻路停留超过 " + Math.max(1, rt().activeRule.patrolStuckRestartSeconds)
                        + " 秒，已重启当前回点寻路。"));
        return true;
    }

    private static boolean shouldYieldPatrolMovementToKillAura(EntityPlayerSP player) {
        return player != null
                && KillAuraHandler.enabled()
                && KillAuraHandler.isHuntEnabled()
                && KillAuraHandler.INSTANCE.hasActiveTarget(player);
    }

    private static void resetPatrolStuckMonitor() {
        rt().lastPatrolProgressPosition = null;
        rt().patrolStoppedSinceMs = 0L;
    }

    private static Point getCurrentReturnPoint() {
        if (rt().activeRule == null) {
            return new Point(0, 0);
        }
        rt().activeRule.ensureReturnPoints();
        List<Point> returnPoints = rt().activeRule.returnPoints;
        if (returnPoints == null || returnPoints.isEmpty()) {
            if (rt().randomPatrolPoint != null) {
                return new Point(rt().randomPatrolPoint.x, rt().randomPatrolPoint.z);
            }
            return new Point((rt().activeRule.minX + rt().activeRule.maxX) * 0.5, (rt().activeRule.minZ + rt().activeRule.maxZ) * 0.5);
        }
        if (rt().currentReturnPointIndex < 0 || rt().currentReturnPointIndex >= returnPoints.size()) {
            rt().currentReturnPointIndex = 0;
        }
        Point point = returnPoints.get(rt().currentReturnPointIndex);
        return point == null ? new Point(0, 0) : AutoFollowRule.copyPoint(point);
    }

    private static Point getRandomPatrolPointWithinBounds() {
        if (rt().activeRule == null) {
            return new Point(0, 0);
        }
        double spanX = Math.max(0.0, rt().activeRule.maxX - rt().activeRule.minX);
        double spanZ = Math.max(0.0, rt().activeRule.maxZ - rt().activeRule.minZ);
        double x = rt().activeRule.minX + (spanX <= 0.01 ? 0.0 : RANDOM.nextDouble() * spanX);
        double z = rt().activeRule.minZ + (spanZ <= 0.01 ? 0.0 : RANDOM.nextDouble() * spanZ);
        return new Point(x, z);
    }

    private static int selectInitialReturnPointIndex() {
        if (rt().activeRule == null) {
            return 0;
        }
        rt().activeRule.ensureReturnPoints();
        List<Point> returnPoints = rt().activeRule.returnPoints;
        if (returnPoints == null || returnPoints.isEmpty()) {
            return 0;
        }
        if (AutoFollowRule.PATROL_MODE_RANDOM.equalsIgnoreCase(rt().activeRule.patrolMode)) {
            return RANDOM.nextInt(returnPoints.size());
        }
        return 0;
    }

    private static int getNearestReturnPointIndex(double x, double y, double z) {
        if (rt().activeRule == null) {
            return 0;
        }
        rt().activeRule.ensureReturnPoints();
        List<Point> returnPoints = rt().activeRule.returnPoints;
        if (returnPoints == null || returnPoints.isEmpty()) {
            return 0;
        }

        int bestIndex = 0;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < returnPoints.size(); i++) {
            Point point = returnPoints.get(i);
            if (point == null) {
                continue;
            }
            double dx = x - point.x;
            double dz = z - point.z;
            double distSq = dx * dx + dz * dz;
            if (point.hasY()) {
                double dy = y - point.y;
                distSq += dy * dy;
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static void advanceToNextReturnPoint() {
        if (rt().activeRule == null) {
            rt().currentReturnPointIndex = 0;
            return;
        }
        rt().activeRule.ensureReturnPoints();
        List<Point> returnPoints = rt().activeRule.returnPoints;
        if (returnPoints == null || returnPoints.size() <= 1) {
            rt().currentReturnPointIndex = 0;
            return;
        }

        if (AutoFollowRule.PATROL_MODE_RANDOM.equalsIgnoreCase(rt().activeRule.patrolMode)) {
            int next = rt().currentReturnPointIndex;
            while (next == rt().currentReturnPointIndex && returnPoints.size() > 1) {
                next = RANDOM.nextInt(returnPoints.size());
            }
            rt().currentReturnPointIndex = next;
        } else {
            rt().currentReturnPointIndex = (rt().currentReturnPointIndex + 1) % returnPoints.size();
        }
    }

    private static double distanceToPoint(double x, double z, Point point) {
        if (point == null) {
            return Double.MAX_VALUE;
        }
        double dx = x - point.x;
        double dz = z - point.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double getReturnArriveDistance() {
        if (rt().activeRule == null || rt().activeRule.returnArriveDistance <= 0) {
            return AutoFollowRule.DEFAULT_RETURN_ARRIVE_DISTANCE;
        }
        return rt().activeRule.returnArriveDistance;
    }

    private static boolean isAtReturnPoint(Point point, double tolerance) {
        if (point == null) {
            return false;
        }
        return mythosScriptMod.ArriveAt(point.x, point.hasY() ? point.y : Double.NaN, point.z, tolerance);
    }

    private static void startGotoPoint(Point point, boolean bypassGotoThrottle) {
        if (point == null) {
            return;
        }
        if (point.hasY()) {
            EmbeddedNavigationHandler.INSTANCE.startGoto(point.x, point.y, point.z, bypassGotoThrottle);
        } else {
            EmbeddedNavigationHandler.INSTANCE.startGotoXZ(point.x, point.z, bypassGotoThrottle);
        }
    }

    private boolean shouldYieldChaseToKillAura(EntityPlayerSP player) {
        return player != null
                && KillAuraHandler.enabled()
                && KillAuraHandler.isHuntEnabled()
                && KillAuraHandler.INSTANCE.hasActiveTarget(player);
    }

    private Entity findNearestMonsterInRule(EntityPlayerSP player) {
        if (player == null || mc.world == null) {
            replaceLastScoredMonsters(Collections.<ScoredMonsterInfo>emptyList());
            return null;
        }

        if (player.ticksExisted - rt().lastMonsterScoreScanTick < MONSTER_SCORE_SCAN_INTERVAL_TICKS) {
            Entity cached = resolveBestCachedMonster();
            if (cached != null) {
                return cached;
            }
        }

        List<ScoredMonsterInfo> snapshots = new ArrayList<>();
        Entity bestEntity = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double playerCenterY = getPlayerCenterY(player);
        Vec3d playerPosition = player.getPositionVector();
        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == player || !entity.isEntityAlive()) {
                continue;
            }
            if (!isEntityInActiveRuleBounds(entity)) {
                continue;
            }

            ScoredMonsterInfo info = scoreMonsterCandidate(player, playerPosition, playerCenterY, entity);
            snapshots.add(info);
            if (bestEntity == null
                    || info.totalScore > bestScore
                    || (info.totalScore == bestScore && entity.getEntityId() < bestEntity.getEntityId())) {
                bestEntity = entity;
                bestScore = info.totalScore;
            }
        }

        if (snapshots.isEmpty()) {
            rt().lastMonsterScoreScanTick = player.ticksExisted;
            replaceLastScoredMonsters(Collections.<ScoredMonsterInfo>emptyList());
            return null;
        }

        snapshots.sort((left, right) -> Double.compare(right.totalScore, left.totalScore));
        rt().lastMonsterScoreScanTick = player.ticksExisted;
        replaceLastScoredMonsters(snapshots);
        return bestEntity;
    }

    private Entity resolveBestCachedMonster() {
        if (mc.world == null || rt().lastScoredMonsters.isEmpty()) {
            return null;
        }
        for (ScoredMonsterInfo info : rt().lastScoredMonsters) {
            Entity entity = mc.world.getEntityByID(info.entityId);
            if (entity != null && entity.isEntityAlive() && isEntityInActiveRuleBounds(entity)) {
                return entity;
            }
        }
        return null;
    }

    private ScoredMonsterInfo scoreMonsterCandidate(EntityPlayerSP player, Vec3d playerPosition, double playerCenterY,
            Entity entity) {
        ScoredMonsterInfo info = new ScoredMonsterInfo();
        info.entityId = entity.getEntityId();
        info.name = getFilterableEntityName(entity);

        double entityCenterY = getEntityCenterY(entity);
        double verticalDelta = entityCenterY - playerCenterY;
        double verticalDiff = Math.abs(verticalDelta);
        double distance = player.getDistance(entity);
        boolean visible = player.canEntityBeSeen(entity);
        boolean reachable = isPathClear(playerPosition, new Vec3d(entity.posX, entityCenterY, entity.posZ));
        boolean floating = isEntitySuspended(entity);

        info.distance = distance;
        info.verticalDiff = verticalDiff;
        info.visible = visible;
        info.reachable = reachable;

        double upwardRange = getAllowedUpwardRange(player, entity);
        double downwardRange = getAllowedDownwardRange(player, entity);
        double allowedVertical = verticalDelta >= 0 ? upwardRange : downwardRange;

        info.distanceScore = Math.max(0.0, 120.0 - distance * 12.0);
        info.visibilityScore = visible ? 35.0 : -25.0;
        info.reachabilityScore = reachable ? 30.0 : -40.0;
        info.verticalScore = Math.max(-35.0, 30.0 - (verticalDiff / Math.max(0.1, allowedVertical)) * 30.0);
        if (floating) {
            info.verticalScore -= 15.0;
        }
        info.lockBonusScore = rt().huntTargetEntity != null && rt().huntTargetEntity.getEntityId() == entity.getEntityId() ? 18.0 : 0.0;
        info.totalScore = info.distanceScore
                + info.visibilityScore
                + info.reachabilityScore
                + info.verticalScore
                + info.lockBonusScore;

        return info;
    }

    private boolean isEntityInActiveRuleBounds(Entity entity) {
        if (rt().activeRule == null || entity == null) {
            return false;
        }
        double x = entity.posX;
        double z = entity.posZ;
        boolean insideHorizontalBounds = x >= rt().activeRule.minX && x <= rt().activeRule.maxX
                && z >= rt().activeRule.minZ && z <= rt().activeRule.maxZ;
        if (!insideHorizontalBounds) {
            return false;
        }
        if (!passesMonsterTargetFilters(entity)) {
            return false;
        }
        return passesMonsterVerticalRange(entity);
    }

    private boolean isEntityWithinLockChaseBounds(Entity entity) {
        if (rt().activeRule == null || entity == null) {
            return false;
        }
        if (!passesMonsterTargetFilters(entity) || !passesMonsterVerticalRange(entity)) {
            return false;
        }
        return isPositionWithinActiveLockChaseBounds(entity.posX, entity.posZ);
    }

    private boolean passesMonsterTargetFilters(Entity entity) {
        if (rt().activeRule == null || entity == null) {
            return false;
        }
        if (!matchesConfiguredEntityType(entity)) {
            return false;
        }
        if (!rt().activeRule.targetInvisibleMonsters && entity.isInvisible()) {
            return false;
        }
        if (!rt().activeRule.enableMonsterNameList) {
            return true;
        }

        String name = getFilterableEntityName(entity);
        boolean hasWhitelist = rt().activeRule.monsterWhitelistNames != null && !rt().activeRule.monsterWhitelistNames.isEmpty();
        boolean matchedWhitelist = !hasWhitelist
                || KillAuraHandler.getNameListMatchIndex(name, rt().activeRule.monsterWhitelistNames) != Integer.MAX_VALUE;
        boolean matchedBlacklist = KillAuraHandler.getNameListMatchIndex(name, rt().activeRule.monsterBlacklistNames) != Integer.MAX_VALUE;
        return matchedWhitelist && !matchedBlacklist;
    }

    private boolean matchesConfiguredEntityType(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand) {
            return false;
        }
        List<String> types = rt().activeRule == null ? null : rt().activeRule.entityTypes;
        if (types == null || types.isEmpty()) {
            return entity instanceof IMob;
        }
        for (String rawType : types) {
            if (matchesEntityTypeToken(entity, rawType)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEntityTypeToken(Entity entity, String rawType) {
        String token = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty()) {
            return false;
        }

        switch (token) {
            case "任意":
            case AutoFollowRule.ENTITY_TYPE_ANY:
                return entity instanceof EntityLivingBase;
            case "生物":
            case AutoFollowRule.ENTITY_TYPE_LIVING:
                return entity instanceof EntityLivingBase;
            case "玩家":
            case AutoFollowRule.ENTITY_TYPE_PLAYER:
                return ModUtils.isRealPlayer(entity);
            case AutoFollowRule.ENTITY_TYPE_NPC:
                return ModUtils.isNpcPlayer(entity);
            case "怪物":
            case "mob":
            case "hostile":
            case AutoFollowRule.ENTITY_TYPE_MONSTER:
                return entity instanceof IMob;
            case "中立":
            case "中立生物":
            case AutoFollowRule.ENTITY_TYPE_NEUTRAL:
                return entity instanceof EntityLivingBase
                        && !(entity instanceof EntityPlayer)
                        && !(entity instanceof IMob)
                        && !(entity instanceof EntityAnimal)
                        && !(entity instanceof EntityAgeable)
                        && !(entity instanceof EntityWaterMob)
                        && !(entity instanceof EntityAmbientCreature)
                        && !(entity instanceof EntityVillager)
                        && !(entity instanceof EntityGolem)
                        && !(entity instanceof EntityTameable);
            case "动物":
            case "passive":
            case AutoFollowRule.ENTITY_TYPE_ANIMAL:
                return entity instanceof EntityAnimal || entity instanceof EntityAgeable;
            case "水生":
            case AutoFollowRule.ENTITY_TYPE_WATER:
                return entity instanceof EntityWaterMob;
            case "环境":
            case AutoFollowRule.ENTITY_TYPE_AMBIENT:
                return entity instanceof EntityAmbientCreature;
            case "村民":
            case AutoFollowRule.ENTITY_TYPE_VILLAGER:
                return entity instanceof EntityVillager;
            case "傀儡":
            case AutoFollowRule.ENTITY_TYPE_GOLEM:
                return entity instanceof EntityGolem;
            case "驯服":
            case "宠物":
            case AutoFollowRule.ENTITY_TYPE_TAMEABLE:
                return entity instanceof EntityTameable;
            case "首领":
            case AutoFollowRule.ENTITY_TYPE_BOSS:
                return entity instanceof EntityLivingBase && !((EntityLivingBase) entity).isNonBoss();
            default:
                return false;
        }
    }

    private String getFilterableEntityName(Entity entity) {
        if (entity == null) {
            return "";
        }
        String displayName = entity.getDisplayName() == null ? "" : entity.getDisplayName().getUnformattedText();
        String normalized = KillAuraHandler.normalizeFilterName(displayName);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return KillAuraHandler.normalizeFilterName(entity.getName());
    }

    private boolean passesMonsterVerticalRange(Entity entity) {
        if (entity == null || mc.player == null || rt().activeRule == null) {
            return false;
        }
        double delta = getEntityCenterY(entity) - getPlayerCenterY(mc.player);
        return delta <= getAllowedUpwardRange(mc.player, entity)
                && delta >= -getAllowedDownwardRange(mc.player, entity);
    }

    private double getEntityCenterY(Entity entity) {
        AxisAlignedBB box = entity == null ? null : entity.getEntityBoundingBox();
        return box == null ? (entity == null ? 0.0 : entity.posY) : (box.minY + box.maxY) * 0.5;
    }

    private double getPlayerCenterY(EntityPlayerSP player) {
        AxisAlignedBB box = player == null ? null : player.getEntityBoundingBox();
        return box == null ? (player == null ? 0.0 : player.posY) : (box.minY + box.maxY) * 0.5;
    }

    private double getAllowedUpwardRange(EntityPlayerSP player, Entity entity) {
        double range = rt().activeRule != null && rt().activeRule.monsterUpwardRange > 0
                ? rt().activeRule.monsterUpwardRange
                : AutoFollowRule.DEFAULT_MONSTER_UPWARD_RANGE;
        if (isStairOrSlopeContext(player, entity)) {
            range += 1.0;
        }
        return range;
    }

    private double getAllowedDownwardRange(EntityPlayerSP player, Entity entity) {
        double range = rt().activeRule != null && rt().activeRule.monsterDownwardRange > 0
                ? rt().activeRule.monsterDownwardRange
                : AutoFollowRule.DEFAULT_MONSTER_DOWNWARD_RANGE;
        if (isStairOrSlopeContext(player, entity)) {
            range += 1.0;
        }
        return range;
    }

    private boolean isStairOrSlopeContext(EntityPlayerSP player, Entity entity) {
        BlockPos playerPos = player == null ? null : player.getPosition();
        BlockPos entityPos = entity == null ? null : new BlockPos(entity.posX, entity.posY, entity.posZ);
        return (playerPos != null && isOnSlabOrStair(playerPos))
                || (entityPos != null && isOnSlabOrStair(entityPos));
    }

    private boolean isEntitySuspended(Entity entity) {
        if (entity == null || mc.world == null) {
            return false;
        }
        BlockPos under = new BlockPos(entity.posX, entity.posY - 0.1, entity.posZ).down();
        return mc.world.getBlockState(under).getMaterial().isReplaceable();
    }

    private boolean handleTimeoutReload(EntityPlayerSP player) {
        if (!cfg().timeoutReloadEnabled) {
            return false;
        }

        rt().timeoutTicksCounter++;
        if (rt().timeoutTicksCounter >= 20) {
            if (rt().lastTimeoutCheckPosition != null && player.getPositionVector()
                    .squareDistanceTo(rt().lastTimeoutCheckPosition) < STUCK_DISTANCE_THRESHOLD_SQ) {
                // 位置没变，继续计时
            } else {
                rt().timeoutTicksCounter = 0;
            }
            rt().lastTimeoutCheckPosition = player.getPositionVector();
        }

        if (rt().timeoutTicksCounter >= cfg().timeoutReloadSeconds * 20) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.AQUA + "[自动追怪] " + TextFormatting.RED + "检测到长时间未移动，触发超时重载！"));
            reloadState(true);
            return true;
        }
        return false;
    }

    private boolean handleGeneralStuckCondition(EntityPlayerSP player) {
        rt().stuckCheckCounter++;
        if (rt().stuckCheckCounter >= STUCK_CHECK_INTERVAL) {
            List<Entity> nearbyEnemies = mc.world.getEntitiesInAABBexcluding(player,
                    player.getEntityBoundingBox().grow(3.5),
                    entity -> entity != null
                            && entity != player
                            && entity.isEntityAlive()
                            && passesMonsterTargetFilters(entity));

            if (!nearbyEnemies.isEmpty()) {
                rt().stuckCheckCounter = 0;
                rt().lastStuckCheckPosition = player.getPositionVector();
                return false;
            }

            if (rt().lastStuckCheckPosition != null && player.getPositionVector()
                    .squareDistanceTo(rt().lastStuckCheckPosition) < STUCK_DISTANCE_THRESHOLD_SQ) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动追怪] " + TextFormatting.RED + "检测到卡住！执行智能逃逸..."));

                if (findAndExecuteEscape(player)) {
                    rt().stuckCheckCounter = 0;
                    rt().lastStuckCheckPosition = null;
                    return true;
                }
            }
            rt().lastStuckCheckPosition = player.getPositionVector();
            rt().stuckCheckCounter = 0;
        }
        return false;
    }

    private boolean findAndExecuteEscape(EntityPlayerSP player) {
        Vec3d escapeVec = findEscapeVector(player);
        if (escapeVec != null) {
            Vec3d avoidancePoint = player.getPositionVector().add(escapeVec.scale(ESCAPE_DISTANCE));

            this.rt().escapeDestination = avoidancePoint;
            this.rt().isMovingToPoint = true;
            rt().returningToCenterFromOutOfBounds = false;
            EmbeddedNavigationHandler.INSTANCE.stop();
            ModUtils.DelayScheduler.instance.schedule(() -> {
                EmbeddedNavigationHandler.INSTANCE.startGotoXZ(avoidancePoint.x, avoidancePoint.z);
            }, COMMAND_DELAY_TICKS);
            rt().lastAvoidanceTime = System.currentTimeMillis();
            return true;
        } else {
            player.sendMessage(new TextComponentString(
                    TextFormatting.AQUA + "[自动追怪] " + TextFormatting.DARK_RED + "无法找到安全的逃逸路径！"));
            reloadState(true);
            return false;
        }
    }

    private Vec3d findEscapeVector(EntityPlayerSP player) {
        Vec3d bestVector = null;
        double bestScore = -1;

        Vec3d forwardVec = Vec3d.fromPitchYaw(0, player.rotationYaw).normalize();

        Vec3d[] directions = {
                new Vec3d(0, 0, -1), new Vec3d(1, 0, -1).normalize(), new Vec3d(1, 0, 0),
                new Vec3d(1, 0, 1).normalize(), new Vec3d(0, 0, 1), new Vec3d(-1, 0, 1).normalize(),
                new Vec3d(-1, 0, 0), new Vec3d(-1, 0, -1).normalize()
        };

        for (Vec3d dir : directions) {
            Vec3d escapePointVec = player.getPositionVector().add(dir.scale(ESCAPE_DISTANCE));
            BlockPos escapePointPos = new BlockPos(escapePointVec);

            // [V4.8 路径清空检查] 增加路径检查
            if (!isWalkable(escapePointPos) || !isPathClear(player.getPositionVector(), escapePointVec)) {
                continue;
            }

            BlockPos nearestHazard = isNearHazard(player, escapePointPos, 10.0);
            double hazardScore = (nearestHazard != null)
                    ? escapePointPos.getDistance(nearestHazard.getX(), nearestHazard.getY(), nearestHazard.getZ())
                    : 100.0;

            double directionScore = forwardVec.dotProduct(dir);
            double intentBonus = (directionScore + 1) * 10;

            double finalScore = hazardScore + intentBonus;

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestVector = dir;
            }
        }
        return bestVector;
    }

    private boolean isWalkable(BlockPos pos) {
        if (mc.world.getBlockState(pos.down()).getMaterial().isReplaceable()) {
            return false;
        }
        if (!mc.world.getBlockState(pos).getMaterial().isReplaceable()
                || !mc.world.getBlockState(pos.up()).getMaterial().isReplaceable()) {
            return false;
        }
        if (Math.abs(pos.getY() - mc.player.posY) > 2) {
            return false;
        }
        return true;
    }

    // [V4.8 路径清空检查] 新增的核心函数
    /**
     * 检查从起点到终点的直线路径是否被固体方块阻挡。
     *
     * @param start 起点
     * @param end   终点
     * @return 如果路径通畅则返回 true
     */
    private boolean isPathClear(Vec3d start, Vec3d end) {
        Vec3d direction = end.subtract(start);
        double distance = direction.lengthVector();
        direction = direction.normalize();

        // 以0.5格为步长，检查路径上的每一个点
        for (double step = 0.5; step < distance; step += 0.5) {
            Vec3d intermediatePoint = start.add(direction.scale(step));
            BlockPos checkPos = new BlockPos(intermediatePoint);

            // 检查脚下和头顶的方块
            IBlockState feetState = mc.world.getBlockState(checkPos);
            IBlockState headState = mc.world.getBlockState(checkPos.up());

            // isFullCube 是一个很好的判断是否为固体障碍物的方法
            if (feetState.isFullCube() || headState.isFullCube()) {
                return false; // 路径被阻挡
            }
        }
        return true; // 路径通畅
    }

    private void resolveHazardZoneAvoidance(EntityPlayerSP player, Vec3d travelVec) {
        player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[自动追怪] " + TextFormatting.GOLD + "感知到前方危险区域！计算最优规避路径..."));

        List<BlockPos> obstacles = scanForwardArea(player, travelVec);
        if (obstacles.isEmpty()) {
            forcefulSidestep(player, travelVec);
            return;
        }

        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        double maxDepth = 0;

        for (BlockPos pos : obstacles) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            Vec3d obstacleVec = new Vec3d(pos.getX() - player.posX, 0, pos.getZ() - player.posZ);
            double depth = obstacleVec.dotProduct(new Vec3d(travelVec.x, 0, travelVec.z));
            maxDepth = Math.max(maxDepth, depth);
        }

        Vec3d hazardCenter = new Vec3d((minX + maxX) / 2, player.posY, (minZ + maxZ) / 2);
        Vec3d playerToHazardVec = hazardCenter.subtract(player.getPositionVector());
        double crossProduct = travelVec.x * playerToHazardVec.z - travelVec.z * playerToHazardVec.x;

        Vec3d sidestepVec = (crossProduct > 0) ? new Vec3d(travelVec.z, 0, -travelVec.x).normalize()
                : new Vec3d(-travelVec.z, 0, travelVec.x).normalize();
        double hazardWidth = Math.sqrt(Math.pow(maxX - minX, 2) + Math.pow(maxZ - minZ, 2));

        double sidestepDistance = (hazardWidth / 2) + AVOIDANCE_SIDESTEP_SAFETY_MARGIN;
        double forwardDistance = maxDepth + AVOIDANCE_FORWARD_SAFETY_MARGIN;

        Vec3d avoidancePoint = player.getPositionVector()
                .add(sidestepVec.scale(sidestepDistance))
                .add(travelVec.scale(forwardDistance));

        // [V4.8 路径清空检查] 在最终确定前，也检查这条规避路径
        if (!isWalkable(new BlockPos(avoidancePoint)) || !isPathClear(player.getPositionVector(), avoidancePoint)) {
            // 如果计算出的路径不理想，则执行一个更简单的侧移
            forcefulSidestep(player, travelVec);
            return;
        }

        this.rt().escapeDestination = avoidancePoint;
        this.rt().isMovingToPoint = true;
        EmbeddedNavigationHandler.INSTANCE.stop();
        ModUtils.DelayScheduler.instance.schedule(() -> {
            EmbeddedNavigationHandler.INSTANCE.startGotoXZ(avoidancePoint.x, avoidancePoint.z);
        }, COMMAND_DELAY_TICKS);

        rt().lastAvoidanceTime = System.currentTimeMillis();
    }

    private List<BlockPos> scanForwardArea(EntityPlayerSP player, Vec3d travelVec) {
        List<BlockPos> obstacles = new ArrayList<>();
        BlockPos center = player.getPosition();

        int scanRadiusXZ = 2;
        int scanRadiusY = 1;

        for (int dx = -scanRadiusXZ; dx <= scanRadiusXZ; dx++) {
            for (int dz = -scanRadiusXZ; dz <= scanRadiusXZ; dz++) {
                for (int dy = -scanRadiusY; dy <= scanRadiusY; dy++) {
                    BlockPos currentPos = center.add(dx, dy, dz);

                    Vec3d blockVec = new Vec3d(currentPos.getX() - player.posX, 0, currentPos.getZ() - player.posZ);
                    if (blockVec.dotProduct(new Vec3d(travelVec.x, 0, travelVec.z)) < 0) {
                        continue;
                    }

                    Block block = mc.world.getBlockState(currentPos).getBlock();
                    if (block != Blocks.AIR) {
                        obstacles.add(currentPos);
                    }
                }
            }
        }
        return obstacles;
    }

    private void forcefulSidestep(EntityPlayerSP player, Vec3d travelVec) {
        player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[自动追怪] " + TextFormatting.YELLOW + "未扫描到明确障碍，执行预防性侧移..."));

        // [V4.8 路径清空检查] 侧移时也检查路径
        Vec3d sidestepVecRight = new Vec3d(-travelVec.z, 0, travelVec.x).normalize();
        Vec3d avoidancePointRight = player.getPositionVector()
                .add(sidestepVecRight.scale(AVOIDANCE_SIDESTEP_SAFETY_MARGIN + 1.0));

        if (isWalkable(new BlockPos(avoidancePointRight))
                && isPathClear(player.getPositionVector(), avoidancePointRight)) {
            this.rt().escapeDestination = avoidancePointRight;
        } else {
            Vec3d sidestepVecLeft = new Vec3d(travelVec.z, 0, -travelVec.x).normalize();
            Vec3d avoidancePointLeft = player.getPositionVector()
                    .add(sidestepVecLeft.scale(AVOIDANCE_SIDESTEP_SAFETY_MARGIN + 1.0));
            if (isWalkable(new BlockPos(avoidancePointLeft))
                    && isPathClear(player.getPositionVector(), avoidancePointLeft)) {
                this.rt().escapeDestination = avoidancePointLeft;
            } else {
                // 如果两侧都不可行，则放弃本次规避
                player.sendMessage(new TextComponentString(
                        TextFormatting.AQUA + "[自动追怪] " + TextFormatting.RED + "两侧均无安全侧移路径，取消规避。"));
                return;
            }
        }

        this.rt().isMovingToPoint = true;
        rt().returningToCenterFromOutOfBounds = false;
        EmbeddedNavigationHandler.INSTANCE.stop();
        ModUtils.DelayScheduler.instance.schedule(() -> {
            EmbeddedNavigationHandler.INSTANCE.startGotoXZ(this.rt().escapeDestination.x, this.rt().escapeDestination.z);
        }, COMMAND_DELAY_TICKS);
        rt().lastAvoidanceTime = System.currentTimeMillis();
    }

    private BlockPos isNearHazard(EntityPlayerSP player, BlockPos playerPos, double distance) {
        int checkRadius = (int) Math.ceil(distance);
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int z = -checkRadius; z <= checkRadius; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (player.getDistanceSq(checkPos) <= distance * distance) {
                        Block block = mc.world.getBlockState(checkPos).getBlock();
                        if (block instanceof BlockVine || block instanceof BlockWeb) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }


    private boolean isOnSlabOrStair(BlockPos playerPos) {
        IBlockState blockStateDown = mc.world.getBlockState(playerPos.down());
        Block blockDown = blockStateDown.getBlock();
        IBlockState blockStateAtFeet = mc.world.getBlockState(playerPos);
        Block blockAtFeet = blockStateAtFeet.getBlock();

        return blockDown instanceof BlockSlab || blockDown instanceof BlockStairs || blockAtFeet instanceof BlockSlab
                || blockAtFeet instanceof BlockStairs;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }

        AutoFollowRule rule = rt().activeRule;
        if (rule == null || !rule.enabled) {
            return;
        }
        if (!rule.visualizeRange && !rule.visualizeLockChaseRadius) {
            return;
        }

        rule.updateBounds();
        rule.ensureReturnPoints();

        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        float partialTicks = event.getPartialTicks();
        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        double baseY = mc.player.posY - 1.0;
        double topY = mc.player.posY + 2.0;

        if (rule.visualizeRange) {
            AxisAlignedBB patrolOuter = new AxisAlignedBB(
                    Math.min(rule.point1.x, rule.point2.x),
                    baseY,
                    Math.min(rule.point1.z, rule.point2.z),
                    Math.max(rule.point1.x, rule.point2.x) + 1.0,
                    topY,
                    Math.max(rule.point1.z, rule.point2.z) + 1.0);

            renderWallShell(patrolOuter, viewerX, viewerY, viewerZ, 0.10F, 0.60F, 1.0F, 0.22F);

            if (rule.returnPoints != null) {
                for (int i = 0; i < rule.returnPoints.size(); i++) {
                    Point point = rule.returnPoints.get(i);
                    if (point == null) {
                        continue;
                    }
                    float alpha = i == rt().currentReturnPointIndex ? 0.34F : 0.22F;
                    double returnBaseY = point.hasY() ? point.y - 0.05D : baseY;
                    double returnTopY = point.hasY() ? point.y + 2.0D : topY;
                    AxisAlignedBB returnOuter = new AxisAlignedBB(
                            point.x - 2.0,
                            returnBaseY,
                            point.z - 2.0,
                            point.x + 3.0,
                            returnTopY,
                            point.z + 3.0);
                    renderWallShell(returnOuter, viewerX, viewerY, viewerZ, 0.20F, 1.0F, 0.35F, alpha);
                }
            }
        }

        if (rule.visualizeLockChaseRadius) {
            drawLockChaseBoundary(rule, viewerX, viewerY, viewerZ, baseY + 0.05D);
        }
    }

    private void drawLockChaseBoundary(AutoFollowRule rule, double viewerX, double viewerY, double viewerZ, double y) {
        if (rule == null) {
            return;
        }
        double radius = rule.lockChaseOutOfBoundsDistance > 0
                ? rule.lockChaseOutOfBoundsDistance
                : AutoFollowRule.DEFAULT_LOCK_CHASE_OUT_OF_BOUNDS_DISTANCE;
        if (radius <= 0.01D) {
            return;
        }

        int arcSegments = 14;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        appendArc(buffer, rule.maxX, y, rule.minZ, radius, -90.0D, 0.0D, arcSegments, viewerX, viewerY, viewerZ);
        appendArc(buffer, rule.maxX, y, rule.maxZ, radius, 0.0D, 90.0D, arcSegments, viewerX, viewerY, viewerZ);
        appendArc(buffer, rule.minX, y, rule.maxZ, radius, 90.0D, 180.0D, arcSegments, viewerX, viewerY, viewerZ);
        appendArc(buffer, rule.minX, y, rule.minZ, radius, 180.0D, 270.0D, arcSegments, viewerX, viewerY, viewerZ);
        appendArc(buffer, rule.maxX, y, rule.minZ, radius, 270.0D, 360.0D, arcSegments, viewerX, viewerY, viewerZ);

        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void appendArc(BufferBuilder buffer, double centerX, double y, double centerZ, double radius,
            double startDeg, double endDeg, int segments,
            double viewerX, double viewerY, double viewerZ) {
        for (int i = 0; i <= segments; i++) {
            double progress = i / (double) segments;
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * progress);
            double x = centerX + Math.cos(angle) * radius - viewerX;
            double z = centerZ + Math.sin(angle) * radius - viewerZ;
            buffer.pos(x, y - viewerY, z).color(1.0F, 0.78F, 0.28F, 0.90F).endVertex();
        }
    }

    private void renderWallShell(AxisAlignedBB outer, double viewerX, double viewerY, double viewerZ,
            float r, float g, float b, float alpha) {
        AxisAlignedBB inner = outer.grow(-1.0, 0.0, -1.0);
        if (inner.maxX <= inner.minX || inner.maxZ <= inner.minZ) {
            drawFilledAndOutline(outer, viewerX, viewerY, viewerZ, r, g, b, alpha);
            return;
        }

        AxisAlignedBB northWall = new AxisAlignedBB(outer.minX, outer.minY, outer.minZ, outer.maxX, outer.maxY,
                inner.minZ);
        AxisAlignedBB southWall = new AxisAlignedBB(outer.minX, outer.minY, inner.maxZ, outer.maxX, outer.maxY,
                outer.maxZ);
        AxisAlignedBB westWall = new AxisAlignedBB(outer.minX, outer.minY, inner.minZ, inner.minX, outer.maxY,
                inner.maxZ);
        AxisAlignedBB eastWall = new AxisAlignedBB(inner.maxX, outer.minY, inner.minZ, outer.maxX, outer.maxY,
                inner.maxZ);

        drawFilledAndOutline(northWall, viewerX, viewerY, viewerZ, r, g, b, alpha);
        drawFilledAndOutline(southWall, viewerX, viewerY, viewerZ, r, g, b, alpha);
        drawFilledAndOutline(westWall, viewerX, viewerY, viewerZ, r, g, b, alpha);
        drawFilledAndOutline(eastWall, viewerX, viewerY, viewerZ, r, g, b, alpha);
    }

    private void drawFilledAndOutline(AxisAlignedBB box, double viewerX, double viewerY, double viewerZ,
            float r, float g, float b, float alpha) {
        AxisAlignedBB renderBox = box.offset(-viewerX, -viewerY, -viewerZ).grow(0.001);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.5F);

        RenderGlobal.renderFilledBox(renderBox, r, g, b, alpha);
        RenderGlobal.drawSelectionBoundingBox(renderBox, r, g, b, Math.min(1.0F, alpha + 0.35F));

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
