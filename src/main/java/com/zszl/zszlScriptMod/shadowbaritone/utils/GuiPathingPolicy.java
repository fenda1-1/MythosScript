package com.zszl.zszlScriptMod.shadowbaritone.utils;

import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
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
