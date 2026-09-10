package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketSequence;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

/** Pure packet-domain state transitions used by the embedded workbench. */
public final class PacketDrafts {
    private PacketDrafts() {
    }

    public static final class DetailSelection {
        private final List<PacketCaptureHandler.CapturedPacketData> packets;
        private final int index;

        public DetailSelection(List<PacketCaptureHandler.CapturedPacketData> packets, int index) {
            this.packets = packets == null ? Collections.<PacketCaptureHandler.CapturedPacketData>emptyList()
                    : new ArrayList<>(packets);
            this.index = index;
        }

        public List<PacketCaptureHandler.CapturedPacketData> packets() {
            return Collections.unmodifiableList(packets);
        }

        public int index() {
            return index;
        }

        public PacketCaptureHandler.CapturedPacketData selected() {
            return index < 0 || index >= packets.size() ? null : packets.get(index);
        }

        public DetailSelection back() {
            return new DetailSelection(packets, -1);
        }
    }

    public static final class FilterDraft {
        private final PacketFilterConfig config;
        private final List<String> originalWhitelist;
        private final List<String> originalBlacklist;
        private final int originalMaxCapturedPackets;
        private final List<String> whitelist;
        private final List<String> blacklist;
        private final int maxCapturedPackets;

        private FilterDraft(PacketFilterConfig config) {
            this.config = config;
            this.originalWhitelist = config.whitelistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.whitelistFilters);
            this.originalBlacklist = config.blacklistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.blacklistFilters);
            this.originalMaxCapturedPackets = config.maxCapturedPackets;
            this.whitelist = config.whitelistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.whitelistFilters);
            this.blacklist = config.blacklistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.blacklistFilters);
            this.maxCapturedPackets = config.maxCapturedPackets;
        }

        public static FilterDraft capture(PacketFilterConfig config) {
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
            return new FilterDraft(config);
        }

        public FilterDraft withWhitelist(String commaSeparated) {
            return new FilterDraft(config, split(commaSeparated), blacklist, maxCapturedPackets);
        }

        public FilterDraft withBlacklist(String commaSeparated) {
            return new FilterDraft(config, whitelist, split(commaSeparated), maxCapturedPackets);
        }

        public FilterDraft withMaxCapturedPackets(int value) {
            return new FilterDraft(config, whitelist, blacklist, clamp(value, 100, 10000));
        }

        public void save() {
            config.whitelistFilters = new ArrayList<>(whitelist);
            config.blacklistFilters = new ArrayList<>(blacklist);
            config.maxCapturedPackets = maxCapturedPackets;
            PacketFilterConfig.save();
        }

        public void cancel() {
            config.whitelistFilters = new ArrayList<>(originalWhitelist);
            config.blacklistFilters = new ArrayList<>(originalBlacklist);
            config.maxCapturedPackets = originalMaxCapturedPackets;
        }

        public List<String> whitelist() { return Collections.unmodifiableList(whitelist); }
        public List<String> blacklist() { return Collections.unmodifiableList(blacklist); }
        public int maxCapturedPackets() { return maxCapturedPackets; }

        private FilterDraft(PacketFilterConfig config, List<String> whitelist, List<String> blacklist, int max) {
            this.config = config;
            this.originalWhitelist = config.whitelistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.whitelistFilters);
            this.originalBlacklist = config.blacklistFilters == null ? new ArrayList<String>() : new ArrayList<>(config.blacklistFilters);
            this.originalMaxCapturedPackets = config.maxCapturedPackets;
            this.whitelist = new ArrayList<>(whitelist);
            this.blacklist = new ArrayList<>(blacklist);
            this.maxCapturedPackets = max;
        }

        private static List<String> split(String value) {
            List<String> result = new ArrayList<>();
            if (value != null) {
                for (String item : value.split(",")) {
                    if (!item.trim().isEmpty()) result.add(item.trim());
                }
            }
            return result;
        }

        private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    }

    public static final class SequenceDraft {
        private final PacketSequence sequence;

        public SequenceDraft(String name) { sequence = new PacketSequence(name); }
        public SequenceDraft(PacketSequence source) { sequence = copySequence(source); }
        public PacketSequence sequence() { return sequence; }
        public SequenceDraft add(PacketSequence.PacketToSend packet) {
            if (packet != null) sequence.packets.add(copyPacket(packet));
            return this;
        }
        public boolean save() { return PacketSequenceManager.saveSequence(sequence); }
        public boolean save(SequenceStore store) { return store.save(sequence); }
    }

    public interface SequenceStore { boolean save(PacketSequence sequence); }

    static PacketSequence copySequence(PacketSequence source) {
        PacketSequence copy = new PacketSequence(source == null ? "" : source.name);
        if (source == null || source.packets == null) {
            return copy;
        }
        for (PacketSequence.PacketToSend packet : source.packets) {
            if (packet == null) {
                continue;
            }
            copy.packets.add(copyPacket(packet));
        }
        return copy;
    }

    private static PacketSequence.PacketToSend copyPacket(PacketSequence.PacketToSend packet) {
        PacketSequence.PacketToSend item = packet.isFmlPacket
                ? new PacketSequence.PacketToSend(packet.channel, packet.hexData, packet.delayTicks)
                : new PacketSequence.PacketToSend(packet.packetId == null ? 0 : packet.packetId.intValue(),
                        packet.hexData, packet.delayTicks);
        item.direction = packet.direction;
        return item;
    }
}
