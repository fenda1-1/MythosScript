// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/KeybindManager.java
package com.zszl.zszlScriptMod.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.gui.modern.packet.PacketSendSupport;
import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import com.zszl.zszlScriptMod.handlers.AutoEatHandler;
import com.zszl.zszlScriptMod.handlers.AutoFishingHandler;
import com.zszl.zszlScriptMod.handlers.AutoPickupHandler;
import com.zszl.zszlScriptMod.handlers.FlyHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.EmbeddedGuiScreenInputBridge;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 快捷键系统的核心管理器。
 * 负责加载、保存、执行和管理所有自定义快捷键。
 */
public class KeybindManager {

    public static final int DEFAULT_COMMAND_PALETTE_KEY = Keyboard.KEY_F1;

    private static class KeybindConfig {
        Map<String, Keybind> actions = new HashMap<>();
        Map<String, Keybind> pathSequences = new LinkedHashMap<>();
    }

    public static class KeyCombination {
        private int keyCode;
        private Set<Integer> modifiers = new HashSet<>();

        public KeyCombination() {
        }

        public KeyCombination(int keyCode, Set<Integer> modifiers) {
            this.keyCode = keyCode;
            if (modifiers != null) {
                this.modifiers.addAll(modifiers);
            }
        }

        public int getKeyCode() {
            return keyCode;
        }

        public Set<Integer> getModifiers() {
            return Collections.unmodifiableSet(modifiers == null ? Collections.<Integer>emptySet() : modifiers);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof KeyCombination)) {
                return false;
            }
            KeyCombination that = (KeyCombination) other;
            return keyCode == that.keyCode && getModifiers().equals(that.getModifiers());
        }

        @Override
        public int hashCode() {
            return 31 * keyCode + getModifiers().hashCode();
        }
    }

    public static class Keybind {
        private int keyCode;
        private Set<Integer> modifiers;
        private String parameter;
        private List<KeyCombination> combinations = new ArrayList<>();

        public Keybind(int keyCode, Set<Integer> modifiers) {
            this.keyCode = keyCode;
            this.modifiers = modifiers != null ? new HashSet<>(modifiers) : new HashSet<>();
            this.parameter = null;
            if (keyCode != Keyboard.KEY_NONE) {
                this.combinations.add(new KeyCombination(keyCode, this.modifiers));
            }
        }

        public Keybind() {
            this.keyCode = Keyboard.KEY_NONE;
            this.modifiers = new HashSet<>();
            this.parameter = null;
            this.combinations = new ArrayList<>();
        }

        public int getKeyCode() {
            normalizeCombinations();
            return keyCode;
        }

        public Set<Integer> getModifiers() {
            normalizeCombinations();
            return modifiers;
        }

        public List<KeyCombination> getCombinations() {
            normalizeCombinations();
            List<KeyCombination> copy = new ArrayList<>();
            for (KeyCombination combination : combinations) {
                copy.add(new KeyCombination(combination.keyCode, combination.getModifiers()));
            }
            return Collections.unmodifiableList(copy);
        }

        public void setCombinations(Collection<KeyCombination> values) {
            if (combinations == null) {
                combinations = new ArrayList<>();
            } else {
                combinations.clear();
            }
            if (values != null) {
                for (KeyCombination value : values) {
                    if (value != null && value.getKeyCode() != Keyboard.KEY_NONE && !combinations.contains(value)) {
                        combinations.add(new KeyCombination(value.getKeyCode(), value.getModifiers()));
                    }
                }
            }
            syncLegacyFields();
        }

        public boolean addCombination(int keyCode, Set<Integer> modifiers) {
            if (keyCode == Keyboard.KEY_NONE) {
                return false;
            }
            normalizeCombinations();
            KeyCombination value = new KeyCombination(keyCode, modifiers);
            if (combinations.contains(value)) {
                return false;
            }
            combinations.add(value);
            syncLegacyFields();
            return true;
        }

        public boolean removeCombination(int index) {
            normalizeCombinations();
            if (index < 0 || index >= combinations.size()) {
                return false;
            }
            combinations.remove(index);
            syncLegacyFields();
            return true;
        }

        public boolean removeCombination(int keyCode, Set<Integer> modifiers) {
            normalizeCombinations();
            KeyCombination target = new KeyCombination(keyCode, modifiers);
            for (int index = 0; index < combinations.size(); index++) {
                if (combinations.get(index).equals(target)) {
                    return removeCombination(index);
                }
            }
            return false;
        }

        public void clearCombinations() {
            if (combinations == null) {
                combinations = new ArrayList<>();
            }
            combinations.clear();
            syncLegacyFields();
        }

        public boolean matches(int keyCode, Set<Integer> modifiers) {
            normalizeCombinations();
            Set<Integer> actual = modifiers == null ? Collections.<Integer>emptySet() : modifiers;
            for (KeyCombination combination : combinations) {
                if (combination.keyCode == keyCode && combination.getModifiers().equals(actual)) {
                    return true;
                }
            }
            return false;
        }

        private void normalizeCombinations() {
            if (modifiers == null) {
                modifiers = new HashSet<>();
            }
            if (combinations == null) {
                combinations = new ArrayList<>();
            }
            combinations.removeIf(value -> value == null || value.getKeyCode() == Keyboard.KEY_NONE);
            if (combinations.isEmpty() && keyCode != Keyboard.KEY_NONE) {
                combinations.add(new KeyCombination(keyCode, modifiers));
            }
            syncLegacyFields();
        }

        private void syncLegacyFields() {
            if (combinations == null || combinations.isEmpty()) {
                keyCode = Keyboard.KEY_NONE;
                modifiers = new HashSet<>();
                return;
            }
            KeyCombination primary = combinations.get(0);
            keyCode = primary.keyCode;
            modifiers = new HashSet<>(primary.getModifiers());
        }

        public String getParameter() {
            return parameter;
        } // 新增Getter

        public void setParameter(String parameter) {
            this.parameter = parameter;
        } // 新增Setter

        @Override
        public String toString() {
            normalizeCombinations();
            if (combinations.isEmpty()) {
                return I18n.format("gui.keybind.unbound");
            }
            List<String> values = new ArrayList<>();
            for (KeyCombination combination : combinations) {
                String mods = Arrays
                    .asList(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT,
                            Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)
                    .stream()
                    .filter(mod -> combination.getModifiers().contains(mod))
                    .map(mod -> {
                        if (mod == Keyboard.KEY_LCONTROL || mod == Keyboard.KEY_RCONTROL)
                            return "Ctrl";
                        if (mod == Keyboard.KEY_LSHIFT || mod == Keyboard.KEY_RSHIFT)
                            return "Shift";
                        if (mod == Keyboard.KEY_LMENU || mod == Keyboard.KEY_RMENU)
                            return "Alt";
                        return "";
                    })
                    .distinct()
                    .collect(Collectors.joining(" + "));
                String keyName = Keyboard.getKeyName(combination.keyCode);
                values.add(mods.isEmpty() ? "[" + keyName + "]" : "[" + mods + " + " + keyName + "]");
            }
            return String.join(" / ", values);
        }
    }

    public static final Map<BindableAction, Keybind> keybinds = new EnumMap<>(BindableAction.class);
    public static final Map<String, Keybind> pathSequenceKeybinds = new LinkedHashMap<>();
    private static volatile boolean recording;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setRecording(boolean value) {
        recording = value;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static void executeAction(BindableAction action) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null)
            return;
        if (action != null && action.isFeatureToggle()) {
            toggleManagedFeature(mc, action);
            return;
        }

        switch (action) {
            case OPEN_COMMAND_PALETTE:
                GuiModernMainScreen activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.toggleCommandPalette();
                }
                break;
            case CLOSE_ACTIVE_TAB:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.closeActiveTab();
                }
                break;
            case RESTORE_CLOSED_TAB:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.restoreClosedTab();
                }
                break;
            case OPEN_RECENT_COMMAND:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.openRecentCommandPalette();
                } else if (mc.currentScreen == null && !DetachedSwingWindowManager.isDetached()) {
                    GuiModernMainScreen.openMenu(mc);
                    mc.addScheduledTask(() -> {
                        GuiModernMainScreen screen = activeModernScreen(mc);
                        if (screen != null) screen.openRecentCommandPalette();
                    });
                }
                break;
            case FOCUS_GLOBAL_SEARCH:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.focusGlobalSearch();
                } else if (mc.currentScreen == null) {
                    GuiModernMainScreen.openMenu(mc);
                    mc.addScheduledTask(() -> {
                        GuiModernMainScreen screen = activeModernScreen(mc);
                        if (screen != null) screen.focusGlobalSearch();
                    });
                }
                break;
            case OPEN_RECENT_SEQUENCE:
                executeRankedSequence(mc, MainUiLayoutManager.getSequencesByRecentUse(), 0);
                break;
            case SAVE_CONFIGURATIONS:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.saveActiveConfiguration();
                } else {
                    try {
                        EmbeddedGuiScreenInputBridge.invokeSaveButton(mc.currentScreen);
                    } catch (IOException exception) {
                        zszlScriptMod.LOGGER.warn("Failed to invoke the current screen save action", exception);
                    }
                    saveConfig();
                    MainUiLayoutManager.save();
                }
                break;
            case EMERGENCY_STOP:
                EmbeddedNavigationHandler.INSTANCE.stop();
                PathSequenceEventListener.stopForegroundSequenceByAction();
                PathSequenceEventListener.stopAllBackgroundRunners();
                GuiInventory.isLooping = false;
                break;
            case NEXT_TAB:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.switchTabByOffset(1);
                }
                break;
            case PREVIOUS_TAB:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.switchTabByOffset(-1);
                }
                break;
            case TOGGLE_AUTOMATION_PAUSE:
                toggleAutomationPause();
                break;
            case RESET_WINDOW_LAYOUT:
                activeScreen = activeModernScreen(mc);
                if (activeScreen != null) {
                    activeScreen.resetWindowLayout();
                }
                break;
            case INCREASE_MODERN_UI_SCALE:
                GuiModernMainScreen.adjustModernUiScale(mc, 1);
                break;
            case DECREASE_MODERN_UI_SCALE:
                GuiModernMainScreen.adjustModernUiScale(mc, -1);
                break;
            case RESET_MODERN_UI_SCALE:
                GuiModernMainScreen.resetModernUiScale(mc);
                break;
            case DETACH_MAIN_WINDOW:
                if (mc.currentScreen instanceof GuiModernMainScreen
                        && !DetachedSwingWindowManager.isDetached()) {
                    DetachedSwingWindowManager.INSTANCE.detach((GuiModernMainScreen) mc.currentScreen);
                }
                break;
            case SELECT_TAB_SLOT_1: selectTabSlot(mc, 1); break;
            case SELECT_TAB_SLOT_2: selectTabSlot(mc, 2); break;
            case SELECT_TAB_SLOT_3: selectTabSlot(mc, 3); break;
            case SELECT_TAB_SLOT_4: selectTabSlot(mc, 4); break;
            case SELECT_TAB_SLOT_5: selectTabSlot(mc, 5); break;
            case SELECT_TAB_SLOT_6: selectTabSlot(mc, 6); break;
            case SELECT_TAB_SLOT_7: selectTabSlot(mc, 7); break;
            case SELECT_TAB_SLOT_8: selectTabSlot(mc, 8); break;
            case SELECT_TAB_SLOT_9: selectTabSlot(mc, 9); break;
            case RUN_FAVORITE_SEQUENCE_1: executeFavoriteSequence(mc, 0); break;
            case RUN_FAVORITE_SEQUENCE_2: executeFavoriteSequence(mc, 1); break;
            case RUN_FAVORITE_SEQUENCE_3: executeFavoriteSequence(mc, 2); break;
            case RUN_FAVORITE_SEQUENCE_4: executeFavoriteSequence(mc, 3); break;
            case RUN_FAVORITE_SEQUENCE_5: executeFavoriteSequence(mc, 4); break;
            case RUN_FAVORITE_SEQUENCE_6: executeFavoriteSequence(mc, 5); break;
            case RUN_FAVORITE_SEQUENCE_7: executeFavoriteSequence(mc, 6); break;
            case RUN_FAVORITE_SEQUENCE_8: executeFavoriteSequence(mc, 7); break;
            case RUN_FAVORITE_SEQUENCE_9: executeFavoriteSequence(mc, 8); break;
            case STOP_SEQUENCE:
                PathSequenceEventListener.instance.stopTracking();
                mc.player
                        .sendMessage(new TextComponentString(TextFormatting.RED + I18n.format("msg.keybind.stop_all")));
                break;
            case SET_LOOP_COUNT:
                mc.addScheduledTask(() -> GuiModernMainScreen.openSettingsTab(mc, "setloop"));
                break;
            case TOGGLE_MOUSE_DETACH:
                ModConfig.isMouseDetached = !ModConfig.isMouseDetached;
                String mouseStatus = ModConfig.isMouseDetached ? I18n.format("gui.inventory.mouse.detached")
                        : I18n.format("gui.inventory.mouse.reattached");
                mc.player.sendMessage(
                        new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.keybind.mouse_status", mouseStatus)));
                if (!ModConfig.isMouseDetached && mc.currentScreen == null) {
                    mc.mouseHelper.grabMouseCursor();
                }
                break;
            case OPEN_INVENTORY_VIEWER:
                mc.addScheduledTask(() -> GuiModernMainScreen.openSettingsTab(mc, "player_equipment_viewer"));
                mc.player.sendMessage(
                        new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.keybind.open_inventory_viewer")));
                break;
            case TOGGLE_AUTO_EAT:
                AutoEatHandler.autoEatEnabled = !AutoEatHandler.autoEatEnabled;
                AutoEatHandler.saveAutoEatConfig();
                mc.player.sendMessage(new TextComponentString(I18n.format("msg.keybind.common_toggle_status",
                        I18n.format("keybind.action.toggle_auto_eat.name"),
                        AutoEatHandler.autoEatEnabled ? I18n.format("gui.common.enabled")
                                : I18n.format("gui.common.disabled"))));
                break;
            case TOGGLE_AUTO_FISHING:
                AutoFishingHandler.INSTANCE.toggleEnabled();
                break;
            case TOGGLE_FLY:
                FlyHandler.INSTANCE.toggleEnabled();
                break;
            case TOGGLE_KILL_AURA:
                KillAuraHandler.INSTANCE.toggleEnabled();
                break;
            case TOGGLE_AUTO_PICKUP:
                AutoPickupHandler.globalEnabled = !AutoPickupHandler.globalEnabled;
                AutoPickupHandler.saveConfig();
                mc.player.sendMessage(new TextComponentString(I18n.format("msg.keybind.common_toggle_status",
                        I18n.format("keybind.action.toggle_auto_pickup.name"),
                        AutoPickupHandler.globalEnabled ? I18n.format("gui.common.enabled")
                                : I18n.format("gui.common.disabled"))));
                break;
            case TOGGLE_PACKET_CAPTURE:
                PacketCaptureHandler.isCapturing = !PacketCaptureHandler.isCapturing;
                mc.player.sendMessage(new TextComponentString(I18n.format("msg.keybind.common_toggle_status",
                        I18n.format("keybind.action.toggle_packet_capture.name"),
                        PacketCaptureHandler.isCapturing ? I18n.format("gui.common.enabled")
                                : I18n.format("gui.common.disabled"))));
                break;
            case EXECUTE_SPECIFIC_PACKET_SEQUENCE:
                Keybind keybind = keybinds.get(action);
                if (keybind != null && keybind.getParameter() != null && !keybind.getParameter().isEmpty()) {
                    String sequenceName = keybind.getParameter();
                    PacketSequence seq = PacketSequenceManager.loadSequence(sequenceName);
                    if (seq != null) {
                        PacketSendSupport.sendSequence(mc, seq);
                    } else {
                        mc.player.sendMessage(new TextComponentString(
                                TextFormatting.RED
                                        + I18n.format("msg.keybind.packet_sequence_not_found", sequenceName)));
                    }
                } else {
                    mc.player.sendMessage(new TextComponentString(
                            TextFormatting.YELLOW + I18n.format("msg.keybind.packet_sequence_not_bound")));
                }
                break;
            default:
                // Feature toggles are handled above by toggleManagedFeature; all other unhandled
                // enum values intentionally no-op here to keep the switch exhaustive for IDEs.
                break;
        }
    }

    public static void executePathSequenceByName(String sequenceName) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || sequenceName == null || sequenceName.trim().isEmpty()) {
            return;
        }
        if (!PathSequenceManager.hasSequence(sequenceName)) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED
                    + I18n.format("msg.keybind.path_sequence_not_found", sequenceName)));
            return;
        }
        PathSequenceManager.runPathSequence(sequenceName);
    }

    private static void selectTabSlot(Minecraft mc, int slot) {
        GuiModernMainScreen screen = activeModernScreen(mc);
        if (screen != null) {
            screen.selectTabSlot(slot);
        }
    }

    private static GuiModernMainScreen activeModernScreen(Minecraft mc) {
        if (mc != null && mc.currentScreen instanceof GuiModernMainScreen) {
            return (GuiModernMainScreen) mc.currentScreen;
        }
        return DetachedSwingWindowManager.isDetached() ? DetachedSwingWindowManager.getActiveScreen() : null;
    }

    private static void executeRankedSequence(Minecraft mc, List<String> names, int index) {
        if (names == null || index < 0 || index >= names.size()) return;
        String name = names.get(index);
        if (PathSequenceManager.hasSequence(name)) {
            PathSequenceManager.runPathSequence(name);
        }
    }

    private static void executeFavoriteSequence(Minecraft mc, int index) {
        List<String> favorites = MainUiLayoutManager.getFavoriteSequences();
        if (favorites.isEmpty()) {
            favorites = MainUiLayoutManager.getSequencesByOpenCount();
        }
        executeRankedSequence(mc, favorites, index);
    }

    private static void toggleAutomationPause() {
        boolean hasActive = PathSequenceEventListener.instance.isTracking();
        hasActive |= PathSequenceEventListener.hasActiveBackgroundSequence();
        boolean hasUnpaused = hasActive && !PathSequenceEventListener.instance.isPaused();
        hasUnpaused |= PathSequenceEventListener.hasUnpausedBackgroundSequence();
        if (!hasActive) return;
        if (hasUnpaused) {
            PathSequenceEventListener.pauseForegroundSequenceByAction();
            PathSequenceEventListener.pauseBackgroundSequencesByAction();
        } else {
            PathSequenceEventListener.resumeForegroundSequenceByAction();
            PathSequenceEventListener.resumeBackgroundSequencesByAction();
        }
    }

    private static void toggleManagedFeature(Minecraft mc, BindableAction action) {
        if (mc == null || mc.player == null || action == null) {
            return;
        }

        String featureId = action.getFeatureId();
        if (featureId == null || featureId.trim().isEmpty()) {
            return;
        }

        boolean targetEnabled;
        switch (action.getFeatureGroup()) {
            case MOVEMENT:
                if ("speed".equalsIgnoreCase(featureId)) {
                    SpeedHandler.INSTANCE.toggleEnabled();
                    return;
                }
                if (!MovementFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !MovementFeatureManager.isEnabled(featureId);
                MovementFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            case BLOCK:
                if (!BlockFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !BlockFeatureManager.isEnabled(featureId);
                BlockFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            case ITEM:
                if (!ItemFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !ItemFeatureManager.isEnabled(featureId);
                ItemFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            case RENDER:
                if (!RenderFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !RenderFeatureManager.isEnabled(featureId);
                RenderFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            case WORLD:
                if (!WorldFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !WorldFeatureManager.isEnabled(featureId);
                WorldFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            case MISC:
                if (!MiscFeatureManager.isManagedFeature(featureId)) {
                    sendMissingManagedFeatureMessage(mc, featureId);
                    return;
                }
                targetEnabled = !MiscFeatureManager.isEnabled(featureId);
                MiscFeatureManager.setEnabled(featureId, targetEnabled);
                break;
            default:
                return;
        }

        mc.player.sendMessage(new TextComponentString(I18n.format("msg.keybind.common_toggle_status",
                action.getDisplayName(),
                targetEnabled ? I18n.format("gui.common.enabled") : I18n.format("gui.common.disabled"))));
    }

    private static void sendMissingManagedFeatureMessage(Minecraft mc, String featureId) {
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.sendMessage(new TextComponentString(
                TextFormatting.RED + "[快捷键] 未找到其他功能: " + featureId));
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("keybinds_v2.json").toFile();
    }

    public static void loadConfig() {
        keybinds.clear();
        pathSequenceKeybinds.clear();
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            ensureDefaultKeybinds();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            JsonElement rootEl = new JsonParser().parse(reader);
            if (rootEl != null && rootEl.isJsonObject()
                    && (rootEl.getAsJsonObject().has("actions") || rootEl.getAsJsonObject().has("pathSequences"))) {
                KeybindConfig config = GSON.fromJson(rootEl, KeybindConfig.class);
                if (config != null && config.actions != null) {
                    for (Map.Entry<String, Keybind> entry : config.actions.entrySet()) {
                        loadActionBinding(entry.getKey(), entry.getValue());
                    }
                }
                if (config != null && config.pathSequences != null) {
                    pathSequenceKeybinds.putAll(config.pathSequences);
                }
            } else {
                Type legacyType = new TypeToken<Map<String, Keybind>>() {
                }.getType();
                Map<String, Keybind> loadedMap = GSON.fromJson(rootEl, legacyType);
                if (loadedMap != null) {
                    for (Map.Entry<String, Keybind> entry : loadedMap.entrySet()) {
                        loadActionBinding(entry.getKey(), entry.getValue());
                    }
                }
            }
            syncPathSequenceKeybinds();
            ensureDefaultKeybinds();
            zszlScriptMod.LOGGER.info("Loaded {} custom keybind(s)", keybinds.size());
        } catch (Exception e) {
            ensureDefaultKeybinds();
            zszlScriptMod.LOGGER.error("Failed to load keybind config", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
                syncPathSequenceKeybinds();
                KeybindConfig config = new KeybindConfig();
                keybinds.forEach((action, keybind) -> config.actions.put(action.name(), keybind));
                config.pathSequences.putAll(pathSequenceKeybinds);
                GSON.toJson(config, writer);
                zszlScriptMod.LOGGER.info("Keybind config saved");
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to save keybind config", e);
        }
    }

    public static void syncPathSequenceKeybinds() {
        Set<String> names = PathSequenceManager.getAllVisibleSequences().stream()
                .map(seq -> seq.getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        pathSequenceKeybinds.entrySet().removeIf(e -> !names.contains(e.getKey()));
        for (String name : names) {
            pathSequenceKeybinds.putIfAbsent(name, new Keybind());
        }
    }

    private static void loadActionBinding(String name, Keybind value) {
        if (name == null) return;
        BindableAction action;
        try {
            if ("CLOSE_MAIN_WINDOW".equals(name)) {
                action = BindableAction.CLOSE_ACTIVE_TAB;
            } else if ("RESTORE_MAIN_WINDOW".equals(name)) {
                action = BindableAction.RESTORE_CLOSED_TAB;
            } else if ("OPEN_COMMANDS_ONLY".equals(name)) {
                action = BindableAction.OPEN_COMMAND_PALETTE;
            } else {
                action = BindableAction.valueOf(name);
            }
        } catch (IllegalArgumentException exception) {
            zszlScriptMod.LOGGER.warn("Unknown action found in keybind config: {}", name);
            return;
        }
        if (value == null) return;
        Keybind existing = keybinds.get(action);
        if (existing == null) {
            keybinds.put(action, copyKeybind(value));
        } else {
            for (KeyCombination combination : value.getCombinations()) {
                existing.addCombination(combination.getKeyCode(), combination.getModifiers());
            }
            if (existing.getParameter() == null && value.getParameter() != null) {
                existing.setParameter(value.getParameter());
            }
        }
    }

    private static Keybind copyKeybind(Keybind source) {
        Keybind copy = new Keybind();
        if (source != null) {
            copy.setCombinations(source.getCombinations());
            copy.setParameter(source.getParameter());
        }
        return copy;
    }

    public static boolean matchesKeybind(BindableAction action, int keyCode, Set<Integer> modifiers) {
        if (action == null) {
            return false;
        }
        Keybind keybind = keybinds.get(action);
        return keybind != null && keybind.matches(keyCode, modifiers);
    }

    private static void ensureDefaultKeybinds() {
        putDefault(BindableAction.OPEN_RECENT_COMMAND, Keyboard.KEY_K, Keyboard.KEY_LCONTROL);
        putDefault(BindableAction.FOCUS_GLOBAL_SEARCH, Keyboard.KEY_F, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        putDefault(BindableAction.OPEN_RECENT_SEQUENCE, Keyboard.KEY_E, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        putDefault(BindableAction.SAVE_CONFIGURATIONS, Keyboard.KEY_S, Keyboard.KEY_LCONTROL);
        putDefault(BindableAction.EMERGENCY_STOP, Keyboard.KEY_BACK, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        putDefault(BindableAction.NEXT_TAB, Keyboard.KEY_TAB, Keyboard.KEY_LCONTROL);
        putDefault(BindableAction.PREVIOUS_TAB, Keyboard.KEY_TAB, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        putDefault(BindableAction.TOGGLE_AUTOMATION_PAUSE, Keyboard.KEY_F6);
        putDefault(BindableAction.RESET_WINDOW_LAYOUT, Keyboard.KEY_R, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        putDefaultWithAdditionalCombination(BindableAction.INCREASE_MODERN_UI_SCALE, Keyboard.KEY_EQUALS,
                new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL)),
                new KeyCombination(Keyboard.KEY_ADD, new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL))),
                new KeyCombination(Keyboard.KEY_EQUALS,
                        new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT))));
        putDefaultWithAdditionalCombination(BindableAction.DECREASE_MODERN_UI_SCALE, Keyboard.KEY_MINUS,
                new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL)),
                new KeyCombination(Keyboard.KEY_SUBTRACT, new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL))));
        putDefaultWithAdditionalCombination(BindableAction.RESET_MODERN_UI_SCALE, Keyboard.KEY_0,
                new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL)),
                new KeyCombination(Keyboard.KEY_NUMPAD0, new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL))));
        putDefault(BindableAction.DETACH_MAIN_WINDOW, Keyboard.KEY_F7);
        for (int i = 1; i <= 9; i++) {
            putDefault(BindableAction.valueOf("SELECT_TAB_SLOT_" + i), Keyboard.KEY_1 + i - 1, Keyboard.KEY_LMENU);
            putDefault(BindableAction.valueOf("RUN_FAVORITE_SEQUENCE_" + i), Keyboard.KEY_1 + i - 1,
                    Keyboard.KEY_LCONTROL);
        }
        if (keybinds.get(BindableAction.OPEN_COMMAND_PALETTE) == null) {
            Keybind palette = new Keybind(DEFAULT_COMMAND_PALETTE_KEY, Collections.<Integer>emptySet());
            palette.addCombination(Keyboard.KEY_P,
                    new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT)));
            keybinds.put(BindableAction.OPEN_COMMAND_PALETTE, palette);
        } else {
            Keybind palette = keybinds.get(BindableAction.OPEN_COMMAND_PALETTE);
            if (palette.getCombinations().size() == 1
                    && palette.matches(DEFAULT_COMMAND_PALETTE_KEY, Collections.<Integer>emptySet())) {
                palette.addCombination(Keyboard.KEY_P,
                        new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT)));
            }
        }
        putDefault(BindableAction.CLOSE_ACTIVE_TAB, Keyboard.KEY_W, Keyboard.KEY_LCONTROL);
        putDefault(BindableAction.RESTORE_CLOSED_TAB, Keyboard.KEY_T, Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT);
        migrateLegacyShortcutDefaults();
    }

    private static void migrateLegacyShortcutDefaults() {
        Keybind emergency = keybinds.get(BindableAction.EMERGENCY_STOP);
        Set<Integer> emergencyModifiers = new HashSet<>(Arrays.asList(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT));
        if (emergency != null && emergency.matches(Keyboard.KEY_ESCAPE, emergencyModifiers)) {
            emergency.removeCombination(Keyboard.KEY_ESCAPE, emergencyModifiers);
            emergency.addCombination(Keyboard.KEY_BACK, emergencyModifiers);
        }
        Keybind mouseDetach = keybinds.get(BindableAction.TOGGLE_MOUSE_DETACH);
        if (mouseDetach != null) {
            mouseDetach.removeCombination(Keyboard.KEY_F7, Collections.<Integer>emptySet());
        }
    }

    private static void putDefault(BindableAction action, int keyCode, Integer... modifiers) {
        if (keybinds.get(action) == null) {
            keybinds.put(action, new Keybind(keyCode, new HashSet<>(Arrays.asList(modifiers))));
        }
    }

    private static void putDefaultWithAdditionalCombination(BindableAction action, int keyCode,
            Set<Integer> modifiers, KeyCombination... additional) {
        putDefault(action, keyCode, modifiers.toArray(new Integer[modifiers.size()]));
        Keybind keybind = keybinds.get(action);
        if (keybind == null || additional == null) {
            return;
        }
        for (KeyCombination combination : additional) {
            if (combination != null) {
                keybind.addCombination(combination.getKeyCode(), combination.getModifiers());
            }
        }
    }
}

