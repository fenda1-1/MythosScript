// 文件路径: src/main/java/com/zszl/zszlScriptMod/utils/UpdateChecker.java
package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.resources.I18n;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

// 移除了不再需要的 java.net.* 和 java.io.* 包
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责从指定URL异步获取和解析版本信息及更新日志。
 */
public class UpdateChecker {

    public static volatile String latestVersion = "...";
    public static volatile String changelogContent = I18n.format("msg.update_checker.fetching_changelog");
    private static boolean hasFetched = false;
    private static volatile boolean fetchInProgress = false;
    private static volatile boolean lastRefreshFailed = false;
    private static volatile boolean changelogLoaded = false;

    public static boolean hasChangelog() {
        return changelogLoaded;
    }

    public static boolean isRefreshing() {
        return fetchInProgress;
    }

    public static boolean didLastRefreshFail() {
        return lastRefreshFailed;
    }
    private static volatile long lastFetchTime = 0L;
    private static volatile long lastCheckRunTime = 0L;
    private static volatile long lastNoticeTime = 0L;
    private static volatile boolean metaLoaded = false;

    private static final String UPDATE_URL_KEY = "update_changelog.url";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\[(v[\\d.]+)\\]");
    private static final String CACHE_FILE = "update_changelog.md";
    private static final String META_CACHE_FILE = "update_checker_meta.txt";
    private static final long NOTICE_INTERVAL_MS = 60L * 60L * 1000L;

    /**
     * 启动一个后台线程来获取版本和更新日志。
     * 此方法是线程安全的，并且只会执行一次网络请求。
     */
    public static void fetchVersionAndChangelog() {
        ensureMetaLoaded();
        if (hasFetched) {
            return;
        }
        String cached = CloudContentCache.readText(CACHE_FILE);
        if (!cached.isEmpty()) {
            applyParsedContent(cached);
        }
        hasFetched = true;
        forceRefresh();
    }

    public static void requestRefreshIfDue(long intervalMs) {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime >= intervalMs) {
            forceRefresh();
        }
    }

    public static void forceRefresh() {
        ensureMetaLoaded();
        if (fetchInProgress) {
            return;
        }
        fetchInProgress = true;
        lastRefreshFailed = false;
        long now = System.currentTimeMillis();
        lastFetchTime = now;
        lastCheckRunTime = now;
        persistMeta();

        new Thread(() -> {
            zszlScriptMod.LOGGER.info("[UpdateChecker] Background refresh thread started");
            try {
                String updateUrl = SharechainLinkConfig.getRequiredUrl(UPDATE_URL_KEY);
                zszlScriptMod.LOGGER.info("[UpdateChecker] Connecting to URL: {}", updateUrl);

                // !! 核心修改：使用Jsoup直接连接和获取文档，让它自动处理编码 !!
                Document doc = HttpsCompat.connect(updateUrl)
                        .userAgent(MOBILE_USER_AGENT)
                        .timeout(15000) // 15秒超时
                        .get();

                Element noteContentDiv = doc.selectFirst("div.note-content");

                if (noteContentDiv != null) {
                    zszlScriptMod.LOGGER.info("[UpdateChecker] Found 'div.note-content' element");

                    StringBuilder markdownBuilder = new StringBuilder();
                    for (Element p : noteContentDiv.select("p")) {
                        // Jsoup的 .text() 会解码HTML实体 (例如 &nbsp; 会变成空格)
                        String lineText = p.text().replace("\u00a0", " "); // 将 &nbsp; 产生的特殊空格替换为普通空格
                        markdownBuilder.append(lineText).append("\n");
                    }
                    String rawText = markdownBuilder.toString().trim();

                    if (rawText.isEmpty()) {
                        throw new Exception(I18n.format("msg.common.error.parsed_content_empty"));
                    }

                    zszlScriptMod.LOGGER.info("[UpdateChecker] Parsed markdown content length: {}", rawText.length());
                    applyParsedContent(rawText);
                    CloudContentCache.writeText(CACHE_FILE, rawText);
                } else {
                    zszlScriptMod.LOGGER.error(
                            "[UpdateChecker] Critical: 'div.note-content' not found in response HTML; parse failed");
                    throw new Exception(I18n.format("msg.common.error.note_content_missing"));
                }

            } catch (Exception e) {
                lastRefreshFailed = true;
                if (changelogContent == null || changelogContent.trim().isEmpty()
                        || changelogContent.equals(I18n.format("msg.update_checker.fetching_changelog"))) {
                    latestVersion = I18n.format("msg.common.fetch_failed");
                    changelogContent = I18n.format("msg.update_checker.fetch_failed", e.getClass().getSimpleName(),
                            e.getMessage());
                }
                zszlScriptMod.LOGGER.error("Failed to fetch update information", e);
            } finally {
                fetchInProgress = false;
            }
        }, "UpdateChecker-Refresh").start();
    }

    private static void applyParsedContent(String rawText) {
        changelogContent = rawText;
        changelogLoaded = true;
        Matcher matcher = VERSION_PATTERN.matcher(rawText == null ? "" : rawText);
        if (matcher.find()) {
            latestVersion = matcher.group(1);
            zszlScriptMod.LOGGER.info("[UpdateChecker] Latest version parsed: {}", latestVersion);
        } else {
            latestVersion = I18n.format("msg.common.unknown");
            zszlScriptMod.LOGGER.warn("[UpdateChecker] Unable to parse version from content");
        }
    }

    public static void notifyIfNewVersion() {
        ensureMetaLoaded();
        requestRefreshIfDue(NOTICE_INTERVAL_MS);

        if (latestVersion == null || latestVersion.equals("...")
                || latestVersion.equals(I18n.format("msg.common.unknown"))
                || latestVersion.equals(I18n.format("msg.common.fetch_failed"))) {
            return;
        }
        if (!latestVersion.equals(zszlScriptMod.VERSION)) {
            long now = System.currentTimeMillis();
            if (lastNoticeTime > 0L && (now - lastNoticeTime) < NOTICE_INTERVAL_MS) {
                return;
            }

            lastNoticeTime = now;
            persistMeta();
            zszlScriptMod.LOGGER.info(
                    "New script version detected. Click update script to update, or click version to view changelog.");
            if (net.minecraft.client.Minecraft.getMinecraft().player != null) {
                net.minecraft.client.Minecraft.getMinecraft().player.sendMessage(
                        new net.minecraft.util.text.TextComponentString(
                                net.minecraft.util.text.TextFormatting.YELLOW
                                        + I18n.format("msg.update_checker.new_version_notice")));
            }
        }
    }

    private static void ensureMetaLoaded() {
        if (metaLoaded) {
            return;
        }
        synchronized (UpdateChecker.class) {
            if (metaLoaded) {
                return;
            }
            String meta = CloudContentCache.readText(META_CACHE_FILE);
            if (!meta.isEmpty()) {
                for (String line : meta.split("\\r?\\n")) {
                    String trimmed = line == null ? "" : line.trim();
                    if (trimmed.isEmpty() || !trimmed.contains("=")) {
                        continue;
                    }
                    String[] kv = trimmed.split("=", 2);
                    String key = kv[0].trim().toLowerCase(Locale.ROOT);
                    String value = kv[1].trim();
                    long parsed = parseLong(value);
                    if ("last_check_run_ms".equals(key)) {
                        lastCheckRunTime = Math.max(0L, parsed);
                    } else if ("last_notice_ms".equals(key)) {
                        lastNoticeTime = Math.max(0L, parsed);
                    }
                }
            }
            metaLoaded = true;
        }
    }

    private static void persistMeta() {
        String content = "last_check_run_ms=" + Math.max(0L, lastCheckRunTime) + "\n"
                + "last_notice_ms=" + Math.max(0L, lastNoticeTime);
        CloudContentCache.writeText(META_CACHE_FILE, content);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
