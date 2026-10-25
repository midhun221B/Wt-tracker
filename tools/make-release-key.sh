#!/usr/bin/env bash
# Makes the private key that signs Downhill releases and prints what to paste into GitHub (repo Settings > Secrets
# and variables > Actions). Run it once on your own computer (needs a JDK for `keytool`), never in a shared place.
# Keep downhill-release.jks and the password safe and backed up: if you lose them you can never update the app.
set -euo pipefail

out="${1:-downhill-release.jks}"
alias_name="downhill"
if [ -e "$out" ]; then echo "$out already exists; not overwriting." >&2; exit 1; fi

read -r -s -p "Choose a password for the key (12+ characters): " pass; echo
read -r -s -p "Type it again: " again; echo
[ "$pass" = "$again" ] || { echo "The passwords differ." >&2; exit 1; }
[ "${#pass}" -ge 12 ] || { echo "Use at least 12 characters." >&2; exit 1; }

keytool -genkeypair -v -keystore "$out" -storetype PKCS12 -alias "$alias_name" \
  -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$pass" -keypass "$pass" -dname "CN=Downhill, O=Midhun, C=JP"

echo
echo "Key written to $out (back it up somewhere safe, NOT in git)."
echo "Add these four repository secrets on GitHub:"
echo "  RELEASE_KEYSTORE_PASSWORD = the password you just typed"
echo "  RELEASE_KEY_PASSWORD      = the same password"
echo "  RELEASE_KEY_ALIAS         = $alias_name"
echo "  RELEASE_KEYSTORE_BASE64   = the long text below"
echo
base64 -w0 "$out" 2>/dev/null || base64 "$out" | tr -d '\n'
echo
