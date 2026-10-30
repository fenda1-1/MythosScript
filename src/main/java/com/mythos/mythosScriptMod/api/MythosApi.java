package com.mythos.mythosScriptMod.api;

/**
 * 插件 API 的版本标识。
 *
 * <p>插件清单里的 {@code apiVersion} 必须和 {@link #VERSION} 相等才会被加载。
 * 之所以用整数而不是版本区间，是因为这个 API 只控制“能不能装”这一件事：
 * 主版本一旦改动就意味着旧插件调用了已不存在的方法，与其在运行时抛
 * {@code NoSuchMethodError}，不如在加载前明确拒绝并告诉用户升级插件。</p>
 */
public final class MythosApi {

    /** 当前 API 版本。破坏性改动时 +1。 */
    public static final int VERSION = 1;

    /** 展示用版本名，写进插件中心。 */
    public static final String VERSION_NAME = "1.0";

    private MythosApi() {
    }
}
