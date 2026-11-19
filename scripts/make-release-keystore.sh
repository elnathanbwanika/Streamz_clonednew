#!/usr/bin/env bash
# Create the release keystore used by .github/workflows/build-apk.yml.
#
# The Android Gradle Plugin cannot sign with a bare PEM keypair — it needs a
# JKS/PKCS12 keystore holding the private key AND a certificate, referenced by
# an alias and protected by a password. This script wraps that conversion and
# prints the exact GitHub secrets to paste.
#
# Run it ONCE, on your own machine. Never commit the .jks, the .pem or the .b64
# it produces: the only place the keystore should live is GitHub Secrets.
#
# Requires: keytool (JDK 17+) and openssl.
#
# Usage:
#   scripts/make-release-keystore.sh private-key.pem public-key.pem
#
# Then, for each printed secret:
#   GitHub repo -> Settings -> Secrets and variables -> Actions -> New secret

set -euo pipefail

PRIV="${1:-}"
PUB="${2:-}"

if [ -z "$PRIV" ] || [ -z "$PUB" ]; then
  echo "usage: $0 <private-key.pem> <public-key.pem>" >&2
  echo "  (save the two PEM blocks you were given into those files first)" >&2
  exit 1
fi

for f in "$PRIV" "$PUB"; do
  [ -f "$f" ] || { echo "error: $f not found" >&2; exit 1; }
done

for tool in keytool openssl base64; do
  command -v "$tool" >/dev/null 2>&1 || { echo "error: '$tool' not on PATH" >&2; exit 1; }
done

ALIAS="${RELEASE_KEY_ALIAS:-release}"
OUT="${OUT:-release-key.jks}"
PASS="${RELEASE_KEYSTORE_PASSWORD:-}"

if [ -z "$PASS" ]; then
  # 32 random hex chars — strong enough, and safe to paste into a secret.
  PASS="$(openssl rand -hex 16)"
  echo "Generated a keystore password (shown once below — store it in GitHub Secrets)."
fi

echo "==> Deriving a self-signed certificate from the private key"
openssl req -new -x509 -key "$PRIV" -days 36500 -sha256 \
  -subj "/CN=LinkStreamz/O=LinkStreamz/C=US" \
  -out linkstreamz-cert.pem

echo "==> Packing key + certificate into $OUT (alias: $ALIAS)"
TMP_P12="linkstreamz-tmp.p12"
openssl pkcs12 -export \
  -inkey "$PRIV" \
  -in linkstreamz-cert.pem \
  -name "$ALIAS" \
  -out "$TMP_P12" \
  -passout "pass:$PASS"

keytool -importkeystore \
  -srckeystore "$TMP_P12" -srcstoretype PKCS12 -srcstorepass "$PASS" \
  -destkeystore "$OUT" -deststoretype JKS -deststorepass "$PASS" \
  -destkeypass "$PASS" -alias "$ALIAS" -noprompt

rm -f "$TMP_P12"

echo "==> Verifying the keystore"
keytool -list -keystore "$OUT" -storepass "$PASS" | grep -E "$ALIAS|PrivateKeyEntry" || true

echo "==> Base64 (single line) for the RELEASE_KEYSTORE_BASE64 secret"
base64 -w0 "$OUT" > "$OUT.b64" 2>/dev/null || base64 "$OUT" | tr -d '\n' > "$OUT.b64"
echo "    written to $OUT.b64 ($(wc -c < "$OUT.b64") bytes)"
echo

echo "──────────────────────────────────────────────────────────────"
echo " Create these 4 secrets in the GitHub repo:"
echo "──────────────────────────────────────────────────────────────"
echo " RELEASE_KEYSTORE_BASE64      = contents of $OUT.b64"
echo " RELEASE_KEYSTORE_PASSWORD   = $PASS"
echo " RELEASE_KEY_ALIAS           = $ALIAS"
echo " RELEASE_KEY_PASSWORD        = $PASS"
echo "──────────────────────────────────────────────────────────────"
echo
echo " Next: delete $OUT, $OUT.b64, linkstreamz-cert.pem and the .pem key"
echo " files from disk once the secrets are saved. Keep the private key"
echo " somewhere safe (a password manager) — losing it means you can never"
echo " update the app over an existing install."
