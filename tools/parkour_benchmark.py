"""Cache-disabled three-course live benchmark, with optional per-tick evidence."""
import argparse
import json
import math
import hashlib
import shutil
import subprocess
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from parkour_publish_sequences import call, client, config

LOBBY = [-8999.5, 52, -10025.5]


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def snapshot():
    return call('mythos_snapshot', {'groups': ['player'], 'includePhysics': True})


def validate(sequence):
    result = call('mythos_validate', {'sequence': sequence})
    if not result.get('valid', True) or result.get('errors') or result.get('issues'):
        raise RuntimeError(str(result))


def setting(name, value=None):
    cursor = call('mythos_chat', {'limit': 1, 'stream': 'all'})
    command = 'set ' + name + ((' ' + value) if value is not None else '')
    call('mythos_navigation', {'command': command})
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        reply = call('mythos_chat', {'connectionId': cursor['connectionId'],
                     'afterId': cursor['nextAfterId'], 'stream': 'all', 'limit': 100})
        if reply['messages']:
            return reply
        time.sleep(.05)
    raise RuntimeError('No setting acknowledgement: ' + name)


def run(course, attempt, output, debug=False, sequence_prefix='跑酷_', require_loaded_goal=False, require_unloaded_goal=False, diagnostic_start=None):
    assert not (require_loaded_goal and require_unloaded_goal)
    output.mkdir(parents=True, exist_ok=True)
    path = output / (course + '-' + str(attempt) + '.json')
    if path.exists():
        raise FileExistsError(path)
    cache = False
    players = call('mythos_clients')['players']
    assert players == ['test_1122_inj'], players
    client.player = players[0]
    call('mythos_discover')
    call('mythos_actions', {'query': 'command'})
    call('mythos_actions', {'type': 'command'})
    call('mythos_templates', {'query': 'command'})
    state = call('mythos_status')
    assert state['inWorld'] and not state['runs'] and not state['navigation']['active']
    call('mythos_gui', {'operation': 'close'})
    setup = {'name': 'baseline-preflight', 'steps': [{'pos': None, 'actions': [
        {'type': 'command', 'params': {'command': command}}
        for command in ['/tp @s -8999.5 52 -10025.5', '/gamemode survival', '/effect @s clear']]}]}
    validate(setup)
    setup_run = call('mythos_run', {'sequence': setup})
    assert setup_run['accepted']
    deadline = time.monotonic() + 15
    while True:
        snap = snapshot()
        state = call('mythos_status')
        if (not state['runs'] and math.dist(snap['player']['pos'], LOBBY) < 3
                and not snap['player']['creative'] and not snap['physics']['flying']
                and not snap['physics']['effects'] and snap['physics']['onGround']):
            break
        if time.monotonic() > deadline:
            raise RuntimeError('Neutral lobby preflight not confirmed')
        time.sleep(.1)
    checks = {key: setting(key, value) for key, value in [
        ('parkourInputCache', 'false'), ('parkourDebugLog', 'false'),
        ('parkourDebugRender', 'false'), ('allowVines', 'true')]}
    checks['cacheQuery'] = setting('parkourInputCache')
    expected = '[Baritone] ' + ('true' if cache else 'false')
    assert any(m['text'].startswith(expected) for m in checks['cacheQuery']['messages']), checks
    debug_dir = config.parents[1] / 'logs/parkour'
    old_logs = set(debug_dir.glob('*.jsonl'))
    if debug:
        checks['debugEnabled'] = setting('parkourDebugLog', 'true')
    name = sequence_prefix + course
    sequence = call('mythos_paths', {'operation': 'get', 'name': name})['sequence']
    if diagnostic_start is not None:
        actions = sequence['steps'][0]['actions']
        teleport = next(a for a in actions if a.get('params', {}).get('command', '').startswith('/tp '))
        teleport['params']['command'] = '/tp @s ' + ' '.join(map(str, diagnostic_start))
        next(a for a in actions if a['type'] == 'wait_until_player_in_area')['params']['center'] = diagnostic_start
        sequence['name'] = 'diagnostic-start_' + name
    validate(sequence)
    actions = sequence['steps'][0]['actions']
    gotos = [(i, a['params']['command']) for i, a in enumerate(actions)
             if a.get('params', {}).get('command', '').startswith('!goto ')]
    endpoint = list(map(float, gotos[-1][1].split()[1:]))
    # Each course ends by waiting for the map transition area (the global lobby
    # for most courses, the next segment for chained ones like swamp-55).
    waits = [a['params'] for a in actions if a.get('type') == 'wait_until_player_in_area']
    marker = list(map(float, waits[-1]['center']))
    marker_radius = float(waits[-1].get('radius', 3))
    if require_loaded_goal or require_unloaded_goal:
        # Establish the loaded-world precondition before the timed, unmodified
        # sequence. A teleport from the lobby otherwise races chunk streaming.
        teleport = next(a for a in actions if a.get('params', {}).get('command', '').startswith('/tp '))
        preload = {'name': 'goal-loading-preflight', 'steps': [{'pos': None, 'actions': [teleport]}]}
        validate(preload)
        assert call('mythos_run', {'sequence': preload})['accepted']
        deadline = time.monotonic() + 30
        while True:
            loaded = call('mythos_snapshot', {'groups': ['player', 'world'],
                          'origin': [math.floor(v) for v in endpoint], 'size': [1, 1, 1]})
            if (math.dist(loaded['player']['pos'], list(map(float, waits[0]['center']))) < 1
                    and ('__unloaded__' not in loaded['world']['palette']) == require_loaded_goal
                    and not call('mythos_status')['runs']):
                checks['loadedGoal' if require_loaded_goal else 'unloadedGoal'] = loaded
                break
            if time.monotonic() >= deadline:
                raise RuntimeError('Goal loading precondition not met from the course start: ' + course)
            time.sleep(.1)
    initial = snapshot()
    chat = call('mythos_chat', {'limit': 1, 'stream': 'all'})
    result = {'course': course, 'attempt': attempt, 'cacheEnabled': cache, 'pid': state['localPid'],
              'marker': marker, 'markerRadius': marker_radius,
              'checks': checks, 'initial': initial, 'sequence': sequence,
              'passed': False, 'samples': [], 'startedAt': time.strftime('%Y-%m-%d %H:%M:%S')}
    result['debugEnabled'] = debug
    result['diagnosticStart'] = diagnostic_start
    result['startedTimestampMs'] = time.time_ns() // 1000000
    began = time.monotonic()
    previous = None
    armed = False
    lobby_time = None
    near_endpoint = False
    previous_action = None
    stationary_pos = None
    stationary_since = None
    try:
        result['run'] = call('mythos_run', {'name': name} if diagnostic_start is None else {'sequence': sequence})
        assert result['run']['accepted'], result['run']
        while time.monotonic() - began < 240:
            state = call('mythos_status')
            snap = snapshot()
            pos = snap['player']['pos']
            elapsed = round(time.monotonic() - began, 3)
            runs = state['runs']
            index = runs[0].get('actionIndex') if runs else None
            armed = armed or (index is not None and index >= gotos[0][0])
            at_lobby = math.dist(pos, marker) <= marker_radius
            sample = {'elapsed': elapsed, 'tick': snap['tick'], 'timestampMs': snap['timestampMs'],
                      'pos': pos, 'action': index, 'active': state['navigation']['active'],
                      'alive': snap['player']['alive'], 'health': snap['player']['health'],
                      'physics': snap['physics'], 'fps': state['fps']}
            result['samples'].append(sample)
            if index != previous_action:
                print(json.dumps({'course': course, 'attempt': attempt, 'elapsed': elapsed,
                                  'action': index, 'pos': pos}), flush=True)
                previous_action = index
            if armed and (pos[1] < 1 or not snap['player']['alive']):
                result['failure'] = 'First attempt fell below the course or died'
                break
            if armed and previous and math.dist(pos, previous) > 8 and not at_lobby:
                result['failure'] = 'Unexpected reset during first attempt'
                break
            if armed:
                if stationary_pos is None or math.dist(pos, stationary_pos) >= .05:
                    stationary_pos, stationary_since = pos, elapsed
                elif elapsed - stationary_since >= 10:
                    result['failure'] = 'No movement for 10 seconds'
                    result['stationarySeconds'] = round(elapsed - stationary_since, 3)
                    break
            near_endpoint = near_endpoint or (armed and math.dist(pos, endpoint) < 4)
            if armed and at_lobby:
                if not near_endpoint:
                    result['failure'] = 'Lobby transition without observed course endpoint'
                    break
                if lobby_time is None:
                    lobby_time = elapsed
                if not runs:
                    result.update(passed=True, completionSeconds=lobby_time,
                                  confirmationSeconds=elapsed)
                    break
            elif armed and not runs:
                result['failure'] = 'Sequence ended without the expected map transition'
                break
            previous = pos if armed else None
            time.sleep(.035)
        if not result['passed'] and 'failure' not in result:
            result['failure'] = 'Timed out before confirmed map transition and sequence completion'
    except Exception as error:
        result['failure'] = repr(error)
        raise
    finally:
        save(path, result)
        call('mythos_control', {'operation': 'stop_all'})
        call('mythos_navigation', {'command': 'cancel'})
        result['logs'] = call('mythos_logs', {'sessionId': result['run']['executionSessionId'],
                                            'includeEvents': True})
        result['cacheAfter'] = setting('parkourInputCache')
        result['chat'] = []
        while True:
            reply = call('mythos_chat', {'connectionId': chat['connectionId'],
                         'afterId': chat['nextAfterId'], 'stream': 'all', 'limit': 200})
            result['chat'].append(reply)
            chat = reply
            if not reply['hasMore']:
                break
        result['final'] = snapshot()
        # Completion must be confirmed by the execution session, not just teleport.
        sessions = result['logs'].get('sessions', [])
        session = next((s for s in sessions if s.get('sessionId') == result['run']['executionSessionId']), None)
        if session is None:
            session = result['logs'].get('session', result['logs'])
        result['executionAudit'] = session
        if result['passed'] and (not session.get('finished') or session.get('timedOut')
                                 or session.get('outcome') != 'success'):
            result.update(passed=False, failure='Execution log did not confirm success')
        if not any(m['text'].startswith('[Baritone] false') for m in result['cacheAfter']['messages']):
            result.update(passed=False, failure='Input cache was not confirmed disabled after run')
        if debug:
            setting('parkourDebugLog', 'false')
            result['debugLogs'] = []
            for source in sorted(set(debug_dir.glob('*.jsonl')) - old_logs):
                deadline = time.monotonic() + 3
                while True:
                    lines = source.read_text(encoding='utf-8').splitlines()
                    if lines and json.loads(lines[-1]).get('type') == 'session_end':
                        break
                    if time.monotonic() >= deadline:
                        raise RuntimeError('Debug log did not close: ' + str(source))
                    time.sleep(.05)
                target = output / (course + '-' + str(attempt) + '-' + source.name)
                shutil.copyfile(source, target)
                result['debugLogs'].append({'file': target.name,
                    'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
        result['observedSeconds'] = result['samples'][-1]['elapsed'] if result['samples'] else None
        save(path, result)
        print(json.dumps({k: result.get(k) for k in ['course', 'attempt', 'passed',
                         'completionSeconds', 'observedSeconds', 'failure']}), flush=True)
    return result['passed']


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--courses', nargs='+', default=['snow-10', 'swamp-55', 'swamp-60'],
                        choices=['snow-10', 'swamp-55', 'swamp-60', 'underground', 'nether-70'])
    parser.add_argument('--attempt', type=int, default=1)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--debug', action='store_true')
    parser.add_argument('--sequence-prefix', default='跑酷_')
    parser.add_argument('--diagnostic-start', nargs=3, type=float,
                        help='Inline diagnostic from this platform; never a full-course result or saved-path edit')
    loading = parser.add_mutually_exclusive_group()
    loading.add_argument('--require-loaded-goal', action='store_true')
    loading.add_argument('--require-unloaded-goal', action='store_true')
    args = parser.parse_args()
    if args.diagnostic_start is not None and (len(args.courses) != 1 or not all(map(math.isfinite, args.diagnostic_start))):
        parser.error('--diagnostic-start requires one course and three finite coordinates')
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = args.output / 'source.json'
    if manifest.exists():
        raise FileExistsError(manifest)
    active = json.loads((ROOT.parent / 'MythosTests/instances/1.12.2/inject/active.json').read_text(encoding='utf-8'))
    launch = json.loads((Path(active['run']) / 'result.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(Path(launch['jar']).read_bytes()).hexdigest() == launch['sha256']
    save(manifest, {'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'diff': subprocess.check_output(['git', 'diff', 'HEAD'], cwd=ROOT).decode('utf-8'),
        'launch': launch})
    results = [run(course, args.attempt, args.output, args.debug, args.sequence_prefix,
                   args.require_loaded_goal, args.require_unloaded_goal, args.diagnostic_start)
               for course in args.courses]
    raise SystemExit(0 if all(results) else 1)
