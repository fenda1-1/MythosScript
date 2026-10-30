package com.mythos.mythosScriptMod.system.command;

import com.mythos.mythosScriptMod.utils.ClientTranslationInjector;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class ReloadLangCommand extends CommandBase {

    @Override
    public String getName() {
        return "reloadlang";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/reloadlang [en_us|zh_cn]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String lang = args == null || args.length < 1 || args[0] == null || args[0].trim().isEmpty()
                ? null : args[0].trim();
        Minecraft mc = Minecraft.getMinecraft();
        String current = mc != null && mc.gameSettings != null && mc.gameSettings.language != null
                ? mc.gameSettings.language : "en_us";
        boolean ok;
        if (lang == null) {
            // Reload every supported locale so a future language switch is fresh.
            ok = ClientTranslationInjector.INSTANCE.reloadLocale("en_us")
                    & ClientTranslationInjector.INSTANCE.reloadLocale("zh_cn");
            ClientTranslationInjector.INSTANCE.reloadLocale(current.toLowerCase(java.util.Locale.ROOT));
        } else {
            ok = ClientTranslationInjector.INSTANCE.reloadLocale(lang);
        }
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    (ok ? TextFormatting.GREEN : TextFormatting.RED)
                            + "[i18n] reloaded module lang: " + (lang == null ? "all" : lang)));
        }
    }
}
