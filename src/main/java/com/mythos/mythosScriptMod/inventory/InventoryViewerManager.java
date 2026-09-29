// 文件路径: src/main/java/com/keycommand2/mythosScriptMod/inventory/InventoryViewerManager.java
package com.mythos.mythosScriptMod.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class InventoryViewerManager {

    // 使用一个标准的IInventory实现来存储物品，大小为54（大箱子）
    private static final IInventory copiedInventory = new InventoryBasic("CopiedInventory", false, 54);
    private static String copiedTargetName = "";
    private static long copiedAt;
    private static int copyVersion;
    private static String lastCopyStatus = "尚未复制任何实体装备";
    /** The normal interaction reach is too short for a read-only crosshair lookup. */
    private static final double MIN_CROSSHAIR_RAY_DISTANCE = 512.0D;

    /**
     * 获取存储被复制物品的物品栏实例。
     * @return IInventory 实例
     */
    public static IInventory getCopiedInventory() {
        return copiedInventory;
    }

    /**
     * 核心逻辑：复制准星目标实体的装备。
     */
    public static synchronized boolean copyInventoryFromTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (player == null) {
            lastCopyStatus = "当前没有可用的玩家物品栏";
            return false;
        }

        Entity targetEntity = findCrosshairTarget(mc, player);
        if (!(targetEntity instanceof EntityLivingBase)) {
            lastCopyStatus = "准星未对准可查看装备的实体，保留上次快照";
            player.sendMessage(new TextComponentString(TextFormatting.RED + "[装备查看器] " + lastCopyStatus));
            return false;
        }

        EntityLivingBase target = (EntityLivingBase) targetEntity;
        copiedInventory.clear();
        // Only player inventory contents already synchronized to this client are available.
        if (target instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) target;
            for (int i = 0; i < targetPlayer.inventory.mainInventory.size() && i < 36; i++) {
                copiedInventory.setInventorySlotContents(i, targetPlayer.inventory.mainInventory.get(i).copy());
            }
        }
        EntityEquipmentSlot[] slots = { EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET, EntityEquipmentSlot.OFFHAND,
                EntityEquipmentSlot.MAINHAND };
        for (int i = 0; i < slots.length; i++) {
            copiedInventory.setInventorySlotContents(45 + i, target.getItemStackFromSlot(slots[i]).copy());
        }

        copiedTargetName = target.getName();
        copiedAt = System.currentTimeMillis();
        copyVersion++;
        lastCopyStatus = "已复制实体 " + copiedTargetName + " 的装备";
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[装备查看器] " + lastCopyStatus));
        return true;
    }

    /**
     * Finds the entity actually under the player's crosshair without using the
     * vanilla interaction-distance limit used to populate Minecraft#pointedEntity.
     * Blocks still occlude entities, and the closest collidable entity wins just
     * like the normal mouse-over calculation.
     */
    private static Entity findCrosshairTarget(Minecraft mc, EntityPlayerSP player) {
        if (mc == null || mc.world == null || player == null) {
            return null;
        }

        float partialTicks = mc.getRenderPartialTicks();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) camera = player;
        Vec3d start = camera.getPositionEyes(partialTicks);
        Vec3d look = camera.getLook(partialTicks);
        if (start == null || look == null) {
            return null;
        }

        double rayDistance = MIN_CROSSHAIR_RAY_DISTANCE;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == null || entity == player || entity == camera) {
                continue;
            }
            double entityDistance = Math.sqrt(start.squareDistanceTo(
                    new Vec3d(entity.posX, entity.posY, entity.posZ)));
            rayDistance = Math.max(rayDistance, entityDistance + 4.0D);
        }

        Vec3d end = start.add(look.scale(rayDistance));
        RayTraceResult blockHit = mc.world.rayTraceBlocks(start, end, false, true, false);
        double maxDistanceSq = rayDistance * rayDistance;
        if (blockHit != null && blockHit.hitVec != null) {
            end = blockHit.hitVec;
            maxDistanceSq = start.squareDistanceTo(end);
        }

        return findClosestEntity(mc.world.loadedEntityList, player, camera, start, end, maxDistanceSq);
    }

    static Entity findClosestEntity(List<Entity> entities, Entity player, Entity camera,
                                    Vec3d start, Vec3d end, double maxDistanceSq) {
        Entity closest = null;
        double closestDistanceSq = maxDistanceSq;
        for (Entity entity : entities) {
            if (entity == null || entity == player || entity == camera
                    || (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) || !entity.canBeCollidedWith()) {
                continue;
            }
            AxisAlignedBB bounds = entity.getEntityBoundingBox();
            if (bounds == null) {
                continue;
            }
            bounds = bounds.grow(entity.getCollisionBorderSize());
            RayTraceResult entityHit = bounds.calculateIntercept(start, end);
            if (!bounds.contains(start) && (entityHit == null || entityHit.hitVec == null)) {
                continue;
            }
            double distanceSq = bounds.contains(start) ? 0.0D : start.squareDistanceTo(entityHit.hitVec);
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq;
                closest = entity;
            }
        }
        return closest;
    }

    public static synchronized void clearCopiedInventory() {
        copiedInventory.clear();
        copiedTargetName = "";
        copiedAt = 0L;
        copyVersion++;
        lastCopyStatus = "已清空实体装备快照";
    }

    public static synchronized String getCopiedTargetName() {
        return copiedTargetName;
    }

    public static synchronized long getCopiedAt() {
        return copiedAt;
    }

    public static synchronized int getCopyVersion() {
        return copyVersion;
    }

    public static synchronized String getLastCopyStatus() {
        return lastCopyStatus;
    }

    public static synchronized boolean hasCopiedInventory() {
        return !copiedTargetName.isEmpty();
    }
}

