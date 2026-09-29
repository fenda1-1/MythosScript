package com.mythos.mythosScriptMod.otherfeatures.handler.movement;

import com.mythos.mythosScriptMod.shadowbaritone.utils.InputOverrideHandler;
import com.mythos.mythosScriptMod.shadowbaritone.utils.PlayerMovementInput;
import dev.mythos.inject.RuntimeBridge;
import dev.mythos.inject.runtime.MovementTransformer;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.junit.Test;
import org.objectweb.asm.*;
import java.lang.reflect.Constructor;
import static org.junit.Assert.*;
import static org.objectweb.asm.Opcodes.*;

public class FreecamIsolationTest {
    public static class Hooks {
        static Object body;
        public static boolean body(Object entity) { return entity == body; }
    }

    @Test public void cameraPreservesNavigationAndInjectedBodyRemainsVisibleAndUpdated() throws Exception {
        Constructor<PlayerMovementInput> constructor=PlayerMovementInput.class.getDeclaredConstructor(InputOverrideHandler.class);
        constructor.setAccessible(true);
        MovementInput automated=constructor.newInstance((Object)null);
        MovementInput keyboard=new MovementInputFromOptions(null);
        for(MovementInput input:new MovementInput[]{automated,keyboard}) {
            input.moveForward=1; input.moveStrafe=-1;
            input.jump=input.sneak=input.forwardKeyDown=input.leftKeyDown=true;
            FreecamFeatureHandler.clearInput(input);
        }
        assertEquals(1,automated.moveForward,0); assertEquals(-1,automated.moveStrafe,0);
        assertTrue(automated.jump && automated.sneak && automated.forwardKeyDown && automated.leftKeyDown);
        assertEquals(0,keyboard.moveForward,0); assertEquals(0,keyboard.moveStrafe,0);
        assertFalse(keyboard.jump || keyboard.sneak || keyboard.forwardKeyDown || keyboard.leftKeyDown);

        // Run the injected boolean hooks, including SRG names used in Forge.
        for(boolean srg:new boolean[]{false,true}) {
            String name="net/minecraft/client/entity/EntityPlayerSP";
            String user=srg?"func_175144_cb":"isUser",view=srg?"func_175160_A":"isCurrentViewEntity";
            ClassWriter writer=new ClassWriter(0);
            writer.visit(V1_8,ACC_PUBLIC,name,null,"java/lang/Object",null);
            MethodVisitor init=writer.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
            init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
            init.visitInsn(RETURN);init.visitMaxs(1,1);init.visitEnd();
            for(String method:new String[]{user,view}) {
                MethodVisitor mv=writer.visitMethod(ACC_PUBLIC,method,"()Z",null,null);
                mv.visitCode();mv.visitInsn(method.equals(user)?ICONST_1:ICONST_0);mv.visitInsn(IRETURN);mv.visitMaxs(1,1);mv.visitEnd();
            }
            writer.visitEnd();
            byte[] bytes=new MovementTransformer().transform(null,name,null,null,writer.toByteArray());
            Class<?> type=new ClassLoader(getClass().getClassLoader()) {
                Class<?> load() { return defineClass(name.replace('/','.'),bytes,0,bytes.length); }
            }.load();
            Object body=type.newInstance();
            RuntimeBridge.install(Hooks.class);
            try {
                Hooks.body=null;
                assertEquals(true,type.getMethod(user).invoke(body));assertEquals(false,type.getMethod(view).invoke(body));
                Hooks.body=body;
                assertEquals(false,type.getMethod(user).invoke(body));assertEquals(true,type.getMethod(view).invoke(body));
            } finally { Hooks.body=null;RuntimeBridge.install(null); }
        }
    }
}
