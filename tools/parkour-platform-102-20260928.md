# 102层连续跳修复（2026-09-28）

用户现场：`Getting Over it The Corrupted`，从`(11.5,102,-118)`导航到方块`(11,102,-149)`，目标中心`(11.5,102,-148.5)`。全整数goto按目标方块处理，非整数按精确站姿处理。

## 录制与根因

`tools/parkour-platform-102-plan.json`只读捕获两片，共18×20×48格，全部已加载，261个去重真实碰撞盒。`src/test/resources/parkour/platform-102/platform-102.zip` SHA256 `e8dea12eb35fc79dea0c508ad51c2e76f4e813ba414e3485f56b5344e562c25a`。录制人物站地、非飞行、无药效；套件显式使用用户起点。

1. `103/-129 → 104/-133`升高一格，目标头顶两格高。固定抛物线碰顶，`lowCeilingCorridor`又以0.6格步高误拒；真实物理16帧可落稳。改为实际跳高能力，整个人物盒净空检查保留。
2. `104/-133 → 103/-138`下降一格，两端都有低顶。整段按较高Y检查会撞落点屋顶；只在终点竖降也无法通过。`turnRoute`补先离开起台、在中间空隙下降、再进入落点的完整净空候选。真实物理30帧可落稳；候选不等于可执行，仍须逐tick验证。
3. 沼泽60回归暴露能力前沿的旧缺陷：增强图从`37/22/-3290`走高处捷径，随后`49/16/-3290`重新汇入普通能力可达图，一直到`98/24/-3291`仍可普通到达。原实现却在第一个捷径缺口停在`37/20/-3290`。改为忽略可绕行的中间缺口，从增强路径最后一个普通可达节点之后寻找能力前沿，仍只返回原能力图的前驱路径。`capability-route-diagnostic.log`完整记录交集，40.008秒为诊断CPU墙钟，不是实机耗时。
4. 抬高低顶路线的高度上限后，额外核验起点上升柱，避免增强跳高直接越过源平台屋顶；这项净空修正本身未解决前沿错误，失败日志保留。

没有硬编码本关中继、改人物尺寸或放宽碰撞；全部输入缓存关闭。

## 离线证据

目录`build/parkour-evidence/raised-platform-20260928/`。

- `platform-102-before.log`：完整规划失败，仅返回`11/103/-129`能力前沿，不能到达终点。
- `platform-102-diagnostic-2.log`：升高一格实际16帧可行，图仍误拒。第一次诊断因GoalBlock构造参数类型写错未编译，已修正，原日志保留。
- `platform-102-after.log`、`platform-102-complete.log`：只修上跳限制/尝试扩大下降距离仍不足；后者距离调整已撤回。
- `platform-102-edges.log`：定位下降时落点低顶的第二缺口，局部真实物理30帧通过。
- `platform-102-descent.log`和`platform-102-descent-results.json`：生产A*、movement组装/成本/实际站姿、连续轨迹和20tick落稳通过；完整102帧，无拒绝重搜。图搜索322.479ms，总离线CPU墙钟5798.8145ms，均不是实机通关耗时。
- `platform-102-regression.log`：27项中26通过、能力前沿1失败；`platform-102-ascent-column.log`补上升柱后前沿仍失败，促成上述交集诊断。不能把这些记录计作整套通过。
- `platform-102-frontier-regression.log`：修复前沿选点后27项全部通过；前沿恢复`103/26/-3290`，阻挡节点`102/30/-3290`，含未加载推进、无新能力不能原地挪动、真实增强能力完整规划等检查。
- `platform-102-final-suite.log`及同名`-results.json`：最终候选完整102帧通过，无拒绝重搜；图402.3791ms、总5883.184399ms。
- `raised-final-suite.log`及同名`-results.json`：前三场景的4例全部通过，14/15/28/134帧；铁砧连续段总离线37312.754601ms，仍有候选搜索耗时，不能称已完成性能优化。
- `platform-102-build.log`：Gradle `reobfJar --offline --console=plain --no-daemon --max-workers=2`成功，53秒；候选JAR SHA256 `99e939bc33ccbdac1a639f4dbb5514812e2c77130669d2656cacaa3222989268`。构建成功不代替实机验收。

离线路线自动选择：`11/102/-118 → 11/103/-122 → 11/102/-126 → 11/103/-129 → 11/104/-133 → 11/103/-138 → 11/103/-142 → 11/103/-144 → 11/102/-149`。各段帧数12/14/10/10/19/14/6/17。

## 实机及保存序列

用户选择立即部署后，使用`tools/parkour_restart.py --world 'Getting Over it The Corrupted'`保存退出并只重启指定客户端，PID48024→32204，42.7秒确认进入原地图。实际运行JAR为`E:/我的世界脚本/MythosTests/runs/1.12.2-inject-20260928-065933-e4ec00/MythosScript-v1.0.72-mc1.12.2.jar`，SHA256与上述构建一致。

命令：`python tools/parkour_platform_probe.py --start 11.5 102 -118 --goal 11.5 102 -148.5 --output build/parkour-evidence/raised-platform-20260928/live-platform-102`。

**实机6.187秒到达，随后落稳1秒确认通过**；额外落稳观察不计入到达时间。缓存关闭、创造模式非飞行、无药效，所有样本存活；没有执行失败、轨迹重规划或10秒停滞中止。这是局部场景实机墙钟，不是生存三关基准，也不是离线CPU耗时。

- 实际自动路线与上述八段一致，102帧控制全部执行，6次连续交接；完整逐段耗时见[路线附录](parkour-platform-102-20260928-routes.md)。
- 累计位置静止0.820秒，均在起点；无movement 0.654秒、MovementParkour 5.493秒。静止与控制器持续时间可重叠，不相加归因CPU。
- graph/flow/prefetch各1个作业，计算墙钟0.339/0.443/0.314秒，线程CPU0.281/0.422/0.281秒；分配117,178,032/363,032,064/228,143,600字节。flow和prefetch均应用，图结果归属未观测不等于失败。
- `live-platform-102/source.json`记录基线`bbd27d2`、完整源码diff和实际JAR；与实测后源码逐字核对（仅统一换行）。`result.json`内保存全部samples，另存原始debug文件；debug SHA256 `d5afbdab28abbba587413cb12640481a63b9f9a237a19bc027044dfb1f88ef94`。`analysis-input.json`/`analysis.json`为派生统计，起始墙钟1790550075054ms。

已原生发布并读回验证`其他测试_102层连续跳`，定义在`tools/parkour-platform-102-sequence.json`，归档为`build/parkour-evidence/raised-platform-20260928/platform-102-published.json`。8个动作，起点等待半径1、超时跳过后4项；单goto到终点方块，终点确认半径0.2。保存的序列未另行执行，实机使用上述相同起终点探针验证。原铁砧序列完整读回未变。

本批局部提交只收录几何候选、支撑格、前沿修复及相应回归/报告；部署包同时带有未提交的受伤恢复候选，其完整diff已留档。本次无伤害场景通过不能作为地下岩浆恢复验收，全面目标和生存三基准复验仍未完成。
