package com.mythos.mythosScriptMod.api;

/**
 * 插件自己的持久化配置，一个插件一个 JSON 文件。
 *
 * <p>文件位置：{@code config/我的世界脚本/plugins/<插件id>.json}。写入只发生在
 * {@link #save()}，插件在 tick 里频繁 {@code set} 不会造成磁盘抖动。</p>
 *
 * <p>读取失败（文件损坏、字段类型不对）一律回退到调用方给的默认值，
 * 并记录一条警告——宁可让插件以默认配置跑起来，也不要因为一个坏字段
 * 让整个插件加载失败。</p>
 */
public interface IPluginConfig {

    String getString(String key, String fallback);

    int getInt(String key, int fallback);

    boolean getBoolean(String key, boolean fallback);

    /** 支持 String / Number / Boolean 及其数组。 */
    void set(String key, Object value);

    /** 立即写盘。返回 false 表示写失败，原因已经记进日志。 */
    boolean save();
}
