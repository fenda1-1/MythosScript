package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Supplies short-lived, client-only collision walls for the active flight segment. */
public final class FlightCorridorCollisionHandler {

    public static final FlightCorridorCollisionHandler INSTANCE = new FlightCorridorCollisionHandler();

    private static final double WALL_THICKNESS = 0.25D;
    private static final long ACTIVE_TICKS = 6L;

    private volatile Object owner;
    private volatile Corridor corridor;

    private FlightCorridorCollisionHandler() {
    }

    public void activate(Object owner, Vec3d start, Vec3d end, double radius) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (owner == null || player == null || mc.world == null || start == null || end == null
                || !Baritone.settings().flightEntityCorridor.value) {
            clear(null);
            return;
        }
        double corridorRadius = Math.max(0.5D, radius);
        if (!isPlayerInside(player, start, end, corridorRadius)) {
            clear(owner);
            return;
        }
        this.owner = owner;
        this.corridor = new Corridor(buildWalls(player, start, end, corridorRadius), mc.world,
                mc.world.provider.getDimension(), mc.world.getTotalWorldTime() + ACTIVE_TICKS);
    }

    public void clear(Object owner) {
        if (owner != null && this.owner != owner) {
            return;
        }
        this.owner = null;
        this.corridor = null;
    }

    @SubscribeEvent
    public void onGetCollisionBoxes(GetCollisionBoxesEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        Corridor active = corridor;
        if (!Baritone.settings().flightEntityCorridor.value || player == null || mc.world == null || active == null
                || event.getWorld() != mc.world || event.getEntity() != player
                || active.world != mc.world
                || active.dimension != mc.world.provider.getDimension()
                || active.expiresAtTick < mc.world.getTotalWorldTime()) {
            return;
        }
        AxisAlignedBB query = event.getAabb();
        if (query == null) {
            return;
        }
        for (AxisAlignedBB wall : active.walls) {
            if (wall.intersects(query)) {
                event.getCollisionBoxesList().add(wall);
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Corridor active = corridor;
        Minecraft mc = Minecraft.getMinecraft();
        double opacity = Math.max(0.0D, Math.min(1.0D, Baritone.settings().entityCorridorOpacity.value));
        if (active == null || !Baritone.settings().flightEntityCorridor.value || opacity <= 0.001D
                || mc.player == null || mc.world != active.world) {
            return;
        }
        net.minecraft.entity.Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * event.getPartialTicks();
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * event.getPartialTicks();
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * event.getPartialTicks();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        for (AxisAlignedBB wall : active.walls) {
            RenderGlobal.drawSelectionBoundingBox(wall.offset(-x, -y, -z), 0.2F, 0.8F, 1.0F,
                    (float) opacity);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static List<AxisAlignedBB> buildWalls(EntityPlayerSP player, Vec3d start, Vec3d end, double radius) {
        double halfWidth = Math.max(0.3D, player.width / 2.0D);
        double height = Math.max(1.8D, player.height);
        List<AxisAlignedBB> walls = new ArrayList<>(6);
        if (Math.abs(end.y - start.y) > Math.abs(end.x - start.x) + Math.abs(end.z - start.z)) {
            addVerticalWalls(walls, start, end, radius, halfWidth, height);
        } else if (Math.abs(end.x - start.x) >= Math.abs(end.z - start.z)) {
            addXWalls(walls, start, end, radius, halfWidth, height);
        } else {
            addZWalls(walls, start, end, radius, halfWidth, height);
        }
        return Collections.unmodifiableList(walls);
    }

    private static boolean isPlayerInside(EntityPlayerSP player, Vec3d start, Vec3d end, double radius) {
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        if (Math.abs(end.y - start.y) > Math.abs(end.x - start.x) + Math.abs(end.z - start.z)) {
            return Math.abs(x - start.x) <= radius && Math.abs(z - start.z) <= radius
                    && y >= Math.min(start.y, end.y) && y <= Math.max(start.y, end.y) + 0.5D;
        }
        if (Math.abs(end.x - start.x) >= Math.abs(end.z - start.z)) {
            return x >= Math.min(start.x, end.x) - 0.5D && x <= Math.max(start.x, end.x) + 0.5D
                    && Math.abs(z - start.z) <= radius && Math.abs(y - start.y) <= radius;
        }
        return z >= Math.min(start.z, end.z) - 0.5D && z <= Math.max(start.z, end.z) + 0.5D
                && Math.abs(x - start.x) <= radius && Math.abs(y - start.y) <= radius;
    }

    private static void addXWalls(List<AxisAlignedBB> walls, Vec3d start, Vec3d end, double radius,
            double halfWidth, double height) {
        double minX = Math.min(start.x, end.x) - halfWidth;
        double maxX = Math.max(start.x, end.x) + halfWidth;
        double minZ = start.z - radius - halfWidth;
        double maxZ = start.z + radius + halfWidth;
        double minY = start.y - radius;
        double maxY = start.y + height + radius;
        walls.add(new AxisAlignedBB(minX, minY, minZ - WALL_THICKNESS, maxX, maxY, minZ));
        walls.add(new AxisAlignedBB(minX, minY, maxZ, maxX, maxY, maxZ + WALL_THICKNESS));
        walls.add(new AxisAlignedBB(minX, minY - WALL_THICKNESS, minZ, maxX, minY, maxZ));
        walls.add(new AxisAlignedBB(minX, maxY, minZ, maxX, maxY + WALL_THICKNESS, maxZ));
        walls.add(new AxisAlignedBB(minX - WALL_THICKNESS, minY, minZ, minX, maxY, maxZ));
        walls.add(new AxisAlignedBB(maxX, minY, minZ, maxX + WALL_THICKNESS, maxY, maxZ));
    }

    private static void addZWalls(List<AxisAlignedBB> walls, Vec3d start, Vec3d end, double radius,
            double halfWidth, double height) {
        double minZ = Math.min(start.z, end.z) - halfWidth;
        double maxZ = Math.max(start.z, end.z) + halfWidth;
        double minX = start.x - radius - halfWidth;
        double maxX = start.x + radius + halfWidth;
        double minY = start.y - radius;
        double maxY = start.y + height + radius;
        walls.add(new AxisAlignedBB(minX - WALL_THICKNESS, minY, minZ, minX, maxY, maxZ));
        walls.add(new AxisAlignedBB(maxX, minY, minZ, maxX + WALL_THICKNESS, maxY, maxZ));
        walls.add(new AxisAlignedBB(minX, minY - WALL_THICKNESS, minZ, maxX, minY, maxZ));
        walls.add(new AxisAlignedBB(minX, maxY, minZ, maxX, maxY + WALL_THICKNESS, maxZ));
        walls.add(new AxisAlignedBB(minX, minY, minZ - WALL_THICKNESS, maxX, maxY, minZ));
        walls.add(new AxisAlignedBB(minX, minY, maxZ, maxX, maxY, maxZ + WALL_THICKNESS));
    }

    private static void addVerticalWalls(List<AxisAlignedBB> walls, Vec3d start, Vec3d end, double radius,
            double halfWidth, double height) {
        double minX = start.x - radius - halfWidth;
        double maxX = start.x + radius + halfWidth;
        double minZ = start.z - radius - halfWidth;
        double maxZ = start.z + radius + halfWidth;
        double minY = Math.min(start.y, end.y);
        double maxY = Math.max(start.y, end.y) + height;
        walls.add(new AxisAlignedBB(minX - WALL_THICKNESS, minY, minZ, minX, maxY, maxZ));
        walls.add(new AxisAlignedBB(maxX, minY, minZ, maxX + WALL_THICKNESS, maxY, maxZ));
        walls.add(new AxisAlignedBB(minX, minY, minZ - WALL_THICKNESS, maxX, maxY, minZ));
        walls.add(new AxisAlignedBB(minX, minY, maxZ, maxX, maxY, maxZ + WALL_THICKNESS));
        walls.add(new AxisAlignedBB(minX, minY - WALL_THICKNESS, minZ, maxX, minY, maxZ));
        walls.add(new AxisAlignedBB(minX, maxY, minZ, maxX, maxY + WALL_THICKNESS, maxZ));
    }

    private static final class Corridor {
        private final List<AxisAlignedBB> walls;
        private final World world;
        private final int dimension;
        private final long expiresAtTick;

        private Corridor(List<AxisAlignedBB> walls, World world, int dimension, long expiresAtTick) {
            this.walls = walls;
            this.world = world;
            this.dimension = dimension;
            this.expiresAtTick = expiresAtTick;
        }
    }
}
