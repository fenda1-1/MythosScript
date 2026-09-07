package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.mojang.authlib.GameProfile;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.UUID;

/** A render-only entity: never replaces mc.player and never sends camera coordinates. */
public final class FreecamFeatureHandler {
    private static EntityPlayerSP body;
    private static GhostCamera camera;
    private static Entity previousView;
    private static int previousPerspective;

    private FreecamFeatureHandler() { }

    public static boolean isActive() {
        Minecraft mc = Minecraft.getMinecraft();
        return camera != null && body == mc.player && camera.world == mc.world
                && body.isEntityAlive() && MovementFeatureManager.isEnabled("freecam");
    }

    public static boolean isBody(Entity entity) {
        return entity == body && isActive();
    }

    public static void stop() {
        Minecraft mc = Minecraft.getMinecraft();
        if (camera != null) {
            if (mc.getRenderViewEntity() == camera) {
                // Keep calls in separate branches: ProGuard sees SRG program classes
                // alongside MCP libraries and can merge Entity/EntityPlayerSP to Object.
                // That merged stack frame fails Java 8 verification before mod init.
                if (previousView != null && previousView.world == mc.world && !previousView.isDead) {
                    mc.setRenderViewEntity(previousView);
                } else {
                    mc.setRenderViewEntity(mc.player);
                }
            }
            mc.gameSettings.thirdPersonView = previousPerspective;
            if (body != null) {
                clearInput(body.movementInput);
                body.motionX = body.motionY = body.motionZ = 0.0D;
            }
        }
        camera = null;
        body = null;
        previousView = null;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!MovementFeatureManager.isEnabled("freecam")) {
            stop();
            return;
        }
        if (camera != null && (body != mc.player || camera.world != mc.world
                || body == null || !body.isEntityAlive() || body.isRiding()
                || mc.getRenderViewEntity() != camera)) {
            stop();
            MovementFeatureManager.setEnabled("freecam", false);
            return;
        }
        if (mc.player == null || mc.world == null || !mc.player.isEntityAlive() || mc.player.isRiding()) {
            return;
        }
        if (camera == null) {
            body = mc.player;
            previousView = mc.getRenderViewEntity();
            previousPerspective = mc.gameSettings.thirdPersonView;
            camera = new GhostCamera(mc.world);
            camera.setLocationAndAngles(body.posX, body.posY, body.posZ, body.rotationYaw, body.rotationPitch);
            syncPreviousPosition();
            body.motionX = body.motionY = body.motionZ = 0.0D;
            body.setSprinting(false);
            mc.playerController.resetBlockRemoving();
            mc.setRenderViewEntity(camera);
            WallClimbFeatureHandler.reset();
        }
        mc.gameSettings.thirdPersonView = 0;
        syncPreviousPosition();
        clearInput(body.movementInput);
        if (mc.currentScreen != null || !mc.inGameHasFocus || zszlScriptMod.isGuiVisible || mc.isGamePaused()) {
            return;
        }
        float forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1 : 0)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1 : 0);
        float strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1 : 0)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1 : 0);
        float vertical = (mc.gameSettings.keyBindJump.isKeyDown() ? 1 : 0)
                - (mc.gameSettings.keyBindSneak.isKeyDown() ? 1 : 0);
        double speed = MovementFeatureManager.getConfiguredValue("freecam", 0.6F)
                * (mc.gameSettings.keyBindSprint.isKeyDown() ? 3.0D : 1.0D);
        Vec3d motion = FreecamMovementMath.motion(camera.rotationYaw, camera.rotationPitch,
                forward, strafe, vertical, speed);
        camera.setPosition(camera.posX + motion.x, camera.posY + motion.y, camera.posZ + motion.z);
    }

    public static boolean turn(Entity entity, float yaw, float pitch) {
        if (!isBody(entity)) {
            return false;
        }
        camera.turn(yaw, pitch);
        return true;
    }

    public static void clearInput(MovementInput input) {
        if (input != null) {
            input.moveForward = input.moveStrafe = 0;
            input.jump = input.sneak = false;
            input.forwardKeyDown = input.backKeyDown = input.leftKeyDown = input.rightKeyDown = false;
        }
    }

    private static void syncPreviousPosition() {
        camera.prevPosX = camera.lastTickPosX = camera.posX;
        camera.prevPosY = camera.lastTickPosY = camera.posY;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ;
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
    }

    private static final class GhostCamera extends EntityOtherPlayerMP {
        GhostCamera(World world) {
            super(world, new GameProfile(UUID.randomUUID(), "Freecam"));
            noClip = true;
            setInvisible(true);
        }

        @Override public boolean isSpectator() { return true; }
        @Override public boolean isEntityInsideOpaqueBlock() { return false; }
    }
}
