package com.zszl.zszlScriptMod.system;

import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimulatedKeyInputManager {
    public static final SimulatedKeyInputManager INSTANCE = new SimulatedKeyInputManager();
    private static final int PRESS_HOLD_TICKS = 10;

    private static final ThreadLocal<SyntheticKeyboardEvent> CURRENT_SYNTHETIC_EVENT = new ThreadLocal<>();
    private static final Field LWJGL_CURRENT_EVENT = findKeyboardField("current_event");
    private static final Field LWJGL_KEY_DOWN_BUFFER = findKeyboardField("keyDownBuffer");
    private static final Field LWJGL_EVENT_KEY = findKeyEventField("key");
    private static final Field LWJGL_EVENT_CHARACTER = findKeyEventField("character");
    private static final Field LWJGL_EVENT_STATE = findKeyEventField("state");
    private static final Field LWJGL_EVENT_NANOS = findKeyEventField("nanos");
    private static final Field LWJGL_EVENT_REPEAT = findKeyEventField("repeat");

    /** An event exposed through LWJGL Keyboard.next/getEvent* for this client instance. */
    public static final class SyntheticKeyboardEvent {
        private final int keyCode;
        private final char character;
        private final boolean keyState;
        private final boolean controlDown;

        private SyntheticKeyboardEvent(int keyCode, char character, boolean keyState) {
            this(keyCode, character, keyState, false);
        }

        private SyntheticKeyboardEvent(int keyCode, char character, boolean keyState, boolean controlDown) {
            this.keyCode = keyCode;
            this.character = character;
            this.keyState = keyState;
            this.controlDown = controlDown;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public char getCharacter() {
            return character;
        }

        public boolean isKeyState() {
            return keyState;
        }

        public boolean isControlDown() {
            return controlDown;
        }
    }

    public static final class SimulatedPressEvent {
        private final int keyCode;
        private final Set<Integer> modifiers;

        private SimulatedPressEvent(int keyCode, Set<Integer> modifiers) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public Set<Integer> getModifiers() {
            return modifiers;
        }
    }

    private final Set<Integer> heldKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> managedKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Map<Integer, Integer> pendingReleaseTicks = new ConcurrentHashMap<>();
    private final Queue<SimulatedPressEvent> pendingPressEvents = new ConcurrentLinkedQueue<>();
    private final Set<Integer> syntheticActionKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> syntheticTapKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> foregroundActionKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> backgroundActionKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> foregroundTapKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> backgroundTapKeys = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Queue<SyntheticKeyboardEvent> syntheticKeyboardEvents = new ConcurrentLinkedQueue<>();

    private SimulatedKeyInputManager() {
    }

    public static void simulateKey(String keyName, String state) {
        simulateKeyInternal(keyName, state, false);
    }

    /** Simulates a path-action key through this JVM's LWJGL keyboard pipeline. */
    public static void simulateActionKey(String keyName, String state) {
        simulateActionKey(keyName, state, PRESS_HOLD_TICKS, false);
    }

    public static void simulateActionKey(String keyName, String state, int pressDurationTicks, boolean background) {
        int keyCode = ModUtils.resolveLwjglKeyCode(keyName);
        if (keyCode == Keyboard.KEY_NONE) {
            zszlScriptMod.LOGGER.warn("忽略未知模拟按键: {}", keyName);
            return;
        }
        String normalizedState = ModUtils.normalizeSimulatedKeyState(state);
        int duration = Math.max(1, pressDurationTicks);
        runOnClientThread(() -> INSTANCE.applySyntheticActionState(keyCode, normalizedState, duration, background));
    }

    public static void releasePathActionKeys(boolean background) {
        runOnClientThread(() -> INSTANCE.releasePathActionKeysInternal(background));
    }

    /** Dispatches one key press while Ctrl is held for the same synthetic event. */
    public static void simulateControlShortcut(String keyName) {
        int keyCode = ModUtils.resolveLwjglKeyCode(keyName);
        if (keyCode == Keyboard.KEY_NONE) {
            zszlScriptMod.LOGGER.warn("忽略未知 Ctrl 组合键: {}", keyName);
            return;
        }
        runOnClientThread(() -> {
            INSTANCE.applyStateChange(keyCode, "Press", false);
            INSTANCE.dispatchSyntheticKeyboardEvent(
                    new SyntheticKeyboardEvent(keyCode, INSTANCE.getEventCharacter(keyCode), true, true));
        });
    }

    private static void simulateKeyInternal(String keyName, String state, boolean forwardToWindow) {
        int keyCode = ModUtils.resolveLwjglKeyCode(keyName);
        if (keyCode == Keyboard.KEY_NONE) {
            zszlScriptMod.LOGGER.warn("忽略未知模拟按键: {}", keyName);
            return;
        }

        String normalizedState = ModUtils.normalizeSimulatedKeyState(state);
        runOnClientThread(() -> {
            if (forwardToWindow) {
                INSTANCE.applySyntheticActionState(keyCode, normalizedState, PRESS_HOLD_TICKS, false);
                return;
            }
            INSTANCE.applyStateChange(keyCode, normalizedState);
        });
    }

    public static void simulateKeyCode(int keyCode, String state) {
        if (keyCode == Keyboard.KEY_NONE) {
            return;
        }

        String normalizedState = ModUtils.normalizeSimulatedKeyState(state);
        runOnClientThread(() -> INSTANCE.applyStateChange(keyCode, normalizedState));
    }

    /**
     * Keeps an AWT/Swing key state visible to existing Keyboard.isKeyDown checks
     * without emitting a global keybind press.
     */
    public static void setExternalKeyState(int keyCode, boolean down) {
        if (keyCode == Keyboard.KEY_NONE) {
            return;
        }
        runOnClientThread(() -> INSTANCE.applyStateChange(keyCode, down ? "Down" : "Up", false));
    }

    /** Runs an action while Shift is held by this client's synthetic keyboard state. */
    public static void runWithSimulatedShift(Runnable action) {
        if (action == null) {
            return;
        }
        runOnClientThread(() -> {
            boolean shiftAlreadyHeld = isEitherKeyDown(Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT);
            if (!shiftAlreadyHeld) {
                INSTANCE.applyStateChange(Keyboard.KEY_LSHIFT, "Down", false);
            }
            try {
                action.run();
            } finally {
                if (!shiftAlreadyHeld) {
                    INSTANCE.applyStateChange(Keyboard.KEY_LSHIFT, "Up", false);
                }
            }
        });
    }

    public static boolean isKeyDown(int keyCode) {
        return Keyboard.isKeyDown(keyCode) || INSTANCE.isSimulatedKeyDown(keyCode);
    }

    public static boolean isEitherKeyDown(int primaryKeyCode, int secondaryKeyCode) {
        return isKeyDown(primaryKeyCode) || isKeyDown(secondaryKeyCode);
    }

    /** Called by the Keyboard mixin at the start of Keyboard.next(). */
    public static boolean beginSyntheticKeyboardEvent() {
        CURRENT_SYNTHETIC_EVENT.remove();
        SyntheticKeyboardEvent event = INSTANCE.syntheticKeyboardEvents.poll();
        if (event == null) {
            return false;
        }
        CURRENT_SYNTHETIC_EVENT.set(event);
        return true;
    }

    /** Called by Keyboard getter mixins while runTickKeyboard handles an event. */
    public static SyntheticKeyboardEvent getCurrentSyntheticKeyboardEvent() {
        return CURRENT_SYNTHETIC_EVENT.get();
    }

    /**
     * Returns a simulated state override for Keyboard.isKeyDown, or null when
     * the physical LWJGL state should be used.
     */
    public static Boolean getSyntheticKeyDownOverride(int keyCode) {
        SyntheticKeyboardEvent event = CURRENT_SYNTHETIC_EVENT.get();
        if (event != null && event.getKeyCode() == keyCode) {
            return event.isKeyState();
        }
        if (event != null && event.isControlDown()
                && (keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL)) {
            return Boolean.TRUE;
        }
        return INSTANCE.isSimulatedKeyDown(keyCode) ? Boolean.TRUE : null;
    }

    public SimulatedPressEvent pollPressedKey() {
        return pendingPressEvents.poll();
    }

    public void reset() {
        runOnClientThread(this::resetInternal);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        syncManagedKeyStates();
        advancePendingReleases();
    }

    private void applyStateChange(int keyCode, String state) {
        applyStateChange(keyCode, state, true);
    }

    private void applyStateChange(int keyCode, String state, boolean emitPressEvent) {
        managedKeys.add(keyCode);
        switch (state) {
            case "Down":
                pressKey(keyCode, false, emitPressEvent);
                break;
            case "Up":
                releaseKey(keyCode);
                break;
            default:
                tapKey(keyCode, emitPressEvent);
                break;
        }
        zszlScriptMod.LOGGER.info("成功模拟实例内按键: {} ({})", Keyboard.getKeyName(keyCode), state);
    }

    private void applySyntheticActionState(int keyCode, String state, int pressDurationTicks, boolean background) {
        managedKeys.add(keyCode);
        Set<Integer> scopedActionKeys = background ? backgroundActionKeys : foregroundActionKeys;
        Set<Integer> scopedTapKeys = background ? backgroundTapKeys : foregroundTapKeys;
        switch (state) {
            case "Down":
                scopedActionKeys.add(keyCode);
                scopedTapKeys.remove(keyCode);
                syntheticActionKeys.add(keyCode);
                syntheticTapKeys.remove(keyCode);
                applyStateChange(keyCode, "Down", false);
                dispatchSyntheticKeyboardEvent(new SyntheticKeyboardEvent(keyCode, getEventCharacter(keyCode), true));
                break;
            case "Up":
                scopedActionKeys.remove(keyCode);
                scopedTapKeys.remove(keyCode);
                refreshSyntheticKeyState(keyCode, true);
                break;
            default:
                scopedActionKeys.add(keyCode);
                scopedTapKeys.add(keyCode);
                syntheticActionKeys.add(keyCode);
                syntheticTapKeys.add(keyCode);
                tapKey(keyCode, false, pressDurationTicks);
                dispatchSyntheticKeyboardEvent(new SyntheticKeyboardEvent(keyCode, getEventCharacter(keyCode), true));
                break;
        }
        zszlScriptMod.LOGGER.info("成功模拟键盘事件: {} ({})", Keyboard.getKeyName(keyCode), state);
    }

    private void pressKey(int keyCode, boolean syntheticTap, boolean emitPressEvent) {
        pressKey(keyCode, syntheticTap, emitPressEvent, PRESS_HOLD_TICKS);
    }

    private void pressKey(int keyCode, boolean syntheticTap, boolean emitPressEvent, int holdTicks) {
        boolean wasHeld = heldKeys.contains(keyCode);
        heldKeys.add(keyCode);
        if (syntheticTap) {
            pendingReleaseTicks.put(keyCode, Math.max(1, holdTicks));
        } else {
            pendingReleaseTicks.remove(keyCode);
        }

        syncKeyBindingState(keyCode);
        if (emitPressEvent && (!wasHeld || syntheticTap)) {
            KeyBinding.onTick(keyCode);
            pendingPressEvents.add(new SimulatedPressEvent(keyCode, snapshotActiveModifiers()));
        }
    }

    private void tapKey(int keyCode, boolean emitPressEvent) {
        tapKey(keyCode, emitPressEvent, PRESS_HOLD_TICKS);
    }

    private void tapKey(int keyCode, boolean emitPressEvent, int holdTicks) {
        if (heldKeys.contains(keyCode) && !pendingReleaseTicks.containsKey(keyCode)) {
            syncKeyBindingState(keyCode);
            if (emitPressEvent) {
                KeyBinding.onTick(keyCode);
                pendingPressEvents.add(new SimulatedPressEvent(keyCode, snapshotActiveModifiers()));
            }
            return;
        }
        pressKey(keyCode, true, emitPressEvent, holdTicks);
    }

    private void releaseKey(int keyCode) {
        heldKeys.remove(keyCode);
        pendingReleaseTicks.remove(keyCode);
        syncKeyBindingState(keyCode);
    }

    private void syncManagedKeyStates() {
        if (managedKeys.isEmpty()) {
            return;
        }

        for (Integer keyCode : new HashSet<>(managedKeys)) {
            syncKeyBindingState(keyCode);
        }
    }

    private void advancePendingReleases() {
        if (pendingReleaseTicks.isEmpty()) {
            return;
        }

        Set<Integer> releaseKeys = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : pendingReleaseTicks.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                releaseKeys.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }

        for (Integer keyCode : releaseKeys) {
            boolean foregroundTap = foregroundTapKeys.remove(keyCode);
            boolean backgroundTap = backgroundTapKeys.remove(keyCode);
            if (foregroundTap) foregroundActionKeys.remove(keyCode);
            if (backgroundTap) backgroundActionKeys.remove(keyCode);
            refreshSyntheticKeyState(keyCode, true);
        }
    }

    private void releasePathActionKeysInternal(boolean background) {
        Set<Integer> scopedActionKeys = background ? backgroundActionKeys : foregroundActionKeys;
        Set<Integer> scopedTapKeys = background ? backgroundTapKeys : foregroundTapKeys;
        Set<Integer> keys = new HashSet<>(scopedActionKeys);
        keys.addAll(scopedTapKeys);
        scopedActionKeys.clear();
        scopedTapKeys.clear();
        for (Integer keyCode : keys) {
            refreshSyntheticKeyState(keyCode, true);
        }
    }

    private void refreshSyntheticKeyState(int keyCode, boolean dispatchRelease) {
        boolean stillForeground = foregroundActionKeys.contains(keyCode);
        boolean stillBackground = backgroundActionKeys.contains(keyCode);
        boolean stillHeld = stillForeground || stillBackground;
        if (stillHeld) {
            syntheticActionKeys.add(keyCode);
            if (!foregroundTapKeys.contains(keyCode) && !backgroundTapKeys.contains(keyCode)) {
                syntheticTapKeys.remove(keyCode);
                pendingReleaseTicks.remove(keyCode);
            }
            syncKeyBindingState(keyCode);
            return;
        }
        syntheticActionKeys.remove(keyCode);
        syntheticTapKeys.remove(keyCode);
        pendingReleaseTicks.remove(keyCode);
        releaseKey(keyCode);
        if (dispatchRelease) {
            dispatchSyntheticKeyboardEvent(new SyntheticKeyboardEvent(keyCode, getEventCharacter(keyCode), false));
        }
    }

    private void resetInternal() {
        Set<Integer> keysToReset = new HashSet<>(managedKeys);
        keysToReset.addAll(heldKeys);
        keysToReset.addAll(pendingReleaseTicks.keySet());

        pendingPressEvents.clear();
        syntheticKeyboardEvents.clear();
        CURRENT_SYNTHETIC_EVENT.remove();
        syntheticActionKeys.clear();
        syntheticTapKeys.clear();
        foregroundActionKeys.clear();
        backgroundActionKeys.clear();
        foregroundTapKeys.clear();
        backgroundTapKeys.clear();
        heldKeys.clear();
        pendingReleaseTicks.clear();
        managedKeys.clear();

        for (Integer keyCode : keysToReset) {
            KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
        }
    }

    private void syncKeyBindingState(int keyCode) {
        KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode) || isSimulatedKeyDown(keyCode));
    }

    /**
     * Runs the same client-side stages as Minecraft.runTickKeyboard for one
     * event.  The thread-local makes Keyboard.getEvent* expose the synthetic
     * values to Forge listeners and custom GUI code during the dispatch.
     */
    private void dispatchSyntheticKeyboardEvent(SyntheticKeyboardEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || event == null) {
            return;
        }

        NativeKeyboardSnapshot nativeState = installNativeKeyboardState(event);
        CURRENT_SYNTHETIC_EVENT.set(event);
        try {
            mc.dispatchKeypresses();
            if (mc.currentScreen != null) {
                try {
                    mc.currentScreen.handleKeyboardInput();
                } catch (java.io.IOException e) {
                    zszlScriptMod.LOGGER.warn("处理模拟 GUI 键盘事件失败", e);
                }
            }

            KeyBinding.setKeyBindState(event.getKeyCode(), event.isKeyState());
            if (event.isKeyState()) {
                KeyBinding.onTick(event.getKeyCode());
            }
            FMLCommonHandler.instance().fireKeyInput();
        } finally {
            CURRENT_SYNTHETIC_EVENT.remove();
            restoreNativeKeyboardState(nativeState);
        }
    }

    private static NativeKeyboardSnapshot installNativeKeyboardState(SyntheticKeyboardEvent event) {
        if (LWJGL_CURRENT_EVENT == null || LWJGL_EVENT_KEY == null || LWJGL_EVENT_CHARACTER == null
                || LWJGL_EVENT_STATE == null || LWJGL_EVENT_NANOS == null || LWJGL_EVENT_REPEAT == null) {
            return null;
        }
        try {
            Object eventObject = LWJGL_CURRENT_EVENT.get(null);
            if (eventObject == null) {
                Class<?> eventClass = Class.forName("org.lwjgl.input.Keyboard$KeyEvent");
                Constructor<?> constructor = eventClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                eventObject = constructor.newInstance();
                LWJGL_CURRENT_EVENT.set(null, eventObject);
            }

            NativeKeyboardSnapshot snapshot = new NativeKeyboardSnapshot(eventObject,
                    LWJGL_EVENT_KEY.getInt(eventObject),
                    LWJGL_EVENT_CHARACTER.getInt(eventObject),
                    LWJGL_EVENT_STATE.getBoolean(eventObject),
                    LWJGL_EVENT_NANOS.getLong(eventObject),
                    LWJGL_EVENT_REPEAT.getBoolean(eventObject));

            LWJGL_EVENT_KEY.setInt(eventObject, event.getKeyCode());
            LWJGL_EVENT_CHARACTER.setInt(eventObject, event.getCharacter());
            LWJGL_EVENT_STATE.setBoolean(eventObject, event.isKeyState());
            LWJGL_EVENT_NANOS.setLong(eventObject, System.nanoTime());
            LWJGL_EVENT_REPEAT.setBoolean(eventObject, false);

            if (LWJGL_KEY_DOWN_BUFFER != null && event.getKeyCode() >= 0) {
                ByteBuffer keyDownBuffer = (ByteBuffer) LWJGL_KEY_DOWN_BUFFER.get(null);
                if (keyDownBuffer != null && event.getKeyCode() < keyDownBuffer.capacity()) {
                    snapshot.keyDownBuffer = keyDownBuffer;
                    snapshot.keyDownValue = keyDownBuffer.get(event.getKeyCode());
                    snapshot.keyDownKeyCode = event.getKeyCode();
                    keyDownBuffer.put(event.getKeyCode(), (byte) (event.isKeyState() ? 1 : 0));
                    if (event.isControlDown() && Keyboard.KEY_LCONTROL != event.getKeyCode()
                            && Keyboard.KEY_LCONTROL < keyDownBuffer.capacity()) {
                        snapshot.controlKeyDownValue = keyDownBuffer.get(Keyboard.KEY_LCONTROL);
                        snapshot.restoreControlKey = true;
                        keyDownBuffer.put(Keyboard.KEY_LCONTROL, (byte) 1);
                    }
                }
            }
            return snapshot;
        } catch (Throwable error) {
            zszlScriptMod.LOGGER.debug("无法临时写入 LWJGL 键盘状态", error);
            return null;
        }
    }

    private static void restoreNativeKeyboardState(NativeKeyboardSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            LWJGL_EVENT_KEY.setInt(snapshot.eventObject, snapshot.key);
            LWJGL_EVENT_CHARACTER.setInt(snapshot.eventObject, snapshot.character);
            LWJGL_EVENT_STATE.setBoolean(snapshot.eventObject, snapshot.state);
            LWJGL_EVENT_NANOS.setLong(snapshot.eventObject, snapshot.nanos);
            LWJGL_EVENT_REPEAT.setBoolean(snapshot.eventObject, snapshot.repeat);
            if (snapshot.keyDownBuffer != null && snapshot.keyDownKeyCode >= 0) {
                snapshot.keyDownBuffer.put(snapshot.keyDownKeyCode, snapshot.keyDownValue);
                if (snapshot.restoreControlKey) {
                    snapshot.keyDownBuffer.put(Keyboard.KEY_LCONTROL, snapshot.controlKeyDownValue);
                }
            }
        } catch (Throwable error) {
            zszlScriptMod.LOGGER.debug("恢复 LWJGL 键盘状态失败", error);
        }
    }

    private static Field findKeyboardField(String name) {
        try {
            Field field = Keyboard.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findKeyEventField(String name) {
        try {
            Class<?> eventClass = Class.forName("org.lwjgl.input.Keyboard$KeyEvent");
            Field field = eventClass.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class NativeKeyboardSnapshot {
        private final Object eventObject;
        private final int key;
        private final int character;
        private final boolean state;
        private final long nanos;
        private final boolean repeat;
        private ByteBuffer keyDownBuffer;
        private byte keyDownValue;
        private int keyDownKeyCode = -1;
        private byte controlKeyDownValue;
        private boolean restoreControlKey;

        private NativeKeyboardSnapshot(Object eventObject, int key, int character, boolean state, long nanos,
                boolean repeat) {
            this.eventObject = eventObject;
            this.key = key;
            this.character = character;
            this.state = state;
            this.nanos = nanos;
            this.repeat = repeat;
        }

    }

    private char getEventCharacter(int keyCode) {
        if (keyCode == Keyboard.KEY_SPACE) {
            return ' ';
        }
        String keyName = Keyboard.getKeyName(keyCode);
        if (keyName == null || keyName.length() != 1) {
            return '\0';
        }
        char character = keyName.charAt(0);
        if (isKeyDown(Keyboard.KEY_LSHIFT) || isKeyDown(Keyboard.KEY_RSHIFT)) {
            return Character.toUpperCase(character);
        }
        return Character.toLowerCase(character);
    }

    private boolean isSimulatedKeyDown(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }
        if (heldKeys.contains(keyCode)) {
            return true;
        }

        switch (keyCode) {
            case Keyboard.KEY_LCONTROL:
                return heldKeys.contains(Keyboard.KEY_RCONTROL);
            case Keyboard.KEY_RCONTROL:
                return heldKeys.contains(Keyboard.KEY_LCONTROL);
            case Keyboard.KEY_LSHIFT:
                return heldKeys.contains(Keyboard.KEY_RSHIFT);
            case Keyboard.KEY_RSHIFT:
                return heldKeys.contains(Keyboard.KEY_LSHIFT);
            case Keyboard.KEY_LMENU:
                return heldKeys.contains(Keyboard.KEY_RMENU);
            case Keyboard.KEY_RMENU:
                return heldKeys.contains(Keyboard.KEY_LMENU);
            default:
                return false;
        }
    }

    private Set<Integer> snapshotActiveModifiers() {
        Set<Integer> modifiers = new HashSet<>();
        if (isKeyDown(Keyboard.KEY_LCONTROL) || isKeyDown(Keyboard.KEY_RCONTROL)) {
            modifiers.add(Keyboard.KEY_LCONTROL);
        }
        if (isKeyDown(Keyboard.KEY_LSHIFT) || isKeyDown(Keyboard.KEY_RSHIFT)) {
            modifiers.add(Keyboard.KEY_LSHIFT);
        }
        if (isKeyDown(Keyboard.KEY_LMENU) || isKeyDown(Keyboard.KEY_RMENU)) {
            modifiers.add(Keyboard.KEY_LMENU);
        }
        return modifiers;
    }

    private static void runOnClientThread(Runnable task) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (mc.isCallingFromMinecraftThread()) {
            task.run();
            return;
        }
        mc.addScheduledTask(task);
    }
}
