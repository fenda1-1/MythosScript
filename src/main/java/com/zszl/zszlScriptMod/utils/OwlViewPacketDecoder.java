// 文件路径: src/main/java/com/zszl/zszlScriptMod/utils/OwlViewPacketDecoder.java
// !! 这是修复了功能冲突并确保兼容性的最终版本 !!
package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class OwlViewPacketDecoder {

    public static String decode(String channel, byte[] data) {
        if ("OwlViewChannel".equals(channel)) {
            return decodeOwlView(data);
        } else if ("OwlControlChannel".equals(channel)) {
            return decodeOwlControl(data);
        }
        return "[未知频道: " + channel + "]";
    }

    private static String decodeOwlControl(byte[] data) {
        if (data == null || data.length == 0)
            return "[空数据包]";
        PacketBuffer buffer = new PacketBuffer(Objects.requireNonNull(Unpooled.wrappedBuffer(data)));
        try {
            if (buffer.readableBytes() < 1)
                return "[数据包过短]";
            buffer.readByte();
            String operation = buffer.readString(32767);
            StringBuilder opBuilder = new StringBuilder("[" + operation + "]");

            if ("Control_set_successKeyBoardMonitor".equals(operation)) {
                opBuilder.append(": \"").append(buffer.readString(32767)).append("\"");
            } else if ("Control_adventure".equals(operation)) {
                String subOp = readControlSubOperation(buffer);
                if (subOp == null || subOp.isEmpty()) {
                    // 兜底：有些包会出现子操作字符串读取失败，但尾部仍是 [8字节经验 + 1字节标记]
                    if (buffer.readableBytes() >= 9) {
                        long exp = buffer.readLong();
                        boolean tail = buffer.readBoolean();
                        opBuilder.append(" -> [Control_set_adventureExp]")
                                .append(": exp=").append(exp)
                                .append(", tail=").append(tail)
                                .append(" [fallback]");
                    } else {
                        opBuilder.append(" [子操作为空]");
                    }
                } else {
                    opBuilder.append(" -> [").append(subOp).append("]");
                    if ("Control_set_adventureExp".equals(subOp)) {
                        if (buffer.readableBytes() >= 8) {
                            long exp = buffer.readLong();
                            opBuilder.append(": exp=").append(exp);
                            if (buffer.readableBytes() >= 1) {
                                opBuilder.append(", tail=").append(buffer.readBoolean());
                            }
                        } else {
                            opBuilder.append(" [经验值数据不完整]");
                        }
                    }
                }
            } else if ("Control_set_adventureExp".equals(operation)) {
                if (buffer.readableBytes() >= 8) {
                    long exp = buffer.readLong();
                    opBuilder.append(": exp=").append(exp);
                    if (buffer.readableBytes() >= 1) {
                        opBuilder.append(", tail=").append(buffer.readBoolean());
                    }
                } else {
                    opBuilder.append(" [数据不完整]");
                }
            } else if ("Control_keyboard".equals(operation)) {
                if (buffer.readableBytes() < 8)
                    return opBuilder.append(" [数据不完整]").toString();
                buffer.readInt();
                buffer.readInt();
                String subOp = buffer.readString(32767);
                opBuilder.append(" -> [").append(subOp).append("]");
                if ("Control_set_keyboardOn".equals(subOp)) {
                    opBuilder.append(": Key: \"").append(buffer.readString(32767)).append("\"");
                }
            } else if (buffer.readableBytes() > 0) {
                opBuilder.append(" (未知参数，剩余HEX: ").append(ByteBufUtil.hexDump(buffer.readBytes(buffer.readableBytes())))
                        .append(")");
            }
            return opBuilder.toString();
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("解析OwlControl数据包时发生错误: " + e.getMessage(), e);
            return "[解码错误: " + e.getMessage() + "]";
        }
    }

    private static String readControlSubOperation(PacketBuffer buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return "";
        }

        // 1) OwlControl 常见格式：unsigned short + UTF-8 bytes
        // 例如：00 18 + "Control_set_adventureExp"
        if (buffer.readableBytes() >= 2) {
            buffer.markReaderIndex();
            try {
                int len = buffer.readUnsignedShort();
                if (len >= 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();
        }

        // 2) 常规 VarInt 字符串
        buffer.markReaderIndex();
        try {
            String s = buffer.readString(32767);
            if (s != null && !s.isEmpty()) {
                return s;
            }
        } catch (Exception ignored) {
        }
        buffer.resetReaderIndex();

        return "";
    }

    private static String decodeOwlView(byte[] data) {
        if (data == null || data.length == 0)
            return "[空数据包]";

        // C->S 点击包兼容解码（避免按 S->C 结构解析时报错）
        String outboundClickDecoded = decodeOutboundButtonClickPayload(data);
        if (outboundClickDecoded != null) {
            return outboundClickDecoded;
        }

        PacketBuffer buffer = new PacketBuffer(Objects.requireNonNull(Unpooled.wrappedBuffer(data)));
        StringBuilder resultBuilder = new StringBuilder();
        int mainComponentId = 0;
        try {
            if (buffer.readableBytes() < 1)
                return "[数据包过短]";
            buffer.readByte(); // Skip first byte
            String category = buffer.readString(32767);

            if (buffer.readableBytes() >= 12) {
                buffer.markReaderIndex();
                buffer.skipBytes(8);
                int potentialId = buffer.readInt();
                if (potentialId < 0 || potentialId > 1000000) {
                    buffer.resetReaderIndex();
                } else {
                    buffer.resetReaderIndex();
                }
            }

            if (buffer.readableBytes() < 4)
                return "[数据包不完整，无法读取ComponentID]";
            int componentId = buffer.readInt();
            mainComponentId = componentId;
            resultBuilder.append("Category: ").append(category).append(" (ComponentID: ").append(componentId)
                    .append(")");

            if (buffer.readableBytes() > 1) {
                buffer.readByte();
                if (buffer.readableBytes() > 0) {
                    String operation = buffer.readString(32767);
                    resultBuilder.append(" -> [").append(operation).append("]");

                    if (operation.endsWith("_init") || operation.startsWith("Component_Create")) {
                        resultBuilder.append(" { ");

                        if (operation.startsWith("Component_Create")) {
                            if (buffer.readableBytes() < 4)
                                return resultBuilder.append(" ...} [数据不完整]").toString();

                            // 兼容多种Create头格式：
                            // A) 1字节前缀 + int parentId
                            // B) 2字节前缀 + int parentId（你提供的删除确认按钮包属于此格式）
                            // C) 无前缀，直接 int parentId
                            int parentId = -1;
                            String name = "";

                            buffer.markReaderIndex();
                            if (buffer.readableBytes() >= 6) {
                                buffer.readUnsignedShort();
                                parentId = buffer.readInt();
                                name = readFlexibleText(buffer);
                            }

                            if (name == null || name.isEmpty()) {
                                buffer.resetReaderIndex();
                                if (buffer.readableBytes() >= 5) {
                                    buffer.readUnsignedByte();
                                    parentId = buffer.readInt();
                                    name = readFlexibleText(buffer);
                                }
                            }

                            if (name == null || name.isEmpty()) {
                                buffer.resetReaderIndex();
                                if (buffer.readableBytes() >= 4) {
                                    parentId = buffer.readInt();
                                    name = readFlexibleText(buffer);
                                }
                            }

                            if (name == null) {
                                name = "";
                            }

                            resultBuilder.append("\n  ParentID: ").append(parentId).append(", \n  Name: \"")
                                    .append(name).append("\"; ");

                        }

                        if (operation.endsWith("_init")) {
                            appendParameters(resultBuilder, operation, buffer, componentId);
                            resultBuilder.append("; ");
                        }

                        while (buffer.readableBytes() > 0) {
                            if (buffer.readableBytes() < 1)
                                break;
                            String subOperation = readViewSubOperation(buffer);
                            if (subOperation == null || subOperation.isEmpty()) {
                                resultBuilder.append("\n  [空子操作，终止解析]");
                                break;
                            }
                            if ("Global_End".equals(subOperation)) {
                                resultBuilder.append("\n  [Global_End] \n}");
                                break;
                            }
                            resultBuilder.append("\n  [").append(subOperation).append("]:");
                            appendParameters(resultBuilder, subOperation, buffer, componentId);
                            resultBuilder.append("; ");
                        }
                    } else {
                        appendParameters(resultBuilder, operation, buffer, componentId);

                        // 容器操作兜底：某些操作本体后会继续携带一串子操作
                        // 例如：ComponentFollow / EntityAnimation_CreateAnimationCure
                        if (buffer.readableBytes() > 0
                                && ("ComponentFollow".equals(operation)
                                        || "EntityAnimation_CreateAnimationCure".equals(operation))) {
                            int nested = appendChainedSubOperations(resultBuilder, buffer, componentId);
                            if (nested > 0) {
                                resultBuilder.append(" [NESTED_OPS=").append(nested).append("]");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return "[解码S->C数据包时出错: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "]";
        }

        if (buffer.readableBytes() > 0) {
            resultBuilder.append(" -> [警告: 仍有 ").append(buffer.readableBytes()).append(" 字节未被解析]");
        }
        return resultBuilder.toString();
    }

    private static String decodeOutboundButtonClickPayload(byte[] raw) {
        if (raw == null || raw.length < 24) {
            return null;
        }
        int marker = indexOf(raw, "Button_click".getBytes(StandardCharsets.US_ASCII), 0);
        if (marker < 6) {
            return null;
        }
        if ((raw[marker - 1] & 0xFF) != 0x0C || (raw[marker - 2] & 0xFF) != 0x00) {
            return null;
        }

        int idPos = marker - 6;
        int clickedId = ((raw[idPos] & 0xFF) << 24)
                | ((raw[idPos + 1] & 0xFF) << 16)
                | ((raw[idPos + 2] & 0xFF) << 8)
                | (raw[idPos + 3] & 0xFF);

        return "Category: ComponentClick (ClickedComponentID: " + clickedId + ") -> [Button_click]";
    }

    private static void appendParameters(StringBuilder builder, String operation, PacketBuffer buffer,
            int mainComponentId) {
        try {
            switch (operation) {
                case "Gui_set_removeBaseComponent":
                    if (buffer.readableBytes() > 0) {
                        byte[] payload = new byte[buffer.readableBytes()];
                        buffer.readBytes(payload);
                        if (payload.length >= 4) {
                            int start = payload.length - 4;
                            int removedId = ((payload[start] & 0xFF) << 24)
                                    | ((payload[start + 1] & 0xFF) << 16)
                                    | ((payload[start + 2] & 0xFF) << 8)
                                    | (payload[start + 3] & 0xFF);
                            builder.append(" RemovedComponentID: ").append(removedId);
                        }
                    }
                    break;

                case "Button_set_isBan":
                case "Gui_set_GuiHighlight":
                case "Gui_set_TopOperation":
                case "Label_set_shadow":
                case "LabelArea_set_centerDisplay":
                    if (buffer.readableBytes() >= 3)
                        builder.append(" ").append(buffer.readBoolean()).append(", ").append(buffer.readBoolean())
                                .append(", ").append(buffer.readBoolean());
                    break;

                case "Gui_set_Hide":
                    if (buffer.readableBytes() >= 3) {
                        boolean hideP1 = buffer.readBoolean();
                        boolean hideP2 = buffer.readBoolean();
                        boolean hideP3 = buffer.readBoolean();
                        builder.append(" ").append(hideP1).append(", ").append(hideP2)
                                .append(", ").append(hideP3);
                    }
                    break;

                case "Component_set_hide":
                    if (buffer.readableBytes() >= 2) {
                        boolean hideParam1 = buffer.readBoolean();
                        boolean hideParam2 = buffer.readBoolean();
                        builder.append(" ").append(hideParam1).append(", ").append(hideParam2);

                    }
                    break;

                case "ViewGui_set_open":
                    if (buffer.readableBytes() >= 2) {
                        boolean openFlag = buffer.readBoolean();
                        boolean extraFlag = buffer.readBoolean();
                        builder.append(" ").append(openFlag).append(", ").append(extraFlag);
                    }
                    break;

                case "Button_set_defOperationHandler_HideGui":
                    if (buffer.readableBytes() >= 2)
                        builder.append(" ").append(buffer.readBoolean()).append(", ").append(buffer.readBoolean());
                    break;

                // !! 核心修复 3: 解耦 Button_set_callBackSuccess 的逻辑 !!
                case "Button_set_callBackSuccess":
                    if (buffer.readableBytes() >= 1) {
                        boolean param = buffer.readBoolean();
                        builder.append(" ").append(param);
                    }
                    break;
                // !! 修复结束 !!

                case "ViewGui_set_hide":
                case "SlotCustom_set_clearItem":
                case "Image_initSizeWithDefImg":
                case "Button_initSizeWithDefImg":
                case "ComponentFollow_set_FollowComponentDeath":
                case "Button_set_isWaitSuccessCallBack":
                case "Label_init":
                case "LabelArea_init":
                case "Button_init":
                case "Image_init":
                case "EntityAnimationCure_init":
                    if (buffer.readableBytes() >= 1) {
                        builder.append(" ").append(buffer.readBoolean());
                        // 兼容某些包结尾带 0x00 占位字节，避免残留 1 字节告警
                        if ("Button_initSizeWithDefImg".equals(operation)
                                && buffer.readableBytes() == 1
                                && buffer.getByte(buffer.readerIndex()) == 0x00) {
                            buffer.readByte();
                        }
                    }
                    break;

                case "View_CreateViewGui":
                case "Gui_set_create":
                case "ComponentFollow_set_FollowComponentType":
                    if (buffer.readableBytes() > 0) {
                        String viewName = readFlexibleText(buffer);
                        builder.append(" \"").append(viewName).append("\"");
                    }
                    break;

                case "ViewBackpack_set_addSlotIndex":
                case "Slot_set_indexSlot":
                    if (buffer.readableBytes() >= 4) {
                        int rawSlotIndex = buffer.readInt();
                        builder.append(" ").append(rawSlotIndex);
                    }
                    break;

                case "EntityImage_set_savePath":
                    if (buffer.readableBytes() > 0) {
                        String savePath = readFlexibleText(buffer);
                        builder.append(" \"").append(savePath).append("\"");
                    }
                    break;

                case "Component_set_coordinateType":
                    if (buffer.readableBytes() > 0) {
                        String coordinateType = readFlexibleText(buffer);
                        builder.append(" \"").append(coordinateType).append("\"");
                    }
                    break;

                case "Label_set_text":
                case "LabelArea_set_text":
                case "TextArea_set_text":
                    if (buffer.readableBytes() > 0) {
                        String text = readLabelTextPayload(buffer);
                        builder.append(" \"").append(text).append("\"");
                    }
                    break;

                case "LabelArea_set_defBackGround":
                case "LabelArea_set_defBackground":
                    // 该子操作常见为无参数，仅用于切换默认背景。
                    // 显式处理避免落入 default 后把后续链式子操作全部吞掉。
                    break;

                case "EntityAnimation_set_runValue":
                    String runValueKey = readLengthPrefixedString(buffer);
                    String runValueText = readFlexibleText(buffer);
                    if (runValueKey != null && !runValueKey.isEmpty()) {
                        builder.append(" {").append(runValueKey).append(": \"").append(runValueText).append("\"}");
                    } else if (runValueText != null && !runValueText.isEmpty()) {
                        builder.append(" \"").append(runValueText).append("\"");
                    }
                    break;

                case "View_set_removeGui":
                case "Button_set_label":
                case "ComponentFollow_set_FollowComponent":
                case "Component_set_width":
                case "Component_set_height":
                    if (buffer.readableBytes() >= 4)
                        builder.append(" ").append(buffer.readInt());
                    break;

                case "Image_set_defImg":
                case "Image_set_mouseInImg":
                case "Button_set_defImg":
                case "Button_set_mouseInImg":
                case "Button_set_mouseClickImg":
                case "Button_set_mouseBanImg":
                    if (buffer.readableBytes() >= 1)
                        buffer.readBoolean();
                    if (buffer.readableBytes() >= 4)
                        builder.append(" ID ").append(buffer.readInt());
                    break;

                case "Component_set_x":
                case "Component_set_y":
                case "Roll_set_rollOffSetPercentage":
                    if (buffer.readableBytes() >= 8)
                        builder.append(" ").append(buffer.readDouble());
                    break;

                case "Component_set_alpha":
                    if (buffer.readableBytes() >= 4)
                        builder.append(" ").append(buffer.readFloat());
                    break;

                case "RollAreaList_set_addComponent":
                    // 新格式：count(1) + id(4) + text(ushort-len)
                    buffer.markReaderIndex();
                    boolean parsedAddComponent = false;
                    if (buffer.readableBytes() >= 6) {
                        int count = buffer.readUnsignedByte();
                        int id = buffer.readInt();
                        String text = readFlexibleText(buffer);
                        if (text != null && !text.isEmpty()) {
                            builder.append(" count=").append(count)
                                    .append(", ID ").append(id)
                                    .append(" -> \"").append(text).append("\"");
                            parsedAddComponent = true;
                        }
                    }

                    if (!parsedAddComponent) {
                        buffer.resetReaderIndex();
                        if (buffer.readableBytes() >= 4) {
                            int parentId = buffer.readInt();
                            String name = readFlexibleText(buffer);
                            if (buffer.readableBytes() >= 4) {
                                int newComponentId = buffer.readInt();
                                builder.append(" ParentID: ").append(parentId).append(", Name: \"").append(name)
                                        .append("\", NewID: ").append(newComponentId);
                            }
                        }
                    }
                    break;

                case "RollAreaList_set_clearComponent":
                    // 样例格式：
                    // 01 + int(componentId) + "99"(ASCII)
                    // 也兼容：仅 bool、bool+int
                    if (buffer.readableBytes() >= 1) {
                        boolean clearAll = buffer.readBoolean();
                        builder.append(" clearAll=").append(clearAll);
                    }
                    if (buffer.readableBytes() >= 4) {
                        int clearComponentId = buffer.readInt();
                        builder.append(", clearComponentId=").append(clearComponentId);
                    }
                    if (buffer.readableBytes() > 0) {
                        String tailText = readFlexibleText(buffer);
                        if (tailText != null && !tailText.isEmpty()) {
                            builder.append(", tag=\"").append(tailText).append("\"");
                        } else if (buffer.readableBytes() > 0) {
                            builder.append(", tailHex=")
                                    .append(ByteBufUtil.hexDump(buffer.readBytes(buffer.readableBytes())));
                        }
                    }
                    break;

                case "EntityHoverListString_set_list":
                    if (buffer.readableBytes() >= 6) {
                        int listType = buffer.readUnsignedByte();
                        int startIndex = buffer.readInt();
                        int listCount = buffer.readUnsignedByte();
                        builder.append(" type=").append(listType)
                                .append(", start=").append(startIndex)
                                .append(", count=").append(listCount);
                        for (int i = 0; i < listCount && buffer.readableBytes() > 0; i++) {
                            String text = readFlexibleText(buffer);
                            if (text == null || text.isEmpty()) {
                                break;
                            }
                            builder.append(" [").append(i).append("]=\"").append(text).append("\"");
                        }
                    }
                    break;

                case "ComponentFollow":
                    appendComponentFollowPayload(builder, buffer, mainComponentId);
                    break;

                case "EntityAnimation_CreateAnimationCure":
                    appendEntityAnimationCreatePayload(builder, buffer, mainComponentId);
                    break;

                case "EntityAnimation_set_currentValueStart":
                    if (buffer.readableBytes() == 1) {
                        builder.append(" ").append(buffer.readUnsignedByte());
                    } else if (buffer.readableBytes() == 2) {
                        builder.append(" ").append(buffer.readUnsignedShort());
                    } else if (buffer.readableBytes() == 3) {
                        int v = ((buffer.readUnsignedByte() & 0xFF) << 16)
                                | ((buffer.readUnsignedByte() & 0xFF) << 8)
                                | (buffer.readUnsignedByte() & 0xFF);
                        builder.append(" ").append(v);
                    } else if (buffer.readableBytes() >= 4) {
                        builder.append(" ").append(buffer.readInt());
                    }
                    break;

                case "SlotCustom_set_item":
                    if (buffer.readableBytes() >= 9) {
                        buffer.readBoolean();
                        buffer.readInt();
                        buffer.readInt();
                        builder.append(" [ItemStack Data]");
                        try {
                            buffer.readItemStack();
                        } catch (IOException | RuntimeException e) {
                            buffer.readerIndex(buffer.writerIndex());
                        }
                    }
                    break;

                case "RollAutoBar_set_addLabelInfo":
                    if (buffer.readableBytes() >= 5) {
                        buffer.readBoolean();
                        int key = buffer.readInt();
                        String text = buffer.readString(32767);
                        builder.append(" key: ").append(key).append(", text: \"").append(text).append("\"");
                    }
                    break;

                default:
                    if (buffer.readableBytes() > 0) {
                        builder.append(" (未知参数, 剩余HEX: ")
                                .append(ByteBufUtil.hexDump(buffer.readBytes(buffer.readableBytes()))).append(")");
                    }
                    break;
            }
        } catch (Exception e) {
            builder.append(" [解码参数时出错: ").append(e.getClass().getSimpleName()).append("]");
            zszlScriptMod.LOGGER.error("解码操作 '{}' 的参数时出错", operation, e);
        }
    }

    private static String readLengthPrefixedString(PacketBuffer buffer) {
        if (buffer == null || buffer.readableBytes() < 4) {
            return "";
        }

        buffer.markReaderIndex();
        try {
            int len = buffer.readInt();
            if (len < 0 || len > buffer.readableBytes()) {
                buffer.resetReaderIndex();
                return "";
            }
            byte[] bytes = new byte[len];
            buffer.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            buffer.resetReaderIndex();
            return "";
        }
    }

    private static String readFlexibleText(PacketBuffer buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return "";
        }

        // 1) 常规 Minecraft VarInt 字符串
        buffer.markReaderIndex();
        try {
            String text = buffer.readString(32767);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        } catch (Exception ignored) {
            // fallback
        }
        buffer.resetReaderIndex();

        // 2) Owl 常见格式：unsigned short + utf8 bytes
        if (buffer.readableBytes() >= 2) {
            buffer.markReaderIndex();
            try {
                int len = buffer.readUnsignedShort();
                if (len >= 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // fallback
            }
            buffer.resetReaderIndex();
        }

        // 3) 兜底：int + utf8 bytes
        if (buffer.readableBytes() >= 4) {
            buffer.markReaderIndex();
            try {
                int len = buffer.readInt();
                if (len >= 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // fallback
            }
            buffer.resetReaderIndex();
        }

        return "";
    }

    private static String readViewSubOperation(PacketBuffer buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return "";
        }

        // 0) int 长度 + UTF-8 bytes（例如 00 00 00 27 + ComponentFollow_set_xxx）
        if (buffer.readableBytes() >= 4) {
            buffer.markReaderIndex();
            try {
                int len = buffer.readInt();
                if (len > 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();
        }

        // 1) OwlView 常见格式：unsigned short + UTF-8 bytes（例如 00 0F + Component_set_x）
        if (buffer.readableBytes() >= 2) {
            buffer.markReaderIndex();
            try {
                int len = buffer.readUnsignedShort();
                if (len > 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();
        }

        // 2) 兼容历史 VarInt 字符串
        buffer.markReaderIndex();
        try {
            String value = buffer.readString(32767);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        } catch (Exception ignored) {
        }
        buffer.resetReaderIndex();

        // 3) 兜底：部分包可能有 1 字节前缀，跳过后再按 ushort / VarInt 解析
        if (buffer.readableBytes() >= 3) {
            buffer.markReaderIndex();
            try {
                buffer.readByte();
                int len = buffer.readUnsignedShort();
                if (len > 0 && len <= buffer.readableBytes()) {
                    byte[] bytes = new byte[len];
                    buffer.readBytes(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();

            buffer.markReaderIndex();
            try {
                buffer.readByte();
                String value = buffer.readString(32767);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();
        }

        return "";
    }

    private static int appendChainedSubOperations(StringBuilder builder, PacketBuffer buffer, int mainComponentId) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return 0;
        }

        int count = 0;
        while (buffer.readableBytes() > 0) {
            buffer.markReaderIndex();
            String subOperation = readViewSubOperation(buffer);
            if (subOperation == null || subOperation.isEmpty()) {
                buffer.resetReaderIndex();
                break;
            }

            if ("Global_End".equals(subOperation)) {
                builder.append(" -> [Global_End]");
                count++;
                break;
            }

            builder.append(" -> [").append(subOperation).append("]");
            appendParameters(builder, subOperation, buffer, mainComponentId);
            count++;
        }

        return count;
    }

    private static void appendComponentFollowPayload(StringBuilder builder, PacketBuffer buffer, int mainComponentId) {
        int parsed = appendChainedSubOperations(builder, buffer, mainComponentId);
        if (parsed <= 0 && buffer.readableBytes() > 0) {
            builder.append(" (未知参数, 剩余HEX: ")
                    .append(ByteBufUtil.hexDump(buffer.readBytes(buffer.readableBytes()))).append(")");
        }
    }

    private static void appendEntityAnimationCreatePayload(StringBuilder builder, PacketBuffer buffer,
            int mainComponentId) {
        // 兼容样例：00 00 00 00 00 80 00 19 <name> + 后续一串 EntityAnimation_set_xxx
        buffer.markReaderIndex();
        if (buffer.readableBytes() >= 6) {
            int seed = buffer.readInt();
            int type = buffer.readUnsignedShort();
            builder.append(" seed=").append(seed).append(", type=").append(type);
            String name = readFlexibleText(buffer);
            if (name != null && !name.isEmpty()) {
                builder.append(", name=\"").append(name).append("\"");
            } else {
                buffer.resetReaderIndex();
            }
        }

        int parsed = appendChainedSubOperations(builder, buffer, mainComponentId);
        if (parsed <= 0 && buffer.readableBytes() > 0) {
            builder.append(" (未知参数, 剩余HEX: ")
                    .append(ByteBufUtil.hexDump(buffer.readBytes(buffer.readableBytes()))).append(")");
        }
    }

    private static String readLabelTextPayload(PacketBuffer buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return "";
        }

        // 0) OwlView 标签文本特殊格式：
        // [flag(1)] 00 00 [len(1)] [utf8 bytes...]
        // 或 [00 00 len(1)] [utf8 bytes...]
        // 例如：01 00 00 01 30 => "0"
        buffer.markReaderIndex();
        try {
            byte[] remaining = new byte[buffer.readableBytes()];
            buffer.readBytes(remaining);
            String special = decodeSpecialLabelTextPayload(remaining);
            if (special != null && !special.isEmpty()) {
                return special;
            }
        } catch (Exception ignored) {
        }
        buffer.resetReaderIndex();

        // 1) 直接是文本（无布尔前缀）
        buffer.markReaderIndex();
        String direct = readFlexibleText(buffer);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        buffer.resetReaderIndex();

        // 2) 兼容布尔前缀 + 文本
        if (buffer.readableBytes() >= 1) {
            buffer.markReaderIndex();
            try {
                buffer.readBoolean();
                String withFlag = readFlexibleText(buffer);
                if (withFlag != null && !withFlag.isEmpty()) {
                    return withFlag;
                }
            } catch (Exception ignored) {
            }
            buffer.resetReaderIndex();
        }

        // 3) 仍失败时返回 direct（可能是空串）
        return direct == null ? "" : direct;
    }

    private static String decodeSpecialLabelTextPayload(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return "";
        }

        // [flag] 00 00 [len] [text...]
        if (payload.length >= 5 && (payload[0] == 0x00 || payload[0] == 0x01)
                && payload[1] == 0x00 && payload[2] == 0x00) {
            int len = payload[3] & 0xFF;
            int start = 4;
            if (len >= 0 && start + len <= payload.length) {
                return new String(payload, start, len, StandardCharsets.UTF_8);
            }
        }

        // 00 00 [len] [text...]
        if (payload[0] == 0x00 && payload[1] == 0x00) {
            int len = payload[2] & 0xFF;
            int start = 3;
            if (len >= 0 && start + len <= payload.length) {
                return new String(payload, start, len, StandardCharsets.UTF_8);
            }
        }

        return "";
    }

    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        if (source == null || target == null || target.length == 0) {
            return -1;
        }
        for (int i = Math.max(0, fromIndex); i <= source.length - target.length; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }
}
