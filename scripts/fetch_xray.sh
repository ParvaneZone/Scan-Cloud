#!/usr/bin/env bash
set -euo pipefail

# Set XRAY_VERSION in CI to pin a specific release. Otherwise GitHub's "Latest" release is used.
# The fallback is retained for environments where the GitHub API is unavailable.
XRAY_VERSION="${XRAY_VERSION:-}"

if [[ -z "$XRAY_VERSION" ]]; then
  latest_response=""
  if latest_response="$(curl --fail --location --retry 3 --silent --show-error \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    'https://api.github.com/repos/XTLS/Xray-core/releases/latest')"; then
    XRAY_VERSION="$(printf '%s' "$latest_response" | python3 -c \
      'import json,sys; print(json.load(sys.stdin).get("tag_name", ""))')"
  fi

  if [[ -z "$XRAY_VERSION" ]]; then
    XRAY_VERSION="v26.9.9"
  fi
fi

echo "Using Xray-core ${XRAY_VERSION}"

BASE_URL="https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

declare -A ABI_ASSETS=(
  [arm64-v8a]="Xray-android-arm64-v8a"
  [x86_64]="Xray-android-amd64"
)

fetch_asset() {
  local url="$1"
  local output="$2"

  if ! curl --fail --location --retry 3 --silent --show-error \
    -o "$output" "$url"; then
    echo "ERROR: failed to download Xray asset (the URL may be a 404): $url" >&2
    exit 1
  fi
}

fetch_abi() {
  local abi="$1"
  local asset="$2"
  local zip="$TMP/${asset}.zip"
  local dgst="$TMP/${asset}.dgst"
  local dest="$OUT/$abi"

  mkdir -p "$dest"
  fetch_asset "$BASE_URL/${asset}.zip" "$zip"
  fetch_asset "$BASE_URL/${asset}.zip.dgst" "$dgst"

  local expected
  expected="$(awk '
    /^(SHA2-256|SHA256)[[:space:]]*[=:]/ {
      value = $0
      sub(/^[^=:]*[=:]/, "", value)
      gsub(/[[:space:]]/, "", value)
      if (length(value) == 64 && value ~ /^[0-9A-Fa-f]+$/) {
        print value
        exit
      }
    }
  ' "$dgst")"

  if [[ ! "$expected" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "ERROR: could not find a SHA-256 value in $dgst" >&2
    exit 1
  fi

  local actual
  actual="$(sha256sum "$zip" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "ERROR: SHA-256 mismatch for $asset.zip" >&2
    echo "Expected: $expected" >&2
    echo "Actual:   $actual" >&2
    exit 1
  fi

  rm -rf "$TMP/$abi"
  mkdir -p "$TMP/$abi"
  unzip -q -o "$zip" -d "$TMP/$abi"

  local xray
  xray="$(find "$TMP/$abi" -type f -name xray -print -quit)"
  if [[ -z "$xray" ]]; then
    echo "ERROR: Xray executable not found in $asset.zip" >&2
    exit 1
  fi

  install -m 0755 "$xray" "$dest/libxray.so"
}

rm -rf "$OUT/arm64-v8a" "$OUT/x86_64"
for abi in "${!ABI_ASSETS[@]}"; do
  fetch_abi "$abi" "${ABI_ASSETS[$abi]}"
done

file "$OUT/arm64-v8a/libxray.so"
file "$OUT/x86_64/libxray.so"
echo "Xray-core ${XRAY_VERSION} installed successfully."
