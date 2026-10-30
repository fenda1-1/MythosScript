package com.mythos.mythosScriptMod.api;

/**
 * 插件订阅客户端收到的聊天消息。
 *
 * <p>回调在客户端主线程执行，且只在插件处于启用状态时触发。抛出的异常
 * 会禁用该监听器（不是整个插件），并把原因记进插件中心——一条坏消息
 * 不应该让插件每个 tick 都崩一次。</p>
 */
public interface IPluginChatListener {

    /** @param text 已去掉格式符号的纯文本 */
    void onChatMessage(String text);
}
