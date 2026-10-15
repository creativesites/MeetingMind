"""The app's companionPalette(accent, dark) (ui/theme/CompanionColors.kt) for previews and defaults."""

ACCENTS = {  # (light, dark) from ui/theme/Appearance.kt and docs/mvp/zuri-options.html
    "indigo": ("5B5BD6", "9B9CF6"), "ocean": ("0E7490", "5CC8DD"), "forest": ("15803D", "5FD08A"),
    "gold": ("B7791F", "E0B25A"), "rose": ("BE185D", "F27AAE"), "graphite": ("3F3F46", "C9CDD6"),
}


def _rgb(h):
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def mix(a, b, t):
    A, B = _rgb(a), _rgb(b)
    return "".join(f"{round(A[i] * (1 - t) + B[i] * t):02X}" for i in range(3))


def palette(accent, dark):
    a = ACCENTS[accent][1 if dark else 0]
    W, INK, NIGHT = "FFFFFF", "18181B", "0C111B"
    p = dict(
        accent=a,
        bodyTop=mix(a, W, .22) if dark else mix(a, W, .50),
        bodyBottom=mix(a, NIGHT, .10) if dark else mix(a, W, .12),
        deep=mix(a, NIGHT, .42) if dark else mix(a, INK, .25),
        light=mix(a, W, .62) if dark else mix(a, W, .80),
        rim=mix(a, W, .60) if dark else mix(a, W, .55),
        paper=mix(a, "EEF0F5", .90) if dark else mix(a, W, .95),
        paperLine=mix(a, NIGHT, .30) if dark else mix(a, "D4D4D8", .72),
        lines=mix(a, W, .10) if dark else mix(a, W, .25),
        eye="151827", blush="F4728A",
        sparkle=mix(a, W, .25) if dark else a,
        gold="E0B25A" if dark else "B7791F",
        mute="8995A8" if dark else "8B8B93",
        shadow="000000" if dark else INK,
        halo=a,
    )
    alpha = dict(blush=0x57, shadow=0x66 if dark else 0x17, halo=0x52 if dark else 0x33)
    return {k: f"{alpha.get(k, 255):02X}{v}" for k, v in p.items()}
