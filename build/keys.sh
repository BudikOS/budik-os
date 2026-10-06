#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Generate your own release signing keys in $BUDIK_ROOT/keys (outside the source
# tree, mode 700) and install them with RestlessOS sign.sh as vendor/budikos-priv.
# Needs a synced tree (development/tools/make_key). Existing keys are never replaced.
# Env: KEY_SUBJECT  certificate subject (default /C=CZ/O=BudikOS/CN=BudikOS)
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/common.sh"
run_in_container "$0" "$@"

subject="${KEY_SUBJECT:-/C=CZ/O=BudikOS/CN=BudikOS}"
ref=/restless/overlays/cawilliamson/vendor_cawilliamson-priv/keys
[ -f /src/development/tools/make_key ] || { echo "ERROR: sync sources first" >&2; exit 1; }

cd /keys
if [ -f releasekey.pk8 ]; then
  echo "==> keys already exist, not regenerating"
else
  sed -e "s|^SUBJECT=.*|SUBJECT=\"$subject\"|" \
      -e "s|/repo/src/|/src/|g" "$ref/make_keys.sh" > make_keys.sh
  bash make_keys.sh
fi
cp -f "$ref/sign.sh" sign.sh
chmod 600 ./*.pk8 ./*.pem 2>/dev/null || true

# local vendor directory outside any manifest, so repo sync leaves it alone
rm -rf /src/vendor/budikos-priv
mkdir -p /src/vendor/budikos-priv
cp -a /keys /src/vendor/budikos-priv/keys
echo "==> installed $(find /src/vendor/budikos-priv/keys -name '*.pk8' | wc -l) keys into vendor/budikos-priv/keys"
openssl x509 -noout -subject -in /keys/releasekey.x509.pem
