// 文件路径: src/main/java/com/zszl/zszlScriptMod/path/PathSequenceEventListener.java
package com.zszl.zszlScriptMod.path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.path.trigger.PlayerListTriggerSupport;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.mcp.McpObservation;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.AutoEatHandler;
import com.zszl.zszlScriptMod.handlers.AutoUseItemHandler;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.handlers.HuntOrbitController;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.handlers.ItemSpreadHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.handlers.NearbyItemPickupActionHandler;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.process.KillAuraOrbitProcess;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PathRenderer;
import com.zszl.zszlScriptMod.system.AutoUseItemRule;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager;
import com.zszl.zszlScriptMod.path.runtime.locks.ResourceLockManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.PacketFieldRuleManager;
import com.zszl.zszlScriptMod.utils.TickRangeSpec;
import com.zszl.zszlScriptMod.utils.vision.ScreenVisionUtils;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class PathSequenceEventListener {
    private static final Path RUNTIME_CONFIG_PATH = Paths.get(ModConfig.CONFIG_DIR, "path_sequence_runtime_config.json");
    private static final Gson RUNTIME_CONFIG_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_BUILTIN_SEQUENCE_DELAY_TICKS = 5;
    private static final int MIN_BUILTIN_SEQUENCE_DELAY_TICKS = 0;
    private static final int MAX_BUILTIN_SEQUENCE_DELAY_TICKS = 200;
    private static final int PATH_RETRY_NOTIFY_TICKS = 20;
    private static final double PATH_RETRY_MOVEMENT_EPSILON_SQ = 0.16D;
    private static boolean builtinSequenceDelayEnabled = true;
    private static int builtinSequenceDelayTicks = DEFAULT_BUILTIN_SEQUENCE_DELAY_TICKS;
    private static String builtinSequenceDelayTicksSpec = String.valueOf(DEFAULT_BUILTIN_SEQUENCE_DELAY_TICKS);
    private static boolean runtimeConfigLoaded = false;
    private static final ThreadLocal<PathSequenceEventListener> ACTION_EXECUTION_CONTEXT = new ThreadLocal<>();
    private static final List<PathSequenceEventListener> backgroundRunners = new CopyOnWriteArrayList<>();
    // Baritone has one shared pathing process. Track the runner that last
    // dispatched a route so stopping a background sequence can cancel its
    // route without touching a later foreground route.
    private static volatile PathSequenceEventListener navigationOwner;
    private static final List<String> debugBreakpoints = new CopyOnWriteArrayList<>();
    public static final PathSequenceEventListener instance = new PathSequenceEventListener();

    public static class ProgressSnapshot {
        private final String sequenceName;
        private final int stepIndex;
        private final int actionIndex;
        private final boolean atTarget;
        private final int remainingLoops;
        private final int tickDelay;
        private final boolean explicitDelay;
        private final String status;
        private final ScopedRuntimeVariables.ScopeSnapshot variableSnapshot;
        private final int stepRetryUsed;
        private final boolean noStopNavigationOnCompletion;
        private final boolean backgroundRunner;
        private final String executionSessionId;

        public ProgressSnapshot(String sequenceName, int stepIndex, int actionIndex, boolean atTarget,
                int remainingLoops, int tickDelay, boolean explicitDelay, String status,
                ScopedRuntimeVariables.ScopeSnapshot variableSnapshot, int stepRetryUsed,
                boolean noStopNavigationOnCompletion) {
            this(sequenceName, stepIndex, actionIndex, atTarget, remainingLoops, tickDelay, explicitDelay, status,
                    variableSnapshot, stepRetryUsed, noStopNavigationOnCompletion, false, "");
        }

        public ProgressSnapshot(String sequenceName, int stepIndex, int actionIndex, boolean atTarget,
                int remainingLoops, int tickDelay, boolean explicitDelay, String status,
                ScopedRuntimeVariables.ScopeSnapshot variableSnapshot, int stepRetryUsed,
                boolean noStopNavigationOnCompletion, boolean backgroundRunner) {
            this(sequenceName, stepIndex, actionIndex, atTarget, remainingLoops, tickDelay, explicitDelay, status,
                    variableSnapshot, stepRetryUsed, noStopNavigationOnCompletion, backgroundRunner, "");
        }

        public ProgressSnapshot(String sequenceName, int stepIndex, int actionIndex, boolean atTarget,
                int remainingLoops, int tickDelay, boolean explicitDelay, String status,
                ScopedRuntimeVariables.ScopeSnapshot variableSnapshot, int stepRetryUsed,
                boolean noStopNavigationOnCompletion, boolean backgroundRunner, String executionSessionId) {
            this.sequenceName = sequenceName;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.atTarget = atTarget;
            this.remainingLoops = remainingLoops;
            this.tickDelay = tickDelay;
            this.explicitDelay = explicitDelay;
            this.status = status;
            this.variableSnapshot = variableSnapshot;
            this.stepRetryUsed = stepRetryUsed;
            this.noStopNavigationOnCompletion = noStopNavigationOnCompletion;
            this.backgroundRunner = backgroundRunner;
            this.executionSessionId = executionSessionId == null ? "" : executionSessionId;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public int getActionIndex() {
            return actionIndex;
        }

        public boolean isAtTarget() {
            return atTarget;
        }

        public int getRemainingLoops() {
            return remainingLoops;
        }

        public int getTickDelay() {
            return tickDelay;
        }

        public boolean isExplicitDelay() {
            return explicitDelay;
        }

        public String getStatus() {
            return status;
        }

        public ScopedRuntimeVariables.ScopeSnapshot getVariableSnapshot() {
            return variableSnapshot;
        }

        public int getStepRetryUsed() {
            return stepRetryUsed;
        }

        public boolean isNoStopNavigationOnCompletion() {
            return noStopNavigationOnCompletion;
        }

        public boolean isBackgroundRunner() {
            return backgroundRunner;
        }

        public String getExecutionSessionId() {
            return executionSessionId;
        }
    }

    public static class DebugSnapshot {
        private final boolean tracking;
        private final boolean paused;
        private final boolean pausedForDebug;
        private final String sequenceName;
        private final int stepIndex;
        private final int actionIndex;
        private final String currentActionDescription;
        private final String status;
        private final List<String> traceLines;
        private final Map<String, String> variablePreview;
        private final int breakpointCount;

        public DebugSnapshot(boolean tracking, boolean paused, boolean pausedForDebug, String sequenceName, int stepIndex, int actionIndex,
                String currentActionDescription, String status, List<String> traceLines,
                Map<String, String> variablePreview, int breakpointCount) {
            this.tracking = tracking;
            this.paused = paused;
            this.pausedForDebug = pausedForDebug;
            this.sequenceName = sequenceName == null ? "" : sequenceName;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.currentActionDescription = currentActionDescription == null ? "" : currentActionDescription;
            this.status = status == null ? "" : status;
            this.traceLines = traceLines == null ? Collections.emptyList() : new ArrayList<>(traceLines);
            this.variablePreview = variablePreview == null ? Collections.emptyMap() : new LinkedHashMap<>(variablePreview);
            this.breakpointCount = breakpointCount;
        }

        public boolean isTracking() {
            return tracking;
        }

        public boolean isPaused() {
            return paused;
        }

        public boolean isPausedForDebug() {
            return pausedForDebug;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public int getActionIndex() {
            return actionIndex;
        }

        public String getCurrentActionDescription() {
            return currentActionDescription;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getTraceLines() {
            return new ArrayList<>(traceLines);
        }

        public Map<String, String> getVariablePreview() {
            return new LinkedHashMap<>(variablePreview);
        }

        public int getBreakpointCount() {
            return breakpointCount;
        }
    }

    public PathSequence currentSequence;
    private int currentStepIndex = 0;
    private int actionIndex = 0;
    private int initialActionIndexForCurrentStep;
    private boolean tracking = false;
    private int tickDelay = 0;
    private boolean atTarget = false;
    private boolean waitingForNavigationToFinishAtTarget = false;
    private int remainingLoops = 0;
    private String status = "";
    private volatile boolean isPaused = false;
    private boolean pausedForDebug = false;
    private boolean debugStepArmed = false;
    private boolean stopAfterCurrentStep = false;
    private String debugIgnoreBreakpointKey = "";
    private boolean isPerformingExplicitDelay = false;
    private boolean explicitDelayNormalizeTo20Tps = false;
    private double explicitDelayRemainingBaselineTicks = 0.0D;
    private boolean pausedByGui = false;
    private boolean pendingLoopRestart = false;
    private boolean eventBusRegistered = false;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final boolean backgroundRunner;
    private final String lockOwnerId = UUID.randomUUID().toString();
    private String waitingLockResource = "";
    private int currentStepRetryUsed = 0;
    private boolean noStopNavigationOnCompletion = false;
    private int currentStepIdleTicks = 0;
    private boolean currentStepIdleAnnounced = false;
    private double currentStepLastMovementX = Double.NaN;
    private double currentStepLastMovementY = Double.NaN;
    private double currentStepLastMovementZ = Double.NaN;
    private final Map<String, Object> initialSequenceVariables = new LinkedHashMap<>();

    public static int loopDelayTicks = 20;

    // --- 新增：狩猎状态变量 ---
    private boolean isHunting = false;
    private double huntRadius;
    private boolean huntAutoAttack;
    private String huntAttackMode = KillAuraHandler.ATTACK_MODE_NORMAL;
    private String huntAttackSequenceName = "";
    private boolean huntAimLockEnabled = true;
    private double huntTrackingDistanceSq; // 使用距离的平方以提高性能
    private double huntUpRange = DEFAULT_CENTER_HUNT_VERTICAL_RANGE;
    private double huntDownRange = DEFAULT_CENTER_HUNT_VERTICAL_RANGE;
    private Entity huntTargetEntity = null;
    private long lastHuntGotoTargetEntityId = -1L;
    private boolean huntMovementStopped = false;
    private String huntMode = KillAuraHandler.HUNT_MODE_FIXED_DISTANCE;
    private boolean huntOrbitEnabled = false;
    private boolean huntChaseIntervalEnabled = false;
    private int huntChaseIntervalTicks = 0;
    private int huntChaseCooldownTicks = 0;
    private boolean huntWasWithinDesiredDistance = false;
    private int huntAttackCooldownTicks = 0;
    private double lastHuntGotoTargetX = Double.NaN;
    private double lastHuntGotoTargetY = Double.NaN;
    private double lastHuntGotoTargetZ = Double.NaN;
    private int huntAttackRemaining = -1;
    private int huntNoTargetSkipCount = 0;
    private int huntEmptyConfirmTicks = 0;
    private int huntNoDamageAttackLimit = KillAuraHandler.DEFAULT_NO_DAMAGE_ATTACK_LIMIT;
    private boolean huntRestrictTargetGroups = true;
    private boolean huntTargetHostile = true;
    private boolean huntTargetPassive = false;
    private boolean huntTargetPlayers = false;
    private boolean huntEnableNameWhitelist = false;
    private boolean huntWaitForWhitelistRespawn = false;
    private boolean huntWaitForWhitelistRespawnAfterCompletion = false;
    private boolean huntConfirmKillCompletion = false;
    private int huntKillCompletionConfirmTicks = 3 * 20;
    private int huntWhitelistRespawnTimeoutTicks = 120 * 20;
    private int huntWhitelistRespawnElapsedTicks = 0;
    private boolean huntWhitelistRespawnWaiting = false;
    private boolean huntWhitelistRespawnCompletesAction = false;
    private boolean huntWhitelistRespawnWaitSatisfied = false;
    private boolean huntWhitelistTargetKilledAfterWait = false;
    private boolean huntHasSeenWhitelistTarget = false;
    private boolean huntKillCompletionConfirming = false;
    private boolean huntKillCompletionConfirmationResumed = false;
    private int huntKillCompletionConfirmationElapsedTicks = 0;
    private boolean huntForceEndEnabled = false;
    private int huntForceEndTimeoutTicks = 60 * 20;
    private int huntForceEndElapsedTicks = 0;
    private boolean huntEnableNameBlacklist = false;
    private boolean huntAreaSweepEnabled = true;
    private int huntAreaSweepCellSize = 8;
    private final List<String> huntNameWhitelist = new ArrayList<>();
    private final List<HuntWhitelistTarget> huntWhitelistTargets = new ArrayList<>();
    private final Map<String, Integer> huntWhitelistKillProgress = new LinkedHashMap<>();
    private final Set<Integer> countedHuntKillEntityIds = new LinkedHashSet<>();
    private final Map<Integer, HuntNoDamageAttackTracker> huntNoDamageAttackTrackers = new LinkedHashMap<>();
    private final Set<Integer> huntNoDamageExcludedEntityIds = new LinkedHashSet<>();
    private final List<String> huntNameBlacklist = new ArrayList<>();
    private boolean huntShowRange = false; // 是否显示搜怪范围
    private boolean huntIgnoreInvisible = false; // 是否忽略隐身目标
    private double huntCenterX = 0.0D; // 搜怪中心点X
    private double huntCenterY = 0.0D; // 搜怪中心点Y
    private double huntCenterZ = 0.0D; // 搜怪中心点Z
    private int huntOrbitLoopNodeIndex = -1;
    private int huntLastOrbitGotoTick = -99999;
    private int huntOrbitStuckTicks = 0;
    private double huntLastOrbitPlayerX = Double.NaN;
    private double huntLastOrbitPlayerZ = Double.NaN;
    private boolean huntPendingCompleteAfterSequence = false;
    private final HuntAttackSequenceExecutor huntAttackSequenceExecutor = new HuntAttackSequenceExecutor();
    private final HuntOrbitController huntOrbitController = new HuntOrbitController();
    private static final double HUNT_FIXED_DISTANCE_TOLERANCE = 0.30D;
    private static final double HUNT_CONTINUOUS_ORBIT_ENTRY_BUFFER = 1.25D;
    private static final double HUNT_CONTINUOUS_ORBIT_EXIT_BUFFER = 2.25D;
    private static final double HUNT_CONTINUOUS_ORBIT_MAX_VERTICAL_DELTA = 3.5D;
    private static final float HUNT_NO_DAMAGE_HEALTH_EPSILON = 0.001F;
    private static final int HUNT_NO_DAMAGE_OBSERVATION_DELAY_TICKS = 1;
    private static final int HUNT_NO_DAMAGE_MAX_TRACKED_TARGETS = 256;
    private static final int HUNT_NO_DAMAGE_MAX_EXCLUDED_TARGETS = 512;
    private static final double DEFAULT_CENTER_HUNT_VERTICAL_RANGE = 3.0D;
    private static final int HUNT_AREA_SWEEP_MIN_CELL_SIZE = 8;
    private static final int HUNT_AREA_SWEEP_MAX_CELL_SIZE = 32;
    private static final int HUNT_AREA_SWEEP_EMPTY_CONFIRM_TICKS = 40;
    private static final int HUNT_KILL_COMPLETION_CONFIRM_DEFAULT_SECONDS = 3;
    private static final int HUNT_AREA_SWEEP_EXACT_ROUTE_CELL_LIMIT = 12;
    private static final int HUNT_AREA_SWEEP_ROUTE_OPTIMIZATION_CELL_LIMIT = 64;
    private static final int HUNT_AREA_SWEEP_LARGE_ROUTE_CELL_LIMIT = 128;
    private static final int HUNT_AREA_SWEEP_MAX_TWO_OPT_PASSES = 6;
    private static final int HUNT_AREA_SWEEP_HEURISTIC_SEED_COUNT = 8;
    private static final int HUNT_AREA_SWEEP_ROUTE_REPLAN_MIN_INTERVAL_TICKS = 4;
    private final HuntAreaSweepPlanner huntAreaSweepPlanner = new HuntAreaSweepPlanner();
    // --- 新增结束 ---

    private static final class HuntWhitelistTarget {
        private final String name;
        private final int killCount;

        private HuntWhitelistTarget(String name, int killCount) {
            this.name = name == null ? "" : name.trim();
            this.killCount = Math.max(0, killCount);
        }

        private boolean hasKillLimit() {
            return this.killCount > 0;
        }
    }

    private static final class HuntNoDamageAttackTracker {
        private float baselineHealth;
        private int pendingAttempts;
        private int observationTicks;
        private int confirmedNoDamageAttempts;

        private HuntNoDamageAttackTracker(float baselineHealth) {
            this.baselineHealth = baselineHealth;
        }
    }

    private static final class HuntSweepCell {
        private final int x;
        private final int z;

        private HuntSweepCell(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof HuntSweepCell)) {
                return false;
            }
            HuntSweepCell cell = (HuntSweepCell) other;
            return x == cell.x && z == cell.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }

    /**
     * Plans one open route across every currently eligible cell. Small cell
     * sets use an exact shortest route; larger sets use a bounded 2-opt route
     * and are replanned only when cells change, never once per combat tick.
     */
    private static final class HuntAreaSweepPlanner implements KillAuraHandler.AreaHuntTargetPlanner {
        private final Set<HuntSweepCell> plannedCells = new LinkedHashSet<>();
        private final List<HuntSweepCell> plannedRoute = new ArrayList<>();
        private HuntSweepCell currentCell;
        private int cellSize = 8;
        private int lastRoutePlanTick = Integer.MIN_VALUE;

        private void reset(int requestedCellSize) {
            cellSize = Math.max(HUNT_AREA_SWEEP_MIN_CELL_SIZE,
                    Math.min(HUNT_AREA_SWEEP_MAX_CELL_SIZE, requestedCellSize));
            plannedCells.clear();
            plannedRoute.clear();
            currentCell = null;
            lastRoutePlanTick = Integer.MIN_VALUE;
        }

        @Override
        public void prioritize(EntityPlayerSP player, List<EntityLivingBase> targets) {
            if (player == null || targets == null || targets.isEmpty()) {
                return;
            }

            Map<HuntSweepCell, List<EntityLivingBase>> byCell = new LinkedHashMap<>();
            for (EntityLivingBase target : targets) {
                if (target == null || target.isDead) {
                    continue;
                }
                HuntSweepCell cell = getCell(target);
                List<EntityLivingBase> cellTargets = byCell.get(cell);
                if (cellTargets == null) {
                    cellTargets = new ArrayList<>();
                    byCell.put(cell, cellTargets);
                }
                cellTargets.add(target);
            }
            if (byCell.isEmpty()) {
                return;
            }

            Set<HuntSweepCell> activeCells = new LinkedHashSet<>(byCell.keySet());
            if (!activeCells.contains(currentCell)) {
                currentCell = null;
            }

            boolean topologyChanged = !plannedCells.equals(activeCells);
            boolean mustRebuild = currentCell == null || plannedRoute.isEmpty();
            if (mustRebuild || (topologyChanged
                    && player.ticksExisted - lastRoutePlanTick >= HUNT_AREA_SWEEP_ROUTE_REPLAN_MIN_INTERVAL_TICKS)) {
                rebuildRoute(player, activeCells, currentCell);
                lastRoutePlanTick = player.ticksExisted;
            }

            HuntSweepCell selected = currentCell;
            if (selected == null) {
                selected = getNextPlannedCell(activeCells);
            }
            if (selected == null) {
                return;
            }
            currentCell = selected;

            List<EntityLivingBase> ordered = new ArrayList<>(targets.size());
            List<EntityLivingBase> selectedTargets = byCell.get(selected);
            Set<Integer> selectedEntityIds = new LinkedHashSet<>();
            if (selectedTargets != null) {
                ordered.addAll(selectedTargets);
                for (EntityLivingBase target : selectedTargets) {
                    selectedEntityIds.add(target.getEntityId());
                }
            }
            for (EntityLivingBase target : targets) {
                if (!selectedEntityIds.contains(target.getEntityId())) {
                    ordered.add(target);
                }
            }
            targets.clear();
            targets.addAll(ordered);
        }

        private void rebuildRoute(EntityPlayerSP player, Set<HuntSweepCell> activeCells,
                HuntSweepCell forcedFirstCell) {
            plannedCells.clear();
            plannedCells.addAll(activeCells);
            plannedRoute.clear();

            List<HuntSweepCell> remaining = new ArrayList<>(activeCells);
            double startX = player.posX;
            double startZ = player.posZ;
            if (forcedFirstCell != null && remaining.remove(forcedFirstCell)) {
                plannedRoute.add(forcedFirstCell);
                startX = getCellCenterX(forcedFirstCell);
                startZ = getCellCenterZ(forcedFirstCell);
            }
            if (remaining.isEmpty()) {
                return;
            }
            if (remaining.size() <= HUNT_AREA_SWEEP_EXACT_ROUTE_CELL_LIMIT) {
                plannedRoute.addAll(buildExactOpenRoute(remaining, startX, startZ));
            } else {
                plannedRoute.addAll(buildBestHeuristicOpenRoute(remaining, startX, startZ));
            }
        }

        private HuntSweepCell getNextPlannedCell(Set<HuntSweepCell> activeCells) {
            for (HuntSweepCell cell : plannedRoute) {
                if (activeCells.contains(cell)) {
                    return cell;
                }
            }
            return null;
        }

        private List<HuntSweepCell> buildExactOpenRoute(List<HuntSweepCell> cells, double startX, double startZ) {
            int count = cells.size();
            int stateCount = 1 << count;
            double[][] costs = new double[stateCount][count];
            int[][] previous = new int[stateCount][count];
            for (int mask = 0; mask < stateCount; mask++) {
                for (int index = 0; index < count; index++) {
                    costs[mask][index] = Double.MAX_VALUE;
                    previous[mask][index] = -1;
                }
            }

            for (int index = 0; index < count; index++) {
                costs[1 << index][index] = distanceFromPoint(startX, startZ, cells.get(index));
            }

            for (int mask = 1; mask < stateCount; mask++) {
                for (int last = 0; last < count; last++) {
                    double currentCost = costs[mask][last];
                    if (currentCost == Double.MAX_VALUE || (mask & (1 << last)) == 0) {
                        continue;
                    }
                    for (int next = 0; next < count; next++) {
                        if ((mask & (1 << next)) != 0) {
                            continue;
                        }
                        int nextMask = mask | (1 << next);
                        double candidateCost = currentCost + distanceBetweenCells(cells.get(last), cells.get(next));
                        if (candidateCost + 1.0E-6D < costs[nextMask][next]
                                || (Math.abs(candidateCost - costs[nextMask][next]) <= 1.0E-6D
                                        && last < previous[nextMask][next])) {
                            costs[nextMask][next] = candidateCost;
                            previous[nextMask][next] = last;
                        }
                    }
                }
            }

            int fullMask = stateCount - 1;
            int bestLast = -1;
            double bestCost = Double.MAX_VALUE;
            for (int index = 0; index < count; index++) {
                double cost = costs[fullMask][index];
                if (cost + 1.0E-6D < bestCost
                        || (Math.abs(cost - bestCost) <= 1.0E-6D && index < bestLast)) {
                    bestCost = cost;
                    bestLast = index;
                }
            }

            List<HuntSweepCell> reversed = new ArrayList<>(count);
            int mask = fullMask;
            int cursor = bestLast;
            while (cursor >= 0) {
                reversed.add(cells.get(cursor));
                int previousCursor = previous[mask][cursor];
                mask &= ~(1 << cursor);
                cursor = previousCursor;
            }
            Collections.reverse(reversed);
            return reversed;
        }

        private List<HuntSweepCell> buildBestHeuristicOpenRoute(List<HuntSweepCell> cells,
                double startX, double startZ) {
            if (cells.size() > HUNT_AREA_SWEEP_LARGE_ROUTE_CELL_LIMIT) {
                return buildLargeAreaSweepRoute(cells, startX, startZ);
            }
            List<HuntSweepCell> seedCells = new ArrayList<>(cells);
            seedCells.sort((left, right) -> {
                int distanceCompare = Double.compare(distanceFromPoint(startX, startZ, left),
                        distanceFromPoint(startX, startZ, right));
                return distanceCompare != 0 ? distanceCompare : compareCells(left, right);
            });

            int seedCount = Math.min(HUNT_AREA_SWEEP_HEURISTIC_SEED_COUNT, seedCells.size());
            List<HuntSweepCell> bestRoute = null;
            double bestCost = Double.MAX_VALUE;
            for (int seed = 0; seed < seedCount; seed++) {
                int index = seedCount == 1 ? 0 : (int) Math.round((double) seed * (seedCells.size() - 1)
                        / (seedCount - 1));
                List<HuntSweepCell> route = buildNearestNeighborRoute(cells, startX, startZ, seedCells.get(index));
                improveOpenRouteWithTwoOpt(route, startX, startZ);
                double routeCost = getOpenRouteCost(route, startX, startZ);
                if (bestRoute == null || routeCost + 1.0E-6D < bestCost) {
                    bestRoute = route;
                    bestCost = routeCost;
                }
            }
            return bestRoute == null ? buildNearestNeighborRoute(cells, startX, startZ, null) : bestRoute;
        }

        private List<HuntSweepCell> buildLargeAreaSweepRoute(List<HuntSweepCell> cells,
                double startX, double startZ) {
            List<HuntSweepCell> xSweep = buildSerpentineRoute(cells, true);
            List<HuntSweepCell> zSweep = buildSerpentineRoute(cells, false);
            orientRouteFromStart(xSweep, startX, startZ);
            orientRouteFromStart(zSweep, startX, startZ);
            return getOpenRouteCost(xSweep, startX, startZ) <= getOpenRouteCost(zSweep, startX, startZ)
                    ? xSweep
                    : zSweep;
        }

        private List<HuntSweepCell> buildSerpentineRoute(List<HuntSweepCell> cells, boolean sweepByX) {
            List<HuntSweepCell> route = new ArrayList<>(cells);
            route.sort((left, right) -> {
                int leftMajor = sweepByX ? left.x : left.z;
                int rightMajor = sweepByX ? right.x : right.z;
                int majorCompare = Integer.compare(leftMajor, rightMajor);
                if (majorCompare != 0) {
                    return majorCompare;
                }
                int leftMinor = sweepByX ? left.z : left.x;
                int rightMinor = sweepByX ? right.z : right.x;
                boolean reverseMinor = Math.floorMod(leftMajor, 2) != 0;
                return reverseMinor
                        ? Integer.compare(rightMinor, leftMinor)
                        : Integer.compare(leftMinor, rightMinor);
            });
            return route;
        }

        private void orientRouteFromStart(List<HuntSweepCell> route, double startX, double startZ) {
            if (route.size() > 1
                    && distanceFromPoint(startX, startZ, route.get(route.size() - 1))
                            + 1.0E-6D < distanceFromPoint(startX, startZ, route.get(0))) {
                Collections.reverse(route);
            }
        }

        private List<HuntSweepCell> buildNearestNeighborRoute(List<HuntSweepCell> cells,
                double startX, double startZ, HuntSweepCell firstCell) {
            List<HuntSweepCell> remaining = new ArrayList<>(cells);
            List<HuntSweepCell> route = new ArrayList<>(cells.size());
            double currentX = startX;
            double currentZ = startZ;
            if (firstCell != null && remaining.remove(firstCell)) {
                route.add(firstCell);
                currentX = getCellCenterX(firstCell);
                currentZ = getCellCenterZ(firstCell);
            }
            while (!remaining.isEmpty()) {
                HuntSweepCell best = null;
                double bestDistance = Double.MAX_VALUE;
                for (HuntSweepCell candidate : remaining) {
                    double distance = distanceFromPoint(currentX, currentZ, candidate);
                    if (best == null || distance + 1.0E-6D < bestDistance
                            || (Math.abs(distance - bestDistance) <= 1.0E-6D
                                    && compareCells(candidate, best) < 0)) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
                route.add(best);
                remaining.remove(best);
                currentX = getCellCenterX(best);
                currentZ = getCellCenterZ(best);
            }
            return route;
        }

        private double getOpenRouteCost(List<HuntSweepCell> route, double startX, double startZ) {
            if (route == null || route.isEmpty()) {
                return 0.0D;
            }
            double cost = distanceFromPoint(startX, startZ, route.get(0));
            for (int index = 1; index < route.size(); index++) {
                cost += distanceBetweenCells(route.get(index - 1), route.get(index));
            }
            return cost;
        }

        private void improveOpenRouteWithTwoOpt(List<HuntSweepCell> route, double startX, double startZ) {
            int optimizeLength = Math.min(route.size(), HUNT_AREA_SWEEP_ROUTE_OPTIMIZATION_CELL_LIMIT);
            if (optimizeLength < 3) {
                return;
            }
            for (int pass = 0; pass < HUNT_AREA_SWEEP_MAX_TWO_OPT_PASSES; pass++) {
                boolean improved = false;
                for (int start = 0; start < optimizeLength - 1 && !improved; start++) {
                    for (int end = start + 1; end < optimizeLength; end++) {
                        double currentCost = getRouteEdgeCost(route, start, end, startX, startZ, false);
                        double swappedCost = getRouteEdgeCost(route, start, end, startX, startZ, true);
                        if (swappedCost + 1.0E-6D < currentCost) {
                            reverseRouteSegment(route, start, end);
                            improved = true;
                            break;
                        }
                    }
                }
                if (!improved) {
                    return;
                }
            }
        }

        private double getRouteEdgeCost(List<HuntSweepCell> route, int start, int end,
                double startX, double startZ, boolean swapped) {
            HuntSweepCell before = start == 0 ? null : route.get(start - 1);
            HuntSweepCell first = route.get(start);
            HuntSweepCell last = route.get(end);
            HuntSweepCell after = end + 1 < route.size() ? route.get(end + 1) : null;
            double firstLeg = before == null
                    ? distanceFromPoint(startX, startZ, swapped ? last : first)
                    : distanceBetweenCells(before, swapped ? last : first);
            if (after == null) {
                return firstLeg;
            }
            return firstLeg + distanceBetweenCells(swapped ? first : last, after);
        }

        private void reverseRouteSegment(List<HuntSweepCell> route, int start, int end) {
            while (start < end) {
                HuntSweepCell swap = route.get(start);
                route.set(start, route.get(end));
                route.set(end, swap);
                start++;
                end--;
            }
        }

        private double distanceFromPoint(double x, double z, HuntSweepCell cell) {
            double dx = getCellCenterX(cell) - x;
            double dz = getCellCenterZ(cell) - z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        private double distanceBetweenCells(HuntSweepCell left, HuntSweepCell right) {
            return distanceFromPoint(getCellCenterX(left), getCellCenterZ(left), right);
        }

        private double getCellCenterX(HuntSweepCell cell) {
            return (cell.x + 0.5D) * cellSize;
        }

        private double getCellCenterZ(HuntSweepCell cell) {
            return (cell.z + 0.5D) * cellSize;
        }

        private HuntSweepCell getCell(EntityLivingBase target) {
            return new HuntSweepCell(Math.floorDiv(MathHelper.floor(target.posX), cellSize),
                    Math.floorDiv(MathHelper.floor(target.posZ), cellSize));
        }

        private int compareCells(HuntSweepCell left, HuntSweepCell right) {
            int xCompare = Integer.compare(left.x, right.x);
            return xCompare != 0 ? xCompare : Integer.compare(left.z, right.z);
        }
    }

    // --- 新增：跟随实体状态变量 ---
    private boolean isFollowingEntity = false;
    private String followEntityType = "player"; // player, hostile, passive, all
    private String followTargetName = ""; // 目标名称（支持部分匹配）
    private double followSearchRadius = 16.0D;
    private double followDistance = 3.0D;
    private int followTimeoutSeconds = 0; // 0表示无限跟随
    private boolean followStopOnLost = true;
    private Entity followTargetEntity = null;
    private long followStartTime = 0L;
    // --- 新增结束 ---

    // --- 使用快捷栏物品动作状态 ---
    private boolean hotbarUseActionRunning = false;
    private String hotbarUseItemName = "";
    private AutoUseItemRule.MatchMode hotbarUseMatchMode = AutoUseItemRule.MatchMode.CONTAINS;
    private AutoUseItemRule.UseMode hotbarUseMode = AutoUseItemRule.UseMode.RIGHT_CLICK;
    private boolean hotbarUseChangeLocalSlot = false;
    private int hotbarUseSwitchItemDelayTicks = 0;
    private int hotbarUseSwitchDelayTicks = 0;
    private int hotbarUseSwitchBackDelayTicks = 0;
    private int hotbarUseRemainingCount = 0;
    private int hotbarUseIntervalTicks = 0;
    private int hotbarUseWaitTicks = 0;
    // --- 新增结束 ---

    // --- 异步动作状态 ---
    private String pendingAsyncActionType = "";
    private int pendingAsyncActionStepIndex = -1;
    private int pendingAsyncActionIndex = -1;
    // --- 新增结束 ---

    // --- 等待条件动作状态 ---
    private boolean waitConditionRunning = false;
    private int waitConditionElapsedTicks = 0;
    private long waitConditionStartCapturedUpdateVersion = 0L;
    private long waitConditionStartCapturedRecaptureVersion = 0L;
    private long waitConditionStartPacketTextVersion = 0L;
    private long waitConditionStartPacketFieldTimestamp = 0L;
    private ActionData deferredWaitActionData = null;
    private int deferredWaitResumeActionIndex = -1;
    private int deferredWaitElapsedTicks = 0;
    private long deferredWaitStartCapturedUpdateVersion = 0L;
    private long deferredWaitStartCapturedRecaptureVersion = 0L;
    private long deferredWaitStartPacketTextVersion = 0L;
    private long deferredWaitStartPacketFieldTimestamp = 0L;
    // --- 新增结束 ---

    // --- 旧动作系统运行时变量/控制流状态 ---
    private final ScopedRuntimeVariables runtimeVariables = new ScopedRuntimeVariables();
    private boolean repeatActionRunning = false;
    private int repeatActionStepIndex = -1;
    private int repeatActionHeaderIndex = -1;
    private int repeatActionBodyStartIndex = -1;
    private int repeatActionBodyEndIndex = -1;
    private int repeatActionRemainingLoops = 0;
    private int repeatActionIteration = 0;
    private String repeatActionLoopVarName = "loop_index";
    private boolean branchBlockRunning = false;
    private int branchBlockStepIndex = -1;
    private int branchBlockSelectedEndIndex = -1;
    private int branchBlockFinalEndIndex = -1;
    private boolean ifElseRunning = false;
    private int ifElseStepIndex = -1;
    private int ifElseThenEndIndex = -1;
    private int ifElseElseEndIndex = -1;
    private boolean whileConditionRunning = false;
    private int whileConditionStepIndex = -1;
    private int whileConditionHeaderIndex = -1;
    private int whileConditionBodyStartIndex = -1;
    private int whileConditionBodyEndIndex = -1;
    private int whileConditionIteration = 0;
    private int whileConditionMaxLoops = 0;
    private String whileConditionLoopVarName = "while_index";
    private boolean forEachListRunning = false;
    private int forEachListStepIndex = -1;
    private int forEachListBodyStartIndex = -1;
    private int forEachListBodyEndIndex = -1;
    private int forEachListIteration = 0;
    private List<Object> forEachListItems = new ArrayList<>();
    private String forEachListItemVarName = "item";
    private String forEachListIndexVarName = "item_index";
    private boolean forEachPointRunning = false;
    private int forEachPointStepIndex = -1;
    private int forEachPointBodyStartIndex = -1;
    private int forEachPointBodyEndIndex = -1;
    private int forEachPointIteration = 0;
    private List<BlockPos> forEachPoints = new ArrayList<>();
    private String forEachPointVarName = "point";
    private String forEachPointIndexVarName = "point_index";
    private boolean retryBlockRunning = false;
    private int retryBlockStepIndex = -1;
    private int retryBlockHeaderIndex = -1;
    private int retryBlockBodyStartIndex = -1;
    private int retryBlockBodyEndIndex = -1;
    private int retryBlockRetryRemaining = 0;
    private int retryBlockAttempt = 1;
    private int retryBlockDelayTicks = 0;
    private JsonObject retryBlockParams = null;
    private String retryBlockAttemptVarName = "retry_block";
    private String retryBlockDescription = "";
    private final Deque<String> debugTraceLines = new ArrayDeque<>();
    private String currentDebugActionDescription = "";
    private String executionLogSessionId = "";
    private boolean executionResultSuccess = false;
    private String executionResultReason = "";
    // --- 新增结束 ---

    private static final class BranchBlockCase {
        private final String key;
        private final int bodyCount;

        private BranchBlockCase(String key, int bodyCount) {
            this.key = key == null ? "" : key.trim();
            this.bodyCount = Math.max(0, bodyCount);
        }
    }

    private PathSequenceEventListener() {
        this(false);
    }

    private PathSequenceEventListener(boolean backgroundRunner) {
        this.backgroundRunner = backgroundRunner;
        // 运行时配置改为首次使用时再加载，避免静态字段初始化顺序导致的 NPE。
    }

    private void ensureEventBusRegistered() {
        if (eventBusRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        eventBusRegistered = true;
    }

    private void ensureEventBusUnregistered() {
        if (!eventBusRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        eventBusRegistered = false;
    }

    static PathSequenceEventListener getCurrentExecutionContext() {
        return ACTION_EXECUTION_CONTEXT.get();
    }

    /**
     * Returns the runner currently executing an action. Action factories live
     * in PathSequenceManager, so they must not blindly address the foreground
     * singleton when the action came from a background sequence.
     */
    static PathSequenceEventListener getActionExecutionTarget() {
        PathSequenceEventListener context = ACTION_EXECUTION_CONTEXT.get();
        return context == null ? instance : context;
    }

    boolean isBackgroundRunner() {
        return backgroundRunner;
    }

    private boolean ensureResources(EnumSet<ResourceLockManager.Resource> resources, String detail) {
        String sequenceName = currentSequence == null ? "" : currentSequence.getName();
        String blocked = ResourceLockManager.acquireOrSync(lockOwnerId, sequenceName, backgroundRunner, resources, detail);
        String policy = currentSequence == null ? "WAIT" : currentSequence.getLockConflictPolicy();
        if (blocked != null && !blocked.isEmpty()
                && "PREEMPT_BACKGROUND".equalsIgnoreCase(policy)
                && !backgroundRunner) {
            ResourceLockManager.Resource blockedResource = ResourceLockManager.parseResource(blocked);
            if (ResourceLockManager.isHeldByBackground(blockedResource)) {
                stopAllBackgroundRunners();
                blocked = ResourceLockManager.acquireOrSync(lockOwnerId, sequenceName, backgroundRunner, resources, detail);
            }
        }
        if (blocked != null && !blocked.isEmpty()) {
            if ("FAIL".equalsIgnoreCase(policy)) {
                waitingLockResource = blocked;
                status = sequenceName + " | 锁冲突失败:" + blocked;
                recordDebugTrace("锁冲突失败: " + blocked + " / " + detail);
                markExecutionResult(false, "锁冲突失败: " + blocked);
                stopTracking();
                return false;
            }
            waitingLockResource = blocked;
            status = sequenceName + " | 等待锁:" + blocked;
            tickDelay = 2;
            return false;
        }
        if (!waitingLockResource.isEmpty() && status != null && status.contains("等待锁:")) {
            status = sequenceName + (backgroundRunner ? " | 后台执行" : "");
        }
        waitingLockResource = "";
        return true;
    }

    private void releaseResources() {
        waitingLockResource = "";
        ResourceLockManager.releaseAll(lockOwnerId);
    }

    private boolean hasActiveExecution() {
        return tracking || pendingLoopRestart;
    }

    private boolean startNavigationGoto(double x, double y, double z, boolean bypassGotoThrottle) {
        navigationOwner = this;
        return EmbeddedNavigationHandler.INSTANCE.startGoto(x, y, z, bypassGotoThrottle);
    }

    private boolean startNavigationGoto(double x, double y, double z) {
        return startNavigationGoto(x, y, z, false);
    }

    private boolean ownsNavigation() {
        return navigationOwner == this;
    }

    private static boolean stopNavigationOwnedByBackgroundRunner() {
        PathSequenceEventListener owner = navigationOwner;
        if (owner == null || !owner.backgroundRunner) {
            return false;
        }
        navigationOwner = null;
        return EmbeddedNavigationHandler.INSTANCE.stop();
    }

    private boolean stopOwnedNavigation() {
        if (!ownsNavigation()) {
            return false;
        }
        navigationOwner = null;
        return EmbeddedNavigationHandler.INSTANCE.stop();
    }

    private boolean stopNavigation() {
        if (ownsNavigation()) {
            navigationOwner = null;
        }
        return EmbeddedNavigationHandler.INSTANCE.stop();
    }

    private boolean ownsResource(ResourceLockManager.Resource resource) {
        if (resource == null) {
            return false;
        }
        for (ResourceLockManager.LockSnapshot snapshot : ResourceLockManager.getSnapshots()) {
            if (snapshot != null && resource == snapshot.getResource()
                    && lockOwnerId.equals(snapshot.getOwnerId())) {
                return true;
            }
        }
        return false;
    }

    private EnumSet<ResourceLockManager.Resource> resolveMovementResources(double[] target) {
        EnumSet<ResourceLockManager.Resource> resources = EnumSet.noneOf(ResourceLockManager.Resource.class);
        if (target != null && target.length >= 3 && !Double.isNaN(target[0])) {
            resources.add(ResourceLockManager.Resource.MOVE);
        }
        return resources;
    }

    private boolean isPreserveViewEnabled(ActionData actionData) {
        JsonObject params = actionData == null ? null : actionData.params;
        return readBooleanParam(params, "preserveView", readBooleanParam(params, "noMoveView", false));
    }

    private boolean readBooleanParam(JsonObject params, String key, boolean defaultValue) {
        if (params == null || key == null || !params.has(key) || !params.get(key).isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String readStringParam(JsonObject params, String key, String defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int readIntParam(JsonObject params, String key, int defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        JsonElement value = params.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return defaultValue;
        }
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private EnumSet<ResourceLockManager.Resource> resolveActionResources(ActionData actionData) {
        EnumSet<ResourceLockManager.Resource> resources = EnumSet.noneOf(ResourceLockManager.Resource.class);
        String type = actionData == null || actionData.type == null ? "" : actionData.type.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "setview":
                resources.add(ResourceLockManager.Resource.LOOK);
                break;
            case "click":
                resources.add(ResourceLockManager.Resource.INTERACT);
                resources.add(ResourceLockManager.Resource.LOOK);
                break;
            case "paste_text":
                resources.add(ResourceLockManager.Resource.INTERACT);
                break;
            case "rightclickblock":
            case "rightclickentity":
                resources.add(ResourceLockManager.Resource.INTERACT);
                if (!isPreserveViewEnabled(actionData)) {
                    resources.add(ResourceLockManager.Resource.LOOK);
                }
                break;
            case "window_click":
            case "conditional_window_click":
            case "takeallitems":
            case "take_all_items_safe":
            case "dropfiltereditems":
            case "autochestclick":
            case "move_inventory_items_to_chest_slots":
            case "spread_inventory_item":
            case "stack_inventory_item":
            case "warehouse_auto_deposit":
            case "transferitemstowarehouse":
            case "move_inventory_item_to_hotbar":
            case "switch_hotbar_slot":
            case "silentuse":
            case "use_hotbar_item":
            case "use_held_item":
            case "autoeat":
            case "autoequip":
            case "autopickup":
                resources.add(ResourceLockManager.Resource.INVENTORY);
                break;
            case "pickup_nearby_items":
                resources.add(ResourceLockManager.Resource.INVENTORY);
                resources.add(ResourceLockManager.Resource.MOVE);
                break;
            case "send_packet":
                resources.add(ResourceLockManager.Resource.PACKET);
                break;
            case "wait_until_inventory_item":
            case "wait_until_gui_title":
            case "wait_until_player_in_area":
            case "wait_until_entity_nearby":
            case "wait_until_hud_text":
            case "wait_until_expression":
            case "wait_until_captured_id":
            case "wait_until_packet_text":
            case "wait_until_screen_region":
            case "wait_combined":
                resources.add(ResourceLockManager.Resource.WAIT);
                break;
            case "hunt":
            case "follow_entity":
            case "toggle_kill_aura":
                resources.add(ResourceLockManager.Resource.COMBAT);
                resources.add(ResourceLockManager.Resource.LOOK);
                resources.add(ResourceLockManager.Resource.MOVE);
                break;
            default:
                break;
        }
        return resources;
    }

    public static boolean isSequenceRunningInForeground(String sequenceName) {
        return sequenceName != null
                && instance.isTracking()
                && instance.currentSequence != null
                && sequenceName.equals(instance.currentSequence.getName());
    }

    /** Includes delayed loop restarts, which must retain transient sequence definitions. */
    public static boolean isSequenceActiveForMcp(String sequenceName) {
        if (instance.hasActiveExecution() && instance.currentSequence != null
                && (sequenceName == null || sequenceName.equals(instance.currentSequence.getName()))) return true;
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.hasActiveExecution() && runner.currentSequence != null
                    && (sequenceName == null || sequenceName.equals(runner.currentSequence.getName()))) return true;
        }
        return false;
    }

    public static boolean isSequenceRunningInBackground(String sequenceName) {
        if (sequenceName == null || sequenceName.trim().isEmpty()) {
            return false;
        }
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null
                    && runner.isTracking()
                    && runner.currentSequence != null
                    && sequenceName.equals(runner.currentSequence.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnySequenceRunning() {
        if (instance != null && instance.isTracking()) {
            return true;
        }
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking()) {
                return true;
            }
        }
        return false;
    }

    /** Returns immutable-at-capture progress snapshots for the status HUD. */
    public static List<ProgressSnapshot> getActiveProgressSnapshots() {
        List<ProgressSnapshot> snapshots = new ArrayList<>();
        if (instance != null) {
            ProgressSnapshot foreground = instance.captureProgressSnapshot();
            if (foreground != null) {
                snapshots.add(foreground);
            }
        }
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner == null) {
                continue;
            }
            ProgressSnapshot background = runner.captureProgressSnapshot();
            if (background != null) {
                snapshots.add(background);
            }
        }
        return snapshots;
    }

    /** Returns the execution-log session currently running the named sequence. */
    public static String getActiveExecutionLogSessionId(String sequenceName) {
        String requested = sequenceName == null ? "" : sequenceName.trim();
        if (instance != null && instance.isTracking() && instance.currentSequence != null
                && (requested.isEmpty() || requested.equals(instance.currentSequence.getName()))) {
            return instance.executionLogSessionId == null ? "" : instance.executionLogSessionId;
        }
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking() && runner.currentSequence != null
                    && (requested.isEmpty() || requested.equals(runner.currentSequence.getName()))) {
                return runner.executionLogSessionId == null ? "" : runner.executionLogSessionId;
            }
        }
        return "";
    }

    public static boolean isAnyHuntOrbitActionRunning() {
        if (instance != null && instance.isHuntOrbitActionActive()) {
            return true;
        }
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isHuntOrbitActionActive()) {
                return true;
            }
        }
        return false;
    }

    private boolean isHuntOrbitActionActive() {
        return isHunting
                && isHuntOrbitMode()
                && huntTargetEntity instanceof EntityLivingBase
                && huntTargetEntity.isEntityAlive();
    }

    public static boolean startBackgroundSequence(PathSequence sequence) {
        return startBackgroundSequence(sequence, 0, null);
    }

    public static boolean startBackgroundSequence(PathSequence sequence, int remainingLoops) {
        return startBackgroundSequence(sequence, remainingLoops, null);
    }

    public static boolean startBackgroundSequence(PathSequence sequence, int remainingLoops,
            Map<String, Object> initialSequenceVariables) {
        return startBackgroundSequence(sequence, remainingLoops, initialSequenceVariables, 0);
    }

    public static boolean startBackgroundSequence(PathSequence sequence, int remainingLoops,
            Map<String, Object> initialSequenceVariables, int startStepIndex) {
        return startBackgroundSequence(sequence, remainingLoops, initialSequenceVariables, startStepIndex, 0);
    }

    public static boolean startBackgroundSequence(PathSequence sequence, int remainingLoops,
            Map<String, Object> initialSequenceVariables, int startStepIndex, int startActionIndex) {
        return startBackgroundSequence(sequence, remainingLoops, initialSequenceVariables, startStepIndex,
                startActionIndex, false);
    }

    public static boolean startBackgroundSequence(PathSequence sequence, int remainingLoops,
            Map<String, Object> initialSequenceVariables, int startStepIndex, int startActionIndex,
            boolean stopAfterCurrentStep) {
        if (sequence == null || sequence.getSteps().isEmpty()) {
            return false;
        }

        stopAllBackgroundRunners();

        PathSequenceEventListener runner = new PathSequenceEventListener(true);
        backgroundRunners.add(runner);
        runner.setStatus(sequence.getName() + " | 后台执行");
        runner.startTracking(sequence, remainingLoops, initialSequenceVariables, startStepIndex, startActionIndex,
                stopAfterCurrentStep);
        runner.resume();
        primeBackgroundNavigation(runner, sequence);
        return true;
    }

    public static void stopAllBackgroundRunners() {
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.hasActiveExecution()) {
                runner.markExecutionResult(false, "后台序列被新的后台执行打断");
                runner.stopTracking();
            }
        }
        // A runner between loops has tracking=false but still owns a delayed
        // restart callback. stopTracking clears that callback as well.
        stopNavigationOwnedByBackgroundRunner();
        backgroundRunners.clear();
    }

    public static boolean stopForegroundSequenceByAction() {
        if (instance == null || !instance.hasActiveExecution()) {
            return false;
        }
        instance.markExecutionResult(false, "动作停止前台序列");
        instance.stopTracking();
        return true;
    }

    public static boolean pauseForegroundSequenceByAction() {
        if (instance == null || !instance.isTracking()) {
            return false;
        }
        if (!instance.isPaused) {
            instance.pause();
        }
        return true;
    }

    public static boolean resumeForegroundSequenceByAction() {
        if (instance == null || !instance.isTracking()) {
            return false;
        }
        if (instance.isPaused) {
            instance.resume();
        }
        return true;
    }

    public static boolean stopBackgroundSequencesByAction() {
        boolean stopped = false;
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.hasActiveExecution()) {
                runner.markExecutionResult(false, "动作停止后台序列");
                runner.stopTracking();
                stopped = true;
            }
        }
        // stopTracking removes itself, but clear stale references as well so a
        // stopped background runner cannot be targeted by later control calls.
        stopNavigationOwnedByBackgroundRunner();
        backgroundRunners.removeIf(runner -> runner == null || !runner.hasActiveExecution());
        return stopped;
    }

    public static boolean pauseBackgroundSequencesByAction() {
        boolean handled = false;
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking()) {
                if (!runner.isPaused) {
                    runner.pause();
                }
                handled = true;
            }
        }
        return handled;
    }

    public static boolean resumeBackgroundSequencesByAction() {
        boolean handled = false;
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking()) {
                if (runner.isPaused) {
                    runner.resume();
                }
                handled = true;
            }
        }
        return handled;
    }

    public static boolean hasUnpausedBackgroundSequence() {
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking() && !runner.isPaused()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasActiveBackgroundSequence() {
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking()) {
                return true;
            }
        }
        return false;
    }

    private static boolean restoreAnyBackgroundNavigation() {
        for (PathSequenceEventListener runner : new ArrayList<>(backgroundRunners)) {
            if (runner != null && runner.isTracking() && runner.restoreNavigationStateIfNeeded()) {
                return true;
            }
        }
        return false;
    }

    private boolean restoreNavigationStateIfNeeded() {
        if (!backgroundRunner || !tracking || currentSequence == null) {
            return false;
        }
        if (isHunting && huntTargetEntity != null && huntTargetEntity.isEntityAlive()) {
            return false;
        }
        if (currentStepIndex < 0 || currentStepIndex >= currentSequence.getSteps().size()) {
            return false;
        }
        if (atTarget) {
            return false;
        }
        double[] target = currentSequence.getSteps().get(currentStepIndex).getGotoPoint();
        if (target != null && target.length >= 3 && !Double.isNaN(target[0])) {
            prepareForSequenceStartDispatch("restoreNavigationStateIfNeeded");
            startNavigationGoto(target[0], target[1], target[2], true);
            return true;
        }
        return false;
    }

    private boolean shouldStopNavigationOnFinish() {
        if (backgroundRunner) {
            return !(PathSequenceEventListener.instance.isTracking()
                    && PathSequenceEventListener.instance.currentSequence != null);
        }
        return !restoreAnyBackgroundNavigation();
    }

    private boolean shouldStopNavigationOnSequenceCompletion() {
        return !noStopNavigationOnCompletion && shouldStopNavigationOnFinish();
    }

    private boolean shouldStopNavigationForStepTransition() {
        if (!backgroundRunner) {
            return true;
        }
        return !(PathSequenceEventListener.instance.isTracking()
                && PathSequenceEventListener.instance.currentSequence != null);
    }

    private static void primeBackgroundNavigation(PathSequenceEventListener runner, PathSequence sequence) {
        if (runner == null || sequence == null || sequence.getSteps().isEmpty()) {
            return;
        }
        int firstStepIndex = Math.max(0, Math.min(runner.currentStepIndex, sequence.getSteps().size() - 1));
        double[] firstTarget = sequence.getSteps().get(firstStepIndex).getGotoPoint();
        if (firstTarget != null && firstTarget.length >= 3 && !Double.isNaN(firstTarget[0])) {
            runner.prepareForSequenceStartDispatch("primeBackgroundNavigation");
            runner.startNavigationGoto(firstTarget[0], firstTarget[1], firstTarget[2], true);
        }
    }

    private static synchronized void ensureRuntimeConfigLoaded() {
        if (runtimeConfigLoaded) {
            return;
        }
        runtimeConfigLoaded = true;
        builtinSequenceDelayEnabled = true;
        builtinSequenceDelayTicks = DEFAULT_BUILTIN_SEQUENCE_DELAY_TICKS;
        builtinSequenceDelayTicksSpec = String.valueOf(DEFAULT_BUILTIN_SEQUENCE_DELAY_TICKS);

        if (!Files.exists(RUNTIME_CONFIG_PATH)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(RUNTIME_CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = RUNTIME_CONFIG_GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            if (root.has("builtinSequenceDelayEnabled")) {
                builtinSequenceDelayEnabled = root.get("builtinSequenceDelayEnabled").getAsBoolean();
            }
            if (root.has("builtinSequenceDelayTicks")) {
                setBuiltinSequenceDelayTicksSpecInternal(root.get("builtinSequenceDelayTicks").getAsString());
            }
            if (root.has("builtinSequenceDelayTicksSpec")) {
                setBuiltinSequenceDelayTicksSpecInternal(root.get("builtinSequenceDelayTicksSpec").getAsString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("读取序列运行时配置失败: {}", RUNTIME_CONFIG_PATH, e);
        }
    }

    private static synchronized void saveRuntimeConfig() {
        ensureRuntimeConfigLoaded();
        try {
            Files.createDirectories(RUNTIME_CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("builtinSequenceDelayEnabled", builtinSequenceDelayEnabled);
            root.addProperty("builtinSequenceDelayTicks", builtinSequenceDelayTicksSpec);
            root.addProperty("builtinSequenceDelayTicksValue", Math.max(0, builtinSequenceDelayTicks));
            try (Writer writer = Files.newBufferedWriter(RUNTIME_CONFIG_PATH, StandardCharsets.UTF_8)) {
                RUNTIME_CONFIG_GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("保存序列运行时配置失败: {}", RUNTIME_CONFIG_PATH, e);
        }
    }

    public static synchronized boolean isBuiltinSequenceDelayEnabled() {
        ensureRuntimeConfigLoaded();
        return builtinSequenceDelayEnabled;
    }

    public static synchronized int getBuiltinSequenceDelayTicks() {
        ensureRuntimeConfigLoaded();
        return Math.max(0, builtinSequenceDelayTicks);
    }

    public static synchronized String getBuiltinSequenceDelayTicksSpec() {
        ensureRuntimeConfigLoaded();
        return normalizeBuiltinSequenceDelayTicksSpec(builtinSequenceDelayTicksSpec);
    }

    public static synchronized String getBuiltinSequenceDelayTicksDisplayText() {
        ensureRuntimeConfigLoaded();
        return TickRangeSpec.parse(builtinSequenceDelayTicksSpec, builtinSequenceDelayTicks,
                MIN_BUILTIN_SEQUENCE_DELAY_TICKS, MAX_BUILTIN_SEQUENCE_DELAY_TICKS).toDisplayText();
    }

    public static synchronized String normalizeBuiltinSequenceDelayTicksSpec(String spec) {
        ensureRuntimeConfigLoaded();
        return TickRangeSpec.normalize(spec, builtinSequenceDelayTicks, MIN_BUILTIN_SEQUENCE_DELAY_TICKS,
                MAX_BUILTIN_SEQUENCE_DELAY_TICKS);
    }

    public static synchronized void updateBuiltinSequenceDelayConfig(boolean enabled, int ticks) {
        updateBuiltinSequenceDelayConfig(enabled, String.valueOf(ticks));
    }

    public static synchronized void updateBuiltinSequenceDelayConfig(boolean enabled, String ticksSpec) {
        ensureRuntimeConfigLoaded();
        builtinSequenceDelayEnabled = enabled;
        setBuiltinSequenceDelayTicksSpecInternal(ticksSpec);
        saveRuntimeConfig();
    }

    private static synchronized int getBuiltinSequenceActionDelayTicks() {
        ensureRuntimeConfigLoaded();
        return builtinSequenceDelayEnabled
                ? TickRangeSpec.sample(builtinSequenceDelayTicksSpec, builtinSequenceDelayTicks,
                        MIN_BUILTIN_SEQUENCE_DELAY_TICKS, MAX_BUILTIN_SEQUENCE_DELAY_TICKS)
                : 0;
    }

    private void applyBuiltinSequenceDelay() {
        this.tickDelay = getBuiltinSequenceActionDelayTicks();
    }

    private static void setBuiltinSequenceDelayTicksSpecInternal(String spec) {
        TickRangeSpec.Range range = TickRangeSpec.parse(spec, builtinSequenceDelayTicks,
                MIN_BUILTIN_SEQUENCE_DELAY_TICKS, MAX_BUILTIN_SEQUENCE_DELAY_TICKS);
        builtinSequenceDelayTicks = range.getMin();
        builtinSequenceDelayTicksSpec = range.toSpec();
    }

    public boolean isTracking() {
        return tracking;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isAutoEatStepPathingActive() {
        if (!tracking || isPaused || currentSequence == null || atTarget || waitingForNavigationToFinishAtTarget) {
            return false;
        }
        if (currentStepIndex < 0 || currentStepIndex >= currentSequence.getSteps().size()) {
            return false;
        }
        PathStep step = currentSequence.getSteps().get(currentStepIndex);
        return stepHasGotoTarget(step) && EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating();
    }

    public void setStatus(String s) {
        String next = s == null ? "" : s;
        if (next.equals(status)) {
            return;
        }
        status = next;
        appendExecutionLogEvent("status", "状态更新: " + next);
    }

    public String getStatus() {
        return status;
    }

    public void pauseForDebug() {
        if (!tracking || isPaused) {
            return;
        }
        pauseForDebugInternal("手动调试暂停");
    }

    public void resumeFromDebug() {
        if (!tracking) {
            return;
        }
        if (isPaused) {
            debugIgnoreBreakpointKey = buildBreakpointKey(currentSequence == null ? "" : currentSequence.getName(),
                    currentStepIndex, actionIndex);
            resume();
        }
        recordDebugTrace("调试继续执行");
    }

    public void requestDebugStep() {
        if (!tracking) {
            return;
        }
        debugStepArmed = true;
        debugIgnoreBreakpointKey = buildBreakpointKey(currentSequence == null ? "" : currentSequence.getName(),
                currentStepIndex, actionIndex);
        if (isPaused) {
            resume();
        }
        recordDebugTrace("请求单步执行");
    }

    public void clearDebugTrace() {
        debugTraceLines.clear();
        currentDebugActionDescription = "";
    }

    public DebugSnapshot getDebugSnapshot() {
        List<String> traces = new ArrayList<>(debugTraceLines);
        Map<String, String> preview = buildVariablePreview();
        return new DebugSnapshot(tracking, isPaused, pausedForDebug,
                currentSequence == null ? "" : currentSequence.getName(),
                currentStepIndex,
                actionIndex,
                currentDebugActionDescription,
                status,
                traces,
                preview,
                countBreakpointsForSequence(currentSequence == null ? "" : currentSequence.getName()));
    }

    public Map<String, Object> getLiveVariableSnapshot() {
        return runtimeVariables.getCanonicalSnapshot();
    }

    public boolean isTrackingSequence(String sequenceName) {
        return tracking && currentSequence != null && sequenceName != null
                && sequenceName.equals(currentSequence.getName());
    }

    public static boolean hasDebugBreakpoint(String sequenceName, int stepIndex, int actionIndex) {
        String key = buildBreakpointKey(sequenceName, stepIndex, actionIndex);
        return !key.isEmpty() && debugBreakpoints.contains(key);
    }

    public static void toggleDebugBreakpoint(String sequenceName, int stepIndex, int actionIndex) {
        String key = buildBreakpointKey(sequenceName, stepIndex, actionIndex);
        if (key.isEmpty()) {
            return;
        }
        if (debugBreakpoints.contains(key)) {
            debugBreakpoints.remove(key);
        } else {
            debugBreakpoints.add(key);
        }
    }

    public static void clearDebugBreakpoints() {
        debugBreakpoints.clear();
    }

    private static String buildBreakpointKey(String sequenceName, int stepIndex, int actionIndex) {
        String sequence = sequenceName == null ? "" : sequenceName.trim();
        if (sequence.isEmpty() || stepIndex < 0 || actionIndex < 0) {
            return "";
        }
        return sequence + "#" + stepIndex + "#" + actionIndex;
    }

    private int countBreakpointsForSequence(String sequenceName) {
        String sequence = sequenceName == null ? "" : sequenceName.trim();
        if (sequence.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String key : debugBreakpoints) {
            if (key != null && key.startsWith(sequence + "#")) {
                count++;
            }
        }
        return count;
    }

    private Map<String, String> buildVariablePreview() {
        return buildVariablePreview(10);
    }

    private Map<String, String> buildVariablePreview(int limit) {
        LinkedHashMap<String, String> preview = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : runtimeVariables.getCanonicalSnapshot().entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            preview.put(entry.getKey(), LegacyActionRuntime.stringifyValue(entry.getValue()));
            if (++count >= Math.max(1, limit)) {
                break;
            }
        }
        return preview;
    }

    private void recordDebugTrace(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        String trimmed = message.trim();
        debugTraceLines.addLast(trimmed);
        while (debugTraceLines.size() > 14) {
            debugTraceLines.removeFirst();
        }
        appendExecutionLogEvent("trace", trimmed);
    }

    private void appendExecutionLogEvent(String type, String message) {
        if (executionLogSessionId == null || executionLogSessionId.trim().isEmpty()) {
            return;
        }
        ExecutionLogManager.appendEvent(executionLogSessionId, type, currentStepIndex, actionIndex, message, status,
                buildVariablePreview(12));
    }

    private void resetExecutionResultState() {
        this.executionResultSuccess = false;
        this.executionResultReason = "";
    }

    private void markExecutionResult(boolean success, String reason) {
        this.executionResultSuccess = success;
        this.executionResultReason = reason == null ? "" : reason;
    }

    private void recordFailureDetail(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return;
        }
        runtimeVariables.putLocal("last_failure_reason", reason.trim());
        recordDebugTrace("失败原因: " + reason.trim());
        appendExecutionLogEvent("failure", reason.trim());
    }

    private void startExecutionLogSession() {
        finishExecutionLogSessionIfNeeded();
        if (currentSequence == null) {
            return;
        }
        executionLogSessionId = ExecutionLogManager.startSession(currentSequence.getName(), backgroundRunner,
                buildVariablePreview(12));
        resetExecutionResultState();
        ExecutionLogManager.appendEvent(executionLogSessionId, "start", currentStepIndex, actionIndex,
                "执行开始: " + currentSequence.getName(), status, buildVariablePreview(12));
    }

    private void finishExecutionLogSessionIfNeeded() {
        if (executionLogSessionId == null || executionLogSessionId.trim().isEmpty()) {
            return;
        }
        String finishReason = executionResultReason == null || executionResultReason.trim().isEmpty()
                ? (executionResultSuccess ? "执行完成" : "执行停止")
                : executionResultReason;
        ExecutionLogManager.finishSession(executionLogSessionId, executionResultSuccess, finishReason, status,
                buildVariablePreview(12));
        executionLogSessionId = "";
    }

    private void pauseForDebugInternal(String reason) {
        if (!tracking || isPaused) {
            return;
        }
        this.isPaused = true;
        this.pausedByGui = false;
        this.pausedForDebug = true;
        EmbeddedNavigationHandler.INSTANCE.pause();
        if (!getStatus().contains(" | " + I18n.format("status.path.paused"))) {
            setStatus(getStatus() + " | " + I18n.format("status.path.paused"));
        }
        recordDebugTrace(reason);
    }

    private void consumeDebugProgress(String message) {
        if (message != null && !message.trim().isEmpty()) {
            recordDebugTrace(message);
        }
        if (debugStepArmed) {
            debugStepArmed = false;
            pauseForDebugInternal("单步执行完成");
        }
    }

    private void runWithExecutionContext(Runnable action) {
        PathSequenceEventListener previous = ACTION_EXECUTION_CONTEXT.get();
        ACTION_EXECUTION_CONTEXT.set(this);
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTION_EXECUTION_CONTEXT.remove();
            } else {
                ACTION_EXECUTION_CONTEXT.set(previous);
            }
        }
    }

    public void pauseByGui() {
        if (isTracking() && !isPaused) {
            this.isPaused = true;
            this.pausedByGui = true;
            this.pausedForDebug = false;
            EmbeddedNavigationHandler.INSTANCE.pause();
            releaseResources();
            setStatus(getStatus() + " | " + I18n.format("status.path.paused_gui"));
            zszlScriptMod.LOGGER.info(I18n.format("log.path.paused_by_gui"));
            recordDebugTrace("GUI 打开，序列暂停");
        }
    }

    public boolean wasPausedByGui() {
        return this.pausedByGui;
    }

    public void pause() {
        if (!isTracking() || isPaused) {
            return;
        }
        this.isPaused = true;
        this.pausedByGui = false;
        this.pausedForDebug = false;
        EmbeddedNavigationHandler.INSTANCE.pause();
        releaseResources();
        setStatus(getStatus() + " | " + I18n.format("status.path.paused"));
        zszlScriptMod.LOGGER.info(I18n.format("log.path.paused"));
        recordDebugTrace("序列暂停");
    }

    public void resume() {
        if (!isPaused) {
            return;
        }
        this.isPaused = false;
        this.pausedByGui = false;
        this.pausedForDebug = false;
        EmbeddedNavigationHandler.INSTANCE.resume();
        stripPauseStatusSuffix();
        zszlScriptMod.LOGGER.info(I18n.format("log.path.resumed"));
        recordDebugTrace("序列恢复");
    }

    private void stripPauseStatusSuffix() {
        String pausedGuiSuffix = " | " + I18n.format("status.path.paused_gui");
        String pausedSuffix = " | " + I18n.format("status.path.paused");
        if (getStatus().contains(pausedGuiSuffix)) {
            setStatus(getStatus().replace(pausedGuiSuffix, ""));
        } else if (getStatus().contains(pausedSuffix)) {
            setStatus(getStatus().replace(pausedSuffix, ""));
        }
    }

    private boolean resetPauseStateForSequenceLifecycle(String reason, boolean resumeNavigation) {
        boolean wasPaused = this.isPaused || this.pausedByGui || this.pausedForDebug;
        this.isPaused = false;
        this.pausedByGui = false;
        this.pausedForDebug = false;
        this.debugStepArmed = false;
        stripPauseStatusSuffix();
        if (wasPaused && resumeNavigation) {
            EmbeddedNavigationHandler.INSTANCE.resume();
            recordDebugTrace("清理暂停状态: " + (reason == null ? "unknown" : reason));
        }
        return wasPaused;
    }

    void prepareForSequenceStartDispatch(String reason) {
        navigationOwner = this;
        resetPauseStateForSequenceLifecycle(reason, true);
    }

    public void startTracking(PathSequence sequence, int remainingLoops) {
        startTracking(sequence, remainingLoops, null);
    }

    public void startTracking(PathSequence sequence, int remainingLoops, Map<String, Object> initialSequenceVariables) {
        startTracking(sequence, remainingLoops, initialSequenceVariables, 0);
    }

    public void startTracking(PathSequence sequence, int remainingLoops, Map<String, Object> initialSequenceVariables,
            int startStepIndex) {
        startTracking(sequence, remainingLoops, initialSequenceVariables, startStepIndex, 0);
    }

    public void startTracking(PathSequence sequence, int remainingLoops, Map<String, Object> initialSequenceVariables,
            int startStepIndex, int startActionIndex) {
        startTracking(sequence, remainingLoops, initialSequenceVariables, startStepIndex, startActionIndex, false);
    }

    public void startTracking(PathSequence sequence, int remainingLoops, Map<String, Object> initialSequenceVariables,
            int startStepIndex, int startActionIndex, boolean stopAfterCurrentStep) {
        releaseResources();
        resetPauseStateForSequenceLifecycle("startTracking:" + (sequence == null ? "unknown" : sequence.getName()),
                true);
        this.currentSequence = sequence;
        this.currentStepIndex = clampStartStepIndex(sequence, startStepIndex);
        this.initialActionIndexForCurrentStep = clampStartActionIndex(sequence, this.currentStepIndex, startActionIndex);
        this.actionIndex = this.initialActionIndexForCurrentStep;
        this.tracking = true;
        this.atTarget = false;
        this.tickDelay = 0;
        this.remainingLoops = remainingLoops;
        this.isPerformingExplicitDelay = false;
        this.explicitDelayNormalizeTo20Tps = false;
        this.explicitDelayRemainingBaselineTicks = 0.0D;
        this.currentStepRetryUsed = 0;
        this.noStopNavigationOnCompletion = false;
        this.pendingLoopRestart = false;
        this.pausedForDebug = false;
        this.debugStepArmed = false;
        this.stopAfterCurrentStep = stopAfterCurrentStep;
        this.debugIgnoreBreakpointKey = "";
        resetStepPathRetryMonitor();
        resetHuntState();
        resetHotbarUseActionState();
        resetAsyncActionState();
        resetWaitConditionState();
        clearDeferredWaitState();
        resetRepeatActionState();
        resetBranchBlockState();
        resetIfElseState();
        resetWhileConditionState();
        resetForEachListState();
        resetForEachPointState();
        resetRetryBlockState();
        runtimeVariables.clear();
        this.initialSequenceVariables.clear();
        if (initialSequenceVariables != null) {
            for (Map.Entry<String, Object> entry : initialSequenceVariables.entrySet()) {
                if (entry != null && entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                    String key = entry.getKey().trim();
                    this.initialSequenceVariables.put(key, entry.getValue());
                    runtimeVariables.putSequence(key, entry.getValue());
                }
            }
        }
        runtimeVariables.enterStep(this.currentStepIndex);
        startExecutionLogSession();
        clearDebugTrace();
        currentDebugActionDescription = "";
        recordDebugTrace("序列开始: " + sequence.getName());

        if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    I18n.format("msg.path.debug.sequence_start", sequence.getName())));
        }

        initializeStepPathRetryMonitor(mc.player);

        ensureEventBusRegistered();
        zszlScriptMod.LOGGER.info(I18n.format("log.path.tracking_started") + sequence.getName());
    }

    private int clampStartStepIndex(PathSequence sequence, int startStepIndex) {
        if (sequence == null || sequence.getSteps() == null || sequence.getSteps().isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(startStepIndex, sequence.getSteps().size() - 1));
    }

    private int clampStartActionIndex(PathSequence sequence, int stepIndex, int startActionIndex) {
        if (sequence == null || sequence.getSteps() == null || sequence.getSteps().isEmpty()) {
            return 0;
        }
        PathStep step = sequence.getSteps().get(clampStartStepIndex(sequence, stepIndex));
        int actionCount = step == null || step.getActions() == null ? 0 : step.getActions().size();
        return Math.max(0, Math.min(startActionIndex, Math.max(0, actionCount - 1)));
    }

    public void stopTracking() {
        if (tracking || pendingLoopRestart) {
            boolean stopOwnedNavigation = backgroundRunner && ownsNavigation();
            SimulatedKeyInputManager.releasePathActionKeys(backgroundRunner);
            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && mc.player != null) {
                mc.player.sendMessage(new TextComponentString(I18n.format("msg.path.debug.sequence_stop",
                        currentSequence != null ? currentSequence.getName() : I18n.format("msg.common.unknown"))));
            }
            resetPauseStateForSequenceLifecycle("stopTracking", false);
            this.tracking = false;
            this.pendingLoopRestart = false;
            this.debugIgnoreBreakpointKey = "";
            resetStepPathRetryMonitor();
            recordDebugTrace("序列停止: " + (currentSequence != null ? currentSequence.getName() : "unknown"));
            finishExecutionLogSessionIfNeeded();
            this.currentSequence = null;
            this.noStopNavigationOnCompletion = false;
            this.stopAfterCurrentStep = false;
            ensureEventBusUnregistered();
            status = I18n.format("status.path.stopped");
            resetHuntState();
            resetHotbarUseActionState();
            resetAsyncActionState();
            resetWaitConditionState();
            resetRepeatActionState();
            resetBranchBlockState();
            resetIfElseState();
            resetWhileConditionState();
            resetForEachListState();
            resetForEachPointState();
            resetRetryBlockState();
            releaseResources();
            if (stopOwnedNavigation) {
                stopOwnedNavigation();
            }
            runtimeVariables.clear();
            if (backgroundRunner) {
                backgroundRunners.remove(this);
            } else {
                GuiInventory.isLooping = false;
                loopDelayTicks = 20;
                PathSequenceManager.clearRunSequenceCallStack();
            }
            zszlScriptMod.LOGGER.info(I18n.format("log.path.tracking_stopped"));
        }
    }

    private void finishTrackingNaturally() {
        if (noStopNavigationOnCompletion && ownsNavigation()) {
            navigationOwner = null;
        }
        stopTracking();
    }

    public ProgressSnapshot captureProgressSnapshot() {
        if (!tracking || currentSequence == null) {
            return null;
        }
        return new ProgressSnapshot(
                currentSequence.getName(),
                currentStepIndex,
                actionIndex,
                atTarget,
                remainingLoops,
                tickDelay,
                isPerformingExplicitDelay,
                status,
                runtimeVariables.captureSnapshot(),
                currentStepRetryUsed,
                noStopNavigationOnCompletion,
                backgroundRunner,
                executionLogSessionId);
    }

    public boolean resumeFromSnapshot(PathSequence sequence, ProgressSnapshot snapshot) {
        return resumeFromSnapshot(sequence, snapshot, false);
    }

    public boolean resumeFromSnapshot(PathSequence sequence, ProgressSnapshot snapshot,
            boolean preserveCurrentNavigation) {
        if (sequence == null || snapshot == null || sequence.getSteps().isEmpty()) {
            return false;
        }

        int maxStep = sequence.getSteps().size() - 1;
        int resumeStep = Math.max(0, Math.min(snapshot.getStepIndex(), maxStep));

        this.currentSequence = sequence;
        this.currentStepIndex = resumeStep;
        this.actionIndex = Math.max(0, snapshot.getActionIndex());
        this.tracking = true;
        this.atTarget = snapshot.isAtTarget();
        this.tickDelay = Math.max(0, snapshot.getTickDelay());
        this.remainingLoops = snapshot.getRemainingLoops();
        this.isPerformingExplicitDelay = snapshot.isExplicitDelay();
        this.explicitDelayNormalizeTo20Tps = false;
        this.explicitDelayRemainingBaselineTicks = this.tickDelay;
        this.currentStepRetryUsed = Math.max(0, snapshot.getStepRetryUsed());
        this.noStopNavigationOnCompletion = snapshot.isNoStopNavigationOnCompletion();
        resetPauseStateForSequenceLifecycle("resumeFromSnapshot:" + sequence.getName(), true);
        this.debugIgnoreBreakpointKey = "";
        resetStepPathRetryMonitor();
        resetHuntState();
        resetHotbarUseActionState();
        resetAsyncActionState();
        resetWaitConditionState();
        clearDeferredWaitState();
        resetRepeatActionState();
        resetBranchBlockState();
        resetIfElseState();
        resetWhileConditionState();
        resetForEachListState();
        resetForEachPointState();
        resetRetryBlockState();
        this.initialSequenceVariables.clear();
        runtimeVariables.clear();
        runtimeVariables.enterStep(this.currentStepIndex);
        runtimeVariables.restoreSnapshot(snapshot.getVariableSnapshot());
        startExecutionLogSession();
        clearDebugTrace();
        currentDebugActionDescription = "";
        recordDebugTrace("恢复序列: " + sequence.getName() + " @ step=" + this.currentStepIndex + ", action=" + this.actionIndex);
        this.status = (snapshot.getStatus() == null || snapshot.getStatus().trim().isEmpty())
                ? sequence.getName()
                : snapshot.getStatus();
        GuiInventory.isLooping = true;

        if (!this.atTarget) {
            double[] target = sequence.getSteps().get(this.currentStepIndex).getGotoPoint();
            if (!Double.isNaN(target[0])) {
                prepareForSequenceStartDispatch("resumeFromSnapshot.goto");
                startNavigationGoto(target[0], target[1], target[2], true);
                initializeStepPathRetryMonitor(mc.player);
            } else if (!preserveCurrentNavigation) {
                stopNavigation();
                resetStepPathRetryMonitor();
            }
        } else if (!preserveCurrentNavigation) {
            stopNavigation();
            resetStepPathRetryMonitor();
        }

        ensureEventBusRegistered();
        return true;
    }

    // --- 新增：启动狩猎模式的方法 ---
    public void startHunting(JsonObject params) {
        resetHuntState();
        this.isHunting = true;
        double radius = readHuntDoubleParam(params, "radius", 3.0D);
        double upRange = readHuntDoubleParam(params, "huntUpRange", DEFAULT_CENTER_HUNT_VERTICAL_RANGE);
        double downRange = readHuntDoubleParam(params, "huntDownRange", DEFAULT_CENTER_HUNT_VERTICAL_RANGE);
        this.huntRadius = Math.max(0.0D, radius);
        this.huntAutoAttack = true;
        this.huntAttackMode = KillAuraHandler.ATTACK_MODE_NORMAL;
        this.huntAttackSequenceName = "";
        this.huntAimLockEnabled = true;
        this.huntPendingCompleteAfterSequence = false;
        this.huntTrackingDistanceSq = 0.0D;
        this.huntUpRange = Math.max(0.0D, upRange);
        this.huntDownRange = Math.max(0.0D, downRange);
        this.huntAreaSweepEnabled = readHuntBooleanParam(params, "enableAreaSweep", true);
        this.huntAreaSweepCellSize = Math.max(HUNT_AREA_SWEEP_MIN_CELL_SIZE,
                Math.min(HUNT_AREA_SWEEP_MAX_CELL_SIZE, readHuntIntParam(params, "areaSweepCellSize", 8)));
        this.huntAreaSweepPlanner.reset(this.huntAreaSweepCellSize);
        this.huntMode = KillAuraHandler.huntMode;
        this.huntOrbitEnabled = KillAuraHandler.isHuntOrbitEnabled();
        this.huntChaseIntervalEnabled = false;
        this.huntChaseIntervalTicks = 0;
        this.huntAttackRemaining = -1;
        this.huntEmptyConfirmTicks = 0;
        this.huntNoTargetSkipCount = Math.max(0, readHuntIntParam(params, "noTargetSkipCount", 0));
        this.huntNoDamageAttackLimit = KillAuraHandler.getNoDamageAttackLimit();
        
        // 读取新增参数
        this.huntShowRange = readHuntBooleanParam(params, "showHuntRange", false);
        this.huntIgnoreInvisible = KillAuraHandler.ignoreInvisible;
        
        // 记录搜怪中心点（玩家当前位置）
        if (mc.player != null) {
            this.huntCenterX = mc.player.posX;
            this.huntCenterY = mc.player.posY;
            this.huntCenterZ = mc.player.posZ;
        }

        String huntEntityType = params != null && params.has("entityType")
                ? params.get("entityType").getAsString().trim()
                : "";
        if (huntEntityType.isEmpty()) {
            this.huntTargetHostile = KillAuraHandler.targetHostile;
            this.huntTargetPassive = KillAuraHandler.targetPassive;
            this.huntTargetPlayers = KillAuraHandler.targetPlayers;
        } else if ("player".equalsIgnoreCase(huntEntityType) || "玩家".equalsIgnoreCase(huntEntityType)) {
            this.huntTargetHostile = false;
            this.huntTargetPassive = false;
            this.huntTargetPlayers = true;
        } else if ("hostile".equalsIgnoreCase(huntEntityType)
                || "monster".equalsIgnoreCase(huntEntityType)
                || "mob".equalsIgnoreCase(huntEntityType)
                || "敌对生物".equalsIgnoreCase(huntEntityType)
                || "怪物".equalsIgnoreCase(huntEntityType)) {
            this.huntTargetHostile = true;
            this.huntTargetPassive = false;
            this.huntTargetPlayers = false;
        } else if ("passive".equalsIgnoreCase(huntEntityType)
                || "animal".equalsIgnoreCase(huntEntityType)
                || "被动生物".equalsIgnoreCase(huntEntityType)
                || "动物".equalsIgnoreCase(huntEntityType)) {
            this.huntTargetHostile = false;
            this.huntTargetPassive = true;
            this.huntTargetPlayers = false;
        } else {
            this.huntTargetHostile = true;
            this.huntTargetPassive = true;
            this.huntTargetPlayers = true;
        }
        this.huntRestrictTargetGroups = true;

        this.huntEnableNameWhitelist = readHuntBooleanParam(params, "enableNameWhitelist", false);
        this.huntWaitForWhitelistRespawn = this.huntEnableNameWhitelist
                && readHuntBooleanParam(params, "waitForWhitelistRespawn", false);
        this.huntWaitForWhitelistRespawnAfterCompletion = this.huntEnableNameWhitelist
                && readHuntBooleanParam(params, "waitForWhitelistRespawnAfterCompletion", false);
        this.huntConfirmKillCompletion = this.huntEnableNameWhitelist
                && readHuntBooleanParam(params, "confirmKillCompletion", false);
        int killCompletionConfirmSeconds = Math.max(0,
                readHuntIntParam(params, "killCompletionConfirmSeconds",
                        HUNT_KILL_COMPLETION_CONFIRM_DEFAULT_SECONDS));
        this.huntKillCompletionConfirmTicks = killCompletionConfirmSeconds > Integer.MAX_VALUE / 20
                ? Integer.MAX_VALUE : killCompletionConfirmSeconds * 20;
        int respawnTimeoutSeconds = Math.max(0,
                readHuntIntParam(params, "whitelistRespawnTimeoutSeconds", 120));
        this.huntWhitelistRespawnTimeoutTicks = respawnTimeoutSeconds > Integer.MAX_VALUE / 20
                ? Integer.MAX_VALUE : respawnTimeoutSeconds * 20;
        this.huntWhitelistRespawnElapsedTicks = 0;
        this.huntWhitelistRespawnWaiting = false;
        this.huntWhitelistRespawnCompletesAction = false;
        this.huntWhitelistRespawnWaitSatisfied = false;
        this.huntWhitelistTargetKilledAfterWait = false;
        this.huntHasSeenWhitelistTarget = false;
        this.huntKillCompletionConfirming = false;
        this.huntKillCompletionConfirmationResumed = false;
        this.huntKillCompletionConfirmationElapsedTicks = 0;
        this.huntForceEndEnabled = readHuntBooleanParam(params, "forceEndHunt", false);
        int forceEndTimeoutSeconds = Math.max(0,
                readHuntIntParam(params, "forceEndHuntTimeoutSeconds", 60));
        this.huntForceEndTimeoutTicks = forceEndTimeoutSeconds > Integer.MAX_VALUE / 20
                ? Integer.MAX_VALUE : forceEndTimeoutSeconds * 20;
        this.huntForceEndElapsedTicks = 0;
        this.huntEnableNameBlacklist = readHuntBooleanParam(params, "enableNameBlacklist", false);
        this.huntWhitelistTargets.addAll(readHuntWhitelistTargets(params));
        for (HuntWhitelistTarget target : this.huntWhitelistTargets) {
            this.huntNameWhitelist.add(target.name);
            if (target.hasKillLimit()) {
                this.huntWhitelistKillProgress.put(target.name, 0);
            }
        }
        if (this.huntNameWhitelist.isEmpty()) {
            this.huntNameWhitelist.addAll(readHuntNameList(params, "nameWhitelist", "nameWhitelistText"));
        }
        this.huntNameBlacklist.addAll(readHuntNameList(params, "nameBlacklist", "nameBlacklistText"));
        if (this.huntNameBlacklist.isEmpty()) {
            this.huntNameBlacklist.addAll(readHuntNameList(params, "nameBlacklistEntries", null));
        }

        stopNavigation();
        setStatus(getStatus().split(" \\| ")[0] + " | " + I18n.format("status.path.hunting"));
        zszlScriptMod.LOGGER.info(
                "进入中心搜怪击杀: 半径={}, 垂直范围=+{}/-{}, 战斗配置=杀戮光环当前配置, 实体类型={}, 攻击模式={}, 追击模式={}, 自动绕圈={}, 无目标跳过={}, 无掉血排除次数={}, 目标类型[敌对={}, 被动={}, 玩家={}], 动作白名单={}, 动作黑名单={}, 显示范围={}",
                this.huntRadius,
                this.huntUpRange,
                this.huntDownRange,
                huntEntityType.isEmpty() ? "沿用杀戮光环" : huntEntityType,
                KillAuraHandler.attackMode,
                KillAuraHandler.huntMode,
                this.huntOrbitEnabled,
                this.huntNoTargetSkipCount,
                this.huntNoDamageAttackLimit > 0 ? this.huntNoDamageAttackLimit : "关闭",
                this.huntTargetHostile,
                this.huntTargetPassive,
                this.huntTargetPlayers,
                this.huntEnableNameWhitelist ? formatHuntWhitelistForLog() : "关闭",
                this.huntEnableNameBlacklist ? this.huntNameBlacklist : "关闭",
                this.huntShowRange);
        recordDebugTrace("hunt config: 中心=("
                + String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.huntCenterX, this.huntCenterY, this.huntCenterZ)
                + "), 半径=" + String.format(Locale.ROOT, "%.1f", this.huntRadius)
                + ", 垂直=+" + String.format(Locale.ROOT, "%.1f", this.huntUpRange)
                + "/-" + String.format(Locale.ROOT, "%.1f", this.huntDownRange)
                + ", 实体类型=" + (huntEntityType.isEmpty() ? "沿用杀戮光环" : huntEntityType)
                + ", 目标类型[敌对=" + this.huntTargetHostile
                + ", 被动=" + this.huntTargetPassive
                + ", 玩家=" + this.huntTargetPlayers
                + "], 动作白名单="
                + (this.huntEnableNameWhitelist ? formatHuntWhitelistForLog() : "关闭")
                + ", 动作黑名单="
                + (this.huntEnableNameBlacklist ? this.huntNameBlacklist : "关闭")
                + ", 忽略隐身=" + this.huntIgnoreInvisible);
    }
    // --- 新增结束 ---

    // --- 新增：启动跟随实体模式的方法 ---
    public void startFollowingEntity(JsonObject params) {
        resetFollowEntityState();
        this.isFollowingEntity = true;
        
        this.followEntityType = params != null && params.has("entityType") 
                ? params.get("entityType").getAsString() : "player";
        this.followTargetName = params != null && params.has("targetName") 
                ? params.get("targetName").getAsString().trim() : "";
        this.followSearchRadius = readHuntDoubleParam(params, "searchRadius", 16.0D);
        this.followDistance = readHuntDoubleParam(params, "followDistance", 3.0D);
        this.followTimeoutSeconds = readHuntIntParam(params, "timeout", 0);
        this.followStopOnLost = !params.has("stopOnLost") || params.get("stopOnLost").getAsBoolean();
        this.followStartTime = System.currentTimeMillis();
        
        stopNavigation();
        setStatus(getStatus().split(" \\| ")[0] + " | 跟随实体中");
        zszlScriptMod.LOGGER.info(
                "开始跟随实体: 类型={}, 名称={}, 搜索半径={}, 跟随距离={}, 超时={}秒, 丢失后停止={}",
                this.followEntityType,
                this.followTargetName.isEmpty() ? "最近目标" : this.followTargetName,
                this.followSearchRadius,
                this.followDistance,
                this.followTimeoutSeconds > 0 ? this.followTimeoutSeconds : "无限",
                this.followStopOnLost);
    }
    // --- 新增结束 ---

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("path_sequence")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("path_sequence");
        try {
        // ==================== 核心修复 ====================
        // 在方法最开始添加此检查，确保只处理主玩家的事件。
        if (mc.player == null || event.player != mc.player) {
            return;
        }
        // ==================== 修复结束 ====================

        if (event.phase != TickEvent.Phase.START || event.side != Side.CLIENT) {
            return;
        }

        if (isPaused) {
            return;
        }

        // --- 核心修复 #1：将 event.player 强制转换为 (EntityPlayerSP) ---
        // 我们在下面已经检查了 event.player.equals(mc.player)，所以这个转换是安全的。
        if (isHunting) {
            executeHuntTick((EntityPlayerSP) event.player);
            return; // 在狩猎模式下，不执行后续的路径跟踪逻辑
        }
        
        if (isFollowingEntity) {
            executeFollowEntityTick((EntityPlayerSP) event.player);
            return; // 在跟随实体模式下，不执行后续的路径跟踪逻辑
        }
        // --- 修复结束 ---

        if (!tracking)
            return;
        // 这一行现在是多余的，但保留也无妨
        if (event.player == null || !event.player.equals(mc.player))
            return;

        EntityPlayerSP player = (EntityPlayerSP) event.player;

        if (tickDelay > 0) {
            if (isPerformingExplicitDelay && explicitDelayNormalizeTo20Tps) {
                explicitDelayRemainingBaselineTicks = Math.max(0.0D,
                        explicitDelayRemainingBaselineTicks - (1.0D / ModUtils.getCurrentTimerSpeedMultiplier()));
                tickDelay = Math.max(0, (int) Math.ceil(explicitDelayRemainingBaselineTicks));
            } else {
                tickDelay--;
            }
            if (isPerformingExplicitDelay && ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                double secondsLeft = explicitDelayNormalizeTo20Tps
                        ? Math.max(0.0D, explicitDelayRemainingBaselineTicks) / 20.0D
                        : tickDelay / 20.0D;
                String baseStatus = status.split(" \\| ")[0];
                setStatus(I18n.format("status.path.delaying", baseStatus, secondsLeft));
                if (tickDelay > 0 && tickDelay % 20 == 0 && mc.player != null) {
                    String chatMessage = I18n.format("msg.path.debug.delaying_left", secondsLeft);
                    mc.player.sendMessage(new TextComponentString(chatMessage));
                }
            }
            return;
        }

        if (isPerformingExplicitDelay) {
            isPerformingExplicitDelay = false;
            explicitDelayNormalizeTo20Tps = false;
            explicitDelayRemainingBaselineTicks = 0.0D;
            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                setStatus(status.split(" \\| ")[0]);
            }
        }

        List<PathStep> steps = currentSequence.getSteps();

        if (currentStepIndex >= steps.size()) {
            boolean preserveCurrentNavigation = noStopNavigationOnCompletion;
            if (shouldStopNavigationOnSequenceCompletion()) {
                stopNavigation();
            }

            if (!backgroundRunner
                    && PathSequenceManager.resumeCallerSequenceAfterAction(player, preserveCurrentNavigation)) {
                markExecutionResult(true, "子序列执行完成并恢复调用方");
                finishExecutionLogSessionIfNeeded();
                return;
            }

            if (remainingLoops != 0) {
                if (remainingLoops > 0) {
                    remainingLoops--;
                }
                if (remainingLoops != 0) {
                    markExecutionResult(true, "单轮执行完成，等待下一轮");
                    finishExecutionLogSessionIfNeeded();
                    status = I18n.format("status.path.wait_next_loop");
                    tracking = false;
                    pendingLoopRestart = true;
                    ensureEventBusUnregistered();

                    int delay = currentSequence.getLoopDelayTicks();
                    PathSequence loopingSequence = currentSequence;
                    int nextRemainingLoops = remainingLoops;
                    zszlScriptMod.LOGGER.info(I18n.format("log.path.loop_finished_wait_next"), delay);
                    new ModUtils.DelayAction(delay, () -> {
                        if (!pendingLoopRestart) {
                            return;
                        }
                        pendingLoopRestart = false;
                        if (backgroundRunner) {
                            if (loopingSequence != null) {
                                startTracking(loopingSequence, nextRemainingLoops, this.initialSequenceVariables);
                                setStatus(loopingSequence.getName() + " | 后台执行");
                                resume();
                                primeBackgroundNavigation(this, loopingSequence);
                            }
                        } else if (currentSequence != null) {
                            int nextLoopCount = nextRemainingLoops < 0
                                    ? -1
                                    : nextRemainingLoops + GuiInventory.loopCounter;
                            PathSequenceManager.startNextLoopWithVariables(currentSequence.getName(),
                                    this.initialSequenceVariables, nextLoopCount);
                        }
                    }).accept(player);
                    return;
                } else {
                    status = I18n.format("status.path.completed_times", GuiInventory.loopCounter);
                    markExecutionResult(true, "序列执行完成");
                    finishTrackingNaturally();
                }
            } else {
                markExecutionResult(true, "序列执行完成");
                finishTrackingNaturally();
            }
            return;
        }

        PathStep currentStep = steps.get(currentStepIndex);
        double[] target = currentStep.getGotoPoint();

        if (!atTarget) {
            if (!ensureResources(resolveMovementResources(target), "move_to_step_target")) {
                return;
            }
            if (AutoEatHandler.isEating) {
                return;
            }
            if (hasReachedGotoTarget(player, currentStep, target)) {
                if (EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()) {
                    if (!waitingForNavigationToFinishAtTarget) {
                        waitingForNavigationToFinishAtTarget = true;
                        recordDebugTrace("进入到达范围，等待寻路自然结束");
                        setStatus(currentSequence.getName() + (backgroundRunner ? " | 后台执行" : "")
                                + " | 到达范围内，等待寻路完成");
                    }
                    resetStepPathRetryMonitor();
                    return;
                }
                if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && !atTarget && mc.player != null) {
                    mc.player.sendMessage(new TextComponentString(
                            I18n.format("msg.path.debug.reached_step_target", currentStepIndex)));
                }
                zszlScriptMod.LOGGER.info(I18n.format("log.path.reached_target"), currentStepIndex,
                        currentSequence.getName());
                if (waitingForNavigationToFinishAtTarget) {
                    recordDebugTrace("寻路自然结束，开始执行步骤动作");
                    restoreSequenceBaseStatus();
                }
                waitingForNavigationToFinishAtTarget = false;
                atTarget = true;
                currentStepRetryUsed = 0;
                resetStepPathRetryMonitor();
                actionIndex = initialActionIndexForCurrentStep;
                initialActionIndexForCurrentStep = 0;
            } else if (waitingForNavigationToFinishAtTarget) {
                waitingForNavigationToFinishAtTarget = false;
                restoreSequenceBaseStatus();
            } else if (handleCurrentStepPathRetry(player, currentStep)) {
                return;
            }
        } else {
            List<ActionData> actions = currentStep.getActions();
            int immediateBurstCount = 0;

            while (true) {
                if (handleRepeatActionBoundary(actions)) {
                    return;
                }
                if (handleBranchBlockBoundary(actions)) {
                    return;
                }
                if (handleIfElseBoundary(actions)) {
                    return;
                }
                if (handleWhileConditionBoundary(actions)) {
                    return;
                }
                if (handleForEachPointBoundary(actions)) {
                    return;
                }
                if (handleForEachListBoundary(actions)) {
                    return;
                }
                if (handleRetryBlockBoundary(actions, player)) {
                    return;
                }

                if (handleDeferredWaitAction(player)) {
                    return;
                }

                if (actionIndex >= actions.size()) {
                    if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && mc.player != null) {
                        mc.player.sendMessage(new TextComponentString(
                                I18n.format("msg.path.debug.step_actions_done", currentStepIndex)));
                    }
                    if (stopAfterCurrentStep) {
                        markExecutionResult(true, "单步执行完成");
                        finishTrackingNaturally();
                        return;
                    }
                    currentStepIndex++;
                    actionIndex = 0;
                    atTarget = false;
                    resetStepArrivalWaitState();
                    currentStepRetryUsed = 0;
                    resetStepPathRetryMonitor();
                    releaseResources();
                    resetAsyncActionState();
                    resetRepeatActionState();
                    runtimeVariables.enterStep(currentStepIndex);
                    if (currentStepIndex < steps.size()) {
                        restartCurrentStepTarget(steps.get(currentStepIndex));
                    } else {
                        if (shouldStopNavigationOnSequenceCompletion()) {
                            stopNavigation();
                        }
                        resetStepPathRetryMonitor();
                    }
                    consumeDebugProgress("进入下一步骤: " + currentStepIndex);
                    return;
                }

                ActionData actionData = actions.get(actionIndex);
                ActionData resolvedActionData = resolveRuntimeActionData(actionData, player);
                currentDebugActionDescription = resolvedActionData == null ? "" : resolvedActionData.getDescription();
                String breakpointKey = buildBreakpointKey(currentSequence == null ? "" : currentSequence.getName(),
                        currentStepIndex, actionIndex);
                if (!breakpointKey.isEmpty()
                        && !breakpointKey.equals(debugIgnoreBreakpointKey)
                        && hasDebugBreakpoint(currentSequence == null ? "" : currentSequence.getName(),
                                currentStepIndex, actionIndex)) {
                    pauseForDebugInternal("命中断点: step=" + currentStepIndex + ", action=" + actionIndex);
                    return;
                }
                if (!breakpointKey.equals(debugIgnoreBreakpointKey)) {
                    debugIgnoreBreakpointKey = "";
                }

                if (!ensureResources(resolveActionResources(resolvedActionData), currentDebugActionDescription)) {
                    return;
                }

                if (handleRuntimeControlAction(player, actions, actionData, resolvedActionData)) {
                    return;
                }

                if ("use_hotbar_item".equalsIgnoreCase(resolvedActionData.type)) {
                    handleUseHotbarItemAction(player, resolvedActionData);
                    return;
                }

                if (handleConditionalOrWaitAction(player, resolvedActionData)) {
                    return;
                }

                Consumer<EntityPlayerSP> action = PathSequenceManager.parseAction(resolvedActionData.type,
                        resolvedActionData.params);

                if (action == null) {
                    recordFailureDetail("动作解析失败: " + currentDebugActionDescription);
                    handleCurrentStepFailure("action_parse_failed:" + resolvedActionData.type, null);
                    return;
                }

                String actionUuid = resolvedActionData.params != null && resolvedActionData.params.has("uuid")
                        ? resolvedActionData.params.get("uuid").getAsString() : "";
                McpObservation.markActionContext(currentSequence == null ? "" : currentSequence.getName(),
                        currentStepIndex, actionIndex, resolvedActionData.type, actionUuid);

                if (handleAsyncAction(player, resolvedActionData, action)) {
                    return;
                }

                if (action instanceof ModUtils.DelayAction) {
                    ModUtils.DelayAction delayAction = (ModUtils.DelayAction) action;
                    recordDebugTrace("delay action: " + currentDebugActionDescription);
                    try {
                        runWithExecutionContext(() -> delayAction.accept(player));
                    } catch (Exception e) {
                        recordFailureDetail("延迟动作异常: " + currentDebugActionDescription + " / " + e.getMessage());
                        handleCurrentStepFailure("delay_action_exception:" + resolvedActionData.type, e);
                        return;
                    }
                    tickDelay = Math.max(0, delayAction.getDelayTicks());
                    isPerformingExplicitDelay = tickDelay > 0;
                    explicitDelayNormalizeTo20Tps = isPerformingExplicitDelay
                            && delayAction.shouldNormalizeDelayTo20Tps();
                    explicitDelayRemainingBaselineTicks = explicitDelayNormalizeTo20Tps
                            ? Math.max(0, delayAction.getConfiguredDelayTicks())
                            : 0.0D;
                    if (tickDelay > 0) {
                        zszlScriptMod.LOGGER.info(I18n.format("log.path.delay_ticks"), tickDelay);
                    }
                    releaseResources();
                    actionIndex++;
                    consumeDebugProgress("执行延迟动作");
                    if (!canContinueImmediateActionBurst(resolvedActionData, tickDelay, ++immediateBurstCount)) {
                        return;
                    }
                    continue;
                }

                if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && mc.player != null) {
                    mc.player.sendMessage(new TextComponentString(
                            I18n.format("msg.path.debug.execute_action", currentStepIndex, actionIndex,
                                    resolvedActionData.getDescription())));
                }
                recordDebugTrace("action: step=" + currentStepIndex + ", action=" + actionIndex + " -> "
                        + currentDebugActionDescription);
                try {
                    runWithExecutionContext(() -> action.accept(player));
                    zszlScriptMod.LOGGER.info(I18n.format("log.path.execute_action"), actionIndex, currentStepIndex);
                } catch (Exception e) {
                    recordFailureDetail("动作执行异常: " + currentDebugActionDescription + " / " + e.getMessage());
                    handleCurrentStepFailure("action_exception:" + resolvedActionData.type, e);
                    return;
                }
                // A stop-current-sequence action may have stopped this runner
                // itself. Do not advance its step or let special long-running
                // actions revive execution after that request.
                if (!tracking) {
                    return;
                }
                if ("hunt".equalsIgnoreCase(resolvedActionData.type)) {
                    releaseResources();
                    consumeDebugProgress("中心搜怪动作运行中");
                    return;
                }
                if ("follow_entity".equalsIgnoreCase(resolvedActionData.type)) {
                    releaseResources();
                    consumeDebugProgress("跟随实体动作运行中");
                    return;
                }
                releaseResources();
                actionIndex++;
                applyBuiltinSequenceDelay();
                consumeDebugProgress("执行动作完成");
                return;
            }
        }
        } catch (Exception e) {
            recordFailureDetail("路径 tick 参数异常: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " / " + e.getMessage()));
            handleCurrentStepFailure("path_tick_exception", e);
            return;
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (mc == null || mc.player == null || mc.playerController == null || mc.world == null) {
            return;
        }
    }

    private void resetHotbarUseActionState() {
        this.hotbarUseActionRunning = false;
        this.hotbarUseItemName = "";
        this.hotbarUseMatchMode = AutoUseItemRule.MatchMode.CONTAINS;
        this.hotbarUseMode = AutoUseItemRule.UseMode.RIGHT_CLICK;
        this.hotbarUseChangeLocalSlot = false;
        this.hotbarUseSwitchItemDelayTicks = 0;
        this.hotbarUseSwitchDelayTicks = 0;
        this.hotbarUseSwitchBackDelayTicks = 0;
        this.hotbarUseRemainingCount = 0;
        this.hotbarUseIntervalTicks = 0;
        this.hotbarUseWaitTicks = 0;
    }

    private void resetAsyncActionState() {
        if ("pickup_nearby_items".equals(this.pendingAsyncActionType)) {
            NearbyItemPickupActionHandler.cancel();
        }
        if ("move_inventory_items_to_chest_slots".equals(this.pendingAsyncActionType)
                || "transferitemstowarehouse".equals(this.pendingAsyncActionType)
                || "warehouse_auto_deposit".equals(this.pendingAsyncActionType)) {
            ItemFilterHandler.cancelMoveChestTransfer();
        }
        this.pendingAsyncActionType = "";
        this.pendingAsyncActionStepIndex = -1;
        this.pendingAsyncActionIndex = -1;
    }

    private boolean canContinueImmediateActionBurst(ActionData actionData, int currentDelayTicks, int burstCount) {
        if (!isZeroDelayAutoChestClick(actionData) || currentDelayTicks > 0) {
            return false;
        }
        if (burstCount < 128) {
            return true;
        }
        zszlScriptMod.LOGGER.warn("连续执行零延迟点击箱子格子动作过多，已切到下一 tick 继续。");
        tickDelay = 1;
        return false;
    }

    private boolean isZeroDelayAutoChestClick(ActionData actionData) {
        if (actionData == null || actionData.type == null || !"autochestclick".equalsIgnoreCase(actionData.type)) {
            return false;
        }
        return !actionData.params.has("delayTicks") || Math.max(0, actionData.params.get("delayTicks").getAsInt()) <= 0;
    }

    private boolean handleAsyncAction(EntityPlayerSP player, ActionData actionData, Consumer<EntityPlayerSP> action) {
        String type = actionData == null || actionData.type == null ? "" : actionData.type.trim().toLowerCase(Locale.ROOT);
        if (!"transferitemstowarehouse".equals(type)
                && !"move_inventory_items_to_chest_slots".equals(type)
                && !"spread_inventory_item".equals(type)
                && !"stack_inventory_item".equals(type)
                && !"pickup_nearby_items".equals(type)
                && !"warehouse_auto_deposit".equals(type)) {
            return false;
        }

        boolean samePendingAction = type.equals(pendingAsyncActionType)
                && pendingAsyncActionStepIndex == currentStepIndex
                && pendingAsyncActionIndex == actionIndex;
        if (samePendingAction) {
            if (isAsyncActionInProgress(type)) {
                tickDelay = 2;
                return true;
            }
            resetAsyncActionState();
            releaseResources();
            actionIndex++;
            applyBuiltinSequenceDelay();
            return true;
        }

        if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE) && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    I18n.format("msg.path.debug.execute_action", currentStepIndex, actionIndex,
                            actionData.getDescription())));
        }
        try {
            runWithExecutionContext(() -> action.accept(player));
            zszlScriptMod.LOGGER.info(I18n.format("log.path.execute_action"), actionIndex, currentStepIndex);
        } catch (Exception e) {
            recordFailureDetail("异步动作异常: " + actionData.getDescription() + " / " + e.getMessage());
            handleCurrentStepFailure("async_action_exception:" + type, e);
            return true;
        }

        if (isAsyncActionInProgress(type)) {
            pendingAsyncActionType = type;
            pendingAsyncActionStepIndex = currentStepIndex;
            pendingAsyncActionIndex = actionIndex;
            tickDelay = 2;
            return true;
        }

        resetAsyncActionState();
        releaseResources();
        actionIndex++;
        applyBuiltinSequenceDelay();
        return true;
    }

    private boolean isAsyncActionInProgress(String actionType) {
        if ("transferitemstowarehouse".equals(actionType)) {
            return ItemFilterHandler.isWarehouseTransferInProgress();
        }
        if ("move_inventory_items_to_chest_slots".equals(actionType)) {
            return ItemFilterHandler.isWarehouseTransferInProgress();
        }
        if ("spread_inventory_item".equals(actionType)) {
            return ItemSpreadHandler.isSpreadInProgress();
        }
        if ("stack_inventory_item".equals(actionType)) {
            return ItemSpreadHandler.isStackInProgress();
        }
        if ("pickup_nearby_items".equals(actionType)) {
            return NearbyItemPickupActionHandler.isRunning();
        }
        if ("warehouse_auto_deposit".equals(actionType)) {
            return WarehouseEventHandler.isAutoDepositRouteRunning();
        }
        return false;
    }

    private void resetWaitConditionState() {
        this.waitConditionRunning = false;
        this.waitConditionElapsedTicks = 0;
        this.waitConditionStartCapturedUpdateVersion = 0L;
        this.waitConditionStartCapturedRecaptureVersion = 0L;
        this.waitConditionStartPacketTextVersion = 0L;
        this.waitConditionStartPacketFieldTimestamp = 0L;
    }

    private void clearDeferredWaitState() {
        this.deferredWaitActionData = null;
        this.deferredWaitResumeActionIndex = -1;
        this.deferredWaitElapsedTicks = 0;
        this.deferredWaitStartCapturedUpdateVersion = 0L;
        this.deferredWaitStartCapturedRecaptureVersion = 0L;
        this.deferredWaitStartPacketTextVersion = 0L;
        this.deferredWaitStartPacketFieldTimestamp = 0L;
    }

    private void resetRepeatActionState() {
        this.repeatActionRunning = false;
        this.repeatActionStepIndex = -1;
        this.repeatActionHeaderIndex = -1;
        this.repeatActionBodyStartIndex = -1;
        this.repeatActionBodyEndIndex = -1;
        this.repeatActionRemainingLoops = 0;
        this.repeatActionIteration = 0;
        this.repeatActionLoopVarName = "loop_index";
    }

    private void resetIfElseState() {
        this.ifElseRunning = false;
        this.ifElseStepIndex = -1;
        this.ifElseThenEndIndex = -1;
        this.ifElseElseEndIndex = -1;
    }

    private void resetBranchBlockState() {
        this.branchBlockRunning = false;
        this.branchBlockStepIndex = -1;
        this.branchBlockSelectedEndIndex = -1;
        this.branchBlockFinalEndIndex = -1;
    }

    private void resetWhileConditionState() {
        runtimeVariables.remove(whileConditionLoopVarName);
        runtimeVariables.remove(whileConditionLoopVarName + "_remaining");
        this.whileConditionRunning = false;
        this.whileConditionStepIndex = -1;
        this.whileConditionHeaderIndex = -1;
        this.whileConditionBodyStartIndex = -1;
        this.whileConditionBodyEndIndex = -1;
        this.whileConditionIteration = 0;
        this.whileConditionMaxLoops = 0;
        this.whileConditionLoopVarName = "while_index";
    }

    private void resetForEachListState() {
        runtimeVariables.remove(forEachListItemVarName);
        runtimeVariables.remove(forEachListItemVarName + "_text");
        runtimeVariables.remove(forEachListIndexVarName);
        runtimeVariables.remove(forEachListIndexVarName + "_total");
        runtimeVariables.remove(forEachListIndexVarName + "_remaining");
        this.forEachListRunning = false;
        this.forEachListStepIndex = -1;
        this.forEachListBodyStartIndex = -1;
        this.forEachListBodyEndIndex = -1;
        this.forEachListIteration = 0;
        this.forEachListItems = new ArrayList<>();
        this.forEachListItemVarName = "item";
        this.forEachListIndexVarName = "item_index";
    }

    private void resetForEachPointState() {
        runtimeVariables.remove(forEachPointVarName);
        runtimeVariables.remove(forEachPointVarName + "_text");
        runtimeVariables.remove(forEachPointVarName + "_x");
        runtimeVariables.remove(forEachPointVarName + "_y");
        runtimeVariables.remove(forEachPointVarName + "_z");
        runtimeVariables.remove(forEachPointIndexVarName);
        runtimeVariables.remove(forEachPointIndexVarName + "_total");
        runtimeVariables.remove(forEachPointIndexVarName + "_remaining");
        this.forEachPointRunning = false;
        this.forEachPointStepIndex = -1;
        this.forEachPointBodyStartIndex = -1;
        this.forEachPointBodyEndIndex = -1;
        this.forEachPointIteration = 0;
        this.forEachPoints = new ArrayList<>();
        this.forEachPointVarName = "point";
        this.forEachPointIndexVarName = "point_index";
    }

    private void resetRetryBlockState() {
        runtimeVariables.remove(retryBlockAttemptVarName);
        runtimeVariables.remove(retryBlockAttemptVarName + "_success");
        runtimeVariables.remove(retryBlockAttemptVarName + "_exhausted");
        runtimeVariables.remove(retryBlockAttemptVarName + "_remaining");
        this.retryBlockRunning = false;
        this.retryBlockStepIndex = -1;
        this.retryBlockHeaderIndex = -1;
        this.retryBlockBodyStartIndex = -1;
        this.retryBlockBodyEndIndex = -1;
        this.retryBlockRetryRemaining = 0;
        this.retryBlockAttempt = 1;
        this.retryBlockDelayTicks = 0;
        this.retryBlockParams = null;
        this.retryBlockAttemptVarName = "retry_block";
        this.retryBlockDescription = "";
    }

    private void resetStepPathRetryMonitor() {
        this.currentStepIdleTicks = 0;
        this.currentStepIdleAnnounced = false;
        this.currentStepLastMovementX = Double.NaN;
        this.currentStepLastMovementY = Double.NaN;
        this.currentStepLastMovementZ = Double.NaN;
    }

    private void resetStepArrivalWaitState() {
        this.waitingForNavigationToFinishAtTarget = false;
    }

    private boolean stepHasGotoTarget(PathStep step) {
        return step != null && step.hasGotoTarget();
    }

    private void initializeStepPathRetryMonitor(EntityPlayerSP player) {
        resetStepPathRetryMonitor();
        if (player == null) {
            return;
        }
        this.currentStepLastMovementX = player.posX;
        this.currentStepLastMovementY = player.posY;
        this.currentStepLastMovementZ = player.posZ;
    }

    private void sendPathRetryMessage(String message, net.minecraft.util.text.TextFormatting color) {
        if (mc.player == null || message == null || message.trim().isEmpty()) {
            return;
        }
        mc.player.sendMessage(new TextComponentString(color + "[路径序列] " + message));
    }

    private void ensureSetVarDefaultValue(String varName, JsonObject rawParams, EntityPlayerSP player) {
        if (varName == null || varName.trim().isEmpty() || rawParams == null) {
            return;
        }
        if (runtimeVariables.containsInDeclaredScope(varName)) {
            return;
        }
        runtimeVariables.put(varName, LegacyActionRuntime.inferAssignedValueDefault(rawParams));
    }

    private String summarizeSetVarFailure(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return "表达式执行失败";
        }
        String message = error.getMessage().trim();
        int lineBreak = message.indexOf('\n');
        if (lineBreak >= 0) {
            message = message.substring(0, lineBreak).trim();
        }
        return message.isEmpty() ? "表达式执行失败" : message;
    }

    private void restoreSequenceBaseStatus() {
        if (currentSequence == null) {
            return;
        }
        String suffix = backgroundRunner ? " | 后台执行" : "";
        if (status == null || status.trim().isEmpty()) {
            setStatus(currentSequence.getName() + suffix);
            return;
        }
        if (!status.contains(" | ")) {
            return;
        }
        String base = status.split(" \\| ")[0];
        if (base == null || base.trim().isEmpty()) {
            base = currentSequence.getName();
        }
        setStatus(base + suffix);
    }

    private void resetSequenceRuntimeToInitialVariables() {
        runtimeVariables.clear();
        for (Map.Entry<String, Object> entry : initialSequenceVariables.entrySet()) {
            if (entry != null && entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                runtimeVariables.putSequence(entry.getKey().trim(), entry.getValue());
            }
        }
    }

    private boolean restartSequenceFromBeginning(String reason) {
        return restartSequenceFromBeginning(reason, "寻路重试已回到开头", "序列从头重试");
    }

    private boolean restartSequenceFromBeginning(String reason, String statusSuffix, String debugPrefix) {
        if (currentSequence == null || currentSequence.getSteps().isEmpty()) {
            markExecutionResult(false, reason == null ? "seek_retry_restart_failed" : reason);
            stopTracking();
            return true;
        }

        resetHuntState();
        resetHotbarUseActionState();
        resetAsyncActionState();
        resetWaitConditionState();
        clearDeferredWaitState();
        resetRepeatActionState();
        resetBranchBlockState();
        resetIfElseState();
        resetWhileConditionState();
        resetForEachListState();
        resetForEachPointState();
        resetRetryBlockState();
        releaseResources();

        this.currentStepIndex = 0;
        this.actionIndex = 0;
        this.atTarget = false;
        resetStepArrivalWaitState();
        this.tickDelay = 1;
        this.isPerformingExplicitDelay = false;
        this.explicitDelayNormalizeTo20Tps = false;
        this.explicitDelayRemainingBaselineTicks = 0.0D;
        this.currentStepRetryUsed = 0;
        this.noStopNavigationOnCompletion = false;
        this.currentDebugActionDescription = "";
        resetSequenceRuntimeToInitialVariables();
        runtimeVariables.enterStep(this.currentStepIndex);
        String safeDebugPrefix = debugPrefix == null || debugPrefix.trim().isEmpty() ? "序列回到开头" : debugPrefix.trim();
        recordDebugTrace(safeDebugPrefix + ": " + (reason == null ? "unknown" : reason));
        restartCurrentStepTarget(currentSequence.getSteps().get(this.currentStepIndex));
        String safeStatusSuffix = statusSuffix == null || statusSuffix.trim().isEmpty()
                ? "已回到开头"
                : statusSuffix.trim();
        setStatus(currentSequence.getName() + (backgroundRunner ? " | 后台执行" : "") + " | " + safeStatusSuffix);
        return true;
    }

    private boolean runRetryExhaustedSequence(EntityPlayerSP player, PathStep step) {
        String callerSequenceName = currentSequence == null ? "" : currentSequence.getName();
        String targetSequenceName = step == null ? "" : step.getRetryExhaustedSequenceName();
        if (targetSequenceName == null || targetSequenceName.trim().isEmpty()) {
            sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 寻路重试已耗尽，但未配置失败后执行序列，序列已停止。",
                    net.minecraft.util.text.TextFormatting.RED);
            markExecutionResult(false, "寻路重试耗尽，但未配置失败后执行序列");
            stopTracking();
            return true;
        }

        String target = targetSequenceName.trim();
        sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 寻路重试已耗尽，按设置执行序列: " + target + "。",
                net.minecraft.util.text.TextFormatting.RED);
        recordDebugTrace("步骤寻路重试耗尽后执行序列: step=" + currentStepIndex + ", target=" + target);
        markExecutionResult(false, "寻路重试耗尽，转执行序列: " + target);
        stopTracking();

        boolean started = PathSequenceManager.executeSequenceByConfiguredMode(target, player, callerSequenceName, null,
                step.getRetryExhaustedStepIndex(), step.getRetryExhaustedActionIndex());
        if (!started && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    net.minecraft.util.text.TextFormatting.RED + "[路径序列] 启动失败后执行序列失败: " + target));
        }
        return true;
    }

    private boolean handleCurrentStepPathRetry(EntityPlayerSP player, PathStep step) {
        if (player == null || step == null || !stepHasGotoTarget(step)) {
            resetStepPathRetryMonitor();
            return false;
        }

        int retryCount = step.getRetryCount();
        int timeoutSeconds = step.getPathRetryTimeoutSeconds();
        if (retryCount <= 0 || timeoutSeconds <= 0) {
            initializeStepPathRetryMonitor(player);
            return false;
        }

        if (Double.isNaN(currentStepLastMovementX)) {
            initializeStepPathRetryMonitor(player);
        }

        double dx = player.posX - currentStepLastMovementX;
        double dy = player.posY - currentStepLastMovementY;
        double dz = player.posZ - currentStepLastMovementZ;
        double movedSq = dx * dx + dy * dy + dz * dz;
        if (movedSq > PATH_RETRY_MOVEMENT_EPSILON_SQ) {
            boolean wasIdleAnnounced = currentStepIdleAnnounced;
            initializeStepPathRetryMonitor(player);
            if (wasIdleAnnounced) {
                restoreSequenceBaseStatus();
            }
            return false;
        }

        currentStepIdleTicks++;
        if (!currentStepIdleAnnounced && currentStepIdleTicks >= PATH_RETRY_NOTIFY_TICKS) {
            int remainingRetries = Math.max(0, retryCount - currentStepRetryUsed);
            sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 检测到人物原地不动，"
                    + timeoutSeconds + " 秒后将自动重发寻路命令，剩余重试 " + remainingRetries + " 次。",
                    net.minecraft.util.text.TextFormatting.YELLOW);
            currentStepIdleAnnounced = true;
            setStatus(currentSequence.getName() + (backgroundRunner ? " | 后台执行" : "") + " | 寻路停留检测中");
        }

        if (currentStepIdleTicks < timeoutSeconds * 20) {
            return false;
        }

        resetHotbarUseActionState();
        resetAsyncActionState();
        resetWaitConditionState();
        clearDeferredWaitState();
        resetRepeatActionState();
        resetBranchBlockState();
        resetIfElseState();
        resetWhileConditionState();
        resetForEachListState();
        resetForEachPointState();
        resetRetryBlockState();
        releaseResources();

        if (currentStepRetryUsed < retryCount) {
            currentStepRetryUsed++;
            this.actionIndex = 0;
            this.currentDebugActionDescription = "";
            sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 寻路停留超时，重新发送寻路命令。剩余重试 "
                    + Math.max(0, retryCount - currentStepRetryUsed) + " 次。",
                    net.minecraft.util.text.TextFormatting.GOLD);
            recordDebugTrace("步骤寻路重试: step=" + currentStepIndex + ", retry=" + currentStepRetryUsed + "/"
                    + retryCount);
            restartCurrentStepTarget(step);
            setStatus(currentSequence.getName() + (backgroundRunner ? " | 后台执行" : "") + " | 寻路重试 "
                    + currentStepRetryUsed + "/" + retryCount);
            tickDelay = 2;
            return true;
        }

        currentStepRetryUsed = 0;
        recordDebugTrace("步骤寻路重试耗尽: step=" + currentStepIndex);
        String exhaustedPolicy = step.getRetryExhaustedPolicy();
        if ("RESTART_SEQUENCE".equalsIgnoreCase(exhaustedPolicy)) {
            sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 寻路重试已耗尽，按设置从序列开头重新执行。",
                    net.minecraft.util.text.TextFormatting.RED);
            return restartSequenceFromBeginning("path_retry_exhausted_restart_sequence");
        }
        if ("RUN_SEQUENCE".equalsIgnoreCase(exhaustedPolicy)) {
            return runRetryExhaustedSequence(player, step);
        }

        sendPathRetryMessage("步骤 " + (currentStepIndex + 1) + " 寻路重试已耗尽，序列已停止。",
                net.minecraft.util.text.TextFormatting.RED);
        markExecutionResult(false, "path_retry_exhausted");
        stopTracking();
        return true;
    }

    private boolean inventoryHasMatchingItem(EntityPlayerSP player, JsonObject params,
            Set<Integer> selectedInventorySlots) {
        if (player == null) {
            return false;
        }
        List<String> itemFilterExpressions = InventoryItemFilterExpressionEngine.readExpressions(params);
        String itemName = params != null && params.has("itemName") ? params.get("itemName").getAsString() : "";
        String matchMode = params != null && params.has("matchMode") ? params.get("matchMode").getAsString() : "CONTAINS";
        List<String> requiredNbtTags = ItemFilterHandler.readTagFilters(params, "requiredNbtTags", "requiredNbtTagsText");
        String requiredNbtTagMatchMode = ItemFilterHandler.readRequiredNbtTagMatchMode(params);
        int minCount = params != null && params.has("count") ? params.get("count").getAsInt() : 1;
        String expected = itemName == null ? "" : itemName.trim().toLowerCase(java.util.Locale.ROOT);
        boolean hasExpressionCondition = itemFilterExpressions != null && !itemFilterExpressions.isEmpty();
        boolean hasNameCondition = !expected.isEmpty();
        boolean hasNbtCondition = requiredNbtTags != null && !requiredNbtTags.isEmpty();
        if (!hasExpressionCondition && !hasNameCondition && !hasNbtCondition) {
            return false;
        }
        int totalCount = 0;
        boolean exact = "EXACT".equalsIgnoreCase(matchMode);
        List<ItemStack> mainInventory = player.inventory.mainInventory;
        boolean restrictSlots = selectedInventorySlots != null && !selectedInventorySlots.isEmpty();
        for (int slotIndex = 0; slotIndex < mainInventory.size(); slotIndex++) {
            if (restrictSlots && !selectedInventorySlots.contains(slotIndex)) {
                continue;
            }
            ItemStack stack = mainInventory.get(slotIndex);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            boolean matched;
            if (hasExpressionCondition) {
                matched = false;
                for (String expression : itemFilterExpressions) {
                    if (expression == null || expression.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        if (InventoryItemFilterExpressionEngine.matches(stack, slotIndex, expression)) {
                            matched = true;
                            break;
                        }
                    } catch (Exception e) {
                        zszlScriptMod.LOGGER.warn("[legacy_path] 物品过滤表达式解析失败: {}", expression, e);
                    }
                }
            } else {
                String actual = net.minecraft.util.text.TextFormatting
                        .getTextWithoutFormattingCodes(stack.getDisplayName());
                if (actual == null) {
                    actual = stack.getDisplayName();
                }
                if (actual == null) {
                    continue;
                }
                actual = actual.trim().toLowerCase(java.util.Locale.ROOT);
                boolean matchedName = !hasNameCondition || (exact ? actual.equals(expected) : actual.contains(expected));
                matched = matchedName
                        && ItemFilterHandler.matchesRequiredNbtTags(stack, requiredNbtTags, requiredNbtTagMatchMode);
            }
            if (!matched) {
                continue;
            }
            totalCount += stack.getCount();
            if (totalCount >= Math.max(1, minCount)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> readMainInventorySlotSelection(JsonObject params) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        if (params == null) {
            return result;
        }
        if (params.has("inventorySlots") && params.get("inventorySlots").isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray("inventorySlots")) {
                try {
                    result.add(Math.max(0, element.getAsInt()));
                } catch (Exception ignored) {
                }
            }
            return result;
        }
        if (params.has("inventorySlots") && params.get("inventorySlots").isJsonPrimitive()) {
            for (String token : params.get("inventorySlots").getAsString().split("[,\\r\\n\\s]+")) {
                if (token == null || token.trim().isEmpty()) {
                    continue;
                }
                try {
                    result.add(Math.max(0, Integer.parseInt(token.trim())));
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String getCurrentGuiTitle() {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return "";
        }
        if (screen instanceof GuiChest && mc.player != null && mc.player.openContainer instanceof ContainerChest) {
            try {
                IInventory inv = ((ContainerChest) mc.player.openContainer).getLowerChestInventory();
                if (inv != null && inv.getDisplayName() != null) {
                    return inv.getDisplayName().getUnformattedText();
                }
            } catch (Exception ignored) {
            }
        }
        if (screen instanceof GuiMerchant) {
            return "Merchant";
        }
        return screen.getClass().getSimpleName();
    }

    private boolean isPlayerInArea(EntityPlayerSP player, JsonObject params) {
        if (player == null || params == null || !params.has("center")) {
            return false;
        }
        try {
            double radius = params.has("radius") ? params.get("radius").getAsDouble() : 3.0D;
            if (radius < 0.0D || Double.isNaN(radius) || Double.isInfinite(radius)) {
                return false;
            }

            com.google.gson.JsonArray center = params.getAsJsonArray("center");
            if (center.size() == 2) {
                double centerX = Math.floor(center.get(0).getAsDouble()) + 0.5D;
                double centerZ = Math.floor(center.get(1).getAsDouble()) + 0.5D;
                double dx = player.posX - centerX;
                double dz = player.posZ - centerZ;
                return dx * dx + dz * dz <= radius * radius;
            }
            if (center.size() < 3) {
                return false;
            }

            BlockPos centerPos = new BlockPos(center.get(0).getAsDouble(), center.get(1).getAsDouble(),
                    center.get(2).getAsDouble());
            return player.getDistanceSq(centerPos.getX() + 0.5D, centerPos.getY() + 0.5D, centerPos.getZ() + 0.5D) <= radius
                    * radius;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasNearbyMatchingEntity(EntityPlayerSP player, JsonObject params) {
        if (player == null || params == null) {
            return false;
        }
        String entityType = params.has("entityType") ? params.get("entityType").getAsString().trim() : "";
        String entityName = params.has("entityName") ? params.get("entityName").getAsString().trim() : "";
        if (entityType.isEmpty() && entityName.isEmpty()) {
            return false;
        }
        return collectMatchingNearbyEntities(player, params,
                params.has("radius") ? params.get("radius").getAsDouble() : 6.0D,
                false).size() >= readNearbyEntityMinCount(params);
    }

    private int readNearbyEntityMinCount(JsonObject params) {
        if (params == null) {
            return 1;
        }
        int minCount = params.has("minCount")
                ? params.get("minCount").getAsInt()
                : (params.has("count") ? params.get("count").getAsInt() : 1);
        return Math.max(1, minCount);
    }

    private List<EntityLivingBase> collectMatchingNearbyEntities(EntityPlayerSP player, JsonObject params,
            double defaultRadius, boolean allowEmptyAsAll) {
        List<EntityLivingBase> matches = new ArrayList<>();
        if (player == null || mc.world == null || params == null) {
            return matches;
        }
        String entityType = params.has("entityType") ? params.get("entityType").getAsString().trim() : "";
        String entityName = params.has("entityName") ? params.get("entityName").getAsString().trim() : "";
        double radius = params.has("radius") ? params.get("radius").getAsDouble() : defaultRadius;
        if (entityType.isEmpty() && entityName.isEmpty() && !allowEmptyAsAll) {
            return matches;
        }
        String expected = entityName.toLowerCase(Locale.ROOT);
        List<EntityLivingBase> entities = mc.world.getEntitiesWithinAABB(EntityLivingBase.class,
                new AxisAlignedBB(player.getPosition()).grow(radius));
        for (EntityLivingBase entity : entities) {
            if (entity == null || entity == player || !entity.isEntityAlive()) {
                continue;
            }
            if (!matchesNearbyEntityType(entity, entityType)) {
                continue;
            }
            String actual = entity.getName() == null ? "" : entity.getName().toLowerCase(Locale.ROOT);
            if (!expected.isEmpty() && !actual.contains(expected)) {
                continue;
            }
            matches.add(entity);
        }
        matches.sort(Comparator.comparingDouble(player::getDistanceSq));
        return matches;
    }

    private boolean matchesNearbyEntityType(EntityLivingBase entity, String entityType) {
        if (entity == null) {
            return false;
        }
        String normalized = entityType == null ? "" : entityType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || "all".equals(normalized) || "entity".equals(normalized)
                || "所有实体".equals(normalized)) {
            return true;
        }
        if ("player".equals(normalized) || "玩家".equals(normalized)) {
            return entity instanceof EntityPlayer;
        }
        if ("hostile".equals(normalized) || "monster".equals(normalized) || "mob".equals(normalized)
                || "敌对生物".equals(normalized) || "怪物".equals(normalized)) {
            return isHostileHuntTarget(entity);
        }
        if ("passive".equals(normalized) || "animal".equals(normalized)
                || "被动生物".equals(normalized) || "动物".equals(normalized)) {
            return isPassiveHuntTarget(entity);
        }
        return false;
    }

    private boolean hasMatchingHudText(JsonObject params) {
        String contains = params.has("contains") ? params.get("contains").getAsString() : "";
        boolean matchBlock = params.has("matchBlock") && params.get("matchBlock").getAsBoolean();
        String separator = params.has("separator") ? params.get("separator").getAsString() : " | ";
        if (contains == null) {
            contains = "";
        }
        contains = contains.trim();
        if (matchBlock) {
            for (com.zszl.zszlScriptMod.utils.HudTextScanner.TextBlock block : com.zszl.zszlScriptMod.utils.HudTextScanner.INSTANCE
                    .getProcessedTextBlocks()) {
                String text = block.getJoinedText(separator);
                if (contains.isEmpty() || text.contains(contains)) {
                    return true;
                }
            }
            return false;
        }
        for (com.zszl.zszlScriptMod.utils.HudTextScanner.CapturedText text : com.zszl.zszlScriptMod.utils.HudTextScanner.INSTANCE
                .getCurrentHudText()) {
            if (contains.isEmpty() || text.text.contains(contains)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMatchingScoreboardText(JsonObject params) {
        String expected = params != null && params.has("text") ? params.get("text").getAsString().trim() : "";
        if (expected.isEmpty() || mc == null || mc.world == null) {
            return false;
        }
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            return false;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return false;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(objective.getDisplayName());
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        int count = 0;
        for (Score score : scores) {
            if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) {
                continue;
            }
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(line);
            count++;
            if (count >= 15) {
                break;
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean hasMatchingRecentPacketText(JsonObject params) {
        String expected = params != null && params.has("packetText") ? params.get("packetText").getAsString().trim() : "";
        if (expected.isEmpty()) {
            return false;
        }
        for (String text : PacketCaptureHandler.getRecentPacketTextsSnapshot()) {
            if (text != null && text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private PacketFieldRuleManager.CapturedFieldSnapshot resolvePacketFieldSnapshot(JsonObject params) {
        String lookupMode = params != null && params.has("lookupMode")
                ? params.get("lookupMode").getAsString()
                : "LATEST_CAPTURE";
        if ("VARIABLE".equalsIgnoreCase(lookupMode)) {
            return null;
        }
        String fieldKey = params != null && params.has("fieldKey")
                ? params.get("fieldKey").getAsString().trim()
                : "";
        return PacketFieldRuleManager.getLatestCapturedField(fieldKey);
    }

    private Object resolvePacketFieldLookupValue(JsonObject params,
            PacketFieldRuleManager.CapturedFieldSnapshot snapshot) {
        String lookupMode = params != null && params.has("lookupMode")
                ? params.get("lookupMode").getAsString()
                : "LATEST_CAPTURE";
        if ("VARIABLE".equalsIgnoreCase(lookupMode)) {
            String fieldKey = params != null && params.has("fieldKey")
                    ? params.get("fieldKey").getAsString().trim()
                    : "";
            if (fieldKey.isEmpty()) {
                return null;
            }
            Object value = runtimeVariables.get(fieldKey);
            if (value == null) {
                value = ScopedRuntimeVariables.getGlobalValue(fieldKey);
            }
            return value;
        }
        return snapshot == null ? null : snapshot.getValue();
    }

    private String stringifyPacketFieldValue(PacketFieldRuleManager.CapturedFieldSnapshot snapshot, Object value) {
        if (snapshot != null && snapshot.getRawValue() != null && !snapshot.getRawValue().trim().isEmpty()) {
            return snapshot.getRawValue().trim();
        }
        return value == null ? "" : LegacyActionRuntime.stringifyValue(value);
    }

    private boolean matchesPacketFieldExpectation(JsonObject params,
            PacketFieldRuleManager.CapturedFieldSnapshot snapshot, Object value) {
        if (snapshot == null && value == null) {
            return false;
        }
        String expected = params != null && params.has("expectedValue")
                ? params.get("expectedValue").getAsString().trim()
                : "";
        if (expected.isEmpty()) {
            return true;
        }
        String actual = stringifyPacketFieldValue(snapshot, value);
        if (actual.trim().isEmpty()) {
            return false;
        }
        String matchMode = params != null && params.has("matchMode")
                ? params.get("matchMode").getAsString()
                : "CONTAINS";
        String normalizedActual = actual.toLowerCase(Locale.ROOT);
        String normalizedExpected = expected.toLowerCase(Locale.ROOT);
        if ("EXACT".equalsIgnoreCase(matchMode)) {
            return normalizedActual.equals(normalizedExpected);
        }
        return normalizedActual.contains(normalizedExpected);
    }

    private boolean hasMatchingPacketField(JsonObject params) {
        PacketFieldRuleManager.CapturedFieldSnapshot snapshot = resolvePacketFieldSnapshot(params);
        Object value = resolvePacketFieldLookupValue(params, snapshot);
        return matchesPacketFieldExpectation(params, snapshot, value);
    }

    private boolean hasMatchingBossbarText(JsonObject params) {
        String expected = params != null && params.has("text") ? params.get("text").getAsString().trim() : "";
        if (expected.isEmpty()) {
            return false;
        }
        String bossbarText = PacketCaptureHandler.getLatestBossbarText();
        return bossbarText != null && bossbarText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean hasMatchingGuiElement(JsonObject params) {
        if (params == null) {
            return false;
        }
        String elementType = params.has("elementType") ? params.get("elementType").getAsString() : "ANY";
        String locatorMode = params.has("guiElementLocatorMode") ? params.get("guiElementLocatorMode").getAsString() : "TEXT";
        String locatorText = params.has("locatorText") ? params.get("locatorText").getAsString() : "";
        String matchMode = params.has("locatorMatchMode") ? params.get("locatorMatchMode").getAsString() : "CONTAINS";

        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        GuiElementInspector.GuiElementInfo best = null;
        if ("PATH".equalsIgnoreCase(locatorMode)) {
            best = GuiElementInspector.findFirstByPath(locatorText, matchMode, resolveGuiElementTypes(elementType));
        } else {
            best = findGuiElementByText(snapshot, locatorText, matchMode, elementType);
        }
        if (best == null && "TITLE".equalsIgnoreCase(elementType)) {
            for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
                if (element != null && element.getType() == GuiElementInspector.ElementType.TITLE) {
                    return true;
                }
            }
        }
        return best != null;
    }

    private boolean hasMatchingPlayerList(JsonObject params) {
        List<PlayerListTriggerSupport.RuleEntry> entries = PlayerListTriggerSupport.readEntries(params);
        if (entries.isEmpty()) {
            return false;
        }
        PlayerListTriggerSupport.PlayerSnapshot snapshot = PlayerListTriggerSupport.captureSnapshot(mc);
        if (snapshot.players.isEmpty()) {
            return false;
        }
        JsonObject eventData = PlayerListTriggerSupport.buildTriggerEvent(
                new PlayerListTriggerSupport.PlayerSnapshot(Collections.<PlayerListTriggerSupport.PlayerRecord>emptyList(), ""),
                snapshot);
        return PlayerListTriggerSupport.matchesConfiguredPlayers(params, eventData);
    }

    private boolean evaluateConditionAction(EntityPlayerSP player, ActionData actionData) {
        if (actionData == null || actionData.type == null) {
            return false;
        }
        switch (actionData.type.toLowerCase(java.util.Locale.ROOT)) {
            case "condition_inventory_item":
            case "wait_until_inventory_item":
                return inventoryHasMatchingItem(player, actionData.params, readMainInventorySlotSelection(actionData.params));
            case "condition_gui_title":
            case "wait_until_gui_title":
                String title = actionData.params.has("title") ? actionData.params.get("title").getAsString() : "";
                String currentTitle = getCurrentGuiTitle();
                return !title.trim().isEmpty() && currentTitle != null && currentTitle.contains(title.trim());
            case "condition_scoreboard":
            case "wait_until_scoreboard":
                return hasMatchingScoreboardText(actionData.params);
            case "condition_packet_field":
            case "wait_until_packet_field":
                return hasMatchingPacketField(actionData.params);
            case "condition_packet_text":
                return hasMatchingRecentPacketText(actionData.params);
            case "condition_bossbar":
                return hasMatchingBossbarText(actionData.params);
            case "condition_gui_element":
            case "wait_until_gui_element":
                return hasMatchingGuiElement(actionData.params);
            case "condition_screen_region":
                return evaluateScreenRegionWait(actionData);
            case "condition_player_in_area":
            case "wait_until_player_in_area":
                return isPlayerInArea(player, actionData.params);
            case "condition_player_list":
            case "wait_until_player_list":
                return hasMatchingPlayerList(actionData.params);
            case "condition_entity_nearby":
            case "wait_until_entity_nearby":
                return hasNearbyMatchingEntity(player, actionData.params);
            case "wait_until_hud_text":
                return hasMatchingHudText(actionData.params);
            case "condition_expression":
            case "wait_until_expression":
                List<String> booleanExpressions = readBooleanExpressionList(actionData.params, "expressions", "expression");
                if (booleanExpressions.isEmpty()) {
                    return false;
                }
                for (String expression : booleanExpressions) {
                    try {
                        if (!LegacyActionRuntime.evaluateExpression(
                                expression,
                                actionData.params,
                                runtimeVariables,
                                player,
                                currentSequence,
                                currentStepIndex,
                                actionIndex)) {
                            return false;
                        }
                    } catch (Exception e) {
                        zszlScriptMod.LOGGER.warn("[legacy_path] 表达式条件解析失败: {}", expression, e);
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private void captureWaitStartVersions(ActionData actionData, boolean deferred) {
        String type = actionData == null || actionData.type == null ? ""
                : actionData.type.toLowerCase(java.util.Locale.ROOT);
        String capturedIdKey = actionData != null && actionData.params != null && actionData.params.has("capturedId")
                ? actionData.params.get("capturedId").getAsString().trim()
                : "";

        if (deferred) {
            deferredWaitStartCapturedUpdateVersion = 0L;
            deferredWaitStartCapturedRecaptureVersion = 0L;
            deferredWaitStartPacketTextVersion = 0L;
            deferredWaitStartPacketFieldTimestamp = 0L;
            if ("wait_until_captured_id".equals(type) && !capturedIdKey.isEmpty()) {
                deferredWaitStartCapturedUpdateVersion = CapturedIdRuleManager.getCapturedUpdateVersion(capturedIdKey);
                deferredWaitStartCapturedRecaptureVersion = CapturedIdRuleManager
                        .getCapturedRecaptureVersion(capturedIdKey);
            } else if ("wait_until_packet_text".equals(type)) {
                deferredWaitStartPacketTextVersion = PacketCaptureHandler.getRecentPacketTextVersion();
            } else if ("wait_until_packet_field".equals(type)) {
                PacketFieldRuleManager.CapturedFieldSnapshot snapshot = resolvePacketFieldSnapshot(actionData.params);
                deferredWaitStartPacketFieldTimestamp = snapshot == null ? 0L : snapshot.getTimestamp();
            }
            return;
        }

        waitConditionStartCapturedUpdateVersion = 0L;
        waitConditionStartCapturedRecaptureVersion = 0L;
        waitConditionStartPacketTextVersion = 0L;
        waitConditionStartPacketFieldTimestamp = 0L;
        if ("wait_until_captured_id".equals(type) && !capturedIdKey.isEmpty()) {
            waitConditionStartCapturedUpdateVersion = CapturedIdRuleManager.getCapturedUpdateVersion(capturedIdKey);
            waitConditionStartCapturedRecaptureVersion = CapturedIdRuleManager.getCapturedRecaptureVersion(capturedIdKey);
        } else if ("wait_until_packet_text".equals(type)) {
            waitConditionStartPacketTextVersion = PacketCaptureHandler.getRecentPacketTextVersion();
        } else if ("wait_until_packet_field".equals(type)) {
            PacketFieldRuleManager.CapturedFieldSnapshot snapshot = resolvePacketFieldSnapshot(actionData.params);
            waitConditionStartPacketFieldTimestamp = snapshot == null ? 0L : snapshot.getTimestamp();
        }
    }

    private boolean evaluateCapturedIdWait(ActionData actionData, long startUpdateVersion, long startRecaptureVersion) {
        if (actionData == null || actionData.params == null || !actionData.params.has("capturedId")) {
            return false;
        }
        String capturedIdKey = actionData.params.get("capturedId").getAsString().trim();
        if (capturedIdKey.isEmpty()) {
            return false;
        }
        String waitMode = actionData.params.has("waitMode")
                ? actionData.params.get("waitMode").getAsString()
                : "update";
        if ("recapture".equalsIgnoreCase(waitMode)) {
            return CapturedIdRuleManager.getCapturedRecaptureVersion(capturedIdKey) > startRecaptureVersion;
        }
        return CapturedIdRuleManager.getCapturedUpdateVersion(capturedIdKey) > startUpdateVersion;
    }

    private boolean evaluatePacketTextWait(ActionData actionData, long startPacketTextVersion) {
        if (actionData == null || actionData.params == null || !actionData.params.has("packetText")) {
            return false;
        }
        String packetText = actionData.params.get("packetText").getAsString();
        if (packetText == null || packetText.trim().isEmpty()) {
            return false;
        }
        if (PacketCaptureHandler.getRecentPacketTextVersion() <= startPacketTextVersion) {
            return false;
        }
        String expected = packetText.trim().toLowerCase(java.util.Locale.ROOT);
        for (String text : PacketCaptureHandler.getRecentPacketTextsSnapshot()) {
            if (text != null && text.toLowerCase(java.util.Locale.ROOT).contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluatePacketFieldWait(ActionData actionData, long startPacketFieldTimestamp) {
        if (actionData == null || actionData.params == null) {
            return false;
        }
        String lookupMode = actionData.params.has("lookupMode")
                ? actionData.params.get("lookupMode").getAsString()
                : "LATEST_CAPTURE";
        PacketFieldRuleManager.CapturedFieldSnapshot snapshot = resolvePacketFieldSnapshot(actionData.params);
        Object value = resolvePacketFieldLookupValue(actionData.params, snapshot);
        if (!"VARIABLE".equalsIgnoreCase(lookupMode)) {
            if (snapshot == null || snapshot.getTimestamp() <= startPacketFieldTimestamp) {
                return false;
            }
        }
        return matchesPacketFieldExpectation(actionData.params, snapshot, value);
    }

    private boolean evaluateCombinedWait(ActionData actionData, EntityPlayerSP player) {
        if (actionData == null || actionData.params == null) {
            return false;
        }
        String rawConditions = actionData.params.has("conditionsText")
                ? actionData.params.get("conditionsText").getAsString()
                : "";
        List<String> expressions = splitCombinedExpressions(rawConditions);
        if (expressions.isEmpty()) {
            return false;
        }

        boolean allMode = actionData.params.has("combinedMode")
                && "ALL".equalsIgnoreCase(actionData.params.get("combinedMode").getAsString());
        boolean matchedAny = false;
        for (String expression : expressions) {
            boolean result = false;
            try {
                result = LegacyActionRuntime.evaluateExpression(
                        expression,
                        actionData.params,
                        runtimeVariables,
                        player,
                        currentSequence,
                        currentStepIndex,
                        actionIndex);
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("[legacy_path] 组合等待表达式解析失败: {}", expression, e);
            }
            if (allMode && !result) {
                return false;
            }
            if (!allMode && result) {
                return true;
            }
            matchedAny |= result;
        }
        return allMode || matchedAny;
    }

    private boolean evaluateWaitCancelExpression(ActionData actionData, EntityPlayerSP player) {
        if (actionData == null || actionData.params == null || !actionData.params.has("cancelExpression")) {
            return false;
        }
        String expression = actionData.params.get("cancelExpression").getAsString();
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            return LegacyActionRuntime.evaluateExpression(
                    expression,
                    actionData.params,
                    runtimeVariables,
                    player,
                    currentSequence,
                    currentStepIndex,
                    actionIndex);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("[legacy_path] 等待取消表达式解析失败: {}", expression, e);
            return false;
        }
    }

    private List<String> splitCombinedExpressions(String rawText) {
        List<String> expressions = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return expressions;
        }
        for (String part : rawText.split("\\r?\\n|;|；")) {
            if (part != null && !part.trim().isEmpty()) {
                expressions.add(part.trim());
            }
        }
        return expressions;
    }

    private List<String> readBooleanExpressionList(JsonObject params, String arrayKey, String legacyKey) {
        List<String> expressions = new ArrayList<>();
        if (params == null) {
            return expressions;
        }
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            JsonArray array = params.getAsJsonArray(arrayKey);
            for (JsonElement element : array) {
                if (element != null && element.isJsonPrimitive()) {
                    String expression = element.getAsString();
                    if (expression != null && !expression.trim().isEmpty()) {
                        expressions.add(expression.trim());
                    }
                }
            }
            if (!expressions.isEmpty()) {
                return expressions;
            }
        }
        if (params.has(legacyKey) && params.get(legacyKey).isJsonPrimitive()) {
            String expression = params.get(legacyKey).getAsString();
            if (expression != null && !expression.trim().isEmpty()) {
                expressions.add(expression.trim());
            }
        }
        return expressions;
    }

    private boolean evaluateWaitAction(EntityPlayerSP player, ActionData actionData,
            long startUpdateVersion, long startRecaptureVersion, long startPacketTextVersion,
            long startPacketFieldTimestamp) {
        if (actionData == null || actionData.type == null) {
            return false;
        }
        String type = actionData.type.toLowerCase(java.util.Locale.ROOT);
        if ("wait_until_captured_id".equals(type)) {
            return evaluateCapturedIdWait(actionData, startUpdateVersion, startRecaptureVersion);
        }
        if ("wait_until_packet_text".equals(type)) {
            return evaluatePacketTextWait(actionData, startPacketTextVersion);
        }
        if ("wait_until_packet_field".equals(type)) {
            return evaluatePacketFieldWait(actionData, startPacketFieldTimestamp);
        }
        if ("wait_until_screen_region".equals(type)) {
            return evaluateScreenRegionWait(actionData);
        }
        if ("wait_until_gui_element".equals(type)) {
            return hasMatchingGuiElement(actionData.params);
        }
        if ("wait_combined".equals(type)) {
            return evaluateCombinedWait(actionData, player);
        }
        return evaluateConditionAction(player, actionData);
    }

    private int getWaitTimeoutTicks(ActionData actionData) {
        return actionData.params.has("timeoutTicks")
                ? Math.max(0, actionData.params.get("timeoutTicks").getAsInt())
                : 200;
    }

    private int getWaitTimeoutSkipCount(ActionData actionData) {
        return actionData.params.has("timeoutSkipCount")
                ? Math.max(0, actionData.params.get("timeoutSkipCount").getAsInt())
                : 0;
    }

    private boolean handleDeferredWaitAction(EntityPlayerSP player) {
        if (deferredWaitActionData == null) {
            return false;
        }

        int timeoutTicks = getWaitTimeoutTicks(deferredWaitActionData);
        boolean matched = evaluateWaitAction(player, deferredWaitActionData,
                deferredWaitStartCapturedUpdateVersion,
                deferredWaitStartCapturedRecaptureVersion,
                deferredWaitStartPacketTextVersion,
                deferredWaitStartPacketFieldTimestamp);
        boolean cancelled = evaluateWaitCancelExpression(deferredWaitActionData, player);
        boolean timedOut = timeoutTicks > 0 && deferredWaitElapsedTicks >= timeoutTicks;

        if (matched || timedOut || cancelled) {
            if (timedOut || cancelled) {
                String reasonDetail = cancelled
                        ? "延后等待被取消: " + deferredWaitActionData.getDescription()
                        : "延后等待超时: " + deferredWaitActionData.getDescription() + " / elapsed="
                                + deferredWaitElapsedTicks + " / timeout=" + timeoutTicks;
                recordFailureDetail(reasonDetail);
                actionIndex += getWaitTimeoutSkipCount(deferredWaitActionData);
            }
            clearDeferredWaitState();
            releaseResources();
            consumeDebugProgress("延后等待结束");
            return false;
        }

        deferredWaitElapsedTicks++;
        if (actionIndex >= deferredWaitResumeActionIndex) {
            tickDelay = 1;
            consumeDebugProgress("延后等待中 tick=" + deferredWaitElapsedTicks);
            return true;
        }
        return false;
    }

    private boolean handleConditionalOrWaitAction(EntityPlayerSP player, ActionData actionData) {
        String type = actionData.type == null ? "" : actionData.type.toLowerCase(java.util.Locale.ROOT);
        boolean matched = evaluateConditionAction(player, actionData);

        if (type.startsWith("condition_")) {
            int skipCount = actionData.params.has("skipCount") ? Math.max(0, actionData.params.get("skipCount").getAsInt()) : 1;
            currentDebugActionDescription = actionData.getDescription();
            recordDebugTrace("condition: " + currentDebugActionDescription + " -> " + (matched ? "true" : "false"));
            if (!matched) {
                recordFailureDetail("条件不满足: " + currentDebugActionDescription + " / skip=" + skipCount);
                actionIndex += skipCount + 1;
            } else {
                actionIndex++;
            }
            releaseResources();
            tickDelay = 2;
            resetWaitConditionState();
            consumeDebugProgress("条件判定完成");
            return true;
        }

        if (type.startsWith("wait_until_")) {
            int preExecuteCount = actionData.params.has("preExecuteCount")
                    ? Math.max(0, actionData.params.get("preExecuteCount").getAsInt())
                    : 0;
            currentDebugActionDescription = actionData.getDescription();

            if (preExecuteCount > 0 && deferredWaitActionData == null) {
                deferredWaitActionData = actionData;
                deferredWaitResumeActionIndex = actionIndex + 1 + preExecuteCount;
                deferredWaitElapsedTicks = 0;
                captureWaitStartVersions(actionData, true);
                recordDebugTrace("wait defer: " + currentDebugActionDescription + " / pre=" + preExecuteCount);
                actionIndex++;
                releaseResources();
                tickDelay = 1;
                resetWaitConditionState();
                consumeDebugProgress("进入延后等待");
                return true;
            }

            if (!waitConditionRunning) {
                waitConditionRunning = true;
                waitConditionElapsedTicks = 0;
                captureWaitStartVersions(actionData, false);
                recordDebugTrace("wait start: " + currentDebugActionDescription);
            }
            int timeoutTicks = getWaitTimeoutTicks(actionData);
            matched = evaluateWaitAction(player, actionData,
                    waitConditionStartCapturedUpdateVersion,
                    waitConditionStartCapturedRecaptureVersion,
                    waitConditionStartPacketTextVersion,
                    waitConditionStartPacketFieldTimestamp);
            boolean cancelled = evaluateWaitCancelExpression(actionData, player);
            boolean timedOut = timeoutTicks > 0 && waitConditionElapsedTicks >= timeoutTicks;

            if (matched || timedOut || cancelled) {
                String waitResult = matched ? "matched" : (cancelled ? "cancel" : "timeout");
                recordDebugTrace("wait finish: " + currentDebugActionDescription + " -> " + waitResult);
                if (!matched) {
                    String reasonDetail = cancelled
                            ? "等待被取消: " + currentDebugActionDescription
                            : "等待超时: " + currentDebugActionDescription + " / elapsed=" + waitConditionElapsedTicks
                                    + " / timeout=" + timeoutTicks;
                    recordFailureDetail(reasonDetail);
                }
                if (timedOut || cancelled) {
                    actionIndex += getWaitTimeoutSkipCount(actionData) + 1;
                } else {
                    actionIndex++;
                }
                releaseResources();
                tickDelay = 2;
                resetWaitConditionState();
                clearDeferredWaitState();
                consumeDebugProgress("等待动作结束");
                return true;
            }

            waitConditionElapsedTicks++;
            tickDelay = 1;
            consumeDebugProgress("等待中 tick=" + waitConditionElapsedTicks);
            return true;
        }

        return false;
    }

    private ActionData resolveRuntimeActionData(ActionData actionData, EntityPlayerSP player) {
        if (actionData == null) {
            return null;
        }
        runtimeVariables.beginAction(currentStepIndex, actionIndex);
        JsonObject resolvedParams = LegacyActionRuntime.resolveParams(
                actionData.params,
                runtimeVariables,
                player,
                currentSequence,
                currentStepIndex,
                actionIndex,
                getLiteralParamKeysForAction(actionData.type));
        return new ActionData(actionData.type, resolvedParams);
    }

    private static Set<String> getLiteralParamKeysForAction(String actionType) {
        String normalizedType = actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
        if ("set_var".equals(normalizedType)) {
            return Collections.singleton("name");
        }
        if ("label".equals(normalizedType)) {
            return Collections.singleton("labelName");
        }
        if ("goto_label".equals(normalizedType)) {
            return Collections.singleton("targetLabel");
        }
        if ("while_condition".equals(normalizedType)) {
            return Collections.singleton("loopVar");
        }
        if ("switch_var".equals(normalizedType)) {
            return Collections.singleton("sourceVar");
        }
        if ("for_each_list".equals(normalizedType)) {
            Set<String> literalKeys = new LinkedHashSet<>();
            literalKeys.add("sourceVar");
            literalKeys.add("itemVar");
            literalKeys.add("indexVar");
            return literalKeys;
        }
        if ("for_each_point".equals(normalizedType)) {
            Set<String> literalKeys = new LinkedHashSet<>();
            literalKeys.add("pointVar");
            literalKeys.add("indexVar");
            return literalKeys;
        }
        if ("retry_block".equals(normalizedType)) {
            return Collections.singleton("attemptVar");
        }
        if ("debug_print_var".equals(normalizedType)) {
            return Collections.singleton("varName");
        }
        if ("capture_nearby_entity".equals(normalizedType)
                || "capture_gui_title".equals(normalizedType)
                || "capture_inventory_slot".equals(normalizedType)
                || "capture_hotbar".equals(normalizedType)
                || "capture_entity_list".equals(normalizedType)
                || "capture_packet_field".equals(normalizedType)
                || "capture_gui_element".equals(normalizedType)
                || "capture_scoreboard".equals(normalizedType)
                || "capture_screen_region".equals(normalizedType)
                || "capture_block_at".equals(normalizedType)) {
            return Collections.singleton("varName");
        }
        return Collections.emptySet();
    }

    private boolean evaluateStructuredConditionExpressions(JsonObject params, EntityPlayerSP player) {
        if (params == null) {
            return false;
        }
        List<String> expressions = readBooleanExpressionList(params, "expressions", "expression");
        if (expressions.isEmpty()) {
            expressions = splitCombinedExpressions(params.has("conditionsText")
                    ? params.get("conditionsText").getAsString()
                    : "");
        }
        if (expressions.isEmpty()) {
            return false;
        }
        for (String expression : expressions) {
            try {
                if (!LegacyActionRuntime.evaluateExpression(
                        expression,
                        params,
                        runtimeVariables,
                        player,
                        currentSequence,
                        currentStepIndex,
                        actionIndex)) {
                    return false;
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("[legacy_path] 结构流控表达式解析失败: {}", expression, e);
                return false;
            }
        }
        return true;
    }

    private List<Object> normalizeForEachItems(Object source) {
        List<Object> items = new ArrayList<>();
        if (source == null) {
            return items;
        }
        if (source instanceof List<?>) {
            items.addAll((List<?>) source);
            return items;
        }
        if (source instanceof JsonArray) {
            JsonArray array = (JsonArray) source;
            for (JsonElement element : array) {
                if (element == null || element.isJsonNull()) {
                    items.add(null);
                } else if (element.isJsonPrimitive()) {
                    items.add(element.getAsString());
                } else {
                    items.add(element);
                }
            }
            return items;
        }
        if (source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            for (int i = 0; i < length; i++) {
                items.add(java.lang.reflect.Array.get(source, i));
            }
            return items;
        }
        if (source instanceof String) {
            String text = ((String) source).trim();
            if (text.isEmpty()) {
                return items;
            }
            for (String part : text.split("\\r?\\n|;|；|,")) {
                if (part != null && !part.trim().isEmpty()) {
                    items.add(part.trim());
                }
            }
            if (!items.isEmpty()) {
                return items;
            }
        }
        items.add(source);
        return items;
    }

    private void bindForEachListIterationVars() {
        if (!forEachListRunning || forEachListIteration < 0 || forEachListIteration >= forEachListItems.size()) {
            return;
        }
        Object item = forEachListItems.get(forEachListIteration);
        runtimeVariables.putLocal(forEachListItemVarName, item);
        runtimeVariables.putLocal(forEachListItemVarName + "_text", LegacyActionRuntime.stringifyValue(item));
        runtimeVariables.putLocal(forEachListIndexVarName, forEachListIteration);
        runtimeVariables.putLocal(forEachListIndexVarName + "_total", forEachListItems.size());
        runtimeVariables.putLocal(forEachListIndexVarName + "_remaining",
                Math.max(0, forEachListItems.size() - forEachListIteration - 1));
    }

    private List<BlockPos> parseForEachPoints(String rawPointsText) {
        List<BlockPos> points = new ArrayList<>();
        if (rawPointsText == null || rawPointsText.trim().isEmpty()) {
            return points;
        }
        for (String line : rawPointsText.split("\\r?\\n|;|；")) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            BlockPos pos = parseBlockPosValue(line.trim());
            if (pos != null) {
                points.add(pos);
            }
        }
        return points;
    }

    private BlockPos parseBlockPosValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        String[] parts = normalized.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = (int) Math.round(Double.parseDouble(parts[0].trim()));
            int y = (int) Math.round(Double.parseDouble(parts[1].trim()));
            int z = (int) Math.round(Double.parseDouble(parts[2].trim()));
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void bindForEachPointIterationVars() {
        if (!forEachPointRunning || forEachPointIteration < 0 || forEachPointIteration >= forEachPoints.size()) {
            return;
        }
        BlockPos point = forEachPoints.get(forEachPointIteration);
        String text = "[" + point.getX() + "," + point.getY() + "," + point.getZ() + "]";
        runtimeVariables.putLocal(forEachPointVarName, text);
        runtimeVariables.putLocal(forEachPointVarName + "_text", text);
        runtimeVariables.putLocal(forEachPointVarName + "_x", point.getX());
        runtimeVariables.putLocal(forEachPointVarName + "_y", point.getY());
        runtimeVariables.putLocal(forEachPointVarName + "_z", point.getZ());
        runtimeVariables.putLocal(forEachPointIndexVarName, forEachPointIteration);
        runtimeVariables.putLocal(forEachPointIndexVarName + "_total", forEachPoints.size());
        runtimeVariables.putLocal(forEachPointIndexVarName + "_remaining",
                Math.max(0, forEachPoints.size() - forEachPointIteration - 1));
    }

    private void bindRetryBlockVars(boolean success, boolean exhausted) {
        runtimeVariables.putLocal(retryBlockAttemptVarName, retryBlockAttempt);
        runtimeVariables.putLocal(retryBlockAttemptVarName + "_success", success);
        runtimeVariables.putLocal(retryBlockAttemptVarName + "_exhausted", exhausted);
        runtimeVariables.putLocal(retryBlockAttemptVarName + "_remaining", Math.max(0, retryBlockRetryRemaining));
    }

    private List<BranchBlockCase> parseBranchBlockCases(String rawCasesText) {
        List<BranchBlockCase> cases = new ArrayList<>();
        if (rawCasesText == null || rawCasesText.trim().isEmpty()) {
            return cases;
        }
        for (String line : rawCasesText.split("\\r?\\n|;|；")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int delimiter = trimmed.indexOf('=');
            if (delimiter < 0) {
                delimiter = trimmed.indexOf(':');
            }
            if (delimiter <= 0 || delimiter >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, delimiter).trim();
            String countText = trimmed.substring(delimiter + 1).trim();
            if (key.isEmpty() || countText.isEmpty()) {
                continue;
            }
            try {
                cases.add(new BranchBlockCase(key, Integer.parseInt(countText)));
            } catch (NumberFormatException ignored) {
            }
        }
        return cases;
    }

    private String resolveBranchBlockKey(ActionData resolvedActionData, EntityPlayerSP player) {
        String type = resolvedActionData == null || resolvedActionData.type == null
                ? ""
                : resolvedActionData.type.toLowerCase(Locale.ROOT);
        if ("switch_var".equals(type)) {
            String sourceVar = resolvedActionData.params.has("sourceVar")
                    ? resolvedActionData.params.get("sourceVar").getAsString().trim()
                    : "";
            Object value = runtimeVariables.get(sourceVar);
            if (value == null && !sourceVar.isEmpty()) {
                value = ScopedRuntimeVariables.getGlobalValue(sourceVar);
            }
            return LegacyActionRuntime.stringifyValue(value).trim();
        }
        if ("branch_table".equals(type)) {
            String expression = resolvedActionData.params.has("keyExpression")
                    ? resolvedActionData.params.get("keyExpression").getAsString().trim()
                    : "";
            if (expression.isEmpty()) {
                return "";
            }
            try {
                Object value = LegacyActionRuntime.evaluateValueExpression(
                        expression,
                        resolvedActionData.params,
                        runtimeVariables,
                        player,
                        currentSequence,
                        currentStepIndex,
                        actionIndex);
                return LegacyActionRuntime.stringifyValue(value).trim();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("[legacy_path] 分支键表达式解析失败: {}", expression, e);
                return "";
            }
        }
        return "";
    }

    private boolean handleRuntimeControlAction(EntityPlayerSP player,
            List<ActionData> actions,
            ActionData rawActionData,
            ActionData resolvedActionData) {
        if (resolvedActionData == null || resolvedActionData.type == null) {
            return false;
        }

        String type = resolvedActionData.type.toLowerCase(Locale.ROOT);

        if ("restart_sequence".equals(type)) {
            boolean handled = restartSequenceFromBeginning("restart_sequence_action",
                    "重头执行序列",
                    "控制流重头执行序列");
            consumeDebugProgress("控制流重头执行序列: restart_sequence -> 0");
            return handled;
        }

        if ("no_stop_navigation".equals(type)) {
            noStopNavigationOnCompletion = true;
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("序列完成时保留当前寻路");
            return true;
        }

        if ("set_var".equals(type)) {
            String varName = resolvedActionData.params.has("name") ? resolvedActionData.params.get("name").getAsString().trim()
                    : "";
            if (!varName.isEmpty()) {
                JsonObject rawParams = rawActionData == null || rawActionData.params == null
                        ? new JsonObject()
                        : rawActionData.params;
                ensureSetVarDefaultValue(varName, rawParams, player);
                try {
                    Object value = LegacyActionRuntime.resolveAssignedValue(
                            rawParams,
                            runtimeVariables,
                            player,
                            currentSequence,
                            currentStepIndex,
                            actionIndex);
                    runtimeVariables.put(varName, value);
                    recordDebugTrace("set_var: " + varName + " = " + LegacyActionRuntime.stringifyValue(value));
                } catch (Exception e) {
                    Object fallbackValue = LegacyActionRuntime.inferAssignedValueDefault(rawParams);
                    runtimeVariables.put(varName, fallbackValue);
                    String detail = summarizeSetVarFailure(e);
                    sendPathRetryMessage("设置变量失败: " + varName + "，已使用默认值 "
                            + LegacyActionRuntime.stringifyValue(fallbackValue) + "；" + detail,
                            net.minecraft.util.text.TextFormatting.RED);
                    recordDebugTrace("set_var 失败: " + varName + " -> " + detail
                            + " ; fallback=" + LegacyActionRuntime.stringifyValue(fallbackValue));
                    zszlScriptMod.LOGGER.warn("[legacy_path] set_var failed: seq={}, step={}, action={}, var={}",
                            currentSequence == null ? "" : currentSequence.getName(),
                            currentStepIndex, actionIndex, varName, e);
                }
            }
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("控制流推进: set_var");
            return true;
        }

        if ("label".equals(type)) {
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("控制流标签: " + (resolvedActionData.params.has("labelName")
                    ? resolvedActionData.params.get("labelName").getAsString()
                    : ""));
            return true;
        }

        if ("goto_action".equals(type)) {
            int targetActionIndex = resolvedActionData.params.has("targetActionIndex")
                    ? Math.max(0, resolvedActionData.params.get("targetActionIndex").getAsInt())
                    : actionIndex + 1;
            actionIndex = Math.min(Math.max(0, targetActionIndex), Math.max(0, actions.size()));
            tickDelay = 2;
            resetWaitConditionState();
            consumeDebugProgress("控制流跳转: goto_action -> " + actionIndex);
            return true;
        }

        if ("goto_label".equals(type)) {
            String targetLabel = resolvedActionData.params.has("targetLabel")
                    ? resolvedActionData.params.get("targetLabel").getAsString().trim()
                    : "";
            int targetActionIndex = findLabelActionIndex(actions, targetLabel);
            if (targetActionIndex < 0) {
                recordDebugTrace("goto_label 未找到: " + targetLabel);
                actionIndex++;
            } else {
                actionIndex = targetActionIndex;
            }
            tickDelay = 2;
            resetWaitConditionState();
            consumeDebugProgress("控制流跳转: goto_label -> "
                    + (targetLabel.isEmpty() ? "(空)" : targetLabel)
                    + " @ " + actionIndex);
            return true;
        }

        if ("if_else".equals(type)) {
            int thenCount = resolvedActionData.params.has("thenCount")
                    ? Math.max(0, resolvedActionData.params.get("thenCount").getAsInt())
                    : 1;
            int elseCount = resolvedActionData.params.has("elseCount")
                    ? Math.max(0, resolvedActionData.params.get("elseCount").getAsInt())
                    : 0;
            boolean matched = evaluateStructuredConditionExpressions(resolvedActionData.params, player);
            int headerIndex = actionIndex;
            int thenStartIndex = headerIndex + 1;
            int thenEndIndex = Math.min(actions.size() - 1, thenStartIndex + thenCount - 1);
            int elseEndIndex = Math.min(actions.size() - 1, thenStartIndex + thenCount + elseCount - 1);
            if (matched) {
                if (thenCount <= 0 || thenStartIndex >= actions.size()) {
                    actionIndex = Math.min(actions.size(), elseEndIndex + 1);
                } else {
                    ifElseRunning = elseCount > 0;
                    ifElseStepIndex = currentStepIndex;
                    ifElseThenEndIndex = thenEndIndex;
                    ifElseElseEndIndex = elseEndIndex;
                    actionIndex = thenStartIndex;
                }
            } else {
                resetIfElseState();
                actionIndex = Math.min(actions.size(), thenStartIndex + thenCount);
            }
            tickDelay = 1;
            consumeDebugProgress("条件块分支: " + (matched ? "true" : "false"));
            return true;
        }

        if ("switch_var".equals(type) || "branch_table".equals(type)) {
            List<BranchBlockCase> branchCases = parseBranchBlockCases(resolvedActionData.params.has("casesText")
                    ? resolvedActionData.params.get("casesText").getAsString()
                    : "");
            int defaultCount = resolvedActionData.params.has("defaultCount")
                    ? Math.max(0, resolvedActionData.params.get("defaultCount").getAsInt())
                    : 0;
            if (branchCases.isEmpty()) {
                actionIndex++;
                tickDelay = 2;
                return true;
            }

            String actualKey = resolveBranchBlockKey(resolvedActionData, player);
            int matchedCaseIndex = -1;
            int matchedStartOffset = 0;
            int matchedBodyCount = 0;
            int offset = 0;
            for (int i = 0; i < branchCases.size(); i++) {
                BranchBlockCase branchCase = branchCases.get(i);
                if (matchedCaseIndex < 0 && branchCase.key.equalsIgnoreCase(actualKey)) {
                    matchedCaseIndex = i;
                    matchedStartOffset = offset;
                    matchedBodyCount = branchCase.bodyCount;
                }
                offset += branchCase.bodyCount;
            }

            int headerIndex = actionIndex;
            int defaultStartOffset = offset;
            int totalBodyCount = offset + defaultCount;
            int finalEndIndex = Math.min(actions.size() - 1, headerIndex + totalBodyCount);
            int selectedStartIndex = matchedCaseIndex >= 0
                    ? headerIndex + 1 + matchedStartOffset
                    : headerIndex + 1 + defaultStartOffset;
            int selectedBodyCount = matchedCaseIndex >= 0 ? matchedBodyCount : defaultCount;
            int selectedEndIndex = selectedStartIndex + selectedBodyCount - 1;

            resetBranchBlockState();
            if (selectedBodyCount <= 0 || selectedStartIndex >= actions.size()) {
                actionIndex = Math.min(actions.size(), finalEndIndex + 1);
                tickDelay = 1;
                consumeDebugProgress("分支表未执行分支");
                return true;
            }

            branchBlockRunning = selectedEndIndex < finalEndIndex;
            branchBlockStepIndex = currentStepIndex;
            branchBlockSelectedEndIndex = Math.min(actions.size() - 1, selectedEndIndex);
            branchBlockFinalEndIndex = finalEndIndex;
            actionIndex = selectedStartIndex;
            tickDelay = 1;
            consumeDebugProgress("分支表命中: key=" + actualKey);
            return true;
        }

        if ("while_condition".equals(type)) {
            int bodyCount = resolvedActionData.params.has("bodyCount")
                    ? Math.max(0, resolvedActionData.params.get("bodyCount").getAsInt())
                    : 0;
            int maxLoops = resolvedActionData.params.has("maxLoops")
                    ? Math.max(0, resolvedActionData.params.get("maxLoops").getAsInt())
                    : 0;
            String loopVarName = resolvedActionData.params.has("loopVar")
                    ? resolvedActionData.params.get("loopVar").getAsString().trim()
                    : "while_index";
            int headerIndex = actionIndex;
            int bodyStartIndex = headerIndex + 1;
            int bodyEndIndex = Math.min(actions.size() - 1, bodyStartIndex + bodyCount - 1);

            if (!whileConditionRunning || whileConditionHeaderIndex != headerIndex
                    || whileConditionStepIndex != currentStepIndex) {
                whileConditionRunning = true;
                whileConditionStepIndex = currentStepIndex;
                whileConditionHeaderIndex = headerIndex;
                whileConditionBodyStartIndex = bodyStartIndex;
                whileConditionBodyEndIndex = bodyEndIndex;
                whileConditionIteration = 0;
                whileConditionMaxLoops = maxLoops;
                whileConditionLoopVarName = loopVarName.isEmpty() ? "while_index" : loopVarName;
            }

            if (bodyCount <= 0 || bodyStartIndex >= actions.size()) {
                resetWhileConditionState();
                actionIndex++;
                tickDelay = 2;
                return true;
            }
            if (whileConditionMaxLoops > 0 && whileConditionIteration >= whileConditionMaxLoops) {
                actionIndex = Math.min(actions.size(), whileConditionBodyEndIndex + 1);
                resetWhileConditionState();
                tickDelay = 1;
                consumeDebugProgress("条件循环达到上限");
                return true;
            }

            runtimeVariables.putLocal(whileConditionLoopVarName, whileConditionIteration);
            runtimeVariables.putLocal(whileConditionLoopVarName + "_remaining",
                    whileConditionMaxLoops > 0
                            ? Math.max(0, whileConditionMaxLoops - whileConditionIteration - 1)
                            : 0);
            boolean matched = evaluateStructuredConditionExpressions(resolvedActionData.params, player);
            if (!matched) {
                actionIndex = Math.min(actions.size(), whileConditionBodyEndIndex + 1);
                resetWhileConditionState();
                tickDelay = 1;
                consumeDebugProgress("条件循环退出");
                return true;
            }
            actionIndex = whileConditionBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("进入条件循环体: " + whileConditionIteration);
            return true;
        }

        if ("for_each_point".equals(type)) {
            int bodyCount = resolvedActionData.params.has("bodyCount")
                    ? Math.max(0, resolvedActionData.params.get("bodyCount").getAsInt())
                    : 0;
            String pointVar = resolvedActionData.params.has("pointVar")
                    ? resolvedActionData.params.get("pointVar").getAsString().trim()
                    : "point";
            String indexVar = resolvedActionData.params.has("indexVar")
                    ? resolvedActionData.params.get("indexVar").getAsString().trim()
                    : "point_index";
            int bodyStartIndex = actionIndex + 1;
            int bodyEndIndex = Math.min(actions.size() - 1, bodyStartIndex + bodyCount - 1);
            List<BlockPos> points = parseForEachPoints(resolvedActionData.params.has("pointsText")
                    ? resolvedActionData.params.get("pointsText").getAsString()
                    : "");
            if (bodyCount <= 0 || bodyStartIndex >= actions.size() || points.isEmpty()) {
                resetForEachPointState();
                actionIndex = Math.min(actions.size(), bodyStartIndex + Math.max(0, bodyCount));
                tickDelay = 1;
                consumeDebugProgress("点列表为空或无循环体");
                return true;
            }

            forEachPointRunning = true;
            forEachPointStepIndex = currentStepIndex;
            forEachPointBodyStartIndex = bodyStartIndex;
            forEachPointBodyEndIndex = bodyEndIndex;
            forEachPointIteration = 0;
            forEachPoints = points;
            forEachPointVarName = pointVar.isEmpty() ? "point" : pointVar;
            forEachPointIndexVarName = indexVar.isEmpty() ? "point_index" : indexVar;
            bindForEachPointIterationVars();
            actionIndex = bodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("进入点列表遍历: count=" + points.size());
            return true;
        }

        if ("for_each_list".equals(type)) {
            int bodyCount = resolvedActionData.params.has("bodyCount")
                    ? Math.max(0, resolvedActionData.params.get("bodyCount").getAsInt())
                    : 0;
            String sourceVar = resolvedActionData.params.has("sourceVar")
                    ? resolvedActionData.params.get("sourceVar").getAsString().trim()
                    : "";
            String itemVar = resolvedActionData.params.has("itemVar")
                    ? resolvedActionData.params.get("itemVar").getAsString().trim()
                    : "item";
            String indexVar = resolvedActionData.params.has("indexVar")
                    ? resolvedActionData.params.get("indexVar").getAsString().trim()
                    : "item_index";
            int bodyStartIndex = actionIndex + 1;
            int bodyEndIndex = Math.min(actions.size() - 1, bodyStartIndex + bodyCount - 1);

            Object sourceValue = runtimeVariables.get(sourceVar);
            if (sourceValue == null && !sourceVar.isEmpty()) {
                sourceValue = ScopedRuntimeVariables.getGlobalValue(sourceVar);
            }
            List<Object> items = normalizeForEachItems(sourceValue);
            if (bodyCount <= 0 || bodyStartIndex >= actions.size() || items.isEmpty()) {
                resetForEachListState();
                actionIndex = Math.min(actions.size(), bodyStartIndex + Math.max(0, bodyCount));
                tickDelay = 1;
                consumeDebugProgress("遍历列表为空或无循环体");
                return true;
            }

            forEachListRunning = true;
            forEachListStepIndex = currentStepIndex;
            forEachListBodyStartIndex = bodyStartIndex;
            forEachListBodyEndIndex = bodyEndIndex;
            forEachListIteration = 0;
            forEachListItems = items;
            forEachListItemVarName = itemVar.isEmpty() ? "item" : itemVar;
            forEachListIndexVarName = indexVar.isEmpty() ? "item_index" : indexVar;
            bindForEachListIterationVars();
            actionIndex = bodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("进入列表遍历: count=" + items.size());
            return true;
        }

        if ("retry_block".equals(type)) {
            int bodyCount = resolvedActionData.params.has("bodyCount")
                    ? Math.max(0, resolvedActionData.params.get("bodyCount").getAsInt())
                    : 0;
            int retryCount = resolvedActionData.params.has("retryCount")
                    ? Math.max(0, resolvedActionData.params.get("retryCount").getAsInt())
                    : 0;
            int retryDelayTicks = resolvedActionData.params.has("retryDelayTicks")
                    ? Math.max(0, resolvedActionData.params.get("retryDelayTicks").getAsInt())
                    : 0;
            String attemptVar = resolvedActionData.params.has("attemptVar")
                    ? resolvedActionData.params.get("attemptVar").getAsString().trim()
                    : "retry_block";
            if (bodyCount <= 0 || actionIndex + 1 >= actions.size()) {
                actionIndex++;
                tickDelay = 2;
                return true;
            }
            retryBlockRunning = true;
            retryBlockStepIndex = currentStepIndex;
            retryBlockHeaderIndex = actionIndex;
            retryBlockBodyStartIndex = actionIndex + 1;
            retryBlockBodyEndIndex = Math.min(actions.size() - 1, retryBlockBodyStartIndex + bodyCount - 1);
            retryBlockRetryRemaining = retryCount;
            retryBlockAttempt = 1;
            retryBlockDelayTicks = retryDelayTicks;
            retryBlockParams = resolvedActionData.params;
            retryBlockAttemptVarName = attemptVar.isEmpty() ? "retry_block" : attemptVar;
            retryBlockDescription = resolvedActionData.getDescription();
            bindRetryBlockVars(false, false);
            actionIndex = retryBlockBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("进入重试块: retry=" + retryCount);
            return true;
        }

        if ("debug_print_var".equals(type)) {
            String varName = resolvedActionData.params.has("varName")
                    ? resolvedActionData.params.get("varName").getAsString().trim()
                    : "";
            Object value = null;
            if (!varName.isEmpty()) {
                value = runtimeVariables.get(varName);
                if (value == null) {
                    value = ScopedRuntimeVariables.getGlobalValue(varName);
                }
            }
            sendPathRetryMessage("调试变量 " + varName + " = " + LegacyActionRuntime.stringifyValue(value),
                    net.minecraft.util.text.TextFormatting.AQUA);
            recordDebugTrace("debug_print_var: " + varName + " = " + LegacyActionRuntime.stringifyValue(value));
            actionIndex++;
            tickDelay = 2;
            return true;
        }

        if ("debug_print_nearby_entities".equals(type)) {
            double radius = resolvedActionData.params.has("radius")
                    ? resolvedActionData.params.get("radius").getAsDouble()
                    : 8.0D;
            List<EntityLivingBase> entities = collectMatchingNearbyEntities(player, resolvedActionData.params, radius, true);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < entities.size() && i < 5; i++) {
                EntityLivingBase entity = entities.get(i);
                names.add(entity.getName() + "@" + String.format(Locale.ROOT, "%.1f", Math.sqrt(player.getDistanceSq(entity))));
            }
            sendPathRetryMessage("附近实体 count=" + entities.size() + " top=" + names,
                    net.minecraft.util.text.TextFormatting.AQUA);
            recordDebugTrace("debug_print_nearby_entities: count=" + entities.size() + " top=" + names);
            actionIndex++;
            tickDelay = 2;
            return true;
        }

        if ("debug_print_gui_summary".equals(type)) {
            GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
            String message = "GUI=" + snapshot.getScreenSimpleName()
                    + " title=" + snapshot.getTitle()
                    + " elements=" + snapshot.getElements().size();
            sendPathRetryMessage(message, net.minecraft.util.text.TextFormatting.AQUA);
            recordDebugTrace("debug_print_gui_summary: " + message);
            actionIndex++;
            tickDelay = 2;
            return true;
        }

        if ("skip_actions".equals(type)) {
            int skipCount = resolvedActionData.params.has("count")
                    ? Math.max(0, resolvedActionData.params.get("count").getAsInt())
                    : 1;
            actionIndex = Math.min(Math.max(0, actionIndex + skipCount + 1), Math.max(0, actions.size()));
            tickDelay = 2;
            resetWaitConditionState();
            consumeDebugProgress("控制流跳过动作: skip_actions -> " + actionIndex);
            return true;
        }

        if ("skip_steps".equals(type)) {
            int skipCount = resolvedActionData.params.has("count")
                    ? Math.max(0, resolvedActionData.params.get("count").getAsInt())
                    : 0;
            List<PathStep> sequenceSteps = currentSequence == null
                    ? java.util.Collections.emptyList()
                    : currentSequence.getSteps();
            int targetStepIndex = Math.min(Math.max(0, currentStepIndex + skipCount + 1), sequenceSteps.size());
            currentStepIndex = targetStepIndex;
            actionIndex = 0;
            atTarget = false;
            currentStepRetryUsed = 0;
            resetStepPathRetryMonitor();
            releaseResources();
            resetAsyncActionState();
            resetRepeatActionState();
            resetWaitConditionState();
            clearDeferredWaitState();
            runtimeVariables.enterStep(currentStepIndex);
            if (currentStepIndex < sequenceSteps.size()) {
                restartCurrentStepTarget(sequenceSteps.get(currentStepIndex));
            } else {
                if (shouldStopNavigationOnSequenceCompletion()) {
                    stopNavigation();
                }
                resetStepPathRetryMonitor();
            }
            tickDelay = 2;
            consumeDebugProgress("控制流跳过步骤: skip_steps -> " + currentStepIndex);
            return true;
        }

        if ("repeat_actions".equals(type)) {
            int bodyCount = resolvedActionData.params.has("bodyCount")
                    ? Math.max(0, resolvedActionData.params.get("bodyCount").getAsInt())
                    : 0;
            int loopCount = resolvedActionData.params.has("count")
                    ? Math.max(0, resolvedActionData.params.get("count").getAsInt())
                    : 0;
            String loopVarName = resolvedActionData.params.has("loopVar")
                    ? resolvedActionData.params.get("loopVar").getAsString().trim()
                    : "loop_index";

            if (bodyCount <= 0 || loopCount <= 0 || actionIndex + 1 >= actions.size()) {
                actionIndex++;
                tickDelay = 2;
                return true;
            }

            repeatActionRunning = true;
            repeatActionStepIndex = currentStepIndex;
            repeatActionHeaderIndex = actionIndex;
            repeatActionBodyStartIndex = actionIndex + 1;
            repeatActionBodyEndIndex = Math.min(actions.size() - 1, repeatActionBodyStartIndex + bodyCount - 1);
            repeatActionRemainingLoops = loopCount;
            repeatActionIteration = 0;
            repeatActionLoopVarName = loopVarName.isEmpty() ? "loop_index" : loopVarName;
            runtimeVariables.putLocal(repeatActionLoopVarName, repeatActionIteration);
            runtimeVariables.putLocal(repeatActionLoopVarName + "_remaining", repeatActionRemainingLoops);
            actionIndex = repeatActionBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("进入循环体: repeat_actions");
            return true;
        }

        if ("capture_nearby_entity".equals(type)) {
            captureNearbyEntity(player, resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_nearby_entity");
            return true;
        }

        if ("capture_gui_title".equals(type)) {
            String varName = resolvedActionData.params.has("varName")
                    ? resolvedActionData.params.get("varName").getAsString().trim()
                    : "gui_title";
            if (!varName.isEmpty()) {
                runtimeVariables.put(varName, getCurrentGuiTitle());
                recordDebugTrace("capture_gui_title -> " + varName);
            }
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_gui_title");
            return true;
        }

        if ("capture_inventory_slot".equals(type)) {
            captureInventorySlot(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_inventory_slot");
            return true;
        }

        if ("capture_hotbar".equals(type)) {
            captureHotbar(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_hotbar");
            return true;
        }

        if ("capture_entity_list".equals(type)) {
            captureEntityList(player, resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_entity_list");
            return true;
        }

        if ("capture_packet_field".equals(type)) {
            capturePacketField(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_packet_field");
            return true;
        }

        if ("capture_scoreboard".equals(type)) {
            captureScoreboard(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_scoreboard");
            return true;
        }

        if ("capture_screen_region".equals(type)) {
            captureScreenRegion(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_screen_region");
            return true;
        }

        if ("capture_gui_element".equals(type)) {
            captureGuiElement(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_gui_element");
            return true;
        }

        if ("capture_block_at".equals(type)) {
            captureBlockAt(resolvedActionData.params);
            actionIndex++;
            tickDelay = 2;
            consumeDebugProgress("采集完成: capture_block_at");
            return true;
        }

        return false;
    }

    private int findLabelActionIndex(List<ActionData> actions, String targetLabel) {
        if (actions == null || actions.isEmpty() || targetLabel == null || targetLabel.trim().isEmpty()) {
            return -1;
        }
        String expected = targetLabel.trim();
        for (int i = 0; i < actions.size(); i++) {
            ActionData action = actions.get(i);
            if (action == null || action.type == null || action.params == null
                    || !"label".equalsIgnoreCase(action.type)) {
                continue;
            }
            String actual = action.params.has("labelName") ? action.params.get("labelName").getAsString().trim() : "";
            if (expected.equalsIgnoreCase(actual)) {
                return i;
            }
        }
        return -1;
    }

    private boolean handleIfElseBoundary(List<ActionData> actions) {
        if (!ifElseRunning || ifElseStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= ifElseThenEndIndex) {
            return false;
        }
        actionIndex = Math.min(Math.max(0, ifElseElseEndIndex + 1), Math.max(0, actions.size()));
        resetIfElseState();
        tickDelay = 1;
        consumeDebugProgress("条件块跳过 false 分支");
        return true;
    }

    private boolean handleBranchBlockBoundary(List<ActionData> actions) {
        if (!branchBlockRunning || branchBlockStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= branchBlockSelectedEndIndex) {
            return false;
        }
        actionIndex = Math.min(Math.max(0, branchBlockFinalEndIndex + 1), Math.max(0, actions.size()));
        resetBranchBlockState();
        tickDelay = 1;
        consumeDebugProgress("分支块跳过剩余分支");
        return true;
    }

    private boolean handleWhileConditionBoundary(List<ActionData> actions) {
        if (!whileConditionRunning || whileConditionStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= whileConditionBodyEndIndex) {
            return false;
        }
        whileConditionIteration++;
        actionIndex = whileConditionHeaderIndex;
        tickDelay = 1;
        consumeDebugProgress("条件循环回到头部: " + whileConditionIteration);
        return true;
    }

    private boolean handleForEachPointBoundary(List<ActionData> actions) {
        if (!forEachPointRunning || forEachPointStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= forEachPointBodyEndIndex) {
            return false;
        }
        if (forEachPointIteration + 1 < forEachPoints.size()) {
            forEachPointIteration++;
            bindForEachPointIterationVars();
            actionIndex = forEachPointBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("点列表遍历下一项: " + forEachPointIteration);
            return true;
        }
        resetForEachPointState();
        return false;
    }

    private boolean handleForEachListBoundary(List<ActionData> actions) {
        if (!forEachListRunning || forEachListStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= forEachListBodyEndIndex) {
            return false;
        }
        if (forEachListIteration + 1 < forEachListItems.size()) {
            forEachListIteration++;
            bindForEachListIterationVars();
            actionIndex = forEachListBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("列表遍历下一项: " + forEachListIteration);
            return true;
        }
        resetForEachListState();
        return false;
    }

    private boolean handleRetryBlockBoundary(List<ActionData> actions, EntityPlayerSP player) {
        if (!retryBlockRunning || retryBlockStepIndex != currentStepIndex) {
            return false;
        }
        if (actionIndex <= retryBlockBodyEndIndex) {
            return false;
        }

        boolean success = evaluateStructuredConditionExpressions(retryBlockParams, player);
        bindRetryBlockVars(success, false);
        if (success) {
            recordDebugTrace("重试块成功: attempt=" + retryBlockAttempt + " / " + retryBlockDescription);
            resetRetryBlockState();
            return false;
        }

        if (retryBlockRetryRemaining > 0) {
            retryBlockRetryRemaining--;
            retryBlockAttempt++;
            bindRetryBlockVars(false, false);
            actionIndex = retryBlockBodyStartIndex;
            tickDelay = Math.max(0, retryBlockDelayTicks);
            consumeDebugProgress("重试块进入下一轮: attempt=" + retryBlockAttempt);
            recordDebugTrace("重试块失败，准备重试: attempt=" + retryBlockAttempt + " / remaining="
                    + retryBlockRetryRemaining);
            return true;
        }

        bindRetryBlockVars(false, true);
        recordFailureDetail("重试块耗尽: " + retryBlockDescription + " / attempt=" + retryBlockAttempt);
        recordDebugTrace("重试块耗尽: attempt=" + retryBlockAttempt + " / " + retryBlockDescription);
        resetRetryBlockState();
        return false;
    }

    private boolean handleRepeatActionBoundary(List<ActionData> actions) {
        if (!repeatActionRunning || repeatActionStepIndex != currentStepIndex) {
            return false;
        }

        if (actionIndex <= repeatActionBodyEndIndex) {
            return false;
        }

        if (repeatActionRemainingLoops > 1) {
            repeatActionRemainingLoops--;
            repeatActionIteration++;
            runtimeVariables.putLocal(repeatActionLoopVarName, repeatActionIteration);
            runtimeVariables.putLocal(repeatActionLoopVarName + "_remaining", repeatActionRemainingLoops);
            actionIndex = repeatActionBodyStartIndex;
            tickDelay = 1;
            consumeDebugProgress("循环下一轮: " + repeatActionIteration);
            return true;
        }

        runtimeVariables.remove(repeatActionLoopVarName + "_remaining");
        resetRepeatActionState();
        return false;
    }

    private void captureNearbyEntity(EntityPlayerSP player, JsonObject params) {
        String varName = params.has("varName") ? params.get("varName").getAsString().trim() : "entity";

        if (varName.isEmpty()) {
            return;
        }

        runtimeVariables.put(varName + "_found", false);
        if (player == null || mc.world == null) {
            return;
        }

        List<EntityLivingBase> entities = collectMatchingNearbyEntities(player, params, 6.0D, true);
        EntityLivingBase best = entities.isEmpty() ? null : entities.get(0);

        if (best == null) {
            recordDebugTrace("capture_nearby_entity -> " + varName + " 未找到");
            return;
        }

        runtimeVariables.put(varName + "_found", true);
        runtimeVariables.put(varName + "_name", best.getName());
        runtimeVariables.put(varName + "_id", best.getEntityId());
        runtimeVariables.put(varName + "_x", best.posX);
        runtimeVariables.put(varName + "_y", best.posY);
        runtimeVariables.put(varName + "_z", best.posZ);
        runtimeVariables.put(varName + "_block_x", best.getPosition().getX());
        runtimeVariables.put(varName + "_block_y", best.getPosition().getY());
        runtimeVariables.put(varName + "_block_z", best.getPosition().getZ());
        recordDebugTrace("capture_nearby_entity -> " + varName + " = " + best.getName());
    }

    private void captureBlockAt(JsonObject params) {
        String varName = params.has("varName") ? params.get("varName").getAsString().trim() : "block";
        if (varName.isEmpty()) {
            return;
        }

        runtimeVariables.put(varName + "_found", false);
        if (mc.world == null || params == null || !params.has("pos") || !params.get("pos").isJsonArray()) {
            return;
        }

        try {
            com.google.gson.JsonArray pos = params.getAsJsonArray("pos");
            BlockPos blockPos = new BlockPos(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
            IBlockState state = mc.world.getBlockState(blockPos);
            Block block = state == null ? null : state.getBlock();
            if (block == null) {
                return;
            }

            runtimeVariables.put(varName + "_found", true);
            runtimeVariables.put(varName + "_name", block.getLocalizedName());
            runtimeVariables.put(varName + "_registry", String.valueOf(block.getRegistryName()));
            runtimeVariables.put(varName + "_x", blockPos.getX());
            runtimeVariables.put(varName + "_y", blockPos.getY());
            runtimeVariables.put(varName + "_z", blockPos.getZ());
            recordDebugTrace("capture_block_at -> " + varName + " = " + block.getLocalizedName());
        } catch (Exception ignored) {
        }
    }

    private void captureInventorySlot(JsonObject params) {
        String varName = params != null && params.has("varName") ? params.get("varName").getAsString().trim() : "slot";
        String area = params != null && params.has("slotArea") ? params.get("slotArea").getAsString() : "MAIN";
        area = area == null || area.trim().isEmpty() ? "MAIN" : area.trim().toUpperCase(Locale.ROOT);
        int slotIndex = params != null && params.has("slotIndex") ? Math.max(0, params.get("slotIndex").getAsInt()) : 0;
        if (varName.isEmpty() || mc.player == null || mc.player.inventory == null) {
            return;
        }

        clearCapturedInventorySlotVariables(varName);
        List<Integer> selectedSlotIndices = readCaptureInventorySlotIndices(params, area);
        if ("MAIN".equals(area) && !selectedSlotIndices.isEmpty()) {
            captureInventorySlots(varName, area, selectedSlotIndices);
            recordDebugTrace("capture_inventory_slot -> " + varName + " slots=" + selectedSlotIndices.size());
            return;
        }

        ItemStack stack = resolveInventoryStack(area, slotIndex);
        Map<String, Object> snapshot = buildCapturedStackSnapshot(area, slotIndex, stack);
        runtimeVariables.put(varName, snapshot);
        writeCapturedStack(varName, stack);
        runtimeVariables.put(varName + "_slot_count", 1);
        runtimeVariables.put(varName + "_filled_count", stack != null && !stack.isEmpty() ? 1 : 0);
        runtimeVariables.put(varName + "_slot_indices", Collections.singletonList(slotIndex));
        runtimeVariables.put(varName + "_slots", Collections.singletonList(snapshot));
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> registries = new ArrayList<>();
        List<String> nbts = new ArrayList<>();
        appendCapturedStackSummary(stack, names, counts, registries, nbts);
        runtimeVariables.put(varName + "_names", names);
        runtimeVariables.put(varName + "_counts", counts);
        runtimeVariables.put(varName + "_registries", registries);
        runtimeVariables.put(varName + "_nbts", nbts);
        runtimeVariables.put(varName + "_slot_index", slotIndex);
        runtimeVariables.put(varName + "_slot_area", area);
        recordDebugTrace("capture_inventory_slot -> " + varName + " = "
                + (stack == null || stack.isEmpty() ? "(empty)" : stack.getDisplayName()));
    }

    private List<Integer> readCaptureInventorySlotIndices(JsonObject params, String area) {
        List<Integer> indices = new ArrayList<>();
        if (params == null || !"MAIN".equalsIgnoreCase(area) || !params.has("slotIndices")) {
            return indices;
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        JsonElement raw = params.get("slotIndices");
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement element : raw.getAsJsonArray()) {
                try {
                    int index = element.getAsInt();
                    if (index >= 0 && index < 36) {
                        unique.add(index);
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (raw != null && raw.isJsonPrimitive()) {
            for (String token : raw.getAsString().split("[,\\r\\n\\s]+")) {
                if (token == null || token.trim().isEmpty()) {
                    continue;
                }
                try {
                    int index = Integer.parseInt(token.trim());
                    if (index >= 0 && index < 36) {
                        unique.add(index);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        indices.addAll(unique);
        Collections.sort(indices);
        return indices;
    }

    private void captureInventorySlots(String varName, String area, List<Integer> slotIndices) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        List<ItemStack> capturedStacks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> registries = new ArrayList<>();
        List<String> nbts = new ArrayList<>();
        int filled = 0;

        for (Integer slotIndexValue : slotIndices) {
            if (slotIndexValue == null) {
                continue;
            }
            int slotIndex = slotIndexValue.intValue();
            ItemStack stack = resolveInventoryStack(area, slotIndex);
            capturedStacks.add(stack);
            snapshots.add(buildCapturedStackSnapshot(area, slotIndex, stack));
            appendCapturedStackSummary(stack, names, counts, registries, nbts);
            if (stack != null && !stack.isEmpty()) {
                filled++;
            }
        }

        runtimeVariables.put(varName, snapshots);
        ItemStack firstStack = capturedStacks.isEmpty() ? ItemStack.EMPTY : capturedStacks.get(0);
        writeCapturedStack(varName, firstStack);
        runtimeVariables.put(varName + "_slots", snapshots);
        runtimeVariables.put(varName + "_found", filled > 0);
        runtimeVariables.put(varName + "_slot_count", snapshots.size());
        runtimeVariables.put(varName + "_filled_count", filled);
        runtimeVariables.put(varName + "_slot_indices", new ArrayList<>(slotIndices));
        runtimeVariables.put(varName + "_slot_area", area);
        runtimeVariables.put(varName + "_slot_index", slotIndices.isEmpty() ? -1 : slotIndices.get(0));
        runtimeVariables.put(varName + "_names", names);
        runtimeVariables.put(varName + "_counts", counts);
        runtimeVariables.put(varName + "_registries", registries);
        runtimeVariables.put(varName + "_nbts", nbts);

        for (Integer slotIndexValue : slotIndices) {
            if (slotIndexValue == null) {
                continue;
            }
            writeCapturedStack(varName + "_" + slotIndexValue, resolveInventoryStack(area, slotIndexValue));
        }
    }

    private void captureHotbar(JsonObject params) {
        String varName = params != null && params.has("varName") ? params.get("varName").getAsString().trim() : "hotbar";
        if (varName.isEmpty() || mc.player == null || mc.player.inventory == null) {
            return;
        }

        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> registries = new ArrayList<>();
        List<String> nbts = new ArrayList<>();
        List<Integer> slotIndices = new ArrayList<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();
        int filled = 0;
        for (int i = 0; i < 9; i++) {
            slotIndices.add(i);
            ItemStack stack = getMainInventoryStack(i);
            snapshots.add(buildCapturedStackSnapshot("HOTBAR", i, stack));
            if (stack != null && !stack.isEmpty()) {
                filled++;
            }
            appendCapturedStackSummary(stack, names, counts, registries, nbts);
        }
        int selectedIndex = Math.max(0, Math.min(8, mc.player.inventory.currentItem));
        runtimeVariables.put(varName, snapshots);
        runtimeVariables.put(varName + "_slots", snapshots);
        runtimeVariables.put(varName + "_found", filled > 0);
        runtimeVariables.put(varName + "_count", 9);
        runtimeVariables.put(varName + "_slot_indices", slotIndices);
        runtimeVariables.put(varName + "_selected_index", selectedIndex);
        runtimeVariables.put(varName + "_selected_slot", selectedIndex + 1);
        ItemStack selectedStack = getMainInventoryStack(selectedIndex);
        runtimeVariables.put(varName + "_selected_data", buildCapturedStackSnapshot("HOTBAR", selectedIndex, selectedStack));
        runtimeVariables.put(varName + "_filled_count", filled);
        runtimeVariables.put(varName + "_names", names);
        runtimeVariables.put(varName + "_counts", counts);
        runtimeVariables.put(varName + "_registries", registries);
        runtimeVariables.put(varName + "_nbts", nbts);
        for (int i = 0; i < 9; i++) {
            writeCapturedStack(varName + "_" + i, getMainInventoryStack(i));
        }
        writeCapturedStack(varName + "_selected", selectedStack);
        recordDebugTrace("capture_hotbar -> " + varName + " filled=" + filled);
    }

    private ItemStack getMainInventoryStack(int slotIndex) {
        if (mc.player == null || mc.player.inventory == null || mc.player.inventory.mainInventory == null
                || slotIndex < 0 || slotIndex >= mc.player.inventory.mainInventory.size()) {
            return ItemStack.EMPTY;
        }
        return mc.player.inventory.mainInventory.get(slotIndex);
    }

    private Map<String, Object> buildCapturedStackSnapshot(String area, int slotIndex, ItemStack stack) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        boolean filled = stack != null && !stack.isEmpty();
        snapshot.put("slot_area", area == null ? "" : area.toUpperCase(Locale.ROOT));
        snapshot.put("slot_index", slotIndex);
        snapshot.put("found", filled);
        snapshot.put("name", filled ? stack.getDisplayName() : "");
        snapshot.put("count", filled ? stack.getCount() : 0);
        snapshot.put("registry", filled ? String.valueOf(stack.getItem().getRegistryName()) : "");
        snapshot.put("damage", filled ? stack.getItemDamage() : 0);
        snapshot.put("has_nbt", filled && stack.hasTagCompound());
        snapshot.put("nbt", filled && stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
        return snapshot;
    }

    private void appendCapturedStackSummary(ItemStack stack, List<String> names, List<Integer> counts,
            List<String> registries, List<String> nbts) {
        if (stack != null && !stack.isEmpty()) {
            names.add(stack.getDisplayName());
            counts.add(stack.getCount());
            registries.add(String.valueOf(stack.getItem().getRegistryName()));
            nbts.add(stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
        } else {
            names.add("");
            counts.add(0);
            registries.add("");
            nbts.add("");
        }
    }

    private void clearCapturedInventorySlotVariables(String varName) {
        for (int slotIndex = 0; slotIndex < 36; slotIndex++) {
            String prefix = varName + "_" + slotIndex;
            runtimeVariables.remove(prefix + "_found");
            runtimeVariables.remove(prefix + "_name");
            runtimeVariables.remove(prefix + "_count");
            runtimeVariables.remove(prefix + "_registry");
            runtimeVariables.remove(prefix + "_damage");
            runtimeVariables.remove(prefix + "_has_nbt");
            runtimeVariables.remove(prefix + "_nbt");
        }
    }

    private void captureEntityList(EntityPlayerSP player, JsonObject params) {
        String varName = params != null && params.has("varName")
                ? params.get("varName").getAsString().trim()
                : "entities";
        double radius = params != null && params.has("radius") ? params.get("radius").getAsDouble() : 8.0D;
        int maxCount = params != null && params.has("maxCount") ? Math.max(1, params.get("maxCount").getAsInt()) : 16;
        if (varName.isEmpty()) {
            return;
        }

        runtimeVariables.put(varName + "_found", false);
        runtimeVariables.put(varName + "_count", 0);
        runtimeVariables.put(varName + "_list", new ArrayList<>());
        runtimeVariables.put(varName + "_names", new ArrayList<>());
        runtimeVariables.put(varName + "_distances", new ArrayList<>());
        runtimeVariables.put(varName + "_categories", new ArrayList<>());
        if (player == null || mc.world == null) {
            return;
        }
        List<EntityLivingBase> entities = collectMatchingNearbyEntities(player, params, radius, true);

        List<Map<String, Object>> list = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        int playerCount = 0;
        int hostileCount = 0;
        int passiveCount = 0;
        Map<String, Object> nearestPlayer = null;
        Map<String, Object> nearestHostile = null;
        for (EntityLivingBase entity : entities) {
            String actualName = entity.getName() == null ? "" : entity.getName();
            double distance = Math.sqrt(player.getDistanceSq(entity));
            String category = describeEntityCategory(entity);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", actualName);
            entry.put("id", entity.getEntityId());
            entry.put("type", entity.getClass().getSimpleName());
            entry.put("category", category);
            entry.put("distance", distance);
            entry.put("x", entity.posX);
            entry.put("y", entity.posY);
            entry.put("z", entity.posZ);
            list.add(entry);

            names.add(actualName);
            ids.add(entity.getEntityId());
            types.add(entity.getClass().getSimpleName());
            categories.add(category);
            distances.add(distance);
            if ("PLAYER".equalsIgnoreCase(category)) {
                playerCount++;
                if (nearestPlayer == null) {
                    nearestPlayer = entry;
                }
            } else if ("HOSTILE".equalsIgnoreCase(category)) {
                hostileCount++;
                if (nearestHostile == null) {
                    nearestHostile = entry;
                }
            } else if ("PASSIVE".equalsIgnoreCase(category)) {
                passiveCount++;
            }
            if (list.size() >= maxCount) {
                break;
            }
        }

        runtimeVariables.put(varName + "_found", !list.isEmpty());
        runtimeVariables.put(varName + "_count", list.size());
        runtimeVariables.put(varName + "_list", list);
        runtimeVariables.put(varName + "_names", names);
        runtimeVariables.put(varName + "_ids", ids);
        runtimeVariables.put(varName + "_types", types);
        runtimeVariables.put(varName + "_categories", categories);
        runtimeVariables.put(varName + "_distances", distances);
        runtimeVariables.put(varName + "_radius", radius);
        runtimeVariables.put(varName + "_max_count", maxCount);
        runtimeVariables.put(varName + "_player_count", playerCount);
        runtimeVariables.put(varName + "_hostile_count", hostileCount);
        runtimeVariables.put(varName + "_passive_count", passiveCount);
        if (!list.isEmpty()) {
            Map<String, Object> first = list.get(0);
            runtimeVariables.put(varName + "_nearest_name", first.get("name"));
            runtimeVariables.put(varName + "_nearest_id", first.get("id"));
            runtimeVariables.put(varName + "_nearest_type", first.get("type"));
            runtimeVariables.put(varName + "_nearest_category", first.get("category"));
            runtimeVariables.put(varName + "_nearest_distance", first.get("distance"));
        } else {
            runtimeVariables.put(varName + "_nearest_name", "");
            runtimeVariables.put(varName + "_nearest_id", 0);
            runtimeVariables.put(varName + "_nearest_type", "");
            runtimeVariables.put(varName + "_nearest_category", "");
            runtimeVariables.put(varName + "_nearest_distance", 0D);
        }
        if (nearestPlayer != null) {
            runtimeVariables.put(varName + "_nearest_player_name", nearestPlayer.get("name"));
            runtimeVariables.put(varName + "_nearest_player_distance", nearestPlayer.get("distance"));
        } else {
            runtimeVariables.put(varName + "_nearest_player_name", "");
            runtimeVariables.put(varName + "_nearest_player_distance", 0D);
        }
        if (nearestHostile != null) {
            runtimeVariables.put(varName + "_nearest_hostile_name", nearestHostile.get("name"));
            runtimeVariables.put(varName + "_nearest_hostile_distance", nearestHostile.get("distance"));
        } else {
            runtimeVariables.put(varName + "_nearest_hostile_name", "");
            runtimeVariables.put(varName + "_nearest_hostile_distance", 0D);
        }
        recordDebugTrace("capture_entity_list -> " + varName + " count=" + list.size());
    }

    private void capturePacketField(JsonObject params) {
        String varName = params != null && params.has("varName")
                ? params.get("varName").getAsString().trim()
                : "packet_field";
        String lookupMode = params != null && params.has("lookupMode")
                ? params.get("lookupMode").getAsString()
                : "LATEST_CAPTURE";
        String fieldKey = params != null && params.has("fieldKey")
                ? params.get("fieldKey").getAsString().trim()
                : "";
        String fallbackValue = params != null && params.has("fallbackValue")
                ? params.get("fallbackValue").getAsString()
                : "";
        if (varName.isEmpty()) {
            return;
        }

        PacketFieldRuleManager.CapturedFieldSnapshot snapshot = null;
        Object value = null;
        if ("VARIABLE".equalsIgnoreCase(lookupMode)) {
            value = runtimeVariables.get(fieldKey);
            if (value == null && fieldKey != null && !fieldKey.trim().isEmpty()) {
                value = ScopedRuntimeVariables.getGlobalValue(fieldKey.trim());
            }
        } else {
            snapshot = PacketFieldRuleManager.getLatestCapturedField(fieldKey);
            if (snapshot != null) {
                value = snapshot.getValue();
            }
        }

        boolean found = value != null || snapshot != null;
        if (!found && fallbackValue != null && !fallbackValue.trim().isEmpty()) {
            value = fallbackValue;
        }

        runtimeVariables.put(varName + "_found", found);
        runtimeVariables.put(varName + "_lookup_mode", lookupMode == null ? "" : lookupMode.toUpperCase(Locale.ROOT));
        runtimeVariables.put(varName + "_field_key", fieldKey);
        runtimeVariables.put(varName + "_value", value);
        runtimeVariables.put(varName, value);
        runtimeVariables.put(varName + "_value_text", value == null ? "" : LegacyActionRuntime.stringifyValue(value));
        if (snapshot == null) {
            runtimeVariables.put(varName + "_rule_name", "");
            runtimeVariables.put(varName + "_variable_name", "");
            runtimeVariables.put(varName + "_scope", "");
            runtimeVariables.put(varName + "_channel", "");
            runtimeVariables.put(varName + "_direction", "");
            runtimeVariables.put(varName + "_source", "");
            runtimeVariables.put(varName + "_packet_class", "");
            runtimeVariables.put(varName + "_raw", "");
            runtimeVariables.put(varName + "_timestamp", 0L);
        } else {
            runtimeVariables.put(varName + "_rule_name", snapshot.getRuleName());
            runtimeVariables.put(varName + "_variable_name", snapshot.getVariableName());
            runtimeVariables.put(varName + "_scope", snapshot.getScope());
            runtimeVariables.put(varName + "_channel", snapshot.getChannel());
            runtimeVariables.put(varName + "_direction", snapshot.getDirection());
            runtimeVariables.put(varName + "_source", snapshot.getSource());
            runtimeVariables.put(varName + "_packet_class", snapshot.getPacketClassName());
            runtimeVariables.put(varName + "_raw", snapshot.getRawValue());
            runtimeVariables.put(varName + "_timestamp", snapshot.getTimestamp());
            runtimeVariables.put(varName + "_snapshot", snapshot.toMap());
        }
        recordDebugTrace("capture_packet_field -> " + varName + " = "
                + (value == null ? "(null)" : LegacyActionRuntime.stringifyValue(value)));
    }

    private void captureScoreboard(JsonObject params) {
        String varName = params != null && params.has("varName")
                ? params.get("varName").getAsString().trim()
                : "scoreboard";
        int lineIndex = params != null && params.has("lineIndex") ? params.get("lineIndex").getAsInt() : -1;
        if (varName.isEmpty() || mc.player == null || mc.world == null) {
            return;
        }

        runtimeVariables.put(varName + "_found", false);
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            return;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return;
        }

        Collection<Score> scores = scoreboard.getSortedScores(objective);
        List<String> lines = new ArrayList<>();
        for (Score score : scores) {
            if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) {
                continue;
            }
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            lines.add(line);
            if (lines.size() >= 15) {
                break;
            }
        }

        runtimeVariables.put(varName + "_found", true);
        runtimeVariables.put(varName + "_title", objective.getDisplayName());
        runtimeVariables.put(varName + "_lines", lines);
        runtimeVariables.put(varName + "_line_count", lines.size());
        runtimeVariables.put(varName + "_joined", String.join("\n", lines));
        runtimeVariables.put(varName + "_selected_index", lineIndex);
        boolean selectedFound = lineIndex >= 0 && lineIndex < lines.size();
        runtimeVariables.put(varName + "_selected_found", selectedFound);
        runtimeVariables.put(varName + "_selected_line", selectedFound ? lines.get(lineIndex) : "");
        recordDebugTrace("capture_scoreboard -> " + varName + " lines=" + lines.size());
    }

    private void captureScreenRegion(JsonObject params) {
        String varName = params != null && params.has("varName")
                ? params.get("varName").getAsString().trim()
                : "vision_region";
        int[] rect = parseVisionRegionRect(params);
        if (varName.isEmpty() || rect == null) {
            return;
        }

        ScreenVisionUtils.RegionMetrics metrics = ScreenVisionUtils.analyzeRegion(rect[0], rect[1], rect[2], rect[3]);
        runtimeVariables.put(varName + "_found", metrics.isFound());
        runtimeVariables.put(varName + "_x", rect[0]);
        runtimeVariables.put(varName + "_y", rect[1]);
        runtimeVariables.put(varName + "_width", rect[2]);
        runtimeVariables.put(varName + "_height", rect[3]);
        runtimeVariables.put(varName + "_avg_r", metrics.getAverageR());
        runtimeVariables.put(varName + "_avg_g", metrics.getAverageG());
        runtimeVariables.put(varName + "_avg_b", metrics.getAverageB());
        runtimeVariables.put(varName + "_avg_hex", metrics.getAverageHex());
        runtimeVariables.put(varName + "_center_r", metrics.getCenterR());
        runtimeVariables.put(varName + "_center_g", metrics.getCenterG());
        runtimeVariables.put(varName + "_center_b", metrics.getCenterB());
        runtimeVariables.put(varName + "_center_hex", metrics.getCenterHex());
        runtimeVariables.put(varName + "_brightness", metrics.getBrightness());
        runtimeVariables.put(varName + "_edge_density", metrics.getEdgeDensity());
        recordDebugTrace("capture_screen_region -> " + varName + " avg=" + metrics.getAverageHex());
    }

    private boolean evaluateScreenRegionWait(ActionData actionData) {
        if (actionData == null || actionData.params == null) {
            return false;
        }
        int[] rect = parseVisionRegionRect(actionData.params);
        if (rect == null) {
            return false;
        }
        String compareMode = actionData.params.has("visionCompareMode")
                ? actionData.params.get("visionCompareMode").getAsString()
                : "AVERAGE_COLOR";
        if ("TEMPLATE".equalsIgnoreCase(compareMode)) {
            String imagePath = actionData.params.has("imagePath") ? actionData.params.get("imagePath").getAsString() : "";
            double threshold = actionData.params.has("similarityThreshold")
                    ? actionData.params.get("similarityThreshold").getAsDouble()
                    : 0.92D;
            ScreenVisionUtils.TemplateMatchResult result = ScreenVisionUtils.compareRegionToTemplate(rect[0], rect[1],
                    rect[2], rect[3], imagePath);
            return result.isFound() && result.getSimilarity() >= threshold;
        }

        ScreenVisionUtils.RegionMetrics metrics = ScreenVisionUtils.analyzeRegion(rect[0], rect[1], rect[2], rect[3]);
        if (!metrics.isFound()) {
            return false;
        }
        if ("EDGE_DENSITY".equalsIgnoreCase(compareMode)) {
            double edgeThreshold = actionData.params.has("edgeThreshold")
                    ? actionData.params.get("edgeThreshold").getAsDouble()
                    : 0.12D;
            return metrics.getEdgeDensity() >= edgeThreshold;
        }

        int targetColor = ScreenVisionUtils.parseColor(
                actionData.params.has("targetColor") ? actionData.params.get("targetColor").getAsString() : "",
                -1);
        if (targetColor < 0) {
            return false;
        }
        double tolerance = actionData.params.has("colorTolerance")
                ? actionData.params.get("colorTolerance").getAsDouble()
                : 48D;
        int averageColor = ((metrics.getAverageR() & 0xFF) << 16)
                | ((metrics.getAverageG() & 0xFF) << 8)
                | (metrics.getAverageB() & 0xFF);
        return ScreenVisionUtils.colorDistance(targetColor, averageColor) <= tolerance;
    }

    private int[] parseVisionRegionRect(JsonObject params) {
        if (params == null || !params.has("regionRect")) {
            return null;
        }
        try {
            if (params.get("regionRect").isJsonArray()) {
                com.google.gson.JsonArray array = params.getAsJsonArray("regionRect");
                if (array.size() < 4) {
                    return null;
                }
                return new int[] {
                        Math.max(0, (int) Math.round(array.get(0).getAsDouble())),
                        Math.max(0, (int) Math.round(array.get(1).getAsDouble())),
                        Math.max(1, (int) Math.round(array.get(2).getAsDouble())),
                        Math.max(1, (int) Math.round(array.get(3).getAsDouble()))
                };
            }
            String text = params.get("regionRect").getAsString();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            String normalized = text.replace("[", "").replace("]", "");
            String[] parts = normalized.split(",");
            if (parts.length < 4) {
                return null;
            }
            return new int[] {
                    Math.max(0, (int) Math.round(Double.parseDouble(parts[0].trim()))),
                    Math.max(0, (int) Math.round(Double.parseDouble(parts[1].trim()))),
                    Math.max(1, (int) Math.round(Double.parseDouble(parts[2].trim()))),
                    Math.max(1, (int) Math.round(Double.parseDouble(parts[3].trim())))
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemStack resolveInventoryStack(String area, int slotIndex) {
        String normalized = area == null ? "MAIN" : area.trim().toUpperCase(Locale.ROOT);
        if ("HOTBAR".equals(normalized)) {
            return slotIndex >= 0 && slotIndex < 9 ? getMainInventoryStack(slotIndex) : ItemStack.EMPTY;
        }
        if ("ARMOR".equals(normalized)) {
            return slotIndex >= 0 && slotIndex < mc.player.inventory.armorInventory.size()
                    ? mc.player.inventory.armorInventory.get(slotIndex)
                    : ItemStack.EMPTY;
        }
        if ("OFFHAND".equals(normalized)) {
            return slotIndex >= 0 && slotIndex < mc.player.inventory.offHandInventory.size()
                    ? mc.player.inventory.offHandInventory.get(slotIndex)
                    : ItemStack.EMPTY;
        }
        return getMainInventoryStack(slotIndex);
    }

    private void writeCapturedStack(String varName, ItemStack stack) {
        if (varName == null || varName.trim().isEmpty()) {
            return;
        }
        runtimeVariables.put(varName + "_found", stack != null && !stack.isEmpty());
        if (stack == null || stack.isEmpty()) {
            runtimeVariables.put(varName + "_name", "");
            runtimeVariables.put(varName + "_count", 0);
            runtimeVariables.put(varName + "_registry", "");
            runtimeVariables.put(varName + "_damage", 0);
            runtimeVariables.put(varName + "_has_nbt", false);
            runtimeVariables.put(varName + "_nbt", "");
            return;
        }
        runtimeVariables.put(varName + "_name", stack.getDisplayName());
        runtimeVariables.put(varName + "_count", stack.getCount());
        runtimeVariables.put(varName + "_registry", String.valueOf(stack.getItem().getRegistryName()));
        runtimeVariables.put(varName + "_damage", stack.getItemDamage());
        runtimeVariables.put(varName + "_has_nbt", stack.hasTagCompound());
        runtimeVariables.put(varName + "_nbt", stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
    }

    private String describeEntityCategory(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            return "PLAYER";
        }
        if (entity instanceof EntityDragon) {
            return "BOSS";
        }
        if (entity instanceof IMob || entity.isCreatureType(EnumCreatureType.MONSTER, false)) {
            return "HOSTILE";
        }
        if (entity instanceof EntityAnimal
                || entity instanceof EntityVillager
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityWaterMob
                || entity instanceof EntityGolem
                || entity instanceof EntityCreature) {
            return "PASSIVE";
        }
        return "OTHER";
    }

    private void captureGuiElement(JsonObject params) {
        String varName = params != null && params.has("varName")
                ? params.get("varName").getAsString().trim()
                : "gui_element";
        if (varName.isEmpty()) {
            return;
        }

        String elementType = params != null && params.has("elementType")
                ? params.get("elementType").getAsString()
                : "ANY";
        String locatorMode = params != null && params.has("guiElementLocatorMode")
                ? params.get("guiElementLocatorMode").getAsString()
                : "TEXT";
        String locatorText = params != null && params.has("locatorText")
                ? params.get("locatorText").getAsString()
                : "";
        String matchMode = params != null && params.has("locatorMatchMode")
                ? params.get("locatorMatchMode").getAsString()
                : "CONTAINS";

        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        runtimeVariables.put(varName + "_found", false);
        runtimeVariables.put(varName + "_screen", snapshot.getScreenSimpleName());
        runtimeVariables.put(varName + "_screen_class", snapshot.getScreenClassName());
        runtimeVariables.put(varName + "_title", snapshot.getTitle());

        GuiElementInspector.GuiElementInfo best = null;
        if ("PATH".equalsIgnoreCase(locatorMode)) {
            best = GuiElementInspector.findFirstByPath(locatorText, matchMode, resolveGuiElementTypes(elementType));
        } else {
            best = findGuiElementByText(snapshot, locatorText, matchMode, elementType);
        }

        if (best == null && "TITLE".equalsIgnoreCase(elementType)) {
            for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
                if (element != null && element.getType() == GuiElementInspector.ElementType.TITLE) {
                    best = element;
                    break;
                }
            }
        }

        if (best == null) {
            recordDebugTrace("capture_gui_element -> " + varName + " 未找到");
            return;
        }

        runtimeVariables.put(varName + "_found", true);
        runtimeVariables.put(varName + "_type", best.getType().name());
        runtimeVariables.put(varName + "_path", best.getPath());
        runtimeVariables.put(varName + "_text", best.getText());
        runtimeVariables.put(varName + "_x", best.getX());
        runtimeVariables.put(varName + "_y", best.getY());
        runtimeVariables.put(varName + "_width", best.getWidth());
        runtimeVariables.put(varName + "_height", best.getHeight());
        if (best.getSlotIndex() >= 0) {
            runtimeVariables.put(varName + "_slot", best.getSlotIndex());
        }
        if (best.getButtonId() != Integer.MIN_VALUE) {
            runtimeVariables.put(varName + "_button_id", best.getButtonId());
        }
        recordDebugTrace("capture_gui_element -> " + varName + " = " + best.getPath());
    }

    private GuiElementInspector.GuiElementInfo findGuiElementByText(GuiElementInspector.GuiSnapshot snapshot,
            String locatorText, String matchMode, String elementType) {
        if (snapshot == null || snapshot.getElements().isEmpty()) {
            return null;
        }

        String expected = locatorText == null ? "" : locatorText.trim().toLowerCase(Locale.ROOT);
        for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
            if (element == null || !matchesGuiElementType(element.getType(), elementType)) {
                continue;
            }
            if (expected.isEmpty()) {
                return element;
            }
            String actual = element.getText() == null ? "" : element.getText().trim().toLowerCase(Locale.ROOT);
            if ("EXACT".equalsIgnoreCase(matchMode) ? actual.equals(expected) : actual.contains(expected)) {
                return element;
            }
        }
        return null;
    }

    private GuiElementInspector.ElementType[] resolveGuiElementTypes(String elementType) {
        if ("TITLE".equalsIgnoreCase(elementType)) {
            return new GuiElementInspector.ElementType[] { GuiElementInspector.ElementType.TITLE };
        }
        if ("BUTTON".equalsIgnoreCase(elementType)) {
            return new GuiElementInspector.ElementType[] { GuiElementInspector.ElementType.BUTTON };
        }
        if ("SLOT".equalsIgnoreCase(elementType)) {
            return new GuiElementInspector.ElementType[] { GuiElementInspector.ElementType.SLOT };
        }
        return new GuiElementInspector.ElementType[] {
                GuiElementInspector.ElementType.TITLE,
                GuiElementInspector.ElementType.BUTTON,
                GuiElementInspector.ElementType.SLOT
        };
    }

    private boolean matchesGuiElementType(GuiElementInspector.ElementType type, String expectedType) {
        if (type == null) {
            return false;
        }
        if (expectedType == null || expectedType.trim().isEmpty() || "ANY".equalsIgnoreCase(expectedType)) {
            return true;
        }
        return type.name().equalsIgnoreCase(expectedType);
    }

    private boolean handleCurrentStepFailure(String reason, Throwable error) {
        if (currentSequence == null || currentStepIndex < 0 || currentStepIndex >= currentSequence.getSteps().size()) {
            markExecutionResult(false, reason);
            stopTracking();
            return true;
        }

        String safeReason = reason == null ? "unknown" : reason;
        if (error != null) {
            zszlScriptMod.LOGGER.error("[legacy_path] 步骤失败: seq={}, step={}, action={}, reason={}",
                    currentSequence.getName(), currentStepIndex, actionIndex, safeReason, error);
        } else {
            zszlScriptMod.LOGGER.warn("[legacy_path] 步骤失败: seq={}, step={}, action={}, reason={}",
                    currentSequence.getName(), currentStepIndex, actionIndex, safeReason);
        }
        String detailedReason = safeReason
                + (currentDebugActionDescription == null || currentDebugActionDescription.trim().isEmpty()
                        ? ""
                        : " / action=" + currentDebugActionDescription);
        recordFailureDetail(detailedReason);
        recordDebugTrace("步骤失败: step=" + currentStepIndex + ", action=" + actionIndex + ", reason=" + detailedReason);

        resetHotbarUseActionState();
        resetAsyncActionState();
        resetWaitConditionState();
        clearDeferredWaitState();
        resetRepeatActionState();
        resetBranchBlockState();
        resetIfElseState();
        resetWhileConditionState();
        resetForEachListState();
        resetForEachPointState();
        resetRetryBlockState();
        resetStepPathRetryMonitor();
        releaseResources();
        markExecutionResult(false, "步骤动作异常停止: " + safeReason);
        stopTracking();
        return true;
    }

    private void restartCurrentStepTarget(PathStep step) {
        resetStepPathRetryMonitor();
        resetStepArrivalWaitState();
        if (step == null) {
            atTarget = true;
            if (shouldStopNavigationForStepTransition()) {
                stopNavigation();
            }
            return;
        }
        double[] target = step.getGotoPoint();
        if (target != null && target.length >= 3 && !Double.isNaN(target[0])) {
            atTarget = false;
            if (shouldStopNavigationForStepTransition()) {
                stopNavigation();
            }
            prepareForSequenceStartDispatch("restartCurrentStepTarget");
            startNavigationGoto(target[0], target[1], target[2], true);
            initializeStepPathRetryMonitor(mc.player);
        } else {
            atTarget = true;
            if (shouldStopNavigationForStepTransition()) {
                stopNavigation();
            }
            resetStepPathRetryMonitor();
        }
    }

    private void moveToStepTargetOrFinish() {
        resetStepPathRetryMonitor();
        resetStepArrivalWaitState();
        if (currentSequence == null) {
            return;
        }
        if (currentStepIndex >= currentSequence.getSteps().size()) {
            atTarget = false;
            return;
        }
        restartCurrentStepTarget(currentSequence.getSteps().get(currentStepIndex));
    }

    private boolean hasReachedGotoTarget(EntityPlayerSP player, PathStep step, double[] target) {
        if (player == null || target == null || target.length < 3) {
            return true;
        }

        if (Double.isNaN(target[0])) {
            return true;
        }

        int toleranceBlocks = step == null ? 0 : Math.max(0, step.getArrivalToleranceBlocks());

        int playerBlockX = MathHelper.floor(player.posX);
        int playerBlockY = MathHelper.floor(player.posY);
        int playerBlockZ = MathHelper.floor(player.posZ);

        int targetBlockX = MathHelper.floor(target[0]);
        int targetBlockZ = MathHelper.floor(target[2]);

        if (Double.isNaN(target[1])) {
            return Math.abs(playerBlockX - targetBlockX) <= toleranceBlocks
                    && Math.abs(playerBlockZ - targetBlockZ) <= toleranceBlocks;
        }

        int targetBlockY = MathHelper.floor(target[1]);
        return Math.abs(playerBlockX - targetBlockX) <= toleranceBlocks
                && Math.abs(playerBlockY - targetBlockY) <= toleranceBlocks
                && Math.abs(playerBlockZ - targetBlockZ) <= toleranceBlocks;
    }

    private void handleUseHotbarItemAction(EntityPlayerSP player, ActionData actionData) {
        if (!hotbarUseActionRunning) {
            this.hotbarUseItemName = readStringParam(actionData.params, "itemName", "").trim();
            this.hotbarUseMatchMode = "EXACT".equalsIgnoreCase(
                    readStringParam(actionData.params, "matchMode", "CONTAINS"))
                            ? AutoUseItemRule.MatchMode.EXACT
                            : AutoUseItemRule.MatchMode.CONTAINS;
            this.hotbarUseMode = "LEFT_CLICK".equalsIgnoreCase(
                    readStringParam(actionData.params, "useMode", "RIGHT_CLICK"))
                            ? AutoUseItemRule.UseMode.LEFT_CLICK
                            : AutoUseItemRule.UseMode.RIGHT_CLICK;
            this.hotbarUseChangeLocalSlot = readBooleanParam(actionData.params, "changeLocalSlot", false);
            this.hotbarUseSwitchItemDelayTicks = Math.max(0,
                    readIntParam(actionData.params, "switchItemDelayTicks", 0));
            this.hotbarUseSwitchDelayTicks = Math.max(0,
                    readIntParam(actionData.params, "switchDelayTicks", 0));
            this.hotbarUseSwitchBackDelayTicks = Math.max(0,
                    readIntParam(actionData.params, "switchBackDelayTicks", 0));
            this.hotbarUseRemainingCount = Math.max(1,
                    readIntParam(actionData.params, "count", 1));
            this.hotbarUseIntervalTicks = Math.max(0,
                    readIntParam(actionData.params, "intervalTicks", 0));
            this.hotbarUseWaitTicks = 0;
            this.hotbarUseActionRunning = true;
        }

        if (hotbarUseItemName.isEmpty()) {
            resetHotbarUseActionState();
            releaseResources();
            actionIndex++;
            applyBuiltinSequenceDelay();
            return;
        }

        if (hotbarUseIntervalTicks <= 0) {
            while (hotbarUseRemainingCount > 0) {
                boolean ok = AutoUseItemHandler.INSTANCE.useMatchingHotbarItem(player, hotbarUseItemName,
                        hotbarUseMatchMode, hotbarUseMode, hotbarUseChangeLocalSlot,
                        hotbarUseSwitchItemDelayTicks, hotbarUseSwitchDelayTicks, hotbarUseSwitchBackDelayTicks);
                hotbarUseRemainingCount--;
                if (!ok) {
                    break;
                }
            }
            resetHotbarUseActionState();
            releaseResources();
            actionIndex++;
            applyBuiltinSequenceDelay();
            return;
        }

        if (hotbarUseWaitTicks > 0) {
            hotbarUseWaitTicks--;
            return;
        }

        boolean ok = AutoUseItemHandler.INSTANCE.useMatchingHotbarItem(player, hotbarUseItemName,
                hotbarUseMatchMode, hotbarUseMode, hotbarUseChangeLocalSlot,
                hotbarUseSwitchItemDelayTicks, hotbarUseSwitchDelayTicks, hotbarUseSwitchBackDelayTicks);
        hotbarUseRemainingCount--;

        if (!ok || hotbarUseRemainingCount <= 0) {
            resetHotbarUseActionState();
            releaseResources();
            actionIndex++;
            applyBuiltinSequenceDelay();
            return;
        }

        hotbarUseWaitTicks = hotbarUseIntervalTicks;
    }

    // --- 新增：狩猎模式的核心逻辑 ---
    private void executeHuntTick(EntityPlayerSP player) {
        if (mc.world == null || player == null) {
            completeHuntAction();
            return;
        }

        if (huntWhitelistRespawnWaiting) {
            tickHuntWhitelistRespawnWait(player);
            return;
        }

        if (huntKillCompletionConfirming) {
            if (hasNearbyHuntWhitelistRespawnTarget(player)) {
                huntKillCompletionConfirming = false;
                huntKillCompletionConfirmationElapsedTicks = 0;
                huntKillCompletionConfirmationResumed = true;
                setStatus(getStatus().split(" \\| ")[0] + " | " + I18n.format("status.path.hunting"));
                recordDebugTrace("hunt kill completion confirmation target reappeared");
            } else if (++huntKillCompletionConfirmationElapsedTicks >= huntKillCompletionConfirmTicks) {
                recordDebugTrace("hunt kill completion confirmation passed");
                completeHuntAction();
                return;
            } else {
                setStatus(getStatus().split(" \\| ")[0] + " | 击杀完成确认 "
                        + huntKillCompletionConfirmationElapsedTicks + "/"
                        + huntKillCompletionConfirmTicks);
                return;
            }
        }

        if (huntForceEndEnabled && huntForceEndTimeoutTicks > 0
                && ++huntForceEndElapsedTicks >= huntForceEndTimeoutTicks) {
            zszlScriptMod.LOGGER.info("中心搜怪击杀强制结束: {} 秒", huntForceEndTimeoutTicks / 20);
            recordDebugTrace("hunt force end timed out");
            completeHuntAction();
            return;
        }

        if (huntTargetEntity instanceof EntityLivingBase
                && isHuntTargetKilled((EntityLivingBase) huntTargetEntity)) {
            recordHuntTargetKill((EntityLivingBase) huntTargetEntity);
            huntTargetEntity = null;
        }

        if (shouldCompleteHuntByWhitelistKillGoals() && !huntKillCompletionConfirmationResumed) {
            if (beginHuntKillCompletionConfirmation()) {
                return;
            }
            if (beginHuntWhitelistRespawnWait(true)) {
                return;
            }
            if (KillAuraHandler.INSTANCE.isAreaHuntSequenceRunning()) {
                huntPendingCompleteAfterSequence = true;
            } else {
                completeHuntAction();
            }
            return;
        }

        if (huntPendingCompleteAfterSequence) {
            if (!KillAuraHandler.INSTANCE.isAreaHuntSequenceRunning()) {
                completeHuntAction();
            } else {
                KillAuraHandler.INSTANCE.tickAreaHuntSequenceOnly(player);
            }
            return;
        }

        KillAuraHandler.AreaHuntTickResult result = KillAuraHandler.INSTANCE.tickAreaHunt(player,
                buildKillAuraAreaHuntOptions());
        EntityLivingBase activeTarget = result.getTarget();
        if (activeTarget != null) {
            huntHasSeenWhitelistTarget = true;
        }
        if (activeTarget != null && huntWaitForWhitelistRespawn) {
            huntWhitelistRespawnWaitSatisfied = true;
        }
        if (activeTarget != huntTargetEntity) {
            setHuntTarget(activeTarget);
        }

        if (huntTargetEntity instanceof EntityLivingBase
                && isHuntTargetKilled((EntityLivingBase) huntTargetEntity)) {
            recordHuntTargetKill((EntityLivingBase) huntTargetEntity);
            huntTargetEntity = null;
        }

        if (shouldCompleteHuntByWhitelistKillGoals() && !huntKillCompletionConfirmationResumed) {
            if (beginHuntKillCompletionConfirmation()) {
                return;
            }
            if (beginHuntWhitelistRespawnWait(true)) {
                return;
            }
            if (result.isSequenceRunning()) {
                huntPendingCompleteAfterSequence = true;
            } else {
                completeHuntAction();
            }
            return;
        }

        if (!result.hasTarget()) {
            if (huntKillCompletionConfirmationResumed) {
                huntKillCompletionConfirmationResumed = false;
                if (beginHuntKillCompletionConfirmation()) {
                    return;
                }
            }
            if (huntConfirmKillCompletion && huntHasSeenWhitelistTarget
                    && beginHuntKillCompletionConfirmation()) {
                return;
            }
            if (huntWaitForWhitelistRespawn && huntWhitelistTargetKilledAfterWait) {
                completeHuntAction();
                return;
            }
            if (beginHuntWhitelistRespawnWait(false)) {
                return;
            }
            if (huntAreaSweepEnabled && huntEmptyConfirmTicks < HUNT_AREA_SWEEP_EMPTY_CONFIRM_TICKS) {
                huntEmptyConfirmTicks++;
                setStatus(getStatus().split(" \\| ")[0] + " | 区域扫荡收尾确认 "
                        + huntEmptyConfirmTicks + "/" + HUNT_AREA_SWEEP_EMPTY_CONFIRM_TICKS);
                return;
            }
            recordHuntNoTargetDiagnostics(player);
            zszlScriptMod.LOGGER.info(I18n.format("log.path.hunt_no_monster_end"));
            completeHuntAction(huntNoTargetSkipCount);
        } else {
            huntEmptyConfirmTicks = 0;
        }
    }

    private KillAuraHandler.AreaHuntOptions buildKillAuraAreaHuntOptions() {
        return new KillAuraHandler.AreaHuntOptions(huntCenterX, huntCenterY, huntCenterZ, huntRadius,
                huntUpRange, huntDownRange, this::isAllowedByHuntActionNameFilters, huntEnableNameWhitelist,
                huntAreaSweepEnabled ? huntAreaSweepPlanner : null);
    }

    private boolean isAllowedByHuntActionNameFilters(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        String filterName = getHuntFilterableEntityName(entity);
        if (huntEnableNameBlacklist
                && KillAuraHandler.getNameListMatchIndex(filterName, huntNameBlacklist) != Integer.MAX_VALUE) {
            return false;
        }
        if (huntEnableNameWhitelist) {
            return getActiveHuntWhitelistMatchIndex(filterName) != Integer.MAX_VALUE;
        }
        return true;
    }

    private void recordHuntNoTargetDiagnostics(EntityPlayerSP player) {
        if (player == null || player.world == null) {
            recordDebugTrace("hunt scan: 玩家或世界为空，无法输出搜怪诊断");
            return;
        }
        List<String> detailLines = new ArrayList<>();
        int inRangeCount = 0;
        int whitelistMatchedCount = 0;
        int groupMatchedCount = 0;
        int finalCandidateCount = 0;

        for (Entity entity : player.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living == player || living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                continue;
            }
            if (!isWithinHuntArea(living) || !isWithinHuntVerticalRange(living)) {
                continue;
            }
            inRangeCount++;
            String name = getHuntFilterableEntityName(living);
            boolean blacklistHit = huntEnableNameBlacklist
                    && KillAuraHandler.getNameListMatchIndex(name, huntNameBlacklist) != Integer.MAX_VALUE;
            boolean whitelistMatched = !huntEnableNameWhitelist
                    || getActiveHuntWhitelistMatchIndex(name) != Integer.MAX_VALUE;
            boolean groupMatched = matchesHuntTargetGroup(living);
            boolean invisibleBlocked = huntIgnoreInvisible && living.isInvisible();
            boolean finalCandidate = !blacklistHit && whitelistMatched && (huntEnableNameWhitelist || groupMatched)
                    && !invisibleBlocked;
            if (whitelistMatched) {
                whitelistMatchedCount++;
            }
            if (groupMatched) {
                groupMatchedCount++;
            }
            if (finalCandidate) {
                finalCandidateCount++;
            }
            if (detailLines.size() < 8) {
                detailLines.add(name
                        + " [类型=" + getHuntEntityGroupLabel(living)
                        + ", 白名单=" + whitelistMatched
                        + ", 黑名单=" + blacklistHit
                        + ", 分组=" + groupMatched
                        + ", 隐身拦截=" + invisibleBlocked
                        + ", 最终可选=" + finalCandidate + "]");
            }
        }

        recordDebugTrace("hunt scan: 范围内实体=" + inRangeCount
                + ", 白名单命中=" + whitelistMatchedCount
                + ", 分组命中=" + groupMatchedCount
                + ", 最终可选=" + finalCandidateCount);
        if (detailLines.isEmpty()) {
            recordDebugTrace("hunt scan detail: 范围内没有找到任何存活实体");
            return;
        }
        for (String line : detailLines) {
            recordDebugTrace("hunt scan detail: " + line);
        }
    }

    private boolean isWithinHuntArea(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        double dx = entity.posX - huntCenterX;
        double dz = entity.posZ - huntCenterZ;
        return dx * dx + dz * dz <= huntRadius * huntRadius + 1.0E-6D;
    }

    private String getHuntEntityGroupLabel(EntityLivingBase entity) {
        if (entity == null) {
            return "unknown";
        }
        if (entity instanceof net.minecraft.entity.player.EntityPlayer) {
            return "player";
        }
        if (isHostileHuntTarget(entity)) {
            return "hostile";
        }
        if (isPassiveHuntTarget(entity)) {
            return "passive";
        }
        return "other";
    }

    private EntityLivingBase getKillAuraSynchronizedHuntTarget(EntityPlayerSP player) {
        if (!KillAuraHandler.enabled || player == null || player.world == null) {
            return null;
        }
        Optional<EntityLivingBase> activeTarget = KillAuraHandler.INSTANCE.getActiveTarget(player);
        if (!activeTarget.isPresent()) {
            return null;
        }
        EntityLivingBase target = activeTarget.get();
        return isValidHuntCandidate(player, target) ? target : null;
    }

    private void setHuntTarget(EntityLivingBase target) {
        this.huntTargetEntity = target;
        lastHuntGotoTargetEntityId = -1L;
        huntMovementStopped = false;
        huntWasWithinDesiredDistance = false;
        lastHuntGotoTargetX = Double.NaN;
        lastHuntGotoTargetY = Double.NaN;
        lastHuntGotoTargetZ = Double.NaN;
        huntOrbitLoopNodeIndex = -1;
        huntLastOrbitGotoTick = -99999;
        huntOrbitStuckTicks = 0;
        huntLastOrbitPlayerX = Double.NaN;
        huntLastOrbitPlayerZ = Double.NaN;
        huntOrbitController.stop();
        if (target != null) {
            zszlScriptMod.LOGGER.info(I18n.format("log.path.hunt_new_target") + getHuntFilterableEntityName(target));
            recordDebugTrace("hunt target -> " + getHuntFilterableEntityName(target));
        }
    }

    private void resetHuntState() {
        KillAuraHandler.INSTANCE.stopAreaHuntAction();
        this.huntAttackSequenceExecutor.stop();
        this.isHunting = false;
        this.huntRadius = 0.0D;
        this.huntAutoAttack = false;
        this.huntAttackMode = KillAuraHandler.ATTACK_MODE_NORMAL;
        this.huntAttackSequenceName = "";
        this.huntAimLockEnabled = true;
        this.huntTrackingDistanceSq = 0.0D;
        this.huntUpRange = DEFAULT_CENTER_HUNT_VERTICAL_RANGE;
        this.huntDownRange = DEFAULT_CENTER_HUNT_VERTICAL_RANGE;
        this.huntTargetEntity = null;
        this.lastHuntGotoTargetEntityId = -1L;
        this.huntMovementStopped = false;
        this.huntMode = KillAuraHandler.HUNT_MODE_FIXED_DISTANCE;
        this.huntOrbitEnabled = false;
        this.huntChaseIntervalEnabled = false;
        this.huntChaseIntervalTicks = 0;
        this.huntChaseCooldownTicks = 0;
        this.huntWasWithinDesiredDistance = false;
        this.huntAttackCooldownTicks = 0;
        this.lastHuntGotoTargetX = Double.NaN;
        this.lastHuntGotoTargetY = Double.NaN;
        this.lastHuntGotoTargetZ = Double.NaN;
        this.huntAttackRemaining = -1;
        this.huntEmptyConfirmTicks = 0;
        this.huntNoTargetSkipCount = 0;
        this.huntNoDamageAttackLimit = KillAuraHandler.DEFAULT_NO_DAMAGE_ATTACK_LIMIT;
        this.huntRestrictTargetGroups = true;
        this.huntTargetHostile = true;
        this.huntTargetPassive = false;
        this.huntTargetPlayers = false;
        this.huntEnableNameWhitelist = false;
        this.huntWaitForWhitelistRespawn = false;
        this.huntWaitForWhitelistRespawnAfterCompletion = false;
        this.huntConfirmKillCompletion = false;
        this.huntKillCompletionConfirmTicks = HUNT_KILL_COMPLETION_CONFIRM_DEFAULT_SECONDS * 20;
        this.huntWhitelistRespawnTimeoutTicks = 120 * 20;
        this.huntWhitelistRespawnElapsedTicks = 0;
        this.huntWhitelistRespawnWaiting = false;
        this.huntWhitelistRespawnCompletesAction = false;
        this.huntWhitelistRespawnWaitSatisfied = false;
        this.huntWhitelistTargetKilledAfterWait = false;
        this.huntHasSeenWhitelistTarget = false;
        this.huntKillCompletionConfirming = false;
        this.huntKillCompletionConfirmationResumed = false;
        this.huntKillCompletionConfirmationElapsedTicks = 0;
        this.huntForceEndEnabled = false;
        this.huntForceEndTimeoutTicks = 60 * 20;
        this.huntForceEndElapsedTicks = 0;
        this.huntEnableNameBlacklist = false;
        this.huntAreaSweepEnabled = true;
        this.huntAreaSweepCellSize = 8;
        this.huntAreaSweepPlanner.reset(this.huntAreaSweepCellSize);
        this.huntNameWhitelist.clear();
        this.huntWhitelistTargets.clear();
        this.huntWhitelistKillProgress.clear();
        this.countedHuntKillEntityIds.clear();
        this.huntNoDamageAttackTrackers.clear();
        this.huntNoDamageExcludedEntityIds.clear();
        this.huntNameBlacklist.clear();
        this.huntShowRange = false;
        this.huntIgnoreInvisible = false;
        this.huntCenterX = 0.0D;
        this.huntCenterY = 0.0D;
        this.huntCenterZ = 0.0D;
        this.huntOrbitLoopNodeIndex = -1;
        this.huntLastOrbitGotoTick = -99999;
        this.huntOrbitStuckTicks = 0;
        this.huntLastOrbitPlayerX = Double.NaN;
        this.huntLastOrbitPlayerZ = Double.NaN;
        this.huntPendingCompleteAfterSequence = false;
        this.huntOrbitController.stop();
    }

    private boolean isHuntNoDamageExclusionEnabled() {
        return huntNoDamageAttackLimit > 0;
    }

    private boolean isHuntNoDamageExcludedTarget(Entity entity) {
        return isHuntNoDamageExclusionEnabled()
                && entity != null
                && huntNoDamageExcludedEntityIds.contains(entity.getEntityId());
    }

    private void tickHuntNoDamageAttackTrackers(EntityPlayerSP player) {
        if (!isHuntNoDamageExclusionEnabled() || player == null || player.world == null) {
            huntNoDamageAttackTrackers.clear();
            huntNoDamageExcludedEntityIds.clear();
            return;
        }

        List<Integer> trackerIds = new ArrayList<>(huntNoDamageAttackTrackers.keySet());
        for (Integer entityId : trackerIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                huntNoDamageAttackTrackers.remove(entityId);
                huntNoDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                huntNoDamageAttackTrackers.remove(entityId);
                huntNoDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            HuntNoDamageAttackTracker tracker = huntNoDamageAttackTrackers.get(entityId);
            if (tracker != null) {
                updateHuntNoDamageAttackTracker(entityId, living, tracker);
            }
        }
        pruneHuntNoDamageExcludedTargets(player);
    }

    private void updateHuntNoDamageAttackTracker(int entityId, EntityLivingBase target,
            HuntNoDamageAttackTracker tracker) {
        float currentHealth = target.getHealth();
        if (currentHealth + HUNT_NO_DAMAGE_HEALTH_EPSILON < tracker.baselineHealth) {
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
        tracker.pendingAttempts = 0;
        tracker.baselineHealth = currentHealth;
        if (tracker.confirmedNoDamageAttempts >= huntNoDamageAttackLimit) {
            excludeHuntNoDamageTarget(entityId, target);
        }
    }

    private void recordHuntNoDamageAttackAttempt(EntityLivingBase target) {
        if (!isHuntNoDamageExclusionEnabled() || target == null || target.getHealth() <= 0.0F) {
            return;
        }
        int entityId = target.getEntityId();
        if (huntNoDamageExcludedEntityIds.contains(entityId)) {
            return;
        }

        float currentHealth = target.getHealth();
        HuntNoDamageAttackTracker tracker = huntNoDamageAttackTrackers.get(entityId);
        if (tracker == null) {
            tracker = new HuntNoDamageAttackTracker(currentHealth);
            huntNoDamageAttackTrackers.put(entityId, tracker);
        } else if (currentHealth + HUNT_NO_DAMAGE_HEALTH_EPSILON < tracker.baselineHealth) {
            tracker.baselineHealth = currentHealth;
            tracker.pendingAttempts = 0;
            tracker.observationTicks = 0;
            tracker.confirmedNoDamageAttempts = 0;
        }
        if (tracker.pendingAttempts <= 0) {
            tracker.baselineHealth = currentHealth;
            tracker.observationTicks = HUNT_NO_DAMAGE_OBSERVATION_DELAY_TICKS;
        }
        tracker.pendingAttempts++;
        pruneHuntNoDamageTrackingSize();
    }

    private void excludeHuntNoDamageTarget(int entityId, EntityLivingBase target) {
        if (!huntNoDamageExcludedEntityIds.add(entityId)) {
            return;
        }
        huntNoDamageAttackTrackers.remove(entityId);
        pruneHuntNoDamageTrackingSize();
        if (huntTargetEntity != null && huntTargetEntity.getEntityId() == entityId) {
            clearHuntTargetLock(true);
        }
        String targetName = target == null ? "" : getHuntFilterableEntityName(target);
        recordDebugTrace("hunt no-damage exclude -> id=" + entityId + ", name=" + targetName
                + ", limit=" + huntNoDamageAttackLimit);
    }

    private void clearHuntTargetLock(boolean stopSequence) {
        if (stopSequence && !huntPendingCompleteAfterSequence && huntAttackSequenceExecutor.isRunning()) {
            huntAttackSequenceExecutor.stop();
        }
        huntTargetEntity = null;
        lastHuntGotoTargetEntityId = -1L;
        huntMovementStopped = false;
        huntWasWithinDesiredDistance = false;
        lastHuntGotoTargetX = Double.NaN;
        lastHuntGotoTargetY = Double.NaN;
        lastHuntGotoTargetZ = Double.NaN;
        huntOrbitLoopNodeIndex = -1;
        huntLastOrbitGotoTick = -99999;
        huntOrbitStuckTicks = 0;
        huntLastOrbitPlayerX = Double.NaN;
        huntLastOrbitPlayerZ = Double.NaN;
        huntOrbitController.stop();
        stopHuntNavigationMode();
    }

    private void pruneHuntNoDamageExcludedTargets(EntityPlayerSP player) {
        if (player == null || player.world == null || huntNoDamageExcludedEntityIds.isEmpty()) {
            return;
        }
        List<Integer> excludedIds = new ArrayList<>(huntNoDamageExcludedEntityIds);
        for (Integer entityId : excludedIds) {
            if (entityId == null) {
                continue;
            }
            Entity entity = player.world.getEntityByID(entityId);
            if (!(entity instanceof EntityLivingBase)) {
                huntNoDamageExcludedEntityIds.remove(entityId);
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.isDead || !living.isEntityAlive() || living.getHealth() <= 0.0F) {
                huntNoDamageExcludedEntityIds.remove(entityId);
            }
        }
    }

    private void pruneHuntNoDamageTrackingSize() {
        while (huntNoDamageAttackTrackers.size() > HUNT_NO_DAMAGE_MAX_TRACKED_TARGETS) {
            Integer first = huntNoDamageAttackTrackers.keySet().iterator().next();
            huntNoDamageAttackTrackers.remove(first);
        }
        while (huntNoDamageExcludedEntityIds.size() > HUNT_NO_DAMAGE_MAX_EXCLUDED_TARGETS) {
            Integer first = huntNoDamageExcludedEntityIds.iterator().next();
            huntNoDamageExcludedEntityIds.remove(first);
        }
    }

    private boolean beginHuntWhitelistRespawnWait(boolean completeWhenTargetAppears) {
        boolean enabled = completeWhenTargetAppears
                ? huntWaitForWhitelistRespawnAfterCompletion : huntWaitForWhitelistRespawn;
        if (!enabled || !huntEnableNameWhitelist
                || (huntWhitelistTargets.isEmpty() && huntNameWhitelist.isEmpty())) {
            return false;
        }
        // The regular respawn wait is a one-time gate for this hunt action. Once
        // the target has appeared and been attacked, do not enter it again.
        if (!completeWhenTargetAppears && huntWhitelistRespawnWaitSatisfied) {
            return false;
        }
        if (!huntWhitelistRespawnWaiting) {
            huntWhitelistRespawnWaiting = true;
            huntWhitelistRespawnCompletesAction = completeWhenTargetAppears;
            huntWhitelistRespawnElapsedTicks = 0;
            huntTargetEntity = null;
            stopHuntNavigationMode();
            KillAuraHandler.INSTANCE.stopAreaHuntAction();
            setStatus(getStatus().split(" \\| ")[0] + " | 等待白名单目标复活");
            recordDebugTrace("hunt whitelist respawn wait started: timeoutTicks="
                    + huntWhitelistRespawnTimeoutTicks);
        }
        return true;
    }

    private boolean beginHuntKillCompletionConfirmation() {
        if (!huntConfirmKillCompletion || !huntEnableNameWhitelist
                || (huntWhitelistTargets.isEmpty() && huntNameWhitelist.isEmpty())
                || huntKillCompletionConfirmationResumed) {
            return false;
        }
        if (!huntKillCompletionConfirming) {
            huntKillCompletionConfirming = true;
            huntKillCompletionConfirmationElapsedTicks = 0;
            huntTargetEntity = null;
            stopHuntNavigationMode();
            KillAuraHandler.INSTANCE.stopAreaHuntAction();
            setStatus(getStatus().split(" \\| ")[0] + " | 击杀完成确认 0/"
                    + huntKillCompletionConfirmTicks);
            recordDebugTrace("hunt kill completion confirmation started: ticks="
                    + huntKillCompletionConfirmTicks);
        }
        return true;
    }

    private void tickHuntWhitelistRespawnWait(EntityPlayerSP player) {
        if (hasNearbyHuntWhitelistRespawnTarget(player)) {
            recordDebugTrace("hunt whitelist respawn target appeared");
            if (huntWhitelistRespawnCompletesAction) {
                completeHuntAction();
            } else {
                huntWhitelistRespawnWaiting = false;
                huntWhitelistRespawnCompletesAction = false;
                huntWhitelistRespawnWaitSatisfied = true;
                huntWhitelistRespawnElapsedTicks = 0;
                setStatus(getStatus().split(" \\| ")[0] + " | " + I18n.format("status.path.hunting"));
            }
            return;
        }
        if (huntWhitelistRespawnTimeoutTicks > 0
                && huntWhitelistRespawnElapsedTicks >= huntWhitelistRespawnTimeoutTicks) {
            zszlScriptMod.LOGGER.warn("中心搜怪击杀等待白名单目标复活超时: {} 秒",
                    huntWhitelistRespawnTimeoutTicks / 20);
            recordDebugTrace("hunt whitelist respawn wait timed out");
            completeHuntAction();
            return;
        }
        huntWhitelistRespawnElapsedTicks++;
    }

    private boolean hasNearbyHuntWhitelistRespawnTarget(EntityPlayerSP player) {
        if (player == null || player.world == null || !huntEnableNameWhitelist
                || (huntWhitelistTargets.isEmpty() && huntNameWhitelist.isEmpty())) {
            return false;
        }
        for (EntityLivingBase entity : player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                new AxisAlignedBB(huntCenterX - huntRadius, huntCenterY - huntDownRange,
                        huntCenterZ - huntRadius, huntCenterX + huntRadius,
                        huntCenterY + huntUpRange, huntCenterZ + huntRadius))) {
            if (entity == null || entity == player || !entity.isEntityAlive()
                    || getAnyHuntWhitelistMatchIndex(getHuntFilterableEntityName(entity)) == Integer.MAX_VALUE) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void completeHuntAction() {
        completeHuntAction(0);
    }

    private void completeHuntAction(int additionalSkipCount) {
        stopHuntNavigationMode();
        KillAuraHandler.INSTANCE.stopAreaHuntAction();
        if (shouldStopNavigationOnFinish()) {
            stopNavigation();
        }
        resetHuntState();
        actionIndex += Math.max(0, additionalSkipCount) + 1;
        applyBuiltinSequenceDelay();
        setStatus(getStatus().split(" \\| ")[0]);
    }

    private boolean isHuntFixedDistanceMode() {
        return KillAuraHandler.HUNT_MODE_FIXED_DISTANCE.equalsIgnoreCase(huntMode);
    }

    private boolean isHuntOrbitMode() {
        return isHuntFixedDistanceMode() && huntOrbitEnabled;
    }

    private double getHuntDesiredDistance() {
        return Math.max(0.5D, Math.sqrt(Math.max(0.0D, huntTrackingDistanceSq)));
    }

    private String normalizeHuntAttackMode(String mode) {
        return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(mode)
                ? KillAuraHandler.ATTACK_MODE_SEQUENCE
                : KillAuraHandler.ATTACK_MODE_NORMAL;
    }

    private boolean isHuntSequenceAttackMode() {
        return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(huntAttackMode);
    }

    private boolean isAnyHuntAttackSequenceMode() {
        return isHuntSequenceAttackMode() || KillAuraHandler.isConfiguredSequenceAttackMode();
    }

    private String getConfiguredHuntAttackSequenceName() {
        return huntAttackSequenceName == null ? "" : huntAttackSequenceName.trim();
    }

    private boolean isWithinHuntDesiredDistance(double distance) {
        if (isHuntFixedDistanceMode()) {
            return Math.abs(distance - getHuntDesiredDistance()) <= HUNT_FIXED_DISTANCE_TOLERANCE;
        }
        return distance <= getHuntDesiredDistance();
    }

    private boolean shouldRunHuntMovementForDistance(double distance) {
        if (isHuntFixedDistanceMode()) {
            if (isHuntOrbitMode()) {
                return true;
            }
            return Math.abs(distance - getHuntDesiredDistance()) > HUNT_FIXED_DISTANCE_TOLERANCE;
        }
        return distance > getHuntDesiredDistance();
    }

    private void rotatePlayerTowardHuntTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return;
        }
        ModUtils.setPlayerViewAngles(player,
                (float) Math.toDegrees(Math.atan2(target.posZ - player.posZ, target.posX - player.posX)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(target.posY + target.getEyeHeight() * 0.85D
                        - (player.posY + player.getEyeHeight()), Math.max(0.001D, player.getDistance(target)))));
    }

    private void tickHuntAttackSequenceExecutor(EntityPlayerSP player) {
        if (!isAnyHuntAttackSequenceMode()) {
            if (huntAttackSequenceExecutor.isRunning()) {
                huntAttackSequenceExecutor.stop();
            }
            return;
        }
        huntAttackSequenceExecutor.tick(player);
    }

    private boolean tryTriggerHuntAttackSequence(EntityPlayerSP player, EntityLivingBase target) {
        return tryTriggerHuntAttackSequence(player, target, getConfiguredHuntAttackSequenceName(), false);
    }

    private boolean tryTriggerKillAuraConfiguredAttackSequence(EntityPlayerSP player, EntityLivingBase target) {
        if (!KillAuraHandler.INSTANCE.prepareCurrentConfigSequenceAttackForHunt(player, target)) {
            return false;
        }
        return tryTriggerHuntAttackSequence(player, target, KillAuraHandler.getConfiguredAttackSequenceName(), true);
    }

    private boolean tryTriggerHuntAttackSequence(EntityPlayerSP player, EntityLivingBase target, String sequenceName,
            boolean useKillAuraDelay) {
        if (player == null || target == null) {
            return false;
        }
        if (huntAttackCooldownTicks > 0 || huntAttackSequenceExecutor.isRunning()) {
            return false;
        }
        sequenceName = sequenceName == null ? "" : sequenceName.trim();
        if (sequenceName.isEmpty()) {
            return false;
        }
        PathSequence configuredSequence = PathSequenceManager.getSequence(sequenceName);
        if (configuredSequence == null || configuredSequence.getSteps().isEmpty()) {
            return false;
        }
        huntAttackSequenceExecutor.start(configuredSequence, player, target);
        if (!huntAttackSequenceExecutor.isRunning()) {
            return false;
        }
        huntAttackCooldownTicks = Math.max(0, useKillAuraDelay
                ? KillAuraHandler.sampleCurrentConfigSequenceDelayTicks()
                : KillAuraHandler.attackSequenceDelayTicks);
        return true;
    }

    private boolean tryHuntAttack(EntityPlayerSP player, EntityLivingBase target) {
        if (isHuntNoDamageExcludedTarget(target)) {
            return false;
        }
        if (isHuntSequenceAttackMode()) {
            boolean triggered = tryTriggerHuntAttackSequence(player, target);
            if (triggered) {
                recordHuntNoDamageAttackAttempt(target);
            }
            return triggered;
        }
        if (KillAuraHandler.isConfiguredSequenceAttackMode()) {
            boolean triggered = tryTriggerKillAuraConfiguredAttackSequence(player, target);
            if (triggered) {
                recordHuntNoDamageAttackAttempt(target);
            }
            return triggered;
        }
        if (huntAttackCooldownTicks > 0 || player == null || target == null) {
            return false;
        }
        if (!KillAuraHandler.INSTANCE.tryAttackUsingCurrentConfigForHunt(player, target)) {
            return false;
        }
        recordHuntNoDamageAttackAttempt(target);
        huntAttackCooldownTicks = Math.max(0, KillAuraHandler.sampleCurrentConfigAttackCooldownTicks());
        return true;
    }

    private void driveContinuousHuntOrbit(EntityPlayerSP player, EntityLivingBase target) {
        huntOrbitController.tick(player, target,
                new HuntOrbitController.OrbitConfig(getHuntDesiredDistance(), HUNT_FIXED_DISTANCE_TOLERANCE,
                        true, true, true));
    }

    private boolean shouldUseContinuousHuntOrbit(EntityPlayerSP player, EntityLivingBase target, double distance) {
        if (!isHuntOrbitMode() || player == null || target == null) {
            return false;
        }
        if (player.isElytraFlying()
                || (player.capabilities != null && player.capabilities.isFlying)
                || Math.abs(player.posY - target.posY) > HUNT_CONTINUOUS_ORBIT_MAX_VERTICAL_DELTA) {
            return false;
        }

        double entryDistance = Math.max(getHuntDesiredDistance() + HUNT_CONTINUOUS_ORBIT_ENTRY_BUFFER,
                KillAuraHandler.attackRange + 0.9D);
        double maxDistance = huntOrbitController.isActive()
                ? entryDistance + HUNT_CONTINUOUS_ORBIT_EXIT_BUFFER
                : entryDistance;
        return distance <= maxDistance;
    }

    private KillAuraOrbitProcess getHuntOrbitProcess() {
        try {
            Object primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (primary instanceof Baritone) {
                return ((Baritone) primary).getKillAuraOrbitProcess();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean requestHuntOrbitProcess(EntityLivingBase target) {
        if (mc.player == null || mc.player.isElytraFlying()
                || (mc.player.capabilities != null && mc.player.capabilities.isFlying)) {
            return false;
        }
        KillAuraOrbitProcess process = getHuntOrbitProcess();
        return process != null && process.requestOrbit(target, getHuntDesiredDistance());
    }

    private boolean driveHuntOrbitMovement(EntityPlayerSP player, EntityLivingBase target, boolean orbitPlanned) {
        if (player == null || target == null || !orbitPlanned) {
            return false;
        }
        List<BetterBlockPos> orbitNodes = getHuntOrbitLoopNodes();
        if (orbitNodes.size() < 2) {
            return false;
        }

        updateHuntOrbitStuckState(player);

        int nextIndex = chooseHuntOrbitLoopNodeIndex(player, orbitNodes);
        if (nextIndex < 0 || nextIndex >= orbitNodes.size()) {
            return false;
        }

        BetterBlockPos goal = orbitNodes.get(nextIndex);
        double goalX = goal.x + 0.5D;
        double goalY = goal.y;
        double goalZ = goal.z + 0.5D;
        double dx = player.posX - goalX;
        double dz = player.posZ - goalZ;
        boolean targetChanged = lastHuntGotoTargetEntityId != target.getEntityId();
        boolean nodeChanged = nextIndex != huntOrbitLoopNodeIndex;
        boolean gotoExpired = player.ticksExisted - huntLastOrbitGotoTick >= 8;
        boolean orbitStuck = huntOrbitStuckTicks >= 4;
        boolean farFromGoal = dx * dx + dz * dz > 3.0D;

        if (targetChanged || nodeChanged || gotoExpired || orbitStuck || farFromGoal) {
            startNavigationGoto(goalX, goalY, goalZ, true);
            huntOrbitLoopNodeIndex = nextIndex;
            huntLastOrbitGotoTick = player.ticksExisted;
            lastHuntGotoTargetEntityId = target.getEntityId();
            lastHuntGotoTargetX = goalX;
            lastHuntGotoTargetY = goalY;
            lastHuntGotoTargetZ = goalZ;
            huntMovementStopped = false;
        }
        return true;
    }

    private List<BetterBlockPos> getHuntOrbitLoopNodes() {
        KillAuraOrbitProcess process = getHuntOrbitProcess();
        if (process == null) {
            return Collections.emptyList();
        }
        return process.getNavigationLoopSnapshot();
    }

    private void updateHuntOrbitStuckState(EntityPlayerSP player) {
        if (player == null) {
            huntOrbitStuckTicks = 0;
            huntLastOrbitPlayerX = Double.NaN;
            huntLastOrbitPlayerZ = Double.NaN;
            return;
        }
        if (Double.isNaN(huntLastOrbitPlayerX) || Double.isNaN(huntLastOrbitPlayerZ)) {
            huntLastOrbitPlayerX = player.posX;
            huntLastOrbitPlayerZ = player.posZ;
            huntOrbitStuckTicks = 0;
            return;
        }
        double dx = player.posX - huntLastOrbitPlayerX;
        double dz = player.posZ - huntLastOrbitPlayerZ;
        if (dx * dx + dz * dz <= 0.0036D) {
            huntOrbitStuckTicks++;
        } else {
            huntOrbitStuckTicks = 0;
        }
        huntLastOrbitPlayerX = player.posX;
        huntLastOrbitPlayerZ = player.posZ;
    }

    private int chooseHuntOrbitLoopNodeIndex(EntityPlayerSP player, List<BetterBlockPos> orbitNodes) {
        if (player == null || orbitNodes == null || orbitNodes.isEmpty()) {
            return -1;
        }
        int nearestIndex = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < orbitNodes.size(); i++) {
            BetterBlockPos node = orbitNodes.get(i);
            double dx = player.posX - (node.x + 0.5D);
            double dz = player.posZ - (node.z + 0.5D);
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearestIndex = i;
            }
        }
        if (nearestIndex < 0) {
            return -1;
        }
        if (nearestDistSq <= 2.25D && orbitNodes.size() > 1) {
            return (nearestIndex + 1) % orbitNodes.size();
        }
        return nearestIndex;
    }

    private void stopHuntOrbitProcess() {
        KillAuraOrbitProcess process = getHuntOrbitProcess();
        if (process != null) {
            process.requestStop();
        }
    }

    private void stopHuntNavigationMode() {
        huntOrbitController.stop();
        stopHuntOrbitProcess();
        stopHuntEmbeddedNavigation();
        huntOrbitLoopNodeIndex = -1;
        huntLastOrbitGotoTick = -99999;
        huntOrbitStuckTicks = 0;
        huntLastOrbitPlayerX = Double.NaN;
        huntLastOrbitPlayerZ = Double.NaN;
    }

    private void stopHuntEmbeddedNavigation() {
        boolean hadEmbeddedGoal = lastHuntGotoTargetEntityId != -1L
                || !Double.isNaN(lastHuntGotoTargetX)
                || !Double.isNaN(lastHuntGotoTargetY)
                || !Double.isNaN(lastHuntGotoTargetZ);
        if (hadEmbeddedGoal) {
            stopNavigation();
        }
        huntMovementStopped = true;
        lastHuntGotoTargetEntityId = -1L;
        lastHuntGotoTargetX = Double.NaN;
        lastHuntGotoTargetY = Double.NaN;
        lastHuntGotoTargetZ = Double.NaN;
    }

    private void navigateHuntTowardsTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (player == null || target == null) {
            return;
        }
        if (isHuntFixedDistanceMode()) {
            double[] destination = computeFixedDistanceHuntDestination(player, target, getHuntDesiredDistance());
            double[] safeDestination = findSafeHuntNavigationDestination(destination[0], destination[1], destination[2]);
            if (safeDestination != null) {
                startNavigationGoto(safeDestination[0], safeDestination[1], safeDestination[2]);
            } else {
                startNavigationGoto(destination[0], destination[1], destination[2]);
            }
            lastHuntGotoTargetEntityId = target.getEntityId();
            lastHuntGotoTargetX = destination[0];
            lastHuntGotoTargetY = destination[1];
            lastHuntGotoTargetZ = destination[2];
        } else if (shouldRefreshHuntGoto(target)) {
            startNavigationGoto(target.posX, target.posY, target.posZ);
            lastHuntGotoTargetEntityId = target.getEntityId();
            lastHuntGotoTargetX = target.posX;
            lastHuntGotoTargetY = target.posY;
            lastHuntGotoTargetZ = target.posZ;
        }
        huntMovementStopped = false;
    }

    private double[] computeFixedDistanceHuntDestination(EntityPlayerSP player, EntityLivingBase target, double desiredDistance) {
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

        double scale = desiredDistance / Math.max(distance, 1.0E-4D);
        double destinationX = target.posX + dx * scale;
        double destinationY = target.posY + dy * scale;
        double destinationZ = target.posZ + dz * scale;
        return clipHuntDestinationXZ(target.posX, target.posZ, destinationX, destinationY, destinationZ);
    }

    private double[] clipHuntDestinationXZ(double centerX, double centerZ, double destinationX, double destinationY,
            double destinationZ) {
        if (!AutoFollowHandler.hasActiveLockChaseRestriction()
                || AutoFollowHandler.isPositionWithinActiveLockChaseBounds(destinationX, destinationZ)) {
            return new double[] { destinationX, destinationY, destinationZ };
        }

        double dx = destinationX - centerX;
        double dz = destinationZ - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-4D) {
            return new double[] { centerX, destinationY, centerZ };
        }

        double clipped[] = getClippedHuntPoint(centerX, centerZ, distance, Math.atan2(dz, dx));
        return new double[] { clipped[0], destinationY, clipped[1] };
    }

    private double[] findSafeHuntNavigationDestination(double desiredX, double desiredY, double desiredZ) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        int baseX = MathHelper.floor(desiredX);
        int baseY = MathHelper.floor(desiredY);
        int baseZ = MathHelper.floor(desiredZ);
        int maxFeetY = MathHelper.floor(mc.player.getEntityBoundingBox().minY + 0.001D) + 1;
        BlockPos bestStandPos = null;

        // Search every nearby X/Z position on the requested level first, then
        // move upward one layer at a time. A top-down scan can choose a
        // platform above an otherwise usable target floor.
        for (int dy = 0; dy <= 3 && bestStandPos == null; dy++) {
            double bestLayerScore = Double.MAX_VALUE;
            for (int radius = 0; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos candidate = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                        if (!isStandableHuntFeetPos(candidate, maxFeetY)) {
                            continue;
                        }

                        double centerX = candidate.getX() + 0.5D;
                        double centerY = candidate.getY();
                        double centerZ = candidate.getZ() + 0.5D;
                        double dxScore = centerX - desiredX;
                        double dyScore = centerY - desiredY;
                        double dzScore = centerZ - desiredZ;
                        double score = dxScore * dxScore + dzScore * dzScore + dyScore * dyScore * 0.45D;
                        if (score < bestLayerScore) {
                            bestLayerScore = score;
                            bestStandPos = candidate;
                        }
                    }
                }
            }
        }

        if (bestStandPos == null) {
            return null;
        }
        return new double[] { bestStandPos.getX() + 0.5D, bestStandPos.getY(), bestStandPos.getZ() + 0.5D };
    }

    private boolean isStandableHuntFeetPos(BlockPos standPos, int maxFeetY) {
        if (mc.world == null || standPos == null) {
            return false;
        }
        if (standPos.getY() > maxFeetY) {
            return false;
        }
        IBlockState feetState = mc.world.getBlockState(standPos);
        IBlockState headState = mc.world.getBlockState(standPos.up());
        IBlockState belowState = mc.world.getBlockState(standPos.down());

        boolean feetPassable = !feetState.getMaterial().blocksMovement();
        boolean headPassable = !headState.getMaterial().blocksMovement();
        boolean hasGround = belowState.getMaterial().blocksMovement();
        return feetPassable && headPassable && hasGround;
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

    private boolean isValidHuntCandidate(EntityPlayerSP player, EntityLivingBase entity) {
        if (player == null || entity == null || entity == player || !entity.isEntityAlive()) {
            return false;
        }
        if (isHuntNoDamageExcludedTarget(entity)) {
            return false;
        }
        if (getDistanceSqToHuntCenter(entity) > getHuntRadiusSq()) {
            return false;
        }
        if (!isWithinHuntVerticalRange(entity)) {
            return false;
        }
        // 检查是否忽略隐身目标
        if (huntIgnoreInvisible && entity.isInvisible()) {
            return false;
        }
        String filterName = getHuntFilterableEntityName(entity);
        if (huntEnableNameBlacklist
                && KillAuraHandler.getNameListMatchIndex(filterName, huntNameBlacklist) != Integer.MAX_VALUE) {
            return false;
        }
        if (huntEnableNameWhitelist) {
            return getActiveHuntWhitelistMatchIndex(filterName) != Integer.MAX_VALUE;
        }
        if (!matchesHuntTargetGroup(entity)) {
            return false;
        }
        return true;
    }

    private boolean isLockedHuntTargetStillTrackable(EntityPlayerSP player, EntityLivingBase entity) {
        return player != null
                && entity != null
                && entity != player
                && entity.isEntityAlive()
                && !entity.isDead
                && entity.world == mc.world
                && !isHuntNoDamageExcludedTarget(entity)
                && isWithinHuntVerticalRange(entity);
    }

    private boolean isWithinHuntVerticalRange(Entity entity) {
        if (entity == null) {
            return false;
        }
        double dy = entity.posY - huntCenterY;
        return dy <= huntUpRange + 1.0E-6D && -dy <= huntDownRange + 1.0E-6D;
    }

    private boolean matchesHuntTargetGroup(EntityLivingBase entity) {
        if (!huntRestrictTargetGroups) {
            return true;
        }
        if (entity instanceof net.minecraft.entity.player.EntityPlayer) {
            return huntTargetPlayers;
        }
        if (isHostileHuntTarget(entity)) {
            return huntTargetHostile;
        }
        if (isPassiveHuntTarget(entity)) {
            return huntTargetPassive;
        }
        return false;
    }

    private boolean isHostileHuntTarget(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof IMob
                || entity instanceof EntityDragon
                || entity.isCreatureType(EnumCreatureType.MONSTER, false);
    }

    private boolean isPassiveHuntTarget(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof EntityAnimal
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityWaterMob
                || entity instanceof EntityVillager
                || entity instanceof EntityGolem
                || entity.isCreatureType(EnumCreatureType.CREATURE, false)
                || entity.isCreatureType(EnumCreatureType.AMBIENT, false)
                || entity.isCreatureType(EnumCreatureType.WATER_CREATURE, false);
    }

    private int compareHuntTargets(EntityPlayerSP player, EntityLivingBase left, EntityLivingBase right) {
        if (huntEnableNameWhitelist && !huntNameWhitelist.isEmpty()) {
            int leftPriority = getActiveHuntWhitelistMatchIndex(getHuntFilterableEntityName(left));
            int rightPriority = getActiveHuntWhitelistMatchIndex(getHuntFilterableEntityName(right));
            if (leftPriority != rightPriority) {
                return Integer.compare(leftPriority, rightPriority);
            }
        }
        if (KillAuraHandler.enabled) {
            int compareByPlayer = Double.compare(getKillAuraStyleDistanceSq(player, left),
                    getKillAuraStyleDistanceSq(player, right));
            if (compareByPlayer != 0) {
                return compareByPlayer;
            }
        }
        int compareByCenter = Double.compare(getDistanceSqToHuntCenter(left), getDistanceSqToHuntCenter(right));
        if (compareByCenter != 0) {
            return compareByCenter;
        }
        return Double.compare(player.getDistanceSq(left), player.getDistanceSq(right));
    }

    private double getKillAuraStyleDistanceSq(EntityPlayerSP player, Entity entity) {
        if (player == null || entity == null) {
            return Double.MAX_VALUE;
        }
        if (KillAuraHandler.isHuntEnabled()) {
            double dx = entity.posX - player.posX;
            double dz = entity.posZ - player.posZ;
            return dx * dx + dz * dz;
        }
        return player.getDistanceSq(entity);
    }

    private AxisAlignedBB getHuntSearchBounds() {
        return new AxisAlignedBB(
                huntCenterX - huntRadius, huntCenterY - huntDownRange, huntCenterZ - huntRadius,
                huntCenterX + huntRadius, huntCenterY + huntUpRange, huntCenterZ + huntRadius);
    }

    private double getDistanceSqToHuntCenter(Entity entity) {
        if (entity == null) {
            return Double.MAX_VALUE;
        }
        double dx = entity.posX - huntCenterX;
        double dz = entity.posZ - huntCenterZ;
        return dx * dx + dz * dz;
    }

    private double getHuntRadiusSq() {
        return huntRadius * huntRadius;
    }

    private boolean shouldRefreshHuntGoto(Entity targetEntity) {
        if (targetEntity == null) {
            return false;
        }
        if (lastHuntGotoTargetEntityId != targetEntity.getEntityId()) {
            return true;
        }
        if (Double.isNaN(lastHuntGotoTargetX) || Double.isNaN(lastHuntGotoTargetY) || Double.isNaN(lastHuntGotoTargetZ)) {
            return true;
        }
        double dx = targetEntity.posX - lastHuntGotoTargetX;
        double dy = targetEntity.posY - lastHuntGotoTargetY;
        double dz = targetEntity.posZ - lastHuntGotoTargetZ;
        return dx * dx + dy * dy + dz * dz >= 1.0D;
    }

    private String getHuntFilterableEntityName(Entity entity) {
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

    private List<String> readHuntNameList(JsonObject params, String arrayKey, String textKey) {
        List<String> values = new ArrayList<>();
        if (params == null) {
            return values;
        }
        if (params.has(arrayKey) && params.get(arrayKey).isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray(arrayKey)) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                if (element.isJsonPrimitive()) {
                    addHuntNameKeyword(values, element.getAsString());
                } else if (element.isJsonObject()) {
                    addHuntNameKeyword(values,
                            readFirstHuntString(element.getAsJsonObject(), "name", "keyword", "target", "value"));
                }
            }
        } else if (params.has(arrayKey) && params.get(arrayKey).isJsonPrimitive()) {
            addHuntNameKeywordsFromText(values, params.get(arrayKey).getAsString());
        }
        if (values.isEmpty() && params.has(textKey) && params.get(textKey).isJsonPrimitive()) {
            addHuntNameKeywordsFromText(values, params.get(textKey).getAsString());
        }
        return values;
    }

    private List<HuntWhitelistTarget> readHuntWhitelistTargets(JsonObject params) {
        List<HuntWhitelistTarget> values = new ArrayList<>();
        if (params == null) {
            return values;
        }
        if (params.has("nameWhitelistEntries") && params.get("nameWhitelistEntries").isJsonArray()) {
            for (JsonElement element : params.getAsJsonArray("nameWhitelistEntries")) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    addHuntWhitelistTarget(values,
                            readFirstHuntString(object, "name", "keyword", "target", "value"),
                            readFirstHuntInt(object, 0, "killCount", "count", "kills", "targetCount"));
                } else if (element.isJsonPrimitive()) {
                    addHuntWhitelistTarget(values, element.getAsString(), 0);
                }
            }
        }
        if (!values.isEmpty()) {
            return values;
        }
        for (String name : readHuntNameList(params, "nameWhitelist", "nameWhitelistText")) {
            addHuntWhitelistTarget(values, name, 0);
        }
        return values;
    }

    private String readFirstHuntString(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        }
        return "";
    }

    private int readFirstHuntInt(JsonObject object, int defaultValue, String... keys) {
        if (object == null || keys == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            try {
                return Math.max(0, object.get(key).getAsInt());
            } catch (Exception ignored) {
                try {
                    return Math.max(0, (int) Math.round(Double.parseDouble(object.get(key).getAsString().trim())));
                } catch (Exception ignoredAgain) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private void addHuntWhitelistTarget(List<HuntWhitelistTarget> target, String rawValue, int killCount) {
        if (target == null) {
            return;
        }
        String normalized = KillAuraHandler.normalizeFilterName(rawValue);
        if (normalized.isEmpty()) {
            return;
        }
        for (int i = 0; i < target.size(); i++) {
            HuntWhitelistTarget existing = target.get(i);
            if (existing != null && existing.name.equalsIgnoreCase(normalized)) {
                target.set(i, new HuntWhitelistTarget(normalized, killCount));
                return;
            }
        }
        target.add(new HuntWhitelistTarget(normalized, killCount));
    }

    private String formatHuntWhitelistForLog() {
        if (huntWhitelistTargets.isEmpty()) {
            return huntNameWhitelist.toString();
        }
        List<String> parts = new ArrayList<>();
        for (HuntWhitelistTarget target : huntWhitelistTargets) {
            if (target == null || target.name.isEmpty()) {
                continue;
            }
            parts.add(target.hasKillLimit() ? target.name + " x" + target.killCount : target.name + " 清完");
        }
        return parts.toString();
    }

    private boolean isHuntTargetKilled(EntityLivingBase entity) {
        return entity != null && (entity.isDead || !entity.isEntityAlive() || entity.getHealth() <= 0.0F);
    }

    private void recordHuntTargetKill(EntityLivingBase entity) {
        if (entity == null || !huntEnableNameWhitelist) {
            return;
        }
        String filterName = getHuntFilterableEntityName(entity);
        int index = getAnyHuntWhitelistMatchIndex(filterName);
        if (index == Integer.MAX_VALUE) {
            return;
        }
        if (huntWaitForWhitelistRespawn && huntWhitelistRespawnWaitSatisfied) {
            huntWhitelistTargetKilledAfterWait = true;
        }
        if (huntWhitelistTargets.isEmpty()) {
            return;
        }
        int entityId = entity.getEntityId();
        if (countedHuntKillEntityIds.contains(entityId)) {
            return;
        }
        if (index < 0 || index >= huntWhitelistTargets.size()) {
            return;
        }
        countedHuntKillEntityIds.add(entityId);
        HuntWhitelistTarget target = huntWhitelistTargets.get(index);
        if (target != null && target.hasKillLimit()) {
            int next = huntWhitelistKillProgress.getOrDefault(target.name, 0) + 1;
            huntWhitelistKillProgress.put(target.name, next);
            recordDebugTrace("hunt whitelist kill -> " + target.name + " " + next + "/" + target.killCount);
        }
    }

    private int getAnyHuntWhitelistMatchIndex(String filterName) {
        if (huntWhitelistTargets.isEmpty()) {
            return KillAuraHandler.getNameListMatchIndex(filterName, huntNameWhitelist);
        }
        for (int i = 0; i < huntWhitelistTargets.size(); i++) {
            HuntWhitelistTarget target = huntWhitelistTargets.get(i);
            if (target == null || target.name.isEmpty()) {
                continue;
            }
            if (KillAuraHandler.getNameListMatchIndex(filterName, Collections.singletonList(target.name))
                    != Integer.MAX_VALUE) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private int getActiveHuntWhitelistMatchIndex(String filterName) {
        if (huntWhitelistTargets.isEmpty()) {
            return KillAuraHandler.getNameListMatchIndex(filterName, huntNameWhitelist);
        }
        for (int i = 0; i < huntWhitelistTargets.size(); i++) {
            HuntWhitelistTarget target = huntWhitelistTargets.get(i);
            if (target == null || target.name.isEmpty()
                    || (isHuntWhitelistTargetComplete(target) && !huntKillCompletionConfirmationResumed)) {
                continue;
            }
            if (KillAuraHandler.getNameListMatchIndex(filterName, Collections.singletonList(target.name))
                    != Integer.MAX_VALUE) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private boolean isHuntWhitelistTargetComplete(HuntWhitelistTarget target) {
        return target != null
                && target.hasKillLimit()
                && huntWhitelistKillProgress.getOrDefault(target.name, 0) >= target.killCount;
    }

    private boolean shouldCompleteHuntByWhitelistKillGoals() {
        if (!huntEnableNameWhitelist || huntWhitelistTargets.isEmpty()) {
            return false;
        }
        for (HuntWhitelistTarget target : huntWhitelistTargets) {
            if (target == null || target.name.isEmpty()) {
                continue;
            }
            if (target.hasKillLimit() && isHuntWhitelistTargetComplete(target)) {
                return true;
            }
        }
        return false;
    }

    private void addHuntNameKeywordsFromText(List<String> target, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String token : text.split("\\r?\\n|,|，")) {
            addHuntNameKeyword(target, token);
        }
    }

    private void addHuntNameKeyword(List<String> target, String rawValue) {
        String normalized = KillAuraHandler.normalizeFilterName(rawValue);
        if (normalized.isEmpty()) {
            return;
        }
        for (String existing : target) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        target.add(normalized);
    }

    private boolean readHuntBooleanParam(JsonObject params, String key, boolean defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int readHuntIntParam(JsonObject params, String key, int defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double readHuntDoubleParam(JsonObject params, String key, double defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            return params.get(key).getAsDouble();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String readHuntModeParam(JsonObject params, String key, String defaultValue) {
        if (params == null || key == null || !params.has(key)) {
            return defaultValue;
        }
        try {
            String mode = params.get(key).getAsString();
            if (KillAuraHandler.HUNT_MODE_APPROACH.equalsIgnoreCase(mode)) {
                return KillAuraHandler.HUNT_MODE_APPROACH;
            }
            if (KillAuraHandler.HUNT_MODE_FIXED_DISTANCE.equalsIgnoreCase(mode)) {
                return KillAuraHandler.HUNT_MODE_FIXED_DISTANCE;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }
    
    // --- 渲染搜怪范围可视化 ---
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isHunting) {
            return;
        }

        EntityPlayerSP player = mc.player;
        Entity viewer = mc.getRenderViewEntity();
        if (player == null || viewer == null) {
            return;
        }

        float partialTicks = event.getPartialTicks();
        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        if (huntShowRange) {
            drawHuntRangeVisualization(huntCenterX, huntCenterY, huntCenterZ, viewerX, viewerY, viewerZ, huntRadius);
        }
    }

    private void drawHuntRangeVisualization(double centerX, double centerY, double centerZ,
            double viewerX, double viewerY, double viewerZ, double radius) {
        double safeRadius = Math.max(0.5D, radius);
        int segments = Math.max(36, (int) Math.round(safeRadius * 10.0D));

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
        buffer.pos(centerX - viewerX, centerY + 0.05D - viewerY, centerZ - viewerZ)
                .color(0.15F, 0.75F, 1.0F, 0.10F).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double x = centerX + Math.cos(angle) * safeRadius;
            double z = centerZ + Math.sin(angle) * safeRadius;
            buffer.pos(x - viewerX, centerY + 0.05D - viewerY, z - viewerZ)
                    .color(0.15F, 0.75F, 1.0F, 0.02F).endVertex();
        }
        tessellator.draw();

        GlStateManager.glLineWidth(4.0F);
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double x = centerX + Math.cos(angle) * safeRadius;
            double z = centerZ + Math.sin(angle) * safeRadius;
            buffer.pos(x - viewerX, centerY + 0.05D - viewerY, z - viewerZ)
                    .color(1.0F, 1.0F, 0.0F, 1.0F).endVertex();
        }
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void renderHuntOrbitLoop() {
        if (!isHuntOrbitMode() || !(huntTargetEntity instanceof EntityLivingBase) || !huntTargetEntity.isEntityAlive()) {
            return;
        }
        List<Vec3d> renderLoop = null;
        KillAuraOrbitProcess process = getHuntOrbitProcess();
        if (process != null) {
            List<Vec3d> processLoop = process.getRenderedLoopView();
            if (processLoop != null && processLoop.size() >= 2) {
                renderLoop = processLoop;
            }
        }
        if (renderLoop == null || renderLoop.size() < 2) {
            renderLoop = HuntOrbitController.buildPreviewLoop((EntityLivingBase) huntTargetEntity,
                    getHuntDesiredDistance(), KillAuraHandler.getConfiguredHuntOrbitSamplePoints());
        }
        if (renderLoop.size() < 2) {
            return;
        }
        PathRenderer.drawPolyline(renderLoop, new Color(0xFF3B30), 0.95F, 3.0F, true);
    }
    // --- 新增结束 ---

    // --- 新增：跟随实体模式的核心逻辑 ---
    private void executeFollowEntityTick(EntityPlayerSP player) {
        if (mc.world == null || player == null) {
            completeFollowEntityAction();
            return;
        }

        // 检查超时
        if (followTimeoutSeconds > 0) {
            long elapsedSeconds = (System.currentTimeMillis() - followStartTime) / 1000L;
            if (elapsedSeconds >= followTimeoutSeconds) {
                zszlScriptMod.LOGGER.info("跟随实体超时，动作结束");
                completeFollowEntityAction();
                return;
            }
        }

        // 查找或更新目标实体
        if (followTargetEntity == null || !followTargetEntity.isEntityAlive()) {
            followTargetEntity = findFollowTarget(player);
            if (followTargetEntity == null) {
                if (followStopOnLost) {
                    zszlScriptMod.LOGGER.info("未找到跟随目标，动作结束");
                    completeFollowEntityAction();
                }
                return;
            }
            zszlScriptMod.LOGGER.info("找到跟随目标: {}", followTargetEntity.getName());
        }

        // 计算与目标的距离
        double distanceToTarget = player.getDistance(followTargetEntity);
        
        // 如果距离大于跟随距离，则导航到目标
        if (distanceToTarget > followDistance + 0.5D) {
            startNavigationGoto(
                    followTargetEntity.posX,
                    followTargetEntity.posY,
                    followTargetEntity.posZ);
        } else if (distanceToTarget < followDistance - 0.5D) {
            // 如果太近，停止移动
            stopNavigation();
        }
    }

    private Entity findFollowTarget(EntityPlayerSP player) {
        if (player == null || mc.world == null) {
            return null;
        }

        List<Entity> candidates = new ArrayList<>();
        AxisAlignedBB searchBox = player.getEntityBoundingBox().grow(followSearchRadius);

        for (Entity entity : mc.world.getLoadedEntityList()) {
            if (entity == null || entity == player || !entity.isEntityAlive()) {
                continue;
            }
            if (!searchBox.intersects(entity.getEntityBoundingBox())) {
                continue;
            }
            if (!isValidFollowTarget(entity)) {
                continue;
            }
            candidates.add(entity);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 如果指定了名称，优先匹配名称
        if (!followTargetName.isEmpty()) {
            for (Entity entity : candidates) {
                String entityName = entity.getName().toLowerCase(Locale.ROOT);
                if (entityName.contains(followTargetName.toLowerCase(Locale.ROOT))) {
                    return entity;
                }
            }
        }

        // 否则返回最近的目标
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity entity : candidates) {
            double dist = player.getDistanceSq(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean isValidFollowTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return false;
        }
        return matchesNearbyEntityType((EntityLivingBase) entity, followEntityType);
    }

    private void resetFollowEntityState() {
        this.isFollowingEntity = false;
        this.followEntityType = "player";
        this.followTargetName = "";
        this.followSearchRadius = 16.0D;
        this.followDistance = 3.0D;
        this.followTimeoutSeconds = 0;
        this.followStopOnLost = true;
        this.followTargetEntity = null;
        this.followStartTime = 0L;
    }

    private void completeFollowEntityAction() {
        resetFollowEntityState();
        if (shouldStopNavigationOnFinish()) {
            stopNavigation();
        }
        actionIndex++;
        applyBuiltinSequenceDelay();
        consumeDebugProgress("跟随实体动作完成");
    }

    private static final class HuntAttackSequenceExecutor {
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
                    zszlScriptMod.LOGGER.error("[hunt_attack_sequence] 执行动作失败: {}", resolvedAction.getDescription(), e);
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
                    player, this.sequence, this.stepIndex, this.actionIndex,
                    getLiteralParamKeysForAction(actionData.type));
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
                    ModUtils.simulateActionKey(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    zszlScriptMod.LOGGER.warn("[hunt_attack_sequence] 释放按键失败: {}", entry.getKey(), e);
                }
            }
        }
    }
    // --- 新增结束 ---
}

