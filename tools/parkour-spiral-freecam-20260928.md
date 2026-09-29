# 旋转塔、灵魂出窍与寻路可视化选点（2026-09-28）

## 修复与入口

- 旋转塔：`(11.5,112,-170.5)` → `(11.5,120,-167.5)`。旧候选只有平行绕行，漏掉井内直角转弯。`ParkourSurface.turnRoute`补两种轴顺序的内部转角，完整人物盒净空、真实逐tick碰撞与落稳验证仍保留。
- 灵魂出窍：保留本体速度、物理更新及Baritone的`PlayerMovementInput`，只屏蔽键盘对本体的手动输入；镜头独立。普通Mixin与注入钩子均保留本体可见性/更新，镜头冲刺键不再影响本体。
- 通用 → 移动与场景 → **寻路可视化选点**：按钮左、右键都切换。开启联动灵魂出窍，准星高亮沿用现有渲染相机射线；世界中左/右键选择目标后本体寻路，镜头继续独立。再次点击按钮或绑定快捷键关闭选点并返回本体，保留已有寻路任务。
- 命令面板与快捷键统一调用`AutoFollowAreaPicker.toggleNavigation()`；快捷键动作`TOGGLE_VISUAL_PATH_PICKER`，不占用默认按键。既有编辑器取点流程保留。

## 验证

证据目录：`build/parkour-evidence/raised-platform-20260928/`。

1. `spiral-tower-before.log`：只能返回中途片段，完整终点断言失败。
2. `spiral-tower-after.log`：生产A*、movement组装、真实轨迹及20tick落稳通过；缓存关闭，9段共97帧，无拒绝重搜。图777.3221ms，总离线6960.2882ms；这些不是实机通关时间。录制文件`src/test/resources/parkour/spiral-tower/spiral-tower.json` SHA256 `f24247d3c9e3d0564361fe39991fa8a86d5bb56fab3e4efad381d3b8c0cb2bc0`。
3. `visual-picker-build.log`：`gradlew test --tests '*FreecamIsolationTest' --tests '*AutoFollowPickProjectionTest' reobfJar --offline --console=plain --no-daemon --max-workers=2`成功；3项检查通过，覆盖键盘/自动输入隔离、MCP/SRG注入方法返回值、实际相机平移/旋转射线。
4. `spiral-tower-regression-after-build.log`：27项通过，包括前三场景、102层、已加载完整目标/未加载续段、能力前沿、地下梯子、雪地、恢复与快照。先前与Gradle同时执行造成`NoClassDefFoundError: com/mythos/mythosScriptMod/shadowbaritone/api/event/listener/AbstractGameEventListener`，原`spiral-tower-regression.log`保留；构建结束后顺序复验通过。
5. 2026-09-28用户实机确认旋转塔已修复；随后确认选点开关、命令面板、快捷键入口均正常（会话消息`m09314`、`m09334`）。这属于用户实测确认，没有捏造逐tick数据或通关秒数。自动塔探针因已有导航/序列而未满足闲置前置条件，在传送前退出，不计为关卡失败或通关。
6. `visual-picker-ui/common-inspect.json`确认实际菜单含`movement_scene`与`baritone_visual_pick`；之后采用用户的实际开关/面板/快捷键验证结果，不重复干扰测试。

## 部署来源

通过`tools/parkour_restart.py --world 'Getting Over it The Corrupted'`保存退出，仅重启指定PID32204，新PID18864，48.2秒进入原地图。

实际JAR：`E:/我的世界脚本/MythosTests/runs/1.12.2-inject-20260928-073852-f37669/MythosScript-v1.0.72-mc1.12.2.jar`，SHA256 `17d0dfe1011929b5e9b99d114dfc813f085a2da16ecd2013f551f6668700f863`。

`visual-picker-ui/source.json`与`source.diff`保留基线`bbd27d2`、实际JAR哈希、变更源码哈希、完整工作区diff及此前被忽略的注入源文件内容。仅把本次修改的两份注入源文件纳入版本管理；其他已有注入文件保留。部署包还含未验收的岩浆受伤恢复工作，不能由本次通过推断其已修复。五处当前地图场景是局部正确性验收，全面A1–F29目标、生存三关本批复验及旧两关问题仍未完成。
