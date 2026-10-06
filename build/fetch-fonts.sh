#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Download the Budik OS fonts (SIL OFL 1.1, from google/fonts) into vendor/budikos/fonts.
# Run once on the build host before apply.sh.
set -euo pipefail
dir="$(dirname "$(dirname "$(readlink -f "$0")")")/vendor/budikos/fonts"
base=https://raw.githubusercontent.com/google/fonts/main/ofl
mkdir -p "$dir"
while read -r src dst; do
  [ -s "$dir/$dst" ] || curl -fsSL -o "$dir/$dst" "$base/$src"
  echo "$dst $(stat -c %s "$dir/$dst")"
done <<'EOF'
instrumentsans/InstrumentSans%5Bwdth,wght%5D.ttf InstrumentSans.ttf
schibstedgrotesk/SchibstedGrotesk%5Bwght%5D.ttf SchibstedGrotesk.ttf
instrumentsans/OFL.txt OFL-InstrumentSans.txt
schibstedgrotesk/OFL.txt OFL-SchibstedGrotesk.txt
EOF
