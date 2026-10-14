#!/usr/bin/env python3
"""Synthesises the First Keep chime (ZURI_SIGNATURE_MOMENTS.md §2.5): a soft two-note chime, ≤ 300 ms, ≤ 20 KB OGG.

Two sine bells a major sixth apart (G5 then E6), each with a fast attack, an
exponential decay and a quiet octave partial, overlapping slightly. Mono, 44.1 kHz, peak −12 dBFS.

Usage: python3 make_first_keep_chime.py OUT.ogg   (needs ffmpeg with libvorbis)
"""
import math, os, struct, subprocess, sys, tempfile, wave

RATE = 44100
LENGTH_S = 0.29
PEAK = 10 ** (-12 / 20)

def bell(t, f, start, decay):
    u = t - start
    if u < 0:
        return 0.0
    attack = min(1.0, u / 0.006)
    env = attack * math.exp(-u / decay)
    return env * (math.sin(2 * math.pi * f * u) + 0.18 * math.sin(2 * math.pi * 2 * f * u))

def main(out):
    n = int(RATE * LENGTH_S)
    samples = []
    for i in range(n):
        t = i / RATE
        v = bell(t, 783.99, 0.0, 0.07) * 0.8 + bell(t, 1318.51, 0.085, 0.075)
        fade = min(1.0, (LENGTH_S - t) / 0.03)  # no click at the end
        samples.append(v * fade)
    peak = max(abs(s) for s in samples)
    data = b"".join(struct.pack("<h", int(s / peak * PEAK * 32767)) for s in samples)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, "chime.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(data)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", "-map_metadata", "-1", out], check=True)
    size = os.path.getsize(out)
    assert size <= 20 * 1024, size
    print(f"{out}: {size} bytes, {LENGTH_S * 1000:.0f} ms")

if __name__ == "__main__":
    main(sys.argv[1])
