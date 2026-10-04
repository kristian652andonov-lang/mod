#!/usr/bin/env python3
"""
Synthesises the mod's PLACEHOLDER sound effects (no vanilla sounds are used) and writes sounds.json.

Each sound event registered in ModSounds gets its own .ogg under assets/fantasyweapons/sounds/<category>/<name>.ogg.
To replace a sound with a real recording, overwrite the .ogg with the same name — nothing else needs to change.
If you add variations, list them in sounds.json (this script only writes it when missing a file entry; pass
--force-json to regenerate it).

Requires: numpy, ffmpeg (for Vorbis encoding).
Usage: python3 tools/gen_sounds.py [--force-json]
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fantasyweapons')
rng = np.random.default_rng(1234)


def t_axis(dur):
    return np.arange(int(round(SR * dur))) / SR


def env(n, attack, release, shape=2.0):
    """Attack/decay envelope over n samples. attack/release in seconds."""
    a = max(1, int(SR * attack))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** 0.6
    r = max(1, min(n - a, int(SR * release)))
    e[n - r:] *= np.linspace(1, 0, r) ** shape
    return e


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / (SR * tau))


def noise(dur):
    return rng.standard_normal(int(round(SR * dur)))


def stft_filter(x, band_fn, frame=2048, hop=512):
    """Time-varying spectral filter. band_fn(progress 0..1, freqs) -> gain array."""
    win = np.hanning(frame)
    pad = np.concatenate([np.zeros(frame), x, np.zeros(frame)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    n_frames = (len(pad) - frame) // hop
    for i in range(n_frames):
        s = i * hop
        seg = pad[s:s + frame] * win
        spec = np.fft.rfft(seg)
        prog = min(1.0, max(0.0, (s - frame) / max(1, len(x))))
        spec *= band_fn(prog, freqs)
        out[s:s + frame] += np.fft.irfft(spec) * win
        norm[s:s + frame] += win ** 2
    out /= np.maximum(norm, 1e-6)
    return out[frame:frame + len(x)]


def bandpass(lo, hi, soft=0.25):
    def fn(_, f):
        g = np.ones_like(f)
        g *= 1 / (1 + (lo / np.maximum(f, 1)) ** (2 / soft))
        g *= 1 / (1 + (f / hi) ** (2 / soft))
        return g
    return fn


def sweep(lo0, hi0, lo1, hi1, soft=0.3):
    def fn(p, f):
        lo = lo0 + (lo1 - lo0) * p
        hi = hi0 + (hi1 - hi0) * p
        return bandpass(lo, hi, soft)(p, f)
    return fn


def sine(freq, dur, phase=0.0):
    t = t_axis(dur)
    if callable(freq):
        f = freq(t / dur)
        return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)
    return np.sin(2 * np.pi * freq * t + phase)


def saw(freq, dur):
    t = t_axis(dur)
    f = freq(t / dur) if callable(freq) else np.full_like(t, freq)
    ph = np.cumsum(f) / SR
    return 2 * (ph - np.floor(ph + 0.5))


def reverb(x, decay=0.35, delays=(0.029, 0.037, 0.051, 0.067, 0.083), mix=0.35):
    y = np.copy(x)
    tail = np.zeros(len(x) + int(SR * 0.6))
    for i, d in enumerate(delays):
        k = int(SR * d)
        g = decay ** (i + 1)
        for rep in range(1, 6):
            off = k * rep
            if off >= len(tail):
                break
            seg = x * (g ** rep)
            tail[off:off + len(seg)] += seg[:len(tail) - off]
    out = np.concatenate([y, np.zeros(len(tail) - len(y))]) + tail * mix
    return out


def normalize(x, peak=0.89):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def crackle(dur, density=600, decay=0.002):
    n = int(round(SR * dur))
    x = np.zeros(n)
    count = int(density * dur)
    for p in rng.integers(0, n, count):
        L = min(n - p, int(SR * 0.006))
        x[p:p + L] += rng.choice([-1, 1]) * rng.uniform(0.3, 1.0) * np.exp(-np.arange(L) / (SR * decay))
    return x


# ---------------------------------------------------------------------------------------------------------------------
# recipes
# ---------------------------------------------------------------------------------------------------------------------

def whoosh(dur=0.35, lo=300, hi=2500, up=True, gain=1.0):
    x = noise(dur)
    fn = sweep(lo * 0.5, hi * 0.6, lo * 1.5, hi * 1.6) if up else sweep(lo * 1.5, hi * 1.6, lo * 0.5, hi * 0.6)
    y = stft_filter(x, fn) * env(len(x), dur * 0.35, dur * 0.6)
    return y * gain


def impact(dur=0.5, f0=110, f1=45, body=1.0, snap=0.6):
    n = int(round(SR * dur))
    thump = sine(lambda p: f0 + (f1 - f0) * p ** 0.4, dur) * exp_decay(n, dur * 0.25) * body
    burst = stft_filter(noise(dur), bandpass(800, 6000)) * exp_decay(n, 0.03) * snap
    return thump + burst


def boom(dur=1.6, low=60):
    n = int(round(SR * dur))
    x = stft_filter(noise(dur), sweep(20, 900, 20, 200)) * exp_decay(n, dur * 0.3)
    sub = sine(lambda p: low * (1 - 0.5 * p), dur) * exp_decay(n, dur * 0.25)
    return x * 1.2 + sub * 0.9


def chime(notes, dur=1.2, spacing=0.09, bright=1.0):
    n = int(round(SR * (dur + spacing * len(notes))))
    out = np.zeros(n)
    for i, f in enumerate(notes):
        s = int(SR * spacing * i)
        L = n - s
        tone = (sine(f, L / SR) + 0.4 * sine(f * 2.01, L / SR) * bright + 0.15 * sine(f * 3.02, L / SR) * bright)
        out[s:] += tone * exp_decay(L, 0.45)
    return reverb(out, mix=0.45)


def zap(dur=0.6):
    n = int(round(SR * dur))
    c = crackle(dur, density=1800, decay=0.0015)
    buzz = saw(lambda p: 90 + 40 * np.sin(p * 40), dur) * 0.4
    hiss = stft_filter(noise(dur), bandpass(3000, 12000)) * 0.5
    return (c + buzz + hiss) * exp_decay(n, dur * 0.35)


def ice(dur=0.9):
    n = int(round(SR * dur))
    out = np.zeros(n)
    for k in range(14):
        s = int(rng.uniform(0, 0.35) * SR)
        f = rng.uniform(1800, 5200)
        L = n - s
        out[s:] += sine(f, L / SR) * exp_decay(L, rng.uniform(0.04, 0.12)) * rng.uniform(0.3, 0.8)
    out += crackle(dur, density=900, decay=0.001) * 0.6 * exp_decay(n, 0.25)
    out += impact(dur, 300, 120, body=0.6, snap=0.9)[:n]
    return reverb(out, mix=0.3)


def fire(dur=0.8):
    n = int(round(SR * dur))
    x = stft_filter(noise(dur), sweep(150, 1400, 250, 3000)) * env(n, 0.08, dur * 0.7)
    return x + crackle(dur, density=300, decay=0.003) * 0.4


def chain_rattle(dur=0.7):
    n = int(round(SR * dur))
    out = np.zeros(n)
    for k in range(18):
        s = int(rng.uniform(0, dur * 0.8) * SR)
        f = rng.uniform(2500, 4800)
        L = min(n - s, int(SR * 0.08))
        out[s:s + L] += sine(f, L / SR) * exp_decay(L, 0.012) * rng.uniform(0.3, 1.0)
    return out


def drone(dur, f, dark=True):
    t = t_axis(dur)
    x = sine(f, dur) + 0.5 * sine(f * 1.5, dur) + 0.3 * sine(f * 0.5, dur)
    x *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t)
    if dark:
        x += stft_filter(noise(dur), bandpass(40, 400)) * 0.6
    return x


def rift(dur=1.4, opening=True):
    n = int(round(SR * dur))
    swell = stft_filter(noise(dur), sweep(80, 600, 300, 4000) if opening else sweep(300, 4000, 60, 500))
    e = np.linspace(0, 1, n) ** 2 if opening else np.linspace(1, 0, n) ** 1.5
    return swell * e * 1.3 + drone(dur, 55) * e * 0.5


def blip(f=1200, dur=0.06):
    n = int(round(SR * dur))
    return sine(f, dur) * exp_decay(n, dur * 0.3)


def mixdown(*layers):
    """Sums layers of different lengths (zero-padded to the longest)."""
    n = max(len(x) for x in layers)
    out = np.zeros(n)
    for x in layers:
        out[:len(x)] += x
    return out


RECIPES = {
    'weapon/swing': lambda: whoosh(0.32, 400, 3000),
    'weapon/swing_heavy': lambda: whoosh(0.55, 150, 1800, gain=1.2),
    'weapon/hit': lambda: impact(0.35, 180, 70, body=0.8, snap=0.8),
    'weapon/heavy_impact': lambda: mixdown(impact(0.8, 120, 35, body=1.3, snap=0.9), boom(0.8, 50) * 0.4),
    'ability/charge_start': lambda: mixdown(whoosh(0.5, 200, 1500) * 0.6, chime([440, 660], 0.4, 0.05, 0.5) * 0.3),
    'ability/charge_loop': lambda: drone(2.0, 110, dark=False) * 0.7,
    'ability/full_charge': lambda: chime([880, 1320, 1760], 0.8, 0.04, 1.2),
    'ability/activate': lambda: mixdown(whoosh(0.4, 600, 5000), impact(0.4, 220, 90, body=0.5, snap=1.0)),
    'ability/impact': lambda: impact(0.7, 150, 40, body=1.2, snap=1.0),
    'ability/fizzle': lambda: stft_filter(noise(0.4), sweep(2000, 6000, 200, 800)) * exp_decay(int(SR * 0.4), 0.12),
    'ability/denied': lambda: np.concatenate([blip(300, 0.07), blip(220, 0.1)]),
    'progression/level_up': lambda: chime([523, 659, 784, 1046], 1.4, 0.08),
    'progression/ability_unlock': lambda: chime([392, 523, 659, 784, 1046, 1318], 1.8, 0.09, 1.3),
    'progression/upgrade': lambda: chime([659, 988, 1318], 0.9, 0.06),
    'progression/exp': lambda: mixdown(blip(1600, 0.05) * 0.5, blip(2400, 0.05) * 0.3),
    'ui/click': lambda: blip(900, 0.05),
    'ui/hover': lambda: blip(1400, 0.03) * 0.5,
    'form/mode_transform': lambda: mixdown(rift(1.2, True) * 0.6, chime([330, 494, 660], 1.0, 0.12) * 0.6),
    'form/chain_transform': lambda: mixdown(chain_rattle(1.2), fire(1.2) * 0.6),
    'form/chain_retract': lambda: mixdown(chain_rattle(0.8)[::-1], whoosh(0.8, 300, 2000, up=False) * 0.5),
    'form/chain_attack': lambda: mixdown(chain_rattle(0.5) * 0.6, whoosh(0.5, 300, 3500), fire(0.5) * 0.4),
    'void/slash': lambda: mixdown(whoosh(0.5, 120, 3500), drone(0.5, 70) * 0.4 * exp_decay(int(SR * 0.5), 0.2)),
    'void/teleport': lambda: mixdown(whoosh(0.35, 800, 6000, up=False)[::-1] * 0.8, chime([1200, 1800], 0.3, 0.03) * 0.4),
    'void/rift_open': lambda: rift(1.4, True),
    'void/rift_close': lambda: mixdown(rift(0.9, False), impact(0.9, 90, 30, body=0.9, snap=0.3)),
    'void/execution': lambda: mixdown(whoosh(0.3, 1000, 8000), impact(0.9, 140, 30, body=1.3, snap=1.0)),
    'void/dimension': lambda: mixdown(rift(3.0, True) * 0.8, drone(3.0, 41) * 0.7),
    'element/explosion': lambda: boom(1.8, 55),
    'element/solar_burst': lambda: mixdown(boom(1.3, 80) * 0.8, chime([660, 990], 1.0, 0.05, 1.5) * 0.5),
    'element/lightning': lambda: mixdown(zap(0.9), boom(0.9, 70) * 0.5),
    'element/ice_burst': lambda: ice(1.0),
    'element/soul_projectile': lambda: mixdown(whoosh(0.6, 300, 1500), drone(0.6, 220, dark=False) * 0.3 * exp_decay(int(SR * 0.6), 0.25)),
    'element/scythe_throw': lambda: np.concatenate([whoosh(0.18, 500, 4000)] * 4),
    'element/scythe_return': lambda: mixdown(whoosh(0.4, 400, 3500, up=False), impact(0.4, 400, 200, body=0.4, snap=0.5)),
    'element/nature_growth': lambda: mixdown(stft_filter(crackle(1.2, 500, 0.004), bandpass(200, 2500)), whoosh(1.2, 100, 900) * 0.6),
    'element/meteor_impact': lambda: mixdown(boom(2.4, 40) * 1.2, impact(2.4, 90, 25, body=1.5, snap=1.0)),
    'element/gravity': lambda: mixdown(drone(1.6, 33), rift(1.6, True) * 0.4),
    'element/earth_impact': lambda: mixdown(boom(1.6, 45), crackle(1.6, 700, 0.006) * 0.5 * exp_decay(int(SR * 1.6), 0.6)),
    'element/energy_fire': lambda: mixdown(whoosh(0.5, 1500, 9000), zap(0.5) * 0.3),
    'element/blood_rage': lambda: mixdown(drone(1.2, 60), whoosh(1.2, 100, 800) * 0.6),
    'element/fire_whoosh': lambda: fire(0.7),
}


def write_ogg(path, samples):
    samples = normalize(np.asarray(samples, dtype=np.float64))
    fade = min(len(samples), int(SR * 0.01))
    samples[-fade:] *= np.linspace(1, 0, fade)
    pcm = (samples * 32767).astype(np.int16)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        with wave.open(tmp.name, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp.name, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.unlink(tmp.name)


def main():
    events = {}
    for key, fn in RECIPES.items():
        path = os.path.join(ROOT, 'sounds', key + '.ogg')
        write_ogg(path, fn())
        event = key.replace('/', '.')
        events[event] = {
            'subtitle': f'subtitles.fantasyweapons.{event}',
            'sounds': [{'name': f'fantasyweapons:{key}', 'stream': key.endswith('charge_loop')}],
        }
    json_path = os.path.join(ROOT, 'sounds.json')
    if '--force-json' in sys.argv or not os.path.exists(json_path):
        with open(json_path, 'w') as f:
            json.dump(events, f, indent=2)
    print(f'wrote {len(RECIPES)} sounds')


if __name__ == '__main__':
    main()
