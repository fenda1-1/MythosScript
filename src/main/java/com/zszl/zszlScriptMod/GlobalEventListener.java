// 文件路径: src/main/java/com/zszl/zszlScriptMod/GlobalEventListener.java
package com.zszl.zszlScriptMod;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.EntityCreature;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Mouse;

import com.zszl.zszlScriptMod.config.ChatOptimizationConfig;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.OverlayGuiHandler;
import com.zszl.zszlScriptMod.handlers.ArenaItemHandler;
import com.zszl.zszlScriptMod.handlers.AutoEatHandler;
import com.zszl.zszlScriptMod.handlers.AutoFollowHandler;
import com.zszl.zszlScriptMod.handlers.AutoUseItemHandler;
import com.zszl.zszlScriptMod.handlers.ConditionalExecutionHandler;
import com.zszl.zszlScriptMod.handlers.GuiBlockerHandler;
import com.zszl.zszlScriptMod.handlers.ArenaItemHandler.DropMode;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.node.NodeTriggerManager;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.path.trigger.PlayerListTriggerSupport;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.listenersupport.PlayerIdleTriggerTracker;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiInspectionManager;
import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class GlobalEventListener {
    public static final GlobalEventListener instance = new GlobalEventListener();
    private int tickCounter = 0;
    private int clientTickCounter = 0;
    private static GuiScreen lastGuiScreen = null;
    private boolean wasPlayerDeadLastTick = false;
    private boolean wasInventoryFullLastCheck = false;
    private String lastInventorySignature = "";
    private String lastAreaKey = "";
    private String lastWorldKey = "";
    private String lastScoreboardSignature = "";
    private NearbyEntitySummary lastNearbyEntitySummary = NearbyEntitySummary.EMPTY;
    private PlayerListTriggerSupport.PlayerSnapshot lastPlayerListSnapshot = new PlayerListTriggerSupport.PlayerSnapshot(
            Collections.<PlayerListTriggerSupport.PlayerRecord>emptyList(), "");
    private String lastLegacyGuiClassName = "";
    private String lastLegacyGuiTitle = "";
    private static final double ENTITY_NEARBY_TRIGGER_RADIUS = 8.0D;

    public static int timedMessageTickCounter = 0;
    private static int timedMessageIndex = 0;
    private static final Random random = new Random();

    private GlobalEventListener() {
    }

    private final AutoFollowHandler autoFollowHandler = new AutoFollowHandler();
    private final PlayerIdleTriggerTracker playerIdleTriggerTracker = new PlayerIdleTriggerTracker();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 自动追怪性能监控
        if (PerformanceMonitor.isFeatureEnabled("auto_follow")) {
            PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("auto_follow");
            try {
                autoFollowHandler.onPlayerTick(event);
            } finally {
                timer.stop();
            }
        }

        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;
            if (ArenaItemHandler.arenaProcessingEnabled && ArenaItemHandler.dropMode == DropMode.TIMED) {
                if (tickCounter % (ArenaItemHandler.timedDropIntervalSeconds * 20) == 0) {
                    // 竞技场物品处理性能监控
                    PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
                    try {
                        ArenaItemHandler.processItems();
                    } finally {
                        timer.stop();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() != null && GuiBlockerHandler.shouldBlockAndConsume(event.getGui())) {
            event.setCanceled(true);
            return;
        }

        if (event.getGui() != null) {
            JsonObject triggerData = new JsonObject();
            triggerData.addProperty("gui", event.getGui().getClass().getName());
            triggerData.addProperty("title", GuiElementInspector.getCurrentGuiTitle(Minecraft.getMinecraft()));
            NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_GUI_OPEN, triggerData);
            LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_GUI_OPEN, triggerData);
        }

        if (ModConfig.enableGuiListener && event.getGui() != null) {
            String guiClassName = event.getGui().getClass().getName();
            String message = "§e[GUI 侦测] §f打开的界面类名: §b" + guiClassName;

            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString(message));
            }
            zszlScriptMod.LOGGER.info(message);
        }

        if (event.getGui() instanceof GuiChat) {
            try {
                GuiChat guiChat = (GuiChat) event.getGui();
                // 兼容不同 Forge 版本的 ObfuscationReflectionHelper 签名
                GuiTextField inputField = ReflectionCompat.getPrivateValue(GuiChat.class, guiChat,
                        "field_146415_a", "inputField");
                if (inputField != null) {
                    inputField.setMaxStringLength(Integer.MAX_VALUE);
                    zszlScriptMod.LOGGER.info("成功解除主聊天输入框的长度限制！");
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("通过反射修改聊天输入框长度失败！", e);
            }
        }

        if (event.getGui() instanceof GuiChest &&
                ArenaItemHandler.arenaProcessingEnabled &&
                ArenaItemHandler.dropMode == DropMode.ON_CHEST_OPEN) {

            ModUtils.DelayScheduler.instance.schedule(() -> {
                PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("warehouse");
                try {
                    ArenaItemHandler.processItems();
                } finally {
                    timer.stop();
                }
            }, 10);
        }

        if (event.getGui() instanceof GuiChest) {
            WarehouseEventHandler.INSTANCE.onGuiOpen(event);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null) {
            playerIdleTriggerTracker.reset();
            lastPlayerListSnapshot = new PlayerListTriggerSupport.PlayerSnapshot(
                    Collections.<PlayerListTriggerSupport.PlayerRecord>emptyList(), "");
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            PerformanceMonitor.PerformanceTimer triggerTimer = PerformanceMonitor.startTimer("trigger_system");
            try {
                clientTickCounter++;
                NodeTriggerManager.tick();
                GuiInspectionManager.onClientTick();
                boolean needsAreaChangedChecks = NodeTriggerManager.hasGraphsForTrigger(NodeTriggerManager.TRIGGER_AREA_CHANGED)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_AREA_CHANGED);
                boolean needsWorldChangedChecks = LegacySequenceTriggerManager
                        .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_WORLD_CHANGED);
                boolean needsInventoryChangedChecks = NodeTriggerManager
                        .hasGraphsForTrigger(NodeTriggerManager.TRIGGER_INVENTORY_CHANGED)
                        || NodeTriggerManager.hasGraphsForTrigger(NodeTriggerManager.TRIGGER_INVENTORY_FULL)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_INVENTORY_CHANGED)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_INVENTORY_FULL);
                boolean needsNearbyEntityChecks = NodeTriggerManager
                        .hasGraphsForTrigger(NodeTriggerManager.TRIGGER_ENTITY_NEARBY)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY);
                boolean needsScoreboardChecks = LegacySequenceTriggerManager
                        .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_SCOREBOARD_CHANGED);
                boolean needsPlayerListChecks = LegacySequenceTriggerManager
                        .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_PLAYER_LIST);
                boolean needsTimerTriggers = NodeTriggerManager.hasGraphsForTrigger(NodeTriggerManager.TRIGGER_TIMER)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_TIMER);
                boolean needsHpLowTriggers = NodeTriggerManager.hasGraphsForTrigger(NodeTriggerManager.TRIGGER_HP_LOW)
                        || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_HP_LOW);
                boolean needsFoodLowTriggers = LegacySequenceTriggerManager
                        .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_FOOD_LOW);
                boolean needsIdleTracking = LegacySequenceTriggerManager
                        .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE);

            boolean playerDeadNow = mc.player.isDead || mc.player.getHealth() <= 0.0F;
            if (playerDeadNow && !wasPlayerDeadLastTick) {
                JsonObject deathTrigger = new JsonObject();
                deathTrigger.addProperty("hp", mc.player.getHealth());
                deathTrigger.addProperty("maxHp", mc.player.getMaxHealth());
                deathTrigger.addProperty("x", mc.player.posX);
                deathTrigger.addProperty("y", mc.player.posY);
                deathTrigger.addProperty("z", mc.player.posZ);
                triggerUnifiedEvent(NodeTriggerManager.TRIGGER_DEATH,
                        LegacySequenceTriggerManager.TRIGGER_DEATH, deathTrigger);
            } else if (!playerDeadNow && wasPlayerDeadLastTick) {
                JsonObject respawnTrigger = new JsonObject();
                respawnTrigger.addProperty("hp", mc.player.getHealth());
                respawnTrigger.addProperty("maxHp", mc.player.getMaxHealth());
                respawnTrigger.addProperty("x", mc.player.posX);
                respawnTrigger.addProperty("y", mc.player.posY);
                respawnTrigger.addProperty("z", mc.player.posZ);
                triggerUnifiedEvent(NodeTriggerManager.TRIGGER_RESPAWN,
                        LegacySequenceTriggerManager.TRIGGER_RESPAWN, respawnTrigger);
            }

            if (needsIdleTracking) {
                playerIdleTriggerTracker.update(mc, playerDeadNow, clientTickCounter);
            } else {
                playerIdleTriggerTracker.reset();
            }

            String currentAreaKey = needsAreaChangedChecks ? buildAreaKey(mc) : "";
            String currentWorldKey = needsWorldChangedChecks ? buildWorldKey(mc) : "";
            if (needsAreaChangedChecks && !lastAreaKey.isEmpty() && !currentAreaKey.equals(lastAreaKey)) {
                JsonObject areaTrigger = new JsonObject();
                areaTrigger.addProperty("from", lastAreaKey);
                areaTrigger.addProperty("to", currentAreaKey);
                areaTrigger.addProperty("x", mc.player.posX);
                areaTrigger.addProperty("y", mc.player.posY);
                areaTrigger.addProperty("z", mc.player.posZ);
                areaTrigger.addProperty("chunkX", mc.player.chunkCoordX);
                areaTrigger.addProperty("chunkZ", mc.player.chunkCoordZ);
                triggerUnifiedEvent(NodeTriggerManager.TRIGGER_AREA_CHANGED,
                        LegacySequenceTriggerManager.TRIGGER_AREA_CHANGED, areaTrigger);
            }

            if (needsWorldChangedChecks && !lastWorldKey.isEmpty() && !currentWorldKey.equals(lastWorldKey)) {
                JsonObject worldTrigger = new JsonObject();
                worldTrigger.addProperty("from", lastWorldKey);
                worldTrigger.addProperty("to", currentWorldKey);
                worldTrigger.addProperty("dimension", mc.player.dimension);
                LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_WORLD_CHANGED, worldTrigger);
            }

            if (needsInventoryChangedChecks && clientTickCounter % 4 == 0) {
                String inventorySignature = buildInventorySignature(mc);
                if (!lastInventorySignature.isEmpty() && !inventorySignature.equals(lastInventorySignature)) {
                    JsonObject inventoryTrigger = new JsonObject();
                    inventoryTrigger.addProperty("before", lastInventorySignature);
                    inventoryTrigger.addProperty("after", inventorySignature);
                    inventoryTrigger.addProperty("filledSlots", countFilledSlots(mc));
                    triggerUnifiedEvent(NodeTriggerManager.TRIGGER_INVENTORY_CHANGED,
                            LegacySequenceTriggerManager.TRIGGER_INVENTORY_CHANGED, inventoryTrigger);
                }
                boolean inventoryFullNow = isMainInventoryFull(mc);
                if (inventoryFullNow && !wasInventoryFullLastCheck) {
                    JsonObject inventoryFullTrigger = new JsonObject();
                    int totalSlots = getMainInventorySlotCount(mc);
                    int filledSlots = countMainInventoryFilledSlots(mc);
                    inventoryFullTrigger.addProperty("filledSlots", filledSlots);
                    inventoryFullTrigger.addProperty("totalSlots", totalSlots);
                    inventoryFullTrigger.addProperty("emptySlots", Math.max(0, totalSlots - filledSlots));
                    inventoryFullTrigger.addProperty("signature", inventorySignature);
                    triggerUnifiedEvent(NodeTriggerManager.TRIGGER_INVENTORY_FULL,
                            LegacySequenceTriggerManager.TRIGGER_INVENTORY_FULL, inventoryFullTrigger);
                }
                wasInventoryFullLastCheck = inventoryFullNow;
                lastInventorySignature = inventorySignature;
            }

            if (needsNearbyEntityChecks && clientTickCounter % 5 == 0) {
                NearbyEntitySummary nearbyEntitySummary = scanNearbyEntities(mc);
                if (!lastNearbyEntitySummary.isEmpty() && nearbyEntitySummary.hasChangedSince(lastNearbyEntitySummary)) {
                    JsonObject entityTrigger = new JsonObject();
                    lastNearbyEntitySummary.writeToTriggerData(entityTrigger, "before");
                    nearbyEntitySummary.writeToTriggerData(entityTrigger, "after");
                    entityTrigger.addProperty("count", nearbyEntitySummary.allCount);
                    triggerUnifiedEvent(NodeTriggerManager.TRIGGER_ENTITY_NEARBY,
                            LegacySequenceTriggerManager.TRIGGER_ENTITY_NEARBY, entityTrigger);
                }
                lastNearbyEntitySummary = nearbyEntitySummary;
            }

            if (needsScoreboardChecks && clientTickCounter % 10 == 0) {
                String scoreboardSignature = buildScoreboardSignature(mc);
                if (!lastScoreboardSignature.isEmpty() && !scoreboardSignature.equals(lastScoreboardSignature)) {
                    JsonObject scoreboardTrigger = new JsonObject();
                    scoreboardTrigger.addProperty("before", lastScoreboardSignature);
                    scoreboardTrigger.addProperty("after", scoreboardSignature);
                    scoreboardTrigger.addProperty("text", scoreboardSignature);
                    LegacySequenceTriggerManager.triggerEvent(
                            LegacySequenceTriggerManager.TRIGGER_SCOREBOARD_CHANGED, scoreboardTrigger);
                }
                lastScoreboardSignature = scoreboardSignature;
            }

            if (needsPlayerListChecks) {
                PlayerListTriggerSupport.PlayerSnapshot playerListSnapshot = PlayerListTriggerSupport.captureSnapshot(mc);
                if (!playerListSnapshot.players.isEmpty()) {
                    LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_PLAYER_LIST,
                            PlayerListTriggerSupport.buildTriggerEvent(lastPlayerListSnapshot, playerListSnapshot));
                }
                lastPlayerListSnapshot = playerListSnapshot;
            } else {
                lastPlayerListSnapshot = new PlayerListTriggerSupport.PlayerSnapshot(
                        Collections.<PlayerListTriggerSupport.PlayerRecord>emptyList(), "");
            }

            if (needsTimerTriggers && clientTickCounter % 20 == 0) {
                JsonObject timerTrigger = new JsonObject();
                timerTrigger.addProperty("tick", clientTickCounter);
                NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_TIMER, timerTrigger);
                LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_TIMER, timerTrigger);
            }

            if (needsHpLowTriggers && mc.player.getHealth() > 0.0F) {
                JsonObject hpTrigger = new JsonObject();
                hpTrigger.addProperty("hp", mc.player.getHealth());
                hpTrigger.addProperty("maxHp", mc.player.getMaxHealth());
                NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_HP_LOW, hpTrigger);
                LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_HP_LOW, hpTrigger);
            }

            if (needsFoodLowTriggers) {
                JsonObject foodTrigger = new JsonObject();
                foodTrigger.addProperty("food", mc.player.getFoodStats().getFoodLevel());
                foodTrigger.addProperty("maxFood", 20);
                LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_FOOD_LOW, foodTrigger);
            }

            wasPlayerDeadLastTick = playerDeadNow;
            if (needsAreaChangedChecks) {
                lastAreaKey = currentAreaKey;
            }
            if (needsWorldChangedChecks) {
                lastWorldKey = currentWorldKey;
            }

            } finally {
                triggerTimer.stop();
            }
        }

        if (event.phase == TickEvent.Phase.END) {
            GuiScreen currentScreen = mc.currentScreen;
            boolean retainedDetachedScreen = DetachedSwingWindowManager.isDetachedScreen(lastGuiScreen);
            if (!retainedDetachedScreen && lastGuiScreen != null && currentScreen != lastGuiScreen) {
                JsonObject triggerData = new JsonObject();
                triggerData.addProperty("gui", safe(lastLegacyGuiClassName));
                triggerData.addProperty("title", safe(lastLegacyGuiTitle));
                LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_GUI_CLOSE, triggerData);
                lastLegacyGuiClassName = "";
                lastLegacyGuiTitle = "";
            }
            if (!retainedDetachedScreen && lastGuiScreen != null && currentScreen == null
                    && !DetachedSwingWindowManager.isDetached()) {
                OverlayGuiHandler.resetLastCheckedChest();

                if (PathSequenceEventListener.instance.wasPausedByGui()) {
                    PathSequenceEventListener.instance.resume();
                }
            }
            if (DetachedSwingWindowManager.isDetached() && currentScreen == null) {
                GuiScreen detachedScreen = DetachedSwingWindowManager.getActiveScreen();
                if (detachedScreen != null) {
                    // Track the retained screen only while no Minecraft screen
                    // is open, so both windows keep independent lifecycles.
                    lastGuiScreen = detachedScreen;
                    lastLegacyGuiClassName = detachedScreen.getClass().getName();
                    lastLegacyGuiTitle = GuiElementInspector.getCurrentGuiTitle(mc);
                }
            } else {
                lastGuiScreen = currentScreen;
                if (currentScreen != null) {
                    lastLegacyGuiClassName = currentScreen.getClass().getName();
                    lastLegacyGuiTitle = GuiElementInspector.getCurrentGuiTitle(mc);
                } else {
                    lastLegacyGuiClassName = "";
                    lastLegacyGuiTitle = "";
                }
            }

            if (mc.playerController == null) {
                return;
            }

            runGuarded("处理鼠标脱离逻辑时出错", () -> {
                if (ModConfig.isMouseDetached && Mouse.isGrabbed()) {
                    Mouse.setGrabbed(false);
                }
            });

            // 每 4 tick：进食检查可降频（约 0.2 秒）
            if (clientTickCounter % 4 == 0) {
                runGuarded("auto_eat", "执行自动进食检查时出错", () -> {
                    AutoEatHandler.checkAutoEat(mc.player);
                });
            }

            // 每 2 tick：静默使用物品降频（约 0.1 秒），减少主线程持续调用压力
            if (clientTickCounter % 2 == 0) {
                runGuarded("auto_use_item", "执行静默使用物品时出错", () -> {
                    AutoUseItemHandler.INSTANCE.tick();
                });
            }

            runGuarded("timed_message", "执行定时发送消息时出错", () -> {
                ChatOptimizationConfig config = ChatOptimizationConfig.INSTANCE;
                if (config.enableTimedMessage && config.timedMessages != null && !config.timedMessages.isEmpty()) {
                    timedMessageTickCounter++;
                    if (timedMessageTickCounter >= config.timedMessageIntervalSeconds * 20) {
                        String messageToSend = null;
                        List<String> validMessages = new ArrayList<>();
                        for (String msg : config.timedMessages) {
                            if (msg != null && !msg.trim().isEmpty()) {
                                validMessages.add(msg);
                            }
                        }

                        if (!validMessages.isEmpty()) {
                            if (config.timedMessageMode == ChatOptimizationConfig.TimedMessageMode.SEQUENTIAL) {
                                if (timedMessageIndex >= validMessages.size()) {
                                    timedMessageIndex = 0;
                                }
                                messageToSend = validMessages.get(timedMessageIndex);
                                timedMessageIndex++;
                            } else { // RANDOM
                                messageToSend = validMessages.get(random.nextInt(validMessages.size()));
                            }
                        }

                        if (messageToSend != null) {
                            mc.player.sendChatMessage(messageToSend);
                        }

                        timedMessageTickCounter = 0;
                    }
                } else {
                    timedMessageTickCounter = 0;
                }
            });
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        autoFollowHandler.onRenderWorldLast(event);
    }

    @SubscribeEvent
    public void onPlayerHurt(LivingHurtEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || event == null || event.getEntityLiving() != mc.player) {
            return;
        }
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("damage", event.getAmount());
        triggerData.addProperty("damageSource", event.getSource() == null ? "" : event.getSource().damageType);
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_PLAYER_HURT, triggerData);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || event == null || event.getEntityPlayer() != mc.player) {
            return;
        }
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("entityName", event.getTarget() == null ? "" : event.getTarget().getName());
        triggerData.addProperty("entityClass", event.getTarget() == null ? "" : event.getTarget().getClass().getName());
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_ATTACK_ENTITY, triggerData);
    }

    @SubscribeEvent
    public void onTargetKilled(LivingDeathEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || event == null || event.getSource() == null
                || event.getSource().getTrueSource() != mc.player) {
            return;
        }
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("entityName", event.getEntityLiving() == null ? "" : event.getEntityLiving().getName());
        triggerData.addProperty("entityClass",
                event.getEntityLiving() == null ? "" : event.getEntityLiving().getClass().getName());
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_TARGET_KILL, triggerData);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        if (!ConditionalExecutionHandler.shouldRenderDebugOverlay()) {
            return;
        }

        renderConditionalExecutionDebugOverlay(mc);
    }

    private void renderConditionalExecutionDebugOverlay(Minecraft mc) {
        List<String> lines = ConditionalExecutionHandler.getDebugLinesSnapshot();
        if (lines.isEmpty()) {
            return;
        }

        int lineHeight = 12;
        int pad = 6;
        int titleHeight = 14;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }

        String title = "条件执行调试";
        maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(title));

        int panelWidth = maxWidth + pad * 2;
        int panelHeight = pad + titleHeight + lines.size() * lineHeight + pad;
        int x = 8;
        int y = 8;

        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xA0101010);
        Gui.drawRect(x, y, x + panelWidth, y + 1, 0xFF4AA3FF);
        Gui.drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF4AA3FF);

        mc.fontRenderer.drawStringWithShadow(title, x + pad, y + 4, 0xFFFFFF);

        int textY = y + pad + titleHeight;
        for (String line : lines) {
            mc.fontRenderer.drawStringWithShadow(line, x + pad, textY, 0xE0E0E0);
            textY += lineHeight;
        }
    }

    private void triggerUnifiedEvent(String nodeTriggerType, String legacyTriggerType, JsonObject eventData) {
        if (eventData == null) {
            return;
        }
        if (nodeTriggerType != null
                && !nodeTriggerType.trim().isEmpty()
                && NodeTriggerManager.hasGraphsForTrigger(nodeTriggerType)) {
            NodeTriggerManager.trigger(nodeTriggerType, eventData);
        }
        if (legacyTriggerType != null
                && !legacyTriggerType.trim().isEmpty()
                && LegacySequenceTriggerManager.hasRulesForTrigger(legacyTriggerType)) {
            LegacySequenceTriggerManager.triggerEvent(legacyTriggerType, eventData);
        }
    }

    private String buildAreaKey(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return "";
        }
        return mc.player.dimension + ":" + mc.player.chunkCoordX + "," + mc.player.chunkCoordZ;
    }

    private String buildWorldKey(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return "";
        }
        return "dim:" + mc.player.dimension;
    }

    private String buildInventorySignature(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.inventory == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendInventorySection(builder, "main", mc.player.inventory.mainInventory);
        appendInventorySection(builder, "armor", mc.player.inventory.armorInventory);
        appendInventorySection(builder, "offhand", mc.player.inventory.offHandInventory);
        return builder.toString();
    }

    private void appendInventorySection(StringBuilder builder, String prefix, List<ItemStack> stacks) {
        if (builder == null || stacks == null) {
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(prefix).append('[').append(i).append("]=")
                    .append(String.valueOf(stack.getItem().getRegistryName()))
                    .append('x').append(stack.getCount());
        }
    }

    private String buildScoreboardSignature(Minecraft mc) {
        if (mc == null || mc.world == null) {
            return "";
        }
        try {
            net.minecraft.scoreboard.Scoreboard scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) {
                return "";
            }
            net.minecraft.scoreboard.ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
            if (objective == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            builder.append(objective.getDisplayName());
            java.util.Collection<net.minecraft.scoreboard.Score> scores = scoreboard.getSortedScores(objective);
            int count = 0;
            for (net.minecraft.scoreboard.Score score : scores) {
                if (score == null || score.getPlayerName() == null || score.getPlayerName().startsWith("#")) {
                    continue;
                }
                net.minecraft.scoreboard.ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
                String line = net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(line);
                count++;
                if (count >= 15) {
                    break;
                }
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int countFilledSlots(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.inventory == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        for (ItemStack stack : mc.player.inventory.armorInventory) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        for (ItemStack stack : mc.player.inventory.offHandInventory) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countMainInventoryFilledSlots(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.inventory == null || mc.player.inventory.mainInventory == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int getMainInventorySlotCount(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.inventory == null || mc.player.inventory.mainInventory == null) {
            return 0;
        }
        return mc.player.inventory.mainInventory.size();
    }

    private boolean isMainInventoryFull(Minecraft mc) {
        int totalSlots = getMainInventorySlotCount(mc);
        return totalSlots > 0 && countMainInventoryFilledSlots(mc) >= totalSlots;
    }

    private NearbyEntitySummary scanNearbyEntities(Minecraft mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return NearbyEntitySummary.EMPTY;
        }
        double radiusSq = ENTITY_NEARBY_TRIGGER_RADIUS * ENTITY_NEARBY_TRIGGER_RADIUS;
        Set<String> allNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> playerNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> hostileNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> passiveNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int allCount = 0;
        int playerCount = 0;
        int hostileCount = 0;
        int passiveCount = 0;
        for (Object entityObj : mc.world.loadedEntityList) {
            if (!(entityObj instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entityObj;
            if (living == mc.player || !living.isEntityAlive()) {
                continue;
            }
            if (mc.player.getDistanceSq(living) > radiusSq) {
                continue;
            }
            allCount++;
            String name = normalizeEntityName(living.getName());
            if (!name.isEmpty()) {
                allNames.add(name);
            }
            if (living instanceof EntityPlayer) {
                playerCount++;
                if (!name.isEmpty()) {
                    playerNames.add(name);
                }
                continue;
            }
            if (isHostileNearbyEntity(living)) {
                hostileCount++;
                if (!name.isEmpty()) {
                    hostileNames.add(name);
                }
                continue;
            }
            if (isPassiveNearbyEntity(living)) {
                passiveCount++;
                if (!name.isEmpty()) {
                    passiveNames.add(name);
                }
            }
        }
        return new NearbyEntitySummary(
                String.join(", ", allNames), allCount,
                String.join(", ", playerNames), playerCount,
                String.join(", ", hostileNames), hostileCount,
                String.join(", ", passiveNames), passiveCount);
    }

    private boolean isHostileNearbyEntity(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof IMob
                || entity instanceof EntityDragon
                || entity.isCreatureType(EnumCreatureType.MONSTER, false);
    }

    private boolean isPassiveNearbyEntity(EntityLivingBase entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof EntityAnimal
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntityWaterMob
                || entity instanceof EntityVillager
                || entity instanceof EntityGolem
                || entity instanceof EntityCreature
                || entity.isCreatureType(EnumCreatureType.CREATURE, false)
                || entity.isCreatureType(EnumCreatureType.AMBIENT, false)
                || entity.isCreatureType(EnumCreatureType.WATER_CREATURE, false);
    }

    private String normalizeEntityName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String trimmed = name.trim();
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            boolean whitespace = Character.isWhitespace(ch) || Character.isSpaceChar(ch) || ch == '\u3000';
            if (whitespace) {
                if (!previousWhitespace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                previousWhitespace = true;
            } else {
                normalized.append(ch);
                previousWhitespace = false;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private static final class NearbyEntitySummary {
        private static final NearbyEntitySummary EMPTY = new NearbyEntitySummary("", 0, "", 0, "", 0, "", 0);

        private final String allSignature;
        private final int allCount;
        private final String playerSignature;
        private final int playerCount;
        private final String hostileSignature;
        private final int hostileCount;
        private final String passiveSignature;
        private final int passiveCount;

        private NearbyEntitySummary(String allSignature, int allCount,
                String playerSignature, int playerCount,
                String hostileSignature, int hostileCount,
                String passiveSignature, int passiveCount) {
            this.allSignature = allSignature;
            this.allCount = allCount;
            this.playerSignature = playerSignature;
            this.playerCount = playerCount;
            this.hostileSignature = hostileSignature;
            this.hostileCount = hostileCount;
            this.passiveSignature = passiveSignature;
            this.passiveCount = passiveCount;
        }

        private boolean isEmpty() {
            return allSignature.isEmpty()
                    && allCount <= 0
                    && playerSignature.isEmpty()
                    && playerCount <= 0
                    && hostileSignature.isEmpty()
                    && hostileCount <= 0
                    && passiveSignature.isEmpty()
                    && passiveCount <= 0;
        }

        private boolean hasChangedSince(NearbyEntitySummary previous) {
            if (previous == null) {
                return !isEmpty();
            }
            return !allSignature.equals(previous.allSignature)
                    || allCount != previous.allCount
                    || !playerSignature.equals(previous.playerSignature)
                    || playerCount != previous.playerCount
                    || !hostileSignature.equals(previous.hostileSignature)
                    || hostileCount != previous.hostileCount
                    || !passiveSignature.equals(previous.passiveSignature)
                    || passiveCount != previous.passiveCount;
        }

        private void writeToTriggerData(JsonObject target, String prefix) {
            if (target == null || prefix == null || prefix.isEmpty()) {
                return;
            }
            target.addProperty(prefix, allSignature);
            target.addProperty(prefix + "Player", playerSignature);
            target.addProperty(prefix + "Hostile", hostileSignature);
            target.addProperty(prefix + "Passive", passiveSignature);
            target.addProperty(prefix + "Count", allCount);
            target.addProperty(prefix + "PlayerCount", playerCount);
            target.addProperty(prefix + "HostileCount", hostileCount);
            target.addProperty(prefix + "PassiveCount", passiveCount);
        }
    }

    private void runGuarded(String errorMessage, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(errorMessage, e);
        }
    }

    private void runGuarded(String featureName, String errorMessage, Runnable task) {
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer(featureName);
        try {
            task.run();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(errorMessage, e);
        } finally {
            timer.stop();
        }
    }

}

