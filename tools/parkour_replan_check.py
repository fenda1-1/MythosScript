"""Check that a live run retires the current segment after a planning-context change.

Usage: python tools/parkour_replan_check.py path/to/parkour.jsonl
This checks executor handoff evidence; the benchmark separately checks map completion.
"""
import json
import sys


def check(rows):
    movement = None
    pending = None
    completed = 0
    for row in rows:
        if row.get('type') == 'tick':
            movement = row.get('movement')
            continue
        if row.get('type') != 'parkour_event':
            continue
        message = row['message']
        if message.startswith(('parkour_goal_loaded', 'parkour_capability_changed')) and movement:
            pending = pending or row['timestampMs']
        if 'Replanning at safe movement boundary after world/capability change' in message:
            assert pending is not None, 'Boundary replan without a recorded context change'
            print('Context change -> safe boundary: %.3fs' % ((row['timestampMs'] - pending) / 1000))
            pending = None
            completed += 1
    assert pending is None, 'Current segment was never retired after a world/capability change'
    assert completed, 'No safe-boundary replan was exercised'
    return completed


if __name__ == '__main__':
    with open(sys.argv[1], encoding='utf-8') as source:
        print('Verified boundary replans:', check(json.loads(line) for line in source))
