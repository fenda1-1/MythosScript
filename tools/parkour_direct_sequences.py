"""Create a separate single-goal optimization group from saved parkour sequences."""
import argparse
from copy import deepcopy
import json
from pathlib import Path


def direct_sequence(source):
    sequence = deepcopy(source)
    assert len(sequence['steps']) == 1, 'Expected one action-only step'
    step = sequence['steps'][0]
    assert step.get('pos') is None
    actions = step['actions']
    gotos = [i for i, a in enumerate(actions)
             if a.get('params', {}).get('command', '').startswith('!goto ')]
    assert gotos and actions[-1]['params']['command'] == '!cancel'
    # Preserve the starting guards/settings and the final map-transition guard.
    step['actions'] = actions[:gotos[0]] + [
        {'type': 'command', 'params': {'command': '!set parkourInputCache false'}}
    ] + actions[gotos[-1]:]
    for i, action in enumerate(step['actions']):
        remaining = len(step['actions']) - i - 2
        if action['type'] == 'wait_until_player_in_area':
            action['params']['timeoutSkipCount'] = remaining
        elif action['type'] == 'condition_player_in_area':
            action['params']['skipCount'] = remaining
    sequence.update(name=source['name'].replace('跑酷_', '优化测试_', 1),
                    category='优化测试', subCategory='直达终点', singleExecution=True,
                    note='关输入缓存；传送并确认起点后，仅一次 goto 终点金压力板。等待地图终点传送后取消导航；超时同样取消。')
    step['note'] = sequence['note']
    return sequence


def self_check():
    actions = [
        {'type': 'wait_until_player_in_area', 'params': {'center': [0, 1, 0]}},
        {'type': 'command', 'params': {'command': '!goto 1 1 0'}},
        {'type': 'wait_until_player_in_area', 'params': {'center': [1, 1, 0]}},
        {'type': 'command', 'params': {'command': '!goto 2 1 0'}},
        {'type': 'wait_until_player_in_area', 'params': {'center': [9, 1, 0]}},
        {'type': 'command', 'params': {'command': '!cancel'}}]
    source = {'name': '跑酷_test', 'steps': [{'pos': None, 'actions': actions}]}
    result = direct_sequence(source)
    transformed = result['steps'][0]['actions']
    assert len(actions) == 6 and 'timeoutSkipCount' not in actions[0]['params']
    assert [a['params']['command'] for a in transformed if a['type'] == 'command'] == [
        '!set parkourInputCache false', '!goto 2 1 0', '!cancel']
    assert transformed[0]['params']['timeoutSkipCount'] == 3
    assert transformed[-2]['params']['center'] == [9, 1, 0]
    assert transformed[-2]['params']['timeoutSkipCount'] == 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('--output', type=Path, default=Path('build/parkour-evidence/direct-sequences.json'))
    args = parser.parse_args()
    self_check()
    if args.publish:
        from parkour_publish_sequences import call, client
        players = call('mythos_clients')['players']
        assert len(players) == 1, players
        client.player = players[0]
        call('mythos_discover')
        assert not call('mythos_status')['runs']
        for kind in ('command', 'wait_until_player_in_area', 'condition_player_in_area'):
            call('mythos_actions', {'query': kind})
            call('mythos_actions', {'type': kind})
        call('mythos_templates', {'query': '传送'})
        existing = {s['name'] for s in call('mythos_paths', {'operation': 'list'})['sequences']}
        originals = [call('mythos_paths', {'operation': 'get', 'name': '跑酷_' + course})['sequence']
                     for course in ('snow-10', 'swamp-55', 'swamp-60')]
        sequences = [direct_sequence(source) for source in originals]
        for sequence in sequences:
            assert sequence['name'] not in existing, 'Refusing to overwrite ' + sequence['name']
            checked = call('mythos_validate', {'sequence': sequence})
            assert checked.get('valid', True) and not checked.get('issues'), checked
        for source, sequence in zip(originals, sequences):
            call('mythos_paths', {'operation': 'put', 'sequence': sequence})
            saved = call('mythos_paths', {'operation': 'get', 'name': sequence['name']})['sequence']
            assert saved['steps'] == sequence['steps'] and saved['category'] == '优化测试'
            assert call('mythos_paths', {'operation': 'get', 'name': source['name']})['sequence'] == source
            print(json.dumps({'saved': sequence['name'], 'category': saved['category'],
                              'subCategory': saved['subCategory']}, ensure_ascii=False), flush=True)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({'originals': originals, 'sequences': sequences},
                                         ensure_ascii=False, indent=2), encoding='utf-8')
