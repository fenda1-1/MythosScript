package dev.mythos.inject.runtime;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** No fields or methods are added: safe for classes already loaded before attach. */
public final class FeatureTransformer1201 implements ClassFileTransformer {
    private static int api() {
        for (String v : new String[]{"ASM9","ASM8","ASM7","ASM6","ASM5"})
            try { return Opcodes.class.getField(v).getInt(null); } catch (Exception ignored) {}
        return ASM5;
    }
    private static final String BRIDGE = "dev/mythos/inject/RuntimeBridge";
    public static boolean targets(String name) {
        return name.equals("net.minecraft.client.renderer.LightTexture")
                || name.equals("net.minecraft.client.renderer.chunk.RenderChunkRegion")
                || name.equals("net.minecraft.client.renderer.chunk.RenderSectionRegion")
                || name.equals("net.minecraft.client.multiplayer.ClientPacketListener")
                || name.equals("net.minecraft.client.renderer.LevelRenderer")
                || name.equals("net.minecraft.client.gui.Gui")
                || name.equals("net.minecraft.client.Timer")
                || name.equals("net.minecraft.client.DeltaTracker$Timer");
    }
    private static boolean named(String value, String a, String b) { return value.equals(a) || value.equals(b); }
    private static boolean named(String value, String a, String b, String c) {
        return value.equals(a) || value.equals(b) || value.equals(c);
    }
    @Override public byte[] transform(ClassLoader loader, String name, Class<?> redef,
            ProtectionDomain domain, byte[] bytes) {
        if (name == null || !targets(name.replace('/', '.'))) return null;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(api(), writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] ex) {
                MethodVisitor next = super.visitMethod(access, method, desc, sig, ex);
                return new MethodVisitor(api(), next) {
                    int optionIndex;
                    int optionGets;
                    int doubleFloats;
                    @Override public void visitInsn(int opcode) {
                        if (opcode == IRETURN && (name.endsWith("/DeltaTracker$Timer") || name.endsWith("/client/Timer"))
                                && named(method, "advanceTime", "m_339365_")) {
                            super.visitVarInsn(ISTORE, 4);
                            super.visitVarInsn(ALOAD, 0);
                            super.visitVarInsn(ILOAD, 4);
                            super.visitVarInsn(ILOAD, 3);
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "timer", "(Ljava/lang/Object;IZ)I", false);
                        }
                        if (opcode == RETURN && name.endsWith("/gui/Gui")
                                && named(method, "render", "m_280421_")) {
                            super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "renderHud", "(Ljava/lang/Object;)V", false);
                        }
                        if (opcode == RETURN && name.endsWith("/LevelRenderer")
                                && named(method, "renderLevel", "renderLevel")) {
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "renderWorld", "()V", false);
                        }
                        if (opcode == ARETURN && name.endsWith("/LevelRenderer")
                                && named(method, "m_440595_", "m_440595_")
                                && desc.equals("()Lnet/minecraft/gizmos/Gizmos$TemporaryCollection;")) {
                            // withCollector has installed the thread-local collector and
                            // the returned TemporaryCollection keeps it active.
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "renderWorld", "()V", false);
                        }
                        if (opcode == ARETURN && (name.endsWith("/RenderChunkRegion") || name.endsWith("/RenderSectionRegion"))
                                && named(method, "getBlockState", "m_8055_")) {
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "renderState", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                            super.visitTypeInsn(CHECKCAST, "net/minecraft/world/level/block/state/BlockState");
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String call, String descriptor, boolean itf) {
                        if (name.endsWith("/LevelRenderer")
                                && named(call, "m_440595_", "m_440595_")
                                && descriptor.equals("()Lnet/minecraft/gizmos/Gizmos$TemporaryCollection;")) {
                            // The temporary collection opens the active Gizmos collector.
                            // Submit immediately after opening it; finalizeGizmoCollection is
                            // too late because the collector has already been closed.
                            super.visitMethodInsn(opcode, owner, call, descriptor, itf);
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "renderWorld", "()V", false);
                        } else if (named(method,"handleSetEntityMotion","m_8048_") && descriptor.equals("(DDD)V")
                                && named(call,"lerpMotion","m_6001_")) {
                            super.visitMethodInsn(INVOKESTATIC,BRIDGE,"velocity","(Ljava/lang/Object;DDD)V",false);
                        } else if (named(method,"handleExplosion","m_7345_")
                                && named(call,"setDeltaMovement","m_20256_") && descriptor.equals("(Lnet/minecraft/world/phys/Vec3;)V")) {
                            super.visitMethodInsn(INVOKESTATIC,BRIDGE,"explosion","(Ljava/lang/Object;Ljava/lang/Object;)V",false);
                        } else {
                            super.visitMethodInsn(opcode,owner,call,descriptor,itf);
                            if (name.endsWith("/LightTexture") && named(method,"updateLightTexture","m_109881_")
                                    && owner.equals("net/minecraft/client/OptionInstance") && call.equals("get")
                                    && descriptor.equals("()Ljava/lang/Object;")) {
                                optionGets++;
                            }
                            if (name.endsWith("/LightTexture") && named(method,"updateLightTexture","m_109881_")
                                    && owner.equals("java/lang/Double") && call.equals("floatValue")
                                    && descriptor.equals("()F") && ++doubleFloats == 2) {
                                super.visitMethodInsn(INVOKESTATIC, BRIDGE, "gammaFloat", "(F)F", false);
                            }
                            if (name.endsWith("/LightTexture") && named(method,"updateLightTexture","m_109881_")
                                    && owner.equals("net/minecraft/client/OptionInstance") && descriptor.equals("()Ljava/lang/Object;")
                                     && optionIndex++ == 2)
                                super.visitMethodInsn(INVOKESTATIC,BRIDGE,"gamma","(Ljava/lang/Object;)Ljava/lang/Object;",false);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
