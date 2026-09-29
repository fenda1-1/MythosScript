"""Analyze benchmark samples and optional tick logs; no game access required."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path


def stationary_intervals(ticks):
    pauses = []
    first = previous = None

    def finish():
        if first is not None and previous['timestampMs'] - first['timestampMs'] >= 200:
            pauses.append({'seconds': (previous['timestampMs'] - first['timestampMs']) / 1000,
                'startMs': first['timestampMs'], 'endMs': previous['timestampMs'],
                'pos': first.get('positionExact', first.get('position')),
                'movement': first.get('movement'), 'parkour': first.get('parkour')})

    for tick in ticks:
        pos = tick.get('positionExact', tick.get('position'))
        if previous is not None and 0 < tick['timestampMs'] - previous['timestampMs'] <= 150 and math.dist(
                pos, previous.get('positionExact', previous.get('position'))) < .003:
            if first is None:
                first = previous
        else:
            finish()
            first = None
        previous = tick
    finish()
    return sorted(pauses, key=lambda p: -p['seconds'])


def search_metrics(events, jvm):
    jobs = {}
    for event in events:
        message = event['message']
        if not message.startswith(('search_submitted ', 'search_timing ', 'search_result ')):
            continue
        fields = dict(part.split('=', 1) for part in message.split() if '=' in part)
        job = jobs.setdefault(fields['id'], {'kind': fields['kind']})
        job[message.split()[0][7:]] = {**fields, 'elapsedSeconds': event['elapsedSeconds']}
    kinds = {}
    for job in jobs.values():
        group = kinds.setdefault(job['kind'], {'jobs': 0, 'timedJobs': 0, 'outcomes': Counter()})
        group['jobs'] += 1
        group['outcomes'][job.get('result', {}).get('outcome', 'unobserved')] += 1
        if 'timing' in job:
            group['timedJobs'] += 1
        for key in ('snapshotNanos', 'queueNanos', 'solveNanos', 'cpuNanos', 'allocatedBytes'):
            value = int(job.get('timing', job.get('submitted', {})).get(key, -1))
            if key == 'snapshotNanos':
                value = int(job.get('submitted', {}).get(key, -1))
            if value >= 0:
                group[key] = group.get(key, 0) + value
    gc = {}
    if jvm:
        gc = {'samples': len(jvm), 'coveredSeconds': (jvm[-1]['timestampMs'] - jvm[0]['timestampMs']) / 1000,
              'gcCount': jvm[-1]['gcCount'] - jvm[0]['gcCount'],
              'gcMillis': jvm[-1]['gcMillis'] - jvm[0]['gcMillis'],
              'maxObservedHeapBytes': max(row['heapUsedBytes'] for row in jvm)}
    return {'byKind': kinds, 'jobs': jobs, 'jvm': gc}


def analyze(path):
    data = json.loads(path.read_text(encoding='utf-8'))
    end = data.get('completionSeconds', data.get('observedSeconds'))
    samples = [s for s in data['samples'] if s['elapsed'] <= end]
    actions = data['sequence']['steps'][0]['actions']
    audit = data.get('executionAudit', data.get('logs', {}))
    executed = {e.get('actionIndex') for e in audit.get('events', [])
                if e.get('message', '').startswith('action: ') and '!goto ' in e['message']}
    stages = []
    for index, action in enumerate(actions):
        command = action.get('params', {}).get('command', '')
        if not command.startswith('!goto '):
            continue
        first = next((s for s in samples if s['action'] == index), None)
        if first is None and index in executed:
            first = next((s for s in samples if s['action'] is not None and s['action'] >= index), None)
        if first:
            stages.append({'action': index, 'target': command[6:], 'startSeconds': first['elapsed']})
    for i, stage in enumerate(stages):
        stage['endSeconds'] = stages[i + 1]['startSeconds'] if i + 1 < len(stages) else end
        stage['seconds'] = round(stage['endSeconds'] - stage['startSeconds'], 3)
    ticks, events, jvm = [], [], []
    start_ms = data['startedTimestampMs']
    for entry in data.get('debugLogs', []):
        for line in (path.parent / entry['file']).read_text(encoding='utf-8').splitlines():
            row = json.loads(line)
            if not start_ms <= row.get('timestampMs', 0) <= start_ms + end * 1000:
                continue
            if row['type'] == 'tick':
                ticks.append(row)
            elif row['type'] == 'parkour_event':
                row['elapsedSeconds'] = (row['timestampMs'] - start_ms) / 1000
                events.append(row)
            elif row['type'] == 'jvm':
                jvm.append(row)
    pauses = stationary_intervals(ticks)
    route, by_type = [], Counter()
    for previous, tick in zip(ticks, ticks[1:]):
        dt = (tick['timestampMs'] - previous['timestampMs']) / 1000
        movement = previous.get('movement')
        key = json.dumps(movement, sort_keys=True)
        if not route or route[-1]['key'] != key:
            route.append({'key': key, 'movement': movement, 'seconds': 0,
                          'startSeconds': (previous['timestampMs'] - start_ms) / 1000})
        route[-1]['seconds'] += dt
        by_type[(movement or {}).get('type', 'noMovement')] += dt
    for leg in route:
        del leg['key']
        leg['seconds'] = round(leg['seconds'], 3)
    for pause in pauses:
        pause['startSeconds'] = (pause['startMs'] - start_ms) / 1000
        pause['nearbyEvents'] = [e['message'] for e in events
            if pause['startMs'] - 1000 <= e['timestampMs'] <= pause['endMs'] + 100
            and not e['message'].startswith('traj_apply ')]
    for stage in stages:
        stage['stationarySeconds'] = round(sum(max(0, min(p['endMs'], start_ms + stage['endSeconds'] * 1000)
            - max(p['startMs'], start_ms + stage['startSeconds'] * 1000)) / 1000 for p in pauses), 3)
    return {'course': data['course'], 'resultFile': path.name, 'passed': data['passed'], 'seconds': end,
        'failure': data.get('failure'), 'stages': stages, 'route': route,
        'movementSeconds': {k: round(v, 3) for k, v in by_type.items()},
        'stationarySeconds': round(sum(p['seconds'] for p in pauses), 3), 'pauses': pauses,
        'eventCounts': dict(Counter(e['message'].split(' ', 1)[0] for e in events)),
        'searchMetrics': search_metrics(events, jvm),
        'failureEvents': [e for e in events if e['message'].startswith(('fail ', 'trajectory_failed', 'trajectory_replan', 'flow_blocked'))
                          or e['message'].startswith('path_executor ') and 'Cancelling' in e['message']],
        'sampleMaxGapSeconds': max((b['elapsed'] - a['elapsed'] for a, b in zip(samples, samples[1:])), default=0),
        'tickCount': len(ticks)}


def self_check():
    metrics = search_metrics([{'elapsedSeconds': i, 'message': message} for i, message in enumerate([
        'search_submitted kind=prefetch id=1 snapshotNanos=10',
        'search_timing kind=prefetch id=1 queueNanos=20 solveNanos=30 cpuNanos=-1 allocatedBytes=100',
        'search_result kind=prefetch id=1 outcome=drift',
        'search_submitted kind=prefetch id=2 snapshotNanos=15',
        'search_result kind=prefetch id=2 outcome=reset'])], [])['byKind']['prefetch']
    assert metrics['jobs'] == 2 and metrics['timedJobs'] == 1
    assert metrics['snapshotNanos'] == 25 and metrics['solveNanos'] == 30
    assert 'cpuNanos' not in metrics and metrics['outcomes'] == {'drift': 1, 'reset': 1}
    # Include stationary time with no executor, and flush a stop at end of file.
    ticks = [{'timestampMs': i * 50, 'position': [0, 1, 0]} for i in range(6)]
    assert stationary_intervals(ticks)[0]['seconds'] == .25
    ticks += [{'timestampMs': 1000 + i * 50, 'position': [10, 1, 0]} for i in range(5)]
    assert [p['seconds'] for p in stationary_intervals(ticks)] == [.25, .2]
    assert not stationary_intervals([{'timestampMs': i * 50, 'position': [i, 1, 0]} for i in range(6)])
    # A completed sequence that skipped goto is not a navigation stage.
    import tempfile
    data = {'course': 'skipped-start', 'passed': False, 'observedSeconds': 1,
            'startedTimestampMs': 0, 'samples': [{'elapsed': 1, 'action': 2}],
            'sequence': {'steps': [{'actions': [{'type': 'command', 'params': {'command': '!goto 1 2 3'}}]}]},
            'executionAudit': {'events': [{'actionIndex': 2, 'message': 'action: step=0, action=2 -> 命令: !cancel'}]}}
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / 'skipped-1.json'
        path.write_text(json.dumps(data), encoding='utf-8')
        assert not analyze(path)['stages']
        data['executionAudit']['events'].append({'actionIndex': 0, 'message': 'action: step=0, action=0 -> 命令: !goto 1 2 3'})
        path.write_text(json.dumps(data), encoding='utf-8')
        assert len(analyze(path)['stages']) == 1
        # Long runs may trim the command log; an exact action sample still counts.
        data['executionAudit']['events'] = []
        data['samples'][0]['action'] = 0
        path.write_text(json.dumps(data), encoding='utf-8')
        assert len(analyze(path)['stages']) == 1


def markdown(reports):
    lines = ['# 实机路线明细', '',
        '时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。', '']
    for report in reports:
        label = '通关' if report['passed'] else '失败停止'
        lines += [f"## {report['course']}：{label} {report['seconds']:.3f}s", '',
            f"原始结果：`{report['resultFile']}`。", '',
            f"静止 {report['stationarySeconds']:.3f}s；控制器时间：`{json.dumps(report['movementSeconds'], ensure_ascii=False)}`。", '',
            f"事件计数：`{json.dumps(report['eventCounts'], ensure_ascii=False)}`。", '',
            '### 最长静止区间', '', '| 开始秒 | 持续秒 | 位置 |', '|---:|---:|---|']
        for pause in report['pauses'][:8]:
            lines.append(f"| {pause['startSeconds']:.3f} | {pause['seconds']:.3f} | {pause['pos']} |")
        metrics = report['searchMetrics']
        if metrics['byKind']:
            lines += ['', '### 搜索计量', '',
                '墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。', '',
                '| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |',
                '|---|---:|---:|---:|---:|---:|---|']
            for kind, group in metrics['byKind'].items():
                values = [f"{group[key] / 1e9:.3f}" if key in group else '—'
                          for key in ('queueNanos', 'solveNanos', 'cpuNanos')]
                allocated = f"{group['allocatedBytes'] / 1048576:.1f}" if 'allocatedBytes' in group else '—'
                lines.append(f"| {kind} | {group['jobs']}/{group['timedJobs']} | {' | '.join(values)} | {allocated} | {dict(group['outcomes'])} |")
            lines += ['', f"JVM采样覆盖：`{json.dumps(metrics['jvm'], ensure_ascii=False)}`。"]
        lines += ['', '### 实际控制器逐段路线', '',
            '| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |', '|---:|---:|---|---|']
        for leg in report['route']:
            move = leg['movement'] or {}
            route = f"{move['source']} → {move['destination']}" if move else '—'
            lines.append(f"| {leg['startSeconds']:.3f} | {leg['seconds']:.3f} | {move.get('type', '无控制器')} | {route} |")
        lines += ['', '### 失败及恢复事件', '']
        lines += [f"- {event['elapsedSeconds']:.3f}s：`{event['message']}`" for event in report['failureEvents']]
        lines.append('')
    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, nargs='?')
    parser.add_argument('--markdown', type=Path, help='Write the complete per-movement route appendix')
    args = parser.parse_args()
    self_check()
    if args.directory:
        reports = [analyze(p) for p in sorted(args.directory.glob('*-[0-9]*.json'))]
        (args.directory / 'analysis.json').write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding='utf-8')
        if args.markdown:
            args.markdown.write_text(markdown(reports), encoding='utf-8', newline='\n')
        for report in reports:
            print(json.dumps({k: v for k, v in report.items() if k not in ('route', 'pauses', 'failureEvents')}, ensure_ascii=False))
