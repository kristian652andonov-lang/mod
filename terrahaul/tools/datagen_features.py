#!/usr/bin/env python3
"""Generates Terrahaul's configured features, placed features and NeoForge biome modifiers.

Usage: python3 tools/datagen_features.py
Output goes to src/main/resources/data and is committed.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'data')


def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def configured(name, ftype, config):
    write(f'terrahaul/worldgen/configured_feature/{name}.json', {"type": ftype, "config": config})


def placed(name, feature, placement):
    write(f'terrahaul/worldgen/placed_feature/{name}.json', {"feature": feature, "placement": placement})


def add_features(name, biomes, features, step):
    write(f'terrahaul/neoforge/biome_modifier/{name}.json',
          {"type": "neoforge:add_features", "biomes": biomes, "features": features, "step": step})


def remove_features(name, biomes, features, steps=None):
    obj = {"type": "neoforge:remove_features", "biomes": biomes, "features": features}
    if steps:
        obj["steps"] = steps
    write(f'terrahaul/neoforge/biome_modifier/{name}.json', obj)


def water():
    # both features are no-ops outside Terrahaul worlds (they need the terrain model)
    configured('surface_water', 'terrahaul:surface_water', {})
    placed('surface_water', 'terrahaul:surface_water', [])
    add_features('surface_water', '#minecraft:is_overworld', 'terrahaul:surface_water', 'lakes')
    configured('waterfall', 'terrahaul:waterfall', {})
    placed('waterfall', 'terrahaul:waterfall', [])
    add_features('waterfall', '#minecraft:is_overworld', 'terrahaul:waterfall', 'fluid_springs')


def main():
    water()


if __name__ == '__main__':
    main()
