#!/usr/bin/env python3
"""
Hand-drawn 16x16 item textures for the romance items (wedding ring, flowers, gifts).

Every sprite is a pixel map below: one character per pixel, looked up in the sprite's palette
('.' is transparent). Output is fully deterministic.

  python3 tools/build_item_textures.py [--preview out.png]

writes assets/aigirlfriend/textures/item/<name>.png and, with --preview, an 8x contact sheet for review.
"""
import argparse
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
OUT = os.path.join(ROOT, "assets", "aigirlfriend", "textures", "item")

GOLD = {"k": "4d2c05", "1": "a8650c", "2": "e3a21c", "3": "fbd54a", "4": "fff3a6", "w": "ffffff"}

SPRITES = {}

# Gold band with a brilliant-cut diamond on prongs.
SPRITES["wedding_ring"] = (dict(GOLD, a="1d4f6e", b="3c97c4", c="86dcf0", e="d4f8ff"), [
    "......aaaa......",
    ".....awecca.....",
    "....aeeccbba....",
    "....k3acba2k....",
    ".....k3aa2k.....",
    ".....kk43kk.....",
    "....k4w4433k....",
    "...k44kkkk32k...",
    "...k4k....k2k...",
    "..k44k....k11k..",
    "..k4k......k1k..",
    "..k33k....k11k..",
    "...k3k....k1k...",
    "...k32kkkk11k...",
    "....k221111k....",
    ".....kkkkkk.....",
])

ROSE = {"k": "4a0712", "r": "9e1028", "R": "d4223b", "L": "f0506a", "H": "ff9eae",
        "q": "173d10", "G": "2b6a1f", "g": "4a9a35", "l": "6cc04a", "m": "9be06c"}

# One long-stemmed red rose, held diagonally like vanilla tools.
SPRITES["red_rose"] = (ROSE, [
    "................",
    ".........kkkk...",
    "........kRLLRk..",
    ".......kRHLrRRk.",
    ".......kLLrRrRk.",
    ".......kRrRRLrk.",
    "........krrrrk..",
    "........qlgGlq..",
    ".........gG.....",
    "...qq...gG......",
    "..qmlq.gG.......",
    "..qlmlgG........",
    "...qqgGqq.......",
    "....gGlmlq......",
    "...gG.qqq.......",
    "..gG............",
])

# Three roses and leaves in a cream paper cone with a pink bow.
SPRITES["rose_bouquet"] = (dict(ROSE, n="6b5235", P="d9c4a0", p="f7eedc", o="b79e7a", b="c2185b", B="f06292", W="fde7ef"), [
    "......kkkk......",
    ".....kHLLRk.....",
    ".kkk.kLrRRk.kkk.",
    "kHLRkkRRrRkkHLRk",
    "kLrRkkrRRrkkLrRk",
    "kRRrkgkkkkgkRRrk",
    ".kkkqlgGgGlqkkk.",
    "qlgnppppppPPnglq",
    ".qqnpppppPPonqq.",
    "..bBnppppPonBb..",
    "..bBBBbbbbBBBb..",
    "...bbnbWWbnbb...",
    ".....npbbPn.....",
    "......nbPn......",
    "......npon......",
    ".......nn.......",
])

# A ring of vine with little flowers all around.
SPRITES["flower_crown"] = ({"q": "1d4a16", "G": "2e7024", "g": "4f9e37", "l": "7ccf55",
                             "x": "c2306e", "p": "f06aa0", "P": "ffb3d1", "y": "ffd23f", "Y": "ffe680", "o": "e09a10",
                             "v": "2a55a8", "b": "5b97f2", "B": "a9cbff", "w": "ffffff", "s": "c9c9dc"}, [
    "................",
    "................",
    "................",
    "................",
    ".....qqqqqq.....",
    "...qqglpgggqq...",
    "..qglqq..qqgGq..",
    ".xPxq......qvBv.",
    ".PyPq......qByb.",
    ".xPxGq....qGvbv.",
    "..qGswslqoYoGq..",
    "...qwywGlYyYqq..",
    "....swsqqoYoq...",
    "................",
    "................",
    "................",
])

# An open heart-shaped box with chocolates inside.
SPRITES["box_of_chocolates"] = ({"k": "4a0a14", "r": "8e1426", "R": "d02a42", "L": "f2687c",
                                  "d": "3a0d12", "x": "2a140a", "c": "6b3a1e", "C": "a7653a"}, [
    "................",
    "................",
    "...kkk....kkk...",
    "..kLLRk..kLRRk..",
    ".kLdddRkkLdddRk.",
    ".kLdCcdRRdCcdrk.",
    ".kLdcxddddcxdrk.",
    ".kRddCcddCcddrk.",
    "..kRdcxddcxdrk..",
    "...kRddCcddrk...",
    "....kRdcxdrk....",
    ".....kRddrk.....",
    "......krrk......",
    ".......kk.......",
    "................",
    "................",
])

# A plush teddy bear with a pink bow.
SPRITES["teddy_bear"] = ({"k": "3d2210", "b": "6e4020", "B": "9c6236", "L": "c78a52", "t": "ecc89c",
                           "x": "1a0e06", "w": "ffffff", "p": "f06292", "P": "ad1457"}, [
    "................",
    "..kkk......kkk..",
    ".kBtBk....kBtBk.",
    ".kBtLkkkkkkLtBk.",
    "..kLLLBBBBBBbk..",
    "..kLBxBBBBxBbk..",
    "..kLBBttttBBbk..",
    "..kBBttxxttbbk..",
    "..kBBBttttbbbk..",
    "...kkpPPPPpkk...",
    "..kLLBttttBbbk..",
    ".kLLkBttttBkbbk.",
    ".kLLkBttttBkbbk.",
    "..kkkBBttBBkkk..",
    "..kttLkkkkBttk..",
    "..kkkk....kkkk..",
])

# A cream envelope sealed with a red wax heart.
SPRITES["love_letter"] = ({"k": "5a4030", "e": "fff6e6", "E": "ecdcc0", "d": "c4a882",
                           "x": "6e0a1c", "r": "c81e3a", "R": "ff5a72", "w": "ffd0d8"}, [
    "................",
    "................",
    "................",
    ".kkkkkkkkkkkkkk.",
    ".kdeeeeeeeeeedk.",
    ".keddeeeeeeddek.",
    ".keeeddeeddeeek.",
    ".keeexxddxxeeek.",
    ".keexRrxxrrxeek.",
    ".kEExrrrrrrxEEk.",
    ".kEEExrrrrxEEEk.",
    ".kEEEExrrxEEEEk.",
    ".kEEEEExxEEEEEk.",
    ".kkkkkkkkkkkkkk.",
    "................",
    "................",
])

# A fine gold chain with a ruby-red heart pendant.
SPRITES["heart_necklace"] = (dict(GOLD, g="7a4a08", r="a8102e", R="e3304f", H="ff8aa0"), [
    "................",
    ".3...........3..",
    ".1...........1..",
    "..3.........3...",
    "..1.........1...",
    "...3.......3....",
    "....13...31.....",
    "......313.......",
    "....gg.3.gg.....",
    "...gHHgggRRg....",
    "...gHRRRRRRg....",
    "...gRRRRRRrg....",
    "....gRRRRrg.....",
    ".....gRRrg......",
    "......grg.......",
    ".......g........",
])

# A pink present with a golden ribbon and bow.
SPRITES["gift_box"] = ({"k": "5a1530", "p": "f7a8c8", "P": "e86c9c", "d": "b83c70", "L": "ffd6e6",
                         "o": "7a5208", "y": "ffe680", "Y": "e8b422"}, [
    "................",
    "...ooo....ooo...",
    "..oyYYo..oYYyo..",
    "..oY..oooo..Yo..",
    "...oYYyyyyYYo...",
    ".kkkkkoyYokkkkk.",
    ".kLLLLkyYkLLLLk.",
    ".kpppPkyYkpppPk.",
    "..kkkkkyYkkkkk..",
    "..kppPkyYkpPPk..",
    "..kppPkyYkpPdk..",
    "..kpPPkyYkPPdk..",
    "..kpPPkyYkPddk..",
    "..kPPdkyYkPddk..",
    "..kPddkyYkdddk..",
    "..kkkkkkkkkkkk..",
])


def render(palette, rows):
    assert len(rows) == 16, len(rows)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            h = palette[ch]
            img.putpixel((x, y), (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255))
    return img


def contact_sheet(images, scale=8):
    pad, label = 12, 14
    cols = 3
    rows = (len(images) + cols - 1) // cols
    cell = 16 * scale
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, rows * (cell + pad + label) + pad), (198, 198, 198, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(images):
        cx = pad + (i % cols) * (cell + pad)
        cy = pad + (i // cols) * (cell + pad + label)
        # checkerboard behind the sprite so transparency is visible
        for ty in range(0, cell, scale * 2):
            for tx in range(0, cell, scale * 2):
                c = (139, 139, 139, 255) if (tx // (scale * 2) + ty // (scale * 2)) % 2 else (120, 120, 120, 255)
                draw.rectangle([cx + tx, cy + ty, cx + tx + scale * 2 - 1, cy + ty + scale * 2 - 1], fill=c)
        sheet.alpha_composite(img.resize((cell, cell), Image.NEAREST), (cx, cy))
        draw.text((cx, cy + cell + 2), name, fill=(30, 30, 30, 255))
    return sheet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="also write an 8x contact sheet here")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    images = []
    for name, (palette, rows) in SPRITES.items():
        img = render(palette, rows)
        img.save(os.path.join(OUT, name + ".png"))
        images.append((name, img))
    if args.preview:
        contact_sheet(images).save(args.preview)
    print("wrote %d item textures to %s" % (len(images), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()
