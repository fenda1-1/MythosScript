package com.mythos.mythosScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mythos.mythosScriptMod.PerformanceMonitor;
import com.mythos.mythosScriptMod.config.DebugModule;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.path.DroppedPickupTarget;
import com.mythos.mythosScriptMod.path.LegacyActionRuntime;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.path.InventoryItemFilterExpressionEngine;
import com.mythos.mythosScriptMod.path.PathSequenceManager.ActionData;
import com.mythos.mythosScriptMod.path.PathSequenceManager.PathSequence;
import com.mythos.mythosScriptMod.path.PathSequenceManager.PathStep;
import com.mythos.mythosScriptMod.path.runtime.ScopedRuntimeVariables;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.PathEvent;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.listener.AbstractGameEventListener;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.listener.IEventBus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.mythos.mythosScriptMod.shadowbaritone.process.KillAuraOrbitProcess;
import com.mythos.mythosScriptMod.shadowbaritone.launch.mixins.AccessorEntityArrow;
import com.mythos.mythosScriptMod.shadowbaritone.utils.PathRenderer;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.JsonConfigCharsetCompat;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.utils.TickRangeSpec;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.EnumAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.lang.reflect.Type;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KillAuraHandler implements AbstractGameEventListener {

    public static final KillAuraHandler INSTANCE = new KillAuraHandler();
    public static final String ATTACK_MODE_NORMAL = "NORMAL";
    public static final String ATTACK_MODE_PACKET = "PACKET";
    public static final String ATTACK_MODE_TELEPORT = "TELEPORT";
    public static final String ATTACK_MODE_SEQUENCE = "SEQUENCE";
    public static final String ATTACK_MODE_MOUSE_CLICK = "MOUSE_CLICK";
    /** 首开即是 TP 攻击；普通近战改成显式选择。 */
    public static final String DEFAULT_ATTACK_MODE = ATTACK_MODE_TELEPORT;
    public static final String HUNT_MODE_OFF = "OFF";
    public static final String HUNT_MODE_APPROACH = "APPROACH";
    public static final String HUNT_MODE_FIXED_DISTANCE = "FIXED_DISTANCE";
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final Type NAME_FILTER_GROUP_LIST_TYPE = new TypeToken<List<NameFilterGroup>>() {
    }.getType();
    private static final Type HUNT_PICKUP_RULE_LIST_TYPE = new TypeToken<List<HuntPickupRule>>() {
    }.getType();
    private static final Type PRESET_LIST_TYPE = new TypeToken<List<KillAuraPreset>>() {
    }.getType();

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，
    // 共用静态字段时它们读到的是当前挂载会话的配置，挂载 nexus_test 会把后台 bot1 的
    // 普通模式改成 TP。下面的字段原来都是 KillAuraHandler 的静态字段，现统一经 cfg() 访问。
    public static final class AuraConfig {
        public boolean rotateToTarget = true;
        public boolean smoothRotation = true;
        public float smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
        public String smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
        public boolean rotateOnlyOnAttack = true;
        public boolean relockOnlyWhenNoCrosshairTarget = true;
        public boolean onlyAttackWhenLookingAtTarget = false;
        public boolean requireLineOfSight = false;
        public boolean throughWallAttack = false;
        public boolean targetHostile = true;
        public boolean targetPassive = false;
        public boolean targetPlayers = false;
        public boolean targetNpcs = false;
        public boolean targetEnderCrystal = false;
        /** 攻击区域 "minX,minY,minZ,maxX,maxY,maxZ"，留空即不限区域。
         *  用来把光环锁在某片场地里，避免顺手清掉路过的村庄和刷怪点。 */
        public String targetArea = "";
        public boolean onlyWeapon = false;
        public boolean focusSingleTarget = true;
        public boolean blockWhileAttacking = false;
        public boolean ignoreInvisible = true;
        public boolean enableNoCollision = true;
        public boolean enableAntiKnockback = true;
        public boolean enableFullBrightVision = false;
        public float fullBrightGamma = 1000.0F;
        public String attackMode = DEFAULT_ATTACK_MODE;
        public String attackSequenceName = "";
        // 各攻击方式各自保存的参数档案只由设置页读写；放在这里是因为 saveConfig 会整体重建 JSON，
        // 不随主配置一起写回的话，任何开关光环的保存都会把档案抹掉。
        public JsonObject attackModeProfiles = new JsonObject();
        public int attackSequenceDelayTicks = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS;
        public String attackSequenceDelayTicksSpec = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC;
        public float aimYawOffset = DEFAULT_AIM_YAW_OFFSET;
        public String aimYawOffsetSpec = DEFAULT_AIM_YAW_OFFSET_SPEC;
        public float aimPitchOffset = DEFAULT_AIM_PITCH_OFFSET;
        public String aimPitchOffsetSpec = DEFAULT_AIM_PITCH_OFFSET_SPEC;
        public String huntMode = HUNT_MODE_APPROACH;
        public boolean huntPickupItemsEnabled = false;
        public List<HuntPickupRule> huntPickupRules = new ArrayList<>();
        public float huntRadius = 8.0F;
        public float huntFixedDistance = 4.2F;
        public float huntUpRange = DEFAULT_HUNT_UP_RANGE;
        public float huntDownRange = DEFAULT_HUNT_DOWN_RANGE;
        public boolean huntOrbitEnabled = false;
        public boolean huntJumpOrbitEnabled = true;
        public int huntOrbitSamplePoints = DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        public boolean enableNameWhitelist = false;
        public boolean enableNameBlacklist = false;
        public List<String> nameWhitelist = new ArrayList<>();
        public List<String> nameBlacklist = new ArrayList<>();
        // 名称分组才是名单的来源；上面两个列表和两个开关由启用的分组合并得出，
        // 这样十几处目标匹配代码不用改，仍然只读合并后的白名单和黑名单。
        public List<NameFilterGroup> nameFilterGroups = new ArrayList<>();
        public float nearbyEntityScanRange = 10.0F;
        public final List<KillAuraPreset> presets = new ArrayList<>();
        public float attackRange = 4.2F;
        public float huntScoreRadiusWeight = DEFAULT_HUNT_SCORE_RADIUS_WEIGHT;
        public float huntScorePlayerDistanceWeight = DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT;
        public float huntScorePlayerPlaneWeight = DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT;
        public float huntScoreTargetHeightWeight = DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT;
        public float huntScoreAttackRangeWeight = DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT;
        public float huntScoreVisibilityWeight = DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT;
        public float huntScoreOpennessWeight = DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT;
        public float minAttackStrength = 0.92F;
        public float minTurnSpeed = 4.0F;
        public float maxTurnSpeed = 18.0F;
        public int minAttackIntervalTicks = 2;
        public int targetsPerAttack = 3;
        /** 只影响操作者自己的画面；服务器与其他玩家看到的始终是瞬移。 */
        public String teleportViewMode = TELEPORT_VIEW_AVATAR;
        public float teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
        public float teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        public int teleportActionsPerTick = DEFAULT_TELEPORT_ACTIONS_PER_TICK;
        public float teleportStepDistancePerAction = DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION;
        /** TP 模式下每次出手是否额外发一个 CPacketPlayer.Rotation 去瞄准。
         *  默认关：Rotation 也走 processPlayer、计入服务端的 i，
         *  出手密集时它会把 i 顶到 5 以上，让每 tick 的位移额度从 100*i 跌回 100，
         *  等于用一半的位移速度换一个服务端并不校验的朝向。 */
        public boolean teleportAttackRotationPackets = false;
        public int teleportAnchorMode = TELEPORT_ANCHOR_SELF;
        public double teleportAnchorX = 0.0D;
        public double teleportAnchorY = 64.0D;
        public double teleportAnchorZ = 0.0D;
        /** Nexus session username whose player is the live anchor (SESSION mode). */
        public String teleportAnchorSession = "";
        public int noDamageAttackLimit = DEFAULT_NO_DAMAGE_ATTACK_LIMIT;
    }

    private static final ConcurrentHashMap<String, AuraConfig> CONFIGS = new ConcurrentHashMap<>();

    /** 当前线程所属会话的配置；bot 线程取自己的，客户端线程取挂载中的会话。 */
    public static AuraConfig cfg() {
        String owner = ProfileManager.currentConfigOwner();
        AuraConfig config = CONFIGS.get(owner);
        return config != null ? config : loadOwnerConfig(owner, false);
    }

    // 不能用 computeIfAbsent：读盘过程中会反复调用 cfg()，ConcurrentHashMap 不允许递归更新。
    private static AuraConfig loadOwnerConfig(String owner, boolean reload) {
        synchronized (com.mythos.mythosScriptMod.system.ProfileManager.CONFIG_LOAD_LOCK) {
            AuraConfig existing = CONFIGS.get(owner);
            if (existing != null && !reload) {
                return existing;
            }
            AuraConfig config = new AuraConfig();
            CONFIGS.put(owner, config);
            ProfileManager.withConfigOwner(owner, (Runnable) KillAuraHandler::loadConfigValues);
            return config;
        }
    }


    // ------------------------------------------------------------------
    // Per-thread session context.  This class is NOT in NexusTransformer's
    // FIELD_CTX_CLASSES rewrite list, so a bare `mc.player`/`mc.world`/
    // `mc.playerController` read on a dedicated bot tick thread (nexus-bot-N)
    // returns the MOUNTED session's entities — the wrong player/world/
    // controller.  That is what made bot12's attack route through the mounted
    // world's PlayerControllerMP.  ctxPlayer()/ctxWorld()/ctxController()
    // resolve the thread-bound session's own fields; on the client thread
    // (and when Nexus is absent) they fall back to the real mc fields, so
    // mounted/single-player behavior is unchanged.  Non-session fields
    // (gameSettings, renderViewEntity, display, getConnection) stay on mc.
    // ------------------------------------------------------------------
    private static EntityPlayerSP ctxPlayer() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
    }
    private static World ctxWorld() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld();
    }
    private static net.minecraft.client.multiplayer.PlayerControllerMP ctxController() {
        return com.mythos.mythosScriptMod.mcp.NexusSessions.boundController();
    }
    public static final float MIN_SMOOTH_MAX_TURN_STEP = 0.5F;
    public static final float MAX_SMOOTH_MAX_TURN_STEP = 60.0F;
    public static final float DEFAULT_SMOOTH_MAX_TURN_STEP = 24.0F;
    public static final String DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC = "24-60";
    private static String cachedTargetAreaRaw;
    private static AxisAlignedBB cachedTargetAreaBounds;
    public static final int MIN_ATTACK_SEQUENCE_DELAY_TICKS = 0;
    public static final int MAX_ATTACK_SEQUENCE_DELAY_TICKS = 200;
    public static final int DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS = 1;
    public static final String DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC = "1-6";
    public static final float MIN_AIM_OFFSET = -30.0F;
    public static final float MAX_AIM_OFFSET = 30.0F;
    public static final float DEFAULT_AIM_OFFSET = 0.0F;
    public static final float DEFAULT_AIM_YAW_OFFSET = DEFAULT_AIM_OFFSET;
    public static final String DEFAULT_AIM_YAW_OFFSET_SPEC = "0";
    public static final float DEFAULT_AIM_PITCH_OFFSET = DEFAULT_AIM_OFFSET;
    public static final String DEFAULT_AIM_PITCH_OFFSET_SPEC = "0";
    public static final String HUNT_PICKUP_RULE_MODE_ALLOW = "ALLOW";
    public static final String HUNT_PICKUP_RULE_MODE_BLOCK = "BLOCK";
    public static final String HUNT_PICKUP_RARITY_COMMON = "common";
    public static final String HUNT_PICKUP_RARITY_UNCOMMON = "uncommon";
    public static final String HUNT_PICKUP_RARITY_RARE = "rare";
    public static final String HUNT_PICKUP_RARITY_EPIC = "epic";
    public static final float DEFAULT_HUNT_UP_RANGE = 5.0F;
    public static final float DEFAULT_HUNT_DOWN_RANGE = 5.0F;
    public static final int MIN_HUNT_ORBIT_SAMPLE_POINTS = 3;
    public static final int MAX_HUNT_ORBIT_SAMPLE_POINTS = 360;
    public static final int DEFAULT_HUNT_ORBIT_SAMPLE_POINTS = MAX_HUNT_ORBIT_SAMPLE_POINTS;

    public static final float DEFAULT_HUNT_SCORE_RADIUS_WEIGHT = 4.0F;
    public static final float DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT = 0.18F;
    public static final float DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT = 1.8F;
    public static final float DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT = 0.12F;
    public static final float DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT = 8.0F;
    public static final float DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT = 12.0F;
    public static final float DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT = 0.65F;

    /** 视角跟随瞬移：本地实体和相机都跟着 TP 走，最接近原版行为。 */
    public static final String TELEPORT_VIEW_FOLLOW = "FOLLOW";
    /** 锁定本体：本地实体一步不动，只把位置告诉服务器；相机因此完全静止。 */
    public static final String TELEPORT_VIEW_LOCK = "LOCK";
    /** 可控化身：相机挂在一个有碰撞的本地实体上，真实玩家跟着服务器瞬移且可见。 */
    public static final String TELEPORT_VIEW_AVATAR = "AVATAR";
    /** TP 路线允许的最大路点数，只防规划失控，不再限制每 tick 发包数。 */
    private static final int TELEPORT_ROUTE_MAX_WAYPOINTS = 320;
    public static final float MIN_TELEPORT_STEP_DISTANCE = 0.5F;
    public static final float MAX_TELEPORT_STEP_DISTANCE = 200.0F;
    /** 一站到下一站的距离上限。默认 8.0：AVATAR 一步跳 20 会超过服务端
     *  每拍 10 格的额度直接被判 moved too quickly 拉回起点（实测 perAction 7.0
     *  都会触发），8.0 是 pens 布局实测能跑满 ~50 格/s 且零回拉的值。 */
    public static final float DEFAULT_TELEPORT_STEP_DISTANCE = 8.0F;
    public static final float MIN_TELEPORT_STATION_ATTACK_RADIUS = 0.5F;
    public static final float MAX_TELEPORT_STATION_ATTACK_RADIUS = 200.0F;
    // 原版服务端对看不见的目标只认 3 格内的攻击，取 2.9 让隔墙出手也不被丢弃。
    public static final float DEFAULT_TELEPORT_STATION_ATTACK_RADIUS = 2.9F;
    /** 每个 bot tick 允许推进几个 TP 动作（一次位移或一次出手算一个）。
     *  上限 5 不是随便取的：服务端 NetHandlerPlayServer.processPlayer 按
     *  `d11 - d10 > 100 * i` 判 moved too quickly，i 是本 tick 收到的移动包数，
     *  **i > 5 会被强制按 1 算**，阈值反而跌回 10 格，再多发只会更早被拉回。 */
    public static final int MIN_TELEPORT_ACTIONS_PER_TICK = 1;
    public static final int MAX_TELEPORT_ACTIONS_PER_TICK = 5;
    public static final int DEFAULT_TELEPORT_ACTIONS_PER_TICK = 2;
    /** 单个 TP 动作最多挪几格。和「每 tick 动作数」是一对：服务端给一拍
     *  10 格的额度（i 被判 1 时），所以动作数 × 这个值必须 ≤ 10，
     *  否则必然撞 moved too quickly 并被 setPlayerLocation 拉回起点。 */
    public static final float MIN_TELEPORT_STEP_DISTANCE_PER_ACTION = 0.5F;
    public static final float MAX_TELEPORT_STEP_DISTANCE_PER_ACTION = 10.0F;
    public static final float DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION = 4.8F;
    /**
     * Where the teleport hunt treats itself as standing. SELF keeps the legacy
     * behaviour (the bot's own feet at plan time); FIXED and SESSION decouple
     * the anchor from the bot, so the bot can roam while the attack radius and
     * the return destination stay pinned to a spot the operator controls.
     */
    public static final int TELEPORT_ANCHOR_SELF = 0;
    public static final int TELEPORT_ANCHOR_FIXED = 1;
    public static final int TELEPORT_ANCHOR_SESSION = 2;
    /** Stay put for this long after the last attack before walking home. */
    private static final long TELEPORT_ANCHOR_IDLE_NANOS = 800_000_000L;
    /** 连续这么久没打出攻击，就认为这一波杀戮结束，虚拟位置回本体。 */
    private static final long TELEPORT_VIRTUAL_STAY_NANOS = 3_000_000_000L;
    /** Squared arrival tolerance for the anchor return walk. */
    private static final double TELEPORT_ANCHOR_ARRIVE_SQ = 1.0D;
    /** 关杀戮后回视角的总时长上限（client tick）；超时按旧行为直接收尾，避免一直悬在半路。 */
    private static final int TELEPORT_VIEW_RETURN_MAX_TICKS = 600;
    /** 连续这么多 tick 找不到能走的一步就放弃回程，退回旧行为。 */
    private static final int TELEPORT_VIEW_RETURN_BLOCKED_TICKS = 20;
    /** 化身走开超过 1.5 格才重新找路；每步都找路会把 A* 预算花在原地。 */
    private static final double TELEPORT_VIEW_RETURN_REROUTE_SQ = 2.25D;
    private static final int TELEPORT_VIEW_RETURN_REROUTE_INTERVAL_TICKS = 5;
    /** TP 攻击描边框的停留时长。 */
    private static final long TELEPORT_ATTACK_FEEDBACK_NANOS = 2_000_000_000L;
    public static final int DEFAULT_NO_DAMAGE_ATTACK_LIMIT = 0;
    public static final int MAX_NO_DAMAGE_ATTACK_LIMIT = 100;

    private static final int HUNT_GOTO_INTERVAL_TICKS = 6;
    /**
     * navActive while the feet haven't moved for this many hunt ticks means
     * baritone is wedged (executor paused on "edge of loaded chunks", a calc
     * stuck in the shared pool queue, a silently dropped setGoalAndPath, …).
     * The normal stallchk only rescues when baritone reports idle — it can
     * wait forever on a path that reports isPathing forever. ~3.5s is long
     * enough to ride out legitimate long movements / fall arcs.
     */
    private static final int HUNT_STUCK_RESET_TICKS = 70;
    /** Feet displacement below this counts as "standing still". */
    private static final double HUNT_STUCK_FEET_EPS_SQ = 0.0004D; // (0.02)^2
    /** Min ticks between hunt-solve submits; 10 = 0.5s at 20tps. */
    private static final int HUNT_SOLVE_MIN_INTERVAL_TICKS = 10;
    private static final double HUNT_GOTO_MOVE_THRESHOLD_SQ = 1.0D;
    /** Target must drift this far (sq) from the CURRENT goal before we burn a
     *  redispatch — villagers pace back and forth under a block, and every
     *  re-goal pauses the executor waiting on a recalc. */
    private static final double HUNT_GOAL_REDISPATCH_DIST_SQ = 6.25D; // 2.5 blocks
    private static final double TARGET_SWITCH_DISTANCE_HYSTERESIS_SQ = 1.0D;
    private static final double HUNT_FIXED_DISTANCE_TOLERANCE = 0.30D;
    private static final int HUNT_ORBIT_PROCESS_REQUEST_INTERVAL_TICKS = 2;
    private static final double HUNT_ORBIT_MAX_ENTRY_DISTANCE_BUFFER = 4.0D;
    private static final double HUNT_ORBIT_MAX_ENTRY_VERTICAL_DELTA = 3.5D;
    private static final double HUNT_CONTINUOUS_ORBIT_ENTRY_BUFFER = 1.25D;
    private static final double HUNT_CONTINUOUS_ORBIT_EXIT_BUFFER = 2.25D;
    private static final double HUNT_CONTINUOUS_ORBIT_LOOP_ENTRY_MAX_DISTANCE = 0.90D;
    private static final double HUNT_ORBIT_ENTRY_RADIUS_BAND = 0.45D;
    private static final int HUNT_ORBIT_ENTRY_SAFE_SEARCH_RADIUS = 1;
    private static final double HUNT_ORBIT_ENTRY_POINT_TOLERANCE = 0.85D;
    // Match the rule manager's pickup refresh cadence. Its navigation bridge
    // performs the final command throttling without force-cancelling a route.
    private static final int HUNT_PICKUP_GOTO_INTERVAL_TICKS = 5;
    private static final int HUNT_PICKUP_AIRBORNE_GOTO_INTERVAL_TICKS = 5;
    private static final double HUNT_PICKUP_AIRBORNE_LEAD_TICKS = 6.0D;
    private static final int HUNT_PICKUP_SEARCH_INTERVAL_TICKS = 3;
    private static final int HUNT_PICKUP_NAVIGATION_STALL_TICKS = 60;
    private static final int HUNT_PICKUP_RETRY_WAIT_TICKS = 10;
    private static final double HUNT_PICKUP_PROGRESS_EPSILON_SQ = 0.0025D;
    private static final double HUNT_APPROACH_MIN_STAND_RADIUS = 0.85D;
    private static final double HUNT_APPROACH_TARGET_BUFFER = 0.35D;
    private static final double HUNT_GOAL_REACHED_TOLERANCE_SQ = 0.36D;
    private static final double HUNT_MIN_MEANINGFUL_GOAL_DISTANCE_SQ = 0.64D;
    private static final double HUNT_MIN_APPROACH_PROGRESS = 0.08D;
    private static final double HUNT_NAVIGATION_RADIUS_SAMPLE_STEP = 0.75D;
    private static final double HUNT_NAVIGATION_ANGLE_SAMPLE_STEP_RADIANS = Math.toRadians(18.0D);
    private static final int HUNT_NAVIGATION_ANGLE_SAMPLE_PAIRS = 10;
    private static final int[] HUNT_STAND_Y_OFFSETS = new int[] { 0, -1, 1, -2, 2, -3, 3 };
    private static final int HUNT_STAND_CANDIDATE_LIMIT = 4;
    private static final double TELEPORT_ATTACK_REACH = 2.85D;
    private static final int TELEPORT_ATTACK_CORRECTION_WINDOW_TICKS = 10;
    private static final int TELEPORT_ATTACK_MAX_CORRECTIONS = 2;
    private static final double TELEPORT_ATTACK_SAFE_ANGLE_STEP_RADIANS = Math.toRadians(15.0D);
    private static final int TELEPORT_ATTACK_SAFE_ANGLE_STEPS = 12;
    private static final double TELEPORT_ATTACK_SAFE_RADIUS_STEP = 0.4D;
    private static final double TELEPORT_ATTACK_MAX_RADIUS_ADJUST = 1.4D;
    private static final double TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ = 0.09D;
    private static final double TELEPORT_ATTACK_WAYPOINT_EPSILON_SQ = 1.0E-8D;
    private static final double TELEPORT_ATTACK_PATH_SAMPLE_DISTANCE = 0.45D;
    private static final double TELEPORT_ATTACK_PATH_GOAL_DISTANCE_SQ = 2.25D;
    private static final long TELEPORT_ATTACK_PATH_TIME_BUDGET_NANOS = 6_000_000L;
    /** The A* grid expands one block at a time, so a 40-block leg needs hundreds
     *  of expansions; 6ms only ever finished the short ones. */
    private static final long TELEPORT_GROUNDED_LEG_BUDGET_NANOS = 35_000_000L;
    private static final int TELEPORT_ATTACK_PATH_FAILURE_COOLDOWN_TICKS = 40;
    private static final double TELEPORT_ATTACK_PATH_WEIGHT = 1.0D;
    private static final int TELEPORT_ATTACK_PATH_MIN_VERTICAL_SEARCH = 32;
    private static final int TELEPORT_ATTACK_PATH_MAX_VERTICAL_SEARCH = 128;
    private static final int TELEPORT_ATTACK_PATH_MIN_LATERAL_SEARCH = 10;
    private static final int TELEPORT_ATTACK_PATH_MAX_LATERAL_SEARCH = 32;
    private static final int TELEPORT_ATTACK_PATH_MIN_EXPANSIONS = 2500;
    private static final int TELEPORT_ATTACK_PATH_MAX_EXPANSIONS = 8000;
    private static final int[][] TELEPORT_ATTACK_PATH_DIRECTIONS = new int[][] {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }
    };
    private static final int TELEPORT_TARGET_SCAN_PER_TICK = 32;
    private static final int TELEPORT_TARGET_CACHE_LIMIT = 64;
    private static final int TELEPORT_TARGET_HISTORY_LIMIT = 512;
    private static final int TELEPORT_TARGET_PLAN_LIMIT = 16;
    private static final int TELEPORT_STATION_SEED_LIMIT = 8;
    private static final int TELEPORT_STATION_CANDIDATE_LIMIT = 4;
    private static final int TELEPORT_STATION_LIMIT = 4;
    /** 每次出手的保底群攻名额。单只目标有 20 tick 无敌帧，能打多快只取决于
     *  同时有多少只目标在身边；targetsPerAttack 想开更大时按它放宽（见
     *  teleportBurstHitLimit），这里只是不让默认配置比以前更窄。 */
    private static final int TELEPORT_ATTACK_MAX_HITS_PER_TICK = 8;
    /** 一个 tour 最多跑多少个动作。ageTicks 按动作计，所以要乘每拍动作数
     *  才等价于「12 秒」这个原来的时间预算。 */
    private static final int TELEPORT_ATTACK_MAX_EXECUTION_BOT_TICKS = 240;
    /** 一次 tour 里最多原地续打几圈。续打是为了不把动作额度浪费在等规划上，
     *  但无上限时 bot 会钉在同一站直到超时，六个围栏只覆盖到身边那几只。 */
    private static final int TELEPORT_TOUR_MAX_REARMS = 3;
    private static final int TELEPORT_STATION_POSITION_PROBE_LIMIT = 8;
    private static final int TELEPORT_ROUTE_COLLISION_PROBE_LIMIT = 8;
    private static final int TELEPORT_ROUTE_LOADED_QUERY_LIMIT = 32;
    /** Must cover a full seed round (TELEPORT_STATION_CANDIDATE_LIMIT ×
     *  TELEPORT_PLANNING_QUERIES_PER_SEED); otherwise the last seeds can never
     *  plan, every pass reports a budget failure, and reachable targets get
     *  deferred alongside the unreachable ones. */
    private static final int TELEPORT_PLANNING_WORLD_QUERY_LIMIT = 420;
    /** Must cover the whole per-seed chain: station probing (6) + straight leg
     *  (17) + the three raised-cruise attempts (3×17 = 51). Anything less makes
     *  the raised fallback unreachable by arithmetic, not by geometry. */
    private static final int TELEPORT_PLANNING_QUERIES_PER_SEED = 96;
    private static final int TELEPORT_RECOVERY_ROUTE_LIMIT = 192;
    private static final int TELEPORT_PLANNING_FAILURE_BACKOFF_TICKS = 3;
    private static final int TELEPORT_TARGET_BUDGET_FAILURE_BACKOFF_TICKS = 6;
    private static final int TELEPORT_TARGET_ROUTE_FAILURE_BACKOFF_TICKS = 20;
    /** A target that keeps failing station selection is unreachable, not unlucky:
     *  the terrain does not change between passes. Back off long enough that it
     *  stops competing with reachable targets. */
    private static final int TELEPORT_UNREACHABLE_TARGET_BACKOFF_TICKS = 200;
    /** Squared distance a target must move before a "no spot to stand" verdict is
     *  considered stale; that verdict described where the target used to be. */
    private static final double TELEPORT_BACKOFF_STALE_DISTANCE_SQ = 4.0D;
    private static final int TELEPORT_UNREACHABLE_FAILURE_STREAK = 3;
    /** A* is the only fallback now, and a grid search over a 40-block leg needs
     *  real time. Planning only happens while no tour is active, a few times a
     *  second at most, so tens of milliseconds here is affordable. */
    private static final long TELEPORT_TOUR_PLANNING_BUDGET_NANOS = 40_000_000L;
    private static final long TELEPORT_DEBUG_PLANNING_BUDGET_NANOS = 50_000_000L;
    private static final int TARGET_SWITCH_SMOOTH_TICKS = 12;
    private static final float SMOOTH_ATTACK_READY_YAW_DEGREES = 28.0F;
    private static final float SMOOTH_ATTACK_READY_PITCH_DEGREES = 40.0F;
    private static final double ADVANCED_AIM_MIN_LEAD_TICKS = 0.25D;
    private static final double ADVANCED_AIM_MAX_LEAD_TICKS = 2.45D;
    private static final double ADVANCED_AIM_MAX_PLAYER_LEAD_TICKS = 0.85D;
    private static final float ADVANCED_OVERSHOOT_MAX_YAW = 2.15F;
    private static final float ADVANCED_OVERSHOOT_MAX_ATTACK_YAW = 1.25F;
    private static final float ADVANCED_OVERSHOOT_MAX_PITCH = 0.95F;
    private static final float NO_DAMAGE_HEALTH_EPSILON = 0.001F;
    private static final int NO_DAMAGE_OBSERVATION_DELAY_TICKS = 1;
    private static final int NO_DAMAGE_MAX_TRACKED_TARGETS = 256;
    private static final int NO_DAMAGE_MAX_EXCLUDED_TARGETS = 512;
    private static final int HUNT_UNREACHABLE_MAX_TRACKED_TARGETS = 256;
    private static final int HUNT_UNREACHABLE_MAX_FAILED_GOALS_PER_TARGET = 16;
    private static final int HUNT_UNREACHABLE_FAILS_BEFORE_TEMP_EXCLUDE = 3;
    private static final int HUNT_UNREACHABLE_TEMP_EXCLUDE_TICKS = 100;
    private static final double HUNT_UNREACHABLE_TARGET_RESET_DISTANCE_SQ = 4.0D;

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    /**
     * True while a teleport tour owns the server-side position. Vanilla walking
     * packets would otherwise drag the server back to the local body every tick.
     */
    public static boolean shouldSuppressVanillaPosition() {
        if (!TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)) {
            return false;
        }
        RuntimeState state = rt();
        // 回程期间同样要挡：原版每 20 tick 的保活位置包会把服务器一步拽回本体。
        return (state.activeTeleportTourExecution != null || state.teleportViewReturn != null)
                && !state.teleport.sendingTeleportPosition;
    }

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static final class RuntimeState {
        // Entity IDs and tick deadlines belong to one Nexus world/session.
        private final KillAuraTeleportState teleport = new KillAuraTeleportState();
        private final Map<Integer, NoDamageAttackTracker> noDamageAttackTrackers = new LinkedHashMap<>();
        private final Set<Integer> noDamageExcludedEntityIds = new LinkedHashSet<>();
        // Runtime switches boxed from former public static fields so each
        // NexusSessions tick owner gets an isolated set (multi-bot isolation).
        public boolean enabled = false;
        public boolean aimOnlyMode = false;
        public boolean huntEnabled = true;
        public boolean visualizeHuntRadius = false;
        public boolean disableOnDisconnect = true;
        // Per-session MCP runtime override state. Boxed into RuntimeState so
        // concurrent aura sessions on different bots do not share the static
        // snapshot / countdown and cannot cross-write each other's fields.
        private JsonObject mcpOriginalFields;
        private int mcpRemainingTicks;
        /** Per-owner live values for the public static config fields. Static
         *  fields swap through MythosStateSwap's group stash on every
         *  mount/dismount/withStatics, which stomps values written via
         *  beginMcpRuntime; holding a per-owner mirror here survives swaps. */
        private final java.util.Map<java.lang.reflect.Field, Object> staticOverrides = new java.util.HashMap<>();
        private int attackCooldownTicks = 0;
        /** Wall-clock attack-interval deadline — same throttle-stretch problem
         *  as sequenceCooldownUntilMillis. */
        private long attackCooldownUntilMillis = 0L;
        private EntityPlayerSP blockingPlayer;
        private ItemStack blockingStack = ItemStack.EMPTY;
        private EnumHand blockingHand;
        private int blockingHotbarSlot;
        private Entity lastBlockingTarget;
        private int lastBlockingAttackTick;
        private EntityPlayerSP blockingAttemptPlayer;
        private int nextBlockingAttemptTick;
        private volatile Rotation blockingServerRotation;
        private int sequenceCooldownTicks = 0;
        /** Wall-clock cooldown deadline — bot sessions tick at a throttled
         *  rate, so a tick-count cooldown would stretch the attack-sequence
         *  delay by 1/rate. 0 = not cooling down. */
        private long sequenceCooldownUntilMillis = 0L;
        private int currentTargetEntityId = -1;
        private int lastAimTargetEntityId = Integer.MIN_VALUE;
        private int targetSwitchSmoothTicks = 0;
        private int lastSmoothTurnLimitTick = Integer.MIN_VALUE;
        private float smoothYawTurnUsedThisTick = 0.0F;
        private float smoothPitchTurnUsedThisTick = 0.0F;
        private int visualRotationCacheTick = Integer.MIN_VALUE;
        private int visualRotationCacheTargetEntityId = Integer.MIN_VALUE;
        private float visualRotationCacheSourceYaw = 0.0F;
        private float visualRotationCacheSourcePitch = 0.0F;
        private Rotation visualRotationCache = null;
        private int aimOffsetSampleTick = Integer.MIN_VALUE;
        private int aimOffsetSampleTargetEntityId = Integer.MIN_VALUE;
        private String aimOffsetSampleYawSpec = "";
        private String aimOffsetSamplePitchSpec = "";
        private float sampledAimYawOffset = 0.0F;
        private float sampledAimPitchOffset = 0.0F;
        private boolean huntNavigationActive = false;
        /** Feet position + hunt tick of the last observed movement — the
         *  HUNT_STUCK watchdog: PathExecutor's clearKeys pauses (edge-of-loaded
         *  -chunks, best-path-backtrack) return early every tick before the
         *  movement timeout accumulates, so a paused bot can sit isPathing=true
         *  forever while navActive stays true. Standing still too long → hard
         *  reset nav and re-dispatch a fresh goal. */
        private double lastHuntFeetX = Double.NaN;
        private double lastHuntFeetZ = Double.NaN;
        private int lastHuntMoveTick = -99999;
        /** Logical per-owner tick counter for hunt nav gating. A background
         *  bot's player.ticksExisted never advances (the session entity isn't
         *  added to loadedEntityList / isn't world-ticked), which froze the
         *  HUNT_GOTO_INTERVAL re-dispatch and unreachable-exclusion clocks at
         *  zero — hunt stalled the first time a path failed. */
        private int huntLogicalTick = 0;
        private int lastHuntGotoTick = -99999;
        private int lastHuntTargetEntityId = Integer.MIN_VALUE;
        private double lastHuntTargetX = 0.0D;
        private double lastHuntTargetZ = 0.0D;
        private double lastHuntGoalX = Double.NaN;
        private double lastHuntGoalY = Double.NaN;
        private double lastHuntGoalZ = Double.NaN;
        /** Whether the cached hunt goal was sent as a Y-specific GoalBlock. */
        private boolean lastHuntGoalUsesY = false;
        private boolean huntPickupNavigationActive = false;
        private int lastHuntPickupGotoTick = -99999;
        private int lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
        private int huntPickupReachedEntityId = Integer.MIN_VALUE;
        private int lastHuntPickupSearchTick = -99999;
        private int lastHuntPickupSearchTargetEntityId = Integer.MIN_VALUE;
        private boolean lastHuntPickupSearchFound = false;
        private int huntPickupLastProgressTick = -99999;
        private double huntPickupLastDistanceSq = Double.MAX_VALUE;
        private int huntPickupRetryAfterTick = -99999;
        private int lastOrbitProcessRequestTick = -99999;
        private int lastOrbitProcessTargetEntityId = Integer.MIN_VALUE;
        private double lastOrbitProcessRequestedRadius = Double.NaN;
        private double lastSafeMotionX = 0.0D;
        private double lastSafeMotionY = 0.0D;
        private double lastSafeMotionZ = 0.0D;
        private boolean fullBrightApplied = false;
        private float previousGammaSetting = 1.0F;
        private IEventBus registeredBaritoneEventBus = null;
        private TeleportAttackPlan activeTeleportAttackPlan = null;
        private TeleportAttackPlan activeTeleportCorrectionPlan = null;
        private TeleportReturnPlan pendingTeleportReturnPlan = null;
        private int pendingTeleportReturnTicks = 0;
        private TeleportTourExecution activeTeleportTourExecution = null;
        /** 关杀戮后正在把服务器位置送回视角处；null 表示没有回程。 */
        private TeleportViewReturn teleportViewReturn = null;
        /** Paces the anchor-return walk at network cadence, same as a tour. */
        private final KillAuraTeleportRouteClock anchorReturnClock = new KillAuraTeleportRouteClock();
        private int lastTeleportCorrectionTick = Integer.MIN_VALUE;
        private int teleportTargetScanCursor = 0;
        private int teleportCrystalScanCursor = 0;
        private int teleportStationAlternativeCursor = 0;
        private int teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
        private int lastHuntScoreDebugTick = Integer.MIN_VALUE;
        private final Map<Integer, HuntUnreachableTracker> huntUnreachableTrackers = new LinkedHashMap<>();
        private final List<HuntScoreDebugEntry> huntScoreDebugEntries = new ArrayList<>();
        private int areaHuntControlTicks = 0;
        /** In-flight async hunt-destination solve; non-null while a worker computes. */
        private volatile Future<double[]> pendingHuntSolve = null;
        /** Last tick a hunt solve was submitted; throttles re-submission so a
         *  moving target can't enqueue a new ~350-block scan every tick. */
        // Long.MIN_VALUE-style sentinel can't be used on an int (nowTick -
        // Integer.MIN_VALUE overflows negative → cooldownOk false forever →
        // the async stand-point solve is never submitted). Start at a large
        // negative-but-safe offset instead.
        private int lastHuntSolveSubmitTick = -1_000_000_000;
        /** Latest solved hunt destination [x,y,z] written by the worker, consumed on the tick thread. */
        private volatile double[] pendingHuntDestination = null;
        /** Entity id the pendingHuntDestination was solved for; stale results are dropped. */
        private volatile int pendingHuntTargetEntityId = Integer.MIN_VALUE;
    }

    /** Published only after an attack packet is sent; never confuses a planned target with a hit. */
    private static final class TeleportAttackFeedback {
        private final EntityPlayerSP player;
        private final int entityId;
        private final java.util.UUID targetUuid;
        private final String name;
        private final long sentNanos;

        private TeleportAttackFeedback(EntityPlayerSP player, EntityLivingBase target, long sentNanos) {
            this.player = player;
            this.entityId = target.getEntityId();
            this.targetUuid = target.getUniqueID();
            this.name = target.getName();
            this.sentNanos = sentNanos;
        }
    }

    /**
     * 关闭杀戮后，把服务器端位置按 TP 参数一步步送回操作者视角所在处。
     *
     * <p>为什么不能直接收尾：LOCK 下本体（镜头）没动、服务器却停在怪堆里，直接停发会让
     * 下一个原版位置包一次跳几十格，被服务器判 moved too quickly 拉回去，镜头跟着本体被拽到怪堆；
     * AVATAR 下本体本来就在怪堆里、镜头在化身上，直接拆化身就是镜头瞬间切回本体。
     * 两种情况操作者看到的都是「人被传回真实位置」，所以要先把位置送到视角处再收尾。
     */
    private static final class TeleportViewReturn {
        /** true：本体走向化身（AVATAR）；false：服务器虚拟位置走回本体（LOCK）。 */
        private final boolean avatar;
        private final World world;
        private List<Vec3d> route = Collections.emptyList();
        private int routeIndex;
        private Vec3d routeGoal;
        private int routeTick = Integer.MIN_VALUE;
        private int ageTicks;
        private int blockedTicks;

        private TeleportViewReturn(boolean avatar, World world) {
            this.avatar = avatar;
            this.world = world;
        }
    }

    /** Mutable TP bookkeeping isolated to one Nexus tick owner. */
    private static final class KillAuraTeleportState {
        private final List<String> teleportDebugBuffer = new ArrayList<>();
        private final LinkedHashSet<Integer> teleportTargetCandidateEntityIds = new LinkedHashSet<>();
        private final Map<Integer, Integer> teleportLastAttackTicks = new LinkedHashMap<>();
        private final Map<Integer, Integer> teleportTargetRetryAfterTicks = new LinkedHashMap<>();
        /** Consecutive planning failures per target; drives the long backoff that
         *  keeps unreachable targets from eating every planning pass. */
        private final Map<Integer, Integer> teleportTargetFailureCounts = new LinkedHashMap<>();
        /** Where each backed-off target stood when the verdict was made. */
        private final Map<Integer, net.minecraft.util.math.Vec3d> teleportTargetRetryPositions =
                new LinkedHashMap<>();
        /** Authoritative backoff deadline in wall-clock millis. logicalTick resets on
         *  mount/respawn, so tick-based deadlines can be astronomically far away. */
        private final Map<Integer, Long> teleportTargetRetryUntilMillis = new LinkedHashMap<>();
        private final KillAuraTeleportTiming timing = new KillAuraTeleportTiming();
        private ItemStack lastAttackWeapon = ItemStack.EMPTY;
        private volatile TeleportAttackFeedback lastAttack;
        /** 每只最近被打过的目标各留一份反馈：群攻一拍打好几只，只留最后一只就只能画一个框。
         *  渲染线程读、bot 线程写，所以用并发 map。 */
        private final Map<Integer, TeleportAttackFeedback> recentAttacks = new ConcurrentHashMap<>();
        private int lastAttackTick = Integer.MIN_VALUE;
        private int logicalTick;
        private int teleportPacketBudgetTick = Integer.MIN_VALUE;
        private int teleportPacketsUsedThisTick;
        /** Set only while the tour itself is emitting a position packet, so the
         *  vanilla-packet filter can tell ours apart from the walking stream. */
        private boolean sendingTeleportPosition;
        /** Server-side position. Non-null means we are away from the body: the tour
         *  keeps chaining between targets from here instead of walking home between
         *  every hit. Cleared once a wave of fighting ends. */
        private Vec3d virtualPosition;
        /** A single plan pass gets one A* attempt. Several seeds each running a
         *  full search would stack their time limits into one long client stall. */
        private boolean astarAttemptedThisPlan;
        /** Rate diagnostics: which gate stops a teleport attack. Counted, not logged,
         *  so a slow aura can be diagnosed without switching on per-attack logging. */
        private long attackSends;
        private long attackSkipsInterval;
        private long attackSkipsNotReady;
        private long attackSkipsStrength;
        private long plansExecuted;
        /** burst 的候选池总和与 burst 次数：用来验证加大站点半径到底多拉进了几只目标，
         *  而不是只多发了空包（t4 消融矩阵要读这两个值）。 */
        private long teleportMeleeCandidates;
        private long teleportBurstTicks;
        /** 主 tick 最近一次 findTargets 的结果，供 fast hook 在「目标全在近战内、
         *  不需要 tour」时直接复用 burst —— 不缓存的话 fast-path 只能等被自适应
         *  节流的主 tick，实测 ~4 burst/s 而不是 tour 的 ~40 burst/s。 */
        private volatile List<EntityLivingBase> directTargets;
        /** Why the running tour was thrown away — the usual reason a wave stops after one hit. */
        private final Map<String, Integer> teleportEndReasons = new LinkedHashMap<>();

        private int currentTick() {
            return logicalTick;
        }

        private void advanceTick() {
            if (logicalTick < Integer.MAX_VALUE) {
                logicalTick++;
            }
        }

        private void syncPacketBudget() {
            if (teleportPacketBudgetTick != logicalTick) {
                teleportPacketBudgetTick = logicalTick;
                teleportPacketsUsedThisTick = 0;
            }
        }

        private void reservePackets(int count) {
            syncPacketBudget();
            teleportPacketsUsedThisTick += count;
        }

        private void recordPackets(int count) {
            syncPacketBudget();
            teleportPacketsUsedThisTick = Math.min(Integer.MAX_VALUE - count,
                    teleportPacketsUsedThisTick) + count;
        }

        private void reset() {
            timing.reset();
            lastAttackWeapon = ItemStack.EMPTY;
            lastAttack = null;
            recentAttacks.clear();
            lastAttackTick = Integer.MIN_VALUE;
            virtualPosition = null;
            teleportDebugBuffer.clear();
            teleportTargetCandidateEntityIds.clear();
            teleportLastAttackTicks.clear();
            teleportTargetRetryAfterTicks.clear();
            teleportPacketBudgetTick = Integer.MIN_VALUE;
            teleportPacketsUsedThisTick = 0;
            attackSends = 0L;
            attackSkipsInterval = 0L;
            attackSkipsNotReady = 0L;
            attackSkipsStrength = 0L;
            plansExecuted = 0L;
            teleportMeleeCandidates = 0L;
            teleportBurstTicks = 0L;
            directTargets = null;
            teleportEndReasons.clear();
        }
    }

    private final AttackSequenceExecutor attackSequenceExecutor = new AttackSequenceExecutor();
    private final HuntOrbitController huntOrbitController = new HuntOrbitController();
    /** Per-owner workers for the expensive hunt stand-point solve so the client
     *  render thread never runs the ~350-block/rayTrace scan inline. One worker
     *  per owner keeps each bot's solves serialized (results are polled next
     *  tick) while bots no longer queue behind each other on a single thread. */
    private static final int HUNT_SOLVER_MAX_WORKERS = 16;
    private static final Map<String, ExecutorService> HUNT_SOLVERS = new ConcurrentHashMap<>();
    /** Marks a hunt-solver worker thread and carries the session it is bound to. */
    private static final ThreadLocal<Object> HUNT_WORKER_SESSION = new ThreadLocal<>();
    private static ExecutorService huntSolverFor(String owner) {
        String key = owner == null ? "" : owner;
        return HUNT_SOLVERS.computeIfAbsent(key, k -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mythos-hunt-solver-" + (k.isEmpty() ? "fg" : k));
            t.setDaemon(true);
            return t;
        }));
    }
    private static void shrinkHuntSolvers() {
        // Bound by session count + foreground so a long-lived JVM doesn't keep
        // workers for sessions that were unmounted long ago.
        while (HUNT_SOLVERS.size() > HUNT_SOLVER_MAX_WORKERS) {
            Iterator<Map.Entry<String, ExecutorService>> it = HUNT_SOLVERS.entrySet().iterator();
            if (!it.hasNext()) break;
            Map.Entry<String, ExecutorService> e = it.next();
            it.remove();
            e.getValue().shutdownNow();
        }
    }
    private static final class HuntUnreachableTracker {
        private double lastTargetX;
        private double lastTargetY;
        private double lastTargetZ;
        private int failedGoalCount;
        private int excludeUntilTick;
        private final LinkedHashSet<Long> failedGoalKeys = new LinkedHashSet<>();

        private HuntUnreachableTracker(EntityLivingBase target) {
            refreshTargetPosition(target);
        }

        private void refreshTargetPosition(EntityLivingBase target) {
            if (target == null) {
                return;
            }
            this.lastTargetX = target.posX;
            this.lastTargetY = target.posY;
            this.lastTargetZ = target.posZ;
        }

        private void resetFailures(EntityLivingBase target) {
            refreshTargetPosition(target);
            this.failedGoalCount = 0;
            this.excludeUntilTick = 0;
            this.failedGoalKeys.clear();
        }
    }

    private static final class HuntPickupRuleDecision {
        private final boolean allowed;
        private final int priority;

        private HuntPickupRuleDecision(boolean allowed, int priority) {
            this.allowed = allowed;
            this.priority = priority;
        }
    }

    public static class HuntPickupRule {
        public String name = "";
        public String category = "默认";
        public boolean enabled = true;
        public String mode = HUNT_PICKUP_RULE_MODE_ALLOW;
        public String nameKeyword = "";
        public String itemIdKeyword = "";
        public List<String> requiredNbtTags = new ArrayList<>();
        public List<String> rarityFilters = new ArrayList<>();
        public List<String> itemFilterExpressions = new ArrayList<>();
        public float maxDistance = 0.0F;
        public int priority = 0;

        public HuntPickupRule() {
        }

        public HuntPickupRule(HuntPickupRule other) {
            if (other == null) {
                return;
            }
            this.name = other.name == null ? "" : other.name;
            this.category = other.category == null ? "默认" : other.category;
            this.enabled = other.enabled;
            this.mode = other.mode == null ? HUNT_PICKUP_RULE_MODE_ALLOW : other.mode;
            this.nameKeyword = other.nameKeyword == null ? "" : other.nameKeyword;
            this.itemIdKeyword = other.itemIdKeyword == null ? "" : other.itemIdKeyword;
            this.requiredNbtTags = new ArrayList<>(
                    other.requiredNbtTags == null ? new ArrayList<String>() : other.requiredNbtTags);
            this.rarityFilters = new ArrayList<>(
                    other.rarityFilters == null ? new ArrayList<String>() : other.rarityFilters);
            this.itemFilterExpressions = new ArrayList<>(
                    other.itemFilterExpressions == null ? new ArrayList<String>() : other.itemFilterExpressions);
            this.maxDistance = other.maxDistance;
            this.priority = other.priority;
        }
    }

    public static class NameFilterGroup {
        public String name = "";
        public boolean enabled = true;
        public boolean blacklist = false;
        public List<String> names = new ArrayList<>();

        public NameFilterGroup() {
        }

        public NameFilterGroup(String name, boolean enabled, boolean blacklist, List<String> names) {
            this.name = name == null ? "" : name;
            this.enabled = enabled;
            this.blacklist = blacklist;
            this.names = new ArrayList<>(names == null ? new ArrayList<>() : names);
        }
    }

    public static class KillAuraPreset {
        public String name = "";
        public boolean rotateToTarget = true;
        public boolean smoothRotation = true;
        public float smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
        public String smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
        public boolean rotateOnlyOnAttack = true;
        public boolean relockOnlyWhenNoCrosshairTarget = true;
        public boolean onlyAttackWhenLookingAtTarget = false;
        public boolean requireLineOfSight = false;
        public boolean throughWallAttack = false;
        public boolean targetHostile = true;
        public boolean targetPassive = false;
        public boolean targetPlayers = false;
        public boolean targetNpcs = false;
        public boolean targetEnderCrystal = false;
        public boolean onlyWeapon = false;
        public boolean aimOnlyMode = false;
        public boolean focusSingleTarget = true;
        public boolean blockWhileAttacking = false;
        public boolean ignoreInvisible = true;
        public boolean enableNoCollision = true;
        public boolean enableAntiKnockback = true;
        public boolean enableFullBrightVision = false;
        public float fullBrightGamma = 1000.0F;
        public String attackMode = DEFAULT_ATTACK_MODE;
        public String attackSequenceName = "";
        public int attackSequenceDelayTicks = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS;
        public String attackSequenceDelayTicksSpec = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC;
        public float aimYawOffset = DEFAULT_AIM_YAW_OFFSET;
        public String aimYawOffsetSpec = DEFAULT_AIM_YAW_OFFSET_SPEC;
        public float aimPitchOffset = DEFAULT_AIM_PITCH_OFFSET;
        public String aimPitchOffsetSpec = DEFAULT_AIM_PITCH_OFFSET_SPEC;
        public String huntMode = HUNT_MODE_APPROACH;
        public boolean huntPickupItemsEnabled = false;
        public List<HuntPickupRule> huntPickupRules = new ArrayList<>();
        public boolean visualizeHuntRadius = false;
        public float huntRadius = 8.0F;
        public float huntFixedDistance = 4.2F;
        public float huntUpRange = DEFAULT_HUNT_UP_RANGE;
        public float huntDownRange = DEFAULT_HUNT_DOWN_RANGE;
        public boolean huntOrbitEnabled = false;
        public boolean huntJumpOrbitEnabled = true;
        public int huntOrbitSamplePoints = DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        public boolean enableNameWhitelist = false;
        public boolean enableNameBlacklist = false;
        public List<String> nameWhitelist = new ArrayList<>();
        public List<String> nameBlacklist = new ArrayList<>();
        // 旧预设里没有这个字段，读出来是 null，应用时按旧的白名单/黑名单迁移。
        public List<NameFilterGroup> nameFilterGroups = null;
        public float nearbyEntityScanRange = 10.0F;
        public float attackRange = 4.2F;
        public float huntScoreRadiusWeight = DEFAULT_HUNT_SCORE_RADIUS_WEIGHT;
        public float huntScorePlayerDistanceWeight = DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT;
        public float huntScorePlayerPlaneWeight = DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT;
        public float huntScoreTargetHeightWeight = DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT;
        public float huntScoreAttackRangeWeight = DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT;
        public float huntScoreVisibilityWeight = DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT;
        public float huntScoreOpennessWeight = DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT;
        public float minAttackStrength = 0.92F;
        public float minTurnSpeed = 4.0F;
        public float maxTurnSpeed = 18.0F;
        public int minAttackIntervalTicks = 2;
        public int targetsPerAttack = 3;
        public float teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
        public float teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        public int teleportActionsPerTick = DEFAULT_TELEPORT_ACTIONS_PER_TICK;
        public float teleportStepDistancePerAction = DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION;
        public boolean teleportAttackRotationPackets = false;
        public boolean disableOnDisconnect = true;
        public int noDamageAttackLimit = DEFAULT_NO_DAMAGE_ATTACK_LIMIT;

        public KillAuraPreset() {
        }

        public KillAuraPreset(KillAuraPreset other) {
            if (other == null) {
                return;
            }
            this.name = other.name == null ? "" : other.name;
            this.rotateToTarget = other.rotateToTarget;
            this.smoothRotation = other.smoothRotation;
            this.smoothMaxTurnStep = other.smoothMaxTurnStep;
            this.smoothMaxTurnStepSpec = other.smoothMaxTurnStepSpec == null
                    || other.smoothMaxTurnStepSpec.trim().isEmpty()
                            ? formatSmoothMaxTurnStepValue(other.smoothMaxTurnStep)
                            : other.smoothMaxTurnStepSpec;
            this.rotateOnlyOnAttack = other.rotateOnlyOnAttack;
            this.relockOnlyWhenNoCrosshairTarget = other.relockOnlyWhenNoCrosshairTarget;
            this.onlyAttackWhenLookingAtTarget = other.onlyAttackWhenLookingAtTarget;
            this.requireLineOfSight = other.requireLineOfSight;
            this.throughWallAttack = other.throughWallAttack;
            this.targetHostile = other.targetHostile;
            this.targetPassive = other.targetPassive;
            this.targetPlayers = other.targetPlayers;
            this.targetNpcs = other.targetNpcs;
            this.targetEnderCrystal = other.targetEnderCrystal;
            this.onlyWeapon = other.onlyWeapon;
            this.aimOnlyMode = other.aimOnlyMode;
            this.focusSingleTarget = other.focusSingleTarget;
            this.blockWhileAttacking = other.blockWhileAttacking;
            this.ignoreInvisible = other.ignoreInvisible;
            this.enableNoCollision = other.enableNoCollision;
            this.enableAntiKnockback = other.enableAntiKnockback;
            this.enableFullBrightVision = other.enableFullBrightVision;
            this.fullBrightGamma = other.fullBrightGamma;
            this.attackMode = other.attackMode == null ? ATTACK_MODE_NORMAL : other.attackMode;
            this.attackSequenceName = other.attackSequenceName == null ? "" : other.attackSequenceName;
            this.attackSequenceDelayTicks = other.attackSequenceDelayTicks;
            this.attackSequenceDelayTicksSpec = other.attackSequenceDelayTicksSpec == null
                    || other.attackSequenceDelayTicksSpec.trim().isEmpty()
                            ? String.valueOf(other.attackSequenceDelayTicks)
                            : other.attackSequenceDelayTicksSpec;
            this.aimYawOffset = other.aimYawOffset;
            this.aimYawOffsetSpec = other.aimYawOffsetSpec == null || other.aimYawOffsetSpec.trim().isEmpty()
                    ? formatAimOffsetValue(other.aimYawOffset)
                    : other.aimYawOffsetSpec;
            this.aimPitchOffset = other.aimPitchOffset;
            this.aimPitchOffsetSpec = other.aimPitchOffsetSpec == null || other.aimPitchOffsetSpec.trim().isEmpty()
                    ? formatAimOffsetValue(other.aimPitchOffset)
                    : other.aimPitchOffsetSpec;
            this.huntMode = other.huntMode == null ? HUNT_MODE_APPROACH : other.huntMode;
            this.huntPickupItemsEnabled = other.huntPickupItemsEnabled;
            this.huntPickupRules = copyHuntPickupRuleList(other.huntPickupRules);
            this.visualizeHuntRadius = other.visualizeHuntRadius;
            this.huntRadius = other.huntRadius;
            this.huntFixedDistance = other.huntFixedDistance;
            this.huntUpRange = other.huntUpRange;
            this.huntDownRange = other.huntDownRange;
            this.huntOrbitEnabled = other.huntOrbitEnabled;
            this.huntJumpOrbitEnabled = other.huntJumpOrbitEnabled;
            this.huntOrbitSamplePoints = other.huntOrbitSamplePoints;
            this.enableNameWhitelist = other.enableNameWhitelist;
            this.enableNameBlacklist = other.enableNameBlacklist;
            this.nameWhitelist = new ArrayList<>(other.nameWhitelist == null ? new ArrayList<>() : other.nameWhitelist);
            this.nameBlacklist = new ArrayList<>(other.nameBlacklist == null ? new ArrayList<>() : other.nameBlacklist);
            this.nameFilterGroups = other.nameFilterGroups == null ? null : copyNameFilterGroups(other.nameFilterGroups);
            this.nearbyEntityScanRange = other.nearbyEntityScanRange;
            this.attackRange = other.attackRange;
            this.huntScoreRadiusWeight = other.huntScoreRadiusWeight;
            this.huntScorePlayerDistanceWeight = other.huntScorePlayerDistanceWeight;
            this.huntScorePlayerPlaneWeight = other.huntScorePlayerPlaneWeight;
            this.huntScoreTargetHeightWeight = other.huntScoreTargetHeightWeight;
            this.huntScoreAttackRangeWeight = other.huntScoreAttackRangeWeight;
            this.huntScoreVisibilityWeight = other.huntScoreVisibilityWeight;
            this.huntScoreOpennessWeight = other.huntScoreOpennessWeight;
            this.minAttackStrength = other.minAttackStrength;
            this.minTurnSpeed = other.minTurnSpeed;
            this.maxTurnSpeed = other.maxTurnSpeed;
            this.minAttackIntervalTicks = other.minAttackIntervalTicks;
            this.targetsPerAttack = other.targetsPerAttack;
            this.teleportStepDistance = other.teleportStepDistance;
            this.teleportStationAttackRadius = other.teleportStationAttackRadius;
            this.teleportActionsPerTick = other.teleportActionsPerTick;
            this.teleportStepDistancePerAction = other.teleportStepDistancePerAction;
            this.teleportAttackRotationPackets = other.teleportAttackRotationPackets;
            this.disableOnDisconnect = other.disableOnDisconnect;
            this.noDamageAttackLimit = other.noDamageAttackLimit;
        }
    }

    private static final class NoDamageAttackTracker {
        private float baselineHealth;
        private int pendingAttempts;
        private int observationTicks;
        private int confirmedNoDamageAttempts;

        private NoDamageAttackTracker(float baselineHealth) {
            this.baselineHealth = baselineHealth;
        }
    }

    public interface AreaHuntTargetPlanner {
        void prioritize(EntityPlayerSP player, List<EntityLivingBase> targets);
    }

    public static final class AreaHuntOptions {
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double radius;
        private final double radiusSq;
        private final double upRange;
        private final double downRange;
        private final Predicate<EntityLivingBase> targetFilter;
        private final boolean whitelistOverridesTargetGroup;
        private final AreaHuntTargetPlanner targetPlanner;

        public AreaHuntOptions(double centerX, double centerY, double centerZ, double radius,
                double upRange, double downRange, Predicate<EntityLivingBase> targetFilter,
                boolean whitelistOverridesTargetGroup) {
            this(centerX, centerY, centerZ, radius, upRange, downRange, targetFilter,
                    whitelistOverridesTargetGroup, null);
        }

        public AreaHuntOptions(double centerX, double centerY, double centerZ, double radius,
                double upRange, double downRange, Predicate<EntityLivingBase> targetFilter,
                boolean whitelistOverridesTargetGroup, AreaHuntTargetPlanner targetPlanner) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = Math.max(0.0D, radius);
            this.radiusSq = this.radius * this.radius;
            this.upRange = Math.max(0.0D, upRange);
            this.downRange = Math.max(0.0D, downRange);
            this.targetFilter = targetFilter;
            this.whitelistOverridesTargetGroup = whitelistOverridesTargetGroup;
            this.targetPlanner = targetPlanner;
        }

        private boolean allows(EntityLivingBase target) {
            return targetFilter == null || targetFilter.test(target);
        }

        private boolean shouldTreatAllowedTargetAsWhitelistMatched() {
            return whitelistOverridesTargetGroup;
        }

        private void prioritizeTargets(EntityPlayerSP player, List<EntityLivingBase> targets) {
            if (targetPlanner != null && !whitelistOverridesTargetGroup && targets != null && !targets.isEmpty()) {
                targetPlanner.prioritize(player, targets);
            }
        }

        private boolean contains(Entity target) {
            if (target == null) {
                return false;
            }
            double dx = target.posX - centerX;
            double dz = target.posZ - centerZ;
            if (dx * dx + dz * dz > radiusSq) {
                return false;
            }
            double dy = target.posY - centerY;
            return dy <= upRange + 1.0E-6D && -dy <= downRange + 1.0E-6D;
        }
    }

    public static final class AreaHuntTickResult {
        private final boolean hasTarget;
        private final EntityLivingBase target;
        private final int attackedCount;
        private final boolean sequenceRunning;

        private AreaHuntTickResult(boolean hasTarget, EntityLivingBase target, int attackedCount,
                boolean sequenceRunning) {
            this.hasTarget = hasTarget;
            this.target = target;
            this.attackedCount = attackedCount;
            this.sequenceRunning = sequenceRunning;
        }

        public boolean hasTarget() {
            return hasTarget;
        }

        public EntityLivingBase getTarget() {
            return target;
        }

        public int getAttackedCount() {
            return attackedCount;
        }

        public boolean isSequenceRunning() {
            return sequenceRunning;
        }
    }

    private KillAuraHandler() {
    }

    static {
        loadConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("keycommand_killaura.json").toFile();
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("keycommand_killaura.json").toFile();
    }

    public static void loadConfig() {
        endMcpRuntime();
        rt().enabled = false;
        loadOwnerConfig(ProfileManager.currentConfigOwner(), true);
    }

    /** 会话切换后只重载配置值：开关与 MCP 运行时是运行态，由各会话自己维护。 */
    public static void reloadConfigValues() {
        // 每个会话的配置对象一直留在内存里，挂载切换时以它为准；只在第一次用到时读盘，
        // 否则会把该会话还没保存的预览值冲回磁盘上的旧值。
        cfg();
    }

    private static void loadConfigValues() {
        cfg().attackModeProfiles = new JsonObject();
        cfg().rotateToTarget = true;
        cfg().smoothRotation = true;
        cfg().smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
        cfg().smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
        cfg().rotateOnlyOnAttack = true;
        cfg().relockOnlyWhenNoCrosshairTarget = true;
        cfg().onlyAttackWhenLookingAtTarget = false;
        cfg().requireLineOfSight = false;
        cfg().throughWallAttack = false;
        cfg().targetHostile = true;
        cfg().targetPassive = false;
        cfg().targetPlayers = false;
        cfg().targetNpcs = false;
        cfg().targetEnderCrystal = false;
        cfg().targetArea = "";
        cfg().onlyWeapon = false;
        rt().aimOnlyMode = false;
        cfg().focusSingleTarget = true;
        cfg().blockWhileAttacking = false;
        cfg().ignoreInvisible = true;
        cfg().enableNoCollision = true;
        cfg().enableAntiKnockback = true;
        cfg().enableFullBrightVision = false;
        cfg().fullBrightGamma = 1000.0F;
        cfg().attackMode = DEFAULT_ATTACK_MODE;
        cfg().attackSequenceName = "";
        cfg().attackSequenceDelayTicks = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS;
        cfg().attackSequenceDelayTicksSpec = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC;
        cfg().aimYawOffset = DEFAULT_AIM_YAW_OFFSET;
        cfg().aimYawOffsetSpec = DEFAULT_AIM_YAW_OFFSET_SPEC;
        cfg().aimPitchOffset = DEFAULT_AIM_PITCH_OFFSET;
        cfg().aimPitchOffsetSpec = DEFAULT_AIM_PITCH_OFFSET_SPEC;
        rt().huntEnabled = true;
        cfg().huntMode = HUNT_MODE_APPROACH;
        cfg().huntPickupItemsEnabled = false;
        cfg().huntPickupRules = new ArrayList<>();
        rt().visualizeHuntRadius = false;
        cfg().huntRadius = 8.0F;
        cfg().huntFixedDistance = 4.2F;
        cfg().huntUpRange = DEFAULT_HUNT_UP_RANGE;
        cfg().huntDownRange = DEFAULT_HUNT_DOWN_RANGE;
        cfg().huntOrbitEnabled = false;
        cfg().huntJumpOrbitEnabled = true;
        cfg().huntOrbitSamplePoints = DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        cfg().enableNameWhitelist = false;
        cfg().enableNameBlacklist = false;
        cfg().nameWhitelist = new ArrayList<>();
        cfg().nameBlacklist = new ArrayList<>();
        cfg().nameFilterGroups = new ArrayList<>();
        cfg().nearbyEntityScanRange = 10.0F;
        cfg().presets.clear();

        cfg().attackRange = 4.2F;
        resetHuntScoreWeights();
        cfg().minAttackStrength = 0.92F;
        cfg().minTurnSpeed = 4.0F;
        cfg().maxTurnSpeed = 18.0F;
        cfg().minAttackIntervalTicks = 2;
        cfg().targetsPerAttack = 3;
        cfg().teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
        cfg().teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        cfg().teleportActionsPerTick = DEFAULT_TELEPORT_ACTIONS_PER_TICK;
        cfg().teleportStepDistancePerAction = DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION;
        cfg().teleportAttackRotationPackets = false;
        rt().disableOnDisconnect = true;
        cfg().noDamageAttackLimit = DEFAULT_NO_DAMAGE_ATTACK_LIMIT;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                normalizeConfig();
                return;
            }

            JsonConfigCharsetCompat.ReadResult readResult = JsonConfigCharsetCompat.readJsonObject(configFile.toPath());
            JsonObject json = readResult.getRoot();
            boolean needsRewrite = readResult.usedLegacyCharset();
            File runtimeFile = getRuntimeConfigFile();
            if (runtimeFile.exists()) {
                JsonConfigCharsetCompat.ReadResult rres = JsonConfigCharsetCompat.readJsonObject(runtimeFile.toPath());
                JsonObject rj = rres.getRoot();
                if (rj.has("enabled")) {
                    rt().enabled = rj.get("enabled").getAsBoolean();
                }
                if (rj.has("aimOnlyMode")) {
                    rt().aimOnlyMode = rj.get("aimOnlyMode").getAsBoolean();
                }
                if (rj.has("disableOnDisconnect")) {
                    rt().disableOnDisconnect = rj.get("disableOnDisconnect").getAsBoolean();
                }
            } else {
                if (json.has("enabled")) {
                    rt().enabled = json.get("enabled").getAsBoolean();
                }
                if (json.has("aimOnlyMode")) {
                    rt().aimOnlyMode = json.get("aimOnlyMode").getAsBoolean();
                }
                if (json.has("disableOnDisconnect")) {
                    rt().disableOnDisconnect = json.get("disableOnDisconnect").getAsBoolean();
                }
            }
            if (json.has("rotateToTarget")) {
                cfg().rotateToTarget = json.get("rotateToTarget").getAsBoolean();
            }
            if (json.has("smoothRotation")) {
                cfg().smoothRotation = json.get("smoothRotation").getAsBoolean();
            }
            if (json.has("smoothMaxTurnStep")) {
                setSmoothMaxTurnStepSpec(json.get("smoothMaxTurnStep").getAsString());
            }
            if (json.has("smoothMaxTurnStepSpec")) {
                setSmoothMaxTurnStepSpec(json.get("smoothMaxTurnStepSpec").getAsString());
            }
            if (json.has("rotateOnlyOnAttack")) {
                cfg().rotateOnlyOnAttack = json.get("rotateOnlyOnAttack").getAsBoolean();
            }
            if (json.has("relockOnlyWhenNoCrosshairTarget")) {
                cfg().relockOnlyWhenNoCrosshairTarget = json.get("relockOnlyWhenNoCrosshairTarget").getAsBoolean();
            }
            if (json.has("onlyAttackWhenLookingAtTarget")) {
                cfg().onlyAttackWhenLookingAtTarget = json.get("onlyAttackWhenLookingAtTarget").getAsBoolean();
            }
            if (json.has("requireLineOfSight")) {
                cfg().requireLineOfSight = json.get("requireLineOfSight").getAsBoolean();
            }
            if (json.has("throughWallAttack")) {
                cfg().throughWallAttack = json.get("throughWallAttack").getAsBoolean();
            }
            if (json.has("targetHostile")) {
                cfg().targetHostile = json.get("targetHostile").getAsBoolean();
            }
            if (json.has("targetPassive")) {
                cfg().targetPassive = json.get("targetPassive").getAsBoolean();
            }
            if (json.has("targetArea")) {
                cfg().targetArea = json.get("targetArea").getAsString();
            }
            if (json.has("targetPlayers")) {
                cfg().targetPlayers = json.get("targetPlayers").getAsBoolean();
            }
            if (json.has("targetNpcs")) {
                cfg().targetNpcs = json.get("targetNpcs").getAsBoolean();
            }
            if (json.has("targetEnderCrystal")) {
                cfg().targetEnderCrystal = json.get("targetEnderCrystal").getAsBoolean();
            }
            if (json.has("onlyWeapon")) {
                cfg().onlyWeapon = json.get("onlyWeapon").getAsBoolean();
            }
            if (json.has("focusSingleTarget")) {
                cfg().focusSingleTarget = json.get("focusSingleTarget").getAsBoolean();
            }
            if (json.has("blockWhileAttacking")) {
                cfg().blockWhileAttacking = json.get("blockWhileAttacking").getAsBoolean();
            }
            if (json.has("ignoreInvisible")) {
                cfg().ignoreInvisible = json.get("ignoreInvisible").getAsBoolean();
            }
            if (json.has("enableNoCollision")) {
                cfg().enableNoCollision = json.get("enableNoCollision").getAsBoolean();
            }
            if (json.has("enableAntiKnockback")) {
                cfg().enableAntiKnockback = json.get("enableAntiKnockback").getAsBoolean();
            }
            if (json.has("enableFullBrightVision")) {
                cfg().enableFullBrightVision = json.get("enableFullBrightVision").getAsBoolean();
            }
            if (json.has("fullBrightGamma")) {
                cfg().fullBrightGamma = json.get("fullBrightGamma").getAsFloat();
            }
            if (json.has("attackMode")) {
                cfg().attackMode = json.get("attackMode").getAsString();
            }
            if (json.has("attackSequenceName")) {
                cfg().attackSequenceName = json.get("attackSequenceName").getAsString();
            }
            if (json.has("attackSequenceDelayTicks")) {
                setAttackSequenceDelayTicksSpec(json.get("attackSequenceDelayTicks").getAsString());
            }
            if (json.has("attackSequenceDelayTicksSpec")) {
                setAttackSequenceDelayTicksSpec(json.get("attackSequenceDelayTicksSpec").getAsString());
            }
            if (json.has("aimYawOffset")) {
                setAimYawOffsetSpec(json.get("aimYawOffset").getAsString());
            }
            if (json.has("aimYawOffsetSpec")) {
                setAimYawOffsetSpec(json.get("aimYawOffsetSpec").getAsString());
            }
            if (json.has("aimPitchOffset")) {
                setAimPitchOffsetSpec(json.get("aimPitchOffset").getAsString());
            }
            if (json.has("aimPitchOffsetSpec")) {
                setAimPitchOffsetSpec(json.get("aimPitchOffsetSpec").getAsString());
            }
            boolean migratedHuntVerticalDefaults = false;
            if (json.has("huntMode")) {
                cfg().huntMode = json.get("huntMode").getAsString();
            } else if (json.has("huntEnabled")) {
                cfg().huntMode = json.get("huntEnabled").getAsBoolean() ? HUNT_MODE_APPROACH : HUNT_MODE_OFF;
            }
            boolean hasHuntFixedDistance = json.has("huntFixedDistance");
            if (json.has("huntPickupItemsEnabled")) {
                cfg().huntPickupItemsEnabled = json.get("huntPickupItemsEnabled").getAsBoolean();
            }
            if (json.has("huntPickupRules") && json.get("huntPickupRules").isJsonArray()) {
                List<HuntPickupRule> loadedRules = GSON.fromJson(json.get("huntPickupRules"),
                        HUNT_PICKUP_RULE_LIST_TYPE);
                cfg().huntPickupRules = normalizeHuntPickupRuleList(loadedRules);
            }
            if (json.has("visualizeHuntRadius")) {
                rt().visualizeHuntRadius = json.get("visualizeHuntRadius").getAsBoolean();
            }
            if (json.has("huntRadius")) {
                cfg().huntRadius = json.get("huntRadius").getAsFloat();
            }
            if (hasHuntFixedDistance) {
                cfg().huntFixedDistance = json.get("huntFixedDistance").getAsFloat();
            }
            if (json.has("huntUpRange") && !json.get("huntUpRange").isJsonNull()) {
                cfg().huntUpRange = json.get("huntUpRange").getAsFloat();
            } else {
                migratedHuntVerticalDefaults = true;
            }
            if (json.has("huntDownRange") && !json.get("huntDownRange").isJsonNull()) {
                cfg().huntDownRange = json.get("huntDownRange").getAsFloat();
            } else {
                migratedHuntVerticalDefaults = true;
            }
            if (json.has("huntOrbitEnabled")) {
                cfg().huntOrbitEnabled = json.get("huntOrbitEnabled").getAsBoolean();
            }
            if (json.has("huntJumpOrbitEnabled")) {
                cfg().huntJumpOrbitEnabled = json.get("huntJumpOrbitEnabled").getAsBoolean();
            }
            if (json.has("huntOrbitSamplePoints")) {
                cfg().huntOrbitSamplePoints = json.get("huntOrbitSamplePoints").getAsInt();
            }
            if (json.has("enableNameWhitelist")) {
                cfg().enableNameWhitelist = json.get("enableNameWhitelist").getAsBoolean();
            }
            if (json.has("enableNameBlacklist")) {
                cfg().enableNameBlacklist = json.get("enableNameBlacklist").getAsBoolean();
            }
            if (json.has("nameWhitelist") && json.get("nameWhitelist").isJsonArray()) {
                List<String> loaded = GSON.fromJson(json.get("nameWhitelist"), STRING_LIST_TYPE);
                cfg().nameWhitelist = normalizeNameList(loaded);
            }
            if (json.has("nameBlacklist") && json.get("nameBlacklist").isJsonArray()) {
                List<String> loaded = GSON.fromJson(json.get("nameBlacklist"), STRING_LIST_TYPE);
                cfg().nameBlacklist = normalizeNameList(loaded);
            }
            if (json.has("nameFilterGroups") && json.get("nameFilterGroups").isJsonArray()) {
                List<NameFilterGroup> loadedGroups = GSON.fromJson(json.get("nameFilterGroups"),
                        NAME_FILTER_GROUP_LIST_TYPE);
                cfg().nameFilterGroups = copyNameFilterGroups(loadedGroups);
            } else {
                // 旧配置只有一份白名单和一份黑名单，原样拆成两张分组卡，启用状态照搬。
                cfg().nameFilterGroups = migrateNameFilterGroups(cfg().enableNameWhitelist, cfg().nameWhitelist,
                        cfg().enableNameBlacklist, cfg().nameBlacklist);
            }
            rebuildNameFiltersFromGroups();
            if (json.has("nearbyEntityScanRange")) {
                cfg().nearbyEntityScanRange = json.get("nearbyEntityScanRange").getAsFloat();
            }
            if (json.has("presets") && json.get("presets").isJsonArray()) {
                List<KillAuraPreset> loadedPresets = GSON.fromJson(json.get("presets"), PRESET_LIST_TYPE);
                cfg().presets.clear();
                if (loadedPresets != null) {
                    for (KillAuraPreset preset : loadedPresets) {
                        KillAuraPreset normalizedPreset = normalizePreset(preset);
                        if (normalizedPreset != null) {
                            cfg().presets.add(normalizedPreset);
                        }
                    }
                }
            }

            if (json.has("attackRange")) {
                cfg().attackRange = json.get("attackRange").getAsFloat();
            }
            if (json.has("huntScoreRadiusWeight")) {
                cfg().huntScoreRadiusWeight = json.get("huntScoreRadiusWeight").getAsFloat();
            }
            if (json.has("huntScorePlayerDistanceWeight")) {
                cfg().huntScorePlayerDistanceWeight = json.get("huntScorePlayerDistanceWeight").getAsFloat();
            }
            if (json.has("huntScorePlayerPlaneWeight")) {
                cfg().huntScorePlayerPlaneWeight = json.get("huntScorePlayerPlaneWeight").getAsFloat();
            }
            if (json.has("huntScoreTargetHeightWeight")) {
                cfg().huntScoreTargetHeightWeight = json.get("huntScoreTargetHeightWeight").getAsFloat();
            }
            if (json.has("huntScoreAttackRangeWeight")) {
                cfg().huntScoreAttackRangeWeight = json.get("huntScoreAttackRangeWeight").getAsFloat();
            }
            if (json.has("huntScoreVisibilityWeight")) {
                cfg().huntScoreVisibilityWeight = json.get("huntScoreVisibilityWeight").getAsFloat();
            }
            if (json.has("huntScoreOpennessWeight")) {
                cfg().huntScoreOpennessWeight = json.get("huntScoreOpennessWeight").getAsFloat();
            }
            if (!hasHuntFixedDistance) {
                cfg().huntFixedDistance = cfg().attackRange;
            }
            if (json.has("minAttackStrength")) {
                cfg().minAttackStrength = json.get("minAttackStrength").getAsFloat();
            }
            if (json.has("minTurnSpeed")) {
                cfg().minTurnSpeed = json.get("minTurnSpeed").getAsFloat();
            }
            if (json.has("maxTurnSpeed")) {
                cfg().maxTurnSpeed = json.get("maxTurnSpeed").getAsFloat();
            }
            if (json.has("minAttackIntervalTicks")) {
                cfg().minAttackIntervalTicks = json.get("minAttackIntervalTicks").getAsInt();
            }
            if (json.has("targetsPerAttack")) {
                cfg().targetsPerAttack = json.get("targetsPerAttack").getAsInt();
            }
            if (json.has("teleportStepDistance")) {
                cfg().teleportStepDistance = json.get("teleportStepDistance").getAsFloat();
            }
            if (json.has("teleportStationAttackRadius")) {
                cfg().teleportStationAttackRadius = json.get("teleportStationAttackRadius").getAsFloat();
            }
            if (json.has("teleportActionsPerTick")) {
                cfg().teleportActionsPerTick = json.get("teleportActionsPerTick").getAsInt();
            }
            if (json.has("teleportStepDistancePerAction")) {
                cfg().teleportStepDistancePerAction = json.get("teleportStepDistancePerAction").getAsFloat();
            }
            if (json.has("teleportAttackRotationPackets")) {
                cfg().teleportAttackRotationPackets = json.get("teleportAttackRotationPackets").getAsBoolean();
            }
            if (json.has("disableOnDisconnect")) {
                rt().disableOnDisconnect = json.get("disableOnDisconnect").getAsBoolean();
            }
            if (json.has("noDamageAttackLimit")) {
                cfg().noDamageAttackLimit = json.get("noDamageAttackLimit").getAsInt();
            }
            if (json.has("attackModeProfiles") && json.get("attackModeProfiles").isJsonObject()) {
                cfg().attackModeProfiles = json.getAsJsonObject("attackModeProfiles");
            }

            normalizeConfig();
            if (migratedHuntVerticalDefaults || needsRewrite) {
                saveConfig();
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error(I18n.format("log.kill_aura.load_failed"), e);
        }
    }

    public static void saveConfig() {
        normalizeConfig();
        // When an MCP temporary runtime is active the statics hold the applied
        // overrides; the pre-runtime baseline lives in mcpOriginalFields. A GUI
        // save must still persist — the user intentionally changed these values —
        // so write the file normally, then rebase the restore snapshot onto the
        // new saved state so endMcpRuntime does not stomp the user's edit.
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("rotateToTarget", cfg().rotateToTarget);
            json.addProperty("smoothRotation", cfg().smoothRotation);
            json.addProperty("smoothMaxTurnStep", cfg().smoothMaxTurnStepSpec);
            json.addProperty("smoothMaxTurnStepValue", cfg().smoothMaxTurnStep);
            json.addProperty("rotateOnlyOnAttack", cfg().rotateOnlyOnAttack);
            json.addProperty("relockOnlyWhenNoCrosshairTarget", cfg().relockOnlyWhenNoCrosshairTarget);
            json.addProperty("onlyAttackWhenLookingAtTarget", cfg().onlyAttackWhenLookingAtTarget);
            json.addProperty("requireLineOfSight", cfg().requireLineOfSight);
            json.addProperty("throughWallAttack", cfg().throughWallAttack);
            json.addProperty("targetHostile", cfg().targetHostile);
            json.addProperty("targetPassive", cfg().targetPassive);
            json.addProperty("targetArea", cfg().targetArea);
            json.addProperty("targetPlayers", cfg().targetPlayers);
            json.addProperty("targetNpcs", cfg().targetNpcs);
            json.addProperty("targetEnderCrystal", cfg().targetEnderCrystal);
            json.addProperty("onlyWeapon", cfg().onlyWeapon);
            json.addProperty("focusSingleTarget", cfg().focusSingleTarget);
            json.addProperty("blockWhileAttacking", cfg().blockWhileAttacking);
            json.addProperty("ignoreInvisible", cfg().ignoreInvisible);
            json.addProperty("enableNoCollision", cfg().enableNoCollision);
            json.addProperty("enableAntiKnockback", cfg().enableAntiKnockback);
            json.addProperty("enableFullBrightVision", cfg().enableFullBrightVision);
            json.addProperty("fullBrightGamma", cfg().fullBrightGamma);
            json.addProperty("attackMode", cfg().attackMode);
            json.addProperty("attackSequenceName", cfg().attackSequenceName);
            json.addProperty("attackSequenceDelayTicks", cfg().attackSequenceDelayTicksSpec);
            json.addProperty("attackSequenceDelayTicksValue", cfg().attackSequenceDelayTicks);
            json.addProperty("aimYawOffset", cfg().aimYawOffsetSpec);
            json.addProperty("aimYawOffsetValue", cfg().aimYawOffset);
            json.addProperty("aimYawOffsetSpec", cfg().aimYawOffsetSpec);
            json.addProperty("aimPitchOffset", cfg().aimPitchOffsetSpec);
            json.addProperty("aimPitchOffsetValue", cfg().aimPitchOffset);
            json.addProperty("aimPitchOffsetSpec", cfg().aimPitchOffsetSpec);
            json.addProperty("huntMode", cfg().huntMode);
            json.addProperty("huntEnabled", isHuntEnabled());
            json.addProperty("huntPickupItemsEnabled", cfg().huntPickupItemsEnabled);
            json.add("huntPickupRules",
                    GSON.toJsonTree(copyHuntPickupRuleList(cfg().huntPickupRules), HUNT_PICKUP_RULE_LIST_TYPE));
            json.addProperty("huntRadius", cfg().huntRadius);
            json.addProperty("huntFixedDistance", cfg().huntFixedDistance);
            json.addProperty("huntUpRange", cfg().huntUpRange);
            json.addProperty("huntDownRange", cfg().huntDownRange);
            json.addProperty("huntOrbitEnabled", cfg().huntOrbitEnabled);
            json.addProperty("huntJumpOrbitEnabled", cfg().huntJumpOrbitEnabled);
            json.addProperty("huntOrbitSamplePoints", cfg().huntOrbitSamplePoints);
            json.addProperty("enableNameWhitelist", cfg().enableNameWhitelist);
            json.addProperty("enableNameBlacklist", cfg().enableNameBlacklist);
            json.add("nameWhitelist", GSON.toJsonTree(normalizeNameList(cfg().nameWhitelist), STRING_LIST_TYPE));
            json.add("nameBlacklist", GSON.toJsonTree(normalizeNameList(cfg().nameBlacklist), STRING_LIST_TYPE));
            // 上面四个旧键继续写合并结果，旧版本 jar 读这份配置也能照常过滤。
            json.add("nameFilterGroups", GSON.toJsonTree(copyNameFilterGroups(cfg().nameFilterGroups),
                    NAME_FILTER_GROUP_LIST_TYPE));
            json.addProperty("nearbyEntityScanRange", cfg().nearbyEntityScanRange);
            json.add("presets", GSON.toJsonTree(getPresetSnapshots(), PRESET_LIST_TYPE));
            json.addProperty("attackRange", cfg().attackRange);
            json.addProperty("huntScoreRadiusWeight", cfg().huntScoreRadiusWeight);
            json.addProperty("huntScorePlayerDistanceWeight", cfg().huntScorePlayerDistanceWeight);
            json.addProperty("huntScorePlayerPlaneWeight", cfg().huntScorePlayerPlaneWeight);
            json.addProperty("huntScoreTargetHeightWeight", cfg().huntScoreTargetHeightWeight);
            json.addProperty("huntScoreAttackRangeWeight", cfg().huntScoreAttackRangeWeight);
            json.addProperty("huntScoreVisibilityWeight", cfg().huntScoreVisibilityWeight);
            json.addProperty("huntScoreOpennessWeight", cfg().huntScoreOpennessWeight);
            json.addProperty("minAttackStrength", cfg().minAttackStrength);
            json.addProperty("minTurnSpeed", cfg().minTurnSpeed);
            json.addProperty("maxTurnSpeed", cfg().maxTurnSpeed);
            json.addProperty("minAttackIntervalTicks", cfg().minAttackIntervalTicks);
            json.addProperty("targetsPerAttack", cfg().targetsPerAttack);
            json.addProperty("teleportStepDistance", cfg().teleportStepDistance);
            json.addProperty("teleportStationAttackRadius", cfg().teleportStationAttackRadius);
            json.addProperty("teleportActionsPerTick", cfg().teleportActionsPerTick);
            json.addProperty("teleportStepDistancePerAction", cfg().teleportStepDistancePerAction);
            json.addProperty("teleportAttackRotationPackets", cfg().teleportAttackRotationPackets);
            json.addProperty("noDamageAttackLimit", cfg().noDamageAttackLimit);
            json.add("attackModeProfiles", cfg().attackModeProfiles == null ? new JsonObject() : cfg().attackModeProfiles);

            JsonConfigCharsetCompat.writeJsonObject(configFile.toPath(), json);

            // Per-owner runtime state — write to runtime file so bots don't
            // stomp each other's enabled/aim/disableOnDisconnect flags.
            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rjson = new JsonObject();
            rjson.addProperty("enabled", rt().enabled);
            rjson.addProperty("aimOnlyMode", rt().aimOnlyMode);
            rjson.addProperty("disableOnDisconnect", rt().disableOnDisconnect);
            rjson.addProperty("visualizeHuntRadius", rt().visualizeHuntRadius);
            JsonConfigCharsetCompat.writeJsonObject(runtimeFile.toPath(), rjson);

            // Rebase the MCP restore snapshot onto the just-saved state so
            // endMcpRuntime returns to these values instead of the stale
            // pre-runtime baseline. Do it for every owner with an active MCP
            // runtime — any bot's endMcpRuntime or reapplyStaticOverrides could
            // otherwise stomp the GUI-saved statics back to the pre-MCP values.
            // 配置已按会话分开，保存只影响当前会话，别的会话的 MCP 基线不能拿这份快照覆盖。
            {
                RuntimeState owner = rt();
                if (owner.mcpOriginalFields != null) {
                    JsonObject refresh = com.mythos.mythosScriptMod.mcp.McpFields.snapshot(KillAuraHandler.class);
                    JsonObject boxedBaseline = boxedRuntimeFields();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> b : boxedBaseline.entrySet()) {
                        refresh.add(b.getKey(), b.getValue());
                    }
                    owner.mcpOriginalFields = refresh;
                }
                owner.staticOverrides.clear();
            }

            // Push the just-saved live statics into every MythosStateSwap stash
            // (group + per-session). Without this, mounting a session whose stash
            // predates the save restores the OLD attackRange/huntRadius on next
            // swapTo — the GUI then shows the stale value and the user thinks the
            // save did nothing even though the file was written.
            // 会话持有独立配置后只有「人类当前会话」才该广播：所有 stash 一起覆盖会把
            // 别的 bot 自己保存的值冲掉，而当前会话的 stash 在 unmount 时会自然刷新。
            if (com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner() == null) {
                try {
                    Class<?> swapCls = Class.forName("com.mythos.nexus.session.MythosStateSwap");
                    swapCls.getMethod("pushLiveToAllStashes", String.class)
                            .invoke(null, "com.mythos.mythosScriptMod.handlers.KillAuraHandler");
                } catch (Throwable ignored) {
                    // Nexus not loaded / older Nexus without the hook — file save already done.
                }
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error(I18n.format("log.kill_aura.save_failed"), e);
        }
    }

    public void toggleEnabled() {
        setEnabled(!rt().enabled);
    }

    // ------------------------------------------------------------------
    // Runtime-switch accessors (boxed RuntimeState fields)
    // ------------------------------------------------------------------

    public static boolean enabled() { return rt().enabled; }

    public static void setEnabledSilently(boolean targetEnabled) { rt().enabled = targetEnabled; }

    public static boolean aimOnlyMode() { return rt().aimOnlyMode; }

    public static void setAimOnlyMode(boolean value) { rt().aimOnlyMode = value; }

    public static boolean huntEnabled() { return rt().huntEnabled; }

    public static void setHuntEnabled(boolean value) { rt().huntEnabled = value; }

    public static boolean visualizeHuntRadius() { return rt().visualizeHuntRadius; }

    public static void setVisualizeHuntRadius(boolean value) { rt().visualizeHuntRadius = value; }

    public static boolean disableOnDisconnect() { return rt().disableOnDisconnect; }

    public static void setDisableOnDisconnect(boolean value) { rt().disableOnDisconnect = value; }

    // ------------------------------------------------------------------
    // MCP runtime override
    // ------------------------------------------------------------------

    /** Per-session override state now lives on {@link RuntimeState}; this
     *  accessor remains only for cross-class probes (e.g. GUI saveConfig guard). */
    public static boolean isMcpRuntimeActive() { return rt().mcpOriginalFields != null; }

    /** Names of the boxed RuntimeState switches, kept outside McpFields' static scan. */
    private static final String[] BOXED_NAMES = {
            "enabled", "aimOnlyMode", "huntEnabled", "visualizeHuntRadius", "disableOnDisconnect"
    };

    /**
     * Removes the boxed runtime-switch keys from {@code fields} into a fresh JsonObject.
     * Callers pass the returned object to {@link #applyBoxedRuntimeFields(JsonObject)};
     * the stripped {@code fields} remains decodable by McpFields.
     */
    private static JsonObject extractBoxedRuntimeFields(JsonObject fields) {
        JsonObject boxed = new JsonObject();
        if (fields == null) {
            return boxed;
        }
        // Collect keys first, then remove — avoids iterator/concurrent-mod issues.
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String name : BOXED_NAMES) {
            if (fields.has(name)) found.add(name);
        }
        for (String name : found) {
            boxed.add(name, fields.remove(name));
        }
        return boxed;
    }

    /** Snapshot of the boxed runtime switches as a JsonObject of booleans. */
    public static JsonObject boxedRuntimeFields() {
        RuntimeState state = rt();
        JsonObject boxed = new JsonObject();
        boxed.addProperty("enabled", state.enabled);
        boxed.addProperty("aimOnlyMode", state.aimOnlyMode);
        boxed.addProperty("huntEnabled", state.huntEnabled);
        boxed.addProperty("visualizeHuntRadius", state.visualizeHuntRadius);
        boxed.addProperty("disableOnDisconnect", state.disableOnDisconnect);
        return boxed;
    }

    private static boolean isInsideTargetArea(EntityLivingBase target) {
        AxisAlignedBB bounds = targetAreaBounds();
        if (bounds == null) return true;
        return bounds.contains(new Vec3d(target.posX, target.posY, target.posZ));
    }

    private static AxisAlignedBB targetAreaBounds() {
        String raw = cfg().targetArea == null ? "" : cfg().targetArea.trim();
        if (raw.isEmpty()) return null;
        if (!raw.equals(cachedTargetAreaRaw)) {
            cachedTargetAreaRaw = raw;
            cachedTargetAreaBounds = parseTargetArea(raw);
        }
        return cachedTargetAreaBounds;
    }

    private static AxisAlignedBB parseTargetArea(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 6) return null;
        try {
            double[] value = new double[6];
            for (int i = 0; i < 6; i++) value[i] = Double.parseDouble(parts[i].trim());
            // 外扩半格：贴着边界生成的实体（中心正好在边界上）也要算在区域内。
            return new AxisAlignedBB(Math.min(value[0], value[3]), Math.min(value[1], value[4]), Math.min(value[2], value[5]),
                    Math.max(value[0], value[3]), Math.max(value[1], value[4]), Math.max(value[2], value[5])).grow(0.5D);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Teleport attack counters for the current tick owner: which gate stops each attempt. */    public static JsonObject teleportCounters() {
        RuntimeState state = rt();
        JsonObject counters = new JsonObject();
        counters.addProperty("sends", state.teleport.attackSends);
        counters.addProperty("skipsInterval", state.teleport.attackSkipsInterval);
        counters.addProperty("skipsNotReady", state.teleport.attackSkipsNotReady);
        counters.addProperty("skipsStrength", state.teleport.attackSkipsStrength);
        counters.addProperty("plansExecuted", state.teleport.plansExecuted);
        counters.addProperty("burstTicks", state.teleport.teleportBurstTicks);
        counters.addProperty("meleeCandidates", state.teleport.teleportMeleeCandidates);
        counters.addProperty("logicalTick", state.teleport.currentTick());
        counters.addProperty("targetsHit", state.teleport.teleportLastAttackTicks.size());
        JsonObject endReasons = new JsonObject();
        for (Map.Entry<String, Integer> entry : state.teleport.teleportEndReasons.entrySet())
            endReasons.addProperty(entry.getKey(), entry.getValue());
        counters.add("endReasons", endReasons);
        return counters;
    }

    /** Writes boxed runtime-switch values from {@code fields} into the current RuntimeState. */
    public static void applyBoxedRuntimeFields(JsonObject fields) {
        if (fields == null) {
            return;
        }
        RuntimeState state = rt();
        if (fields.has("enabled")) {
            state.enabled = fields.get("enabled").getAsBoolean();
        }
        if (fields.has("aimOnlyMode")) {
            state.aimOnlyMode = fields.get("aimOnlyMode").getAsBoolean();
        }
        if (fields.has("huntEnabled")) {
            state.huntEnabled = fields.get("huntEnabled").getAsBoolean();
        }
        if (fields.has("visualizeHuntRadius")) {
            state.visualizeHuntRadius = fields.get("visualizeHuntRadius").getAsBoolean();
        }
        if (fields.has("disableOnDisconnect")) {
            state.disableOnDisconnect = fields.get("disableOnDisconnect").getAsBoolean();
        }
    }

    /** Client thread only. Validate everything before replacing an existing override. */
    public static void beginMcpRuntime(JsonObject fields, boolean runtimeEnabled, int durationTicks) {
        JsonObject boxedOverrides = extractBoxedRuntimeFields(fields);
        java.util.Map<java.lang.reflect.Field, Object> decoded =
                com.mythos.mythosScriptMod.mcp.McpFields.decode(KillAuraHandler.class, fields);
        endMcpRuntime();
        RuntimeState rt = rt();
        rt.mcpOriginalFields = com.mythos.mythosScriptMod.mcp.McpFields.snapshot(KillAuraHandler.class);
        // Baseline for restoring the boxed switches when the runtime ends.
        JsonObject boxedBaseline = boxedRuntimeFields();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : boxedBaseline.entrySet()) {
            rt.mcpOriginalFields.add(entry.getKey(), entry.getValue());
        }
        try {
            com.mythos.mythosScriptMod.mcp.McpFields.apply(decoded);
            applyBoxedRuntimeFields(boxedOverrides);
            rt().enabled = runtimeEnabled;
            normalizeConfig();
            // Persist the applied static-field values per owner so a later
            // MythosStateSwap group-stash round-trip cannot stomp them.
            rt.staticOverrides.putAll(decoded);
            INSTANCE.resetRuntimeState();
            rt.mcpRemainingTicks = durationTicks;
        } catch (RuntimeException e) { endMcpRuntime(); throw e; }
    }

    public static void endMcpRuntime() {
        RuntimeState rt = rt();
        if (rt.mcpOriginalFields == null) return;
        JsonObject original = rt.mcpOriginalFields;
        // Strip the boxed-switch baseline keys so McpFields.decode sees only static fields.
        JsonObject boxedBaseline = extractBoxedRuntimeFields(original);
        com.mythos.mythosScriptMod.mcp.McpFields.apply(
                com.mythos.mythosScriptMod.mcp.McpFields.decode(KillAuraHandler.class, original));
        applyBoxedRuntimeFields(boxedBaseline);
        rt.staticOverrides.clear();
        rt.mcpOriginalFields = null;
        rt.mcpRemainingTicks = 0;
        INSTANCE.resetRuntimeState();
    }

    public void setEnabled(boolean targetEnabled) {
        Minecraft mc = Minecraft.getMinecraft();
        normalizeConfig();
        if (rt().enabled == targetEnabled) {
            saveConfig();
            return;
        }

        rt().enabled = targetEnabled;
        TeleportViewReturn viewReturn = targetEnabled ? null : beginTeleportViewReturn();
        // LOCK 下服务器停在虚拟位置，复位会把它清掉；回程要从那里出发，回程中途重新开启
        // 时下一次规划也要从那里接着走，否则第一步就是从本体位置瞬移。
        Vec3d keptVirtual = TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)
                && (viewReturn != null || rt().teleportViewReturn != null)
                ? currentLockServerPosition() : null;
        resetRuntimeState();
        rt().teleportViewReturn = viewReturn;
        if (keptVirtual != null) {
            rt().teleport.virtualPosition = keptVirtual;
        }
        saveConfig();

        if (ctxPlayer() != null) {
            ctxPlayer().sendMessage(
                    new TextComponentString(I18n.format(rt().enabled ? "msg.kill_aura.enabled" : "msg.kill_aura.disabled")));
        }
    }

    public void onClientDisconnect() {
        endMcpRuntime();
        if (rt().disableOnDisconnect && rt().enabled) {
            rt().enabled = false;
            saveConfig();
        }
        resetRuntimeState();
    }

    public static boolean isHuntEnabled() {
        return !HUNT_MODE_OFF.equals(normalizeHuntModeValue(cfg().huntMode));
    }

    public static boolean isHuntApproachMode() {
        return HUNT_MODE_APPROACH.equals(normalizeHuntModeValue(cfg().huntMode));
    }

    public static boolean isHuntFixedDistanceMode() {
        return HUNT_MODE_FIXED_DISTANCE.equals(normalizeHuntModeValue(cfg().huntMode));
    }

    public static void setHuntMode(String mode) {
        cfg().huntMode = normalizeHuntModeValue(mode);
        rt().huntEnabled = !HUNT_MODE_OFF.equals(cfg().huntMode);
        cfg().huntRadius = Math.max(cfg().huntRadius, cfg().attackRange);
        if (!rt().huntEnabled) {
            rt().visualizeHuntRadius = false;
        }
    }

    public static synchronized List<KillAuraPreset> getPresetSnapshots() {
        List<KillAuraPreset> snapshots = new ArrayList<>();
        for (KillAuraPreset preset : cfg().presets) {
            KillAuraPreset normalizedPreset = normalizePreset(preset);
            if (normalizedPreset != null) {
                snapshots.add(new KillAuraPreset(normalizedPreset));
            }
        }
        return snapshots;
    }

    public static synchronized List<HuntPickupRule> getHuntPickupRuleSnapshots() {
        return copyHuntPickupRuleList(cfg().huntPickupRules);
    }

    public static synchronized void replaceHuntPickupRules(List<HuntPickupRule> rules) {
        cfg().huntPickupRules = normalizeHuntPickupRuleList(rules);
        saveConfig();
    }

    public static synchronized boolean hasPreset(String name) {
        return findPresetIndex(name) >= 0;
    }

    public static synchronized boolean saveCurrentAsPreset(String name) {
        String normalizedName = normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return false;
        }
        KillAuraPreset preset = captureCurrentAsPreset(normalizedName);
        int existingIndex = findPresetIndex(normalizedName);
        if (existingIndex >= 0) {
            cfg().presets.set(existingIndex, preset);
        } else {
            cfg().presets.add(preset);
        }
        saveConfig();
        return true;
    }

    public static synchronized boolean overwritePreset(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        cfg().presets.set(index, captureCurrentAsPreset(cfg().presets.get(index).name));
        saveConfig();
        return true;
    }

    public static synchronized boolean applyPresetByName(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        applyPreset(cfg().presets.get(index));
        return true;
    }

    public static synchronized boolean renamePreset(String oldName, String newName) {
        int index = findPresetIndex(oldName);
        String normalizedNew = normalizePresetName(newName);
        if (index < 0 || normalizedNew.isEmpty()) {
            return false;
        }
        int duplicateIndex = findPresetIndex(normalizedNew);
        if (duplicateIndex >= 0 && duplicateIndex != index) {
            return false;
        }
        cfg().presets.get(index).name = normalizedNew;
        saveConfig();
        return true;
    }

    public static synchronized boolean deletePreset(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        cfg().presets.remove(index);
        saveConfig();
        return true;
    }

    private static void applyPreset(KillAuraPreset preset) {
        KillAuraPreset safePreset = normalizePreset(preset);
        if (safePreset == null) {
            return;
        }
        cfg().rotateToTarget = safePreset.rotateToTarget;
        cfg().smoothRotation = safePreset.smoothRotation;
        setSmoothMaxTurnStepSpec(safePreset.smoothMaxTurnStepSpec);
        cfg().rotateOnlyOnAttack = safePreset.rotateOnlyOnAttack;
        cfg().relockOnlyWhenNoCrosshairTarget = safePreset.relockOnlyWhenNoCrosshairTarget;
        cfg().onlyAttackWhenLookingAtTarget = safePreset.onlyAttackWhenLookingAtTarget;
        cfg().requireLineOfSight = safePreset.requireLineOfSight;
        cfg().throughWallAttack = safePreset.throughWallAttack;
        cfg().targetHostile = safePreset.targetHostile;
        cfg().targetPassive = safePreset.targetPassive;
        cfg().targetPlayers = safePreset.targetPlayers;
        cfg().targetNpcs = safePreset.targetNpcs;
        cfg().targetEnderCrystal = safePreset.targetEnderCrystal;
        cfg().onlyWeapon = safePreset.onlyWeapon;
        rt().aimOnlyMode = safePreset.aimOnlyMode;
        cfg().focusSingleTarget = safePreset.focusSingleTarget;
        cfg().blockWhileAttacking = safePreset.blockWhileAttacking;
        cfg().ignoreInvisible = safePreset.ignoreInvisible;
        cfg().enableNoCollision = safePreset.enableNoCollision;
        cfg().enableAntiKnockback = safePreset.enableAntiKnockback;
        cfg().enableFullBrightVision = safePreset.enableFullBrightVision;
        cfg().fullBrightGamma = safePreset.fullBrightGamma;
        cfg().attackMode = safePreset.attackMode;
        cfg().attackSequenceName = safePreset.attackSequenceName;
        setAttackSequenceDelayTicksSpec(safePreset.attackSequenceDelayTicksSpec);
        setAimYawOffsetSpec(safePreset.aimYawOffsetSpec);
        setAimPitchOffsetSpec(safePreset.aimPitchOffsetSpec);
        cfg().huntMode = safePreset.huntMode;
        cfg().huntPickupItemsEnabled = safePreset.huntPickupItemsEnabled;
        cfg().huntPickupRules = copyHuntPickupRuleList(safePreset.huntPickupRules);
        rt().visualizeHuntRadius = safePreset.visualizeHuntRadius;
        cfg().huntRadius = safePreset.huntRadius;
        cfg().huntFixedDistance = safePreset.huntFixedDistance;
        cfg().huntUpRange = safePreset.huntUpRange;
        cfg().huntDownRange = safePreset.huntDownRange;
        cfg().huntOrbitEnabled = safePreset.huntOrbitEnabled;
        cfg().huntJumpOrbitEnabled = safePreset.huntJumpOrbitEnabled;
        cfg().huntOrbitSamplePoints = safePreset.huntOrbitSamplePoints;
        cfg().enableNameWhitelist = safePreset.enableNameWhitelist;
        cfg().enableNameBlacklist = safePreset.enableNameBlacklist;
        cfg().nameWhitelist = new ArrayList<>(safePreset.nameWhitelist);
        cfg().nameBlacklist = new ArrayList<>(safePreset.nameBlacklist);
        cfg().nameFilterGroups = safePreset.nameFilterGroups != null
                ? copyNameFilterGroups(safePreset.nameFilterGroups)
                : migrateNameFilterGroups(cfg().enableNameWhitelist, cfg().nameWhitelist, cfg().enableNameBlacklist, cfg().nameBlacklist);
        rebuildNameFiltersFromGroups();
        cfg().nearbyEntityScanRange = safePreset.nearbyEntityScanRange;
        cfg().attackRange = safePreset.attackRange;
        cfg().huntScoreRadiusWeight = safePreset.huntScoreRadiusWeight;
        cfg().huntScorePlayerDistanceWeight = safePreset.huntScorePlayerDistanceWeight;
        cfg().huntScorePlayerPlaneWeight = safePreset.huntScorePlayerPlaneWeight;
        cfg().huntScoreTargetHeightWeight = safePreset.huntScoreTargetHeightWeight;
        cfg().huntScoreAttackRangeWeight = safePreset.huntScoreAttackRangeWeight;
        cfg().huntScoreVisibilityWeight = safePreset.huntScoreVisibilityWeight;
        cfg().huntScoreOpennessWeight = safePreset.huntScoreOpennessWeight;
        cfg().minAttackStrength = safePreset.minAttackStrength;
        cfg().minTurnSpeed = safePreset.minTurnSpeed;
        cfg().maxTurnSpeed = safePreset.maxTurnSpeed;
        cfg().minAttackIntervalTicks = safePreset.minAttackIntervalTicks;
        cfg().targetsPerAttack = safePreset.targetsPerAttack;
        cfg().teleportStepDistance = safePreset.teleportStepDistance;
        cfg().teleportStationAttackRadius = safePreset.teleportStationAttackRadius;
        cfg().teleportActionsPerTick = safePreset.teleportActionsPerTick;
        cfg().teleportStepDistancePerAction = safePreset.teleportStepDistancePerAction;
        cfg().teleportAttackRotationPackets = safePreset.teleportAttackRotationPackets;
        rt().disableOnDisconnect = safePreset.disableOnDisconnect;
        cfg().noDamageAttackLimit = safePreset.noDamageAttackLimit;
        normalizeConfig();
        INSTANCE.resetRuntimeState();
        saveConfig();
    }

    public void resetRuntimeState() {
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            stopAttackBlocking();
            rt().blockingAttemptPlayer = null;
            rt().nextBlockingAttemptTick = 0;
            stopHuntPickupNavigation();
            stopHuntNavigation();
            rt().attackCooldownTicks = 0;
            rt().attackCooldownUntilMillis = 0L;
            rt().sequenceCooldownTicks = 0;
            rt().sequenceCooldownUntilMillis = 0L;
            rt().currentTargetEntityId = -1;
            clearAimTargetTransition();
            resetSmoothTurnLimitBudget();
            clearVisualRotationCache();
            rt().huntNavigationActive = false;
            rt().lastHuntGotoTick = -99999;
            rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
            rt().lastHuntTargetX = 0.0D;
            rt().lastHuntTargetZ = 0.0D;
            rt().lastHuntGoalUsesY = false;
            rt().huntPickupNavigationActive = false;
            rt().lastHuntPickupGotoTick = -99999;
            rt().lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
            rt().huntPickupReachedEntityId = Integer.MIN_VALUE;
            rt().lastHuntPickupSearchTick = -99999;
            rt().lastHuntPickupSearchTargetEntityId = Integer.MIN_VALUE;
            rt().lastHuntPickupSearchFound = false;
            rt().huntScoreDebugEntries.clear();
            rt().lastHuntScoreDebugTick = Integer.MIN_VALUE;
            rt().huntPickupLastProgressTick = -99999;
            rt().huntPickupLastDistanceSq = Double.MAX_VALUE;
            rt().huntPickupRetryAfterTick = -99999;
            rt().lastOrbitProcessRequestTick = -99999;
            rt().lastOrbitProcessTargetEntityId = Integer.MIN_VALUE;
            rt().lastOrbitProcessRequestedRadius = Double.NaN;
            rt().lastSafeMotionX = 0.0D;
            rt().lastSafeMotionY = 0.0D;
            rt().lastSafeMotionZ = 0.0D;
            rt().activeTeleportTourExecution = null;
            rt().teleportViewReturn = null;
            rt().activeTeleportAttackPlan = null;
            rt().activeTeleportCorrectionPlan = null;
            rt().pendingTeleportReturnPlan = null;
            rt().pendingTeleportReturnTicks = 0;
            rt().lastTeleportCorrectionTick = Integer.MIN_VALUE;
            rt().teleportTargetScanCursor = 0;
            rt().teleportCrystalScanCursor = 0;
            rt().teleportStationAlternativeCursor = 0;
            rt().teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
            rt.teleport.reset();
            this.attackSequenceExecutor.stop();
            clearNoDamageAttackTracking();
            rt().areaHuntControlTicks = 0;
        }
    }

    private boolean ownsAttackBlocking(EntityPlayerSP player) {
        RuntimeState rt = rt();
        return player != null && player == rt().blockingPlayer && rt().blockingHand != null
                && player.isHandActive() && player.getActiveHand() == rt().blockingHand
                && player.getActiveItemStack() == rt().blockingStack;
    }

    private boolean isAttackBlockingRequested(Minecraft mc) {
        RuntimeState rt = rt();
        EntityPlayerSP player = ctxPlayer();
        if (!cfg().blockWhileAttacking || rt().aimOnlyMode || player == null || ctxWorld() == null
                || player.world != ctxWorld() || ctxController() == null || player.connection == null
                || mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused()
                || player.isDead || player.getHealth() <= 0.0F || player.isSpectator()
                || AutoEatHandler.isEating() || this.attackSequenceExecutor.isRunning()
                || !PerformanceMonitor.isFeatureEnabled("kill_aura")) {
            return false;
        }
        // Target loss must not restart vanilla's five-tick shield warm-up.
        return rt().enabled || rt().areaHuntControlTicks > 0 || (rt().lastBlockingTarget != null
                && player.ticksExisted - rt().lastBlockingAttackTick >= 0
                && player.ticksExisted - rt().lastBlockingAttackTick <= 2);
    }

    /** Finds moving projectiles whose next few ticks intersect the player's body. */
    private Entity findBlockingProjectileThreat(EntityPlayerSP player) {
        if (!cfg().blockWhileAttacking || player == null || player.world == null) return null;
        Entity best = null;
        double bestTime = Double.POSITIVE_INFINITY;
        AxisAlignedBB body = player.getEntityBoundingBox().grow(0.35D);
        Vec3d playerMotion = new Vec3d(player.motionX, player.onGround ? 0D : player.motionY, player.motionZ);
        // Projectiles move fast; search a few blocks of future travel, still
        // far smaller than scanning the whole loaded list.
        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(16.0D))) {
            if (!(entity instanceof EntityArrow || entity instanceof EntityFireball
                    || entity instanceof EntityThrowable || entity instanceof EntityShulkerBullet)
                    || entity.isDead) continue;
            if (entity instanceof EntityArrow && ((AccessorEntityArrow) entity).mythos$isInGround()) continue;
            Vec3d velocity = new Vec3d(entity.motionX, entity.motionY, entity.motionZ);
            Vec3d start = new Vec3d(entity.posX, entity.posY, entity.posZ);
            Vec3d acceleration = Vec3d.ZERO;
            double gravity = 0D;
            double drag = 1D;
            if (entity instanceof EntityArrow || entity instanceof EntityThrowable) {
                drag = entity.isInWater() ? (entity instanceof EntityArrow ? 0.6F : 0.8F) : 0.99F;
                if (!entity.hasNoGravity()) {
                    gravity = entity instanceof EntityArrow || entity instanceof EntityPotion ? 0.05F : 0.03F;
                }
            } else if (entity instanceof EntityFireball) {
                EntityFireball fireball = (EntityFireball) entity;
                acceleration = new Vec3d(fireball.accelerationX, fireball.accelerationY, fireball.accelerationZ);
                drag = entity.isInWater() ? 0.8F : 0.95F;
            }
            double time = KillAuraBlocking.impactTime(body, playerMotion, start, velocity, acceleration,
                    drag, gravity, 20, (from, to) -> player.world.rayTraceBlocks(from, to, false, true, false));
            if (time < bestTime) {
                best = entity;
                bestTime = time;
            }
        }
        return best;
    }

    public Optional<Rotation> getBlockingServerRotation(EntityPlayerSP player) {
        // Packet dispatch may run off-thread. Publish the game-thread result instead of scanning the world here.
        RuntimeState rt = rt();
        return player != null && player == rt().blockingPlayer && cfg().blockWhileAttacking && !rt().aimOnlyMode
                ? Optional.ofNullable(rt().blockingServerRotation) : Optional.empty();
    }

    private EntityLivingBase findBlockingMeleeThreat(EntityPlayerSP player) {
        EntityLivingBase best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (EntityLivingBase entity : player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                player.getEntityBoundingBox().grow(4D))) {
            if (entity == player || !entity.isEntityAlive() || entity instanceof EntityArmorStand
                    || entity.isOnSameTeam(player) || !player.canEntityBeSeen(entity)) continue;
            boolean targetingPlayer = entity instanceof EntityLiving && ((EntityLiving) entity).getAttackTarget() == player;
            if (!targetingPlayer && !(entity instanceof IMob) && !(entity instanceof EntityPlayer)
                    && !entity.isSwingInProgress
                    && player.getRevengeTarget() != entity) continue;
            double distance = player.getDistanceSq(entity);
            if (distance > 16D) continue;
            double score = distance - (entity.isSwingInProgress ? 16D : 0D) - (targetingPlayer ? 8D : 0D);
            if (score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    private void syncBlockingServerRotation() {
        RuntimeState rt = rt();
        EntityPlayerSP player = ctxPlayer();
        if (!ownsAttackBlocking(player)) return;
        Entity source = findBlockingProjectileThreat(player);
        if (source == null) source = findBlockingMeleeThreat(player);
        if (source == null && rt.lastBlockingTarget != null && rt.lastBlockingTarget.isEntityAlive()
                && rt.lastBlockingTarget.world == player.world) source = rt.lastBlockingTarget;
        if (source == null && rt().enabled && rt.currentTargetEntityId != -1) {
            source = player.world.getEntityByID(rt.currentTargetEntityId);
        }
        Rotation previous = rt().blockingServerRotation;
        rt().blockingServerRotation = source == null || !source.isEntityAlive() ? null
                : KillAuraBlocking.faceSource(player.getPositionVector(), source.getPositionVector());
        Rotation rotation = rt().blockingServerRotation;
        // A stationary vanilla player can go twenty ticks without any movement packet.
        if (rotation != null || previous != null) {
            player.connection.sendPacket(new CPacketPlayer.Rotation(rotation == null ? player.rotationYaw : rotation.getYaw(),
                    rotation == null ? player.rotationPitch : rotation.getPitch(), player.onGround));
        }
    }

    private boolean canUseBlockingStack(EntityPlayerSP player, ItemStack stack) {
        return !stack.isEmpty() && stack.getItemUseAction() == EnumAction.BLOCK
                && stack.getItem().isShield(stack, player)
                && !player.getCooldownTracker().hasCooldown(stack.getItem());
    }

    // Vanilla releases active use when the use key is up. Keep only the shield we own,
    // without faking right-click input (which could eat, place blocks or open containers).
    public boolean shouldKeepAttackBlocking() {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        return ownsAttackBlocking(ctxPlayer()) && isAttackBlockingRequested(mc)
                && ctxPlayer().inventory.currentItem == rt().blockingHotbarSlot
                && ctxPlayer().getHeldItem(rt().blockingHand) == rt().blockingStack
                && canUseBlockingStack(ctxPlayer(), rt().blockingStack);
    }

    public void stopAttackBlocking() {
        RuntimeState rt = rt();
        rt().lastBlockingTarget = null;
        releaseAttackBlocking();
    }

    private void releaseAttackBlocking() {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = rt().blockingPlayer;
        boolean owned = ownsAttackBlocking(player);
        Rotation previous = rt().blockingServerRotation;
        rt().blockingServerRotation = null;
        rt().blockingPlayer = null;
        rt().blockingHand = null;
        rt().blockingStack = ItemStack.EMPTY;
        if (player != null && player == ctxPlayer() && player.world == ctxWorld() && ctxController() != null
                && player.connection != null) {
            if (previous != null) {
                player.connection.sendPacket(new CPacketPlayer.Rotation(player.rotationYaw, player.rotationPitch, player.onGround));
            }
            // A real or externally held use key takes over the shield without interruption.
            if (owned && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
                ctxController().onStoppedUsingItem(player);
            }
        } else if (owned) {
            player.resetActiveHand();
        }
    }

    private void updateAttackBlocking() {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        if (this.attackSequenceExecutor.isRunning() && cfg().blockWhileAttacking && !rt().aimOnlyMode) {
            releaseAttackBlocking();
            return;
        }
        if (!isAttackBlockingRequested(mc)) {
            stopAttackBlocking();
            return;
        }
        EntityPlayerSP player = ctxPlayer();
        if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
            releaseAttackBlocking();
            return;
        }
        if (shouldKeepAttackBlocking()) return;
        releaseAttackBlocking();
        if (player.isHandActive()) return;
        if (rt().blockingAttemptPlayer == player && player.ticksExisted < rt().nextBlockingAttemptTick) return;
        EnumHand hand = canUseBlockingStack(player, player.getHeldItemOffhand()) ? EnumHand.OFF_HAND
                : canUseBlockingStack(player, player.getHeldItemMainhand()) ? EnumHand.MAIN_HAND : null;
        if (hand == null) return;
        ItemStack stack = player.getHeldItem(hand);
        rt().blockingAttemptPlayer = player;
        rt().nextBlockingAttemptTick = player.ticksExisted + 5;
        ctxController().processRightClick(player, ctxWorld(), hand);
        if (player.isHandActive() && player.getActiveHand() == hand
                && player.getActiveItemStack() == stack) {
            rt().blockingPlayer = player;
            rt().blockingHand = hand;
            rt().blockingStack = stack;
            rt().blockingHotbarSlot = player.inventory.currentItem;
            rt().nextBlockingAttemptTick = player.ticksExisted;
        }
    }

    private void rememberBlockingAttack(EntityPlayerSP player, Entity target) {
        RuntimeState rt = rt();
        if (!cfg().blockWhileAttacking) return;
        rt().lastBlockingTarget = target;
        rt().lastBlockingAttackTick = player.ticksExisted;
    }

    public void refreshHuntAttackBlocking(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (player != null && target != null && rt().lastBlockingTarget == target) {
            rt().lastBlockingAttackTick = player.ticksExisted;
        }
    }

    private boolean shouldYieldAttackToItemUse(EntityPlayerSP player) {
        return cfg().blockWhileAttacking && (AutoEatHandler.isEating()
                || Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown()
                || (player.isHandActive() && !ownsAttackBlocking(player)));
    }

    public boolean hasActiveTarget(EntityPlayerSP player) {
        return getActiveTarget(player).isPresent();
    }

    public Optional<EntityLivingBase> getActiveTarget(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (!rt().enabled || player == null || player.world == null || rt().currentTargetEntityId == -1) {
            return Optional.empty();
        }

        Entity target = player.world.getEntityByID(rt().currentTargetEntityId);
        double targetSearchRadius = getTargetSearchRadius();
        boolean useWhitelistPriority = cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        if (target instanceof EntityLivingBase
                && isTrackableTarget(player, (EntityLivingBase) target, targetSearchRadius * targetSearchRadius,
                        useWhitelistPriority)) {
            return Optional.of((EntityLivingBase) target);
        }
        return Optional.empty();
    }

    public Optional<Rotation> getVisualTargetRotation(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null || player.world == null || !shouldRotateToTarget() || rt().currentTargetEntityId == -1) {
            return Optional.empty();
        }
        if (!shouldApplyContinuousRotation(false)) {
            return Optional.empty();
        }
        Entity target = player.world.getEntityByID(rt().currentTargetEntityId);
        if (!(target instanceof EntityLivingBase)) {
            return Optional.empty();
        }
        EntityLivingBase livingTarget = (EntityLivingBase) target;
        if (!isValidTarget(player, livingTarget)) {
            return Optional.empty();
        }
        if (isRelockSuppressedByCrosshairTarget(player, livingTarget)) {
            return Optional.empty();
        }

        Rotation cachedRotation = getCachedVisualRotation(player, livingTarget);
        if (cachedRotation != null) {
            return Optional.of(cachedRotation);
        }

        Rotation nextRotation = computeNextAimRotation(player, livingTarget, false, false);
        if (nextRotation == null) {
            return Optional.empty();
        }
        cacheVisualRotation(player, livingTarget, nextRotation);
        return Optional.of(nextRotation);
    }

    public boolean tryAttackUsingCurrentConfigForHunt(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || player == null || target == null || ctxController() == null) {
                return false;
            }
            refreshHuntAttackBlocking(player, target);
            if (shouldYieldAttackToItemUse(player)) return false;
            if (rt().aimOnlyMode || isSequenceAttackMode()) {
                return false;
            }
            if (isTeleportAttackRecoveryActive()) {
                return false;
            }
            if (cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
                return false;
            }
            if ((isPacketAttackMode() || isTeleportAttackMode()) && player.connection == null) {
                return false;
            }
            if (getAttackCooledStrength(player) < cfg().minAttackStrength) {
                return false;
            }
            if (isTeleportAttackMode()) {
                double searchRangeSq = cfg().attackRange * cfg().attackRange;
                boolean useWhitelistPriority = cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
                if (buildTargetCandidate(player, target, searchRangeSq, useWhitelistPriority,
                        target.getEntityId() == rt().currentTargetEntityId, true, null) == null) {
                    return false;
                }
                rt().currentTargetEntityId = target.getEntityId();
                updateAimTargetTransition(target);
                boolean attacked = performTeleportAttack(player, target);
                if (attacked) {
                    updateAttackBlocking();
                }
                decayTargetSwitchSmoothTicks();
                return attacked;
            }
            if (!canAttackTargetBeforeRotation(player, target)) {
                return false;
            }

            rt().currentTargetEntityId = target.getEntityId();
            updateAimTargetTransition(target);
            EntityLivingBase crosshairLockedTarget = isRelockSuppressedByCrosshairTarget(player, target) ? target : null;
            if (!prepareRotationForAttack(player, target, crosshairLockedTarget)) {
                decayTargetSwitchSmoothTicks();
                return false;
            }

            boolean teleportAttack = shouldUseTeleportAttack(player, target);
            if (!canAttackTarget(player, target, shouldRequireCrosshairHitForAttack(teleportAttack))) {
                decayTargetSwitchSmoothTicks();
                return false;
            }

            // Read BEFORE attack — attackEntityFrom sets hurtResistantTime=max on
            // the hit itself, so post-attack reads are always >0.
            boolean attackInsideIframe = target.hurtResistantTime
                    > target.maxHurtResistantTime / 2.0F;
            boolean attacked = false;
            if (teleportAttack) {
                attacked = performTeleportAttack(player, target);
            } else if (isMouseClickAttackMode()) {
                attacked = performMouseClickAttack(mc);
            } else if (isPacketAttackMode()) {
                player.connection.sendPacket(new CPacketUseEntity(target));
                attacked = true;
            } else {
                ctxController().attackEntity(player, target);
                attacked = true;
            }
            if (attacked && !isMouseClickAttackMode()) {
                player.swingArm(EnumHand.MAIN_HAND);
            }
            if (attacked && !teleportAttack && !attackInsideIframe) {
                recordNoDamageAttackAttempt(target);
            }
            if (attacked) {
                rememberBlockingAttack(player, target);
                updateAttackBlocking();
            }
            decayTargetSwitchSmoothTicks();
            return attacked;
        }
    }

    public boolean prepareCurrentConfigSequenceAttackForHunt(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (player == null || target == null || !isSequenceAttackMode() || !hasConfiguredAttackSequence()) {
            return false;
        }
        if (!isValidTarget(player, target)) {
            return false;
        }
        stopAttackBlocking();
        rt().currentTargetEntityId = target.getEntityId();
        updateAimTargetTransition(target);
        EntityLivingBase crosshairLockedTarget = isRelockSuppressedByCrosshairTarget(player, target) ? target : null;
        boolean prepared = prepareRotationForAttack(player, target, crosshairLockedTarget);
        decayTargetSwitchSmoothTicks();
        return prepared;
    }

    public static boolean isConfiguredSequenceAttackMode() {
        return ATTACK_MODE_SEQUENCE.equalsIgnoreCase(cfg().attackMode);
    }

    public static int sampleCurrentConfigAttackCooldownTicks() {
        return ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(cfg().attackMode)
                ? sampleAttackSequenceDelayTicks()
                : cfg().minAttackIntervalTicks;
    }

    public static int sampleCurrentConfigSequenceDelayTicks() {
        return sampleAttackSequenceDelayTicks();
    }

    public AreaHuntTickResult tickAreaHunt(EntityPlayerSP player, AreaHuntOptions options) {
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            rt().areaHuntControlTicks = 2;
            ensureBaritonePacketListenerRegistered();

            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || player == null || ctxWorld() == null) {
                return new AreaHuntTickResult(false, null, 0, this.attackSequenceExecutor.isRunning());
            }

            if (rt().attackCooldownTicks > 0) {
                if (rt().attackCooldownUntilMillis > 0L) {
                    rt().attackCooldownTicks = (int) Math.max(0L,
                            (rt().attackCooldownUntilMillis - System.currentTimeMillis() + 49L) / 50L);
                } else {
                    rt().attackCooldownTicks--;
                }
            }
            if (rt().sequenceCooldownTicks > 0) {
                // Wall-clock countdown: remaining ticks derived from the deadline,
                // immune to the session's throttled tick rate.
                if (rt().sequenceCooldownUntilMillis > 0L) {
                    rt().sequenceCooldownTicks = (int) Math.max(0L,
                            (rt().sequenceCooldownUntilMillis - System.currentTimeMillis() + 49L) / 50L);
                } else {
                    rt().sequenceCooldownTicks--;
                }
            }
            tickTeleportAttackRecovery(player);
            if (rt.activeTeleportTourExecution != null) {
                return new AreaHuntTickResult(true, null, 0, false);
            }
            applyFullBright(cfg().enableFullBrightVision);

            if (player.isDead || player.getHealth() <= 0.0F || player.isSpectator()) {
                clearAreaHuntRuntimeState(true);
                return new AreaHuntTickResult(false, null, 0, false);
            }

            tickNoDamageAttackTrackers(player);
            tickHuntUnreachableTrackers(player);

            boolean sequenceAttackMode = isSequenceAttackMode();
            if (!sequenceAttackMode && this.attackSequenceExecutor.isRunning()) {
                this.attackSequenceExecutor.stop();
            }

            List<EntityLivingBase> targets = findTargets(player, options);
            if (isTeleportAttackMode()) {
                syncTeleportPacketBudget(player);
                teleportDebug(player, "AREA_TICK_STATE",
                        "targets=%d,targetIds=%s,currentTarget=%d,cache=%d,history=%d,noDamageExcluded=%d,cooldown=%d,gate=%s,packetsUsed=%d,recoveryActive=%s,pendingReturn=%s",
                        targets.size(), formatTeleportTargetIds(targets, 24), rt().currentTargetEntityId,
                        rt().teleport.teleportTargetCandidateEntityIds.size(), rt().teleport.teleportLastAttackTicks.size(),
                        rt().noDamageExcludedEntityIds.size(), rt().attackCooldownTicks, getTeleportAttackGateReason(player),
                        rt().teleport.teleportPacketsUsedThisTick,
                        isTeleportAttackRecoveryActive(), rt().pendingTeleportReturnPlan != null);
            }
            if (targets.isEmpty()) {
                if (isTeleportAttackMode()) {
                    teleportDebug(player, "AREA_ATTACK_STOP", "reason=NO_TARGETS_AFTER_SCAN");
                }
                clearAreaHuntRuntimeState(false);
                return new AreaHuntTickResult(false, null, 0, this.attackSequenceExecutor.isRunning());
            }

            EntityLivingBase primaryTarget = targets.get(0);
            if (!rt().aimOnlyMode && !sequenceAttackMode && cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
                return new AreaHuntTickResult(true, primaryTarget, 0, this.attackSequenceExecutor.isRunning());
            }

            updateAimTargetTransition(primaryTarget);
            boolean orbitFacingActive = shouldForceOrbitFacing(player, primaryTarget);
            boolean crosshairTargetLocked = !isTeleportAttackMode()
                    && isRelockSuppressedByCrosshairTarget(player, primaryTarget, options);

            if (shouldApplyContinuousRotation(orbitFacingActive) && !crosshairTargetLocked) {
                applyRotation(player, primaryTarget, orbitFacingActive);
            }

            if (shouldRunHuntMovement(player, primaryTarget)) {
                stopHuntPickupNavigation();
                handleHuntMovement(player, primaryTarget);
            } else {
                stopHuntPickupNavigation();
                stopHuntNavigation();
            }

            int attackedCount = 0;
            if (sequenceAttackMode) {
                this.attackSequenceExecutor.tick(player);
                // Read before triggerAttackSequence — attackEntityFrom sets
                // hurtResistantTime=max on the hit itself, so post-attack reads
                // are always >0 and would silently disable the tracker.
                boolean seqAttackInsideIframe = primaryTarget != null
                        && primaryTarget.hurtResistantTime
                                > primaryTarget.maxHurtResistantTime / 2.0F;
                if (canTriggerAttackSequence(player, primaryTarget, options)
                        && prepareRotationForAttack(player, primaryTarget, crosshairTargetLocked ? primaryTarget : null)
                        && triggerAttackSequence(player, primaryTarget)) {
                    if (!seqAttackInsideIframe) {
                        recordNoDamageAttackAttempt(primaryTarget);
                    }
                    rt().sequenceCooldownTicks = sampleAttackSequenceDelayTicks();
                    rt().sequenceCooldownUntilMillis = System.currentTimeMillis()
                            + rt().sequenceCooldownTicks * 50L;
                }
                decayTargetSwitchSmoothTicks();
                return new AreaHuntTickResult(true, primaryTarget, 0, this.attackSequenceExecutor.isRunning());
            }

            if (rt().aimOnlyMode) {
                decayTargetSwitchSmoothTicks();
                return new AreaHuntTickResult(true, primaryTarget, 0, false);
            }

            boolean canStartAreaAttack = canStartAttack(player);
            if (isTeleportAttackMode() && !canStartAreaAttack) {
                teleportDebug(player, "AREA_ATTACK_STOP", "reason=%s,primary={%s}",
                        getTeleportAttackGateReason(player), formatTeleportTarget(player, primaryTarget));
            }
            if (canStartAreaAttack && ctxController() != null) {
                attackedCount = attackTargets(mc, player, targets, crosshairTargetLocked ? primaryTarget : null, options);
                if (attackedCount > 0) {
                    if (!isMouseClickAttackMode() && !isTeleportAttackMode()) {
                        player.swingArm(EnumHand.MAIN_HAND);
                    }
                    rt().attackCooldownTicks = isMouseClickAttackMode()
                            ? sampleAttackSequenceDelayTicks()
                            : cfg().minAttackIntervalTicks;
                    rt().attackCooldownUntilMillis = System.currentTimeMillis()
                            + rt().attackCooldownTicks * 50L;
                    if (!isHuntOrbitEnabled()) {
                        stopHuntNavigation();
                    }
                } else if (isTeleportAttackMode()) {
                    teleportDebug(player, "AREA_ATTACK_STOP",
                            "reason=ATTACK_PIPELINE_RETURNED_ZERO,primary={%s},targets=%s",
                            formatTeleportTarget(player, primaryTarget), formatTeleportTargetIds(targets, 24));
                }
            }
            decayTargetSwitchSmoothTicks();
            return new AreaHuntTickResult(true, primaryTarget, attackedCount, this.attackSequenceExecutor.isRunning());
        }
    }

    public void stopAreaHuntAction() {
        RuntimeState rt = rt();
        clearAreaHuntRuntimeState(true);
        clearTeleportAttackState();
        rt().areaHuntControlTicks = 0;
        clearNoDamageAttackTracking();
    }

    public boolean isAreaHuntSequenceRunning() {
        return this.attackSequenceExecutor.isRunning();
    }

    public void tickAreaHuntSequenceOnly(EntityPlayerSP player) {
        RuntimeState rt = rt();
        rt().areaHuntControlTicks = 2;
        if (player != null && this.attackSequenceExecutor.isRunning()) {
            this.attackSequenceExecutor.tick(player);
        }
    }

    private void clearAreaHuntRuntimeState(boolean stopSequence) {
        RuntimeState rt = rt();
        stopAttackBlocking();
        rt().currentTargetEntityId = -1;
        clearAimTargetTransition();
        stopHuntPickupNavigation();
        stopHuntNavigation();
        clearHuntUnreachableTracking();
        if (stopSequence) {
            this.attackSequenceExecutor.stop();
        }
    }

    private void ensureBaritonePacketListenerRegistered() {
        RuntimeState rt = rt();
        try {
            // Resolve the baritone for THIS call site's player — on a bot's
            // tick thread that's the session baritone, not the primary. The
            // primary-only subscription meant a background bot's CALC_FAILED
            // events never reached onPathEvent, so unreachable-goal tracking
            // never engaged and hunt re-dispatched the dead goal forever.
            IBaritone baritone = null;
            EntityPlayerSP p = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
            if (p != null) {
                baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(p);
            }
            if (baritone == null) {
                baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            }
            IEventBus eventBus = baritone == null ? null : baritone.getGameEventHandler();
            if (eventBus == null || eventBus == rt().registeredBaritoneEventBus) {
                return;
            }
            eventBus.registerEventListener(this);
            rt().registeredBaritoneEventBus = eventBus;
        } catch (Throwable ignored) {
        }
    }

    private boolean isTeleportAttackRecoveryActive() {
        RuntimeState rt = rt();
        return rt().activeTeleportTourExecution != null
                || (rt().activeTeleportAttackPlan != null && rt().pendingTeleportReturnPlan != null);
    }

    /** Called on the bound bot thread at network cadence, independent of world TPS. */
    public void tickTeleportFast() {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundSession() == null) return;
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            EntityPlayerSP player = ctxPlayer();
            int actions = getTeleportActionsPerTick();
            rt.anchorReturnClock.beginTick(actions);
            TeleportTourExecution execution = rt.activeTeleportTourExecution;
            if (execution != null) execution.clock.beginTick(actions);
            // bot 线程每 50ms 才调一次，动作额度必须在这唯一一次调用里用满，
            // 否则「一拍一个动作」就是天花板。额度是操作者给的，时钟只负责放行。
            for (int i = 0; i < actions; i++) {
                if (rt.activeTeleportTourExecution != null) {
                    tickTeleportTourExecution(player);
                    if (rt.activeTeleportTourExecution == null) return;
                } else {
                    // 目标全在近战内时主 tick 不建 tour，若这里只走 anchor return，
                    // fast-path 就被晾在被节流的主 tick 上（实测 ~4 burst/s）。
                    // 复用主 tick 缓存的 directTargets，让 fast hook 按动作额度直接
                    // burst，对齐 tour 路径 ~40 burst/s；打不到才回退回家路径。
                    if (tryTeleportDirectBurst(player) <= 0) {
                        tickTeleportAnchorReturn(player);
                        return;
                    }
                }
            }
        }
    }

    /** Anchor position for the teleport hunt, or null when the bot is its own anchor. */
    private Vec3d resolveTeleportAnchorPosition(EntityPlayerSP player) {
        int mode = cfg().teleportAnchorMode;
        if (mode == TELEPORT_ANCHOR_FIXED) {
            return new Vec3d(cfg().teleportAnchorX, cfg().teleportAnchorY, cfg().teleportAnchorZ);
        }
        if (mode != TELEPORT_ANCHOR_SESSION) {
            return null;
        }
        String username = cfg().teleportAnchorSession;
        if (username == null || username.isEmpty()) {
            return null;
        }
        try {
            Object session = com.mythos.mythosScriptMod.mcp.NexusSessions.resolve(username);
            EntityPlayerSP anchor = session == null ? null
                    : com.mythos.mythosScriptMod.mcp.NexusSessions.player(session);
            if (anchor == null || anchor == player) {
                return null;
            }
            return anchor.getPositionVector();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Walks the bot home to its anchor while no tour is running. The legacy
     * behaviour retraced the tour and stopped wherever it started; with an
     * operator-controlled anchor the bot must instead converge on the anchor
     * between fights, including after the aura is switched off.
     */
    /** True while a wave of fighting is still going, so the virtual position must
     *  stay where it is instead of walking home between attacks. */
    private boolean staysAwayFromBody() {
        if (!rt().enabled || !isTeleportAttackMode()) {
            return false;
        }
        TeleportAttackFeedback last = rt().teleport.lastAttack;
        return last != null && System.nanoTime() - last.sentNanos < TELEPORT_VIRTUAL_STAY_NANOS;
    }

    /**
     * 一圈站跑完后重新装弹：回到第一个还有活人的站，位移腿也一起重置。
     * 为什么必须重装：一次 tour 只打一轮的话，剩下的动作额度全花在等下一次规划上——
     * 实测单只村民只有 33 包/秒，而 2 动作/拍 × 8 发 = 320 包/秒才是这条路径的上限。
     * 为什么重置位移腿而不是就地重打：就地重打会让 bot 钉在第一个站上不动，
     * 六个围栏只打到身边那几个（实测 travel 归零、targetsHit 掉到 12/24）。
     */
    private boolean rearmTeleportTourTargets(EntityPlayerSP player, TeleportTourExecution execution) {
        if (player == null || execution == null || execution.plan == null
                || execution.lastSentPosition == null) return false;
        // 续打的前提是「没有别的活目标等着打」：脚下这一圈打完、附近也不剩目标时，
        // 拆掉 tour 再等下一次规划只是白白空转。只要攻击范围内还有不在 plan 里的
        // 活目标，就必须照原路收尾交给下一轮规划，否则 bot 会钉在最后一站不动
        // （实测六个围栏下位移从 47 格/秒掉到 0，只覆盖到身边 11 只）。
        // 单只和挤一点这两种布局全场就只有 plan 里这些目标，所以照常续打。
        if (hasLivingTargetOutsidePlan(player, execution)) return false;
        double meleeLimit = getTeleportMeleeRange() + 0.05D;
        double meleeLimitSq = meleeLimit * meleeLimit;
        for (TeleportTourStop stop : execution.plan.stops) {
            if (stop == null || stop.position == null) continue;
            if (getDistanceSq(execution.lastSentPosition, stop.position.x, stop.position.y,
                    stop.position.z) > meleeLimitSq) return false;
        }
        boolean anyInMelee = false;
        for (TeleportTourStop stop : execution.plan.stops) {
            if (stop == null || stop.targets == null) continue;
            for (EntityLivingBase target : stop.targets) {
                if (!isLivingTourTarget(player, target)) continue;
                if (getDistanceSq(execution.lastSentPosition, target.posX, target.posY, target.posZ)
                        > meleeLimitSq) return false;
                anyInMelee = true;
            }
        }
        if (!anyInMelee) return false;
        // 走到这里说明所有活目标都在脚下：重开这一圈（含位移腿），
        // 让 burst 继续按动作额度出手，而不是每轮都等一次新规划。
        execution.stopIndex = 0;
        execution.legIndex = 0;
        execution.targetIndex = 0;
        execution.rearms++;
        return true;
    }

    /** 攻击范围内是否还有不在这次 plan 里的活目标。 */
    private boolean hasLivingTargetOutsidePlan(EntityPlayerSP player, TeleportTourExecution execution) {
        if (player == null || player.world == null) return false;
        java.util.Set<Integer> planned = new java.util.HashSet<>();
        for (TeleportTourStop stop : execution.plan.stops) {
            if (stop == null || stop.targets == null) continue;
            for (EntityLivingBase target : stop.targets) {
                if (isLivingTourTarget(player, target)) planned.add(target.getEntityId());
            }
        }
        double range = cfg().attackRange;
        List<Entity> nearby = player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(range));
        if (nearby == null) return false;
        double rangeSq = range * range;
        double meleeLimit = getTeleportMeleeRange() + 0.05D;
        double meleeLimitSq = meleeLimit * meleeLimit;
        for (Entity entity : nearby) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!isLivingTourTarget(player, living)) continue;
            if (planned.contains(living.getEntityId())) continue;
            // 必须按同一套过滤条件判：附近还有别的 bot 玩家，targetPlayers 关着时
            // 它们根本不会被当成目标，若把它们算成「没打过的目标」，
            // 单只和挤一点这两种布局会永远等不到续打（实测掉回 33 包/秒）。
            if (buildTargetCandidate(player, living, rangeSq, false, false, true, null) == null) continue;
            // 只有「要走过去才打得到」的目标才算必须收尾：脚下就够得着的目标
            // 留在这圈续打里顺手就打掉了。挤一点布局全场目标都堆在同一处，
            // 若把够得着的也算成待赶路，它的续打会永远不触发（实测掉回 103 包/秒）。
            if (player.getDistanceSq(living) <= meleeLimitSq) continue;
            return true;
        }
        return false;
    }

    private boolean isLivingTourTarget(EntityPlayerSP player, EntityLivingBase target) {
        return player != null && target != null && target.world == player.world
                && target.isEntityAlive() && target.getHealth() > 0.0F
                && player.world.getEntityByID(target.getEntityId()) == target;
    }

    /** fast hook 专用：主 tick 扫出的目标若全在近战内，就按动作额度直接打一轮 burst。
     *  返回值 ≤0 表示打不了（目标不全在近战、 aura 停了、正在回家等），调用方回退 anchor return。 */
    private int tryTeleportDirectBurst(EntityPlayerSP player) {
        RuntimeState rt = rt();
        List<EntityLivingBase> targets = rt.teleport.directTargets;
        if (player == null || player.connection == null || !player.isEntityAlive()
                || targets == null || targets.isEmpty()) return 0;
        if (!rt.enabled || !isTeleportAttackMode() || rt.teleport.timing.isRouteRejected()
                || isTeleportAttackRecoveryActive()) return 0;
        if (!teleportAttackSlotDue()) {
            rt.teleport.attackSkipsInterval++;
            return 0;
        }
        Vec3d serverPosition = rt.teleport.virtualPosition != null
                ? rt.teleport.virtualPosition : player.getPositionVector();
        if (!allTeleportTargetsInMelee(player, serverPosition, targets)) return 0;
        return attackTeleportMeleeBurst(player, serverPosition, targets, teleportBurstHitLimit());
    }

    /** LOCK 下服务器此刻认为玩家在哪：巡游中是最后一个发出的位置，停驻时是虚拟位置。 */
    private Vec3d currentLockServerPosition() {
        RuntimeState rt = rt();
        TeleportTourExecution execution = rt.activeTeleportTourExecution;
        return execution != null && execution.lastSentPosition != null
                ? execution.lastSentPosition : rt.teleport.virtualPosition;
    }

    /** 关杀戮那一刻决定要不要回程；只有本地玩家有「视角」，后台 bot 不需要。 */
    private TeleportViewReturn beginTeleportViewReturn() {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundSession() != null || !isTeleportAttackMode()) {
            return null;
        }
        EntityPlayerSP player = ctxPlayer();
        if (player == null || player.connection == null || player.world == null || !player.isEntityAlive()) {
            return null;
        }
        Vec3d body = player.getPositionVector();
        if (TELEPORT_VIEW_AVATAR.equals(cfg().teleportViewMode)) {
            Vec3d avatar = TeleportCompanionHandler.position();
            return TeleportCompanionHandler.isActive() && avatar != null
                    && avatar.squareDistanceTo(body) > TELEPORT_ANCHOR_ARRIVE_SQ
                    ? new TeleportViewReturn(true, player.world) : null;
        }
        if (TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)) {
            Vec3d server = currentLockServerPosition();
            return server != null && server.squareDistanceTo(body) > TELEPORT_ANCHOR_ARRIVE_SQ
                    ? new TeleportViewReturn(false, player.world) : null;
        }
        // FOLLOW 的镜头就在本体上，本体就是服务器位置，没有可回的地方。
        return null;
    }

    private void tickTeleportViewReturn(EntityPlayerSP player) {
        RuntimeState rt = rt();
        TeleportViewReturn viewReturn = rt.teleportViewReturn;
        if (viewReturn == null) return;
        boolean stillValid = !rt.enabled && isTeleportAttackMode()
                && player != null && player.connection != null && player.isEntityAlive()
                && player.world == viewReturn.world
                && (viewReturn.avatar
                        ? TELEPORT_VIEW_AVATAR.equals(cfg().teleportViewMode) && TeleportCompanionHandler.isActive()
                        : TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode) && rt.teleport.virtualPosition != null);
        if (!stillValid || ++viewReturn.ageTicks > TELEPORT_VIEW_RETURN_MAX_TICKS) {
            finishTeleportViewReturn(player, viewReturn, false);
            return;
        }
        // 和巡游同一套节奏：每 tick 按「每 tick 动作数」走，每步不超过单动作位移。
        int actions = getTeleportActionsPerTick();
        for (int i = 0; i < actions && rt.teleportViewReturn == viewReturn; i++) {
            if (!advanceTeleportViewReturn(player, viewReturn)) break;
        }
    }

    /** 走一步；返回 false 表示这一 tick 不再继续（到了、被挡或额度不够）。 */
    private boolean advanceTeleportViewReturn(EntityPlayerSP player, TeleportViewReturn viewReturn) {
        RuntimeState rt = rt();
        Vec3d current = viewReturn.avatar ? player.getPositionVector() : rt.teleport.virtualPosition;
        Vec3d goal = viewReturn.avatar ? TeleportCompanionHandler.position() : player.getPositionVector();
        if (current == null || goal == null) {
            finishTeleportViewReturn(player, viewReturn, false);
            return false;
        }
        if (current.squareDistanceTo(goal) <= TELEPORT_ANCHOR_ARRIVE_SQ) {
            // AVATAR 最后补一跳贴到化身脚下：镜头切回本体时位置和朝向都要对得上，否则会闪一下。
            if (viewReturn.avatar && current.squareDistanceTo(goal) > 1.0E-6D
                    && isTeleportStepClear(player, current, goal) && reserveTeleportPackets(player, 1)) {
                sendTeleportViewReturnStep(player, goal, true);
            }
            finishTeleportViewReturn(player, viewReturn, true);
            return false;
        }
        // 化身还在被操作者走动；目标漂远了就重新找路，否则本体会被送到化身几秒前的位置。
        boolean routeSpent = viewReturn.routeGoal == null || viewReturn.routeIndex >= viewReturn.route.size();
        boolean goalDrifted = viewReturn.routeGoal != null
                && viewReturn.routeGoal.squareDistanceTo(goal) > TELEPORT_VIEW_RETURN_REROUTE_SQ
                && viewReturn.ageTicks - viewReturn.routeTick >= TELEPORT_VIEW_RETURN_REROUTE_INTERVAL_TICKS;
        if (routeSpent || goalDrifted) {
            List<Vec3d> route = findTeleportRoute(player, current, goal);
            if (route != null && route.size() >= 2) {
                viewReturn.route = route;
                viewReturn.routeIndex = 1;
            } else {
                // 找不到绕路时仍试直线：下面的逐步碰撞检查会拦住穿墙，连续拦住才放弃。
                viewReturn.route = Collections.singletonList(goal);
                viewReturn.routeIndex = 0;
            }
            viewReturn.routeGoal = goal;
            viewReturn.routeTick = viewReturn.ageTicks;
        }
        Vec3d waypoint = viewReturn.route.get(viewReturn.routeIndex);
        double stepLimit = Math.min(getTeleportStepDistancePerAction(), getTeleportStepDistance());
        double distance = current.distanceTo(waypoint);
        Vec3d next = distance <= stepLimit ? waypoint
                : current.add(waypoint.subtract(current).scale(stepLimit / distance));
        if (!isTeleportStepClear(player, current, next) || !isTeleportPositionClear(player, next)) {
            viewReturn.routeGoal = null;
            if (++viewReturn.blockedTicks > TELEPORT_VIEW_RETURN_BLOCKED_TICKS) {
                finishTeleportViewReturn(player, viewReturn, false);
            }
            return false;
        }
        viewReturn.blockedTicks = 0;
        if (!reserveTeleportPackets(player, 1)) return false;
        sendTeleportViewReturnStep(player, next, viewReturn.avatar);
        if (next.squareDistanceTo(waypoint) <= 1.0E-6D) viewReturn.routeIndex++;
        return true;
    }

    private void sendTeleportViewReturnStep(EntityPlayerSP player, Vec3d next, boolean moveBody) {
        RuntimeState rt = rt();
        boolean grounded = !player.world.getCollisionBoxes(player,
                getTeleportPlayerBox(player, next).offset(0.0D, -0.0625D, 0.0D)).isEmpty();
        if (moveBody) {
            // AVATAR：本体就是服务器位置，必须跟着走；镜头在化身上，本体怎么跳都不晃视角。
            player.setPosition(next.x, next.y, next.z);
            player.motionX = player.motionY = player.motionZ = 0.0D;
            player.onGround = grounded;
        }
        rt.teleport.sendingTeleportPosition = true;
        try {
            player.connection.sendPacket(new CPacketPlayer.Position(next.x, next.y, next.z, grounded));
        } finally {
            rt.teleport.sendingTeleportPosition = false;
        }
        if (!moveBody) {
            rt.teleport.virtualPosition = next;
        }
    }

    private void finishTeleportViewReturn(EntityPlayerSP player, TeleportViewReturn viewReturn, boolean arrived) {
        RuntimeState rt = rt();
        if (rt.teleportViewReturn != viewReturn) return;
        rt.teleportViewReturn = null;
        if (viewReturn.avatar) {
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (arrived && player != null && view != null && view != player && TeleportCompanionHandler.isActive()) {
                // 镜头接下来切回本体，朝向沿用化身的，操作者才不会觉得视角被扭了一下。
                player.rotationYaw = player.prevRotationYaw = view.rotationYaw;
                player.rotationPitch = player.prevRotationPitch = view.rotationPitch;
                player.rotationYawHead = player.prevRotationYawHead = view.getRotationYawHead();
            }
            // 化身由 onClientTick 随后的 start/stop 判断拆掉；这里不直接 stop，
            // 是为了回程中途重新开启 AVATAR 杀戮时不把正在用的化身拆了。
        } else if (!rt.enabled) {
            // 到了：服务器已经在本体身边；没到：放弃回程，交还原版位置包（即旧行为）。
            rt.teleport.virtualPosition = null;
        }
        teleportDebug(player, "VIEW_RETURN_END", "avatar=%s,arrived=%s,ageTicks=%d",
                viewReturn.avatar, arrived, viewReturn.ageTicks);
    }

    private void tickTeleportAnchorReturn(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null || player.connection == null || !player.isEntityAlive()) return;
        if (rt.activeTeleportAttackPlan != null || rt.pendingTeleportReturnPlan != null) return;
        Vec3d anchor = resolveTeleportAnchorPosition(player);
        if (anchor == null) {
            // No anchor configured: the only "home" that matters is leaving the
            // virtual position behind and putting the server back on the body.
            if (rt.teleport.virtualPosition == null) return;
            anchor = player.getPositionVector();
        }
        Vec3d current = rt.teleport.virtualPosition != null
                ? rt.teleport.virtualPosition : player.getPositionVector();
        if (current.squareDistanceTo(anchor) <= TELEPORT_ANCHOR_ARRIVE_SQ) {
            rt.teleport.virtualPosition = null;
            return;
        }
        TeleportAttackFeedback lastAttack = rt.teleport.lastAttack;
        if (rt.enabled && lastAttack != null
                && System.nanoTime() - lastAttack.sentNanos < TELEPORT_ANCHOR_IDLE_NANOS) return;
        if (!rt.anchorReturnClock.tryAdvance(getTeleportActionsPerTick(), System.nanoTime())) return;
        double distance = current.distanceTo(anchor);
        if (distance <= 1.0E-4D) return;
        double ratio = Math.min(1.0D, Math.min(getTeleportStepDistancePerAction(), getTeleportStepDistance()) / distance);
        Vec3d next = current.add(anchor.subtract(current).scale(ratio));
        if (!isTeleportStepClear(player, current, next) || !isTeleportPositionClear(player, next)) return;
        if (!reserveTeleportPackets(player, 1)) return;
        // Body stays put; only the server-side position travels home.
        boolean grounded = !player.world.getCollisionBoxes(player,
                getTeleportPlayerBox(player, next).offset(0.0D, -0.0625D, 0.0D)).isEmpty();
        rt.teleport.sendingTeleportPosition = true;
        try {
            player.connection.sendPacket(new CPacketPlayer.Position(next.x, next.y, next.z, grounded));
        } finally {
            rt.teleport.sendingTeleportPosition = false;
        }
        rt.teleport.virtualPosition = next.squareDistanceTo(anchor) <= TELEPORT_ANCHOR_ARRIVE_SQ ? null : next;
    }

    /** One paced movement per action; packet ordering places attacks after arrival. */
    private void tickTeleportTourExecution(EntityPlayerSP player) {
        RuntimeState rt = rt();
        TeleportTourExecution execution = rt.activeTeleportTourExecution;
        if (execution == null) return;
        if (player == null || player != execution.player || player.connection != execution.connection
                || player.world != execution.world || !player.isEntityAlive()
                || rt.teleport.timing.isRouteRejected()) {
            clearTeleportAttackState(player, "TOUR_OWNER_OR_CONNECTION_CHANGED");
            return;
        }
        long now = System.nanoTime();
        if (!execution.clock.tryAdvance(getTeleportActionsPerTick(), now)) return;
        if (++execution.ageTicks > TELEPORT_ATTACK_MAX_EXECUTION_BOT_TICKS * getTeleportActionsPerTick()
                || (execution.startedEnabled && !rt.enabled) || !isTeleportAttackMode()) {
            clearTeleportAttackState(player, "TOUR_ABORTED");
            return;
        }
        Vec3d localPosition = player.getPositionVector();
        if (localPosition.squareDistanceTo(execution.lastLocalPosition) > 4.0D) {
            clearTeleportAttackState(player, "TOUR_EXTERNAL_MOVEMENT");
            return;
        }
        execution.lastLocalPosition = localPosition;
        // The body is never moved by the tour, so its momentum belongs to the
        // player's own walking and must not be cleared here.
        if (execution.returning) {
            if (execution.returnIndex >= 0) {
                Vec3d destination = execution.traversed.get(execution.returnIndex);
                if (advanceTeleportTourToward(player, execution, destination, false)) execution.returnIndex--;
                return;
            }
            // The retraced path ends at the feet the tour launched from. With an
            // anchor elsewhere that is not home, so cover the remaining gap.
            if (execution.lastSentPosition.distanceTo(execution.returnPosition) > 1.0E-4D) {
                advanceTeleportTourToward(player, execution, execution.returnPosition, false);
                return;
            }
            clearTeleportAttackState(player, "TOUR_RETURN_COMPLETE");
            return;
        }
        // Consume bookkeeping without spending extra network ticks on it.
        while (execution.stopIndex < execution.plan.stops.size()) {
            TeleportTourStop candidate = execution.plan.stops.get(execution.stopIndex);
            if (execution.legIndex < candidate.legPositions.size()
                    || execution.targetIndex < candidate.targets.size()) break;
            execution.stopIndex++;
            execution.legIndex = execution.targetIndex = 0;
        }
        if (execution.stopIndex >= execution.plan.stops.size()) {
            // 站走完但身边还有目标时把名额重新打开：一次 tour 只换一轮 burst 的话，
            // 大部分动作额度都花在「等下一次规划」上，实测单只村民只有 33 包/秒，
            // 而 2 动作/拍 × 8 发 = 320 包/秒才是这条路径的上限。
            if (!staysAwayFromBody() || !rearmTeleportTourTargets(player, execution)) {
                if (staysAwayFromBody()) {
                    // Still fighting: park the virtual position here and let the next
                    // plan chain onward. The body never moved, so the view is unchanged.
                    rt.teleport.virtualPosition = execution.lastSentPosition;
                    clearTeleportAttackState(player, "TOUR_SEGMENT_COMPLETE");
                    return;
                }
                execution.returning = true;
                execution.returnIndex = execution.traversed.size() - 2;
                if (execution.returnIndex >= 0
                        && advanceTeleportTourToward(player, execution,
                                execution.traversed.get(execution.returnIndex), false)) execution.returnIndex--;
                return;
            }
        }
        TeleportTourStop stop = execution.plan.stops.get(execution.stopIndex);
        // 群攻名额必须紧贴 45ms 动作闸门，否则 22 拍/秒的动作只换 22 包/秒；
        // 但 minAttackIntervalTicks 是操作者唯一能表达「每几拍出手一次」的入口，
        // 所以间隔判断只保留在这里：为 0 时恒真（不额外限流），大于 0 时如实降频。
        int burstHits = 0;
        if (teleportAttackSlotDue()) {
            burstHits = attackTeleportMeleeBurst(player, execution.lastSentPosition,
                    collectTeleportTourTargets(execution), teleportBurstHitLimit());
        } else {
            rt().teleport.attackSkipsInterval++;
        }
        boolean attackedThisTick = burstHits > 0;
        if (execution.legIndex < stop.legPositions.size()) {
            if (!advanceTeleportTourToward(player, execution, stop.legPositions.get(execution.legIndex), true)) return;
            execution.legIndex++;
            if (execution.legIndex < stop.legPositions.size()) return;
        }
        if (attackedThisTick) {
            // 一次 burst 已经把这站能打的名额打掉了，按实际出手数核销：
            // 只 +1 会让同一站剩下的名额变成对无敌帧目标的空转发包。
            execution.targetIndex = Math.min(execution.targetIndex + burstHits, stop.targets.size());
            return;
        }
        // TCP delivers the position before UseEntity. No additional idle tick is required.
        if (!teleportAttackSlotDue()) {
            rt().teleport.attackSkipsInterval++;
            return;
        }
        EntityLivingBase target = pickTeleportMeleeTarget(player, execution.lastSentPosition, stop.targets);
        if (target == null) {
            execution.targetIndex = stop.targets.size();
            return;
        }
        if (!isTeleportTargetReady(target)) {
            rt().teleport.attackSkipsNotReady++;
            teleportDebug(player, "TOUR_TARGET_NOT_READY", "targetId=%d,hurtResistantTime=%d,maxHurt=%d",
                    target.getEntityId(), target.hurtResistantTime, target.maxHurtResistantTime);
            return;
        }
        float tourCooled = getAttackCooledStrength(player);
        if (tourCooled < cfg().minAttackStrength) {
            rt().teleport.attackSkipsStrength++;
            teleportDebug(player, "TOUR_TARGET_COOLING", "cooled=%.3f,min=%.3f", tourCooled, cfg().minAttackStrength);
            return;
        }
        if (sendTeleportAttack(player, target, execution.lastSentPosition, now)) {
            execution.targetIndex++;
        }
    }

    /** 一次出手能打几只。targetsPerAttack 是操作者表达群攻规模的唯一入口，
     *  固定 8 会让默认值 3 与 50 在实际发包数上完全一样，等于这个设置失效。 */
    private int teleportBurstHitLimit() {
        return Math.max(TELEPORT_ATTACK_MAX_HITS_PER_TICK, Math.max(1, cfg().targetsPerAttack));
    }

    private boolean teleportAttackSlotDue() {
        KillAuraTeleportState teleport = rt().teleport;
        if (cfg().minAttackIntervalTicks <= 0) return true;
        if (teleport.lastAttackTick == Integer.MIN_VALUE) return true;
        return teleport.currentTick() - teleport.lastAttackTick >= cfg().minAttackIntervalTicks;
    }

    private List<EntityLivingBase> collectTeleportTourTargets(TeleportTourExecution execution) {
        List<EntityLivingBase> targets = new ArrayList<>();
        for (TeleportTourStop stop : execution.plan.stops) {
            if (stop.targets != null) targets.addAll(stop.targets);
        }
        return targets;
    }

    private boolean allTeleportTargetsInMelee(EntityPlayerSP player, Vec3d from,
            List<EntityLivingBase> targets) {
        if (player == null || from == null || targets == null || targets.isEmpty()) return false;
        double meleeLimit = getTeleportMeleeRange() + 0.05D;
        double limitSq = meleeLimit * meleeLimit;
        for (EntityLivingBase target : targets) {
            if (target == null || target.world != player.world || !target.isEntityAlive()
                    || player.world.getEntityByID(target.getEntityId()) != target) continue;
            if (getDistanceSq(from, target.posX, target.posY, target.posZ) > limitSq) return false;
        }
        return true;
    }

    private EntityLivingBase pickTeleportMeleeTarget(EntityPlayerSP player, Vec3d from,            List<EntityLivingBase> candidates) {
        if (player == null || from == null || candidates == null || candidates.isEmpty()) return null;
        KillAuraTeleportState teleport = rt().teleport;
        double meleeLimit = getTeleportMeleeRange() + 0.05D;
        double limitSq = meleeLimit * meleeLimit;
        // 轮转靠"最久没被打到的先打"：只按距离挑就会一直盯着最近那只，
        // 而距离排序在 tick 之间几乎不变。
        EntityLivingBase ready = null;
        int readyTick = Integer.MAX_VALUE;
        double readyDistanceSq = Double.MAX_VALUE;
        EntityLivingBase fallback = null;
        int fallbackTick = Integer.MAX_VALUE;
        double fallbackDistanceSq = Double.MAX_VALUE;
        for (EntityLivingBase candidate : candidates) {
            if (candidate == null || candidate.world != player.world || !candidate.isEntityAlive()
                    || player.world.getEntityByID(candidate.getEntityId()) != candidate) continue;
            double distanceSq = getDistanceSq(from, candidate.posX, candidate.posY, candidate.posZ);
            if (distanceSq > limitSq) continue;
            int lastTick = teleport.teleportLastAttackTicks.getOrDefault(
                    candidate.getEntityId(), Integer.MIN_VALUE);
            if (lastTick < fallbackTick || (lastTick == fallbackTick && distanceSq < fallbackDistanceSq)) {
                fallback = candidate;
                fallbackTick = lastTick;
                fallbackDistanceSq = distanceSq;
            }
            if (!isTeleportTargetReady(candidate)) continue;
            if (lastTick < readyTick || (lastTick == readyTick && distanceSq < readyDistanceSq)) {
                ready = candidate;
                readyTick = lastTick;
                readyDistanceSq = distanceSq;
            }
        }
        if (ready == null && fallback != null) teleport.attackSkipsNotReady++;
        return ready != null ? ready : fallback;
    }

    /** 把身边所有能打的目标在同一 tick 内轮流打一遍，返回成功出手数。
     *  minAttackIntervalTicks > 0 时第一发就会占掉名额，等价于仍然受间隔约束。 */
    private int attackTeleportMeleeBurst(EntityPlayerSP player, Vec3d from,
            List<EntityLivingBase> candidates, int limit) {
        if (player == null || from == null || candidates == null || candidates.isEmpty() || limit <= 0) return 0;
        List<EntityLivingBase> remaining = new ArrayList<>(candidates);
        // meleeCandidates 记的是「半径内可打的目标数」，用来判断加大站点半径后
        // 到底是候选池真的变大了，还是只是多发了几包空转。
        double meleeLimit = getTeleportMeleeRange() + 0.05D;
        double meleeLimitSq = meleeLimit * meleeLimit;
        int inMelee = 0;
        for (EntityLivingBase candidate : candidates) {
            if (candidate == null || candidate.world != player.world || !candidate.isEntityAlive()
                    || player.world.getEntityByID(candidate.getEntityId()) != candidate) continue;
            if (getDistanceSq(from, candidate.posX, candidate.posY, candidate.posZ) <= meleeLimitSq) inMelee++;
        }
        rt().teleport.teleportBurstTicks++;
        rt().teleport.teleportMeleeCandidates += inMelee;
        int sent = 0;
        while (sent < limit && teleportAttackSlotDue()) {
            EntityLivingBase target = pickTeleportMeleeTarget(player, from, remaining);
            if (target == null) break;
            if (getAttackCooledStrength(player) < cfg().minAttackStrength) {
                rt().teleport.attackSkipsStrength++;
                break;
            }
            // pick 在没有 ready 目标时会回退到最该打的那只，照发就是给无敌帧目标
            // 空转发包；直接 break 更糟——一只不 ready 就让整拍 0 命中。把它从候选
            // 池里摘掉再继续，既不会拿到同一只，也保住了这一拍的其它目标。
            if (!isTeleportTargetReady(target)) {
                rt().teleport.attackSkipsNotReady++;
                remaining.remove(target);
                continue;
            }
            if (!sendTeleportAttack(player, target, from, System.nanoTime())) break;
            // 只剩一只时不要把它从候选池摘掉：摘掉就等于「每拍最多打同一只一次」，
            // 单目标场景的实际攻速被钉死在 20 次/秒。抢怪要的是发包密度，
            // 所以同一只允许在这一拍里被重复选中，直到用满名额。
            if (remaining.size() > 1) remaining.remove(target);
            sent++;
        }
        return sent;
    }

    private boolean sendTeleportAttack(EntityPlayerSP player, EntityLivingBase target, Vec3d aimFrom, long now) {
        if (player == null || player.connection == null || target == null) return false;
        int packets = shouldSendTeleportRotationPacket() ? 2 : 1;
        if (!reserveTeleportPackets(player, packets)) return false;
        if (shouldSendTeleportRotationPacket()) {
            Rotation rotation = getFastTeleportAttackRotation(player, target, aimFrom);
            player.connection.sendPacket(new CPacketPlayer.Rotation(rotation.getYaw(), rotation.getPitch(), player.onGround));
        }
        player.connection.sendPacket(new CPacketUseEntity(target));
        RuntimeState rt = rt();
        rt.teleport.attackSends++;
        rt.teleport.lastAttackTick = rt.teleport.currentTick();
        rt.teleport.timing.recordTarget(target.getEntityId(), target.maxHurtResistantTime, now);
        rt.teleport.lastAttack = new TeleportAttackFeedback(player, target, now);
        rt.teleport.recentAttacks.put(target.getEntityId(), rt.teleport.lastAttack);
        recordNoDamageAttackAttempt(target);
        rememberBlockingAttack(player, target);
        rt.teleport.teleportLastAttackTicks.put(target.getEntityId(), rt.teleport.currentTick());
        rt.teleport.teleportTargetRetryAfterTicks.remove(target.getEntityId());
        rt.teleport.teleportTargetRetryUntilMillis.remove(target.getEntityId());
        rt.teleport.teleportTargetFailureCounts.remove(target.getEntityId());
        player.resetCooldown();
        rt.teleport.lastAttackWeapon = player.getHeldItemMainhand().copy();
        rt.teleport.timing.recordAttack(now);
        player.swingArm(EnumHand.MAIN_HAND);
        trimTeleportRuntimeMaps();
        return true;
    }

    private boolean advanceTeleportTourToward(EntityPlayerSP player, TeleportTourExecution execution,
            Vec3d destination, boolean outbound) {
        Vec3d current = execution.lastSentPosition;
        double distance = current.distanceTo(destination);
        if (distance <= 1.0E-4D) return true;
        double ratio = Math.min(1.0D, Math.min(getTeleportStepDistancePerAction(), getTeleportStepDistance()) / distance);
        Vec3d next = current.add(destination.subtract(current).scale(ratio));
        boolean stepClear = isTeleportStepClear(player, current, next);
        boolean destClear = stepClear && isTeleportPositionClear(player, next);
        if (!stepClear || !destClear) {
            teleportDebug(player, "TOUR_STEP_BLOCKED",
                    "stepClear=%s,destClear=%s,current=(%.3f,%.3f,%.3f),next=(%.3f,%.3f,%.3f),"
                            + "dest=(%.3f,%.3f,%.3f),ratio=%.3f,playerPos=(%.3f,%.3f,%.3f)",
                    stepClear, destClear, current.x, current.y, current.z,
                    next.x, next.y, next.z, destination.x, destination.y, destination.z,
                    ratio, player.posX, player.posY, player.posZ);
            clearTeleportAttackState(player, "TOUR_PATH_BLOCKED");
            return false;
        }
        if (!reserveTeleportPackets(player, 1)) return false;
        // The body stays put; only the server is told about the jump. Moving the
        // local entity is what drags the camera through every step.
        boolean grounded = !player.world.getCollisionBoxes(player,
                getTeleportPlayerBox(player, next).offset(0.0D, -0.0625D, 0.0D)).isEmpty();
        if (!TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)) {
            // FOLLOW and AVATAR both let the real body travel: in FOLLOW the camera
            // rides it, in AVATAR the camera is on the companion so the body can move
            // freely without disturbing the view.
            player.setPosition(next.x, next.y, next.z);
            player.motionX = player.motionY = player.motionZ = 0.0D;
            player.onGround = grounded;
            // The tour's own hop must not read as external movement next tick.
            execution.lastLocalPosition = next;
        }
        RuntimeState rt = rt();
        rt.teleport.sendingTeleportPosition = true;
        try {
            player.connection.sendPacket(new CPacketPlayer.Position(next.x, next.y, next.z, grounded));
        } finally {
            rt.teleport.sendingTeleportPosition = false;
        }
        execution.lastSentPosition = next;
        if (outbound) execution.traversed.add(next);
        return next.squareDistanceTo(destination) <= 1.0E-8D;
    }

    private void tickTeleportAttackRecovery(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (rt.activeTeleportTourExecution != null) {
            // 主 tick 是本地玩家唯一能推进巡游的地方（fast hook 只在后台 bot
            // 线程上跑），所以这里必须按操作者额度连推，而不是一拍一推。
            int actions = getTeleportActionsPerTick();
            for (int i = 0; i < actions && rt.activeTeleportTourExecution != null; i++) {
                tickTeleportTourExecution(player);
            }
            return;
        }
        if (rt.teleport.timing.isRouteRejected()) {
            if (rt.activeTeleportAttackPlan != null || rt.pendingTeleportReturnPlan != null) {
                clearTeleportAttackState(player, "REJECTED_ROUTE_CANCELLED");
            }
            return;
        }
        if (rt().pendingTeleportReturnTicks > 0) {
            rt().pendingTeleportReturnTicks--;
        }
        if (rt().activeTeleportAttackPlan == null) {
            return;
        }
        if (player == null || player.connection == null) {
            clearTeleportAttackState(player, "PLAYER_OR_CONNECTION_NULL");
            return;
        }

        if (rt().pendingTeleportReturnPlan != null) {
            if (!tryExecutePendingTeleportReturn(player)) {
                teleportDebug(player, "RECOVERY_WAIT",
                        "reason=PENDING_RETURN_NOT_EXECUTED,returnTicks=%d,notBeforeTick=%d,packetsUsed=%d",
                        rt().pendingTeleportReturnTicks, rt().pendingTeleportReturnPlan.notBeforeTick,
                        rt().teleport.teleportPacketsUsedThisTick);
                return;
            }
            teleportDebug(player, "RECOVERY_RETURN_SENT", "returnTicks=%d", rt().pendingTeleportReturnTicks);
            return;
        }

        if (isPlayerNearTeleportOrigin(player, rt().activeTeleportAttackPlan)) {
            rt().activeTeleportAttackPlan.returnCompleted = true;
            if (rt().pendingTeleportReturnTicks <= 0) {
                clearTeleportAttackState(player, "AT_ORIGIN_AND_WINDOW_EXPIRED");
            }
            return;
        }

        if (rt().pendingTeleportReturnTicks > 0) {
            teleportDebug(player, "RECOVERY_WAIT",
                    "reason=AWAITING_SERVER_CORRECTION,returnTicks=%d,player=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    rt().pendingTeleportReturnTicks, player.posX, player.posY, player.posZ,
                    rt().activeTeleportAttackPlan.originX, rt().activeTeleportAttackPlan.originY,
                    rt().activeTeleportAttackPlan.originZ);
            return;
        }

        TeleportAttackPlan correctionPlan = rt().activeTeleportCorrectionPlan == null
                ? rt().activeTeleportAttackPlan
                : rt().activeTeleportCorrectionPlan;
        if (correctionPlan.correctedByServer
                && correctionPlan.correctionCount < TELEPORT_ATTACK_MAX_CORRECTIONS
                && rt().teleport.currentTick() != rt().lastTeleportCorrectionTick) {
            if (!scheduleTeleportCorrectionReturn(player, correctionPlan,
                    player.posX, player.posY, player.posZ)) {
                clearTeleportAttackState(player, "CORRECTION_RETURN_PLANNING_FAILED_AFTER_WINDOW");
                return;
            }
            tryExecutePendingTeleportReturn(player);
            return;
        }

        clearTeleportAttackState(player, "RECOVERY_WINDOW_ENDED_WITHOUT_RETRY");
    }

    private void clearTeleportAttackState() {
        Minecraft mc = Minecraft.getMinecraft();
        clearTeleportAttackState(mc == null ? null : ctxPlayer(), "UNSPECIFIED");
    }

    private void clearTeleportAttackState(EntityPlayerSP player, String reason) {
        RuntimeState rt = rt();
        if (rt.activeTeleportTourExecution != null || rt().activeTeleportAttackPlan != null) {
            rt.teleport.teleportEndReasons.merge(reason, 1, Integer::sum);
        }
        teleportDebug(player, "RECOVERY_CLEAR",
                "reason=%s,activePlan=%s,correctionPlan=%s,pendingReturn=%s,returnTicks=%d,lastCorrectionTick=%d",
                reason, rt().activeTeleportAttackPlan != null, rt().activeTeleportCorrectionPlan != null,
                rt().pendingTeleportReturnPlan != null, rt().pendingTeleportReturnTicks,
                rt().lastTeleportCorrectionTick);
        rt.activeTeleportTourExecution = null;
        rt().activeTeleportAttackPlan = null;
        rt().activeTeleportCorrectionPlan = null;
        rt().pendingTeleportReturnPlan = null;
        rt().pendingTeleportReturnTicks = 0;
        rt().lastTeleportCorrectionTick = Integer.MIN_VALUE;
    }

    private void syncTeleportPacketBudget(EntityPlayerSP player) {
        rt().teleport.syncPacketBudget();
    }

    private boolean reserveTeleportPackets(EntityPlayerSP player, int packetCount) {
        if (player == null || packetCount < 0) {
            teleportDebug(player, "PACKET_RESERVE_FAIL", "reason=INVALID_INPUT,requested=%d", packetCount);
            return false;
        }
        rt().teleport.reservePackets(packetCount);
        return true;
    }

    private void recordTeleportPackets(EntityPlayerSP player, int packetCount) {
        RuntimeState rt = rt();
        if (player == null || packetCount <= 0) {
            return;
        }
        syncTeleportPacketBudget(player);
        rt().teleport.recordPackets(packetCount);
    }

    private boolean isPlayerNearTeleportOrigin(EntityPlayerSP player, TeleportAttackPlan plan) {
        return player != null && plan != null
                && player.getDistanceSq(plan.originX, plan.originY,
                        plan.originZ) <= TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ;
    }

    private boolean isSamePosition(double leftX, double leftY, double leftZ, double rightX, double rightY,
            double rightZ) {
        double dx = leftX - rightX;
        double dy = leftY - rightY;
        double dz = leftZ - rightZ;
        return dx * dx + dy * dy + dz * dz <= TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        RuntimeState rt = rt();
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != ctxPlayer() || ctxPlayer() == null || ctxWorld() == null) {
            return;
        }

        boolean flyEnabled = FlyHandler.enabled();
        boolean areaHuntActive = rt().areaHuntControlTicks > 0;
        boolean movementProtectionActive = rt().enabled || areaHuntActive || flyEnabled;
        boolean useNoCollision = ((rt().enabled || areaHuntActive) && cfg().enableNoCollision)
                || (flyEnabled && FlyHandler.cfg().enableNoCollision);
        boolean useAntiKnockback = ((rt().enabled || areaHuntActive) && cfg().enableAntiKnockback)
                || (flyEnabled && FlyHandler.cfg().enableAntiKnockback);
        applyKillAuraOwnMovementProtection(ctxPlayer(), movementProtectionActive, useNoCollision, useAntiKnockback);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            if (event.phase == TickEvent.Phase.END) {
                rt.teleport.advanceTick();
            }
            if (event.phase == TickEvent.Phase.END && rt.mcpOriginalFields != null
                    && rt.mcpRemainingTicks > 0 && --rt.mcpRemainingTicks == 0) endMcpRuntime();
            if (!PerformanceMonitor.isFeatureEnabled("kill_aura")) {
                stopAttackBlocking();
                return;
            }
            PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("kill_aura");
            try {
                if (event.phase != TickEvent.Phase.END) {
                    return;
                }

                ensureBaritonePacketListenerRegistered();
                reapplyStaticOverrides();

                Minecraft mc = Minecraft.getMinecraft();
                EntityPlayerSP player = ctxPlayer();
                if (player == null || ctxWorld() == null) {
                    return;
                }

                if (rt().areaHuntControlTicks > 0) {
                    rt().areaHuntControlTicks--;
                    return;
                }

                if (rt().attackCooldownTicks > 0) {
                    if (rt().attackCooldownUntilMillis > 0L) {
                        rt().attackCooldownTicks = (int) Math.max(0L,
                                (rt().attackCooldownUntilMillis - System.currentTimeMillis() + 49L) / 50L);
                    } else {
                        rt().attackCooldownTicks--;
                    }
                }
                if (rt().sequenceCooldownTicks > 0) {
                    if (rt().sequenceCooldownUntilMillis > 0L) {
                        rt().sequenceCooldownTicks = (int) Math.max(0L,
                                (rt().sequenceCooldownUntilMillis - System.currentTimeMillis() + 49L) / 50L);
                    } else {
                        rt().sequenceCooldownTicks--;
                    }
                }
                tickTeleportAttackRecovery(player);
                tickTeleportViewReturn(player);
                if (TELEPORT_VIEW_AVATAR.equals(cfg().teleportViewMode) && rt().enabled
                        && isTeleportAttackMode()) {
                    TeleportCompanionHandler.start();
                } else if (rt().teleportViewReturn == null) {
                    // 回程中镜头必须留在化身上，本体到位后才拆。
                    TeleportCompanionHandler.stop();
                }
                TeleportCompanionHandler.tick();
                if (rt.activeTeleportTourExecution != null) {
                    return;
                }

                boolean flyEnabled = FlyHandler.enabled();
                boolean movementProtectionActive = rt().enabled || flyEnabled;
                boolean useNoCollision = (rt().enabled && cfg().enableNoCollision)
                        || (flyEnabled && FlyHandler.cfg().enableNoCollision);
                boolean useAntiKnockback = (rt().enabled && cfg().enableAntiKnockback)
                        || (flyEnabled && FlyHandler.cfg().enableAntiKnockback);
                applyKillAuraOwnMovementProtection(player, movementProtectionActive, useNoCollision, useAntiKnockback);
                applyFullBright(cfg().enableFullBrightVision);

                if (!rt().enabled) {
                    rt().attackCooldownTicks = 0;
                    rt().attackCooldownUntilMillis = 0L;
                    rt().sequenceCooldownTicks = 0;
                    rt().sequenceCooldownUntilMillis = 0L;
                    rt().currentTargetEntityId = -1;
                    clearAimTargetTransition();
                    stopHuntPickupNavigation();
                    if (!PathSequenceEventListener.isAnyHuntOrbitActionRunning()) {
                        stopHuntNavigation();
                    }
                    this.attackSequenceExecutor.stop();
                    if (!movementProtectionActive) {
                        rt().lastSafeMotionX = 0.0D;
                        rt().lastSafeMotionY = 0.0D;
                        rt().lastSafeMotionZ = 0.0D;
                    }
                    clearNoDamageAttackTracking();
                    clearHuntUnreachableTracking();
                    return;
                }

                if (player.isDead || player.getHealth() <= 0.0F || player.isSpectator()) {
                    rt().currentTargetEntityId = -1;
                    clearAimTargetTransition();
                    stopHuntPickupNavigation();
                    stopHuntNavigation();
                    this.attackSequenceExecutor.stop();
                    clearNoDamageAttackTracking();
                    clearHuntUnreachableTracking();
                    return;
                }

                tickNoDamageAttackTrackers(player);
                tickHuntUnreachableTrackers(player);

                boolean sequenceAttackMode = isSequenceAttackMode();
                if (!sequenceAttackMode && this.attackSequenceExecutor.isRunning()) {
                    this.attackSequenceExecutor.stop();
                }

                if (!rt().aimOnlyMode && !sequenceAttackMode && cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
                    rt().currentTargetEntityId = -1;
                    clearAimTargetTransition();
                    stopHuntPickupNavigation();
                    stopHuntNavigation();
                    return;
                }

                boolean teleportAttackMode = isTeleportAttackMode();
                boolean autoPickupRulePriority = !teleportAttackMode
                        && AutoPickupHandler.INSTANCE.shouldPrioritizeNavigation(player);
                boolean autoPickupRuleAreaActive = !teleportAttackMode
                        && AutoPickupHandler.INSTANCE.isPlayerInsideEnabledRule(player);
                Entity huntPriorityPickupItem = (!isTeleportAttackMode() && !autoPickupRuleAreaActive
                        && isHuntEnabled() && cfg().huntPickupItemsEnabled)
                                ? findHuntPriorityPickupItem(player)
                                : null;

                List<EntityLivingBase> targets = findTargets(player);
                if (teleportAttackMode) {
                    syncTeleportPacketBudget(player);
                    teleportDebug(player, "TICK_STATE",
                            "enabled=%s,targets=%d,targetIds=%s,currentTarget=%d,cache=%d,history=%d,noDamageExcluded=%d,cooldown=%d,gate=%s,packetsUsed=%d,retryAfter=%d,recoveryActive=%s,activeRecovery=%s,pendingReturn=%s,returnTicks=%d",
                            rt().enabled, targets.size(), formatTeleportTargetIds(targets, 24), rt().currentTargetEntityId,
                            rt().teleport.teleportTargetCandidateEntityIds.size(), rt().teleport.teleportLastAttackTicks.size(),
                            rt().noDamageExcludedEntityIds.size(), rt().attackCooldownTicks, getTeleportAttackGateReason(player),
                            rt().teleport.teleportPacketsUsedThisTick,
                            rt().teleportPlanningRetryAfterTick, isTeleportAttackRecoveryActive(),
                            rt().activeTeleportAttackPlan != null, rt().pendingTeleportReturnPlan != null,
                            rt().pendingTeleportReturnTicks);
                }
                EntityEnderCrystal crystalTarget = isTeleportAttackMode() ? null : findBestEnderCrystalTarget(player);
                if (shouldPrioritizeEnderCrystalTarget(player, crystalTarget, targets.isEmpty() ? null : targets.get(0))) {
                    handleEnderCrystalTarget(Minecraft.getMinecraft(), player, crystalTarget);
                    return;
                }
                if (targets.isEmpty()) {
                    if (teleportAttackMode) {
                        teleportDebug(player, "ATTACK_STOP", "reason=NO_TARGETS_AFTER_SCAN");
                    }
                    rt().currentTargetEntityId = -1;
                    clearAimTargetTransition();
                    this.attackSequenceExecutor.stop();
                    if (autoPickupRulePriority) {
                        stopHuntPickupNavigation();
                        stopHuntNavigation();
                        return;
                    }
                    if (huntPriorityPickupItem != null) {
                        stopHuntNavigation();
                        handleHuntPickupMovement(player, huntPriorityPickupItem);
                        return;
                    }
                    stopHuntPickupNavigation();
                    stopHuntNavigation();
                    return;
                }

                EntityLivingBase primaryTarget = targets.get(0);
                updateAimTargetTransition(primaryTarget);
                hunt$dbg("tick target=%s targets=%d huntEnabled=%s navActive=%s pendingDest=%s pendingSolve=%s lastGoal=(%.1f,%.1f,%.1f) goalY=%s targetId=%d lastTargetId=%d",
                        primaryTarget == null ? "null" : primaryTarget.getName(),
                        targets.size(), isHuntEnabled(), rt().huntNavigationActive,
                        rt().pendingHuntDestination != null ? "ready" : "null",
                        rt().pendingHuntSolve != null && !rt().pendingHuntSolve.isDone() && !rt().pendingHuntSolve.isCancelled() ? "running" : "idle",
                        rt().lastHuntGoalX, rt().lastHuntGoalY, rt().lastHuntGoalZ,
                        rt().lastHuntGoalUsesY, primaryTarget == null ? -1 : primaryTarget.getEntityId(), rt().lastHuntTargetEntityId);
                boolean orbitFacingActive = shouldForceOrbitFacing(player, primaryTarget);
                boolean crosshairTargetLocked = !isTeleportAttackMode()
                        && isRelockSuppressedByCrosshairTarget(player, primaryTarget);

                if (shouldApplyContinuousRotation(orbitFacingActive) && !crosshairTargetLocked) {
                    applyRotation(player, primaryTarget, orbitFacingActive);
                }

                if (autoPickupRulePriority) {
                    stopHuntPickupNavigation();
                    stopHuntNavigation();
                } else if (huntPriorityPickupItem != null) {
                    stopHuntNavigation();
                    handleHuntPickupMovement(player, huntPriorityPickupItem);
                } else if (shouldRunHuntMovement(player, primaryTarget)) {
                    stopHuntPickupNavigation();
                    handleHuntMovement(player, primaryTarget);
                } else {
                    hunt$dbg("hunt-skip player=%s target=%s enabled=%s huntEnabled=%s",
                            player == null ? "null" : player.getName(),
                            primaryTarget == null ? "null" : primaryTarget.getName(),
                            rt().enabled, isHuntEnabled());
                    stopHuntPickupNavigation();
                    stopHuntNavigation();
                }

                if (sequenceAttackMode) {
                    this.attackSequenceExecutor.tick(player);
                    // Read before triggerAttackSequence — post-attack
                    // hurtResistantTime is always max (set by the hit itself).
                    boolean seqAttackInsideIframe = primaryTarget != null
                            && primaryTarget.hurtResistantTime
                                    > primaryTarget.maxHurtResistantTime / 2.0F;
                    if (canTriggerAttackSequence(player, primaryTarget)
                            && prepareRotationForAttack(player, primaryTarget, crosshairTargetLocked ? primaryTarget : null)
                            && triggerAttackSequence(player, primaryTarget)) {
                        if (!seqAttackInsideIframe) {
                            recordNoDamageAttackAttempt(primaryTarget);
                        }
                        rt().sequenceCooldownTicks = sampleAttackSequenceDelayTicks();
                        rt().sequenceCooldownUntilMillis = System.currentTimeMillis()
                                + rt().sequenceCooldownTicks * 50L;
                    }
                    decayTargetSwitchSmoothTicks();
                    return;
                }

                if (rt().aimOnlyMode) {
                    decayTargetSwitchSmoothTicks();
                    return;
                }

                boolean canStartCurrentAttack = canStartAttack(player);
                if (teleportAttackMode && !canStartCurrentAttack) {
                    teleportDebug(player, "ATTACK_STOP", "reason=%s,primary={%s}",
                            getTeleportAttackGateReason(player), formatTeleportTarget(player, primaryTarget));
                }
                if (java.lang.Math.random() < 0.02) {
                    hunt$dbg("pre-attack canStart=%s ctrl=%s targets=%d dist=%.2f",
                            canStartCurrentAttack, ctxController() == null ? "null" : "ok",
                            targets.size(),
                            primaryTarget == null ? -1 : player.getDistance(primaryTarget));
                }
                if (canStartCurrentAttack && ctxController() != null) {
                    int attackedCount = attackTargets(mc, player, targets, crosshairTargetLocked ? primaryTarget : null);
                    if (java.lang.Math.random() < 0.05) {
                        hunt$dbg("attack-result targets=%d attacked=%d dist=%.2f",
                                targets.size(), attackedCount,
                                primaryTarget == null ? -1 : player.getDistance(primaryTarget));
                    }
                    if (attackedCount > 0) {
                        if (!isMouseClickAttackMode() && !isTeleportAttackMode()) {
                            player.swingArm(EnumHand.MAIN_HAND);
                        }
                        rt().attackCooldownTicks = isMouseClickAttackMode()
                                ? sampleAttackSequenceDelayTicks()
                                : cfg().minAttackIntervalTicks;
                        rt().attackCooldownUntilMillis = System.currentTimeMillis()
                                + rt().attackCooldownTicks * 50L;
                        if (!isHuntOrbitEnabled()) {
                            stopHuntNavigation();
                        }
                    } else if (teleportAttackMode) {
                        teleportDebug(player, "ATTACK_STOP",
                                "reason=ATTACK_PIPELINE_RETURNED_ZERO,primary={%s},targets=%s",
                                formatTeleportTarget(player, primaryTarget), formatTeleportTargetIds(targets, 24));
                    }
                }
                decayTargetSwitchSmoothTicks();
            } finally {
                if (event.phase == TickEvent.Phase.END) {
                    updateAttackBlocking();
                    syncBlockingServerRotation();
                }
                flushTeleportDebug();
                timer.stop();
            }
        }
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (event == null) {
            return;
        }
        if (event == PathEvent.CALC_FINISHED_NOW_EXECUTING) {
            clearCurrentHuntUnreachableFailureCount();
            return;
        }
        if (event != PathEvent.CALC_FAILED) {
            return;
        }
        handleHuntPathCalculationFailed();
    }

    public void onPlayerPositionCorrectionHandled(SPacketPlayerPosLook packet) {
        RuntimeState rt = rt();
        synchronized (rt.teleport) {
            if (packet == null) return;
            EntityPlayerSP player = ctxPlayer();
            TeleportAttackFeedback sent = rt.teleport.lastAttack;
            TeleportViewReturn viewReturn = rt.teleportViewReturn;
            if (viewReturn != null && !viewReturn.avatar) {
                // LOCK 回程被服务器拉回时，原版已经把本体挪到服务器位置，虚拟位置失去意义。
                // AVATAR 回程不用停：本体就是服务器位置，下一步从被拉回的地方接着走。
                rt.teleportViewReturn = null;
                rt.teleport.virtualPosition = null;
            }
            boolean hadRoute = rt.activeTeleportTourExecution != null
                    || rt.activeTeleportAttackPlan != null || rt.pendingTeleportReturnPlan != null;
            boolean recentlySent = sent != null && sent.player == player
                    && System.nanoTime() - sent.sentNanos < 2_000_000_000L;
            if (!hadRoute && !recentlySent) return;

            // Vanilla already applied and acknowledged the authoritative position.
            // Never counter a server correction by replaying a client return route.
            Vec3d virtual = rt.teleport.virtualPosition;
            double awayFromBody = virtual == null || player == null
                    ? 0.0D : virtual.distanceTo(player.getPositionVector());
            // Do NOT suspend the aura. The body has already been moved to the
            // authoritative position, and that is now the centre of the kill radius.
            // Dropping the virtual position is the only reset needed; the next wave
            // plans from wherever the server put us.
            rt.teleport.virtualPosition = null;
            rt.teleport.lastAttack = null;
            rt.teleport.recentAttacks.clear();
            clearTeleportAttackState(player, "SERVER_POSITION_ACCEPTED_ROUTE_CANCELLED");
            teleportDebug(player, "TP_ROUTE_RESET",
                    "reason=SERVER_POSITION_CORRECTION,awayFromBody=%.2f,virtual=(%.2f,%.2f,%.2f)",
                    awayFromBody,
                    virtual == null ? 0.0D : virtual.x,
                    virtual == null ? 0.0D : virtual.y,
                    virtual == null ? 0.0D : virtual.z);
        }
    }

    private TeleportAttackPlan findTeleportCorrectionPlan(TeleportAttackPlan recoveryPlan, double x, double y,
            double z) {
        if (recoveryPlan == null) {
            return null;
        }
        Vec3d corrected = new Vec3d(x, y, z);
        TeleportAttackPlan bestPlan = null;
        double bestDistanceSq = 2.25D;
        for (int i = recoveryPlan.relatedRoutes.size() - 1; i >= 0; i--) {
            TeleportRoute route = recoveryPlan.relatedRoutes.get(i);
            if (route == null || route.owner == null || route.points.size() < 2) {
                continue;
            }
            double distanceSq = getDistanceSqToTeleportRoute(corrected, route.points);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestPlan = route.owner;
            }
        }
        return bestPlan;
    }

    private double getDistanceSqToTeleportRoute(Vec3d point, List<Vec3d> route) {
        if (point == null || route == null || route.size() < 2) {
            return Double.MAX_VALUE;
        }
        double bestDistanceSq = Double.MAX_VALUE;
        Vec3d previous = route.get(0);
        for (int i = 1; i < route.size(); i++) {
            Vec3d current = route.get(i);
            if (previous != null && current != null) {
                bestDistanceSq = Math.min(bestDistanceSq, getDistanceSqToSegment(point, previous, current));
            }
            if (current != null) {
                previous = current;
            }
        }
        return bestDistanceSq;
    }

    private int getTeleportCorrectionWindowTicks(EntityPlayerSP player) {
        int responseTimeMs = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (player != null && mc != null && mc.getConnection() != null) {
            try {
                NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(player.getUniqueID());
                if (info != null) {
                    responseTimeMs = Math.max(0, info.getResponseTime());
                }
            } catch (Exception ignored) {
            }
        }
        int latencyWindow = MathHelper.ceil((responseTimeMs * 2.0D + 250.0D) / 50.0D);
        return MathHelper.clamp(Math.max(TELEPORT_ATTACK_CORRECTION_WINDOW_TICKS, latencyWindow),
                TELEPORT_ATTACK_CORRECTION_WINDOW_TICKS, 80);
    }

    private double getDistanceSqToSegment(Vec3d point, Vec3d start, Vec3d end) {
        if (point == null || start == null || end == null) {
            return Double.MAX_VALUE;
        }
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq <= 1.0E-8D) {
            return point.squareDistanceTo(start);
        }
        double progress = ((point.x - start.x) * dx + (point.y - start.y) * dy + (point.z - start.z) * dz)
                / lengthSq;
        progress = MathHelper.clamp(progress, 0.0D, 1.0D);
        Vec3d closest = new Vec3d(start.x + dx * progress, start.y + dy * progress, start.z + dz * progress);
        return point.squareDistanceTo(closest);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        RuntimeState rt = rt();
        if (!PerformanceMonitor.isFeatureEnabled("kill_aura")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("kill_aura");
        try {
            if (!rt().enabled && rt().areaHuntControlTicks <= 0) {
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = ctxPlayer();
            Entity viewer = mc.getRenderViewEntity();
            if (player == null || viewer == null) {
                return;
            }

            float partialTicks = event.getPartialTicks();
            double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
            double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
            double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

            double worldCenterX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
            double worldCenterY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + 0.05D;
            double worldCenterZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

            if (isHuntEnabled() && rt().visualizeHuntRadius) {
                drawHuntRadiusAura(worldCenterX, worldCenterY, worldCenterZ, viewerX, viewerY, viewerZ, cfg().huntRadius);
            }
            if (rt().enabled && cfg().throughWallAttack) {
                renderThroughWallAttackBlocks(player, viewerX, viewerY, viewerZ);
            }
            renderTeleportAttackFeedback(player, viewerX, viewerY, viewerZ);
            renderHuntOrbitLoop();
        } finally {
            timer.stop();
        }
    }

    private EntityLivingBase teleportFeedbackTarget(EntityPlayerSP player, TeleportAttackFeedback feedback) {
        Entity entity = player.world.getEntityByID(feedback.entityId);
        return entity instanceof EntityLivingBase && feedback.targetUuid.equals(entity.getUniqueID())
                ? (EntityLivingBase) entity : null;
    }

    @SubscribeEvent
    public void onTeleportAttackOverlay(RenderGameOverlayEvent.Text event) {
        if (com.mythos.mythosScriptMod.mcp.NexusSessions.boundSession() != null) return;
        EntityPlayerSP player = ctxPlayer();
        if (player == null || !rt().enabled || !isTeleportAttackMode()) return;
        // 只在路由被服务器判非法时提示；逐次攻击的成功反馈由目标描边盒子表达，不再刷文本行
        if (rt().teleport.timing.isRouteRejected()) {
            event.getLeft().add(I18n.format("hud.kill_aura.tp.suspended"));
        }
    }

    private void renderTeleportAttackFeedback(EntityPlayerSP player, double viewerX, double viewerY, double viewerZ) {
        if (!isTeleportAttackMode()) return;
        Map<Integer, TeleportAttackFeedback> recent = rt().teleport.recentAttacks;
        if (recent.isEmpty()) return;
        // 群攻一拍会打好几只，只画最后一只会让人以为「单次攻击目标数」没生效；
        // 每只最近被打过的目标各画一个框，过期的顺手清掉，map 不会越积越多。
        long now = System.nanoTime();
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (Iterator<TeleportAttackFeedback> it = recent.values().iterator(); it.hasNext(); ) {
            TeleportAttackFeedback feedback = it.next();
            if (feedback.player != player || now - feedback.sentNanos >= TELEPORT_ATTACK_FEEDBACK_NANOS) {
                it.remove();
                continue;
            }
            EntityLivingBase target = teleportFeedbackTarget(player, feedback);
            if (target != null) {
                boxes.add(target.getEntityBoundingBox().offset(-viewerX, -viewerY, -viewerZ).grow(0.05D));
            }
        }
        if (boxes.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2F);
        try {
            for (AxisAlignedBB box : boxes) {
                RenderGlobal.drawSelectionBoundingBox(box, 1F, 0.6F, 0.1F, 0.9F);
            }
        } finally {
            GlStateManager.glLineWidth(1F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private void renderThroughWallAttackBlocks(EntityPlayerSP player, double viewerX, double viewerY, double viewerZ) {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || ctxWorld() == null || player == null || rt().currentTargetEntityId == -1) {
            return;
        }
        Entity entity = ctxWorld().getEntityByID(rt().currentTargetEntityId);
        if (!(entity instanceof EntityLivingBase) || entity.isDead) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        if (target.getHealth() <= 0.0F || !isValidTarget(player, target)) {
            return;
        }

        Vec3d from = player.getPositionEyes(1.0F);
        Vec3d to = getEntityCenter(target);
        List<BlockPos> blockingBlocks = collectBlockingBlocksBetween(ctxWorld(), from, to, 80);
        if (blockingBlocks.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        for (BlockPos pos : blockingBlocks) {
            AxisAlignedBB box = getWorldBlockOverlayBox(ctxWorld(), pos);
            if (box == null) {
                continue;
            }
            AxisAlignedBB renderBox = box.offset(-viewerX, -viewerY, -viewerZ).grow(0.002D);
            RenderGlobal.renderFilledBox(renderBox, 0.1F, 0.85F, 1.0F, 0.22F);
            RenderGlobal.drawSelectionBoundingBox(renderBox, 0.15F, 0.95F, 1.0F, 0.65F);
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private List<BlockPos> collectBlockingBlocksBetween(World world, Vec3d from, Vec3d to, int maxBlocks) {
        List<BlockPos> blocks = new ArrayList<>();
        if (world == null || from == null || to == null || maxBlocks <= 0) {
            return blocks;
        }
        double distance = from.distanceTo(to);
        int steps = MathHelper.clamp((int) Math.ceil(distance * 2.0D), 1, 128);
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (int i = 0; i <= steps && seen.size() < maxBlocks; i++) {
            double t = i / (double) steps;
            BlockPos pos = new BlockPos(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t);
            if (seen.contains(pos) || !isThroughWallOverlayBlock(world, pos)) {
                continue;
            }
            seen.add(pos);
        }
        blocks.addAll(seen);
        return blocks;
    }

    private boolean isThroughWallOverlayBlock(World world, BlockPos pos) {
        if (world == null || pos == null || world.isAirBlock(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state == null || state.getBlock().isAir(state, world, pos) || state.getMaterial().isLiquid()) {
            return false;
        }
        return state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
    }

    private AxisAlignedBB getWorldBlockOverlayBox(World world, BlockPos pos) {
        IBlockState state = world == null || pos == null ? null : world.getBlockState(pos);
        if (state == null) {
            return null;
        }
        AxisAlignedBB box = state.getCollisionBoundingBox(world, pos);
        if (box == null || box == Block.NULL_AABB) {
            return null;
        }
        return box.offset(pos);
    }

    private Vec3d getEntityCenter(Entity entity) {
        AxisAlignedBB box = entity == null ? null : entity.getEntityBoundingBox();
        if (box == null) {
            return entity == null ? Vec3d.ZERO : entity.getPositionVector().addVector(0.0D, entity.height * 0.5D, 0.0D);
        }
        return new Vec3d((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D);
    }

    private void drawHuntRadiusAura(double worldCenterX, double worldCenterY, double worldCenterZ, double viewerX,
            double viewerY, double viewerZ, double radius) {
        double safeRadius = Math.max(0.5D, radius);
        int segments = MathHelper.clamp((int) Math.round(safeRadius * 4.0D), 36, 256);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(worldCenterX - viewerX, worldCenterY - viewerY, worldCenterZ - viewerZ)
                .color(0.15F, 0.75F, 1.0F, 0.10F).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double[] point = getClippedHuntPoint(worldCenterX, worldCenterZ, safeRadius, angle);
            buffer.pos(point[0] - viewerX, worldCenterY - viewerY, point[1] - viewerZ).color(0.15F, 0.75F, 1.0F, 0.02F)
                    .endVertex();
        }
        tessellator.draw();

        GlStateManager.glLineWidth(4.0F);
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double[] point = getClippedHuntPoint(worldCenterX, worldCenterZ, safeRadius, angle);
            buffer.pos(point[0] - viewerX, worldCenterY - viewerY, point[1] - viewerZ).color(1.0F, 1.0F, 0.0F, 1.0F)
                    .endVertex();
        }
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private double[] getClippedHuntPoint(double centerX, double centerZ, double radius, double angle) {
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        double endX = centerX + dirX * radius;
        double endZ = centerZ + dirZ * radius;

        if (!AutoFollowHandler.hasActiveLockChaseRestriction()
                || AutoFollowHandler.isPositionWithinActiveLockChaseBounds(endX, endZ)) {
            return new double[] { endX, endZ };
        }

        double low = 0.0D;
        double high = radius;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) * 0.5D;
            double testX = centerX + dirX * mid;
            double testZ = centerZ + dirZ * mid;
            if (AutoFollowHandler.isPositionWithinActiveLockChaseBounds(testX, testZ)) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return new double[] { centerX + dirX * low, centerZ + dirZ * low };
    }

    private List<EntityLivingBase> findTargets(EntityPlayerSP player) {
        return findTargets(player, null);
    }

    public List<HuntScoreDebugEntry> getHuntScoreDebugEntries() {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : ctxPlayer();
        if (player == null || player.world == null) {
            return new ArrayList<>();
        }
        int nowTick = player.ticksExisted;
        if (rt().lastHuntScoreDebugTick != Integer.MIN_VALUE
                && nowTick - rt().lastHuntScoreDebugTick < 10) {
            return new ArrayList<>(rt.huntScoreDebugEntries);
        }

        rt().lastHuntScoreDebugTick = nowTick;
        rt.huntScoreDebugEntries.clear();
        double searchRadius = getTargetSearchRadius();
        double searchRadiusSq = searchRadius * searchRadius;
        double aabbGrow = Math.min(searchRadius, 128.0D);
        boolean useWhitelistPriority = cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        double preferredRadius = isHuntFixedDistanceMode()
                ? Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance())
                : Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, cfg().attackRange - HUNT_APPROACH_TARGET_BUFFER * 2.0D);

        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(aabbGrow))) {
            if (!(entity instanceof EntityLivingBase) || rt.huntScoreDebugEntries.size() >= 24) {
                continue;
            }
            EntityLivingBase target = (EntityLivingBase) entity;
            if (buildTargetCandidate(player, target, searchRadiusSq, useWhitelistPriority,
                    target.getEntityId() == rt().currentTargetEntityId,
                    shouldAllowHuntTrackingWithoutLineOfSight(), null) == null) {
                continue;
            }
            // The detailed route solver evaluates many angle/layer pairs and is
            // reserved for the active target. The panel needs comparable score
            // components, not a full path search for every visible mob.
            double[] destination = findHuntScoreDebugDestination(player, target, preferredRadius);
            HuntScoreBreakdown breakdown = destination == null
                    ? new HuntScoreBreakdown(Double.POSITIVE_INFINITY, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false)
                    : buildHuntScoreBreakdown(player, target, destination, preferredRadius,
                            hasHuntLineOfSightFromStandPos(new BlockPos(destination[0], destination[1], destination[2]),
                                    target));
            rt.huntScoreDebugEntries.add(new HuntScoreDebugEntry(target, breakdown, destination));
        }
        rt.huntScoreDebugEntries.sort((left, right) -> Double.compare(left.totalScore, right.totalScore));
        return new ArrayList<>(rt.huntScoreDebugEntries);
    }

    private List<EntityLivingBase> findTargets(EntityPlayerSP player, AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        if (isTeleportAttackMode()) {
            return findTeleportTargetsBounded(player, areaOptions);
        }
        List<EntityLivingBase> targets = new ArrayList<>();
        EntityLivingBase lockedTarget = null;
        double targetSearchRadius = areaOptions == null ? getTargetSearchRadius() : Double.MAX_VALUE;
        double targetSearchRadiusSq = targetSearchRadius * targetSearchRadius;
        boolean useWhitelistPriority = areaOptions == null
                && cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        boolean preferStableOrbitTarget = isHuntOrbitEnabled();
        int previousTargetEntityId = rt().currentTargetEntityId;

        if (cfg().focusSingleTarget && rt().currentTargetEntityId != -1) {
            Entity existing = player.world.getEntityByID(rt().currentTargetEntityId);
            if (existing instanceof EntityLivingBase
                    && isTrackableTarget(player, (EntityLivingBase) existing, targetSearchRadiusSq,
                            useWhitelistPriority,
                            areaOptions)) {
                lockedTarget = (EntityLivingBase) existing;
                targets.add(lockedTarget);
            }
        }

        List<TargetCandidate> nearbyTargets = new ArrayList<>();

        // Narrow the world scan to the search box instead of walking every
        // loaded entity — 15+ bots each iterating hundreds of entities per tick
        // was the dominant tick cost.
        // Double.MAX_VALUE (area mode) can't feed AxisAlignedBB — fall back to a
        // large but finite grow box so the chunk lookup still narrows the list.
        double aabbGrow = Math.min(targetSearchRadius, 128.0D);
        List<Entity> nearbyEntities = player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(aabbGrow));
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase candidate = (EntityLivingBase) entity;
            if (candidate == lockedTarget) {
                continue;
            }
            // Reject distant entities before name matching and advanced aim
            // prediction. Large mob groups otherwise pay those costs for every
            // loaded entity even though they cannot become a target.
            if (getTargetSearchDistanceSq(player, candidate) > targetSearchRadiusSq) {
                continue;
            }
            TargetCandidate targetCandidate = buildTargetCandidate(player, candidate, targetSearchRadiusSq,
                    useWhitelistPriority, candidate.getEntityId() == previousTargetEntityId,
                    shouldAllowHuntTrackingWithoutLineOfSight(), areaOptions);
            if (targetCandidate != null) {
                nearbyTargets.add(targetCandidate);
            }
        }

        nearbyTargets.sort((left, right) -> {
            int whitelistPriorityCompare = Integer.compare(left.whitelistPriority, right.whitelistPriority);
            if (whitelistPriorityCompare != 0) {
                return whitelistPriorityCompare;
            }
            if (preferStableOrbitTarget) {
                int continuityCompare = Integer.compare(left.currentTargetPriority, right.currentTargetPriority);
                if (continuityCompare != 0) {
                    return continuityCompare;
                }
                int yawCompare = Float.compare(left.yawDeltaAbs, right.yawDeltaAbs);
                if (yawCompare != 0) {
                    return yawCompare;
                }
            }
            int stickyDistanceCompare = compareCurrentTargetWithinDistanceHysteresis(left, right);
            if (stickyDistanceCompare != 0) {
                return stickyDistanceCompare;
            }
            int distanceCompare = Double.compare(left.distanceSq, right.distanceSq);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            return Integer.compare(left.entity.getEntityId(), right.entity.getEntityId());
        });

        for (TargetCandidate nearbyTarget : nearbyTargets) {
            targets.add(nearbyTarget.entity);
        }
        if (lockedTarget == null) {
            promoteCrosshairLockedTarget(player, targets, areaOptions);
            if (areaOptions != null) {
                areaOptions.prioritizeTargets(player, targets);
            }
        }
        int nextTargetEntityId = targets.isEmpty() ? -1 : targets.get(0).getEntityId();
        if (nextTargetEntityId != rt().currentTargetEntityId) {
            clearVisualRotationCache();
        }
        rt().currentTargetEntityId = nextTargetEntityId;
        return targets;
    }

    private List<EntityLivingBase> findTeleportTargetsBounded(EntityPlayerSP player, AreaHuntOptions areaOptions) {
        synchronized (rt().teleport) {
            return findTeleportTargetsBoundedLocked(player, areaOptions);
        }
    }

    private List<EntityLivingBase> findTeleportTargetsBoundedLocked(EntityPlayerSP player,
            AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        List<EntityLivingBase> targets = new ArrayList<>();
        if (player == null || player.world == null) {
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=PLAYER_OR_WORLD_NULL");
            return targets;
        }
        int currentTick = rt().teleport.currentTick();
        if (!rt().aimOnlyMode && currentTick < rt().teleportPlanningRetryAfterTick) {
            rt().currentTargetEntityId = -1;
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=PLANNING_BACKOFF,retryAfter=%d,remainingTicks=%d",
                    rt().teleportPlanningRetryAfterTick, rt().teleportPlanningRetryAfterTick - currentTick);
            return targets;
        }

        // Chunk-narrowed list: same coverage as loadedEntityList for in-range
        // targets but far fewer entries to bucket-walk per tick.
        double searchRadius = cfg().attackRange;
        List<Entity> loadedEntities = player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(searchRadius));
        int loadedCount = loadedEntities == null ? 0 : loadedEntities.size();
        if (loadedCount <= 0) {
            rt().teleportTargetScanCursor = 0;
            rt().teleport.teleportTargetCandidateEntityIds.clear();
            rt().currentTargetEntityId = -1;
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=NO_LOADED_ENTITIES");
            return targets;
        }

        double searchRadiusSq = searchRadius * searchRadius;
        boolean useWhitelistPriority = areaOptions == null
                && cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        int scanCount = Math.min(TELEPORT_TARGET_SCAN_PER_TICK, loadedCount);
        int cursor = Math.floorMod(rt().teleportTargetScanCursor, loadedCount);
        teleportDebug(player, "TARGET_SCAN_BEGIN",
                "loaded=%d,scanCount=%d,cursor=%d,cacheBefore=%d,range=%.3f,areaMode=%s",
                loadedCount, scanCount, cursor, rt().teleport.teleportTargetCandidateEntityIds.size(), searchRadius,
                areaOptions != null);
        trimTeleportRuntimeMaps();
        for (int scanned = 0; scanned < scanCount; scanned++) {
            Entity entity = loadedEntities.get((cursor + scanned) % loadedCount);
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) entity;
            if (isTeleportTargetBackedOff(candidate) && !teleportBackoffTargetStale(candidate)) {
                rt().teleport.teleportTargetCandidateEntityIds.remove(candidate.getEntityId());
                teleportDebug(player, "TARGET_REJECT",
                        "phase=SCAN,reason=TARGET_RETRY_BACKOFF,retryAfter=%d,target={%s}",
                        rt().teleport.teleportTargetRetryAfterTicks.getOrDefault(candidate.getEntityId(), Integer.MIN_VALUE),
                        formatTeleportTarget(player, candidate));
                continue;
            }
            TargetCandidate targetCandidate = buildTargetCandidate(player, candidate, searchRadiusSq,
                    useWhitelistPriority, candidate.getEntityId() == rt().currentTargetEntityId, true, areaOptions);
            if (targetCandidate == null) {
                rt().teleport.teleportTargetCandidateEntityIds.remove(candidate.getEntityId());
                teleportDebug(player, "TARGET_REJECT", "phase=SCAN,reason=%s,target={%s}",
                        getTeleportTargetRejectionReason(player, candidate, searchRadiusSq,
                                useWhitelistPriority, areaOptions),
                        formatTeleportTarget(player, candidate));
            } else {
                addTeleportTargetCandidate(candidate.getEntityId());
                teleportDebug(player, "TARGET_ACCEPT", "phase=SCAN,target={%s}",
                        formatTeleportTarget(player, candidate));
            }
        }
        rt().teleportTargetScanCursor = (cursor + scanCount) % loadedCount;

        if (rt().currentTargetEntityId != -1) {
            addTeleportTargetCandidate(rt().currentTargetEntityId);
        }

        List<TargetCandidate> candidates = new ArrayList<>();
        Iterator<Integer> iterator = rt().teleport.teleportTargetCandidateEntityIds.iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.next();
            if (currentTick < rt().teleport.teleportTargetRetryAfterTicks.getOrDefault(entityId, Integer.MIN_VALUE)) {
                iterator.remove();
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,id=%d,reason=TARGET_RETRY_BACKOFF",
                        entityId);
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                iterator.remove();
                rt().teleport.teleportLastAttackTicks.remove(entityId);
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,id=%d,reason=ENTITY_NOT_LIVING_OR_UNLOADED",
                        entityId);
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) entity;
            TargetCandidate targetCandidate = buildTargetCandidate(player, candidate, searchRadiusSq,
                    useWhitelistPriority, entityId == rt().currentTargetEntityId, true, areaOptions);
            if (targetCandidate == null) {
                iterator.remove();
                rt().teleport.teleportLastAttackTicks.remove(entityId);
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,reason=%s,target={%s}",
                        getTeleportTargetRejectionReason(player, candidate, searchRadiusSq,
                                useWhitelistPriority, areaOptions),
                        formatTeleportTarget(player, candidate));
                continue;
            }
            if (!rt().aimOnlyMode && !isTeleportTargetReady(candidate)) {
                teleportDebug(player, "TARGET_DEFERRED",
                        "reason=HURT_WINDOW,hurt=%d,maxHurt=%d,sentWaitMs=%d,observedWaitMs=%d,target={%s}",
                        candidate.hurtResistantTime, candidate.maxHurtResistantTime,
                        rt().teleport.timing.sentWaitMillis(candidate.getEntityId(), System.nanoTime()),
                        rt().teleport.timing.observedWaitMillis(candidate.getEntityId(), System.nanoTime()),
                        formatTeleportTarget(player, candidate));
                continue;
            }
            candidates.add(targetCandidate);
        }

        candidates.sort((left, right) -> {
            int leftAttackTick = rt().teleport.teleportLastAttackTicks.getOrDefault(left.entity.getEntityId(),
                    Integer.MIN_VALUE);
            int rightAttackTick = rt().teleport.teleportLastAttackTicks.getOrDefault(right.entity.getEntityId(),
                    Integer.MIN_VALUE);
            int attackAgeCompare = Integer.compare(leftAttackTick, rightAttackTick);
            if (attackAgeCompare != 0) {
                return attackAgeCompare;
            }
            int whitelistCompare = Integer.compare(left.whitelistPriority, right.whitelistPriority);
            if (whitelistCompare != 0) {
                return whitelistCompare;
            }
            int distanceCompare = Double.compare(left.distanceSq, right.distanceSq);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            return Integer.compare(left.entity.getEntityId(), right.entity.getEntityId());
        });

        int limit = Math.min(TELEPORT_TARGET_PLAN_LIMIT, candidates.size());
        for (int i = 0; i < limit; i++) {
            targets.add(candidates.get(i).entity);
        }
        int nextTargetEntityId = targets.isEmpty() ? -1 : targets.get(0).getEntityId();
        if (nextTargetEntityId != rt().currentTargetEntityId) {
            clearVisualRotationCache();
        }
        rt().currentTargetEntityId = nextTargetEntityId;
        teleportDebug(player, "TARGET_SCAN_RESULT",
                "cacheAfter=%d,validCandidates=%d,returned=%d,returnedIds=%s,currentTarget=%d",
                rt().teleport.teleportTargetCandidateEntityIds.size(), candidates.size(), targets.size(),
                formatTeleportTargetIds(targets, 24), rt().currentTargetEntityId);
        for (int i = 0; i < targets.size(); i++) {
            EntityLivingBase target = targets.get(i);
            teleportDebug(player, "TARGET_PRIORITY", "index=%d,target={%s}", i,
                    formatTeleportTarget(player, target));
        }
        return targets;
    }

    private void addTeleportTargetCandidate(int entityId) {
        synchronized (rt().teleport) {
            rt().teleport.teleportTargetCandidateEntityIds.add(entityId);
            while (rt().teleport.teleportTargetCandidateEntityIds.size() > TELEPORT_TARGET_CACHE_LIMIT) {
                Iterator<Integer> iterator = rt().teleport.teleportTargetCandidateEntityIds.iterator();
                if (!iterator.hasNext()) {
                    return;
                }
                int evictedEntityId = iterator.next();
                iterator.remove();
                Minecraft mc = Minecraft.getMinecraft();
                EntityPlayerSP player = mc == null ? null : ctxPlayer();
                teleportDebug(player, "TARGET_CACHE_EVICT",
                        "evictedId=%d,admittedId=%d,cacheLimit=%d,evictedLastAttackTick=%d",
                        evictedEntityId, entityId, TELEPORT_TARGET_CACHE_LIMIT,
                        rt().teleport.teleportLastAttackTicks.getOrDefault(evictedEntityId, Integer.MIN_VALUE));
            }
        }
    }

    private EntityEnderCrystal findBestEnderCrystalTarget(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (!cfg().targetEnderCrystal || player == null || player.world == null) {
            return null;
        }
        EntityEnderCrystal best = null;
        int bestWhitelistPriority = Integer.MAX_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;
        List<Entity> loadedEntities = player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(Math.min(cfg().attackRange, 128.0D)));
        int loadedCount = loadedEntities.size();
        int scanCount = isTeleportAttackMode() ? Math.min(16, loadedCount) : loadedCount;
        int cursor = loadedCount <= 0 ? 0 : Math.floorMod(rt().teleportCrystalScanCursor, loadedCount);
        for (int scanned = 0; scanned < scanCount; scanned++) {
            Entity entity = loadedEntities.get((cursor + scanned) % loadedCount);
            if (!(entity instanceof EntityEnderCrystal)) {
                continue;
            }
            EntityEnderCrystal crystal = (EntityEnderCrystal) entity;
            if (!isValidEnderCrystalTarget(player, crystal)) {
                continue;
            }
            String crystalName = getFilterableEntityName(crystal);
            int whitelistPriority = cfg().enableNameWhitelist
                    ? getNormalizedNameListMatchIndex(crystalName, cfg().nameWhitelist)
                    : 0;
            double distanceSq = player.getDistanceSq(crystal);
            if (cfg().enableNameWhitelist) {
                if (whitelistPriority < bestWhitelistPriority
                        || (whitelistPriority == bestWhitelistPriority && distanceSq < bestDistanceSq)) {
                    best = crystal;
                    bestWhitelistPriority = whitelistPriority;
                    bestDistanceSq = distanceSq;
                }
                continue;
            }
            if (distanceSq < bestDistanceSq) {
                best = crystal;
                bestDistanceSq = distanceSq;
            }
        }
        if (isTeleportAttackMode() && loadedCount > 0) {
            rt().teleportCrystalScanCursor = (cursor + scanCount) % loadedCount;
        }
        return best;
    }

    private boolean isValidEnderCrystalTarget(EntityPlayerSP player, EntityEnderCrystal crystal) {
        if (player == null || crystal == null || crystal.isDead || !crystal.isEntityAlive()) {
            return false;
        }
        if (isNoDamageExcludedTarget(crystal)) {
            return false;
        }
        if (isHuntEnabled() && !isWithinConfiguredHuntVerticalRange(player, crystal)) {
            return false;
        }
        if (getTargetSearchDistanceSq(player, crystal) > getTargetSearchRadius() * getTargetSearchRadius()) {
            return false;
        }
        if (!isHuntEnabled() && player.getDistanceSq(crystal) > cfg().attackRange * cfg().attackRange) {
            return false;
        }
        if (AutoFollowHandler.hasActiveLockChaseRestriction()
                && !AutoFollowHandler.isPositionWithinActiveLockChaseBounds(crystal.posX, crystal.posZ)) {
            return false;
        }
        if (shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(crystal)) {
            return false;
        }
        String targetName = getFilterableEntityName(crystal);
        boolean whitelistMatched = false;
        if (cfg().enableNameBlacklist && matchesNameList(targetName, cfg().nameBlacklist)) {
            return false;
        }
        if (cfg().enableNameWhitelist) {
            if (getNormalizedNameListMatchIndex(targetName, cfg().nameWhitelist) == Integer.MAX_VALUE) {
                return false;
            }
            whitelistMatched = true;
        }
        if (!whitelistMatched && !matchesEnabledTargetGroup(crystal)) {
            return false;
        }
        return true;
    }

    private boolean shouldPrioritizeEnderCrystalTarget(EntityPlayerSP player, EntityEnderCrystal crystalTarget,
            EntityLivingBase primaryLivingTarget) {
        if (player == null || crystalTarget == null) {
            return false;
        }
        if (primaryLivingTarget == null) {
            return true;
        }
        if (cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty()) {
            int crystalPriority = getNormalizedNameListMatchIndex(getFilterableEntityName(crystalTarget),
                    cfg().nameWhitelist);
            int livingPriority = getNormalizedNameListMatchIndex(getFilterableEntityName(primaryLivingTarget),
                    cfg().nameWhitelist);
            if (crystalPriority != livingPriority) {
                return crystalPriority < livingPriority;
            }
        }
        return player.getDistanceSq(crystalTarget) <= player.getDistanceSq(primaryLivingTarget);
    }

    private void handleEnderCrystalTarget(Minecraft mc, EntityPlayerSP player, EntityEnderCrystal crystalTarget) {
        RuntimeState rt = rt();
        if (mc == null || player == null || crystalTarget == null) {
            return;
        }
        if (rt().currentTargetEntityId != crystalTarget.getEntityId()) {
            clearAimTargetTransition();
            clearVisualRotationCache();
        }
        rt().currentTargetEntityId = crystalTarget.getEntityId();
        stopHuntPickupNavigation();
        if (shouldRunHuntMovementForEntity(player, crystalTarget)) {
            handleHuntMovementForEntity(player, crystalTarget);
        } else {
            stopHuntNavigation();
        }

        if (shouldRotateToTarget()) {
            applyRotationToEntity(player, crystalTarget);
        }

        if (rt().aimOnlyMode) {
            decayTargetSwitchSmoothTicks();
            return;
        }
        if (!canStartAttack(player) || ctxController() == null) {
            decayTargetSwitchSmoothTicks();
            return;
        }
        if (!canAttackEntityTarget(player, crystalTarget, shouldRequireCrosshairHitForAttack(false))) {
            decayTargetSwitchSmoothTicks();
            return;
        }

        boolean attacked = false;
        if (isMouseClickAttackMode()) {
            attacked = performMouseClickAttack(mc);
        } else if (isPacketAttackMode() && player.connection != null) {
            player.connection.sendPacket(new CPacketUseEntity(crystalTarget));
            attacked = true;
        } else {
            ctxController().attackEntity(player, crystalTarget);
            attacked = true;
        }
        if (attacked) {
            rememberBlockingAttack(player, crystalTarget);
            if (!isMouseClickAttackMode()) {
                player.swingArm(EnumHand.MAIN_HAND);
            }
            rt().attackCooldownTicks = isMouseClickAttackMode()
                    ? sampleAttackSequenceDelayTicks()
                    : cfg().minAttackIntervalTicks;
            rt().attackCooldownUntilMillis = System.currentTimeMillis()
                    + rt().attackCooldownTicks * 50L;
        }
        decayTargetSwitchSmoothTicks();
    }

    private boolean shouldRunHuntMovementForEntity(EntityPlayerSP player, Entity target) {
        if (!isHuntEnabled() || player == null || target == null) {
            return false;
        }
        double distance = player.getDistance(target);
        boolean missingAttackLineOfSight = shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target);
        if (isHuntFixedDistanceMode()) {
            boolean farBeyondRange = distance > Math.max(cfg().attackRange + 2.0D,
                    getEffectiveHuntFixedDistance() + HUNT_FIXED_DISTANCE_TOLERANCE);
            return missingAttackLineOfSight
                    || (!rt().huntNavigationActive
                            && Math.abs(distance - getEffectiveHuntFixedDistance()) > HUNT_FIXED_DISTANCE_TOLERANCE)
                    || farBeyondRange;
        }
        return missingAttackLineOfSight || distance > cfg().attackRange;
    }

    private void handleHuntMovementForEntity(EntityPlayerSP player, Entity target) {
        RuntimeState rt = rt();
        if (player == null || target == null) {
            stopHuntNavigation();
            return;
        }
        // Logical per-owner tick — session bots' ticksExisted never advance.
        int nowTick = ++rt().huntLogicalTick;
        int targetId = target.getEntityId();
        double dx = target.posX - rt().lastHuntTargetX;
        double dz = target.posZ - rt().lastHuntTargetZ;
        double movedSq = dx * dx + dz * dz;
        boolean targetChanged = targetId != rt().lastHuntTargetEntityId;
        boolean targetMoved = movedSq >= HUNT_GOTO_MOVE_THRESHOLD_SQ;
        // Small target jiggle shouldn't tear down an in-flight path: the
        // executor pauses for the recalc and the new calc often starts a
        // backtrack wait before the bot has even moved. Only a meaningful
        // shift of the CURRENT goal merits a fresh dispatch.
        if (targetMoved && !targetChanged) {
            double gdx = target.posX - rt().lastHuntGoalX;
            double gdz = target.posZ - rt().lastHuntGoalZ;
            targetMoved = (gdx * gdx + gdz * gdz) >= HUNT_GOAL_REDISPATCH_DIST_SQ;
        }
        boolean previousGoalReached = !targetChanged && !targetMoved && hasReachedLastHuntGoal(player);

        // A target switch must invalidate the old goal immediately. Otherwise a
        // dead target's last landing point can keep winning this early-return
        // branch until the player happens to move off that block.
        if (rt.huntNavigationActive && !previousGoalReached && !targetChanged && !targetMoved) {
            if (!EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()
                    && !Double.isNaN(rt().lastHuntGoalX)
                    && !Double.isNaN(rt().lastHuntGoalZ)
                    && (!rt().lastHuntGoalUsesY || !Double.isNaN(rt().lastHuntGoalY))
                    && (nowTick - rt().lastHuntGotoTick) >= HUNT_GOTO_INTERVAL_TICKS) {
                boolean dispatched = dispatchHuntNavigationGoal(target, rt().lastHuntGoalX, rt().lastHuntGoalY,
                        rt().lastHuntGoalZ, rt().lastHuntGoalUsesY, nowTick);
                if (!dispatched) {
                    // Keep a failed refresh from being attempted every game tick,
                    // while still allowing the normal retry path to recover.
                    rt().lastHuntGotoTick = nowTick;
                }
            }
            return;
        }

        boolean shouldSendGoto = !rt.huntNavigationActive
                || previousGoalReached
                || targetChanged
                || targetMoved;
        if (!shouldSendGoto) {
            return;
        }

        if (isHuntFixedDistanceMode()) {
            double distance = Math.max(0.0001D, player.getDistance(target));
            double desired = getEffectiveHuntFixedDistance();
            double ratio = Math.max(0.0D, (distance - desired) / distance);
            double goalX = target.posX + (player.posX - target.posX) * ratio;
            double goalZ = target.posZ + (player.posZ - target.posZ) * ratio;
            dispatchHuntNavigationGoal(target, goalX, target.posY, goalZ, false, nowTick);
            return;
        }

        dispatchHuntNavigationGoal(target, target.posX, target.posY, target.posZ, false, nowTick);
    }

    private int compareCurrentTargetWithinDistanceHysteresis(TargetCandidate left, TargetCandidate right) {
        if (left == null || right == null || left.currentTargetPriority == right.currentTargetPriority) {
            return 0;
        }
        TargetCandidate current = left.currentTargetPriority < right.currentTargetPriority ? left : right;
        TargetCandidate challenger = current == left ? right : left;
        if (current.distanceSq <= challenger.distanceSq + TARGET_SWITCH_DISTANCE_HYSTERESIS_SQ) {
            return current == left ? -1 : 1;
        }
        return 0;
    }

    private void promoteCrosshairLockedTarget(EntityPlayerSP player, List<EntityLivingBase> targets) {
        promoteCrosshairLockedTarget(player, targets, null);
    }

    private void promoteCrosshairLockedTarget(EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions) {
        EntityLivingBase crosshairTarget = getCrosshairLockedTarget(player, targets, areaOptions);
        if (crosshairTarget == null || targets == null || targets.isEmpty() || targets.get(0) == crosshairTarget) {
            return;
        }
        targets.remove(crosshairTarget);
        targets.add(0, crosshairTarget);
    }

    private EntityLivingBase getCrosshairLockedTarget(EntityPlayerSP player, List<EntityLivingBase> targets) {
        return getCrosshairLockedTarget(player, targets, null);
    }

    private EntityLivingBase getCrosshairLockedTarget(EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions) {
        if (!shouldUseRelockOnlyWhenNoCrosshairTarget() || player == null || targets == null || targets.isEmpty()) {
            return null;
        }

        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        double searchDistance = Math.max(0.1D, cfg().attackRange);
        Vec3d endPos = eyePos.addVector(lookVec.x * searchDistance, lookVec.y * searchDistance,
                lookVec.z * searchDistance);

        EntityLivingBase bestTarget = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (EntityLivingBase target : targets) {
            if (!canAttackTargetBeforeRotation(player, target, areaOptions)) {
                continue;
            }
            double hitDistanceSq = getCrosshairHitDistanceSq(player, target, eyePos, endPos);
            if (hitDistanceSq >= 0.0D && hitDistanceSq < bestDistanceSq) {
                bestDistanceSq = hitDistanceSq;
                bestTarget = target;
            }
        }
        return bestTarget;
    }

    private boolean isRelockSuppressedByCrosshairTarget(EntityPlayerSP player, EntityLivingBase target) {
        return isRelockSuppressedByCrosshairTarget(player, target, null);
    }

    private boolean isRelockSuppressedByCrosshairTarget(EntityPlayerSP player, EntityLivingBase target,
            AreaHuntOptions areaOptions) {
        return shouldUseRelockOnlyWhenNoCrosshairTarget()
                && target != null
                && canAttackTargetBeforeRotation(player, target, areaOptions)
                && getCrosshairHitDistanceSq(player, target) >= 0.0D;
    }

    private double getCrosshairHitDistanceSq(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null) {
            return -1.0D;
        }
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        double searchDistance = Math.max(0.1D, cfg().attackRange);
        Vec3d endPos = eyePos.addVector(lookVec.x * searchDistance, lookVec.y * searchDistance,
                lookVec.z * searchDistance);
        return getCrosshairHitDistanceSq(player, target, eyePos, endPos);
    }

    private double getCrosshairHitDistanceSq(EntityPlayerSP player, EntityLivingBase target, Vec3d eyePos,
            Vec3d endPos) {
        if (player == null || target == null || eyePos == null || endPos == null) {
            return -1.0D;
        }
        AxisAlignedBB boundingBox = target.getEntityBoundingBox();
        if (boundingBox == null) {
            return -1.0D;
        }
        double border = Math.max(0.0D, target.getCollisionBorderSize());
        AxisAlignedBB hitBox = border > 0.0D ? boundingBox.grow(border) : boundingBox;
        Vec3d hitVec;
        if (containsPoint(hitBox, eyePos)) {
            hitVec = eyePos;
        } else {
            RayTraceResult intercept = hitBox.calculateIntercept(eyePos, endPos);
            if (intercept == null || intercept.hitVec == null) {
                return -1.0D;
            }
            hitVec = intercept.hitVec;
        }
        if (shouldRequireAttackLineOfSight() && isCrosshairHitBlockedByWorld(eyePos, hitVec)) {
            return -1.0D;
        }
        return eyePos.squareDistanceTo(hitVec);
    }

    private boolean containsPoint(AxisAlignedBB box, Vec3d point) {
        return box != null
                && point != null
                && point.x >= box.minX
                && point.x <= box.maxX
                && point.y >= box.minY
                && point.y <= box.maxY
                && point.z >= box.minZ
                && point.z <= box.maxZ;
    }

    private boolean isCrosshairHitBlockedByWorld(Vec3d eyePos, Vec3d hitVec) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || ctxWorld() == null || eyePos == null || hitVec == null) {
            return false;
        }
        RayTraceResult blockRay = ctxWorld().rayTraceBlocks(eyePos, hitVec, false, true, false);
        return blockRay != null && blockRay.typeOfHit == RayTraceResult.Type.BLOCK;
    }

    private void applyRotationToEntity(EntityPlayerSP player, Entity target) {
        Rotation rotation = getDesiredAimRotationForEntity(player, target);
        if (player == null || rotation == null) {
            return;
        }
        player.rotationYaw = rotation.getYaw();
        player.rotationPitch = rotation.getPitch();
    }

    private Rotation getDesiredAimRotationForEntity(EntityPlayerSP player, Entity target) {
        if (player == null || target == null || target.getEntityBoundingBox() == null) {
            return null;
        }
        AxisAlignedBB box = target.getEntityBoundingBox();
        Vec3d eyePos = player.getPositionEyes(1.0F);
        double targetX = (box.minX + box.maxX) * 0.5D;
        double targetY = (box.minY + box.maxY) * 0.5D;
        double targetZ = (box.minZ + box.maxZ) * 0.5D;
        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new Rotation(yaw, pitch);
    }

    private boolean canAttackEntityTarget(EntityPlayerSP player, Entity target, boolean requireCrosshairHit) {
        if (player == null || target == null || target.isDead) {
            return false;
        }
        String targetName = getFilterableEntityName(target);
        boolean whitelistMatched = false;
        if (cfg().enableNameBlacklist && matchesNameList(targetName, cfg().nameBlacklist)) {
            return false;
        }
        if (cfg().enableNameWhitelist) {
            if (getNormalizedNameListMatchIndex(targetName, cfg().nameWhitelist) == Integer.MAX_VALUE) {
                return false;
            }
            whitelistMatched = true;
        }
        if (!whitelistMatched && !matchesEnabledTargetGroup(target)) {
            return false;
        }
        if (shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target)) {
            return false;
        }
        if (player.getDistanceSq(target) > cfg().attackRange * cfg().attackRange) {
            return false;
        }
        if (requireCrosshairHit && getCrosshairHitDistanceSq(player, target) < 0.0D) {
            return false;
        }
        return true;
    }

    private double getCrosshairHitDistanceSq(EntityPlayerSP player, Entity target) {
        if (player == null) {
            return -1.0D;
        }
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        double searchDistance = Math.max(0.1D, cfg().attackRange);
        Vec3d endPos = eyePos.addVector(lookVec.x * searchDistance, lookVec.y * searchDistance,
                lookVec.z * searchDistance);
        return getCrosshairHitDistanceSq(target, eyePos, endPos);
    }

    private double getCrosshairHitDistanceSq(Entity target, Vec3d eyePos, Vec3d endPos) {
        if (target == null || eyePos == null || endPos == null || target.getEntityBoundingBox() == null) {
            return -1.0D;
        }
        AxisAlignedBB boundingBox = target.getEntityBoundingBox();
        double border = Math.max(0.0D, target.getCollisionBorderSize());
        AxisAlignedBB hitBox = border > 0.0D ? boundingBox.grow(border) : boundingBox;
        Vec3d hitVec;
        if (containsPoint(hitBox, eyePos)) {
            hitVec = eyePos;
        } else {
            RayTraceResult intercept = hitBox.calculateIntercept(eyePos, endPos);
            if (intercept == null || intercept.hitVec == null) {
                return -1.0D;
            }
            hitVec = intercept.hitVec;
        }
        if (shouldRequireAttackLineOfSight() && isCrosshairHitBlockedByWorld(eyePos, hitVec)) {
            return -1.0D;
        }
        return eyePos.squareDistanceTo(hitVec);
    }

    private boolean isValidTarget(EntityPlayerSP player, EntityLivingBase target) {
        return isValidTarget(player, target, null);
    }

    private boolean isValidTarget(EntityPlayerSP player, EntityLivingBase target, AreaHuntOptions areaOptions) {
        double targetSearchRadius = getTargetSearchRadius();
        return buildTargetCandidate(player, target,
                areaOptions == null ? targetSearchRadius * targetSearchRadius : Double.MAX_VALUE,
                areaOptions == null && cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty(),
                false, false, areaOptions) != null;
    }

    private boolean isTrackableTarget(EntityPlayerSP player, EntityLivingBase target, double targetSearchRadiusSq,
            boolean useWhitelistPriority) {
        return isTrackableTarget(player, target, targetSearchRadiusSq, useWhitelistPriority, null);
    }

    private boolean isTrackableTarget(EntityPlayerSP player, EntityLivingBase target, double targetSearchRadiusSq,
            boolean useWhitelistPriority, AreaHuntOptions areaOptions) {
        return buildTargetCandidate(player, target, targetSearchRadiusSq, useWhitelistPriority, false,
                shouldAllowHuntTrackingWithoutLineOfSight(), areaOptions) != null;
    }

    private TargetCandidate buildTargetCandidate(EntityPlayerSP player, EntityLivingBase target,
            double targetSearchRadiusSq,
            boolean useWhitelistPriority, boolean isCurrentTarget, boolean ignoreLineOfSightRequirement) {
        return buildTargetCandidate(player, target, targetSearchRadiusSq, useWhitelistPriority, isCurrentTarget,
                ignoreLineOfSightRequirement, null);
    }

    private TargetCandidate buildTargetCandidate(EntityPlayerSP player, EntityLivingBase target,
            double targetSearchRadiusSq, boolean useWhitelistPriority, boolean isCurrentTarget,
            boolean ignoreLineOfSightRequirement, AreaHuntOptions areaOptions) {
        if (player == null || target == null || target == player) {
            return null;
        }
        if (target.isDead || target.getHealth() <= 0.0F) {
            return null;
        }
        if (isNoDamageExcludedTarget(target)) {
            return null;
        }
        if (!isInsideTargetArea(target)) {
            return null;
        }
        if (!isTeleportAttackMode() && isHuntUnreachableExcludedTarget(player, target)) {
            return null;
        }
        if (target instanceof EntityArmorStand) {
            return null;
        }
        if (cfg().ignoreInvisible && target.isInvisible()) {
            return null;
        }
        if (areaOptions != null && (!areaOptions.contains(target) || !areaOptions.allows(target))) {
            return null;
        }
        boolean whitelistMatched = areaOptions != null && areaOptions.shouldTreatAllowedTargetAsWhitelistMatched();
        if (areaOptions == null && isHuntEnabled() && !isTeleportAttackMode()
                && !isWithinConfiguredHuntVerticalRange(player, target)) {
            return null;
        }
        double distanceSq = getTargetSearchDistanceSq(player, target);
        if (distanceSq > targetSearchRadiusSq) {
            return null;
        }
        if (AutoFollowHandler.hasActiveLockChaseRestriction()
                && !AutoFollowHandler.isPositionWithinActiveLockChaseBounds(target.posX, target.posZ)) {
            return null;
        }
        if (!ignoreLineOfSightRequirement && shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target)) {
            return null;
        }

        String targetName = getFilterableEntityName(target);
        int whitelistPriority = whitelistMatched ? 0 : Integer.MAX_VALUE;
        if (areaOptions == null) {
            if (cfg().enableNameBlacklist && matchesNameList(targetName, cfg().nameBlacklist)) {
                return null;
            }
            if (cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty()) {
                whitelistPriority = getNormalizedNameListMatchIndex(targetName, cfg().nameWhitelist);
                if (whitelistPriority == Integer.MAX_VALUE) {
                    return null;
                }
                whitelistMatched = true;
            }
        }

        if (!whitelistMatched && !matchesEnabledTargetGroup(target)) {
            return null;
        }
        // Yaw is only an orbit tie breaker. Use a cheap horizontal bearing
        // rather than the full predictive aim pipeline for every nearby mob.
        // Compute atan2 only when the orbit tie-break is actually used — with
        // orbit off, yawDeltaAbs stays 0 and each entity skips a Math.atan2
        // call (~30 bots × every loaded entity per tick).
        float yawDeltaAbs = 0.0F;
        if (isHuntOrbitEnabled()) {
            double yawToTarget = Math.toDegrees(Math.atan2(target.posZ - player.posZ, target.posX - player.posX)) - 90.0D;
            yawDeltaAbs = Math.abs(MathHelper.wrapDegrees((float) yawToTarget - player.rotationYaw));
        }
        return new TargetCandidate(target, distanceSq, useWhitelistPriority ? whitelistPriority : 0,
                isCurrentTarget ? 0 : 1, yawDeltaAbs);
    }

    private static boolean isWithinConfiguredHuntVerticalRange(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            return false;
        }
        double dy = target.posY - player.posY;
        return dy <= cfg().huntUpRange + 1.0E-6D && -dy <= cfg().huntDownRange + 1.0E-6D;
    }

    private double getTargetSearchDistanceSq(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            return Double.MAX_VALUE;
        }
        if (isTeleportAttackMode()) {
            if (TELEPORT_VIEW_AVATAR.equals(cfg().teleportViewMode)) {
                // The operator judges reach by what they see, and what they see is the
                // avatar. Fall through to the body if the avatar is not up yet.
                Vec3d companion = com.mythos.mythosScriptMod.handlers.TeleportCompanionHandler.position();
                if (companion != null) {
                    double dx = companion.x - target.posX;
                    double dy = companion.y + 1.5D - (target.posY + target.height * 0.5D);
                    double dz = companion.z - target.posZ;
                    return dx * dx + dy * dy + dz * dz;
                }
            }
            // With an anchor configured, attackRange means "reachable from the
            // dummy", not from wherever the bot happens to stand.
            Vec3d anchor = resolveTeleportAnchorPosition(player);
            if (anchor != null) {
                double dx = anchor.x - target.posX;
                double dy = anchor.y + 1.5D - (target.posY + target.height * 0.5D);
                double dz = anchor.z - target.posZ;
                return dx * dx + dy * dy + dz * dz;
            }
        }
        if (isHuntEnabled() && !isTeleportAttackMode()) {
            double dx = player.posX - target.posX;
            double dz = player.posZ - target.posZ;
            return dx * dx + dz * dz;
        }
        return player.getDistanceSq(target);
    }

    private boolean shouldAllowHuntTrackingWithoutLineOfSight() {
        return isHuntEnabled();
    }

    /** Re-assert this owner's static-field overrides after a MythosStateSwap
     *  may have restored the group stash. Called once per aura tick. */
    private static void reapplyStaticOverrides() {
        RuntimeState rt = rt();
        if (rt.staticOverrides.isEmpty()) {
            return;
        }
        try {
            for (java.util.Map.Entry<java.lang.reflect.Field, Object> e : rt.staticOverrides.entrySet()) {
                e.getKey().set(cfg(), e.getValue());
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private float getAttackCooledStrength(EntityPlayerSP player) {
        float vanilla = player.getCooledAttackStrength(0.0F);
        if (!isTeleportAttackMode()) {
            return vanilla;
        }
        KillAuraTeleportState teleport = rt().teleport;
        return teleport.timing.cooledStrength(vanilla, player.getCooldownPeriod(), System.nanoTime(),
                ItemStack.areItemStacksEqual(teleport.lastAttackWeapon, player.getHeldItemMainhand()));
    }

    /** 无敌帧门只在我们自己记的 sentUntil 里有内容：客户端别的实体永远读不到服务端
     *  的 hurtResistantTime（1.12.2 不 sync 这个字段，handleStatusUpdate 只写 hurtTime），
     *  所以它等价于「每目标固定 500ms 一次」这个写死的限流。minAttackIntervalTicks 和
     *  minAttackStrength 是操作者表达攻速的唯一入口，两个都关掉还要被这个常量压着，
     *  就等于「关掉间隔」这个设置没生效。 */
    private boolean isTeleportTargetReady(EntityLivingBase target) {
        if (target == null || target.isDead || target.getHealth() <= 0F) return false;
        if (!teleportReadyGateActive()) return true;
        return rt().teleport.timing.targetReady(target.getEntityId(), target.hurtResistantTime,
                target.maxHurtResistantTime, System.nanoTime());
    }

    /** 要蓄力（minAttackStrength > 0）才按原版手感放慢；纯间隔模式下间隔本身就是节流。 */
    private boolean teleportReadyGateActive() {
        return cfg().minAttackStrength > 0F;
    }

    private boolean canStartAttack(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null) {
            return false;
        }
        if (java.lang.Math.random() < 0.03) {
            hunt$dbg("canStart yield=%s aim=%s seq=%s cd=%d tele=%s recov=%s weap=%s cooled=%.2f min=%.2f swing=%s",
                    shouldYieldAttackToItemUse(player), rt().aimOnlyMode, isSequenceAttackMode(),
                    rt().attackCooldownTicks, isTeleportAttackMode(),
                    isTeleportAttackMode() && isTeleportAttackRecoveryActive(),
                    cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0,
                    player.getCooledAttackStrength(0.0F), cfg().minAttackStrength, player.isSwingInProgress);
        }
        if (shouldYieldAttackToItemUse(player)) return false;
        if (rt().aimOnlyMode) {
            return false;
        }
        if (isSequenceAttackMode()) {
            return false;
        }
        if (rt().attackCooldownTicks > 0) {
            return false;
        }
        if (isTeleportAttackMode() && isTeleportAttackRecoveryActive()) {
            return false;
        }
        if (isTeleportAttackMode()
                && rt().teleport.timing.isRouteRejected()) {
            return false;
        }
        if (cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
            return false;
        }
        float cooled = getAttackCooledStrength(player);
        if (cooled < cfg().minAttackStrength && java.lang.Math.random() < 0.05) {
            mythosScriptMod.LOGGER.info("[ka] gate=cooled_strength cooled={} min={} swingInProgress={}",
                    cooled, cfg().minAttackStrength, player.isSwingInProgress);
        }
        return cooled >= cfg().minAttackStrength;
    }

    private int attackTargets(Minecraft mc, EntityPlayerSP player, List<EntityLivingBase> targets,
            EntityLivingBase crosshairLockedTarget) {
        return attackTargets(mc, player, targets, crosshairLockedTarget, null);
    }

    private int attackTargets(Minecraft mc, EntityPlayerSP player, List<EntityLivingBase> targets,
            EntityLivingBase crosshairLockedTarget, AreaHuntOptions areaOptions) {
        if (mc == null || player == null || targets == null || targets.isEmpty()) {
            return 0;
        }
        if (isTeleportAttackMode()) {
            return attackTeleportTargets(mc, player, targets, areaOptions);
        }

        int attackLimit = isMouseClickAttackMode() ? 1 : Math.max(1, cfg().targetsPerAttack);
        int candidateLimit = shouldAttackPrimaryTargetOnly(crosshairLockedTarget)
                ? 1
                : targets.size();
        int attackedCount = 0;
        for (int i = 0; i < candidateLimit; i++) {
            if (attackedCount >= attackLimit) {
                break;
            }
            EntityLivingBase target = targets.get(i);
            if (!canAttackTargetBeforeRotation(player, target, areaOptions)) {
                if (java.lang.Math.random() < 0.05) {
                    hunt$dbg("attack-gate beforeRot dist=%.2f range=%.2f dead=%s seen=%s valid=%s",
                            player.getDistance(target), cfg().attackRange, target.isDead,
                            shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target),
                            isValidTarget(player, target, areaOptions));
                }
                if (java.lang.Math.random() < 0.02) {
                    hunt$dbg("attack-gate beforeRot dist=%.2f range=%.2f dead=%s seen=%s",
                            player.getDistance(target), cfg().attackRange, target.isDead,
                            shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target));
                }
                continue;
            }
            if (!prepareRotationForAttack(player, target, crosshairLockedTarget)) {
                if (java.lang.Math.random() < 0.05) hunt$dbg("attack-gate rotFail yaw=%.0f target=%d",
                        player.rotationYaw, target.getEntityId());
                continue;
            }
            boolean teleportAttack = shouldUseTeleportAttack(player, target);
            if (!canAttackTarget(player, target, shouldRequireCrosshairHitForAttack(teleportAttack), areaOptions)) {
                if (java.lang.Math.random() < 0.05) {
                    float yawDiff = Math.abs(MathHelper.wrapDegrees(
                            getDesiredAimRotation(player, target).getYaw() - player.rotationYaw));
                    hunt$dbg("attack-gate postRot yaw=%.0f yawDiff=%.0f target=%d crosshair=%s",
                            player.rotationYaw, yawDiff, target.getEntityId(),
                            shouldRequireCrosshairHitForAttack(teleportAttack));
                }
                continue;
            }

            // i-frame check must be read BEFORE attackEntity — attackEntityFrom
            // sets hurtResistantTime=maxHurtResistantTime on the hit itself, so
            // post-attack reads are always >0 and would skip every record.
            boolean attackInsideIframe = target.hurtResistantTime
                    > target.maxHurtResistantTime / 2.0F;
            if (java.lang.Math.random() < 0.05) {
                hunt$dbg("attack-hit target=%d yaw=%.0f iFrame=%s",
                        target.getEntityId(), player.rotationYaw, attackInsideIframe);
            }
            if (teleportAttack) {
                if (!performTeleportAttack(player, target)) {
                    continue;
                }
            } else if (isMouseClickAttackMode()) {
                if (!performMouseClickAttack(mc)) {
                    continue;
                }
            } else if (isPacketAttackMode()) {
                player.connection.sendPacket(new CPacketUseEntity(target));
            } else {
                ctxController().attackEntity(player, target);
            }
            if (!attackInsideIframe) {
                recordNoDamageAttackAttempt(target);
            }
            rememberBlockingAttack(player, target);
            attackedCount++;
        }
        return attackedCount;
    }

    private int attackTeleportTargets(Minecraft mc, EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        if (rt.teleport.timing.isRouteRejected()) return 0;
        if (mc == null || player == null || player.connection == null || targets == null || targets.isEmpty()
                || isTeleportAttackRecoveryActive()) {
            teleportDebug(player, "PLAN_STOP",
                    "reason=INVALID_ENTRY_OR_RECOVERY,mcNull=%s,playerNull=%s,connectionNull=%s,targetsNull=%s,targetCount=%d,recoveryActive=%s",
                    mc == null, player == null, player == null || player.connection == null, targets == null,
                    targets == null ? 0 : targets.size(), isTeleportAttackRecoveryActive());
            return 0;
        }

        // 缓存本次扫描结果：fast hook 的 directTargets 就靠它，否则 fast-path
        // 在 fast 线程上无目标可打，只能等下一次被节流的主 tick（~4 burst/s）。
        rt.teleport.directTargets = targets;
        syncTeleportPacketBudget(player);
        int currentTick = rt().teleport.currentTick();
        if (currentTick < rt().teleportPlanningRetryAfterTick) {
            teleportDebug(player, "PLAN_STOP", "reason=PLANNING_BACKOFF,retryAfter=%d,remainingTicks=%d",
                    rt().teleportPlanningRetryAfterTick, rt().teleportPlanningRetryAfterTick - currentTick);
            return 0;
        }
        int remainingPacketBudget = TELEPORT_ROUTE_MAX_WAYPOINTS;

        if (teleportAttackSlotDue()) {
            Vec3d serverPosition = rt().teleport.virtualPosition;
            if (serverPosition == null) serverPosition = player.getPositionVector();
            // 只有全部目标都已在近战范围内才走这条不规划的快路径；
            // 否则会一直原地打身边那只，远处的目标永远轮不到。
            if (allTeleportTargetsInMelee(player, serverPosition, targets)) {
                int burst = attackTeleportMeleeBurst(player, serverPosition, targets,
                        teleportBurstHitLimit());
                if (burst > 0) {
                    teleportDebug(player, "PLAN_DIRECT_HIT", "hits=%d", burst);
                    return burst;
                }
            }
        } else {
            rt.teleport.attackSkipsInterval++;
        }
        teleportDebug(player, "PLAN_BEGIN",
                "targets=%d,targetIds=%s,targetLimit=%d,remainingPacketBudget=%d,stepDistance=%.3f,stationRadius=%.3f,attackRange=%.3f",
                targets.size(), formatTeleportTargetIds(targets, 24), Math.max(1, cfg().targetsPerAttack),
                remainingPacketBudget, getTeleportStepDistance(), getTeleportMeleeRange(), cfg().attackRange);
        TeleportTourPlan plan = buildTeleportTourPlan(player, targets, areaOptions,
                Math.max(1, cfg().targetsPerAttack), remainingPacketBudget);
        if (plan == null || plan.attackCount <= 0) {
            rt().teleportPlanningRetryAfterTick = currentTick + TELEPORT_PLANNING_FAILURE_BACKOFF_TICKS;
            teleportDebug(player, "PLAN_STOP",
                    "reason=PLAN_NULL_OR_EMPTY,retryAfter=%d,targetIds=%s",
                    rt().teleportPlanningRetryAfterTick, formatTeleportTargetIds(targets, 24));
            return 0;
        }
        teleportDebug(player, "PLAN_READY",
                "stops=%d,attacks=%d,forwardPositions=%d,rotationPackets=%d,restoreRotation=%s,packetCost=%d,budget=%d",
                plan.stops.size(), plan.attackCount, plan.forwardPositions.size(), plan.rotationPacketCount,
                plan.restoreRotationAtOrigin, plan.packetCost, remainingPacketBudget);
        int attackedCount = executeTeleportTourPlan(player, plan, areaOptions);
        if (attackedCount > 0) {
            rt().teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
            teleportDebug(player, "PLAN_EXECUTED", "attackedCount=%d,packetCost=%d", attackedCount, plan.packetCost);
        } else {
            teleportDebug(player, "PLAN_STOP", "reason=EXECUTION_RETURNED_ZERO,packetCost=%d,attacks=%d",
                    plan.packetCost, plan.attackCount);
        }
        return attackedCount;
    }

    private TeleportTourPlan buildTeleportTourPlan(EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions, int targetLimit, int packetBudget) {
        RuntimeState rt = rt();
        if (player == null || targets == null || targets.isEmpty() || targetLimit <= 0 || packetBudget <= 1) {
            teleportDebug(player, "PLAN_BUILD_STOP",
                    "reason=INVALID_INPUT,targetCount=%d,targetLimit=%d,packetBudget=%d",
                    targets == null ? 0 : targets.size(), targetLimit, packetBudget);
            return null;
        }

        rt().teleport.astarAttemptedThisPlan = false;
        long planningStartedNanos = System.nanoTime();
        long planningTimeBudgetNanos = isTeleportDebugEnabled()
                ? TELEPORT_DEBUG_PLANNING_BUDGET_NANOS
                : TELEPORT_TOUR_PLANNING_BUDGET_NANOS;
        TeleportPlanningBudget planningBudget = new TeleportPlanningBudget(
                planningStartedNanos + planningTimeBudgetNanos,
                TELEPORT_PLANNING_WORLD_QUERY_LIMIT);
        teleportDebug(player, "PLAN_DIAGNOSTIC_BUDGET",
                "normalBudgetMicros=%d,effectiveBudgetMicros=%d,worldQueryLimit=%d",
                TELEPORT_TOUR_PLANNING_BUDGET_NANOS / 1000L, planningTimeBudgetNanos / 1000L,
                TELEPORT_PLANNING_WORLD_QUERY_LIMIT);
        Vec3d anchoredOrigin = resolveTeleportAnchorPosition(player);
        if (anchoredOrigin == null && TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)) {
            // Mid-wave chaining only makes sense when the body is parked; in the other
            // modes the body itself is where the server thinks we are.
            anchoredOrigin = rt.teleport.virtualPosition;
        }
        TeleportTourPlan plan = new TeleportTourPlan(anchoredOrigin != null
                ? new TeleportOrigin(anchoredOrigin, player)
                : new TeleportOrigin(player));
        List<EntityLivingBase> remaining = new ArrayList<>();
        int currentTick = rt().teleport.currentTick();
        double searchRangeSq = cfg().attackRange * cfg().attackRange;
        boolean useWhitelistPriority = areaOptions == null
                && cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        for (EntityLivingBase target : targets) {
            if (remaining.size() >= TELEPORT_TARGET_PLAN_LIMIT) {
                teleportDebug(player, "PLAN_TARGET_LIMIT", "limit=%d,remainingInput=%d",
                        TELEPORT_TARGET_PLAN_LIMIT, targets.size() - remaining.size());
                break;
            }
            if (target == null) {
                teleportDebug(player, "PLAN_TARGET_REJECT", "reason=TARGET_NULL");
                continue;
            }
            int retryAfterTick = rt().teleport.teleportTargetRetryAfterTicks.getOrDefault(target.getEntityId(), Integer.MIN_VALUE);
            if (currentTick < retryAfterTick) {
                teleportDebug(player, "PLAN_TARGET_REJECT", "reason=TARGET_RETRY_BACKOFF,retryAfter=%d,target={%s}",
                        retryAfterTick, formatTeleportTarget(player, target));
                continue;
            }
            if (buildTargetCandidate(player, target, searchRangeSq, useWhitelistPriority,
                    target.getEntityId() == rt().currentTargetEntityId, true, areaOptions) == null) {
                teleportDebug(player, "PLAN_TARGET_REJECT", "reason=%s,target={%s}",
                        getTeleportTargetRejectionReason(player, target, searchRangeSq,
                                useWhitelistPriority, areaOptions),
                        formatTeleportTarget(player, target));
                continue;
            }
            remaining.add(target);
        }
        if (remaining.isEmpty()) {
            teleportDebug(player, "PLAN_BUILD_STOP", "reason=NO_VALID_TARGETS_AFTER_FILTER");
            return null;
        }

        // Route legs start at the feet; plan.origin stays the walk-home target.
        Vec3d currentPosition = player.getPositionVector();
        while (!remaining.isEmpty() && plan.attackCount < targetLimit
                && plan.stops.size() < TELEPORT_STATION_LIMIT && planningBudget.canContinue()) {
            List<TeleportStationSeed> seeds = buildTeleportStationSeeds(currentPosition, remaining);
            teleportDebug(player, "PLAN_STATION_ROUND",
                    "round=%d,current=(%.3f,%.3f,%.3f),remainingTargets=%s,seeds=%d,queriesRemaining=%d,timeRemainingMicros=%d",
                    plan.stops.size(), currentPosition.x, currentPosition.y, currentPosition.z,
                    formatTeleportTargetIds(remaining, 24), seeds.size(), planningBudget.remainingWorldQueries,
                    Math.max(0L, planningBudget.deadlineNanos - System.nanoTime()) / 1000L);
            TeleportTourStop bestStop = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (TeleportStationSeed seed : seeds) {
                if (!planningBudget.canContinue()) {
                    break;
                }
                TeleportPlanningBudget seedBudget = planningBudget.child(TELEPORT_PLANNING_QUERIES_PER_SEED);
                teleportDebug(player, "PLAN_SEED_BEGIN",
                        "primary=%s,coverage=%d,desired=(%.3f,%.3f,%.3f),distance=%.3f,parentQueries=%d,seedQueries=%d",
                        seed.primary, seed.coverage, seed.position.x, seed.position.y, seed.position.z,
                        Math.sqrt(currentPosition.squareDistanceTo(seed.position)), planningBudget.remainingWorldQueries,
                        seedBudget.remainingWorldQueries);
                Vec3d stationPosition = findFastTeleportStation(player, currentPosition, seed.position, seedBudget);
                if (stationPosition == null) {
                    deferFailedTeleportSeed(player, seed, seedBudget, "NO_CLEAR_STATION");
                    teleportDebug(player, "PLAN_SEED_REJECT",
                            "reason=NO_CLEAR_STATION,detail=%s,primary=%s,desired=(%.3f,%.3f,%.3f),parentQueries=%d,seedQueries=%d,deadlineReached=%s",
                            seedBudget.getFailureReason(),
                            seed.primary, seed.position.x, seed.position.y, seed.position.z,
                            planningBudget.remainingWorldQueries, seedBudget.remainingWorldQueries,
                            System.nanoTime() >= seedBudget.deadlineNanos);
                    continue;
                }
                int maximumLegPackets = Math.max(0,
                        (packetBudget - plan.attackCount - plan.rotationPacketCount - 1) / 2
                                - plan.forwardPositions.size());
                List<Vec3d> legPositions = buildFastTeleportLeg(player, currentPosition, stationPosition,
                        maximumLegPackets, seedBudget);
                if (legPositions != null && !isTeleportLegExecutable(player, currentPosition, legPositions)) {
                    // The leg builder and the tour executor use different clearance
                    // tests, so a leg can be planned and then rejected on every tick.
                    // Validate with the executor's own test before committing.
                    teleportDebug(player, "PLAN_SEED_REJECT",
                            "reason=LEG_NOT_EXECUTABLE,station=(%.3f,%.3f,%.3f),from=(%.3f,%.3f,%.3f),legPoints=%d",
                            stationPosition.x, stationPosition.y, stationPosition.z,
                            currentPosition.x, currentPosition.y, currentPosition.z, legPositions.size());
                    legPositions = null;
                }
                if (legPositions == null) {
                    deferFailedTeleportSeed(player, seed, seedBudget, "NO_CLEAR_ROUTE_OR_LEG_BUDGET");
                    teleportDebug(player, "PLAN_SEED_REJECT",
                            "reason=NO_CLEAR_ROUTE_OR_LEG_BUDGET,detail=%s,station=(%.3f,%.3f,%.3f),maximumLegPackets=%d,distance=%.3f,parentQueries=%d,seedQueries=%d,deadlineReached=%s",
                            seedBudget.getFailureReason(),
                            stationPosition.x, stationPosition.y, stationPosition.z, maximumLegPackets,
                            currentPosition.distanceTo(stationPosition), planningBudget.remainingWorldQueries,
                            seedBudget.remainingWorldQueries, System.nanoTime() >= seedBudget.deadlineNanos);
                    continue;
                }
                List<EntityLivingBase> stationTargets = collectTeleportStationTargets(player, remaining,
                        stationPosition, legPositions.size(), plan, targetLimit, packetBudget, seedBudget);
                if (stationTargets.isEmpty()) {
                    teleportDebug(player, "PLAN_SEED_REJECT",
                            "reason=NO_TARGETS_ATTACKABLE_FROM_STATION,station=(%.3f,%.3f,%.3f),legPackets=%d,remainingTargets=%s,seedQueries=%d",
                            stationPosition.x, stationPosition.y, stationPosition.z, legPositions.size(),
                            formatTeleportTargetIds(remaining, 24), seedBudget.remainingWorldQueries);
                    continue;
                }
                int candidateRotationPackets = getTeleportStationRotationPacketCount(stationTargets.size(),
                        legPositions.isEmpty());
                int addedPackets = legPositions.size() * 2 + stationTargets.size() + candidateRotationPackets;
                double score = (seed.primary ? 1.0E9D : 0.0D)
                        + stationTargets.size() * 1000.0D / Math.max(1, addedPackets)
                        - currentPosition.squareDistanceTo(stationPosition) * 0.0001D;
                if (bestStop == null || score > bestScore
                        || (Math.abs(score - bestScore) <= 1.0E-8D
                                && stationTargets.size() > bestStop.targets.size())) {
                    bestScore = score;
                    bestStop = new TeleportTourStop(stationPosition, legPositions, stationTargets,
                            candidateRotationPackets);
                }
                teleportDebug(player, "PLAN_SEED_ACCEPT",
                        "station=(%.3f,%.3f,%.3f),targets=%s,legPackets=%d,rotationPackets=%d,addedPackets=%d,score=%.6f",
                        stationPosition.x, stationPosition.y, stationPosition.z,
                        formatTeleportTargetIds(stationTargets, 24), legPositions.size(), candidateRotationPackets,
                        addedPackets, score);
            }

            if (bestStop == null) {
                teleportDebug(player, "PLAN_BUILD_STOP",
                        "reason=NO_STATION_SELECTED,queriesRemaining=%d,deadlineReached=%s,elapsedMicros=%d,remainingTargets=%s",
                        planningBudget.remainingWorldQueries, System.nanoTime() >= planningBudget.deadlineNanos,
                        (System.nanoTime() - planningStartedNanos) / 1000L,
                        formatTeleportTargetIds(remaining, 24));
                trimTeleportRuntimeMaps();
                break;
            }

            plan.addStop(bestStop);
            teleportDebug(player, "PLAN_STOP_ADDED",
                    "stopIndex=%d,station=(%.3f,%.3f,%.3f),targets=%s,legPackets=%d,totalAttacks=%d,totalForwardPositions=%d",
                    plan.stops.size() - 1, bestStop.position.x, bestStop.position.y, bestStop.position.z,
                    formatTeleportTargetIds(bestStop.targets, 24), bestStop.legPositions.size(), plan.attackCount,
                    plan.forwardPositions.size());
            Set<Integer> attackedIds = new HashSet<>();
            for (EntityLivingBase target : bestStop.targets) {
                attackedIds.add(target.getEntityId());
            }
            remaining.removeIf(target -> attackedIds.contains(target.getEntityId()));
            currentPosition = bestStop.position;
        }

        if (plan.attackCount <= 0) {
            teleportDebug(player, "PLAN_BUILD_STOP",
                    "reason=ZERO_ATTACKS,elapsedMicros=%d,queriesRemaining=%d,deadlineReached=%s",
                    (System.nanoTime() - planningStartedNanos) / 1000L, planningBudget.remainingWorldQueries,
                    System.nanoTime() >= planningBudget.deadlineNanos);
            return null;
        }
        plan.restoreRotationAtOrigin = plan.forwardPositions.isEmpty() && plan.rotationPacketCount > 0;
        plan.packetCost = 1 + plan.forwardPositions.size() * 2
                + plan.attackCount + plan.rotationPacketCount + (plan.restoreRotationAtOrigin ? 1 : 0);
        if (plan.packetCost > packetBudget) {
            teleportDebug(player, "PLAN_BUILD_STOP",
                    "reason=FINAL_PACKET_COST_EXCEEDED,cost=%d,budget=%d,attacks=%d,forwardPositions=%d,rotations=%d",
                    plan.packetCost, packetBudget, plan.attackCount, plan.forwardPositions.size(),
                    plan.rotationPacketCount);
            return null;
        }
        teleportDebug(player, "PLAN_BUILD_DONE",
                "elapsedMicros=%d,exceededNormalOneMillisecondBudget=%s,queriesUsed=%d,queriesRemaining=%d,stops=%d,attacks=%d,packetCost=%d",
                (System.nanoTime() - planningStartedNanos) / 1000L,
                System.nanoTime() - planningStartedNanos > TELEPORT_TOUR_PLANNING_BUDGET_NANOS,
                TELEPORT_PLANNING_WORLD_QUERY_LIMIT - planningBudget.remainingWorldQueries,
                planningBudget.remainingWorldQueries, plan.stops.size(), plan.attackCount, plan.packetCost);
        return plan;
    }

    /** Wall-clock backoff check; the tick counter is not a reliable clock here. */
    private boolean isTeleportTargetBackedOff(EntityLivingBase target) {
        Long until = rt().teleport.teleportTargetRetryUntilMillis.get(target.getEntityId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        rt().teleport.teleportTargetRetryUntilMillis.remove(target.getEntityId());
        rt().teleport.teleportTargetRetryAfterTicks.remove(target.getEntityId());
        return false;
    }

    /**
     * A backed-off target that has since walked away deserves a fresh evaluation:
     * the earlier "nowhere to stand" verdict described its old footprint.
     */
    private boolean teleportBackoffTargetStale(EntityLivingBase target) {
        net.minecraft.util.math.Vec3d recorded =
                rt().teleport.teleportTargetRetryPositions.get(target.getEntityId());
        if (recorded == null) {
            return false;
        }
        if (recorded.squareDistanceTo(target.posX, target.posY, target.posZ)
                <= TELEPORT_BACKOFF_STALE_DISTANCE_SQ) {
            return false;
        }
        rt().teleport.teleportTargetRetryAfterTicks.remove(target.getEntityId());
        rt().teleport.teleportTargetRetryUntilMillis.remove(target.getEntityId());
        rt().teleport.teleportTargetRetryPositions.remove(target.getEntityId());
        rt().teleport.teleportTargetFailureCounts.remove(target.getEntityId());
        return true;
    }

    private void deferFailedTeleportSeed(EntityPlayerSP player, TeleportStationSeed seed,
            TeleportPlanningBudget planningBudget, String phase) {
        if (player == null || seed == null || seed.targetEntityId == Integer.MIN_VALUE) {
            return;
        }
        String detail = planningBudget == null ? "NO_BUDGET" : planningBudget.getFailureReason();
        boolean budgetFailure = detail.contains("BUDGET")
                || detail.contains("QUERY_LIMIT")
                || detail.contains("DEADLINE")
                || detail.contains("PARENT");
        int retryTicks;
        if (budgetFailure) {
            // Running out of planning budget says nothing about the target, so it
            // must not feed the unreachable streak.
            retryTicks = TELEPORT_TARGET_BUDGET_FAILURE_BACKOFF_TICKS;
        } else if (!"NO_CLEAR_STATION".equals(phase)) {
            // A failed route plan is a property of this attempt, not of the target:
            // the terrain can be walked around next time the grid happens to expand
            // further. Only "no spot to stand in at all" is permanent.
            rt().teleport.teleportTargetFailureCounts.remove(seed.targetEntityId);
            retryTicks = TELEPORT_TARGET_ROUTE_FAILURE_BACKOFF_TICKS;
        } else {
            int failures = rt().teleport.teleportTargetFailureCounts
                    .merge(seed.targetEntityId, 1, Integer::sum);
            retryTicks = failures >= TELEPORT_UNREACHABLE_FAILURE_STREAK
                    ? TELEPORT_UNREACHABLE_TARGET_BACKOFF_TICKS
                    : TELEPORT_TARGET_ROUTE_FAILURE_BACKOFF_TICKS;
        }
        int retryAfterTick = rt().teleport.currentTick() + retryTicks;
        int existingRetry = rt().teleport.teleportTargetRetryAfterTicks.getOrDefault(seed.targetEntityId, Integer.MIN_VALUE);
        long retryUntilMillis = System.currentTimeMillis() + retryTicks * 50L;
        Long existingUntil = rt().teleport.teleportTargetRetryUntilMillis.get(seed.targetEntityId);
        if (retryUntilMillis > (existingUntil == null ? Long.MIN_VALUE : existingUntil)) {
            rt().teleport.teleportTargetRetryUntilMillis.put(seed.targetEntityId, retryUntilMillis);
        }
        if (retryAfterTick > existingRetry) {
            rt().teleport.teleportTargetRetryAfterTicks.put(seed.targetEntityId, retryAfterTick);
            net.minecraft.entity.Entity backedOff = player.world.getEntityByID(seed.targetEntityId);
            if (backedOff != null) {
                rt().teleport.teleportTargetRetryPositions.put(seed.targetEntityId,
                        new net.minecraft.util.math.Vec3d(backedOff.posX, backedOff.posY, backedOff.posZ));
            }
        }
        teleportDebug(player, "TARGET_DEFERRED",
                "id=%d,phase=%s,detail=%s,budgetFailure=%s,retryTicks=%d,retryAfter=%d",
                seed.targetEntityId, phase, detail, budgetFailure, retryTicks, retryAfterTick);
    }

    private List<TeleportStationSeed> buildTeleportStationSeeds(Vec3d currentPosition,
            List<EntityLivingBase> targets) {
        RuntimeState rt = rt();
        List<TeleportStationSeed> seeds = new ArrayList<>();
        int seedLimit = Math.min(TELEPORT_STATION_SEED_LIMIT, targets.size());
        if (seedLimit <= 0) {
            return seeds;
        }

        EntityLivingBase primaryTarget = targets.get(0);
        Vec3d primaryPosition = getPreferredTeleportStationPosition(currentPosition, primaryTarget);
        seeds.add(new TeleportStationSeed(primaryTarget.getEntityId(), primaryPosition,
                countTargetsWithinTeleportStation(primaryPosition, targets),
                currentPosition.squareDistanceTo(primaryPosition), true));

        // Stations that cover several attack circles at once. With targetsPerAttack
        // above one this is where most of the travel distance disappears.
        List<TeleportStationSeed> multicover = new ArrayList<>();
        if (targets.size() > 1) {
            double radius = getTeleportMeleeRange();
            for (Vec3d candidate : buildMultiCoverCandidates(targets, radius)) {
                int coverage = countTargetsWithinTeleportStation(candidate, targets);
                if (coverage < 2) {
                    continue;
                }
                multicover.add(new TeleportStationSeed(nearestTeleportTargetId(candidate, targets),
                        candidate, coverage, currentPosition.squareDistanceTo(candidate), false));
            }
            multicover.sort(null);
        }
        int multicoverLimit = Math.min(TELEPORT_STATION_CANDIDATE_LIMIT, multicover.size());
        for (int i = 0; i < multicoverLimit; i++) {
            seeds.add(multicover.get(i));
        }

        List<TeleportStationSeed> alternatives = new ArrayList<>();
        for (int i = 1; i < seedLimit; i++) {
            EntityLivingBase target = targets.get(i);
            Vec3d position = getPreferredTeleportStationPosition(currentPosition, target);
            int coverage = countTargetsWithinTeleportStation(position, targets);
            TeleportStationSeed seed = new TeleportStationSeed(target.getEntityId(), position, coverage,
                    currentPosition.squareDistanceTo(position), false);
            int insertAt = 0;
            while (insertAt < alternatives.size() && alternatives.get(insertAt).compareTo(seed) <= 0) {
                insertAt++;
            }
            alternatives.add(insertAt, seed);
        }
        if (!alternatives.isEmpty()) {
            int start = Math.floorMod(rt().teleportStationAlternativeCursor, alternatives.size());
            int alternativeLimit = Math.min(TELEPORT_STATION_CANDIDATE_LIMIT - 1, alternatives.size());
            for (int i = 0; i < alternativeLimit; i++) {
                seeds.add(alternatives.get((start + i) % alternatives.size()));
            }
            rt().teleportStationAlternativeCursor = (start + 1) % alternatives.size();
        }
        return seeds;
    }

    /**
     * Candidate feet positions that can sit inside more than one target's attack
     * circle: sampled points on each circle plus every pair's intersections.
     */
    private List<Vec3d> buildMultiCoverCandidates(List<EntityLivingBase> targets, double radius) {
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            EntityLivingBase a = targets.get(i);
            for (int k = 0; k < 8; k++) {
                double angle = k * Math.PI * 2.0D / 8.0D;
                out.add(new Vec3d(a.posX + radius * Math.cos(angle), a.posY,
                        a.posZ + radius * Math.sin(angle)));
            }
            for (int j = i + 1; j < targets.size(); j++) {
                EntityLivingBase b = targets.get(j);
                double midY = (a.posY + b.posY) * 0.5D;
                for (Vec3d point : circleIntersectionsXZ(a.posX, a.posZ, b.posX, b.posZ, radius)) {
                    out.add(new Vec3d(point.x, midY, point.z));
                }
            }
        }
        return out;
    }

    private static List<Vec3d> circleIntersectionsXZ(double x1, double z1, double x2, double z2,
            double radius) {
        List<Vec3d> out = new ArrayList<>();
        double dx = x2 - x1;
        double dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-6D || distance > radius * 2.0D) {
            return out;
        }
        double half = distance * 0.5D;
        double heightSq = radius * radius - half * half;
        if (heightSq < 0.0D) {
            return out;
        }
        double height = Math.sqrt(heightSq);
        double midX = x1 + dx * 0.5D;
        double midZ = z1 + dz * 0.5D;
        double offX = -dz * height / distance;
        double offZ = dx * height / distance;
        out.add(new Vec3d(midX + offX, 0.0D, midZ + offZ));
        out.add(new Vec3d(midX - offX, 0.0D, midZ - offZ));
        return out;
    }

    private int nearestTeleportTargetId(Vec3d position, List<EntityLivingBase> targets) {
        int bestId = Integer.MIN_VALUE;
        double bestSq = Double.MAX_VALUE;
        for (EntityLivingBase target : targets) {
            double d = getDistanceSq(position, target.posX, target.posY, target.posZ);
            if (d < bestSq) {
                bestSq = d;
                bestId = target.getEntityId();
            }
        }
        return bestId;
    }

    private Vec3d getPreferredTeleportStationPosition(Vec3d currentPosition, EntityLivingBase target) {
        Vec3d targetPosition = new Vec3d(target.posX, target.posY, target.posZ);
        Vec3d delta = targetPosition.subtract(currentPosition);
        double distance = delta.lengthVector();
        double radius = getTeleportMeleeRange();
        if (distance <= radius * 0.9D || distance <= 1.0E-6D) {
            return currentPosition;
        }
        double standOff = Math.max(0.25D, radius * 0.85D);
        double scale = Math.max(0.0D, distance - standOff) / distance;
        return currentPosition.addVector(delta.x * scale, delta.y * scale, delta.z * scale);
    }

    private int countTargetsWithinTeleportStation(Vec3d stationPosition, List<EntityLivingBase> targets) {
        if (stationPosition == null || targets == null) {
            return 0;
        }
        double radiusSq = getTeleportMeleeRange() * getTeleportMeleeRange();
        int count = 0;
        for (EntityLivingBase target : targets) {
            if (target != null && getDistanceSq(stationPosition, target.posX, target.posY, target.posZ) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private boolean isTeleportLegExecutable(EntityPlayerSP player, Vec3d start, List<Vec3d> leg) {
        if (start == null || leg == null) {
            return false;
        }
        Vec3d cursor = start;
        for (Vec3d point : leg) {
            if (point == null) {
                return false;
            }
            if (!isTeleportStepClear(player, cursor, point) || !isTeleportPositionClear(player, point)) {
                return false;
            }
            cursor = point;
        }
        return true;
    }

    private Vec3d findFastTeleportStation(EntityPlayerSP player, Vec3d currentPosition, Vec3d desiredPosition,
            TeleportPlanningBudget planningBudget) {
        if (player == null || desiredPosition == null || planningBudget == null) {
            return null;
        }
        if (currentPosition.squareDistanceTo(desiredPosition) <= TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ) {
            return currentPosition;
        }
        // One step up covers a kerb or half-slab when the interpolated station lands
        // inside a block; the full body box still has to fit, so a low ceiling rejects
        // it. Nothing higher: two steps would park the station above the ceiling.
        double[][] offsets = new double[][] {
                { 0.0D, 0.0D, 0.0D }, { 0.0D, 1.0D, 0.0D }, { 0.0D, -1.0D, 0.0D },
                { 0.75D, 0.0D, 0.0D }, { -0.75D, 0.0D, 0.0D },
                { 0.0D, 0.0D, 0.75D }, { 0.0D, 0.0D, -0.75D }
        };
        int probeLimit = Math.min(TELEPORT_STATION_POSITION_PROBE_LIMIT, offsets.length);
        for (int i = 0; i < probeLimit && planningBudget.canContinue(); i++) {
            Vec3d candidate = desiredPosition.addVector(offsets[i][0], offsets[i][1], offsets[i][2]);
            if (isFastTeleportPositionClear(player, candidate, planningBudget)) {
                return candidate;
            }
        }
        return null;
    }

    private List<EntityLivingBase> collectTeleportStationTargets(EntityPlayerSP player,
            List<EntityLivingBase> remaining, Vec3d stationPosition, int legPacketCount,
            TeleportTourPlan plan, int targetLimit, int packetBudget, TeleportPlanningBudget planningBudget) {
        List<EntityLivingBase> selected = new ArrayList<>();
        for (EntityLivingBase target : remaining) {
            if (plan.attackCount + selected.size() >= targetLimit || !planningBudget.canContinue()) {
                if (!planningBudget.canContinue()) {
                    planningBudget.markFailure("STATION_TARGET_SELECTION_BUDGET_EXHAUSTED");
                }
                break;
            }
            if (!canAttackTargetFromTeleportStation(player, target, stationPosition, planningBudget)) {
                continue;
            }
            int selectedCount = selected.size() + 1;
            int candidateRotationPackets = getTeleportStationRotationPacketCount(selectedCount, legPacketCount <= 0);
            int projectedForwardPackets = plan.forwardPositions.size() + legPacketCount;
            int projectedRotationPackets = plan.rotationPacketCount + candidateRotationPackets;
            int projectedRestorePackets = projectedForwardPackets == 0 && projectedRotationPackets > 0 ? 1 : 0;
            int projectedCost = 1 + projectedForwardPackets * 2 + plan.attackCount
                    + projectedRotationPackets + selectedCount + projectedRestorePackets;
            if (projectedCost > packetBudget) {
                planningBudget.markFailure("STATION_TARGET_PACKET_BUDGET(projected=" + projectedCost
                        + ",budget=" + packetBudget + ")");
                teleportDebug(player, "STATION_TARGET_REJECT",
                        "reason=PACKET_BUDGET,projectedCost=%d,budget=%d,target={%s}",
                        projectedCost, packetBudget, formatTeleportTarget(player, target));
                break;
            }
            selected.add(target);
            teleportDebug(player, "STATION_TARGET_ACCEPT",
                    "station=(%.3f,%.3f,%.3f),selectedCount=%d,target={%s}",
                    stationPosition.x, stationPosition.y, stationPosition.z, selected.size(),
                    formatTeleportTarget(player, target));
        }
        return selected;
    }

    private boolean canAttackTargetFromTeleportStation(EntityPlayerSP player, EntityLivingBase target,
            Vec3d stationPosition, TeleportPlanningBudget planningBudget) {
        if (player == null || target == null || stationPosition == null || target.isDead
                || target.getHealth() <= 0.0F) {
            if (planningBudget != null) {
                planningBudget.markFailure("STATION_TARGET_INVALID_OR_DEAD");
            }
            teleportDebug(player, "STATION_TARGET_REJECT", "reason=INVALID_OR_DEAD,target={%s}",
                    formatTeleportTarget(player, target));
            return false;
        }
        // Deliberately NOT gated on the hurt window here. Planning answers "can I
        // physically reach an attack position", not "is it this instant". Filtering
        // on the window made every plan fail for ~500ms right after each hit, so the
        // aura idled instead of walking to the next station. The tour waits for the
        // window in ticks once it is standing there.
        double radius = getTeleportMeleeRange();
        double stationDistanceSq = getDistanceSq(stationPosition, target.posX, target.posY, target.posZ);
        // Station seeds shift by up to 0.75 blocks to find clear footing, so a
        // target can land a few thousandths past the radius. Rejecting that is
        // pure churn, so allow a small slack.
        double stationLimit = radius + 0.05D;
        if (stationDistanceSq > stationLimit * stationLimit) {
            planningBudget.markFailure("STATION_TARGET_OUT_OF_RADIUS");
            teleportDebug(player, "STATION_TARGET_REJECT",
                    "reason=OUT_OF_RADIUS,distance=%.3f,radius=%.3f,target={%s}",
                    Math.sqrt(stationDistanceSq), radius, formatTeleportTarget(player, target));
            return false;
        }
        // TP 攻击不看视线：站位本来就是传到目标身边，只要路线走得通就该能打。
        // 之前这里按「必须可见」射线过滤，隔着围栏的目标每次都被判成站点打不到，
        // 规划只能退回本体身边那一站，远处目标永远轮不到。
        return true;
    }

    private int getTeleportStationRotationPacketCount(int targetCount, boolean stationHasNoMovement) {
        if (!shouldRotateToTarget() || targetCount <= 0) {
            return 0;
        }
        return stationHasNoMovement ? targetCount : Math.max(0, targetCount - 1);
    }

    /** Returns scheduled attack count, not successful hits. Execution happens on later ticks. */
    private int executeTeleportTourPlan(EntityPlayerSP player, TeleportTourPlan plan, AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        if (rt.teleport.timing.isRouteRejected() || rt.activeTeleportTourExecution != null
                || player == null || player.connection == null || plan == null || plan.attackCount <= 0
                || !isTeleportTourPlanStillValid(player, plan, areaOptions)) return 0;
        stopHuntNavigation();
        stopHuntPickupNavigation();
        rt.activeTeleportTourExecution = new TeleportTourExecution(player, plan);
        rt.teleport.plansExecuted++;
        teleportDebug(player, "TOUR_SCHEDULED", "stops=%d,plannedAttacks=%d,origin=(%.3f,%.3f,%.3f)",
                plan.stops.size(), plan.attackCount, plan.origin.x, plan.origin.y, plan.origin.z);
        return plan.attackCount;
    }

    private boolean isTeleportTourPlanStillValid(EntityPlayerSP player, TeleportTourPlan plan,
            AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        if (player == null || player.world == null || plan == null) {
            teleportDebug(player, "REVALIDATE_FAIL", "reason=PLAYER_WORLD_OR_PLAN_NULL");
            return false;
        }
        Vec3d originFrom = TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)
                && rt().teleport.virtualPosition != null
                ? rt().teleport.virtualPosition : player.getPositionVector();
        double originDistanceSq = getDistanceSq(originFrom, plan.origin.x, plan.origin.y, plan.origin.z);
        if (originDistanceSq > TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ) {
            teleportDebug(player, "REVALIDATE_FAIL",
                    "reason=PLAYER_MOVED_FROM_ORIGIN,distanceSq=%.6f,toleranceSq=%.6f,current=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    originDistanceSq, TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ, player.posX, player.posY, player.posZ,
                    plan.origin.x, plan.origin.y, plan.origin.z);
            return false;
        }
        double searchRangeSq = cfg().attackRange * cfg().attackRange;
        double revalidateLimit = getTeleportMeleeRange() + 0.05D;
        double stationRadiusSq = revalidateLimit * revalidateLimit;
        boolean useWhitelistPriority = areaOptions == null
                && cfg().enableNameWhitelist && cfg().nameWhitelist != null && !cfg().nameWhitelist.isEmpty();
        int validatedTargets = 0;
        for (TeleportTourStop stop : plan.stops) {
            if (stop == null || stop.position == null || stop.targets.isEmpty()) {
                teleportDebug(player, "REVALIDATE_FAIL", "reason=EMPTY_OR_INVALID_STOP");
                return false;
            }
            for (EntityLivingBase target : stop.targets) {
                if (target == null) {
                    teleportDebug(player, "REVALIDATE_FAIL", "reason=TARGET_NULL");
                    return false;
                }
                if (player.world.getEntityByID(target.getEntityId()) != target) {
                    teleportDebug(player, "REVALIDATE_FAIL", "reason=TARGET_UNLOADED_OR_REPLACED,target={%s}",
                            formatTeleportTarget(player, target));
                    return false;
                }
                if (buildTargetCandidate(player, target, searchRangeSq, useWhitelistPriority,
                        target.getEntityId() == rt().currentTargetEntityId, true, areaOptions) == null) {
                    teleportDebug(player, "REVALIDATE_FAIL", "reason=%s,target={%s}",
                            getTeleportTargetRejectionReason(player, target, searchRangeSq,
                                    useWhitelistPriority, areaOptions),
                            formatTeleportTarget(player, target));
                    return false;
                }
                double stationDistanceSq = getDistanceSq(stop.position, target.posX, target.posY, target.posZ);
                if (stationDistanceSq > stationRadiusSq) {
                    teleportDebug(player, "REVALIDATE_FAIL",
                            "reason=TARGET_MOVED_OUT_OF_STATION_RADIUS,stationDistance=%.3f,stationRadius=%.3f,target={%s}",
                            Math.sqrt(stationDistanceSq), Math.sqrt(stationRadiusSq),
                            formatTeleportTarget(player, target));
                    return false;
                }
                validatedTargets++;
            }
        }
        if (validatedTargets != plan.attackCount) {
            teleportDebug(player, "REVALIDATE_FAIL", "reason=ATTACK_COUNT_MISMATCH,validated=%d,planned=%d",
                    validatedTargets, plan.attackCount);
            return false;
        }
        teleportDebug(player, "REVALIDATE_OK", "validatedTargets=%d", validatedTargets);
        return true;
    }

    private TeleportAttackPlan mergeTeleportRecoveryPlan(TeleportAttackPlan activePlan,
            TeleportAttackPlan newPlan) {
        if (newPlan == null) {
            return activePlan;
        }
        if (activePlan == null) {
            return newPlan;
        }
        List<TeleportRoute> mergedRoutes = new ArrayList<>(activePlan.relatedRoutes);
        mergedRoutes.addAll(newPlan.relatedRoutes);
        newPlan.relatedRoutes.clear();
        newPlan.relatedRoutes.addAll(mergedRoutes);
        while (newPlan.relatedRoutes.size() > TELEPORT_RECOVERY_ROUTE_LIMIT) {
            newPlan.relatedRoutes.remove(0);
        }
        return newPlan;
    }

    private TeleportAttackPlan createTeleportTourRecoveryPlan(TeleportTourPlan plan) {
        if (plan == null || plan.forwardPositions.isEmpty()) {
            return null;
        }
        Vec3d assault = plan.forwardPositions.get(plan.forwardPositions.size() - 1);
        List<Vec3d> outboundWaypoints = new ArrayList<>(plan.forwardPositions);
        outboundWaypoints.remove(outboundWaypoints.size() - 1);
        List<Vec3d> returnWaypoints = new ArrayList<>();
        for (int i = plan.forwardPositions.size() - 2; i >= 0; i--) {
            returnWaypoints.add(plan.forwardPositions.get(i));
        }
        TeleportAttackPlan recoveryPlan = new TeleportAttackPlan(plan.origin,
                new TeleportAssaultCandidate(assault.x, assault.y, assault.z, false, 0.0D),
                outboundWaypoints, returnWaypoints, plan.origin.yaw, plan.origin.pitch);
        registerTeleportRoute(recoveryPlan, recoveryPlan, plan.origin.toPosition(),
                outboundWaypoints, assault);
        List<Vec3d> returnRoute = new ArrayList<>();
        returnRoute.add(assault);
        returnRoute.addAll(returnWaypoints);
        returnRoute.add(plan.origin.toPosition());
        registerTeleportRoute(recoveryPlan, recoveryPlan, returnRoute);
        return recoveryPlan;
    }

    private Rotation getFastTeleportAttackRotation(EntityPlayerSP player, EntityLivingBase target,
            Vec3d stationPosition) {
        if (target == null || stationPosition == null) {
            return new Rotation(player == null ? 0.0F : player.rotationYaw,
                    player == null ? 0.0F : player.rotationPitch);
        }
        double dx = target.posX - stationPosition.x;
        double dy = target.posY + target.getEyeHeight() * 0.75D
                - (stationPosition.y + (player == null ? 1.62D : player.getEyeHeight()));
        double dz = target.posZ - stationPosition.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new Rotation((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D),
                (float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
    }

    private void trimTeleportRuntimeMaps() {
        trimOldestEntries(rt().teleport.teleportLastAttackTicks, TELEPORT_TARGET_HISTORY_LIMIT);
        trimOldestEntries(rt().teleport.teleportTargetRetryAfterTicks, TELEPORT_TARGET_CACHE_LIMIT);
    }

    private void trimOldestEntries(Map<Integer, Integer> values, int maximumSize) {
        while (values.size() > maximumSize) {
            Iterator<Integer> iterator = values.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private List<Vec3d> buildFastTeleportLeg(EntityPlayerSP player, Vec3d start, Vec3d end,
            int maximumPackets, TeleportPlanningBudget planningBudget) {
        if (player == null || start == null || end == null || planningBudget == null) {
            return null;
        }
        if (start.squareDistanceTo(end) <= TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ) {
            return new ArrayList<>();
        }
        if (maximumPackets <= 0) {
            return null;
        }
        double stepDistance = getTeleportStepDistance();
        int directPackets = Math.max(1, (int) Math.ceil(start.distanceTo(end) / stepDistance));
        if (directPackets <= maximumPackets
                && isFastTeleportSegmentClear(player, start, end, planningBudget)) {
            return interpolateTeleportLeg(start, end, directPackets);
        }
        // No raised-cruise fallback: climbing to y+4/8/16 and flying over is slower,
        // is visible from far away, and costs the world-query budget the ground
        // route needs. Ground A* is the only detour.
        return buildGroundedTeleportLeg(player, start, end, maximumPackets, stepDistance, planningBudget);
    }

    /**
     * 用 A* 瞬移寻路生成地面绕障路线：沿网格路径抽稀成每步不超过 stepDistance 的路点，
     * 全程贴着可行走面走，不再抬高巡航。A* 失败或预算耗尽时返回 null 让上层退回抬高方案。
     */
    private List<Vec3d> buildGroundedTeleportLeg(EntityPlayerSP player, Vec3d start, Vec3d end,
            int maximumPackets, double stepDistance, TeleportPlanningBudget planningBudget) {
        if (player == null || start == null || end == null || stepDistance <= 0.0D) {
            return null;
        }
        // Honour the enclosing planning deadline too: several seeds each running a
        // full search would otherwise blow past the whole tour budget.
        RuntimeState rt = rt();
        if (rt.teleport.astarAttemptedThisPlan) {
            return null;
        }
        rt.teleport.astarAttemptedThisPlan = true;
        long deadlineNanos = System.nanoTime() + TELEPORT_GROUNDED_LEG_BUDGET_NANOS;
        if (planningBudget != null && planningBudget.deadlineNanos < deadlineNanos) {
            deadlineNanos = planningBudget.deadlineNanos;
        }
        if (System.nanoTime() >= deadlineNanos) {
            return null;
        }
        List<Vec3d> route = findTeleportRouteWithAStar(player, start, end, deadlineNanos);
        if (route == null || route.size() < 2) {
            return null;
        }
        List<Vec3d> result = new ArrayList<>();
        Vec3d anchor = start;
        for (int index = 1; index < route.size(); index++) {
            if (System.nanoTime() >= deadlineNanos) {
                return null;
            }
            Vec3d node = route.get(index);
            Vec3d next = index + 1 < route.size() ? route.get(index + 1) : null;
            if (next != null && anchor.distanceTo(next) <= stepDistance
                    && isTeleportSegmentClear(player, anchor, next)) {
                continue;
            }
            if (!isTeleportSegmentClear(player, anchor, node)) {
                return null;
            }
            result.add(node);
            if (result.size() > maximumPackets) {
                return null;
            }
            anchor = node;
        }
        return result.isEmpty() ? null : result;
    }

    private List<Vec3d> interpolateTeleportLeg(Vec3d start, Vec3d end, int packets) {
        List<Vec3d> result = new ArrayList<>();
        appendInterpolatedTeleportLeg(result, start, end, packets);
        return result;
    }

    private void appendInterpolatedTeleportLeg(List<Vec3d> result, Vec3d start, Vec3d end, int packets) {
        if (result == null || start == null || end == null || packets <= 0) {
            return;
        }
        for (int packet = 1; packet <= packets; packet++) {
            double progress = packet / (double) packets;
            addTeleportWaypoint(result, new Vec3d(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                    start.z + (end.z - start.z) * progress));
        }
    }

    private boolean isFastTeleportSegmentClear(EntityPlayerSP player, Vec3d start, Vec3d end,
            TeleportPlanningBudget planningBudget) {
        if (player == null || player.world == null || start == null || end == null || planningBudget == null) {
            return false;
        }
        if (!isFastTeleportPositionClear(player, start, planningBudget)
                || !isFastTeleportPositionClear(player, end, planningBudget)) {
            return false;
        }
        if (!isFastTeleportSegmentLoaded(player, start, end, planningBudget)) {
            return false;
        }
        double height = Math.max(1.0D, player.height);
        double halfWidth = Math.max(0.3D, player.width * 0.5D) - 1.0E-3D;
        double[][] rayOffsets = new double[][] {
                { 0.0D, 0.08D, 0.0D },
                { 0.0D, height * 0.5D, 0.0D },
                { 0.0D, height - 0.08D, 0.0D },
                { halfWidth, height * 0.5D, halfWidth },
                { halfWidth, height * 0.5D, -halfWidth },
                { -halfWidth, height * 0.5D, halfWidth },
                { -halfWidth, height * 0.5D, -halfWidth }
        };
        for (double[] offset : rayOffsets) {
            if (!planningBudget.consumeWorldQuery()) {
                return false;
            }
            RayTraceResult obstruction = player.world.rayTraceBlocks(
                    start.addVector(offset[0], offset[1], offset[2]),
                    end.addVector(offset[0], offset[1], offset[2]), false, true, false);
            if (obstruction != null && obstruction.typeOfHit == RayTraceResult.Type.BLOCK) {
                return false;
            }
        }
        int samples = Math.min(TELEPORT_ROUTE_COLLISION_PROBE_LIMIT,
                Math.max(1, (int) Math.ceil(start.distanceTo(end) / Math.max(1.0D, getTeleportStepDistance()))));
        for (int sample = 1; sample < samples; sample++) {
            if (!planningBudget.canContinue()) {
                return false;
            }
            double progress = sample / (double) samples;
            Vec3d point = new Vec3d(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                    start.z + (end.z - start.z) * progress);
            if (!isFastTeleportPositionClear(player, point, planningBudget)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFastTeleportSegmentLoaded(EntityPlayerSP player, Vec3d start, Vec3d end,
            TeleportPlanningBudget planningBudget) {
        if (player == null || player.world == null || start == null || end == null || planningBudget == null
                || !planningBudget.consumeWorldQuery()) {
            return false;
        }
        int probes = Math.max(1, (int) Math.ceil(start.distanceTo(end) / 8.0D));
        double halfWidth = Math.max(0.3D, player.width * 0.5D);
        double[][] offsets = new double[][] {
                { halfWidth, halfWidth }, { halfWidth, -halfWidth },
                { -halfWidth, halfWidth }, { -halfWidth, -halfWidth }
        };
        Set<Long> checkedChunks = new HashSet<>();
        for (int probe = 0; probe <= probes; probe++) {
            if (!planningBudget.canContinue()) {
                return false;
            }
            double progress = probe / (double) probes;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;
            for (double[] offset : offsets) {
                int chunkX = MathHelper.floor(x + offset[0]) >> 4;
                int chunkZ = MathHelper.floor(z + offset[1]) >> 4;
                long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
                if (!checkedChunks.add(chunkKey)) {
                    continue;
                }
                if (checkedChunks.size() > TELEPORT_ROUTE_LOADED_QUERY_LIMIT
                        || !player.world.isBlockLoaded(new BlockPos(chunkX << 4, y, chunkZ << 4), false)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isFastTeleportPositionClear(EntityPlayerSP player, Vec3d position,
            TeleportPlanningBudget planningBudget) {
        if (player == null || player.world == null || position == null || planningBudget == null
                || !planningBudget.consumeWorldQuery()) {
            return false;
        }
        double height = Math.max(1.0D, player.height);
        if (position.y < 0.0D || position.y + height > player.world.getActualHeight()) {
            return false;
        }
        AxisAlignedBB playerBox = getTeleportPlayerBox(player, position);
        if (playerBox.minX <= player.world.getWorldBorder().minX()
                || playerBox.maxX >= player.world.getWorldBorder().maxX()
                || playerBox.minZ <= player.world.getWorldBorder().minZ()
                || playerBox.maxZ >= player.world.getWorldBorder().maxZ()) {
            return false;
        }
        int minX = MathHelper.floor(playerBox.minX);
        int maxX = MathHelper.floor(playerBox.maxX - 1.0E-6D);
        int minY = MathHelper.floor(playerBox.minY);
        int maxY = MathHelper.floor(playerBox.maxY - 1.0E-6D);
        int minZ = MathHelper.floor(playerBox.minZ);
        int maxZ = MathHelper.floor(playerBox.maxZ - 1.0E-6D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (!player.world.isBlockLoaded(blockPos, false)) {
                        return false;
                    }
                    IBlockState state = player.world.getBlockState(blockPos);
                    AxisAlignedBB collisionBox = state.getCollisionBoundingBox(player.world, blockPos);
                    if (collisionBox != null && collisionBox != Block.NULL_AABB
                            && collisionBox.offset(blockPos).intersects(playerBox)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private double getDistanceSq(Vec3d position, double x, double y, double z) {
        if (position == null) {
            return Double.MAX_VALUE;
        }
        double dx = position.x - x;
        double dy = position.y - y;
        double dz = position.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static float getTeleportStepDistance() {
        return clampFiniteFloat(cfg().teleportStepDistance, DEFAULT_TELEPORT_STEP_DISTANCE,
                MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
    }

    public static int getTeleportActionsPerTick() {
        return MathHelper.clamp(cfg().teleportActionsPerTick, MIN_TELEPORT_ACTIONS_PER_TICK,
                MAX_TELEPORT_ACTIONS_PER_TICK);
    }

    /** 每动作位移的实际上限还受「一拍总量」约束：服务端在 i>5 时按 1 判，
     *  所以动作数 × 每动作位移必须 ≤ 10，否则多给的速度只会换来回拉。 */
    public static float getTeleportStepDistancePerAction() {
        float configured = clampFiniteFloat(cfg().teleportStepDistancePerAction,
                DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION, MIN_TELEPORT_STEP_DISTANCE_PER_ACTION,
                MAX_TELEPORT_STEP_DISTANCE_PER_ACTION);
        float budget = 10.0F / Math.max(1, getTeleportActionsPerTick());
        return Math.min(configured, budget);
    }

    private static float getTeleportMeleeRange() {
        // No 3-block cap: the operator owns this number. The server rejects
        // out-of-range attacks on its own, which is the real limit.
        return getTeleportStationAttackRadius();
    }

    public static float getTeleportStationAttackRadius() {
        return clampFiniteFloat(cfg().teleportStationAttackRadius, DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
    }

    private boolean shouldAttackPrimaryTargetOnly(EntityLivingBase crosshairLockedTarget) {
        return cfg().focusSingleTarget
                || crosshairLockedTarget != null
                || cfg().rotateOnlyOnAttack
                || isTargetSwitchSmoothingActive();
    }

    private boolean canAttackTargetBeforeRotation(EntityPlayerSP player, EntityLivingBase target) {
        return canAttackTargetBeforeRotation(player, target, null);
    }

    private boolean canAttackTargetBeforeRotation(EntityPlayerSP player, EntityLivingBase target,
            AreaHuntOptions areaOptions) {
        if (target == null || target.isDead || target.getHealth() <= 0.0F) {
            return false;
        }
        if (!isValidTarget(player, target, areaOptions)) {
            return false;
        }
        if (shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target)) {
            return false;
        }
        if (player.getDistanceSq(target) > cfg().attackRange * cfg().attackRange) {
            return false;
        }
        return true;
    }

    private boolean canAttackTarget(EntityPlayerSP player, EntityLivingBase target, boolean requireCrosshairHit) {
        return canAttackTarget(player, target, requireCrosshairHit, null);
    }

    private boolean canAttackTarget(EntityPlayerSP player, EntityLivingBase target, boolean requireCrosshairHit,
            AreaHuntOptions areaOptions) {
        if (!canAttackTargetBeforeRotation(player, target, areaOptions)) {
            return false;
        }
        float yawDiff = Math
                .abs(MathHelper.wrapDegrees(getDesiredAimRotation(player, target).getYaw() - player.rotationYaw));
        // During hunt chase the bot's yaw lags the moving target — don't block
        // the swing on a stale rotation check. The swing itself re-aims.
        if (shouldRotateToTarget() && yawDiff > 160.0F) {
            return false;
        }
        if (requireCrosshairHit && !isViewRayHittingAttackableTarget(player, target, areaOptions)) {
            return false;
        }
        return true;
    }

    private boolean shouldRequireCrosshairHitForAttack(boolean teleportAttack) {
        if (teleportAttack) {
            return false;
        }
        if (isMouseClickAttackMode()) {
            return true;
        }
        return cfg().onlyAttackWhenLookingAtTarget && shouldRotateToTarget();
    }

    private boolean isViewRayHittingAttackableTarget(EntityPlayerSP player, EntityLivingBase target) {
        return isViewRayHittingAttackableTarget(player, target, null);
    }

    private boolean isViewRayHittingAttackableTarget(EntityPlayerSP player, EntityLivingBase target,
            AreaHuntOptions areaOptions) {
        return player != null
                && target != null
                && canAttackTargetBeforeRotation(player, target, areaOptions)
                && getCrosshairHitDistanceSq(player, target) >= 0.0D;
    }

    private boolean shouldUseTeleportAttack(EntityPlayerSP player, EntityLivingBase target) {
        return isTeleportAttackMode() && player != null && target != null && !isTeleportAttackRecoveryActive();
    }

    public static final class HuntScoreDebugEntry {
        public final String name;
        public final int entityId;
        public final double totalScore;
        public final double radiusScore;
        public final double playerDistanceScore;
        public final double playerPlaneScore;
        public final double targetHeightScore;
        public final double attackRangeScore;
        public final double visibilityScore;
        public final double opennessScore;
        public final boolean hasDestination;
        public final boolean visible;
        public final double destinationX;
        public final double destinationY;
        public final double destinationZ;

        private HuntScoreDebugEntry(EntityLivingBase target, HuntScoreBreakdown breakdown, double[] destination) {
            this.name = getFilterableEntityName(target);
            this.entityId = target.getEntityId();
            this.totalScore = breakdown.totalScore;
            this.radiusScore = breakdown.radiusScore;
            this.playerDistanceScore = breakdown.playerDistanceScore;
            this.playerPlaneScore = breakdown.playerPlaneScore;
            this.targetHeightScore = breakdown.targetHeightScore;
            this.attackRangeScore = breakdown.attackRangeScore;
            this.visibilityScore = breakdown.visibilityScore;
            this.opennessScore = breakdown.opennessScore;
            this.hasDestination = destination != null;
            this.visible = breakdown.visible;
            this.destinationX = destination == null ? 0.0D : destination[0];
            this.destinationY = destination == null ? 0.0D : destination[1];
            this.destinationZ = destination == null ? 0.0D : destination[2];
        }
    }

    private static final class HuntScoreBreakdown {
        private final double totalScore;
        private final double radiusScore;
        private final double playerDistanceScore;
        private final double playerPlaneScore;
        private final double targetHeightScore;
        private final double attackRangeScore;
        private final double visibilityScore;
        private final double opennessScore;
        private final boolean visible;

        private HuntScoreBreakdown(double radiusScore, double playerDistanceScore, double playerPlaneScore,
                double targetHeightScore, double attackRangeScore, double visibilityScore, double opennessScore,
                boolean visible) {
            this.radiusScore = radiusScore;
            this.playerDistanceScore = playerDistanceScore;
            this.playerPlaneScore = playerPlaneScore;
            this.targetHeightScore = targetHeightScore;
            this.attackRangeScore = attackRangeScore;
            this.visibilityScore = visibilityScore;
            this.opennessScore = opennessScore;
            this.visible = visible;
            this.totalScore = radiusScore + playerDistanceScore + playerPlaneScore + targetHeightScore
                    + attackRangeScore + visibilityScore + opennessScore;
        }
    }

    private static final class HuntStandCandidate {
        private final BlockPos position;
        private final double terrainScore;

        private HuntStandCandidate(BlockPos position, double terrainScore) {
            this.position = position;
            this.terrainScore = terrainScore;
        }
    }

    private static final class TargetCandidate {
        private final EntityLivingBase entity;
        private final double distanceSq;
        private final int whitelistPriority;
        private final int currentTargetPriority;
        private final float yawDeltaAbs;

        private TargetCandidate(EntityLivingBase entity, double distanceSq, int whitelistPriority,
                int currentTargetPriority, float yawDeltaAbs) {
            this.entity = entity;
            this.distanceSq = distanceSq;
            this.whitelistPriority = whitelistPriority;
            this.currentTargetPriority = currentTargetPriority;
            this.yawDeltaAbs = yawDeltaAbs;
        }
    }

    public boolean isNoDamageExcludedTarget(Entity target) {
        return isNoDamageExclusionEnabled()
                && target != null
                && rt().noDamageExcludedEntityIds.contains(target.getEntityId());
    }

    private boolean isHuntUnreachableExcludedTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return false;
        }
        HuntUnreachableTracker tracker = rt().huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            return false;
        }
        if (hasHuntTargetRelocated(tracker, target)) {
            tracker.resetFailures(target);
            if (tracker.failedGoalKeys.isEmpty()) {
                rt().huntUnreachableTrackers.remove(target.getEntityId());
            }
            return false;
        }
        // Same frozen-clock hazard as shouldRunHuntMovement: compare against
        // the per-owner logical tick, not the session entity's ticksExisted.
        return tracker.excludeUntilTick > rt().huntLogicalTick;
    }

    private void tickHuntUnreachableTrackers(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null || player.world == null || rt.huntUnreachableTrackers.isEmpty()) {
            return;
        }
        List<Integer> trackedIds = new ArrayList<>(rt.huntUnreachableTrackers.keySet());
        for (Integer entityId : trackedIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            HuntUnreachableTracker tracker = rt.huntUnreachableTrackers.get(entityId);
            if (tracker == null) {
                continue;
            }
            if (!(entity instanceof EntityLivingBase)) {
                rt.huntUnreachableTrackers.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                rt.huntUnreachableTrackers.remove(entityId);
                continue;
            }
            if (hasHuntTargetRelocated(tracker, living)) {
                tracker.resetFailures(living);
                if (tracker.failedGoalKeys.isEmpty()) {
                    rt.huntUnreachableTrackers.remove(entityId);
                }
            }
        }
        pruneHuntUnreachableTrackingSize();
    }

    private void clearHuntUnreachableTracking() {
        rt().huntUnreachableTrackers.clear();
    }

    private boolean hasHuntTargetRelocated(HuntUnreachableTracker tracker, EntityLivingBase target) {
        if (tracker == null || target == null) {
            return false;
        }
        double dx = target.posX - tracker.lastTargetX;
        double dz = target.posZ - tracker.lastTargetZ;
        return dx * dx + dz * dz >= HUNT_UNREACHABLE_TARGET_RESET_DISTANCE_SQ;
    }

    private boolean isBlockedHuntNavigationDestination(EntityLivingBase target, double goalX, double goalY,
            double goalZ) {
        if (target == null) {
            return false;
        }
        HuntUnreachableTracker tracker = rt().huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null || tracker.failedGoalKeys.isEmpty()) {
            return false;
        }
        return tracker.failedGoalKeys.contains(toHuntGoalKey(goalX, goalY, goalZ));
    }

    private long toHuntGoalKey(double goalX, double goalY, double goalZ) {
        return new BlockPos(MathHelper.floor(goalX), MathHelper.floor(goalY), MathHelper.floor(goalZ)).toLong();
    }

    private void clearCurrentHuntUnreachableFailureCount() {
        RuntimeState rt = rt();
        if (!rt.huntNavigationActive || rt.lastHuntTargetEntityId == Integer.MIN_VALUE) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || ctxWorld() == null) {
            return;
        }
        Entity entity = ctxWorld().getEntityByID(rt.lastHuntTargetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        HuntUnreachableTracker tracker = rt.huntUnreachableTrackers.get(rt.lastHuntTargetEntityId);
        if (tracker == null) {
            return;
        }
        tracker.failedGoalCount = 0;
        tracker.excludeUntilTick = 0;
        tracker.refreshTargetPosition((EntityLivingBase) entity);
    }

    private void handleHuntPathCalculationFailed() {
        RuntimeState rt = rt();
        if (!rt.huntNavigationActive || rt.lastHuntTargetEntityId == Integer.MIN_VALUE
                || Double.isNaN(rt.lastHuntGoalX) || Double.isNaN(rt.lastHuntGoalY) || Double.isNaN(rt.lastHuntGoalZ)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : ctxPlayer();
        if (player == null || ctxWorld() == null) {
            return;
        }
        Entity entity = ctxWorld().getEntityByID(rt.lastHuntTargetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            stopHuntNavigation();
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        HuntUnreachableTracker tracker = rt.huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            tracker = new HuntUnreachableTracker(target);
            rt.huntUnreachableTrackers.put(target.getEntityId(), tracker);
        } else if (hasHuntTargetRelocated(tracker, target)) {
            tracker.resetFailures(target);
        } else {
            tracker.refreshTargetPosition(target);
        }

        tracker.failedGoalCount++;
        tracker.failedGoalKeys.add(toHuntGoalKey(rt.lastHuntGoalX, rt.lastHuntGoalY, rt.lastHuntGoalZ));
        while (tracker.failedGoalKeys.size() > HUNT_UNREACHABLE_MAX_FAILED_GOALS_PER_TARGET) {
            Long first = tracker.failedGoalKeys.iterator().next();
            tracker.failedGoalKeys.remove(first);
        }
        pruneHuntUnreachableTrackingSize();

        boolean temporarilyExcluded = tracker.failedGoalCount >= HUNT_UNREACHABLE_FAILS_BEFORE_TEMP_EXCLUDE;
        if (temporarilyExcluded) {
            tracker.excludeUntilTick = rt().huntLogicalTick + HUNT_UNREACHABLE_TEMP_EXCLUDE_TICKS;
            tracker.failedGoalCount = 0;
        }

        String targetName = getFilterableEntityName(target);
        mythosScriptMod.LOGGER.info(
                "杀戮光环追击寻路不可达: id={}, name={}, goal=({}, {}, {}), failedGoals={}, tempExcluded={}",
                target.getEntityId(),
                targetName,
                MathHelper.floor(rt.lastHuntGoalX),
                MathHelper.floor(rt.lastHuntGoalY),
                MathHelper.floor(rt.lastHuntGoalZ),
                tracker.failedGoalKeys.size(),
                temporarilyExcluded);

        stopHuntNavigation();
        if (temporarilyExcluded && rt().currentTargetEntityId == target.getEntityId()) {
            rt().currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            this.attackSequenceExecutor.stop();
        }
    }

    private void pruneHuntUnreachableTrackingSize() {
        Map<Integer, HuntUnreachableTracker> trackers = rt().huntUnreachableTrackers;
        while (trackers.size() > HUNT_UNREACHABLE_MAX_TRACKED_TARGETS) {
            Integer first = trackers.keySet().iterator().next();
            trackers.remove(first);
        }
    }

    public static boolean isNoDamageExclusionEnabled() {
        return getNoDamageAttackLimit() > 0;
    }

    public static int getNoDamageAttackLimit() {
        return MathHelper.clamp(cfg().noDamageAttackLimit, 0, MAX_NO_DAMAGE_ATTACK_LIMIT);
    }

    private void tickNoDamageAttackTrackers(EntityPlayerSP player) {
        if (!isNoDamageExclusionEnabled() || player == null || player.world == null) {
            clearNoDamageAttackTracking();
            return;
        }

        int limit = getNoDamageAttackLimit();
        List<Integer> trackerIds = new ArrayList<>(rt().noDamageAttackTrackers.keySet());
        for (Integer entityId : trackerIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                rt().noDamageAttackTrackers.remove(entityId);
                rt().noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                rt().noDamageAttackTrackers.remove(entityId);
                rt().noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            NoDamageAttackTracker tracker = rt().noDamageAttackTrackers.get(entityId);
            if (tracker != null) {
                updateNoDamageAttackTracker(entityId, living, tracker, limit);
            }
        }

        pruneNoDamageExcludedTargets(player);
    }

    private void updateNoDamageAttackTracker(int entityId, EntityLivingBase target,
            NoDamageAttackTracker tracker, int limit) {
        float currentHealth = target.getHealth();
        if (currentHealth + NO_DAMAGE_HEALTH_EPSILON < tracker.baselineHealth) {
            Minecraft mc = Minecraft.getMinecraft();
            teleportDebug(mc == null ? null : ctxPlayer(), "NO_DAMAGE_RESET",
                    "reason=HEALTH_DECREASED,id=%d,baseline=%.3f,current=%.3f,pending=%d,confirmed=%d",
                    entityId, tracker.baselineHealth, currentHealth, tracker.pendingAttempts,
                    tracker.confirmedNoDamageAttempts);
            tracker.baselineHealth = currentHealth;
            tracker.pendingAttempts = 0;
            tracker.observationTicks = 0;
            tracker.confirmedNoDamageAttempts = 0;
            return;
        }

        if (tracker.pendingAttempts <= 0) {
            tracker.baselineHealth = currentHealth;
            return;
        }
        if (tracker.observationTicks > 0) {
            tracker.observationTicks--;
            return;
        }

        tracker.confirmedNoDamageAttempts += tracker.pendingAttempts;
        Minecraft mc = Minecraft.getMinecraft();
        teleportDebug(mc == null ? null : ctxPlayer(), "NO_DAMAGE_CONFIRMED",
                "id=%d,baseline=%.3f,current=%.3f,added=%d,confirmedNow=%d,limit=%d,target={%s}",
                entityId, tracker.baselineHealth, currentHealth, tracker.pendingAttempts,
                tracker.confirmedNoDamageAttempts, limit,
                formatTeleportTarget(mc == null ? null : ctxPlayer(), target));
        tracker.pendingAttempts = 0;
        tracker.baselineHealth = currentHealth;
        if (tracker.confirmedNoDamageAttempts >= limit) {
            excludeNoDamageTarget(entityId, target);
        }
    }

    private void recordNoDamageAttackAttempt(EntityLivingBase target) {
        if (!isNoDamageExclusionEnabled() || target == null || target.getHealth() <= 0.0F) {
            return;
        }
        // i-frame skip is now handled in attackTargets BEFORE attackEntity —
        // post-attack hurtResistantTime is always 20 (set by the hit itself),
        // so checking it here would silently disable the whole tracker.
        int entityId = target.getEntityId();
        if (rt().noDamageExcludedEntityIds.contains(entityId)) {
            return;
        }

        NoDamageAttackTracker tracker = rt().noDamageAttackTrackers.get(entityId);
        float currentHealth = target.getHealth();
        if (tracker == null) {
            tracker = new NoDamageAttackTracker(currentHealth);
            rt().noDamageAttackTrackers.put(entityId, tracker);
        } else if (currentHealth + NO_DAMAGE_HEALTH_EPSILON < tracker.baselineHealth) {
            tracker.baselineHealth = currentHealth;
            tracker.pendingAttempts = 0;
            tracker.observationTicks = 0;
            tracker.confirmedNoDamageAttempts = 0;
        }
        if (tracker.pendingAttempts <= 0) {
            tracker.baselineHealth = currentHealth;
            tracker.observationTicks = NO_DAMAGE_OBSERVATION_DELAY_TICKS;
        }
        tracker.pendingAttempts++;
        Minecraft mc = Minecraft.getMinecraft();
        teleportDebug(mc == null ? null : ctxPlayer(), "NO_DAMAGE_ATTEMPT",
                "id=%d,baseline=%.3f,current=%.3f,pending=%d,confirmed=%d,observationTicks=%d,limit=%d",
                entityId, tracker.baselineHealth, currentHealth, tracker.pendingAttempts,
                tracker.confirmedNoDamageAttempts, tracker.observationTicks, getNoDamageAttackLimit());
        pruneNoDamageTrackingSize();
    }

    private void excludeNoDamageTarget(int entityId, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (!rt().noDamageExcludedEntityIds.add(entityId)) {
            return;
        }
        rt().noDamageAttackTrackers.remove(entityId);
        pruneNoDamageTrackingSize();
        if (rt().currentTargetEntityId == entityId) {
            rt().currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            stopHuntNavigation();
            this.attackSequenceExecutor.stop();
        }
        String targetName = target == null ? "" : getFilterableEntityName(target);
        Minecraft mc = Minecraft.getMinecraft();
        teleportDebug(mc == null ? null : ctxPlayer(), "TARGET_NO_DAMAGE_EXCLUDED",
                "id=%d,name=%s,limit=%d,target={%s},cacheContains=%s,historyTick=%d",
                entityId, targetName, getNoDamageAttackLimit(),
                formatTeleportTarget(mc == null ? null : ctxPlayer(), target),
                rt().teleport.teleportTargetCandidateEntityIds.contains(entityId),
                rt().teleport.teleportLastAttackTicks.getOrDefault(entityId, Integer.MIN_VALUE));
        mythosScriptMod.LOGGER.info("杀戮光环无掉血排除目标: id={}, name={}, limit={}",
                entityId, targetName, getNoDamageAttackLimit());
    }

    private void pruneNoDamageExcludedTargets(EntityPlayerSP player) {
        if (player == null || player.world == null || rt().noDamageExcludedEntityIds.isEmpty()) {
            return;
        }
        List<Integer> excludedIds = new ArrayList<>(rt().noDamageExcludedEntityIds);
        for (Integer entityId : excludedIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                rt().noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                rt().noDamageExcludedEntityIds.remove(entityId);
            }
        }
    }

    private void pruneNoDamageTrackingSize() {
        while (rt().noDamageAttackTrackers.size() > NO_DAMAGE_MAX_TRACKED_TARGETS) {
            Integer first = rt().noDamageAttackTrackers.keySet().iterator().next();
            rt().noDamageAttackTrackers.remove(first);
        }
        while (rt().noDamageExcludedEntityIds.size() > NO_DAMAGE_MAX_EXCLUDED_TARGETS) {
            Integer first = rt().noDamageExcludedEntityIds.iterator().next();
            rt().noDamageExcludedEntityIds.remove(first);
        }
    }

    private void clearNoDamageAttackTracking() {
        rt().noDamageAttackTrackers.clear();
        rt().noDamageExcludedEntityIds.clear();
    }

    private boolean performTeleportAttack(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (rt.teleport.timing.isRouteRejected()) return false;
        if (player == null || target == null || player.connection == null || isTeleportAttackRecoveryActive()) {
            return false;
        }
        int remainingPacketBudget = TELEPORT_ROUTE_MAX_WAYPOINTS;
        TeleportTourPlan plan = buildTeleportTourPlan(player, Collections.singletonList(target), null,
                1, remainingPacketBudget);
        if (plan == null) {
            return false;
        }
        return executeTeleportTourPlan(player, plan, null) > 0;
    }

    private void registerTeleportRoute(TeleportAttackPlan recoveryPlan, TeleportAttackPlan owner,
            Vec3d start, List<Vec3d> waypoints, Vec3d end) {
        if (recoveryPlan == null || owner == null || start == null || end == null) {
            return;
        }
        List<Vec3d> route = new ArrayList<>();
        route.add(start);
        if (waypoints != null) {
            for (Vec3d waypoint : waypoints) {
                if (waypoint != null) {
                    route.add(waypoint);
                }
            }
        }
        route.add(end);
        registerTeleportRoute(recoveryPlan, owner, route);
    }

    private void registerTeleportRoute(TeleportAttackPlan recoveryPlan, TeleportAttackPlan owner,
            List<Vec3d> route) {
        if (recoveryPlan == null || owner == null || route == null || route.size() < 2) {
            return;
        }
        recoveryPlan.relatedRoutes.add(new TeleportRoute(owner, route));
    }

    private boolean performMouseClickAttack(Minecraft mc) {
        if (mc == null || mc.currentScreen != null) {
            return false;
        }

        int screenWidth = Math.max(1, mc.displayWidth);
        int screenHeight = Math.max(1, mc.displayHeight);
        ModUtils.simulateMouseClick(screenWidth / 2, screenHeight / 2, true, screenWidth, screenHeight);
        return true;
    }

    private TeleportAttackPlan buildTeleportAttackPlan(EntityPlayerSP player, EntityLivingBase target,
            boolean preserveCurrentLook, TeleportOrigin origin) {
        if (player == null || target == null || origin == null) {
            return null;
        }

        TeleportAssaultCandidate assaultCandidate = findBestTeleportAssaultCandidate(player, target);
        if (assaultCandidate == null) {
            return null;
        }

        List<Vec3d> outboundWaypoints = buildTeleportPathWaypoints(player,
                origin.x, origin.y, origin.z,
                assaultCandidate.x, assaultCandidate.y, assaultCandidate.z);
        if (outboundWaypoints == null && assaultCandidate.usedSafeStandPos) {
            assaultCandidate = findBestAirborneTeleportAssaultCandidate(player, target);
            if (assaultCandidate != null) {
                outboundWaypoints = buildTeleportPathWaypoints(player,
                        origin.x, origin.y, origin.z,
                        assaultCandidate.x, assaultCandidate.y, assaultCandidate.z);
            }
        }
        if (outboundWaypoints == null) {
            return null;
        }
        List<Vec3d> returnWaypoints = new ArrayList<>(outboundWaypoints);
        Collections.reverse(returnWaypoints);

        Rotation attackRotation = shouldRotateToTarget() && !preserveCurrentLook
                ? getAdvancedAimRotationFromPosition(player, target, assaultCandidate.x, assaultCandidate.y,
                        assaultCandidate.z, new Rotation(origin.yaw, origin.pitch), true)
                : new Rotation(origin.yaw, origin.pitch);
        attackRotation = applyMouseGcdCompensation(player, attackRotation, Float.MAX_VALUE, Float.MAX_VALUE);

        return new TeleportAttackPlan(origin, assaultCandidate, outboundWaypoints, returnWaypoints,
                attackRotation.getYaw(), attackRotation.getPitch());
    }

    private TeleportAssaultCandidate findBestTeleportAssaultCandidate(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return null;
        }

        double preferredRadius = Math.max(1.8D, TELEPORT_ATTACK_REACH + target.width * 0.5D);
        double minRadius = Math.max(0.9D, preferredRadius - TELEPORT_ATTACK_MAX_RADIUS_ADJUST);
        double maxRadius = Math.max(preferredRadius, preferredRadius + TELEPORT_ATTACK_MAX_RADIUS_ADJUST);
        double preferredAngle = Math.atan2(player.posZ - target.posZ, player.posX - target.posX);
        TeleportAssaultCandidate best = null;

        for (int angleStep = 0; angleStep <= TELEPORT_ATTACK_SAFE_ANGLE_STEPS; angleStep++) {
            if (angleStep == 0) {
                best = findTeleportAssaultCandidateForAngle(player, target, preferredAngle, preferredRadius, minRadius,
                        maxRadius, best);
                continue;
            }

            double angleOffset = angleStep * TELEPORT_ATTACK_SAFE_ANGLE_STEP_RADIANS;
            best = findTeleportAssaultCandidateForAngle(player, target, wrapOrbitAngle(preferredAngle + angleOffset),
                    preferredRadius, minRadius, maxRadius, best);
            best = findTeleportAssaultCandidateForAngle(player, target, wrapOrbitAngle(preferredAngle - angleOffset),
                    preferredRadius, minRadius, maxRadius, best);
        }

        if (best != null) {
            return best;
        }
        return findBestAirborneTeleportAssaultCandidate(player, target);
    }

    private TeleportAssaultCandidate findTeleportAssaultCandidateForAngle(EntityPlayerSP player,
            EntityLivingBase target,
            double angle, double preferredRadius, double minRadius, double maxRadius,
            TeleportAssaultCandidate currentBest) {
        int radiusSteps = Math.max(1,
                (int) Math.ceil((maxRadius - minRadius) / Math.max(0.1D, TELEPORT_ATTACK_SAFE_RADIUS_STEP)));
        TeleportAssaultCandidate best = currentBest;

        for (int radiusStep = 0; radiusStep <= radiusSteps; radiusStep++) {
            if (radiusStep == 0) {
                best = evaluateTeleportAssaultCandidate(player, target, angle, preferredRadius, preferredRadius, best);
                continue;
            }

            double largerRadius = Math.min(maxRadius, preferredRadius + radiusStep * TELEPORT_ATTACK_SAFE_RADIUS_STEP);
            best = evaluateTeleportAssaultCandidate(player, target, angle, largerRadius, preferredRadius, best);

            double smallerRadius = Math.max(minRadius, preferredRadius - radiusStep * TELEPORT_ATTACK_SAFE_RADIUS_STEP);
            if (smallerRadius < largerRadius - 1.0E-4D) {
                best = evaluateTeleportAssaultCandidate(player, target, angle, smallerRadius, preferredRadius, best);
            }
        }

        return best;
    }

    private TeleportAssaultCandidate evaluateTeleportAssaultCandidate(EntityPlayerSP player, EntityLivingBase target,
            double preferredAngle, double radius, double preferredRadius, TeleportAssaultCandidate currentBest) {
        double desiredX = target.posX + Math.cos(preferredAngle) * radius;
        double desiredZ = target.posZ + Math.sin(preferredAngle) * radius;
        double[] clippedDesired = clipHuntDestinationXZ(target.posX, target.posZ, desiredX, desiredZ);
        double[] safeAssaultPos = findSafeHuntNavigationDestination(player, target, clippedDesired[0], target.posY,
                clippedDesired[1]);
        if (safeAssaultPos == null) {
            return currentBest;
        }

        BlockPos standPos = new BlockPos(safeAssaultPos[0], safeAssaultPos[1], safeAssaultPos[2]);
        if (!hasHuntLineOfSightFromStandPos(standPos, target)) {
            return currentBest;
        }

        double attackDx = target.posX - safeAssaultPos[0];
        double attackDy = target.posY + target.getEyeHeight() * 0.85D - (safeAssaultPos[1] + player.getEyeHeight());
        double attackDz = target.posZ - safeAssaultPos[2];
        double attackDistance = Math.sqrt(attackDx * attackDx + attackDy * attackDy + attackDz * attackDz);
        double maxAttackDistance = Math.max(2.85D, TELEPORT_ATTACK_REACH + target.width * 0.8D + 0.55D);
        if (attackDistance > maxAttackDistance) {
            return currentBest;
        }

        double actualAngle = Math.atan2(safeAssaultPos[2] - target.posZ, safeAssaultPos[0] - target.posX);
        double desiredPenalty = centerDistSq(safeAssaultPos[0], safeAssaultPos[2], clippedDesired[0], clippedDesired[1])
                * 3.0D;
        double anglePenalty = Math.abs(wrapOrbitAngle(actualAngle - preferredAngle)) * 6.0D;
        double radiusPenalty = Math.abs(Math.sqrt((safeAssaultPos[0] - target.posX) * (safeAssaultPos[0] - target.posX)
                + (safeAssaultPos[2] - target.posZ) * (safeAssaultPos[2] - target.posZ)) - preferredRadius) * 4.5D;
        double heightPenalty = Math.abs(safeAssaultPos[1] - player.posY) * 0.6D;
        double approachPenalty = player.getDistanceSq(safeAssaultPos[0], safeAssaultPos[1], safeAssaultPos[2]) * 0.04D;
        double score = desiredPenalty + anglePenalty + radiusPenalty + heightPenalty + approachPenalty;

        if (currentBest == null || score < currentBest.score) {
            return new TeleportAssaultCandidate(safeAssaultPos[0], safeAssaultPos[1], safeAssaultPos[2], true, score);
        }
        return currentBest;
    }

    private TeleportAssaultCandidate findBestAirborneTeleportAssaultCandidate(EntityPlayerSP player,
            EntityLivingBase target) {
        if (player == null || player.world == null || target == null) {
            return null;
        }

        double preferredRadius = Math.max(1.8D, TELEPORT_ATTACK_REACH + target.width * 0.5D);
        double preferredAngle = Math.atan2(player.posZ - target.posZ, player.posX - target.posX);
        double maxAttackDistance = Math.max(2.85D, TELEPORT_ATTACK_REACH + target.width * 0.8D + 0.55D);
        double maxFeetY = player.world.getActualHeight() - Math.max(1.0D, player.height) - 0.05D;
        double[] radiusOffsets = new double[] { 0.0D, -0.4D, 0.4D, -0.8D, 0.8D };
        double[] heightOffsets = new double[] { 0.0D, 0.75D, -0.75D, 1.5D, -1.5D, 2.25D };
        TeleportAssaultCandidate best = null;

        for (int angleStep = 0; angleStep <= TELEPORT_ATTACK_SAFE_ANGLE_STEPS; angleStep++) {
            int angleVariants = angleStep == 0 ? 1 : 2;
            for (int variant = 0; variant < angleVariants; variant++) {
                double signedStep = variant == 0 ? angleStep : -angleStep;
                double angle = wrapOrbitAngle(preferredAngle
                        + signedStep * TELEPORT_ATTACK_SAFE_ANGLE_STEP_RADIANS);
                for (double radiusOffset : radiusOffsets) {
                    double radius = Math.max(1.2D, preferredRadius + radiusOffset);
                    double desiredX = target.posX + Math.cos(angle) * radius;
                    double desiredZ = target.posZ + Math.sin(angle) * radius;
                    double[] clipped = clipHuntDestinationXZ(target.posX, target.posZ, desiredX, desiredZ);
                    for (double heightOffset : heightOffsets) {
                        double candidateY = MathHelper.clamp(target.posY + heightOffset, 0.05D, maxFeetY);
                        Vec3d candidate = new Vec3d(clipped[0], candidateY, clipped[1]);
                        if (!isTeleportPositionClear(player, candidate)
                                || !hasTeleportLineOfSightFromPosition(player, candidate, target)) {
                            continue;
                        }

                        double attackDx = target.posX - candidate.x;
                        double attackDy = target.posY + target.getEyeHeight() * 0.85D
                                - (candidate.y + player.getEyeHeight());
                        double attackDz = target.posZ - candidate.z;
                        double attackDistance = Math.sqrt(attackDx * attackDx + attackDy * attackDy
                                + attackDz * attackDz);
                        if (attackDistance > maxAttackDistance) {
                            continue;
                        }

                        double anglePenalty = Math.abs(wrapOrbitAngle(angle - preferredAngle)) * 6.0D;
                        double radiusPenalty = Math.abs(radius - preferredRadius) * 4.5D;
                        double heightPenalty = Math.abs(candidate.y - target.posY) * 1.25D;
                        double approachPenalty = player.getDistanceSq(candidate.x, candidate.y, candidate.z) * 0.04D;
                        double score = anglePenalty + radiusPenalty + heightPenalty + approachPenalty + 4.0D;
                        if (best == null || score < best.score) {
                            best = new TeleportAssaultCandidate(candidate.x, candidate.y, candidate.z, false, score);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean hasTeleportLineOfSightFromPosition(EntityPlayerSP player, Vec3d feetPosition,
            EntityLivingBase target) {
        if (player == null || player.world == null || feetPosition == null || target == null) {
            return false;
        }

        Vec3d eyePosition = feetPosition.addVector(0.0D, player.getEyeHeight(), 0.0D);
        Vec3d targetEyePosition = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        RayTraceResult ray = player.world.rayTraceBlocks(eyePosition, targetEyePosition, false, true, false);
        return ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    private List<Vec3d> buildTeleportPathWaypoints(EntityPlayerSP player, double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ) {
        if (player == null || player.world == null) {
            return null;
        }

        Vec3d start = new Vec3d(fromX, fromY, fromZ);
        Vec3d end = new Vec3d(toX, toY, toZ);
        if (!isTeleportPositionClear(player, start) || !isTeleportPositionClear(player, end)) {
            return null;
        }

        List<Vec3d> route = findTeleportRoute(player, start, end);
        if (route == null || route.size() < 2) {
            return null;
        }
        forceTeleportRouteEndpoints(route, start, end);

        List<Vec3d> waypoints = new ArrayList<>();
        for (int routeIndex = 1; routeIndex < route.size(); routeIndex++) {
            Vec3d segmentStart = route.get(routeIndex - 1);
            Vec3d segmentEnd = route.get(routeIndex);
            double distance = segmentStart.distanceTo(segmentEnd);
            int steps = Math.max(1, (int) Math.ceil(distance / Math.min(getTeleportStepDistancePerAction(), getTeleportStepDistance())));
            boolean finalSegment = routeIndex == route.size() - 1;
            for (int step = 1; step <= steps; step++) {
                if (finalSegment && step == steps) {
                    continue;
                }
                double progress = step / (double) steps;
                addTeleportWaypoint(waypoints, new Vec3d(
                        segmentStart.x + (segmentEnd.x - segmentStart.x) * progress,
                        segmentStart.y + (segmentEnd.y - segmentStart.y) * progress,
                        segmentStart.z + (segmentEnd.z - segmentStart.z) * progress));
            }
        }
        return isTeleportPacketPathSafe(player, start, waypoints, end) ? waypoints : null;
    }

    private void forceTeleportRouteEndpoints(List<Vec3d> route, Vec3d start, Vec3d end) {
        if (route == null || start == null || end == null) {
            return;
        }
        if (route.isEmpty()) {
            route.add(start);
            route.add(end);
            return;
        }
        route.set(0, start);
        if (route.size() == 1) {
            route.add(end);
        } else {
            route.set(route.size() - 1, end);
        }
    }

    private boolean isTeleportPacketPathSafe(EntityPlayerSP player, Vec3d start, List<Vec3d> waypoints, Vec3d end) {
        Vec3d previous = start;
        if (waypoints != null) {
            for (Vec3d waypoint : waypoints) {
                if (waypoint == null || previous.distanceTo(waypoint) > getTeleportStepDistance() + 1.0E-4D
                        || !isTeleportSegmentClear(player, previous, waypoint)) {
                    return false;
                }
                previous = waypoint;
            }
        }
        return previous.distanceTo(end) <= getTeleportStepDistance() + 1.0E-4D
                && isTeleportSegmentClear(player, previous, end);
    }

    private List<Vec3d> findTeleportRoute(EntityPlayerSP player, Vec3d start, Vec3d end) {
        if (isTeleportSegmentClear(player, start, end)) {
            List<Vec3d> directRoute = new ArrayList<>(2);
            directRoute.add(start);
            directRoute.add(end);
            return directRoute;
        }

        long deadlineNanos = System.nanoTime() + TELEPORT_ATTACK_PATH_TIME_BUDGET_NANOS;
        List<Vec3d> elevatedRoute = findElevatedTeleportRoute(player, start, end, deadlineNanos);
        if (elevatedRoute != null) {
            return elevatedRoute;
        }
        if (System.nanoTime() >= deadlineNanos) {
            return null;
        }

        List<Vec3d> searchedRoute = findTeleportRouteWithAStar(player, start, end, deadlineNanos);
        return searchedRoute == null ? null : simplifyTeleportRoute(player, searchedRoute, deadlineNanos);
    }

    private List<Vec3d> findElevatedTeleportRoute(EntityPlayerSP player, Vec3d start, Vec3d end,
            long deadlineNanos) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double directionX = horizontalDistance <= 1.0E-4D ? 1.0D : dx / horizontalDistance;
        double directionZ = horizontalDistance <= 1.0E-4D ? 0.0D : dz / horizontalDistance;
        double perpendicularX = -directionZ;
        double perpendicularZ = directionX;
        double[][] offsets = new double[][] {
                { 0.0D, 0.0D },
                { perpendicularX * 2.5D, perpendicularZ * 2.5D },
                { -perpendicularX * 2.5D, -perpendicularZ * 2.5D },
                { perpendicularX * 5.0D, perpendicularZ * 5.0D },
                { -perpendicularX * 5.0D, -perpendicularZ * 5.0D },
                { directionX * 2.5D, directionZ * 2.5D },
                { -directionX * 2.5D, -directionZ * 2.5D }
        };
        int verticalSearch = MathHelper.clamp((int) Math.ceil(horizontalDistance * 0.6D) + 16,
                TELEPORT_ATTACK_PATH_MIN_VERTICAL_SEARCH, TELEPORT_ATTACK_PATH_MAX_VERTICAL_SEARCH);
        double maxFeetY = player.world.getActualHeight() - Math.max(1.0D, player.height) - 0.05D;
        double baseY = Math.max(start.y, end.y);
        int[] rises = new int[] { 2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128 };

        for (int rise : rises) {
            if (System.nanoTime() >= deadlineNanos) {
                return null;
            }
            if (rise > verticalSearch) {
                break;
            }
            double cruiseY = Math.min(maxFeetY, baseY + rise);
            if (cruiseY <= baseY + 0.5D) {
                continue;
            }
            for (double[] offset : offsets) {
                if (System.nanoTime() >= deadlineNanos) {
                    return null;
                }
                Vec3d startAir = new Vec3d(start.x + offset[0], cruiseY, start.z + offset[1]);
                Vec3d endAir = new Vec3d(end.x + offset[0], cruiseY, end.z + offset[1]);
                if (!isTeleportSegmentClear(player, start, startAir)
                        || !isTeleportSegmentClear(player, startAir, endAir)
                        || !isTeleportSegmentClear(player, endAir, end)) {
                    continue;
                }
                List<Vec3d> route = new ArrayList<>(4);
                route.add(start);
                addTeleportRoutePoint(route, startAir);
                addTeleportRoutePoint(route, endAir);
                addTeleportRoutePoint(route, end);
                return route;
            }
        }
        return null;
    }

    private List<Vec3d> findTeleportRouteWithAStar(EntityPlayerSP player, Vec3d start, Vec3d end,
            long deadlineNanos) {
        double directDistance = start.distanceTo(end);
        int lateralSearch = MathHelper.clamp((int) Math.ceil(directDistance * 0.16D) + 6,
                TELEPORT_ATTACK_PATH_MIN_LATERAL_SEARCH, TELEPORT_ATTACK_PATH_MAX_LATERAL_SEARCH);
        int verticalSearch = MathHelper.clamp((int) Math.ceil(directDistance * 0.55D) + 16,
                TELEPORT_ATTACK_PATH_MIN_VERTICAL_SEARCH, TELEPORT_ATTACK_PATH_MAX_VERTICAL_SEARCH);
        int expansionLimit = MathHelper.clamp(TELEPORT_ATTACK_PATH_MIN_EXPANSIONS
                + (int) Math.ceil(directDistance) * 90,
                TELEPORT_ATTACK_PATH_MIN_EXPANSIONS, TELEPORT_ATTACK_PATH_MAX_EXPANSIONS);
        double minX = Math.min(start.x, end.x) - lateralSearch;
        double maxX = Math.max(start.x, end.x) + lateralSearch;
        double minZ = Math.min(start.z, end.z) - lateralSearch;
        double maxZ = Math.max(start.z, end.z) + lateralSearch;
        int downwardSearch = Math.min(32, Math.max(10, verticalSearch / 3));
        double minY = Math.max(0.05D, Math.min(start.y, end.y) - downwardSearch);
        double maxY = Math.min(player.world.getActualHeight() - Math.max(1.0D, player.height) - 0.05D,
                Math.max(start.y, end.y) + verticalSearch);

        PriorityQueue<TeleportPathNode> open = new PriorityQueue<>((left, right) -> Double.compare(left.fScore,
                right.fScore));
        Map<Long, TeleportPathNode> bestNodes = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        TeleportPathNode startNode = new TeleportPathNode(0, 0, 0, 0.0D,
                teleportPathHeuristic(start, end) * TELEPORT_ATTACK_PATH_WEIGHT, null);
        open.add(startNode);
        bestNodes.put(getTeleportPathNodeKey(0, 0, 0), startNode);

        int expansions = 0;
        while (!open.isEmpty() && expansions < expansionLimit && System.nanoTime() < deadlineNanos) {
            TeleportPathNode current = open.poll();
            long currentKey = getTeleportPathNodeKey(current.offsetX, current.offsetY, current.offsetZ);
            if (bestNodes.get(currentKey) != current || !closed.add(currentKey)) {
                continue;
            }
            expansions++;

            Vec3d currentPosition = current.toPosition(start);
            if (currentPosition.squareDistanceTo(end) <= TELEPORT_ATTACK_PATH_GOAL_DISTANCE_SQ
                    && isTeleportSegmentClear(player, currentPosition, end)) {
                return reconstructTeleportRoute(current, start, end);
            }

            for (int[] direction : TELEPORT_ATTACK_PATH_DIRECTIONS) {
                int nextOffsetX = current.offsetX + direction[0];
                int nextOffsetY = current.offsetY + direction[1];
                int nextOffsetZ = current.offsetZ + direction[2];
                long nextKey = getTeleportPathNodeKey(nextOffsetX, nextOffsetY, nextOffsetZ);
                if (closed.contains(nextKey)) {
                    continue;
                }

                Vec3d nextPosition = new Vec3d(start.x + nextOffsetX, start.y + nextOffsetY,
                        start.z + nextOffsetZ);
                if (nextPosition.x < minX || nextPosition.x > maxX
                        || nextPosition.y < minY || nextPosition.y > maxY
                        || nextPosition.z < minZ || nextPosition.z > maxZ
                        || !isTeleportStepClear(player, currentPosition, nextPosition)) {
                    continue;
                }

                double moveCost = direction[1] > 0 ? 1.15D : direction[1] < 0 ? 1.05D : 1.0D;
                double nextGScore = current.gScore + moveCost;
                TeleportPathNode known = bestNodes.get(nextKey);
                if (known != null && nextGScore >= known.gScore - 1.0E-6D) {
                    continue;
                }
                double nextFScore = nextGScore
                        + teleportPathHeuristic(nextPosition, end) * TELEPORT_ATTACK_PATH_WEIGHT;
                TeleportPathNode next = new TeleportPathNode(nextOffsetX, nextOffsetY, nextOffsetZ,
                        nextGScore, nextFScore, current);
                bestNodes.put(nextKey, next);
                open.add(next);
            }
        }
        teleportDebug(player, "ASTAR_FAIL",
                "reason=%s,expansions=%d,expansionLimit=%d,openRemaining=%d,distance=%.2f,"
                        + "lateral=%d,vertical=%d",
                open.isEmpty() ? "OPEN_EXHAUSTED"
                        : expansions >= expansionLimit ? "EXPANSION_LIMIT" : "DEADLINE",
                expansions, expansionLimit, open.size(), directDistance,
                lateralSearch, verticalSearch);
        return null;
    }

    private List<Vec3d> reconstructTeleportRoute(TeleportPathNode goalNode, Vec3d start, Vec3d end) {
        List<Vec3d> reversed = new ArrayList<>();
        TeleportPathNode current = goalNode;
        while (current != null) {
            reversed.add(current.toPosition(start));
            current = current.parent;
        }
        Collections.reverse(reversed);
        if (reversed.isEmpty()) {
            reversed.add(start);
        } else {
            reversed.set(0, start);
        }
        addTeleportRoutePoint(reversed, end);
        return reversed;
    }

    private List<Vec3d> simplifyTeleportRoute(EntityPlayerSP player, List<Vec3d> route, long deadlineNanos) {
        List<Vec3d> compressed = compressCollinearTeleportRoute(route);
        if (compressed == null || compressed.size() < 3) {
            return compressed;
        }
        List<Vec3d> simplified = new ArrayList<>();
        int index = 0;
        simplified.add(compressed.get(0));
        while (index < compressed.size() - 1) {
            if (System.nanoTime() >= deadlineNanos) {
                for (int remaining = index + 1; remaining < compressed.size(); remaining++) {
                    addTeleportRoutePoint(simplified, compressed.get(remaining));
                }
                break;
            }
            int furthest = Math.min(compressed.size() - 1, index + 64);
            while (furthest > index + 1
                    && !isTeleportSegmentClear(player, compressed.get(index), compressed.get(furthest))) {
                furthest--;
            }
            index = furthest;
            addTeleportRoutePoint(simplified, compressed.get(index));
        }
        return simplified;
    }

    private List<Vec3d> compressCollinearTeleportRoute(List<Vec3d> route) {
        if (route == null || route.size() < 3) {
            return route;
        }
        List<Vec3d> compressed = new ArrayList<>();
        compressed.add(route.get(0));
        for (int index = 1; index < route.size() - 1; index++) {
            Vec3d previous = route.get(index - 1);
            Vec3d current = route.get(index);
            Vec3d next = route.get(index + 1);
            double firstX = current.x - previous.x;
            double firstY = current.y - previous.y;
            double firstZ = current.z - previous.z;
            double secondX = next.x - current.x;
            double secondY = next.y - current.y;
            double secondZ = next.z - current.z;
            if (Math.abs(firstX - secondX) > 1.0E-6D
                    || Math.abs(firstY - secondY) > 1.0E-6D
                    || Math.abs(firstZ - secondZ) > 1.0E-6D) {
                compressed.add(current);
            }
        }
        compressed.add(route.get(route.size() - 1));
        return compressed;
    }

    private void addTeleportRoutePoint(List<Vec3d> route, Vec3d point) {
        if (route == null || point == null) {
            return;
        }
        if (route.isEmpty()
                || route.get(route.size() - 1).squareDistanceTo(point) > TELEPORT_ATTACK_WAYPOINT_EPSILON_SQ) {
            route.add(point);
        }
    }

    private boolean isTeleportSegmentClear(EntityPlayerSP player, Vec3d start, Vec3d end) {
        if (player == null || player.world == null || start == null || end == null
                || !isTeleportPositionClear(player, start)) {
            return false;
        }
        double distance = start.distanceTo(end);
        int samples = Math.max(1, (int) Math.ceil(distance / TELEPORT_ATTACK_PATH_SAMPLE_DISTANCE));
        AxisAlignedBB previousBox = getTeleportPlayerBox(player, start);
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            Vec3d point = new Vec3d(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                    start.z + (end.z - start.z) * progress);
            AxisAlignedBB currentBox = getTeleportPlayerBox(player, point);
            if (!isTeleportBoxClear(player, previousBox.union(currentBox))) {
                return false;
            }
            previousBox = currentBox;
        }
        return true;
    }

    private boolean isTeleportPositionClear(EntityPlayerSP player, Vec3d feetPosition) {
        if (player == null || player.world == null || feetPosition == null) {
            return false;
        }
        double height = Math.max(1.0D, player.height);
        if (feetPosition.y < 0.0D || feetPosition.y + height > player.world.getActualHeight()) {
            return false;
        }
        return isTeleportBoxClear(player, getTeleportPlayerBox(player, feetPosition));
    }

    private boolean isTeleportStepClear(EntityPlayerSP player, Vec3d start, Vec3d end) {
        if (player == null || player.world == null || start == null || end == null) {
            return false;
        }
        double distance = start.distanceTo(end);
        if (distance <= 1.5D) {
            // Short hops keep the tight two-box hull: it is exact there and cheap,
            // which matters because the A* neighbour expansion calls this a lot.
            AxisAlignedBB sweptBox = getTeleportPlayerBox(player, start).union(getTeleportPlayerBox(player, end));
            return isTeleportBoxClear(player, sweptBox);
        }
        // Long teleport steps must sample the line itself. The union of the two
        // boxes spans the whole rectangle between them, so any ledge or wall
        // corner beside the line would reject a step the planner already cleared.
        int samples = Math.max(2, (int) Math.ceil(distance / TELEPORT_ATTACK_PATH_SAMPLE_DISTANCE));
        for (int i = 1; i <= samples; i++) {
            Vec3d point = start.add(end.subtract(start).scale((double) i / (double) samples));
            if (!isTeleportPositionClear(player, point)) {
                return false;
            }
        }
        return true;
    }

    private AxisAlignedBB getTeleportPlayerBox(EntityPlayerSP player, Vec3d feetPosition) {
        double height = Math.max(1.0D, player.height);
        double halfWidth = Math.max(0.3D, player.width * 0.5D) - 1.0E-4D;
        return new AxisAlignedBB(
                feetPosition.x - halfWidth, feetPosition.y + 0.001D, feetPosition.z - halfWidth,
                feetPosition.x + halfWidth, feetPosition.y + height - 0.001D, feetPosition.z + halfWidth);
    }

    private boolean isTeleportBoxClear(EntityPlayerSP player, AxisAlignedBB box) {
        if (player == null || player.world == null || box == null
                || box.minY < 0.0D || box.maxY > player.world.getActualHeight()) {
            return false;
        }
        double borderMinX = player.world.getWorldBorder().minX();
        double borderMaxX = player.world.getWorldBorder().maxX();
        double borderMinZ = player.world.getWorldBorder().minZ();
        double borderMaxZ = player.world.getWorldBorder().maxZ();
        if (box.minX <= borderMinX || box.maxX >= borderMaxX
                || box.minZ <= borderMinZ || box.maxZ >= borderMaxZ
                || !isTeleportBoxLoaded(player, box)) {
            return false;
        }
        return player.world.getCollisionBoxes(null, box).isEmpty();
    }

    private boolean isTeleportBoxLoaded(EntityPlayerSP player, AxisAlignedBB box) {
        int minChunkX = MathHelper.floor(box.minX) >> 4;
        int maxChunkX = MathHelper.floor(box.maxX - 1.0E-6D) >> 4;
        int minChunkZ = MathHelper.floor(box.minZ) >> 4;
        int maxChunkZ = MathHelper.floor(box.maxZ - 1.0E-6D) >> 4;
        int sampleY = MathHelper.clamp(MathHelper.floor(box.minY), 0, player.world.getActualHeight() - 1);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!player.world.isBlockLoaded(new BlockPos(chunkX << 4, sampleY, chunkZ << 4), false)) {
                    return false;
                }
            }
        }
        return true;
    }

    private double teleportPathHeuristic(Vec3d position, Vec3d end) {
        double dx = position.x - end.x;
        double dy = position.y - end.y;
        double dz = position.z - end.z;
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy);
    }

    private long getTeleportPathNodeKey(int offsetX, int offsetY, int offsetZ) {
        return ((long) offsetX & 0x1FFFFFL) << 42
                | ((long) offsetY & 0x1FFFFFL) << 21
                | ((long) offsetZ & 0x1FFFFFL);
    }

    private void addTeleportWaypoint(List<Vec3d> waypoints, Vec3d waypoint) {
        if (waypoint == null) {
            return;
        }
        if (waypoints.isEmpty()) {
            waypoints.add(waypoint);
            return;
        }
        Vec3d last = waypoints.get(waypoints.size() - 1);
        if (last.squareDistanceTo(waypoint) > TELEPORT_ATTACK_WAYPOINT_EPSILON_SQ) {
            waypoints.add(waypoint);
        }
    }

    private void sendTeleportWaypoints(EntityPlayerSP player, List<Vec3d> waypoints, boolean onGround) {
        if (player == null || player.connection == null || waypoints == null) {
            return;
        }
        for (Vec3d waypoint : waypoints) {
            if (waypoint == null) {
                continue;
            }
            player.connection.sendPacket(new CPacketPlayer.Position(waypoint.x, waypoint.y, waypoint.z, onGround));
        }
    }

    private boolean sendTeleportReturnToOrigin(EntityPlayerSP player, TeleportAttackPlan plan, double startX,
            double startY,
            double startZ) {
        if (player == null || player.connection == null || plan == null) {
            return false;
        }

        List<Vec3d> returnWaypoints = isTeleportPathStartExact(startX, startY, startZ,
                plan.assaultX, plan.assaultY, plan.assaultZ)
                        ? plan.returnWaypoints
                        : buildTeleportPathWaypoints(player, startX, startY, startZ, plan.originX, plan.originY,
                                plan.originZ);
        if (returnWaypoints == null) {
            return false;
        }
        plan.latestReturnRoute.clear();
        plan.latestReturnRoute.add(new Vec3d(startX, startY, startZ));
        plan.latestReturnRoute.addAll(returnWaypoints);
        plan.latestReturnRoute.add(new Vec3d(plan.originX, plan.originY, plan.originZ));
        sendTeleportWaypoints(player, returnWaypoints, plan.originOnGround);
        player.connection.sendPacket(new CPacketPlayer.PositionRotation(plan.originX, plan.originY, plan.originZ,
                plan.originYaw, plan.originPitch, plan.originOnGround));
        plan.returnCompleted = false;
        return true;
    }

    private boolean scheduleTeleportCorrectionReturn(EntityPlayerSP player, TeleportAttackPlan plan,
            double startX, double startY, double startZ) {
        RuntimeState rt = rt();
        if (player == null || player.connection == null || plan == null) {
            teleportDebug(player, "CORRECTION_PLAN_STOP",
                    "reason=INVALID_INPUT,playerNull=%s,connectionNull=%s,planNull=%s",
                    player == null, player == null || player.connection == null, plan == null);
            return false;
        }
        List<Vec3d> returnWaypoints = buildKnownTeleportCorrectionWaypoints(player,
                new Vec3d(startX, startY, startZ), plan);
        if (returnWaypoints == null) {
            returnWaypoints = isTeleportPathStartExact(startX, startY, startZ,
                    plan.assaultX, plan.assaultY, plan.assaultZ)
                    ? new ArrayList<>(plan.returnWaypoints)
                    : buildFastTeleportCorrectionWaypoints(player, startX, startY, startZ, plan);
        }
        if (returnWaypoints == null || returnWaypoints.size() + 1 > TELEPORT_ROUTE_MAX_WAYPOINTS) {
            teleportDebug(player, "CORRECTION_PLAN_STOP",
                    "reason=NO_ROUTE_OR_TOO_MANY_WAYPOINTS,waypoints=%d,cost=%d,limit=%d,start=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    returnWaypoints == null ? -1 : returnWaypoints.size(),
                    returnWaypoints == null ? -1 : returnWaypoints.size() + 1,
                    TELEPORT_ROUTE_MAX_WAYPOINTS, startX, startY, startZ,
                    plan.originX, plan.originY, plan.originZ);
            return false;
        }
        List<Vec3d> route = new ArrayList<>();
        route.add(new Vec3d(startX, startY, startZ));
        route.addAll(returnWaypoints);
        route.add(new Vec3d(plan.originX, plan.originY, plan.originZ));
        rt().activeTeleportCorrectionPlan = plan;
        rt().pendingTeleportReturnPlan = new TeleportReturnPlan(plan, returnWaypoints, route);
        rt().pendingTeleportReturnTicks = getTeleportCorrectionWindowTicks(player);
        plan.correctedByServer = true;
        teleportDebug(player, "CORRECTION_PLAN_READY",
                "waypoints=%d,packetCost=%d,start=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f),returnTicks=%d",
                returnWaypoints.size(), returnWaypoints.size() + 1, startX, startY, startZ,
                plan.originX, plan.originY, plan.originZ, rt().pendingTeleportReturnTicks);
        return true;
    }

    private List<Vec3d> buildKnownTeleportCorrectionWaypoints(EntityPlayerSP player, Vec3d corrected,
            TeleportAttackPlan plan) {
        RuntimeState rt = rt();
        TeleportAttackPlan routeHolder = rt().activeTeleportAttackPlan;
        if (player == null || corrected == null || plan == null || routeHolder == null) {
            return null;
        }
        TeleportRoute bestRoute = null;
        int bestSegmentIndex = -1;
        boolean originAtStart = false;
        double bestDistanceSq = 2.25D;
        for (TeleportRoute route : routeHolder.relatedRoutes) {
            if (route == null || route.owner != plan || route.points.size() < 2) {
                continue;
            }
            boolean routeOriginAtStart = isSamePosition(route.points.get(0).x, route.points.get(0).y,
                    route.points.get(0).z, plan.originX, plan.originY, plan.originZ);
            Vec3d last = route.points.get(route.points.size() - 1);
            boolean routeOriginAtEnd = isSamePosition(last.x, last.y, last.z,
                    plan.originX, plan.originY, plan.originZ);
            if (!routeOriginAtStart && !routeOriginAtEnd) {
                continue;
            }
            for (int segment = 0; segment < route.points.size() - 1; segment++) {
                double distanceSq = getDistanceSqToSegment(corrected,
                        route.points.get(segment), route.points.get(segment + 1));
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestRoute = route;
                    bestSegmentIndex = segment;
                    originAtStart = routeOriginAtStart;
                }
            }
        }
        if (bestRoute == null || bestSegmentIndex < 0) {
            return null;
        }

        int anchorIndex = originAtStart ? bestSegmentIndex : bestSegmentIndex + 1;
        Vec3d anchor = bestRoute.points.get(anchorIndex);
        List<Vec3d> routeWaypoints = new ArrayList<>();
        if (originAtStart) {
            for (int index = anchorIndex; index > 0; index--) {
                addTeleportWaypoint(routeWaypoints, bestRoute.points.get(index));
            }
        } else {
            for (int index = anchorIndex; index < bestRoute.points.size() - 1; index++) {
                addTeleportWaypoint(routeWaypoints, bestRoute.points.get(index));
            }
        }

        int maximumWaypoints = Math.max(0, TELEPORT_ROUTE_MAX_WAYPOINTS - 1);
        int connectorBudget = maximumWaypoints - routeWaypoints.size();
        if (connectorBudget < 0) {
            return null;
        }
        TeleportPlanningBudget planningBudget = new TeleportPlanningBudget(
                System.nanoTime() + TELEPORT_TOUR_PLANNING_BUDGET_NANOS,
                TELEPORT_PLANNING_WORLD_QUERY_LIMIT);
        List<Vec3d> connector = buildFastTeleportLeg(player, corrected, anchor, connectorBudget, planningBudget);
        if (connector == null) {
            return null;
        }
        List<Vec3d> result = new ArrayList<>();
        for (Vec3d waypoint : connector) {
            addTeleportWaypoint(result, waypoint);
        }
        for (Vec3d waypoint : routeWaypoints) {
            addTeleportWaypoint(result, waypoint);
        }
        if (!result.isEmpty()) {
            Vec3d lastWaypoint = result.get(result.size() - 1);
            if (isSamePosition(lastWaypoint.x, lastWaypoint.y, lastWaypoint.z,
                    plan.originX, plan.originY, plan.originZ)) {
                result.remove(result.size() - 1);
            }
        }
        return result.size() <= maximumWaypoints ? result : null;
    }

    private List<Vec3d> buildFastTeleportCorrectionWaypoints(EntityPlayerSP player,
            double startX, double startY, double startZ, TeleportAttackPlan plan) {
        if (player == null || plan == null) {
            return null;
        }
        Vec3d start = new Vec3d(startX, startY, startZ);
        Vec3d origin = new Vec3d(plan.originX, plan.originY, plan.originZ);
        TeleportPlanningBudget planningBudget = new TeleportPlanningBudget(
                System.nanoTime() + TELEPORT_TOUR_PLANNING_BUDGET_NANOS,
                TELEPORT_PLANNING_WORLD_QUERY_LIMIT);
        List<Vec3d> waypoints = buildFastTeleportLeg(player, start, origin,
                Math.max(1, TELEPORT_ROUTE_MAX_WAYPOINTS - 1), planningBudget);
        if (waypoints == null) {
            return null;
        }
        if (!waypoints.isEmpty()
                && waypoints.get(waypoints.size() - 1)
                        .squareDistanceTo(origin) <= TELEPORT_ATTACK_WAYPOINT_EPSILON_SQ) {
            waypoints.remove(waypoints.size() - 1);
        }
        return waypoints;
    }

    private boolean tryExecutePendingTeleportReturn(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (rt.teleport.timing.isRouteRejected()) return false;
        TeleportReturnPlan pendingReturn = rt().pendingTeleportReturnPlan;
        if (player == null || player.connection == null || pendingReturn == null) {
            teleportDebug(player, "RETURN_STOP",
                    "reason=INVALID_INPUT,playerNull=%s,connectionNull=%s,pendingReturnNull=%s",
                    player == null, player == null || player.connection == null, pendingReturn == null);
            return false;
        }
        if (rt().teleport.currentTick() < pendingReturn.notBeforeTick) {
            rt().pendingTeleportReturnTicks = Math.max(2, rt().pendingTeleportReturnTicks);
            teleportDebug(player, "RETURN_WAIT", "reason=NOT_BEFORE_TICK,notBeforeTick=%d,currentTick=%d",
                    pendingReturn.notBeforeTick, rt().teleport.currentTick());
            return false;
        }
        int packetCost = pendingReturn.waypoints.size() + 1;
        if (packetCost > TELEPORT_ROUTE_MAX_WAYPOINTS) {
            teleportDebug(player, "RETURN_STOP", "reason=COST_ABOVE_HARD_LIMIT,cost=%d,limit=%d",
                    packetCost, TELEPORT_ROUTE_MAX_WAYPOINTS);
            clearTeleportAttackState(player, "PENDING_RETURN_COST_ABOVE_LIMIT");
            return false;
        }
        if (!reserveTeleportPackets(player, packetCost)) {
            rt().pendingTeleportReturnTicks = Math.max(2, rt().pendingTeleportReturnTicks);
            teleportDebug(player, "RETURN_WAIT",
                    "reason=PACKET_BUDGET,cost=%d,used=%d",
                    packetCost, rt().teleport.teleportPacketsUsedThisTick);
            return false;
        }

        TeleportAttackPlan plan = pendingReturn.owner;
        sendTeleportWaypoints(player, pendingReturn.waypoints, false);
        player.connection.sendPacket(new CPacketPlayer.PositionRotation(plan.originX, plan.originY, plan.originZ,
                plan.originYaw, plan.originPitch, plan.originOnGround));
        plan.latestReturnRoute.clear();
        plan.latestReturnRoute.addAll(pendingReturn.route);
        registerTeleportRoute(rt().activeTeleportAttackPlan, plan, pendingReturn.route);
        plan.correctionCount++;
        plan.returnCompleted = true;
        rt().lastTeleportCorrectionTick = rt().teleport.currentTick();
        rt().pendingTeleportReturnPlan = null;
        player.setPositionAndRotation(plan.originX, plan.originY, plan.originZ,
                plan.originYaw, plan.originPitch);
        if (rt().activeTeleportAttackPlan != plan) {
            List<TeleportRoute> knownRoutes = new ArrayList<>(rt().activeTeleportAttackPlan.relatedRoutes);
            plan.relatedRoutes.clear();
            plan.relatedRoutes.addAll(knownRoutes);
            rt().activeTeleportAttackPlan = plan;
        }
        rt().activeTeleportCorrectionPlan = null;
        rt().pendingTeleportReturnTicks = getTeleportCorrectionWindowTicks(player);
        teleportDebug(player, "RETURN_SENT",
                "waypoints=%d,packetCost=%d,origin=(%.3f,%.3f,%.3f),correctionCount=%d,returnTicks=%d",
                pendingReturn.waypoints.size(), packetCost, plan.originX, plan.originY, plan.originZ,
                plan.correctionCount, rt().pendingTeleportReturnTicks);
        return true;
    }

    private boolean isTeleportPathStartExact(double leftX, double leftY, double leftZ,
            double rightX, double rightY, double rightZ) {
        double dx = leftX - rightX;
        double dy = leftY - rightY;
        double dz = leftZ - rightZ;
        return dx * dx + dy * dy + dz * dz <= 1.0E-6D;
    }

    private void applyRotation(EntityPlayerSP player, EntityLivingBase target) {
        applyRotation(player, target, false);
    }

    private void applyRotation(EntityPlayerSP player, EntityLivingBase target, boolean forceSmoothRotation) {
        applyRotation(player, target, forceSmoothRotation, false);
    }

    private boolean prepareRotationForAttack(EntityPlayerSP player, EntityLivingBase target,
            EntityLivingBase crosshairLockedTarget) {
        if (crosshairLockedTarget != null) {
            return isSameEntity(target, crosshairLockedTarget);
        }
        boolean targetSwitchSmoothing = isTargetSwitchSmoothingActive();
        boolean rotateOnlyForAttack = shouldRotateOnlyOnAttack();
        if (!rotateOnlyForAttack && !targetSwitchSmoothing) {
            return true;
        }
        if (rotateOnlyForAttack && !targetSwitchSmoothing) {
            applyRotation(player, target, false, true);
        }
        return targetSwitchSmoothing ? isRotationReadyForSmoothAttack(player, target)
                : isRotationReadyForAttack(player, target);
    }

    private void applyRotation(EntityPlayerSP player, EntityLivingBase target, boolean forceSmoothRotation,
            boolean attackRotation) {
        Rotation nextRotation = computeNextAimRotation(player, target, forceSmoothRotation, attackRotation);
        if (nextRotation == null) {
            return;
        }

        player.rotationYaw = nextRotation.getYaw();
        player.rotationPitch = nextRotation.getPitch();
        player.rotationYawHead = nextRotation.getYaw();
        player.renderYawOffset = nextRotation.getYaw();
    }

    private Rotation computeNextAimRotation(EntityPlayerSP player, EntityLivingBase target, boolean forceSmoothRotation,
            boolean attackRotation) {
        RuntimeState rt = rt();
        if (player == null || target == null || isRelockSuppressedByCrosshairTarget(player, target)) {
            return null;
        }

        Rotation desiredAim = getDesiredAimRotation(player, target, attackRotation);
        boolean useSmoothRotation = forceSmoothRotation || cfg().smoothRotation;
        if (!useSmoothRotation) {
            return applyMouseGcdCompensation(player, desiredAim, Float.MAX_VALUE, Float.MAX_VALUE);
        }

        desiredAim = applyAdvancedOvershootCorrection(player, target, desiredAim, attackRotation);
        float yawDelta = MathHelper.wrapDegrees(desiredAim.getYaw() - player.rotationYaw);
        float pitchDelta = desiredAim.getPitch() - player.rotationPitch;
        float yawSpeed = Math.max(computeTurnSpeed(Math.abs(yawDelta), attackRotation),
                computeTrackingYawSpeedFloor(player, target) * (attackRotation ? 0.85F : 0.65F));
        float pitchSpeed = Math.max(0.6F, yawSpeed * 0.62F);

        float yawStep = clampSigned(yawDelta, yawSpeed);
        float pitchStep = clampSigned(pitchDelta, pitchSpeed);
        updateSmoothTurnLimitBudget(player);
        float turnLimit = sampleSmoothMaxTurnStepForTurn();
        float remainingYawTurn = getRemainingSmoothYawTurnStep(turnLimit);
        float remainingPitchTurn = getRemainingSmoothPitchTurnStep(turnLimit);
        yawStep = clampSigned(yawStep, remainingYawTurn);
        pitchStep = clampSigned(pitchStep, remainingPitchTurn);
        float gcdStep = getMouseGcdStep();
        yawStep = quantizeRotationStepForGcd(yawStep, remainingYawTurn, gcdStep);
        pitchStep = quantizeRotationStepForGcd(pitchStep, remainingPitchTurn, gcdStep);
        rt.smoothYawTurnUsedThisTick += Math.abs(yawStep);
        rt.smoothPitchTurnUsedThisTick += Math.abs(pitchStep);

        float nextYaw = player.rotationYaw + yawStep;
        float nextPitch = player.rotationPitch + pitchStep;
        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private void updateSmoothTurnLimitBudget(EntityPlayerSP player) {
        RuntimeState rt = rt();
        int currentTick = rt().teleport.currentTick();
        if (currentTick != rt().lastSmoothTurnLimitTick) {
            rt().lastSmoothTurnLimitTick = currentTick;
            rt().smoothYawTurnUsedThisTick = 0.0F;
            rt().smoothPitchTurnUsedThisTick = 0.0F;
        }
    }

    private void resetSmoothTurnLimitBudget() {
        RuntimeState rt = rt();
        rt().lastSmoothTurnLimitTick = Integer.MIN_VALUE;
        rt().smoothYawTurnUsedThisTick = 0.0F;
        rt().smoothPitchTurnUsedThisTick = 0.0F;
        clearVisualRotationCache();
    }

    private Rotation getCachedVisualRotation(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (player == null || target == null || rt().visualRotationCache == null) {
            return null;
        }
        if (rt().visualRotationCacheTick != player.ticksExisted
                || rt().visualRotationCacheTargetEntityId != target.getEntityId()
                || Float.compare(rt().visualRotationCacheSourceYaw, player.rotationYaw) != 0
                || Float.compare(rt().visualRotationCacheSourcePitch, player.rotationPitch) != 0) {
            return null;
        }
        return rt().visualRotationCache;
    }

    private void cacheVisualRotation(EntityPlayerSP player, EntityLivingBase target, Rotation rotation) {
        RuntimeState rt = rt();
        if (player == null || target == null || rotation == null) {
            clearVisualRotationCache();
            return;
        }
        rt().visualRotationCacheTick = player.ticksExisted;
        rt().visualRotationCacheTargetEntityId = target.getEntityId();
        rt().visualRotationCacheSourceYaw = player.rotationYaw;
        rt().visualRotationCacheSourcePitch = player.rotationPitch;
        rt().visualRotationCache = rotation;
    }

    private void clearVisualRotationCache() {
        RuntimeState rt = rt();
        rt().visualRotationCacheTick = Integer.MIN_VALUE;
        rt().visualRotationCacheTargetEntityId = Integer.MIN_VALUE;
        rt().visualRotationCacheSourceYaw = 0.0F;
        rt().visualRotationCacheSourcePitch = 0.0F;
        rt().visualRotationCache = null;
    }

    private float getRemainingSmoothYawTurnStep(float turnLimit) {
        RuntimeState rt = rt();
        return Math.max(0.0F, turnLimit - rt().smoothYawTurnUsedThisTick);
    }

    private float getRemainingSmoothPitchTurnStep(float turnLimit) {
        RuntimeState rt = rt();
        return Math.max(0.0F, turnLimit - rt().smoothPitchTurnUsedThisTick);
    }

    private Rotation applyMouseGcdCompensation(EntityPlayerSP player, Rotation desiredRotation,
            float maxYawStep, float maxPitchStep) {
        if (player == null || desiredRotation == null) {
            return desiredRotation;
        }
        float yawStep = MathHelper.wrapDegrees(desiredRotation.getYaw() - player.rotationYaw);
        float pitchStep = desiredRotation.getPitch() - player.rotationPitch;
        float gcdStep = getMouseGcdStep();
        yawStep = quantizeRotationStepForGcd(yawStep, maxYawStep, gcdStep);
        pitchStep = quantizeRotationStepForGcd(pitchStep, maxPitchStep, gcdStep);
        return new Rotation(player.rotationYaw + yawStep,
                MathHelper.clamp(player.rotationPitch + pitchStep, -90.0F, 90.0F));
    }

    private float getMouseGcdStep() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return 0.0F;
        }
        float sensitivity = MathHelper.clamp(mc.gameSettings.mouseSensitivity, 0.0F, 1.0F);
        float f = sensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F * 0.15F;
    }

    private float quantizeRotationStepForGcd(float step, float maxMagnitude, float gcdStep) {
        if (step == 0.0F || gcdStep <= 1.0E-5F || maxMagnitude <= 0.0F) {
            return step == 0.0F ? 0.0F : clampSigned(step, maxMagnitude);
        }
        float maxStep = Math.abs(maxMagnitude);
        float absStep = Math.min(Math.abs(step), maxStep);
        if (absStep <= 1.0E-5F) {
            return 0.0F;
        }

        // When the user's smooth cap is below one mouse quantum, preserving the cap
        // matters more than quantizing.
        if (maxStep + 1.0E-5F < gcdStep) {
            return Math.copySign(absStep, step);
        }

        float quantized = Math.round(absStep / gcdStep) * gcdStep;
        if (quantized <= 1.0E-5F && absStep >= gcdStep * 0.45F) {
            quantized = gcdStep;
        }
        if (quantized > maxStep) {
            quantized = (float) Math.floor(maxStep / gcdStep) * gcdStep;
        }
        if (quantized <= 1.0E-5F) {
            return 0.0F;
        }
        return Math.copySign(Math.min(quantized, maxStep), step);
    }

    private boolean isRotationReadyForAttack(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return false;
        }
        Rotation desiredAim = getDesiredAimRotation(player, target);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(desiredAim.getYaw() - player.rotationYaw));
        float pitchDiff = Math.abs(desiredAim.getPitch() - player.rotationPitch);
        return yawDiff <= 100.0F && pitchDiff <= 80.0F;
    }

    private boolean isRotationReadyForSmoothAttack(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return false;
        }
        Rotation desiredAim = getDesiredAimRotation(player, target);
        float yawDiff = Math.abs(MathHelper.wrapDegrees(desiredAim.getYaw() - player.rotationYaw));
        float pitchDiff = Math.abs(desiredAim.getPitch() - player.rotationPitch);
        return yawDiff <= SMOOTH_ATTACK_READY_YAW_DEGREES && pitchDiff <= SMOOTH_ATTACK_READY_PITCH_DEGREES;
    }

    private boolean shouldApplyContinuousRotation(boolean orbitFacingActive) {
        if (isTeleportAttackMode() && !rt().aimOnlyMode) {
            return false;
        }
        if (!shouldRotateToTarget() && !orbitFacingActive) {
            return false;
        }
        return !cfg().rotateOnlyOnAttack || rt().aimOnlyMode || isTargetSwitchSmoothingActive();
    }

    private void updateAimTargetTransition(EntityLivingBase target) {
        RuntimeState rt = rt();
        if (target == null) {
            clearAimTargetTransition();
            return;
        }

        int targetEntityId = target.getEntityId();
        if (targetEntityId == rt().lastAimTargetEntityId) {
            return;
        }

        boolean hadPreviousTarget = rt().lastAimTargetEntityId != Integer.MIN_VALUE && rt().lastAimTargetEntityId != -1;
        rt().lastAimTargetEntityId = targetEntityId;
        if (hadPreviousTarget && cfg().smoothRotation && shouldRotateToTarget()) {
            rt().targetSwitchSmoothTicks = TARGET_SWITCH_SMOOTH_TICKS;
        } else {
            rt().targetSwitchSmoothTicks = 0;
        }
    }

    private boolean isTargetSwitchSmoothingActive() {
        RuntimeState rt = rt();
        return rt().targetSwitchSmoothTicks > 0 && cfg().smoothRotation && shouldRotateToTarget();
    }

    private void decayTargetSwitchSmoothTicks() {
        RuntimeState rt = rt();
        if (rt().targetSwitchSmoothTicks > 0) {
            rt().targetSwitchSmoothTicks--;
        }
    }

    private void clearAimTargetTransition() {
        RuntimeState rt = rt();
        rt().lastAimTargetEntityId = Integer.MIN_VALUE;
        rt().targetSwitchSmoothTicks = 0;
        clearVisualRotationCache();
    }

    private boolean shouldForceOrbitFacing(EntityPlayerSP player, EntityLivingBase target) {
        return isHuntOrbitEnabled() && canStartOrbitHunt(player, target);
    }

    private float computeTrackingYawSpeedFloor(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return cfg().minTurnSpeed;
        }

        double radiusX = target.posX - player.posX;
        double radiusZ = target.posZ - player.posZ;
        double horizontalDistance = Math.sqrt(radiusX * radiusX + radiusZ * radiusZ);
        if (horizontalDistance <= 1.0E-4D) {
            return cfg().minTurnSpeed;
        }

        double playerDeltaX = player.posX - player.lastTickPosX;
        double playerDeltaZ = player.posZ - player.lastTickPosZ;
        double targetDeltaX = target.posX - target.lastTickPosX;
        double targetDeltaZ = target.posZ - target.lastTickPosZ;
        double relativeDeltaX = targetDeltaX - playerDeltaX;
        double relativeDeltaZ = targetDeltaZ - playerDeltaZ;

        double tangentX = -radiusZ / horizontalDistance;
        double tangentZ = radiusX / horizontalDistance;
        double tangentialSpeed = Math.abs(relativeDeltaX * tangentX + relativeDeltaZ * tangentZ);
        double angularVelocityDeg = Math.toDegrees(Math.atan2(tangentialSpeed, horizontalDistance));
        double speedFloor = angularVelocityDeg * 1.18D + 1.35D;

        if (isHuntOrbitEnabled() && canStartOrbitHunt(player, target)) {
            speedFloor += 2.25D;
        }
        if (SpeedHandler.INSTANCE.isEnabledNow()) {
            speedFloor += Math.max(0.0D, (SpeedHandler.getCurrentTimerSpeedMultiplier() - 1.0F) * 8.0D);
        }

        return MathHelper.clamp((float) speedFloor, cfg().minTurnSpeed, Math.max(cfg().maxTurnSpeed, 60.0F));
    }

    private Rotation getDesiredAimRotation(EntityPlayerSP player, EntityLivingBase target) {
        return getDesiredAimRotation(player, target, false);
    }

    private Rotation getDesiredAimRotation(EntityPlayerSP player, EntityLivingBase target, boolean attackRotation) {
        if (player == null || target == null) {
            return new Rotation(0.0F, 0.0F);
        }
        return getAdvancedAimRotation(player, target, attackRotation);
    }

    private Rotation getAdvancedAimRotation(EntityPlayerSP player, EntityLivingBase target, boolean attackRotation) {
        float partialTicks = getCurrentAimPartialTicks();
        Vec3d eyePos = player.getPositionEyes(partialTicks);
        return getAdvancedAimRotationFromEye(player, target, eyePos, true,
                new Rotation(player.rotationYaw, player.rotationPitch), attackRotation);
    }

    private Rotation getAdvancedAimRotationFromPosition(EntityPlayerSP player, EntityLivingBase target,
            double fromX, double fromY, double fromZ, Rotation currentRotation, boolean attackRotation) {
        double eyeHeight = player == null ? 1.62D : player.getEyeHeight();
        return getAdvancedAimRotationFromEye(player, target, new Vec3d(fromX, fromY + eyeHeight, fromZ), false,
                currentRotation == null ? new Rotation(0.0F, 0.0F) : currentRotation, attackRotation);
    }

    private Rotation getAdvancedAimRotationFromEye(EntityPlayerSP player, EntityLivingBase target, Vec3d baseEyePos,
            boolean predictEye, Rotation currentRotation) {
        return getAdvancedAimRotationFromEye(player, target, baseEyePos, predictEye, currentRotation, false);
    }

    private Rotation getAdvancedAimRotationFromEye(EntityPlayerSP player, EntityLivingBase target, Vec3d baseEyePos,
            boolean predictEye, Rotation currentRotation, boolean attackRotation) {
        if (target == null || baseEyePos == null) {
            return currentRotation == null ? new Rotation(0.0F, 0.0F) : currentRotation;
        }

        float partialTicks = getCurrentAimPartialTicks();
        double targetX = interpolateAimCoordinate(target.lastTickPosX, target.posX, partialTicks);
        double targetY = interpolateAimCoordinate(target.lastTickPosY, target.posY, partialTicks)
                + getAdvancedAimHeightOffset(player, target, baseEyePos, attackRotation);
        double targetZ = interpolateAimCoordinate(target.lastTickPosZ, target.posZ, partialTicks);

        double leadTicks = computeAdvancedAimLeadTicks(player, target, baseEyePos, targetX, targetY, targetZ,
                attackRotation);
        double targetMotionX = clampPredictionMotion(target.posX - target.lastTickPosX);
        double targetMotionY = clampPredictionMotion(target.posY - target.lastTickPosY);
        double targetMotionZ = clampPredictionMotion(target.posZ - target.lastTickPosZ);
        targetX += targetMotionX * leadTicks;
        targetY += targetMotionY * leadTicks * 0.7D;
        targetZ += targetMotionZ * leadTicks;

        Vec3d eyePos = baseEyePos;
        if (predictEye && player != null) {
            double playerLead = Math.min(ADVANCED_AIM_MAX_PLAYER_LEAD_TICKS, leadTicks * 0.45D);
            eyePos = eyePos.addVector(
                    clampPredictionMotion(player.posX - player.lastTickPosX) * playerLead,
                    clampPredictionMotion(player.posY - player.lastTickPosY) * playerLead * 0.45D,
                    clampPredictionMotion(player.posZ - player.lastTickPosZ) * playerLead);
        }

        Rotation desired = RotationUtils.calcRotationFromVec3d(eyePos, new Vec3d(targetX, targetY, targetZ),
                currentRotation == null ? new Rotation(0.0F, 0.0F) : currentRotation);
        AimOffsetSample offset = sampleAimOffsets(player, target);
        return new Rotation(MathHelper.wrapDegrees(desired.getYaw() + offset.yaw),
                MathHelper.clamp(desired.getPitch() + offset.pitch, -90.0F, 90.0F));
    }

    private double getAdvancedAimHeightOffset(EntityPlayerSP player, EntityLivingBase target, Vec3d eyePos,
            boolean attackRotation) {
        double distance = 4.0D;
        if (target != null && eyePos != null) {
            double dx = target.posX - eyePos.x;
            double dy = target.posY - eyePos.y;
            double dz = target.posZ - eyePos.z;
            distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        double factor = attackRotation ? 0.80D : 0.84D;
        if (distance < 3.0D) {
            factor -= (3.0D - distance) * 0.035D;
        }
        if (target != null && target.height > 2.35F) {
            factor -= 0.08D;
        }
        double eyeHeight = target == null ? 1.5D : target.getEyeHeight();
        double maxOffset = target == null ? eyeHeight : Math.max(0.32D, target.height - 0.08D);
        return MathHelper.clamp(eyeHeight * factor, 0.28D, maxOffset);
    }

    private double computeAdvancedAimLeadTicks(EntityPlayerSP player, EntityLivingBase target, Vec3d eyePos,
            double targetX, double targetY, double targetZ, boolean attackRotation) {
        if (target == null || eyePos == null) {
            return ADVANCED_AIM_MIN_LEAD_TICKS;
        }
        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double targetMotionX = target.posX - target.lastTickPosX;
        double targetMotionZ = target.posZ - target.lastTickPosZ;
        double playerMotionX = player == null ? 0.0D : player.posX - player.lastTickPosX;
        double playerMotionZ = player == null ? 0.0D : player.posZ - player.lastTickPosZ;
        double relativeSpeed = Math.sqrt((targetMotionX - playerMotionX) * (targetMotionX - playerMotionX)
                + (targetMotionZ - playerMotionZ) * (targetMotionZ - playerMotionZ));

        double lead = 0.30D
                + Math.min(1.05D, distance / 9.0D)
                + Math.min(0.80D, relativeSpeed * 2.45D);
        if (attackRotation) {
            lead += 0.18D;
        }
        if (SpeedHandler.INSTANCE.isEnabledNow()) {
            lead += MathHelper.clamp((double) (SpeedHandler.getCurrentTimerSpeedMultiplier() - 1.0F) * 0.65D,
                    0.0D, 0.75D);
        }
        if (distance < 2.4D) {
            lead *= 0.58D;
        }
        return MathHelper.clamp(lead, ADVANCED_AIM_MIN_LEAD_TICKS, ADVANCED_AIM_MAX_LEAD_TICKS);
    }

    private double clampPredictionMotion(double motion) {
        return MathHelper.clamp(motion, -1.15D, 1.15D);
    }

    private Rotation applyAdvancedOvershootCorrection(EntityPlayerSP player, EntityLivingBase target,
            Rotation desiredAim, boolean attackRotation) {
        if (player == null || target == null || desiredAim == null) {
            return desiredAim;
        }
        float yawDelta = MathHelper.wrapDegrees(desiredAim.getYaw() - player.rotationYaw);
        float pitchDelta = desiredAim.getPitch() - player.rotationPitch;
        float absYaw = Math.abs(yawDelta);
        float absPitch = Math.abs(pitchDelta);

        float yawFade = MathHelper.clamp((absYaw - 1.35F) / 24.0F, 0.0F, 1.0F);
        float pitchFade = MathHelper.clamp((absPitch - 1.0F) / 22.0F, 0.0F, 1.0F);
        float angularVelocity = Math.abs(computeRelativeYawVelocity(player, target));
        float yawMax = attackRotation ? ADVANCED_OVERSHOOT_MAX_ATTACK_YAW : ADVANCED_OVERSHOOT_MAX_YAW;
        float yawOvershoot = MathHelper.clamp(absYaw * 0.045F + angularVelocity * 0.24F, 0.0F, yawMax) * yawFade;
        float pitchOvershoot = MathHelper.clamp(absPitch * 0.030F, 0.0F, ADVANCED_OVERSHOOT_MAX_PITCH) * pitchFade;

        float correctedYaw = desiredAim.getYaw() + Math.copySign(yawOvershoot, yawDelta);
        float correctedPitch = desiredAim.getPitch() + Math.copySign(pitchOvershoot, pitchDelta);
        return new Rotation(MathHelper.wrapDegrees(correctedYaw), MathHelper.clamp(correctedPitch, -90.0F, 90.0F));
    }

    private float computeRelativeYawVelocity(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return 0.0F;
        }
        double currentDx = target.posX - player.posX;
        double currentDz = target.posZ - player.posZ;
        double previousDx = target.lastTickPosX - player.lastTickPosX;
        double previousDz = target.lastTickPosZ - player.lastTickPosZ;
        if ((currentDx * currentDx + currentDz * currentDz) < 1.0E-5D
                || (previousDx * previousDx + previousDz * previousDz) < 1.0E-5D) {
            return 0.0F;
        }
        float currentYaw = (float) (Math.toDegrees(Math.atan2(currentDz, currentDx)) - 90.0D);
        float previousYaw = (float) (Math.toDegrees(Math.atan2(previousDz, previousDx)) - 90.0D);
        return MathHelper.wrapDegrees(currentYaw - previousYaw);
    }

    private AimOffsetSample sampleAimOffsets(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        int tick = player == null ? Integer.MIN_VALUE : player.ticksExisted;
        int targetEntityId = target == null ? Integer.MIN_VALUE : target.getEntityId();
        String yawSpec = getAimYawOffsetSpec();
        String pitchSpec = getAimPitchOffsetSpec();
        if (rt().aimOffsetSampleTick == tick
                && rt().aimOffsetSampleTargetEntityId == targetEntityId
                && yawSpec.equals(rt().aimOffsetSampleYawSpec)
                && pitchSpec.equals(rt().aimOffsetSamplePitchSpec)) {
            return new AimOffsetSample(rt().sampledAimYawOffset, rt().sampledAimPitchOffset);
        }

        rt().aimOffsetSampleTick = tick;
        rt().aimOffsetSampleTargetEntityId = targetEntityId;
        rt().aimOffsetSampleYawSpec = yawSpec;
        rt().aimOffsetSamplePitchSpec = pitchSpec;
        rt().sampledAimYawOffset = sampleAimOffset(cfg().aimYawOffsetSpec, cfg().aimYawOffset);
        rt().sampledAimPitchOffset = sampleAimOffset(cfg().aimPitchOffsetSpec, cfg().aimPitchOffset);
        return new AimOffsetSample(rt().sampledAimYawOffset, rt().sampledAimPitchOffset);
    }

    private boolean shouldUseMotionCompensatedVisualAim(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null || !SpeedHandler.INSTANCE.isEnabledNow()) {
            return false;
        }
        double horizontalSpeed = getHorizontalPlayerMotion(player);
        return horizontalSpeed > 0.32D || SpeedHandler.getCurrentTimerSpeedMultiplier() > 1.02F;
    }

    private float getCurrentAimPartialTicks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return 1.0F;
        }
        return MathHelper.clamp(mc.getRenderPartialTicks(), 0.0F, 1.0F);
    }

    private double interpolateAimCoordinate(double previous, double current, float progress) {
        return previous + (current - previous) * progress;
    }

    private double getHorizontalPlayerMotion(EntityPlayerSP player) {
        if (player == null) {
            return 0.0D;
        }
        return Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
    }

    private float getTargetYaw(EntityPlayerSP player, EntityLivingBase target) {
        double dx = target.posX - player.posX;
        double dz = target.posZ - player.posZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private float getTargetYawFromPosition(double fromX, double fromZ, EntityLivingBase target) {
        double dx = target.posX - fromX;
        double dz = target.posZ - fromZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
    }

    private float getTargetPitch(EntityPlayerSP player, EntityLivingBase target) {
        double dx = target.posX - player.posX;
        double dz = target.posZ - player.posZ;
        double dy = target.posY + target.getEyeHeight() * 0.85D - (player.posY + player.getEyeHeight());
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private float getTargetPitchFromPosition(double fromX, double fromY, double fromZ, EntityLivingBase target) {
        double dx = target.posX - fromX;
        double dz = target.posZ - fromZ;
        EntityPlayerSP currentPlayer = ctxPlayer();
        double eyeHeight = currentPlayer == null ? 1.62D : currentPlayer.getEyeHeight();
        double dy = target.posY + target.getEyeHeight() * 0.85D - (fromY + eyeHeight);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private float computeTurnSpeed(float yawDeltaAbs, boolean attackRotation) {
        float normalized = MathHelper.clamp(yawDeltaAbs / 120.0F, 0.0F, 1.0F);
        float eased = normalized * normalized;
        float effectiveMin = Math.max(0.65F, cfg().minTurnSpeed * (attackRotation ? 0.75F : 0.58F));
        float effectiveMax = Math.max(effectiveMin, cfg().maxTurnSpeed * (attackRotation ? 0.86F : 0.72F));
        return effectiveMin + (effectiveMax - effectiveMin) * eased;
    }

    private float clampSigned(float value, float maxMagnitude) {
        if (maxMagnitude <= 0.0F) {
            return 0.0F;
        }
        return Math.copySign(Math.min(Math.abs(value), maxMagnitude), value);
    }

    private static float sampleSmoothMaxTurnStepForTurn() {
        SmoothTurnStepRange range = parseSmoothMaxTurnStepSpec(cfg().smoothMaxTurnStepSpec, cfg().smoothMaxTurnStep);
        if (!range.isRandom()) {
            return range.min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(range.min, range.max);
    }

    public static void setSmoothMaxTurnStepSpec(String spec) {
        SmoothTurnStepRange range = parseSmoothMaxTurnStepSpec(spec, cfg().smoothMaxTurnStep);
        cfg().smoothMaxTurnStep = range.min;
        cfg().smoothMaxTurnStepSpec = range.toSpec();
    }

    public static String getSmoothMaxTurnStepDisplayText() {
        return parseSmoothMaxTurnStepSpec(cfg().smoothMaxTurnStepSpec, cfg().smoothMaxTurnStep).toDisplayText();
    }

    public static String getSmoothMaxTurnStepSpec() {
        return parseSmoothMaxTurnStepSpec(cfg().smoothMaxTurnStepSpec, cfg().smoothMaxTurnStep).toSpec();
    }

    private static SmoothTurnStepRange parseSmoothMaxTurnStepSpec(String spec, float fallback) {
        float safeFallback = MathHelper.clamp(Float.isNaN(fallback) ? DEFAULT_SMOOTH_MAX_TURN_STEP : fallback,
                MIN_SMOOTH_MAX_TURN_STEP, MAX_SMOOTH_MAX_TURN_STEP);
        String normalized = spec == null ? "" : spec.trim();
        if (normalized.isEmpty()) {
            return new SmoothTurnStepRange(safeFallback, safeFallback);
        }

        normalized = normalized.replace("°", "")
                .replace("，", ",")
                .replace("－", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("~", "-")
                .replace("～", "-")
                .replace("至", "-")
                .replace("到", "-")
                .replaceAll("\\s+", "");
        String[] parts = normalized.split("-", -1);
        try {
            if (parts.length == 1) {
                float value = MathHelper.clamp(Float.parseFloat(parts[0]), MIN_SMOOTH_MAX_TURN_STEP,
                        MAX_SMOOTH_MAX_TURN_STEP);
                return new SmoothTurnStepRange(value, value);
            }
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                float first = Float.parseFloat(parts[0]);
                float second = Float.parseFloat(parts[1]);
                float min = MathHelper.clamp(Math.min(first, second), MIN_SMOOTH_MAX_TURN_STEP,
                        MAX_SMOOTH_MAX_TURN_STEP);
                float max = MathHelper.clamp(Math.max(first, second), MIN_SMOOTH_MAX_TURN_STEP,
                        MAX_SMOOTH_MAX_TURN_STEP);
                return new SmoothTurnStepRange(min, Math.max(min, max));
            }
        } catch (Exception ignored) {
        }
        return new SmoothTurnStepRange(safeFallback, safeFallback);
    }

    private static String formatSmoothMaxTurnStepValue(float value) {
        float clamped = MathHelper.clamp(value, MIN_SMOOTH_MAX_TURN_STEP, MAX_SMOOTH_MAX_TURN_STEP);
        if (Math.abs(clamped - Math.round(clamped)) < 0.0001F) {
            return String.valueOf(Math.round(clamped));
        }
        return String.format(Locale.ROOT, "%.2f", clamped).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static final class SmoothTurnStepRange {
        private final float min;
        private final float max;

        private SmoothTurnStepRange(float min, float max) {
            this.min = MathHelper.clamp(min, MIN_SMOOTH_MAX_TURN_STEP, MAX_SMOOTH_MAX_TURN_STEP);
            this.max = MathHelper.clamp(Math.max(this.min, max), MIN_SMOOTH_MAX_TURN_STEP,
                    MAX_SMOOTH_MAX_TURN_STEP);
        }

        private boolean isRandom() {
            return this.max - this.min > 0.0001F;
        }

        private String toSpec() {
            if (!isRandom()) {
                return formatSmoothMaxTurnStepValue(this.min);
            }
            return formatSmoothMaxTurnStepValue(this.min) + "-" + formatSmoothMaxTurnStepValue(this.max);
        }

        private String toDisplayText() {
            return toSpec();
        }
    }

    private static int sampleAttackSequenceDelayTicks() {
        return TickRangeSpec.sample(cfg().attackSequenceDelayTicksSpec, cfg().attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
    }

    public static void setAttackSequenceDelayTicksSpec(String spec) {
        TickRangeSpec.Range range = TickRangeSpec.parse(spec, cfg().attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
        cfg().attackSequenceDelayTicks = range.getMin();
        cfg().attackSequenceDelayTicksSpec = range.toSpec();
    }

    public static String getAttackSequenceDelayTicksDisplayText() {
        return TickRangeSpec.parse(cfg().attackSequenceDelayTicksSpec, cfg().attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS).toDisplayText();
    }

    public static String getAttackSequenceDelayTicksSpec() {
        return TickRangeSpec.parse(cfg().attackSequenceDelayTicksSpec, cfg().attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS).toSpec();
    }

    private static float sampleAimOffset(String spec, float fallback) {
        AimOffsetRange range = parseAimOffsetSpec(spec, fallback);
        if (!range.isRandom()) {
            return range.min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(range.min, range.max);
    }

    public static void setAimYawOffsetSpec(String spec) {
        AimOffsetRange range = parseAimOffsetSpec(spec, cfg().aimYawOffset);
        cfg().aimYawOffset = range.min;
        cfg().aimYawOffsetSpec = range.toSpec();
    }

    public static void setAimPitchOffsetSpec(String spec) {
        AimOffsetRange range = parseAimOffsetSpec(spec, cfg().aimPitchOffset);
        cfg().aimPitchOffset = range.min;
        cfg().aimPitchOffsetSpec = range.toSpec();
    }

    public static String getAimYawOffsetDisplayText() {
        return parseAimOffsetSpec(cfg().aimYawOffsetSpec, cfg().aimYawOffset).toDisplayText();
    }

    public static String getAimPitchOffsetDisplayText() {
        return parseAimOffsetSpec(cfg().aimPitchOffsetSpec, cfg().aimPitchOffset).toDisplayText();
    }

    public static String getAimYawOffsetSpec() {
        return parseAimOffsetSpec(cfg().aimYawOffsetSpec, cfg().aimYawOffset).toSpec();
    }

    public static String getAimPitchOffsetSpec() {
        return parseAimOffsetSpec(cfg().aimPitchOffsetSpec, cfg().aimPitchOffset).toSpec();
    }

    private static AimOffsetRange parseAimOffsetSpec(String spec, float fallback) {
        float safeFallback = MathHelper.clamp(Float.isNaN(fallback) ? DEFAULT_AIM_OFFSET : fallback,
                MIN_AIM_OFFSET, MAX_AIM_OFFSET);
        String normalized = spec == null ? "" : spec.trim();
        if (normalized.isEmpty()) {
            return new AimOffsetRange(safeFallback, safeFallback);
        }

        normalized = normalized.replace("°", "")
                .replace("，", ",")
                .replace("－", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("～", "~")
                .replace("至", "~")
                .replace("到", "~")
                .trim();

        Matcher matcher = Pattern
                .compile("^([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*(?:[-~]\\s*([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)))?$")
                .matcher(normalized);
        try {
            if (matcher.matches()) {
                float first = Float.parseFloat(matcher.group(1));
                String secondText = matcher.group(2);
                if (secondText == null || secondText.trim().isEmpty()) {
                    float value = MathHelper.clamp(first, MIN_AIM_OFFSET, MAX_AIM_OFFSET);
                    return new AimOffsetRange(value, value);
                }
                float second = Float.parseFloat(secondText);
                float min = MathHelper.clamp(Math.min(first, second), MIN_AIM_OFFSET, MAX_AIM_OFFSET);
                float max = MathHelper.clamp(Math.max(first, second), MIN_AIM_OFFSET, MAX_AIM_OFFSET);
                return new AimOffsetRange(min, Math.max(min, max));
            }
        } catch (Exception ignored) {
        }
        return new AimOffsetRange(safeFallback, safeFallback);
    }

    private static String formatAimOffsetValue(float value) {
        float clamped = MathHelper.clamp(value, MIN_AIM_OFFSET, MAX_AIM_OFFSET);
        if (Math.abs(clamped - Math.round(clamped)) < 0.00005F) {
            return String.valueOf(Math.round(clamped));
        }
        return String.format(Locale.ROOT, "%.4f", clamped).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static final class AimOffsetRange {
        private final float min;
        private final float max;

        private AimOffsetRange(float min, float max) {
            this.min = MathHelper.clamp(min, MIN_AIM_OFFSET, MAX_AIM_OFFSET);
            this.max = MathHelper.clamp(Math.max(this.min, max), MIN_AIM_OFFSET, MAX_AIM_OFFSET);
        }

        private boolean isRandom() {
            return this.max - this.min > 0.000001F;
        }

        private String toSpec() {
            if (!isRandom()) {
                return formatAimOffsetValue(this.min);
            }
            return formatAimOffsetValue(this.min) + "-" + formatAimOffsetValue(this.max);
        }

        private String toDisplayText() {
            return toSpec();
        }
    }

    private static final class AimOffsetSample {
        private final float yaw;
        private final float pitch;

        private AimOffsetSample(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private float getTargetSearchRadius() {
        return isHuntEnabled() ? Math.max(cfg().attackRange, cfg().huntRadius) : cfg().attackRange;
    }

    private boolean matchesEnabledTargetGroup(Entity target) {
        if (target instanceof EntityEnderCrystal) {
            return cfg().targetEnderCrystal;
        }
        if (!(target instanceof EntityLivingBase)) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            return ModUtils.isNpcPlayer(target) ? cfg().targetNpcs : cfg().targetPlayers;
        }
        if (isHostileTargetType((EntityLivingBase) target)) {
            return cfg().targetHostile;
        }
        if (isPassiveTargetType((EntityLivingBase) target)) {
            return cfg().targetPassive;
        }
        return false;
    }

    private boolean isHostileTargetType(EntityLivingBase target) {
        if (target == null) {
            return false;
        }
        return target instanceof IMob || target instanceof EntityDragon
                || target.isCreatureType(EnumCreatureType.MONSTER, false);
    }

    private boolean isPassiveTargetType(EntityLivingBase target) {
        if (target == null) {
            return false;
        }
        return target instanceof EntityAnimal || target instanceof EntityAmbientCreature
                || target instanceof EntityWaterMob || target instanceof EntityVillager || target instanceof EntityGolem
                || target.isCreatureType(EnumCreatureType.CREATURE, false)
                || target.isCreatureType(EnumCreatureType.AMBIENT, false)
                || target.isCreatureType(EnumCreatureType.WATER_CREATURE, false);
    }

    private boolean isPacketAttackMode() {
        return ATTACK_MODE_PACKET.equalsIgnoreCase(cfg().attackMode);
    }

    private boolean isTeleportAttackMode() {
        return ATTACK_MODE_TELEPORT.equalsIgnoreCase(cfg().attackMode);
    }

    private boolean isSequenceAttackMode() {
        return ATTACK_MODE_SEQUENCE.equalsIgnoreCase(cfg().attackMode);
    }

    private boolean isMouseClickAttackMode() {
        return ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(cfg().attackMode);
    }

    private boolean shouldRotateToTarget() {
        return rt().aimOnlyMode || (!isPacketAttackMode() && cfg().rotateToTarget);
    }

    /** TP 出手要不要额外带一个 Rotation 包。Rotation 也走 processPlayer、
     *  计入服务端的 i，出手密集时会把 i 顶过 5，让每 tick 的位移额度从 100*i
     *  跌回 100 —— 等于用一半位移速度换一个服务端并不校验的朝向。 */
    private boolean shouldSendTeleportRotationPacket() {
        return isTeleportAttackMode() ? cfg().teleportAttackRotationPackets : shouldRotateToTarget();
    }

    private boolean shouldRotateOnlyOnAttack() {
        return cfg().rotateOnlyOnAttack && !rt().aimOnlyMode && shouldRotateToTarget();
    }

    private boolean shouldUseRelockOnlyWhenNoCrosshairTarget() {
        return cfg().relockOnlyWhenNoCrosshairTarget && shouldRotateToTarget();
    }

    private static boolean shouldRequireAttackLineOfSight() {
        return cfg().requireLineOfSight && !cfg().throughWallAttack;
    }

    private boolean isSameEntity(EntityLivingBase left, EntityLivingBase right) {
        return left != null && right != null && left.getEntityId() == right.getEntityId();
    }

    private boolean canTriggerAttackSequence(EntityPlayerSP player, EntityLivingBase target) {
        return canTriggerAttackSequence(player, target, null);
    }

    private boolean canTriggerAttackSequence(EntityPlayerSP player, EntityLivingBase target,
            AreaHuntOptions areaOptions) {
        RuntimeState rt = rt();
        if (player == null || target == null) {
            return false;
        }
        if (shouldYieldAttackToItemUse(player)) return false;
        if (rt().sequenceCooldownTicks > 0 || this.attackSequenceExecutor.isRunning()) {
            return false;
        }
        if (!hasConfiguredAttackSequence()) {
            return false;
        }
        if (shouldRequireCrosshairHitForAttack(false)
                && !isViewRayHittingAttackableTarget(player, target, areaOptions)) {
            return false;
        }
        return isValidTarget(player, target, areaOptions);
    }

    private boolean triggerAttackSequence(EntityPlayerSP player, EntityLivingBase target) {
        String sequenceName = getConfiguredAttackSequenceName();
        if (sequenceName.isEmpty()) {
            return false;
        }

        PathSequence configuredSequence = PathSequenceManager.getSequence(sequenceName);
        if (configuredSequence == null || configuredSequence.getSteps().isEmpty()) {
            return false;
        }

        releaseAttackBlocking();
        rememberBlockingAttack(player, target);
        this.attackSequenceExecutor.start(configuredSequence, player, target);
        return this.attackSequenceExecutor.isRunning();
    }

    private static boolean hasConfiguredAttackSequence() {
        String sequenceName = getConfiguredAttackSequenceName();
        return !sequenceName.isEmpty() && PathSequenceManager.hasSequence(sequenceName);
    }

    public static String getConfiguredAttackSequenceName() {
        return cfg().attackSequenceName == null ? "" : cfg().attackSequenceName.trim();
    }

    private KillAuraOrbitProcess getKillAuraOrbitProcess() {
        try {
            Object primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (primary instanceof Baritone) {
                return ((Baritone) primary).getKillAuraOrbitProcess();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean requestHuntOrbitProcess(EntityLivingBase target, int nowTick) {
        RuntimeState rt = rt();
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess == null || target == null) {
            return false;
        }
        double radius = getEffectiveHuntFixedDistance();
        boolean sameTarget = target.getEntityId() == rt().lastOrbitProcessTargetEntityId;
        boolean sameRadius = !Double.isNaN(rt().lastOrbitProcessRequestedRadius)
                && Math.abs(rt().lastOrbitProcessRequestedRadius - radius) <= 0.001D;
        boolean shouldRefreshRequest = !orbitProcess.isActive()
                || !sameTarget
                || !sameRadius
                || nowTick - rt().lastOrbitProcessRequestTick >= HUNT_ORBIT_PROCESS_REQUEST_INTERVAL_TICKS;
        if (shouldRefreshRequest) {
            rt().lastOrbitProcessRequestTick = nowTick;
            rt().lastOrbitProcessTargetEntityId = target.getEntityId();
            rt().lastOrbitProcessRequestedRadius = radius;
            return orbitProcess.requestOrbit(target, radius);
        }
        return orbitProcess.hasUsableLoop();
    }

    private boolean isHuntOrbitProcessActive() {
        if (!isHuntOrbitEnabled()) {
            return false;
        }
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        return orbitProcess != null && orbitProcess.hasUsableLoop();
    }

    private void stopHuntOrbitProcess() {
        RuntimeState rt = rt();
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess != null) {
            orbitProcess.requestStop();
        }
        rt().lastOrbitProcessRequestTick = -99999;
        rt().lastOrbitProcessTargetEntityId = Integer.MIN_VALUE;
        rt().lastOrbitProcessRequestedRadius = Double.NaN;
    }

    private void renderHuntOrbitLoop() {
        RuntimeState rt = rt();
        if (!isHuntOrbitEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (ctxWorld() == null || rt().currentTargetEntityId == -1) {
            return;
        }
        Entity entity = ctxWorld().getEntityByID(rt().currentTargetEntityId);
        if (!(entity instanceof EntityLivingBase) || !entity.isEntityAlive()) {
            return;
        }
        List<Vec3d> renderLoop = null;
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess != null) {
            List<Vec3d> processLoop = orbitProcess.getRenderedLoopView();
            if (processLoop != null && processLoop.size() >= 2) {
                renderLoop = processLoop;
            }
        }
        if (renderLoop == null || renderLoop.size() < 2) {
            renderLoop = HuntOrbitController.buildPreviewLoop((EntityLivingBase) entity,
                    getEffectiveHuntFixedDistance(), getConfiguredHuntOrbitSamplePoints());
        }
        if (renderLoop.size() < 2) {
            return;
        }
        PathRenderer.drawPolyline(renderLoop, new Color(0xFF3B30), 0.95F, 3.0F, true);
    }

    private void handleHuntMovement(EntityPlayerSP player, EntityLivingBase target) {
        RuntimeState rt = rt();
        if (player == null || target == null) {
            stopHuntNavigation();
            return;
        }
        if (isHuntOrbitEnabled() && shouldBlockOrbitNavigationWhileAirborne(player)) {
            stopHuntNavigation();
            return;
        }

        // player.ticksExisted is frozen on a session bot's own tick thread
        // (the entity is driven by BotTicker, not world.updateEntities), so
        // use a per-owner logical counter that advances once per hunt tick.
        int nowTick = ++rt().huntLogicalTick;
        if (isHuntOrbitEnabled()) {
            if (this.huntOrbitController.isActive() && canStartOrbitHunt(player, target)) {
                stopEmbeddedHuntNavigation();
                stopHuntOrbitProcess();
                driveContinuousHuntOrbit(player, target);
                return;
            }

            boolean orbitProcessActive = requestHuntOrbitProcess(target, nowTick);
            if (orbitProcessActive) {
                stopEmbeddedHuntNavigation();
                if (shouldUseContinuousOrbitController(player, target)) {
                    stopHuntOrbitProcess();
                    driveContinuousHuntOrbit(player, target);
                } else {
                    this.huntOrbitController.stop();
                }
                return;
            }

            this.huntOrbitController.stop();
        }

        int targetId = target.getEntityId();
        double dx = target.posX - rt().lastHuntTargetX;
        double dz = target.posZ - rt().lastHuntTargetZ;
        double movedSq = dx * dx + dz * dz;
        boolean targetChanged = targetId != rt().lastHuntTargetEntityId;
        boolean targetMoved = movedSq >= HUNT_GOTO_MOVE_THRESHOLD_SQ;
        // Small target jiggle shouldn't tear down an in-flight path: the
        // executor pauses for the recalc and the new calc often starts a
        // backtrack wait before the bot has even moved. Only a meaningful
        // shift of the CURRENT goal merits a fresh dispatch.
        if (targetMoved && !targetChanged) {
            double gdx = target.posX - rt().lastHuntGoalX;
            double gdz = target.posZ - rt().lastHuntGoalZ;
            targetMoved = (gdx * gdx + gdz * gdz) >= HUNT_GOAL_REDISPATCH_DIST_SQ;
        }
        boolean previousGoalReached = !targetChanged && !targetMoved && hasReachedLastHuntGoal(player);

        // Watchdog: huntNavigationActive is bookkeeping, not proof the bot is
        // moving. PathExecutor's clearKeys pauses (edge-of-loaded-chunks,
        // best-path-backtrack) return early EVERY tick before the movement
        // timeout accumulates, and setGoalAndPath silently keeps the old path
        // when it returns false — either way the bot freezes while navActive
        // stays true and no new goal is ever sent. Track feet motion whenever
        // nav is "active"; standing still too long → hard reset and let the
        // anchor fallback dispatch a fresh goal this same tick.
        int stuckTicks = 0;
        if (rt.huntNavigationActive) {
            double fdx = player.posX - rt().lastHuntFeetX;
            double fdz = player.posZ - rt().lastHuntFeetZ;
            // lastHuntFeet starts NaN so the first comparison is false and the
            // move-tick never updates — treat uninitialized as "just moved" so
            // the watchdog doesn't fire a phantom stuck=100001 on first nav.
            if (Double.isNaN(rt().lastHuntFeetX) || (fdx * fdx + fdz * fdz) > HUNT_STUCK_FEET_EPS_SQ) {
                rt().lastHuntFeetX = player.posX;
                rt().lastHuntFeetZ = player.posZ;
                rt().lastHuntMoveTick = nowTick;
            }
            stuckTicks = nowTick - rt().lastHuntMoveTick;
            if (stuckTicks >= HUNT_STUCK_RESET_TICKS) {
                hunt$dbg("stuck-reset stuck=%d why=%s feet=(%.1f,%.1f) lastGoal=(%.1f,%.1f)",
                        stuckTicks, dbg$pathingFlags(player), player.posX, player.posZ,
                        rt().lastHuntGoalX, rt().lastHuntGoalZ);
                EmbeddedNavigationHandler.INSTANCE.cancelPathingOnly();
                rt().huntNavigationActive = false;
                rt().lastHuntGotoTick = -99999;
                rt().lastHuntMoveTick = nowTick;
                previousGoalReached = false;
            }
        }

        // Do not let the previous target's landing point block a new target.
        // This is especially visible when that point is a stair/snow column and
        // Baritone resolves it to the player's current logical feet node.
        if (rt.huntNavigationActive && !previousGoalReached && !targetChanged && !targetMoved) {
            boolean pathingOrCalc = EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating();
            if ((nowTick % 20) == 0) {
                hunt$dbg("stallchk pathingOrCalc=%s dt=%d pend=%s stuck=%d why=%s",
                        pathingOrCalc, nowTick - rt().lastHuntGotoTick,
                        rt().pendingHuntDestination != null, stuckTicks,
                        dbg$pathingFlags(player));
            }
            if (!pathingOrCalc && (nowTick - rt().lastHuntGotoTick) >= HUNT_GOTO_INTERVAL_TICKS) {
                // Baritone dropped the path (calc failed / executor finished
                // without reaching the goal). Re-dispatching the same anchor
                // here just loops on the same unreachable node — drop
                // navActive so the next tick takes the shouldSendGoto branch,
                // which submits the async stand-point solve and walks the
                // cheap anchor only until that lands.
                hunt$dbg("navdrop dt=%d feet=(%.1f,%.1f) lastGoal=(%.1f,%.1f)",
                        nowTick - rt().lastHuntGotoTick, player.posX, player.posZ,
                        rt().lastHuntGoalX, rt().lastHuntGoalZ);
                rt().huntNavigationActive = false;
            }
            return;
        }

        boolean shouldSendGoto = !rt.huntNavigationActive
                || previousGoalReached
                || targetChanged
                || targetMoved;

        if (shouldSendGoto) {
            // Consume a destination already solved by the async worker, then
            // dispatch it. We never run the ~350-block/rayTrace stand-point
            // scan inline on the render thread — that's the stall that froze
            // the client every time the hunt target moved.
            double[] ready = rt().pendingHuntDestination;
            if (ready != null) {
                rt().pendingHuntDestination = null;
                if (rt().pendingHuntTargetEntityId == targetId) {
                    dispatchHuntNavigationGoal(target, ready[0], ready[1], ready[2], true, nowTick);
                    return;
                }
            }
            Future<double[]> inFlight = rt().pendingHuntSolve;
            // Throttle re-submits: without this, a fast-solving worker lets the
            // next tick immediately queue another ~350-block scan even though
            // the bot is already walking the last destination. A half-second
            // floor keeps solver CPU flat under 15+ bots.
            boolean cooldownOk = (nowTick - rt().lastHuntSolveSubmitTick) >= HUNT_SOLVE_MIN_INTERVAL_TICKS;
            if ((inFlight == null || inFlight.isDone() || inFlight.isCancelled()) && cooldownOk) {
                rt().lastHuntSolveSubmitTick = nowTick;
                hunt$dbg("submit solve target=%d nowTick=%d", targetId, nowTick);
                submitHuntSolve(player, target, nowTick);
            }
            // Until the solve lands, keep walking a cheap direct anchor so the
            // hunt never stalls waiting on the worker.
            if (!rt().huntNavigationActive) {
                double[] anchor = computeFixedDistanceHuntDestination(player, target);
                hunt$dbg("anchor=(%.1f,%.1f,%.1f) blocked=%s", anchor[0], target.posY, anchor[2],
                        isBlockedHuntNavigationDestination(target, anchor[0], target.posY, anchor[2]));
                if (!isBlockedHuntNavigationDestination(target, anchor[0], target.posY, anchor[2])) {
                    dispatchHuntNavigationGoal(target, anchor[0], target.posY, anchor[2], false, nowTick);
                }
            }
        }
    }

    /** Submit the heavy stand-point solve to the hunt worker. Runs the same
     *  find* destination logic but off the render thread, bound to the same
     *  owner session so ctxWorld()/ctxPlayer() resolve to the right bot. */
    private void submitHuntSolve(EntityPlayerSP player, EntityLivingBase target, int nowTick) {
        final Object session = com.mythos.mythosScriptMod.mcp.NexusSessions.boundSession();
        final boolean fixedDistance = isHuntFixedDistanceMode();
        final int targetId = target.getEntityId();
        final EntityPlayerSP playerRef = player;
        final EntityLivingBase targetRef = target;
        final String owner = com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner();
        RuntimeState rt = rt();
        rt().pendingHuntTargetEntityId = targetId;
        shrinkHuntSolvers();
        rt().pendingHuntSolve = huntSolverFor(owner).submit(() -> {
            // The worker thread carries NO NexusCtx binding — without bind(),
            // boundWorld()/boundPlayer()/ctxWorld() inside the solver resolve to
            // the FOREGROUND world (mc.world fallback), so the stand-point
            // rayTrace ran against the wrong bot's world copy. Bind the bot's
            // session for the duration of the callable.
            bindSessionForWorker(session);
            try {
                double[] solved = fixedDistance
                        ? findFixedDistanceHuntNavigationDestination(playerRef, targetRef)
                        : findApproachHuntNavigationDestination(playerRef, targetRef);
                // Must use the captured rt — rt() on this worker thread resolves
                // currentTickOwner() to "" / the mounted session, so the solved
                // destination silently landed on the WRONG RuntimeState and the
                // bot's pendingHuntDestination stayed null forever (frozen hunt).
                rt.pendingHuntDestination = solved;
                hunt$dbg("solver done session=%s target=%d solved=(%.1f,%.1f,%.1f)",
                        session == null ? "null" : session.toString(), targetId,
                        solved == null ? Double.NaN : solved[0],
                        solved == null ? Double.NaN : solved[1],
                        solved == null ? Double.NaN : solved[2]);
                return solved;
            } catch (Throwable t) {
                rt.pendingHuntDestination = null;
                hunt$dbg("solver fail session=%s target=%d err=%s",
                        session == null ? "null" : session.toString(), targetId, t);
                return null;
            } finally {
                unbindSessionForWorker(session);
            }
        });
    }

    private void bindSessionForWorker(Object session) {
        if (session == null) return;
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            c.getMethod("bind", Class.forName("com.mythos.nexus.session.BotSession")).invoke(null, session);
        } catch (Throwable t) {
            hunt$dbg("bind worker session fail: %s", t);
        }
    }

    private void unbindSessionForWorker(Object session) {
        if (session == null) return;
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            c.getMethod("unbind").invoke(null);
        } catch (Throwable ignored) {}
    }

    private boolean hasReachedLastHuntGoal(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null || !rt().huntNavigationActive
                || Double.isNaN(rt().lastHuntGoalX)
                || Double.isNaN(rt().lastHuntGoalZ)
                || (rt().lastHuntGoalUsesY && Double.isNaN(rt().lastHuntGoalY))) {
            return false;
        }

        BetterBlockPos logicalFeet = null;
        if (rt().lastHuntGoalUsesY) {
            // Match Baritone's logical feet node on slabs/stairs instead of
            // using the entity's raw fractional posY. The latter can differ
            // by a full block on partial collision surfaces.
            logicalFeet = getHuntPlayerFeet(player);
            if (logicalFeet != null
                    && logicalFeet.getX() == MathHelper.floor(rt().lastHuntGoalX)
                    && logicalFeet.getZ() == MathHelper.floor(rt().lastHuntGoalZ)) {
                // Goal normalization may move a partial-surface goal one node
                // upward. Keep the original arrival tolerance in logical Y.
                return Math.abs(logicalFeet.getY() - MathHelper.floor(rt().lastHuntGoalY)) <= 1;
            }
        }

        double dx = player.posX - rt().lastHuntGoalX;
        double dz = player.posZ - rt().lastHuntGoalZ;
        double horizontalDistanceSq = dx * dx + dz * dz;
        if (horizontalDistanceSq > HUNT_GOAL_REACHED_TOLERANCE_SQ) {
            return false;
        }

        if (!rt().lastHuntGoalUsesY) {
            // GoalXZ deliberately has no vertical component. Comparing its
            // cached Y with the target's entity Y made stair/snow transitions
            // look like an unfinished route forever.
            return true;
        }

        if (logicalFeet != null) {
            return Math.abs(logicalFeet.getY() - MathHelper.floor(rt().lastHuntGoalY)) <= 1;
        }

        // Keep a conservative fallback for the short period before Baritone's
        // player context is available (world load or teardown).
        double dy = Math.abs(player.posY - rt().lastHuntGoalY);
        return dy <= 1.25D;
    }

    private BetterBlockPos getHuntPlayerFeet(EntityPlayerSP player) {
        if (player == null) {
            return null;
        }
        // feet node must come from THIS player — going through
        // getPrimaryBaritone().getPlayerContext() returns the *mounted*
        // session's feet, so a background bot's hunt compares the target
        // against another player's position and never converges.
        return BetterBlockPos.from(new BlockPos(player));
    }

    private boolean isSameLogicalFeetNode(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            return false;
        }
        BetterBlockPos feet = getHuntPlayerFeet(player);
        if (feet == null) {
            return false;
        }
        return feet.getX() == MathHelper.floor(target.posX)
                && feet.getY() == MathHelper.floor(target.posY)
                && feet.getZ() == MathHelper.floor(target.posZ);
    }

    private boolean dispatchHuntNavigationGoal(Entity target, double goalX, double goalY, double goalZ,
            boolean goalUsesY, int nowTick) {
        if (target == null || Double.isNaN(goalX) || Double.isNaN(goalZ)
                || (goalUsesY && Double.isNaN(goalY))) {
            return false;
        }

        boolean dispatched = goalUsesY
                ? EmbeddedNavigationHandler.INSTANCE.startGoto(goalX, goalY, goalZ, true)
                : EmbeddedNavigationHandler.INSTANCE.startGotoXZ(goalX, goalZ, true);
        if (dispatched) {
            recordHuntNavigationDispatch(target, goalX, goalY, goalZ, goalUsesY, nowTick);
        }
        return dispatched;
    }

    private void recordHuntNavigationDispatch(Entity target, double goalX, double goalY, double goalZ,
            boolean goalUsesY, int nowTick) {
        RuntimeState rt = rt();
        if (target == null) {
            return;
        }
        rt().huntNavigationActive = true;
        rt().lastHuntGotoTick = nowTick;
        rt().lastHuntTargetEntityId = target.getEntityId();
        rt().lastHuntTargetX = target.posX;
        rt().lastHuntTargetZ = target.posZ;
        rt().lastHuntGoalX = goalX;
        rt().lastHuntGoalY = goalY;
        rt().lastHuntGoalZ = goalZ;
        rt().lastHuntGoalUsesY = goalUsesY;
    }

    private void temporarilyExcludeUnreachableHuntTarget(EntityLivingBase target, int nowTick) {
        RuntimeState rt = rt();
        if (target == null) {
            return;
        }
        HuntUnreachableTracker tracker = rt().huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            tracker = new HuntUnreachableTracker(target);
            rt().huntUnreachableTrackers.put(target.getEntityId(), tracker);
        }
        tracker.refreshTargetPosition(target);
        tracker.failedGoalCount = 0;
        tracker.excludeUntilTick = nowTick + HUNT_UNREACHABLE_TEMP_EXCLUDE_TICKS;
        pruneHuntUnreachableTrackingSize();
        mythosScriptMod.LOGGER.info("杀戮光环追击目标暂时排除: id={}, name={}, untilTick={}",
                target.getEntityId(), getFilterableEntityName(target), tracker.excludeUntilTick);
        stopHuntNavigation();
        if (rt().currentTargetEntityId == target.getEntityId()) {
            rt().currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            this.attackSequenceExecutor.stop();
        }
    }

    private Entity findHuntPriorityPickupItem(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null || player.world == null || !isHuntEnabled() || cfg().huntRadius <= 0.05F) {
            return null;
        }

        // Logical per-owner tick — session bots' ticksExisted never advance.
        int nowTick = ++rt().huntLogicalTick;
        if (nowTick < rt.huntPickupRetryAfterTick) {
            return null;
        }
        double radiusSq = cfg().huntRadius * cfg().huntRadius;
        if (nowTick - rt.lastHuntPickupSearchTick < HUNT_PICKUP_SEARCH_INTERVAL_TICKS) {
            Entity cached = resolveCachedHuntPickupItem(player, radiusSq);
            if (cached != null) {
                return cached;
            }
            if (!rt.lastHuntPickupSearchFound) {
                return null;
            }
        }

        Entity bestItem = null;
        double bestDistSq = Double.MAX_VALUE;
        int bestPriority = Integer.MIN_VALUE;
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();

        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(Math.min(cfg().huntRadius, 128.0D)))) {
            if (!DroppedPickupTarget.isSupported(entity)) {
                continue;
            }

            if (!DroppedPickupTarget.isAlive(entity)) {
                continue;
            }

            double playerDistSq = player.getDistanceSq(entity);
            if (playerDistSq > radiusSq) {
                continue;
            }

            HuntPickupRuleDecision decision = evaluateHuntPickupItem(player, entity, Math.sqrt(playerDistSq),
                    hasAllowRules);
            if (!decision.allowed) {
                continue;
            }

            if (decision.priority > bestPriority
                    || (decision.priority == bestPriority && playerDistSq < bestDistSq)) {
                bestPriority = decision.priority;
                bestDistSq = playerDistSq;
                bestItem = entity;
            }
        }

        rt.lastHuntPickupSearchTick = nowTick;
        rt.lastHuntPickupSearchTargetEntityId = bestItem == null ? Integer.MIN_VALUE : bestItem.getEntityId();
        rt.lastHuntPickupSearchFound = bestItem != null;
        return bestItem;
    }

    private HuntPickupRuleDecision evaluateHuntPickupItem(EntityPlayerSP player, Entity item,
            double playerDistance, boolean hasAllowRules) {
        if (item == null || !DroppedPickupTarget.isAlive(item)) {
            return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
        }

        if (!DroppedPickupTarget.isExperienceOrb(item)
                && DroppedPickupTarget.getItemStack(item).isEmpty()) {
            return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
        }

        List<HuntPickupRule> rules = cfg().huntPickupRules == null ? new ArrayList<HuntPickupRule>() : cfg().huntPickupRules;
        if (rules.isEmpty()) {
            return new HuntPickupRuleDecision(true, 0);
        }

        String rarity = DroppedPickupTarget.isExperienceOrb(item) ? HUNT_PICKUP_RARITY_COMMON
                : normalizeHuntPickupRarityToken(getHuntPickupRarityToken(DroppedPickupTarget.getItemStack(item)));
        boolean allowMatched = false;
        int bestAllowPriority = Integer.MIN_VALUE;

        for (HuntPickupRule rule : rules) {
            if (!isMatchingHuntPickupRule(item, rule, rarity, playerDistance)) {
                continue;
            }
            if (HUNT_PICKUP_RULE_MODE_BLOCK.equals(rule.mode)) {
                return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
            }
            allowMatched = true;
            bestAllowPriority = Math.max(bestAllowPriority, rule.priority);
        }

        if (hasAllowRules && !allowMatched) {
            return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
        }
        return new HuntPickupRuleDecision(true, allowMatched ? bestAllowPriority : 0);
    }

    /** Applies the current Kill Aura pickup rules to an item used by another action. */
    public synchronized boolean matchesHuntPickupRulesForAction(ItemStack stack, double playerDistance) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        List<HuntPickupRule> rules = cfg().huntPickupRules == null
                ? new ArrayList<HuntPickupRule>() : cfg().huntPickupRules;
        if (rules.isEmpty()) {
            return true;
        }
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();
        String rarity = normalizeHuntPickupRarityToken(getHuntPickupRarityToken(stack));
        boolean allowMatched = false;
        for (HuntPickupRule rule : rules) {
            if (!isMatchingHuntPickupRuleStack(stack, rule, rarity, playerDistance)) {
                continue;
            }
            if (HUNT_PICKUP_RULE_MODE_BLOCK.equals(rule.mode)) {
                return false;
            }
            allowMatched = true;
        }
        return !hasAllowRules || allowMatched;
    }

    /** Applies the current Kill Aura pickup rules to an item or experience orb. */
    public synchronized boolean matchesHuntPickupRulesForAction(Entity target, double playerDistance) {
        if (target == null || !DroppedPickupTarget.isSupported(target)
                || !DroppedPickupTarget.isEligibleForScan(target)) {
            return false;
        }
        if (!DroppedPickupTarget.isExperienceOrb(target)
                && DroppedPickupTarget.getItemStack(target).isEmpty()) {
            return false;
        }
        List<HuntPickupRule> rules = cfg().huntPickupRules == null
                ? new ArrayList<HuntPickupRule>() : cfg().huntPickupRules;
        if (rules.isEmpty()) {
            return true;
        }
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();
        String rarity = DroppedPickupTarget.isExperienceOrb(target) ? HUNT_PICKUP_RARITY_COMMON
                : normalizeHuntPickupRarityToken(getHuntPickupRarityToken(DroppedPickupTarget.getItemStack(target)));
        boolean allowMatched = false;
        for (HuntPickupRule rule : rules) {
            if (!isMatchingHuntPickupTarget(target, rule, rarity, playerDistance)) {
                continue;
            }
            if (HUNT_PICKUP_RULE_MODE_BLOCK.equals(rule.mode)) {
                return false;
            }
            allowMatched = true;
        }
        return !hasAllowRules || allowMatched;
    }

    private boolean hasEnabledHuntPickupAllowRules() {
        if (cfg().huntPickupRules == null || cfg().huntPickupRules.isEmpty()) {
            return false;
        }
        for (HuntPickupRule rule : cfg().huntPickupRules) {
            if (rule != null && rule.enabled && !HUNT_PICKUP_RULE_MODE_BLOCK.equals(rule.mode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMatchingHuntPickupRule(Entity item, HuntPickupRule rule, String rarity,
            double playerDistance) {
        return isMatchingHuntPickupTarget(item, rule, rarity, playerDistance);
    }

    private boolean isMatchingHuntPickupTarget(Entity target, HuntPickupRule rule, String rarity,
            double playerDistance) {
        if (rule == null || !rule.enabled || target == null || !DroppedPickupTarget.isSupported(target)) {
            return false;
        }
        if (rule.maxDistance > 0.0F && playerDistance - rule.maxDistance > 0.0001D) {
            return false;
        }
        if (DroppedPickupTarget.isExperienceOrb(target)) {
            if (rule.itemFilterExpressions == null || rule.itemFilterExpressions.isEmpty()) {
                return true;
            }
            for (String expression : rule.itemFilterExpressions) {
                if (expression == null || expression.trim().isEmpty()) {
                    continue;
                }
                try {
                    if (InventoryItemFilterExpressionEngine.matches(target, expression, rarity, playerDistance)) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                }
            }
            return false;
        }
        return isMatchingHuntPickupRuleStack(DroppedPickupTarget.getItemStack(target), rule, rarity,
                playerDistance);
    }

    private boolean isMatchingHuntPickupRuleStack(ItemStack stack, HuntPickupRule rule, String rarity,
            double playerDistance) {
        if (rule == null || !rule.enabled) {
            return false;
        }
        if (rule.maxDistance > 0.0F && playerDistance - rule.maxDistance > 0.0001D) {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (rule.itemFilterExpressions == null || rule.itemFilterExpressions.isEmpty()) {
            return true;
        }
        for (String expression : rule.itemFilterExpressions) {
            if (expression == null || expression.trim().isEmpty()) {
                continue;
            }
            try {
                if (InventoryItemFilterExpressionEngine.matches(stack, -1, expression, rarity, playerDistance)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private String getHuntPickupRarityToken(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return HUNT_PICKUP_RARITY_COMMON;
        }
        EnumRarity rarity = stack.getRarity();
        if (rarity == EnumRarity.UNCOMMON) {
            return HUNT_PICKUP_RARITY_UNCOMMON;
        }
        if (rarity == EnumRarity.RARE) {
            return HUNT_PICKUP_RARITY_RARE;
        }
        if (rarity == EnumRarity.EPIC) {
            return HUNT_PICKUP_RARITY_EPIC;
        }
        return HUNT_PICKUP_RARITY_COMMON;
    }

    private Entity resolveCachedHuntPickupItem(EntityPlayerSP player, double radiusSq) {
        RuntimeState rt = rt();
        if (player == null || player.world == null || rt.lastHuntPickupSearchTargetEntityId == Integer.MIN_VALUE) {
            return null;
        }
        Entity entity = player.world.getEntityByID(rt.lastHuntPickupSearchTargetEntityId);
        if (!DroppedPickupTarget.isSupported(entity)) {
            return null;
        }
        if (!DroppedPickupTarget.isAlive(entity)) {
            return null;
        }
        double distanceSq = player.getDistanceSq(entity);
        if (distanceSq > radiusSq) {
            return null;
        }
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();
        HuntPickupRuleDecision decision = evaluateHuntPickupItem(player, entity, Math.sqrt(distanceSq), hasAllowRules);
        return decision.allowed ? entity : null;
    }

    private void handleHuntPickupMovement(EntityPlayerSP player, Entity item) {
        RuntimeState rt = rt();
        if (player == null || item == null || item.isDead) {
            stopHuntPickupNavigation();
            return;
        }

        // Logical per-owner tick — session bots' ticksExisted never advance.
        int nowTick = ++rt().huntLogicalTick;
        int itemId = item.getEntityId();
        if (hasReachedHuntPickupItem(player, item)) {
            // This deliberately mirrors AutoPickupHandler: once within its
            // pickup radius, release navigation and wait for the server-side
            // item entity to disappear. Restarting a forced GoalBlock route
            // here repeatedly cancelled the player before vanilla collection.
            if (rt().huntPickupReachedEntityId != itemId) {
                EmbeddedNavigationHandler.INSTANCE.stop();
            }
            rt().huntPickupNavigationActive = false;
            rt().lastHuntPickupTargetEntityId = itemId;
            rt().huntPickupReachedEntityId = itemId;
            rt().huntPickupLastProgressTick = nowTick;
            return;
        }
        rt().huntPickupReachedEntityId = Integer.MIN_VALUE;

        double distanceSq = player.getDistanceSq(item);
        if (rt().huntPickupNavigationActive && itemId == rt().lastHuntPickupTargetEntityId
                && distanceSq + HUNT_PICKUP_PROGRESS_EPSILON_SQ < rt().huntPickupLastDistanceSq) {
            rt().huntPickupLastDistanceSq = distanceSq;
            rt().huntPickupLastProgressTick = nowTick;
        }
        boolean stalled = rt().huntPickupNavigationActive
                && itemId == rt().lastHuntPickupTargetEntityId
                && nowTick - rt().huntPickupLastProgressTick >= HUNT_PICKUP_NAVIGATION_STALL_TICKS;
        int gotoInterval = item instanceof EntityItem && item.onGround
                ? HUNT_PICKUP_GOTO_INTERVAL_TICKS : HUNT_PICKUP_AIRBORNE_GOTO_INTERVAL_TICKS;
        boolean shouldSendGoto = !rt.huntPickupNavigationActive
                || itemId != rt().lastHuntPickupTargetEntityId
                || stalled
                || (nowTick - rt().lastHuntPickupGotoTick) >= gotoInterval;
        if (!shouldSendGoto) {
            return;
        }

        if (stalled) {
            EmbeddedNavigationHandler.INSTANCE.stop();
        }
        boolean dispatched = dispatchHuntPickupGoto(item);
        if (dispatched) {
            rt().huntPickupNavigationActive = true;
            rt().lastHuntPickupGotoTick = nowTick;
            rt().huntPickupLastProgressTick = nowTick;
            rt().huntPickupLastDistanceSq = distanceSq;
            rt().lastHuntPickupTargetEntityId = itemId;
            rt().huntPickupRetryAfterTick = -99999;
        } else if (rt().huntPickupNavigationActive
                && itemId == rt().lastHuntPickupTargetEntityId
                && EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()) {
            // The shared manager intentionally uses the normal goto throttle.
            // A throttled refresh must not discard the still-running route.
            rt().lastHuntPickupGotoTick = nowTick;
        } else {
            // Do not leave a false active state behind when the navigation layer
            // rejects a request (for example while another command is throttled).
            rt().huntPickupNavigationActive = false;
            rt().lastHuntPickupGotoTick = -99999;
            rt().lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
            rt().huntPickupRetryAfterTick = nowTick + HUNT_PICKUP_RETRY_WAIT_TICKS;
        }
    }

    private boolean dispatchHuntPickupGoto(Entity item) {
        if (item == null) {
            return false;
        }
        if (DroppedPickupTarget.isExperienceOrb(item)) {
            return EmbeddedNavigationHandler.INSTANCE.startGotoXZ(item.posX, item.posZ);
        }
        if (!(item instanceof EntityItem)) {
            return false;
        }
        EntityItem itemEntity = (EntityItem) item;
        if (itemEntity.onGround) {
            // Reuse the automatic pickup manager's exact local-goal resolver.
            // It navigates to a passable feet cell (or its direct upper cell),
            // rather than the item entity's often-unwalkable raw Y coordinate.
            return AutoPickupHandler.INSTANCE.startNavigationToPickupItem(itemEntity);
        }

        // An airborne ItemEntity does not provide a useful standable Y goal.
        // Approach its short-term horizontal landing trajectory, then vanilla
        // collision picks it up as soon as the item reaches the player.
        double goalX = itemEntity.posX + itemEntity.motionX * HUNT_PICKUP_AIRBORNE_LEAD_TICKS;
        double goalZ = itemEntity.posZ + itemEntity.motionZ * HUNT_PICKUP_AIRBORNE_LEAD_TICKS;
        return EmbeddedNavigationHandler.INSTANCE.startGotoXZ(goalX, goalZ);
    }

    private boolean hasReachedHuntPickupItem(EntityPlayerSP player, Entity item) {
        if (player == null || item == null || item.isDead) {
            return false;
        }

        // Keep this consistent with the automatic pickup rule manager's
        // default target reach distance.
        return player.getDistanceSq(item) < 0.25D;
    }

    private static void hunt$dbg(String fmt, Object... a) {
        try {
            if (!huntLogTried) {
                huntLogTried = true;
                huntLogBaritoneM = Class.forName("com.mythos.nexus.core.NexusLog")
                        .getMethod("baritone", String.class, Object[].class);
            }
            if (huntLogBaritoneM != null) {
                huntLogBaritoneM.invoke(null, "[hunt] " + String.format(fmt, a), new Object[0]);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean huntLogTried;
    private static java.lang.reflect.Method huntLogBaritoneM;

    /** isCurrentViewEntity is protected — call it reflectively, cached. */
    private static boolean dbg$isCurrentViewEntity(EntityPlayerSP player) {
        try {
            if (dbgIsViewM == null && !dbgIsViewTried) {
                dbgIsViewTried = true;
                for (String n : new String[]{"isCurrentViewEntity", "func_175161_o", "K"}) {
                    try {
                        java.lang.reflect.Method m = EntityPlayerSP.class.getDeclaredMethod(n);
                        m.setAccessible(true);
                        dbgIsViewM = m;
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                hunt$dbg("isView method resolved = %s",
                        dbgIsViewM == null ? "NONE" : dbgIsViewM.getName());
            }
            return dbgIsViewM != null && (Boolean) dbgIsViewM.invoke(player);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean dbgIsViewTried;
    private static java.lang.reflect.Method dbgIsViewM;

    /** Direct probe of the rewritten path: call NexusCtx.getRenderViewEntity
     *  and check identity vs the hunt player. If the transformer rerouted
     *  isCurrentViewEntity's virtual call, both must agree. */
    private static boolean dbg$rveSame(EntityPlayerSP player) {
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            java.lang.reflect.Method m = c.getMethod("getRenderViewEntity",
                    net.minecraft.client.Minecraft.class);
            Object rve = m.invoke(null, net.minecraft.client.Minecraft.getMinecraft());
            return rve == player;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String dbg$rveName() {
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            java.lang.reflect.Method m = c.getMethod("getRenderViewEntity",
                    net.minecraft.client.Minecraft.class);
            Object rve = m.invoke(null, net.minecraft.client.Minecraft.getMinecraft());
            return rve == null ? "null"
                    : rve.getClass().getSimpleName() + "#" + System.identityHashCode(rve);
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    /** Is the SESSION baritone pathing (executor alive) + what inputs does
     *  its InputOverrideHandler currently force.  Uses NexusTickBridge's own
     *  session resolution so we see the exact same instance PathExecutor
     *  feeds. */
    private static String dbg$isPathing(EntityPlayerSP player) {
        try {
            Object b = dbg$sessionBaritone();
            if (b == null) return "no-baritone";
            Object pb = b.getClass().getMethod("getPathingBehavior").invoke(b);
            return String.valueOf(pb.getClass().getMethod("isPathing").invoke(pb));
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    private static String dbg$forcedInputs(EntityPlayerSP player) {
        try {
            Object b = dbg$sessionBaritone();
            if (b == null) return "no-baritone";
            Object ioh = b.getClass().getMethod("getInputOverrideHandler").invoke(b);
            java.lang.reflect.Method m = ioh.getClass().getMethod("isInputForcedDown",
                    com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input.class);
            StringBuilder sb = new StringBuilder();
            for (com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input in
                    : com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input.values()) {
                if ((Boolean) m.invoke(ioh, in)) sb.append(in).append('+');
            }
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    /** Which of the five isPathingOrCalculating() flags is holding. */
    private static String dbg$pathingFlags(EntityPlayerSP player) {
        try {
            Object b = dbg$sessionBaritone();
            if (b == null) return "no-baritone";
            Object pb = b.getClass().getMethod("getPathingBehavior").invoke(b);
            StringBuilder sb = new StringBuilder();
            if ((Boolean) pb.getClass().getMethod("isPathing").invoke(pb)) sb.append("isPathing ");
            if ((Boolean) pb.getClass().getMethod("hasPath").invoke(pb)) sb.append("hasPath ");
            java.util.Optional<?> ip = (java.util.Optional<?>) pb.getClass()
                    .getMethod("getInProgress").invoke(pb);
            if (ip != null && ip.isPresent()) sb.append("inProgress:").append(ip.get().getClass().getSimpleName()).append(' ');
            try {
                if ((Boolean) pb.getClass().getMethod("isFlightWaitingForContinuation").invoke(pb)) sb.append("flightWait ");
            } catch (Throwable ignored) {}
            try {
                if ((Boolean) pb.getClass().getMethod("isFlightTerminalReached").invoke(pb)) sb.append("flightTerm ");
            } catch (Throwable ignored) {}
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    private static Object dbg$sessionBaritone() {
        try {
            String sess = dbg$nexusCur();
            if (sess == null || "null".equals(sess) || sess.startsWith("ERR")) {
                // Client thread — hunt may be running for the MOUNTED bot.
                sess = com.mythos.mythosScriptMod.mcp.NexusSessions.mountedBotUsername();
            }
            if (sess == null || "null".equals(sess) || sess.startsWith("ERR")) return null;
            Object provider = com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI.getProvider();
            if (!(provider instanceof com.mythos.mythosScriptMod.shadowbaritone.BaritoneProvider)) return null;
            final String name = sess;
            return ((com.mythos.mythosScriptMod.shadowbaritone.BaritoneProvider) provider)
                    .sessionBaritone(name,
                            () -> com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer(),
                            () -> com.mythos.mythosScriptMod.mcp.NexusSessions.boundWorld());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String dbg$nexusCur() {
        try {
            Class<?> c = Class.forName("com.mythos.nexus.session.NexusCtx");
            java.lang.reflect.Method m = c.getMethod("current");
            Object s = m.invoke(null);
            return s == null ? "null"
                    : String.valueOf(s.getClass().getField("username").get(s));
        } catch (Throwable t) {
            return "ERR:" + t;
        }
    }

    private void stopHuntNavigation() {
        this.huntOrbitController.stop();
        stopHuntOrbitProcess();
        stopEmbeddedHuntNavigation();
        // Drop any async solve still in flight / already solved so a stale
        // destination can't be dispatched after the hunt was cancelled.
        RuntimeState rt = rt();
        rt().pendingHuntDestination = null;
        rt().pendingHuntTargetEntityId = Integer.MIN_VALUE;
        Future<double[]> pending = rt().pendingHuntSolve;
        if (pending != null) {
            pending.cancel(false);
            rt().pendingHuntSolve = null;
        }
    }

    private void stopEmbeddedHuntNavigation() {
        RuntimeState rt = rt();
        if (!rt().huntNavigationActive) {
            return;
        }
        // cancelPathingOnly, not stop(): stop() also kills the owning path
        // sequence via executeBuiltinStop (builtin-baritone mode), which is
        // what made the hunt action die one tick after it started.
        EmbeddedNavigationHandler.INSTANCE.cancelPathingOnly();
        rt().huntNavigationActive = false;
        rt().lastHuntGotoTick = -99999;
        rt().lastHuntTargetEntityId = Integer.MIN_VALUE;
        rt().lastHuntTargetX = 0.0D;
        rt().lastHuntTargetZ = 0.0D;
        rt().lastHuntGoalX = Double.NaN;
        rt().lastHuntGoalY = Double.NaN;
        rt().lastHuntGoalZ = Double.NaN;
        rt().lastHuntGoalUsesY = false;
    }

    private void stopHuntPickupNavigation() {
        RuntimeState rt = rt();
        if (!rt().huntPickupNavigationActive && rt().lastHuntPickupTargetEntityId == Integer.MIN_VALUE) {
            return;
        }
        // cancelPathingOnly, not stop(): see stopEmbeddedHuntNavigation.
        EmbeddedNavigationHandler.INSTANCE.cancelPathingOnly();
        rt().huntPickupNavigationActive = false;
        rt().lastHuntPickupGotoTick = -99999;
        rt().lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
        rt().huntPickupReachedEntityId = Integer.MIN_VALUE;
        rt().huntPickupLastProgressTick = -99999;
        rt().huntPickupLastDistanceSq = Double.MAX_VALUE;
        rt().huntPickupRetryAfterTick = -99999;
    }

    public boolean isHuntPickupNavigationActive() {
        RuntimeState rt = rt();
        return rt().huntPickupNavigationActive;
    }

    private boolean shouldRunHuntMovement(EntityPlayerSP player, EntityLivingBase target) {
        if (isTeleportAttackMode() || !isHuntEnabled() || player == null || target == null) {
            hunt$dbg("why=%s result=false mode=%s enabled=%s huntEnabled=%s player=%s target=%s",
                    isTeleportAttackMode() ? "teleport-attack" : !isHuntEnabled() ? "hunt-disabled"
                            : player == null ? "player-null" : "target-null",
                    cfg().huntMode, rt().enabled, rt().huntEnabled, player == null ? "null" : player.getName(),
                    target == null ? "null" : target.getEntityId());
            return false;
        }
        if (isHuntOrbitEnabled() && shouldBlockOrbitNavigationWhileAirborne(player)) {
            hunt$dbg("why=orbit-airborne result=false mode=%s player=%s target=%s onGround=%s",
                    cfg().huntMode, player.getName(), target.getEntityId(), player.onGround);
            return false;
        }

        double distance = player.getDistance(target);
        BetterBlockPos feet = getHuntPlayerFeet(player);
        // Background-hunt debug: pin down which link of the movement chain is
        // dead. miClass/miFwd = what the input object itself holds (baritone
        // forcing); moveForward/moveStrafing = what updateEntityActionState
        // copied into the entity (isCurrentViewEntity gate); onGround/motion
        // = whether travel() ever ran.
        net.minecraft.util.MovementInput mi = player.movementInput;
        hunt$dbg("feet=%s target=(%.1f,%.1f,%.1f) dist=%.2f attackRange=%.2f fixedDist=%.2f "
                        + "mi=%s miFwd=%.2f miStrafe=%.2f fwd=%.2f strafe=%.2f jump=%s onGround=%s "
                        + "mot=(%.3f,%.3f,%.3f) viewEnt=%s rveSame=%s rve=%s nexusCur=%s "
                        + "isPathing=%s forced=%s",
                feet == null ? "null" : feet.toString(),
                target.posX, target.posY, target.posZ, distance, cfg().attackRange,
                getEffectiveHuntFixedDistance(),
                mi == null ? "null" : mi.getClass().getSimpleName(),
                mi == null ? -9.9f : mi.moveForward, mi == null ? -9.9f : mi.moveStrafe,
                player.moveForward, player.moveStrafing, player.isSprinting(),
                player.onGround, player.motionX, player.motionY, player.motionZ,
                dbg$isCurrentViewEntity(player),
                dbg$rveSame(player), dbg$rveName(), dbg$nexusCur(),
                dbg$isPathing(player), dbg$forcedInputs(player));
        boolean sameFeetNode = feet != null
                && feet.getX() == MathHelper.floor(target.posX)
                && feet.getY() == MathHelper.floor(target.posY)
                && feet.getZ() == MathHelper.floor(target.posZ);
        boolean missingAttackLineOfSight = shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target);
        if (isHuntFixedDistanceMode()) {
            if (canStartOrbitHunt(player, target)) {
                hunt$dbg("why=orbit-start result=true mode=%s player=%s target=%s dist=%.3f",
                        cfg().huntMode, player.getName(), target.getEntityId(), distance);
                return true;
            }
            // Once pathing is in motion don't hold the bot to the exact ring
            // distance — the goal the solver picks can drift off the dead-
            // reckoned ring point and re-solving a new "1.0-away" point every
            // tick creates a stop/go loop. Let the active goto land, then
            // shouldRunHuntMovement flips false only when we really are close.
            boolean farBeyondRange = distance > Math.max(cfg().attackRange + 2.0D,
                    getEffectiveHuntFixedDistance() + HUNT_FIXED_DISTANCE_TOLERANCE);
            boolean result = missingAttackLineOfSight
                    || (!rt().huntNavigationActive
                            && Math.abs(distance - getEffectiveHuntFixedDistance()) > HUNT_FIXED_DISTANCE_TOLERANCE)
                    || farBeyondRange;
            hunt$dbg("why=%s result=%s mode=%s player=%s target=%s dist=%.3f fixedDist=%.3f tolerance=%.3f missingLos=%s navActive=%s farBeyond=%s",
                    missingAttackLineOfSight ? "fixed-missing-los" : "fixed-distance-band", result,
                    cfg().huntMode, player.getName(), target.getEntityId(), distance,
                    getEffectiveHuntFixedDistance(), HUNT_FIXED_DISTANCE_TOLERANCE, missingAttackLineOfSight,
                    rt().huntNavigationActive, farBeyondRange);
            return result;
        }
        boolean result = missingAttackLineOfSight
                || (!sameFeetNode && distance > cfg().attackRange)
                || (rt().huntNavigationActive && "true".equals(dbg$isPathing(player)) && distance <= cfg().attackRange + 1.0D);
        hunt$dbg("why=%s result=%s mode=%s player=%s target=%s dist=%.3f attackRange=%.3f sameFeet=%s missingLos=%s navActive=%s pathing=%s",
                missingAttackLineOfSight ? "approach-missing-los" : sameFeetNode ? "approach-same-feet"
                        : distance > cfg().attackRange ? "approach-out-of-range" : "approach-in-range", result,
                cfg().huntMode, player.getName(), target.getEntityId(), distance, cfg().attackRange,
                sameFeetNode, missingAttackLineOfSight, rt().huntNavigationActive, dbg$isPathing(player));
        return result;
    }

    private boolean canStartOrbitHunt(EntityPlayerSP player, EntityLivingBase target) {
        if (!isHuntOrbitEnabled() || player == null || target == null) {
            return false;
        }
        if (shouldBlockOrbitNavigationWhileAirborne(player)) {
            return false;
        }
        if (Math.abs(player.posY - target.posY) > HUNT_ORBIT_MAX_ENTRY_VERTICAL_DELTA) {
            return false;
        }
        double maxEntryDistance = getEffectiveHuntFixedDistance() + HUNT_CONTINUOUS_ORBIT_ENTRY_BUFFER;
        double allowedDistance = this.huntOrbitController.isActive()
                ? maxEntryDistance + HUNT_CONTINUOUS_ORBIT_EXIT_BUFFER
                : maxEntryDistance;
        return player.getDistanceSq(target) <= allowedDistance * allowedDistance;
    }

    private boolean shouldUseContinuousOrbitController(EntityPlayerSP player, EntityLivingBase target) {
        if (!canStartOrbitHunt(player, target)) {
            return false;
        }
        return isPlayerOnHuntOrbitLoop(player);
    }

    private boolean isPlayerOnHuntOrbitLoop(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess == null || !orbitProcess.isActive()) {
            return false;
        }
        List<Vec3d> renderLoop = orbitProcess.getRenderedLoopView();
        if (renderLoop == null || renderLoop.size() < 2) {
            return false;
        }
        double distanceToLoop = getHorizontalDistanceToOrbitLoop(player.posX, player.posZ, renderLoop);
        return distanceToLoop <= HUNT_CONTINUOUS_ORBIT_LOOP_ENTRY_MAX_DISTANCE;
    }

    private double getHorizontalDistanceToOrbitLoop(double playerX, double playerZ, List<Vec3d> renderLoop) {
        if (renderLoop == null || renderLoop.size() < 2) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3d playerPos = new Vec3d(playerX, 0.0D, playerZ);
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < renderLoop.size() - 1; i++) {
            Vec3d start = flattenToHorizontal(renderLoop.get(i));
            Vec3d end = flattenToHorizontal(renderLoop.get(i + 1));
            Vec3d nearest = nearestPointOnHorizontalSegment(playerPos, start, end);
            bestDistanceSq = Math.min(bestDistanceSq, playerPos.squareDistanceTo(nearest));
        }
        return bestDistanceSq == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : Math.sqrt(bestDistanceSq);
    }

    private Vec3d nearestPointOnHorizontalSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSq = segment.lengthSquared();
        if (lengthSq <= 1.0E-6D) {
            return start;
        }
        double t = point.subtract(start).dotProduct(segment) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return start.add(segment.scale(t));
    }

    private Vec3d flattenToHorizontal(Vec3d vec) {
        return vec == null ? Vec3d.ZERO : new Vec3d(vec.x, 0.0D, vec.z);
    }

    private boolean shouldBlockOrbitNavigationWhileAirborne(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        return (player.capabilities != null && player.capabilities.isFlying) || player.isElytraFlying();
    }

    private void driveContinuousHuntOrbit(EntityPlayerSP player, EntityLivingBase target) {
        this.huntOrbitController.tick(player, target,
                new HuntOrbitController.OrbitConfig(getEffectiveHuntFixedDistance(), HUNT_FIXED_DISTANCE_TOLERANCE,
                        cfg().huntJumpOrbitEnabled, true, true));
    }

    private double[] computeFixedDistanceHuntDestination(EntityPlayerSP player, EntityLivingBase target) {
        double dx = player.posX - target.posX;
        double dy = player.posY - target.posY;
        double dz = player.posZ - target.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance <= 1.0E-4D) {
            double yawRadians = Math.toRadians(player.rotationYaw);
            dx = -Math.sin(yawRadians);
            dy = 0.0D;
            dz = Math.cos(yawRadians);
            distance = Math.sqrt(dx * dx + dz * dz);
        }

        double desiredDistance = getEffectiveHuntFixedDistance();
        double scale = desiredDistance / Math.max(distance, 1.0E-4D);
        double destinationX = target.posX + dx * scale;
        double destinationY = target.posY + dy * scale;
        double destinationZ = target.posZ + dz * scale;
        double[] clippedDestination = clipHuntDestinationXZ(target.posX, target.posZ, destinationX, destinationZ);
        return new double[] { clippedDestination[0], destinationY, clippedDestination[1] };
    }

    private double[] findApproachHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return null;
        }
        double maxStandRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, cfg().attackRange - HUNT_APPROACH_TARGET_BUFFER);
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS,
                Math.min(maxStandRadius, cfg().attackRange - HUNT_APPROACH_TARGET_BUFFER * 2.0D));
        return findHuntNavigationDestinationAroundTarget(player, target, preferredRadius,
                HUNT_APPROACH_MIN_STAND_RADIUS, maxStandRadius);
    }

    private double[] findHuntScoreDebugDestination(EntityPlayerSP player, EntityLivingBase target,
            double preferredRadius) {
        if (player == null || target == null) {
            return null;
        }
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance <= 1.0E-4D) {
            double yawRadians = Math.toRadians(player.rotationYaw);
            dx = -Math.sin(yawRadians);
            dz = Math.cos(yawRadians);
            horizontalDistance = 1.0D;
        }
        double[] clipped = clipHuntDestinationXZ(target.posX, target.posZ,
                target.posX + dx / horizontalDistance * preferredRadius,
                target.posZ + dz / horizontalDistance * preferredRadius);
        // Keep the debug panel responsive in dense fights. The active target
        // still uses the full angle/layer solver; this is a representative
        // local stand point used only to expose the configured score weights.
        return findSafeHuntNavigationDestination(player, null, clipped[0], player.posY, clipped[1], 1);
    }

    private double[] findFixedDistanceHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return null;
        }
        if (isHuntOrbitEnabled()) {
            double[] orbitAligned = findOrbitAlignedHuntNavigationDestination(player, target);
            if (orbitAligned != null) {
                return orbitAligned;
            }
        }
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance());
        double minRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, preferredRadius - 1.0D);
        double maxRadius = Math.max(minRadius, preferredRadius + 1.0D);
        double[] destination = findHuntNavigationDestinationAroundTarget(player, target, preferredRadius,
                minRadius, maxRadius);
        if (destination != null) {
            return destination;
        }
        double[] fallback = computeFixedDistanceHuntDestination(player, target);
        return findSafeHuntNavigationDestination(player, target, fallback[0], fallback[1], fallback[2]);
    }

    private double[] findOrbitAlignedHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return null;
        }
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess != null) {
            double[] plannedEntry = findOrbitEntryFromLoopNodes(player, target,
                    orbitProcess.getNavigationLoopSnapshot());
            if (plannedEntry != null) {
                orbitDebug("huntOrbitEntry source=planned_nodes dest=%s radius=%.3f", formatVec3(plannedEntry),
                        getHorizontalRadiusToTarget(target, plannedEntry));
                return plannedEntry;
            }
            double[] renderedEntry = findOrbitEntryFromRenderLoop(player, target, orbitProcess.getRenderedLoopView());
            if (renderedEntry != null) {
                orbitDebug("huntOrbitEntry source=process_render dest=%s radius=%.3f", formatVec3(renderedEntry),
                        getHorizontalRadiusToTarget(target, renderedEntry));
                return renderedEntry;
            }
        }

        double[] previewEntry = findOrbitEntryFromRenderLoop(player, target,
                HuntOrbitController.buildPreviewLoop(target, getEffectiveHuntFixedDistance(),
                        getConfiguredHuntOrbitSamplePoints()));
        if (previewEntry != null) {
            orbitDebug("huntOrbitEntry source=preview_render dest=%s radius=%.3f", formatVec3(previewEntry),
                    getHorizontalRadiusToTarget(target, previewEntry));
            return previewEntry;
        }

        double[] exactOrbitPoint = computeFixedDistanceHuntDestination(player, target);
        double[] directSafeDestination = findSafeHuntNavigationDestination(player, target, exactOrbitPoint[0],
                exactOrbitPoint[1], exactOrbitPoint[2], HUNT_ORBIT_ENTRY_SAFE_SEARCH_RADIUS);
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance());
        double orbitBand = Math.max(HUNT_FIXED_DISTANCE_TOLERANCE, HUNT_ORBIT_ENTRY_RADIUS_BAND);
        if (directSafeDestination != null
                && isDestinationNearOrbitBand(target, directSafeDestination, preferredRadius, orbitBand)
                && centerDistSq(directSafeDestination[0], directSafeDestination[2], exactOrbitPoint[0],
                        exactOrbitPoint[2]) <= 0.65D * 0.65D) {
            orbitDebug("huntOrbitEntry source=exact_safe dest=%s radius=%.3f exact=%s",
                    formatVec3(directSafeDestination),
                    getHorizontalRadiusToTarget(target, directSafeDestination), formatVec3(exactOrbitPoint));
            return directSafeDestination;
        }

        orbitDebug("huntOrbitEntry source=exact_xz dest=%s radius=%.3f", formatVec3(exactOrbitPoint),
                getHorizontalRadiusToTarget(target, exactOrbitPoint));
        return null;
    }

    private double[] findOrbitEntryFromLoopNodes(EntityPlayerSP player, EntityLivingBase target,
            List<BetterBlockPos> loopNodes) {
        if (player == null || target == null || loopNodes == null || loopNodes.isEmpty()) {
            return null;
        }
        double[] bestVisibleDestination = null;
        double bestVisibleScore = Double.POSITIVE_INFINITY;
        double[] bestFallbackDestination = null;
        double bestFallbackScore = Double.POSITIVE_INFINITY;
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance());
        double orbitBand = Math.max(HUNT_FIXED_DISTANCE_TOLERANCE, HUNT_ORBIT_ENTRY_RADIUS_BAND);

        for (BetterBlockPos node : loopNodes) {
            if (node == null) {
                continue;
            }
            double[] destination = new double[] { node.x + 0.5D, node.y, node.z + 0.5D };
            if (isBlockedHuntNavigationDestination(target, destination[0], destination[1], destination[2])) {
                continue;
            }
            if (!isDestinationNearOrbitBand(target, destination, preferredRadius, orbitBand + 0.35D)) {
                continue;
            }
            BlockPos standPos = new BlockPos(node.x, node.y, node.z);
            boolean hasLineOfSight = hasHuntLineOfSightFromStandPos(standPos, target);
            double score = scoreHuntNavigationDestination(player, target, destination, preferredRadius, hasLineOfSight);
            if (hasLineOfSight && score < bestVisibleScore) {
                bestVisibleScore = score;
                bestVisibleDestination = destination;
            }
            if (score < bestFallbackScore) {
                bestFallbackScore = score;
                bestFallbackDestination = destination;
            }
        }

        return bestVisibleDestination != null ? bestVisibleDestination : bestFallbackDestination;
    }

    private double[] findOrbitEntryFromRenderLoop(EntityPlayerSP player, EntityLivingBase target,
            List<Vec3d> renderLoop) {
        if (player == null || target == null || renderLoop == null || renderLoop.isEmpty()) {
            return null;
        }
        double[] bestVisibleDestination = null;
        double bestVisibleScore = Double.POSITIVE_INFINITY;
        double[] bestFallbackDestination = null;
        double bestFallbackScore = Double.POSITIVE_INFINITY;
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance());
        double orbitBand = Math.max(HUNT_FIXED_DISTANCE_TOLERANCE, HUNT_ORBIT_ENTRY_RADIUS_BAND);
        int candidateCount = getOrbitRenderCandidateCount(renderLoop);

        for (int i = 0; i < candidateCount; i++) {
            Vec3d point = renderLoop.get(i);
            if (point == null) {
                continue;
            }
            double[] safeDestination = findSafeHuntNavigationDestination(player, target, point.x, target.posY, point.z,
                    HUNT_ORBIT_ENTRY_SAFE_SEARCH_RADIUS);
            if (safeDestination == null) {
                continue;
            }
            if (!isDestinationNearOrbitBand(target, safeDestination, preferredRadius, orbitBand)) {
                continue;
            }
            if (centerDistSq(safeDestination[0], safeDestination[2], point.x,
                    point.z) > HUNT_ORBIT_ENTRY_POINT_TOLERANCE * HUNT_ORBIT_ENTRY_POINT_TOLERANCE) {
                continue;
            }

            BlockPos standPos = new BlockPos(safeDestination[0], safeDestination[1], safeDestination[2]);
            boolean hasLineOfSight = hasHuntLineOfSightFromStandPos(standPos, target);
            double score = scoreOrbitEntryDestination(player, target, safeDestination, point, preferredRadius,
                    hasLineOfSight);
            if (hasLineOfSight && score < bestVisibleScore) {
                bestVisibleScore = score;
                bestVisibleDestination = safeDestination;
            }
            if (score < bestFallbackScore) {
                bestFallbackScore = score;
                bestFallbackDestination = safeDestination;
            }
        }

        return bestVisibleDestination != null ? bestVisibleDestination : bestFallbackDestination;
    }

    private int getOrbitRenderCandidateCount(List<Vec3d> renderLoop) {
        if (renderLoop == null || renderLoop.isEmpty()) {
            return 0;
        }
        if (renderLoop.size() >= 2 && renderLoop.get(0) != null && renderLoop.get(renderLoop.size() - 1) != null
                && renderLoop.get(0).squareDistanceTo(renderLoop.get(renderLoop.size() - 1)) <= 1.0E-4D) {
            return renderLoop.size() - 1;
        }
        return renderLoop.size();
    }

    private double[] findHuntNavigationDestinationAroundTarget(EntityPlayerSP player, EntityLivingBase target,
            double preferredRadius, double minRadius, double maxRadius) {
        return findHuntNavigationDestinationAroundTarget(player, target, preferredRadius, minRadius, maxRadius, true,
                2);
    }

    private double[] findHuntNavigationDestinationAroundTarget(EntityPlayerSP player, EntityLivingBase target,
            double preferredRadius, double minRadius, double maxRadius, boolean allowCenterFallback,
            int safeSearchRadius) {
        if (player == null || player.world == null || target == null) {
            return null;
        }

        double clampedMinRadius = Math.max(0.0D, minRadius);
        double clampedPreferredRadius = Math.max(clampedMinRadius, preferredRadius);
        double clampedMaxRadius = Math.max(clampedPreferredRadius, maxRadius);
        double[] bestVisibleDestination = null;
        double bestVisibleScore = Double.POSITIVE_INFINITY;
        double[] bestFallbackDestination = null;
        double bestFallbackScore = Double.POSITIVE_INFINITY;
        double baseAngle = Math.atan2(player.posZ - target.posZ, player.posX - target.posX);

        for (double radius : buildHuntRadiusSamples(clampedPreferredRadius, clampedMinRadius, clampedMaxRadius)) {
            for (int angleIndex = 0; angleIndex <= HUNT_NAVIGATION_ANGLE_SAMPLE_PAIRS * 2; angleIndex++) {
                double angleOffset;
                if (angleIndex == 0) {
                    angleOffset = 0.0D;
                } else {
                    int ringIndex = (angleIndex + 1) / 2;
                    angleOffset = ringIndex * HUNT_NAVIGATION_ANGLE_SAMPLE_STEP_RADIANS;
                    if ((angleIndex & 1) == 0) {
                        angleOffset = -angleOffset;
                    }
                }

                double desiredX = target.posX + Math.cos(baseAngle + angleOffset) * radius;
                double desiredZ = target.posZ + Math.sin(baseAngle + angleOffset) * radius;
                double[] clippedDestination = clipHuntDestinationXZ(target.posX, target.posZ, desiredX, desiredZ);
                double[] safeDestination = findSafeHuntNavigationDestination(player, target, clippedDestination[0],
                        target.posY, clippedDestination[1], safeSearchRadius);
                if (safeDestination == null) {
                    continue;
                }
                if (!isMeaningfulHuntNavigationDestination(player, target, safeDestination, clampedPreferredRadius)) {
                    continue;
                }

                BlockPos standPos = new BlockPos(safeDestination[0], safeDestination[1], safeDestination[2]);
                boolean hasLineOfSight = hasHuntLineOfSightFromStandPos(standPos, target);
                double score = scoreHuntNavigationDestination(player, target, safeDestination, clampedPreferredRadius,
                        hasLineOfSight);
                if (hasLineOfSight && score < bestVisibleScore) {
                    bestVisibleScore = score;
                    bestVisibleDestination = safeDestination;
                }
                if (score < bestFallbackScore) {
                    bestFallbackScore = score;
                    bestFallbackDestination = safeDestination;
                }
            }
        }

        if (bestVisibleDestination != null) {
            return bestVisibleDestination;
        }
        if (bestFallbackDestination != null) {
            return bestFallbackDestination;
        }
        if (!allowCenterFallback) {
            return null;
        }
        return findSafeHuntNavigationDestination(player, target, target.posX, target.posY, target.posZ);
    }

    private List<Double> buildHuntRadiusSamples(double preferredRadius, double minRadius, double maxRadius) {
        List<Double> samples = new ArrayList<>();
        addHuntRadiusSample(samples, preferredRadius, minRadius, maxRadius);
        double maxOffset = Math.max(preferredRadius - minRadius, maxRadius - preferredRadius);
        for (double offset = HUNT_NAVIGATION_RADIUS_SAMPLE_STEP; offset <= maxOffset
                + 1.0E-4D; offset += HUNT_NAVIGATION_RADIUS_SAMPLE_STEP) {
            addHuntRadiusSample(samples, preferredRadius - offset, minRadius, maxRadius);
            addHuntRadiusSample(samples, preferredRadius + offset, minRadius, maxRadius);
        }
        addHuntRadiusSample(samples, minRadius, minRadius, maxRadius);
        addHuntRadiusSample(samples, maxRadius, minRadius, maxRadius);
        return samples;
    }

    private void addHuntRadiusSample(List<Double> samples, double radius, double minRadius, double maxRadius) {
        if (samples == null) {
            return;
        }
        double clamped = MathHelper.clamp(radius, minRadius, maxRadius);
        for (Double existing : samples) {
            if (existing != null && Math.abs(existing - clamped) <= 1.0E-4D) {
                return;
            }
        }
        samples.add(clamped);
    }

    private double scoreHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target, double[] destination,
            double preferredRadius, boolean hasLineOfSight) {
        return buildHuntScoreBreakdown(player, target, destination, preferredRadius, hasLineOfSight).totalScore;
    }

    private boolean isMeaningfulHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target,
            double[] destination, double preferredRadius) {
        if (player == null || target == null || destination == null || destination.length < 3) {
            return false;
        }

        double currentDistance = player.getDistance(target);
        boolean orbitNavigation = isHuntFixedDistanceMode() && isHuntOrbitEnabled();
        boolean fixedDistanceNeedsAdjustment = !orbitNavigation && isHuntFixedDistanceMode()
                && Math.abs(currentDistance - preferredRadius) > HUNT_FIXED_DISTANCE_TOLERANCE;
        boolean approachNeedsProgress = !isHuntFixedDistanceMode() && currentDistance > cfg().attackRange;
        boolean missingAttackLineOfSight = shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target);
        if (!orbitNavigation && !fixedDistanceNeedsAdjustment && !approachNeedsProgress && !missingAttackLineOfSight) {
            return true;
        }

        // EmbeddedNavigationHandler floors GoalBlock coordinates. A point can
        // look 0.8 blocks away by centre distance while still resolving to the
        // exact path node currently occupied by the player. Baritone then
        // completes it instantly and KillAura keeps reissuing the same goal.
        if (isHuntGoalAtCurrentPathNode(player, destination)) {
            return false;
        }
        double goalDx = destination[0] - player.posX;
        double goalDz = destination[2] - player.posZ;
        if (goalDx * goalDx + goalDz * goalDz < HUNT_MIN_MEANINGFUL_GOAL_DISTANCE_SQ) {
            return false;
        }

        double destinationDistance = Math.sqrt(getHuntCandidateDistanceSq(target, destination[0], destination[1],
                destination[2]));
        if (approachNeedsProgress) {
            return destinationDistance <= currentDistance - HUNT_MIN_APPROACH_PROGRESS;
        }
        if (fixedDistanceNeedsAdjustment) {
            return Math.abs(destinationDistance - preferredRadius) <= Math.abs(currentDistance - preferredRadius)
                    - HUNT_MIN_APPROACH_PROGRESS;
        }
        // Entering an orbit often requires a lateral move that deliberately
        // keeps the same radius. It only needs to be a real movement goal.
        return true;
    }

    private boolean isHuntGoalAtCurrentPathNode(EntityPlayerSP player, double[] destination) {
        if (player == null || destination == null || destination.length < 3) {
            return false;
        }
        int goalX = MathHelper.floor(destination[0]);
        int goalY = MathHelper.floor(destination[1]);
        int goalZ = MathHelper.floor(destination[2]);
        BetterBlockPos feet = getHuntPlayerFeet(player);
        if (feet != null) {
            return feet.getX() == goalX && feet.getY() == goalY && feet.getZ() == goalZ;
        }
        return MathHelper.floor(player.posX) == goalX
                && MathHelper.floor(player.posY + 0.1251D) == goalY
                && MathHelper.floor(player.posZ) == goalZ;
    }

    private HuntScoreBreakdown buildHuntScoreBreakdown(EntityPlayerSP player, EntityLivingBase target,
            double[] destination, double preferredRadius, boolean hasLineOfSight) {
        if (player == null || target == null || destination == null || destination.length < 3) {
            return new HuntScoreBreakdown(Double.POSITIVE_INFINITY, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false);
        }

        double targetDx = destination[0] - target.posX;
        double targetDz = destination[2] - target.posZ;
        double actualRadius = Math.sqrt(targetDx * targetDx + targetDz * targetDz);
        double radiusPenalty = Math.abs(actualRadius - preferredRadius);
        double playerDx = destination[0] - player.posX;
        double playerDy = destination[1] - player.posY;
        double playerDz = destination[2] - player.posZ;
        double playerDistancePenalty = playerDx * playerDx + playerDz * playerDz;
        double playerHeightPenalty = Math.abs(playerDy);
        double targetHeightPenalty = Math.abs(destination[1] - target.posY);
        double targetDistance = Math.sqrt(getHuntCandidateDistanceSq(target, destination[0], destination[1],
                destination[2]));
        double outsideAttackRangePenalty = Math.max(0.0D, targetDistance - cfg().attackRange);
        double opennessPenalty = 4 - getHuntStandOpenness(new BlockPos(destination[0], destination[1],
                destination[2]));
        // When several floors intersect the attack sphere, stay on the player's
        // current plane whenever that floor can actually see/reach the target.
        // Target-height matching is only a weak tie breaker.
        return new HuntScoreBreakdown(
                radiusPenalty * cfg().huntScoreRadiusWeight,
                playerDistancePenalty * cfg().huntScorePlayerDistanceWeight,
                playerHeightPenalty * cfg().huntScorePlayerPlaneWeight,
                targetHeightPenalty * cfg().huntScoreTargetHeightWeight,
                outsideAttackRangePenalty * cfg().huntScoreAttackRangeWeight,
                hasLineOfSight ? 0.0D : cfg().huntScoreVisibilityWeight,
                opennessPenalty * cfg().huntScoreOpennessWeight,
                hasLineOfSight);
    }

    private double scoreOrbitEntryDestination(EntityPlayerSP player, EntityLivingBase target, double[] destination,
            Vec3d desiredPoint, double preferredRadius, boolean hasLineOfSight) {
        double score = scoreHuntNavigationDestination(player, target, destination, preferredRadius, hasLineOfSight);
        if (destination == null || desiredPoint == null) {
            return score;
        }
        return score + centerDistSq(destination[0], destination[2], desiredPoint.x, desiredPoint.z) * 4.5D;
    }

    private boolean isDestinationNearOrbitBand(EntityLivingBase target, double[] destination, double preferredRadius,
            double orbitBand) {
        if (target == null || destination == null || destination.length < 3) {
            return false;
        }
        double actualRadius = Math.sqrt(centerDistSq(destination[0], destination[2], target.posX, target.posZ));
        return Math.abs(actualRadius - preferredRadius) <= Math.max(0.1D, orbitBand);
    }

    private double getHorizontalRadiusToTarget(EntityLivingBase target, double[] destination) {
        if (target == null || destination == null || destination.length < 3) {
            return 0.0D;
        }
        return Math.sqrt(centerDistSq(destination[0], destination[2], target.posX, target.posZ));
    }

    private boolean isOrbitDebugEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_ORBIT);
    }

    private boolean isTeleportDebugEnabled() {
        return ModConfig.isDebugFlagEnabled(DebugModule.KILL_AURA_TELEPORT);
    }

    private void teleportDebug(EntityPlayerSP player, String event, String format, Object... args) {
        if (!isTeleportDebugEnabled()) {
            return;
        }
        int tick = player == null ? Integer.MIN_VALUE : player.ticksExisted;
        String message;
        try {
            message = args == null || args.length == 0 ? format : String.format(Locale.ROOT, format, args);
        } catch (Exception exception) {
            message = format + " [formatError=" + exception.getClass().getSimpleName() + "]";
        }
        if (rt().teleport.teleportDebugBuffer.size() >= 4096) {
            rt().teleport.teleportDebugBuffer.remove(0);
        }
        rt().teleport.teleportDebugBuffer.add("tick=" + tick + " event=" + event + " " + message);
    }

    private void flushTeleportDebug() {
        if (rt().teleport.teleportDebugBuffer.isEmpty()) {
            return;
        }
        if (!isTeleportDebugEnabled()) {
            rt().teleport.teleportDebugBuffer.clear();
            return;
        }
        List<String> messages = new ArrayList<>(rt().teleport.teleportDebugBuffer);
        rt().teleport.teleportDebugBuffer.clear();
        for (String message : messages) {
            ModConfig.debugLog(DebugModule.KILL_AURA_TELEPORT, message);
        }
    }

    private String formatTeleportTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (target == null) {
            return "target=null";
        }
        double distance = player == null ? -1.0D : Math.sqrt(player.getDistanceSq(target));
        NoDamageAttackTracker tracker = rt().noDamageAttackTrackers.get(target.getEntityId());
        return String.format(Locale.ROOT,
                "id=%d,name=%s,type=%s,hp=%.3f,dead=%s,pos=(%.3f,%.3f,%.3f),distance=%.3f,lastAttackTick=%d,noDamageExcluded=%s,noDamagePending=%d,noDamageConfirmed=%d",
                target.getEntityId(), getFilterableEntityName(target), target.getClass().getSimpleName(),
                target.getHealth(), target.isDead, target.posX, target.posY, target.posZ, distance,
                rt().teleport.teleportLastAttackTicks.getOrDefault(target.getEntityId(), Integer.MIN_VALUE),
                rt().noDamageExcludedEntityIds.contains(target.getEntityId()),
                tracker == null ? 0 : tracker.pendingAttempts,
                tracker == null ? 0 : tracker.confirmedNoDamageAttempts);
    }

    private String formatTeleportTargetIds(List<? extends Entity> targets, int limit) {
        if (targets == null || targets.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int count = Math.min(Math.max(1, limit), targets.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            Entity target = targets.get(i);
            builder.append(target == null ? "null" : target.getEntityId());
        }
        if (targets.size() > count) {
            builder.append(",...+").append(targets.size() - count);
        }
        return builder.append(']').toString();
    }

    private String getTeleportTargetRejectionReason(EntityPlayerSP player, EntityLivingBase target,
            double targetSearchRadiusSq, boolean useWhitelistPriority, AreaHuntOptions areaOptions) {
        if (player == null) {
            return "PLAYER_NULL";
        }
        if (target == null) {
            return "TARGET_NULL";
        }
        if (target == player) {
            return "SELF";
        }
        if (target.isDead || target.getHealth() <= 0.0F) {
            return "DEAD_OR_ZERO_HEALTH";
        }
        if (isNoDamageExcludedTarget(target)) {
            return "NO_DAMAGE_EXCLUDED(limit=" + getNoDamageAttackLimit() + ")";
        }
        if (target instanceof EntityArmorStand) {
            return "ARMOR_STAND";
        }
        if (cfg().ignoreInvisible && target.isInvisible()) {
            return "INVISIBLE";
        }
        if (areaOptions != null && !areaOptions.contains(target)) {
            return "OUTSIDE_AREA";
        }
        if (areaOptions != null && !areaOptions.allows(target)) {
            return "AREA_RULE_REJECTED";
        }
        double distanceSq = getTargetSearchDistanceSq(player, target);
        if (distanceSq > targetSearchRadiusSq) {
            return String.format(Locale.ROOT, "OUT_OF_RANGE(distance=%.3f,range=%.3f)",
                    Math.sqrt(distanceSq), Math.sqrt(targetSearchRadiusSq));
        }
        if (AutoFollowHandler.hasActiveLockChaseRestriction()
                && !AutoFollowHandler.isPositionWithinActiveLockChaseBounds(target.posX, target.posZ)) {
            return "OUTSIDE_LOCK_CHASE_BOUNDS";
        }
        String targetName = getFilterableEntityName(target);
        boolean whitelistMatched = areaOptions != null && areaOptions.shouldTreatAllowedTargetAsWhitelistMatched();
        if (areaOptions == null && cfg().enableNameBlacklist && matchesNameList(targetName, cfg().nameBlacklist)) {
            return "NAME_BLACKLIST";
        }
        if (areaOptions == null && cfg().enableNameWhitelist) {
            int whitelistPriority = getNormalizedNameListMatchIndex(targetName, cfg().nameWhitelist);
            if (whitelistPriority == Integer.MAX_VALUE) {
                return "NAME_WHITELIST_MISS";
            }
            whitelistMatched = true;
        }
        if (!whitelistMatched && !matchesEnabledTargetGroup(target)) {
            return useWhitelistPriority ? "TARGET_GROUP_REJECTED_WITH_WHITELIST_PRIORITY" : "TARGET_GROUP_REJECTED";
        }
        return "UNKNOWN_REJECTION";
    }

    private String getTeleportAttackGateReason(EntityPlayerSP player) {
        RuntimeState rt = rt();
        if (player == null) {
            return "PLAYER_NULL";
        }
        if (rt().aimOnlyMode) {
            return "AIM_ONLY";
        }
        if (isSequenceAttackMode()) {
            return "SEQUENCE_MODE";
        }
        if (rt().attackCooldownTicks > 0) {
            return "ATTACK_COOLDOWN(" + rt().attackCooldownTicks + ")";
        }
        if (isTeleportAttackRecoveryActive()) {
            return "PENDING_RETURN";
        }
        if (rt.teleport.timing.isRouteRejected()) {
            return "SERVER_CORRECTION_ROUTE_SUSPENDED";
        }
        if (cfg().onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
            return "NO_ALLOWED_WEAPON";
        }
        float cooledStrength = getAttackCooledStrength(player);
        if (cooledStrength < cfg().minAttackStrength) {
            return String.format(Locale.ROOT, "ATTACK_STRENGTH(%.3f<%.3f)", cooledStrength, cfg().minAttackStrength);
        }
        return "READY";
    }

    private void orbitDebug(String format, Object... args) {
        if (!isOrbitDebugEnabled()) {
            return;
        }
        ModConfig.debugLog(DebugModule.KILL_AURA_ORBIT, String.format(Locale.ROOT, format, args));
    }

    private String formatVec3(double[] pos) {
        if (pos == null || pos.length < 3) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", pos[0], pos[1], pos[2]);
    }

    private double[] clipHuntDestinationXZ(double centerX, double centerZ, double destinationX, double destinationZ) {
        if (!AutoFollowHandler.hasActiveLockChaseRestriction()
                || AutoFollowHandler.isPositionWithinActiveLockChaseBounds(destinationX, destinationZ)) {
            return new double[] { destinationX, destinationZ };
        }

        double dx = destinationX - centerX;
        double dz = destinationZ - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-4D) {
            return new double[] { centerX, centerZ };
        }

        return getClippedHuntPoint(centerX, centerZ, distance, Math.atan2(dz, dx));
    }

    private double[] findSafeHuntNavigationDestination(EntityPlayerSP player, double desiredX, double desiredY,
            double desiredZ) {
        return findSafeHuntNavigationDestination(player, null, desiredX, desiredY, desiredZ, 2);
    }

    private double[] findSafeHuntNavigationDestination(EntityPlayerSP player, double desiredX, double desiredY,
            double desiredZ, int horizontalSearchRadius) {
        return findSafeHuntNavigationDestination(player, null, desiredX, desiredY, desiredZ, horizontalSearchRadius);
    }

    private double[] findSafeHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target, double desiredX,
            double desiredY, double desiredZ) {
        return findSafeHuntNavigationDestination(player, target, desiredX, desiredY, desiredZ, 2);
    }

    private double[] findSafeHuntNavigationDestination(EntityPlayerSP player, EntityLivingBase target, double desiredX,
            double desiredY, double desiredZ, int horizontalSearchRadius) {
        if (player == null || player.world == null) {
            return null;
        }

        int baseX = MathHelper.floor(desiredX);
        int baseZ = MathHelper.floor(desiredZ);
        int maxHorizontalSearchRadius = Math.max(0, horizontalSearchRadius);
        List<HuntStandCandidate> standCandidates = new ArrayList<>(HUNT_STAND_CANDIDATE_LIMIT);

        // Check the most useful layers first. This is the normal fast path.
        int playerBaseY = target == null ? MathHelper.floor(desiredY) : MathHelper.floor(player.posY);
        int targetBaseY = target == null ? MathHelper.floor(desiredY) : MathHelper.floor(target.posY);
        int layerOrigins = target != null && targetBaseY != playerBaseY ? 2 : 1;
        for (int originIndex = 0; originIndex < layerOrigins; originIndex++) {
            int layerBaseY = originIndex == 0 ? playerBaseY : targetBaseY;
            for (int yOffset : HUNT_STAND_Y_OFFSETS) {
                collectHuntStandCandidates(standCandidates, player, target, baseX, baseZ, layerBaseY + yOffset,
                        desiredX, desiredY, desiredZ, maxHorizontalSearchRadius);
            }
        }

        // If neither nearby plane has a stand point, fall back to every layer
        // intersecting the target's attack sphere. This keeps caves, bridges,
        // and intermediate floors reachable without paying that scan normally.
        if (standCandidates.isEmpty() && target != null) {
            int verticalPadding = Math.max(3, MathHelper.ceil(cfg().attackRange) + 1);
            int minY = MathHelper.floor(target.posY) - verticalPadding;
            int maxY = MathHelper.floor(target.posY) + verticalPadding;
            for (int candidateY = minY; candidateY <= maxY; candidateY++) {
                collectHuntStandCandidates(standCandidates, player, target, baseX, baseZ, candidateY,
                        desiredX, desiredY, desiredZ, maxHorizontalSearchRadius);
            }
        }

        if (standCandidates.isEmpty()) {
            return null;
        }
        HuntStandCandidate best = chooseBestHuntStandCandidate(standCandidates, target);
        if (best == null || best.position == null) {
            return null;
        }
        return new double[] { best.position.getX() + 0.5D, best.position.getY(), best.position.getZ() + 0.5D };
    }

    private void collectHuntStandCandidates(List<HuntStandCandidate> candidates, EntityPlayerSP player,
            EntityLivingBase target, int baseX, int baseZ, int candidateY, double desiredX, double desiredY,
            double desiredZ, int maxHorizontalSearchRadius) {
        if (candidates == null) {
            return;
        }
        for (int radius = 0; radius <= maxHorizontalSearchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = new BlockPos(baseX + dx, candidateY, baseZ + dz);
                    if (!isStandableHuntFeetPos(candidate)) {
                        continue;
                    }

                    double centerX = candidate.getX() + 0.5D;
                    double centerY = candidate.getY();
                    double centerZ = candidate.getZ() + 0.5D;
                    if (isBlockedHuntNavigationDestination(target, centerX, centerY, centerZ)) {
                        continue;
                    }
                    double dxScore = centerX - desiredX;
                    double dyScore = centerY - desiredY;
                    double dzScore = centerZ - desiredZ;
                    double score = dxScore * dxScore + dzScore * dzScore + dyScore * dyScore * 0.12D;
                    if (target != null) {
                        double targetDistance = Math
                                .sqrt(getHuntCandidateDistanceSq(target, centerX, centerY, centerZ));
                        score += Math.abs(centerY - player.posY) * cfg().huntScorePlayerPlaneWeight
                                + Math.abs(centerY - target.posY) * cfg().huntScoreTargetHeightWeight
                                + Math.max(0.0D, targetDistance - cfg().attackRange) * cfg().huntScoreAttackRangeWeight;
                    }
                    insertHuntStandCandidate(candidates, new HuntStandCandidate(candidate, score));
                }
            }
        }
    }

    private void insertHuntStandCandidate(List<HuntStandCandidate> candidates, HuntStandCandidate candidate) {
        if (candidates == null || candidate == null || candidate.position == null) {
            return;
        }
        for (HuntStandCandidate existing : candidates) {
            if (existing != null && candidate.position.equals(existing.position)) {
                return;
            }
        }
        int index = 0;
        while (index < candidates.size() && candidates.get(index).terrainScore <= candidate.terrainScore) {
            index++;
        }
        if (index >= HUNT_STAND_CANDIDATE_LIMIT && candidates.size() >= HUNT_STAND_CANDIDATE_LIMIT) {
            return;
        }
        candidates.add(index, candidate);
        if (candidates.size() > HUNT_STAND_CANDIDATE_LIMIT) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private HuntStandCandidate chooseBestHuntStandCandidate(List<HuntStandCandidate> candidates,
            EntityLivingBase target) {
        HuntStandCandidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (HuntStandCandidate candidate : candidates) {
            if (candidate == null || candidate.position == null) {
                continue;
            }
            double score = candidate.terrainScore;
            if (target != null) {
                boolean visible = hasHuntLineOfSightFromStandPos(candidate.position, target);
                score += visible ? 0.0D : cfg().huntScoreVisibilityWeight * 1.5D;
                score += (4 - getHuntStandOpenness(candidate.position))
                        * (cfg().huntScoreOpennessWeight / DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT) * 0.8D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private boolean hasHuntLineOfSightFromStandPos(BlockPos standPos, EntityLivingBase target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (ctxWorld() == null || standPos == null || target == null) {
            return false;
        }

        Vec3d eyePos = new Vec3d(standPos).addVector(0.5D, 1.62D, 0.5D);
        AxisAlignedBB targetBox = target.getEntityBoundingBox();
        double targetX = (targetBox.minX + targetBox.maxX) * 0.5D;
        double targetZ = (targetBox.minZ + targetBox.maxZ) * 0.5D;
        double[] targetHeights = new double[] {
                targetBox.minY + 0.15D,
                (targetBox.minY + targetBox.maxY) * 0.5D,
                Math.max(targetBox.minY + 0.15D, targetBox.maxY - 0.15D)
        };
        for (double targetY : targetHeights) {
            Vec3d targetPoint = new Vec3d(targetX, targetY, targetZ);
            RayTraceResult ray = ctxWorld().rayTraceBlocks(eyePos, targetPoint, false, true, false);
            if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) {
                return true;
            }
        }
        return false;
    }

    private double getHuntCandidateDistanceSq(EntityLivingBase target, double x, double y, double z) {
        if (target == null) {
            return Double.MAX_VALUE;
        }
        AxisAlignedBB box = target.getEntityBoundingBox();
        double closestX = Math.max(box.minX, Math.min(x, box.maxX));
        double closestY = Math.max(box.minY, Math.min(y, box.maxY));
        double closestZ = Math.max(box.minZ, Math.min(z, box.maxZ));
        double dx = x - closestX;
        double dy = y - closestY;
        double dz = z - closestZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private int getHuntStandOpenness(BlockPos standPos) {
        if (standPos == null || ctxWorld() == null) {
            return 0;
        }
        int openSides = 0;
        BlockPos[] neighbors = new BlockPos[] {
                standPos.north(), standPos.south(), standPos.west(), standPos.east()
        };
        for (BlockPos neighbor : neighbors) {
            IBlockState feetState = ctxWorld().getBlockState(neighbor);
            IBlockState headState = ctxWorld().getBlockState(neighbor.up());
            if (!feetState.getMaterial().blocksMovement() && !headState.getMaterial().blocksMovement()) {
                openSides++;
            }
        }
        return openSides;
    }

    private double centerDistSq(double leftX, double leftZ, double rightX, double rightZ) {
        double dx = leftX - rightX;
        double dz = leftZ - rightZ;
        return dx * dx + dz * dz;
    }

    private double wrapOrbitAngle(double angle) {
        double wrapped = angle;
        while (wrapped <= -Math.PI) {
            wrapped += Math.PI * 2.0D;
        }
        while (wrapped > Math.PI) {
            wrapped -= Math.PI * 2.0D;
        }
        return wrapped;
    }

    private boolean isStandableHuntFeetPos(BlockPos standPos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (ctxWorld() == null || standPos == null) {
            return false;
        }

        IBlockState feetState = ctxWorld().getBlockState(standPos);
        IBlockState headState = ctxWorld().getBlockState(standPos.up());
        IBlockState belowState = ctxWorld().getBlockState(standPos.down());

        boolean feetPassable = !feetState.getMaterial().blocksMovement();
        boolean headPassable = !headState.getMaterial().blocksMovement();
        boolean hasGround = hasStandableTopSurface(ctxWorld(), standPos.down(), belowState);
        return feetPassable && headPassable && hasGround;
    }

    private boolean hasStandableTopSurface(net.minecraft.world.World world, BlockPos supportPos,
            IBlockState supportState) {
        if (world == null || supportPos == null || supportState == null
                || !supportState.getMaterial().blocksMovement()) {
            return false;
        }
        try {
            if (supportState.isSideSolid(world, supportPos, EnumFacing.UP)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        net.minecraft.util.math.AxisAlignedBB collisionBox = supportState.getCollisionBoundingBox(world, supportPos);
        return collisionBox != null
                && collisionBox != net.minecraft.block.Block.NULL_AABB
                && collisionBox.maxY >= 1.0D - 1.0E-4D;
    }

    private int getPreferredAttackHotbarSlot(EntityPlayerSP player) {
        if (player == null) {
            return -1;
        }
        return isHoldingWeapon(player) ? player.inventory.currentItem : -1;
    }

    private boolean isHoldingWeapon(EntityPlayerSP player) {
        if (player == null || player.getHeldItemMainhand().isEmpty()) {
            return false;
        }
        return player.getHeldItemMainhand().getItem() instanceof ItemSword
                || player.getHeldItemMainhand().getItem() instanceof ItemAxe;
    }

    private void applyFullBright(boolean active) {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }

        if (active) {
            if (!rt().fullBrightApplied) {
                // Only record gamma values inside the vanilla [0,1] range —
                // a higher value is another feature's boost (e.g. the render
                // tab's 亮度增强), and restoring it later would re-apply that
                // boost when fullbright toggles off.
                rt().previousGammaSetting = Math.min(1.0F, Math.max(0.0F, mc.gameSettings.gammaSetting));
                rt().fullBrightApplied = true;
            }
            float targetGamma = Math.max(1.0F, cfg().fullBrightGamma);
            if (mc.gameSettings.gammaSetting != targetGamma) {
                mc.gameSettings.gammaSetting = targetGamma;
            }
        } else {
            restoreFullBright();
        }
    }

    private void restoreFullBright() {
        RuntimeState rt = rt();
        Minecraft mc = Minecraft.getMinecraft();
        if (!rt().fullBrightApplied) {
            return;
        }
        if (mc != null && mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = rt().previousGammaSetting;
        }
        rt().fullBrightApplied = false;
    }

    public void applyMovementProtection(EntityPlayerSP player, boolean active, boolean applyNoCollision,
            boolean applyAntiKnockback) {
        RuntimeState rt = rt();
        if (player == null) {
            return;
        }

        if (!active) {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
            rt().lastSafeMotionX = 0.0D;
            rt().lastSafeMotionY = 0.0D;
            rt().lastSafeMotionZ = 0.0D;
            return;
        }

        if (applyNoCollision) {
            player.entityCollisionReduction = 1.0F;
            player.noClip = false;
        } else {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
        }

        if (applyAntiKnockback && player.hurtTime > 0) {
            boolean hasMoveInput = player.movementInput != null && (Math.abs(player.movementInput.moveForward) > 0.01F
                    || Math.abs(player.movementInput.moveStrafe) > 0.01F || player.movementInput.jump
                    || player.movementInput.sneak);
            boolean jumpPressed = player.movementInput != null && player.movementInput.jump;

            if (!hasMoveInput) {
                player.motionX = 0.0D;
                player.motionZ = 0.0D;
                player.velocityChanged = true;
            } else {
                double preservedSpeed = Math.sqrt(rt().lastSafeMotionX * rt().lastSafeMotionX
                        + rt().lastSafeMotionZ * rt().lastSafeMotionZ);
                double[] preservedMotion = resolveProtectionMotion(player, preservedSpeed);
                player.motionX = preservedMotion[0];
                player.motionZ = preservedMotion[1];
                player.velocityChanged = true;
            }

            if (!jumpPressed && player.motionY > 0.0D) {
                player.motionY = Math.min(0.0D, rt().lastSafeMotionY);
                player.velocityChanged = true;
            }
        } else {
            rt().lastSafeMotionX = player.motionX;
            rt().lastSafeMotionY = player.motionY;
            rt().lastSafeMotionZ = player.motionZ;
        }
    }

    private void applyKillAuraOwnMovementProtection(EntityPlayerSP player, boolean active, boolean applyNoCollision,
            boolean applyAntiKnockback) {
        RuntimeState rt = rt();
        if (player == null) {
            return;
        }

        if (!active) {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
            rt().lastSafeMotionX = 0.0D;
            rt().lastSafeMotionY = 0.0D;
            rt().lastSafeMotionZ = 0.0D;
            return;
        }

        if (applyNoCollision) {
            player.entityCollisionReduction = 1.0F;
            player.noClip = false;
        } else {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
        }

        if (applyAntiKnockback && player.hurtTime > 0) {
            boolean hasMoveInput = player.movementInput != null && (Math.abs(player.movementInput.moveForward) > 0.01F
                    || Math.abs(player.movementInput.moveStrafe) > 0.01F || player.movementInput.jump
                    || player.movementInput.sneak);
            boolean jumpPressed = player.movementInput != null && player.movementInput.jump;

            if (!hasMoveInput) {
                player.motionX = 0.0D;
                player.motionZ = 0.0D;
                player.velocityChanged = true;
            } else {
                double preservedSpeed = Math.sqrt(rt().lastSafeMotionX * rt().lastSafeMotionX
                        + rt().lastSafeMotionZ * rt().lastSafeMotionZ);
                double[] preservedMotion = resolveProtectionMotion(player, preservedSpeed);
                player.motionX = preservedMotion[0];
                player.motionZ = preservedMotion[1];
                player.velocityChanged = true;
            }

            if (!jumpPressed && player.motionY > 0.0D) {
                player.motionY = Math.min(0.0D, rt().lastSafeMotionY);
                player.velocityChanged = true;
            }
        } else {
            rt().lastSafeMotionX = player.motionX;
            rt().lastSafeMotionY = player.motionY;
            rt().lastSafeMotionZ = player.motionZ;
        }
    }

    private double[] resolveProtectionMotion(EntityPlayerSP player, double speed) {
        RuntimeState rt = rt();
        if (player == null) {
            return new double[] { 0.0D, 0.0D };
        }
        if (speed <= 1.0E-4D) {
            return new double[] { 0.0D, 0.0D };
        }

        float forward = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        float strafe = player.movementInput == null ? 0.0F : player.movementInput.moveStrafe;
        float yaw = player.rotationYaw;

        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) {
            return new double[] { rt().lastSafeMotionX, rt().lastSafeMotionZ };
        }

        if (forward != 0.0F) {
            if (strafe > 0.0F) {
                yaw += forward > 0.0F ? -45.0F : 45.0F;
            } else if (strafe < 0.0F) {
                yaw += forward > 0.0F ? 45.0F : -45.0F;
            }
            strafe = 0.0F;
            forward = forward > 0.0F ? 1.0F : -1.0F;
        }

        if (strafe > 0.0F) {
            strafe = 1.0F;
        } else if (strafe < 0.0F) {
            strafe = -1.0F;
        }

        double rad = Math.toRadians(yaw + 90.0F);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double motionX = (forward * cos + strafe * sin) * speed;
        double motionZ = (forward * sin - strafe * cos) * speed;
        return new double[] { motionX, motionZ };
    }

    public static List<String> getNearbyEntityNames(float scanRange) {
        return new ArrayList<>(getNearbyEntityNameTypeMap(scanRange).keySet());
    }

    /** 附近实体 名字→类型标签（玩家/NPC/敌对/被动/其他），供 GUI 列表右侧标记。 */
    public static LinkedHashMap<String, String> getNearbyEntityNameTypeMap(float scanRange) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : ctxPlayer();
        if (player == null || ctxWorld() == null) {
            return map;
        }

        float actualRange = MathHelper.clamp(scanRange, 1.0F, 64.0F);
        for (Entity entity : ctxWorld().getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(actualRange))) {
            if (entity == player || entity instanceof EntityArmorStand) {
                continue;
            }
            if (!(entity instanceof EntityLivingBase) && !(entity instanceof EntityEnderCrystal)) {
                continue;
            }
            if (player.getDistance(entity) > actualRange) {
                continue;
            }
            String name = getFilterableEntityName(entity);
            if (!name.isEmpty() && !map.containsKey(name)) {
                map.put(name, ModUtils.entityTypeLabel(entity));
            }
        }

        List<String> names = new ArrayList<>(map.keySet());
        names.sort(String::compareToIgnoreCase);
        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        for (String name : names) {
            sorted.put(name, map.get(name));
        }
        return sorted;
    }

    public static String normalizeFilterName(String rawName) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(rawName);
        String source = stripped == null ? (rawName == null ? "" : rawName) : stripped;
        if (source.isEmpty()) {
            return "";
        }

        StringBuilder visible = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT) {
                continue;
            }
            visible.append(ch);
        }
        return trimUnicodeWhitespace(visible.toString());
    }

    private static String getFilterableEntityName(Entity entity) {
        if (entity == null) {
            return "";
        }
        if (entity instanceof EntityEnderCrystal) {
            String translated = translateEntityNameKey("entity.EnderCrystal.name");
            if (translated.isEmpty()) {
                translated = translateEntityNameKey("entity.endercrystal.name");
            }
            if (!translated.isEmpty()) {
                return translated;
            }
        }

        String displayName = entity.getDisplayName() == null ? "" : entity.getDisplayName().getUnformattedText();
        String normalized = normalizeFilterName(displayName);
        if (looksLikeTranslationKey(normalized) && I18n.hasKey(normalized)) {
            normalized = normalizeFilterName(I18n.format(normalized));
        }
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String fallbackName = normalizeFilterName(entity.getName());
        if (looksLikeTranslationKey(fallbackName) && I18n.hasKey(fallbackName)) {
            fallbackName = normalizeFilterName(I18n.format(fallbackName));
        }
        return fallbackName;
    }

    private static String translateEntityNameKey(String translationKey) {
        String key = normalizeFilterName(translationKey);
        if (key.isEmpty()) {
            return "";
        }
        String translated = net.minecraft.util.text.translation.I18n.translateToLocal(key);
        if (translated == null || translated.trim().isEmpty() || key.equals(translated)) {
            return "";
        }
        return normalizeFilterName(translated);
    }

    private static boolean looksLikeTranslationKey(String value) {
        return value != null
                && !value.isEmpty()
                && value.indexOf('.') >= 0
                && value.chars().noneMatch(Character::isWhitespace);
    }

    private static String trimUnicodeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int start = 0;
        int end = text.length();
        while (start < end && isIgnorableNameBoundary(text.charAt(start))) {
            start++;
        }
        while (end > start && isIgnorableNameBoundary(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isIgnorableNameBoundary(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch) || Character.isISOControl(ch)
                || Character.getType(ch) == Character.FORMAT;
    }

    private static boolean matchesNameList(String entityName, List<String> filters) {
        return getNameListMatchIndex(entityName, filters) != Integer.MAX_VALUE;
    }

    public static int getNameListMatchIndex(String entityName, List<String> filters) {
        String loweredName = normalizeFilterName(entityName).toLowerCase(Locale.ROOT);
        if (loweredName.isEmpty() || filters == null || filters.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return getNormalizedNameListMatchIndex(loweredName, filters);
    }

    private static int getNormalizedNameListMatchIndex(String loweredName, List<String> filters) {
        String normalizedName = normalizeNameFilterKeyword(loweredName);
        if (normalizedName.isEmpty() || filters == null || filters.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        String nameWithoutLevel = stripScannedLevelSuffix(normalizedName);
        for (int i = 0; i < filters.size(); i++) {
            String keyword = normalizeNameFilterKeyword(filters.get(i));
            if (keyword.isEmpty()) {
                continue;
            }
            String keywordWithoutLevel = stripScannedLevelSuffix(keyword);
            if (normalizedName.contains(keyword)
                    || normalizedName.contains(keywordWithoutLevel)
                    || nameWithoutLevel.contains(keyword)
                    || nameWithoutLevel.contains(keywordWithoutLevel)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Entity scanners commonly append a display-only level (for example
     * {@code Lv.23}) to a name. Keep that suffix out of matching so a saved
     * whitelist entry remains stable across changing entity levels.
     */
    private static String stripScannedLevelSuffix(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String stripped = value.replaceFirst("(?i)\\s*(?:lv|level|等级)\\.?\\s*\\d+\\s*$", "");
        return stripped.trim();
    }

    private static List<String> normalizeNameList(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (source != null) {
            for (String entry : source) {
                String normalized = normalizeNameFilterKeyword(entry);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    public static List<NameFilterGroup> copyNameFilterGroups(List<NameFilterGroup> source) {
        List<NameFilterGroup> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (NameFilterGroup group : source) {
            if (group == null) {
                continue;
            }
            String name = group.name == null ? "" : group.name.trim();
            copied.add(new NameFilterGroup(name, group.enabled, group.blacklist, normalizeNameList(group.names)));
        }
        return copied;
    }

    private static List<NameFilterGroup> migrateNameFilterGroups(boolean whitelistEnabled, List<String> whitelist,
            boolean blacklistEnabled, List<String> blacklist) {
        List<NameFilterGroup> groups = new ArrayList<>();
        groups.add(new NameFilterGroup("白名单", whitelistEnabled, false, normalizeNameList(whitelist)));
        groups.add(new NameFilterGroup("黑名单", blacklistEnabled, true, normalizeNameList(blacklist)));
        return groups;
    }

    /**
     * 按卡片顺序合并启用的分组。白名单的先后就是目标优先级，所以先按分组顺序、
     * 再按组内顺序排；开关只在合并结果非空时打开，空的白名单组不会把所有目标都拦掉。
     */
    public static void rebuildNameFiltersFromGroups() {
        LinkedHashSet<String> whitelist = new LinkedHashSet<>();
        LinkedHashSet<String> blacklist = new LinkedHashSet<>();
        if (cfg().nameFilterGroups == null) {
            cfg().nameFilterGroups = new ArrayList<>();
        }
        for (NameFilterGroup group : cfg().nameFilterGroups) {
            if (group == null || !group.enabled) {
                continue;
            }
            (group.blacklist ? blacklist : whitelist).addAll(normalizeNameList(group.names));
        }
        cfg().nameWhitelist = new ArrayList<>(whitelist);
        cfg().nameBlacklist = new ArrayList<>(blacklist);
        cfg().enableNameWhitelist = !cfg().nameWhitelist.isEmpty();
        cfg().enableNameBlacklist = !cfg().nameBlacklist.isEmpty();
    }

    private static String normalizeNameFilterKeyword(String entry) {
        return normalizeFilterName(entry).toLowerCase(Locale.ROOT);
    }

    private static List<HuntPickupRule> copyHuntPickupRuleList(List<HuntPickupRule> source) {
        List<HuntPickupRule> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (HuntPickupRule rule : source) {
            HuntPickupRule normalized = normalizeHuntPickupRule(rule);
            if (normalized != null) {
                copied.add(new HuntPickupRule(normalized));
            }
        }
        return copied;
    }

    private static List<HuntPickupRule> normalizeHuntPickupRuleList(List<HuntPickupRule> source) {
        List<HuntPickupRule> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }
        LinkedHashSet<String> seenNames = new LinkedHashSet<>();
        int fallbackIndex = 1;
        for (HuntPickupRule rule : source) {
            HuntPickupRule safeRule = normalizeHuntPickupRule(rule);
            if (safeRule == null) {
                continue;
            }
            String baseName = safeRule.name;
            while (!seenNames.add(baseName.toLowerCase(Locale.ROOT))) {
                baseName = safeRule.name + "_" + fallbackIndex++;
            }
            safeRule.name = baseName;
            normalized.add(safeRule);
        }
        return normalized;
    }

    private static HuntPickupRule normalizeHuntPickupRule(HuntPickupRule source) {
        if (source == null) {
            return null;
        }
        HuntPickupRule rule = new HuntPickupRule(source);
        rule.name = normalizePresetName(rule.name);
        if (rule.name.isEmpty()) {
            return null;
        }
        rule.category = normalizeCategoryName(rule.category);
        rule.mode = normalizeHuntPickupRuleMode(rule.mode);
        rule.nameKeyword = normalizeNameFilterKeyword(rule.nameKeyword);
        rule.itemIdKeyword = rule.itemIdKeyword == null ? "" : rule.itemIdKeyword.trim().toLowerCase(Locale.ROOT);
        rule.requiredNbtTags = normalizeNameList(rule.requiredNbtTags);
        rule.rarityFilters = normalizeHuntPickupRarityList(rule.rarityFilters);
        rule.itemFilterExpressions = normalizeHuntPickupExpressions(rule.itemFilterExpressions);
        migrateLegacyHuntPickupRuleToExpressions(rule);
        rule.maxDistance = MathHelper.clamp(rule.maxDistance, 0.0F, 100.0F);
        rule.priority = MathHelper.clamp(rule.priority, -999, 999);

        boolean hasMatcher = !rule.itemFilterExpressions.isEmpty()
                || rule.maxDistance > 0.0F;
        return hasMatcher ? rule : null;
    }

    private static String normalizeCategoryName(String category) {
        String normalized = category == null ? "" : category.trim();
        return normalized.isEmpty() ? "默认" : normalized;
    }

    private static String normalizeHuntPickupRuleMode(String mode) {
        return HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(mode)
                ? HUNT_PICKUP_RULE_MODE_BLOCK
                : HUNT_PICKUP_RULE_MODE_ALLOW;
    }

    private static List<String> normalizeHuntPickupRarityList(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (source != null) {
            for (String entry : source) {
                String normalized = normalizeHuntPickupRarityToken(entry);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    public static String normalizeHuntPickupRarityToken(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("普通".equals(normalized) || "common".equals(normalized)) {
            return HUNT_PICKUP_RARITY_COMMON;
        }
        if ("罕见".equals(normalized) || "少见".equals(normalized) || "uncommon".equals(normalized)) {
            return HUNT_PICKUP_RARITY_UNCOMMON;
        }
        if ("稀有".equals(normalized) || "rare".equals(normalized)) {
            return HUNT_PICKUP_RARITY_RARE;
        }
        if ("史诗".equals(normalized) || "epic".equals(normalized)) {
            return HUNT_PICKUP_RARITY_EPIC;
        }
        return "";
    }

    public static String getHuntPickupRarityDisplayName(String rarityToken) {
        String normalized = normalizeHuntPickupRarityToken(rarityToken);
        if (HUNT_PICKUP_RARITY_UNCOMMON.equals(normalized)) {
            return "罕见";
        }
        if (HUNT_PICKUP_RARITY_RARE.equals(normalized)) {
            return "稀有";
        }
        if (HUNT_PICKUP_RARITY_EPIC.equals(normalized)) {
            return "史诗";
        }
        return "普通";
    }

    private static List<String> normalizeHuntPickupExpressions(List<String> source) {
        List<String> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String expression : source) {
            String text = expression == null ? "" : expression.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                InventoryItemFilterExpressionEngine.validate(text);
                unique.add(text);
            } catch (RuntimeException ignored) {
            }
        }
        normalized.addAll(unique);
        return normalized;
    }

    private static void migrateLegacyHuntPickupRuleToExpressions(HuntPickupRule rule) {
        if (rule == null || !rule.itemFilterExpressions.isEmpty()) {
            return;
        }

        List<String> expressions = new ArrayList<>();
        String legacyExpression = buildLegacyHuntPickupExpression(rule.nameKeyword, rule.itemIdKeyword,
                rule.requiredNbtTags, rule.rarityFilters);
        if (!legacyExpression.isEmpty()) {
            expressions.add(legacyExpression);
        }
        rule.itemFilterExpressions = normalizeHuntPickupExpressions(expressions);
        rule.nameKeyword = "";
        rule.itemIdKeyword = "";
        rule.requiredNbtTags = new ArrayList<>();
        rule.rarityFilters = new ArrayList<>();
    }

    private static String buildLegacyHuntPickupExpression(String nameKeyword, String itemIdKeyword,
            List<String> requiredNbtTags, List<String> rarityFilters) {
        List<String> parts = new ArrayList<>();
        String normalizedName = normalizeNameFilterKeyword(nameKeyword);
        if (!normalizedName.isEmpty()) {
            parts.add("nameContains(\"" + escapeExpressionText(normalizedName) + "\")");
        }
        String normalizedId = itemIdKeyword == null ? "" : itemIdKeyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedId.isEmpty()) {
            parts.add("registryContains(\"" + escapeExpressionText(normalizedId) + "\")");
        }
        List<String> normalizedTags = normalizeNameList(requiredNbtTags);
        if (!normalizedTags.isEmpty()) {
            List<String> tagParts = new ArrayList<>();
            for (String tag : normalizedTags) {
                tagParts.add("NBT(\"" + escapeExpressionText(tag) + "\")");
            }
            parts.add("(" + String.join(" || ", tagParts) + ")");
        }
        List<String> normalizedRarities = normalizeHuntPickupRarityList(rarityFilters);
        if (!normalizedRarities.isEmpty()) {
            List<String> rarityParts = new ArrayList<>();
            for (String rarity : normalizedRarities) {
                rarityParts.add("rarity == \"" + escapeExpressionText(rarity) + "\"");
            }
            parts.add("(" + String.join(" || ", rarityParts) + ")");
        }
        return parts.isEmpty() ? "" : String.join(" && ", parts);
    }

    private static String escapeExpressionText(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalizeHuntModeValue(String mode) {
        String normalizedMode = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (HUNT_MODE_FIXED_DISTANCE.equals(normalizedMode)) {
            return HUNT_MODE_FIXED_DISTANCE;
        }
        if (HUNT_MODE_OFF.equals(normalizedMode)) {
            return HUNT_MODE_OFF;
        }
        return HUNT_MODE_APPROACH;
    }

    private static String normalizePresetName(String name) {
        return name == null ? "" : name.trim();
    }

    private static int findPresetIndex(String name) {
        String normalizedName = normalizePresetName(name);
        if (normalizedName.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < cfg().presets.size(); i++) {
            KillAuraPreset preset = cfg().presets.get(i);
            if (preset != null && normalizedName.equalsIgnoreCase(normalizePresetName(preset.name))) {
                return i;
            }
        }
        return -1;
    }

    private static KillAuraPreset captureCurrentAsPreset(String name) {
        KillAuraPreset preset = new KillAuraPreset();
        preset.name = normalizePresetName(name);
        preset.rotateToTarget = cfg().rotateToTarget;
        preset.smoothRotation = cfg().smoothRotation;
        preset.smoothMaxTurnStep = cfg().smoothMaxTurnStep;
        preset.smoothMaxTurnStepSpec = cfg().smoothMaxTurnStepSpec;
        preset.rotateOnlyOnAttack = cfg().rotateOnlyOnAttack;
        preset.relockOnlyWhenNoCrosshairTarget = cfg().relockOnlyWhenNoCrosshairTarget;
        preset.onlyAttackWhenLookingAtTarget = cfg().onlyAttackWhenLookingAtTarget;
        preset.requireLineOfSight = cfg().requireLineOfSight;
        preset.throughWallAttack = cfg().throughWallAttack;
        preset.targetHostile = cfg().targetHostile;
        preset.targetPassive = cfg().targetPassive;
        preset.targetPlayers = cfg().targetPlayers;
        preset.targetNpcs = cfg().targetNpcs;
        preset.targetEnderCrystal = cfg().targetEnderCrystal;
        preset.onlyWeapon = cfg().onlyWeapon;
        preset.aimOnlyMode = rt().aimOnlyMode;
        preset.focusSingleTarget = cfg().focusSingleTarget;
        preset.blockWhileAttacking = cfg().blockWhileAttacking;
        preset.ignoreInvisible = cfg().ignoreInvisible;
        preset.enableNoCollision = cfg().enableNoCollision;
        preset.enableAntiKnockback = cfg().enableAntiKnockback;
        preset.enableFullBrightVision = cfg().enableFullBrightVision;
        preset.fullBrightGamma = cfg().fullBrightGamma;
        preset.attackMode = cfg().attackMode;
        preset.attackSequenceName = cfg().attackSequenceName;
        preset.attackSequenceDelayTicks = cfg().attackSequenceDelayTicks;
        preset.attackSequenceDelayTicksSpec = cfg().attackSequenceDelayTicksSpec;
        preset.aimYawOffset = cfg().aimYawOffset;
        preset.aimYawOffsetSpec = cfg().aimYawOffsetSpec;
        preset.aimPitchOffset = cfg().aimPitchOffset;
        preset.aimPitchOffsetSpec = cfg().aimPitchOffsetSpec;
        preset.huntMode = cfg().huntMode;
        preset.huntPickupItemsEnabled = cfg().huntPickupItemsEnabled;
        preset.huntPickupRules = copyHuntPickupRuleList(cfg().huntPickupRules);
        preset.visualizeHuntRadius = rt().visualizeHuntRadius;
        preset.huntRadius = cfg().huntRadius;
        preset.huntFixedDistance = cfg().huntFixedDistance;
        preset.huntUpRange = cfg().huntUpRange;
        preset.huntDownRange = cfg().huntDownRange;
        preset.huntOrbitEnabled = cfg().huntOrbitEnabled;
        preset.huntJumpOrbitEnabled = cfg().huntJumpOrbitEnabled;
        preset.huntOrbitSamplePoints = cfg().huntOrbitSamplePoints;
        preset.enableNameWhitelist = cfg().enableNameWhitelist;
        preset.enableNameBlacklist = cfg().enableNameBlacklist;
        preset.nameWhitelist = new ArrayList<>(cfg().nameWhitelist == null ? new ArrayList<>() : cfg().nameWhitelist);
        preset.nameBlacklist = new ArrayList<>(cfg().nameBlacklist == null ? new ArrayList<>() : cfg().nameBlacklist);
        preset.nameFilterGroups = copyNameFilterGroups(cfg().nameFilterGroups);
        preset.nearbyEntityScanRange = cfg().nearbyEntityScanRange;
        preset.attackRange = cfg().attackRange;
        preset.huntScoreRadiusWeight = cfg().huntScoreRadiusWeight;
        preset.huntScorePlayerDistanceWeight = cfg().huntScorePlayerDistanceWeight;
        preset.huntScorePlayerPlaneWeight = cfg().huntScorePlayerPlaneWeight;
        preset.huntScoreTargetHeightWeight = cfg().huntScoreTargetHeightWeight;
        preset.huntScoreAttackRangeWeight = cfg().huntScoreAttackRangeWeight;
        preset.huntScoreVisibilityWeight = cfg().huntScoreVisibilityWeight;
        preset.huntScoreOpennessWeight = cfg().huntScoreOpennessWeight;
        preset.minAttackStrength = cfg().minAttackStrength;
        preset.minTurnSpeed = cfg().minTurnSpeed;
        preset.maxTurnSpeed = cfg().maxTurnSpeed;
        preset.minAttackIntervalTicks = cfg().minAttackIntervalTicks;
        preset.targetsPerAttack = cfg().targetsPerAttack;
        preset.teleportStepDistance = cfg().teleportStepDistance;
        preset.teleportStationAttackRadius = cfg().teleportStationAttackRadius;
        preset.teleportActionsPerTick = cfg().teleportActionsPerTick;
        preset.teleportStepDistancePerAction = cfg().teleportStepDistancePerAction;
        preset.teleportAttackRotationPackets = cfg().teleportAttackRotationPackets;
        preset.disableOnDisconnect = rt().disableOnDisconnect;
        preset.noDamageAttackLimit = cfg().noDamageAttackLimit;
        return normalizePreset(preset);
    }

    private static KillAuraPreset normalizePreset(KillAuraPreset preset) {
        if (preset == null) {
            return null;
        }
        String normalizedName = normalizePresetName(preset.name);
        if (normalizedName.isEmpty()) {
            return null;
        }
        KillAuraPreset normalizedPreset = new KillAuraPreset(preset);
        normalizedPreset.name = normalizedName;
        normalizedPreset.rotateOnlyOnAttack = normalizedPreset.rotateOnlyOnAttack && normalizedPreset.rotateToTarget;
        normalizedPreset.relockOnlyWhenNoCrosshairTarget = normalizedPreset.relockOnlyWhenNoCrosshairTarget
                && (normalizedPreset.rotateToTarget || normalizedPreset.aimOnlyMode);
        normalizedPreset.nameWhitelist = normalizeNameList(normalizedPreset.nameWhitelist);
        normalizedPreset.nameBlacklist = normalizeNameList(normalizedPreset.nameBlacklist);
        if (normalizedPreset.nameFilterGroups != null) {
            normalizedPreset.nameFilterGroups = copyNameFilterGroups(normalizedPreset.nameFilterGroups);
        }
        normalizedPreset.huntPickupRules = normalizeHuntPickupRuleList(normalizedPreset.huntPickupRules);
        normalizedPreset.attackSequenceName = normalizedPreset.attackSequenceName == null
                ? ""
                : normalizedPreset.attackSequenceName.trim();
        normalizedPreset.fullBrightGamma = MathHelper.clamp(normalizedPreset.fullBrightGamma, 1.0F, 1000.0F);
        normalizedPreset.attackRange = MathHelper.clamp(normalizedPreset.attackRange, 1.0F, 200.0F);
        normalizeHuntScoreWeights(normalizedPreset);
        normalizedPreset.minAttackStrength = MathHelper.clamp(normalizedPreset.minAttackStrength, 0.0F, 1.0F);
        SmoothTurnStepRange presetTurnRange = parseSmoothMaxTurnStepSpec(normalizedPreset.smoothMaxTurnStepSpec,
                normalizedPreset.smoothMaxTurnStep);
        normalizedPreset.smoothMaxTurnStep = presetTurnRange.min;
        normalizedPreset.smoothMaxTurnStepSpec = presetTurnRange.toSpec();
        normalizedPreset.minTurnSpeed = MathHelper.clamp(normalizedPreset.minTurnSpeed, 1.0F, 40.0F);
        normalizedPreset.maxTurnSpeed = MathHelper.clamp(normalizedPreset.maxTurnSpeed,
                normalizedPreset.minTurnSpeed, 60.0F);
        normalizedPreset.minAttackIntervalTicks = MathHelper.clamp(normalizedPreset.minAttackIntervalTicks, 0, 20);
        normalizedPreset.targetsPerAttack = MathHelper.clamp(normalizedPreset.targetsPerAttack, 1, 50);
        normalizedPreset.teleportStepDistance = clampFiniteFloat(normalizedPreset.teleportStepDistance,
                DEFAULT_TELEPORT_STEP_DISTANCE, MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
        normalizedPreset.teleportStationAttackRadius = clampFiniteFloat(
                normalizedPreset.teleportStationAttackRadius, DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
        normalizedPreset.teleportActionsPerTick = MathHelper.clamp(normalizedPreset.teleportActionsPerTick,
                MIN_TELEPORT_ACTIONS_PER_TICK, MAX_TELEPORT_ACTIONS_PER_TICK);
        normalizedPreset.teleportStepDistancePerAction = clampFiniteFloat(
                normalizedPreset.teleportStepDistancePerAction, DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION,
                MIN_TELEPORT_STEP_DISTANCE_PER_ACTION, MAX_TELEPORT_STEP_DISTANCE_PER_ACTION);
        normalizedPreset.noDamageAttackLimit = MathHelper.clamp(normalizedPreset.noDamageAttackLimit, 0,
                MAX_NO_DAMAGE_ATTACK_LIMIT);
        TickRangeSpec.Range attackSequenceDelayRange = TickRangeSpec.parse(
                normalizedPreset.attackSequenceDelayTicksSpec,
                normalizedPreset.attackSequenceDelayTicks, MIN_ATTACK_SEQUENCE_DELAY_TICKS,
                MAX_ATTACK_SEQUENCE_DELAY_TICKS);
        normalizedPreset.attackSequenceDelayTicks = attackSequenceDelayRange.getMin();
        normalizedPreset.attackSequenceDelayTicksSpec = attackSequenceDelayRange.toSpec();
        AimOffsetRange presetYawOffsetRange = parseAimOffsetSpec(normalizedPreset.aimYawOffsetSpec,
                normalizedPreset.aimYawOffset);
        normalizedPreset.aimYawOffset = presetYawOffsetRange.min;
        normalizedPreset.aimYawOffsetSpec = presetYawOffsetRange.toSpec();
        AimOffsetRange presetPitchOffsetRange = parseAimOffsetSpec(normalizedPreset.aimPitchOffsetSpec,
                normalizedPreset.aimPitchOffset);
        normalizedPreset.aimPitchOffset = presetPitchOffsetRange.min;
        normalizedPreset.aimPitchOffsetSpec = presetPitchOffsetRange.toSpec();
        normalizedPreset.huntRadius = MathHelper.clamp(normalizedPreset.huntRadius, normalizedPreset.attackRange,
                100.0F);
        normalizedPreset.huntFixedDistance = MathHelper.clamp(normalizedPreset.huntFixedDistance, 0.5F, 100.0F);
        normalizedPreset.huntUpRange = MathHelper.clamp(normalizedPreset.huntUpRange, 0.0F, 100.0F);
        normalizedPreset.huntDownRange = MathHelper.clamp(normalizedPreset.huntDownRange, 0.0F, 100.0F);
        normalizedPreset.huntOrbitSamplePoints = MathHelper.clamp(normalizedPreset.huntOrbitSamplePoints,
                MIN_HUNT_ORBIT_SAMPLE_POINTS, MAX_HUNT_ORBIT_SAMPLE_POINTS);
        normalizedPreset.nearbyEntityScanRange = MathHelper.clamp(normalizedPreset.nearbyEntityScanRange, 1.0F, 64.0F);

        String normalizedAttackMode = normalizedPreset.attackMode == null ? ""
                : normalizedPreset.attackMode.trim().toUpperCase(Locale.ROOT);
        if (ATTACK_MODE_PACKET.equals(normalizedAttackMode)) {
            normalizedPreset.attackMode = ATTACK_MODE_PACKET;
        } else if (ATTACK_MODE_TELEPORT.equals(normalizedAttackMode)) {
            normalizedPreset.attackMode = ATTACK_MODE_TELEPORT;
        } else if (ATTACK_MODE_SEQUENCE.equals(normalizedAttackMode)) {
            normalizedPreset.attackMode = ATTACK_MODE_SEQUENCE;
        } else if (ATTACK_MODE_MOUSE_CLICK.equals(normalizedAttackMode)) {
            normalizedPreset.attackMode = ATTACK_MODE_MOUSE_CLICK;
        } else {
            normalizedPreset.attackMode = ATTACK_MODE_NORMAL;
        }
        normalizedPreset.huntMode = normalizeHuntModeValue(normalizedPreset.huntMode);
        if (normalizedPreset.aimOnlyMode) {
            normalizedPreset.attackMode = ATTACK_MODE_SEQUENCE;
            normalizedPreset.rotateOnlyOnAttack = false;
        } else if (ATTACK_MODE_PACKET.equals(normalizedPreset.attackMode)) {
            normalizedPreset.rotateToTarget = false;
            normalizedPreset.smoothRotation = false;
            normalizedPreset.rotateOnlyOnAttack = false;
            normalizedPreset.relockOnlyWhenNoCrosshairTarget = false;
        }
        if (!normalizedPreset.targetHostile
                && !normalizedPreset.targetPassive
                && !normalizedPreset.targetPlayers
                && !normalizedPreset.targetNpcs
                && !normalizedPreset.targetEnderCrystal) {
            normalizedPreset.targetHostile = true;
        }
        if (HUNT_MODE_OFF.equals(normalizedPreset.huntMode)) {
            normalizedPreset.visualizeHuntRadius = false;
        }
        return normalizedPreset;
    }

    public static void resetHuntScoreWeights() {
        RuntimeState rt = rt();
        cfg().huntScoreRadiusWeight = DEFAULT_HUNT_SCORE_RADIUS_WEIGHT;
        cfg().huntScorePlayerDistanceWeight = DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT;
        cfg().huntScorePlayerPlaneWeight = DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT;
        cfg().huntScoreTargetHeightWeight = DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT;
        cfg().huntScoreAttackRangeWeight = DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT;
        cfg().huntScoreVisibilityWeight = DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT;
        cfg().huntScoreOpennessWeight = DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT;
        rt().lastHuntScoreDebugTick = Integer.MIN_VALUE;
    }

    private static void normalizeHuntScoreWeights(KillAuraPreset preset) {
        if (preset == null) {
            return;
        }
        preset.huntScoreRadiusWeight = clampHuntScoreWeight(preset.huntScoreRadiusWeight,
                DEFAULT_HUNT_SCORE_RADIUS_WEIGHT);
        preset.huntScorePlayerDistanceWeight = clampHuntScoreWeight(preset.huntScorePlayerDistanceWeight,
                DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT);
        preset.huntScorePlayerPlaneWeight = clampHuntScoreWeight(preset.huntScorePlayerPlaneWeight,
                DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT);
        preset.huntScoreTargetHeightWeight = clampHuntScoreWeight(preset.huntScoreTargetHeightWeight,
                DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT);
        preset.huntScoreAttackRangeWeight = clampHuntScoreWeight(preset.huntScoreAttackRangeWeight,
                DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT);
        preset.huntScoreVisibilityWeight = clampHuntScoreWeight(preset.huntScoreVisibilityWeight,
                DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT);
        preset.huntScoreOpennessWeight = clampHuntScoreWeight(preset.huntScoreOpennessWeight,
                DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT);
    }

    private static float clampHuntScoreWeight(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : MathHelper.clamp(value, 0.0F, 100.0F);
    }

    private static float clampFiniteFloat(float value, float fallback, float min, float max) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : MathHelper.clamp(value, min, max);
    }

    private static void normalizeConfig() {
        cfg().attackRange = MathHelper.clamp(cfg().attackRange, 1.0F, 200.0F);
        cfg().huntScoreRadiusWeight = clampHuntScoreWeight(cfg().huntScoreRadiusWeight, DEFAULT_HUNT_SCORE_RADIUS_WEIGHT);
        cfg().huntScorePlayerDistanceWeight = clampHuntScoreWeight(cfg().huntScorePlayerDistanceWeight,
                DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT);
        cfg().huntScorePlayerPlaneWeight = clampHuntScoreWeight(cfg().huntScorePlayerPlaneWeight,
                DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT);
        cfg().huntScoreTargetHeightWeight = clampHuntScoreWeight(cfg().huntScoreTargetHeightWeight,
                DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT);
        cfg().huntScoreAttackRangeWeight = clampHuntScoreWeight(cfg().huntScoreAttackRangeWeight,
                DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT);
        cfg().huntScoreVisibilityWeight = clampHuntScoreWeight(cfg().huntScoreVisibilityWeight,
                DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT);
        cfg().huntScoreOpennessWeight = clampHuntScoreWeight(cfg().huntScoreOpennessWeight,
                DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT);
        cfg().minAttackStrength = MathHelper.clamp(cfg().minAttackStrength, 0.0F, 1.0F);
        SmoothTurnStepRange turnRange = parseSmoothMaxTurnStepSpec(cfg().smoothMaxTurnStepSpec, cfg().smoothMaxTurnStep);
        cfg().smoothMaxTurnStep = turnRange.min;
        cfg().smoothMaxTurnStepSpec = turnRange.toSpec();
        cfg().minTurnSpeed = MathHelper.clamp(cfg().minTurnSpeed, 1.0F, 40.0F);
        cfg().maxTurnSpeed = MathHelper.clamp(cfg().maxTurnSpeed, cfg().minTurnSpeed, 60.0F);
        cfg().minAttackIntervalTicks = MathHelper.clamp(cfg().minAttackIntervalTicks, 0, 20);
        cfg().targetsPerAttack = MathHelper.clamp(cfg().targetsPerAttack, 1, 50);
        cfg().teleportStepDistance = clampFiniteFloat(cfg().teleportStepDistance, DEFAULT_TELEPORT_STEP_DISTANCE,
                MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
        cfg().teleportStationAttackRadius = clampFiniteFloat(cfg().teleportStationAttackRadius,
                DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
        cfg().teleportActionsPerTick = MathHelper.clamp(cfg().teleportActionsPerTick, MIN_TELEPORT_ACTIONS_PER_TICK,
                MAX_TELEPORT_ACTIONS_PER_TICK);
        cfg().teleportStepDistancePerAction = clampFiniteFloat(cfg().teleportStepDistancePerAction,
                DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION, MIN_TELEPORT_STEP_DISTANCE_PER_ACTION,
                MAX_TELEPORT_STEP_DISTANCE_PER_ACTION);
        cfg().noDamageAttackLimit = MathHelper.clamp(cfg().noDamageAttackLimit, 0, MAX_NO_DAMAGE_ATTACK_LIMIT);
        TickRangeSpec.Range attackSequenceDelayRange = TickRangeSpec.parse(cfg().attackSequenceDelayTicksSpec,
                cfg().attackSequenceDelayTicks, MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
        cfg().attackSequenceDelayTicks = attackSequenceDelayRange.getMin();
        cfg().attackSequenceDelayTicksSpec = attackSequenceDelayRange.toSpec();
        AimOffsetRange yawOffsetRange = parseAimOffsetSpec(cfg().aimYawOffsetSpec, cfg().aimYawOffset);
        cfg().aimYawOffset = yawOffsetRange.min;
        cfg().aimYawOffsetSpec = yawOffsetRange.toSpec();
        AimOffsetRange pitchOffsetRange = parseAimOffsetSpec(cfg().aimPitchOffsetSpec, cfg().aimPitchOffset);
        cfg().aimPitchOffset = pitchOffsetRange.min;
        cfg().aimPitchOffsetSpec = pitchOffsetRange.toSpec();
        cfg().huntRadius = MathHelper.clamp(cfg().huntRadius, cfg().attackRange, 100.0F);
        cfg().huntFixedDistance = MathHelper.clamp(cfg().huntFixedDistance, 0.5F, 100.0F);
        cfg().huntUpRange = MathHelper.clamp(cfg().huntUpRange, 0.0F, 100.0F);
        cfg().huntDownRange = MathHelper.clamp(cfg().huntDownRange, 0.0F, 100.0F);
        cfg().huntOrbitSamplePoints = MathHelper.clamp(cfg().huntOrbitSamplePoints,
                MIN_HUNT_ORBIT_SAMPLE_POINTS, MAX_HUNT_ORBIT_SAMPLE_POINTS);
        cfg().fullBrightGamma = MathHelper.clamp(cfg().fullBrightGamma, 1.0F, 1000.0F);
        cfg().nearbyEntityScanRange = MathHelper.clamp(cfg().nearbyEntityScanRange, 1.0F, 64.0F);
        cfg().nameWhitelist = normalizeNameList(cfg().nameWhitelist);
        cfg().nameBlacklist = normalizeNameList(cfg().nameBlacklist);
        cfg().nameFilterGroups = copyNameFilterGroups(cfg().nameFilterGroups);
        rebuildNameFiltersFromGroups();
        cfg().huntPickupRules = normalizeHuntPickupRuleList(cfg().huntPickupRules);
        cfg().attackSequenceName = getConfiguredAttackSequenceName();

        String normalizedAttackMode = cfg().attackMode == null ? "" : cfg().attackMode.trim().toUpperCase(Locale.ROOT);
        if (ATTACK_MODE_PACKET.equals(normalizedAttackMode)) {
            cfg().attackMode = ATTACK_MODE_PACKET;
        } else if (ATTACK_MODE_TELEPORT.equals(normalizedAttackMode)) {
            cfg().attackMode = ATTACK_MODE_TELEPORT;
        } else if (ATTACK_MODE_SEQUENCE.equals(normalizedAttackMode)) {
            cfg().attackMode = ATTACK_MODE_SEQUENCE;
        } else if (ATTACK_MODE_MOUSE_CLICK.equals(normalizedAttackMode)) {
            cfg().attackMode = ATTACK_MODE_MOUSE_CLICK;
        } else {
            cfg().attackMode = ATTACK_MODE_NORMAL;
        }

        if (rt().aimOnlyMode) {
            cfg().attackMode = ATTACK_MODE_SEQUENCE;
            cfg().rotateOnlyOnAttack = false;
        } else if (ATTACK_MODE_PACKET.equals(cfg().attackMode)) {
            cfg().rotateToTarget = false;
            cfg().smoothRotation = false;
            cfg().rotateOnlyOnAttack = false;
            cfg().relockOnlyWhenNoCrosshairTarget = false;
        }
        cfg().rotateOnlyOnAttack = cfg().rotateOnlyOnAttack && cfg().rotateToTarget;
        cfg().relockOnlyWhenNoCrosshairTarget = cfg().relockOnlyWhenNoCrosshairTarget && (rt().aimOnlyMode || cfg().rotateToTarget);

        cfg().huntMode = normalizeHuntModeValue(cfg().huntMode);
        rt().huntEnabled = !HUNT_MODE_OFF.equals(cfg().huntMode);
        if (!rt().huntEnabled) {
            rt().visualizeHuntRadius = false;
        }

        if (!cfg().targetHostile && !cfg().targetPassive && !cfg().targetPlayers && !cfg().targetNpcs && !cfg().targetEnderCrystal) {
            cfg().targetHostile = true;
        }
    }

    private double getEffectiveHuntFixedDistance() {
        return Math.max(0.5D, cfg().huntFixedDistance);
    }

    public static boolean isHuntOrbitEnabled() {
        return isHuntFixedDistanceMode() && cfg().huntOrbitEnabled;
    }

    public static int getConfiguredHuntOrbitSamplePoints() {
        return MathHelper.clamp(cfg().huntOrbitSamplePoints, MIN_HUNT_ORBIT_SAMPLE_POINTS, MAX_HUNT_ORBIT_SAMPLE_POINTS);
    }

    public static boolean isHuntOrbitSampleCountAtMaximum() {
        return getConfiguredHuntOrbitSamplePoints() >= MAX_HUNT_ORBIT_SAMPLE_POINTS;
    }

    public boolean shouldKeepRunningDuringGui(Minecraft mc) {
        if (mc == null || ctxPlayer() == null || ctxWorld() == null || !rt().enabled || !isHuntOrbitEnabled()) {
            return false;
        }
        return this.huntOrbitController.isActive() && hasActiveTarget(ctxPlayer());
    }

    private static final class TeleportPathNode {
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final double gScore;
        private final double fScore;
        private final TeleportPathNode parent;

        private TeleportPathNode(int offsetX, int offsetY, int offsetZ, double gScore, double fScore,
                TeleportPathNode parent) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.gScore = gScore;
            this.fScore = fScore;
            this.parent = parent;
        }

        private Vec3d toPosition(Vec3d origin) {
            return new Vec3d(origin.x + this.offsetX, origin.y + this.offsetY, origin.z + this.offsetZ);
        }
    }

    private static final class TeleportAssaultCandidate {
        private final double x;
        private final double y;
        private final double z;
        private final boolean usedSafeStandPos;
        private final double score;

        private TeleportAssaultCandidate(double x, double y, double z, boolean usedSafeStandPos, double score) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.usedSafeStandPos = usedSafeStandPos;
            this.score = score;
        }
    }

    private static final class TeleportPlanningBudget {
        private final TeleportPlanningBudget parent;
        private final long deadlineNanos;
        private int remainingWorldQueries;
        private String failureReason = "NONE";

        private TeleportPlanningBudget(long deadlineNanos, int worldQueryLimit) {
            this.parent = null;
            this.deadlineNanos = deadlineNanos;
            this.remainingWorldQueries = Math.max(0, worldQueryLimit);
        }

        private TeleportPlanningBudget(TeleportPlanningBudget parent, int worldQueryLimit) {
            this.parent = parent;
            this.deadlineNanos = parent == null ? Long.MIN_VALUE : parent.deadlineNanos;
            this.remainingWorldQueries = Math.max(0, worldQueryLimit);
        }

        private TeleportPlanningBudget child(int worldQueryLimit) {
            return new TeleportPlanningBudget(this, Math.min(Math.max(0, worldQueryLimit),
                    this.remainingWorldQueries));
        }

        private boolean canContinue() {
            return this.remainingWorldQueries > 0
                    && System.nanoTime() < this.deadlineNanos
                    && (this.parent == null || this.parent.canContinue());
        }

        private boolean consumeWorldQuery() {
            if (!canContinue()) {
                if (System.nanoTime() >= this.deadlineNanos) {
                    markFailure("DEADLINE_REACHED");
                } else if (this.remainingWorldQueries <= 0) {
                    markFailure("WORLD_QUERY_LIMIT_REACHED");
                } else {
                    markFailure("PARENT_BUDGET_EXHAUSTED");
                }
                return false;
            }
            if (this.parent != null && !this.parent.consumeWorldQuery()) {
                markFailure("PARENT_BUDGET_EXHAUSTED(" + this.parent.getFailureReason() + ")");
                return false;
            }
            this.remainingWorldQueries--;
            return true;
        }

        private void markFailure(String reason) {
            if (reason != null && !reason.trim().isEmpty()) {
                this.failureReason = reason;
            }
        }

        private String getFailureReason() {
            if (!"NONE".equals(this.failureReason)) {
                return this.failureReason;
            }
            if (System.nanoTime() >= this.deadlineNanos) {
                return "DEADLINE_REACHED";
            }
            if (this.remainingWorldQueries <= 0) {
                return "WORLD_QUERY_LIMIT_REACHED";
            }
            return "UNKNOWN";
        }
    }

    private static final class TeleportStationSeed implements Comparable<TeleportStationSeed> {
        private final int targetEntityId;
        private final Vec3d position;
        private final int coverage;
        private final double distanceSq;
        private final boolean primary;

        private TeleportStationSeed(int targetEntityId, Vec3d position, int coverage, double distanceSq,
                boolean primary) {
            this.targetEntityId = targetEntityId;
            this.position = position;
            this.coverage = coverage;
            this.distanceSq = distanceSq;
            this.primary = primary;
        }

        @Override
        public int compareTo(TeleportStationSeed other) {
            if (other == null) {
                return -1;
            }
            int coverageCompare = Integer.compare(other.coverage, this.coverage);
            if (coverageCompare != 0) {
                return coverageCompare;
            }
            return Double.compare(this.distanceSq, other.distanceSq);
        }
    }

    private static final class TeleportTourExecution {
        private final EntityPlayerSP player;
        private final net.minecraft.client.network.NetHandlerPlayClient connection;
        private final World world;
        private final boolean startedEnabled;
        private final TeleportTourPlan plan;
        private final KillAuraTeleportRouteClock clock = new KillAuraTeleportRouteClock();
        private final List<Vec3d> traversed = new ArrayList<>();
        /** Where the returning walk ends: the plan origin (anchor when configured). */
        private final Vec3d returnPosition;
        private Vec3d lastSentPosition;
        /** Where the real body was last seen. The tour never moves it, so any
         *  drift means something outside the tour teleported or knocked it. */
        private Vec3d lastLocalPosition;
        private int ageTicks, stopIndex, legIndex, targetIndex, returnIndex, rearms;
        private boolean returning;

        private TeleportTourExecution(EntityPlayerSP player, TeleportTourPlan plan) {
            this.player = player;
            this.connection = player.connection;
            this.world = player.world;
            this.startedEnabled = rt().enabled;
            this.plan = plan;
            // The walk starts wherever the bot actually is; plan.origin is the
            // home the tour comes back to (the anchor, when one is configured).
            Vec3d start = TELEPORT_VIEW_LOCK.equals(cfg().teleportViewMode)
                    && rt().teleport.virtualPosition != null
                    ? rt().teleport.virtualPosition : player.getPositionVector();
            this.lastSentPosition = start;
            this.lastLocalPosition = player.getPositionVector();
            this.returnPosition = plan.origin.toPosition();
            this.traversed.add(start);
        }
    }

    private static final class TeleportTourStop {
        private final Vec3d position;
        private final List<Vec3d> legPositions;
        private final List<EntityLivingBase> targets;
        private final int rotationPacketCount;

        private TeleportTourStop(Vec3d position, List<Vec3d> legPositions,
                List<EntityLivingBase> targets, int rotationPacketCount) {
            this.position = position;
            this.legPositions = legPositions == null ? new ArrayList<>() : new ArrayList<>(legPositions);
            this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
            this.rotationPacketCount = Math.max(0, rotationPacketCount);
        }
    }

    private static final class TeleportTourPlan {
        private final TeleportOrigin origin;
        private final List<TeleportTourStop> stops = new ArrayList<>();
        private final List<Vec3d> forwardPositions = new ArrayList<>();
        private int attackCount;
        private int rotationPacketCount;
        private int packetCost;
        private boolean restoreRotationAtOrigin;

        private TeleportTourPlan(TeleportOrigin origin) {
            this.origin = origin;
        }

        private void addStop(TeleportTourStop stop) {
            if (stop == null || stop.targets.isEmpty()) {
                return;
            }
            this.stops.add(stop);
            this.forwardPositions.addAll(stop.legPositions);
            this.attackCount += stop.targets.size();
            this.rotationPacketCount += stop.rotationPacketCount;
        }
    }

    private static final class TeleportOrigin {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean onGround;

        private TeleportOrigin(EntityPlayerSP player) {
            this.x = player == null ? 0.0D : player.posX;
            this.y = player == null ? 0.0D : player.posY;
            this.z = player == null ? 0.0D : player.posZ;
            this.yaw = player == null ? 0.0F : player.rotationYaw;
            this.pitch = player == null ? 0.0F : player.rotationPitch;
            this.onGround = player != null && player.onGround;
        }

        /** Anchor-pinned origin: position from the anchor, orientation from the bot. */
        private TeleportOrigin(Vec3d anchoredPosition, EntityPlayerSP player) {
            this.x = anchoredPosition == null ? 0.0D : anchoredPosition.x;
            this.y = anchoredPosition == null ? 0.0D : anchoredPosition.y;
            this.z = anchoredPosition == null ? 0.0D : anchoredPosition.z;
            this.yaw = player == null ? 0.0F : player.rotationYaw;
            this.pitch = player == null ? 0.0F : player.rotationPitch;
            this.onGround = player != null && player.onGround;
        }

        private Vec3d toPosition() {
            return new Vec3d(this.x, this.y, this.z);
        }
    }

    private static final class TeleportRoute {
        private final TeleportAttackPlan owner;
        private final List<Vec3d> points;

        private TeleportRoute(TeleportAttackPlan owner, List<Vec3d> points) {
            this.owner = owner;
            this.points = points == null ? new ArrayList<>() : new ArrayList<>(points);
        }
    }

    private static final class TeleportReturnPlan {
        private final TeleportAttackPlan owner;
        private final List<Vec3d> waypoints;
        private final List<Vec3d> route;
        private int notBeforeTick;

        private TeleportReturnPlan(TeleportAttackPlan owner, List<Vec3d> waypoints, List<Vec3d> route) {
            this.owner = owner;
            this.waypoints = waypoints == null ? new ArrayList<>() : new ArrayList<>(waypoints);
            this.route = route == null ? new ArrayList<>() : new ArrayList<>(route);
            this.notBeforeTick = Integer.MIN_VALUE;
        }
    }

    private static final class TeleportAttackPlan {
        private final double originX;
        private final double originY;
        private final double originZ;
        private final float originYaw;
        private final float originPitch;
        private final boolean originOnGround;
        private final double assaultX;
        private final double assaultY;
        private final double assaultZ;
        private final float attackYaw;
        private final float attackPitch;
        private final List<Vec3d> outboundWaypoints;
        private final List<Vec3d> returnWaypoints;
        private final List<Vec3d> latestReturnRoute;
        private final List<TeleportRoute> relatedRoutes;
        private boolean correctedByServer;
        private boolean returnCompleted;
        private int correctionCount;

        private TeleportAttackPlan(TeleportOrigin origin, TeleportAssaultCandidate assaultCandidate,
                List<Vec3d> outboundWaypoints, List<Vec3d> returnWaypoints, float attackYaw, float attackPitch) {
            this.originX = origin == null ? 0.0D : origin.x;
            this.originY = origin == null ? 0.0D : origin.y;
            this.originZ = origin == null ? 0.0D : origin.z;
            this.originYaw = origin == null ? 0.0F : origin.yaw;
            this.originPitch = origin == null ? 0.0F : origin.pitch;
            this.originOnGround = origin != null && origin.onGround;
            this.assaultX = assaultCandidate == null ? this.originX : assaultCandidate.x;
            this.assaultY = assaultCandidate == null ? this.originY : assaultCandidate.y;
            this.assaultZ = assaultCandidate == null ? this.originZ : assaultCandidate.z;
            this.attackYaw = attackYaw;
            this.attackPitch = attackPitch;
            this.outboundWaypoints = outboundWaypoints == null ? new ArrayList<>() : new ArrayList<>(outboundWaypoints);
            this.returnWaypoints = returnWaypoints == null ? new ArrayList<>() : new ArrayList<>(returnWaypoints);
            this.latestReturnRoute = new ArrayList<>();
            this.relatedRoutes = new ArrayList<>();
            this.correctedByServer = false;
            this.returnCompleted = false;
            this.correctionCount = 0;
        }
    }

    private static final class AttackSequenceExecutor {
        private static final int POST_ACTION_DELAY_TICKS = 5;

        private PathSequence sequence;
        private int stepIndex = 0;
        private int actionIndex = 0;
        private int tickDelay = 0;
        private int targetEntityId = Integer.MIN_VALUE;
        private final ScopedRuntimeVariables runtimeVariables = new ScopedRuntimeVariables();
        private final Map<String, String> heldKeys = new LinkedHashMap<>();

        boolean isRunning() {
            return this.sequence != null;
        }

        void start(PathSequence sourceSequence, EntityPlayerSP player, EntityLivingBase target) {
            stop();
            if (sourceSequence == null || sourceSequence.getSteps().isEmpty()) {
                return;
            }

            this.sequence = new PathSequence(sourceSequence);
            this.stepIndex = 0;
            this.actionIndex = 0;
            this.tickDelay = 0;
            this.targetEntityId = target == null ? Integer.MIN_VALUE : target.getEntityId();
            this.runtimeVariables.clear();
            populateTargetVariables(player, target);
            this.runtimeVariables.enterStep(this.stepIndex);
        }

        void stop() {
            releaseHeldKeys();
            this.sequence = null;
            this.stepIndex = 0;
            this.actionIndex = 0;
            this.tickDelay = 0;
            this.targetEntityId = Integer.MIN_VALUE;
            this.runtimeVariables.clear();
            this.heldKeys.clear();
        }

        void tick(EntityPlayerSP player) {
            if (!isRunning()) {
                return;
            }
            if (player == null) {
                stop();
                return;
            }
            refreshTargetVariables(player);
            if (this.tickDelay > 0) {
                this.tickDelay--;
                return;
            }

            int guard = 0;
            while (isRunning() && guard++ < 128) {
                if (this.sequence == null || this.stepIndex >= this.sequence.getSteps().size()) {
                    stop();
                    return;
                }

                PathStep currentStep = this.sequence.getSteps().get(this.stepIndex);
                List<ActionData> actions = currentStep == null ? null : currentStep.getActions();
                if (actions == null || this.actionIndex >= actions.size()) {
                    this.stepIndex++;
                    this.actionIndex = 0;
                    this.runtimeVariables.enterStep(this.stepIndex);
                    continue;
                }

                ActionData rawAction = actions.get(this.actionIndex);
                ActionData resolvedAction = resolveActionData(rawAction, player);
                if (resolvedAction == null || resolvedAction.type == null) {
                    this.actionIndex++;
                    continue;
                }

                String actionType = resolvedAction.type.trim().toLowerCase(Locale.ROOT);
                if (actionType.isEmpty() || shouldSkipAction(actionType)) {
                    this.actionIndex++;
                    continue;
                }

                Consumer<EntityPlayerSP> action = PathSequenceManager.parseAction(resolvedAction.type,
                        resolvedAction.params);
                if (action == null) {
                    this.actionIndex++;
                    continue;
                }

                if (action instanceof ModUtils.DelayAction) {
                    this.tickDelay = ((ModUtils.DelayAction) action).getDelayTicks();
                    this.actionIndex++;
                    return;
                }

                try {
                    action.accept(player);
                } catch (Exception e) {
                    mythosScriptMod.LOGGER.error("[kill_aura_sequence] 执行动作失败: {}", resolvedAction.getDescription(), e);
                }

                updateHeldKeyState(resolvedAction);
                this.actionIndex++;
                this.tickDelay = POST_ACTION_DELAY_TICKS;
                return;
            }
        }

        private ActionData resolveActionData(ActionData actionData, EntityPlayerSP player) {
            if (actionData == null) {
                return null;
            }
            this.runtimeVariables.beginAction(this.stepIndex, this.actionIndex);

            JsonObject resolvedParams = LegacyActionRuntime.resolveParams(actionData.params, this.runtimeVariables,
                    player, this.sequence, this.stepIndex, this.actionIndex);
            return new ActionData(actionData.type, resolvedParams);
        }

        private boolean shouldSkipAction(String actionType) {
            return "run_sequence".equals(actionType) || "hunt".equals(actionType) || "set_var".equals(actionType)
                    || "sequence_control".equals(actionType)
                    || "no_stop_navigation".equals(actionType)
                    || "goto_action".equals(actionType) || "repeat_actions".equals(actionType)
                    || "restart_sequence".equals(actionType)
                    || "capture_nearby_entity".equals(actionType) || "capture_gui_title".equals(actionType)
                    || "capture_block_at".equals(actionType) || actionType.startsWith("condition_")
                    || actionType.startsWith("wait_until_");
        }

        private void populateTargetVariables(EntityPlayerSP player, EntityLivingBase target) {
            this.runtimeVariables.put("target_found", target != null);
            if (target == null) {
                this.runtimeVariables.remove("target_name");
                this.runtimeVariables.remove("target_id");
                this.runtimeVariables.remove("target_x");
                this.runtimeVariables.remove("target_y");
                this.runtimeVariables.remove("target_z");
                this.runtimeVariables.remove("target_block_x");
                this.runtimeVariables.remove("target_block_y");
                this.runtimeVariables.remove("target_block_z");
                this.runtimeVariables.remove("target_health");
                this.runtimeVariables.remove("target_distance");
                return;
            }

            this.runtimeVariables.put("target_name", target.getName());
            this.runtimeVariables.put("target_id", target.getEntityId());
            this.runtimeVariables.put("target_x", target.posX);
            this.runtimeVariables.put("target_y", target.posY);
            this.runtimeVariables.put("target_z", target.posZ);
            this.runtimeVariables.put("target_block_x", target.getPosition().getX());
            this.runtimeVariables.put("target_block_y", target.getPosition().getY());
            this.runtimeVariables.put("target_block_z", target.getPosition().getZ());
            this.runtimeVariables.put("target_health", target.getHealth());
            if (player != null) {
                this.runtimeVariables.put("target_distance", player.getDistance(target));
            }
        }

        private void refreshTargetVariables(EntityPlayerSP player) {
            if (player == null || player.world == null) {
                populateTargetVariables(player, null);
                return;
            }
            Entity targetEntity = this.targetEntityId == Integer.MIN_VALUE
                    ? null
                    : player.world.getEntityByID(this.targetEntityId);
            EntityLivingBase target = targetEntity instanceof EntityLivingBase ? (EntityLivingBase) targetEntity : null;
            if (target != null && (target.isDead || target.getHealth() <= 0.0F)) {
                target = null;
            }
            populateTargetVariables(player, target);
        }

        private void updateHeldKeyState(ActionData actionData) {
            if (actionData == null || actionData.params == null || !"key".equalsIgnoreCase(actionData.type)) {
                return;
            }

            String key = actionData.params.has("key") ? actionData.params.get("key").getAsString().trim() : "";
            String state = actionData.params.has("state") ? actionData.params.get("state").getAsString().trim() : "";
            if (key.isEmpty() || state.isEmpty()) {
                return;
            }

            String normalizedState = state.toLowerCase(Locale.ROOT);
            if ("down".equals(normalizedState) || "robotdown".equals(normalizedState)) {
                this.heldKeys.put(key, "Up");
            } else if ("up".equals(normalizedState) || "robotup".equals(normalizedState)) {
                this.heldKeys.remove(key);
            }
        }

        private void releaseHeldKeys() {
            if (this.heldKeys.isEmpty()) {
                return;
            }

            for (Map.Entry<String, String> entry : this.heldKeys.entrySet()) {
                try {
                    ModUtils.simulateKey(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    mythosScriptMod.LOGGER.warn("[kill_aura_sequence] 释放按键失败: {}", entry.getKey(), e);
                }
            }
        }
    }
}
