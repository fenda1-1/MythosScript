package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.List;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.FreecamFeatureHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Temporary world interaction owned by the automatic-follow editor. */
public final class AutoFollowAreaPicker {
    private static AutoFollowAreaPicker active;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final GuiScreen menu;
    private final World world;
    private final Entity player;
    private final BiConsumer<BlockPos, BlockPos> callback;
    private final PointPickingHud pickingHud;
    private BlockPos first;
    private BlockPos second;
    private BlockPos preview;
    private boolean singlePoint;
    private BiConsumer<BlockPos, Double> radiusCallback;
    private String radiusPrompt;
    private AutoFollowReturnSelection returnSelection;
    private Consumer<List<String>> returnCallback;
    private double[] previewRay;
    private int hoveredReturn = -1;
    private double rangeY;
    private long previewAt;
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer near = BufferUtils.createFloatBuffer(3);
    private final FloatBuffer far = BufferUtils.createFloatBuffer(3);

    private AutoFollowAreaPicker(BiConsumer<BlockPos, BlockPos> callback, String hudTitle) {
        this.callback = callback;
        menu = mc.currentScreen;
        world = mc.world;
        player = mc.player;
        pickingHud = new PointPickingHud(mc, hudTitle);
    }

    public static void startRadius(BiConsumer<BlockPos, Double> callback) {
        startRadius(callback, null);
    }

    public static void startRadius(BiConsumer<BlockPos, Double> callback, String prompt) {
        begin(null, false, null, null, callback, prompt);
    }

    public static void startPoint(Consumer<BlockPos> callback) {
        begin((first, second) -> callback.accept(first), true);
    }

    public static void start(BiConsumer<BlockPos, BlockPos> callback) { begin(callback, false); }

    static void startReturns(double[] area, List<String> existing, int replacement, Consumer<List<String>> callback) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        AutoFollowReturnSelection selection = new AutoFollowReturnSelection(area, existing, mc.player.posY, replacement);
        begin(null, false, selection, callback);
    }

    private static void begin(BiConsumer<BlockPos, BlockPos> callback, boolean singlePoint) {
        begin(callback, singlePoint, null, null);
    }

    private static void begin(BiConsumer<BlockPos, BlockPos> callback, boolean singlePoint,
            AutoFollowReturnSelection selection, Consumer<List<String>> returnCallback) {
        begin(callback, singlePoint, selection, returnCallback, null);
    }

    private static void begin(BiConsumer<BlockPos, BlockPos> callback, boolean singlePoint,
            AutoFollowReturnSelection selection, Consumer<List<String>> returnCallback,
            BiConsumer<BlockPos, Double> radiusCallback) {
        begin(callback, singlePoint, selection, returnCallback, radiusCallback, null);
    }

    private static void begin(BiConsumer<BlockPos, BlockPos> callback, boolean singlePoint,
            AutoFollowReturnSelection selection, Consumer<List<String>> returnCallback,
            BiConsumer<BlockPos, Double> radiusCallback, String radiusPrompt) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null || mc.player == null || mc.world == null) return;
        if (mc.player.isRiding() || !mc.player.isEntityAlive()) {
            messageKey("gui.point_picker.error.player_not_ready");
            return;
        }
        String hudTitle = selection != null
                ? tr("gui.point_picker.title.returns")
                : radiusCallback != null
                        ? tr("gui.point_picker.title.radius")
                        : singlePoint ? tr("gui.point_picker.title.single") : tr("gui.point_picker.title.area");
        AutoFollowAreaPicker picker = new AutoFollowAreaPicker(callback, hudTitle);
        picker.singlePoint = singlePoint;
        picker.radiusCallback = radiusCallback;
        picker.radiusPrompt = radiusPrompt;
        picker.returnSelection = selection;
        picker.returnCallback = returnCallback;
        picker.rangeY = mc.player.posY;
        MovementFeatureManager.setEnabled("freecam", true);
        FreecamFeatureHandler.tick();
        if (!FreecamFeatureHandler.isActive()) {
            MovementFeatureManager.setEnabled("freecam", false);
            messageKey("gui.point_picker.error.freecam");
            return;
        }
        active = picker;
        MinecraftForge.EVENT_BUS.register(picker);
        mc.displayGuiScreen(null);
        mc.setIngameFocus();
        if (radiusCallback != null) {
            message(radiusPrompt == null || radiusPrompt.isEmpty()
                    ? tr("gui.point_picker.start.radius") : radiusPrompt);
        } else if (selection != null) {
            message(tr("gui.point_picker.start.returns"));
        } else if (singlePoint) {
            message(tr("gui.point_picker.start.single"));
        } else {
            message(tr("gui.point_picker.start.area"));
        }
    }

    private BlockPos target(double originX, double originY, double originZ) {
        // Bind to Buffer's Java 8 descriptor even when javac runs on a newer JDK.
        ((Buffer) model).clear();
        ((Buffer) projection).clear();
        ((Buffer) viewport).clear();
        ((Buffer) near).clear();
        ((Buffer) far).clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        // Use the actual rendered camera, including view bobbing and camera-mod transforms.
        double[] ray = AutoFollowPickProjection.ray(model, projection, viewport, near, far, originX, originY, originZ);
        previewRay = ray;
        if (ray == null) return null;
        Vec3d start = new Vec3d(ray[0], ray[1], ray[2]);
        Vec3d end = new Vec3d(ray[3], ray[4], ray[5]);
        RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
        return hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.getBlockPos().up() : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void mouse(MouseEvent event) {
        if (active != this || mc.currentScreen != null || event.getButton() < 0 || event.getButton() > 2) return;
        event.setCanceled(true); // Neither mining, use-item nor pick-block may reach the player.
        if (!event.isButtonstate()) return;
        if (radiusCallback != null) {
            if (event.getButton() == 2) {
                if (first == null || second == null) messageKey("gui.point_picker.radius.need_confirm");
                else mc.addScheduledTask(() -> finish(true, true));
            } else {
                BlockPos pos = System.currentTimeMillis() - previewAt < 500 ? preview : null;
                if (pos == null) { messageKey("gui.point_picker.error.look_at_block"); return; }
                if (event.getButton() == 0) {
                    first = pos; second = null;
                    messageKey("gui.point_picker.radius.center_selected");
                } else if (first == null) messageKey("gui.point_picker.radius.need_center");
                else if (first.equals(pos)) messageKey("gui.point_picker.radius.too_small");
                else {
                    second = pos;
                    messageKey("gui.point_picker.radius.confirm",
                            String.format(java.util.Locale.ROOT, "%.1f", radius(first, second)));
                }
            }
            return;
        }
        if (returnSelection != null) {
            if (event.getButton() == 2) {
                mc.addScheduledTask(() -> finish(true, true));
            } else if (System.currentTimeMillis() - previewAt < 500) {
                if (event.getButton() == 1 && hoveredReturn >= 0) {
                    returnSelection.remove(hoveredReturn);
                    messageKey("gui.point_picker.returns.deleted", returnSelection.points.size());
                } else if (event.getButton() == 0 && hoveredReturn >= 0) {
                    messageKey("gui.point_picker.returns.hovered");
                } else if (event.getButton() == 0 && preview != null) {
                    double px = preview.getX() + 0.5, pz = preview.getZ() + 0.5;
                    if (!returnSelection.contains(px, pz)) messageKey("gui.point_picker.returns.outside");
                    else if (returnSelection.add(px, preview.getY(), pz)) {
                        messageKey("gui.point_picker.returns.added", returnSelection.points.size());
                    } else messageKey("gui.point_picker.returns.duplicate");
                }
                hoveredReturn = -1;
                previewAt = 0;
            }
            return;
        }
        if (event.getButton() == 2) {
            if (first == null || !singlePoint && second == null) {
                messageKey(singlePoint ? "gui.point_picker.single.need_point" : "gui.point_picker.area.need_both");
            } else {
                // Leave the mouse-event dispatch before reopening the screen.
                mc.addScheduledTask(() -> finish(true, true));
            }
            return;
        }
        if (singlePoint && event.getButton() != 0) return;
        // Commit exactly the location shown in the last frame, never a second ray from a different tick.
        BlockPos pos = System.currentTimeMillis() - previewAt < 500 ? preview : null;
        if (pos == null) {
            messageKey("gui.point_picker.error.look_at_block");
            return;
        }
        if (event.getButton() == 0) first = pos;
        else second = pos;
        String x = String.format(java.util.Locale.ROOT, "%.1f", pos.getX() + 0.5D);
        String z = String.format(java.util.Locale.ROOT, "%.1f", pos.getZ() + 0.5D);
        messageKey(singlePoint ? "gui.point_picker.single.selected"
                : event.getButton() == 0 ? "gui.point_picker.area.selected_first" : "gui.point_picker.area.selected_second", x, z);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (active != this || event.phase != TickEvent.Phase.END) return;
        if (mc.world != world || mc.player != player || !player.isEntityAlive()) {
            finish(false, false);
        } else if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) || mc.currentScreen != null
                || !FreecamFeatureHandler.isActive()) {
            finish(false, true);
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (active != this || mc.world != world || mc.player != player || mc.currentScreen != null) return;
        pickingHud.render(event);
    }

    private void finish(boolean confirm, boolean reopen) {
        if (active != this) return;
        active = null;
        MinecraftForge.EVENT_BUS.unregister(this);
        MovementFeatureManager.setEnabled("freecam", false);
        FreecamFeatureHandler.stop();
        try {
            if (confirm) {
                if (radiusCallback != null) radiusCallback.accept(first, radius(first, second));
                else if (returnSelection != null) returnCallback.accept(returnSelection.result());
                else callback.accept(first, second);
            }
            if (reopen) messageKey(confirm ? "gui.point_picker.finish.confirmed" : "gui.point_picker.finish.cancelled");
        } finally {
            if (reopen && mc.world == world && mc.player == player) mc.displayGuiScreen(menu);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void render(RenderWorldLastEvent event) {
        if (active != this || mc.world != world || mc.getRenderViewEntity() == null) return;
        Entity view = mc.getRenderViewEntity();
        double x = mc.getRenderManager().viewerPosX;
        double y = mc.getRenderManager().viewerPosY;
        double z = mc.getRenderManager().viewerPosZ;
        preview = target(x, y, z);
        hoveredReturn = returnSelection == null ? -1 : returnSelection.hit(previewRay);
        previewAt = System.currentTimeMillis();
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(2);
        try {
            BlockPos hover = hoveredReturn >= 0 ? null : preview;
            boolean outside = returnSelection != null && hover != null
                    && !returnSelection.contains(hover.getX() + 0.5, hover.getZ() + 0.5);
            box(hover, x, y, z, 0.95F, outside ? 0.2F : 0.8F, 0.2F);
            if (returnSelection != null) drawReturnSelection(x, y, z);
            if (hover != null) {
                RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(hover.down()).offset(-x, -y, -z), 1, 0.85F, 0.2F, 1);
            }
            if (first != null && second != null && !singlePoint && radiusCallback == null) {
                AxisAlignedBB range = new AxisAlignedBB(Math.min(first.getX(), second.getX()) + 0.5,
                        Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()) + 0.5,
                        Math.max(first.getX(), second.getX()) + 0.5, Math.max(first.getY(), second.getY()) + 2,
                        Math.max(first.getZ(), second.getZ()) + 0.5).offset(-x, -y, -z);
                RenderGlobal.renderFilledBox(range, 0.2F, 1, 0.35F, 0.08F);
                RenderGlobal.drawSelectionBoundingBox(range, 0.2F, 1, 0.35F, 1);
            }
            if (radiusCallback != null && first != null) {
                BlockPos edge = second != null ? second : preview;
                if (edge != null) drawRadius(first, radius(first, edge), x, y, z);
            }
            box(first, x, y, z, 0.2F, 1, 0.35F);
            box(second, x, y, z, 0.2F, 1, 0.35F);
            GlStateManager.enableTexture2D();
            if (returnSelection != null) {
                label(returnSelection.x1, rangeY + 1, returnSelection.z1, "点1", x, y, z);
                label(returnSelection.x2, rangeY + 1, returnSelection.z2, "点2", x, y, z);
                for (int i = 0; i < returnSelection.points.size(); i++) {
                    AutoFollowReturnSelection.Point p = returnSelection.points.get(i);
                    label(p.x, p.y, p.z, "回点 " + (i + 1) + (i == hoveredReturn ? " · 右键删除" : "")
                            + (returnSelection.contains(p.x, p.z) ? "" : " · 范围外"), x, y, z);
                }
            }
            label(first, radiusCallback != null ? "中心" : singlePoint ? "回点" : first != null && first.equals(second) ? "点1 / 点2" : "点1", x, y, z);
            if (second != null && !second.equals(first)) label(second, radiusCallback != null ? "半径 " + String.format(java.util.Locale.ROOT, "%.1f", radius(first, second)) + " 格 · 中键确认" : "点2", x, y, z);
        } finally {
            GL11.glLineWidth(lineWidth);
            GlStateManager.color(1, 1, 1, 1);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            GlStateManager.depthMask(depthWrite);
            if (depthTest) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static double radius(BlockPos center, BlockPos edge) {
        double dx = edge.getX() - (double) center.getX(), dy = edge.getY() - (double) center.getY(), dz = edge.getZ() - (double) center.getZ();
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0) / 10.0;
    }

    private void drawRadius(BlockPos center, double radius, double x, double y, double z) {
        // Pickup matching uses 3D distance: show a spherical wireframe, not a flat circle.
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_LINES, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 96; i++) {
            for (int end = 0; end < 2; end++) {
                double angle = (i + end) * Math.PI * 2 / 96, a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
                buffer.pos(center.getX() + 0.5 - x + (plane == 2 ? 0 : a),
                        center.getY() - y + (plane == 0 ? 0 : plane == 1 ? b : a),
                        center.getZ() + 0.5 - z + (plane == 0 || plane == 2 ? b : 0))
                        .color(0.2F, 1F, 0.35F, 0.85F).endVertex();
            }
        }
        tess.draw();
    }

    private void drawReturnSelection(double x, double y, double z) {
        AutoFollowReturnSelection selection = returnSelection;
        AxisAlignedBB range = new AxisAlignedBB(Math.min(selection.x1, selection.x2), rangeY,
                Math.min(selection.z1, selection.z2), Math.max(selection.x1, selection.x2), rangeY + 2,
                Math.max(selection.z1, selection.z2)).offset(-x, -y, -z);
        RenderGlobal.renderFilledBox(range, 0.2F, 0.8F, 1, 0.05F);
        RenderGlobal.drawSelectionBoundingBox(range, 0.2F, 0.8F, 1, 1);
        for (double[] corner : new double[][] {{selection.x1, selection.z1}, {selection.x2, selection.z2}}) {
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(corner[0] - 0.5, rangeY, corner[1] - 0.5,
                    corner[0] + 0.5, rangeY + 2, corner[1] + 0.5).offset(-x, -y, -z), 0.2F, 0.8F, 1, 1);
        }
        for (int i = 0; i < selection.points.size(); i++) {
            AutoFollowReturnSelection.Point p = selection.points.get(i);
            boolean highlight = i == hoveredReturn || !selection.contains(p.x, p.z);
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(p.x - 0.5, p.y, p.z - 0.5,
                    p.x + 0.5, p.y + 2, p.z + 0.5).offset(-x, -y, -z), highlight ? 1 : 0.2F, highlight ? 0.3F : 1, 0.35F, 1);
        }
    }

    private void box(BlockPos pos, double x, double y, double z, float r, float g, float b) {
        if (pos == null) return;
        RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1).offset(-x, -y, -z), r, g, b, 1);
    }

    private void label(BlockPos pos, String text, double x, double y, double z) {
        if (pos == null) return;
        label(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, text, x, y, z);
    }

    private void label(double px, double py, double pz, String text, double x, double y, double z) {
        double dx = px - x, dy = py + 2.25 - y, dz = pz - z;
        Vec3d look = mc.getRenderViewEntity().getLook(1);
        double depth = new Vec3d(dx, dy - mc.getRenderViewEntity().getEyeHeight(), dz).dotProduct(look);
        if (depth <= 0.1) return;
        // Scale with camera-space depth so the label keeps the same apparent size.
        double scale = depth * 0.003;
        GlStateManager.pushMatrix();
        GlStateManager.translate(dx, dy, dz);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0, 1, 0);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);
        GlStateManager.scale(-scale, -scale, scale);
        mc.fontRenderer.drawStringWithShadow(text, -mc.fontRenderer.getStringWidth(text) / 2F, 0, 0xFF55FF77);
        GlStateManager.popMatrix();
    }

    private static String tr(String key, Object... args) {
        return I18n.format(key, args);
    }

    private static void messageKey(String key, Object... args) {
        message(tr(key, args));
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null) {
            active.pickingHud.addMessage(text);
        } else if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(text));
        }
    }
}
