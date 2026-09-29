package com.mythos.mythosScriptMod.mcp;

import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import org.junit.Assume;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

/** Checks the actual third-party bytes without loading Minecraft, invoking scripts or networking. */
public class DragonCoreContractTest {
    private boolean method(ZipFile jar,String cls,String name,String descriptor) throws Exception {
        boolean[] found={false};
        try(InputStream in=jar.getInputStream(jar.getEntry("eos/moe/dragoncore/"+cls+".class"))) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access,String n,String d,String signature,String[] exceptions) {
                    if(name.equals(n)&&descriptor.equals(d))found[0]=true;return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return found[0];
    }
    @Test public void actualDragonCoreSupportsAllInjectedAndReflectedEntryPoints() throws Exception {
        String path=System.getProperty("dragoncore.jar");Assume.assumeNotNull(path);
        try(ZipFile jar=new ZipFile(path)) {
            assertTrue(method(jar,"sl","func_73863_a","(IIF)V"));
            assertTrue(method(jar,"sl","func_73864_a","(III)V"));
            assertTrue(method(jar,"sl","func_146286_b","(III)V"));
            assertTrue(method(jar,"sl","func_146273_a","(IIIJ)V"));
            assertTrue(method(jar,"sl","runWheel","()Z"));
            for(String cls:new String[]{"gd","wd","oj","ie","kf","nh","cn"})assertTrue(cls,method(jar,cls,"render","(II)V"));
            assertTrue(method(jar,"pz","func_110591_a","(Ljava/lang/String;)Ljava/io/InputStream;"));
            assertTrue(method(jar,"oj","writeText","(Ljava/lang/String;)V"));
            assertTrue(method(jar,"ie","keyTyped1","(CI)V"));
            assertTrue(method(jar,"ie","isActive","()Z"));
            assertTrue(method(jar,"kn","m","(Leos/moe/dragoncore/wd;)Ljava/util/List;"));
            assertTrue(method(jar,"vv","func_110591_a","(Ljava/lang/String;)Ljava/io/InputStream;"));
            assertTrue(method(jar,"dh","u","(Ljava/lang/String;)V"));
            assertTrue(method(jar,"dh","j","(Ljava/lang/String;)V"));
            assertTrue(method(jar,"jn","getValue","(Ljava/lang/String;)Ljava/lang/Object;"));
        }
    }
}
