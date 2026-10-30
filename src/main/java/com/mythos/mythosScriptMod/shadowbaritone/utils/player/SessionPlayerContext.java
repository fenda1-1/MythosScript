package com.mythos.mythosScriptMod.shadowbaritone.utils.player;

import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.cache.IWorldData;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.IPlayerContext;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.IPlayerController;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.RayTraceUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * IPlayerContext pinned to a specific bot session's player/world instead of the
 * live Minecraft singleton fields. Identifies this baritone via
 * getBaritoneForPlayer even when a different session is mounted; during the
 * session's withContext tick the pinned values equal mc's anyway.
 */
public final class SessionPlayerContext implements IPlayerContext {

    private final Minecraft mc;
    private final Supplier<EntityPlayerSP> player;
    private final Supplier<World> world;
    private final IPlayerController playerController;
    private Baritone baritone;

    public SessionPlayerContext(Minecraft mc,
                                Supplier<EntityPlayerSP> player, Supplier<World> world) {
        this.mc = mc;
        this.player = player;
        this.world = world;
        this.playerController = new BaritonePlayerController(mc);
    }

    public void bind(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public Minecraft minecraft() {
        return mc;
    }

    @Override
    public EntityPlayerSP player() {
        return player.get();
    }

    @Override
    public IPlayerController playerController() {
        return playerController;
    }

    @Override
    public World world() {
        return world.get();
    }

    @Override
    public IWorldData worldData() {
        return baritone.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = mc.getRenderViewEntity();
        EntityPlayerSP p = player.get();
        if (entity != null && p != null && entity.getEntityWorld() == p.getEntityWorld()) {
            return BetterBlockPos.from(new BlockPos(entity));
        }
        return p == null ? null : BetterBlockPos.from(p.getPosition());
    }

    @Override
    public Rotation playerRotations() {
        // During the first tick after a bot connects the session's player field
        // is still null; the default impl would NPE on player().rotationYaw.
        if (player.get() == null) return new Rotation(0, 0);
        return baritone.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public RayTraceResult objectMouseOver() {
        if (player() == null) return null;
        return RayTraceUtils.rayTraceTowards(player(), playerRotations(), playerController().getBlockReachDistance());
    }
}
