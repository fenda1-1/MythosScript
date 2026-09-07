package com.zszl.zszlScriptMod.system;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.gui.packet.InputTimelineManager;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.system.KeybindManager.Keybind;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Global keyboard listener.
 * Key triggers must remain active regardless of whether a GUI is open.
 */
public class GlobalKeybindListener {

    /** Dispatcher used by the Swing detached window, which has no LWJGL
     * InputEvent to feed through Forge's normal global listener. */
    private static final GlobalKeybindListener EXTERNAL_DISPATCHER = new GlobalKeybindListener();

    private final Map<Integer, Boolean> lastPhysicalKeyStates = new HashMap<>();

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        int keyCode = Keyboard.getEventKey();
        if (keyCode == Keyboard.KEY_NONE) {
            return;
        }

        InputTimelineManager.recordKeyPress(keyCode);
        this.lastPhysicalKeyStates.put(keyCode, Keyboard.isKeyDown(keyCode));
        if (!areShortcutsBlockedByTextInput() && !KeybindManager.isRecording()) {
            dispatchLegacyKeyTrigger(keyCode);
        }
        if (!KeybindManager.isRecording()) {
            handleTriggeredKey(keyCode, getActivePhysicalModifiers());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        SimulatedKeyInputManager.SimulatedPressEvent pressEvent;
        while ((pressEvent = SimulatedKeyInputManager.INSTANCE.pollPressedKey()) != null) {
            handleTriggeredKey(pressEvent.getKeyCode(), pressEvent.getModifiers());
        }

        pollPhysicalKeybindPresses();
    }

    private void handleTriggeredKey(int keyCode, Set<Integer> providedModifiers) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (KeybindManager.isRecording()) {
            return;
        }
        if (keyCode == zszlScriptMod.getGuiToggleKeyCode()) {
            return;
        }

        Set<Integer> currentModifiers = providedModifiers == null ? getActiveModifiers() : providedModifiers;
        if (areShortcutsBlockedByTextInput()) {
            if (mc.currentScreen instanceof GuiChat
                    || !KeybindManager.matchesKeybind(BindableAction.SAVE_CONFIGURATIONS, keyCode,
                            currentModifiers)) {
                return;
            }
            KeybindManager.executeAction(BindableAction.SAVE_CONFIGURATIONS);
            return;
        }

        for (Map.Entry<BindableAction, Keybind> entry : KeybindManager.keybinds.entrySet()) {
            BindableAction action = entry.getKey();
            boolean mainWindow = mc.currentScreen instanceof GuiModernMainScreen
                    || DetachedSwingWindowManager.isDetached();
            if (entry.getKey() == BindableAction.OPEN_COMMAND_PALETTE
                    && !mainWindow) {
                continue;
            }
            if ((action == BindableAction.OPEN_RECENT_COMMAND || action == BindableAction.FOCUS_GLOBAL_SEARCH)
                    && mc.currentScreen != null && !mainWindow) {
                continue;
            }
            if ((action == BindableAction.NEXT_TAB || action == BindableAction.PREVIOUS_TAB
                    || action == BindableAction.RESET_WINDOW_LAYOUT
                    || action.name().startsWith("SELECT_TAB_SLOT_")) && !mainWindow) {
                continue;
            }
            if (action == BindableAction.DETACH_MAIN_WINDOW
                    && (!(mc.currentScreen instanceof GuiModernMainScreen) || DetachedSwingWindowManager.isDetached())) {
                continue;
            }
            if (entry.getKey() == BindableAction.CLOSE_ACTIVE_TAB
                    && !mainWindow) {
                continue;
            }
            if (entry.getKey() == BindableAction.RESTORE_CLOSED_TAB
                    && !mainWindow) {
                continue;
            }
            Keybind keybind = entry.getValue();
            if (keybind == null) {
                continue;
            }
            if (keybind.matches(keyCode, currentModifiers)) {
                KeybindManager.executeAction(entry.getKey());
                return;
            }
        }

        for (Map.Entry<BindableDebugAction, Keybind> entry : DebugKeybindManager.keybinds.entrySet()) {
            Keybind keybind = entry.getValue();
            if (keybind != null && keybind.matches(keyCode, currentModifiers)) {
                DebugKeybindManager.executeAction(entry.getKey());
                return;
            }
        }

        for (Map.Entry<String, Keybind> entry : new ArrayList<>(KeybindManager.pathSequenceKeybinds.entrySet())) {
            Keybind keybind = entry.getValue();
            if (keybind == null) {
                continue;
            }
            if (keybind.matches(keyCode, currentModifiers)) {
                KeybindManager.executePathSequenceByName(entry.getKey());
                return;
            }
        }
    }

    /** Dispatches one key press originating from the detached Swing window. */
    public static void dispatchExternalKeyPress(int keyCode, Set<Integer> modifiers) {
        if (keyCode == Keyboard.KEY_NONE || KeybindManager.isRecording()) {
            return;
        }
        GlobalKeybindListener dispatcher = EXTERNAL_DISPATCHER;
        if (!dispatcher.areShortcutsBlockedByTextInput()) {
            dispatcher.dispatchLegacyKeyTrigger(keyCode);
        }
        dispatcher.handleTriggeredKey(keyCode, modifiers == null ? new HashSet<Integer>() : modifiers);
    }

    private void pollPhysicalKeybindPresses() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            this.lastPhysicalKeyStates.clear();
            return;
        }

        Set<Integer> watchedKeys = collectWatchedPhysicalKeys();
        if (watchedKeys.isEmpty()) {
            this.lastPhysicalKeyStates.clear();
            return;
        }

        Set<Integer> currentModifiers = getActivePhysicalModifiers();
        for (Integer keyCode : watchedKeys) {
            boolean downNow = Keyboard.isKeyDown(keyCode);
            boolean downBefore = this.lastPhysicalKeyStates.getOrDefault(keyCode, Boolean.FALSE);
            if (downNow && !downBefore) {
                if (!areShortcutsBlockedByTextInput() && !KeybindManager.isRecording()) {
                    dispatchLegacyKeyTrigger(keyCode);
                }
                handleTriggeredKey(keyCode, currentModifiers);
            }
            this.lastPhysicalKeyStates.put(keyCode, downNow);
        }
        this.lastPhysicalKeyStates.keySet().retainAll(watchedKeys);
    }

    private void dispatchLegacyKeyTrigger(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || keyCode == Keyboard.KEY_NONE) {
            return;
        }

        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("keyCode", keyCode);
        triggerData.addProperty("keyName", Keyboard.getKeyName(keyCode));
        triggerData.addProperty("guiOpen", mc.currentScreen != null);
        triggerData.addProperty("currentScreen",
                mc.currentScreen == null ? "" : mc.currentScreen.getClass().getName());
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_KEY_INPUT, triggerData);
    }

    private Set<Integer> collectWatchedPhysicalKeys() {
        Set<Integer> watchedKeys = new HashSet<>();
        for (Keybind keybind : KeybindManager.keybinds.values()) {
            addWatchedPhysicalKey(watchedKeys, keybind);
        }
        for (Keybind keybind : DebugKeybindManager.keybinds.values()) {
            addWatchedPhysicalKey(watchedKeys, keybind);
        }
        for (Keybind keybind : KeybindManager.pathSequenceKeybinds.values()) {
            addWatchedPhysicalKey(watchedKeys, keybind);
        }
        if (LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_KEY_INPUT)) {
            for (int keyCode = Keyboard.KEY_ESCAPE; keyCode < Keyboard.KEYBOARD_SIZE; keyCode++) {
                if (keyCode != Keyboard.KEY_NONE) {
                    watchedKeys.add(keyCode);
                }
            }
        }
        return watchedKeys;
    }

    private void addWatchedPhysicalKey(Set<Integer> watchedKeys, Keybind keybind) {
        if (keybind == null) {
            return;
        }
        for (KeybindManager.KeyCombination combination : keybind.getCombinations()) {
            if (combination.getKeyCode() > Keyboard.KEY_NONE) {
                watchedKeys.add(combination.getKeyCode());
            }
        }
    }

    private Set<Integer> getActiveModifiers() {
        Set<Integer> modifiers = new HashSet<>();
        if (SimulatedKeyInputManager.isEitherKeyDown(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (SimulatedKeyInputManager.isEitherKeyDown(Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (SimulatedKeyInputManager.isEitherKeyDown(Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return modifiers;
    }

    private Set<Integer> getActivePhysicalModifiers() {
        Set<Integer> modifiers = new HashSet<>();
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return modifiers;
    }

    private boolean isAnyTextFieldFocused() {
        Minecraft mc = Minecraft.getMinecraft();
        return GuiModernMainScreen.hasTextInputFocus(mc);
    }

    private boolean areShortcutsBlockedByTextInput() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.currentScreen instanceof GuiChat || isAnyTextFieldFocused();
    }
}
