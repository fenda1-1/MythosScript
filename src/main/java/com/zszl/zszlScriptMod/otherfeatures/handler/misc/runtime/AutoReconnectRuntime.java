package com.zszl.zszlScriptMod.otherfeatures.handler.misc.runtime;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

public final class AutoReconnectRuntime {

    private ServerData pendingReconnectServer = null;
    private ServerData lastConnectedServer = null;
    private int reconnectDelayTicks = 0;
    private int reconnectKeepAliveTicks = 0;
    private int reconnectAttemptCount = 0;

    public void onClientDisconnected(Minecraft mc, boolean featureEnabled, int reconnectDelayTicks) {
        rememberCurrentServer(mc);
        if (!featureEnabled || mc == null || mc.isSingleplayer()) {
            clearState();
            return;
        }

        ServerData reconnectTarget = copyServerData(mc.getCurrentServerData());
        if (reconnectTarget == null) {
            reconnectTarget = copyServerData(this.lastConnectedServer);
        }
        if (reconnectTarget == null) {
            clearState();
            return;
        }

        this.pendingReconnectServer = reconnectTarget;
        this.reconnectDelayTicks = Math.max(0, reconnectDelayTicks);
        this.reconnectKeepAliveTicks = 200;
        this.reconnectAttemptCount = 0;
    }

    public void onClientConnected() {
        clearState();
    }

    public void onClientDisconnectReset(boolean featureEnabled) {
        if (!featureEnabled) {
            clearState();
        }
    }

    public void tick(boolean featureEnabled, boolean infiniteAttempts, int maxAttempts, int retryDelayTicks, Minecraft mc) {
        if (!featureEnabled || this.pendingReconnectServer == null || mc == null) {
            return;
        }
        if (mc.player != null && mc.world != null) {
            rememberCurrentServer(mc);
            clearState();
            return;
        }
        if (mc.currentScreen instanceof GuiConnecting) {
            return;
        }
        if (mc.currentScreen instanceof GuiDisconnected || mc.currentScreen instanceof GuiMultiplayer) {
            if (this.reconnectDelayTicks > 0) {
                this.reconnectDelayTicks--;
                return;
            }
            if (!infiniteAttempts && this.reconnectAttemptCount >= maxAttempts) {
                clearState();
                return;
            }
            try {
                ServerData reconnectTarget = copyServerData(this.pendingReconnectServer);
                if (reconnectTarget == null) {
                    clearState();
                    return;
                }
                mc.displayGuiScreen(new GuiConnecting(resolveReconnectParentScreen(mc), mc, reconnectTarget));
                this.reconnectAttemptCount++;
                this.reconnectDelayTicks = Math.max(0, retryDelayTicks);
                this.reconnectKeepAliveTicks = 200;
            } catch (Exception e) {
                zszlScriptMod.LOGGER.debug("自动重连执行失败", e);
                clearState();
            }
            return;
        }
        if (this.reconnectKeepAliveTicks > 0) {
            this.reconnectKeepAliveTicks--;
            return;
        }
        clearState();
    }

    public void rememberCurrentServer(Minecraft mc) {
        if (mc == null || mc.isSingleplayer()) {
            return;
        }
        ServerData current = copyServerData(mc.getCurrentServerData());
        if (current != null) {
            this.lastConnectedServer = current;
        }
    }

    public void clearState() {
        this.pendingReconnectServer = null;
        this.reconnectDelayTicks = 0;
        this.reconnectKeepAliveTicks = 0;
        this.reconnectAttemptCount = 0;
    }

    public ServerData getPendingReconnectServer() {
        return this.pendingReconnectServer;
    }

    public int getReconnectDelayTicks() {
        return this.reconnectDelayTicks;
    }

    public int getReconnectAttemptCount() {
        return this.reconnectAttemptCount;
    }

    private static ServerData copyServerData(ServerData source) {
        if (source == null || source.serverIP == null || source.serverIP.trim().isEmpty()) {
            return null;
        }
        String name = source.serverName == null || source.serverName.trim().isEmpty()
                ? source.serverIP.trim()
                : source.serverName;
        return new ServerData(name, source.serverIP.trim(), false);
    }

    private static GuiScreen resolveReconnectParentScreen(Minecraft mc) {
        if (mc != null && mc.currentScreen instanceof GuiMultiplayer) {
            return (GuiScreen) mc.currentScreen;
        }
        return new GuiMultiplayer(new GuiMainMenu());
    }
}
