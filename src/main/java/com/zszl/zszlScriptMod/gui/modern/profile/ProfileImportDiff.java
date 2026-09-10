package com.zszl.zszlScriptMod.gui.modern.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Theme-safe line diff for share-code import previews. */
public final class ProfileImportDiff {

    public enum Kind {
        KEEP,
        ADD,
        REMOVE
    }

    public static final class Line {
        public final Kind kind;
        public final String text;

        public Line(Kind kind, String text) {
            this.kind = kind == null ? Kind.KEEP : kind;
            this.text = text == null ? "" : text;
        }
    }

    private ProfileImportDiff() {
    }

    public static List<Line> diff(String beforeContent, String afterContent) {
        List<String> beforeLines = split(beforeContent);
        List<String> afterLines = split(afterContent);
        long complexity = (long) beforeLines.size() * (long) afterLines.size();
        List<Line> result = complexity > 120000L
                ? simpleDiff(beforeLines, afterLines)
                : lcsDiff(beforeLines, afterLines);
        if (result.isEmpty()) {
            result.add(new Line(Kind.KEEP, ""));
        }
        return result;
    }

    private static List<Line> lcsDiff(List<String> beforeLines, List<String> afterLines) {
        int n = beforeLines.size();
        int m = afterLines.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (beforeLines.get(i).equals(afterLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        List<Line> result = new ArrayList<Line>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            String before = beforeLines.get(i);
            String after = afterLines.get(j);
            if (before.equals(after)) {
                result.add(new Line(Kind.KEEP, before));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new Line(Kind.REMOVE, before));
                i++;
            } else {
                result.add(new Line(Kind.ADD, after));
                j++;
            }
        }
        while (i < n) {
            result.add(new Line(Kind.REMOVE, beforeLines.get(i++)));
        }
        while (j < m) {
            result.add(new Line(Kind.ADD, afterLines.get(j++)));
        }
        return result;
    }

    private static List<Line> simpleDiff(List<String> beforeLines, List<String> afterLines) {
        List<Line> result = new ArrayList<Line>();
        int max = Math.max(beforeLines.size(), afterLines.size());
        for (int i = 0; i < max; i++) {
            String before = i < beforeLines.size() ? beforeLines.get(i) : null;
            String after = i < afterLines.size() ? afterLines.get(i) : null;
            if (before != null && after != null && before.equals(after)) {
                result.add(new Line(Kind.KEEP, before));
            } else {
                if (before != null) {
                    result.add(new Line(Kind.REMOVE, before));
                }
                if (after != null) {
                    result.add(new Line(Kind.ADD, after));
                }
            }
        }
        return result;
    }

    private static List<String> split(String content) {
        List<String> result = new ArrayList<String>();
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        Collections.addAll(result, lines);
        if (result.isEmpty()) {
            result.add("");
        }
        if (result.size() > 1 && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
