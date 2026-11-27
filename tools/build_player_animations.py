"""Parametric keyframe source for player skill animations.

Run from the repository root: python tools/build_player_animations.py
Rewrites every document under assets/yumis_dungeon_and_fight/player_animation/
and validates bone names, key ordering, finiteness and loop closure.

Conventions (verified against the 26.2 HumanoidModel):
- arm/leg xRot in degrees: negative raises the limb forward or up, positive swings it back.
- arm yRot: with the arm raised forward, positive on right_arm / negative on left_arm turns it outward.
- arm zRot: positive on right_arm / negative on left_arm lifts the limb outward.
- body xRot: positive bends the torso forward (same direction as the vanilla crouch).
- stock clips leave the head available for free look; avatar clips can animate it explicitly.
- pos values are vanilla model units: 16 units equal one block.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/yumis_dungeon_and_fight/player_animation'
BONES = {'root', 'head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'held_item'}
EASINGS = {'smooth', 'linear', 'in', 'out', 'inout', 'hold'}


def stops(*frames):
    """frames: (millis, value[, easing]) -> raw channel list."""
    channel = []
    for frame in frames:
        t, v = int(frame[0]), frame[1]
        ease = frame[2] if len(frame) > 2 else 'smooth'
        key = [t, v]
        if ease != 'smooth':
            key.append(ease)
        channel.append(key)
    return channel


def signed(stops_list, bone):
    """Mirror limb keys authored using right-side angles."""
    sign = 1 if bone.startswith('right') else -1
    out = []
    for s in stops_list:
        key = [int(s[0]), sign * s[1]]
        if len(s) > 2:
            key.append(s[2])
        out.append(key)
    return out


def bone_doc(rot=None, pos=None):
    doc = {}
    if rot:
        doc['rot'] = rot
    if pos:
        doc['pos'] = pos
    return doc


def anim(bones, duration=None, loop=False):
    doc = {'loop': loop}
    if duration is not None:
        doc['duration'] = int(duration)
    doc['bones'] = bones
    return doc


def sem(bone, x=None, y=None, z=None, px=None, py=None, pz=None):
    """Limb channels with mirrored yaw and roll for the left side."""
    rot, pos = {}, {}
    if x is not None:
        rot['x'] = stops(*x)
    if y is not None:
        rot['y'] = signed(list(y), bone)
    if z is not None:
        rot['z'] = signed(list(z), bone)
    if px is not None:
        pos['x'] = stops(*px)
    if py is not None:
        pos['y'] = stops(*py)
    if pz is not None:
        pos['z'] = stops(*pz)
    return bone_doc(rot or None, pos or None)


def body(x=None, y=None, py=None):
    rot, pos = {}, {}
    if x is not None:
        rot['x'] = stops(*x)
    if y is not None:
        rot['y'] = stops(*y)
    if py is not None:
        pos['y'] = stops(*py)
    return bone_doc(rot or None, pos or None)


def root(py=None, x=None):
    rot, pos = {}, {}
    if x is not None:
        rot['x'] = stops(*x)
    if py is not None:
        pos['y'] = stops(*py)
    return bone_doc(rot or None, pos or None)


def sample_channel(keys, time):
    if time <= keys[0][0]:
        return keys[0][1]
    for lower, upper in zip(keys, keys[1:]):
        if time < upper[0]:
            u = (time - lower[0]) / (upper[0] - lower[0])
            easing = lower[2] if len(lower) > 2 else 'smooth'
            weight = {
                'smooth': u * u * (3 - 2 * u), 'linear': u, 'in': u * u,
                'out': 1 - (1 - u) ** 2, 'inout': (1 - math.cos(math.pi * u)) / 2, 'hold': 0,
            }[easing]
            return lower[1] + (upper[1] - lower[1]) * weight
    return keys[-1][1]


def sword_stroke(source, first, last, duration, use_root_yaw=False):
    """Retiming uses only the strike and keeps the equipped blade on the right arm."""
    def clip(*channels):
        times = {first, last}
        times.update(key[0] for keys in channels for key in keys if first < key[0] < last)
        return stops(*((round((time - first) * duration / (last - first)),
                        round(sum(sample_channel(keys, time) for keys in channels), 2))
                       for time in sorted(times)))

    bones = source['bones']
    arm_rotation = {axis: clip(keys) for axis, keys in bones['right_arm']['rot'].items()}
    if use_root_yaw:
        arm_rotation['y'] = clip(bones['right_arm']['rot']['y'], bones['root']['rot']['y'])
    result = {'right_arm': bone_doc(rot=arm_rotation)}
    if 'held_item' in bones:
        result['held_item'] = bone_doc(rot={axis: clip(keys) for axis, keys in bones['held_item']['rot'].items()})
    return result



# --------------------------------------------------------------------------
# Catalog: every castable skill opts in by file name.
# --------------------------------------------------------------------------

def build_catalog():
    catalog = {}

    # --- basic attacks -----------------------------------------------------
    # ATTACK: kendo-structured - a small coiling anticipation, the strike
    # snapping in at 250ms, then the blade SETTLING low across before return
    # instead of snapping straight back to neutral.
    catalog['ATTACK'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (110, 34, 'out'), (250, -108, 'in'), (380, -96), (560, 0)],
                         y=[(0, 0), (110, -26), (250, 36, 'in'), (380, 30), (560, 0)],
                         z=[(0, 0), (110, 18), (250, -12, 'in'), (560, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (110, -14), (560, 0)]),
        'body': body(x=[(0, 0), (110, -5), (250, 10, 'in'), (560, 0)],
                     y=[(0, 0), (110, -12), (250, 15, 'in'), (560, 0)]),
    }, duration=560)

    # --- swordmaster -------------------------------------------------------
    # ghost: 1s charge plateau (left hand sheath-braced, stance rooted), then a
    # 'hold'-preloaded release: full-body counter-twist cut with a root pop.
    catalog['ghost'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (120, 30, 'out'), (960, 30), (1080, 30, 'hold'), (1240, -132, 'in'), (1380, -118), (1600, 0)],
                         y=[(0, 0), (120, -26), (960, -26), (1240, 42, 'in'), (1600, 0)],
                         z=[(0, 0), (120, 14), (960, 14), (1240, -18, 'in'), (1600, 0)]),
        'left_arm': sem('left_arm',
                        x=[(0, 0), (120, -18), (960, -18), (1120, -34), (1600, 0)],
                        y=[(0, 0), (120, 18), (960, 18), (1600, 0)]),
        'body': body(x=[(0, 0), (120, 9), (960, 9), (1240, -9, 'in'), (1600, 0)],
                     y=[(0, 0), (120, 14), (960, 14), (1240, -18, 'in'), (1600, 0)]),
        'root': root(py=[(0, 0), (120, -1.2), (960, -1.2), (1080, -1.6), (1240, .8, 'in'), (1600, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (120, -16), (960, -16), (1240, 10, 'in'), (1600, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (120, 12), (960, 12), (1240, -8, 'in'), (1600, 0)]),
    }, duration=1600)
    # triple: three advancing cuts with three DIFFERENT blade roads:
    # high falling diagonal -> reverse rising -> low sweep, yaw rocking each dash.
    catalog['triple'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (100, -38, 'out'), (280, -118, 'in'), (420, -62), (560, -124, 'in'), (700, -70), (840, -108, 'in'), (1100, 0)],
                         y=[(0, 0), (100, -14), (280, 30), (420, -6), (560, 34), (700, -8), (840, 26), (1100, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (100, -22), (840, -26), (1100, 0)]),
        'body': body(x=[(0, 0), (100, 16), (840, 14), (1100, 0)],
                     y=[(0, 0), (100, 12), (280, -16, 'in'), (420, 10), (560, -16, 'in'), (700, 10), (840, -12, 'in'), (1100, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (100, -22), (840, -18), (1100, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (100, 18), (840, 14), (1100, 0)]),
    }, duration=1100)
    # wave: not a slam - a ground impalement. Both hands overhead, blade driven
    # into the earth, torso bowing with a fine tremor while the fissure runs.
    catalog['wave'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (120, -150, 'out'), (320, -16, 'in'), (560, -12), (840, 0)],
                         z=[(0, 0), (120, 12), (320, -10), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (120, -140, 'out'), (320, -8, 'in'), (560, -4), (840, 0)],
                        y=[(0, 0), (120, 20), (320, 14), (840, 0)]),
        'body': body(x=[(0, 0), (120, -8), (320, 20, 'in'), (420, 21), (520, 19), (560, 18), (840, 0)]),
        'root': root(py=[(0, 0), (120, .7), (320, -1.8, 'in'), (840, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (120, -8), (320, 14), (840, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (120, 10), (320, -12), (840, 0)]),
    }, duration=840)
    # mastery: a quiet sword salute - weapon lifted outward in presentation,
    # held, lowered. One-shot so a stray visual can never pin the body.
    catalog['mastery'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, -72, 'out'), (700, -72), (1000, 0)],
                         z=[(0, 0), (200, 26, 'out'), (700, 26), (1000, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -24), (700, -24), (1000, 0)]),
        'body': body(x=[(0, 0), (200, 5), (700, 5), (1000, 0)]),
    }, duration=1000)
    # MASTERY_WAVE: asura's basic-attack wave proc - a one-hand tide rising
    # from low-back to forward-up, palm riding the swell.
    catalog['MASTERY_WAVE'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (140, 24, 'out'), (320, -96, 'in'), (540, -88), (840, 0)],
                         y=[(0, 0), (140, -22), (320, 30, 'in'), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (140, -14), (320, -30), (840, 0)]),
        'body': body(x=[(0, 0), (140, -5), (320, 12, 'in'), (840, 0)],
                     y=[(0, 0), (140, 10), (320, -12), (840, 0)]),
        'root': root(py=[(0, 0), (140, -.6), (320, .5, 'in'), (840, 0)]),
    }, duration=840)
    # overdrive: two-stage awakening - arms cross tight over the chest, then
    # burst open: blade arm skyward (-168), off arm flung wide, chest opened.
    catalog['overdrive'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, -64, 'out'), (300, -168, 'in'), (760, -160), (1150, 0)],
                         y=[(0, 0), (200, 34), (300, -6), (1150, 0)],
                         z=[(0, 0), (200, -20), (300, 14), (1150, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -58, 'out'), (300, -104, 'in'), (760, -100), (1150, 0)],
                        z=[(0, 0), (200, 34), (300, 62), (1150, 0)]),
        'body': body(x=[(0, 0), (200, 10), (300, -12, 'in'), (760, -10), (1150, 0)]),
        'root': root(py=[(0, 0), (200, -.8), (300, 1.3, 'in'), (760, 1.2), (1150, 0)]),
    }, duration=1150)
    # rising: spiral dragon - coil with torso twist, then counter-twist into a
    # full uppercut with tucked airborne legs and a 2.6-block root jump.
    catalog['rising'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (110, 34, 'out'), (300, -172, 'in'), (460, -162), (780, 0)],
                         y=[(0, 0), (110, -20), (300, 28, 'in'), (780, 0)],
                         z=[(0, 0), (110, 12), (300, -20, 'in'), (780, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (110, -16), (300, 26), (780, 0)]),
        'body': body(x=[(0, 0), (110, 12), (300, -14, 'in'), (460, -12), (780, 0)],
                     y=[(0, 0), (110, 16), (300, -14, 'in'), (780, 0)]),
        'root': root(py=[(0, 0), (110, -1.6, 'out'), (300, 2.6, 'in'), (460, 2.2), (780, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (110, 10), (300, -26, 'in'), (780, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (110, -12), (300, 16, 'in'), (780, 0)]),
    }, duration=780)
    # draw: iai. Left hand pins the sheath (across +22), 390ms of stillness,
    # one step-cut with the right leg advancing, then a SLOW sheathing return.
    catalog['draw'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (80, 26, 'out'), (390, 26), (530, -116, 'in'), (720, -104), (940, 0)],
                         y=[(0, 0), (80, -24), (390, -24), (530, 44, 'in'), (940, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (80, -20), (390, -20), (530, -26), (940, 0)],
                        y=[(0, 0), (80, 22), (390, 22), (940, 0)]),
        'body': body(y=[(0, 0), (80, 16), (390, 16), (530, -18, 'in'), (940, 0)],
                    x=[(0, 0), (80, 6), (390, 6), (530, 10), (940, 0)]),
        'root': root(py=[(0, 0), (80, -1.2), (390, -1.2), (530, .6, 'in'), (940, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (80, -6), (530, -14, 'in'), (940, 0)]),
    }, duration=940)
    # dragon: four rush-cuts, alternating diagonals, the fourth RISES to a high
    # finisher instead of echoing the first three.
    catalog['dragon'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (130, -44, 'out'), (330, -128, 'in'), (520, -60), (700, -132, 'in'), (880, -56), (1060, -126, 'in'), (1240, -58), (1480, -146, 'in'), (1800, 0)],
                         y=[(0, 0), (130, -22), (330, 36), (520, -8), (700, 32), (880, -6), (1060, 34), (1240, -8), (1480, 18), (1800, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (130, -34), (1480, -40), (1800, 0)]),
        'body': body(x=[(0, 0), (130, 20), (1480, 16), (1800, 0)],
                     y=[(0, 0), (130, 14), (330, -18, 'in'), (520, 12), (700, -18, 'in'), (880, 12), (1060, -16, 'in'), (1240, 12), (1480, -8, 'in'), (1800, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (130, -30), (1480, -24), (1800, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (130, 22), (1480, 18), (1800, 0)]),
    }, duration=1800)
    # dance: figure-8 blade weave, arms trading the lead every 125ms with the
    # torso rolling between them; legs locked in a fencing stride.
    catalog['dance'] = anim({
        'right_arm': sem('right_arm', x=[(0, -64), (190, -112, 'in'), (380, -64)],
                         y=[(0, 24), (190, -20, 'in'), (380, 24)],
                         z=[(0, 10), (190, -6, 'in'), (380, 10)]),
        'left_arm': sem('left_arm', x=[(0, -34), (190, -86, 'in'), (380, -34)],
                        y=[(0, -14), (190, 18, 'in'), (380, -14)]),
        'body': body(x=[(0, 9), (190, 11, 'in'), (380, 9)],
                     y=[(0, 14), (190, -14, 'in'), (380, 14)]),
        'right_leg': sem('right_leg', x=[(0, -16), (380, -16)]),
        'left_leg': sem('left_leg', x=[(0, 13), (380, 13)]),
        'root': root(py=[(0, -.5), (380, -.5)]),
    }, duration=380, loop=True)
    # storm: no pirouette - a field commander holding the storm: blade arm
    # raised high, off arm swept behind, forward lean, slow surge breathing.
    catalog['storm'] = anim({
        'right_arm': sem('right_arm', x=[(0, -158), (320, -163, 'inout'), (640, -158)],
                         z=[(0, 16), (320, 19, 'inout'), (640, 16)]),
        'left_arm': sem('left_arm', x=[(0, 32), (320, 26, 'inout'), (640, 32)],
                        z=[(0, 14), (320, 11, 'inout'), (640, 14)]),
        'body': body(x=[(0, 13), (320, 15, 'inout'), (640, 13)],
                    y=[(0, -6), (320, 0, 'inout'), (640, -6)]),
        'right_leg': sem('right_leg', x=[(0, -18), (640, -18)]),
        'left_leg': sem('left_leg', x=[(0, 16), (640, 16)]),
        'root': root(py=[(0, -.7), (320, -.6, 'inout'), (640, -.7)]),
    }, duration=640, loop=True)
    catalog['blink'] = anim({
        'body': body(x=[(0, 0), (90, 10, 'out'), (260, 2), (420, 0)]),
        'root': root(py=[(0, 0), (90, -1.6, 'out'), (260, 0, 'in'), (420, 0)]),
        'right_arm': sem('right_arm', x=[(0, 0), (90, 20), (420, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (90, -16), (420, 0)]),
    }, duration=420)
    # rising_slay (破军斩龙击): rapid triple rush-cuts -> soaring uppercut -> heavy two-hand dragon-slaying slam.
    catalog['rising_slay'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (90, -52, 'out'), (220, -124, 'in'), (340, -172, 'in'), (480, -176), (560, -18, 'in'), (760, -14), (920, 0)],
                         y=[(0, 0), (90, -18), (220, 32), (340, 22), (480, -8), (560, 16, 'in'), (920, 0)],
                         z=[(0, 0), (340, -16), (480, 14), (560, -24, 'in'), (920, 0)]),
        'left_arm': sem('left_arm',
                        x=[(0, 0), (90, -24), (340, 18), (480, -152, 'out'), (560, -16, 'in'), (920, 0)],
                        y=[(0, 0), (480, 24), (560, 18), (920, 0)]),
        'body': body(x=[(0, 0), (90, 18), (260, 16), (340, -14, 'in'), (480, -18), (560, 28, 'in'), (760, 22), (920, 0)],
                     y=[(0, 0), (90, 18), (220, -20, 'in'), (340, 16, 'in'), (560, -14, 'in'), (920, 0)]),
        'root': root(py=[(0, 0), (120, -1.2), (340, 2.8, 'in'), (460, 3.2), (560, -1.8, 'in'), (760, -1.0), (920, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (120, -22), (340, -28), (560, -20), (920, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (120, 20), (340, 18), (560, 24), (920, 0)]),
    }, duration=920)
    # meteor_rise (流星·升): nimble flowing-stance coil -> soaring upward spiral crescent cut.
    catalog['meteor_rise'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (80, 32, 'out'), (220, -168, 'in'), (380, -142), (550, 0)],
                         y=[(0, 0), (80, -24), (220, 36, 'in'), (550, 0)],
                         z=[(0, 0), (80, 10), (220, -22, 'in'), (550, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (80, -20), (220, 28, 'in'), (550, 0)]),
        'body': body(x=[(0, 0), (80, 14), (220, -16, 'in'), (380, -10), (550, 0)],
                     y=[(0, 0), (80, 22), (220, -26, 'in'), (550, 0)]),
        'root': root(py=[(0, 0), (80, -1.4, 'out'), (240, 2.9, 'in'), (380, 2.0), (550, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (80, -14), (240, -30, 'in'), (550, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (80, 14), (240, 20, 'in'), (550, 0)]),
    }, duration=550)
    # meteor_leap (流星·跃): agile forward pounce arc -> forceful overhead plunging cleave.
    catalog['meteor_leap'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (120, -158, 'out'), (280, -170), (360, -22, 'in'), (500, -16), (650, 0)],
                         y=[(0, 0), (120, -14), (360, 24, 'in'), (650, 0)],
                         z=[(0, 0), (120, 16), (360, -18, 'in'), (650, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (120, -110, 'out'), (360, -18, 'in'), (650, 0)]),
        'body': body(x=[(0, 0), (120, 12), (280, 18), (360, 26, 'in'), (500, 18), (650, 0)],
                     y=[(0, 0), (120, 14), (360, -18, 'in'), (650, 0)]),
        'root': root(py=[(0, 0), (140, 2.6, 'out'), (280, 1.4), (360, -1.6, 'in'), (500, -.8), (650, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (140, -32), (360, -18), (650, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (140, 26), (360, 22), (650, 0)]),
    }, duration=650)
    # meteor_pierce (流星·刺): low fencer's stance -> lightning-fast triple forward sword thrusts.
    catalog['meteor_pierce'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (60, -48, 'out'), (120, -104, 'in'), (180, -54), (240, -108, 'in'), (300, -52), (360, -112, 'in'), (450, 0)],
                         y=[(0, 0), (120, 8), (240, -6), (360, 10), (450, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (60, 28), (360, 34), (450, 0)]),
        'body': body(x=[(0, 0), (60, 18), (360, 22), (450, 0)],
                     y=[(0, 0), (60, 24), (120, -18, 'in'), (180, 18), (240, -20, 'in'), (300, 16), (360, -22, 'in'), (450, 0)]),
        'root': root(py=[(0, 0), (60, -1.4), (360, -1.4), (450, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (60, -28), (360, -32), (450, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (60, 24), (360, 28), (450, 0)]),
    }, duration=450)

    # --- asura wave school -------------------------------------------------
    # ghost_orb: the orb condenses from the brands orbiting the body, so the
    # hand first GATHERS INWARD to the chest, then pushes the finished bead out.
    catalog['ghost_orb'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, 26, 'out'), (320, 26), (460, -96, 'in'), (560, -88, 'hold'), (840, 0)],
                         y=[(0, 0), (200, 34), (320, 34), (460, 6, 'in'), (560, 4), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -18), (460, -24), (560, -22), (840, 0)]),
        'body': body(x=[(0, 0), (200, -5), (320, -5), (460, 8, 'in'), (560, 7), (840, 0)]),
        'root': root(py=[(0, 0), (200, .5), (320, .5), (460, -.4, 'in'), (560, -.4), (840, 0)]),
    }, duration=840)
    # spirit_crescent: charged horizontal iai - coiled across the torso with a
    # quarter-turn away during the 1s build, then a full 180 sweep on release.
    catalog['spirit_crescent'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (150, 22, 'out'), (1000, 22), (1260, -140, 'in'), (1460, -124), (1720, 0)],
                         y=[(0, 0), (150, -38), (1000, -38), (1260, 52, 'in'), (1720, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (150, -24), (1000, -24), (1260, -36), (1720, 0)],
                        y=[(0, 0), (150, 20), (1000, 20), (1720, 0)]),
        'body': body(x=[(0, 0), (150, 6), (1000, 6), (1260, 4, 'in'), (1720, 0)],
                     y=[(0, 0), (150, 22), (1000, 22), (1260, -24, 'in'), (1720, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (150, -10), (1720, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (150, 10), (1720, 0)]),
    }, duration=1720)
    # ice_wave: a flat, calm lateral line - arm extends sideways then carves a
    # low horizontal arc across the front, torso pivoting through the sweep.
    catalog['ice_wave'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, -84, 'out'), (300, -96, 'in'), (620, -84), (780, 0)],
                         y=[(0, 0), (160, -52), (300, 46, 'in'), (620, 30), (780, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -20), (780, 0)]),
        'body': body(x=[(0, 0), (160, 6), (300, 8, 'in'), (780, 0)],
                     y=[(0, 0), (160, 18), (300, -14, 'in'), (780, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (160, -12), (780, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (160, 12), (780, 0)]),
    }, duration=780)
    # fire_wave: opposite of the ground impalement - an ERUPTION. The arm whips
    # from low-back up through vertical, chest opening to the sky.
    catalog['fire_wave'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (180, 40, 'out'), (400, -160, 'in'), (620, -148), (900, 0)],
                         y=[(0, 0), (180, -26), (400, 8, 'in'), (900, 0)],
                         z=[(0, 0), (180, 16), (400, -14, 'in'), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (180, -30), (400, -96, 'in'), (620, -88), (900, 0)]),
        'body': body(x=[(0, 0), (180, 12), (400, -14, 'in'), (620, -12), (900, 0)]),
        'root': root(py=[(0, 0), (180, -1.0), (400, 1.1, 'in'), (620, .8), (900, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (180, 12), (400, -18, 'in'), (900, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (180, -14), (400, 10, 'in'), (900, 0)]),
    }, duration=900)
    # agni: immovable king - palms pressed into a mudra at the chest, horse
    # stance rooted, nothing but breath. The caster is control-locked, so the
    # held loop is the intended performance.
    catalog['agni'] = anim({
        'right_arm': sem('right_arm', x=[(0, -44), (450, -48, 'inout'), (900, -44)],
                         y=[(0, 38), (900, 38)],
                         z=[(0, -12), (900, -12)]),
        'left_arm': sem('left_arm', x=[(0, -40), (450, -44, 'inout'), (900, -40)],
                        y=[(0, 34), (900, 34)]),
        'body': body(x=[(0, 3), (450, 5, 'inout'), (900, 3)]),
        'right_leg': sem('right_leg', x=[(0, -14), (900, -14)]),
        'left_leg': sem('left_leg', x=[(0, 14), (900, 14)]),
        'root': root(py=[(0, -.5), (900, -.5)]),
    }, duration=900, loop=True)
    # wave_eye: a curtain-opening reveal - arms crossed high over the head,
    # then flung wide and DOWN as the dark eye opens; buff persists unpinned.
    catalog['wave_eye'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (180, -150, 'out'), (360, -150), (560, -28, 'in'), (900, 0)],
                         y=[(0, 0), (180, 34), (360, 34), (560, -6, 'in'), (900, 0)],
                         z=[(0, 0), (180, -16), (560, 74, 'out'), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (180, -150, 'out'), (360, -150), (560, -28, 'in'), (900, 0)],
                        y=[(0, 0), (180, 34), (360, 34), (560, -6, 'in'), (900, 0)],
                        z=[(0, 0), (180, -16), (560, 74, 'out'), (900, 0)]),
        'body': body(x=[(0, 0), (180, -6), (360, -6), (560, 10, 'in'), (900, 0)]),
        'root': root(py=[(0, 0), (180, 1.0), (360, 1.0), (560, -.8, 'in'), (900, 0)]),
    }, duration=900)
    catalog['wave_eye_gear'] = anim({
        'right_arm': sem('right_arm', x=[(0, -80), (240, -100, 'in'), (620, -80)], y=[(0, 26), (240, 40, 'in'), (620, 26)]),
        'left_arm': sem('left_arm', x=[(0, -80), (240, -100, 'in'), (620, -80)], y=[(0, 26), (240, 40, 'in'), (620, 26)]),
        'body': body(x=[(0, 6), (240, 10), (620, 6)]),
    }, duration=620, loop=True)

    # --- soul bender -------------------------------------------------------
    # ghost_field: draw the array - palm swept up, then pressed to the ground
    # with a held beat while the ghosts rise; the field runs on unpinned.
    catalog['ghost_field'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (140, -40, 'out'), (340, -52, 'in'), (720, -48), (1300, 0)],
                         y=[(0, 0), (140, 30), (340, -8, 'in'), (720, -6), (1300, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (140, -18), (340, -12), (1300, 0)]),
        'body': body(x=[(0, 0), (140, -6), (340, 18, 'in'), (540, 20), (720, 17), (1300, 0)]),
        'root': root(py=[(0, 0), (140, .4), (340, -1.8, 'in'), (720, -1.4), (1300, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (340, 10, 'in'), (720, 8), (1300, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (340, -12, 'in'), (720, -10), (1300, 0)]),
    }, duration=1300)
    # soul_slash: a devouring cut - the blade hooks INWARD first (swallowing
    # the spirit), then backhands the release back out. Swallow, spit.
    catalog['soul_slash'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, -78, 'out'), (380, -118, 'in'), (460, -110, 'hold'), (720, 0)],
                         y=[(0, 0), (200, 44), (380, -22, 'in'), (460, -18), (720, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -16), (460, -20), (720, 0)]),
        'body': body(x=[(0, 0), (200, 6), (380, 12, 'in'), (460, 10), (720, 0)],
                     y=[(0, 0), (200, -14), (380, 16, 'in'), (460, 13), (720, 0)]),
        'root': root(py=[(0, 0), (200, .4), (380, -.5, 'in'), (460, -.5), (720, 0)]),
    }, duration=720)
    # moonlight_slash: swift advancing dark-moon crescent cut with sweeping follow-through.
    catalog['moonlight_slash'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (70, -132, 'out'), (170, -48, 'in'), (240, -54), (320, 0)],
                         y=[(0, 0), (70, 42, 'out'), (170, -58, 'in'), (240, -46), (320, 0)],
                         z=[(0, 0), (70, 14), (170, -20), (320, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (70, -26), (170, -12), (320, 0)]),
        'body': body(x=[(0, 0), (70, 14), (170, 10), (320, 0)],
                     y=[(0, 0), (70, -22, 'out'), (170, 26, 'in'), (320, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (70, -20), (170, 12), (320, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (70, 18), (170, -14), (320, 0)]),
    }, duration=320)
    # tombstone: call the sky, then CONDUCT the fall - two pressing waves ride
    # the ten-stone cadence, torso bowing with every push.
    catalog['tombstone'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (240, -158, 'out'), (520, 8, 'in'), (620, 6), (900, 12, 'in'), (1120, 8), (1400, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (240, -158, 'out'), (520, 8, 'in'), (620, 6), (900, 12, 'in'), (1120, 8), (1400, 0)]),
        'body': body(x=[(0, 0), (240, -8), (520, 20, 'in'), (620, 17), (900, 24, 'in'), (1120, 20), (1400, 0)]),
        'root': root(py=[(0, 0), (240, .9), (520, -1.6, 'in'), (900, -2.2, 'in'), (1400, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (240, -8), (520, 12, 'in'), (1400, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (240, 10), (520, -14, 'in'), (1400, 0)]),
    }, duration=1400)
    # phantom_slash: the afterimage step - body snaps into a deep low fold,
    # blade arm DRAGGING behind, and holds the silhouette through the dash.
    catalog['phantom_slash'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (80, 34, 'out'), (560, 30), (700, 0)],
                         y=[(0, 0), (80, 18), (560, 14), (700, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (80, -88, 'out'), (560, -82), (700, 0)]),
        'body': body(x=[(0, 0), (80, 26, 'out'), (560, 24), (700, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (80, -26, 'out'), (560, -24), (700, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (80, 18, 'out'), (560, 16), (700, 0)]),
        'root': root(py=[(0, 0), (80, -1.6, 'out'), (560, -1.4), (700, 0)]),
    }, duration=700)
    # kalla: an ignition flick - the left hand claws back to gather the dark
    # flame, then flings it forward. Kept at 620ms so per-bolt re-triggers
    # read as quick flicks of the still-burning hand.
    catalog['kalla'] = anim({
        'left_arm': sem('left_arm', x=[(0, 0), (140, 38, 'out'), (300, -96, 'in'), (460, -88), (620, 0)],
                        y=[(0, 0), (140, -26), (300, 10, 'in'), (620, 0)]),
        'right_arm': sem('right_arm', x=[(0, 0), (140, -12), (620, 0)]),
        'body': body(x=[(0, 0), (140, -6), (300, 9, 'in'), (620, 0)]),
        'root': root(py=[(0, 0), (140, -.5), (300, .4, 'in'), (620, 0)]),
    }, duration=620)
    # blache: the seventh ghost. Arms raised through a trembling channel, and
    # the crash lands exactly on the finisher at ~1180ms with a deep bow.
    catalog['blache'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (300, -168, 'out'), (700, -168), (880, -164), (1050, -168), (1180, -14, 'in'), (1700, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (300, -168, 'out'), (700, -168), (880, -164), (1050, -168), (1180, -14, 'in'), (1700, 0)]),
        'body': body(x=[(0, 0), (300, -10), (700, -10), (1050, -10), (1180, 26, 'in'), (1700, 0)]),
        'root': root(py=[(0, 0), (300, 1.3), (700, 1.3), (1050, 1.3), (1180, -2.8, 'in'), (1700, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (300, -10), (1050, -10), (1180, 14, 'in'), (1700, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (300, 12), (1050, 12), (1180, -12, 'in'), (1700, 0)]),
    }, duration=1700)

    # --- berserker ----------------------------------------------------------
    # frenzy: the rage tears open - arms rip outward from center, then a fine
    # tremble while the blood aura climbs, chest first bowed back then crushed
    # forward.
    catalog['frenzy'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, -116, 'out'), (300, -96, 'in'), (380, -104), (460, -94), (540, -102), (620, -96), (900, 0)],
                         z=[(0, 0), (160, 34, 'out'), (300, 48, 'in'), (380, 52), (460, 50), (540, 54), (620, 50), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -116, 'out'), (300, -96, 'in'), (380, -104), (460, -94), (540, -102), (620, -96), (900, 0)],
                        z=[(0, 0), (160, 34, 'out'), (300, 48, 'in'), (380, 52), (460, 50), (540, 54), (620, 50), (900, 0)]),
        'body': body(x=[(0, 0), (160, -12), (300, -8), (520, -6), (900, 0)]),
        'root': root(py=[(0, 0), (160, -1.2), (520, -1.2), (900, 0)]),
    }, duration=900)
    # mountain_crash: a single-arm hop chop - short crouch, modest hop, ONE
    # arm carrying the whole fall while the other stays low in guard.
    catalog['mountain_crash'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (140, -52, 'out'), (380, -165, 'out'), (620, -10, 'in'), (1000, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (140, -22), (620, -18), (1000, 0)]),
        'body': body(x=[(0, 0), (140, 14), (380, -10, 'out'), (620, 24, 'in'), (1000, 0)]),
        'root': root(py=[(0, 0), (140, -1.4, 'out'), (380, 3.2, 'out'), (620, -2.0, 'in'), (1000, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (140, 12), (380, -24), (620, 12, 'in'), (1000, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (140, -12), (380, 18), (620, -12, 'in'), (1000, 0)]),
    }, duration=1000)
    # blood_hand: a claw - the hand shoots open, CLENCHES, and drags the prize
    # back into the body. Reach, seize, yank.
    catalog['blood_hand'] = anim({
        'left_arm': sem('left_arm', x=[(0, 0), (120, 24, 'out'), (220, -96, 'in'), (340, -90), (420, -34, 'out'), (600, 0)],
                        y=[(0, 0), (120, -18), (220, 8, 'in'), (340, 6), (420, 40, 'out'), (600, 0)],
                        z=[(0, 0), (220, 12, 'in'), (420, 8), (600, 0)]),
        'right_arm': sem('right_arm', x=[(0, 0), (120, -14), (600, 0)]),
        'body': body(x=[(0, 0), (120, -6), (220, 8, 'in'), (420, -4, 'out'), (600, 0)],
                     y=[(0, 0), (220, 8, 'in'), (420, -10), (600, 0)]),
        'root': root(py=[(0, 0), (220, .5, 'in'), (420, -.6, 'out'), (600, 0)]),
    }, duration=600)
    # blood_rage: the ground answers - arms flung wide-low, both fists
    # hammered down at 240ms, then two surging pulls as the pillars climb.
    catalog['blood_rage'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (120, -36, 'out'), (240, 22, 'in'), (340, 18), (520, -62, 'out'), (640, -54), (760, -84, 'out'), (1000, 0)],
                         z=[(0, 0), (120, 40, 'out'), (240, 24), (1000, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (120, -36, 'out'), (240, 22, 'in'), (340, 18), (520, -62, 'out'), (640, -54), (760, -84, 'out'), (1000, 0)],
                        z=[(0, 0), (120, 40, 'out'), (240, 24), (1000, 0)]),
        'body': body(x=[(0, 0), (120, -6), (240, 18, 'in'), (340, 16), (520, 4, 'out'), (1000, 0)]),
        'root': root(py=[(0, 0), (120, .6), (240, -1.2, 'in'), (520, -.4), (1000, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (240, 10, 'in'), (1000, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (240, -10, 'in'), (1000, 0)]),
    }, duration=1000)
    # blood_blade: the blade is paid for in blood - claw into the sternum,
    # rip the weapon out sideways, spend it on one committed diagonal.
    catalog['blood_blade'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, -30, 'out'), (300, -70, 'in'), (340, -64), (440, -34), (560, -120, 'in'), (660, 0)],
                         y=[(0, 0), (160, 40), (300, -26, 'in'), (440, -8), (560, 32, 'in'), (660, 0)],
                         z=[(0, 0), (160, -14), (300, 20, 'in'), (560, -12), (660, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -20), (660, 0)]),
        'body': body(x=[(0, 0), (160, 6), (300, 8, 'in'), (560, 14, 'in'), (660, 0)],
                     y=[(0, 0), (160, -12), (300, 16, 'in'), (560, -14), (660, 0)]),
        'root': root(py=[(0, 0), (160, -.5), (300, .4, 'in'), (560, -.6, 'in'), (660, 0)]),
    }, duration=660)
    # blood_eruption: crash's big brother - a full two-hand SKY rend: deep
    # crouch, arms thrown wide aloft, and a landing that plows the ground.
    catalog['blood_eruption'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (180, -60, 'out'), (480, -172, 'out'), (860, -10, 'in'), (1080, -6), (1380, 0)],
                         z=[(0, 0), (180, 18), (480, 26), (860, 8), (1380, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (180, -60, 'out'), (480, -172, 'out'), (860, -10, 'in'), (1080, -6), (1380, 0)],
                        z=[(0, 0), (180, 18), (480, 26), (860, 8), (1380, 0)]),
        'body': body(x=[(0, 0), (180, 18), (480, -14, 'out'), (860, 28, 'in'), (1080, 26), (1380, 0)]),
        'root': root(py=[(0, 0), (180, -2.6, 'out'), (480, 6.4, 'out'), (860, -2.6, 'in'), (1380, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (180, 14), (480, -26), (860, 14, 'in'), (1380, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (180, -16), (480, 22), (860, -14, 'in'), (1380, 0)]),
    }, duration=1380)
    # blood_sword: a possession rite, not a held stance - arms shudder up into
    # a crossed overhead grip as the demon takes hold, the whole body quakes,
    # then sinks into a low wide guard. The 60s aura plays out unpinned.
    catalog['blood_sword'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (300, -130, 'out'), (480, -136), (600, -128), (760, -136), (900, -120), (1300, -58, 'inout'), (1600, 0)],
                         y=[(0, 0), (300, 38), (900, 36), (1300, 14), (1600, 0)],
                         z=[(0, 0), (300, -16), (900, -14), (1300, 18), (1600, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (300, -130, 'out'), (480, -124), (600, -134), (760, -124), (900, -132), (1300, -54, 'inout'), (1600, 0)],
                        y=[(0, 0), (300, 36), (900, 34), (1300, 12), (1600, 0)]),
        'body': body(x=[(0, 0), (300, -8), (760, 6), (1000, 10), (1300, 8), (1600, 0)]),
        'root': root(py=[(0, 0), (300, .8), (700, .2), (800, .9), (1000, .3), (1300, -.8), (1600, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (300, -10), (1000, -8), (1600, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (300, 12), (1000, 10), (1600, 0)]),
    }, duration=1600)

    # --- mage ---------------------------------------------------------------
    # magic_star: twirl the conjure - the hand inscribes a small inward arc
    # gathering the star, then a crisp two-finger flick sends it spinning.
    catalog['magic_star'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (120, -40, 'out'), (240, -70), (400, -104, 'in'), (560, 0)],
                         y=[(0, 0), (120, 30), (240, -12), (400, 4, 'in'), (560, 0)],
                         z=[(0, 0), (240, 12), (400, 10), (560, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (240, -16), (560, 0)]),
        'body': body(x=[(0, 0), (240, -3), (400, 7, 'in'), (560, 0)]),
        'root': root(py=[(0, 0), (400, -.4, 'in'), (560, 0)]),
    }, duration=560)
    # dragon_fang: stacked palms - both hands gather to the side, then press
    # forward TOGETHER (right over left) as the fangs erupt ahead.
    catalog['dragon_fang'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (110, 34, 'out'), (240, -100, 'in'), (340, -94), (520, 0)],
                         y=[(0, 0), (110, 22), (240, 10, 'in'), (520, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (110, -30, 'out'), (240, -86, 'in'), (340, -80), (520, 0)],
                        y=[(0, 0), (110, 26), (240, 14, 'in'), (520, 0)]),
        'body': body(x=[(0, 0), (110, -6), (240, 14, 'in'), (520, 0)],
                     y=[(0, 0), (110, 8), (240, -4), (520, 0)]),
        'root': root(py=[(0, 0), (110, .4), (240, -.6, 'in'), (520, 0)]),
    }, duration=520)
    # jack_bomb: the lob keeps its side-wind, but gains the signature beat -
    # a recoil half-step with the off arm raised, bracing for the boom.
    catalog['jack_bomb'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, 22, 'out'), (340, -84, 'in'), (480, -66), (640, 0)],
                         y=[(0, 0), (160, 40), (340, -6, 'in'), (640, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -12), (400, -58, 'out'), (640, 0)]),
        'body': body(x=[(0, 0), (160, -5), (340, 8, 'in'), (400, -4, 'out'), (640, 0)]),
        'root': root(py=[(0, 0), (340, .4, 'in'), (400, -.5, 'out'), (640, 0)]),
    }, duration=640)
    # herder: trace the circle, then point at the rise - the summoning arc
    # drawn down and forward, one crisp gesture commanding the spot. Kept at
    # 900ms to finish inside the short summon-visual window.
    catalog['herder'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (140, -20, 'out'), (260, 34), (300, 34), (460, -92, 'in'), (600, -84), (900, 0)],
                         y=[(0, 0), (140, 26), (260, 4), (300, 4), (460, 8, 'in'), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (140, -14), (900, 0)]),
        'body': body(x=[(0, 0), (140, -4), (260, 10), (300, 10), (460, 2, 'in'), (900, 0)]),
        'root': root(py=[(0, 0), (260, -.8), (300, -.8), (460, .4, 'in'), (900, 0)]),
    }, duration=900)

    # --- summoner -----------------------------------------------------------
    # Each contract gets its own entrance rite. The shared arms-up toss is gone.
    # four_spirits: a V-offering - arms swept up and wide open to all four
    # directions at once, the pact presented to the elements themselves.
    catalog['four_spirits'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (240, -128, 'out'), (520, -120), (840, 0)],
                         z=[(0, 0), (240, 30, 'out'), (520, 28), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (240, -120, 'out'), (520, -112), (840, 0)],
                        z=[(0, 0), (240, 26, 'out'), (520, 24), (840, 0)]),
        'body': body(x=[(0, 0), (240, -8), (520, -7), (840, 0)]),
        'root': root(py=[(0, 0), (240, .8), (520, .7), (840, 0)]),
    }, duration=840)
    # louise: the ice knight deserves a bow - the arm carves a low presenting
    # arc, then opens palm toward her rise spot. Slow curves, a host's grace.
    catalog['louise'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, -26, 'out'), (420, -64), (640, -86, 'out'), (840, 0)],
                         y=[(0, 0), (200, 36), (420, 16), (640, -4, 'out'), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -18), (840, 0)]),
        'body': body(x=[(0, 0), (200, 4), (420, 8), (840, 0)],
                     y=[(0, 0), (200, 10), (420, -8), (840, 0)]),
        'root': root(py=[(0, 0), (420, .5), (840, 0)]),
    }, duration=840)
    # sandor: something HEAVY comes out of the ground - both fists raised high,
    # then hammered down at the flanks, the body bowing with the pull.
    catalog['sandor'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, -140, 'out'), (340, 16, 'in'), (520, 12), (780, 0)],
                         z=[(0, 0), (160, 20), (340, 34), (520, 32), (780, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -140, 'out'), (340, 16, 'in'), (520, 12), (780, 0)],
                        z=[(0, 0), (160, 20), (340, 34), (520, 32), (780, 0)]),
        'body': body(x=[(0, 0), (160, -8), (340, 18, 'in'), (520, 16), (780, 0)]),
        'root': root(py=[(0, 0), (160, .6), (340, -1.6, 'in'), (780, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (340, 10, 'in'), (780, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (340, -10, 'in'), (780, 0)]),
    }, duration=780)
    # echeverria: a royal salute - one arm across the chest in fealty, the
    # other extended forward palm-UP, offering the throne it rises to.
    catalog['echeverria'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (240, -96, 'out'), (620, -88), (900, 0)],
                         y=[(0, 0), (240, 10), (620, 8), (900, 0)],
                         z=[(0, 0), (240, -14, 'out'), (620, -12), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (240, -44, 'out'), (620, -40), (900, 0)],
                        y=[(0, 0), (240, 36), (620, 34), (900, 0)]),
        'body': body(x=[(0, 0), (240, 4), (900, 0)],
                     y=[(0, 0), (240, -6), (900, 0)]),
        'root': root(py=[(0, 0), (240, .4), (900, 0)]),
    }, duration=900)
    # kasijas: the conqueror arrives in chains - arms bound crossed over the
    # chest, then RIPPED wide apart, spine arching back. Longest window, the
    # biggest gesture.
    catalog['kasijas'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (260, -70, 'out'), (520, -70), (760, -40, 'in'), (1400, 0)],
                         y=[(0, 0), (260, 44), (520, 44), (760, -8, 'in'), (1400, 0)],
                         z=[(0, 0), (260, -16), (520, -16), (760, 58, 'in'), (1400, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (260, -64, 'out'), (520, -64), (760, -36, 'in'), (1400, 0)],
                        y=[(0, 0), (260, 40), (520, 40), (760, -6, 'in'), (1400, 0)],
                        z=[(0, 0), (260, -16), (520, -16), (760, 54, 'in'), (1400, 0)]),
        'body': body(x=[(0, 0), (260, 6), (520, 6), (760, -14, 'in'), (1400, 0)]),
        'root': root(py=[(0, 0), (260, -.6), (520, -.6), (760, 1.4, 'in'), (1400, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (760, -12, 'in'), (1400, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (760, 14, 'in'), (1400, 0)]),
    }, duration=1400)
    # summon_frenzy: a whip-crack order - arm raised back, then one snapped
    # point of command held a beat. One-shot; the frenzy runs on unpinned.
    catalog['summon_frenzy'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (140, -30, 'out'), (280, 40), (360, -104, 'in'), (560, -96), (700, 0)],
                         y=[(0, 0), (140, -18), (280, 24), (360, 8, 'in'), (700, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (280, -16), (700, 0)]),
        'body': body(x=[(0, 0), (280, -4), (360, 10, 'in'), (700, 0)]),
        'root': root(py=[(0, 0), (280, .4), (360, -.5, 'in'), (700, 0)]),
    }, duration=700)

    # --- sun and moon -------------------------------------------------------
    # moon_flow: the lunar bow - left arm is the bow, the right hand draws the
    # string back to the chest, then releases the crescent off the string.
    catalog['moon_flow'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (200, -40, 'out'), (420, -34), (560, -116, 'in'), (760, 0)],
                         y=[(0, 0), (200, 40), (420, 38), (560, -14, 'in'), (760, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (200, -84, 'out'), (420, -80), (760, 0)]),
        'body': body(x=[(0, 0), (200, -4), (420, -2), (560, 8, 'in'), (760, 0)],
                     y=[(0, 0), (200, -10), (420, -10), (560, 6, 'in'), (760, 0)]),
        'root': root(py=[(0, 0), (200, .5), (420, .5), (560, -.4, 'in'), (760, 0)]),
    }, duration=760)
    # sunfire: the sun is LIFTED - right arm carries it aloft at -178 while
    # the left stays low in support; an asymmetric solar hoist, then a slow set.
    catalog['sunfire'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (260, -178, 'out'), (760, -176), (1080, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (260, -60, 'out'), (760, -58), (1080, 0)],
                        y=[(0, 0), (260, 30), (760, 28), (1080, 0)]),
        'body': body(x=[(0, 0), (260, -10), (760, -9), (1080, 0)]),
        'root': root(py=[(0, 0), (260, .9), (760, .8), (1080, 0)]),
    }, duration=1080)
    # moon_reflux: the inverse of every outward cast - arms wrap INWARD in a
    # spiral about the chest, then press the gathered light gently down.
    catalog['moon_reflux'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (180, -56, 'out'), (400, -40), (620, 18, 'in'), (800, 0)],
                         y=[(0, 0), (180, 8), (400, 40), (620, 18, 'in'), (800, 0)],
                         z=[(0, 0), (180, 34, 'out'), (400, 10), (800, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (180, -50, 'out'), (400, -36), (620, 14, 'in'), (800, 0)],
                        y=[(0, 0), (180, -6), (400, 34), (620, 16, 'in'), (800, 0)],
                        z=[(0, 0), (180, 30, 'out'), (400, 8), (800, 0)]),
        'body': body(x=[(0, 0), (180, -6), (400, 2), (620, 12, 'in'), (800, 0)]),
        'root': root(py=[(0, 0), (180, .6), (620, -.8, 'in'), (800, 0)]),
    }, duration=800)
    # solar_flare: dawn breaking - arms start crossed low, then burst up and
    # out into a high V with the chest opened to the sky.
    catalog['solar_flare'] = anim({
        'right_arm': sem('right_arm', x=[(0, 20), (200, -40, 'out'), (620, -168, 'in'), (980, 0)],
                         z=[(0, 0), (200, 40, 'out'), (620, 26), (980, 0)]),
        'left_arm': sem('left_arm', x=[(0, 20), (200, -40, 'out'), (620, -168, 'in'), (980, 0)],
                        z=[(0, 0), (200, 40, 'out'), (620, 26), (980, 0)]),
        'body': body(x=[(0, 8), (200, -6), (620, -14, 'in'), (980, 0)]),
        'root': root(py=[(0, 0), (200, -.8), (620, 1.2, 'in'), (980, 0)]),
    }, duration=980)
    # corona: the crown - arms held aloft in an open high V with a slow
    # alternating shimmer; the 19s buff itself plays out unpinned.
    catalog['corona'] = anim({
        'right_arm': sem('right_arm', x=[(0, -172), (480, -175, 'inout'), (960, -172), (1440, -175, 'inout'), (1900, -172)],
                         z=[(0, 16), (1900, 16)]),
        'left_arm': sem('left_arm', x=[(0, -175), (480, -172, 'inout'), (960, -175), (1440, -172, 'inout'), (1900, -175)],
                        z=[(0, 16), (1900, 16)]),
        'body': body(x=[(0, -8), (1900, -8)]),
        'root': root(py=[(0, 1.0), (1900, 1.0)]),
    }, duration=1900)
    # moon_starfall: counting the stars down - a high open crescent hold with
    # the arms dipping in turn, one beat for every falling star.
    catalog['moon_starfall'] = anim({
        'right_arm': sem('right_arm', x=[(0, -152), (480, -158, 'inout'), (960, -152)],
                         z=[(0, 26), (960, 26)]),
        'left_arm': sem('left_arm', x=[(0, -146), (480, -140, 'inout'), (960, -146)],
                        z=[(0, 26), (960, 26)]),
        'body': body(x=[(0, -6), (480, -7, 'inout'), (960, -6)]),
        'root': root(py=[(0, .6), (960, .6)]),
    }, duration=960, loop=True)
    # paradox: an asymmetric tear - right arm rips skyward while the left is
    # hauled low-back at the same instant, torso twisted through the split;
    # the torn pose is HELD, then snaps back.
    catalog['paradox'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (220, -164, 'out'), (620, -160), (840, 0)],
                         y=[(0, 0), (220, -10), (840, 0)],
                         z=[(0, 0), (220, 18), (840, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (220, 56, 'out'), (620, 52), (840, 0)]),
        'body': body(x=[(0, 0), (220, 6), (840, 0)],
                     y=[(0, 0), (220, 18), (620, 18), (840, 0)]),
        'root': root(py=[(0, 0), (220, .7), (840, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (220, -10), (840, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (220, 12), (840, 0)]),
    }, duration=840)

    # --- demon hunter -------------------------------------------------------
    # chaos_strike: twin X-fang - one sharp whip across the front from a wide
    # outside guard; the left variant mirrors it by cast parity.
    catalog['chaos_strike'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (60, 18, 'out'), (170, -104, 'in'), (240, -96, 'hold'), (520, 0)],
                         y=[(0, 0), (60, -30), (170, 24, 'in'), (240, 20), (520, 0)],
                         z=[(0, 0), (60, 28), (170, -10, 'in'), (240, -8), (520, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (60, -20), (520, 0)]),
        'body': body(x=[(0, 0), (60, 4), (170, 10, 'in'), (240, 8, 'hold'), (520, 0)],
                     y=[(0, 0), (60, -14), (170, 14, 'in'), (240, 10), (520, 0)]),
    }, duration=520)
    catalog['chaos_strike_left'] = anim({
        'left_arm': sem('left_arm', x=[(0, 0), (60, 18, 'out'), (170, -104, 'in'), (240, -96, 'hold'), (520, 0)],
                         y=[(0, 0), (60, -30), (170, 24, 'in'), (240, 20), (520, 0)],
                         z=[(0, 0), (60, 28), (170, -10, 'in'), (240, -8), (520, 0)]),
        'right_arm': sem('right_arm', x=[(0, 0), (60, -20), (520, 0)]),
        'body': body(x=[(0, 0), (60, 4), (170, 10, 'in'), (240, 8, 'hold'), (520, 0)],
                     y=[(0, 0), (60, 14), (170, -14, 'in'), (240, -10), (520, 0)]),
    }, duration=520)
    # throw_glaive: a discus throw - flat pull across the body, torso pre-wound,
    # then one hard release at 300ms with the follow-through carrying past.
    catalog['throw_glaive'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (130, 34, 'out'), (300, -84, 'in'), (440, -62), (620, 0)],
                         y=[(0, 0), (130, -34), (300, 16, 'in'), (620, 0)],
                         z=[(0, 0), (130, 26), (300, -10), (620, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (130, -22), (620, 0)]),
        'body': body(x=[(0, 0), (130, -6), (300, 10, 'in'), (620, 0)],
                     y=[(0, 0), (130, 12), (300, -14, 'in'), (620, 0)]),
    }, duration=620)
    # immolation_aura: the IGNITION only - arms crossed low, ripped up and out
    # in one fire burst; the aura then burns on its own, body unpinned.
    catalog['immolation_aura'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (160, -24, 'out'), (340, -70, 'in'), (600, -62), (900, 0)],
                         y=[(0, 0), (160, 36), (340, -6, 'in'), (900, 0)],
                         z=[(0, 0), (160, -16), (340, 52, 'out'), (600, 48), (900, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -24, 'out'), (340, -70, 'in'), (600, -62), (900, 0)],
                        y=[(0, 0), (160, 36), (340, -6, 'in'), (900, 0)],
                        z=[(0, 0), (160, -16), (340, 52, 'out'), (600, 48), (900, 0)]),
        'body': body(x=[(0, 0), (160, 8), (340, -10, 'in'), (900, 0)]),
        'root': root(py=[(0, 0), (160, -.8), (340, .8, 'in'), (900, 0)]),
    }, duration=900)
    # eye_beam: the firing stance, kept braced against recoil - forward fold,
    # root sunk, both hands half-raised toward the eyes, with a sustained
    # vibration while the fel pours.
    catalog['eye_beam'] = anim({
        'body': body(x=[(0, 0), (160, -18, 'out'), (260, -12), (600, -13), (900, -11), (1200, -14), (1500, -12), (1800, -13), (2200, -11)]),
        'root': root(py=[(0, 0), (160, -3.2, 'out'), (260, -2.6), (700, -2.8), (1100, -2.5), (1500, -2.8), (2200, -2.5)]),
        'right_arm': sem('right_arm', x=[(0, 0), (160, -42, 'out'), (260, -36), (700, -38), (1100, -34), (1500, -37), (2200, -34)],
                         z=[(0, 0), (160, 20), (260, 17), (700, 18), (1100, 15), (1500, 17), (2200, 15)]),
        'left_arm': sem('left_arm', x=[(0, 0), (160, -42, 'out'), (260, -36), (700, -34), (1100, -38), (1500, -34), (2200, -36)],
                        z=[(0, 0), (160, 20), (260, 17), (700, 15), (1100, 18), (1500, 15), (2200, 16)]),
        'right_leg': sem('right_leg', x=[(0, 0), (160, -14), (260, -12), (2200, -12)]),
        'left_leg': sem('left_leg', x=[(0, 0), (160, 10), (260, 8), (2200, 8)]),
    }, duration=2200)
    # fel_rush: the shoulder ram - body pitched hard over, lead arm cocked as
    # the battering head, rear arm dragging, legs in a lunge split.
    catalog['fel_rush'] = anim({
        'body': body(x=[(0, 0), (120, 30, 'in'), (420, 28)]),
        'right_arm': sem('right_arm', x=[(0, 0), (120, -46, 'in'), (420, -42)], z=[(0, 0), (120, 20), (420, 18)]),
        'left_arm': sem('left_arm', x=[(0, 0), (120, 40, 'in'), (420, 36)]),
        'right_leg': sem('right_leg', x=[(0, 0), (120, -28, 'in'), (420, -26)]),
        'left_leg': sem('left_leg', x=[(0, 0), (120, 22, 'in'), (420, 20)]),
    }, duration=420)
    # blade_dance: the full choreography, not an endless spin - a weaving
    # spin phase, then the leap at 1400, the crash at 1600, the landing.
    catalog['blade_dance'] = anim({
        'body': body(x=[(0, 8), (260, 6, 'inout'), (520, 9, 'inout'), (780, 6, 'inout'), (1040, 9, 'inout'), (1300, -6, 'out'), (1600, 22, 'in'), (2000, 4)],
                     y=[(0, 22), (260, -22, 'in'), (520, 22, 'in'), (780, -22, 'in'), (1040, 22, 'in'), (1300, -12, 'in'), (1600, 8, 'in'), (2000, 0)]),
        'right_arm': sem('right_arm', x=[(0, -60), (260, -108, 'in'), (520, -58, 'in'), (780, -110, 'in'), (1040, -58, 'in'), (1300, -150, 'out'), (1600, -12, 'in'), (2000, 0)]),
        'left_arm': sem('left_arm', x=[(0, -100), (260, -52, 'in'), (520, -104, 'in'), (780, -52, 'in'), (1040, -104, 'in'), (1300, -146, 'out'), (1600, -10, 'in'), (2000, 0)]),
        'root': root(py=[(0, -.4), (1300, -.2), (1400, 2.4, 'out'), (1600, -2.0, 'in'), (2000, 0)]),
        'right_leg': sem('right_leg', x=[(0, -12), (1300, -12), (1600, 10, 'in'), (2000, 0)]),
        'left_leg': sem('left_leg', x=[(0, 10), (1300, 10), (1600, -10, 'in'), (2000, 0)]),
    }, duration=2000)
    # the_hunt: a predator stalking through the combo - deep forward lean, both
    # arms swept back like a sprinting beast, wide stride, subtle pump.
    catalog['the_hunt'] = anim({
        'body': body(x=[(0, 24), (280, 27, 'inout'), (560, 24)]),
        'right_arm': sem('right_arm', x=[(0, 30), (280, 34, 'inout'), (560, 30)]),
        'left_arm': sem('left_arm', x=[(0, 42), (280, 46, 'inout'), (560, 42)]),
        'right_leg': sem('right_leg', x=[(0, -24), (280, -20, 'inout'), (560, -24)]),
        'left_leg': sem('left_leg', x=[(0, 18), (280, 22, 'inout'), (560, 18)]),
    }, duration=560, loop=True)
    # the_hunt_left: the off-hand slash of the combo - same beast-stalk loop
    # with the left arm driving the reach.
    catalog['the_hunt_left'] = anim({
        'body': body(x=[(0, 24), (280, 27, 'inout'), (560, 24)]),
        'left_arm': sem('left_arm', x=[(0, 42), (280, 46, 'inout'), (560, 42)]),
        'right_arm': sem('right_arm', x=[(0, 30), (280, 34, 'inout'), (560, 30)]),
        'left_leg': sem('left_leg', x=[(0, -24), (280, -20, 'inout'), (560, -24)]),
        'right_leg': sem('right_leg', x=[(0, 18), (280, 22, 'inout'), (560, 18)]),
    }, duration=560, loop=True)
    # metamorphosis: the demon takes the wheel - gathering crouch, a violent
    # skyward rip with a roar-arch of the spine, then the landing brace.
    catalog['metamorphosis'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (220, -46, 'out'), (560, -158, 'out'), (820, -84, 'in'), (1200, 0)],
                         z=[(0, 0), (220, 30, 'out'), (560, 62), (820, 40), (1200, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (220, -46, 'out'), (560, -158, 'out'), (820, -84, 'in'), (1200, 0)],
                        z=[(0, 0), (220, 30, 'out'), (560, 62), (820, 40), (1200, 0)]),
        'body': body(x=[(0, 0), (220, 14), (560, -16, 'out'), (700, 8, 'in'), (1200, 0)]),
        'root': root(py=[(0, 0), (220, -2.4, 'out'), (560, 4.6, 'out'), (820, -.8, 'in'), (1200, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (220, 12), (560, -34), (820, 6), (1200, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (220, -14), (560, 24), (820, -10), (1200, 0)]),
    }, duration=1200)
    # warglaive swings: feral and wide - a big outside backswing, then an
    # overhead diagonal crash that carves the torso through with it.
    catalog['warglaive_attack'] = anim({
        'right_arm': sem('right_arm', x=[(0, 0), (70, 26, 'out'), (210, -118, 'in'), (290, -108, 'hold'), (560, 0)],
                         y=[(0, 0), (70, -30), (210, 34, 'in'), (290, 28), (560, 0)],
                         z=[(0, 0), (70, 30), (210, -14, 'in'), (290, -12), (560, 0)]),
        'left_arm': sem('left_arm', x=[(0, 0), (70, -16), (560, 0)]),
        'body': body(x=[(0, 0), (70, -6), (210, 12, 'in'), (290, 10, 'hold'), (560, 0)],
                     y=[(0, 0), (70, -12), (210, 16, 'in'), (290, 13), (560, 0)]),
    }, duration=560)
    catalog['warglaive_attack_left'] = anim({
        'left_arm': sem('left_arm', x=[(0, 0), (70, 26, 'out'), (210, -118, 'in'), (290, -108, 'hold'), (560, 0)],
                        y=[(0, 0), (70, -30), (210, 34, 'in'), (290, 28), (560, 0)],
                        z=[(0, 0), (70, 30), (210, -14, 'in'), (290, -12), (560, 0)]),
        'right_arm': sem('right_arm', x=[(0, 0), (70, -16), (560, 0)]),
        'body': body(x=[(0, 0), (70, -6), (210, 12, 'in'), (290, 10, 'hold'), (560, 0)],
                     y=[(0, 0), (70, 12), (210, -16, 'in'), (290, -13), (560, 0)]),
    }, duration=560)

    # --- oracle -------------------------------------------------------------
    # Oracle reads as a golden fencing prosecutor: every skill keeps its server
    # hit mark (down/thrust/rift at 300ms, sweep at 500ms, smile at 100/300ms)
    # while the silhouettes are deliberately distinct - a two-handed collapse
    # with a heel raise, a rare back-coil pierce with a held lunge, a hip-driven
    # wipe with follow-through, a taunting courtesy with an open left arm, a
    # center-line draw torn open, and a three-act kata for the replica.
    # oracle_down: the verdict press - rise onto the toes with the blade
    # carried two-handed overhead, then a full-body collapse that folds the
    # knees and plants the point exactly at the 300ms hit mark.
    catalog['oracle_down'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, -20), (170, -172, 'out'), (240, -172, 'hold'), (300, -12, 'in'), (410, -8), (620, 0)],
                         y=[(0, 0), (170, -10), (300, 6, 'in'), (620, 0)]),
        'left_arm': sem('left_arm',
                        x=[(0, -10), (170, -150, 'out'), (240, -150, 'hold'), (300, -22, 'in'), (620, 0)]),
        'body': body(x=[(0, 0), (170, -12), (240, -12, 'hold'), (300, 30, 'in'), (620, 0)]),
        'root': root(py=[(0, 0), (170, .9), (240, .9, 'hold'), (300, -2.4, 'in'), (410, -2.1), (620, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (170, -6), (300, 18, 'in'), (620, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (170, 6), (300, -24, 'in'), (620, 0)]),
    }, duration=620)
    # oracle_thrust: the needle - coil the arm back past the hip with the torso
    # turned away, then a level pierce that rides onto the point, left arm
    # swept back-out, rear leg extended, and the lunge HELD with a settle
    # shimmer instead of a snap return.
    catalog['oracle_thrust'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, 0), (130, 38, 'out'), (200, 38, 'hold'), (300, -92, 'in'), (430, -86), (620, 0)],
                         y=[(0, 0), (130, -12), (300, 4, 'in'), (620, 0)]),
        'left_arm': sem('left_arm',
                        x=[(0, 0), (130, -8), (300, 22, 'in'), (450, 18), (620, 0)],
                        z=[(0, 0), (130, 8), (300, 30, 'in'), (620, 0)]),
        'body': body(x=[(0, 0), (130, -8), (300, 16, 'in'), (620, 0)],
                     y=[(0, 0), (130, 18), (300, -4, 'in'), (620, 0)]),
        'root': root(py=[(0, 0), (130, .4), (300, -1.0, 'in'), (620, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (130, -6), (300, 20, 'in'), (620, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (130, 8), (300, -38, 'in'), (450, -34), (620, 0)]),
    }, duration=620)
    # oracle_sweep: the gate wipe - the hips wind the blade across the body
    # while the weight sinks, then a yaw-whipped release through the 500ms
    # hit mark, rear foot pivoting, blade finishing high and outside.
    catalog['oracle_sweep'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, -70), (200, -84, 'out'), (260, -84, 'hold'), (500, -66, 'in'), (700, -76), (900, -70)],
                         y=[(0, -40), (200, -58, 'out'), (260, -58, 'hold'), (500, 36, 'in'), (900, -40)],
                         z=[(0, 6), (200, -10), (500, 34, 'in'), (900, 6)]),
        'left_arm': sem('left_arm',
                        x=[(0, -30), (200, -44), (500, -20, 'in'), (900, -30)],
                        z=[(0, 0), (200, 10), (500, 24, 'in'), (900, 0)]),
        'body': body(x=[(0, 10), (200, 16, 'out'), (500, 6, 'in'), (900, 10)],
                     y=[(0, 12), (200, 30, 'out'), (260, 30, 'hold'), (500, -22, 'in'), (900, 12)]),
        'root': root(py=[(0, 0), (200, -1.0), (500, -.4, 'in'), (900, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (200, -10), (500, 6, 'in'), (900, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (200, 8), (500, -14, 'in'), (900, 0)]),
    }, duration=900)
    # oracle_smile: the courtesy - blade presented vertical at face height
    # with a wrist roll and a heel pop, left arm opened wide for the first
    # beat, then a backhand flick through the second before settling into a
    # relaxed show pose instead of neutral.
    catalog['oracle_smile'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, -40), (100, -150, 'out'), (150, -146), (300, -104, 'in'), (430, -96), (620, -30)],
                         z=[(0, 0), (100, 16, 'out'), (150, 12), (300, -12, 'in'), (620, 8)]),
        'left_arm': sem('left_arm',
                        x=[(0, 0), (100, -16), (300, 4, 'in'), (620, 0)],
                        z=[(0, 0), (100, 26, 'out'), (300, 18), (620, 10)]),
        'body': body(x=[(0, 0), (100, -6), (300, 8, 'in'), (620, 0)],
                     y=[(0, 0), (100, -8), (300, 4, 'in'), (620, 0)]),
        'root': root(py=[(0, 0), (100, .7), (150, .7, 'hold'), (300, 0, 'in'), (620, 0)]),
    }, duration=620)
    # oracle_rift: the seam tear - sink, square up, and draw the blade slowly
    # through the center line at the 300ms mark, then rip both arms to
    # opposite sides with a hard yaw as space splits along the cut, and hold
    # the spread while it runs.
    catalog['oracle_rift'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, -26), (240, -38, 'out'), (300, -58, 'in'), (560, -52), (780, 0)],
                         y=[(0, 36), (240, 44), (300, -6, 'in'), (560, -10), (780, 0)],
                         z=[(0, -6), (240, -10), (300, 44, 'in'), (560, 40), (780, 0)]),
        'left_arm': sem('left_arm',
                        x=[(0, -22), (240, -30), (300, 14, 'in'), (560, 10), (780, 0)],
                        z=[(0, 10), (240, 14), (300, 32, 'in'), (560, 28), (780, 0)]),
        'body': body(x=[(0, 6), (240, 10), (300, -4, 'in'), (560, -6), (780, 0)],
                     y=[(0, -10), (240, -16), (300, 24, 'in'), (560, 20), (780, 0)]),
        'root': root(py=[(0, 0), (240, -1.4), (300, .8, 'in'), (560, .5), (780, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (240, 8), (300, -16, 'in'), (780, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (240, -8), (300, 22, 'in'), (780, 0)]),
    }, duration=780)
    # furioso_replica: the three-act kata, matching the real cast run - eight
    # measured prelude presentations alternating blade roads, eight rapid
    # combo exchanges that sink the stance, then a coiled finisher lunge that
    # holds the point to the last frame. Non-loop: the document spans the
    # nine-stack run exactly; shorter runs simply end mid-kata.
    replica_arm_x, replica_arm_y, replica_arm_z = [], [], []
    replica_body_y, replica_root = [], []
    for step in range(8):
        t = step * 350
        strike = step % 2 == 0
        arm_x, arm_y, arm_z = (-122, 34, 4) if strike else (-76, -30, -12)
        sway = 12 if strike else -12
        replica_arm_x += [(t, arm_x, 'in'), (t + 90, arm_x, 'hold'), (t + 240, -60)]
        replica_arm_y += [(t, arm_y, 'in'), (t + 90, arm_y, 'hold'), (t + 240, 10)]
        replica_arm_z += [(t, arm_z, 'in'), (t + 90, arm_z, 'hold'), (t + 240, 8)]
        replica_body_y += [(t, sway, 'in'), (t + 240, 0)]
        replica_root += [(t, -step * .08, 'in')]
    for step in range(8):
        t = 2800 + step * 100
        strike = step % 2 == 0
        replica_arm_x += [(t, -118 if strike else -70, 'in'), (t + 55, -118 if strike else -70, 'hold')]
        replica_arm_y += [(t, 34 if strike else -32, 'in'), (t + 55, 34 if strike else -32, 'hold')]
        replica_body_y += [(t, 16 if strike else -16, 'in')]
        replica_root += [(t, -1.2 - step * .05, 'in')]
    replica_arm_x += [(3600, 34, 'out'), (4050, 34, 'hold'), (4300, -96, 'in'), (4520, -88), (4750, -84, 'hold')]
    replica_arm_y += [(3600, -14), (4050, -14, 'hold'), (4300, 4, 'in'), (4750, 4, 'hold')]
    replica_arm_z += [(3600, -10), (4050, -10, 'hold'), (4300, 2, 'in'), (4750, 2, 'hold')]
    catalog['furioso_replica'] = anim({
        'right_arm': sem('right_arm', x=replica_arm_x, y=replica_arm_y, z=replica_arm_z),
        'body': body(x=[(0, 4), (700, 7), (1400, 4), (2100, 7), (2790, 4),
                        (3600, -6, 'out'), (4050, -6, 'hold'), (4300, 16, 'in'), (4750, 14, 'hold')],
                     y=replica_body_y),
        'root': root(py=replica_root + [(3600, -2.0, 'out'), (4050, -2.0, 'hold'), (4300, -1.0, 'in'), (4750, -1.0, 'hold')]),
        'right_leg': sem('right_leg', x=[(0, 0), (3600, 0), (4300, 18, 'in'), (4750, 18, 'hold')]),
        'left_leg': sem('left_leg', x=[(0, 0), (3600, 0), (4300, -36, 'in'), (4750, -36, 'hold')]),
    }, duration=4750)

    # --- elementalist -------------------------------------------------------
    # elementalist_charge: upper-body only loop so legs keep vanilla walking during Moving Cast.
    catalog['elementalist_charge'] = anim({
        'right_arm': sem('right_arm',
                         x=[(0, -88), (450, -98, 'inout'), (900, -88)],
                         y=[(0, -14), (450, -20, 'inout'), (900, -14)],
                         z=[(0, 14), (450, 18, 'inout'), (900, 14)]),
        'left_arm': sem('left_arm',
                        x=[(0, -76), (450, -84, 'inout'), (900, -76)],
                        y=[(0, 18), (450, 24, 'inout'), (900, 18)],
                        z=[(0, -10), (450, -14, 'inout'), (900, -10)]),
    }, duration=900, loop=True)
    catalog['ice_wall'] = anim({
        'right_arm': sem('right_arm', x=[(0, -70), (140, -96, 'out'), (280, -80), (420, 0)],
                         y=[(0, -28), (140, 44, 'out'), (280, 52), (420, 0)],
                         z=[(0, 10), (140, 36, 'out'), (420, 0)]),
        'left_arm': sem('left_arm', x=[(0, -70), (140, -96, 'out'), (280, -80), (420, 0)],
                        y=[(0, -28), (140, 44, 'out'), (280, 52), (420, 0)],
                        z=[(0, 10), (140, 36, 'out'), (420, 0)]),
    }, duration=420)
    catalog['lightning_vortex'] = anim({
        'right_arm': sem('right_arm', x=[(0, -60), (160, -124, 'out'), (310, -86), (460, 0)],
                         y=[(0, -24), (160, 28, 'out'), (310, 18), (460, 0)],
                         z=[(0, 16), (160, 26), (460, 0)]),
        'left_arm': sem('left_arm', x=[(0, -34), (160, -64, 'out'), (460, 0)],
                        z=[(0, 8), (160, 16), (460, 0)]),
    }, duration=460)
    catalog['void_sphere'] = anim({
        'right_arm': sem('right_arm', x=[(0, -68), (180, -104, 'out'), (340, -96), (480, 0)],
                         y=[(0, -20), (180, -8, 'out'), (480, 0)]),
        'left_arm': sem('left_arm', x=[(0, -68), (180, -104, 'out'), (340, -96), (480, 0)],
                        y=[(0, -20), (180, -8, 'out'), (480, 0)]),
    }, duration=480)
    catalog['flame_pillar'] = anim({
        'right_arm': sem('right_arm', x=[(0, -36), (170, -148, 'out'), (320, -134), (480, 0)],
                         z=[(0, 12), (170, 22, 'out'), (480, 0)]),
        'left_arm': sem('left_arm', x=[(0, -24), (170, -96, 'out'), (480, 0)]),
    }, duration=480)
    catalog['halloween_buster'] = anim({
        'right_arm': sem('right_arm', x=[(0, -146), (200, -162, 'out'), (340, -76, 'in'), (520, 0)],
                         z=[(0, 24), (200, 32), (340, 10, 'in'), (520, 0)]),
        'left_arm': sem('left_arm', x=[(0, -130), (200, -148, 'out'), (340, -70, 'in'), (520, 0)],
                        z=[(0, 20), (200, 28), (340, 10, 'in'), (520, 0)]),
    }, duration=520)
    catalog['arctic_feast'] = anim({
        'right_arm': sem('right_arm', x=[(0, -110), (200, -138, 'out'), (360, -82), (520, 0)],
                         y=[(0, -12), (200, 34, 'out'), (360, 42), (520, 0)],
                         z=[(0, 14), (200, 42, 'out'), (520, 0)]),
        'left_arm': sem('left_arm', x=[(0, -110), (200, -138, 'out'), (360, -82), (520, 0)],
                        y=[(0, -12), (200, 34, 'out'), (360, 42), (520, 0)],
                        z=[(0, 14), (200, 42, 'out'), (520, 0)]),
    }, duration=520)
    catalog['black_hole'] = anim({
        'right_arm': sem('right_arm', x=[(0, -86), (170, -78), (320, -112, 'out'), (540, 0)],
                         y=[(0, 22), (170, -28, 'in'), (320, 10, 'out'), (540, 0)]),
        'left_arm': sem('left_arm', x=[(0, -86), (170, -78), (320, -112, 'out'), (540, 0)],
                        y=[(0, 22), (170, -28, 'in'), (320, 10, 'out'), (540, 0)]),
    }, duration=540)
    catalog['lightning_strike'] = anim({
        'right_arm': sem('right_arm', x=[(0, -158), (360, -166, 'inout'), (720, -158)],
                         z=[(0, 14), (360, 20, 'inout'), (720, 14)]),
        'left_arm': sem('left_arm', x=[(0, -78), (360, -86, 'inout'), (720, -78)],
                        y=[(0, -12), (360, -18, 'inout'), (720, -12)]),
    }, duration=720, loop=True)
    catalog['astral_storm'] = anim({
        'right_arm': sem('right_arm', x=[(0, -128), (500, -142, 'inout'), (1000, -128)],
                         z=[(0, 48), (500, 58, 'inout'), (1000, 48)]),
        'left_arm': sem('left_arm', x=[(0, -128), (500, -142, 'inout'), (1000, -128)],
                        z=[(0, 48), (500, 58, 'inout'), (1000, 48)]),
        'right_leg': sem('right_leg', x=[(0, 14), (500, 20, 'inout'), (1000, 14)]),
        'left_leg': sem('left_leg', x=[(0, 8), (500, 14, 'inout'), (1000, 8)]),
    }, duration=1000, loop=True)

    # --- battle mage -------------------------------------------------------
    # Niu's Combat Art & Teana Battle Goddess polearm choreography.
    catalog['bm_basic_1'] = anim({
        'right_arm': sem('right_arm', x=[(0, -108), (110, -72, 'in'), (220, -45), (340, 0)],
                         y=[(0, 48), (110, -46, 'in'), (220, -52), (340, 0)],
                         z=[(0, 18), (110, -12, 'in'), (340, 0)]),
        'left_arm': sem('left_arm', x=[(0, -82), (110, -52, 'in'), (340, 0)],
                        y=[(0, -24), (110, 32, 'in'), (340, 0)]),
        'body': body(x=[(0, 6), (110, 12, 'in'), (340, 0)], y=[(0, -24), (110, 28, 'in'), (340, 0)]),
    }, duration=340)
    catalog['bm_basic_2'] = anim({
        'right_arm': sem('right_arm', x=[(0, 22), (95, -92, 'in'), (200, -88), (340, 0)],
                         y=[(0, -16), (95, 4, 'in'), (340, 0)]),
        'left_arm': sem('left_arm', x=[(0, -48), (95, -88, 'in'), (200, -82), (340, 0)],
                        y=[(0, -20), (95, -18, 'in'), (340, 0)]),
        'body': body(x=[(0, 6), (95, 16, 'in'), (340, 0)], y=[(0, 26), (95, 34, 'in'), (340, 0)]),
    }, duration=340)
    catalog['bm_basic_3'] = anim({
        'right_arm': sem('right_arm', x=[(0, -35), (140, -126, 'out'), (260, -118), (400, 0)],
                         y=[(0, -40), (140, 22, 'out'), (400, 0)],
                         z=[(0, 12), (140, 24, 'out'), (400, 0)]),
        'left_arm': sem('left_arm', x=[(0, -28), (140, -96, 'out'), (400, 0)]),
        'body': body(x=[(0, 10), (140, -8, 'out'), (400, 0)], y=[(0, -30), (140, 32, 'out'), (400, 0)]),
    }, duration=400)
    catalog['teana_basic_1'] = catalog['bm_basic_1']
    catalog['teana_basic_2'] = catalog['bm_basic_2']
    catalog['teana_basic_3'] = catalog['bm_basic_3']
    catalog['palm_blast_charge'] = anim({
        'right_arm': sem('right_arm', x=[(0, 30), (260, 36, 'inout'), (520, 30)],
                         z=[(0, 14), (260, 18, 'inout'), (520, 14)]),
        'left_arm': sem('left_arm', x=[(0, -56), (260, -64, 'inout'), (520, -56)],
                        y=[(0, -16), (260, -22, 'inout'), (520, -16)]),
        'body': body(x=[(0, 16), (260, 20, 'inout'), (520, 16)], y=[(0, 32), (260, 36, 'inout'), (520, 32)]),
    }, duration=520, loop=True)
    catalog['palm_blast'] = anim({
        'right_arm': sem('right_arm', x=[(0, 32), (130, 52, 'out'), (300, 40), (460, 0)],
                         z=[(0, 16), (130, 32, 'out'), (460, 0)]),
        'left_arm': sem('left_arm', x=[(0, -58), (130, -104, 'in'), (300, -96), (460, 0)],
                        y=[(0, -18), (130, 6, 'in'), (460, 0)]),
        'body': body(x=[(0, 16), (130, 24, 'in'), (460, 0)], y=[(0, 34), (130, -26, 'in'), (460, 0)]),
    }, duration=460)
    catalog['teana_palm_blast'] = anim({
        'right_arm': sem('right_arm', x=[(0, 24), (140, -96, 'in'), (270, -138, 'out'), (460, 0)]),
        'left_arm': sem('left_arm', x=[(0, -34), (140, -90, 'in'), (270, -124, 'out'), (460, 0)]),
        'body': body(x=[(0, 14), (140, 22, 'in'), (270, -8, 'out'), (460, 0)]),
    }, duration=460)
    catalog['full_swing'] = anim({
        'right_arm': sem('right_arm', x=[(0, -86), (110, -92), (200, -88, 'in'), (320, -76), (440, 0)],
                         y=[(0, -64), (110, -74, 'out'), (200, 68, 'in'), (320, 62), (440, 0)],
                         z=[(0, -12), (200, 26, 'in'), (440, 0)]),
        'left_arm': sem('left_arm', x=[(0, -78), (110, -84), (200, -74, 'in'), (440, 0)],
                        y=[(0, 38), (110, 46), (200, -34, 'in'), (440, 0)]),
        'body': body(x=[(0, 10), (200, 14, 'in'), (440, 0)], y=[(0, -48), (110, -58), (200, 56, 'in'), (440, 0)]),
    }, duration=440)
    catalog['teana_full_swing'] = catalog['full_swing']
    catalog['full_swing_empress'] = anim({
        'right_arm': sem('right_arm', x=[(0, -130), (160, -168, 'out'), (310, -24, 'in'), (430, -20), (560, 0)],
                         z=[(0, 18), (160, 34), (310, 12), (560, 0)]),
        'left_arm': sem('left_arm', x=[(0, -120), (160, -158, 'out'), (310, -26, 'in'), (560, 0)]),
        'body': body(x=[(0, -8), (160, -16, 'out'), (310, 28, 'in'), (560, 0)]),
    }, duration=560)
    catalog['chaser_launcher'] = anim({
        'left_arm': sem('left_arm', x=[(0, -40), (110, -94, 'out'), (260, 0)]),
    }, duration=260)
    catalog['chaser_fusion'] = anim({
        'right_arm': sem('right_arm', x=[(0, -60), (150, -96, 'in'), (300, -88), (460, 0)],
                         y=[(0, 36), (150, -18, 'in'), (460, 0)]),
        'left_arm': sem('left_arm', x=[(0, -60), (150, -96, 'in'), (300, -88), (460, 0)],
                        y=[(0, 36), (150, -18, 'in'), (460, 0)]),
        'body': body(x=[(0, -8), (150, 14, 'in'), (460, 0)]),
    }, duration=460)
    catalog['chaser_press'] = anim({
        'right_arm': sem('right_arm', x=[(0, -80), (180, -168, 'out'), (250, -168, 'hold'), (340, -24, 'in'), (470, -20), (620, 0)]),
        'left_arm': sem('left_arm', x=[(0, -80), (180, -162, 'out'), (250, -162, 'hold'), (340, -24, 'in'), (470, -20), (620, 0)]),
        'body': body(x=[(0, -6), (180, -16, 'out'), (340, 30, 'in'), (470, 26), (620, 0)]),
    }, duration=620)
    catalog['punto_wave'] = anim({
        'right_arm': sem('right_arm', x=[(0, -54), (55, -96, 'in'), (110, -54, 'out')],
                         y=[(0, -14), (55, 8, 'in'), (110, -14, 'out')]),
        'left_arm': sem('left_arm', x=[(0, -68), (55, -90, 'in'), (110, -68, 'out')]),
        'body': body(x=[(0, 14), (55, 18, 'in'), (110, 14)]),
    }, duration=110, loop=True)
    catalog['punto_wave_loop'] = catalog['punto_wave']
    catalog['punto_wave_finish'] = catalog['full_swing']
    catalog['punto_wave_triple'] = anim({
        'right_arm': sem('right_arm', x=[(0, 18), (110, -104, 'in'), (190, -40), (290, -84, 'in'), (380, -35), (480, -96, 'in'), (640, 0)],
                         y=[(0, -24), (110, 24), (290, -34), (480, 56, 'in'), (640, 0)]),
        'left_arm': sem('left_arm', x=[(0, -40), (110, -96), (290, -78), (480, -84), (640, 0)]),
        'body': body(x=[(0, 12), (480, 16), (640, 0)], y=[(0, 24), (110, -20), (290, 26), (480, -42), (640, 0)]),
    }, duration=640)
    catalog['dragon_strike_charge'] = anim({
        'right_arm': sem('right_arm', x=[(0, 44), (250, 50, 'inout'), (500, 44)],
                         y=[(0, -18), (250, -22, 'inout'), (500, -18)]),
        'left_arm': sem('left_arm', x=[(0, -76), (250, -82, 'inout'), (500, -76)]),
        'body': body(x=[(0, 22), (250, 26, 'inout'), (500, 22)], y=[(0, 38), (250, 42, 'inout'), (500, 38)]),
    }, duration=500, loop=True)
    catalog['dragon_strike'] = anim({
        'right_arm': sem('right_arm', x=[(0, 44), (120, -96, 'in'), (380, -94, 'hold'), (540, 0)],
                         y=[(0, -18), (120, 4, 'in'), (540, 0)]),
        'left_arm': sem('left_arm', x=[(0, -76), (120, 34, 'in'), (380, 38), (540, 0)],
                        z=[(0, 10), (120, 38, 'in'), (540, 0)]),
        'body': body(x=[(0, 22), (120, 32, 'in'), (380, 28), (540, 0)]),
    }, duration=540)
    catalog['dragon_spear'] = anim({
        'right_arm': sem('right_arm', x=[(0, 24), (160, -92, 'in'), (1450, -92, 'hold'), (1680, -152, 'out'), (1880, -34, 'in'), (2100, 0)],
                         y=[(0, -20), (160, 0, 'in'), (1450, 0, 'hold'), (1880, 48, 'in'), (2100, 0)]),
        'left_arm': sem('left_arm', x=[(0, -32), (160, -90, 'in'), (1450, -90, 'hold'), (1680, -142, 'out'), (1880, -38, 'in'), (2100, 0)]),
        'body': body(x=[(0, 10), (160, 16, 'in'), (1450, 14), (1680, -12), (1880, 20, 'in'), (2100, 0)]),
    }, duration=2100)
    catalog['dragon_spear_thrust'] = anim({
        'right_arm': sem('right_arm', x=[(0, 28), (160, -94, 'in'), (320, -90), (460, 0)]),
        'left_arm': sem('left_arm', x=[(0, -34), (160, -90, 'in'), (320, -86), (460, 0)]),
        'body': body(x=[(0, 10), (160, 18, 'in'), (460, 0)]),
    }, duration=460)
    catalog['dragon_spear_cleave'] = anim({
        'right_arm': sem('right_arm', x=[(0, -40), (180, -172, 'out'), (250, -172, 'hold'), (360, -18, 'in'), (500, -16), (640, 0)]),
        'left_arm': sem('left_arm', x=[(0, -36), (180, -166, 'out'), (250, -166, 'hold'), (360, -22, 'in'), (640, 0)]),
        'body': body(x=[(0, -6), (180, -18, 'out'), (360, 28, 'in'), (640, 0)]),
    }, duration=640)
    catalog['teana_transformation'] = anim({
        'right_arm': sem('right_arm', x=[(0, -20), (200, -44, 'out'), (440, -118, 'out'), (620, -92), (760, 0)],
                         z=[(0, 10), (200, 28), (440, 74, 'out'), (620, 62), (760, 0)]),
        'left_arm': sem('left_arm', x=[(0, -20), (200, -44, 'out'), (440, -118, 'out'), (620, -92), (760, 0)],
                        z=[(0, 10), (200, 28), (440, 74, 'out'), (620, 62), (760, 0)]),
        'body': body(x=[(0, 12), (200, 18), (440, -18, 'out'), (760, 0)]),
    }, duration=760)
    catalog['teana_finisher'] = anim({
        'right_arm': sem('right_arm', x=[(0, -50), (380, -164, 'out'), (560, -160, 'hold'), (780, -96, 'in'), (1120, -92), (1400, 0)],
                         z=[(0, 12), (380, 42), (780, 8, 'in'), (1400, 0)]),
        'left_arm': sem('left_arm', x=[(0, -46), (380, -148, 'out'), (780, 32, 'in'), (1400, 0)],
                        z=[(0, 12), (380, 48), (780, 38), (1400, 0)]),
        'body': body(x=[(0, -8), (380, -18, 'out'), (780, 34, 'in'), (1120, 28), (1400, 0)]),
    }, duration=1400)

    # Witch (魔道学者 / 魔术师) stock clips
    catalog['shadow_cloak'] = anim({
        'right_arm': sem('right_arm', x=[(0, -24), (160, -108, 'out'), (380, -96, 'hold'), (520, -22, 'in'), (650, 0)],
                         z=[(0, 14), (160, 74, 'out'), (380, 62), (520, 18), (650, 0)]),
        'left_arm': sem('left_arm', x=[(0, -24), (160, -108, 'out'), (380, -96, 'hold'), (520, -22, 'in'), (650, 0)],
                        z=[(0, 14), (160, 74, 'out'), (380, 62), (520, 18), (650, 0)]),
        'body': body(x=[(0, 6), (160, 18), (450, -12, 'out'), (650, 0)]),
    }, duration=650)
    catalog['giant_flyswatter'] = anim({
        'right_arm': sem('right_arm', x=[(0, -40), (140, -168, 'out'), (240, -18, 'in'), (380, -20), (500, 0)],
                         z=[(0, 12), (140, 26), (240, 8), (500, 0)]),
        'left_arm': sem('left_arm', x=[(0, -34), (140, -148, 'out'), (240, -28, 'in'), (500, 0)]),
        'body': body(x=[(0, -6), (140, -18, 'out'), (240, 28, 'in'), (500, 0)]),
    }, duration=500)
    catalog['lava_potion'] = anim({
        'right_arm': sem('right_arm', x=[(0, -20), (140, -148, 'out'), (260, -52, 'in'), (450, 0)],
                         z=[(0, 8), (140, 24), (260, 10), (450, 0)]),
        'left_arm': sem('left_arm', x=[(0, -18), (140, -48), (260, -22), (450, 0)]),
    }, duration=450)
    catalog['acid_rain'] = anim({
        'right_arm': sem('right_arm', x=[(0, -30), (160, -152, 'out'), (340, -124), (480, 0)],
                         z=[(0, 10), (160, 32), (480, 0)]),
        'left_arm': sem('left_arm', x=[(0, -26), (160, -142, 'out'), (340, -112), (480, 0)],
                        z=[(0, 10), (160, 28), (480, 0)]),
    }, duration=480)
    catalog['anti_gravity'] = anim({
        'right_arm': sem('right_arm', x=[(0, -24), (160, -116, 'out'), (340, -142), (560, 0)],
                         z=[(0, 12), (160, 42), (340, 18), (560, 0)]),
        'left_arm': sem('left_arm', x=[(0, -24), (160, -116, 'out'), (340, -142), (560, 0)],
                        z=[(0, 12), (160, 42), (340, 18), (560, 0)]),
    }, duration=560)
    catalog['raging_flame_furnace'] = anim({
        'right_arm': sem('right_arm', x=[(0, -68), (200, -82), (400, -68)], z=[(0, 16), (200, 24), (400, 16)]),
        'left_arm': sem('left_arm', x=[(0, -68), (200, -82), (400, -68)], z=[(0, 16), (200, 24), (400, 16)]),
        'right_leg': sem('right_leg', x=[(0, -62), (200, -54), (400, -62)], z=[(0, 18), (200, 22), (400, 18)]),
        'left_leg': sem('left_leg', x=[(0, -62), (200, -54), (400, -62)], z=[(0, 18), (200, 22), (400, 18)]),
    }, duration=400, loop=True)
    catalog['mega_drill'] = anim({
        'right_arm': sem('right_arm', x=[(0, -84), (160, -90), (320, -84)], z=[(0, 12), (160, 16), (320, 12)]),
        'left_arm': sem('left_arm', x=[(0, -84), (160, -90), (320, -84)], z=[(0, 12), (160, 16), (320, 12)]),
        'right_leg': sem('right_leg', x=[(0, -68), (160, -64), (320, -68)], z=[(0, 16), (160, 18), (320, 16)]),
        'left_leg': sem('left_leg', x=[(0, -68), (160, -64), (320, -68)], z=[(0, 16), (160, 18), (320, 16)]),
    }, duration=320, loop=True)
    catalog['florae_collider'] = anim({
        'right_arm': sem('right_arm', x=[(0, -96), (180, -104), (360, -96)], z=[(0, 24), (180, 32), (360, 24)]),
        'left_arm': sem('left_arm', x=[(0, -96), (180, -104), (360, -96)], z=[(0, 24), (180, 32), (360, 24)]),
        'right_leg': sem('right_leg', x=[(0, -48), (180, -44), (360, -48)], z=[(0, 14), (180, 16), (360, 14)]),
        'left_leg': sem('left_leg', x=[(0, -48), (180, -44), (360, -48)], z=[(0, 14), (180, 16), (360, 14)]),
    }, duration=360, loop=True)
    catalog['fusion_craft'] = anim({
        'right_arm': sem('right_arm', x=[(0, -112), (200, -138), (400, -112)], z=[(0, 28), (200, 42), (400, 28)]),
        'left_arm': sem('left_arm', x=[(0, -112), (200, -138), (400, -112)], z=[(0, 28), (200, 42), (400, 28)]),
        'right_leg': sem('right_leg', x=[(0, -66), (200, -58), (400, -66)], z=[(0, 16), (200, 20), (400, 16)]),
        'left_leg': sem('left_leg', x=[(0, -66), (200, -58), (400, -66)], z=[(0, 16), (200, 20), (400, 16)]),
    }, duration=400, loop=True)

    return catalog


def build_avatar_catalog(catalog):
    """Player poses use the same axes and held-item transforms as the avatar model."""
    poses = {}
    right, left = 'right_arm', 'left_arm'

    def turn(*keys):
        return bone_doc(rot={'y': stops(*keys)})

    def swing(arm, start=0, duration=310):
        def channel(raised, lowered):
            keys = [(start, raised, 'out'), (start + duration // 2, lowered)]
            return [(0, 0, 'hold'), *keys] if start else keys

        return sem(arm, x=channel(-137, -61), y=channel(31, -29))

    def double_swing(first, second, beat):
        return anim({first: swing(first, duration=beat), second: swing(second, beat, beat)}, 2 * beat)

    def joined_grip(*pitch_keys):
        # The adult hand attachment is 10 units below and 2 forward of the arm pivot,
        # with a 1-unit offset toward the outside. Aim both grips 5 units inward.
        yaw_keys = []
        for time, pitch, *easing in pitch_keys:
            angle = math.radians(pitch)
            depth = 2 * math.cos(angle) - 10 * math.sin(angle)
            yaw = math.atan2(1, depth) + math.asin(5 / math.hypot(1, depth))
            yaw_keys.append((time, -round(math.degrees(yaw), 2), *easing))
        return {arm: sem(arm, x=pitch_keys, y=yaw_keys) for arm in (right, left)}

    poses['chaos_strike'] = double_swing(right, left, 150)
    poses['chaos_strike_left'] = double_swing(left, right, 150)

    down_cut_ready = anim({right: sem(right, x=[(0, -138)], y=[(0, 32)], z=[(0, -6)])}, 120)
    down_cut = anim({
        right: sem(right, x=[(0, -138, 'inout'), (155, -49), (280, -48), (360, -36)],
                   y=[(0, 32, 'inout'), (155, -58), (280, -58), (360, -51)],
                   z=[(0, -6, 'inout'), (155, -18), (360, -12)]),
    }, 360)
    for name in ('ghost', 'spirit_crescent'):
        poses[name] = down_cut_ready
    for name in ('ghost_release', 'ghost_orb', 'phantom_slash'):
        poses[name] = down_cut

    poses['frenzy'] = anim({
        'head': bone_doc(rot={'x': stops((0, 0), (245, -26, 'out'), (760, -20))}),
        right: sem(right, x=[(0, 0), (245, -30, 'out'), (760, -26)],
                   z=[(0, 0, 'out'), (245, 78), (760, 73)]),
        left: sem(left, x=[(0, 0), (245, -30, 'out'), (760, -26)],
                  z=[(0, 0, 'out'), (245, 78), (760, 73)]),
    }, 760)
    poses['frenzy_finish'] = anim({
        'head': bone_doc(rot={'x': stops((0, 0), (210, -32, 'out'), (560, -30), (760, 0)),
                              'z': stops((0, 0), (200, 8), (310, -5), (410, 6), (570, -4), (760, 0))}),
        right: sem(right, x=[(0, 0), (150, -66), (245, -126), (335, -102), (420, -120), (550, -106), (760, 0)],
                   y=[(0, 0), (160, 38), (245, 2), (760, 0)], z=[(0, 0), (245, 53), (550, 63), (760, 0)]),
        left: sem(left, x=[(0, 0), (155, -72), (245, -120), (340, -101), (430, -124), (550, -110), (760, 0)],
                  y=[(0, 0), (155, 40), (245, 2), (760, 0)], z=[(0, 0), (245, 53), (550, 63), (760, 0)]),
    }, 760)
    raise_left = anim({left: sem(left, x=[(0, -12), (150, -155, 'out'), (335, -154), (510, 0)],
                                      z=[(0, 0), (150, 16), (335, 16), (510, 0)])}, 510)
    for name in ('blood_rage', 'blood_sword', 'tombstone'):
        poses[name] = raise_left
    poses['agni'] = anim({
        left: sem(left, x=[(0, -12, 'out'), (150, -155)], z=[(0, 0, 'out'), (150, 16)]),
    }, 510)
    poses['blood_blade'] = anim({
        right: sem(right, x=[(0, -45), (110, -70), (200, -91, 'in'), (350, -92, 'hold'), (580, 0)],
                   y=[(0, 28), (110, 68), (200, 0, 'in'), (350, 0, 'hold'), (580, 0)],
                   z=[(0, 5), (110, 3), (200, 5), (580, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, 0), (110, -28), (200, -90), (350, -90), (580, 0))}),
    }, 580)
    poses['blood_eruption'] = anim({
        right: sem(right, x=[(0, -92), (250, -92), (510, -173, 'out'), (680, -161),
                             (850, -26, 'in'), (1090, 0)],
                   y=[(0, 0), (250, 0), (510, 12), (850, 0), (1090, 0)]),
        left: sem(left, x=[(0, -74), (250, -74), (510, -149), (680, -130), (850, -22), (1090, 0)],
                  y=[(0, 24), (250, 24), (510, 13), (850, 5), (1090, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -80), (250, -80), (510, -17), (850, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (250, 0), (500, -36), (850, 8), (1090, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (250, 0), (500, 32), (850, -13), (1090, 0)]),
    }, 1090)
    poses['blood_eruption']['heldModel'] = 'effect/blood_sword'
    poses['blood_sword_finish'] = anim({
        right: sem(right, x=[(0, -148), (145, -177, 'out'), (205, -157), (300, -16, 'in'), (585, 0)],
                   y=[(0, 18), (145, 8), (300, 6), (585, 0)]),
        left: sem(left, x=[(0, -118), (145, -154), (205, -139), (300, -19, 'in'), (585, 0)],
                  y=[(0, 28), (145, 27), (300, 22), (585, 0)]),
        'head': bone_doc(rot={'x': stops((0, -8), (145, -16), (300, 12), (585, 0))}),
    }, 585)
    poses['blood_sword_finish']['heldModel'] = 'effect/blood_sword'

    draw_pitch, draw_yaw = -90, -75
    draw_item_yaw, draw_item_roll = 185, -90
    poses['draw'] = anim({
        right: sem(right, x=[(0, draw_pitch), (535, draw_pitch), (800, -30)],
                   y=[(0, draw_yaw, 'hold'), (335, draw_yaw, 'inout'), (505, 66), (800, 55)]),
        left: sem(left, x=[(0, -14), (180, -32), (410, -45), (800, 0)]),
        # With the arm level, the item roll lays the blade flat. Its yaw lets
        # the tip trail behind the grip and align with the arm at 90 degrees.
        'held_item': bone_doc(rot={
            'y': stops((0, draw_item_yaw), (335, draw_item_yaw), (370, 189, 'inout'), (505, 90), (535, 90), (800, 0)),
            'z': stops((0, draw_item_roll), (535, draw_item_roll), (800, 0)),
        }),
    }, 800)
    poses['dragon'] = anim({
        left: sem(left, x=[(0, -70), (180, -121, 'out'), (250, -110)],
                  y=[(0, 47), (180, -42, 'out'), (250, -39)], z=[(0, 0), (180, 57), (250, 55)]),
        right: sem(right, x=[(0, -91), (180, -21, 'out'), (250, -18)],
                   y=[(0, 40), (180, -33), (250, -28)], z=[(0, 0), (180, 46), (250, 43)]),
    }, 250)
    poses['dance'] = anim({
        right: sem(right, x=[(0, -92), (70, -105), (125, -94), (195, -113), (250, -92)],
                   y=[(0, -48), (88, -59), (125, 62, 'in'), (210, 65), (250, -48, 'in')]),
        left: sem(left, x=[(0, -20)], z=[(0, 10)]),
    }, 250, loop=True)

    # Two diagonal cuts followed by a horizontal cut across the front.
    poses['kalla_finish'] = anim({
        right: sem(right, x=[(0, -138, 'out'), (150, -49), (185, -48), (300, -133, 'out'),
                             (450, -47), (490, -47), (600, -89, 'inout'), (760, -89), (900, -34)],
                   y=[(0, 32, 'out'), (150, -55), (300, -60, 'out'),
                      (450, 54), (600, 66, 'inout'), (760, -65), (900, -45)]),
    }, 900)
    poses['moonlight_slash'] = anim({
        right: sem(right, x=[(0, -134, 'out'), (110, -52, 'in'), (175, -56), (220, -24)],
                   y=[(0, 46, 'out'), (110, -58, 'in'), (175, -48), (220, -22)],
                   z=[(0, 12), (110, -18), (220, 0)]),
        left: sem(left, x=[(0, -28), (110, -14), (220, 0)]),
    }, 220)
    poses['ghost_field'] = anim({
        left: sem(left, x=[(0, -30), (135, -93, 'in'), (245, -92, 'hold'), (420, 0)],
                  y=[(0, 13), (135, 0), (420, 0)]),
    }, 420)
    up_cut = anim({
        'root': turn((0, -42, 'inout'), (225, 65, 'out'), (365, 35.5), (570, 35.5)),
        **joined_grip((0, -53, 'inout'), (225, -117, 'out'), (365, -90), (570, -90)),
        'held_item': bone_doc(rot={'x': stops((0, -90))}),
    }, 570)
    for name in ('wave', 'ice_wave', 'fire_wave'):
        poses[name] = up_cut

    # A wind-up, the final real-time 350 ms of chanting, and the released pose
    # are separate clips so the transition lands on the actual release frame.
    side_chant = anim({
        right: sem(right, x=[(0, -28), (150, -84), (320, -84, 'hold')],
                   y=[(0, 10), (150, 66), (320, 66, 'hold')]),
        left: sem(left, x=[(0, -12), (150, -34), (320, -34, 'hold')]),
    }, 320)
    cross_body_chant = anim({
        right: sem(right, x=[(0, -28), (150, draw_pitch, 'hold')],
                   y=[(0, 10), (150, draw_yaw, 'hold')]),
        left: side_chant['bones'][left],
        'held_item': bone_doc(rot={
            'y': stops((0, 0), (150, draw_item_yaw, 'hold')),
            'z': stops((0, 0), (150, draw_item_roll, 'hold')),
        }),
    }, 320)
    point_prepare = anim({
        right: sem(right, x=[(0, draw_pitch), (180, draw_pitch), (350, -93, 'in')],
                   y=[(0, draw_yaw), (180, -69), (350, 0, 'in')]),
        left: sem(left, x=[(0, -34), (350, -26)]),
        'held_item': bone_doc(rot={
            'x': stops((0, 0), (180, -22), (350, -90)),
            'y': stops((0, draw_item_yaw), (180, draw_item_yaw), (350, 0)),
            'z': stops((0, draw_item_roll), (180, draw_item_roll), (350, 0)),
        }),
    }, 350)
    point_release = anim({
        right: sem(right, x=[(0, -93), (125, -98, 'out'), (250, -93), (430, 0)]),
        left: sem(left, x=[(0, -26), (430, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -90), (245, -90), (430, 0))}),
    }, 430)
    for name in ('moon_flow', 'sunfire', 'solar_flare'):
        poses[name + '_chant'] = cross_body_chant
        poses[name + '_prepare'] = point_prepare
        poses[name] = point_release
    poses['chant'] = side_chant

    raised = {}
    push = {}
    released = {}
    for arm in (right, left):
        raised[arm] = sem(arm, x=[(0, -35), (180, -135, 'out'), (350, -135, 'hold')],
                          y=[(0, 2), (180, 18), (350, 18, 'hold')], z=[(0, 0), (180, 11), (350, 11)])
        push[arm] = sem(arm, x=[(0, -135), (235, -128), (350, -90, 'in')],
                        y=[(0, 18), (235, 16), (350, 9)], z=[(0, 11), (350, 4)])
        released[arm] = sem(arm, x=[(0, -90), (110, -94, 'out'), (235, -88), (450, 0)],
                            y=[(0, 9), (235, 9), (450, 0)], z=[(0, 4), (450, 0)])
    for name in ('corona', 'moon_starfall'):
        poses[name + '_chant'] = anim(raised, 350)
        poses[name + '_prepare'] = anim(push, 350)
        poses[name] = anim(released, 450)

    poses['moon_reflux'] = anim({
        right: sem(right, x=[(0, -147), (195, -92), (370, 12), (615, -152), (800, 0)],
                   y=[(0, 0), (195, 71), (370, 43), (615, -8), (800, 0)],
                   z=[(0, 12), (370, 10), (615, 32), (800, 0)]),
        left: sem(left, x=[(0, 10), (195, -105), (370, -151), (615, -45), (800, 0)],
                  y=[(0, -48), (195, -49), (370, 3), (615, 65), (800, 0)],
                  z=[(0, 38), (370, 12), (615, 26), (800, 0)]),
    }, 800)
    poses['paradox'] = anim({
        'root': root(py=[(0, 0), (135, -5, 'out'), (230, -5, 'hold'), (350, 0, 'in')]),
        right: sem(right, x=[(0, -14), (130, -44, 'out'), (245, -43), (350, 0)],
                   z=[(0, 0), (130, 75, 'out'), (245, 74), (350, 0)]),
        left: sem(left, x=[(0, -14), (130, -44, 'out'), (245, -43), (350, 0)],
                  z=[(0, 0), (130, 75, 'out'), (245, 74), (350, 0)]),
    }, 350)

    poses['immolation_aura'] = anim({
        'root': bone_doc(rot={'y': stops((0, 0), (470, 360, 'linear'), (600, 360, 'hold'))},
                         pos={'y': stops((0, 0), (230, -7, 'out'), (495, 0, 'in'), (600, 0))}),
        right: sem(right, x=[(0, -13), (145, -67), (400, -52), (600, 0)], z=[(0, 7), (145, 50), (490, 46), (600, 0)]),
        left: sem(left, x=[(0, -13), (145, -67), (400, -52), (600, 0)], z=[(0, 7), (145, 50), (490, 46), (600, 0)]),
        'right_leg': sem('right_leg', x=[(0, 0), (200, -36), (490, 0), (600, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (200, 29), (490, 0), (600, 0)]),
    }, 600)
    poses['eye_beam_flip'] = anim({
        # A rotation around the middle of the model instead of the neck pivot.
        'root': bone_doc(rot={'x': stops((0, 0, 'linear'), (200, -360))},
                         pos={'y': stops((0, 0), (50, 12), (100, 24), (150, 12), (200, 0)),
                              'z': stops((0, 0), (50, 12), (100, 0), (150, -12), (200, 0))}),
        right: sem(right, x=[(0, -20), (105, -84), (200, -43)], z=[(0, 0), (200, 21)]),
        left: sem(left, x=[(0, -20), (105, -84), (200, -43)], z=[(0, 0), (200, 21)]),
        'right_leg': sem('right_leg', x=[(0, 0), (100, -52), (200, -12)]),
        'left_leg': sem('left_leg', x=[(0, 0), (100, -62), (200, 9)]),
    }, 200)
    poses['eye_beam'] = anim({
        right: sem(right, x=[(0, -43), (325, -37), (650, -43)], z=[(0, 21), (325, 17), (650, 21)]),
        left: sem(left, x=[(0, -43), (325, -38), (650, -43)], z=[(0, 21), (325, 17), (650, 21)]),
        'right_leg': sem('right_leg', x=[(0, -12)]),
        'left_leg': sem('left_leg', x=[(0, 9)]),
    }, 650, loop=True)
    rush = anim({
        right: sem(right, x=[(0, -61), (100, -93, 'out'), (420, -93, 'hold')], z=[(0, 14), (420, 14)]),
        left: sem(left, x=[(0, 42), (100, 61, 'out'), (420, 61, 'hold')], z=[(0, 8), (420, 8)]),
        'right_leg': sem('right_leg', x=[(0, -15), (100, -32), (420, -32)]),
        'left_leg': sem('left_leg', x=[(0, 22), (100, 32), (420, 32)]),
    }, 420)
    poses['fel_rush'] = poses['the_hunt'] = rush
    poses['the_hunt_combo'] = anim({
        right: sem(right, x=[(0, -133), (100, -25, 'in'), (200, -133, 'out')], y=[(0, -20), (100, 40), (200, -20)]),
        left: sem(left, x=[(0, -25), (100, -133, 'out'), (200, -25, 'in')], y=[(0, 40), (100, -20), (200, 40)]),
    }, 200, loop=True)

    poses['oracle_smile'] = anim({
        'root': turn((0, 0), (510, 360, 'linear'), (600, 360, 'hold')),
        right: sem(right, x=[(0, -132), (110, -81), (290, -86), (475, -68), (600, 0)],
                   y=[(0, 26), (110, -38), (290, -43), (475, -50), (600, 0)], z=[(0, 10), (110, 46), (475, 48), (600, 0)]),
        left: sem(left, x=[(0, -19), (110, -47), (475, -43), (600, 0)], z=[(0, 4), (110, 43), (475, 43), (600, 0)]),
    }, 600)
    poses['oracle_rift'] = anim({
        right: sem(right, x=[(0, -135, 'inout'), (245, -43), (390, -28), (650, 0)],
                   y=[(0, -65, 'inout'), (245, 53), (390, 48), (650, 0)],
                   z=[(0, 13), (245, 39), (650, 0)]),
        left: sem(left, x=[(0, -129, 'inout'), (245, -48), (390, -30), (650, 0)],
                  y=[(0, -62, 'inout'), (245, 53), (390, 48), (650, 0)],
                  z=[(0, 11), (245, 39), (650, 0)]),
    }, 650)
    poses['replica_combo'] = anim({right: sem(right, x=[(0, -131), (90, -27, 'in'), (100, -18)],
                                               y=[(0, -20), (90, 52), (100, 62)])}, 100)
    poses['replica_finish'] = anim({
        right: sem(right, x=[(0, -30), (480, -160, 'out'), (600, -147), (800, -25, 'in'), (1150, 0)]),
        left: sem(left, x=[(0, -35), (480, -134, 'out'), (600, -130), (800, -17, 'in'), (1150, 0)]),
    }, 1150)

    throw_root = root(x=[(0, -6, 'out'), (120, -24, 'inout'), (200, 17), (285, 14), (400, 0)])
    throw_release = [(200, -106), (285, -100), (400, -18)]
    throw_yaw = [(0, 8), (120, 16), (200, -17), (400, 0)]
    poses['throw_glaive'] = anim({
        'root': throw_root,
        right: sem(right, x=[(0, 18, 'out'), (120, 66, 'inout'), *throw_release],
                   y=throw_yaw, z=[(0, 20), (120, 43), (200, 8), (400, 0)]),
    }, 400)
    poses['magic_star'] = anim({
        'root': throw_root,
        right: sem(right, x=[(0, -55, 'out'), (120, -198, 'inout'), *throw_release],
                   y=throw_yaw, z=[(0, 20), (120, -8), (200, 8), (400, 0)]),
    }, 400)

    overhead_throw = [(0, -62, 'out'), (210, -216), (345, -216, 'inout'), (500, -96)]
    poses['jack_bomb_chant'] = anim({
        'root': root(x=[(0, 0, 'out'), (210, -24), (345, -24, 'inout'), (500, 10)]),
        right: sem(right, x=overhead_throw),
        left: sem(left, x=overhead_throw),
    }, 500)
    poses['jack_bomb'] = anim({
        'root': root(x=[(0, 10), (185, 5), (380, 0)]),
        right: sem(right, x=[(0, -96), (185, -81), (380, 0)]),
        left: sem(left, x=[(0, -96), (185, -81), (380, 0)]),
    }, 380)
    poses['dragon_fang'] = anim({
        'root': turn((0, 15), (95, 18, 'in'), (200, 35.5), (340, 35.5), (475, 15)),
        **joined_grip((0, -60), (95, -51, 'in'), (200, -90), (340, -90), (475, -60)),
        'held_item': bone_doc(rot={'x': stops((0, -22), (95, -34, 'in'), (200, -90), (340, -90), (475, -22))}),
    }, 475)

    # Aliases read from the champion catalog share their limb keys, while the
    # avatar's torso and head stay connected under the common model root.
    for name in ('herder', 'four_spirits', 'louise', 'sandor', 'echeverria', 'kasijas'):
        original = catalog['louise']
        poses[name] = anim({bone: keys for bone, keys in original['bones'].items()
                            if bone not in ('body', 'root')}, original['duration'], original['loop'])
    for name in ('elementalist_charge', 'ice_wall', 'lightning_vortex', 'void_sphere', 'flame_pillar',
                 'halloween_buster', 'arctic_feast', 'black_hole', 'lightning_strike', 'astral_storm'):
        original = catalog[name]
        poses[name] = anim({bone: keys for bone, keys in original['bones'].items()
                            if bone not in ('body', 'root')}, original['duration'], original['loop'])

    # --- Battle Mage DNF-authentic avatar choreography (root turns/lunges/leaps + held_item spear alignment) ---
    # 1. Niu's Combat Art 3-hit basic spear/polearm combo
    poses['bm_basic_1'] = anim({
        'root': bone_doc(rot={'y': stops((0, -28), (115, 36, 'in'), (235, 30), (340, 0)),
                              'x': stops((0, 4), (115, 11, 'in'), (340, 0))}),
        right: sem(right, x=[(0, -112), (115, -66, 'in'), (235, -52), (340, 0)],
                   y=[(0, 46), (115, -52, 'in'), (235, -46), (340, 0)],
                   z=[(0, 22), (115, -10, 'in'), (340, 0)]),
        left: sem(left, x=[(0, -76), (115, -48, 'in'), (340, 0)],
                  y=[(0, -26), (115, 34, 'in'), (340, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -44), (115, -72, 'in'), (340, 0)),
                                   'z': stops((0, -48), (115, -72, 'in'), (340, 0))}),
    }, 340)
    poses['bm_basic_2'] = anim({
        'root': bone_doc(rot={'y': stops((0, 22), (95, 38, 'in'), (220, 38), (340, 0)),
                              'x': stops((0, 6), (95, 15, 'in'), (220, 12), (340, 0))}),
        **joined_grip((0, -48), (95, -90, 'in'), (220, -90), (340, -42)),
        'held_item': bone_doc(rot={'x': stops((0, -36), (95, -90, 'in'), (220, -90), (340, -16))}),
        'right_leg': sem('right_leg', x=[(0, -6), (95, 22, 'in'), (340, 0)]),
        'left_leg': sem('left_leg', x=[(0, 8), (95, -28, 'in'), (340, 0)]),
    }, 340)
    poses['bm_basic_3'] = anim({
        'root': bone_doc(rot={'y': stops((0, 0, 'linear'), (260, 360), (400, 360, 'hold'))},
                         pos={'y': stops((0, 0), (140, -3.5, 'out'), (280, 0, 'in'), (400, 0))}),
        right: sem(right, x=[(0, -48), (140, -126, 'out'), (260, -102), (400, 0)],
                   y=[(0, -30), (140, 24, 'out'), (400, 0)],
                   z=[(0, 18), (140, 28), (400, 0)]),
        left: sem(left, x=[(0, -42), (140, -106, 'out'), (400, 0)],
                  z=[(0, 14), (140, 32), (400, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -60), (140, -90), (260, -82), (400, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (140, -30), (280, 0), (400, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (140, 24), (280, 0), (400, 0)]),
    }, 400)

    # 2. Teana Battle Goddess golden lance 3-hit combo (elevated, wider, faster)
    poses['teana_basic_1'] = anim({
        'root': bone_doc(rot={'y': stops((0, 18), (85, 36, 'in'), (210, 34), (300, 0)),
                              'x': stops((0, 8), (85, 16, 'in'), (300, 0))},
                         pos={'y': stops((0, -1.5), (120, -2.5), (300, -1.0))}),
        **joined_grip((0, -52), (85, -92, 'in'), (210, -92), (300, -48)),
        'held_item': bone_doc(rot={'x': stops((0, -45), (85, -90, 'in'), (210, -90), (300, -25))}),
    }, 300)
    poses['teana_basic_2'] = anim({
        'root': bone_doc(rot={'y': stops((0, -55), (125, 72, 'in'), (230, 66), (320, 0))},
                         pos={'y': stops((0, -1.5), (125, -3.2, 'out'), (320, -1.0))}),
        right: sem(right, x=[(0, -90), (230, -90), (320, -32)],
                   y=[(0, -68), (125, 66, 'in'), (320, 0)]),
        left: sem(left, x=[(0, -60), (125, -42), (320, 0)], z=[(0, 26), (125, 42), (320, 0)]),
        'held_item': bone_doc(rot={'y': stops((0, 185), (125, 88, 'in'), (320, 0)),
                                   'z': stops((0, -90), (230, -90), (320, 0))}),
    }, 320)
    poses['teana_basic_3'] = anim({
        'root': bone_doc(rot={'y': stops((0, 0, 'linear'), (240, 360), (360, 360, 'hold')),
                              'x': stops((0, 6), (160, 18), (360, 0))},
                         pos={'y': stops((0, -1.5), (140, -5.2, 'out'), (360, -1.0))}),
        right: sem(right, x=[(0, -70), (140, -118, 'out'), (250, -94, 'in'), (360, 0)]),
        left: sem(left, x=[(0, -54), (140, -104, 'out'), (250, -82, 'in'), (360, 0)], z=[(0, 18), (140, 44), (360, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -70), (250, -90), (360, 0))}),
    }, 360)

    # 3. Palm Blast (落花掌) charge coil & explosive open-palm thrust + Teana Sky Piercer (苍天击)
    poses['palm_blast_charge'] = anim({
        'root': bone_doc(rot={'x': stops((0, 18), (260, 22, 'inout'), (520, 18)),
                              'y': stops((0, 38), (260, 42, 'inout'), (520, 38))},
                         pos={'y': stops((0, 2.0), (260, 2.5, 'inout'), (520, 2.0))}),
        right: sem(right, x=[(0, 32), (260, 38, 'inout'), (520, 32)],
                   y=[(0, -12), (260, -16, 'inout'), (520, -12)],
                   z=[(0, 14), (260, 18, 'inout'), (520, 14)]),
        left: sem(left, x=[(0, -58), (260, -66, 'inout'), (520, -58)],
                  y=[(0, -18), (260, -24, 'inout'), (520, -18)]),
        'held_item': bone_doc(rot={'x': stops((0, -82), (260, -86, 'inout'), (520, -82))}),
        'right_leg': sem('right_leg', x=[(0, 24), (260, 28, 'inout'), (520, 24)]),
        'left_leg': sem('left_leg', x=[(0, -30), (260, -34, 'inout'), (520, -30)]),
    }, 520, loop=True)
    poses['palm_blast'] = anim({
        'root': bone_doc(rot={'x': stops((0, 18), (120, 26, 'in'), (290, 20), (460, 0)),
                              'y': stops((0, 38), (120, -30, 'in'), (290, -24), (460, 0))},
                         pos={'y': stops((0, 2.0), (120, -1.0, 'out'), (290, 0), (460, 0))}),
        right: sem(right, x=[(0, 32), (120, 52, 'out'), (290, 44), (460, 0)],
                   z=[(0, 16), (120, 34, 'out'), (460, 0)]),
        left: sem(left, x=[(0, -58), (120, -104, 'in'), (290, -96), (460, 0)],
                  y=[(0, -18), (120, 8, 'in'), (460, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -82), (120, -76), (460, 0))}),
        'right_leg': sem('right_leg', x=[(0, 24), (120, -34, 'in'), (460, 0)]),
        'left_leg': sem('left_leg', x=[(0, -30), (120, 32, 'in'), (460, 0)]),
    }, 460)
    poses['teana_palm_blast'] = anim({
        'root': bone_doc(rot={'x': stops((0, 14), (140, 24, 'in'), (275, -10, 'out'), (460, 0)),
                              'y': stops((0, 28), (140, 36, 'in'), (275, -16, 'out'), (460, 0))},
                         pos={'y': stops((0, 0), (140, -3.5, 'out'), (275, -4.5), (460, 0))}),
        **joined_grip((0, -54), (140, -92, 'in'), (275, -138, 'out'), (460, -40)),
        'held_item': bone_doc(rot={'x': stops((0, -55), (140, -90, 'in'), (275, -96), (460, 0))}),
        'right_leg': sem('right_leg', x=[(0, 12), (140, 34, 'in'), (460, 0)]),
        'left_leg': sem('left_leg', x=[(0, -16), (140, -28, 'in'), (460, 0)]),
    }, 460)

    # 4. Full Swing (碎霸 210° flat horizontal crescent), Teana Full Swing (天地碎霸 360°), Ultimate Empress (终极女皇)
    poses['full_swing'] = anim({
        'root': bone_doc(rot={'y': stops((0, -68), (100, -76, 'out'), (205, 118, 'in'), (320, 108), (440, 0)),
                              'x': stops((0, 10), (205, 16, 'in'), (440, 0))},
                         pos={'y': stops((0, 1.0), (205, -0.8, 'out'), (440, 0))}),
        right: sem(right, x=[(0, -90), (320, -90), (440, -24)],
                   y=[(0, -66), (100, -75, 'hold'), (205, 72, 'in'), (320, 66), (440, 0)]),
        left: sem(left, x=[(0, -74), (205, -52, 'in'), (440, 0)],
                  y=[(0, 36), (205, -32, 'in'), (440, 0)]),
        'held_item': bone_doc(rot={'y': stops((0, 185), (100, 185), (205, 88, 'in'), (320, 88), (440, 0)),
                                   'z': stops((0, -90), (320, -90), (440, 0))}),
        'right_leg': sem('right_leg', x=[(0, -14), (205, 18, 'in'), (440, 0)]),
        'left_leg': sem('left_leg', x=[(0, 14), (205, -22, 'in'), (440, 0)]),
    }, 440)
    poses['teana_full_swing'] = anim({
        'root': bone_doc(rot={'y': stops((0, -40, 'linear'), (280, 320), (440, 360))},
                         pos={'y': stops((0, -1.0), (160, -4.5, 'out'), (340, -1.2), (440, 0))}),
        right: sem(right, x=[(0, -90), (320, -90), (440, -24)],
                   y=[(0, -65), (220, 74, 'in'), (440, 0)]),
        left: sem(left, x=[(0, -66), (220, -48), (440, 0)], z=[(0, 26), (220, 48), (440, 0)]),
        'held_item': bone_doc(rot={'y': stops((0, 185), (220, 90, 'in'), (440, 0)),
                                   'z': stops((0, -90), (320, -90), (440, 0))}),
    }, 440)
    poses['full_swing_empress'] = anim({
        'root': bone_doc(rot={'y': stops((0, 0, 'linear'), (290, 360), (560, 360, 'hold')),
                              'x': stops((0, -10), (160, -18, 'out'), (315, 30, 'in'), (450, 24), (560, 0))},
                         pos={'y': stops((0, 0), (160, -7.5, 'out'), (315, 2.2, 'in'), (450, 1.4), (560, 0))}),
        right: sem(right, x=[(0, -120), (160, -170, 'out'), (240, -170, 'hold'), (315, -22, 'in'), (450, -18), (560, 0)],
                   y=[(0, -24), (160, -14), (315, -22), (560, 0)]),
        left: sem(left, x=[(0, -116), (160, -164, 'out'), (240, -164, 'hold'), (315, -26, 'in'), (450, -20), (560, 0)],
                  y=[(0, -24), (160, -14), (315, -22), (560, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -20), (160, -10), (315, -88, 'in'), (450, -88), (560, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (160, -34), (315, 22, 'in'), (560, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (160, 26), (315, -28, 'in'), (560, 0)]),
    }, 560)

    # 5. Chaser Fusion (炫纹融合) & Chaser Press (炫纹强压 overhead gather -> earth slam!)
    poses['chaser_launcher'] = anim({
        left: sem(left, x=[(0, -40), (100, -96, 'out'), (240, 0)], y=[(0, 10), (100, -12), (240, 0)]),
    }, 240)
    poses['chaser_fusion'] = anim({
        'root': bone_doc(rot={'x': stops((0, -8), (150, 16, 'in'), (320, 10), (460, 0))},
                         pos={'y': stops((0, 0), (150, -1.5, 'out'), (460, 0))}),
        right: sem(right, x=[(0, -64), (150, -96, 'in'), (320, -88), (460, 0)],
                   y=[(0, 38), (150, -22, 'in'), (460, 0)], z=[(0, 24), (150, -6, 'in'), (460, 0)]),
        left: sem(left, x=[(0, -64), (150, -96, 'in'), (320, -88), (460, 0)],
                  y=[(0, 38), (150, -22, 'in'), (460, 0)], z=[(0, 24), (150, -6, 'in'), (460, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -40), (150, -90, 'in'), (320, -90), (460, 0))}),
    }, 460)
    poses['chaser_press'] = anim({
        'root': bone_doc(rot={'x': stops((0, -8), (180, -18, 'out'), (250, -18, 'hold'), (340, 32, 'in'), (480, 26), (620, 0))},
                         pos={'y': stops((0, 0), (180, -4.8, 'out'), (250, -4.8, 'hold'), (340, 2.4, 'in'), (480, 1.8), (620, 0))}),
        'head': bone_doc(rot={'x': stops((0, 0), (180, -20, 'out'), (340, 18, 'in'), (620, 0))}),
        right: sem(right, x=[(0, -76), (180, -168, 'out'), (250, -168, 'hold'), (340, -24, 'in'), (480, -20), (620, 0)],
                   y=[(0, -22), (180, -12), (340, -18), (620, 0)]),
        left: sem(left, x=[(0, -74), (180, -162, 'out'), (250, -162, 'hold'), (340, -26, 'in'), (480, -20), (620, 0)],
                  y=[(0, -22), (180, -12), (340, -18), (620, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -20), (180, -12), (250, -12, 'hold'), (340, -90, 'in'), (480, -90), (620, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (180, -26), (340, 26, 'in'), (620, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (180, 22), (340, -32, 'in'), (620, 0)]),
    }, 620)

    # 6. Punto Wave (流星闪影击 / 啦啦啦) rapid multi-angle spear thrust loop with 6 variants + sweeping finisher + VP1 Triple
    punto_beat = 110
    punto_loop = anim({
        'root': bone_doc(rot={'x': stops((0, 16), (55, 20, 'in'), (punto_beat, 16)),
                              'y': stops((0, 28), (55, 34, 'in'), (punto_beat, 28))},
                         pos={'y': stops((0, 1.4), (55, 1.8), (punto_beat, 1.4))}),
        right: sem(right, x=[(0, -52), (55, -94, 'in'), (punto_beat, -52)],
                   y=[(0, -14), (55, 6, 'in'), (punto_beat, -14)]),
        left: sem(left, x=[(0, -68), (55, -88, 'in'), (punto_beat, -68)],
                  y=[(0, -24), (55, -16, 'in'), (punto_beat, -24)]),
        'held_item': bone_doc(rot={'x': stops((0, -72), (55, -90, 'in'), (punto_beat, -72))}),
        'right_leg': sem('right_leg', x=[(0, 20), (punto_beat, 20)]),
        'left_leg': sem('left_leg', x=[(0, -24), (punto_beat, -24)]),
    }, punto_beat, loop=True)
    punto_variants = []
    for pitch, yaw, roll, body_y in (
        (-106, -18, 6, 22),   # high-left spear jab
        (-92, 2, 0, 34),      # dead-center pierce
        (-76, 18, -6, 38),    # low-right spear thrust
        (-108, 16, 10, 26),   # high-right lance pierce
        (-82, -20, -8, 20),   # low-left rapid jab
        (-96, 8, 4, 42),      # deep lunge thrust
    ):
        punto_variants.append({
            'root': bone_doc(rot={'y': stops((0, body_y - 6), (55, body_y, 'in'), (punto_beat, body_y - 6))}),
            right: sem(right, x=[(0, pitch + 36), (55, pitch, 'in'), (punto_beat, pitch + 36)],
                       y=[(0, yaw - 10), (55, yaw, 'in'), (punto_beat, yaw - 10)],
                       z=[(0, roll), (55, roll), (punto_beat, roll)]),
            'held_item': bone_doc(rot={'x': stops((0, -76), (55, -92, 'in'), (punto_beat, -76))}),
        })
    punto_loop['variants'] = punto_variants
    poses['punto_wave'] = poses['punto_wave_loop'] = punto_loop
    poses['punto_wave_finish'] = anim({
        'root': bone_doc(rot={'y': stops((0, 48), (150, -96, 'in'), (270, -86), (380, 0)),
                              'x': stops((0, 14), (150, 10, 'in'), (380, 0))}),
        right: sem(right, x=[(0, -90), (270, -90), (380, -20)],
                   y=[(0, 68), (150, -74, 'in'), (270, -68), (380, 0)]),
        left: sem(left, x=[(0, -68), (150, -40), (380, 0)], z=[(0, 16), (150, 36), (380, 0)]),
        'held_item': bone_doc(rot={'y': stops((0, 90), (150, 195, 'in'), (270, 190), (380, 0)),
                                   'z': stops((0, -90), (270, -90), (380, 0))}),
    }, 380)
    poses['punto_wave_triple'] = anim({
        'root': bone_doc(rot={'y': stops((0, 32), (110, -24, 'in'), (290, 36, 'in'), (480, -84, 'in'), (640, 0)),
                              'x': stops((0, 16), (480, 14), (640, 0))}),
        right: sem(right, x=[(0, -48), (110, -104, 'in'), (190, -52), (290, -86, 'in'), (380, -90), (480, -90, 'in'), (640, 0)],
                   y=[(0, -24), (110, 22), (290, -30), (380, 64), (480, -72, 'in'), (640, 0)]),
        left: sem(left, x=[(0, -64), (110, -96), (290, -80), (480, -48), (640, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -75), (110, -92), (290, -92), (480, -40), (640, 0)),
                                   'z': stops((0, 0), (370, 0), (480, -90), (640, 0))}),
    }, 640)

    # 7. Dragon Strike (强袭流星打) deep spear crouch charge & hyperspeed torpedo dash
    poses['dragon_strike_charge'] = anim({
        'root': bone_doc(rot={'x': stops((0, 26), (250, 30, 'inout'), (500, 26)),
                              'y': stops((0, 44), (250, 48, 'inout'), (500, 44))},
                         pos={'y': stops((0, 2.8), (250, 3.2, 'inout'), (500, 2.8))}),
        right: sem(right, x=[(0, 46), (250, 52, 'inout'), (500, 46)],
                   y=[(0, -16), (250, -20, 'inout'), (500, -16)],
                   z=[(0, 10), (250, 14, 'inout'), (500, 10)]),
        left: sem(left, x=[(0, -78), (250, -84, 'inout'), (500, -78)]),
        'held_item': bone_doc(rot={'x': stops((0, -90), (250, -90), (500, -90))}),
        'right_leg': sem('right_leg', x=[(0, 32), (250, 36, 'inout'), (500, 32)]),
        'left_leg': sem('left_leg', x=[(0, -38), (250, -42, 'inout'), (500, -38)]),
    }, 500, loop=True)
    poses['dragon_strike'] = anim({
        'root': bone_doc(rot={'x': stops((0, 26), (110, 36, 'in'), (370, 32), (540, 0)),
                              'y': stops((0, 44), (110, 24, 'in'), (370, 24), (540, 0))},
                         pos={'y': stops((0, 2.4), (110, -2.2, 'out'), (370, -1.6), (540, 0))}),
        right: sem(right, x=[(0, 46), (110, -96, 'in'), (370, -94, 'hold'), (540, 0)],
                   y=[(0, -16), (110, 4, 'in'), (540, 0)]),
        left: sem(left, x=[(0, -78), (110, 38, 'in'), (370, 42), (540, 0)],
                  z=[(0, 12), (110, 40, 'in'), (540, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -90), (370, -90), (540, 0))}),
        'right_leg': sem('right_leg', x=[(0, 32), (110, 48, 'in'), (370, 44), (540, 0)]),
        'left_leg': sem('left_leg', x=[(0, -38), (110, 32, 'in'), (370, 28), (540, 0)]),
    }, 540)

    # 8. Dragon Spear (煌龙偃月 impale -> dragon surge hold -> explosive lift sweep) + Cleave VP1
    poses['dragon_spear'] = anim({
        'root': bone_doc(rot={'y': stops((0, 15), (160, 36, 'in'), (1450, 36, 'hold'), (1680, 12, 'out'), (1880, -68, 'in'), (2100, 0)),
                              'x': stops((0, 8), (160, 18, 'in'), (1450, 16), (1680, -14), (1880, 22, 'in'), (2100, 0))},
                         pos={'y': stops((0, 0), (160, 1.2), (1450, 1.2), (1680, -3.5, 'out'), (1880, 1.0, 'in'), (2100, 0))}),
        right: sem(right, x=[(0, -42), (160, -90, 'in'), (1450, -90, 'hold'), (1680, -154, 'out'), (1880, -34, 'in'), (2100, 0)],
                   y=[(0, -32), (160, -35, 'in'), (1450, -35, 'hold'), (1680, -16), (1880, -28), (2100, 0)]),
        left: sem(left, x=[(0, -42), (160, -90, 'in'), (1450, -90, 'hold'), (1680, -146, 'out'), (1880, -38, 'in'), (2100, 0)],
                  y=[(0, -32), (160, -35, 'in'), (1450, -35, 'hold'), (1680, -16), (1880, -28), (2100, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -34), (160, -90, 'in'), (1450, -90, 'hold'), (1680, -30), (1880, -86, 'in'), (2100, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (160, 24, 'in'), (1450, 24), (1880, -18), (2100, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (160, -28, 'in'), (1450, -28), (1880, 22), (2100, 0)]),
    }, 2100)
    poses['dragon_spear_thrust'] = anim({
        'root': bone_doc(rot={'y': stops((0, 15), (150, 38, 'in'), (320, 36), (460, 0)),
                              'x': stops((0, 8), (150, 18, 'in'), (460, 0))}),
        **joined_grip((0, -42), (150, -92, 'in'), (320, -90), (460, -36)),
        'held_item': bone_doc(rot={'x': stops((0, -34), (150, -90, 'in'), (320, -90), (460, 0))}),
    }, 460)
    poses['dragon_spear_cleave'] = anim({
        'root': bone_doc(rot={'x': stops((0, -6), (180, -20, 'out'), (250, -20, 'hold'), (360, 30, 'in'), (500, 24), (640, 0))},
                         pos={'y': stops((0, 0), (180, -9.0, 'out'), (250, -9.0, 'hold'), (360, 2.2, 'in'), (500, 1.4), (640, 0))}),
        'head': bone_doc(rot={'x': stops((0, 0), (180, -22), (360, 16), (640, 0))}),
        right: sem(right, x=[(0, -50), (180, -172, 'out'), (250, -172, 'hold'), (360, -18, 'in'), (500, -16), (640, 0)],
                   y=[(0, -26), (180, -12), (360, -18), (640, 0)]),
        left: sem(left, x=[(0, -48), (180, -166, 'out'), (250, -166, 'hold'), (360, -22, 'in'), (500, -18), (640, 0)],
                  y=[(0, -26), (180, -12), (360, -18), (640, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -20), (180, -10), (360, -88, 'in'), (500, -88), (640, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (180, -38), (360, 24, 'in'), (640, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (180, 28), (360, -30, 'in'), (640, 0)]),
    }, 640)

    # 9. Teana Transformation (变身：贝亚娜翼展跃升) & Apostle Spear Finisher (使徒穿刺)
    poses['teana_transformation'] = anim({
        'root': bone_doc(rot={'x': stops((0, 14), (200, 20, 'in'), (440, -18, 'out'), (620, -12), (760, 0))},
                         pos={'y': stops((0, 2.4), (200, 3.0), (440, -8.2, 'out'), (620, -5.0), (760, 0))}),
        'head': bone_doc(rot={'x': stops((0, 10), (200, 14), (440, -26, 'out'), (620, -18), (760, 0))}),
        right: sem(right, x=[(0, -24), (200, -48), (440, -118, 'out'), (620, -92), (760, 0)],
                   z=[(0, 10), (200, 28), (440, 74, 'out'), (620, 62), (760, 0)]),
        left: sem(left, x=[(0, -24), (200, -48), (440, -118, 'out'), (620, -92), (760, 0)],
                  z=[(0, 10), (200, 28), (440, 74, 'out'), (620, 62), (760, 0)]),
        'right_leg': sem('right_leg', x=[(0, 14), (440, -26, 'out'), (760, 0)]),
        'left_leg': sem('left_leg', x=[(0, -14), (440, 22, 'out'), (760, 0)]),
    }, 760)
    poses['teana_finisher'] = anim({
        'root': bone_doc(rot={'y': stops((0, 0, 'linear'), (420, 360), (1400, 360, 'hold')),
                              'x': stops((0, -10), (380, -20, 'out'), (560, -18, 'hold'), (780, 36, 'in'), (1120, 30), (1400, 0))},
                         pos={'y': stops((0, 0), (380, -7.5, 'out'), (560, -7.5, 'hold'), (780, -2.5, 'in'), (1120, -1.5), (1400, 0))}),
        right: sem(right, x=[(0, -50), (380, -164, 'out'), (560, -162, 'hold'), (780, -96, 'in'), (1120, -92), (1400, 0)],
                   z=[(0, 12), (380, 44), (780, 6, 'in'), (1400, 0)]),
        left: sem(left, x=[(0, -46), (380, -148, 'out'), (780, 34, 'in'), (1120, 30), (1400, 0)],
                  z=[(0, 12), (380, 48), (780, 40), (1400, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -24), (380, -18), (560, -18, 'hold'), (780, -90, 'in'), (1120, -90), (1400, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (380, -32), (780, 42, 'in'), (1400, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (380, 24), (780, 26, 'in'), (1400, 0)]),
    }, 1400)
    for clip_name in ('teana_basic_1', 'teana_basic_2', 'teana_basic_3',
                      'teana_palm_blast', 'teana_full_swing',
                      'teana_transformation', 'teana_finisher'):
        poses[clip_name]['heldModel'] = 'effect/teana_spear'
    for clip_name in ('dragon_spear', 'dragon_spear_thrust', 'dragon_spear_cleave'):
        poses[clip_name]['heldModel'] = 'effect/dragon_halberd'

    # Witch (魔道学者 / 魔术师) player avatar clips
    # 1. Elytra-style broomstick gliding riding pose: seated legs, right hand gripping broom,
    #    left hand extended for balance/casting, equipped BROOM shifted horizontally beneath seated legs!
    poses['witch_broom_glide'] = anim({
        'root': root(py=[(0, -2.5), (450, -3.5), (900, -2.5)], x=[(0, 10), (450, 13), (900, 10)]),
        right: sem(right, x=[(0, -38), (450, -42), (900, -38)], y=[(0, -18), (450, -16), (900, -18)], z=[(0, 8), (450, 10), (900, 8)]),
        left: sem(left, x=[(0, -64), (450, -72), (900, -64)], z=[(0, 18), (450, 24), (900, 18)]),
        'right_leg': sem('right_leg', x=[(0, -66), (450, -62), (900, -66)], z=[(0, 14), (450, 16), (900, 14)]),
        'left_leg': sem('left_leg', x=[(0, -56), (450, -60), (900, -56)], z=[(0, 14), (450, 12), (900, 14)]),
        'held_item': bone_doc(
            rot={'x': stops((0, 72), (450, 72), (900, 72)), 'y': stops((0, 8), (450, 8), (900, 8)), 'z': stops((0, -12), (450, -12), (900, -12))},
            pos={'x': stops((0, -4.5), (450, -4.5), (900, -4.5)), 'y': stops((0, 4.5), (450, 4.5), (900, 4.5)), 'z': stops((0, -2.0), (450, -2.0), (900, -2.0))},
        ),
    }, 900, loop=True)

    poses['giant_flyswatter'] = anim({
        'root': root(py=[(0, 0), (140, -1.5, 'out'), (240, 1.0, 'in'), (500, 0)]),
        right: sem(right, x=[(0, -42), (140, -172, 'out'), (240, -14, 'in'), (380, -18), (500, 0)],
                   z=[(0, 12), (140, 26), (240, 8), (500, 0)]),
        left: sem(left, x=[(0, -36), (140, -152, 'out'), (240, -24, 'in'), (500, 0)], z=[(0, 10), (140, 22), (500, 0)]),
        'held_item': bone_doc(rot={'x': stops((0, -12), (140, -24), (240, 32, 'in'), (380, 24), (500, 0))}),
        'right_leg': sem('right_leg', x=[(0, 0), (140, -18), (240, 22), (500, 0)]),
        'left_leg': sem('left_leg', x=[(0, 0), (140, 14), (240, -18), (500, 0)]),
    }, 500)
    poses['giant_flyswatter']['heldModel'] = 'effect/witch_flyswatter'
    poses['giant_flyswatter_giant'] = json.loads(json.dumps(poses['giant_flyswatter']))
    poses['giant_flyswatter_giant']['heldModel'] = 'effect/witch_flyswatter_giant'

    poses['raging_flame_furnace'] = anim({
        'root': root(py=[(0, -12.0), (100, -14.5, 'out'), (200, -11.5, 'in'), (300, -14.0, 'out'), (400, -12.0)]),
        right: sem(right, x=[(0, -72), (200, -88), (400, -72)], z=[(0, 18), (200, 26), (400, 18)]),
        left: sem(left, x=[(0, -72), (200, -88), (400, -72)], z=[(0, 18), (200, 26), (400, 18)]),
        'right_leg': sem('right_leg', x=[(0, -68), (200, -58), (400, -68)], z=[(0, 20), (200, 24), (400, 20)]),
        'left_leg': sem('left_leg', x=[(0, -68), (200, -58), (400, -68)], z=[(0, 20), (200, 24), (400, 20)]),
    }, 400, loop=True)

    poses['mega_drill'] = anim({
        'root': root(py=[(0, -7.5), (160, -8.5), (320, -7.5)], x=[(0, 12), (160, 15), (320, 12)]),
        right: sem(right, x=[(0, -86), (160, -94), (320, -86)], z=[(0, 14), (160, 18), (320, 14)]),
        left: sem(left, x=[(0, -86), (160, -94), (320, -86)], z=[(0, 14), (160, 18), (320, 14)]),
        'right_leg': sem('right_leg', x=[(0, -70), (160, -66), (320, -70)], z=[(0, 18), (160, 20), (320, 18)]),
        'left_leg': sem('left_leg', x=[(0, -70), (160, -66), (320, -70)], z=[(0, 18), (160, 20), (320, 18)]),
    }, 320, loop=True)

    poses['florae_collider'] = anim({
        'root': root(py=[(0, -6.0), (180, -7.0), (360, -6.0)]),
        right: sem(right, x=[(0, -96), (180, -108), (360, -96)], z=[(0, 26), (180, 34), (360, 26)]),
        left: sem(left, x=[(0, -96), (180, -108), (360, -96)], z=[(0, 26), (180, 34), (360, 26)]),
        'right_leg': sem('right_leg', x=[(0, -50), (180, -46), (360, -50)], z=[(0, 16), (180, 18), (360, 16)]),
        'left_leg': sem('left_leg', x=[(0, -50), (180, -46), (360, -50)], z=[(0, 16), (180, 18), (360, 16)]),
    }, 360, loop=True)

    poses['fusion_craft'] = anim({
        'root': root(py=[(0, -14.0), (200, -16.5), (400, -14.0)]),
        right: sem(right, x=[(0, -118), (200, -148), (400, -118)], z=[(0, 32), (200, 48), (400, 32)]),
        left: sem(left, x=[(0, -118), (200, -148), (400, -118)], z=[(0, 32), (200, 48), (400, 32)]),
        'right_leg': sem('right_leg', x=[(0, -68), (200, -60), (400, -68)], z=[(0, 18), (200, 22), (400, 18)]),
        'left_leg': sem('left_leg', x=[(0, -68), (200, -60), (400, -68)], z=[(0, 18), (200, 22), (400, 18)]),
    }, 400, loop=True)

    # Slice the individual strokes so multi-hit skills contribute one cut per beat.
    stroke_ranges = (
        ('ghost_release', 0, 165), ('triple', 100, 300), ('wave', 0, 245),
        ('rising', 110, 320), ('draw', 335, 515), ('dragon', 0, 220), ('dance', 0, 125),
        ('kalla_finish', 0, 180), ('kalla_finish', 300, 480), ('kalla_finish', 600, 790),
        ('blache', 1050, 1190), ('mountain_crash', 380, 640), ('blood_blade', 110, 230),
        ('blood_eruption', 680, 868), ('blood_sword_finish', 145, 320),
        ('oracle_down', 240, 325), ('oracle_thrust', 200, 320), ('oracle_sweep', 260, 525),
        ('oracle_smile', 0, 135), ('oracle_rift', 0, 260), ('replica_combo', 0, 100),
        ('replica_finish', 600, 830),
    )
    beat = 100
    dance = anim({left: poses['dance']['bones'][left]}, beat, loop=True)
    single_cut = sword_stroke(anim({right: swing(right)}, 310), 0, 155, beat)
    dance['variants'] = [single_cut] + [
        sword_stroke(poses[name] if name in poses else catalog[name], first, last, beat, use_root_yaw=name == 'wave')
        for name, first, last in stroke_ranges
    ]
    poses['dance'] = dance
    return poses


def validate(doc, name):
    bones = doc.get('bones')
    assert isinstance(bones, dict) and bones, f'{name}: bones missing'
    for bone, channels in bones.items():
        assert bone in BONES, f'{name}: unknown bone {bone}'
        for group in ('rot', 'pos'):
            for axis, channel in (channels.get(group) or {}).items():
                assert axis in ('x', 'y', 'z'), f'{name}: unknown axis {axis}'
                assert channel, f'{name}: empty channel {bone}.{group}.{axis}'
                previous = -1
                for key in channel:
                    assert len(key) in (2, 3), f'{name}: malformed key {key}'
                    t, v = key[0], key[1]
                    assert isinstance(t, (int, float)) and math.isfinite(t) and t >= 0, f'{name}: bad time {key}'
                    assert isinstance(v, (int, float)) and math.isfinite(v), f'{name}: bad value {key}'
                    assert t > previous, f'{name}: times must increase in {bone}.{group}.{axis}'
                    previous = t
                    if len(key) == 3:
                        assert key[2] in EASINGS, f'{name}: unknown easing {key[2]}'
                if doc.get('loop'):
                    first, last = channel[0][1], channel[-1][1]
                    assert abs(first - last) < 1e-6, (
                        f'{name}: loop channel {bone}.{group}.{axis} opens {first} but closes {last}; '
                        'looping animations must return to their opening pose')
    duration = doc.get('duration')
    if duration is not None:
        assert isinstance(duration, (int, float)) and math.isfinite(duration) and duration > 0, f'{name}: bad duration'
    if 'variants' in doc:
        assert doc['loop'] and duration and doc['variants'], f'{name}: variants require a loop with a duration'
        for index, variant in enumerate(doc['variants']):
            validate(anim(variant, duration), f'{name}[{index}]')


def write_avatar_catalog(catalog):
    destination = OUT / 'avatar'
    destination.mkdir(parents=True, exist_ok=True)
    for name, doc in sorted(build_avatar_catalog(catalog).items()):
        validate(doc, name)
        encoded = json.dumps(doc, ensure_ascii=False, separators=(',', ':'), sort_keys=True) + '\n'
        target = destination / (name + '.json')
        if not target.exists() or target.read_text(encoding='utf-8') != encoded:
            target.write_text(encoded, encoding='utf-8')


def main():
    catalog = build_catalog()
    OUT.mkdir(parents=True, exist_ok=True)
    expected = {name.lower() + '.json' for name in catalog}
    stale = [p.name for p in OUT.glob('*.json') if p.name.lower() not in expected]
    if stale:
        raise SystemExit('refusing to run: unexpected files in animation dir: ' + ', '.join(sorted(stale)))
    for stale in OUT.glob('*.json'):
        # covers renames like ATTACK.json -> attack.json
        if stale.name not in expected:
            stale.unlink()
    report = []
    for name in sorted(catalog):
        doc = catalog[name]
        validate(doc, name)
        text = json.dumps(doc, ensure_ascii=False, separators=(',', ':'), sort_keys=True)
        (OUT / (name.lower() + '.json')).write_text(text + '\n', encoding='utf-8')
        latest = 0
        for channels in doc['bones'].values():
            for group in ('rot', 'pos'):
                for channel in (channels.get(group) or {}).values():
                    latest = max(latest, channel[-1][0])
        report.append((name, int(doc.get('duration', latest)), doc.get('loop', False), len(doc['bones'])))
    print(f'wrote {len(report)} animations to {OUT}')
    for name, duration, loop, bones in report:
        print(f'  {name:62s} {duration:5d}ms loop={str(loop):5s} bones={bones}')
    write_avatar_catalog(catalog)
    return 0


if __name__ == '__main__':
    sys.exit(main())
