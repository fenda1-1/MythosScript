// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/BindableDebugAction.java
// (这是一个新文件)
package com.zszl.zszlScriptMod.system;

import net.minecraft.client.resources.I18n;

/**
 * 定义所有可以被快捷键绑定的【调试】动作。
 */
public enum BindableDebugAction {
    TOGGLE_AUTO_EQUIP("keybind.debug_action.toggle_auto_equip.name", "keybind.debug_action.toggle_auto_equip.desc"),
    START_CHEST_RECORDING("keybind.debug_action.start_chest_recording.name",
            "keybind.debug_action.start_chest_recording.desc");

    private final String displayNameKey;
    private final String descriptionKey;

    BindableDebugAction(String displayNameKey, String descriptionKey) {
        this.displayNameKey = displayNameKey;
        this.descriptionKey = descriptionKey;
    }

    public String getDisplayName() {
        return I18n.format(displayNameKey);
    }

    public String getDescription() {
        return I18n.format(descriptionKey);
    }
}
