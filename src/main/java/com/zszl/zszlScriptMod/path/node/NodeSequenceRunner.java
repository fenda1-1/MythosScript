package com.zszl.zszlScriptMod.path.node;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import com.zszl.zszlScriptMod.handlers.ItemSpreadHandler;
import com.zszl.zszlScriptMod.handlers.NearbyItemPickupActionHandler;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.entity.EntityPlayerSP;

public class NodeSequenceRunner {

    private static final String PORT_NEXT = "next";
    private static final String PORT_TRUE = "true";
    private static final String PORT_FALSE = "false";
    private static final String PORT_TIMEOUT = "timeout";

    private static final String ON_ERROR_STOP = "stop";
    private static final String ON_ERROR_CONTINUE = "continue";
    private static final String ON_ERROR_GOTO = "goto";

    private static final Map<String, Boolean> RESULT_CACHE = new HashMap<>();
    private static final Map<String, String> RESOURCE_LOCK_OWNERS = new HashMap<>();
    private static final Map<String, Integer> JOIN_COUNTERS = new HashMap<>();
    private static final int MAX_NESTED_GRAPH_CALLS = 24;
    private static final ThreadLocal<List<String>> RUN_CALL_STACK = new ThreadLocal<>();

    private final NodeGraph graph;
    private final NodeExecutionContext context;
    private final Map<String, NodeNode> nodeById = new LinkedHashMap<>();
    private final Map<String, Map<String, NodeEdge>> outgoingEdgeByNodeAndPort = new HashMap<>();
    private String currentNodeId;

    public NodeSequenceRunner(NodeGraph graph) {
        this(graph, new NodeExecutionContext());
    }

    public NodeSequenceRunner(NodeGraph graph, NodeExecutionContext context) {
        this.graph = graph;
        this.context = context == null ? new NodeExecutionContext() : context;
        initialize();
    }

    public NodeExecutionContext getContext() {
        return context;
    }

    public NodeGraph getGraph() {
        return graph;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public boolean isFinished() {
        return context.isCompleted() || context.hasError();
    }

    public boolean run(EntityPlayerSP player) {
        List<String> stack = RUN_CALL_STACK.get();
        boolean isRootCall = false;
        if (stack == null) {
            stack = new ArrayList<>();
            RUN_CALL_STACK.set(stack);
            isRootCall = true;
        }

        String graphKey = resolveGraphKey();
        if (stack.contains(graphKey)) {
            fail("检测到子图递归调用: " + formatStackWithNext(stack, graphKey));
            return false;
        }
        if (stack.size() >= MAX_NESTED_GRAPH_CALLS) {
            fail("子图嵌套层级超过上限(" + MAX_NESTED_GRAPH_CALLS + "): " + formatStackWithNext(stack, graphKey));
            return false;
        }

        stack.add(graphKey);
        try {
            while (!isFinished() && !context.isWaiting() && !context.isPaused()) {
                long tickStart = System.currentTimeMillis();
                tick(player);
                if (context.isTickBudgetExceeded(tickStart, System.currentTimeMillis())) {
                    break;
                }
            }
            return context.isCompleted() && !context.hasError();
        } catch (StackOverflowError error) {
            fail("节点执行发生栈溢出，疑似递归调用过深: " + error.getClass().getSimpleName());
            return false;
        } catch (Throwable throwable) {
            fail("节点执行异常: " + throwable.getClass().getSimpleName() + ": " + safe(throwable.getMessage()));
            return false;
        } finally {
            if (!stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
            if (isRootCall) {
                RUN_CALL_STACK.remove();
            }
        }
    }

    public void tick(EntityPlayerSP player) {
        if (isFinished() || context.isPaused()) {
            return;
        }

        long now = System.currentTimeMillis();
        context.markStarted(now);
        context.markTickStart(now);

        if (context.isWatchdogTimedOut(now)) {
            fail(new NodeExecutionContext.ExecutionError(
                    currentNodeId,
                    context.getCurrentNodeType(),
                    "",
                    "WatchdogTimeout",
                    "执行超时，已被看门狗停止"));
            return;
        }

        if (currentNodeId == null || currentNodeId.trim().isEmpty()) {
            fail("缺少当前节点，无法继续执行");
            return;
        }
        if (context.getExecutedSteps() >= context.getMaxSteps()) {
            fail("执行步数超过上限，已停止以防止死循环");
            return;
        }

        NodeNode currentNode = nodeById.get(currentNodeId);
        if (currentNode == null) {
            fail("当前节点不存在: " + currentNodeId);
            return;
        }

        context.setCurrentNodeId(currentNodeId);
        context.setCurrentNodeType(currentNode.getNormalizedType());

        if (isNodeIgnored(currentNode)) {
            context.incrementExecutedSteps();
            context.clearWaiting();
            context.addHistory(currentNodeId, currentNode.getNormalizedType(), "ignored", "节点已忽略，自动跳过");
            jumpToPortIgnoreMissing(currentNode, PORT_NEXT);
            return;
        }

        if (!context.isStepRequested() && context.hasBreakpoint(currentNodeId)) {
            context.pauseAtBreakpoint();
            context.addHistory(currentNodeId, currentNode.getNormalizedType(), "breakpoint", "命中断点");
            return;
        }

        context.incrementExecutedSteps();

        if (NodeNode.TYPE_WAIT_UNTIL.equals(currentNode.getNormalizedType())) {
            handleWaitUntil(player, currentNode);
        } else {
            context.clearWaiting();
            executeNode(player, currentNode);
        }

        if (!isFinished() && !context.isWaiting() && context.getRunMode() == NodeExecutionContext.RunMode.STEP) {
            context.consumeStepRequest();
            context.pause();
        }
    }

    private void executeNode(EntityPlayerSP player, NodeNode currentNode) {
        String type = currentNode.getNormalizedType();

        if (NodeNode.TYPE_START.equals(type) || NodeNode.TYPE_TRIGGER.equals(type)) {
            String typeLabel = NodeNode.getDisplayName(type);
            context.addHistory(currentNode.getId(), type, PORT_NEXT, "进入" + typeLabel);
            jumpToPort(currentNode, PORT_NEXT, typeLabel);
            return;
        }
        if (NodeNode.TYPE_ACTION.equals(type)) {
            handleAction(player, currentNode);
            return;
        }
        if (NodeNode.TYPE_IF.equals(type)) {
            handleIf(currentNode);
            return;
        }
        if (NodeNode.TYPE_SET_VAR.equals(type)) {
            handleSetVar(currentNode);
            return;
        }
        if (NodeNode.TYPE_WAIT_UNTIL.equals(type)) {
            handleWaitUntil(player, currentNode);
            return;
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(type) || NodeNode.TYPE_SUBGRAPH.equals(type)) {
            handleRunSequenceNode(player, currentNode);
            return;
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(type)) {
            handleDelayTask(currentNode);
            return;
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(type)) {
            handleResourceLock(currentNode);
            return;
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(type)) {
            handleResultCache(currentNode);
            return;
        }
        if (NodeNode.TYPE_PARALLEL.equals(type)) {
            handleParallel(player, currentNode);
            return;
        }
        if (NodeNode.TYPE_JOIN.equals(type)) {
            handleJoin(currentNode);
            return;
        }
        if (NodeNode.TYPE_END.equals(type)) {
            context.addHistory(currentNode.getId(), type, "end", "流程结束");
            context.markCompleted();
            currentNodeId = currentNode.getId();
            return;
        }

        fail("不支持的节点类型: " + currentNode.getType());
    }

    private void handleRunSequenceNode(EntityPlayerSP player, NodeNode node) {
        JsonObject data = safeData(node);
        String graphName = readString(data, "sequenceName", "graphName", "target");
        if (graphName.isEmpty()) {
            handleNodeFailure(node, "子图/序列节点缺少目标图名称", null);
            return;
        }

        NodeGraph targetGraph = findGraphByName(graphName);
        if (targetGraph == null) {
            handleNodeFailure(node, "未找到目标节点图: " + graphName, null);
            return;
        }

        NodeExecutionContext childContext = new NodeExecutionContext(context.getMaxSteps());
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            childContext.setVariable(entry.getKey(), entry.getValue());
        }

        JsonObject args = readObject(data, "args");
        childContext.setVariablesFromJson(args);

        NodeSequenceRunner childRunner = new NodeSequenceRunner(targetGraph, childContext);
        boolean success = childRunner.run(player);

        for (Map.Entry<String, Object> entry : childContext.getVariables().entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        if (success && childContext.isCompleted()) {
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_TRUE, "子图执行成功: " + graphName);
            jumpToPreferredPort(node, PORT_TRUE, PORT_NEXT);
            return;
        }

        if (childContext.hasError()) {
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_FALSE,
                    "子图执行失败: " + childContext.getErrorMessage());
            jumpToPreferredPort(node, PORT_FALSE, PORT_NEXT);
            return;
        }

        jumpToPreferredPort(node, PORT_NEXT, PORT_TRUE);
    }

    private void handleDelayTask(NodeNode node) {
        JsonObject data = safeData(node);
        long now = System.currentTimeMillis();
        if (!context.isWaiting()) {
            context.enterWaiting(now);
        }

        long delayMs = data.has("delayMs")
                ? Math.max(0L, data.get("delayMs").getAsLong())
                : (data.has("delayTicks") ? Math.max(0L, data.get("delayTicks").getAsLong() * 50L) : 0L);

        if (delayMs <= 0L || now - context.getWaitStartTimeMs() >= delayMs) {
            context.clearWaiting();
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "延迟完成");
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
            return;
        }

        long timeoutMs = resolveTimeoutMs(data);
        if (timeoutMs >= 0L && now - context.getWaitStartTimeMs() >= timeoutMs) {
            context.clearWaiting();
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_TIMEOUT, "延迟任务超时");
            jumpToPort(node, PORT_TIMEOUT, NodeNode.getDisplayName(node.getNormalizedType()));
            return;
        }

        context.addHistory(node.getId(), node.getNormalizedType(), "wait", "延迟任务等待中");
    }

    private void handleResourceLock(NodeNode node) {
        JsonObject data = safeData(node);
        String key = readString(data, "resourceKey", "lockKey", "key");
        if (key.isEmpty()) {
            handleNodeFailure(node, "资源锁节点缺少 key", null);
            return;
        }

        String owner = graph == null ? "" : safe(graph.getName()) + ":" + safe(node.getId());
        String currentOwner = RESOURCE_LOCK_OWNERS.get(key);
        if (currentOwner == null || currentOwner.equals(owner)) {
            RESOURCE_LOCK_OWNERS.put(key, owner);
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "获取资源锁: " + key);
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
            return;
        }

        context.addHistory(node.getId(), node.getNormalizedType(), PORT_FALSE, "资源锁被占用: " + key);
        jumpToPreferredPort(node, PORT_FALSE, PORT_NEXT);
    }

    private void handleResultCache(NodeNode node) {
        JsonObject data = safeData(node);
        String cacheKey = readString(data, "cacheKey", "key");
        if (cacheKey.isEmpty()) {
            handleNodeFailure(node, "结果缓存节点缺少 cacheKey", null);
            return;
        }

        if (RESULT_CACHE.containsKey(cacheKey)) {
            context.setVariable(cacheKey, RESULT_CACHE.get(cacheKey));
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_TRUE, "缓存命中: " + cacheKey);
            jumpToPreferredPort(node, PORT_TRUE, PORT_NEXT);
            return;
        }

        boolean cacheValue = data.has("value") && data.get("value").isJsonPrimitive()
                ? data.get("value").getAsBoolean()
                : true;
        RESULT_CACHE.put(cacheKey, cacheValue);
        context.setVariable(cacheKey, cacheValue);
        context.addHistory(node.getId(), node.getNormalizedType(), PORT_FALSE, "缓存写入: " + cacheKey);
        jumpToPreferredPort(node, PORT_FALSE, PORT_NEXT);
    }

    private void handleParallel(EntityPlayerSP player, NodeNode node) {
        JsonObject data = safeData(node);
        JsonObject branches = readObject(data, "branches");
        int started = 0;
        String scope = "parallel:" + safe(node.getId());

        for (Map.Entry<String, JsonElement> entry : branches.entrySet()) {
            String branchName = safe(entry.getKey()).trim().isEmpty() ? ("branch_" + started) : safe(entry.getKey()).trim();
            if (!entry.getValue().isJsonPrimitive()) {
                context.setScopedVariable(scope, branchName, "配置无效");
                continue;
            }
            String graphName = entry.getValue().getAsString();
            NodeGraph target = findGraphByName(graphName);
            if (target == null) {
                context.setScopedVariable(scope, branchName, "未找到子图: " + graphName);
                continue;
            }

            context.setScopedVariable(scope, branchName, "运行中: " + graphName);
            NodeExecutionContext childContext = new NodeExecutionContext(context.getMaxSteps());
            for (Map.Entry<String, Object> variable : context.getVariables().entrySet()) {
                childContext.setVariable(variable.getKey(), variable.getValue());
            }
            childContext.setVariable("triggerSource", context.getVariable("triggerSource"));
            NodeSequenceRunner childRunner = new NodeSequenceRunner(target, childContext);
            boolean success = childRunner.run(player);
            started++;

            if (success && childContext.isCompleted()) {
                context.setScopedVariable(scope, branchName, "完成: " + graphName);
            } else if (childContext.hasError()) {
                context.setScopedVariable(scope, branchName,
                        "失败: " + graphName + " / " + safe(childContext.getErrorMessage()));
            } else if (childContext.isPaused()) {
                context.setScopedVariable(scope, branchName, "暂停: " + graphName);
            } else if (childContext.isWaiting()) {
                context.setScopedVariable(scope, branchName, "等待: " + graphName);
            } else {
                context.setScopedVariable(scope, branchName, "结束: " + graphName);
            }
        }

        context.setVariable(node.getId() + ".parallelCount", started);
        context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "并行分支数: " + started);
        jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
    }

    private void handleJoin(NodeNode node) {
        JsonObject data = safeData(node);
        String joinKey = readString(data, "joinKey", "key");
        if (joinKey.isEmpty()) {
            joinKey = node.getId();
        }
        int required = data.has("required") && data.get("required").isJsonPrimitive()
                && data.get("required").getAsJsonPrimitive().isNumber()
                        ? Math.max(1, data.get("required").getAsInt())
                        : 1;

        int count = JOIN_COUNTERS.containsKey(joinKey) ? JOIN_COUNTERS.get(joinKey).intValue() + 1 : 1;
        JOIN_COUNTERS.put(joinKey, count);

        if (count >= required) {
            JOIN_COUNTERS.remove(joinKey);
            context.setScopedVariable("join", joinKey, "已满足: " + count + "/" + required);
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT,
                    "汇聚条件满足: " + count + "/" + required);
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
            return;
        }

        context.setScopedVariable("join", joinKey, "等待中: " + count + "/" + required);
        context.pause();
        context.addHistory(node.getId(), node.getNormalizedType(), "join-wait",
                "汇聚等待: " + count + "/" + required);
    }

    private NodeGraph findGraphByName(String graphName) {
        NodeSequenceStorage.LoadResult loadResult = NodeSequenceStorage.loadAll();
        if (!loadResult.isCompatible()) {
            return null;
        }
        for (NodeGraph item : loadResult.getSequences()) {
            if (item != null && graphName.equalsIgnoreCase(safe(item.getName()))) {
                return item;
            }
        }
        return null;
    }

    private void jumpToPreferredPort(NodeNode node, String preferredPort, String fallbackPort) {
        NodeEdge preferred = getOutgoingEdge(node.getId(), preferredPort);
        if (preferred != null) {
            jumpToPort(node, preferredPort, node.getNormalizedType());
            return;
        }
        jumpToPort(node, fallbackPort, node.getNormalizedType());
    }

    private void initialize() {
        NodeGraphValidator.ValidationResult validationResult = NodeGraphValidator.validate(graph);
        if (!validationResult.isValid()) {
            fail("节点图校验失败: " + joinErrors(validationResult.getErrors()));
            return;
        }

        String firstTriggerNodeId = null;
        List<NodeNode> nodes = graph.getNodes() == null ? Collections.<NodeNode>emptyList() : graph.getNodes();
        for (NodeNode node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            nodeById.put(node.getId().trim(), node);
            if (NodeNode.TYPE_START.equals(node.getNormalizedType())) {
                currentNodeId = node.getId().trim();
            } else if (firstTriggerNodeId == null && NodeNode.TYPE_TRIGGER.equals(node.getNormalizedType())) {
                firstTriggerNodeId = node.getId().trim();
            }
        }

        List<NodeEdge> edges = graph.getEdges() == null ? Collections.<NodeEdge>emptyList() : graph.getEdges();
        for (NodeEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String fromId = safeTrim(edge.getFromNodeId());
            String fromPort = normalize(edge.getFromPort());
            if (fromId.isEmpty() || fromPort.isEmpty()) {
                continue;
            }
            if (!outgoingEdgeByNodeAndPort.containsKey(fromId)) {
                outgoingEdgeByNodeAndPort.put(fromId, new HashMap<String, NodeEdge>());
            }
            outgoingEdgeByNodeAndPort.get(fromId).put(fromPort, edge);
        }

        JsonObject graphData = new JsonObject();
        graphData.addProperty("graphName", graph == null ? "" : safeTrim(graph.getName()));
        context.setVariablesFromJson(graphData);

        if ((currentNodeId == null || currentNodeId.isEmpty()) && firstTriggerNodeId != null) {
            currentNodeId = firstTriggerNodeId;
            context.setCurrentNodeId(currentNodeId);
        }

        if (currentNodeId == null || currentNodeId.isEmpty()) {
            fail("未找到 Start 或 Trigger 入口节点");
        }
    }

    private void handleAction(EntityPlayerSP player, NodeNode node) {
        JsonObject data = safeData(node);
        String actionType = readString(data, "actionType", "type", "action");
        if (actionType.isEmpty()) {
            handleNodeFailure(node, "动作节点缺少 actionType", null);
            return;
        }

        JsonObject params = readObject(data, "params");
        Consumer<EntityPlayerSP> action = PathSequenceManager.parseAction(actionType, params);
        if (action == null) {
            handleNodeFailure(node, "动作节点解析失败", null);
            return;
        }

        if (handleAsyncAction(player, node, actionType, action)) {
            return;
        }

        try {
            action.accept(player);
            context.resetRetryCount(node.getId());
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "执行动作: " + actionType);
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
        } catch (Exception e) {
            handleNodeFailure(node, "动作节点执行异常", e);
        }
    }

    private boolean handleAsyncAction(EntityPlayerSP player, NodeNode node, String actionType,
            Consumer<EntityPlayerSP> action) {
        String normalized = normalize(actionType);
        if (!"transferitemstowarehouse".equals(normalized)
                && !"move_inventory_items_to_chest_slots".equals(normalized)
                && !"spread_inventory_item".equals(normalized)
                && !"stack_inventory_item".equals(normalized)
                && !"pickup_nearby_items".equals(normalized)
                && !"warehouse_auto_deposit".equals(normalized)) {
            return false;
        }

        String scope = "asyncAction:" + safeTrim(node.getId());
        boolean started = "started".equals(String.valueOf(context.getScopedVariable(scope, "state")));

        if (!started) {
            try {
                action.accept(player);
            } catch (Exception e) {
                handleNodeFailure(node, "动作节点执行异常", e);
                return true;
            }

            if (isAsyncActionRunning(normalized)) {
                context.setScopedVariable(scope, "state", "started");
                context.enterWaiting(System.currentTimeMillis());
                context.addHistory(node.getId(), node.getNormalizedType(), "wait", "等待动作完成: " + actionType);
                return true;
            }

            context.setScopedVariable(scope, "state", "");
            context.resetRetryCount(node.getId());
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "执行动作: " + actionType);
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
            return true;
        }

        if (isAsyncActionRunning(normalized)) {
            context.enterWaiting(System.currentTimeMillis());
            return true;
        }

        context.setScopedVariable(scope, "state", "");
        context.clearWaiting();
        context.resetRetryCount(node.getId());
        context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "执行动作完成: " + actionType);
        jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
        return true;
    }

    private boolean isAsyncActionRunning(String actionType) {
        if ("transferitemstowarehouse".equals(actionType)) {
            return ItemFilterHandler.isWarehouseTransferInProgress();
        }
        if ("move_inventory_items_to_chest_slots".equals(actionType)) {
            return ItemFilterHandler.isWarehouseTransferInProgress();
        }
        if ("spread_inventory_item".equals(actionType)) {
            return ItemSpreadHandler.isSpreadInProgress();
        }
        if ("stack_inventory_item".equals(actionType)) {
            return ItemSpreadHandler.isStackInProgress();
        }
        if ("pickup_nearby_items".equals(actionType)) {
            return NearbyItemPickupActionHandler.isRunning();
        }
        if ("warehouse_auto_deposit".equals(actionType)) {
            return WarehouseEventHandler.isAutoDepositRouteRunning();
        }
        return false;
    }

    private void handleIf(NodeNode node) {
        JsonObject data = safeData(node);
        try {
            boolean result = evaluateCondition(data);
            String branch = result ? PORT_TRUE : PORT_FALSE;
            context.setLastBranch(branch);
            context.addHistory(node.getId(), node.getNormalizedType(), branch, "条件结果: " + result);
            jumpToPort(node, branch, NodeNode.getDisplayName(node.getNormalizedType()));
        } catch (Exception e) {
            handleNodeFailure(node, "条件节点解析失败", e);
        }
    }

    private void handleSetVar(NodeNode node) {
        JsonObject data = safeData(node);
        String name = readString(data, "name", "var", "key");
        if (name.isEmpty()) {
            handleNodeFailure(node, "SetVar 节点缺少变量名", null);
            return;
        }

        try {
            Object value = resolveAssignedValue(data);
            context.setVariable(name, value);
            context.resetRetryCount(node.getId());
            context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT,
                    "设置变量 " + name + "=" + String.valueOf(value));
            jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
        } catch (Exception e) {
            handleNodeFailure(node, "变量节点值解析失败", e);
        }
    }

    private void handleWaitUntil(EntityPlayerSP player, NodeNode node) {
        JsonObject data = safeData(node);
        long now = System.currentTimeMillis();
        if (!context.isWaiting()) {
            context.enterWaiting(now);
        }

        try {
            boolean matched = evaluateCondition(data);
            if (matched) {
                context.clearWaiting();
                context.resetRetryCount(node.getId());
                context.setLastBranch(PORT_NEXT);
                context.addHistory(node.getId(), node.getNormalizedType(), PORT_NEXT, "等待条件满足");
                jumpToPort(node, PORT_NEXT, NodeNode.getDisplayName(node.getNormalizedType()));
                return;
            }

            long timeoutMs = resolveTimeoutMs(data);
            if (timeoutMs >= 0L && now - context.getWaitStartTimeMs() >= timeoutMs) {
                context.clearWaiting();
                context.setLastBranch(PORT_TIMEOUT);
                context.addHistory(node.getId(), node.getNormalizedType(), PORT_TIMEOUT, "等待超时");
                jumpToPort(node, PORT_TIMEOUT, NodeNode.getDisplayName(node.getNormalizedType()));
                return;
            }

            context.enterWaiting(context.getWaitStartTimeMs() > 0L ? context.getWaitStartTimeMs() : now);
            context.addHistory(node.getId(), node.getNormalizedType(), "wait", "等待中");
        } catch (Exception e) {
            handleNodeFailure(node, "等待节点条件解析失败", e);
        }
    }

    private void handleNodeFailure(NodeNode node, String message, Exception exception) {
        JsonObject data = safeData(node);
        int retryLimit = resolveRetryLimit(data);
        int retryDelayTicks = resolveRetryDelayTicks(data);
        int currentRetry = context.getRetryCount(node.getId());

        if (currentRetry < retryLimit) {
            int nextRetry = context.incrementRetryCount(node.getId());
            context.enterWaiting(System.currentTimeMillis());
            context.addHistory(node.getId(), node.getNormalizedType(), "retry",
                    "重试 " + nextRetry + "/" + retryLimit + "，延迟 " + retryDelayTicks + " ticks");
            if (retryDelayTicks > 0) {
                context.enterWaiting(System.currentTimeMillis() - 1L + retryDelayTicks * 50L - retryDelayTicks * 50L);
            }
            return;
        }

        context.resetRetryCount(node.getId());

        String strategy = normalize(readString(data, "onError"));
        if (strategy.isEmpty()) {
            strategy = ON_ERROR_STOP;
        }

        NodeExecutionContext.ExecutionError error = new NodeExecutionContext.ExecutionError(
                node.getId(),
                node.getNormalizedType(),
                summarizeParams(data),
                exception == null ? "" : exception.getClass().getSimpleName(),
                exception == null ? message : message + ": " + safe(exception.getMessage()));

        if (ON_ERROR_CONTINUE.equals(strategy)) {
            context.addHistory(node.getId(), node.getNormalizedType(), "onError:continue", error.toDisplayMessage());
            jumpToPortIgnoreMissing(node, PORT_NEXT);
            return;
        }

        if (ON_ERROR_GOTO.equals(strategy)) {
            String gotoNodeId = readString(data, "onErrorGoto", "gotoNodeId");
            if (!gotoNodeId.isEmpty() && nodeById.containsKey(gotoNodeId)) {
                context.addHistory(node.getId(), node.getNormalizedType(), "onError:goto", error.toDisplayMessage());
                context.setPreviousNodeId(node.getId());
                context.setPreviousNodeType(node.getNormalizedType());
                currentNodeId = gotoNodeId;
                context.setCurrentNodeId(gotoNodeId);
                context.setNextNodeId(gotoNodeId);
                return;
            }
        }

        fail(error);
    }

    private void jumpToPort(NodeNode node, String port, String nodeTypeLabel) {
        NodeEdge edge = getOutgoingEdge(node.getId(), port);
        if (edge == null) {
            fail(nodeTypeLabel + " 节点缺少 " + port + " 输出连线: " + node.getId());
            return;
        }

        String targetId = safeTrim(edge.getToNodeId());
        if (!nodeById.containsKey(targetId)) {
            fail(nodeTypeLabel + " 节点跳转目标不存在: " + node.getId() + " -> " + targetId);
            return;
        }

        context.setPreviousNodeId(node.getId());
        context.setPreviousNodeType(node.getNormalizedType());
        context.setLastBranch(port);
        context.setNextNodeId(targetId);
        currentNodeId = targetId;
        context.setCurrentNodeId(currentNodeId);
    }

    private void jumpToPortIgnoreMissing(NodeNode node, String port) {
        NodeEdge edge = getOutgoingEdge(node.getId(), port);
        if (edge == null) {
            context.setLastBranch(port);
            context.markCompleted();
            currentNodeId = node.getId();
            return;
        }
        String targetId = safeTrim(edge.getToNodeId());
        if (!nodeById.containsKey(targetId)) {
            context.setLastBranch(port);
            context.markCompleted();
            currentNodeId = node.getId();
            return;
        }
        context.setPreviousNodeId(node.getId());
        context.setPreviousNodeType(node.getNormalizedType());
        context.setLastBranch(port);
        context.setNextNodeId(targetId);
        currentNodeId = targetId;
        context.setCurrentNodeId(currentNodeId);
    }

    private NodeEdge getOutgoingEdge(String nodeId, String port) {
        Map<String, NodeEdge> byPort = outgoingEdgeByNodeAndPort.get(safeTrim(nodeId));
        if (byPort == null) {
            return null;
        }
        return byPort.get(normalize(port));
    }

    private boolean evaluateCondition(JsonObject data) {
        String expression = readString(data, "expression", "condition");
        if (!expression.isEmpty()) {
            return new ExpressionParser(expression, data).parseExpression();
        }

        String operator = normalize(readString(data, "operator", "op"));
        Object left = resolveOperand(data, "left", "leftVar");
        Object right = resolveOperand(data, "right", "rightVar");

        if (operator.isEmpty()) {
            operator = "==";
        }

        return evaluateComparison(left, operator, right);
    }

    private boolean evaluateComparison(Object left, String operator, Object right) {
        switch (operator) {
            case "==":
                return compareEquals(left, right);
            case "!=":
                return !compareEquals(left, right);
            case ">":
                return compareNumbers(left, right) > 0;
            case "<":
                return compareNumbers(left, right) < 0;
            case ">=":
                return compareNumbers(left, right) >= 0;
            case "<=":
                return compareNumbers(left, right) <= 0;
            default:
                throw new IllegalArgumentException("不支持的比较操作符: " + operator);
        }
    }

    private Object resolveAssignedValue(JsonObject data) {
        String explicitType = normalize(readString(data, "valueType", "typeHint", "dataType"));
        if (data.has("value")) {
            return parseValueByType(data.get("value"), explicitType);
        }
        if (data.has("number")) {
            return parseValueByType(data.get("number"), "number");
        }
        if (data.has("boolean")) {
            return parseValueByType(data.get("boolean"), "boolean");
        }
        if (data.has("string")) {
            return parseValueByType(data.get("string"), "string");
        }
        if (data.has("fromVar")) {
            return context.getVariable(data.get("fromVar").getAsString());
        }
        return null;
    }

    private Object resolveOperand(JsonObject data, String literalKey, String variableKey) {
        if (data.has(variableKey)) {
            String variableName = safeString(data.get(variableKey));
            return context.getVariable(variableName);
        }
        if (data.has(literalKey)) {
            String typeHint = normalize(readString(data, literalKey + "Type"));
            return parseValueByType(data.get(literalKey), typeHint);
        }
        return null;
    }

    private Object parseValueByType(JsonElement element, String typeHint) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        String normalizedType = normalize(typeHint);
        if ("string".equals(normalizedType)) {
            return safeString(element);
        }
        if ("number".equals(normalizedType) || "int".equals(normalizedType) || "double".equals(normalizedType)) {
            return element.getAsDouble();
        }
        if ("boolean".equals(normalizedType) || "bool".equals(normalizedType)) {
            return parseBoolean(element);
        }

        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                long longValue = (long) value;
                if (Double.compare(value, (double) longValue) == 0) {
                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                        return (int) longValue;
                    }
                    return longValue;
                }
                return value;
            }
            return element.getAsString();
        }

        if (element.isJsonArray() || element.isJsonObject()) {
            return new JsonParser().parse(element.toString());
        }

        return safeString(element);
    }

    private long resolveTimeoutMs(JsonObject data) {
        if (data.has("timeoutMs")) {
            return Math.max(-1L, data.get("timeoutMs").getAsLong());
        }
        if (data.has("timeoutTicks")) {
            return Math.max(-1L, data.get("timeoutTicks").getAsLong() * 50L);
        }
        return -1L;
    }

    private int resolveRetryLimit(JsonObject data) {
        if (data.has("retry")) {
            return Math.max(0, data.get("retry").getAsInt());
        }
        return context.getMaxRetriesPerNode();
    }

    private int resolveRetryDelayTicks(JsonObject data) {
        if (data.has("retryDelayTicks")) {
            return Math.max(0, data.get("retryDelayTicks").getAsInt());
        }
        return 0;
    }

    private int compareNumbers(Object left, Object right) {
        Double leftValue = toNumber(left);
        Double rightValue = toNumber(right);
        if (leftValue == null || rightValue == null) {
            throw new IllegalArgumentException("比较操作需要数字类型");
        }
        return Double.compare(leftValue, rightValue);
    }

    private boolean compareEquals(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        Double leftNumber = toNumber(left);
        Double rightNumber = toNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return Double.compare(leftNumber, rightNumber) == 0;
        }

        if (left instanceof Boolean || right instanceof Boolean) {
            return toBoolean(left) == toBoolean(right);
        }

        return String.valueOf(left).equals(String.valueOf(right));
    }

    private Double toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean parseBoolean(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return Boolean.parseBoolean(safeString(element));
    }

    private boolean isNodeIgnored(NodeNode node) {
        JsonObject data = safeData(node);
        return data.has("ignored")
                && data.get("ignored").isJsonPrimitive()
                && data.get("ignored").getAsJsonPrimitive().isBoolean()
                && data.get("ignored").getAsBoolean();
    }

    private JsonObject safeData(NodeNode node) {
        return node.getData() == null ? new JsonObject() : node.getData();
    }

    private JsonObject readObject(JsonObject data, String key) {
        if (data != null && data.has(key) && data.get(key).isJsonObject()) {
            return data.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private String readString(JsonObject data, String... keys) {
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                return safeString(data.get(key));
            }
        }
        return "";
    }

    private String safeString(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return value.toString();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String joinErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "未知校验错误";
        }
        return String.join("; ", new ArrayList<>(errors));
    }

    private String summarizeParams(JsonObject data) {
        if (data == null || data.entrySet().isEmpty()) {
            return "";
        }
        String text = data.toString();
        return text.length() > 160 ? text.substring(0, 160) + "..." : text;
    }

    private void fail(String message) {
        context.addConsoleLine("[FAIL] " + safe(message));
        context.fail(message);
    }

    private void fail(NodeExecutionContext.ExecutionError error) {
        if (error != null) {
            context.addConsoleLine("[FAIL] " + error.toDisplayMessage());
        }
        context.fail(error);
    }

    private String resolveGraphKey() {
        String name = graph == null ? "" : safeTrim(graph.getName());
        if (!name.isEmpty()) {
            return name.toLowerCase(Locale.ROOT);
        }
        return "<graph@" + System.identityHashCode(graph) + ">";
    }

    private String formatStackWithNext(List<String> stack, String next) {
        List<String> all = new ArrayList<>(stack);
        all.add(next);
        return String.join(" -> ", all);
    }

    private final class ExpressionParser {
        private final String expression;
        private final JsonObject data;
        private int index = 0;

        private ExpressionParser(String expression, JsonObject data) {
            this.expression = expression == null ? "" : expression;
            this.data = data == null ? new JsonObject() : data;
        }

        private boolean parseExpression() {
            boolean result = parseOr();
            skipWhitespace();
            if (index < expression.length()) {
                throw new IllegalArgumentException("表达式存在未解析内容: " + expression.substring(index));
            }
            return result;
        }

        private boolean parseOr() {
            boolean value = parseAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    boolean right = parseAnd();
                    value = value || right;
                } else {
                    return value;
                }
            }
        }

        private boolean parseAnd() {
            boolean value = parsePrimary();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    boolean right = parsePrimary();
                    value = value && right;
                } else {
                    return value;
                }
            }
        }

        private boolean parsePrimary() {
            skipWhitespace();
            if (match("(")) {
                boolean value = parseOr();
                skipWhitespace();
                if (!match(")")) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            Object left = parseValueToken();
            skipWhitespace();
            String operator = parseOperator();
            skipWhitespace();
            Object right = parseValueToken();
            return evaluateComparison(left, operator, right);
        }

        private Object parseValueToken() {
            skipWhitespace();
            if (index >= expression.length()) {
                throw new IllegalArgumentException("表达式缺少值");
            }

            char c = expression.charAt(index);
            if (c == '\'' || c == '"') {
                return parseQuotedString();
            }

            String token = parseToken();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("表达式值为空");
            }

            if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
                return Boolean.parseBoolean(token);
            }

            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
            }

            if (token.startsWith("$")) {
                return context.getVariable(token.substring(1));
            }

            if (data.has(token + "Var")) {
                return context.getVariable(readString(data, token + "Var"));
            }
            if (data.has(token)) {
                return parseValueByType(data.get(token), normalize(readString(data, token + "Type")));
            }

            return context.getVariable(token);
        }

        private String parseQuotedString() {
            char quote = expression.charAt(index++);
            StringBuilder sb = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index++);
                if (c == quote) {
                    return sb.toString();
                }
                sb.append(c);
            }
            throw new IllegalArgumentException("字符串未闭合");
        }

        private String parseOperator() {
            String[] operators = new String[] { "==", "!=", ">=", "<=", ">", "<" };
            for (String operator : operators) {
                if (match(operator)) {
                    return operator;
                }
            }
            throw new IllegalArgumentException("缺少比较操作符");
        }

        private String parseToken() {
            StringBuilder sb = new StringBuilder();
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '&' || c == '|' || c == '=' || c == '!'
                        || c == '>' || c == '<') {
                    break;
                }
                sb.append(c);
                index++;
            }
            return sb.toString().trim();
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }

        private boolean match(String token) {
            if (expression.regionMatches(index, token, 0, token.length())) {
                index += token.length();
                return true;
            }
            return false;
        }
    }
}
