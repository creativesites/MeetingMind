#!/usr/bin/env python3
"""Generate a companion Rive project (rive.yaml + scene.rml) for one form.

    gen_companion.py zuri            -> tools/rive/companion/zuri/        (ships: real state machine inputs)
    gen_companion.py zuri --test     -> tools/rive/companion/build-test/zuri/  (same graph driven by view model
                                         properties, so `rive --data=state=3 --screenshot` can render any state)

Then build: `rive <dir> --once` writes <dir>/build/<form>.riv.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from forms import *  # noqa: E402,F401,F403
from rivegen import *  # noqa: E402,F401,F403

M0 = ("n", "mode", "eq", 0)
NC = ("b", "calm", False)
CALM = ("b", "calm", True)


def S(v):
    return ("n", "state", "eq", v)


def MODE(v):
    return ("n", "mode", "eq", v)


STATE_GE6 = ("n", "state", "ge", 6)  # Phase B states (6 Curious, 7 Proud, 8 Reading) fall back to Idle for now

POSE_TARGETS = {
    "idle": [[M0, NC, S(0)], [M0, NC, STATE_GE6]],
    "listening": [[M0, NC, S(1)]],
    "thinking": [[M0, NC, S(2)]],
    "celebA": [[MODE(6)], [M0, NC, S(3)]],
    "worried": [[M0, NC, S(4)]],
    "sleepy": [[M0, NC, S(5)]],
    "prayerful": [[MODE(1)]],
    "peaceful": [[MODE(2)], [M0, CALM]],
    "grateful": [[MODE(3)]],
    "joyful": [[MODE(4)]],
    "reflective": [[MODE(5)]],
}
FX_TARGETS = {
    "none": [[M0, NC, S(0)], [M0, NC, S(4)], [M0, NC, STATE_GE6], [MODE(1)], [MODE(2)], [MODE(5)], [M0, CALM]],
    "listening": [[M0, NC, S(1)]],
    "thinking": [[M0, NC, S(2)]],
    "celebA": [[MODE(6)], [M0, NC, S(3)]],
    "sleepy": [[M0, NC, S(5)]],
    "joyful": [[MODE(4)]],
    "grateful": [[MODE(3)]],
}
BLINK_TARGETS = {
    "idle": [[M0, NC, S(0)], [M0, NC, STATE_GE6]],
    "thinking": [[M0, NC, S(2)]],
    "worried": [[M0, NC, S(4)]],
    "reflective": [[MODE(5)]],
    "none": [[M0, NC, S(1)], [M0, NC, S(3)], [M0, NC, S(5)], [MODE(1)], [MODE(2)], [MODE(3)], [MODE(4)], [MODE(6)], [M0, CALM]],
}
BLINK_PERIOD = dict(idle=4.6, thinking=3.1, worried=3.8, reflective=4.2, none=1.0)


def grid(keys, cols=4, x0=160, dx=220, dy=130):
    return {k: (x0 + (i % cols) * dx, 60 + (i // cols) * dy) for i, k in enumerate(keys)}


def build(form, mode, bg=None):
    F = FORMS[form]
    ctx = Ctx(form)
    reg_defaults(ctx)
    reg_fx_defaults(ctx)
    children = F.build(ctx)
    smb = SM(ctx, mode)
    anims = []
    periods = dict(PERIOD)
    if form == "page":
        periods["thinking"] = 5.0

    def add(name, T, loop, tracks, layers):
        aid = ctx.nid()
        anims.append(anim_xml(ctx, name, T, loop, tracks, layers, aid))
        return aid

    # ---- pose layer animations
    pose_anim = {}
    for key in POSES:
        T = periods[key]
        dt = 1 / 60.0 if key == "celebrating" else 1 / 30.0
        if key == "listening":
            for lv in (0, 1):
                f = lambda t, lv=lv: {**common(pose("listening", t, float(lv)), F.cfg), **F.extra("listening", t, float(lv))}
                pose_anim[("listening", lv)] = add(f"Listening{lv * 100}", T, True, sample(f, T, dt), {"pose"})
            continue
        f = lambda t, key=key: {**common(pose(key, t, 0.0), F.cfg), **F.extra(key, t, 0.0)}
        jumps = {"trim0": 0.5, "trim1": 0.5, "trim2": 0.5, "caretX": 5, "caretY": 5} if form == "page" else None
        tracks = sample(f, T, dt if form != "page" or key != "thinking" else 1 / 60.0, jumps)
        if form == "page" and key == "thinking":  # blinking caret: hold keys every third of a second
            tracks["caretOp"] = [(k / 3.0, 1.0 if k % 2 == 0 else 0.25, "h") for k in range(int(T * 3))]
        pose_anim[key] = add(key.capitalize(), T, key not in ONESHOT, tracks, {"pose"})

    # ---- fx layer animations
    fx_anim = {}
    for kind in ("none", "thinking", "celebrating", "sleepy", "joyful", "grateful"):
        T = FX_PERIOD[kind]
        tracks = sample(lambda t, kind=kind: fx_tracks(kind, t), T, 1 / 30.0, FX_JUMPS) if kind != "none" else {}
        fx_anim[kind] = add("Fx" + kind.capitalize(), T, kind in ("thinking", "joyful", "grateful", "none"), tracks, {"fx"})
    LT = F.listen_fx_period
    for lv in (0, 1):
        f = lambda t, lv=lv: F.fx_listen(t, float(lv))
        fx_anim[("listening", lv)] = add(f"FxListening{lv * 100}", LT, True, sample(f, LT, 1 / 30.0, F.fx_listen_jumps), {"fx"})

    # ---- blink layer animations
    blink_anim = {}
    for kind, T in BLINK_PERIOD.items():
        if kind == "none":
            tracks = {}
        else:
            tracks = {"bl_blink": [(0.0, 1.0, "l"), (T - 0.16, 1.0, "l"), (T - 0.08, 0.12, "l"), (T, 1.0, "h")],
                      "bl_catch": [(0.0, 1.0, "h"), (T - 0.13, 0.0, "h"), (T - 0.03, 1.0, "h")]}
        blink_anim[kind] = add("Blink" + kind.capitalize(), T, True, tracks, {"blink"})

    # ---- state machine
    def mk_states(keys, spec):
        pos = grid(keys)
        out = {}
        for k in keys:
            d = dict(spec[k])
            d["id"] = ctx.nid()
            d["x"], d["y"] = pos[k]
            out[k] = d
        return out

    # pose layer
    pose_keys = ["idle", "listening", "thinking", "celebA", "celebB", "worried", "sleepy", "prayerful", "peaceful", "grateful", "joyful", "reflective", "nod"]
    pspec = {k: dict(kind="anim", anim=pose_anim[k]) for k in ("idle", "thinking", "worried", "sleepy", "prayerful", "peaceful", "grateful", "joyful", "reflective")}
    pspec["listening"] = dict(kind="blend", blend=[(pose_anim[("listening", 0)], 0), (pose_anim[("listening", 1)], 100)])
    pspec["celebA"] = dict(kind="anim", anim=pose_anim["celebrating"], reset=True)
    pspec["celebB"] = dict(kind="anim", anim=pose_anim["celebrating"], reset=True)
    pspec["nod"] = dict(kind="anim", anim=pose_anim["nod"], reset=True)
    pstates = mk_states(pose_keys, pspec)
    ptr = []
    for src in pose_keys:
        if src != "nod":
            ptr.append((src, "nod", [("t", "nod"), NC], 150, False))
        for tgt, alts in POSE_TARGETS.items():
            if tgt == src or (tgt == "celebA" and src == "celebB"):
                continue
            for conds in alts:
                ptr.append((src, tgt, conds, 250, src == "nod"))
        if src == "celebA":
            ptr.append((src, "celebB", [("t", "celebrate"), NC], 200, False))
        if src == "celebB":
            ptr.append((src, "celebA", [("t", "celebrate"), NC], 200, False))
    # the replay triggers must beat the generic transitions that cannot fire anyway: order is irrelevant

    # fx layer
    fx_keys = ["none", "listening", "thinking", "celebA", "celebB", "sleepy", "joyful", "grateful"]
    fspec = {
        "none": dict(kind="anim", anim=fx_anim["none"]),
        "listening": dict(kind="blend", blend=[(fx_anim[("listening", 0)], 0), (fx_anim[("listening", 1)], 100)]),
        "thinking": dict(kind="anim", anim=fx_anim["thinking"]),
        "celebA": dict(kind="anim", anim=fx_anim["celebrating"], reset=True),
        "celebB": dict(kind="anim", anim=fx_anim["celebrating"], reset=True),
        "sleepy": dict(kind="anim", anim=fx_anim["sleepy"], reset=True),
        "joyful": dict(kind="anim", anim=fx_anim["joyful"]),
        "grateful": dict(kind="anim", anim=fx_anim["grateful"]),
    }
    fstates = mk_states(fx_keys, fspec)
    ftr = []
    for src in fx_keys:
        for tgt, alts in FX_TARGETS.items():
            if tgt == src or (tgt == "celebA" and src == "celebB"):
                continue
            for conds in alts:
                ftr.append((src, tgt, conds, 250, False))
        if src == "celebA":
            ftr.append((src, "celebB", [("t", "celebrate"), NC], 0, False))
        if src == "celebB":
            ftr.append((src, "celebA", [("t", "celebrate"), NC], 0, False))

    # blink layer
    b_keys = ["none", "idle", "thinking", "worried", "reflective"]
    bspec = {k: dict(kind="anim", anim=blink_anim[k]) for k in b_keys}
    bstates = mk_states(b_keys, bspec)
    btr = []
    for src in b_keys:
        for tgt, alts in BLINK_TARGETS.items():
            if tgt == src:
                continue
            for conds in alts:
                btr.append((src, tgt, conds, 120, False))

    layers = (
        smb.layer("Pose", ctx.nid(), pstates, ptr, "idle")
        + smb.layer("Fx", ctx.nid(), fstates, ftr, "none")
        + smb.layer("Blink", ctx.nid(), bstates, btr, "none")
    )
    sm_xml = el("StateMachine", {"name": "Companion", "id": "0:7"}, smb.inputs_xml() + layers)

    art = el(
        "Artboard",
        {"defaultStateMachineId": "0:7", "viewModelId": VM_ID, "viewModelInstanceId": VM_INSTANCE, "styleId": "0:5",
         "width": 500, "height": 500, "name": F.name, "id": "0:2"},
        el("LayoutComponentStyle", {"name": "Artboard Style", "id": "0:5"}) + children
        + (el("Fill", {}, el("SolidColor", {"colorValue": "FF" + bg})) if bg else "") + sm_xml + "".join(anims),
    )
    return '<Rive version="1" kind="fragment">\n' + art + "\n" + view_model_xml(mode) + "\n</Rive>\n", ctx


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("form", choices=sorted(FORMS))
    ap.add_argument("--test", action="store_true", help="drive the graph from view model properties (never shipped)")
    ap.add_argument("--out", default=None)
    ap.add_argument("--bg", default=None, help="test builds: an opaque artboard backdrop (RRGGBB) to judge light and dark")
    a = ap.parse_args()
    here = os.path.dirname(os.path.abspath(__file__))
    out = a.out or os.path.join(here, "build-test" if a.test else "", a.form + (("-" + a.bg) if a.test and a.bg else ""))
    os.makedirs(out, exist_ok=True)
    xml, ctx = build(a.form, "vm" if a.test else "input", a.bg if a.test else None)
    with open(os.path.join(out, "scene.rml"), "w") as fh:
        fh.write(xml)
    with open(os.path.join(out, "rive.yaml"), "w") as fh:
        fh.write(f"name: {a.form}\n")
    print(f"{a.form}: {len(xml)//1024} KB rml, {len(ctx.targets)} params -> {out}")


if __name__ == "__main__":
    main()
