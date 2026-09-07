package com.zszl.zszlScriptMod.path.runtime.log;

import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ExecutionLogManager {

    private static final int MAX_SESSIONS = 48;
    private static final int MAX_EVENTS_PER_SESSION = 400;
    private static final List<ExecutionSession> SESSIONS = new ArrayList<>();
    private static final SimpleDateFormat FILE_TIME = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);
    private static final SimpleDateFormat DISPLAY_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    public static final class ExecutionEvent {
        private final long timestamp;
        private final String type;
        private final int stepIndex;
        private final int actionIndex;
        private final String message;
        private final String status;
        private final Map<String, String> variablePreview;

        private ExecutionEvent(long timestamp, String type, int stepIndex, int actionIndex, String message,
                String status, Map<String, String> variablePreview) {
            this.timestamp = timestamp;
            this.type = safe(type);
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.message = safe(message);
            this.status = safe(status);
            this.variablePreview = variablePreview == null
                    ? Collections.emptyMap()
                    : new LinkedHashMap<>(variablePreview);
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getType() {
            return type;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public int getActionIndex() {
            return actionIndex;
        }

        public String getMessage() {
            return message;
        }

        public String getStatus() {
            return status;
        }

        public Map<String, String> getVariablePreview() {
            return new LinkedHashMap<>(variablePreview);
        }
    }

    public static final class SessionSnapshot {
        private final String sessionId;
        private final String sequenceName;
        private final boolean background;
        private final long startTime;
        private final long endTime;
        private final boolean success;
        private final String finishReason;
        private final String finalStatus;
        private final Map<String, String> initialVariables;
        private final List<ExecutionEvent> events;

        private SessionSnapshot(ExecutionSession session) {
            this.sessionId = session.sessionId;
            this.sequenceName = session.sequenceName;
            this.background = session.background;
            this.startTime = session.startTime;
            this.endTime = session.endTime;
            this.success = session.success;
            this.finishReason = session.finishReason;
            this.finalStatus = session.finalStatus;
            this.initialVariables = new LinkedHashMap<>(session.initialVariables);
            this.events = new ArrayList<>(session.events);
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public boolean isBackground() {
            return background;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public boolean isFinished() {
            return endTime > 0L;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public String getFinalStatus() {
            return finalStatus;
        }

        public long getDurationMs() {
            long effectiveEnd = endTime > 0L ? endTime : System.currentTimeMillis();
            return Math.max(0L, effectiveEnd - startTime);
        }

        public Map<String, String> getInitialVariables() {
            return new LinkedHashMap<>(initialVariables);
        }

        public List<ExecutionEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public String buildSummary() {
            String mode = background ? "后台" : "前台";
            String result;
            if (!isFinished()) {
                result = "运行中";
            } else {
                result = success ? "成功" : "停止";
            }
            return sequenceName + " / " + mode + " / " + result + " / " + events.size() + " 条";
        }
    }

    private static final class ExecutionSession {
        private final String sessionId;
        private final String sequenceName;
        private final boolean background;
        private final long startTime;
        private final Map<String, String> initialVariables = new LinkedHashMap<>();
        private final List<ExecutionEvent> events = new ArrayList<>();
        private long endTime = 0L;
        private boolean success = false;
        private String finishReason = "";
        private String finalStatus = "";

        private ExecutionSession(String sessionId, String sequenceName, boolean background, long startTime,
                Map<String, String> initialVariables) {
            this.sessionId = sessionId;
            this.sequenceName = safe(sequenceName);
            this.background = background;
            this.startTime = startTime;
            if (initialVariables != null) {
                this.initialVariables.putAll(initialVariables);
            }
        }
    }

    private ExecutionLogManager() {
    }

    public static synchronized String startSession(String sequenceName, boolean background,
            Map<String, String> initialVariables) {
        String sessionId = UUID.randomUUID().toString();
        ExecutionSession session = new ExecutionSession(sessionId, sequenceName, background,
                System.currentTimeMillis(), initialVariables);
        SESSIONS.add(0, session);
        trimSessions();
        return sessionId;
    }

    public static synchronized void appendEvent(String sessionId, String type, int stepIndex, int actionIndex,
            String message, String status, Map<String, String> variablePreview) {
        ExecutionSession session = findSession(sessionId);
        if (session == null) {
            return;
        }
        session.events.add(new ExecutionEvent(System.currentTimeMillis(), type, stepIndex, actionIndex, message, status,
                variablePreview));
        while (session.events.size() > MAX_EVENTS_PER_SESSION) {
            session.events.remove(0);
        }
        session.finalStatus = safe(status);
    }

    public static synchronized void finishSession(String sessionId, boolean success, String finishReason,
            String finalStatus, Map<String, String> variablePreview) {
        ExecutionSession session = findSession(sessionId);
        if (session == null || session.endTime > 0L) {
            return;
        }
        session.endTime = System.currentTimeMillis();
        session.success = success;
        session.finishReason = safe(finishReason);
        session.finalStatus = safe(finalStatus);
        session.events.add(new ExecutionEvent(session.endTime, "finish", -1, -1,
                safe(finishReason).isEmpty() ? (success ? "执行完成" : "执行停止") : finishReason,
                finalStatus, variablePreview));
        while (session.events.size() > MAX_EVENTS_PER_SESSION) {
            session.events.remove(0);
        }
    }

    public static synchronized List<SessionSnapshot> getSessionsSnapshot() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (ExecutionSession session : SESSIONS) {
            snapshots.add(new SessionSnapshot(session));
        }
        return snapshots;
    }

    public static synchronized void clearSessions() {
        SESSIONS.clear();
    }

    public static synchronized String getSessionText(String sessionId) {
        ExecutionSession session = findSession(sessionId);
        return session == null ? "" : buildSessionText(session);
    }

    public static synchronized Path exportSession(String sessionId) {
        ExecutionSession session = findSession(sessionId);
        if (session == null) {
            return null;
        }

        try {
            Path dir = ProfileManager.getCurrentProfileDir().resolve("execution_logs");
            Files.createDirectories(dir);
            String sequencePart = sanitizeFileName(session.sequenceName.isEmpty() ? "sequence" : session.sequenceName);
            Path file = dir.resolve(FILE_TIME.format(new Date(session.startTime)) + "_" + sequencePart + ".txt");
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(buildSessionText(session));
            }
            return file;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[ExecutionLog] 导出会话失败: {}", sessionId, e);
            return null;
        }
    }

    private static String buildSessionText(ExecutionSession session) {
        if (session == null) {
            return "";
        }

        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder(2048);
        appendLine(builder, "会话ID: " + session.sessionId, lineSeparator);
        appendLine(builder, "序列: " + session.sequenceName, lineSeparator);
        appendLine(builder, "模式: " + (session.background ? "后台" : "前台"), lineSeparator);
        appendLine(builder, "开始时间: " + DISPLAY_TIME.format(new Date(session.startTime)), lineSeparator);
        appendLine(builder, "结束时间: "
                + (session.endTime > 0L ? DISPLAY_TIME.format(new Date(session.endTime)) : "(运行中)"), lineSeparator);
        appendLine(builder, "结果: "
                + (session.endTime <= 0L ? "运行中" : (session.success ? "成功" : "停止/失败")), lineSeparator);
        appendLine(builder, "结束原因: " + safe(session.finishReason), lineSeparator);
        appendLine(builder, "最终状态: " + safe(session.finalStatus), lineSeparator);
        builder.append(lineSeparator);

        appendLine(builder, "初始变量:", lineSeparator);
        if (session.initialVariables.isEmpty()) {
            appendLine(builder, "  (无)", lineSeparator);
        } else {
            for (Map.Entry<String, String> entry : session.initialVariables.entrySet()) {
                appendLine(builder, "  " + entry.getKey() + " = " + entry.getValue(), lineSeparator);
            }
        }

        builder.append(lineSeparator);
        appendLine(builder, "事件:", lineSeparator);
        for (ExecutionEvent event : session.events) {
            builder.append("[").append(DISPLAY_TIME.format(new Date(event.timestamp))).append("] ")
                    .append(event.type.toUpperCase(Locale.ROOT));
            if (event.stepIndex >= 0) {
                builder.append(" step=").append(event.stepIndex);
            }
            if (event.actionIndex >= 0) {
                builder.append(" action=").append(event.actionIndex);
            }
            builder.append(lineSeparator);
            appendLine(builder, "  message: " + event.message, lineSeparator);
            if (!event.status.isEmpty()) {
                appendLine(builder, "  status: " + event.status, lineSeparator);
            }
            if (!event.variablePreview.isEmpty()) {
                appendLine(builder, "  vars: " + joinVariables(event.variablePreview), lineSeparator);
            }
        }
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String line, String lineSeparator) {
        builder.append(line).append(lineSeparator);
    }

    private static String joinVariables(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(" ; ", parts);
    }

    private static ExecutionSession findSession(String sessionId) {
        String key = safe(sessionId);
        if (key.isEmpty()) {
            return null;
        }
        for (ExecutionSession session : SESSIONS) {
            if (session != null && key.equals(session.sessionId)) {
                return session;
            }
        }
        return null;
    }

    private static void trimSessions() {
        while (SESSIONS.size() > MAX_SESSIONS) {
            SESSIONS.remove(SESSIONS.size() - 1);
        }
    }

    private static String sanitizeFileName(String value) {
        String sanitized = safe(value).replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        return sanitized.isEmpty() ? "sequence" : sanitized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
