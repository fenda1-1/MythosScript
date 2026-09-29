package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.google.common.util.concurrent.ListenableFuture;
import com.mythos.mythosScriptMod.system.DragonDisconnectTasks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "eos.moe.dragoncore.xf", remap = false)
public abstract class MixinDragonDisconnect {
    @Redirect(method = "ALLATORIxDEMO(Lnet/minecraftforge/fml/common/network/FMLNetworkEvent$ClientDisconnectionFromServerEvent;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_152344_a(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"),
        require = 1, remap = false)
    private static ListenableFuture<?> mythos$deferDisconnectCleanup(Minecraft mc, Runnable action) {
        return DragonDisconnectTasks.submit(action);
    }
}
