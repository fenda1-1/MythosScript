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

package com.mythos.mythosScriptMod.shadowbaritone;

import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritoneProvider;
import com.mythos.mythosScriptMod.shadowbaritone.api.cache.IWorldScanner;
import com.mythos.mythosScriptMod.shadowbaritone.api.command.ICommandSystem;
import com.mythos.mythosScriptMod.shadowbaritone.api.schematic.ISchematicSystem;
import com.mythos.mythosScriptMod.shadowbaritone.cache.FasterWorldScanner;
import com.mythos.mythosScriptMod.shadowbaritone.command.CommandSystem;
import com.mythos.mythosScriptMod.shadowbaritone.command.ExampleBaritoneControl;
import com.mythos.mythosScriptMod.shadowbaritone.utils.schematic.SchematicSystem;
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
    // Baritones pinned to Nexus bot sessions. Deliberately kept out of `all` so
    // the vanilla mixin tick does not drive them with the mounted session's
    // fields — NexusTickBridge ticks each inside its own withContext.
    private final java.util.Map<String, IBaritone> sessionBaritones = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** One baritone per bot session, keyed by username. */
    public synchronized IBaritone sessionBaritone(String key,
            java.util.function.Supplier<net.minecraft.client.entity.EntityPlayerSP> player,
            java.util.function.Supplier<net.minecraft.world.World> world) {
        return sessionBaritones.computeIfAbsent(key, k -> {
            com.mythos.mythosScriptMod.shadowbaritone.utils.player.SessionPlayerContext ctx =
                    new com.mythos.mythosScriptMod.shadowbaritone.utils.player.SessionPlayerContext(
                            Minecraft.getMinecraft(), player, world);
            Baritone b = new Baritone(Minecraft.getMinecraft(), ctx);
            ctx.bind(b);
            return b;
        });
    }

    @Override
    public IBaritone getBaritoneForPlayer(net.minecraft.client.entity.EntityPlayerSP player) {
        if (player != null) {
            String name = player.getName();
            IBaritone b = sessionBaritones.get(name);
            if (b != null) return b;
            // Nexus: lazily pin this session (including the mounted/human one)
            // to its own player/world suppliers so ctx.world() never follows
            // the mc.world swap on a mount switch — that swap otherwise makes
            // WorldProvider tear down the running path's cached world.
            // Absent Nexus this is a no-op and falls through to primary.
            try {
                if (com.mythos.mythosScriptMod.mcp.NexusSessions.present()) {
                    final Object session =
                            com.mythos.mythosScriptMod.mcp.NexusSessions.resolveForPlayer(player);
                    if (session != null) {
                        return sessionBaritone(name,
                                () -> com.mythos.mythosScriptMod.mcp.NexusSessions.player(session),
                                () -> com.mythos.mythosScriptMod.mcp.NexusSessions.world(session));
                    }
                }
            } catch (Throwable ignored) {}
        }
        return IBaritoneProvider.super.getBaritoneForPlayer(player);
    }

    @Override
    public IBaritone getBaritoneForConnection(net.minecraft.client.network.NetHandlerPlayClient connection) {
        for (IBaritone baritone : sessionBaritones.values()) {
            net.minecraft.client.entity.EntityPlayerSP p = baritone.getPlayerContext().player();
            if (p != null && p.connection == connection) return baritone;
        }
        return IBaritoneProvider.super.getBaritoneForConnection(connection);
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
