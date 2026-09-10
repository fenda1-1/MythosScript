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

package com.zszl.zszlScriptMod.shadowbaritone;

import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritoneProvider;
import com.zszl.zszlScriptMod.shadowbaritone.api.cache.IWorldScanner;
import com.zszl.zszlScriptMod.shadowbaritone.api.command.ICommandSystem;
import com.zszl.zszlScriptMod.shadowbaritone.api.schematic.ISchematicSystem;
import com.zszl.zszlScriptMod.shadowbaritone.cache.FasterWorldScanner;
import com.zszl.zszlScriptMod.shadowbaritone.command.CommandSystem;
import com.zszl.zszlScriptMod.shadowbaritone.command.ExampleBaritoneControl;
import com.zszl.zszlScriptMod.shadowbaritone.utils.schematic.SchematicSystem;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class BaritoneProvider implements IBaritoneProvider {

    private final List<IBaritone> all;
    private final List<IBaritone> allView;

    public BaritoneProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);

        // Setup chat control, just for the primary instance
        final Baritone primary = (Baritone) this.createBaritone(Minecraft.getMinecraft());
        primary.registerBehavior(new ExampleBaritoneControl(primary));
    }

    @Override
    public IBaritone getPrimaryBaritone() {
        return this.all.get(0);
    }

    @Override
    public List<IBaritone> getAllBaritones() {
        return this.allView;
    }

    @Override
    public synchronized IBaritone createBaritone(Minecraft minecraft) {
        IBaritone baritone = this.getBaritoneForMinecraft(minecraft);
        if (baritone == null) {
            this.all.add(baritone = new Baritone(minecraft));
        }
        return baritone;
    }

    @Override
    public synchronized boolean destroyBaritone(IBaritone baritone) {
        return baritone != this.getPrimaryBaritone() && this.all.remove(baritone);
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
