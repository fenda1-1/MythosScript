package com.zszl.zszlScriptMod.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.PerformanceMonitor;
import com.zszl.zszlScriptMod.PerformanceMonitor.PerformanceTimer;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.config.DebugModule;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoEquipHandler {

    public static final AutoEquipHandler INSTANCE = new AutoEquipHandler();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CLICK_PACKET_TEMPLATE = "00 04 56 69 65 77 00 00 {id} 00 00 00 01 00 0C 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 00 00 00 12 4F 77 6C 56 69 65 77 5F 73 6C 6F 74 5F 63 6C 69 63 6B %s 00 00 00 00";
    private static final String PACKET_CHANNEL = "OwlViewChannel";
    private static final int DEFAULT_EQUIP_INTERVAL_TICKS = 3;
    private static final int MIN_EQUIP_INTERVAL_TICKS = 3;

    // 总开关（保留，默认开启；快捷键改为直接控制 enabled）
    public static boolean masterSwitchEnabled = true;

    public static boolean enabled = false;
    public static boolean smartActivationEnabled = false;
    public static int smartActivationRange = 5;
    public static int equipIntervalTicks = DEFAULT_EQUIP_INTERVAL_TICKS;
    public static Map<String, EquipmentSet> equipmentSets = new ConcurrentHashMap<>();
    public static String activeSetName = "";

    private static volatile boolean isBusy = false;
    private int smartActivationCheckCooldown = 0;

    public enum ArmorSlot {
        HELMET(EntityEquipmentSlot.HEAD, 5),
        CHESTPLATE(EntityEquipmentSlot.CHEST, 6),
        LEGGINGS(EntityEquipmentSlot.LEGS, 7),
        BOOTS(EntityEquipmentSlot.FEET, 8);

        public final EntityEquipmentSlot equipmentSlot;
        public final int containerSlotIndex;

        ArmorSlot(EntityEquipmentSlot slot, int containerSlotIndex) {
            this.equipmentSlot = slot;
            this.containerSlotIndex = containerSlotIndex;
        }
    }

    public static class SlotConfig {
        public String itemName = "";
        public boolean enabled = false;
        public boolean leaveOne = false;

        public SlotConfig() {
        }
    }

    public static class EquipmentSet {
        public Map<ArmorSlot, SlotConfig> slots = new EnumMap<>(ArmorSlot.class);
        public boolean sequentialEquip = false;

        public EquipmentSet() {
            for (ArmorSlot slot : ArmorSlot.values()) {
                slots.put(slot, new SlotConfig());
            }
        }
    }

    private static class ConfigWrapper {
        String activeSetName;
        Map<String, EquipmentSet> sets;
        boolean smartActivationEnabled;
        int smartActivationRange;
        int equipIntervalTicks;
    }

    static {
        loadConfig();
    }

    private AutoEquipHandler() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve("auto_equip_sets_v5.json");
    }

    public static void loadConfig() {
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<ConfigWrapper>() {
                }.getType();
                ConfigWrapper wrapper = GSON.fromJson(reader, type);
                if (wrapper != null) {
                    if (wrapper.sets != null)
                        equipmentSets = new ConcurrentHashMap<>(wrapper.sets);
                    activeSetName = wrapper.activeSetName;
                    smartActivationEnabled = wrapper.smartActivationEnabled;
                    smartActivationRange = wrapper.smartActivationRange > 0 ? wrapper.smartActivationRange : 5;
                    equipIntervalTicks = wrapper.equipIntervalTicks > 0
                            ? Math.max(MIN_EQUIP_INTERVAL_TICKS, wrapper.equipIntervalTicks)
                            : DEFAULT_EQUIP_INTERVAL_TICKS;

                    // 启动时默认关闭自动穿戴，避免“进游戏即自动开启”
                    enabled = false;
                }
            } catch (Exception e) {
                zszlScriptMod.LOGGER.error("加载自动穿戴配置集v5失败", e);
            }
        }
        if (equipmentSets.isEmpty()) {
            addSet("默认配置");
            setActiveSet("", false);
        }
    }

    public static void saveConfig() {
        Path configFile = getConfigFile();
        ConfigWrapper wrapper = new ConfigWrapper();
        wrapper.activeSetName = activeSetName;
        wrapper.sets = equipmentSets;
        wrapper.smartActivationEnabled = smartActivationEnabled;
        wrapper.smartActivationRange = smartActivationRange;
        wrapper.equipIntervalTicks = Math.max(MIN_EQUIP_INTERVAL_TICKS, equipIntervalTicks);
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(wrapper, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("保存自动穿戴配置集v5失败", e);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!PerformanceMonitor.isFeatureEnabled("auto_equip")) {
            if (enabled) {
                enabled = false;
            }
            return;
        }

        PerformanceTimer timer = PerformanceMonitor.startTimer("auto_equip");

        try {
            if (event.phase != TickEvent.Phase.END || mc.player == null) {
                return;
            }

            // --- 核心修改：检查总开关 ---
            if (!masterSwitchEnabled) {
                if (enabled)
                    enabled = false; // 确保在总开关关闭时，内部开关也关闭
                return;
            }

            if (smartActivationEnabled) {
                if (smartActivationCheckCooldown > 0) {
                    smartActivationCheckCooldown--;
                } else {
                    boolean mobNearby = isHostileMobNearby(smartActivationRange);
                    if (enabled != mobNearby) {
                        enabled = mobNearby;
                        if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EQUIP)) {
                            mc.player.sendMessage(
                                    new TextComponentString("§d[调试] §e自动穿戴智能激活: " + (enabled ? "§a开启" : "§c关闭")));
                        }
                    }
                    smartActivationCheckCooldown = 10;
                }
            }

            if (!enabled || isBusy || mc.currentScreen instanceof GuiChest || activeSetName == null
                    || activeSetName.isEmpty()) {
                return;
            }

            EquipmentSet activeSet = equipmentSets.get(activeSetName);
            if (activeSet == null)
                return;

            for (Map.Entry<ArmorSlot, SlotConfig> entry : activeSet.slots.entrySet()) {
                ArmorSlot armorSlot = entry.getKey();
                SlotConfig config = entry.getValue();

                if (!config.enabled || config.itemName.trim().isEmpty())
                    continue;

                ItemStack currentlyWorn = mc.player.getItemStackFromSlot(armorSlot.equipmentSlot);
                boolean isWearingCorrectItem = !currentlyWorn.isEmpty()
                        && currentlyWorn.getDisplayName().contains(config.itemName);

                if (!isWearingCorrectItem) {
                    boolean equipped = performEquip(armorSlot, config);
                    if (equipped) {
                        return;
                    } else if (activeSet.sequentialEquip) {
                        return;
                    }
                }
            }
        } finally {
            timer.stop();
        }
    }

    private boolean isHostileMobNearby(double range) {
        if (mc.world == null)
            return false;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity instanceof IMob && entity instanceof EntityLivingBase) {
                if (((EntityLivingBase) entity).getHealth() > 0 && !entity.isDead) {
                    if (mc.player.getDistance(entity) <= range) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean performEquip(ArmorSlot armorSlot, SlotConfig config) {
        int sourceInventorySlot = -1, totalItemCount = 0;
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.inventoryContainer.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getDisplayName().contains(config.itemName)) {
                totalItemCount += stack.getCount();
                if (sourceInventorySlot == -1)
                    sourceInventorySlot = i;
            }
        }

        if (sourceInventorySlot == -1)
            return false;

        if (config.leaveOne && totalItemCount <= 1) {
            if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EQUIP)) {
                mc.player.sendMessage(new TextComponentString(
                        String.format("§d[调试] §e跳过穿戴 [%s]，因为“留一件”已开启且背包中总数仅剩 %d 件。", config.itemName, totalItemCount)));
            }
            return false;
        }

        isBusy = true;
        if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EQUIP)) {
            mc.player.sendMessage(new TextComponentString(String.format("§d[调试] §7开始穿戴 [%s] 到 %s... (锁定 %d ticks)",
                    config.itemName, armorSlot.name(), Math.max(MIN_EQUIP_INTERVAL_TICKS, equipIntervalTicks))));
        }

        final int finalSourceSlot = sourceInventorySlot;
        sendClickPacket(finalSourceSlot);
        ModUtils.DelayScheduler.instance.schedule(() -> sendClickPacket(armorSlot.containerSlotIndex), 1);
        ModUtils.DelayScheduler.instance.schedule(() -> sendClickPacket(finalSourceSlot), 2);
        ModUtils.DelayScheduler.instance.schedule(() -> {
            if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EQUIP)) {
                mc.player.sendMessage(
                        new TextComponentString(String.format("§d[调试] §7穿戴 [%s] 完成，解除锁定。", config.itemName)));
            }
            isBusy = false;
        }, Math.max(MIN_EQUIP_INTERVAL_TICKS, equipIntervalTicks));
        return true;
    }

    public static Set<String> getAllSetNames() {
        return equipmentSets.keySet();
    }

    public static EquipmentSet getSet(String name) {
        return equipmentSets.get(name);
    }

    public static void addSet(String name) {
        if (name == null || name.trim().isEmpty() || equipmentSets.containsKey(name))
            return;
        equipmentSets.put(name, new EquipmentSet());
        saveConfig();
    }

    public static void deleteSet(String name) {
        if (name == null || !equipmentSets.containsKey(name))
            return;
        equipmentSets.remove(name);
        if (name.equals(activeSetName)) {
            setActiveSet("", false);
        }
        saveConfig();
    }

    public static void setActiveSet(String name, boolean isSmart) {
        if (name.equals(activeSetName) && isSmart == smartActivationEnabled) {
            activeSetName = "";
            smartActivationEnabled = false;
            enabled = false;
        } else {
            activeSetName = name;
            smartActivationEnabled = isSmart;
            enabled = !isSmart;
        }
        saveConfig();
    }

    private void sendClickPacket(int slotIndex) {
        if (!PerformanceMonitor.isFeatureEnabled("auto_equip") || !masterSwitchEnabled) {
            isBusy = false;
            return;
        }

        String sessionIdHex = PacketCaptureHandler.getSessionIdAsHex();
        if (sessionIdHex == null || mc.getConnection() == null) {
            zszlScriptMod.LOGGER.error("[自动穿戴] 发送点击包失败: 会话ID或连接为空。");
            isBusy = false;
            return;
        }
        String formattedSlot = String.format("%02X %02X %02X %02X", (slotIndex >> 24) & 0xFF, (slotIndex >> 16) & 0xFF,
                (slotIndex >> 8) & 0xFF, slotIndex & 0xFF);
        String hexPayload = String.format(CLICK_PACKET_TEMPLATE, formattedSlot).replace("{id}", sessionIdHex);
        try {
            ModUtils.sendFmlPacket(PACKET_CHANNEL, hexPayload);
            if (ModConfig.isDebugFlagEnabled(DebugModule.AUTO_EQUIP)) {
                zszlScriptMod.LOGGER.info("[自动穿戴] 发送点击包 -> 槽位: {} (0x{}), HEX: {}", slotIndex,
                        Integer.toHexString(slotIndex).toUpperCase(), hexPayload);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[自动穿戴] 构建或发送点击数据包时出错", e);
            isBusy = false;
        }
    }
}
