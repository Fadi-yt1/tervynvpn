#!/usr/bin/env bash
# Alternative to build-xray.sh: downloads the OFFICIAL prebuilt release archives of Xray-core from
# GitHub, verifies each archive against the SHA-256 in the release's .dgst file, and installs the
# binaries as xray/android/<abi>/libxray.so.
#
# Note: the .dgst file comes from the same release, so this verifies integrity of the download.
# Compare the printed digests with the release page if you need independent confirmation.
# Usage: scripts/fetch-xray.sh [tag]   (default v26.9.30)
set -euo pipefail
TAG="${1:-v26.9.30}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/xray/android"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BASE="https://github.com/XTLS/Xray-core/releases/download/$TAG"
declare -A ASSET=(
  [arm64-v8a]="Xray-android-arm64-v8a.zip"
  [x86_64]="Xray-android-amd64.zip"
  [armeabi-v7a]="Xray-linux-arm32-v7a.zip"
  [x86]="Xray-linux-32.zip"
)
for abi in "${!ASSET[@]}"; do
  f="${ASSET[$abi]}"
  curl -fsSL -o "$WORK/$f" "$BASE/$f"
  curl -fsSL -o "$WORK/$f.dgst" "$BASE/$f.dgst"
  expected="$(grep -i '^SHA2-256' "$WORK/$f.dgst" | awk '{print $NF}')"
  actual="$(sha256sum "$WORK/$f" | awk '{print $1}')"
  [[ -n "$expected" && "$expected" == "$actual" ]] || { echo "SHA-256 mismatch for $f" >&2; exit 1; }
  echo "$f  sha256=$actual  OK"
  mkdir -p "$WORK/$abi" "$OUT/$abi"
  unzip -q -o "$WORK/$f" xray -d "$WORK/$abi"
  install -m 0755 "$WORK/$abi/xray" "$OUT/$abi/libxray.so"
done
(cd "$OUT" && sha256sum */libxray.so > SHA256SUMS.official && cat SHA256SUMS.official)
echo "Installed official binaries. (SHA256SUMS describes the source build; SHA256SUMS.official these files.)"
