package dev.mythos.inject.runtime;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Retransformation-safe: only existing method bodies change. */
public final class MovementTransformer implements ClassFileTransformer {
    private static final String BRIDGE = "dev/mythos/inject/RuntimeBridge";
    private static final int API = asmApi();
    private static int asmApi() {
        for (String version : new String[]{"ASM9","ASM8","ASM7","ASM6","ASM5"})
            try { return Opcodes.class.getField(version).getInt(null); } catch (Exception ignored) {}
        return ASM5;
    }
    private static boolean named(String value, String... names) {
        for (String name : names) if (value.equals(name)) return true;
        return false;
    }
    public static boolean targets(String name) {
        return named(name, "net.minecraft.client.Minecraft", "net.minecraft.entity.Entity",
                "net.minecraft.entity.EntityLivingBase", "net.minecraft.entity.player.EntityPlayer",
                "net.minecraft.client.entity.EntityPlayerSP", "net.minecraft.client.entity.EntityClientPlayerMP",
                "net.minecraft.client.gui.GuiChat", "net.minecraft.client.gui.GuiScreen", "net.minecraft.world.entity.Entity",
                "net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.player.Player",
                "net.minecraft.client.player.LocalPlayer", "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.multiplayer.ClientPacketListener", "net.minecraft.client.renderer.EntityRenderer", "net.minecraft.world.World",
                "net.minecraft.world.level.Level");
    }
    @Override public byte[] transform(ClassLoader loader, String className, Class<?> redef,
            ProtectionDomain domain, byte[] bytes) {
        if (className == null || !targets(className.replace('/', '.'))) return null;
        final boolean[] changed = {false};
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(API, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
                if ((access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0) return next;
                return new MethodVisitor(API, next) {
                    void hook(String method, String descriptor) {
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, method, descriptor, false);
                        changed[0] = true;
                    }
                    void stop(boolean resultBoolean) {
                        Label vanilla = new Label();
                        super.visitJumpInsn(IFEQ, vanilla);
                        if (resultBoolean) super.visitInsn(ICONST_0);
                        super.visitInsn(resultBoolean ? IRETURN : RETURN);
                        super.visitLabel(vanilla);
                        super.visitFrame(F_SAME, 0, null, 0, null);
                    }
                    @Override public void visitCode() {
                        super.visitCode();
                        if (named(name,"travel","func_191986_a","m_7023_","moveEntityWithHeading","func_70612_e")) {
                            super.visitVarInsn(ALOAD,0); hook("travel","(Ljava/lang/Object;)Z"); stop(false);
                        } else if (named(name,"turn","m_19884_","func_70082_c","setAngles") && (desc.equals("(DD)V") || desc.equals("(FF)V"))) {
                            super.visitVarInsn(ALOAD,0);
                            boolean doubles=desc.equals("(DD)V");
                            super.visitVarInsn(doubles?DLOAD:FLOAD,1); super.visitVarInsn(doubles?DLOAD:FLOAD,doubles?3:2);
                            hook(doubles?"turn":"turnLegacy",doubles?"(Ljava/lang/Object;DD)Z":"(Ljava/lang/Object;FF)Z"); stop(false);
                        } else if (named(name,"push","m_7334_","applyEntityCollision","func_70108_f") && desc.startsWith("(L") && desc.endsWith(")V")) {
                            super.visitVarInsn(ALOAD,0); super.visitVarInsn(ALOAD,1); hook("push","(Ljava/lang/Object;Ljava/lang/Object;)Z"); stop(false);
                        } else if (chatEntry(className, name, desc)) {
                            // ChatScreen/GuiChat own the typed text. Commands lose their
                            // leading slash before ClientPacketListener sees them.
                            int messageSlot = 1;
                            for (org.objectweb.asm.Type type : org.objectweb.asm.Type.getArgumentTypes(desc)) {
                                if ("java/lang/String".equals(type.getInternalName())) break;
                                messageSlot += type.getSize();
                            }
                            super.visitVarInsn(ALOAD, messageSlot);
                            hook(commandEntry(className, name) ? "command" : "chat", "(Ljava/lang/String;)Z");
                            stop(desc.endsWith(")Z"));
                        } else if (className.equals("net/minecraft/client/renderer/EntityRenderer")
                                && named(name,"renderHand","func_78476_b","applyBobbing","func_78475_f","hurtCameraEffect","func_78482_e")) {
                            hook("active","()Z"); stop(false);
                        } else if (className.equals("net/minecraft/client/Minecraft")) {
                            if (named(name,"pickBlock","m_91280_","middleClickMouse","func_147112_ai")) { hook("pick","()Z"); stop(false); }
                            else if (named(name,"startAttack","m_202354_","startUseItem","m_91277_","continueAttack","m_91386_",
                                    "clickMouse","func_147116_af","rightClickMouse","func_147121_ag","sendClickBlockToController","func_147115_a")) {
                                hook("active","()Z"); stop(desc.endsWith(")Z"));
                            }
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (safeWalkSite(name, desc, method, descriptor)) {
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            super.visitVarInsn(ALOAD,0); hook("safeWalk","(ZLjava/lang/Object;)Z");
                        } else if (className.equals("net/minecraft/client/renderer/EntityRenderer")
                                && named(name,"renderWorldPass","func_175068_a") && named(method,"isSpectator","func_175149_v") && descriptor.equals("()Z")) {
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            hook("active","()Z"); super.visitInsn(IOR);
                        } else if (className.equals("net/minecraft/client/entity/EntityPlayerSP")
                                && named(name,"onLivingUpdate","func_70636_d") && named(method,"isKeyDown","func_151470_d") && descriptor.equals("()Z")) {
                            // The sprint key accelerates the camera, not the body.
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            super.visitVarInsn(ALOAD,0); hook("bodyUser","(ZLjava/lang/Object;)Z");
                        } else if (named(name,"sendPosition","m_108640_","onUpdateWalkingPlayer","func_175161_p")
                                && named(method,"isControlledCamera","m_108636_","isCurrentViewEntity","func_175160_A") && descriptor.equals("()Z")) {
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            super.visitVarInsn(ALOAD,0); hook("bodyUpdates","(ZLjava/lang/Object;)Z");
                        } else if (named(name,"collide","m_20272_") && named(method,"getEntityCollisions","m_183134_")
                                && named(owner, "net/minecraft/world/level/Level")) {
                            // Preserve the expanded query box in a fresh local slot.
                            // These methods have one Vec3 argument; slot 32 is outside vanilla locals.
                            super.visitInsn(DUP); super.visitVarInsn(ASTORE,32);
                            super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                            super.visitVarInsn(ALOAD,0); super.visitVarInsn(ALOAD,32);
                            hook("collisions","(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;");
                        } else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                    @Override public void visitInsn(int opcode) {
                        if (opcode == IRETURN && className.equals("net/minecraft/client/entity/EntityPlayerSP") && desc.equals("()Z")) {
                            // Match the normal-load mixins: render the real player
                            // and keep living/packet updates on it, not the camera.
                            if (named(name,"isUser","func_175144_cb")) {
                                super.visitVarInsn(ALOAD,0); hook("bodyUser","(ZLjava/lang/Object;)Z");
                            } else if (named(name,"isCurrentViewEntity","func_175160_A")) {
                                super.visitVarInsn(ALOAD,0); hook("bodyUpdates","(ZLjava/lang/Object;)Z");
                            }
                        }
                        if (opcode == ARETURN && className.equals("net/minecraft/world/World")
                                && named(name,"getCollidingBoundingBoxes","func_72945_a","getCollisionBoxes")) {
                            super.visitVarInsn(ALOAD,1); super.visitVarInsn(ALOAD,2);
                            hook("collisions","(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;");
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        return changed[0] ? writer.toByteArray() : null;
    }

    /** Typed chat never reaches LocalPlayer. Verified against the runtime jars. */
    private static boolean chatEntry(String className, String name, String desc) {
        if (named(className, "net/minecraft/client/gui/GuiChat")
                && named(name, "submitChatMessage", "sendChatMessage") && desc.equals("(Ljava/lang/String;)V")) return true;
        if (named(className, "net/minecraft/client/gui/GuiScreen")
                && name.equals("sendChatMessage") && desc.equals("(Ljava/lang/String;)V")) return true;
        if (named(className, "net/minecraft/client/gui/screens/ChatScreen")
                && named(name, "handleChatInput", "m_241797_")
                && named(desc, "(Ljava/lang/String;Z)Z", "(Ljava/lang/String;Z)V")) return true;
        if (named(className, "net/minecraft/client/multiplayer/ClientPacketListener")
                && named(name, "sendChat", "m_246175_") && desc.equals("(Ljava/lang/String;)V")) return true;
        if (commandEntry(className, name) && desc.equals("(Ljava/lang/String;)V")) return true;
        return named(className, "net/minecraft/client/entity/EntityClientPlayerMP", "net/minecraft/client/entity/EntityPlayerSP")
                && named(name, "sendChatMessage", "func_71165_d") && desc.equals("(Ljava/lang/String;)V");
    }

    private static boolean commandEntry(String className, String name) {
        return named(className, "net/minecraft/client/multiplayer/ClientPacketListener")
                && named(name, "sendCommand", "m_246623_");
    }

    /** 1.7.10 sneaks inside moveEntity; modern clients sneak inside Player.maybeBackOffFromEdge. */
    private static boolean safeWalkSite(String name, String desc, String method, String descriptor) {
        if (!descriptor.equals("()Z") || !named(method, "isStayingOnGroundSurface", "m_36343_", "isSneaking", "func_70093_af")) return false;
        return named(name, "maybeBackOffFromEdge", "m_5763_") || named(name, "moveEntity", "func_70091_d")
                || (name.equals("move") && desc.equals("(Lnet/minecraft/entity/MoverType;DDD)V"));
    }
}
