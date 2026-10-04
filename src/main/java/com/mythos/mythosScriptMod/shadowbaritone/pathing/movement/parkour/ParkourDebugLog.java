package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.MovementState;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements.MovementParkour;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

/** Client-tick measurements and movement decisions share a dedicated session file. */
public final class ParkourDebugLog {
    public static final ParkourDebugLog INSTANCE = new ParkourDebugLog();
    private ParkourLogSession session;
    private boolean requested;
    private long tick;
    private Object world;
    private EntityPlayerSP previousPlayer;
    private int previousPlayerTick = -1;
    private double x, y, z, attribute;
    private float yaw, slipperiness;
    private boolean grounded;
    private final ParkourSpeedSampler speed = new ParkourSpeedSampler();

    private ParkourDebugLog() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> close("shutdown"), "Parkour-log-close"));
    }

    private static volatile boolean suppressed;

    /** Unit tests have no bootstrapped registry. Skip the settings lookup. */
    public static void suppressForTests(boolean value) { suppressed = value; }
    public static boolean suppressed() { return suppressed; }

    public static boolean enabled() {
        if (suppressed) return false;
        Settings settings = BaritoneAPI.getSettings();
        return settings != null && settings.parkourDebugLog.value;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public synchronized void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tick++;
            sync();
            return;
        }
        sync();
        if (session == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        if (world != mc.world || previousPlayer != p) {
            world = mc.world;
            previousPlayer = p;
            previousPlayerTick = -1;
            speed.reset();
            JsonObject context = record("world");
            context.addProperty("inWorld", p != null && world != null);
            if (p != null) {
                context.addProperty("player", p.getName());
                context.addProperty("dimension", p.dimension);
                context.addProperty("server", mc.getCurrentServerData() == null ? "singleplayer" : mc.getCurrentServerData().serverIP);
            }
            write(context);
        }
        if (p == null || world == null || mc.isGamePaused() || previousPlayerTick == p.ticksExisted) return;
        try {
            sample(mc, p);
            // Bound crash-loss and make a running capture readable without per-line disk flushes.
            if (tick % 20 == 0) {
                sampleJvm();
                session.flush();
            }
        } catch (Exception ex) {
            failed(ex);
        }
    }

    private void sample(Minecraft mc, EntityPlayerSP p) {
        boolean consecutive = previousPlayerTick >= 0 && p.ticksExisted == previousPlayerTick + 1;
        double dx = p.posX - x, dy = p.posY - y, dz = p.posZ - z;
        double horizontal = Math.hypot(dx, dz);
        boolean discontinuity = consecutive && (horizontal > 2D || Math.abs(dy) > 2D);
        double movementAttribute = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        BlockPos supportPos = new BlockPos(p.posX, p.getEntityBoundingBox().minY - 0.01D, p.posZ);
        IBlockState support = mc.world.getBlockState(supportPos);
        float slip = support.getBlock().getSlipperiness(support, mc.world, supportPos, p);
        boolean eligible = consecutive && !discontinuity && grounded && p.onGround && p.isSprinting()
                && !p.collidedHorizontally && !p.isInWater() && !p.isInLava() && !p.isOnLadder()
                && !p.capabilities.isFlying && !p.isRiding() && !p.isElytraFlying() && !p.isSneaking()
                && p.movementInput.moveForward > 0.9F && Math.abs(p.movementInput.moveStrafe) < 0.01F
                && !p.movementInput.jump && Math.abs(dy) < 0.001D
                && Math.abs(MathHelper.wrapDegrees(p.rotationYaw - yaw)) < 1F
                && Math.abs(movementAttribute - attribute) < 1E-6D && Math.abs(slip - slipperiness) < 1E-6F;
        speed.sample(horizontal, eligible);
        JsonObject row = record("tick");
        row.addProperty("playerTick", p.ticksExisted);
        row.addProperty("worldTick", mc.world.getTotalWorldTime());
        row.addProperty("dimension", p.dimension);
        row.add("position", vector(2, p.posX, p.posY, p.posZ));
        row.add("positionExact", vector(6, p.posX, p.posY, p.posZ));
        row.add("rotation", vector(2, p.rotationYaw, p.rotationPitch));
        row.add("motion", vector(6, p.motionX, p.motionY, p.motionZ));
        row.addProperty("deltaValid", consecutive && !discontinuity);
        row.addProperty("discontinuitySuspected", discontinuity);
        row.add("delta", consecutive ? vector(6, dx, dy, dz) : JsonNull.INSTANCE);
        number(row, "horizontalBlocksPerTick", consecutive ? horizontal : Double.NaN);
        number(row, "horizontalBlocksPerSecondAt20Tps", consecutive ? horizontal * 20D : Double.NaN);
        number(row, "movementSpeedAttribute", movementAttribute);
        number(row, "groundSlipperiness", slip);
        row.addProperty("groundSprintSampleEligible", eligible);
        row.addProperty("groundSprintStable", speed.isStable());
        row.addProperty("groundSprintStableTicks", speed.getStableTicks());
        number(row, "observedGroundSprintPlateau", speed.getEstimate());
        row.addProperty("onGround", p.onGround);
        row.addProperty("sprinting", p.isSprinting());
        row.addProperty("sneaking", p.isSneaking());
        row.addProperty("flying", p.capabilities.isFlying);
        row.addProperty("inWater", p.isInWater());
        row.addProperty("inLava", p.isInLava());
        row.addProperty("health", p.getHealth());
        row.addProperty("hurtTime", p.hurtTime);
        row.addProperty("burning", p.isBurning());
        AxisAlignedBB body=p.getEntityBoundingBox();
        row.add("body",vector(9,body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ));
        JsonArray lavaContacts=new JsonArray();
        for (BlockPos pos:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY,body.minZ),
                new BlockPos(body.maxX,body.maxY,body.maxZ))) {
            IBlockState state=mc.world.getBlockState(pos);
            if (state.getMaterial()!=net.minecraft.block.material.Material.LAVA) continue;
            AxisAlignedBB liquid=new AxisAlignedBB(pos);
            if (body.intersects(liquid))
                lavaContacts.add(vector(9,liquid.minX,liquid.minY,liquid.minZ,liquid.maxX,liquid.maxY,liquid.maxZ));
        }
        row.add("lavaContacts",lavaContacts);
        row.addProperty("lavaContactRule","full_body_block_cell");
        row.addProperty("onLadder", p.isOnLadder());
        row.addProperty("collidedHorizontally", p.collidedHorizontally);
        row.addProperty("collidedVertically", p.collidedVertically);
        number(row, "fallDistance", p.fallDistance);
        row.addProperty("foodLevel", p.getFoodStats().getFoodLevel());
        row.add("supportPosition", vector(0, supportPos.getX(), supportPos.getY(), supportPos.getZ()));
        row.addProperty("supportState", support.toString());
        JsonArray effects = new JsonArray();
        for (PotionEffect effect : p.getActivePotionEffects()) effects.add(effect.toString());
        row.add("effects", effects);
        JsonObject input = new JsonObject();
        number(input, "forward", p.movementInput.moveForward);
        number(input, "strafe", p.movementInput.moveStrafe);
        input.addProperty("jump", p.movementInput.jump);
        input.addProperty("sneak", p.movementInput.sneak);
        row.add("input", input);
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        JsonObject forced = new JsonObject();
        for (Input key : Input.values()) forced.addProperty(key.name(), baritone.getInputOverrideHandler().isInputForcedDown(key));
        row.add("forcedInput", forced);
        com.mythos.mythosScriptMod.shadowbaritone.api.pathing.path.IPathExecutor executor = baritone.getPathingBehavior().getCurrent();
        if (executor != null && executor.getPosition() < executor.getPath().movements().size()) {
            com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.IMovement active = executor.getPath().movements().get(executor.getPosition());
            JsonObject navigation = new JsonObject();
            navigation.addProperty("type", active.getClass().getSimpleName());
            navigation.addProperty("index", executor.getPosition());
            navigation.add("source", vector(0, active.getSrc().x, active.getSrc().y, active.getSrc().z));
            navigation.add("destination", vector(0, active.getDest().x, active.getDest().y, active.getDest().z));
            row.add("movement", navigation);
        }
        MovementParkour movement = ParkourRuntimeHelper.getActiveParkourMovement(baritone);
        row.add("parkour", movement == null ? JsonNull.INSTANCE : movementContext(movement));
        write(row);
        x = p.posX; y = p.posY; z = p.posZ; yaw = p.rotationYaw;
        grounded = p.onGround; attribute = movementAttribute; slipperiness = slip;
        previousPlayerTick = p.ticksExisted;
    }

    public synchronized void decision(MovementParkour movement, MovementState state) {
        if (!enabled() || session == null) return;
        JsonObject row = record("decision");
        row.add("parkour", movementContext(movement));
        row.addProperty("status", String.valueOf(state.getStatus()));
        state.getTarget().getRotation().ifPresent(r -> row.add("targetRotation", vector(2, r.getYaw(), r.getPitch())));
        JsonObject inputs = new JsonObject();
        state.getInputStates().forEach((key, value) -> inputs.addProperty(key.name(), value));
        row.add("requestedInput", inputs);
        write(row);
    }

    public synchronized void event(String message) {
        if (suppressed) {
            if (!Boolean.getBoolean("parkour.test.quiet"))
                System.out.println("[parkour-test] " + message);
            return;
        }
        if (!enabled() || session == null) return;
        JsonObject row = record("parkour_event");
        row.addProperty("message", message);
        row.addProperty("thread", Thread.currentThread().getName());
        write(row);
    }

    /** Worker wall time and thread CPU are distinct from time waiting in its queue. */
    public static <T> T profile(String label, long submittedAt, Supplier<T> work) {
        if (!enabled()) return work.get();
        ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
        long started = System.nanoTime();
        long cpuStart = cpu.isCurrentThreadCpuTimeSupported() && cpu.isThreadCpuTimeEnabled()
                ? cpu.getCurrentThreadCpuTime() : -1;
        com.sun.management.ThreadMXBean allocation = cpu instanceof com.sun.management.ThreadMXBean
                ? (com.sun.management.ThreadMXBean) cpu : null;
        boolean measureAllocation = allocation != null && allocation.isThreadAllocatedMemorySupported()
                && allocation.isThreadAllocatedMemoryEnabled();
        long thread = Thread.currentThread().getId();
        long bytes = measureAllocation ? allocation.getThreadAllocatedBytes(thread) : -1;
        boolean completed = false;
        try {
            T result = work.get();
            completed = true;
            return result;
        } finally {
            long finished = System.nanoTime();
            long cpuUsed = cpuStart < 0 ? -1 : cpu.getCurrentThreadCpuTime() - cpuStart;
            long allocated = bytes < 0 ? -1 : allocation.getThreadAllocatedBytes(thread) - bytes;
            INSTANCE.event("search_timing " + label + " queueNanos=" + (started - submittedAt)
                    + " solveNanos=" + (finished - started) + " cpuNanos=" + cpuUsed
                    + " allocatedBytes=" + allocated + " completed=" + completed
                    + " interrupted=" + Thread.currentThread().isInterrupted());
        }
    }

    private void sampleJvm() throws IOException {
        JsonObject row = record("jvm");
        Runtime runtime = Runtime.getRuntime();
        row.addProperty("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        row.addProperty("heapCommittedBytes", runtime.totalMemory());
        row.addProperty("heapMaxBytes", runtime.maxMemory());
        long count = 0, millis = 0;
        for (java.lang.management.GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, gc.getCollectionCount());
            millis += Math.max(0, gc.getCollectionTime());
        }
        row.addProperty("gcCount", count);
        row.addProperty("gcMillis", millis);
        session.write(row);
    }

    private JsonObject movementContext(MovementParkour movement) {
        JsonObject value = new JsonObject();
        ParkourJumpCandidate c = movement.getCandidate();
        value.addProperty("id", Integer.toHexString(System.identityHashCode(movement)));
        value.addProperty("type", c.getType().name());
        value.addProperty("phase", movement.getExecutionPhaseName());
        value.addProperty("failure", movement.getLastFailureReason().name());
        value.addProperty("segmentIndex", movement.getActiveSegmentIndex());
        value.addProperty("chainLength", c.getChainLength());
        value.add("source", vector(0, c.getSrc().x, c.getSrc().y, c.getSrc().z));
        value.add("destination", vector(0, c.getDest().x, c.getDest().y, c.getDest().z));
        value.addProperty("requiresSprint", c.requiresSprint());
        value.add("launchWindow", vector(6, c.getLaunchWindow().getMinProgress(), c.getLaunchWindow().getIdealProgress(), c.getLaunchWindow().getMaxProgress()));
        JsonArray route = new JsonArray();
        for (net.minecraft.util.math.Vec3d point : c.getRoutePoints()) route.add(vector(6, point.x, point.y, point.z));
        value.add("route", route);
        return value;
    }

    private void sync() {
        boolean enable = enabled();
        if (enable == requested) return;
        requested = enable;
        if (!enable) { close("disabled"); return; }
        previousPlayer = null;
        previousPlayerTick = -1;
        world = null;
        speed.reset();
        try {
            session = new ParkourLogSession(Minecraft.getMinecraft().mcDataDir.toPath().resolve("logs/parkour"));
            JsonObject start = record("session_start");
            start.addProperty("schema", 1);
            start.addProperty("file", session.getPath().toAbsolutePath().toString());
            start.addProperty("sampling", "client END tick; delta is displacement, motion is post-physics velocity; rotation=[yaw,pitch] degrees");
            start.addProperty("speedCalibration", "6 consecutive stable straight ground sprint samples; observed plateau only, not guaranteed maximum or jump range; >2 block/tick deltas flagged as discontinuities");
            JsonObject settings = new JsonObject();
            for (Settings.Setting<?> setting : BaritoneAPI.getSettings().allSettings) {
                if (setting.value instanceof Number || setting.value instanceof Boolean || setting.value instanceof Enum) {
                    settings.addProperty(setting.getName(), String.valueOf(setting.value));
                }
            }
            start.add("settings", settings);
            session.write(start);
            sampleJvm();
            session.flush();
        } catch (Exception ex) { failed(ex); }
    }

    private JsonObject record(String type) {
        JsonObject row = new JsonObject();
        row.addProperty("type", type);
        row.addProperty("timestampMs", System.currentTimeMillis());
        row.addProperty("clientTick", tick);
        return row;
    }

    private void write(JsonObject row) {
        if (session == null) return;
        try { session.write(row); } catch (IOException ex) { failed(ex); }
    }

    private synchronized void close(String reason) {
        if (session == null) return;
        try {
            sampleJvm();
            JsonObject end = record("session_end");
            end.addProperty("reason", reason);
            session.write(end);
        } catch (IOException ex) {
            mythosScriptMod.LOGGER.warn("Cannot close parkour debug log", ex);
        } finally {
            try { session.close(); } catch (IOException ex) {
                mythosScriptMod.LOGGER.warn("Cannot close parkour debug log", ex);
            }
            session = null;
        }
    }

    private void failed(Exception ex) {
        // One diagnostic only; disable retries until the user toggles again.
        mythosScriptMod.LOGGER.warn("Parkour debug logging stopped after an I/O or sampling error", ex);
        close("error");
        BaritoneAPI.getSettings().parkourDebugLog.value = false;
    }

    private static void number(JsonObject object, String key, double value) {
        object.add(key, Double.isFinite(value) ? new JsonPrimitive(decimal(value, 6)) : JsonNull.INSTANCE);
    }

    private static BigDecimal decimal(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP);
    }

    private static JsonArray vector(int places, double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(Double.isFinite(value) ? new JsonPrimitive(decimal(value, places)) : JsonNull.INSTANCE);
        return array;
    }
}
