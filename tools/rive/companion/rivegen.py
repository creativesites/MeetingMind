"""Core of the companion Rive generator: ids, parameters, shapes, paths, keyframes, state machines.

Everything here is form-agnostic. `forms.py` describes the four characters, `gen_companion.py` is the CLI.

Units: the rigs are authored in the prototype's 100-unit box (docs/mvp/zuri-options.html) and
written to a 500 x 500 artboard, so 1 unit = 5 px. Angles in pose code are degrees; the file
stores radians.
"""
import math
import re

U = 5.0
RAD = math.pi / 180.0
TAU = math.pi * 2
FPS = 60


def fn(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def px(u):
    return u * U


# ------------------------------------------------------------------ colours / view model

# Indigo, light (docs/mvp/ZURI_RIVE_BRIEF.md 3.4). `blush` 34% and `shadow` 9% carry their alpha.
COLORS = [
    ("accent", "FF5B5BD6"),
    ("bodyTop", "FFADADEA"),
    ("bodyBottom", "FF6F6FDB"),
    ("deep", "FF4A4AA7"),
    ("light", "FFDEDEF7"),
    ("rim", "FFB5B5ED"),
    ("paper", "FFF7F7FD"),
    ("paperLine", "FFB2B2D7"),
    ("lines", "FF8484E0"),
    ("eye", "FF151827"),
    ("blush", "57F4728A"),
    ("sparkle", "FF5B5BD6"),
    ("gold", "FFB7791F"),
    ("mute", "FF8B8B93"),
    ("shadow", "17" + "18181B"),
    ("halo", "335B5BD6"),
]
COLOR_DEFAULT = dict(COLORS)

VM_ID = "0:40"
VM_INSTANCE = "0:41"
VM_PROP = {name: f"0:{42 + i}" for i, (name, _) in enumerate(COLORS)}
# input mirrors, only present in the test build (see StateMachineBuilder)
VM_TEST = {"state": "0:70", "level": "0:71", "mode": "0:72", "calm": "0:73", "celebrate": "0:74", "nod": "0:75"}


class Ctx:
    """Per-form build context."""

    def __init__(self, form):
        self.form = form
        self._next = 100
        self.targets = {}      # param -> [(objid, prop, mode, base, mult)]
        self.defaults = {}     # param -> default (param units)
        self.layer_of = {}     # param -> 'pose' | 'fx' | 'blink'
        self.anims = []        # xml of LinearAnimation elements

    def nid(self):
        self._next += 1
        return f"0:{self._next}"

    def default(self, param, v, layer="pose"):
        self.defaults[param] = v
        self.layer_of.setdefault(param, layer)

    def layer(self, param, layer):
        self.layer_of[param] = layer

    def reg(self, objid, attrs, entries):
        for e in entries:
            param, prop = e[0], e[1]
            mode = e[2] if len(e) > 2 else "d"
            mult = e[3] if len(e) > 3 else 1.0
            base = 0.0
            if mode == "d":
                base = float(attrs.get(prop, 0.0))
            self.targets.setdefault(param, []).append((objid, prop, mode, base, mult))
            if param not in self.layer_of:
                self.layer_of[param] = "fx" if param.startswith("fx_") else ("blink" if param.startswith("bl_") else "pose")


# ------------------------------------------------------------------ element helpers


def attr_str(attrs):
    out = []
    for k, v in attrs.items():
        if v is None:
            continue
        if isinstance(v, bool):
            v = "true" if v else "false"
        elif isinstance(v, float):
            v = fn(v)
        out.append(f'{k}="{v}"')
    return " ".join(out)


def el(tag, attrs=None, children=""):
    a = attr_str(attrs or {})
    a = (" " + a) if a else ""
    if children:
        return f"<{tag}{a}>{children}</{tag}>"
    return f"<{tag}{a}/>"


def bind_color(prop, key):
    return el("DataBindContext", {"sourcePathIds": f"{VM_ID}-{VM_PROP[prop]}", "propertyKey": key})


def solid(prop):
    return el("SolidColor", {"colorValue": COLOR_DEFAULT[prop]}, bind_color(prop, 37))


def fill_c(prop):
    return el("Fill", {}, solid(prop))


def fill_raw(argb):
    return el("Fill", {}, el("SolidColor", {"colorValue": argb}))


def stroke_c(prop, w_units, cap="round", join="round", extra=""):
    return el("Stroke", {"thickness": px(w_units), "cap": cap, "join": join}, solid(prop) + extra)


def radial_fill(top, bottom, start, radius_px):
    """Radial gradient, `start` and the radius in the shape's local px, stops bound to two colours."""
    stops = (
        el("GradientStop", {"colorValue": COLOR_DEFAULT[top], "position": 0}, bind_color(top, 38))
        + el("GradientStop", {"colorValue": COLOR_DEFAULT[bottom], "position": 1}, bind_color(bottom, 38))
    )
    g = el("RadialGradient", {"startX": start[0], "startY": start[1], "endX": start[0] + radius_px, "endY": start[1]}, stops)
    return el("Fill", {}, g)


def bbox_gradient(top, bottom, w_u, h_u, fx, fy, fr):
    """CSS objectBoundingBox radial gradient (cx=fx, cy=fy, r=fr) for a w x h shape centred on its origin."""
    sx = (fx - 0.5) * w_u
    sy = (fy - 0.5) * h_u
    return radial_fill(top, bottom, (px(sx), px(sy)), px(fr * w_u))


def node(ctx, name, ox, oy, org, children, reg=(), rot=0.0, sx=1.0, sy=1.0, op=None, nid=None):
    """A Node whose origin sits at unit point (ox, oy); children are authored relative to that origin."""
    attrs = {"x": px(ox - org[0]), "y": px(oy - org[1]), "rotation": rot * RAD}
    if sx != 1.0:
        attrs["scaleX"] = sx
    if sy != 1.0:
        attrs["scaleY"] = sy
    if op is not None:
        attrs["opacity"] = op
    attrs["name"] = name
    if reg or nid:
        nid = nid or ctx.nid()
        attrs["id"] = nid
        ctx.reg(nid, attrs, reg)
    return el("Node", attrs, children)


def shape(ctx, name, geom, paints, x=0.0, y=0.0, rot=0.0, op=None, reg=(), sx=1.0, sy=1.0):
    """A Shape at (x, y) px relative to its parent origin (use P() for unit conversion)."""
    attrs = {"x": x, "y": y, "rotation": rot * RAD}
    if sx != 1.0:
        attrs["scaleX"] = sx
    if sy != 1.0:
        attrs["scaleY"] = sy
    if op is not None:
        attrs["opacity"] = op
    attrs["name"] = name
    if reg:
        sid = ctx.nid()
        attrs["id"] = sid
        ctx.reg(sid, attrs, reg)
    return el("Shape", attrs, geom + paints)


def ellipse_geom(rx_u, ry_u, reg_ctx=None, name="Path"):
    return el("Ellipse", {"width": px(2 * rx_u), "height": px(2 * ry_u), "originX": 0.5, "originY": 0.5, "name": name})


def ell(ctx, name, cx, cy, rx, ry, org, paints, rot=0.0, op=None, reg=()):
    return shape(ctx, name, ellipse_geom(rx, ry), paints, px(cx - org[0]), px(cy - org[1]), rot, op, reg)


def rect_geom(w_u, h_u, radius_u=0.0, ox=0.5, oy=0.5, ctx=None, reg=(), name="Path"):
    attrs = {"width": px(w_u), "height": px(h_u), "originX": ox, "originY": oy, "cornerRadiusTL": px(radius_u), "name": name}
    if reg and ctx:
        rid = ctx.nid()
        attrs["id"] = rid
        ctx.reg(rid, attrs, reg)
    return el("Rectangle", attrs)


def star_geom(r_u, points=4, inner=0.38):
    return el("Star", {"width": px(2 * r_u), "height": px(2 * r_u), "points": points, "innerRadius": inner, "originX": 0.5, "originY": 0.5, "name": "Path"})


# ------------------------------------------------------------------ svg-ish paths

_tok = re.compile(r"([MLHVQCZ])|(-?\d*\.?\d+(?:e-?\d+)?)")


def parse_path(d):
    """Absolute M/L/H/V/Q/C/Z path -> list of subpaths [(closed, [(x, y, in_h|None, out_h|None)])] in units."""
    toks = [(m.group(1), m.group(2)) for m in _tok.finditer(d)]
    i = 0
    subs = []
    cur = None
    pos = (0.0, 0.0)

    def nums(n):
        nonlocal i
        vals = []
        while len(vals) < n:
            vals.append(float(toks[i][1]))
            i += 1
        return vals

    cmd = None
    while i < len(toks):
        if toks[i][0]:
            cmd = toks[i][0]
            i += 1
            if cmd == "Z":
                if cur:
                    subs.append((True, cur))
                    cur = None
                continue
        if cmd == "M":
            if cur:
                subs.append((False, cur))
            x, y = nums(2)
            cur = [[x, y, None, None]]
            pos = (x, y)
            cmd = "L"
        elif cmd == "L":
            x, y = nums(2)
            cur.append([x, y, None, None])
            pos = (x, y)
        elif cmd == "H":
            (x,) = nums(1)
            cur.append([x, pos[1], None, None])
            pos = (x, pos[1])
        elif cmd == "V":
            (y,) = nums(1)
            cur.append([pos[0], y, None, None])
            pos = (pos[0], y)
        elif cmd == "Q":
            cx, cy, x, y = nums(4)
            c1 = (pos[0] + 2 / 3 * (cx - pos[0]), pos[1] + 2 / 3 * (cy - pos[1]))
            c2 = (x + 2 / 3 * (cx - x), y + 2 / 3 * (cy - y))
            cur[-1][3] = c1
            cur.append([x, y, c2, None])
            pos = (x, y)
        elif cmd == "C":
            x1, y1, x2, y2, x, y = nums(6)
            cur[-1][3] = (x1, y1)
            cur.append([x, y, (x2, y2), None])
            pos = (x, y)
    if cur:
        subs.append((False, cur))
    return subs


def path_geoms(d, org, name="Path", closed=None):
    """PointsPath elements for an svg path string; vertices relative to `org` (units), emitted in px."""
    out = []
    for n, (is_closed, verts) in enumerate(parse_path(d)):
        if closed is not None:
            is_closed = closed
        vx = []
        # a closing segment that ends where it began contributes a duplicate last vertex: fold it in
        if is_closed and len(verts) > 1 and abs(verts[0][0] - verts[-1][0]) < 1e-6 and abs(verts[0][1] - verts[-1][1]) < 1e-6:
            verts[0][2] = verts[-1][2]
            verts = verts[:-1]
        for x, y, hin, hout in verts:
            ax, ay = px(x - org[0]), px(y - org[1])
            if hin or hout:
                a = {"x": ax, "y": ay}
                if hin:
                    dx, dy = px(hin[0] - x), px(hin[1] - y)
                    a["inRotation"] = math.atan2(dy, dx)
                    a["inDistance"] = math.hypot(dx, dy)
                if hout:
                    dx, dy = px(hout[0] - x), px(hout[1] - y)
                    a["outRotation"] = math.atan2(dy, dx)
                    a["outDistance"] = math.hypot(dx, dy)
                vx.append(el("CubicDetachedVertex", a))
            else:
                vx.append(el("StraightVertex", {"x": ax, "y": ay}))
        out.append(el("PointsPath", {"isClosed": is_closed, "name": f"{name}{n or ''}"}, "".join(vx)))
    return "".join(out)


def path_shape(ctx, name, d, org, paints, op=None, reg=(), closed=None):
    return shape(ctx, name, path_geoms(d, org, closed=closed), paints, 0.0, 0.0, 0.0, op, reg)


# ------------------------------------------------------------------ animation


def thin(keys, tol):
    """Douglas-Peucker on [(t, v, interp)]; keeps endpoints."""
    if len(keys) < 3:
        return keys
    keep = [True] * len(keys)

    def rec(a, b):
        t0, v0 = keys[a][0], keys[a][1]
        t1, v1 = keys[b][0], keys[b][1]
        worst, wi = 0.0, -1
        for i in range(a + 1, b):
            t, v = keys[i][0], keys[i][1]
            lv = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 0)
            e = abs(v - lv)
            if e > worst:
                worst, wi = e, i
        if worst > tol and wi >= 0:
            rec(a, wi)
            rec(wi, b)
        else:
            for i in range(a + 1, b):
                keep[i] = False

    rec(0, len(keys) - 1)
    return [k for k, kp in zip(keys, keep) if kp]


def sample(f, T, dt=1 / 30.0, jump=None, tol_rel=0.004, tol_min=1e-3):
    """Sample f(t) -> {param: value} over [0, T]. Returns {param: [(t, v, interp)]}.

    `jump`: {param: threshold}; a step larger than the threshold between two samples becomes a hold.
    """
    n = max(1, int(round(T / dt)))
    times = [T * i / n for i in range(n + 1)]
    cols = {}
    for t in times:
        for k, v in f(t).items():
            cols.setdefault(k, []).append((t, float(v)))
    tracks = {}
    for k, pts in cols.items():
        vs = [v for _, v in pts]
        rng = max(vs) - min(vs)
        if rng < 1e-6:
            tracks[k] = [(0.0, vs[0], "h")]
            continue
        tol = max(tol_min, tol_rel * rng)
        th = (jump or {}).get(k)
        segs, cur = [], [pts[0]]
        for a, b in zip(pts, pts[1:]):
            if th is not None and abs(b[1] - a[1]) > th:
                segs.append(cur)
                cur = [b]
            else:
                cur.append(b)
        segs.append(cur)
        keys = []
        for si, seg in enumerate(segs):
            ks = thin([(t, v, "l") for t, v in seg], tol)
            if si < len(segs) - 1:
                ks[-1] = (ks[-1][0], ks[-1][1], "h")
            keys += ks
        tracks[k] = keys
    return tracks


def const(v):
    return [(0.0, v, "h")]


def anim_xml(ctx, name, T, loop, tracks, layers, aid, extra_layers=()):
    """Build a LinearAnimation; every param owned by `layers` is keyed (defaults fill the gaps)."""
    owned = {p for p, l in ctx.layer_of.items() if l in layers}
    full = dict(tracks)
    for p in owned:
        if p not in full:
            full[p] = const(ctx.defaults.get(p, 0.0))
    frames_total = max(1, int(round(T * FPS)))
    by_obj = {}
    for p, keys in full.items():
        if p not in ctx.targets:
            continue
        for (oid, prop, mode, base, mult) in ctx.targets[p]:
            by_obj.setdefault(oid, []).append((prop, p, mode, base, mult, keys))
    body = []
    for oid, props in by_obj.items():
        kps = []
        for prop, p, mode, base, mult, keys in props:
            kx, last = [], -1
            for (t, v, interp) in keys:
                fr = min(frames_total, int(round(t * FPS)))
                if fr <= last:
                    fr = last + 1
                    if fr > frames_total:
                        continue
                last = fr
                val = (base + mult * v) if mode == "d" else mult * v
                kx.append(el("KeyFrameDouble", {"value": val, "frame": fr, "interpolationType": "linear" if interp == "l" else "hold"}))
            kps.append(el("KeyedProperty", {"property": prop}, "".join(kx)))
        body.append(el("KeyedObject", {"objectId": oid}, "".join(kps)))
    return el("LinearAnimation", {"loopValue": "loop" if loop else "oneShot", "duration": frames_total, "name": name, "id": aid}, "".join(body))


# ------------------------------------------------------------------ state machine


class SM:
    """The `Companion` state machine: three layers (Pose, Fx, Blink) driven by the contract inputs.

    mode == "input": real StateMachine inputs (what ships).
    mode == "vm":    the same graph driven by view model properties, so the CLI (which can set view model
                     data but not machine inputs) can render every state. Never shipped.
    """

    OPS = {"eq": "equal", "ge": "greaterThanOrEqual"}

    def __init__(self, ctx, mode):
        self.ctx = ctx
        self.mode = mode
        self.in_id = {"state": "0:30", "level": "0:31", "mode": "0:32", "celebrate": "0:33", "nod": "0:34", "calm": "0:35"}
        self.layers = []

    def inputs_xml(self):
        if self.mode != "input":
            return ""
        i = self.in_id
        return (
            el("StateMachineNumber", {"name": "state", "id": i["state"]})
            + el("StateMachineNumber", {"name": "level", "id": i["level"]})
            + el("StateMachineNumber", {"name": "mode", "id": i["mode"]})
            + el("StateMachineTrigger", {"name": "celebrate", "id": i["celebrate"]})
            + el("StateMachineTrigger", {"name": "nod", "id": i["nod"]})
            + el("StateMachineBool", {"name": "calm", "id": i["calm"]})
        )

    def _vm_ref(self, name):
        return f"{VM_ID}-{VM_TEST[name]}"

    def cond(self, c):
        kind = c[0]
        if self.mode == "input":
            if kind == "n":
                return el("TransitionNumberCondition", {"inputId": self.in_id[c[1]], "opValue": self.OPS[c[2]], "value": float(c[3])})
            if kind == "b":
                return el("TransitionBoolCondition", {"inputId": self.in_id["calm"], "opValue": "equal" if c[2] else "notEqual"})
            return el("TransitionTriggerCondition", {"inputId": self.in_id[c[1]]})
        # vm mode
        if kind == "n":
            bp = el("BindablePropertyNumber", {}, el("DataBindContext", {"sourcePathIds": self._vm_ref(c[1]), "propertyKey": 636}))
            return el(
                "TransitionViewModelCondition",
                {"opValue": self.OPS[c[2]]},
                el("TransitionPropertyViewModelComparator", {}, bp) + el("TransitionValueNumberComparator", {"value": float(c[3])}),
            )
        if kind == "b":
            bp = el("BindablePropertyBoolean", {}, el("DataBindContext", {"sourcePathIds": self._vm_ref("calm"), "propertyKey": 634}))
            return el(
                "TransitionViewModelCondition",
                {},
                el("TransitionPropertyViewModelComparator", {}, bp) + el("TransitionValueBooleanComparator", {"value": bool(c[2])}),
            )
        bp = el("BindablePropertyTrigger", {}, el("DataBindContext", {"sourcePathIds": self._vm_ref(c[1]), "propertyKey": 686}))
        return el(
            "TransitionViewModelCondition",
            {},
            el("TransitionPropertyViewModelComparator", {}, bp) + el("TransitionValueTriggerComparator", {}),
        )

    def transition(self, to_id, conds, duration=250, exit_time=False, tag="StateTransition"):
        a = {"stateToId": to_id, "duration": duration}
        if exit_time:
            a.update({"enableExitTime": True, "exitTimeIsPercetange": True, "exitTime": 100})
        return el(tag, a, "".join(self.cond(c) for c in conds))

    def blend_state(self, sid, x, y, anims):
        """anims: [(anim_id, value)]; transitions are appended by the caller via placeholders."""
        raise NotImplementedError

    def layer(self, name, lid, states, transitions, entry):
        """states: {key: dict(kind='anim'|'blend', anim=id | blend=[(id, v)], reset=bool, x, y, id)}
        transitions: [(from_key, to_key, conds, duration, exit_time)] in priority order per source."""
        by_from = {}
        for fk, tk, conds, dur, ex in transitions:
            tag = "BlendStateTransition" if states[fk]["kind"] == "blend" else "StateTransition"
            by_from.setdefault(fk, []).append(self.transition(states[tk]["id"], conds, dur, ex, tag))
        parts = [el("AnyState", {"x": 0, "y": -120}), el("ExitState", {"x": 200, "y": -120}),
                 el("EntryState", {"x": 0, "y": 0}, el("StateTransition", {"stateToId": states[entry]["id"], "duration": 0}))]
        for key, s in states.items():
            trs = "".join(by_from.get(key, []))
            if s["kind"] == "anim":
                parts.append(el("AnimationState", {"x": s["x"], "y": s["y"], "animationId": s["anim"], "reset": s.get("reset", False) or None, "id": s["id"]}, trs))
            else:
                if self.mode == "input":
                    bs = el("BlendState1DInput", {"x": s["x"], "y": s["y"], "inputId": self.in_id["level"], "id": s["id"]},
                            "".join(el("BlendAnimation1D", {"animationId": a, "value": v}) for a, v in s["blend"]) + trs)
                else:
                    bp = el("BindablePropertyNumber", {}, el("DataBindContext", {"sourcePathIds": self._vm_ref("level"), "propertyKey": 636}))
                    bs = el("BlendState1DViewModel", {"x": s["x"], "y": s["y"], "id": s["id"]},
                            bp + "".join(el("BlendAnimation1D", {"animationId": a, "value": v}) for a, v in s["blend"]) + trs)
                parts.append(bs)
        return el("StateMachineLayer", {"name": name, "id": lid}, "".join(parts))


def view_model_xml(mode):
    props = "".join(el("ViewModelPropertyColor", {"name": n, "id": VM_PROP[n]}) for n, _ in COLORS)
    vals = "".join(el("ViewModelInstanceColor", {"propertyValue": c, "viewModelPropertyId": VM_PROP[n]}) for n, c in COLORS)
    if mode == "vm":
        for n, t in (("state", "Number"), ("level", "Number"), ("mode", "Number"), ("calm", "Boolean"), ("celebrate", "Trigger"), ("nod", "Trigger")):
            props += el(f"ViewModelProperty{t}", {"name": n, "id": VM_TEST[n]})
            v = {"Number": "0", "Boolean": "false", "Trigger": "0"}[t]
            vals += el(f"ViewModelInstance{t}", {"propertyValue": v, "viewModelPropertyId": VM_TEST[n]})
    inst = el("ViewModelInstance", {"exports": True, "name": "Default", "id": VM_INSTANCE}, vals)
    return el("ViewModel", {"defaultInstanceId": VM_INSTANCE, "name": "CompanionTheme", "id": VM_ID}, props + inst)
