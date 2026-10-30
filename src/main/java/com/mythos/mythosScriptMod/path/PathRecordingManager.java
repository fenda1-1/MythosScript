package com.mythos.mythosScriptMod.path;

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

import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.utils.PacketCaptureHandler;

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
    private static int packetRecordCount = RecordingPacketSupport.DEFAULT_PACKET_COUNT;
    private static long packetCaptureStartedAt;
    private static boolean packetCaptureWasEnabled;
    private static boolean packetCaptureOwnedByRecording;
    /** 录制开始时挂载的会话；中途切换挂载也要把开关还给它，而不是还给新会话。 */
    private static String packetCaptureOwner = "";
    /** When true, startRecording() force-enables packet capture for the session. */
    public static boolean recordPacketCapture = false;
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
        packetCaptureOwner = PacketCaptureHandler.foregroundOwner();
        packetCaptureWasEnabled = PacketCaptureHandler.isCapturing(packetCaptureOwner);
        packetCaptureOwnedByRecording = !packetCaptureWasEnabled;
        packetCaptureStartedAt = System.currentTimeMillis();
        // InputTimelineManager and the packet browser use this same switch.
        // Recording owns it only when the user had not already enabled capture.
        if (recordPacketCapture) {
            PacketCaptureHandler.setCapturing(packetCaptureOwner, true);
        }
        recordedSteps.clear();
        recordedCombatEntityIds.clear();
        eventListener.resetScreenObservation();
        revision++;
        MinecraftForge.EVENT_BUS.register(eventListener);
        mythosScriptMod.LOGGER.info(I18n.format("log.path.recording_started"));
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
        mythosScriptMod.LOGGER.info(I18n.format("log.path.recording_stopped_cleared"));
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
        mythosScriptMod.LOGGER.info(I18n.format("log.path.recording_finished"));
    }

    private static void restorePacketCaptureState() {
        if (packetCaptureOwnedByRecording) {
            PacketCaptureHandler.setCapturing(packetCaptureOwner, packetCaptureWasEnabled);
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

    public static synchronized void setPacketRecordCount(int count) {
        packetRecordCount = RecordingPacketSupport.clampPacketCount(count);
    }

    public static int getPacketRecordCount() {
        return packetRecordCount;
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
        inputParams.addProperty(RecordingPacketSupport.COUNT_KEY, packetRecordCount);
        inputParams.remove(RecordingPacketSupport.WINDOW_SECONDS_KEY);
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
     * Updates the packet arrays attached to input actions. Only the packets
     * captured at or after the input timestamp are kept — a server cannot
     * respond before the action is sent. During recording each refresh waits
     * until the requested count has arrived after the input; finishing forces
     * the final snapshot with whatever exists.
     */
    public static synchronized void refreshPacketAssociations(boolean force) {
        if (recordedSteps.isEmpty()) {
            return;
        }
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
                int count = params.has(RecordingPacketSupport.COUNT_KEY)
                        ? safePacketCount(params.get(RecordingPacketSupport.COUNT_KEY).getAsInt())
                        : packetRecordCount;
                if (!force && RecordingPacketSupport.countAfter(inputTimestamp, packetCaptureStartedAt) < count) {
                    continue;
                }
                List<RecordingPacketSupport.PacketEntry> entries =
                        RecordingPacketSupport.snapshotAfter(inputTimestamp, count, packetCaptureStartedAt);
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

    private static int safePacketCount(int value) {
        return RecordingPacketSupport.clampPacketCount(value);
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
