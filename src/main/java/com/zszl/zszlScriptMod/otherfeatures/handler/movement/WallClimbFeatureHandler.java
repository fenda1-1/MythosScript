package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.zszl.zszlScriptMod.handlers.FlyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.MoverType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Local surface locomotion. Never changes gravity flags, flight abilities or block collision. */
public final class WallClimbFeatureHandler {
    private static final double CONTACT = 0.10D;
    private static EntityPlayerSP owner;
    // Direction INTO the supporting block, i.e. the opposite of the surface normal.
    private static EnumFacing support;
    private static Vec3d heading;
    private static float lastYaw;
    private static int detachTicks;
    private static RayTraceResult webTarget;
    private static int webTicks;

    private WallClimbFeatureHandler() { }

    public static void reset() {
        owner = null;
        support = null;
        heading = null;
        detachTicks = 0;
        cancelWeb();
    }

    public static void cancelWeb() {
        webTarget = null;
        webTicks = 0;
    }

    public static void shootWeb(EntityPlayerSP player) {
        if (!canTravel(player) || FreecamFeatureHandler.isActive()) return;
        if (webTarget != null) {
            cancelWeb();
            return;
        }
        Vec3d eyes = player.getPositionEyes(1.0F);
        RayTraceResult hit = player.world.rayTraceBlocks(eyes,
                eyes.add(player.getLookVec().scale(64.0D)), false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK
                || hit.sideHit == null || hit.sideHit == EnumFacing.UP) return;
        reset();
        owner = player;
        webTarget = hit;
    }

    private static boolean canTravel(EntityPlayerSP player) {
        return player != null && MovementFeatureManager.isEnabled("wall_climb") && player.world != null
                && player.isEntityAlive() && !player.isRiding() && !player.isSpectator()
                && !player.isElytraFlying() && !player.capabilities.isFlying && !FlyHandler.enabled
                && !player.noClip && !player.isInWater() && !player.isInLava() && player.movementInput != null;
    }

    public static boolean isAttached(EntityPlayerSP player) {
        return owner == player && (webTarget != null || (support != null && support != EnumFacing.DOWN))
                && MovementFeatureManager.isEnabled("wall_climb");
    }

    public static boolean travel(EntityPlayerSP player) {
        if (player != Minecraft.getMinecraft().player) {
            return false;
        }
        if (!canTravel(player)) {
            reset();
            return false;
        }
        if (owner != player) {
            reset();
            owner = player;
        }
        if (player.movementInput.sneak || (player.movementInput.jump && (support != null || webTarget != null))) {
            cancelWeb();
            if (support != null) {
                Vec3d impulse = vector(support).scale(-0.32D);
                player.motionX = impulse.x;
                player.motionY = impulse.y + (player.movementInput.jump ? 0.20D : 0.0D);
                player.motionZ = impulse.z;
            }
            support = null;
            detachTicks = 8;
            return false;
        }
        if (detachTicks > 0) {
            detachTicks--;
            return false;
        }
        if (!MovementFeatureManager.isWallClimbWebEnabled() || Minecraft.getMinecraft().currentScreen != null) {
            cancelWeb();
        }
        if (webTarget != null && pullWeb(player)) return true;

        EnumFacing previous = support == null ? EnumFacing.DOWN : support;
        if (support == null) {
            heading = SurfaceWalkMath.cameraHeading(player.rotationYaw);
        } else {
            heading = SurfaceWalkMath.rotate(heading, vector(previous).scale(-1.0D),
                    -Math.toRadians(MathHelper.wrapDegrees(player.rotationYaw - lastYaw)));
        }
        lastYaw = player.rotationYaw;
        Vec3d normal = vector(previous).scale(-1.0D);
        Vec3d wish = SurfaceWalkMath.input(heading, normal,
                player.movementInput.moveForward, player.movementInput.moveStrafe);
        if (previous == EnumFacing.UP) {
            wish = SurfaceWalkMath.ceilingInput(player.rotationYaw,
                    player.movementInput.moveForward, player.movementInput.moveStrafe);
        }

        EnumFacing next = touches(player, previous) ? previous : null;
        // Prefer a face in the direction of travel, permitting floor -> wall -> ceiling turns.
        double best = 0.15D;
        for (EnumFacing face : EnumFacing.values()) {
            if (face == previous || face == previous.getOpposite()) {
                continue;
            }
            double into = wish.dotProduct(vector(face));
            if (into > best && touches(player, face)) {
                best = into;
                next = face;
            }
        }
        // Catch a ceiling after jumping.
        if (next == null && previous == EnumFacing.DOWN && touches(player, EnumFacing.UP)) {
            next = EnumFacing.UP;
        }
        // Once the body clears a ceiling edge, keep climbing toward a ceiling up to
        // one block higher. Real collision resolution stops us at the intervening rim.
        if (next == null && previous == EnumFacing.UP && hasRaisedCeiling(player)) {
            next = EnumFacing.UP;
        }
        if (next == null && support != null && previous.getAxis().isHorizontal()
                && wish.y > 0.15D && stepOntoLedge(player, previous)) {
            support = null;
            return true;
        }
        if (next == null || next == EnumFacing.DOWN) {
            support = null;
            return false;
        }
        if (next != previous) {
            heading = SurfaceWalkMath.transport(heading, vector(previous).scale(-1.0D),
                    vector(next).scale(-1.0D));
        }
        support = next;
        if (next == EnumFacing.UP) heading = SurfaceWalkMath.cameraHeading(player.rotationYaw);
        normal = vector(next).scale(-1.0D);
        wish = SurfaceWalkMath.input(heading, normal,
                player.movementInput.moveForward, player.movementInput.moveStrafe);
        if (next == EnumFacing.UP) {
            wish = SurfaceWalkMath.ceilingInput(player.rotationYaw,
                    player.movementInput.moveForward, player.movementInput.moveStrafe);
        }
        double speed = MovementFeatureManager.getConfiguredValue("wall_climb", 0.20F);
        boolean rising = next == EnumFacing.UP && !touches(player, next);
        double adhesion = rising ? speed : CONTACT;
        Vec3d motion = wish.scale(rising ? 0.0D : speed).add(vector(next).scale(adhesion));
        // Vanilla move resolves real collision boxes (including partial blocks). Cancelling travel
        // prevents gravity and air acceleration from pulling the player off the supporting face.
        float stepHeight = player.stepHeight;
        player.stepHeight = 0.0F;
        player.fallDistance = 0.0F;
        try {
            player.move(MoverType.SELF, motion.x, motion.y, motion.z);
        } finally {
            player.stepHeight = stepHeight;
        }
        player.motionX = player.motionY = player.motionZ = 0.0D;
        player.fallDistance = 0.0F;
        // Entity.move assumes world-down gravity. Surface support is also a grounded state.
        player.onGround = touches(player, next);
        player.prevLimbSwingAmount = player.limbSwingAmount;
        double dx = player.posX - player.prevPosX;
        double dy = player.posY - player.prevPosY;
        double dz = player.posZ - player.prevPosZ;
        float stride = (float) Math.min(1.0D, Math.sqrt(dx * dx + dy * dy + dz * dz) * 4.0D);
        player.limbSwingAmount += (stride - player.limbSwingAmount) * 0.4F;
        player.limbSwing += player.limbSwingAmount;
        return true;
    }

    private static boolean touches(EntityPlayerSP player, EnumFacing face) {
        Vec3d offset = vector(face).scale(CONTACT);
        AxisAlignedBB probe = player.getEntityBoundingBox().grow(-0.001D)
                .offset(offset.x, offset.y, offset.z);
        // A null entity excludes entity collision boxes; mobs must never become walkable walls.
        return !player.world.getCollisionBoxes(null, probe).isEmpty();
    }

    private static boolean hasRaisedCeiling(EntityPlayerSP player) {
        AxisAlignedBB probe = SurfaceWalkMath.raisedCeilingProbe(player.getEntityBoundingBox(), CONTACT);
        return !player.world.getCollisionBoxes(null, probe).isEmpty();
    }

    private static boolean pullWeb(EntityPlayerSP player) {
        RayTraceResult target = webTarget;
        Vec3d eyes = player.getPositionEyes(1.0F);
        RayTraceResult visible = player.world.rayTraceBlocks(eyes,
                target.hitVec.add(vector(target.sideHit).scale(-0.02D)), false, true, false);
        if (++webTicks > 1400 || visible == null || !target.getBlockPos().equals(visible.getBlockPos())) {
            cancelWeb();
            return false;
        }
        EnumFacing face = target.sideHit.getOpposite();
        Vec3d destination = target.hitVec.add(vector(target.sideHit).scale(player.width * 0.5D + 0.02D));
        destination = new Vec3d(destination.x,
                face == EnumFacing.UP ? target.hitVec.y - player.height - 0.02D
                        : target.hitVec.y - player.getEyeHeight(), destination.z);
        Vec3d delta = destination.subtract(player.getPositionVector());
        double distance = delta.lengthVector();
        Vec3d motion = delta.normalize().scale(Math.min(distance, MovementFeatureManager.getWallClimbWebSpeed()));
        Vec3d before = player.getPositionVector();
        float step = player.stepHeight;
        player.stepHeight = 0;
        player.fallDistance = 0;
        try {
            player.move(MoverType.SELF, motion.x, motion.y, motion.z);
        } finally {
            player.stepHeight = step;
        }
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        Vec3d silk = target.hitVec.subtract(eyes);
        int particles = Math.min(32, Math.max(2, (int) (silk.lengthVector() * 2)));
        for (int i = 0; i <= particles; i++) {
            Vec3d point = eyes.add(silk.scale((double) i / particles));
            player.world.spawnParticle(EnumParticleTypes.END_ROD, point.x, point.y, point.z, 0, 0, 0);
        }
        if (player.getEntityBoundingBox().grow(CONTACT).contains(target.hitVec) && touches(player, face)) {
            support = face;
            heading = SurfaceWalkMath.transport(SurfaceWalkMath.cameraHeading(player.rotationYaw),
                    new Vec3d(0, 1, 0), vector(face).scale(-1));
            lastYaw = player.rotationYaw;
            player.onGround = true;
            cancelWeb();
        } else if (distance < 0.03D || player.getPositionVector().squareDistanceTo(before) < 0.000001D) {
            cancelWeb();
        }
        return true;
    }

    private static boolean stepOntoLedge(EntityPlayerSP player, EnumFacing wall) {
        Vec3d across = vector(wall).scale(player.width * 0.5D + CONTACT);
        AxisAlignedBB box = player.getEntityBoundingBox();
        // Sweep the whole short crossing; never teleport through a lip or adjacent block.
        if (!player.world.getCollisionBoxes(null,
                box.expand(across.x, 0.0D, across.z).grow(-0.001D)).isEmpty()
                || player.world.getCollisionBoxes(null,
                box.offset(across.x, -CONTACT, across.z).grow(-0.001D)).isEmpty()) {
            return false;
        }
        player.fallDistance = 0.0F;
        player.move(MoverType.SELF, across.x, -CONTACT, across.z);
        player.motionX = player.motionY = player.motionZ = 0.0D;
        player.fallDistance = 0.0F;
        return true;
    }

    private static Vec3d vector(EnumFacing face) {
        return new Vec3d(face.getDirectionVec());
    }
}
