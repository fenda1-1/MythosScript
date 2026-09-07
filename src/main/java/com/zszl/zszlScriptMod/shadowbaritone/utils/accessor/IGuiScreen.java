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

package com.zszl.zszlScriptMod.shadowbaritone.utils.accessor;

import net.minecraft.client.gui.GuiScreen;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;

public interface IGuiScreen {

    default void openLink(URI url) {
        if (url == null) {
            return;
        }
        if (this instanceof GuiScreen) {
            try {
                Method openWebLink = GuiScreen.class.getDeclaredMethod("openWebLink", URI.class);
                openWebLink.setAccessible(true);
                openWebLink.invoke(this, url);
                return;
            } catch (ReflectiveOperationException | SecurityException ignored) {
            }
        }
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(url);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
        }
    }
}
