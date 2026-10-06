#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Budik OS mobile signal icons for the SettingsLib level list ic_mobile_level_list (Android 17,
new_status_bar_icons): icons/statusbar/stat_sys_signal_{0..4}.xml cropped to the bars and
sized like the AOSP icons (12 dp high). Signals with 5 levels map onto the 4 bars; the error
variants (no data) add a cross in the free top left corner.
Usage: gen-signal-icons.py <design export icons/statusbar dir> <overlay res/drawable dir>"""
import re
import sys

src, dst = sys.argv[1:3]
# The bars span x 3..21, y 4..21 of the 24 dp grid.
LEFT, TOP, WIDTH, HEIGHT = 3, 4, 18, 17
CROSS = '''    <path
        android:pathData="M3.5 4.5L7.5 8.5M7.5 4.5L3.5 8.5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"/>
'''


def bars(level):
    xml = open(f'{src}/stat_sys_signal_{level}.xml', encoding='utf-8').read()
    paths = re.findall(r'<path[\s\S]*?/>', xml)
    return ''.join('    ' + p.replace('\n', '\n  ') + '\n' for p in paths)


def vector(body):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{12 * WIDTH / HEIGHT:.2f}dp"\n'
        '    android:height="12dp"\n'
        f'    android:viewportWidth="{WIDTH}"\n'
        f'    android:viewportHeight="{HEIGHT}">\n'
        f'  <group android:translateX="-{LEFT}" android:translateY="-{TOP}">\n'
        f'{body}'
        '  </group>\n'
        '</vector>\n'
    )


for bins, levels in ((4, range(5)), (5, range(6))):
    for level in levels:
        shown = level if bins == 4 else round(level * 4 / 5)
        name = f'{dst}/ic_mobile_{level}_{bins}_bar'
        open(f'{name}.xml', 'w', encoding='utf-8').write(vector(bars(shown)))
        open(f'{name}_error.xml', 'w', encoding='utf-8').write(vector(bars(shown) + CROSS))
print('OK 22 signal icons')
