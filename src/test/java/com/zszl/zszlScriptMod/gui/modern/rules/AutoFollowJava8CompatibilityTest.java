package com.zszl.zszlScriptMod.gui.modern.rules;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.*;

public class AutoFollowJava8CompatibilityTest {
    @Test public void compiledPickerDoesNotReferenceJava9CovariantBufferMethods() throws Exception {
        if (Boolean.getBoolean("autofollow.test.requireJava8")) {
            assertEquals("1.8", System.getProperty("java.specification.version"));
            System.out.println("Verified runtime: " + System.getProperty("java.runtime.version"));
        }
        Set<String> bufferMethods = new HashSet<>(Arrays.asList("clear", "flip", "rewind", "mark", "reset", "position", "limit"));
        int[] clearCalls = {0};
        for (String name : Arrays.asList("AutoFollowAreaPicker", "AutoFollowPickProjection", "AutoFollowPickProjectionTest")) {
            String resource = "com/zszl/zszlScriptMod/gui/modern/rules/" + name + ".class";
            try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(resource, bytes);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                    String desc, boolean isInterface) {
                                if (owner.startsWith("java/nio/") && owner.endsWith("Buffer")
                                        && bufferMethods.contains(called) && desc.contains(")Ljava/nio/")) {
                                    assertTrue(name + "." + method + " invokes " + owner + "." + called + desc,
                                            desc.endsWith(")Ljava/nio/Buffer;"));
                                    if (name.equals("AutoFollowAreaPicker") && called.equals("clear")) clearCalls[0]++;
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertEquals("The picker must reset all five NIO buffers", 5, clearCalls[0]);
    }
}
