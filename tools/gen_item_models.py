#!/usr/bin/env python3
"""
Writes assets/fantasyweapons/models/item/<weapon>.json for every weapon: a builtin/entity model (GeckoLib renders
it) with display transforms computed from the artist's geometry.

The artist's models put the grip at the origin with the blade along +Y, and are life-size (several blocks long), so:
  * first/third person reuse vanilla's handheld poses, pre-rotated 45 degrees (vanilla sprites are diagonal) and
    translated so the grip sits in the hand; the size is clamped so colossal weapons stay readable;
  * GUI is a diagonal, fitted to the slot; FIXED (item frames + the progression-menu preview) is upright and centred.

Bones that the idle animation hides (scale ~0, e.g. Voidfang's dimension rift) are excluded from the bounds.

Usage: python3 tools/gen_item_models.py
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons')
WEAPONS = ['voidfang', 'solaris', 'frostrend', 'doomcleaver', 'stormbreaker', 'gravebite', 'soulreaper', 'bloomfall',
           'eclipse_reaper', 'starforge', 'aetherlance', 'monolith', 'infernochain']


def hidden_bones(weapon):
    with open(os.path.join(ROOT, 'animations', 'item', f'{weapon}.animation.json')) as f:
        anims = json.load(f)['animations']
    idle = next(a for n, a in anims.items() if n.endswith('.idle'))
    hidden = set()
    for bone, ch in idle['bones'].items():
        sc = ch.get('scale')
        if isinstance(sc, dict):
            vals = [max(v) if isinstance(v, list) else v for v in sc.values() if not isinstance(v, dict)]
            if vals and max(vals) < 0.01:
                hidden.add(bone)
    return hidden


def bounds(weapon):
    with open(os.path.join(ROOT, 'geo', 'item', f'{weapon}.geo.json')) as f:
        geo = json.load(f)['minecraft:geometry'][0]
    skip = hidden_bones(weapon)
    xs, ys, zs = [], [], []
    for bone in geo['bones']:
        if bone['name'] in skip:
            continue
        for c in bone.get('cubes', []):
            o, s = c['origin'], c['size']
            # json space has X mirrored relative to render space
            xs += [-(o[0] + s[0]), -o[0]]
            ys += [o[1], o[1] + s[1]]
            zs += [o[2], o[2] + s[2]]
    return [min(xs) / 16, max(xs) / 16], [min(ys) / 16, max(ys) / 16], [min(zs) / 16, max(zs) / 16]


def rot_xyz(rx, ry, rz):
    """Rotation matrix for Minecraft display rotations (applied as Rx * Ry * Rz)."""
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(mul(Rx, Ry), Rz)


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def fit(bx, by, bz, rot, target):
    """Scale + translation (px) that centres the rotated bounds and fits them in `target` blocks."""
    m = rot_xyz(*rot)
    corners = [apply(m, [x, y, z]) for x in bx for y in by for z in bz]
    w = max(c[0] for c in corners) - min(c[0] for c in corners)
    h = max(c[1] for c in corners) - min(c[1] for c in corners)
    s = min(4.0, target / max(w, h))
    center = apply(m, [(bx[0] + bx[1]) / 2, (by[0] + by[1]) / 2, (bz[0] + bz[1]) / 2])
    t = [-center[0] * s * 16, -center[1] * s * 16, -center[2] * s * 16]
    return s, t


def r(v, n=3):
    return [round(x, n) for x in v]


# In-hand size: one uniform scale keeps the artist's relative sizes (Monolith really is twice Voidfang), capped so the
# longest weapons stay manageable. Voidfang ends up ~2.7 blocks (about 1.5x a player's height).
TP_SCALE, TP_MAX_LENGTH = 0.65, 5.0
# First person: big but readable; longer weapons get a little longer on screen.
FP_BASE, FP_PER_BLOCK = 1.05, 0.11


def model(weapon):
    bx, by, bz = bounds(weapon)
    length = by[1] - by[0]
    tp = min(TP_SCALE, TP_MAX_LENGTH / length)
    fp = (FP_BASE + FP_PER_BLOCK * length) / length
    gui_rot = [0, 20, -45]
    gui_s, gui_t = fit(bx, by, bz, gui_rot, 0.95)
    fixed_s, fixed_t = fit(bx, by, bz, [0, 0, 0], 0.95)
    ground_s = min(0.5, 1.6 / length)
    return {
        'parent': 'builtin/entity',
        'gui_light': 'front',
        'display': {
            # vanilla handheld pose (pre-rotated 45 deg because vanilla sprites are diagonal) plus a -45 deg X tilt so the
            # blade is raised ~55 deg instead of pointing straight ahead; grip stays in the hand
            'thirdperson_righthand': {'rotation': [-45, -90, 10], 'translation': [0, -1.92, 1.54], 'scale': [round(tp, 4)] * 3},
            'thirdperson_lefthand': {'rotation': [-45, 90, -10], 'translation': [0, -1.92, 1.54], 'scale': [round(tp, 4)] * 3},
            # first person: blade rises up-left towards the centre of the screen, flat side mostly towards the camera
            'firstperson_righthand': {'rotation': [-35, 20, 25], 'translation': [2.0, -2.5, 0], 'scale': [round(fp, 4)] * 3},
            'firstperson_lefthand': {'rotation': [-35, -20, -25], 'translation': [2.0, -2.5, 0], 'scale': [round(fp, 4)] * 3},
            'gui': {'rotation': gui_rot, 'translation': r(gui_t), 'scale': [round(gui_s, 4)] * 3},
            'fixed': {'rotation': [0, 0, 0], 'translation': r(fixed_t), 'scale': [round(fixed_s, 4)] * 3},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [round(ground_s, 4)] * 3},
            'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        },
    }


def main():
    out_dir = os.path.join(ROOT, 'models', 'item')
    os.makedirs(out_dir, exist_ok=True)
    for w in WEAPONS:
        m = model(w)
        with open(os.path.join(out_dir, f'{w}.json'), 'w') as f:
            json.dump(m, f, indent=2)
        bx, by, _ = bounds(w)
        print(f'{w:15s} length {by[1] - by[0]:.2f} blocks  tp {m["display"]["thirdperson_righthand"]["scale"][0]:.3f}'
              f'  fp {m["display"]["firstperson_righthand"]["scale"][0]:.3f}  gui {m["display"]["gui"]["scale"][0]:.3f}')


if __name__ == '__main__':
    main()
