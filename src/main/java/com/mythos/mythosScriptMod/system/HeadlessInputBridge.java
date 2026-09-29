package com.mythos.mythosScriptMod.system;

import com.mythos.mythosScriptMod.mythosScriptMod;
import com.mythos.mythosScriptMod.mcp.McpDragonCore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.lwjgl.input.Keyboard;

public final class HeadlessInputBridge {
    private HeadlessInputBridge() {}

    public static void install() {
        try {
            ClassLoader loader = Keyboard.class.getClassLoader();
            Class<?> api = Class.forName("io.github.headlesshq.headlessmc.lwjgl.api.RedirectionApi", false, loader);
            Class<?> redirection = Class.forName("io.github.headlesshq.headlessmc.lwjgl.api.Redirection", false, loader);
            Object manager = api.getMethod("getRedirectionManager").invoke(null);
            Method redirect = manager.getClass().getMethod("redirect", String.class, redirection);
            Map<String, Integer> codes = new HashMap<>();
            Map<Integer, String> names = new HashMap<>();
            for (Field field : Keyboard.class.getFields()) {
                if (field.getName().startsWith("KEY_") && field.getType() == int.class
                        && Modifier.isStatic(field.getModifiers())) {
                    int code = field.getInt(null);
                    String name = field.getName().substring(4);
                    codes.put(name, code);
                    names.put(code, name);
                }
            }
            Map<String, Function<Object[], Object>> handlers = new HashMap<>();
            handlers.put("getKeyIndex(Ljava/lang/String;)I", args -> args[0] == null ? 0
                    : codes.getOrDefault(args[0].toString().toUpperCase(Locale.ROOT), 0));
            handlers.put("getKeyName(I)Ljava/lang/String;", args -> names.get((Integer) args[0]));
            handlers.put("isCreated()Z", args -> true);
            handlers.put("next()Z", args -> SimulatedKeyInputManager.beginSyntheticKeyboardEvent());
            handlers.put("getEventKey()I", args -> current() == null ? 0 : current().getKeyCode());
            handlers.put("getEventCharacter()C", args -> current() == null ? '\0' : current().getCharacter());
            handlers.put("getEventKeyState()Z", args -> current() != null && current().isKeyState());
            handlers.put("getEventNanoseconds()J", args -> current() == null ? 0L : System.nanoTime());
            handlers.put("isRepeatEvent()Z", args -> false);
            handlers.put("isKeyDown(I)Z", args -> McpDragonCore.active() ? McpDragonCore.keyDown((Integer) args[0]) : Boolean.TRUE.equals(
                    SimulatedKeyInputManager.getSyntheticKeyDownOverride((Integer) args[0])));
            for (Map.Entry<String, Function<Object[], Object>> handler : handlers.entrySet()) {
                Function<Object[], Object> function = handler.getValue();
                Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{redirection}, (instance, method, args) -> {
                    if (method.getName().equals("invoke")) return function.apply((Object[]) args[3]);
                    if (method.getName().equals("toString")) return "MythosScript headless keyboard redirection";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
                    if (method.getName().equals("equals")) return instance == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
                redirect.invoke(manager, "Lorg/lwjgl/input/Keyboard;" + handler.getKey(), proxy);
            }
            Map<String, Function<Object[], Object>> mouse = new HashMap<>();
            mouse.put("getX()I", args -> dragonMouse(false));
            mouse.put("getY()I", args -> dragonMouse(true));
            mouse.put("getEventX()I", args -> dragonMouse(false));
            mouse.put("getEventY()I", args -> dragonMouse(true));
            mouse.put("getDWheel()I", args -> McpDragonCore.active() ? McpDragonCore.wheel() : 0);
            mouse.put("getEventDWheel()I", args -> McpDragonCore.active() ? McpDragonCore.wheel() : 0);
            mouse.put("isButtonDown(I)Z", args -> McpDragonCore.active() && McpDragonCore.buttonDown((Integer)args[0]));
            for (Map.Entry<String, Function<Object[], Object>> handler : mouse.entrySet()) {
                Function<Object[], Object> function = handler.getValue();
                Object proxy = Proxy.newProxyInstance(loader,new Class<?>[]{redirection},(instance,method,args) -> {
                    if(method.getName().equals("invoke"))return function.apply((Object[])args[3]);
                    if(method.getName().equals("toString"))return "Mythos DragonCore mouse";
                    if(method.getName().equals("hashCode"))return System.identityHashCode(instance);
                    if(method.getName().equals("equals"))return instance==args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
                redirect.invoke(manager,"Lorg/lwjgl/input/Mouse;"+handler.getKey(),proxy);
            }
            mythosScriptMod.LOGGER.info("Headless keyboard redirections installed");
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException failure) {
            mythosScriptMod.LOGGER.error("Could not install headless keyboard redirections", failure);
        }
    }

    private static SimulatedKeyInputManager.SyntheticKeyboardEvent current() {
        return SimulatedKeyInputManager.getCurrentSyntheticKeyboardEvent();
    }
    private static int dragonMouse(boolean y) {
        if(!McpDragonCore.active())return 0;
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution size=new net.minecraft.client.gui.ScaledResolution(mc);
        return y ? (size.getScaledHeight()-McpDragonCore.mouseY()-1)*mc.displayHeight/size.getScaledHeight()
            : McpDragonCore.mouseX()*mc.displayWidth/size.getScaledWidth();
    }
}
