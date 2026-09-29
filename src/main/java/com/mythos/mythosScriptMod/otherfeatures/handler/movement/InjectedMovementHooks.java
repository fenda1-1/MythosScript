package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import java.util.List;

public final class InjectedMovementHooks {
    /** Callback signature used by the current late-attach chat transformer. */
    public static boolean chat(String message) {
        return com.mythos.mythosScriptMod.handlers.ChatEventHandler.consumeBaritoneChat(message);
    }

    public static boolean travel(Object entity) {
        return entity instanceof EntityPlayerSP && WallClimbFeatureHandler.travel((EntityPlayerSP) entity);
    }
    public static boolean turn(Object entity, double yaw, double pitch) {
        return FreecamFeatureHandler.turn((Entity) entity, (float) yaw, (float) pitch);
    }
    public static boolean push(Object entity, Object other) {
        return MovementFeatureManager.hasNoCollision((Entity) entity) || MovementFeatureManager.hasNoCollision((Entity) other);
    }
    public static boolean safeWalk(Object entity) { return MovementFeatureManager.shouldApplyVanillaSafeWalk((Entity) entity); }
    public static boolean active() { return FreecamFeatureHandler.isActive(); }
    public static boolean body(Object entity) { return FreecamFeatureHandler.isBody((Entity) entity); }
    public static boolean pick() {
        if (active()) return true;
        if (!MovementFeatureManager.isEnabled("wall_climb") || !MovementFeatureManager.isWallClimbWebEnabled()) return false;
        WallClimbFeatureHandler.shootWeb(Minecraft.getMinecraft().player);
        return true;
    }
    public static List<?> collisions(List<?> original, Object entity, Object box) { return original; }
}
