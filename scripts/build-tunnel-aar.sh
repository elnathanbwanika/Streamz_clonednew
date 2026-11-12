#!/usr/bin/env bash
# Rebuild the userspace WireGuard AAR (wireguard-go + gvisor netstack).
#
# Requirements:
#   - Go 1.26+ with gomobile/gobind installed:
#       go install golang.org/x/mobile/cmd/gomobile@latest
#       go install golang.org/x/mobile/cmd/gobind@latest
#   - Android NDK (r26d tested). Point ANDROID_NDK_HOME at it.
#   - Android SDK, if ANDROID_HOME is not already set.
#
# Usage:
#   ANDROID_NDK_HOME=/path/to/android-ndk ./scripts/build-tunnel-aar.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/app/libs/linktunnel.aar"

if ! command -v gomobile >/dev/null 2>&1; then
  PATH="$PATH:$(go env GOPATH)/bin"
fi
if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found (go install golang.org/x/mobile/cmd/gomobile@latest)" >&2
  exit 1
fi
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_NDK_HOME must point at an Android NDK" >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME must point at an Android SDK" >&2
  exit 1
fi

cd "$ROOT/vpnlib"
go mod tidy
mkdir -p "$ROOT/app/libs"
gomobile bind \
  -target=android/arm64,android/arm \
  -androidapi 24 \
  -o "$OUT" \
  .
# gomobile also emits a sources jar next to the AAR; it is not needed in the app.
rm -f "${OUT%.aar}-sources.jar"
echo "built $OUT"
