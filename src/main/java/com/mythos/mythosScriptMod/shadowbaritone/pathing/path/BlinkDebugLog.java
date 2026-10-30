package com.mythos.mythosScriptMod.shadowbaritone.pathing.path;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mythos.mythosScriptMod.config.BlinkPathingConfig;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.Settings;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourLogSession;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Per-tick blink-pathing telemetry: player state, route geometry, sweep probes,
 * node credit, and the terrain column under every sampled point — everything
 * needed to reconstruct why a hop landed where it did.
 *
 * Toggle: Baritone setting {@code blinkDebugLog}. Output: one timestamped
 * JSONL file under {@code logs/blink/}, identical layout to ParkourDebugLog.
 */
public final class BlinkDebugLog {
    public static final BlinkDebugLog INSTANCE = new BlinkDebugLog();

    private ParkourLogSession session;
    private boolean requested;
    private long tick;

    private BlinkDebugLog() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> close("shutdown"), "Blink-log-close"));
    }

    private static volatile boolean suppressed;

    /** Unit tests have no bootstrapped registry. Skip the settings lookup. */
    public static void suppressForTests(boolean value) { suppressed = value; }
    public static boolean suppressed() { return suppressed; }

    public static boolean enabled() {
        if (suppressed) return false;
        Settings settings = BaritoneAPI.getSettings();
        return settings != null && settings.blinkDebugLog.value;
    }

    /** Called once per client tick from PathingBehavior. */
    public synchronized void onTick() {
        tick++;
        sync();
    }

    /** Called at the top of every blinkAlongPath invocation. */
    public synchronized void beginHop(EntityPlayerSP player, int pathPosition,
            List<BetterBlockPos> positions) {
        if (!enabled() || session == null || player == null) return;
        JsonObject row = record("hop_begin");
        row.addProperty("pathPosition", pathPosition);
        row.addProperty("pathLength", positions.size());
        row.add("playerPos", vector(6, player.posX, player.posY, player.posZ));
        row.add("playerRot", vector(2, player.rotationYaw, player.rotationPitch));
        row.add("playerBox", box(player.getEntityBoundingBox()));
        row.addProperty("onGround", player.onGround);
        row.addProperty("collidedH", player.collidedHorizontally);
        row.addProperty("collidedV", player.collidedVertically);
        BetterBlockPos node = positions.get(Math.min(pathPosition, positions.size() - 1));
        row.add("targetNode", node(node));
        // Terrain column under the player: block states from feet-1 to feet+2.
        JsonArray terrain = new JsonArray();
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        for (int dy = -1; dy <= 2; dy++) {
            JsonObject cell = new JsonObject();
            BlockPos bp = new BlockPos(px, py + dy, pz);
            IBlockState state = player.world.getBlockState(bp);
            cell.addProperty("pos", bp.getX() + "," + bp.getY() + "," + bp.getZ());
            cell.addProperty("state", state.getBlock().getRegistryName() + "#" + state.getBlock().getMetaFromState(state));
            cell.addProperty("collides", state.getMaterial().blocksMovement());
            terrain.add(cell);
        }
        row.add("terrainColumn", terrain);
        write(row);
    }

    /** Route polyline before the margin lift is applied. */
    public synchronized void route(List<Vec3d> route, int prefixEnd, Vec3d cursor) {
        if (!enabled() || session == null) return;
        JsonObject row = record("route");
        row.addProperty("points", route.size());
        JsonArray pts = new JsonArray();
        for (int i = 0; i < route.size(); i++) {
            JsonObject p = new JsonObject();
            Vec3d v = route.get(i);
            p.add("v", vector(6, v.x, v.y, v.z));
            if (i == prefixEnd) p.addProperty("hopEnd", true);
            pts.add(p);
        }
        row.add("points", pts);
        if (cursor != null) row.add("cursor", vector(6, cursor.x, cursor.y, cursor.z));
        write(row);
    }

    /** Margin decision: ground height, wanted lift, fitted lift. */
    public synchronized void margin(double groundY, double wanted, double fitted, double applied) {
        if (!enabled() || session == null) return;
        JsonObject row = record("margin");
        number(row, "groundY", groundY);
        number(row, "wanted", wanted);
        number(row, "fitted", fitted);
        number(row, "applied", applied);
        write(row);
    }

    /** Per-leg sweep outcome inside a hop. */
    public synchronized void leg(int legIndex, Vec3d from, Vec3d target,
            double lx, double ly, double lz, double hop, double moved,
            double posX, double posY, double posZ) {
        if (!enabled() || session == null) return;
        JsonObject row = record("leg");
        row.addProperty("index", legIndex);
        row.add("from", vector(6, from.x, from.y, from.z));
        row.add("target", vector(6, target.x, target.y, target.z));
        row.add("delta", vector(6, lx, ly, lz));
        number(row, "hopBudget", hop);
        number(row, "moved", moved);
        row.add("landedAt", vector(6, posX, posY, posZ));
        write(row);
    }

    /** Node credit event. */
    public synchronized void credit(int pathPosition, BetterBlockPos node, double distSq) {
        if (!enabled() || session == null) return;
        JsonObject row = record("credit");
        row.addProperty("pathPosition", pathPosition);
        row.add("node", node(node));
        number(row, "distSq", distSq);
        write(row);
    }

    /** Stall escape: blinkStuckTicks reached threshold. */
    public synchronized void stallEscape(int pathPosition, int stuckTicks) {
        if (!enabled() || session == null) return;
        JsonObject row = record("stall_escape");
        row.addProperty("pathPosition", pathPosition);
        row.addProperty("stuckTicks", stuckTicks);
        write(row);
    }

    /** blinkSurfaceY result for a single node. */
    public synchronized void surfaceY(BetterBlockPos node, double surfaceY) {
        if (!enabled() || session == null) return;
        JsonObject row = record("surface_y");
        row.add("node", node(node));
        number(row, "surfaceY", surfaceY);
        write(row);
    }

    /** End-of-hop summary. */
    public synchronized void endHop(EntityPlayerSP player, int pathPosition,
            boolean moved, double blinkLift, double blinkHoldY) {
        if (!enabled() || session == null || player == null) return;
        JsonObject row = record("hop_end");
        row.addProperty("pathPosition", pathPosition);
        row.addProperty("moved", moved);
        number(row, "blinkLift", blinkLift);
        number(row, "blinkHoldY", blinkHoldY);
        row.add("pos", vector(6, player.posX, player.posY, player.posZ));
        write(row);
        try { if (session != null) session.flush(); } catch (IOException ignored) {}
    }

    /** Free-form event (escape-embedded, nudge, etc.). */
    public synchronized void event(String message) {
        if (suppressed) {
            if (!Boolean.getBoolean("blink.test.quiet"))
                System.out.println("[blink-test] " + message);
            return;
        }
        if (!enabled() || session == null) return;
        JsonObject row = record("blink_event");
        row.addProperty("message", message);
        row.addProperty("thread", Thread.currentThread().getName());
        write(row);
    }

    // ------------------------------------------------------------------ internal

    private void sync() {
        boolean enable = enabled();
        if (enable == requested) return;
        requested = enable;
        if (!enable) { close("disabled"); return; }
        try {
            session = new ParkourLogSession(
                    Minecraft.getMinecraft().mcDataDir.toPath().resolve("logs/blink"));
            JsonObject start = record("session_start");
            start.addProperty("schema", 1);
            start.addProperty("file", session.getPath().toAbsolutePath().toString());
            start.addProperty("sampling",
                    "one record per blinkAlongPath invocation; hop_begin carries player state + "
                    + "terrain column; route carries the full polyline with hopEnd flag; "
                    + "margin carries groundUnder/wanted/fitted/applied; leg carries per-leg "
                    + "sweep deltas and landing point; credit/stall_escape record position changes.");
            JsonObject settings = new JsonObject();
            settings.addProperty("stepDistance", BlinkPathingConfig.cfg().stepDistance);
            settings.addProperty("maxHorizontalStep", BlinkPathingConfig.cfg().maxHorizontalStep);
            settings.addProperty("maxVerticalStep", BlinkPathingConfig.cfg().maxVerticalStep);
            settings.addProperty("tickInterval", BlinkPathingConfig.cfg().tickInterval);
            settings.addProperty("routeHeightRange", BlinkPathingConfig.cfg().routeHeightRange);
            settings.addProperty("collisionMargin", BlinkPathingConfig.cfg().collisionMargin);
            start.add("blinkConfig", settings);
            session.write(start);
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
            JsonObject end = record("session_end");
            end.addProperty("reason", reason);
            session.write(end);
        } catch (IOException ex) {
            mythosScriptMod.LOGGER.warn("Cannot close blink debug log", ex);
        } finally {
            try { session.close(); } catch (IOException ex) {
                mythosScriptMod.LOGGER.warn("Cannot close blink debug log", ex);
            }
            session = null;
        }
    }

    private void failed(Exception ex) {
        mythosScriptMod.LOGGER.warn("Blink debug logging stopped after an I/O error", ex);
        close("error");
        BaritoneAPI.getSettings().blinkDebugLog.value = false;
    }

    // ------------------------------------------------------------------ helpers

    private static JsonObject node(BetterBlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x); o.addProperty("y", p.y); o.addProperty("z", p.z);
        return o;
    }

    private static JsonArray box(AxisAlignedBB b) {
        return vector(9, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
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
