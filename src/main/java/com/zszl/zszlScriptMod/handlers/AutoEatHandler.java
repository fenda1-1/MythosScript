package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.JsonConfigCharsetCompat;
import com.zszl.zszlScriptMod.utils.ModUtils;

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

public class AutoEatHandler {
    public static boolean autoEatEnabled = false;
    public static int foodLevelThreshold = 12;
    public static boolean autoMoveFoodEnabled = true;
    public static boolean eatWithLookDown = false;
    public static boolean smoothLookDown = true;
    public static boolean onlyDuringSequenceStepPathing = true;
    public static int targetHotbarSlot = 9;
    public static final List<String> DEFAULT_FOOD_KEYWORDS = Arrays.asList("牛排", "面包", "苹果", "曲奇饼");
    public static List<String> foodKeywords = new ArrayList<>(DEFAULT_FOOD_KEYWORDS);

    public static boolean isEating = false;
    public static int originalHotbarSlot = -1;
    public static ItemStack swappedItem = ItemStack.EMPTY;
    private static int eatTimeoutTicks = 0;
    private static int eatStartFoodLevel = 20;
    private static boolean eatStartedUsingHand = false;
    private static boolean pitchAdjusted = false;
    private static float originalPitch = 0.0F;
    private static int eatRetryAttempts = 0;
    private static int eatActiveHotbarSlot = -1;
    private static int eatStartStackCount = 0;
    private static final int EAT_ATTEMPT_TIMEOUT_TICKS = 100;
    private static final int MAX_EAT_RETRY_ATTEMPTS = 4;
    private static final float EAT_LOOKDOWN_TARGET_PITCH = 80.0F;
    private static final float EAT_LOOKDOWN_MIN_STEP = 3.0F;
    private static final float EAT_LOOKDOWN_MAX_STEP = 14.0F;

    static {
        loadAutoEatConfig();
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keycommand_autoeat.json").toFile();
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
                autoEatEnabled = json.has("enabled") && json.get("enabled").getAsBoolean();
                foodLevelThreshold = json.has("foodLevelThreshold") ? json.get("foodLevelThreshold").getAsInt() : 12;
                autoMoveFoodEnabled = !json.has("autoMoveFoodEnabled")
                        || json.get("autoMoveFoodEnabled").getAsBoolean();
                eatWithLookDown = json.has("eatWithLookDown") && json.get("eatWithLookDown").getAsBoolean();
                smoothLookDown = !json.has("smoothLookDown") || json.get("smoothLookDown").getAsBoolean();
                onlyDuringSequenceStepPathing = !json.has("onlyDuringSequenceStepPathing")
                        || json.get("onlyDuringSequenceStepPathing").getAsBoolean();
                targetHotbarSlot = json.has("targetHotbarSlot") ? json.get("targetHotbarSlot").getAsInt() : 9;

                foodKeywords = new ArrayList<>();
                if (json.has("foodKeywords") && json.get("foodKeywords").isJsonArray()) {
                    json.getAsJsonArray("foodKeywords").forEach(e -> {
                        if (e != null && e.isJsonPrimitive()) {
                            String keyword = e.getAsString().trim();
                            if (!keyword.isEmpty()) {
                                foodKeywords.add(keyword);
                            }
                        }
                    });
                }

                if (shouldResetToDefaultKeywords(foodKeywords)) {
                    foodKeywords = new ArrayList<>(DEFAULT_FOOD_KEYWORDS);
                    needsRewrite = true;
                }
                normalizeConfigValues();
                if (needsRewrite) {
                    saveAutoEatConfig();
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("加载自动进食配置失败", e);
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
            json.addProperty("enabled", autoEatEnabled);
            json.addProperty("foodLevelThreshold", foodLevelThreshold);
            json.addProperty("autoMoveFoodEnabled", autoMoveFoodEnabled);
            json.addProperty("eatWithLookDown", eatWithLookDown);
            json.addProperty("smoothLookDown", smoothLookDown);
            json.addProperty("onlyDuringSequenceStepPathing", onlyDuringSequenceStepPathing);
            json.addProperty("targetHotbarSlot", targetHotbarSlot);

            com.google.gson.JsonArray keywordArray = new com.google.gson.JsonArray();
            for (String keyword : foodKeywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    keywordArray.add(keyword.trim());
                }
            }
            json.add("foodKeywords", keywordArray);
            JsonConfigCharsetCompat.writeJsonObject(configFile.toPath(), json);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存自动进食配置失败", e);
        }
    }

    /**
     * 自动进食检查方法
     * 
     * @param player 玩家实体
     */
    public static void checkAutoEat(EntityPlayerSP player) {
        if (player == null || !autoEatEnabled)
            return;

        if (isEating) {
            continueEatingProcess(player);
            return;
        }

        if (ModConfig.isGuiOpen() || player.getFoodStats().getFoodLevel() > foodLevelThreshold)
            return;

        if (onlyDuringSequenceStepPathing && !PathSequenceEventListener.instance.isAutoEatStepPathingActive()) {
            return;
        }

        int hotbarFoodSlot = findBestFoodInHotbar(player);
        if (hotbarFoodSlot != -1) {
            AutoEatHandler.originalHotbarSlot = player.inventory.currentItem;
            handleEatingProcess(player, hotbarFoodSlot);
            return;
        }

        if (!autoMoveFoodEnabled) {
            return;
        }

        Container container = player.openContainer;
        if (container == null)
            return;

        int sourceContainerSlot = findBestFoodInMainInventory(container, player);
        int targetContainerSlot = getContainerSlotByPlayerInventoryIndex(container, player, targetHotbarSlot - 1);
        if (sourceContainerSlot != -1 && targetContainerSlot != -1 && sourceContainerSlot != targetContainerSlot) {
            moveOrSwapItemByClick(sourceContainerSlot, targetContainerSlot);
        }
    }

    private static void normalizeConfigValues() {
        foodLevelThreshold = Math.max(0, Math.min(20, foodLevelThreshold));
        targetHotbarSlot = Math.max(1, Math.min(9, targetHotbarSlot));

        if (foodKeywords == null) {
            foodKeywords = new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String keyword : foodKeywords) {
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
        foodKeywords = normalized;
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
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.openContainer == null || Minecraft.getMinecraft().playerController == null) {
            return;
        }
        if (slot < 0 || slot >= player.openContainer.inventorySlots.size()) {
            return;
        }
        Minecraft.getMinecraft().playerController.windowClick(player.openContainer.windowId, slot, 0, ClickType.PICKUP,
                player);
    }

    private static void swapItemWithHotbar2(EntityPlayerSP player, int sourcePlayerInvSlot) {
        if (player == null || sourcePlayerInvSlot < 0 || sourcePlayerInvSlot > 35
                || Minecraft.getMinecraft().playerController == null || player.openContainer == null) {
            return;
        }

        int sourceContainerSlot = getContainerSlotByPlayerInventoryIndex(player.openContainer, player,
                sourcePlayerInvSlot);
        if (sourceContainerSlot == -1) {
            return;
        }

        int currentHotbar = player.inventory.currentItem;
        Minecraft.getMinecraft().playerController.windowClick(
                player.openContainer.windowId,
                sourceContainerSlot,
                currentHotbar,
                ClickType.SWAP,
                player);
    }

    private static int findInventorySlotByItem(ItemStack target) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || target == null || target.isEmpty()) {
            return -1;
        }

        for (int i = 9; i < 36; i++) {
            if (ItemStack.areItemStacksEqual(player.inventory.getStackInSlot(i), target)) {
                return i;
            }
        }
        return -1;
    }

    // 进食处理方法
    private static void handleEatingProcess(EntityPlayerSP player, int slot) {
        if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EAT) && Minecraft.getMinecraft().player != null) {
            ItemStack foodStack = player.inventory.getStackInSlot(slot);
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    String.format("§d[调试] §7开始自动进食 [%s]。", foodStack.getDisplayName())));
        }
        AutoEatHandler.isEating = true;
        AutoEatHandler.eatTimeoutTicks = EAT_ATTEMPT_TIMEOUT_TICKS;
        AutoEatHandler.eatStartFoodLevel = player.getFoodStats().getFoodLevel();
        AutoEatHandler.eatStartedUsingHand = false;
        AutoEatHandler.eatRetryAttempts = 0;
        AutoEatHandler.eatActiveHotbarSlot = slot;
        ItemStack startingStack = player.inventory.getStackInSlot(slot);
        AutoEatHandler.eatStartStackCount = startingStack.isEmpty() ? 0 : startingStack.getCount();

        EmbeddedNavigationHandler.INSTANCE.pause();

        pitchAdjusted = false;
        if (eatWithLookDown) {
            originalPitch = player.rotationPitch;
            pitchAdjusted = true;
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

        if (eatActiveHotbarSlot >= 0 && player.inventory.currentItem != eatActiveHotbarSlot) {
            player.inventory.currentItem = eatActiveHotbarSlot;
            player.connection.sendPacket(new CPacketHeldItemChange(eatActiveHotbarSlot));
        }

        applyLookDownPitch(player);
        setUseKeyState(true);
        tryStartEatingUse(player);

        if (player.isHandActive()) {
            eatStartedUsingHand = true;
        }

        boolean hungerRecovered = player.getFoodStats().getFoodLevel() > eatStartFoodLevel;
        boolean stackConsumed = hasConsumedFoodStack(player);
        boolean finishedUseWithoutSuccess = eatStartedUsingHand && !player.isHandActive() && !hungerRecovered
                && !stackConsumed;
        eatTimeoutTicks--;
        boolean timeout = eatTimeoutTicks <= 0 && !hungerRecovered && !stackConsumed;

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

        if (player != null && pitchAdjusted) {
            player.rotationPitch = originalPitch;
        }

        setUseKeyState(false);

        if (player != null && originalHotbarSlot != -1) {
            player.inventory.currentItem = originalHotbarSlot;
            player.connection.sendPacket(new CPacketHeldItemChange(originalHotbarSlot));
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
        AutoEatHandler.isEating = false;
        AutoEatHandler.originalHotbarSlot = -1;
        AutoEatHandler.swappedItem = ItemStack.EMPTY;
        AutoEatHandler.eatTimeoutTicks = 0;
        AutoEatHandler.eatStartFoodLevel = 20;
        AutoEatHandler.eatStartedUsingHand = false;
        AutoEatHandler.pitchAdjusted = false;
        AutoEatHandler.eatRetryAttempts = 0;
        AutoEatHandler.eatActiveHotbarSlot = -1;
        AutoEatHandler.eatStartStackCount = 0;
    }

    private static boolean isPreferredFoodItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFood && isMatchingFoodKeyword(stack);
    }

    private static boolean isMatchingFoodKeyword(ItemStack stack) {
        if (foodKeywords == null || foodKeywords.isEmpty()) {
            return true;
        }
        String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
        for (String keyword : foodKeywords) {
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
        if (foodKeywords == null || foodKeywords.isEmpty()) {
            return 0;
        }
        String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
        for (int i = 0; i < foodKeywords.size(); i++) {
            String keyword = foodKeywords.get(i);
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
        if (player == null || Minecraft.getMinecraft().playerController == null) {
            return;
        }
        if (!player.isHandActive()) {
            Minecraft.getMinecraft().playerController.processRightClick(player, player.world, EnumHand.MAIN_HAND);
        }
        setUseKeyState(true);
    }

    private static boolean hasConsumedFoodStack(EntityPlayerSP player) {
        if (player == null || eatActiveHotbarSlot < 0 || eatActiveHotbarSlot > 8) {
            return false;
        }
        ItemStack current = player.inventory.getStackInSlot(eatActiveHotbarSlot);
        if (current == null || current.isEmpty()) {
            return eatStartedUsingHand;
        }
        return current.getCount() < eatStartStackCount;
    }

    private static void retryEatingUse(EntityPlayerSP player) {
        if (player == null) {
            finalizeEating(null, true);
            return;
        }
        if (eatRetryAttempts >= MAX_EAT_RETRY_ATTEMPTS) {
            finalizeEating(player, true);
            return;
        }
        eatRetryAttempts++;
        eatTimeoutTicks = EAT_ATTEMPT_TIMEOUT_TICKS;
        eatStartedUsingHand = false;
        ItemStack current = eatActiveHotbarSlot >= 0 && eatActiveHotbarSlot <= 8
                ? player.inventory.getStackInSlot(eatActiveHotbarSlot)
                : ItemStack.EMPTY;
        eatStartStackCount = current.isEmpty() ? 0 : current.getCount();
        tryStartEatingUse(player);
    }

    private static void applyLookDownPitch(EntityPlayerSP player) {
        if (player == null || !eatWithLookDown || !pitchAdjusted) {
            return;
        }
        if (!smoothLookDown) {
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

