#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Build and sign the GSI.
# Usage: build.sh [all|sign]   sign = only re-sign the existing target files
# Env: DEBUG_BUILD=1 (userdebug), JOBS (default: all CPUs), NINJA_HIGHMEM_NUM_JOBS
# Result: $BUDIK_ROOT/out/BudikOS-arm64-ab-<android version>-<YYYYmmddHHMM>.img
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/common.sh"
run_in_container "$0" "$@"

stage="${1:-all}"
cd /src
[ -f .budikos/env ] || { echo "ERROR: run sync.sh first" >&2; exit 1; }
[ -f .budikos/applied ] || { echo "ERROR: run apply.sh first" >&2; exit 1; }
[ -f vendor/budikos-priv/keys/releasekey.pk8 ] || { echo "ERROR: run keys.sh first" >&2; exit 1; }
set -a
# shellcheck source=/dev/null
. .budikos/env
set +a

variant=user
[ "${DEBUG_BUILD:-0}" = 1 ] && variant=userdebug
JOBS="${JOBS:-$(nproc --all)}"
# R8/D8/metalava/kotlinc take 3-6 GB each; cap only those so the rest can run wide
export NINJA_HIGHMEM_NUM_JOBS="${NINJA_HIGHMEM_NUM_JOBS:-10}"
BUILD_NUMBER=$(date +%Y%m%d%H%M)

export USE_CCACHE=1 CCACHE_EXEC=/usr/bin/ccache CCACHE_DIR=/ccache
ccache -M 100G >/dev/null

if [ "$stage" = all ]; then
  echo "==> treble_app ($(date -Is))"
  (cd treble_app && bash build.sh release)

  echo "==> device/phh/treble product (rom.mk + BudikOS branding)"
  (
    cd device/phh/treble
    cp -fv /restless/configs/rom/rom.mk rom.mk
    # Google Sans is proprietary; vendor/budikos/budikos.mk ships our fonts instead
    sed -i '/vendor\/pixel\/gsans/d' rom.mk
    cat >> rom.mk <<EOF

# Budik OS
PRODUCT_SYSTEM_PROPERTIES += \\
    ro.budikos.version=${ANDROID_VERSION}-${BUILD_NUMBER} \\
    ro.budikos.base=GrapheneOS-${GRAPHENEOS_TAG} \\
    ro.build.display.id=BudikOS-${ANDROID_VERSION}-${BUILD_NUMBER}
# product/etc/build.prop has its own ro.build.display.id (treble_arm64_bvN-...) and is
# loaded last, so the system value alone loses
PRODUCT_PRODUCT_PROPERTIES += \\
    ro.build.display.id=BudikOS-${ANDROID_VERSION}-${BUILD_NUMBER}

# Budik OS product layer, copied in by apply.sh
\$(call inherit-product, vendor/budikos/budikos.mk)
EOF
    bash generate.sh rom
  )
fi

# envsetup.sh and lunch are not written for set -u
set +u
# shellcheck source=/dev/null
. build/envsetup.sh
lunch "treble_arm64_bvN-${ANDROID_VERSION_TAG}-${variant}"
set -u

if [ "$stage" = all ]; then
  echo "==> make systemimage -j${JOBS} ($(date -Is))"
  make systemimage -j"${JOBS}"
  make target-files-package otatools -j"${JOBS}"
fi

echo "==> signing with vendor/budikos-priv keys ($(date -Is))"
rm -f "${OUT}/signed-target_files.zip"
bash vendor/budikos-priv/keys/sign.sh
img="/artifacts/BudikOS-arm64-ab-${ANDROID_VERSION}-${BUILD_NUMBER}.img"
unzip -p "${OUT}/signed-target_files.zip" IMAGES/system.img > "$img"
rm -f "${OUT}/signed-target_files.zip"
ccache -s | head -5 || true
echo "==> done: $img ($(du -h "$img" | cut -f1))"
