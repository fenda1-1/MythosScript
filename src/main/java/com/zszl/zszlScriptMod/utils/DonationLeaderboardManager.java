package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.zszlScriptMod;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DonationLeaderboardManager {

    public static class Entry {
        public final int rank;
        public final String name;
        public final String amount;

        public Entry(int rank, String name, String amount) {
            this.rank = rank;
            this.name = name;
            this.amount = amount;
        }
    }

    // 打赏码图片资源（内置到 jar）
    public static final String PAYMENT_QR_RESOURCE = "img/Sponsored.jpg";
    private static final String LEADERBOARD_URL_KEY = "donation_leaderboard.url";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1";
    private static final String CACHE_FILE = "donation_leaderboard.md";

    public static volatile List<Entry> leaderboard = new CopyOnWriteArrayList<>();
    public static volatile String rawMarkdown = "榜单加载中...";

    private static volatile boolean fetchInProgress = false;
    private static volatile boolean hasFetched = false;
    private static volatile long lastFetchTime = 0L;

    public static void fetchContent() {
        if (hasFetched) {
            return;
        }

        // 与神人榜一致：优先显示本地缓存，再异步刷新云端
        String cached = CloudContentCache.readText(CACHE_FILE);
        if (!cached.isEmpty()) {
            rawMarkdown = cached;
            leaderboard = parseLeaderboardFromMarkdown(cached);
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
        if (fetchInProgress) {
            return;
        }
        fetchInProgress = true;
        lastFetchTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                Document doc = HttpsCompat.connect(SharechainLinkConfig.getRequiredUrl(LEADERBOARD_URL_KEY))
                        .userAgent(MOBILE_USER_AGENT)
                        .timeout(15000)
                        .get();

                Element noteContentDiv = doc.selectFirst("div.note-content");
                if (noteContentDiv == null) {
                    throw new Exception("note-content not found");
                }

                StringBuilder markdownBuilder = new StringBuilder();
                for (Element p : noteContentDiv.select("p")) {
                    String lineText = p.text().replace("\u00a0", " ").trim();
                    if (!lineText.isEmpty()) {
                        markdownBuilder.append(lineText).append("\n");
                    }
                }

                String latest = markdownBuilder.toString().trim();
                if (!latest.isEmpty()) {
                    rawMarkdown = latest;
                    leaderboard = parseLeaderboardFromMarkdown(latest);
                    CloudContentCache.writeText(CACHE_FILE, latest);
                }
            } catch (Exception e) {
                if (leaderboard.isEmpty() && (rawMarkdown == null || rawMarkdown.trim().isEmpty())) {
                    rawMarkdown = "榜单加载失败：" + e.getMessage();
                }
                zszlScriptMod.LOGGER.warn("[Donation] 榜单刷新失败", e);
            } finally {
                fetchInProgress = false;
            }
        }, "DonationLeaderboard-Refresh").start();
    }

    private static List<Entry> parseLeaderboardFromMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return new CopyOnWriteArrayList<>();
        }

        List<Entry> parsed = new ArrayList<>();
        String[] lines = markdown.split("\\r?\\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Entry tableEntry = tryParseMarkdownTableLine(line);
            if (tableEntry != null) {
                parsed.add(tableEntry);
                continue;
            }

            Entry looseEntry = tryParseLooseLine(line);
            if (looseEntry != null) {
                parsed.add(looseEntry);
            }
        }

        Collections.sort(parsed, Comparator.comparingInt(o -> o.rank));
        return new CopyOnWriteArrayList<>(parsed);
    }

    private static Entry tryParseMarkdownTableLine(String line) {
        if (!line.contains("|")) {
            return null;
        }

        String[] cells = line.split("\\|");
        List<String> trimmed = new ArrayList<>();
        for (String cell : cells) {
            String c = cell.trim();
            if (!c.isEmpty()) {
                trimmed.add(c);
            }
        }

        if (trimmed.size() < 3) {
            return null;
        }

        String rankCell = trimmed.get(0);
        String nameCell = trimmed.get(1);
        String amountCell = trimmed.get(2);

        if (isHeaderOrSeparator(rankCell, nameCell, amountCell)) {
            return null;
        }

        int rank = parseRank(rankCell);
        if (rank <= 0) {
            return null;
        }

        if (nameCell.isEmpty() || amountCell.isEmpty()) {
            return null;
        }

        return new Entry(rank, nameCell, normalizeAmount(amountCell));
    }

    private static Entry tryParseLooseLine(String line) {
        // 例如：1. 张三 66.6
        Pattern p = Pattern.compile("^(\\d{1,3})[、.．)]\\s*([^\\s]+)\\s+(.+)$");
        Matcher m = p.matcher(line);
        if (!m.find()) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return null;
        }

        if (rank <= 0) {
            return null;
        }

        String name = m.group(2).trim();
        String amount = normalizeAmount(m.group(3).trim());
        if (name.isEmpty() || amount.isEmpty()) {
            return null;
        }

        return new Entry(rank, name, amount);
    }

    private static boolean isHeaderOrSeparator(String rankCell, String nameCell, String amountCell) {
        String r = rankCell.replace(" ", "");
        String n = nameCell.replace(" ", "");
        String a = amountCell.replace(" ", "");

        if (r.matches("[-:]+") || n.matches("[-:]+") || a.matches("[-:]+")) {
            return true;
        }

        return containsAny(r, "排名", "名次", "rank")
                || containsAny(n, "昵称", "名字", "玩家", "name")
                || containsAny(a, "金额", "打赏", "amount");
    }

    private static boolean containsAny(String text, String... keys) {
        String lower = text.toLowerCase();
        for (String key : keys) {
            if (lower.contains(key.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static int parseRank(String rankCell) {
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(rankCell);
        if (!m.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String normalizeAmount(String amount) {
        String a = amount.trim();
        if (a.endsWith("元") || a.endsWith("¥")) {
            return a;
        }
        return a + " 元";
    }
}
