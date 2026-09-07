package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.LegacyActionRuntime;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.PathEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.AbstractGameEventListener;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.IEventBus;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.RotationUtils;
import com.zszl.zszlScriptMod.shadowbaritone.process.KillAuraOrbitProcess;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathRenderer;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.JsonConfigCharsetCompat;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.TickRangeSpec;
import com.zszl.zszlScriptMod.zszlScriptMod;
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
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
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
    public static final String DEFAULT_ATTACK_MODE = ATTACK_MODE_MOUSE_CLICK;
    public static final String HUNT_MODE_OFF = "OFF";
    public static final String HUNT_MODE_APPROACH = "APPROACH";
    public static final String HUNT_MODE_FIXED_DISTANCE = "FIXED_DISTANCE";
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final Type HUNT_PICKUP_RULE_LIST_TYPE = new TypeToken<List<HuntPickupRule>>() {
    }.getType();
    private static final Type PRESET_LIST_TYPE = new TypeToken<List<KillAuraPreset>>() {
    }.getType();

    public static boolean enabled = false;
    public static boolean rotateToTarget = true;
    public static boolean smoothRotation = true;
    public static final float MIN_SMOOTH_MAX_TURN_STEP = 0.5F;
    public static final float MAX_SMOOTH_MAX_TURN_STEP = 60.0F;
    public static final float DEFAULT_SMOOTH_MAX_TURN_STEP = 24.0F;
    public static final String DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC = "24-60";
    public static float smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
    public static String smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
    public static boolean rotateOnlyOnAttack = true;
    public static boolean relockOnlyWhenNoCrosshairTarget = true;
    public static boolean onlyAttackWhenLookingAtTarget = true;
    public static boolean requireLineOfSight = true;
    public static boolean throughWallAttack = false;
    public static boolean targetHostile = true;
    public static boolean targetPassive = false;
    public static boolean targetPlayers = false;
    public static boolean targetEnderCrystal = false;
    public static boolean onlyWeapon = false;
    public static boolean aimOnlyMode = false;
    public static boolean focusSingleTarget = true;
    public static boolean ignoreInvisible = true;
    public static boolean enableNoCollision = true;
    public static boolean enableAntiKnockback = true;
    public static boolean enableFullBrightVision = false;
    public static float fullBrightGamma = 1000.0F;
    public static String attackMode = DEFAULT_ATTACK_MODE;
    public static String attackSequenceName = "";
    public static final int MIN_ATTACK_SEQUENCE_DELAY_TICKS = 0;
    public static final int MAX_ATTACK_SEQUENCE_DELAY_TICKS = 200;
    public static final int DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS = 1;
    public static final String DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC = "1-6";
    public static int attackSequenceDelayTicks = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS;
    public static String attackSequenceDelayTicksSpec = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC;
    public static final float MIN_AIM_OFFSET = -30.0F;
    public static final float MAX_AIM_OFFSET = 30.0F;
    public static final float DEFAULT_AIM_OFFSET = 0.0F;
    public static final float DEFAULT_AIM_YAW_OFFSET = -3.0F;
    public static final String DEFAULT_AIM_YAW_OFFSET_SPEC = "-3-3";
    public static final float DEFAULT_AIM_PITCH_OFFSET = -11.0F;
    public static final String DEFAULT_AIM_PITCH_OFFSET_SPEC = "-11-11";
    public static float aimYawOffset = DEFAULT_AIM_YAW_OFFSET;
    public static String aimYawOffsetSpec = DEFAULT_AIM_YAW_OFFSET_SPEC;
    public static float aimPitchOffset = DEFAULT_AIM_PITCH_OFFSET;
    public static String aimPitchOffsetSpec = DEFAULT_AIM_PITCH_OFFSET_SPEC;
    public static boolean huntEnabled = true;
    public static String huntMode = HUNT_MODE_APPROACH;
    public static boolean huntPickupItemsEnabled = false;
    public static final String HUNT_PICKUP_RULE_MODE_ALLOW = "ALLOW";
    public static final String HUNT_PICKUP_RULE_MODE_BLOCK = "BLOCK";
    public static final String HUNT_PICKUP_RARITY_COMMON = "common";
    public static final String HUNT_PICKUP_RARITY_UNCOMMON = "uncommon";
    public static final String HUNT_PICKUP_RARITY_RARE = "rare";
    public static final String HUNT_PICKUP_RARITY_EPIC = "epic";
    public static List<HuntPickupRule> huntPickupRules = new ArrayList<>();
    public static boolean visualizeHuntRadius = false;
    public static float huntRadius = 8.0F;
    public static float huntFixedDistance = 4.2F;
    public static final float DEFAULT_HUNT_UP_RANGE = 1.0F;
    public static final float DEFAULT_HUNT_DOWN_RANGE = 1.0F;
    public static float huntUpRange = DEFAULT_HUNT_UP_RANGE;
    public static float huntDownRange = DEFAULT_HUNT_DOWN_RANGE;
    public static boolean huntOrbitEnabled = false;
    public static boolean huntJumpOrbitEnabled = true;
    public static final int MIN_HUNT_ORBIT_SAMPLE_POINTS = 3;
    public static final int MAX_HUNT_ORBIT_SAMPLE_POINTS = 360;
    public static final int DEFAULT_HUNT_ORBIT_SAMPLE_POINTS = MAX_HUNT_ORBIT_SAMPLE_POINTS;
    public static int huntOrbitSamplePoints = DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
    public static boolean enableNameWhitelist = false;
    public static boolean enableNameBlacklist = false;
    public static List<String> nameWhitelist = new ArrayList<>();
    public static List<String> nameBlacklist = new ArrayList<>();
    public static float nearbyEntityScanRange = 10.0F;
    public static final List<KillAuraPreset> presets = new ArrayList<>();

    public static float attackRange = 4.2F;
    public static final float DEFAULT_HUNT_SCORE_RADIUS_WEIGHT = 4.0F;
    public static final float DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT = 0.18F;
    public static final float DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT = 1.8F;
    public static final float DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT = 0.12F;
    public static final float DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT = 8.0F;
    public static final float DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT = 12.0F;
    public static final float DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT = 0.65F;
    public static float huntScoreRadiusWeight = DEFAULT_HUNT_SCORE_RADIUS_WEIGHT;
    public static float huntScorePlayerDistanceWeight = DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT;
    public static float huntScorePlayerPlaneWeight = DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT;
    public static float huntScoreTargetHeightWeight = DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT;
    public static float huntScoreAttackRangeWeight = DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT;
    public static float huntScoreVisibilityWeight = DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT;
    public static float huntScoreOpennessWeight = DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT;
    public static float minAttackStrength = 0.92F;
    public static float minTurnSpeed = 4.0F;
    public static float maxTurnSpeed = 18.0F;
    public static int minAttackIntervalTicks = 2;
    public static int targetsPerAttack = 1;
    public static final int MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK = 1;
    public static final int MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK = 320;
    public static final int DEFAULT_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK = 24;
    public static int teleportAttackPacketLimitPerTick = DEFAULT_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK;
    public static final float MIN_TELEPORT_STEP_DISTANCE = 0.5F;
    public static final float MAX_TELEPORT_STEP_DISTANCE = 200.0F;
    public static final float DEFAULT_TELEPORT_STEP_DISTANCE = 20.0F;
    public static float teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
    public static final float MIN_TELEPORT_STATION_ATTACK_RADIUS = 0.5F;
    public static final float MAX_TELEPORT_STATION_ATTACK_RADIUS = 200.0F;
    public static final float DEFAULT_TELEPORT_STATION_ATTACK_RADIUS = 5.5F;
    public static float teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
    public static boolean disableOnDisconnect = true;
    public static final int DEFAULT_NO_DAMAGE_ATTACK_LIMIT = 10;
    public static final int MAX_NO_DAMAGE_ATTACK_LIMIT = 100;
    public static int noDamageAttackLimit = DEFAULT_NO_DAMAGE_ATTACK_LIMIT;

    private static final int HUNT_GOTO_INTERVAL_TICKS = 6;
    private static final double HUNT_GOTO_MOVE_THRESHOLD_SQ = 1.0D;
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
    private static final int TELEPORT_STATION_POSITION_PROBE_LIMIT = 8;
    private static final int TELEPORT_ROUTE_COLLISION_PROBE_LIMIT = 8;
    private static final int TELEPORT_ROUTE_LOADED_QUERY_LIMIT = 32;
    private static final int TELEPORT_PLANNING_WORLD_QUERY_LIMIT = 128;
    private static final int TELEPORT_PLANNING_QUERIES_PER_SEED = 44;
    private static final int TELEPORT_RECOVERY_ROUTE_LIMIT = 192;
    private static final int TELEPORT_PLANNING_FAILURE_BACKOFF_TICKS = 3;
    private static final int TELEPORT_TARGET_BUDGET_FAILURE_BACKOFF_TICKS = 6;
    private static final int TELEPORT_TARGET_ROUTE_FAILURE_BACKOFF_TICKS = 20;
    private static final long TELEPORT_TOUR_PLANNING_BUDGET_NANOS = 1_000_000L;
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

    private int attackCooldownTicks = 0;
    private int sequenceCooldownTicks = 0;
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
    private int lastTeleportCorrectionTick = Integer.MIN_VALUE;
    private int teleportPacketBudgetTick = Integer.MIN_VALUE;
    private int teleportPacketsUsedThisTick = 0;
    private int teleportTargetScanCursor = 0;
    private int teleportCrystalScanCursor = 0;
    private int teleportStationAlternativeCursor = 0;
    private final List<String> teleportDebugBuffer = new ArrayList<>();
    private int teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
    private final LinkedHashSet<Integer> teleportTargetCandidateEntityIds = new LinkedHashSet<>();
    private final Map<Integer, Integer> teleportLastAttackTicks = new LinkedHashMap<>();
    private final Map<Integer, Integer> teleportTargetRetryAfterTicks = new LinkedHashMap<>();
    private final AttackSequenceExecutor attackSequenceExecutor = new AttackSequenceExecutor();
    private final HuntOrbitController huntOrbitController = new HuntOrbitController();
    private final Map<Integer, NoDamageAttackTracker> noDamageAttackTrackers = new LinkedHashMap<>();
    private final Set<Integer> noDamageExcludedEntityIds = new LinkedHashSet<>();
    private final Map<Integer, HuntUnreachableTracker> huntUnreachableTrackers = new LinkedHashMap<>();
    private final List<HuntScoreDebugEntry> huntScoreDebugEntries = new ArrayList<>();
    private int lastHuntScoreDebugTick = Integer.MIN_VALUE;
    private int areaHuntControlTicks = 0;

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

    public static class KillAuraPreset {
        public String name = "";
        public boolean rotateToTarget = true;
        public boolean smoothRotation = true;
        public float smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
        public String smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
        public boolean rotateOnlyOnAttack = true;
        public boolean relockOnlyWhenNoCrosshairTarget = true;
        public boolean onlyAttackWhenLookingAtTarget = true;
        public boolean requireLineOfSight = true;
        public boolean throughWallAttack = false;
        public boolean targetHostile = true;
        public boolean targetPassive = false;
        public boolean targetPlayers = false;
        public boolean targetEnderCrystal = false;
        public boolean onlyWeapon = false;
        public boolean aimOnlyMode = false;
        public boolean focusSingleTarget = true;
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
        public int targetsPerAttack = 1;
        public int teleportAttackPacketLimitPerTick = DEFAULT_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK;
        public float teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
        public float teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
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
            this.targetEnderCrystal = other.targetEnderCrystal;
            this.onlyWeapon = other.onlyWeapon;
            this.aimOnlyMode = other.aimOnlyMode;
            this.focusSingleTarget = other.focusSingleTarget;
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
            this.teleportAttackPacketLimitPerTick = other.teleportAttackPacketLimitPerTick;
            this.teleportStepDistance = other.teleportStepDistance;
            this.teleportStationAttackRadius = other.teleportStationAttackRadius;
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
        return ProfileManager.getCurrentProfileDir().resolve("keycommand_killaura.json").toFile();
    }

    public static void loadConfig() {
        enabled = false;
        rotateToTarget = true;
        smoothRotation = true;
        smoothMaxTurnStep = DEFAULT_SMOOTH_MAX_TURN_STEP;
        smoothMaxTurnStepSpec = DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC;
        rotateOnlyOnAttack = true;
        relockOnlyWhenNoCrosshairTarget = true;
        onlyAttackWhenLookingAtTarget = true;
        requireLineOfSight = true;
        throughWallAttack = false;
        targetHostile = true;
        targetPassive = false;
        targetPlayers = false;
        targetEnderCrystal = false;
        onlyWeapon = false;
        aimOnlyMode = false;
        focusSingleTarget = true;
        ignoreInvisible = true;
        enableNoCollision = true;
        enableAntiKnockback = true;
        enableFullBrightVision = false;
        fullBrightGamma = 1000.0F;
        attackMode = DEFAULT_ATTACK_MODE;
        attackSequenceName = "";
        attackSequenceDelayTicks = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS;
        attackSequenceDelayTicksSpec = DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC;
        aimYawOffset = DEFAULT_AIM_YAW_OFFSET;
        aimYawOffsetSpec = DEFAULT_AIM_YAW_OFFSET_SPEC;
        aimPitchOffset = DEFAULT_AIM_PITCH_OFFSET;
        aimPitchOffsetSpec = DEFAULT_AIM_PITCH_OFFSET_SPEC;
        huntEnabled = true;
        huntMode = HUNT_MODE_APPROACH;
        huntPickupItemsEnabled = false;
        huntPickupRules = new ArrayList<>();
        visualizeHuntRadius = false;
        huntRadius = 8.0F;
        huntFixedDistance = 4.2F;
        huntUpRange = DEFAULT_HUNT_UP_RANGE;
        huntDownRange = DEFAULT_HUNT_DOWN_RANGE;
        huntOrbitEnabled = false;
        huntJumpOrbitEnabled = true;
        huntOrbitSamplePoints = DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        enableNameWhitelist = false;
        enableNameBlacklist = false;
        nameWhitelist = new ArrayList<>();
        nameBlacklist = new ArrayList<>();
        nearbyEntityScanRange = 10.0F;
        presets.clear();

        attackRange = 4.2F;
        resetHuntScoreWeights();
        minAttackStrength = 0.92F;
        minTurnSpeed = 4.0F;
        maxTurnSpeed = 18.0F;
        minAttackIntervalTicks = 2;
        targetsPerAttack = 1;
        teleportAttackPacketLimitPerTick = DEFAULT_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK;
        teleportStepDistance = DEFAULT_TELEPORT_STEP_DISTANCE;
        teleportStationAttackRadius = DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        disableOnDisconnect = true;
        noDamageAttackLimit = DEFAULT_NO_DAMAGE_ATTACK_LIMIT;

        try {
            File configFile = getConfigFile();
            if (!configFile.exists()) {
                normalizeConfig();
                return;
            }

            JsonConfigCharsetCompat.ReadResult readResult = JsonConfigCharsetCompat.readJsonObject(configFile.toPath());
            JsonObject json = readResult.getRoot();
            boolean needsRewrite = readResult.usedLegacyCharset();
            if (json.has("enabled")) {
                enabled = json.get("enabled").getAsBoolean();
            }
            if (json.has("rotateToTarget")) {
                rotateToTarget = json.get("rotateToTarget").getAsBoolean();
            }
            if (json.has("smoothRotation")) {
                smoothRotation = json.get("smoothRotation").getAsBoolean();
            }
            if (json.has("smoothMaxTurnStep")) {
                setSmoothMaxTurnStepSpec(json.get("smoothMaxTurnStep").getAsString());
            }
            if (json.has("smoothMaxTurnStepSpec")) {
                setSmoothMaxTurnStepSpec(json.get("smoothMaxTurnStepSpec").getAsString());
            }
            if (json.has("rotateOnlyOnAttack")) {
                rotateOnlyOnAttack = json.get("rotateOnlyOnAttack").getAsBoolean();
            }
            if (json.has("relockOnlyWhenNoCrosshairTarget")) {
                relockOnlyWhenNoCrosshairTarget = json.get("relockOnlyWhenNoCrosshairTarget").getAsBoolean();
            }
            if (json.has("onlyAttackWhenLookingAtTarget")) {
                onlyAttackWhenLookingAtTarget = json.get("onlyAttackWhenLookingAtTarget").getAsBoolean();
            }
            if (json.has("requireLineOfSight")) {
                requireLineOfSight = json.get("requireLineOfSight").getAsBoolean();
            }
            if (json.has("throughWallAttack")) {
                throughWallAttack = json.get("throughWallAttack").getAsBoolean();
            }
            if (json.has("targetHostile")) {
                targetHostile = json.get("targetHostile").getAsBoolean();
            }
            if (json.has("targetPassive")) {
                targetPassive = json.get("targetPassive").getAsBoolean();
            }
            if (json.has("targetPlayers")) {
                targetPlayers = json.get("targetPlayers").getAsBoolean();
            }
            if (json.has("targetEnderCrystal")) {
                targetEnderCrystal = json.get("targetEnderCrystal").getAsBoolean();
            }
            if (json.has("onlyWeapon")) {
                onlyWeapon = json.get("onlyWeapon").getAsBoolean();
            }
            if (json.has("aimOnlyMode")) {
                aimOnlyMode = json.get("aimOnlyMode").getAsBoolean();
            }
            if (json.has("focusSingleTarget")) {
                focusSingleTarget = json.get("focusSingleTarget").getAsBoolean();
            }
            if (json.has("ignoreInvisible")) {
                ignoreInvisible = json.get("ignoreInvisible").getAsBoolean();
            }
            if (json.has("enableNoCollision")) {
                enableNoCollision = json.get("enableNoCollision").getAsBoolean();
            }
            if (json.has("enableAntiKnockback")) {
                enableAntiKnockback = json.get("enableAntiKnockback").getAsBoolean();
            }
            if (json.has("enableFullBrightVision")) {
                enableFullBrightVision = json.get("enableFullBrightVision").getAsBoolean();
            }
            if (json.has("fullBrightGamma")) {
                fullBrightGamma = json.get("fullBrightGamma").getAsFloat();
            }
            if (json.has("attackMode")) {
                attackMode = json.get("attackMode").getAsString();
            }
            if (json.has("attackSequenceName")) {
                attackSequenceName = json.get("attackSequenceName").getAsString();
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
                huntMode = json.get("huntMode").getAsString();
            } else if (json.has("huntEnabled")) {
                huntMode = json.get("huntEnabled").getAsBoolean() ? HUNT_MODE_APPROACH : HUNT_MODE_OFF;
            }
            boolean hasHuntFixedDistance = json.has("huntFixedDistance");
            if (json.has("huntPickupItemsEnabled")) {
                huntPickupItemsEnabled = json.get("huntPickupItemsEnabled").getAsBoolean();
            }
            if (json.has("huntPickupRules") && json.get("huntPickupRules").isJsonArray()) {
                List<HuntPickupRule> loadedRules = GSON.fromJson(json.get("huntPickupRules"),
                        HUNT_PICKUP_RULE_LIST_TYPE);
                huntPickupRules = normalizeHuntPickupRuleList(loadedRules);
            }
            if (json.has("visualizeHuntRadius")) {
                visualizeHuntRadius = json.get("visualizeHuntRadius").getAsBoolean();
            }
            if (json.has("huntRadius")) {
                huntRadius = json.get("huntRadius").getAsFloat();
            }
            if (hasHuntFixedDistance) {
                huntFixedDistance = json.get("huntFixedDistance").getAsFloat();
            }
            if (json.has("huntUpRange") && !json.get("huntUpRange").isJsonNull()) {
                huntUpRange = json.get("huntUpRange").getAsFloat();
            } else {
                migratedHuntVerticalDefaults = true;
            }
            if (json.has("huntDownRange") && !json.get("huntDownRange").isJsonNull()) {
                huntDownRange = json.get("huntDownRange").getAsFloat();
            } else {
                migratedHuntVerticalDefaults = true;
            }
            if (json.has("huntOrbitEnabled")) {
                huntOrbitEnabled = json.get("huntOrbitEnabled").getAsBoolean();
            }
            if (json.has("huntJumpOrbitEnabled")) {
                huntJumpOrbitEnabled = json.get("huntJumpOrbitEnabled").getAsBoolean();
            }
            if (json.has("huntOrbitSamplePoints")) {
                huntOrbitSamplePoints = json.get("huntOrbitSamplePoints").getAsInt();
            }
            if (json.has("enableNameWhitelist")) {
                enableNameWhitelist = json.get("enableNameWhitelist").getAsBoolean();
            }
            if (json.has("enableNameBlacklist")) {
                enableNameBlacklist = json.get("enableNameBlacklist").getAsBoolean();
            }
            if (json.has("nameWhitelist") && json.get("nameWhitelist").isJsonArray()) {
                List<String> loaded = GSON.fromJson(json.get("nameWhitelist"), STRING_LIST_TYPE);
                nameWhitelist = normalizeNameList(loaded);
            }
            if (json.has("nameBlacklist") && json.get("nameBlacklist").isJsonArray()) {
                List<String> loaded = GSON.fromJson(json.get("nameBlacklist"), STRING_LIST_TYPE);
                nameBlacklist = normalizeNameList(loaded);
            }
            if (json.has("nearbyEntityScanRange")) {
                nearbyEntityScanRange = json.get("nearbyEntityScanRange").getAsFloat();
            }
            if (json.has("presets") && json.get("presets").isJsonArray()) {
                List<KillAuraPreset> loadedPresets = GSON.fromJson(json.get("presets"), PRESET_LIST_TYPE);
                presets.clear();
                if (loadedPresets != null) {
                    for (KillAuraPreset preset : loadedPresets) {
                        KillAuraPreset normalizedPreset = normalizePreset(preset);
                        if (normalizedPreset != null) {
                            presets.add(normalizedPreset);
                        }
                    }
                }
            }

            if (json.has("attackRange")) {
                attackRange = json.get("attackRange").getAsFloat();
            }
            if (json.has("huntScoreRadiusWeight")) {
                huntScoreRadiusWeight = json.get("huntScoreRadiusWeight").getAsFloat();
            }
            if (json.has("huntScorePlayerDistanceWeight")) {
                huntScorePlayerDistanceWeight = json.get("huntScorePlayerDistanceWeight").getAsFloat();
            }
            if (json.has("huntScorePlayerPlaneWeight")) {
                huntScorePlayerPlaneWeight = json.get("huntScorePlayerPlaneWeight").getAsFloat();
            }
            if (json.has("huntScoreTargetHeightWeight")) {
                huntScoreTargetHeightWeight = json.get("huntScoreTargetHeightWeight").getAsFloat();
            }
            if (json.has("huntScoreAttackRangeWeight")) {
                huntScoreAttackRangeWeight = json.get("huntScoreAttackRangeWeight").getAsFloat();
            }
            if (json.has("huntScoreVisibilityWeight")) {
                huntScoreVisibilityWeight = json.get("huntScoreVisibilityWeight").getAsFloat();
            }
            if (json.has("huntScoreOpennessWeight")) {
                huntScoreOpennessWeight = json.get("huntScoreOpennessWeight").getAsFloat();
            }
            if (!hasHuntFixedDistance) {
                huntFixedDistance = attackRange;
            }
            if (json.has("minAttackStrength")) {
                minAttackStrength = json.get("minAttackStrength").getAsFloat();
            }
            if (json.has("minTurnSpeed")) {
                minTurnSpeed = json.get("minTurnSpeed").getAsFloat();
            }
            if (json.has("maxTurnSpeed")) {
                maxTurnSpeed = json.get("maxTurnSpeed").getAsFloat();
            }
            if (json.has("minAttackIntervalTicks")) {
                minAttackIntervalTicks = json.get("minAttackIntervalTicks").getAsInt();
            }
            if (json.has("targetsPerAttack")) {
                targetsPerAttack = json.get("targetsPerAttack").getAsInt();
            }
            if (json.has("teleportAttackPacketLimitPerTick")) {
                teleportAttackPacketLimitPerTick = json.get("teleportAttackPacketLimitPerTick").getAsInt();
            }
            if (json.has("teleportStepDistance")) {
                teleportStepDistance = json.get("teleportStepDistance").getAsFloat();
            }
            if (json.has("teleportStationAttackRadius")) {
                teleportStationAttackRadius = json.get("teleportStationAttackRadius").getAsFloat();
            }
            if (json.has("disableOnDisconnect")) {
                disableOnDisconnect = json.get("disableOnDisconnect").getAsBoolean();
            }
            if (json.has("noDamageAttackLimit")) {
                noDamageAttackLimit = json.get("noDamageAttackLimit").getAsInt();
            }

            normalizeConfig();
            if (migratedHuntVerticalDefaults || needsRewrite) {
                saveConfig();
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.kill_aura.load_failed"), e);
        }
    }

    public static void saveConfig() {
        normalizeConfig();
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("rotateToTarget", rotateToTarget);
            json.addProperty("smoothRotation", smoothRotation);
            json.addProperty("smoothMaxTurnStep", smoothMaxTurnStepSpec);
            json.addProperty("smoothMaxTurnStepValue", smoothMaxTurnStep);
            json.addProperty("rotateOnlyOnAttack", rotateOnlyOnAttack);
            json.addProperty("relockOnlyWhenNoCrosshairTarget", relockOnlyWhenNoCrosshairTarget);
            json.addProperty("onlyAttackWhenLookingAtTarget", onlyAttackWhenLookingAtTarget);
            json.addProperty("requireLineOfSight", requireLineOfSight);
            json.addProperty("throughWallAttack", throughWallAttack);
            json.addProperty("targetHostile", targetHostile);
            json.addProperty("targetPassive", targetPassive);
            json.addProperty("targetPlayers", targetPlayers);
            json.addProperty("targetEnderCrystal", targetEnderCrystal);
            json.addProperty("onlyWeapon", onlyWeapon);
            json.addProperty("aimOnlyMode", aimOnlyMode);
            json.addProperty("focusSingleTarget", focusSingleTarget);
            json.addProperty("ignoreInvisible", ignoreInvisible);
            json.addProperty("enableNoCollision", enableNoCollision);
            json.addProperty("enableAntiKnockback", enableAntiKnockback);
            json.addProperty("enableFullBrightVision", enableFullBrightVision);
            json.addProperty("fullBrightGamma", fullBrightGamma);
            json.addProperty("attackMode", attackMode);
            json.addProperty("attackSequenceName", attackSequenceName);
            json.addProperty("attackSequenceDelayTicks", attackSequenceDelayTicksSpec);
            json.addProperty("attackSequenceDelayTicksValue", attackSequenceDelayTicks);
            json.addProperty("aimYawOffset", aimYawOffsetSpec);
            json.addProperty("aimYawOffsetValue", aimYawOffset);
            json.addProperty("aimYawOffsetSpec", aimYawOffsetSpec);
            json.addProperty("aimPitchOffset", aimPitchOffsetSpec);
            json.addProperty("aimPitchOffsetValue", aimPitchOffset);
            json.addProperty("aimPitchOffsetSpec", aimPitchOffsetSpec);
            json.addProperty("huntMode", huntMode);
            json.addProperty("huntEnabled", isHuntEnabled());
            json.addProperty("huntPickupItemsEnabled", huntPickupItemsEnabled);
            json.add("huntPickupRules",
                    GSON.toJsonTree(copyHuntPickupRuleList(huntPickupRules), HUNT_PICKUP_RULE_LIST_TYPE));
            json.addProperty("visualizeHuntRadius", visualizeHuntRadius);
            json.addProperty("huntRadius", huntRadius);
            json.addProperty("huntFixedDistance", huntFixedDistance);
            json.addProperty("huntUpRange", huntUpRange);
            json.addProperty("huntDownRange", huntDownRange);
            json.addProperty("huntOrbitEnabled", huntOrbitEnabled);
            json.addProperty("huntJumpOrbitEnabled", huntJumpOrbitEnabled);
            json.addProperty("huntOrbitSamplePoints", huntOrbitSamplePoints);
            json.addProperty("enableNameWhitelist", enableNameWhitelist);
            json.addProperty("enableNameBlacklist", enableNameBlacklist);
            json.add("nameWhitelist", GSON.toJsonTree(normalizeNameList(nameWhitelist), STRING_LIST_TYPE));
            json.add("nameBlacklist", GSON.toJsonTree(normalizeNameList(nameBlacklist), STRING_LIST_TYPE));
            json.addProperty("nearbyEntityScanRange", nearbyEntityScanRange);
            json.add("presets", GSON.toJsonTree(getPresetSnapshots(), PRESET_LIST_TYPE));
            json.addProperty("attackRange", attackRange);
            json.addProperty("huntScoreRadiusWeight", huntScoreRadiusWeight);
            json.addProperty("huntScorePlayerDistanceWeight", huntScorePlayerDistanceWeight);
            json.addProperty("huntScorePlayerPlaneWeight", huntScorePlayerPlaneWeight);
            json.addProperty("huntScoreTargetHeightWeight", huntScoreTargetHeightWeight);
            json.addProperty("huntScoreAttackRangeWeight", huntScoreAttackRangeWeight);
            json.addProperty("huntScoreVisibilityWeight", huntScoreVisibilityWeight);
            json.addProperty("huntScoreOpennessWeight", huntScoreOpennessWeight);
            json.addProperty("minAttackStrength", minAttackStrength);
            json.addProperty("minTurnSpeed", minTurnSpeed);
            json.addProperty("maxTurnSpeed", maxTurnSpeed);
            json.addProperty("minAttackIntervalTicks", minAttackIntervalTicks);
            json.addProperty("targetsPerAttack", targetsPerAttack);
            json.addProperty("teleportAttackPacketLimitPerTick", teleportAttackPacketLimitPerTick);
            json.addProperty("teleportStepDistance", teleportStepDistance);
            json.addProperty("teleportStationAttackRadius", teleportStationAttackRadius);
            json.addProperty("disableOnDisconnect", disableOnDisconnect);
            json.addProperty("noDamageAttackLimit", noDamageAttackLimit);

            JsonConfigCharsetCompat.writeJsonObject(configFile.toPath(), json);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(I18n.format("log.kill_aura.save_failed"), e);
        }
    }

    public void toggleEnabled() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean targetEnabled) {
        Minecraft mc = Minecraft.getMinecraft();
        normalizeConfig();
        if (enabled == targetEnabled) {
            saveConfig();
            return;
        }

        enabled = targetEnabled;
        resetRuntimeState();
        saveConfig();

        if (mc.player != null) {
            mc.player.sendMessage(
                    new TextComponentString(I18n.format(enabled ? "msg.kill_aura.enabled" : "msg.kill_aura.disabled")));
        }
    }

    public void onClientDisconnect() {
        if (disableOnDisconnect && enabled) {
            enabled = false;
            saveConfig();
        }
        resetRuntimeState();
    }

    public static boolean isHuntEnabled() {
        return !HUNT_MODE_OFF.equals(normalizeHuntModeValue(huntMode));
    }

    public static boolean isHuntApproachMode() {
        return HUNT_MODE_APPROACH.equals(normalizeHuntModeValue(huntMode));
    }

    public static boolean isHuntFixedDistanceMode() {
        return HUNT_MODE_FIXED_DISTANCE.equals(normalizeHuntModeValue(huntMode));
    }

    public static void setHuntMode(String mode) {
        huntMode = normalizeHuntModeValue(mode);
        huntEnabled = !HUNT_MODE_OFF.equals(huntMode);
        huntRadius = Math.max(huntRadius, attackRange);
        if (!huntEnabled) {
            visualizeHuntRadius = false;
        }
    }

    public static synchronized List<KillAuraPreset> getPresetSnapshots() {
        List<KillAuraPreset> snapshots = new ArrayList<>();
        for (KillAuraPreset preset : presets) {
            KillAuraPreset normalizedPreset = normalizePreset(preset);
            if (normalizedPreset != null) {
                snapshots.add(new KillAuraPreset(normalizedPreset));
            }
        }
        return snapshots;
    }

    public static synchronized List<HuntPickupRule> getHuntPickupRuleSnapshots() {
        return copyHuntPickupRuleList(huntPickupRules);
    }

    public static synchronized void replaceHuntPickupRules(List<HuntPickupRule> rules) {
        huntPickupRules = normalizeHuntPickupRuleList(rules);
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
            presets.set(existingIndex, preset);
        } else {
            presets.add(preset);
        }
        saveConfig();
        return true;
    }

    public static synchronized boolean overwritePreset(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        presets.set(index, captureCurrentAsPreset(presets.get(index).name));
        saveConfig();
        return true;
    }

    public static synchronized boolean applyPresetByName(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        applyPreset(presets.get(index));
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
        presets.get(index).name = normalizedNew;
        saveConfig();
        return true;
    }

    public static synchronized boolean deletePreset(String name) {
        int index = findPresetIndex(name);
        if (index < 0) {
            return false;
        }
        presets.remove(index);
        saveConfig();
        return true;
    }

    private static void applyPreset(KillAuraPreset preset) {
        KillAuraPreset safePreset = normalizePreset(preset);
        if (safePreset == null) {
            return;
        }
        rotateToTarget = safePreset.rotateToTarget;
        smoothRotation = safePreset.smoothRotation;
        setSmoothMaxTurnStepSpec(safePreset.smoothMaxTurnStepSpec);
        rotateOnlyOnAttack = safePreset.rotateOnlyOnAttack;
        relockOnlyWhenNoCrosshairTarget = safePreset.relockOnlyWhenNoCrosshairTarget;
        onlyAttackWhenLookingAtTarget = safePreset.onlyAttackWhenLookingAtTarget;
        requireLineOfSight = safePreset.requireLineOfSight;
        throughWallAttack = safePreset.throughWallAttack;
        targetHostile = safePreset.targetHostile;
        targetPassive = safePreset.targetPassive;
        targetPlayers = safePreset.targetPlayers;
        targetEnderCrystal = safePreset.targetEnderCrystal;
        onlyWeapon = safePreset.onlyWeapon;
        aimOnlyMode = safePreset.aimOnlyMode;
        focusSingleTarget = safePreset.focusSingleTarget;
        ignoreInvisible = safePreset.ignoreInvisible;
        enableNoCollision = safePreset.enableNoCollision;
        enableAntiKnockback = safePreset.enableAntiKnockback;
        enableFullBrightVision = safePreset.enableFullBrightVision;
        fullBrightGamma = safePreset.fullBrightGamma;
        attackMode = safePreset.attackMode;
        attackSequenceName = safePreset.attackSequenceName;
        setAttackSequenceDelayTicksSpec(safePreset.attackSequenceDelayTicksSpec);
        setAimYawOffsetSpec(safePreset.aimYawOffsetSpec);
        setAimPitchOffsetSpec(safePreset.aimPitchOffsetSpec);
        huntMode = safePreset.huntMode;
        huntPickupItemsEnabled = safePreset.huntPickupItemsEnabled;
        huntPickupRules = copyHuntPickupRuleList(safePreset.huntPickupRules);
        visualizeHuntRadius = safePreset.visualizeHuntRadius;
        huntRadius = safePreset.huntRadius;
        huntFixedDistance = safePreset.huntFixedDistance;
        huntUpRange = safePreset.huntUpRange;
        huntDownRange = safePreset.huntDownRange;
        huntOrbitEnabled = safePreset.huntOrbitEnabled;
        huntJumpOrbitEnabled = safePreset.huntJumpOrbitEnabled;
        huntOrbitSamplePoints = safePreset.huntOrbitSamplePoints;
        enableNameWhitelist = safePreset.enableNameWhitelist;
        enableNameBlacklist = safePreset.enableNameBlacklist;
        nameWhitelist = new ArrayList<>(safePreset.nameWhitelist);
        nameBlacklist = new ArrayList<>(safePreset.nameBlacklist);
        nearbyEntityScanRange = safePreset.nearbyEntityScanRange;
        attackRange = safePreset.attackRange;
        huntScoreRadiusWeight = safePreset.huntScoreRadiusWeight;
        huntScorePlayerDistanceWeight = safePreset.huntScorePlayerDistanceWeight;
        huntScorePlayerPlaneWeight = safePreset.huntScorePlayerPlaneWeight;
        huntScoreTargetHeightWeight = safePreset.huntScoreTargetHeightWeight;
        huntScoreAttackRangeWeight = safePreset.huntScoreAttackRangeWeight;
        huntScoreVisibilityWeight = safePreset.huntScoreVisibilityWeight;
        huntScoreOpennessWeight = safePreset.huntScoreOpennessWeight;
        minAttackStrength = safePreset.minAttackStrength;
        minTurnSpeed = safePreset.minTurnSpeed;
        maxTurnSpeed = safePreset.maxTurnSpeed;
        minAttackIntervalTicks = safePreset.minAttackIntervalTicks;
        targetsPerAttack = safePreset.targetsPerAttack;
        teleportAttackPacketLimitPerTick = safePreset.teleportAttackPacketLimitPerTick;
        teleportStepDistance = safePreset.teleportStepDistance;
        teleportStationAttackRadius = safePreset.teleportStationAttackRadius;
        disableOnDisconnect = safePreset.disableOnDisconnect;
        noDamageAttackLimit = safePreset.noDamageAttackLimit;
        normalizeConfig();
        INSTANCE.resetRuntimeState();
        saveConfig();
    }

    public void resetRuntimeState() {
        stopHuntPickupNavigation();
        stopHuntNavigation();
        this.attackCooldownTicks = 0;
        this.sequenceCooldownTicks = 0;
        this.currentTargetEntityId = -1;
        clearAimTargetTransition();
        resetSmoothTurnLimitBudget();
        clearVisualRotationCache();
        this.huntNavigationActive = false;
        this.lastHuntGotoTick = -99999;
        this.lastHuntTargetEntityId = Integer.MIN_VALUE;
        this.lastHuntTargetX = 0.0D;
        this.lastHuntTargetZ = 0.0D;
        this.lastHuntGoalUsesY = false;
        this.huntPickupNavigationActive = false;
        this.lastHuntPickupGotoTick = -99999;
        this.lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
        this.huntPickupReachedEntityId = Integer.MIN_VALUE;
        this.lastHuntPickupSearchTick = -99999;
        this.lastHuntPickupSearchTargetEntityId = Integer.MIN_VALUE;
        this.lastHuntPickupSearchFound = false;
        this.huntScoreDebugEntries.clear();
        this.lastHuntScoreDebugTick = Integer.MIN_VALUE;
        this.huntPickupLastProgressTick = -99999;
        this.huntPickupLastDistanceSq = Double.MAX_VALUE;
        this.huntPickupRetryAfterTick = -99999;
        this.lastOrbitProcessRequestTick = -99999;
        this.lastOrbitProcessTargetEntityId = Integer.MIN_VALUE;
        this.lastOrbitProcessRequestedRadius = Double.NaN;
        this.lastSafeMotionX = 0.0D;
        this.lastSafeMotionY = 0.0D;
        this.lastSafeMotionZ = 0.0D;
        this.activeTeleportAttackPlan = null;
        this.activeTeleportCorrectionPlan = null;
        this.pendingTeleportReturnPlan = null;
        this.pendingTeleportReturnTicks = 0;
        this.lastTeleportCorrectionTick = Integer.MIN_VALUE;
        this.teleportPacketBudgetTick = Integer.MIN_VALUE;
        this.teleportPacketsUsedThisTick = 0;
        this.teleportTargetScanCursor = 0;
        this.teleportCrystalScanCursor = 0;
        this.teleportStationAlternativeCursor = 0;
        this.teleportDebugBuffer.clear();
        this.teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
        this.teleportTargetCandidateEntityIds.clear();
        this.teleportLastAttackTicks.clear();
        this.teleportTargetRetryAfterTicks.clear();
        this.attackSequenceExecutor.stop();
        clearNoDamageAttackTracking();
        this.areaHuntControlTicks = 0;
    }

    public boolean hasActiveTarget(EntityPlayerSP player) {
        return getActiveTarget(player).isPresent();
    }

    public Optional<EntityLivingBase> getActiveTarget(EntityPlayerSP player) {
        if (!enabled || player == null || player.world == null || this.currentTargetEntityId == -1) {
            return Optional.empty();
        }

        Entity target = player.world.getEntityByID(this.currentTargetEntityId);
        double targetSearchRadius = getTargetSearchRadius();
        boolean useWhitelistPriority = enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
        if (target instanceof EntityLivingBase
                && isTrackableTarget(player, (EntityLivingBase) target, targetSearchRadius * targetSearchRadius,
                        useWhitelistPriority)) {
            return Optional.of((EntityLivingBase) target);
        }
        return Optional.empty();
    }

    public Optional<Rotation> getVisualTargetRotation(EntityPlayerSP player) {
        if (player == null || player.world == null || !shouldRotateToTarget() || this.currentTargetEntityId == -1) {
            return Optional.empty();
        }
        if (!shouldApplyContinuousRotation(false)) {
            return Optional.empty();
        }
        Entity target = player.world.getEntityByID(this.currentTargetEntityId);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || player == null || target == null || mc.playerController == null) {
            return false;
        }
        if (aimOnlyMode || isSequenceAttackMode()) {
            return false;
        }
        if (isTeleportAttackRecoveryActive()) {
            return false;
        }
        if (onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
            return false;
        }
        if ((isPacketAttackMode() || isTeleportAttackMode()) && player.connection == null) {
            return false;
        }
        if (player.getCooledAttackStrength(0.0F) < minAttackStrength) {
            return false;
        }
        if (isTeleportAttackMode()) {
            double searchRangeSq = attackRange * attackRange;
            boolean useWhitelistPriority = enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
            if (buildTargetCandidate(player, target, searchRangeSq, useWhitelistPriority,
                    target.getEntityId() == this.currentTargetEntityId, true, null) == null) {
                return false;
            }
            this.currentTargetEntityId = target.getEntityId();
            updateAimTargetTransition(target);
            boolean attacked = performTeleportAttack(player, target);
            if (attacked) {
                player.swingArm(EnumHand.MAIN_HAND);
            }
            decayTargetSwitchSmoothTicks();
            return attacked;
        }
        if (!canAttackTargetBeforeRotation(player, target)) {
            return false;
        }

        this.currentTargetEntityId = target.getEntityId();
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

        boolean attacked = false;
        if (teleportAttack) {
            attacked = performTeleportAttack(player, target);
        } else if (isMouseClickAttackMode()) {
            attacked = performMouseClickAttack(mc);
        } else if (isPacketAttackMode()) {
            player.connection.sendPacket(new CPacketUseEntity(target));
            attacked = true;
        } else {
            mc.playerController.attackEntity(player, target);
            attacked = true;
        }
        if (attacked && !isMouseClickAttackMode()) {
            player.swingArm(EnumHand.MAIN_HAND);
        }
        if (attacked && !teleportAttack) {
            recordNoDamageAttackAttempt(target);
        }
        decayTargetSwitchSmoothTicks();
        return attacked;
    }

    public boolean prepareCurrentConfigSequenceAttackForHunt(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null || !isSequenceAttackMode() || !hasConfiguredAttackSequence()) {
            return false;
        }
        if (!isValidTarget(player, target)) {
            return false;
        }
        this.currentTargetEntityId = target.getEntityId();
        updateAimTargetTransition(target);
        EntityLivingBase crosshairLockedTarget = isRelockSuppressedByCrosshairTarget(player, target) ? target : null;
        boolean prepared = prepareRotationForAttack(player, target, crosshairLockedTarget);
        decayTargetSwitchSmoothTicks();
        return prepared;
    }

    public static boolean isConfiguredSequenceAttackMode() {
        return ATTACK_MODE_SEQUENCE.equalsIgnoreCase(attackMode);
    }

    public static int sampleCurrentConfigAttackCooldownTicks() {
        return ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(attackMode)
                ? sampleAttackSequenceDelayTicks()
                : minAttackIntervalTicks;
    }

    public static int sampleCurrentConfigSequenceDelayTicks() {
        return sampleAttackSequenceDelayTicks();
    }

    public AreaHuntTickResult tickAreaHunt(EntityPlayerSP player, AreaHuntOptions options) {
        this.areaHuntControlTicks = 2;
        ensureBaritonePacketListenerRegistered();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || player == null || mc.world == null) {
            return new AreaHuntTickResult(false, null, 0, this.attackSequenceExecutor.isRunning());
        }

        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }
        if (this.sequenceCooldownTicks > 0) {
            this.sequenceCooldownTicks--;
        }
        tickTeleportAttackRecovery(player);
        applyFullBright(enableFullBrightVision);

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
                    "targets=%d,targetIds=%s,currentTarget=%d,cache=%d,history=%d,noDamageExcluded=%d,cooldown=%d,gate=%s,packetsUsed=%d,packetLimit=%d,recoveryActive=%s,pendingReturn=%s",
                    targets.size(), formatTeleportTargetIds(targets, 24), this.currentTargetEntityId,
                    this.teleportTargetCandidateEntityIds.size(), this.teleportLastAttackTicks.size(),
                    noDamageExcludedEntityIds.size(), this.attackCooldownTicks, getTeleportAttackGateReason(player),
                    this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick(),
                    isTeleportAttackRecoveryActive(), this.pendingTeleportReturnPlan != null);
        }
        if (targets.isEmpty()) {
            if (isTeleportAttackMode()) {
                teleportDebug(player, "AREA_ATTACK_STOP", "reason=NO_TARGETS_AFTER_SCAN");
            }
            clearAreaHuntRuntimeState(false);
            return new AreaHuntTickResult(false, null, 0, this.attackSequenceExecutor.isRunning());
        }

        EntityLivingBase primaryTarget = targets.get(0);
        if (!aimOnlyMode && !sequenceAttackMode && onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
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
            if (canTriggerAttackSequence(player, primaryTarget, options)
                    && prepareRotationForAttack(player, primaryTarget, crosshairTargetLocked ? primaryTarget : null)
                    && triggerAttackSequence(player, primaryTarget)) {
                recordNoDamageAttackAttempt(primaryTarget);
                this.sequenceCooldownTicks = sampleAttackSequenceDelayTicks();
            }
            decayTargetSwitchSmoothTicks();
            return new AreaHuntTickResult(true, primaryTarget, 0, this.attackSequenceExecutor.isRunning());
        }

        if (aimOnlyMode) {
            decayTargetSwitchSmoothTicks();
            return new AreaHuntTickResult(true, primaryTarget, 0, false);
        }

        boolean canStartAreaAttack = canStartAttack(player);
        if (isTeleportAttackMode() && !canStartAreaAttack) {
            teleportDebug(player, "AREA_ATTACK_STOP", "reason=%s,primary={%s}",
                    getTeleportAttackGateReason(player), formatTeleportTarget(player, primaryTarget));
        }
        if (canStartAreaAttack && mc.playerController != null) {
            attackedCount = attackTargets(mc, player, targets, crosshairTargetLocked ? primaryTarget : null, options);
            if (attackedCount > 0) {
                if (!isMouseClickAttackMode()) {
                    player.swingArm(EnumHand.MAIN_HAND);
                }
                this.attackCooldownTicks = isMouseClickAttackMode()
                        ? sampleAttackSequenceDelayTicks()
                        : minAttackIntervalTicks;
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

    public void stopAreaHuntAction() {
        clearAreaHuntRuntimeState(true);
        this.areaHuntControlTicks = 0;
        clearNoDamageAttackTracking();
    }

    public boolean isAreaHuntSequenceRunning() {
        return this.attackSequenceExecutor.isRunning();
    }

    public void tickAreaHuntSequenceOnly(EntityPlayerSP player) {
        this.areaHuntControlTicks = 2;
        if (player != null && this.attackSequenceExecutor.isRunning()) {
            this.attackSequenceExecutor.tick(player);
        }
    }

    private void clearAreaHuntRuntimeState(boolean stopSequence) {
        this.currentTargetEntityId = -1;
        clearAimTargetTransition();
        stopHuntPickupNavigation();
        stopHuntNavigation();
        clearHuntUnreachableTracking();
        if (stopSequence) {
            this.attackSequenceExecutor.stop();
        }
    }

    private void ensureBaritonePacketListenerRegistered() {
        try {
            IBaritone primaryBaritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            IEventBus eventBus = primaryBaritone == null ? null : primaryBaritone.getGameEventHandler();
            if (eventBus == null || eventBus == this.registeredBaritoneEventBus) {
                return;
            }
            eventBus.registerEventListener(this);
            this.registeredBaritoneEventBus = eventBus;
        } catch (Throwable ignored) {
        }
    }

    private boolean isTeleportAttackRecoveryActive() {
        return this.activeTeleportAttackPlan != null && this.pendingTeleportReturnPlan != null;
    }

    private void tickTeleportAttackRecovery(EntityPlayerSP player) {
        if (this.pendingTeleportReturnTicks > 0) {
            this.pendingTeleportReturnTicks--;
        }
        if (this.activeTeleportAttackPlan == null) {
            return;
        }
        if (player == null || player.connection == null) {
            clearTeleportAttackState(player, "PLAYER_OR_CONNECTION_NULL");
            return;
        }

        if (this.pendingTeleportReturnPlan != null) {
            if (!tryExecutePendingTeleportReturn(player)) {
                teleportDebug(player, "RECOVERY_WAIT",
                        "reason=PENDING_RETURN_NOT_EXECUTED,returnTicks=%d,notBeforeTick=%d,packetsUsed=%d,packetLimit=%d",
                        this.pendingTeleportReturnTicks, this.pendingTeleportReturnPlan.notBeforeTick,
                        this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick());
                return;
            }
            teleportDebug(player, "RECOVERY_RETURN_SENT", "returnTicks=%d", this.pendingTeleportReturnTicks);
            return;
        }

        if (isPlayerNearTeleportOrigin(player, this.activeTeleportAttackPlan)) {
            this.activeTeleportAttackPlan.returnCompleted = true;
            if (this.pendingTeleportReturnTicks <= 0) {
                clearTeleportAttackState(player, "AT_ORIGIN_AND_WINDOW_EXPIRED");
            }
            return;
        }

        if (this.pendingTeleportReturnTicks > 0) {
            teleportDebug(player, "RECOVERY_WAIT",
                    "reason=AWAITING_SERVER_CORRECTION,returnTicks=%d,player=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    this.pendingTeleportReturnTicks, player.posX, player.posY, player.posZ,
                    this.activeTeleportAttackPlan.originX, this.activeTeleportAttackPlan.originY,
                    this.activeTeleportAttackPlan.originZ);
            return;
        }

        TeleportAttackPlan correctionPlan = this.activeTeleportCorrectionPlan == null
                ? this.activeTeleportAttackPlan
                : this.activeTeleportCorrectionPlan;
        if (correctionPlan.correctedByServer
                && correctionPlan.correctionCount < TELEPORT_ATTACK_MAX_CORRECTIONS
                && player.ticksExisted != this.lastTeleportCorrectionTick) {
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
        clearTeleportAttackState(mc == null ? null : mc.player, "UNSPECIFIED");
    }

    private void clearTeleportAttackState(EntityPlayerSP player, String reason) {
        teleportDebug(player, "RECOVERY_CLEAR",
                "reason=%s,activePlan=%s,correctionPlan=%s,pendingReturn=%s,returnTicks=%d,lastCorrectionTick=%d",
                reason, this.activeTeleportAttackPlan != null, this.activeTeleportCorrectionPlan != null,
                this.pendingTeleportReturnPlan != null, this.pendingTeleportReturnTicks,
                this.lastTeleportCorrectionTick);
        this.activeTeleportAttackPlan = null;
        this.activeTeleportCorrectionPlan = null;
        this.pendingTeleportReturnPlan = null;
        this.pendingTeleportReturnTicks = 0;
        this.lastTeleportCorrectionTick = Integer.MIN_VALUE;
    }

    private void syncTeleportPacketBudget(EntityPlayerSP player) {
        int currentTick = player == null ? Integer.MIN_VALUE : player.ticksExisted;
        if (currentTick != this.teleportPacketBudgetTick) {
            this.teleportPacketBudgetTick = currentTick;
            this.teleportPacketsUsedThisTick = 0;
        }
    }

    private boolean reserveTeleportPackets(EntityPlayerSP player, int packetCount) {
        if (player == null || packetCount < 0) {
            teleportDebug(player, "PACKET_RESERVE_FAIL", "reason=INVALID_INPUT,requested=%d", packetCount);
            return false;
        }
        syncTeleportPacketBudget(player);
        if (packetCount > getTeleportAttackPacketLimitPerTick() - this.teleportPacketsUsedThisTick) {
            teleportDebug(player, "PACKET_RESERVE_FAIL",
                    "reason=LIMIT_EXCEEDED,requested=%d,used=%d,limit=%d,remaining=%d",
                    packetCount, this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick(),
                    getTeleportAttackPacketLimitPerTick() - this.teleportPacketsUsedThisTick);
            return false;
        }
        this.teleportPacketsUsedThisTick += packetCount;
        teleportDebug(player, "PACKET_RESERVED", "reserved=%d,usedNow=%d,limit=%d",
                packetCount, this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick());
        return true;
    }

    private void recordTeleportPackets(EntityPlayerSP player, int packetCount) {
        if (player == null || packetCount <= 0) {
            return;
        }
        syncTeleportPacketBudget(player);
        this.teleportPacketsUsedThisTick = Math.min(Integer.MAX_VALUE - packetCount,
                this.teleportPacketsUsedThisTick) + packetCount;
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
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.player || mc.player == null || mc.world == null) {
            return;
        }

        boolean flyEnabled = FlyHandler.enabled;
        boolean areaHuntActive = this.areaHuntControlTicks > 0;
        boolean movementProtectionActive = enabled || areaHuntActive || flyEnabled;
        boolean useNoCollision = ((enabled || areaHuntActive) && enableNoCollision)
                || (flyEnabled && FlyHandler.enableNoCollision);
        boolean useAntiKnockback = ((enabled || areaHuntActive) && enableAntiKnockback)
                || (flyEnabled && FlyHandler.enableAntiKnockback);
        applyKillAuraOwnMovementProtection(mc.player, movementProtectionActive, useNoCollision, useAntiKnockback);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("kill_aura")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("kill_aura");
        try {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            ensureBaritonePacketListenerRegistered();

            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.player;
            if (player == null || mc.world == null) {
                return;
            }

            if (this.areaHuntControlTicks > 0) {
                this.areaHuntControlTicks--;
                return;
            }

            if (this.attackCooldownTicks > 0) {
                this.attackCooldownTicks--;
            }
            if (this.sequenceCooldownTicks > 0) {
                this.sequenceCooldownTicks--;
            }
            tickTeleportAttackRecovery(player);

            boolean flyEnabled = FlyHandler.enabled;
            boolean movementProtectionActive = enabled || flyEnabled;
            boolean useNoCollision = (enabled && enableNoCollision)
                    || (flyEnabled && FlyHandler.enableNoCollision);
            boolean useAntiKnockback = (enabled && enableAntiKnockback)
                    || (flyEnabled && FlyHandler.enableAntiKnockback);
            applyKillAuraOwnMovementProtection(player, movementProtectionActive, useNoCollision, useAntiKnockback);
            applyFullBright(enableFullBrightVision);

            if (!enabled) {
                this.attackCooldownTicks = 0;
                this.sequenceCooldownTicks = 0;
                this.currentTargetEntityId = -1;
                clearAimTargetTransition();
                stopHuntPickupNavigation();
                if (!PathSequenceEventListener.isAnyHuntOrbitActionRunning()) {
                    stopHuntNavigation();
                }
                this.attackSequenceExecutor.stop();
                if (!movementProtectionActive) {
                    this.lastSafeMotionX = 0.0D;
                    this.lastSafeMotionY = 0.0D;
                    this.lastSafeMotionZ = 0.0D;
                }
                clearNoDamageAttackTracking();
                clearHuntUnreachableTracking();
                return;
            }

            if (player.isDead || player.getHealth() <= 0.0F || player.isSpectator()) {
                this.currentTargetEntityId = -1;
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

            if (!aimOnlyMode && !sequenceAttackMode && onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
                this.currentTargetEntityId = -1;
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
            EntityItem huntPriorityPickupItem = (!isTeleportAttackMode() && !autoPickupRuleAreaActive
                    && isHuntEnabled() && huntPickupItemsEnabled)
                            ? findHuntPriorityPickupItem(player)
                            : null;

            List<EntityLivingBase> targets = findTargets(player);
            if (teleportAttackMode) {
                syncTeleportPacketBudget(player);
                teleportDebug(player, "TICK_STATE",
                        "enabled=%s,targets=%d,targetIds=%s,currentTarget=%d,cache=%d,history=%d,noDamageExcluded=%d,cooldown=%d,gate=%s,packetsUsed=%d,packetLimit=%d,retryAfter=%d,recoveryActive=%s,activeRecovery=%s,pendingReturn=%s,returnTicks=%d",
                        enabled, targets.size(), formatTeleportTargetIds(targets, 24), this.currentTargetEntityId,
                        this.teleportTargetCandidateEntityIds.size(), this.teleportLastAttackTicks.size(),
                        noDamageExcludedEntityIds.size(), this.attackCooldownTicks, getTeleportAttackGateReason(player),
                        this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick(),
                        this.teleportPlanningRetryAfterTick, isTeleportAttackRecoveryActive(),
                        this.activeTeleportAttackPlan != null, this.pendingTeleportReturnPlan != null,
                        this.pendingTeleportReturnTicks);
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
                this.currentTargetEntityId = -1;
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
                stopHuntPickupNavigation();
                stopHuntNavigation();
            }

            if (sequenceAttackMode) {
                this.attackSequenceExecutor.tick(player);
                if (canTriggerAttackSequence(player, primaryTarget)
                        && prepareRotationForAttack(player, primaryTarget, crosshairTargetLocked ? primaryTarget : null)
                        && triggerAttackSequence(player, primaryTarget)) {
                    recordNoDamageAttackAttempt(primaryTarget);
                    this.sequenceCooldownTicks = sampleAttackSequenceDelayTicks();
                }
                decayTargetSwitchSmoothTicks();
                return;
            }

            if (aimOnlyMode) {
                decayTargetSwitchSmoothTicks();
                return;
            }

            boolean canStartCurrentAttack = canStartAttack(player);
            if (teleportAttackMode && !canStartCurrentAttack) {
                teleportDebug(player, "ATTACK_STOP", "reason=%s,primary={%s}",
                        getTeleportAttackGateReason(player), formatTeleportTarget(player, primaryTarget));
            }
            if (canStartCurrentAttack && mc.playerController != null) {
                int attackedCount = attackTargets(mc, player, targets, crosshairTargetLocked ? primaryTarget : null);
                if (attackedCount > 0) {
                    if (!isMouseClickAttackMode()) {
                        player.swingArm(EnumHand.MAIN_HAND);
                    }
                    this.attackCooldownTicks = isMouseClickAttackMode()
                            ? sampleAttackSequenceDelayTicks()
                            : minAttackIntervalTicks;
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
            flushTeleportDebug();
            timer.stop();
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
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        TeleportAttackPlan recoveryPlan = this.activeTeleportAttackPlan;
        if (packet == null || player == null || player.connection == null || recoveryPlan == null
                || this.pendingTeleportReturnTicks <= 0) {
            teleportDebug(player, "CORRECTION_IGNORED",
                    "packetNull=%s,playerNull=%s,connectionNull=%s,recoveryPlanNull=%s,returnTicks=%d",
                    packet == null, player == null, player == null || player.connection == null,
                    recoveryPlan == null, this.pendingTeleportReturnTicks);
            return;
        }

        // Vanilla has already sent ConfirmTeleport and PositionRotation before this
        // RETURN callback.
        recordTeleportPackets(player, 2);

        double correctedX = player.posX;
        double correctedY = player.posY;
        double correctedZ = player.posZ;
        teleportDebug(player, "CORRECTION_RECEIVED",
                "corrected=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f),correctionCount=%d,returnTicks=%d,packetsUsed=%d",
                correctedX, correctedY, correctedZ, recoveryPlan.originX, recoveryPlan.originY,
                recoveryPlan.originZ, recoveryPlan.correctionCount, this.pendingTeleportReturnTicks,
                this.teleportPacketsUsedThisTick);
        if (isSamePosition(correctedX, correctedY, correctedZ,
                recoveryPlan.originX, recoveryPlan.originY, recoveryPlan.originZ)) {
            recoveryPlan.returnCompleted = true;
            this.activeTeleportCorrectionPlan = null;
            this.pendingTeleportReturnPlan = null;
            teleportDebug(player, "CORRECTION_AT_ORIGIN", "noReturnRequired=true");
            return;
        }
        TeleportAttackPlan correctionPlan = findTeleportCorrectionPlan(recoveryPlan,
                correctedX, correctedY, correctedZ);
        if (correctionPlan == null) {
            clearTeleportAttackState(player, "CORRECTION_DID_NOT_MATCH_KNOWN_ROUTE");
            return;
        }

        if (correctionPlan.correctionCount >= TELEPORT_ATTACK_MAX_CORRECTIONS) {
            clearTeleportAttackState(player, "CORRECTION_LIMIT_REACHED");
            return;
        }

        boolean deferUntilNextTick = player.ticksExisted == this.lastTeleportCorrectionTick;
        this.lastTeleportCorrectionTick = player.ticksExisted;
        if (!scheduleTeleportCorrectionReturn(player, correctionPlan, correctedX, correctedY, correctedZ)) {
            clearTeleportAttackState(player, "CORRECTION_RETURN_PLANNING_FAILED");
            return;
        }
        if (deferUntilNextTick && this.pendingTeleportReturnPlan != null) {
            this.pendingTeleportReturnPlan.notBeforeTick = player.ticksExisted + 1;
            teleportDebug(player, "CORRECTION_DEFERRED", "notBeforeTick=%d",
                    this.pendingTeleportReturnPlan.notBeforeTick);
        }
        if (!deferUntilNextTick) {
            tryExecutePendingTeleportReturn(player);
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
        if (!PerformanceMonitor.isFeatureEnabled("kill_aura")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("kill_aura");
        try {
            if (!enabled && this.areaHuntControlTicks <= 0) {
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc.player;
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

            if (isHuntEnabled() && visualizeHuntRadius) {
                drawHuntRadiusAura(worldCenterX, worldCenterY, worldCenterZ, viewerX, viewerY, viewerZ, huntRadius);
            }
            if (enabled && throughWallAttack) {
                renderThroughWallAttackBlocks(player, viewerX, viewerY, viewerZ);
            }
            renderHuntOrbitLoop();
        } finally {
            timer.stop();
        }
    }

    private void renderThroughWallAttackBlocks(EntityPlayerSP player, double viewerX, double viewerY, double viewerZ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || player == null || this.currentTargetEntityId == -1) {
            return;
        }
        Entity entity = mc.world.getEntityByID(this.currentTargetEntityId);
        if (!(entity instanceof EntityLivingBase) || entity.isDead) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        if (target.getHealth() <= 0.0F || !isValidTarget(player, target)) {
            return;
        }

        Vec3d from = player.getPositionEyes(1.0F);
        Vec3d to = getEntityCenter(target);
        List<BlockPos> blockingBlocks = collectBlockingBlocksBetween(mc.world, from, to, 80);
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
            AxisAlignedBB box = getWorldBlockOverlayBox(mc.world, pos);
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
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || player.world == null) {
            return new ArrayList<>();
        }
        int nowTick = player.ticksExisted;
        if (this.lastHuntScoreDebugTick != Integer.MIN_VALUE
                && nowTick - this.lastHuntScoreDebugTick < 10) {
            return new ArrayList<>(this.huntScoreDebugEntries);
        }

        this.lastHuntScoreDebugTick = nowTick;
        this.huntScoreDebugEntries.clear();
        double searchRadius = getTargetSearchRadius();
        double searchRadiusSq = searchRadius * searchRadius;
        boolean useWhitelistPriority = enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
        double preferredRadius = isHuntFixedDistanceMode()
                ? Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, getEffectiveHuntFixedDistance())
                : Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, attackRange - HUNT_APPROACH_TARGET_BUFFER * 2.0D);

        for (Entity entity : player.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || this.huntScoreDebugEntries.size() >= 24) {
                continue;
            }
            EntityLivingBase target = (EntityLivingBase) entity;
            if (buildTargetCandidate(player, target, searchRadiusSq, useWhitelistPriority,
                    target.getEntityId() == this.currentTargetEntityId,
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
            this.huntScoreDebugEntries.add(new HuntScoreDebugEntry(target, breakdown, destination));
        }
        this.huntScoreDebugEntries.sort((left, right) -> Double.compare(left.totalScore, right.totalScore));
        return new ArrayList<>(this.huntScoreDebugEntries);
    }

    private List<EntityLivingBase> findTargets(EntityPlayerSP player, AreaHuntOptions areaOptions) {
        if (isTeleportAttackMode()) {
            return findTeleportTargetsBounded(player, areaOptions);
        }
        List<EntityLivingBase> targets = new ArrayList<>();
        EntityLivingBase lockedTarget = null;
        double targetSearchRadius = areaOptions == null ? getTargetSearchRadius() : Double.MAX_VALUE;
        double targetSearchRadiusSq = targetSearchRadius * targetSearchRadius;
        boolean useWhitelistPriority = areaOptions == null
                && enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
        boolean preferStableOrbitTarget = isHuntOrbitEnabled();
        int previousTargetEntityId = this.currentTargetEntityId;

        if (focusSingleTarget && this.currentTargetEntityId != -1) {
            Entity existing = player.world.getEntityByID(this.currentTargetEntityId);
            if (existing instanceof EntityLivingBase
                    && isTrackableTarget(player, (EntityLivingBase) existing, targetSearchRadiusSq,
                            useWhitelistPriority,
                            areaOptions)) {
                lockedTarget = (EntityLivingBase) existing;
                targets.add(lockedTarget);
            }
        }

        List<TargetCandidate> nearbyTargets = new ArrayList<>();

        for (Entity entity : player.world.loadedEntityList) {
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
        if (nextTargetEntityId != this.currentTargetEntityId) {
            clearVisualRotationCache();
        }
        this.currentTargetEntityId = nextTargetEntityId;
        return targets;
    }

    private List<EntityLivingBase> findTeleportTargetsBounded(EntityPlayerSP player, AreaHuntOptions areaOptions) {
        List<EntityLivingBase> targets = new ArrayList<>();
        if (player == null || player.world == null) {
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=PLAYER_OR_WORLD_NULL");
            return targets;
        }
        if (!aimOnlyMode && getTeleportAttackPacketLimitPerTick() <= 1) {
            this.currentTargetEntityId = -1;
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=PACKET_LIMIT_TOO_LOW,limit=%d",
                    getTeleportAttackPacketLimitPerTick());
            return targets;
        }
        int currentTick = player.ticksExisted;
        if (!aimOnlyMode && currentTick < this.teleportPlanningRetryAfterTick) {
            this.currentTargetEntityId = -1;
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=PLANNING_BACKOFF,retryAfter=%d,remainingTicks=%d",
                    this.teleportPlanningRetryAfterTick, this.teleportPlanningRetryAfterTick - currentTick);
            return targets;
        }

        List<Entity> loadedEntities = player.world.loadedEntityList;
        int loadedCount = loadedEntities == null ? 0 : loadedEntities.size();
        if (loadedCount <= 0) {
            this.teleportTargetScanCursor = 0;
            this.teleportTargetCandidateEntityIds.clear();
            this.currentTargetEntityId = -1;
            teleportDebug(player, "TARGET_SCAN_STOP", "reason=NO_LOADED_ENTITIES");
            return targets;
        }

        double searchRadiusSq = attackRange * attackRange;
        boolean useWhitelistPriority = areaOptions == null
                && enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
        int scanCount = Math.min(TELEPORT_TARGET_SCAN_PER_TICK, loadedCount);
        int cursor = Math.floorMod(this.teleportTargetScanCursor, loadedCount);
        teleportDebug(player, "TARGET_SCAN_BEGIN",
                "loaded=%d,scanCount=%d,cursor=%d,cacheBefore=%d,range=%.3f,areaMode=%s",
                loadedCount, scanCount, cursor, this.teleportTargetCandidateEntityIds.size(), attackRange,
                areaOptions != null);
        trimTeleportRuntimeMaps();
        for (int scanned = 0; scanned < scanCount; scanned++) {
            Entity entity = loadedEntities.get((cursor + scanned) % loadedCount);
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) entity;
            if (currentTick < this.teleportTargetRetryAfterTicks.getOrDefault(candidate.getEntityId(),
                    Integer.MIN_VALUE)) {
                this.teleportTargetCandidateEntityIds.remove(candidate.getEntityId());
                teleportDebug(player, "TARGET_REJECT",
                        "phase=SCAN,reason=TARGET_RETRY_BACKOFF,retryAfter=%d,target={%s}",
                        this.teleportTargetRetryAfterTicks.getOrDefault(candidate.getEntityId(), Integer.MIN_VALUE),
                        formatTeleportTarget(player, candidate));
                continue;
            }
            TargetCandidate targetCandidate = buildTargetCandidate(player, candidate, searchRadiusSq,
                    useWhitelistPriority, candidate.getEntityId() == this.currentTargetEntityId, true, areaOptions);
            if (targetCandidate == null) {
                this.teleportTargetCandidateEntityIds.remove(candidate.getEntityId());
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
        this.teleportTargetScanCursor = (cursor + scanCount) % loadedCount;

        if (this.currentTargetEntityId != -1) {
            addTeleportTargetCandidate(this.currentTargetEntityId);
        }

        List<TargetCandidate> candidates = new ArrayList<>();
        Iterator<Integer> iterator = this.teleportTargetCandidateEntityIds.iterator();
        while (iterator.hasNext()) {
            int entityId = iterator.next();
            if (currentTick < this.teleportTargetRetryAfterTicks.getOrDefault(entityId, Integer.MIN_VALUE)) {
                iterator.remove();
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,id=%d,reason=TARGET_RETRY_BACKOFF",
                        entityId);
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                iterator.remove();
                this.teleportLastAttackTicks.remove(entityId);
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,id=%d,reason=ENTITY_NOT_LIVING_OR_UNLOADED",
                        entityId);
                continue;
            }
            EntityLivingBase candidate = (EntityLivingBase) entity;
            TargetCandidate targetCandidate = buildTargetCandidate(player, candidate, searchRadiusSq,
                    useWhitelistPriority, entityId == this.currentTargetEntityId, true, areaOptions);
            if (targetCandidate == null) {
                iterator.remove();
                this.teleportLastAttackTicks.remove(entityId);
                teleportDebug(player, "TARGET_REMOVE", "phase=CACHE_VALIDATE,reason=%s,target={%s}",
                        getTeleportTargetRejectionReason(player, candidate, searchRadiusSq,
                                useWhitelistPriority, areaOptions),
                        formatTeleportTarget(player, candidate));
                continue;
            }
            candidates.add(targetCandidate);
        }

        candidates.sort((left, right) -> {
            int leftAttackTick = this.teleportLastAttackTicks.getOrDefault(left.entity.getEntityId(),
                    Integer.MIN_VALUE);
            int rightAttackTick = this.teleportLastAttackTicks.getOrDefault(right.entity.getEntityId(),
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
        if (nextTargetEntityId != this.currentTargetEntityId) {
            clearVisualRotationCache();
        }
        this.currentTargetEntityId = nextTargetEntityId;
        teleportDebug(player, "TARGET_SCAN_RESULT",
                "cacheAfter=%d,validCandidates=%d,returned=%d,returnedIds=%s,currentTarget=%d",
                this.teleportTargetCandidateEntityIds.size(), candidates.size(), targets.size(),
                formatTeleportTargetIds(targets, 24), this.currentTargetEntityId);
        for (int i = 0; i < targets.size(); i++) {
            EntityLivingBase target = targets.get(i);
            teleportDebug(player, "TARGET_PRIORITY", "index=%d,target={%s}", i,
                    formatTeleportTarget(player, target));
        }
        return targets;
    }

    private void addTeleportTargetCandidate(int entityId) {
        this.teleportTargetCandidateEntityIds.add(entityId);
        while (this.teleportTargetCandidateEntityIds.size() > TELEPORT_TARGET_CACHE_LIMIT) {
            Iterator<Integer> iterator = this.teleportTargetCandidateEntityIds.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            int evictedEntityId = iterator.next();
            iterator.remove();
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayerSP player = mc == null ? null : mc.player;
            teleportDebug(player, "TARGET_CACHE_EVICT",
                    "evictedId=%d,admittedId=%d,cacheLimit=%d,evictedLastAttackTick=%d",
                    evictedEntityId, entityId, TELEPORT_TARGET_CACHE_LIMIT,
                    this.teleportLastAttackTicks.getOrDefault(evictedEntityId, Integer.MIN_VALUE));
        }
    }

    private EntityEnderCrystal findBestEnderCrystalTarget(EntityPlayerSP player) {
        if (!targetEnderCrystal || player == null || player.world == null) {
            return null;
        }
        EntityEnderCrystal best = null;
        int bestWhitelistPriority = Integer.MAX_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;
        List<Entity> loadedEntities = player.world.loadedEntityList;
        int loadedCount = loadedEntities.size();
        int scanCount = isTeleportAttackMode() ? Math.min(16, loadedCount) : loadedCount;
        int cursor = loadedCount <= 0 ? 0 : Math.floorMod(this.teleportCrystalScanCursor, loadedCount);
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
            int whitelistPriority = enableNameWhitelist
                    ? getNormalizedNameListMatchIndex(crystalName, nameWhitelist)
                    : 0;
            double distanceSq = player.getDistanceSq(crystal);
            if (enableNameWhitelist) {
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
            this.teleportCrystalScanCursor = (cursor + scanCount) % loadedCount;
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
        if (!isHuntEnabled() && player.getDistanceSq(crystal) > attackRange * attackRange) {
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
        if (enableNameBlacklist && matchesNameList(targetName, nameBlacklist)) {
            return false;
        }
        if (enableNameWhitelist) {
            if (getNormalizedNameListMatchIndex(targetName, nameWhitelist) == Integer.MAX_VALUE) {
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
        if (enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty()) {
            int crystalPriority = getNormalizedNameListMatchIndex(getFilterableEntityName(crystalTarget),
                    nameWhitelist);
            int livingPriority = getNormalizedNameListMatchIndex(getFilterableEntityName(primaryLivingTarget),
                    nameWhitelist);
            if (crystalPriority != livingPriority) {
                return crystalPriority < livingPriority;
            }
        }
        return player.getDistanceSq(crystalTarget) <= player.getDistanceSq(primaryLivingTarget);
    }

    private void handleEnderCrystalTarget(Minecraft mc, EntityPlayerSP player, EntityEnderCrystal crystalTarget) {
        if (mc == null || player == null || crystalTarget == null) {
            return;
        }
        if (this.currentTargetEntityId != crystalTarget.getEntityId()) {
            clearAimTargetTransition();
            clearVisualRotationCache();
        }
        this.currentTargetEntityId = crystalTarget.getEntityId();
        stopHuntPickupNavigation();
        if (shouldRunHuntMovementForEntity(player, crystalTarget)) {
            handleHuntMovementForEntity(player, crystalTarget);
        } else {
            stopHuntNavigation();
        }

        if (shouldRotateToTarget()) {
            applyRotationToEntity(player, crystalTarget);
        }

        if (aimOnlyMode) {
            decayTargetSwitchSmoothTicks();
            return;
        }
        if (!canStartAttack(player) || mc.playerController == null) {
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
            mc.playerController.attackEntity(player, crystalTarget);
            attacked = true;
        }
        if (attacked) {
            if (!isMouseClickAttackMode()) {
                player.swingArm(EnumHand.MAIN_HAND);
            }
            this.attackCooldownTicks = isMouseClickAttackMode()
                    ? sampleAttackSequenceDelayTicks()
                    : minAttackIntervalTicks;
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
            return missingAttackLineOfSight
                    || Math.abs(distance - getEffectiveHuntFixedDistance()) > HUNT_FIXED_DISTANCE_TOLERANCE;
        }
        return missingAttackLineOfSight || distance > attackRange;
    }

    private void handleHuntMovementForEntity(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            stopHuntNavigation();
            return;
        }
        int nowTick = player.ticksExisted;
        int targetId = target.getEntityId();
        double dx = target.posX - this.lastHuntTargetX;
        double dz = target.posZ - this.lastHuntTargetZ;
        double movedSq = dx * dx + dz * dz;
        boolean targetChanged = targetId != this.lastHuntTargetEntityId;
        boolean targetMoved = movedSq >= HUNT_GOTO_MOVE_THRESHOLD_SQ;
        boolean previousGoalReached = !targetChanged && !targetMoved && hasReachedLastHuntGoal(player);

        // A target switch must invalidate the old goal immediately. Otherwise a
        // dead target's last landing point can keep winning this early-return
        // branch until the player happens to move off that block.
        if (huntNavigationActive && !previousGoalReached && !targetChanged && !targetMoved) {
            if (!EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()
                    && !Double.isNaN(this.lastHuntGoalX)
                    && !Double.isNaN(this.lastHuntGoalZ)
                    && (!this.lastHuntGoalUsesY || !Double.isNaN(this.lastHuntGoalY))
                    && (nowTick - this.lastHuntGotoTick) >= HUNT_GOTO_INTERVAL_TICKS) {
                boolean dispatched = dispatchHuntNavigationGoal(target, this.lastHuntGoalX, this.lastHuntGoalY,
                        this.lastHuntGoalZ, this.lastHuntGoalUsesY, nowTick);
                if (!dispatched) {
                    // Keep a failed refresh from being attempted every game tick,
                    // while still allowing the normal retry path to recover.
                    this.lastHuntGotoTick = nowTick;
                }
            }
            return;
        }

        boolean shouldSendGoto = !huntNavigationActive
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
        double searchDistance = Math.max(0.1D, attackRange);
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
        double searchDistance = Math.max(0.1D, attackRange);
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
        if (mc == null || mc.world == null || eyePos == null || hitVec == null) {
            return false;
        }
        RayTraceResult blockRay = mc.world.rayTraceBlocks(eyePos, hitVec, false, true, false);
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
        if (enableNameBlacklist && matchesNameList(targetName, nameBlacklist)) {
            return false;
        }
        if (enableNameWhitelist) {
            if (getNormalizedNameListMatchIndex(targetName, nameWhitelist) == Integer.MAX_VALUE) {
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
        if (player.getDistanceSq(target) > attackRange * attackRange) {
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
        double searchDistance = Math.max(0.1D, attackRange);
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
                areaOptions == null && enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty(),
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
        if (!isTeleportAttackMode() && isHuntUnreachableExcludedTarget(player, target)) {
            return null;
        }
        if (target instanceof EntityArmorStand) {
            return null;
        }
        if (ignoreInvisible && target.isInvisible()) {
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
            if (enableNameBlacklist && matchesNameList(targetName, nameBlacklist)) {
                return null;
            }
            if (enableNameWhitelist) {
                whitelistPriority = getNormalizedNameListMatchIndex(targetName, nameWhitelist);
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
        double yawToTarget = Math.toDegrees(Math.atan2(target.posZ - player.posZ, target.posX - player.posX)) - 90.0D;
        float yawDeltaAbs = isHuntOrbitEnabled()
                ? Math.abs(MathHelper.wrapDegrees((float) yawToTarget - player.rotationYaw))
                : 0.0F;
        return new TargetCandidate(target, distanceSq, useWhitelistPriority ? whitelistPriority : 0,
                isCurrentTarget ? 0 : 1, yawDeltaAbs);
    }

    private static boolean isWithinConfiguredHuntVerticalRange(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            return false;
        }
        double dy = target.posY - player.posY;
        return dy <= huntUpRange + 1.0E-6D && -dy <= huntDownRange + 1.0E-6D;
    }

    private double getTargetSearchDistanceSq(EntityPlayerSP player, Entity target) {
        if (player == null || target == null) {
            return Double.MAX_VALUE;
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

    private boolean canStartAttack(EntityPlayerSP player) {
        if (player == null) {
            return false;
        }
        if (aimOnlyMode) {
            return false;
        }
        if (isSequenceAttackMode()) {
            return false;
        }
        if (this.attackCooldownTicks > 0) {
            return false;
        }
        if (isTeleportAttackMode() && isTeleportAttackRecoveryActive()) {
            return false;
        }
        if (onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
            return false;
        }
        return player.getCooledAttackStrength(0.0F) >= minAttackStrength;
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

        int attackLimit = isMouseClickAttackMode() ? 1 : Math.max(1, targetsPerAttack);
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
                continue;
            }
            if (!prepareRotationForAttack(player, target, crosshairLockedTarget)) {
                continue;
            }
            boolean teleportAttack = shouldUseTeleportAttack(player, target);
            if (!canAttackTarget(player, target, shouldRequireCrosshairHitForAttack(teleportAttack), areaOptions)) {
                continue;
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
                mc.playerController.attackEntity(player, target);
            }
            recordNoDamageAttackAttempt(target);
            attackedCount++;
        }
        return attackedCount;
    }

    private int attackTeleportTargets(Minecraft mc, EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions) {
        if (mc == null || player == null || player.connection == null || targets == null || targets.isEmpty()
                || isTeleportAttackRecoveryActive()) {
            teleportDebug(player, "PLAN_STOP",
                    "reason=INVALID_ENTRY_OR_RECOVERY,mcNull=%s,playerNull=%s,connectionNull=%s,targetsNull=%s,targetCount=%d,recoveryActive=%s",
                    mc == null, player == null, player == null || player.connection == null, targets == null,
                    targets == null ? 0 : targets.size(), isTeleportAttackRecoveryActive());
            return 0;
        }

        syncTeleportPacketBudget(player);
        if (player.ticksExisted < this.teleportPlanningRetryAfterTick) {
            teleportDebug(player, "PLAN_STOP", "reason=PLANNING_BACKOFF,retryAfter=%d,remainingTicks=%d",
                    this.teleportPlanningRetryAfterTick, this.teleportPlanningRetryAfterTick - player.ticksExisted);
            return 0;
        }
        int remainingPacketBudget = getTeleportAttackPacketLimitPerTick() - this.teleportPacketsUsedThisTick;
        if (remainingPacketBudget <= 1) {
            teleportDebug(player, "PLAN_STOP",
                    "reason=PACKET_BUDGET_TOO_LOW,remaining=%d,used=%d,limit=%d",
                    remainingPacketBudget, this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick());
            return 0;
        }

        teleportDebug(player, "PLAN_BEGIN",
                "targets=%d,targetIds=%s,targetLimit=%d,remainingPacketBudget=%d,stepDistance=%.3f,stationRadius=%.3f,attackRange=%.3f",
                targets.size(), formatTeleportTargetIds(targets, 24), Math.max(1, targetsPerAttack),
                remainingPacketBudget, getTeleportStepDistance(), getTeleportStationAttackRadius(), attackRange);
        TeleportTourPlan plan = buildTeleportTourPlan(player, targets, areaOptions,
                Math.max(1, targetsPerAttack), remainingPacketBudget);
        if (plan == null || plan.attackCount <= 0) {
            this.teleportPlanningRetryAfterTick = player.ticksExisted + TELEPORT_PLANNING_FAILURE_BACKOFF_TICKS;
            teleportDebug(player, "PLAN_STOP",
                    "reason=PLAN_NULL_OR_EMPTY,retryAfter=%d,targetIds=%s",
                    this.teleportPlanningRetryAfterTick, formatTeleportTargetIds(targets, 24));
            return 0;
        }
        teleportDebug(player, "PLAN_READY",
                "stops=%d,attacks=%d,forwardPositions=%d,rotationPackets=%d,restoreRotation=%s,packetCost=%d,budget=%d",
                plan.stops.size(), plan.attackCount, plan.forwardPositions.size(), plan.rotationPacketCount,
                plan.restoreRotationAtOrigin, plan.packetCost, remainingPacketBudget);
        int attackedCount = executeTeleportTourPlan(player, plan, areaOptions);
        if (attackedCount > 0) {
            this.teleportPlanningRetryAfterTick = Integer.MIN_VALUE;
            teleportDebug(player, "PLAN_EXECUTED", "attackedCount=%d,packetCost=%d", attackedCount, plan.packetCost);
        } else {
            teleportDebug(player, "PLAN_STOP", "reason=EXECUTION_RETURNED_ZERO,packetCost=%d,attacks=%d",
                    plan.packetCost, plan.attackCount);
        }
        return attackedCount;
    }

    private TeleportTourPlan buildTeleportTourPlan(EntityPlayerSP player, List<EntityLivingBase> targets,
            AreaHuntOptions areaOptions, int targetLimit, int packetBudget) {
        if (player == null || targets == null || targets.isEmpty() || targetLimit <= 0 || packetBudget <= 1) {
            teleportDebug(player, "PLAN_BUILD_STOP",
                    "reason=INVALID_INPUT,targetCount=%d,targetLimit=%d,packetBudget=%d",
                    targets == null ? 0 : targets.size(), targetLimit, packetBudget);
            return null;
        }

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
        TeleportTourPlan plan = new TeleportTourPlan(new TeleportOrigin(player));
        List<EntityLivingBase> remaining = new ArrayList<>();
        int currentTick = player.ticksExisted;
        double searchRangeSq = attackRange * attackRange;
        boolean useWhitelistPriority = areaOptions == null
                && enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
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
            int retryAfterTick = this.teleportTargetRetryAfterTicks.getOrDefault(target.getEntityId(), Integer.MIN_VALUE);
            if (currentTick < retryAfterTick) {
                teleportDebug(player, "PLAN_TARGET_REJECT", "reason=TARGET_RETRY_BACKOFF,retryAfter=%d,target={%s}",
                        retryAfterTick, formatTeleportTarget(player, target));
                continue;
            }
            if (buildTargetCandidate(player, target, searchRangeSq, useWhitelistPriority,
                    target.getEntityId() == this.currentTargetEntityId, true, areaOptions) == null) {
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

        Vec3d currentPosition = plan.origin.toPosition();
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
        int retryTicks = budgetFailure
                ? TELEPORT_TARGET_BUDGET_FAILURE_BACKOFF_TICKS
                : TELEPORT_TARGET_ROUTE_FAILURE_BACKOFF_TICKS;
        int retryAfterTick = player.ticksExisted + retryTicks;
        int existingRetry = this.teleportTargetRetryAfterTicks.getOrDefault(seed.targetEntityId, Integer.MIN_VALUE);
        if (retryAfterTick > existingRetry) {
            this.teleportTargetRetryAfterTicks.put(seed.targetEntityId, retryAfterTick);
        }
        teleportDebug(player, "TARGET_DEFERRED",
                "id=%d,phase=%s,detail=%s,budgetFailure=%s,retryTicks=%d,retryAfter=%d",
                seed.targetEntityId, phase, detail, budgetFailure, retryTicks, retryAfterTick);
    }

    private List<TeleportStationSeed> buildTeleportStationSeeds(Vec3d currentPosition,
            List<EntityLivingBase> targets) {
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
            int start = Math.floorMod(this.teleportStationAlternativeCursor, alternatives.size());
            int alternativeLimit = Math.min(TELEPORT_STATION_CANDIDATE_LIMIT - 1, alternatives.size());
            for (int i = 0; i < alternativeLimit; i++) {
                seeds.add(alternatives.get((start + i) % alternatives.size()));
            }
            this.teleportStationAlternativeCursor = (start + 1) % alternatives.size();
        }
        return seeds;
    }

    private Vec3d getPreferredTeleportStationPosition(Vec3d currentPosition, EntityLivingBase target) {
        Vec3d targetPosition = new Vec3d(target.posX, target.posY, target.posZ);
        Vec3d delta = targetPosition.subtract(currentPosition);
        double distance = delta.lengthVector();
        double radius = getTeleportStationAttackRadius();
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
        double radiusSq = getTeleportStationAttackRadius() * getTeleportStationAttackRadius();
        int count = 0;
        for (EntityLivingBase target : targets) {
            if (target != null && getDistanceSq(stationPosition, target.posX, target.posY, target.posZ) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private Vec3d findFastTeleportStation(EntityPlayerSP player, Vec3d currentPosition, Vec3d desiredPosition,
            TeleportPlanningBudget planningBudget) {
        if (player == null || desiredPosition == null || planningBudget == null) {
            return null;
        }
        if (currentPosition.squareDistanceTo(desiredPosition) <= TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ) {
            return currentPosition;
        }
        double[][] offsets = new double[][] {
                { 0.0D, 0.0D, 0.0D }, { 0.0D, 1.0D, 0.0D }, { 0.0D, -1.0D, 0.0D },
                { 0.75D, 0.0D, 0.0D }, { -0.75D, 0.0D, 0.0D },
                { 0.0D, 0.0D, 0.75D }, { 0.0D, 0.0D, -0.75D }, { 0.0D, 2.0D, 0.0D }
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
        double radius = getTeleportStationAttackRadius();
        double stationDistanceSq = getDistanceSq(stationPosition, target.posX, target.posY, target.posZ);
        if (stationDistanceSq > radius * radius) {
            planningBudget.markFailure("STATION_TARGET_OUT_OF_RADIUS");
            teleportDebug(player, "STATION_TARGET_REJECT",
                    "reason=OUT_OF_RADIUS,distance=%.3f,radius=%.3f,target={%s}",
                    Math.sqrt(stationDistanceSq), radius, formatTeleportTarget(player, target));
            return false;
        }
        if (!shouldRequireAttackLineOfSight()) {
            return true;
        }
        if (!planningBudget.consumeWorldQuery()) {
            planningBudget.markFailure("STATION_TARGET_LOS_QUERY_BUDGET");
            teleportDebug(player, "STATION_TARGET_REJECT", "reason=LOS_QUERY_BUDGET,target={%s}",
                    formatTeleportTarget(player, target));
            return false;
        }
        Vec3d eyePosition = stationPosition.addVector(0.0D, player.getEyeHeight(), 0.0D);
        Vec3d targetEyePosition = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        RayTraceResult ray = player.world.rayTraceBlocks(eyePosition, targetEyePosition, false, true, false);
        boolean visible = ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK;
        if (!visible) {
            planningBudget.markFailure("STATION_TARGET_LINE_OF_SIGHT_BLOCKED");
            teleportDebug(player, "STATION_TARGET_REJECT", "reason=LINE_OF_SIGHT_BLOCKED,target={%s}",
                    formatTeleportTarget(player, target));
        }
        return visible;
    }

    private int getTeleportStationRotationPacketCount(int targetCount, boolean stationHasNoMovement) {
        if (!shouldRotateToTarget() || targetCount <= 0) {
            return 0;
        }
        return stationHasNoMovement ? targetCount : Math.max(0, targetCount - 1);
    }

    private int executeTeleportTourPlan(EntityPlayerSP player, TeleportTourPlan plan, AreaHuntOptions areaOptions) {
        if (player == null || player.connection == null || plan == null || plan.attackCount <= 0) {
            teleportDebug(player, "EXECUTE_STOP",
                    "reason=INVALID_PLAN_OR_CONNECTION,playerNull=%s,connectionNull=%s,planNull=%s,attackCount=%d",
                    player == null, player == null || player.connection == null, plan == null,
                    plan == null ? 0 : plan.attackCount);
            return 0;
        }
        if (!isTeleportTourPlanStillValid(player, plan, areaOptions)) {
            teleportDebug(player, "EXECUTE_STOP", "reason=PLAN_REVALIDATION_FAILED,attacks=%d,packetCost=%d",
                    plan.attackCount, plan.packetCost);
            return 0;
        }
        if (!reserveTeleportPackets(player, plan.packetCost)) {
            teleportDebug(player, "EXECUTE_STOP", "reason=PACKET_RESERVATION_FAILED,packetCost=%d",
                    plan.packetCost);
            return 0;
        }

        teleportDebug(player, "EXECUTE_BEGIN",
                "stops=%d,attacks=%d,forwardPositions=%d,packetCost=%d,origin=(%.3f,%.3f,%.3f)",
                plan.stops.size(), plan.attackCount, plan.forwardPositions.size(), plan.packetCost,
                plan.origin.x, plan.origin.y, plan.origin.z);
        TeleportAttackPlan recoveryPlan = createTeleportTourRecoveryPlan(plan);
        if (recoveryPlan != null) {
            this.activeTeleportAttackPlan = mergeTeleportRecoveryPlan(this.activeTeleportAttackPlan, recoveryPlan);
            this.pendingTeleportReturnTicks = getTeleportCorrectionWindowTicks(player);
        }

        int attackedCount = 0;
        for (int stopIndex = 0; stopIndex < plan.stops.size(); stopIndex++) {
            TeleportTourStop stop = plan.stops.get(stopIndex);
            teleportDebug(player, "EXECUTE_STOP_BEGIN",
                    "stopIndex=%d,station=(%.3f,%.3f,%.3f),legPackets=%d,targetIds=%s",
                    stopIndex, stop.position.x, stop.position.y, stop.position.z, stop.legPositions.size(),
                    formatTeleportTargetIds(stop.targets, 24));
            for (int i = 0; i < stop.legPositions.size(); i++) {
                Vec3d position = stop.legPositions.get(i);
                boolean stationPacket = i == stop.legPositions.size() - 1;
                if (stationPacket && shouldRotateToTarget() && !stop.targets.isEmpty()) {
                    Rotation rotation = getFastTeleportAttackRotation(player, stop.targets.get(0), position);
                    player.connection.sendPacket(new CPacketPlayer.PositionRotation(position.x, position.y, position.z,
                            rotation.getYaw(), rotation.getPitch(), false));
                } else {
                    player.connection.sendPacket(new CPacketPlayer.Position(position.x, position.y, position.z, false));
                }
            }

            for (int i = 0; i < stop.targets.size(); i++) {
                EntityLivingBase target = stop.targets.get(i);
                if (shouldRotateToTarget() && (stop.legPositions.isEmpty() || i > 0)) {
                    Rotation rotation = getFastTeleportAttackRotation(player, target, stop.position);
                    player.connection
                            .sendPacket(new CPacketPlayer.Rotation(rotation.getYaw(), rotation.getPitch(), false));
                }
                player.connection.sendPacket(new CPacketUseEntity(target));
                recordNoDamageAttackAttempt(target);
                this.teleportLastAttackTicks.remove(target.getEntityId());
                this.teleportLastAttackTicks.put(target.getEntityId(), player.ticksExisted);
                this.teleportTargetRetryAfterTicks.remove(target.getEntityId());
                attackedCount++;
                teleportDebug(player, "ATTACK_SENT",
                        "stopIndex=%d,targetIndex=%d,station=(%.3f,%.3f,%.3f),attackedCount=%d,target={%s}",
                        stopIndex, i, stop.position.x, stop.position.y, stop.position.z, attackedCount,
                        formatTeleportTarget(player, target));
            }
        }

        for (int i = plan.forwardPositions.size() - 2; i >= 0; i--) {
            Vec3d position = plan.forwardPositions.get(i);
            player.connection.sendPacket(new CPacketPlayer.Position(position.x, position.y, position.z, false));
        }
        if (!plan.forwardPositions.isEmpty()) {
            player.connection.sendPacket(new CPacketPlayer.PositionRotation(plan.origin.x, plan.origin.y, plan.origin.z,
                    plan.origin.yaw, plan.origin.pitch, plan.origin.onGround));
        } else if (plan.restoreRotationAtOrigin) {
            player.connection.sendPacket(new CPacketPlayer.Rotation(plan.origin.yaw, plan.origin.pitch,
                    plan.origin.onGround));
        }
        if (recoveryPlan != null) {
            recoveryPlan.returnCompleted = true;
        }
        trimTeleportRuntimeMaps();
        teleportDebug(player, "EXECUTE_DONE",
                "attackedCount=%d,returnedToOrigin=%s,recoveryPlanCreated=%s,historySize=%d,cacheSize=%d",
                attackedCount, !plan.forwardPositions.isEmpty() || plan.restoreRotationAtOrigin,
                recoveryPlan != null, this.teleportLastAttackTicks.size(),
                this.teleportTargetCandidateEntityIds.size());
        return attackedCount;
    }

    private boolean isTeleportTourPlanStillValid(EntityPlayerSP player, TeleportTourPlan plan,
            AreaHuntOptions areaOptions) {
        if (player == null || player.world == null || plan == null) {
            teleportDebug(player, "REVALIDATE_FAIL", "reason=PLAYER_WORLD_OR_PLAN_NULL");
            return false;
        }
        double originDistanceSq = player.getDistanceSq(plan.origin.x, plan.origin.y, plan.origin.z);
        if (originDistanceSq > TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ) {
            teleportDebug(player, "REVALIDATE_FAIL",
                    "reason=PLAYER_MOVED_FROM_ORIGIN,distanceSq=%.6f,toleranceSq=%.6f,current=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    originDistanceSq, TELEPORT_ATTACK_ORIGIN_TOLERANCE_SQ, player.posX, player.posY, player.posZ,
                    plan.origin.x, plan.origin.y, plan.origin.z);
            return false;
        }
        double searchRangeSq = attackRange * attackRange;
        double stationRadiusSq = getTeleportStationAttackRadius() * getTeleportStationAttackRadius();
        boolean useWhitelistPriority = areaOptions == null
                && enableNameWhitelist && nameWhitelist != null && !nameWhitelist.isEmpty();
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
                        target.getEntityId() == this.currentTargetEntityId, true, areaOptions) == null) {
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
        trimOldestEntries(this.teleportLastAttackTicks, TELEPORT_TARGET_HISTORY_LIMIT);
        trimOldestEntries(this.teleportTargetRetryAfterTicks, TELEPORT_TARGET_CACHE_LIMIT);
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

        double maxFeetY = player.world.getActualHeight() - Math.max(1.0D, player.height) - 0.05D;
        double[] rises = new double[] { 4.0D, 8.0D, 16.0D };
        int probes = 1;
        for (double rise : rises) {
            if (probes++ >= TELEPORT_ROUTE_COLLISION_PROBE_LIMIT || !planningBudget.canContinue()) {
                break;
            }
            double cruiseY = Math.min(maxFeetY, Math.max(start.y, end.y) + rise);
            Vec3d startAir = new Vec3d(start.x, cruiseY, start.z);
            Vec3d endAir = new Vec3d(end.x, cruiseY, end.z);
            int firstPackets = Math.max(1, (int) Math.ceil(start.distanceTo(startAir) / stepDistance));
            int middlePackets = Math.max(1, (int) Math.ceil(startAir.distanceTo(endAir) / stepDistance));
            int lastPackets = Math.max(1, (int) Math.ceil(endAir.distanceTo(end) / stepDistance));
            if (firstPackets + middlePackets + lastPackets > maximumPackets
                    || !isFastTeleportSegmentClear(player, start, startAir, planningBudget)
                    || !isFastTeleportSegmentClear(player, startAir, endAir, planningBudget)
                    || !isFastTeleportSegmentClear(player, endAir, end, planningBudget)) {
                continue;
            }
            List<Vec3d> result = new ArrayList<>();
            appendInterpolatedTeleportLeg(result, start, startAir, firstPackets);
            appendInterpolatedTeleportLeg(result, startAir, endAir, middlePackets);
            appendInterpolatedTeleportLeg(result, endAir, end, lastPackets);
            return result;
        }
        return null;
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
        return clampFiniteFloat(teleportStepDistance, DEFAULT_TELEPORT_STEP_DISTANCE,
                MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
    }

    public static float getTeleportStationAttackRadius() {
        return clampFiniteFloat(teleportStationAttackRadius, DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
    }

    private boolean shouldAttackPrimaryTargetOnly(EntityLivingBase crosshairLockedTarget) {
        return focusSingleTarget
                || crosshairLockedTarget != null
                || rotateOnlyOnAttack
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
        if (player.getDistanceSq(target) > attackRange * attackRange) {
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
        if (shouldRotateToTarget() && yawDiff > 100.0F) {
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
        return onlyAttackWhenLookingAtTarget && shouldRotateToTarget();
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
                && noDamageExcludedEntityIds.contains(target.getEntityId());
    }

    private boolean isHuntUnreachableExcludedTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return false;
        }
        HuntUnreachableTracker tracker = huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            return false;
        }
        if (hasHuntTargetRelocated(tracker, target)) {
            tracker.resetFailures(target);
            if (tracker.failedGoalKeys.isEmpty()) {
                huntUnreachableTrackers.remove(target.getEntityId());
            }
            return false;
        }
        return tracker.excludeUntilTick > player.ticksExisted;
    }

    private void tickHuntUnreachableTrackers(EntityPlayerSP player) {
        if (player == null || player.world == null || huntUnreachableTrackers.isEmpty()) {
            return;
        }
        List<Integer> trackedIds = new ArrayList<>(huntUnreachableTrackers.keySet());
        for (Integer entityId : trackedIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            HuntUnreachableTracker tracker = huntUnreachableTrackers.get(entityId);
            if (tracker == null) {
                continue;
            }
            if (!(entity instanceof EntityLivingBase)) {
                huntUnreachableTrackers.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                huntUnreachableTrackers.remove(entityId);
                continue;
            }
            if (hasHuntTargetRelocated(tracker, living)) {
                tracker.resetFailures(living);
                if (tracker.failedGoalKeys.isEmpty()) {
                    huntUnreachableTrackers.remove(entityId);
                }
            }
        }
        pruneHuntUnreachableTrackingSize();
    }

    private void clearHuntUnreachableTracking() {
        huntUnreachableTrackers.clear();
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
        HuntUnreachableTracker tracker = huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null || tracker.failedGoalKeys.isEmpty()) {
            return false;
        }
        return tracker.failedGoalKeys.contains(toHuntGoalKey(goalX, goalY, goalZ));
    }

    private long toHuntGoalKey(double goalX, double goalY, double goalZ) {
        return new BlockPos(MathHelper.floor(goalX), MathHelper.floor(goalY), MathHelper.floor(goalZ)).toLong();
    }

    private void clearCurrentHuntUnreachableFailureCount() {
        if (!huntNavigationActive || lastHuntTargetEntityId == Integer.MIN_VALUE) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }
        Entity entity = mc.world.getEntityByID(lastHuntTargetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        HuntUnreachableTracker tracker = huntUnreachableTrackers.get(lastHuntTargetEntityId);
        if (tracker == null) {
            return;
        }
        tracker.failedGoalCount = 0;
        tracker.excludeUntilTick = 0;
        tracker.refreshTargetPosition((EntityLivingBase) entity);
    }

    private void handleHuntPathCalculationFailed() {
        if (!huntNavigationActive || lastHuntTargetEntityId == Integer.MIN_VALUE
                || Double.isNaN(lastHuntGoalX) || Double.isNaN(lastHuntGoalY) || Double.isNaN(lastHuntGoalZ)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || mc.world == null) {
            return;
        }
        Entity entity = mc.world.getEntityByID(lastHuntTargetEntityId);
        if (!(entity instanceof EntityLivingBase)) {
            stopHuntNavigation();
            return;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        HuntUnreachableTracker tracker = huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            tracker = new HuntUnreachableTracker(target);
            huntUnreachableTrackers.put(target.getEntityId(), tracker);
        } else if (hasHuntTargetRelocated(tracker, target)) {
            tracker.resetFailures(target);
        } else {
            tracker.refreshTargetPosition(target);
        }

        tracker.failedGoalCount++;
        tracker.failedGoalKeys.add(toHuntGoalKey(lastHuntGoalX, lastHuntGoalY, lastHuntGoalZ));
        while (tracker.failedGoalKeys.size() > HUNT_UNREACHABLE_MAX_FAILED_GOALS_PER_TARGET) {
            Long first = tracker.failedGoalKeys.iterator().next();
            tracker.failedGoalKeys.remove(first);
        }
        pruneHuntUnreachableTrackingSize();

        boolean temporarilyExcluded = tracker.failedGoalCount >= HUNT_UNREACHABLE_FAILS_BEFORE_TEMP_EXCLUDE;
        if (temporarilyExcluded) {
            tracker.excludeUntilTick = player.ticksExisted + HUNT_UNREACHABLE_TEMP_EXCLUDE_TICKS;
            tracker.failedGoalCount = 0;
        }

        String targetName = getFilterableEntityName(target);
        zszlScriptMod.LOGGER.info(
                "杀戮光环追击寻路不可达: id={}, name={}, goal=({}, {}, {}), failedGoals={}, tempExcluded={}",
                target.getEntityId(),
                targetName,
                MathHelper.floor(lastHuntGoalX),
                MathHelper.floor(lastHuntGoalY),
                MathHelper.floor(lastHuntGoalZ),
                tracker.failedGoalKeys.size(),
                temporarilyExcluded);

        stopHuntNavigation();
        if (temporarilyExcluded && this.currentTargetEntityId == target.getEntityId()) {
            this.currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            this.attackSequenceExecutor.stop();
        }
    }

    private void pruneHuntUnreachableTrackingSize() {
        while (huntUnreachableTrackers.size() > HUNT_UNREACHABLE_MAX_TRACKED_TARGETS) {
            Integer first = huntUnreachableTrackers.keySet().iterator().next();
            huntUnreachableTrackers.remove(first);
        }
    }

    public static boolean isNoDamageExclusionEnabled() {
        return getNoDamageAttackLimit() > 0;
    }

    public static int getNoDamageAttackLimit() {
        return MathHelper.clamp(noDamageAttackLimit, 0, MAX_NO_DAMAGE_ATTACK_LIMIT);
    }

    private void tickNoDamageAttackTrackers(EntityPlayerSP player) {
        if (!isNoDamageExclusionEnabled() || player == null || player.world == null) {
            clearNoDamageAttackTracking();
            return;
        }

        int limit = getNoDamageAttackLimit();
        List<Integer> trackerIds = new ArrayList<>(noDamageAttackTrackers.keySet());
        for (Integer entityId : trackerIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                noDamageAttackTrackers.remove(entityId);
                noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                noDamageAttackTrackers.remove(entityId);
                noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            NoDamageAttackTracker tracker = noDamageAttackTrackers.get(entityId);
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
            teleportDebug(mc == null ? null : mc.player, "NO_DAMAGE_RESET",
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
        teleportDebug(mc == null ? null : mc.player, "NO_DAMAGE_CONFIRMED",
                "id=%d,baseline=%.3f,current=%.3f,added=%d,confirmedNow=%d,limit=%d,target={%s}",
                entityId, tracker.baselineHealth, currentHealth, tracker.pendingAttempts,
                tracker.confirmedNoDamageAttempts, limit,
                formatTeleportTarget(mc == null ? null : mc.player, target));
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
        int entityId = target.getEntityId();
        if (noDamageExcludedEntityIds.contains(entityId)) {
            return;
        }

        NoDamageAttackTracker tracker = noDamageAttackTrackers.get(entityId);
        float currentHealth = target.getHealth();
        if (tracker == null) {
            tracker = new NoDamageAttackTracker(currentHealth);
            noDamageAttackTrackers.put(entityId, tracker);
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
        teleportDebug(mc == null ? null : mc.player, "NO_DAMAGE_ATTEMPT",
                "id=%d,baseline=%.3f,current=%.3f,pending=%d,confirmed=%d,observationTicks=%d,limit=%d",
                entityId, tracker.baselineHealth, currentHealth, tracker.pendingAttempts,
                tracker.confirmedNoDamageAttempts, tracker.observationTicks, getNoDamageAttackLimit());
        pruneNoDamageTrackingSize();
    }

    private void excludeNoDamageTarget(int entityId, EntityLivingBase target) {
        if (!noDamageExcludedEntityIds.add(entityId)) {
            return;
        }
        noDamageAttackTrackers.remove(entityId);
        pruneNoDamageTrackingSize();
        if (this.currentTargetEntityId == entityId) {
            this.currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            stopHuntNavigation();
            this.attackSequenceExecutor.stop();
        }
        String targetName = target == null ? "" : getFilterableEntityName(target);
        Minecraft mc = Minecraft.getMinecraft();
        teleportDebug(mc == null ? null : mc.player, "TARGET_NO_DAMAGE_EXCLUDED",
                "id=%d,name=%s,limit=%d,target={%s},cacheContains=%s,historyTick=%d",
                entityId, targetName, getNoDamageAttackLimit(),
                formatTeleportTarget(mc == null ? null : mc.player, target),
                this.teleportTargetCandidateEntityIds.contains(entityId),
                this.teleportLastAttackTicks.getOrDefault(entityId, Integer.MIN_VALUE));
        zszlScriptMod.LOGGER.info("杀戮光环无掉血排除目标: id={}, name={}, limit={}",
                entityId, targetName, getNoDamageAttackLimit());
    }

    private void pruneNoDamageExcludedTargets(EntityPlayerSP player) {
        if (player == null || player.world == null || noDamageExcludedEntityIds.isEmpty()) {
            return;
        }
        List<Integer> excludedIds = new ArrayList<>(noDamageExcludedEntityIds);
        for (Integer entityId : excludedIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                noDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                noDamageExcludedEntityIds.remove(entityId);
            }
        }
    }

    private void pruneNoDamageTrackingSize() {
        while (noDamageAttackTrackers.size() > NO_DAMAGE_MAX_TRACKED_TARGETS) {
            Integer first = noDamageAttackTrackers.keySet().iterator().next();
            noDamageAttackTrackers.remove(first);
        }
        while (noDamageExcludedEntityIds.size() > NO_DAMAGE_MAX_EXCLUDED_TARGETS) {
            Integer first = noDamageExcludedEntityIds.iterator().next();
            noDamageExcludedEntityIds.remove(first);
        }
    }

    private void clearNoDamageAttackTracking() {
        noDamageAttackTrackers.clear();
        noDamageExcludedEntityIds.clear();
    }

    private boolean performTeleportAttack(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null || player.connection == null || isTeleportAttackRecoveryActive()) {
            return false;
        }
        syncTeleportPacketBudget(player);
        int remainingPacketBudget = getTeleportAttackPacketLimitPerTick() - this.teleportPacketsUsedThisTick;
        TeleportTourPlan plan = buildTeleportTourPlan(player, Collections.singletonList(target), null,
                1, remainingPacketBudget);
        if (plan == null) {
            return false;
        }
        return executeTeleportTourPlan(player, plan, null) > 0;
    }

    public static int getTeleportAttackPacketLimitPerTick() {
        return MathHelper.clamp(teleportAttackPacketLimitPerTick,
                MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK, MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK);
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
            int steps = Math.max(1, (int) Math.ceil(distance / getTeleportStepDistance()));
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
        AxisAlignedBB sweptBox = getTeleportPlayerBox(player, start).union(getTeleportPlayerBox(player, end));
        return isTeleportBoxClear(player, sweptBox);
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
        if (returnWaypoints == null || returnWaypoints.size() + 1 > getTeleportAttackPacketLimitPerTick()) {
            teleportDebug(player, "CORRECTION_PLAN_STOP",
                    "reason=NO_ROUTE_OR_PACKET_LIMIT,waypoints=%d,cost=%d,limit=%d,start=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f)",
                    returnWaypoints == null ? -1 : returnWaypoints.size(),
                    returnWaypoints == null ? -1 : returnWaypoints.size() + 1,
                    getTeleportAttackPacketLimitPerTick(), startX, startY, startZ,
                    plan.originX, plan.originY, plan.originZ);
            return false;
        }
        List<Vec3d> route = new ArrayList<>();
        route.add(new Vec3d(startX, startY, startZ));
        route.addAll(returnWaypoints);
        route.add(new Vec3d(plan.originX, plan.originY, plan.originZ));
        this.activeTeleportCorrectionPlan = plan;
        this.pendingTeleportReturnPlan = new TeleportReturnPlan(plan, returnWaypoints, route);
        this.pendingTeleportReturnTicks = getTeleportCorrectionWindowTicks(player);
        plan.correctedByServer = true;
        teleportDebug(player, "CORRECTION_PLAN_READY",
                "waypoints=%d,packetCost=%d,start=(%.3f,%.3f,%.3f),origin=(%.3f,%.3f,%.3f),returnTicks=%d",
                returnWaypoints.size(), returnWaypoints.size() + 1, startX, startY, startZ,
                plan.originX, plan.originY, plan.originZ, this.pendingTeleportReturnTicks);
        return true;
    }

    private List<Vec3d> buildKnownTeleportCorrectionWaypoints(EntityPlayerSP player, Vec3d corrected,
            TeleportAttackPlan plan) {
        TeleportAttackPlan routeHolder = this.activeTeleportAttackPlan;
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

        int maximumWaypoints = Math.max(0, getTeleportAttackPacketLimitPerTick() - 1);
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
                Math.max(1, getTeleportAttackPacketLimitPerTick() - 1), planningBudget);
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
        TeleportReturnPlan pendingReturn = this.pendingTeleportReturnPlan;
        if (player == null || player.connection == null || pendingReturn == null) {
            teleportDebug(player, "RETURN_STOP",
                    "reason=INVALID_INPUT,playerNull=%s,connectionNull=%s,pendingReturnNull=%s",
                    player == null, player == null || player.connection == null, pendingReturn == null);
            return false;
        }
        if (player.ticksExisted < pendingReturn.notBeforeTick) {
            this.pendingTeleportReturnTicks = Math.max(2, this.pendingTeleportReturnTicks);
            teleportDebug(player, "RETURN_WAIT", "reason=NOT_BEFORE_TICK,notBeforeTick=%d,currentTick=%d",
                    pendingReturn.notBeforeTick, player.ticksExisted);
            return false;
        }
        int packetCost = pendingReturn.waypoints.size() + 1;
        if (packetCost > getTeleportAttackPacketLimitPerTick()) {
            teleportDebug(player, "RETURN_STOP", "reason=COST_ABOVE_HARD_LIMIT,cost=%d,limit=%d",
                    packetCost, getTeleportAttackPacketLimitPerTick());
            clearTeleportAttackState(player, "PENDING_RETURN_COST_ABOVE_LIMIT");
            return false;
        }
        if (!reserveTeleportPackets(player, packetCost)) {
            this.pendingTeleportReturnTicks = Math.max(2, this.pendingTeleportReturnTicks);
            teleportDebug(player, "RETURN_WAIT",
                    "reason=PACKET_BUDGET,cost=%d,used=%d,limit=%d,remaining=%d",
                    packetCost, this.teleportPacketsUsedThisTick, getTeleportAttackPacketLimitPerTick(),
                    getTeleportAttackPacketLimitPerTick() - this.teleportPacketsUsedThisTick);
            return false;
        }

        TeleportAttackPlan plan = pendingReturn.owner;
        sendTeleportWaypoints(player, pendingReturn.waypoints, false);
        player.connection.sendPacket(new CPacketPlayer.PositionRotation(plan.originX, plan.originY, plan.originZ,
                plan.originYaw, plan.originPitch, plan.originOnGround));
        plan.latestReturnRoute.clear();
        plan.latestReturnRoute.addAll(pendingReturn.route);
        registerTeleportRoute(this.activeTeleportAttackPlan, plan, pendingReturn.route);
        plan.correctionCount++;
        plan.returnCompleted = true;
        this.lastTeleportCorrectionTick = player.ticksExisted;
        this.pendingTeleportReturnPlan = null;
        player.setPositionAndRotation(plan.originX, plan.originY, plan.originZ,
                plan.originYaw, plan.originPitch);
        if (this.activeTeleportAttackPlan != plan) {
            List<TeleportRoute> knownRoutes = new ArrayList<>(this.activeTeleportAttackPlan.relatedRoutes);
            plan.relatedRoutes.clear();
            plan.relatedRoutes.addAll(knownRoutes);
            this.activeTeleportAttackPlan = plan;
        }
        this.activeTeleportCorrectionPlan = null;
        this.pendingTeleportReturnTicks = getTeleportCorrectionWindowTicks(player);
        teleportDebug(player, "RETURN_SENT",
                "waypoints=%d,packetCost=%d,origin=(%.3f,%.3f,%.3f),correctionCount=%d,returnTicks=%d",
                pendingReturn.waypoints.size(), packetCost, plan.originX, plan.originY, plan.originZ,
                plan.correctionCount, this.pendingTeleportReturnTicks);
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
        if (player == null || target == null || isRelockSuppressedByCrosshairTarget(player, target)) {
            return null;
        }

        Rotation desiredAim = getDesiredAimRotation(player, target, attackRotation);
        boolean useSmoothRotation = forceSmoothRotation || smoothRotation;
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
        smoothYawTurnUsedThisTick += Math.abs(yawStep);
        smoothPitchTurnUsedThisTick += Math.abs(pitchStep);

        float nextYaw = player.rotationYaw + yawStep;
        float nextPitch = player.rotationPitch + pitchStep;
        return new Rotation(nextYaw, MathHelper.clamp(nextPitch, -90.0F, 90.0F));
    }

    private void updateSmoothTurnLimitBudget(EntityPlayerSP player) {
        int currentTick = player == null ? Integer.MIN_VALUE : player.ticksExisted;
        if (currentTick != this.lastSmoothTurnLimitTick) {
            this.lastSmoothTurnLimitTick = currentTick;
            this.smoothYawTurnUsedThisTick = 0.0F;
            this.smoothPitchTurnUsedThisTick = 0.0F;
        }
    }

    private void resetSmoothTurnLimitBudget() {
        this.lastSmoothTurnLimitTick = Integer.MIN_VALUE;
        this.smoothYawTurnUsedThisTick = 0.0F;
        this.smoothPitchTurnUsedThisTick = 0.0F;
        clearVisualRotationCache();
    }

    private Rotation getCachedVisualRotation(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null || this.visualRotationCache == null) {
            return null;
        }
        if (this.visualRotationCacheTick != player.ticksExisted
                || this.visualRotationCacheTargetEntityId != target.getEntityId()
                || Float.compare(this.visualRotationCacheSourceYaw, player.rotationYaw) != 0
                || Float.compare(this.visualRotationCacheSourcePitch, player.rotationPitch) != 0) {
            return null;
        }
        return this.visualRotationCache;
    }

    private void cacheVisualRotation(EntityPlayerSP player, EntityLivingBase target, Rotation rotation) {
        if (player == null || target == null || rotation == null) {
            clearVisualRotationCache();
            return;
        }
        this.visualRotationCacheTick = player.ticksExisted;
        this.visualRotationCacheTargetEntityId = target.getEntityId();
        this.visualRotationCacheSourceYaw = player.rotationYaw;
        this.visualRotationCacheSourcePitch = player.rotationPitch;
        this.visualRotationCache = rotation;
    }

    private void clearVisualRotationCache() {
        this.visualRotationCacheTick = Integer.MIN_VALUE;
        this.visualRotationCacheTargetEntityId = Integer.MIN_VALUE;
        this.visualRotationCacheSourceYaw = 0.0F;
        this.visualRotationCacheSourcePitch = 0.0F;
        this.visualRotationCache = null;
    }

    private float getRemainingSmoothYawTurnStep(float turnLimit) {
        return Math.max(0.0F, turnLimit - this.smoothYawTurnUsedThisTick);
    }

    private float getRemainingSmoothPitchTurnStep(float turnLimit) {
        return Math.max(0.0F, turnLimit - this.smoothPitchTurnUsedThisTick);
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
        if (isTeleportAttackMode() && !aimOnlyMode) {
            return false;
        }
        if (!shouldRotateToTarget() && !orbitFacingActive) {
            return false;
        }
        return !rotateOnlyOnAttack || aimOnlyMode || isTargetSwitchSmoothingActive();
    }

    private void updateAimTargetTransition(EntityLivingBase target) {
        if (target == null) {
            clearAimTargetTransition();
            return;
        }

        int targetEntityId = target.getEntityId();
        if (targetEntityId == this.lastAimTargetEntityId) {
            return;
        }

        boolean hadPreviousTarget = this.lastAimTargetEntityId != Integer.MIN_VALUE && this.lastAimTargetEntityId != -1;
        this.lastAimTargetEntityId = targetEntityId;
        if (hadPreviousTarget && smoothRotation && shouldRotateToTarget()) {
            this.targetSwitchSmoothTicks = TARGET_SWITCH_SMOOTH_TICKS;
        } else {
            this.targetSwitchSmoothTicks = 0;
        }
    }

    private boolean isTargetSwitchSmoothingActive() {
        return this.targetSwitchSmoothTicks > 0 && smoothRotation && shouldRotateToTarget();
    }

    private void decayTargetSwitchSmoothTicks() {
        if (this.targetSwitchSmoothTicks > 0) {
            this.targetSwitchSmoothTicks--;
        }
    }

    private void clearAimTargetTransition() {
        this.lastAimTargetEntityId = Integer.MIN_VALUE;
        this.targetSwitchSmoothTicks = 0;
        clearVisualRotationCache();
    }

    private boolean shouldForceOrbitFacing(EntityPlayerSP player, EntityLivingBase target) {
        return isHuntOrbitEnabled() && canStartOrbitHunt(player, target);
    }

    private float computeTrackingYawSpeedFloor(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return minTurnSpeed;
        }

        double radiusX = target.posX - player.posX;
        double radiusZ = target.posZ - player.posZ;
        double horizontalDistance = Math.sqrt(radiusX * radiusX + radiusZ * radiusZ);
        if (horizontalDistance <= 1.0E-4D) {
            return minTurnSpeed;
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
        if (SpeedHandler.enabled) {
            speedFloor += Math.max(0.0D, (SpeedHandler.getCurrentTimerSpeedMultiplier() - 1.0F) * 8.0D);
        }

        return MathHelper.clamp((float) speedFloor, minTurnSpeed, Math.max(maxTurnSpeed, 60.0F));
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
        if (SpeedHandler.enabled) {
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
        int tick = player == null ? Integer.MIN_VALUE : player.ticksExisted;
        int targetEntityId = target == null ? Integer.MIN_VALUE : target.getEntityId();
        String yawSpec = getAimYawOffsetSpec();
        String pitchSpec = getAimPitchOffsetSpec();
        if (this.aimOffsetSampleTick == tick
                && this.aimOffsetSampleTargetEntityId == targetEntityId
                && yawSpec.equals(this.aimOffsetSampleYawSpec)
                && pitchSpec.equals(this.aimOffsetSamplePitchSpec)) {
            return new AimOffsetSample(this.sampledAimYawOffset, this.sampledAimPitchOffset);
        }

        this.aimOffsetSampleTick = tick;
        this.aimOffsetSampleTargetEntityId = targetEntityId;
        this.aimOffsetSampleYawSpec = yawSpec;
        this.aimOffsetSamplePitchSpec = pitchSpec;
        this.sampledAimYawOffset = sampleAimOffset(aimYawOffsetSpec, aimYawOffset);
        this.sampledAimPitchOffset = sampleAimOffset(aimPitchOffsetSpec, aimPitchOffset);
        return new AimOffsetSample(this.sampledAimYawOffset, this.sampledAimPitchOffset);
    }

    private boolean shouldUseMotionCompensatedVisualAim(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null || !SpeedHandler.enabled) {
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
        EntityPlayerSP currentPlayer = Minecraft.getMinecraft().player;
        double eyeHeight = currentPlayer == null ? 1.62D : currentPlayer.getEyeHeight();
        double dy = target.posY + target.getEyeHeight() * 0.85D - (fromY + eyeHeight);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private float computeTurnSpeed(float yawDeltaAbs, boolean attackRotation) {
        float normalized = MathHelper.clamp(yawDeltaAbs / 120.0F, 0.0F, 1.0F);
        float eased = normalized * normalized;
        float effectiveMin = Math.max(0.65F, minTurnSpeed * (attackRotation ? 0.75F : 0.58F));
        float effectiveMax = Math.max(effectiveMin, maxTurnSpeed * (attackRotation ? 0.86F : 0.72F));
        return effectiveMin + (effectiveMax - effectiveMin) * eased;
    }

    private float clampSigned(float value, float maxMagnitude) {
        if (maxMagnitude <= 0.0F) {
            return 0.0F;
        }
        return Math.copySign(Math.min(Math.abs(value), maxMagnitude), value);
    }

    private static float sampleSmoothMaxTurnStepForTurn() {
        SmoothTurnStepRange range = parseSmoothMaxTurnStepSpec(smoothMaxTurnStepSpec, smoothMaxTurnStep);
        if (!range.isRandom()) {
            return range.min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(range.min, range.max);
    }

    public static void setSmoothMaxTurnStepSpec(String spec) {
        SmoothTurnStepRange range = parseSmoothMaxTurnStepSpec(spec, smoothMaxTurnStep);
        smoothMaxTurnStep = range.min;
        smoothMaxTurnStepSpec = range.toSpec();
    }

    public static String getSmoothMaxTurnStepDisplayText() {
        return parseSmoothMaxTurnStepSpec(smoothMaxTurnStepSpec, smoothMaxTurnStep).toDisplayText();
    }

    public static String getSmoothMaxTurnStepSpec() {
        return parseSmoothMaxTurnStepSpec(smoothMaxTurnStepSpec, smoothMaxTurnStep).toSpec();
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
        return TickRangeSpec.sample(attackSequenceDelayTicksSpec, attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
    }

    public static void setAttackSequenceDelayTicksSpec(String spec) {
        TickRangeSpec.Range range = TickRangeSpec.parse(spec, attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
        attackSequenceDelayTicks = range.getMin();
        attackSequenceDelayTicksSpec = range.toSpec();
    }

    public static String getAttackSequenceDelayTicksDisplayText() {
        return TickRangeSpec.parse(attackSequenceDelayTicksSpec, attackSequenceDelayTicks,
                MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS).toDisplayText();
    }

    public static String getAttackSequenceDelayTicksSpec() {
        return TickRangeSpec.parse(attackSequenceDelayTicksSpec, attackSequenceDelayTicks,
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
        AimOffsetRange range = parseAimOffsetSpec(spec, aimYawOffset);
        aimYawOffset = range.min;
        aimYawOffsetSpec = range.toSpec();
    }

    public static void setAimPitchOffsetSpec(String spec) {
        AimOffsetRange range = parseAimOffsetSpec(spec, aimPitchOffset);
        aimPitchOffset = range.min;
        aimPitchOffsetSpec = range.toSpec();
    }

    public static String getAimYawOffsetDisplayText() {
        return parseAimOffsetSpec(aimYawOffsetSpec, aimYawOffset).toDisplayText();
    }

    public static String getAimPitchOffsetDisplayText() {
        return parseAimOffsetSpec(aimPitchOffsetSpec, aimPitchOffset).toDisplayText();
    }

    public static String getAimYawOffsetSpec() {
        return parseAimOffsetSpec(aimYawOffsetSpec, aimYawOffset).toSpec();
    }

    public static String getAimPitchOffsetSpec() {
        return parseAimOffsetSpec(aimPitchOffsetSpec, aimPitchOffset).toSpec();
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
        return isHuntEnabled() ? Math.max(attackRange, huntRadius) : attackRange;
    }

    private boolean matchesEnabledTargetGroup(Entity target) {
        if (target instanceof EntityEnderCrystal) {
            return targetEnderCrystal;
        }
        if (!(target instanceof EntityLivingBase)) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            return targetPlayers;
        }
        if (isHostileTargetType((EntityLivingBase) target)) {
            return targetHostile;
        }
        if (isPassiveTargetType((EntityLivingBase) target)) {
            return targetPassive;
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
        return ATTACK_MODE_PACKET.equalsIgnoreCase(attackMode);
    }

    private boolean isTeleportAttackMode() {
        return ATTACK_MODE_TELEPORT.equalsIgnoreCase(attackMode);
    }

    private boolean isSequenceAttackMode() {
        return ATTACK_MODE_SEQUENCE.equalsIgnoreCase(attackMode);
    }

    private boolean isMouseClickAttackMode() {
        return ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(attackMode);
    }

    private boolean shouldRotateToTarget() {
        return aimOnlyMode || (!isPacketAttackMode() && rotateToTarget);
    }

    private boolean shouldRotateOnlyOnAttack() {
        return rotateOnlyOnAttack && !aimOnlyMode && shouldRotateToTarget();
    }

    private boolean shouldUseRelockOnlyWhenNoCrosshairTarget() {
        return relockOnlyWhenNoCrosshairTarget && shouldRotateToTarget();
    }

    private static boolean shouldRequireAttackLineOfSight() {
        return requireLineOfSight && !throughWallAttack;
    }

    private boolean isSameEntity(EntityLivingBase left, EntityLivingBase right) {
        return left != null && right != null && left.getEntityId() == right.getEntityId();
    }

    private boolean canTriggerAttackSequence(EntityPlayerSP player, EntityLivingBase target) {
        return canTriggerAttackSequence(player, target, null);
    }

    private boolean canTriggerAttackSequence(EntityPlayerSP player, EntityLivingBase target,
            AreaHuntOptions areaOptions) {
        if (player == null || target == null) {
            return false;
        }
        if (this.sequenceCooldownTicks > 0 || this.attackSequenceExecutor.isRunning()) {
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

        this.attackSequenceExecutor.start(configuredSequence, player, target);
        return this.attackSequenceExecutor.isRunning();
    }

    private static boolean hasConfiguredAttackSequence() {
        String sequenceName = getConfiguredAttackSequenceName();
        return !sequenceName.isEmpty() && PathSequenceManager.hasSequence(sequenceName);
    }

    public static String getConfiguredAttackSequenceName() {
        return attackSequenceName == null ? "" : attackSequenceName.trim();
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
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess == null || target == null) {
            return false;
        }
        double radius = getEffectiveHuntFixedDistance();
        boolean sameTarget = target.getEntityId() == this.lastOrbitProcessTargetEntityId;
        boolean sameRadius = !Double.isNaN(this.lastOrbitProcessRequestedRadius)
                && Math.abs(this.lastOrbitProcessRequestedRadius - radius) <= 0.001D;
        boolean shouldRefreshRequest = !orbitProcess.isActive()
                || !sameTarget
                || !sameRadius
                || nowTick - this.lastOrbitProcessRequestTick >= HUNT_ORBIT_PROCESS_REQUEST_INTERVAL_TICKS;
        if (shouldRefreshRequest) {
            this.lastOrbitProcessRequestTick = nowTick;
            this.lastOrbitProcessTargetEntityId = target.getEntityId();
            this.lastOrbitProcessRequestedRadius = radius;
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
        KillAuraOrbitProcess orbitProcess = getKillAuraOrbitProcess();
        if (orbitProcess != null) {
            orbitProcess.requestStop();
        }
        this.lastOrbitProcessRequestTick = -99999;
        this.lastOrbitProcessTargetEntityId = Integer.MIN_VALUE;
        this.lastOrbitProcessRequestedRadius = Double.NaN;
    }

    private void renderHuntOrbitLoop() {
        if (!isHuntOrbitEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || this.currentTargetEntityId == -1) {
            return;
        }
        Entity entity = mc.world.getEntityByID(this.currentTargetEntityId);
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
        if (player == null || target == null) {
            stopHuntNavigation();
            return;
        }
        if (isHuntOrbitEnabled() && shouldBlockOrbitNavigationWhileAirborne(player)) {
            stopHuntNavigation();
            return;
        }

        int nowTick = player.ticksExisted;
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
        double dx = target.posX - this.lastHuntTargetX;
        double dz = target.posZ - this.lastHuntTargetZ;
        double movedSq = dx * dx + dz * dz;
        boolean targetChanged = targetId != this.lastHuntTargetEntityId;
        boolean targetMoved = movedSq >= HUNT_GOTO_MOVE_THRESHOLD_SQ;
        boolean previousGoalReached = !targetChanged && !targetMoved && hasReachedLastHuntGoal(player);

        // Do not let the previous target's landing point block a new target.
        // This is especially visible when that point is a stair/snow column and
        // Baritone resolves it to the player's current logical feet node.
        if (huntNavigationActive && !previousGoalReached && !targetChanged && !targetMoved) {
            if (!EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()
                    && !Double.isNaN(this.lastHuntGoalX)
                    && !Double.isNaN(this.lastHuntGoalZ)
                    && (!this.lastHuntGoalUsesY || !Double.isNaN(this.lastHuntGoalY))
                    && (nowTick - this.lastHuntGotoTick) >= HUNT_GOTO_INTERVAL_TICKS) {
                boolean dispatched = dispatchHuntNavigationGoal(target, this.lastHuntGoalX, this.lastHuntGoalY,
                        this.lastHuntGoalZ, this.lastHuntGoalUsesY, nowTick);
                if (!dispatched) {
                    // Avoid a tight retry loop if the navigation bridge rejects
                    // the refresh; the next interval will try again.
                    this.lastHuntGotoTick = nowTick;
                }
            }
            return;
        }

        boolean shouldSendGoto = !huntNavigationActive
                || previousGoalReached
                || targetChanged
                || targetMoved;

        if (shouldSendGoto) {
            if (isHuntFixedDistanceMode()) {
                double[] safeDestination = findFixedDistanceHuntNavigationDestination(player, target);
                if (safeDestination != null && !isMeaningfulHuntNavigationDestination(player, target,
                        safeDestination, getEffectiveHuntFixedDistance())) {
                    safeDestination = null;
                }
                if (safeDestination != null) {
                    dispatchHuntNavigationGoal(target, safeDestination[0], safeDestination[1], safeDestination[2], true,
                            nowTick);
                } else {
                    // If the orbit process failed to produce a usable loop, do not keep
                    // simulating a fake orbit point with the legacy fallback. That causes
                    // "no red loop, but the goal point still jumps around the circle".
                    // In this case we should fall back to a plain fixed-distance anchor.
                    double[] destination = computeFixedDistanceHuntDestination(player, target);
                    if (isBlockedHuntNavigationDestination(target, destination[0], target.posY, destination[2])) {
                        temporarilyExcludeUnreachableHuntTarget(target, nowTick);
                        return;
                    }
                    dispatchHuntNavigationGoal(target, destination[0], target.posY, destination[2], false, nowTick);
                }
            } else {
                double[] safeDestination = findApproachHuntNavigationDestination(player, target);
                double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS,
                        Math.min(Math.max(HUNT_APPROACH_MIN_STAND_RADIUS,
                                attackRange - HUNT_APPROACH_TARGET_BUFFER),
                                attackRange - HUNT_APPROACH_TARGET_BUFFER * 2.0D));
                if (safeDestination != null && !isMeaningfulHuntNavigationDestination(player, target,
                        safeDestination, preferredRadius)) {
                    safeDestination = null;
                }
                if (safeDestination != null) {
                    dispatchHuntNavigationGoal(target, safeDestination[0], safeDestination[1], safeDestination[2], true,
                            nowTick);
                } else {
                    if (isBlockedHuntNavigationDestination(target, target.posX, target.posY, target.posZ)) {
                        temporarilyExcludeUnreachableHuntTarget(target, nowTick);
                        return;
                    }
                    dispatchHuntNavigationGoal(target, target.posX, target.posY, target.posZ, false, nowTick);
                }
            }
        }
    }

    private boolean hasReachedLastHuntGoal(EntityPlayerSP player) {
        if (player == null || !this.huntNavigationActive
                || Double.isNaN(this.lastHuntGoalX)
                || Double.isNaN(this.lastHuntGoalZ)
                || (this.lastHuntGoalUsesY && Double.isNaN(this.lastHuntGoalY))) {
            return false;
        }

        BetterBlockPos logicalFeet = null;
        if (this.lastHuntGoalUsesY) {
            // Match Baritone's logical feet node on slabs/stairs instead of
            // using the entity's raw fractional posY. The latter can differ
            // by a full block on partial collision surfaces.
            logicalFeet = getHuntPlayerFeet(player);
            if (logicalFeet != null
                    && logicalFeet.getX() == MathHelper.floor(this.lastHuntGoalX)
                    && logicalFeet.getZ() == MathHelper.floor(this.lastHuntGoalZ)) {
                // Goal normalization may move a partial-surface goal one node
                // upward. Keep the original arrival tolerance in logical Y.
                return Math.abs(logicalFeet.getY() - MathHelper.floor(this.lastHuntGoalY)) <= 1;
            }
        }

        double dx = player.posX - this.lastHuntGoalX;
        double dz = player.posZ - this.lastHuntGoalZ;
        double horizontalDistanceSq = dx * dx + dz * dz;
        if (horizontalDistanceSq > HUNT_GOAL_REACHED_TOLERANCE_SQ) {
            return false;
        }

        if (!this.lastHuntGoalUsesY) {
            // GoalXZ deliberately has no vertical component. Comparing its
            // cached Y with the target's entity Y made stair/snow transitions
            // look like an unfinished route forever.
            return true;
        }

        if (logicalFeet != null) {
            return Math.abs(logicalFeet.getY() - MathHelper.floor(this.lastHuntGoalY)) <= 1;
        }

        // Keep a conservative fallback for the short period before Baritone's
        // player context is available (world load or teardown).
        double dy = Math.abs(player.posY - this.lastHuntGoalY);
        return dy <= 1.25D;
    }

    private BetterBlockPos getHuntPlayerFeet(EntityPlayerSP player) {
        if (player == null) {
            return null;
        }
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && baritone.getPlayerContext() != null) {
                return baritone.getPlayerContext().playerFeet();
            }
        } catch (Throwable ignored) {
        }
        return null;
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
        if (target == null) {
            return;
        }
        this.huntNavigationActive = true;
        this.lastHuntGotoTick = nowTick;
        this.lastHuntTargetEntityId = target.getEntityId();
        this.lastHuntTargetX = target.posX;
        this.lastHuntTargetZ = target.posZ;
        this.lastHuntGoalX = goalX;
        this.lastHuntGoalY = goalY;
        this.lastHuntGoalZ = goalZ;
        this.lastHuntGoalUsesY = goalUsesY;
    }

    private void temporarilyExcludeUnreachableHuntTarget(EntityLivingBase target, int nowTick) {
        if (target == null) {
            return;
        }
        HuntUnreachableTracker tracker = huntUnreachableTrackers.get(target.getEntityId());
        if (tracker == null) {
            tracker = new HuntUnreachableTracker(target);
            huntUnreachableTrackers.put(target.getEntityId(), tracker);
        }
        tracker.refreshTargetPosition(target);
        tracker.failedGoalCount = 0;
        tracker.excludeUntilTick = nowTick + HUNT_UNREACHABLE_TEMP_EXCLUDE_TICKS;
        pruneHuntUnreachableTrackingSize();
        zszlScriptMod.LOGGER.info("杀戮光环追击目标暂时排除: id={}, name={}, untilTick={}",
                target.getEntityId(), getFilterableEntityName(target), tracker.excludeUntilTick);
        stopHuntNavigation();
        if (this.currentTargetEntityId == target.getEntityId()) {
            this.currentTargetEntityId = -1;
            clearAimTargetTransition();
            clearVisualRotationCache();
            this.attackSequenceExecutor.stop();
        }
    }

    private EntityItem findHuntPriorityPickupItem(EntityPlayerSP player) {
        if (player == null || player.world == null || !isHuntEnabled() || huntRadius <= 0.05F) {
            return null;
        }

        int nowTick = player.ticksExisted;
        if (nowTick < huntPickupRetryAfterTick) {
            return null;
        }
        double radiusSq = huntRadius * huntRadius;
        if (nowTick - lastHuntPickupSearchTick < HUNT_PICKUP_SEARCH_INTERVAL_TICKS) {
            EntityItem cached = resolveCachedHuntPickupItem(player, radiusSq);
            if (cached != null) {
                return cached;
            }
            if (!lastHuntPickupSearchFound) {
                return null;
            }
        }

        EntityItem bestItem = null;
        double bestDistSq = Double.MAX_VALUE;
        int bestPriority = Integer.MIN_VALUE;
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();

        for (Entity entity : player.world.loadedEntityList) {
            if (!(entity instanceof EntityItem)) {
                continue;
            }

            EntityItem item = (EntityItem) entity;
            if (item.isDead) {
                continue;
            }

            double playerDistSq = player.getDistanceSq(item);
            if (playerDistSq > radiusSq) {
                continue;
            }

            HuntPickupRuleDecision decision = evaluateHuntPickupItem(player, item, Math.sqrt(playerDistSq),
                    hasAllowRules);
            if (!decision.allowed) {
                continue;
            }

            if (decision.priority > bestPriority
                    || (decision.priority == bestPriority && playerDistSq < bestDistSq)) {
                bestPriority = decision.priority;
                bestDistSq = playerDistSq;
                bestItem = item;
            }
        }

        lastHuntPickupSearchTick = nowTick;
        lastHuntPickupSearchTargetEntityId = bestItem == null ? Integer.MIN_VALUE : bestItem.getEntityId();
        lastHuntPickupSearchFound = bestItem != null;
        return bestItem;
    }

    private HuntPickupRuleDecision evaluateHuntPickupItem(EntityPlayerSP player, EntityItem item,
            double playerDistance, boolean hasAllowRules) {
        if (item == null || item.isDead) {
            return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
        }

        ItemStack stack = item.getItem();
        if (stack == null || stack.isEmpty()) {
            return new HuntPickupRuleDecision(false, Integer.MIN_VALUE);
        }

        List<HuntPickupRule> rules = huntPickupRules == null ? new ArrayList<HuntPickupRule>() : huntPickupRules;
        if (rules.isEmpty()) {
            return new HuntPickupRuleDecision(true, 0);
        }

        String rarity = normalizeHuntPickupRarityToken(getHuntPickupRarityToken(stack));
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

    private boolean hasEnabledHuntPickupAllowRules() {
        if (huntPickupRules == null || huntPickupRules.isEmpty()) {
            return false;
        }
        for (HuntPickupRule rule : huntPickupRules) {
            if (rule != null && rule.enabled && !HUNT_PICKUP_RULE_MODE_BLOCK.equals(rule.mode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMatchingHuntPickupRule(EntityItem item, HuntPickupRule rule, String rarity,
            double playerDistance) {
        if (rule == null || !rule.enabled) {
            return false;
        }
        if (rule.maxDistance > 0.0F && playerDistance - rule.maxDistance > 0.0001D) {
            return false;
        }
        ItemStack stack = item == null ? ItemStack.EMPTY : item.getItem();
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

    private EntityItem resolveCachedHuntPickupItem(EntityPlayerSP player, double radiusSq) {
        if (player == null || player.world == null || lastHuntPickupSearchTargetEntityId == Integer.MIN_VALUE) {
            return null;
        }
        Entity entity = player.world.getEntityByID(lastHuntPickupSearchTargetEntityId);
        if (!(entity instanceof EntityItem)) {
            return null;
        }
        EntityItem item = (EntityItem) entity;
        if (item.isDead) {
            return null;
        }
        double distanceSq = player.getDistanceSq(item);
        if (distanceSq > radiusSq) {
            return null;
        }
        boolean hasAllowRules = hasEnabledHuntPickupAllowRules();
        HuntPickupRuleDecision decision = evaluateHuntPickupItem(player, item, Math.sqrt(distanceSq), hasAllowRules);
        return decision.allowed ? item : null;
    }

    private void handleHuntPickupMovement(EntityPlayerSP player, EntityItem item) {
        if (player == null || item == null || item.isDead) {
            stopHuntPickupNavigation();
            return;
        }

        int nowTick = player.ticksExisted;
        int itemId = item.getEntityId();
        if (hasReachedHuntPickupItem(player, item)) {
            // This deliberately mirrors AutoPickupHandler: once within its
            // pickup radius, release navigation and wait for the server-side
            // item entity to disappear. Restarting a forced GoalBlock route
            // here repeatedly cancelled the player before vanilla collection.
            if (this.huntPickupReachedEntityId != itemId) {
                EmbeddedNavigationHandler.INSTANCE.stop();
            }
            this.huntPickupNavigationActive = false;
            this.lastHuntPickupTargetEntityId = itemId;
            this.huntPickupReachedEntityId = itemId;
            this.huntPickupLastProgressTick = nowTick;
            return;
        }
        this.huntPickupReachedEntityId = Integer.MIN_VALUE;

        double distanceSq = player.getDistanceSq(item);
        if (this.huntPickupNavigationActive && itemId == this.lastHuntPickupTargetEntityId
                && distanceSq + HUNT_PICKUP_PROGRESS_EPSILON_SQ < this.huntPickupLastDistanceSq) {
            this.huntPickupLastDistanceSq = distanceSq;
            this.huntPickupLastProgressTick = nowTick;
        }
        boolean stalled = this.huntPickupNavigationActive
                && itemId == this.lastHuntPickupTargetEntityId
                && nowTick - this.huntPickupLastProgressTick >= HUNT_PICKUP_NAVIGATION_STALL_TICKS;
        int gotoInterval = item.onGround ? HUNT_PICKUP_GOTO_INTERVAL_TICKS : HUNT_PICKUP_AIRBORNE_GOTO_INTERVAL_TICKS;
        boolean shouldSendGoto = !huntPickupNavigationActive
                || itemId != this.lastHuntPickupTargetEntityId
                || stalled
                || (nowTick - this.lastHuntPickupGotoTick) >= gotoInterval;
        if (!shouldSendGoto) {
            return;
        }

        if (stalled) {
            EmbeddedNavigationHandler.INSTANCE.stop();
        }
        boolean dispatched = dispatchHuntPickupGoto(item);
        if (dispatched) {
            this.huntPickupNavigationActive = true;
            this.lastHuntPickupGotoTick = nowTick;
            this.huntPickupLastProgressTick = nowTick;
            this.huntPickupLastDistanceSq = distanceSq;
            this.lastHuntPickupTargetEntityId = itemId;
            this.huntPickupRetryAfterTick = -99999;
        } else if (this.huntPickupNavigationActive
                && itemId == this.lastHuntPickupTargetEntityId
                && EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()) {
            // The shared manager intentionally uses the normal goto throttle.
            // A throttled refresh must not discard the still-running route.
            this.lastHuntPickupGotoTick = nowTick;
        } else {
            // Do not leave a false active state behind when the navigation layer
            // rejects a request (for example while another command is throttled).
            this.huntPickupNavigationActive = false;
            this.lastHuntPickupGotoTick = -99999;
            this.lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
            this.huntPickupRetryAfterTick = nowTick + HUNT_PICKUP_RETRY_WAIT_TICKS;
        }
    }

    private boolean dispatchHuntPickupGoto(EntityItem item) {
        if (item == null) {
            return false;
        }
        if (item.onGround) {
            // Reuse the automatic pickup manager's exact local-goal resolver.
            // It navigates to a passable feet cell (or its direct upper cell),
            // rather than the item entity's often-unwalkable raw Y coordinate.
            return AutoPickupHandler.INSTANCE.startNavigationToPickupItem(item);
        }

        // An airborne ItemEntity does not provide a useful standable Y goal.
        // Approach its short-term horizontal landing trajectory, then vanilla
        // collision picks it up as soon as the item reaches the player.
        double goalX = item.posX + item.motionX * HUNT_PICKUP_AIRBORNE_LEAD_TICKS;
        double goalZ = item.posZ + item.motionZ * HUNT_PICKUP_AIRBORNE_LEAD_TICKS;
        return EmbeddedNavigationHandler.INSTANCE.startGotoXZ(goalX, goalZ);
    }

    private boolean hasReachedHuntPickupItem(EntityPlayerSP player, EntityItem item) {
        if (player == null || item == null || item.isDead) {
            return false;
        }

        // Keep this consistent with the automatic pickup rule manager's
        // default target reach distance.
        return player.getDistanceSq(item) < 0.25D;
    }

    private void stopHuntNavigation() {
        this.huntOrbitController.stop();
        stopHuntOrbitProcess();
        stopEmbeddedHuntNavigation();
    }

    private void stopEmbeddedHuntNavigation() {
        if (!this.huntNavigationActive) {
            return;
        }
        EmbeddedNavigationHandler.INSTANCE.stop();
        this.huntNavigationActive = false;
        this.lastHuntGotoTick = -99999;
        this.lastHuntTargetEntityId = Integer.MIN_VALUE;
        this.lastHuntTargetX = 0.0D;
        this.lastHuntTargetZ = 0.0D;
        this.lastHuntGoalX = Double.NaN;
        this.lastHuntGoalY = Double.NaN;
        this.lastHuntGoalZ = Double.NaN;
        this.lastHuntGoalUsesY = false;
    }

    private void stopHuntPickupNavigation() {
        if (!this.huntPickupNavigationActive && this.lastHuntPickupTargetEntityId == Integer.MIN_VALUE) {
            return;
        }
        EmbeddedNavigationHandler.INSTANCE.stop();
        this.huntPickupNavigationActive = false;
        this.lastHuntPickupGotoTick = -99999;
        this.lastHuntPickupTargetEntityId = Integer.MIN_VALUE;
        this.huntPickupReachedEntityId = Integer.MIN_VALUE;
        this.huntPickupLastProgressTick = -99999;
        this.huntPickupLastDistanceSq = Double.MAX_VALUE;
        this.huntPickupRetryAfterTick = -99999;
    }

    public boolean isHuntPickupNavigationActive() {
        return this.huntPickupNavigationActive;
    }

    private boolean shouldRunHuntMovement(EntityPlayerSP player, EntityLivingBase target) {
        if (isTeleportAttackMode() || !isHuntEnabled() || player == null || target == null) {
            return false;
        }
        if (isHuntOrbitEnabled() && shouldBlockOrbitNavigationWhileAirborne(player)) {
            return false;
        }

        double distance = player.getDistance(target);
        boolean missingAttackLineOfSight = shouldRequireAttackLineOfSight() && !player.canEntityBeSeen(target);
        if (isHuntFixedDistanceMode()) {
            if (canStartOrbitHunt(player, target)) {
                return true;
            }
            return missingAttackLineOfSight
                    || Math.abs(distance - getEffectiveHuntFixedDistance()) > HUNT_FIXED_DISTANCE_TOLERANCE;
        }
        return missingAttackLineOfSight || distance > attackRange;
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
                        huntJumpOrbitEnabled, true, true));
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
        double maxStandRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS, attackRange - HUNT_APPROACH_TARGET_BUFFER);
        double preferredRadius = Math.max(HUNT_APPROACH_MIN_STAND_RADIUS,
                Math.min(maxStandRadius, attackRange - HUNT_APPROACH_TARGET_BUFFER * 2.0D));
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
        boolean approachNeedsProgress = !isHuntFixedDistanceMode() && currentDistance > attackRange;
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
        double outsideAttackRangePenalty = Math.max(0.0D, targetDistance - attackRange);
        double opennessPenalty = 4 - getHuntStandOpenness(new BlockPos(destination[0], destination[1],
                destination[2]));
        // When several floors intersect the attack sphere, stay on the player's
        // current plane whenever that floor can actually see/reach the target.
        // Target-height matching is only a weak tie breaker.
        return new HuntScoreBreakdown(
                radiusPenalty * huntScoreRadiusWeight,
                playerDistancePenalty * huntScorePlayerDistanceWeight,
                playerHeightPenalty * huntScorePlayerPlaneWeight,
                targetHeightPenalty * huntScoreTargetHeightWeight,
                outsideAttackRangePenalty * huntScoreAttackRangeWeight,
                hasLineOfSight ? 0.0D : huntScoreVisibilityWeight,
                opennessPenalty * huntScoreOpennessWeight,
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
        if (this.teleportDebugBuffer.size() >= 4096) {
            this.teleportDebugBuffer.remove(0);
        }
        this.teleportDebugBuffer.add("tick=" + tick + " event=" + event + " " + message);
    }

    private void flushTeleportDebug() {
        if (this.teleportDebugBuffer.isEmpty()) {
            return;
        }
        if (!isTeleportDebugEnabled()) {
            this.teleportDebugBuffer.clear();
            return;
        }
        List<String> messages = new ArrayList<>(this.teleportDebugBuffer);
        this.teleportDebugBuffer.clear();
        for (String message : messages) {
            ModConfig.debugLog(DebugModule.KILL_AURA_TELEPORT, message);
        }
    }

    private String formatTeleportTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (target == null) {
            return "target=null";
        }
        double distance = player == null ? -1.0D : Math.sqrt(player.getDistanceSq(target));
        NoDamageAttackTracker tracker = noDamageAttackTrackers.get(target.getEntityId());
        return String.format(Locale.ROOT,
                "id=%d,name=%s,type=%s,hp=%.3f,dead=%s,pos=(%.3f,%.3f,%.3f),distance=%.3f,lastAttackTick=%d,noDamageExcluded=%s,noDamagePending=%d,noDamageConfirmed=%d",
                target.getEntityId(), getFilterableEntityName(target), target.getClass().getSimpleName(),
                target.getHealth(), target.isDead, target.posX, target.posY, target.posZ, distance,
                this.teleportLastAttackTicks.getOrDefault(target.getEntityId(), Integer.MIN_VALUE),
                noDamageExcludedEntityIds.contains(target.getEntityId()),
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
        if (ignoreInvisible && target.isInvisible()) {
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
        if (areaOptions == null && enableNameBlacklist && matchesNameList(targetName, nameBlacklist)) {
            return "NAME_BLACKLIST";
        }
        if (areaOptions == null && enableNameWhitelist) {
            int whitelistPriority = getNormalizedNameListMatchIndex(targetName, nameWhitelist);
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
        if (player == null) {
            return "PLAYER_NULL";
        }
        if (aimOnlyMode) {
            return "AIM_ONLY";
        }
        if (isSequenceAttackMode()) {
            return "SEQUENCE_MODE";
        }
        if (this.attackCooldownTicks > 0) {
            return "ATTACK_COOLDOWN(" + this.attackCooldownTicks + ")";
        }
        if (isTeleportAttackRecoveryActive()) {
            return "PENDING_RETURN";
        }
        if (onlyWeapon && getPreferredAttackHotbarSlot(player) < 0) {
            return "NO_ALLOWED_WEAPON";
        }
        float cooledStrength = player.getCooledAttackStrength(0.0F);
        if (cooledStrength < minAttackStrength) {
            return String.format(Locale.ROOT, "ATTACK_STRENGTH(%.3f<%.3f)", cooledStrength, minAttackStrength);
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
            int verticalPadding = Math.max(3, MathHelper.ceil(attackRange) + 1);
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
                        score += Math.abs(centerY - player.posY) * huntScorePlayerPlaneWeight
                                + Math.abs(centerY - target.posY) * huntScoreTargetHeightWeight
                                + Math.max(0.0D, targetDistance - attackRange) * huntScoreAttackRangeWeight;
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
                score += visible ? 0.0D : huntScoreVisibilityWeight * 1.5D;
                score += (4 - getHuntStandOpenness(candidate.position))
                        * (huntScoreOpennessWeight / DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT) * 0.8D;
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
        if (mc.world == null || standPos == null || target == null) {
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
            RayTraceResult ray = mc.world.rayTraceBlocks(eyePos, targetPoint, false, true, false);
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
        if (standPos == null || Minecraft.getMinecraft().world == null) {
            return 0;
        }
        int openSides = 0;
        BlockPos[] neighbors = new BlockPos[] {
                standPos.north(), standPos.south(), standPos.west(), standPos.east()
        };
        for (BlockPos neighbor : neighbors) {
            IBlockState feetState = Minecraft.getMinecraft().world.getBlockState(neighbor);
            IBlockState headState = Minecraft.getMinecraft().world.getBlockState(neighbor.up());
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
        if (mc.world == null || standPos == null) {
            return false;
        }

        IBlockState feetState = mc.world.getBlockState(standPos);
        IBlockState headState = mc.world.getBlockState(standPos.up());
        IBlockState belowState = mc.world.getBlockState(standPos.down());

        boolean feetPassable = !feetState.getMaterial().blocksMovement();
        boolean headPassable = !headState.getMaterial().blocksMovement();
        boolean hasGround = hasStandableTopSurface(mc.world, standPos.down(), belowState);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }

        if (active) {
            if (!this.fullBrightApplied) {
                this.previousGammaSetting = mc.gameSettings.gammaSetting;
                this.fullBrightApplied = true;
            }
            float targetGamma = Math.max(1.0F, fullBrightGamma);
            if (mc.gameSettings.gammaSetting != targetGamma) {
                mc.gameSettings.gammaSetting = targetGamma;
            }
        } else {
            restoreFullBright();
        }
    }

    private void restoreFullBright() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!this.fullBrightApplied) {
            return;
        }
        if (mc != null && mc.gameSettings != null) {
            mc.gameSettings.gammaSetting = this.previousGammaSetting;
        }
        this.fullBrightApplied = false;
    }

    public void applyMovementProtection(EntityPlayerSP player, boolean active, boolean applyNoCollision,
            boolean applyAntiKnockback) {
        if (player == null) {
            return;
        }

        if (!active) {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
            this.lastSafeMotionX = 0.0D;
            this.lastSafeMotionY = 0.0D;
            this.lastSafeMotionZ = 0.0D;
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
                double preservedSpeed = Math.sqrt(this.lastSafeMotionX * this.lastSafeMotionX
                        + this.lastSafeMotionZ * this.lastSafeMotionZ);
                double[] preservedMotion = resolveProtectionMotion(player, preservedSpeed);
                player.motionX = preservedMotion[0];
                player.motionZ = preservedMotion[1];
                player.velocityChanged = true;
            }

            if (!jumpPressed && player.motionY > 0.0D) {
                player.motionY = Math.min(0.0D, this.lastSafeMotionY);
                player.velocityChanged = true;
            }
        } else {
            this.lastSafeMotionX = player.motionX;
            this.lastSafeMotionY = player.motionY;
            this.lastSafeMotionZ = player.motionZ;
        }
    }

    private void applyKillAuraOwnMovementProtection(EntityPlayerSP player, boolean active, boolean applyNoCollision,
            boolean applyAntiKnockback) {
        if (player == null) {
            return;
        }

        if (!active) {
            player.entityCollisionReduction = 0.0F;
            player.noClip = false;
            this.lastSafeMotionX = 0.0D;
            this.lastSafeMotionY = 0.0D;
            this.lastSafeMotionZ = 0.0D;
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
                double preservedSpeed = Math.sqrt(this.lastSafeMotionX * this.lastSafeMotionX
                        + this.lastSafeMotionZ * this.lastSafeMotionZ);
                double[] preservedMotion = resolveProtectionMotion(player, preservedSpeed);
                player.motionX = preservedMotion[0];
                player.motionZ = preservedMotion[1];
                player.velocityChanged = true;
            }

            if (!jumpPressed && player.motionY > 0.0D) {
                player.motionY = Math.min(0.0D, this.lastSafeMotionY);
                player.velocityChanged = true;
            }
        } else {
            this.lastSafeMotionX = player.motionX;
            this.lastSafeMotionY = player.motionY;
            this.lastSafeMotionZ = player.motionZ;
        }
    }

    private double[] resolveProtectionMotion(EntityPlayerSP player, double speed) {
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
            return new double[] { this.lastSafeMotionX, this.lastSafeMotionZ };
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
        List<String> result = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || mc.world == null) {
            return result;
        }

        float actualRange = MathHelper.clamp(scanRange, 1.0F, 64.0F);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Entity entity : mc.world.loadedEntityList) {
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
            if (!name.isEmpty()) {
                unique.add(name);
            }
        }

        result.addAll(unique);
        result.sort((a, b) -> a.compareToIgnoreCase(b));
        return result;
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
        for (int i = 0; i < presets.size(); i++) {
            KillAuraPreset preset = presets.get(i);
            if (preset != null && normalizedName.equalsIgnoreCase(normalizePresetName(preset.name))) {
                return i;
            }
        }
        return -1;
    }

    private static KillAuraPreset captureCurrentAsPreset(String name) {
        KillAuraPreset preset = new KillAuraPreset();
        preset.name = normalizePresetName(name);
        preset.rotateToTarget = rotateToTarget;
        preset.smoothRotation = smoothRotation;
        preset.smoothMaxTurnStep = smoothMaxTurnStep;
        preset.smoothMaxTurnStepSpec = smoothMaxTurnStepSpec;
        preset.rotateOnlyOnAttack = rotateOnlyOnAttack;
        preset.relockOnlyWhenNoCrosshairTarget = relockOnlyWhenNoCrosshairTarget;
        preset.onlyAttackWhenLookingAtTarget = onlyAttackWhenLookingAtTarget;
        preset.requireLineOfSight = requireLineOfSight;
        preset.throughWallAttack = throughWallAttack;
        preset.targetHostile = targetHostile;
        preset.targetPassive = targetPassive;
        preset.targetPlayers = targetPlayers;
        preset.targetEnderCrystal = targetEnderCrystal;
        preset.onlyWeapon = onlyWeapon;
        preset.aimOnlyMode = aimOnlyMode;
        preset.focusSingleTarget = focusSingleTarget;
        preset.ignoreInvisible = ignoreInvisible;
        preset.enableNoCollision = enableNoCollision;
        preset.enableAntiKnockback = enableAntiKnockback;
        preset.enableFullBrightVision = enableFullBrightVision;
        preset.fullBrightGamma = fullBrightGamma;
        preset.attackMode = attackMode;
        preset.attackSequenceName = attackSequenceName;
        preset.attackSequenceDelayTicks = attackSequenceDelayTicks;
        preset.attackSequenceDelayTicksSpec = attackSequenceDelayTicksSpec;
        preset.aimYawOffset = aimYawOffset;
        preset.aimYawOffsetSpec = aimYawOffsetSpec;
        preset.aimPitchOffset = aimPitchOffset;
        preset.aimPitchOffsetSpec = aimPitchOffsetSpec;
        preset.huntMode = huntMode;
        preset.huntPickupItemsEnabled = huntPickupItemsEnabled;
        preset.huntPickupRules = copyHuntPickupRuleList(huntPickupRules);
        preset.visualizeHuntRadius = visualizeHuntRadius;
        preset.huntRadius = huntRadius;
        preset.huntFixedDistance = huntFixedDistance;
        preset.huntUpRange = huntUpRange;
        preset.huntDownRange = huntDownRange;
        preset.huntOrbitEnabled = huntOrbitEnabled;
        preset.huntJumpOrbitEnabled = huntJumpOrbitEnabled;
        preset.huntOrbitSamplePoints = huntOrbitSamplePoints;
        preset.enableNameWhitelist = enableNameWhitelist;
        preset.enableNameBlacklist = enableNameBlacklist;
        preset.nameWhitelist = new ArrayList<>(nameWhitelist == null ? new ArrayList<>() : nameWhitelist);
        preset.nameBlacklist = new ArrayList<>(nameBlacklist == null ? new ArrayList<>() : nameBlacklist);
        preset.nearbyEntityScanRange = nearbyEntityScanRange;
        preset.attackRange = attackRange;
        preset.huntScoreRadiusWeight = huntScoreRadiusWeight;
        preset.huntScorePlayerDistanceWeight = huntScorePlayerDistanceWeight;
        preset.huntScorePlayerPlaneWeight = huntScorePlayerPlaneWeight;
        preset.huntScoreTargetHeightWeight = huntScoreTargetHeightWeight;
        preset.huntScoreAttackRangeWeight = huntScoreAttackRangeWeight;
        preset.huntScoreVisibilityWeight = huntScoreVisibilityWeight;
        preset.huntScoreOpennessWeight = huntScoreOpennessWeight;
        preset.minAttackStrength = minAttackStrength;
        preset.minTurnSpeed = minTurnSpeed;
        preset.maxTurnSpeed = maxTurnSpeed;
        preset.minAttackIntervalTicks = minAttackIntervalTicks;
        preset.targetsPerAttack = targetsPerAttack;
        preset.teleportAttackPacketLimitPerTick = teleportAttackPacketLimitPerTick;
        preset.teleportStepDistance = teleportStepDistance;
        preset.teleportStationAttackRadius = teleportStationAttackRadius;
        preset.disableOnDisconnect = disableOnDisconnect;
        preset.noDamageAttackLimit = noDamageAttackLimit;
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
        normalizedPreset.teleportAttackPacketLimitPerTick = MathHelper.clamp(
                normalizedPreset.teleportAttackPacketLimitPerTick,
                MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK, MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK);
        normalizedPreset.teleportStepDistance = clampFiniteFloat(normalizedPreset.teleportStepDistance,
                DEFAULT_TELEPORT_STEP_DISTANCE, MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
        normalizedPreset.teleportStationAttackRadius = clampFiniteFloat(
                normalizedPreset.teleportStationAttackRadius, DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
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
                && !normalizedPreset.targetEnderCrystal) {
            normalizedPreset.targetHostile = true;
        }
        if (HUNT_MODE_OFF.equals(normalizedPreset.huntMode)) {
            normalizedPreset.visualizeHuntRadius = false;
        }
        return normalizedPreset;
    }

    public static void resetHuntScoreWeights() {
        huntScoreRadiusWeight = DEFAULT_HUNT_SCORE_RADIUS_WEIGHT;
        huntScorePlayerDistanceWeight = DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT;
        huntScorePlayerPlaneWeight = DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT;
        huntScoreTargetHeightWeight = DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT;
        huntScoreAttackRangeWeight = DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT;
        huntScoreVisibilityWeight = DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT;
        huntScoreOpennessWeight = DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT;
        INSTANCE.lastHuntScoreDebugTick = Integer.MIN_VALUE;
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
        attackRange = MathHelper.clamp(attackRange, 1.0F, 200.0F);
        huntScoreRadiusWeight = clampHuntScoreWeight(huntScoreRadiusWeight, DEFAULT_HUNT_SCORE_RADIUS_WEIGHT);
        huntScorePlayerDistanceWeight = clampHuntScoreWeight(huntScorePlayerDistanceWeight,
                DEFAULT_HUNT_SCORE_PLAYER_DISTANCE_WEIGHT);
        huntScorePlayerPlaneWeight = clampHuntScoreWeight(huntScorePlayerPlaneWeight,
                DEFAULT_HUNT_SCORE_PLAYER_PLANE_WEIGHT);
        huntScoreTargetHeightWeight = clampHuntScoreWeight(huntScoreTargetHeightWeight,
                DEFAULT_HUNT_SCORE_TARGET_HEIGHT_WEIGHT);
        huntScoreAttackRangeWeight = clampHuntScoreWeight(huntScoreAttackRangeWeight,
                DEFAULT_HUNT_SCORE_ATTACK_RANGE_WEIGHT);
        huntScoreVisibilityWeight = clampHuntScoreWeight(huntScoreVisibilityWeight,
                DEFAULT_HUNT_SCORE_VISIBILITY_WEIGHT);
        huntScoreOpennessWeight = clampHuntScoreWeight(huntScoreOpennessWeight,
                DEFAULT_HUNT_SCORE_OPENNESS_WEIGHT);
        minAttackStrength = MathHelper.clamp(minAttackStrength, 0.0F, 1.0F);
        SmoothTurnStepRange turnRange = parseSmoothMaxTurnStepSpec(smoothMaxTurnStepSpec, smoothMaxTurnStep);
        smoothMaxTurnStep = turnRange.min;
        smoothMaxTurnStepSpec = turnRange.toSpec();
        minTurnSpeed = MathHelper.clamp(minTurnSpeed, 1.0F, 40.0F);
        maxTurnSpeed = MathHelper.clamp(maxTurnSpeed, minTurnSpeed, 60.0F);
        minAttackIntervalTicks = MathHelper.clamp(minAttackIntervalTicks, 0, 20);
        targetsPerAttack = MathHelper.clamp(targetsPerAttack, 1, 50);
        teleportAttackPacketLimitPerTick = MathHelper.clamp(teleportAttackPacketLimitPerTick,
                MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK, MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK);
        teleportStepDistance = clampFiniteFloat(teleportStepDistance, DEFAULT_TELEPORT_STEP_DISTANCE,
                MIN_TELEPORT_STEP_DISTANCE, MAX_TELEPORT_STEP_DISTANCE);
        teleportStationAttackRadius = clampFiniteFloat(teleportStationAttackRadius,
                DEFAULT_TELEPORT_STATION_ATTACK_RADIUS,
                MIN_TELEPORT_STATION_ATTACK_RADIUS, MAX_TELEPORT_STATION_ATTACK_RADIUS);
        noDamageAttackLimit = MathHelper.clamp(noDamageAttackLimit, 0, MAX_NO_DAMAGE_ATTACK_LIMIT);
        TickRangeSpec.Range attackSequenceDelayRange = TickRangeSpec.parse(attackSequenceDelayTicksSpec,
                attackSequenceDelayTicks, MIN_ATTACK_SEQUENCE_DELAY_TICKS, MAX_ATTACK_SEQUENCE_DELAY_TICKS);
        attackSequenceDelayTicks = attackSequenceDelayRange.getMin();
        attackSequenceDelayTicksSpec = attackSequenceDelayRange.toSpec();
        AimOffsetRange yawOffsetRange = parseAimOffsetSpec(aimYawOffsetSpec, aimYawOffset);
        aimYawOffset = yawOffsetRange.min;
        aimYawOffsetSpec = yawOffsetRange.toSpec();
        AimOffsetRange pitchOffsetRange = parseAimOffsetSpec(aimPitchOffsetSpec, aimPitchOffset);
        aimPitchOffset = pitchOffsetRange.min;
        aimPitchOffsetSpec = pitchOffsetRange.toSpec();
        huntRadius = MathHelper.clamp(huntRadius, attackRange, 100.0F);
        huntFixedDistance = MathHelper.clamp(huntFixedDistance, 0.5F, 100.0F);
        huntUpRange = MathHelper.clamp(huntUpRange, 0.0F, 100.0F);
        huntDownRange = MathHelper.clamp(huntDownRange, 0.0F, 100.0F);
        huntOrbitSamplePoints = MathHelper.clamp(huntOrbitSamplePoints,
                MIN_HUNT_ORBIT_SAMPLE_POINTS, MAX_HUNT_ORBIT_SAMPLE_POINTS);
        fullBrightGamma = MathHelper.clamp(fullBrightGamma, 1.0F, 1000.0F);
        nearbyEntityScanRange = MathHelper.clamp(nearbyEntityScanRange, 1.0F, 64.0F);
        nameWhitelist = normalizeNameList(nameWhitelist);
        nameBlacklist = normalizeNameList(nameBlacklist);
        huntPickupRules = normalizeHuntPickupRuleList(huntPickupRules);
        attackSequenceName = getConfiguredAttackSequenceName();

        String normalizedAttackMode = attackMode == null ? "" : attackMode.trim().toUpperCase(Locale.ROOT);
        if (ATTACK_MODE_PACKET.equals(normalizedAttackMode)) {
            attackMode = ATTACK_MODE_PACKET;
        } else if (ATTACK_MODE_TELEPORT.equals(normalizedAttackMode)) {
            attackMode = ATTACK_MODE_TELEPORT;
        } else if (ATTACK_MODE_SEQUENCE.equals(normalizedAttackMode)) {
            attackMode = ATTACK_MODE_SEQUENCE;
        } else if (ATTACK_MODE_MOUSE_CLICK.equals(normalizedAttackMode)) {
            attackMode = ATTACK_MODE_MOUSE_CLICK;
        } else {
            attackMode = ATTACK_MODE_NORMAL;
        }

        if (aimOnlyMode) {
            attackMode = ATTACK_MODE_SEQUENCE;
            rotateOnlyOnAttack = false;
        } else if (ATTACK_MODE_PACKET.equals(attackMode)) {
            rotateToTarget = false;
            smoothRotation = false;
            rotateOnlyOnAttack = false;
            relockOnlyWhenNoCrosshairTarget = false;
        }
        rotateOnlyOnAttack = rotateOnlyOnAttack && rotateToTarget;
        relockOnlyWhenNoCrosshairTarget = relockOnlyWhenNoCrosshairTarget && (aimOnlyMode || rotateToTarget);

        huntMode = normalizeHuntModeValue(huntMode);
        huntEnabled = !HUNT_MODE_OFF.equals(huntMode);
        if (!huntEnabled) {
            visualizeHuntRadius = false;
        }

        if (!targetHostile && !targetPassive && !targetPlayers && !targetEnderCrystal) {
            targetHostile = true;
        }
    }

    private double getEffectiveHuntFixedDistance() {
        return Math.max(0.5D, huntFixedDistance);
    }

    public static boolean isHuntOrbitEnabled() {
        return isHuntFixedDistanceMode() && huntOrbitEnabled;
    }

    public static int getConfiguredHuntOrbitSamplePoints() {
        return MathHelper.clamp(huntOrbitSamplePoints, MIN_HUNT_ORBIT_SAMPLE_POINTS, MAX_HUNT_ORBIT_SAMPLE_POINTS);
    }

    public static boolean isHuntOrbitSampleCountAtMaximum() {
        return getConfiguredHuntOrbitSamplePoints() >= MAX_HUNT_ORBIT_SAMPLE_POINTS;
    }

    public boolean shouldKeepRunningDuringGui(Minecraft mc) {
        if (mc == null || mc.player == null || mc.world == null || !enabled || !isHuntOrbitEnabled()) {
            return false;
        }
        return this.huntOrbitController.isActive() && hasActiveTarget(mc.player);
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
                    zszlScriptMod.LOGGER.error("[kill_aura_sequence] 执行动作失败: {}", resolvedAction.getDescription(), e);
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
                    zszlScriptMod.LOGGER.warn("[kill_aura_sequence] 释放按键失败: {}", entry.getKey(), e);
                }
            }
        }
    }
}
