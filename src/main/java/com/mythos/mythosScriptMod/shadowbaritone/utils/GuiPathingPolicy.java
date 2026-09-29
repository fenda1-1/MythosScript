package com.mythos.mythosScriptMod.shadowbaritone.utils;

import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.path.PathSequenceEventListener;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import net.minecraft.client.Minecraft;

public final class GuiPathingPolicy {

    private GuiPathingPolicy() {
    }

    public static boolean shouldKeepPathingDuringGui(Minecraft mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return false;
        }
        IBaritone primary = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (primary == null) {
            return KillAuraHandler.INSTANCE.shouldKeepRunningDuringGui(mc)
                    || PathSequenceEventListener.isAnyHuntOrbitActionRunning();
        }
        return primary.getPathingBehavior().isPathing()
                || KillAuraHandler.INSTANCE.shouldKeepRunningDuringGui(mc)
                || PathSequenceEventListener.isAnyHuntOrbitActionRunning();
    }
}
