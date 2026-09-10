package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.shadowbaritone.api.BaritoneAPI;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.IBaritoneProvider;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.ChatEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.PlayerUpdateEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.RenderEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.TickEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.WorldEvent;
import com.zszl.zszlScriptMod.shadowbaritone.api.event.events.type.EventState;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.behavior.PathingBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.BiFunction;

public class ShadowBaritoneForgeBridge {

    private final Minecraft mc = Minecraft.getMinecraft();
    private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;
    private WorldClient lastWorld;
    private long lastOutTickWarnAt = 0L;

    @SubscribeEvent
    public void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        // Tick/world/player update events are already dispatched from MixinMinecraft.
        // Re-dispatching them here drives Baritone twice per client tick, which can
        // desynchronize path execution and block breaking input.
    }

    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        // Player update PRE/POST events are dispatched from MixinMinecraft around
        // WorldClient.updateEntities(), after Baritone has already chosen this tick's
        // look target and before the POST restore runs.
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        if (mc.player == null || event.getMessage() == null) {
            return;
        }
        IBaritoneProvider provider;
        try {
            provider = BaritoneAPI.getProvider();
        } catch (Throwable ignored) {
            return;
        }
        if (provider == null) {
            return;
        }
        try {
            IBaritone baritone = provider.getBaritoneForPlayer(mc.player);
            if (baritone == null) {
                return;
            }
            ChatEvent chatEvent = new ChatEvent(event.getMessage());
            baritone.getGameEventHandler().onSendChatMessage(chatEvent);
            if (chatEvent.isCancelled()) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            zszlScriptMod.LOGGER.error("[DBG][ForgeBridge] onClientChat failed", t);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        IBaritoneProvider provider;
        try {
            provider = BaritoneAPI.getProvider();
        } catch (Throwable ignored) {
            return;
        }
        if (provider == null) {
            return;
        }
        for (IBaritone baritone : provider.getAllBaritones()) {
            try {
                baritone.getGameEventHandler().onRenderPass(new RenderEvent(event.getPartialTicks()));
            } catch (Throwable ignored) {
            }
        }
    }

    private void fireTick(IBaritoneProvider provider, EventState state) {
        if (tickProvider == null) {
            return;
        }
        for (IBaritone baritone : provider.getAllBaritones()) {
            try {
                TickEvent.Type type = getTickType(baritone);
                if (type == TickEvent.Type.OUT && baritone.getCustomGoalProcess().getGoal() != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastOutTickWarnAt > 1000L) {
                        lastOutTickWarnAt = now;
                        zszlScriptMod.LOGGER.warn(
                                "[DBG][ForgeBridge] TickType=OUT while goal exists. playerNull={}, worldNull={}, goal={}",
                                baritone.getPlayerContext().player() == null,
                                baritone.getPlayerContext().world() == null,
                                baritone.getCustomGoalProcess().getGoal());
                    }
                }
                baritone.getGameEventHandler().onTick(tickProvider.apply(state, type));
            } catch (Throwable t) {
                zszlScriptMod.LOGGER.error("[DBG][ForgeBridge] fireTick failed", t);
            }
        }
    }

    private void firePostTick(IBaritoneProvider provider, EventState state) {
        if (tickProvider == null) {
            return;
        }
        for (IBaritone baritone : provider.getAllBaritones()) {
            try {
                TickEvent.Type type = getTickType(baritone);
                baritone.getGameEventHandler().onPostTick(tickProvider.apply(state, type));
            } catch (Throwable t) {
                zszlScriptMod.LOGGER.error("[DBG][ForgeBridge] firePostTick failed", t);
            }
        }
    }

    private void firePlayerUpdate(IBaritoneProvider provider, EventState state) {
        if (mc.player == null) {
            return;
        }
        try {
            IBaritone baritone = provider.getBaritoneForPlayer(mc.player);
            if (baritone != null) {
                baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(state));
            }
        } catch (Throwable ignored) {
        }
    }

    private void fireWorldEventIfChanged(IBaritoneProvider provider) {
        WorldClient currentWorld = mc.world;
        if (currentWorld == lastWorld) {
            return;
        }
        try {
            provider.getPrimaryBaritone().getGameEventHandler()
                    .onWorldEvent(new WorldEvent(currentWorld, EventState.PRE));
            provider.getPrimaryBaritone().getGameEventHandler()
                    .onWorldEvent(new WorldEvent(currentWorld, EventState.POST));
        } catch (Throwable ignored) {
        }
        lastWorld = currentWorld;
    }

    private TickEvent.Type getTickType(IBaritone baritone) {
        return baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
                ? TickEvent.Type.IN
                : TickEvent.Type.OUT;
    }

    private void kickStartGoalPathing(IBaritoneProvider provider) {
        for (IBaritone baritone : provider.getAllBaritones()) {
            try {
                Goal goal = baritone.getCustomGoalProcess().getGoal();
                if (goal == null) {
                    continue;
                }
                if (baritone.getPathingBehavior().isPathing()
                        || baritone.getPathingBehavior().getInProgress().isPresent()) {
                    continue;
                }
                ((PathingBehavior) baritone.getPathingBehavior()).secretInternalSetGoalAndPath(
                        new PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH));
            } catch (Throwable ignored) {
            }
        }
    }
}
