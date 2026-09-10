package com.zszl.zszlScriptMod.gui.modern.nonmanagement;

import java.awt.Desktop;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.modern.ModernThemeSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernUtilitySettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernTabDescriptor;

/** Routes for non-management pages and external support actions. */
public final class ModernNonManagementRoutes {
    private ModernNonManagementRoutes() { }

    public static List<ModernTabDescriptor> routes() {
        return Collections.unmodifiableList(java.util.Arrays.asList(
                route("theme", "主题", "主题预览与选择"),
                route("donate", "支持作者/联系方式", "作者 fenda · QQ 群 954821473"),
                route("update", "更新", "在外部浏览器打开更新页面"),
                route("changelog", "gui.changelog.title", "gui.changelog.description"),
                route("recognition", "识别", "识别结果查看")));
    }

    public static ModernSettingsTab create(String command, ModernScreenContext context) {
        for (ModernTabDescriptor descriptor : routes()) {
            if (descriptor.getCommand().equals(command)) {
                return descriptor.getFactory().create(context == null ? null : context.getMinecraft(), context);
            }
        }
        return null;
    }

    public static String openExternal(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "未打开外部链接：地址为空";
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                return "未打开外部链接：系统不支持浏览器";
            }
            Desktop.getDesktop().browse(new URI(url));
            return "已在外部浏览器打开链接";
        } catch (Exception exception) {
            return "未打开外部链接：" + exception.getClass().getSimpleName();
        }
    }

    private static ModernTabDescriptor route(String command, String title, String subtitle) {
        return new ModernTabDescriptor(command, title, (minecraft, context) ->
                createTab(command, minecraft));
    }

    private static ModernSettingsTab createTab(String command, net.minecraft.client.Minecraft minecraft) {
        if ("theme".equals(command)) return ModernThemeSettingsTab.create();
        if ("donate".equals(command)) return new ModernDonationSupportTab(minecraft);
        if ("changelog".equals(command)) return new ModernChangelogTab();
        if ("recognition".equals(command)) return ModernUtilitySettingsTab.guiInspector();
        return new ExternalLinkWorkbenchTab();
    }
}
