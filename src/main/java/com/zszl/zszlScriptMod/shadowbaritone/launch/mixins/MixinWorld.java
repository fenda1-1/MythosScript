package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.List;

@Mixin(World.class)
public class MixinWorld {
    // Only omit entity boxes. The block collision pass and Forge event remain intact.
    @Redirect(method = "getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getEntitiesWithinAABBExcludingEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;"), require = 1)
    private List<Entity> zszl$filterEntityCollisions(World world, Entity entity, AxisAlignedBB box) {
        if (MovementFeatureManager.hasNoCollision(entity)) return Collections.emptyList();
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(entity, box);
        entities.removeIf(MovementFeatureManager::hasNoCollision);
        return entities;
    }
}
