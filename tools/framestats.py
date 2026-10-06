#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Per-phase frame time medians and p90 (ms) from `dumpsys gfxinfo <package> framestats`.
Usage: adb shell dumpsys gfxinfo com.android.systemui framestats | tools/framestats.py"""
import sys

PHASES = [
    ('input', 'HandleInputStart', 'AnimationStart'),
    ('animation', 'AnimationStart', 'PerformTraversalsStart'),
    ('measure/layout', 'PerformTraversalsStart', 'DrawStart'),
    ('draw (record)', 'DrawStart', 'SyncQueued'),
    ('sync', 'SyncStart', 'IssueDrawCommandsStart'),
    ('issue', 'IssueDrawCommandsStart', 'SwapBuffers'),
    ('gpu', 'IssueDrawCommandsStart', 'GpuCompleted'),
    ('total', 'IntendedVsync', 'FrameCompleted'),
]


def read_frames(lines):
    header, frames = None, []
    for line in lines:
        line = line.strip()
        if line.startswith('Flags,'):
            header = line.rstrip(',').split(',')
        elif header and line[:1].isdigit():
            values = line.rstrip(',').split(',')
            # flags 0 = a normal frame; the others are layout-only or invalid
            if len(values) >= len(header) - 2 and values[0] == '0':
                frames.append(dict(zip(header, map(int, values))))
    return frames


def quantile(values, q):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * q))] if values else 0


def report(name, values):
    print(f'{name:16} p50 {quantile(values, .5):6.1f}  p90 {quantile(values, .9):6.1f}')


def main():
    frames = read_frames(sys.stdin)
    print('frames', len(frames))
    for name, start, end in PHASES:
        report(name, [(f[end] - f[start]) / 1e6 for f in frames
                      if f.get(start, 0) > 0 and f.get(end, 0) >= f[start]])
    report('dequeue', [f['DequeueBufferDuration'] / 1e6 for f in frames
                       if 'DequeueBufferDuration' in f])


if __name__ == '__main__':
    main()
