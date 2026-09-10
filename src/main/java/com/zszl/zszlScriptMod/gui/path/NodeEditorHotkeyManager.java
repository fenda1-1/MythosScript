package com.zszl.zszlScriptMod.gui.path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.system.KeybindManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import org.lwjgl.input.Keyboard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class NodeEditorHotkeyManager {

    public enum Action {
        DELETE("删除节点/连线", key(Keyboard.KEY_DELETE)),
        SELECT_ALL("全选节点", ctrl(Keyboard.KEY_A)),
        CUT("剪切", ctrl(Keyboard.KEY_X)),
        COPY("复制节点", ctrl(Keyboard.KEY_C)),
        PASTE("粘贴节点", ctrl(Keyboard.KEY_V)),
        DUPLICATE("复制一份", ctrl(Keyboard.KEY_D)),
        UNDO("撤销", ctrl(Keyboard.KEY_Z)),
        REDO("重做", ctrl(Keyboard.KEY_Y)),
        NEW_WORKFLOW("新建工作流", ctrl(Keyboard.KEY_N)),
        RENAME_WORKFLOW("重命名工作流", key(Keyboard.KEY_F2)),
        FOCUS_SELECTED("聚焦选中节点", key(Keyboard.KEY_F)),
        FOCUS_START("返回 Start", key(Keyboard.KEY_HOME)),
        SAVE("保存", ctrl(Keyboard.KEY_S));

        private final String displayName;
        private final KeybindManager.Keybind defaultKeybind;

        Action(String displayName, KeybindManager.Keybind defaultKeybind) {
            this.displayName = displayName;
            this.defaultKeybind = defaultKeybind;
        }

        public String getDisplayName() {
            return displayName;
        }

        public KeybindManager.Keybind createDefaultKeybind() {
            return cloneKeybind(defaultKeybind);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Action, KeybindManager.Keybind> KEYBINDS = new EnumMap<>(Action.class);

    private NodeEditorHotkeyManager() {
    }

    public static void load() {
        KEYBINDS.clear();
        Path file = getConfigFile();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, KeybindManager.Keybind>>() {
                }.getType();
                Map<String, KeybindManager.Keybind> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    for (Map.Entry<String, KeybindManager.Keybind> entry : loaded.entrySet()) {
                        try {
                            Action action = Action.valueOf(entry.getKey());
                            KEYBINDS.put(action, normalize(entry.getValue()));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("Failed to load node editor hotkeys", e);
            }
        }
        for (Action action : Action.values()) {
            KEYBINDS.putIfAbsent(action, action.createDefaultKeybind());
        }
    }

    public static void save() {
        try {
            Path file = getConfigFile();
            Files.createDirectories(file.getParent());
            Map<String, KeybindManager.Keybind> toSave = new java.util.LinkedHashMap<>();
            for (Action action : Action.values()) {
                toSave.put(action.name(), normalize(KEYBINDS.get(action)));
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(toSave, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to save node editor hotkeys", e);
        }
    }

    public static KeybindManager.Keybind get(Action action) {
        if (KEYBINDS.isEmpty()) {
            load();
        }
        return KEYBINDS.computeIfAbsent(action, k -> k.createDefaultKeybind());
    }

    public static void set(Action action, KeybindManager.Keybind keybind) {
        KEYBINDS.put(action, normalize(keybind));
    }

    public static boolean matches(Action action, int keyCode) {
        KeybindManager.Keybind keybind = get(action);
        if (keybind == null) return false;
        for (KeybindManager.KeyCombination combination : keybind.getCombinations()) {
            if (combination.getKeyCode() == keyCode && modifiersMatch(combination.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    private static boolean modifiersMatch(Set<Integer> expected) {
        boolean ctrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean altDown = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);

        boolean expectCtrl = expected != null
                && (expected.contains(Keyboard.KEY_LCONTROL) || expected.contains(Keyboard.KEY_RCONTROL));
        boolean expectShift = expected != null
                && (expected.contains(Keyboard.KEY_LSHIFT) || expected.contains(Keyboard.KEY_RSHIFT));
        boolean expectAlt = expected != null
                && (expected.contains(Keyboard.KEY_LMENU) || expected.contains(Keyboard.KEY_RMENU));

        return ctrlDown == expectCtrl && shiftDown == expectShift && altDown == expectAlt;
    }

    private static KeybindManager.Keybind key(int keyCode) {
        return new KeybindManager.Keybind(keyCode, new HashSet<Integer>());
    }

    private static KeybindManager.Keybind ctrl(int keyCode) {
        Set<Integer> modifiers = new HashSet<>();
        modifiers.add(Keyboard.KEY_LCONTROL);
        return new KeybindManager.Keybind(keyCode, modifiers);
    }

    private static KeybindManager.Keybind normalize(KeybindManager.Keybind keybind) {
        if (keybind == null) {
            return new KeybindManager.Keybind();
        }
        return cloneKeybind(keybind);
    }

    private static KeybindManager.Keybind cloneKeybind(KeybindManager.Keybind keybind) {
        KeybindManager.Keybind copy = new KeybindManager.Keybind();
        copy.setCombinations(keybind.getCombinations());
        copy.setParameter(keybind.getParameter());
        return copy;
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("node_editor_hotkeys.json");
    }
}
