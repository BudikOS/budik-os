#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Build the ocean kernel (LineageOS android_kernel_motorola_sdm632) with clang.
# Usage: KERNEL_SRC=<kernel tree> kernel/build.sh [fragment.config]
#   without a fragment: plain ocean_defconfig, with one: ocean_defconfig + fragment
# Result: $KERNEL_SRC/out/arch/arm64/boot/Image.gz-dtb, log in $KERNEL_SRC/out/build.log
set -euo pipefail
: "${KERNEL_SRC:?set KERNEL_SRC to the kernel tree}"
frag=""
[ $# -eq 0 ] || frag="$(readlink -f "$1")"
cd "$KERNEL_SRC"

mk=(make O=out ARCH=arm64 CC=clang LD=ld.lld AR=llvm-ar NM=llvm-nm
    OBJCOPY=llvm-objcopy OBJDUMP=llvm-objdump STRIP=llvm-strip
    CLANG_TRIPLE=aarch64-linux-gnu- CROSS_COMPILE=aarch64-linux-gnu-
    CROSS_COMPILE_ARM32=arm-linux-gnueabi- DTC_EXT="$(command -v dtc)"
    KCFLAGS="-mcpu=cortex-a53")

"${mk[@]}" ocean_defconfig
if [ -n "$frag" ]; then
  ARCH=arm64 scripts/kconfig/merge_config.sh -m -O out out/.config "$frag" > /dev/null
  "${mk[@]}" olddefconfig > /dev/null
  missing=0
  while read -r line; do
    grep -qxF "$line" out/.config || { echo "not applied: $line" >&2; missing=1; }
  done < <(grep -vE '^#|^$' "$frag")
  [ "$missing" = 0 ] || exit 1
fi

if ! "${mk[@]}" -j"$(nproc)" > out/build.log 2>&1; then
  grep -nE 'error:|Error [0-9]' out/build.log | head -20 >&2 || true
  exit 1
fi
ls -l out/arch/arm64/boot/Image.gz-dtb
