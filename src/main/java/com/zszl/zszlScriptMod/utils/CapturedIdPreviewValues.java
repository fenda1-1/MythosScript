package com.zszl.zszlScriptMod.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import com.google.gson.Gson;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager.RuleCard;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager.RuleEditModel;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData;

/** Historical values exist only in editor evaluation contexts. */
public final class CapturedIdPreviewValues {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "expression-captured-id-preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final Gson GSON = new Gson();
    private static Map<String, CapturedIdRulePreview.Result> history = Collections.emptyMap();
    private static CompletableFuture<Map<String, CapturedIdRulePreview.Result>> pending;
    private static String rulesKey = "", pendingRulesKey = "";
    private static long packetsKey, pendingPacketsKey, nextCheck;
    private static boolean ready;

    private CapturedIdPreviewValues() { }

    public static final class Lookup implements Function<String, String> {
        private final Map<String, CapturedIdRulePreview.Result> history;
        private final Function<String, String> live;
        private final boolean pending;
        private final Map<String, String> resolved = new LinkedHashMap<>();
        private final Map<String, String> sources = new LinkedHashMap<>();

        public Lookup(Map<String, CapturedIdRulePreview.Result> history, Function<String, String> live, boolean pending) {
            this.history = history; this.live = live; this.pending = pending;
        }

        @Override public String apply(String key) {
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (resolved.containsKey(normalized)) return resolved.get(normalized);
            String value = live.apply(key);
            if (value != null && !value.trim().isEmpty()) {
                sources.put(normalized, "实际保存值 " + value);
            } else {
                CapturedIdRulePreview.Result result = history.get(normalized);
                if (result != null && result.hex != null) {
                    value = result.hex;
                    sources.put(normalized, "历史抓包 " + result.origin + "，HEX=" + value + "（实际未保存）");
                } else {
                    sources.put(normalized, pending ? "历史抓包计算中" : result == null
                            ? "实际未保存，且没有可用的历史匹配值" : result.message);
                    if (pending) throw new IllegalArgumentException("捕获ID " + key + " 的历史抓包预览正在计算");
                }
            }
            resolved.put(normalized, value);
            return value;
        }

        public Map<String, String> sources() { return Collections.unmodifiableMap(sources); }
    }

    public static synchronized Lookup current() {
        long now = System.currentTimeMillis();
        if (now >= nextCheck) {
            nextCheck = now + 250;
            List<RuleEditModel> rules = new ArrayList<>();
            for (RuleCard card : CapturedIdRuleManager.getRuleCards()) rules.add(card.model);
            String nextRulesKey = GSON.toJson(rules);
            List<CapturedPacketData> received = copy(PacketCaptureHandler.capturedReceivedPackets);
            List<CapturedPacketData> sent = copy(PacketCaptureHandler.capturedPackets);
            long nextPacketsKey = fingerprint(received) * 31 + fingerprint(sent);
            if (!nextRulesKey.equals(rulesKey) || (received.isEmpty() && sent.isEmpty())) {
                history = Collections.emptyMap();
                ready = received.isEmpty() && sent.isEmpty();
                packetsKey = nextPacketsKey;
            }
            rulesKey = nextRulesKey;
            if (pending != null && pending.isDone()) {
                if (pendingRulesKey.equals(rulesKey) && (!received.isEmpty() || !sent.isEmpty())) {
                    try { history = pending.join(); } catch (RuntimeException ignored) { history = Collections.emptyMap(); }
                    packetsKey = pendingPacketsKey;
                    ready = true;
                }
                pending = null;
            }
            if (pending == null && (!ready || packetsKey != nextPacketsKey)) {
                pendingRulesKey = rulesKey;
                pendingPacketsKey = nextPacketsKey;
                pending = CompletableFuture.supplyAsync(() -> collect(rules, received, sent), WORKER);
            }
        }
        return new Lookup(history, CapturedIdRuleManager::getCapturedIdHex, !ready || pending != null);
    }

    public static Map<String, CapturedIdRulePreview.Result> collect(List<RuleEditModel> rules,
            List<CapturedPacketData> received, List<CapturedPacketData> sent) {
        Map<String, CapturedIdRulePreview.Result> values = new LinkedHashMap<>();
        for (RuleEditModel rule : rules) {
            if (!rule.enabled || "recapture".equalsIgnoreCase(rule.updateSequenceMode) || rule.name == null) continue;
            CapturedIdRulePreview.Result result = CapturedIdRulePreview.inspect(rule, received, sent);
            values.putIfAbsent(rule.name.trim().toLowerCase(Locale.ROOT), result);
            if (rule.aliasesCsv != null) for (String alias : rule.aliasesCsv.split(",")) {
                if (!alias.trim().isEmpty()) values.putIfAbsent(alias.trim().toLowerCase(Locale.ROOT), result);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static List<CapturedPacketData> copy(List<CapturedPacketData> packets) {
        synchronized (packets) { return new ArrayList<>(packets); }
    }

    private static long fingerprint(List<CapturedPacketData> packets) {
        long hash = packets.size();
        for (CapturedPacketData packet : packets) hash = hash * 31 + System.identityHashCode(packet) + packet.getLastTimestamp();
        return hash;
    }
}
