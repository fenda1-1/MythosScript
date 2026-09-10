package com.zszl.zszlScriptMod.path.node;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NodeGraphValidator {

    private static final String PORT_IN = "in";
    private static final String PORT_NEXT = "next";
    private static final String PORT_TRUE = "true";
    private static final String PORT_FALSE = "false";
    private static final String PORT_TIMEOUT = "timeout";

    private static final Map<String, Set<String>> INPUT_PORTS_BY_TYPE;
    private static final Map<String, Set<String>> OUTPUT_PORTS_BY_TYPE;

    static {
        Map<String, Set<String>> inputPorts = new HashMap<>();
        Map<String, Set<String>> outputPorts = new HashMap<>();

        inputPorts.put(NodeNode.TYPE_START, Collections.<String>emptySet());
        outputPorts.put(NodeNode.TYPE_START, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_ACTION, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_ACTION, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_IF, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_IF, setOf(PORT_TRUE, PORT_FALSE));

        inputPorts.put(NodeNode.TYPE_SET_VAR, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_SET_VAR, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_WAIT_UNTIL, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_WAIT_UNTIL, setOf(PORT_NEXT, PORT_TIMEOUT));

        inputPorts.put(NodeNode.TYPE_END, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_END, Collections.<String>emptySet());

        inputPorts.put(NodeNode.TYPE_TRIGGER, Collections.<String>emptySet());
        outputPorts.put(NodeNode.TYPE_TRIGGER, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_RUN_SEQUENCE, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_RUN_SEQUENCE, setOf(PORT_NEXT, PORT_TRUE, PORT_FALSE));

        inputPorts.put(NodeNode.TYPE_SUBGRAPH, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_SUBGRAPH, setOf(PORT_NEXT, PORT_TRUE, PORT_FALSE));

        inputPorts.put(NodeNode.TYPE_PARALLEL, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_PARALLEL, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_JOIN, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_JOIN, setOf(PORT_NEXT));

        inputPorts.put(NodeNode.TYPE_DELAY_TASK, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_DELAY_TASK, setOf(PORT_NEXT, PORT_TIMEOUT));

        inputPorts.put(NodeNode.TYPE_RESOURCE_LOCK, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_RESOURCE_LOCK, setOf(PORT_NEXT, PORT_FALSE));

        inputPorts.put(NodeNode.TYPE_RESULT_CACHE, setOf(PORT_IN));
        outputPorts.put(NodeNode.TYPE_RESULT_CACHE, setOf(PORT_NEXT, PORT_TRUE, PORT_FALSE));

        INPUT_PORTS_BY_TYPE = Collections.unmodifiableMap(inputPorts);
        OUTPUT_PORTS_BY_TYPE = Collections.unmodifiableMap(outputPorts);
    }

    private NodeGraphValidator() {
    }

    public static ValidationResult validate(NodeGraph graph) {
        ValidationResult result = new ValidationResult();
        if (graph == null) {
            result.addError("图为空");
            return result;
        }

        if (graph.getSchemaVersion() <= 0) {
            result.addError("schemaVersion 无效");
        }

        List<NodeNode> nodes = graph.getNodes() == null ? Collections.<NodeNode>emptyList() : graph.getNodes();
        List<NodeEdge> edges = graph.getEdges() == null ? Collections.<NodeEdge>emptyList() : graph.getEdges();

        if (nodes.isEmpty()) {
            result.addError("图中没有节点");
            return result;
        }

        Map<String, NodeNode> nodeById = new HashMap<>();
        int startCount = 0;
        int triggerCount = 0;
        int endCount = 0;
        List<String> entryNodeIds = new ArrayList<>();

        for (NodeNode node : nodes) {
            if (node == null) {
                result.addError("存在空节点");
                continue;
            }
            String nodeId = safeTrim(node.getId());
            if (nodeId.isEmpty()) {
                result.addError("存在缺少 id 的节点");
                continue;
            }
            if (nodeById.containsKey(nodeId)) {
                result.addError("节点 id 重复: " + nodeId);
                continue;
            }
            nodeById.put(nodeId, node);

            String nodeType = node.getNormalizedType();
            if (!isKnownType(nodeType)) {
                result.addError("节点类型不支持: " + node.getType() + " (id=" + nodeId + ")");
                continue;
            }
            if (NodeNode.TYPE_START.equals(nodeType)) {
                startCount++;
                entryNodeIds.add(nodeId);
            } else if (NodeNode.TYPE_TRIGGER.equals(nodeType)) {
                triggerCount++;
                entryNodeIds.add(nodeId);
            } else if (NodeNode.TYPE_END.equals(nodeType)) {
                endCount++;
            }
        }

        if (startCount > 1) {
            result.addError("Start 节点数量最多为 1，当前为 " + startCount);
        }
        if (startCount <= 0 && triggerCount <= 0) {
            result.addError("至少需要 1 个 Start 或 Trigger 节点作为入口");
        }
        if (endCount <= 0) {
            result.addError("至少需要 1 个 End 节点");
        }

        Set<String> edgeIds = new HashSet<>();
        List<NodeEdge> validEdges = new ArrayList<>();
        for (NodeEdge edge : edges) {
            if (edge == null) {
                result.addError("存在空边");
                continue;
            }
            String edgeId = safeTrim(edge.getId());
            if (edgeId.isEmpty()) {
                result.addError("存在缺少 id 的边");
            } else if (!edgeIds.add(edgeId)) {
                result.addError("边 id 重复: " + edgeId);
            }

            String fromId = safeTrim(edge.getFromNodeId());
            String toId = safeTrim(edge.getToNodeId());
            NodeNode fromNode = nodeById.get(fromId);
            NodeNode toNode = nodeById.get(toId);

            boolean edgeValid = true;
            if (fromNode == null) {
                result.addError("边引用了不存在的起点节点: " + fromId + " (edge=" + edgeId + ")");
                edgeValid = false;
            }
            if (toNode == null) {
                result.addError("边引用了不存在的终点节点: " + toId + " (edge=" + edgeId + ")");
                edgeValid = false;
            }

            String fromPort = normalizePort(edge.getFromPort());
            String toPort = normalizePort(edge.getToPort());
            if (fromPort.isEmpty()) {
                result.addError("边缺少 fromPort: " + edgeId);
                edgeValid = false;
            }
            if (toPort.isEmpty()) {
                result.addError("边缺少 toPort: " + edgeId);
                edgeValid = false;
            }

            if (fromNode != null && !isValidOutputPort(fromNode.getNormalizedType(), fromPort)) {
                result.addError("边输出端口不合法: " + edgeId + " -> " + fromId + "." + fromPort);
                edgeValid = false;
            }
            if (toNode != null && !isValidInputPort(toNode.getNormalizedType(), toPort)) {
                result.addError("边输入端口不合法: " + edgeId + " -> " + toId + "." + toPort);
                edgeValid = false;
            }

            if (edgeValid) {
                validEdges.add(edge);
            }
        }

        for (NodeNode node : nodes) {
            if (node == null) {
                continue;
            }
            validateNodeParameters(node, nodeById, result);
        }

        if (!entryNodeIds.isEmpty() && endCount > 0) {
            if (!hasReachableEnd(entryNodeIds, nodeById, validEdges)) {
                result.addError("从入口节点(Start/Trigger)不可达任何 End 节点");
            }
        }

        return result;
    }

    private static void validateNodeParameters(NodeNode node, Map<String, NodeNode> nodeById, ValidationResult result) {
        String nodeType = node.getNormalizedType();
        JsonObject data = node.getData() == null ? new JsonObject() : node.getData();
        String nodeId = safeTrim(node.getId());

        String nodeLabel = NodeNode.getDisplayName(nodeType);
        if (NodeNode.TYPE_ACTION.equals(nodeType)) {
            String actionType = readString(data, "actionType", "type", "action");
            if (actionType.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少 actionType: " + nodeId);
            }
        } else if (NodeNode.TYPE_TRIGGER.equals(nodeType)) {
            String triggerType = readString(data, "triggerType", "event", "type");
            if (triggerType.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少 triggerType/event/type: " + nodeId);
            }
        } else if (NodeNode.TYPE_IF.equals(nodeType)) {
            validateConditionNode(nodeId, data, nodeLabel, result);
        } else if (NodeNode.TYPE_SET_VAR.equals(nodeType)) {
            String name = readString(data, "name", "var", "key");
            if (name.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少变量名: " + nodeId);
            }
            if (!hasAny(data, "value", "number", "boolean", "string", "fromVar")) {
                result.addError(nodeLabel + " 节点缺少赋值内容: " + nodeId);
            }
        } else if (NodeNode.TYPE_WAIT_UNTIL.equals(nodeType)) {
            validateConditionNode(nodeId, data, nodeLabel, result);
            if (data.has("timeoutMs") && !isNumeric(data.get("timeoutMs"))) {
                result.addError(nodeLabel + " 的 timeoutMs 必须为数字: " + nodeId);
            }
            if (data.has("timeoutTicks") && !isNumeric(data.get("timeoutTicks"))) {
                result.addError(nodeLabel + " 的 timeoutTicks 必须为数字: " + nodeId);
            }
        } else if (NodeNode.TYPE_RUN_SEQUENCE.equals(nodeType) || NodeNode.TYPE_SUBGRAPH.equals(nodeType)) {
            String sequenceName = readString(data, "sequenceName", "graphName", "target");
            if (sequenceName.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少 sequenceName/graphName/target: " + nodeId);
            }
        } else if (NodeNode.TYPE_DELAY_TASK.equals(nodeType)) {
            if (!data.has("delayMs") && !data.has("delayTicks")) {
                result.addError(nodeLabel + " 节点缺少 delayMs/delayTicks: " + nodeId);
            }
        } else if (NodeNode.TYPE_RESOURCE_LOCK.equals(nodeType)) {
            String resourceKey = readString(data, "resourceKey", "lockKey", "key");
            if (resourceKey.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少 resourceKey/lockKey/key: " + nodeId);
            }
        } else if (NodeNode.TYPE_RESULT_CACHE.equals(nodeType)) {
            String cacheKey = readString(data, "cacheKey", "key");
            if (cacheKey.isEmpty()) {
                result.addError(nodeLabel + " 节点缺少 cacheKey/key: " + nodeId);
            }
        }

        validateOnError(nodeId, data, nodeById, result);
        validateRetry(nodeId, data, result);
    }

    private static void validateConditionNode(String nodeId, JsonObject data, String label, ValidationResult result) {
        String expression = readString(data, "expression", "condition");
        if (!expression.isEmpty()) {
            if (!hasBalancedParentheses(expression)) {
                result.addError(label + " 节点表达式括号不平衡: " + nodeId);
            }
            return;
        }

        String operator = readString(data, "operator", "op");
        if (operator.isEmpty()) {
            result.addError(label + " 节点缺少 operator/op: " + nodeId);
        } else if (!isSupportedOperator(operator.trim())) {
            result.addError(label + " 节点 operator 不支持: " + operator + " (id=" + nodeId + ")");
        }

        boolean hasLeft = data.has("left") || data.has("leftVar");
        boolean hasRight = data.has("right") || data.has("rightVar");
        if (!hasLeft || !hasRight) {
            result.addError(label + " 节点缺少 left/right 或 leftVar/rightVar: " + nodeId);
        }
    }

    private static void validateOnError(String nodeId, JsonObject data, Map<String, NodeNode> nodeById,
            ValidationResult result) {
        String onError = normalizePort(readString(data, "onError"));
        if (onError.isEmpty()) {
            return;
        }
        if (!"stop".equals(onError) && !"continue".equals(onError) && !"goto".equals(onError)) {
            result.addError("节点 onError 不支持: " + onError + " (id=" + nodeId + ")");
            return;
        }
        if ("goto".equals(onError)) {
            String gotoNodeId = readString(data, "onErrorGoto", "gotoNodeId");
            if (gotoNodeId.isEmpty()) {
                result.addError("节点 onError=goto 但缺少 onErrorGoto/gotoNodeId: " + nodeId);
            } else if (!nodeById.containsKey(gotoNodeId)) {
                result.addError("节点 onError goto 目标不存在: " + gotoNodeId + " (id=" + nodeId + ")");
            }
        }
    }

    private static void validateRetry(String nodeId, JsonObject data, ValidationResult result) {
        if (data.has("retry") && !isNumeric(data.get("retry"))) {
            result.addError("节点 retry 必须为数字: " + nodeId);
        }
        if (data.has("retryDelayTicks") && !isNumeric(data.get("retryDelayTicks"))) {
            result.addError("节点 retryDelayTicks 必须为数字: " + nodeId);
        }
    }

    public static boolean isKnownType(String type) {
        return INPUT_PORTS_BY_TYPE.containsKey(type);
    }

    public static boolean isValidInputPort(String type, String port) {
        Set<String> ports = INPUT_PORTS_BY_TYPE.get(type);
        return ports != null && ports.contains(port);
    }

    public static boolean isValidOutputPort(String type, String port) {
        Set<String> ports = OUTPUT_PORTS_BY_TYPE.get(type);
        return ports != null && ports.contains(port);
    }

    private static boolean hasReachableEnd(List<String> entryNodeIds, Map<String, NodeNode> nodeById, List<NodeEdge> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (NodeEdge edge : edges) {
            String from = safeTrim(edge.getFromNodeId());
            String to = safeTrim(edge.getToNodeId());
            if (!adjacency.containsKey(from)) {
                adjacency.put(from, new ArrayList<String>());
            }
            adjacency.get(from).add(to);
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String entryId : entryNodeIds) {
            String id = safeTrim(entryId);
            if (!id.isEmpty() && visited.add(id)) {
                queue.add(id);
            }
        }

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            NodeNode node = nodeById.get(nodeId);
            if (node != null && NodeNode.TYPE_END.equals(node.getNormalizedType())) {
                return true;
            }
            List<String> nextList = adjacency.get(nodeId);
            if (nextList == null) {
                continue;
            }
            for (String next : nextList) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static boolean isNumeric(JsonElement element) {
        return element != null && !element.isJsonNull() && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber();
    }

    private static boolean hasAny(JsonObject data, String... keys) {
        if (data == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedOperator(String operator) {
        String value = operator == null ? "" : operator.trim();
        return "==".equals(value)
                || "!=".equals(value)
                || ">".equals(value)
                || "<".equals(value)
                || ">=".equals(value)
                || "<=".equals(value);
    }

    private static boolean hasBalancedParentheses(String text) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inSingle && !inDouble;
    }

    private static String readString(JsonObject data, String... keys) {
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                return data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : data.get(key).toString();
            }
        }
        return "";
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePort(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    public static final class ValidationResult {
        private final List<String> errors = new ArrayList<>();

        private void addError(String message) {
            errors.add(message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }
}