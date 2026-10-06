#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Copy the tracked files of this checkout (including uncommitted changes) to the
# build host and fetch the fonts there. Refuses while a build container runs.
# Env: BUDIK_HOST (see watch.sh)
#      BUDIK_REMOTE_REPO  checkout on the build host (default: budikos/budik-os)
set -euo pipefail
: "${BUDIK_HOST:?set BUDIK_HOST to the ssh destination of the build host}"
dir="${BUDIK_REMOTE_REPO:-budikos/budik-os}"
remote() { ssh -o BatchMode=yes -o ConnectTimeout=10 "$BUDIK_HOST" "$@"; }

if remote 'docker ps --format "{{.Names}}"' | grep -qx budikos-build; then
  echo "a build is running on $BUDIK_HOST, not overwriting the scripts" >&2
  exit 1
fi
cd "$(dirname "$(dirname "$(readlink -f "$0")")")"
git ls-files -z | tar --null -T - -czf - | remote "mkdir -p $dir && tar xzf - -C $dir && $dir/build/fetch-fonts.sh"
