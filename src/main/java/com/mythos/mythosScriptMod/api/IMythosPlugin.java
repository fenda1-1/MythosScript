package com.mythos.mythosScriptMod.api;

/**
 * MythosScript 第三方插件入口。
 *
 * <p>插件就是一个普通的 Forge mod jar，额外带一个 {@code META-INF/mythos-plugin.json}
 * 清单，清单里的 {@code entrypoint} 指向一个实现了本接口、并且有无参构造器的类。
 * 加载器只做一件事：实例化它，然后调 {@link #onLoad}。</p>
 *
 * <p>{@code onLoad} 抛出的任何异常都只会让这一个插件的状态变成“加载失败”，
 * 插件中心会把原因显示出来，不会影响其它插件，也不会影响客户端启动。</p>
 */
public interface IMythosPlugin {

    /**
     * 插件被加载时调用一次，所有注册动作（命令、tick 回调）都必须在这里完成。
     *
     * @param context 注册句柄，加载结束后即失效
     */
    void onLoad(IPluginContext context) throws Exception;

    /**
     * 插件被卸载时调用。当前版本只在客户端退出时触发，默认什么都不做。
     */
    default void onUnload() {
    }
}
