package com.zszl.zszlScriptMod.utils;

import com.google.gson.JsonElement;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class TickRangeSpec {
    private TickRangeSpec() {
    }

    public static Range parse(JsonElement element, int fallback, int minLimit, int maxLimit) {
        if (element == null || element.isJsonNull()) {
            return parse("", fallback, minLimit, maxLimit);
        }
        try {
            return parse(element.getAsString(), fallback, minLimit, maxLimit);
        } catch (Exception ignored) {
            return parse("", fallback, minLimit, maxLimit);
        }
    }

    public static Range parse(String spec, int fallback, int minLimit, int maxLimit) {
        int lower = Math.min(minLimit, maxLimit);
        int upper = Math.max(minLimit, maxLimit);
        int safeFallback = clamp(fallback, lower, upper);
        String normalized = spec == null ? "" : spec.trim();
        if (normalized.isEmpty()) {
            return new Range(safeFallback, safeFallback, lower, upper);
        }

        normalized = cleanSpec(normalized);

        String[] parts = normalized.split("-", -1);
        try {
            if (parts.length == 1) {
                int value = clamp(Integer.parseInt(parts[0]), lower, upper);
                return new Range(value, value, lower, upper);
            }
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                int first = Integer.parseInt(parts[0]);
                int second = Integer.parseInt(parts[1]);
                int min = clamp(Math.min(first, second), lower, upper);
                int max = clamp(Math.max(first, second), lower, upper);
                return new Range(min, Math.max(min, max), lower, upper);
            }
        } catch (Exception ignored) {
        }

        return new Range(safeFallback, safeFallback, lower, upper);
    }

    public static boolean isValid(String spec) {
        String normalized = spec == null ? "" : spec.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String[] parts = cleanSpec(normalized).split("-", -1);
        try {
            if (parts.length == 1 && !parts[0].isEmpty()) {
                Integer.parseInt(parts[0]);
                return true;
            }
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                Integer.parseInt(parts[0]);
                Integer.parseInt(parts[1]);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isValid(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        try {
            return isValid(element.getAsString());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String normalize(String spec, int fallback, int minLimit, int maxLimit) {
        return parse(spec, fallback, minLimit, maxLimit).toSpec();
    }

    public static String normalize(JsonElement element, int fallback, int minLimit, int maxLimit) {
        return parse(element, fallback, minLimit, maxLimit).toSpec();
    }

    public static int sample(String spec, int fallback, int minLimit, int maxLimit) {
        return parse(spec, fallback, minLimit, maxLimit).sample();
    }

    public static int sample(JsonElement element, int fallback, int minLimit, int maxLimit) {
        return parse(element, fallback, minLimit, maxLimit).sample();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static String cleanSpec(String spec) {
        return (spec == null ? "" : spec).toLowerCase(Locale.ROOT)
                .replace("ticks", "")
                .replace("tick", "")
                .replace("t", "")
                .replace("，", ",")
                .replace("－", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("~", "-")
                .replace("～", "-")
                .replace("至", "-")
                .replace("到", "-")
                .replaceAll("\\s+", "");
    }

    public static final class Range {
        private final int min;
        private final int max;
        private final int minLimit;
        private final int maxLimit;

        private Range(int min, int max, int minLimit, int maxLimit) {
            this.minLimit = Math.min(minLimit, maxLimit);
            this.maxLimit = Math.max(minLimit, maxLimit);
            this.min = clamp(Math.min(min, max), this.minLimit, this.maxLimit);
            this.max = clamp(Math.max(this.min, max), this.minLimit, this.maxLimit);
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public boolean isRandom() {
            return max > min;
        }

        public int sample() {
            if (!isRandom()) {
                return min;
            }
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }

        public String toSpec() {
            if (!isRandom()) {
                return String.valueOf(min);
            }
            return min + "-" + max;
        }

        public String toDisplayText() {
            return toSpec();
        }
    }
}
