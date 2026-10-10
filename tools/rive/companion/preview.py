#!/usr/bin/env python3
"""Render a contact sheet of a form with the CLI (test build, view model driven).

    preview.py zuri [--dark] [--accent indigo] [--advance 40] [--only idle,listening50]
"""
import argparse, os, subprocess, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from palette import palette

RIVE = os.path.expanduser("~/.rive/bin/rive")
HERE = os.path.dirname(os.path.abspath(__file__))
CASES = [  # name, data
    ("idle", dict(state=0)), ("listen0", dict(state=1, level=0)), ("listen50", dict(state=1, level=50)), ("listen100", dict(state=1, level=100)),
    ("thinking", dict(state=2)), ("celebrating", dict(state=3)), ("worried", dict(state=4)), ("sleepy", dict(state=5)),
    ("m1 prayerful", dict(mode=1)), ("m2 peaceful", dict(mode=2)), ("m3 grateful", dict(mode=3)), ("m4 joyful", dict(mode=4)),
    ("m5 reflective", dict(mode=5)), ("m6 celebratory", dict(mode=6)), ("calm", dict(calm="true", state=1, level=80)), ("phaseB 7", dict(state=7)),
]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("form"); ap.add_argument("--dark", action="store_true"); ap.add_argument("--accent", default="indigo")
    ap.add_argument("--advance", default="40"); ap.add_argument("--only", default=""); ap.add_argument("--cols", type=int, default=4)
    ap.add_argument("--size", type=int, default=250)
    a = ap.parse_args()
    bg = "0C111B" if a.dark else "F6F5F2"
    proj = os.path.join(HERE, "build-test", f"{a.form}-{bg}")
    subprocess.run([sys.executable, os.path.join(HERE, "gen_companion.py"), a.form, "--test", "--bg", bg], check=True, stdout=subprocess.DEVNULL)
    pal = palette(a.accent, a.dark)
    shots = os.path.join(proj, "shots"); os.makedirs(shots, exist_ok=True)
    only = [s for s in a.only.split(",") if s]
    ims = []
    for name, data in CASES:
        if only and not any(name.startswith(o) for o in only):
            continue
        path = os.path.join(shots, name.split()[0] + ".png")
        cmd = [RIVE, proj, f"--screenshot={path}", f"--advance={a.advance}"]
        for k, v in {**pal, **data}.items():
            cmd.append(f"--data={k}={v}")
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0:
            print(name, "FAILED", r.stdout[-300:], r.stderr[-300:]); continue
        im = Image.open(path).convert("RGB").resize((a.size, a.size), Image.LANCZOS)
        ImageDraw.Draw(im).text((6, 4), name, fill=(120, 120, 120))
        ims.append(im)
    rows = (len(ims) + a.cols - 1) // a.cols
    sheet = Image.new("RGB", (a.cols * a.size, rows * a.size), (255, 255, 255))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % a.cols) * a.size, (i // a.cols) * a.size))
    out = os.path.join(HERE, "build-test", f"{a.form}-{'dark' if a.dark else 'light'}-{a.accent}.png")
    sheet.save(out); print(out)

main()
