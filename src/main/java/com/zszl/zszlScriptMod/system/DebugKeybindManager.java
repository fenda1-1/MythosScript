package com.zszl.zszlScriptMod.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.handlers.AutoEquipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 调试功能快捷键的核心管理器。
 */
public class DebugKeybindManager {

    public static final Map<BindableDebugAction, KeybindManager.Keybind> keybinds = new EnumMap<>(
            BindableDebugAction.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void executeAction(BindableDebugAction action) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null)
            return;

        switch (action) {
            case TOGGLE_AUTO_EQUIP:
                // 直接控制自动穿戴开关，而不是总开关
                AutoEquipHandler.enabled = !AutoEquipHandler.enabled;
                if (AutoEquipHandler.enabled) {
                    AutoEquipHandler.masterSwitchEnabled = true;
                }
                String status = AutoEquipHandler.enabled ? I18n.format("gui.common.enabled")
                        : I18n.format("gui.common.disabled");
                mc.player.sendMessage(
                        new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.debug_keybind.auto_equip_status", status)));
                AutoEquipHandler.saveConfig();
                break;
            case START_CHEST_RECORDING:
                mc.player.sendMessage(
                        new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.debug_keybind.open_chest_recording")));
                mc.addScheduledTask(() -> GuiModernMainScreen.openSettingsTab(mc, "recording"));
                break;
        }
    }

    private static File getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("debug_keybinds.json").toFile();
    }

    public static void loadConfig() {
        keybinds.clear();
        File configFile = getConfigFile();
        if (!configFile.exists())
            return;

        try (BufferedReader reader = Files.newBufferedReader(configFile.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, KeybindManager.Keybind>>() {
            }.getType();
            Map<String, KeybindManager.Keybind> loadedMap = GSON.fromJson(reader, type);

            if (loadedMap != null) {
                for (Map.Entry<String, KeybindManager.Keybind> entry : loadedMap.entrySet()) {
                    try {
                        BindableDebugAction action = BindableDebugAction.valueOf(entry.getKey());
                        keybinds.put(action, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        zszlScriptMod.LOGGER.warn("Unknown action found in debug keybind config: {}", entry.getKey());
                    }
                }
            }
            zszlScriptMod.LOGGER.info("Loaded {} debug keybind(s)", keybinds.size());
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to load debug keybind config", e);
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            try (BufferedWriter writer = Files.newBufferedWriter(configFile.toPath(), StandardCharsets.UTF_8)) {
                Map<String, KeybindManager.Keybind> mapToSave = new HashMap<>();
                keybinds.forEach((action, keybind) -> mapToSave.put(action.name(), keybind));
                GSON.toJson(mapToSave, writer);
                zszlScriptMod.LOGGER.info("Debug keybind config saved");
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("Failed to save debug keybind config", e);
        }
    }
}
