package com.zszl.zszlScriptMod.path.node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NodeSequenceStorage {

    public static final int CURRENT_VERSION = 3;
    private static final int MIN_SUPPORTED_VERSION = 1;
    private static final String FILE_NAME = "node_sequences.json";
    private static final String BACKUP_FILE_NAME = "node_sequences.json.bak";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path cachedLoadPath = null;
    private static boolean cachedLoadExists = false;
    private static long cachedLoadLastModified = Long.MIN_VALUE;
    private static long cachedLoadSize = Long.MIN_VALUE;
    private static LoadResult cachedLoadResult = null;

    private NodeSequenceStorage() {
    }

    public static Path getStorageFile() {
        return ProfileManager.getCurrentProfileDir().resolve(FILE_NAME);
    }

    public static Path getBackupFile() {
        return ProfileManager.getCurrentProfileDir().resolve(BACKUP_FILE_NAME);
    }

    public static synchronized LoadResult loadAll() {
        Path file = getStorageFile();
        boolean exists = Files.exists(file);
        long lastModified = exists ? getLastModifiedMillis(file) : Long.MIN_VALUE;
        long size = exists ? getFileSize(file) : Long.MIN_VALUE;
        if (isCacheValid(file, exists, lastModified, size)) {
            return cachedLoadResult;
        }
        if (!exists) {
            LoadResult result = LoadResult.success(CURRENT_VERSION, new ArrayList<NodeGraph>());
            updateCache(file, false, lastModified, size, result);
            return result;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (!isCompatibleVersion(version)) {
                LoadResult result = LoadResult.incompatible(version, "不支持的 node_sequences.json 版本: " + version);
                updateCache(file, true, lastModified, size, result);
                return result;
            }

            StorageRoot storageRoot = GSON.fromJson(root, StorageRoot.class);
            List<NodeGraph> graphs = storageRoot == null || storageRoot.sequences == null
                    ? new ArrayList<NodeGraph>()
                    : new ArrayList<NodeGraph>(storageRoot.sequences);

            MigrationResult migrationResult = migrateIfNeeded(version, graphs);
            if (migrationResult.migrated) {
                zszlScriptMod.LOGGER.info("已加载并迁移节点序列: {} ({})，version={} -> {}",
                        migrationResult.graphs.size(), file.getFileName(), version, CURRENT_VERSION);
            } else {
                zszlScriptMod.LOGGER.debug("已加载节点序列: {} ({})，version={}，migrated=false",
                        migrationResult.graphs.size(), file.getFileName(), version);
            }

            LoadResult result = LoadResult.success(
                    migrationResult.migrated ? CURRENT_VERSION : version,
                    migrationResult.graphs,
                    migrationResult.migrated,
                    migrationResult.message);
            updateCache(file, true, lastModified, size, result);
            return result;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("读取节点序列失败: {}", file, e);
            LoadResult result = LoadResult.incompatible(-1, "读取 node_sequences.json 失败: " + e.getMessage());
            updateCache(file, true, lastModified, size, result);
            return result;
        }
    }

    public static synchronized LoadResult peekCachedLoadResult() {
        return cachedLoadResult;
    }

    public static synchronized void saveAll(List<NodeGraph> graphs) throws IOException {
        Path file = getStorageFile();
        Path backup = getBackupFile();
        Path tempFile = file.resolveSibling(file.getFileName().toString() + ".tmp");

        Files.createDirectories(file.getParent());

        List<NodeGraph> normalizedGraphs = graphs == null
                ? new ArrayList<NodeGraph>()
                : new ArrayList<NodeGraph>(graphs);
        for (NodeGraph graph : normalizedGraphs) {
            normalizeGraph(graph);
        }

        StorageRoot root = new StorageRoot();
        root.version = CURRENT_VERSION;
        root.sequences = normalizedGraphs;

        if (Files.exists(file)) {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            updateCache(file, true, getLastModifiedMillis(file), getFileSize(file),
                    LoadResult.success(CURRENT_VERSION, new ArrayList<NodeGraph>(normalizedGraphs)));
            zszlScriptMod.LOGGER.info("已保存节点序列: {} ({})", root.sequences.size(), file.getFileName());
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存节点序列失败，尝试回滚: {}", file, e);
            rollbackFromBackup(file, backup, tempFile);
            cachedLoadResult = null;
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("保存 node_sequences.json 失败: " + e.getMessage(), e);
        }
    }

    public static boolean isCompatibleVersion(int version) {
        return version >= MIN_SUPPORTED_VERSION && version <= CURRENT_VERSION;
    }

    private static void rollbackFromBackup(Path file, Path backup, Path tempFile) {
        try {
            if (Files.exists(tempFile)) {
                Files.delete(tempFile);
            }
            if (Files.exists(backup)) {
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
                zszlScriptMod.LOGGER.warn("已从备份回滚节点序列文件: {}", backup.getFileName());
            }
        } catch (Exception rollbackError) {
            zszlScriptMod.LOGGER.error("节点序列回滚失败", rollbackError);
        }
    }

    private static MigrationResult migrateIfNeeded(int version, List<NodeGraph> graphs) {
        List<NodeGraph> migrated = new ArrayList<NodeGraph>();
        boolean changed = false;

        for (NodeGraph graph : graphs) {
            NodeGraph normalized = normalizeGraph(graph);
            if (normalized.getSchemaVersion() != NodeGraph.CURRENT_SCHEMA_VERSION || version < CURRENT_VERSION) {
                normalized.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);
                changed = true;
            }
            migrated.add(normalized);
        }

        String message = changed ? "已自动迁移到 schemaVersion=3" : "";
        return new MigrationResult(migrated, changed, message);
    }

    private static NodeGraph normalizeGraph(NodeGraph graph) {
        if (graph == null) {
            NodeGraph empty = new NodeGraph();
            empty.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);
            return empty;
        }
        graph.setSchemaVersion(
                graph.getSchemaVersion() <= 0 ? NodeGraph.CURRENT_SCHEMA_VERSION : graph.getSchemaVersion());
        if (graph.getNodes() == null) {
            graph.setNodes(new ArrayList<NodeNode>());
        }
        if (graph.getEdges() == null) {
            graph.setEdges(new ArrayList<NodeEdge>());
        }
        if (graph.getNotes() == null) {
            graph.setNotes(new ArrayList<NodeCanvasNote>());
        }
        if (graph.getGroups() == null) {
            graph.setGroups(new ArrayList<NodeGroupBox>());
        }
        return graph;
    }

    private static boolean isCacheValid(Path file, boolean exists, long lastModified, long size) {
        return cachedLoadResult != null
                && cachedLoadPath != null
                && cachedLoadPath.equals(file)
                && cachedLoadExists == exists
                && cachedLoadLastModified == lastModified
                && cachedLoadSize == size;
    }

    private static void updateCache(Path file, boolean exists, long lastModified, long size, LoadResult result) {
        cachedLoadPath = file;
        cachedLoadExists = exists;
        cachedLoadLastModified = lastModified;
        cachedLoadSize = size;
        cachedLoadResult = result;
    }

    private static long getLastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static long getFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static final class StorageRoot {
        int version = CURRENT_VERSION;
        List<NodeGraph> sequences = new ArrayList<NodeGraph>();
    }

    private static final class MigrationResult {
        private final List<NodeGraph> graphs;
        private final boolean migrated;
        private final String message;

        private MigrationResult(List<NodeGraph> graphs, boolean migrated, String message) {
            this.graphs = graphs == null ? new ArrayList<NodeGraph>() : graphs;
            this.migrated = migrated;
            this.message = message == null ? "" : message;
        }
    }

    public static final class LoadResult {
        private final boolean compatible;
        private final int version;
        private final String message;
        private final List<NodeGraph> sequences;
        private final boolean migrated;

        private LoadResult(boolean compatible, int version, String message, List<NodeGraph> sequences,
                boolean migrated) {
            this.compatible = compatible;
            this.version = version;
            this.message = message == null ? "" : message;
            this.sequences = sequences == null ? new ArrayList<NodeGraph>() : sequences;
            this.migrated = migrated;
        }

        public static LoadResult success(int version, List<NodeGraph> sequences) {
            return new LoadResult(true, version, "", sequences, false);
        }

        public static LoadResult success(int version, List<NodeGraph> sequences, boolean migrated, String message) {
            return new LoadResult(true, version, message, sequences, migrated);
        }

        public static LoadResult incompatible(int version, String message) {
            return new LoadResult(false, version, message, new ArrayList<NodeGraph>(), false);
        }

        public boolean isCompatible() {
            return compatible;
        }

        public int getVersion() {
            return version;
        }

        public String getMessage() {
            return message;
        }

        public boolean isMigrated() {
            return migrated;
        }

        public List<NodeGraph> getSequences() {
            return Collections.unmodifiableList(sequences);
        }
    }
}
