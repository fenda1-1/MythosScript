package com.zszl.zszlScriptMod.system.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.regex.Pattern;

public class CopyNameCommand extends CommandBase {

    // 支持英文/数字/下划线/中文名（中日韩统一表意文字）
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\p{IsHan}]{2,20}$");

    @Override
    public String getName() {
        return "copy";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/copy <name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args == null || args.length < 1) {
            throw new CommandException("用法: /copy <name>");
        }
        String name = args[0] == null ? "" : args[0].trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new CommandException("名称格式无效，仅支持字母/数字/下划线/中文，长度2-20");
        }
        copyToClipboard(name, true);
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static void copyToClipboard(String name, boolean notify) {
        if (!isValidPlayerName(name)) {
            return;
        }
        GuiScreen.setClipboardString(name);
        if (notify) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "已复制玩家名称: " + name));
            }
        }
    }
}
