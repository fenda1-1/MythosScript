// 文件路径: src/main/java/com/zszl/zszlScriptMod/utils/ModUtils.java
package com.zszl.zszlScriptMod.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.inventory.Container;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.lang.reflect.Method;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.system.AutoUseItemRule;
import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;
import net.minecraft.item.EnumAction;

import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.item.ItemStack;

import net.minecraft.network.Packet;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import com.zszl.zszlScriptMod.gui.packet.PacketIdMapping;
import io.netty.channel.Channel;

import java.lang.reflect.Field;

public class ModUtils {
    private static final int CHEST_WITHDRAW_INITIAL_DELAY_TICKS = 10;
    private static final int CHEST_WITHDRAW_ITEMS_PER_BATCH = 1;
    private static final int CHEST_WITHDRAW_OPERATION_INTERVAL_TICKS = 2;
    ;
    private static volatile boolean disconnectScheduled = false;
    private static final Pattern CHAT_COMMAND_PLACEHOLDER_PATTERN = Pattern
            .compile("\\{\\s*([a-zA-Z0-9_\\.]+)\\s*\\}");

    public static void disconnectFromCurrentWorld() {
        if (disconnectScheduled) {
            return;
        }
        disconnectScheduled = true;
        Runnable task = () -> {
            disconnectScheduled = false;
            performDisconnectFromCurrentWorld();
        };
        if (DelayScheduler.instance != null) {
            DelayScheduler.instance.schedule(task, 1, "disconnect_world");
        } else {
            task.run();
        }
    }

    private static void performDisconnectFromCurrentWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        boolean singleplayer = mc.isSingleplayer();
        WorldClient world = mc.world;
        // For a local server, loadWorld performs the coordinated shutdown.
        // Sending quit first can stop its tick thread before initiateShutdown
        // queues its cleanup task, leaving the client waiting on that task forever.
        if (world != null && !singleplayer) {
            try {
                world.sendQuittingDisconnectingPacket();
            } catch (Exception e) {
                zszlScriptMod.LOGGER.warn("发送断开连接数据包失败", e);
            }
        }

        mc.loadWorld(null);
        if (singleplayer) {
            mc.displayGuiScreen(new GuiMainMenu());
        } else {
            mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
        }
    }

    /**
     * 延迟任务调度器
     * 这是一个单例类，用于管理所有延迟任务，避免每次延迟都注册新的事件监听器。
     */
    public static class DelayScheduler {
        public static DelayScheduler instance;
        private final Queue<DelayTask> tasks = new ConcurrentLinkedQueue<>();

        private DelayScheduler() {
            MinecraftForge.EVENT_BUS.register(this);
        }

        public static void init() {
            if (instance == null) {
                instance = new DelayScheduler();
                zszlScriptMod.LOGGER.info("DelayScheduler 已初始化并注册到事件总线。");
            }
        }

        public void schedule(Runnable action, int delayTicks) {
            schedule(action, delayTicks, null);
        }

        public void schedule(Runnable action, int delayTicks, String tag) {
            schedule(action, delayTicks, false, tag);
        }

        public void schedule(Runnable action, int delayTicks, boolean normalizeDelayTo20Tps) {
            schedule(action, delayTicks, normalizeDelayTo20Tps, null);
        }

        public void schedule(Runnable action, int delayTicks, boolean normalizeDelayTo20Tps, String tag) {
            tasks.add(new DelayTask(action, delayTicks, normalizeDelayTo20Tps, tag));
        }

        public void cancelTasks(java.util.function.Predicate<DelayTask> predicate) {
            tasks.removeIf(predicate);
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Iterator<DelayTask> iterator = tasks.iterator();
                while (iterator.hasNext()) {
                    DelayTask task = iterator.next();
                    task.tick();
                    if (task.isFinished()) {
                        task.run();
                        iterator.remove();
                    }
                }
            }
        }

        // ==================== 核心修复 ====================
        // 将 DelayTask 的访问权限从 private 修改为 public
        public static class DelayTask {
            // =================================================
            private final Runnable action;
            private int remainingTicks;
            private double remainingBaselineTicks;
            private final boolean normalizeDelayTo20Tps;
            private final String tag;

            public DelayTask(Runnable action, int delayTicks, boolean normalizeDelayTo20Tps, String tag) {
                this.action = action;
                this.normalizeDelayTo20Tps = normalizeDelayTo20Tps && delayTicks > 0;
                this.remainingTicks = Math.max(0, delayTicks);
                this.remainingBaselineTicks = Math.max(0, delayTicks);
                this.tag = tag;
            }

            public void tick() {
                if (normalizeDelayTo20Tps) {
                    remainingBaselineTicks -= 1.0D / getCurrentTimerSpeedMultiplier();
                    remainingTicks = Math.max(0, (int) Math.ceil(Math.max(0.0D, remainingBaselineTicks)));
                } else {
                    remainingTicks--;
                }
            }

            public boolean isFinished() {
                return remainingTicks <= 0;
            }

            public void run() {
                action.run();
            }

            public String getTag() {
                return tag;
            }
        }
    }

    public static class Click {
        public final int slot;
        public final int button;
        public final ClickType type;

        public Click(int slot, int button, ClickType type) {
            this.slot = slot;
            this.button = button;
            this.type = type;
        }
    }

    /**
     * 延迟动作类 (现在使用 DelayScheduler)
     */
    public static class DelayAction implements Consumer<EntityPlayerSP> {
        private final int delayTicks;
        private final boolean normalizeDelayTo20Tps;
        private final Runnable action;

        public DelayAction(int delayTicks) {
            this(delayTicks, null);
        }

        public DelayAction(int delayTicks, Runnable action) {
            this(delayTicks, action, false);
        }

        public DelayAction(int delayTicks, boolean normalizeDelayTo20Tps) {
            this(delayTicks, null, normalizeDelayTo20Tps);
        }

        public DelayAction(int delayTicks, Runnable action, boolean normalizeDelayTo20Tps) {
            this.delayTicks = delayTicks;
            this.action = action;
            this.normalizeDelayTo20Tps = normalizeDelayTo20Tps;
        }

        public int getDelayTicks() {
            return normalizeDelayTo20Tps
                    ? normalizeDelayTicksToVanilla20Tps(delayTicks)
                    : delayTicks;
        }

        public int getConfiguredDelayTicks() {
            return delayTicks;
        }

        public boolean shouldNormalizeDelayTo20Tps() {
            return normalizeDelayTo20Tps;
        }

        @Override
        public void accept(EntityPlayerSP player) {
            if (delayTicks > 0) {
                DelayScheduler.instance.schedule(() -> {
                    if (action != null) {
                        action.run();
                    }
                }, delayTicks, normalizeDelayTo20Tps);
            } else {
                if (action != null) {
                    action.run();
                }
            }
        }
    }

    public static int normalizeDelayTicksToVanilla20Tps(int delayTicks) {
        if (delayTicks <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(delayTicks * getCurrentTimerSpeedMultiplier()));
    }

    public static float getCurrentTimerSpeedMultiplier() {
        return Math.max(1.0F, SpeedHandler.getCurrentTimerSpeedMultiplier());
    }

    /**
     * 将3D世界坐标转换为2D屏幕坐标
     * 
     * @param pos 3D世界坐标
     * @return 2D屏幕坐标 (x, y, z)，其中z表示是否在屏幕前方 (z<1)
     */
    public static Vec3d worldToScreenPos(Vec3d pos) {
        EntityPlayerSP player = Minecraft.getMinecraft().getRenderViewEntity() instanceof EntityPlayerSP
                ? (EntityPlayerSP) Minecraft.getMinecraft().getRenderViewEntity()
                : Minecraft.getMinecraft().player;
        if (player == null)
            return null;

        Vec3d cameraPos = player.getPositionEyes(Minecraft.getMinecraft().getRenderPartialTicks());

        FloatBuffer screenCoords = BufferUtils.createFloatBuffer(4);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        boolean result = GLU.gluProject((float) (pos.x - cameraPos.x), (float) (pos.y - cameraPos.y),
                (float) (pos.z - cameraPos.z), modelView, projection, viewport, screenCoords);

        if (result) {
            return new Vec3d(screenCoords.get(0), Display.getHeight() - screenCoords.get(1), screenCoords.get(2));
        }
        return null;
    }

    /**
     * 设置玩家视角角度
     * 
     * @param player 玩家实体
     * @param yaw    偏航角
     * @param pitch  俯仰角
     */
    public static void setPlayerViewAngles(EntityPlayerSP player, float yaw, float pitch) {
        // 添加视角范围限制
        yaw = yaw % 360.0F;
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        // 强制更新所有旋转参数
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        player.rotationYawHead = yaw;
        player.prevRotationYaw = yaw;
        player.prevRotationPitch = pitch;
        player.renderYawOffset = yaw;

        player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, player.onGround));

        zszlScriptMod.LOGGER.info("Set player view angles: yaw={}, pitch={}", yaw, pitch);
    }

    /**
     * 触发一次可靠跳跃。
     * 优先使用原版跳跃逻辑（player.jump），再补发一次位置包同步，避免仅发包被服务端忽略。
     *
     * @param player 玩家实体
     */
    public static void sendJumpPacket(EntityPlayerSP player) {
        if (player == null || player.connection == null) {
            return;
        }

        // 非落地状态下，原版也不能再次起跳（除飞行等特殊状态）
        if (!player.onGround && !player.capabilities.isFlying) {
            return;
        }

        // 先走客户端原生跳跃逻辑，确保 motionY、统计、事件链路一致
        player.jump();

        // 再发一个当前位置包，尽快让服务端同步到离地状态
        player.connection.sendPacket(new CPacketPlayer.Position(player.posX, player.posY, player.posZ, false));
    }

    /**
     * 发送聊天命令
     * 
     * @param command 命令字符串
     */
    public static void sendChatCommand(String command) {
        String resolvedCommand;
        try {
            resolvedCommand = resolveChatCommandPlaceholders(command);
        } catch (IllegalStateException e) {
            zszlScriptMod.LOGGER.error("[发送命令] 占位符解析失败: {}", e.getMessage());
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player
                        .sendMessage(new TextComponentString(TextFormatting.RED + "[脚本错误] 发送命令失败: " + e.getMessage()));
            }
            return;
        }

        if (tryHandleInternalNavigation(resolvedCommand)) {
            if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player
                        .sendMessage(new TextComponentString("§d[DEBUG] §7内置导航接管命令: §f" + resolvedCommand));
            }
            return;
        }
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§d[DEBUG] §7发送命令: §f" + resolvedCommand));
        }
        if (Minecraft.getMinecraft().player != null && !Minecraft.getMinecraft().player.isSpectator()) {
            Minecraft.getMinecraft().player.sendChatMessage(resolvedCommand);
            zszlScriptMod.LOGGER.info("Sent command: " + resolvedCommand);
        }
    }

    private static String resolveChatCommandPlaceholders(String command) {
        String text = command == null ? "" : command;
        Matcher matcher = CHAT_COMMAND_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = resolveChatCommandPlaceholderValue(name);
            if (value == null) {
                throw new IllegalStateException("命令中需要 {" + name + "}，但未捕获到对应值。");
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveChatCommandPlaceholderValue(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        PacketFieldRuleManager.CapturedFieldSnapshot field = PacketFieldRuleManager.getLatestCapturedField(name);
        if (field != null && field.getValue() != null) {
            return String.valueOf(field.getValue());
        }

        byte[] capturedBytes = CapturedIdRuleManager.getCapturedIdBytes(name);
        if (capturedBytes == null || capturedBytes.length == 0) {
            return null;
        }
        String text = tryDecodePrintableUtf8(capturedBytes);
        return text != null ? text : bytesToHexWithSpaces(capturedBytes);
    }

    private static String tryDecodePrintableUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                return null;
            }
        }
        return text;
    }

    /**
     * 通过反射调用 EmbeddedNavigationHandler，规避 IDE/编译缓存中损坏的 .sig 索引导致的类型访问错误。
     */
    private static boolean tryHandleInternalNavigation(String command) {
        try {
            Class<?> clazz = Class.forName("com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler");
            Object instance = clazz.getField("INSTANCE").get(null);
            Object handled = clazz.getMethod("handleInternalCommand", String.class).invoke(instance, command);
            return handled instanceof Boolean && (Boolean) handled;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 右键点击方块。
     * 不再依赖“必须射线精确命中方块中心”的严格校验，避免很多可交互方块因为中心点/遮挡/边缘问题导致完全不执行。
     *
     * @param player 玩家实体
     * @param pos    目标方块位置
     */
    public static void rightClickOnBlock(EntityPlayerSP player, BlockPos pos) {
        rightClickOnBlock(player, pos, false);
    }

    /**
     * 右键点击方块。
     *
     * @param player       玩家实体
     * @param pos          目标方块位置
     * @param preserveView 是否保留当前视角
     */
    public static void rightClickOnBlock(EntityPlayerSP player, BlockPos pos, boolean preserveView) {
        if (player == null || player.world == null || Minecraft.getMinecraft().playerController == null || pos == null) {
            zszlScriptMod.LOGGER.warn("rightClickOnBlock 失败: 玩家、世界、控制器或坐标为空");
            return;
        }

        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player
                    .sendMessage(new TextComponentString("§d[DEBUG] §7尝试右键方块于: §f" + pos.toString()));
        }

        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        double dx = centerX - eyeX;
        double dy = centerY - eyeY;
        double dz = centerZ - eyeZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double distanceSq = dx * dx + dy * dy + dz * dz;

        // 超出常规交互距离时直接放弃，避免“看起来执行了但服务端根本不会接受”
        if (distanceSq > 36.0D) {
            zszlScriptMod.LOGGER.warn("rightClickOnBlock 失败: 目标方块距离过远 pos={}, distSq={}", pos, distanceSq);
            return;
        }

        // 根据玩家相对位置，选择最合理的点击面
        EnumFacing facing;
        if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > Math.abs(dz)) {
            facing = dy > 0 ? EnumFacing.DOWN : EnumFacing.UP;
        } else if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? EnumFacing.WEST : EnumFacing.EAST;
        } else {
            facing = dz > 0 ? EnumFacing.NORTH : EnumFacing.SOUTH;
        }

        Vec3d hitVec = new Vec3d(
                centerX + facing.getFrontOffsetX() * 0.5D,
                centerY + facing.getFrontOffsetY() * 0.5D,
                centerZ + facing.getFrontOffsetZ() * 0.5D);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horizontalDistance)));

        if (!preserveView) {
            setPlayerViewAngles(player, yaw, pitch);
        }

        new DelayAction(3, () -> {
            RayTraceResult validateRay = player.world.rayTraceBlocks(
                    new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ),
                    hitVec,
                    false,
                    true,
                    false);

            if (validateRay != null && validateRay.typeOfHit == RayTraceResult.Type.BLOCK
                    && !pos.equals(validateRay.getBlockPos())) {
                zszlScriptMod.LOGGER.warn("rightClickOnBlock 校验射线命中了其他方块，仍尝试执行点击。target={}, hit={}",
                        pos, validateRay.getBlockPos());
            }

            Minecraft.getMinecraft().playerController.processRightClickBlock(
                    player,
                    Minecraft.getMinecraft().world,
                    pos,
                    facing,
                    hitVec,
                    EnumHand.MAIN_HAND);
            player.swingArm(EnumHand.MAIN_HAND);
            zszlScriptMod.LOGGER.info("执行右键方块: {} 面: {} 命中点: {} 保留视角: {}", pos, facing, hitVec, preserveView);
        }).accept(player);
    }

    /**
     * 右键点击最近的实体
     * 
     * @param player 玩家实体
     * @param pos    目标位置（用于查找附近的实体）
     * @param range  查找范围
     */
    public static void rightClickOnNearestEntity(EntityPlayerSP player, BlockPos pos, double range) {
        rightClickOnNearestEntity(player, pos, range, false);
    }

    /**
     * 右键点击最近的实体
     *
     * @param player       玩家实体
     * @param pos          目标位置（用于查找附近的实体）
     * @param range        查找范围
     * @param preserveView 是否保留当前视角
     */
    public static void rightClickOnNearestEntity(EntityPlayerSP player, BlockPos pos, double range, boolean preserveView) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player
                    .sendMessage(new TextComponentString("§d[DEBUG] §7尝试右键实体于: §f" + pos.toString()));
        }
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;

        // 查找附近所有实体（不包括玩家自己）
        Entity nearest = null;
        double minDistSq = Double.MAX_VALUE;
        for (Entity entity : Minecraft.getMinecraft().world.getEntitiesWithinAABB(
                Entity.class,
                new AxisAlignedBB(
                        px - range, py - range, pz - range,
                        px + range, py + range, pz + range))) {
            if (entity == player)
                continue;
            double distSq = entity.getDistanceSq(px, py, pz);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearest = entity;
            }
        }

        if (nearest != null) {
            Minecraft.getMinecraft().playerController.interactWithEntity(player, nearest, EnumHand.MAIN_HAND);
            zszlScriptMod.LOGGER.info("Right clicked entity {} at {} preserveView={}", nearest.getName(), pos,
                    preserveView);
        } else {
            zszlScriptMod.LOGGER.warn("No entity found near: " + pos);
        }
    }

    /**
     * 自动执行指定村民交易，自动补全输入物品并领取交易物品，支持NBT精确匹配
     * 
     * @param player     玩家实体
     * @param tradeIndex 村民交易序号（从0开始）
     * @param tradeCount 执行多少次该交易
     */
    public static void autoVillagerTradeFull(EntityPlayerSP player, int tradeIndex, int tradeCount) {
        zszlScriptMod.LOGGER.info("当前GUI: " + Minecraft.getMinecraft().currentScreen.getClass().getName());
        zszlScriptMod.LOGGER.info("当前容器: " + player.openContainer.getClass().getName());
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiMerchant) || tradeCount <= 0)
            return;
        GuiMerchant gui = (GuiMerchant) Minecraft.getMinecraft().currentScreen;
        MerchantRecipeList recipes = gui.getMerchant().getRecipes(player);

        if (recipes == null || tradeIndex < 0 || tradeIndex >= recipes.size())
            return;
        MerchantRecipe recipe = recipes.get(tradeIndex);
        if (recipe == null || recipe.isRecipeDisabled())
            return;

        // 反射设置GuiMerchant的currentRecipeIndex（适配开发版和混淆版）
        try {
            java.lang.reflect.Field field;
            try {
                // 开发环境名
                field = GuiMerchant.class.getDeclaredField("currentRecipeIndex");
            } catch (NoSuchFieldException e) {
                // 混淆名
                field = GuiMerchant.class.getDeclaredField("field_147041_z");
            }
            field.setAccessible(true);
            field.setInt(gui, tradeIndex);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (int t = 0; t < tradeCount; t++) {
            // 补全输入物品
            boolean inputOk = fillMerchantInputsWithNBT(gui, recipe);
            if (!inputOk) {
                zszlScriptMod.LOGGER.warn("背包中缺少交易所需物品（含NBT），无法继续交易");
                break;
            }

            int emptySlot = findFirstEmptyInventorySlot(gui);
            if (emptySlot >= 0) {
                Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, 2, 0,
                        ClickType.PICKUP, player);
                Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, emptySlot, 0,
                        ClickType.PICKUP, player);
            } else {
                zszlScriptMod.LOGGER.warn("背包已满，无法领取交易物品！");
                break;
            }
        }
        zszlScriptMod.LOGGER.info("自动完成村民交易（含NBT精确匹配），交易序号: " + tradeIndex + "，次数: " + tradeCount);

        // 交易完成后强制清理两个输入槽
        clearMerchantInputSlot(gui, 0);
        clearMerchantInputSlot(gui, 1);

        // 额外执行一次清空操作（防止残留）
        Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, 0, 0, ClickType.QUICK_MOVE,
                player);
        Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, 1, 0, ClickType.QUICK_MOVE,
                player);
    }

    /**
     * 补全村民交易输入物品（支持1或2输入物品，且匹配NBT标签）
     * 
     * @return 是否成功放入所需数量的输入物品
     */
    private static boolean fillMerchantInputsWithNBT(GuiMerchant gui, MerchantRecipe recipe) {
        // 1. 先清空输入槽（slot 0, slot 1）
        clearMerchantInputSlot(gui, 0);
        clearMerchantInputSlot(gui, 1);

        // 2. 放入输入1
        boolean ok1 = moveItemToInputWithNBT(gui, recipe.getItemToBuy(), 0, recipe.getItemToBuy().getCount());
        // 3. 放入输入2（如果有）
        boolean ok2 = true;
        if (!recipe.getSecondItemToBuy().isEmpty()) {
            ok2 = moveItemToInputWithNBT(gui, recipe.getSecondItemToBuy(), 1,
                    recipe.getSecondItemToBuy().getCount());
        }
        return ok1 && ok2;
    }

    /**
     * 从背包移动指定数量物品（含精确NBT）到村民输入槽
     * 
     * @param gui         GuiMerchant
     * @param targetStack 目标物品
     * @param inputSlot   输入槽号（0或1）
     * @param neededCount 需要的数量
     * @return 是否足量成功
     */
    private static boolean moveItemToInputWithNBT(GuiMerchant gui, net.minecraft.item.ItemStack targetStack,
            int inputSlot,
            int neededCount) {
        int moved = 0;
        for (int i = 0; i <= 35; i++) {
            Slot slot = gui.inventorySlots.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                net.minecraft.item.ItemStack stack = slot.getStack();
                if (itemStackNBTEquals(stack, targetStack)) {
                    int toMove = Math.min(stack.getCount(), neededCount - moved);
                    for (int j = 0; j < toMove; j++) {
                        Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, i, 0,
                                ClickType.PICKUP,
                                Minecraft.getMinecraft().player);
                        Minecraft.getMinecraft().playerController.windowClick(gui.inventorySlots.windowId, inputSlot, 0,
                                ClickType.PICKUP,
                                Minecraft.getMinecraft().player);
                        moved++;
                        stack = slot.getStack();
                        if (stack == null || stack.isEmpty())
                            break;
                    }
                }
                if (moved >= neededCount)
                    break;
            }
        }
        return moved >= neededCount;
    }

    /**
     * 精确比较两个ItemStack，包括NBT
     */
    private static boolean itemStackNBTEquals(net.minecraft.item.ItemStack a, net.minecraft.item.ItemStack b) {
        if (a == null || b == null)
            return false;
        NBTTagCompound nbtA = a.getTagCompound();
        NBTTagCompound nbtB = b.getTagCompound();
        if (nbtA == null && nbtB == null)
            return true;
        if (nbtA == null || nbtB == null)
            return false;
        return nbtA.equals(nbtB);
    }

    /**
     * 查找玩家背包中的第一个空槽位
     * 
     * @param gui 当前GuiMerchant
     * @return 槽位索引（GUI里的），没有空位返回-1
     */
    private static int findFirstEmptyInventorySlot(GuiMerchant gui) {
        for (int i = 0; i <= 35; i++) {
            Slot slot = gui.inventorySlots.getSlot(i);
            if (slot != null && !slot.getHasStack()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 清空村民交易输入槽
     */
    private static void clearMerchantInputSlot(GuiMerchant gui, int slotId) {
        if (gui.inventorySlots == null)
            return; // 添加空指针检查
        Slot slot = gui.inventorySlots.getSlot(slotId);
        if (slot != null && slot.getHasStack()) {
            // 使用快速移动模式清空槽位（按住Shift点击）
            Minecraft.getMinecraft().playerController.windowClick(
                    gui.inventorySlots.windowId,
                    slotId,
                    0,
                    ClickType.QUICK_MOVE, // 修改为快速移动
                    Minecraft.getMinecraft().player);

            // 二次清理确保槽位清空（针对无法快速移动的情况）
            if (slot.getHasStack()) {
                Minecraft.getMinecraft().playerController.windowClick(
                        gui.inventorySlots.windowId,
                        slotId,
                        1, // 使用右键点击逐个移除
                        ClickType.PICKUP,
                        Minecraft.getMinecraft().player);
            }
        }
    }

    /**
     * 自动点击箱子或大箱子GUI的指定格子 (应用初始延迟)
     * 
     * @param player         玩家实体
     * @param chestSlotIndex 格子编号（大箱子0-53，小箱子0-26）
     */
    public static void autoChestClick(EntityPlayerSP player, int chestSlotIndex) {
        autoChestClick(player, chestSlotIndex, 1);
    }

    /**
     * 自动点击箱子或大箱子GUI的指定格子
     *
     * @param player         玩家实体
     * @param chestSlotIndex 格子编号（大箱子0-53，小箱子0-26）
     * @param delayTicks     自定义延迟 tick
     */
    public static void autoChestClick(EntityPlayerSP player, int chestSlotIndex, int delayTicks) {
        autoChestClick(player, chestSlotIndex, delayTicks, "PICKUP");
    }

    public static void autoChestClick(EntityPlayerSP player, int chestSlotIndex, int delayTicks, String clickTypeName) {
        autoChestClick(player, chestSlotIndex, delayTicks, 0, clickTypeName);
    }

    public static void autoChestClick(EntityPlayerSP player, int chestSlotIndex, int delayTicks, int button,
            String clickTypeName) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player
                    .sendMessage(new TextComponentString(
                            "§d[调试] §7自动点击箱子格子: §f" + chestSlotIndex + " §7延迟: §f"
                                    + Math.max(0, delayTicks) + " tick §7类型: §f"
                                    + clickTypeToDisplayName(clickTypeName)));
        }

        int actualDelayTicks = Math.max(0, delayTicks);

        new DelayAction(actualDelayTicks, () -> clickChestSlotNow(chestSlotIndex, button, clickTypeName)).accept(player);
    }

    public static void clickChestSlotNow(int chestSlotIndex, String clickTypeName) {
        clickChestSlotNow(chestSlotIndex, 0, clickTypeName);
    }

    public static void clickChestSlotNow(int chestSlotIndex, int button, String clickTypeName) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null) {
            return;
        }
        if (!(mc.currentScreen instanceof GuiChest)) {
            return;
        }

        GuiChest gui = (GuiChest) mc.currentScreen;
        if (chestSlotIndex < 0 || chestSlotIndex >= gui.inventorySlots.inventorySlots.size()) {
            return;
        }

        ClickType clickType = resolveClickType(clickTypeName);
        mc.playerController.windowClick(gui.inventorySlots.windowId, chestSlotIndex, button, clickType, player);
        zszlScriptMod.LOGGER.info("自动点击箱子格子: {} button={} type={}", chestSlotIndex, button,
                normalizeClickTypeName(clickTypeName));
    }

    public static int resolveLwjglKeyCode(String keyName) {
        if (keyName == null) {
            return Keyboard.KEY_NONE;
        }

        String normalized = keyName.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Keyboard.KEY_NONE;
        }

        switch (normalized) {
            case "CTRL":
            case "CONTROL":
                return Keyboard.KEY_LCONTROL;
            case "SHIFT":
                return Keyboard.KEY_LSHIFT;
            case "LSHIFT":
            case "LEFTSHIFT":
                return Keyboard.KEY_LSHIFT;
            case "RSHIFT":
            case "RIGHTSHIFT":
                return Keyboard.KEY_RSHIFT;
            case "ALT":
                return Keyboard.KEY_LMENU;
            case "LALT":
            case "LEFTALT":
                return Keyboard.KEY_LMENU;
            case "RALT":
            case "RIGHTALT":
                return Keyboard.KEY_RMENU;
            case "LCTRL":
            case "LEFTCTRL":
                return Keyboard.KEY_LCONTROL;
            case "RCTRL":
            case "RIGHTCTRL":
                return Keyboard.KEY_RCONTROL;
            case "ENTER":
                return Keyboard.KEY_RETURN;
            case "ESC":
                return Keyboard.KEY_ESCAPE;
            case "BACKSPACE":
            case "BACK":
                return Keyboard.KEY_BACK;
            case "DELETE":
            case "DEL":
                return Keyboard.KEY_DELETE;
            case "CAPSLOCK":
            case "CAPS":
                return Keyboard.KEY_CAPITAL;
            case "PAGEUP":
            case "PGUP":
                return Keyboard.KEY_PRIOR;
            case "PAGEDOWN":
            case "PGDN":
                return Keyboard.KEY_NEXT;
            case "INS":
                return Keyboard.KEY_INSERT;
            case "UPARROW":
                return Keyboard.KEY_UP;
            case "DOWNARROW":
                return Keyboard.KEY_DOWN;
            case "LEFTARROW":
                return Keyboard.KEY_LEFT;
            case "RIGHTARROW":
                return Keyboard.KEY_RIGHT;
            case "SEMICOLON":
                return Keyboard.KEY_SEMICOLON;
            case "QUOTE":
                return Keyboard.KEY_APOSTROPHE;
            case "BACKTICK":
                return Keyboard.KEY_GRAVE;
            case "LEFTBRACKET":
                return Keyboard.KEY_LBRACKET;
            case "RIGHTBRACKET":
                return Keyboard.KEY_RBRACKET;
            case "NUMPADDECIMAL":
            case "NUMPAD.":
                return Keyboard.KEY_DECIMAL;
            case "NUMPADENTER":
                return Keyboard.KEY_NUMPADENTER;
            case "NUMPADPLUS":
            case "NUMPAD+":
                return Keyboard.KEY_ADD;
            case "NUMPADMINUS":
            case "NUMPAD-":
                return Keyboard.KEY_SUBTRACT;
            case "NUMPADMULTIPLY":
            case "NUMPAD*":
                return Keyboard.KEY_MULTIPLY;
            case "NUMPADDIVIDE":
            case "NUMPAD/":
                return Keyboard.KEY_DIVIDE;
            default:
                return Keyboard.getKeyIndex(normalized);
        }
    }

    public static int parseWindowIdSpec(String windowIdSpec) {
        if (windowIdSpec == null || windowIdSpec.trim().isEmpty()) {
            return -1;
        }

        String spec = windowIdSpec.trim();

        if (spec.contains("{")) {
            spec = resolvePacketHexPlaceholders(spec).trim();
        }

        if (spec.matches("[-+]?\\d+")) {
            return Integer.parseInt(spec);
        }

        String hex = spec.replaceAll("\\s+", "");
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (!hex.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException("非法窗口ID: " + windowIdSpec);
        }

        if (hex.length() <= 2) {
            int b = Integer.parseInt(hex, 16) & 0xFF;
            if (b >= 0x30 && b <= 0x39) {
                return b - 0x30;
            }
            return b;
        }

        return Integer.parseInt(hex, 16);
    }

    public static int parseNumericSpec(String valueSpec, String baseHint) {
        if (valueSpec == null || valueSpec.trim().isEmpty()) {
            return -1;
        }

        String spec = valueSpec.trim();
        if (spec.contains("{")) {
            spec = resolvePacketHexPlaceholders(spec).trim();
        }

        if ("DEC".equalsIgnoreCase(baseHint)) {
            return Integer.parseInt(spec);
        }

        String hex = spec.replaceAll("\\s+", "");
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }

        if ("HEX".equalsIgnoreCase(baseHint) || hex.matches("[0-9A-Fa-f]+")) {
            return Integer.parseInt(hex, 16);
        }

        if (spec.matches("[-+]?\\d+")) {
            return Integer.parseInt(spec);
        }

        throw new IllegalArgumentException("非法数值: " + valueSpec);
    }

    /**
     * 在当前打开容器执行一次 windowClick。
     *
     * @param windowIdSpec  期望窗口ID。可填十进制、十六进制、或占位符（如 {dragoncore_window_id_out}）。
     *                      为空或 -1 表示不校验，直接使用当前窗口。
     * @param slot          槽位
     * @param button        按钮
     * @param clickTypeName ClickType 名称（如 PICKUP/QUICK_MOVE/SWAP）
     */
    public static void performWindowClick(String windowIdSpec, int slot, int button, String clickTypeName) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.openContainer == null || mc.playerController == null) {
            zszlScriptMod.LOGGER.warn("windowClick 失败: 玩家或容器不可用");
            return;
        }

        int expectedWindowId;
        try {
            expectedWindowId = parseWindowIdSpec(windowIdSpec);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("windowClick 失败: windowId 解析异常: {}", windowIdSpec);
            return;
        }

        int actualWindowId = player.openContainer.windowId;
        if (expectedWindowId >= 0 && expectedWindowId != actualWindowId) {
            zszlScriptMod.LOGGER.warn("windowClick 失败: 窗口ID不匹配, expected={}, actual={}", expectedWindowId,
                    actualWindowId);
            return;
        }

        ClickType clickType = resolveClickType(clickTypeName);
        mc.playerController.windowClick(actualWindowId, slot, button, clickType, player);
    }

    public static String normalizeClickTypeName(String clickTypeName) {
        if (clickTypeName == null || clickTypeName.trim().isEmpty()) {
            return "PICKUP";
        }

        String raw = clickTypeName.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("PICKUP_ALL")) {
            return "PICKUP_ALL";
        }
        if (upper.contains("QUICK_MOVE")) {
            return "QUICK_MOVE";
        }
        if (upper.contains("QUICK_CRAFT")) {
            return "QUICK_CRAFT";
        }
        if (upper.contains("CLONE")) {
            return "CLONE";
        }
        if (upper.contains("SWAP")) {
            return "SWAP";
        }
        if (upper.contains("THROW")) {
            return "THROW";
        }
        if (upper.contains("PICKUP")) {
            return "PICKUP";
        }

        switch (raw) {
            case "普通点击":
            case "左键点击":
            case "右键":
                return "PICKUP";
            case "Shift快速移动":
            case "快速移动":
            case "Shift点击":
            case "Shift右键":
                return "QUICK_MOVE";
            case "数字键交换":
            case "交换":
                return "SWAP";
            case "丢弃":
                return "THROW";
            case "收集同类":
                return "PICKUP_ALL";
            case "拖拽分发":
            case "快速拖拽":
                return "QUICK_CRAFT";
            case "创造复制":
                return "CLONE";
            default:
                return "PICKUP";
        }
    }

    public static String clickTypeToDisplayName(String clickTypeName) {
        switch (normalizeClickTypeName(clickTypeName)) {
            case "QUICK_MOVE":
                return "Shift快速移动";
            case "SWAP":
                return "数字键交换";
            case "THROW":
                return "丢弃";
            case "PICKUP_ALL":
                return "收集同类";
            case "QUICK_CRAFT":
                return "拖拽分发";
            case "CLONE":
                return "创造复制";
            case "PICKUP":
            default:
                return "普通点击";
        }
    }

    public static ClickType resolveClickType(String clickTypeName) {
        String normalizedName = normalizeClickTypeName(clickTypeName);
        try {
            return ClickType.valueOf(normalizedName);
        } catch (Exception ignored) {
            zszlScriptMod.LOGGER.warn("windowClick ClickType 无效: {}, 已回退 PICKUP", clickTypeName);
            return ClickType.PICKUP;
        }
    }

    /**
     * 安全版一键取出箱子里面的所有物品。
     * 
     * @param shiftQuickMove true=使用 Shift 一键拾取(QUICK_MOVE)，false=逐格普通拾取(PICKUP)
     */
    public static void takeAllItemsFromChest(boolean shiftQuickMove) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                    "§d[调试] §7执行一键取物 (" + (shiftQuickMove ? "Shift一键拾取" : "逐格拾取") + ")。"));
        }

        DelayScheduler.instance.schedule(() -> {
            if (Minecraft.getMinecraft().currentScreen instanceof GuiChest
                    && Minecraft.getMinecraft().player.openContainer != null) {
                GuiChest gui = (GuiChest) Minecraft.getMinecraft().currentScreen;
                Container container = Minecraft.getMinecraft().player.openContainer;
                int windowId = container.windowId;

                int chestSlots = container.inventorySlots.size() - 36;
                if (chestSlots <= 0) {
                    return;
                }

                List<Integer> slotsToProcess = new ArrayList<>();
                for (int i = 0; i < chestSlots; i++) {
                    Slot slot = container.getSlot(i);
                    if (slot != null && slot.getHasStack()) {
                        slotsToProcess.add(i);
                    }
                }

                if (slotsToProcess.isEmpty()) {
                    return;
                }

                int initialDelay = CHEST_WITHDRAW_INITIAL_DELAY_TICKS;
                int batchSize = CHEST_WITHDRAW_ITEMS_PER_BATCH;
                int interval = CHEST_WITHDRAW_OPERATION_INTERVAL_TICKS;
                ClickType clickType = shiftQuickMove ? ClickType.QUICK_MOVE : ClickType.PICKUP;

                for (int i = 0; i < slotsToProcess.size(); i++) {
                    final int slotIndex = slotsToProcess.get(i);
                    int batchIndex = i / Math.max(1, batchSize);
                    int delay = initialDelay + (batchIndex * interval);

                    DelayScheduler.instance.schedule(() -> {
                        if (Minecraft.getMinecraft().currentScreen == gui
                                && Minecraft.getMinecraft().player.openContainer.windowId == windowId) {
                            Minecraft.getMinecraft().playerController.windowClick(
                                    windowId,
                                    slotIndex,
                                    0,
                                    clickType,
                                    Minecraft.getMinecraft().player);
                        }
                    }, delay);
                }
            } else {
                zszlScriptMod.LOGGER.warn("箱子GUI未正常打开或容器为空");
            }
        }, 1);
    }

    /**
     * 默认保持旧行为：使用 Shift 一键拾取。
     */
    public static void takeAllItemsFromChest() {
        takeAllItemsFromChest(true);
    }

    private static void processChestSlotsSafely(GuiChest gui, List<Integer> slots) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            // 将槽位分成四列并行处理
            for (int batchIndex = 0; batchIndex < slots.size(); batchIndex += 3) {
                final int currentBatch = batchIndex;
                // 每列间隔3tick
                new DelayAction(batchIndex, () -> {
                    // 并行处理当前列的4个槽位
                    for (int i = 0; i < 4; i++) {
                        int globalIndex = currentBatch + i;
                        if (globalIndex >= slots.size())
                            return;

                        int slotIndex = slots.get(globalIndex);
                        if (slotIndex >= 0 && slotIndex < gui.inventorySlots.inventorySlots.size()
                                && gui.inventorySlots.getSlot(slotIndex) != null) {
                            Minecraft.getMinecraft().playerController.windowClick(
                                    gui.inventorySlots.windowId,
                                    slotIndex,
                                    0,
                                    ClickType.QUICK_MOVE,
                                    Minecraft.getMinecraft().player);
                            zszlScriptMod.LOGGER.info("并行转移槽位 {} (批次 {} 进度 {}/4)", slotIndex, currentBatch / 4 + 1,
                                    i + 1);
                        }
                    }
                }).accept(Minecraft.getMinecraft().player);
            }
        });
    }

    public static String normalizeSimulatedKeyState(String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        if ("down".equals(normalized) || "robotdown".equals(normalized)) {
            return "Down";
        }
        if ("up".equals(normalized) || "robotup".equals(normalized)) {
            return "Up";
        }
        return "Press";
    }

    /**
     * 按键模拟方法。
     *
     * 所有键盘状态统一走当前客户端实例内注入，避免多开窗口互相串键。
     */
    public static void simulateKey(String Key, String State) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player
                    .sendMessage(new TextComponentString(String.format("§d[调试] §7模拟按键: §f%s, §7状态: §f%s", Key, State)));
        }
        try {
            SimulatedKeyInputManager.simulateKey(Key, State);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("模拟按键失败", e);
        }
    }

    /** Simulates a path action through this JVM's LWJGL keyboard event stream. */
    public static void simulateActionKey(String key, String state) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player
                    .sendMessage(new TextComponentString(String.format("§d[调试] §7模拟原始按键: §f%s, §7状态: §f%s", key,
                            state)));
        }
        try {
            SimulatedKeyInputManager.simulateActionKey(key, state);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("模拟菜单按键失败", e);
        }
    }

    public static void simulateActionKey(String key, String state, int pressDurationTicks, boolean background) {
        if (ModConfig.isDebugModeEnabled && Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(String.format(
                    "§d[调试] §7模拟路径按键: §f%s, §7状态: §f%s", key, state)));
        }
        try {
            SimulatedKeyInputManager.simulateActionKey(key, state, pressDurationTicks, background);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("模拟路径按键失败", e);
        }
    }

    /** Writes text to the system clipboard and injects Ctrl+V into the current Minecraft window. */
    public static void simulatePasteText(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        Runnable pasteTask = () -> {
            try {
                GuiScreen.setClipboardString(text == null ? "" : text);
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("写入系统剪贴板失败", e);
                return;
            }
            SimulatedKeyInputManager.simulateControlShortcut("V");
        };

        if (mc.isCallingFromMinecraftThread()) {
            pasteTask.run();
        } else {
            mc.addScheduledTask(pasteTask);
        }
    }

    /**
     * 模拟鼠标点击。
     * 默认优先走原版世界点击或当前 GUI 事件注入，仅在 GUI 注入失败时才回退到 AHK。
     */
    public static void simulateMouseClick(int x, int y, boolean isLeftClick, int originalWidth, int originalHeight) {
        simulateMouseClick(x, y, isLeftClick ? "left" : "right", originalWidth, originalHeight);
    }

    public static void simulateMouseClick(int x, int y, String mouseButton, int originalWidth, int originalHeight) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        final boolean withShift = isShiftModifiedMouseButton(mouseButton);
        final String baseMouseButton = getBaseMouseButton(mouseButton);
        Runnable task = () -> {
            Runnable clickTask = () -> {
                try {
                    if (trySimulateMouseClickDirect(mc, x, y, baseMouseButton, originalWidth, originalHeight)) {
                        debugMouseClickRoute("原生/GUI 注入", x, y, originalWidth, originalHeight, mouseButton);
                        return;
                    }

                    if (mc.currentScreen != null) {
                        performAhkMouseClickFallback(x, y, mouseButton, originalWidth, originalHeight);
                    }
                } catch (Exception e) {
                    zszlScriptMod.LOGGER.error("模拟鼠标点击失败", e);
                }
            };
            if (withShift) {
                SimulatedKeyInputManager.runWithSimulatedShift(clickTask);
            } else {
                clickTask.run();
            }
        };

        if (mc.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            mc.addScheduledTask(task);
        }
    }

    private static boolean trySimulateMouseClickDirect(Minecraft mc, int x, int y, String mouseButton, int originalWidth,
            int originalHeight) {
        if (mc == null) {
            return false;
        }
        if (mc.currentScreen == null) {
            return invokeMinecraftWorldClick(mc, mouseButton);
        }
        return invokeGuiClick(mc, x, y, mouseButton, originalWidth, originalHeight);
    }

    private static boolean invokeMinecraftWorldClick(Minecraft mc, String mouseButton) {
        String[] methodNames;
        if ("middle".equalsIgnoreCase(mouseButton)) {
            methodNames = new String[] { "middleClickMouse", "func_147112_ai", "pickBlock" };
        } else if ("right".equalsIgnoreCase(mouseButton)) {
            methodNames = new String[] { "rightClickMouse", "func_147121_ag" };
        } else {
            methodNames = new String[] { "clickMouse", "func_147116_af" };
        }
        for (String methodName : methodNames) {
            try {
                Method method = Minecraft.class.getDeclaredMethod(methodName);
                method.setAccessible(true);
                method.invoke(mc);
                return true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        zszlScriptMod.LOGGER.warn("无法触发 Minecraft 世界点击: button={}, methods={}", mouseButton,
                java.util.Arrays.toString(methodNames));
        return false;
    }

    private static boolean invokeGuiClick(Minecraft mc, int x, int y, String mouseButton, int originalWidth,
            int originalHeight) {
        if (mc == null || mc.currentScreen == null) {
            return false;
        }

        int currentScreenWidth = Math.max(1, mc.displayWidth);
        int currentScreenHeight = Math.max(1, mc.displayHeight);
        int sourceWidth = originalWidth <= 0 ? 2560 : originalWidth;
        int sourceHeight = originalHeight <= 0 ? 1334 : originalHeight;
        int actualX = scaleScreenCoordinate(x, sourceWidth, currentScreenWidth);
        int actualY = scaleScreenCoordinate(y, sourceHeight, currentScreenHeight);
        int button = "middle".equalsIgnoreCase(mouseButton) ? 2 : ("right".equalsIgnoreCase(mouseButton) ? 1 : 0);

        ScaledResolution resolution = new ScaledResolution(mc);
        int scaledX = actualX * resolution.getScaledWidth() / currentScreenWidth;
        int scaledY = actualY * resolution.getScaledHeight() / currentScreenHeight;
        try {
            invokeGuiScreenMouseMethod(mc.currentScreen, new String[] { "mouseClicked", "func_73864_a" },
                    scaledX, scaledY, button);
            invokeGuiScreenMouseMethod(mc.currentScreen, new String[] { "mouseReleased", "func_146286_b" },
                    scaledX, scaledY, button);
            return true;
        } catch (ReflectiveOperationException e) {
            zszlScriptMod.LOGGER.warn("GUI 点击注入失败: button={}, scaled=({}, {})", mouseButton, scaledX, scaledY, e);
        }
        return false;
    }

    private static void invokeGuiScreenMouseMethod(GuiScreen screen, String[] methodNames, int mouseX, int mouseY,
            int mouseButton) throws ReflectiveOperationException {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            for (String methodName : methodNames) {
                try {
                    Method method = type.getDeclaredMethod(methodName, int.class, int.class, int.class);
                    method.setAccessible(true);
                    method.invoke(screen, mouseX, mouseY, mouseButton);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        throw new NoSuchMethodException("未找到 GuiScreen 鼠标方法");
    }

    private static void performAhkMouseClickFallback(int x, int y, String mouseButton, int originalWidth,
            int originalHeight) {
        debugMouseClickRoute("AHK fallback", x, y, originalWidth, originalHeight, mouseButton);

        new Thread(() -> {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) {
                    return;
                }

                int currentScreenWidth = mc.displayWidth;
                int currentScreenHeight = mc.displayHeight;
                int sourceWidth = originalWidth <= 0 ? 2560 : originalWidth;
                int sourceHeight = originalHeight <= 0 ? 1334 : originalHeight;
                int actualX = scaleScreenCoordinate(x, sourceWidth, currentScreenWidth);
                int actualY = scaleScreenCoordinate(y, sourceHeight, currentScreenHeight);

                String xCoordinate = String.valueOf(actualX);
                String yCoordinate = String.valueOf(actualY);
                boolean withShift = isShiftModifiedMouseButton(mouseButton);
                String baseMouseButton = getBaseMouseButton(mouseButton);
                String buttonText = "middle".equalsIgnoreCase(baseMouseButton)
                        ? "Middle"
                        : ("right".equalsIgnoreCase(baseMouseButton) ? "Right" : "Left");

                if (ModConfig.ahkMoveMouseMode) {
                    AHKExecutor.executeTargetedAHKScript("MouseClick.ahk", Display.getTitle(), xCoordinate, yCoordinate,
                            buttonText, "true", String.valueOf(withShift));
                } else {
                    AHKExecutor.executeTargetedAHKScript("GuiClick.ahk", xCoordinate, yCoordinate, buttonText,
                            String.valueOf(withShift));
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("AHK fallback 模拟鼠标点击失败", e);
            }
        }).start();
    }

    private static void debugMouseClickRoute(String route, int x, int y, int originalWidth, int originalHeight,
            String mouseButton) {
        if (!ModConfig.isDebugFlagEnabled(DebugModule.AHK_EXECUTION)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null) {
            String debugMsg = String.format("§d[调试] §7模拟鼠标点击 [%s/%s]: 逻辑坐标(%d, %d), 原始分辨率(%dx%d)",
                    route, mouseButton, x, y, originalWidth, originalHeight);
            mc.player.sendMessage(new TextComponentString(debugMsg));
        }
    }

    private static boolean isShiftModifiedMouseButton(String mouseButton) {
        String normalized = mouseButton == null ? "" : mouseButton.trim().toLowerCase(Locale.ROOT);
        return "shift_left".equals(normalized) || "shift-left".equals(normalized)
                || "shift_right".equals(normalized) || "shift-right".equals(normalized);
    }

    private static String getBaseMouseButton(String mouseButton) {
        String normalized = mouseButton == null ? "" : mouseButton.trim().toLowerCase(Locale.ROOT);
        if ("shift_right".equals(normalized) || "shift-right".equals(normalized)) {
            return "right";
        }
        if ("shift_left".equals(normalized) || "shift-left".equals(normalized)) {
            return "left";
        }
        return normalized;
    }

    private static int scaleScreenCoordinate(int coordinate, int sourceSize, int currentSize) {
        int source = Math.max(1, sourceSize);
        int current = Math.max(1, currentSize);
        return Math.max(0, Math.min(current - 1,
                (int) Math.round(coordinate * (double) current / source)));
    }

    private static String normalizeDisplayName(String name) {
        String stripped = TextFormatting.getTextWithoutFormattingCodes(name);
        if (stripped == null) {
            stripped = name == null ? "" : name;
        }
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesDisplayName(String normalizedExpectedName, String actualDisplayName,
            AutoUseItemRule.MatchMode matchMode) {
        String normalizedActual = normalizeDisplayName(actualDisplayName);
        if (normalizedExpectedName.isEmpty() || normalizedActual.isEmpty()) {
            return false;
        }
        return matchMode == AutoUseItemRule.MatchMode.EXACT
                ? normalizedActual.equals(normalizedExpectedName)
                : normalizedActual.contains(normalizedExpectedName);
    }

    /**
     * 将背包中的指定物品移动到目标快捷栏槽位。
     *
     * @param itemName          物品显示名称（忽略颜色代码）
     * @param matchMode         匹配模式：包含 / 完全相同
     * @param targetHotbarSlot  目标快捷栏槽位（1-9）
     * @param count             移动数量；0 表示整堆
     * @param skipIfTargetOccupied 目标槽已有任意物品时是否跳过
     * @return 是否成功找到并移动物品
     */
    public static boolean moveInventoryItemToHotbar(String itemName, AutoUseItemRule.MatchMode matchMode,
            int targetHotbarSlot, int count, boolean skipIfTargetOccupied) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.openContainer == null || Minecraft.getMinecraft().playerController == null) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 玩家或容器不可用");
            return false;
        }

        String normalizedItemName = normalizeDisplayName(itemName);
        if (normalizedItemName.isEmpty()) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 物品名称为空");
            return false;
        }

        int safeHotbarSlot = Math.max(1, Math.min(9, targetHotbarSlot));
        int hotbarIndex = safeHotbarSlot - 1;
        if (skipIfTargetOccupied && !player.inventory.getStackInSlot(hotbarIndex).isEmpty()) {
            zszlScriptMod.LOGGER.info("跳过移动背包物品: 目标快捷栏槽位 {} 已有物品", safeHotbarSlot);
            return false;
        }
        int sourceInventorySlot = -1;
        ItemStack sourceStack = ItemStack.EMPTY;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (matchesDisplayName(normalizedItemName, stack.getDisplayName(), matchMode)) {
                sourceInventorySlot = i;
                sourceStack = stack;
                break;
            }
        }

        if (sourceInventorySlot < 0) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 未找到物品 '{}'", itemName);
            return false;
        }

        Container activeContainer = player.openContainer != null ? player.openContainer : player.inventoryContainer;
        int sourceContainerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player, sourceInventorySlot);
        int targetContainerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player, hotbarIndex);
        if (sourceContainerSlot < 0 || targetContainerSlot < 0) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 当前容器中找不到来源或目标槽位");
            return false;
        }

        int safeCount = Math.max(0, count);
        if (safeCount <= 0) {
            Minecraft.getMinecraft().playerController.windowClick(
                    activeContainer.windowId, sourceContainerSlot, hotbarIndex, ClickType.SWAP, player);
            return true;
        }

        if (!player.inventory.getItemStack().isEmpty()) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 鼠标上已有物品，无法安全拆分");
            return false;
        }

        ItemStack targetStack = player.inventory.getStackInSlot(hotbarIndex);
        boolean targetEmpty = targetStack.isEmpty();
        boolean targetMatches = !targetEmpty
                && ItemStack.areItemsEqual(sourceStack, targetStack)
                && ItemStack.areItemStackTagsEqual(sourceStack, targetStack);
        if (targetEmpty || targetMatches) {
            int capacity = targetEmpty ? sourceStack.getMaxStackSize()
                    : Math.max(0, targetStack.getMaxStackSize() - targetStack.getCount());
            int transferCount = Math.min(Math.min(safeCount, sourceStack.getCount()), capacity);
            if (transferCount <= 0) {
                zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 目标快捷栏槽位已满");
                return false;
            }
            return movePartialStack(activeContainer, player, sourceContainerSlot, targetContainerSlot, transferCount);
        }

        int transferCount = Math.min(safeCount, sourceStack.getCount());
        if (transferCount >= sourceStack.getCount()) {
            Minecraft.getMinecraft().playerController.windowClick(
                    activeContainer.windowId, sourceContainerSlot, hotbarIndex, ClickType.SWAP, player);
            return true;
        }

        int temporaryInventorySlot = findEmptyMainInventorySlot(player, sourceInventorySlot);
        int temporaryContainerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player,
                temporaryInventorySlot);
        if (temporaryInventorySlot < 0 || temporaryContainerSlot < 0) {
            zszlScriptMod.LOGGER.warn("移动背包物品到快捷栏失败: 目标槽有其他物品且背包没有空位用于拆分");
            return false;
        }
        if (!movePartialStack(activeContainer, player, sourceContainerSlot, temporaryContainerSlot, transferCount)) {
            return false;
        }
        Minecraft.getMinecraft().playerController.windowClick(
                activeContainer.windowId, temporaryContainerSlot, hotbarIndex, ClickType.SWAP, player);
        return true;
    }

    private static boolean movePartialStack(Container container, EntityPlayerSP player, int sourceContainerSlot,
            int targetContainerSlot, int count) {
        if (container == null || player == null || count <= 0 || Minecraft.getMinecraft().playerController == null) {
            return false;
        }
        Minecraft.getMinecraft().playerController.windowClick(
                container.windowId, sourceContainerSlot, 0, ClickType.PICKUP, player);
        for (int i = 0; i < count; i++) {
            Minecraft.getMinecraft().playerController.windowClick(
                    container.windowId, targetContainerSlot, 1, ClickType.PICKUP, player);
        }
        Minecraft.getMinecraft().playerController.windowClick(
                container.windowId, sourceContainerSlot, 0, ClickType.PICKUP, player);
        return player.inventory.getItemStack().isEmpty();
    }

    private static int findEmptyMainInventorySlot(EntityPlayerSP player, int excludedSlot) {
        if (player == null) {
            return -1;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (slot != excludedSlot && player.inventory.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean switchToHotbarSlot(int targetHotbarSlot) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.connection == null) {
            return false;
        }

        int safeHotbarSlot = Math.max(1, Math.min(9, targetHotbarSlot));
        int hotbarIndex = safeHotbarSlot - 1;
        if (player.inventory.currentItem == hotbarIndex) {
            return true;
        }

        player.inventory.currentItem = hotbarIndex;
        player.connection.sendPacket(new CPacketHeldItemChange(hotbarIndex));
        return true;
    }

    private static int findContainerSlotForPlayerInventoryIndex(Container container, EntityPlayerSP player,
            int inventorySlotIndex) {
        if (container == null || player == null) {
            return -1;
        }

        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot == null || slot.inventory != player.inventory) {
                continue;
            }
            if (slot.getSlotIndex() == inventorySlotIndex) {
                return i;
            }
        }
        return -1;
    }

    private static void scheduleDelayedAction(Runnable action, int delayTicks) {
        if (action == null) {
            return;
        }

        int safeDelayTicks = Math.max(0, delayTicks);
        if (safeDelayTicks <= 0 || DelayScheduler.instance == null) {
            action.run();
            return;
        }

        DelayScheduler.instance.schedule(action, safeDelayTicks);
    }

    public static boolean useHeldItemNow() {
        return triggerHeldItemUse(Minecraft.getMinecraft().player);
    }

    public static void useHeldItem(int delayTicks) {
        useHeldItem(Minecraft.getMinecraft().player, delayTicks);
    }

    public static void useHeldItem(EntityPlayerSP player, int delayTicks) {
        scheduleDelayedAction(() -> triggerHeldItemUse(player), delayTicks);
    }

    private static boolean triggerHeldItemUse(EntityPlayerSP player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == null || player.connection == null) {
            return false;
        }

        boolean triggered = false;
        if (mc.playerController != null && player.world != null) {
            EnumActionResult result = mc.playerController.processRightClick(player, player.world, EnumHand.MAIN_HAND);
            triggered = result != EnumActionResult.FAIL;
        }

        if (!triggered) {
            player.connection.sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
            triggered = true;
        }

        return triggered;
    }

    private static int getSilentUseAutoRestoreDelayTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1;
        }

        EnumAction action = stack.getItemUseAction();
        if (action == EnumAction.EAT || action == EnumAction.DRINK) {
            return Math.min(40, Math.max(2, stack.getMaxItemUseDuration() + 2));
        }
        if (action == EnumAction.BOW || action == EnumAction.BLOCK) {
            return 8;
        }
        return 1;
    }

    /**
     * [新方法] "静默"使用背包中的物品，例如食物或药水。
     * 通过快速的后台物品交换和数据包发送实现，尽量减少视觉上的干扰。
     *
     * @param itemName       要使用的物品的显示名称中包含的关键词 (例如 "牛排", "金苹果")。
     * @param tempHotbarSlot 临时用于放置和使用物品的快捷栏槽位 (0-8)。建议使用一个不常用的槽位。
     */
    public static void useItemFromInventory(String itemName, int tempHotbarSlot) {
        useItemFromInventory(itemName, tempHotbarSlot, -1, -1, -1);
    }

    public static void useItemFromInventory(String itemName, int tempHotbarSlot, int switchDelayTicks,
            int useDelayTicks, int switchBackDelayTicks) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.connection == null || Minecraft.getMinecraft().playerController == null) {
            zszlScriptMod.LOGGER.error("静默使用物品失败: 玩家或容器为空。");
            return;
        }

        String normalizedItemName = normalizeDisplayName(itemName);
        if (normalizedItemName.isEmpty()) {
            zszlScriptMod.LOGGER.warn("静默使用物品失败: 物品名称为空");
            return;
        }

        // 1. 在玩家背包中查找物品（包含快捷栏），并使用忽略颜色代码的显示名匹配
        int itemInvSlot = -1;
        ItemStack matchedStack = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (matchesDisplayName(normalizedItemName, stack.getDisplayName(), AutoUseItemRule.MatchMode.CONTAINS)) {
                itemInvSlot = i;
                matchedStack = stack.copy();
                break;
            }
        }

        if (itemInvSlot == -1) {
            zszlScriptMod.LOGGER.warn("静默使用物品失败: 未在背包中找到 '{}'。", itemName);
            return;
        }

        final int sourceInventorySlot = itemInvSlot;
        int originalHotbarSlot = player.inventory.currentItem;
        final int safeTempHotbarSlot = Math.max(0, Math.min(8, tempHotbarSlot));
        final boolean alreadyInHotbar = sourceInventorySlot >= 0 && sourceInventorySlot < 9;
        final int useHotbarSlot = alreadyInHotbar ? sourceInventorySlot : safeTempHotbarSlot;
        final int safeSwitchDelayTicks = switchDelayTicks >= 0 ? Math.max(0, switchDelayTicks)
                : (alreadyInHotbar ? 0 : 1);
        final int safeUseDelayTicks = useDelayTicks >= 0 ? Math.max(0, useDelayTicks) : 1;
        final int restoreDelayTicks = switchBackDelayTicks >= 0
                ? Math.max(0, switchBackDelayTicks)
                : getSilentUseAutoRestoreDelayTicks(matchedStack);

        if (!alreadyInHotbar) {
            Container activeContainer = player.openContainer != null ? player.openContainer : player.inventoryContainer;
            int sourceContainerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player,
                    sourceInventorySlot);
            if (sourceContainerSlot < 0) {
                zszlScriptMod.LOGGER.warn("静默使用物品失败: 当前容器中找不到来源槽位 {}", sourceInventorySlot);
                return;
            }

            Minecraft.getMinecraft().playerController.windowClick(
                    activeContainer.windowId,
                    sourceContainerSlot,
                    safeTempHotbarSlot,
                    ClickType.SWAP,
                    player);
        }

        scheduleDelayedAction(() -> {
            if (player.connection == null) {
                return;
            }

            player.connection.sendPacket(new CPacketHeldItemChange(useHotbarSlot));

            scheduleDelayedAction(() -> {
                if (player.connection == null) {
                    return;
                }

                player.connection.sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));

                scheduleDelayedAction(() -> {
                    if (player.connection != null) {
                        player.connection.sendPacket(new CPacketHeldItemChange(originalHotbarSlot));
                    }

                    if (alreadyInHotbar || Minecraft.getMinecraft().playerController == null) {
                        return;
                    }

                    Container activeContainer = player.openContainer != null ? player.openContainer
                            : player.inventoryContainer;
                    int restoreSourceContainerSlot = findContainerSlotForPlayerInventoryIndex(activeContainer, player,
                            sourceInventorySlot);
                    if (restoreSourceContainerSlot < 0) {
                        zszlScriptMod.LOGGER.warn("静默使用物品恢复失败: 当前容器中找不到来源槽位 {}", sourceInventorySlot);
                        return;
                    }

                    Minecraft.getMinecraft().playerController.windowClick(
                            activeContainer.windowId,
                            restoreSourceContainerSlot,
                            safeTempHotbarSlot,
                            ClickType.SWAP,
                            player);
                }, restoreDelayTicks);
            }, safeUseDelayTicks);
        }, safeSwitchDelayTicks);
    }

    /**
     * [新方法] 根据频道和HEX数据发送一个FML代理数据包。
     * 会自动替换 {id} 占位符为当前的会话ID。
     *
     * @param channel 目标频道 (例如 "OwlViewChannel")
     * @param hexData 要发送的16进制数据字符串
     */
    private static final Pattern HEX_PLACEHOLDER_PATTERN = Pattern
            .compile("\\{\\s*([a-zA-Z0-9_]+)\\s*(?:([+-])\\s*([0-9A-Fa-f]+))?\\s*\\}");

    private static byte[] getPlaceholderBytes(String placeholderName) {
        return CapturedIdRuleManager.getCapturedIdBytes(placeholderName);
    }

    private static String bytesToHexWithSpaces(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static byte[] applyOffsetToBytes(byte[] baseBytes, String sign, String offsetHex) {
        BigInteger base = new BigInteger(1, baseBytes);
        BigInteger offset = new BigInteger(offsetHex, 16);
        BigInteger mod = BigInteger.ONE.shiftLeft(baseBytes.length * 8);

        BigInteger result = "+".equals(sign) ? base.add(offset) : base.subtract(offset);
        result = result.mod(mod);

        byte[] raw = result.toByteArray();
        byte[] fixed = new byte[baseBytes.length];

        int copyLength = Math.min(raw.length, fixed.length);
        System.arraycopy(raw, raw.length - copyLength, fixed, fixed.length - copyLength, copyLength);
        return fixed;
    }

    private static String resolvePacketHexPlaceholders(String hexData) {
        String finalHexData = hexData;

        Matcher matcher = HEX_PLACEHOLDER_PATTERN.matcher(finalHexData);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String name = matcher.group(1);
            String sign = matcher.group(2);
            String offsetHex = matcher.group(3);

            byte[] baseBytes = getPlaceholderBytes(name);
            if (baseBytes == null || baseBytes.length == 0) {
                throw new IllegalStateException("HEX中需要 {" + name + "}，但未捕获到对应值。");
            }

            byte[] valueBytes;
            if (sign != null && offsetHex != null && !offsetHex.isEmpty()) {
                valueBytes = applyOffsetToBytes(baseBytes, sign, offsetHex);
            } else {
                valueBytes = baseBytes;
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(bytesToHexWithSpaces(valueBytes)));
        }
        matcher.appendTail(sb);
        finalHexData = sb.toString();

        if (finalHexData.contains("{")) {
            throw new IllegalStateException("HEX中存在无法识别的占位符: " + finalHexData);
        }

        return finalHexData;
    }

    public static void sendFmlPacket(String channel, String hexData) {
        if (!PerformanceMonitor.isFeatureEnabled("packet_send_fml")) {
            return;
        }

        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("packet_send_fml");
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().getConnection() == null) {
            zszlScriptMod.LOGGER.error("[发送数据包] 失败: 玩家或网络连接为空。");
            timer.stop();
            return;
        }

        try {
            String finalHexData = resolvePacketHexPlaceholders(hexData);
            byte[] data = parseHexToBytes(finalHexData);

            // 创建并发送数据包
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            FMLProxyPacket packet = new FMLProxyPacket(buffer, channel);
            Minecraft.getMinecraft().getConnection().sendPacket(packet);

            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                zszlScriptMod.LOGGER.info("[路径序列] 发送数据包 -> 频道: {}, HEX: {}", channel, finalHexData);
            }

        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[发送数据包] 构建或发送数据包时出错。频道: {}, HEX: {}", channel, hexData, e);
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player
                        .sendMessage(new TextComponentString(TextFormatting.RED + "[脚本错误] 发送数据包失败: " + e.getMessage()));
            }
        } finally {
            timer.stop();
        }
    }

    /**
     * [新方法] 根据 Packet ID 和 HEX 数据发送一个标准的 Minecraft 数据包。
     * 会自动替换 {id} 等占位符。
     *
     * @param packetId 目标数据包的整数ID
     * @param hexData  要发送的16进制数据字符串
     */
    public static void sendStandardPacketById(int packetId, String hexData) {
        if (!PerformanceMonitor.isFeatureEnabled("packet_send_standard")) {
            return;
        }

        PerformanceMonitor.PerformanceTimer timer = PerformanceMonitor.startTimer("packet_send_standard");
        NetHandlerPlayClient netHandler = Minecraft.getMinecraft().getConnection();
        if (netHandler == null) {
            zszlScriptMod.LOGGER.error("[发送数据包] 失败: NetHandlerPlayClient 为空。");
            timer.stop();
            return;
        }

        try {
            // 1. 根据ID获取Packet类
            Class<? extends Packet<?>> packetClass = PacketIdMapping.getClassById(packetId);
            if (packetClass == null) {
                String errorMsg = "[脚本错误] 无法发送数据包: 未知的 Packet ID: " + String.format("0x%02X", packetId);
                zszlScriptMod.LOGGER.error(errorMsg);
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + errorMsg));
                }
                return;
            }

            // 2. 实例化Packet
            Packet<?> packetToSend = packetClass.newInstance();

            // 3. 处理占位符
            String finalHexData = resolvePacketHexPlaceholders(hexData);

            // 4. 将HEX转换为字节数组
            byte[] bytes = parseHexToBytes(finalHexData);

            // 5. 使用PacketBuffer填充数据包
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
            packetToSend.readPacketData(buffer);

            // 6. 发送数据包
            netHandler.sendPacket(packetToSend);

            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                zszlScriptMod.LOGGER.info("[路径序列] 发送标准数据包 -> ID: 0x{}, 类: {}, HEX: {}",
                        String.format("%02X", packetId), packetClass.getSimpleName(), finalHexData);
            }

        } catch (Exception e) {
            String errorMsg = "[脚本错误] 发送标准数据包 (ID: " + String.format("0x%02X", packetId) + ") 时发生严重错误: "
                    + buildThrowableDetail(e);
            zszlScriptMod.LOGGER.error(errorMsg, e);
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + errorMsg));
            }
        } finally {
            timer.stop();
        }
    }

    public static void mockReceiveFmlPacket(String channel, String hexData) {
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().getConnection() == null) {
            zszlScriptMod.LOGGER.error("[模拟接收数据包] 失败: 玩家或网络连接为空。 channel={}", channel);
            return;
        }

        try {
            String finalHexData = resolvePacketHexPlaceholders(hexData);
            byte[] data = parseHexToBytes(finalHexData);

            // Owl 系频道优先走本地业务解码/分发，避免 FMLProxyPacket 直接 processPacket 依赖 Netty/FML 上下文导致 NPE
            if ("OwlViewChannel".equals(channel) || "OwlControlChannel".equals(channel)) {
                String decoded = OwlViewPacketDecoder.decode(channel, data);
                CapturedIdRuleManager.processPacket(channel, false, data, decoded);
                PacketFieldRuleManager.processPacket(channel, false, data, decoded, "MockReceiveFmlPacket");
                if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                    zszlScriptMod.LOGGER.info("[路径序列] 模拟接收Owl FML数据包(本地分发) <- 频道: {}, HEX: {}, 解码: {}",
                            channel, finalHexData, decoded);
                }
                return;
            }

            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            FMLProxyPacket packet = new FMLProxyPacket(buffer, channel);
            NetHandlerPlayClient clientHandler = Minecraft.getMinecraft().getConnection();
            if (clientHandler == null) {
                throw new IllegalStateException("NetHandlerPlayClient 为空");
            }
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Packet rawPacket = (Packet) packet;
            rawPacket.processPacket((net.minecraft.network.INetHandler) clientHandler);

            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                zszlScriptMod.LOGGER.info("[路径序列] 模拟接收FML数据包 <- 频道: {}, HEX: {}", channel, finalHexData);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[模拟接收数据包] 构建或注入FML包失败。频道: {}, HEX: {}", channel, hexData, e);
            if (Minecraft.getMinecraft().player != null) {
                String detail = buildThrowableDetail(e);
                Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentString(TextFormatting.RED + "[脚本错误] 模拟接收FML包失败: " + detail));
            }
        }
    }

    public static void mockReceiveStandardPacketById(int packetId, String hexData) {
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().getConnection() == null) {
            zszlScriptMod.LOGGER.error("[模拟接收数据包] 失败: 玩家或网络连接为空。 packetId={}", packetId);
            return;
        }

        try {
            Class<? extends Packet<?>> packetClass = PacketIdMapping.getClientboundClassById(packetId);
            if (packetClass == null) {
                String msg = "[脚本错误] 无法模拟接收: 未知或未加载的 CLIENTBOUND Packet ID: "
                        + String.format("0x%02X", packetId);
                zszlScriptMod.LOGGER.error(msg);
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + msg));
                }
                return;
            }

            Packet<?> packet = packetClass.newInstance();
            String finalHexData = resolvePacketHexPlaceholders(hexData);
            byte[] bytes = parseHexToBytes(finalHexData);
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(bytes));
            packet.readPacketData(buffer);

            // 直接在客户端主线程本地处理，避免注入 Netty pipeline 导致连接异常断开
            NetHandlerPlayClient clientHandler = Minecraft.getMinecraft().getConnection();
            if (clientHandler == null) {
                throw new IllegalStateException("NetHandlerPlayClient 为空");
            }
            @SuppressWarnings("unchecked")
            Packet<NetHandlerPlayClient> typedPacket = (Packet<NetHandlerPlayClient>) packet;
            typedPacket.processPacket(clientHandler);

            if (ModConfig.isDebugFlagEnabled(DebugModule.PATH_SEQUENCE)) {
                zszlScriptMod.LOGGER.info("[路径序列] 模拟接收标准数据包 <- ID: 0x{}, 类: {}, HEX: {}",
                        String.format("%02X", packetId), packetClass.getSimpleName(), finalHexData);
            }
        } catch (Exception e) {
            String msg = "[脚本错误] 模拟接收标准包(ID: " + String.format("0x%02X", packetId) + ") 失败: "
                    + e.getMessage();
            zszlScriptMod.LOGGER.error(msg, e);
            if (Minecraft.getMinecraft().player != null) {
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + msg));
            }
        }
    }

    private static byte[] parseHexToBytes(String hexData) {
        String cleanHex = hexData == null ? "" : hexData.replaceAll("\\s", "");
        if (cleanHex.isEmpty()) {
            return new byte[0];
        }
        if ((cleanHex.length() & 1) != 0) {
            cleanHex = "0" + cleanHex;
        }
        byte[] data = new byte[cleanHex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int index = i * 2;
            data[i] = (byte) Integer.parseInt(cleanHex.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String buildThrowableDetail(Throwable e) {
        if (e == null) {
            return "UnknownError";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName());
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            sb.append(": ").append(e.getMessage().trim());
        }
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append(" | cause=").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
                sb.append(": ").append(cause.getMessage().trim());
            }
        }
        return sb.toString();
    }

    private static void fireInboundPacket(Packet<?> packet) throws Exception {
        NetHandlerPlayClient connection = Minecraft.getMinecraft().getConnection();
        if (connection == null) {
            throw new IllegalStateException("NetHandlerPlayClient 为空");
        }
        NetworkManager networkManager = connection.getNetworkManager();
        if (networkManager == null) {
            throw new IllegalStateException("NetworkManager 为空");
        }

        Field channelField;
        try {
            channelField = NetworkManager.class.getDeclaredField("channel");
        } catch (NoSuchFieldException ex) {
            channelField = null;
            for (Field f : NetworkManager.class.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    channelField = f;
                    break;
                }
            }
            if (channelField == null) {
                throw new IllegalStateException("无法在 NetworkManager 中找到 Netty channel 字段");
            }
        }

        channelField.setAccessible(true);
        Channel channel = (Channel) channelField.get(networkManager);
        if (channel == null || channel.pipeline() == null) {
            throw new IllegalStateException("Netty channel 或 pipeline 为空");
        }

        channel.pipeline().fireChannelRead(packet);
    }
}

