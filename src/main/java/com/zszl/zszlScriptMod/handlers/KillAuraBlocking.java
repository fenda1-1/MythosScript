package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import java.util.function.BiFunction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Shield geometry and packet encoding, independent of client runtime state. */
public final class KillAuraBlocking {
    private KillAuraBlocking() {}

    public static CPacketPlayer withRotation(CPacketPlayer packet, Rotation rotation) {
        // Changing yaw fields alone cannot add rotation bytes to a position/ground packet.
        if (packet instanceof CPacketPlayer.Position || packet instanceof CPacketPlayer.PositionRotation) {
            return new CPacketPlayer.PositionRotation(packet.getX(0), packet.getY(0), packet.getZ(0),
                    rotation.getYaw(), rotation.getPitch(), packet.isOnGround());
        }
        return new CPacketPlayer.Rotation(rotation.getYaw(), rotation.getPitch(), packet.isOnGround());
    }

    static Rotation faceSource(Vec3d player, Vec3d source) {
        // Vanilla checks the horizontal damage direction. Looking straight up/down weakens that test.
        return new Rotation((float) Math.toDegrees(Math.atan2(source.z - player.z, source.x - player.x)) - 90F, 0F);
    }

    static double impactTime(AxisAlignedBB body, Vec3d playerMotion, Vec3d position, Vec3d velocity,
            Vec3d acceleration, double drag, double gravity, int maxTicks,
            BiFunction<Vec3d, Vec3d, RayTraceResult> blockTrace) {
        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3d next = position.add(velocity);
            RayTraceResult wall = blockTrace.apply(position, next);
            Vec3d end = wall == null ? next : wall.hitVec;
            // Test relative motion so walking into an arrow is detected too.
            Vec3d relativeStart = position.subtract(playerMotion.scale(tick));
            double segmentFraction = wall == null || velocity.lengthSquared() < 1.0E-10D
                    ? 1D : position.distanceTo(end) / velocity.lengthVector();
            Vec3d relativeEnd = end.subtract(playerMotion.scale(tick + segmentFraction));
            if (body.contains(relativeStart)) return tick;
            RayTraceResult hit = body.calculateIntercept(relativeStart, relativeEnd);
            if (hit != null) {
                double length = relativeStart.distanceTo(relativeEnd);
                return tick + (length < 1.0E-10D ? 0D
                        : segmentFraction * relativeStart.distanceTo(hit.hitVec) / length);
            }
            if (wall != null) return Double.POSITIVE_INFINITY;
            position = next;
            velocity = velocity.add(acceleration).scale(drag).subtract(0D, gravity, 0D);
        }
        return Double.POSITIVE_INFINITY;
    }
}
