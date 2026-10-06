#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Open and close both shade panels n times with input swipes, then print the
# SystemUI gfxinfo summary. Coordinates are for a 720x1520 screen (ocean).
# Usage: tools/jank-measure.sh [n]   Env: ADB (default: adb)
set -euo pipefail
adb="${ADB:-adb}"
n="${1:-5}"
"$adb" shell input keyevent KEYCODE_WAKEUP
"$adb" shell dumpsys gfxinfo com.android.systemui reset > /dev/null
for _ in $(seq "$n"); do
  for x in 180 540; do
    "$adb" shell input swipe "$x" 5 "$x" 1100 400
    sleep 1
    "$adb" shell input swipe 360 1400 360 100 300
    sleep 1
  done
done
"$adb" shell dumpsys gfxinfo com.android.systemui |
  grep -E "Total frames|Janky|percentile|Slow|Missed|Frame deadline|High input"
