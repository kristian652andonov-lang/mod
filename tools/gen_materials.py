#!/usr/bin/env python3
"""
Material textures for custom VFX meshes (RGBA, tinted at runtime): ice crystal, rock, bark/vine.
Usage: python3 tools/gen_materials.py
"""
import numpy as np

from gen_textures import tileable_noise, coords, save_rgba


def ice():
    n = tileable_noise(64, octaves=4, seed=101)
    u, v = coords(64, 64)
    streak = 0.5 + 0.5 * np.sin((u * 3 + v * 9 + n * 2) * np.pi)
    lum = np.clip(0.72 + 0.25 * streak + 0.15 * (n - 0.5), 0, 1)
    alpha = np.clip(0.55 + 0.35 * n + 0.25 * (np.abs(u - 0.5) * 2) ** 4, 0, 1)
    arr = np.zeros((64, 64, 4))
    arr[..., 0] = lum * 235
    arr[..., 1] = lum * 248
    arr[..., 2] = 255
    arr[..., 3] = alpha * 255
    save_rgba('vfx/ice.png', arr)


def rock():
    n = tileable_noise(64, octaves=5, seed=202)
    n2 = tileable_noise(64, octaves=3, seed=203, base=8)
    cracks = np.exp(-((n2 - 0.5) ** 2) / 0.0015)
    lum = np.clip(0.5 + 0.45 * n - 0.35 * cracks, 0.15, 1)
    arr = np.zeros((64, 64, 4))
    arr[..., 0] = lum * 255
    arr[..., 1] = lum * 245
    arr[..., 2] = lum * 232
    arr[..., 3] = 255
    save_rgba('vfx/rock.png', arr)


def bark():
    w, h = 128, 32
    u, v = coords(w, h)
    n = tileable_noise(128, octaves=4, seed=303)[:h, :]
    ridges = 0.5 + 0.5 * np.sin((v * 7 + n * 1.5) * np.pi)
    lum = np.clip(0.45 + 0.4 * ridges + 0.2 * (n - 0.5), 0, 1)
    edge = np.clip(1 - np.abs(v - 0.5) * 2, 0, 1)
    shade = 0.55 + 0.45 * np.sqrt(edge)
    arr = np.zeros((h, w, 4))
    arr[..., 0] = lum * shade * 255
    arr[..., 1] = lum * shade * 255
    arr[..., 2] = lum * shade * 255
    arr[..., 3] = np.clip(edge * 4, 0, 1) * 255
    save_rgba('vfx/bark.png', arr)


if __name__ == '__main__':
    ice()
    rock()
    bark()
    print('materials written')
