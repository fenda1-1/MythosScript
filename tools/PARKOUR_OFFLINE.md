# 批量现场采集 → 一键离线测试

## 命令

首次更新代码后准备 Java 8 测试环境：

```powershell
python tools/parkour_offline.py --prepare
```

游戏安装本次更新后，一条命令采集并测试（输出目录必须是新目录）：

```powershell
python tools/parkour_snapshot.py --player test_1122_inj --url http://127.0.0.1:8821/mcp --token-file "../MythosTests/instances/1.12.2/inject/game/config/我的世界脚本/mcp.token" --plan tools/parkour-level70-plan.json --output "build/parkour-captures/new-run" --test
```

以后修改移动、物理或 A* 搜索代码，只重跑离线数据，无需重启游戏。入口自动编译 `pathing/movement`、`pathing/calc` 和快照适配器；修改这些范围以外的依赖时执行一次 `--prepare`：

```powershell
python tools/parkour_offline.py --suite "build/parkour-captures/new-run/suite.json"
```

`--output` 可以是任意可写的绝对/相对路径，由采集客户端保存。MCP 服务返回数据，不在游戏进程中任意写文件。`--bridge` 可指定项目版 MCP 客户端；默认自动发现端点，不把 token 写入场景。

示例使用当前测试实例的端口和凭证文件路径，直接连接以避免逐个探测历史端点。其他实例替换 `--url`、`--token-file`，或同时省略它们使用自动发现。

## 配置

参见 `parkour-level70-plan.json`。`regions`、`tests` 都可配置多个条目，不需要新增 Java 测试。

- 顶层 `origin`：默认原点；省略时使用采集开始时玩家所在方块。
- 区域 `origin`：绝对中心；`offset`：相对默认原点偏移，二选一。坐标向下取整。
- 区域 `size`：三个正整数。也可用绝对 `min/max`（均包含边界）替代 origin/offset/size。
- 超过 32 的轴自动切片，切片完整覆盖，不把超限参数截小。
- 测试 `regions`：要合并的区域 ID；省略则使用全部区域。不相连区域之间仍是未知区域。
- 测试 `start`：可选绝对脚坐标，指定时明确重置为普通步行、静止站立起点（先检查支撑）；省略则重放第一份选中快照的玩家位置、速度、落地状态，飞行状态会报错。两种模式均读取速度属性、跳跃药水。`results.json` 的 `startMode` 标明实际模式。
- `goals`：按顺序到达的绝对脚坐标。普通终点必须停稳且保持 20 tick；连续跳跃、贴墙攀爬、水池和机关接力采用各自的接触/速度条件，不强制在接力中间停住。
- `budgetMillis`：每段搜索预算，默认 3000；`maxTicks` 默认 120。
- `mode`：默认 `trajectory`，按给定 `goals` 验证局部物理。`graph` 只接受一个最终目标，由生产 A* 自动选择全部中间落点；同时检查移动组装、执行前代价重算、选中站位、逐段物理可达与停稳交接。`graphBudgetMillis` 为 A* 预算，默认 5000。

输出包括 `suite.json`、每个场景一个 `<场景>.json`（超过单片时切成 `<场景>.zip`，包内 `NN.json` 按顺序合并）、测试后的 `results.json`。结果保存每段耗时、失败原因、逐 tick 按键，方便同一条路线实机对比。进程退出码非零表示失败。

## 完整自动选路回归

`parkour-next-node-plan.json` 包含栏杆外沿及实机失败平台两种起点，均只指定下一压力板为最终目标，采集三个相连区域：

```powershell
python tools/parkour_snapshot.py --player test_1122_inj --url http://127.0.0.1:8821/mcp --token-file "../MythosTests/instances/1.12.2/inject/game/config/我的世界脚本/mcp.token" --plan tools/parkour-next-node-plan.json --output "build/parkour-captures/next-node-new" --test
```

已有采集可直接反复测试：

```powershell
python tools/parkour_offline.py --suite src/test/resources/parkour/next-node/suite.json
```

`results.json` 的 `graph` 记录真实移动类型及选中的站位，`segments` 记录连续物理验证。栏杆同一格的不同站立侧不能合并为同一个 A* 状态，也不能在路径组装或代价重算时换回方块中心。

此入口覆盖静态规划和物理可执行性；普通行走动作仍以轨迹求解器验证，并非逐行运行客户端输入处理器。测试采用禁止飞行、破坏和放置的普通跑酷规划配置；动态机关、服务器纠正及真实输入交接仍需最终实机核验。

`graph` 会核对生产执行器的连跳选择策略，普通落点使用相同的 `searchWithRunup` 入口，动量交接使用 `searchConnection`。灵魂沙加速入口也能作为 `MOMENTUM_SPRINT` 单条图边，执行时用 `searchMomentumRoute` 联合求解两个跳跃；中间只接受真实接触并保留速度，终点仍需停稳。

## 手跑动量回放

```powershell
python tools/parkour_offline.py Level70SoulMomentumTest
python tools/parkour_offline.py --suite src/test/resources/parkour/level70/suite.json
```

`level70/soul-runway-hand-replay.json` 保留 `parkour-2026-09-25_06-43-04-789-528489814039502003.jsonl` 的连续 36 拍原始输入/状态（117937–117972）。从普通地面静止助跑，全程逐拍比较位置、三轴速度和落地状态，不在中途重置速度。`tools/parkour_extract_replay.py` 可从其他日志导出指定连续 tick，拒绝缺帧、飞行和传送不连续。

关键证据：117964 拍在 `[127.485865,11.875,-3756.166233]` 落地，仍保留 `vx=0.257874,vz=-0.012738`；117965 拍立即再跳，117972 拍落到后方 `y=13` 平台。碰撞先处理 Y 再处理 X/Z，末端接触这一拍可以在水平移动后完全离开灵魂沙体积，因此不触发该块减速；不是人为增加一拍减速豁免。测试同时要求自动生成两跳轨迹，以及生产 A* 选择包含普通地面助跑入口的动量图边。

## MCP 原生批量调用

```json
{
  "groups": ["player", "world"],
  "includePhysics": true,
  "regions": [
    {"id": "left", "origin": [28, 29, -3756], "size": [12, 12, 12]},
    {"id": "right", "origin": [32, 29, -3750], "size": [12, 12, 12]}
  ]
}
```

`mythos_snapshot` 的 `regions` 在同一次客户端线程调用中读取；每次最多 64 区域、总共 32768 格。采集脚本逐片调用以支持任意多区域，并保留各片 tick/session，**不声称跨调用原子快照**。重叠区域状态冲突会拒绝合并。世界切换、未加载区块、采集失败不会发布完成的 suite。

## 数据精度与适用范围

保存完整方块状态、方向、游戏引擎实际碰撞盒（楼梯等多部分形状也保留）、玩家速度、碰撞/落地/梯子状态、速度属性、药水、采集 tick/session。方块压缩坐标相对各片原点；碰撞盒是绝对坐标。

当前模型包含站立/潜行实际身高及帧末姿态更新、藤蔓碰撞与连续攀爬、源水浮力/阻力/出水及连续水池接力、冰面助跑、粘液反弹。跳跃提升保留有效期，地图命令区的刷新来源显式记录在 `effectRefresh`；它不是无限药水。未覆盖完整流动水、深海探索者或所有速度药水的到期变化。

静态世界仍拒绝正在运动的活塞、未知区块、蛛网和危险块。活塞回归另外使用 `dynamicReplay`：移动接触和服务器纠正来自真实逐 tick 记录，控制器决策和至少 15 帧离开机关后的自由飞行独立计算校验；**这不是完整红石/动态活塞世界模拟**。离线普通动作也不是完整客户端 `PathExecutor` 回放，因此必须配合实机首轮测试，不能用离线通过代替无失败通关证明。

## 新增关卡与首次尝试回归（2026-09-26）

```powershell
python tools/parkour_offline.py AdditionalParkourRegressionTest SwampNetherRegressionTest
python tools/parkour_offline.py --suite src/test/resources/parkour/additional-courses/suite.json
python tools/parkour_offline.py --suite src/test/resources/parkour/swamp-nether69/suite.json
```

- `additional-courses`：5 个按区域打包的真实场景、6 个自动选路用例，覆盖 swamp52、swamp58 三段活塞及完整提升塔、snow03、snow08；6/6 通过。
- `swamp-nether69`：8 个按区域打包的真实场景、17 个用例，覆盖 swamp54/55/57/60、nether69，并单独保留用户的 `[60.5,18,-3290]` 起点；17/17 通过。
- 地下、snow10、nether70 旧采集共 17 个用例再次通过，合计 40 个场景；新增针对性断言共 9 项通过。
- 首次实机验证：swamp52 约 7 秒；swamp58 完整路线约 15 秒；snow03 约 7 秒；snow08 约 11 秒。60 指定起点两段活塞首轮通过，并重复通过；前段藤蔓约 16 秒通过。测试监控死亡、掉落和异常重置，不把死亡后的重试算首轮成功。
- 保存的 `跑酷_swamp-58` 已补全提升塔与终点确认，整条序列首轮通过约 25.2 秒；`跑酷_swamp-60` 整条序列首轮通过约 85.6 秒。均确认地图终点传送及序列结束，途中没有死亡或异常重置。
- 关键实机记录：
  - `build/parkour-evidence/20260926-181539-swamp52-clean-first.json`
  - `build/parkour-evidence/20260926-183001-snow03-final-first.json`
  - `build/parkour-evidence/20260926-183015-snow08-final-first.json`
  - `build/parkour-evidence/20260926-184236-swamp60-exact-repeat-first.json`
  - `build/parkour-evidence/20260926-184224-swamp60-vines-final-first.json`
  - `build/parkour-evidence/20260926-184954-sequence.json`（58 全程）
  - `build/parkour-evidence/20260926-185121-sequence.json`（60 全程）
- 采集脚本的 3 项 Python 测试通过，`git diff --check` 通过；实机验证后已停止导航、序列及临时调试日志。
- 回归断言覆盖：首次压力板接触必须先落在板侧而非提前在粘液反弹；0.075 格交接偏差改变支撑时禁止复用缓存输入；连续藤蔓保持朝墙；水池接力保留入水动量；另有粘液位置纠正、悬浮水池稳定接住、树叶低顶碰撞等测试。

场景按区域保存为 `<场景>.zip`（只有一片时为 `<场景>.json`），采集原始字节不变。重新采集使用 `tools/parkour-additional-plan.json` 或 `tools/parkour-swamp-nether69-plan.json`；用 `tools/parkour_fixture.py` 发布到新目录。活塞另外保存为 `<场景>-piston.json`，由 `tools/parkour_piston_fixture.py` 从本次运行的连续日志导出并填写 `dynamicReplay`，不能自动沿用旧运行的机关时序。`requiredEffects` 可要求指定等级或 `null`（已消失），避免上一关药水污染下一关起点；60 起点自带地图持续刷新的缓慢效果，不应要求它消失。

## 本次验证记录（2026-09-25）

- `build/parkour-captures/level70-multipoint/suite.json`：左右两个现场区域导出、重叠合并及两条自动搜索路线通过；缓存编译后的整套离线入口约 6 秒。首次 Java 环境准备另计。
- 左转身跳实机：`build/parkour-evidence/20260925-025732.json`，稳定落点 `[32.528921,26,-3756.557672]`。
- 右侧三段梯子实机：`build/parkour-evidence/20260925-030715.json`，稳定落点 `[33.493932,32,-3749.936857]`。
- 两次均从 `[28.218892,26,-3754.015264]` 发出单次 goto，自动寻路并在终点保持 2 秒；记录的是这两个局部场景的通过结果。
- 修复了梯子方向/碰撞与脚部接触、蹲边位移与动量混淆、抓梯过早清空制动路线，以及抓梯到攀爬交接时漏一帧蹲下的问题；相应失败现场已加入离线回归。
- 下一节点 `[95,21,-3760]` 实机确认石质压力板 `powered=true`：`build/parkour-evidence/20260925-050529-continue.json`，本次连续运行从 `[62.564591,21,-3758.650028]` 开始。
- 固定回归 `src/test/resources/parkour/next-node/suite.json` 保留三个现场区域与两个自动选路用例。修复了栏杆同格不同站立侧被合并、普通平台误用强制动量连跳，以及到达瞬间合格但残余速度让落点漂出的判定；不再用手选中间落点绕开 A*。
