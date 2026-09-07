// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/MemoryManager.java
package com.zszl.zszlScriptMod.system;

import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import com.zszl.zszlScriptMod.zszlScriptMod; // !! 修复：添加缺失的导入 !!
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
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.*;

public class MemoryManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static final Map<String, MemorySnapshot> snapshots = new LinkedHashMap<>();

    // --- 核心修复：使用反射来访问受保护的方法 ---
    private static Method writeChunkToNBTMethod = null;

    static {
        writeChunkToNBTMethod = findWriteChunkToNBTMethod();
        if (writeChunkToNBTMethod == null) {
            zszlScriptMod.LOGGER.error(
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
                zszlScriptMod.LOGGER.error("Failed to get chunk list via reflection", e);
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
                zszlScriptMod.LOGGER.error("Failed to get render chunk list via reflection", e);
            }
        }
        // --- 修复结束 ---

        MemorySnapshot snapshot = new MemorySnapshot(name, totalMemory, freeMemory,
                entityCounts, tileEntityCounts, chunkCounts, renderChunkCounts,
                entityMemoryUsage, tileEntityMemoryUsage, chunkMemoryUsage, renderChunkMemoryUsage);
        snapshots.put(name, snapshot);
    }

    public static void deleteSnapshot(String name) {
        snapshots.remove(name);
    }

    public static void clearSnapshots() {
        snapshots.clear();
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
