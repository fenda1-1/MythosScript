package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.DroppedPickupTarget;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Executes the action that walks to and collects matching nearby drops. */
public final class NearbyItemPickupActionHandler {
    public static final String FILTER_MODE_INHERIT_KILLAURA = "INHERIT_KILLAURA";
    public static final String FILTER_MODE_CUSTOM = "CUSTOM";

    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int GOTO_INTERVAL_TICKS = 5;
    private static final int MAX_SCANNED_ITEM_ENTITIES = 512;

    private static boolean running;
    private static List<String> expressions = new ArrayList<>();
    private static double centerX;
    private static double centerY;
    private static double centerZ;
    private static double searchRadius;
    private static double reachDistanceSq;
    private static int maxItems;
    private static int timeoutTicks;
    private static int startTick;
    private static int pickedCount;
    private static int targetEntityId = Integer.MIN_VALUE;
    private static int lastGotoTick = -99999;
    private static boolean inheritKillAuraRules;

    private NearbyItemPickupActionHandler() {
    }

    public static synchronized void start(EntityPlayerSP player, JsonObject params) {
        cancel();
        if (player == null || player.world == null) {
            return;
        }

        inheritKillAuraRules = usesKillAuraPickupRules(params);
        List<String> configuredExpressions = inheritKillAuraRules
                ? new ArrayList<String>() : InventoryItemFilterExpressionEngine.readExpressions(params);
        if (!inheritKillAuraRules && configuredExpressions.isEmpty()) {
            zszlScriptMod.LOGGER.warn("[pickup_nearby_items] 缺少物品过滤表达式，动作取消。");
            return;
        }

        expressions = new ArrayList<>(configuredExpressions);
        centerX = player.posX;
        centerY = player.posY;
        centerZ = player.posZ;
        searchRadius = readPositiveDouble(params, "searchRadius", 16.0D);
        double reachDistance = readPositiveDouble(params, "reachDistance", 0.5D);
        reachDistanceSq = reachDistance * reachDistance;
        maxItems = readNonNegativeInt(params, "maxItems", 0);
        timeoutTicks = readNonNegativeInt(params, "timeoutSeconds", 30) * 20;
        startTick = player.ticksExisted;
        pickedCount = 0;
        targetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
        running = true;
        update(player);
    }

    public static synchronized boolean isRunning() {
        if (running) {
            update(MC.player);
        }
        return running;
    }

    public static synchronized void cancel() {
        if (running) {
            EmbeddedNavigationHandler.INSTANCE.stop();
        }
        running = false;
        expressions = new ArrayList<>();
        inheritKillAuraRules = false;
        targetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
    }

    private static void update(EntityPlayerSP player) {
        if (!running || player == null || player.world == null) {
            finish();
            return;
        }
        if (timeoutTicks > 0 && player.ticksExisted - startTick >= timeoutTicks) {
            zszlScriptMod.LOGGER.info("[pickup_nearby_items] 动作超时，已拾取 {} 个物品。", pickedCount);
            finish();
            return;
        }
        if (maxItems > 0 && pickedCount >= maxItems) {
            finish();
            return;
        }

        Entity target = resolveCurrentTarget(player);
        if (maxItems > 0 && pickedCount >= maxItems) {
            finish();
            return;
        }
        if (target == null) {
            target = findNearestMatchingTarget(player);
            if (target == null) {
                finish();
                return;
            }
            targetEntityId = target.getEntityId();
            lastGotoTick = -99999;
        }

        if (player.getDistanceSq(target) <= reachDistanceSq) {
            EmbeddedNavigationHandler.INSTANCE.stop();
            return;
        }

        int nowTick = player.ticksExisted;
        if (nowTick - lastGotoTick < GOTO_INTERVAL_TICKS) {
            return;
        }
        AutoPickupHandler.INSTANCE.startNavigationToPickupTarget(target);
        lastGotoTick = nowTick;
    }

    private static Entity resolveCurrentTarget(EntityPlayerSP player) {
        if (targetEntityId == Integer.MIN_VALUE || player.world == null) {
            return null;
        }
        Entity entity = player.world.getEntityByID(targetEntityId);
        if (!DroppedPickupTarget.isSupported(entity) || entity.isDead) {
            pickedCount++;
            targetEntityId = Integer.MIN_VALUE;
            lastGotoTick = -99999;
            return null;
        }
        if (isEligible(entity, player)) {
            return entity;
        }
        targetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
        return null;
    }

    private static Entity findNearestMatchingTarget(EntityPlayerSP player) {
        Entity nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        int scanned = 0;
        for (Entity entity : player.world.loadedEntityList) {
            if (!DroppedPickupTarget.isSupported(entity)) {
                continue;
            }
            if (!isEligible(entity, player)) {
                continue;
            }
            double distanceSq = player.getDistanceSq(entity);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                nearest = entity;
            }
            if (++scanned >= MAX_SCANNED_ITEM_ENTITIES) {
                break;
            }
        }
        return nearest;
    }

    private static boolean isEligible(Entity target, EntityPlayerSP player) {
        if (target == null || !DroppedPickupTarget.isEligibleForScan(target)) {
            return false;
        }
        double dx = target.posX - centerX;
        double dy = target.posY - centerY;
        double dz = target.posZ - centerZ;
        if (dx * dx + dy * dy + dz * dz > searchRadius * searchRadius) {
            return false;
        }

        double playerDistance = Math.sqrt(player.getDistanceSq(target));
        String rarity = DroppedPickupTarget.isExperienceOrb(target) ? "common"
                : getRarityToken(DroppedPickupTarget.getItemStack(target));
        if (inheritKillAuraRules) {
            return KillAuraHandler.INSTANCE.matchesHuntPickupRulesForAction(target, playerDistance);
        }
        for (String expression : expressions) {
            try {
                if (InventoryItemFilterExpressionEngine.matches(target, expression, rarity, playerDistance)) {
                    return true;
                }
            } catch (RuntimeException e) {
                zszlScriptMod.LOGGER.warn("[pickup_nearby_items] 物品过滤表达式解析失败: {}", expression, e);
            }
        }
        return false;
    }

    /** Missing mode keeps legacy actions compatible: stored expressions imply custom mode. */
    public static boolean usesKillAuraPickupRules(JsonObject params) {
        if (params != null && params.has("pickupFilterMode")) {
            return !FILTER_MODE_CUSTOM.equalsIgnoreCase(params.get("pickupFilterMode").getAsString());
        }
        return InventoryItemFilterExpressionEngine.readExpressions(params).isEmpty();
    }

    private static String getRarityToken(ItemStack stack) {
        EnumRarity rarity = stack.getRarity();
        if (rarity == EnumRarity.UNCOMMON) {
            return "uncommon";
        }
        if (rarity == EnumRarity.RARE) {
            return "rare";
        }
        if (rarity == EnumRarity.EPIC) {
            return "epic";
        }
        return "common";
    }

    private static double readPositiveDouble(JsonObject params, String key, double fallback) {
        try {
            double value = params != null && params.has(key) ? params.get(key).getAsDouble() : fallback;
            return value > 0.0D ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readNonNegativeInt(JsonObject params, String key, int fallback) {
        try {
            int value = params != null && params.has(key) ? params.get(key).getAsInt() : fallback;
            return Math.max(0, value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void finish() {
        if (running) {
            EmbeddedNavigationHandler.INSTANCE.stop();
        }
        running = false;
        inheritKillAuraRules = false;
        targetEntityId = Integer.MIN_VALUE;
        lastGotoTick = -99999;
    }
}
