#!/usr/bin/env python3
"""Writes the GameTest arena structure (data/fantasyweapons/structure/arena.nbt): a 24x8x24 box with a stone floor."""
import gzip
import io
import os
import struct

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'fantasyweapons', 'structure', 'arena.nbt')
SX, SY, SZ = 24, 8, 24
DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def w_str(buf, s):
    b = s.encode('utf-8')
    buf.write(struct.pack('>H', len(b)))
    buf.write(b)


def w_named(buf, tag, name):
    buf.write(struct.pack('>b', tag))
    w_str(buf, name)


def w_payload(buf, tag, value):
    if tag == TAG_INT:
        buf.write(struct.pack('>i', value))
    elif tag == TAG_STRING:
        w_str(buf, value)
    elif tag == TAG_LIST:
        elem_tag, items = value
        buf.write(struct.pack('>b', elem_tag if items else TAG_END))
        buf.write(struct.pack('>i', len(items)))
        for it in items:
            w_payload(buf, elem_tag, it)
    elif tag == TAG_COMPOUND:
        for name, (t, v) in value.items():
            w_named(buf, t, name)
            w_payload(buf, t, v)
        buf.write(struct.pack('>b', TAG_END))


def main():
    palette = [{'Name': (TAG_STRING, 'minecraft:smooth_stone')}, {'Name': (TAG_STRING, 'minecraft:air')}]
    blocks = []
    for x in range(SX):
        for y in range(SY):
            for z in range(SZ):
                blocks.append({'pos': (TAG_LIST, (TAG_INT, [x, y, z])), 'state': (TAG_INT, 0 if y == 0 else 1)})
    root = {
        'DataVersion': (TAG_INT, DATA_VERSION),
        'size': (TAG_LIST, (TAG_INT, [SX, SY, SZ])),
        'palette': (TAG_LIST, (TAG_COMPOUND, palette)),
        'blocks': (TAG_LIST, (TAG_COMPOUND, blocks)),
        'entities': (TAG_LIST, (TAG_COMPOUND, [])),
    }
    raw = io.BytesIO()
    w_named(raw, TAG_COMPOUND, '')
    w_payload(raw, TAG_COMPOUND, root)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, 'wb') as f:
        f.write(raw.getvalue())
    print('wrote', os.path.relpath(OUT), len(blocks), 'blocks')


if __name__ == '__main__':
    main()
