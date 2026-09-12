package com.zszl.zszlScriptMod.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData;

/** Read-only evaluation: never updates captured IDs or dispatches sequences. */
public final class CapturedIdRulePreview {
    private CapturedIdRulePreview() { }

    public static final class Result {
        public final String message;
        public final String hex;
        public final String origin;

        private Result(String message, String hex, String origin) {
            this.message = message; this.hex = hex; this.origin = origin;
        }
    }

    private static Result message(String text) { return new Result(text, null, null); }

    private static final class Entry {
        final CapturedPacketData packet;
        final boolean outbound;
        final int index;
        Entry(CapturedPacketData packet, boolean outbound, int index) {
            this.packet = packet; this.outbound = outbound; this.index = index;
        }
    }

    public static String evaluate(CapturedIdRuleManager.RuleEditModel rule,
            List<CapturedPacketData> received, List<CapturedPacketData> sent) {
        return inspect(rule, received, sent).message;
    }

    public static Result inspect(CapturedIdRuleManager.RuleEditModel rule,
            List<CapturedPacketData> received, List<CapturedPacketData> sent) {
        try {
            if (rule.pattern == null || rule.pattern.trim().isEmpty()) return message("预览：请填写正则表达式");
            Pattern pattern = Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE);
            boolean triggerOnly = "recapture".equals(rule.updateSequenceMode);
            if (!triggerOnly && (rule.group < 0 || rule.group > pattern.matcher("").groupCount()))
                return message("预览错误：捕获分组不存在 (group=" + rule.group + ")");
            if (!triggerOnly && "decimal-int".equals(rule.valueType)
                    && (rule.byteLength < 1 || rule.byteLength > 8))
                return message("预览错误：十进制整数的字节数必须为 1～8");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < received.size(); i++) entries.add(new Entry(received.get(i), false, i));
            for (int i = 0; i < sent.size(); i++) entries.add(new Entry(sent.get(i), true, i));
            entries.sort(Comparator.comparingLong((Entry e) -> e.packet.getLastTimestamp()).reversed());
            if (entries.isEmpty()) return message("预览：抓包列表为空");
            int eligible = 0;
            for (Entry entry : entries) {
                CapturedPacketData packet = entry.packet;
                String direction = entry.outbound ? "outbound" : "inbound";
                String channel = packet.isFmlPacket ? packet.channel : packet.packetClassName;
                if (!CapturedIdRuleManager.matchesDirection(rule.direction, direction)
                        || !CapturedIdRuleManager.matchesChannel(rule.channel, channel)) continue;
                eligible++;
                String source = packet.getHexData();
                if ("decoded".equals(rule.target)) {
                    boolean owl = packet.isFmlPacket && ("OwlViewChannel".equals(packet.channel)
                            || "OwlControlChannel".equals(packet.channel));
                    source = owl ? PacketPayloadDecoder.decodeForRules(packet.rawData,
                            OwlViewPacketDecoder.decode(packet.channel, packet.rawData)) : packet.getDecodedFullData();
                }
                Matcher match = pattern.matcher(source);
                if (!match.find()) continue;
                String origin = (entry.outbound ? "发送" : "接收") + " #" + (entry.index + 1);
                String state = rule.enabled ? "" : " [规则已停用]";
                if (triggerOnly) return message("历史匹配 " + origin + state + "：此规则为触发模式；预览未执行序列");
                String raw = match.group(rule.group);
                byte[] value = CapturedIdRuleManager.parseValue(raw, rule.valueType, rule.byteLength);
                if (value == null || value.length == 0) return message("预览命中 " + origin + "，但分组无法转换：" + raw);
                if ("hex".equalsIgnoreCase(rule.valueType)) value = CapturedIdRuleManager.applyRuleOffset(value, rule.offset);
                StringBuilder hex = new StringBuilder();
                for (byte b : value) { if (hex.length() > 0) hex.append(' '); hex.append(String.format("%02X", b & 255)); }
                String number = value.length <= 8 ? " | 数值=" + new BigInteger(1, value) : "";
                return new Result("历史预览值" + state + "：" + hex + number + " | 分组=" + raw + " | " + origin,
                        hex.toString(), origin);
            }
            return message("预览：未匹配（频道/方向符合 " + eligible + " 包，共 " + entries.size() + " 包）");
        } catch (RuntimeException e) {
            return message("预览错误：" + e.getMessage());
        }
    }
}
