# HeadlessMC 内存优化

适用于保留 Forge 1.12.2、MythosScript 及内置寻路的 HeadlessMC 实例。优化不替换动作执行器，不关闭事件捕获，不丢弃服务器仍加载的逻辑区块。

## 代码变化

- MCP 事件历史以 UTF-8 载荷保存，较大的事件使用无损 DEFLATE 压缩。保留原有事件数量上限、逻辑字节预算、过滤条件、游标和过期语义。分页只展开当前页，按组、类型和时间筛选时先检查元数据。`retainedBytes` 仍是兼容旧版本的 UTF-16 文本预算；新增 `retainedPayloadBytes` 是存储载荷字节数，不含对象头和索引。
- 寻路缓存去掉位图顶部未使用的空气空间，高度表由 256 个 int 改为 256 个无符号 byte。方块分类、表面信息、特殊方块和磁盘序列化格式不变。高度 128–255 按无符号值读取。
- `-Dmythosscript.headless.lowMemory=true` 让区块渲染器从创建时就只分配一套共享工作缓冲；另有原版同步工作器自己的缓冲。每层以及 Tessellator 缓冲的初始容量最多 64 KiB，沿用原版自动扩容。纹理上传缓冲由 16 MiB 减至 256 KiB，并同步缩小每批上传的行数。切换世界时不会先分配大池再释放。保留世界数据、射线检测、GUI 状态和 tick；未设置此参数时使用原版分配参数。
- `HeadlessInputBridge` 在检测到 HeadlessMC 时注册键盘重定向，让 `getEventKey`、`getEventCharacter`、`isKeyDown` 等读取本实例的模拟输入；同时提供键名映射。普通客户端不会安装该桥接。`ModUtils` 也在键名查询失败时通过 `KEY_*` 常量回退。
- MCP 连接另一服务器之前关闭已有世界的网络连接，避免旧连接继续处理包。

## 测试实例启动设置

启动器和游戏是两个进程，必须分别计量和配置。测试启动器使用 `-Xms16m -Xmx128m -XX:+UseSerialGC`，并给 `launch` 添加 `-quit -keep`：创建游戏后退出，保留游戏仍需读取的临时运行库。后续由 MCP 控制游戏。保留的运行库占磁盘，不能在对应游戏进程运行时清除。

OpenJ9 Java 8 的低内存配置组合（不能直接用于 HotSpot）：

```text
-Xms32m -Xmx256m -Xmn16m
-Xminf0.1 -Xmaxf0.2 -Xcodecachetotal32m
-Xgcpolicy:gencon -Xtune:virtualized -Xsoftrefthreshold0
-XX:ActiveProcessorCount=1 -Xshareclasses:none
-Dio.netty.allocator.numDirectArenas=1
-Dio.netty.allocator.numHeapArenas=1
-Dio.netty.allocator.maxOrder=8
-Dmythosscript.headless.lowMemory=true
```

对比用 HotSpot Java 8 配置为 `-Xms32m -Xmx256m -XX:+UseSerialGC -XX:-TieredCompilation -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:ActiveProcessorCount=1 -XX:SoftRefLRUPolicyMSPerMB=0`，保留相同的 `-D` 参数。不同 JVM 必须同时比较工作集和私有提交，不能把较低的工作集等同于较低的提交。更积极的回收和较小的 JIT/线程池预算会影响吞吐，需要核对 GC 时间、代码缓存余量和动作超时。堆上限不是整个进程的内存上限；没有为直接内存设置额外硬上限。

隔离实例安装了 FoamFix 0.10.15（Modrinth 项目 `jupr7Bf5`，版本 `oEGIQQnQ`），下载后校验发布元数据中的 SHA512。使用默认开启的方块状态紧凑存储、模型去重/加载缓存清理和 LaunchWrapper 资源缓存弱引用；FoamFix 不是 MythosScript 的必需依赖。

HeadlessMC 2.10.0 的 `InstructionUtil.getWrapper` 将 `char` 错写成 `java.lang.Char`，会在模拟按键触发 `getEventCharacter` 时崩溃。测试目录中的 `patch-headlessmc.py` 对原启动器及其内嵌 LWJGL 库做精确常量池修复，生成 `headlessmc-launcher-2.10.0-mythos.jar`，保留原 JAR。`VerifyHeadlessChar.java` 验证包装类型及默认字符重定向。启动脚本使用修复后的启动器；单独更新 Mod 不会修复旧启动器生成的错误字节码。

测试实例另外使用 HeadlessMC 的 dummy assets、320×240 窗口参数、视距 2 和 50 ms 的 LWJGL 更新等待。dummy assets 的磁盘体积变化不能直接算成节省的进程内存。无头模式下的像素识别、截图等视觉行为不属于本次可验证的完整非视觉动作兼容性。

## 验证与边界

### 2026-09-18 实测结果

测试实例位于 `E:\我的世界脚本\HeadlessMC-test`，通过 `start-headless.ps1` 启动。优先尝试的 `127.0.0.1:25565` 拒绝连接，备用 `127.0.0.1:62782` 连接成功，玩家为 `HmcTest`。以下单位均为 MiB（1,048,576 字节），仅统计无头游戏进程；启动器已经退出，不包含用户自己的客户端或服务器。

| 配置/采样 | 工作集 | 私有提交 | 说明 |
| --- | --- | --- | --- |
| 优化前，1024 MiB 堆及真实资源 | 历史峰值约 1342 | 约 1542 | 先前运行“测试”狩猎时记录，非严格相同世界状态的 A/B 测试 |
| 最终配置，PID 29560 | 311.42–342.06，均值 325.98 | 371.84–399.31，均值 387.18 | 36 次采样，间隔 5 秒，共约 175 秒；装载了 JMX 诊断代理 |
| 最终配置重新启动，PID 17156 | 365.75–383.86，均值 373.73 | 421.71–439.90，均值 433.71 | 24 次采样，间隔 5 秒，共约 115 秒；没有附加 JMX 代理 |

最后一轮进程截至采样结束的工作集峰值为 385.00 MiB。两轮采用同一配置，但运行位置、区块、事件历史和 GC 时机不同；不能由这两轮差异推断诊断代理的开销。采样没有主动触发完整 GC 或裁剪工作集，也没有将计算卸载给另一个代理进程。因此当前应按数百 MiB 的整进程占用评估，不能把 `-Xmx256m` 理解为总占用 256 MiB，更不能承诺低于 100 MiB。

原始样本在测试目录的 `evidence/memory-29560-20260918-053729.jsonl` 和 `evidence/memory-17156-20260918-054552.jsonl`。正常复测使用 `measure-memory.ps1 -GameProcessId <PID> -SampleCount 24 -IntervalSeconds 5`；诊断工具 `MemoryProbe` 会向目标 JVM 加载管理代理，不用于最终正常占用采样。

定位到的具体节省包括：直接缓冲从先前约 243 MiB 降至诊断轮末约 1.65 MiB；饱和事件日志的逻辑文本预算约 16 MiB，对应压缩载荷约 1.78 MiB（不含索引和对象头）；寻路缓存主要位图所在的 long 数组从先前约 27.5 MiB 降至约 3 MiB。不同测量口径不能相加作为总节省量。

最终构建通过 59 个测试套件、214 个测试，失败和错误均为 0。安装到测试实例的 Mod JAR 与构建产物 SHA256 一致：`C41D9E451C1464E11CB093AE36446CB47627CF43C775C6F9464B1A2F3DB4E859`。实时验证包括 MCP 连接、序列预检、路点到达、跳跃、SPACE 模拟输入（玩家高度从 4 上升至约 5.166 后落地）、持续狩猎及事件读取；此前诊断轮还记录了拾取确认和容器槽位更新。最后一轮两个路点均到达、无步骤重试，状态为 `Hunting...`，并记录攻击尝试事件；攻击尝试本身不等于击杀确认。

“测试”的狩猎动作没有强制结束条件，因此保持运行是预期行为，不能报告整个序列已经完成。最后一轮日志未发现 OOM、未捕获线程异常或缺失类错误。该短时验证没有逐一覆盖所有动作，也没有证明任意地图、长时间挂机或多任务并行时的内存上界。

运行 `gradlew.bat build`。回归测试覆盖事件压缩内容、中文和 Unicode、过滤分页、超时唤醒、游标过期、不可变快照，以及低矮地形、高度 128–255 和缓存字节往返。

运行时需验证：进入世界、走到路点、执行动作、持续寻路/战斗、通过 MCP 读取过滤事件和状态。记录整个游戏进程和启动器的工作集、私有提交，以及 JVM 堆、非堆和直接缓冲。额外观察 GC 时间、断线、动作超时及 OOM。不能以强制 GC 后或被系统换页后的工作集作为正常占用结论。

“所有动作类型仍保留”不代表已在服务器上逐项实测所有动作。不同地图、资源包、模组和并发脚本需要重新测试；短时路线测试不能证明任意负载下的绝对最低内存。

未主动卸载服务器仍在使用的逻辑区块：服务器不保证在客户端擅自删除后重新发送完整内容，可能导致碰撞、寻路和交互失真。远处寻路缓存本身已有保存后回收机制；本次通过紧凑表示节省空间，不缩短寻路距离或删减缓存信息。
