package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler.KillAuraPreset;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class KillAuraBlockingContractTest {
    @Test public void newAndLegacyPresetsLeaveBlockingDisabled() {
        assertFalse(new KillAuraPreset().blockWhileAttacking);
        assertFalse(new Gson().fromJson("{\"name\":\"legacy\"}", KillAuraPreset.class).blockWhileAttacking);
    }

    @Test public void enabledBlockingSurvivesPresetCopyAndJsonRoundTrip() {
        KillAuraPreset source = new KillAuraPreset();
        source.blockWhileAttacking = true;
        KillAuraPreset copy = new KillAuraPreset(source);
        source.blockWhileAttacking = false;
        assertTrue(copy.blockWhileAttacking);
        Gson gson = new Gson();
        assertTrue(gson.fromJson(gson.toJson(copy), KillAuraPreset.class).blockWhileAttacking);
        assertFalse(gson.fromJson(gson.toJson(source), KillAuraPreset.class).blockWhileAttacking);
    }

    @Test public void vanillaReleaseHookExistsExactlyOnceInKeyboardProcessing() throws Exception {
        MethodNode hook = method("com/zszl/zszlScriptMod/shadowbaritone/launch/mixins/MixinMinecraft",
                "zszl$keepAuraShieldRaised");
        String targetMethod = null;
        for (AnnotationNode annotation : hook.visibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("method")) {
                    targetMethod = (String) ((List<?>) annotation.values.get(i + 1)).get(0);
                }
            }
        }
        assertNotNull("The shield hook must declare a target method", targetMethod);
        MethodNode keyboard = method("net/minecraft/client/Minecraft", targetMethod);
        assertEquals(1, calls(keyboard, "net/minecraft/client/multiplayer/PlayerControllerMP", "onStoppedUsingItem"));
        assertTrue(calls(keyboard, "net/minecraft/client/settings/KeyBinding", "isKeyDown") > 0);
    }

    @Test public void releaseSynchronizesServerAndClientAndDirectUseDoesNotInteractWithBlocks() throws Exception {
        MethodNode release = method("net/minecraft/client/multiplayer/PlayerControllerMP", "onStoppedUsingItem");
        assertEquals(1, calls(release, "net/minecraft/client/network/NetHandlerPlayClient", "sendPacket"));
        assertEquals(1, calls(release, "net/minecraft/entity/player/EntityPlayer", "stopActiveHand"));
        MethodNode use = method("net/minecraft/client/multiplayer/PlayerControllerMP", "processRightClick");
        assertEquals(0, calls(use, "net/minecraft/client/multiplayer/PlayerControllerMP", "processRightClickBlock"));
        assertTrue(calls(use, "net/minecraft/item/ItemStack", "useItemRightClick") > 0);
    }

    @Test public void directAttackPathsDoNotLowerTheShield() throws Exception {
        String handler = "com/zszl/zszlScriptMod/handlers/KillAuraHandler";
        for (String name : new String[] {"tryAttackUsingCurrentConfigForHunt", "handleEnderCrystalTarget",
                "executeTeleportTourPlan", "performMouseClickAttack"}) {
            assertEquals(name, 0, calls(method(handler, name), handler, "releaseAttackBlocking"));
        }
        MethodNode targets = method(handler, "attackTargets",
                "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/entity/EntityPlayerSP;Ljava/util/List;"
                        + "Lnet/minecraft/entity/EntityLivingBase;L" + handler + "$AreaHuntOptions;)I");
        assertEquals(0, calls(targets, handler, "releaseAttackBlocking"));
        for (String[] entry : new String[][] {
                {"net/minecraft/client/Minecraft", "clickMouse"},
                {"net/minecraft/client/multiplayer/PlayerControllerMP", "attackEntity"}}) {
            MethodNode attack = method(entry[0], entry[1]);
            for (AbstractInsnNode instruction : attack.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                String name = ((MethodInsnNode) instruction).name;
                assertFalse(entry[1] + " must permit active shield use", name.equals("isHandActive")
                        || name.equals("stopActiveHand") || name.equals("resetActiveHand")
                        || name.equals("onStoppedUsingItem"));
            }
        }
    }

    private static MethodNode method(String type, String name) throws Exception {
        return method(type, name, null);
    }

    private static MethodNode method(String type, String name, String descriptor) throws Exception {
        try (InputStream stream = KillAuraBlockingContractTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(type, stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))) return method;
            }
        }
        throw new AssertionError(type + "." + name);
    }

    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(owner) && call.name.equals(name)) count++;
            }
        }
        return count;
    }
}
