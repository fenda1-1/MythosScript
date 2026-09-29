"""Capture real-world physics regions and export a runnable offline parkour suite."""
import argparse
import importlib.util
import json
import pathlib
import itertools
import math
import subprocess
import sys
import time
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def vector(value, name, integer=False):
    if not isinstance(value, list) or len(value) != 3 or any(
            isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in value):
        raise ValueError(name + ' must be a finite [x,y,z] array')
    if integer and any(v != int(v) for v in value):
        raise ValueError(name + ' must contain integers')
    return [int(v) if integer else v for v in value]


def tiles(region, default_origin):
    """Split arbitrary volumes into exact, non-overlapping <=32-axis requests."""
    if 'min' in region or 'max' in region:
        if any(k in region for k in ('origin', 'offset', 'size')):
            raise ValueError('min/max cannot be combined with origin/offset/size')
        low, high = [vector(region[k], k, True) for k in ('min', 'max')]
        size = [high[i] - low[i] + 1 for i in range(3)]
    else:
        if 'origin' in region and 'offset' in region:
            raise ValueError('Choose origin or offset')
        centre = vector(region.get('origin', default_origin), 'origin')
        offset = vector(region.get('offset', [0, 0, 0]), 'offset')
        centre = [math.floor(centre[i] + offset[i]) for i in range(3)]
        size = vector(region.get('size', [16, 16, 16]), 'size', True)
        low = [centre[i] - size[i] // 2 for i in range(3)]
    if any(n < 1 for n in size):
        raise ValueError('Empty or inverted region')
    for start in itertools.product(*(range(0, n, 32) for n in size)):
        extent = [min(32, size[i] - start[i]) for i in range(3)]
        yield {'origin': [low[i] + start[i] + extent[i] // 2 for i in range(3)], 'size': extent}


def compact(snapshot):
    if not snapshot.get('inWorld') or 'world' not in snapshot or 'player' not in snapshot:
        raise ValueError('Capture is missing the world/player')
    world = snapshot['world']
    if any(state.startswith('__') for state in world['palette']):
        raise ValueError('Capture includes unloaded/out-of-world cells; load the region before capturing')
    if 'physics' not in snapshot or 'collisionBoxes' not in world:
        raise ValueError('Running mod lacks includePhysics; rebuild/restart it before exporting replay fixtures')
    data = {k: snapshot[k] for k in ('origin', 'size', 'player', 'world', 'physics', 'sessionId', 'server', 'tick')}
    data['schemaVersion'] = 1
    data['world'] = dict(world)
    data['world']['cuboids'] = [[*box['min'], *box['max'], box['state']] for box in world['cuboids']]
    return data


def capture_plan(plan, output, call):
    """One call per bounded tile; retain each capture tick instead of claiming atomicity."""
    regions, tests = plan.get('regions', []), plan.get('tests', [])
    if not regions:
        raise ValueError('Plan needs regions')
    names = [r.get('id') for r in regions]
    load_positions = {r['id']: vector(r['loadAt'], 'loadAt') for r in regions if 'loadAt' in r}
    required_effects = {r['id']: r.get('requiredEffects', {}) for r in regions}
    if any(not isinstance(n, str) or not n for n in names) or len(set(names)) != len(names):
        raise ValueError('Region IDs must be nonempty and unique')
    test_names = set()
    for test in tests:
        if not test.get('name') or test['name'] in test_names:
            raise ValueError('Test names must be nonempty and unique')
        test_names.add(test['name'])
        if test.get('mode', 'trajectory') not in ('trajectory', 'graph'):
            raise ValueError('Unknown test mode: ' + str(test.get('mode')))
        if test.get('mode') == 'graph' and len(test.get('goals', [])) != 1:
            raise ValueError('Graph mode needs one final goal, not hand-selected waypoints')
        selected = test.get('regions', names)
        if not selected or any(n not in names for n in selected):
            raise ValueError('Test references unknown/empty regions: ' + test['name'])
        if 'start' in test:
            vector(test['start'], 'start')
        if not test.get('goals'):
            raise ValueError('Test needs goals: ' + test['name'])
        for goal in test['goals']:
            vector(goal, 'goal')
    if output.exists():
        raise ValueError('Output directory exists; choose a new capture path')
    baseline = call('mythos_snapshot', {'groups': ['player']})
    if not baseline.get('inWorld'):
        raise ValueError('Selected player is not in a world')
    origin = vector(plan.get('origin', baseline['player']['originBlock']), 'origin')
    region_tiles = [(region['id'], list(tiles(region, origin))) for region in regions]
    total_tiles = sum(len(region_tile) for _, region_tile in region_tiles)
    output.mkdir(parents=True)
    index = {'schemaVersion': 1, 'origin': origin, 'sessionId': baseline['sessionId'],
              'player': baseline['player']['name'], 'captures': [], 'tests': tests}
    if 'allowVines' in plan:
        index['allowVines'] = bool(plan['allowVines'])
    if 'effectRefresh' in plan:
        index['effectRefresh'] = plan['effectRefresh']
    loaded_regions, captured = set(), 0
    for name, region_tile in region_tiles:
        if name in load_positions and name not in loaded_regions:
            target = load_positions[name]
            actions = [{'type': 'command', 'params': {'command': '/tp @s ' + ' '.join(map(str, target))}},
                       {'type': 'wait_until_player_in_area', 'params': {'center': target, 'radius': .8, 'timeoutTicks': 200}}]
            validation = call('mythos_validate', {'actions': actions})
            if any(issue.get('severity') == 'ERROR' for issue in validation.get('issues', [])):
                raise ValueError('Invalid capture teleport: ' + str(validation))
            call('mythos_run', {'actions': actions})
            deadline = time.monotonic() + 15
            while True:
                status = call('mythos_status', {})
                p = status.get('player', {})
                if p and math.dist([p['x'], p['y'], p['z']], target) < .8 and not status.get('runs'):
                    break
                if time.monotonic() >= deadline:
                    raise ValueError('Capture teleport was not confirmed: ' + name)
                time.sleep(.1)
            loaded_regions.add(name)
        data, first_tick = [], None
        for request in region_tile:
            deadline = time.monotonic() + 15
            while True:
                snap = call('mythos_snapshot', {'groups': ['player', 'world'], 'includePhysics': True, **request})
                effects = {e['id']: e['amplifier'] for e in snap.get('physics', {}).get('effects', [])}
                if (not any(state.startswith('__') for state in snap.get('world', {}).get('palette', []))
                        and all(effects.get(effect) == amplifier
                                for effect, amplifier in required_effects[name].items())):
                    break
                if time.monotonic() >= deadline:
                    raise ValueError('Capture region or required potion effects were not ready: ' + name)
                time.sleep(.2)
            if snap.get('sessionId') != baseline['sessionId'] or snap.get('player', {}).get('dimension') != baseline['player']['dimension']:
                raise ValueError('World session changed during capture; incomplete suite was not published')
            data.append(compact(snap))
            if first_tick is None:
                first_tick = snap['tick']
            captured += 1
            print(f'Captured {name} tile {captured}/{total_tiles}: {request}', flush=True)
        # One file per scene: a single tile stays readable JSON, larger scenes pack every tile in order.
        if len(data) == 1:
            filename = f'{name}.json'
            (output/filename).write_text(json.dumps(data[0], ensure_ascii=False) + '\n', encoding='utf-8')
        else:
            filename = f'{name}.zip'
            with zipfile.ZipFile(output/filename, 'w', zipfile.ZIP_DEFLATED) as archive:
                for number, tile in enumerate(data):
                    archive.writestr(f'{number:02d}.json', json.dumps(tile, ensure_ascii=False) + '\n')
        index['captures'].append({'region': name, 'file': filename, 'tick': first_tick})
    # Publish only after every capture was validated. Partial directories have no suite.json.
    (output/'suite.json').write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return output/'suite.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bridge', type=pathlib.Path, default=ROOT/'skills/mythosscript-mcp/scripts/client.py')
    parser.add_argument('--player', required=True)
    parser.add_argument('--pid', type=int)
    parser.add_argument('--url', help='Explicit local MCP endpoint (otherwise auto-discover)')
    parser.add_argument('--token-file', type=pathlib.Path)
    parser.add_argument('--origin', type=int, nargs=3)
    parser.add_argument('--size', type=int, nargs=3, default=[16, 16, 16])
    parser.add_argument('--output', required=True, type=pathlib.Path)
    parser.add_argument('--scene', action='store_true', help='Store a compact, state-preserving offline world fixture')
    parser.add_argument('--plan', type=pathlib.Path, help='JSON regions/tests plan; output is a new suite directory')
    parser.add_argument('--test', action='store_true', help='Run the exported suite immediately (requires --plan)')
    args = parser.parse_args()
    if any(n < 1 or n > 32 for n in args.size):
        parser.error('Each size must be between 1 and 32')
    if args.output.exists():
        parser.error('Output exists; choose a new snapshot filename')
    spec = importlib.util.spec_from_file_location('mythos_bridge', args.bridge)
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    if bool(args.url) != bool(args.token_file):
        parser.error('--url and --token-file must be supplied together')
    client = bridge.LocalClient(args.token_file, args.url, args.pid, args.player, not args.url, None)
    def call(name, arguments):
        response = client.send({'jsonrpc': '2.0', 'id': 'parkour-capture', 'method': 'tools/call',
                                'params': {'name': name, 'arguments': arguments}})
        if response.get('error') or response.get('result', {}).get('isError'):
            raise RuntimeError(json.dumps(response, ensure_ascii=False))
        return response['result']['structuredContent']
    if args.plan:
        if args.origin is not None or args.scene:
            parser.error('--plan supplies its own origins and always exports scenes')
        call('mythos_clients', {})
        call('mythos_discover', {})
        call('mythos_status', {})
        suite = capture_plan(json.loads(args.plan.read_text(encoding='utf-8-sig')), args.output.resolve(), call)
        print('Saved offline suite: ' + str(suite))
        if args.test:
            raise SystemExit(subprocess.call([sys.executable, str(ROOT/'tools/parkour_offline.py'), '--suite', str(suite)]))
        return
    if args.test:
        parser.error('--test requires --plan')
    arguments = {'player': args.player, 'groups': ['player', 'world'], 'size': args.size}
    if args.scene:
        arguments['includePhysics'] = True
    if args.pid is not None:
        arguments['pid'] = args.pid
    if args.origin is not None:
        arguments['origin'] = args.origin
    response = client.send({'jsonrpc': '2.0', 'id': 'parkour-snapshot', 'method': 'tools/call',
                            'params': {'name': 'mythos_snapshot', 'arguments': arguments}})
    if response.get('error') or response.get('result', {}).get('isError'):
        raise RuntimeError(json.dumps(response, ensure_ascii=False))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    data = response
    if args.scene:
        snapshot = response['result']['structuredContent']
        data = compact(snapshot)
        print('Position:', data['player']['pos'])
        print('Palette:', data['world']['palette'])
        for box in data['world']['cuboids']:
            state = data['world']['palette'][box[6]]
            if any(kind in state for kind in ('ladder', 'pressure_plate')):
                print(state, [data['origin'][i] + box[i] for i in range(3)],
                      [data['origin'][i] + box[i + 3] for i in range(3)])
    with args.output.open('x', encoding='utf-8') as destination:
        json.dump(data, destination, ensure_ascii=False, indent=None if args.scene else 2)
        destination.write('\n')
    print('Saved MCP snapshot: ' + str(args.output.resolve()))


if __name__ == '__main__':
    main()
