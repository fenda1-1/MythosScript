package com.mythos.mythosScriptMod.api;

/**
 * 插件在 {@link IMythosPlugin#onLoad} 里拿到的注册句柄。
 *
 * <p>只有加载期间 registrations 有效；加载结束后再调用会抛
 * {@link IllegalStateException}，这样“注册动作写在了 tick 回调里”这类错误会在开发阶段
 * 立刻暴露，而不是悄悄注册了一堆永远不会生效的回调。</p>
 *
 * <p>退出时（{@link IMythosPlugin#onUnload} 到 {@code tick} 之间）这个句柄也保持可用，
 * 但只允许 {@link #config()} 和 {@link #info} / {@link #warn}：
 * 卸载时把最后的状态写进配置是合法需求，注册新东西不是。</p>
 */
public interface IPluginContext {

    /** 清单里声明的插件 id。 */
    String pluginId();

    /** 清单里声明的插件版本。 */
    String pluginVersion();

    /** 当前 API 版本，插件可据此做能力判断。 */
    default int apiVersion() {
        return MythosApi.VERSION;
    }

    void info(String message);

    void warn(String message, Throwable error);

    /**
     * 注册一条客户端命令，名字不带斜杠。
     *
     * <p>名字已经存在（原版命令、其它模组、别的插件）时抛 {@link IllegalArgumentException}，
     * 本次加载判定失败——静默覆盖别人的命令比加载失败更难排查。</p>
     */
    void registerCommand(String name, IPluginCommand command);

    /**
     * 注册一个每个客户端 tick 执行一次的回调。
     *
     * <p>回调抛异常时会被禁用并记录一次原因，不会每 tick 重复刷屏。</p>
     */
    void registerTick(Runnable callback);

    /**
     * 注册一个 MCP 工具，名字全局唯一。
     *
     * <p>与内置工具重名判为加载失败：外部调用方依赖内置工具的语义稳定，
     * 不允许被插件偷换。</p>
     */
    void registerMcpTool(IPluginMcpTool tool);

    /**
     * 注册一个设置标签页。
     *
     * @param command 路由名，出现在命令面板与 {@code MythosGui open <command>}
     * @param title   标题的 i18n key 或原文；找不到 key 时按原文显示
     */
    void registerTab(String command, String title, IPluginTabFactory factory);

    /** 订阅客户端聊天消息。 */
    void registerChatListener(IPluginChatListener listener);

    /** 本插件的持久化配置。任何时候都可读，写盘只在 {@link IPluginConfig#save()}。 */
    IPluginConfig config();
}
