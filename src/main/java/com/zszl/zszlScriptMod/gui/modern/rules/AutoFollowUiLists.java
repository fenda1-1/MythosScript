package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.*;

/** Parsing and ordering shared by the card editor and its regression tests. */
final class AutoFollowUiLists {
    static List<String> entries(String text, boolean points) {
        List<String> result = new ArrayList<>();
        for (String item : (text == null ? "" : text).split(points ? "[;；\\n]+" : "[,，;；\\n]+")) {
            if (!item.trim().isEmpty() && (points || !result.contains(item.trim()))) result.add(item.trim());
        }
        return result;
    }

    static String[] coordinates(String point) {
        String[] parts = point.split(",");
        if (parts.length == 2) return new String[] { parts[0].trim(), "", parts[1].trim() };
        if (parts.length != 3) throw new IllegalArgumentException("请输入 X、Y、Z 坐标");
        return new String[] { parts[0].trim(), parts[1].trim(), parts[2].trim() };
    }

    static String point(String x, String y, String z) {
        validate(x); validate(z);
        if (y.trim().isEmpty()) return x.trim() + "," + z.trim();
        validate(y);
        return x.trim() + "," + y.trim() + "," + z.trim();
    }

    private static void validate(String value) {
        try {
            if (!Double.isFinite(Double.parseDouble(value.trim()))) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("坐标必须是有效数字");
        }
    }

    static <T> void move(List<T> list, int from, int insertion) {
        if (from < 0 || from >= list.size()) return;
        int to = Math.max(0, Math.min(list.size(), insertion));
        T value = list.remove(from);
        if (to > from) to--;
        list.add(to, value);
    }
}
