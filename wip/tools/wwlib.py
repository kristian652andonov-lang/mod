#!/usr/bin/env python3
"""Reads the blocks of a saved world around a point and replays the water effect scanner on them:
falling-water columns (foot, height, landing, open sides, lip direction), running water, and how
often the foam and splash spawn positions would be accepted.

Usage: worldwater.py <region dir> <x> <z> <radius>
"""
import math
import os
import struct
import sys
import zlib
from collections import Counter


def read_nbt(buf, pos=0):
    def read_tag(t, pos):
        if t == 1:
            return struct.unpack_from('>b', buf, pos)[0], pos + 1
        if t == 2:
            return struct.unpack_from('>h', buf, pos)[0], pos + 2
        if t == 3:
            return struct.unpack_from('>i', buf, pos)[0], pos + 4
        if t == 4:
            return struct.unpack_from('>q', buf, pos)[0], pos + 8
        if t == 5:
            return struct.unpack_from('>f', buf, pos)[0], pos + 4
        if t == 6:
            return struct.unpack_from('>d', buf, pos)[0], pos + 8
        if t == 7:
            n = struct.unpack_from('>i', buf, pos)[0]
            return buf[pos + 4:pos + 4 + n], pos + 4 + n
        if t == 8:
            n = struct.unpack_from('>H', buf, pos)[0]
            return buf[pos + 2:pos + 2 + n].decode('utf-8', 'replace'), pos + 2 + n
        if t == 9:
            et, n = struct.unpack_from('>bi', buf, pos)
            pos += 5
            out = []
            for _ in range(n):
                v, pos = read_tag(et, pos)
                out.append(v)
            return out, pos
        if t == 10:
            out = {}
            while True:
                tt = buf[pos]
                pos += 1
                if tt == 0:
                    return out, pos
                n = struct.unpack_from('>H', buf, pos)[0]
                name = buf[pos + 2:pos + 2 + n].decode('utf-8', 'replace')
                pos += 2 + n
                v, pos = read_tag(tt, pos)
                out[name] = v
        if t == 11:
            n = struct.unpack_from('>i', buf, pos)[0]
            return list(struct.unpack_from('>%di' % n, buf, pos + 4)), pos + 4 + 4 * n
        if t == 12:
            n = struct.unpack_from('>i', buf, pos)[0]
            return list(struct.unpack_from('>%dq' % n, buf, pos + 4)), pos + 4 + 8 * n
        raise ValueError(t)
    t = buf[pos]
    n = struct.unpack_from('>H', buf, pos + 1)[0]
    v, _ = read_tag(t, pos + 3 + n)
    return v


class World:
    def __init__(self, region_dir):
        self.dir = region_dir
        self.chunks = {}

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        rx, rz = cx >> 5, cz >> 5
        path = os.path.join(self.dir, 'r.%d.%d.mca' % (rx, rz))
        sections = None
        if os.path.exists(path):
            with open(path, 'rb') as f:
                data = f.read()
            i = 4 * ((cx & 31) + (cz & 31) * 32)
            off = int.from_bytes(data[i:i + 3], 'big')
            if off:
                start = off * 4096
                length = struct.unpack_from('>i', data, start)[0]
                comp = data[start + 4]
                raw = data[start + 5:start + 4 + length]
                nbt = read_nbt(zlib.decompress(raw) if comp == 2 else raw)
                if nbt.get('Status', '').endswith('full'):
                    sections = {}
                    for s in nbt.get('sections', []):
                        bs = s.get('block_states')
                        if not bs:
                            continue
                        palette = bs['palette']
                        sections[s['Y']] = (palette, bs.get('data'))
        self.chunks[key] = sections
        return sections

    def block(self, x, y, z):
        sections = self.chunk(x >> 4, z >> 4)
        if sections is None:
            return None
        sec = sections.get(y >> 4)
        if sec is None:
            return {'Name': 'minecraft:air'}
        palette, data = sec
        if data is None:
            return palette[0]
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        idx = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        word = data[idx // per] & 0xFFFFFFFFFFFFFFFF
        v = (word >> ((idx % per) * bits)) & ((1 << bits) - 1)
        return palette[v]


SOLID_PASS = ('air', 'cave_air', 'water', 'lava', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'vine', 'seagrass',
              'tall_seagrass', 'kelp', 'kelp_plant', 'lily_pad', 'snow', 'dead_bush', 'glow_lichen', 'moss_carpet', 'sugar_cane',
              'dandelion', 'poppy', 'torch', 'bamboo', 'cobweb')


def fluid(b):
    """(kind, amount, falling): kind None/'source'/'flowing'."""
    if b is None:
        return (None, 0, False)
    name = b['Name'].split(':')[1]
    props = b.get('Properties', {})
    if name == 'water':
        lv = int(props.get('level', '0'))
        if lv == 0:
            return ('source', 8, False)
        if lv >= 8:
            return ('flowing', 8, True)
        return ('flowing', 8 - lv, False)
    if props.get('waterlogged') == 'true' or name in ('seagrass', 'tall_seagrass', 'kelp', 'kelp_plant', 'bubble_column'):
        return ('source', 8, False)
    return (None, 0, False)


def is_air(b):
    return b is not None and b['Name'].split(':')[1] in ('air', 'cave_air')


def blocks_motion(b):
    if b is None:
        return True
    name = b['Name'].split(':')[1]
    if name in SOLID_PASS or name.endswith('_flower') or name.endswith('sapling') or 'carpet' in name or name.endswith('_button'):
        return False
    if 'leaves' in name:
        return True
    return True


DX = [0, 1, 0, -1]
DZ = [-1, 0, 1, 0]


def scan(world, x0, z0, r, ymin, ymax):
    columns = []
    running = []
    for x in range(x0 - r, x0 + r + 1):
        for z in range(z0 - r, z0 + r + 1):
            if world.chunk(x >> 4, z >> 4) is None:
                continue
            for y in range(ymin, ymax):
                b = world.block(x, y, z)
                k, amt, fall = fluid(b)
                if k != 'flowing':
                    continue
                if fall:
                    bk, bamt, bfall = fluid(world.block(x, y - 1, z))
                    if bk == 'flowing' and bfall:
                        continue
                    top = y
                    while True:
                        tk, _, tf = fluid(world.block(x, top + 1, z))
                        if tk == 'flowing' and tf:
                            top += 1
                        else:
                            break
                    h = top - y + 1
                    below = world.block(x, y - 1, z)
                    if bk is not None:
                        base, surface = 'pool', y - 1 + bamt / 9.0
                    elif blocks_motion(below):
                        base, surface = 'ground', y
                    else:
                        continue
                    lip = world.block(x, top + 1, z)
                    lk, lamt, _ = fluid(lip)
                    mid = y + h // 2
                    open_ = []
                    for i in range(4):
                        s = world.block(x + DX[i], mid, z + DZ[i])
                        if is_air(s) or (not blocks_motion(s) and fluid(s)[0] is None):
                            open_.append(i)
                    columns.append(dict(x=x, y=y, z=z, h=h, base=base, surface=surface, lip=lk, open=open_))
                else:
                    if is_air(world.block(x, y + 1, z)):
                        running.append((x, y, z, amt))
    return columns, running


def surface_at(world, x, near_y, z):
    px, py, pz = math.floor(x), math.floor(near_y - 0.05), math.floor(z)
    for _ in range(2):
        k, amt, fall = fluid(world.block(px, py, pz))
        if k is not None:
            above = world.block(px, py + 1, pz)
            if fluid(above)[0] is None and not blocks_motion(above):
                return py + amt / 9.0
            return None
        py -= 1
    return None


def main():
    region, x0, z0, r = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    ymin = int(sys.argv[5]) if len(sys.argv) > 5 else 60
    ymax = int(sys.argv[6]) if len(sys.argv) > 6 else 260
    world = World(region)
    columns, running = scan(world, x0, z0, r, ymin, ymax)
    print('columns', len(columns), 'running cells', len(running))
    print('heights', sorted(Counter(c['h'] for c in columns).items()))
    print('bases', Counter(c['base'] for c in columns), 'lips', Counter(c['lip'] for c in columns),
          'open counts', Counter(len(c['open']) for c in columns))
    # foam spawn acceptance: random positions around each column like poolFoam (radial, no direction)
    import random
    rnd = random.Random(1)
    acc = tot = 0
    reasons = Counter()
    for c in columns:
        if c['base'] != 'pool':
            continue
        for _ in range(20):
            along = 0.6 + rnd.random() * (1.0 + min(c['h'], 60) * 0.03)
            side = (rnd.random() - 0.5) * 1.4
            a = rnd.random() * 2 * math.pi
            ax, az = math.cos(a), math.sin(a)
            x = c['x'] + 0.5 + ax * along - az * side
            z = c['z'] + 0.5 + az * along + ax * side
            tot += 1
            s = surface_at(world, x, c['surface'], z)
            if s is None:
                b = world.block(math.floor(x), math.floor(c['surface'] - 0.05), math.floor(z))
                a2 = world.block(math.floor(x), math.floor(c['surface'] - 0.05) + 1, math.floor(z))
                reasons[(b['Name'].split(':')[1] if b else None, a2['Name'].split(':')[1] if a2 else None,
                         fluid(a2)[0] if a2 else None)] += 1
            else:
                acc += 1
    print('foam spawn acceptance (random directions): %d of %d' % (acc, tot))
    for k, v in reasons.most_common(8):
        print('   rejected at (block, above, above fluid)', k, v)
    for c in sorted(columns, key=lambda c: -c['h'])[:25]:
        print('  col', c['x'], c['y'], c['z'], 'h', c['h'], c['base'], 'surf %.2f' % c['surface'], 'lip', c['lip'], 'open', c['open'])


if __name__ == '__main__':
    main()
