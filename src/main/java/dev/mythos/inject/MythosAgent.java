package dev.mythos.inject;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Forge event-mode entry point, deliberately independent of Minecraft linkage.
 * Full schema-changing mixins are only supported by ordinary mods installation.
 * The agent does not try to retrofit interfaces/fields into already loaded classes.
 */
public final class MythosAgent {
    private static final String MOD = "com.mythos.mythosScriptMod.mythosScriptMod";
    private static final String RECEIPT = "mythos.attach.receipt";
    private static final String STATE = "mythos.attach.state";
    private static volatile ClassLoader runtimeLoader;
    private static java.util.jar.JarFile bridgeArchive;

    private MythosAgent() {}
    public static void premain(String options, Instrumentation instrumentation) {
        final String request = "startup-" + java.util.UUID.randomUUID();
        Thread starter = new Thread(() -> {
            try {
                for (int i = 0; i < 600; i++) {
                    Class<?> game = findGame(instrumentation);
                    if (game != null && instance(game) != null) {
                        install(request, instrumentation, ownJar());
                        return;
                    }
                    Thread.sleep(200);
                }
                fail(request, new IllegalStateException("Minecraft did not become ready within 120 seconds"));
            } catch (Throwable error) { fail(request, error); }
        }, "Mythos-Agent-Startup");
        starter.setDaemon(true);
        starter.start();
    }

    public static void agentmain(String options, Instrumentation instrumentation) {
        String request = "manual";
        String encodedJar = null;
        for (String option : options == null ? new String[0] : options.split(";")) {
            if (option.startsWith("request=")) request = option.substring(8);
            if (option.startsWith("jar=")) encodedJar = option.substring(4);
        }
        try {
            File selectedJar = encodedJar != null
                ? new File(new String(java.util.Base64.getDecoder().decode(encodedJar), java.nio.charset.StandardCharsets.UTF_8))
                : ownJar();
            install(request, instrumentation, selectedJar);
        }
        catch (Throwable error) { fail(request, error); }
    }

    private static File ownJar() throws Exception {
        return new File(MythosAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static void install(final String request, Instrumentation instrumentation, File jar) throws Exception {
        String expected;
        try (JarFile archive = new JarFile(jar)) {
            expected = archive.getManifest().getMainAttributes().getValue("Mythos-Minecraft-Version");
        }
        if (expected == null) throw new IllegalStateException("Missing Minecraft version metadata");
        Class<?> game = findGame(instrumentation);
        if (game == null) throw new IllegalStateException("No running Minecraft client found");
        final ClassLoader parent = game.getClassLoader();
        String actual = gameVersion(parent);
        if (!expected.equals(actual)) throw new IllegalStateException("Minecraft version mismatch: JAR=" + expected + ", target=" + actual);
        final Object minecraft = instance(game);
        if (minecraft == null) throw new IllegalStateException("Minecraft client is not initialized");

        Properties properties = System.getProperties();
        synchronized (properties) {
            String state = properties.getProperty(STATE, "");
            if (state.equals("installed:" + expected)) { receipt(request, "installed"); return; }
            if (!state.isEmpty()) throw new IllegalStateException("Previous injection state: " + state + "; restart client before retrying");
            // A mod already loaded by Forge must not register duplicate event handlers.
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (type.getName().equals(MOD) && type.getField("instance").get(null) != null) {
                    receipt(request, "installed"); return;
                }
            }
            properties.setProperty(STATE, "pending:" + expected);
            receipt(request, "pending");
        }
        final String version = expected;
        // Child-first for archive classes prevents the system agent loader from
        // defining mod classes that cannot see Minecraft's transforming loader.
        final ClassLoader loader;
        if (parent.getClass().getName().equals("net.minecraft.launchwrapper.LaunchClassLoader")) {
            // Legacy Forge generates event invokers in the game loader. The
            // subscriber class must be visible there, not solely in a child.
            parent.getClass().getMethod("addURL", URL.class).invoke(parent, jar.toURI().toURL());
            parent.getClass().getMethod("addClassLoaderExclusion", String.class).invoke(parent, "dev.mythos.inject.");
            loader = parent;
        } else {
            loader = new RuntimeLoader(jar.toURI().toURL(), parent);
        }
        runtimeLoader = loader; // retained: resources and lazy classes remain available
        Runnable initialize = () -> {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader);
                // Forge may have initialized the ordinary mod after premain
                // queued this task; check again on the actual client thread.
                boolean alreadyLoaded = false;
                for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                    if (type.getName().equals(MOD) && type.getField("instance").get(null) != null) {
                        alreadyLoaded = true;
                        break;
                    }
                }
                if (!alreadyLoaded) {
                    Class<?> mod = Class.forName(MOD, true, loader);
                    mod.getMethod("initializeForAttach").invoke(null);
                    installMovement(instrumentation, parent, loader);
                }
                System.setProperty(STATE, "installed:" + version);
                receipt(request, "installed");
                System.out.println("[MythosAgent] Initialized Minecraft " + version + " (Forge events mode)");
            } catch (Throwable error) {
                System.setProperty(STATE, "failed-partial");
                fail(request, error);
            }
            finally { Thread.currentThread().setContextClassLoader(previous); }
        };
        try {
            schedule(game, minecraft, initialize);
        } catch (Throwable error) {
            // Nothing was queued or registered; a corrected environment can retry.
            System.clearProperty(STATE);
            throw new IllegalStateException("Unable to schedule client initialization", error);
        }
    }

    private static Class<?> findGame(Instrumentation instrumentation) {
        for (Class<?> type : instrumentation.getAllLoadedClasses())
            if (type.getName().equals("net.minecraft.client.Minecraft")) return type;
        return null;
    }
    private static void installMovement(Instrumentation instrumentation, ClassLoader gameLoader, ClassLoader loader) throws Exception {
        if (!instrumentation.isRetransformClassesSupported()) throw new IllegalStateException("JVM retransformation is unavailable");
        if (bridgeArchive == null) {
            // SecureJarHandler's fallback is the platform loader, not the
            // system loader. A JDK-only bridge on bootstrap is visible to both.
            File bridge = File.createTempFile("mythos-runtime-bridge-", ".jar");
            bridge.deleteOnExit();
            try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(bridge));
                 java.io.InputStream in = MythosAgent.class.getResourceAsStream("/dev/mythos/inject/RuntimeBridge.class")) {
                if (in == null) throw new IllegalStateException("Missing runtime bridge bytecode");
                out.putNextEntry(new java.util.jar.JarEntry("dev/mythos/inject/RuntimeBridge.class"));
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer,0,count);
                out.closeEntry();
            }
            bridgeArchive = new java.util.jar.JarFile(bridge);
            instrumentation.appendToBootstrapClassLoaderSearch(bridgeArchive);
        }
        Class<?> hooks = Class.forName("com.mythos.mythosScriptMod.otherfeatures.handler.movement.InjectedMovementHooks", true, loader);
        Class.forName("dev.mythos.inject.RuntimeBridge", true, null).getMethod("install",Class.class).invoke(null,hooks);
        Class<?> type = Class.forName("dev.mythos.inject.runtime.MovementTransformer", true, loader);
        java.lang.instrument.ClassFileTransformer transformer = (java.lang.instrument.ClassFileTransformer) type.newInstance();
        instrumentation.addTransformer(transformer, true);
        Method targets = type.getMethod("targets", String.class);
        for (Class<?> target : instrumentation.getAllLoadedClasses()) {
            if (target.getClassLoader() != gameLoader || !Boolean.TRUE.equals(targets.invoke(null,target.getName()))) continue;
            if (!instrumentation.isModifiableClass(target)) throw new IllegalStateException("Unmodifiable movement class: " + target.getName());
            instrumentation.retransformClasses(target);
            System.out.println("[MythosAgent] Movement hooks: " + target.getName());
        }
        String version = gameVersion(gameLoader);
        if ("1.20.1".equals(version) || "1.21.11".equals(version)) {
            String hookName = "1.21.11".equals(version)
                    ? "com.mythos.mythosScriptMod.otherfeatures.handler.render.FeatureRuntimeHooks1211"
                    : "com.mythos.mythosScriptMod.otherfeatures.handler.render.FeatureRuntimeHooks1201";
            Class<?> features = Class.forName(hookName, true, loader);
            Class.forName("dev.mythos.inject.RuntimeBridge", true, null).getMethod("installFeatures",Class.class).invoke(null,features);
            Class<?> featureType = Class.forName("dev.mythos.inject.runtime.FeatureTransformer1201", true, loader);
            instrumentation.addTransformer((java.lang.instrument.ClassFileTransformer) featureType.newInstance(), true);
            Method featureTargets = featureType.getMethod("targets", String.class);
            for (Class<?> target : instrumentation.getAllLoadedClasses()) {
                if (target.getClassLoader() == gameLoader && Boolean.TRUE.equals(featureTargets.invoke(null,target.getName()))) {
                    instrumentation.retransformClasses(target);
                    System.out.println("[MythosAgent] Render/network hooks: " + target.getName());
                }
            }
            try { features.getMethod("refresh").invoke(null); } catch (NoSuchMethodException ignored) { }
        }
        System.setProperty("mythos.runtime.movementHooks", "true");
    }
    private static Object instance(Class<?> game) throws Exception {
        for (Method method : game.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length == 0 && method.getReturnType() == game) {
                method.setAccessible(true);
                return method.invoke(null);
            }
        }
        return null;
    }
    private static String gameVersion(ClassLoader loader) throws Exception {
        try {
            Class<?> type = Class.forName("net.minecraftforge.versions.mcp.MCPVersion", true, loader);
            return String.valueOf(type.getMethod("getMCVersion").invoke(null));
        } catch (ClassNotFoundException legacy) {
            Class<?> type = Class.forName("net.minecraftforge.common.ForgeVersion", true, loader);
            try { return String.valueOf(type.getField("mcVersion").get(null)); }
            catch (NoSuchFieldException oldForge) {
                return String.valueOf(Class.forName("cpw.mods.fml.common.Loader", true, loader).getField("MC_VERSION").get(null));
            }
        }
    }
    private static void schedule(Class<?> game, Object minecraft, Runnable task) throws Exception {
        for (String name : new String[]{"execute", "addScheduledTask", "func_152344_a", "m_18689_"}) {
            try { game.getMethod(name, Runnable.class).invoke(minecraft, task); return; }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("Minecraft client Runnable scheduler");
    }
    private static void receipt(String request, String status) { System.setProperty(RECEIPT, request + ":" + status); }
    private static void fail(String request, Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null) error = error.getCause();
        receipt(request, "failed:" + error.getClass().getName() + ":" + error.getMessage());
        System.err.println("[MythosAgent] Initialization failed");
        error.printStackTrace();
    }
    private static final class RuntimeLoader extends URLClassLoader {
        RuntimeLoader(URL jar, ClassLoader parent) { super(new URL[]{jar}, parent); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.startsWith("com.mythos.mythosScriptMod.compat.legacy.net.minecraftforge.")
                    && (name.contains(".event.") || name.contains(".gameevent."))) {
                try {
                    ClassLoader parent = getParent();
                    Class<?> transformType = Class.forName("net.minecraftforge.eventbus.EventSubclassTransformer", false, parent);
                    URL resource = findResource(name.replace('.', '/') + ".class");
                    if (resource != null) {
                        Class<?> nodeType = Class.forName("org.objectweb.asm.tree.ClassNode", true, parent);
                        Class<?> visitorType = Class.forName("org.objectweb.asm.ClassVisitor", false, parent);
                        Class<?> readerType = Class.forName("org.objectweb.asm.ClassReader", true, parent);
                        Object node = nodeType.getConstructor().newInstance();
                        try (java.io.InputStream in = resource.openStream()) {
                            Object reader = readerType.getConstructor(java.io.InputStream.class).newInstance(in);
                            readerType.getMethod("accept", visitorType, int.class).invoke(reader, node, 0);
                        }
                        Class<?> asmType = Class.forName("org.objectweb.asm.Type", true, parent);
                        Object type = asmType.getMethod("getObjectType", String.class).invoke(null, name.replace('.', '/'));
                        ClassLoader previous = Thread.currentThread().getContextClassLoader();
                        try {
                            Thread.currentThread().setContextClassLoader(this);
                            transformType.getMethod("transform", nodeType, asmType).invoke(transformType.getConstructor().newInstance(), node, type);
                        } finally { Thread.currentThread().setContextClassLoader(previous); }
                        Class<?> writerType = Class.forName("org.objectweb.asm.ClassWriter", true, parent);
                        Object writer = writerType.getConstructor(int.class).newInstance(1);
                        nodeType.getMethod("accept", visitorType).invoke(node, writer);
                        byte[] bytes = (byte[]) writerType.getMethod("toByteArray").invoke(writer);
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                } catch (ClassNotFoundException unavailable) { /* Forge 1.21 uses dedicated event buses. */ }
                catch (Exception error) { throw new ClassNotFoundException("Attached Forge event " + name, error); }
            }
            try { return super.findClass(name); }
            catch (ClassNotFoundException missing) {
                // Forge queues event wrapper bytecode for ModLauncher's transformer.
                // Attached subscribers live in this loader, so define their queued
                // wrappers here using Forge's own generator and parent ASM classes.
                if (!name.startsWith("com.mythos.") || !name.contains(".__")) throw missing;
                try {
                    ClassLoader parent = getParent();
                    Class<?> factory = Class.forName("net.minecraftforge.eventbus.ModLauncherFactory", false, parent);
                    if (!Boolean.TRUE.equals(factory.getMethod("hasPendingWrapperClass", String.class).invoke(null, name))) throw missing;
                    Class<?> nodeType = Class.forName("org.objectweb.asm.tree.ClassNode", true, parent);
                    Object node = nodeType.getConstructor().newInstance();
                    factory.getMethod("processWrapperClass", String.class, nodeType).invoke(null, name, node);
                    Class<?> writerType = Class.forName("org.objectweb.asm.ClassWriter", true, parent);
                    Object writer = writerType.getConstructor(int.class).newInstance(0);
                    nodeType.getMethod("accept", Class.forName("org.objectweb.asm.ClassVisitor", false, parent)).invoke(node, writer);
                    byte[] bytes = (byte[]) writerType.getMethod("toByteArray").invoke(writer);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (ClassNotFoundException absent) { throw missing; }
                catch (ReflectiveOperationException error) { throw new ClassNotFoundException("Forge event wrapper " + name, error); }
            }
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> type = findLoadedClass(name);
            boolean platform = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                || name.startsWith("jdk.") || name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.")
                || name.startsWith("cpw.mods.") || name.startsWith("org.lwjgl.") || name.startsWith("com.mojang.")
                || name.startsWith("io.netty.") || name.startsWith("org.apache.logging.")
                || name.startsWith("com.google.") || name.startsWith("org.spongepowered.")
                || (name.startsWith("dev.mythos.inject.") && !name.startsWith("dev.mythos.inject.runtime."));
            if (type == null && !platform) { try { type = findClass(name); } catch (ClassNotFoundException ignored) {} }
            if (type == null) type = super.loadClass(name, false);
            if (resolve) resolveClass(type);
            return type;
        }
        @Override public URL getResource(String name) {
            URL own = findResource(name);
            return own != null ? own : super.getResource(name);
        }
    }
}
