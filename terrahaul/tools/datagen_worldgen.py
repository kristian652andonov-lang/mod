#!/usr/bin/env python3
"""Generates Terrahaul's core worldgen data (dimension type, noise settings, world preset,
density functions, noises and tags).

Usage: python3 tools/datagen_worldgen.py <vanilla-data-dir>
  <vanilla-data-dir> is the extracted "data/minecraft" directory of the Minecraft 1.21.1 client
  resources; it is only read to copy the vanilla overworld surface rule and spawn target.
The output is written to src/main/resources/data and is committed, so the script only needs to
be re-run after editing it.
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data')

MIN_Y = -512
HEIGHT = 2544          # -512 .. 2031, the full range Minecraft 1.21.1 supports at the top
SEA_LEVEL = 63
TOP_SLIDE = (1984, 2016)
BOTTOM_SLIDE = (MIN_Y, MIN_Y + 24)


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def terrain(output):
    return {"type": "terrahaul:terrain", "output": output, "seed_noise": "terrahaul:terrain_seed"}


def flat2d(fn):
    return {"type": "minecraft:flat_cache", "argument": {"type": "minecraft:cache_2d", "argument": fn}}


def noise(name, xz=1.0, y=1.0):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def fmin(a, b):
    return {"type": "minecraft:min", "argument1": a, "argument2": b}


def fmax(a, b):
    return {"type": "minecraft:max", "argument1": a, "argument2": b}


def clamp(a, lo, hi):
    return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}


def rc(inp, lo, hi, inside, outside):
    return {"type": "minecraft:range_choice", "input": inp, "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": inside, "when_out_of_range": outside}


def ycg(from_y, to_y, from_v, to_v):
    return {"type": "minecraft:y_clamped_gradient", "from_y": from_y, "to_y": to_y, "from_value": from_v, "to_value": to_v}


def main(vanilla):
    # ---- dimension type: expanded vertical range ---------------------------------------------
    with open(os.path.join(vanilla, 'dimension_type', 'overworld.json')) as f:
        dim = json.load(f)
    dim['min_y'] = MIN_Y
    dim['height'] = HEIGHT
    dim['logical_height'] = HEIGHT
    # client sky effects identical to the Overworld's except for a higher cloud layer (registered
    # by the client; clients without them fall back to the vanilla Overworld effects)
    dim['effects'] = 'terrahaul:overworld'
    write('terrahaul/dimension_type/overworld.json', dim)

    # ---- noises -------------------------------------------------------------------------------
    write('terrahaul/worldgen/noise/terrain_seed.json', {"firstOctave": 0, "amplitudes": [1.0]})

    # ---- named density functions ----------------------------------------------------------------
    write('terrahaul/worldgen/density_function/terrain/density.json',
          {"type": "minecraft:cache_once", "argument": terrain("density")})
    for out in ["preliminary", "continentalness", "erosion", "weirdness", "temperature", "humidity",
                "depth", "surface_height", "cave_allowance"]:
        write(f'terrahaul/worldgen/density_function/terrain/{out}.json', terrain(out))

    T = "terrahaul:terrain/density"
    P = "terrahaul:terrain/preliminary"
    # positive where caves must not open (under lake and river beds); see TerrainDensityFunction
    cave_guard = add(1.5, mul(-3.0, "terrahaul:terrain/cave_allowance"))
    write('terrahaul/worldgen/density_function/caves/guard.json', {"type": "minecraft:cache_once", "argument": cave_guard})
    G = "terrahaul:caves/guard"

    # vanilla underground cave expression with the Terrahaul terrain distance in place of
    # sloped_cheese: cheese caverns, cave layers, entrances, 2D spaghetti and pillars
    cheese = add(mul(4.0, {"type": "minecraft:square", "argument": noise("minecraft:cave_layer", 1.0, 8.0)}),
                 add(clamp(add(0.27, noise("minecraft:cave_cheese", 1.0, 0.6666666666666666)), -1.0, 1.0),
                     clamp(add(1.5, mul(-0.64, T)), 0.0, 0.5)))
    underground = fmax(
        fmin(fmin(cheese, "minecraft:overworld/caves/entrances"),
             add("minecraft:overworld/caves/spaghetti_2d", "minecraft:overworld/caves/spaghetti_roughness_function")),
        rc("minecraft:overworld/caves/pillars", -1000000.0, 0.03, -1000000.0, "minecraft:overworld/caves/pillars"))
    underground = fmax(underground, G)
    near_surface = rc(T, -1000000.0, -1.0, T, fmin(T, fmax(mul(5.0, "minecraft:overworld/caves/entrances"), G)))
    caved = rc(T, -1000000.0, 1.5625, near_surface, underground)
    write('terrahaul/worldgen/density_function/terrain/caved.json', caved)

    slid = add(0.1171875, mul(ycg(BOTTOM_SLIDE[0], BOTTOM_SLIDE[1], 0.0, 1.0),
                               add(-0.1171875, add(-0.078125, mul(ycg(TOP_SLIDE[0], TOP_SLIDE[1], 1.0, 0.0),
                                                                    add(0.078125, "terrahaul:terrain/caved"))))))
    main_density = {"type": "minecraft:squeeze", "argument": mul(0.64, {"type": "minecraft:interpolated", "argument": {
        "type": "minecraft:blend_density", "argument": slid}})}

    # noodle caves: vanilla shape, but evaluated only under the terrain surface (P >= 1, i.e. at
    # least 8 blocks deep) instead of the fixed vanilla band Y -60..320
    def noodle_part(inner, outside):
        return {"type": "minecraft:interpolated", "argument": rc(P, 1.0, 1000000.0, inner, outside)}
    noodle = rc(noodle_part(noise("minecraft:noodle"), -1.0), -1000000.0, 0.0, 64.0,
                add(noodle_part(add(-0.07500000000000001, mul(-0.025, noise("minecraft:noodle_thickness"))), 0.0),
                    mul(1.5, fmax({"type": "minecraft:abs", "argument": noodle_part(noise("minecraft:noodle_ridge_a", 2.6666666666666665, 2.6666666666666665), 0.0)},
                                  {"type": "minecraft:abs", "argument": noodle_part(noise("minecraft:noodle_ridge_b", 2.6666666666666665, 2.6666666666666665), 0.0)}))))
    noodle = fmax(noodle, G)
    write('terrahaul/worldgen/density_function/caves/noodle.json', noodle)

    final_density = {
        "type": "terrahaul:column_bounded",
        "terrain": "terrahaul:terrain/surface_height",
        "margin": 48,
        "argument": fmin(main_density, "terrahaul:caves/noodle")
    }

    with open(os.path.join(vanilla, 'worldgen', 'noise_settings', 'overworld.json')) as f:
        vanilla_settings = json.load(f)
    vr = vanilla_settings['noise_router']

    router = {
        "barrier": vr['barrier'],
        "fluid_level_floodedness": vr['fluid_level_floodedness'],
        "fluid_level_spread": vr['fluid_level_spread'],
        "lava": vr['lava'],
        "temperature": flat2d("terrahaul:terrain/temperature"),
        "vegetation": flat2d("terrahaul:terrain/humidity"),
        "continents": flat2d("terrahaul:terrain/continentalness"),
        "erosion": flat2d("terrahaul:terrain/erosion"),
        "depth": "terrahaul:terrain/depth",
        "ridges": flat2d("terrahaul:terrain/weirdness"),
        # must be the terrain function itself: NoiseChunkMixin reads the surface from it directly
        "initial_density_without_jaggedness": P,
        "final_density": final_density,
        "vein_toggle": vr['vein_toggle'],
        "vein_ridged": vr['vein_ridged'],
        "vein_gap": vr['vein_gap'],
    }

    spawn_target = [
        {"continentalness": [-0.11, 1.0], "depth": 0.0, "erosion": [-0.2, 1.0], "humidity": [-1.0, 1.0],
         "offset": 0.0, "temperature": [-0.45, 1.0], "weirdness": [-1.0, -0.16]},
        {"continentalness": [-0.11, 1.0], "depth": 0.0, "erosion": [-0.2, 1.0], "humidity": [-1.0, 1.0],
         "offset": 0.0, "temperature": [-0.45, 1.0], "weirdness": [0.16, 1.0]},
    ]

    surface_rule = {
        "type": "minecraft:sequence",
        "sequence": [
            {"type": "terrahaul:terrain_surface"},
            vanilla_settings['surface_rule'],
        ]
    }

    settings = {
        "sea_level": SEA_LEVEL,
        "disable_mob_generation": False,
        "aquifers_enabled": True,
        "ore_veins_enabled": True,
        "legacy_random_source": False,
        "default_block": {"Name": "minecraft:stone"},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "noise": {"min_y": MIN_Y, "height": HEIGHT, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": router,
        "spawn_target": spawn_target,
        "surface_rule": surface_rule,
    }
    write('terrahaul/worldgen/noise_settings/overworld.json', settings)

    # ---- world preset ---------------------------------------------------------------------------
    with open(os.path.join(vanilla, 'worldgen', 'world_preset', 'normal.json')) as f:
        preset = json.load(f)
    preset['dimensions']['minecraft:overworld'] = {
        "type": "terrahaul:overworld",
        "generator": {
            "type": "minecraft:noise",
            "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:overworld"},
            "settings": "terrahaul:overworld"
        }
    }
    write('terrahaul/worldgen/world_preset/terrahaul.json', preset)
    # listed in the "World Type" button of the Create World screen
    write('minecraft/tags/worldgen/world_preset/normal.json', {"replace": False, "values": ["terrahaul:terrahaul"]})


if __name__ == '__main__':
    main(sys.argv[1])
