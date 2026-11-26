#!/usr/bin/env python3
"""Summarize executed ORT kernel events by provider and operator."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path


def summarize(path):
    events = json.loads(path.read_text())
    counts = Counter()
    operations = defaultdict(Counter)
    for event in events:
        if event.get('cat') != 'Node' or not event.get('name', '').endswith('_kernel_time'):
            continue
        args = event.get('args', {})
        provider = args.get('provider')
        if not provider:
            continue
        counts[provider] += 1
        operations[provider][args.get('op_name', '?')] += 1
    return {'profile': path.name, 'kernel_events': dict(counts),
            'operations': {provider: dict(ops) for provider, ops in operations.items()}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('profiles', type=Path, nargs='+')
    args = parser.parse_args()
    print(json.dumps([summarize(path) for path in args.profiles], indent=2))
