/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.api;

import com.zszl.zszlScriptMod.shadowbaritone.BaritoneProvider;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.SettingsUtil;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Exposes the {@link IBaritoneProvider} instance and the {@link Settings}
 * instance for API usage.
 *
 * @author Brady
 * @since 9/23/2018
 */
public final class BaritoneAPI {

    private static final Object INIT_LOCK = new Object();
    private static IBaritoneProvider provider;
    private static Settings settings;
    private static boolean initializing;

    private static void ensureInitialized() {
        if (provider != null && settings != null) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (provider != null && settings != null) {
                return;
            }
            if (initializing) {
                return;
            }
            initializing = true;
            try {
                if (settings == null) {
                    settings = new Settings();
                    try {
                        SettingsUtil.readAndApply(settings, SettingsUtil.SETTINGS_DEFAULT_NAME);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                if (provider == null) {
                    try {
                        ServiceLoader<IBaritoneProvider> baritoneLoader = ServiceLoader.load(IBaritoneProvider.class);
                        Iterator<IBaritoneProvider> instances = baritoneLoader.iterator();
                        if (instances.hasNext()) {
                            provider = instances.next();
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    if (provider == null) {
                        provider = new BaritoneProvider();
                    }
                }
            } finally {
                initializing = false;
            }
        }
    }

    public static IBaritoneProvider getProvider() {
        ensureInitialized();
        return BaritoneAPI.provider;
    }

    public static Settings getSettings() {
        ensureInitialized();
        return BaritoneAPI.settings;
    }
}
