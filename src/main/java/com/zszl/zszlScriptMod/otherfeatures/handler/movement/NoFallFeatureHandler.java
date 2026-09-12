package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.entity.EntityPlayerSP;

public final class NoFallFeatureHandler {

    private NoFallFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        if (player == null
                || !MovementFeatureManager.isEnabled("no_fall")) {
            return;
        }
        player.fallDistance = 0.0F;
    }
}
