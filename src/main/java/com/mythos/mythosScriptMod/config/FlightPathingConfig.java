package com.mythos.mythosScriptMod.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.mythosScriptMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Per-profile controls for linking Baritone flight pathing with the fly feature. */
public final class FlightPathingConfig {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public boolean autoEnableFly = true;
        public boolean autoDisableFlyOnArrival = true;
        public boolean keepFlyEnabledOnDisconnect = true;
        public int arrivalRange = 2;
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) FlightPathingConfig::load);
            return config;
        }
    }



    private FlightPathingConfig() {
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("baritone_flight_pathing.json").toFile();
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
            if (json.has("autoEnableFly")) {
                cfg().autoEnableFly = json.get("autoEnableFly").getAsBoolean();
            }
            if (json.has("autoDisableFlyOnArrival")) {
                cfg().autoDisableFlyOnArrival = json.get("autoDisableFlyOnArrival").getAsBoolean();
            }
            if (json.has("keepFlyEnabledOnDisconnect")) {
                cfg().keepFlyEnabledOnDisconnect = json.get("keepFlyEnabledOnDisconnect").getAsBoolean();
            }
            if (json.has("arrivalRange")) {
                cfg().arrivalRange = json.get("arrivalRange").getAsInt();
            }
            normalize();
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载飞行寻路配置失败", e);
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
            json.addProperty("autoEnableFly", cfg().autoEnableFly);
            json.addProperty("autoDisableFlyOnArrival", cfg().autoDisableFlyOnArrival);
            json.addProperty("keepFlyEnabledOnDisconnect", cfg().keepFlyEnabledOnDisconnect);
            json.addProperty("arrivalRange", cfg().arrivalRange);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存飞行寻路配置失败", e);
        }
    }

    public static void applyDefaults() {
        cfg().autoEnableFly = true;
        cfg().autoDisableFlyOnArrival = true;
        cfg().keepFlyEnabledOnDisconnect = true;
        cfg().arrivalRange = 2;
    }

    public static void normalize() {
        cfg().arrivalRange = Math.max(0, Math.min(16, cfg().arrivalRange));
    }
}
