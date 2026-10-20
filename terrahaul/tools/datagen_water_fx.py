#!/usr/bin/env python3
"""Generates the assets of Terrahaul's client-side water effects: the spray, mist and foam particle
sprites, their particle descriptions, and the looping waterfall and rapids sounds with sounds.json.

Usage: python3 tools/datagen_water_fx.py
Needs Pillow and numpy, and ffmpeg with the libvorbis encoder for the sounds. Everything is written
to src/main/resources/assets/terrahaul and committed.

Everything here is original and procedural, drawn and synthesised from fixed random seeds, so
re-running the script without changes produces identical files:
- sprites are small white pixel-art shapes (the particles tint them): droplets, soft mist puffs
  with a smooth alpha falloff, and lacy foam patches;
- the sounds are built from noise and bubble models, not recordings. Each loop is periodic by
  construction (its noise is synthesised over exactly one loop length in the frequency domain and
  every modulation completes whole cycles), so it repeats without a seam or a click.
"""
import json
import math
import os
import random
import subprocess

import numpy as np
from PIL import Image

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'terrahaul')
RATE = 44100


def write_json(path, obj):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save_texture(img, name):
    full = os.path.join(ASSETS, 'textures', 'particle', name + '.png')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


# ------------------------------------------------------------------------------------------------
# sprites
# ------------------------------------------------------------------------------------------------

def spray_sprites():
    """Droplets thrown off falling water: a round drop, a few scattered beads, a falling streak and a
    small puff of spray. 8x8, white, alpha marks the soft rim of each drop."""
    def put(img, x, y, a):
        if 0 <= x < 8 and 0 <= y < 8:
            old = img.getpixel((x, y))[3]
            img.putpixel((x, y), (255, 255, 255, max(old, a)))

    def drop(img, x, y, core=245, rim=120):
        put(img, x, y, core)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, rim)

    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        put(img, x, y, 250)
    for x, y in ((2, 3), (2, 4), (5, 3), (5, 4), (3, 2), (4, 2), (3, 5), (4, 5)):
        put(img, x, y, 130)
    save_texture(img, 'spray_0')

    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    drop(img, 2, 2)
    drop(img, 5, 3, 230, 100)
    drop(img, 3, 5, 240, 110)
    save_texture(img, 'spray_1')

    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y, a in ((1, 110), (2, 170), (3, 220), (4, 250), (5, 250)):
        put(img, 3, y, a)
        put(img, 4, y, a - 60 if y < 4 else 200)
    put(img, 3, 6, 140)
    put(img, 4, 6, 140)
    save_texture(img, 'spray_2')

    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    rnd = random.Random(31)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d < 3.3:
                a = 235 if d < 1.6 else 165 if d < 2.5 else 95
                if d > 2.5 and rnd.random() < 0.35:
                    continue
                put(img, x, y, a)
    save_texture(img, 'spray_3')


def blob_radius(angle, harmonics):
    """Radius of an irregular blob outline in a direction, from a few phase-shifted harmonics."""
    r = 1.0
    for k, amp, phase in harmonics:
        r += amp * math.sin(k * angle + phase)
    return r


def mist_sprites():
    """Soft puffs of mist and spray cloud. 32x32 (a puff is drawn up to fourteen blocks wide, so
    its pixels must stay small), white, with alpha falling smoothly from a lumpy, dense core to
    nothing well inside the square, so the edge of the quad never shows. The mist has its own
    shader, which keeps even the faintest rim (Minecraft's particle shader drops everything under
    an opacity of 0.1), so overlapping puffs blend into one soft cloud and fade out smoothly."""
    rnd = random.Random(47)
    size = 32
    centre = (size - 1) / 2.0
    for i in range(4):
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        harmonics = [(k, rnd.uniform(0.05, 0.16) / (1 + 0.3 * k), rnd.uniform(0, 2 * math.pi)) for k in (2, 3, 5)]
        # two or three lobes make the puff look like a cauliflower of smaller puffs
        lobes = [(rnd.uniform(-4.2, 4.2), rnd.uniform(-4.2, 4.2), rnd.uniform(6.0, 8.0)) for _ in range(2 + i % 2)]
        for y in range(size):
            for x in range(size):
                dx, dy = x - centre, y - centre
                r = math.hypot(dx, dy) / (12.8 * blob_radius(math.atan2(dy, dx), harmonics))
                density = max(0.0, 1.0 - r * r)
                for lx, ly, lr in lobes:
                    d = math.hypot(dx - lx, dy - ly) / lr
                    density = max(density, 0.85 * max(0.0, 1.0 - d * d))
                density = min(1.0, density * 1.15)
                # a soft shoulder: thin at the rim, dense in the core
                a = int(round(240 * density ** 1.4 / 4.0)) * 4
                if a:
                    # a slightly darker underside gives the puff some volume
                    shade = 255 - int(max(0.0, dy) * 1.1)
                    img.putpixel((x, y), (shade, shade, 255 - int(max(0.0, dy) * 0.6), a))
        save_texture(img, f'mist_{i}')


def foam_sprites():
    """Patches of foam floating on water: a clump of froth made of overlapping round clusters that
    fills most of the square, densest in the middle, with a few open bubbles inside it and loose
    bubbles around its edge. 16x16."""
    rnd = random.Random(59)
    for i in range(4):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        # the clusters wander from the middle, so each patch has its own lumpy outline
        clusters = []
        x, y = 8.0, 8.0
        for _ in range(6 + i):
            clusters.append((x, y, rnd.uniform(4.0, 5.6)))
            x = min(10.8, max(5.2, x + rnd.uniform(-2.8, 2.8)))
            y = min(10.8, max(5.2, y + rnd.uniform(-2.8, 2.8)))
        holes = [(rnd.uniform(4, 12), rnd.uniform(4, 12), rnd.uniform(0.6, 1.1)) for _ in range(4)]
        for py in range(16):
            for px in range(16):
                cx, cy = px + 0.5, py + 0.5
                cover = max(1.0 - math.hypot(cx - qx, cy - qy) / qr for qx, qy, qr in clusters)
                if cover <= 0.0:
                    continue
                a = 245 if cover > 0.45 else 210 if cover > 0.2 else 150
                if any(math.hypot(cx - hx, cy - hy) < hr for hx, hy, hr in holes) and cover > 0.2:
                    a = 110
                b = 255 - rnd.randint(0, 12)
                img.putpixel((px, py), (b, b, 255, a))
        for _ in range(6):
            qx, qy, qr = clusters[rnd.randrange(len(clusters))]
            angle = rnd.uniform(0, 2 * math.pi)
            bx = int(qx + math.cos(angle) * (qr + 1.2))
            by = int(qy + math.sin(angle) * (qr + 1.2))
            if 0 <= bx < 16 and 0 <= by < 16 and img.getpixel((bx, by))[3] == 0:
                img.putpixel((bx, by), (250, 250, 255, 200))
        save_texture(img, f'foam_{i}')


def particle_descriptions():
    write_json('particles/spray.json', {"textures": [f"terrahaul:spray_{i}" for i in range(4)]})
    write_json('particles/mist.json', {"textures": [f"terrahaul:mist_{i}" for i in range(4)]})
    write_json('particles/foam.json', {"textures": [f"terrahaul:foam_{i}" for i in range(4)]})


# ------------------------------------------------------------------------------------------------
# sounds
# ------------------------------------------------------------------------------------------------

def periodic_noise(n, power, rng):
    """Gaussian noise of exactly n samples whose power spectrum follows power(f), periodic over n:
    it is built from whole cycles only, so the loop joins without a seam."""
    freqs = np.fft.rfftfreq(n, 1.0 / RATE)
    amp = np.sqrt(np.maximum(power(np.maximum(freqs, 1e-3)), 0.0))
    amp[0] = 0.0
    spectrum = amp * (rng.standard_normal(len(freqs)) + 1j * rng.standard_normal(len(freqs)))
    signal = np.fft.irfft(spectrum, n)
    return signal / np.sqrt(np.mean(signal ** 2))


def periodic_envelope(n, f_low, f_high, rng):
    """A smooth random modulation between f_low and f_high Hz, periodic over n, scaled to unit RMS."""
    length = n / RATE
    out = np.zeros(n)
    t = np.arange(n) / RATE
    k_low = max(1, int(math.ceil(f_low * length)))
    k_high = max(k_low, int(f_high * length))
    for k in range(k_low, k_high + 1):
        out += rng.standard_normal() * np.sin(2 * math.pi * k * t / length + rng.uniform(0, 2 * math.pi)) / math.sqrt(k)
    return out / np.sqrt(np.mean(out ** 2))


def band(f, low, high, order=2):
    """Magnitude-squared of a smooth band: high pass at low, low pass at high."""
    hp = (f / low) ** (2 * order) / (1 + (f / low) ** (2 * order))
    lp = 1 / (1 + (f / high) ** (2 * order))
    return hp * lp


def waterfall_roar(seconds, seed):
    """The roar of a big fall heard from its pool: a deep, broad rush (a little steeper than pink
    noise, strongest from about 80 to 700 Hz) whose body swells slowly with the turbulence, under
    a lighter hiss of spray that crackles with the impacts of single masses of water."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    body = periodic_noise(n, lambda f: band(f, 45, 700, 1) * (1 + (f / 200.0)) ** -0.35 * (1 + 0.6 * np.exp(-((np.log(f) - math.log(140)) ** 2) / 0.5)), rng)
    hiss = periodic_noise(n, lambda f: band(f, 1500, 6000, 2), rng)
    swell = 1 + 0.16 * periodic_envelope(n, 0.15, 1.6, rng)
    crackle = np.clip(1 + 0.45 * periodic_envelope(n, 5.0, 28.0, rng), 0.15, None)
    out = body * swell + 0.22 * hiss * crackle * swell
    return out


def bubble_layer(n, rate, f_range, rng, gain=1.0):
    """Babble of bubbles: short decaying chirps (a bubble rings at a pitch set by its size and the
    pitch rises as it reaches the surface), at a rate that swells and falls. Placed around the
    loop and wrapped at its end, so the layer stays periodic."""
    out = np.zeros(n)
    length = n / RATE
    density = 1 + 0.55 * periodic_envelope(n, 0.2, 2.5, rng)
    count = int(rate * length)
    log_lo, log_hi = math.log(f_range[0]), math.log(f_range[1])
    accepted = 0
    while accepted < count:
        start = int(rng.uniform(0, n))
        if rng.uniform(0, 2.2) > density[start]:
            continue
        accepted += 1
        f0 = math.exp(rng.uniform(log_lo, log_hi))
        # big bubbles ring longer; every bubble's pitch rises by up to half an octave
        tau = min(0.03, 2.6 / f0 + rng.uniform(0.0, 0.004))
        dur = int(tau * 5 * RATE)
        t = np.arange(dur) / RATE
        rise = rng.uniform(0.15, 0.6)
        phase = 2 * math.pi * f0 * (t + rise * t * t / (2 * tau * 5))
        amp = gain * rng.lognormal(0.0, 0.5) * (600.0 / f0) ** 0.5
        chirp = amp * np.sin(phase) * np.exp(-t / tau) * (1 - np.exp(-t / 0.0006))
        idx = (start + np.arange(dur)) % n
        np.add.at(out, idx, chirp)
    return out


def water_rush(seconds, seed):
    """Water running over rocks and down small steps: a lighter, brighter rush than the roar with
    a babble of bubbles on top, the familiar sound of a mountain stream."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    bed = periodic_noise(n, lambda f: band(f, 220, 3200, 1) * (1 + f / 1500.0) ** -0.3, rng)
    swell = 1 + 0.22 * periodic_envelope(n, 0.25, 2.0, rng)
    bubbles = bubble_layer(n, 110, (380, 1900), rng)
    bubbles /= np.sqrt(np.mean(bubbles ** 2))
    fizz = periodic_noise(n, lambda f: band(f, 2500, 8000, 2), rng) * np.clip(1 + 0.6 * periodic_envelope(n, 8.0, 30.0, rng), 0.1, None)
    return bed * swell * 0.75 + bubbles * 0.55 + fizz * 0.08


def encode(signal, name, rms_db):
    """Normalises to the given RMS level, softly limits peaks and writes a mono Ogg Vorbis file.
    The levels are set like Minecraft's own ambient loops (about -30 to -45 dBFS RMS), so a fall at
    full volume sits with the game's sounds instead of over them."""
    signal = signal / np.sqrt(np.mean(signal ** 2)) * 10 ** (rms_db / 20.0)
    signal = np.tanh(signal / 0.9) * 0.9
    full = os.path.join(ASSETS, 'sounds', 'ambient', name + '.ogg')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    pcm = (np.clip(signal, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                    '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact',
                    '-c:a', 'libvorbis', '-q:a', '4', full], input=pcm, check=True)


def sounds():
    # two loops of different lengths per sound, so nearby falls never play in step
    encode(waterfall_roar(8.0, 101), 'waterfall_roar_0', -30.0)
    encode(waterfall_roar(6.6, 102), 'waterfall_roar_1', -30.0)
    encode(water_rush(7.0, 201), 'water_rush_0', -31.0)
    encode(water_rush(5.8, 202), 'water_rush_1', -31.0)
    write_json('sounds.json', {
        "ambient.waterfall": {
            "subtitle": "subtitles.terrahaul.ambient.waterfall",
            "sounds": [{"name": "terrahaul:ambient/waterfall_roar_0"}, {"name": "terrahaul:ambient/waterfall_roar_1"}]
        },
        "ambient.rapids": {
            "subtitle": "subtitles.terrahaul.ambient.rapids",
            "sounds": [{"name": "terrahaul:ambient/water_rush_0"}, {"name": "terrahaul:ambient/water_rush_1"}]
        }
    })


def main():
    spray_sprites()
    mist_sprites()
    foam_sprites()
    particle_descriptions()
    sounds()


if __name__ == '__main__':
    main()
