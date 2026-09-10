package com.zszl.zszlScriptMod.path.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class NodeAuditLogManager {

    private static final String FILE_NAME = "node_audit_log.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES = 500;

    private NodeAuditLogManager() {
    }

    public static Path getAuditFile() {
        return ProfileManager.getCurrentProfileDir().resolve(FILE_NAME);
    }

    public static synchronized void log(String action, String graphName, String detail) {
        List<AuditEntry> entries = loadInternal();
        entries.add(new AuditEntry(now(), safe(action), safe(graphName), safe(detail)));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        saveInternal(entries);
    }

    public static synchronized List<AuditEntry> loadAll() {
        return Collections.unmodifiableList(loadInternal());
    }

    private static List<AuditEntry> loadInternal() {
        Path file = getAuditFile();
        if (!Files.exists(file)) {
            return new ArrayList<AuditEntry>();
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            AuditRoot root = GSON.fromJson(json, AuditRoot.class);
            if (root == null || root.entries == null) {
                return new ArrayList<AuditEntry>();
            }
            return new ArrayList<AuditEntry>(root.entries);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("读取节点审计日志失败: {}", file, e);
            return new ArrayList<AuditEntry>();
        }
    }

    private static void saveInternal(List<AuditEntry> entries) {
        Path file = getAuditFile();
        try {
            Files.createDirectories(file.getParent());
            AuditRoot root = new AuditRoot();
            root.entries = entries == null ? new ArrayList<AuditEntry>() : new ArrayList<AuditEntry>(entries);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("保存节点审计日志失败: {}", file, e);
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class AuditRoot {
        private List<AuditEntry> entries = new ArrayList<AuditEntry>();
    }

    public static final class AuditEntry {
        private final String timestamp;
        private final String action;
        private final String graphName;
        private final String detail;

        public AuditEntry(String timestamp, String action, String graphName, String detail) {
            this.timestamp = timestamp == null ? "" : timestamp;
            this.action = action == null ? "" : action;
            this.graphName = graphName == null ? "" : graphName;
            this.detail = detail == null ? "" : detail;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getAction() {
            return action;
        }

        public String getGraphName() {
            return graphName;
        }

        public String getDetail() {
            return detail;
        }
    }
}