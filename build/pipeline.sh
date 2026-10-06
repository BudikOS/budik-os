#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Run build stages in order, logging to $BUDIK_ROOT/logs/<stage>.log and pipeline.log.
# Usage: pipeline.sh [stage...]   stages: sync apply keys build (default: apply keys build)
#   tmux new -d -s budikos build/pipeline.sh          full run after a sync
#   tmux new -d -s budikos "build/pipeline.sh build"  rebuild only, keeps ninja state
# Env: SYSTEM_PARTITION_SIZE  bytes; warns when the image is larger
set -euo pipefail
here="$(dirname "$(readlink -f "$0")")"
logs="${BUDIK_ROOT:-$HOME/budikos}/logs"
out="${BUDIK_ROOT:-$HOME/budikos}/out"
mkdir -p "$logs"

log() { echo "[$(date '+%F %T')] $*" | tee -a "$logs/pipeline.log"; }

[ $# -gt 0 ] || set -- apply keys build
for stage; do
  case "$stage" in
    sync | apply | keys | build) ;;
    *) echo "unknown stage: $stage" >&2; exit 2 ;;
  esac
done

for stage; do
  log "start $stage"
  if ! "$here/$stage.sh" > "$logs/$stage.log" 2>&1; then
    log "FAIL $stage, see $logs/$stage.log"
    tail -20 "$logs/$stage.log" >> "$logs/pipeline.log"
    exit 1
  fi
  log "OK $stage"
done

case " $* " in
  *" build "*)
    img=$(ls -1t "$out"/BudikOS-arm64-ab-*.img)
    img=${img%%$'\n'*}
    size=$(stat -c %s "$img")
    log "DONE $img ($size bytes)"
    if [ -n "${SYSTEM_PARTITION_SIZE:-}" ] && [ "$size" -gt "$SYSTEM_PARTITION_SIZE" ]; then
      log "WARNING: image is larger than the system partition ($SYSTEM_PARTITION_SIZE bytes)"
    fi
    ;;
  *) log "DONE" ;;
esac
