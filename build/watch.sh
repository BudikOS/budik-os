#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Follow pipeline.log on the build host from a workstation until the pipeline
# reports DONE or FAIL, or its tmux session disappears.
# Env: BUDIK_HOST         ssh destination of the build host (keys/ports via ~/.ssh/config)
#      BUDIK_REMOTE_ROOT  BUDIK_ROOT on the build host (default: budikos, relative to home)
#      BUDIK_SESSION      tmux session running pipeline.sh (default: budikos)
set -euo pipefail
: "${BUDIK_HOST:?set BUDIK_HOST to the ssh destination of the build host}"
log="${BUDIK_REMOTE_ROOT:-budikos}/logs/pipeline.log"
session="${BUDIK_SESSION:-budikos}"
remote() { ssh -o BatchMode=yes -o ConnectTimeout=10 "$BUDIK_HOST" "$@"; }

seen=$(remote "wc -l < $log" 2>/dev/null || echo 0)
while true; do
  if ! out=$(remote "tail -n +$((seen + 1)) $log; echo \"#lines \$(wc -l < $log)\"; tmux has -t $session 2>/dev/null && echo '#up' || echo '#down'"); then
    echo "ssh failed at $(date +%T), retrying"
    sleep 60
    continue
  fi
  grep -v '^#' <<< "$out" || true
  seen=$(sed -n 's/^#lines //p' <<< "$out")
  grep -qE '\] (DONE|FAIL)' <<< "$out" && exit 0
  grep -qx '#down' <<< "$out" && { echo "tmux session $session ended without DONE or FAIL"; exit 1; }
  sleep 60
done
