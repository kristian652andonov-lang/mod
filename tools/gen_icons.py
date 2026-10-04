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


# ---------------------------------------------------------------------------------------------------------------------
# DOOMCLEAVER — blood red
# ---------------------------------------------------------------------------------------------------------------------
D = (0xE0213A, 0x1C0006, 0xFFD6DC)


def axe(ic, d, c, cx, cy, s=1.0, rot=0.0):
    def R(x, y):
        x, y = x * s, y * s
        return (cx + x * math.cos(rot) - y * math.sin(rot), cy + x * math.sin(rot) + y * math.cos(rot))
    d.line([ic.p(*R(0, -22)), ic.p(*R(0, 24))], fill=c, width=int(5 * 4 * s))
    d.polygon(ic.pts([R(2, -20), R(20, -26), R(24, -8), R(18, 6), R(2, -2)]), fill=c)


@icon('doomcleaver', 'crimson_cleave', *D)
def crimson_cleave(ic):
    ic.shape(lambda d, c: axe(ic, d, c, 26, 30, 0.9, -0.5))
    def f(d, c):
        for x, h in ((34, 10), (42, 16), (50, 12)):
            d.polygon(ic.pts([(x - 3, 58), (x, 58 - h), (x + 3, 58)]), fill=c)
    ic.shape(f)


@icon('doomcleaver', 'blood_rage', *D)
def blood_rage(ic):
    def f(d, c):
        d.polygon(ic.pts([(32, 6), (40, 22), (52, 14), (46, 32), (58, 38), (42, 44), (44, 58), (32, 48), (20, 58), (22, 44), (6, 38),
                          (18, 32), (12, 14), (24, 22)]), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(24, 26), ic.p(40, 42)], fill=(120, 0, 16, 255))
    ic.d.polygon(ic.pts([(26, 30), (31, 33), (26, 35)]), fill=(255, 220, 220, 255))
    ic.d.polygon(ic.pts([(38, 30), (33, 33), (38, 35)]), fill=(255, 220, 220, 255))


@icon('doomcleaver', 'bloodthirst', *D)
def bloodthirst(ic):
    def f(d, c):
        d.polygon(ic.pts([(32, 8), (46, 30), (48, 42), (40, 54), (24, 54), (16, 42), (18, 30)]), fill=c)
    ic.shape(f)
    ic.d.polygon(ic.pts([(32, 22), (40, 36), (36, 48), (28, 48), (24, 36)]), fill=(150, 0, 20, 255))
    ic.d.ellipse([ic.p(26, 30), ic.p(32, 38)], fill=(255, 255, 255, 180))


@icon('doomcleaver', 'sanguine_leap', *D)
def sanguine_leap(ic):
    def f(d, c):
        d.arc([ic.p(6, 12), ic.p(58, 70)], 200, 320, fill=c, width=4 * 4)
        axe(ic, d, c, 46, 30, 0.6, 0.6)
        for k in range(5):
            a = math.pi + k * math.pi / 4
            d.line([ic.p(32 + math.cos(a) * 6, 56), ic.p(32 + math.cos(a) * 16, 56 + math.sin(a) * 10)], fill=c, width=3 * 4)
    ic.shape(f)


@icon('doomcleaver', 'crimson_apocalypse', *D)
def crimson_apocalypse(ic):
    def f(d, c):
        d.ellipse([ic.p(18, 6), ic.p(46, 34)], fill=c)
        for x in (10, 22, 42, 54):
            d.line([ic.p(32, 30), ic.p(x, 58)], fill=c, width=3 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(22, 10), ic.p(42, 30)], fill=(150, 0, 20, 255))


# ---------------------------------------------------------------------------------------------------------------------
# STORMBREAKER — electric blue
# ---------------------------------------------------------------------------------------------------------------------
B = (0x5CB8FF, 0x060C24, 0xE6F3FF)


def bolt(ic, d, c, pts, w=3):
    d.line([ic.p(x, y) for x, y in pts], fill=c, width=int(w * 4), joint='curve')


@icon('stormbreaker', 'chain_lightning', *B)
def chain_lightning(ic):
    def f(d, c):
        bolt(ic, d, c, [(6, 14), (18, 22), (14, 30), (28, 34)], 3.5)
        bolt(ic, d, c, [(28, 34), (38, 28), (36, 40), (48, 46)], 3)
        bolt(ic, d, c, [(48, 46), (52, 52), (58, 56)], 2.5)
        for x, y in ((28, 34), (48, 46)):
            d.ellipse([ic.p(x - 4, y - 4), ic.p(x + 4, y + 4)], fill=c)
    ic.shape(f)


@icon('stormbreaker', 'tempest_spin', *B)
def tempest_spin(ic):
    def f(d, c):
        for k in range(3):
            a0 = k * 120
            d.arc([ic.p(8, 8), ic.p(56, 56)], a0, a0 + 70, fill=c, width=4 * 4)
            d.arc([ic.p(18, 18), ic.p(46, 46)], a0 + 60, a0 + 120, fill=c, width=3 * 4)
        d.polygon(ic.pts([(32, 20), (38, 30), (32, 44), (26, 30)]), fill=c)
    ic.shape(f)


@icon('stormbreaker', 'static_charge', *B)
def static_charge(ic):
    def f(d, c):
        d.polygon(ic.pts([(36, 4), (16, 36), (30, 36), (24, 60), (48, 26), (34, 26), (42, 4)]), fill=c)
    ic.shape(f)
    for x, y in ((10, 16), (52, 44), (12, 50), (54, 12)):
        ic.d.ellipse([ic.p(x - 2, y - 2), ic.p(x + 2, y + 2)], fill=(255, 255, 255, 220))


@icon('stormbreaker', 'thunderstrike', *B)
def thunderstrike(ic):
    def f(d, c):
        d.ellipse([ic.p(8, 4), ic.p(56, 20)], fill=c)
        bolt(ic, d, c, [(32, 16), (26, 30), (36, 34), (28, 52)], 4)
        d.ellipse([ic.p(14, 50), ic.p(42, 58)], outline=c, width=2 * 4)
    ic.shape(f)


@icon('stormbreaker', 'wrath_of_the_storm', *B)
def wrath_of_the_storm(ic):
    def f(d, c):
        for x, y, r in ((18, 14, 10), (32, 10, 12), (46, 14, 10), (26, 20, 9), (40, 20, 9)):
            d.ellipse([ic.p(x - r, y - r), ic.p(x + r, y + r)], fill=c)
        for x in (14, 30, 48):
            bolt(ic, d, c, [(x, 26), (x - 4, 38), (x + 3, 42), (x - 2, 58)], 3)
    ic.shape(f)


# ---------------------------------------------------------------------------------------------------------------------
# GRAVEBITE — spectral green
# ---------------------------------------------------------------------------------------------------------------------
G = (0x5CFFB8, 0x0C0418, 0xC9FFE9)


def skull(ic, d, c, cx, cy, s=1.0, open_jaw=0.0):
    P = lambda x, y: ic.p(cx + x * s, cy + y * s)
    d.ellipse([P(-12, -14), P(12, 8)], fill=c)
    d.rectangle([P(-8, 4), P(8, 12 + open_jaw)], fill=c)


def soul_shape(ic, d, c, x, y, s=1.0):
    d.ellipse([ic.p(x - 6 * s, y - 6 * s), ic.p(x + 6 * s, y + 6 * s)], fill=c)
    d.polygon(ic.pts([(x - 6 * s, y), (x + 6 * s, y), (x + 2 * s, y + 14 * s), (x - 1 * s, y + 9 * s), (x - 4 * s, y + 16 * s)]), fill=c)


@icon('gravebite', 'soul_volley', *G)
def soul_volley(ic):
    def f(d, c):
        skull(ic, d, c, 18, 34, 0.9, 4)
        for x, y, s in ((40, 18, 0.7), (48, 34, 0.8), (40, 48, 0.6)):
            soul_shape(ic, d, c, x, y, s)
    ic.shape(f)
    ic.d.ellipse([ic.p(11, 26), ic.p(16, 31)], fill=(10, 4, 20, 255))
    ic.d.ellipse([ic.p(20, 26), ic.p(25, 31)], fill=(10, 4, 20, 255))


@icon('gravebite', 'grave_chains', *G)
def grave_chains(ic):
    def f(d, c):
        for x0, x1 in ((10, 26), (54, 38), (22, 30), (42, 34)):
            for k in range(4):
                t = k / 4
                x = x0 + (x1 - x0) * t
                y = 58 - 40 * t
                d.ellipse([ic.p(x - 3, y - 4), ic.p(x + 3, y + 4)], outline=c, width=2 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(26, 10), ic.p(38, 22)], fill=ic.light + (255,))


@icon('gravebite', 'soul_harvest', *G)
def soul_harvest(ic):
    def f(d, c):
        soul_shape(ic, d, c, 32, 16, 1.2)
        d.arc([ic.p(10, 26), ic.p(54, 62)], 200, 340, fill=c, width=4 * 4)
    ic.shape(f)


@icon('gravebite', 'deaths_maw', *G)
def deaths_maw(ic):
    def f(d, c):
        skull(ic, d, c, 32, 28, 1.6, 6)
    ic.shape(f)
    for x in (22, 28, 34, 40):
        ic.d.polygon(ic.pts([(x - 2, 44), (x + 2, 44), (x, 50)]), fill=(10, 4, 20, 255))
    ic.d.ellipse([ic.p(20, 20), ic.p(28, 28)], fill=(10, 4, 20, 255))
    ic.d.ellipse([ic.p(36, 20), ic.p(44, 28)], fill=(10, 4, 20, 255))


@icon('gravebite', 'legion_of_the_damned', *G)
def legion_of_the_damned(ic):
    def f(d, c):
        for k in range(8):
            a = k * math.pi / 4
            soul_shape(ic, d, c, 32 + math.cos(a) * 20, 28 + math.sin(a) * 18, 0.5)
        skull(ic, d, c, 32, 32, 0.7, 2)
    ic.shape(f)


# ---------------------------------------------------------------------------------------------------------------------
# SOULREAPER — soul blue
# ---------------------------------------------------------------------------------------------------------------------
R = (0x5A8CFF, 0x0B0620, 0xCFE0FF)


def scythe(ic, d, c, cx, cy, s=1.0, rot=0.0):
    def T(x, y):
        x, y = x * s, y * s
        return (cx + x * math.cos(rot) - y * math.sin(rot), cy + x * math.sin(rot) + y * math.cos(rot))
    d.line([ic.p(*T(0, -24)), ic.p(*T(0, 24))], fill=c, width=int(4 * 4 * s))
    blade = [T(0, -24), T(-10, -26), T(-22, -20), T(-28, -8), T(-24, -12), T(-14, -18), T(0, -18)]
    d.polygon(ic.pts(blade), fill=c)


@icon('soulreaper', 'reapers_throw', *R)
def reapers_throw(ic):
    def f(d, c):
        scythe(ic, d, c, 40, 34, 0.7, 0.9)
        d.arc([ic.p(6, 10), ic.p(58, 62)], 150, 260, fill=c, width=3 * 4)
    ic.shape(f)


@icon('soulreaper', 'soul_rend', *R)
def soul_rend(ic):
    ic.shape(lambda d, c: d.pieslice([ic.p(4, 12), ic.p(60, 68)], 180, 360, fill=c))
    ic.d.pieslice([ic.p(12, 22), ic.p(52, 68)], 180, 360, fill=mix(ic.dark, ic.primary, 0.2) + (255,))
    for x in (20, 32, 44):
        ic.d.ellipse([ic.p(x - 3, 36), ic.p(x + 3, 42)], fill=ic.light + (230,))


@icon('soulreaper', 'soul_siphon', *R)
def soul_siphon(ic):
    def f(d, c):
        d.ellipse([ic.p(20, 8), ic.p(44, 32)], fill=c)
        d.polygon(ic.pts([(20, 22), (44, 22), (38, 46), (32, 40), (26, 58)]), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(25, 16), ic.p(30, 22)], fill=(10, 4, 30, 255))
    ic.d.ellipse([ic.p(34, 16), ic.p(39, 22)], fill=(10, 4, 30, 255))


@icon('soulreaper', 'reaping_whirl', *R)
def reaping_whirl(ic):
    def f(d, c):
        d.ellipse([ic.p(8, 20), ic.p(56, 52)], outline=c, width=3 * 4)
        scythe(ic, d, c, 46, 30, 0.45, 2.2)
        scythe(ic, d, c, 18, 42, 0.45, -0.9)
    ic.shape(f)
    ic.d.ellipse([ic.p(28, 28), ic.p(36, 44)], fill=ic.light + (255,))


@icon('soulreaper', 'deaths_toll', *R)
def deaths_toll(ic):
    def f(d, c):
        d.polygon(ic.pts([(32, 8), (44, 16), (48, 42), (52, 48), (12, 48), (16, 42), (20, 16)]), fill=c)
        d.ellipse([ic.p(28, 50), ic.p(36, 58)], fill=c)
    ic.shape(f)
    ic.d.arc([ic.p(4, 4), ic.p(60, 60)], 200, 340, fill=ic.light + (200,), width=2 * 4)


# ---------------------------------------------------------------------------------------------------------------------
# BLOOMFALL — leaf green / pollen gold
# ---------------------------------------------------------------------------------------------------------------------
N = (0x5BE063, 0x0B2A0E, 0xFFE08A)


def flower(ic, d, c, cx, cy, r, n=6):
    for k in range(n):
        a = k * 2 * math.pi / n
        x, y = cx + math.cos(a) * r * 0.6, cy + math.sin(a) * r * 0.6
        d.ellipse([ic.p(x - r * 0.45, y - r * 0.45), ic.p(x + r * 0.45, y + r * 0.45)], fill=c)


def vine(ic, d, c, pts, w=3):
    d.line([ic.p(x, y) for x, y in pts], fill=c, width=int(w * 4), joint='curve')


@icon('bloomfall', 'thorn_sweep', *N)
def thorn_sweep(ic):
    ic.shape(lambda d, c: d.pieslice([ic.p(6, 10), ic.p(58, 62)], 195, 345, fill=c))
    ic.d.pieslice([ic.p(13, 18), ic.p(57, 64)], 190, 350, fill=mix(ic.dark, ic.primary, 0.2) + (255,))
    for x in range(12, 56, 8):
        ic.d.polygon(ic.pts([(x - 2, 56), (x, 46), (x + 2, 56)]), fill=(60, 110, 40, 255))


@icon('bloomfall', 'entangling_roots', *N)
def entangling_roots(ic):
    def f(d, c):
        vine(ic, d, c, [(10, 58), (18, 40), (12, 26), (22, 12)], 4)
        vine(ic, d, c, [(54, 58), (46, 40), (52, 26), (42, 12)], 4)
        vine(ic, d, c, [(32, 60), (26, 44), (36, 30), (30, 16)], 4)
    ic.shape(f)


@icon('bloomfall', 'venom_bloom', *N)
def venom_bloom(ic):
    def f(d, c):
        flower(ic, d, c, 32, 30, 20)
    ic.shape(f)
    ic.d.ellipse([ic.p(26, 24), ic.p(38, 36)], fill=(120, 220, 60, 255))
    for x, y in ((12, 52), (20, 56), (44, 54), (52, 50)):
        ic.d.ellipse([ic.p(x - 2, y - 2), ic.p(x + 2, y + 2)], fill=(190, 255, 110, 220))


@icon('bloomfall', 'overgrowth', *N)
def overgrowth(ic):
    def f(d, c):
        for x0 in (12, 24, 38, 50):
            vine(ic, d, c, [(x0, 58), (x0 - 4, 44), (x0 + 3, 32), (x0 - 1, 22)], 3)
        flower(ic, d, c, 24, 18, 7, 5)
        flower(ic, d, c, 46, 24, 6, 5)
    ic.shape(f)
    ic.d.rectangle([ic.p(6, 56), ic.p(58, 60)], fill=(70, 50, 30, 255))


@icon('bloomfall', 'wrath_of_the_wild', *N)
def wrath_of_the_wild(ic):
    def f(d, c):
        flower(ic, d, c, 32, 32, 26, 8)
    ic.shape(f)
    ic.d.ellipse([ic.p(24, 24), ic.p(40, 40)], fill=(255, 140, 200, 255))
    ic.d.ellipse([ic.p(29, 29), ic.p(35, 35)], fill=(255, 240, 160, 255))


# ---------------------------------------------------------------------------------------------------------------------
# ECLIPSE REAPER — gold / violet
# ---------------------------------------------------------------------------------------------------------------------
E = (0xFFE7A0, 0x14081F, 0xFFFFFF)
EV = (0x8B3DFF, 0x0A0412, 0xE2C8FF)


@icon('eclipse_reaper', 'eclipse_disc', *E)
def eclipse_disc(ic):
    def f(d, c):
        d.ellipse([ic.p(12, 12), ic.p(52, 52)], outline=c, width=5 * 4)
        for k in range(4):
            a = k * math.pi / 2 + 0.4
            d.polygon(ic.pts([(32 + math.cos(a) * 20, 32 + math.sin(a) * 20), (32 + math.cos(a + 0.5) * 28, 32 + math.sin(a + 0.5) * 28),
                              (32 + math.cos(a + 0.25) * 18, 32 + math.sin(a + 0.25) * 18)]), fill=c)
    ic.shape(f)
    ic.d.pieslice([ic.p(22, 22), ic.p(42, 42)], 90, 270, fill=(255, 220, 120, 255))
    ic.d.pieslice([ic.p(22, 22), ic.p(42, 42)], 270, 90, fill=(110, 40, 220, 255))


@icon('eclipse_reaper', 'solar_flare', *E)
def solar_flare(ic):
    ic.shape(lambda d, c: d.polygon(star(ic, 32, 32, 28, 10, 16), fill=c))
    ic.d.ellipse([ic.p(22, 22), ic.p(42, 42)], fill=(255, 255, 255, 255))


@icon('eclipse_reaper', 'umbral_vortex', *EV)
def umbral_vortex(ic):
    def f(d, c):
        for k in range(4):
            a0 = k * 90
            d.arc([ic.p(8 + k * 3, 8 + k * 3), ic.p(56 - k * 3, 56 - k * 3)], a0, a0 + 200, fill=c, width=3 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(26, 26), ic.p(38, 38)], fill=(4, 0, 10, 255))


@icon('eclipse_reaper', 'equilibrium', *E)
def equilibrium(ic):
    def f(d, c):
        d.ellipse([ic.p(10, 10), ic.p(54, 54)], outline=c, width=3 * 4)
    ic.shape(f)
    ic.d.pieslice([ic.p(14, 14), ic.p(50, 50)], 90, 270, fill=(255, 225, 140, 255))
    ic.d.pieslice([ic.p(14, 14), ic.p(50, 50)], 270, 90, fill=(120, 50, 230, 255))
    ic.d.ellipse([ic.p(23, 14), ic.p(41, 32)], fill=(255, 225, 140, 255))
    ic.d.ellipse([ic.p(23, 32), ic.p(41, 50)], fill=(120, 50, 230, 255))


@icon('eclipse_reaper', 'total_eclipse', *E)
def total_eclipse(ic):
    def f(d, c):
        d.polygon(star(ic, 32, 30, 28, 18, 18), fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(16, 14), ic.p(48, 46)], fill=(6, 2, 12, 255))
    ic.d.ellipse([ic.p(16, 14), ic.p(48, 46)], outline=(180, 120, 255, 255), width=4)


# ---------------------------------------------------------------------------------------------------------------------
# STARFORGE — cosmic violet
# ---------------------------------------------------------------------------------------------------------------------
C = (0x8A6CFF, 0x070A2E, 0xF0EDFF)


def hammer(ic, d, c, cx, cy, s=1.0):
    d.line([ic.p(cx, cy - 4 * s), ic.p(cx, cy + 26 * s)], fill=c, width=int(4 * 4 * s))
    d.rounded_rectangle([ic.p(cx - 14 * s, cy - 16 * s), ic.p(cx + 14 * s, cy - 2 * s)], radius=int(3 * 4 * s), fill=c)


@icon('starforge', 'gravity_slam', *C)
def gravity_slam(ic):
    def f(d, c):
        hammer(ic, d, c, 32, 22, 0.8)
        d.ellipse([ic.p(8, 46), ic.p(56, 60)], outline=c, width=3 * 4)
        for k in range(6):
            a = math.pi + k * math.pi / 5
            d.line([ic.p(32 + math.cos(a) * 26, 53 + math.sin(a) * 7), ic.p(32 + math.cos(a) * 14, 53 + math.sin(a) * 4)], fill=c, width=2 * 4)
    ic.shape(f)


@icon('starforge', 'meteor_strike', *C)
def meteor_strike(ic):
    def f(d, c):
        d.line([ic.p(10, 8), ic.p(36, 38)], fill=c, width=8 * 4)
        d.ellipse([ic.p(30, 32), ic.p(50, 52)], fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(34, 36), ic.p(46, 48)], fill=(255, 160, 80, 255))
    ic.d.rectangle([ic.p(6, 56), ic.p(58, 59)], fill=(255, 140, 60, 255))


@icon('starforge', 'gravity_well', *C)
def gravity_well(ic):
    def f(d, c):
        for k in range(4):
            r = 26 - k * 6
            d.ellipse([ic.p(32 - r, 32 - r * 0.45), ic.p(32 + r, 32 + r * 0.45)], outline=c, width=2 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(27, 28), ic.p(37, 36)], fill=(4, 2, 20, 255))


@icon('starforge', 'event_horizon', *C)
def event_horizon(ic):
    def f(d, c):
        d.ellipse([ic.p(4, 24), ic.p(60, 40)], outline=c, width=4 * 4)
        d.ellipse([ic.p(18, 18), ic.p(46, 46)], outline=c, width=2 * 4)
    ic.shape(f)
    ic.d.ellipse([ic.p(20, 20), ic.p(44, 44)], fill=(0, 0, 0, 255))
    ic.d.arc([ic.p(4, 24), ic.p(60, 40)], 180, 360, fill=(255, 230, 200, 255), width=3 * 4)


@icon('starforge', 'starfall', *C)
def starfall(ic):
    def f(d, c):
        for x, y, s in ((14, 10, 1.0), (34, 6, 0.8), (50, 18, 1.0), (24, 30, 0.7), (44, 38, 0.9)):
            d.line([ic.p(x - 8 * s, y - 8 * s), ic.p(x, y)], fill=c, width=int(3 * 4 * s))
            d.polygon(star(ic, x, y, 6 * s, 2.5 * s, 5), fill=c)
    ic.shape(f)
    ic.d.rectangle([ic.p(6, 56), ic.p(58, 59)], fill=(255, 140, 60, 255))


# ---------------------------------------------------------------------------------------------------------------------
# AETHERLANCE — aether cyan
# ---------------------------------------------------------------------------------------------------------------------
A = (0x5FF3FF, 0x062433, 0xF2FFFF)


def lance(ic, d, c, x0, y0, x1, y1, w=4):
    d.line([ic.p(x0, y0), ic.p(x1, y1)], fill=c, width=int(w * 4))
    ang = math.atan2(y1 - y0, x1 - x0)
    tip = (x1 + math.cos(ang) * 10, y1 + math.sin(ang) * 10)
    l = (x1 + math.cos(ang + 1.9) * 5, y1 + math.sin(ang + 1.9) * 5)
    r = (x1 + math.cos(ang - 1.9) * 5, y1 + math.sin(ang - 1.9) * 5)
    d.polygon(ic.pts([tip, l, r]), fill=c)


@icon('aetherlance', 'aether_bolt', *A)
def aether_bolt(ic):
    def f(d, c):
        lance(ic, d, c, 8, 56, 30, 34)
        d.ellipse([ic.p(38, 16), ic.p(54, 32)], fill=c)
    ic.shape(f)
    ic.d.ellipse([ic.p(42, 20), ic.p(50, 28)], fill=(255, 255, 255, 255))


@icon('aetherlance', 'piercing_charge', *A)
def piercing_charge(ic):
    def f(d, c):
        lance(ic, d, c, 10, 32, 44, 32, 5)
        for y in (22, 42):
            d.line([ic.p(6, y), ic.p(26, y)], fill=c, width=2 * 4)
    ic.shape(f)


@icon('aetherlance', 'aether_resonance', *A)
def aether_resonance(ic):
    def f(d, c):
        for k in range(3):
            r = 10 + k * 8
            d.arc([ic.p(32 - r, 32 - r), ic.p(32 + r, 32 + r)], -50, 50, fill=c, width=3 * 4)
        lance(ic, d, c, 6, 32, 22, 32, 4)
    ic.shape(f)


@icon('aetherlance', 'celestial_barrage', *A)
def celestial_barrage(ic):
    def f(d, c):
        for a in (-0.6, -0.3, 0, 0.3, 0.6):
            x1, y1 = 12 + math.cos(a) * 38, 32 + math.sin(a) * 38
            d.line([ic.p(12, 32), ic.p(x1, y1)], fill=c, width=3 * 4)
            d.ellipse([ic.p(x1 - 3, y1 - 3), ic.p(x1 + 3, y1 + 3)], fill=c)
    ic.shape(f)


@icon('aetherlance', 'judgement_ray', *A)
def judgement_ray(ic):
    def f(d, c):
        d.polygon(ic.pts([(8, 28), (58, 18), (58, 46), (8, 36)]), fill=c)
    ic.shape(f)
    ic.d.polygon(ic.pts([(8, 31), (58, 26), (58, 38), (8, 33)]), fill=(255, 255, 255, 255))
    ic.d.ellipse([ic.p(4, 26), ic.p(14, 38)], fill=(255, 217, 120, 255))


# ---------------------------------------------------------------------------------------------------------------------
# MONOLITH — quarried stone / limestone / molten amber ground energy
# ---------------------------------------------------------------------------------------------------------------------
M = (0xE0B070, 0x1E1812, 0xFFF0D8)
AMBER = (255, 166, 64, 255)


def greatsword_down(ic, d, c, cx, top, length, w=7):
    """A colossal blade standing point-down: pommel, guard, blade ending at top + length."""
    d.rectangle([ic.p(cx - 2, top), ic.p(cx + 2, top + 8)], fill=c)
    d.ellipse([ic.p(cx - 4, top - 4), ic.p(cx + 4, top + 3)], fill=c)
    d.rectangle([ic.p(cx - 13, top + 8), ic.p(cx + 13, top + 12)], fill=c)
    d.polygon(ic.pts([(cx - w, top + 12), (cx + w, top + 12), (cx + w, top + length - 6), (cx, top + length), (cx - w, top + length - 6)]), fill=c)


def crack(ic, d, c, pts, w=3):
    d.line([ic.p(x, y) for x, y in pts], fill=c, width=int(w * 4), joint='curve')


@icon('monolith', 'earthshatter', *M)
def earthshatter(ic):
    def f(d, c):
        greatsword_down(ic, d, c, 32, 6, 44)
        d.rectangle([ic.p(4, 46), ic.p(60, 49)], fill=c)
    ic.shape(f)
    for pts in (((32, 50), (24, 54), (16, 52), (8, 58)), ((32, 50), (40, 55), (50, 53), (58, 59)), ((32, 50), (30, 58), (34, 62))):
        crack(ic, ic.d, AMBER, pts, 2)


@icon('monolith', 'seismic_fissure', *M)
def seismic_fissure(ic):
    def f(d, c):
        for x, h in ((14, 12), (26, 18), (38, 24), (50, 30)):
            d.polygon(ic.pts([(x - 5, 52), (x - 1, 52 - h), (x + 2, 52 - h + 4), (x + 5, 52)]), fill=c)
        d.rectangle([ic.p(4, 52), ic.p(60, 55)], fill=c)
    ic.shape(f)
    crack(ic, ic.d, AMBER, ((6, 58), (18, 56), (28, 59), (40, 56), (52, 59), (60, 57)), 3)


@icon('monolith', 'mountains_weight', *M)
def mountains_weight(ic):
    def f(d, c):
        d.polygon(ic.pts([(6, 50), (24, 16), (32, 28), (40, 18), (58, 50)]), fill=c)
    ic.shape(f)
    ic.d.polygon(ic.pts([(24, 16), (29, 25), (20, 25)]), fill=(255, 250, 240, 255))
    for r in (10, 18, 26):
        ic.d.arc([ic.p(32 - r, 52 - r * 0.3), ic.p(32 + r, 52 + r * 0.3)], 0, 180, fill=AMBER, width=2 * 4)


@icon('monolith', 'tectonic_slam', *M)
def tectonic_slam(ic):
    def f(d, c):
        greatsword_down(ic, d, c, 32, 2, 36, w=6)
        for x, h in ((10, 14), (20, 20), (44, 20), (54, 14)):
            d.polygon(ic.pts([(x - 5, 58), (x, 58 - h), (x + 5, 58)]), fill=c)
    ic.shape(f)
    ic.d.arc([ic.p(4, 40), ic.p(60, 64)], 180, 360, fill=AMBER, width=3 * 4)


@icon('monolith', 'worldbreaker', *M)
def worldbreaker(ic):
    def f(d, c):
        greatsword_down(ic, d, c, 32, 4, 34, w=6)
    ic.shape(f)
    for k in range(8):
        a = k * math.pi / 4 + 0.2
        pts = [(32, 40)]
        for j in range(1, 4):
            r = j * 8
            pts.append((32 + math.cos(a + (0.15 if j % 2 else -0.15)) * r, 40 + math.sin(a + (0.15 if j % 2 else -0.15)) * r * 0.55))
        crack(ic, ic.d, AMBER, pts, 2)
    ic.d.ellipse([ic.p(26, 36), ic.p(38, 44)], fill=(255, 231, 184, 255))


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
