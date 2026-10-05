#!/usr/bin/env python3
"""
Generates the mod's CUSTOM textures (no vanilla assets are used or copied):

  textures/vfx/*.png         greyscale/white energy textures tinted at runtime by the VFX system
  textures/gui/ability/...   ability icons (per weapon)
  textures/gui/status/*.png  status-effect icons
  textures/gui/*.png         misc UI glyphs

Everything is drawn procedurally with numpy/Pillow so the look can be tuned and regenerated, and any file can be
replaced by hand-made art with the same name.

Usage: python3 tools/gen_textures.py
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons', 'textures')
SS = 4  # supersampling for anti-aliased icons


def out(path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def save_alpha(path, alpha, blur=True):
    """Save a float [0,1] alpha map as a white RGBA texture (+ blur/clamp mcmeta)."""
    a = np.clip(alpha, 0, 1)
    rgba = np.zeros(a.shape + (4,), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(out(path))
    if blur:
        with open(out(path) + '.mcmeta', 'w') as f:
            f.write('{"texture":{"blur":true,"clamp":false}}\n')


def save_rgba(path, arr, blur=True):
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA').save(out(path))
    if blur:
        with open(out(path) + '.mcmeta', 'w') as f:
            f.write('{"texture":{"blur":true,"clamp":false}}\n')


# ---------------------------------------------------------------------------------------------------------------------
# noise helpers
# ---------------------------------------------------------------------------------------------------------------------

def tileable_noise(size, octaves=5, seed=1, base=4):
    rng = np.random.default_rng(seed)
    acc = np.zeros((size, size))
    amp, total = 1.0, 0.0
    freq = base
    for _ in range(octaves):
        grid = rng.random((freq, freq))
        # bicubic-ish upsampling with wraparound via PIL
        img = Image.fromarray((grid * 255).astype(np.uint8), 'L')
        tiled = Image.new('L', (freq * 3, freq * 3))
        for i in range(3):
            for j in range(3):
                tiled.paste(img, (i * freq, j * freq))
        up = tiled.resize((size * 3, size * 3), Image.BICUBIC)
        layer = np.asarray(up, dtype=np.float64)[size:size * 2, size:size * 2] / 255.0
        acc += layer * amp
        total += amp
        amp *= 0.5
        freq *= 2
    acc /= total
    acc = (acc - acc.min()) / (acc.max() - acc.min() + 1e-9)
    return acc


def coords(w, h):
    y, x = np.mgrid[0:h, 0:w]
    return (x + 0.5) / w, (y + 0.5) / h


# ---------------------------------------------------------------------------------------------------------------------
# VFX textures
# ---------------------------------------------------------------------------------------------------------------------

def vfx():
    # soft radial glow
    u, v = coords(128, 128)
    r = np.hypot(u - 0.5, v - 0.5) * 2
    save_alpha('vfx/glow.png', np.exp(-(r ** 2) * 5.5) * (r < 1))

    # sharp core flash (brighter center, faster falloff)
    save_alpha('vfx/flash.png', np.clip(np.exp(-(r ** 2) * 14) * 1.2 + np.exp(-(r ** 2) * 3) * 0.25, 0, 1) * (r < 1))

    # ring
    u, v = coords(256, 256)
    r = np.hypot(u - 0.5, v - 0.5) * 2
    ring = np.exp(-((r - 0.82) ** 2) / 0.0015) + 0.35 * np.exp(-((r - 0.78) ** 2) / 0.01) * (r < 0.82)
    save_alpha('vfx/ring.png', ring * (r < 1))

    # shockwave (thick leading edge, faint interior)
    shock = np.exp(-((r - 0.9) ** 2) / 0.003) + 0.25 * np.clip((r - 0.3) / 0.6, 0, 1) ** 3 * (r < 0.9)
    save_alpha('vfx/shockwave.png', shock * (r < 1))

    # noise (tileable)
    n = tileable_noise(128, octaves=5, seed=7)
    save_alpha('vfx/noise.png', n)

    # slash crescent: u = along the arc (0 tail -> 1 head), v = across (0 inner -> 1 outer)
    w, h = 256, 64
    u, v = coords(w, h)
    noise = tileable_noise(256, octaves=4, seed=11)[:h, :]
    edge = np.exp(-((v - 0.72) ** 2) / 0.006)                 # bright outer cutting edge
    body = np.clip(1 - np.abs(v - 0.55) / 0.45, 0, 1) ** 1.6   # soft body
    streaks = 0.55 + 0.45 * np.sin(v * 70 + noise * 9) * 0.5 + noise * 0.3
    along = np.clip(u, 0, 1) ** 1.4 * np.clip((1 - u) / 0.12, 0, 1)  # fades in from the tail, sharp head
    save_alpha('vfx/slash.png', np.clip((edge * 1.1 + body * streaks * 0.75) * along, 0, 1))

    # energy streak (tileable along u) for ribbons / trails / beams
    w, h = 256, 32
    u, v = coords(w, h)
    nz = tileable_noise(256, octaves=4, seed=3)
    nz = nz[np.linspace(0, 255, h).astype(int), :]
    core = np.exp(-((v - 0.5) ** 2) / 0.012)
    flow = 0.5 + 0.5 * np.sin((u * 6 + nz * 1.5) * 2 * math.pi)
    save_alpha('vfx/streak.png', np.clip(core * (0.55 + 0.45 * flow) + np.exp(-((v - 0.5) ** 2) / 0.08) * 0.3, 0, 1))

    # lightning cross-profile (v) with tiny variation along u
    w, h = 64, 32
    u, v = coords(w, h)
    save_alpha('vfx/lightning.png', np.clip(np.exp(-((v - 0.5) ** 2) / 0.004) * 1.3 + np.exp(-((v - 0.5) ** 2) / 0.05) * 0.45, 0, 1))

    # 4-point spark star
    u, v = coords(64, 64)
    x, y = u - 0.5, v - 0.5
    star = np.exp(-np.abs(x) * 60) * np.exp(-np.abs(y) * 6) + np.exp(-np.abs(y) * 60) * np.exp(-np.abs(x) * 6)
    star += np.exp(-(x * x + y * y) * 120)
    save_alpha('vfx/spark.png', np.clip(star, 0, 1))

    # crystal shard (kite shape with bright edges)
    img = Image.new('L', (64 * SS, 64 * SS), 0)
    d = ImageDraw.Draw(img)
    pts = [(32, 2), (50, 30), (32, 62), (14, 30)]
    d.polygon([(px * SS, py * SS) for px, py in pts], fill=150)
    d.line([(px * SS, py * SS) for px, py in pts + [pts[0]]], fill=255, width=3 * SS)
    d.line([(32 * SS, 2 * SS), (32 * SS, 62 * SS)], fill=220, width=SS)
    img = img.resize((64, 64), Image.LANCZOS)
    save_alpha('vfx/shard.png', np.asarray(img) / 255.0)

    # swirl (void core)
    u, v = coords(128, 128)
    x, y = u - 0.5, v - 0.5
    r = np.hypot(x, y) * 2
    th = np.arctan2(y, x)
    sw = 0.5 + 0.5 * np.sin(th * 3 + r * 14)
    save_alpha('vfx/swirl.png', np.clip(sw * np.clip(1 - r, 0, 1) ** 0.7 + np.exp(-(r ** 2) * 30) * 0.6, 0, 1))

    # rift edge: jagged glowing strip along u, v across (0..1). Tileable along u.
    w, h = 256, 64
    u, v = coords(w, h)
    nz = tileable_noise(256, octaves=5, seed=21)
    jag = 0.5 + (nz[0, :][None, :] - 0.5) * 0.35
    dist = np.abs(v - jag)
    save_alpha('vfx/rift_edge.png', np.clip(np.exp(-(dist ** 2) / 0.002) * 1.2 + np.exp(-(dist ** 2) / 0.02) * 0.4, 0, 1))

    # rune circle
    s = 256 * SS
    img = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(img)
    c = s / 2

    def circ(rad, width, fill=255):
        d.ellipse([c - rad, c - rad, c + rad, c + rad], outline=fill, width=width)

    circ(s * 0.47, 3 * SS)
    circ(s * 0.43, 1 * SS, 200)
    circ(s * 0.30, 2 * SS)
    circ(s * 0.18, 1 * SS, 180)
    rng = random.Random(5)
    for i in range(36):
        a = i / 36 * 2 * math.pi
        r1, r2 = s * 0.43, s * 0.47
        if i % 3 == 0:
            d.line([(c + math.cos(a) * r1, c + math.sin(a) * r1), (c + math.cos(a) * r2, c + math.sin(a) * r2)], fill=255, width=2 * SS)
        # glyph marks between rings
        gx, gy = c + math.cos(a + 0.08) * s * 0.365, c + math.sin(a + 0.08) * s * 0.365
        k = rng.randint(0, 3)
        g = s * 0.018
        if k == 0:
            d.line([(gx - g, gy), (gx + g, gy)], fill=230, width=SS * 2)
        elif k == 1:
            d.ellipse([gx - g * 0.7, gy - g * 0.7, gx + g * 0.7, gy + g * 0.7], outline=230, width=SS * 2)
        elif k == 2:
            d.polygon([(gx, gy - g), (gx + g, gy + g), (gx - g, gy + g)], outline=230)
        else:
            d.line([(gx, gy - g), (gx, gy + g)], fill=230, width=SS * 2)
    for i in range(6):  # inner hexagram
        a = i / 6 * 2 * math.pi
        b = (i + 2) / 6 * 2 * math.pi
        d.line([(c + math.cos(a) * s * 0.30, c + math.sin(a) * s * 0.30), (c + math.cos(b) * s * 0.30, c + math.sin(b) * s * 0.30)],
               fill=210, width=SS * 2)
    img = img.resize((256, 256), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.4))
    save_alpha('vfx/rune_circle.png', np.asarray(img) / 255.0)

    # ground crack decal: an impact fracture - a crushed centre, fine jagged radial cracks that fork as they run out,
    # and broken concentric fracture rings between them, over a faint scuff of disturbed ground
    S = 512
    s = S * 2
    img = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(img)
    rng = random.Random(9)
    c = s / 2

    def crack(x, y, a, length, width, depth):
        steps = max(4, int(length / (s * 0.018)))
        for k in range(steps):
            a += rng.gauss(0, 0.22)
            seg = length / steps * rng.uniform(0.7, 1.3)
            nx, ny = x + math.cos(a) * seg, y + math.sin(a) * seg
            w = max(1.6, width * (1 - k / steps * 0.75))
            d.line([(x, y), (nx, ny)], fill=255, width=int(round(w)))
            if depth > 0 and k > 1 and rng.random() < 0.1:
                crack(nx, ny, a + rng.choice([-1, 1]) * rng.uniform(0.35, 0.8), length * rng.uniform(0.25, 0.45), w * 0.7, depth - 1)
            x, y = nx, ny

    radial = 17
    angles = []
    for i in range(radial):
        a = i / radial * 2 * math.pi + rng.uniform(-0.15, 0.15)
        angles.append(a)
        r0 = s * rng.uniform(0.03, 0.06)
        crack(c + math.cos(a) * r0, c + math.sin(a) * r0, a, s * rng.uniform(0.3, 0.47), 6.5, 2)
    # concentric fracture segments between neighbouring radial cracks
    for ring_r in (0.12, 0.21, 0.31):
        for i in range(radial):
            if rng.random() < 0.45:
                continue
            a0, a1 = angles[i], angles[(i + 1) % radial] + (2 * math.pi if i == radial - 1 else 0)
            rr = s * ring_r * rng.uniform(0.9, 1.1)
            pts = []
            n = 6
            for k in range(n + 1):
                t = k / n
                aa = a0 + (a1 - a0) * t
                r2 = rr * (1 + rng.gauss(0, 0.04))
                pts.append((c + math.cos(aa) * r2, c + math.sin(aa) * r2))
            d.line(pts, fill=230, width=3)
    # crushed centre
    for _ in range(40):
        a = rng.uniform(0, 2 * math.pi)
        r = s * 0.045 * math.sqrt(rng.random())
        x, y = c + math.cos(a) * r, c + math.sin(a) * r
        rad = rng.uniform(4, 12)
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=255)
    lines = np.asarray(img.resize((S, S), Image.LANCZOS)) / 255.0
    u, v = np.mgrid[0:S, 0:S] / (S - 1)
    rr = np.hypot(u - 0.5, v - 0.5) * 2
    noise = tileable_noise(S, octaves=5, seed=19)
    scuff = np.clip(1 - rr / 0.7, 0, 1) ** 1.5 * (0.15 + 0.25 * noise)
    save_alpha('vfx/crack.png', np.clip(lines + scuff, 0, 1) * np.clip((1 - rr) * 6, 0, 1))

    # flame tongue (u across, v along: base at v=1)
    w, h = 64, 128
    u, v = coords(w, h)
    nz = tileable_noise(128, octaves=4, seed=33)[:h, :w]
    width = 0.42 * (v ** 0.8) + 0.02
    shape = np.clip(1 - np.abs(u - 0.5 + (nz - 0.5) * 0.25 * (1 - v)) / width, 0, 1)
    save_alpha('vfx/flame.png', np.clip(shape ** 1.3 * (0.6 + 0.6 * nz) * np.clip(v * 1.4, 0, 1), 0, 1))

    # mist / dust puff
    n = tileable_noise(128, octaves=5, seed=44)
    u, v = coords(128, 128)
    r = np.hypot(u - 0.5, v - 0.5) * 2
    save_alpha('vfx/mist.png', np.clip((n - 0.25) * 1.6, 0, 1) * np.clip(1 - r, 0, 1) ** 1.5)

    # hexagon cell grid (shields, UI backgrounds)
    s = 128
    img = Image.new('L', (s * SS, s * SS), 0)
    d = ImageDraw.Draw(img)
    rad = 16 * SS
    hh = math.sqrt(3) * rad
    for row in range(-1, 6):
        for col in range(-1, 6):
            cx = col * rad * 1.5
            cy = row * hh + (col % 2) * hh / 2
            pts = [(cx + rad * math.cos(k * math.pi / 3), cy + rad * math.sin(k * math.pi / 3)) for k in range(6)]
            d.polygon(pts, outline=255)
    img = img.resize((s, s), Image.LANCZOS)
    save_alpha('vfx/hex.png', np.asarray(img) / 255.0)

    # soul wisp (teardrop with hollow eyes)
    s = 64 * SS
    img = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(img)
    d.ellipse([s * 0.22, s * 0.12, s * 0.78, s * 0.62], fill=255)
    d.polygon([(s * 0.24, s * 0.42), (s * 0.76, s * 0.42), (s * 0.5, s * 0.98)], fill=255)
    d.ellipse([s * 0.33, s * 0.28, s * 0.45, s * 0.42], fill=40)
    d.ellipse([s * 0.55, s * 0.28, s * 0.67, s * 0.42], fill=40)
    img = img.resize((64, 64), Image.LANCZOS).filter(ImageFilter.GaussianBlur(1.2))
    save_alpha('vfx/soul.png', np.asarray(img) / 255.0)

    # petal & leaf
    for name, pts in [('petal', None), ('leaf', None)]:
        s = 64 * SS
        img = Image.new('L', (s, s), 0)
        d = ImageDraw.Draw(img)
        if name == 'petal':
            d.ellipse([s * 0.3, s * 0.05, s * 0.7, s * 0.95], fill=255)
        else:
            d.polygon([(s * 0.5, s * 0.02), (s * 0.82, s * 0.45), (s * 0.5, s * 0.98), (s * 0.18, s * 0.45)], fill=230)
            d.line([(s * 0.5, s * 0.05), (s * 0.5, s * 0.95)], fill=255, width=2 * SS)
        img = img.resize((64, 64), Image.LANCZOS)
        save_alpha(f'vfx/{name}.png', np.asarray(img) / 255.0)

    # 5-point star
    s = 64 * SS
    img = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = s * (0.48 if i % 2 == 0 else 0.2)
        pts.append((s / 2 + math.cos(a) * rr, s / 2 + math.sin(a) * rr))
    d.polygon(pts, fill=255)
    img = img.resize((64, 64), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))
    save_alpha('vfx/star.png', np.asarray(img) / 255.0)

    # screen vignette (transparent centre, opaque edges)
    u, v = coords(256, 256)
    r = np.hypot((u - 0.5) * 1.15, (v - 0.5)) * 2
    save_alpha('gui/vignette.png', np.clip((r - 0.45) / 0.75, 0, 1) ** 1.6)



# ---------------------------------------------------------------------------------------------------------------------
# Icons
# ---------------------------------------------------------------------------------------------------------------------

def hex_rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Icon:
    """64x64 anti-aliased icon canvas. Coordinates are in 0..64 icon units."""

    def __init__(self, primary, dark, light, size=64):
        self.size = size
        self.s = size * SS
        self.primary, self.dark, self.light = hex_rgb(primary), hex_rgb(dark), hex_rgb(light)
        self.img = Image.new('RGBA', (self.s, self.s), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        return (x * SS * self.size / 64, y * SS * self.size / 64)

    def pts(self, ps):
        return [self.p(x, y) for x, y in ps]

    def background(self):
        # rounded square with vertical gradient and bevel
        grad = Image.new('RGBA', (self.s, self.s))
        gd = ImageDraw.Draw(grad)
        for y in range(self.s):
            t = y / self.s
            col = mix(mix(self.dark, self.primary, 0.35), self.dark, t)
            gd.line([(0, y), (self.s, y)], fill=col + (255,))
        mask = Image.new('L', (self.s, self.s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([self.p(2, 2), self.p(62, 62)], radius=9 * SS, fill=255)
        self.img.paste(grad, (0, 0), mask)
        self.d.rounded_rectangle([self.p(2, 2), self.p(62, 62)], radius=9 * SS, outline=self.primary + (255,), width=2 * SS)
        self.d.rounded_rectangle([self.p(5, 5), self.p(59, 59)], radius=7 * SS, outline=mix(self.primary, (0, 0, 0), 0.5) + (160,), width=SS)
        # inner glow
        glow = Image.new('RGBA', (self.s, self.s), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse([self.p(14, 14), self.p(50, 50)], fill=self.primary + (90,))
        glow = glow.filter(ImageFilter.GaussianBlur(10 * SS))
        self.img.alpha_composite(glow)

    def glow_layer(self, draw_fn, color, blur=3.0, alpha=200):
        layer = Image.new('RGBA', (self.s, self.s), (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(layer), color + (alpha,))
        layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
        self.img.alpha_composite(layer)

    def shape(self, draw_fn, glow=True):
        if glow:
            self.glow_layer(draw_fn, self.primary, 3.5, 230)
        layer = Image.new('RGBA', (self.s, self.s), (0, 0, 0, 0))
        draw_fn(ImageDraw.Draw(layer), self.light + (255,))
        self.img.alpha_composite(layer)

    def save(self, path):
        self.img.resize((self.size, self.size), Image.LANCZOS).save(out(path))


def voidfang_icons():
    P, D, L = 0x9B4DFF, 0x12041F, 0xEBDDFF

    def make(name, fn):
        ic = Icon(P, D, L)
        ic.background()
        fn(ic)
        ic.save(f'gui/ability/voidfang/{name}.png')

    def void_slash(ic):
        def f(d, col):
            d.pieslice([ic.p(8, 8), ic.p(58, 58)], 200, 340, fill=col)
        ic.shape(f)
        ic.d.pieslice([ic.p(14, 15), ic.p(56, 58)], 195, 345, fill=mix(ic.dark, ic.primary, 0.15) + (255,))
        for i, (y, l) in enumerate([(40, 18), (46, 24), (52, 14)]):
            ic.d.line([ic.p(10, y), ic.p(10 + l, y - 4)], fill=ic.light + (200,), width=2 * SS)

    def void_blink(ic):
        def f(d, col):
            d.ellipse([ic.p(36, 12), ic.p(46, 22)], fill=col)               # head
            d.polygon(ic.pts([(34, 24), (48, 24), (46, 42), (36, 42)]), fill=col)  # body
            d.polygon(ic.pts([(36, 42), (41, 42), (38, 56), (33, 56)]), fill=col)
            d.polygon(ic.pts([(41, 42), (46, 42), (50, 56), (45, 56)]), fill=col)
        ic.shape(f)
        rng = random.Random(2)
        for i in range(9):  # dissolving fragments to the left
            x = 28 - i * 2.4 + rng.uniform(-1, 1)
            y = 18 + rng.uniform(0, 34)
            s = 3.5 - i * 0.3
            ic.d.rectangle([ic.p(x, y), ic.p(x + s, y + s)], fill=ic.light + (int(230 - i * 22),))
        ic.d.polygon(ic.pts([(8, 33), (20, 27), (20, 39)]), fill=ic.primary + (255,))

    def void_mark(ic):
        def f(d, col):
            d.polygon(ic.pts([(32, 8), (54, 32), (32, 56), (10, 32)]), outline=col, width=3 * SS)
            d.ellipse([ic.p(22, 24), ic.p(42, 40)], outline=col, width=3 * SS)
            d.ellipse([ic.p(28, 28), ic.p(36, 36)], fill=col)
        ic.shape(f)

    def rift_tear(ic):
        def f(d, col):
            d.polygon(ic.pts([(33, 6), (38, 18), (35, 26), (42, 36), (36, 46), (38, 58), (29, 46), (31, 36), (24, 27), (30, 18)]), fill=col)
        ic.glow_layer(f, ic.primary, 6, 255)
        ic.shape(f, glow=False)
        def core(d, col):
            d.polygon(ic.pts([(33, 12), (35, 19), (33, 26), (37, 36), (34, 44), (35, 52), (31, 44), (33, 36), (28, 27), (31, 19)]), fill=col)
        layer = Image.new('RGBA', (ic.s, ic.s), (0, 0, 0, 0))
        core(ImageDraw.Draw(layer), (8, 0, 18, 255))
        ic.img.alpha_composite(layer)

    def void_execution(ic):
        def f(d, col):
            d.polygon(ic.pts([(50, 8), (56, 14), (22, 48), (16, 42)]), fill=col)       # blade
            d.polygon(ic.pts([(14, 40), (24, 50), (20, 54), (10, 44)]), fill=col)       # guard
            d.line([ic.p(15, 49), ic.p(8, 56)], fill=col, width=4 * SS)                # grip
        ic.shape(f)
        ic.d.ellipse([ic.p(30, 26), ic.p(50, 46)], outline=(255, 90, 140, 255), width=2 * SS)
        ic.d.line([ic.p(40, 22), ic.p(40, 30)], fill=(255, 90, 140, 255), width=2 * SS)
        ic.d.line([ic.p(40, 42), ic.p(40, 50)], fill=(255, 90, 140, 255), width=2 * SS)

    def void_dimension(ic):
        def f(d, col):
            d.ellipse([ic.p(10, 10), ic.p(54, 54)], outline=col, width=3 * SS)
            for k in range(3):
                a0 = k * 120
                d.arc([ic.p(16, 16), ic.p(48, 48)], a0, a0 + 80, fill=col, width=3 * SS)
        ic.shape(f)
        ic.d.ellipse([ic.p(24, 24), ic.p(40, 40)], fill=(6, 0, 14, 255))
        ic.d.ellipse([ic.p(24, 24), ic.p(40, 40)], outline=ic.light + (255,), width=SS)

    make('void_slash', void_slash)
    make('void_blink', void_blink)
    make('void_mark', void_mark)
    make('rift_tear', rift_tear)
    make('void_execution', void_execution)
    make('void_dimension', void_dimension)


STATUS = {
    'void_mark': 0x9B4DFF, 'solar_burn': 0xFFB627, 'frostbite': 0x8FE3FF, 'frozen': 0xDDF8FF, 'berserker': 0xE0213A,
    'soul_drain': 0x5A8CFF, 'nature_poison': 0x6BE36F, 'eclipse_light': 0xFFE7A0, 'eclipse_darkness': 0x8B3DFF,
    'gravity_bound': 0x8A6CFF, 'inferno_overheat': 0xFF5A1F, 'rooted': 0x4CAF50, 'staggered': 0xB08A5A,
    'seared': 0xFF3A10,
}


def status_icons():
    for name, col in STATUS.items():
        rgb = hex_rgb(col)
        ic = Icon(col, 0x0A0A12, 0xFFFFFF, size=32)
        ic.d.ellipse([ic.p(3, 3), ic.p(61, 61)], fill=(10, 10, 18, 230), outline=rgb + (255,), width=3 * SS)

        def glyph(d, c, name=name):
            if name in ('void_mark',):
                d.polygon(ic.pts([(32, 14), (48, 32), (32, 50), (16, 32)]), outline=c, width=4 * SS)
                d.ellipse([ic.p(27, 27), ic.p(37, 37)], fill=c)
            elif name in ('solar_burn', 'inferno_overheat', 'seared'):
                d.polygon(ic.pts([(32, 12), (42, 30), (38, 30), (44, 50), (20, 50), (26, 30), (22, 30)]), fill=c)
            elif name in ('frostbite', 'frozen'):
                for k in range(3):
                    a = k * math.pi / 3
                    d.line([ic.p(32 + math.cos(a) * 18, 32 + math.sin(a) * 18), ic.p(32 - math.cos(a) * 18, 32 - math.sin(a) * 18)], fill=c, width=4 * SS)
                if name == 'frozen':
                    d.rectangle([ic.p(24, 24), ic.p(40, 40)], outline=c, width=3 * SS)
            elif name == 'berserker':
                d.polygon(ic.pts([(32, 50), (14, 30), (20, 18), (32, 26), (44, 18), (50, 30)]), fill=c)
            elif name == 'soul_drain':
                d.ellipse([ic.p(22, 14), ic.p(42, 36)], fill=c)
                d.polygon(ic.pts([(22, 28), (42, 28), (32, 52)]), fill=c)
            elif name in ('nature_poison', 'rooted'):
                d.polygon(ic.pts([(32, 12), (46, 30), (32, 52), (18, 30)]), fill=c)
                d.line([ic.p(32, 18), ic.p(32, 50)], fill=(10, 30, 10, 255), width=2 * SS)
            elif name == 'eclipse_light':
                d.ellipse([ic.p(18, 18), ic.p(46, 46)], fill=c)
            elif name == 'eclipse_darkness':
                d.ellipse([ic.p(18, 18), ic.p(46, 46)], fill=c)
                d.ellipse([ic.p(24, 14), ic.p(52, 42)], fill=(10, 10, 18, 255))
            elif name == 'staggered':
                # a boulder cracked in two
                d.polygon(ic.pts([(14, 40), (20, 22), (34, 14), (48, 20), (52, 38), (40, 50), (22, 50)]), fill=c)
                d.line([ic.p(32, 14), ic.p(28, 26), ic.p(36, 34), ic.p(30, 50)], fill=(10, 10, 18, 255), width=3 * SS)
            elif name == 'gravity_bound':
                for rr in (18, 11, 4):
                    d.ellipse([ic.p(32 - rr, 32 - rr), ic.p(32 + rr, 32 + rr)], outline=c, width=3 * SS)
        ic.shape(glyph)
        ic.save(f'gui/status/{name}.png')


def ui_glyphs():
    # lock
    ic = Icon(0xB0B0C0, 0x101018, 0xE0E0F0, size=32)
    ic.d.rounded_rectangle([ic.p(14, 28), ic.p(50, 56)], radius=4 * SS, fill=(200, 200, 215, 255))
    ic.d.arc([ic.p(20, 10), ic.p(44, 40)], 180, 360, fill=(200, 200, 215, 255), width=5 * SS)
    ic.d.ellipse([ic.p(29, 36), ic.p(35, 44)], fill=(40, 40, 50, 255))
    ic.save('gui/lock.png')
    # mastery point gem
    ic = Icon(0xC9A2FF, 0x1A0D2E, 0xFFFFFF, size=32)
    ic.shape(lambda d, c: d.polygon(ic.pts([(32, 6), (54, 26), (32, 58), (10, 26)]), fill=c))
    ic.d.polygon(ic.pts([(32, 6), (54, 26), (32, 30), (10, 26)]), fill=(255, 255, 255, 90))
    ic.save('gui/mastery_point.png')


if __name__ == '__main__':
    vfx()
    voidfang_icons()
    status_icons()
    ui_glyphs()
    print('textures written to', os.path.relpath(ROOT))
