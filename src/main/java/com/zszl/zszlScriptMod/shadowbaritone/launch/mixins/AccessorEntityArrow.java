package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityArrow.class)
public interface AccessorEntityArrow {
    @Accessor("inGround")
    boolean zszl$isInGround();
}
