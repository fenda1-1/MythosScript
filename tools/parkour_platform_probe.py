"""Verify one captured jump in the current world, including one second of stable landing."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import time

from parkour_benchmark import ROOT, save, setting, snapshot, validate
from parkour_publish_sequences import call, client, config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--start', nargs=3, type=float, required=True)
    parser.add_argument('--goal', nargs=3, type=float, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not all(math.isfinite(v) for v in args.start + args.goal):
        parser.error('Coordinates must be finite')
    args.output.mkdir(parents=True, exist_ok=False)
    assert call('mythos_clients')['players'] == ['test_1122_inj']
    client.player = 'test_1122_inj'
    call('mythos_discover')
    state = call('mythos_status')
    assert state['inWorld'] and not state['runs'] and not state['navigation']['active']
    call('mythos_actions', {'query': 'command'})
    call('mythos_actions', {'type': 'command'})
    call('mythos_templates', {'query': 'command'})
    call('mythos_gui', {'operation': 'close'})
    active = json.loads((ROOT.parent / 'MythosTests/instances/1.12.2/inject/active.json').read_text(encoding='utf-8'))
    launch = json.loads((Path(active['run']) / 'result.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(Path(launch['jar']).read_bytes()).hexdigest() == launch['sha256']
    save(args.output / 'source.json', {'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'diff': subprocess.check_output(['git', 'diff', 'HEAD']).decode('utf-8'), 'launch': launch})
    sequence = {'name': 'captured-platform-start', 'steps': [{'pos': None, 'actions': [
        {'type': 'command', 'params': {'command': '/tp @s ' + ' '.join(map(str, args.start))}}]}]}
    validate(sequence)
    assert call('mythos_run', {'sequence': sequence})['accepted']
    deadline = time.monotonic() + 10
    while True:
        snap = snapshot()
        if not call('mythos_status')['runs'] and snap['physics']['onGround'] and math.dist(snap['player']['pos'], args.start) < .01:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError('Start was not confirmed')
        time.sleep(.05)
    assert not snap['physics']['flying'] and not snap['physics']['effects']
    for name, value in [('parkourMode', 'true'), ('parkourInputCache', 'false'), ('parkourDebugLog', 'false')]:
        setting(name, value)
    debug_dir = config.parents[1] / 'logs/parkour'
    old_logs = set(debug_dir.glob('*.jsonl'))
    setting('parkourDebugLog', 'true')
    result = {'start': args.start, 'goal': args.goal, 'pid': state['localPid'], 'passed': False, 'samples': []}
    began = time.monotonic()
    result['startedTimestampMs'] = int(time.time() * 1000)
    anchor, moved, stable = args.start, began, None
    try:
        result['navigation'] = call('mythos_navigation', {'command': 'goto ' + ' '.join(map(str, args.goal))})
        while time.monotonic() - began < 30:
            snap = snapshot()
            now = time.monotonic()
            pos = snap['player']['pos']
            result['samples'].append({'elapsed': now-began, **snap})
            if not snap['player']['alive'] or snap['physics']['flying'] or pos[1] < min(args.start[1], args.goal[1]) - 1:
                result['failure'] = 'Fell, died or flew during the jump'
                break
            at_goal = snap['physics']['onGround'] and math.dist(pos, args.goal) < .2
            stable = (stable or now) if at_goal else None
            if stable and now-stable >= 1:
                result['passed'] = True
                result['arrivalSeconds'] = stable-began
                break
            if math.dist(pos, anchor) >= .05:
                anchor, moved = pos, now
            if now-moved >= 10:
                result['failure'] = 'No movement for 10 seconds'
                break
            time.sleep(.02)
        result.setdefault('failure', None if result['passed'] else 'Timed out')
    finally:
        call('mythos_navigation', {'command': 'cancel'})
        setting('parkourDebugLog', 'false')
        result['debugLogs'] = []
        for source in sorted(set(debug_dir.glob('*.jsonl')) - old_logs):
            target = args.output / source.name
            shutil.copyfile(source, target)
            result['debugLogs'].append({'file': target.name, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
        save(args.output / 'result.json', result)
    print(json.dumps({k: v for k, v in result.items() if k != 'samples'}, ensure_ascii=False))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
