package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HuntOrbitController {
    private static final double STUCK_MOVEMENT_EPSILON_SQ = 0.0036D;
    private static final int STUCK_TICKS_THRESHOLD = 6;
    private static final int RECOVERY_TICKS = 8;
    private static final int PREVIEW_SEGMENTS = 32;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Integer, Boolean> heldKeyStates = new LinkedHashMap<>();
    private int activeTargetEntityId = Integer.MIN_VALUE;
    private boolean counterClockwise = true;
    private boolean active = false;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    private int stuckTicks = 0;
    private int recoveryTicksRemaining = 0;

    public static final class OrbitConfig {
        private final double desiredDistance;
        private final double distanceTolerance;
        private final boolean holdJump;
        private final boolean holdSprint;
        private final boolean counterClockwise;

        public OrbitConfig(double desiredDistance, double distanceTolerance, boolean holdJump,
                boolean holdSprint, boolean counterClockwise) {
            this.desiredDistance = desiredDistance;
            this.distanceTolerance = distanceTolerance;
            this.holdJump = holdJump;
            this.holdSprint = holdSprint;
            this.counterClockwise = counterClockwise;
        }
    }

    public void tick(EntityPlayerSP player, EntityLivingBase target, OrbitConfig config) {
        if (player == null || target == null || config == null) {
            stop();
            return;
        }

        int targetEntityId = target.getEntityId();
        if (!this.active || targetEntityId != this.activeTargetEntityId) {
            releaseHeldKeys();
            resetMovementTracking();
            this.activeTargetEntityId = targetEntityId;
            this.counterClockwise = config.counterClockwise;
        }

        this.active = true;
        updateStuckState(player);
        applyMovement(player, target, config);
    }

    public boolean isActive() {
        return this.active;
    }

    public void stop() {
        releaseHeldKeys();
        this.active = false;
        this.activeTargetEntityId = Integer.MIN_VALUE;
        resetMovementTracking();
    }

    public static List<Vec3d> buildPreviewLoop(EntityLivingBase target, double radius) {
        return buildPreviewLoop(target, radius, PREVIEW_SEGMENTS);
    }

    public static List<Vec3d> buildPreviewLoop(EntityLivingBase target, double radius, int samplePoints) {
        List<Vec3d> points = new ArrayList<>();
        if (target == null || radius <= 0.0D) {
            return points;
        }

        int segments = Math.max(3, samplePoints);
        double centerY = target.posY + 0.08D;
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            double x = target.posX + Math.cos(angle) * radius;
            double z = target.posZ + Math.sin(angle) * radius;
            points.add(new Vec3d(x, centerY, z));
        }
        return points;
    }

    private void applyMovement(EntityPlayerSP player, EntityLivingBase target, OrbitConfig config) {
        if (mc.gameSettings == null) {
            stop();
            return;
        }

        double desiredDistance = Math.max(0.5D, config.desiredDistance);
        double tolerance = Math.max(0.15D, config.distanceTolerance);
        double distance = player.getDistance(target);
        boolean moveForward = false;
        boolean moveBackward = false;

        double farBand = desiredDistance + tolerance;
        double closeBand = Math.max(0.45D, desiredDistance - tolerance);
        if (distance > farBand) {
            moveForward = true;
        } else if (distance < closeBand) {
            moveBackward = true;
        }

        if (distance > desiredDistance + Math.max(1.10D, tolerance * 2.0D)) {
            moveForward = true;
            moveBackward = false;
        }
        if (distance < Math.max(0.45D, desiredDistance - Math.max(0.75D, tolerance * 2.2D))) {
            moveBackward = true;
            moveForward = false;
        }

        if (this.recoveryTicksRemaining > 0) {
            this.recoveryTicksRemaining--;
            if (!moveBackward) {
                moveForward = true;
            }
        }

        boolean shouldHoldSprint = config.holdSprint && !moveBackward && canPrimeSprint(player);

        setKeyState(mc.gameSettings.keyBindLeft, this.counterClockwise);
        setKeyState(mc.gameSettings.keyBindRight, !this.counterClockwise);
        setKeyState(mc.gameSettings.keyBindForward, moveForward);
        setKeyState(mc.gameSettings.keyBindBack, moveBackward);
        setKeyState(mc.gameSettings.keyBindJump, config.holdJump);
        setKeyState(mc.gameSettings.keyBindSprint, shouldHoldSprint);
        if (shouldHoldSprint) {
            player.setSprinting(true);
        }
    }

    private void updateStuckState(EntityPlayerSP player) {
        if (player == null) {
            resetMovementTracking();
            return;
        }

        if (Double.isNaN(this.lastPlayerX) || Double.isNaN(this.lastPlayerZ)) {
            this.lastPlayerX = player.posX;
            this.lastPlayerZ = player.posZ;
            this.stuckTicks = 0;
            return;
        }

        double dx = player.posX - this.lastPlayerX;
        double dz = player.posZ - this.lastPlayerZ;
        if (dx * dx + dz * dz <= STUCK_MOVEMENT_EPSILON_SQ) {
            this.stuckTicks++;
            if (this.stuckTicks >= STUCK_TICKS_THRESHOLD) {
                this.recoveryTicksRemaining = Math.max(this.recoveryTicksRemaining, RECOVERY_TICKS);
                this.stuckTicks = 0;
            }
        } else {
            this.stuckTicks = 0;
        }

        this.lastPlayerX = player.posX;
        this.lastPlayerZ = player.posZ;
    }

    private void resetMovementTracking() {
        this.lastPlayerX = Double.NaN;
        this.lastPlayerZ = Double.NaN;
        this.stuckTicks = 0;
        this.recoveryTicksRemaining = 0;
    }

    private boolean canPrimeSprint(EntityPlayerSP player) {
        return player != null
                && player.getFoodStats().getFoodLevel() > 6
                && !player.isSneaking()
                && !player.collidedHorizontally
                && !player.isHandActive();
    }

    private void setKeyState(KeyBinding keyBinding, boolean pressed) {
        int keyCode = keyBinding == null ? Keyboard.KEY_NONE : keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) {
            return;
        }

        boolean alreadyPressed = this.heldKeyStates.containsKey(keyCode);
        if (alreadyPressed == pressed) {
            return;
        }

        SimulatedKeyInputManager.simulateKeyCode(keyCode, pressed ? "Down" : "Up");
        if (pressed) {
            this.heldKeyStates.put(keyCode, Boolean.TRUE);
        } else {
            this.heldKeyStates.remove(keyCode);
        }
    }

    private void releaseHeldKeys() {
        if (this.heldKeyStates.isEmpty()) {
            return;
        }

        for (Integer keyCode : new ArrayList<>(this.heldKeyStates.keySet())) {
            SimulatedKeyInputManager.simulateKeyCode(keyCode, "Up");
        }
        this.heldKeyStates.clear();
    }
}
