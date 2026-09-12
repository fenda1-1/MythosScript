package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class MovementProtectionMixinContractTest {
    @Test public void animalsAndBothPlayerSidesUseTheLivingCollisionHooks() throws Exception {
        String living = "net/minecraft/entity/EntityLivingBase";
        assertNotNull(method(living, "canBePushed", "()Z"));
        MethodNode nearby = method(living, "collideWithNearbyEntities", "()V");
        assertTrue(callIndex(nearby, "net/minecraft/util/EntitySelectors", "getTeamCollisionPredicate") >= 0);
        MethodNode collide = method(living, "collideWithEntity", "(Lnet/minecraft/entity/Entity;)V");
        assertTrue(callIndex(collide, "net/minecraft/entity/Entity", "applyEntityCollision") >= 0);
        for (String type : new String[] {"net/minecraft/client/entity/EntityPlayerSP",
                "net/minecraft/entity/player/EntityPlayerMP", "net/minecraft/entity/passive/EntityCow",
                "net/minecraft/entity/passive/EntitySheep"}) {
            for (String current = type; !current.equals(living); ) {
                ClassNode node = read(current);
                assertFalse(type + " overrides the push-candidate hook in " + current,
                        node.methods.stream().anyMatch(m -> m.name.equals("canBePushed") && m.desc.equals("()Z")));
                assertFalse(type + " overrides the nearby-collision hook in " + current,
                        node.methods.stream().anyMatch(m -> m.name.equals("collideWithNearbyEntities") && m.desc.equals("()V")));
                current = node.superName;
                assertNotNull(type + " must inherit EntityLivingBase", current);
            }
        }
    }

    @Test public void velocityHookRunsAfterThreadScheduling() throws Exception {
        MethodNode velocity = method("net/minecraft/client/network/NetHandlerPlayClient", "handleEntityVelocity",
                "(Lnet/minecraft/network/play/server/SPacketEntityVelocity;)V");
        int scheduled = callIndex(velocity, "net/minecraft/network/PacketThreadUtil", "checkThreadAndEnqueue");
        int applied = callIndex(velocity, "net/minecraft/entity/Entity", "setVelocity");
        assertTrue("Velocity must be filtered on the game thread", scheduled >= 0 && applied > scheduled);
        MethodNode explosion = method("net/minecraft/client/network/NetHandlerPlayClient", "handleExplosion",
                "(Lnet/minecraft/network/play/server/SPacketExplosion;)V");
        for (String axis : new String[] {"X", "Y", "Z"}) {
            assertTrue(callIndex(explosion, "net/minecraft/network/play/server/SPacketExplosion", "getMotion" + axis) >= 0);
        }
    }

    @Test public void entityCollisionHookIsSeparateFromBlockPass() throws Exception {
        MethodNode collision = method("net/minecraft/world/World", "getCollisionBoxes",
                "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;");
        int blocks = callIndex(collision, "net/minecraft/world/World", "getCollisionBoxes");
        int entities = callIndex(collision, "net/minecraft/world/World", "getEntitiesWithinAABBExcludingEntity");
        assertTrue("Omitting entity candidates must retain the preceding block pass", blocks >= 0 && entities > blocks);
        for (String type : new String[] {"EntityBoat", "EntityMinecart"}) {
            assertNotNull(method("net/minecraft/entity/item/" + type, "applyEntityCollision", "(Lnet/minecraft/entity/Entity;)V"));
        }
    }

    @Test public void everyMovementPacketUsesTheAccessorBaseClass() throws Exception {
        String base = "net/minecraft/network/play/client/CPacketPlayer";
        assertTrue(read(base).fields.stream().anyMatch(field -> field.name.equals("onGround") && field.desc.equals("Z")));
        for (String variant : new String[] {"Position", "Rotation", "PositionRotation"}) {
            assertEquals(base, read(base + "$" + variant).superName);
        }
    }

    private static ClassNode read(String type) throws Exception {
        try (InputStream stream = MovementProtectionMixinContractTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            assertNotNull(type, stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(String type, String name, String descriptor) throws Exception {
        for (MethodNode method : read(type).methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        }
        throw new AssertionError(type + "." + name + descriptor);
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(owner) && call.name.equals(name)) return index;
            }
            index++;
        }
        return -1;
    }
}
