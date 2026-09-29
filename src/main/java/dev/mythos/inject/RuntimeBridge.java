package dev.mythos.inject;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** System-loader bridge; Minecraft classes never link to child-loader mod types. */
public final class RuntimeBridge {
    private static volatile Class<?> hooks;
    private static final Map<String, Method> methods = new ConcurrentHashMap<>();
    public static void install(Class<?> target) { hooks = target; methods.clear(); }
    public static Object invoke(String name, Object... args) {
        if (hooks == null) return null;
        try {
            Method method = methods.get(name);
            if (method == null) {
                for (Method candidate : hooks.getMethods()) if (candidate.getName().equals(name)) {
                    method = candidate; methods.put(name, method); break;
                }
            }
            if (method == null) throw new NoSuchMethodException(name);
            return method.invoke(null, args);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Movement hook failed: " + name, error.getCause());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
    }
    public static boolean travel(Object entity) { return Boolean.TRUE.equals(invoke("travel", entity)); }
    public static boolean turn(Object entity, double yaw, double pitch) { return Boolean.TRUE.equals(invoke("turn", entity, yaw, pitch)); }
    public static boolean turnLegacy(Object entity, float yaw, float pitch) { return turn(entity, yaw, pitch); }
    public static boolean push(Object entity, Object other) { return Boolean.TRUE.equals(invoke("push", entity, other)); }
    public static boolean safeWalk(boolean vanilla, Object entity) { return vanilla || Boolean.TRUE.equals(invoke("safeWalk", entity)); }
    public static boolean active() { return Boolean.TRUE.equals(invoke("active")); }
    public static boolean pick() { return Boolean.TRUE.equals(invoke("pick")); }
    public static boolean chat(String message) { return Boolean.TRUE.equals(invoke("chat", message)); }
    /** Command methods receive the text after the slash. Restore it for the chat hook. */
    public static boolean command(String message) {
        return chat(message != null && message.startsWith("/") ? message : "/" + message);
    }
    public static boolean bodyUpdates(boolean vanilla, Object entity) { return vanilla || Boolean.TRUE.equals(invoke("body", entity)); }
    public static boolean bodyUser(boolean vanilla, Object entity) { return vanilla && !Boolean.TRUE.equals(invoke("body", entity)); }
    public static java.util.List<?> collisions(java.util.List<?> vanilla, Object entity, Object box) {
        Object result = invoke("collisions", vanilla, entity, box);
        return result == null ? vanilla : (java.util.List<?>) result;
    }
    private static volatile Class<?> featureHooks;
    public static void installFeatures(Class<?> target) { featureHooks = target; }
    private static Object feature(String name, Class<?>[] types, Object... args) {
        try {
            String key = "feature." + name;
            Method method = methods.get(key);
            if (method == null) { method = featureHooks.getMethod(name, types); methods.put(key, method); }
            return method.invoke(null, args);
        }
        catch (InvocationTargetException error) { throw new IllegalStateException(name, error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(name, error); }
    }
    public static Object renderState(Object state) {
        return feature("renderState", new Class<?>[]{Object.class}, state);
    }
    public static Object gamma(Object value) {
        return feature("gamma", new Class<?>[]{Object.class}, value);
    }
    public static float gammaFloat(float value) {
        Object result = feature("gamma", new Class<?>[]{Object.class}, Float.valueOf(value));
        return result instanceof Number ? ((Number) result).floatValue() : value;
    }
    public static void velocity(Object entity, double x, double y, double z) {
        feature("velocity", new Class<?>[]{Object.class,double.class,double.class,double.class}, entity,x,y,z);
    }
    public static boolean velocityPacket(Object packet) {
        Object result = feature("velocityPacket", new Class<?>[]{Object.class}, packet);
        return Boolean.TRUE.equals(result);
    }
    public static void explosion(Object entity, Object motion) {
        feature("explosion", new Class<?>[]{Object.class,Object.class}, entity,motion);
    }
    public static void renderWorld() { feature("renderWorld", new Class<?>[]{}, new Object[]{}); }
    public static void renderHud(Object graphics) { feature("renderHud", new Class<?>[]{Object.class}, graphics); }
    public static int timer(Object timer, int original, boolean advanceGameTime) {
        Object result = feature("timer", new Class<?>[]{Object.class,int.class,boolean.class}, timer, original, advanceGameTime);
        return result instanceof Number ? ((Number) result).intValue() : original;
    }
}
