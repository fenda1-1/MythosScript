// 文件路径: src/main/java/com/mythos/mythosScriptMod/system/MemoryManager.java
package com.mythos.mythosScriptMod.system;

import com.mythos.mythosScriptMod.mcp.McpJson;
import com.mythos.mythosScriptMod.config.ModConfig;
import com.mythos.mythosScriptMod.utils.ReflectionCompat;
import com.mythos.mythosScriptMod.mythosScriptMod; // !! 修复：添加缺失的导入 !!
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MemoryManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static final Map<String, MemorySnapshot> snapshots = new LinkedHashMap<>();
    private static final com.google.gson.Gson GSON = McpJson.GSON;
    private static volatile boolean snapshotsLoaded;
    private static volatile Map<String, String> classToModId;

    // --- 核心修复：使用反射来访问受保护的方法 ---
    private static Method writeChunkToNBTMethod = null;

    static {
        writeChunkToNBTMethod = findWriteChunkToNBTMethod();
        if (writeChunkToNBTMethod == null) {
            mythosScriptMod.LOGGER.error(
                    "Unable to find AnvilChunkLoader.writeChunkToNBT via reflection; chunk memory estimation may be inaccurate.",
                    new NoSuchMethodException("writeChunkToNBT(Chunk, World, NBTTagCompound)"));
        }
    }

    private static Method findWriteChunkToNBTMethod() {
        try {
            Method method = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75820_a",
                    void.class, Chunk.class, World.class, NBTTagCompound.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            // Fall through for environments that expose MCP, SRG, or raw obfuscated names directly.
        }

        String[] candidateNames = { "writeChunkToNBT", "func_75820_a", "a" };
        for (String candidateName : candidateNames) {
            try {
                Method method = AnvilChunkLoader.class.getDeclaredMethod(candidateName, Chunk.class, World.class,
                        NBTTagCompound.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the next name used by the active Forge/Minecraft mapping.
            } catch (SecurityException ignored) {
                return null;
            }
        }
        return null;
    }

    public static void takeSnapshot(String name) {
        if (mc.world == null)
            return;

        System.gc();

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        Map<String, Integer> entityCounts = new HashMap<>();
        Map<String, Integer> tileEntityCounts = new HashMap<>();
        Map<String, Long> entityMemoryUsage = new HashMap<>();
        Map<String, Long> tileEntityMemoryUsage = new HashMap<>();
        Map<String, Integer> chunkCounts = new HashMap<>();
        Map<String, Long> chunkMemoryUsage = new HashMap<>();
        Map<String, Integer> renderChunkCounts = new HashMap<>();
        Map<String, Long> renderChunkMemoryUsage = new HashMap<>();

        // 扫描实体 (逻辑不变)
        for (Entity entity : mc.world.loadedEntityList) {
            String className = entity.getClass().getName();
            entityCounts.put(className, entityCounts.getOrDefault(className, 0) + 1);
            try {
                NBTTagCompound nbt = new NBTTagCompound();
                entity.writeToNBT(nbt);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(nbt, new DataOutputStream(baos));
                entityMemoryUsage.put(className, entityMemoryUsage.getOrDefault(className, 0L) + baos.size());
            } catch (Exception e) {
            }
        }

        // 扫描方块实体 (逻辑不变)
        for (TileEntity tileEntity : mc.world.loadedTileEntityList) {
            String className = tileEntity.getClass().getName();
            tileEntityCounts.put(className, tileEntityCounts.getOrDefault(className, 0) + 1);
            try {
                NBTTagCompound nbt = new NBTTagCompound();
                tileEntity.writeToNBT(nbt);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(nbt, new DataOutputStream(baos));
                tileEntityMemoryUsage.put(className, tileEntityMemoryUsage.getOrDefault(className, 0L) + baos.size());
            } catch (Exception e) {
            }
        }

        // --- 核心修复：使用反射访问客户端的区块列表 ---
        ChunkProviderClient chunkProvider = mc.world.getChunkProvider();
        if (chunkProvider != null) {
            try {
                // ObfuscationReflectionHelper 会自动处理混淆名 (field_73244_f)
                Long2ObjectMap<Chunk> chunkMapping = ReflectionCompat
                        .getPrivateValue(ChunkProviderClient.class, chunkProvider, "chunkMapping", "field_73244_f");
                if (chunkMapping != null) {
                    String chunkClassName = Chunk.class.getName();
                    AnvilChunkLoader loader = new AnvilChunkLoader(null, null);

                    for (Chunk chunk : chunkMapping.values()) {
                        chunkCounts.put(chunkClassName, chunkCounts.getOrDefault(chunkClassName, 0) + 1);
                        if (writeChunkToNBTMethod != null) { // 确保方法已成功获取
                            try {
                                NBTTagCompound nbt = new NBTTagCompound();
                                // 使用反射调用方法
                                writeChunkToNBTMethod.invoke(loader, chunk, mc.world, nbt);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                CompressedStreamTools.writeCompressed(nbt, new DataOutputStream(baos));
                                chunkMemoryUsage.put(chunkClassName,
                                        chunkMemoryUsage.getOrDefault(chunkClassName, 0L) + baos.size());
                            } catch (Exception e) {
                                // 忽略序列化失败
                            }
                        }
                    }
                }
            } catch (Exception e) {
                mythosScriptMod.LOGGER.error("Failed to get chunk list via reflection", e);
            }
        }
        // --- 修复结束 ---

        // --- 核心修复：使用反射访问渲染区块列表 ---
        if (mc.renderGlobal != null) {
            try {
                // ObfuscationReflectionHelper 会自动处理混淆名 (field_174992_B)
                RenderChunk[] renderChunks = ReflectionCompat.getPrivateValue(RenderGlobal.class,
                        mc.renderGlobal, "renderChunks", "field_174992_B");
                if (renderChunks != null) {
                    String renderChunkClassName = RenderChunk.class.getName();
                    renderChunkCounts.put(renderChunkClassName, renderChunks.length);
                    renderChunkMemoryUsage.put(renderChunkClassName, 0L); // 内存占用依然不估算
                }
            } catch (Exception e) {
                mythosScriptMod.LOGGER.error("Failed to get render chunk list via reflection", e);
            }
        }
        // --- 修复结束 ---

        Map<String, Long> modMemoryUsage = new HashMap<>();
        Map<String, Integer> modObjectCounts = new HashMap<>();
        aggregateByMod(entityMemoryUsage, entityCounts, modMemoryUsage, modObjectCounts);
        aggregateByMod(tileEntityMemoryUsage, tileEntityCounts, modMemoryUsage, modObjectCounts);
        aggregateByMod(chunkMemoryUsage, chunkCounts, modMemoryUsage, modObjectCounts);

        MemorySnapshot snapshot = new MemorySnapshot(name, System.currentTimeMillis(), totalMemory, freeMemory,
                entityCounts, tileEntityCounts, chunkCounts, renderChunkCounts,
                entityMemoryUsage, tileEntityMemoryUsage, chunkMemoryUsage, renderChunkMemoryUsage,
                modMemoryUsage, modObjectCounts);
        snapshots.put(name, snapshot);
        persistSnapshot(snapshot);
    }

    private static void aggregateByMod(Map<String, Long> memoryByClass, Map<String, Integer> countsByClass,
            Map<String, Long> modMemoryUsage, Map<String, Integer> modObjectCounts) {
        for (Map.Entry<String, Long> entry : memoryByClass.entrySet()) {
            String modId = modIdForClass(entry.getKey());
            long bytes = entry.getValue() == null ? 0L : entry.getValue();
            int count = countsByClass == null ? 0 : countsByClass.getOrDefault(entry.getKey(), 0);
            modMemoryUsage.merge(modId, bytes, Long::sum);
            modObjectCounts.merge(modId, count, Integer::sum);
        }
    }

    private static String modIdForClass(String className) {
        if (className == null || className.isEmpty()) {
            return "unknown";
        }
        Map<String, String> map = classToModId;
        if (map == null) {
            map = buildClassToModId();
            classToModId = map;
        }
        String modId = map.get(className);
        if (modId != null) {
            return modId;
        }
        // 前缀匹配：按包名从长到短找归属 mod
        String pkg = className;
        while (true) {
            int idx = pkg.lastIndexOf('.');
            if (idx <= 0) {
                return "unknown";
            }
            pkg = pkg.substring(0, idx);
            modId = map.get(pkg + ".*");
            if (modId != null) {
                return modId;
            }
        }
    }

    private static Map<String, String> buildClassToModId() {
        Map<String, String> map = new HashMap<>();
        try {
            for (ModContainer mod : Loader.instance().getModList()) {
                String modId = mod.getModId();
                if (modId == null || modId.isEmpty()) {
                    continue;
                }
                for (String pkg : mod.getOwnedPackages()) {
                    if (pkg != null && !pkg.isEmpty()) {
                        map.putIfAbsent(pkg + ".*", modId);
                    }
                }
                // 兜底：mod 主类所在包
                Object modInstance = mod.getMod();
                if (modInstance != null) {
                    map.putIfAbsent(modInstance.getClass().getPackage().getName() + ".*", modId);
                }
            }
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.warn("Failed to build class->modId map", t);
        }
        // 原版归属
        map.put("net.minecraft.*", "minecraft");
        map.put("net.minecraftforge.*", "forge");
        return map;
    }

    private static Path getSnapshotDir() {
        Path dir = Paths.get(ModConfig.CONFIG_DIR, "MemorySnapshots");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                mythosScriptMod.LOGGER.error("Failed to create memory snapshot directory", e);
            }
        }
        return dir;
    }

    private static String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "snapshot";
        }
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
        return cleaned.isEmpty() ? "snapshot" : cleaned;
    }

    private static Path resolveSnapshotPath(String name) {
        return getSnapshotDir().resolve(sanitizeName(name) + ".json");
    }

    private static void persistSnapshot(MemorySnapshot snapshot) {
        Path file = resolveSnapshotPath(snapshot.name);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to persist memory snapshot: {}", snapshot.name, e);
        }
    }

    /** 从磁盘加载全部快照到内存（仅第一次访问或显式调用时） */
    public static synchronized void loadSnapshotsFromDisk() {
        if (snapshotsLoaded) {
            return;
        }
        snapshotsLoaded = true;
        Path dir = getSnapshotDir();
        try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> {
                        try (java.io.Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                            MemorySnapshot snapshot = GSON.fromJson(reader, MemorySnapshot.class);
                            if (snapshot != null && snapshot.name != null) {
                                snapshots.putIfAbsent(snapshot.name, snapshot);
                            }
                        } catch (Exception e) {
                            mythosScriptMod.LOGGER.warn("Skip invalid memory snapshot file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to list memory snapshots", e);
        }
    }

    public static void deleteSnapshot(String name) {
        snapshots.remove(name);
        try {
            Files.deleteIfExists(resolveSnapshotPath(name));
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to delete memory snapshot file: {}", name, e);
        }
    }

    public static void clearSnapshots() {
        snapshots.clear();
        Path dir = getSnapshotDir();
        try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            mythosScriptMod.LOGGER.warn("Failed to delete memory snapshot file: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            mythosScriptMod.LOGGER.error("Failed to clear memory snapshot files", e);
        }
    }

    /** 写一份完整 JVM 堆转储(.hprof)，返回文件路径；失败返回 null */
    public static String dumpHeap() {
        try {
            Path dir = getSnapshotDir();
            String fileName = "heap-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                    .format(new java.util.Date()) + ".hprof";
            Path file = dir.resolve(fileName);
            Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            Object bean = ManagementFactory.getPlatformMXBean(
                    clazz.asSubclass(java.lang.management.PlatformManagedObject.class));
            clazz.getMethod("dumpHeap", String.class, boolean.class)
                    .invoke(bean, file.toAbsolutePath().toString(), true);
            return file.toAbsolutePath().toString();
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.error("Heap dump failed", t);
            return null;
        }
    }

    public static ComparisonResult compare(MemorySnapshot before, MemorySnapshot after) {
        return new ComparisonResult(before, after);
    }

    public static class ComparisonResult {
        public final MemorySnapshot before;
        public final MemorySnapshot after;
        public final List<Map.Entry<String, Long>> topMemoryIncreases;
        public final List<Map.Entry<String, Long>> topMemoryDecreases;

        public ComparisonResult(MemorySnapshot before, MemorySnapshot after) {
            this.before = before;
            this.after = after;

            Map<String, Long> memoryDeltas = new HashMap<>();

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(before.entityMemoryUsage.keySet());
            allKeys.addAll(after.entityMemoryUsage.keySet());
            allKeys.addAll(before.tileEntityMemoryUsage.keySet());
            allKeys.addAll(after.tileEntityMemoryUsage.keySet());
            allKeys.addAll(before.chunkMemoryUsage.keySet());
            allKeys.addAll(after.chunkMemoryUsage.keySet());
            allKeys.addAll(before.renderChunkMemoryUsage.keySet());
            allKeys.addAll(after.renderChunkMemoryUsage.keySet());

            for (String key : allKeys) {
                long beforeMemory = before.entityMemoryUsage.getOrDefault(key, 0L)
                        + before.tileEntityMemoryUsage.getOrDefault(key, 0L)
                        + before.chunkMemoryUsage.getOrDefault(key, 0L)
                        + before.renderChunkMemoryUsage.getOrDefault(key, 0L);

                long afterMemory = after.entityMemoryUsage.getOrDefault(key, 0L)
                        + after.tileEntityMemoryUsage.getOrDefault(key, 0L)
                        + after.chunkMemoryUsage.getOrDefault(key, 0L)
                        + after.renderChunkMemoryUsage.getOrDefault(key, 0L);

                long memoryDelta = afterMemory - beforeMemory;

                if (memoryDelta != 0) {
                    memoryDeltas.put(key, memoryDelta);
                }
            }

            List<Map.Entry<String, Long>> sortedDeltas = new ArrayList<>(memoryDeltas.entrySet());
            topMemoryIncreases = new ArrayList<>();
            topMemoryDecreases = new ArrayList<>();

            for (Map.Entry<String, Long> entry : sortedDeltas) {
                if (entry.getValue() > 0) {
                    topMemoryIncreases.add(entry);
                } else {
                    topMemoryDecreases.add(entry);
                }
            }

            topMemoryIncreases.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
            topMemoryDecreases.sort(Map.Entry.comparingByValue());
        }
    }
}
