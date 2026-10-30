// [已修复] src/main/java/com/mythos/mythosScriptMod/mythosScriptMod.java
package com.mythos.mythosScriptMod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.mythos.mythosScriptMod.config.BaritoneSettingsConfig;
import com.mythos.mythosScriptMod.config.ChatOptimizationConfig;
import com.mythos.mythosScriptMod.config.BlinkPathingConfig;
import com.mythos.mythosScriptMod.config.FlightPathingConfig;
import com.mythos.mythosScriptMod.config.LoopExecutionConfig;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.config.DebugLogManager;
import com.mythos.mythosScriptMod.config.HumanLikeMovementConfig;
import com.mythos.mythosScriptMod.gui.CustomGuiNewChat;
import com.mythos.mythosScriptMod.gui.DetachedSwingWindowManager;
import com.mythos.mythosScriptMod.gui.GuiInventory;
import com.mythos.mythosScriptMod.gui.GuiModernMainScreen;
import com.mythos.mythosScriptMod.gui.MainUiLayoutManager;
import com.mythos.mythosScriptMod.gui.OverlayGuiHandler;
import com.mythos.mythosScriptMod.gui.components.GlobalThemedButtonHandler;
import com.mythos.mythosScriptMod.handlers.AutoEatHandler;
import com.mythos.mythosScriptMod.handlers.AutoEscapeHandler;
import com.mythos.mythosScriptMod.handlers.AutoFishingHandler;
import com.mythos.mythosScriptMod.handlers.ChatEventHandler;
import com.mythos.mythosScriptMod.handlers.ConditionalExecutionHandler;
import com.mythos.mythosScriptMod.handlers.DungeonWarehouseHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.handlers.FlyHandler;
import com.mythos.mythosScriptMod.handlers.FlightCorridorCollisionHandler;
import com.mythos.mythosScriptMod.handlers.GuiBlockerHandler;
import com.mythos.mythosScriptMod.handlers.GuiVisibilityHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.path.PathRecordingManager;
import com.mythos.mythosScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.mythos.mythosScriptMod.path.runtime.ScopedRuntimeVariables;
import com.mythos.mythosScriptMod.system.KeybindManager;
import com.mythos.mythosScriptMod.gui.packet.PacketFilterConfig;
import com.mythos.mythosScriptMod.gui.packet.PacketInterceptConfig;
import com.mythos.mythosScriptMod.system.ProfileManager;
import com.mythos.mythosScriptMod.utils.ModUtils;
import com.mythos.mythosScriptMod.shadowbaritone.utils.HumanLikeMovementController;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.HumanLikeRouteTemplateManager;
import com.mythos.mythosScriptMod.utils.HttpsCompat;
import com.mythos.mythosScriptMod.utils.PacketCaptureHandler;
import com.mythos.mythosScriptMod.utils.ReflectionCompat;
import com.mythos.mythosScriptMod.utils.ClientTranslationInjector;
import com.mythos.mythosScriptMod.utils.TextureManagerHelper;
import com.mythos.mythosScriptMod.handlers.WarehouseEventHandler; // 新增导入
import com.mythos.mythosScriptMod.handlers.WarehouseManager; // 新增导入
import com.mythos.mythosScriptMod.handlers.BlockReplacementHandler;
import com.mythos.mythosScriptMod.handlers.ShadowBaritoneForgeBridge;
import com.mythos.mythosScriptMod.system.DebugKeybindManager;
import com.mythos.mythosScriptMod.system.command.BaritoneChatCommand;
import com.mythos.mythosScriptMod.system.command.CopyNameCommand;
import com.mythos.mythosScriptMod.system.command.RunNodeSequenceCommand;
import com.mythos.mythosScriptMod.system.SimulatedKeyInputManager;
import com.mythos.mythosScriptMod.mcp.MythosScriptMcpServer;

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
import com.mythos.mythosScriptMod.handlers.AutoPickupHandler;
import com.mythos.mythosScriptMod.handlers.EmbeddedNavigationHandler;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(modid = "mythos_script", name = mythosScriptMod.NAME, version = mythosScriptMod.VERSION)
public class mythosScriptMod {
    public static final String MODID = "mythos_script";
    public static final String NAME = "MythosScript";
    public static final String VERSION = "v1.0.74";

    public static final Logger LOGGER = LogManager.getLogger(mythosScriptMod.class);
    public static mythosScriptMod instance;
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean isGuiVisible = false;
    private static final KeyBinding guiKey = new KeyBinding("key.mythos_script.open_menu", Keyboard.KEY_F,
            "key.categories.mythos_script");

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

    private static boolean attachedRuntime;

    public static boolean isAttachedRuntime() { return attachedRuntime; }

    // ---- cached NexusHooks handles ----
    // Class.forName+getMethod on every call was the top jdk-reflection cost in
    // the 30-bot CPU profile — resolve once and reuse.
    private static Class<?> nexusHooksCls;
    private static java.lang.reflect.Method nexusInContextM;
    private static java.lang.reflect.Method nexusContextMismatchM;
    private static boolean nexusHandlesTried;

    private static void initNexusHandles() {
        if (nexusHandlesTried) return;
        nexusHandlesTried = true;
        try {
            nexusHooksCls = Class.forName("com.mythos.nexus.core.NexusHooks");
            nexusInContextM = nexusHooksCls.getMethod("inContext");
            nexusContextMismatchM = nexusHooksCls.getMethod("contextMismatch");
        } catch (Throwable ignored) {}
    }

    /**
     * True while Nexus is dispatching packets for a session that is NOT the
     * mounted one.  When true, every Forge event handler in this class
     * should early-out — the mc singleton belongs to the mounted session,
     * not the session currently executing.
     * Implemented via reflection so MythosScript compiles without Nexus.
     */
    public static boolean nexusInBackgroundContext() {
        initNexusHandles();
        if (nexusInContextM == null || nexusContextMismatchM == null) return false;
        try {
            Object ctx = nexusInContextM.invoke(null);
            if (ctx == null) return false;
            return Boolean.TRUE.equals(nexusContextMismatchM.invoke(null));
        } catch (Throwable t) {
            return false; // Nexus absent → single-instance mode
        }
    }

    /** Called only on the Minecraft client thread by the version-checked Agent. */
    public static synchronized void initializeForAttach() {
        if (instance != null) return;
        attachedRuntime = true;
        new mythosScriptMod().init(null);
    }
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        HttpsCompat.install();
        DebugLogManager.ensureLoaded();
        DebugLogManager.installLoggerBridge();

        ProfileManager.initialize();
        com.mythos.mythosScriptMod.config.session.SessionConfigBinder.apply();
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
        applyOpenMenuToVanillaKey();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(SimulatedKeyInputManager.INSTANCE);
        com.mythos.mythosScriptMod.system.HeadlessInputBridge.install();
        LOGGER.info("Key Command Mod initialized!");
        MinecraftForge.EVENT_BUS.register(GlobalEventListener.instance);
        MinecraftForge.EVENT_BUS.register(com.mythos.mythosScriptMod.gui.GuiScaleLock.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new OverlayGuiHandler());

        MinecraftForge.EVENT_BUS.register(new com.mythos.mythosScriptMod.system.GlobalKeybindListener());
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
        BlinkPathingConfig.load();
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
        mythosScriptMod.LOGGER.info("Warehouse Manager registered!");

        MinecraftForge.EVENT_BUS.register(AutoPickupHandler.INSTANCE);
        LOGGER.info("Auto Pickup Handler registered!");

        MinecraftForge.EVENT_BUS.register(EmbeddedNavigationHandler.INSTANCE);
        LOGGER.info("Embedded Navigation Handler registered!");

        MinecraftForge.EVENT_BUS.register(new ShadowBaritoneForgeBridge());
        MinecraftForge.EVENT_BUS.register(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog.INSTANCE);
        LOGGER.info("Shadow Baritone Forge Bridge registered!");

        try {
            BaritoneAPI.getProvider().getPrimaryBaritone();
            BaritoneSettingsConfig.applyRuntimeNavigationMode();
            LOGGER.info("内置导航初始化成功");
        } catch (Throwable t) {
            LOGGER.error("内置导航初始化失败", t);
        }

        // Expose the listener set so Mythos Nexus can re-fire tick events
        // per bot session (NexusTickBridge reads this).
        nexusListeners = java.util.Arrays.asList(
                this,
                SimulatedKeyInputManager.INSTANCE,
                GlobalEventListener.instance,
                ConditionalExecutionHandler.INSTANCE,
                AutoEscapeHandler.INSTANCE,
                FlyHandler.INSTANCE,
                SpeedHandler.INSTANCE,
                MovementFeatureManager.INSTANCE,
                BlockFeatureManager.INSTANCE,
                WorldFeatureManager.INSTANCE,
                RenderFeatureManager.INSTANCE,
                ItemFeatureManager.INSTANCE,
                MiscFeatureManager.INSTANCE,
                DungeonWarehouseHandler.INSTANCE,
                KillAuraHandler.INSTANCE,
                AutoFishingHandler.INSTANCE,
                WarehouseEventHandler.INSTANCE,
                AutoPickupHandler.INSTANCE,
                EmbeddedNavigationHandler.INSTANCE,
                BlockReplacementHandler.INSTANCE,
                // 后台 bot 的 DelayTask 由 owner 键控——DelayScheduler 必须随该
                // bot 的 tick 线程跑 onClientTick，否则 unmount 后循环重启等延迟
                // 任务永远等不到匹配 owner（client thread 只有 mounted owner）。
                ModUtils.DelayScheduler.instance
        );
        // Register our tick relay with Nexus if the coremod is present.
        try {
            Class<?> hooks = Class.forName("com.mythos.nexus.core.NexusHooks");
            java.lang.reflect.Method reg = hooks.getMethod(
                    "registerTickHook", Runnable.class);
            reg.invoke(null, (Runnable) com.mythos.mythosScriptMod.handlers
                    .NexusTickBridge::tickAll);
            LOGGER.info("Registered NexusTickBridge with Mythos Nexus");
            // Fast hook: DelayScheduler drains wall-clock tasks every bot
            // tick-thread iteration — not gated by the adaptive tick rate,
            // so a "2 tick" container click delay stays ~100ms real even
            // when the session is throttled to 20%.
            java.lang.reflect.Method regFast = hooks.getMethod(
                    "registerFastTickHook", Runnable.class);
            regFast.invoke(null, (Runnable) () -> {
                ModUtils.DelayScheduler s = ModUtils.DelayScheduler.instance;
                if (s != null) s.drainForCurrentOwner();
                AutoFishingHandler.INSTANCE.tickFishingFast();
                KillAuraHandler.INSTANCE.tickTeleportFast();
            });
        } catch (ClassNotFoundException e) {
            // Nexus absent — single-instance mode, no relay needed.
        } catch (Throwable t) {
            LOGGER.warn("Failed to register NexusTickBridge", t);
        }

        MinecraftForge.EVENT_BUS.register(BlockReplacementHandler.INSTANCE);
        BlockReplacementHandler.loadConfig();
        LOGGER.info("Block Replacement Handler registered!");

        MinecraftForge.EVENT_BUS.register(new GlobalThemedButtonHandler());
        MinecraftForge.EVENT_BUS.register(DetachedSwingWindowManager.INSTANCE);
        LOGGER.info("Swing detached GUI manager registered!");

        ClientCommandHandler.instance.registerCommand(new CopyNameCommand());
        ClientCommandHandler.instance.registerCommand(new BaritoneChatCommand());
        ClientCommandHandler.instance.registerCommand(new RunNodeSequenceCommand());
        ClientCommandHandler.instance.registerCommand(new com.mythos.mythosScriptMod.system.command.ReloadLangCommand());
        LOGGER.info("/copy 与 /run_node_sequence 命令已注册。");
        // 第三方插件在命令表填好之后才加载：插件注册的命令要能和自己内置命令查重，
        // 反过来（插件先注册、内置命令后注册）会让内置命令把插件的同名命令覆盖掉。
        try {
            com.mythos.mythosScriptMod.gui.modern.plugin.PluginLoader.loadAll();
        } catch (Throwable broken) {
            LOGGER.warn("第三方插件加载失败", broken);
        }
        MythosScriptMcpServer.start();
    }

    public static void tryAutoStartLoop() {
        try {
            Path autorunFile = ProfileManager.getCurrentProfileDir().resolve("mythosScriptMod_autorun.json");
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
        PathSequenceEventListener.stopAllForegroundRunners();
        PathSequenceEventListener.stopAllBackgroundRunners();
        ScopedRuntimeVariables.clearGlobalScope();
        GuiInventory.isLooping = false;
        // 断线/重登后应恢复用户持久化设置，不应强制重置为无限循环(-1)
        int persistedLoopCount = (LoopExecutionConfig.INSTANCE == null) ? 1 : LoopExecutionConfig.INSTANCE.loopCount;
        GuiInventory.loopCount = persistedLoopCount;
        GuiInventory.loopCounter = 0;
        AutoEatHandler.resetRuntimeState();

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
        mc.addScheduledTask(mythosScriptMod::resetAllStates);
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

    /** Exposed for NexusTickBridge — the exact set of listeners we
     *  registered on the Forge bus during init(). */
    private static volatile java.util.List<Object> nexusListeners =
            java.util.Collections.emptyList();
    public static java.util.List<Object> nexusListeners() {
        return nexusListeners;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Per-session support (Mythos Nexus): when running inside a bot's
        // packet context the mc singleton points at a DIFFERENT session —
        // ClientTickEvent here means "the mounted session". Background bots
        // get their tick via NexusHooks.runSessionTicks calling
        // NexusTickBridge.tickFor(session) below.
        if (nexusInBackgroundContext()) {
            // A non-mounted session is dispatching packets on this thread.
            // The normal Forge bus already fired this tick on the client
            // thread for the mounted session — we must NOT also run
            // MythosScript ticks here or feature state would double-apply.
            // The background tick is driven explicitly by NexusTickBridge.
            return;
        }

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
            // 挂载的会话变了就把静态配置字段换成新会话那一套（各会话配置独立存储）。
            com.mythos.mythosScriptMod.config.session.SessionConfigSync.tick();
            if (mc.currentScreen == null) {
                pullOpenMenuFromVanillaKeyIfChanged();
            }
            consumeGuiToggleKey();
            // 插件 tick 挂在挂载会话的客户端 tick 上：`mc` 此时指向玩家正在看的那个会话，
            // 插件拿到的 Minecraft 实例就是它，不需要插件自己处理多开路由。
            com.mythos.mythosScriptMod.gui.modern.plugin.PluginLoader.tick();
        }

    }

    @SubscribeEvent
    public void onClientChat(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        // Plugins see exactly what the player sees. Action-bar lines (type 2) are
        // excluded on purpose: they refresh several times per second and would turn
        // every plugin's chat log into noise.
        if (event.getType() == net.minecraft.util.text.ChatType.GAME_INFO) {
            return;
        }
        com.mythos.mythosScriptMod.gui.modern.plugin.PluginLoader.onChatMessage(
                event.getMessage().getUnformattedText());
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
        // Menu is allowed at the title screen too — the multiboxer leaves
        // the human parked at title while bots run, and F must still open it.
        // (Formerly this block drained the keypress and returned when
        // mc.player/mc.world were null, which silently ate F at title.)
        //
        // Multibox: guiKey.pressTime is shared static state. Background
        // sessions re-fire this method via NexusTickBridge — if one drains
        // isPressed() first, the mounted session (the screen the user is
        // actually looking at) never toggles. Only the mounted session may
        // consume the key; background sessions must not even drain it.
        String mountedName = com.mythos.mythosScriptMod.mcp.NexusSessions.mountedUsername();
        if (mountedName != null && mc.player != null
                && !mc.player.getName().equalsIgnoreCase(mountedName)) {
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
        LOGGER.info("mythosScriptMod: Connected to server, attempting to inject PacketCaptureHandler...");
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
                mythosScriptMod.tryAutoStartLoop();
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

    /** Vanilla Controls and the script OPEN_MENU binding are the same key. */
    public static void applyOpenMenuToVanillaKey() {
        guiKey.setKeyCode(com.mythos.mythosScriptMod.system.KeybindManager
                .primaryKeyCode(com.mythos.mythosScriptMod.system.BindableAction.OPEN_MENU));
        KeyBinding.resetKeyBindingArrayAndHash();
    }

    public static void pullOpenMenuFromVanillaKeyIfChanged() {
        int shown = guiKey.getKeyCode();
        int saved = com.mythos.mythosScriptMod.system.KeybindManager
                .primaryKeyCode(com.mythos.mythosScriptMod.system.BindableAction.OPEN_MENU);
        if (shown == saved) {
            return;
        }
        com.mythos.mythosScriptMod.system.KeybindManager.keybinds.put(
                com.mythos.mythosScriptMod.system.BindableAction.OPEN_MENU,
                new com.mythos.mythosScriptMod.system.KeybindManager.Keybind(shown, new java.util.HashSet<Integer>()));
        com.mythos.mythosScriptMod.system.KeybindManager.saveConfig();
    }

    public static int getGuiToggleKeyCode() {
        return guiKey.getKeyCode();
    }

}

