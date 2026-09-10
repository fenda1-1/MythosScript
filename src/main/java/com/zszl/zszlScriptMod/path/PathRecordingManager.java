package com.zszl.zszlScriptMod.path;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.resources.I18n;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

/**
 * 管理自定义路径录制的状态和数据
 */
public class PathRecordingManager {

    // 内部类，用于存储每一步录制的详细信息
    public static class RecordedStep {
        public final Vec3d playerPos;
        public final float playerYaw;
        public final float playerPitch;
        public final BlockPos chestPos;
        private final List<PathSequenceManager.ActionData> actions = new ArrayList<>();

        public RecordedStep(Vec3d playerPos, float playerYaw, float playerPitch, BlockPos chestPos) {
            this.playerPos = playerPos;
            this.playerYaw = playerYaw;
            this.playerPitch = playerPitch;
            this.chestPos = chestPos;
        }

        public List<PathSequenceManager.ActionData> getActions() {
            return actions;
        }

        private void addAction(String type, JsonObject params) {
            // Keep consecutive captured interactions in this step separated during playback.
            if (!actions.isEmpty()) {
                JsonObject delayParams = new JsonObject();
                delayParams.addProperty("ticks", 10);
                actions.add(new PathSequenceManager.ActionData("delay", delayParams));
            }
            actions.add(new PathSequenceManager.ActionData(type, params));
        }

        @Override
        public String toString() {
            if (chestPos != null) {
                return I18n.format("path.record.step.desc",
                        playerPos.x, playerPos.y, playerPos.z,
                        chestPos.getX(), chestPos.getY(), chestPos.getZ());
            }
            return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f · %d 个动作",
                    playerPos.x, playerPos.y, playerPos.z, actions.size());
        }
    }

    private static boolean isRecording = false;
    private static boolean isPaused = false;
    private static double influenceRadius = 10.0D;
    private static int packetRecordWindowSeconds = RecordingPacketSupport.DEFAULT_WINDOW_SECONDS;
    private static long packetCaptureStartedAt;
    private static boolean packetCaptureWasEnabled;
    private static boolean packetCaptureOwnedByRecording;
    private static long revision;
    private static final List<RecordedStep> recordedSteps = new ArrayList<>();
    private static final Set<Integer> recordedCombatEntityIds = new HashSet<>();
    private static final RecordingEventListener eventListener = new RecordingEventListener();

    /**
     * 开始录制
     */
    public static void startRecording() {
        if (isRecording)
            return;
        isRecording = true;
        isPaused = false;
        packetCaptureWasEnabled = PacketCaptureHandler.isCapturing;
        packetCaptureOwnedByRecording = !packetCaptureWasEnabled;
        packetCaptureStartedAt = System.currentTimeMillis();
        // InputTimelineManager and the packet browser use this same switch.
        // Recording owns it only when the user had not already enabled capture.
        PacketCaptureHandler.isCapturing = true;
        recordedSteps.clear();
        recordedCombatEntityIds.clear();
        eventListener.resetScreenObservation();
        revision++;
        MinecraftForge.EVENT_BUS.register(eventListener);
        zszlScriptMod.LOGGER.info(I18n.format("log.path.recording_started"));
    }

    /**
     * 停止并放弃录制
     */
    public static void stopAndClearRecording() {
        if (!isRecording)
            return;
        eventListener.flushPendingInput();
        refreshPacketAssociations(true);
        isRecording = false;
        isPaused = false;
        recordedSteps.clear();
        recordedCombatEntityIds.clear();
        revision++;
        MinecraftForge.EVENT_BUS.unregister(eventListener);
        restorePacketCaptureState();
        zszlScriptMod.LOGGER.info(I18n.format("log.path.recording_stopped_cleared"));
    }

    /**
     * 完成录制（不清空数据，等待保存）
     */
    public static void finishRecording() {
        if (!isRecording)
            return;
        eventListener.flushPendingInput();
        refreshPacketAssociations(true);
        isRecording = false;
        isPaused = false;
        MinecraftForge.EVENT_BUS.unregister(eventListener);
        restorePacketCaptureState();
        zszlScriptMod.LOGGER.info(I18n.format("log.path.recording_finished"));
    }

    private static void restorePacketCaptureState() {
        if (packetCaptureOwnedByRecording) {
            PacketCaptureHandler.isCapturing = packetCaptureWasEnabled;
        }
        packetCaptureOwnedByRecording = false;
        packetCaptureWasEnabled = false;
        packetCaptureStartedAt = 0L;
    }

    /**
     * 添加一个录制步骤
     * 
     * @param step 录制的步骤数据
     */
    public static void addStep(RecordedStep step) {
        if (isRecording && !isPaused) {
            recordedSteps.add(step);
            revision++;
        }
    }

    public static void togglePaused() {
        if (isRecording) {
            isPaused = !isPaused;
        }
    }

    public static boolean isPaused() {
        return isPaused;
    }

    public static void setInfluenceRadius(double radius) {
        if (!Double.isNaN(radius) && !Double.isInfinite(radius)) {
            influenceRadius = Math.max(1.0D, Math.min(64.0D, radius));
        }
    }

    public static double getInfluenceRadius() {
        return influenceRadius;
    }

    public static synchronized void setPacketRecordWindowSeconds(int seconds) {
        packetRecordWindowSeconds = RecordingPacketSupport.clampWindowSeconds(seconds);
    }

    public static int getPacketRecordWindowSeconds() {
        return packetRecordWindowSeconds;
    }

    /** @deprecated use {@link #setPacketRecordWindowSeconds(int)}. */
    @Deprecated
    public static synchronized void setPacketRecordRange(int range) {
        setPacketRecordWindowSeconds(range);
    }

    /** @deprecated use {@link #getPacketRecordWindowSeconds()}. */
    @Deprecated
    public static int getPacketRecordRange() {
        return getPacketRecordWindowSeconds();
    }

    public static long getRevision() {
        return revision;
    }

    /** Records a meaningful interaction and groups consecutive interactions by block position. */
    public static synchronized void recordAction(Vec3d playerPos, float yaw, float pitch,
            String type, JsonObject params) {
        if (!isRecording || isPaused || playerPos == null || type == null || type.trim().isEmpty()) {
            return;
        }
        RecordedStep step = lastStepAt(playerPos);
        if (step == null) {
            step = new RecordedStep(playerPos, yaw, pitch, null);
            recordedSteps.add(step);
        }
        step.addAction(type, params);
        revision++;
    }

    /** Records an input action and keeps its timestamp for packet correlation. */
    public static synchronized void recordInputAction(Vec3d playerPos, float yaw, float pitch,
            String type, JsonObject params, long inputTimestamp) {
        if (!isRecording || isPaused || playerPos == null || type == null || type.trim().isEmpty()) {
            return;
        }
        JsonObject inputParams = params == null ? new JsonObject() : params;
        inputParams.addProperty(RecordingPacketSupport.INPUT_TIMESTAMP_KEY,
                inputTimestamp > 0L ? inputTimestamp : System.currentTimeMillis());
        inputParams.addProperty(RecordingPacketSupport.WINDOW_SECONDS_KEY, packetRecordWindowSeconds);
        inputParams.remove(RecordingPacketSupport.RANGE_KEY);
        recordAction(playerPos, yaw, pitch, type, inputParams);
    }

    public static synchronized void recordInputAction(String type, JsonObject params, long inputTimestamp) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc != null && mc.player != null) {
            recordInputAction(mc.player.getPositionVector(), mc.player.rotationYaw, mc.player.rotationPitch,
                    type, params, inputTimestamp);
        }
    }

    /**
     * Updates the packet arrays attached to input actions. During recording it
     * waits briefly for the post-input side of the range; finishing recording
     * forces the final snapshot even when the server sent fewer packets.
     */
    public static synchronized void refreshPacketAssociations(boolean force) {
        if (recordedSteps.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (RecordedStep step : recordedSteps) {
            if (step == null) continue;
            for (PathSequenceManager.ActionData action : step.getActions()) {
                if (action == null || action.params == null || !action.params.has(RecordingPacketSupport.INPUT_TIMESTAMP_KEY)) {
                    continue;
                }
                JsonObject params = action.params;
                long inputTimestamp;
                try {
                    inputTimestamp = params.get(RecordingPacketSupport.INPUT_TIMESTAMP_KEY).getAsLong();
                } catch (Exception ignored) {
                    continue;
                }
                boolean legacyCountRange = !params.has(RecordingPacketSupport.WINDOW_SECONDS_KEY)
                        && params.has(RecordingPacketSupport.RANGE_KEY);
                int windowSeconds = params.has(RecordingPacketSupport.WINDOW_SECONDS_KEY)
                        ? safePacketWindow(params.get(RecordingPacketSupport.WINDOW_SECONDS_KEY).getAsInt())
                        : packetRecordWindowSeconds;
                if (!force && !packetAssociationReady(inputTimestamp, windowSeconds, now)) {
                    continue;
                }
                List<RecordingPacketSupport.PacketEntry> entries = legacyCountRange
                        ? RecordingPacketSupport.snapshotAroundCount(inputTimestamp,
                                safePacketRange(params.get(RecordingPacketSupport.RANGE_KEY).getAsInt()), packetCaptureStartedAt)
                        : RecordingPacketSupport.snapshotAround(inputTimestamp, windowSeconds, packetCaptureStartedAt);
                JsonArray packets = RecordingPacketSupport.toJson(entries);
                String previous = params.has(RecordingPacketSupport.PACKETS_KEY)
                        ? params.get(RecordingPacketSupport.PACKETS_KEY).toString() : "";
                if (!packets.toString().equals(previous)) {
                    params.add(RecordingPacketSupport.PACKETS_KEY, packets);
                    changed = true;
                }
            }
        }
        if (changed) {
            revision++;
        }
    }

    private static boolean packetAssociationReady(long inputTimestamp, int windowSeconds, long now) {
        if (windowSeconds <= 0) return true;
        long waitMillis = windowSeconds * 1000L;
        return now >= inputTimestamp && now - inputTimestamp >= waitMillis;
    }

    private static int safePacketWindow(int value) {
        return RecordingPacketSupport.clampWindowSeconds(value);
    }

    private static int safePacketRange(int value) {
        return RecordingPacketSupport.clampRange(value);
    }

    public static synchronized void recordAction(String type, JsonObject params) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc != null && mc.player != null) {
            recordAction(mc.player.getPositionVector(), mc.player.rotationYaw, mc.player.rotationPitch, type, params);
        }
    }

    public static synchronized boolean markCombatEntity(int entityId) {
        return isRecording && !isPaused && recordedCombatEntityIds.add(Integer.valueOf(entityId));
    }

    public static synchronized void recordContainerClick(int slot, int button, String clickType, int count,
            int screenX, int screenY, String title) {
        JsonObject params = new JsonObject();
        params.addProperty("locatorMode", "DIRECT_SLOT");
        params.addProperty("windowId", "-1");
        params.addProperty("slot", slot);
        params.addProperty("slotBase", "DEC");
        params.addProperty("button", button);
        params.addProperty("clickType", clickType == null ? "PICKUP" : clickType);
        params.addProperty("containerTitle", title == null ? "" : title);
        params.addProperty("slotCount", Math.max(0, count));
        params.addProperty("screenX", screenX);
        params.addProperty("screenY", screenY);
        recordInputAction("window_click", params, System.currentTimeMillis());
    }

    public static synchronized void recordScreenClick(int screenX, int screenY, int button, String title) {
        JsonObject params = new JsonObject();
        params.addProperty("locatorMode", "COORDINATE");
        params.addProperty("x", screenX);
        params.addProperty("y", screenY);
        params.addProperty("left", button == 1 ? "RIGHT" : button == 2 ? "MIDDLE" : "LEFT");
        params.addProperty("guiTitle", title == null ? "" : title);
        recordInputAction("click", params, System.currentTimeMillis());
    }

    private static RecordedStep lastStepAt(Vec3d position) {
        if (recordedSteps.isEmpty()) {
            return null;
        }
        RecordedStep last = recordedSteps.get(recordedSteps.size() - 1);
        if (last == null || last.playerPos == null) {
            return null;
        }
        return Math.floor(last.playerPos.x) == Math.floor(position.x)
                && Math.floor(last.playerPos.y) == Math.floor(position.y)
                && Math.floor(last.playerPos.z) == Math.floor(position.z) ? last : null;
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static List<RecordedStep> getRecordedSteps() {
        return recordedSteps;
    }
}
