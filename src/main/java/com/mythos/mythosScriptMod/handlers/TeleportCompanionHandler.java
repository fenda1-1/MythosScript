package com.mythos.mythosScriptMod.handlers;

import com.mojang.authlib.GameProfile;
import com.mythos.mythosScriptMod.mcp.NexusSessions;
import com.mythos.mythosScriptMod.mythosScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The "soul" for TP kill aura: a real, collidable avatar the operator drives while
 * the actual player entity does the teleporting.
 *
 * <p>Why a separate entity instead of moving the player: the player's position must
 * match the server's, so it has to follow every teleport. Anything the operator sees
 * therefore has to come from another entity. Making that entity collidable (rather
 * than a no-clip ghost camera) is what keeps normal walking feel — gravity, step-up,
 * walls — so the view behaves like ordinary play.
 *
 * <p>The real player stays visible: {@code MixinEntityPlayerSP.isUser()} already
 * returns false for the body while a camera entity owns the view, so the renderer
 * draws it. That gives the "god view of yourself teleporting" the operator asked for.
 */
public final class TeleportCompanionHandler {
    private static EntityPlayerSP body;
    private static Companion avatar;
    private static Entity previousView;
    private static int previousPerspective;

    private TeleportCompanionHandler() {
    }

    public static boolean isActive() {
        if (NexusSessions.boundSession() != null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return avatar != null && body == mc.player && avatar.world == mc.world
                && body.isEntityAlive();
    }

    /** Same contract as the freecam body check: tells the renderer to draw the real player. */
    public static boolean isBody(Entity entity) {
        return entity == body && isActive();
    }

    public static Vec3d position() {
        return avatar == null ? null : avatar.getPositionVector();
    }

    public static void start() {
        if (NexusSessions.boundSession() != null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || !mc.player.isEntityAlive()) {
            return;
        }
        if (avatar != null) {
            return;
        }
        body = mc.player;
        previousView = mc.getRenderViewEntity();
        previousPerspective = mc.gameSettings.thirdPersonView;
        avatar = new Companion(mc.world, mc.player.getGameProfile());
        avatar.setLocationAndAngles(body.posX, body.posY, body.posZ, body.rotationYaw, body.rotationPitch);
        // The avatar is a separate entity, so it would hold nothing at all. Sharing the
        // inventory object (rather than copying stacks every tick) keeps the held item,
        // armour, offhand and the hotbar selection identical to the body by construction.
        avatar.inventory = body.inventory;
        syncPreviousPosition();
        mc.world.addEntityToWorld(nextVirtualEntityId(mc), avatar);
        mc.setRenderViewEntity(avatar);
    }

    public static void stop() {
        if (NexusSessions.boundSession() != null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (avatar != null) {
            mc.world.removeEntity(avatar);
            if (mc.getRenderViewEntity() == avatar) {
                // Separate branches: ProGuard can merge Entity/EntityPlayerSP into
                // Object here, and that merged frame fails Java 8 verification.
                if (previousView != null && previousView.world == mc.world && !previousView.isDead) {
                    mc.setRenderViewEntity(previousView);
                } else {
                    mc.setRenderViewEntity(mc.player);
                }
            }
            // The operator is free to cycle the perspective while in the avatar, so
            // put back whatever the body had rather than a fixed first person.
            mc.gameSettings.thirdPersonView = previousPerspective;
        }
        avatar = null;
        body = null;
        previousView = null;
    }

    /**
     * Keeps the camera on the avatar. Movement itself is driven inside the avatar's own
     * tick ({@link Companion#onLivingUpdate()}) so it runs in the same phase as the rest
     * of the world, exactly like the local player.
     */
    public static void tick() {
        if (NexusSessions.boundSession() != null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (body == null || avatar == null) {
            return;
        }
        if (body != mc.player || avatar.world != mc.world || !body.isEntityAlive()) {
            stop();
            return;
        }
        if (mc.getRenderViewEntity() != avatar) {
            // Nexus mount swaps and other camera features restore the body's view
            // entity transiently; reassert instead of tearing the avatar down.
            mc.setRenderViewEntity(avatar);
        }
    }

    /**
     * Mouse look goes to the avatar. {@code Entity.turn} is used instead of assigning
     * yaw/pitch directly so sensitivity scaling, the pitch clamp and the prev-rotation
     * bookkeeping stay identical to the local player's.
     */
    public static boolean turn(Entity entity, float yaw, float pitch) {
        if (!isBody(entity) || avatar == null) {
            return false;
        }
        avatar.turn(yaw, pitch);
        return true;
    }

    /** Physical keys drive the avatar; Baritone's PlayerMovementInput must keep the real player. */
    public static void clearInput(MovementInput input) {
        if (input instanceof MovementInputFromOptions) {
            input.moveForward = input.moveStrafe = 0.0F;
            input.jump = input.sneak = false;
            input.forwardKeyDown = input.backKeyDown = input.leftKeyDown = input.rightKeyDown = false;
        }
    }

    private static void syncPreviousPosition() {
        if (avatar == null) {
            return;
        }
        avatar.prevPosX = avatar.lastTickPosX = avatar.posX;
        avatar.prevPosY = avatar.lastTickPosY = avatar.posY;
        avatar.prevPosZ = avatar.lastTickPosZ = avatar.posZ;
        avatar.prevRotationYaw = avatar.rotationYaw;
        avatar.prevRotationPitch = avatar.rotationPitch;
    }

    private static int nextVirtualEntityId(Minecraft mc) {
        return -Math.abs(mc.player.getEntityId() * 31 + 7) - 1;
    }

    private static final class Companion extends EntityOtherPlayerMP {
        private final MovementInputFromOptions input;
        private int jumpCooldownTicks;

        Companion(World world, GameProfile profile) {
            super(world, profile);
            this.noClip = false;
            this.stepHeight = 0.6F;
            // 1.12.2 declares the movement input on EntityPlayerSP, not on
            // EntityLivingBase, so this hierarchy has none of its own.
            this.input = new MovementInputFromOptions(Minecraft.getMinecraft().gameSettings);
            setInvisible(false);
        }

        @Override
        public void onLivingUpdate() {
            // EntityOtherPlayerMP overrides onLivingUpdate without calling super, so the
            // vanilla movement model in EntityLivingBase.onLivingUpdate - travel(),
            // which owns ground acceleration, friction, slipperiness, water, ladders and
            // gravity - never runs for a remote-player entity. Feed it the same input the
            // local player feeds itself and call travel() below; without this the avatar
            // slides at a constant velocity with no inertia.
            if (inputAccepted()) {
                input.updatePlayerMoveState();
            } else {
                input.moveForward = input.moveStrafe = 0.0F;
                input.jump = input.sneak = false;
            }
            moveStrafing = input.moveStrafe;
            moveForward = input.moveForward;
            isJumping = input.jump;
            setSneaking(input.sneak);
            // 0.8F is the vanilla threshold: walk speed is 1.0 and sneak is 0.3, so
            // sneak-walking must not sprint.
            setSprinting(moveForward >= 0.8F && !isSneaking()
                    && Minecraft.getMinecraft().gameSettings.keyBindSprint.isKeyDown());

            if (jumpCooldownTicks > 0) {
                --jumpCooldownTicks;
            }
            if (isJumping) {
                if (isInWater()) {
                    handleJumpWater();
                } else if (isInLava()) {
                    handleJumpLava();
                } else if (onGround && jumpCooldownTicks == 0) {
                    jump();
                    jumpCooldownTicks = 10;
                }
            }

            travel(moveStrafing, moveVertical, moveForward);
            super.onLivingUpdate();

            // A remote-player entity takes its head rotation from the server's head-look
            // packet, which a purely local avatar never receives: the head would keep
            // facing the heading it spawned with while the body turned. Drive both from
            // the camera yaw so the model looks where the operator looks.
            renderYawOffset = rotationYaw;
            rotationYawHead = rotationYaw;
        }

        @Override
        public boolean isServerWorld() {
            // travel() only simulates movement for entities that claim authority over
            // their own position. The avatar exists only on this side, so it must claim
            // it - this is exactly what EntityPlayerSP does for the local player.
            return true;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isEntityInsideOpaqueBlock() {
            return false;
        }

        private static boolean inputAccepted() {
            Minecraft mc = Minecraft.getMinecraft();
            // Screens unpress all keys anyway; the mod's own overlay does not, so it has
            // to be excluded explicitly or the avatar keeps walking under it.
            return mc.currentScreen == null && mc.inGameHasFocus
                    && !mythosScriptMod.isGuiVisible && !mc.isGamePaused();
        }
    }
}
