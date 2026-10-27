#!/usr/bin/env python3
"""
Generates SUPPLEMENTARY GeckoLib animations derived from the artist-supplied animation files.

The original files in src/main/resources/assets/fantasyweapons/animations/item/<weapon>.animation.json are never
modified. Extra animations are written to <weapon>_extra.animation.json and found by GeckoLib through
GeoModel#getAnimationResourceFallbacks.

Why they are needed
-------------------
* Infernochain: `transform` ends in the extended chainblade pose and `retract` starts from it, but there is no
  looping animation that HOLDS chainblade form between attacks. `chain_idle` is the final frame of `transform`
  with a gentle sway so the weapon stays a chainblade until it is retracted.
* Eclipse Reaper: `transform` goes LIGHT -> DARK. `idle_dark` holds the transformed pose (keeping the idle's
  continuous spinning parts), and `transform_reverse` is the exact transform played backwards for DARK -> LIGHT.
  Every other animation was made for the light form only: played in the dark form, the parts that the dark form
  lays out differently (the crescents, the moons, the ring) jumped between the two layouts at the start and end of
  each one. `<animation>_dark` is the same motion with each part kept in the dark idle's layout relative to the
  shaft, which the weapon is held by (see WeaponGeoModel#anchorGrip), and the whole weapon turned as the dark idle
  holds it, so a dark animation and the dark idle meet exactly.

Usage: python3 tools/gen_extra_animations.py
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons',
                    'animations', 'item')


def load(weapon):
    with open(os.path.join(ROOT, f'{weapon}.animation.json')) as f:
        return json.load(f)


def keys_sorted(channel):
    return sorted(channel.items(), key=lambda kv: float(kv[0]))


def value_at_end(channel):
    if not isinstance(channel, dict):
        return channel
    return keys_sorted(channel)[-1][1]


def value_at_start(channel):
    if not isinstance(channel, dict):
        return channel
    return keys_sorted(channel)[0][1]


def span(channel):
    """Largest per-axis variation of a channel over time."""
    if not isinstance(channel, dict):
        return 0.0
    vals = [v for _, v in channel.items() if isinstance(v, list)]
    if not vals:
        return 0.0
    return max(max(v[i] for v in vals) - min(v[i] for v in vals) for i in range(3))


def fmt(t):
    return f'{t:.4f}'


def write(weapon, animations):
    out = {
        'format_version': '1.8.0',
        'animations': animations,
        'geckolib_format_version': 2,
    }
    path = os.path.join(ROOT, f'{weapon}_extra.animation.json')
    with open(path, 'w') as f:
        json.dump(out, f, indent=1 if len(json.dumps(out)) < 200000 else None, separators=None if len(json.dumps(out)) < 200000 else (',', ':'))
    print('wrote', os.path.relpath(path), '->', ', '.join(animations))


def reverse(anim):
    length = float(anim['animation_length'])
    bones = {}
    for bone, channels in anim['bones'].items():
        nb = {}
        for ch, kf in channels.items():
            if isinstance(kf, dict):
                nb[ch] = {fmt(max(0.0, length - float(t))): v for t, v in kf.items()}
                nb[ch] = dict(sorted(nb[ch].items(), key=lambda kv: float(kv[0])))
            else:
                nb[ch] = kf
        bones[bone] = nb
    return {'animation_length': length, 'bones': bones}


# ----------------------------------------------------------------------------------------------------------------
# Bone transforms, as GeckoLib applies the animation file's values: position, then about the pivot the rotation
# Rz(-z) Ry(y) Rx(-x) (degrees) and the scale.
# ----------------------------------------------------------------------------------------------------------------

def _mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(len(b))) for j in range(len(b[0]))] for i in range(len(a))]


def _rot(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    if axis == 'x':
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == 'y':
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _euler_matrix(r):
    return _mat_mul(_mat_mul(_rot('z', -r[2]), _rot('y', r[1])), _rot('x', -r[0]))


def _affine(m3, t):
    return [m3[0] + [t[0]], m3[1] + [t[1]], m3[2] + [t[2]], [0, 0, 0, 1]]


def bone_matrix(rot, pos, scale, pivot):
    m3 = _mat_mul(_euler_matrix(rot), [[scale[0], 0, 0], [0, scale[1], 0], [0, 0, scale[2]]])
    shift = [pivot[i] - sum(m3[i][k] * pivot[k] for k in range(3)) for i in range(3)]
    return _affine(m3, [pos[i] + shift[i] for i in range(3)])


def invert(m):
    """Inverse of an affine transform whose 3x3 part is a rotation times a positive diagonal scale."""
    a = [row[:3] for row in m[:3]]
    sc = [math.sqrt(sum(a[r][c] ** 2 for r in range(3))) for c in range(3)]
    rinv = [[a[c][r] / (sc[r] ** 2) for c in range(3)] for r in range(3)]
    t = [m[i][3] for i in range(3)]
    return _affine(rinv, [-sum(rinv[i][k] * t[k] for k in range(3)) for i in range(3)])


def decompose(m, pivot, near):
    """Rotation (the Euler angles nearest {@code near}), position and scale that make {@code m} about {@code pivot}."""
    m3 = [row[:3] for row in m[:3]]
    sc = [math.sqrt(sum(m3[r][c] ** 2 for r in range(3))) for c in range(3)]
    r3 = [[m3[r][c] / sc[c] for c in range(3)] for r in range(3)]
    sy = max(-1.0, min(1.0, -r3[2][0]))
    y1 = math.degrees(math.asin(sy))
    cands = []
    for y in (y1, 180 - y1):
        cy = math.cos(math.radians(y))
        if abs(cy) < 1e-6:
            a, c = -near[0], math.degrees(math.atan2(-r3[0][1], r3[1][1])) + near[0]
        else:
            a = math.degrees(math.atan2(r3[2][1] / cy, r3[2][2] / cy))
            c = math.degrees(math.atan2(r3[1][0] / cy, r3[0][0] / cy))
        cands.append([-a, y, -c])
    def wrap(v, ref):
        return v - 360 * math.floor((v - ref + 180) / 360)
    best = None
    for cand in cands:
        e = [wrap(cand[i], near[i]) for i in range(3)]
        d = sum((e[i] - near[i]) ** 2 for i in range(3))
        if best is None or d < best[0]:
            best = (d, e)
    rot = best[1]
    pos = [m[i][3] - pivot[i] + sum(m3[i][k] * pivot[k] for k in range(3)) for i in range(3)]
    return rot, pos, sc


def channel_value(channel, t, default):
    if channel is None:
        return list(default)
    if not isinstance(channel, dict):
        return [float(v) for v in channel]
    frames = [(float(k), [float(x) for x in v]) for k, v in keys_sorted(channel)]
    if t <= frames[0][0]:
        return frames[0][1]
    for (t0, v0), (t1, v1) in zip(frames, frames[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return frames[-1][1]


def channel_times(channel):
    return [float(k) for k, _ in keys_sorted(channel)] if isinstance(channel, dict) else [0.0]


def pose(anim, bone, t, pivots):
    ch = anim['bones'].get(bone, {})
    return bone_matrix(channel_value(ch.get('rotation'), t, (0, 0, 0)), channel_value(ch.get('position'), t, (0, 0, 0)),
                       channel_value(ch.get('scale'), t, (1, 1, 1)), pivots[bone])


def is_identity(m, tol=1e-3):
    return all(abs(m[i][j] - (1.0 if i == j else 0.0)) < (tol if j < 3 else 0.05) for i in range(3) for j in range(4))


def turned(anim, w, pivots):
    """
    {@code anim} with the whole weapon carried by the rigid transform {@code w}, whose turn is a half turn about the
    weapon's long axis (the turn between the light and the dark idles). Each bone's angles and position follow from
    its own in a straight line, so GeckoLib's straight-line keying between keyframes carries over unchanged.
    """
    r3 = [row[:3] for row in w[:3]]
    assert all(abs(r3[i][j] - (-1 if i == j and i != 1 else 1 if i == j else 0)) < 1e-3 for i in range(3) for j in range(3)), r3
    t = [w[i][3] for i in range(3)]
    out = {k: v for k, v in anim.items() if k != 'bones'}
    out['bones'] = {}
    for bone, channels in anim['bones'].items():
        if bone not in pivots:
            out['bones'][bone] = channels
            continue
        piv = pivots[bone]
        times = sorted({round(x, 4) for ch in (channels.get('rotation'), channels.get('position')) if ch is not None for x in channel_times(ch)} | {0.0})
        nb = dict(channels)
        rot, pos = {}, {}
        for x in times:
            r = channel_value(channels.get('rotation'), x, (0, 0, 0))
            p = channel_value(channels.get('position'), x, (0, 0, 0))
            rot[fmt(x)] = [round(r[0], 4), round(r[1] + 180, 4), round(-r[2], 4)]
            pos[fmt(x)] = [round(-p[0] - 2 * piv[0] + t[0], 4), round(p[1] + t[1], 4), round(-p[2] - 2 * piv[2] + t[2], 4)]
        nb['rotation'], nb['position'] = rot, pos
        out['bones'][bone] = nb
    return out


def relayout(anim, grip, layout, pivots, near):
    """
    {@code anim} with each bone in {@code layout} (bone -> transform in the grip's frame) re-posed: the bone keeps its
    own motion relative to the grip, set in the new layout instead of the one the animation was made for. GeckoLib
    moves each angle in a straight line from one keyframe to the next, which a re-posed part would not follow, so
    such parts are keyed closer together wherever a straight line would stray from their motion. Parts held rigidly
    to the grip share their keyframe times with it (so they can be welded to it in the hand, see
    WeaponGeoModel#weldToGrip); parts that turn on their own (spinning rings, orbiting moons) get times of their own.
    """
    bones = [b for b in anim['bones'] if b in pivots]
    base = sorted({round(t, 4) for b in bones for ch in anim['bones'][b].values() for t in channel_times(ch)} | {0.0, float(anim['animation_length'])})

    def composite(b, t):
        g = pose(anim, grip, t, pivots)
        return _mat_mul(_mat_mul(_mat_mul(g, layout[b]), invert(g)), pose(anim, b, t, pivots))

    def strays(b, t0, t1, r0):
        """Whether keying {@code b} in a straight line from {@code t0} to {@code t1} strays from its motion."""
        a = decompose(composite(b, t0), pivots[b], r0)
        c = decompose(composite(b, t1), pivots[b], a[0])
        if max(abs(x - y) for x, y in zip(a[0], c[0])) > 40:
            return True
        for f in (0.25, 0.5, 0.75):
            tm = t0 + (t1 - t0) * f
            lerp = [[x + (y - x) * f for x, y in zip(a[i], c[i])] for i in range(3)]
            m = bone_matrix(lerp[0], lerp[1], lerp[2], pivots[b])
            want = composite(b, tm)
            if max(abs(m[i][j] - want[i][j]) * (1 if j == 3 else 16) for i in range(3) for j in range(4)) > 0.15:
                return True
        return False

    def refine(times, group):
        for _ in range(8):
            extra = []
            for t0, t1 in zip(times, times[1:]):
                if t1 - t0 >= 0.0015 and any(strays(b, t0, t1, near.get(b, [0.0, 0.0, 0.0])) for b in group):
                    extra.append(round((t0 + t1) / 2, 4))
            if not extra:
                break
            times = sorted(set(times) | set(extra))
        return times

    def rigid(b):
        rel0 = _mat_mul(invert(pose(anim, grip, 0.0, pivots)), pose(anim, b, 0.0, pivots))
        for t in base:
            rel = _mat_mul(invert(pose(anim, grip, t, pivots)), pose(anim, b, t, pivots))
            if max(abs(rel[i][j] - rel0[i][j]) for i in range(3) for j in range(3)) > 0.01:
                return False
        return True

    moving = [b for b in bones if b in layout]
    held = [b for b in moving if rigid(b)]
    shared = refine(base, held)
    grids = {b: (shared if b in held else refine(shared, [b])) for b in moving}

    out = {'animation_length': anim['animation_length'], 'bones': {}}
    if 'loop' in anim:
        out['loop'] = anim['loop']
    for bone, channels in anim['bones'].items():
        if bone not in pivots:
            out['bones'][bone] = channels
            continue
        if bone not in layout:
            if len(shared) == len(base):
                out['bones'][bone] = channels
                continue
            nb = {}
            for name, ch in channels.items():
                default = (1, 1, 1) if name == 'scale' else (0, 0, 0)
                nb[name] = {fmt(t): [round(v, 4) for v in channel_value(ch, t, default)] for t in shared}
            out['bones'][bone] = nb
            continue
        rot, pos, scale = {}, {}, {}
        prev = near.get(bone, [0.0, 0.0, 0.0])
        for t in grids[bone]:
            r, p, sc = decompose(composite(bone, t), pivots[bone], prev)
            prev = r
            rot[fmt(t)] = [round(v, 4) for v in r]
            pos[fmt(t)] = [round(v, 4) for v in p]
            scale[fmt(t)] = [round(v, 4) for v in sc]
        nb = {'rotation': rot, 'position': pos}
        if any(abs(v - 1) > 1e-4 for frame in scale.values() for v in frame):
            nb['scale'] = scale
        out['bones'][bone] = nb
    return out


def infernochain():
    src = load('infernochain')['animations']
    transform = src['animation.infernochain.transform']
    idle = src['animation.infernochain.idle']
    length, step = 4.0, 0.25
    bones = {}
    for bone, channels in transform['bones'].items():
        nb = {}
        phase = sum(ord(c) for c in bone) % 7 * 0.6
        is_segment = bone.startswith('segment_') or bone.startswith('chain_link_') or bone.startswith('lock_')
        for ch, kf in channels.items():
            end = value_at_end(kf)
            if not isinstance(end, list):
                nb[ch] = end
                continue
            if ch == 'rotation' and is_segment:
                frames = {}
                t = 0.0
                while t <= length + 1e-6:
                    sway = 2.5 * math.sin(2 * math.pi * t / length + phase)
                    frames[fmt(t)] = [end[0], end[1] + sway * 0.6, end[2] + sway]
                    t += step
                nb[ch] = frames
            elif ch == 'rotation' and bone == 'drive_gear':
                nb[ch] = {fmt(0.0): end, fmt(length): [end[0], end[1], end[2] + 160.0]}
            else:
                nb[ch] = {fmt(0.0): end, fmt(length): end}
        bones[bone] = nb
    # bones only animated by idle keep their idle motion (e.g. glowing parts that pulse)
    for bone, channels in idle['bones'].items():
        if bone not in bones:
            bones[bone] = channels
    write('infernochain', {
        'animation.infernochain.chain_idle': {'loop': True, 'animation_length': length, 'bones': bones},
    })


def eclipse_reaper():
    src = load('eclipse_reaper')['animations']
    transform = src['animation.eclipse_reaper.transform']
    idle = src['animation.eclipse_reaper.idle']
    length = float(idle['animation_length'])
    dark = {}
    for bone in set(idle['bones']) | set(transform['bones']):
        idle_ch = idle['bones'].get(bone, {})
        tr_ch = transform['bones'].get(bone, {})
        nb = {}
        for ch in set(idle_ch) | set(tr_ch):
            if ch in idle_ch and span(idle_ch[ch]) > 5.0:
                nb[ch] = idle_ch[ch]  # continuously moving part (spinning ring/corona): keep idle motion
            elif ch in tr_ch:
                end = value_at_end(tr_ch[ch])
                nb[ch] = {fmt(0.0): end, fmt(length): end}
            else:
                nb[ch] = idle_ch[ch]
        dark[bone] = nb
    idle_dark = {'loop': True, 'animation_length': length, 'bones': dark}
    out = {
        'animation.eclipse_reaper.idle_dark': idle_dark,
        'animation.eclipse_reaper.transform_reverse': reverse(transform),
    }
    # the dark form's layout relative to the shaft, against the light form's: what each part changes by
    with open(os.path.join(ROOT, '..', '..', 'geo', 'item', 'eclipse_reaper.geo.json')) as f:
        geo = json.load(f)['minecraft:geometry'][0]
    pivots = {b['name']: [float(v) for v in b.get('pivot', [0, 0, 0])] for b in geo['bones']}
    grip = 'shaft'
    layout = {}
    for bone in idle['bones']:
        if bone == grip or bone not in pivots:
            continue
        light = _mat_mul(invert(pose(idle, grip, 0.0, pivots)), pose(idle, bone, 0.0, pivots))
        dark_rel = _mat_mul(invert(pose(idle_dark, grip, 0.0, pivots)), pose(idle_dark, bone, 0.0, pivots))
        k = _mat_mul(dark_rel, invert(light))
        if not is_identity(k):
            layout[bone] = k
    # the dark idle holds the whole weapon half a turn round from the light one (and a touch higher): each dark
    # version is carried round alike, so it starts and ends with every part exactly where the dark idle has it, and
    # a blend between them moves nothing
    whole = _mat_mul(pose(idle_dark, grip, 0.0, pivots), invert(pose(idle, grip, 0.0, pivots)))
    near = {bone: channel_value(ch.get('rotation'), 0.0, (0, 0, 0)) for bone, ch in idle['bones'].items()}
    for name, anim in src.items():
        short = name.split('.')[-1]
        if short in ('idle', 'transform'):
            continue
        out[name + '_dark'] = turned(relayout(anim, grip, layout, pivots, near), whole, pivots)
    print('eclipse_reaper dark layout differs for', ', '.join(sorted(layout)))
    write('eclipse_reaper', out)


if __name__ == '__main__':
    infernochain()
    eclipse_reaper()
