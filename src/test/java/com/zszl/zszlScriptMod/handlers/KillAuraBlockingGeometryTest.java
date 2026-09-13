package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Rotation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KillAuraBlockingGeometryTest {
    @Test
    public void predictsProjectileApproachingFromBehind() {
        AxisAlignedBB player = new AxisAlignedBB(-0.5D, 0D, -0.5D, 0.5D, 2D, 0.5D);
        double impact = KillAuraBlocking.impactTime(player, Vec3d.ZERO,
                new Vec3d(0D, 1.8D, 8D), new Vec3d(0D, 0D, -1D), Vec3d.ZERO,
                0.99D, 0.05D, 20, (from, to) -> null);
        assertTrue("an arrow from behind must be detected before it reaches the player", impact < 12D);
    }

    @Test
    public void rotationUpgradeRetainsMovementAndGroundState() {
        CPacketPlayer original = new CPacketPlayer.Position(12D, 64D, -3D, true);
        CPacketPlayer upgraded = KillAuraBlocking.withRotation(original, new Rotation(180F, 0F));
        assertTrue(upgraded instanceof CPacketPlayer.PositionRotation);
        assertEquals(12D, upgraded.getX(0D), 0D);
        assertEquals(64D, upgraded.getY(0D), 0D);
        assertEquals(-3D, upgraded.getZ(0D), 0D);
        assertTrue(upgraded.isOnGround());
        assertEquals(180F, upgraded.getYaw(0F), 0F);
    }
}
