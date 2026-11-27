#!/usr/bin/env sh
# Quick local sanity check for a downloaded APK. Works with plain `sh`.
# Usage: sh scripts/check-release-apk.sh /path/to/LinkStreamz-3.5.4.apk
set -eu

APK="${1:-}"
if [ -z "$APK" ]; then
  echo "Usage: sh scripts/check-release-apk.sh <apk-file>" >&2
  exit 2
fi
if [ ! -f "$APK" ]; then
  echo "NOT OK: file not found: $APK" >&2
  exit 2
fi

# unzip -t is enough to distinguish a real APK (a ZIP) from a renamed Actions ZIP/HTML.
if command -v unzip >/dev/null 2>&1; then
  if ! unzip -t "$APK" >/dev/null 2>&1; then
    echo "NOT OK: not a valid ZIP archive: $APK" >&2
    exit 1
  fi
  if ! unzip -l "$APK" 2>/dev/null | grep -q 'AndroidManifest.xml'; then
    echo "NOT OK: APK has no AndroidManifest.xml: $APK" >&2
    exit 1
  fi
  if unzip -l "$APK" 2>/dev/null | grep -q 'META-INF/MANIFEST.MF'; then
    echo "OK: signed APK archive (AndroidManifest.xml + signature block present)"
  else
    echo "NOT OK: no signature block found (unsigned APK): $APK" >&2
    exit 1
  fi
else
  echo "unzip not installed; skipping ZIP/signature check" >&2
fi

ls -lh "$APK"