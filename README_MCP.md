# MythosScript MCP 控制接口

安装包含此次改造的模组后，游戏客户端初始化完成时自动启动游戏侧 MCP 服务；stdio 桥接器可以在游戏未启动时先完成 MCP 初始化并列出工具：

- 地址：http://127.0.0.1:8765/mcp
- 传输：标准 Streamable HTTP；也提供 Python stdio 桥接。
- 鉴权：游戏目录下 config/我的世界脚本/mcp.token 自动生成的 Bearer Token。
- 协议：initialize、ping、tools/list、tools/call 和无响应通知。
- 所有游戏操作在 Minecraft 客户端线程执行。

从右上角“工具”菜单中，点击“支持作者/联系方式”下面的“MCP 控制 / 调试”打开独立标签页。页面可以开启/关闭服务、修改端口并立即应用、复制监听地址，查看调用总数及失败数。

点击左侧调用记录，在“参数”和“返回”之间切换；支持工具名/请求 ID 筛选、滚轮纵向滚动、Shift+滚轮横向滚动和复制完整文本。记录包含时间、请求 ID、工具名、完整参数、完整返回、耗时及成功/失败状态。历史仅保存在内存，最近最多 100 条，总预算约 16 MiB，超出时淘汰旧记录；单条超大记录保留完整内容。关闭接口会拒绝尚未执行的旧连接请求，已经启动的路径任务仍可从路径界面控制。

开关与端口保存到 config/我的世界脚本/mcp_server.json，与游戏配置方案独立。第一个启用 MCP 的客户端绑定该端口成为中心；后续客户端加入同一端口。用 `mythos_clients` 的 `players` 查看正在游戏的用户名，其它工具用参数 `player` 指定；只有一个在线用户名时不必选择。`pid` 仅在两个窗口同名时需要。新端口在本进程内被占用时保留旧监听服务。未保存设置时，JVM 参数 -Dmythosscript.mcp.port=8765、环境变量 MYTHOSSCRIPT_MCP_PORT 和 -Dmythosscript.mcp.disabled=true 可作为首次启动默认值；保存后以界面设置为准。

## AI 工具

| 工具 | 能力 |
| --- | --- |
| mythos_clients | 正在游戏的用户名列表 `players`，以及各客户端 PID/角色；可选 `player` 按用户名筛选 |
| mythos_discover | 当前配置方案、配置根目录、模块目录、格式约定；含 localPid 与客户端列表 |
| mythos_status | 玩家、附近实体、运行进度、变量预览、临时状态 |
| mythos_preflight | 执行前验证序列并检查当前世界与可选的 targetServer |
| mythos_snapshot | 玩家与朝向、完整物品栏、实体血量、GUI 元素和容器槽位、压缩三维方块快照 |
| mythos_gui | GUI 语义查看与控制：打开主界面/设置标签、选择标签、按路径或文本点击、输入/设置值、按键、滚动、保存和关闭 |
| mythos_events | 分组监听、实体生命周期、输入与交互、事件时间线、多组筛选、时间范围、游标增量与长轮询 |
| mythos_chat | 直接读取聊天消息，关键词/类型筛选、增量游标、格式与悬浮/点击组件 |
| mythos_notes | 工具菜单「服务器 Markdown 记事本」的查看、整篇写入、追加；按服务器分文件，打开中的界面草稿对 MCP 可见 |
| mythos_actions | 108 个动作的 ID、字段说明、选项、默认值、运行时代码中的参数 |
| mythos_templates | 内置/自定义动作模板的查询与完整动作 JSON；返回编写原则。GUI 必须先等标题再稳定 5-10 tick 后点击；传送必须 3 秒内到点确认，冷却/失败则整段重试 |
| mythos_config | 全部业务配置文件的读取、写入、JSON Pointer 修改、字段/文件删除 |
| mythos_modules | 43 个模块的查询、字段类型说明、保存及重新加载入口 |
| mythos_paths | 完整路径、步骤、动作的创建、读取、替换、插入、删除 |
| mythos_run | 执行已有路径或直接传入动作/路径；循环次数、起始位置、初始变量 |
| mythos_control | 暂停、恢复、停止前台/后台路径；stop_all 同时清理 临时杀戮状态 |
| mythos_kill_aura | 杀戮光环全部可写公开字段说明、临时参数覆盖、定时恢复 |
| mythos_packets | 抓包开关、分页读取发送/接收包、十六进制与解码文本、清空 |
| mythos_packet_trace | 将一个或多个抓包输入时间线事件与 C2S、S2C 或双向数据包按时间窗口/前后数量关联；支持键盘、鼠标、时间范围、HEX、解码和时间差 |
| mythos_validate | 路径与动作参数预检，返回步骤/动作位置和错误原因 |
| mythos_logs | 执行会话、完成结果、错误原因与详细日志 |
| mythos_wait | 在不阻塞 Minecraft 客户端线程的情况下等待指定执行会话完成 |
| mythos_navigation | 内置导航命令目录与完整用法；执行 goto/mine/build/farm/cancel 等命令 |

动作目录来自现有动作库；字段说明来自现有编辑器，`mythos_actions(type=...)` 还会返回可直接用于 `params` 的 `paramsSchema`。发送数据包使用 send_packet 动作，支持 FML channel、标准 packetId、hex 和 C2S/S2C。S2C 对应现有的客户端接收模拟。

## 接入 AI 客户端

原生 HTTP 客户端配置地址，并发送 Authorization: Bearer TOKEN。不要把实际 Token 提交到仓库。

兼容 stdio 的 MCP 客户端只需配置一次通用桥接器。桥接器会自动发现当前实例，不需要填写游戏目录、端口或 token 路径。即使 Minecraft 尚未启动，桥接器也会正常响应 `initialize` 和 `tools/list`，因此 MCP 客户端不会进入 `failed` 状态；此时调用游戏功能会返回 `GAME_NOT_RUNNING`，启动游戏后下一次调用会自动连接：

~~~json
{
  "mcpServers": {
    "mythosscript": {
      "command": "python",
      "args": [
        "E:/我的世界脚本/MythosScript/skills/mythosscript-mcp/scripts/client.py",
        "--auto-discover",
        "stdio"
      ]
    }
  }
}
~~~

桥接只依赖 Python 标准库。默认自动读取 `%LOCALAPPDATA%/MythosScript/mcp-endpoints.json`；这是模组自动维护的机器级发现记录，不需要用户编辑。也可以不修改 AI 客户端配置，直接调用辅助脚本：

自动桥接器会合并所有已发现的 MCP 端点。工具调用按 `pid` 优先路由；没有 `pid` 时，只有全局能唯一匹配的在线玩家才会自动选择。相同玩家名出现在不同服务器或多个单人客户端时，错误信息会列出 `pid`、玩家名和服务器地址，调用方应传入 `pid`。`mythos_clients` 返回每个客户端的 `server` 和 `pid`，其中 `singleplayer` 表示单人世界。

~~~powershell
python skills/mythosscript-mcp/scripts/client.py --auto-discover probe
python skills/mythosscript-mcp/scripts/client.py --auto-discover call mythos_clients
python skills/mythosscript-mcp/scripts/client.py --auto-discover --player Steve call mythos_discover
python skills/mythosscript-mcp/scripts/client.py --auto-discover --pid 12345 call mythos_discover
python skills/mythosscript-mcp/scripts/client.py --auto-discover --player all call mythos_status
~~~

## 不保存配置的参数直传

对 mythos_kill_aura 调用：

~~~json
{
  "operation": "start",
  "enabled": true,
  "durationTicks": 200,
  "fields": {
    "attackRange": 3.5,
    "targetHostile": true,
    "targetPlayers": false
  }
}
~~~

先使用 operation:describe 读取完整字段类型。200 tick 在 20 TPS 下约为 10 秒；0 表示直到停止。停止、到期、断线或重新加载杀戮配置时会恢复原字段快照。临时覆盖期间，原有 GUI/动作触发的杀戮配置保存被挂起，避免把临时值写到磁盘。

临时路径执行示例（mythos_run 参数）：

~~~json
{
  "loops": 3,
  "actions": [
    {"type": "system_message", "params": {"message": "AI 临时执行"}},
    {"type": "delay", "params": {"ticks": 20}}
  ]
}
~~~

传 sequence 可以直接执行含多个坐标步骤的路径；传 name 可以执行已有路径。支持 startStep、startAction、variables。索引从 0 开始，loops:-1 为无限循环。pos:null 表示不寻路、只执行动作。

临时路径不会进入保存列表，但其中某个动作本身可能保存设置或产生游戏效果，例如 toggle_fly。需要不改变杀戮配置时应使用专用临时接口，而不是普通开关动作。

## 持久配置与执行结果

mythos_config 的路径相对业务配置根目录。read 返回 hash，后续写操作可传 expectedHash 防止覆盖其他编辑。patch 支持 add、replace、remove、test；校验失败不写入，成功采用临时文件替换。文件写入与运行时应用明确分开：编辑后使用 mythos_modules reload 指定拥有该配置的模块。

### GUI 语义控制

`mythos_gui` 用于不依赖屏幕坐标的界面操作。先调用 `operation:inspect`，从 `elements` 中取得稳定的 `path`。每个元素还会返回 `controlType`、当前 `value`、`enabled`、`editable`、支持的 `actions` 和选项 `choices`；`visible:false` 表示控件在当前滚动视口外，但仍可用语义路径定位。

~~~json
{"operation":"open","target":"toggle_kill_aura"}
~~~

现代主界面的设置页可以用路由或从 `modern.availableTabCommands` 取得的命令直接打开；也可以把完整的设置控件路径传给 `open`。随后可按元素路径点击、输入或直接设置值，最后用 `operation:inspect` 检查结果：

~~~json
{"operation":"click","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/field/targetHostile","button":"LEFT"}
{"operation":"input","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/field/attackRange/input","text":"4.2"}
{"operation":"set","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/field/attackMode","value":"PACKET"}
{"operation":"click","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/save"}
~~~

`input` 和 `set` 默认修改页面草稿，保存由页面的 Save 操作完成；`set` 对开关、选项和数值输入会按控件类型处理。视口外的现代表单控件在点击前会自动滚入视口。`key` 传 `keyCode`/`character` 或 `ESCAPE`、`ENTER`、方向键等名称时注入 GUI 按键；不带 GUI 按键参数时仍保留原来的游戏动作键模拟。旧式自绘页和其它自定义 GUI 会自动发布已布局的矩形与输入框，页面有专用语义适配器时优先使用适配器。

如果只想直接修改配置文件，继续使用 `mythos_config` 或 `mythos_modules`。标准容器界面仍可通过 `mythos_snapshot` 的槽位信息和 `mythos_run` 的动作控制。

mythos_paths 支持 operation:put/get/list/delete、step_put/step_delete、action_put/action_delete。insert:true 表示插入，默认替换。路径保存使用原子写入，写盘失败不会提交新的运行时路径列表。

运行前可调用 `mythos_preflight` 检查序列语法、当前是否在世界中以及可选的 `targetServer`。`mythos_run` 返回 `executionSessionId`、`eventSessionId` 和 `eventAfterId`；随后可用 `mythos_wait` 等待完成，并用 `mythos_logs` 的 `includeEvents:true` 读取结构化步骤事件。不能把 `accepted:true` 当作“已完成”。超时之前仍在队列中的请求会取消；已经开始的操作超时会提示先检查状态再重试。stop_all 恢复临时杀戮之前的状态，原来已经开启的持久杀戮仍可能开启。

`mythos_paths` 的序列可选 `targetServer`，例如 `t32.sjcmc.cn:14232`；为空时保留旧版跨服务器行为。主状态和快照会返回当前 `server`。GUI 快照附带 `fingerprint`，背包快照附带 `inventoryFingerprint`；容器点击事件有 `interactionId`，随后发生的 GUI/背包变化可能带 `correlation`。这些是客户端证据和时间关联，不是服务端事务确认。

## 输入时间线与数据包关联

`mythos_packet_trace` 直接读取抓包工作台的输入时间线，并为每条匹配输入返回附近数据包。`inputs` 可以传多个选择器；`types` 使用 `key`/`mouse`，`keys` 是键名（例如 `F`），`keyCodes` 是键码，`buttons` 支持 `LEFT`/`RIGHT`/`MIDDLE` 或数字，`fromMs`/`toMs` 指定输入时间范围，`eventIds` 可以精确选取之前返回的时间线事件。没有 `inputs` 时，可以把这些筛选字段放在顶层。

~~~json
{
  "inputs": [
    {"types":["key"],"keys":["F"],"fromMs":1789007786000,"toMs":1789007787000},
    {"types":["mouse"],"buttons":["LEFT"],"fromMs":1789007786000,"toMs":1789007787000}
  ],
  "directions": ["C2S", "S2C"],
  "windowMs": 200,
  "before": 5,
  "after": 10,
  "packetLimit": 100,
  "maxTotalPackets": 1000
}
~~~

`directions:["C2S","S2C"]` 表示双向；`windowMs` 返回输入前后指定毫秒内的包，`before`/`after` 额外加入最近的前后 N 个包，三者结果合并去重。返回值中的 `deltaMs`、`absDeltaMs`、`relation` 和 `matchReasons` 用于判断包发生在输入之前、之后还是覆盖输入时刻；`occurrenceCount` 表示抓包缓冲对相同连续包的聚合次数。`inputTruncated`、`packetTruncated` 为 true 时，应缩小时间范围或继续使用更精确的 `eventIds`/筛选条件。单次最多返回 400 条输入、每条 500 个包、总计 5000 个包。

## 专用 Skill

[skills/mythosscript-mcp/SKILL.md](skills/mythosscript-mcp/SKILL.md) 是可随仓库分发的技能。
[操作示例](skills/mythosscript-mcp/references/operations.md) 提供配置修改、路径编辑、循环执行、数据包和导航的具体参数。
[稳健序列编写](skills/mythosscript-mcp/references/authoring.md) 要求等待游戏状态再点击/传送，禁止盲等延迟；动作用 mythos_templates 作骨架，用 mythos_snapshot/mythos_chat 替换真实标题、槽位和冷却原文。
将整个 mythosscript-mcp 文件夹安装到 AI 客户端的技能目录。本次开发也将其安装到本机 Codex 技能目录。

## 开发与验证

~~~powershell
python tools/generate_mcp_catalog.py
./gradlew.bat test reobfJar
python C:/Users/33385/.codex/skills/.system/skill-creator/scripts/quick_validate.py skills/mythosscript-mcp
~~~

增加动作或配置加载入口后，重新生成并提交 src/main/resources/mcp 下的目录。混淆构建保留目录中的模块入口。

测试覆盖协议、HTTP 鉴权、JSON Patch 原子性、冲突检测、路径越界、字段类型与目录一致性。另在独立 Forge 1.12.2 世界验证过实际 HTTP/stdio 官方 SDK 接入、动作和模块查询、杀戮临时恢复与文件不变、临时多轮执行、保存路径 CRUD和导航目录。Windows 未授予创建符号链接权限时，对应单测会跳过；实际文件访问仍检查链接和解析后的目录边界。

控制接口覆盖现有脚本能力，不会赋予游戏服务端未授予的权限；动作执行仍使用原来的功能引擎与条件校验。

## 直接读取聊天消息

使用 mythos_chat，无需读取客户端日志。默认返回最近 50 条服务器接收消息，按消息编号升序排列：

~~~json
{"limit":50,"stream":"received"}
~~~

执行命令前记录 connectionId 和 nextAfterId；执行后把它们传回，只检查新的服务器回复：

~~~json
{"afterId":123,"connectionId":"上次返回的连接标识","query":"成功","limit":50}
~~~

返回 messages（id、timestamp、stream、type、text、chatLineId）、nextAfterId、hasMore、connected、server、connectionChanged 和 cursorExpired。hasMore=true 时继续分页。连接变化会清空上一连接的消息；游标过期会明确报告。修改筛选条件时重新从无 afterId 的查询开始。

stream=received 记录服务器事件，发生在本地过滤/防刷屏之前，同文重复消息不会合并，类型为 CHAT、SYSTEM 或 GAME_INFO（动作栏）。stream=displayed 记录现有聊天栏显示事件，包含本地模组提示及防刷屏后的文字，类型为 DISPLAYED。stream=all 同时返回两路记录，因此同一消息可能出现两次。displayed 是显示事件历史，不是当前屏幕行位置的快照。

includeFormatted=true 返回带颜色格式文本；includeComponent=true 返回完整聊天组件 JSON，包含可用的悬浮和点击数据。记录只保存在内存，最多 1000 个事件、约 8 MiB，淘汰旧记录时可通过 cursorExpired 识别遗漏；单条超大消息保留完整内容。断开后保留末次消息，但 connected=false。模组加载前的聊天不会从日志自动补录，也不会收集输入框或发送命令历史。


## 游戏观测与事件时间线

新增 `mythos_snapshot` 和 `mythos_events`，支持 session、player、entities、inventory、interaction、input、gui、world、chat 分组。先用 `{"operation":"groups"}` 查看分组及能力，再以 `watch` 创建命名监听。`read` 可以同时选取多个 groups/entityIds/watchIds，指定 fromMs/toMs 时间范围，并用 afterId/sessionId 增量读取。watch 的 filter 支持实体 ID、事件类型、文本和绝对坐标范围；显式实体 ID 监听会跟踪范围之外仍已加载的目标。换世界、维度或连接后需重新建立监听。

默认记录玩家周围 **10×10×10 个方块**。快照以玩家所在方块为原点，偏移 -5..4，返回完整方块状态调色板及互不重叠的长方体，两个对角坐标均包含端点。空气、未加载和世界边界分开标记；实体使用同一原点的相对坐标；玩家附带 yaw/pitch 和 forward 朝向向量。size 可调整，每轴最多 32。这里的长方体是信息编码，不会修改或填充游戏方块。

事件包含编号、毫秒时间戳、客户端 tick、会话 ID、分组、类型和事件数据。物品栏记录槽位 before/after；交互组记录使用物品、攻击、切换快捷栏、容器点击意图及服务器拾取回包；GUI 组复用 GUI 识别管理的元素扫描器，提供元素路径、文本、矩形、按钮 ID、容器 windowId/槽位和物品数据。输入记录游戏/GUI 按键代码及鼠标事件，不采集输入字符。健康与背包每 tick 检查，空间/GUI 默认每 5 tick 采样，不能保证捕获采样间隔内全部瞬态变化。

实体出现、离开观察范围、卸载、血量变化和死亡证据分别记录。远程服务器死亡回包只能确认死亡，不能确认击杀者；单人集成服务器额外记录死亡事件的伤害来源、攻击者 UUID、是否本地玩家及攻击者手持物。未提供的击杀归属不会推断为成功。

读取可以传 `waitMs:25000` 等待下一批事件，不占用游戏主线程。最多两个并行等待，其余工作线程仍处理控制调用。Python 客户端新增 `watch --arguments-file filters.json --duration 60`，持续打印逐行 JSON 事件和游标检查点。HTTP 使用 MCP 长轮询响应，CLI 提供连续事件流，不是 SSE 订阅。完整用法见 [观测接口参考](skills/mythosscript-mcp/references/observations.md)。

调试页点击“工具调用 / 事件时间线”切换视图。事件筛选例子：`g=entities,gui id=21987 from=1788810000000 to=1788810060000`。多组/多实体用逗号分隔；显示最近 500 条匹配事件，可查看和复制完整内容，更长历史通过 MCP 分页读取。

时间线为内存环形缓存，最多 12,000 条、约 16 MiB；`cursorExpired` 和 `capture_gap` 明确提示历史缺失。关闭 MCP 停止记录；不重建启用前、禁用分组期间或已淘汰的事件。监听配置属于本次运行，不改写玩法配置。节点图仍不提供接口。
