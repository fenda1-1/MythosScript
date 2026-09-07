// 文件路径: src/main/java/com/zszl/zszlScriptMod/utils/UpdateManager.java
// !! 这是修复了链接提取问题的最终版本 !!
package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.awt.Desktop;
import java.net.URI;
// !! 核心修改 1: 导入正则表达式相关的类 !!
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateManager {

    private static final String PRIMARY_URL_KEY = "update_primary.url";
    private static final String PASSWORD = "fenda";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1";

    // !! 核心修改 2: 创建一个静态的正则表达式模式，用于匹配URL !!
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[\\w./-]+)");

    public static void fetchUpdateLinkAndOpen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null)
            return;

        mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + I18n.format("msg.update.fetching")));

        new Thread(() -> {
            try {
                String primaryUrl = SharechainLinkConfig.getRequiredUrl(PRIMARY_URL_KEY);
                // 1. 获取包含下载链接的页面的全部文本内容
                String pageText = HttpsCompat.connect(primaryUrl)
                        .userAgent(MOBILE_USER_AGENT)
                        .timeout(15000)
                        .get()
                        .body()
                        .text();

                // !! 核心修改 3: 使用正则表达式从文本中查找URL !!
                Matcher matcher = URL_PATTERN.matcher(pageText);
                String downloadLink = null;

                if (matcher.find()) {
                    // 如果找到了匹配项，提取第一个捕获组（也就是完整的URL）
                    downloadLink = matcher.group(1);
                }

                // 2. 验证提取出的链接是否有效
                if (downloadLink == null || !downloadLink.startsWith("http")) {
                    // 抛出更详细的错误信息，方便调试
                    throw new Exception(I18n.format("msg.update.error.no_valid_link") + pageText);
                }

                final String finalDownloadLink = downloadLink;

                // 3. 在主线程中执行与Minecraft相关的操作
                mc.addScheduledTask(() -> {
                    try {
                        // 复制密码到剪贴板
                        GuiScreen.setClipboardString(PASSWORD);
                        mc.player.sendMessage(new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.update.password_copied", PASSWORD)));

                        // 打开浏览器
                        URI uri = new URI(finalDownloadLink);
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(uri);
                            mc.player.sendMessage(
                                    new TextComponentString(
                                            TextFormatting.AQUA + I18n.format("msg.update.opening_browser")));
                        } else {
                            mc.player.sendMessage(new TextComponentString(
                                    TextFormatting.RED + I18n.format("msg.update.browser_unsupported")));
                        }
                    } catch (Exception e) {
                        zszlScriptMod.LOGGER.error("Failed to open link or copy password on main thread", e);
                        mc.player.sendMessage(new TextComponentString(
                                TextFormatting.RED + I18n.format("msg.update.operation_failed")));
                    }
                });

            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("Failed to fetch update link", e);
                mc.addScheduledTask(() -> {
                    mc.player.sendMessage(
                            new TextComponentString(TextFormatting.RED + I18n.format("msg.update.fetch_failed")));
                });
            }
        }).start();
    }
}
