package com.zszl.zszlScriptMod.gui.packet;

import com.google.common.collect.BiMap;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PacketIdMapping {

    // SERVERBOUND: 客户端 -> 服务端
    private static final Map<Integer, Class<? extends Packet<?>>> SERVERBOUND_PACKETS = new HashMap<>();
    // CLIENTBOUND: 服务端 -> 客户端
    private static final Map<Integer, Class<? extends Packet<?>>> CLIENTBOUND_PACKETS = new HashMap<>();

    static {
        try {
            if (!tryLoadMappingsViaReflection()) {
                throw new IllegalStateException("No valid PLAY packet direction map found in EnumConnectionState");
            }

            zszlScriptMod.LOGGER.info(
                    "Loaded packet mappings via reflection. serverbound={}, clientbound={}",
                    SERVERBOUND_PACKETS.size(),
                    CLIENTBOUND_PACKETS.size());
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error(
                    "Failed to load packet mappings via reflection, using fallback mapping table.",
                    e);
            loadFallbackMappings();
            zszlScriptMod.LOGGER.info(
                    "Loaded fallback packet mappings. serverbound={}, clientbound={}",
                    SERVERBOUND_PACKETS.size(),
                    CLIENTBOUND_PACKETS.size());
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean tryLoadMappingsViaReflection() throws IllegalAccessException {
        for (Field field : EnumConnectionState.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            field.setAccessible(true);
            Object rawValue = field.get(EnumConnectionState.PLAY);
            if (!(rawValue instanceof Map)) {
                continue;
            }

            Map<?, ?> candidateMap = (Map<?, ?>) rawValue;
            if (candidateMap.isEmpty()) {
                continue;
            }

            Object firstKey = candidateMap.keySet().iterator().next();
            Object firstValue = candidateMap.values().iterator().next();

            // 仅接受形如 Map<EnumPacketDirection, BiMap<Integer, PacketClass>> 的字段
            if (!(firstKey instanceof EnumPacketDirection) || !(firstValue instanceof BiMap)) {
                continue;
            }

            Map<EnumPacketDirection, BiMap<Integer, Class<? extends Packet<?>>>> directionMaps =
                    (Map<EnumPacketDirection, BiMap<Integer, Class<? extends Packet<?>>>>) candidateMap;

            BiMap<Integer, Class<? extends Packet<?>>> serverboundMap =
                    directionMaps.get(EnumPacketDirection.SERVERBOUND);
            BiMap<Integer, Class<? extends Packet<?>>> clientboundMap =
                    directionMaps.get(EnumPacketDirection.CLIENTBOUND);

            if (serverboundMap == null || serverboundMap.isEmpty()) {
                continue;
            }

            SERVERBOUND_PACKETS.clear();
            CLIENTBOUND_PACKETS.clear();

            SERVERBOUND_PACKETS.putAll(serverboundMap);
            if (clientboundMap != null) {
                CLIENTBOUND_PACKETS.putAll(clientboundMap);
            }

            return true;
        }

        return false;
    }

    private static void loadFallbackMappings() {
        SERVERBOUND_PACKETS.clear();
        CLIENTBOUND_PACKETS.clear();

        // ==================== Minecraft 1.12.2 PLAY / SERVERBOUND ====================
        // 已根据用户抓包结果修正关键 ID，并补全 1.12.2 常用表
        SERVERBOUND_PACKETS.put(0x00, net.minecraft.network.play.client.CPacketConfirmTeleport.class);
        SERVERBOUND_PACKETS.put(0x01, net.minecraft.network.play.client.CPacketTabComplete.class);
        SERVERBOUND_PACKETS.put(0x02, net.minecraft.network.play.client.CPacketChatMessage.class);
        SERVERBOUND_PACKETS.put(0x03, net.minecraft.network.play.client.CPacketClientStatus.class);
        SERVERBOUND_PACKETS.put(0x04, net.minecraft.network.play.client.CPacketClientSettings.class);
        SERVERBOUND_PACKETS.put(0x05, net.minecraft.network.play.client.CPacketConfirmTransaction.class);
        SERVERBOUND_PACKETS.put(0x06, net.minecraft.network.play.client.CPacketEnchantItem.class);
        SERVERBOUND_PACKETS.put(0x07, net.minecraft.network.play.client.CPacketClickWindow.class);
        SERVERBOUND_PACKETS.put(0x08, net.minecraft.network.play.client.CPacketCloseWindow.class);
        SERVERBOUND_PACKETS.put(0x09, net.minecraft.network.play.client.CPacketCustomPayload.class);
        SERVERBOUND_PACKETS.put(0x0A, net.minecraft.network.play.client.CPacketUseEntity.class);
        SERVERBOUND_PACKETS.put(0x0B, net.minecraft.network.play.client.CPacketKeepAlive.class);
        SERVERBOUND_PACKETS.put(0x0C, net.minecraft.network.play.client.CPacketPlayer.class);
        SERVERBOUND_PACKETS.put(0x0D, net.minecraft.network.play.client.CPacketPlayer.Position.class);
        SERVERBOUND_PACKETS.put(0x0E, net.minecraft.network.play.client.CPacketPlayer.PositionRotation.class);
        SERVERBOUND_PACKETS.put(0x0F, net.minecraft.network.play.client.CPacketPlayer.Rotation.class);
        SERVERBOUND_PACKETS.put(0x10, net.minecraft.network.play.client.CPacketVehicleMove.class);
        SERVERBOUND_PACKETS.put(0x11, net.minecraft.network.play.client.CPacketSteerBoat.class);
        SERVERBOUND_PACKETS.put(0x12, net.minecraft.network.play.client.CPacketPlaceRecipe.class);
        SERVERBOUND_PACKETS.put(0x13, net.minecraft.network.play.client.CPacketPlayerAbilities.class);
        SERVERBOUND_PACKETS.put(0x14, net.minecraft.network.play.client.CPacketPlayerDigging.class);
        SERVERBOUND_PACKETS.put(0x15, net.minecraft.network.play.client.CPacketEntityAction.class);
        SERVERBOUND_PACKETS.put(0x16, net.minecraft.network.play.client.CPacketInput.class);
        SERVERBOUND_PACKETS.put(0x17, net.minecraft.network.play.client.CPacketRecipeInfo.class);
        SERVERBOUND_PACKETS.put(0x18, net.minecraft.network.play.client.CPacketResourcePackStatus.class);
        SERVERBOUND_PACKETS.put(0x19, net.minecraft.network.play.client.CPacketSeenAdvancements.class);
        SERVERBOUND_PACKETS.put(0x1A, net.minecraft.network.play.client.CPacketHeldItemChange.class);
        SERVERBOUND_PACKETS.put(0x1B, net.minecraft.network.play.client.CPacketCreativeInventoryAction.class);
        SERVERBOUND_PACKETS.put(0x1C, net.minecraft.network.play.client.CPacketUpdateSign.class);
        SERVERBOUND_PACKETS.put(0x1D, net.minecraft.network.play.client.CPacketAnimation.class);
        SERVERBOUND_PACKETS.put(0x1E, net.minecraft.network.play.client.CPacketSpectate.class);
        SERVERBOUND_PACKETS.put(0x1F, net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock.class);
        SERVERBOUND_PACKETS.put(0x20, net.minecraft.network.play.client.CPacketPlayerTryUseItem.class);

        // ==================== Minecraft 1.12.2 PLAY / CLIENTBOUND ====================
        // 这里优先补入用户实测抓包得到的 ID，确保按 ID 模拟收包时常见包可正确解析
        CLIENTBOUND_PACKETS.put(0x00, net.minecraft.network.play.server.SPacketSpawnObject.class);
        CLIENTBOUND_PACKETS.put(0x03, net.minecraft.network.play.server.SPacketSpawnMob.class);
        CLIENTBOUND_PACKETS.put(0x0B, net.minecraft.network.play.server.SPacketBlockChange.class);
        CLIENTBOUND_PACKETS.put(0x10, net.minecraft.network.play.server.SPacketMultiBlockChange.class);
        CLIENTBOUND_PACKETS.put(0x13, net.minecraft.network.play.server.SPacketOpenWindow.class);
        CLIENTBOUND_PACKETS.put(0x14, net.minecraft.network.play.server.SPacketWindowItems.class);
        CLIENTBOUND_PACKETS.put(0x16, net.minecraft.network.play.server.SPacketSetSlot.class);
        CLIENTBOUND_PACKETS.put(0x18, net.minecraft.network.play.server.SPacketCustomPayload.class);
        CLIENTBOUND_PACKETS.put(0x1B, net.minecraft.network.play.server.SPacketEntityStatus.class);
        CLIENTBOUND_PACKETS.put(0x1D, net.minecraft.network.play.server.SPacketUnloadChunk.class);
        CLIENTBOUND_PACKETS.put(0x1F, net.minecraft.network.play.server.SPacketKeepAlive.class);
        CLIENTBOUND_PACKETS.put(0x20, net.minecraft.network.play.server.SPacketChunkData.class);
        CLIENTBOUND_PACKETS.put(0x21, net.minecraft.network.play.server.SPacketEffect.class);
        CLIENTBOUND_PACKETS.put(0x26, net.minecraft.network.play.server.SPacketEntity.S15PacketEntityRelMove.class);
        CLIENTBOUND_PACKETS.put(0x27, net.minecraft.network.play.server.SPacketEntity.S17PacketEntityLookMove.class);
        CLIENTBOUND_PACKETS.put(0x28, net.minecraft.network.play.server.SPacketEntity.S16PacketEntityLook.class);
        CLIENTBOUND_PACKETS.put(0x2E, net.minecraft.network.play.server.SPacketPlayerListItem.class);
        CLIENTBOUND_PACKETS.put(0x31, net.minecraft.network.play.server.SPacketRecipeBook.class);
        CLIENTBOUND_PACKETS.put(0x32, net.minecraft.network.play.server.SPacketDestroyEntities.class);
        CLIENTBOUND_PACKETS.put(0x36, net.minecraft.network.play.server.SPacketEntityHeadLook.class);
        CLIENTBOUND_PACKETS.put(0x3C, net.minecraft.network.play.server.SPacketEntityMetadata.class);
        CLIENTBOUND_PACKETS.put(0x3E, net.minecraft.network.play.server.SPacketEntityVelocity.class);
        CLIENTBOUND_PACKETS.put(0x47, net.minecraft.network.play.server.SPacketTimeUpdate.class);
        CLIENTBOUND_PACKETS.put(0x49, net.minecraft.network.play.server.SPacketSoundEffect.class);
        CLIENTBOUND_PACKETS.put(0x4B, net.minecraft.network.play.server.SPacketCollectItem.class);
        CLIENTBOUND_PACKETS.put(0x4C, net.minecraft.network.play.server.SPacketEntityTeleport.class);
        CLIENTBOUND_PACKETS.put(0x4D, net.minecraft.network.play.server.SPacketAdvancementInfo.class);
        CLIENTBOUND_PACKETS.put(0x4E, net.minecraft.network.play.server.SPacketEntityProperties.class);
    }

    public static Class<? extends Packet<?>> getClassById(int id) {
        return SERVERBOUND_PACKETS.get(id);
    }

    public static Class<? extends Packet<?>> getClientboundClassById(int id) {
        return CLIENTBOUND_PACKETS.get(id);
    }
}