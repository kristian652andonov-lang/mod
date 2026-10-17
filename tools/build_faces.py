#!/usr/bin/env python3
"""
Builds the facial-expression overlays (textures/entity/girlfriend/face/*.png) from her own face in skin.png.

Her face lives on the head's front (texture 8..15, 8..15). The hair/hood overlay covers the upper rows and leaves
holes for the eyes (row 12, columns 2 and 5) and the lower face (rows 13-15), so expressions only repaint those
visible pixels; every other pixel stays transparent so her real face shows through.

Usage: python3 tools/build_faces.py
"""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "aigirlfriend", "textures", "entity", "girlfriend")

SKIN = (0xe5, 0xd2, 0xd2)      # face skin
LIGHT = (0xff, 0xf3, 0xf2)     # lower face highlight
EYE = (0x7a, 0x60, 0x6c)       # eye colour, a touch darker than the base for readability
LASH = (0xa8, 0x93, 0x97)
BLUSH = (0xf2, 0xa9, 0xb8)
MOUTH = (0xc9, 0x7b, 0x8c)
MOUTH_DARK = (0x9a, 0x55, 0x66)
TEAR = (0xb8, 0xd8, 0xf0)

# (row, col) inside the 8x8 face; rows 12..15 and cols 1..6 are visible through the overlay.
FACES = {
    "happy":       {(12, 2): SKIN, (12, 5): SKIN, (13, 1): EYE, (12, 2): EYE, (13, 2): SKIN, (13, 3): EYE,
                    (12, 5): EYE, (13, 4): EYE, (13, 5): SKIN, (13, 6): EYE, (14, 1): BLUSH, (14, 6): BLUSH,
                    (15, 3): MOUTH, (15, 4): MOUTH},
    "sad":         {(12, 2): SKIN, (12, 5): SKIN, (13, 2): EYE, (13, 5): EYE, (13, 1): LASH, (13, 6): LASH,
                    (14, 2): TEAR, (15, 3): MOUTH_DARK, (15, 4): MOUTH_DARK, (15, 2): MOUTH, (15, 5): MOUTH},
    "fear":        {(12, 2): EYE, (13, 2): EYE, (12, 5): EYE, (13, 5): EYE, (13, 3): LIGHT, (13, 4): LIGHT,
                    (15, 3): MOUTH_DARK, (15, 4): MOUTH_DARK, (14, 1): LIGHT, (14, 6): LIGHT},
    "embarrassed": {(12, 2): EYE, (13, 3): EYE, (13, 2): SKIN, (12, 5): EYE, (13, 4): EYE, (13, 5): SKIN,
                    (14, 1): BLUSH, (14, 2): BLUSH, (14, 5): BLUSH, (14, 6): BLUSH, (13, 1): BLUSH, (13, 6): BLUSH,
                    (15, 3): MOUTH, (15, 4): MOUTH},
    "affection":   {(13, 1): EYE, (12, 2): EYE, (13, 2): SKIN, (13, 3): EYE, (12, 5): EYE, (13, 4): EYE,
                    (13, 5): SKIN, (13, 6): EYE, (14, 1): BLUSH, (14, 2): BLUSH, (14, 5): BLUSH, (14, 6): BLUSH,
                    (15, 3): MOUTH, (15, 4): MOUTH},
    "angry":       {(12, 2): SKIN, (12, 5): SKIN, (13, 1): EYE, (13, 2): EYE, (13, 5): EYE, (13, 6): EYE,
                    (15, 2): MOUTH_DARK, (15, 3): MOUTH_DARK, (15, 4): MOUTH_DARK, (15, 5): MOUTH_DARK},
    "sleepy":      {(12, 2): SKIN, (12, 5): SKIN, (13, 1): EYE, (13, 2): EYE, (13, 5): EYE, (13, 6): EYE,
                    (15, 3): MOUTH, (15, 4): LIGHT},
    "surprised":   {(12, 2): EYE, (13, 2): EYE, (12, 5): EYE, (13, 5): EYE, (14, 3): MOUTH_DARK, (14, 4): MOUTH_DARK,
                    (15, 3): MOUTH_DARK, (15, 4): MOUTH_DARK},
    "blink":       {(12, 2): SKIN, (12, 5): SKIN, (13, 1): LASH, (13, 2): EYE, (13, 5): EYE, (13, 6): LASH},
}


def main():
    out_dir = os.path.join(TEX, "face")
    os.makedirs(out_dir, exist_ok=True)
    for name, pixels in FACES.items():
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for (row, col), color in pixels.items():
            img.putpixel((8 + col, row), color + (255,))
        img.save(os.path.join(out_dir, name + ".png"))
    print(len(FACES), "face overlays written")


if __name__ == "__main__":
    main()
