#!/usr/bin/env python3
"""
Material textures for custom VFX meshes (RGBA, tinted at runtime): ice crystal, rock, bark/vine.
Usage: python3 tools/gen_materials.py
"""
import numpy as np

from gen_textures import tileable_noise, coords, save_rgba


def ice():
    """
    Ice crystal for spike meshes. v runs along the spike (top row = tip, bottom row = base), u across one facet.
    Clear, cold body with a bright rim on the facet edges, a specular stripe, internal fracture planes, trapped air
    bubbles and white rime where it breaks out of the ground.
    """
    from PIL import Image, ImageDraw, ImageFilter
    import random
    w, h = 128, 256
    rng = random.Random(4242)
    u, v = coords(w, h)
    n = tileable_noise(256, octaves=5, seed=111)[:h, :w]
    n2 = tileable_noise(256, octaves=3, seed=112, base=8)[:h, :w]
    edge = np.abs(u - 0.5) * 2                                   # 0 centre of the facet .. 1 facet edge
    rim = np.clip((edge - 0.78) / 0.22, 0, 1) ** 1.5             # bright crystal edges
    spec = np.exp(-((u - 0.3 - 0.05 * (1 - v)) ** 2) / 0.0035) * (0.4 + 0.6 * (1 - v))
    depth = 0.55 + 0.45 * (1 - v)                                # deep at the base, bright towards the tip
    lum = depth * (0.82 + 0.12 * n) + 0.35 * rim + 0.45 * spec

    # fracture planes: thin bright lines with a soft glow, more of them near the base
    cr = Image.new('L', (w * 4, h * 4), 0)
    d = ImageDraw.Draw(cr)
    for _ in range(14):
        y0 = h * 4 * (0.3 + 0.7 * rng.random() ** 0.6)
        x0 = rng.uniform(0, w * 4)
        ang = rng.uniform(-1.2, -0.3) if rng.random() < 0.5 else rng.uniform(0.3, 1.2)
        length = rng.uniform(120, 320)
        pts = [(x0, y0)]
        for _k in range(4):
            x0 += np.cos(ang) * length / 4
            y0 -= abs(np.sin(ang)) * length / 4
            ang += rng.uniform(-0.5, 0.5)
            pts.append((x0, y0))
        d.line(pts, fill=rng.randint(130, 255), width=rng.choice((2, 3, 4)))
    cracks = np.asarray(cr.resize((w, h), Image.LANCZOS)) / 255.0
    crack_glow = np.asarray(cr.filter(ImageFilter.GaussianBlur(10)).resize((w, h), Image.LANCZOS)) / 255.0
    lum += 0.55 * cracks + 0.25 * crack_glow

    # trapped air bubbles in the lower half
    bub = Image.new('L', (w * 4, h * 4), 0)
    d = ImageDraw.Draw(bub)
    for _ in range(60):
        x, y = rng.uniform(0, w * 4), h * 4 * (0.45 + 0.55 * rng.random())
        r = rng.uniform(2, 7)
        d.ellipse([x - r, y - r, x + r, y + r], outline=200, width=2)
    bubbles = np.asarray(bub.resize((w, h), Image.LANCZOS)) / 255.0
    lum += 0.3 * bubbles

    # frosty rime at the base
    rime = np.clip((v - 0.84) / 0.16, 0, 1) * (0.55 + 0.45 * (n2 > 0.48))
    lum = lum * (1 - 0.6 * rime) + 1.15 * rime

    alpha = np.clip(0.5 + 0.18 * (1 - v) + 0.4 * rim + 0.35 * cracks + 0.3 * spec + 0.5 * rime + 0.1 * (n - 0.5), 0, 1)
    lum = np.clip(lum, 0, 1.15)
    arr = np.zeros((h, w, 4))
    arr[..., 0] = np.clip(lum * 205 + 40 * rim + 50 * rime, 0, 255)
    arr[..., 1] = np.clip(lum * 238 + 20 * rim + 20 * rime, 0, 255)
    arr[..., 2] = np.clip(lum * 255, 0, 255)
    arr[..., 3] = alpha * 255
    save_rgba('vfx/ice.png', arr)


def snowflake():
    """Six-fold dendritic snowflake (white alpha, tinted at runtime) with a soft glow, for frost decals and flakes."""
    from PIL import Image, ImageDraw, ImageFilter
    import math
    from gen_textures import save_alpha, SS
    size = 512 * SS
    c = size / 2
    R = size * 0.47
    img = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(img)

    def seg(p0, p1, wdt):
        d.line([p0, p1], fill=255, width=max(1, int(wdt)))
        r = wdt / 2
        for p in (p0, p1):
            d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=255)

    def at(ang, dist, base=(c, c)):
        return (base[0] + math.cos(ang) * dist, base[1] + math.sin(ang) * dist)

    for k in range(6):
        a = k * math.pi / 3 - math.pi / 2
        seg((c, c), at(a, R), 9 * SS)
        # side branches at 60 degrees, both sides, shrinking outwards, each with its own twigs
        for t, ln in ((0.3, 0.2), (0.48, 0.27), (0.66, 0.22), (0.82, 0.13)):
            base = at(a, R * t)
            for side in (-1, 1):
                ba = a + side * math.pi / 3
                tip = at(ba, R * ln, base)
                seg(base, tip, 6 * SS)
                for tt, tl in ((0.45, 0.35), (0.75, 0.22)):
                    tb = at(ba, R * ln * tt, base)
                    seg(tb, at(a, R * ln * tl, tb), 3.5 * SS)
                    seg(tb, at(ba + side * math.pi / 3, R * ln * tl * 0.8, tb), 3.5 * SS)
        # small arrow-head plate at the tip
        tip = at(a, R * 0.95)
        for side in (-1, 1):
            seg(tip, at(a + math.pi + side * 0.55, R * 0.07, tip), 4 * SS)
    # hexagonal plate in the middle: outline and an inner star
    hexo = [at(k * math.pi / 3 - math.pi / 2, R * 0.16) for k in range(6)]
    d.line(hexo + [hexo[0]], fill=255, width=int(5 * SS))
    hexi = [at(k * math.pi / 3, R * 0.08) for k in range(6)]
    d.polygon(hexi, fill=210)

    sharp = img.resize((512, 512), Image.LANCZOS)
    glow = img.filter(ImageFilter.GaussianBlur(14 * SS)).resize((512, 512), Image.LANCZOS)
    a = np.asarray(sharp) / 255.0
    g = np.asarray(glow) / 255.0
    save_alpha('vfx/snowflake.png', np.clip(a + g * 0.9, 0, 1))


def rock():
    n = tileable_noise(64, octaves=5, seed=202)
    n2 = tileable_noise(64, octaves=3, seed=203, base=8)
    cracks = np.exp(-((n2 - 0.5) ** 2) / 0.0015)
    lum = np.clip(0.5 + 0.45 * n - 0.35 * cracks, 0.15, 1)
    arr = np.zeros((64, 64, 4))
    arr[..., 0] = lum * 255
    arr[..., 1] = lum * 245
    arr[..., 2] = lum * 232
    arr[..., 3] = 255
    save_rgba('vfx/rock.png', arr)


def bark():
    w, h = 128, 32
    u, v = coords(w, h)
    n = tileable_noise(128, octaves=4, seed=303)[:h, :]
    ridges = 0.5 + 0.5 * np.sin((v * 7 + n * 1.5) * np.pi)
    lum = np.clip(0.45 + 0.4 * ridges + 0.2 * (n - 0.5), 0, 1)
    edge = np.clip(1 - np.abs(v - 0.5) * 2, 0, 1)
    shade = 0.55 + 0.45 * np.sqrt(edge)
    arr = np.zeros((h, w, 4))
    arr[..., 0] = lum * shade * 255
    arr[..., 1] = lum * shade * 255
    arr[..., 2] = lum * shade * 255
    arr[..., 3] = np.clip(edge * 4, 0, 1) * 255
    save_rgba('vfx/bark.png', arr)


def frost():
    """Radial frost growth decal: dendritic ice branches spreading from the centre."""
    from PIL import Image, ImageDraw, ImageFilter
    import math, random
    from gen_textures import save_alpha, SS
    size = 256 * SS
    img = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(img)
    rng = random.Random(77)
    c = size / 2

    def branch(x, y, ang, length, width, depth):
        if depth <= 0 or length < 6 * SS:
            return
        steps = 6
        for _ in range(steps):
            nx = x + math.cos(ang) * length / steps
            ny = y + math.sin(ang) * length / steps
            d.line([(x, y), (nx, ny)], fill=255, width=max(1, int(width)))
            x, y = nx, ny
            ang += rng.uniform(-0.15, 0.15)
            if rng.random() < 0.45:
                side = rng.choice((-1, 1))
                branch(x, y, ang + side * rng.uniform(0.6, 1.1), length * 0.45, width * 0.65, depth - 1)

    for i in range(9):
        a = i * 2 * math.pi / 9 + rng.uniform(-0.2, 0.2)
        branch(c, c, a, size * 0.46, 3.2 * SS, 4)
    img = img.filter(ImageFilter.GaussianBlur(SS * 0.8)).resize((256, 256), Image.LANCZOS)
    import numpy as np
    a = np.asarray(img) / 255.0
    u, v = np.mgrid[0:256, 0:256] / 255.0
    r = np.hypot(u - 0.5, v - 0.5) * 2
    glow = np.exp(-(r ** 2) * 3) * 0.25
    save_alpha('vfx/frost.png', np.clip(a * 1.2 + glow, 0, 1) * (r < 1))


def thorn():
    """Thorn/root spike: fibrous wood grain running up the spike (v: top = tip), lighter, polished towards the tip."""
    w, h = 64, 128
    u, v = coords(w, h)
    n = tileable_noise(128, octaves=4, seed=404)[:h, :w]
    n2 = tileable_noise(128, octaves=3, seed=405, base=16)[:h, :w]
    grain = 0.5 + 0.5 * np.sin((u * 9 + n * 1.8) * np.pi * 2)
    lum = 0.55 + 0.25 * grain + 0.15 * (n2 - 0.5)
    tip = np.clip((0.35 - v) / 0.35, 0, 1)
    lum = lum * (1 - 0.5 * tip) + 0.95 * tip * (0.85 + 0.15 * grain)
    edge = np.abs(u - 0.5) * 2
    lum *= 0.8 + 0.2 * (1 - edge ** 2)
    arr = np.zeros((h, w, 4))
    arr[..., 0] = np.clip(lum * 255, 0, 255)
    arr[..., 1] = np.clip(lum * 248, 0, 255)
    arr[..., 2] = np.clip(lum * 236, 0, 255)
    arr[..., 3] = 255
    save_rgba('vfx/thorn.png', arr)


def petal_vein():
    """Petal surface (u across, v: top = tip, bottom = base): fine veins fanning out from the base, a soft sheen."""
    w, h = 64, 128
    u, v = coords(w, h)
    x = (u - 0.5) * 2
    base = 1 - v                                                  # 0 at the base .. 1 at the tip
    fan = x / np.maximum(0.08, base ** 0.7)                       # veins spread out from the base
    veins = np.abs(np.sin(fan * 5.5 * np.pi)) ** 18
    mid = np.exp(-(x ** 2) / 0.004) * (1 - base * 0.6)
    n = tileable_noise(128, octaves=3, seed=506)[:h, :w]
    lum = 0.9 - 0.18 * veins * (0.4 + 0.6 * base) - 0.12 * mid + 0.06 * (n - 0.5) + 0.08 * base
    arr = np.zeros((h, w, 4))
    arr[..., 0] = np.clip(lum * 255, 0, 255)
    arr[..., 1] = np.clip(lum * 255, 0, 255)
    arr[..., 2] = np.clip(lum * 255, 0, 255)
    arr[..., 3] = 255
    save_rgba('vfx/petal_vein.png', arr)


def soil():
    """Smooth earth for broken-ground meshes (tinted at runtime with the struck block's colour): mottled soil with
    pebbles, fine grain and hairline cracks, no pixel grid."""
    from PIL import Image, ImageDraw, ImageFilter
    import random
    S = 256
    rng = random.Random(901)
    n = tileable_noise(S, octaves=6, seed=902, base=4)
    fine = tileable_noise(S, octaves=3, seed=903, base=32)
    lum = 0.72 + 0.32 * (n - 0.5) + 0.12 * (fine - 0.5)
    peb = Image.new('L', (S, S), 128)
    d = ImageDraw.Draw(peb)
    for _ in range(170):
        x, y, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(1.5, 5.5)
        c = rng.choice([60, 90, 175, 205])
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                d.ellipse([x - r + ox, y - r * 0.8 + oy, x + r + ox, y + r * 0.8 + oy], fill=c)
    peb = np.asarray(peb.filter(ImageFilter.GaussianBlur(0.8))) / 255.0 - 0.5
    cr = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(cr)
    for _ in range(5):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        a = rng.uniform(0, 6.28)
        pts = [(x, y)]
        for _k in range(8):
            a += rng.uniform(-0.6, 0.6)
            x += np.cos(a) * 7
            y += np.sin(a) * 7
            pts.append((x % S, y % S) if 0 <= x < S and 0 <= y < S else (x, y))
        d.line(pts, fill=255, width=1)
    cracks = np.asarray(cr.filter(ImageFilter.GaussianBlur(0.6))) / 255.0
    lum = np.clip(lum + 0.35 * peb - 0.12 * cracks, 0.25, 1.0)
    arr = np.zeros((S, S, 4))
    arr[..., 0] = lum * 255
    arr[..., 1] = lum * 250
    arr[..., 2] = lum * 242
    arr[..., 3] = 255
    save_rgba('vfx/soil.png', arr)


def turf():
    """Smooth grass turf for the tops of torn-up sods (tinted with the biome's grass colour)."""
    from PIL import Image, ImageDraw, ImageFilter
    import random
    S = 256
    rng = random.Random(911)
    n = tileable_noise(S, octaves=5, seed=912, base=8)
    base = (0.62 + 0.25 * (n - 0.5)) * 255
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), 'L')
    d = ImageDraw.Draw(img)
    for _ in range(2600):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        ln = rng.uniform(3, 9)
        a = rng.uniform(-0.5, 0.5) - np.pi / 2
        c = int(rng.uniform(110, 245))
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                d.line([(x + ox, y + oy), (x + ox + np.cos(a) * ln, y + oy + np.sin(a) * ln)], fill=c, width=1)
    lum = np.asarray(img.filter(ImageFilter.GaussianBlur(0.5))) / 255.0
    arr = np.zeros((S, S, 4))
    arr[..., 0] = lum * 255
    arr[..., 1] = lum * 255
    arr[..., 2] = lum * 245
    arr[..., 3] = 255
    save_rgba('vfx/turf.png', arr)


if __name__ == '__main__':
    frost()
    ice()
    snowflake()
    thorn()
    petal_vein()
    soil()
    turf()
    rock()
    bark()
    print('materials written')
