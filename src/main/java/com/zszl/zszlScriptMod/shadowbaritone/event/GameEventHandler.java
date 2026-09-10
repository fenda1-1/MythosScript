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

package com.zszl.zszlScriptMod.shadowbaritone.event;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.*;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.type.EventState;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.IEventBus;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.listener.IGameEventListener;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Helper;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.Pair;
import com.zszl.zszlScriptMod.shadowbaritone.cache.CachedChunk;
import com.zszl.zszlScriptMod.shadowbaritone.cache.WorldProvider;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BlockStateInterface;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus, Helper {

    private static final long LISTENER_FAILURE_LOG_INTERVAL_MS = 5000L;

    private final Baritone baritone;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastListenerFailureLogTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedListenerFailureCounts = new ConcurrentHashMap<>();

    public GameEventHandler(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            try {
                baritone.bsi = new BlockStateInterface(baritone.getPlayerContext(), true);
            } catch (Exception ex) {
                baritone.bsi = null;
            }
        } else {
            baritone.bsi = null;
        }
        for (IGameEventListener l : listeners) {
            try {
                l.onTick(event);
            } catch (Throwable t) {
                logListenerFailure("onTick", l, t);
            }
        }
    }

    @Override
    public void onPostTick(TickEvent event) {
        for (IGameEventListener l : listeners) {
            try {
                l.onPostTick(event);
            } catch (Throwable t) {
                logListenerFailure("onPostTick", l, t);
            }
        }
    }

    private void logListenerFailure(String phase, IGameEventListener listener, Throwable throwable) {
        String listenerName = listener.getClass().getName();
        String exceptionType = throwable.getClass().getName();
        String exceptionMessage = String.valueOf(throwable.getMessage());
        String throttleKey = phase + "|" + listenerName + "|" + exceptionType + "|" + exceptionMessage;
        long now = System.currentTimeMillis();

        Long lastLoggedAt = lastListenerFailureLogTimes.get(throttleKey);
        if (lastLoggedAt != null && now - lastLoggedAt < LISTENER_FAILURE_LOG_INTERVAL_MS) {
            suppressedListenerFailureCounts.merge(throttleKey, 1, Integer::sum);
            return;
        }

        lastListenerFailureLogTimes.put(throttleKey, now);
        int suppressedCount = suppressedListenerFailureCounts.getOrDefault(throttleKey, 0);
        suppressedListenerFailureCounts.remove(throttleKey);

        StringBuilder message = new StringBuilder()
                .append("[ShadowBaritone/EventBus] ")
                .append(phase)
                .append(" listener failed: ")
                .append(listenerName);
        if (suppressedCount > 0) {
            message.append(" (suppressed ").append(suppressedCount).append(" repeats)");
        }

        zszlScriptMod.LOGGER.warn(message.toString(), throwable);
    }

    @Override
    public final void onPlayerUpdate(PlayerUpdateEvent event) {
        listeners.forEach(l -> l.onPlayerUpdate(event));
    }

    @Override
    public final void onSendChatMessage(ChatEvent event) {
        listeners.forEach(l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        listeners.forEach(l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        EventState state = event.getState();
        ChunkEvent.Type type = event.getType();

        World world = baritone.getPlayerContext().world();

        // Whenever the server sends us to another dimension, chunks are unloaded
        // technically after the new world has been loaded, so we perform a check
        // to make sure the chunk being unloaded is already loaded.
        boolean isPreUnload = state == EventState.PRE
                && type == ChunkEvent.Type.UNLOAD
                && world.getChunkProvider().isChunkGeneratedAt(event.getX(), event.getZ());

        if (event.isPostPopulate() || isPreUnload) {
            baritone.getWorldProvider().ifWorldLoaded(worldData -> {
                Chunk chunk = world.getChunkFromChunkCoords(event.getX(), event.getZ());
                worldData.getCachedWorld().queueForPacking(chunk);
            });
        }

        listeners.forEach(l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (Baritone.settings().repackOnAnyBlockChange.value) {
            final boolean keepingTrackOf = event.getBlocks().stream()
                    .map(Pair::second).map(IBlockState::getBlock)
                    .anyMatch(CachedChunk.BLOCKS_TO_KEEP_TRACK_OF::contains);

            if (keepingTrackOf) {
                baritone.getWorldProvider().ifWorldLoaded(worldData -> {
                    final World world = baritone.getPlayerContext().world();
                    ChunkPos pos = event.getChunkPos();
                    worldData.getCachedWorld().queueForPacking(world.getChunkFromChunkCoords(pos.x, pos.z));
                });
            }
        }

        listeners.forEach(l -> l.onBlockChange(event));
    }

    @Override
    public final void onRenderPass(RenderEvent event) {
        listeners.forEach(l -> l.onRenderPass(event));
    }

    @Override
    public final void onWorldEvent(WorldEvent event) {
        WorldProvider cache = baritone.getWorldProvider();

        if (event.getState() == EventState.POST) {
            cache.closeWorld();
            if (event.getWorld() != null) {
                cache.initWorld(event.getWorld());
            }
        }

        listeners.forEach(l -> l.onWorldEvent(event));
    }

    @Override
    public final void onSendPacket(PacketEvent event) {
        listeners.forEach(l -> l.onSendPacket(event));
    }

    @Override
    public final void onReceivePacket(PacketEvent event) {
        listeners.forEach(l -> l.onReceivePacket(event));
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        listeners.forEach(l -> l.onPlayerRotationMove(event));
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        listeners.forEach(l -> l.onPlayerSprintState(event));
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        listeners.forEach(l -> l.onBlockInteract(event));
    }

    @Override
    public void onPlayerDeath() {
        listeners.forEach(IGameEventListener::onPlayerDeath);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        listeners.forEach(l -> l.onPathEvent(event));
    }

    @Override
    public final void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
