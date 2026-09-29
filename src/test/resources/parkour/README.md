# 跑酷离线回归场景

本目录保存从真实世界采集的离线跑酷回归数据，供 `tools/parkour_offline.py` 和 `src/test/java/.../pathing/movement/parkour` 下的单元测试使用。

## 命名规则

- 一个场景一个文件，文件名等于 `suite.json` 里的 `region` ID。
- 场景需要多片切片时打包成 `<场景>.zip`，包内 `00.json`、`01.json` … 按顺序合并成同一个世界。
- 只有一片切片时用 `<场景>.json`。
- 动态活塞机关的逐 tick 记录保存为 `<场景>-piston.json`，即用例里的 `dynamicReplay`。
- `suite.json` 描述场景清单和用例；`results.json` 是运行输出，不提交。

## 目录总览

| 目录 | 课程/关卡 | 场景数 | 用例数 | 入口 |
| --- | --- | --- | --- | --- |
| `level70/` | 关卡70（梯子转角、出口栏杆、灵魂沙跑道） | 3 | 3 Java + 1 suite | `Level70ParkourTest`、`Level70SoulMomentumTest`、`NextRelayParkourTest`、`CapturedParkourGraphTest`、`level70/suite.json` |
| `additional-courses/` | swamp52、swamp58、swamp58-tower、snow03、snow08 | 5 | 6 + 4 Java | `AdditionalParkourRegressionTest`、`additional-courses/suite.json` |
| `next-node/` | 出口栏杆到下一接力点（west、middle、relay） | 3 | 2 | `next-node/suite.json` |
| `swamp-nether69/` | swamp54/55/57/60、nether69 | 8 | 17 + 5 Java | `SwampNetherRegressionTest`、`swamp-nether69/suite.json` |

运行方式：

```powershell
python tools/parkour_offline.py AdditionalParkourRegressionTest SwampNetherRegressionTest
python tools/parkour_offline.py --suite src/test/resources/parkour/additional-courses/suite.json
python tools/parkour_offline.py --suite src/test/resources/parkour/swamp-nether69/suite.json
python tools/parkour_offline.py --suite src/test/resources/parkour/next-node/suite.json
python tools/parkour_offline.py --suite src/test/resources/parkour/level70/suite.json
```

## level70/

| 场景文件 | 场景段 | 覆盖测试 |
| --- | --- | --- |
| `ladder-turn.json` | 关卡70 西侧梯子与左右转角区（原点 28,29,-3753） | `Level70ParkourTest`：西侧梯子碰撞盒与脚部判定、右侧梯子搜索、右梯三段转角、左转身跳、蹲边保动量且离支撑后不可起跳、左起跳稳定转弯、抓梯前完成制动 |
| `exit-fence.json` | 关卡70 出口栏杆外沿（原点 40,28,-3756） | `NextRelayParkourTest`：起飞点旁薄栏杆被拒、栏杆底部需要曲线脱离而非直落、楼梯与高起点到达栏杆边；`CapturedParkourGraphTest`：生产 A* 沿外沿离开且不穿栏杆 |
| `soul-runway.json` | 灵魂沙助跑跑道（原点 126,15,-3759） | `Level70SoulMomentumTest`：普通地面静止助跑立即接灵魂沙跳、普通跑道跨越灵魂沙边保留动量、A* 把跑道与灵魂沙接触选成一条动量边；suite 用例 `ordinary-runway-through-soul-to-exit` |

- `soul-runway-hand-replay.json`：36 拍手跑原始输入/状态（tick 117937–117972），逐拍比较位置、三轴速度和落地状态。

## additional-courses/

| 场景文件 | 场景段 | suite 用例 |
| --- | --- | --- |
| `swamp52.zip` | swamp52 起始到金色终点 | `swamp52-start-to-gold` `[0.5,11,-2913.5] → [7.5,15,-2913.5]` |
| `swamp58.zip` | swamp58 起始到上层 | `swamp58-start-to-upper` `[0.5,11,-3195.5] → [19.5,23,-3195.5]`（活塞 `swamp58-piston.json`）、`swamp58-upper-to-tower` `→ [26.5,25,-3195.5]` |
| `swamp58-tower.zip` | swamp58 完整提升塔到金色终点 | `swamp58-tower-to-gold` `[26.5,25,-3195.5] → [26.5,45,-3195.5]` |
| `snow03.zip` | snow03 起始到金色终点 | `snow03-start-to-gold` `[0.5,11,-1738.5] → [12.5,15,-1738.5]` |
| `snow08.zip` | snow08 起始到金色终点 | `snow08-start-to-gold` `[0.5,11,-1973.5] → [26.5,12,-1973.5]` |

`AdditionalParkourRegressionTest` 针对该目录的定点断言：

- `pistonEntryMustLandOnPlateBeforeSlimeBounce`（swamp58）：首次必须落在压力板上，粘液反弹不算活塞就绪。
- `cachedSnowHandoffRejectsSmallSupportChangingError`（snow03）：0.075 格误差改变支撑时禁止复用缓存输入。
- `risingPoolsPreserveMomentumThroughFirstCatch`（swamp52）：上升水池接力保留入水动量。
- `adjacentVineCellsKeepFacingTheirBackingWall`（swamp60-front，跨套引用）：连续藤蔓保持朝墙。

## next-node/

| 场景文件 | 场景段 | 用例 |
| --- | --- | --- |
| `west.json`、`middle.json`、`relay.json` | 出口栏杆外沿 → 中间平台 → 下一接力点 | `outer-fence-to-next-relay` `[43.07,25,-3757.70] → [95.5,21,-3759.5]`、`live-platform-handoff-to-next-relay` `[62.63,21,-3758.45] → [95.5,21,-3759.5]` |

栏杆同一格的不同站立侧不合并为同一个 A* 状态，也不能在路径组装或代价重算时换回方块中心。

## swamp-nether69/

| 场景文件 | 场景段 | suite 用例 |
| --- | --- | --- |
| `swamp54.zip` | swamp54 起始到金色终点 | `swamp54-start-to-gold` |
| `swamp55.zip` | swamp55 起始到金色终点 | `swamp55-start-to-gold` |
| `swamp57.zip` | swamp57 起始到金色终点（粘液链） | `swamp57-start-to-gold` |
| `nether69.zip` | nether69 起始到金色终点 | `nether69-start-to-gold` |
| `swamp60-front.zip` | 60 前段（藤蔓、起始两段活塞） | `swamp60-start-to-relay24`、`swamp60-relay24-to-relay60`、`swamp60-relay60-to-relay74`（活塞）、`swamp60-user-start-first-pistons`（用户起点 `[60.5,18,-3290]`，活塞） |
| `swamp60-middle.zip` | 60 中段（藤蔓、机关接力） | `relay74-to-relay84`、`relay84-to-relay97`、`relay97-to-relay102`、`relay104-to-relay111`、`relay111-to-relay118`、`relay118-to-relay148` |
| `swamp60-back.zip` | 60 后段到金色终点 | `relay148-to-relay160`、`relay160-to-gold` |
| `swamp60-tower.zip` | 60 提升塔 | `swamp60-relay102-to-relay104` |

`swamp60-piston.json` 供前段两段活塞用例作为 `dynamicReplay`。`SwampNetherRegressionTest` 的定点断言：

- `slimeLiveLaunch`（swamp57）：粘液实时发射与地图垂直纠正后仍能重新规划落地。
- `swimGap`（swamp60-front）：共享游泳控制器到达下一水池。
- `suspendedPoolCatchSettlesBeforeHandoff`（swamp60-front）：悬浮水池必须先稳定接住再交接。
- `crouchedVineCannotPassLeafCeiling`（swamp60-middle）：蹲姿藤蔓不能穿过树叶顶。
- `vineAscentRequiresClearBodyCorridor`（swamp60-middle）：藤蔓攀升需要畅通的身体通道。
