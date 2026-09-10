package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

/** Pure packet search semantics shared by the native viewer and tests. */
public final class PacketQuerySupport {
    private PacketQuerySupport() { }

    public static boolean matches(PacketCaptureHandler.CapturedPacketData packet, String query) {
        if (packet == null) return false;
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) return true;
        String searchable = searchableText(packet);
        String lowerSearchable = searchable.toLowerCase(Locale.ROOT);
        String packetHex = normalizeHex(packet.getHexData());
        for (String token : tokens) {
            if (isRegexToken(token)) {
                try {
                    if (!Pattern.compile(regexPattern(token), Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                            .matcher(searchable).find()) return false;
                } catch (PatternSyntaxException ignored) {
                    return false;
                }
                continue;
            }
            String lower = token.toLowerCase(Locale.ROOT);
            String queryHex = isHexQuery(token) ? normalizeHex(token) : "";
            if (!lowerSearchable.contains(lower)
                    && (queryHex.isEmpty() || !packetHex.contains(queryHex))) return false;
        }
        return true;
    }

    /** Splits comma/space separated terms while keeping delimited regexes intact. */
    public static List<String> tokenize(String query) {
        List<String> result = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return result;
        int i = 0;
        while (i < query.length()) {
            while (i < query.length() && (Character.isWhitespace(query.charAt(i)) || query.charAt(i) == ',')) i++;
            if (i >= query.length()) break;
            int start = i;
            if (query.startsWith("re://", i)) {
                int end = query.indexOf("//", i + 5);
                if (end >= 0) i = end + 2;
                else {
                    end = query.indexOf('/', i + 5);
                    i = end >= 0 ? end + 1 : query.length();
                }
            } else if (query.charAt(i) == '/') {
                i++;
                boolean escaped = false;
                while (i < query.length()) {
                    char c = query.charAt(i++);
                    if (c == '/' && !escaped) break;
                    escaped = c == '\\' && !escaped;
                    if (c != '\\') escaped = false;
                }
            } else {
                while (i < query.length() && !Character.isWhitespace(query.charAt(i)) && query.charAt(i) != ',') i++;
            }
            if (i > start) result.add(query.substring(start, i));
        }
        return result;
    }

    public static boolean isRegexToken(String value) {
        if (value == null) return false;
        String token = value.trim();
        if (token.startsWith("re://")) return token.length() > 6 && (token.endsWith("/") || token.endsWith("//"));
        return (token.startsWith("re:") && token.length() > 3)
                || (token.startsWith("/") && token.endsWith("/") && token.length() > 2);
    }

    public static String regexPattern(String value) {
        String token = value == null ? "" : value.trim();
        if (token.startsWith("re://")) {
            if (token.endsWith("//") && token.length() > 7) return token.substring(5, token.length() - 2);
            if (token.endsWith("/") && token.length() > 6) return token.substring(5, token.length() - 1);
        }
        if (token.startsWith("re:")) return token.substring(3);
        return token.length() > 2 ? token.substring(1, token.length() - 1) : token;
    }

    public static String searchableText(PacketCaptureHandler.CapturedPacketData packet) {
        if (packet == null) return "";
        StringBuilder value = new StringBuilder();
        append(value, packet.packetClassName);
        append(value, packet.channel);
        append(value, packet.getHexData());
        append(value, packet.getDecodedData());
        append(value, packet.getDecodedDetailData());
        append(value, packet.getDecodedFullData());
        if (packet.packetId != null) {
            append(value, String.valueOf(packet.packetId));
            append(value, String.format(Locale.ROOT, "0x%02X", packet.packetId));
            append(value, "id:" + packet.packetId);
        }
        return value.toString();
    }

    public static String normalizeHex(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9A-Fa-f]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isHexQuery(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.regionMatches(true, 0, "0x", 0, 2)) text = text.substring(2);
        text = text.replaceAll("[\\s:_-]", "");
        if (text.isEmpty() || (text.length() & 1) != 0) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.digit(c, 16) < 0) return false;
        }
        return true;
    }

    private static void append(StringBuilder target, String value) {
        if (value != null && !value.isEmpty()) target.append(value).append(' ');
    }
}
