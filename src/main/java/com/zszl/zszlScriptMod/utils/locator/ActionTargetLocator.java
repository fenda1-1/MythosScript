package com.zszl.zszlScriptMod.utils.locator;

import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDropper;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ActionTargetLocator {

    public static final String CLICK_MODE_COORDINATE = "COORDINATE";
    public static final String CLICK_MODE_BUTTON_TEXT = "BUTTON_TEXT";
    public static final String CLICK_MODE_SLOT_TEXT = "SLOT_TEXT";
    public static final String CLICK_MODE_ELEMENT_PATH = "ELEMENT_PATH";

    public static final String SLOT_MODE_DIRECT = "DIRECT_SLOT";
    public static final String SLOT_MODE_ITEM_TEXT = "ITEM_TEXT";
    public static final String SLOT_MODE_EMPTY = "EMPTY_SLOT";
    public static final String SLOT_MODE_PATH = "SLOT_PATH";

    public static final String TARGET_MODE_POSITION = "POSITION";
    public static final String TARGET_MODE_NAME = "NAME";

    public static final String MATCH_MODE_CONTAINS = "CONTAINS";
    public static final String MATCH_MODE_EXACT = "EXACT";

    private static Field guiButtonListField;

    private ActionTargetLocator() {
    }

    public static final class ClickPoint {
        private final int x;
        private final int y;
        private final String description;

        public ClickPoint(int x, int y, String description) {
            this.x = x;
            this.y = y;
            this.description = description == null ? "" : description;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class SlotResult {
        private final int slotIndex;
        private final String description;

        public SlotResult(int slotIndex, String description) {
            this.slotIndex = slotIndex;
            this.description = description == null ? "" : description;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public String getDescription() {
            return description;
        }
    }

    public static ClickPoint resolveScreenClickPoint(String locatorMode, String locatorText, String matchMode) {
        String mode = safe(locatorMode).trim().toUpperCase(Locale.ROOT);
        if (mode.isEmpty() || CLICK_MODE_COORDINATE.equals(mode)) {
            return null;
        }
        if (CLICK_MODE_BUTTON_TEXT.equals(mode)) {
            return findButtonClickPoint(locatorText, matchMode);
        }
        if (CLICK_MODE_SLOT_TEXT.equals(mode)) {
            return findSlotClickPoint(locatorText, matchMode);
        }
        if (CLICK_MODE_ELEMENT_PATH.equals(mode)) {
            return findElementClickPointByPath(locatorText, matchMode);
        }
        return null;
    }

    public static boolean tryInvokeCurrentScreenClick(String locatorMode, String locatorText, String matchMode,
            boolean isLeftClick) {
        ClickPoint point = resolveScreenClickPoint(locatorMode, locatorText, matchMode);
        if (point == null) {
            return false;
        }
        return tryInvokeCurrentScreenClick(point.getX(), point.getY(), isLeftClick);
    }

    public static boolean tryInvokeCurrentScreenClick(String locatorMode, String locatorText, String matchMode,
            String mouseButton) {
        ClickPoint point = resolveScreenClickPoint(locatorMode, locatorText, matchMode);
        if (point == null) {
            return false;
        }
        return tryInvokeCurrentScreenClick(point.getX(), point.getY(), mouseButton);
    }

    public static boolean tryInvokeCurrentScreenClick(int x, int y, boolean isLeftClick) {
        return tryInvokeCurrentScreenClick(x, y, isLeftClick ? "left" : "right");
    }

    public static boolean tryInvokeCurrentScreenClick(int x, int y, String mouseButton) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (screen == null) {
            return false;
        }
        int mouseButtonCode = "middle".equalsIgnoreCase(mouseButton)
                ? 2
                : ("right".equalsIgnoreCase(mouseButton) ? 1 : 0);
        try {
            Method mouseClicked = resolveMethod(screen.getClass(), "mouseClicked", int.class, int.class, int.class);
            mouseClicked.invoke(screen, x, y, mouseButtonCode);
            Method mouseReleased = resolveMethod(screen.getClass(), "mouseReleased", int.class, int.class, int.class);
            mouseReleased.invoke(screen, x, y, mouseButtonCode);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static SlotResult resolveContainerSlot(String locatorMode, String locatorText, String matchMode) {
        String mode = safe(locatorMode).trim().toUpperCase(Locale.ROOT);
        if (mode.isEmpty() || SLOT_MODE_DIRECT.equals(mode)) {
            return null;
        }
        if (SLOT_MODE_ITEM_TEXT.equals(mode)) {
            return findContainerSlotByItemText(locatorText, matchMode);
        }
        if (SLOT_MODE_EMPTY.equals(mode)) {
            return findContainerEmptySlot();
        }
        if (SLOT_MODE_PATH.equals(mode)) {
            return findContainerSlotByPath(locatorText, matchMode);
        }
        return null;
    }

    public static BlockPos findNearbyInteractableBlock(String locatorText, String matchMode, double range) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || player.world == null) {
            return null;
        }

        String query = normalize(locatorText);
        if (query.isEmpty()) {
            return null;
        }

        int radius = Math.max(1, (int) Math.ceil(Math.max(1.0D, range)));
        double maxDistSq = range <= 0.0D ? radius * radius : range * range;
        BlockPos center = player.getPosition();
        List<BlockMatch> matches = new ArrayList<>();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distSq = x * x + y * y + z * z;
                    if (distSq > maxDistSq) {
                        continue;
                    }

                    BlockPos current = center.add(x, y, z);
                    IBlockState state = player.world.getBlockState(current);
                    if (!isInteractableBlock(state)) {
                        continue;
                    }

                    if (!matchesBlock(state, query, matchMode)) {
                        continue;
                    }
                    matches.add(new BlockMatch(current, distSq, state.getBlock().getLocalizedName()));
                }
            }
        }

        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(Comparator.comparingDouble(a -> a.distSq));
        return matches.get(0).pos;
    }

    public static Entity findNearbyEntity(String locatorText, String matchMode, double range) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || player.world == null) {
            return null;
        }

        String query = normalize(locatorText);
        if (query.isEmpty()) {
            return null;
        }

        double radius = Math.max(1.0D, range);
        AxisAlignedBB box = new AxisAlignedBB(player.getPosition()).grow(radius);
        List<EntityMatch> matches = new ArrayList<>();
        for (Entity entity : player.world.getEntitiesWithinAABB(Entity.class, box)) {
            if (entity == null || entity == player || !entity.isEntityAlive()) {
                continue;
            }
            String searchText = buildEntitySearchText(entity);
            if (!matches(searchText, query, matchMode)) {
                continue;
            }
            matches.add(new EntityMatch(entity, entity.getDistanceSq(player)));
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(Comparator.comparingDouble(a -> a.distSq));
        return matches.get(0).entity;
    }

    private static ClickPoint findButtonClickPoint(String locatorText, String matchMode) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (screen == null) {
            return null;
        }

        String query = normalize(locatorText);
        if (query.isEmpty()) {
            return null;
        }

        for (GuiButton button : getButtonList(screen)) {
            if (button == null || !button.visible || !button.enabled) {
                continue;
            }
            String label = stripFormatting(button.displayString);
            if (!matches(label, query, matchMode)) {
                continue;
            }
            return new ClickPoint(button.x + button.width / 2, button.y + button.height / 2,
                    "button:" + label);
        }

        GuiElementInspector.GuiElementInfo custom = findCustomElementByText(locatorText, matchMode);
        if (custom != null) {
            return new ClickPoint(custom.getX() + Math.max(1, custom.getWidth()) / 2,
                    custom.getY() + Math.max(1, custom.getHeight()) / 2,
                    custom.getPath());
        }
        return null;
    }

    private static ClickPoint findSlotClickPoint(String locatorText, String matchMode) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc == null ? null : mc.currentScreen;
        if (!(screen instanceof GuiContainer)) {
            return null;
        }

        String query = normalize(locatorText);
        if (query.isEmpty()) {
            return null;
        }

        GuiContainer gui = (GuiContainer) screen;
        int xSize = readIntField(gui, 176, "xSize", "field_146999_f");
        int ySize = readIntField(gui, 166, "ySize", "field_147000_g");
        int guiLeft = readIntField(gui, (screen.width - xSize) / 2, "guiLeft", "field_147003_i");
        int guiTop = readIntField(gui, (screen.height - ySize) / 2, "guiTop", "field_147009_r");

        for (Object slotObj : gui.inventorySlots.inventorySlots) {
            if (!(slotObj instanceof Slot)) {
                continue;
            }
            Slot slot = (Slot) slotObj;
            if (slot == null || !slot.getHasStack()) {
                continue;
            }
            String searchText = buildItemSearchText(slot.getStack());
            if (!matches(searchText, query, matchMode)) {
                continue;
            }
            return new ClickPoint(guiLeft + slot.xPos + 8, guiTop + slot.yPos + 8,
                    "slot:" + stripFormatting(slot.getStack().getDisplayName()));
        }
        return null;
    }

    private static SlotResult findContainerSlotByItemText(String locatorText, String matchMode) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || player.openContainer == null) {
            return null;
        }

        String query = normalize(locatorText);
        if (query.isEmpty()) {
            return null;
        }

        Container container = player.openContainer;
        for (int index : buildPreferredSlotOrder(container)) {
            if (index < 0 || index >= container.inventorySlots.size()) {
                continue;
            }
            Slot slot = container.getSlot(index);
            if (slot == null || !slot.getHasStack()) {
                continue;
            }
            String searchText = buildItemSearchText(slot.getStack());
            if (!matches(searchText, query, matchMode)) {
                continue;
            }
            return new SlotResult(index, stripFormatting(slot.getStack().getDisplayName()));
        }
        return null;
    }

    private static SlotResult findContainerEmptySlot() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        if (player == null || player.openContainer == null) {
            return null;
        }

        Container container = player.openContainer;
        for (int index : buildPreferredSlotOrder(container)) {
            if (index < 0 || index >= container.inventorySlots.size()) {
                continue;
            }
            Slot slot = container.getSlot(index);
            if (slot != null && !slot.getHasStack()) {
                return new SlotResult(index, "empty");
            }
        }
        return null;
    }

    private static ClickPoint findElementClickPointByPath(String locatorText, String matchMode) {
        GuiElementInspector.GuiElementInfo element = GuiElementInspector.findFirstByPath(locatorText, matchMode,
                GuiElementInspector.ElementType.BUTTON,
                GuiElementInspector.ElementType.SLOT,
                GuiElementInspector.ElementType.CUSTOM);
        if (element == null) {
            return null;
        }
        int centerX = element.getX() + Math.max(1, element.getWidth()) / 2;
        int centerY = element.getY() + Math.max(1, element.getHeight()) / 2;
        return new ClickPoint(centerX, centerY, element.getPath());
    }

    private static GuiElementInspector.GuiElementInfo findCustomElementByText(String locatorText, String matchMode) {
        GuiElementInspector.GuiSnapshot snapshot = GuiElementInspector.captureCurrentSnapshot();
        String query = normalize(locatorText);
        if (query.isEmpty() || snapshot.getElements().isEmpty()) {
            return null;
        }
        for (GuiElementInspector.GuiElementInfo element : snapshot.getElements()) {
            if (element == null || element.getType() != GuiElementInspector.ElementType.CUSTOM) {
                continue;
            }
            if (matches(element.getText(), query, matchMode)) {
                return element;
            }
        }
        return null;
    }

    private static SlotResult findContainerSlotByPath(String locatorText, String matchMode) {
        GuiElementInspector.GuiElementInfo element = GuiElementInspector.findFirstByPath(locatorText, matchMode,
                GuiElementInspector.ElementType.SLOT);
        if (element == null) {
            Integer parsedSlotIndex = parseSlotIndexFromPath(locatorText);
            return parsedSlotIndex == null ? null : new SlotResult(parsedSlotIndex.intValue(), "path:" + locatorText);
        }
        return new SlotResult(element.getSlotIndex(), element.getPath());
    }

    private static Integer parseSlotIndexFromPath(String locatorText) {
        String text = safe(locatorText).trim();
        if (text.isEmpty()) {
            return null;
        }
        int slotTagIndex = text.lastIndexOf("/slot[");
        if (slotTagIndex < 0) {
            slotTagIndex = text.lastIndexOf("slot[");
        }
        if (slotTagIndex < 0) {
            return null;
        }
        int leftBracket = text.indexOf('[', slotTagIndex);
        int rightBracket = leftBracket < 0 ? -1 : text.indexOf(']', leftBracket + 1);
        if (leftBracket < 0 || rightBracket <= leftBracket + 1) {
            return null;
        }
        try {
            return Integer.parseInt(text.substring(leftBracket + 1, rightBracket).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<Integer> buildPreferredSlotOrder(Container container) {
        List<Integer> indices = new ArrayList<>();
        if (container == null || container.inventorySlots == null) {
            return indices;
        }

        int primaryCount = container.inventorySlots.size();
        if (container instanceof ContainerChest) {
            try {
                primaryCount = Math.min(primaryCount,
                        ((ContainerChest) container).getLowerChestInventory().getSizeInventory());
            } catch (Exception ignored) {
            }
        }

        for (int i = 0; i < primaryCount; i++) {
            indices.add(i);
        }
        for (int i = primaryCount; i < container.inventorySlots.size(); i++) {
            indices.add(i);
        }
        return indices;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen screen) {
        if (screen == null) {
            return java.util.Collections.emptyList();
        }
        try {
            if (guiButtonListField == null) {
                guiButtonListField = resolveField(GuiScreen.class, "buttonList", "field_146292_n");
            }
            Object value = guiButtonListField == null ? null : guiButtonListField.get(screen);
            if (value instanceof List) {
                return (List<GuiButton>) value;
            }
        } catch (Exception ignored) {
        }
        return java.util.Collections.emptyList();
    }

    private static Field resolveField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method resolveMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static int readIntField(Object target, int fallback, String... names) {
        if (target == null) {
            return fallback;
        }
        try {
            Field field = resolveField(target.getClass(), names);
            Object value = field == null ? null : field.get(target);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static boolean matches(String sourceText, String normalizedQuery, String matchMode) {
        String normalizedSource = normalize(sourceText);
        if (normalizedSource.isEmpty() || normalizedQuery == null || normalizedQuery.isEmpty()) {
            return false;
        }
        if (MATCH_MODE_EXACT.equalsIgnoreCase(safe(matchMode))) {
            return normalizedSource.equals(normalizedQuery);
        }
        return normalizedSource.contains(normalizedQuery);
    }

    private static String buildItemSearchText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(stripFormatting(stack.getDisplayName()));
        ResourceLocation registryName = stack.getItem() == null ? null : stack.getItem().getRegistryName();
        if (registryName != null) {
            builder.append(' ').append(registryName.toString());
        }
        return builder.toString();
    }

    private static boolean matchesBlock(IBlockState state, String normalizedQuery, String matchMode) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        Block block = state.getBlock();
        if (matches(block.getLocalizedName(), normalizedQuery, matchMode)) {
            return true;
        }
        ResourceLocation registryName = block.getRegistryName();
        return registryName != null && matches(registryName.toString(), normalizedQuery, matchMode);
    }

    private static String buildEntitySearchText(Entity entity) {
        if (entity == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (entity.getDisplayName() != null) {
            builder.append(stripFormatting(entity.getDisplayName().getUnformattedText()));
        }
        String entityName = safe(entity.getName()).trim();
        if (!entityName.isEmpty()) {
            builder.append(' ').append(stripFormatting(entityName));
        }
        builder.append(' ').append(entity.getClass().getSimpleName());
        return builder.toString();
    }

    private static String normalize(String text) {
        String stripped = stripFormatting(text).trim().toLowerCase(Locale.ROOT);
        return stripped.replace('\u3000', ' ').replaceAll("\\s+", " ");
    }

    private static String stripFormatting(String text) {
        String cleaned = TextFormatting.getTextWithoutFormattingCodes(safe(text));
        return cleaned == null ? safe(text) : cleaned;
    }

    private static boolean isInteractableBlock(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof BlockChest
                || block instanceof BlockEnderChest
                || block instanceof BlockWorkbench
                || block instanceof BlockFurnace
                || block instanceof BlockAnvil
                || block instanceof BlockBrewingStand
                || block instanceof BlockHopper
                || block instanceof BlockDispenser
                || block instanceof BlockDropper
                || block instanceof BlockDoor
                || block instanceof BlockTrapDoor
                || block instanceof BlockFenceGate
                || block instanceof BlockLever
                || block instanceof BlockButton
                || block instanceof BlockContainer;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class EntityMatch {
        private final Entity entity;
        private final double distSq;

        private EntityMatch(Entity entity, double distSq) {
            this.entity = entity;
            this.distSq = distSq;
        }
    }

    private static final class BlockMatch {
        private final BlockPos pos;
        private final double distSq;
        @SuppressWarnings("unused")
        private final String label;

        private BlockMatch(BlockPos pos, double distSq, String label) {
            this.pos = pos;
            this.distSq = distSq;
            this.label = label;
        }
    }
}
