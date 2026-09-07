package com.zszl.zszlScriptMod.gui.modern;

import java.util.Locale;

import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import net.minecraft.client.resources.I18n;

/**
 * Shared modern tab for the additional-feature catalog.
 *
 * <p>The catalog contains many small toggles, but their state is owned by
 * separate managers. This adapter keeps one visual form and delegates all
 * persistence to those managers. Every supported control is rendered through
 * the native modern form pipeline, so the complete workflow remains in the
 * main-window tab instead of nesting another GuiScreen.</p>
 */
public final class ModernOtherFeatureSettingsTab {

    private static final String PREFIX = "other_feature:";

    private ModernOtherFeatureSettingsTab() {
    }

    public static boolean isCommand(String command) {
        return command != null && command.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static String featureIdFromCommand(String command) {
        if (!isCommand(command)) {
            return "";
        }
        return command.substring(PREFIX.length()).trim().toLowerCase(Locale.ROOT);
    }

    public static String commandFor(String featureId) {
        return PREFIX + (featureId == null ? "" : featureId.trim().toLowerCase(Locale.ROOT));
    }

    public static String resolveDisplayName(String featureId, String fallback) {
        FeatureAccess access = resolve(featureId, fallback, "");
        return access == null || access.name.isEmpty() ? safe(fallback) : access.name;
    }

    public static ModernSettingsTab createFromCommand(String command) {
        return create(featureIdFromCommand(command), "", "");
    }

    public static ModernSettingsTab create(String featureId, String featureName, String description) {
        FeatureAccess access = resolve(featureId, featureName, description);
        if (access == null) {
            return ModernFormSettingsTab.builder(text("gui.other_feature.title", "其他功能"),
                    text("gui.other_feature.unavailable", "功能不可用"),
                    text("gui.other_feature.missing_manager", "无法找到该功能的配置管理器。"),
                    emptyAdapter()).section("状态", "").readOnly("功能标识", "请求的功能标识。", safe(featureId)).build();
        }

        ModernFormSettingsTab.Builder<Object> builder = ModernFormSettingsTab.builder(access.name,
                access.subtitle(), access.description, adapter(access))
                .headerToggle(access.enabledValue(), text("gui.other_feature.master_toggle_tip", "控制该功能的总开关；修改会在保存配置后保留。"))
                .section(text("gui.other_feature.status", "状态与反馈"),
                        text("gui.other_feature.status_tip", "集中管理 HUD 反馈并查看当前处理器的实时状态。"));
        if (access.supportsHud()) {
            builder.toggle(text("gui.other_feature.status_hud", "状态 HUD"),
                    text("gui.other_feature.status_hud_tip", "控制该功能是否在总状态 HUD 中显示。总状态 HUD 关闭时不会显示。"),
                    access.hudValue());
        }
        builder.readOnly(text("gui.other_feature.runtime", "运行状态"),
                text("gui.other_feature.runtime_tip", "当前处理器的实时摘要。"), access.runtimeValue());

        if (access.supportsValue()) {
            builder.section(text("gui.other_feature.core_parameters", "核心参数"),
                    text("gui.other_feature.core_parameters_tip", "调整该功能最主要的运行参数；输入值会自动限制在安全范围内。"));
            if (access.integerValue()) {
                builder.integer(access.valueLabel, "范围 " + access.valueRange() + "。保存后生效。", access.intValue(),
                        Math.round(access.minValue), Math.round(access.maxValue));
            } else {
                builder.decimal(access.valueLabel, "范围 " + access.valueRange() + "。保存后生效。", access.floatValue(),
                        access.minValue, access.maxValue);
            }
        }

        appendSpecialFields(builder, access);
        builder.section(text("gui.other_feature.description", "功能说明"),
                text("gui.other_feature.description_tip", "核对功能用途、配置归属和内部标识。"))
                .readOnly(text("gui.other_feature.purpose", "用途"), access.description, access.description)
                .readOnly(text("gui.other_feature.category", "功能分类"),
                        text("gui.other_feature.category_tip", "该功能所属的配置与运行模块。"), access.subtitle())
                .readOnly(text("gui.other_feature.id", "功能标识"),
                        text("gui.other_feature.id_tip", "用于配置文件、命令和快捷键定位。"), access.id);
        return builder.build();
    }

    private static void appendSpecialFields(ModernFormSettingsTab.Builder<Object> builder, final FeatureAccess access) {
        if (access.kind == Kind.MOVEMENT && "wall_climb".equals(access.id)) {
            builder.section(text("gui.other_feature.web", "吐丝吸附"),
                    text("gui.other_feature.web_tip", "开启爬墙后，中键向准星所指墙面或天花板吐丝；再次中键、空格或潜行取消。最远 64 格。"))
                    .toggle(text("gui.other_feature.web_enabled", "启用吐丝"),
                            text("gui.other_feature.web_enabled_tip", "默认开启；关闭后恢复中键原有操作。"),
                            new ModernFormSettingsTab.BooleanValue() {
                                @Override public boolean get() { return MovementFeatureManager.isWallClimbWebEnabled(); }
                                @Override public void set(boolean value) { MovementFeatureManager.setWallClimbWebEnabled(value); }
                            })
                    .decimal(text("gui.other_feature.web_speed", "吸附速度"),
                            text("gui.other_feature.web_speed_tip", "每 tick 移动的格数，范围 0.10–5.00，默认 1.00。"),
                            new ModernFormSettingsTab.FloatValue() {
                                @Override public float get() { return MovementFeatureManager.getWallClimbWebSpeed(); }
                                @Override public void set(float value) { MovementFeatureManager.setWallClimbWebSpeed(value); }
                            }, 0.1F, 5.0F);
        } else if (access.kind == Kind.SPEED) {
            builder.section(text("gui.other_feature.speed_strategy", "加速策略"), text("gui.other_feature.speed_strategy_tip", "速度模式和预设会同时影响地面、空中与跳跃行为。"))
                    .choice(text("gui.other_feature.mode", "模式"), text("gui.other_feature.mode_tip", "选择加速行为模式。"), access.speedModeValue(),
                            ModernFormSettingsTab.options(
                                    ModernFormSettingsTab.option(SpeedHandler.MODE_GROUND, "Ground"),
                                    ModernFormSettingsTab.option(SpeedHandler.MODE_AIR, "Air"),
                                    ModernFormSettingsTab.option(SpeedHandler.MODE_BHOP, "Bhop"),
                                    ModernFormSettingsTab.option(SpeedHandler.MODE_LOWHOP, "LowHop"),
                                    ModernFormSettingsTab.option(SpeedHandler.MODE_ONGROUND, "OnGround")))
                    .choice(text("gui.other_feature.preset", "预设"), text("gui.other_feature.preset_tip", "预设会批量调整速度参数。"), access.speedPresetValue(),
                            ModernFormSettingsTab.options(
                                    ModernFormSettingsTab.option(SpeedHandler.PRESET_SAFE, "稳妥"),
                                    ModernFormSettingsTab.option(SpeedHandler.PRESET_BALANCED, "平衡"),
                                    ModernFormSettingsTab.option(SpeedHandler.PRESET_AGGRESSIVE, "激进"),
                                    ModernFormSettingsTab.option(SpeedHandler.PRESET_CUSTOM, "自定义")))
                    .toggle(text("gui.other_feature.timer_boost", "Timer 加速"), text("gui.other_feature.timer_boost_tip", "是否同时调整客户端 tick 节奏。"), access.speedTimerValue())
                    .toggle(text("gui.other_feature.feature_hud", "单项状态 HUD"), text("gui.other_feature.feature_hud_tip", "在状态 HUD 中显示速度模式和参数。"), access.speedHudValue())
                    .decimal(text("gui.other_feature.timer_multiplier", "Timer 倍率"), "范围 1.00 - 2.50。", access.speedTimerSpeedValue(), 1.00F, 2.50F)
                    .decimal(text("gui.other_feature.horizontal_speed", "水平速度"), "范围 0.10 - 3.00。", access.speedVanillaValue(), 0.10F, 3.00F)
                    .decimal(text("gui.other_feature.jump_height", "跳跃高度"), "范围 0.00 - 1.00；仅跳跃模式使用。", access.speedJumpValue(), 0.00F, 1.00F)
                    .enabledWhen(new ModernFormSettingsTab.EnabledPredicate() {
                        @Override
                        public boolean matches() {
                            return access.speedUsesJumpHeight();
                        }
                    });
        } else if (access.kind == Kind.LAVA_WALK) {
            builder.section(text("gui.other_feature.liquid_strategy", "液体策略"), text("gui.other_feature.liquid_strategy_tip", "控制水面、危险液体和潜行下沉行为。"))
                    .toggle(text("gui.other_feature.water_walk", "水面可行走"), text("gui.other_feature.water_walk_tip", "普通水面是否也视作可行走平台。"), access.lavaWaterValue())
                    .toggle(text("gui.other_feature.dangerous_liquids", "仅危险液体"), text("gui.other_feature.dangerous_liquids_tip", "开启后仅处理岩浆等危险液体。"), access.lavaDangerousValue())
                    .toggle(text("gui.other_feature.sink_while_sneaking", "潜行时下沉"), text("gui.other_feature.sink_while_sneaking_tip", "按住潜行时允许主动沉入液体。"), access.lavaSneakValue());
        } else if (access.kind == Kind.ITEM && access.hasItemTiming()) {
            builder.section(text("gui.other_feature.execution_timing", "执行节奏"), text("gui.other_feature.execution_timing_tip", "控制物品功能执行频率，避免每 tick 重复处理。"))
                    .integer(text("gui.other_feature.execution_interval", "执行间隔"), text("gui.other_feature.execution_interval_tip", "按功能限制范围调整 tick 间隔。"), access.itemTimingValue(),
                            access.itemTimingMinimum(), access.itemTimingMaximum());
            if ("drop_all".equals(access.id)) {
                builder.text(text("gui.other_feature.drop_expression", "丢弃表达式"), text("gui.other_feature.drop_expression_tip", "每行一条过滤表达式；命中任意表达式的物品会被自动丢弃。"),
                        access.dropExpressionsValue(), "示例: name contains 垃圾", 2048);
            }
        } else if (access.kind == Kind.MISC && "auto_reconnect".equals(access.id)) {
            builder.section(text("gui.other_feature.reconnect_strategy", "重连策略"), text("gui.other_feature.reconnect_strategy_tip", "配置断线后的等待时间、尝试上限和持续重试方式。"))
                    .integer(text("gui.other_feature.reconnect_delay", "重连延迟"), text("gui.other_feature.reconnect_delay_tip", "断线后等待多少 tick 再发起重连。"), access.autoReconnectDelayValue(), 5, 200)
                    .toggle(text("gui.other_feature.infinite_retries", "无限重试"), text("gui.other_feature.infinite_retries_tip", "开启后忽略最大尝试次数，持续按延迟重连。"), access.autoReconnectInfiniteValue())
                    .integer(text("gui.other_feature.max_attempts", "最大尝试次数"), text("gui.other_feature.max_attempts_tip", "关闭无限重试后允许连续尝试的次数。"),
                            access.autoReconnectAttemptsValue(), 1, 10)
                    .enabledWhen(new ModernFormSettingsTab.EnabledPredicate() {
                        @Override
                        public boolean matches() {
                            return !MiscFeatureManager.isAutoReconnectInfiniteAttempts();
                        }
                    });
        } else if (access.kind == Kind.MISC && "auto_respawn".equals(access.id)) {
            builder.section(text("gui.other_feature.respawn_strategy", "复活策略"), text("gui.other_feature.respawn_strategy_tip", "控制死亡界面出现后自动复活的等待时间。"))
                    .integer(text("gui.other_feature.respawn_delay", "复活冷却"), "范围 1-100 tick。", access.autoRespawnDelayValue(), 1, 100);
        }
    }

    private static ModernFormSettingsTab.StateAdapter<Object> emptyAdapter() {
        return new ModernFormSettingsTab.StateAdapter<Object>() {
            @Override public void load() { }
            @Override public Object capture() { return null; }
            @Override public void restore(Object state) { }
        };
    }

    private static ModernFormSettingsTab.StateAdapter<Object> adapter(final FeatureAccess access) {
        return new ModernFormSettingsTab.StateAdapter<Object>() {
            @Override
            public void load() {
                access.load();
            }

            @Override
            public Object capture() {
                return null;
            }

            @Override
            public void restore(Object state) {
            }

            @Override
            public void save() {
                access.save();
            }

            @Override
            public boolean supportsDefaults() {
                return true;
            }

            @Override
            public void restoreDefaults() {
                access.reset();
            }
        };
    }

    private enum Kind {
        SPEED, LAVA_WALK, MOVEMENT, BLOCK, RENDER, WORLD, ITEM, MISC
    }

    private static final class FeatureAccess {
        private final String id;
        private final String name;
        private final String description;
        private final Kind kind;
        private final String valueLabel;
        private final float minValue;
        private final float maxValue;

        private FeatureAccess(String id, String name, String description, Kind kind, String valueLabel,
                float minValue, float maxValue) {
            this.id = safe(id).toLowerCase(Locale.ROOT);
            this.name = safe(name);
            this.description = safe(description);
            this.kind = kind;
            this.valueLabel = safe(valueLabel);
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        private String subtitle() {
            switch (kind) {
            case SPEED: return text("gui.other_feature.subtitle.speed", "移动增强 · 模式与节奏");
            case LAVA_WALK: return text("gui.other_feature.subtitle.lava_walk", "移动增强 · 液体策略");
            case RENDER: return text("gui.other_feature.subtitle.render", "渲染增强 · 显示参数");
            case BLOCK: return text("gui.other_feature.subtitle.block", "方块增强 · 交互策略");
            case WORLD: return text("gui.other_feature.subtitle.world", "世界增强 · 环境与 HUD");
            case ITEM: return text("gui.other_feature.subtitle.item", "物品增强 · 自动处理");
            case MISC: return text("gui.other_feature.subtitle.misc", "杂项增强 · 客户端辅助");
            default: return text("gui.other_feature.subtitle.default", "移动增强 · 通用设置");
            }
        }

        private boolean supportsValue() { return !valueLabel.isEmpty() && maxValue > minValue; }
        private boolean supportsHud() { return kind != Kind.RENDER; }
        private boolean integerValue() { return "auto_step".equals(id) || "trajectory_line".equals(id); }
        private String valueRange() { return format(minValue) + " - " + format(maxValue); }

        private ModernFormSettingsTab.BooleanValue enabledValue() {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { return enabled(); }
                @Override public void set(boolean value) { setEnabled(value); }
            };
        }

        private ModernFormSettingsTab.BooleanValue hudValue() {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { return hud(); }
                @Override public void set(boolean value) { setHud(value); }
            };
        }

        private ModernFormSettingsTab.ReadOnlyValue runtimeValue() {
            return new ModernFormSettingsTab.ReadOnlyValue() {
                @Override public String get() { return runtime(); }
            };
        }

        private ModernFormSettingsTab.IntValue intValue() {
            return new ModernFormSettingsTab.IntValue() {
                @Override public int get() { return Math.round(value()); }
                @Override public void set(int value) { setValue(value); }
            };
        }

        private ModernFormSettingsTab.FloatValue floatValue() {
            return new ModernFormSettingsTab.FloatValue() {
                @Override public float get() { return value(); }
                @Override public void set(float value) { setValue(value); }
            };
        }

        private boolean enabled() {
            switch (kind) {
            case SPEED: return SpeedHandler.enabled;
            case MOVEMENT: return MovementFeatureManager.isEnabled(id);
            case BLOCK: return BlockFeatureManager.isEnabled(id);
            case RENDER: return RenderFeatureManager.isEnabled(id);
            case WORLD: return WorldFeatureManager.isEnabled(id);
            case ITEM: return ItemFeatureManager.isEnabled(id);
            default: return MiscFeatureManager.isEnabled(id);
            }
        }

        private void setEnabled(boolean value) {
            switch (kind) {
            case SPEED: SpeedHandler.INSTANCE.setEnabled(value); break;
            case MOVEMENT: MovementFeatureManager.setEnabled(id, value); break;
            case BLOCK: BlockFeatureManager.setEnabled(id, value); break;
            case RENDER: RenderFeatureManager.setEnabled(id, value); break;
            case WORLD: WorldFeatureManager.setEnabled(id, value); break;
            case ITEM: ItemFeatureManager.setEnabled(id, value); break;
            default: MiscFeatureManager.setEnabled(id, value); break;
            }
        }

        private boolean hud() {
            if (kind == Kind.SPEED) return SpeedHandler.showStatusHud;
            if (kind == Kind.MOVEMENT) return MovementFeatureManager.isFeatureStatusHudEnabled(id);
            if (kind == Kind.BLOCK) return BlockFeatureManager.isFeatureStatusHudEnabled(id);
            if (kind == Kind.WORLD) return WorldFeatureManager.isFeatureStatusHudEnabled(id);
            if (kind == Kind.ITEM) return ItemFeatureManager.isFeatureStatusHudEnabled(id);
            if (kind == Kind.MISC) return MiscFeatureManager.isFeatureStatusHudEnabled(id);
            return true;
        }

        private void setHud(boolean value) {
            if (kind == Kind.SPEED) { SpeedHandler.showStatusHud = value; return; }
            if (kind == Kind.MOVEMENT) { MovementFeatureManager.setFeatureStatusHudEnabled(id, value); return; }
            if (kind == Kind.BLOCK) { BlockFeatureManager.setFeatureStatusHudEnabled(id, value); return; }
            if (kind == Kind.WORLD) { WorldFeatureManager.setFeatureStatusHudEnabled(id, value); return; }
            if (kind == Kind.ITEM) { ItemFeatureManager.setFeatureStatusHudEnabled(id, value); return; }
            if (kind == Kind.MISC) { MiscFeatureManager.setFeatureStatusHudEnabled(id, value); }
        }

        private float value() {
            if (kind == Kind.MOVEMENT || kind == Kind.LAVA_WALK) {
                MovementFeatureManager.FeatureState state = MovementFeatureManager.getFeature(id);
                return state == null ? minValue : state.getValue();
            }
            if (kind == Kind.BLOCK) {
                BlockFeatureManager.FeatureState state = BlockFeatureManager.getFeature(id);
                return state == null ? minValue : state.getValue();
            }
            if (kind == Kind.WORLD) {
                WorldFeatureManager.FeatureState state = WorldFeatureManager.getFeature(id);
                return state == null ? minValue : state.getValue();
            }
            if (kind == Kind.ITEM) {
                ItemFeatureManager.FeatureState state = ItemFeatureManager.getFeature(id);
                return state == null ? minValue : state.getValue();
            }
            return minValue;
        }

        private void setValue(float value) {
            if (kind == Kind.MOVEMENT || kind == Kind.LAVA_WALK) MovementFeatureManager.setValue(id, value);
            else if (kind == Kind.BLOCK) BlockFeatureManager.setValue(id, value);
            else if (kind == Kind.WORLD) WorldFeatureManager.setValue(id, value);
        }

        private String runtime() {
            if (kind == Kind.SPEED) return SpeedHandler.getModeDisplayName() + " · " + SpeedHandler.getPresetDisplayName();
            if (kind == Kind.MOVEMENT) return MovementFeatureManager.getFeatureRuntimeSummary(id);
            if (kind == Kind.BLOCK) return BlockFeatureManager.getFeatureRuntimeSummary(id);
            if (kind == Kind.RENDER) return RenderFeatureManager.getFeatureRuntimeSummary(id);
            if (kind == Kind.WORLD) return WorldFeatureManager.getFeatureRuntimeSummary(id);
            if (kind == Kind.ITEM) return ItemFeatureManager.getFeatureRuntimeSummary(id);
            return MiscFeatureManager.getFeatureRuntimeSummary(id);
        }

        private void load() {
            if (kind == Kind.SPEED) SpeedHandler.loadConfig();
            else if (kind == Kind.MOVEMENT) MovementFeatureManager.loadConfig();
            else if (kind == Kind.BLOCK) BlockFeatureManager.loadConfig();
            else if (kind == Kind.RENDER) RenderFeatureManager.loadConfig();
            else if (kind == Kind.WORLD) WorldFeatureManager.loadConfig();
            else if (kind == Kind.ITEM) ItemFeatureManager.loadConfig();
            else MiscFeatureManager.loadConfig();
        }

        private void save() {
            if (kind == Kind.SPEED) SpeedHandler.saveConfig();
            else if (kind == Kind.MOVEMENT) MovementFeatureManager.saveConfig();
            else if (kind == Kind.BLOCK) BlockFeatureManager.saveConfig();
            else if (kind == Kind.RENDER) RenderFeatureManager.saveConfig();
            else if (kind == Kind.WORLD) WorldFeatureManager.saveConfig();
            else if (kind == Kind.ITEM) ItemFeatureManager.saveConfig();
            else MiscFeatureManager.saveConfig();
        }

        private void reset() {
            if (kind == Kind.SPEED) { SpeedHandler.applyPreset(SpeedHandler.PRESET_BALANCED); SpeedHandler.showStatusHud = true; return; }
            if (kind == Kind.MOVEMENT) MovementFeatureManager.resetFeature(id);
            else if (kind == Kind.BLOCK) BlockFeatureManager.resetFeature(id);
            else if (kind == Kind.RENDER) RenderFeatureManager.resetFeature(id);
            else if (kind == Kind.WORLD) WorldFeatureManager.resetFeature(id);
            else if (kind == Kind.ITEM) ItemFeatureManager.resetFeature(id);
            else MiscFeatureManager.resetFeature(id);
        }

        private boolean speedUsesJumpHeight() { return kind == Kind.SPEED && SpeedHandler.usesJumpHeight(); }
        private boolean hasItemTiming() { return "chest_steal".equals(id) || "auto_equip".equals(id) || "drop_all".equals(id); }
        private int itemTimingMinimum() { return "auto_equip".equals(id) ? 1 : 0; }
        private int itemTimingMaximum() { return "chest_steal".equals(id) ? 20 : 40; }

        private ModernFormSettingsTab.ChoiceValue<String> speedModeValue() {
            return new ModernFormSettingsTab.ChoiceValue<String>() {
                @Override public String get() { return SpeedHandler.speedMode; }
                @Override public void set(String value) { SpeedHandler.speedMode = value; SpeedHandler.markCustomPreset(); }
            };
        }
        private ModernFormSettingsTab.ChoiceValue<String> speedPresetValue() {
            return new ModernFormSettingsTab.ChoiceValue<String>() {
                @Override public String get() { return SpeedHandler.presetId; }
                @Override public void set(String value) { SpeedHandler.applyPreset(value); }
            };
        }
        private ModernFormSettingsTab.BooleanValue speedTimerValue() {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { return SpeedHandler.useTimerBoost; }
                @Override public void set(boolean value) { SpeedHandler.useTimerBoost = value; SpeedHandler.markCustomPreset(); }
            };
        }
        private ModernFormSettingsTab.BooleanValue speedHudValue() {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { return SpeedHandler.showStatusHud; }
                @Override public void set(boolean value) { SpeedHandler.showStatusHud = value; }
            };
        }
        private ModernFormSettingsTab.FloatValue speedTimerSpeedValue() { return speedFloat(0); }
        private ModernFormSettingsTab.FloatValue speedVanillaValue() { return speedFloat(1); }
        private ModernFormSettingsTab.FloatValue speedJumpValue() { return speedFloat(2); }
        private ModernFormSettingsTab.FloatValue speedFloat(final int which) {
            return new ModernFormSettingsTab.FloatValue() {
                @Override public float get() { return which == 0 ? SpeedHandler.timerSpeed : which == 1 ? SpeedHandler.vanillaSpeed : SpeedHandler.jumpHeight; }
                @Override public void set(float value) { if (which == 0) SpeedHandler.timerSpeed = value; else if (which == 1) SpeedHandler.vanillaSpeed = value; else SpeedHandler.jumpHeight = value; SpeedHandler.markCustomPreset(); }
            };
        }
        private ModernFormSettingsTab.BooleanValue lavaWaterValue() { return lavaBool(0); }
        private ModernFormSettingsTab.BooleanValue lavaDangerousValue() { return lavaBool(1); }
        private ModernFormSettingsTab.BooleanValue lavaSneakValue() { return lavaBool(2); }
        private ModernFormSettingsTab.BooleanValue lavaBool(final int which) {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { MovementFeatureManager.LiquidWalkSettings s = MovementFeatureManager.getLiquidWalkSettings(); return which == 0 ? s.isWalkOnWater() : which == 1 ? s.isDangerousOnly() : s.isSneakToDescend(); }
                @Override public void set(boolean value) { MovementFeatureManager.LiquidWalkSettings s = MovementFeatureManager.getLiquidWalkSettings(); boolean water = which == 0 ? value : s.isWalkOnWater(); boolean danger = which == 1 ? value : s.isDangerousOnly(); boolean sneak = which == 2 ? value : s.isSneakToDescend(); MovementFeatureManager.setLiquidWalkSettings(water, danger, sneak); }
            };
        }
        private ModernFormSettingsTab.IntValue itemTimingValue() {
            return new ModernFormSettingsTab.IntValue() {
                @Override public int get() { if ("chest_steal".equals(id)) return ItemFeatureManager.getChestStealDelayTicks(); if ("auto_equip".equals(id)) return ItemFeatureManager.getAutoEquipIntervalTicks(); return ItemFeatureManager.getDropAllDelayTicks(); }
                @Override public void set(int value) { if ("chest_steal".equals(id)) ItemFeatureManager.setChestStealDelayTicks(value); else if ("auto_equip".equals(id)) ItemFeatureManager.setAutoEquipIntervalTicks(value); else ItemFeatureManager.setDropAllDelayTicks(value); }
            };
        }

        private ModernFormSettingsTab.TextValue dropExpressionsValue() {
            return new ModernFormSettingsTab.TextValue() {
                @Override public String get() {
                    StringBuilder result = new StringBuilder();
                    for (String expression : ItemFeatureManager.getDropAllItemFilterExpressions()) {
                        if (result.length() > 0) result.append("\\n");
                        result.append(expression);
                    }
                    return result.toString();
                }
                @Override public void set(String value) {
                    java.util.List<String> expressions = new java.util.ArrayList<>();
                    for (String expression : (value == null ? "" : value).replace("\\n", "\n").split("\n")) {
                        if (!expression.trim().isEmpty()) expressions.add(expression.trim());
                    }
                    ItemFeatureManager.setDropAllItemFilterExpressions(expressions);
                }
            };
        }

        private ModernFormSettingsTab.IntValue autoReconnectDelayValue() {
            return intValue(MiscFeatureManager::getAutoReconnectDelayTicks,
                    MiscFeatureManager::setAutoReconnectDelayTicks);
        }

        private ModernFormSettingsTab.IntValue autoReconnectAttemptsValue() {
            return intValue(MiscFeatureManager::getAutoReconnectMaxAttempts,
                    MiscFeatureManager::setAutoReconnectMaxAttempts);
        }

        private ModernFormSettingsTab.IntValue autoRespawnDelayValue() {
            return intValue(MiscFeatureManager::getAutoRespawnDelayTicks,
                    MiscFeatureManager::setAutoRespawnDelayTicks);
        }

        private ModernFormSettingsTab.BooleanValue autoReconnectInfiniteValue() {
            return new ModernFormSettingsTab.BooleanValue() {
                @Override public boolean get() { return MiscFeatureManager.isAutoReconnectInfiniteAttempts(); }
                @Override public void set(boolean value) { MiscFeatureManager.setAutoReconnectInfiniteAttempts(value); }
            };
        }

        private ModernFormSettingsTab.IntValue intValue(final java.util.function.IntSupplier getter,
                final java.util.function.IntConsumer setter) {
            return new ModernFormSettingsTab.IntValue() {
                @Override public int get() { return getter.getAsInt(); }
                @Override public void set(int value) { setter.accept(value); }
            };
        }

    }

    private static FeatureAccess resolve(String featureId, String fallbackName, String fallbackDescription) {
        String id = safe(featureId).toLowerCase(Locale.ROOT);
        if (id.isEmpty()) return null;
        if ("speed".equals(id)) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, "加速")), featureText(id, "description", nonEmpty(fallbackDescription, "移动加速与 Timer 策略。")), Kind.SPEED, "", 0, 0);
        if ("lava_walk".equals(id)) {
            MovementFeatureManager.FeatureState state = MovementFeatureManager.getFeature(id);
            return movementAccess(state, fallbackName, fallbackDescription, Kind.LAVA_WALK);
        }
        MovementFeatureManager.FeatureState movement = MovementFeatureManager.getFeature(id);
        if (movement != null) return movementAccess(movement, fallbackName, fallbackDescription, Kind.MOVEMENT);
        BlockFeatureManager.FeatureState block = BlockFeatureManager.getFeature(id);
        if (block != null) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, block.name)), featureText(id, "description", nonEmpty(fallbackDescription, block.description)), Kind.BLOCK, block.valueLabel, block.minValue, block.maxValue);
        RenderFeatureManager.FeatureState render = RenderFeatureManager.getFeature(id);
        if (render != null) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, render.name)), featureText(id, "description", nonEmpty(fallbackDescription, render.description)), Kind.RENDER, "", 0, 0);
        WorldFeatureManager.FeatureState world = WorldFeatureManager.getFeature(id);
        if (world != null) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, world.name)), featureText(id, "description", nonEmpty(fallbackDescription, world.description)), Kind.WORLD, world.valueLabel, world.minValue, world.maxValue);
        ItemFeatureManager.FeatureState item = ItemFeatureManager.getFeature(id);
        if (item != null) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, item.name)), featureText(id, "description", nonEmpty(fallbackDescription, item.description)), Kind.ITEM, item.valueLabel, item.minValue, item.maxValue);
        MiscFeatureManager.FeatureState misc = MiscFeatureManager.getFeature(id);
        if (misc != null) return new FeatureAccess(id, featureText(id, "name", nonEmpty(fallbackName, misc.name)), featureText(id, "description", nonEmpty(fallbackDescription, misc.description)), Kind.MISC, misc.valueLabel, misc.minValue, misc.maxValue);
        return null;
    }

    private static FeatureAccess movementAccess(MovementFeatureManager.FeatureState state, String fallbackName,
            String fallbackDescription, Kind kind) {
        if (state == null) return null;
        return new FeatureAccess(state.id, featureText(state.id, "name", nonEmpty(fallbackName, state.name)),
                featureText(state.id, "description", nonEmpty(fallbackDescription, state.description)), kind,
                state.valueLabel, state.minValue, state.maxValue);
    }

    private static String nonEmpty(String value, String fallback) { return safe(value).isEmpty() ? safe(fallback) : safe(value); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String format(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static String featureText(String featureId, String suffix, String fallback) {
        return text("gui.other_feature.feature." + safe(featureId).toLowerCase(Locale.ROOT) + "." + suffix, fallback);
    }

    private static String text(String key, String fallback) {
        String value = I18n.format(key);
        return value == null || value.equals(key) ? fallback : value;
    }
}
