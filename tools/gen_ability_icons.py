#!/usr/bin/env python3
"""
Ability icons as pixel art in the manner of the Codex's thumb-tab icons, drawn on their 32 grid and doubled into 64x64 textures (see the Codex's tabs.png): each a
small picture of what the ability does, built of flat-shaded shapes on a 48 grid (three tones to a material and a
glint), set down pixel by pixel in those flat tones with hard edges and ringed in a one-pixel dark ink outline; every
picture is fitted to fill its cell alike, so all read the same size. No background of their own: the HUD, the menus
and the banners set them in their own frames, drawn with sharp pixels.

Usage: python3 tools/gen_ability_icons.py [weapon ...] [--preview out.png]
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons',
                    'textures', 'gui', 'ability')
N, OUT, K = 48, 64, 12
ART = 32                 # the pixel grid the icons are drawn on: each of its pixels is 2x2 in the 64x64 texture
S = N * K
INK = (30, 18, 12)

# each material: darkest, dark, mid, light, glint
PAL = {
    'steel': [(58, 62, 74), (98, 104, 118), (150, 156, 170), (204, 210, 222), (248, 250, 255)],
    'gold': [(110, 70, 18), (164, 112, 30), (218, 166, 58), (250, 214, 116), (255, 246, 200)],
    'wood': [(60, 36, 18), (100, 62, 30), (140, 92, 48), (178, 126, 72), (214, 170, 112)],
    'stone': [(58, 54, 52), (92, 86, 82), (128, 120, 112), (166, 158, 148), (206, 200, 190)],
    'earth': [(64, 40, 22), (104, 68, 38), (144, 100, 58), (182, 138, 86), (220, 184, 130)],
    'blood': [(70, 6, 10), (122, 14, 20), (180, 28, 34), (226, 70, 66), (255, 168, 150)],
    'fire': [(120, 20, 8), (196, 56, 12), (246, 128, 26), (255, 202, 70), (255, 248, 200)],
    'lava': [(50, 20, 14), (150, 34, 10), (232, 92, 18), (255, 170, 40), (255, 236, 150)],
    'ice': [(28, 66, 120), (54, 120, 182), (110, 186, 230), (186, 232, 252), (246, 254, 255)],
    'water': [(14, 50, 112), (26, 96, 176), (58, 152, 222), (130, 210, 246), (226, 248, 255)],
    'void': [(28, 10, 54), (64, 26, 110), (112, 54, 178), (172, 112, 236), (236, 210, 255)],
    'shadow': [(16, 10, 26), (38, 26, 58), (66, 48, 96), (102, 80, 138), (160, 140, 200)],
    'leaf': [(22, 62, 22), (40, 104, 34), (74, 152, 50), (130, 204, 76), (214, 250, 160)],
    'poison': [(46, 64, 12), (88, 116, 18), (140, 170, 34), (192, 222, 70), (240, 255, 170)],
    'petal': [(120, 30, 80), (186, 58, 122), (234, 110, 168), (252, 176, 208), (255, 232, 244)],
    'light': [(150, 104, 30), (214, 160, 48), (250, 214, 96), (255, 242, 170), (255, 255, 240)],
    'holy': [(170, 140, 70), (220, 196, 110), (248, 232, 160), (255, 248, 214), (255, 255, 255)],
    'storm': [(30, 52, 120), (52, 96, 196), (96, 156, 248), (176, 214, 255), (250, 252, 255)],
    'cloud': [(54, 60, 82), (88, 96, 122), (128, 136, 160), (176, 184, 204), (226, 230, 242)],
    'soul': [(18, 76, 74), (30, 128, 118), (64, 196, 172), (150, 244, 220), (230, 255, 248)],
    'bone': [(110, 96, 70), (160, 146, 112), (206, 194, 160), (236, 228, 200), (255, 252, 236)],
    'cosmic': [(16, 14, 46), (36, 30, 96), (70, 56, 160), (128, 108, 220), (220, 210, 255)],
    'crystal': [(90, 20, 50), (150, 30, 66), (206, 50, 86), (246, 110, 130), (255, 200, 210)],
    'chain': [(48, 46, 50), (80, 76, 80), (118, 112, 114), (160, 154, 152), (210, 206, 200)],
    'night': [(10, 8, 22), (24, 18, 44), (44, 34, 76), (72, 58, 112), (120, 104, 170)],
    'rune': [(70, 30, 120), (120, 60, 200), (176, 120, 255), (220, 190, 255), (255, 245, 255)],
    'rainbow': [(170, 40, 60), (230, 150, 40), (240, 230, 70), (70, 200, 120), (90, 150, 250)],
}


def P(x, y):
    return (x * K, y * K)


def rot(pts, ang, cx=0.0, cy=0.0):
    c, s = math.cos(ang), math.sin(ang)
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]


def lerp(a, b, t):
    return a + (b - a) * t


# the materials given a grain, as the Codex's stone, steel and leather are: stray pixels a tone lighter or darker
GRAINED = ('steel', 'stone', 'earth', 'wood', 'bone', 'chain', 'cloud', 'gold')
TONE_OF = {tuple(c): (m, t) for m, tones in PAL.items() for t, c in enumerate(tones)}


def grain(cell):
    """Speckles the grained materials' pixels (not those at an edge), the same way every time."""
    rnd = random.Random(7)
    h, w = cell.shape[:2]
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            r = rnd.random()
            px = cell[y, x]
            if px[3] < 255 or r > 0.16:
                continue
            key = TONE_OF.get((int(px[0]), int(px[1]), int(px[2])))
            if key is None or key[0] not in GRAINED:
                continue
            if any(cell[y + dy, x + dx][3] == 0 or tuple(cell[y + dy, x + dx][:3]) != tuple(px[:3]) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                continue
            m, t = key
            t2 = max(0, min(4, t + (1 if r < 0.07 else -1)))
            cell[y, x, :3] = PAL[m][t2]


class Canvas:
    """A picture on the 48 grid. Colours are a material name and a tone (0 darkest .. 4 glint), 'ink', or an RGB tuple."""

    def __init__(self):
        self.img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    @staticmethod
    def col(c, alpha=255):
        if c is None:
            return (0, 0, 0, 0)
        if c == 'ink':
            return (*INK, alpha)
        if isinstance(c, tuple) and len(c) == 2 and isinstance(c[0], str):
            return (*PAL[c[0]][c[1]], alpha)
        if isinstance(c, tuple):
            return (*c[:3], alpha)
        raise ValueError(c)

    # ---- plain shapes ----

    def _translucent(self, draw):
        """Draws a see-through shape blended over what is there (PIL would write its alpha straight in)."""
        layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        draw(ImageDraw.Draw(layer))
        self.img.alpha_composite(layer)

    def poly(self, pts, c, alpha=255):
        if len(pts) < 3:
            return
        if alpha < 255:
            self._translucent(lambda d: d.polygon([P(x, y) for x, y in pts], fill=self.col(c, alpha)))
        else:
            self.d.polygon([P(x, y) for x, y in pts], fill=self.col(c, alpha))

    def ellipse(self, cx, cy, rx, ry, c, alpha=255):
        box = [P(cx - rx, cy - ry), P(cx + rx, cy + ry)]
        if alpha < 255:
            self._translucent(lambda d: d.ellipse(box, fill=self.col(c, alpha)))
        else:
            self.d.ellipse(box, fill=self.col(c, alpha))

    def circle(self, cx, cy, r, c, alpha=255):
        self.ellipse(cx, cy, r, r, c, alpha)

    def rect(self, x0, y0, x1, y1, c):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], c)

    def line(self, pts, w, c, alpha=255):
        """A stroke of width {@code w} along {@code pts}, round at the joins and ends."""
        pts = [P(x, y) for x, y in pts]
        col = self.col(c, alpha)
        self.d.line(pts, fill=col, width=max(1, int(w * K)), joint='curve')
        r = w * K / 2
        for x, y in (pts[0], pts[-1]):
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=col)

    def stroke(self, pts, widths, c):
        """A stroke whose width runs through {@code widths} (one per point): tapering vines, tails, slashes."""
        left, right = [], []
        for i, (x, y) in enumerate(pts):
            a = pts[max(0, i - 1)]
            b = pts[min(len(pts) - 1, i + 1)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            ln = math.hypot(dx, dy) or 1
            nx, ny = -dy / ln, dx / ln
            w = widths[i] / 2
            left.append((x + nx * w, y + ny * w))
            right.append((x - nx * w, y - ny * w))
        self.poly(left + right[::-1], c)

    def arc_band(self, cx, cy, r0, r1, a0, a1, c, steps=48, taper=True):
        """A crescent band between radius r0 and r1 from angle a0 to a1 (degrees), thinning to points at the ends."""
        outer, inner = [], []
        for i in range(steps + 1):
            t = i / steps
            a = math.radians(lerp(a0, a1, t))
            k = math.sin(math.pi * t) if taper else 1.0
            ro = r1
            ri = lerp(r1, r0, k) if taper else r0
            outer.append((cx + ro * math.cos(a), cy + ro * math.sin(a)))
            inner.append((cx + ri * math.cos(a), cy + ri * math.sin(a)))
        self.poly(outer + inner[::-1], c)

    def ring(self, cx, cy, r, w, c):
        self.d.ellipse([P(cx - r, cy - r), P(cx + r, cy + r)], outline=self.col(c), width=max(1, int(w * K)))

    def ering(self, cx, cy, rx, ry, w, c):
        self.d.ellipse([P(cx - rx, cy - ry), P(cx + rx, cy + ry)], outline=self.col(c), width=max(1, int(w * K)))

    def star(self, cx, cy, ro, ri, n, c, ang=-90.0):
        pts = []
        for i in range(n * 2):
            r = ro if i % 2 == 0 else ri
            a = math.radians(ang + i * 180.0 / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        self.poly(pts, c)

    def finish(self, fill=0.9):
        """
        The picture as pixel art on the 32 grid the Codex's tab icons are drawn on, each pixel doubled into the 64x64
        texture: fitted to fill its cell alike, each pixel takes the colour most of its patch of the drawing has (so
        every pixel is one of the drawing's own flat tones, nothing blended), a pixel is kept where the drawing covers
        enough of it, and the whole is ringed in a one-pixel ink outline.
        """
        box = self.img.getbbox()
        w, h = box[2] - box[0], box[3] - box[1]
        size = round(ART * fill) - 2                    # the outline takes a pixel each side
        scale = size / max(w, h)
        tw, th = max(1, round(w * scale)), max(1, round(h * scale))
        k = 16
        hi = np.asarray(self.img.crop(box).resize((tw * k, th * k), Image.NEAREST), dtype=np.uint8)
        blocks = hi.reshape(th, k, tw, k, 4).transpose(0, 2, 1, 3, 4).reshape(th, tw, k * k, 4)
        art = np.zeros((th, tw, 4), dtype=np.uint8)
        for y in range(th):
            for x in range(tw):
                px = blocks[y, x]
                solid = px[px[:, 3] > 0]
                if len(solid) < k * k * 0.34:
                    continue
                packed = (solid[:, 0].astype(np.uint32) << 24) | (solid[:, 1].astype(np.uint32) << 16) | (solid[:, 2].astype(np.uint32) << 8) | solid[:, 3]
                values, counts = np.unique(packed, return_counts=True)
                v = int(values[np.argmax(counts)])
                a = v & 255
                # see-through parts (a bubble, a beam of light) keep one of three steps of see-through
                a = 255 if a > 200 else 170 if a > 130 else 110
                art[y, x] = (v >> 24 & 255, v >> 16 & 255, v >> 8 & 255, a)
        cell = np.zeros((ART, ART, 4), dtype=np.uint8)
        ox, oy = (ART - tw) // 2, (ART - th) // 2
        cell[oy:oy + th, ox:ox + tw] = art
        grain(cell)
        filled = cell[:, :, 3] > 0
        grown = filled.copy()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                grown |= np.roll(np.roll(filled, dy, 0), dx, 1)
        ring = grown & ~filled
        cell[ring] = (*INK, 255)
        return Image.fromarray(cell, 'RGBA').resize((OUT, OUT), Image.NEAREST)


# ------------------------------------------------------------------------------------------------------------
# shaded pieces
# ------------------------------------------------------------------------------------------------------------

def ball(cv, cx, cy, r, m, glint=True):
    """A shaded sphere: lit from the upper left."""
    cv.circle(cx, cy, r, (m, 1))
    cv.circle(cx - r * 0.12, cy - r * 0.12, r * 0.82, (m, 2))
    cv.circle(cx - r * 0.3, cy - r * 0.3, r * 0.48, (m, 3))
    if glint:
        cv.ellipse(cx - r * 0.42, cy - r * 0.46, r * 0.18, r * 0.13, (m, 4))


def blade(cv, cx, cy, ang, length, width, m='steel', guard='gold', grip='wood', pommel='gold', guard_w=None, tip=None):
    """A sword pointing along {@code ang} (radians, 0 = up) with its hilt at (cx, cy)."""
    a = ang
    tip = tip or width * 1.6
    L, w = length, width / 2
    body = [(0, -L), (w, -L + tip), (w, 0), (-w, 0), (-w, -L + tip)]
    cv.poly(rot(body, a, cx, cy), (m, 2))
    cv.poly(rot([(0, -L), (0, 0), (-w, 0), (-w, -L + tip)], a, cx, cy), (m, 3))
    cv.poly(rot([(-w * 0.55, -L + tip), (-w * 0.25, -L + tip * 0.6), (-w * 0.25, -1), (-w * 0.55, -1)], a, cx, cy), (m, 4))
    cv.poly(rot([(w * 0.2, -L + tip), (w * 0.45, -L + tip), (w * 0.45, -1), (w * 0.2, -1)], a, cx, cy), (m, 1))
    gw = guard_w or width * 2.4
    cv.poly(rot([(-gw / 2, 0), (gw / 2, 0), (gw / 2 + 0.8, 1.2), (gw / 2, 2.4), (-gw / 2, 2.4), (-gw / 2 - 0.8, 1.2)], a, cx, cy), (guard, 2))
    cv.poly(rot([(-gw / 2, 0), (gw / 2, 0), (gw / 2 + 0.8, 1.2), (-gw / 2 - 0.8, 1.2)], a, cx, cy), (guard, 3))
    gl = length * 0.24
    cv.poly(rot([(-w * 0.7, 2.4), (w * 0.7, 2.4), (w * 0.7, 2.4 + gl), (-w * 0.7, 2.4 + gl)], a, cx, cy), (grip, 1))
    cv.poly(rot([(-w * 0.7, 2.4), (0, 2.4), (0, 2.4 + gl), (-w * 0.7, 2.4 + gl)], a, cx, cy), (grip, 2))
    px, py = rot([(0, 2.4 + gl + 1.3)], a, cx, cy)[0]
    ball(cv, px, py, 1.8, pommel)


def axe_head(cv, cx, cy, ang, s, m='steel', haft='wood', haft_len=None, double=False):
    """An axe with its haft through (cx, cy), pointing along {@code ang} (0 = head up)."""
    hl = haft_len if haft_len is not None else 30 * s
    cv.poly(rot([(-1.3 * s, -hl * 0.45), (1.3 * s, -hl * 0.45), (1.3 * s, hl * 0.55), (-1.3 * s, hl * 0.55)], ang, cx, cy), (haft, 1))
    cv.poly(rot([(-1.3 * s, -hl * 0.45), (0, -hl * 0.45), (0, hl * 0.55), (-1.3 * s, hl * 0.55)], ang, cx, cy), (haft, 2))
    top = -hl * 0.45
    for side in ((1, -1) if double else (1,)):
        head = [(1 * s * side, top + 1 * s), (11 * s * side, top - 4 * s), (13 * s * side, top + 4 * s), (11 * s * side, top + 12 * s), (1 * s * side, top + 8 * s)]
        cv.poly(rot(head, ang, cx, cy), (m, 2))
        cv.poly(rot([(1 * s * side, top + 1 * s), (11 * s * side, top - 4 * s), (12 * s * side, top + 1 * s), (1 * s * side, top + 4 * s)], ang, cx, cy), (m, 3))
        cv.poly(rot([(11 * s * side, top - 4 * s), (13 * s * side, top + 4 * s), (11 * s * side, top + 12 * s), (10 * s * side, top + 4 * s)], ang, cx, cy), (m, 4))
        cv.poly(rot([(1 * s * side, top + 8 * s), (6 * s * side, top + 9 * s), (11 * s * side, top + 12 * s), (6 * s * side, top + 6.5 * s)], ang, cx, cy), (m, 1))


def hammer(cv, cx, cy, ang, s, m='stone', band='gold', haft='wood'):
    cv.poly(rot([(-1.4 * s, -6 * s), (1.4 * s, -6 * s), (1.4 * s, 18 * s), (-1.4 * s, 18 * s)], ang, cx, cy), (haft, 1))
    cv.poly(rot([(-1.4 * s, -6 * s), (0, -6 * s), (0, 18 * s), (-1.4 * s, 18 * s)], ang, cx, cy), (haft, 2))
    head = [(-9 * s, -14 * s), (9 * s, -14 * s), (9 * s, -4 * s), (-9 * s, -4 * s)]
    cv.poly(rot(head, ang, cx, cy), (m, 2))
    cv.poly(rot([(-9 * s, -14 * s), (9 * s, -14 * s), (9 * s, -11.5 * s), (-9 * s, -11.5 * s)], ang, cx, cy), (m, 3))
    cv.poly(rot([(-9 * s, -6.5 * s), (9 * s, -6.5 * s), (9 * s, -4 * s), (-9 * s, -4 * s)], ang, cx, cy), (m, 1))
    cv.poly(rot([(-2.4 * s, -14.5 * s), (2.4 * s, -14.5 * s), (2.4 * s, -3.5 * s), (-2.4 * s, -3.5 * s)], ang, cx, cy), (band, 2))
    cv.poly(rot([(-2.4 * s, -14.5 * s), (0, -14.5 * s), (0, -3.5 * s), (-2.4 * s, -3.5 * s)], ang, cx, cy), (band, 3))


def scythe(cv, cx, cy, ang, s, m='steel', haft='shadow'):
    """A scythe: haft through (cx, cy) along ang (0 = up), the blade curving off the top to the left."""
    cv.poly(rot([(-1.2 * s, -16 * s), (1.2 * s, -16 * s), (1.2 * s, 18 * s), (-1.2 * s, 18 * s)], ang, cx, cy), (haft, 2))
    cv.poly(rot([(-1.2 * s, -16 * s), (0, -16 * s), (0, 18 * s), (-1.2 * s, 18 * s)], ang, cx, cy), (haft, 3))
    pts_out, pts_in = [], []
    for i in range(25):
        t = i / 24
        a = math.radians(lerp(-10, 140, t))
        r = 14 * s
        thick = 4.5 * s * math.sin(math.pi * min(1, t * 1.15)) + 0.2
        x, y = -r * math.cos(a) + r * math.cos(math.radians(-10)), -16 * s - r * math.sin(a) * 0.55
        pts_out.append((x, y))
        pts_in.append((x + thick * 0.25, y + thick))
    cv.poly(rot(pts_out + pts_in[::-1], ang, cx, cy), (m, 2))
    cv.poly(rot(pts_out + [(p[0] + 0.1, p[1] + 1.2 * s) for p in pts_out[::-1]], ang, cx, cy), (m, 4))


def lance(cv, x0, y0, x1, y1, w, m='gold', tipm='holy'):
    """A lance from its butt (x0, y0) to its tip (x1, y1)."""
    ang = math.atan2(x1 - x0, -(y1 - y0))
    L = math.hypot(x1 - x0, y1 - y0)
    cv.poly(rot([(-w * 0.35, -L * 0.6), (w * 0.35, -L * 0.6), (w * 0.35, 0), (-w * 0.35, 0)], ang, x0, y0), (m, 1))
    cv.poly(rot([(-w * 0.35, -L * 0.6), (0, -L * 0.6), (0, 0), (-w * 0.35, 0)], ang, x0, y0), (m, 2))
    cone = [(0, -L), (w, -L * 0.6), (w * 0.6, -L * 0.52), (-w * 0.6, -L * 0.52), (-w, -L * 0.6)]
    cv.poly(rot(cone, ang, x0, y0), (tipm, 2))
    cv.poly(rot([(0, -L), (0, -L * 0.52), (-w * 0.6, -L * 0.52), (-w, -L * 0.6)], ang, x0, y0), (tipm, 3))
    cv.poly(rot([(-w * 1.3, -L * 0.52), (w * 1.3, -L * 0.52), (w * 1.0, -L * 0.47), (-w * 1.0, -L * 0.47)], ang, x0, y0), (m, 3))


def flame(cv, cx, by, h, w, m='fire', lean=0.0):
    """A flame standing on (cx, by), {@code h} tall."""
    def tongue(scale, tone, dx=0.0):
        out = []
        for i in range(31):
            t = i / 30
            yv = by - h * scale * t
            half = w * scale * 0.5 * math.sin(math.pi * min(1.0, 0.25 + t * 0.9)) * (1 - t) ** 0.6
            out.append((cx + dx + lean * h * t * t + half, yv))
        for i in range(30, -1, -1):
            t = i / 30
            yv = by - h * scale * t
            half = w * scale * 0.5 * math.sin(math.pi * min(1.0, 0.25 + t * 0.9)) * (1 - t) ** 0.6
            out.append((cx + dx + lean * h * t * t - half, yv))
        cv.poly(out, (m, tone))
    tongue(1.0, 1)
    tongue(0.8, 2, -w * 0.04)
    tongue(0.56, 3, -w * 0.06)
    tongue(0.3, 4, -w * 0.05)


def fireball(cv, cx, cy, r, dx, dy, m='fire', tail=2.6):
    """A ball of fire flying along (dx, dy), its tail streaming behind."""
    ln = math.hypot(dx, dy) or 1
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux
    for tone, k in ((1, 1.0), (2, 0.78), (3, 0.5)):
        tail_pts = [(cx + nx * r * k, cy + ny * r * k), (cx - ux * r * tail * k * 1.6 + nx * r * 0.2, cy - uy * r * tail * k * 1.6 + ny * r * 0.2),
                    (cx - nx * r * k, cy - ny * r * k)]
        cv.poly(tail_pts, (m, tone))
        cv.circle(cx, cy, r * k, (m, tone))
    cv.circle(cx + ux * r * 0.15, cy + uy * r * 0.15, r * 0.28, (m, 4))


def bolt(cv, pts, w, m='storm'):
    """A lightning bolt through {@code pts}."""
    cv.line(pts, w * 1.6, (m, 1))
    cv.line(pts, w, (m, 3))
    cv.line(pts, w * 0.4, (m, 4))


def zigzag(x0, y0, x1, y1, n, amp, seed):
    rnd = random.Random(seed)
    pts = [(x0, y0)]
    for i in range(1, n):
        t = i / n
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        off = amp * (1 if i % 2 else -1) * (0.6 + 0.4 * rnd.random())
        pts.append((x0 + dx * t - dy / ln * off, y0 + dy * t + dx / ln * off))
    pts.append((x1, y1))
    return pts


def snowflake(cv, cx, cy, r, m='ice'):
    for k in range(6):
        a = math.radians(k * 60 - 90)
        ex, ey = cx + r * math.cos(a), cy + r * math.sin(a)
        cv.line([(cx, cy), (ex, ey)], r * 0.16, (m, 2))
        for f in (0.45, 0.72):
            bx, by = cx + r * f * math.cos(a), cy + r * f * math.sin(a)
            for s in (-1, 1):
                b = a + s * math.radians(50)
                cv.line([(bx, by), (bx + r * 0.28 * math.cos(b), by + r * 0.28 * math.sin(b))], r * 0.12, (m, 3))
        cv.line([(cx, cy), (cx + r * 0.9 * math.cos(a), cy + r * 0.9 * math.sin(a))], r * 0.06, (m, 4))
    cv.circle(cx, cy, r * 0.18, (m, 3))


def shard(cv, cx, by, h, w, lean=0.0, m='ice'):
    """An ice spike (or crystal) rising from (cx, by)."""
    tipx = cx + lean
    pts = [(cx - w / 2, by), (tipx, by - h), (cx + w / 2, by)]
    cv.poly(pts, (m, 2))
    cv.poly([(cx - w / 2, by), (tipx, by - h), (cx + w * 0.02 + lean * 0.4, by)], (m, 3))
    cv.poly([(tipx, by - h), (cx + w / 2, by), (cx + w * 0.25 + lean * 0.3, by)], (m, 1))
    cv.line([(cx - w * 0.22 + lean * 0.2, by - h * 0.15), (tipx - w * 0.06, by - h * 0.85)], max(0.4, w * 0.08), (m, 4))


def crystal(cv, cx, cy, h, w, ang=0.0, m='crystal'):
    """A faceted gem standing along ang (0 = up), centred at (cx, cy)."""
    top, bot = -h / 2, h / 2
    body = [(0, top), (w / 2, top + w * 0.6), (w / 2, bot - w * 0.6), (0, bot), (-w / 2, bot - w * 0.6), (-w / 2, top + w * 0.6)]
    cv.poly(rot(body, ang, cx, cy), (m, 2))
    cv.poly(rot([(0, top), (0, bot), (-w / 2, bot - w * 0.6), (-w / 2, top + w * 0.6)], ang, cx, cy), (m, 3))
    cv.poly(rot([(0, top), (w / 2, top + w * 0.6), (w / 2, bot - w * 0.6), (0, bot)], ang, cx, cy), (m, 1))
    cv.poly(rot([(-w * 0.32, top + w * 0.7), (-w * 0.14, top + w * 0.4), (-w * 0.14, bot - w * 0.9), (-w * 0.32, bot - w * 0.7)], ang, cx, cy), (m, 4))


def skull(cv, cx, cy, s, m='bone', eyes=None):
    cv.ellipse(cx, cy - 2 * s, 9 * s, 8.5 * s, (m, 2))
    cv.poly([(cx - 6 * s, cy + 2 * s), (cx + 6 * s, cy + 2 * s), (cx + 5 * s, cy + 9 * s), (cx - 5 * s, cy + 9 * s)], (m, 2))
    cv.ellipse(cx - 2.5 * s, cy - 4.5 * s, 5.5 * s, 4.5 * s, (m, 3))
    cv.ellipse(cx - 4 * s, cy - 6.5 * s, 2 * s, 1.4 * s, (m, 4))
    cv.poly([(cx + 3 * s, cy + 4 * s), (cx + 6 * s, cy + 2 * s), (cx + 5 * s, cy + 9 * s), (cx + 2 * s, cy + 9 * s)], (m, 1))
    for ex in (-3.4, 3.4):
        cv.ellipse(cx + ex * s, cy + 0.2 * s, 2.6 * s, 2.9 * s, 'ink')
        if eyes:
            cv.circle(cx + ex * s, cy + 0.6 * s, 1.1 * s, (eyes, 3))
            cv.circle(cx + ex * s - 0.3 * s, cy + 0.2 * s, 0.45 * s, (eyes, 4))
    cv.poly([(cx, cy + 3 * s), (cx + 1.3 * s, cy + 5.2 * s), (cx - 1.3 * s, cy + 5.2 * s)], 'ink')
    for tx in (-3, -1, 1, 3):
        cv.rect(cx + tx * s - 0.35 * s, cy + 7 * s, cx + tx * s + 0.35 * s, cy + 9.2 * s, (m, 1))


def soul(cv, cx, cy, s, m='soul', lean=0.0, face=True):
    """A wailing ghost: round head, a tail trailing down and to the side."""
    pts = []
    for i in range(41):
        t = i / 40
        a = math.pi * (1 + t)
        pts.append((cx + 5 * s * math.cos(a), cy + 5 * s * math.sin(a)))
    tail = [(cx + 5 * s, cy), (cx + 4 * s + lean * 0.3, cy + 7 * s), (cx + 2 * s + lean * 0.6, cy + 10 * s), (cx + lean, cy + 14 * s),
            (cx - 1 * s + lean * 0.6, cy + 9 * s), (cx - 3.5 * s + lean * 0.3, cy + 7 * s), (cx - 5 * s, cy)]
    cv.poly(pts + tail, (m, 2))
    cv.ellipse(cx - 1.5 * s, cy - 1 * s, 3 * s, 3.4 * s, (m, 3))
    cv.ellipse(cx - 2.2 * s, cy - 2.6 * s, 1.2 * s, 0.9 * s, (m, 4))
    if face:
        cv.ellipse(cx - 2 * s, cy - 0.2 * s, 1.1 * s, 1.4 * s, (m, 0))
        cv.ellipse(cx + 2 * s, cy - 0.2 * s, 1.1 * s, 1.4 * s, (m, 0))
        cv.ellipse(cx, cy + 2.8 * s, 1.1 * s, 1.6 * s, (m, 0))


def chain(cv, pts, r, m='chain'):
    """Chain links along a polyline, alternating face-on and edge-on."""
    total = 0.0
    segs = []
    for a, b in zip(pts, pts[1:]):
        ln = math.hypot(b[0] - a[0], b[1] - a[1])
        segs.append((a, b, ln))
        total += ln
    step = r * 1.7
    k = 0
    d = 0.0
    while d <= total:
        acc = 0.0
        for a, b, ln in segs:
            if acc + ln >= d:
                t = (d - acc) / ln if ln else 0
                x, y = lerp(a[0], b[0], t), lerp(a[1], b[1], t)
                ang = math.atan2(b[1] - a[1], b[0] - a[0])
                break
            acc += ln
        if k % 2 == 0:
            cv.poly(rot([(-r * 1.1, -r * 0.75), (r * 1.1, -r * 0.75), (r * 1.1, r * 0.75), (-r * 1.1, r * 0.75)], ang, x, y), (m, 2))
            cv.poly(rot([(-r * 1.1, -r * 0.75), (r * 1.1, -r * 0.75), (r * 1.1, -r * 0.2), (-r * 1.1, -r * 0.2)], ang, x, y), (m, 3))
            cv.poly(rot([(-r * 0.55, -r * 0.25), (r * 0.55, -r * 0.25), (r * 0.55, r * 0.25), (-r * 0.55, r * 0.25)], ang, x, y), 'ink')
        else:
            cv.poly(rot([(-r * 1.15, -r * 0.3), (r * 1.15, -r * 0.3), (r * 1.15, r * 0.3), (-r * 1.15, r * 0.3)], ang, x, y), (m, 1))
            cv.poly(rot([(-r * 1.15, -r * 0.3), (r * 1.15, -r * 0.3), (r * 1.15, 0), (-r * 1.15, 0)], ang, x, y), (m, 2))
        d += step
        k += 1


def vine(cv, pts, w, m='leaf', thorns=0, thorn_m='earth', leaves=0, seed=1):
    """A tapering vine along {@code pts}, with thorns and leaves along it."""
    n = len(pts)
    widths = [w * (1 - 0.75 * i / max(1, n - 1)) for i in range(n)]
    cv.stroke(pts, widths, (m, 1))
    cv.stroke(pts, [x * 0.55 for x in widths], (m, 2))
    rnd = random.Random(seed)
    for i in range(1, n - 1):
        x, y = pts[i]
        ax, ay = pts[i - 1]
        bx, by = pts[i + 1]
        dx, dy = bx - ax, by - ay
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        side = 1 if i % 2 else -1
        if thorns and i % max(1, (n - 2) // thorns) == 0:
            base = widths[i] * 0.5
            tl = widths[i] * 0.9 + 1.0
            cv.poly([(x + nx * base * side - dx / ln * 0.8, y + ny * base * side - dy / ln * 0.8),
                     (x + nx * (base + tl) * side + dx / ln * 0.6, y + ny * (base + tl) * side + dy / ln * 0.6),
                     (x + nx * base * side + dx / ln * 0.8, y + ny * base * side + dy / ln * 0.8)], (thorn_m, 3))
        if leaves and rnd.random() < leaves:
            lx, ly = x + nx * widths[i] * 0.4 * -side, y + ny * widths[i] * 0.4 * -side
            leaf(cv, lx, ly, math.atan2(ny * -side, nx * -side) + math.pi / 2, 3.2 + rnd.random() * 1.2)


def leaf(cv, cx, cy, ang, s, m='leaf'):
    """A leaf growing from (cx, cy) along ang (0 = up)."""
    pts = []
    for i in range(17):
        t = i / 16
        pts.append((s * 0.42 * math.sin(math.pi * t), -s * 2 * t))
    for i in range(16, -1, -1):
        t = i / 16
        pts.append((-s * 0.42 * math.sin(math.pi * t), -s * 2 * t))
    cv.poly(rot(pts, ang, cx, cy), (m, 2))
    cv.poly(rot(pts[:17] + [(0, 0)], ang, cx, cy), (m, 3))
    cv.line(rot([(0, -0.2 * s), (0, -1.7 * s)], ang, cx, cy), s * 0.12, (m, 1))


def flower(cv, cx, cy, r, petals=6, m='petal', centre='gold', ang=0.0):
    for k in range(petals):
        a = ang + k * 2 * math.pi / petals
        px, py = cx + r * 0.55 * math.sin(a), cy - r * 0.55 * math.cos(a)
        pts = rot([(0, -r * 0.62), (r * 0.36, -r * 0.1), (0, r * 0.5), (-r * 0.36, -r * 0.1)], a, px, py)
        cv.poly(pts, (m, 2))
        cv.poly(rot([(0, -r * 0.62), (0, r * 0.5), (-r * 0.36, -r * 0.1)], a, px, py), (m, 3))
    ball(cv, cx, cy, r * 0.3, centre)


def sun(cv, cx, cy, r, rays=12, m='light', ray_len=None, core=True):
    rl = ray_len or r * 0.75
    for k in range(rays):
        a = k * 2 * math.pi / rays
        long = k % 2 == 0
        L = r + (rl if long else rl * 0.6)
        w = r * (0.32 if long else 0.24)
        cv.poly([(cx + L * math.sin(a), cy - L * math.cos(a)),
                 (cx + r * 0.8 * math.sin(a + w / r), cy - r * 0.8 * math.cos(a + w / r)),
                 (cx + r * 0.8 * math.sin(a - w / r), cy - r * 0.8 * math.cos(a - w / r))], (m, 3 if long else 2))
    if core:
        ball(cv, cx, cy, r, m)


def _disc(x, y, r):
    mk = Image.new('L', (S, S), 0)
    ImageDraw.Draw(mk).ellipse([P(x - r, y - r), P(x + r, y + r)], fill=255)
    return mk


def crescent_moon(cv, cx, cy, r, m='holy', ang=0.0, cut=0.62):
    """A crescent: the disc less a disc shifted toward ang (radians, 0 = right), lit along its back."""
    hole = _disc(cx + r * cut * math.cos(ang), cy + r * cut * math.sin(ang), r * 0.92)
    body = ImageChops.subtract(_disc(cx, cy, r), hole)
    cv.img.paste(Image.new('RGBA', (S, S), Canvas.col((m, 2))), (0, 0), body)
    lit = ImageChops.subtract(_disc(cx - r * 0.12 * math.cos(ang), cy - r * 0.12 * math.sin(ang), r * 0.86), hole)
    lit = ImageChops.subtract(lit, ImageChops.subtract(_disc(cx, cy, r), _disc(cx - r * 0.1 * math.cos(ang - 0.6), cy - r * 0.1 * math.sin(ang - 0.6), r * 0.97)))
    cv.img.paste(Image.new('RGBA', (S, S), Canvas.col((m, 3))), (0, 0), lit)
    dark = ImageChops.subtract(body, _disc(cx - r * 0.04 * math.cos(ang + 1.2), cy - r * 0.04 * math.sin(ang + 1.2), r * 0.97))
    cv.img.paste(Image.new('RGBA', (S, S), Canvas.col((m, 1))), (0, 0), dark)


def cloud(cv, cx, cy, w, m='cloud'):
    puffs = [(-0.32, 0.05, 0.24), (-0.1, -0.15, 0.3), (0.18, -0.08, 0.26), (0.36, 0.08, 0.18), (0.0, 0.1, 0.28)]
    for dx, dy, r in puffs:
        cv.circle(cx + dx * w, cy + dy * w, r * w, (m, 1))
    for dx, dy, r in puffs:
        cv.circle(cx + dx * w - 0.03 * w, cy + dy * w - 0.04 * w, r * w * 0.85, (m, 2))
    for dx, dy, r in puffs[:3]:
        cv.circle(cx + dx * w - 0.06 * w, cy + dy * w - 0.08 * w, r * w * 0.55, (m, 3))


def drop(cv, cx, cy, s, m='water'):
    pts = []
    for i in range(33):
        t = i / 32
        a = math.pi * 2 * t
        pts.append((cx + s * 0.6 * math.sin(a) * (0.2 + 0.8 * (1 - (math.cos(a) + 1) / 2) ** 0.5), cy - s * math.cos(a) * 0.9))
    cv.poly(pts, (m, 2))
    cv.ellipse(cx - s * 0.18, cy + s * 0.15, s * 0.22, s * 0.32, (m, 3))
    cv.ellipse(cx - s * 0.22, cy + s * 0.05, s * 0.08, s * 0.14, (m, 4))


def wave(cv, x0, x1, y, h, m='water', crest=True):
    """A breaking wave over [x0, x1] at baseline y, {@code h} tall, curling to the right."""
    pts = [(x0, y)]
    for i in range(41):
        t = i / 40
        x = lerp(x0, x1, t)
        yy = y - h * (math.sin(math.pi * t * 0.85) ** 1.5)
        pts.append((x, yy))
    pts.append((x1, y))
    cv.poly(pts, (m, 2))
    cv.poly([(x0, y)] + [(lerp(x0, x1, i / 40), y - h * 0.65 * (math.sin(math.pi * i / 40 * 0.85) ** 1.5)) for i in range(41)] + [(x1, y)], (m, 1))
    if crest:
        cx, cy = lerp(x0, x1, 0.58), y - h * 0.95
        cv.arc_band(cx + h * 0.05, cy + h * 0.32, h * 0.18, h * 0.36, 180, 360, (m, 4), taper=True)


def rock(cv, cx, cy, r, m='stone', seed=3):
    rnd = random.Random(seed)
    n = 8
    pts = []
    for i in range(n):
        a = i * 2 * math.pi / n + rnd.uniform(-0.2, 0.2)
        rr = r * rnd.uniform(0.78, 1.05)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a) * 0.85))
    cv.poly(pts, (m, 2))
    cv.poly([pts[3], pts[4], pts[5], pts[6], (cx - r * 0.1, cy - r * 0.05)], (m, 3))
    cv.poly([pts[0], pts[1], pts[2], (cx + r * 0.1, cy + r * 0.1)], (m, 1))


def pillar(cv, cx, by, h, w, m='stone', jag=True):
    """A rock pillar bursting up from (cx, by), its top broken."""
    top = by - h
    pts = [(cx - w / 2, by), (cx - w / 2 * 0.9, top + w * 0.4), (cx - w * 0.15, top), (cx + w * 0.1, top + w * 0.3), (cx + w * 0.3, top + w * 0.05),
           (cx + w / 2 * 0.95, top + w * 0.5), (cx + w / 2, by)]
    cv.poly(pts, (m, 2))
    cv.poly([(cx - w / 2, by), (cx - w / 2 * 0.9, top + w * 0.4), (cx - w * 0.15, top), (cx - w * 0.1, by)], (m, 3))
    cv.poly([(cx + w * 0.3, top + w * 0.05), (cx + w / 2 * 0.95, top + w * 0.5), (cx + w / 2, by), (cx + w * 0.22, by)], (m, 1))
    for f in (0.35, 0.65):
        y = lerp(top + w * 0.5, by, f)
        cv.line([(cx - w * 0.35, y), (cx + w * 0.1, y + 0.6)], 0.5, (m, 1))


def ground(cv, x0, x1, y, depth, m='earth', top='leaf'):
    """A slab of ground seen side-on: a turf (or other) top over earth."""
    cv.poly([(x0, y), (x1, y), (x1 - depth * 0.6, y + depth), (x0 + depth * 0.6, y + depth)], (m, 1))
    cv.poly([(x0, y), (x1, y), (x1 - depth * 0.2, y + depth * 0.35), (x0 + depth * 0.2, y + depth * 0.35)], (m, 2))
    if top:
        cv.poly([(x0 - 0.3, y - 1.2), (x1 + 0.3, y - 1.2), (x1, y + 0.6), (x0, y + 0.6)], (top, 2))
        cv.poly([(x0 - 0.3, y - 1.2), (x1 + 0.3, y - 1.2), (x1, y - 0.4), (x0, y - 0.4)], (top, 3))


def crack(cv, pts, w, glow=None):
    cv.line(pts, w, 'ink')
    if glow:
        cv.line(pts, w * 0.45, (glow, 3))


def spiral(cv, cx, cy, r, turns, w, m, ang0=0.0, steps=120):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        a = ang0 + turns * 2 * math.pi * t
        rr = r * (1 - t) + r * 0.08 * t
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    widths = [w * (1 - 0.7 * i / steps) for i in range(steps + 1)]
    cv.stroke(pts, widths, m)


def eye(cv, cx, cy, s, m='void', iris='gold'):
    up, lo = [], []
    for i in range(25):
        t = i / 24
        x = cx + lerp(-s, s, t)
        h = s * 0.6 * math.sin(math.pi * t)
        up.append((x, cy - h))
        lo.append((x, cy + h))
    cv.poly(up + lo[::-1], (m, 4))
    ball(cv, cx, cy, s * 0.5, iris)
    cv.ellipse(cx, cy, s * 0.14, s * 0.42, 'ink')
    cv.stroke(up, [s * 0.18] * len(up), (m, 1))


def shield(cv, cx, cy, s, m='steel', boss='gold'):
    pts = [(cx - 9 * s, cy - 10 * s), (cx + 9 * s, cy - 10 * s), (cx + 9 * s, cy - 1 * s), (cx + 6 * s, cy + 6 * s), (cx, cy + 11 * s),
           (cx - 6 * s, cy + 6 * s), (cx - 9 * s, cy - 1 * s)]
    cv.poly(pts, (m, 2))
    cv.poly([(cx - 9 * s, cy - 10 * s), (cx, cy - 10 * s), (cx, cy + 11 * s), (cx - 6 * s, cy + 6 * s), (cx - 9 * s, cy - 1 * s)], (m, 3))
    cv.poly([(cx - 9 * s, cy - 10 * s), (cx + 9 * s, cy - 10 * s), (cx + 9 * s, cy - 8.4 * s), (cx - 9 * s, cy - 8.4 * s)], (boss, 3))
    cv.line([(cx, cy - 8.4 * s), (cx, cy + 10 * s)], 1.3 * s, (boss, 2))


def bell(cv, cx, cy, s, m='gold'):
    pts = [(cx - 2 * s, cy - 9 * s), (cx + 2 * s, cy - 9 * s), (cx + 4 * s, cy - 7 * s), (cx + 5 * s, cy + 2 * s), (cx + 8 * s, cy + 6 * s),
           (cx - 8 * s, cy + 6 * s), (cx - 5 * s, cy + 2 * s), (cx - 4 * s, cy - 7 * s)]
    cv.poly(pts, (m, 2))
    cv.poly([(cx - 2 * s, cy - 9 * s), (cx, cy - 9 * s), (cx - 1 * s, cy + 6 * s), (cx - 8 * s, cy + 6 * s), (cx - 5 * s, cy + 2 * s), (cx - 4 * s, cy - 7 * s)], (m, 3))
    cv.poly([(cx - 8.6 * s, cy + 5 * s), (cx + 8.6 * s, cy + 5 * s), (cx + 8.6 * s, cy + 7 * s), (cx - 8.6 * s, cy + 7 * s)], (m, 1))
    cv.ring(cx, cy - 10 * s, 1.6 * s, 0.8 * s, (m, 1))
    ball(cv, cx, cy + 8.2 * s, 1.8 * s, m)


def halo(cv, cx, cy, rx, ry, w, m='light'):
    cv.ering(cx, cy, rx, ry, w, (m, 1))
    cv.ering(cx, cy - w * 0.15, rx - w * 0.15, ry - w * 0.1, w * 0.6, (m, 3))


def book(cv, cx, cy, s, m='wood', pages='bone', open_=True):
    """An open book, seen from above, its pages fanning."""
    if open_:
        for side in (-1, 1):
            cv.poly([(cx, cy - 6 * s), (cx + side * 12 * s, cy - 8 * s), (cx + side * 12.5 * s, cy + 6 * s), (cx, cy + 8 * s)], (m, 1))
            cv.poly([(cx, cy - 6.5 * s), (cx + side * 11 * s, cy - 8.8 * s), (cx + side * 11.4 * s, cy + 4.8 * s), (cx, cy + 6.8 * s)], (pages, 3 if side < 0 else 2))
            for k in range(3):
                y0 = cy - 4.5 * s + k * 3 * s
                cv.line([(cx + side * 2.2 * s, y0 - side * 0.0), (cx + side * 9 * s, y0 - 1.4 * s)], 0.5 * s, (pages, 1))
    else:
        cv.rect(cx - 9 * s, cy - 11 * s, cx + 9 * s, cy + 11 * s, (m, 2))


def figure(cv, cx, by, s, m='shadow'):
    """A standing figure, small, for a foe or an ally."""
    cv.circle(cx, by - 13 * s, 2.6 * s, (m, 2))
    cv.poly([(cx - 3.4 * s, by - 10 * s), (cx + 3.4 * s, by - 10 * s), (cx + 2.6 * s, by - 3 * s), (cx - 2.6 * s, by - 3 * s)], (m, 2))
    cv.poly([(cx - 2.4 * s, by - 3 * s), (cx - 0.4 * s, by - 3 * s), (cx - 0.8 * s, by), (cx - 2.6 * s, by)], (m, 1))
    cv.poly([(cx + 0.4 * s, by - 3 * s), (cx + 2.4 * s, by - 3 * s), (cx + 2.6 * s, by), (cx + 0.8 * s, by)], (m, 1))
    cv.poly([(cx - 3.4 * s, by - 10 * s), (cx, by - 10 * s), (cx - 0.2 * s, by - 3 * s), (cx - 2.6 * s, by - 3 * s)], (m, 3))


def streaks(cv, pts_list, w, m):
    for pts in pts_list:
        cv.stroke(pts, [w * (i / (len(pts) - 1)) + 0.2 for i in range(len(pts))], m)


def rune_circle(cv, cx, cy, r, m='rune', ticks=12):
    cv.ring(cx, cy, r, r * 0.09, (m, 2))
    cv.ring(cx, cy, r * 0.74, r * 0.06, (m, 3))
    for k in range(ticks):
        a = k * 2 * math.pi / ticks
        cv.circle(cx + r * 0.87 * math.cos(a), cy + r * 0.87 * math.sin(a), r * 0.06, (m, 4))
    for k in range(3):
        a0 = k * 2 * math.pi / 3 - math.pi / 2
        a1 = a0 + 2 * math.pi / 3
        cv.line([(cx + r * 0.72 * math.cos(a0), cy + r * 0.72 * math.sin(a0)), (cx + r * 0.72 * math.cos(a1), cy + r * 0.72 * math.sin(a1))], r * 0.05, (m, 3))


def slash(cv, cx, cy, r, a0, a1, w, m, glint=True):
    """A crescent slash of energy: outer edge bright, trailing thin."""
    cv.arc_band(cx, cy, r - w, r, a0, a1, (m, 2))
    cv.arc_band(cx, cy, r - w * 0.55, r, a0 + (a1 - a0) * 0.08, a1 - (a1 - a0) * 0.04, (m, 3))
    if glint:
        cv.arc_band(cx, cy, r - w * 0.2, r, a0 + (a1 - a0) * 0.2, a1 - (a1 - a0) * 0.1, (m, 4))


def heart(cv, cx, cy, s, m='blood'):
    pts = []
    for i in range(61):
        t = i / 60 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * s / 16, cy + y * s / 16))
    cv.poly(pts, (m, 2))
    cv.ellipse(cx - s * 0.42, cy - s * 0.42, s * 0.36, s * 0.3, (m, 3))
    cv.ellipse(cx - s * 0.5, cy - s * 0.52, s * 0.12, s * 0.09, (m, 4))


def wing(cv, cx, cy, s, side=1, m='holy'):
    """A wing spreading from (cx, cy) to the side (1 right, -1 left), feathered."""
    for k, (ang, ln, w) in enumerate(((-70, 16, 4.2), (-48, 18, 4), (-26, 17, 3.8), (-6, 14, 3.4), (12, 10, 3))):
        a = math.radians(ang)
        tip = (cx + side * ln * s * math.cos(a), cy + ln * s * math.sin(a))
        nx, ny = -math.sin(a) * w * s * 0.5, math.cos(a) * w * s * 0.5
        cv.poly([(cx, cy), (tip[0] + side * nx * 0.3, tip[1] + ny * 0.3), (cx + side * (ln * 0.6 * s * math.cos(a)) + side * nx, cy + ln * 0.6 * s * math.sin(a) + ny)], (m, 2 if k % 2 else 3))
    cv.circle(cx, cy, 2.4 * s, (m, 3))


def note(cv, cx, cy, s, m='gold'):
    cv.ellipse(cx, cy, 2.8 * s, 2.1 * s, (m, 2))
    cv.ellipse(cx - 0.8 * s, cy - 0.6 * s, 1.1 * s, 0.7 * s, (m, 4))
    cv.rect(cx + 2.0 * s, cy - 12 * s, cx + 3.0 * s, cy, (m, 2))
    cv.poly([(cx + 2.0 * s, cy - 12 * s), (cx + 7 * s, cy - 9 * s), (cx + 6.5 * s, cy - 6.5 * s), (cx + 3 * s, cy - 8.5 * s)], (m, 3))


def tree(cv, cx, by, s, m='leaf', trunk='wood'):
    cv.poly([(cx - 2 * s, by), (cx + 2 * s, by), (cx + 1.4 * s, by - 12 * s), (cx - 1.4 * s, by - 12 * s)], (trunk, 2))
    cv.poly([(cx - 2 * s, by), (cx, by), (cx, by - 12 * s), (cx - 1.4 * s, by - 12 * s)], (trunk, 3))
    for dx, dy, r in ((0, -22, 9), (-7, -16, 7), (7, -16, 7), (-4, -26, 6), (5, -25, 6)):
        cv.circle(cx + dx * s, by + dy * s, r * s, (m, 1))
    for dx, dy, r in ((0, -22, 7.5), (-7, -16, 5.5), (7, -16, 5.5), (-4, -26, 4.6), (5, -25, 4.6)):
        cv.circle(cx + dx * s - 0.6 * s, by + dy * s - 0.8 * s, r * s, (m, 2))
    for dx, dy, r in ((-2, -25, 4), (-8, -18, 3), (3, -28, 2.6)):
        cv.circle(cx + dx * s, by + dy * s, r * s, (m, 3))


def trident(cv, cx, cy, ang, s, m='gold'):
    cv.poly(rot([(-1.1 * s, -10 * s), (1.1 * s, -10 * s), (1.1 * s, 22 * s), (-1.1 * s, 22 * s)], ang, cx, cy), (m, 1))
    cv.poly(rot([(-1.1 * s, -10 * s), (0, -10 * s), (0, 22 * s), (-1.1 * s, 22 * s)], ang, cx, cy), (m, 2))
    cv.poly(rot([(-8 * s, -10 * s), (8 * s, -10 * s), (8 * s, -7.5 * s), (-8 * s, -7.5 * s)], ang, cx, cy), (m, 2))
    for x in (-7, 0, 7):
        tip = -24 * s if x == 0 else -20 * s
        cv.poly(rot([(x * s - 1.3 * s, -10 * s), (x * s + 1.3 * s, -10 * s), (x * s + 1.3 * s, tip + 3 * s), (x * s, tip), (x * s - 1.3 * s, tip + 3 * s)], ang, cx, cy), (m, 3))
        cv.poly(rot([(x * s - 1.3 * s, -10 * s), (x * s, -10 * s), (x * s, tip), (x * s - 1.3 * s, tip + 3 * s)], ang, cx, cy), (m, 4))


def cube(cv, cx, cy, r, m='rune', tones=(1, 2, 3)):
    """An isometric cube centred at (cx, cy)."""
    h = r * 0.5
    top = [(cx, cy - r), (cx + r * 0.87, cy - h), (cx, cy), (cx - r * 0.87, cy - h)]
    left = [(cx - r * 0.87, cy - h), (cx, cy), (cx, cy + r), (cx - r * 0.87, cy + h)]
    right = [(cx + r * 0.87, cy - h), (cx + r * 0.87, cy + h), (cx, cy + r), (cx, cy)]
    cv.poly(top, (m, tones[2]))
    cv.poly(left, (m, tones[1]))
    cv.poly(right, (m, tones[0]))


def blackhole(cv, cx, cy, r, m='void', disc=True):
    if disc:
        cv.ellipse(cx, cy, r * 2.1, r * 0.62, (m, 2))
        cv.ellipse(cx, cy, r * 1.75, r * 0.42, (m, 3))
        cv.ellipse(cx, cy, r * 1.35, r * 0.26, (m, 4))
    cv.circle(cx, cy, r * 1.08, (m, 4))
    cv.circle(cx, cy, r, 'ink')
    if disc:
        cv.poly([(cx - r * 2.1, cy), (cx + r * 2.1, cy), (cx + r * 1.4, cy + r * 0.5), (cx - r * 1.4, cy + r * 0.5)], None)
        cv.arc_band(cx, cy, r * 1.2, r * 2.1, 10, 170, (m, 3), taper=True)


def volcano(cv, cx, by, s, m='stone', lava='lava'):
    cv.poly([(cx - 20 * s, by), (cx - 6 * s, by - 18 * s), (cx + 6 * s, by - 18 * s), (cx + 20 * s, by)], (m, 1))
    cv.poly([(cx - 20 * s, by), (cx - 6 * s, by - 18 * s), (cx, by - 18 * s), (cx - 4 * s, by)], (m, 2))
    cv.poly([(cx - 6 * s, by - 18 * s), (cx + 6 * s, by - 18 * s), (cx + 4 * s, by - 15 * s), (cx - 4 * s, by - 15 * s)], (lava, 3))
    cv.poly([(cx - 1.5 * s, by - 16 * s), (cx + 1.8 * s, by - 16 * s), (cx + 5 * s, by - 7 * s), (cx + 3 * s, by - 6 * s)], (lava, 2))
    cv.poly([(cx - 3 * s, by - 16 * s), (cx - 1 * s, by - 16 * s), (cx - 7 * s, by - 4 * s), (cx - 9 * s, by - 5 * s)], (lava, 2))


def bubble(cv, cx, cy, r, m='water'):
    cv.ring(cx, cy, r, r * 0.12, (m, 2))
    cv.circle(cx, cy, r * 0.95, (m, 3), 70)
    cv.arc_band(cx, cy, r * 0.7, r * 0.86, 200, 260, (m, 4))
    cv.circle(cx + r * 0.45, cy + r * 0.42, r * 0.09, (m, 4))


def tornado(cv, cx, by, h, w, m='fire'):
    for k in range(6):
        t = k / 5
        y = by - h * t
        rw = w * (0.25 + 0.75 * t)
        off = math.sin(t * 5) * w * 0.12
        cv.ellipse(cx + off, y, rw / 2, h * 0.07 + 0.6, (m, 1 + (k % 3)))
    for k in range(5):
        t = (k + 0.5) / 5
        y = by - h * t
        rw = w * (0.25 + 0.75 * t)
        cv.arc_band(cx + math.sin(t * 5) * w * 0.12, y, rw * 0.3, rw * 0.5, 200, 340, (m, 4))


def hook(cv, cx, cy, s, m='steel'):
    pts = []
    for i in range(21):
        a = math.radians(lerp(-10, 200, i / 20))
        pts.append((cx + 6 * s * math.cos(a), cy + 6 * s * math.sin(a)))
    cv.stroke([(cx + 6 * s, cy - 14 * s), (cx + 6 * s, cy - 4 * s)] + pts, [2.4 * s] * (len(pts) + 2), (m, 2))
    cv.stroke([(cx + 5.6 * s, cy - 14 * s), (cx + 5.6 * s, cy - 4 * s)] + [(x - 0.3 * s, y - 0.3 * s) for x, y in pts], [1.0 * s] * (len(pts) + 2), (m, 4))
    ex, ey = pts[-1]
    cv.poly([(ex - 1.5 * s, ey + 1 * s), (ex + 3.5 * s, ey - 4 * s), (ex + 1.5 * s, ey + 2 * s)], (m, 3))


def dragon_head(cv, cx, cy, s, ang=0.0, m='fire', horn='bone'):
    """A dragon's head facing along ang (0 = right), its jaws open."""
    upper = [(-10, -4), (2, -6), (12, -3), (14, -1), (6, 0), (-6, 1)]
    lower = [(-6, 2), (6, 2), (12, 5), (2, 6), (-8, 6)]
    cv.poly(rot([(x * s, y * s) for x, y in upper], ang, cx, cy), (m, 2))
    cv.poly(rot([(x * s, y * s) for x, y in upper[:3] + [(0, -2)]], ang, cx, cy), (m, 3))
    cv.poly(rot([(x * s, y * s) for x, y in lower], ang, cx, cy), (m, 1))
    for hx in (-6, -2):
        cv.poly(rot([(hx * s, -5 * s), ((hx - 6) * s, -11 * s), ((hx + 2) * s, -5.5 * s)], ang, cx, cy), (horn, 3))
    cv.circle(*rot([(4 * s, -3.6 * s)], ang, cx, cy)[0], 1.1 * s, (('gold', 4)))
    for tx in (4, 8, 11):
        cv.poly(rot([(tx * s, -0.5 * s), ((tx + 1) * s, 1.5 * s), ((tx + 2) * s, -0.5 * s)], ang, cx, cy), (horn, 4))


def bird(cv, cx, cy, s, m='fire'):
    """A phoenix seen from the front, wings spread, a trailing tail of flame."""
    for side in (-1, 1):
        for k, (a, ln) in enumerate(((-35, 20), (-15, 22), (5, 18), (25, 13))):
            ang = math.radians(a)
            tip = (cx + side * ln * s * math.cos(ang), cy + ln * s * math.sin(ang))
            base = (cx + side * 2 * s, cy + (k - 1) * 1.6 * s)
            cv.poly([base, tip, (base[0] + side * 4 * s, base[1] + 4 * s)], (m, 2 + (k % 2)))
    flame(cv, cx, cy + 20 * s, 18 * s, 8 * s, m)
    cv.ellipse(cx, cy + 2 * s, 3.4 * s, 6 * s, (m, 3))
    cv.circle(cx, cy - 5 * s, 2.8 * s, (m, 3))
    cv.poly([(cx, cy - 4 * s), (cx + 1.2 * s, cy - 2.5 * s), (cx - 1.2 * s, cy - 2.5 * s)], ('gold', 4))
    for k in (-1, 0, 1):
        cv.poly([(cx + k * 1.6 * s, cy - 7 * s), (cx + k * 3.5 * s, cy - 12 * s), (cx + k * 1.6 * s + 1 * s, cy - 7 * s)], (m, 4))


ICONS = {}


def icon(weapon, name, fill=0.9):
    def deco(fn):
        ICONS[(weapon, name)] = (fn, fill)
        return fn
    return deco


def render(key):
    fn, fill = ICONS[key]
    cv = Canvas()
    fn(cv)
    return cv.finish(fill)


# ============================================================================================================
# Solaris: the sun's sword
# ============================================================================================================

@icon('solaris', 'radiant_slash')
def radiant_slash(cv):
    slash(cv, 26, 28, 19, 200, 330, 7, 'fire')
    blade(cv, 30, 34, math.radians(40), 22, 4.2, 'light', 'gold', 'blood', 'fire')


@icon('solaris', 'solar_burst')
def solar_burst(cv):
    sun(cv, 24, 22, 9, 16, 'fire', ray_len=10)
    ball(cv, 24, 22, 6, 'light')
    ground(cv, 6, 42, 38, 6, 'earth', 'stone')
    blade(cv, 24, 32, math.radians(180), 16, 3.6, 'light', 'gold', 'blood', 'fire')


@icon('solaris', 'sunfire')
def sunfire(cv):
    flame(cv, 24, 44, 34, 24, 'fire')
    sun(cv, 24, 32, 5.2, 10, 'light', ray_len=3.2)


@icon('solaris', 'supernova')
def supernova(cv):
    streaks(cv, [[(4, 44), (12, 36), (19, 30)], [(2, 34), (10, 30), (16, 27)], [(12, 46), (18, 40), (23, 34)]], 2.6, ('fire', 3))
    sun(cv, 28, 22, 9, 14, 'fire', ray_len=8)
    ball(cv, 28, 22, 6.4, 'holy')


@icon('solaris', 'celestial_inferno')
def celestial_inferno(cv):
    for x, h in ((12, 26), (24, 30), (36, 24)):
        cv.rect(x - 2.6, 44 - h, x + 2.6, 44, ('fire', 2))
        cv.rect(x - 1.4, 44 - h, x + 1.4, 44, ('fire', 3))
        cv.rect(x - 0.5, 44 - h, x + 0.5, 44, ('fire', 4))
        flame(cv, x, 46, 8, 9, 'fire')
    sun(cv, 24, 10, 7, 14, 'light', ray_len=5)


# ============================================================================================================
# Frostrend: the frost sword
# ============================================================================================================

@icon('frostrend', 'frost_slash')
def frost_slash(cv):
    slash(cv, 22, 26, 19, 210, 345, 7, 'ice')
    for x, y, h in ((10, 40, 7), (17, 43, 9), (26, 44, 6)):
        shard(cv, x, y, h, 3.4, 0.6)


@icon('frostrend', 'ice_spikes')
def ice_spikes(cv):
    ground(cv, 2, 46, 40, 5, 'stone', 'ice')
    for x, h, w, lean in ((7, 9, 5, -0.6), (15, 15, 6.5, 0.4), (25, 22, 8, -0.4), (36, 30, 10, 0.8)):
        shard(cv, x, 40, h, w, lean)


@icon('frostrend', 'frostbite')
def frostbite(cv):
    for x, l in ((12, 13), (20, 16), (28, 16), (36, 13)):
        cv.poly([(x - 3.6, 6), (x + 3.6, 6), (x, 6 + l)], ('ice', 2))
        cv.poly([(x - 3.6, 6), (x, 6), (x, 6 + l)], ('ice', 3))
    for x, l in ((16, 12), (24, 14), (32, 12)):
        cv.poly([(x - 3.4, 44), (x + 3.4, 44), (x, 44 - l)], ('ice', 1))
        cv.poly([(x - 3.4, 44), (x, 44), (x, 44 - l)], ('ice', 2))
    cv.rect(6, 3, 42, 6.5, ('ice', 1))
    cv.rect(8, 44, 40, 47, ('ice', 0))
    snowflake(cv, 24, 25, 7)


@icon('frostrend', 'glacial_domain')
def glacial_domain(cv):
    cv.ellipse(24, 38, 22, 7, ('ice', 1))
    cv.ellipse(24, 37, 19, 5.4, ('ice', 3))
    cv.ellipse(20, 36, 9, 2, ('ice', 4))
    for x, h, w, lean in ((8, 10, 4, -1.5), (15, 15, 5, -0.6), (33, 15, 5, 0.6), (40, 10, 4, 1.5), (24, 20, 6, 0)):
        shard(cv, x, 39, h, w, lean)
    snowflake(cv, 24, 9, 7)


@icon('frostrend', 'absolute_zero')
def absolute_zero(cv):
    cube(cv, 24, 24, 17, 'ice', (1, 2, 3))
    figure(cv, 24, 33, 1.25, 'night')
    cv.poly([(24, 7), (38.8, 15.5), (24, 24), (9.2, 15.5)], ('ice', 4), 90)
    cv.poly([(9.2, 15.5), (24, 24), (24, 41), (9.2, 32.5)], ('ice', 3), 110)
    cv.poly([(38.8, 15.5), (38.8, 32.5), (24, 41), (24, 24)], ('ice', 2), 120)
    crack(cv, [(24, 24), (20, 30), (23, 34), (18, 40)], 0.8, 'ice')
    crack(cv, [(24, 24), (31, 27), (29, 33), (35, 37)], 0.8, 'ice')
    for x, y, a in ((7, 10, -0.6), (41, 9, 0.7), (7, 38, -2.2), (41, 39, 2.4)):
        crystal(cv, x, y, 6, 3, a, 'ice')


# ============================================================================================================
# Bloomfall: the blooming scythe
# ============================================================================================================

@icon('bloomfall', 'thorn_sweep')
def thorn_sweep(cv):
    slash(cv, 24, 30, 19, 195, 340, 6, 'leaf')
    ground(cv, 4, 44, 40, 5, 'earth', 'leaf')
    for x, h, lean, seed in ((10, 14, -3, 1), (18, 19, 2, 2), (28, 17, -2, 3), (37, 12, 3, 4)):
        vine(cv, [(x, 40), (x + lean * 0.3, 40 - h * 0.35), (x - lean * 0.2, 40 - h * 0.7), (x + lean, 40 - h)], 3.2, 'leaf', thorns=3, seed=seed)


@icon('bloomfall', 'entangling_roots')
def entangling_roots(cv):
    figure(cv, 24, 38, 1.7, 'shadow')
    ground(cv, 4, 44, 39, 6, 'earth', 'leaf')
    vine(cv, [(12, 41), (14, 30), (22, 25), (30, 22), (33, 14)], 3.4, 'wood', thorns=3, thorn_m='bone', seed=5)
    vine(cv, [(36, 41), (32, 33), (24, 31), (17, 27), (15, 19)], 3.2, 'wood', thorns=3, thorn_m='bone', seed=6)
    vine(cv, [(20, 42), (22, 37), (29, 35), (31, 30)], 2.6, 'wood', thorns=2, thorn_m='bone', seed=7)


@icon('bloomfall', 'overgrowth')
def overgrowth(cv):
    ground(cv, 2, 46, 38, 7, 'earth', 'leaf')
    for x, h, lean, seed in ((7, 18, -4, 1), (15, 26, 3, 2), (33, 24, -3, 3), (41, 16, 4, 4)):
        vine(cv, [(x, 38), (x + lean * 0.4, 38 - h * 0.4), (x - lean * 0.3, 38 - h * 0.75), (x + lean, 38 - h)], 3.4, 'leaf', thorns=2, leaves=0.7, seed=seed)
    flower(cv, 24, 22, 8, 6, 'petal')
    flower(cv, 13, 11, 4.5, 5, 'petal')
    flower(cv, 36, 13, 4.5, 5, 'petal')


@icon('bloomfall', 'venom_bloom')
def venom_bloom(cv):
    for x, y, r in ((12, 12, 5), (20, 7, 4), (31, 9, 5.5), (38, 16, 4), (26, 15, 3.4)):
        ball(cv, x, y, r, 'poison')
    vine(cv, [(24, 46), (23, 38), (25, 32)], 3, 'leaf', seed=2)
    leaf(cv, 23, 41, math.radians(-60), 3.6)
    leaf(cv, 24, 38, math.radians(60), 3.4)
    flower(cv, 24, 28, 10, 5, 'poison', 'void')


@icon('bloomfall', 'wrath_of_the_wild')
def wrath_of_the_wild(cv):
    for x, h, lean, w in ((5, 22, -2, 5), (12, 30, 1, 6), (36, 30, -1, 6), (43, 22, 2, 5)):
        shard(cv, x, 46, h, w, lean, 'earth')
    flower(cv, 24, 22, 17, 8, 'petal', 'gold')
    flower(cv, 24, 22, 9, 6, 'crystal', 'gold', ang=math.pi / 6)


# ============================================================================================================
# Aetherlance: the celestial lance
# ============================================================================================================

def aether_dart(cv, x0, y0, x1, y1, w, m='storm'):
    """A bolt of aether: a long bright diamond from (x0, y0) to its point at (x1, y1)."""
    ang = math.atan2(x1 - x0, -(y1 - y0))
    L = math.hypot(x1 - x0, y1 - y0)
    cv.poly(rot([(0, -L), (w, -L * 0.72), (0, 0), (-w, -L * 0.72)], ang, x0, y0), (m, 2))
    cv.poly(rot([(0, -L), (0, 0), (-w, -L * 0.72)], ang, x0, y0), (m, 3))
    cv.poly(rot([(0, -L * 0.96), (w * 0.35, -L * 0.72), (0, -L * 0.2), (-w * 0.35, -L * 0.72)], ang, x0, y0), (m, 4))


@icon('aetherlance', 'aether_bolt')
def aether_bolt(cv):
    lance(cv, 4, 44, 20, 28, 3.2)
    streaks(cv, [[(14, 30), (22, 22)], [(18, 34), (25, 27)]], 1.8, ('storm', 3))
    aether_dart(cv, 21, 27, 44, 4, 4.4)


@icon('aetherlance', 'aether_resonance')
def aether_resonance(cv):
    for r, w in ((19, 1.4), (14, 1.6), (9, 1.8)):
        cv.ring(30, 18, r, w, ('storm', 3 if r < 15 else 2))
    lance(cv, 4, 44, 30, 18, 4)
    aether_dart(cv, 34, 14, 45, 3, 2.6)


@icon('aetherlance', 'celestial_barrage')
def celestial_barrage(cv):
    for ang in (-62, -31, 0, 31, 62):
        a = math.radians(ang)
        aether_dart(cv, 24 + 8 * math.sin(a), 30 - 8 * math.cos(a), 24 + 22 * math.sin(a), 30 - 22 * math.cos(a), 2.6)
    for k in range(5):
        a = math.radians(-60 + k * 30)
        cv.poly(rot([(0, -9), (3, -3), (0, 0), (-3, -3)], a, 24, 32), ('holy', 2 if k % 2 else 3))
    lance(cv, 24, 47, 24, 30, 3.4)


@icon('aetherlance', 'judgement_ray')
def judgement_ray(cv):
    ang = math.atan2(44 - 12, -(4 - 36))
    for w, tone in ((9, 1), (6.4, 2), (3.8, 3), (1.6, 4)):
        cv.poly(rot([(-w, -46), (w, -46), (w * 0.6, 0), (-w * 0.6, 0)], ang, 14, 34), ('holy', tone))
    lance(cv, 2, 46, 15, 33, 4.2, 'gold', 'holy')
    sun(cv, 16, 32, 3.4, 8, 'holy', ray_len=3)


@icon('aetherlance', 'piercing_charge')
def piercing_charge(cv):
    streaks(cv, [[(2, 18), (12, 20), (22, 21)], [(4, 28), (14, 27), (24, 26)], [(6, 36), (14, 33), (22, 31)]], 3, ('holy', 3))
    lance(cv, 10, 30, 46, 22, 4.6)
    figure(cv, 39, 40, 1.15, 'shadow')


# ============================================================================================================
# Cinderspire: the volcano's staff
# ============================================================================================================

@icon('cinderspire', 'flame_geysers')
def flame_geysers(cv):
    ground(cv, 2, 46, 40, 6, 'stone', None)
    for x, h, w in ((10, 18, 8), (24, 30, 10), (38, 22, 8)):
        cv.poly([(x - w * 0.4, 41), (x + w * 0.4, 41), (x + w * 0.2, 39), (x - w * 0.2, 39)], ('lava', 2))
        flame(cv, x, 40, h, w, 'fire')


@icon('cinderspire', 'heart_of_the_volcano')
def heart_of_the_volcano(cv):
    volcano(cv, 24, 44, 1.1)
    for x, y, r, dx, dy in ((12, 14, 3, -1, -1.2), (24, 6, 3.6, 0, -1), (36, 13, 3, 1, -1.2)):
        fireball(cv, x, y, r, dx, dy, 'lava', tail=2)
    flame(cv, 24, 25, 12, 10, 'lava')


@icon('cinderspire', 'kindling')
def kindling(cv):
    for k in range(5):
        a = math.radians(-90 + k * 72)
        x, y = 24 + 17 * math.cos(a), 26 + 17 * math.sin(a)
        flame(cv, x, y + 4, 9, 6.4, 'fire')
    flame(cv, 24, 38, 26, 18, 'fire')


@icon('cinderspire', 'meteor_brand')
def meteor_brand(cv):
    cv.ellipse(26, 40, 18, 5.6, ('lava', 1))
    cv.ellipse(26, 40, 14, 4, ('fire', 2))
    cv.ellipse(26, 40, 9, 2.4, ('fire', 3))
    for k in range(6):
        a = k * math.pi / 3
        cv.circle(26 + 16 * math.cos(a), 40 + 5 * math.sin(a), 0.9, ('fire', 4))
    fireball(cv, 26, 18, 7.5, 0.7, 1, 'lava', tail=2.3)
    rock(cv, 26, 18, 5.4, 'stone', 4)
    cv.circle(24.5, 16.5, 1.4, ('lava', 4))


# ============================================================================================================
# Codex of Tides: the healing tide
# ============================================================================================================

@icon('codex_of_tides', 'bubble_aegis')
def bubble_aegis(cv):
    figure(cv, 24, 38, 1.5, 'night')
    bubble(cv, 24, 24, 19)


@icon('codex_of_tides', 'ebb_and_flow')
def ebb_and_flow(cv):
    shield(cv, 24, 22, 1.9, 'water', 'water')
    heart(cv, 24, 20, 8, 'blood')
    wave(cv, 4, 44, 44, 10, 'water')


@icon('codex_of_tides', 'healing_rain')
def healing_rain(cv):
    cloud(cv, 24, 14, 36, 'cloud')
    for x, y in ((10, 30), (18, 36), (26, 30), (34, 37), (14, 44), (30, 45)):
        drop(cv, x, y, 3.4, 'water')
    heart(cv, 38, 28, 6, 'leaf')


@icon('codex_of_tides', 'tides_embrace')
def tides_embrace(cv):
    heart(cv, 26, 22, 10, 'blood')
    cv.arc_band(24, 24, 15, 21, 100, 400, ('water', 2), taper=True)
    cv.arc_band(24, 24, 17, 20, 120, 380, ('water', 3), taper=True)
    cv.arc_band(24, 24, 18.6, 20, 160, 330, ('water', 4), taper=True)
    for x, y in ((6, 8), (42, 10), (8, 42)):
        drop(cv, x, y, 2.4, 'water')


# ============================================================================================================
# Doomcleaver: the blood axe
# ============================================================================================================

@icon('doomcleaver', 'blood_rage')
def blood_rage(cv):
    for x, h, w in ((8, 20, 10), (40, 20, 10), (16, 30, 12), (32, 30, 12), (24, 38, 14)):
        flame(cv, x, 46, h, w, 'blood')
    axe_head(cv, 24, 26, math.radians(0), 1.0, 'steel', 'wood', double=True)
    crystal(cv, 24, 15, 5, 3, 0, 'crystal')


@icon('doomcleaver', 'bloodthirst')
def bloodthirst(cv):
    heart(cv, 24, 26, 18, 'blood')
    for x in (17, 31):
        cv.poly([(x - 2.6, 4), (x + 2.6, 4), (x, 17)], ('bone', 3))
        cv.poly([(x - 2.6, 4), (x, 4), (x, 17)], ('bone', 4))
    for x, y in ((17, 22), (31, 22)):
        drop(cv, x, y, 2.4, 'blood')


@icon('doomcleaver', 'crimson_apocalypse')
def crimson_apocalypse(cv):
    cv.circle(24, 13, 13.5, ('blood', 4))
    ball(cv, 24, 13, 12.5, 'blood')
    for x, y, r in ((19, 9, 2.4), (29, 16, 1.8), (22, 19, 1.4)):
        cv.circle(x, y, r, ('blood', 1))
    for (x0, y0), (x1, y1), seed in (((14, 22), (6, 44), 1), ((20, 25), (17, 46), 2), ((28, 25), (31, 46), 3), ((34, 22), (42, 44), 4)):
        vine(cv, [(x0, y0), (lerp(x0, x1, 0.4) + (x0 - 24) * 0.25, lerp(y0, y1, 0.45)), (x1, y1)], 2.2, 'blood', seed=seed)
    for x in (6, 17, 31, 42):
        cv.circle(x, 45, 2.2, ('night', 2))


@icon('doomcleaver', 'crimson_cleave')
def crimson_cleave(cv):
    ground(cv, 2, 46, 40, 6, 'earth', 'stone')
    for x, h in ((22, 8), (29, 13), (36, 19), (43, 11)):
        cv.poly([(x - 3.6, 40), (x + 1.2, 40 - h), (x + 3.6, 40)], ('blood', 2))
        cv.poly([(x - 3.6, 40), (x + 1.2, 40 - h), (x - 0.4, 40)], ('blood', 3))
    axe_head(cv, 14, 22, math.radians(-20), 1.1, 'steel', 'wood')


@icon('doomcleaver', 'sanguine_leap')
def sanguine_leap(cv):
    ground(cv, 2, 46, 40, 5, 'earth', 'stone')
    for x, h, lean in ((7, 12, -3), (14, 18, -1.5), (34, 18, 1.5), (41, 12, 3)):
        crystal(cv, x, 40 - h / 2, h, 3.8, math.radians(lean * 10), 'crystal')
    streaks(cv, [[(14, 2), (16, 12)], [(34, 2), (32, 12)]], 2.2, ('blood', 3))
    axe_head(cv, 24, 20, math.radians(180), 1.05, 'steel', 'wood')


# ============================================================================================================
# Eclipse Reaper: sun and moon
# ============================================================================================================

@icon('eclipse_reaper', 'eclipse_disc')
def eclipse_disc(cv):
    cv.circle(24, 24, 20, ('void', 2))
    cv.arc_band(24, 24, 13, 20, 0, 180, ('light', 3), taper=False)
    cv.circle(24, 24, 13, ('night', 2))
    ball(cv, 24, 24, 8, 'light')
    crescent_moon(cv, 24, 24, 8, 'void', ang=0.4, cut=0.5)
    for k in range(4):
        a = math.radians(k * 90 + 30)
        cv.poly(rot([(0, -20), (6, -15), (0, -14)], a, 24, 24), ('steel', 4))


@icon('eclipse_reaper', 'equilibrium')
def equilibrium(cv):
    cv.circle(24, 24, 20, ('void', 2))
    cv.poly([(24, 4)] + [(24 + 20 * math.cos(math.radians(a)), 24 + 20 * math.sin(math.radians(a))) for a in range(-90, 91, 6)] + [(24, 44)], ('light', 3))
    cv.circle(24, 14, 10, ('light', 3))
    cv.circle(24, 34, 10, ('void', 2))
    sun(cv, 24, 14, 4, 8, 'light', ray_len=2.4)
    crescent_moon(cv, 24, 34, 4.5, 'holy', ang=0.3, cut=0.55)


@icon('eclipse_reaper', 'solar_flare')
def solar_flare(cv):
    sun(cv, 24, 24, 11, 16, 'light', ray_len=11)
    ball(cv, 24, 24, 8.5, 'holy')
    cv.ring(24, 24, 15, 1.2, ('light', 4))


@icon('eclipse_reaper', 'total_eclipse')
def total_eclipse(cv):
    sun(cv, 24, 18, 13, 20, 'light', ray_len=7, core=False)
    cv.circle(24, 18, 13, ('light', 4))
    cv.circle(24, 18, 11.8, ('night', 1))
    for x, y, a, m in ((10, 38, 0.6, 'holy'), (24, 42, 1.57, 'void'), (38, 38, 2.5, 'holy')):
        crescent_moon(cv, x, y, 5, m, ang=a, cut=0.5)


@icon('eclipse_reaper', 'umbral_vortex')
def umbral_vortex(cv):
    cv.circle(24, 24, 20, ('shadow', 1))
    for k in range(3):
        spiral(cv, 24, 24, 20, 1.2, 4.2, ('void', 2 + k), ang0=k * 2 * math.pi / 3)
    cv.circle(24, 24, 4, 'ink')
    cv.ring(24, 24, 4.6, 0.8, ('void', 4))


# ============================================================================================================
# Frostbound Grimoire: the winter book
# ============================================================================================================

@icon('frostbound_grimoire', 'absolute_winter')
def absolute_winter(cv):
    cloud(cv, 24, 12, 40, 'cloud')
    for x, y, r in ((10, 28, 4), (24, 32, 5), (38, 27, 4), (16, 42, 3.4), (33, 42, 3.4)):
        snowflake(cv, x, y, r)
    streaks(cv, [[(6, 22), (2, 34)], [(44, 22), (40, 36)], [(28, 22), (24, 30)]], 1.6, ('ice', 4))


@icon('frostbound_grimoire', 'frozen_tomb')
def frozen_tomb(cv):
    pts = [(12, 44), (12, 14), (17, 6), (31, 6), (36, 14), (36, 44)]
    cv.poly(pts, ('ice', 2))
    figure(cv, 24, 40, 1.6, 'night')
    cv.poly(pts, ('ice', 3), 120)
    cv.poly([(12, 44), (12, 14), (17, 6), (21, 6), (17, 14), (17, 44)], ('ice', 4), 140)
    crack(cv, [(30, 8), (27, 18), (31, 24)], 0.7, 'ice')
    ground(cv, 6, 42, 44, 3, 'stone', 'ice')


@icon('frostbound_grimoire', 'rime_armor')
def rime_armor(cv):
    body = [(9, 9), (17, 6), (20, 10), (28, 10), (31, 6), (39, 9), (42, 20), (36, 22), (35, 40), (24, 43), (13, 40), (12, 22), (6, 20)]
    cv.poly(body, ('ice', 2))
    cv.poly([(9, 9), (17, 6), (20, 10), (24, 10), (24, 43), (13, 40), (12, 22), (6, 20)], ('ice', 3))
    for y in (18, 26, 34):
        cv.poly([(13, y), (24, y + 3), (35, y), (35, y + 2.4), (24, y + 5.4), (13, y + 2.4)], ('ice', 4))
    snowflake(cv, 24, 14, 3.6)


@icon('frostbound_grimoire', 'shatterguard')
def shatterguard(cv):
    cv.ering(24, 28, 20, 8, 1.2, ('ice', 3))
    figure(cv, 24, 38, 1.5, 'night')
    for a in (195, 320, 75):
        r = math.radians(a)
        crystal(cv, 24 + 19 * math.cos(r), 27 + 8 * math.sin(r) - 4, 14, 6, math.radians(a + 90), 'ice')


# ============================================================================================================
# Gloamreach: the staff of rot
# ============================================================================================================

@icon('gloamreach', 'decay')
def decay(cv):
    heart(cv, 24, 22, 18, 'poison')
    crack(cv, [(24, 10), (21, 17), (26, 22), (22, 30), (24, 36)], 1.4, 'void')
    for x, y in ((14, 36), (34, 34), (24, 42)):
        drop(cv, x, y, 2.8, 'void')


@icon('gloamreach', 'dirge_of_ruin')
def dirge_of_ruin(cv):
    for r in (16, 20):
        cv.arc_band(24, 22, r - 1.4, r, 200, 340, ('void', 3))
    bell(cv, 24, 22, 1.5, 'shadow')
    skull(cv, 24, 22, 0.5, 'bone', eyes='void')


@icon('gloamreach', 'miasma')
def miasma(cv):
    cloud(cv, 24, 26, 42, 'void')
    for x, y, r in ((14, 30, 2.6), (32, 33, 2.2), (24, 38, 1.8)):
        ball(cv, x, y, r, 'poison')
    skull(cv, 24, 22, 0.8, 'poison', eyes='void')


@icon('gloamreach', 'withering_curse')
def withering_curse(cv):
    for k in range(3):
        cv.ring(24, 24, 20 - k * 4.5, 1.0, ('void', 2 + k))
    eye(cv, 24, 24, 11, 'void', 'poison')
    for x, y in ((10, 38), (38, 38)):
        drop(cv, x, y, 3, 'void')


# ============================================================================================================
# Gravebite: the skull of souls
# ============================================================================================================

@icon('gravebite', 'deaths_maw')
def deaths_maw(cv):
    # a spectral skull, its jaw flung open over its prey
    cv.ellipse(22, 16, 17, 13, ('soul', 2))
    cv.ellipse(18, 12, 10, 7, ('soul', 3))
    cv.ellipse(14, 9, 3, 2, ('soul', 4))
    for ex in (14, 28):
        cv.ellipse(ex, 17, 3.6, 3.2, 'ink')
        cv.circle(ex, 17.5, 1.4, ('soul', 4))
    cv.poly([(8, 24), (40, 22), (36, 28), (10, 29)], ('soul', 2))
    for k in range(6):
        x = 11 + k * 4.8
        cv.poly([(x - 1.8, 27), (x + 1.8, 27), (x, 32)], ('bone', 4))
    cv.poly([(10, 36), (38, 30), (40, 38), (14, 45)], ('soul', 1))
    for k in range(5):
        x = 14 + k * 5
        y = 36 - k * 1.2
        cv.poly([(x - 1.8, y), (x + 1.8, y - 0.4), (x + 0.2, y - 5)], ('bone', 3))
    figure(cv, 41, 47, 0.75, 'night')


@icon('gravebite', 'grave_chains')
def grave_chains(cv):
    figure(cv, 24, 40, 1.6, 'night')
    ground(cv, 4, 44, 41, 5, 'earth', 'stone')
    chain(cv, [(8, 42), (12, 30), (22, 24), (34, 22), (38, 14)], 1.7, 'soul')
    chain(cv, [(40, 42), (36, 32), (26, 30), (14, 20)], 1.7, 'chain')


@icon('gravebite', 'legion_of_the_damned')
def legion_of_the_damned(cv):
    for k in range(6):
        a = k * math.pi / 3 + 0.3
        soul(cv, 24 + 17 * math.cos(a), 22 + 15 * math.sin(a), 0.55, lean=-6 * math.cos(a))
    skull(cv, 24, 24, 1.0, 'bone', eyes='soul')


@icon('gravebite', 'soul_harvest')
def soul_harvest(cv):
    skull(cv, 24, 32, 1.05, 'bone', eyes='soul')
    soul(cv, 24, 8, 0.8, lean=0)
    heart(cv, 38, 12, 6, 'leaf')


@icon('gravebite', 'soul_volley')
def soul_volley(cv):
    for x, y, lean in ((34, 8, -8), (42, 20, -10), (36, 33, -9)):
        soul(cv, x, y, 0.6, lean=lean)
    skull(cv, 14, 30, 1.0, 'bone', eyes='soul')


# ============================================================================================================
# The Great Sword of Obliteration
# ============================================================================================================

@icon('great_sword_of_obliteration', 'arch_dragon_of_hell')
def arch_dragon_of_hell(cv):
    pts = [(6, 44), (10, 34), (20, 30), (28, 34), (34, 30), (36, 20)]
    cv.stroke(pts, [3, 5, 6.5, 6.5, 6, 5.5], ('fire', 1))
    cv.stroke(pts, [1.6, 3, 4, 4, 3.6, 3.2], ('fire', 2))
    for x, y in pts[1:-1]:
        cv.poly([(x - 1.6, y - 2.8), (x + 1.6, y - 2.8), (x, y - 6)], ('bone', 3))
    dragon_head(cv, 34, 14, 1.15, math.radians(-30), 'fire', 'bone')
    flame(cv, 46, 8, 8, 6, 'fire', lean=0.5)


@icon('great_sword_of_obliteration', 'fourth_dimension_ascension')
def fourth_dimension_ascension(cv):
    cube(cv, 24, 22, 17, 'rune', (1, 2, 3))
    cube(cv, 24, 22, 8.5, 'rainbow', (0, 2, 4))
    for x, y, r in ((7, 40, 4), (41, 38, 5), (14, 6, 3), (39, 7, 3.4)):
        cube(cv, x, y, r, 'earth', (1, 2, 3))
    streaks(cv, [[(24, 47), (24, 41)], [(18, 46), (19, 41)], [(30, 46), (29, 41)]], 1.6, ('rune', 4))


@icon('great_sword_of_obliteration', 'ice_rain')
def ice_rain(cv):
    cloud(cv, 24, 10, 44, 'cloud')
    for x, y, h in ((9, 32, 14), (21, 40, 18), (34, 36, 16), (43, 44, 10)):
        cv.poly([(x - 3, y - h), (x + 3, y - h), (x, y)], ('ice', 2))
        cv.poly([(x - 3, y - h), (x, y - h), (x, y)], ('ice', 3))
        cv.line([(x - 1.2, y - h + 1), (x - 0.2, y - 3)], 0.5, ('ice', 4))


@icon('great_sword_of_obliteration', 'the_grand_earthquake')
def the_grand_earthquake(cv):
    ground(cv, 0, 48, 30, 14, 'earth', 'leaf')
    for pts in (((24, 31), (16, 34), (10, 40), (2, 43)), ((24, 31), (32, 35), (39, 39), (46, 44)), ((24, 31), (22, 38), (25, 44)),
                ((24, 31), (12, 31.5)), ((24, 31), (37, 31.6))):
        crack(cv, list(pts), 1.6, 'lava')
    blade(cv, 24, 14, math.radians(180), 18, 5, 'steel', 'blood', 'night', 'blood')


@icon('great_sword_of_obliteration', 'the_great_slash')
def the_great_slash(cv):
    for w, tone in ((7, 2), (4.6, 3), (2.2, 4)):
        cv.poly([(2, 40 + w * 0.2), (46, 6 - w * 0.6), (46, 6 + w * 0.6), (2, 40 + w * 1.1)], ('holy', tone))
    blade(cv, 12, 38, math.radians(55), 20, 4.6, 'steel', 'blood', 'night', 'blood')


@icon('great_sword_of_obliteration', 'thornspire_cataclysm')
def thornspire_cataclysm(cv):
    ground(cv, 0, 48, 40, 7, 'earth', 'leaf')
    for k, (x, h, w) in enumerate(((5, 12, 4), (12, 20, 5), (21, 30, 7), (31, 36, 8), (41, 24, 6))):
        shard(cv, x, 40, h, w, (-1) ** k * 1.2, 'leaf')
        for f in (0.35, 0.6):
            y = 40 - h * f
            side = 1 if k % 2 else -1
            cv.poly([(x + side * w * 0.2, y), (x + side * (w * 0.5 + 2), y - 2.4), (x + side * w * 0.15, y - 1.4)], ('earth', 3))


# ============================================================================================================
# Infernochain: the burning chain-sword
# ============================================================================================================

@icon('infernochain', 'cinder_cyclone')
def cinder_cyclone(cv):
    # a funnel of flame, wide aloft and narrow at the ground, wound round with the burning chain
    for k in range(7):
        t = k / 6
        y = 44 - 38 * t
        rw = 4 + 18 * t
        off = math.sin(t * 6) * 2.4
        cv.arc_band(24 + off, y, rw * 0.55, rw, 200, 520, ('fire', 1 + (k % 3)), steps=40)
    for k in range(5):
        t = (k + 0.5) / 6
        cv.arc_band(24 + math.sin(t * 6) * 2.4, 44 - 38 * t, (4 + 18 * t) * 0.7, 4 + 18 * t, 210, 300, ('fire', 4))
    chain(cv, [(4, 30), (14, 36), (30, 36), (44, 26)], 1.5, 'chain')


@icon('infernochain', 'drakes_wrath')
def drakes_wrath(cv):
    chain(cv, [(4, 46), (8, 36), (16, 30), (24, 30)], 1.8, 'chain')
    flame(cv, 12, 46, 14, 10, 'fire')
    dragon_head(cv, 32, 22, 1.05, math.radians(-25), 'fire', 'bone')
    flame(cv, 46, 12, 12, 8, 'fire', lean=0.4)


@icon('infernochain', 'hellhook')
def hellhook(cv):
    chain(cv, [(4, 44), (12, 34), (20, 26), (26, 20)], 1.6, 'chain')
    hook(cv, 32, 18, 1.15, 'steel')
    flame(cv, 37, 14, 12, 8, 'fire')
    figure(cv, 42, 46, 0.9, 'night')


@icon('infernochain', 'inferno_lash')
def inferno_lash(cv):
    slash(cv, 26, 30, 18, 190, 345, 6, 'fire')
    chain(cv, [(8, 30), (14, 18), (26, 12), (38, 16), (44, 26)], 1.4, 'chain')
    blade(cv, 9, 40, math.radians(30), 12, 3.6, 'fire', 'gold', 'night', 'blood')


@icon('infernochain', 'overheat')
def overheat(cv):
    flame(cv, 24, 46, 40, 30, 'fire')
    blade(cv, 24, 36, 0, 26, 6, 'lava', 'gold', 'night', 'blood')
    for y in (10, 15, 20):
        cv.poly([(38, y + 3), (42, y), (46, y + 3), (46, y + 5), (42, y + 2), (38, y + 5)], ('fire', 4))


# ============================================================================================================
# Lumen Psalter: the book of light
# ============================================================================================================

@icon('lumen_psalter', 'ascension')
def ascension(cv):
    for x, w in ((24, 10), (24, 6)):
        cv.poly([(x - w, 0), (x + w, 0), (x + w * 0.6, 46), (x - w * 0.6, 46)], ('holy', 2 if w > 7 else 3), 200)
    wing(cv, 22, 22, 0.85, -1, 'holy')
    wing(cv, 26, 22, 0.85, 1, 'holy')
    figure(cv, 24, 36, 1.3, 'gold')
    halo(cv, 24, 13, 5, 1.6, 1.2, 'light')
    streaks(cv, [[(18, 46), (18, 40)], [(30, 46), (30, 40)]], 1.6, ('holy', 4))


@icon('lumen_psalter', 'chorus')
def chorus(cv):
    for x, s in ((10, 1.0), (24, 1.25), (38, 1.0)):
        figure(cv, x, 46, s, 'light')
    note(cv, 14, 18, 1.0, 'gold')
    note(cv, 30, 12, 1.2, 'gold')


@icon('lumen_psalter', 'halo_of_fortitude')
def halo_of_fortitude(cv):
    shield(cv, 24, 28, 1.6, 'holy', 'gold')
    halo(cv, 24, 7, 13, 3.6, 2.2, 'light')
    sun(cv, 24, 26, 4, 8, 'light', ray_len=2.4)


@icon('lumen_psalter', 'hymn_of_swiftness')
def hymn_of_swiftness(cv):
    note(cv, 22, 34, 1.6, 'gold')
    wing(cv, 30, 16, 0.75, 1, 'holy')
    streaks(cv, [[(2, 20), (14, 22)], [(2, 30), (14, 30)], [(4, 40), (16, 38)]], 2.2, ('holy', 3))


# ============================================================================================================
# Monolith: the mountain's blade
# ============================================================================================================

@icon('monolith', 'earthshatter')
def earthshatter(cv):
    ground(cv, 0, 48, 32, 14, 'earth', 'stone')
    for pts in (((24, 33), (14, 37), (6, 44)), ((24, 33), (34, 37), (42, 44)), ((24, 33), (23, 46))):
        crack(cv, list(pts), 1.4, None)
    for x, y, r in ((7, 22, 3.4), (41, 20, 3.8), (13, 13, 2.4), (35, 11, 2.6)):
        rock(cv, x, y, r, 'stone', int(x))
    blade(cv, 24, 16, math.radians(180), 18, 8, 'stone', 'earth', 'wood', 'stone', tip=6)


@icon('monolith', 'mountains_weight')
def mountains_weight(cv):
    cv.poly([(4, 30), (18, 6), (26, 16), (32, 10), (44, 30)], ('stone', 2))
    cv.poly([(4, 30), (18, 6), (20, 30)], ('stone', 3))
    cv.poly([(15, 11), (18, 6), (21, 11), (18, 10)], ('bone', 4))
    cv.poly([(29, 14), (32, 10), (35, 14), (32, 13)], ('bone', 4))
    for r in (10, 16, 22):
        cv.arc_band(24, 44, r - 1.4, r, 190, 350, ('earth', 3), taper=True)
    figure(cv, 24, 46, 0.9, 'night')


@icon('monolith', 'seismic_fissure')
def seismic_fissure(cv):
    ground(cv, 0, 48, 34, 12, 'earth', 'stone')
    crack(cv, [(2, 44), (12, 40), (22, 38), (32, 35), (44, 33)], 1.8, 'lava')
    for x, h, w in ((12, 10, 5), (24, 16, 6), (36, 22, 7)):
        pillar(cv, x, 38 - (x - 12) * 0.15, h, w, 'stone')


@icon('monolith', 'tectonic_slam')
def tectonic_slam(cv):
    ground(cv, 0, 48, 38, 8, 'earth', 'stone')
    for x, h, w in ((8, 12, 6), (40, 12, 6), (16, 18, 6), (32, 18, 6)):
        pillar(cv, x, 38, h, w, 'stone')
    blade(cv, 24, 12, math.radians(180), 22, 9, 'stone', 'earth', 'wood', 'stone', tip=7)
    streaks(cv, [[(18, 0), (18, 8)], [(30, 0), (30, 8)]], 1.6, ('stone', 4))


@icon('monolith', 'worldbreaker')
def worldbreaker(cv):
    cv.circle(24, 26, 20, ('earth', 1))
    cv.circle(22, 24, 17.5, ('earth', 2))
    cv.circle(18, 20, 9, ('earth', 3))
    for pts in (((24, 26), (12, 14), (8, 8)), ((24, 26), (38, 18), (44, 12)), ((24, 26), (20, 38), (14, 46)), ((24, 26), (34, 38), (38, 46))):
        crack(cv, list(pts), 2, 'lava')
    flame(cv, 24, 30, 14, 10, 'lava')
    blade(cv, 24, 10, math.radians(180), 14, 6, 'stone', 'earth', 'wood', 'stone', tip=5)


# ============================================================================================================
# Pyre Codex: the book of fire
# ============================================================================================================

@icon('pyre_codex', 'phoenix')
def phoenix(cv):
    bird(cv, 24, 18, 1.0, 'fire')


@icon('pyre_codex', 'searing_chain')
def searing_chain(cv):
    pts = [(4, 40), (16, 18), (30, 34), (44, 12)]
    cv.line(pts, 3.6, ('fire', 1))
    cv.line(pts, 2.0, ('fire', 3))
    cv.line(pts, 0.8, ('fire', 4))
    for x, y in pts[1:]:
        flame(cv, x, y + 6, 12, 8, 'fire')


@icon('pyre_codex', 'wall_of_pyres')
def wall_of_pyres(cv):
    ground(cv, 0, 48, 38, 7, 'stone', None)
    for x, h, w in ((5, 18, 10), (14, 26, 11), (24, 32, 12), (34, 26, 11), (43, 18, 10)):
        flame(cv, x, 39, h, w, 'fire')


@icon('pyre_codex', 'wildfire')
def wildfire(cv):
    skull(cv, 24, 30, 0.95, 'bone', eyes='fire')
    for x, h, w, lean in ((8, 22, 9, -0.25), (40, 22, 9, 0.25), (24, 26, 12, 0)):
        flame(cv, x, 28 if x != 24 else 18, h * 0.8, w, 'fire', lean=lean)


# ============================================================================================================
# Rimeheart: the frost bulwark
# ============================================================================================================

@icon('rimeheart', 'everfrost_bastion')
def everfrost_bastion(cv):
    cv.ellipse(24, 38, 22, 7, ('ice', 1))
    for x, y, h, w in ((6, 36, 14, 5), (14, 33, 20, 6), (24, 32, 26, 7), (34, 33, 20, 6), (42, 36, 14, 5)):
        shard(cv, x, y, h, w, 0)
    for x, y, h, w in ((10, 43, 12, 5), (24, 46, 14, 6), (38, 43, 12, 5)):
        shard(cv, x, y, h, w, 0)


@icon('rimeheart', 'frost_rampart')
def frost_rampart(cv):
    ground(cv, 0, 48, 40, 5, 'stone', 'ice')
    cv.poly([(4, 40), (4, 16), (10, 12), (16, 16), (22, 10), (28, 15), (34, 9), (40, 14), (44, 12), (44, 40)], ('ice', 2))
    cv.poly([(4, 40), (4, 16), (10, 12), (16, 16), (22, 10), (24, 40)], ('ice', 3))
    for x in (12, 24, 36):
        cv.line([(x, 20), (x + 1.4, 36)], 0.6, ('ice', 4))
    for x, y, a in ((8, 30, 0.4), (40, 26, -0.5)):
        cv.poly(rot([(-1, -5), (1, -5), (0.6, 2), (-0.6, 2)], a, x, y), ('wood', 2))


@icon('rimeheart', 'glacial_ward')
def glacial_ward(cv):
    figure(cv, 24, 38, 1.5, 'night')
    for k in range(6):
        a = math.radians(k * 60)
        crystal(cv, 24 + 18 * math.sin(a), 24 - 18 * math.cos(a), 10, 4.6, a, 'ice')
    cv.ring(24, 24, 18, 0.8, ('ice', 4))


@icon('rimeheart', 'permafrost')
def permafrost(cv):
    shield(cv, 24, 24, 2.0, 'ice', 'steel')
    snowflake(cv, 24, 22, 9)


# ============================================================================================================
# Soulreaper: the scythe of souls
# ============================================================================================================

@icon('soulreaper', 'deaths_toll')
def deaths_toll(cv):
    for r in (17, 21):
        cv.arc_band(24, 22, r - 1.4, r, 200, 340, ('soul', 3))
    bell(cv, 24, 22, 1.6, 'bone')
    skull(cv, 24, 21, 0.55, 'shadow', eyes='soul')


@icon('soulreaper', 'reapers_throw')
def reapers_throw(cv):
    for k in range(3):
        cv.arc_band(26, 22, 15, 19, k * 120 + 10, k * 120 + 90, ('soul', 2 if k % 2 else 3))
    scythe(cv, 26, 22, math.radians(40), 0.95, 'steel', 'shadow')
    streaks(cv, [[(2, 40), (11, 33)], [(4, 47), (13, 40)], [(10, 46), (17, 39)]], 2.2, ('soul', 3))


@icon('soulreaper', 'reaping_whirl')
def reaping_whirl(cv):
    cv.ering(24, 30, 22, 9, 1.6, ('soul', 2))
    cv.arc_band(24, 30, 20, 23, 200, 340, ('soul', 4))
    figure(cv, 24, 40, 1.45, 'night')
    scythe(cv, 36, 18, math.radians(-60), 0.8, 'steel', 'shadow')


@icon('soulreaper', 'soul_rend')
def soul_rend(cv):
    figure(cv, 18, 44, 1.5, 'night')
    soul(cv, 30, 14, 0.9, lean=-8)
    slash(cv, 24, 26, 20, 200, 320, 6, 'soul')


@icon('soulreaper', 'soul_siphon')
def soul_siphon(cv):
    heart(cv, 34, 30, 12, 'blood')
    for k in range(3):
        soul(cv, 10 + k * 7, 8 + k * 7, 0.42, lean=6, face=False)
    scythe(cv, 14, 30, math.radians(-15), 0.6, 'steel', 'shadow')


# ============================================================================================================
# The Spell Book of Absolute Domination
# ============================================================================================================

@icon('spell_book_of_absolute_domination', 'beam_of_grant_execution')
def beam_of_grant_execution(cv):
    cv.ellipse(24, 8, 20, 6, ('rune', 2))
    cv.ellipse(24, 8, 16, 4.4, ('night', 2))
    cv.ering(24, 8, 12, 3.2, 0.8, ('rune', 4))
    for w, tone in ((7, 2), (4.6, 3), (2.2, 4)):
        cv.poly([(24 - w, 9), (24 + w, 9), (24 + w * 0.8, 40), (24 - w * 0.8, 40)], ('holy', tone))
    ground(cv, 4, 44, 40, 6, 'earth', 'stone')
    cv.ellipse(24, 40, 10, 2.4, ('holy', 4))


@icon('spell_book_of_absolute_domination', 'earthsplitting_eruption')
def earthsplitting_eruption(cv):
    ground(cv, 0, 48, 34, 12, 'stone', 'earth')
    for pts in (((24, 35), (12, 38), (4, 44)), ((24, 35), (36, 38), (44, 44)), ((24, 35), (22, 46))):
        crack(cv, list(pts), 2.0, 'lava')
    flame(cv, 24, 36, 34, 18, 'lava')
    for x, y, r in ((8, 12, 3), (40, 10, 3.4), (14, 4, 2.2)):
        rock(cv, x, y, r, 'stone', int(x))


@icon('spell_book_of_absolute_domination', 'roots_of_the_great_tree')
def roots_of_the_great_tree(cv):
    ground(cv, 0, 48, 38, 9, 'earth', 'leaf')
    for pts, seed in ((((8, 40), (4, 28), (10, 18), (16, 12)), 1), (((24, 40), (28, 28), (22, 18), (26, 6)), 2), (((40, 40), (44, 28), (38, 18), (32, 12)), 3)):
        vine(cv, list(pts), 9, 'wood', seed=seed)
    figure(cv, 24, 24, 0.75, 'night')


@icon('spell_book_of_absolute_domination', 'tectonic_breakage')
def tectonic_breakage(cv):
    # the land torn in two: two plates tipping apart over a canyon that runs down to fire
    cv.poly([(2, 16), (21, 12), (19, 22), (22, 32), (18, 44), (2, 44)], ('earth', 2))
    cv.poly([(2, 16), (21, 12), (20.4, 15.5), (2, 19.6)], ('leaf', 2))
    cv.poly([(2, 19.6), (20.4, 15.5), (19, 22), (2, 24)], ('earth', 3))
    cv.poly([(27, 14), (46, 18), (46, 44), (30, 44), (26, 32), (29, 22)], ('earth', 1))
    cv.poly([(27, 14), (46, 18), (46, 21.6), (27.6, 17.6)], ('leaf', 2))
    cv.poly([(21, 12), (27, 14), (29, 22), (26, 32), (30, 44), (18, 44), (22, 32), (19, 22)], ('night', 1))
    cv.poly([(21, 36), (27, 36), (29, 44), (19, 44)], ('lava', 2))
    cv.poly([(22, 39), (26, 39), (27, 44), (21, 44)], ('lava', 3))
    for x, y, r in ((24, 20, 1.8), (23, 28, 1.4)):
        rock(cv, x, y, r, 'stone', int(y))
    for x0, d in ((10, -1), (38, 1)):
        cv.poly([(x0, 4), (x0 + d * 6, 8), (x0, 12)], ('stone', 4))


@icon('spell_book_of_absolute_domination', 'the_ice_age')
def the_ice_age(cv):
    wave(cv, 2, 46, 42, 30, 'water')
    cv.poly([(2, 42), (2, 28), (8, 20), (14, 16), (24, 14), (24, 42)], ('ice', 2), 255)
    cv.poly([(2, 42), (2, 28), (8, 20), (14, 16), (16, 42)], ('ice', 3))
    for x, y in ((8, 26), (16, 22)):
        shard(cv, x, y + 6, 8, 3)
    snowflake(cv, 38, 10, 5)


@icon('spell_book_of_absolute_domination', 'ultramassive_blackhole')
def ultramassive_blackhole(cv):
    blackhole(cv, 24, 24, 9, 'fire')
    for x, y, r in ((6, 40, 3), (42, 38, 2.6), (10, 8, 2.2), (40, 10, 2.4)):
        cube(cv, x, y, r, 'earth')


# ============================================================================================================
# Starforge: the star hammer
# ============================================================================================================

@icon('starforge', 'event_horizon')
def event_horizon(cv):
    blackhole(cv, 24, 24, 8.5, 'cosmic')
    for k in range(6):
        a = k * math.pi / 3
        cv.star(24 + 21 * math.cos(a), 24 + 15 * math.sin(a), 2.2, 0.8, 4, ('light', 4))


@icon('starforge', 'gravity_slam')
def gravity_slam(cv):
    for r in (12, 17, 22):
        cv.ering(24, 40, r, r * 0.3, 1.2, ('cosmic', 3))
    for x, d in ((4, 1), (44, -1)):
        cv.poly([(x, 36), (x + d * 7, 40), (x, 44)], ('cosmic', 4))
    hammer(cv, 24, 18, 0, 1.0, 'cosmic', 'gold', 'wood')


@icon('starforge', 'gravity_well')
def gravity_well(cv):
    cv.circle(24, 24, 7, ('cosmic', 1))
    ball(cv, 24, 24, 6, 'cosmic')
    cv.ering(24, 24, 18, 7, 1.2, ('cosmic', 3))
    cv.ering(24, 24, 12, 18, 1.0, ('cosmic', 2))
    for x, y, r, seed in ((8, 20, 3.4, 1), (40, 28, 3.2, 2), (28, 8, 2.6, 3), (20, 40, 2.8, 4)):
        rock(cv, x, y, r, 'stone', seed)


@icon('starforge', 'meteor_strike')
def meteor_strike(cv):
    cv.ellipse(30, 42, 16, 4.6, ('earth', 1))
    cv.ellipse(30, 41.4, 11, 2.8, ('lava', 2))
    fireball(cv, 22, 20, 8.5, 0.8, 1.2, 'fire', tail=2.4)
    rock(cv, 22, 20, 6.2, 'cosmic', 7)
    cv.star(19, 18, 2, 0.8, 4, ('light', 4))


@icon('starforge', 'starfall')
def starfall(cv):
    for x, y, r in ((12, 12, 4), (34, 8, 3.4), (40, 24, 4.2), (20, 30, 5.6)):
        fireball(cv, x, y, r, 0.7, 1, 'fire', tail=2.6)
        rock(cv, x, y, r * 0.72, 'cosmic', int(x))
    ground(cv, 2, 46, 44, 4, 'earth', 'stone')


# ============================================================================================================
# Stormbreaker: the storm axe
# ============================================================================================================

@icon('stormbreaker', 'chain_lightning')
def chain_lightning(cv):
    pts = [(6, 10), (20, 28), (30, 12), (42, 34)]
    for (x0, y0), (x1, y1), seed in zip(pts, pts[1:], (1, 2, 3)):
        bolt(cv, zigzag(x0, y0, x1, y1, 4, 2.4, seed), 1.4)
    for x, y in pts[1:]:
        figure(cv, x, y + 9, 0.7, 'night')
    ball(cv, 6, 10, 3.2, 'storm')


@icon('stormbreaker', 'static_charge')
def static_charge(cv):
    axe_head(cv, 26, 24, math.radians(15), 0.95, 'storm', 'wood')
    for x0, y0, x1, y1, seed in ((4, 8, 12, 16, 1), (40, 40, 46, 30, 2), (6, 38, 14, 32, 3)):
        bolt(cv, zigzag(x0, y0, x1, y1, 3, 1.6, seed), 1.0)


@icon('stormbreaker', 'tempest_spin')
def tempest_spin(cv):
    for k in range(3):
        cv.arc_band(24, 24, 15, 21, k * 120, k * 120 + 90, ('storm', 2 + k % 2))
    axe_head(cv, 24, 24, math.radians(30), 0.75, 'steel', 'wood', double=True)
    for x0, y0, x1, y1, seed in ((2, 20, 8, 34, 4), (40, 6, 46, 18, 5)):
        bolt(cv, zigzag(x0, y0, x1, y1, 3, 1.4, seed), 1.0)


@icon('stormbreaker', 'thunderstrike')
def thunderstrike(cv):
    cloud(cv, 24, 8, 32, 'cloud')
    bolt(cv, zigzag(24, 14, 24, 40, 5, 3, 9), 2.2)
    cv.ellipse(24, 41, 12, 3.6, ('storm', 3))
    for k in range(6):
        a = math.radians(180 + k * 36)
        cv.line([(24 + 6 * math.cos(a), 41 + 2 * math.sin(a)), (24 + 14 * math.cos(a), 41 + 5 * math.sin(a) - 4)], 1.0, ('storm', 4))


@icon('stormbreaker', 'wrath_of_the_storm')
def wrath_of_the_storm(cv):
    cloud(cv, 24, 12, 46, 'cloud')
    for x0, x1, seed in ((10, 6, 1), (24, 26, 2), (38, 42, 3)):
        bolt(cv, zigzag(x0, 18, x1, 44, 4, 2.2, seed), 1.6 if x0 == 24 else 1.1)


# ============================================================================================================
# Sunwarden: the dawn staff
# ============================================================================================================

@icon('sunwarden', 'blessing_of_valor')
def blessing_of_valor(cv):
    sun(cv, 24, 16, 9, 12, 'light', ray_len=6, core=False)
    blade(cv, 24, 36, 0, 26, 5, 'holy', 'gold', 'blood', 'gold')


@icon('sunwarden', 'dawnbringer')
def dawnbringer(cv):
    for w, tone in ((10, 2), (7, 3), (3.4, 4)):
        cv.poly([(24 - w * 0.6, 0), (24 + w * 0.6, 0), (24 + w, 40), (24 - w, 40)], ('light', tone))
    cv.ellipse(24, 40, 16, 4, ('light', 3))
    for x in (8, 40):
        figure(cv, x, 44, 0.9, 'gold')


@icon('sunwarden', 'radiance')
def radiance(cv):
    sun(cv, 24, 14, 7, 12, 'light', ray_len=5)
    for x in (9, 24, 39):
        cv.poly([(24, 20), (x - 3, 36), (x + 3, 36)], ('light', 4), 140)
        figure(cv, x, 46, 0.9, 'gold')


@icon('sunwarden', 'sanctified_ground')
def sanctified_ground(cv):
    cv.ellipse(24, 36, 22, 8, ('light', 1))
    cv.ellipse(24, 36, 19, 6.4, ('light', 3))
    cv.ering(24, 36, 14, 4.4, 0.8, ('light', 4))
    cv.star(24, 36, 10, 4, 8, ('holy', 4))
    for x in (10, 24, 38):
        cv.poly([(x - 1.6, 34), (x + 1.6, 34), (x + 0.6, 10), (x - 0.6, 10)], ('holy', 3), 170)


# ============================================================================================================
# The Weapon of God
# ============================================================================================================

@icon('the_weapon_of_god', 'combined_power_of_the_six_gods')
def combined_power_of_the_six_gods(cv):
    rune_circle(cv, 24, 24, 13, 'holy')
    for k, m in enumerate(('fire', 'ice', 'leaf', 'void', 'light', 'water')):
        a = math.radians(-90 + k * 60)
        ball(cv, 24 + 19 * math.cos(a), 24 + 19 * math.sin(a), 4.2, m)
    ball(cv, 24, 24, 5, 'holy')


@icon('the_weapon_of_god', 'dimension_breakage')
def dimension_breakage(cv):
    cube(cv, 24, 24, 18, 'rainbow', (0, 1, 2))
    cube(cv, 24, 24, 9, 'rune', (1, 2, 4))
    for pts in (((24, 6), (20, 14), (25, 20)), ((8, 33), (16, 30), (18, 36)), ((40, 33), (32, 28), (34, 36))):
        crack(cv, list(pts), 1.2, 'holy')
    for x, y, r in ((4, 6, 3), (44, 6, 3), (4, 44, 2.6), (44, 44, 2.6)):
        cube(cv, x, y, r, 'rune')


@icon('the_weapon_of_god', 'judgement_of_the_final_dawn')
def judgement_of_the_final_dawn(cv):
    cv.ellipse(24, 7, 21, 6, ('light', 2))
    cv.ellipse(24, 7, 16, 4, ('holy', 4))
    for w, tone in ((6, 2), (3.6, 3), (1.6, 4)):
        cv.poly([(24 - w, 8), (24 + w, 8), (24 + w, 40), (24 - w, 40)], ('holy', tone))
    for a in (25, 155):
        slash(cv, 24, 40, 20, a - 18, a + 18, 4, 'holy', glint=False)
    ground(cv, 4, 44, 41, 5, 'earth', 'stone')


@icon('the_weapon_of_god', 'the_abyss_beyond_dimensions')
def the_abyss_beyond_dimensions(cv):
    blackhole(cv, 24, 24, 9, 'void')
    for x, y, r in ((6, 8, 3.4), (42, 8, 3), (6, 40, 3), (42, 40, 3.6), (24, 4, 2.4)):
        cube(cv, x, y, r, 'earth')
    eye(cv, 24, 24, 6, 'void', 'rune')


@icon('the_weapon_of_god', 'the_breaking_of_the_world')
def the_breaking_of_the_world(cv):
    cv.circle(24, 24, 21, ('leaf', 1))
    cv.circle(22, 22, 18.5, ('water', 2))
    for x, y, r in ((16, 18, 7), (30, 30, 6), (32, 14, 4)):
        cv.circle(x, y, r, ('leaf', 2))
    for pts in (((4, 20), (16, 22), (24, 30), (44, 26)), ((24, 4), (22, 16), (26, 24), (20, 44)), ((10, 38), (24, 30), (38, 40))):
        crack(cv, list(pts), 2.2, 'lava')


@icon('the_weapon_of_god', 'the_eternal_winter')
def the_eternal_winter(cv):
    cloud(cv, 24, 8, 42, 'cloud')
    for x, y, h in ((10, 30, 12), (22, 36, 16), (36, 32, 13)):
        cv.poly([(x - 2.6, y - h), (x + 2.6, y - h), (x, y)], ('ice', 2))
        cv.poly([(x - 2.6, y - h), (x, y - h), (x, y)], ('ice', 3))
    wave(cv, 2, 46, 46, 10, 'ice')
    snowflake(cv, 41, 20, 4)


@icon('the_weapon_of_god', 'the_hellfire_apocalypse')
def the_hellfire_apocalypse(cv):
    ground(cv, 0, 48, 36, 12, 'stone', None)
    for pts in (((8, 38), (18, 40), (26, 44)), ((40, 38), (32, 41), (28, 46))):
        crack(cv, list(pts), 1.6, 'lava')
    flame(cv, 24, 38, 18, 20, 'lava')
    dragon_head(cv, 22, 14, 1.0, math.radians(35), 'fire', 'bone')


@icon('the_weapon_of_god', 'trident_from_heaven')
def trident_from_heaven(cv):
    cloud(cv, 12, 8, 22, 'cloud')
    cloud(cv, 36, 7, 22, 'cloud')
    trident(cv, 24, 22, math.radians(180), 0.95, 'gold')
    for x in (16, 32):
        cv.line([(x, 2), (x, 10)], 1.0, ('holy', 4))


@icon('the_weapon_of_god', 'wrath_of_the_world_tree')
def wrath_of_the_world_tree(cv):
    tree(cv, 24, 34, 1.15, 'leaf', 'wood')
    for x, h, lean in ((6, 12, -1), (42, 12, 1), (14, 8, -0.5), (34, 8, 0.5)):
        shard(cv, x, 46, h, 4.4, lean, 'leaf')
    for pts, seed in ((((24, 34), (16, 40), (6, 46)), 1), (((24, 34), (32, 40), (42, 46)), 2)):
        vine(cv, list(pts), 4, 'wood', seed=seed)


# ============================================================================================================
# Thornwake: the grove staff
# ============================================================================================================

@icon('thornwake', 'grasping_grove')
def grasping_grove(cv):
    ground(cv, 2, 46, 40, 6, 'earth', 'leaf')
    for x, h, seed in ((8, 20, 1), (18, 28, 2), (30, 26, 3), (40, 18, 4)):
        vine(cv, [(x, 40), (x - 3, 40 - h * 0.4), (x + 3, 40 - h * 0.75), (x, 40 - h)], 3.4, 'leaf', thorns=2, leaves=0.8, seed=seed)
    figure(cv, 24, 40, 0.9, 'night')
    heart(cv, 40, 10, 5, 'leaf')


@icon('thornwake', 'lifebloom')
def lifebloom(cv):
    vine(cv, [(24, 46), (23, 38), (24, 30)], 3.2, 'leaf', seed=3)
    leaf(cv, 23, 42, math.radians(-60), 4)
    leaf(cv, 24, 38, math.radians(60), 3.6)
    flower(cv, 24, 22, 14, 7, 'petal', 'gold')
    for x, y in ((6, 10), (42, 12), (8, 32), (40, 34)):
        cv.rect(x - 0.7, y - 2.4, x + 0.7, y + 2.4, ('leaf', 4))
        cv.rect(x - 2.4, y - 0.7, x + 2.4, y + 0.7, ('leaf', 4))


@icon('thornwake', 'verdant_pulse')
def verdant_pulse(cv):
    for r, tone in ((20, 2), (15, 3), (10, 4)):
        cv.ring(24, 24, r, 1.4, ('leaf', tone))
    heart(cv, 24, 24, 9, 'leaf')


@icon('thornwake', 'worldtrees_boon')
def worldtrees_boon(cv):
    tree(cv, 24, 42, 1.3, 'leaf', 'wood')
    for x, y in ((8, 40), (40, 40), (14, 30), (34, 30)):
        drop(cv, x, y, 2.4, 'leaf')


# ============================================================================================================
# Tidecaller: the staff of the sea
# ============================================================================================================

@icon('tidecaller', 'maelstrom_sanctuary')
def maelstrom_sanctuary(cv):
    cv.ellipse(24, 26, 22, 14, ('water', 1))
    for k in range(3):
        spiral(cv, 24, 26, 20, 1.1, 3.6, ('water', 2 + k), ang0=k * 2 * math.pi / 3)
    cv.ellipse(24, 26, 4, 2.6, ('water', 0))
    cv.ering(24, 26, 22, 14, 1.0, ('water', 4))


@icon('tidecaller', 'riptide_surge')
def riptide_surge(cv):
    cv.ellipse(24, 32, 22, 9, ('water', 1))
    cv.ellipse(24, 32, 15, 5.6, ('water', 2))
    for a in range(0, 360, 45):
        r = math.radians(a)
        x, y = 24 + 18 * math.cos(r), 30 + 7 * math.sin(r)
        cv.arc_band(x, y, 2, 4.4, 180, 360, ('water', 4))
    figure(cv, 24, 34, 1.2, 'night')
    bubble(cv, 24, 26, 9)


@icon('tidecaller', 'tidal_spring')
def tidal_spring(cv):
    ground(cv, 4, 44, 40, 6, 'stone', 'leaf')
    cv.ellipse(24, 40, 12, 3, ('water', 2))
    for x, h, w in ((24, 30, 6), (17, 18, 4), (31, 18, 4)):
        cv.poly([(x - w / 2, 40), (x - w * 0.3, 40 - h), (x + w * 0.3, 40 - h), (x + w / 2, 40)], ('water', 2))
        cv.poly([(x - w / 2, 40), (x - w * 0.3, 40 - h), (x, 40 - h), (x, 40)], ('water', 3))
    for x, y in ((12, 10), (24, 6), (36, 10)):
        drop(cv, x, y, 2.8, 'water')


@icon('tidecaller', 'undertow')
def undertow(cv):
    fireball(cv, 32, 18, 7, 1, -0.7, 'water', tail=2.6)
    wave(cv, 2, 46, 44, 14, 'water')
    heart(cv, 10, 12, 5, 'leaf')


# ============================================================================================================
# Umbral Codex: the book of curses
# ============================================================================================================

@icon('umbral_codex', 'bane_seeds')
def bane_seeds(cv):
    for x, y, a in ((12, 30, -0.6), (24, 18, 0), (36, 28, 0.6)):
        streaks(cv, [[(x - 6 * math.sin(a) - 0, y + 10), (x, y + 3)]], 2, ('void', 3))
        cv.ellipse(x, y, 3.4, 4.8, ('void', 2))
        cv.ellipse(x - 1, y - 1.4, 1.4, 2, ('void', 4))
    for x, y in ((12, 44), (36, 44)):
        figure(cv, x, y + 2, 0.6, 'night')


@icon('umbral_codex', 'contagion')
def contagion(cv):
    skull(cv, 14, 34, 0.7, 'poison', eyes='void')
    skull(cv, 36, 34, 0.7, 'poison', eyes='void')
    skull(cv, 25, 12, 0.7, 'poison', eyes='void')
    for pts in (((18, 28), (22, 20)), ((32, 28), (28, 20)), ((20, 36), (30, 36))):
        cv.line(list(pts), 1.6, ('void', 3))


@icon('umbral_codex', 'hex_of_leeching')
def hex_of_leeching(cv):
    skull(cv, 12, 16, 0.75, 'bone', eyes='void')
    heart(cv, 36, 34, 9, 'blood')
    pts = [(18, 20), (22, 26), (26, 24), (30, 30)]
    cv.line(pts, 1.6, ('void', 2))
    for x, y in pts[1:3]:
        drop(cv, x, y + 3, 1.6, 'blood')


@icon('umbral_codex', 'plague_of_night')
def plague_of_night(cv):
    # a rift of night torn open and the plague boiling out of it
    pts = [(24, 2), (21, 12), (26, 20), (22, 30)]
    cv.stroke(pts, [1, 6, 7, 3], ('void', 2))
    cv.stroke(pts, [0.5, 3.6, 4.2, 1.4], 'ink')
    cloud(cv, 24, 34, 40, 'poison')
    for x, y, r in ((14, 34, 1.4), (34, 36, 1.2), (24, 40, 1.0)):
        cv.circle(x, y, r, ('void', 3))
    skull(cv, 24, 30, 0.55, 'bone', eyes='poison')


# ============================================================================================================
# Verdant Almanac: the book of growing things
# ============================================================================================================

@icon('verdant_almanac', 'mending_vines')
def mending_vines(cv):
    heart(cv, 30, 26, 14, 'blood')
    vine(cv, [(4, 44), (10, 32), (18, 30), (22, 22), (30, 14), (40, 18), (42, 30)], 2.6, 'leaf', leaves=0.8, seed=4)


@icon('verdant_almanac', 'photosynthesis')
def photosynthesis(cv):
    sun(cv, 14, 12, 7, 10, 'light', ray_len=4)
    leaf(cv, 30, 44, math.radians(30), 11)
    for k in range(3):
        cv.line([(19 + k * 3, 17 + k * 2), (25 + k * 3, 25 + k * 2)], 0.9, ('light', 4))


@icon('verdant_almanac', 'pollen_bloom')
def pollen_bloom(cv):
    flower(cv, 18, 32, 10, 6, 'light', 'earth')
    vine(cv, [(18, 46), (18, 40)], 2.6, 'leaf', seed=1)
    for x, y, r in ((30, 12, 2.4), (38, 18, 2), (34, 26, 1.6), (26, 6, 1.8), (42, 8, 1.4), (40, 30, 1.4)):
        ball(cv, x, y, r, 'gold')


@icon('verdant_almanac', 'spring_eternal')
def spring_eternal(cv):
    vine(cv, [(4, 42), (14, 34), (24, 30), (34, 22), (44, 12)], 3.4, 'wood', seed=6)
    for x, y, r in ((14, 30, 5), (26, 24, 6), (38, 15, 5)):
        flower(cv, x, y, r, 5, 'petal', 'gold')
    for x, y in ((20, 38), (32, 32)):
        leaf(cv, x, y, math.radians(140), 3.2)


# ============================================================================================================
# Voidfang: the void blade
# ============================================================================================================

@icon('voidfang', 'rift_tear')
def rift_tear(cv):
    pts = [(24, 2), (20, 12), (27, 20), (19, 30), (25, 38), (22, 46)]
    w = [1, 5, 8, 8, 5, 1]
    cv.stroke(pts, [x + 3 for x in w], ('void', 2))
    cv.stroke(pts, w, ('night', 0))
    cv.stroke(pts, [x * 0.35 for x in w], ('void', 4))
    for x, y, r in ((8, 14, 2.4), (40, 30, 2.6), (10, 36, 1.8), (38, 10, 1.6)):
        cube(cv, x, y, r, 'void')


@icon('voidfang', 'void_blink')
def void_blink(cv):
    # where you stood, a tear and your fading shape; where you are, the blade already cutting
    pts = [(10, 10), (7, 22), (11, 34)]
    cv.stroke(pts, [1, 6, 1], ('void', 2))
    cv.stroke(pts, [0.5, 3, 0.5], 'ink')
    for k, (x, y) in enumerate(((16, 30), (19, 24), (16, 20), (21, 36), (22, 28))):
        cv.poly(rot([(-1.3, -1.3), (1.3, -1.3), (1.3, 1.3), (-1.3, 1.3)], k * 0.6, x, y), ('void', 3 + k % 2))
    streaks(cv, [[(14, 26), (26, 26)], [(16, 34), (27, 33)]], 2, ('void', 3))
    figure(cv, 34, 44, 1.5, 'void')
    blade(cv, 36, 26, math.radians(50), 15, 3.4, 'void', 'steel', 'night', 'void')


@icon('voidfang', 'void_dimension')
def void_dimension(cv):
    cv.circle(24, 24, 21, ('void', 1))
    cv.circle(24, 24, 18, ('night', 1))
    for k in range(5):
        a = math.radians(-90 + k * 72)
        blade(cv, 24 + 14 * math.cos(a), 24 + 14 * math.sin(a), a + math.pi / 2 + math.pi, 9, 2.4, 'void', 'void', 'night', 'void')
    ball(cv, 24, 24, 3, 'void')


@icon('voidfang', 'void_execution')
def void_execution(cv):
    skull(cv, 24, 26, 1.1, 'bone', eyes='void')
    for w, tone in ((4.4, 2), (2.6, 3), (1.0, 4)):
        cv.poly([(4, 42 + w * 0.3), (44, 6 - w * 0.6), (44, 6 + w * 0.6), (4, 42 + w)], ('void', tone))


@icon('voidfang', 'void_mark')
def void_mark(cv):
    cv.circle(24, 24, 18, ('void', 1))
    cv.circle(24, 24, 15.5, ('night', 2))
    cv.star(24, 24, 13, 4.4, 4, ('void', 3), ang=-90)
    cv.star(24, 24, 8, 2.6, 4, ('void', 4), ang=-45)
    cv.circle(24, 24, 2.6, 'ink')


@icon('voidfang', 'void_slash')
def void_slash(cv):
    slash(cv, 22, 28, 20, 200, 340, 8, 'void')
    for x, y, r in ((8, 40, 2), (40, 36, 2.4), (14, 8, 1.6)):
        cube(cv, x, y, r, 'void')


def main(argv):
    preview = None
    write = True
    if '--preview' in argv:
        i = argv.index('--preview')
        preview = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if '--dry' in argv:
        write = False
        argv = [a for a in argv if a != '--dry']
    keys = [k for k in ICONS if not argv or k[0] in argv]
    rendered = []
    for key in keys:
        img = render(key)
        rendered.append((key, img))
        if write:
            path = os.path.join(ROOT, key[0], key[1] + '.png')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            img.save(path)
    if preview:
        cols = 10
        rows = (len(rendered) + cols - 1) // cols
        cell = 96
        sheet = Image.new('RGBA', (cols * cell, rows * cell * 2), (44, 34, 26, 255))
        d = ImageDraw.Draw(sheet)
        for i, (key, img) in enumerate(rendered):
            x, y = (i % cols) * cell, (i // cols) * cell * 2
            d.ellipse([x + 12, y + 12, x + 84, y + 84], fill=(26, 20, 16, 255), outline=(150, 118, 70, 255), width=2)
            sheet.alpha_composite(img, (x + 16, y + 16))
            small = img.resize((22, 22), Image.BILINEAR)
            d.ellipse([x + 30, y + 112, x + 66, y + 148], fill=(26, 20, 16, 255), outline=(150, 118, 70, 255), width=1)
            sheet.alpha_composite(small, (x + 37, y + 119))
            d.text((x + 4, y + 160), key[1][:15], fill=(230, 210, 170, 255))
        sheet.save(preview)
        print('preview', preview, sheet.size)
    print(len(rendered), 'icons' + (' written' if write else ''))


if __name__ == '__main__':
    main(sys.argv[1:])
