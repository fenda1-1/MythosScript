package com.zszl.zszlScriptMod.mcp;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class McpPacketTraceTest {
    @Test public void timeWindowAndDirectionalCountContextAreMerged() {
        List<McpPacketTrace.PacketSnapshot> packets = Arrays.asList(
                packet("C2S", 0, 700), packet("C2S", 1, 900),
                packet("S2C", 0, 1100), packet("S2C", 1, 1800));

        McpPacketTrace.Correlation result = McpPacketTrace.correlate(1000, packets, 100, 1, 1, 20);

        assertEquals(2, result.candidateCount);
        assertEquals(2, result.matches.size());
        assertEquals(900, result.matches.get(0).packet.timestamp);
        assertEquals(1100, result.matches.get(1).packet.timestamp);
        assertTrue(result.matches.get(0).reasons.contains("beforeCount"));
        assertTrue(result.matches.get(1).reasons.contains("afterCount"));
        assertFalse(result.truncated);
    }

    @Test public void aggregatedPacketIntervalMatchesWhenInputFallsInsideIt() {
        McpPacketTrace.PacketSnapshot packet = packet("C2S", 0, 900, 1100);

        McpPacketTrace.Correlation result = McpPacketTrace.correlate(1000,
                Arrays.asList(packet), 0, 0, 0, 10);

        assertEquals(1, result.matches.size());
        assertEquals(0, result.matches.get(0).absoluteDelta(1000));
        assertTrue(result.matches.get(0).reasons.contains("timeWindow"));
    }

    @Test public void packetLimitKeepsClosestPacketsAndReportsTruncation() {
        List<McpPacketTrace.PacketSnapshot> packets = Arrays.asList(
                packet("C2S", 0, 700), packet("C2S", 1, 950),
                packet("S2C", 0, 1050), packet("S2C", 1, 1300));

        McpPacketTrace.Correlation result = McpPacketTrace.correlate(1000, packets, 400, 0, 0, 2);

        assertEquals(4, result.candidateCount);
        assertEquals(2, result.matches.size());
        assertEquals(950, result.matches.get(0).packet.timestamp);
        assertEquals(1050, result.matches.get(1).packet.timestamp);
        assertTrue(result.truncated);
    }

    private static McpPacketTrace.PacketSnapshot packet(String direction, int index, long timestamp) {
        return packet(direction, index, timestamp, timestamp);
    }

    private static McpPacketTrace.PacketSnapshot packet(String direction, int index, long timestamp, long lastTimestamp) {
        return new McpPacketTrace.PacketSnapshot(direction, index, null, timestamp, lastTimestamp, 1, 0);
    }
}
