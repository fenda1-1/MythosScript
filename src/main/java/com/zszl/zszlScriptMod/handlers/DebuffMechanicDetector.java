// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/DebuffMechanicDetector.java
package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DebuffMechanicDetector {

    public static final DebuffMechanicDetector INSTANCE = new DebuffMechanicDetector();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CLICK_PACKET_TEMPLATE = "00 04 56 69 65 77 00 00 {id} 00 00 00 01 00 0C 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 00 00 00 12 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 5F 63 6C 69 63 6B %s 00 00 00 00";
    private static final String PACKET_CHANNEL = "OwlViewChannel";

    // !! 核心修复：重构状态机 !!
    private enum State {
        IDLE,
        STARTING,
        EQUIPPING,
        WEARING,
        UNEQUIPPING,
        COOLDOWN,
        FINISHED,
        FAILED
    }

    private State currentState = State.IDLE;

    // --- 测试参数 ---
    private int startWearDuration = 1;
    private int maxWearDuration = 100;
    private int wearDurationStep = 1;
    private int startCooldownDuration = 1;
    private int maxCooldownDuration = 100;
    private int cooldownDurationStep = 1;
    private int repetitionsPerSetting = 5;
    private String targetItemName = "牛排";

    // --- 运行时状态 ---
    private int currentWearDuration;
    private int currentCooldownDuration;
    private int currentRepetition;
    private int waitTicks;
    private TestReport currentReport;

    private DebuffMechanicDetector() {
    }

    public void startDetection(int startWear, int maxWear, int wearStep, int startCooldown, int maxCooldown,
            int cooldownStep, int reps, String item) {
        if (currentState != State.IDLE) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 已在运行中，请先停止。"));
            return;
        }
        if (PacketCaptureHandler.getOwlViewSessionID() == null) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 未获取到会话ID，请打开一次背包(E)以捕获。测试中止。"));
            return;
        }

        this.startWearDuration = startWear;
        this.maxWearDuration = maxWear;
        this.wearDurationStep = wearStep;
        this.startCooldownDuration = startCooldown;
        this.maxCooldownDuration = maxCooldown;
        this.cooldownDurationStep = cooldownStep;
        this.repetitionsPerSetting = reps;
        this.targetItemName = item;

        this.currentWearDuration = startWearDuration;
        this.currentCooldownDuration = startCooldownDuration;
        this.currentRepetition = 0;

        this.currentReport = new TestReport();

        mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[检测器] 开始检测脱下机制..."));
        currentState = State.STARTING;
    }

    public void stopDetection() {
        if (currentState == State.IDLE)
            return;

        mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[检测器] 检测已手动停止。"));
        if (currentReport != null && !currentReport.results.isEmpty()) {
            saveReport();
        }
        resetState();
    }

    private void resetState() {
        currentState = State.IDLE;
        currentReport = null;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("debuff_detector")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("debuff_detector");
        try {
        if (event.phase != TickEvent.Phase.END || mc.player == null || currentState == State.IDLE) {
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (currentState) {
            case STARTING:
                if (!mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()) {
                    mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 请先取下头盔！测试中止。"));
                    currentState = State.FAILED;
                    return;
                }
                if (findItemSlot(targetItemName) == -1) {
                    mc.player.sendMessage(new TextComponentString(
                            TextFormatting.RED + "[检测器] 背包里没有找到 '" + targetItemName + "'！测试中止。"));
                    currentState = State.FAILED;
                    return;
                }
                mc.player.sendMessage(new TextComponentString(
                        String.format("§b[检测器] §f测试参数: 穿戴时长=%d, 冷却时长=%d, 第 %d/%d 次", currentWearDuration,
                                currentCooldownDuration, currentRepetition + 1, repetitionsPerSetting)));
                equipItem();
                break;

            case EQUIPPING:
                // 等待穿戴完成
                if (!mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()) {
                    currentState = State.WEARING;
                    waitTicks = currentWearDuration; // 设置穿戴持续时间
                }
                // 如果超时还没穿上，可以在这里加一个超时逻辑
                break;

            case WEARING:
                // 穿戴时间到，执行脱下
                unequipItem();
                break;

            case UNEQUIPPING:
                // 等待脱下完成
                if (mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()) {
                    currentState = State.COOLDOWN;
                    waitTicks = currentCooldownDuration; // 设置冷却时间
                }
                // 如果超时还没脱下，可以在这里加一个超时逻辑
                break;

            case COOLDOWN:
                // 冷却时间到，准备下一轮
                currentRepetition++;
                if (currentRepetition >= repetitionsPerSetting) {
                    recordResult(true);
                    advanceToNextTest();
                } else {
                    currentState = State.STARTING;
                }
                break;

            case FINISHED:
                mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[检测器] 所有测试已完成！"));
                saveReport();
                resetState();
                break;

            case FAILED:
                stopDetection();
                break;
        }
        } finally {
            timer.stop();
        }
    }

    private void equipItem() {
        int itemSlot = findItemSlot(targetItemName);
        if (itemSlot == -1) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 无法找到物品，测试失败。"));
            currentState = State.FAILED;
            return;
        }
        sendClickPacket(itemSlot);
        sendClickPacket(5); // 立即尝试放到头盔槽
        sendClickPacket(itemSlot); // 清空鼠标

        currentState = State.EQUIPPING;
        waitTicks = 5; // 等待5 ticks让服务器响应
    }

    private void unequipItem() {
        ItemStack headStack = mc.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (headStack.isEmpty() || !headStack.getDisplayName().contains(targetItemName)) {
            // 装备在中途消失了，这是一次失败的测试
            recordResult(false);
            advanceToNextTest();
            return;
        }

        int emptySlot = findEmptySlot();
        if (emptySlot == -1) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 背包已满，无法脱下装备！测试中止。"));
            currentState = State.FAILED;
            return;
        }
        sendClickPacket(5); // 拿起头盔
        sendClickPacket(emptySlot); // 放入背包
        sendClickPacket(5); // 清空鼠标

        currentState = State.UNEQUIPPING;
        waitTicks = 5; // 等待5 ticks让服务器响应
    }

    private void advanceToNextTest() {
        currentCooldownDuration += cooldownDurationStep;
        if (currentCooldownDuration > maxCooldownDuration) {
            currentWearDuration += wearDurationStep;
            if (currentWearDuration > maxWearDuration) {
                currentState = State.FINISHED;
            } else {
                currentCooldownDuration = startCooldownDuration;
                currentState = State.STARTING;
            }
        } else {
            currentState = State.STARTING;
        }
        currentRepetition = 0;
    }

    private int findItemSlot(String name) {
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.inventoryContainer.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getDisplayName().contains(name)) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptySlot() {
        for (int i = 9; i < 45; i++) {
            if (mc.player.inventoryContainer.getSlot(i).getStack().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void sendClickPacket(int slotIndex) {
        String sessionIdHex = PacketCaptureHandler.getSessionIdAsHex();
        if (sessionIdHex == null || mc.getConnection() == null) {
            zszlScriptMod.LOGGER.error("[检测器] 发送点击包失败: 会话ID或连接为空。");
            currentState = State.FAILED;
            return;
        }

        String formattedSlot = String.format("%02X %02X %02X %02X",
                (slotIndex >> 24) & 0xFF, (slotIndex >> 16) & 0xFF, (slotIndex >> 8) & 0xFF, slotIndex & 0xFF);

        String hexPayload = String.format(CLICK_PACKET_TEMPLATE, formattedSlot).replace("{id}", sessionIdHex);

        try {
            String cleanHex = hexPayload.replaceAll("\\s", "");
            byte[] data = new byte[cleanHex.length() / 2];
            for (int j = 0; j < data.length; j++) {
                data[j] = (byte) Integer.parseInt(cleanHex.substring(j * 2, j * 2 + 2), 16);
            }

            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            FMLProxyPacket packet = new FMLProxyPacket(buffer, PACKET_CHANNEL);
            mc.getConnection().sendPacket(packet);

        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[检测器] 构建或发送点击数据包时出错", e);
            currentState = State.FAILED;
        }
    }

    private void recordResult(boolean success) {
        if (currentReport == null) {
            zszlScriptMod.LOGGER.warn("[检测器] 尝试记录结果时，currentReport 为空，可能测试已停止。");
            return;
        }
        TestResult result = new TestResult(currentWearDuration, currentCooldownDuration, success);
        currentReport.results.add(result);
        String status = success ? "§a成功" : "§c失败";
        mc.player.sendMessage(new TextComponentString(String.format("§b[检测器] §f结果: 穿戴=%d, 冷却=%d -> %s",
                currentWearDuration, currentCooldownDuration, status)));
    }

    private void saveReport() {
        if (currentReport == null) {
            zszlScriptMod.LOGGER.warn("[检测器] 尝试保存报告时，currentReport 为空。");
            return;
        }
        try {
            Path reportDir = ProfileManager.getCurrentProfileDir().resolve("debuff_reports");
            Files.createDirectories(reportDir);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            Path reportFile = reportDir.resolve("report_" + timestamp + ".json");
            currentReport.timestamp = timestamp;

            try (BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                GSON.toJson(currentReport, writer);
            }
            mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[检测器] 测试报告已保存。"));
        } catch (IOException e) {
            zszlScriptMod.LOGGER.error("保存检测报告失败", e);
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[检测器] 保存报告失败！"));
        }
    }

    public static class TestReport {
        public String timestamp;
        public List<TestResult> results = new ArrayList<>();
    }

    public static class TestResult {
        public int wearDuration;
        public int cooldownDuration;
        public boolean success;

        public TestResult(int wear, int cooldown, boolean success) {
            this.wearDuration = wear;
            this.cooldownDuration = cooldown;
            this.success = success;
        }
    }
}
