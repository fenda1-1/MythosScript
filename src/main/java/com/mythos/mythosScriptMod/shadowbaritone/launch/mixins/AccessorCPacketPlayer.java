package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import net.minecraft.network.play.client.CPacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CPacketPlayer.class)
public interface AccessorCPacketPlayer {
    @Accessor("onGround")
    void mythos$setOnGround(boolean onGround);
    @Accessor("yaw")
    void mythos$setYaw(float yaw);
    @Accessor("pitch")
    void mythos$setPitch(float pitch);
}
