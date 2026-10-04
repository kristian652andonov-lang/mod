#!/usr/bin/env python3
"""
Ability icons for every weapon (except Voidfang, drawn in gen_textures.py). 64x64, procedurally drawn on the shared
Icon canvas: element-coloured bevelled background, glowing light glyph.

Usage: python3 tools/gen_icons.py [weapon ...]
"""
import math
import random
import sys

from PIL import Image, ImageDraw

from gen_textures import Icon, mix

ICONS = {}


def icon(weapon, name, primary, dark, light):
    def deco(fn):
        ICONS.setdefault(weapon, []).append((name, primary, dark, light, fn))
        return fn
    return deco


def star(ic, cx, cy, r_out, r_in, n, rot=0.0):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / n - math.pi / 2
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return ic.pts(pts)


def poly_circle(ic, cx, cy, r, n=40):
    return ic.pts([(cx + math.cos(i * 2 * math.pi / n) * r, cy + math.sin(i * 2 * math.pi / n) * r) for i in range(n)])


# ---------------------------------------------------------------------------------------------------------------------
# SOLARIS — gold / white / ember
# ---------------------------------------------------------------------------------------------------------------------
S = (0xFFB627, 0x2A1200, 0xFFF4C2)


@icon('solaris', 'radiant_slash', *S)
def radiant_slash(ic):
    ic.shape(lambda d, c: d.pieslice([ic.p(6, 10), ic.p(60, 64)], 195, 345, fill=c))
    ic.d.pieslice([ic.p(13, 18), ic.p(57, 66)], 190, 350, fill=mix(ic.dark, ic.primary, 0.2) + (255,))
    for i in range(6):
        x = 14 + i * 7
        ic.d.polygon(ic.pts([(x, 44), (x + 3, 36 - (i % 2) * 4), (x + 6, 44)]), fill=(255, 140, 40, 230))


@icon('solaris', 'solar_burst', *S)
def solar_burst(ic):
    def f(d, c):
        d.polygon(star(ic, 32, 34, 27, 12, 12), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(22, 24), ic.p(42, 44)], fill=(255, 255, 255, 255))
    ic.d.ellipse([ic.p(14, 50), ic.p(50, 58)], outline=ic.primary + (255,), width=2 * 4)


@icon('solaris', 'sunfire', *S)
def sunfire(ic):
    def f(d, c):
        d.polygon(ic.pts([(32, 6), (44, 24), (48, 40), (40, 56), (24, 56), (16, 40), (20, 26), (26, 34), (28, 18)]), fill=c)
    ic.shape(f)
    ic.d.polygon(ic.pts([(32, 26), (38, 38), (36, 50), (28, 50), (26, 40)]), fill=(255, 120, 30, 255))


@icon('solaris', 'supernova', *S)
def supernova(ic):
    def f(d, c):
        d.polygon(star(ic, 38, 26, 18, 6, 8, 0.2), fill=c)
        d.line([ic.p(8, 56), ic.p(30, 34)], fill=c, width=5 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(31, 19), ic.p(45, 33)], fill=(255, 255, 255, 255))


@icon('solaris', 'celestial_inferno', *S)
def celestial_inferno(ic):
    def f(d, c):
        d.ellipse([ic.p(20, 6), ic.p(44, 30)], fill=c)
        for x in (14, 26, 38, 50):
            d.polygon(ic.pts([(32, 26), (x - 3, 56), (x + 3, 56)]), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(25, 11), ic.p(39, 25)], fill=(255, 255, 255, 255))
    ic.d.rectangle([ic.p(6, 55), ic.p(58, 58)], fill=(255, 106, 0, 255))


# ---------------------------------------------------------------------------------------------------------------------
# FROSTREND — glacier blue / white
# ---------------------------------------------------------------------------------------------------------------------
F = (0x6FD8FF, 0x061A2E, 0xE8FBFF)


def crystal(ic, d, c, x, y, w, h, lean=0.0):
    d.polygon(ic.pts([(x + lean, y - h), (x + w / 2, y - h * 0.25), (x + w / 2 * 0.7, y), (x - w / 2 * 0.7, y), (x - w / 2, y - h * 0.25)]), fill=c)


@icon('frostrend', 'frost_slash', *F)
def frost_slash(ic):
    ic.shape(lambda d, c: d.pieslice([ic.p(8, 8), ic.p(58, 58)], 200, 340, fill=c))
    ic.d.pieslice([ic.p(14, 15), ic.p(56, 58)], 195, 345, fill=mix(ic.dark, ic.primary, 0.15) + (255,))
    def f(d, c):
        for x, h in ((16, 12), (26, 16), (36, 12), (46, 15)):
            crystal(ic, d, c, x, 56, 6, h)
    ic.shape(f)


@icon('frostrend', 'ice_spikes', *F)
def ice_spikes(ic):
    def f(d, c):
        crystal(ic, d, c, 14, 56, 10, 18, -2)
        crystal(ic, d, c, 32, 56, 14, 40)
        crystal(ic, d, c, 50, 56, 10, 24, 2)
    ic.shape(f)
    ic.d.line([ic.p(32, 18), ic.p(32, 52)], fill=(255, 255, 255, 200), width=4)


@icon('frostrend', 'frostbite', *F)
def frostbite(ic):
    def f(d, c):
        for k in range(6):
            a = k * math.pi / 3
            x2, y2 = 32 + math.cos(a) * 24, 32 + math.sin(a) * 24
            d.line([ic.p(32, 32), ic.p(x2, y2)], fill=c, width=4 * 4)
            for t in (0.45, 0.7):
                bx, by = 32 + math.cos(a) * 24 * t, 32 + math.sin(a) * 24 * t
                for s in (-1, 1):
                    d.line([ic.p(bx, by), ic.p(bx + math.cos(a + s * 0.8) * 7, by + math.sin(a + s * 0.8) * 7)], fill=c, width=3 * 4)
    ic.shape(f)


@icon('frostrend', 'glacial_domain', *F)
def glacial_domain(ic):
    def f(d, c):
        d.ellipse([ic.p(6, 38), ic.p(58, 58)], outline=c, width=3 * 4)
        for x, h in ((14, 12), (24, 20), (40, 18), (50, 11)):
            crystal(ic, d, c, x, 50, 7, h)
    ic.shape(f)
    ic.d.ellipse([ic.p(26, 12), ic.p(38, 24)], fill=(255, 255, 255, 230))


@icon('frostrend', 'absolute_zero', *F)
def absolute_zero(ic):
    def f(d, c):
        d.ellipse([ic.p(10, 10), ic.p(54, 54)], outline=c, width=3 * 4)
        for k in range(8):
            a = k * math.pi / 4
            crystal_pts = [(32 + math.cos(a) * 8, 32 + math.sin(a) * 8), (32 + math.cos(a + 0.18) * 20, 32 + math.sin(a + 0.18) * 20),
                           (32 + math.cos(a) * 27, 32 + math.sin(a) * 27), (32 + math.cos(a - 0.18) * 20, 32 + math.sin(a - 0.18) * 20)]
            d.polygon(ic.pts(crystal_pts), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(27, 27), ic.p(37, 37)], fill=(255, 255, 255, 255))


def build(weapons):
    for weapon in weapons:
        for name, primary, dark, light, fn in ICONS.get(weapon, []):
            ic = Icon(primary, dark, light)
            ic.background()
            fn(ic)
            ic.save(f'gui/ability/{weapon}/{name}.png')
        print(weapon, len(ICONS.get(weapon, [])), 'icons')


if __name__ == '__main__':
    build(sys.argv[1:] or list(ICONS))
