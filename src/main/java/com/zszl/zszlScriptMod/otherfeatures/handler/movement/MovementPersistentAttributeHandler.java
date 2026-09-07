package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import net.minecraft.client.entity.EntityPlayerSP;

final class MovementPersistentAttributeHandler {

    // Five snow layers are 0.625 blocks high, just above Minecraft's normal
    // 0.6-step limit. Raise it slightly only while the embedded navigator owns
    // movement so its flat snow-layer path nodes are physically reachable.
    private static final float PATHING_LOW_SNOW_STEP_HEIGHT = 0.626F;

    private MovementPersistentAttributeHandler() {
    }

    static void apply(EntityPlayerSP player) {
        float stepHeight = MovementFeatureManager.isEnabled("auto_step")
                ? MovementFeatureManager.getConfiguredValue("auto_step", MovementFeatureManager.DEFAULT_STEP_HEIGHT)
                : MovementFeatureManager.DEFAULT_STEP_HEIGHT;
        if (EmbeddedNavigationHandler.INSTANCE.isPathingOrCalculating()) {
            stepHeight = Math.max(stepHeight, PATHING_LOW_SNOW_STEP_HEIGHT);
        }
        player.stepHeight = stepHeight;
        player.entityCollisionReduction = MovementFeatureManager.DEFAULT_COLLISION_REDUCTION;
    }

    static void reset(EntityPlayerSP player) {
        if (player == null) {
            return;
        }
        player.stepHeight = MovementFeatureManager.DEFAULT_STEP_HEIGHT;
        player.entityCollisionReduction = MovementFeatureManager.DEFAULT_COLLISION_REDUCTION;
    }
}
