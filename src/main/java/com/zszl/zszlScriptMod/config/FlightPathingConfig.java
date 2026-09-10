package com.zszl.zszlScriptMod.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Per-profile controls for linking Baritone flight pathing with the fly feature. */
public final class FlightPathingConfig {

    public static boolean autoEnableFly = true;
    public static boolean autoDisableFlyOnArrival = true;
    public static boolean keepFlyEnabledOnDisconnect = true;
    public static int arrivalRange = 2;

    private FlightPathingConfig() {
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("baritone_flight_pathing.json").toFile();
    }

    public static void load() {
        applyDefaults();
        try {
            File file = getConfigFile();
            if (!file.exists()) {
                return;
            }
            JsonObject json = new JsonParser().parse(new FileReader(file)).getAsJsonObject();
            if (json.has("autoEnableFly")) {
                autoEnableFly = json.get("autoEnableFly").getAsBoolean();
            }
            if (json.has("autoDisableFlyOnArrival")) {
                autoDisableFlyOnArrival = json.get("autoDisableFlyOnArrival").getAsBoolean();
            }
            if (json.has("keepFlyEnabledOnDisconnect")) {
                keepFlyEnabledOnDisconnect = json.get("keepFlyEnabledOnDisconnect").getAsBoolean();
            }
            if (json.has("arrivalRange")) {
                arrivalRange = json.get("arrivalRange").getAsInt();
            }
            normalize();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载飞行寻路配置失败", e);
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
            json.addProperty("autoEnableFly", autoEnableFly);
            json.addProperty("autoDisableFlyOnArrival", autoDisableFlyOnArrival);
            json.addProperty("keepFlyEnabledOnDisconnect", keepFlyEnabledOnDisconnect);
            json.addProperty("arrivalRange", arrivalRange);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存飞行寻路配置失败", e);
        }
    }

    public static void applyDefaults() {
        autoEnableFly = true;
        autoDisableFlyOnArrival = true;
        keepFlyEnabledOnDisconnect = true;
        arrivalRange = 2;
    }

    public static void normalize() {
        arrivalRange = Math.max(0, Math.min(16, arrivalRange));
    }
}
