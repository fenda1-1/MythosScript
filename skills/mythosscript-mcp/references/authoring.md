# Robust sequence authoring

This is a hard rule, not a preference. Sequences must wait for game state, act, then confirm. Blind delays that “usually work” are bugs.

Before writing actions, observe the live client:

1. `mythos_snapshot` with `groups:["player","gui","inventory"]` for GUI title, slot text, buttons and coordinates.
2. `mythos_chat` for the real cooldown/failure/success wording.
3. `mythos_templates` for a recipe (`query` or `id`), then replace placeholders. Do not paste a template unchanged.
4. `mythos_actions` for the exact action `type` and params. `mythos_validate` before `mythos_run`.

## Forbidden

Bad: right-click or `/菜单`, delay 1s, click, delay 0.5s, assume success.

Network jitter, server lag or a slow GUI makes that click the wrong slot, or click before the window exists.

## GUI click (chest, menu, confirm)

Required skeleton:

1. Open: `command` / `rightclickblock` / `rightclickentity` / `use_hotbar_item` / `silentuse`.
2. `wait_until_gui_title` for the real title (timeout ~200 ticks). Do not continue on a guess.
3. `delay` 5–10 ticks (`ticks: "8"`, `normalizeDelayTo20Tps: true`) so slots finish loading.
4. Prefer `wait_until_gui_element` for the slot/button text, then `window_click` (container item) or `click` (GuiButton).
5. Confirm the effect: packet/HUD text, inventory change, GUI close, or player area. If it did not happen, retry the whole open→wait→click flow, not only the click.

Example: open a chest and take items.

~~~json
{"actions":[
  {"type":"label","params":{"labelName":"open_chest"}},
  {"type":"rightclickblock","params":{"locatorMode":"POSITION","pos":[10,64,20],"range":5,"preserveView":true}},
  {"type":"wait_until_gui_title","params":{"title":"箱子","timeoutTicks":60,"timeoutSkipCount":1}},
  {"type":"skip_actions","params":{"count":1}},
  {"type":"goto_label","params":{"targetLabel":"open_chest"}},
  {"type":"delay","params":{"ticks":"8","normalizeDelayTo20Tps":true}},
  {"type":"take_all_items_safe","params":{"shiftQuickMove":true}},
  {"type":"delay","params":{"ticks":"8","normalizeDelayTo20Tps":true}},
  {"type":"close_container_window","params":{}}
]}
~~~

`timeoutSkipCount` on a wait skips the following N actions **on timeout only**. Match continues at the next action. The skip/goto pair above retries when the title never appears, and continues to the settle delay when it does.

Start recipe: template id `gui_title_settle_then_click` or `open_world_chest_wait_then_loot`.

## Teleport, dungeon entry, warp

Required skeleton:

1. Close a leftover GUI (`close_container_window`) so a retry is not clicking the previous window.
2. Open the menu the same way the player does.
3. Wait title → settle 8 ticks → wait slot text → `window_click`.
4. `wait_until_player_in_area` with `timeoutTicks: 60` (~3s at 20 TPS). Arrival at any moment inside that window is success.
5. If still not in the area: look for cooldown/failure text (`wait_until_packet_text` or `wait_until_hud_text`), wait extra if it matched, then retry from the open step.
6. Cap retries (`retry_block` retryCount, or a counter). Do not loop forever.

Example: click a dungeon slot, confirm arrival in 3s, retry on cooldown.

~~~json
{"actions":[
  {"type":"retry_block","params":{
    "conditionsText":"abs(player_x - 100) <= 8 && abs(player_y - 64) <= 8 && abs(player_z - 200) <= 8",
    "bodyCount":10,"retryCount":8,"retryDelayTicks":10,"attemptVar":"sequence.teleport_retry"
  }},
  {"type":"close_container_window","params":{}},
  {"type":"command","params":{"command":"/菜单"}},
  {"type":"wait_until_gui_title","params":{"title":"菜单","timeoutTicks":200,"timeoutSkipCount":0}},
  {"type":"condition_gui_title","params":{"title":"菜单","skipCount":6}},
  {"type":"delay","params":{"ticks":"8","normalizeDelayTo20Tps":true}},
  {"type":"wait_until_gui_element","params":{"elementType":"SLOT","guiElementLocatorMode":"TEXT","locatorText":"第一层副本","locatorMatchMode":"CONTAINS","timeoutTicks":80,"timeoutSkipCount":0}},
  {"type":"window_click","params":{"locatorMode":"ITEM_TEXT","locatorText":"第一层副本","locatorMatchMode":"CONTAINS","windowId":"-1","button":0,"clickType":"PICKUP"}},
  {"type":"wait_until_packet_text","params":{"packetText":"冷却","timeoutTicks":10,"timeoutSkipCount":1}},
  {"type":"delay","params":{"ticks":"40","normalizeDelayTo20Tps":true}},
  {"type":"wait_until_player_in_area","params":{"center":[100,64,200],"radius":8,"timeoutTicks":50,"timeoutSkipCount":0}}
]}
~~~

Replace `/菜单`, titles, slot text, `冷却`, and the target `center` from observation. Action-bar cooldowns use `wait_until_hud_text` with `contains` instead of packet text. Chat packets are visible to `wait_until_packet_text` while a sequence is running.

Start recipe: template id `dungeon_teleport_cooldown_area_retry`. Simpler area-only retry: `container_teleport_retry_until_area`.

Label-based 3s confirm without `retry_block`:

~~~json
{"actions":[
  {"type":"label","params":{"labelName":"teleport_start"}},
  {"type":"command","params":{"command":"/菜单"}},
  {"type":"wait_until_gui_title","params":{"title":"菜单","timeoutTicks":200,"timeoutSkipCount":0}},
  {"type":"delay","params":{"ticks":"8","normalizeDelayTo20Tps":true}},
  {"type":"window_click","params":{"locatorMode":"ITEM_TEXT","locatorText":"第一层副本","locatorMatchMode":"CONTAINS","windowId":"-1","button":0,"clickType":"PICKUP"}},
  {"type":"wait_until_player_in_area","params":{"center":[100,64,200],"radius":8,"timeoutTicks":60,"timeoutSkipCount":1}},
  {"type":"skip_actions","params":{"count":3}},
  {"type":"wait_until_packet_text","params":{"packetText":"冷却","timeoutTicks":10,"timeoutSkipCount":1}},
  {"type":"delay","params":{"ticks":"40","normalizeDelayTo20Tps":true}},
  {"type":"goto_label","params":{"targetLabel":"teleport_start"}}
]}
~~~

On area match: `skip_actions` jumps past cooldown handling. On area timeout: skip the skip, then cooldown wait; match waits extra ticks, timeout still retries.

## Condition vs wait skip

- `condition_*`: true continues; false skips `skipCount` following actions.
- `wait_until_*` timeout: skip `timeoutSkipCount` following actions, then continue. Match always goes to the next action.
- Do not invert those. “Wait until arrived” cannot use the same skip layout as “wait until failure text”.

## What to confirm

| Intent | Confirm with | Retry when |
| --- | --- | --- |
| Open chest/menu | GUI title, then slot/button exists | Title missing after ~3s |
| Click a slot | Packet/HUD text, GUI change, or inventory | Click had no effect |
| Teleport / dungeon | `wait_until_player_in_area` ~60 ticks | Not in area, or chat contains 冷却/失败/繁忙/请稍后 |
| Buy / claim / submit | Packet/HUD success **and** item/count change | Text missing or count unchanged |
| NPC interact | Nearby entity, then GUI title | Entity missing or GUI missing |

## Templates

`mythos_templates` `{ }` lists recipes with `principles`. `{ "id":"dungeon_teleport_cooldown_area_retry" }` returns the action JSON. `{ "query":"传送" }` or `{ "category":"传送/副本" }` filters. Custom user templates are included.

Treat every template as a skeleton: keep waits and retries; replace names, titles, texts and coordinates from the live game.
