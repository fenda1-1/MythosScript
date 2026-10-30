package com.mythos.mythosScriptMod.mixin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Routes every MythosScript access to the per-session Minecraft singletons
 * through {@link SessionFieldAccess}.
 *
 * Mythos-Nexus ticks each background bot on its own nexus-bot-* thread and
 * replays MythosScript's tick listeners there (NexusTickBridge). Nexus only
 * rewrites mc.player & co. for a short whitelist of classes, so everywhere
 * else a bot thread read the MOUNTED player and mutated it concurrently with
 * the client thread (e.g. ForceSprint -> setSprinting -> "Modifier is already
 * applied on this attribute!"). This transformer closes that gap for all of
 * MythosScript at load time, so no call site can bypass the session check.
 *
 * Rewritten (MCP and SRG spellings; stack shape is unchanged, so frames stay valid):
 *   GETFIELD/PUTFIELD Minecraft.player / world / playerController / currentScreen / objectMouseOver
 *   GETFIELD/PUTFIELD GameSettings.thirdPersonView
 *   INVOKEVIRTUAL     Minecraft.getConnection()
 */
public class SessionFieldTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogManager.getLogger("MythosScriptCorePlugin");

    static final String ACCESS = "com/mythos/mythosScriptMod/mixin/SessionFieldAccess";

    private static final String MOD_PREFIX = "com.mythos.mythosScriptMod.";
    private static final String[] EXCLUDED_PREFIXES = {
            // Our own coremod package: SessionFieldAccess reads the real fields.
            "com.mythos.mythosScriptMod.mixin.",
            // Mixin classes are merged into vanilla classes; their bytes must stay
            // as written and their call sites choose the accessor explicitly.
            "com.mythos.mythosScriptMod.shadowbaritone.launch.",
    };
    /** Already rewritten to NexusCtx by Mythos-Nexus (NexusRefs FIELD_CTX_CLASSES). */
    private static final Set<String> NEXUS_REWRITTEN = new HashSet<>(Arrays.asList(
            "com.mythos.mythosScriptMod.mcp.McpWorldView",
            "com.mythos.mythosScriptMod.mcp.McpGameService",
            "com.mythos.mythosScriptMod.system.SimulatedKeyInputManager"));

    private static final String MC = "net/minecraft/client/Minecraft";
    private static final String GS = "net/minecraft/client/settings/GameSettings";
    private static final String MC_DESC = "Lnet/minecraft/client/Minecraft;";
    private static final String GS_DESC = "Lnet/minecraft/client/settings/GameSettings;";

    static final class Route {
        final String owner;
        final String mcpName;
        final String srgName;
        final String desc;
        final String getter;
        final String setter;

        Route(String owner, String mcpName, String srgName, String desc, String getter, String setter) {
            this.owner = owner;
            this.mcpName = mcpName;
            this.srgName = srgName;
            this.desc = desc;
            this.getter = getter;
            this.setter = setter;
        }

        boolean matches(String owner, String name, String desc) {
            return this.owner.equals(owner) && this.desc.equals(desc)
                    && (mcpName.equals(name) || srgName.equals(name));
        }

        String ownerDesc() {
            return MC.equals(owner) ? MC_DESC : GS_DESC;
        }
    }

    static final Route[] FIELD_ROUTES = {
            new Route(MC, "player", "field_71439_g",
                    "Lnet/minecraft/client/entity/EntityPlayerSP;", "getPlayer", "setPlayer"),
            new Route(MC, "world", "field_71441_e",
                    "Lnet/minecraft/client/multiplayer/WorldClient;", "getWorld", "setWorld"),
            new Route(MC, "playerController", "field_71442_b",
                    "Lnet/minecraft/client/multiplayer/PlayerControllerMP;",
                    "getPlayerController", "setPlayerController"),
            new Route(MC, "currentScreen", "field_71462_r",
                    "Lnet/minecraft/client/gui/GuiScreen;", "getCurrentScreen", "setCurrentScreen"),
            new Route(MC, "objectMouseOver", "field_71476_x",
                    "Lnet/minecraft/util/math/RayTraceResult;", "getObjectMouseOver", "setObjectMouseOver"),
            new Route(GS, "thirdPersonView", "field_74320_O",
                    "I", "getThirdPersonView", "setThirdPersonView"),
    };

    private static final String CONNECTION_DESC = "()Lnet/minecraft/client/network/NetHandlerPlayClient;";

    static boolean isTarget(String className) {
        if (className == null || !className.startsWith(MOD_PREFIX)) {
            return false;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return !NEXUS_REWRITTEN.contains(className);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName != null ? transformedName : name;
        if (!isTarget(className)) {
            return basicClass;
        }
        try {
            byte[] out = rewrite(basicClass);
            return out != null ? out : basicClass;
        } catch (Throwable t) {
            LOGGER.error("Session field rewrite failed for {}; class left unchanged", className, t);
            return basicClass;
        }
    }

    /** Returns rewritten bytes, or null when the class has no matching access. */
    static byte[] rewrite(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        int[] count = {0};
        reader.accept(new RewriteClassVisitor(writer, count), 0);
        return count[0] == 0 ? null : writer.toByteArray();
    }

    static final class RewriteClassVisitor extends ClassVisitor {
        private final int[] count;

        RewriteClassVisitor(ClassVisitor cv, int[] count) {
            super(Opcodes.ASM5, cv);
            this.count = count;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return mv == null ? null : new RewriteMethodVisitor(mv, count);
        }
    }

    static final class RewriteMethodVisitor extends MethodVisitor {
        private final int[] count;

        RewriteMethodVisitor(MethodVisitor mv, int[] count) {
            super(Opcodes.ASM5, mv);
            this.count = count;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                for (Route r : FIELD_ROUTES) {
                    if (r.matches(owner, name, desc)) {
                        if (opcode == Opcodes.GETFIELD) {
                            // ..., receiver -> ..., value
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS, r.getter,
                                    "(" + r.ownerDesc() + ")" + desc, false);
                        } else {
                            // ..., receiver, value -> ...
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS, r.setter,
                                    "(" + r.ownerDesc() + desc + ")V", false);
                        }
                        count[0]++;
                        return;
                    }
                }
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKEVIRTUAL && MC.equals(owner) && CONNECTION_DESC.equals(desc)
                    && ("getConnection".equals(name) || "func_147114_u".equals(name))) {
                // ..., mc -> ..., handler
                super.visitMethodInsn(Opcodes.INVOKESTATIC, ACCESS, "getConnection",
                        "(" + MC_DESC + ")Lnet/minecraft/client/network/NetHandlerPlayClient;", false);
                count[0]++;
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
