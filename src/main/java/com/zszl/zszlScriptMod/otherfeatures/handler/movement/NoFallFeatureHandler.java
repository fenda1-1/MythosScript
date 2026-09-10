package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;

public final class NoFallFeatureHandler {

    private static final float MIN_PROTECT_FALL_DISTANCE = 2.0F;

    private NoFallFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (player == null
                || !MovementFeatureManager.isEnabled("no_fall")
                || player.connection == null
                || player.capabilities.isFlying
                || player.isElytraFlying()
                || player.isInWater()
                || player.isInLava()
                || player.fallDistance <= MIN_PROTECT_FALL_DISTANCE) {
            return;
        }
        player.connection.sendPacket(new CPacketPlayer(true));
        player.fallDistance = 0.0F;
    }
}
