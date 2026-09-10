package com.zszl.zszlScriptMod.mixin;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("ZszlScriptCorePlugin")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class ZszlScriptCorePlugin implements IFMLLoadingPlugin {

    private static final Logger LOGGER = LogManager.getLogger("ZszlScriptCorePlugin");
    private static final String SHADOWBARITONE_MIXIN_CONFIG = "mixins.shadowbaritone.json";
    private static final String MIXIN_BOOTSTRAP_CLASS = "org.spongepowered.asm.launch.MixinBootstrap";
    private static final String MIXINS_CLASS = "org.spongepowered.asm.mixin.Mixins";
    private static final String MIXIN_ENVIRONMENT_CLASS = "org.spongepowered.asm.mixin.MixinEnvironment";
    private static boolean mixinsInitialized = false;

    private static final class MixinBootstrapHandle {
        private final ClassLoader classLoader;
        private final String sourceDescription;
        private final boolean sharedRuntime;

        private MixinBootstrapHandle(ClassLoader classLoader, String sourceDescription, boolean sharedRuntime) {
            this.classLoader = classLoader;
            this.sourceDescription = sourceDescription == null ? "(unknown)" : sourceDescription;
            this.sharedRuntime = sharedRuntime;
        }
    }

    public ZszlScriptCorePlugin() {
        if (hasQueuedExternalMixinTweaker()) {
            LOGGER.info("Detected external MixinTweaker, attempting compatibility bootstrap for {}",
                    SHADOWBARITONE_MIXIN_CONFIG);
        }
        initializeMixins();
    }

    private static synchronized void initializeMixins() {
        if (mixinsInitialized) {
            return;
        }

        try {
            MixinBootstrapHandle bootstrapHandle = resolveMixinBootstrapHandle();
            ClassLoader mixinClassLoader = bootstrapHandle.classLoader;
            Class<?> bootstrapClass = Class.forName(MIXIN_BOOTSTRAP_CLASS, true, mixinClassLoader);
            Method initMethod = bootstrapClass.getMethod("init");
            initMethod.invoke(null);

            Class<?> mixinsClass = Class.forName(MIXINS_CLASS, true, mixinClassLoader);
            Method addConfigurationMethod = mixinsClass.getMethod("addConfiguration", String.class);
            addConfigurationMethod.invoke(null, SHADOWBARITONE_MIXIN_CONFIG);

            try {
                Class<?> environmentClass = Class.forName(MIXIN_ENVIRONMENT_CLASS, true, mixinClassLoader);
                Method getDefaultEnvironmentMethod = environmentClass.getMethod("getDefaultEnvironment");
                Object defaultEnvironment = getDefaultEnvironmentMethod.invoke(null);
                Method setObfuscationContextMethod = environmentClass.getMethod("setObfuscationContext", String.class);
                setObfuscationContextMethod.invoke(defaultEnvironment, "searge");
            } catch (Throwable ignored) {
                LOGGER.warn("Failed to set Mixin obfuscation context for {}", SHADOWBARITONE_MIXIN_CONFIG);
            }

            mixinsInitialized = true;
            LOGGER.info("Initialized Mixin configuration {} using {} runtime from {}",
                    SHADOWBARITONE_MIXIN_CONFIG,
                    bootstrapHandle.sharedRuntime ? "shared" : "embedded",
                    bootstrapHandle.sourceDescription);
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize Mixin configuration {}", SHADOWBARITONE_MIXIN_CONFIG, t);
        }
    }

    private static MixinBootstrapHandle resolveMixinBootstrapHandle() throws Exception {
        ClassLoader hostLoader = getHostMixinClassLoader();
        Class<?> sharedBootstrap = tryLoadMixinBootstrap(hostLoader);
        if (sharedBootstrap != null) {
            return new MixinBootstrapHandle(hostLoader, describeCodeSource(sharedBootstrap), true);
        }

        if (hasQueuedExternalMixinTweaker()) {
            LOGGER.warn("Detected queued external MixinTweaker but no shared Mixin runtime was visible from the host loader; falling back to embedded runtime bootstrap");
        }

        attachCurrentJarToHostLoader(hostLoader);
        Class<?> embeddedBootstrap = tryLoadMixinBootstrap(hostLoader);
        if (embeddedBootstrap == null) {
            throw new ClassNotFoundException("Failed to load " + MIXIN_BOOTSTRAP_CLASS + " from host loader "
                    + describeClassLoader(hostLoader));
        }
        return new MixinBootstrapHandle(hostLoader, describeCodeSource(embeddedBootstrap), false);
    }

    private static ClassLoader getHostMixinClassLoader() {
        ClassLoader loader = Launch.class.getClassLoader();
        if (loader != null) {
            return loader;
        }
        loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            return loader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    private static void attachCurrentJarToHostLoader(ClassLoader hostLoader) throws Exception {
        URL currentJar = getCurrentJarLocation();
        if (currentJar == null) {
            throw new IllegalStateException("Cannot resolve current mod jar location for embedded Mixin bootstrap");
        }
        if (!(hostLoader instanceof URLClassLoader)) {
            throw new IllegalStateException("Host class loader does not support URL injection: "
                    + describeClassLoader(hostLoader));
        }

        URLClassLoader urlClassLoader = (URLClassLoader) hostLoader;
        if (containsUrl(urlClassLoader, currentJar)) {
            return;
        }

        Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addUrlMethod.setAccessible(true);
        addUrlMethod.invoke(urlClassLoader, currentJar);
        LOGGER.info("Attached current mod jar to host class loader {} for embedded Mixin bootstrap: {}",
                describeClassLoader(hostLoader), currentJar);
    }

    private static boolean containsUrl(URLClassLoader loader, URL targetUrl) {
        if (loader == null || targetUrl == null) {
            return false;
        }
        String target = normalizeUrl(targetUrl);
        for (URL url : loader.getURLs()) {
            if (target.equals(normalizeUrl(url))) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> tryLoadMixinBootstrap(ClassLoader loader) {
        if (loader == null) {
            return null;
        }
        try {
            return Class.forName(MIXIN_BOOTSTRAP_CLASS, false, loader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static URL getCurrentJarLocation() {
        try {
            CodeSource codeSource = ZszlScriptCorePlugin.class.getProtectionDomain() == null
                    ? null
                    : ZszlScriptCorePlugin.class.getProtectionDomain().getCodeSource();
            return codeSource == null ? null : codeSource.getLocation();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String describeCodeSource(Class<?> type) {
        if (type == null || type.getProtectionDomain() == null || type.getProtectionDomain().getCodeSource() == null) {
            return "(unknown source)";
        }
        URL location = type.getProtectionDomain().getCodeSource().getLocation();
        return location == null ? "(unknown source)" : String.valueOf(location);
    }

    private static String describeClassLoader(ClassLoader loader) {
        if (loader == null) {
            return "(bootstrap)";
        }
        return loader.getClass().getName();
    }

    private static String normalizeUrl(URL url) {
        return url == null ? "" : String.valueOf(url).replace('\\', '/');
    }

    @SuppressWarnings("unchecked")
    private static boolean hasQueuedExternalMixinTweaker() {
        try {
            Object tweakClasses = Launch.blackboard == null ? null : Launch.blackboard.get("TweakClasses");
            if (!(tweakClasses instanceof List)) {
                return false;
            }
            for (Object entry : (List<Object>) tweakClasses) {
                if (entry instanceof String
                        && "org.spongepowered.asm.launch.MixinTweaker".equals(entry)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to inspect Launch blackboard for queued Mixin tweakers", t);
        }
        return false;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                "com.zszl.zszlScriptMod.mixin.WweCompatibilityTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
