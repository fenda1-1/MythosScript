# 变更记录

## 2026-10-05 构建修复：全新克隆可直接构建

修复「别人克隆仓库后执行 `gradlew.bat build` 无法构建」的问题。

### 构建脚本

- `gradlew.bat` 不再写死本机 JDK 路径。原来的 `set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202` 会无条件覆盖环境变量，其他机器上连构建 JVM 都起不来（`ERROR: JAVA_HOME is set to an invalid directory`）。现在按 `JAVA_HOME` → `JAVA8_HOME` → `JDK8_HOME` 解析，都没有时退回 `PATH` 上的 `java`，与 README 的描述一致。
- 只有 `JAVA_HOME` 存在时才传 `-Dorg.gradle.java.home`，避免空值破坏依赖 `PATH` 的环境。
- `gradle.properties` 移除写死的 `org.gradle.java.home`（该文件随仓库共享，与本文件第 2 行自身的说明一致）。

### 纳入版本控制的构建必需文件

这些文件原先被 `.gitignore` 忽略，但缺失会让克隆后的构建失败或缺类：

- `agent.gradle`：`build.gradle` 末尾无条件 `apply from: 'agent.gradle'`，缺失时配置阶段直接失败。
- `src/main/java/AttachBridge.java`、`src/main/java/dev/mythos/inject/MythosAgent.java`、`src/main/java/dev/mythos/inject/runtime/FeatureTransformer1201.java`：manifest 声明的 `Main-Class` 与 `Agent-Class`/`Premain-Class`。缺失时 ProGuard 因 `-dontwarn`/`-ignorewarnings` 静默通过，却产出没有入口类的坏 jar。
- `src/test/java/com/mythos/mythosScriptMod/shadowbaritone/pathing/movement/parkour/CapturedParkourFlightTest.java`：已跟踪的 `CapturedParkourSuiteTest.java:157` 静态调用了它，缺失时 `:compileTestJava` 报「找不到符号」。

### 文档

- README / README_EN：补充 JDK 8 的解析顺序、首次构建需要联网（ForgeGradle 需下载 Minecraft 1.12.2 依赖与映射）、Gradle 用户目录固定在仓库内 `.gradle/`（缓存不跨项目共享，空缓存时 `--offline` 必然失败）；示例产物文件名版本号由 v1.0.71 更正为 v1.0.73。

## 2026-08-30 工作区更新

本次更新围绕现代化主界面、快捷键操作和路径序列编辑流程进行了集中调整。

### 主界面与窗口

- 复用现代主界面实例，统一主菜单打开、关闭、返回和设置页跳转流程。
- 支持将现代主界面切换到独立 Swing 窗口。
- 独立窗口使用 Minecraft 帧缓冲区渲染，并转发鼠标、滚轮和键盘输入。
- 增加独立窗口返回游戏内、关闭窗口、文本焦点识别和外部按键状态同步。
- 增加内嵌文本输入框、隐藏分类恢复面板和即时悬浮提示。
- 支持关闭标签页后恢复、最近命令筛选、全局搜索聚焦、标签页切换和窗口布局重置。

### 分类与路径序列

- 主界面导航与路径工作台共享当前分类和子分类范围。
- 路径序列支持收藏，并记录最近打开次数和最近打开顺序。
- 支持在分类上下文菜单中重命名、隐藏和恢复分类。
- 路径工作台使用隔离草稿，只有明确保存时才提交序列、重命名、分类和持久化动作清理；取消时放弃草稿改动。
- 撤销/重做快照保留持久化动作标识，避免编辑过程意外生成或删除运行时身份。

### 快捷键与输入

- 一个快捷键动作可以绑定多个按键组合，录制器支持连续添加、删除最后一项、清空、确认和取消。
- 快捷键匹配、冲突检测和旧配置读取统一使用组合列表。
- 文本输入和快捷键录制期间会阻止全局快捷键误触发；独立窗口的按键状态可参与现有输入检查。
- 新增默认快捷键动作：

  | 动作 | 默认组合 |
  | --- | --- |
  | 关闭当前标签页 | `Ctrl+W` |
  | 恢复关闭的标签页 | `Ctrl+Shift+T` |
  | 打开最近命令 | `Ctrl+K` |
  | 聚焦全局搜索 | `Ctrl+Shift+F` |
  | 运行最近路径 | `Ctrl+Shift+E` |
  | 保存当前配置 | `Ctrl+S` |
  | 紧急停止 | `Ctrl+Shift+Backspace` |
  | 切换前后标签页 | `Ctrl+Tab` / `Ctrl+Shift+Tab` |
  | 暂停或恢复自动化 | `F6` |
  | 重置窗口布局 | `Ctrl+Shift+R` |
  | 打开独立窗口 | `F7` |
  | 选择标签页 | `Alt+1` 至 `Alt+9` |
  | 运行常用路径 | `Ctrl+1` 至 `Ctrl+9` |

- `Ctrl+1` 至 `Ctrl+9` 优先运行收藏路径；没有收藏时按打开次数排序运行常用路径。
- 新增快捷键名称和说明的中英文资源。

### 物品过滤动作

- 批量移动物品动作新增 `button` 和 `clickType` 参数。
- 支持按 `PICKUP`、`QUICK_MOVE`、`SWAP`、`THROW`、`PICKUP_ALL`、`QUICK_CRAFT` 和 `CLONE` 等点击类型执行。
- 非光标搬运类型会直接对匹配的来源槽位执行点击，不再要求目标槽位选择。
- 未配置新参数时继续使用 `button=0` 和 `clickType=PICKUP`，保持旧路径配置的行为。

### 兼容性

- 旧版快捷键配置仍可读取，并兼容旧动作名称和单组合格式。
- 现代主界面的全局保存动作可以转发到当前内嵌旧版界面的保存按钮。
- 路径工作台和旧版界面之间保留返回、保存和取消流程。
