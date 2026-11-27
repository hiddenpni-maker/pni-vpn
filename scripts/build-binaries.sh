#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME to Android SDK}}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
JNI_OUT="$ROOT_DIR/app/src/main/jniLibs"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
EXT=""; [[ "$HOST" == windows* ]] && EXT=".cmd"
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 ${CGO_LDFLAGS:-}"

echo "[1/5] Building libhev-socks5-tunnel.so (JNI package ir/pnifox/core/vpn)..."
git clone --depth=1 --recursive https://github.com/heiher/hev-socks5-tunnel.git "$TMP/hev"
mkdir -p "$TMP/jni"
echo 'include $(call all-subdir-makefiles)' > "$TMP/jni/Android.mk"
ln -s "$TMP/hev" "$TMP/jni/hev-socks5-tunnel"

"$NDK/ndk-build" -C "$TMP" \
  NDK_PROJECT_PATH="$TMP" \
  APP_BUILD_SCRIPT="$TMP/jni/Android.mk" \
  "APP_ABI=arm64-v8a armeabi-v7a x86_64" \
  APP_PLATFORM=android-24 \
  NDK_LIBS_OUT="$TMP/hev-libs" \
  NDK_OUT="$TMP/hev-obj" \
  "APP_CFLAGS=-O3 -DPKGNAME=ir/pnifox/core/vpn" \
  "APP_LDFLAGS=-Wl,--build-id=none -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$JNI_OUT/$abi"
  cp "$TMP/hev-libs/$abi/libhev-socks5-tunnel.so" "$JNI_OUT/$abi/libhev-socks5-tunnel.so"
done

echo "[2/5] Fetching Tor Project Android Expert Bundle (libtor.so + liblyrebird.so)..."
TOR_VER=$(curl -fsSL https://dist.torproject.org/torbrowser/ | sed -nE 's/.*href="([0-9]+\.[0-9]+\.[0-9]+)\/".*/\1/p' | sort -V | tail -1 || true)
TOR_VER="${TOR_VER:-16.0.3}"
BASE="https://dist.torproject.org/torbrowser/$TOR_VER"
echo "Using Tor Expert Bundle version: $TOR_VER"
curl -fsSL -o "$TMP/tor_sha256sums.txt" "$BASE/sha256sums-signed-build.txt"
declare -A TOR_ABI=([aarch64]=arm64-v8a [armv7]=armeabi-v7a [x86_64]=x86_64)

for arch in aarch64 armv7 x86_64; do
  file="tor-expert-bundle-android-$arch-$TOR_VER.tar.gz"
  curl -fsSL -o "$TMP/$file" "$BASE/$file"
  expected_sha=$(grep "  $file\$" "$TMP/tor_sha256sums.txt" | awk '{print $1}')
  if [ -n "$expected_sha" ]; then
    echo "$expected_sha  $TMP/$file" | sha256sum -c --quiet -
  fi
  mkdir -p "$TMP/tor-$arch"
  tar -xzf "$TMP/$file" -C "$TMP/tor-$arch"
  cp "$TMP/tor-$arch/tor/libTor.so" "$JNI_OUT/${TOR_ABI[$arch]}/libtor.so"
  cp "$TMP/tor-$arch/tor/pluggable_transports/lyrebird" "$JNI_OUT/${TOR_ABI[$arch]}/liblyrebird.so"
done

echo "[3/5] Fetching SHA256-verified Aether + Psiphon Android binaries (patterniha/Aether)..."
AETHER_TAG=$(curl -fsSL https://api.github.com/repos/patterniha/Aether/releases/latest | sed -nE 's/.*"tag_name": *"([^"]+)".*/\1/p' | head -1 || true)
AETHER_TAG="${AETHER_TAG:-v2.4.0}"
AETHER_BASE="https://github.com/patterniha/Aether/releases/download/$AETHER_TAG"
echo "Using Aether release: $AETHER_TAG"
curl -fsSL -o "$TMP/aether_sha256sums.txt" "$AETHER_BASE/SHA256SUMS.txt" || true

declare -A AETHER_ABI=([arm64]=arm64-v8a [armv7]=armeabi-v7a [x86_64]=x86_64)
for arch in arm64 armv7 x86_64; do
  tarball="aether-android-$arch.tar.gz"
  if curl -fsSL -o "$TMP/$tarball" "$AETHER_BASE/$tarball"; then
    expected_sha=$(grep "  $tarball\$" "$TMP/aether_sha256sums.txt" | awk '{print $1}' || true)
    if [ -n "$expected_sha" ]; then
      echo "$expected_sha  $TMP/$tarball" | sha256sum -c --quiet -
    fi
    mkdir -p "$TMP/aether-$arch"
    tar -xzf "$TMP/$tarball" -C "$TMP/aether-$arch"
    cp "$TMP/aether-$arch/aether" "$JNI_OUT/${AETHER_ABI[$arch]}/libaether.so"
    if [ -f "$TMP/aether-$arch/pt/psiphon-tunnel-core" ]; then
      cp "$TMP/aether-$arch/pt/psiphon-tunnel-core" "$JNI_OUT/${AETHER_ABI[$arch]}/libpsiphon.so"
    fi
  fi
done

echo "[4/5] Building libdnstt.so (DNSTT / SlowDNS tunnel client like AM Tunnel)..."
git clone --depth=1 -b plugin https://github.com/Mygod/dnstt.git "$TMP/dnstt"
(
  cd "$TMP/dnstt/dnstt-client"
  CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$BIN/aarch64-linux-android24-clang$EXT" \
    go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/arm64-v8a/libdnstt.so" .
  CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 CC="$BIN/armv7a-linux-androideabi24-clang$EXT" \
    go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/armeabi-v7a/libdnstt.so" .
  CGO_ENABLED=1 GOOS=android GOARCH=amd64 CC="$BIN/x86_64-linux-android24-clang$EXT" \
    go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/x86_64/libdnstt.so" .
)

echo "[5/5] Fetching libmhrv_rs.so (therealaleph/MasterHttpRelayVPN-RUST)..."
MHR_TAG=$(curl -fsSL https://api.github.com/repos/therealaleph/MasterHttpRelayVPN-RUST/releases/latest | sed -nE 's/.*"tag_name": *"([^"]+)".*/\1/p' | head -1 || true)
MHR_TAG="${MHR_TAG:-v1.9.38}"
MHR_BASE="https://github.com/therealaleph/MasterHttpRelayVPN-RUST/releases/download/$MHR_TAG"
echo "Using MHR release: $MHR_TAG"
for abi in arm64-v8a armeabi-v7a x86_64; do
  apk="mhrv-rs-android-$abi-$MHR_TAG.apk"
  if curl -fsSL -o "$TMP/$apk" "$MHR_BASE/$apk"; then
    unzip -o -j "$TMP/$apk" "lib/$abi/libmhrv_rs.so" -d "$JNI_OUT/$abi"
  fi
done

ls -lh "$JNI_OUT"/*/*.so
