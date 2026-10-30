package com.mythos.mythosScriptMod.api;

import java.util.Collections;
import java.util.List;

/**
 * 插件注册的客户端命令体。
 *
 * <p>命令名和权限由 {@link IPluginContext#registerCommand} 负责，这里只处理参数。
 * 抛出的异常会被加载器捕获并转成聊天栏里的一条错误，绝不会变成客户端崩溃。</p>
 */
public interface IPluginCommand {

    /**
     * @param args 不含命令名本身的参数数组，永远不会是 {@code null}
     */
    void execute(String[] args) throws Exception;

    /** 命令用法，展示在 {@code /help} 与命令提示里。 */
    default String usage() {
        return "";
    }

    /** Tab 补全候选，默认无。 */
    default List<String> complete(String[] args) {
        return Collections.emptyList();
    }
}
