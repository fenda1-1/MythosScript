package com.mythos.mythosScriptMod.system;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Opt-in compatibility for clients whose original resource password used a legacy charset. */
public final class DragonResourceEncoding {
    private DragonResourceEncoding() {}
    public static byte[] encode(String password) {
        String charset=System.getProperty("mythosscript.dragoncore.resourceCharset","");
        if(charset.isEmpty()) {
            Path config=Minecraft.getMinecraft().mcDataDir.toPath().resolve("config/mythosscript-dragoncore.properties");
            if(Files.isRegularFile(config)) {
                Properties p=new Properties();
                try(InputStream in=Files.newInputStream(config)) {p.load(in);charset=p.getProperty("resourceCharset","");}
                catch(java.io.IOException e) {throw new IllegalStateException("Cannot read DragonCore charset configuration",e);}
            }
        }
        return charset.trim().isEmpty()?password.getBytes():password.getBytes(Charset.forName(charset.trim()));
    }
}
