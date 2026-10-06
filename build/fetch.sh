#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Download the newest image from the build host as a sparse image for fastboot.
# Usage: fetch.sh [output.simg]   (default: budikos-system.simg)
# Env: BUDIK_HOST, BUDIK_REMOTE_ROOT (see watch.sh)
set -euo pipefail
: "${BUDIK_HOST:?set BUDIK_HOST to the ssh destination of the build host}"
root="${BUDIK_REMOTE_ROOT:-budikos}"
dest="${1:-budikos-system.simg}"
remote() { ssh -o BatchMode=yes -o ConnectTimeout=10 "$BUDIK_HOST" "$@"; }

img=$(remote "ls -1t $root/out/BudikOS-arm64-ab-*.img | head -1")
echo "image: $img"
tmp=$(remote mktemp)
trap 'remote rm -f "$tmp"' EXIT
sum=$(remote "$root/src/out/host/linux-x86/bin/img2simg $img $tmp && sha256sum $tmp" | cut -d' ' -f1)
scp -q "$BUDIK_HOST:$tmp" "$dest"
echo "$sum  $dest" | sha256sum -c -
