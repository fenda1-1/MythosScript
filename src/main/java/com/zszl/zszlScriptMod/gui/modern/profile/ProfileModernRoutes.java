package com.zszl.zszlScriptMod.gui.modern.profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Modern profile lifecycle and sharing workbench routes. */
public final class ProfileModernRoutes {
    private ProfileModernRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(Arrays.asList(
                new ModernTabDescriptor("profile_manager", "配置档案",
                        (minecraft, context) -> ProfileWorkbenchTab.create("profile_manager")),
                route("profile_editor", "档案编辑", "档案名称与当前档案选择", "profile_manager"),
                route("profile_files", "档案文件", "查看和编辑当前档案文件", "profile_file_editor", "profile_manager"),
                route("profile_file_editor", "文件编辑器", "保存或取消文件草稿", "profile_files"),
                route("profile_share", "分享导入导出", "预览分享码并确认导入", "profile_import_preview", "profile_manager"),
                route("profile_import_preview", "导入预览", "确认后写入目标档案", "profile_share")));
    }

    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor route : routes()) {
            if (route.getCommand().equals(command)) {
                return ProfileWorkbenchTab.create(command);
            }
        }
        return null;
    }

    private static ModernTabDescriptor route(String command, String title, String subtitle, String... children) {
        return new ModernTabDescriptor(command, title, (minecraft, context) -> ProfileWorkbenchTab.create(command));
    }
}
