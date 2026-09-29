# 第5批：预取交接、碰撞查询复用与搜索计量

最终三关同客户端通过，保留本批改动。基线为`c831c99`，上一批报告为`tools/parkour-round4-20260927.md`。所有秒数如无特别说明均为实机墙钟；离线搜索毫秒和模拟帧不能替代通关时间。全面目标尚未完成。

## 实现及依据

- 普通稳定落地SUCCESS前将水平速度归零，使中性交接tick与`prepareAhead`的零水平速度预测相符。原沼泽55在14/24的预测和实测末帧一致，交接后约.0103格漂移超过.005阈值，整条19段预取链丢弃。起点位置、速度、ground检查仍全部保留。
- 空水集合不再分配检测包围盒；beam内每个from节点的水/ground控制模式、整次搜索不变的高度/距离边界移出内层循环，删除未使用的距离/速度计算。
- 中心阻挡、直线阻挡、落地稳定接触查询复用既有`CollisionIndex`，保持原相交/支撑重叠判定；未加预算、缩候选或降低安全阈值。
- 四类轨迹任务记录提交、快照/准备、排队、求解墙钟、线程CPU、分配字节及结果；图搜索单独计量；JVM每20tick采样堆/GC。未知CPU/分配记-1，报告不冒充0。线程任务可能互相重叠，也可能与移动重叠，累计量不能直接相加成实机通关时间。

## 首次实验保留

`build/parkour-evidence/round5-live/swamp-55-1.json`：初版只有交接修复及仪表，PID42828，沼泽55成功85.547s，静止48.424s、无控制器24.427s，5次地面COLLISION_REJECTED。比上一批40.766s显著退化，不能删去或描述为提速。

第一个失败是(13,22,-3059)→(14,24,-3055)的cocoa跳跃：预测起点13.48564995999734/22.75/-3058.662564690525与旧版一致；实际起点偏差.00408格，小于.005阈值。旧实机在2s预算内搜得33帧；初版在20.651s返回空，求解2.040s、CPU1.969s、分配1230683296B，beam仅推进约十余模拟tick。后续选择另外4条慢失败边。不是已证明几何无路，也不是落点交接漂移拒绝了这次结果。

首次实验计量（搜索墙钟/CPU秒，分配MiB）：

|任务|数量|墙钟|CPU|分配|应用结果|
|---|---:|---:|---:|---:|---|
|graph|6|22.174|20.969|17326.4|独立图计算，未做结果标签|
|flow|15|13.176|12.484|9320.9|14 applied / 1 empty|
|single|7|3.210|3.016|1767.7|5 applied / 1 drift / 1 empty|
|prefetch|11|12.967|12.438|8868.7|7 applied / 3 empty / 1 drift|

所有任务排队总计不足.014s；GC205次、累计969ms，采样覆盖85.002s，最大观测已用堆569022776B。证据更支持减少计算与分配，而非增加并行worker或调整优先级；这些处置仍需结合最终三关计量。完整首次路线见`build/parkour-evidence/round5-live/routes.md`。

## 离线与部署

- 初版24项回归通过：`build/parkour-evidence/round5-offline.log`；编译与测试进程23.45s。
- 新增`CocoaRunupTest.recordedPrefetchStartReachesUpperPlatform()`，使用录制世界及精确预取起点，关闭缓存，断言有路、稳定落地，并重新模拟全部输入。
- `build/parkour-evidence/round5-cocoa-before.log`复现frames=0/searchMillis=2019，`java.lang.AssertionError: Recorded upper platform must be reachable with cache disabled`。
- 优化后`build/parkour-evidence/round5-cocoa-after.log`：33帧/searchMillis=1784；Cocoa1+VineExit3+SwampNether5+Additional6+SnowFlow7共22项通过，编译与测试20.94s。这是离线证据，尚非实机收益。
- `build/parkour-evidence/round5b-deploy.log`构建成功，但断开步骤返回`MCP call failed: mythos_run`，未停止旧进程。读回仍在世界且序列已结束；当时同一客户端另有nether-64/69/70序列活动。失败部署记录保留，不算关卡尝试。随后重试仅部署已有新jar。

## 最终实机及来源

| 关卡 | 第4批→本批秒 | 减少秒 | 静止秒：前→后 | 无控制器秒：前→后 | 本次失败/轨迹重规划 |
|---|---:|---:|---:|---:|---:|
| snow-10 | 61.157 → 58.500 | 2.657 | 12.786 → 10.006 | 3.252 → 4.703 | 0 / 0 |
| swamp-55 | 40.766 → 39.890 | .876 | 11.161 → 10.154 | 4.688 → 5.706 | 0 / 0 |
| swamp-60 | 85.593 → 83.266 | 2.327 | 18.767 → 17.838 | 11.508 → 10.805 | 0 / 0 |
| 合计 | 187.516 → 181.656 | 5.860（3.125%） | 42.714 → 37.998 | 19.448 → 21.214 | 0 / 0 |

- 基线完整提交`c831c990981efcc962b36f6ab0ae1cf53f72fb8c`。最终部署`build/parkour-evidence/round5b-deploy-retry.log`，同PID27660依次沼泽55、雪地、沼泽60，各一次；生存、cache=false、debug=true、原保存单目标序列。中途未换jar或重启。
- 实际jar：`E:/我的世界脚本/MythosTests/runs/1.12.2-inject-20260927-172506-7dee6a/MythosScript-v1.0.72-mc1.12.2.jar`，SHA256 `3729c283e58e1cfba8b0bc292afea16ca2c036a20f1e26b23ecf6a6a22bb0219`。两个结果目录`source.json`均为`3c67dc1d832a40c78df61a17447f9019f904c1c4b96f955ab4f764a76537dab6`；保存的生产diff统一换行后与提交前源码相同，实际jar及全部debug SHA重新校验通过。
- 三关均通过终点区域/传送和序列success验证、无超时；初始creative=false、所有状态样本alive且flying=false、前后缓存查询false。逐tick数量雪地1171、沼泽55为798、沼泽60为1665；状态采样最大间隔.375/.187/.157s。普通支撑交接检查分别Descend1、Ascend2、Descend2+Ascend1，全部通过。
- 上表是成功尝试的单次观察，不是稳定收益证明，也没有计入初版85.547s实验或第4批60首试失败。加入本批计量、JVM冷暖及加载时机都会影响墙钟；无控制器总量反而增加1.766s，不能说所有等待均下降。

## 路线及局部耗时归因

完整控制器路线、静止区间、回退事件及任务计量见`tools/parkour-round5-swamp55-routes-20260927.md`、`tools/parkour-round5-other-routes-20260927.md`；初版失败搜索路线见`tools/parkour-round5-first-routes-20260927.md`。这些交接点是实际执行记录，不是新增脚本中继。

- **雪地**：不同控制器边集合基本一致（79→80个含无控制器条目，仅多观察到90/19/-2067→91/19/-2067的.048s交接）。8/8→12/9耗时5.453→3.303s；77/19→78/19为2.103→.357s；44/14/-2062→44/14/-2063为1.156→.155s。局部退化仍在：24/8→27/9为1.651→2.249s，31/14→30/15为1.551→1.902s。受伤落地确认仍12条，没有省略安全等待。起点静止3.748s，25/8处1.650s，31/14处1.453s是剩余热点。
- **沼泽55**：前后50个不同控制器边（含无控制器）完全相同，仍通过可可豆13/22→14/24，没有绕开困难跳跃。该边2.599→2.701s，末梯子8/45→5/46为.751→.749s。关键14/24→15/25控制器5.305→3.100s、落点静止4.804→2.650s：19段链20.057s开始、24.314s求解结束，24.339s应用，未因中性交接tick漂移整链重算。该任务计算4.257s/CPU4.094s/分配4988320816B；仍然等了2.650s，不能称零等待。可可豆预取1.927s得到33帧并应用；起点静止反增至5.101s，抵消部分局部收益。
- **沼泽60**：前后76个不同控制器边（含无控制器）完全相同，仍经过双活塞、藤蔓、真实药效高塔及黏液弹射。110/46→111/46为.900→.200s，102/30→103/31为1.098→.504s；退化112/46→117/45为1.199→1.695s、43/20→46/17为.900→1.296s。没有轨迹失配重搜；本次活塞通过不等于第4批突变原因已解决。

## 搜索计量与下一步依据

| 关卡 | 排队累计秒 | 快照/准备累计秒 | graph墙钟/CPU秒 | prefetch墙钟/CPU秒 | 预取应用/漂移丢弃 | GC次数/累计ms |
|---|---:|---:|---:|---:|---:|---:|
| snow-10 | .166079 | .053671 | 3.074 / 2.750 | 9.071 / 8.703 | 8 / 5 | 86 / 917 |
| swamp-55 | .009192 | .069141 | 3.503 / 3.312 | 7.780 / 7.547 | 4 / 1 | 73 / 415 |
| swamp-60 | .004739 | .050321 | 15.596 / 15.141 | 2.452 / 2.297 | 7 / 2 | 135 / 742 |

- 最大观测已用堆分别599106576/506474816/708466544B；JVM采样覆盖56.990/38.994/81.998s，比关卡稍短。GC累计时间不是精确客户端停顿总量，线程CPU短任务可能出现计时粒度造成的0或略大于墙钟。
- 沼泽55预取累计分配7904.1MiB、沼泽60图搜索12795.3MiB，说明计算与临时对象仍值得优化；此处是分配量，不是同时驻留内存或泄漏证明。漂移丢弃仍雪地5次、55一次、60两次；应继续追踪具体状态差异及浪费，而非放宽起点校验。
- F26本轮不扩worker/改优先级：三关排队都不足.167s，不能解释数秒落点等待。F27本轮保留Java8u202/-Xmx800M，先消除已有分配热点；尚未进行独立JVM对照，不能把它记成完成。沼泽60图任务12个（包括失效/未应用任务），图结果标签未覆盖，不能把`unobserved`当失败。

## 证据索引与复现

| 记录 | 结果JSON SHA256 | debug SHA256 |
|---|---|---|
| `build/parkour-evidence/round5b-live/swamp-55-1.json` | `2df84b44dd1bab3abd024a4e7e0a909c33b83b3cc3e9d068e160cb2f56444b39` | `44e32ec6ecfe7ddca4c0b436ce15783be10050c49b9374249dc765f995557fef` |
| `build/parkour-evidence/round5b-remaining/snow-10-1.json` | `15bbe0c1435b60e478d34dc6009f65f83d2e8db30416dc7687f178efe3693ca6` | `7ddb94e12d6706e9c2b57791819a0c914fb54bae5ba1fbc7d5494f6fe4c8cc72` |
| `build/parkour-evidence/round5b-remaining/swamp-60-1.json` | `eb5e2747de47041bcc1fb74e985a9d1484c0a997ddde2ef08a993996da2613e0` | `c38dbe34bd70cefb7402e0a1bc217b8f5553428d124eb098a305c9a22e1c1970` |

初版jar路径为`E:/我的世界脚本/MythosTests/runs/1.12.2-inject-20260927-170539-edf0e4/MythosScript-v1.0.72-mc1.12.2.jar`，SHA256 `d45543c3b881e0fbbb16646c560ac1c6329381bbb2f4210d22f3dde89393bf1d`；对应`build/parkour-evidence/round5-live/source.json`保留最初生产diff。

- 离线：`python tools/parkour_offline.py CocoaRunupTest VineExitTest SwampNetherRegressionTest AdditionalParkourRegressionTest SnowFlowTest`。
- 实机先`python tools/parkour_benchmark.py --courses swamp-55 --output build/parkour-evidence/round5b-live --debug --sequence-prefix 优化测试_`，同PID再`--courses snow-10 swamp-60 --output build/parkour-evidence/round5b-remaining`，其余参数相同。
- 附录生成：`python tools/parkour_benchmark_report.py <结果目录> --markdown <附录路径>`；该工具同时运行指标自检查。支撑检查：`python tools/parkour_handoff_check.py <逐tick.jsonl>`。
- 本批提交可用`git log -1 -- tools/parkour-round5-20260927.md`定位。

## 未关闭事项

第4批沼泽60活塞首试掉落原因尚未解释；E24速度/缓慢到期模型缺口已登记。全面A1–F29目标保持进行中。
