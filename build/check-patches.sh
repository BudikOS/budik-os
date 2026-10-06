#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Check that the whole patch set in patches/ applies on a clean base of every
# project, without touching the working tree (uses temporary git worktrees).
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/common.sh"
run_in_container "$0" "$@"

failed=0
for dir in $(cd /build/patches && find . -name '*.patch' -printf '%h\n' | sed 's|^\./||' | sort -u); do
  base=$(budik_base "$dir")
  wt=$(mktemp -d)
  git -C "/src/$dir" worktree add -q --detach "$wt" "$base"
  if git -C "$wt" -c user.name=BudikOS -c user.email=info@budikstore.org am -q /build/patches/"$dir"/*.patch; then
    echo "OK   $dir ($(find /build/patches/"$dir" -name '*.patch' | wc -l) patches)"
  else
    echo "FAIL $dir"
    git -C "$wt" am --abort
    failed=1
  fi
  git -C "/src/$dir" worktree remove --force "$wt"
done
exit "$failed"
