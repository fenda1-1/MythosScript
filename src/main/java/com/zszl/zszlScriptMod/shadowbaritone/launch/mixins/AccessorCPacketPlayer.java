package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.network.play.client.CPacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CPacketPlayer.class)
public interface AccessorCPacketPlayer {
    @Accessor("onGround")
    void zszl$setOnGround(boolean onGround);
}
