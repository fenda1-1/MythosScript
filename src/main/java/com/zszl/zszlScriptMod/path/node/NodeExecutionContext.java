package com.zszl.zszlScriptMod.path.node;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeExecutionContext {

    public static final int DEFAULT_MAX_STEPS = 1024;
    public static final int DEFAULT_MAX_HISTORY = 50;
    public static final int DEFAULT_MAX_CONSOLE_LINES = 200;
    public static final int DEFAULT_MAX_RETRIES = 0;
    public static final long DEFAULT_WATCHDOG_TIMEOUT_MS = 60000L;
    public static final long DEFAULT_TICK_BUDGET_MS = 8L;

    public enum RunMode {
        CONTINUE,
        STEP
    }

    public static final class ExecutionRecord {
        private final long timestampMs;
        private final String nodeId;
        private final String nodeType;
        private final String branch;
        private final String message;

        public ExecutionRecord(long timestampMs, String nodeId, String nodeType, String branch, String message) {
            this.timestampMs = timestampMs;
            this.nodeId = nodeId == null ? "" : nodeId;
            this.nodeType = nodeType == null ? "" : nodeType;
            this.branch = branch == null ? "" : branch;
            this.message = message == null ? "" : message;
        }

        public long getTimestampMs() {
            return timestampMs;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getBranch() {
            return branch;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class ExecutionError {
        private final String nodeId;
        private final String nodeType;
        private final String parameterSummary;
        private final String exceptionClass;
        private final String message;

        public ExecutionError(String nodeId, String nodeType, String parameterSummary, String exceptionClass,
                String message) {
            this.nodeId = nodeId == null ? "" : nodeId;
            this.nodeType = nodeType == null ? "" : nodeType;
            this.parameterSummary = parameterSummary == null ? "" : parameterSummary;
            this.exceptionClass = exceptionClass == null ? "" : exceptionClass;
            this.message = message == null ? "未知错误" : message;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getParameterSummary() {
            return parameterSummary;
        }

        public String getExceptionClass() {
            return exceptionClass;
        }

        public String getMessage() {
            return message;
        }

        public String toDisplayMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append(message);
            if (!nodeId.isEmpty()) {
                sb.append("（节点ID：").append(nodeId).append("）");
            }
            if (!nodeType.isEmpty()) {
                sb.append("（节点类型：").append(NodeNode.getDisplayName(nodeType)).append("）");
            }
            if (!parameterSummary.isEmpty()) {
                sb.append("（参数：").append(parameterSummary).append("）");
            }
            if (!exceptionClass.isEmpty()) {
                sb.append("（异常：").append(exceptionClass).append("）");
            }
            return sb.toString();
        }
    }

    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> scopedVariables = new LinkedHashMap<>();
    private final Map<String, Integer> retryCountByNodeId = new LinkedHashMap<>();
    private final List<ExecutionRecord> history = new ArrayList<>();
    private final List<String> consoleLines = new ArrayList<>();
    private final Set<String> breakpoints = new LinkedHashSet<>();

    private final int maxSteps;
    private int maxHistory;
    private int maxConsoleLines;
    private int maxRetriesPerNode;
    private long watchdogTimeoutMs;
    private long tickBudgetMs;

    private int executedSteps = 0;
    private String currentNodeId = "";
    private String currentNodeType = "";
    private String previousNodeId = "";
    private String previousNodeType = "";
    private String nextNodeId = "";
    private String lastBranch = "";
    private String errorMessage = "";
    private boolean completed = false;
    private boolean waiting = false;
    private boolean paused = false;
    private boolean pausedAtBreakpoint = false;
    private boolean stepRequested = false;
    private long waitStartTimeMs = 0L;
    private long startTimeMs = 0L;
    private long lastTickStartTimeMs = 0L;
    private ExecutionError executionError = null;
    private RunMode runMode = RunMode.CONTINUE;

    public NodeExecutionContext() {
        this(DEFAULT_MAX_STEPS);
    }

    public NodeExecutionContext(int maxSteps) {
        this.maxSteps = Math.max(1, maxSteps);
        this.maxHistory = DEFAULT_MAX_HISTORY;
        this.maxConsoleLines = DEFAULT_MAX_CONSOLE_LINES;
        this.maxRetriesPerNode = DEFAULT_MAX_RETRIES;
        this.watchdogTimeoutMs = DEFAULT_WATCHDOG_TIMEOUT_MS;
        this.tickBudgetMs = DEFAULT_TICK_BUDGET_MS;
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public Object getVariable(String name) {
        return name == null ? null : variables.get(name);
    }

    public Object getScopedVariable(String scope, String name) {
        if (scope == null || name == null) {
            return null;
        }
        Map<String, Object> values = scopedVariables.get(scope.trim());
        return values == null ? null : values.get(name.trim());
    }

    public boolean hasVariable(String name) {
        return name != null && variables.containsKey(name);
    }

    public boolean hasScopedVariable(String scope, String name) {
        if (scope == null || name == null) {
            return false;
        }
        Map<String, Object> values = scopedVariables.get(scope.trim());
        return values != null && values.containsKey(name.trim());
    }

    public void setVariable(String name, Object value) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        variables.put(name.trim(), value);
    }

    public void setScopedVariable(String scope, String name, Object value) {
        if (scope == null || scope.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            return;
        }
        String scopeKey = scope.trim();
        if (!scopedVariables.containsKey(scopeKey)) {
            scopedVariables.put(scopeKey, new LinkedHashMap<String, Object>());
        }
        scopedVariables.get(scopeKey).put(name.trim(), value);
    }

    public void setVariablesFromJson(JsonObject values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            setVariable(entry.getKey(), toJavaValue(entry.getValue()));
        }
    }

    public void setScopedVariablesFromJson(String scope, JsonObject values) {
        if (scope == null || values == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            setScopedVariable(scope, entry.getKey(), toJavaValue(entry.getValue()));
        }
    }

    public Map<String, Map<String, Object>> getScopedVariables() {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : scopedVariables.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<String, Object>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public int getExecutedSteps() {
        return executedSteps;
    }

    public void incrementExecutedSteps() {
        this.executedSteps++;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId == null ? "" : currentNodeId;
    }

    public String getCurrentNodeType() {
        return currentNodeType;
    }

    public void setCurrentNodeType(String currentNodeType) {
        this.currentNodeType = currentNodeType == null ? "" : currentNodeType;
    }

    public String getPreviousNodeId() {
        return previousNodeId;
    }

    public void setPreviousNodeId(String previousNodeId) {
        this.previousNodeId = previousNodeId == null ? "" : previousNodeId;
    }

    public String getPreviousNodeType() {
        return previousNodeType;
    }

    public void setPreviousNodeType(String previousNodeType) {
        this.previousNodeType = previousNodeType == null ? "" : previousNodeType;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(String nextNodeId) {
        this.nextNodeId = nextNodeId == null ? "" : nextNodeId;
    }

    public String getLastBranch() {
        return lastBranch;
    }

    public void setLastBranch(String lastBranch) {
        this.lastBranch = lastBranch == null ? "" : lastBranch;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }

    public ExecutionError getExecutionError() {
        return executionError;
    }

    public void fail(String message) {
        fail(new ExecutionError(currentNodeId, currentNodeType, "", "", message));
    }

    public void fail(ExecutionError executionError) {
        this.executionError = executionError;
        this.errorMessage = executionError == null ? "未知错误" : executionError.toDisplayMessage();
        addConsoleLine("[ERROR] " + this.errorMessage);
        this.waiting = false;
        this.completed = false;
        this.paused = false;
        this.pausedAtBreakpoint = false;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
        addConsoleLine("[DONE] 执行完成");
        this.waiting = false;
        this.paused = false;
        this.pausedAtBreakpoint = false;
    }

    public boolean isWaiting() {
        return waiting;
    }

    public void enterWaiting(long nowMs) {
        this.waiting = true;
        this.waitStartTimeMs = Math.max(0L, nowMs);
    }

    public void clearWaiting() {
        this.waiting = false;
        this.waitStartTimeMs = 0L;
    }

    public long getWaitStartTimeMs() {
        return waitStartTimeMs;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isPausedAtBreakpoint() {
        return pausedAtBreakpoint;
    }

    public void pause() {
        this.paused = true;
        addConsoleLine("[PAUSE] 已暂停");
    }

    public void pauseAtBreakpoint() {
        this.paused = true;
        this.pausedAtBreakpoint = true;
        addConsoleLine("[BREAKPOINT] 命中断点: " + currentNodeId);
    }

    public void resume() {
        this.paused = false;
        this.pausedAtBreakpoint = false;
        this.stepRequested = false;
        this.runMode = RunMode.CONTINUE;
        addConsoleLine("[RESUME] 继续运行");
    }

    public void requestStep() {
        this.stepRequested = true;
        this.paused = false;
        this.pausedAtBreakpoint = false;
        this.runMode = RunMode.STEP;
        addConsoleLine("[STEP] 请求单步执行");
    }

    public boolean isStepRequested() {
        return stepRequested;
    }

    public void consumeStepRequest() {
        this.stepRequested = false;
    }

    public RunMode getRunMode() {
        return runMode;
    }

    public void setRunMode(RunMode runMode) {
        this.runMode = runMode == null ? RunMode.CONTINUE : runMode;
    }

    public List<ExecutionRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
        trimHistory();
    }

    public List<String> getConsoleLines() {
        return Collections.unmodifiableList(consoleLines);
    }

    public int getMaxConsoleLines() {
        return maxConsoleLines;
    }

    public void setMaxConsoleLines(int maxConsoleLines) {
        this.maxConsoleLines = Math.max(20, maxConsoleLines);
        trimConsoleLines();
    }

    public void addConsoleLine(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            return;
        }
        consoleLines.add(text);
        trimConsoleLines();
    }

    public void addHistory(String nodeId, String nodeType, String branch, String message) {
        history.add(new ExecutionRecord(System.currentTimeMillis(), nodeId, nodeType, branch, message));
        trimHistory();
        StringBuilder console = new StringBuilder();
        console.append("[").append(nodeId == null ? "" : nodeId).append("]");
        if (nodeType != null && !nodeType.trim().isEmpty()) {
            console.append(" ").append(NodeNode.getDisplayName(nodeType));
        }
        if (branch != null && !branch.trim().isEmpty()) {
            console.append(" / ").append(branch.trim());
        }
        if (message != null && !message.trim().isEmpty()) {
            console.append(" - ").append(message.trim());
        }
        addConsoleLine(console.toString());
    }

    private void trimHistory() {
        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }

    private void trimConsoleLines() {
        while (consoleLines.size() > maxConsoleLines) {
            consoleLines.remove(0);
        }
    }

    public Set<String> getBreakpoints() {
        return Collections.unmodifiableSet(breakpoints);
    }

    public void addBreakpoint(String nodeId) {
        if (nodeId != null && !nodeId.trim().isEmpty()) {
            breakpoints.add(nodeId.trim());
        }
    }

    public void removeBreakpoint(String nodeId) {
        if (nodeId != null) {
            breakpoints.remove(nodeId.trim());
        }
    }

    public void clearBreakpoints() {
        breakpoints.clear();
    }

    public boolean hasBreakpoint(String nodeId) {
        return nodeId != null && breakpoints.contains(nodeId.trim());
    }

    public int getRetryCount(String nodeId) {
        if (nodeId == null) {
            return 0;
        }
        Integer count = retryCountByNodeId.get(nodeId.trim());
        return count == null ? 0 : count.intValue();
    }

    public int incrementRetryCount(String nodeId) {
        String key = nodeId == null ? "" : nodeId.trim();
        int next = getRetryCount(key) + 1;
        retryCountByNodeId.put(key, next);
        return next;
    }

    public void resetRetryCount(String nodeId) {
        if (nodeId != null) {
            retryCountByNodeId.remove(nodeId.trim());
        }
    }

    public int getMaxRetriesPerNode() {
        return maxRetriesPerNode;
    }

    public void setMaxRetriesPerNode(int maxRetriesPerNode) {
        this.maxRetriesPerNode = Math.max(0, maxRetriesPerNode);
    }

    public long getWatchdogTimeoutMs() {
        return watchdogTimeoutMs;
    }

    public void setWatchdogTimeoutMs(long watchdogTimeoutMs) {
        this.watchdogTimeoutMs = Math.max(0L, watchdogTimeoutMs);
    }

    public long getTickBudgetMs() {
        return tickBudgetMs;
    }

    public void setTickBudgetMs(long tickBudgetMs) {
        this.tickBudgetMs = Math.max(0L, tickBudgetMs);
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void markStarted(long nowMs) {
        if (this.startTimeMs <= 0L) {
            this.startTimeMs = Math.max(0L, nowMs);
        }
        this.lastTickStartTimeMs = Math.max(0L, nowMs);
    }

    public long getLastTickStartTimeMs() {
        return lastTickStartTimeMs;
    }

    public void markTickStart(long nowMs) {
        this.lastTickStartTimeMs = Math.max(0L, nowMs);
    }

    public boolean isWatchdogTimedOut(long nowMs) {
        return watchdogTimeoutMs > 0L && startTimeMs > 0L && nowMs - startTimeMs >= watchdogTimeoutMs;
    }

    public boolean isTickBudgetExceeded(long tickStartMs, long nowMs) {
        return tickBudgetMs > 0L && tickStartMs > 0L && nowMs - tickStartMs >= tickBudgetMs;
    }

    private static Object toJavaValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            return element;
        }

        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            long asLong = (long) value;
            if (Double.compare(value, (double) asLong) == 0) {
                if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
                    return (int) asLong;
                }
                return asLong;
            }
            return value;
        }
        return element.getAsString();
    }
}