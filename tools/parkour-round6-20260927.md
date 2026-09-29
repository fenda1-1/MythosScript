# 跑酷优化 第6批报告（2026-09-27）

状态：**已完成** —— 离线快照证据已核实；代码冻结并部署成功（PID 25148，`round6-deploy.log` 成功）；三关实机结果已回填并逐项核对（JSON 字段/SHA256/生存样本均验证）。

## 基线

- 基线：`a1f7ea4`（第5批基线）
- 第5批三关实测：snow-10 = 58.500s，swamp-55 = 39.890s，swamp-60 = 83.266s（同 PID 27660）
- 第5批证据：`build/parkour-evidence/round5b-live/`、`build/parkour-evidence/round5b-remaining/`（JSON/JSONL SHA256 与 `tools/parkour-round5-20260927.md` 全部吻合）

## 第6批候选优化（主代理实现，已部署并实机验证）

1. **空hazard谓词快路径**
2. **非默认摩擦稀疏快照**
3. **CollisionIndex 直接存在性查询**
4. **beam resume 子预算续算**（CocoaRunupTest 修复引入，见下节）

## 离线证据（2026-09-27，未部署）

### ① 首次测试（round6-snapshot-before.log）

- `build/parkour-evidence/round6-snapshot-before.log`
- ParkourSnapshotTest：**2 tests / 1 failure**
- 失败原因：测试错误假定 captured context 禁止火接触；断言已按 `context.allowFireContact` 修正（测试侧修正，非优化回退）
- 快照基线指标：空hazard 100000 checks = 41600208 bytes / 24098400 ns；索引潜行 20000 steps = 35862800 bytes / 101135800 ns
- 离线 compile+tests：10.42s

### ② 断言修正后（round6-snapshot-before-fixed.log）

- `build/parkour-evidence/round6-snapshot-before-fixed.log`
- ParkourSnapshotTest：**2 tests / 0 failures**（ms=4353）
- 空hazard 100000 checks = 41600208 bytes / 19234600 ns
- 索引潜行 20000 steps = 36036496 bytes / 95783100 ns
- 离线 compile+tests：10.19s

### ③ 三项优化后（round6-offline.log）

- `build/parkour-evidence/round6-offline.log`
- ParkourSnapshotTest：**2 tests / 0 failures**（ms=8876）
- 空hazard 100000 checks = **208 bytes** / 4568200 ns（基线 41600208 bytes → 约 0.0005%）
- 索引潜行 20000 steps = **29639968 bytes** / 75613800 ns，且 20000 物理结果与基线**逐字段相等**
- 其余回归：VineExitTest 3/0、SwampNetherRegressionTest 5/0、AdditionalParkourRegressionTest 6/0、SnowFlowTest 7/0 —— 全部通过
- **CocoaRunupTest：1/1 失败**（frames=0，searchMillis=2016，"Recorded upper platform must be reachable with cache disabled"，CocoaRunupTest.java:23）—— 已在后续 round6-resume-offline.log 中修复（见下节），当时失败未解决，不能记成回归全过
- 离线 compile+tests 合计：34.28s
- **未部署**，无实机三关数据

## 代码冻结后离线复验（round6-resume-offline.log，2026-09-27）

`build/parkour-evidence/round6-resume-offline.log` —— **25 tests 全部 0 failures**：

| 测试 | 数量 |
|---|---|
| CocoaRunupTest | 2/0（ms=8306） |
| ParkourSnapshotTest | 2/0（ms=686） |
| VineExitTest | 3/0（ms=1070） |
| SwampNetherRegressionTest | 5/0（ms=572） |
| AdditionalParkourRegressionTest | 6/0（ms=3347） |
| SnowFlowTest | 7/0（ms=10120） |

### 续算机制证据（beam resume）

- 日志第8行：`trajectory_search_resume tick=21 nextFrame=187 frontier=380 remainingMs=5985`
- 第9行：`cocoa prefetch frames=33 searchMillis=2126`
- 证明：在**原 8s 整体预算内**，保留 2s 子预算的未完成 beam 续算，**不是扩大总预算，也不是跨次缓存**
- **范围限定（不得夸大）**：当前仅 `searchWithRunup` **高目标分支**在备用路径失败后续算；**同高度分支仍为原策略**，不能声称全部轨迹搜索均支持续算

### 新增回归（确定性 resume 回放）

- 用 interrupt 触发检查点、`finally` 清 flag、测试中显式 resume 并验证逐控制回放
- 生产取消任务不会恢复（仅测试路径显式续算）

### 分配复验

- 空hazard：208 bytes（不变）
- 潜行快照：29506192 bytes / 128547300 ns —— **时间不稳定，不可声称固定速度提升**

### 生产变更范围

- 生产代码仅 `ParkourTrajectory.java`（slipperiness!=.6F 稀疏快照、CollisionIndex.intersects 存在性查询、BeamSearch/resumeBeam 续算）与 `ParkourSurface.java`（空 hazards/liquids isEmpty 快路径）
- 测试：`CocoaRunupTest.java` 新增 `pausedBeamKeepsItsPartiallyExpandedFrontier`
- 部署 jar：`MythosScript-v1.0.72-mc1.12.2.jar`，launch.sha256=`86f6ee605d4ad3131fcdeeb0575f1ee89d9858a17f03c41f931263f1f5be6589`，commit=`a1f7ea4a2b43084e0eb43465af8c6ecf3d1e6d00`（source.json 两目录一致：7F90E9189AF227241C2AC2C86806E7596E02FAF9839B4E1CB44128FC4FFD9998；launch.mode=inject，port=8821，source.json 两目录同 hash 即同一次部署同 PID）

## 实机结果（round6-live + round6-remaining，PID 25148）——第6批验证完成，待主代理本地提交；不代表全面验收目标完成

三关全部 passed=true，同 PID 25148，cache=false（脚本 trace：`!set parkourInputCache false`，结束后读取当前值=false），debugEnabled=true。三份结果的 `initial.player.creative` 和 `final.player.creative` 均为 false；预检强制并确认生存。全部样本 alive=true、flying=false；中间样本未保存 creative 字段，因此不以健康值代替游戏模式证据。executionAudit 均 finished=true、outcome=success、timedOut=false。

| 关卡 | 第5批 | 第6批 | 差值 | 证据 |
|---|---:|---:|---:|---|
| snow-10 | 58.500 | 57.047 | -1.453 | `build/parkour-evidence/round6-remaining/snow-10-1.json` |
| swamp-55 | 39.890 | 39.656 | -0.234 | `build/parkour-evidence/round6-live/swamp-55-1.json` |
| swamp-60 | 83.266 | 83.765 | +0.499 | `build/parkour-evidence/round6-remaining/swamp-60-1.json` |
| **合计** | **181.656** | **180.468** | **-1.188** | |

**单次观察，不声称稳定提速**。prefetch applied/drift 三关分别 8/5、4/1、7/2，与第5批一致；graph 任务数雪地3→1、沼泽55为1→1、沼泽60为12→10。graph 没有单列结果事件，reporter 标为 unobserved，不能解读为任务失败。两批三关均没有 `fail` 或 `trajectory_replan`；仍有 flow_blocked 9/3/5（flow 候选回退，不是整关失败）。

### 关键字段核对（已验证）

- swamp-55-1.json（marker=`0.5 46 -3101.5`）：observedSeconds=40.344，debugLogs[0].sha256=`10c11cc1aa20b79750b7d9faa41e3cb6d14d5c36ada3abf742c6f0af9442f069`（与实际 Get-FileHash 一致）
- snow-10-1.json（marker=`-8999.5 52 -10025.5`）：observedSeconds=57.75，samples 546 条全 alive=true/flying=false（health 16–20）；debug jsonl `build/parkour-evidence/round6-remaining/snow-10-1-parkour-2026-09-27_18-16-49-495-4698221001424311370.jsonl` sha256=`1B058FC09DC2760A47092670BC1BEA3B19447D18BCBC7277A7B977C5DD1FC4F2`（一致），type="tick" 记录 1170
- swamp-60-1.json（marker=`-8999.5 52 -10025.5`）：observedSeconds=84.609，samples 795 条全 alive=true/flying=false（health 15–20）；debug jsonl `build/parkour-evidence/round6-remaining/swamp-60-1-parkour-2026-09-27_18-17-50-443-2074636177282987697.jsonl` sha256=`A48C9EDDF8E53DAD4D7EAB20F5E08B60DA0707AADF483DA4D930298B46139122`（一致），type="tick" 记录 1708
- 沼泽55 marker 是下一段入口 `0.5 46 -3101.5`；雪地和沼泽60返回大厅 `-8999.5 52 -10025.5`。终点金压力板仍由原单目标序列指定，marker 只是通关传送确认。

### 第6批 vs 第5批归因（同 PID 单进程口径，墙钟/CPU/分配均不能相加为通关时间）

| 关卡 | Δ秒 | 主要差异（第6批 vs 第5批） |
|---|---:|---|
| snow-10 | -1.453 | 静止 10.006→8.696s、noMovement 4.703→4.092s；graph solve 3.074→2.587s；flow 7 applied不变；prefetch solve 9.071→8.161s、CPU 8.703→7.969s、分配6.647→5.365GB；观测最大堆599→551MB、GC累计917→325ms |
| swamp-55 | -0.234 | 静止10.154→9.992s；noMovement 5.706→6.015s；graph solve 3.503→3.896s、CPU 3.312→3.703s；prefetch solve 7.780→7.237s、CPU 7.547→7.031s、分配8.288→7.123GB；MovementParkour 31.728→31.185s；GC累计415→324ms |
| swamp-60 | +0.499 | 静止17.838→17.788s；noMovement 10.805→11.207s、MovementParkour 53.902→54.005s；graph solve 15.596→16.359s、CPU 15.141→15.516s、分配13.417→12.677GB；flow 18 applied相同、分配4.269→3.489GB；prefetch分配2.094→1.730GB；观测最大堆708→615MB、GC累计742→834ms |

位置归因：雪地起点静止3.748→3.440s、25/8平台1.650→1.251s、31/14平台1.453→1.250s；沼泽55起点5.101→5.385s变慢，但14/24平台2.650→2.356s、可可豆13/22.75平台1.100→.949s缩短；沼泽60的74/25平台6.600→6.709s、102/25平台1.350→1.750s等待增加。后者与无movement增加吻合，是本次局部退化的实际位置。

GB/MB均为十进制字节单位。图任务数减少而累计求解增加，但任务负载及调度不同，不能从均值断言相同任务变慢；GC累计也不是本线程停顿时间。只有一次前后实测，既不能确认稳定收益，也不能宣称差值必然落在已知统计波动范围。沼泽60墙钟差值主要落在无movement阶段，尚不能把它精确分摊给CPU或GC。完整逐段路线与搜索事件见两份路线附录。

### 路线附录

- `tools/parkour-round6-swamp55-routes-20260927.md`（swamp-55，39.656s）
- `tools/parkour-round6-remaining-routes-20260927.md`（snow-10 57.047s / swamp-60 83.765s）

## 本批验收

- [x] CocoaRunupTest 失败定位与修复（beam resume 机制，25 tests 全绿验证）
- [x] 修复后离线回归全绿（含新增 `ParkourSnapshotTest` 2 tests 及新 `pausedBeamKeepsItsPartiallyExpandedFrontier` 后 `CocoaRunupTest` 2 tests；25 tests 全绿见 `build/parkour-evidence/round6-resume-offline.log`）
- [x] 实机三关复测：snow-10=57.047 / swamp-55=39.656 / swamp-60=83.765（合计 180.468 vs 第5批 181.656，-1.188s）
- [x] 与第5批同 PID 单进程口径对齐（第6批同 PID 25148）
- [x] tick 口径说明（本批实数）：reporter 的 tickCount（`build/parkour-evidence/round6-live/analysis.json`=793、`build/parkour-evidence/round6-remaining/analysis.json` snow=1141 / swamp-60=1675）为执行窗口统计；原始 JSONL 中 type="tick" 记录数为 swamp-55=822、snow-10=1170、swamp-60=1708（含等待/收尾 tick）。两种口径分属不同统计窗口，引用时须注明来源，不混用。（第5批的 1171/798/1665 与 1196/828/1695 为旧批数字，与第6批无关）
- [x] 证据核对：JSON pid/cacheEnabled/cacheAfter/debugEnabled、samples alive/非fly、debugLogs[].sha256 与实际 Get-FileHash 一致、source.json commit/jar SHA256 与 diff 一致（三关全部通过，无文档错误）
- [x] 主代理再次核对实际jar SHA256、两份source.json的完整源码diff（统一换行后与当前生产/已跟踪测试diff一致）、initial/final生存字段、executionAudit成功和全部debug SHA256。
- [x] `tools/parkour_handoff_check.py` 三关通过：雪地Descend1、沼泽55 Ascend2、沼泽60 Descend2/Ascend1；`tools/parkour_replan_check.py` 沼泽60三次安全边界续算通过，实际等待2.849/.299/1.100s。

## E24 新证据（未完成项记录）

`build/parkour-evidence/vanilla-EntityLivingBase-bytecode.txt:1346-1397`：客户端 isRemote 时药效 duration=0 不会移除 activePotionsMap；`:1871-1888` onFinishedPotionEffect 在客户端不修改属性，实际速度来自服务器属性包。结论：速度/缓慢效应不能按本地 duration 猜测修改，后续需记录属性更新与效应包，**E24 仍未完成**。

## 状态声明

第6批文档验证与核对**已完成**，待主代理本地提交；第6批三关通过不代表全面验收目标（F29）完成。
