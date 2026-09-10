// [已修复] src/main/java/com/zszl/zszlScriptMod/zszlScriptMod.java
package com.zszl.zszlScriptMod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.zszl.zszlScriptMod.config.BaritoneSettingsConfig;
import com.zszl.zszlScriptMod.config.ChatOptimizationConfig;
import com.zszl.zszlScriptMod.config.FlightPathingConfig;
import com.zszl.zszlScriptMod.config.LoopExecutionConfig;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.config.DebugLogManager;
import com.zszl.zszlScriptMod.config.HumanLikeMovementConfig;
import com.zszl.zszlScriptMod.gui.CustomGuiNewChat;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.OverlayGuiHandler;
import com.zszl.zszlScriptMod.gui.components.GlobalThemedButtonHandler;
import com.zszl.zszlScriptMod.handlers.AutoEatHandler;
import com.zszl.zszlScriptMod.handlers.AutoEscapeHandler;
import com.zszl.zszlScriptMod.handlers.AutoFishingHandler;
import com.zszl.zszlScriptMod.handlers.ChatEventHandler;
import com.zszl.zszlScriptMod.handlers.ConditionalExecutionHandler;
import com.zszl.zszlScriptMod.handlers.DungeonWarehouseHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.handlers.FlightCorridorCollisionHandler;
import com.zszl.zszlScriptMod.handlers.GuiBlockerHandler;
import com.zszl.zszlScriptMod.handlers.GuiVisibilityHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathRecordingManager;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.system.KeybindManager;
import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketInterceptConfig;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.shadowbaritone.utils.HumanLikeMovementController;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.HumanLikeRouteTemplateManager;
import com.zszl.zszlScriptMod.utils.HttpsCompat;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import com.zszl.zszlScriptMod.utils.ClientTranslationInjector;
import com.zszl.zszlScriptMod.utils.TextureManagerHelper;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler; // 新增导入
import com.zszl.zszlScriptMod.handlers.WarehouseManager; // 新增导入
import com.zszl.zszlScriptMod.handlers.AutoEquipHandler;
import com.zszl.zszlScriptMod.handlers.BlockReplacementHandler;
import com.zszl.zszlScriptMod.handlers.ShadowBaritoneForgeBridge;
import com.zszl.zszlScriptMod.system.DebugKeybindManager;
import com.zszl.zszlScriptMod.system.command.BaritoneChatCommand;
import com.zszl.zszlScriptMod.system.command.CopyNameCommand;
import com.zszl.zszlScriptMod.system.command.RunNodeSequenceCommand;
import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import com.zszl.zszlScriptMod.mcp.MythosScriptMcpServer;

import net.minecraft.client.gui.GuiIngame;
import net.minecraftforge.client.ClientCommandHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import com.zszl.zszlScriptMod.handlers.AutoPickupHandler;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(modid = "zszl_script", name = zszlScriptMod.NAME, version = zszlScriptMod.VERSION)
public class zszlScriptMod {
    public static final String MODID = "zszl_script";
    public static final String NAME = "我的世界脚本";
    public static final String VERSION = "v1.0.7";

    public static final Logger LOGGER = LogManager.getLogger(zszlScriptMod.class);
    public static zszlScriptMod instance;
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean isGuiVisible = false;
    private static final KeyBinding guiKey = new KeyBinding("key.zszl_script.open_menu", Keyboard.KEY_F,
            "key.categories.zszl_script");

    private boolean hasInjectedGui = false;
    private boolean guiToggleHandledByScreenEvent;

    private static class AutoRunConfig {
        @SerializedName("autoLoop")
        boolean autoLoop = false;
        @SerializedName("loopSequence")
        String loopSequence = "";
        @SerializedName("loopCount")
        int loopCount = 1;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        HttpsCompat.install();
        DebugLogManager.ensureLoaded();
        DebugLogManager.installLoggerBridge();

        ProfileManager.initialize();
        ModConfig.loadAllConfigs();
        LoopExecutionConfig.load();
        GuiInventory.loopCount = LoopExecutionConfig.INSTANCE.loopCount;
        ChatOptimizationConfig.load();
        BaritoneSettingsConfig.load();
        HumanLikeMovementConfig.load();
        HumanLikeRouteTemplateManager.load();
        PacketFilterConfig.load();
        PacketInterceptConfig.load();

        TextureManagerHelper.clearCache();
        ClientTranslationInjector.INSTANCE.install();

        ClientRegistry.registerKeyBinding(guiKey);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(SimulatedKeyInputManager.INSTANCE);
        LOGGER.info("Key Command Mod initialized!");
        MinecraftForge.EVENT_BUS.register(GlobalEventListener.instance);
        MinecraftForge.EVENT_BUS.register(new OverlayGuiHandler());

        MinecraftForge.EVENT_BUS.register(new com.zszl.zszlScriptMod.system.GlobalKeybindListener());
        KeybindManager.loadConfig();
        LOGGER.info("Global Keybind Listener registered and config loaded!");

        DebugKeybindManager.loadConfig();
        LOGGER.info("Debug Keybind Manager initialized!");

        MinecraftForge.EVENT_BUS.register(ConditionalExecutionHandler.INSTANCE);
        ConditionalExecutionHandler.loadConfig();
        LOGGER.info("Conditional Execution Handler registered!");

        MinecraftForge.EVENT_BUS.register(AutoEscapeHandler.INSTANCE);
        AutoEscapeHandler.loadConfig();
        LOGGER.info("Auto Escape Handler registered!");

        ModUtils.DelayScheduler.init();
        LOGGER.info("Delay scheduler initialized!");
        MinecraftForge.EVENT_BUS.register(FlyHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FlightCorridorCollisionHandler.INSTANCE);
        FlyHandler.loadConfig();
        FlightPathingConfig.load();
        LOGGER.info("Fly Handler registered!");

        MinecraftForge.EVENT_BUS.register(SpeedHandler.INSTANCE);
        SpeedHandler.loadConfig();
        LOGGER.info("Speed Handler registered!");

        MinecraftForge.EVENT_BUS.register(MovementFeatureManager.INSTANCE);
        MovementFeatureManager.loadConfig();
        LOGGER.info("Movement Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(BlockFeatureManager.INSTANCE);
        BlockFeatureManager.loadConfig();
        LOGGER.info("Block Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(WorldFeatureManager.INSTANCE);
        WorldFeatureManager.loadConfig();
        LOGGER.info("World Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(RenderFeatureManager.INSTANCE);
        RenderFeatureManager.loadConfig();
        LOGGER.info("Render Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(ItemFeatureManager.INSTANCE);
        ItemFeatureManager.loadConfig();
        LOGGER.info("Item Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(MiscFeatureManager.INSTANCE);
        MiscFeatureManager.loadConfig();
        LOGGER.info("Misc Feature Handler registered!");

        MinecraftForge.EVENT_BUS.register(DungeonWarehouseHandler.INSTANCE);
        DungeonWarehouseHandler.loadConfig();
        LOGGER.info("Dungeon Warehouse Handler registered!");

        MinecraftForge.EVENT_BUS.register(new ChatEventHandler());

        MinecraftForge.EVENT_BUS.register(KillAuraHandler.INSTANCE);
        KillAuraHandler.loadConfig();
        LOGGER.info("Kill Aura Handler registered!");

        MinecraftForge.EVENT_BUS.register(AutoFishingHandler.INSTANCE);
        AutoFishingHandler.loadConfig();
        LOGGER.info("Auto Fishing Handler registered!");

        MinecraftForge.EVENT_BUS.register(WarehouseEventHandler.INSTANCE);
        WarehouseManager.loadWarehouses();
        zszlScriptMod.LOGGER.info("Warehouse Manager registered!");

        MinecraftForge.EVENT_BUS.register(AutoPickupHandler.INSTANCE);
        LOGGER.info("Auto Pickup Handler registered!");

        MinecraftForge.EVENT_BUS.register(EmbeddedNavigationHandler.INSTANCE);
        LOGGER.info("Embedded Navigation Handler registered!");

        MinecraftForge.EVENT_BUS.register(new ShadowBaritoneForgeBridge());
        LOGGER.info("Shadow Baritone Forge Bridge registered!");

        try {
            BaritoneAPI.getProvider().getPrimaryBaritone();
            BaritoneSettingsConfig.applyRuntimeNavigationMode();
            LOGGER.info("内置导航初始化成功");
        } catch (Throwable t) {
            LOGGER.error("内置导航初始化失败", t);
        }

        MinecraftForge.EVENT_BUS.register(AutoEquipHandler.INSTANCE);
        AutoEquipHandler.loadConfig();
        LOGGER.info("Auto Equip Handler registered!");

        MinecraftForge.EVENT_BUS.register(BlockReplacementHandler.INSTANCE);
        BlockReplacementHandler.loadConfig();
        LOGGER.info("Block Replacement Handler registered!");

        MinecraftForge.EVENT_BUS.register(new GlobalThemedButtonHandler());
        MinecraftForge.EVENT_BUS.register(DetachedSwingWindowManager.INSTANCE);
        LOGGER.info("Swing detached GUI manager registered!");

        ClientCommandHandler.instance.registerCommand(new CopyNameCommand());
        ClientCommandHandler.instance.registerCommand(new BaritoneChatCommand());
        ClientCommandHandler.instance.registerCommand(new RunNodeSequenceCommand());
        LOGGER.info("/copy 与 /run_node_sequence 命令已注册。");
        MythosScriptMcpServer.start();
    }

    public static void tryAutoStartLoop() {
        try {
            Path autorunFile = ProfileManager.getCurrentProfileDir().resolve("zszlScriptMod_autorun.json");
            LOGGER.info("尝试读取当前配置的自动运行文件: " + autorunFile.toAbsolutePath());

            if (!Files.exists(autorunFile)) {
                return;
            }

            String content = new String(Files.readAllBytes(autorunFile), java.nio.charset.StandardCharsets.UTF_8);
            Gson gson = new Gson();
            AutoRunConfig config = gson.fromJson(content, AutoRunConfig.class);

            try {
                Files.delete(autorunFile);
                LOGGER.info("已成功读取并删除一次性自动运行配置: {}", autorunFile.getFileName());
            } catch (IOException e) {
                LOGGER.error("删除自动运行配置文件失败！", e);
            }

            if (config != null && config.autoLoop && config.loopSequence != null && !config.loopSequence.isEmpty()) {
                LOGGER.info("检测到需要自动循环执行：序列 '{}', 次数: {}", config.loopSequence, config.loopCount);
                mc.addScheduledTask(() -> {
                    if (mc.player == null || mc.world == null) {
                        LOGGER.error("无法启动自动循环：玩家或世界未初始化");
                        return;
                    }
                    try {
                        GuiInventory.loopCount = config.loopCount;
                        GuiInventory.loopCounter = 0;
                        GuiInventory.isLooping = true;
                        PathSequenceManager.runPathSequence(config.loopSequence);
                    } catch (Exception e) {
                        LOGGER.error("自动循环执行失败", e);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("读取或解析自动循环配置失败", e);
        }
    }

    public static void resetAllStates() {
        LOGGER.info("重置所有Mod状态...");
        if (PathSequenceEventListener.instance.isTracking()) {
            PathSequenceEventListener.instance.stopTracking();
        }
        PathSequenceEventListener.stopAllBackgroundRunners();
        ScopedRuntimeVariables.clearGlobalScope();
        GuiInventory.isLooping = false;
        // 断线/重登后应恢复用户持久化设置，不应强制重置为无限循环(-1)
        int persistedLoopCount = (LoopExecutionConfig.INSTANCE == null) ? 1 : LoopExecutionConfig.INSTANCE.loopCount;
        GuiInventory.loopCount = persistedLoopCount;
        GuiInventory.loopCounter = 0;
        AutoEatHandler.isEating = false;
        AutoEatHandler.originalHotbarSlot = -1;
        AutoEatHandler.swappedItem = net.minecraft.item.ItemStack.EMPTY;

        AutoEscapeHandler.resetRuntimeState();
        GuiBlockerHandler.reset();
        GuiVisibilityHandler.reset();
        KillAuraHandler.INSTANCE.resetRuntimeState();
        AutoFishingHandler.INSTANCE.resetRuntimeState();
        SpeedHandler.INSTANCE.onClientDisconnect();
        MovementFeatureManager.INSTANCE.onClientDisconnect();
        BlockFeatureManager.INSTANCE.onClientDisconnect();
        RenderFeatureManager.INSTANCE.onClientDisconnect();
        WorldFeatureManager.INSTANCE.onClientDisconnect();
        ItemFeatureManager.INSTANCE.onClientDisconnect();
        MiscFeatureManager.INSTANCE.onClientDisconnect();
        HumanLikeMovementController.INSTANCE.reset();
        SimulatedKeyInputManager.INSTANCE.reset();
        PacketCaptureHandler.clearRuntimeState();
        LOGGER.info("Mod状态已完全重置。");
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        LOGGER.info("已从服务器断开连接，将重置所有Mod状态。");
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("state", "disconnected");
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_SERVER_DISCONNECT, triggerData);
        mc.addScheduledTask(zszlScriptMod::resetAllStates);
        mc.addScheduledTask(FlyHandler.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(SpeedHandler.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(MovementFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(BlockFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(RenderFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(WorldFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(ItemFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(MiscFeatureManager.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(KillAuraHandler.INSTANCE::onClientDisconnect);
        mc.addScheduledTask(AutoFishingHandler.INSTANCE::onClientDisconnect);

    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !hasInjectedGui) {
            if (mc.ingameGUI != null) {
                try {
                    CustomGuiNewChat customChat = new CustomGuiNewChat(mc);

                    ReflectionCompat.setPrivateValue(
                            GuiIngame.class,
                            mc.ingameGUI,
                            customChat,
                            "persistantChatGUI", "field_73840_e");

                    LOGGER.info("Successfully injected CustomGuiNewChat using ObfuscationReflectionHelper!");

                } catch (Exception e) {
                    LOGGER.error("Failed to inject CustomGuiNewChat!", e);
                } finally {
                    hasInjectedGui = true;
                }
            }
        }

        if (event.phase == TickEvent.Phase.END) {
            consumeGuiToggleKey();
        }

    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == Keyboard.KEY_N
                && mc.currentScreen instanceof GuiModernMainScreen
                && !GuiModernMainScreen.hasTextInputFocus(mc)
                && !PathRecordingManager.isRecording()) {
            PathRecordingManager.startRecording();
            mc.displayGuiScreen(null);
            return;
        }
        consumeGuiToggleKey();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGuiKeyboardInputPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (mc.player == null || mc.world == null || !Keyboard.getEventKeyState()
                || Keyboard.getEventKey() != guiKey.getKeyCode()
                || GuiModernMainScreen.hasTextInputFocus(mc) || KeybindManager.isRecording()) {
            return;
        }

        toggleGuiVisibility();
        guiToggleHandledByScreenEvent = true;
        event.setCanceled(true);
    }

    private void consumeGuiToggleKey() {
        if (mc.player == null || mc.world == null) {
            while (guiKey.isPressed()) {
            }
            return;
        }

        if (guiToggleHandledByScreenEvent) {
            guiToggleHandledByScreenEvent = false;
            while (guiKey.isPressed()) {
            }
            return;
        }

        if (KeybindManager.isRecording()) {
            while (guiKey.isPressed()) {
            }
            return;
        }

        if (DetachedSwingWindowManager.isDetached()) {
            while (guiKey.isPressed()) {
                if (!GuiModernMainScreen.hasTextInputFocus(mc)) {
                    // Detached mode is controlled by focus only.  Pressing F
                    // in Minecraft brings the retained Swing window forward;
                    // it must never close or dock that window.
                    DetachedSwingWindowManager.INSTANCE.focusDetachedWindow();
                }
            }
            return;
        }

        while (guiKey.isPressed()) {
            if (!GuiModernMainScreen.hasTextInputFocus(mc)) {
                toggleGuiVisibility();
            }
        }
    }

    private void toggleGuiVisibility() {
        boolean menuOpen = mc.currentScreen instanceof GuiModernMainScreen;
        if (menuOpen) {
            if (PathSequenceEventListener.instance.wasPausedByGui()) {
                PathSequenceEventListener.instance.resume();
            }
            GuiModernMainScreen.toggleMenu(mc);
            return;
        }

        boolean wasVisible = isGuiVisible;
        GuiModernMainScreen.openMenu(mc);
        if (!wasVisible && MainUiLayoutManager.isModernAutoPauseOnMenuOpen()) {
            PathSequenceEventListener.instance.pauseByGui();
        }
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        LOGGER.info("zszlScriptMod: Connected to server, attempting to inject PacketCaptureHandler...");
        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("state", "connected");
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_SERVER_CONNECT, triggerData);

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                /* 忽略 */ }

            mc.addScheduledTask(() -> {
                try {
                    NetHandlerPlayClient netHandler = mc.getConnection();
                    if (netHandler == null) {
                        LOGGER.error("注入失败: NetHandlerPlayClient is null.");
                        return;
                    }
                    LOGGER.info("步骤 1: 获取 NetHandlerPlayClient 成功: " + netHandler.getClass().getName());

                    NetworkManager networkManager = netHandler.getNetworkManager();
                    if (networkManager == null) {
                        LOGGER.error("注入失败: NetworkManager is null.");
                        return;
                    }
                    LOGGER.info("步骤 2: 获取 NetworkManager 成功: " + networkManager.getClass().getName());

                    Field channelField = null;
                    try {
                        channelField = NetworkManager.class.getDeclaredField("channel");
                        LOGGER.info("计划 A 成功: 通过名称 'channel' 找到字段。");
                    } catch (NoSuchFieldException e) {
                        LOGGER.warn("计划 A 失败: 字段 'channel' 未找到。尝试计划 B (按类型搜索)...");
                        for (Field f : NetworkManager.class.getDeclaredFields()) {
                            if (Channel.class.isAssignableFrom(f.getType())) {
                                channelField = f;
                                LOGGER.info("计划 B 成功: 通过类型找到字段: '{}' (类型: {})", f.getName(), f.getType().getName());
                                break;
                            }
                        }
                    }

                    if (channelField == null) {
                        LOGGER.error("计划 B 失败: 无法找到类型为 io.netty.channel.Channel 的字段。");
                        LOGGER.error("--- NetworkManager 字段列表 ---");
                        for (Field f : NetworkManager.class.getDeclaredFields()) {
                            LOGGER.error("字段名: {} | 类型: {}", f.getName(), f.getType().getName());
                        }
                        LOGGER.error("---------------------------------");
                        throw new IllegalStateException("PacketCaptureHandler 注入失败。请检查日志中的字段列表。");
                    }

                    channelField.setAccessible(true);
                    Channel channel = (Channel) channelField.get(networkManager);
                    if (channel == null) {
                        LOGGER.error("注入失败: 'channel' 字段的值为 null。");
                        return;
                    }
                    LOGGER.info("步骤 3: 成功访问 Netty Channel。");

                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get("keycommand_packet_handler") != null) {
                        pipeline.remove("keycommand_packet_handler");
                        LOGGER.info("在注入前移除了已存在的 packet handler。");
                    }
                    pipeline.addBefore("packet_handler", "keycommand_packet_handler", new PacketCaptureHandler());
                    LOGGER.info("步骤 4: PacketCaptureHandler 已成功注入到网络管道中！");

                } catch (Exception e) {
                    LOGGER.error("在注入 PacketCaptureHandler 期间发生严重错误！", e);
                }
            });
        }).start();

        new Thread(() -> {
            int waited = 0;
            while (mc.player == null && waited < 15) {
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
                waited++;
            }
            if (mc.player == null) {
                LOGGER.error("等待超时，player仍然为null，自动循环取消");
                return;
            }
            mc.addScheduledTask(() -> {
                LOGGER.info("Player已加载，执行 tryAutoStartLoop");
                zszlScriptMod.tryAutoStartLoop();
            });
        }).start();
    }

    public static boolean ArriveAt(double x, double y, double z, double tolerance) {
        if (mc == null || mc.player == null) {
            return false;
        }
        EntityPlayerSP player = mc.player;
        double playerX = player.posX;
        double playerY = player.posY;
        double playerZ = player.posZ;
        double distanceSq = 0;
        int coordCount = 0;
        if (!Double.isNaN(x)) {
            distanceSq += Math.pow(playerX - x, 2);
            coordCount++;
        }
        if (!Double.isNaN(y)) {
            distanceSq += Math.pow(playerY - y, 2);
            coordCount++;
        }
        if (!Double.isNaN(z)) {
            distanceSq += Math.pow(playerZ - z, 2);
            coordCount++;
        }
        if (coordCount == 0) {
            return true;
        }
        return distanceSq <= Math.pow(tolerance, 2);
    }

    public static int getGuiToggleKeyCode() {
        return guiKey.getKeyCode();
    }

}

