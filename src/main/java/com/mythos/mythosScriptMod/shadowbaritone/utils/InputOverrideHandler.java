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

package com.mythos.mythosScriptMod.shadowbaritone.utils;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.TickEvent;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.IInputOverrideHandler;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.behavior.Behavior;
import net.minecraft.util.MovementInputFromOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * An interface with the game's control system allowing the ability to
 * force down certain controls, having the same effect as if we were actually
 * physically forcing down the assigned key.
 *
 * @author Brady
 * @since 7/31/2018
 */
public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {

    /**
     * Maps inputs to whether or not we are forcing their state down.
     */
    private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();

    private final BlockBreakHelper blockBreakHelper;
    private final BlockPlaceHelper blockPlaceHelper;

    public InputOverrideHandler(Baritone baritone) {
        super(baritone);
        this.blockBreakHelper = new BlockBreakHelper(baritone.getPlayerContext());
        this.blockPlaceHelper = new BlockPlaceHelper(baritone.getPlayerContext());
    }

    /**
     * Returns whether or not we are forcing down the specified {@link Input}.
     *
     * @param input The input
     * @return Whether or not it is being forced down
     */
    @Override
    public final boolean isInputForcedDown(Input input) {
        return input == null ? false : this.inputForceStateMap.getOrDefault(input, false);
    }

    /**
     * Sets whether or not the specified {@link Input} is being forced down.
     *
     * @param input  The {@link Input}
     * @param forced Whether or not the state is being forced
     */
    @Override
    public final void setInputForceState(Input input, boolean forced) {
        this.inputForceStateMap.put(input, forced);
    }

    /**
     * Clears the override state for all keys
     */
    @Override
    public final void clearAllKeys() {
        this.inputForceStateMap.clear();
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        if (isInputForcedDown(Input.CLICK_LEFT)) {
            setInputForceState(Input.CLICK_RIGHT, false);
        }
        blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT));
        blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT));

        net.minecraft.client.entity.EntityPlayerSP p = ctx.player();
        if (p == null || p.movementInput == null) {
            mythos$dbg("onTick: player/movementInput null, skip (player=%s)", p);
            return;
        }
        // Only the baritone that owns this player may touch its movementInput.
        // The primary baritone's ctx.player() is the SHARED mc.player — during
        // a live-viewport render pass that field is briefly swapped to a
        // background bot, and the primary's onTick would then see that bot's
        // PlayerMovementInput, decide "not in control" (primary is never in
        // control), and tear it back to MovementInputFromOptions mid-path —
        // the "background bot stops chasing" ping-pong. Gate by ownership:
        // session baritones already resolve ctx.player() to their own pinned
        // player so this check only ever filters the primary.
        if (baritone == BaritoneAPI.getProvider().getPrimaryBaritone()) {
            net.minecraft.client.entity.EntityPlayerSP fg =
                    com.mythos.mythosScriptMod.mcp.NexusSessions.foregroundPlayer();
            if (fg != null && p != fg) {
                // mc.player is a swapped-in background bot right now — not ours.
                return;
            }
        }

        boolean control = inControl();
        boolean ours = p.movementInput.getClass() == PlayerMovementInput.class;
        if (control) {
            if (!ours) {
                mythos$dbg("movementInput -> PlayerMovementInput (player=%s old=%s)",
                        p.getName(), p.movementInput.getClass().getSimpleName());
                p.movementInput = new PlayerMovementInput(this);
            }
        } else {
            if (ours) { // allow other movement inputs that aren't this one
                mythos$dbg("movementInput -> MovementInputFromOptions (player=%s)",
                        p.getName());
                p.movementInput = new MovementInputFromOptions(ctx.minecraft().gameSettings);
            }
        }
        // only set it if it was previously incorrect
        // gotta do it this way, or else it constantly thinks you're beginning a double
        // tap W sprint lol
    }

    /**
     * True while this baritone owns the player's movementInput. A session
     * (bot) baritone used to be "always in control" so background pathing
     * kept working — but once that bot is mounted its player IS the live
     * mc.player and PlayerMovementInput eats the real keyboard. Bots only
     * stay in control while their player is NOT the mounted one.
     */
    private boolean inControl() {
        for (Input input : new Input[] { Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT,
                Input.SNEAK, Input.JUMP }) {
            if (isInputForcedDown(input)) {
                return true;
            }
        }
        if (baritone.getPathingBehavior().isPathing()) {
            return true;
        }
        net.minecraft.client.entity.EntityPlayerSP mine = ctx.player();
        // Session baritone: when actively pathing / forcing inputs (checked
        // above) it owns movementInput even while mounted — the mounted bot
        // keeps autopiloting. When idle, a mounted bot hands movementInput
        // back to the real keyboard so the human can steer it manually; a
        // background bot keeps PlayerMovementInput ready for the next path.
        if (baritone != BaritoneAPI.getProvider().getPrimaryBaritone()) {
            if (mine == null) return false;
            // A mounted bot keeps autopiloting — its PlayerMovementInput stays
            // installed so baritone drives it in first person; the human only
            // steers when the bot is idle (pathing already returned above).
            net.minecraft.client.entity.EntityPlayerSP fg =
                    com.mythos.mythosScriptMod.mcp.NexusSessions.foregroundPlayer();
            return fg == null || mine != fg;
        }
        return false;
    }

    private static java.lang.reflect.Method nexusLogBaritoneM;
    private static boolean nexusLogTried;

    private static void mythos$dbg(String fmt, Object... a) {
        try {
            if (!nexusLogTried) {
                nexusLogTried = true;
                nexusLogBaritoneM = Class.forName("com.mythos.nexus.core.NexusLog")
                        .getMethod("baritone", String.class, Object[].class);
            }
            if (nexusLogBaritoneM != null)
                nexusLogBaritoneM.invoke(null, String.format(fmt, a), new Object[0]);
        } catch (Throwable ignored) {
            // Nexus absent — debug logging is optional
        }
    }

    public BlockBreakHelper getBlockBreakHelper() {
        return blockBreakHelper;
    }

    float getPlayerYaw() {
        net.minecraft.client.entity.EntityPlayerSP p = ctx.player();
        return p != null ? p.rotationYaw : 0.0F;
    }

    float getMovementYaw() {
        return baritone.getLookBehavior()
                .getEffectiveRotation()
                .map(rotation -> rotation.getYaw())
                .orElseGet(this::getPlayerYaw);
    }
}
