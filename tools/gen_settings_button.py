"""The settings button for the player's inventory (textures/gui/sprites/inventory/settings*.png): a carved gear with a
gold gem at its heart, in the frame of the Quest Journal's button (and its highlighted frame)."""
import math
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, "..", "src", "main", "resources", "assets", "fantasyweapons", "textures", "gui", "sprites", "inventory")
OUTLINE, LIGHT, SHADE, DARK = (0x1C, 0x14, 0x0C, 255), (0xBE, 0xC0, 0xCC, 255), (0x78, 0x7A, 0x88, 255), (0x28, 0x28, 0x34, 255)
GOLD, GOLD_DARK = (0xF2, 0xC4, 0x6B, 255), (0xB0, 0x7A, 0x2A, 255)
CX, CY = 9.5, 8.5


def inside(x, y):
    dx, dy = x - CX, y - CY
    r = math.hypot(dx, dy)
    a = math.atan2(dy, dx) + math.pi / 8
    outer = 6.7 if math.cos(8 * a) > 0.3 else 5.1
    return 2.1 <= r <= outer


def build(frame_name, out_name):
    im = Image.open(os.path.join(DIR, frame_name)).convert("RGBA")
    background = im.getpixel((2, 15))
    # clear the scroll, keep the frame
    for y in range(2, 16):
        for x in range(2, 18):
            im.putpixel((x, y), background)
    for y in range(2, 16):
        for x in range(2, 18):
            if inside(x, y):
                edge = any(not inside(x + ox, y + oy) for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    im.putpixel((x, y), OUTLINE)
                else:
                    dx, dy = x - CX, y - CY
                    im.putpixel((x, y), LIGHT if dx + dy < 0.5 else SHADE)
            elif math.hypot(x - CX, y - CY) < 2.1:
                # the gem in the hub
                im.putpixel((x, y), GOLD if (x - CX) + (y - CY) < 0 else GOLD_DARK)
    # a shadowed rim along the bottom right of each tooth
    for y in range(2, 16):
        for x in range(2, 18):
            if im.getpixel((x, y)) == SHADE and not inside(x + 1, y + 1):
                im.putpixel((x, y), DARK)
    im.save(os.path.join(DIR, out_name))


build("quests.png", "settings.png")
build("quests_highlighted.png", "settings_highlighted.png")
