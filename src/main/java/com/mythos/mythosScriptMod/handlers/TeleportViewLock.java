package com.mythos.mythosScriptMod.handlers;

import com.mojang.authlib.GameProfile;
import com.mythos.mythosScriptMod.mcp.NexusSessions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.UUID;

/**
 * Parks the local camera at the tour's home position for the duration of a
 * teleport tour, so the screen no longer whips out and back once per attack.
 *
 * The server still sees the real body teleport; only the rendered viewpoint
 * stays put. The body is hidden meanwhile, otherwise it would be drawn flying
 * away from a camera that is no longer attached to it.
 */
final class TeleportViewLock {
    private static Entity camera;
    private static Entity previousView;
    private static EntityPlayerSP hiddenBody;
    private static boolean previousInvisible;

    private TeleportViewLock() { }

    static void hold(EntityPlayerSP anchorBody, Vec3d anchor) {
        // Bot threads tick kill aura too, but they own no camera.
        if (NexusSessions.boundSession() != null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || anchorBody == null || anchor == null) return;
        if (camera != null && camera.world != mc.world) release();
        if (camera == null) {
            previousView = mc.getRenderViewEntity();
            camera = new AnchorCamera(mc.world, anchor);
            mc.setRenderViewEntity(camera);
        } else if (mc.getRenderViewEntity() != camera) {
            // Nexus context swaps can transiently restore the body's view.
            mc.setRenderViewEntity(camera);
        }
        if (hiddenBody != anchorBody) {
            restoreBody();
            hiddenBody = anchorBody;
            previousInvisible = anchorBody.isInvisible();
            anchorBody.setInvisible(true);
        }
        camera.prevPosX = camera.lastTickPosX = camera.posX = anchor.x;
        camera.prevPosY = camera.lastTickPosY = camera.posY = anchor.y;
        camera.prevPosZ = camera.lastTickPosZ = camera.posZ = anchor.z;
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;
        camera.rotationYaw = anchorBody.rotationYaw;
        camera.rotationPitch = anchorBody.rotationPitch;
    }

    static void release() {
        if (NexusSessions.boundSession() != null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (camera != null && mc != null && mc.getRenderViewEntity() == camera) {
            // Keep the branches apart: ProGuard can merge Entity and
            // EntityPlayerSP to Object across SRG/MCP classes, and that stack
            // frame then fails Java 8 verification before mod init.
            if (previousView != null && previousView.world == mc.world && !previousView.isDead) {
                mc.setRenderViewEntity(previousView);
            } else {
                mc.setRenderViewEntity(mc.player);
            }
        }
        camera = null;
        previousView = null;
        restoreBody();
    }

    private static void restoreBody() {
        if (hiddenBody != null) {
            hiddenBody.setInvisible(previousInvisible);
            hiddenBody = null;
        }
    }

    private static final class AnchorCamera extends EntityOtherPlayerMP {
        AnchorCamera(World world, Vec3d anchor) {
            super(world, new GameProfile(UUID.randomUUID(), "KillAuraAnchor"));
            noClip = true;
            setInvisible(true);
            setPosition(anchor.x, anchor.y, anchor.z);
        }

        @Override public boolean isSpectator() { return true; }
        @Override public boolean isEntityInsideOpaqueBlock() { return false; }
    }
}
