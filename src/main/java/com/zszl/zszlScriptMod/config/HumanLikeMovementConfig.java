package com.zszl.zszlScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模拟真人移动配置（按 Profile 保存）。
 */
public class HumanLikeMovementConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static HumanLikeMovementConfig INSTANCE = new HumanLikeMovementConfig();

    /**
     * 总开关。
     */
    public boolean enabled = false;

    /**
     * 视角每 tick 最小转速。
     */
    public float minTurnSpeed = 2.1F;

    /**
     * 视角每 tick 最大转速。
     */
    public float maxTurnSpeed = 10.5F;

    /**
     * 视角微抖动强度。
     */
    public float viewJitter = 0.34F;

    /**
     * 视角轻微过冲强度。
     */
    public float turnOvershoot = 0.12F;

    /**
     * 前进输入加速系数，越大越快贴近目标速度。
     */
    public float acceleration = 0.18F;

    /**
     * 松开时减速系数。
     */
    public float deceleration = 0.14F;

    /**
     * 锐角转弯时的减速强度。
     */
    public float turnSlowdown = 0.52F;

    /**
     * 狭窄位置的额外减速强度。
     */
    public float narrowSlowdown = 0.42F;

    /**
     * 前进时注入轻微横移的概率。
     */
    public float strafeJitterChance = 0.08F;

    /**
     * 横移扰动强度。
     */
    public float strafeJitterStrength = 0.22F;

    /**
     * 长直线路段的节奏变化强度。
     */
    public float rhythmVariation = 0.11F;

    /**
     * 长直线路段“贴左/贴右”式路径偏置强度。
     */
    public float corridorBiasStrength = 0.12F;

    /**
     * 路径随机化强度，用于 A* Favoring 的随机偏好场。
     */
    public float routeNoiseStrength = 0.35F;

    /**
     * 路径随机化尺度，越大越倾向形成较大范围的路线偏好区域。
     */
    public int routeNoiseScale = 10;

    /**
     * 中途虚拟锚点的随机半径，越大越容易出现更明显的中段偏折。
     */
    public float routeAnchorRadius = 3.8F;

    /**
     * 起步前若视角偏差过大，则先原地转头到该阈值以内再开始移动。
     */
    public float startTurnThreshold = 20.0F;

    /**
     * 偶发短暂停顿概率（每 tick）。
     */
    public float microPauseChance = 0.008F;

    /**
     * 偶发轻跳概率（每 tick，仅在地面长直线路段尝试）。
     */
    public float lightHopChance = 0.85F;

    /**
     * 两次轻跳之间的最小冷却 tick。
     */
    public int lightHopCooldownTicks = 20;

    /**
     * 停顿最短 tick。
     */
    public int microPauseMinTicks = 2;

    /**
     * 停顿最长 tick。
     */
    public int microPauseMaxTicks = 4;

    /**
     * 末段收敛距离，越接近目标越减少扰动。
     */
    public float finalApproachDistance = 2.8F;

    /**
     * 末段额外减速强度，越接近目标越明显。
     */
    public float finalApproachSlowdown = 0.01F;

    /**
     * 是否启用卡住后的随机恢复动作。
     */
    public boolean enableStuckRecovery = true;

    /**
     * 连续多少 tick 基本未移动时判定为“疑似卡住”。
     */
    public int stuckRecoveryTicks = 20;

    /**
     * 卡住恢复时横移扰动的强度。
     */
    public float stuckRecoveryStrafeStrength = 0.72F;

    /**
     * 卡住恢复动作的最短持续 tick。
     */
    public int stuckRecoveryMinTicks = 4;

    /**
     * 卡住恢复动作的最长持续 tick。
     */
    public int stuckRecoveryMaxTicks = 9;

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("human_like_movement.json");
    }

    public static void load() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(reader, HumanLikeMovementConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new HumanLikeMovementConfig();
                }
                INSTANCE.normalize();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("无法加载模拟真人移动配置", e);
                INSTANCE = new HumanLikeMovementConfig();
            }
        } else {
            INSTANCE = new HumanLikeMovementConfig();
            save();
        }
    }

    public static void save() {
        Path configFile = getConfigFile();
        try {
            Files.createDirectories(configFile.getParent());
            INSTANCE.normalize();
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存模拟真人移动配置", e);
        }
    }

    public void normalize() {
        minTurnSpeed = clamp(minTurnSpeed, 0.1F, 30.0F);
        maxTurnSpeed = clamp(maxTurnSpeed, minTurnSpeed, 60.0F);
        viewJitter = clamp(viewJitter, 0.0F, 5.0F);
        turnOvershoot = clamp(turnOvershoot, 0.0F, 2.0F);
        acceleration = clamp(acceleration, 0.01F, 1.0F);
        deceleration = clamp(deceleration, 0.01F, 1.0F);
        turnSlowdown = clamp(turnSlowdown, 0.0F, 1.0F);
        narrowSlowdown = clamp(narrowSlowdown, 0.0F, 1.0F);
        strafeJitterChance = clamp(strafeJitterChance, 0.0F, 1.0F);
        strafeJitterStrength = clamp(strafeJitterStrength, 0.0F, 1.0F);
        rhythmVariation = clamp(rhythmVariation, 0.0F, 0.6F);
        corridorBiasStrength = clamp(corridorBiasStrength, 0.0F, 0.45F);
        routeNoiseStrength = clamp(routeNoiseStrength, 0.0F, 0.35F);
        routeNoiseScale = clamp(routeNoiseScale, 2, 16);
        routeAnchorRadius = clamp(routeAnchorRadius, 0.0F, 16.0F);
        startTurnThreshold = clamp(startTurnThreshold, 0.0F, 90.0F);
        microPauseChance = clamp(microPauseChance, 0.0F, 0.5F);
        lightHopChance = clamp(lightHopChance, 0.0F, 0.15F);
        lightHopCooldownTicks = clamp(lightHopCooldownTicks, 5, 200);
        microPauseMinTicks = clamp(microPauseMinTicks, 0, 40);
        microPauseMaxTicks = clamp(microPauseMaxTicks, microPauseMinTicks, 60);
        finalApproachDistance = clamp(finalApproachDistance, 0.0F, 8.0F);
        finalApproachSlowdown = clamp(finalApproachSlowdown, 0.0F, 0.95F);
        stuckRecoveryTicks = clamp(stuckRecoveryTicks, 5, 120);
        stuckRecoveryStrafeStrength = clamp(stuckRecoveryStrafeStrength, 0.1F, 1.0F);
        stuckRecoveryMinTicks = clamp(stuckRecoveryMinTicks, 1, 40);
        stuckRecoveryMaxTicks = clamp(stuckRecoveryMaxTicks, stuckRecoveryMinTicks, 60);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}