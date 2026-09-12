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

package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.BlockChangeEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.ChunkEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.type.EventState;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Pair;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketCombatEvent;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import net.minecraft.network.play.server.SPacketExplosion;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author Brady
 * @since 8/3/2018
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

        // Vanilla reaches this call on the client thread, after packet scheduling.
        @Redirect(method = "handleEntityVelocity", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/entity/Entity;setVelocity(DDD)V"), require = 1)
        private void zszl$filterVelocity(Entity entity, double x, double y, double z) {
                double resistance = entity == Minecraft.getMinecraft().player
                        ? MovementFeatureManager.getKnockbackResistance() : 0.0D;
                if (resistance >= 1.0D) return;
                double keep = 1.0D - resistance;
                entity.setVelocity(entity.motionX * resistance + x * keep,
                        entity.motionY * resistance + y * keep, entity.motionZ * resistance + z * keep);
        }

        @Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/network/play/server/SPacketExplosion;getMotionX()F"), require = 1)
        private float zszl$explosionX(SPacketExplosion packet) {
                return (float) (packet.getMotionX() * (1.0D - MovementFeatureManager.getKnockbackResistance()));
        }

        @Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/network/play/server/SPacketExplosion;getMotionY()F"), require = 1)
        private float zszl$explosionY(SPacketExplosion packet) {
                return (float) (packet.getMotionY() * (1.0D - MovementFeatureManager.getKnockbackResistance()));
        }

        @Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/network/play/server/SPacketExplosion;getMotionZ()F"), require = 1)
        private float zszl$explosionZ(SPacketExplosion packet) {
                return (float) (packet.getMotionZ() * (1.0D - MovementFeatureManager.getKnockbackResistance()));
        }

        @Inject(method = "handlePlayerPosLook", at = @At("RETURN"))
        private void postHandlePlayerPosLook(SPacketPlayerPosLook packetIn, CallbackInfo ci) {
                KillAuraHandler.INSTANCE.onPlayerPositionCorrectionHandled(packetIn);
        }

        @Inject(method = "handleChunkData", at = @At(value = "INVOKE", target = "net/minecraft/world/chunk/Chunk.read(Lnet/minecraft/network/PacketBuffer;IZ)V"))
        private void preRead(SPacketChunkData packetIn, CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider()
                                .getBaritoneForConnection((NetHandlerPlayClient) (Object) this);
                if (baritone == null) {
                        return;
                }
                baritone.getGameEventHandler().onChunkEvent(
                                new ChunkEvent(
                                                EventState.PRE,
                                                packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL
                                                                : ChunkEvent.Type.POPULATE_PARTIAL,
                                                packetIn.getChunkX(),
                                                packetIn.getChunkZ()));
        }

        @Inject(method = "handleChunkData", at = @At("RETURN"))
        private void postHandleChunkData(SPacketChunkData packetIn, CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider()
                                .getBaritoneForConnection((NetHandlerPlayClient) (Object) this);
                if (baritone == null) {
                        return;
                }
                baritone.getGameEventHandler().onChunkEvent(
                                new ChunkEvent(
                                                EventState.POST,
                                                packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL
                                                                : ChunkEvent.Type.POPULATE_PARTIAL,
                                                packetIn.getChunkX(),
                                                packetIn.getChunkZ()));
        }

        @Inject(method = "handleBlockChange", at = @At("RETURN"))
        private void postHandleBlockChange(SPacketBlockChange packetIn, CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider()
                                .getBaritoneForConnection((NetHandlerPlayClient) (Object) this);
                if (baritone == null) {
                        return;
                }

                final ChunkPos pos = new ChunkPos(packetIn.getBlockPosition().getX() >> 4,
                                packetIn.getBlockPosition().getZ() >> 4);
                final Pair<BlockPos, IBlockState> changed = new Pair<>(packetIn.getBlockPosition(),
                                packetIn.getBlockState());
                baritone.getGameEventHandler()
                                .onBlockChange(new BlockChangeEvent(pos, Collections.singletonList(changed)));
        }

        @Inject(method = "handleMultiBlockChange", at = @At("RETURN"))
        private void postHandleMultiBlockChange(SPacketMultiBlockChange packetIn, CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider()
                                .getBaritoneForConnection((NetHandlerPlayClient) (Object) this);
                if (baritone == null) {
                        return;
                }

                // All blocks have the same ChunkPos
                final ChunkPos pos = new ChunkPos(packetIn.getChangedBlocks()[0].getPos());

                baritone.getGameEventHandler().onBlockChange(new BlockChangeEvent(
                                pos,
                                Arrays.stream(packetIn.getChangedBlocks())
                                                .map(data -> new Pair<>(data.getPos(), data.getBlockState()))
                                                .collect(Collectors.toList())));
        }

        @Inject(method = "handleCombatEvent", at = @At(value = "INVOKE", target = "net/minecraft/client/Minecraft.displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V"))
        private void onPlayerDeath(SPacketCombatEvent packetIn, CallbackInfo ci) {
                IBaritone baritone = BaritoneAPI.getProvider()
                                .getBaritoneForConnection((NetHandlerPlayClient) (Object) this);
                if (baritone == null) {
                        return;
                }
                baritone.getGameEventHandler().onPlayerDeath();
        }

        @Inject(method = "handleEntityHeadLook", at = @At("HEAD"), cancellable = true)
        private void guardNullEntityHeadLook(SPacketEntityHeadLook packetIn, CallbackInfo ci) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.world == null || packetIn == null) {
                        ci.cancel();
                        return;
                }
                Entity entity = packetIn.getEntity(mc.world);
                if (entity == null) {
                        ci.cancel();
                }
        }

        @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
        private void guardNullEntityVelocity(SPacketEntityVelocity packetIn, CallbackInfo ci) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.world == null || packetIn == null) {
                        ci.cancel();
                        return;
                }
                Entity entity = mc.world.getEntityByID(packetIn.getEntityID());
                if (entity == null) {
                        ci.cancel();
                }
        }
}
