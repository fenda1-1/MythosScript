// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/ChatEventHandler.java
package com.zszl.zszlScriptMod.handlers;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.config.ChatOptimizationConfig;
import com.zszl.zszlScriptMod.gui.CustomGuiNewChat;
import com.zszl.zszlScriptMod.path.node.NodeTriggerManager;
import com.zszl.zszlScriptMod.path.trigger.LegacySequenceTriggerManager;
import com.zszl.zszlScriptMod.utils.ReflectionCompat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zszl.zszlScriptMod.shadowbaritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.config.DebugLogManager;

public class ChatEventHandler {

    private static final Pattern PLAYER_NAME_IN_HOVER = Pattern.compile("玩家名称\\]\\s*([A-Za-z0-9_\\p{IsHan}]{2,20})");
    private static final Pattern PLAUSIBLE_PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_\\p{IsHan}]{2,20}$");
    private static final Pattern COPY_COMMAND_PATTERN = Pattern.compile("^/copy\\s+([A-Za-z0-9_\\p{IsHan}]{2,20})$",
            Pattern.CASE_INSENSITIVE);

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientChat(ClientChatEvent event) {
        if (event == null || event.getMessage() == null) {
            return;
        }
        String raw = event.getMessage().trim();
        if (!raw.startsWith("!") && !raw.startsWith(FORCE_COMMAND_PREFIX)) {
            return;
        }
        boolean executed = InternalBaritoneBridge.executeRawChatLikeCommand(raw);
        event.setCanceled(true);
        if (!executed) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString("§c[Baritone] 命令未执行: §f" + raw));
            }
        }
    }

    public static void triggerDisplayedChatMessage(ITextComponent originalComponent, ITextComponent displayedComponent,
            int chatLineId) {
        if (ModConfig.isInternalChatSuppressed()) {
            return;
        }
        String rawMessage = normalizeTriggerMessage(originalComponent == null ? null : originalComponent.getUnformattedText());
        String displayedMessage = normalizeTriggerMessage(
                displayedComponent == null ? null : displayedComponent.getUnformattedText());
        if (rawMessage.isEmpty() && displayedMessage.isEmpty()) {
            return;
        }
        String rawDetectionText = originalComponent == null ? rawMessage : safe(originalComponent.getFormattedText());
        String displayedDetectionText = displayedComponent == null ? displayedMessage
                : safe(displayedComponent.getFormattedText());
        if (ModConfig.isInternalDebugChatMessage(rawDetectionText)
                || ModConfig.isInternalDebugChatMessage(displayedDetectionText)) {
            return;
        }

        // A number of mature handlers still emit their debug line directly
        // to the client chat instead of through ModConfig.debugPrint(). Feed
        // those legacy lines into the bounded terminal buffer as well.
        DebugLogManager.appendChatDebug(displayedMessage.isEmpty() ? rawMessage : displayedMessage);

        JsonObject triggerData = new JsonObject();
        triggerData.addProperty("message", rawMessage);
        triggerData.addProperty("displayedMessage", displayedMessage);
        triggerData.addProperty("formatted", displayedComponent == null ? "" : safe(displayedComponent.getFormattedText()));
        triggerData.addProperty("source", chatLineId == 0 ? "chat" : "system");
        triggerData.addProperty("chatLineId", chatLineId);
        NodeTriggerManager.trigger(NodeTriggerManager.TRIGGER_CHAT, triggerData);
        LegacySequenceTriggerManager.triggerEvent(LegacySequenceTriggerManager.TRIGGER_CHAT, triggerData);
    }

    private static String normalizeTriggerMessage(String text) {
        return text == null ? "" : text.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatKeyboardPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiChat) || !Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        if (key != Keyboard.KEY_RETURN && key != Keyboard.KEY_NUMPADENTER) {
            return;
        }
        GuiTextField inputField;
        try {
            inputField = ReflectionCompat.getPrivateValue(GuiChat.class, (GuiChat) event.getGui(),
                    "field_146415_a");
        } catch (Exception ignored) {
            return;
        }
        if (inputField == null) {
            return;
        }
        String raw = inputField.getText();
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("!")) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        boolean executed = InternalBaritoneBridge.executeRawChatLikeCommand(trimmed);
        event.setCanceled(true);

        if (mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
            mc.ingameGUI.getChatGUI().addToSentMessages(raw);
        }
        mc.displayGuiScreen(null);

        if (!executed && mc.player != null) {
            mc.player.sendMessage(new TextComponentString("§c[Baritone] 命令未执行: §f" + trimmed));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!ChatOptimizationConfig.INSTANCE.enableSmartCopy || !(event.getGui() instanceof GuiChat)) {
            return;
        }

        int button = Mouse.getEventButton();
        if ((button == 0 || button == 1) && Mouse.getEventButtonState()) {
            Minecraft mc = Minecraft.getMinecraft();

            if (!(mc.ingameGUI.getChatGUI() instanceof CustomGuiNewChat)) {
                return;
            }
            CustomGuiNewChat customChatGui = (CustomGuiNewChat) mc.ingameGUI.getChatGUI();

            ScaledResolution scaledResolution = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * scaledResolution.getScaledWidth() / mc.displayWidth;
            int mouseY = scaledResolution.getScaledHeight()
                    - Mouse.getY() * scaledResolution.getScaledHeight() / mc.displayHeight - 1;

            ITextComponent visualLineComponent = customChatGui.getChatComponent(mouseX, mouseY);
            if (visualLineComponent == null) {
                return;
            }

            String command = buildCopyCommand(visualLineComponent);
            if (command != null) {
                ClientCommandHandler.instance.executeCommand(mc.player, command);
                event.setCanceled(true);
            }
        }
    }

    private String buildCopyCommand(ITextComponent clickedComponent) {
        if (clickedComponent == null || clickedComponent.getStyle() == null) {
            return null;
        }

        ClickEvent click = clickedComponent.getStyle().getClickEvent();
        if (click == null) {
            return null;
        }

        if (click.getAction() != ClickEvent.Action.RUN_COMMAND
                && click.getAction() != ClickEvent.Action.SUGGEST_COMMAND) {
            return null;
        }

        String command = click.getValue() == null ? "" : click.getValue().trim();
        Matcher cmdMatcher = COPY_COMMAND_PATTERN.matcher(command);
        if (!cmdMatcher.matches()) {
            String hoverName = extractPlayerNameFromHover(clickedComponent);
            if (hoverName != null) {
                return "/copy " + hoverName;
            }
            return null;
        }
        String fromCmd = normalizePossiblePlayerName(cmdMatcher.group(1));
        if (fromCmd == null) {
            return null;
        }
        return "/copy " + fromCmd;
    }

    private String extractPlayerNameFromHover(ITextComponent clickedComponent) {
        HoverEvent hover = clickedComponent.getStyle().getHoverEvent();
        if (hover == null || hover.getValue() == null) {
            return null;
        }
        String hoverText = hover.getValue().getUnformattedText();
        if (hoverText == null || hoverText.isEmpty()) {
            return null;
        }
        Matcher matcher = PLAYER_NAME_IN_HOVER.matcher(hoverText);
        if (!matcher.find()) {
            return null;
        }
        return normalizePossiblePlayerName(matcher.group(1));
    }

    private String normalizePossiblePlayerName(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        text = text.replace("§", "");
        text = text.replace("[", "").replace("]", "").replace(":", "");
        text = text.replace(" ", "").trim();
        if (PLAUSIBLE_PLAYER_NAME.matcher(text).matches()) {
            return text;
        }
        return null;
    }
}

