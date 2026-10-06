#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Apply the RestlessOS overlays and patch tiers, then the Budik OS layer and patches.
# Default is incremental: only the Budik layer and projects whose patch set changed.
# Env: FULL=1           reset the whole tree and apply everything (also done
#                       automatically when the RestlessOS checkout moved)
#      DEBUG_BUILD=1    debug-builds tier instead of release-builds
#      BUDIK_DUAL_SHADE=1  Dual Shade (off by default: crashes the GrapheneOS keyguard
#                       second factor)
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/common.sh"
run_in_container "$0" "$@"

cd /src
[ -f .budikos/env ] || { echo "ERROR: run sync.sh first" >&2; exit 1; }
set -a
# shellcheck source=/dev/null
. .budikos/env
set +a

# Budik layer: idempotent steps that only touch vendor/budikos, device/phh/treble/base.mk
# and the release config. Run by both full and quick mode.
budik_layer() {
  [ -s /build/vendor/budikos/fonts/InstrumentSans.ttf ] ||
    { echo "ERROR: fonts missing, run build/fetch-fonts.sh on the host" >&2; exit 1; }
  rm -rf /src/vendor/budikos
  cp -a /build/vendor/budikos /src/vendor/budikos

  # RestlessOS branding out (rom patch device_phh_treble/0003): their wallpapers +
  # props, RestlessWallpapers app and bootanimation.zip (14 MB, disabled anyway).
  # Budik wallpapers/props come from vendor/budikos/budikos.mk instead.
  BM=device/phh/treble/base.mk
  sed -i '/RestlessStars\|wallpapers\/LICENSE\|RestlessWallpapers\|treble\/bootanimation\.zip/d' "$BM"
  ! grep -q 'RestlessStars\|RestlessWallpapers\|treble/bootanimation\.zip' "$BM" || { echo "ERROR: Restless branding sed did not apply" >&2; exit 1; }

  # Dual Shade (swipe top-left = notifications, top-right = quick settings). Both flags
  # are compiled-in as off in cp2a (not overridable on device), so enable them in the
  # release config; config_dualShadeEnabledByDefault is already true in SystemUI.
  RC=build/release/aconfig/${ANDROID_VERSION_TAG}/com.android.systemui
  [ -f "$RC/Android.bp" ] || { echo "ERROR: release config $RC missing" >&2; exit 1; }
  for f in scene_container dual_shade; do
    if [ "${BUDIK_DUAL_SHADE:-0}" = 1 ]; then
      printf 'flag_value {\n  package: "com.android.systemui"\n  name: "%s"\n  state: ENABLED\n  permission: READ_ONLY\n}\n' "$f" \
        > "$RC/${f}_flag_values.textproto"
    else
      rm -f "$RC/${f}_flag_values.textproto"
    fi
  done
}

# Our patches: /build/patches/<project path>/*.patch, applied with git am as commits
# committed by "BudikOS" on top of the restless tiers. Per project we remember the hash of
# the applied patch set; only projects whose set changed are reset to their base
# (first commit not committed by BudikOS) and re-patched -> git rewrites only the touched files,
# ninja rebuilds only what depends on them.
budik_patch_dirs() {
  { (cd /build/patches && find . -name '*.patch' -printf '%h\n'); \
    [ -d .budikos/patchsum ] && (cd .budikos/patchsum && find . -type f -printf '%p\n' | sed 's|/_sum$||'); } \
    | sed 's|^\./||' | sort -u
}
budik_patches() {  # $1 = all -> re-patch every project, else only changed ones
  local d sum old base n=0
  for d in $(budik_patch_dirs); do
    sum=none; [ -d "/build/patches/$d" ] && sum=$(cat /build/patches/"$d"/*.patch | sha256sum | cut -c1-16)
    old=$(cat ".budikos/patchsum/$d/_sum" 2>/dev/null || echo missing)
    [ "$1" != all ] && [ "$sum" = "$old" ] && continue
    base=$(budik_base "$d")
    git -C "/src/$d" am --abort >/dev/null 2>&1 || true
    git -C "/src/$d" reset -q --hard "$base"
    if [ "$sum" != none ]; then
      echo "> budikos $d"
      git -C "/src/$d" -c user.name=BudikOS -c user.email=info@budikstore.org am "/build/patches/$d"/*.patch || {
        git -C "/src/$d" am --abort; exit 1; }
      mkdir -p ".budikos/patchsum/$d"; echo "$sum" > ".budikos/patchsum/$d/_sum"
    else
      echo "> budikos $d: patches removed, reset to base"; rm -rf ".budikos/patchsum/$d"
    fi
    n=$((n+1))
  done
  echo "==> budikos patches: $n project(s) (re)applied"
}

# Edits of other projects; idempotent (a project reset above may have dropped them).
budik_seds() {
  # Monet fallback seed (used when the wallpaper has no usable color, e.g. our
  # mostly white default wallpaper): Google blue -> Budik OS mint #21D4BE
  CS=frameworks/libs/systemui/monet/src/com/android/systemui/monet/ColorScheme.java
  sed -i 's/GOOGLE_BLUE = 0xFF1b6ef3;/GOOGLE_BLUE = 0xFF21D4BE; \/\/ Budik OS mint/' "$CS"
  grep -q 'GOOGLE_BLUE = 0xFF21D4BE' "$CS" || { echo "ERROR: Monet seed sed did not apply" >&2; exit 1; }

  # No VNDK apexes: ocean's Lineage 22.2 vendor is API 35 without ro.vndk.version, so
  # com.android.vndk.v28..v34 (~470 MB) are never mounted and the image would not fit
  # the 2752 MiB system partition. Older vendors may need one of them back.
  PC=build/make/core/product_config.mk
  if ! grep -q '^# Budik OS: vendor >= 35 has no VNDK' "$PC"; then
    # shellcheck disable=SC2016 # literal $(...) in the sed pattern
    sed -i '/^  PRODUCT_EXTRA_VNDK_VERSIONS := \$(OVERRIDE_PRODUCT_EXTRA_VNDK_VERSIONS)$/{n;a\
# Budik OS: vendor >= 35 has no VNDK\
PRODUCT_EXTRA_VNDK_VERSIONS :=
}' "$PC"
  fi
  grep -q '^# Budik OS: vendor >= 35 has no VNDK' "$PC" || { echo "ERROR: VNDK sed did not apply" >&2; exit 1; }
}

# Default = incremental: restless tiers stay applied, only the Budik layer, changed
# patch projects and the seds are refreshed. FULL=1 (or a new restless commit)
# resets the whole tree and re-applies everything (near-full rebuild, ~1 h).
restless_head=$(git -C /restless rev-parse HEAD)
applied=$(cut -d' ' -f1 .budikos/applied 2>/dev/null || true)
if [ "${FULL:-0}" != 1 ] && [ "$applied" = "$restless_head" ]; then
  budik_layer
  budik_patches changed
  budik_seds
  echo "$restless_head" > .budikos/applied
  echo "==> incremental apply done (restless $restless_head)"
  exit 0
fi
echo "==> full apply (FULL=${FULL:-0}, restless applied=${applied:-none} head=$restless_head)"

# restless repo stores .zip/.apk/.so in Git LFS; without pull they are 132 B pointer files
git config --global --add safe.directory /restless
git -C /restless lfs install --local >/dev/null
git -C /restless lfs pull

# always reset: a failed earlier run leaves patches half-applied without .budikos/applied.
# NOT restless reset-sources.sh: it resets to the root commit, which is wrong for projects
# with full history (frameworks/base -> "Initial Contribution"). repo -l -d = local checkout
# of the exact manifest revision, no network.
echo "==> resetting sources to manifest revisions"
repo forall -j16 -c 'git am --abort >/dev/null 2>&1; git reset -q --hard; git clean -qfd' || true
repo sync -l -d -j16 --force-sync
rm -rf .budikos/applied .budikos/patchsum

bash /restless/scripts/copy-overlays.sh /src

for tier in trebledroid rom personal; do
  bash /restless/patches/apply.sh . "$tier"
done
if [ "${DEBUG_BUILD:-0}" = 1 ]; then
  bash /restless/patches/apply.sh . debug-builds
else
  bash /restless/patches/apply.sh . release-builds
fi

budik_layer
budik_patches all
budik_seds

echo "$restless_head" > .budikos/applied
echo "==> patches applied (restless $restless_head)"
