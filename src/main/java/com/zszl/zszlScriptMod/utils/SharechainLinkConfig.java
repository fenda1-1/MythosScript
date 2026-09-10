package com.zszl.zszlScriptMod.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class SharechainLinkConfig {

    private static final String RESOURCE_NAME = "sharechain-links.properties";
    private static final Properties PROPERTIES = new Properties();
    private static final Object LOAD_LOCK = new Object();
    private static volatile boolean loaded = false;

    private SharechainLinkConfig() {
    }

    public static String getRequiredUrl(String key) {
        ensureLoaded();
        String value = PROPERTIES.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing sharechain url config for key: " + key);
        }
        return value;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (loaded) {
                return;
            }
            try (InputStream in = SharechainLinkConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
                if (in == null) {
                    throw new IllegalStateException("Missing resource config: " + RESOURCE_NAME);
                }
                PROPERTIES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                loaded = true;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load resource config: " + RESOURCE_NAME, e);
            }
        }
    }
}
