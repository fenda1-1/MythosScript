package com.zszl.zszlScriptMod.path.node;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.zszlScriptMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public final class NodeTriggerManager {

    public static final String TRIGGER_CHAT = "onchat";
    public static final String TRIGGER_PACKET = "onpacket";
    public static final String TRIGGER_GUI_OPEN = "onguiopen";
    public static final String TRIGGER_HP_LOW = "onhplow";
    public static final String TRIGGER_TIMER = "ontimer";
    public static final String TRIGGER_DEATH = "ondeath";
    public static final String TRIGGER_RESPAWN = "onrespawn";
    public static final String TRIGGER_AREA_CHANGED = "onareachanged";
    public static final String TRIGGER_INVENTORY_CHANGED = "oninventorychanged";
    public static final String TRIGGER_INVENTORY_FULL = "oninventoryfull";
    public static final String TRIGGER_ENTITY_NEARBY = "onentitynearby";

    private static final int DEFAULT_MAX_CONCURRENT_RUNS = 8;
    private static final Map<String, Long> lastTriggerTimeByKey = new LinkedHashMap<>();
    private static final List<ActiveRun> activeRuns = new ArrayList<>();
    private static TriggerIndex cachedTriggerIndex = null;

    private NodeTriggerManager() {
    }

    public static synchronized void tick() {
        if (activeRuns.isEmpty()) {
            return;
        }

        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            activeRuns.clear();
            return;
        }

        List<ActiveRun> finished = new ArrayList<>();
        for (ActiveRun run : activeRuns) {
            try {
                run.runner.tick(player);
                if (run.runner.getContext().isCompleted()
                        || run.runner.getContext().hasError()
                        || run.runner.getContext().isPaused()) {
                    finished.add(run);
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("[NodeTrigger] 触发图运行异常: {}", run.graphName, e);
                finished.add(run);
            }
        }
        activeRuns.removeAll(finished);
    }

    public static synchronized TriggerResult trigger(String triggerType, JsonObject eventData) {
        String normalizedType = normalize(triggerType);
        if (normalizedType.isEmpty()) {
            return TriggerResult.ignored("triggerType 为空");
        }

        TriggerIndex triggerIndex = getTriggerIndex();
        if (!triggerIndex.compatible) {
            return TriggerResult.failed("节点图存储不兼容: " + triggerIndex.message);
        }

        List<TriggerBinding> bindings = triggerIndex.bindingsByType.get(normalizedType);
        if (bindings == null || bindings.isEmpty()) {
            return TriggerResult.ignored("无匹配触发图: " + normalizedType);
        }

        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return TriggerResult.failed("当前无玩家实例");
        }

        String searchText = buildSearchText(eventData);
        int matched = 0;
        int started = 0;
        for (TriggerBinding binding : bindings) {
            if (binding == null || !binding.matches(eventData, searchText)) {
                continue;
            }
            matched++;
            if (activeRuns.size() >= DEFAULT_MAX_CONCURRENT_RUNS) {
                break;
            }
            if (!allowTrigger(binding)) {
                continue;
            }

            NodeExecutionContext context = new NodeExecutionContext();
            if (eventData != null) {
                context.setVariablesFromJson(eventData);
            }
            context.setVariable("triggerType", normalizedType);
            context.setVariable("triggerSource", buildTriggerSource(normalizedType, eventData));

            NodeSequenceRunner runner = new NodeSequenceRunner(binding.graph, context);
            activeRuns.add(new ActiveRun(binding.graph.getName(), normalizedType, runner));
            rememberTrigger(binding.graph, normalizedType);
            started++;
        }

        if (matched <= 0) {
            return TriggerResult.ignored("无匹配触发图: " + normalizedType);
        }
        if (started <= 0) {
            return TriggerResult.ignored("触发被节流或并发上限阻止");
        }
        return TriggerResult.started(started, matched);
    }

    public static synchronized boolean hasGraphsForTrigger(String triggerType) {
        String normalizedType = normalize(triggerType);
        if (normalizedType.isEmpty()) {
            return false;
        }
        TriggerIndex triggerIndex = getTriggerIndex();
        if (!triggerIndex.compatible) {
            return false;
        }
        List<TriggerBinding> bindings = triggerIndex.bindingsByType.get(normalizedType);
        return bindings != null && !bindings.isEmpty();
    }

    public static synchronized List<String> getActiveRunSummaries() {
        if (activeRuns.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (ActiveRun run : activeRuns) {
            NodeExecutionContext context = run.runner.getContext();
            lines.add(run.graphName + " | " + run.triggerType + " | current=" + context.getCurrentNodeId());
        }
        return lines;
    }

    private static boolean allowTrigger(TriggerBinding binding) {
        if (binding == null || binding.graph == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = buildTriggerKey(binding.graph, binding.triggerType);
        long throttleMs = binding.throttleMs;
        Long last = lastTriggerTimeByKey.get(key);
        return last == null || throttleMs <= 0L || now - last.longValue() >= throttleMs;
    }

    private static void rememberTrigger(NodeGraph graph, String triggerType) {
        lastTriggerTimeByKey.put(buildTriggerKey(graph, triggerType), System.currentTimeMillis());
    }

    private static String buildTriggerKey(NodeGraph graph, String triggerType) {
        return (graph == null ? "" : safe(graph.getName())) + "|" + normalize(triggerType);
    }

    private static String buildTriggerSource(String triggerType, JsonObject eventData) {
        String normalized = normalize(triggerType);
        String detail = readString(eventData, "message", "packet", "gui", "text", "source");
        if (detail.length() > 64) {
            detail = detail.substring(0, 64) + "...";
        }
        if (detail.isEmpty()) {
            return normalized;
        }
        return normalized + ": " + detail;
    }

    private static String readString(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : object.get(key).toString();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static TriggerIndex getTriggerIndex() {
        NodeSequenceStorage.LoadResult loadResult = NodeSequenceStorage.peekCachedLoadResult();
        if (loadResult == null) {
            loadResult = NodeSequenceStorage.loadAll();
        }
        if (cachedTriggerIndex != null && cachedTriggerIndex.loadResult == loadResult) {
            return cachedTriggerIndex;
        }

        if (!loadResult.isCompatible()) {
            cachedTriggerIndex = new TriggerIndex(loadResult, Collections.<String, List<TriggerBinding>>emptyMap(),
                    false, loadResult.getMessage());
            return cachedTriggerIndex;
        }

        Map<String, List<TriggerBinding>> bindingsByType = new HashMap<>();
        for (NodeGraph graph : loadResult.getSequences()) {
            if (graph == null || graph.getNodes() == null) {
                continue;
            }
            for (NodeNode node : graph.getNodes()) {
                if (node == null || !NodeNode.TYPE_TRIGGER.equals(node.getNormalizedType())) {
                    continue;
                }
                JsonObject data = node.getData() == null ? new JsonObject() : node.getData();
                String nodeTriggerType = normalize(readString(data, "triggerType", "event", "type"));
                if (nodeTriggerType.isEmpty()) {
                    continue;
                }
                bindingsByType.computeIfAbsent(nodeTriggerType, key -> new ArrayList<>())
                        .add(new TriggerBinding(graph, nodeTriggerType, data));
            }
        }
        for (Map.Entry<String, List<TriggerBinding>> entry : bindingsByType.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        cachedTriggerIndex = new TriggerIndex(loadResult, Collections.unmodifiableMap(bindingsByType), true, "");
        return cachedTriggerIndex;
    }

    private static String buildSearchText(JsonObject eventData) {
        if (eventData == null || eventData.entrySet().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : eventData.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static final class ActiveRun {
        private final String graphName;
        private final String triggerType;
        private final NodeSequenceRunner runner;

        private ActiveRun(String graphName, String triggerType, NodeSequenceRunner runner) {
            this.graphName = graphName == null ? "" : graphName;
            this.triggerType = triggerType == null ? "" : triggerType;
            this.runner = runner;
        }
    }

    private static final class TriggerBinding {
        private final NodeGraph graph;
        private final String triggerType;
        private final String containsLower;
        private final long throttleMs;

        private TriggerBinding(NodeGraph graph, String triggerType, JsonObject data) {
            this.graph = graph;
            this.triggerType = triggerType == null ? "" : triggerType;
            this.containsLower = readString(data, "contains", "filter", "keyword").trim().toLowerCase(Locale.ROOT);
            this.throttleMs = data != null
                    && data.has("throttleMs")
                    && data.get("throttleMs").isJsonPrimitive()
                    && data.get("throttleMs").getAsJsonPrimitive().isNumber()
                            ? Math.max(0L, data.get("throttleMs").getAsLong())
                            : 0L;
        }

        private boolean matches(JsonObject eventData, String searchText) {
            if (this.containsLower.isEmpty()) {
                return true;
            }
            String text = searchText == null ? buildSearchText(eventData) : searchText;
            return !text.isEmpty() && text.toLowerCase(Locale.ROOT).contains(this.containsLower);
        }
    }

    private static final class TriggerIndex {
        private final NodeSequenceStorage.LoadResult loadResult;
        private final Map<String, List<TriggerBinding>> bindingsByType;
        private final boolean compatible;
        private final String message;

        private TriggerIndex(NodeSequenceStorage.LoadResult loadResult, Map<String, List<TriggerBinding>> bindingsByType,
                boolean compatible, String message) {
            this.loadResult = loadResult;
            this.bindingsByType = bindingsByType == null
                    ? Collections.<String, List<TriggerBinding>>emptyMap()
                    : bindingsByType;
            this.compatible = compatible;
            this.message = message == null ? "" : message;
        }
    }

    public static final class TriggerResult {
        private final boolean started;
        private final String message;
        private final int startedCount;
        private final int matchedCount;

        private TriggerResult(boolean started, String message, int startedCount, int matchedCount) {
            this.started = started;
            this.message = message == null ? "" : message;
            this.startedCount = startedCount;
            this.matchedCount = matchedCount;
        }

        public static TriggerResult started(int startedCount, int matchedCount) {
            return new TriggerResult(true, "started", startedCount, matchedCount);
        }

        public static TriggerResult ignored(String message) {
            return new TriggerResult(false, message, 0, 0);
        }

        public static TriggerResult failed(String message) {
            return new TriggerResult(false, message, 0, 0);
        }

        public boolean isStarted() {
            return started;
        }

        public String getMessage() {
            return message;
        }

        public int getStartedCount() {
            return startedCount;
        }

        public int getMatchedCount() {
            return matchedCount;
        }
    }
}
