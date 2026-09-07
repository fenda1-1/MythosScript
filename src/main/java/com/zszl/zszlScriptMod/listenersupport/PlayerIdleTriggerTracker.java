package com.zszl.zszlScriptMod.listenersupport;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import net.minecraft.client.Minecraft;

public final class PlayerIdleTriggerTracker {

    private static final double PLAYER_IDLE_HORIZONTAL_DISTANCE_THRESHOLD = 0.08D;
    private static final double PLAYER_IDLE_VERTICAL_DISTANCE_THRESHOLD = 0.12D;
    private static final double PLAYER_IDLE_HORIZONTAL_MOTION_THRESHOLD = 0.08D;
    private static final double PLAYER_IDLE_VERTICAL_MOTION_THRESHOLD = 0.12D;
    private static final int DAMAGE_IDLE_SETTLE_REQUIRED_TICKS = 3;

    private double lastIdleCheckPosX = 0.0D;
    private double lastIdleCheckPosY = 0.0D;
    private double lastIdleCheckPosZ = 0.0D;
    private boolean hasIdleCheckPos = false;
    private long playerIdleStartMs = -1L;
    private int playerIdleStartTick = -1;
    private long playerIdleStartMsIgnoringDamage = -1L;
    private int playerIdleStartTickIgnoringDamage = -1;
    private long playerIdleStartMsExcludingPath = -1L;
    private int playerIdleStartTickExcludingPath = -1;
    private long playerIdleStartMsExcludingPathIgnoringDamage = -1L;
    private int playerIdleStartTickExcludingPathIgnoringDamage = -1;
    private boolean damageRecoveryIdleBypassActive = false;
    private int damageRecoverySettledTicks = 0;

    public void update(Minecraft mc, boolean playerDeadNow, int clientTickCounter) {
        if (mc == null || mc.player == null || mc.world == null || playerDeadNow) {
            reset();
            return;
        }
        if (!this.hasIdleCheckPos) {
            captureReference(mc);
            return;
        }

        updateDamageRecoveryIdleBypassState(mc);

        boolean standingStill = isPlayerStandingStill(mc, false);
        boolean standingStillIgnoringDamage = isPlayerStandingStill(mc, true);

        if (!standingStill) {
            this.playerIdleStartMs = -1L;
            this.playerIdleStartTick = -1;
            this.playerIdleStartMsExcludingPath = -1L;
            this.playerIdleStartTickExcludingPath = -1;
        }

        if (!standingStillIgnoringDamage) {
            this.playerIdleStartMsIgnoringDamage = -1L;
            this.playerIdleStartTickIgnoringDamage = -1;
            this.playerIdleStartMsExcludingPathIgnoringDamage = -1L;
            this.playerIdleStartTickExcludingPathIgnoringDamage = -1;
        }

        if (!standingStill && !standingStillIgnoringDamage) {
            captureReference(mc);
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (standingStill && this.playerIdleStartMs < 0L) {
            this.playerIdleStartMs = nowMs;
            this.playerIdleStartTick = clientTickCounter;
        }
        if (standingStillIgnoringDamage && this.playerIdleStartMsIgnoringDamage < 0L) {
            this.playerIdleStartMsIgnoringDamage = nowMs;
            this.playerIdleStartTickIgnoringDamage = clientTickCounter;
        }
        boolean sequenceRunning = PathSequenceEventListener.isAnySequenceRunning();
        if (sequenceRunning) {
            this.playerIdleStartMsExcludingPath = -1L;
            this.playerIdleStartTickExcludingPath = -1;
            this.playerIdleStartMsExcludingPathIgnoringDamage = -1L;
            this.playerIdleStartTickExcludingPathIgnoringDamage = -1;
        } else if (standingStill && this.playerIdleStartMsExcludingPath < 0L) {
            this.playerIdleStartMsExcludingPath = nowMs;
            this.playerIdleStartTickExcludingPath = clientTickCounter;
        } else if (standingStillIgnoringDamage && this.playerIdleStartMsExcludingPathIgnoringDamage < 0L) {
            this.playerIdleStartMsExcludingPathIgnoringDamage = nowMs;
            this.playerIdleStartTickExcludingPathIgnoringDamage = clientTickCounter;
        }

        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("idleMs",
                this.playerIdleStartMs < 0L ? 0L : Math.max(0L, nowMs - this.playerIdleStartMs));
        triggerData.addProperty("idleTicks",
                this.playerIdleStartTick < 0 ? 0 : Math.max(0, clientTickCounter - this.playerIdleStartTick));
        triggerData.addProperty("idleMsIgnoringDamage",
                this.playerIdleStartMsIgnoringDamage < 0L ? 0L
                        : Math.max(0L, nowMs - this.playerIdleStartMsIgnoringDamage));
        triggerData.addProperty("idleTicksIgnoringDamage",
                this.playerIdleStartTickIgnoringDamage < 0 ? 0
                        : Math.max(0, clientTickCounter - this.playerIdleStartTickIgnoringDamage));
        triggerData.addProperty("idleMsExcludingPathTracking",
                this.playerIdleStartMsExcludingPath < 0L ? 0L
                        : Math.max(0L, nowMs - this.playerIdleStartMsExcludingPath));
        triggerData.addProperty("idleTicksExcludingPathTracking",
                this.playerIdleStartTickExcludingPath < 0 ? 0
                        : Math.max(0, clientTickCounter - this.playerIdleStartTickExcludingPath));
        triggerData.addProperty("idleMsExcludingPathTrackingIgnoringDamage",
                this.playerIdleStartMsExcludingPathIgnoringDamage < 0L ? 0L
                        : Math.max(0L, nowMs - this.playerIdleStartMsExcludingPathIgnoringDamage));
        triggerData.addProperty("idleTicksExcludingPathTrackingIgnoringDamage",
                this.playerIdleStartTickExcludingPathIgnoringDamage < 0 ? 0
                        : Math.max(0, clientTickCounter - this.playerIdleStartTickExcludingPathIgnoringDamage));
        triggerData.addProperty("pathTrackingActive", sequenceRunning);
        triggerData.addProperty("recentlyHurt", isDamageRecoveryIdleBypassActive(mc));
        triggerData.addProperty("x", mc.player.posX);
        triggerData.addProperty("y", mc.player.posY);
        triggerData.addProperty("z", mc.player.posZ);
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_PLAYER_IDLE, triggerData);
        captureReference(mc);
    }

    public void reset() {
        this.hasIdleCheckPos = false;
        this.playerIdleStartMs = -1L;
        this.playerIdleStartTick = -1;
        this.playerIdleStartMsIgnoringDamage = -1L;
        this.playerIdleStartTickIgnoringDamage = -1;
        this.playerIdleStartMsExcludingPath = -1L;
        this.playerIdleStartTickExcludingPath = -1;
        this.playerIdleStartMsExcludingPathIgnoringDamage = -1L;
        this.playerIdleStartTickExcludingPathIgnoringDamage = -1;
        this.damageRecoveryIdleBypassActive = false;
        this.damageRecoverySettledTicks = 0;
        this.lastIdleCheckPosX = 0.0D;
        this.lastIdleCheckPosY = 0.0D;
        this.lastIdleCheckPosZ = 0.0D;
    }

    private boolean isPlayerStandingStill(Minecraft mc, boolean ignoreDamageReset) {
        if (mc == null || mc.player == null) {
            return false;
        }
        if (hasPlayerMovementInput(mc)) {
            return false;
        }
        if (ignoreDamageReset && isDamageRecoveryIdleBypassActive(mc)) {
            if (!mc.player.isRiding() && !mc.player.isInWater() && !mc.player.isInLava()) {
                return true;
            }
        }
        if (!mc.player.onGround || mc.player.isRiding() || mc.player.isInWater() || mc.player.isInLava()) {
            return false;
        }

        double dx = mc.player.posX - this.lastIdleCheckPosX;
        double dy = mc.player.posY - this.lastIdleCheckPosY;
        double dz = mc.player.posZ - this.lastIdleCheckPosZ;
        double horizontalDistanceSq = dx * dx + dz * dz;
        if (horizontalDistanceSq > PLAYER_IDLE_HORIZONTAL_DISTANCE_THRESHOLD * PLAYER_IDLE_HORIZONTAL_DISTANCE_THRESHOLD) {
            return false;
        }
        if (Math.abs(dy) > PLAYER_IDLE_VERTICAL_DISTANCE_THRESHOLD) {
            return false;
        }

        double horizontalMotionSq = mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ;
        if (horizontalMotionSq > PLAYER_IDLE_HORIZONTAL_MOTION_THRESHOLD * PLAYER_IDLE_HORIZONTAL_MOTION_THRESHOLD) {
            return false;
        }
        return Math.abs(mc.player.motionY) <= PLAYER_IDLE_VERTICAL_MOTION_THRESHOLD;
    }

    private void updateDamageRecoveryIdleBypassState(Minecraft mc) {
        if (mc == null || mc.player == null) {
            this.damageRecoveryIdleBypassActive = false;
            this.damageRecoverySettledTicks = 0;
            return;
        }

        if (mc.player.hurtTime > 0) {
            this.damageRecoveryIdleBypassActive = true;
            this.damageRecoverySettledTicks = 0;
            return;
        }

        if (!this.damageRecoveryIdleBypassActive) {
            return;
        }

        if (hasPlayerMovementInput(mc)) {
            this.damageRecoveryIdleBypassActive = false;
            this.damageRecoverySettledTicks = 0;
            return;
        }

        if (isDamageMotionSettled(mc)) {
            this.damageRecoverySettledTicks++;
            if (this.damageRecoverySettledTicks >= DAMAGE_IDLE_SETTLE_REQUIRED_TICKS) {
                this.damageRecoveryIdleBypassActive = false;
                this.damageRecoverySettledTicks = 0;
            }
        } else {
            this.damageRecoverySettledTicks = 0;
        }
    }

    private boolean isDamageRecoveryIdleBypassActive(Minecraft mc) {
        return mc != null
                && mc.player != null
                && (mc.player.hurtTime > 0 || this.damageRecoveryIdleBypassActive);
    }

    private boolean isDamageMotionSettled(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return true;
        }
        if (!mc.player.onGround) {
            return false;
        }

        double horizontalMotionSq = mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ;
        if (horizontalMotionSq > PLAYER_IDLE_HORIZONTAL_MOTION_THRESHOLD * PLAYER_IDLE_HORIZONTAL_MOTION_THRESHOLD) {
            return false;
        }
        return Math.abs(mc.player.motionY) <= PLAYER_IDLE_VERTICAL_MOTION_THRESHOLD;
    }

    private boolean hasPlayerMovementInput(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameSettings == null) {
            return false;
        }
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown()
                || mc.gameSettings.keyBindJump.isKeyDown();
    }

    private void captureReference(Minecraft mc) {
        if (mc == null || mc.player == null) {
            reset();
            return;
        }
        this.lastIdleCheckPosX = mc.player.posX;
        this.lastIdleCheckPosY = mc.player.posY;
        this.lastIdleCheckPosZ = mc.player.posZ;
        this.hasIdleCheckPos = true;
    }
}
