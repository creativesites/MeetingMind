"""The four companion forms: rigs (shape trees) and pose maths, ported from docs/mvp/zuri-options.html.

Each form provides:
  build(ctx)            -> artboard children (front to back), registering animatable parameters on ctx
  extra(key, t, L)      -> {param: value} for the form-specific channels of pose `key`
  fx_extra(kind, t, L)  -> form-specific FX tracks (rings, arcs, waves)
  cfg                   -> face constants used by the shared pose mapping
"""
import math

from rivegen import *  # noqa: F401,F403

HEART = "F4F47A8A"[2:]  # tongue / heart rose, baked on purpose (not an accent colour)
ROSE = "FFF47A8A"
WHITE = "FFFFFFFF"

POSES = ["idle", "listening", "thinking", "celebrating", "worried", "sleepy", "prayerful", "peaceful", "grateful", "joyful", "reflective", "nod"]
PERIOD = dict(idle=6.8, listening=4.8, thinking=4.5, celebrating=1.6, worried=7.0, sleepy=5.5, prayerful=6.0, peaceful=6.0, grateful=9.0, joyful=1.2, reflective=4.5, nod=0.6)
ONESHOT = {"celebrating", "nod"}


def sm(x0, x1, v):  # smoothstep from x0..x1
    if v <= x0:
        return 0.0
    if v >= x1:
        return 1.0
    u = (v - x0) / (x1 - x0)
    return u * u * (3 - 2 * u)


def lerp(a, b, u):
    return a + (b - a) * u


def breath(t, per, amp):
    v = math.sin(t * TAU / per)
    return 1 - amp * 0.55 * v, 1 + amp * v


def celeb_env(t, hop_scale=1.0):
    """The Celebrating hop from the brief (5): anticipation, hop, land squash, soft spring to 1.6 s.
    Returns (by_units, sx, sy, hop 0..1)."""
    if t < 0.12:
        u = sm(0, 0.12, t)
        return 0.0, lerp(1, 1.03, u), lerp(1, 0.94, u), 0.0
    if t < 0.37:  # up, ease-out
        u = math.sin((t - 0.12) / 0.25 * math.pi / 2)
        return -9 * hop_scale * u, lerp(1.03, 0.97, u), lerp(0.94, 1.05, u), u
    if t < 0.62:  # down, ease-in
        u = 1 - (1 - (t - 0.37) / 0.25) ** 2
        return -9 * hop_scale * (1 - u), lerp(0.97, 1.0, u), lerp(1.05, 1.0, u), 1 - u
    if t < 0.71:  # land squash
        u = sm(0.62, 0.71, t)
        return 0.0, lerp(1.0, 1.08, u), lerp(1.0, 0.92, u), 0.0
    if t < 0.80:
        u = sm(0.71, 0.80, t)
        return 0.0, lerp(1.08, 0.985, u), lerp(0.92, 1.02, u), 0.0
    d = t - 0.80  # damped spring
    k = math.exp(-3.4 * d) * math.cos(TAU * 1.6 * d)
    return 0.0, 1 - 0.015 * k, 1 + 0.02 * k, 0.0


def base_pose():
    return dict(by=0.0, sx=1.0, sy=1.0, rot=0.0, eye="open", lookX=0.0, lookY=0.0, mouth="smile", brows=False,
                hop=0.0, eyeScale=1.0, hands=False, cheeks=True)


def pose(key, t, L):
    p = base_pose()

    def br(per, amp):
        p["sx"], p["sy"] = breath(t, per, amp)

    if key == "idle":
        br(3.4, 0.022)
    elif key == "listening":
        b = math.sin(t * TAU / 2.4)
        p["sy"] = 1 + 0.035 * L + 0.01 * b
        p["sx"] = 1 + 0.014 * L
        p["rot"] = -5 + math.sin(t * TAU / 4.8) * 2
        p["eye"] = "soft"
        p["lookX"] = 0.6
    elif key == "thinking":
        p["rot"] = math.sin(t * TAU / 4.5) * 3
        p["lookX"], p["lookY"] = 1.1, -1.5
        p["mouth"] = "o"
    elif key == "celebrating":
        by, sx, sy, hop = celeb_env(t)
        p.update(by=by, sx=sx, sy=sy, hop=hop, eye="happy", mouth="open")
    elif key == "worried":
        p.update(sx=1.03, sy=0.95, by=1.5, rot=math.sin(t * TAU / 7.0) * 1.5, brows=True, mouth="wobble", lookY=0.7, eyeScale=0.88, cheeks=False)
    elif key == "sleepy":
        br(5.5, 0.025)
        p["sy"] -= 0.03
        p.update(by=2.0, rot=6.0, eye="closed", mouth="none")
    elif key == "prayerful":
        br(6, 0.008)
        p.update(by=1.0, eye="closed", mouth="none", hands=True)
    elif key == "peaceful":
        br(6, 0.015)
        p.update(eye="closed", mouth="smile")
    elif key == "grateful":
        br(4.5, 0.012)
        p.update(eye="happy", mouth="smile", rot=4 + math.sin(t * TAU / 9.0))
    elif key == "joyful":
        p.update(eye="happy", mouth="open", by=-abs(math.sin(math.pi * t / 1.2)) * 3)
    elif key == "reflective":
        br(4.5, 0.012)
        p.update(lookX=-1.0, lookY=-1.5, rot=-3.0)
    elif key == "nod":
        p.update(eye="happy", mouth="smile", by=3 * math.sin(math.pi * t / 0.6))
    return p


# ------------------------------------------------------------------ shared face


def reg_defaults(ctx):
    d = ctx.default
    d("bodyY", 0.0); d("bodyRot", 0.0); d("bodySX", 1.0); d("bodySY", 1.0); d("shadowSX", 1.0)
    d("eyeOpen", 1.0); d("eyeHappy", 0.0); d("eyeClosed", 0.0); d("eyeSX", 1.0); d("eyeSY", 1.0); d("eyeDY", 0.0)
    d("gazeX", 0.0); d("gazeY", 0.0)
    d("mouthSmile", 1.0); d("mouthOpen", 0.0); d("mouthO", 0.0); d("mouthWobble", 0.0)
    d("brows", 0.0); d("cheeks", 1.0); d("hands", 0.0)
    d("bl_blink", 1.0, "blink"); d("bl_catch", 1.0, "blink")


def common(p, cfg):
    eye = p["eye"]
    out = dict(
        bodyY=p["by"], bodySX=p["sx"], bodySY=p["sy"], bodyRot=p["rot"], shadowSX=1 - p["hop"] * 0.35,
        eyeOpen=1.0 if eye in ("open", "soft") else 0.0,
        eyeHappy=1.0 if eye == "happy" else 0.0,
        eyeClosed=1.0 if eye == "closed" else 0.0,
        eyeSX=p["eyeScale"], eyeSY=p["eyeScale"] * (0.6 if eye == "soft" else 1.0),
        eyeDY=cfg["eye_ry"] * p["eyeScale"] * 0.28 if eye == "soft" else 0.0,
        gazeX=p["lookX"], gazeY=p["lookY"],
        brows=1.0 if p["brows"] else 0.0, cheeks=1.0 if p["cheeks"] else 0.0, hands=1.0 if p["hands"] else 0.0,
    )
    m = p["mouth"]
    if m == "o" and not cfg.get("has_o", True):
        m = "smile"
    out.update(mouthSmile=1.0 if m == "smile" else 0.0, mouthOpen=1.0 if m == "open" else 0.0,
               mouthO=1.0 if m == "o" else 0.0, mouthWobble=1.0 if m == "wobble" else 0.0)
    return out


def eyes_xml(ctx, org, centers, rx, ry, sw, look=1.0):
    """Gaze node holding one blink node per eye. `org` is the parent origin; the node sits on it."""
    out = []
    for i, (cx, cy) in enumerate(centers):
        open_shape = node(
            ctx, "eyeOpen", 0, 0, (0, 0),
            ell(ctx, "catch", -0.3 * rx, -0.42 * ry, 0.36 * rx, 0.36 * rx, (0, 0), fill_raw(WHITE), op=0.92, reg=[("bl_catch", "opacity", "a", 0.92)])
            + ell(ctx, "eye", 0, 0, rx, ry, (0, 0), fill_c("eye")),
            reg=[("eyeOpen", "opacity", "a", 1.0), ("eyeSX", "scaleX", "a", 1.0), ("eyeSY", "scaleY", "a", 1.0), ("eyeDY", "y", "d", U)],
        )
        happy = path_shape(ctx, "eyeHappy", f"M{-rx*1.15} {ry*.35} Q0 {-ry*1.25} {rx*1.15} {ry*.35}", (0, 0), stroke_c("eye", sw), reg=[("eyeHappy", "opacity", "a", 1.0)])
        closed = path_shape(ctx, "eyeClosed", f"M{-rx*1.15} 0 Q0 {ry*.95} {rx*1.15} 0", (0, 0), stroke_c("eye", sw), reg=[("eyeClosed", "opacity", "a", 1.0)])
        out.append(node(ctx, f"eye{i}", cx, cy, org, open_shape + happy + closed, reg=[("bl_blink", "scaleY", "a", 1.0)]))
    return node(ctx, "gaze", org[0], org[1], org, "".join(out), reg=[("gazeX", "x", "d", U * look), ("gazeY", "y", "d", U * look)])


def brows_xml(ctx, org, centers, rx, ry, sw, rows=None):
    out = []
    for i, (x, y) in enumerate(centers):
        if i == 0:
            d = f"M{x-rx*1.4} {y-ry*1.35} L{x+rx*1.1} {y-ry*1.95}"
        else:
            d = f"M{x-rx*1.1} {y-ry*1.95} L{x+rx*1.4} {y-ry*1.35}"
        out.append(path_shape(ctx, f"brow{i}", d, org, stroke_c("eye", sw), reg=[("brows", "opacity", "a", 1.0)]))
    return "".join(out)


def cheeks_xml(ctx, org, pts, rx, ry):
    return "".join(ell(ctx, f"cheek{i}", x, y, rx, ry, org, fill_c("blush"), reg=[("cheeks", "opacity", "a", 1.0)]) for i, (x, y) in enumerate(pts))


def mouth_xml(ctx, org, x, y, w):
    smile = path_shape(ctx, "mouthSmile", f"M{x-w} {y} Q{x} {y+w*.85} {x+w} {y}", org, stroke_c("eye", w * 0.5), reg=[("mouthSmile", "opacity", "a", 1.0)])
    open_ = node(ctx, "mouthOpen", 0, 0, (0, 0),
                 ell(ctx, "tongue", x - org[0], y + w * 0.75 - org[1], w * 0.55, w * 0.3, (0, 0), fill_raw(ROSE))
                 + path_shape(ctx, "mouth", f"M{x-w*1.15} {y-.4} Q{x} {y+w*2} {x+w*1.15} {y-.4} Z", org, fill_c("eye")),
                 reg=[("mouthOpen", "opacity", "a", 1.0)])
    o = ell(ctx, "mouthO", x + 1, y + 0.4, w * 0.42, w * 0.5, org, fill_c("eye"), reg=[("mouthO", "opacity", "a", 1.0)])
    wob = path_shape(ctx, "mouthWobble", f"M{x-w} {y+.8} Q{x-w/2} {y-.7} {x} {y+.3} Q{x+w/2} {y+1.2} {x+w} {y-.1}", org, stroke_c("eye", w * 0.45), reg=[("mouthWobble", "opacity", "a", 1.0)])
    return open_ + o + wob + smile


def hands_xml(ctx, org, x, y, s=1.0, fill_prop="bodyTop"):
    pair = []
    for sgn in (-1, 1):
        cx = x + sgn * 2.6 * s
        pair.append(ell(ctx, "hand", cx, y, 3.6 * s, 6.4 * s, org, fill_c(fill_prop) + stroke_c("deep", 0.9), rot=-sgn * 18))
    return node(ctx, "hands", org[0], org[1], org, "".join(pair), reg=[("hands", "opacity", "a", 1.0)])


def shadow_xml(ctx, cy):
    return ell(ctx, "shadow", 50, cy, 18, 2.6, (0, 0), fill_c("shadow"), reg=[("shadowSX", "scaleX", "a", 1.0)])


# ------------------------------------------------------------------ fx (sparkles, dots, z's, heart)


def reg_fx_defaults(ctx):
    for i in range(4):
        ctx.default(f"fx_sOp{i}", 0.0, "fx"); ctx.default(f"fx_sSc{i}", 1.0, "fx")
    for i in range(3):
        ctx.default(f"fx_dotY{i}", 0.0, "fx"); ctx.default(f"fx_dotOp{i}", 0.0, "fx")
        ctx.default(f"fx_zX{i}", 0.0, "fx"); ctx.default(f"fx_zY{i}", 0.0, "fx"); ctx.default(f"fx_zS{i}", 1.0, "fx"); ctx.default(f"fx_zOp{i}", 0.0, "fx")
    ctx.default("fx_hY", 0.0, "fx"); ctx.default("fx_hOp", 0.0, "fx")


def fx_xml(ctx, sp, dots, z, heart_dy=4.0):
    org = (0, 0)
    out = []
    # heart
    hx, hy = sp[1][0] - 2, sp[1][1] + heart_dy
    s = 3.6
    d = f"M0 {s} C{-s*1.7} 0 {-s*.7} {-s*1.2} 0 {-s*.35} C{s*.7} {-s*1.2} {s*1.7} 0 0 {s} Z"
    out.append(node(ctx, "heart", hx, hy, org, path_shape(ctx, "heart", d, (0, 0), fill_raw(ROSE)), reg=[("fx_hY", "y", "d", U), ("fx_hOp", "opacity", "a", 0.9)], op=0.0))
    for i in range(3):
        out.append(node(ctx, f"z{i}", z[0], z[1], org,
                        path_shape(ctx, "z", "M-2.4 -3.6 H2.4 L-2.4 0 H2.4", (0, 0), stroke_c("mute", 1.3)),
                        reg=[(f"fx_zX{i}", "x", "d", U), (f"fx_zY{i}", "y", "d", U), (f"fx_zS{i}", "scaleX", "a", 1.0), (f"fx_zS{i}", "scaleY", "a", 1.0), (f"fx_zOp{i}", "opacity", "a", 1.0)], op=0.0))
    for i in range(3):
        out.append(ell(ctx, f"dot{i}", dots[0] + i * 6.5, dots[1], 2.1, 2.1, org, fill_c("sparkle"), op=0.0,
                       reg=[(f"fx_dotY{i}", "y", "d", U), (f"fx_dotOp{i}", "opacity", "a", 1.0)]))
    for i, (x, y, r) in enumerate(sp):
        out.append(node(ctx, f"sparkle{i}", x, y, org, shape(ctx, "star", star_geom(r), fill_c("gold" if i % 2 else "sparkle")),
                        reg=[(f"fx_sOp{i}", "opacity", "a", 1.0), (f"fx_sSc{i}", "scaleX", "a", 1.0), (f"fx_sSc{i}", "scaleY", "a", 1.0)], op=0.0))
    return node(ctx, "fx", 0, 0, org, "".join(out))


def fx_tracks(kind, t, sp_count=4):
    """Shared FX motion: returns {param: value} for the FX layer animation `kind` at time t."""
    o = {}
    if kind == "celebrating":
        fade = 1.0 if t <= 1.4 else max(0.0, 1 - (t - 1.4) / 0.2)
        for i in range(4):
            k = 0.55 + 0.45 * math.sin(t * 5 + i * 1.9)
            o[f"fx_sOp{i}"] = (0.45 + 0.55 * k) * fade
            o[f"fx_sSc{i}"] = k
    elif kind == "joyful":
        for i in range(2):
            k = 0.5 + 0.4 * math.sin(t * TAU / 2.1 + i * 2)
            o[f"fx_sOp{i}"] = 0.8
            o[f"fx_sSc{i}"] = k
    elif kind == "thinking":
        for i in range(3):
            dy = -max(0.0, math.sin(t * TAU / 1.5 - i * 0.7)) * 3.2
            o[f"fx_dotY{i}"] = dy
            o[f"fx_dotOp{i}"] = 0.45 + 0.55 * (-dy / 3.2)
    elif kind == "grateful":
        o["fx_hY"] = math.sin(t * TAU / 4.5) * 1.5
        o["fx_hOp"] = 0.9
    elif kind == "sleepy":
        place = [(0.30, 0.85), (0.68, 0.85), (0.30, 0.0)]
        for i in range(3):
            q = (t / 2.6 + i / 3) % 1.0
            op = math.sin(q * math.pi)
            if t > 7.0:
                w = sm(7.0, 7.8, t)
                pq, pop = place[i]
                q = lerp(q, pq, w)
                op = lerp(op, pop, w)
            o[f"fx_zX{i}"] = q * 10
            o[f"fx_zY{i}"] = -q * 16
            o[f"fx_zS{i}"] = (6 + q * 5) / 8.0
            o[f"fx_zOp{i}"] = op
    return o


FX_PERIOD = dict(none=0.5, thinking=1.5, celebrating=1.6, sleepy=7.8, joyful=2.1, grateful=4.5)
FX_JUMPS = {**{f"fx_zX{i}": 5 for i in range(3)}, **{f"fx_zY{i}": 8 for i in range(3)}, **{f"fx_zS{i}": 0.35 for i in range(3)}}


# ------------------------------------------------------------------ Zuri


class Zuri:
    name = "Zuri"
    cfg = dict(eye_ry=4.6)
    listen_fx_period = 1.5

    @staticmethod
    def build(ctx):
        org = (50, 88)
        ctx.default("haloY", 0.0)
        for i in range(3):
            ctx.default(f"bar{i}", [9.0, 12.0, 9.0][i])
        ctx.default("sproutLean", 0.0)
        ctx.default("fx_r0S", 1.0, "fx"); ctx.default("fx_r0Op", 0.0, "fx"); ctx.default("fx_r1S", 1.0, "fx"); ctx.default("fx_r1Op", 0.0, "fx")

        bars = ""
        for i, x in enumerate((43.5, 50, 56.5)):
            geom = rect_geom(4.4, 8.0, 2.2, 0.5, 1.0, ctx=ctx, reg=[(f"bar{i}", "height", "a", U)])
            bars += shape(ctx, f"bar{i}", geom, fill_c("deep"), px(x - 50), px(34 - 30))
        sprout = node(ctx, "sprout", 50, 30, org, bars, reg=[("sproutLean", "rotation", "d", RAD)])
        body_circle = shape(ctx, "bodyCircle", ellipse_geom(30, 30), bbox_gradient("bodyTop", "bodyBottom", 60, 60, 0.36, 0.30, 0.85), px(50 - org[0]), px(58 - org[1]))
        shine = ell(ctx, "shine", 39, 44, 8.5, 4.6, org, fill_raw(WHITE), rot=-35, op=0.4)
        face = (
            hands_xml(ctx, org, 50, 76)
            + mouth_xml(ctx, org, 50, 65, 3)
            + brows_xml(ctx, org, [(41, 56), (59, 56)], 3.3, 4.6, 1.8)
            + eyes_xml(ctx, org, [(41, 56), (59, 56)], 3.3, 4.6, 2.4)
            + cheeks_xml(ctx, org, [(33.5, 65), (66.5, 65)], 4.2, 2.5)
        )
        body = node(ctx, "body", 50, 88, (0, 0), face + shine + body_circle + sprout,
                    reg=[("bodyY", "y", "d", U), ("bodyRot", "rotation", "d", RAD), ("bodySX", "scaleX", "a", 1.0), ("bodySY", "scaleY", "a", 1.0)])
        shadow = shadow_xml(ctx, 93)
        rings = "".join(
            shape(ctx, f"ring{i}", ellipse_geom(31, 31), stroke_c("accent", 1.4), px(50), px(58), 0.0, 0.0,
                  reg=[(f"fx_r{i}S", "scaleX", "a", 1.0), (f"fx_r{i}S", "scaleY", "a", 1.0), (f"fx_r{i}Op", "opacity", "a", 1.0)])
            for i in range(2)
        )
        # the halo: a soft disc, feathered (the brief's radial gradient has no bindable transparent stop)
        halo = shape(ctx, "halo", ellipse_geom(31, 31),
                     el("Fill", {"fillRule": "clockwise"}, solid("halo") + el("Feather", {"strength": 55.0})), px(50), px(60), reg=[("haloY", "y", "d", U)])
        fx = fx_xml(ctx, [(16, 30, 5), (84, 24, 4), (88, 62, 3.5), (12, 64, 3.5)], (67, 20), (70, 32))
        return fx + body + shadow + rings + halo

    @staticmethod
    def extra(key, t, L):
        lean = 0.0
        hs = [5, 8, 5]
        if key == "listening":
            hs = [3.5 + L * 7 + (2 if i == 1 else 0) for i in range(3)]
        elif key == "thinking":
            hs = [4 + 2.5 * max(0.0, math.sin(t * TAU / 1.5 - i * 0.7)) for i in range(3)]
        elif key == "celebrating":
            hs = [8, 12, 8]
        elif key == "joyful":
            hs = [6 + 2 * math.sin(t * TAU / 1.2 + i) for i in range(3)]
        elif key in ("worried", "sleepy"):
            hs, lean = [3, 4, 3], 14.0
        elif key == "prayerful":
            hs, lean = [3, 5, 3], 10.0
        elif key == "peaceful":
            hs, lean = [4, 6, 4], 6.0
        o = {f"bar{i}": max(2.5, h) + 4 for i, h in enumerate(hs)}
        o["sproutLean"] = lean
        p = pose(key, t, L)
        o["haloY"] = p["by"] * 0.5
        return o

    @staticmethod
    def fx_listen(t, L):
        """Rings plus the sprout shimmer (the shimmer overrides the pose layer's calm bar heights)."""
        o = {}
        for i in range(2):
            k = (t / 1.5 + i / 2) % 1.0
            o[f"fx_r{i}S"] = 1 + k * 0.24 * (0.45 + L)
            o[f"fx_r{i}Op"] = (1 - k) * (0.18 + 0.55 * L)
        for i in range(3):
            h = 3.5 + L * (7 + 5 * math.sin(t * TAU / 0.75 + i * 1.9)) + (2 if i == 1 else 0)
            o[f"bar{i}"] = max(2.5, h) + 4
        return o

    fx_listen_jumps = {"fx_r0S": 0.1, "fx_r1S": 0.1, "fx_r0Op": 0.05, "fx_r1Op": 0.05}


# ------------------------------------------------------------------ Nas


class Nas:
    name = "Nas"
    cfg = dict(eye_ry=3.8, has_o=False)
    listen_fx_period = 0.6

    @staticmethod
    def build(ctx):
        org = (50, 91)
        ctx.default("ear", 12.0); ctx.default("headR", 0.0); ctx.default("headDy", 0.0); ctx.default("tail", 0.0)
        ctx.default("pawsDown", 1.0); ctx.default("pawsUp", 0.0)

        horg = (50, 60)
        # Nas's mouth lives under the nose: a stem and a "w" (open: a tongue; worried: a flat wobble)
        stem = path_shape(ctx, "stem", "M50 50.2 V52.2", horg, stroke_c("eye", 1.1))
        smile = path_shape(ctx, "mouthSmile", "M46.6 52.4 Q48.3 54.6 50 52.4 Q51.7 54.6 53.4 52.4", horg, stroke_c("eye", 1.1), reg=[("mouthSmile", "opacity", "a", 1.0)])
        open_ = node(ctx, "mouthOpen", 0, 0, (0, 0),
                     ell(ctx, "tongue", 50 - horg[0], 55.6 - horg[1], 2.4, 2.6, (0, 0), fill_raw(ROSE))
                     + path_shape(ctx, "mouth", "M46.5 52.5 Q50 55.5 53.5 52.5", horg, stroke_c("eye", 1.1)), reg=[("mouthOpen", "opacity", "a", 1.0)])
        wob = path_shape(ctx, "mouthWobble", "M46.8 54.2 Q50 52 53.2 54.2", horg, stroke_c("eye", 1.1), reg=[("mouthWobble", "opacity", "a", 1.0)])
        nose = (ell(ctx, "noseShine", 48.9, 46.8, 1.1, 0.6, horg, fill_raw(WHITE), op=0.7)
                + ell(ctx, "nose", 50, 47.6, 3.6, 2.6, horg, fill_c("eye")))
        E = [(42, 40), (58, 40)]
        earL = node(ctx, "earL", 33, 31, horg, ell(ctx, "ear", 30.5, 44, 7.6, 14.5, (33, 31), fill_c("deep")), reg=[("ear", "rotation", "d", RAD)])
        earR = node(ctx, "earR", 67, 31, horg, ell(ctx, "ear", 69.5, 44, 7.6, 14.5, (67, 31), fill_c("deep")), reg=[("ear", "rotation", "d", -RAD)])
        head_children = (
            nose + open_ + wob + smile + stem
            + brows_xml(ctx, horg, E, 3.1, 3.8, 1.7)
            + eyes_xml(ctx, horg, E, 3.1, 3.8, 2.2)
            + cheeks_xml(ctx, horg, [(37.5, 49), (62.5, 49)], 3.4, 2.1)
            + ell(ctx, "snout", 50, 51, 9.5, 7, horg, fill_c("light"))
            + earR + earL
            + ell(ctx, "patch", 59, 40, 7, 6.5, horg, fill_c("deep"), op=0.28)
            + shape(ctx, "headCircle", ellipse_geom(21, 21), bbox_gradient("bodyTop", "bodyBottom", 42, 42, 0.4, 0.3, 0.9), px(50 - horg[0]), px(42 - horg[1]))
        )
        head = node(ctx, "head", 50, 60, org, head_children, reg=[("headR", "rotation", "d", RAD), ("headDy", "y", "d", U)])
        paws_up = node(ctx, "pawsUp", org[0], org[1], org,
                       "".join(ell(ctx, "paw", cx, 64, 4, 6.2, org, fill_c("light") + stroke_c("deep", 0.9), rot=r) for cx, r in ((47.2, 14), (52.8, -14))),
                       reg=[("pawsUp", "opacity", "a", 1.0)])
        paws_down = node(ctx, "pawsDown", org[0], org[1], org,
                         "".join(ell(ctx, "paw", cx, 89, 5.6, 3.4, org, fill_c("light")) for cx in (42.5, 57.5)),
                         reg=[("pawsDown", "opacity", "a", 1.0)])
        belly = ell(ctx, "belly", 50, 80, 9, 8, org, fill_c("light"), op=0.85)
        body_ell = shape(ctx, "bodyOval", ellipse_geom(17.5, 13.5), bbox_gradient("bodyTop", "bodyBottom", 35, 27, 0.4, 0.3, 0.9), px(50 - org[0]), px(77 - org[1]))
        tail = node(ctx, "tail", 64, 76, org, shape(ctx, "tailRect", rect_geom(16, 6.5, 3.25), fill_c("deep"), px(71 - 64), px(75.25 - 76)),
                    rot=-35.0, reg=[("tail", "rotation", "d", -RAD)])
        body = node(ctx, "body", 50, 91, (0, 0), paws_up + head + paws_down + belly + body_ell + tail,
                    reg=[("bodyY", "y", "d", U), ("bodyRot", "rotation", "d", RAD), ("bodySX", "scaleX", "a", 1.0), ("bodySY", "scaleY", "a", 1.0)])
        fx = fx_xml(ctx, [(12, 26, 4.5), (88, 20, 4), (92, 64, 3.5), (9, 66, 3)], (68, 10), (76, 22))
        return fx + body + shadow_xml(ctx, 93)

    @staticmethod
    def extra(key, t, L):
        ear, headR, headDy, tail = 12.0, 0.0, 0.0, 0.0
        p = pose(key, t, L)
        if key == "idle":
            tail = math.sin(t * TAU / 6.8) * 5
            ear = 12 + math.sin(t * TAU / 3.4) * 1.5
        elif key == "listening":
            headR = -10 + math.sin(t * TAU / 4.8) * 2
            ear = 12 + L * 16
        elif key == "thinking":
            headR, ear = 8.0, 16.0
        elif key == "reflective":
            headR, ear = -6.0, 14.0
        elif key == "celebrating":
            ear = 24 + 6 * math.sin(t * 9) * (1 if t < 1.2 else max(0.0, 1 - (t - 1.2) / 0.4))
            tail = 28 * math.sin(TAU * 7 * t) * max(0.0, 1 - t / 1.2)
            by, sx, sy, hop = celeb_env(t, 0.5)
            p.update(by=by, hop=hop)
        elif key == "joyful":
            ear, tail = 18.0, math.sin(t * TAU / 0.6) * 16
        elif key == "worried":
            ear, headDy, tail = 2.0, 2.0, 35.0
        elif key == "sleepy":
            ear, headDy, headR, tail = 4.0, 4.0, 8.0, 20.0
        elif key == "prayerful":
            ear, headDy = 5.0, 2.0
        elif key == "peaceful":
            ear, headR = 8.0, 4.0
        elif key == "grateful":
            ear, headR, headDy, tail = 10.0, 6.0, 1.0, math.sin(t * TAU / 2.25) * 6
        o = dict(ear=ear, headR=headR, headDy=headDy, tail=tail, pawsUp=1.0 if p["hands"] else 0.0, pawsDown=0.0 if p["hands"] else 1.0)
        if key == "celebrating":
            o.update(bodyY=p["by"], shadowSX=1 - p["hop"] * 0.35)
        return o

    @staticmethod
    def fx_listen(t, L):
        return {"ear": 12 + L * (16 + 4 * math.sin(t * TAU / 0.6))}

    fx_listen_jumps = {}


# ------------------------------------------------------------------ Wren


class Wren:
    name = "Wren"
    cfg = dict(eye_ry=3.4, has_o=False)
    listen_fx_period = 1.2

    @staticmethod
    def build(ctx):
        org = (50, 88)
        ctx.default("tail", 0.0); ctx.default("wing", 0.0); ctx.default("headR", 0.0); ctx.default("headDy", 0.0)
        ctx.default("beakClosed", 1.0); ctx.default("beakOpen", 0.0)
        for i in range(3):
            ctx.default(f"fx_a{i}X", 0.0, "fx"); ctx.default(f"fx_a{i}Op", 0.0, "fx")
        horg = (58, 50)
        beak_closed = path_shape(ctx, "beakClosed", "M76 38 L87 41.8 L76 45.5 Z", horg, fill_c("deep"), reg=[("beakClosed", "opacity", "a", 1.0)])
        beak_open = path_shape(ctx, "beakOpen", "M76 37.5 L86.5 39.8 L76 42 Z M76 43 L84 46.5 L76 47 Z", horg, fill_c("deep"), reg=[("beakOpen", "opacity", "a", 1.0)])
        head_children = (
            ell(ctx, "cheek", 70, 45.5, 3.2, 1.9, horg, fill_c("blush"), reg=[("cheeks", "opacity", "a", 1.0)])
            + path_shape(ctx, "brow", "M63.5 30.5 L71.5 33", horg, stroke_c("eye", 1.7), reg=[("brows", "opacity", "a", 1.0)])
            + eyes_xml(ctx, horg, [(68, 37)], 2.8, 3.4, 2.0, look=0.8)
            + beak_open + beak_closed
            + shape(ctx, "headCircle", ellipse_geom(15.5, 15.5), fill_c("bodyTop"), px(62 - horg[0]), px(40 - horg[1]))
        )
        head = node(ctx, "head", 58, 50, org, head_children, reg=[("headR", "rotation", "d", RAD), ("headDy", "y", "d", U)])
        wing = node(ctx, "wing", 40, 57, org, path_shape(ctx, "wingPath", "M28 60 Q42 45 63 59 Q47 73 28 60 Z", (40, 57), fill_c("deep")), reg=[("wing", "rotation", "d", RAD)])
        belly = ell(ctx, "belly", 57, 69, 13, 10.5, org, fill_c("light"), op=0.9)
        body_circle = shape(ctx, "bodyCircle", ellipse_geom(25, 25), bbox_gradient("bodyTop", "bodyBottom", 50, 50, 0.4, 0.3, 0.9), px(49 - org[0]), px(60 - org[1]))
        legs = path_shape(ctx, "legs", "M45 80 L43.5 89 M55 80 L56 89", org, stroke_c("deep", 2.4))
        tail = node(ctx, "tail", 34, 54, org, path_shape(ctx, "tailPath", "M32 58 Q16 44 15 24 Q24 27 40 48 Z", (34, 54), fill_c("deep")), reg=[("tail", "rotation", "d", RAD)])
        body = node(ctx, "body", 50, 88, (0, 0), wing + belly + head + body_circle + legs + tail,
                    reg=[("bodyY", "y", "d", U), ("bodyRot", "rotation", "d", RAD), ("bodySX", "scaleX", "a", 1.0), ("bodySY", "scaleY", "a", 1.0)])
        arcs = ""
        for i in range(3):
            arcs += path_shape(ctx, f"arc{i}", "M95 26 Q89 36 95 46", (0, 0), stroke_c("accent", 1.6), op=0.0,
                               reg=[(f"fx_a{i}X", "x", "d", U), (f"fx_a{i}Op", "opacity", "a", 1.0)])
        fx = fx_xml(ctx, [(14, 30, 4.5), (88, 22, 4), (90, 66, 3.5), (12, 72, 3)], (70, 14), (74, 26))
        return fx + body + shadow_xml(ctx, 92) + arcs

    @staticmethod
    def extra(key, t, L):
        tail = wing = headR = headDy = 0.0
        p = pose(key, t, L)
        if key == "idle":
            tail = math.sin(t * TAU / 3.4) * 2
        elif key == "listening":
            tail, headR = 6 + 3 * L, -6.0
        elif key == "thinking":
            tail, headR = 4.0, -4.0
        elif key == "reflective":
            tail, headR = 2.0, -8.0
        elif key == "celebrating":
            tail = 12.0
            if t < 0.1:
                wing = lerp(0, -30, t / 0.1)
            elif t < 0.7:
                wing = -30 - abs(math.sin(math.pi * (t - 0.1) / 0.3)) * 25
            else:
                wing = lerp(-30, -34, sm(0.7, 1.0, t))
            by, sx, sy, hop = celeb_env(t)
            p.update(by=by, hop=hop)
        elif key == "joyful":
            tail, wing = 8 + math.sin(t * TAU / 1.2) * 5, -12.0
        elif key == "worried":
            tail, wing, headDy = -26.0, 6.0, 2.0
        elif key == "sleepy":
            tail, headDy, headR = -18.0, 5.0, 10.0
        elif key == "prayerful":
            tail, headDy, headR, wing = -8.0, 3.0, 14.0, -16.0
        elif key == "peaceful":
            tail, headR = -4.0, 6.0
        elif key == "grateful":
            tail, headR, headDy = 4.0, 10.0, 1.0
        open_beak = key in ("celebrating", "joyful")
        o = dict(tail=tail, wing=wing, headR=headR, headDy=headDy, beakOpen=1.0 if open_beak else 0.0, beakClosed=0.0 if open_beak else 1.0)
        if key == "celebrating":
            o.update(bodyY=p["by"], shadowSX=1 - p["hop"] * 0.35)
        return o

    @staticmethod
    def fx_listen(t, L):
        o = {"tail": 6 + 3 * L + math.sin(t * TAU / 0.6) * L * 10}
        for i in range(3):
            k = (t / 1.2 + i / 3) % 1.0
            o[f"fx_a{i}X"] = -k * 9
            o[f"fx_a{i}Op"] = math.sin(k * math.pi) * (0.15 + 0.7 * L)
        return o

    fx_listen_jumps = {"fx_a0X": 3, "fx_a1X": 3, "fx_a2X": 3}


# ------------------------------------------------------------------ Page


class Page:
    name = "Page"
    cfg = dict(eye_ry=3.9, has_o=True)
    listen_fx_period = 0.8
    LY = [58, 65, 72]
    LL = [28, 22, 15]
    NW = 11

    @staticmethod
    def build(ctx):
        org = (50, 84)
        ctx.default("foldD", 0.0); ctx.default("lean", 0.0)
        ctx.default("lineOp", 1.0); ctx.default("dashOp", 0.0)
        for i in range(3):
            ctx.default(f"trim{i}", 1.0)
        ctx.default("caretX", 0.0); ctx.default("caretY", 0.0); ctx.default("caretOp", 0.0)
        ctx.default("fx_waveOp", 0.0, "fx")
        for i in range(3):
            for k in range(Page.NW):
                ctx.default(f"fx_w{i}_{k}", 0.0, "fx")

        # page and fold: vertices keyed by `foldD`
        def sv(x, y, r=0.0, regs=()):
            a = {"x": px(x - org[0]), "y": px(y - org[1]), "radius": px(r) if r else None}
            if regs:
                vid = ctx.nid()
                a["id"] = vid
                ctx.reg(vid, a, regs)
            return el("StraightVertex", a)

        F = 12.0
        page_geom = el("PointsPath", {"isClosed": True, "name": "Path"},
                       sv(28, 20, 4) + sv(72 - F, 20, 0, [("foldD", "x", "d", -U)]) + sv(72, 20 + F, 0, [("foldD", "y", "d", U)]) + sv(72, 82, 4) + sv(28, 82, 4))
        page = shape(ctx, "page", page_geom, fill_c("paper") + stroke_c("paperLine", 1.4))
        fold_geom = el("PointsPath", {"isClosed": True, "name": "Path"},
                       sv(72 - F, 20, 0, [("foldD", "x", "d", -U)]) + sv(72 - F, 20 + F, 3, [("foldD", "x", "d", -U), ("foldD", "y", "d", U)]) + sv(72, 20 + F, 0, [("foldD", "y", "d", U)]))
        fold = shape(ctx, "fold", fold_geom, fill_c("light") + stroke_c("paperLine", 1.4))
        ribbon = path_shape(ctx, "ribbon", "M36 78 H44 V94 L40 90 L36 94 Z", org, fill_c("accent"))

        lines = ""
        for i, (y, ln) in enumerate(zip(Page.LY, Page.LL)):
            trim_id = ctx.nid()
            ctx.reg(trim_id, {}, [(f"trim{i}", "end", "a", 1.0)])
            trim = el("TrimPath", {"start": 0, "end": 1, "modeValue": "sequential", "id": trim_id})
            geom = path_geoms(f"M35 {y} L{35+ln} {y}", org)
            lines += shape(ctx, f"line{i}", geom, el("Stroke", {"thickness": px(2.2), "cap": "round", "join": "round"}, solid("lines") + trim),
                           reg=[("lineOp", "opacity", "a", 1.0)])
            dash = el("DashPath", {}, el("Dash", {"length": px(3)}) + el("Dash", {"length": px(3.5)}))
            lines += shape(ctx, f"dash{i}", geom, el("Stroke", {"thickness": px(2.2), "cap": "round", "join": "round"}, solid("lines") + dash),
                           op=0.0, reg=[("dashOp", "opacity", "a", 1.0)])
        waves = ""
        for i, y in enumerate(Page.LY):
            vx = ""
            for k in range(Page.NW):
                x = 35 + 2.8 * k
                vid = ctx.nid()
                a = {"x": px(x - org[0]), "y": px(y - org[1]), "rotation": 0.0, "distance": px(0.9), "id": vid}
                ctx.reg(vid, a, [(f"fx_w{i}_{k}", "y", "d", U)])
                vx += el("CubicMirroredVertex", a)
            waves += shape(ctx, f"wave{i}", el("PointsPath", {"isClosed": False, "name": "Path"}, vx), stroke_c("lines", 2.2), op=0.0,
                           reg=[("fx_waveOp", "opacity", "a", 1.0)])
        caret = shape(ctx, "caret", rect_geom(1.6, 6.4, 0.8), fill_c("accent"), px(35 + 2.8 - org[0]), px(58 - org[1]), op=0.0,
                      reg=[("caretX", "x", "d", U), ("caretY", "y", "d", U), ("caretOp", "opacity", "a", 1.0)])
        E = [(42, 40), (56, 40)]
        face = (
            caret + waves + lines + hands_xml(ctx, org, 49, 66, 0.9)
            + mouth_xml(ctx, org, 49, 47.5, 2.6)
            + brows_xml(ctx, org, E, 2.9, 3.9, 1.6)
            + eyes_xml(ctx, org, E, 2.9, 3.9, 2.1)
            + cheeks_xml(ctx, org, [(36.5, 47), (61.5, 47)], 3.2, 2)
        )
        body = node(ctx, "body", 50, 84, (0, 0), face + fold + page + ribbon,
                    reg=[("bodyY", "y", "d", U), ("bodyRot", "rotation", "d", RAD), ("bodySX", "scaleX", "a", 1.0), ("bodySY", "scaleY", "a", 1.0)])
        fx = fx_xml(ctx, [(16, 26, 4.5), (86, 20, 4), (88, 60, 3.5), (14, 66, 3)], (68, 10), (74, 22))
        return fx + body + shadow_xml(ctx, 95)

    @staticmethod
    def extra(key, t, L):
        p = pose(key, t, L)
        F, lean = 12.0, 0.0
        line_op, dash_op = 1.0, 0.0
        o = {}
        if key == "celebrating":
            F = 12 + math.sin(t * TAU * 1.9) * 3 * (1 if t < 1.2 else max(0.0, 1 - (t - 1.2) / 0.4))
        elif key == "worried":
            F, lean, line_op, dash_op = 17.0, -4.0, 0.0, 0.55
        elif key == "sleepy":
            lean, line_op = 4.0, 0.35
        elif key in ("prayerful", "grateful"):
            lean = 3.0
            if key == "prayerful":
                line_op = 0.0
        elif key == "listening":
            line_op = 0.0
        caret_op = 0.0
        if key == "thinking":
            total = (t * 5.0 * 16 / 5.0) % 80  # 16 units/s over a 5 s cycle
            rem = total
            cx, cy = 35.0, 58.0
            for i, ln in enumerate(Page.LL):
                l = max(0.0, min(ln, rem))
                rem -= ln
                o[f"trim{i}"] = l / ln
                if l > 0:
                    cx, cy = 35 + l, Page.LY[i]
            o["caretX"], o["caretY"] = cx - 35, cy - 58
            caret_op = 1.0
        else:
            for i in range(3):
                o[f"trim{i}"] = 1.0
        o.update(foldD=F - 12.0, lineOp=line_op, dashOp=dash_op, caretOp=caret_op, bodyRot=p["rot"] + lean)
        return o

    @staticmethod
    def fx_listen(t, L):
        o = {"fx_waveOp": 1.0}
        for i, y in enumerate(Page.LY):
            a = L * 3.4 * 0.8
            for k in range(Page.NW):
                x = 35 + 2.8 * k
                env = math.sin(math.pi * (x - 35) / 28)
                o[f"fx_w{i}_{k}"] = math.sin(x * 0.55 + t * TAU / 0.8 + i * 1.3) * a * env
        return o

    fx_listen_jumps = {}


FORMS = {"zuri": Zuri, "nas": Nas, "wren": Wren, "page": Page}
