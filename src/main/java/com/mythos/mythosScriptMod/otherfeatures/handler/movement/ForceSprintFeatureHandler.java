package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import com.mythos.mythosScriptMod.mixin.SessionFieldAccess;
import net.minecraft.client.entity.EntityPlayerSP;

final class ForceSprintFeatureHandler {

    private ForceSprintFeatureHandler() {
    }

    static void apply(EntityPlayerSP player) {
        // setSprinting edits the attribute map (a plain HashMap); it must only
        // ever run on the thread that owns this player.
        if (!SessionFieldAccess.ownsPlayer(player)) {
            return;
        }
        if (MovementFeatureManager.isEnabled("force_sprint")
                && MovementFeatureSupport.isMoving(player)
                && !player.isSneaking()
                && !player.collidedHorizontally) {
            player.setSprinting(true);
        }
    }
}
