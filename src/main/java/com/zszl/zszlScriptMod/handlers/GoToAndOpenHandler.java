// 文件路径: src/main/java/com/zszl/zszlScriptMod/handlers/GoToAndOpenHandler.java
package com.zszl.zszlScriptMod.handlers;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.utils.ModUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GoToAndOpenHandler {
    private static final GoToAndOpenHandler INSTANCE = new GoToAndOpenHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();

    private enum State {
        IDLE, MOVING, OPENING
    }

    private State currentState = State.IDLE;
    private BlockPos targetChestPos = null;
    private BlockPos targetStandPos = null;
    private int timeoutTicks = 0;

    private GoToAndOpenHandler() {
    }

    public static boolean start(BlockPos chestPos) {
        if (INSTANCE.currentState != State.IDLE) {
            mc.player.sendMessage(new TextComponentString(I18n.format("msg.goto_open.task_in_progress")));
            return false;
        }
        INSTANCE.targetChestPos = chestPos;
        INSTANCE.targetStandPos = INSTANCE.findBestStandPosition(chestPos);

        if (INSTANCE.targetStandPos != null) {
            INSTANCE.currentState = State.MOVING;
            INSTANCE.timeoutTicks = 600; // 30秒超时
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            mc.player.sendMessage(new TextComponentString(
                    I18n.format("msg.goto_open.start_to_interact_pos", INSTANCE.targetStandPos.toString())));
            EmbeddedNavigationHandler.INSTANCE.startGoto(INSTANCE.targetStandPos.getX(), INSTANCE.targetStandPos.getY(),
                    INSTANCE.targetStandPos.getZ());
            return true;
        } else {
            mc.player.sendMessage(new TextComponentString(
                    I18n.format("msg.goto_open.no_interact_pos", chestPos.toString())));
            INSTANCE.targetChestPos = null;
            INSTANCE.targetStandPos = null;
            return false;
        }
    }

    private void stop() {
        this.currentState = State.IDLE;
        this.targetChestPos = null;
        this.targetStandPos = null;
        MinecraftForge.EVENT_BUS.unregister(this);
        EmbeddedNavigationHandler.INSTANCE.stop();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("goto_open")) {
            return;
        }
        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("goto_open");
        try {
        if (event.phase != TickEvent.Phase.END || mc.player == null)
            return;

        timeoutTicks--;
        if (timeoutTicks <= 0) {
            mc.player.sendMessage(new TextComponentString(I18n.format("msg.goto_open.timeout")));
            stop();
            return;
        }

        if (currentState == State.MOVING) {
            BlockPos arrivalPos = targetStandPos != null ? targetStandPos : targetChestPos;
            // Check if arrived at target position
            double distance = mc.player.getDistance(arrivalPos.getX() + 0.5, arrivalPos.getY() + 0.5,
                    arrivalPos.getZ() + 0.5);
            if (distance < 2.0) {
                EmbeddedNavigationHandler.INSTANCE.stop();
                mc.player.sendMessage(new TextComponentString(
                        I18n.format("msg.goto_open.arrived_try_open")));
                currentState = State.OPENING;
                // 延迟一小会再打开，确保导航完全停止
                ModUtils.DelayScheduler.instance.schedule(() -> {
                    if (currentState == State.OPENING) {
                        ModUtils.rightClickOnBlock(mc.player, targetChestPos);
                    }
                }, 5);
            }
        }
        } finally {
            timer.stop();
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (currentState != State.OPENING || !(event.getGui() instanceof GuiChest)) {
            return;
        }

        GuiChest gui = (GuiChest) event.getGui();
        if (!(gui.inventorySlots instanceof ContainerChest)) {
            return;
        }

        ContainerChest container = (ContainerChest) gui.inventorySlots;
        IInventory inv = container.getLowerChestInventory();
        BlockPos openedChestPos = null;
        if (inv instanceof TileEntityChest) {
            openedChestPos = ((TileEntityChest) inv).getPos();
        }

        if (openedChestPos != null && targetChestPos != null && !openedChestPos.equals(targetChestPos)) {
            if (mc.player != null) {
                mc.player.sendMessage(new TextComponentString(I18n.format("msg.goto_open.not_target_retry")));
            }
            return;
        }

        mc.player.sendMessage(
                new TextComponentString(I18n.format("msg.goto_open.success")));
        stop();
    }

    private BlockPos findBestStandPosition(BlockPos chestPos) {
        if (mc.world == null) {
            return null;
        }

        BlockPos playerPos = mc.player == null ? chestPos : mc.player.getPosition();
        Vec3d chestCenter = new Vec3d(chestPos).addVector(0.5, 0.5, 0.5);

        for (BlockPos candidate : buildStandPositionCandidates(chestPos, playerPos, 4)) {
            if (!isStandable(candidate)) {
                continue;
            }
            if (!hasLineOfSightToChest(candidate, chestCenter, chestPos)) {
                continue;
            }
            return candidate;
        }

        return null;
    }

    static List<BlockPos> buildStandPositionCandidates(BlockPos chestPos, BlockPos playerPos, int interactionRange) {
        List<BlockPos> candidates = new ArrayList<>();
        int rangeSq = interactionRange * interactionRange;
        int playerY = playerPos.getY();

        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            addCandidateLayer(candidates, chestPos, playerY + yOffset, rangeSq);
            if (yOffset > 0) {
                addCandidateLayer(candidates, chestPos, playerY - yOffset, rangeSq);
            }
        }

        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> Math.abs(pos.getY() - playerY))
                .thenComparingDouble(pos -> playerPos.distanceSq(pos)));
        return candidates;
    }

    private static void addCandidateLayer(List<BlockPos> candidates, BlockPos chestPos, int y, int rangeSq) {
        int range = (int) Math.ceil(Math.sqrt(rangeSq));
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos candidate = new BlockPos(chestPos.getX() + dx, y, chestPos.getZ() + dz);
                if (candidate.equals(chestPos) || candidates.contains(candidate)) {
                    continue;
                }
                int chestDx = candidate.getX() - chestPos.getX();
                int chestDy = candidate.getY() - chestPos.getY();
                int chestDz = candidate.getZ() - chestPos.getZ();
                if (chestDx * chestDx + chestDy * chestDy + chestDz * chestDz > rangeSq) {
                    continue;
                }
                candidates.add(candidate);
            }
        }
    }

    private boolean isStandable(BlockPos standPos) {
        if (mc.world == null) {
            return false;
        }

        IBlockState feetState = mc.world.getBlockState(standPos);
        IBlockState headState = mc.world.getBlockState(standPos.up());
        IBlockState belowState = mc.world.getBlockState(standPos.down());

        boolean feetPassable = !feetState.getMaterial().blocksMovement();
        boolean headPassable = !headState.getMaterial().blocksMovement();
        boolean hasGround = belowState.getMaterial().blocksMovement();

        return feetPassable && headPassable && hasGround;
    }

    private boolean hasLineOfSightToChest(BlockPos standPos, Vec3d chestCenter, BlockPos chestPos) {
        if (mc.world == null) {
            return false;
        }

        Vec3d eyePos = new Vec3d(standPos).addVector(0.5, 1.62, 0.5);
        RayTraceResult ray = mc.world.rayTraceBlocks(eyePos, chestCenter, false, true, false);
        return ray == null || (ray.typeOfHit == RayTraceResult.Type.BLOCK && chestPos.equals(ray.getBlockPos()));
    }
}

