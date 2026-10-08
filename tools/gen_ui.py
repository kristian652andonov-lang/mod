#!/usr/bin/env python3
"""
Fantasy UI art for the progression menu, HUD, banners and tooltips (real colours, not tinted):

  gui/fantasy/frame.png      9-slice ornate frame: bevelled bronze band, gold inlay, gem-set filigree corners
  gui/fantasy/leather.png    tileable dark tooled leather for panel bodies
  gui/fantasy/button.png     9-slice carved bronze plaque, three states stacked (normal, hover, disabled)
  gui/fantasy/divider.png    gold flourish with a central gem
  gui/fantasy/medallion.png  round gold-rimmed socket for skill-tree nodes and HUD icons
  gui/fantasy/banner.png     3-slice crimson ribbon banner with swallowtail ends
  gui/fantasy/bar.png        9-slice bronze frame for progress bars

Usage: python3 tools/gen_ui.py
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from gen_textures import tileable_noise

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons', 'textures', 'gui', 'fantasy')
SS = 4

BRONZE_D = (58, 38, 20)
BRONZE = (128, 88, 42)
BRONZE_L = (196, 150, 78)
GOLD = (232, 192, 104)
GOLD_L = (255, 230, 160)
GEM = (168, 26, 40)
GEM_L = (255, 120, 120)


def out(name):
    os.makedirs(ROOT, exist_ok=True)
    return os.path.join(ROOT, name)


def finish(img, size):
    return img.resize(size, Image.LANCZOS)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def gem(d, cx, cy, r, color=GEM, light=GEM_L):
    d.ellipse([cx - r - SS, cy - r - SS, cx + r + SS, cy + r + SS], fill=BRONZE_D + (255,))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (255,))
    d.ellipse([cx - r * 0.75, cy - r * 0.8, cx + r * 0.35, cy + r * 0.1], fill=lerp(color, light, 0.55) + (255,))
    d.ellipse([cx - r * 0.5, cy - r * 0.6, cx - r * 0.1, cy - r * 0.2], fill=light + (255,))


def frame():
    S = 96
    s = S * SS
    b = 24 * SS
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # bevelled bronze band
    for i in range(6 * SS):
        t = i / (6 * SS)
        c = lerp(BRONZE_L, BRONZE_D, abs(t - 0.35) * 1.6)
        d.rounded_rectangle([i, i, s - 1 - i, s - 1 - i], radius=6 * SS - i // 2, outline=c + (255,), width=1)
    # dark groove and gold inlay line
    d.rectangle([6 * SS, 6 * SS, s - 1 - 6 * SS, s - 1 - 6 * SS], outline=(20, 12, 6, 255), width=SS)
    d.rectangle([7 * SS, 7 * SS, s - 1 - 7 * SS, s - 1 - 7 * SS], outline=GOLD + (255,), width=SS)
    # inner shadow fading into the panel
    for i in range(6 * SS):
        a = int(150 * (1 - i / (6 * SS)) ** 2)
        d.rectangle([8 * SS + i, 8 * SS + i, s - 1 - 8 * SS - i, s - 1 - 8 * SS - i], outline=(0, 0, 0, a), width=1)
    # gem-set filigree corners (kept inside the 24px corner cells)
    corner = Image.new('RGBA', (b, b), (0, 0, 0, 0))
    cd = ImageDraw.Draw(corner)
    c = 10 * SS
    # curling scrolls along both edges
    for sgn in (0, 1):
        pts = []
        for k in range(40):
            t = k / 39
            x = c + t * 13 * SS
            y = c - 2 * SS + math.sin(t * math.pi * 1.5) * 3 * SS
            pts.append((x, y) if sgn == 0 else (y, x))
        cd.line(pts, fill=GOLD + (255,), width=int(1.6 * SS))
        tip = pts[-1]
        cd.ellipse([tip[0] - 1.6 * SS, tip[1] - 1.6 * SS, tip[0] + 1.6 * SS, tip[1] + 1.6 * SS], fill=GOLD_L + (255,))
    # diamond plate with a gem
    r = 8 * SS
    plate = [(c, c - r), (c + r, c), (c, c + r), (c - r, c)]
    cd.polygon(plate, fill=BRONZE + (255,), outline=BRONZE_D + (255,))
    inner = [(c, c - r + 2 * SS), (c + r - 2 * SS, c), (c, c + r - 2 * SS), (c - r + 2 * SS, c)]
    cd.polygon(inner, outline=GOLD + (255,))
    gem(cd, c, c, 3.4 * SS)
    for flip in range(4):
        piece = corner
        if flip in (1, 2):
            piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
        if flip in (2, 3):
            piece = piece.transpose(Image.FLIP_TOP_BOTTOM)
        x = 0 if flip in (0, 3) else s - b
        y = 0 if flip in (0, 1) else s - b
        img.alpha_composite(piece, (x, y))
    finish(img, (S, S)).save(out('frame.png'))


def leather():
    S = 128
    n = tileable_noise(S, octaves=6, seed=711, base=4)
    fine = tileable_noise(S, octaves=2, seed=712, base=32)
    lum = 0.8 + 0.3 * (n - 0.5) + 0.12 * (fine - 0.5)
    base = np.array([40, 27, 19], dtype=float)
    arr = np.zeros((S, S, 4))
    for i in range(3):
        arr[..., i] = np.clip(base[i] * lum * (1.0 + 0.08 * (i == 0)), 0, 255)
    arr[..., 3] = 255
    Image.fromarray(arr.astype(np.uint8), 'RGBA').save(out('leather.png'))


def button():
    W, H = 64, 24
    sheet = Image.new('RGBA', (W, H * 3), (0, 0, 0, 0))
    states = [
        (BRONZE_L, BRONZE_D, GOLD, (0, 0, 0)),          # normal
        (GOLD_L, BRONZE, GOLD_L, (255, 200, 110)),      # hover
        ((112, 104, 96), (46, 42, 38), (130, 122, 110), (0, 0, 0)),  # disabled
    ]
    for k, (top, bottom, line, glow) in enumerate(states):
        s_w, s_h = W * SS, H * SS
        img = Image.new('RGBA', (s_w, s_h), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for y in range(s_h):
            t = y / (s_h - 1)
            c = lerp(top, bottom, t ** 0.8)
            d.line([(0, y), (s_w, y)], fill=c + (255,))
        mask = Image.new('L', (s_w, s_h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s_w - 1, s_h - 1], radius=5 * SS, fill=255)
        img.putalpha(mask)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([0, 0, s_w - 1, s_h - 1], radius=5 * SS, outline=(24, 14, 6, 255), width=SS)
        d.rounded_rectangle([3 * SS, 3 * SS, s_w - 1 - 3 * SS, s_h - 1 - 3 * SS], radius=3 * SS, outline=lerp(bottom, (0, 0, 0), 0.4) + (255,), width=SS)
        d.rounded_rectangle([3 * SS, 4 * SS, s_w - 1 - 3 * SS, s_h - 3 * SS], radius=3 * SS, outline=line + (200,), width=max(1, SS // 2))
        for x in (6 * SS, s_w - 6 * SS):
            d.ellipse([x - 1.6 * SS, s_h / 2 - 1.6 * SS, x + 1.6 * SS, s_h / 2 + 1.6 * SS], fill=lerp(line, (255, 255, 255), 0.2) + (255,),
                      outline=(30, 18, 8, 255))
        if k == 1:
            glow_img = Image.new('RGBA', (s_w, s_h), glow + (0,))
            ga = Image.new('L', (s_w, s_h), 0)
            ImageDraw.Draw(ga).rounded_rectangle([2 * SS, 2 * SS, s_w - 2 * SS, s_h - 2 * SS], radius=4 * SS, outline=120, width=2 * SS)
            glow_img.putalpha(ga.filter(ImageFilter.GaussianBlur(2 * SS)))
            img.alpha_composite(glow_img)
        sheet.alpha_composite(finish(img, (W, H)), (0, H * k))
    sheet.save(out('button.png'))


def divider():
    W, H = 192, 16
    s_w, s_h = W * SS, H * SS
    img = Image.new('RGBA', (s_w, s_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = s_w / 2, s_h / 2
    for side in (-1, 1):
        for i in range(int(s_w / 2 - 10 * SS)):
            x = cx + side * (10 * SS + i)
            t = i / (s_w / 2 - 10 * SS)
            a = int(255 * (1 - t) ** 1.3)
            w = max(1, int(SS * 1.4 * (1 - t * 0.7)))
            d.line([(x, cy - w / 2), (x, cy + w / 2)], fill=GOLD + (a,))
        # small curls beside the gem
        pts = [(cx + side * (10 * SS + k * SS), cy - math.sin(k / 14 * math.pi) * 3.5 * SS) for k in range(15)]
        d.line(pts, fill=GOLD_L + (230,), width=SS)
        dot = (cx + side * 30 * SS, cy)
        d.ellipse([dot[0] - 1.4 * SS, dot[1] - 1.4 * SS, dot[0] + 1.4 * SS, dot[1] + 1.4 * SS], fill=GOLD_L + (220,))
    r = 6 * SS
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=BRONZE + (255,), outline=BRONZE_D + (255,))
    gem(d, cx, cy, 2.8 * SS)
    finish(img, (W, H)).save(out('divider.png'))


def medallion():
    S = 64
    s = S * SS
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    ro, ri = 31 * SS, 24 * SS
    for i in range(int(ro - ri)):
        r = ro - i
        t = i / (ro - ri)
        col = lerp(BRONZE_L, BRONZE_D, abs(t - 0.3) * 1.5)
        d.ellipse([c - r, c - r, c + r, c + r], outline=col + (255,), width=SS)
    # bevel light from the top-left
    hl = Image.new('L', (s, s), 0)
    ImageDraw.Draw(hl).arc([c - ro + 2 * SS, c - ro + 2 * SS, c + ro - 2 * SS, c + ro - 2 * SS], 170, 280, fill=200, width=2 * SS)
    gl = Image.new('RGBA', (s, s), GOLD_L + (0,))
    gl.putalpha(hl.filter(ImageFilter.GaussianBlur(SS)))
    img.alpha_composite(gl)
    d = ImageDraw.Draw(img)
    d.ellipse([c - ri, c - ri, c + ri, c + ri], outline=GOLD + (255,), width=SS)
    d.ellipse([c - ro, c - ro, c + ro, c + ro], outline=(22, 12, 6, 255), width=SS)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, y = c + math.cos(a) * (ro + ri) / 2, c + math.sin(a) * (ro + ri) / 2
        d.ellipse([x - 1.7 * SS, y - 1.7 * SS, x + 1.7 * SS, y + 1.7 * SS], fill=GOLD_L + (255,), outline=BRONZE_D + (255,))
    # inner shadow
    for i in range(5 * SS):
        r = ri - SS - i
        d.ellipse([c - r, c - r, c + r, c + r], outline=(0, 0, 0, int(140 * (1 - i / (5 * SS)) ** 2)), width=1)
    finish(img, (S, S)).save(out('medallion.png'))


def banner():
    W, H = 256, 48
    s_w, s_h = W * SS, H * SS
    img = Image.new('RGBA', (s_w, s_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top, bot = 9 * SS, 39 * SS
    end = 40 * SS
    cloth = (122, 18, 30)
    cloth_d = (58, 8, 16)
    # swallowtail ends, folded behind the band
    for side in (0, 1):
        x0 = 0 if side == 0 else s_w - end
        pts = [(0, top + 5 * SS), (end, top + 5 * SS), (end, bot + 5 * SS), (0, bot + 5 * SS), (12 * SS, (top + bot) / 2 + 5 * SS)]
        if side == 1:
            pts = [(s_w - x, y) for x, y in pts]
        d.polygon(pts, fill=cloth_d + (255,))
    # main band with a cloth gradient
    for y in range(top, bot):
        t = (y - top) / (bot - top)
        c = lerp(lerp(cloth, (180, 40, 52), 0.35), cloth_d, abs(t - 0.3) * 1.3)
        d.line([(end - 6 * SS, y), (s_w - end + 6 * SS, y)], fill=c + (255,))
    for y in (top + 2 * SS, bot - 3 * SS):
        d.line([(end - 6 * SS, y), (s_w - end + 6 * SS, y)], fill=GOLD + (255,), width=SS)
    d.rectangle([end - 6 * SS, top, s_w - end + 6 * SS, bot], outline=(30, 4, 8, 255), width=SS)
    finish(img, (W, H)).save(out('banner.png'))


def bar():
    W, H = 64, 12
    s_w, s_h = W * SS, H * SS
    img = Image.new('RGBA', (s_w, s_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, s_w - 1, s_h - 1], radius=4 * SS, fill=BRONZE + (255,), outline=(24, 14, 6, 255), width=SS)
    d.rounded_rectangle([SS, SS, s_w - 1 - SS, s_h / 2], radius=3 * SS, fill=BRONZE_L + (255,))
    d.rounded_rectangle([2 * SS, 2 * SS, s_w - 1 - 2 * SS, s_h - 1 - 2 * SS], radius=3 * SS, fill=(14, 8, 4, 255))
    # punch the centre out so the fill shows through
    hole = Image.new('L', (s_w, s_h), 255)
    ImageDraw.Draw(hole).rounded_rectangle([3 * SS, 3 * SS, s_w - 1 - 3 * SS, s_h - 1 - 3 * SS], radius=2 * SS, fill=0)
    a = np.minimum(np.asarray(img.split()[3]), np.asarray(hole))
    img.putalpha(Image.fromarray(a))
    finish(img, (W, H)).save(out('bar.png'))


if __name__ == '__main__':
    frame()
    leather()
    button()
    divider()
    medallion()
    banner()
    bar()
    print('ui textures written to', os.path.relpath(ROOT))
