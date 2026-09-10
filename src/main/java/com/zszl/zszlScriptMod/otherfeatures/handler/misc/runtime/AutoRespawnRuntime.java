package com.zszl.zszlScriptMod.otherfeatures.handler.misc.runtime;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.network.play.client.CPacketClientStatus;

public final class AutoRespawnRuntime {

    private int autoRespawnCooldownTicks = 0;

    public void onClientDisconnect() {
        this.autoRespawnCooldownTicks = 0;
    }

    public void tick(boolean featureEnabled, int autoRespawnDelayTicks) {
        if (this.autoRespawnCooldownTicks > 0) {
            this.autoRespawnCooldownTicks--;
        }
        if (!featureEnabled || this.autoRespawnCooldownTicks > 0) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || !(mc.currentScreen instanceof GuiGameOver) || mc.player.connection == null) {
            return;
        }
        if (!mc.player.isDead && mc.player.getHealth() > 0.0F) {
            return;
        }

        try {
            mc.player.connection.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
            mc.displayGuiScreen(null);
            this.autoRespawnCooldownTicks = autoRespawnDelayTicks;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.debug("自动复活执行失败", e);
        }
    }

    public int getAutoRespawnCooldownTicks() {
        return this.autoRespawnCooldownTicks;
    }
}
