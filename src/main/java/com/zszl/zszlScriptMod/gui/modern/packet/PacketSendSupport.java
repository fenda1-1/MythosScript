package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.utils.ModUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Direction-aware packet replay adapter shared by viewer and sequence editor. */
public final class PacketSendSupport {
    private PacketSendSupport() { }

    public static int sendSequence(Minecraft minecraft, PacketSequence sequence) {
        if (sequence == null || sequence.packets == null) return 0;
        List<Integer> cumulativeDelays = cumulativeDelays(sequence);
        int total = cumulativeDelays.isEmpty() ? 0 : cumulativeDelays.get(cumulativeDelays.size() - 1);
        AtomicInteger cumulative = new AtomicInteger(0);
        for (int i = 0; i < sequence.packets.size(); i++) {
            final PacketSequence.PacketToSend packet = sequence.packets.get(i);
            if (packet == null) continue;
            final int index = i + 1;
            cumulative.set(cumulativeDelays.get(i));
            Runnable action = () -> sendOne(packet);
            if (ModUtils.DelayScheduler.instance != null) ModUtils.DelayScheduler.instance.schedule(action, cumulative.get());
            else action.run();
            if (minecraft != null && minecraft.player != null) minecraft.player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + tr("gui.modern.pktsend.fmt.queued", String.valueOf(index), String.valueOf(sequence.packets.size()))));
        }
        return total;
    }

    /** Returns the cumulative tick offsets used by the replay scheduler. */
    static List<Integer> cumulativeDelays(PacketSequence sequence) {
        if (sequence == null || sequence.packets == null || sequence.packets.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>(sequence.packets.size());
        long cumulative = 0L;
        for (PacketSequence.PacketToSend packet : sequence.packets) {
            cumulative += Math.max(0, packet == null ? 0 : packet.delayTicks);
            result.add(cumulative > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cumulative);
        }
        return result;
    }

    static int sendPackets(Minecraft minecraft, List<com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData> packets,
            int delayTicks, String direction) {
        if (packets == null) return 0;
        PacketSequence sequence = new PacketSequence("viewer_replay");
        for (com.zszl.zszlScriptMod.utils.PacketCaptureHandler.CapturedPacketData packet : packets) {
            if (packet == null) continue;
            PacketSequence.PacketToSend item = packet.isFmlPacket
                    ? new PacketSequence.PacketToSend(packet.channel, packet.getHexData(), delayTicks)
                    : new PacketSequence.PacketToSend(packet.packetId == null ? 0 : packet.packetId, packet.getHexData(), delayTicks);
            item.direction = normalizeDirection(direction); sequence.packets.add(item);
        }
        return sendSequence(minecraft, sequence);
    }

    private static void sendOne(PacketSequence.PacketToSend packet) {
        boolean inbound = "S2C".equals(normalizeDirection(packet.direction));
        if (inbound) {
            if (packet.isFmlPacket) ModUtils.mockReceiveFmlPacket(packet.channel, packet.hexData);
            else if (packet.packetId != null) ModUtils.mockReceiveStandardPacketById(packet.packetId, packet.hexData);
        } else if (packet.isFmlPacket) ModUtils.sendFmlPacket(packet.channel, packet.hexData);
        else if (packet.packetId != null) ModUtils.sendStandardPacketById(packet.packetId, packet.hexData);
    }

    /** Accept the labels persisted by both the old dropdown and the native editor. */
    static String normalizeDirection(String value) {
        if (value == null) return "C2S";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("S2C".equals(normalized) || "S->C".equals(normalized) || "S_TO_C".equals(normalized)
                || "SERVERTOCLIENT".equals(normalized) || "INBOUND".equals(normalized)
                || "RECV".equals(normalized) || "RECEIVE".equals(normalized)
                || "RECEIVED".equals(normalized) || normalized.contains("接收")) return "S2C";
        if ("C2S".equals(normalized) || "C->S".equals(normalized) || "C_TO_S".equals(normalized)
                || "CLIENTTOSERVER".equals(normalized) || "OUTBOUND".equals(normalized)
                || "SEND".equals(normalized) || "SENT".equals(normalized) || normalized.contains("发送")) return "C2S";
        return "C2S";
    }

    static boolean isInboundDirection(String value) {
        return "S2C".equals(normalizeDirection(value));
    }

    /** Integer.decode-compatible parser used by the sequence editor's ID field. */
    static int parsePacketId(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return Math.max(0, Math.min(255, Integer.decode(value.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
