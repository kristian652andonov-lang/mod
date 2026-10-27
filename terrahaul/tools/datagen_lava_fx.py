#!/usr/bin/env python3
"""Generates the sprites of Terrahaul's client-side lava smoke and their particle description.

Usage: python3 tools/datagen_lava_fx.py
Needs Pillow. Everything is written to src/main/resources/assets/terrahaul and committed.

The sprites are original and procedural, drawn from a fixed random seed, so re-running the script
without changes produces identical files. They are white (the particle tints them dark grey and
warms them over the lava): billowing puffs of smoke, each a cluster of round lobes with a lit top
and a shaded underside and creases between the lobes, so a column of them reads as rolling smoke
rather than as the smooth blobs of the waterfall mist.
"""
import json
import math
import os
import random

from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'terrahaul')


def write_json(path, obj):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save_texture(img, name):
    full = os.path.join(ASSETS, 'textures', 'particle', name + '.png')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def smoke_sprites():
    """Puffs of smoke, 32x32 (a puff is drawn up to nine blocks wide, so its pixels must stay
    small). Each is a cluster of seven to eleven soft lobes around a larger middle one, nothing within three
    pixels of the edge, so the quad's edge never shows. Alpha falls from a dense core to a soft rim
    (the smoke has the mist's shader, which keeps the faintest rim); brightness is highest on the
    lobes' upper left, lowest on their undersides and in the creases where lobes meet, which gives
    the puff its rolling, lumpy look once it is tinted grey."""
    rnd = random.Random(83)
    size = 32
    centre = (size - 1) / 2.0
    for i in range(4):
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        lobes = []
        for k in range(7 + i % 2 + rnd.randint(0, 3)):
            angle = rnd.uniform(0, 2 * math.pi)
            dist = rnd.uniform(1.5, 7.0) if k else 0.0
            lobes.append((centre + math.cos(angle) * dist, centre + math.sin(angle) * dist * 0.85, rnd.uniform(4.2, 7.0) if k else 7.5))
        for y in range(size):
            for x in range(size):
                density = 0.0
                light = 0.0
                weight = 0.0
                crease = 0.0
                for lx, ly, lr in lobes:
                    d = math.hypot(x - lx, y - ly) / lr
                    if d >= 1.0:
                        continue
                    w = 1.0 - d * d
                    density = max(density, w)
                    # the lobe's surface faces up and to the left where it is lit
                    nx = (x - lx) / lr
                    ny = (y - ly) / lr
                    light += w * (0.5 - 0.35 * nx - 0.55 * ny)
                    weight += w
                    # a crease where two lobes meet at similar strength
                    crease += w
                if density <= 0.0:
                    continue
                # the creases: overlapping lobes add up past the strongest of them
                crease = max(0.0, min(1.0, (crease - density) / 0.9))
                shade = 0.66 + 0.3 * max(0.0, min(1.0, light / max(weight, 1e-6) + 0.35)) - 0.18 * crease
                edge = min(x, y, size - 1 - x, size - 1 - y)
                fade = max(0.0, min(1.0, (edge - 2) / 3.0))
                a = int(round(235 * min(1.0, density * 1.2) ** 1.25 * fade / 4.0)) * 4
                if a:
                    v = int(round(255 * max(0.0, min(1.0, shade))))
                    img.putpixel((x, y), (v, v, v, a))
        save_texture(img, f'lava_smoke_{i}')


def particle_descriptions():
    write_json('particles/lava_smoke.json', {"textures": [f"terrahaul:lava_smoke_{i}" for i in range(4)]})


def main():
    smoke_sprites()
    particle_descriptions()


if __name__ == '__main__':
    main()
