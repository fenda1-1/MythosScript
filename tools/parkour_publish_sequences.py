"""Publish guarded parkour sequences through MCP without executing or teleporting."""
import importlib.util
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bridge', ROOT / 'skills/mythosscript-mcp/scripts/client.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
config = ROOT.parent / 'MythosTests/instances/1.12.2/inject/game/config/我的世界脚本'
port = json.loads((config / 'mcp_server.json').read_text(encoding='utf-8-sig'))['port']
client = bridge.LocalClient(config / 'mcp.token', f'http://127.0.0.1:{port}/mcp')

def call(name, args=None):
    reply = client.send({'jsonrpc':'2.0','id':1,'method':'tools/call',
                         'params':{'name':name,'arguments':args or {}}})
    result = reply.get('result', {})
    if reply.get('error') or result.get('isError'):
        raise RuntimeError(str(reply))
    return result.get('structuredContent', result)

def action(kind, **params):
    return {'type':kind, 'params':params}

def check(actions, center, radius, timeout):
    actions.append(action('wait_until_player_in_area', center=center, radius=radius,
                          timeoutTicks=timeout, timeoutSkipCount=0))
    # The wait already confirms arrival. A second check several ticks later
    # races continuous movement and can reject a relay just crossed successfully.

def standing(point):
    # Integer coordinates name a block; the player stands at its horizontal center.
    return [value + .5 if float(value).is_integer() else value for value in point]

def goto(point):
    return action('command', command='!goto ' + ' '.join(map(str, point)))

def route_actions(route):
    actions = [action('command', command='!cancel'),
               action('command', command='/tp @s ' + ' '.join(map(str, route['start'])))]
    check(actions, route['start'], .9, 100)
    actions.append(action('command', command='!set parkourMode true'))
    if route.get('requiresVines'):
        actions.append(action('command', command='!set allowVines true'))
    for relay in route.get('relays', []):
        point = relay.get('pos') if isinstance(relay, dict) else relay
        # The lower optional pad leads away from the verified 34 -> 43 detour.
        if route['id'] == 'snow-10' and point == [31,14,-2065]:
            continue
        destination = standing(point)
        actions.append(goto(destination))
        radius = relay.get('arrivalRadius', .8) if isinstance(relay, dict) else .8
        check(actions, destination, radius, 2400)
    if route.get('partialEndpoint'):
        destination = standing(route['partialEndpoint'])
        actions.append(goto(destination))
        check(actions, destination, .8, 2400)
        actions.append(action('command', command='!cancel'))
        return actions
    navigate = goto(standing(route['gold']))
    if route.get('navigateBeforeTrigger'):
        actions.append(navigate)
    actions.extend(route.get('beforeGoldActions', []))
    if not route.get('navigateBeforeTrigger'):
        actions.append(navigate)
    check(actions, route['finishTeleport'], 3, 3600)
    actions.append(action('command', command='!cancel'))
    return actions

def publish(name, subcategory, actions, note):
    # A failed arrival check skips the remainder; cleanup still cancels navigation.
    if not actions or actions[-1] != action('command', command='!cancel'):
        actions.append(action('command', command='!cancel'))
    for i, item in enumerate(actions):
        if item['type'] == 'condition_player_in_area':
            item['params']['skipCount'] = max(0, len(actions)-i-2)
        if item['type'] in ('wait_until_gui_title', 'wait_until_player_in_area'):
            item['params']['timeoutSkipCount'] = max(0, len(actions)-i-2)
    existing = next((s for s in known if s['name'] == name), None)
    sequence = call('mythos_paths', {'operation':'get','name':name})['sequence'] if existing else {}
    sequence.update(name=name, category='跑酷关卡', subCategory=subcategory,
                    note=note, singleExecution=True,
                    steps=[{'pos':None, 'actions':actions, 'note':note}])
    checked = call('mythos_validate', {'sequence':sequence})
    if checked.get('issues'):
        raise RuntimeError((name, checked))
    call('mythos_paths', {'operation':'put','sequence':sequence})
    saved = call('mythos_paths', {'operation':'get','name':name})['sequence']
    if saved['subCategory'] != subcategory or saved['steps'][0]['actions'] != actions:
        raise RuntimeError('Saved sequence differs: '+name)
    print(json.dumps({'saved':name,'subCategory':subcategory,'actions':len(actions)}, ensure_ascii=False), flush=True)

def add_start_teleports(routes):
    """Patch saved courses in place, preserving custom steps and action parameters."""
    pending = []
    names = {s['name'] for s in known}
    for route in routes:
        name = '跑酷_' + route['id']
        if name not in names:
            continue
        sequence = call('mythos_paths', {'operation':'get', 'name':name})['sequence']
        first = sequence['steps'][0]
        actions = first['actions']
        prefix = [action('command', command='!cancel'),
                  action('command', command='/tp @s ' + ' '.join(map(str, route['start'])))]
        offset = 2 if actions[:2] == prefix else 0
        if (first.get('pos') is not None or len(actions) < offset + 2
                or actions[offset]['type'] != 'wait_until_player_in_area'
                or actions[offset]['params']['center'] != route['start']
                or actions[offset+1]['type'] != 'condition_player_in_area'):
            raise RuntimeError('Unexpected starting guard; not overwriting: ' + name)
        actions[offset]['params'].update(radius=.9, timeoutTicks=100)
        actions[offset+1]['params'].update(radius=.9)
        if not offset:
            actions[:0] = prefix
        # Prefix insertion leaves each existing relative skip count unchanged.
        for owner in (sequence, first):
            owner['note'] = owner.get('note', '').replace(
                '站在本段起点执行；不自动传送。',
                '执行后自动传送到本段起点，确认到达后开始跑酷。')
        checked = call('mythos_validate', {'sequence':sequence})
        if checked.get('issues'):
            raise RuntimeError((name, checked))
        pending.append(sequence)
    for sequence in pending:
        call('mythos_paths', {'operation':'put', 'sequence':sequence})
        saved = call('mythos_paths', {'operation':'get', 'name':sequence['name']})['sequence']
        if saved['steps'] != sequence['steps']:
            raise RuntimeError('Saved steps differ: ' + sequence['name'])
        print(json.dumps({'updated':sequence['name'], 'verified':True}, ensure_ascii=False), flush=True)
    print(json.dumps({'updatedCount':len(pending)}), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--add-start-teleports', action='store_true')
    parser.add_argument('--routes', nargs='+', help='Publish only the selected route IDs')
    args = parser.parse_args()
    players = call('mythos_clients')['players']
    if len(players) != 1: raise RuntimeError('Expected one in-world player')
    client.player = players[0]
    call('mythos_discover')
    call('mythos_status')
    known = call('mythos_paths', {'operation':'list'})['sequences']
    data = json.loads((ROOT / 'PARKOUR_ROUTES.json').read_text(encoding='utf-8'))
    if args.add_start_teleports:
        add_start_teleports(data['routes'])
        raise SystemExit(0)
    names = {'underground':'01 地下','snow':'02 雪地','desert':'03 沙漠',
             'swamp':'04 沼泽','nether':'05 下界','end':'06 末地','void':'07 虚空'}
    for route in data['routes']:
        if args.routes and route['id'] not in args.routes: continue
        if not route.get('finishTeleport'): continue
        if 'skip' in route['status']: continue
        level = route['id'].split('-')[0]
        publish('跑酷_'+route['id'], names[level], route_actions(route),
                '执行后自动传送到本段起点，确认到达后开始跑酷；传送或到达超时则取消。'
                + ('仅运行已验证局部段，到中继结束；后半由用户要求省略，不代表整关通关。' if route.get('partialEndpoint') else '')
                + '历史验证：'+route['status'])
    for marker in data['lobbyMarkers']:
        if args.routes and ('入口_'+marker['level']) not in args.routes: continue
        if not marker.get('arrival'): continue
        actions = []
        check(actions, [-8999.5,52,-10000.5], 32, 40)
        point = marker.get('entryGoal', marker.get('approach', marker.get('entryFeet')))
        actions.append(goto(standing(point)))
        check(actions, marker['arrival'], 3, 1200)
        publish('跑酷_入口_'+marker['level'], names[marker['level']], actions,
                '从大厅执行，等待地图传送；门须已解锁。')
