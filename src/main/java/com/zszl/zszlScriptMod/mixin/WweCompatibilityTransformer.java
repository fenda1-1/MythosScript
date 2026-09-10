package com.zszl.zszlScriptMod.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * WWE 3.0.3 can crash in wwe.cQna.<clinit> when its string parsing logic
 * encounters unexpected remote data. Patch that class to use a minimal safe
 * static initialiser so the rest of the client can continue booting.
 */
public class WweCompatibilityTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("ZszlScriptCorePlugin");

    private static final String WWE_CRASH_CLASS = "wwe.cQna";
    private static final String HASH_MAP_DESC = "Ljava/util/HashMap;";
    private static final String ARRAY_LIST_DESC = "Ljava/util/ArrayList;";
    private static final String LINKED_HASH_MAP_DESC = "Ljava/util/LinkedHashMap;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }

        String className = transformedName != null ? transformedName : name;
        if (!WWE_CRASH_CLASS.equals(className)) {
            return basicClass;
        }

        try {
            byte[] patched = patchDangerousStaticInitializer(basicClass);
            if (patched != basicClass) {
                LOGGER.warn("Applied WWE compatibility patch to {}", className);
            } else {
                LOGGER.warn("Skipped WWE compatibility patch for {} because the expected static initialiser was not found", className);
            }
            return patched;
        } catch (Throwable t) {
            LOGGER.error("Failed to apply WWE compatibility patch to {}", className, t);
            return basicClass;
        }
    }

    static byte[] patchDangerousStaticInitializer(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);

        PatchState state = new PatchState();
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                owner = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ((access & Opcodes.ACC_STATIC) != 0) {
                    if (HASH_MAP_DESC.equals(descriptor)) {
                        state.hashMapFields.add(new FieldSpec(name, descriptor));
                    } else if (ARRAY_LIST_DESC.equals(descriptor)) {
                        state.arrayListFields.add(new FieldSpec(name, descriptor));
                    } else if (LINKED_HASH_MAP_DESC.equals(descriptor)) {
                        state.linkedHashMapFields.add(new FieldSpec(name, descriptor));
                    }
                }
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<clinit>".equals(name) && "()V".equals(descriptor)) {
                    state.replacedStaticInitializer = true;
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                if (state.replacedStaticInitializer) {
                    MethodVisitor method = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                    method.visitCode();

                    emitCollectionInitialiser(method, owner, state.hashMapFields, "java/util/HashMap");
                    emitCollectionInitialiser(method, owner, state.linkedHashMapFields, "java/util/LinkedHashMap");
                    emitCollectionInitialiser(method, owner, state.arrayListFields, "java/util/ArrayList");

                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(2, 0);
                    method.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);

        return state.replacedStaticInitializer ? writer.toByteArray() : basicClass;
    }

    private static void emitCollectionInitialiser(MethodVisitor method, String owner, List<FieldSpec> fields, String implementationInternalName) {
        for (FieldSpec field : fields) {
            method.visitTypeInsn(Opcodes.NEW, implementationInternalName);
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, implementationInternalName, "<init>", "()V", false);
            method.visitFieldInsn(Opcodes.PUTSTATIC, owner, field.name, field.descriptor);
        }
    }

    private static final class PatchState {
        private final List<FieldSpec> hashMapFields = new ArrayList<FieldSpec>();
        private final List<FieldSpec> arrayListFields = new ArrayList<FieldSpec>();
        private final List<FieldSpec> linkedHashMapFields = new ArrayList<FieldSpec>();
        private boolean replacedStaticInitializer;
    }

    private static final class FieldSpec {
        private final String name;
        private final String descriptor;

        private FieldSpec(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }
}
