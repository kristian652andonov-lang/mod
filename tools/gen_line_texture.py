"""The cross-section of the lines the magic circles are drawn with (v across the line): solid in the middle, a soft
edge of a few texels on each side so a line stays smooth at any size. White, tinted in game."""
import os, sys
from PIL import Image

OUT = sys.argv[1]
W, H = 4, 64
img = Image.new('RGBA', (W, H), (255, 255, 255, 0))
px = img.load()
for y in range(H):
    d = abs((y + 0.5) / H * 2 - 1)          # 0 in the middle, 1 at the edge
    k = min(1.0, max(0.0, (1 - d) / 0.42))  # the outer 42% fades out
    a = k * k * (3 - 2 * k)
    for x in range(W):
        px[x, y] = (255, 255, 255, int(round(255 * a)))
img.save(os.path.join(OUT, 'line_crisp.png'))
with open(os.path.join(OUT, 'line_crisp.png.mcmeta'), 'w') as f:
    f.write('{"texture":{"blur":true,"clamp":true}}')
print('ok')
