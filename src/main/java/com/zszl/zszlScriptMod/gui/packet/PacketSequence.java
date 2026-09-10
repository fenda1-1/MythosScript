// 文件路径: src/main/java/com/zszl/zszlScriptMod/gui/packet/PacketSequence.java
package com.zszl.zszlScriptMod.gui.packet;

import java.util.ArrayList;
import java.util.List;

public class PacketSequence {
    public String name;
    // 核心修复：移除 transient 关键字，确保该字段能被序列化
    public List<PacketToSend> packets;

    public static class PacketToSend {
        public String direction; // C->S(default) / S->C
        public boolean isFmlPacket;
        public Integer packetId;
        public String channel;
        public String hexData;
        public int delayTicks;

        // 构造函数用于FML包
        public PacketToSend(String channel, String hexData, int delayTicks) {
            this.direction = "C2S";
            this.isFmlPacket = true;
            this.packetId = null;
            this.channel = channel;
            this.hexData = hexData;
            this.delayTicks = delayTicks;
        }

        // 构造函数用于标准包
        public PacketToSend(int packetId, String hexData, int delayTicks) {
            this.direction = "C2S";
            this.isFmlPacket = false;
            this.packetId = packetId;
            this.channel = "N/A";
            this.hexData = hexData;
            this.delayTicks = delayTicks;
        }
    }

    public PacketSequence(String name) {
        this.name = name;
        this.packets = new ArrayList<>();
    }
}
