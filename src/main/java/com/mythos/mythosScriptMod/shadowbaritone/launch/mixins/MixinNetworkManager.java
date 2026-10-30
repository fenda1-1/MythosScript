/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.PacketEvent;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.type.EventState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraBlocking;
import com.mythos.mythosScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/6/2018
 */
@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @ModifyVariable(method = "dispatchPacket", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private Packet<?> mythos$sendShieldFacing(Packet<?> packet) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND || !(packet instanceof CPacketPlayer)) {
            return packet;
        }
        net.minecraft.client.entity.EntityPlayerSP player = com.mythos.mythosScriptMod.mixin.SessionFieldAccess.getPlayer(net.minecraft.client.Minecraft.getMinecraft());
        if (player == null || player.connection == null
                || player.connection.getNetworkManager() != (NetworkManager) (Object) this) return packet;
        java.util.Optional<com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation> facing =
                KillAuraHandler.INSTANCE.getBlockingServerRotation(player);
        if (!facing.isPresent()) return packet;
        return KillAuraBlocking.withRotation((CPacketPlayer) packet, facing.get());
    }

    @Inject(method = "dispatchPacket", at = @At("HEAD"), cancellable = true)
    private void mythos$dropLocalPositionDuringTeleport(Packet<?> inPacket,
            final GenericFutureListener<? extends Future<? super Void>>[] futureListeners, CallbackInfo ci) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND) {
            return;
        }
        if (!(inPacket instanceof CPacketPlayer.Position)
                && !(inPacket instanceof CPacketPlayer.PositionRotation)) {
            return;
        }
        if (KillAuraHandler.shouldSuppressVanillaPosition()) {
            ci.cancel();
        }
    }

    /**
     * 去除移动饥饿值消耗：服务端只在收到 START_SPRINTING 后按疾跑结算移动消耗（0.1/米），
     * 不上报即按走路结算（0）。客户端本地疾跑速度不受影响。
     */
    @Inject(method = "dispatchPacket", at = @At("HEAD"), cancellable = true)
    private void mythos$dropSprintStartForMoveHunger(Packet<?> inPacket,
            final GenericFutureListener<? extends Future<? super Void>>[] futureListeners, CallbackInfo ci) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND
                || !(inPacket instanceof CPacketEntityAction)
                || ((CPacketEntityAction) inPacket).getAction() != CPacketEntityAction.Action.START_SPRINTING
                || !mythos$isLocalPlayerConnection()
                || !ItemFeatureManager.isEnabled("force_no_hunger")) {
            return;
        }
        ci.cancel();
    }

    /** 最近一次实际发出的 CPacketPlayer 着地标记，即服务端当前认为的 onGround。 */
    @Unique
    private boolean mythos$lastSentOnGround = false;

    /** 最近一次实际发出的带坐标 CPacketPlayer 的 Y。 */
    @Unique
    private double mythos$lastSentY = Double.NaN;

    /**
     * 服务端在 "上一包着地 && 本包未着地 && Y 上升" 时调用 jump() 结算跳跃消耗（0.05，疾跑跳 0.2）。
     * 上升阶段改报着地即可避开；到达最高点后如实上报，摔落距离从最高点开始正常累计。
     */
    @Unique
    private void mythos$spoofAscentOnGround(CPacketPlayer packet, boolean enabled) {
        boolean onGround = packet.isOnGround();
        if (packet instanceof CPacketPlayer.Position || packet instanceof CPacketPlayer.PositionRotation) {
            double y = packet.getY(0.0D);
            if (enabled && !onGround && this.mythos$lastSentOnGround
                    && !Double.isNaN(this.mythos$lastSentY) && y > this.mythos$lastSentY) {
                ((AccessorCPacketPlayer) packet).mythos$setOnGround(true);
                onGround = true;
            }
            this.mythos$lastSentY = y;
        }
        this.mythos$lastSentOnGround = onGround;
    }

    @Unique
    private boolean mythos$isLocalPlayerConnection() {
        net.minecraft.client.entity.EntityPlayerSP player = com.mythos.mythosScriptMod.mixin.SessionFieldAccess.getPlayer(net.minecraft.client.Minecraft.getMinecraft());
        return player != null && player.connection != null
                && player.connection.getNetworkManager() == (Object) this;
    }

    @Shadow(remap = false)
    private Channel field_150746_k;

    @Shadow(remap = false)
    @Final
    private EnumPacketDirection field_179294_g;

    @Inject(method = "dispatchPacket", at = @At("HEAD"))
    private void preDispatchPacket(Packet<?> inPacket,
            final GenericFutureListener<? extends Future<? super Void>>[] futureListeners, CallbackInfo ci) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND) {
            return;
        }

        net.minecraft.client.entity.EntityPlayerSP player = com.mythos.mythosScriptMod.mixin.SessionFieldAccess.getPlayer(net.minecraft.client.Minecraft.getMinecraft());
        if (inPacket instanceof CPacketPlayer && player != null && player.connection != null
                && player.connection.getNetworkManager() == (Object) this
                && MovementFeatureManager.isEnabled("no_fall")) {
            ((AccessorCPacketPlayer) inPacket).mythos$setOnGround(true);
        }
        if (inPacket instanceof CPacketPlayer && mythos$isLocalPlayerConnection()) {
            mythos$spoofAscentOnGround((CPacketPlayer) inPacket, ItemFeatureManager.isEnabled("force_no_hunger"));
        }

        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().connection
                    .getNetworkManager() == (NetworkManager) (Object) this) {
                ibaritone.getGameEventHandler()
                        .onSendPacket(new PacketEvent((NetworkManager) (Object) this, EventState.PRE, inPacket));
            }
        }
    }

    @Inject(method = "dispatchPacket", at = @At("RETURN"))
    private void postDispatchPacket(Packet<?> inPacket,
            final GenericFutureListener<? extends Future<? super Void>>[] futureListeners, CallbackInfo ci) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND) {
            return;
        }

        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().connection
                    .getNetworkManager() == (NetworkManager) (Object) this) {
                ibaritone.getGameEventHandler()
                        .onSendPacket(new PacketEvent((NetworkManager) (Object) this, EventState.POST, inPacket));
            }
        }
    }

    @Inject(method = "channelRead0", at = @At(value = "INVOKE", target = "net/minecraft/network/Packet.processPacket(Lnet/minecraft/network/INetHandler;)V"))
    private void preProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.field_179294_g != EnumPacketDirection.CLIENTBOUND) {
            return;
        }
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().connection
                    .getNetworkManager() == (NetworkManager) (Object) this) {
                ibaritone.getGameEventHandler()
                        .onReceivePacket(new PacketEvent((NetworkManager) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(method = "channelRead0", at = @At("RETURN"))
    private void postProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!this.field_150746_k.isOpen() || this.field_179294_g != EnumPacketDirection.CLIENTBOUND) {
            return;
        }
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            if (ibaritone.getPlayerContext().player() != null && ibaritone.getPlayerContext().player().connection
                    .getNetworkManager() == (NetworkManager) (Object) this) {
                ibaritone.getGameEventHandler()
                        .onReceivePacket(new PacketEvent((NetworkManager) (Object) this, EventState.POST, packet));
            }
        }
    }
}
