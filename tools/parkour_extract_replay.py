"""Export an unmodified, contiguous walking trace for offline physics regression."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--start-tick', type=int, required=True)
    parser.add_argument('--end-tick', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    samples = []
    with args.log.open(encoding='utf-8') as stream:
        for line in stream:
            sample = json.loads(line)
            if sample.get('type') == 'tick' and args.start_tick <= sample['clientTick'] <= args.end_tick:
                if sample['flying'] or sample['discontinuitySuspected']:
                    raise ValueError('Replay contains flight or a discontinuity')
                fields = ('clientTick', 'positionExact', 'motion', 'rotation', 'input', 'sprinting',
                          'sneaking', 'onGround', 'collidedHorizontally', 'movementSpeedAttribute')
                samples.append({field: sample[field] for field in fields})
    expected = list(range(args.start_tick, args.end_tick + 1))
    if [sample['clientTick'] for sample in samples] != expected:
        raise ValueError('Replay must contain every requested tick exactly once')
    data = {'samples': samples}
    with args.output.open('x', encoding='utf-8') as destination:
        json.dump(data, destination, ensure_ascii=False, indent=2)
        destination.write('\n')
    print(f'Saved {len(samples)} samples: {args.output}')


if __name__ == '__main__':
    main()
