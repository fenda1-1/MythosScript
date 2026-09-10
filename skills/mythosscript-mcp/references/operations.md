# Connection and invocation

Install the rebuilt mod and launch the game. The mod publishes its local endpoint and generated token path automatically for the Codex bridge. This project uses http://127.0.0.1:8765/mcp by default. The first client binds that port; later clients join it instead of taking another port. Target by in-world username with `player` (see below). Use the top-right Tools menu, MCP control / debug tab to enable/disable the listener, change its port and inspect full call parameters/results. Saved values in mcp_server.json override JVM/environment bootstrap defaults.

Native Streamable HTTP clients send Authorization: Bearer TOKEN. For stdio clients, configure Python once to launch scripts/client.py --auto-discover stdio. The mod publishes the endpoint and token file to a machine-local discovery record automatically; no game directory path is required in the AI client configuration. No MCP SDK installation is required by the bridge.

One-shot invocation:
~~~text
python client.py --auto-discover probe
python client.py --auto-discover call mythos_clients
python client.py --auto-discover call mythos_clients --arguments "{\"player\":\"Steve\"}"
python client.py --auto-discover --player Steve call mythos_discover
python client.py --auto-discover --pid 12345 call mythos_discover
python client.py --auto-discover call mythos_run --arguments-file run.json
~~~
All examples below are tool arguments, not raw JSON-RPC envelopes. For raw HTTP, use method tools/call with params {name: TOOL_NAME, arguments: ARGUMENTS} after initialize.

# Persistent configuration and GUI policy

Persistent settings use a silent JSON-first workflow by default. If the user has not explicitly requested interface interaction, preview, or manual editing, do not open or inspect a GUI merely to change a setting. Read the file with `mythos_config`, apply one or more JSON Pointer operations with `patch` (or write the complete preserved value with `write`), and pass the read `expectedHash`:

~~~json
{"operation":"read","path":"profiles/Default/gui_themes.json"}
~~~

~~~json
{"operation":"patch","path":"profiles/Default/gui_themes.json","expectedHash":"HASH_FROM_READ","patch":[{"op":"replace","path":"/activeIndex","value":10},{"op":"replace","path":"/profiles/0/name","value":"新名称"}]}
~~~

After a persistent write, reload the owning module through `mythos_modules` and read the file or runtime state back. Preserve fields that are not being changed, including unknown fields and persistent IDs. `mythos_gui` is a compatibility and preview path for explicit GUI requests only.

# Choose a running player

Tool mythos_clients returns `players` (unique in-world usernames) and `clients` (pid, player, inWorld, role). Optional `player` is a case-insensitive substring filter.

~~~json
{}
~~~

~~~json
{"player":"Steve"}
~~~

Every other tool accepts the same `player` field (and `pid`). Rules:

- The user already named a username → pass `player` with that name. Do not ask.
- `players` has exactly one entry → use that client. Do not ask.
- Several in-world usernames and the user did not specify → list `players` and ask. Do not guess.
- `player="all"` or `pid=-1` broadcasts. Use `pid` only when two clients share one username.

~~~json
{"player":"Steve","operation":"describe"}
~~~

# Temporary aura

Tool mythos_kill_aura:
~~~json
{"operation":"describe"}
~~~
Then use the returned field names and types:
~~~json
{"operation":"start","enabled":true,"durationTicks":200,"fields":{"attackRange":3.5,"targetHostile":true,"targetPlayers":false}}
~~~
200 client ticks is approximately 10 seconds at 20 ticks/s. Zero means until stopped/disconnected/replaced. Omitted fields inherit the pre-lease state; the entire snapshot is restored afterward.
~~~json
{"operation":"stop"}
~~~

# Action templates

Tool mythos_templates lists built-in and custom recipes. Call `{}` first and read `principles`. Filter with query or category. Pass id to get the action JSON.

~~~json
{"query":"传送"}
~~~

~~~json
{"id":"dungeon_teleport_cooldown_area_retry"}
~~~

~~~json
{"category":"背包/容器","includeActions":true}
~~~

Templates are starting recipes. Observe live GUI titles, slot text, cooldown chat and coordinates, then replace placeholders. Never convert a wait/retry skeleton into a blind delay click.

# Direct GUI control

Use `mythos_gui` for the live script interface. Start with `inspect`; use the
returned element `path` instead of calculating coordinates. Elements expose
`controlType`, `value`, `enabled`, `editable`, `actions`, `choices` and
`visible`. Form controls can be changed in the draft with `set` and persisted
with the page's Save control. A path outside the current form viewport is
revealed automatically before a semantic click.

~~~json
{"operation":"inspect"}
{"operation":"open","target":"toggle_kill_aura"}
{"operation":"set","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/field/attackMode","value":"PACKET"}
{"operation":"set","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/field/attackRange","value":4.2}
{"operation":"click","target":"screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/save"}
~~~

`input` is useful when the value is intentionally a text draft (use
`append:true` to append). `set` handles booleans, choices and text fields and
returns a fresh inspection. For GUI keyboard input, pass `key` as `ESCAPE`,
`ENTER`, `TAB`, an arrow name, or provide `keyCode` and `character`. A `key`
without those GUI-key forms retains the legacy game action-key behavior.

# Paths, steps and actions

Tool mythos_paths, operation put creates/replaces a complete path:
~~~json
{"operation":"put","sequence":{"name":"AI巡逻","category":"AI","loopDelayTicks":20,"steps":[{"pos":[10,64,20],"note":"第一站","actions":[{"type":"system_message","params":{"message":"已到达第一站"}}]},{"pos":null,"actions":[{"type":"delay","params":{"ticks":20}}]}]}}
~~~
Read with {operation:"get",name:"AI巡逻"}. Update a step using step_put and stepIndex; use insert:true to insert instead of replacing. Delete with step_delete. For actions use action_put/action_delete, stepIndex and actionIndex. All indices are zero based.
~~~json
{"operation":"action_put","name":"AI巡逻","stepIndex":1,"actionIndex":1,"insert":true,"action":{"type":"system_message","params":{"message":"本轮结束"}}}
~~~
Tool mythos_run:
~~~json
{"name":"AI巡逻","loops":3}
~~~
Or run without saving a path:
~~~json
{"actions":[{"type":"system_message","params":{"message":"临时动作"}},{"type":"delay","params":{"ticks":10}}],"loops":2}
~~~
Use sequence instead of actions for inline multi-step paths. Optional variables, startStep and startAction initialize the run. A current execution requires replace:true or an explicit stop.

`mythos_run` returns `executionSessionId`, `eventSessionId`, and `eventAfterId`. Wait for the execution result without blocking the game thread:

~~~json
{"sessionId":"EXECUTION_SESSION_ID","waitMs":25000,"includeEvents":true}
~~~

Call this with `mythos_wait`; `finished:false` means the bounded wait expired and the session is still running. `mythos_preflight` can validate a saved/inline sequence and enforce its optional `targetServer` before `mythos_run`.

GUI clicks must wait for the title, settle 5-10 ticks, then click. Example (replace title/slot text from mythos_snapshot):

~~~json
{"actions":[{"type":"command","params":{"command":"/菜单"}},{"type":"wait_until_gui_title","params":{"title":"菜单","timeoutTicks":200,"timeoutSkipCount":0}},{"type":"delay","params":{"ticks":"8","normalizeDelayTo20Tps":true}},{"type":"window_click","params":{"locatorMode":"ITEM_TEXT","locatorText":"第一层副本","locatorMatchMode":"CONTAINS","windowId":"-1","button":0,"clickType":"PICKUP"}}]}
~~~

Teleport must confirm arrival in about 3 seconds and retry on cooldown. See [authoring.md](authoring.md) and template id dungeon_teleport_cooldown_area_retry.

Tool mythos_control accepts operation pause/resume/stop and scope foreground/background/all; operation stop_all restores the temporary aura snapshot. It does not disable unrelated persistent features or undo game effects.

# Markdown notebook

Tool mythos_notes is the Tools-menu notebook, one Markdown file per server under notes/. `{}` or `{operation:"read"}` reads the current world (`singleplayer` when offline or in an integrated world). write replaces the whole document; append adds to the end (inserts a newline when needed); list enumerates saved notebooks. server selects another notebook using the same sanitizing as the GUI. expectedHash from the previous read prevents lost updates. An open GUI draft is returned by read and replaced by write/append.

~~~json
{"operation":"read"}
~~~

~~~json
{"operation":"write","text":"# 记录\n坐标 100 64 200","expectedHash":"HASH_FROM_READ"}
~~~

~~~json
{"operation":"append","text":"- 下一站"}
~~~

~~~json
{"operation":"list"}
~~~

~~~json
{"operation":"read","server":"play.hypixel.net:25565"}
~~~

This is not the path/step `note` field. Use mythos_paths for those.

# Persistent configuration

mythos_config list enumerates files relative to the config root. read returns text, parsed value where possible, and hash. write accepts value (JSON) or text. patch supports add/replace/remove/test JSON Pointer operations:
~~~json
{"operation":"patch","path":"profiles/PROFILE/CONFIG.json","expectedHash":"HASH_FROM_READ","patch":[{"op":"replace","path":"/enabled","value":true},{"op":"remove","path":"/rules/0"}]}
~~~
Replace the example path with one returned by discovery. Reload the matching owner through mythos_modules {operation:"reload",module:"MODULE_ID"}. The generic file layer does not pretend a disk write has already taken effect in game.
Module describe returns writable runtime field types and nested item schemas. set needs fields plus persist:true; it saves and reloads the module. Other object-based settings are edited through their JSON file.

# Packets

Tool mythos_packets: capture_start, capture_stop, clear, or list:
~~~json
{"operation":"list","direction":"C2S","offset":0,"limit":20}
~~~
Capture respects the mod's existing filter settings. S2C lists received packets. Read the send_packet action schema before replay. channel selects FML custom payload, otherwise packetId selects a standard packet; hex is the serialized payload. C2S sends to the connected server. S2C performs the existing local receive simulation.
~~~json
{"actions":[{"type":"send_packet","params":{"direction":"C2S","channel":"ExampleChannel","hex":"00 01"}}]}
~~~
The channel and bytes are illustrative; use the user's intended packet format.

For action-impact analysis, use `mythos_packet_trace`. It reads the packet-workbench input timeline and correlates each selected input with captured packet entries. `inputs` accepts multiple selectors; use exact `keys`, numeric `keyCodes`, `buttons` (`LEFT`/`RIGHT`/`MIDDLE` or numbers), `types` (`key`/`mouse`), `eventIds`, `sessionIds`, `guiTitles`, `screenNames`, `fromMs` and `toMs`.

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
  "maxTotalPackets": 1000,
  "includeDecoded": true
}
~~~

`windowMs` selects packets whose captured interval overlaps the input's ±window. `before` and `after` add the nearest packet entries by count, and duplicate entries are merged. Each result includes the input `eventId`, packet `direction`, `timestamp`, `lastTimestamp`, signed/absolute time deltas, relation, match reasons, HEX, decoded data and aggregate occurrence count. Use `inputTruncated` or `packetTruncated` to detect bounded results; narrow the selector or use returned `eventIds` for a follow-up. `directions:["C2S","S2C"]` is the bidirectional form; `direction:"BOTH"` is also accepted.

# Navigation and results


mythos_navigation with no arguments lists every registered command's names, description and usage. Passing command executes through embedded navigation, without the leading #. For example command "goto 10 64 20"; command "cancel" stops navigation.

mythos_status reads the world/player, nearby entities, active legacy path progress and variable previews. mythos_logs lists recent sessions and actual completion results; pass sessionId for detailed execution text. A finished session can represent one loop, so check active runs as well.

# Chat and command confirmation

Tool mythos_chat reads in-memory messages directly. Query {} for the latest received server messages. Retain connectionId and nextAfterId, then invoke the intended command. Read replies with {"afterId":123,"connectionId":"previous-id","query":"成功"}. Inspect full message context, not just the keyword. Continue using nextAfterId while hasMore is true; connectionChanged/cursorExpired indicate a new connection or lost retained history. connected=false means the messages are from a disconnected session.

Optional stream is received (default), displayed, or all. The latter may contain two records per server message. Types are CHAT, SYSTEM, GAME_INFO and DISPLAYED. includeFormatted and includeComponent expose formatted text and complete component JSON. No outgoing command history is collected. The buffer begins when the new mod is loaded and resets on connection change; it cannot reconstruct earlier login messages from disk logs.
