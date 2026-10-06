#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Repack a boot image with our kernel. Header, ramdisk and command line come from
# BASE_BOOT, e.g. the boot.img of the LineageOS build for ocean.
# Usage: BASE_BOOT=<boot.img> KERNEL_SRC=<kernel tree> kernel/pack.sh [output.img]
# Env: ADB_KEY        adbkey.pub to add as /adb_keys (adb without authorisation)
#      EXTRA_CMDLINE  appended to the kernel command line
#      MKBOOTIMG      mkbootimg checkout (cloned on first use)
set -euo pipefail
: "${BASE_BOOT:?set BASE_BOOT to the boot image to take the ramdisk from}"
: "${KERNEL_SRC:?set KERNEL_SRC to the kernel tree}"
kernel="$KERNEL_SRC/out/arch/arm64/boot/Image.gz-dtb"
out="$(readlink -f "${1:-boot-ocean.img}")"
tools="${MKBOOTIMG:-${XDG_CACHE_HOME:-$HOME/.cache}/mkbootimg}"
[ -f "$kernel" ] || { echo "ERROR: $kernel missing, run kernel/build.sh first" >&2; exit 1; }
[ -d "$tools" ] || git clone -q --depth 1 https://android.googlesource.com/platform/system/tools/mkbootimg "$tools"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 "$tools/unpack_bootimg.py" --boot_img "$BASE_BOOT" --out "$work" --format=mkbootimg > "$work/args"

if [ -n "${ADB_KEY:-}" ]; then
  rd="$work/rd"
  mkdir "$rd"
  if lz4 -t "$work/ramdisk" 2>/dev/null; then
    comp=lz4
    lz4 -dc "$work/ramdisk" | (cd "$rd" && cpio -idm --quiet)
  else
    comp=gzip
    gzip -dc "$work/ramdisk" | (cd "$rd" && cpio -idm --quiet)
  fi
  install -m 644 "$ADB_KEY" "$rd/adb_keys"
  (cd "$rd" && find . | sort | cpio -o -H newc --quiet -R 0:0) > "$work/rd.cpio"
  if [ "$comp" = lz4 ]; then
    lz4 -l -12 -f -q "$work/rd.cpio" "$work/ramdisk"
  else
    gzip -9c "$work/rd.cpio" > "$work/ramdisk"
  fi
fi

args=$(sed "s#--kernel [^ ]*#--kernel $kernel#" "$work/args")
if [ -n "${EXTRA_CMDLINE:-}" ]; then
  # shellcheck disable=SC2001 # the pattern needs a regex group
  args=$(sed "s#--cmdline '\([^']*\)'#--cmdline '\1 $EXTRA_CMDLINE'#" <<< "$args")
fi
eval python3 "$tools/mkbootimg.py" "$args" --output "$out"
ls -l "$out"
