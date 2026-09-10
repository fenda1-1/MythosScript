package com.zszl.zszlScriptMod.system.command;

import com.zszl.zszlScriptMod.handlers.InternalBaritoneBridge;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class BaritoneChatCommand extends CommandBase {

    @Override
    public String getName() {
        return "!";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "! <baritone command>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args == null || args.length == 0) {
            return;
        }
        String joined = String.join(" ", args).trim();
        if (!joined.isEmpty()) {
            InternalBaritoneBridge.executeRawChatLikeCommand("!" + joined);
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
