"""Check recorded ordinary ascend/descend handoffs during a parkour benchmark.

Usage: python tools/parkour_handoff_check.py path/to/parkour.jsonl
The benchmark separately verifies successful map completion.
"""
import json
import sys
from collections import Counter


def check(rows):
    completed = Counter()
    tick = None
    for row in rows:
        if row.get('type') == 'tick':
            tick = row
            continue
        message = row.get('message', '')
        if row.get('type') != 'parkour_event':
            continue
        if message.startswith('ordinary_handoff '):
            fields = dict(part.split('=', 1) for part in message.split() if '=' in part)
            assert any(fields[key] == 'true' for key in ('ground', 'ladder', 'water')), message
            completed[fields['type']] += 1
        elif 'Auto-advanced path position from ' in message and tick:
            movement = tick.get('movement') or {}
            if movement.get('type') not in ('MovementAscend', 'MovementDescend'):
                continue
            assert row['clientTick'] == tick['clientTick'], 'Missing handoff state sample'
            assert 0 <= row['timestampMs'] - tick['timestampMs'] <= 150, 'Stale handoff state sample'
            assert any(tick[key] for key in ('onGround', 'onLadder', 'inWater')), message
            completed[movement['type']] += 1
    assert completed, 'No ordinary handoff was recorded'
    return dict(completed)


if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as source:
        print('Verified supported handoffs:', check(json.loads(line) for line in source))
