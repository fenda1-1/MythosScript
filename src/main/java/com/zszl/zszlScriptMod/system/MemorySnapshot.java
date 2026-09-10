// 文件路径: src/main/java/com/zszl/zszlScriptMod/system/MemorySnapshot.java
package com.zszl.zszlScriptMod.system;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class MemorySnapshot {
    public final String name;
    public final long timestamp;
    public final long totalMemory;
    public final long freeMemory;
    public final long usedMemory;
    public final Map<String, Integer> entityCounts;
    public final Map<String, Integer> tileEntityCounts;
    // --- 核心修改：新增用于存储区块和其他对象的Map ---
    public final Map<String, Integer> chunkCounts;
    public final Map<String, Integer> renderChunkCounts;
    // --- 修改结束 ---
    public final Map<String, Long> entityMemoryUsage;
    public final Map<String, Long> tileEntityMemoryUsage;
    // --- 核心修改：新增用于存储区块和其他对象内存占用的Map ---
    public final Map<String, Long> chunkMemoryUsage;
    public final Map<String, Long> renderChunkMemoryUsage;
    // --- 修改结束 ---

    public MemorySnapshot(String name, long total, long free,
                          Map<String, Integer> entities, Map<String, Integer> tileEntities,
                          Map<String, Integer> chunks, Map<String, Integer> renderChunks, // 新增参数
                          Map<String, Long> entityMemory, Map<String, Long> tileEntityMemory,
                          Map<String, Long> chunkMemory, Map<String, Long> renderChunkMemory) { // 新增参数
        this.name = name;
        this.timestamp = System.currentTimeMillis();
        this.totalMemory = total;
        this.freeMemory = free;
        this.usedMemory = total - free;
        this.entityCounts = entities;
        this.tileEntityCounts = tileEntities;
        // --- 核心修改：初始化新字段 ---
        this.chunkCounts = chunks;
        this.renderChunkCounts = renderChunks;
        // --- 修改结束 ---
        this.entityMemoryUsage = entityMemory;
        this.tileEntityMemoryUsage = tileEntityMemory;
        // --- 核心修改：初始化新字段 ---
        this.chunkMemoryUsage = chunkMemory;
        this.renderChunkMemoryUsage = renderChunkMemory;
        // --- 修改结束 ---
    }

    public String getFormattedTimestamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }
}
