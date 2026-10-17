#!/usr/bin/env python3
"""
Generates generated support assets for AI Girlfriend Companion:

  - Player animations  assets/aigirlfriend/player_animations/player.animation.json
  - GUI icon atlas     assets/aigirlfriend/textures/gui/icons.png
  - Mod logo           logo.png

(The stand-in companion model that used to be generated here was replaced by the real character in art/character.)
The companion model/animation code below (BONES, GF, make_skin...) is kept only as a reference for a minimal rig.

Usage:  python3 tools/standin_assets.py   (from the repository root; needs Pillow)
"""
import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "aigirlfriend")


def out(path):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


# ----------------------------------------------------------------------------------------------- model
# Bedrock coordinates (pixels, +Y up, feet at y=0). 64x64 texture, box UV.
def cube(origin, size, uv, inflate=0.0, mirror=False):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    if mirror:
        c["mirror"] = True
    return c


BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "body", "parent": "root", "pivot": [0, 12, 0]},
    {"name": "torso", "parent": "body", "pivot": [0, 12, 0], "cubes": [cube([-3.5, 12, -2], [7, 8, 4], [16, 16])]},
    {"name": "skirt", "parent": "body", "pivot": [0, 13, 0], "cubes": [cube([-4, 9, -2.5], [8, 4, 5], [16, 32])]},
    {"name": "head", "parent": "torso", "pivot": [0, 20, 0], "cubes": [cube([-4, 20, -4], [8, 8, 8], [0, 0])]},
    {"name": "hair", "parent": "head", "pivot": [0, 20, 0], "cubes": [cube([-4, 20, -4], [8, 8, 8], [32, 0], inflate=0.5)]},
    {"name": "hair_back", "parent": "head", "pivot": [0, 27, 4], "cubes": [cube([-4, 12, 3.6], [8, 10, 1], [42, 32])]},
    {"name": "right_arm", "parent": "torso", "pivot": [-5, 19.5, 0], "cubes": [cube([-6.5, 11, -1.5], [3, 9, 3], [40, 16])]},
    {"name": "left_arm", "parent": "torso", "pivot": [5, 19.5, 0], "cubes": [cube([3.5, 11, -1.5], [3, 9, 3], [52, 16])]},
    {"name": "right_leg", "parent": "root", "pivot": [-1.5, 12, 0], "cubes": [cube([-3, 0, -1.5], [3, 12, 3], [0, 16])]},
    {"name": "left_leg", "parent": "root", "pivot": [1.5, 12, 0], "cubes": [cube([0, 0, -1.5], [3, 12, 3], [0, 32])]},
]

GEO = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.girlfriend",
            "texture_width": 64, "texture_height": 64,
            "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0],
        },
        "bones": BONES,
    }],
}

# ----------------------------------------------------------------------------------------------- textures
SKIN = (255, 222, 202)
SKIN_SHADE = (240, 200, 182)
HAIR = (120, 72, 52)
HAIR_LIGHT = (150, 96, 70)
HAIR_DARK = (92, 52, 38)
EYE = (78, 52, 110)
EYE_LIGHT = (150, 120, 200)
WHITE = (250, 250, 250)
BLUSH = (250, 165, 170)
MOUTH = (200, 90, 100)

OUTFITS = [
    {"top": (250, 248, 252), "top_shade": (228, 222, 236), "accent": (255, 140, 180), "skirt": (255, 150, 190), "skirt_shade": (226, 120, 160), "legs": SKIN, "shoes": (120, 70, 90)},
    {"top": (160, 200, 250), "top_shade": (130, 170, 225), "accent": (255, 255, 255), "skirt": (150, 190, 245), "skirt_shade": (120, 160, 220), "legs": (250, 250, 250), "shoes": (70, 90, 140)},
    {"top": (245, 214, 150), "top_shade": (220, 188, 126), "accent": (180, 90, 60), "skirt": (70, 60, 80), "skirt_shade": (52, 44, 60), "legs": (60, 50, 66), "shoes": (40, 34, 40)},
]


def box_regions(u, v, w, h, d):
    """Box-UV face rectangles: (x0, y0, x1, y1) for top, bottom, east(right side), north(front), west, south(back)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fill_box(px, u, v, w, h, d, color, shade=None):
    for name, (x0, y0, x1, y1) in box_regions(u, v, w, h, d).items():
        c = shade if shade and name in ("east", "west", "bottom") else color
        for x in range(int(x0), int(x1)):
            for y in range(int(y0), int(y1)):
                px[x, y] = c + (255,)


def draw_face(px, ox, oy, expression):
    """Paints an 8x8 face at (ox, oy) = top-left of the head's front face."""
    for x in range(8):
        for y in range(8):
            px[ox + x, oy + y] = SKIN + (255,)
    # bangs on the first row
    for x in range(8):
        px[ox + x, oy] = HAIR + (255,)
    px[ox + 0, oy + 1] = HAIR + (255,)
    px[ox + 7, oy + 1] = HAIR + (255,)
    px[ox + 3, oy + 1] = HAIR_DARK + (255,)

    def eye(x, closed=False, happy=False, wide=False, look_down=False):
        if closed:
            px[ox + x, oy + 4] = EYE + (255,)
            px[ox + x + 1, oy + 4] = EYE + (255,)
            return
        if happy:
            px[ox + x, oy + 4] = EYE + (255,)
            px[ox + x + 1, oy + 3] = EYE + (255,)
            return
        top = 2 if wide else 3
        for yy in range(top, 5):
            px[ox + x, oy + yy] = EYE + (255,)
            px[ox + x + 1, oy + yy] = EYE + (255,)
        if not look_down:
            px[ox + x + 1, oy + top] = EYE_LIGHT + (255,)
        px[ox + x, oy + top] = WHITE + (255,) if wide else EYE + (255,)

    def blush():
        px[ox + 1, oy + 5] = BLUSH + (255,)
        px[ox + 6, oy + 5] = BLUSH + (255,)

    def mouth(kind):
        if kind == "smile":
            px[ox + 3, oy + 6] = MOUTH + (255,)
            px[ox + 4, oy + 6] = MOUTH + (255,)
        elif kind == "open":
            px[ox + 3, oy + 6] = MOUTH + (255,)
            px[ox + 4, oy + 6] = MOUTH + (255,)
            px[ox + 3, oy + 7] = (150, 60, 70, 255)
            px[ox + 4, oy + 7] = (150, 60, 70, 255)
        elif kind == "small":
            px[ox + 4, oy + 6] = MOUTH + (255,)
        elif kind == "frown":
            px[ox + 3, oy + 7] = MOUTH + (255,)
            px[ox + 4, oy + 7] = MOUTH + (255,)
            px[ox + 2, oy + 7] = SKIN_SHADE + (255,)
            px[ox + 5, oy + 7] = SKIN_SHADE + (255,)
        elif kind == "wavy":
            px[ox + 2, oy + 6] = MOUTH + (255,)
            px[ox + 3, oy + 7] = MOUTH + (255,)
            px[ox + 4, oy + 6] = MOUTH + (255,)
            px[ox + 5, oy + 7] = MOUTH + (255,)
        elif kind == "kiss":
            px[ox + 4, oy + 6] = (230, 90, 120, 255)
            px[ox + 4, oy + 7] = (230, 90, 120, 255)

    e = expression
    if e == "neutral":
        eye(1); eye(5); mouth("small")
    elif e == "happy":
        eye(1, happy=True); eye(5, happy=True); mouth("open"); blush()
    elif e == "sad":
        eye(1, look_down=True); eye(5, look_down=True); mouth("frown")
        px[ox + 1, oy + 2] = HAIR_DARK + (255,); px[ox + 6, oy + 2] = HAIR_DARK + (255,)
    elif e == "fear":
        eye(1, wide=True); eye(5, wide=True); mouth("wavy")
    elif e == "embarrassed":
        eye(1, closed=True); eye(5, closed=True); mouth("wavy"); blush()
        px[ox + 2, oy + 5] = BLUSH + (255,); px[ox + 5, oy + 5] = BLUSH + (255,)
    elif e == "affection":
        eye(1, happy=True); eye(5, happy=True); mouth("kiss"); blush()
    elif e == "angry":
        eye(1); eye(5); mouth("frown")
        px[ox + 1, oy + 2] = HAIR_DARK + (255,); px[ox + 2, oy + 3] = HAIR_DARK + (255,)
        px[ox + 6, oy + 2] = HAIR_DARK + (255,); px[ox + 5, oy + 3] = HAIR_DARK + (255,)
    elif e == "sleepy":
        eye(1, closed=True); eye(5, closed=True); mouth("small")
    elif e == "surprised":
        eye(1, wide=True); eye(5, wide=True); mouth("open")
    elif e == "blink":
        eye(1, closed=True); eye(5, closed=True); mouth("small")


def make_skin(outfit):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    o = OUTFITS[outfit]
    # head: skin with hair on top/back/sides
    fill_box(px, 0, 0, 8, 8, 8, SKIN, SKIN_SHADE)
    r = box_regions(0, 0, 8, 8, 8)
    for name in ("top", "south"):
        x0, y0, x1, y1 = r[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = (HAIR_LIGHT if (x + y) % 5 == 0 else HAIR) + (255,)
    for name in ("east", "west"):
        x0, y0, x1, y1 = r[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                if y < y0 + 5 or (name == "east" and x < x0 + 3) or (name == "west" and x > x1 - 4):
                    px[x, y] = HAIR + (255,)
    draw_face(px, 8, 8, "neutral")
    # hair overlay layer (inflated): bangs + side locks + top
    hr = box_regions(32, 0, 8, 8, 8)
    for name in ("top", "south"):
        x0, y0, x1, y1 = hr[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = (HAIR_DARK if (x * 3 + y) % 7 == 0 else HAIR) + (255,)
    x0, y0, x1, y1 = hr["north"]
    for x in range(x0, x1):
        px[x, y0] = HAIR + (255,)
        if (x - x0) in (0, 1, 6, 7):
            px[x, y0 + 1] = HAIR + (255,)
        if (x - x0) in (0, 7):
            for y in range(y0 + 2, y0 + 7):
                px[x, y] = HAIR + (255,)
    for name in ("east", "west"):
        x0, y0, x1, y1 = hr[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = HAIR + (255,)
    px[hr["north"][0] + 6, hr["north"][1]] = o["accent"] + (255,)  # hair clip
    px[hr["north"][0] + 6, hr["north"][1] + 1] = o["accent"] + (255,)
    # long hair back
    fill_box(px, 42, 32, 8, 10, 1, HAIR, HAIR_DARK)
    for x in range(43, 51):
        for y in range(33, 43):
            if (x + y) % 6 == 0:
                px[x, y] = HAIR_LIGHT + (255,)
    # torso (top)
    fill_box(px, 16, 16, 7, 8, 4, o["top"], o["top_shade"])
    tr = box_regions(16, 16, 7, 8, 4)
    x0, y0, x1, y1 = tr["north"]
    px[x0 + 3, y0] = SKIN + (255,)
    px[x0 + 2, y0] = SKIN + (255,)
    px[x0 + 4, y0] = SKIN + (255,)
    px[x0 + 3, y0 + 1] = o["accent"] + (255,)  # bow
    px[x0 + 2, y0 + 1] = o["accent"] + (255,)
    px[x0 + 4, y0 + 1] = o["accent"] + (255,)
    for x in range(x0, x1):
        px[x, y1 - 1] = o["accent"] + (255,)  # waist band
    # arms: sleeves + skin hands
    for u in (40, 52):
        fill_box(px, u, 16, 3, 9, 3, SKIN, SKIN_SHADE)
        ar = box_regions(u, 16, 3, 9, 3)
        for name in ("east", "north", "west", "south"):
            x0, y0, x1, y1 = ar[name]
            for x in range(x0, x1):
                for y in range(y0, y0 + 4):
                    px[x, y] = o["top"] + (255,)
                px[x, y0 + 4] = o["accent"] + (255,)
        x0, y0, x1, y1 = ar["top"]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = o["top"] + (255,)
    # skirt
    fill_box(px, 16, 32, 8, 4, 5, o["skirt"], o["skirt_shade"])
    sr = box_regions(16, 32, 8, 4, 5)
    for name in ("east", "north", "west", "south"):
        x0, y0, x1, y1 = sr[name]
        for x in range(x0, x1):
            if x % 2 == 0:
                px[x, y1 - 1] = o["skirt_shade"] + (255,)
    # legs
    for v in (16, 32):
        fill_box(px, 0, v, 3, 12, 3, o["legs"], tuple(max(0, c - 18) for c in o["legs"]))
        lr = box_regions(0, v, 3, 12, 3)
        for name in ("east", "north", "west", "south"):
            x0, y0, x1, y1 = lr[name]
            for x in range(x0, x1):
                for y in range(y1 - 2, y1):
                    px[x, y] = o["shoes"] + (255,)
        x0, y0, x1, y1 = lr["bottom"]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = o["shoes"] + (255,)
    return img


def make_face(expression):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    draw_face(img.load(), 8, 8, expression)
    return img


# ----------------------------------------------------------------------------------------------- animations
def kf(frames, lerp="catmullrom"):
    """frames: list of (time, [x, y, z])"""
    if len(frames) == 1:
        return frames[0][1]
    d = {}
    for t, v in frames:
        d[f"{t:.4g}"] = {"post": v, "lerp_mode": lerp} if lerp != "linear" else v
    return d


def wave(length, amp, axis=0, base=None, phase=0.0, steps=8, offset=None):
    base = base or [0, 0, 0]
    frames = []
    for i in range(steps + 1):
        t = length * i / steps
        v = list(base)
        val = amp * math.sin(2 * math.pi * (i / steps) + phase)
        if offset:
            v = [base[k] + offset[k] * math.sin(2 * math.pi * (i / steps) + phase) for k in range(3)]
        else:
            v[axis] = base[axis] + val
        frames.append((t, [round(x, 3) for x in v]))
    return kf(frames)


def anim(length, bones, loop=True):
    a = {"animation_length": length, "bones": bones}
    if loop is True:
        a["loop"] = True
    elif loop == "hold":
        a["loop"] = "hold_on_last_frame"
    return a


def walk_cycle(length, leg, arm, lean=0.0, bounce=0.0):
    return {
        "right_leg": {"rotation": wave(length, leg, 0)},
        "left_leg": {"rotation": wave(length, leg, 0, phase=math.pi)},
        "right_arm": {"rotation": wave(length, arm, 0, phase=math.pi)},
        "left_arm": {"rotation": wave(length, arm, 0)},
        "body": {"rotation": [lean, 0, 0], "position": wave(length / 2, bounce, 1) if bounce else [0, 0, 0]},
        "head": {"rotation": [-lean * 0.6, 0, 0]},
    }


GF = {}
# ---- locomotion
GF["move.idle"] = anim(4.0, {
    "torso": {"rotation": wave(4.0, 1.2, 0)},
    "right_arm": {"rotation": wave(4.0, 2.0, 2, base=[0, 0, 3])},
    "left_arm": {"rotation": wave(4.0, 2.0, 2, base=[0, 0, -3], phase=math.pi)},
    "head": {"rotation": wave(4.0, 2.0, 0, phase=0.5)},
    "hair_back": {"rotation": wave(4.0, 2.0, 0, base=[4, 0, 0])},
})
GF["move.idle_tired"] = anim(5.0, {
    "torso": {"rotation": [8, 0, 0]},
    "head": {"rotation": wave(5.0, 3.0, 0, base=[14, 0, 0])},
    "right_arm": {"rotation": [-4, 0, 2]},
    "left_arm": {"rotation": [-4, 0, -2]},
})
GF["move.idle_sad"] = anim(5.0, {
    "torso": {"rotation": [6, 0, 0]},
    "head": {"rotation": wave(5.0, 2.0, 2, base=[18, 0, 0])},
    "right_arm": {"rotation": [-12, 10, 6]},
    "left_arm": {"rotation": [-12, -10, -6]},
})
GF["move.idle_scared"] = anim(0.6, {
    "torso": {"rotation": [6, 0, 0], "position": wave(0.3, 0.15, 0)},
    "head": {"rotation": [8, 0, 0]},
    "right_arm": {"rotation": [-70, 40, 10]},
    "left_arm": {"rotation": [-70, -40, -10]},
})
GF["move.hide"] = anim(0.8, {
    "body": {"rotation": [18, 0, 0], "position": [0, -2, 0]},
    "head": {"rotation": [10, 0, 0]},
    "right_arm": {"rotation": [-130, 30, 0]},
    "left_arm": {"rotation": [-130, -30, 0]},
    "right_leg": {"rotation": [-20, 0, 0]},
    "left_leg": {"rotation": [-20, 0, 0]},
})
GF["move.walk"] = anim(1.0, walk_cycle(1.0, 32, 26, 0, 0.3) | {"hair_back": {"rotation": wave(0.5, 3, 0, base=[8, 0, 0])}})
GF["move.walk_tired"] = anim(1.4, walk_cycle(1.4, 22, 10, 8, 0.2))
GF["move.walk_scared"] = anim(0.8, walk_cycle(0.8, 30, 8, 6, 0.3) | {"right_arm": {"rotation": [-60, 35, 0]}, "left_arm": {"rotation": [-60, -35, 0]}})
GF["move.run"] = anim(0.6, walk_cycle(0.6, 55, 50, 10, 0.6) | {"hair_back": {"rotation": wave(0.3, 6, 0, base=[25, 0, 0])}})
GF["move.crouch"] = anim(2.0, {"body": {"rotation": [25, 0, 0], "position": [0, -2, 0]}, "head": {"rotation": [-20, 0, 0]},
                                "right_leg": {"rotation": [-25, 0, 0]}, "left_leg": {"rotation": [-25, 0, 0]}})
GF["move.crouch_walk"] = anim(1.2, walk_cycle(1.2, 25, 15, 25, 0) | {"body": {"rotation": [25, 0, 0], "position": [0, -2, 0]}})
GF["move.swim"] = anim(1.6, {"body": {"rotation": [10, 0, 0]}, "right_arm": {"rotation": wave(1.6, 70, 0, base=[-90, 0, 20])},
                              "left_arm": {"rotation": wave(1.6, 70, 0, base=[-90, 0, -20], phase=math.pi)},
                              "right_leg": {"rotation": wave(0.8, 20, 0)}, "left_leg": {"rotation": wave(0.8, 20, 0, phase=math.pi)}})
# ---- poses
SIT_LEGS = {"right_leg": {"rotation": [-88, 6, 0]}, "left_leg": {"rotation": [-88, -6, 0]}}
GF["pose.sit"] = anim(4.0, SIT_LEGS | {
    "right_arm": {"rotation": [-28, 12, 0]}, "left_arm": {"rotation": [-28, -12, 0]},
    "head": {"rotation": wave(4.0, 2, 0)},
})
GF["pose.sit_ground"] = anim(4.0, SIT_LEGS | {
    "root": {"position": [0, -11, 0]},
    "right_arm": {"rotation": [-18, 0, 12]}, "left_arm": {"rotation": [-18, 0, -12]},
})
GF["pose.sleep"] = anim(4.0, {
    "torso": {"rotation": wave(4.0, 1.5, 0)},
    "right_arm": {"rotation": [-20, 0, 8]}, "left_arm": {"rotation": [-30, -20, -10]},
    "head": {"rotation": [0, 0, 10]},
})
GF["pose.fainted"] = anim(2.0, {
    "root": {"rotation": [-90, 0, 0], "position": [0, 2, 0]},
    "right_arm": {"rotation": [0, 0, 25]}, "left_arm": {"rotation": [0, 0, -25]},
    "head": {"rotation": [0, 0, 20]},
}, loop="hold")
GF["pose.carried"] = anim(3.0, {
    "root": {"rotation": [-75, 0, 0], "position": [0, 4, 0]},
    "right_leg": {"rotation": [-55, 0, 0]}, "left_leg": {"rotation": [-55, 0, 0]},
    "head": {"rotation": [-25, 0, 0]},
})
# ---- interactions (she is the left side of these when facing the player)
GF["interaction.hug"] = anim(2.0, {
    "right_arm": {"rotation": wave(2.0, 4, 1, base=[-80, 28, 0])}, "left_arm": {"rotation": wave(2.0, 4, 1, base=[-80, -28, 0], phase=math.pi)},
    "head": {"rotation": [5, 0, 14]}, "torso": {"rotation": wave(2.0, 2, 2)},
})
GF["interaction.back_hug"] = anim(2.0, {
    "right_arm": {"rotation": [-80, 32, 0]}, "left_arm": {"rotation": [-80, -32, 0]},
    "head": {"rotation": [8, 0, -14]}, "torso": {"rotation": [6, 0, 0]},
})
GF["interaction.cuddle"] = anim(3.0, {
    "right_arm": {"rotation": [-75, 35, 0]}, "left_arm": {"rotation": [-75, -35, 0]},
    "head": {"rotation": wave(3.0, 3, 2, base=[12, 0, 18])}, "torso": {"rotation": wave(3.0, 3, 2, base=[4, 0, 0])},
})
GF["interaction.kiss"] = anim(2.7, {
    "torso": {"rotation": kf([(0, [0, 0, 0]), (0.5, [6, 0, 0]), (2.2, [6, 0, 0]), (2.7, [0, 0, 0])])},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-14, 0, 10]), (2.2, [-14, 0, 10]), (2.7, [0, 0, 0])])},
    "right_arm": {"rotation": [-110, 20, 0]}, "left_arm": {"rotation": [-110, -20, 0]},
    "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 1, 0]), (2.3, [0, 1, 0]), (2.7, [0, 0, 0])])},
}, loop="hold")
GF["interaction.kiss_cheek"] = anim(1.8, {
    "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 1.5, 0]), (1.3, [0, 1.5, 0]), (1.8, [0, 0, 0])])},
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-10, 25, 0]), (1.3, [-10, 25, 0]), (1.8, [0, 0, 0])])},
    "right_arm": {"rotation": [20, 0, 10]}, "left_arm": {"rotation": [20, 0, -10]},
}, loop="hold")
GF["interaction.forehead_kiss"] = anim(2.3, {
    "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.8, [12, 0, 0]), (2.3, [0, 0, 0])])},
    "right_arm": {"rotation": [-30, 30, 0]}, "left_arm": {"rotation": [-30, -30, 0]},
}, loop="hold")
GF["interaction.lean"] = anim(3.0, {
    "torso": {"rotation": wave(3.0, 1.5, 0, base=[0, 0, -10])}, "head": {"rotation": [6, 0, -18]},
    "left_arm": {"rotation": [-25, -15, -10]}, "right_arm": {"rotation": [-10, 0, 4]},
})
GF["interaction.hold_hands"] = anim(1.0, {"left_arm": {"rotation": [-12, 0, -18]}})
GF["interaction.lead_by_hand"] = anim(1.0, {"right_arm": {"rotation": [25, 0, 18]}, "head": {"rotation": [0, 20, 0]}})
GF["interaction.sit_together"] = anim(4.0, SIT_LEGS | {"right_arm": {"rotation": [-28, 12, 0]}, "left_arm": {"rotation": [-28, -12, 0]}})
GF["interaction.head_on_shoulder"] = anim(4.0, SIT_LEGS | {
    "torso": {"rotation": [0, 0, -8]}, "head": {"rotation": wave(4.0, 1.5, 0, base=[6, 0, -26])},
    "right_arm": {"rotation": [-30, 20, 0]}, "left_arm": {"rotation": [-26, -10, 0]},
})
GF["interaction.cuddle_sitting"] = anim(4.0, SIT_LEGS | {
    "torso": {"rotation": [0, 10, -14]}, "head": {"rotation": [8, 0, -28]},
    "left_arm": {"rotation": [-65, -45, 0]}, "right_arm": {"rotation": [-40, 30, 0]},
})
GF["interaction.carried"] = anim(3.0, {
    "root": {"rotation": [-75, 0, 0], "position": [0, 4, 0]},
    "right_leg": {"rotation": [-55, 0, 0]}, "left_leg": {"rotation": [-55, 0, 0]},
    "right_arm": {"rotation": [-160, 0, -20]}, "left_arm": {"rotation": [-150, 0, 30]},
    "head": {"rotation": [-20, 0, -15]},
})
GF["interaction.cuddle_bed"] = anim(4.0, {
    "root": {"rotation": [-90, 0, 0], "position": [0, 11, 0]},
    "right_leg": {"rotation": [0, 0, 0]}, "left_leg": {"rotation": [0, 0, 0]},
    "right_arm": {"rotation": [-40, 0, 60]}, "left_arm": {"rotation": [0, 0, -8]},
    "head": {"rotation": [0, 0, 24]}, "torso": {"rotation": wave(4.0, 1.5, 0)},
})
GF["interaction.pet_hair"] = anim(1.6, {
    "head": {"rotation": wave(1.6, 3, 2, base=[10, 0, 0])}, "torso": {"rotation": wave(1.6, 1.5, 2)},
    "right_arm": {"rotation": [20, -20, 0]}, "left_arm": {"rotation": [20, 20, 0]},
})
GF["interaction.pet_cheek"] = anim(2.0, {
    "head": {"rotation": wave(2.0, 4, 2, base=[0, -10, 16])},
    "right_arm": {"rotation": [-40, 30, 0]}, "left_arm": {"rotation": [10, 0, -6]},
})
GF["pose.lap_sit"] = anim(4.0, SIT_LEGS | {"right_arm": {"rotation": [-25, 10, 0]}, "left_arm": {"rotation": [-25, -10, 0]}})
GF["interaction.lap_sit"] = anim(4.0, SIT_LEGS | {
    "right_arm": {"rotation": [-150, -20, -25]}, "left_arm": {"rotation": [-60, -30, 0]},
    "head": {"rotation": wave(4.0, 2, 2, base=[6, -20, 14])}, "torso": {"rotation": [0, 0, 8]},
})
GF["interaction.sleep_together"] = anim(4.0, {
    "right_arm": {"rotation": [-40, 0, 55]}, "left_arm": {"rotation": [0, 0, -6]},
    "head": {"rotation": [0, 0, 18]},
})
# ---- gestures (play once)
G = {}
G["action.wave"] = anim(2.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-160, 0, -25]), (0.6, [-160, 0, 5]), (0.9, [-160, 0, -25]), (1.2, [-160, 0, 5]), (1.6, [-160, 0, -20]), (2.0, [0, 0, 0])])},
                           "head": {"rotation": [0, 0, 6]}}, loop=False)
G["action.wave_goodbye"] = anim(2.5, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-150, 0, -20]), (0.8, [-150, 0, 10]), (1.2, [-150, 0, -20]), (1.6, [-150, 0, 10]), (2.0, [-150, 0, -10]), (2.5, [0, 0, 0])])},
                                  "head": {"rotation": [6, 0, 8]}}, loop=False)
G["action.yawn"] = anim(2.5, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-25, 0, 0]), (1.8, [-25, 0, 0]), (2.5, [0, 0, 0])])},
                             "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.6, [-140, 20, 0]), (1.8, [-140, 20, 0]), (2.5, [0, 0, 0])])}}, loop=False)
G["action.stretch"] = anim(3.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-175, 0, -12]), (2.2, [-175, 0, -12]), (3.0, [0, 0, 0])])},
                                "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-175, 0, 12]), (2.2, [-175, 0, 12]), (3.0, [0, 0, 0])])},
                                "torso": {"rotation": kf([(0, [0, 0, 0]), (0.8, [-8, 0, 0]), (2.2, [-8, 0, 0]), (3.0, [0, 0, 0])])},
                                "root": {"position": kf([(0, [0, 0, 0]), (0.8, [0, 1, 0]), (2.2, [0, 1, 0]), (3.0, [0, 0, 0])])}}, loop=False)
G["action.eat"] = anim(2.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-115, 35, 0]), (0.6, [-105, 35, 0]), (0.9, [-115, 35, 0]), (1.2, [-105, 35, 0]), (1.5, [-115, 35, 0]), (2.0, [0, 0, 0])])},
                            "head": {"rotation": wave(0.3, 5, 0, base=[8, 0, 0])}}, loop=False)
G["action.drink"] = anim(1.8, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-130, 30, 0]), (1.4, [-150, 30, 0]), (1.8, [0, 0, 0])])},
                              "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.4, [-30, 0, 0]), (1.8, [0, 0, 0])])}}, loop=False)
G["action.look_around"] = anim(3.5, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.8, [0, 40, 0]), (1.6, [0, 40, 0]), (2.4, [0, -40, 0]), (3.0, [0, -40, 0]), (3.5, [0, 0, 0])])}}, loop=False)
G["action.look_at_animal"] = anim(4.0, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [10, 0, 15]), (3.5, [10, 0, 15]), (4.0, [0, 0, 0])])},
                                       "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-40, 20, 0]), (3.5, [-40, 20, 0]), (4.0, [0, 0, 0])])},
                                       "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-40, -20, 0]), (3.5, [-40, -20, 0]), (4.0, [0, 0, 0])])}}, loop=False)
G["action.startled"] = anim(1.3, {"root": {"position": kf([(0, [0, 0, 0]), (0.15, [0, 2, -1]), (0.4, [0, 0, -1.5]), (1.3, [0, 0, 0])])},
                                 "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-120, 20, -30]), (0.9, [-100, 20, -20]), (1.3, [0, 0, 0])])},
                                 "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-120, -20, 30]), (0.9, [-100, -20, 20]), (1.3, [0, 0, 0])])},
                                 "head": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-12, 0, 0]), (1.3, [0, 0, 0])])}}, loop=False)
G["action.celebrate"] = anim(3.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-170, 0, -15]), (2.6, [-170, 0, -15]), (3.0, [0, 0, 0])])},
                                  "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-170, 0, 15]), (2.6, [-170, 0, 15]), (3.0, [0, 0, 0])])},
                                  "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 3, 0]), (0.7, [0, 0, 0]), (1.0, [0, 3, 0]), (1.3, [0, 0, 0]), (1.6, [0, 3, 0]), (1.9, [0, 0, 0])])}}, loop=False)
G["action.sad"] = anim(4.0, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.8, [25, 0, 0]), (3.2, [25, 0, 0]), (4.0, [0, 0, 0])])},
                            "torso": {"rotation": kf([(0, [0, 0, 0]), (0.8, [8, 0, 0]), (3.2, [8, 0, 0]), (4.0, [0, 0, 0])])},
                            "right_arm": {"rotation": [-10, 15, 0]}, "left_arm": {"rotation": [-10, -15, 0]}}, loop=False)
G["action.reach_hand"] = anim(2.5, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-45, 10, 0]), (2.0, [-45, 10, 0]), (2.5, [0, 0, 0])])},
                                   "head": {"rotation": [5, 0, 10]}}, loop=False)
G["action.gift_happy"] = anim(2.5, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-70, 40, 0]), (2.1, [-70, 40, 0]), (2.5, [0, 0, 0])])},
                                   "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-70, -40, 0]), (2.1, [-70, -40, 0]), (2.5, [0, 0, 0])])},
                                   "root": {"position": kf([(0, [0, 0, 0]), (0.4, [0, 2, 0]), (0.7, [0, 0, 0]), (1.0, [0, 2, 0]), (1.3, [0, 0, 0])])},
                                   "head": {"rotation": wave(1.0, 6, 2)}}, loop=False)
G["action.shy"] = anim(2.5, {"right_arm": {"rotation": [25, -20, 0]}, "left_arm": {"rotation": [25, 20, 0]},
                            "head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [20, 0, 14]), (2.1, [20, 0, 14]), (2.5, [0, 0, 0])])},
                            "torso": {"rotation": wave(1.25, 4, 1)}}, loop=False)
G["action.hurt"] = anim(0.6, {"torso": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-12, 0, 0]), (0.6, [0, 0, 0])])},
                             "head": {"rotation": kf([(0, [0, 0, 0]), (0.1, [-15, 0, 0]), (0.6, [0, 0, 0])])}}, loop=False)
G["action.defend"] = anim(0.9, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-90, 10, 0]), (0.5, [-90, 10, 0]), (0.9, [0, 0, 0])])},
                               "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.15, [-90, -10, 0]), (0.5, [-90, -10, 0]), (0.9, [0, 0, 0])])},
                               "torso": {"rotation": kf([(0, [0, 0, 0]), (0.15, [10, 0, 0]), (0.9, [0, 0, 0])])}}, loop=False)
G["action.talk"] = anim(2.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-40, 20, 10]), (1.0, [-30, 25, 0]), (1.6, [-40, 20, 10]), (2.0, [0, 0, 0])])},
                             "head": {"rotation": wave(1.0, 3, 2)}}, loop=False)
G["action.pout"] = anim(2.5, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-60, 45, 0]), (2.2, [-60, 45, 0]), (2.5, [0, 0, 0])])},
                             "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-60, -45, 0]), (2.2, [-60, -45, 0]), (2.5, [0, 0, 0])])},
                             "head": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-8, -35, 0]), (2.2, [-8, -35, 0]), (2.5, [0, 0, 0])])}}, loop=False)
G["action.blow_kiss"] = anim(1.8, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.4, [-120, 40, 0]), (0.8, [-120, 40, 0]), (1.2, [-80, 0, -30]), (1.8, [0, 0, 0])])},
                                  "head": {"rotation": [0, 0, 8]}}, loop=False)
G["action.giggle"] = anim(1.8, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-125, 35, 0]), (1.5, [-125, 35, 0]), (1.8, [0, 0, 0])])},
                               "torso": {"rotation": wave(0.3, 4, 0)}, "head": {"rotation": [10, 0, 6]}}, loop=False)
G["action.wake_greeting"] = anim(3.0, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-160, 0, -20]), (1.0, [-160, 0, 5]), (1.5, [-160, 0, -20]), (2.0, [-160, 0, 5]), (3.0, [0, 0, 0])])},
                                      "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.5, [-40, 0, 0]), (3.0, [0, 0, 0])])}}, loop=False)
G["action.relieved"] = anim(2.5, {"torso": {"rotation": kf([(0, [0, 0, 0]), (0.6, [12, 0, 0]), (2.0, [12, 0, 0]), (2.5, [0, 0, 0])])},
                                 "head": {"rotation": kf([(0, [0, 0, 0]), (0.6, [15, 0, 0]), (2.0, [15, 0, 0]), (2.5, [0, 0, 0])])},
                                 "right_arm": {"rotation": [-50, 40, 0]}, "left_arm": {"rotation": [-10, 0, 0]}}, loop=False)
G["action.ask_help"] = anim(2.5, {"right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-80, 15, 0]), (2.2, [-80, 15, 0]), (2.5, [0, 0, 0])])},
                                 "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-80, -15, 0]), (2.2, [-80, -15, 0]), (2.5, [0, 0, 0])])},
                                 "head": {"rotation": [-6, 0, 0]}}, loop=False)
GF.update(G)

# ---- player animations (Bedrock convention, see README)
P = {}
P["hold_hands"] = anim(1.0, {"right_arm": {"rotation": [-12, 0, -16]}})
P["led_by_hand"] = anim(1.0, {"right_arm": {"rotation": [-55, -10, -10]}})
P["hug"] = anim(2.0, {"right_arm": {"rotation": wave(2.0, 4, 1, base=[-75, 26, 0])}, "left_arm": {"rotation": wave(2.0, 4, 1, base=[-75, -26, 0], phase=math.pi)},
                      "head": {"rotation": [12, 0, -10]}})
P["back_hugged"] = anim(2.0, {"right_arm": {"rotation": [-25, 15, 0]}, "left_arm": {"rotation": [-25, -15, 0]}, "head": {"rotation": [0, 25, 0]}})
P["cuddle"] = anim(3.0, {"right_arm": {"rotation": [-62, 36, 0]}, "left_arm": {"rotation": [-62, -36, 0]}, "head": {"rotation": wave(3.0, 3, 2, base=[18, 0, -6])}})
P["kiss"] = anim(2.7, {"right_arm": {"rotation": [-50, 32, 0]}, "left_arm": {"rotation": [-50, -32, 0]},
                       "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [22, 0, -8]), (2.2, [22, 0, -8]), (2.7, [0, 0, 0])])},
                       "body": {"rotation": [5, 0, 0]}}, loop="hold")
P["kiss_cheek_receive"] = anim(1.8, {"head": {"rotation": kf([(0, [0, 0, 0]), (0.4, [12, -25, 0]), (1.3, [12, -25, 0]), (1.8, [0, 0, 0])])}}, loop="hold")
P["forehead_kiss"] = anim(2.3, {"right_arm": {"rotation": [-70, 16, 0]}, "left_arm": {"rotation": [-70, -16, 0]},
                                "head": {"rotation": kf([(0, [0, 0, 0]), (0.5, [28, 0, 0]), (1.8, [28, 0, 0]), (2.3, [0, 0, 0])])}}, loop="hold")
P["lean_support"] = anim(3.0, {"right_arm": {"rotation": [-20, -10, -70]}, "head": {"rotation": [6, 0, 8]}})
P["sit_together"] = anim(4.0, {"right_arm": {"rotation": [-30, 10, 0]}, "left_arm": {"rotation": [-30, -10, 0]}})
P["arm_around"] = anim(4.0, {"right_arm": {"rotation": [-12, -15, -66]}, "left_arm": {"rotation": [-30, -10, 0]}, "head": {"rotation": [8, 0, 10]}})
P["cuddle_sitting"] = anim(4.0, {"right_arm": {"rotation": [-14, -15, -70]}, "left_arm": {"rotation": [-60, -38, 0]}, "head": {"rotation": [14, 10, 12]}})


def mirror(a):
    """Left/right mirror of a player clip (Bedrock: swap left/right bones, negate Y and Z rotations)."""
    def flip(v):
        if isinstance(v, list):
            return [v[0], -v[1], -v[2]]
        if isinstance(v, dict) and "post" in v:
            return dict(v, post=flip(v["post"]))
        return {t: flip(k) for t, k in v.items()}
    out = {k: v for k, v in a.items() if k != "bones"}
    out["bones"] = {}
    for bone, chans in a["bones"].items():
        name = bone.replace("right", "@").replace("left", "right").replace("@", "left")
        out["bones"][name] = {c: (flip(v) if c == "rotation" else v) for c, v in chans.items()}
    return out


# in-game review: the arm around her must be on her side; "_right" = she sits on the player's right
for _n in ("arm_around", "cuddle_sitting"):
    P[_n + "_right"] = P[_n]
    P[_n + "_left"] = mirror(P[_n])

P["carry"] = anim(3.0, {"right_arm": {"rotation": [-58, 18, 0]}, "left_arm": {"rotation": [-58, -18, 0]}, "body": {"rotation": [-4, 0, 0]}})
P["pet_hair"] = anim(1.6, {"right_arm": {"rotation": wave(1.6, 12, 1, base=[-130, 10, 0])}, "head": {"rotation": [18, 0, 0]}})
P["pet_cheek"] = anim(2.0, {"right_arm": {"rotation": wave(2.0, 5, 0, base=[-80, 22, 0])}, "head": {"rotation": [14, 0, -8]}})
P["lap_sit"] = anim(4.0, {"right_arm": {"rotation": [-45, 30, 0]}, "left_arm": {"rotation": [-50, -35, 0]}, "head": {"rotation": [14, 0, 0]}})
P["cuddle_bed"] = anim(4.0, {"root": {"rotation": [-90, 0, 0]}, "right_leg": {"rotation": [0, 0, 0]}, "left_leg": {"rotation": [0, 0, 0]},
                             "left_arm": {"rotation": [-20, 0, 70]}, "right_arm": {"rotation": [0, 0, -6]}, "head": {"rotation": [0, 0, -16]}})
P["cuddle_bed"]["root_pivot"] = [0, 12, 0]

# ----------------------------------------------------------------------------------------------- icons
# 16x16 pixel icons drawn from tiny ASCII maps. Index = ActionType ordinal (see ActionType.java); 32+ = status icons.
PAL = {
    "#": (40, 22, 40), "p": (255, 120, 170), "P": (220, 70, 130), "w": (255, 255, 255), "y": (255, 214, 90),
    "o": (240, 150, 60), "b": (110, 180, 255), "B": (60, 110, 200), "g": (110, 220, 140), "G": (50, 150, 90),
    "r": (230, 70, 70), "s": (255, 220, 200), "k": (150, 110, 80), "K": (100, 70, 50), "v": (180, 130, 255),
    "l": (200, 200, 210), "d": (120, 120, 130), "m": (250, 240, 170),
}

HEART = [
    "................",
    "..PPPP....PPPP..",
    ".PppppP..PppppP.",
    "PpwwpppPPppppppP",
    "PpwppppppppppppP",
    "PppppppppppppppP",
    "PppppppppppppppP",
    ".PppppppppppppP.",
    "..PppppppppppP..",
    "...PppppppppP...",
    "....PppppppP....",
    ".....PpppppP....",
    "......PpppP.....",
    ".......PpP......",
    "........P.......",
    "................",
]


def small_heart(x, y, size=1):
    rows = [".PP.PP.", "PppPppP", "PpppppP", ".PpppP.", "..PpP..", "...P..."]
    return [(x + cx, y + cy, ch) for cy, row in enumerate(rows) for cx, ch in enumerate(row) if ch != "."]


ICONS = {}


def icon(name, rows=None, pixels=None):
    ICONS[name] = (rows, pixels or [])


# command icons
icon("TALK", [
    "................", "..############..", ".#wwwwwwwwwwww#.", ".#ww##ww##ww#w#.", ".#wwwwwwwwwwww#.",
    ".#ww######wwww#.", ".#wwwwwwwwwwww#.", "..#####wwww###..", "......#ww#......", ".....#w#........",
    "....##..........", "................", "................", "................", "................", "................"])
icon("FOLLOW", [
    "................", "......####......", ".....#ssss#.....", ".....#ssss#.....", "......####......",
    ".....#pppp#.....", "....#pppppp#....", "....#pppppp#....", ".....#pppp#.....", ".....#bb#bb#....",
    ".....#bb.#bb#...", ".....#bb..#bb#..", "....###....###..", "................", ".yy.yy.yy.......", "................"])
icon("WAIT", [
    "................", ".....######.....", "....#wwwwww#....", "...#ww#wwwww#...", "..#www#wwwwww#..",
    "..#www#wwwwww#..", "..#www####www#..", "..#wwwwwwwwww#..", "..#wwwwwwwwww#..", "...#wwwwwwww#...",
    "....#wwwwww#....", ".....######.....", "................", "................", "................", "................"])
icon("SIT", [
    "................", "..##............", "..#k#...........", "..#k#...........", "..#k#...........",
    "..#k#...........", "..#k##########..", "..#kkkkkkkkkkk#.", "..############..", "..#k#......#k#..",
    "..#k#......#k#..", "..#k#......#k#..", "..###......###..", "................", "................", "................"])
icon("STAND_UP", [
    "................", "......###.......", ".....#sss#......", ".....#sss#......", "......###.......",
    ".....#ppp#......", "....#ppppp#.....", "....#ppppp#.....", ".....#ppp#......", ".....#b#b#......",
    ".....#b#b#..g...", ".....#b#b#.ggg..", ".....##.##ggggg.", "...........ggg..", "...........ggg..", "................"])
icon("STAY_SAFE", [
    "................", "...##########...", "..#bbbbbbbbbb#..", "..#bBBBBBBBBb#..", "..#bBwwBBBBBb#..",
    "..#bBwBBBBBBb#..", "..#bBBBBBBBBb#..", "..#bBBBBBBBBb#..", "...#bBBBBBBb#...", "...#bBBBBBBb#...",
    "....#bBBBBb#....", ".....#bBBb#.....", "......#bb#......", ".......##.......", "................", "................"])
icon("RETURN_HOME", [
    "................", ".......##.......", "......#rr#......", ".....#rrrr#.....", "....#rrrrrr#....",
    "...#rrrrrrrr#...", "..############..", "...#kkkkkkkk#...", "...#kyykkyyk#...", "...#kyykkyyk#...",
    "...#kkk##kkk#...", "...#kkk#Kkkk#...", "...#kkk#Kkkk#...", "...##########...", "................", "................"])
icon("SET_HOME", [
    "................", ".......##.......", "......#rr#......", ".....#rrrr#.....", "....#rrrrrr#....",
    "...#rrrrrrrr#...", "..############..", "...#kkkkkkkk#...", "...#kkkkkkkk#...", "...#kkk##kkk#...",
    "...#kkk#Kkkk#.g.", "...#kkk#Kkkkgggg", "...########..g..", ".............g..", "................", "................"])
icon("STATUS", [
    "................", "..##########....", "..#wwwwwwww#....", "..#w######w#....", "..#wwwwwwww#....",
    "..#w####www#....", "..#wwwwwwww#....", "..#w######w#....", "..#wwwwwwww#....", "..#w###wwww#....",
    "..#wwwwwwww#....", "..##########....", "................", "................", "................", "................"],
     small_heart(8, 8))
# affection icons
icon("HUG", None, small_heart(4, 1) + [(x, y, "#") for x in range(2, 14) for y in (9,)] +
     [(x, 10, "p") for x in range(3, 13)] + [(2, 10, "#"), (13, 10, "#")] + [(x, 11, "#") for x in range(2, 14)] +
     [(1, 8, "s"), (14, 8, "s"), (1, 9, "s"), (14, 9, "s")])
icon("CUDDLE", None, small_heart(1, 3) + small_heart(8, 6))
icon("KISS", [
    "................", "................", "................", "................", "....rr....rr....",
    "...rPPr..rPPr...", "..rPPPPrrPPPPr..", ".rPPPPPPPPPPPPr.", ".rrrrrrrrrrrrrr.", ".rPPPPPPPPPPPPr.",
    "..rPPPPPPPPPPr..", "...rPPPPPPPPr...", "....rrrrrrrr....", "................", "................", "................"])
icon("KISS_CHEEK", [
    "................", ".....######.....", "....#ssssss#....", "...#ssssssss#...", "...#s##ss##s#...",
    "...#ssssssss#...", "...#ppssssss#...", "...#ppsss#ss#...", "...#ssssssss#...", "....#ssssss#....",
    ".....######.....", "................", "................", "................", "................", "................"],
     [(11, 6, "r"), (12, 6, "r"), (11, 7, "r"), (12, 7, "r"), (13, 5, "p"), (14, 4, "p")])
icon("FOREHEAD_KISS", [
    "................", "................", ".....######.....", "....#ssssss#....", "...#ssssssss#...",
    "...#s##ss##s#...", "...#ssssssss#...", "...#ssssssss#...", "...#sss##sss#...", "....#ssssss#....",
    ".....######.....", "................", "................", "................", "................", "................"],
     [(7, 1, "r"), (8, 1, "r"), (7, 2, "r"), (8, 2, "r")])
icon("BACK_HUG", None, small_heart(5, 0) + [(x, 8, "p") for x in range(3, 13)] + [(3, 9, "p"), (12, 9, "p"), (3, 10, "p"), (12, 10, "p")] +
     [(x, 12, "#") for x in range(4, 12)] + [(2, 8, "s"), (13, 8, "s")])
icon("HOLD_HANDS", [
    "................", "................", "................", "...ss......ss...", "...ss......ss...",
    "...sss....sss...", "....ssssssss....", ".....ssssss.....", "......ssss......", "......#..#......",
    "................", "................", "................", "................", "................", "................"],
     small_heart(5, 10))
icon("LET_GO", [
    "................", "................", "..ss........ss..", "..ss........ss..", "..sss......sss..",
    "...ssss..ssss...", "................", "................", "................", "................",
    "................", "................", "................", "................", "................", "................"],
     [(x, 9, "d") for x in range(4, 12)])
icon("LEAN", None, [(x, y, "s") for x in range(4, 7) for y in range(2, 5)] + [(x, y, "p") for x in range(4, 7) for y in range(5, 13)] +
     [(x, y, "s") for x in range(8, 11) for y in range(3, 6)] + [(x, y, "b") for x in range(8, 11) for y in range(6, 13)] +
     [(7, 4, "p"), (7, 5, "p")])
icon("SIT_TOGETHER", [
    "................", "...ss.....ss....", "...ss.....ss....", "..pppp...bbbb...", "..pppp...bbbb...",
    "..pppp...bbbb...", "..############..", "..#kkkkkkkkkk#..", "..############..", "..#k#......#k#..",
    "..#k#......#k#..", "..###......###..", "................", "................", "................", "................"])
icon("HEAD_ON_SHOULDER", [
    "................", "................", ".....ss.ss......", "....sss.ss......", "....ss..bbbb....",
    "...pppp.bbbb....", "...ppppbbbbb....", "...ppppbbbbb....", "...pppp.bbbb....", "..############..",
    "..#kkkkkkkkkk#..", "..############..", "................", "................", "................", "................"],
     small_heart(9, 0))
icon("CUDDLE_SITTING", None, small_heart(4, 0) + [(x, 9, "#") for x in range(2, 14)] + [(x, 10, "k") for x in range(2, 14)] +
     [(x, 11, "#") for x in range(2, 14)] + [(x, y, "p") for x in range(4, 8) for y in range(6, 9)] + [(x, y, "b") for x in range(8, 12) for y in range(6, 9)])
icon("PICK_UP", None, [(x, y, "s") for x in range(6, 10) for y in range(1, 4)] + [(x, y, "b") for x in range(5, 11) for y in range(4, 11)] +
     [(x, y, "p") for x in range(2, 14) for y in range(6, 8)] + [(x, 5, "w") for x in (3, 4, 11, 12)])
icon("PUT_DOWN", None, [(x, y, "p") for x in range(6, 10) for y in range(6, 12)] + [(x, y, "s") for x in range(6, 10) for y in range(3, 6)] +
     [(x, 13, "G") for x in range(2, 14)] + [(7, 1, "d"), (8, 1, "d"), (6, 2, "d"), (9, 2, "d")])
icon("GIVE_GIFT", [
    "................", "......P..P......", ".....P.PP.P.....", "......PPPP......", "..############..",
    "..#rrrrPPrrrr#..", "..############..", "...#rrrPPrrr#...", "...#rrrPPrrr#...", "...#rrrPPrrr#...",
    "...#rrrPPrrr#...", "...#rrrPPrrr#...", "...##########...", "................", "................", "................"])
icon("FEED", [
    "................", "........K.......", ".......Kg.......", "......#gg.......", "....######......",
    "...#rrrrrr#.....", "..#rwrrrrrr#....", "..#rwrrrrrr#....", "..#rrrrrrrr#....", "..#rrrrrrrr#....",
    "...#rrrrrr#.....", "....######......", "................", "................", "................", "................"])
icon("SLEEP_TOGETHER", [
    "................", "...........mmm..", "..........mm....", "..........mm....", "...........mmm..",
    "................", "..ss.ss.........", "..##############", "..#wwwwprrrrrrr#", "..#wwwwprrrrrrr#",
    "..##############", "..#............#", "................", "................", "................", "................"])
icon("CUDDLE_BED", None, small_heart(5, 1) + [(x, 8, "#") for x in range(1, 15)] + [(x, 9, "w") for x in range(1, 5)] +
     [(x, 9, "r") for x in range(5, 15)] + [(x, 10, "r") for x in range(1, 15)] + [(x, 11, "#") for x in range(1, 15)] + [(1, 12, "#"), (14, 12, "#")])
icon("STOP", [
    "................", ".....######.....", "....#rrrrrr#....", "...#rrrrrrrr#...", "..#rrrrrrrrrr#..",
    "..#rwwwwwwwwr#..", "..#rwwwwwwwwr#..", "..#rrrrrrrrrr#..", "...#rrrrrrrr#...", "....#rrrrrr#....",
    ".....######.....", "................", "................", "................", "................", "................"])
icon("PET_HAIR", None, [(x, y, "k") for x in range(4, 12) for y in range(8, 14)] + [(x, y, "s") for x in range(5, 11) for y in range(10, 14)] +
     [(x, y, "s") for x in range(5, 12) for y in range(2, 5)] + [(4, 5, "s"), (11, 5, "s"), (12, 4, "s")] + [(2, 1, "p"), (13, 1, "p"), (1, 2, "p"), (14, 2, "p")])
icon("PET_CHEEK", ICONS["KISS_CHEEK"][0], [(12, 5, "s"), (13, 5, "s"), (12, 6, "s"), (13, 6, "s"), (13, 7, "s"), (14, 4, "s"), (14, 5, "s")] + small_heart(0, 0))
icon("LAP_SIT", None, small_heart(9, 0) + [(x, y, "b") for x in range(3, 8) for y in range(5, 10)] + [(x, y, "s") for x in range(4, 7) for y in range(2, 5)] +
     [(x, 10, "b") for x in range(3, 13)] + [(12, y, "b") for y in range(11, 14)] + [(x, y, "p") for x in range(6, 11) for y in range(6, 9)] +
     [(x, y, "s") for x in range(9, 12) for y in range(3, 6)] + [(x, 14, "#") for x in range(1, 15)])
icon("GET_UP", ICONS["STAND_UP"][0])
# status icons (index 32+)
STATUS_ICONS = ["HEART", "FOOD", "ENERGY", "ATTENTION", "TRUST", "HEALTH", "MOON", "REQUEST", "MEMORY", "HOME"]
icon("HEART", HEART)
icon("FOOD", ICONS["FEED"][0])
icon("ENERGY", [
    "................", "........yy......", ".......yy.......", "......yy........", ".....yy.........",
    "....yyyyyy......", ".......yy.......", "......yy........", ".....yy.........", "....yy..........",
    "...y............", "................", "................", "................", "................", "................"])
icon("ATTENTION", None, [(7, 1, "v"), (7, 2, "v"), (7, 3, "v"), (1, 7, "v"), (2, 7, "v"), (3, 7, "v"), (11, 7, "v"), (12, 7, "v"), (13, 7, "v"),
                          (7, 11, "v"), (7, 12, "v"), (7, 13, "v"), (3, 3, "v"), (11, 3, "v"), (3, 11, "v"), (11, 11, "v")] + small_heart(4, 5))
icon("TRUST", ICONS["STAY_SAFE"][0])
icon("HEALTH", [
    "................", "......####......", "......#rr#......", "......#rr#......", "..####.rr.####..",
    "..#rrrrrrrrrr#..", "..#rrrrrrrrrr#..", "..####.rr.####..", "......#rr#......", "......#rr#......",
    "......####......", "................", "................", "................", "................", "................"])
icon("MOON", [
    "................", "......vvvv......", "....vvv.........", "...vvv..........", "...vv...........",
    "..vvv...........", "..vvv...........", "..vvv...........", "...vv...........", "...vvv..........",
    "....vvv....v....", "......vvvvv.....", "................", "................", "................", "................"])
icon("REQUEST", [
    "................", "......####......", ".....#yyyy#.....", ".....#yyyy#.....", ".....#yyyy#.....",
    ".....#yyyy#.....", "......#yy#......", "......#yy#......", "......#yy#......", "................",
    "......####......", "......#yy#......", "......####......", "................", "................", "................"])
icon("MEMORY", [
    "................", "...##########...", "...#mmmmmmmm#...", "...#m######m#...", "...#mmmmmmmm#...",
    "...#m####mmm#...", "...#mmmmmmmm#...", "...#m######m#...", "...#mmmmmmmm#...", "...##########...",
    "................", "................", "................", "................", "................", "................"])
icon("HOME", ICONS["RETURN_HOME"][0])

ACTION_ORDER = ["TALK", "FOLLOW", "WAIT", "SIT", "STAND_UP", "STAY_SAFE", "RETURN_HOME", "SET_HOME", "STATUS",
                "HUG", "CUDDLE", "KISS", "KISS_CHEEK", "FOREHEAD_KISS", "BACK_HUG", "HOLD_HANDS", "LET_GO", "LEAN",
                "SIT_TOGETHER", "HEAD_ON_SHOULDER", "CUDDLE_SITTING", "PICK_UP", "PUT_DOWN", "GIVE_GIFT", "FEED",
                "SLEEP_TOGETHER", "CUDDLE_BED", "STOP", "PET_HAIR", "PET_CHEEK", "LAP_SIT", "GET_UP"]


def render_icon(img, index, name):
    rows, pixels = ICONS[name]
    ox, oy = (index % 16) * 16, (index // 16) * 16
    px = img.load()
    if rows:
        for y, row in enumerate(rows[:16]):
            for x, ch in enumerate(row[:16]):
                if ch in PAL:
                    px[ox + x, oy + y] = PAL[ch] + (255,)
    for x, y, ch in pixels:
        if 0 <= x < 16 and 0 <= y < 16 and ch in PAL:
            px[ox + x, oy + y] = PAL[ch] + (255,)


def main():
    # The companion's model, outfits, textures and animations now come from art/character (see build_animations.py
    # and build_faces.py). This script only produces the player animations, GUI icons and the logo.
    player = {"format_version": "1.8.0", "animations": {"animation.player." + k: v for k, v in P.items()}}
    with open(out("player_animations/player.animation.json"), "w") as f:
        json.dump(player, f, indent=1)
    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for i, name in enumerate(ACTION_ORDER):
        render_icon(atlas, i, name)
    for i, name in enumerate(STATUS_ICONS):
        render_icon(atlas, 32 + i, name)
    atlas.save(out("textures/gui/icons.png"))
    logo = Image.new("RGBA", (128, 128), (40, 22, 40, 255))
    d = ImageDraw.Draw(logo)
    d.rounded_rectangle([4, 4, 123, 123], radius=18, outline=(255, 140, 190, 255), width=4)
    heart = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    render_icon(heart, 0, "HEART")
    logo.alpha_composite(heart.crop((0, 0, 16, 16)).resize((80, 80), Image.NEAREST), (24, 24))
    logo.save(os.path.join(ROOT, "logo.png"))
    print("player animations, icons and logo written:", len(P), "player animations")


if __name__ == "__main__":
    main()
