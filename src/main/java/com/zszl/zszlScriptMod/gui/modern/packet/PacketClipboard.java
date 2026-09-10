package com.zszl.zszlScriptMod.gui.modern.packet;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.Minecraft;

/** Clipboard bridge kept isolated so packet panels do not depend on a screen. */
final class PacketClipboard {
    private PacketClipboard() { }
    static void copy(String value) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value == null ? "" : value), null); }
        catch (Throwable ignored) { }
    }

    /** Copies ordinary text, exporting oversized clipboard payloads like the legacy viewer. */
    static String copyOrExport(Minecraft minecraft, String value) {
        String text = value == null ? "" : value;
        if (text.length() <= 32767 || minecraft == null || minecraft.mcDataDir == null) {
            copy(text);
            return "";
        }
        try {
            Path directory = minecraft.mcDataDir.toPath().resolve("zszl_script").resolve("packet_exports");
            Files.createDirectories(directory);
            String name = "packet-copy-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date()) + ".txt";
            Path file = directory.resolve(name);
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            copy(file.toAbsolutePath().toString());
            return file.toAbsolutePath().toString();
        } catch (Throwable ignored) {
            copy(text);
            return "";
        }
    }
}
