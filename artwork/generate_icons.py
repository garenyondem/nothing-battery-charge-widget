#!/usr/bin/env python3
"""Generate the Glyph Charge icon assets from one geometry.

The Android launcher uses vector drawables, so it does not need density
variants. The PNG exports are reference sizes and the Google Play asset.

Requirements: Python 3 and Pillow (python -m pip install pillow).
Run from the repository root: python artwork/generate_icons.py
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
ART = ROOT / "artwork"

# Design space: a 108 x 108 adaptive-icon layer. The mark itself is 58.4 wide.
CENTER = 54.0
RING_RADIUS = 26.0
DOT_RADIUS = 3.2
BOLT_HEIGHT = 21.0
BOLT_STROKE = 1.6  # stroke width in design units, round joins and caps
LIT_DOTS = 9  # 9 of 12 = 75%
GHOST_ALPHA = 0.30
MARK_DIAMETER = 2 * (RING_RADIUS + DOT_RADIUS)  # 58.4
BACKGROUND = "#0B0B0C"
MARK_COLOR = "#FFFFFF"

# Lucide-style bolt, normalized to a 20-unit height and centered on (0, 0).
BOLT = [(1.0, -10.0), (-9.0, 2.0), (0.0, 2.0), (-1.0, 10.0), (9.0, -2.0), (0.0, -2.0)]


def num(value: float) -> str:
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return text if text != "-0" else "0"


def dot_centers(scale: float, cx: float, cy: float) -> list[tuple[float, float]]:
    return [
        (
            cx + RING_RADIUS * scale * math.cos(math.radians(-90 + i * 30)),
            cy + RING_RADIUS * scale * math.sin(math.radians(-90 + i * 30)),
        )
        for i in range(12)
    ]


def bolt_points(scale: float, cx: float, cy: float) -> list[tuple[float, float]]:
    factor = BOLT_HEIGHT * scale / 20.0
    return [(cx + x * factor, cy + y * factor) for x, y in BOLT]


def circle_path(cx: float, cy: float, r: float) -> str:
    return (
        f"M{num(cx - r)},{num(cy)}"
        f"a{num(r)},{num(r)} 0 1,0 {num(2 * r)},0"
        f"a{num(r)},{num(r)} 0 1,0 {num(-2 * r)},0Z"
    )


def poly_path(points: list[tuple[float, float]]) -> str:
    return "M" + " L".join(f"{num(x)},{num(y)}" for x, y in points) + "Z"


def mark_paths(scale: float, cx: float, cy: float):
    centers = dot_centers(scale, cx, cy)
    lit = "".join(circle_path(x, y, DOT_RADIUS * scale) for x, y in centers[:LIT_DOTS])
    ghost = "".join(circle_path(x, y, DOT_RADIUS * scale) for x, y in centers[LIT_DOTS:])
    return lit, ghost, poly_path(bolt_points(scale, cx, cy))


def vector(size_dp: int, viewport: float, content: float, color: str) -> str:
    scale = content / MARK_DIAMETER
    lit, ghost, bolt = mark_paths(scale, viewport / 2, viewport / 2)
    return "\n".join(
        [
            '<?xml version="1.0" encoding="utf-8"?>',
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            f'    android:width="{size_dp}dp"',
            f'    android:height="{size_dp}dp"',
            f'    android:viewportWidth="{num(viewport)}"',
            f'    android:viewportHeight="{num(viewport)}">',
            '    <path',
            f'        android:fillColor="{color}"',
            f'        android:pathData="{lit}" />',
            '    <path',
            f'        android:fillColor="{color}"',
            f'        android:fillAlpha="{GHOST_ALPHA:g}"',
            f'        android:pathData="{ghost}" />',
            '    <path',
            f'        android:fillColor="{color}"',
            f'        android:pathData="{bolt}"',
            f'        android:strokeColor="{color}"',
            f'        android:strokeWidth="{num(BOLT_STROKE * scale)}"',
            '        android:strokeLineCap="round"',
            '        android:strokeLineJoin="round" />',
            '</vector>',
            '',
        ]
    )


def background_vector(size_dp: int, viewport: float, color: str) -> str:
    side = num(viewport)
    return "\n".join(
        [
            '<?xml version="1.0" encoding="utf-8"?>',
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            f'    android:width="{size_dp}dp"',
            f'    android:height="{size_dp}dp"',
            f'    android:viewportWidth="{side}"',
            f'    android:viewportHeight="{side}">',
            '    <path',
            f'        android:fillColor="{color}"',
            f'        android:pathData="M0,0h{side}v{side}H0z" />',
            '</vector>',
            '',
        ]
    )


def svg_mark(scale: float, cx: float, cy: float) -> list[str]:
    centers = dot_centers(scale, cx, cy)
    elements = [f'<circle cx="{num(x)}" cy="{num(y)}" r="{num(DOT_RADIUS * scale)}"/>'
                for x, y in centers[:LIT_DOTS]]
    elements.append(f'<g fill-opacity="{GHOST_ALPHA:g}">')
    elements += [f'<circle cx="{num(x)}" cy="{num(y)}" r="{num(DOT_RADIUS * scale)}"/>'
                 for x, y in centers[LIT_DOTS:]]
    elements.append("</g>")
    elements.append(
        f'<path d="{poly_path(bolt_points(scale, cx, cy))}" '
        f'stroke="{MARK_COLOR}" stroke-width="{num(BOLT_STROKE * scale)}" '
        'stroke-linejoin="round" stroke-linecap="round"/>'
    )
    return elements


def svg(size: int, content: float, background: str | None) -> str:
    scale = content / MARK_DIAMETER
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {size} {size}">',
    ]
    if background:
        lines.append(f'<rect width="{size}" height="{size}" fill="{background}"/>')
    lines.append(f'<g fill="{MARK_COLOR}">')
    lines += ["  " + line for line in svg_mark(scale, size / 2, size / 2)]
    lines += ["</g>", "</svg>", ""]
    return "\n".join(lines)


def render(size: int, content: float, background: str | None, supersample: int = 4) -> Image.Image:
    work = size * supersample
    scale = content * supersample / MARK_DIAMETER
    cx = cy = work / 2
    foreground = Image.new("RGBA", (work, work), (0, 0, 0, 0))
    draw = ImageDraw.Draw(foreground, "RGBA")
    for index, (x, y) in enumerate(dot_centers(scale, cx, cy)):
        r = DOT_RADIUS * scale
        alpha = 255 if index < LIT_DOTS else round(255 * GHOST_ALPHA)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, alpha))
    points = bolt_points(scale, cx, cy)
    draw.polygon(points, fill=(255, 255, 255, 255))
    width = max(1, round(BOLT_STROKE * scale))
    draw.line(points + [points[0]], fill=(255, 255, 255, 255), width=width, joint="curve")
    for x, y in points:
        draw.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2],
                     fill=(255, 255, 255, 255))
    image = foreground.resize((size, size), Image.LANCZOS)
    if background is None:
        return image
    base = Image.new("RGBA", (size, size), background + "FF")
    return Image.alpha_composite(base, image)


def write(path: Path, text: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def main():
    # Adaptive layer: the launcher masks the outer 18 dp of the 108 dp canvas.
    write(RES / "drawable" / "ic_launcher_background.xml",
          background_vector(108, 108, BACKGROUND))
    write(RES / "drawable" / "ic_launcher_foreground.xml",
          vector(108, 108, MARK_DIAMETER, MARK_COLOR))
    # In-app header mark: 60 of 64 dp, transparent.
    write(RES / "drawable" / "ic_glyph_mark.xml",
          vector(64, 64, 60, "@color/glyph_mark"))
    # Notification small icon: 22 of 24 dp, masked by the status bar.
    write(RES / "drawable" / "ic_stat_glyph_charge.xml",
          vector(24, 24, 22, "#FFFFFF"))

    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n'
    )
    write(RES / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive)
    write(RES / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive)

    # Master artwork: the icon uses the same 108 dp composition as the launcher.
    full_content = 512 * MARK_DIAMETER / 108
    write(ART / "glyph-charge-icon.svg", svg(512, full_content, BACKGROUND))
    write(ART / "glyph-charge-mark.svg", svg(512, 480, None))

    # Google Play: 512 x 512, 32-bit PNG, full bleed, no corner radius.
    (ART / "exports").mkdir(parents=True, exist_ok=True)
    render(512, full_content, BACKGROUND).save(ART / "play-store-icon-512.png", optimize=True)
    for size in (48, 72, 96, 144, 192):
        render(size, size * MARK_DIAMETER / 108, BACKGROUND).save(
            ART / "exports" / f"ic_launcher-{size}.png", optimize=True
        )
    render(512, 480, None).save(ART / "exports" / "glyph-charge-mark-512.png", optimize=True)


if __name__ == "__main__":
    main()
