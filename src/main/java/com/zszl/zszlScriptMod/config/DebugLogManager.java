package com.zszl.zszlScriptMod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.zszlScriptMod;

import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Bounded in-memory debug log bus used by the in-game terminal panel.
 *
 * <p>The producers can run on any client thread. The panel receives snapshots
 * only when the monotonically increasing version changes, so the render loop
 * never walks or mutates the live queue.</p>
 */
public final class DebugLogManager {

    public static final int DEFAULT_RETAINED_LOG_COUNT = 3000;
    public static final int MIN_RETAINED_LOG_COUNT = 1000;
    public static final int MAX_RETAINED_LOG_COUNT = 10000;
    private static final int MAX_ENTRY_TEXT_LENGTH = 8192;
    private static final String CONFIG_FILE_NAME = "debug_log_config.json";
    private static final String LOGGER_BRIDGE_NAME = "zszl-debug-terminal";
    private static final String MOD_LOGGER_PREFIX = "com.zszl.zszlScriptMod";
    private static final String CORE_LOGGER_NAME = "ZszlScriptCorePlugin";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static final Object LOGGER_BRIDGE_LOCK = new Object();
    private static final Deque<LogEntry> ENTRIES = new ArrayDeque<>();

    private static boolean loaded;
    private static TerminalLogAppender loggerAppender;
    private static int retainedLogCount = DEFAULT_RETAINED_LOG_COUNT;
    private static long nextId;
    private static long version;
    private static long droppedCount;

    private DebugLogManager() {
    }

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static final class LogEntry {
        private final long id;
        private final long timestamp;
        private final DebugModule module;
        private final Level level;
        private final String message;
        private final boolean forced;
        private final boolean truncated;

        private LogEntry(long id, long timestamp, DebugModule module, Level level, String message, boolean forced,
                boolean truncated) {
            this.id = id;
            this.timestamp = timestamp;
            this.module = module;
            this.level = level == null ? Level.INFO : level;
            this.message = message == null ? "" : message;
            this.forced = forced;
            this.truncated = truncated;
        }

        public long getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public DebugModule getModule() {
            return module;
        }

        public Level getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public boolean isForced() {
            return forced;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    private static final class Store {
        int retainedLogCount = DEFAULT_RETAINED_LOG_COUNT;
    }

    public static void ensureLoaded() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            loaded = true;
            retainedLogCount = readRetainedLogCount();
            trimToLimitLocked();
        }
    }

    public static int getRetainedLogCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return retainedLogCount;
        }
    }

    /** Returns the clamped value that the UI and config file will use. */
    public static int normalizeRetainedLogCount(int value) {
        if (value <= 0) {
            return DEFAULT_RETAINED_LOG_COUNT;
        }
        return Math.max(MIN_RETAINED_LOG_COUNT, Math.min(MAX_RETAINED_LOG_COUNT, value));
    }

    public static void setRetainedLogCount(int value) {
        ensureLoaded();
        int normalized = normalizeRetainedLogCount(value);
        boolean changed;
        synchronized (LOCK) {
            changed = retainedLogCount != normalized;
            retainedLogCount = normalized;
            if (trimToLimitLocked()) {
                changed = true;
            }
            if (changed) {
                version++;
            }
        }
        if (changed) {
            saveConfig();
        }
    }

    public static long getVersion() {
        ensureLoaded();
        synchronized (LOCK) {
            return version;
        }
    }

    /** Appends a normal debug record without routing it to the chat window. */
    public static void append(DebugModule module, Level level, String message) {
        append(module, level, message, false);
    }

    public static void append(DebugModule module, Level level, String message, boolean forced) {
        ensureLoaded();
        appendAt(module, level, message, forced, System.currentTimeMillis());
    }

    /**
     * Adds a non-chat producer to the terminal. This is used by integrations
     * such as the embedded Baritone logger whose output is not tagged with the
     * legacy debug prefix.
     */
    public static void appendExternal(DebugModule module, String message) {
        ensureLoaded();
        String normalized = stripFormatting(message);
        if (normalized.isEmpty()) {
            return;
        }
        appendAt(module, parseLevel(normalized), normalized, false, System.currentTimeMillis());
    }

    /**
     * Attaches a lightweight Log4j appender to this mod's logger namespace.
     * The original Log4j appenders remain active, so latest.log continues to
     * receive the same records while the terminal gets a bounded copy.
     */
    public static void installLoggerBridge() {
        ensureLoaded();
        synchronized (LOGGER_BRIDGE_LOCK) {
            if (loggerAppender != null) {
                return;
            }
            TerminalLogAppender appender = new TerminalLogAppender();
            appender.start();
            boolean attached = attachAppender(LogManager.getLogger(MOD_LOGGER_PREFIX), appender);
            attached |= attachAppender(LogManager.getLogger(CORE_LOGGER_NAME), appender);
            if (attached) {
                loggerAppender = appender;
            } else {
                appender.stop();
            }
        }
    }

    private static void appendAt(DebugModule module, Level level, String message, boolean forced, long timestamp) {
        String safeMessage = message == null ? "" : message;
        if (safeMessage.trim().isEmpty()) {
            return;
        }
        boolean truncated = safeMessage.length() > MAX_ENTRY_TEXT_LENGTH;
        if (truncated) {
            String suffix = "\n… [日志内容过长，内存面板已截断；完整内容仍由游戏日志文件保存]";
            int messageLength = Math.max(0, MAX_ENTRY_TEXT_LENGTH - suffix.length());
            safeMessage = safeMessage.substring(0, messageLength) + suffix;
        }
        synchronized (LOCK) {
            ENTRIES.addLast(new LogEntry(++nextId, timestamp <= 0L ? System.currentTimeMillis() : timestamp, module, level,
                    safeMessage, forced,
                    truncated));
            trimToLimitLocked();
            version++;
        }
    }

    /** Captures legacy client-chat debug messages that bypass ModConfig helpers. */
    public static void appendChatDebug(String message) {
        String normalized = stripFormatting(message);
        if (!isDebugChatMessage(normalized)) {
            return;
        }
        DebugModule module = parseModule(normalized);
        if (module == null) {
            module = moduleForLogger(null, normalized);
        }
        appendAt(module, parseLevel(normalized), normalized, normalized.startsWith("[FORCE DEBUG:"),
                System.currentTimeMillis());
    }

    /** Returns whether a chat component is a legacy debug line. */
    public static boolean isDebugChatMessage(String message) {
        String normalized = stripFormatting(message);
        return isDebugChatMessageNormalized(normalized);
    }

    public static List<LogEntry> snapshot() {
        ensureLoaded();
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
        }
    }

    public static int size() {
        ensureLoaded();
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    public static long getDroppedCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return droppedCount;
        }
    }

    public static void clear() {
        ensureLoaded();
        synchronized (LOCK) {
            if (ENTRIES.isEmpty()) {
                return;
            }
            ENTRIES.clear();
            version++;
        }
    }

    public static void saveConfig() {
        ensureLoadedWithoutReentry();
        int count;
        synchronized (LOCK) {
            count = retainedLogCount;
        }
        try {
            Path file = getConfigFile();
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Store store = new Store();
            store.retainedLogCount = count;
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(store, writer);
            }
        } catch (Exception exception) {
            zszlScriptMod.LOGGER.warn("保存调试日志配置失败", exception);
        }
    }

    private static void ensureLoadedWithoutReentry() {
        synchronized (LOCK) {
            if (!loaded) {
                loaded = true;
                retainedLogCount = readRetainedLogCount();
            }
        }
    }

    private static int readRetainedLogCount() {
        Path file = getConfigFile();
        if (!Files.exists(file)) {
            return DEFAULT_RETAINED_LOG_COUNT;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Store store = GSON.fromJson(reader, Store.class);
            return store == null ? DEFAULT_RETAINED_LOG_COUNT : normalizeRetainedLogCount(store.retainedLogCount);
        } catch (Exception exception) {
            zszlScriptMod.LOGGER.warn("读取调试日志配置失败，使用默认保留条数", exception);
            return DEFAULT_RETAINED_LOG_COUNT;
        }
    }

    private static Path getConfigFile() {
        return Paths.get(ModConfig.CONFIG_DIR, CONFIG_FILE_NAME);
    }

    private static boolean attachAppender(org.apache.logging.log4j.Logger logger, Appender appender) {
        if (!(logger instanceof org.apache.logging.log4j.core.Logger)) {
            return false;
        }
        org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) logger;
        if (!coreLogger.getAppenders().containsKey(appender.getName())) {
            coreLogger.addAppender(appender);
        }
        return true;
    }

    private static void appendLogEvent(LogEvent event) {
        if (event == null) {
            return;
        }
        String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        Throwable throwable = event.getThrown();
        if (throwable == null && event.getMessage() != null) {
            throwable = event.getMessage().getThrowable();
        }
        if (throwable != null) {
            StringWriter stack = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stack));
            if (!message.isEmpty()) {
                message += "\n";
            }
            message += stack.toString();
        }
        if (isMirroredDebugLog(message) || message.startsWith("[CHAT]")) {
            // ModConfig already appends its structured DEBUG records before
            // writing the compatibility line to Log4j. CustomGuiNewChat also
            // logs every visible chat line; neither should be duplicated here.
            return;
        }
        DebugModule module = parseModule(message);
        if (module == null) {
            module = moduleForLogger(event.getLoggerName(), message);
        }
        appendAt(module, mapLevel(event.getLevel()), message,
                message.startsWith("[FORCE DEBUG:"), event.getTimeMillis());
    }

    private static Level mapLevel(org.apache.logging.log4j.Level level) {
        if (level == null) {
            return Level.INFO;
        }
        if (level == org.apache.logging.log4j.Level.FATAL || level == org.apache.logging.log4j.Level.ERROR) {
            return Level.ERROR;
        }
        if (level == org.apache.logging.log4j.Level.WARN) {
            return Level.WARN;
        }
        if (level == org.apache.logging.log4j.Level.DEBUG || level == org.apache.logging.log4j.Level.TRACE) {
            return Level.DEBUG;
        }
        return Level.INFO;
    }

    private static boolean isMirroredDebugLog(String message) {
        return message != null && ((message.startsWith("[DEBUG:") && message.indexOf('|') > 7)
                || (message.startsWith("[FORCE DEBUG:") && message.indexOf(']') > 13));
    }

    private static DebugModule moduleForLogger(String loggerName, String message) {
        String value = ((loggerName == null ? "" : loggerName) + " "
                + (message == null ? "" : message)).toLowerCase(Locale.ROOT);
        if (value.contains("autoeat")) return DebugModule.AUTO_EAT;
        if (value.contains("自动进食")) return DebugModule.AUTO_EAT;
        if (value.contains("autopickup")) return DebugModule.AUTO_PICKUP;
        if (value.contains("自动拾取")) return DebugModule.AUTO_PICKUP;
        if (value.contains("掉落物")) return DebugModule.AUTO_PICKUP;
        if (value.contains("conditionalexecution")) return DebugModule.CONDITIONAL_EXECUTION;
        if (value.contains("条件执行")) return DebugModule.CONDITIONAL_EXECUTION;
        if (value.contains("itemfilter")) return DebugModule.ITEM_FILTER;
        if (value.contains("物品过滤")) return DebugModule.ITEM_FILTER;
        if (value.contains("仓库转移") || value.contains("槽位转移")) return DebugModule.ITEM_FILTER;
        if (value.contains("chest")) return DebugModule.CHEST_ANALYSIS;
        if (value.contains("箱子")) return DebugModule.CHEST_ANALYSIS;
        if (value.contains("warehouse")) return DebugModule.WAREHOUSE_ANALYSIS;
        if (value.contains("仓库")) return DebugModule.WAREHOUSE_ANALYSIS;
        if (value.contains("autoescape") || value.contains("evacuation")) return DebugModule.EVACUATION;
        if (value.contains("撤离")) return DebugModule.EVACUATION;
        if (value.contains("trigger")) return DebugModule.TRIGGER_RULES;
        if (value.contains("触发器")) return DebugModule.TRIGGER_RULES;
        if (value.contains("pathsequence") || value.contains("path.") || value.contains("路径序列")) {
            return DebugModule.PATH_SEQUENCE;
        }
        if (value.contains("killaur") || value.contains("huntorbit")) return DebugModule.KILL_AURA_ORBIT;
        if (value.contains("杀戮绕圈")) return DebugModule.KILL_AURA_ORBIT;
        if (value.contains("baritone") || value.contains("navigation")) return DebugModule.BARITONE;
        if (value.contains("导航")) return DebugModule.BARITONE;
        if (value.contains("ahk")) return DebugModule.AHK_EXECUTION;
        return null;
    }

    private static boolean trimToLimitLocked() {
        boolean trimmed = false;
        while (ENTRIES.size() > retainedLogCount) {
            ENTRIES.removeFirst();
            droppedCount++;
            trimmed = true;
        }
        return trimmed;
    }

    private static String stripFormatting(String value) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(value == null ? "" : value);
        return (stripped == null ? value : stripped).trim();
    }

    private static boolean isDebugChatMessageNormalized(String message) {
        return message != null && (message.startsWith("[调试]") || message.startsWith("[DEBUG]")
                || message.startsWith("[DEBUG:") || message.startsWith("[FORCE DEBUG:")
                || message.startsWith("[高亮调试]"));
    }

    private static Level parseLevel(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("错误") || lower.contains(" error") || lower.contains("error")
                || lower.contains("异常") || lower.contains("exception") || lower.startsWith("[error")) {
            return Level.ERROR;
        }
        if (lower.contains("失败") || lower.contains("failed") || lower.contains("failure")
                || lower.contains("警告") || lower.contains("warning") || lower.startsWith("[warn")) {
            return Level.WARN;
        }
        return isDebugChatMessageNormalized(message) ? Level.DEBUG : Level.INFO;
    }

    private static DebugModule parseModule(String message) {
        int moduleStart;
        if (message.startsWith("[DEBUG:")) {
            moduleStart = 7;
        } else if (message.startsWith("[FORCE DEBUG:")) {
            moduleStart = 13;
        } else {
            return null;
        }
        int separator = message.indexOf('|', moduleStart);
        int end = message.indexOf(']', moduleStart);
        int moduleEnd = separator > moduleStart ? separator : end;
        if (moduleEnd <= moduleStart) {
            return null;
        }
        String token = message.substring(moduleStart, moduleEnd).trim();
        for (DebugModule module : DebugModule.values()) {
            if (module.name().equalsIgnoreCase(token) || module.getDisplayName().equals(token)) {
                return module;
            }
        }
        return null;
    }

    private static final class TerminalLogAppender extends AbstractAppender {

        private TerminalLogAppender() {
            super(LOGGER_BRIDGE_NAME, null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            appendLogEvent(event);
        }
    }
}
