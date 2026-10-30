package com.mythos.mythosScriptMod.handlers;

import com.google.gson.JsonObject;
import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.config.DebugModule;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.JsonConfigCharsetCompat;
import com.mythos.mythosScriptMod.utils.ModUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class AutoEatHandler {

    // 配置按会话（owner）各存一份：Nexus 的 bot 在各自线程上并发 tick，共用静态字段时
    // 后台 bot 读到的是当前挂载会话的配置。下面这些原来都是静态字段，现统一经 cfg() 访问。
    private static final java.util.concurrent.ConcurrentHashMap<String, OwnerConfig> CONFIGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class OwnerConfig {
        public int foodLevelThreshold = 12;
        public boolean autoMoveFoodEnabled = true;
        public boolean eatWithLookDown = false;
        public boolean smoothLookDown = true;
        public boolean onlyDuringSequenceStepPathing = true;
        public int targetHotbarSlot = 9;
        public List<String> foodKeywords = new ArrayList<>(DEFAULT_FOOD_KEYWORDS);
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
            com.mythos.mythosScriptMod.system.ProfileManager.withConfigOwner(owner, (Runnable) AutoEatHandler::loadAutoEatConfig);
            return config;
        }
    }

    public static final List<String> DEFAULT_FOOD_KEYWORDS = Arrays.asList("牛排", "面包", "苹果", "曲奇饼");

    private static final int EAT_ATTEMPT_TIMEOUT_TICKS = 100;
    private static final int MAX_EAT_RETRY_ATTEMPTS = 4;
    private static final float EAT_LOOKDOWN_TARGET_PITCH = 80.0F;
    private static final float EAT_LOOKDOWN_MIN_STEP = 3.0F;
    private static final float EAT_LOOKDOWN_MAX_STEP = 14.0F;

    private static final ConcurrentHashMap<String, RuntimeState> STATES = new ConcurrentHashMap<>();

    private static RuntimeState rt() {
        return STATES.computeIfAbsent(com.mythos.mythosScriptMod.mcp.NexusSessions.currentTickOwner(),
                k -> new RuntimeState());
    }

    private static final class RuntimeState {
        boolean autoEatEnabled = false;
        boolean isEating = false;
        int originalHotbarSlot = -1;
        ItemStack swappedItem = ItemStack.EMPTY;
        int eatTimeoutTicks = 0;
        int eatStartFoodLevel = 20;
        boolean eatStartedUsingHand = false;
        boolean pitchAdjusted = false;
        float originalPitch = 0.0F;
        int eatRetryAttempts = 0;
        int eatActiveHotbarSlot = -1;
        int eatStartStackCount = 0;
    }

    // ------------------------------------------------------------------
    // Runtime-switch accessors (boxed RuntimeState fields)
    // ------------------------------------------------------------------

    public static boolean autoEatEnabled() { return rt().autoEatEnabled; }

    public static void setAutoEatEnabledSilently(boolean value) { rt().autoEatEnabled = value; }

    public static boolean isEating() {
        return rt().isEating;
    }

    public static void resetRuntimeState() {
        RuntimeState rt = rt();
        rt.isEating = false;
        rt.originalHotbarSlot = -1;
        rt.swappedItem = ItemStack.EMPTY;
        rt.eatTimeoutTicks = 0;
        rt.eatStartFoodLevel = 20;
        rt.eatStartedUsingHand = false;
        rt.pitchAdjusted = false;
        rt.originalPitch = 0.0F;
        rt.eatRetryAttempts = 0;
        rt.eatActiveHotbarSlot = -1;
        rt.eatStartStackCount = 0;
    }

    static {
        loadAutoEatConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.currentConfigFile("keycommand_autoeat.json").toFile();
    }

    private static File getRuntimeConfigFile() {
        return ProfileManager.getCurrentRuntimeProfileDir().resolve("keycommand_autoeat.json").toFile();
    }

    public static void loadAutoEatConfig() {
        boolean needsRewrite = false;
        try {
            File configFile = getConfigFile();
            if (configFile.exists()) {
                JsonConfigCharsetCompat.ReadResult readResult = JsonConfigCharsetCompat
                        .readJsonObject(configFile.toPath());
                JsonObject json = readResult.getRoot();
                needsRewrite = readResult.usedLegacyCharset();
                File runtimeFile = getRuntimeConfigFile();
                if (runtimeFile.exists()) {
                    JsonConfigCharsetCompat.ReadResult rres = JsonConfigCharsetCompat
                            .readJsonObject(runtimeFile.toPath());
                    JsonObject rj = rres.getRoot();
                    rt().autoEatEnabled = rj.has("enabled") && rj.get("enabled").getAsBoolean();
                } else {
                    rt().autoEatEnabled = json.has("enabled") && json.get("enabled").getAsBoolean();
                }
                cfg().foodLevelThreshold = json.has("foodLevelThreshold") ? json.get("foodLevelThreshold").getAsInt() : 12;
                cfg().autoMoveFoodEnabled = !json.has("autoMoveFoodEnabled")
                        || json.get("autoMoveFoodEnabled").getAsBoolean();
                cfg().eatWithLookDown = json.has("eatWithLookDown") && json.get("eatWithLookDown").getAsBoolean();
                cfg().smoothLookDown = !json.has("smoothLookDown") || json.get("smoothLookDown").getAsBoolean();
                cfg().onlyDuringSequenceStepPathing = !json.has("onlyDuringSequenceStepPathing")
                        || json.get("onlyDuringSequenceStepPathing").getAsBoolean();
                cfg().targetHotbarSlot = json.has("targetHotbarSlot") ? json.get("targetHotbarSlot").getAsInt() : 9;

                cfg().foodKeywords = new ArrayList<>();
                if (json.has("foodKeywords") && json.get("foodKeywords").isJsonArray()) {
                    json.getAsJsonArray("foodKeywords").forEach(e -> {
                        if (e != null && e.isJsonPrimitive()) {
                            String keyword = e.getAsString().trim();
                            if (!keyword.isEmpty()) {
                                cfg().foodKeywords.add(keyword);
                            }
                        }
                    });
                }

                if (shouldResetToDefaultKeywords(cfg().foodKeywords)) {
                    cfg().foodKeywords = new ArrayList<>(DEFAULT_FOOD_KEYWORDS);
                    needsRewrite = true;
                }
                normalizeConfigValues();
                if (needsRewrite) {
                    saveAutoEatConfig();
                }
            }
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("加载自动进食配置失败", e);
            normalizeConfigValues();
        }
    }

    public static void saveAutoEatConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("foodLevelThreshold", cfg().foodLevelThreshold);
            json.addProperty("autoMoveFoodEnabled", cfg().autoMoveFoodEnabled);
            json.addProperty("eatWithLookDown", cfg().eatWithLookDown);
            json.addProperty("smoothLookDown", cfg().smoothLookDown);
            json.addProperty("onlyDuringSequenceStepPathing", cfg().onlyDuringSequenceStepPathing);
            json.addProperty("targetHotbarSlot", cfg().targetHotbarSlot);

            com.google.gson.JsonArray keywordArray = new com.google.gson.JsonArray();
            for (String keyword : cfg().foodKeywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    keywordArray.add(keyword.trim());
                }
            }
            json.add("foodKeywords", keywordArray);
            JsonConfigCharsetCompat.writeJsonObject(configFile.toPath(), json);

            File runtimeFile = getRuntimeConfigFile();
            if (!runtimeFile.getParentFile().exists()) {
                runtimeFile.getParentFile().mkdirs();
            }
            JsonObject rjson = new JsonObject();
            rjson.addProperty("enabled", rt().autoEatEnabled);
            JsonConfigCharsetCompat.writeJsonObject(runtimeFile.toPath(), rjson);
        } catch (Exception e) {
            mythosScriptMod.LOGGER.error("保存自动进食配置失败", e);
        }
    }

    /**
     * 自动进食检查方法
     * 
     * @param player 玩家实体
     */
    public static void checkAutoEat(EntityPlayerSP player) {
        if (player == null || !rt().autoEatEnabled)
            return;

        if (rt().isEating) {
            continueEatingProcess(player);
            return;
        }

        if (ModConfig.isGuiOpen() || player.getFoodStats().getFoodLevel() > cfg().foodLevelThreshold)
            return;

        if (cfg().onlyDuringSequenceStepPathing && !PathSequenceEventListener.foregroundRunner().isAutoEatStepPathingActive()) {
            return;
        }

        int hotbarFoodSlot = findBestFoodInHotbar(player);
        if (hotbarFoodSlot != -1) {
            rt().originalHotbarSlot = player.inventory.currentItem;
            handleEatingProcess(player, hotbarFoodSlot);
            return;
        }

        if (!cfg().autoMoveFoodEnabled) {
            return;
        }

        Container container = player.openContainer;
        if (container == null)
            return;

        int sourceContainerSlot = findBestFoodInMainInventory(container, player);
        int targetContainerSlot = getContainerSlotByPlayerInventoryIndex(container, player, cfg().targetHotbarSlot - 1);
        if (sourceContainerSlot != -1 && targetContainerSlot != -1 && sourceContainerSlot != targetContainerSlot) {
            moveOrSwapItemByClick(sourceContainerSlot, targetContainerSlot);
        }
    }

    private static void normalizeConfigValues() {
        cfg().foodLevelThreshold = Math.max(0, Math.min(20, cfg().foodLevelThreshold));
        cfg().targetHotbarSlot = Math.max(1, Math.min(9, cfg().targetHotbarSlot));

        if (cfg().foodKeywords == null) {
            cfg().foodKeywords = new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String keyword : cfg().foodKeywords) {
            if (keyword != null) {
                String trimmed = keyword.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(DEFAULT_FOOD_KEYWORDS);
        }
        cfg().foodKeywords = normalized;
    }

    private static boolean shouldResetToDefaultKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (!JsonConfigCharsetCompat.looksLikeMojibake(keyword)) {
                return false;
            }
        }
        return true;
    }

    private static int findBestFoodInHotbar(EntityPlayerSP player) {
        int bestSlot = -1;
        int bestPriority = Integer.MAX_VALUE;
        int bestHeal = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!isPreferredFoodItem(stack)) {
                continue;
            }

            int priority = getFoodPriority(stack);
            int heal = ((ItemFood) stack.getItem()).getHealAmount(stack);
            if (priority < bestPriority || (priority == bestPriority && heal > bestHeal)) {
                bestPriority = priority;
                bestHeal = heal;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private static int findBestFoodInMainInventory(Container container, EntityPlayerSP player) {
        int bestContainerSlot = -1;
        int bestPriority = Integer.MAX_VALUE;
        int bestHeal = -1;

        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            if (slot == null || slot.inventory != player.inventory || !slot.getHasStack()) {
                continue;
            }

            int playerInvIndex = slot.getSlotIndex();
            if (playerInvIndex < 9 || playerInvIndex > 35) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (!isPreferredFoodItem(stack)) {
                continue;
            }

            int priority = getFoodPriority(stack);
            int heal = ((ItemFood) stack.getItem()).getHealAmount(stack);
            if (priority < bestPriority || (priority == bestPriority && heal > bestHeal)) {
                bestPriority = priority;
                bestHeal = heal;
                bestContainerSlot = i;
            }
        }

        return bestContainerSlot;
    }

    private static int getContainerSlotByPlayerInventoryIndex(Container container, EntityPlayerSP player, int index) {
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.inventory == player.inventory && slot.getSlotIndex() == index) {
                return i;
            }
        }
        return -1;
    }

    private static void moveOrSwapItemByClick(int sourceContainerSlot, int destinationContainerSlot) {
        clickContainerSlot(sourceContainerSlot);
        ModUtils.DelayScheduler.instance.schedule(() -> clickContainerSlot(destinationContainerSlot), 2);
        ModUtils.DelayScheduler.instance.schedule(() -> clickContainerSlot(sourceContainerSlot), 4);
    }

    private static void clickContainerSlot(int slot) {
        EntityPlayerSP player = com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer();
        if (player == null || player.openContainer == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundController() == null) {
            return;
        }
        if (slot < 0 || slot >= player.openContainer.inventorySlots.size()) {
            return;
        }
        com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().windowClick(player.openContainer.windowId, slot, 0, ClickType.PICKUP,
                player);
    }



    // 进食处理方法
    private static void handleEatingProcess(EntityPlayerSP player, int slot) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EAT) && com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer() != null) {
            ItemStack foodStack = player.inventory.getStackInSlot(slot);
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundPlayer().sendMessage(new TextComponentString(
                    String.format("§d[调试] §7开始自动进食 [%s]。", foodStack.getDisplayName())));
        }
        rt().isEating = true;
        rt().eatTimeoutTicks = EAT_ATTEMPT_TIMEOUT_TICKS;
        rt().eatStartFoodLevel = player.getFoodStats().getFoodLevel();
        rt().eatStartedUsingHand = false;
        rt().eatRetryAttempts = 0;
        rt().eatActiveHotbarSlot = slot;
        ItemStack startingStack = player.inventory.getStackInSlot(slot);
        rt().eatStartStackCount = startingStack.isEmpty() ? 0 : startingStack.getCount();

        EmbeddedNavigationHandler.INSTANCE.pause();

        rt().pitchAdjusted = false;
        if (cfg().eatWithLookDown) {
            rt().originalPitch = player.rotationPitch;
            rt().pitchAdjusted = true;
            applyLookDownPitch(player);
        }

        player.inventory.currentItem = slot;
        player.connection.sendPacket(new CPacketHeldItemChange(slot));

        tryStartEatingUse(player);
    }

    private static void continueEatingProcess(EntityPlayerSP player) {
        if (player == null) {
            resetEatingState();
            EmbeddedNavigationHandler.INSTANCE.resume();
            return;
        }

        if (ModConfig.isGuiOpen()) {
            finalizeEating(player, true);
            return;
        }

        if (rt().eatActiveHotbarSlot >= 0 && player.inventory.currentItem != rt().eatActiveHotbarSlot) {
            player.inventory.currentItem = rt().eatActiveHotbarSlot;
            player.connection.sendPacket(new CPacketHeldItemChange(rt().eatActiveHotbarSlot));
        }

        applyLookDownPitch(player);
        setUseKeyState(true);
        tryStartEatingUse(player);

        if (player.isHandActive()) {
            rt().eatStartedUsingHand = true;
        }

        boolean hungerRecovered = player.getFoodStats().getFoodLevel() > rt().eatStartFoodLevel;
        boolean stackConsumed = hasConsumedFoodStack(player);
        boolean finishedUseWithoutSuccess = rt().eatStartedUsingHand && !player.isHandActive() && !hungerRecovered
                && !stackConsumed;
        rt().eatTimeoutTicks--;
        boolean timeout = rt().eatTimeoutTicks <= 0 && !hungerRecovered && !stackConsumed;

        if (hungerRecovered || stackConsumed) {
            finalizeEating(player, false);
            return;
        }

        if (finishedUseWithoutSuccess || timeout) {
            retryEatingUse(player);
        }
    }

    private static void finalizeEating(EntityPlayerSP player, boolean interrupted) {
        if (player != null && player.isHandActive()) {
            player.stopActiveHand();
        }

        if (player != null && rt().pitchAdjusted) {
            player.rotationPitch = rt().originalPitch;
        }

        setUseKeyState(false);

        if (player != null && rt().originalHotbarSlot != -1) {
            player.inventory.currentItem = rt().originalHotbarSlot;
            player.connection.sendPacket(new CPacketHeldItemChange(rt().originalHotbarSlot));
        }

        EmbeddedNavigationHandler.INSTANCE.resume();
        resetEatingState();

        if (interrupted && ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EAT) && player != null) {
            player.sendMessage(new TextComponentString("§d[调试] §7自动进食被中断。"));
        }
    }

    private static void setUseKeyState(boolean pressed) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindUseItem == null) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), pressed);
    }

    // 辅助重置方法
    private static void resetEatingState() {
        setUseKeyState(false);
        AutoEatHandler.rt().isEating = false;
        AutoEatHandler.rt().originalHotbarSlot = -1;
        AutoEatHandler.rt().swappedItem = ItemStack.EMPTY;
        AutoEatHandler.rt().eatTimeoutTicks = 0;
        AutoEatHandler.rt().eatStartFoodLevel = 20;
        AutoEatHandler.rt().eatStartedUsingHand = false;
        AutoEatHandler.rt().pitchAdjusted = false;
        AutoEatHandler.rt().eatRetryAttempts = 0;
        AutoEatHandler.rt().eatActiveHotbarSlot = -1;
        AutoEatHandler.rt().eatStartStackCount = 0;
    }

    private static boolean isPreferredFoodItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFood && isMatchingFoodKeyword(stack);
    }

    private static boolean isMatchingFoodKeyword(ItemStack stack) {
        if (cfg().foodKeywords == null || cfg().foodKeywords.isEmpty()) {
            return true;
        }
        String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
        for (String keyword : cfg().foodKeywords) {
            if (keyword == null)
                continue;
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && name.contains(trimmed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int getFoodPriority(ItemStack stack) {
        if (cfg().foodKeywords == null || cfg().foodKeywords.isEmpty()) {
            return 0;
        }
        String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
        for (int i = 0; i < cfg().foodKeywords.size(); i++) {
            String keyword = cfg().foodKeywords.get(i);
            if (keyword == null)
                continue;
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && name.contains(trimmed.toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static void tryStartEatingUse(EntityPlayerSP player) {
        if (player == null || com.mythos.mythosScriptMod.mcp.NexusSessions.boundController() == null) {
            return;
        }
        if (!player.isHandActive()) {
            com.mythos.mythosScriptMod.mcp.NexusSessions.boundController().processRightClick(player, player.world, EnumHand.MAIN_HAND);
        }
        setUseKeyState(true);
    }

    private static boolean hasConsumedFoodStack(EntityPlayerSP player) {
        if (player == null || rt().eatActiveHotbarSlot < 0 || rt().eatActiveHotbarSlot > 8) {
            return false;
        }
        ItemStack current = player.inventory.getStackInSlot(rt().eatActiveHotbarSlot);
        if (current == null || current.isEmpty()) {
            return rt().eatStartedUsingHand;
        }
        return current.getCount() < rt().eatStartStackCount;
    }

    private static void retryEatingUse(EntityPlayerSP player) {
        if (player == null) {
            finalizeEating(null, true);
            return;
        }
        if (rt().eatRetryAttempts >= MAX_EAT_RETRY_ATTEMPTS) {
            finalizeEating(player, true);
            return;
        }
        rt().eatRetryAttempts++;
        rt().eatTimeoutTicks = EAT_ATTEMPT_TIMEOUT_TICKS;
        rt().eatStartedUsingHand = false;
        ItemStack current = rt().eatActiveHotbarSlot >= 0 && rt().eatActiveHotbarSlot <= 8
                ? player.inventory.getStackInSlot(rt().eatActiveHotbarSlot)
                : ItemStack.EMPTY;
        rt().eatStartStackCount = current.isEmpty() ? 0 : current.getCount();
        tryStartEatingUse(player);
    }

    private static void applyLookDownPitch(EntityPlayerSP player) {
        if (player == null || !cfg().eatWithLookDown || !rt().pitchAdjusted) {
            return;
        }
        if (!cfg().smoothLookDown) {
            player.rotationPitch = EAT_LOOKDOWN_TARGET_PITCH;
            return;
        }
        float delta = EAT_LOOKDOWN_TARGET_PITCH - player.rotationPitch;
        float absDelta = Math.abs(delta);
        if (absDelta <= 0.001F) {
            player.rotationPitch = EAT_LOOKDOWN_TARGET_PITCH;
            return;
        }
        float stepLimit = Math.min(EAT_LOOKDOWN_MAX_STEP,
                Math.max(EAT_LOOKDOWN_MIN_STEP, EAT_LOOKDOWN_MIN_STEP + absDelta * 0.35F));
        float step = clampSigned(delta, stepLimit);
        float gcdStep = getMouseGcdStep();
        step = quantizeRotationStepForGcd(step, stepLimit, gcdStep);
        if (Math.abs(step) <= 0.001F) {
            step = Math.copySign(Math.min(stepLimit, absDelta), delta);
        }
        player.rotationPitch = clampPitch(player.rotationPitch + step);
    }

    private static float clampSigned(float value, float limit) {
        float safeLimit = Math.max(0.0F, Math.abs(limit));
        return Math.copySign(Math.min(Math.abs(value), safeLimit), value);
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private static float getMouseGcdStep() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return 0.0F;
        }
        float sensitivity = Math.max(0.0F, Math.min(1.0F, mc.gameSettings.mouseSensitivity));
        float f = sensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F * 0.15F;
    }

    private static float quantizeRotationStepForGcd(float step, float maxMagnitude, float gcdStep) {
        if (step == 0.0F || gcdStep <= 1.0E-5F || maxMagnitude <= 0.0F) {
            return step == 0.0F ? 0.0F : clampSigned(step, maxMagnitude);
        }
        float maxStep = Math.abs(maxMagnitude);
        float absStep = Math.min(Math.abs(step), maxStep);
        if (absStep <= 1.0E-5F) {
            return 0.0F;
        }
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
}

