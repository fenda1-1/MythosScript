package com.mythos.mythosScriptMod.api;

import com.mythos.mythosScriptMod.gui.modern.ModernSettingsTab;

/**
 * 插件注册设置标签页时提供的工厂。
 *
 * <p>工厂而不是实例：主界面每次打开都会重建标签页（{@code ModernTabCatalog}
 * 是每次 new 的组合根），缓存一个实例会让上一次的滚动位置、输入框焦点
 * 泄漏到下一次打开。</p>
 */
public interface IPluginTabFactory {

    ModernSettingsTab create();
}
