// --- Full Java Content (src/main/java/com/zszl/zszlScriptMod/utils/PacketCaptureHandler.java) ---
package com.zszl.zszlScriptMod.utils;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.packet.InputTimelineManager;
import com.zszl.zszlScriptMod.gui.packet.PacketIdRecordManager;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.node.NodeTriggerManager;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCollectItem;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.network.play.server.SPacketUpdateBossInfo;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

public class PacketCaptureHandler extends ChannelDuplexHandler {

    private static final int MAX_PACKET_PROCESS_QUEUE = 512;
    private static final long MAX_PACKET_PROCESS_RETAINED_BYTES = 16L * 1024 * 1024;
    private static final int MAX_BUSINESS_TASKS = 1024;
    private static final long MAX_BUSINESS_TASK_RETAINED_BYTES = 16L * 1024 * 1024;
    private static final long MAX_CAPTURE_QUEUE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_CAPTURED_RAW_BYTES_PER_DIRECTION = 24L * 1024 * 1024;
    private static final int MAX_SINGLE_CAPTURE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RECENT_ENTRY_CHARS = 64 * 1024;
    private static final int MAX_RECENT_TEXT_CHARS = 1024 * 1024;
    private static final int MAX_CAPTURED_DERIVED_CHARS = 256 * 1024;
    private static final AtomicLong droppedPacketProcessTaskCount = new AtomicLong();
    private static final AtomicLong packetProcessRetainedBytes = new AtomicLong();
    private static final ThreadPoolExecutor PACKET_PROCESS_EXECUTOR = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_PACKET_PROCESS_QUEUE), r -> {
                Thread t = new Thread(r, "zszl-packet-processor");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());

    private static final String OWL_CONTROL_CHANNEL = "OwlControlChannel";
    private static final String OWL_VIEW_CHANNEL = "OwlViewChannel";

    public static boolean isCapturing = false;
    public static final List<CapturedPacketData> capturedPackets = Collections.synchronizedList(new ArrayList<>());
    public static final List<CapturedPacketData> capturedReceivedPackets = Collections
            .synchronizedList(new ArrayList<>());

    // 捕获队列与节流参数：避免同一帧内大量 addScheduledTask 造成主线程卡顿
    private static final int MAX_CAPTURE_QUEUE = 6000;
    private static final int MAX_CAPTURE_PROCESS_PER_TICK = 40;
    private static final int MAX_CAPTURE_PROCESS_BYTES_PER_TICK = 256 * 1024;
    private static final long MAX_CAPTURE_PROCESS_NANOS_PER_TICK = 2_000_000L;
    private static final int MAX_CAPTURED_PACKETS = 3000;
    private static final int MAX_RECENT_TEXT_DECODE_BYTES = 64 * 1024;
    private static final long UI_SNAPSHOT_INTERVAL_MS = 500L;
    private static final long AGGREGATION_WINDOW_MS = 500L;
    private static final long MAX_BUSINESS_TASK_NANOS_PER_TICK = 1_500_000L;
    private static final ConcurrentLinkedQueue<PendingPacketSnapshot> pendingCaptureQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicLong pendingCaptureBytes = new AtomicLong();
    private static final AtomicBoolean captureDrainScheduled = new AtomicBoolean(false);
    private static volatile long lastCaptureDropWarnAt = 0L;
    private static final AtomicBoolean businessTaskScheduled = new AtomicBoolean(false);
    private static final ArrayBlockingQueue<PendingBusinessTask> pendingBusinessTasks = new ArrayBlockingQueue<>(
            MAX_BUSINESS_TASKS);
    private static final AtomicLong pendingBusinessTaskBytes = new AtomicLong();
    private static final AtomicLong capturedSentRawBytes = new AtomicLong();
    private static final AtomicLong capturedReceivedRawBytes = new AtomicLong();
    private static volatile int lastKnownCaptureQueueSize = 0;
    private static volatile long sampledPacketCount = 0L;
    private static volatile long droppedPacketCount = 0L;
    private static volatile int activeSamplingModulo = 1;
    private static volatile PacketCaptureUiSnapshot lastUiSnapshot = new PacketCaptureUiSnapshot(0, 0, 0, 0, 1, true,
            true, System.currentTimeMillis());

    // 始终缓存最近接收的 OwlView HEX（用于业务逻辑解析，不依赖捕获开关）
    private static final int MAX_RECENT_OWLVIEW_HEX = 120;
    private static final List<String> recentOwlViewIncomingHex = Collections.synchronizedList(new ArrayList<>());
    // 始终缓存最近接收的 OwlView 解码文本（用于业务逻辑解析）
    private static final List<String> recentOwlViewDecoded = Collections.synchronizedList(new ArrayList<>());
    // 始终缓存最近数据包文本（用于等待数据包文本动作）
    private static final int MAX_RECENT_PACKET_TEXTS = 200;
    private static final List<String> recentPacketTexts = Collections.synchronizedList(new ArrayList<>());
    private static volatile long recentPacketTextVersion = 0L;
    private static volatile String latestBossbarText = "";

    private static class PendingPacketSnapshot {
        final String packetClassName;
        final boolean isFmlPacket;
        final Integer packetId;
        final String channel;
        final byte[] rawData;
        final boolean isSent;

        PendingPacketSnapshot(String packetClassName, boolean isFmlPacket, Integer packetId, String channel,
                byte[] rawData, boolean isSent) {
            this.packetClassName = packetClassName;
            this.isFmlPacket = isFmlPacket;
            this.packetId = packetId;
            this.channel = channel;
            this.rawData = rawData;
            this.isSent = isSent;
        }
    }

    public enum CaptureMode {
        BLACKLIST, WHITELIST;

        public CaptureMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public static class CapturedPacketData {
        public final long timestamp;
        public final String packetClassName;
        public final boolean isFmlPacket;
        public final Integer packetId;
        public final String channel;
        public final byte[] rawData;
        private volatile SoftReference<String> hexData;
        private volatile SoftReference<String> decodedData;
        private volatile SoftReference<String> decodedDetailData;
        private volatile SoftReference<String> decodedFullData;
        private final int payloadSize;
        private volatile long lastTimestamp;
        private volatile int occurrenceCount;
        private volatile int totalPayloadBytes;

        public CapturedPacketData(long timestamp, String packetClassName, boolean isFmlPacket, Integer packetId,
                String channel, byte[] rawData, String decodedData) {
            this.timestamp = timestamp;
            this.packetClassName = packetClassName;
            this.isFmlPacket = isFmlPacket;
            this.packetId = packetId;
            this.channel = channel;
            this.rawData = rawData == null ? new byte[0] : rawData;
            this.decodedData = decodedData == null ? null
                    : new SoftReference<>(boundCapturedDerivedData(decodedData));
            this.payloadSize = this.rawData.length;
            this.lastTimestamp = timestamp;
            this.occurrenceCount = 1;
            this.totalPayloadBytes = this.payloadSize;
        }

        public CapturedPacketData(String packetClassName, boolean isFmlPacket, Integer packetId, String channel,
                byte[] rawData, String decodedData) {
            this(System.currentTimeMillis(), packetClassName, isFmlPacket, packetId, channel, rawData, decodedData);
        }

        public int getPayloadSize() {
            return payloadSize;
        }

        public long getLastTimestamp() {
            return lastTimestamp;
        }

        public int getOccurrenceCount() {
            return occurrenceCount;
        }

        public int getTotalPayloadBytes() {
            return totalPayloadBytes;
        }

        public boolean isAggregated() {
            return occurrenceCount > 1;
        }

        public String getHexData() {
            String local = dereference(hexData);
            if (local == null) {
                local = bytesToHex(rawData);
                hexData = new SoftReference<>(local == null ? "" : local);
            }
            return local == null ? "" : local;
        }

        public String getDecodedData() {
            String local = dereference(decodedData);
            if (local == null) {
                if (isFmlPacket && OWL_CONTROL_CHANNEL.equals(channel)) {
                    local = OwlViewPacketDecoder.decode(channel, rawData);
                } else {
                    local = decodePayload(rawData);
                }
                local = boundCapturedDerivedData(local);
                decodedData = new SoftReference<>(local == null ? "" : local);
            }
            return local == null ? "" : local;
        }

        public String getDecodedDetailData() {
            String local = dereference(decodedDetailData);
            if (local == null) {
                if (isFmlPacket && OWL_CONTROL_CHANNEL.equals(channel)) {
                    local = getDecodedData();
                } else {
                    local = PacketPayloadDecoder.decodeDetailed(rawData);
                    String cachedDecoded = dereference(decodedData);
                    if ((local == null || local.trim().isEmpty()) && cachedDecoded != null) {
                        local = cachedDecoded;
                    }
                }
                local = boundCapturedDerivedData(local);
                decodedDetailData = new SoftReference<>(local == null ? "" : local);
            }
            return local == null ? "" : local;
        }

        public String getDecodedFullData() {
            String local = dereference(decodedFullData);
            if (local == null) {
                if (isFmlPacket && OWL_CONTROL_CHANNEL.equals(channel)) {
                    local = getDecodedData();
                } else {
                    local = PacketPayloadDecoder.decodeFull(rawData);
                    String cachedDecoded = dereference(decodedData);
                    if ((local == null || local.trim().isEmpty()) && cachedDecoded != null) {
                        local = cachedDecoded;
                    }
                }
                local = boundCapturedDerivedData(local);
                decodedFullData = new SoftReference<>(local == null ? "" : local);
            }
            return local == null ? "" : local;
        }

        public boolean canAggregate(CapturedPacketData other) {
            if (other == null) {
                return false;
            }
            if (other.timestamp - this.lastTimestamp > AGGREGATION_WINDOW_MS) {
                return false;
            }
            return this.isFmlPacket == other.isFmlPacket && Objects.equals(this.packetClassName, other.packetClassName)
                    && Objects.equals(this.channel, other.channel) && Objects.equals(this.packetId, other.packetId)
                    && Arrays.equals(this.rawData, other.rawData);
        }

        public void mergeFrom(CapturedPacketData other) {
            if (other == null) {
                return;
            }
            this.lastTimestamp = Math.max(this.lastTimestamp, other.lastTimestamp);
            this.occurrenceCount += other.occurrenceCount;
            this.totalPayloadBytes += other.totalPayloadBytes;
        }

        public void restoreAggregateState(long restoredLastTimestamp, int restoredOccurrenceCount,
                int restoredTotalPayloadBytes) {
            this.lastTimestamp = Math.max(this.timestamp, restoredLastTimestamp);
            this.occurrenceCount = Math.max(1, restoredOccurrenceCount);
            this.totalPayloadBytes = Math.max(this.payloadSize, restoredTotalPayloadBytes);
        }
    }

    public static class PacketCaptureUiSnapshot {
        public final int sentCount;
        public final int receivedCount;
        public final int queueSize;
        public final long droppedCount;
        public final int samplingModulo;
        public final boolean businessProcessingEnabled;
        public final boolean adaptiveSamplingEnabled;
        public final long createdAt;

        public PacketCaptureUiSnapshot(int sentCount, int receivedCount, int queueSize, long droppedCount,
                int samplingModulo, boolean businessProcessingEnabled, boolean adaptiveSamplingEnabled,
                long createdAt) {
            this.sentCount = sentCount;
            this.receivedCount = receivedCount;
            this.queueSize = queueSize;
            this.droppedCount = droppedCount;
            this.samplingModulo = samplingModulo;
            this.businessProcessingEnabled = businessProcessingEnabled;
            this.adaptiveSamplingEnabled = adaptiveSamplingEnabled;
            this.createdAt = createdAt;
        }
    }

    public static byte[] getOwlViewSessionID() {
        return CapturedIdRuleManager.getCapturedIdBytes("id");
    }

    public static String getSessionIdAsHex() {
        return CapturedIdRuleManager.getCapturedIdHex("id");
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return null;
        int bytesToEncode = Math.min(bytes.length, MAX_CAPTURED_DERIVED_CHARS / 3);
        final char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder hex = new StringBuilder(bytesToEncode * 3 + 16);
        for (int i = 0; i < bytesToEncode; i++) {
            int value = bytes[i] & 0xFF;
            hex.append(digits[value >>> 4]).append(digits[value & 0x0F]).append(' ');
        }
        if (bytes.length > bytesToEncode) {
            hex.append("...");
        }
        return hex.toString().trim();
    }

    private static String dereference(SoftReference<String> reference) {
        return reference == null ? null : reference.get();
    }

    private static String boundCapturedDerivedData(String value) {
        if (value == null || value.length() <= MAX_CAPTURED_DERIVED_CHARS) {
            return value;
        }
        return value.substring(0, MAX_CAPTURED_DERIVED_CHARS) + "...";
    }

    private static int indexOf(byte[] source, byte[] target) {
        if (source == null || target == null || target.length == 0 || source.length < target.length) {
            return -1;
        }
        for (int i = 0; i <= source.length - target.length; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    public static void clearCapturedIdsForSession() {
        CapturedIdRuleManager.clearAllCapturedIds();
        zszlScriptMod.LOGGER.info("[PacketCapture] 已清除当前会话的捕获值。");
    }

    /**
     * 客户端 Tick 钩子（兼容旧调用点）。 当前会话更新逻辑已改为“无自动动作”，此处保留空实现避免编译失败。
     */
    public static void onClientTick() {
        // no-op
    }

    public static void clearAllPackets() {
        synchronized (capturedPackets) {
            capturedPackets.clear();
        }
        synchronized (capturedReceivedPackets) {
            capturedReceivedPackets.clear();
        }
        synchronized (pendingCaptureQueue) {
            pendingCaptureQueue.clear();
            pendingCaptureBytes.set(0L);
        }
        capturedSentRawBytes.set(0L);
        capturedReceivedRawBytes.set(0L);
        sampledPacketCount = 0L;
        droppedPacketCount = 0L;
        activeSamplingModulo = 1;
        lastKnownCaptureQueueSize = 0;
        lastUiSnapshot = new PacketCaptureUiSnapshot(0, 0, 0, 0, 1, true, true,
                System.currentTimeMillis());
        InputTimelineManager.clear();
    }

    /** Releases all packet-capture state that must not survive a server session. */
    public static void clearRuntimeState() {
        isCapturing = false;
        clearAllPackets();
        pendingBusinessTasks.clear();
        pendingBusinessTaskBytes.set(0L);
        List<Runnable> abandonedProcessTasks = new ArrayList<>();
        PACKET_PROCESS_EXECUTOR.getQueue().drainTo(abandonedProcessTasks);
        for (Runnable abandonedTask : abandonedProcessTasks) {
            if (abandonedTask instanceof RetainedPacketProcessTask) {
                ((RetainedPacketProcessTask) abandonedTask).releaseReservation();
            }
        }
        businessTaskScheduled.set(false);
        captureDrainScheduled.set(false);
        droppedPacketProcessTaskCount.set(0L);
        synchronized (recentOwlViewIncomingHex) {
            recentOwlViewIncomingHex.clear();
        }
        synchronized (recentOwlViewDecoded) {
            recentOwlViewDecoded.clear();
        }
        clearRecentPacketTexts();
        clearCapturedIdsForSession();
    }

    private static class PendingBusinessTask {
        final Runnable action;
        final int retainedBytes;

        PendingBusinessTask(Runnable action, int retainedBytes) {
            this.action = action;
            this.retainedBytes = retainedBytes;
        }
    }

    private static class RetainedPacketProcessTask implements Runnable {
        private final Runnable action;
        private final int retainedBytes;
        private final AtomicBoolean released = new AtomicBoolean(false);

        RetainedPacketProcessTask(Runnable action, int retainedBytes) {
            this.action = action;
            this.retainedBytes = retainedBytes;
        }

        @Override
        public void run() {
            try {
                action.run();
            } finally {
                releaseReservation();
            }
        }

        void releaseReservation() {
            if (released.compareAndSet(false, true)) {
                packetProcessRetainedBytes.updateAndGet(value -> Math.max(0L, value - retainedBytes));
            }
        }
    }

    public static int getPendingCaptureQueueSize() {
        return lastKnownCaptureQueueSize;
    }

    public static long getSampledPacketCount() {
        return sampledPacketCount;
    }

    public static long getDroppedPacketCount() {
        return droppedPacketCount;
    }

    public static int getActiveSamplingModulo() {
        return activeSamplingModulo;
    }

    public static PacketCaptureUiSnapshot getUiSnapshot() {
        PacketCaptureUiSnapshot snapshot = lastUiSnapshot;
        long now = System.currentTimeMillis();
        if (snapshot == null || now - snapshot.createdAt >= UI_SNAPSHOT_INTERVAL_MS) {
            PacketFilterConfig config = PacketFilterConfig.INSTANCE;
            snapshot = new PacketCaptureUiSnapshot(capturedPackets.size(), capturedReceivedPackets.size(),
                    lastKnownCaptureQueueSize, droppedPacketCount, activeSamplingModulo,
                    config == null || config.enableBusinessPacketProcessing,
                    config == null || config.enableAdaptiveSampling, now);
            lastUiSnapshot = snapshot;
        }
        return snapshot;
    }

    public static List<String> getRecentOwlViewIncomingHexSnapshot() {
        synchronized (recentOwlViewIncomingHex) {
            return new ArrayList<>(recentOwlViewIncomingHex);
        }
    }

    public static void clearRecentOwlViewIncomingHex() {
        synchronized (recentOwlViewIncomingHex) {
            recentOwlViewIncomingHex.clear();
        }
    }

    public static List<String> getRecentPacketTextsSnapshot() {
        synchronized (recentPacketTexts) {
            return new ArrayList<>(recentPacketTexts);
        }
    }

    public static long getRecentPacketTextVersion() {
        return recentPacketTextVersion;
    }

    public static String getLatestBossbarText() {
        return latestBossbarText == null ? "" : latestBossbarText;
    }

    public static void clearRecentPacketTexts() {
        synchronized (recentPacketTexts) {
            recentPacketTexts.clear();
            recentPacketTextVersion = 0L;
        }
        latestBossbarText = "";
    }

    private static void storeIncomingOwlViewHex(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return;
        }
        int bytesToEncode = Math.min(rawData.length, MAX_RECENT_TEXT_DECODE_BYTES);
        final char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder hex = new StringBuilder(bytesToEncode * 3 + 16);
        for (int i = 0; i < bytesToEncode; i++) {
            int value = rawData[i] & 0xFF;
            hex.append(digits[value >>> 4]).append(digits[value & 0x0F]).append(' ');
        }
        if (rawData.length > bytesToEncode) {
            hex.append("...");
        }
        String hexData = hex.toString().trim();
        synchronized (recentOwlViewIncomingHex) {
            recentOwlViewIncomingHex.add(hexData);
            trimRecentStrings(recentOwlViewIncomingHex, MAX_RECENT_OWLVIEW_HEX, MAX_RECENT_TEXT_CHARS);
        }
    }

    private boolean isBusinessProcessingEnabled() {
        PacketFilterConfig config = PacketFilterConfig.INSTANCE;
        return config == null || config.enableBusinessPacketProcessing;
    }

    private boolean hasPacketTriggerListeners() {
        return NodeTriggerManager.hasGraphsForTrigger(NodeTriggerManager.TRIGGER_PACKET)
                || LegacySequenceTriggerManager.hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_PACKET);
    }

    private boolean isRecentPacketTextFeedNeeded() {
        return PathSequenceEventListener.instance != null && PathSequenceEventListener.instance.isTracking();
    }

    private boolean enqueueBusinessTask(Runnable task) {
        return enqueueBusinessTask(task, 0);
    }

    private boolean enqueueBusinessTask(Runnable task, int retainedBytes) {
        if (task == null) {
            return false;
        }
        int safeRetainedBytes = Math.max(0, retainedBytes);
        long reserved = pendingBusinessTaskBytes.addAndGet(safeRetainedBytes);
        if (reserved > MAX_BUSINESS_TASK_RETAINED_BYTES) {
            pendingBusinessTaskBytes.addAndGet(-safeRetainedBytes);
            droppedPacketCount++;
            return false;
        }
        if (!pendingBusinessTasks.offer(new PendingBusinessTask(task, safeRetainedBytes))) {
            pendingBusinessTaskBytes.addAndGet(-safeRetainedBytes);
            droppedPacketCount++;
            return false;
        }
        scheduleBusinessTaskDrain();
        return true;
    }

    private void scheduleBusinessTaskDrain() {
        if (!businessTaskScheduled.compareAndSet(false, true)) {
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(this::drainBusinessTasksOnMainThread);
    }

    private void drainBusinessTasksOnMainThread() {
        businessTaskScheduled.set(false);
        int budget = 32;
        long startNanos = System.nanoTime();
        while (budget-- > 0) {
            if (System.nanoTime() - startNanos >= MAX_BUSINESS_TASK_NANOS_PER_TICK) {
                break;
            }
            PendingBusinessTask task = pendingBusinessTasks.poll();
            if (task == null) {
                break;
            }
            pendingBusinessTaskBytes.updateAndGet(value -> Math.max(0L, value - task.retainedBytes));
            try {
                task.action.run();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("[PacketCapture] 执行业务任务失败", e);
            }
        }
        if (!pendingBusinessTasks.isEmpty()) {
            scheduleBusinessTaskDrain();
        }
    }

    private boolean shouldSampleCapture() {
        PacketFilterConfig config = PacketFilterConfig.INSTANCE;
        if (config == null || !config.enableAdaptiveSampling) {
            activeSamplingModulo = 1;
            return true;
        }

        int queueSize = pendingCaptureQueue.size();
        lastKnownCaptureQueueSize = queueSize;
        if (queueSize < config.adaptiveSamplingQueueThreshold) {
            activeSamplingModulo = 1;
            return true;
        }

        activeSamplingModulo = Math.max(2, config.adaptiveSamplingModulo);
        long current = sampledPacketCount + droppedPacketCount + 1L;
        boolean keep = current % activeSamplingModulo == 0;
        if (!keep) {
            sampledPacketCount++;
        }
        return keep;
    }

    // --- 核心修改：修复 channelRead 方法 ---
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean inboundFeatureEnabled = PerformanceMonitor.isFeatureEnabled("packet_capture_inbound");
        boolean businessProcessingEnabled = isBusinessProcessingEnabled();
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("packet_capture_inbound");
        try {
            Object inboundMsg = msg;

            // S->C 拦截与改包：在进入游戏主逻辑前执行
            if (PerformanceMonitor.isFeatureEnabled("packet_intercept") && msg instanceof Packet) {
                PerformanceMonitor.PerformanceTimer interceptTimer = PerformanceMonitor.startTimer("packet_intercept");
                try {
                    Packet<?> inboundPacket = (Packet<?>) msg;
                    boolean skipStandardSerialization = inboundPacket instanceof net.minecraft.network.play.server.SPacketSpawnPlayer
                            || inboundPacket instanceof net.minecraft.network.play.server.SPacketSpawnMob;
                    if (msg instanceof FMLProxyPacket) {
                        FMLProxyPacket origin = (FMLProxyPacket) msg;
                        String channel = origin.channel();
                        ByteBuf payload = origin.payload();
                        byte[] rawData = new byte[payload.readableBytes()];
                        payload.getBytes(payload.readerIndex(), rawData);

                        PacketInterceptManager.PacketMeta meta = new PacketInterceptManager.PacketMeta(channel,
                                origin.getClass().getSimpleName(), null);
                        PacketInterceptManager.InterceptResult interceptResult = PacketInterceptManager
                                .applyInboundRules(meta, rawData);
                        if (interceptResult.modified && interceptResult.payload != null) {
                            // 关键：不要 new FMLProxyPacket（会丢失 dispatcher，导致 NetworkRegistry NPE）
                            // 直接改写原始 payload，保留原包上下文。
                            try {
                                payload.readerIndex(0);
                                payload.writerIndex(0);
                                payload.writeBytes(interceptResult.payload);
                                inboundMsg = origin;
                            } catch (Exception overwriteEx) {
                                zszlScriptMod.LOGGER.warn("[PacketIntercept] 改写FMLProxyPacket载荷失败，回退原包: {}", channel,
                                        overwriteEx);
                                inboundMsg = origin;
                            }
                        }
                    } else if (!skipStandardSerialization) {
                        PacketBuffer rawBuffer = null;
                        try {
                            rawBuffer = new PacketBuffer(Unpooled.buffer());
                            inboundPacket.writePacketData(rawBuffer);
                            byte[] rawData = new byte[rawBuffer.readableBytes()];
                            rawBuffer.readBytes(rawData);

                            Integer packetId = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND,
                                    inboundPacket);
                            PacketInterceptManager.PacketMeta meta = new PacketInterceptManager.PacketMeta("N/A",
                                    inboundPacket.getClass().getSimpleName(), packetId);
                            PacketInterceptManager.InterceptResult interceptResult = PacketInterceptManager
                                    .applyInboundRules(meta, rawData);

                            if (interceptResult.modified && interceptResult.payload != null) {
                                @SuppressWarnings("unchecked")
                                Packet<?> rebuilt = inboundPacket.getClass().newInstance();
                                PacketBuffer modifiedBuffer = new PacketBuffer(
                                        Unpooled.wrappedBuffer(interceptResult.payload));
                                try {
                                    rebuilt.readPacketData(modifiedBuffer);
                                } finally {
                                    modifiedBuffer.release();
                                }
                                inboundMsg = rebuilt;
                            }
                        } catch (Exception rebuildEx) {
                            zszlScriptMod.LOGGER.warn("[PacketIntercept] 重建标准S->C包失败，回退原包: {}",
                                    inboundPacket.getClass().getSimpleName(), rebuildEx);
                        } finally {
                            if (rawBuffer != null) {
                                rawBuffer.release();
                            }
                        }
                    }
                } finally {
                    interceptTimer.stop();
                }
            }

            // --- 第一部分：无条件特殊处理逻辑 ---
            // 无论 isCapturing 开关是否打开，我们都必须检查特定的数据包以实现核心功能。
            if (businessProcessingEnabled && inboundMsg instanceof FMLProxyPacket) {
                FMLProxyPacket fmlPacket = (FMLProxyPacket) inboundMsg;
                final String channel = fmlPacket.channel();
                final boolean packetTriggerListeners = hasPacketTriggerListeners();
                final boolean recentPacketTextFeedNeeded = isRecentPacketTextFeedNeeded();
                final boolean needsCapturedIdRules = CapturedIdRuleManager.hasEnabledRulesForChannel(channel, false);
                final boolean needsFieldRules = PacketFieldRuleManager.hasEnabledRulesForChannel(channel, false);
                final boolean shouldProcessFmlPacket = OWL_VIEW_CHANNEL.equals(channel)
                        || OWL_CONTROL_CHANNEL.equals(channel)
                        || packetTriggerListeners
                        || recentPacketTextFeedNeeded
                        || needsCapturedIdRules
                        || needsFieldRules;
                if (!shouldProcessFmlPacket) {
                    // 无规则、无等待、无触发器关注该 FML 包时，直接透传，避免在进服阶段无意义解码。
                } else {
                // 关键修复：必须在当前 Netty 线程里先拷贝 payload，
                // 不能把 fmlPacket/payload 直接丢到主线程任务里再读，避免 refCnt 已归零导致崩溃。
                ByteBuf payload = fmlPacket.payload();
                final byte[] rawData = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), rawData);
                final String packetClassName = fmlPacket.getClass().getSimpleName();

                enqueueBusinessTask(() -> {

                    final String decoded;
                    if ("OwlViewChannel".equals(channel)) {
                        storeIncomingOwlViewHex(rawData);
                        decoded = OwlViewPacketDecoder.decode(channel, rawData);
                        storeIncomingOwlViewDecoded(decoded);
                    } else if ("OwlControlChannel".equals(channel)) {
                        decoded = OwlViewPacketDecoder.decode(channel, rawData);
                        storeIncomingOwlViewDecoded(decoded);
                    } else {
                        decoded = null;
                    }
                    if (recentPacketTextFeedNeeded || packetTriggerListeners) {
                        storeRecentPacketText(channel, packetClassName, decoded, rawData);
                    }

                    if (packetTriggerListeners) {
                        JsonObject triggerData = new JsonObject();
                        triggerData.addProperty("channel", channel);
                        triggerData.addProperty("packetClass", packetClassName);
                        triggerData.addProperty("direction", "inbound");
                        if (decoded != null) {
                            triggerData.addProperty("packet", decoded);
                            triggerData.addProperty("decoded", decoded);
                        }
                        NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_PACKET, triggerData);
                        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_PACKET,
                                triggerData);
                    }

                    // 异步处理规则匹配，避免主线程卡顿
                    final String finalDecoded = decoded;
                    final String finalChannel = channel;
                    final byte[] finalRawData = rawData;
                    if (needsCapturedIdRules || needsFieldRules) {
                        executePacketProcessTask(() -> {
                            try {
                                if (needsCapturedIdRules) {
                                    CapturedIdRuleManager.processPacket(finalChannel, false, finalRawData, finalDecoded);
                                }
                                if (needsFieldRules) {
                                    String decodedForRules = finalDecoded != null && !finalDecoded.trim().isEmpty()
                                            ? finalDecoded
                                            : decodePayloadFull(finalRawData);
                                    PacketFieldRuleManager.processPacket(finalChannel, false, finalRawData,
                                            decodedForRules,
                                            packetClassName);
                                }
                            } catch (Exception e) {
                                zszlScriptMod.LOGGER.error("[PacketCapture] 异步处理数据包失败: {}", finalChannel, e);
                            }
                        }, finalRawData.length);
                    }
                }, rawData.length);
                }
            } else if (businessProcessingEnabled && inboundMsg instanceof Packet) {
                Packet<?> inboundPacket = (Packet<?>) inboundMsg;
                try {
                    boolean hasTitleListener = LegacySequenceTriggerManager
                            .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_TITLE);
                    boolean hasActionbarListener = LegacySequenceTriggerManager
                            .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_ACTIONBAR);
                    boolean hasBossbarListener = LegacySequenceTriggerManager
                            .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_BOSSBAR);
                    boolean hasItemPickupListener = LegacySequenceTriggerManager
                            .hasRulesForTrigger(LegacySequenceTriggerManager.TRIGGER_ITEM_PICKUP);
                    if (inboundPacket instanceof SPacketTitle && (hasTitleListener || hasActionbarListener)) {
                        SPacketTitle titlePacket = (SPacketTitle) inboundPacket;
                        if (titlePacket.getMessage() != null) {
                            String text = titlePacket.getMessage().getUnformattedText();
                            if (text != null && !text.trim().isEmpty()) {
                                JsonObject triggerData = new JsonObject();
                                triggerData.addProperty("text", text);
                                triggerData.addProperty("type", String.valueOf(titlePacket.getType()));
                                if (titlePacket.getType() == SPacketTitle.Type.ACTIONBAR && hasActionbarListener) {
                                    LegacySequenceTriggerManager.triggerEvent(
                                            LegacySequenceTriggerManager.TRIGGER_ACTIONBAR, triggerData);
                                } else if (hasTitleListener) {
                                    LegacySequenceTriggerManager.triggerEvent(
                                            LegacySequenceTriggerManager.TRIGGER_TITLE, triggerData);
                                }
                            }
                        }
                    }
                    if (inboundPacket instanceof SPacketUpdateBossInfo) {
                        SPacketUpdateBossInfo bossPacket = (SPacketUpdateBossInfo) inboundPacket;
                        if (bossPacket.getName() != null) {
                            String text = bossPacket.getName().getUnformattedText();
                            if (text != null && !text.trim().isEmpty()) {
                                latestBossbarText = text;
                                if (hasBossbarListener) {
                                    JsonObject triggerData = new JsonObject();
                                    triggerData.addProperty("text", text);
                                    triggerData.addProperty("operation", String.valueOf(bossPacket.getOperation()));
                                    LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_BOSSBAR,
                                            triggerData);
                                }
                            }
                        }
                    }
                    if (inboundPacket instanceof SPacketCollectItem && hasItemPickupListener) {
                        SPacketCollectItem collectPacket = (SPacketCollectItem) inboundPacket;
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.player != null && collectPacket.getEntityID() == mc.player.getEntityId()) {
                            JsonObject triggerData = new JsonObject();
                            triggerData.addProperty("collectorEntityId", collectPacket.getEntityID());
                            triggerData.addProperty("itemEntityId", collectPacket.getCollectedItemEntityID());
                            triggerData.addProperty("count", 1);
                            if (mc.world != null) {
                                Entity entity = mc.world.getEntityByID(collectPacket.getCollectedItemEntityID());
                                if (entity instanceof EntityItem) {
                                    EntityItem itemEntity = (EntityItem) entity;
                                    if (itemEntity.getItem() != null) {
                                        triggerData.addProperty("itemName", itemEntity.getItem().getDisplayName());
                                        if (itemEntity.getItem().getItem() != null
                                                && itemEntity.getItem().getItem().getRegistryName() != null) {
                                            triggerData.addProperty("registryName",
                                                    String.valueOf(itemEntity.getItem().getItem().getRegistryName()));
                                        }
                                        triggerData.addProperty("count", Math.max(1, itemEntity.getItem().getCount()));
                                    }
                                }
                            }
                            LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_ITEM_PICKUP,
                                    triggerData);
                        }
                    }
                    if (!shouldSkipStandardBusinessPayload(inboundPacket)) {
                        final String packetClassName = inboundPacket.getClass().getSimpleName();
                        boolean packetTriggerListeners = hasPacketTriggerListeners();
                        boolean recentPacketTextFeedNeeded = isRecentPacketTextFeedNeeded();
                        boolean needsCapturedIdRules = CapturedIdRuleManager.hasEnabledRulesForChannel(packetClassName, false)
                                || CapturedIdRuleManager.hasEnabledRulesForChannel("", false);
                        boolean needsFieldRules = PacketFieldRuleManager.hasEnabledRulesForChannel("", false);
                        boolean needsRawSnapshot = recentPacketTextFeedNeeded || needsCapturedIdRules || needsFieldRules;
                        if (packetTriggerListeners || needsRawSnapshot) {
                            byte[] rawData = null;
                            if (needsRawSnapshot) {
                                PacketBuffer rawBuffer = new PacketBuffer(Unpooled.buffer());
                                try {
                                    inboundPacket.writePacketData(rawBuffer);
                                    rawData = new byte[rawBuffer.readableBytes()];
                                    rawBuffer.readBytes(rawData);
                                } finally {
                                    rawBuffer.release();
                                }
                            }

                            if (recentPacketTextFeedNeeded && rawData != null) {
                                // 仅在路径序列确实需要等待“数据包文本”时才维护这份最近文本缓存。
                                storeRecentPacketText("N/A", inboundPacket.getClass().getSimpleName(), null, rawData);
                            }

                            if (packetTriggerListeners) {
                                JsonObject triggerData = new JsonObject();
                                triggerData.addProperty("channel", "N/A");
                                triggerData.addProperty("packetClass", inboundPacket.getClass().getSimpleName());
                                triggerData.addProperty("direction", "inbound");
                                triggerData.addProperty("packet", inboundPacket.getClass().getSimpleName());
                                NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_PACKET, triggerData);
                                LegacySequenceTriggerManager.triggerEvent(
                                        LegacySequenceTriggerManager.TRIGGER_PACKET, triggerData);
                            }

                            if ((needsCapturedIdRules || needsFieldRules) && rawData != null) {
                                final byte[] finalRawData = rawData;
                                final String finalPacketClassName = packetClassName;
                                executePacketProcessTask(() -> {
                                    try {
                                        if (needsCapturedIdRules) {
                                            String decodedForCapturedRules = decodePayload(finalRawData);
                                            if (decodedForCapturedRules == null || decodedForCapturedRules.trim().isEmpty()) {
                                                decodedForCapturedRules = decodePayloadFull(finalRawData);
                                            }
                                            CapturedIdRuleManager.processPacket(finalPacketClassName, false, finalRawData,
                                                    decodedForCapturedRules);
                                        }
                                        if (needsFieldRules) {
                                            PacketFieldRuleManager.processPacket("", false, finalRawData,
                                                    decodePayloadFull(finalRawData), finalPacketClassName);
                                        }
                                    } catch (Exception e) {
                                        zszlScriptMod.LOGGER.error("[PacketCapture] 异步处理标准数据包失败", e);
                                    }
                                }, finalRawData.length);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // --- 第二部分：由用户开关控制的通用捕获逻辑 ---
            // 只有当用户在GUI中打开“行为捕获”且性能面板允许时，才执行通用的数据包记录功能
            if (inboundFeatureEnabled && isCapturing && inboundMsg instanceof Packet) {
                handlePacketCapture((Packet<?>) inboundMsg, false);
            }

            // 确保原始数据包继续在Netty管道中传递
            super.channelRead(ctx, inboundMsg);
        } finally {
            timer.stop();
        }
    }
    // --- 修改结束 ---

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        boolean outboundFeatureEnabled = PerformanceMonitor.isFeatureEnabled("packet_capture_outbound");
        boolean businessProcessingEnabled = isBusinessProcessingEnabled();
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("packet_capture_outbound");
        try {
            if (businessProcessingEnabled && msg instanceof FMLProxyPacket) {
                FMLProxyPacket fmlPacket = (FMLProxyPacket) msg;
                String channel = fmlPacket.channel();
                boolean packetTriggerListeners = hasPacketTriggerListeners();
                boolean recentPacketTextFeedNeeded = isRecentPacketTextFeedNeeded();
                boolean needsCapturedIdRules = CapturedIdRuleManager.hasEnabledRulesForChannel(channel, true);
                boolean needsFieldRules = PacketFieldRuleManager.hasEnabledRulesForChannel(channel, true);
                boolean shouldProcessFmlPacket = OWL_VIEW_CHANNEL.equals(channel)
                        || OWL_CONTROL_CHANNEL.equals(channel)
                        || packetTriggerListeners
                        || recentPacketTextFeedNeeded
                        || needsCapturedIdRules
                        || needsFieldRules;
                if (!shouldProcessFmlPacket) {
                    // 无任何业务依赖时直接透传该 FML 包，避免无意义的出站解码。
                } else {

                ByteBuf outboundPayload = fmlPacket.payload();
                byte[] outboundData = new byte[outboundPayload.readableBytes()];
                outboundPayload.getBytes(outboundPayload.readerIndex(), outboundData);
                final String packetClassName = fmlPacket.getClass().getSimpleName();
                String outboundDecoded = null;
                if ("OwlViewChannel".equals(channel) || "OwlControlChannel".equals(channel)) {
                    outboundDecoded = OwlViewPacketDecoder.decode(channel, outboundData);
                }
                if (recentPacketTextFeedNeeded || packetTriggerListeners) {
                    storeRecentPacketText(channel, packetClassName, outboundDecoded, outboundData);
                }
                if (packetTriggerListeners) {
                    JsonObject triggerData = new JsonObject();
                    triggerData.addProperty("channel", channel);
                    triggerData.addProperty("packetClass", packetClassName);
                    triggerData.addProperty("direction", "outbound");
                    if (outboundDecoded != null) {
                        triggerData.addProperty("packet", outboundDecoded);
                        triggerData.addProperty("decoded", outboundDecoded);
                    }
                    NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_PACKET, triggerData);
                    LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_PACKET, triggerData);
                }

                // 异步处理规则匹配，避免主线程卡顿
                final String finalOutboundDecoded = outboundDecoded;
                final String finalChannel = channel;
                final byte[] finalOutboundData = outboundData;
                if (needsCapturedIdRules || needsFieldRules) {
                    executePacketProcessTask(() -> {
                        try {
                            if (needsCapturedIdRules) {
                                CapturedIdRuleManager.processPacket(finalChannel, true, finalOutboundData,
                                        finalOutboundDecoded);
                            }
                            if (needsFieldRules) {
                                PacketFieldRuleManager.processPacket(finalChannel, true, finalOutboundData,
                                        finalOutboundDecoded, packetClassName);
                            }
                        } catch (Exception e) {
                            zszlScriptMod.LOGGER.error("[PacketCapture] 异步处理出站数据包失败: {}", finalChannel, e);
                        }
                    }, finalOutboundData.length);
                }
                }
            }

            // --- 通用捕获逻辑，由开关控制 ---
            if (outboundFeatureEnabled && isCapturing && msg instanceof Packet) {
                handlePacketCapture((Packet<?>) msg, true);
            }

            super.write(ctx, msg, promise);
        } finally {
            timer.stop();
        }
    }

    private static String decodePayload(byte[] data) {
        return PacketPayloadDecoder.decode(data);
    }

    private static boolean executePacketProcessTask(Runnable task, int retainedBytes) {
        if (task == null) {
            return false;
        }
        int safeRetainedBytes = Math.max(0, retainedBytes);
        long reserved = packetProcessRetainedBytes.addAndGet(safeRetainedBytes);
        if (reserved > MAX_PACKET_PROCESS_RETAINED_BYTES) {
            packetProcessRetainedBytes.addAndGet(-safeRetainedBytes);
            droppedPacketProcessTaskCount.incrementAndGet();
            return false;
        }
        RetainedPacketProcessTask retainedTask = new RetainedPacketProcessTask(task, safeRetainedBytes);
        try {
            PACKET_PROCESS_EXECUTOR.execute(retainedTask);
            return true;
        } catch (RejectedExecutionException rejected) {
            retainedTask.releaseReservation();
            droppedPacketProcessTaskCount.incrementAndGet();
            return false;
        }
    }

    private static String decodePayloadFull(byte[] data) {
        return PacketPayloadDecoder.decodeFull(data);
    }

    private void handlePacketCapture(Packet<?> packet, boolean isSent) {
        try {
            if (!shouldSampleCapture()) {
                droppedPacketCount++;
                return;
            }

            PendingPacketSnapshot snapshot = buildSnapshot(packet, isSent);
            if (snapshot == null) {
                return;
            }

            if (!shouldCapture(snapshot)) {
                return;
            }

            int snapshotBytes = snapshot.rawData == null ? 0 : snapshot.rawData.length;
            if (snapshotBytes > MAX_SINGLE_CAPTURE_BYTES) {
                droppedPacketCount++;
                return;
            }

            boolean droppedForCapacity = false;
            synchronized (pendingCaptureQueue) {
                while (!pendingCaptureQueue.isEmpty()
                        && (pendingCaptureQueue.size() >= MAX_CAPTURE_QUEUE
                                || pendingCaptureBytes.get() + snapshotBytes > MAX_CAPTURE_QUEUE_BYTES)) {
                    PendingPacketSnapshot dropped = pendingCaptureQueue.poll();
                    if (dropped != null) {
                        pendingCaptureBytes.addAndGet(-(dropped.rawData == null ? 0 : dropped.rawData.length));
                        droppedPacketCount++;
                        droppedForCapacity = true;
                    }
                }
                if (pendingCaptureBytes.get() + snapshotBytes > MAX_CAPTURE_QUEUE_BYTES) {
                    droppedPacketCount++;
                    return;
                }
                pendingCaptureQueue.offer(snapshot);
                pendingCaptureBytes.addAndGet(snapshotBytes);
                lastKnownCaptureQueueSize = pendingCaptureQueue.size();
            }

            long now = System.currentTimeMillis();
            if (droppedForCapacity && now - lastCaptureDropWarnAt > 3000L) {
                lastCaptureDropWarnAt = now;
                zszlScriptMod.LOGGER.warn("[PacketCapture] 捕获流量过高，已限流以防止内存持续增长。queue={}",
                        lastKnownCaptureQueueSize);
            }
            scheduleDrainCaptureQueue();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("捕获并序列化数据包时出错: " + packet.getClass().getName(), e);
            if (Minecraft.getMinecraft().player != null) {
                String packetSimpleName = packet.getClass().getSimpleName();
                // SPacketSpawnPlayer 在部分场景下可能无法稳定序列化，避免在聊天栏刷屏干扰用户。
                if (!"SPacketSpawnPlayer".equals(packetSimpleName)) {
                    String errorMessage = "§c[数据包捕获失败] " + packetSimpleName;
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(errorMessage));
                }
            }
        }
    }

    private PendingPacketSnapshot buildSnapshot(Packet<?> packet, boolean isSent) throws Exception {
        String packetClassName = packet.getClass().getSimpleName();
        String channel = "N/A";
        byte[] rawData;
        boolean isFml = packet instanceof FMLProxyPacket;
        Integer packetId = null;

        if (isFml) {
            FMLProxyPacket fmlPacket = (FMLProxyPacket) packet;
            channel = fmlPacket.channel();
            ByteBuf payload = fmlPacket.payload();
            if (payload.readableBytes() > MAX_SINGLE_CAPTURE_BYTES) {
                return null;
            }
            rawData = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), rawData);
        } else {
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                EnumPacketDirection direction = isSent ? EnumPacketDirection.SERVERBOUND
                        : EnumPacketDirection.CLIENTBOUND;
                packetId = EnumConnectionState.PLAY.getPacketId(direction, packet);
                packet.writePacketData(buffer);
                if (buffer.readableBytes() > MAX_SINGLE_CAPTURE_BYTES) {
                    return null;
                }
                rawData = new byte[buffer.readableBytes()];
                buffer.readBytes(rawData);
            } finally {
                buffer.release();
            }
        }

        return new PendingPacketSnapshot(packetClassName, isFml, packetId, channel, rawData, isSent);
    }

    private boolean shouldCapture(PendingPacketSnapshot snapshot) {
        PacketFilterConfig config = PacketFilterConfig.INSTANCE;
        if (config == null) {
            return true;
        }

        if (config.captureMode == CaptureMode.WHITELIST) {
            if (config.whitelistFilters == null || config.whitelistFilters.isEmpty()) {
                return true;
            }
            for (String filter : config.whitelistFilters) {
                if (packetMatchesFilter(snapshot, filter)) {
                    return true;
                }
            }
            return false;
        }

        if (config.blacklistFilters == null || config.blacklistFilters.isEmpty()) {
            return true;
        }
        for (String filter : config.blacklistFilters) {
            if (packetMatchesFilter(snapshot, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean packetMatchesFilter(PendingPacketSnapshot snapshot, String keyword) {
        if (snapshot == null || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        String lowerKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);

        String packetClassLower = safeLower(snapshot.packetClassName);
        String packetChannelLower = safeLower(snapshot.channel);
        String packetIdHexLower = snapshot.packetId == null ? "" : String.format("0x%02x", snapshot.packetId);
        String packetIdDecLower = snapshot.packetId == null ? "" : String.valueOf(snapshot.packetId);

        if (isRegexKeyword(keyword)) {
            return packetMatchesRegex(extractRegexPattern(keyword), packetClassLower, packetChannelLower,
                    packetIdHexLower, packetIdDecLower);
        }

        if (packetClassLower.contains(lowerKeyword) || packetChannelLower.contains(lowerKeyword)
                || packetIdHexLower.contains(lowerKeyword) || packetIdDecLower.contains(lowerKeyword)) {
            return true;
        }

        if (requiresDeepPayloadInspection(lowerKeyword, normalizedKeyword)) {
            String packetHexNoSpace = ByteBufUtil.hexDump(snapshot.rawData == null ? new byte[0] : snapshot.rawData)
                    .toLowerCase(Locale.ROOT);
            String cleanedHexKeyword = normalizeHexText(lowerKeyword);
            if (!cleanedHexKeyword.isEmpty() && lowerKeyword.matches("^[0-9a-f\\s,:]+$")
                    && packetHexNoSpace.contains(cleanedHexKeyword)) {
                return true;
            }

            if (containsNonAscii(normalizedKeyword)) {
                String utf8Hex = toUtf8HexNoSpace(normalizedKeyword);
                if (!utf8Hex.isEmpty() && packetHexNoSpace.contains(utf8Hex)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean requiresDeepPayloadInspection(String lowerKeyword, String normalizedKeyword) {
        if (lowerKeyword == null || normalizedKeyword == null) {
            return false;
        }
        return lowerKeyword.matches("^[0-9a-f\\s,:]+$") || containsNonAscii(normalizedKeyword);
    }

    private boolean isRegexKeyword(String keyword) {
        if (keyword == null) {
            return false;
        }
        String text = keyword.trim();
        return text.startsWith("re:") || (text.startsWith("/") && text.endsWith("/") && text.length() > 2);
    }

    private String extractRegexPattern(String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        if (text.startsWith("re:")) {
            return text.substring(3);
        }
        if (text.startsWith("/") && text.endsWith("/") && text.length() > 2) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private boolean packetMatchesRegex(String regex, String... haystacks) {
        if (regex == null || regex.isEmpty()) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            for (String hay : haystacks) {
                if (hay != null && pattern.matcher(hay).find()) {
                    return true;
                }
            }
            return false;
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private boolean containsNonAscii(String str) {
        if (str == null) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    private String toUtf8HexNoSpace(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private void scheduleDrainCaptureQueue() {
        if (!captureDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(this::drainCaptureQueueOnMainThread);
    }

    private void drainCaptureQueueOnMainThread() {
        captureDrainScheduled.set(false);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            synchronized (pendingCaptureQueue) {
                pendingCaptureQueue.clear();
                pendingCaptureBytes.set(0L);
            }
            lastKnownCaptureQueueSize = 0;
            return;
        }

        int processed = 0;
        int processedBytes = 0;
        long startNanos = System.nanoTime();
        while (processed < MAX_CAPTURE_PROCESS_PER_TICK) {
            if (processedBytes >= MAX_CAPTURE_PROCESS_BYTES_PER_TICK) {
                break;
            }
            if (System.nanoTime() - startNanos >= MAX_CAPTURE_PROCESS_NANOS_PER_TICK) {
                break;
            }

            PendingPacketSnapshot snapshot;
            synchronized (pendingCaptureQueue) {
                snapshot = pendingCaptureQueue.poll();
                if (snapshot != null) {
                    int rawBytes = snapshot.rawData == null ? 0 : snapshot.rawData.length;
                    pendingCaptureBytes.updateAndGet(value -> Math.max(0L, value - rawBytes));
                }
            }
            if (snapshot == null) {
                break;
            }

            CapturedPacketData packetData = new CapturedPacketData(snapshot.packetClassName, snapshot.isFmlPacket,
                    snapshot.packetId, snapshot.channel, snapshot.rawData, null);

            if (snapshot.isSent) {
                appendCapturedPacket(capturedPackets, packetData, capturedSentRawBytes);
            } else {
                appendCapturedPacket(capturedReceivedPackets, packetData, capturedReceivedRawBytes);
            }
            PacketIdRecordManager.recordCapturedPacket(snapshot.isSent, packetData);

            processed++;
            processedBytes += snapshot.rawData == null ? 0 : snapshot.rawData.length;
        }

        if (!pendingCaptureQueue.isEmpty()) {
            lastKnownCaptureQueueSize = pendingCaptureQueue.size();
            scheduleDrainCaptureQueue();
        } else {
            lastKnownCaptureQueueSize = 0;
        }
    }

    private static void appendCapturedPacket(List<CapturedPacketData> target, CapturedPacketData data,
            AtomicLong retainedRawBytes) {
        synchronized (target) {
            int limit = resolveMaxCapturedPackets();
            boolean added = false;
            if (!target.isEmpty()) {
                CapturedPacketData last = target.get(target.size() - 1);
                if (last.canAggregate(data)) {
                    last.mergeFrom(data);
                } else {
                    target.add(data);
                    added = true;
                }
            } else {
                target.add(data);
                added = true;
            }
            long currentBytes = added ? retainedRawBytes.addAndGet(data.getPayloadSize()) : retainedRawBytes.get();
            int countTrim = Math.max(0, target.size() - limit);
            for (int i = 0; i < countTrim; i++) {
                currentBytes -= target.get(i).getPayloadSize();
            }
            int removeCount = countTrim;
            while (removeCount < target.size() && currentBytes > MAX_CAPTURED_RAW_BYTES_PER_DIRECTION) {
                currentBytes -= target.get(removeCount).getPayloadSize();
                removeCount++;
            }
            if (removeCount > 0) {
                target.subList(0, removeCount).clear();
                retainedRawBytes.set(Math.max(0L, currentBytes));
            }
        }
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private String normalizeKeyword(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u00A0', ' ').trim();
    }

    private String normalizeHexText(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
    }

    private static int resolveMaxCapturedPackets() {
        int fallback = MAX_CAPTURED_PACKETS;
        PacketFilterConfig cfg = PacketFilterConfig.INSTANCE;
        if (cfg == null) {
            return fallback;
        }
        int value = cfg.maxCapturedPackets;
        if (value <= 0) {
            return fallback;
        }
        if (value < 100) {
            return 100;
        }
        if (value > 10000) {
            return 10000;
        }
        return value;
    }

    private static void storeIncomingOwlViewDecoded(String decoded) {
        if (decoded == null)
            return;
        synchronized (recentOwlViewDecoded) {
            recentOwlViewDecoded.add(truncateRecentEntry(decoded));
            trimRecentStrings(recentOwlViewDecoded, MAX_RECENT_OWLVIEW_HEX, MAX_RECENT_TEXT_CHARS);
        }
    }

    private static void storeRecentPacketText(String channel, String packetClassName, String decodedText,
            byte[] rawData) {
        StringBuilder sb = new StringBuilder();
        if (packetClassName != null && !packetClassName.trim().isEmpty()) {
            sb.append(packetClassName.trim());
        }
        if (channel != null && !channel.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(channel.trim());
        }
        if (decodedText != null && !decodedText.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(decodedText.trim());
        } else {
            String fallbackDecoded = shouldSkipRecentTextPayloadDecode(packetClassName, rawData)
                    ? ""
                    : decodePayload(rawData);
            if (fallbackDecoded != null && !fallbackDecoded.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(fallbackDecoded.trim());
            }
        }

        String packetText = truncateRecentEntry(sb.toString().trim());
        if (packetText.isEmpty()) {
            return;
        }

        synchronized (recentPacketTexts) {
            recentPacketTexts.add(packetText);
            trimRecentStrings(recentPacketTexts, MAX_RECENT_PACKET_TEXTS, MAX_RECENT_TEXT_CHARS);
            recentPacketTextVersion++;
        }
    }

    private static String truncateRecentEntry(String value) {
        if (value == null || value.length() <= MAX_RECENT_ENTRY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_RECENT_ENTRY_CHARS) + "...";
    }

    private static void trimRecentStrings(List<String> values, int maxEntries, int maxChars) {
        int totalChars = 0;
        for (String value : values) {
            totalChars += value == null ? 0 : value.length();
        }
        while (!values.isEmpty() && (values.size() > maxEntries || totalChars > maxChars)) {
            String removed = values.remove(0);
            totalChars -= removed == null ? 0 : removed.length();
        }
    }

    private static boolean shouldSkipStandardBusinessPayload(Packet<?> packet) {
        if (packet == null) {
            return true;
        }
        String packetClassName = packet.getClass().getSimpleName();
        return "SPacketSpawnPlayer".equals(packetClassName)
                || "SPacketSpawnMob".equals(packetClassName)
                || isWorldStreamingPacket(packetClassName);
    }

    private static boolean shouldSkipRecentTextPayloadDecode(String packetClassName, byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return true;
        }
        return rawData.length > MAX_RECENT_TEXT_DECODE_BYTES || isWorldStreamingPacket(packetClassName);
    }

    private static boolean isWorldStreamingPacket(String packetClassName) {
        String name = packetClassName == null ? "" : packetClassName.toLowerCase(Locale.ROOT);
        return name.contains("chunk")
                || name.contains("lightupdate")
                || name.contains("levelchunk")
                || name.contains("forgetlevelchunk")
                || name.contains("chunksbiomes");
    }

    public static List<String> getRecentOwlViewDecodedSnapshot() {
        synchronized (recentOwlViewDecoded) {
            return new ArrayList<>(recentOwlViewDecoded);
        }
    }

    public static void clearRecentOwlViewDecoded() {
        synchronized (recentOwlViewDecoded) {
            recentOwlViewDecoded.clear();
        }
    }

}
