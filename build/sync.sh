#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Sync the GrapheneOS tree (latest stable tag) with the RestlessOS local manifests
# into $BUDIK_ROOT/src. The private RestlessOS signing-key repository is left out;
# keys.sh provides vendor/budikos-priv instead.
# Env: GRAPHENEOS_TAG (10-digit tag, default: latest stable), SYNC_JOBS, SYNC_NET_JOBS
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/common.sh"
run_in_container "$0" "$@"

tag="${GRAPHENEOS_TAG:-}"
if [ -z "$tag" ]; then
  tag=$(curl -fsS https://grapheneos.org/releases |
    grep -oP "id=[a-z]+-stable><td>[^<]+</td><td><a href=#\K[0-9]{10}" | sort -nr | head -1)
fi
[ -n "$tag" ] || { echo "ERROR: failed to resolve the GrapheneOS tag" >&2; exit 1; }
echo "==> GrapheneOS tag: $tag ($(date -Is))"

cd /src
mkdir -p .budikos

if [ -f .budikos/applied ]; then
  echo "==> resetting previously applied patches"
  bash /restless/scripts/reset-sources.sh || true
  rm -f .budikos/applied
fi

repo init --depth=1 --git-lfs \
  --manifest-branch "refs/tags/$tag" \
  --manifest-url https://github.com/GrapheneOS/platform_manifest.git

mkdir -p .repo/local_manifests
rm -f .repo/local_manifests/*.xml
cp -v /restless/configs/manifests/remove.xml .repo/local_manifests/
grep -v 'cawilliamson-priv' /restless/configs/manifests/default.xml > .repo/local_manifests/default.xml

# android.googlesource.com answers HTTP 429 above ~4 parallel network jobs
n=0
until repo sync -j"${SYNC_JOBS:-16}" --jobs-network="${SYNC_NET_JOBS:-4}" -c --no-tags --force-sync --no-clone-bundle; do
  n=$((n + 1))
  [ "$n" -ge 30 ] && { echo "ERROR: repo sync failed $n times" >&2; exit 1; }
  delay=$((60 * (n < 10 ? n : 10)))
  echo "repo sync failed (attempt $n), retrying in ${delay}s"
  sleep "$delay"
done

aosp_tag=$(grep -m1 "aosp_revision:" .repo/manifests/config.yml | sed "s/.*: *//")
cat > .budikos/env <<ENV
GRAPHENEOS_TAG=$tag
AOSP_TAG=$aosp_tag
ANDROID_VERSION=$(echo "$aosp_tag" | sed "s/android-//;s/_r.*//")
ANDROID_VERSION_TAG=$(grep -m1 "target:" build/release/release_config_map.textproto | sed 's/.*"\([^"]*\)".*/\1/')
ENV
echo "==> sync complete ($(date -Is))"
cat .budikos/env
