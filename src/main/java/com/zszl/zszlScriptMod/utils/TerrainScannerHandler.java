// (这是一个全新的文件)
package com.zszl.zszlScriptMod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.ProfileManager;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TerrainScannerHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ScanStatus scanStatus = ScanStatus.idle();

    /** Immutable state exposed to embedded UI pages without sharing mutable scan data. */
    public static final class ScanStatus {
        private final boolean scanning;
        private final int radius;
        private final int processedBlocks;
        private final int totalBlocks;
        private final int blockCount;
        private final String outputFileName;
        private final String errorMessage;
        private final long startedAt;
        private final long finishedAt;

        private ScanStatus(boolean scanning, int radius, int processedBlocks, int totalBlocks, int blockCount,
                String outputFileName, String errorMessage, long startedAt, long finishedAt) {
            this.scanning = scanning;
            this.radius = radius;
            this.processedBlocks = Math.max(0, processedBlocks);
            this.totalBlocks = Math.max(0, totalBlocks);
            this.blockCount = blockCount;
            this.outputFileName = outputFileName == null ? "" : outputFileName;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }

        private static ScanStatus idle() {
            return new ScanStatus(false, 0, 0, 0, 0, "", "", 0L, 0L);
        }

        private static ScanStatus running(int radius, int processedBlocks, int totalBlocks, long startedAt) {
            return new ScanStatus(true, radius, processedBlocks, totalBlocks, 0, "", "", startedAt, 0L);
        }

        private static ScanStatus completed(int radius, int totalBlocks, int blockCount, String outputFileName,
                long startedAt, long finishedAt) {
            return new ScanStatus(false, radius, totalBlocks, totalBlocks, blockCount, outputFileName, "", startedAt,
                    finishedAt);
        }

        private static ScanStatus failed(int radius, int processedBlocks, int totalBlocks, String errorMessage,
                long startedAt, long finishedAt) {
            return new ScanStatus(false, radius, processedBlocks, totalBlocks, 0, "", errorMessage, startedAt,
                    finishedAt);
        }

        public boolean isScanning() {
            return scanning;
        }

        public int getRadius() {
            return radius;
        }

        public int getProcessedBlocks() {
            return processedBlocks;
        }

        public int getTotalBlocks() {
            return totalBlocks;
        }

        public int getBlockCount() {
            return blockCount;
        }

        public String getOutputFileName() {
            return outputFileName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getFinishedAt() {
            return finishedAt;
        }
    }

    public static ScanStatus getScanStatus() {
        return scanStatus;
    }

    /**
     * 扫描以玩家为中心的地形并保存到文件。
     * 
     * @param radius 扫描半径 (立方体范围)
     */
    public static void scanAndSaveTerrain(int radius) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            long now = System.currentTimeMillis();
            synchronized (TerrainScannerHandler.class) {
                if (scanStatus.isScanning()) {
                    return;
                }
                scanStatus = ScanStatus.failed(Math.max(1, Math.min(50, radius)), 0, 0,
                        "player_or_world_not_loaded", now, now);
            }
            if (mc.player != null) {
                mc.player.sendMessage(
                        new TextComponentString(
                                TextFormatting.RED + I18n.format("msg.terrain_scan.player_world_not_loaded")));
            }
            return;
        }

        final int finalRadius = Math.max(1, Math.min(50, radius)); // 半径限制在1-50之间
        final long startedAt = System.currentTimeMillis();
        final int totalBlocks = (finalRadius * 2 + 1) * (finalRadius * 2 + 1) * (finalRadius * 2 + 1);
        synchronized (TerrainScannerHandler.class) {
            if (scanStatus.isScanning()) {
                return;
            }
            scanStatus = ScanStatus.running(finalRadius, 0, totalBlocks, startedAt);
        }

        // 避免把 Entity / EntityPlayerSP 混放到同一个局部变量里。
        // ProGuard 7.8.1 在当前构建链路下会把该局部变量的 StackMapTable
        // 错误降级成 Object，进而在 Java 8 客户端触发 VerifyError。
        final BlockPos center = mc.getRenderViewEntity() != null
                ? mc.getRenderViewEntity().getPosition()
                : mc.player.getPosition();
        final World world = mc.world;

        mc.player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + I18n.format("msg.terrain_scan.start", finalRadius)));

        // 在新线程中执行扫描，防止游戏卡顿
        new Thread(() -> {
            int processedBlocks = 0;
            try {
                JsonObject resultJson = new JsonObject();
                JsonArray blocksArray = new JsonArray();

                // 添加元数据
                resultJson.addProperty("scanTimestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                resultJson.addProperty("scanRadius", finalRadius);
                JsonObject centerPosJson = new JsonObject();
                centerPosJson.addProperty("x", center.getX());
                centerPosJson.addProperty("y", center.getY());
                centerPosJson.addProperty("z", center.getZ());
                resultJson.add("scanCenterAbsolute", centerPosJson);

                // 执行扫描
                for (int dx = -finalRadius; dx <= finalRadius; dx++) {
                    for (int dy = -finalRadius; dy <= finalRadius; dy++) {
                        for (int dz = -finalRadius; dz <= finalRadius; dz++) {
                            BlockPos currentPos = center.add(dx, dy, dz);
                            IBlockState state = world.getBlockState(currentPos);
                            Block block = state.getBlock();
                            processedBlocks++;
                            if ((processedBlocks & 4095) == 0) {
                                scanStatus = ScanStatus.running(finalRadius, processedBlocks, totalBlocks, startedAt);
                            }

                            // 忽略空气方块以减小文件大小
                            if (block == Blocks.AIR) {
                                continue;
                            }

                            String blockName = Block.REGISTRY.getNameForObject(block).toString();

                            JsonObject blockJson = new JsonObject();
                            blockJson.addProperty("x", dx); // 相对坐标
                            blockJson.addProperty("y", dy);
                            blockJson.addProperty("z", dz);
                            blockJson.addProperty("block", blockName);
                            // 可选：添加方块元数据
                            // blockJson.addProperty("meta", block.getMetaFromState(state));

                            blocksArray.add(blockJson);
                        }
                    }
                }
                scanStatus = ScanStatus.running(finalRadius, totalBlocks, totalBlocks, startedAt);
                resultJson.add("blocks", blocksArray);

                // 保存文件
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                Path terrainDir = ProfileManager.getCurrentProfileDir().resolve("terrain");
                Files.createDirectories(terrainDir);
                Path outputPath = terrainDir.resolve(timestamp + ".json");

                try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                    GSON.toJson(resultJson, writer);
                }

                scanStatus = ScanStatus.completed(finalRadius, totalBlocks, blocksArray.size(),
                        outputPath.getFileName().toString(), startedAt, System.currentTimeMillis());

                // 在主线程中发送成功消息
                mc.addScheduledTask(() -> {
                    if (mc.player != null) {
                        mc.player.sendMessage(new TextComponentString(
                                TextFormatting.AQUA + I18n.format("msg.terrain_scan.done", blocksArray.size())));
                        mc.player.sendMessage(new TextComponentString(TextFormatting.GRAY + outputPath.toString()));
                    }
                });

            } catch (Exception e) {
                scanStatus = ScanStatus.failed(finalRadius, processedBlocks, totalBlocks, e.getClass().getSimpleName(),
                        startedAt, System.currentTimeMillis());
                zszlScriptMod.LOGGER.error("Error occurred during terrain scanning", e);
                mc.addScheduledTask(() -> {
                    if (mc.player != null) {
                        mc.player.sendMessage(
                                new TextComponentString(TextFormatting.RED + I18n.format("msg.terrain_scan.failed")));
                    }
                });
            }
        }).start();
    }
}
