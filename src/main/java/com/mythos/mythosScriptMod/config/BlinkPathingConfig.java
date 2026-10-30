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

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        /** Distance advanced along the route per teleport, in blocks. */
        public double stepDistance = 5.0D;
        /** Maximum horizontal component of a single teleport. */
        public double maxHorizontalStep = 8.0D;
        /** Maximum vertical component of a single teleport. */
        public double maxVerticalStep = 6.0D;
        /** Ticks to wait between teleports. */
        public int tickInterval = 1;
        /** Route height range used while blink pathing (replaces Baritone's routeHeightRange). */
        public int routeHeightRange = 10;
        /**
         * Clearance in blocks above the ground under the route, not above the
         * route's own height. A 2-block gap already holds the player, so the hover
         * there collapses toward 0. Ignored at the destination node.
         */
        public double collisionMargin = 2.0D;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) BlinkPathingConfig::load);
            return config;
        }
    }



    private BlinkPathingConfig() {
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("baritone_blink_pathing.json").toFile();
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
                cfg().stepDistance = json.get("stepDistance").getAsDouble();
            }
            if (json.has("maxHorizontalStep")) {
                cfg().maxHorizontalStep = json.get("maxHorizontalStep").getAsDouble();
            }
            if (json.has("maxVerticalStep")) {
                cfg().maxVerticalStep = json.get("maxVerticalStep").getAsDouble();
            }
            if (json.has("tickInterval")) {
                cfg().tickInterval = json.get("tickInterval").getAsInt();
            }
            if (json.has("routeHeightRange")) {
                cfg().routeHeightRange = json.get("routeHeightRange").getAsInt();
            }
            if (json.has("collisionMargin")) {
                cfg().collisionMargin = json.get("collisionMargin").getAsDouble();
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
            json.addProperty("stepDistance", cfg().stepDistance);
            json.addProperty("maxHorizontalStep", cfg().maxHorizontalStep);
            json.addProperty("maxVerticalStep", cfg().maxVerticalStep);
            json.addProperty("tickInterval", cfg().tickInterval);
            json.addProperty("routeHeightRange", cfg().routeHeightRange);
            json.addProperty("collisionMargin", cfg().collisionMargin);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存瞬移寻路配置失败", e);
        }
    }

    public static void applyDefaults() {
        cfg().stepDistance = 5.0D;
        cfg().maxHorizontalStep = 8.0D;
        cfg().maxVerticalStep = 6.0D;
        cfg().tickInterval = 1;
        cfg().routeHeightRange = 10;
        cfg().collisionMargin = 0.0D;
    }

    public static void normalize() {
        cfg().stepDistance = clamp(cfg().stepDistance, 0.5D, 64.0D);
        cfg().maxHorizontalStep = clamp(cfg().maxHorizontalStep, 0.5D, 128.0D);
        cfg().maxVerticalStep = clamp(cfg().maxVerticalStep, 0.5D, 128.0D);
        cfg().tickInterval = Math.max(1, Math.min(20, cfg().tickInterval));
        cfg().routeHeightRange = Math.max(1, Math.min(100, cfg().routeHeightRange));
        cfg().collisionMargin = clamp(cfg().collisionMargin, 0.0D, 10.0D);
    }


    private static double clamp(double value, double minimum, double maximum) {
        double safe = Double.isNaN(value) || Double.isInfinite(value) ? minimum : value;
        return Math.max(minimum, Math.min(maximum, safe));
    }
}
