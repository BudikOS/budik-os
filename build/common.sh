# SPDX-License-Identifier: GPL-3.0-only
# shellcheck shell=bash
# Sourced by the build scripts. On the host it re-runs the calling script inside
# the build container; inside the container (/.dockerenv exists) it returns.
#
# BUDIK_ROOT (default ~/budikos) holds the working state, mounted as:
#   src/      -> /src and /restless/src (the RestlessOS scripts default to it)
#   restless/ -> /restless   ccache/ -> /ccache   out/ -> /artifacts
#   keys/     -> /keys       signing keys, kept outside the source tree
# This repository is mounted read-only as /build.

IMAGE="${IMAGE:-budikos-build:24.04}"

run_in_container() {
  [ -f /.dockerenv ] && return 0
  local root="${BUDIK_ROOT:-$HOME/budikos}" repo stage tty=()
  repo="$(dirname "$(dirname "$(readlink -f "$1")")")"
  stage="$(basename "$1" .sh)"
  shift
  mkdir -p "$root"/{src,ccache,keys,logs,out} "$root/restless/src"
  chmod 700 "$root/keys"
  [ -t 0 ] && [ -t 1 ] && tty=(-t)
  exec docker run --rm -i "${tty[@]}" --name "budikos-$stage" \
    -e GRAPHENEOS_TAG -e DEBUG_BUILD -e JOBS -e SYNC_JOBS -e SYNC_NET_JOBS \
    -e BUDIK_DUAL_SHADE -e FULL -e NINJA_HIGHMEM_NUM_JOBS \
    --ulimit nofile=1048576:1048576 \
    -v "$root/src:/src" -v "$root/src:/restless/src" \
    -v "$root/restless:/restless" -v "$repo:/build:ro" \
    -v "$root/ccache:/ccache" -v "$root/keys:/keys" -v "$root/out:/artifacts" \
    -w /src "$IMAGE" nice -n 10 bash "/build/build/$stage.sh" "$@"
}

# First ancestor of HEAD in /src/$1 that was not committed by apply.sh
# (our patches are applied with git am and committer name BudikOS).
budik_base() {
  local c
  for c in $(git -C "/src/$1" rev-list --max-count=300 HEAD); do
    [ "$(git -C "/src/$1" log -1 --format=%cn "$c")" = BudikOS ] || { echo "$c"; return; }
  done
  echo "ERROR: no base commit in $1" >&2
  return 1
}
