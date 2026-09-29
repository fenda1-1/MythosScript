# 跑酷全面优化验收账本

目标起点：`528d4a12e88567d481c98bf5383cb92e3e42f0ad`。本账本对应用户2026-09-27提出的“完成全部优化”，持续跨批更新。**当前未完成**；一批通关或有收益不能代替全面验收。

## 总体交付与证据门槛

| 要求 | 证据位置 / 当前状态 |
|---|---|
| A1–F29全部有实现验收或有依据的不采用结论 | 下表；未决项不能勾选完成 |
| 沼泽55八次碰撞拒绝与重算等待 | 第4批修普通上下台阶空中完成，实机8→0、95.610→40.766s，原路线继续；五条地面边独立搜索限制仍待审计 |
| 沼泽60藤蔓111tick超时 | 第3批离线129→35帧；最终实机5.548s超时→2.350s成功，整关通过 |
| 沼泽60尾段11次重搜及局部退化 | 第3批统一黏液物理及异步起点校验，实机重搜11→0、尾段8.495→4.554s |
| 已加载远终点完整规划、未加载分段及加载续算 | 可靠性批次 `fe8ebc9`、`tools/parkour-loaded-goal-20260927.md`；最终组合须再核验 |
| 真实药效、活塞/黏液/消失冰、受伤/中毒同步 | 录制物理回归及实机事件；不得以静态fixture代替动态验收 |
| 固定单目标三关、生存、历史输入缓存关闭 | `tools/parkour_benchmark.py --courses snow-10 swamp-55 swamp-60 --debug --sequence-prefix 优化测试_`，每批同客户端每关一次，仅部署新jar重启 |
| 保留全部失败/无效测试，完整路线与耗时归因 | 各批原JSON/逐tick JSONL及 `tools/parkour_benchmark_report.py`；失败秒不是通关时间，静止/无executor与后台计算不能相加 |
| 来源可追溯，每批本地提交 | 各目录 `source.json` 的commit/diff/实际部署jar SHA256；报告和源码随批提交 |
| 不降低碰撞/落地安全、不硬编码路线 | 每批审查源码diff及相关物理/动态回归；缓存默认开启兼容性另验，不混入算法基准 |
| 最终没有未处理的有证据优化项 | 最终全表审计、同口径三关实机、累计对照、剩余主要耗时解释；目前不满足 |

## A1–F29逐项跟踪

“已有部分证据”不是最终处置。候选可以不采用，但需具体成本、适用性或对照证据；不能仅写“以后再做”。原始边界和验证指标见 `todolist.md`。

| 项 | 当前证据 / 下一验收点 | 状态 |
|---|---|---|
| A1 跨movement交接 | 第4批真实支撑完成；第5批清普通落地残速，使19段预取成功应用，14/24静止4.804→2.650s；三关0碰撞 | 部分保留 |
| A2 有限时域预取 | 第2批150tick永久面截链；第5批预取应用/漂移丢弃雪地8/5、55为4/1、60为7/2，仍需浪费归因 | 部分保留 |
| A3 ETA提前规划 | 依赖F28排队/求解与执行剩余时域对照 | 未决 |
| A4 安全续行/驻留 | 第3批等待潜行抑制黏液微弹跳，实机重搜11→0；其它驻留收益待审计 | 部分保留 |
| A5 增量图搜索 | 第4批沼泽55无executor降至4.688s、仅1条规划路线；第6批沼泽60图10 jobs全unobserved solve16.359s（第5批12 jobs 15.596s），安全边界重算仍需归因，再决定复用 | 未决 |
| A6 可执行解/少量备选 | 已有flow前缀与能力前沿；需失败重搜代价、备选命中与维护成本 | 未决 |
| A7 无效暂停 | 可靠性批次修复弹射飞行包围区被当作进度；需最终误暂停统计 | 部分修复 |
| B8 近期边提前验证 | 沼泽55五条地面边被执行期拒绝，另有空中失败；需按实际起点验证并及时反馈 | 未决 |
| B9 按需验证 | 先审计B8现有flow/prepareAhead命中与无效验证 | 未决 |
| B10 备选并行 | 依赖A6/B9及F26 CPU/队列证据，不直接扩大线程数 | 未决 |
| B11 运动+验证成本 | 沼泽55坏捷径造成长搜索；先修物理/必要几何条件，避免无依据固定罚分 | 未决 |
| B12 保守几何界 | 沼泽55窄支撑/头顶障碍及极限上升候选待逐边复现；只允许可靠必要条件硬拒绝 | 未决 |
| C13 控制候选 | 第3批藤蔓129→35模拟tick，实机5.548s超时→2.350s成功；其它候选热点待审计 | 部分保留 |
| C14 搜索结构/去重 | 现有5ms栈采样不能证明CPU改善；需热点与分配证据后决定 | 未决 |
| C15 临时分配 | 第5批空水快路径/beam不变量外提/复用碰撞索引；第6批安全谓词快路径/稀疏摩擦快照/无列表支撑查询，25离线与三关通过；沼泽55预取分配8.288→7.123GB，仍需进一步归因 | 部分保留 |
| C16 版本化几何复用 | 同次context已有缓存；跨搜索重建成本和可靠失效证据尚缺 | 未决 |
| C17 复合边 | 现有flow链已实现；需比较重复展开节省与长链失败扩大范围 | 未决 |
| C18 助跑枚举 | 需区分searchSprintLanding与普通searchWithRunup热点；不能由总耗时推断；第6批beam resume已修该分支子预算丢弃问题 | 未决 |
| C19 失败分类/失效 | 当前空结果统一拒绝边，TTL120–600s；需区分预算耗尽和几何失败及状态变化 | 未决 |
| D20 真实状态重模拟 | 第3批补齐异步起点ground/vy校验并实机通过；剩余输入重模拟需收益和安全证据 | 部分保留 |
| D21 后备动作恢复 | 第4批确认沼泽55三次空中失败来自普通运动提前完成，已修并实测0失败；其它恢复适用性待审计 | 部分保留 |
| D22 安全前缀 | 已有flow分段及未加载/能力前沿；需期限与动态支撑覆盖验收 | 部分实现 |
| E23 冰/活塞窗口 | 第4批沼泽60首试活塞中位置/速度突变掉落，用户要求同jar重跑85.593s成功；异常原因未抓包，仍需解释 | 部分验证 |
| E24 效果时间线 | 跳跃效果递减/真实能力变化已处理；第6批新增bytecode证据（客户端isRemote duration=0不移除activePotionsMap、onFinishedPotionEffect客户端不改属性、速度来自服务器属性包），速度/缓慢不能按本地duration猜改，需记录属性更新和效应包 | 未决 |
| E25 服务器纠正对账 | 受伤/中毒已有抓包证据和本地3tick裕量；第3批尾段是物理/起点问题，不能归因服务器纠正 | 未决 |
| F26 调度/条件扩线程 | 第5批三关排队.166/.009/.005s，本轮不扩线程；仍需取消浪费、CallerRunsPolicy及客户端tick尾延迟审计 | 部分处置 |
| F27 JVM独立对照 | Java8u202/-Xmx800M；第5批GC累计917/415/742ms，最大堆599/506/708MB，先减少分配，独立对照未完成（第6批maxHeap 551/495/615MB） | 未决 |
| F28 分阶段计时 | 第5批四类轨迹+图墙钟/CPU/分配、快照/排队/结果及JVM采样已验；图任务结果和短任务粒度仍需明确 | 部分保留 |
| F29 全面最终验收 | 第2批3关69.766/96.750/96.094s为起点；第6批三关57.047/39.656/83.765s合计180.468s（第5批181.656s），最终组合尚未完成 | 未完成 |

## 第3批已验：藤蔓与黏液状态一致性

- 基线：`tools/parkour-round2-20260927.md`，实机jar `1ed429c721f53ab920d2e74b5aa350a7f9cc174002b474a505135393a997e8d1`。
- 藤蔓：实际80/26→81/27在到达目标高度后仍向墙攀爬，绕到Y28再反复升降。实机选132帧计划但在111tick超时；从记录位置80.499744/26.617055/-3288.506160离线复现129帧。修正ladderRoute只在低于目标时继续顶墙，仍使用原安全与稳定落地判断。
- 黏液：实机clientTick5045/5046同位置onGround交替，vy=-.001568/-.078400。旧普通轨迹不模拟黏液，只在专用弹射中启用，错误预测持续落地；下一跳实际未触发，出现约.43位置误差。修正统一模拟真实黏液物理，搜索等待潜行保持起点，异步结果同时校验ground与vy，未放宽原误差阈值。
- 先失败后通过的检查：`build/parkour-evidence/vine-slime-before.log`，VineExitTest 3项中新增2项失败（129帧、vy错误）；`build/parkour-evidence/vine-slime-after.log`，VineExit3+SwampNether5+Additional6+SlimeReplan1+SnowFlow7共22项通过。藤蔓变为35模拟tick，进程31.73s是离线墙钟，均不等于实机通关时间。
- 首次部署：`build/parkour-evidence/vine-slime-deploy.log`，PID14840；实际jar SHA256 `f9ab2723ecb89ee2c9d67e58a9dfde2c7452138ef84ec02d7b6b7d73e17b1792`。`build/parkour-evidence/vine-slime-live/` 保存source/diff/逐tick/样本，整关240.031s超时，**不是通关耗时**；静止190.718s，无executor189.194s（重叠，非CPU计时）。藤蔓80/26→81/27用2.350s通过；末段黏液未到达，不能声称已实机解决。
- 新暴露的旧片段失效漏洞：26.242s规划的未加载探索片段包含102/25→102/10水池；32.878s终点加载后仅清next/inProgress，current仍保留整个旧片段；49.277s真实跳高变6.134969096353679后再次只清后继，仍执行旧MovementFall，最终106.829599/11/-3289.512954无路。时间为JSONL起点，和序列墙钟存在约.22s偏移。根因不是藤蔓/黏液模拟，而是世界/能力变化后未淘汰current的旧后半段。
- 候选修复：请求PathExecutor在可取消且有落地/梯子/水支撑的边界退出旧片段；完整保留进行中的Parkour轨迹与连续活塞/黏液弹射，再从实际位置规划。等待期间禁止拼接造成取消标志丢失。`tools/parkour_replan_check.py` 对上述旧日志确实失败；新的实机日志须通过该检查及原通关判断。
- 补充离线：`build/parkour-evidence/safe-boundary-offline.log`，LoadedParkourGoal7+CapabilityFrontier1+VineExit3共11项通过，80.75s进程墙钟。它们验证相关规划/物理回归，不代替新的执行边界实机检查。新部署日志 `build/parkour-evidence/safe-boundary-deploy.log`；本批尚未提交。

以上为实验过程记录。最终见 `tools/parkour-round3-20260927.md`：同PID24200三关69.000/95.610/84.297s通过，总262.610→248.907s。3次安全边界检查通过；藤蔓及尾段故障本次未复现，74/25新增6.806s静止、高塔增加.701s均记录。沼泽55仍8次拒绝，继续下一批，未完成全面目标。源码与报告共同本地提交。

## 第4批已验：普通运动的落地交接

- 基线提交 `2aa2593`。沼泽55三次碰撞拒绝紧接普通Ascend/Descend在空中宣布SUCCESS，真实vy分别约+.165/-.377/-.377；雪地和沼泽60也有同类失败。脚部包围盒进入目标格不等于落地，PathExecutor的自动推进保护没有覆盖movement自己的SUCCESS返回。
- 候选修复：Ascend/Descend共用`Movement.finishAfterLanding`，跑酷模式等真实ground/ladder/water支撑，期间释放跳跃、潜行并继续向落点纠偏；普通模式保持原完成规则。新增`ordinary_handoff`日志及`tools/parkour_handoff_check.py`，实机必须核验所有记录的普通上下台阶交接均有支撑。
- `build/parkour-evidence/handoff-ground-offline.log`：21项已有回归通过；另一个诊断用例重放5条地面失败边，仍全部空轨迹，分别2032/3198/4003/4214/7260ms搜索墙钟。这不是5条边不可达的证明，也不是实机耗时。
- 新jar部署 `build/parkour-evidence/handoff-deploy.log`，PID43712。完整结果见 `tools/parkour-round4-20260927.md`：雪地61.157s、沼泽55为40.766s、沼泽60用户要求重跑85.593s成功，成功尝试总187.516s（原248.907s）；普通支撑交接检查全部通过，三关成功尝试碰撞均0。沼泽60首试36.515s重置保留，不能称首试全过或活塞故障已排除。
- 临时`SwampGroundDiagnosticTest`用30秒预算重放：可可豆起点1851ms找到25帧，其余四条9.8–10.4s仍空解，不能硬判无路。实机可可豆仍经过且预取33帧成功；另外4条未进入旧失败恢复路线，不等于其搜索问题已修复。
- 下一处已知浪费：沼泽55在14/24静止4.804s，同一19段链18.854–21.979s预取后22.067–25.305s重复计算。须量化同次计划有效复用、队列/快照/CPU/GC，再处理A2/A3/B8/B9/C17/F26–F28；继续保留完整安全和关闭跨次缓存。

## 第5批已验：预取交接、等价查询复用及分阶段计量

- 原记录在20.246s末帧与预测吻合，接着中性交接tick水平漂移约.0103格，超出异步起点.005阈值；普通稳定落地SUCCESS前清零水平速度，与既有`prepareAhead`预测一致，保留全部起点及安全检查。最终19段链成功应用，14/24落点静止4.804→2.650s。
- 四类轨迹提交增加job id、快照/准备、排队、求解墙钟、线程CPU、分配字节及applied/drift/empty/reset/damage结果；图搜索单列，JVM每20tick采样堆/GC。不支持的CPU/分配指标记-1而非0；任务可以与移动及其它线程重叠，累计值不能相加成通关时间。
- `build/parkour-evidence/round5-offline.log`：VineExit3+SwampNether5+Additional6+SnowFlow7+Diagnostics3共24项通过；编译与测试进程墙钟23.45s。reporter自检查通过，覆盖未知CPU、排队后取消、丢弃预取和快照累计。
- E24源码审计发现待处理项：`ParkourTrajectory.Frame`只有jumpBoostTicks递减；walkingSpeed直接捕获当前属性且不含速度/缓慢效果的到期时间。尚未证明这三关触发，但它是明确模型缺口，需独立物理回归，不能将E24勾选完成。
- 初版沼泽55虽成功但85.547s/5次碰撞，原始记录保留；真实可可豆预取起点在2秒预算返回空，追加等价碰撞索引查询复用/不变量外提，录制回归从空解变33帧且安全重模拟通过，另外21项既有回归通过。
- 最终同PID27660三关58.500/39.890/83.266s，合计181.656s（第4批187.516s），均0碰撞/轨迹重规划、支撑交接及来源SHA核验通过。无控制器总量19.448→21.214s，局部退化与初版失败均记录于`tools/parkour-round5-20260927.md`和三份完整路线附录；这是单次观察，非统计性提速证明。
- 下一批依据：沼泽55单条19段预取仍CPU4.094s/分配4.99GB，沼泽60图计算累计CPU15.141s/分配13.42GB；先定位分配及无效预取，不靠扩线程掩盖计算。第4批活塞异常和E24效果到期仍是开放正确性事项。

## 第6批已验：空hazard快路径、稀疏摩擦快照、存在性查询与beam子预算续算

- 基线 `a1f7ea4`。生产变更仅 `ParkourTrajectory.java`（slipperiness!=.6F 稀疏快照、CollisionIndex.intersects 存在性查询、BeamSearch/resumeBeam 续算）与 `ParkourSurface.java`（空 hazards/liquids isEmpty 快路径）；测试新增 `CocoaRunupTest.pausedBeamKeepsItsPartiallyExpandedFrontier`。jar `MythosScript-v1.0.72-mc1.12.2.jar`，launch.sha256 `86f6ee605d4ad3131fcdeeb0575f1ee89d9858a17f03c41f931263f1f5be6589`。
- 根因与修复：searchWithRunup 高目标分支 direct 子预算耗尽时丢弃 beam 进度；修复为同次搜索内保留前沿、8s 剩余预算复用续算（不扩预算、不跨次缓存），仅该分支备用路径失败后续算，同高度分支保持原策略；生产取消任务不恢复。新增确定性 resume 回归（interrupt 触发检查点、finally 清 flag、显式 resume 逐控制回放）。初版 `ParkourSnapshotTest` 1 失败（火接触断言错误，`build/parkour-evidence/round6-snapshot-before.log`）保留；CocoaRunupTest 失败（frames=0/searchMillis=2016，`build/parkour-evidence/round6-offline.log`）保留；火属性断言修正后 25 tests 全绿（`build/parkour-evidence/round6-resume-offline.log`：trajectory_search_resume tick=21 nextFrame=187 frontier=380 remainingMs=5985，cocoa frames=33/searchMillis=2126）。
- 实机同 PID25148 三关 57.047/39.656/83.765s，合计 180.468s（第5批 181.656s，-1.188s）；**单次波动，非统计性提速证明**。全部 passed=true、cache=false（trace 设 false 且结束读取 false）、生存样本全 alive/非fly、debug jsonl SHA256 与 JSON 记录一致、source.json commit/jar SHA256 核验通过。搜索计量与第5批同模式：snow prefetch 13(8/5)、55 prefetch 5(4/1)、60 prefetch 9(7/2)；60 graph 10 jobs 全 unobserved（第5批12）。详见 `tools/parkour-round6-20260927.md` 与两份路线附录。
- E24 新证据（仍未完成）：`build/parkour-evidence/vanilla-EntityLivingBase-bytecode.txt:1346-1397` 客户端 isRemote 时药效 duration=0 不移除 activePotionsMap；`:1871-1888` onFinishedPotionEffect 客户端不改属性，实际速度来自服务器属性包。速度/缓慢不能按本地 duration 猜改，需记录属性更新和效应包。
- 新增 `src/test/java/com/mythos/mythosScriptMod/shadowbaritone/pathing/movement/parkour/ParkourSnapshotTest.java`：危险物/液体边界与快照隔离、2万次随机潜行碰撞逐字段等价；空危险物10万检查分配41600208→208字节，不能由微基准计时推断实机收益。三关普通运动落地交接检查、沼泽60三次安全边界续算检查通过；initial/final creative=false、实际jar与源码diff及debug SHA均再次核对。
- 遗留：第4批活塞异常未抓包解释；C15沼泽55预取仍分配7.123GB、沼泽60图分配12.677GB；F27独立对照未做；A3/A5/B8–B12/C14/C16–C19/E23/E25/F26/F28证据门槛未变。

## 第7批可靠性修复进行中（2026-09-28，未验收）

- 基线 HEAD `bdc4f23`；旧部署 PID43392、jar SHA256 `af3cdefb4665f74223fa627ae0d9e13af00c7639471f463a6c160a1ae6bee401`。此前 `build/build.log` 实为325项中6失败、4跳过，不能把已生成JAR当作构建验收通过。新增地下/70直达序列已经发布，原序列未改。
- 撤销 `0e4e22e` 的两处无证据补救：完整图穷尽后随意走最远节点、空中空轨迹后盲冲40tick。恢复已加载完整规划/确实无路拒绝、真实能力前沿及未加载分段语义；LoadedGoal7项、Capability1项回归通过。地下旧空中速度突变尚未抓包证实原因；一次旧jar抓包诊断停滞失败且早期包被容量逐出，记录保留在 `build/parkour-evidence/round7-underground-packets`。
- 岩浆单位立方体误封半砖跳跃空间：用覆盖原版全部渲染角的保守液面上界，完整人物盒与液面检查外继续保留原版收缩伤害检查；快照只存不可变形状。500组原版渲染高度函数×四角对照通过，离线与生产规则统一。新增逐tick实际body/lavaContacts日志用于实机验收。
- 永久录制夹具：`src/test/resources/parkour/underground/direct-goal.zip`（SHA256 `d01cf6ebaba914ddb19b7d4621c1c35677b1f8c65c2658dac902d16a7fddcc4f`）、`src/test/resources/parkour/nether-70/direct-goal.zip`（`f0db1cd5b0440a64207a781e2c90e97cead74873b8757a82dcec200d7ba09297`）。正确70终点取189.5/18/-3759.5，不再使用错格-3759或宽松partial断言。
- `build/parkour-evidence/round7-cache-off-regressions.log`：34项32通过；两项旧 `ParkourTrajectoryTest`（liveCapturedLavaGapReachesTheWestLedge、reverseLongGapUsesSidewaysRunway）仍失败，待独立诊断。没有宣称全套通过；UI字体失败也未修复。
- 完整单终点生产图/轨迹/交接关缓存回放：`build/parkour-evidence/round7-underground-verified-boxes-results.json` 地下46段、598安全帧、无拒绝；`build/parkour-evidence/round7-nether-70-verified-boxes-results.json` 70关100段、1039安全帧、3拒绝后重算（123/13→130/13，161/19→165/19两落点），均SUCCESS_TO_GOAL。进程19.19/108.72s为离线编译和搜索墙钟，不是实机通关秒数。
- 测试工具停滞改按真实单调时钟满10秒及固定位置锚点累计位移中止，替代280次MCP轮询的错误近似。剩余门槛：候选部署、两故障关实机全盒核验、三基准同PID完整通关、实际源码/jar一致、报告和本地提交；A1–F29全面目标仍未完成。

### 第7批后续纠错：液面模型不能证明无火伤

- 上述598/1039帧仅通过当时的液面模型，**不是无岩浆火伤证明**。候选实机地下9.734秒坠落、70关规划超过10秒中止，均未通过，不能计为通关。
- `build/parkour-evidence/round7-filtered-packets` 捕获地下火伤状态37、UpdateHealth 19及随后的实体速度包；速度包的 `.419875` 上升量与下一客户端tick吻合，随后轨迹误差 `.21397369143512535` 触发重算。此前 `inLava=false` 和 `lavaContacts=[]` 漏检。
- 原版字节码 `build/parkour-evidence/vanilla-Entity-bytecode.txt` 与 `build/parkour-evidence/vanilla-World-bytecode.txt` 确认 `Entity.move → boundingBox.shrink(.001) → World.isFlammableWithin` 检查整格 LAVA/FLOWING_LAVA，不看渲染高度。因此撤回液面边界，生产和独立捕获回放都恢复完整人物盒与整格岩浆相交判定。日志新增 `health/hurtTime/burning` 和口径 `lavaContactRule=full_body_block_cell`；核验脚本拒绝旧口径和燃烧样本。
- `build/parkour-evidence/round7-full-cell-offline.log`：Snapshot 2项通过；70关严格整格图完整达189/18/-3760，搜索11.399秒；地下5项中2失败（半砖重规划/助跑），不是整批通过。
- 地下半砖排查：`round7-slab-physics-bootstrap.log` 两个静止起点各10秒搜索为空；`round7-slab-control-grid.log` 和 `round7-slab-nearest-pad.log` 的短控制枚举也未找到静止起步解。不能由此证明无路。`round7-slab-momentum.log` 仅作能力对照：10.05/10.5/-1502.4 静止为空，预置vx=.26/vz=-.05得到13帧全盒安全解到13.5/10.5/-1503.5；此速度尚需证明来自真实前一跳，不能直接写入生产起点或当通关。
- 后续 `build/parkour-evidence/round7-slab-chain.log` 已证明来源动量：从5.5/11/-1502.5正常起跳，16帧落到9.820350189524733/10.5/-1502.5，vx=.23159652538272107；紧接13帧安全到13.578703268050388/10.5/-1503.4200908473867。没有预置非零起速。新增 `UndergroundMomentumTest` 保留真实前跳、所有帧全人物盒及20tick稳定落地检查。
- 初版候选用完整净空走廊补充固定弧线过滤；`build/parkour-evidence/round7-clearance-offline.log` 地下动量1、LoadedGoal7、Capability1、NetherLoaded1共10项通过。但能力前沿用例97.353秒、70图14.804秒，不能称性能通过。后续将走廊补充限定于原固定弧线不碰实体障碍、仅危险物净空不足的候选。
- `build/parkour-evidence/round7-underground-clearance.log` 完整地下回放仍失败：流式规划只完成前三跳，随后在半砖静止，12次换边后无路。这暴露“安全落点不等于保留后续可行性”；局部29帧成功不能冒充整关成功。正在先纠正将竖向净空包络误计为实际绕路的成本，再复验完整链。`round7-underground-clearance-cost.log` 是拼错已有碰撞辅助函数名导致的编译失败，修正后另存新日志，未覆盖失败记录。

### 空中恢复候选与地下梯子局部验收

- 用户后续允许少量岩浆擦伤，优先恢复受伤同步后的控制；原零全盒接触门槛已被替代。当前仅LAVA用原版浸入收缩盒拒绝，水仍全盒；独立oracle及snapshot边界回归通过。这不保证零火伤，日志仍保留全格接触/血量/燃烧。
- 空中恢复增加不再次起跳的7曲线并使用完整既有40ms预算。两个录制真实状态离线恢复通过，但probe9只有首次12帧恢复成功；落地前再次变速进入岩浆边缘，后续仍按陆地物理起跳导致失败。没有宣称受伤恢复或地下整关已修完。
- 地下北向梯子挂住已另行复现：普通Ascend漏查当前格梯子，接受向邻梯横向升高边，挂住5.293s且视角连续转动。补当前格/下方梯子朝向与真实背墙支撑校验，8项相关离线回归通过。
- PID25508隔离起点22.5/12/-1506.5实机27.016s到原终点；目标梯子先站到y20/onGround，再跳对面，无replan/fail。相同PID整关复测10.344s在前段岩浆失败，记录未覆盖。完整路线、来源、SHA、耗时口径见 `tools/parkour-underground-ladder-20260928.md`；该局部修复不关闭全面优化或第7批验收。
- 同PID25508三基准单关各一次兼容回归58.907/38.906/83.797s全部成功，无fail/replan；flow_blocked10/3/5次回退均保留。雪地开头同时有约11秒离线编译/检查，不能用这一轮宣称提速。完整路线见 `tools/parkour-underground-ladder-three-routes-20260928.md`。

### 当前地图通用候选缺口（未完成整批验收）

- `Getting Over it The Corrupted`前三场景已在PID48024实机落稳：低顶上跳1.140s、远处门洞1.750s、铁砧连续段21.750s，cache=false、创造非飞行、无药效；不是生存三基准。JAR SHA256 `b56d1cf114dd73526620bd925b97b64aac2784b1ec4f27e0d2f59c0fd185ce12`，详细根因/来源/路线见 `tools/parkour-raised-platform-20260928.md`及`tools/parkour-raised-platform-20260928-routes.md`。连续段静止累计10.390s，最长单次4.450s，未触发连续10s停测。flow/prefetch累计求解8.787/6.342s，分配5.307/3.867GB，仍是后续C15/F26优化依据。
- `其他测试_铁砧连续段`已原生保存并读回验证；保留用户把起点等待半径改成1的编辑。102层在PID32204实机6.187s到达并落稳，静止0.820s、无失败/重规划，随后发布`其他测试_102层连续跳`并验证原铁砧序列未变；JAR SHA256 `99e939bc33ccbdac1a639f4dbb5514812e2c77130669d2656cacaa3222989268`。见 `tools/parkour-platform-102-20260928.md`及路线附录。最终27项回归+5例完整生产图/轨迹套件通过；低顶升降候选、悬边站姿/支撑格及能力前沿绕过可重汇合捷径均有录制证据，不能由这些有限案例承诺全场景100%。创造非飞行局部验收不替代生存三基准，全面目标仍未完成。
- 岩浆支撑边缘恢复新增真实probe9二次纠正状态回归：液体跳升、实体支撑重叠、禁止新跳/掉到支撑面以下、退出岩浆后20tick落稳均检查；`AirborneRecoveryTest`2项通过。尚未重跑实机伤害探针，不把前三场景通过算作该恢复分支验收。
- 旋转塔补内部直角候选，生产完整离线97帧通过；PID18864部署后用户确认实机修复，未记录实机通关秒数。灵魂出窍修正本体输入/物理隔离与注入可见性；新可视化寻路选点按钮、命令面板及快捷键入口亦由用户实测确认。3项UI/注入检查、27项跑酷回归通过，详见`tools/parkour-spiral-freecam-20260928.md`。这些局部结果不关闭全面目标。
