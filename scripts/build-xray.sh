#!/usr/bin/env bash
# Builds the official Xray-core release tag from source for every Android ABI Tervyn ships,
# writes xray/android/<abi>/libxray.so and verifies/records SHA-256 checksums.
#
# Requirements: git, Go (version required by Xray's go.mod).
# Usage: scripts/build-xray.sh [tag]      (default tag: v26.9.30)
set -euo pipefail
TAG="${1:-v26.9.30}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/xray/android"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone --depth 1 --branch "$TAG" https://github.com/XTLS/Xray-core "$WORK/src"
cd "$WORK/src"
COMMIT="$(git rev-parse --short HEAD)"
echo "Building Xray-core $TAG ($COMMIT)"

build() { # abi goos goarch [goarm]
  mkdir -p "$OUT/$1"
  env CGO_ENABLED=0 GOOS="$2" GOARCH="$3" ${4:+GOARM=$4} go build -trimpath -buildvcs=false \
    -ldflags "-checklinkname=0 -s -w -buildid= -X github.com/xtls/xray-core/core.build=$COMMIT" \
    -o "$OUT/$1/libxray.so" ./main
  echo "  built $1"
}
# android/arm64 links internally without cgo; the other ABIs use static linux binaries, which run on Android.
build arm64-v8a  android arm64
build x86_64     linux   amd64
build armeabi-v7a linux  arm 7
build x86        linux   386
# Host binary used by unit tests to validate generated configs (xray run -test).
mkdir -p "$ROOT/xray/host"
env CGO_ENABLED=0 go build -trimpath -buildvcs=false -ldflags "-checklinkname=0 -s -w" -o "$ROOT/xray/host/xray" ./main

cd "$OUT"
if [[ -f SHA256SUMS ]] && [[ "${UPDATE_SUMS:-0}" != "1" ]]; then
  echo "Verifying against committed SHA256SUMS (set UPDATE_SUMS=1 to replace them)"
  sha256sum -c SHA256SUMS || { echo "Checksum mismatch: Go toolchain differs from the one used for the committed sums." >&2; exit 1; }
else
  sha256sum */libxray.so > SHA256SUMS
fi
cat SHA256SUMS
