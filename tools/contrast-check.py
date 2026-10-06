#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""WCAG contrast check of the Budik OS text/background color pairs, light and night.
Usage: tools/contrast-check.py <values/colors.xml> <values-night/colors.xml>
Text needs 4.5:1 (AA), icons and large text 3:1. Exits 1 on a failing pair."""
import re
import sys

# (foreground, background, minimum ratio)
PAIRS = [(fg, bg, 4.5) for fg in ('budik_fg', 'budik_fg2', 'budik_fg3')
         for bg in ('budik_bg', 'budik_surface')] + [
    ('budik_on_primary', 'budik_primary', 4.5),
    ('budik_on_accent', 'budik_accent', 4.5),
    ('budik_on_inverse', 'budik_inverse', 4.5),
    ('budik_snack_action', 'budik_inverse', 4.5),
    ('budik_accent', 'budik_ink', 3.0),  # mint only for indicators and icons on dark ink
    ('budik_border', 'budik_surface', 1.3),  # separator, not text
]


def load(path):
    with open(path, encoding='utf-8') as f:
        return dict(re.findall(r'<color name="([^"]+)">#([0-9A-Fa-f]{6,8})</color>', f.read()))


def luminance(color):
    rgb = [int(color[-6:][i:i + 2], 16) / 255 for i in (0, 2, 4)]
    r, g, b = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    light = load(sys.argv[1])
    night = {**light, **load(sys.argv[2])}
    failed = 0
    for mode, colors in (('light', light), ('night', night)):
        for fg, bg, minimum in PAIRS:
            if fg not in colors or bg not in colors:
                continue
            r = ratio(colors[fg], colors[bg])
            failed += r < minimum
            print(f"{'OK ' if r >= minimum else 'BAD'} {mode:5} {fg:20} on {bg:15} {r:5.2f}:1 (min {minimum})")
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
