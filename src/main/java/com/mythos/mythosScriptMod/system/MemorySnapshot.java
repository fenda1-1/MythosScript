// 文件路径: src/main/java/com/mythos/mythosScriptMod/system/MemorySnapshot.java
package com.mythos.mythosScriptMod.system;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MemorySnapshot {
    public String name;
    public long timestamp;
    public long totalMemory;
    public long freeMemory;
    public long usedMemory;
    public Map<String, Integer> entityCounts;
    public Map<String, Integer> tileEntityCounts;
    public Map<String, Integer> chunkCounts;
    public Map<String, Integer> renderChunkCounts;
    public Map<String, Long> entityMemoryUsage;
    public Map<String, Long> tileEntityMemoryUsage;
    public Map<String, Long> chunkMemoryUsage;
    public Map<String, Long> renderChunkMemoryUsage;
    // 按 mod 分组的估算字节与对象计数（key 为 modId，未识别的归入 "unknown"）
    public Map<String, Long> modMemoryUsage;
    public Map<String, Integer> modObjectCounts;

    public MemorySnapshot() {
        // Gson 反序列化需要无参构造器
    }

    public MemorySnapshot(String name, long total, long free,
                          Map<String, Integer> entities, Map<String, Integer> tileEntities,
                          Map<String, Integer> chunks, Map<String, Integer> renderChunks,
                          Map<String, Long> entityMemory, Map<String, Long> tileEntityMemory,
                          Map<String, Long> chunkMemory, Map<String, Long> renderChunkMemory) {
        this(name, System.currentTimeMillis(), total, free,
                entities, tileEntities, chunks, renderChunks,
                entityMemory, tileEntityMemory, chunkMemory, renderChunkMemory,
                new HashMap<String, Long>(), new HashMap<String, Integer>());
    }

    public MemorySnapshot(String name, long timestamp, long total, long free,
                          Map<String, Integer> entities, Map<String, Integer> tileEntities,
                          Map<String, Integer> chunks, Map<String, Integer> renderChunks,
                          Map<String, Long> entityMemory, Map<String, Long> tileEntityMemory,
                          Map<String, Long> chunkMemory, Map<String, Long> renderChunkMemory,
                          Map<String, Long> modMemoryUsage, Map<String, Integer> modObjectCounts) {
        this.name = name;
        this.timestamp = timestamp;
        this.totalMemory = total;
        this.freeMemory = free;
        this.usedMemory = total - free;
        this.entityCounts = entities;
        this.tileEntityCounts = tileEntities;
        this.chunkCounts = chunks;
        this.renderChunkCounts = renderChunks;
        this.entityMemoryUsage = entityMemory;
        this.tileEntityMemoryUsage = tileEntityMemory;
        this.chunkMemoryUsage = chunkMemory;
        this.renderChunkMemoryUsage = renderChunkMemory;
        this.modMemoryUsage = modMemoryUsage == null ? new HashMap<String, Long>() : modMemoryUsage;
        this.modObjectCounts = modObjectCounts == null ? new HashMap<String, Integer>() : modObjectCounts;
    }

    public String getFormattedTimestamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }
}
