package com.zszl.zszlScriptMod.utils;

import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.Locale;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ClientTranslationInjector implements IResourceManagerReloadListener {

    public static final ClientTranslationInjector INSTANCE = new ClientTranslationInjector();

    private static final Pattern NUMERIC_VARIABLE_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]");
    private static final Pattern MODULE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String[] DOMAINS = { "zszl_script", "shadowbaritone" };

    /** Keys owned by the mod, allowing a language switch without a full reload. */
    private final Map<String, String> originalValues = new HashMap<>();
    private final Set<String> managedKeys = new HashSet<>();
    private final Map<String, Map<String, String>> translationCache = new HashMap<>();
    private final Set<String> loadingLocales = new HashSet<>();
    private boolean registered;

    private ClientTranslationInjector() {
    }

    public void install() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (!registered && mc.getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
            registered = true;
        }
        injectTranslations("install");
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        injectTranslations("resource_reload");
    }

    @SuppressWarnings("unchecked")
    private synchronized void injectTranslations(String reason) {
        try {
            Object localeObject = ReflectionCompat.getPrivateValue(I18n.class, null, "i18nLocale", "field_135054_a");
            if (!(localeObject instanceof Locale)) {
                return;
            }

            Map<String, String> properties = ReflectionCompat.getPrivateValue(Locale.class, (Locale) localeObject,
                    "properties", "field_135032_a");
            if (properties == null) {
                return;
            }

            String currentLanguage = resolveCurrentLanguageCode();
            int injected = replaceManagedTranslations(properties, currentLanguage);
            zszlScriptMod.LOGGER.info("Injected {} translation entries via {} for locale {}", injected, reason,
                    currentLanguage);
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.warn("Failed to inject bundled translations via {}", reason, t);
        }
    }

    /**
     * Applies only the mod's translation keys. Unlike Minecraft.refreshResources,
     * this does not reload textures, language packs, fonts, or the current world.
     */
    public synchronized boolean applyLanguage(String languageCode) {
        String normalizedLanguage = normalizeLocaleCode(languageCode);
        try {
            Map<String, String> baseTranslations = getCachedTranslations("en_us");
            Map<String, String> selectedTranslations = getCachedTranslations(normalizedLanguage);
            if (baseTranslations == null || selectedTranslations == null) {
                preloadLocaleAsync("en_us");
                preloadLocaleAsync(normalizedLanguage);
                return false;
            }
            Object localeObject = ReflectionCompat.getPrivateValue(I18n.class, null, "i18nLocale", "field_135054_a");
            if (!(localeObject instanceof Locale)) {
                return false;
            }
            Map<String, String> properties = ReflectionCompat.getPrivateValue(Locale.class, (Locale) localeObject,
                    "properties", "field_135032_a");
            if (properties == null) {
                return false;
            }
            replaceManagedTranslations(properties, baseTranslations, selectedTranslations);
            return true;
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.warn("Failed to switch bundled translations to {}", normalizedLanguage, t);
            return false;
        }
    }

    private int replaceManagedTranslations(Map<String, String> properties, String localeCode) {
        Map<String, String> baseTranslations = loadTranslations("en_us");
        Map<String, String> selectedTranslations = "en_us".equals(localeCode)
                ? baseTranslations : loadTranslations(localeCode);
        return replaceManagedTranslations(properties, baseTranslations, selectedTranslations);
    }

    private int replaceManagedTranslations(Map<String, String> properties, Map<String, String> baseTranslations,
            Map<String, String> selectedTranslations) {
        restoreOriginalTranslations(properties);
        Map<String, String> translated = new HashMap<>(baseTranslations);
        if (selectedTranslations != baseTranslations) {
            translated.putAll(selectedTranslations);
        }
        int count = 0;
        for (Map.Entry<String, String> entry : translated.entrySet()) {
            String key = entry.getKey();
            if (!originalValues.containsKey(key)) {
                originalValues.put(key, properties.get(key));
            }
            managedKeys.add(key);
            properties.put(key, entry.getValue());
            count++;
        }
        return count;
    }

    private Map<String, String> getCachedTranslations(String localeCode) {
        synchronized (this) {
            return translationCache.get(normalizeLocaleCode(localeCode));
        }
    }

    private Map<String, String> loadTranslations(String localeCode) {
        String normalizedLocale = normalizeLocaleCode(localeCode);
        Map<String, String> cached = getCachedTranslations(normalizedLocale);
        if (cached != null) {
            return cached;
        }
        Map<String, String> loaded = new HashMap<>();
        mergeLocale(loaded, normalizedLocale);
        synchronized (this) {
            Map<String, String> existing = translationCache.get(normalizedLocale);
            if (existing != null) {
                return existing;
            }
            translationCache.put(normalizedLocale, loaded);
            return loaded;
        }
    }

    private void preloadLocaleAsync(final String localeCode) {
        final String normalizedLocale = normalizeLocaleCode(localeCode);
        synchronized (this) {
            if (translationCache.containsKey(normalizedLocale) || !loadingLocales.add(normalizedLocale)) {
                return;
            }
        }
        Thread loader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    loadTranslations(normalizedLocale);
                } finally {
                    synchronized (ClientTranslationInjector.this) {
                        loadingLocales.remove(normalizedLocale);
                    }
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null) {
                        mc.addScheduledTask(new Runnable() {
                            @Override
                            public void run() {
                                if (mc.gameSettings != null
                                        && normalizedLocale.equals(normalizeLocaleCode(mc.gameSettings.language))) {
                                    applyLanguage(normalizedLocale);
                                }
                            }
                        });
                    }
                }
            }
        }, "zszl-script-language-" + normalizedLocale);
        loader.setDaemon(true);
        loader.start();
    }

    private void restoreOriginalTranslations(Map<String, String> properties) {
        for (String key : managedKeys) {
            if (originalValues.containsKey(key) && originalValues.get(key) != null) {
                properties.put(key, originalValues.get(key));
            } else {
                properties.remove(key);
            }
        }
        managedKeys.clear();
        originalValues.clear();
    }

    private void mergeLocale(Map<String, String> properties, String localeCode) {
        String normalizedLocale = normalizeLocaleCode(localeCode);
        for (String domain : DOMAINS) {
            mergeModularLocale(properties, domain, normalizedLocale);
        }
    }

    /**
     * Loads one bundled modular .lang file.
     */
    private void mergeResource(Map<String, String> properties, String resourcePath) {
        try (InputStream stream = ClientTranslationInjector.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim();
                    if (trimmedLine.isEmpty() || trimmedLine.charAt(0) == '#') {
                        continue;
                    }

                    int splitIndex = findSplitIndex(line);
                    if (splitIndex <= 0 || splitIndex >= line.length() - 1) {
                        continue;
                    }

                    String key = line.substring(0, splitIndex).trim();
                    String value = line.substring(splitIndex + 1).trim();
                    if (key.isEmpty() || value.isEmpty()) {
                        continue;
                    }

                    properties.put(key, NUMERIC_VARIABLE_PATTERN.matcher(value).replaceAll("%$1s"));
                }
            }
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.warn("Failed to merge translation resource {}", resourcePath, t);
        }
    }

    /**
     * Reads the module manifest instead of enumerating a classpath directory.
     * Directory enumeration works on an exploded development run but is not
     * reliable once the resources are packaged in the mod JAR.
     */
    private void mergeModularLocale(Map<String, String> properties, String domain, String localeCode) {
        String moduleRoot = "assets/" + domain + "/lang/i18n/";
        String manifestPath = moduleRoot + "modules.list";
        try (InputStream stream = ClientTranslationInjector.class.getClassLoader().getResourceAsStream(manifestPath)) {
            if (stream == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String moduleName = line.trim();
                    if (moduleName.isEmpty() || moduleName.charAt(0) == '#'
                            || !MODULE_NAME_PATTERN.matcher(moduleName).matches()) {
                        continue;
                    }
                    mergeResource(properties, moduleRoot + localeCode + "/" + moduleName + ".lang");
                }
            }
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.warn("Failed to read translation module manifest {}", manifestPath, t);
        }
    }

    private String resolveCurrentLanguageCode() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.language == null) {
            return "en_us";
        }
        return normalizeLocaleCode(mc.gameSettings.language);
    }

    private String normalizeLocaleCode(String localeCode) {
        if (localeCode == null) {
            return "en_us";
        }
        String normalized = localeCode.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        if (normalized.isEmpty() || !("zh_cn".equals(normalized) || "en_us".equals(normalized))) {
            return "en_us";
        }
        return normalized;
    }

    private int findSplitIndex(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }
}
