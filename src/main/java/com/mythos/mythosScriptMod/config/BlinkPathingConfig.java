package com.mythos.mythosScriptMod.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Per-profile controls for Baritone blink (teleport) pathing. */
public final class BlinkPathingConfig {

    /** Distance advanced along the route per teleport, in blocks. */
    public static double stepDistance = 5.0D;
    /** Maximum horizontal component of a single teleport. */
    public static double maxHorizontalStep = 8.0D;
    /** Maximum vertical component of a single teleport. */
    public static double maxVerticalStep = 6.0D;
    /** Ticks to wait between teleports. */
    public static int tickInterval = 1;
    /** Route height range used while blink pathing (replaces Baritone's routeHeightRange). */
    public static int routeHeightRange = 10;
    /**
     * Clearance in blocks above the ground under the route, not above the
     * route's own height. A 2-block gap already holds the player, so the hover
     * there collapses toward 0. Ignored at the destination node.
     */
    public static double collisionMargin = 2.0D;

    private BlinkPathingConfig() {
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("baritone_blink_pathing.json").toFile();
    }

    public static void load() {
        applyDefaults();
        try {
            File file = getConfigFile();
            if (!file.exists()) {
                return;
            }
            JsonObject json;
            try (FileReader reader = new FileReader(file)) {
                json = new JsonParser().parse(reader).getAsJsonObject();
            }
            if (json.has("stepDistance")) {
                stepDistance = json.get("stepDistance").getAsDouble();
            }
            if (json.has("maxHorizontalStep")) {
                maxHorizontalStep = json.get("maxHorizontalStep").getAsDouble();
            }
            if (json.has("maxVerticalStep")) {
                maxVerticalStep = json.get("maxVerticalStep").getAsDouble();
            }
            if (json.has("tickInterval")) {
                tickInterval = json.get("tickInterval").getAsInt();
            }
            if (json.has("routeHeightRange")) {
                routeHeightRange = json.get("routeHeightRange").getAsInt();
            }
            if (json.has("collisionMargin")) {
                collisionMargin = json.get("collisionMargin").getAsDouble();
            }
            normalize();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载瞬移寻路配置失败", e);
        }
    }

    public static void save() {
        normalize();
        try {
            File file = getConfigFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("stepDistance", stepDistance);
            json.addProperty("maxHorizontalStep", maxHorizontalStep);
            json.addProperty("maxVerticalStep", maxVerticalStep);
            json.addProperty("tickInterval", tickInterval);
            json.addProperty("routeHeightRange", routeHeightRange);
            json.addProperty("collisionMargin", collisionMargin);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存瞬移寻路配置失败", e);
        }
    }

    public static void applyDefaults() {
        stepDistance = 5.0D;
        maxHorizontalStep = 8.0D;
        maxVerticalStep = 6.0D;
        tickInterval = 1;
        routeHeightRange = 10;
        collisionMargin = 0.0D;
    }

    public static void normalize() {
        stepDistance = clamp(stepDistance, 0.5D, 64.0D);
        maxHorizontalStep = clamp(maxHorizontalStep, 0.5D, 128.0D);
        maxVerticalStep = clamp(maxVerticalStep, 0.5D, 128.0D);
        tickInterval = Math.max(1, Math.min(20, tickInterval));
        routeHeightRange = Math.max(1, Math.min(100, routeHeightRange));
        collisionMargin = clamp(collisionMargin, 0.0D, 10.0D);
    }

    /** Blocks travelled per tick, used for movement cost estimates. */
    public static double blocksPerTick() {
        return Math.max(0.05D, stepDistance / Math.max(1, tickInterval));
    }

    private static double clamp(double value, double minimum, double maximum) {
        double safe = Double.isNaN(value) || Double.isInfinite(value) ? minimum : value;
        return Math.max(minimum, Math.min(maximum, safe));
    }
}
