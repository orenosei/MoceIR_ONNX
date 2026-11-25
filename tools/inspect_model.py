#!/usr/bin/env python3
"""Read-only ONNX wire-format audit; no model rewriting or third-party dependency."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def varint(data, position):
    value = shift = 0
    while True:
        byte = data[position]
        position += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, position
        shift += 7


def fields(data):
    position = 0
    while position < len(data):
        key, position = varint(data, position)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, position = varint(data, position)
        elif wire == 2:
            size, position = varint(data, position)
            value = data[position:position + size]
            position += size
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            value = data[position:position + size]
            position += size
        else:
            raise ValueError(f"Unsupported protobuf wire type: {wire}")
        yield number, value


def first(data, tag, default=None):
    return next((value for number, value in fields(data) if number == tag), default)


def inspect(path):
    data = path.read_bytes()
    graph = first(data, 7)
    counts = Counter()
    dft = Counter()
    for tag, node in fields(graph):
        if tag != 1:
            continue
        domain = first(node, 7, b'').decode()
        op = first(node, 4).decode()
        counts[f"{domain or 'ai.onnx'}:{op}"] += 1
        if op == 'DFT':
            attrs = {first(value, 1).decode(): first(value, 3)
                     for number, value in fields(node) if number == 5}
            dft[json.dumps(attrs, sort_keys=True)] += 1
    result = {'model': str(path), 'sha256': hashlib.sha256(data).hexdigest(),
              'total_nodes': sum(counts.values()), 'operators': dict(sorted(counts.items())),
              'dft_attributes': dict(dft)}
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('model', nargs='?', type=Path,
                        default=Path('app/src/main/assets/models/moce_ir_s_aio5.onnx'))
    args = parser.parse_args()
    print(json.dumps(inspect(args.model), indent=2))
