package com.zszl.zszlScriptMod.path;

import com.zszl.zszlScriptMod.handlers.ItemFilterHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.ItemStack;

/** Common metadata and target checks for inventory drops and experience orbs. */
public final class DroppedPickupTarget {
    public static final String EXPERIENCE_ORB_ID = "minecraft:xp_orb";
    public static final String EXPERIENCE_ORB_TYPE = "experience_orb";
    public static final String EXPERIENCE_ORB_NAME = "经验球";

    private DroppedPickupTarget() {
    }

    public static boolean isSupported(Entity entity) {
        return entity instanceof EntityItem || entity instanceof EntityXPOrb;
    }

    /** Supported and still present; callers may apply their own motion rule. */
    public static boolean isAlive(Entity entity) {
        return isSupported(entity) && !entity.isDead;
    }

    public static boolean isExperienceOrb(Entity entity) {
        return entity instanceof EntityXPOrb;
    }

    /**
     * Ordinary item pickup keeps its legacy grounded-only scan rule. XP orbs
     * are allowed while airborne because vanilla collects them through entity
     * collision rather than an item stack entity.
     */
    public static boolean isEligibleForScan(Entity entity) {
        if (!isAlive(entity)) {
            return false;
        }
        return !(entity instanceof EntityItem) || entity.onGround;
    }

    public static ItemStack getItemStack(Entity entity) {
        return entity instanceof EntityItem ? ((EntityItem) entity).getItem() : ItemStack.EMPTY;
    }

    public static String getDisplayName(Entity entity) {
        if (entity instanceof EntityXPOrb) {
            return EXPERIENCE_ORB_NAME;
        }
        ItemStack stack = getItemStack(entity);
        return stack == null || stack.isEmpty() ? "" : stack.getDisplayName();
    }

    public static String getRegistryName(Entity entity) {
        if (entity instanceof EntityXPOrb) {
            return EXPERIENCE_ORB_ID;
        }
        ItemStack stack = getItemStack(entity);
        if (stack == null || stack.isEmpty() || stack.getItem() == null
                || stack.getItem().getRegistryName() == null) {
            return "";
        }
        return stack.getItem().getRegistryName().toString();
    }

    public static String getTypeName(Entity entity) {
        return entity instanceof EntityXPOrb ? EXPERIENCE_ORB_TYPE : "item";
    }

    public static int getCount(Entity entity) {
        if (entity instanceof EntityXPOrb) {
            return 1;
        }
        ItemStack stack = getItemStack(entity);
        return stack == null || stack.isEmpty() ? 0 : stack.getCount();
    }

    public static int getExperienceValue(Entity entity) {
        return entity instanceof EntityXPOrb ? ((EntityXPOrb) entity).getXpValue() : 0;
    }

    /** Search text used by the legacy name/NBT keyword lists. */
    public static String getSearchableText(Entity entity) {
        if (entity instanceof EntityXPOrb) {
            return EXPERIENCE_ORB_NAME + " " + EXPERIENCE_ORB_ID
                    + " xp_orb experience_orb experience orb xp";
        }
        ItemStack stack = getItemStack(entity);
        return stack == null || stack.isEmpty() ? "" : ItemFilterHandler.buildItemSearchableText(stack);
    }

    public static double distanceSq(Entity entity, Entity other) {
        return entity == null || other == null ? Double.MAX_VALUE : entity.getDistanceSq(other);
    }
}
