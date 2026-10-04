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
        json.dump(out, f, indent=1)
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
    write('eclipse_reaper', {
        'animation.eclipse_reaper.idle_dark': {'loop': True, 'animation_length': length, 'bones': dark},
        'animation.eclipse_reaper.transform_reverse': reverse(transform),
    })


if __name__ == '__main__':
    infernochain()
    eclipse_reaper()
