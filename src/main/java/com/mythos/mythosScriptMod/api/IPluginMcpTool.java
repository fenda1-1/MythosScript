package com.mythos.mythosScriptMod.api;

import com.google.gson.JsonObject;

/**
 * 插件注册的 MCP 工具。
 *
 * <p>注册后该工具会出现在 {@code tools/list} 里，和内置工具完全同权：外部 AI
 * 通过 {@code mythos_*} 之外的独立名字调用它。名字冲突一律判为加载失败，
 * 不允许插件覆盖内置工具——外部调用方依赖内置工具语义稳定。</p>
 *
 * <p>{@link #call} 抛出的异常会被转成一次工具错误返回给调用方，
 * 不会让 MCP 服务或客户端崩溃。</p>
 */
public interface IPluginMcpTool {

    /** 工具名，建议 {@code 插件id_动作} 形式，全局唯一。 */
    String name();

    /** 写给调用方看的说明：做什么、参数含义、副作用。 */
    String description();

    /**
     * 参数表，格式与内置工具一致：{@code "operation:string,limit:integer"}。
     * 类型支持 {@code string / integer / boolean / array / object / any}。
     */
    default String parameters() {
        return "";
    }

    /** 必填参数名，逗号分隔。 */
    default String required() {
        return "";
    }

    JsonObject call(JsonObject arguments) throws Exception;
}
