#!/usr/bin/env python3
"""
Builds src/main/resources/assets/aigirlfriend/animations/entity/girlfriend.animation.json for the character rig in
art/character (root > body > waist > chest > head / arms with forearms, legs with shins).

  * The character pack's own clips (art/character/assets_companion/animations/...) are kept and renamed to the
    animation names the mod drives (see README "Asset contract"), optionally tweaked where in-game testing showed
    a problem (see TWEAKS).
  * Clips the pack does not have are authored here for the same rig.

Conventions (Bedrock/Blockbench degrees, as in the pack): arms/legs -X = swing forward, forearm -X = bend elbow,
shin +X = bend knee, chest/waist +X = lean forward, head +X = look down, body position in pixels (+Y up).
Right arm: -Y = inward, +Z = outward. Left arm mirrored. Gesture clips start and end at the rest pose.

Usage: python3 tools/build_animations.py   (from the repository root)
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACK = os.path.join(ROOT, "art", "character", "assets_companion", "animations", "entity", "girlfriend.animation.json")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "aigirlfriend", "animations", "entity", "girlfriend.animation.json")

BONES = ["body", "waist", "chest", "head", "right_arm", "right_forearm", "left_arm", "left_forearm",
         "right_leg", "right_shin", "left_leg", "left_shin"]


# ------------------------------------------------------------------------------------------------ helpers
def r(v):
    return [round(float(x), 3) for x in v]


def keys(frames, ease="catmullrom"):
    """frames: list of (time, [x, y, z]). Returns a Bedrock keyframe object."""
    if len(frames) == 1:
        return {"0.0": r(frames[0][1])}
    out = {}
    for t, v in frames:
        if ease == "linear":
            out[f"{t:.4g}"] = r(v)
        else:
            out[f"{t:.4g}"] = {"post": r(v), "lerp_mode": "catmullrom"}
    return out


def osc(length, base, amp, steps=8, phase=0.0, cycles=1):
    """Smooth loop: base + amp * sin(2*pi*cycles*t/length + phase) per axis (amp is a 3-vector)."""
    frames = []
    for i in range(steps + 1):
        t = length * i / steps
        s = math.sin(2 * math.pi * cycles * i / steps + phase)
        frames.append((t, [base[k] + amp[k] * s for k in range(3)]))
    return keys(frames)


def clip(length, bones, loop=True):
    a = {"animation_length": round(length, 4), "bones": bones}
    if loop is True:
        a["loop"] = True
    elif loop == "hold":
        a["loop"] = "hold_on_last_frame"
    return a


def gesture(length, tracks, rest_at=None):
    """One-shot clip: every track gets the rest pose added at t=0 and t=length unless given."""
    bones = {}
    for bone, chans in tracks.items():
        b = {}
        for chan, frames in chans.items():
            fr = list(frames)
            if fr[0][0] > 0:
                fr.insert(0, (0.0, [0, 0, 0]))
            if fr[-1][0] < length:
                fr.append((length, [0, 0, 0]))
            b[chan] = keys(fr)
        bones[bone] = b
    return clip(length, bones, loop=False)


def rot(frames):
    return {"rotation": frames}


def pos(frames):
    return {"position": frames}


SIT_LEGS = {
    "body": {"position": keys([(0, [0, -6, 0])])},
    "right_leg": rot(keys([(0, [-88, 4, 0])])), "left_leg": rot(keys([(0, [-88, -4, 0])])),
    "right_shin": rot(keys([(0, [86, 0, 0])])), "left_shin": rot(keys([(0, [86, 0, 0])])),
}


def merge(*dicts):
    out = {}
    for d in dicts:
        for bone, chans in d.items():
            out.setdefault(bone, {}).update(chans)
    return out


# ------------------------------------------------------------------------------------------------ pack clips
RENAME = {
    "idle": ["move.idle"],
    "walk": ["move.walk"],
    "run": ["move.run"],
    "swim": ["move.swim"],
    "sit": ["pose.sit"],
    "hug": ["interaction.hug"],
    "carried": ["interaction.carried", "pose.carried"],
    "lap": ["interaction.lap_sit", "pose.lap_sit"],
    "pet_head": ["interaction.pet_hair"],
    "pet_cheek": ["interaction.pet_cheek"],
    "lie_down": ["pose.lie_down"],
    "sleep": ["pose.sleep"],
    "cuddle": ["interaction.cuddle_bed", "interaction.sleep_together"],
    "stretch": ["action.stretch"],
    "wake_up": ["action.wake_up"],
}

# Adjustments found during in-game review (applied on top of the pack's clips).
TWEAKS = {}


def pack_clips():
    with open(PACK) as f:
        data = json.load(f)["animations"]
    out = {}
    for full, anim in data.items():
        short = full.split(".")[-1]
        for name in RENAME.get(short, []):
            a = json.loads(json.dumps(anim))
            for bone, chans in TWEAKS.get(name, {}).items():
                a["bones"].setdefault(bone, {}).update(chans)
            out[name] = a
    return out


# ------------------------------------------------------------------------------------------------ authored clips
def authored():
    A = {}
    breathe = lambda length, base=0.0, amp=1.0: rot(osc(length, [base, 0, 0], [amp, 0, 0]))

    # ---- idles
    A["move.idle_tired"] = clip(5.0, {
        "chest": breathe(5.0, 7, 1.5), "waist": rot(keys([(0, [3, 0, 0])])),
        "head": rot(osc(5.0, [16, 0, 0], [4, 0, 3])),
        "right_arm": rot(keys([(0, [-6, 0, 4])])), "left_arm": rot(keys([(0, [-6, 0, -4])])),
        "right_forearm": rot(keys([(0, [-8, 0, 0])])), "left_forearm": rot(keys([(0, [-8, 0, 0])])),
    })
    A["move.idle_sad"] = clip(5.0, {
        "chest": breathe(5.0, 9, 1.2), "head": rot(osc(5.0, [22, 0, 0], [2, 4, 0])),
        "right_arm": rot(keys([(0, [-18, -16, 2])])), "left_arm": rot(keys([(0, [-18, 16, -2])])),
        "right_forearm": rot(keys([(0, [-34, 0, -10])])), "left_forearm": rot(keys([(0, [-34, 0, 10])])),
    })
    A["move.idle_scared"] = clip(0.8, {
        "waist": rot(keys([(0, [6, 0, 0])])), "chest": rot(osc(0.4, [10, 0, 0], [0, 0, 1.2], steps=4)),
        "head": rot(osc(0.8, [6, 0, 0], [0, 6, 0], steps=6)),
        "right_arm": rot(keys([(0, [-48, -22, 6])])), "left_arm": rot(keys([(0, [-48, 22, -6])])),
        "right_forearm": rot(keys([(0, [-96, 0, -18])])), "left_forearm": rot(keys([(0, [-96, 0, 18])])),
        "right_leg": rot(keys([(0, [0, 0, 3])])), "left_leg": rot(keys([(0, [0, 0, -3])])),
    })
    A["move.hide"] = clip(0.8, {
        "body": pos(keys([(0, [0, -3, 0])])), "waist": rot(keys([(0, [16, 0, 0])])), "chest": rot(osc(0.4, [12, 0, 0], [0, 0, 1], steps=4)),
        "head": rot(keys([(0, [14, 0, 0])])),
        "right_arm": rot(keys([(0, [-128, -20, 10])])), "left_arm": rot(keys([(0, [-128, 20, -10])])),
        "right_forearm": rot(keys([(0, [-70, 0, -20])])), "left_forearm": rot(keys([(0, [-70, 0, 20])])),
        "right_leg": rot(keys([(0, [-28, 0, 0])])), "left_leg": rot(keys([(0, [-28, 0, 0])])),
        "right_shin": rot(keys([(0, [46, 0, 0])])), "left_shin": rot(keys([(0, [46, 0, 0])])),
    })
    # ---- locomotion variants derived from the pack's walk
    A["move.crouch"] = clip(2.0, {
        "body": pos(keys([(0, [0, -2, 0])])), "waist": rot(keys([(0, [20, 0, 0])])), "chest": breathe(2.0, 6, 1),
        "head": rot(keys([(0, [-18, 0, 0])])),
        "right_leg": rot(keys([(0, [-26, 0, 0])])), "left_leg": rot(keys([(0, [-26, 0, 0])])),
        "right_shin": rot(keys([(0, [38, 0, 0])])), "left_shin": rot(keys([(0, [38, 0, 0])])),
    })
    # ---- poses
    A["pose.sit_ground"] = clip(4.0, {
        "body": pos(keys([(0, [0, -11, 0])])), "chest": breathe(4.0, -4, 1),
        "right_leg": rot(keys([(0, [-82, 8, 0])])), "left_leg": rot(keys([(0, [-82, -8, 0])])),
        "right_shin": rot(keys([(0, [18, 0, 0])])), "left_shin": rot(keys([(0, [24, 0, 0])])),
        "right_arm": rot(keys([(0, [22, 0, 18])])), "left_arm": rot(keys([(0, [22, 0, -18])])),
        "right_forearm": rot(keys([(0, [-6, 0, 0])])), "left_forearm": rot(keys([(0, [-6, 0, 0])])),
        "head": rot(osc(4.0, [4, 0, 0], [0, 0, 3])),
    })
    A["pose.fainted"] = clip(2.0, {
        "body": {"rotation": keys([(0, [-90, 0, 0])]), "position": keys([(0, [0, -9.5, 0])])},
        "head": rot(keys([(0, [0, 25, 0])])),
        "right_arm": rot(keys([(0, [0, 0, 32])])), "left_arm": rot(keys([(0, [-10, 0, -20])])),
        "right_forearm": rot(keys([(0, [-20, 0, 0])])), "left_forearm": rot(keys([(0, [-30, 0, 0])])),
        "right_leg": rot(keys([(0, [0, 0, 6])])), "left_leg": rot(keys([(0, [-14, 0, -4])])),
        "left_shin": rot(keys([(0, [24, 0, 0])])),
    }, loop="hold")

    # ---- interactions
    A["interaction.cuddle"] = clip(3.0, {
        "waist": rot(keys([(0, [4, 0, 0])])), "chest": rot(osc(3.0, [12, 0, 0], [1, 0, 0])),
        "head": rot(osc(3.0, [10, 26, 12], [1.5, 2, 1])),
        "right_arm": rot(keys([(0, [-62, -34, 0])])), "left_arm": rot(keys([(0, [-62, 34, 0])])),
        "right_forearm": rot(keys([(0, [-10, 0, -62])])), "left_forearm": rot(keys([(0, [-10, 0, 62])])),
        "body": rot(osc(3.0, [0, 0, 0], [0, 3, 0])),
    })
    A["interaction.back_hug"] = clip(3.0, {
        "waist": rot(keys([(0, [3, 0, 0])])), "chest": rot(osc(3.0, [8, 0, 0], [1, 0, 0])),
        "head": rot(keys([(0, [6, -14, -10])])),
        "right_arm": rot(keys([(0, [-80, -34, 0])])), "left_arm": rot(keys([(0, [-80, 34, 0])])),
        "right_forearm": rot(keys([(0, [-8, 0, -64])])), "left_forearm": rot(keys([(0, [-8, 0, 64])])),
        "body": rot(osc(3.0, [0, 0, 0], [0, 2.5, 0])),
    })
    A["interaction.kiss"] = clip(2.7, {
        "chest": rot(keys([(0, [0, 0, 0]), (0.5, [-6, 0, 0]), (2.2, [-6, 0, 0]), (2.7, [0, 0, 0])])),
        "head": rot(keys([(0, [0, 0, 0]), (0.55, [-16, 0, 9]), (2.2, [-16, 0, 9]), (2.7, [0, 0, 0])])),
        "right_arm": rot(keys([(0, [-20, 0, 0]), (0.45, [-118, -26, 0]), (2.25, [-118, -26, 0]), (2.7, [-20, 0, 0])])),
        "left_arm": rot(keys([(0, [-20, 0, 0]), (0.45, [-118, 26, 0]), (2.25, [-118, 26, 0]), (2.7, [-20, 0, 0])])),
        "right_forearm": rot(keys([(0, [0, 0, 0]), (0.45, [-30, 0, -30]), (2.25, [-30, 0, -30]), (2.7, [0, 0, 0])])),
        "left_forearm": rot(keys([(0, [0, 0, 0]), (0.45, [-30, 0, 30]), (2.25, [-30, 0, 30]), (2.7, [0, 0, 0])])),
    }, loop="hold")
    A["interaction.kiss_cheek"] = clip(1.8, {
        "chest": rot(keys([(0, [0, 0, 0]), (0.4, [8, 0, 0]), (1.3, [8, 0, 0]), (1.8, [0, 0, 0])])),
        "head": rot(keys([(0, [0, 0, 0]), (0.4, [-8, 26, 8]), (1.3, [-8, 26, 8]), (1.8, [0, 0, 0])])),
        "right_arm": rot(keys([(0, [0, 0, 0]), (0.35, [24, 10, 4]), (1.4, [24, 10, 4]), (1.8, [0, 0, 0])])),
        "left_arm": rot(keys([(0, [0, 0, 0]), (0.35, [24, -10, -4]), (1.4, [24, -10, -4]), (1.8, [0, 0, 0])])),
        "right_forearm": rot(keys([(0, [0, 0, 0]), (0.35, [-40, 0, 0]), (1.4, [-40, 0, 0]), (1.8, [0, 0, 0])])),
        "left_forearm": rot(keys([(0, [0, 0, 0]), (0.35, [-40, 0, 0]), (1.4, [-40, 0, 0]), (1.8, [0, 0, 0])])),
    }, loop="hold")
    A["interaction.forehead_kiss"] = clip(2.3, {
        "head": rot(keys([(0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.8, [12, 0, 0]), (2.3, [0, 0, 0])])),
        "chest": rot(keys([(0, [0, 0, 0]), (0.5, [4, 0, 0]), (1.8, [4, 0, 0]), (2.3, [0, 0, 0])])),
        "right_arm": rot(keys([(0, [0, 0, 0]), (0.4, [-34, -26, 0]), (1.9, [-34, -26, 0]), (2.3, [0, 0, 0])])),
        "left_arm": rot(keys([(0, [0, 0, 0]), (0.4, [-34, 26, 0]), (1.9, [-34, 26, 0]), (2.3, [0, 0, 0])])),
        "right_forearm": rot(keys([(0, [0, 0, 0]), (0.4, [-92, 0, -14]), (1.9, [-92, 0, -14]), (2.3, [0, 0, 0])])),
        "left_forearm": rot(keys([(0, [0, 0, 0]), (0.4, [-92, 0, 14]), (1.9, [-92, 0, 14]), (2.3, [0, 0, 0])])),
    }, loop="hold")
    # she stands at the partner's right: partner is on HER left
    A["interaction.lean"] = clip(3.0, {
        "waist": rot(keys([(0, [0, 0, -6])])), "chest": rot(osc(3.0, [2, 0, -8], [1, 0, 0])),
        "head": rot(keys([(0, [6, 8, -22])])),
        "left_arm": rot(keys([(0, [-14, 12, -24])])), "left_forearm": rot(keys([(0, [-30, 0, 30])])),
        "right_arm": rot(keys([(0, [-30, -24, 0])])), "right_forearm": rot(keys([(0, [-60, 0, -10])])),
    })
    A["interaction.hold_hands"] = clip(1.0, {
        "left_arm": rot(keys([(0, [-14, 0, -16])])), "left_forearm": rot(keys([(0, [-14, 0, 0])])),
    })
    A["interaction.lead_by_hand"] = clip(1.0, {
        "left_arm": rot(keys([(0, [34, 0, -14])])), "left_forearm": rot(keys([(0, [-6, 0, 0])])),
        "head": rot(osc(1.0, [0, 24, 0], [0, 6, 0])), "chest": rot(keys([(0, [0, 10, 0])])),
    })
    sit_hands = {
        "right_arm": rot(keys([(0, [-26, -10, 4])])), "left_arm": rot(keys([(0, [-26, 10, -4])])),
        "right_forearm": rot(keys([(0, [-40, 0, -14])])), "left_forearm": rot(keys([(0, [-40, 0, 14])])),
        "chest": breathe(4.0, 4, 1), "head": rot(osc(4.0, [4, 0, 0], [0, 6, 0])),
    }
    A["interaction.sit_together"] = clip(4.0, merge(SIT_LEGS, sit_hands))
    for side, s in (("left", -1), ("right", 1)):
        # partner on her <side>: lean and rest head toward that side
        A[f"interaction.head_on_shoulder_{side}"] = clip(4.0, merge(SIT_LEGS, {
            "waist": rot(keys([(0, [0, 0, 6 * s])])), "chest": rot(osc(4.0, [3, 0, 10 * s], [1, 0, 0])),
            "head": rot(osc(4.0, [6, 6 * -s, 26 * s], [1, 0, 1])),
            "right_arm": rot(keys([(0, [-30, -12, 2])])), "left_arm": rot(keys([(0, [-30, 12, -2])])),
            "right_forearm": rot(keys([(0, [-42, 0, -12])])), "left_forearm": rot(keys([(0, [-42, 0, 12])])),
        }))
        near, far = ("left", "right") if side == "left" else ("right", "left")
        A[f"interaction.cuddle_sitting_{side}"] = clip(4.0, merge(SIT_LEGS, {
            "waist": rot(keys([(0, [0, 14 * -s, 8 * s])])), "chest": rot(osc(4.0, [6, 10 * -s, 12 * s], [1, 0, 0])),
            "head": rot(osc(4.0, [8, 10 * -s, 28 * s], [1, 0, 1])),
            f"{far}_arm": rot(keys([(0, [-62, -40 if far == "right" else 40, 0])])),
            f"{far}_forearm": rot(keys([(0, [-30, 0, -40 if far == "right" else 40])])),
            f"{near}_arm": rot(keys([(0, [-20, 0, 0])])), f"{near}_forearm": rot(keys([(0, [-50, 0, 0])])),
        }))

    # ---- gestures (start and end at rest)
    G = {}
    G["action.wave"] = gesture(2.0, {
        "right_arm": rot([(0.3, [-150, 0, 14]), (1.7, [-150, 0, 14])]),
        "right_forearm": rot([(0.3, [-24, 0, -22]), (0.6, [-24, 0, 22]), (0.9, [-24, 0, -22]), (1.2, [-24, 0, 22]), (1.5, [-24, 0, -18]), (1.75, [-10, 0, 0])]),
        "head": rot([(0.3, [0, 0, 7]), (1.7, [0, 0, 7])]),
    })
    G["action.wave_goodbye"] = gesture(2.6, {
        "right_arm": rot([(0.4, [-140, 0, 20]), (2.2, [-140, 0, 20])]),
        "right_forearm": rot([(0.4, [-20, 0, -26]), (0.8, [-20, 0, 26]), (1.2, [-20, 0, -26]), (1.6, [-20, 0, 26]), (2.0, [-20, 0, -10])]),
        "head": rot([(0.4, [6, 0, 9]), (2.2, [6, 0, 9])]),
    })
    G["action.yawn"] = gesture(2.6, {
        "right_arm": rot([(0.5, [-112, -30, 0]), (1.9, [-112, -30, 0])]),
        "right_forearm": rot([(0.5, [-108, 0, -12]), (1.9, [-108, 0, -12])]),
        "left_arm": rot([(0.5, [-10, 0, -40]), (1.9, [-10, 0, -40])]),
        "left_forearm": rot([(0.5, [-80, 0, 0]), (1.9, [-80, 0, 0])]),
        "head": rot([(0.6, [-18, 0, 0]), (1.8, [-20, 0, 4])]),
        "chest": rot([(0.6, [-6, 0, 0]), (1.8, [-6, 0, 0])]),
    })
    G["action.eat"] = gesture(2.0, {
        "right_arm": rot([(0.3, [-74, -28, 0]), (1.7, [-74, -28, 0])]),
        "right_forearm": rot([(0.3, [-92, 0, -10]), (0.55, [-80, 0, -10]), (0.8, [-92, 0, -10]), (1.05, [-80, 0, -10]), (1.3, [-92, 0, -10]), (1.7, [-80, 0, -10])]),
        "head": rot([(0.3, [8, 0, 0]), (0.55, [12, 0, 0]), (0.8, [8, 0, 0]), (1.05, [12, 0, 0]), (1.3, [8, 0, 0]), (1.7, [6, 0, 0])]),
    })
    G["action.drink"] = gesture(1.8, {
        "right_arm": rot([(0.35, [-90, -26, 0]), (1.45, [-100, -26, 0])]),
        "right_forearm": rot([(0.35, [-96, 0, -8]), (1.45, [-110, 0, -8])]),
        "head": rot([(0.4, [-14, 0, 0]), (1.4, [-26, 0, 0])]),
        "chest": rot([(0.4, [-4, 0, 0]), (1.4, [-6, 0, 0])]),
    })
    G["action.look_around"] = gesture(3.5, {
        "head": rot([(0.8, [0, 40, 0]), (1.6, [2, 40, 0]), (2.4, [0, -40, 0]), (3.0, [2, -38, 0])]),
        "chest": rot([(0.8, [0, 10, 0]), (1.6, [0, 10, 0]), (2.4, [0, -10, 0]), (3.0, [0, -10, 0])]),
    })
    G["action.look_at_animal"] = gesture(4.0, {
        "waist": rot([(0.6, [12, 0, 0]), (3.4, [12, 0, 0])]),
        "chest": rot([(0.6, [14, 0, 0]), (3.4, [14, 0, 0])]),
        "head": rot([(0.6, [6, 0, 14]), (2.0, [8, 0, -10]), (3.4, [6, 0, 14])]),
        "right_arm": rot([(0.6, [-24, -22, 0]), (3.4, [-24, -22, 0])]),
        "left_arm": rot([(0.6, [-24, 22, 0]), (3.4, [-24, 22, 0])]),
        "right_forearm": rot([(0.6, [-30, 0, 0]), (3.4, [-30, 0, 0])]),
        "left_forearm": rot([(0.6, [-30, 0, 0]), (3.4, [-30, 0, 0])]),
    })
    G["action.startled"] = gesture(1.4, {
        "body": pos([(0.12, [0, 1.5, 1.2]), (0.35, [0, 0, 1.6]), (1.0, [0, 0, 1.0])]),
        "chest": rot([(0.12, [-12, 0, 0]), (1.0, [-4, 0, 0])]),
        "head": rot([(0.12, [-12, 0, 0]), (1.0, [-2, 0, 0])]),
        "right_arm": rot([(0.12, [-92, -20, 18]), (1.0, [-70, -20, 10])]),
        "left_arm": rot([(0.12, [-92, 20, -18]), (1.0, [-70, 20, -10])]),
        "right_forearm": rot([(0.12, [-60, 0, 0]), (1.0, [-80, 0, 0])]),
        "left_forearm": rot([(0.12, [-60, 0, 0]), (1.0, [-80, 0, 0])]),
    })
    G["action.celebrate"] = gesture(3.0, {
        "right_arm": rot([(0.3, [-170, 0, 20]), (0.7, [-160, 0, 30]), (1.1, [-170, 0, 20]), (1.5, [-160, 0, 30]), (2.6, [-165, 0, 22])]),
        "left_arm": rot([(0.3, [-170, 0, -20]), (0.7, [-160, 0, -30]), (1.1, [-170, 0, -20]), (1.5, [-160, 0, -30]), (2.6, [-165, 0, -22])]),
        "right_forearm": rot([(0.3, [-10, 0, 0]), (2.6, [-10, 0, 0])]),
        "left_forearm": rot([(0.3, [-10, 0, 0]), (2.6, [-10, 0, 0])]),
        "body": pos([(0.25, [0, 0, 0]), (0.45, [0, 2.5, 0]), (0.65, [0, 0, 0]), (0.85, [0, 2.5, 0]), (1.05, [0, 0, 0]), (1.25, [0, 2.5, 0]), (1.45, [0, 0, 0])]),
        "head": rot([(0.3, [-10, 0, 0]), (2.6, [-8, 0, 0])]),
    })
    G["action.sad"] = gesture(4.0, {
        "head": rot([(0.8, [24, 0, 0]), (3.2, [26, 0, 4])]),
        "chest": rot([(0.8, [10, 0, 0]), (3.2, [10, 0, 0])]),
        "right_arm": rot([(0.8, [-16, -14, 0]), (3.2, [-16, -14, 0])]),
        "left_arm": rot([(0.8, [-16, 14, 0]), (3.2, [-16, 14, 0])]),
        "right_forearm": rot([(0.8, [-40, 0, -12]), (3.2, [-40, 0, -12])]),
        "left_forearm": rot([(0.8, [-40, 0, 12]), (3.2, [-40, 0, 12])]),
    })
    G["action.reach_hand"] = gesture(2.5, {
        "left_arm": rot([(0.5, [-42, 0, -10]), (2.0, [-42, 0, -10])]),
        "left_forearm": rot([(0.5, [-10, 0, 0]), (2.0, [-10, 0, 0])]),
        "head": rot([(0.5, [6, -10, -8]), (2.0, [6, -10, -8])]),
    })
    G["action.gift_happy"] = gesture(2.6, {
        "right_arm": rot([(0.3, [-44, -30, 0]), (2.2, [-44, -30, 0])]),
        "left_arm": rot([(0.3, [-44, 30, 0]), (2.2, [-44, 30, 0])]),
        "right_forearm": rot([(0.3, [-84, 0, -16]), (2.2, [-84, 0, -16])]),
        "left_forearm": rot([(0.3, [-84, 0, 16]), (2.2, [-84, 0, 16])]),
        "body": pos([(0.3, [0, 0, 0]), (0.5, [0, 2, 0]), (0.7, [0, 0, 0]), (0.9, [0, 2, 0]), (1.1, [0, 0, 0])]),
        "head": rot([(0.4, [-6, 0, 10]), (1.0, [-6, 0, -10]), (1.6, [-6, 0, 10]), (2.2, [-4, 0, 0])]),
    })
    G["action.shy"] = gesture(2.6, {
        "right_arm": rot([(0.4, [22, 14, 0]), (2.2, [22, 14, 0])]),
        "left_arm": rot([(0.4, [22, -14, 0]), (2.2, [22, -14, 0])]),
        "right_forearm": rot([(0.4, [-30, 0, 0]), (2.2, [-30, 0, 0])]),
        "left_forearm": rot([(0.4, [-30, 0, 0]), (2.2, [-30, 0, 0])]),
        "head": rot([(0.4, [18, 0, 12]), (1.3, [20, 0, 16]), (2.2, [18, 0, 12])]),
        "chest": rot([(0.4, [4, 8, 0]), (1.0, [4, -6, 0]), (1.6, [4, 8, 0]), (2.2, [4, 0, 0])]),
        "right_leg": rot([(0.6, [0, 0, -4]), (2.0, [0, 0, -4])]),
    })
    G["action.hurt"] = gesture(0.7, {
        "chest": rot([(0.1, [-14, 0, 0])]), "head": rot([(0.1, [-16, 0, 0])]),
        "right_arm": rot([(0.1, [-30, -20, 10])]), "left_arm": rot([(0.1, [-30, 20, -10])]),
    })
    G["action.defend"] = gesture(0.9, {
        "right_arm": rot([(0.15, [-86, -8, 0]), (0.5, [-86, -8, 0])]),
        "left_arm": rot([(0.15, [-86, 8, 0]), (0.5, [-86, 8, 0])]),
        "chest": rot([(0.15, [10, 0, 0]), (0.5, [6, 0, 0])]),
        "head": rot([(0.15, [-8, 0, 0])]),
    })
    G["action.talk"] = gesture(2.2, {
        "right_arm": rot([(0.4, [-30, -12, 6]), (1.1, [-22, -6, 10]), (1.8, [-30, -12, 6])]),
        "right_forearm": rot([(0.4, [-50, 0, -10]), (1.1, [-36, 0, 6]), (1.8, [-50, 0, -10])]),
        "head": rot([(0.5, [0, 0, 5]), (1.2, [2, 0, -4]), (1.8, [0, 0, 3])]),
    })
    G["action.pout"] = gesture(2.6, {
        "right_arm": rot([(0.3, [-48, -40, 0]), (2.3, [-48, -40, 0])]),
        "left_arm": rot([(0.3, [-44, 40, 0]), (2.3, [-44, 40, 0])]),
        "right_forearm": rot([(0.3, [-70, 0, -46]), (2.3, [-70, 0, -46])]),
        "left_forearm": rot([(0.3, [-70, 0, 46]), (2.3, [-70, 0, 46])]),
        "head": rot([(0.3, [-8, -34, 0]), (2.3, [-8, -34, 0])]),
    })
    G["action.blow_kiss"] = gesture(1.9, {
        "right_arm": rot([(0.4, [-96, -26, 0]), (0.8, [-96, -26, 0]), (1.25, [-80, 0, 30])]),
        "right_forearm": rot([(0.4, [-100, 0, -10]), (0.8, [-100, 0, -10]), (1.25, [-10, 0, 0])]),
        "head": rot([(0.4, [4, 0, 8]), (1.25, [-6, 0, 8])]),
    })
    G["action.giggle"] = gesture(1.9, {
        "right_arm": rot([(0.3, [-70, -30, 0]), (1.6, [-70, -30, 0])]),
        "right_forearm": rot([(0.3, [-104, 0, -10]), (1.6, [-104, 0, -10])]),
        "chest": rot([(0.3, [8, 0, 0]), (0.5, [4, 0, 0]), (0.7, [8, 0, 0]), (0.9, [4, 0, 0]), (1.1, [8, 0, 0]), (1.6, [4, 0, 0])]),
        "head": rot([(0.3, [12, 0, 8]), (1.6, [10, 0, 8])]),
    })
    G["action.wake_greeting"] = gesture(2.6, {
        "right_arm": rot([(0.5, [-150, 0, 14]), (2.1, [-150, 0, 14])]),
        "right_forearm": rot([(0.5, [-24, 0, -22]), (0.9, [-24, 0, 22]), (1.3, [-24, 0, -22]), (1.7, [-24, 0, 18])]),
        "left_arm": rot([(0.5, [-24, 20, 0]), (2.1, [-24, 20, 0])]),
        "left_forearm": rot([(0.5, [-90, 0, 10]), (2.1, [-90, 0, 10])]),
        "head": rot([(0.5, [-4, 0, 8]), (2.1, [-4, 0, 8])]),
    })
    G["action.relieved"] = gesture(2.6, {
        "chest": rot([(0.6, [12, 0, 0]), (2.0, [10, 0, 0])]),
        "head": rot([(0.6, [16, 0, 0]), (2.0, [12, 0, 0])]),
        "right_arm": rot([(0.6, [-38, -34, 0]), (2.0, [-38, -34, 0])]),
        "right_forearm": rot([(0.6, [-74, 0, -24]), (2.0, [-74, 0, -24])]),
    })
    G["action.ask_help"] = gesture(2.6, {
        "right_arm": rot([(0.3, [-70, -12, 0]), (2.3, [-70, -12, 0])]),
        "left_arm": rot([(0.3, [-70, 12, 0]), (2.3, [-70, 12, 0])]),
        "right_forearm": rot([(0.3, [-20, 0, 0]), (2.3, [-20, 0, 0])]),
        "left_forearm": rot([(0.3, [-20, 0, 0]), (2.3, [-20, 0, 0])]),
        "head": rot([(0.3, [-6, 0, 10]), (2.3, [-6, 0, 10])]),
    })
    A.update(G)
    return A


def main():
    anims = {}
    anims.update(authored())
    anims.update(pack_clips())  # the pack's own clips win where names overlap
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump({"format_version": "1.8.0", "animations": dict(sorted(anims.items()))}, f, indent=1)
    print(f"{len(anims)} animations written to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
