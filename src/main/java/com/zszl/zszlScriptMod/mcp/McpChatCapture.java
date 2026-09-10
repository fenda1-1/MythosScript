package com.zszl.zszlScriptMod.mcp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

/** Copies text immediately; retained messages never keep mutable Minecraft components. */
public final class McpChatCapture {
    private McpChatCapture() {}
    public static void capture(String stream, String type, ITextComponent component, int lineId) {
        if (component == null) return;
        String plain = TextFormatting.getTextWithoutFormattingCodes(component.getUnformattedText());
        String json;
        try { json = ITextComponent.Serializer.componentToJson(component); }
        catch (RuntimeException e) { json = McpJson.object("text", plain).toString(); }
        ServerData data = Minecraft.getMinecraft().getCurrentServerData();
        McpChatHistory.INSTANCE.append(stream, type, plain, component.getFormattedText(), json, lineId,
                data == null ? "" : data.serverIP);
    }
}
