#!/usr/bin/env bash
# Creates the release signing key (make keystore): keystore/release.jks plus
# keystore/release.jks.base64 for the RELEASE_KEYSTORE_BASE64 secret.
# Never overwrites: every update must be signed with the same key.
set -euo pipefail
cd /src/bsfreetv

ks=keystore/release.jks
alias=bsfreetv
if [ -e "$ks" ]; then
    echo "$ks already exists; refusing to replace a release key." >&2
    echo "Installed copies only accept updates signed with it. Move it away first if you really mean to." >&2
    exit 1
fi

read -rsp "New keystore password (12+ characters): " pass; echo
read -rsp "Repeat it: " again; echo
[ "$pass" = "$again" ] || { echo "Passwords differ." >&2; exit 1; }
[ "${#pass}" -ge 12 ] || { echo "Use at least 12 characters." >&2; exit 1; }
export KS_PASS="$pass"

mkdir -p keystore
keytool -genkeypair -keystore "$ks" -storetype PKCS12 -alias "$alias" \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=BSfreeTV Release" \
    -storepass:env KS_PASS -keypass:env KS_PASS 2>/dev/null
base64 -w0 "$ks" > "$ks.base64"
chmod 600 "$ks" "$ks.base64"
# apksigner's format (lowercase, no colons), as the release log prints it.
sha=$(keytool -list -v -keystore "$ks" -storepass:env KS_PASS | sed -n 's/^\s*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')

owner="$(stat -c '%u:%g' . 2>/dev/null || echo '')"
[ -n "$owner" ] && chown -R "$owner" keystore

cat <<MSG

Created $ks (alias $alias). Back it up somewhere safe now: losing it means
users must uninstall to get updates.

GitHub > this repo > Settings > Secrets and variables > Actions:
  RELEASE_KEYSTORE_BASE64    contents of $ks.base64 (delete that file afterwards)
  RELEASE_KEYSTORE_PASSWORD  the password you just chose
  RELEASE_KEY_ALIAS          $alias
  RELEASE_KEY_PASSWORD       the same password

Certificate SHA-256 (release logs print the same):
  $sha
MSG
