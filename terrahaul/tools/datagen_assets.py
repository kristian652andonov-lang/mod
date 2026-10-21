#!/usr/bin/env python3
"""Generates Terrahaul's block/item assets and block data: blockstates, models, item models,
procedural pixel textures, language entries, loot tables, tags, recipes and data maps.

Usage: python3 tools/datagen_assets.py
Everything is written to src/main/resources and committed. Textures are drawn procedurally with a
fixed random seed (they are original artwork, not copies of vanilla textures); most models instead
reference vanilla textures (stone, logs, leaves) so resource packs restyle them automatically.
"""
import copy
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'terrahaul')
DATA = os.path.join(ROOT, 'data')

STONES = {
    # name: (texture, pebble/rock texture for tops)
    "stone": "minecraft:block/stone",
    "mossy": "minecraft:block/mossy_cobblestone",
    "andesite": "minecraft:block/andesite",
    "granite": "minecraft:block/granite",
    "diorite": "minecraft:block/diorite",
    "deepslate": "minecraft:block/deepslate",
    "tuff": "minecraft:block/tuff",
    "sandstone": "minecraft:block/sandstone_top",
    "red_sandstone": "minecraft:block/red_sandstone_top",
    "blackstone": "minecraft:block/blackstone",
}
STONE_RECIPES = {
    "stone": "minecraft:cobblestone", "mossy": "minecraft:mossy_cobblestone", "andesite": "minecraft:andesite",
    "granite": "minecraft:granite", "diorite": "minecraft:diorite", "deepslate": "minecraft:cobbled_deepslate",
    "tuff": "minecraft:tuff", "sandstone": "minecraft:sandstone", "red_sandstone": "minecraft:red_sandstone",
    "blackstone": "minecraft:blackstone",
}
WOODS = ["oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "mangrove", "cherry"]
LITTERS = {
    # name: (texture, tinted)
    "oak_leaf_litter": ("terrahaul:block/leaf_litter", True),
    "birch_leaf_litter": ("terrahaul:block/leaf_litter", True),
    "spruce_needle_litter": ("terrahaul:block/needle_litter", True),
    "jungle_leaf_litter": ("terrahaul:block/leaf_litter_large", True),
    "acacia_leaf_litter": ("terrahaul:block/leaf_litter_small", True),
    "dark_oak_leaf_litter": ("terrahaul:block/leaf_litter_large", True),
    "mangrove_leaf_litter": ("terrahaul:block/leaf_litter", True),
    "azalea_leaf_litter": ("terrahaul:block/azalea_leaf_litter", False),
    "dry_leaf_litter": ("terrahaul:block/dry_leaf_litter", False),
}
SHRUBS = {
    "leafy_bush": ("minecraft:block/oak_leaves", (3, 0, 3, 13, 9, 13), 12),
    "dense_shrub": ("minecraft:block/dark_oak_leaves", (1, 0, 1, 15, 12, 15), 15),
    "alpine_shrub": ("minecraft:block/spruce_leaves", (2, 0, 2, 14, 6, 14), 8),
}
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(base, path, obj):
    full = os.path.join(base, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def model(name, obj):
    # Block model faces may only name a texture through a '#variable' of the "textures" map; a
    # direct resource location silently resolves to the missing texture. Rewrite direct references
    # into variables so the element builders above can stay simple. Element dicts are shared
    # between models, so work on a copy.
    obj = copy.deepcopy(obj)
    textures = obj.setdefault("textures", {})
    variables = {v: k for k, v in textures.items() if not v.startswith('#')}
    for element in obj.get("elements", []):
        for face in element["faces"].values():
            tex = face["texture"]
            if tex.startswith('#'):
                continue
            if tex not in variables:
                var = tex.split('/')[-1]
                while var in textures:
                    var += '_'
                textures[var] = tex
                variables[tex] = var
            face["texture"] = '#' + variables[tex]
    write_json(ASSETS, f'models/block/{name}.json', obj)


def item_model(name, parent):
    write_json(ASSETS, f'models/item/{name}.json', {"parent": parent})


def blockstate(name, variants):
    write_json(ASSETS, f'blockstates/{name}.json', {"variants": variants})


def face_all(tex, uv=None, tint=None, cull=None):
    faces = {}
    for d in ["north", "south", "east", "west", "up"]:
        f = {"texture": tex}
        if uv:
            f["uv"] = uv
        if tint is not None:
            f["tintindex"] = tint
        faces[d] = f
    return faces


def box(fr, to, tex, rotation=None, tint=None, faces=None, shade=True):
    el = {"from": list(fr), "to": list(to), "faces": faces or face_all(tex, tint=tint)}
    if rotation:
        el["rotation"] = rotation
    if not shade:
        el["shade"] = False
    return el


# ------------------------------------------------------------------------------------------------
# textures
# ------------------------------------------------------------------------------------------------

def save_texture(img, path):
    full = os.path.join(ASSETS, 'textures', path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def leaf_shape(img, cx, cy, length, angle, shade_fn, rnd, width=1.0):
    """Draws one small leaf (a pointed ellipse) into an RGBA image."""
    w, h = img.size
    for py in range(h):
        for px in range(w):
            # distance in the leaf's local frame, wrapped so leaves tile
            for ox in (-w, 0, w):
                for oy in (-h, 0, h):
                    dx = px + ox - cx
                    dy = py + oy - cy
                    u = dx * math.cos(angle) + dy * math.sin(angle)
                    v = -dx * math.sin(angle) + dy * math.cos(angle)
                    half = length / 2.0
                    if abs(u) > half:
                        continue
                    thickness = width * (1.0 - (u / half) ** 2) * (0.55 + 0.45 * (1 - abs(u) / half))
                    if abs(v) <= thickness:
                        img.putpixel((px, py), shade_fn(u / half, v / max(thickness, 0.01), rnd))


def litter_texture(seed, count, length_range, palette_fn, width=1.0):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for _ in range(count):
        leaf_shape(img, rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(*length_range), rnd.uniform(0, math.pi),
                   palette_fn, rnd, width)
    return img


def gray_palette(u, v, rnd):
    base = 150 + int(70 * (1 - abs(v))) - rnd.randint(0, 25)
    if abs(v) < 0.25:  # midrib
        base -= 30
    return (base, base, base, 255)


def dry_palette_factory(seed):
    rnd0 = random.Random(seed)
    colours = [(178, 98, 38), (196, 132, 46), (150, 70, 34), (205, 160, 70), (120, 82, 50)]

    def fn(u, v, rnd):
        c = colours[rnd0.randrange(len(colours))]
        k = 0.8 + 0.2 * (1 - abs(v))
        return (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
    return fn


def azalea_palette(u, v, rnd):
    if rnd.random() < 0.25:
        return (214, 120, 196, 255)
    g = 100 + int(50 * (1 - abs(v)))
    return (int(g * 0.55), g, int(g * 0.35), 255)


def needle_texture(seed):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for _ in range(42):
        x, y = rnd.uniform(0, 16), rnd.uniform(0, 16)
        a = rnd.uniform(0, math.pi)
        length = rnd.uniform(3, 6)
        shade = rnd.randint(120, 210)
        for t in range(int(length * 3)):
            px = int(x + math.cos(a) * t / 3) % 16
            py = int(y + math.sin(a) * t / 3) % 16
            img.putpixel((px, py), (shade, shade, shade, 255))
    return img


def pine_cone_texture():
    rnd = random.Random(7)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            # offset rows of scales
            row = y // 2
            col = (x + (row % 2) * 2) // 4
            local = ((x + (row % 2) * 2) % 4, y % 2)
            base = (112, 72, 40) if (row + col) % 2 else (96, 60, 34)
            if local[1] == 1:
                base = (70, 44, 26)
            if local[0] == 0:
                base = tuple(int(c * 0.85) for c in base)
            n = rnd.randint(-8, 8)
            img.putpixel((x, y), (base[0] + n, base[1] + n, base[2] + n, 255))
    return img


def dry_shrub_texture():
    rnd = random.Random(11)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    def stroke(x, y, a, length, colour):
        for t in range(int(length * 2)):
            px = int(round(x + math.cos(a) * t / 2))
            py = int(round(y - math.sin(a) * t / 2))
            if 0 <= px < 16 and 0 <= py < 16:
                img.putpixel((px, py), colour)
        return x + math.cos(a) * length, y - math.sin(a) * length

    stems = [(7.5, 15.5, math.pi / 2 + d) for d in (-0.6, -0.25, 0.1, 0.45, 0.8)]
    for (x, y, a) in stems:
        ex, ey = stroke(x, y, a, rnd.uniform(6, 10), (120, 86, 52, 255))
        for _ in range(3):
            stroke(ex, ey, a + rnd.uniform(-0.9, 0.9), rnd.uniform(2, 4), (150, 112, 70, 255))
        # a few dry leaf tips
        for _ in range(3):
            px, py = int(ex + rnd.uniform(-2, 2)), int(ey + rnd.uniform(-2, 2))
            if 0 <= px < 16 and 0 <= py < 16:
                img.putpixel((px, py), (176, 140, 82, 255))
    return img


def particle_textures():
    """Falling-leaf, needle, petal and dust sprites. Leaf, needle and dust sprites are grey so the
    particle can tint them with the colour of the leaves or ground they come from."""
    rnd = random.Random(21)

    def leaf_sprite(path, length, width, angle, lobes, tip_power):
        size = 16
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        ca, sa = math.cos(angle), math.sin(angle)
        cx, cy = 7.5, 7.5
        for y in range(size):
            for x in range(size):
                # leaf space: u along the midrib (-1 stem end .. 1 tip), v across
                dx, dy = x - cx, y - cy
                u = (dx * ca + dy * sa) / (length / 2)
                v = (-dx * sa + dy * ca) / (width / 2)
                if u < -1.0 or u > 1.0:
                    continue
                t = (u + 1) / 2
                half = math.sin(math.pi * t) ** tip_power * (1.0 if t < 0.5 else (1 - t) * 2 + 0.25 * math.sin(math.pi * t))
                if lobes:
                    half *= 0.78 + 0.22 * abs(math.sin(t * math.pi * lobes))
                if abs(v) > half:
                    continue
                edge = abs(v) / max(half, 1e-3)
                b = 222 - int(48 * edge * edge) - int(10 * t)
                if abs(v) < 0.16:
                    b -= 34  # midrib
                b += rnd.randint(-6, 6)
                img.putpixel((x, y), (b, b, b, 255))
        # stem
        for k in range(3):
            sx = round(cx - ca * (length / 2 + k * 0.9))
            sy = round(cy - sa * (length / 2 + k * 0.9))
            if 0 <= sx < size and 0 <= sy < size:
                img.putpixel((sx, sy), (150, 150, 150, 255))
        save_texture(img, path)

    leaf_sprite('particle/falling_leaf_0.png', 12.0, 7.5, 0.7, 0, 0.75)    # oval
    leaf_sprite('particle/falling_leaf_1.png', 13.0, 5.0, 2.3, 0, 0.9)     # slender
    leaf_sprite('particle/falling_leaf_2.png', 11.0, 9.0, -0.4, 0, 0.6)    # broad
    leaf_sprite('particle/falling_leaf_3.png', 12.5, 8.5, 1.9, 3, 0.7)     # lobed (oak)

    for i in range(3):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        rx, ry = 4.6 + i * 0.5, 3.4 + i * 0.4
        for y in range(16):
            for x in range(16):
                dx, dy = (x - 7.5) / rx, (y - 7.5) / ry
                # notched heart-like petal
                notch = dy < -0.55 and abs(dx) < 0.18
                if dx * dx + dy * dy <= 1.0 and not notch:
                    d = math.sqrt(dx * dx + dy * dy)
                    img.putpixel((x, y), (255, 196 - int(46 * d) + rnd.randint(-5, 5), 222 - int(24 * d), 255))
        save_texture(img, f'particle/petal_{i}.png')

    for i in range(2):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        count = 1 + i * 2
        for n in range(count):
            a = 0.8 + (n - (count - 1) / 2) * 0.45
            bend = rnd.uniform(-0.03, 0.03)
            for k in range(12):
                x = 3 + math.cos(a + bend * k) * k
                y = 3 + math.sin(a + bend * k) * k
                xi, yi = round(x), round(y)
                if 0 <= xi < 16 and 0 <= yi < 16:
                    b = 205 - k * 3
                    img.putpixel((xi, yi), (b, b, b, 255))
        save_texture(img, f'particle/needle_{i}.png')

    for i in range(3):
        img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
        for _ in range(4 + i * 2):
            x, y = rnd.randint(1, 6), rnd.randint(1, 6)
            a = rnd.randint(90, 200)
            img.putpixel((x, y), (236, 236, 236, a))
        save_texture(img, f'particle/dust_{i}.png')


def textures():
    save_texture(litter_texture(1, 15, (4.0, 6.0), gray_palette, 2.0), 'block/leaf_litter.png')
    save_texture(litter_texture(2, 9, (5.5, 8.0), gray_palette, 2.6), 'block/leaf_litter_large.png')
    save_texture(litter_texture(3, 24, (2.5, 4.0), gray_palette, 1.5), 'block/leaf_litter_small.png')
    save_texture(needle_texture(4), 'block/needle_litter.png')
    save_texture(litter_texture(5, 16, (4.0, 6.5), dry_palette_factory(5), 2.1), 'block/dry_leaf_litter.png')
    save_texture(litter_texture(6, 16, (3.5, 5.5), azalea_palette, 2.0), 'block/azalea_leaf_litter.png')
    save_texture(pine_cone_texture(), 'block/pine_cone.png')
    save_texture(dry_shrub_texture(), 'block/dry_shrub.png')
    particle_textures()


# ------------------------------------------------------------------------------------------------
# models and blockstates
# ------------------------------------------------------------------------------------------------

PEBBLES = [  # (cx, cz, w, d, h)
    (8.0, 8.0, 4, 3, 2.0),
    (4.0, 11.5, 3, 3, 1.5),
    (11.5, 4.0, 3, 2, 1.5),
    (12.0, 12.0, 2, 3, 1.0),
]


def pebble_elements(count, tex):
    els = []
    for i in range(count):
        cx, cz, w, d, h = PEBBLES[i]
        x0, z0 = cx - w / 2, cz - d / 2
        uv0 = (i * 4) % 12
        faces = {}
        for side in ["north", "south", "east", "west"]:
            faces[side] = {"texture": tex, "uv": [uv0, 4, uv0 + max(w, d), 4 + h]}
        faces["up"] = {"texture": tex, "uv": [uv0, 8, uv0 + w, 8 + d]}
        els.append({"from": [x0, 0, z0], "to": [x0 + w, h, z0 + d], "faces": faces})
        # rounded cap
        if w > 2 and d > 2:
            cap = {"north": {"texture": tex, "uv": [uv0, 0, uv0 + w - 1, 0.5]},
                   "south": {"texture": tex, "uv": [uv0, 0, uv0 + w - 1, 0.5]},
                   "east": {"texture": tex, "uv": [uv0, 0, uv0 + d - 1, 0.5]},
                   "west": {"texture": tex, "uv": [uv0, 0, uv0 + d - 1, 0.5]},
                   "up": {"texture": tex, "uv": [uv0, 12, uv0 + w - 1, 12 + d - 1]}}
            els.append({"from": [x0 + 0.5, h, z0 + 0.5], "to": [x0 + w - 0.5, h + 0.5, z0 + d - 0.5], "faces": cap})
    return els


ROCKS = [
    [(3, 0, 3, 13, 6, 12), (5, 6, 5, 11, 8, 10), (2, 0, 7, 6, 4, 13), (10, 0, 2, 14, 3, 7)],
    [(1, 0, 4, 13, 7, 12), (3, 7, 5, 10, 9, 11), (10, 0, 3, 15, 5, 9), (4, 0, 2, 8, 3, 5)],
]


def rock_elements(variant, tex):
    els = []
    for i, (x0, y0, z0, x1, y1, z1) in enumerate(ROCKS[variant]):
        faces = {}
        for side in ["north", "south"]:
            faces[side] = {"texture": tex, "uv": [x0, 16 - y1, x1, 16 - y0]}
        for side in ["east", "west"]:
            faces[side] = {"texture": tex, "uv": [z0, 16 - y1, z1, 16 - y0]}
        faces["up"] = {"texture": tex, "uv": [x0, z0, x1, z1]}
        els.append({"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces})
    return els


def stick(fr, to, side, end, rotation=None):
    faces = {
        "north": {"texture": end, "uv": [6, 6, 8, 8]}, "south": {"texture": end, "uv": [6, 6, 8, 8]},
        "east": {"texture": side, "uv": [0, 4, 16, 6]}, "west": {"texture": side, "uv": [0, 8, 16, 10]},
        "up": {"texture": side, "uv": [0, 0, 16, 2], "rotation": 90}, "down": {"texture": side, "uv": [0, 12, 16, 14], "rotation": 90},
    }
    # sticks are modelled along the x axis: their ends face east/west
    if to[0] - fr[0] >= to[2] - fr[2]:
        faces = {
            "east": {"texture": end, "uv": [6, 6, 8, 8]}, "west": {"texture": end, "uv": [6, 6, 8, 8]},
            "north": {"texture": side, "uv": [0, 4, 16, 6]}, "south": {"texture": side, "uv": [0, 8, 16, 10]},
            "up": {"texture": side, "uv": [0, 0, 16, 2]}, "down": {"texture": side, "uv": [0, 12, 16, 14]},
        }
    el = {"from": list(fr), "to": list(to), "faces": faces}
    if rotation is not None:
        el["rotation"] = {"origin": [8, 1, 8], "axis": "y", "angle": rotation}
    return el


BRANCH_SHAPES = [
    [((1, 0, 7), (15, 2, 9), None), ((5, 0, 9), (10, 1, 10), 22.5)],
    [((2, 0, 7), (13, 2, 9), None), ((8, 0.5, 4), (15, 1.5, 5), 45), ((4, 0, 9), (8, 1, 10), -22.5)],
    [((1, 0, 5), (9, 2, 7), -22.5), ((7, 0, 8), (15, 2, 10), 22.5), ((10, 0.5, 4), (14, 1.5, 5), 0)],
]


def branch_elements(shape, side, end, mossy):
    els = [stick(fr, to, side, end, rot) for fr, to, rot in BRANCH_SHAPES[shape]]
    if mossy:
        fr, to, rot = BRANCH_SHAPES[shape][0]
        moss = {"from": [fr[0] + 1, to[1], fr[2]], "to": [to[0] - 2, to[1] + 0.3, to[2]],
                "faces": {"up": {"texture": "minecraft:block/moss_block", "uv": [0, 0, 10, 2]},
                          "north": {"texture": "minecraft:block/moss_block", "uv": [0, 0, 10, 0.3]},
                          "south": {"texture": "minecraft:block/moss_block", "uv": [0, 0, 10, 0.3]}}}
        if rot is not None:
            moss["rotation"] = {"origin": [8, 1, 8], "axis": "y", "angle": rot}
        els.append(moss)
    return els


def facing_variants(model_fn, extra_props):
    """Builds blockstate variants for every facing (rotating the model) and the extra properties."""
    variants = {}

    def rec(props, keys):
        if not keys:
            for facing, y in FACING_Y.items():
                key = ",".join([f"facing={facing}"] + [f"{k}={v}" for k, v in props])
                v = {"model": model_fn(dict(props))}
                if y:
                    v["y"] = y
                variants[key] = v
            return
        k, values = keys[0]
        for value in values:
            rec(props + [(k, value)], keys[1:])
    rec([], extra_props)
    return variants


def lang_name(name):
    return " ".join(w.capitalize() for w in name.replace("_", " ").split())


def models_and_states(lang):
    for stone, tex in STONES.items():
        for n in range(1, 5):
            model(f"{stone}_pebbles_{n}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                          "textures": {"particle": tex}, "elements": pebble_elements(n, tex)})
        blockstate(f"{stone}_pebbles", facing_variants(lambda p, s=stone: f"terrahaul:block/{s}_pebbles_{p['count']}",
                                                       [("count", [1, 2, 3, 4]), ("waterlogged", ["false", "true"])]))
        item_model(f"{stone}_pebbles", f"terrahaul:block/{stone}_pebbles_3")
        lang[f"block.terrahaul.{stone}_pebbles"] = lang_name(("mossy stone" if stone == "mossy" else stone) + " pebbles")
        for v in range(2):
            model(f"{stone}_rock_{v}", {"parent": "minecraft:block/block", "textures": {"particle": tex},
                                       "elements": rock_elements(v, tex)})
        blockstate(f"{stone}_rock", facing_variants(lambda p, s=stone: f"terrahaul:block/{s}_rock_{p['variant']}",
                                                    [("variant", [0, 1]), ("waterlogged", ["false", "true"])]))
        item_model(f"{stone}_rock", f"terrahaul:block/{stone}_rock_0")
        lang[f"block.terrahaul.{stone}_rock"] = lang_name(("mossy stone" if stone == "mossy" else stone) + " rock")

    for wood in WOODS:
        side = f"minecraft:block/{wood}_log"
        end = f"minecraft:block/{wood}_log_top"
        for shape in range(3):
            for mossy in (False, True):
                suffix = "_mossy" if mossy else ""
                model(f"{wood}_branch_{shape}{suffix}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                         "textures": {"particle": side}, "elements": branch_elements(shape, side, end, mossy)})
        blockstate(f"{wood}_branch", facing_variants(
            lambda p, w=wood: f"terrahaul:block/{w}_branch_{p['shape']}{'_mossy' if p['mossy'] == 'true' else ''}",
            [("mossy", ["false", "true"]), ("shape", [0, 1, 2]), ("waterlogged", ["false", "true"])]))
        item_model(f"{wood}_branch", f"terrahaul:block/{wood}_branch_1")
        lang[f"block.terrahaul.{wood}_branch"] = lang_name(f"{wood} branch")

    # twigs: a scatter of thin sticks of two woods
    twig_side = "minecraft:block/oak_log"
    twig_side2 = "minecraft:block/spruce_log"
    twig_els = [
        stick((2, 0, 7.5), (14, 1, 8.5), twig_side, "minecraft:block/oak_log_top", 22.5),
        stick((4, 0, 4), (12, 1, 5), twig_side2, "minecraft:block/spruce_log_top", -45),
        stick((6, 0, 10.5), (13, 1, 11.5), twig_side, "minecraft:block/oak_log_top", None),
        stick((3, 0.5, 12), (9, 1.5, 13), twig_side2, "minecraft:block/spruce_log_top", 45),
    ]
    for shape in range(3):
        for mossy in (False, True):
            suffix = "_mossy" if mossy else ""
            els = twig_els[:2 + shape]
            if mossy:
                els = els + [{"from": [5, 0, 6], "to": [10, 0.2, 10], "faces": {"up": {"texture": "minecraft:block/moss_block", "uv": [0, 0, 5, 4]}}}]
            model(f"twigs_{shape}{suffix}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                             "textures": {"particle": twig_side}, "elements": els})
    blockstate("twigs", facing_variants(lambda p: f"terrahaul:block/twigs_{p['shape']}{'_mossy' if p['mossy'] == 'true' else ''}",
                                        [("mossy", ["false", "true"]), ("shape", [0, 1, 2]), ("waterlogged", ["false", "true"])]))
    item_model("twigs", "terrahaul:block/twigs_2")
    lang["block.terrahaul.twigs"] = "Twigs"

    # pine cones
    cones = [(8, 8), (4.5, 11), (11.5, 5), (11.5, 11.5)]
    for n in range(1, 5):
        els = []
        for i in range(n):
            cx, cz = cones[i]
            faces = {d: {"texture": "terrahaul:block/pine_cone", "uv": [4 * i, 0, 4 * i + 3, 4]} for d in ["north", "south", "east", "west"]}
            faces["up"] = {"texture": "terrahaul:block/pine_cone", "uv": [0, 12, 2, 14]}
            # cones lie on their side
            els.append({"from": [cx - 2, 0, cz - 1], "to": [cx + 2, 2, cz + 1], "faces": faces,
                        "rotation": {"origin": [cx, 1, cz], "axis": "y", "angle": [0, 45, -22.5, 22.5][i]}})
        model(f"pine_cone_{n}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                 "textures": {"particle": "terrahaul:block/pine_cone"}, "elements": els})
    blockstate("pine_cone", facing_variants(lambda p: f"terrahaul:block/pine_cone_{p['count']}",
                                            [("count", [1, 2, 3, 4]), ("waterlogged", ["false", "true"])]))
    item_model("pine_cone", "terrahaul:block/pine_cone_2")
    lang["block.terrahaul.pine_cone"] = "Pine Cone"

    # leaf litter: a single flat layer
    for name, (tex, tinted) in LITTERS.items():
        face = {"texture": tex, "uv": [0, 0, 16, 16]}
        if tinted:
            face["tintindex"] = 0
        model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                     "textures": {"particle": tex},
                     "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False, "faces": {"up": face, "down": dict(face)}}]})
        blockstate(name, facing_variants(lambda p, n=name: f"terrahaul:block/{n}", [("waterlogged", ["false", "true"])]))
        write_json(ASSETS, f'models/item/{name}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": tex}})
        lang[f"block.terrahaul.{name}"] = lang_name(name)

    # shrubs: a leafy core with cross planes for an irregular silhouette
    for name, (tex, core, height) in SHRUBS.items():
        x0, y0, z0, x1, y1, z1 = core
        els = [{"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {d: {"texture": "#leaves", "tintindex": 0, "cullface": None} for d in
                                                                     ["north", "south", "east", "west", "up", "down"]}}]
        for el in els:
            for f in el["faces"].values():
                f.pop("cullface")
        for angle in (45, -45):
            els.append({"from": [0.8, 0, 8], "to": [15.2, height, 8], "shade": False,
                        "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True},
                        "faces": {"north": {"texture": "#leaves", "uv": [0, 16 - height, 16, 16], "tintindex": 0},
                                  "south": {"texture": "#leaves", "uv": [0, 16 - height, 16, 16], "tintindex": 0}}})
        model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped", "ambientocclusion": False,
                     "textures": {"particle": tex, "leaves": tex}, "elements": els})
        blockstate(name, {"": {"model": f"terrahaul:block/{name}"}})
        item_model(name, f"terrahaul:block/{name}")
        lang[f"block.terrahaul.{name}"] = lang_name(name)
    model("dry_shrub", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": "terrahaul:block/dry_shrub"}})
    blockstate("dry_shrub", {"": {"model": "terrahaul:block/dry_shrub"}})
    write_json(ASSETS, 'models/item/dry_shrub.json', {"parent": "minecraft:item/generated", "textures": {"layer0": "terrahaul:block/dry_shrub"}})
    lang["block.terrahaul.dry_shrub"] = "Dry Shrub"


# ------------------------------------------------------------------------------------------------
# data: loot tables, tags, recipes, data maps
# ------------------------------------------------------------------------------------------------

def all_blocks():
    names = []
    for stone in STONES:
        names += [f"{stone}_pebbles", f"{stone}_rock"]
    names += [f"{w}_branch" for w in WOODS] + ["twigs", "pine_cone"] + list(LITTERS) + list(SHRUBS) + ["dry_shrub"]
    return names


def loot_tables():
    for name in all_blocks():
        entry = {"type": "minecraft:item", "name": f"terrahaul:{name}"}
        if name.endswith("_pebbles") or name == "pine_cone":
            entry["functions"] = [{"function": "minecraft:set_count", "count": n, "add": False,
                                   "conditions": [{"condition": "minecraft:block_state_property", "block": f"terrahaul:{name}",
                                                   "properties": {"count": str(n)}}]} for n in range(2, 5)]
        table = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry],
                                                        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
        if name in LITTERS or name in SHRUBS or name == "dry_shrub":
            # like grass: only collected with shears or silk touch
            entry_cond = {"condition": "minecraft:any_of", "terms": [
                {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
                {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
            table["pools"][0]["conditions"] = [entry_cond]
            if name == "dry_shrub":
                table["pools"].append({"rolls": {"type": "minecraft:uniform", "min": 0, "max": 2},
                                       "entries": [{"type": "minecraft:item", "name": "minecraft:stick"}]})
        write_json(DATA, f'terrahaul/loot_table/blocks/{name}.json', table)


def tags():
    def tag(path, values):
        write_json(DATA, path, {"replace": False, "values": values})
    rocks = [f"terrahaul:{s}_rock" for s in STONES]
    pebbles = [f"terrahaul:{s}_pebbles" for s in STONES]
    branches = [f"terrahaul:{w}_branch" for w in WOODS] + ["terrahaul:twigs", "terrahaul:pine_cone"]
    litter = [f"terrahaul:{n}" for n in LITTERS]
    shrubs = [f"terrahaul:{n}" for n in SHRUBS] + ["terrahaul:dry_shrub"]
    tag('minecraft/tags/block/mineable/pickaxe.json', rocks + pebbles)
    tag('minecraft/tags/block/mineable/axe.json', branches)
    tag('minecraft/tags/block/mineable/hoe.json', litter)
    tag('minecraft/tags/block/sword_efficient.json', litter + shrubs)
    tag('minecraft/tags/block/replaceable_by_trees.json', litter + pebbles + branches + shrubs)
    tag('minecraft/tags/block/replaceable.json', litter)
    tag('minecraft/tags/block/enderman_holdable.json', [])
    tag('terrahaul/tags/block/ground_details.json', pebbles + branches + litter)
    # structures moved below the terrain in Terrahaul worlds (UndergroundStructures); badlands
    # mineshafts are meant to break out of the ground and are left out
    tag('terrahaul/tags/worldgen/structure/keep_underground.json',
        ["minecraft:trial_chambers", "minecraft:ancient_city", "minecraft:stronghold", "minecraft:mineshaft"])


def recipes():
    for stone, result in STONE_RECIPES.items():
        write_json(DATA, f'terrahaul/recipe/{stone}_pebbles_to_block.json', {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
            "key": {"#": {"item": f"terrahaul:{stone}_pebbles"}}, "result": {"id": result, "count": 1}})
    for wood in WOODS:
        write_json(DATA, f'terrahaul/recipe/{wood}_branch_to_sticks.json', {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": f"terrahaul:{wood}_branch"}], "result": {"id": "minecraft:stick", "count": 2}})
    write_json(DATA, 'terrahaul/recipe/twigs_to_stick.json', {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "terrahaul:twigs"}], "result": {"id": "minecraft:stick", "count": 1}})


def data_maps():
    values = {}
    for n in LITTERS:
        values[f"terrahaul:{n}"] = {"chance": 0.3}
    for n in list(SHRUBS) + ["dry_shrub"]:
        values[f"terrahaul:{n}"] = {"chance": 0.5}
    values["terrahaul:pine_cone"] = {"chance": 0.3}
    write_json(DATA, 'neoforge/data_maps/item/compostables.json', {"replace": False, "values": values})
    flammable = {}
    write_json(DATA, 'neoforge/data_maps/item/furnace_fuels.json', {"replace": False, "values": {
        **{f"terrahaul:{w}_branch": {"burn_time": 200} for w in WOODS}, "terrahaul:twigs": {"burn_time": 100},
        "terrahaul:pine_cone": {"burn_time": 150}}})


def main():
    lang_path = os.path.join(ASSETS, 'lang', 'en_us.json')
    with open(lang_path) as f:
        lang = json.load(f)
    textures()
    models_and_states(lang)
    lang["itemGroup.terrahaul"] = "Terrahaul"
    write_json(ASSETS, 'lang/en_us.json', lang)
    loot_tables()
    tags()
    recipes()
    data_maps()


if __name__ == '__main__':
    main()
