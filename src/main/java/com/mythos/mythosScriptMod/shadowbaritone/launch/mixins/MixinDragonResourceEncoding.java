package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.system.DragonResourceEncoding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="eos.moe.dragoncore.dh", remap=false)
public abstract class MixinDragonResourceEncoding {
    @Redirect(method={"u(Ljava/lang/String;)V", "j(Ljava/lang/String;)V"},
        at=@At(value="INVOKE", target="Ljava/lang/String;getBytes()[B"), require=0, remap=false)
    private static byte[] mythos$resourcePasswordBytes(String password) {
        return DragonResourceEncoding.encode(password);
    }
}
