package com.zszl.zszlScriptMod.otherfeatures.handler.render;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.INpc;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemLingeringPotion;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSplashPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RenderFeatureSupport {

    private static final int MAX_BLOCK_SCAN_RESULTS = 384;
    private static final int BLOCK_SCAN_COMPLETED_COOLDOWN_TICKS = 10;
    private static final int BLOCK_SCAN_ACTIVE_COOLDOWN_TICKS = 1;
    private static final int ORE_SCAN_VERTICAL_RADIUS_LIMIT = 24;
    private static final int ORE_SCAN_POSITION_BUDGET = 4096;
    private static final int ORE_SCAN_REFRESH_INTERVAL_TICKS = 40;
    private static final double ORE_SCAN_CENTER_RESTART_DISTANCE_SQ = 4.0D;
    private static final List<BlockHighlightEntry> BLOCK_HIGHLIGHTS = new ArrayList<>();
    private static final List<BlockHighlightEntry> ORE_BLOCK_HIGHLIGHTS = new ArrayList<>();
    private static int blockScanCooldown = 0;
    private static RenderFrameData cachedRenderFrameData = null;
    private static int cachedRenderFrameTick = Integer.MIN_VALUE;
    private static int cachedRenderFrameDimension = Integer.MIN_VALUE;
    private static OreScanState oreScanState = OreScanState.EMPTY;

    private RenderFeatureSupport() {
    }

    static void clearRuntimeCaches() {
        BLOCK_HIGHLIGHTS.clear();
        ORE_BLOCK_HIGHLIGHTS.clear();
        blockScanCooldown = 0;
        cachedRenderFrameData = null;
        cachedRenderFrameTick = Integer.MIN_VALUE;
        cachedRenderFrameDimension = Integer.MIN_VALUE;
        oreScanState = OreScanState.EMPTY;
    }

    static void onClientTick(Minecraft mc, EntityPlayerSP player) {
        if (blockScanCooldown > 0) {
            blockScanCooldown--;
        }
        if (RenderFeatureManager.isEnabled("block_highlight") && blockScanCooldown <= 0) {
            updateBlockHighlights(mc, player);
            blockScanCooldown = isOreScanPending() ? BLOCK_SCAN_ACTIVE_COOLDOWN_TICKS : BLOCK_SCAN_COMPLETED_COOLDOWN_TICKS;
        } else if (!RenderFeatureManager.isEnabled("block_highlight")) {
            BLOCK_HIGHLIGHTS.clear();
            ORE_BLOCK_HIGHLIGHTS.clear();
            oreScanState = OreScanState.EMPTY;
        }
    }

    static void renderWorld(Minecraft mc, float partialTicks) {
        RenderFrameData frameData = getRenderFrameData(mc);
        if (RenderFeatureManager.isEnabled("entity_visual")) {
            renderEntityVisuals(mc, partialTicks, frameData);
        }
        if (RenderFeatureManager.isEnabled("player_skeleton")) {
            renderPlayerSkeletons(mc, partialTicks, frameData);
        }
        if (RenderFeatureManager.isEnabled("tracer_line")) {
            renderTracers(mc, partialTicks, frameData);
        }
        if (RenderFeatureManager.isEnabled("entity_tags")) {
            renderEntityTags(mc, partialTicks, frameData);
        }
        if (RenderFeatureManager.isEnabled("block_highlight")) {
            renderBlockHighlights(mc, partialTicks);
        }
        if (RenderFeatureManager.isEnabled("item_esp")) {
            renderItemEsp(mc, partialTicks, frameData);
        }
        if (RenderFeatureManager.isEnabled("trajectory_line")) {
            renderTrajectory(mc, partialTicks);
        }
        if (RenderFeatureManager.isEnabled("block_outline")) {
            renderBlockOutline(mc, partialTicks);
        }
    }

    static void renderCrosshair(Minecraft mc) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int centerX = scaledResolution.getScaledWidth() / 2;
        int centerY = scaledResolution.getScaledHeight() / 2;
        float attackPenalty = mc.player == null ? 0.0F : 1.0F - mc.player.getCooledAttackStrength(0.0F);
        double motion = mc.player == null ? 0.0D
                : Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
        int gap = RenderFeatureManager.crosshairDynamicGap
                ? 4 + Math.round((float) Math.min(3.0D, motion * 10.0D) + attackPenalty * 4.0F)
                : 4;
        int size = Math.round(RenderFeatureManager.crosshairSize);
        int thickness = Math.max(1, Math.round(RenderFeatureManager.crosshairThickness));
        int color = 0xFF000000 | (RenderFeatureManager.crosshairColorRgb & 0xFFFFFF);

        Gui.drawRect(centerX - thickness / 2, centerY - gap - size, centerX + (thickness + 1) / 2, centerY - gap, color);
        Gui.drawRect(centerX - thickness / 2, centerY + gap, centerX + (thickness + 1) / 2, centerY + gap + size, color);
        Gui.drawRect(centerX - gap - size, centerY - thickness / 2, centerX - gap, centerY + (thickness + 1) / 2, color);
        Gui.drawRect(centerX + gap, centerY - thickness / 2, centerX + gap + size, centerY + (thickness + 1) / 2, color);
        Gui.drawRect(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
    }

    static void renderRadar(Minecraft mc) {
        if (mc == null || mc.player == null || mc.fontRenderer == null) {
            return;
        }
        RenderFrameData frameData = getRenderFrameData(mc);

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int radarSize = clampInt(RenderFeatureManager.radarSize, 60, 180);
        int x = scaledResolution.getScaledWidth() - radarSize - 12;
        int y = 12;
        int centerX = x + radarSize / 2;
        int centerY = y + radarSize / 2;
        int radius = radarSize / 2 - 7;
        float maxDistance = Math.max(8.0F, RenderFeatureManager.radarMaxDistance);
        double scale = radius / maxDistance;

        Gui.drawRect(x, y, x + radarSize, y + radarSize, 0x8A0E141C);
        Gui.drawRect(x, y, x + radarSize, y + 1, 0xFF5FB8FF);
        Gui.drawRect(x, y + radarSize - 1, x + radarSize, y + radarSize, 0xFF35536C);
        Gui.drawRect(x, y, x + 1, y + radarSize, 0xFF35536C);
        Gui.drawRect(x + radarSize - 1, y, x + radarSize, y + radarSize, 0xFF35536C);
        Gui.drawRect(centerX - 1, y + 6, centerX + 1, y + radarSize - 6, 0x443F6D8F);
        Gui.drawRect(x + 6, centerY - 1, x + radarSize - 6, centerY + 1, 0x443F6D8F);
        Gui.drawRect(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow("雷达", x + 5, y + 4, 0xFFEAF6FF);
        mc.fontRenderer.drawStringWithShadow(String.valueOf((int) maxDistance), x + radarSize - 18, y + radarSize - 11,
                0xFFB8D8F2);

        double playerX = interpolate(mc.player.lastTickPosX, mc.player.posX, 1.0F);
        double playerZ = interpolate(mc.player.lastTickPosZ, mc.player.posZ, 1.0F);
        double yawRad = Math.toRadians(mc.player.rotationYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double maxDistanceSq = maxDistance * maxDistance;
        for (EntityRenderSample sample : frameData.livingEntities) {
            if (!sample.matches(RenderFeatureManager.radarPlayers, RenderFeatureManager.radarMonsters,
                    RenderFeatureManager.radarAnimals, maxDistanceSq)) {
                continue;
            }
            Entity entity = sample.entity;
            double dx = interpolate(entity.lastTickPosX, entity.posX, 1.0F) - playerX;
            double dz = interpolate(entity.lastTickPosZ, entity.posZ, 1.0F) - playerZ;
            double localX;
            double localZ;
            if (RenderFeatureManager.radarRotateWithView) {
                localX = dx * cos + dz * sin;
                localZ = dz * cos - dx * sin;
            } else {
                localX = dx;
                localZ = dz;
            }

            int px = centerX + (int) Math.round(localX * scale);
            int py = centerY + (int) Math.round(localZ * scale);
            int clampedX = clampInt(px, x + 5, x + radarSize - 5);
            int clampedY = clampInt(py, y + 5, y + radarSize - 5);
            int dotColor = getEntityHudColor(entity);
            Gui.drawRect(clampedX - 1, clampedY - 1, clampedX + 2, clampedY + 2, dotColor);
        }
    }

    static void renderGroundSpeed(Minecraft mc) {
        if (mc == null || mc.player == null || mc.fontRenderer == null) {
            return;
        }

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int radarSize = RenderFeatureManager.isEnabled("radar")
                ? clampInt(RenderFeatureManager.radarSize, 60, 180)
                : 0;
        double deltaX = mc.player.posX - mc.player.lastTickPosX;
        double deltaZ = mc.player.posZ - mc.player.lastTickPosZ;
        float groundSpeed = (float) (Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20.0D);
        String title = "地速";
        String value = RenderFeatureManager.formatGroundSpeed(groundSpeed)
                + (RenderFeatureManager.groundSpeedShowUnit ? " 格/秒" : "");
        int panelWidth = Math.max(72,
                Math.max(RenderFeatureManager.groundSpeedShowTitle ? mc.fontRenderer.getStringWidth(title) : 0,
                        mc.fontRenderer.getStringWidth(value)) + 10);
        int lineCount = RenderFeatureManager.groundSpeedShowTitle ? 2 : 1;
        int panelHeight = mc.fontRenderer.FONT_HEIGHT * lineCount + 8 + (RenderFeatureManager.groundSpeedShowTitle ? 2 : 0);
        int x = scaledResolution.getScaledWidth() - panelWidth - 12;
        int y = 12 + (radarSize > 0 ? radarSize + 8 : 0);
        y = Math.min(y, Math.max(12, scaledResolution.getScaledHeight() - panelHeight - 12));

        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0x8A0E141C);
        Gui.drawRect(x, y, x + panelWidth, y + 1, 0xFF5FB8FF);
        Gui.drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF35536C);
        Gui.drawRect(x, y, x + 1, y + panelHeight, 0xFF35536C);
        Gui.drawRect(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, 0xFF35536C);
        if (RenderFeatureManager.groundSpeedShowTitle) {
            mc.fontRenderer.drawStringWithShadow(title, x + 5, y + 3, 0xFFEAF6FF);
            mc.fontRenderer.drawStringWithShadow(value, x + 5, y + 5 + mc.fontRenderer.FONT_HEIGHT, 0xFFFFFFFF);
        } else {
            mc.fontRenderer.drawStringWithShadow(value, x + 5, y + 4, 0xFFFFFFFF);
        }
    }

    static void renderEntityInfo(Minecraft mc) {
        if (mc == null || mc.player == null || mc.fontRenderer == null || mc.objectMouseOver == null) {
            return;
        }
        RayTraceResult rayTraceResult = mc.objectMouseOver;
        if (rayTraceResult.typeOfHit != RayTraceResult.Type.ENTITY || rayTraceResult.entityHit == null) {
            return;
        }

        Entity entity = rayTraceResult.entityHit;
        if (entity.isDead || mc.player.getDistance(entity) > RenderFeatureManager.entityInfoMaxDistance) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("§b" + entity.getName() + " §7[" + entity.getClass().getSimpleName() + "]");
        if (RenderFeatureManager.entityInfoShowHealth && entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            lines.add("§c生命: §f" + formatFloat(Math.max(0.0F, living.getHealth())));
        }
        if (RenderFeatureManager.entityInfoShowDistance) {
            lines.add("§e距离: §f" + formatFloat(mc.player.getDistance(entity)) + "m");
        }
        if (RenderFeatureManager.entityInfoShowPosition) {
            lines.add("§a坐标: §f" + formatFloat((float) entity.posX) + ", "
                    + formatFloat((float) entity.posY) + ", " + formatFloat((float) entity.posZ));
        }
        if (RenderFeatureManager.entityInfoShowHeldItem && entity instanceof EntityLivingBase) {
            ItemStack heldItem = ((EntityLivingBase) entity).getHeldItemMainhand();
            if (heldItem != null && !heldItem.isEmpty()) {
                lines.add("§d手持: §f" + heldItem.getDisplayName());
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }
        int lineHeight = mc.fontRenderer.FONT_HEIGHT + 2;
        int panelX = 10;
        int panelY = 10;
        int panelWidth = maxWidth + 10;
        int panelHeight = lines.size() * lineHeight + 8;

        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA10161D);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF62C6FF);
        Gui.drawRect(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF35536C);
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRenderer.drawStringWithShadow(lines.get(i), panelX + 5, panelY + 4 + i * lineHeight, 0xFFFFFFFF);
        }
    }

    private static RenderFrameData getRenderFrameData(Minecraft mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            return RenderFrameData.EMPTY;
        }

        int tick = mc.player.ticksExisted;
        int dimension = mc.player.dimension;
        if (cachedRenderFrameData != null && cachedRenderFrameTick == tick && cachedRenderFrameDimension == dimension) {
            return cachedRenderFrameData;
        }

        double maxLivingDistanceSq = getMaxLivingDistanceSq();
        double maxSkeletonDistanceSq = RenderFeatureManager.isEnabled("player_skeleton")
                ? RenderFeatureManager.skeletonMaxDistance * RenderFeatureManager.skeletonMaxDistance
                : 0.0D;
        double maxItemDistanceSq = RenderFeatureManager.isEnabled("item_esp")
                ? RenderFeatureManager.itemEspMaxDistance * RenderFeatureManager.itemEspMaxDistance
                : 0.0D;

        boolean needLivingScan = maxLivingDistanceSq > 0.0D || maxSkeletonDistanceSq > 0.0D;
        boolean needItemScan = maxItemDistanceSq > 0.0D;
        if (!needLivingScan && !needItemScan) {
            cachedRenderFrameData = RenderFrameData.EMPTY;
            cachedRenderFrameTick = tick;
            cachedRenderFrameDimension = dimension;
            return cachedRenderFrameData;
        }

        List<EntityRenderSample> livingEntities = new ArrayList<>();
        List<EntityRenderSample> playerEntities = new ArrayList<>();
        List<ItemRenderSample> itemEntities = new ArrayList<>();

        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == null || entity == mc.player || entity.isDead) {
                continue;
            }

            if (needLivingScan && entity instanceof EntityLivingBase && entity.isEntityAlive()) {
                double distanceSq = mc.player.getDistanceSq(entity);
                boolean isPlayer = entity instanceof EntityPlayer;
                boolean isMonster = isMonsterEntity(entity);
                boolean isAnimal = isAnimalEntity(entity);

                if (maxLivingDistanceSq > 0.0D
                        && distanceSq <= maxLivingDistanceSq
                        && (isPlayer || isMonster || isAnimal)) {
                    livingEntities.add(new EntityRenderSample(entity, distanceSq, isPlayer, isMonster, isAnimal));
                }
                if (maxSkeletonDistanceSq > 0.0D && distanceSq <= maxSkeletonDistanceSq && isPlayer) {
                    playerEntities.add(new EntityRenderSample(entity, distanceSq, true, false, false));
                }
            }

            if (needItemScan && entity instanceof EntityItem) {
                EntityItem item = (EntityItem) entity;
                if (!item.onGround) {
                    continue;
                }
                double distanceSq = mc.player.getDistanceSq(item);
                if (distanceSq <= maxItemDistanceSq) {
                    itemEntities.add(new ItemRenderSample(item, distanceSq));
                }
            }
        }

        cachedRenderFrameData = new RenderFrameData(livingEntities, playerEntities, itemEntities);
        cachedRenderFrameTick = tick;
        cachedRenderFrameDimension = dimension;
        return cachedRenderFrameData;
    }

    private static double getMaxLivingDistanceSq() {
        double maxDistance = 0.0D;
        if (RenderFeatureManager.isEnabled("radar")) {
            maxDistance = Math.max(maxDistance, RenderFeatureManager.radarMaxDistance);
        }
        if (RenderFeatureManager.isEnabled("entity_visual")) {
            maxDistance = Math.max(maxDistance, RenderFeatureManager.entityVisualMaxDistance);
        }
        if (RenderFeatureManager.isEnabled("tracer_line")) {
            maxDistance = Math.max(maxDistance, RenderFeatureManager.tracerMaxDistance);
        }
        if (RenderFeatureManager.isEnabled("entity_tags")) {
            maxDistance = Math.max(maxDistance, RenderFeatureManager.entityTagMaxDistance);
        }
        return maxDistance <= 0.0D ? 0.0D : maxDistance * maxDistance;
    }

    private static boolean isMonsterEntity(Entity entity) {
        return entity instanceof IMob;
    }

    private static boolean isAnimalEntity(Entity entity) {
        return entity instanceof EntityAnimal
                || entity instanceof EntityAmbientCreature
                || entity instanceof EntitySquid
                || entity instanceof IMerchant
                || entity instanceof INpc;
    }

    private static float[] getEntityColor(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return new float[] { 0.22F, 0.86F, 1.0F };
        }
        if (isMonsterEntity(entity)) {
            return new float[] { 1.0F, 0.30F, 0.30F };
        }
        return new float[] { 0.35F, 1.0F, 0.48F };
    }

    private static int getEntityHudColor(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return 0xFF55E3FF;
        }
        if (isMonsterEntity(entity)) {
            return 0xFFFF6464;
        }
        return 0xFF66FF7A;
    }

    private static Vec3d getEntityCenter(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + entity.height * 0.5D;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static void renderEntityLabel(Minecraft mc, Entity entity, float partialTicks) {
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        List<String> lines = new ArrayList<>();
        lines.add(entity.getName());
        if (RenderFeatureManager.entityTagShowHealth) {
            lines.add("§cHP: §f" + formatFloat(Math.max(0.0F, living.getHealth())));
        }
        if (RenderFeatureManager.entityTagShowDistance) {
            lines.add("§b距离: §f" + formatFloat(mc.player.getDistance(entity)) + "m");
        }
        if (RenderFeatureManager.entityTagShowHeldItem) {
            ItemStack held = living.getHeldItemMainhand();
            if (held != null && !held.isEmpty()) {
                lines.add("§e手持: §f" + held.getDisplayName());
            }
        }
        drawWorldLabel(mc, getEntityCenter(entity, partialTicks).addVector(0.0D, entity.height * 0.45D, 0.0D),
                lines, 0xFFFFFFFF, true);
    }

    private static void renderItemLabel(Minecraft mc, EntityItem entityItem, float partialTicks) {
        List<String> lines = new ArrayList<>();
        if (RenderFeatureManager.itemEspShowName) {
            ItemStack stack = entityItem.getItem();
            lines.add(stack == null || stack.isEmpty() ? entityItem.getName() : stack.getDisplayName());
        }
        if (RenderFeatureManager.itemEspShowDistance) {
            lines.add("§e距离: §f" + formatFloat(mc.player.getDistance(entityItem)) + "m");
        }
        if (lines.isEmpty()) {
            return;
        }
        drawWorldLabel(mc, getEntityCenter(entityItem, partialTicks).addVector(0.0D, 0.35D, 0.0D),
                lines, 0xFFFFFFAA, RenderFeatureManager.itemEspThroughWalls);
    }

    private static void drawWorldLabel(Minecraft mc, Vec3d worldPos, List<String> lines, int textColor,
            boolean throughWalls) {
        RenderManager renderManager = mc.getRenderManager();
        if (mc.fontRenderer == null || renderManager == null || lines == null || lines.isEmpty()) {
            return;
        }

        double x = worldPos.x - renderManager.viewerPosX;
        double y = worldPos.y - renderManager.viewerPosY;
        double z = worldPos.z - renderManager.viewerPosZ;
        float scale = 0.026F;
        int lineHeight = mc.fontRenderer.FONT_HEIGHT + 1;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        float pitchFactor = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
        GlStateManager.rotate(renderManager.playerViewX * pitchFactor, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        if (throughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.depthMask(false);

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }

        int totalHeight = lines.size() * lineHeight;
        Gui.drawRect(-maxWidth / 2 - 3, -2, maxWidth / 2 + 3, totalHeight + 1, 0x66101010);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            mc.fontRenderer.drawStringWithShadow(line, -mc.fontRenderer.getStringWidth(line) / 2.0F,
                    i * lineHeight, textColor);
        }

        GlStateManager.depthMask(true);
        if (throughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int clampInt(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private static void renderEntityVisuals(Minecraft mc, float partialTicks, RenderFrameData frameData) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (RenderFeatureManager.entityVisualThroughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.glLineWidth(1.8F);

        double maxDistanceSq = RenderFeatureManager.entityVisualMaxDistance * RenderFeatureManager.entityVisualMaxDistance;
        for (EntityRenderSample sample : frameData.livingEntities) {
            if (!sample.matches(RenderFeatureManager.entityVisualPlayers, RenderFeatureManager.entityVisualMonsters,
                    RenderFeatureManager.entityVisualAnimals, maxDistanceSq)) {
                continue;
            }
            Entity entity = sample.entity;
            float[] color = getEntityColor(entity);
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(0.05D).offset(-viewerX, -viewerY, -viewerZ);
            if (RenderFeatureManager.entityVisualFilledBox) {
                RenderGlobal.renderFilledBox(box, color[0], color[1], color[2], 0.12F);
            }
            RenderGlobal.drawSelectionBoundingBox(box, color[0], color[1], color[2], 0.88F);
        }

        GlStateManager.glLineWidth(1.0F);
        if (RenderFeatureManager.entityVisualThroughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderPlayerSkeletons(Minecraft mc, float partialTicks, RenderFrameData frameData) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        double viewerX = interpolate(viewer.lastTickPosX, viewer.posX, partialTicks);
        double viewerY = interpolate(viewer.lastTickPosY, viewer.posY, partialTicks);
        double viewerZ = interpolate(viewer.lastTickPosZ, viewer.posZ, partialTicks);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (RenderFeatureManager.skeletonThroughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.glLineWidth(Math.max(1.0F, RenderFeatureManager.skeletonLineWidth));

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (EntityRenderSample sample : frameData.playerEntities) {
            Entity entity = sample.entity;
            EntityPlayer target = (EntityPlayer) entity;
            float swing = target.limbSwing - target.limbSwingAmount * (1.0F - partialTicks);
            float swingAmount = Math.min(1.0F, target.limbSwingAmount);
            double armSwing = Math.cos(swing * 0.6662F) * 0.16D * swingAmount;
            double legSwing = Math.cos(swing * 0.6662F + Math.PI) * 0.18D * swingAmount;
            double yawRad = Math.toRadians(interpolate(target.prevRotationYaw, target.rotationYaw, partialTicks));
            double px = interpolate(target.lastTickPosX, target.posX, partialTicks) - viewerX;
            double py = interpolate(target.lastTickPosY, target.posY, partialTicks) - viewerY;
            double pz = interpolate(target.lastTickPosZ, target.posZ, partialTicks) - viewerZ;
            double height = target.height;
            double shoulderY = height * 0.78D;
            double pelvisY = height * 0.52D;
            double headY = height * 1.02D;
            double armY = height * 0.54D;
            double footY = 0.05D;
            double shoulderX = target.width * 0.36D;
            double hipX = target.width * 0.18D;
            double armX = target.width * 0.52D;

            Vec3d pelvis = localToWorld(px, py, pz, 0.0D, pelvisY, 0.0D, yawRad);
            Vec3d neck = localToWorld(px, py, pz, 0.0D, shoulderY, 0.0D, yawRad);
            Vec3d head = localToWorld(px, py, pz, 0.0D, headY, 0.0D, yawRad);
            Vec3d leftShoulder = localToWorld(px, py, pz, -shoulderX, shoulderY, 0.0D, yawRad);
            Vec3d rightShoulder = localToWorld(px, py, pz, shoulderX, shoulderY, 0.0D, yawRad);
            Vec3d leftArm = localToWorld(px, py, pz, -armX, armY, armSwing, yawRad);
            Vec3d rightArm = localToWorld(px, py, pz, armX, armY, -armSwing, yawRad);
            Vec3d leftHip = localToWorld(px, py, pz, -hipX, pelvisY, 0.0D, yawRad);
            Vec3d rightHip = localToWorld(px, py, pz, hipX, pelvisY, 0.0D, yawRad);
            Vec3d leftLeg = localToWorld(px, py, pz, -hipX, footY, legSwing, yawRad);
            Vec3d rightLeg = localToWorld(px, py, pz, hipX, footY, -legSwing, yawRad);

            addLine(buffer, head, neck, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, neck, pelvis, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, leftShoulder, rightShoulder, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, neck, leftShoulder, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, neck, rightShoulder, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, leftShoulder, leftArm, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, rightShoulder, rightArm, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, pelvis, leftHip, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, pelvis, rightHip, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, leftHip, leftLeg, 0.30F, 0.88F, 1.0F, 0.92F);
            addLine(buffer, rightHip, rightLeg, 0.30F, 0.88F, 1.0F, 0.92F);
        }
        tessellator.draw();

        GlStateManager.glLineWidth(1.0F);
        if (RenderFeatureManager.skeletonThroughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static Vec3d localToWorld(double baseX, double baseY, double baseZ, double localX, double localY,
            double localZ, double yawRad) {
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double worldX = baseX + localX * cos - localZ * sin;
        double worldZ = baseZ + localX * sin + localZ * cos;
        return new Vec3d(worldX, baseY + localY, worldZ);
    }

    private static void addLine(BufferBuilder buffer, Vec3d start, Vec3d end, float red, float green, float blue,
            float alpha) {
        buffer.pos(start.x, start.y, start.z).color(red, green, blue, alpha).endVertex();
        buffer.pos(end.x, end.y, end.z).color(red, green, blue, alpha).endVertex();
    }

    private static void renderTracers(Minecraft mc, float partialTicks, RenderFrameData frameData) {
        Entity viewer = mc.getRenderViewEntity();
        if (!(viewer instanceof EntityPlayerSP)) {
            return;
        }

        Vec3d eyePosition = viewer.getPositionEyes(partialTicks);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        RenderManager renderManager = mc.getRenderManager();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (RenderFeatureManager.tracerThroughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.glLineWidth(RenderFeatureManager.tracerLineWidth);

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        double maxDistanceSq = RenderFeatureManager.tracerMaxDistance * RenderFeatureManager.tracerMaxDistance;
        for (EntityRenderSample sample : frameData.livingEntities) {
            if (!sample.matches(RenderFeatureManager.tracerPlayers, RenderFeatureManager.tracerMonsters,
                    RenderFeatureManager.tracerAnimals, maxDistanceSq)) {
                continue;
            }
            Entity entity = sample.entity;
            float[] color = getEntityColor(entity);
            Vec3d target = getEntityCenter(entity, partialTicks);
            buffer.pos(eyePosition.x - renderManager.viewerPosX, eyePosition.y - renderManager.viewerPosY,
                    eyePosition.z - renderManager.viewerPosZ).color(color[0], color[1], color[2], 0.82F).endVertex();
            buffer.pos(target.x - renderManager.viewerPosX, target.y - renderManager.viewerPosY,
                    target.z - renderManager.viewerPosZ).color(color[0], color[1], color[2], 0.82F).endVertex();
        }
        tessellator.draw();

        GlStateManager.glLineWidth(1.0F);
        if (RenderFeatureManager.tracerThroughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderEntityTags(Minecraft mc, float partialTicks, RenderFrameData frameData) {
        double maxDistanceSq = RenderFeatureManager.entityTagMaxDistance * RenderFeatureManager.entityTagMaxDistance;
        for (EntityRenderSample sample : frameData.livingEntities) {
            if (sample.matches(RenderFeatureManager.entityTagPlayers, RenderFeatureManager.entityTagMonsters,
                    RenderFeatureManager.entityTagAnimals, maxDistanceSq)) {
                renderEntityLabel(mc, sample.entity, partialTicks);
            }
        }
    }

    private static void renderItemEsp(Minecraft mc, float partialTicks, RenderFrameData frameData) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (RenderFeatureManager.itemEspThroughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.glLineWidth(1.6F);

        for (ItemRenderSample sample : frameData.itemEntities) {
            AxisAlignedBB box = sample.entityItem.getEntityBoundingBox().grow(0.03D).offset(-viewerX, -viewerY, -viewerZ);
            RenderGlobal.renderFilledBox(box, 1.0F, 0.88F, 0.22F, 0.10F);
            RenderGlobal.drawSelectionBoundingBox(box, 1.0F, 0.88F, 0.22F, 0.90F);
        }

        GlStateManager.glLineWidth(1.0F);
        if (RenderFeatureManager.itemEspThroughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        for (ItemRenderSample sample : frameData.itemEntities) {
            renderItemLabel(mc, sample.entityItem, partialTicks);
        }
    }

    private static void updateBlockHighlights(Minecraft mc, EntityPlayerSP player) {
        BLOCK_HIGHLIGHTS.clear();
        if (mc.world == null || player == null) {
            ORE_BLOCK_HIGHLIGHTS.clear();
            oreScanState = OreScanState.EMPTY;
            return;
        }

        float maxDistance = RenderFeatureManager.blockHighlightMaxDistance;
        double maxDistanceSq = maxDistance * maxDistance;

        if (RenderFeatureManager.blockHighlightStorages || RenderFeatureManager.blockHighlightSpawners) {
            for (TileEntity tileEntity : mc.world.loadedTileEntityList) {
                if (tileEntity == null || tileEntity.getPos() == null
                        || player.getDistanceSqToCenter(tileEntity.getPos()) > maxDistanceSq) {
                    continue;
                }
                if (RenderFeatureManager.blockHighlightStorages && isStorageTile(tileEntity)) {
                    addBlockHighlight(tileEntity.getPos(), 1.0F, 0.74F, 0.20F);
                } else if (RenderFeatureManager.blockHighlightSpawners && tileEntity instanceof TileEntityMobSpawner) {
                    addBlockHighlight(tileEntity.getPos(), 1.0F, 0.28F, 0.88F);
                }
            }
        }

        if (RenderFeatureManager.blockHighlightOres) {
            updateOreHighlights(mc, player, maxDistanceSq);
            for (BlockHighlightEntry entry : ORE_BLOCK_HIGHLIGHTS) {
                if (BLOCK_HIGHLIGHTS.size() >= MAX_BLOCK_SCAN_RESULTS) {
                    break;
                }
                BLOCK_HIGHLIGHTS.add(entry);
            }
        } else {
            ORE_BLOCK_HIGHLIGHTS.clear();
            oreScanState = OreScanState.EMPTY;
        }
    }

    private static void updateOreHighlights(Minecraft mc, EntityPlayerSP player, double maxDistanceSq) {
        BlockPos center = player.getPosition();
        int radius = Math.max(4, Math.round(RenderFeatureManager.blockHighlightMaxDistance));
        int verticalRadius = Math.min(radius, ORE_SCAN_VERTICAL_RADIUS_LIMIT);
        int configKey = buildOreScanConfigKey(radius, verticalRadius);
        int tick = player.ticksExisted;

        if (shouldRestartOreScan(player, center, radius, verticalRadius, configKey, tick)) {
            ORE_BLOCK_HIGHLIGHTS.clear();
            oreScanState = new OreScanState(center, radius, verticalRadius, configKey, 0, false, tick);
        }

        if (oreScanState.completed) {
            return;
        }

        int side = radius * 2 + 1;
        int verticalSide = verticalRadius * 2 + 1;
        int total = side * verticalSide * side;
        int scanned = 0;
        int index = oreScanState.nextIndex;

        while (index < total && scanned < ORE_SCAN_POSITION_BUDGET && ORE_BLOCK_HIGHLIGHTS.size() < MAX_BLOCK_SCAN_RESULTS) {
            int localIndex = index;
            int dx = localIndex % side - radius;
            localIndex /= side;
            int dy = localIndex % verticalSide - verticalRadius;
            int dz = localIndex / verticalSide - radius;

            BlockPos pos = center.add(dx, dy, dz);
            if (player.getDistanceSqToCenter(pos) <= maxDistanceSq && mc.world.isBlockLoaded(pos, false)) {
                IBlockState state = mc.world.getBlockState(pos);
                if (RenderFeatureManager.isOreBlock(state.getBlock())) {
                    float[] color = RenderFeatureManager.getBlockHighlightColor(state);
                    ORE_BLOCK_HIGHLIGHTS.add(new BlockHighlightEntry(pos, color[0], color[1], color[2]));
                }
            }

            index++;
            scanned++;
        }

        boolean completed = index >= total || ORE_BLOCK_HIGHLIGHTS.size() >= MAX_BLOCK_SCAN_RESULTS;
        oreScanState = new OreScanState(center, radius, verticalRadius, configKey, index, completed,
                completed ? tick : oreScanState.lastCompletedTick);
    }

    private static boolean shouldRestartOreScan(EntityPlayerSP player, BlockPos center, int radius, int verticalRadius,
            int configKey, int tick) {
        if (player == null) {
            return false;
        }
        if (oreScanState == OreScanState.EMPTY) {
            return true;
        }
        if (oreScanState.radius != radius || oreScanState.verticalRadius != verticalRadius || oreScanState.configKey != configKey) {
            return true;
        }
        if (oreScanState.center == null || oreScanState.center.distanceSq(center) > ORE_SCAN_CENTER_RESTART_DISTANCE_SQ) {
            return true;
        }
        return oreScanState.completed && tick - oreScanState.lastCompletedTick >= ORE_SCAN_REFRESH_INTERVAL_TICKS;
    }

    private static int buildOreScanConfigKey(int radius, int verticalRadius) {
        int result = 17;
        result = 31 * result + radius;
        result = 31 * result + verticalRadius;
        result = 31 * result + Boolean.valueOf(RenderFeatureManager.blockHighlightOres).hashCode();
        result = 31 * result + Float.valueOf(RenderFeatureManager.blockHighlightMaxDistance).hashCode();
        return result;
    }

    private static boolean isOreScanPending() {
        return RenderFeatureManager.isEnabled("block_highlight")
                && RenderFeatureManager.blockHighlightOres
                && oreScanState != OreScanState.EMPTY
                && !oreScanState.completed;
    }

    private static void renderBlockHighlights(Minecraft mc, float partialTicks) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null || BLOCK_HIGHLIGHTS.isEmpty()) {
            return;
        }

        double viewerX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double viewerY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double viewerZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (RenderFeatureManager.blockHighlightThroughWalls) {
            GlStateManager.disableDepth();
        }
        GlStateManager.glLineWidth(1.8F);

        for (BlockHighlightEntry entry : BLOCK_HIGHLIGHTS) {
            AxisAlignedBB box = new AxisAlignedBB(entry.pos).grow(0.003D).offset(-viewerX, -viewerY, -viewerZ);
            if (RenderFeatureManager.blockHighlightFilledBox) {
                RenderGlobal.renderFilledBox(box, entry.red, entry.green, entry.blue, 0.12F);
            }
            RenderGlobal.drawSelectionBoundingBox(box, entry.red, entry.green, entry.blue, 0.92F);
        }

        GlStateManager.glLineWidth(1.0F);
        if (RenderFeatureManager.blockHighlightThroughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderBlockOutline(Minecraft mc, float partialTicks) {
        if (mc == null || mc.world == null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK
                || mc.objectMouseOver.getBlockPos() == null) {
            return;
        }

        BlockPos pos = mc.objectMouseOver.getBlockPos();
        IBlockState state = mc.world.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.AIR) {
            return;
        }

        AxisAlignedBB selectedBox = state.getSelectedBoundingBox(mc.world, pos);
        if (selectedBox == null || selectedBox == Block.NULL_AABB) {
            return;
        }

        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null) {
            return;
        }

        double viewerX = interpolate(viewer.lastTickPosX, viewer.posX, partialTicks);
        double viewerY = interpolate(viewer.lastTickPosY, viewer.posY, partialTicks);
        double viewerZ = interpolate(viewer.lastTickPosZ, viewer.posZ, partialTicks);
        AxisAlignedBB box = selectedBox.grow(0.002D).offset(-viewerX, -viewerY, -viewerZ);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(Math.max(1.0F, RenderFeatureManager.blockOutlineLineWidth));
        if (RenderFeatureManager.blockOutlineFilledBox) {
            RenderGlobal.renderFilledBox(box, 0.22F, 0.72F, 1.0F, 0.12F);
        }
        RenderGlobal.drawSelectionBoundingBox(box, 0.22F, 0.72F, 1.0F, 0.95F);
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void addBlockHighlight(BlockPos pos, float red, float green, float blue) {
        if (pos != null && BLOCK_HIGHLIGHTS.size() < MAX_BLOCK_SCAN_RESULTS) {
            BLOCK_HIGHLIGHTS.add(new BlockHighlightEntry(pos, red, green, blue));
        }
    }

    private static boolean isStorageTile(TileEntity tileEntity) {
        return tileEntity instanceof TileEntityChest
                || tileEntity instanceof TileEntityEnderChest
                || tileEntity instanceof TileEntityShulkerBox;
    }

    private static void renderTrajectory(Minecraft mc, float partialTicks) {
        EntityPlayerSP player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
        if (stack == null || stack.isEmpty()) {
            stack = player.getHeldItem(EnumHand.OFF_HAND);
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }

        TrajectoryData trajectoryData = buildTrajectoryData(player, stack, partialTicks);
        if (trajectoryData == null || trajectoryData.points.size() < 2) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        RenderManager renderManager = mc.getRenderManager();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(2.0F);

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (Vec3d point : trajectoryData.points) {
            buffer.pos(point.x - renderManager.viewerPosX, point.y - renderManager.viewerPosY,
                    point.z - renderManager.viewerPosZ).color(0.95F, 0.45F, 1.0F, 0.86F).endVertex();
        }
        tessellator.draw();

        if (trajectoryData.hitPos != null) {
            AxisAlignedBB hitBox = new AxisAlignedBB(trajectoryData.hitPos.addVector(-0.15D, -0.15D, -0.15D),
                    trajectoryData.hitPos.addVector(0.15D, 0.15D, 0.15D))
                    .offset(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
            RenderGlobal.renderFilledBox(hitBox, 1.0F, 0.25F, 0.85F, 0.15F);
            RenderGlobal.drawSelectionBoundingBox(hitBox, 1.0F, 0.25F, 0.85F, 0.92F);
        }

        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static TrajectoryData buildTrajectoryData(EntityPlayerSP player, ItemStack stack, float partialTicks) {
        Item item = stack.getItem();
        if (item == null) {
            return null;
        }

        boolean potionItem = item instanceof ItemPotion
                || item instanceof ItemSplashPotion
                || item instanceof ItemLingeringPotion
                || item instanceof ItemExpBottle;
        boolean throwableItem = item instanceof ItemSnowball || item instanceof ItemEgg;
        boolean pearlItem = item instanceof ItemEnderPearl;
        boolean bowItem = item instanceof ItemBow;

        if ((bowItem && !RenderFeatureManager.trajectoryBows)
                || (pearlItem && !RenderFeatureManager.trajectoryPearls)
                || (throwableItem && !RenderFeatureManager.trajectoryThrowables)
                || (potionItem && !RenderFeatureManager.trajectoryPotions)
                || (!bowItem && !pearlItem && !throwableItem && !potionItem)) {
            return null;
        }

        float velocity;
        float gravity;
        if (bowItem) {
            if (!player.isHandActive()) {
                return null;
            }
            int useTicks = stack.getMaxItemUseDuration() - player.getItemInUseCount();
            float charge = useTicks / 20.0F;
            charge = (charge * charge + charge * 2.0F) / 3.0F;
            if (charge < 0.1F) {
                return null;
            }
            charge = Math.min(charge, 1.0F);
            velocity = charge * 3.0F;
            gravity = 0.05F;
        } else if (potionItem) {
            velocity = 0.5F;
            gravity = 0.05F;
        } else {
            velocity = pearlItem ? 1.5F : 1.2F;
            gravity = 0.03F;
        }

        Vec3d start = player.getPositionEyes(partialTicks);
        Vec3d look = player.getLook(partialTicks).normalize();
        Vec3d currentPos = start.add(look.scale(0.16D));
        Vec3d motion = look.scale(velocity);
        if (potionItem) {
            motion = motion.addVector(0.0D, -0.12D, 0.0D);
        }

        List<Vec3d> points = new ArrayList<>();
        points.add(currentPos);
        Vec3d hitPos = null;
        for (int i = 0; i < RenderFeatureManager.trajectoryMaxSteps; i++) {
            Vec3d nextPos = currentPos.add(motion);
            RayTraceResult hit = player.world.rayTraceBlocks(currentPos, nextPos, false, true, false);
            if (hit != null && hit.hitVec != null) {
                hitPos = hit.hitVec;
                points.add(hit.hitVec);
                break;
            }
            currentPos = nextPos;
            points.add(currentPos);
            motion = motion.scale(0.99D).addVector(0.0D, -gravity, 0.0D);
        }
        return new TrajectoryData(points, hitPos);
    }

    private static final class BlockHighlightEntry {
        private final BlockPos pos;
        private final float red;
        private final float green;
        private final float blue;

        private BlockHighlightEntry(BlockPos pos, float red, float green, float blue) {
            this.pos = pos;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    private static final class RenderFrameData {
        private static final RenderFrameData EMPTY = new RenderFrameData(new ArrayList<EntityRenderSample>(0),
                new ArrayList<EntityRenderSample>(0), new ArrayList<ItemRenderSample>(0));

        private final List<EntityRenderSample> livingEntities;
        private final List<EntityRenderSample> playerEntities;
        private final List<ItemRenderSample> itemEntities;

        private RenderFrameData(List<EntityRenderSample> livingEntities, List<EntityRenderSample> playerEntities,
                List<ItemRenderSample> itemEntities) {
            this.livingEntities = livingEntities;
            this.playerEntities = playerEntities;
            this.itemEntities = itemEntities;
        }
    }

    private static final class EntityRenderSample {
        private final Entity entity;
        private final double distanceSq;
        private final boolean player;
        private final boolean monster;
        private final boolean animal;

        private EntityRenderSample(Entity entity, double distanceSq, boolean player, boolean monster, boolean animal) {
            this.entity = entity;
            this.distanceSq = distanceSq;
            this.player = player;
            this.monster = monster;
            this.animal = animal;
        }

        private boolean matches(boolean includePlayers, boolean includeMonsters, boolean includeAnimals,
                double maxDistanceSq) {
            if (distanceSq > maxDistanceSq) {
                return false;
            }
            return (includePlayers && player) || (includeMonsters && monster) || (includeAnimals && animal);
        }
    }

    private static final class ItemRenderSample {
        private final EntityItem entityItem;

        private ItemRenderSample(EntityItem entityItem, double distanceSq) {
            this.entityItem = entityItem;
        }
    }

    private static final class OreScanState {
        private static final OreScanState EMPTY = new OreScanState(null, 0, 0, 0, 0, true, Integer.MIN_VALUE);

        private final BlockPos center;
        private final int radius;
        private final int verticalRadius;
        private final int configKey;
        private final int nextIndex;
        private final boolean completed;
        private final int lastCompletedTick;

        private OreScanState(BlockPos center, int radius, int verticalRadius, int configKey, int nextIndex,
                boolean completed, int lastCompletedTick) {
            this.center = center;
            this.radius = radius;
            this.verticalRadius = verticalRadius;
            this.configKey = configKey;
            this.nextIndex = nextIndex;
            this.completed = completed;
            this.lastCompletedTick = lastCompletedTick;
        }
    }

    private static final class TrajectoryData {
        private final List<Vec3d> points;
        private final Vec3d hitPos;

        private TrajectoryData(List<Vec3d> points, Vec3d hitPos) {
            this.points = points;
            this.hitPos = hitPos;
        }
    }
}
