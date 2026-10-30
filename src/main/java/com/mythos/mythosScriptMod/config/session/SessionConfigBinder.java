package com.mythos.mythosScriptMod.config.session;

import com.mythos.mythosScriptMod.mythosScriptMod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 把各 handler/feature 的静态配置字段注册进 Nexus 的
 * {@code MythosStateSwap.PER_SESSION_FIELDS}，让 mount/unmount 时这些字段走
 * SESSION_STASH（按 session 实例分桶）而不是 group stash（按 session.group 共享）。
 *
 * <p>必须赶在 Nexus 的 {@code MythosStateSwap} 第一次 {@code ensureBuilt()} 之前注册，
 * 否则槽位已经建好、注册无效——所以这段逻辑在 mod 初始化阶段就要跑。</p>
 *
 * <p>KillAuraHandler 的配置读写已改为按 owner 落盘，这里同样登记，让它的静态字段走
 * per-session stash 而不是 group stash。</p>
 */
public final class SessionConfigBinder {

    /**
     * 与 Nexus {@code MythosStateSwap.SINGLETON_CLASSES} 一致，含 KillAuraHandler。
     * 这些类的静态字段会被 Nexus 纳入 stash，因此在这里登记才有意义。
     */
    private static final String[] CONFIG_CLASSES = {
            "com.mythos.mythosScriptMod.handlers.KillAuraHandler",
            "com.mythos.mythosScriptMod.handlers.FlyHandler",
            "com.mythos.mythosScriptMod.handlers.AutoEatHandler",
            "com.mythos.mythosScriptMod.handlers.AutoFishingHandler",
            "com.mythos.mythosScriptMod.handlers.AutoPickupHandler",
            "com.mythos.mythosScriptMod.handlers.ConditionalExecutionHandler",
            "com.mythos.mythosScriptMod.handlers.AutoEscapeHandler",
            "com.mythos.mythosScriptMod.handlers.AutoFollowHandler",
            "com.mythos.mythosScriptMod.handlers.EmbeddedNavigationHandler",
            "com.mythos.mythosScriptMod.otherfeatures.handler.movement.SpeedHandler",
            "com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager",
            "com.mythos.mythosScriptMod.otherfeatures.handler.misc.MiscFeatureManager",
            "com.mythos.mythosScriptMod.otherfeatures.handler.render.RenderFeatureManager",
            "com.mythos.mythosScriptMod.otherfeatures.handler.world.WorldFeatureManager",
            "com.mythos.mythosScriptMod.otherfeatures.handler.item.ItemFeatureManager",
            "com.mythos.mythosScriptMod.otherfeatures.handler.block.BlockFeatureManager",
    };

    private static boolean applied;

    private SessionConfigBinder() {
    }

    /**
     * 反射往 {@code MythosStateSwap.PER_SESSION_FIELDS} 里塞 "FQCN.field" 形式的名字。
     * Nexus 未安装时静默跳过：单客户端场景本来就不需要 per-session 分桶。
     */
    public static synchronized void apply() {
        if (applied) {
            return;
        }
        applied = true;
        try {
            Class<?> swap = Class.forName("com.mythos.nexus.session.MythosStateSwap");
            Field registryField = swap.getDeclaredField("PER_SESSION_FIELDS");
            registryField.setAccessible(true);
            Object raw = registryField.get(null);
            if (!(raw instanceof Set)) {
                mythosScriptMod.LOGGER.warn("MythosStateSwap.PER_SESSION_FIELDS is not a Set; per-session config binding skipped");
                return;
            }
            @SuppressWarnings("unchecked")
            Set<String> registry = (Set<String>) raw;
            int added = 0;
            for (String className : CONFIG_CLASSES) {
                added += registerClass(registry, className);
            }
            mythosScriptMod.LOGGER.info("Per-session config fields registered: {} (total {})", added, registry.size());
        } catch (ClassNotFoundException e) {
            mythosScriptMod.LOGGER.info("Nexus not present; per-session config binding skipped");
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.warn("Failed to register per-session config fields", t);
        }
    }

    private static int registerClass(Set<String> registry, String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (Throwable t) {
            mythosScriptMod.LOGGER.warn("Per-session config class not found: {}", className);
            return 0;
        }
        int added = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (!isConfigType(field.getType())) {
                continue;
            }
            if (registry.add(type.getName() + "." + field.getName())) {
                added++;
            }
        }
        return added;
    }

    /** 配置值的类型范围，与 MCP 的 {@code McpFields} 保持一致（其余是反射句柄/运行时缓存）。 */
    private static boolean isConfigType(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type == String.class
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type);
    }
}
