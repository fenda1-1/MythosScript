package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.gui.packet.PacketInterceptConfig;
import io.netty.buffer.ByteBufUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PacketInterceptManager {

    private static final Pattern TEMPLATE_GROUP_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*|\\d+)\\}");
    private static final Pattern TEMPLATE_COPY_CMD_PATTERN = Pattern
            .compile("\\{copycmd:([A-Za-z_][A-Za-z0-9_]*|\\d+):([A-Za-z_][A-Za-z0-9_]*|\\d+)\\}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPLATE_ADD_ASCII_PATTERN = Pattern
            .compile("\\{addascii:([A-Za-z_][A-Za-z0-9_]*|\\d+):([+-]?\\d+)\\}", Pattern.CASE_INSENSITIVE);

    private PacketInterceptManager() {
    }

    public static class InterceptResult {
        public final byte[] payload;
        public final boolean modified;
        public final String matchedRule;

        private InterceptResult(byte[] payload, boolean modified, String matchedRule) {
            this.payload = payload;
            this.modified = modified;
            this.matchedRule = matchedRule;
        }

        public static InterceptResult noChange(byte[] payload) {
            return new InterceptResult(payload, false, null);
        }

        public static InterceptResult changed(byte[] payload, String ruleName) {
            return new InterceptResult(payload, true, ruleName);
        }
    }

    public static class PacketMeta {
        public final String channel;
        public final String packetClassName;
        public final Integer packetId;

        public PacketMeta(String channel, String packetClassName, Integer packetId) {
            this.channel = channel;
            this.packetClassName = packetClassName;
            this.packetId = packetId;
        }
    }

    public static InterceptResult applyInboundRules(PacketMeta meta, byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return InterceptResult.noChange(rawData);
        }

        PacketInterceptConfig config = PacketInterceptConfig.INSTANCE;
        if (config == null || !config.inboundInterceptEnabled || config.inboundRules == null
                || config.inboundRules.isEmpty()) {
            return InterceptResult.noChange(rawData);
        }

        String currentHex = ByteBufUtil.hexDump(rawData).toUpperCase(Locale.ROOT);
        boolean anyChanged = false;
        String firstMatchedRule = null;

        for (PacketInterceptConfig.InterceptRule rule : config.inboundRules) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            PacketInterceptConfig.normalizeRule(rule);

            if (!channelMatches(rule.channel, meta == null ? null : meta.channel)) {
                continue;
            }
            if (!packetMatches(rule.packetFilter, meta)) {
                continue;
            }

            String matchHex = normalizeHex(rule.matchHex);
            if (rule.regexEnabled) {
                String rawRegex = rule.matchHex == null ? "" : rule.matchHex.trim();
                String replacement = rule.replaceHex == null ? "" : rule.replaceHex;
                if (rawRegex.isEmpty()) {
                    continue;
                }
                String regex = normalizeRegexPattern(rawRegex);
                try {
                    Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                    Matcher m = p.matcher(currentHex);
                    if (!m.find()) {
                        continue;
                    }
                    if (firstMatchedRule == null) {
                        firstMatchedRule = rule.name;
                    }
                    if (containsTemplateGroupReference(replacement)) {
                        currentHex = applyRegexReplacementWithTemplate(p, currentHex, replacement, rule.replaceAll);
                    } else {
                        currentHex = rule.replaceAll ? m.replaceAll(replacement) : m.replaceFirst(replacement);
                    }
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            } else {
                String replaceHex = normalizeHex(rule.replaceHex);
                if (matchHex.isEmpty() || replaceHex.isEmpty()) {
                    continue;
                }
                if ((matchHex.length() & 1) == 1 || (replaceHex.length() & 1) == 1) {
                    continue;
                }

                if (!currentHex.contains(matchHex)) {
                    continue;
                }

                if (firstMatchedRule == null) {
                    firstMatchedRule = rule.name;
                }

                if (rule.replaceAll) {
                    currentHex = currentHex.replace(matchHex, replaceHex);
                } else {
                    currentHex = replaceFirst(currentHex, matchHex, replaceHex);
                }
            }
            anyChanged = true;
        }

        if (!anyChanged) {
            return InterceptResult.noChange(rawData);
        }

        // 兼容正则替换模板中夹带空白字符的输入
        currentHex = currentHex.replaceAll("\\s+", "");

        byte[] bytes = hexToBytes(currentHex);
        if (bytes == null) {
            return InterceptResult.noChange(rawData);
        }
        return InterceptResult.changed(bytes, firstMatchedRule);
    }

    private static boolean packetMatches(String packetFilter, PacketMeta meta) {
        String filter = packetFilter == null ? "" : packetFilter.trim();
        if (filter.isEmpty() || "*".equals(filter) || "all".equalsIgnoreCase(filter)) {
            return true;
        }
        if (meta == null) {
            return false;
        }

        String f = filter.toLowerCase(Locale.ROOT);
        String cls = meta.packetClassName == null ? "" : meta.packetClassName.toLowerCase(Locale.ROOT);

        if (f.startsWith("id:")) {
            Integer expect = parseId(f.substring(3).trim());
            return expect != null && meta.packetId != null && expect.intValue() == meta.packetId.intValue();
        }
        if (f.startsWith("0x") || f.matches("^[0-9]+$")) {
            Integer expect = parseId(f);
            return expect != null && meta.packetId != null && expect.intValue() == meta.packetId.intValue();
        }

        return cls.contains(f);
    }

    private static Integer parseId(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            String t = text.trim().toLowerCase(Locale.ROOT);
            if (t.startsWith("0x")) {
                return Integer.parseInt(t.substring(2), 16);
            }
            return Integer.parseInt(t, 10);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String replaceFirst(String src, String target, String replacement) {
        int idx = src.indexOf(target);
        if (idx < 0) {
            return src;
        }
        return src.substring(0, idx) + replacement + src.substring(idx + target.length());
    }

    private static boolean channelMatches(String ruleChannel, String actualChannel) {
        String r = (ruleChannel == null) ? "" : ruleChannel.trim();
        if (r.isEmpty() || "*".equals(r) || "all".equalsIgnoreCase(r)) {
            return true;
        }
        String a = (actualChannel == null) ? "" : actualChannel.trim();
        return r.equalsIgnoreCase(a);
    }

    private static String normalizeHex(String hex) {
        if (hex == null) {
            return "";
        }
        String upper = hex.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) == 1) {
            return null;
        }
        try {
            int len = hex.length();
            byte[] out = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeRegexPattern(String rawRegex) {
        // 允许用户写带空格HEX风格正则，自动移除空白，目标串是无空格HEX
        return rawRegex.replaceAll("\\s+", "");
    }

    private static boolean containsTemplateGroupReference(String replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return false;
        }
        return TEMPLATE_GROUP_PATTERN.matcher(replacement).find()
                || TEMPLATE_COPY_CMD_PATTERN.matcher(replacement).find()
                || TEMPLATE_ADD_ASCII_PATTERN.matcher(replacement).find();
    }

    private static String applyRegexReplacementWithTemplate(Pattern pattern, String input, String template,
            boolean replaceAll) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        boolean matched = false;

        while (matcher.find()) {
            matched = true;
            String expanded = expandTemplateWithGroups(template, matcher);
            matcher.appendReplacement(sb, expanded);
            if (!replaceAll) {
                break;
            }
        }

        if (!matched) {
            return input;
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String expandTemplateWithGroups(String template, Matcher regexMatcher) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        // 先处理函数模板：{copycmd:nameGroup:targetLenGroup}
        Matcher fm = TEMPLATE_COPY_CMD_PATTERN.matcher(template);
        StringBuffer templWithFunctions = new StringBuffer();
        while (fm.find()) {
            String nameHex = getRegexGroup(regexMatcher, fm.group(1));
            String targetHex = getRegexGroup(regexMatcher, fm.group(2));
            String copyHex = buildCopyCommandHex(nameHex, targetHex == null ? 0 : targetHex.length() / 2);
            fm.appendReplacement(templWithFunctions, Matcher.quoteReplacement(copyHex));
        }
        fm.appendTail(templWithFunctions);

        String expandedTemplate = templWithFunctions.toString();

        // 再处理数值函数模板：{addascii:group:+200}
        Matcher am = TEMPLATE_ADD_ASCII_PATTERN.matcher(expandedTemplate);
        StringBuffer afterAddAscii = new StringBuffer();
        while (am.find()) {
            String sourceHex = getRegexGroup(regexMatcher, am.group(1));
            int delta = 0;
            try {
                delta = Integer.parseInt(am.group(2));
            } catch (Exception ignored) {
            }
            String outHex = addAsciiDecimalHex(sourceHex, delta);
            am.appendReplacement(afterAddAscii, Matcher.quoteReplacement(outHex));
        }
        am.appendTail(afterAddAscii);
        expandedTemplate = afterAddAscii.toString();

        Matcher tm = TEMPLATE_GROUP_PATTERN.matcher(expandedTemplate);
        StringBuffer out = new StringBuffer();
        while (tm.find()) {
            String key = tm.group(1);
            String value = getRegexGroup(regexMatcher, key);

            tm.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        tm.appendTail(out);
        return out.toString();
    }

    private static String addAsciiDecimalHex(String sourceHex, int delta) {
        String clean = normalizeHex(sourceHex);
        if (clean.isEmpty() || (clean.length() & 1) == 1) {
            return clean;
        }

        // sourceHex 是ASCII数字的HEX（例如 "353738" -> "578"）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i += 2) {
            int b;
            try {
                b = Integer.parseInt(clean.substring(i, i + 2), 16);
            } catch (Exception e) {
                return clean;
            }
            if (b < 0x30 || b > 0x39) {
                return clean;
            }
            sb.append((char) b);
        }

        long value;
        try {
            value = Long.parseLong(sb.toString());
        } catch (Exception e) {
            return clean;
        }

        long next = Math.max(0L, value + delta);
        String nextText = Long.toString(next);

        // 保持原长度，避免影响上层长度字段
        int oldLen = clean.length() / 2;
        if (nextText.length() > oldLen) {
            // 超长时保留低位，保证不改字节数
            nextText = nextText.substring(nextText.length() - oldLen);
        } else if (nextText.length() < oldLen) {
            StringBuilder padded = new StringBuilder(oldLen);
            for (int i = nextText.length(); i < oldLen; i++) {
                padded.append('0');
            }
            padded.append(nextText);
            nextText = padded.toString();
        }

        StringBuilder out = new StringBuilder(oldLen * 2);
        for (int i = 0; i < nextText.length(); i++) {
            out.append(String.format("%02X", (int) nextText.charAt(i)));
        }
        return out.toString();
    }

    private static String getRegexGroup(Matcher regexMatcher, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.chars().allMatch(Character::isDigit)) {
            try {
                int idx = Integer.parseInt(key);
                String g = regexMatcher.group(idx);
                return g == null ? "" : g;
            } catch (Exception ignored) {
                return "";
            }
        }
        try {
            String g = regexMatcher.group(key);
            return g == null ? "" : g;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String buildCopyCommandHex(String nameHex, int targetBytes) {
        String safeNameHex = normalizeHex(nameHex);
        String baseHex = "2F636F707920" + safeNameHex; // "/copy " + name

        if (targetBytes <= 0) {
            return baseHex;
        }

        int targetHexLen = targetBytes * 2;
        if (baseHex.length() > targetHexLen) {
            return baseHex.substring(0, targetHexLen);
        }
        if (baseHex.length() < targetHexLen) {
            StringBuilder sb = new StringBuilder(targetHexLen);
            sb.append(baseHex);
            while (sb.length() < targetHexLen) {
                sb.append("20"); // 空格填充，保持原字段总长度
            }
            return sb.toString();
        }
        return baseHex;
    }

    public static List<String> validateRules(List<PacketInterceptConfig.InterceptRule> rules) {
        List<String> errors = new ArrayList<>();
        if (rules == null) {
            return errors;
        }
        for (int i = 0; i < rules.size(); i++) {
            PacketInterceptConfig.InterceptRule r = rules.get(i);
            if (r == null) {
                errors.add("#" + (i + 1) + " 规则为空");
                continue;
            }
            String m = normalizeHex(r.matchHex);
            if (r.regexEnabled) {
                String regex = (r.matchHex == null) ? "" : r.matchHex.trim();
                if (regex.isEmpty()) {
                    errors.add("#" + (i + 1) + " regex 为空");
                } else {
                    try {
                        Pattern.compile(normalizeRegexPattern(regex), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                    } catch (PatternSyntaxException e) {
                        errors.add("#" + (i + 1) + " regex 非法: " + e.getDescription());
                    }
                }
                if (r.replaceHex == null) {
                    errors.add("#" + (i + 1) + " 替换模板为空");
                }
            } else {
                String rep = normalizeHex(r.replaceHex);
                if (m.isEmpty()) {
                    errors.add("#" + (i + 1) + " matchHex 为空");
                } else if ((m.length() & 1) == 1) {
                    errors.add("#" + (i + 1) + " matchHex 不是偶数字节");
                }
                if (rep.isEmpty()) {
                    errors.add("#" + (i + 1) + " replaceHex 为空");
                } else if ((rep.length() & 1) == 1) {
                    errors.add("#" + (i + 1) + " replaceHex 不是偶数字节");
                }
            }
        }
        return errors;
    }
}
