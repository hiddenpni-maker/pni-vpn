#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME to Android SDK}}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
JNI_OUT="$ROOT_DIR/app/src/main/jniLibs"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "[1/4] Building libhev-socks5-tunnel.so (JNI package ir/pni/vpn/service)..."
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
  "APP_CFLAGS=-O3 -DPKGNAME=ir/pni/vpn/service" \
  "APP_LDFLAGS=-Wl,--build-id=none -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$JNI_OUT/$abi"
  cp "$TMP/hev-libs/$abi/libhev-socks5-tunnel.so" "$JNI_OUT/$abi/libhev-socks5-tunnel.so"
done

echo "[2/4] Fetching Tor Project Android Expert Bundle (libtor.so + liblyrebird.so)..."
TOR_VER=$(curl -fsSL https://dist.torproject.org/torbrowser/ | sed -nE 's/.*href="([0-9]+\.[0-9]+\.[0-9]+)\/".*/\1/p' | sort -V | tail -1 || true)
TOR_VER="${TOR_VER:-16.0.3}"
BASE="https://dist.torproject.org/torbrowser/$TOR_VER"
echo "Using Tor Expert Bundle version: $TOR_VER"
curl -fsSL -o "$TMP/sha256sums.txt" "$BASE/sha256sums-signed-build.txt"
declare -A ABI_MAP=([aarch64]=arm64-v8a [armv7]=armeabi-v7a [x86_64]=x86_64)

for arch in aarch64 armv7 x86_64; do
  file="tor-expert-bundle-android-$arch-$TOR_VER.tar.gz"
  curl -fsSL -o "$TMP/$file" "$BASE/$file"
  expected_sha=$(grep "  $file\$" "$TMP/sha256sums.txt" | awk '{print $1}')
  if [ -n "$expected_sha" ]; then
    echo "$expected_sha  $TMP/$file" | sha256sum -c --quiet -
  fi
  mkdir -p "$TMP/tor-$arch"
  tar -xzf "$TMP/$file" -C "$TMP/tor-$arch"
  cp "$TMP/tor-$arch/tor/libTor.so" "$JNI_OUT/${ABI_MAP[$arch]}/libtor.so"
  cp "$TMP/tor-$arch/tor/pluggable_transports/lyrebird" "$JNI_OUT/${ABI_MAP[$arch]}/liblyrebird.so"
done

echo "[3/4] Building optional Psiphon & Aether binaries if sources/toolchains exist..."
if command -v go >/dev/null 2>&1; then
  git clone --depth=1 https://github.com/bia-pain-bache/BPB-VPN.git "$TMP/bpb" || true
  if [ -d "$TMP/bpb/psiphon" ]; then
    HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
    BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
    (
      cd "$TMP/bpb/psiphon"
      export GOTOOLCHAIN=go1.26.9
      CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$BIN/aarch64-linux-android24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid= -checklinkname=0" -o "$JNI_OUT/arm64-v8a/libpsiphon.so" . || true
      CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 CC="$BIN/armv7a-linux-androideabi24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid= -checklinkname=0" -o "$JNI_OUT/armeabi-v7a/libpsiphon.so" . || true
      CGO_ENABLED=1 GOOS=android GOARCH=amd64 CC="$BIN/x86_64-linux-android24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid= -checklinkname=0" -o "$JNI_OUT/x86_64/libpsiphon.so" . || true
    )
  fi
fi

if command -v cargo >/dev/null 2>&1; then
  git clone --depth=1 --recursive https://github.com/patterniha/PattNG.git "$TMP/pattng" || true
  if [ -x "$TMP/pattng/compile-aether.sh" ]; then
    NDK_HOME="$NDK" bash "$TMP/pattng/compile-aether.sh" || true
    for abi in arm64-v8a armeabi-v7a x86_64; do
      if [ -f "$TMP/pattng/libs-aether/$abi/libaether.so" ]; then
        cp "$TMP/pattng/libs-aether/$abi/libaether.so" "$JNI_OUT/$abi/libaether.so"
      fi
    done
  fi
fi

echo "[4/4] Building libdnstt.so (DNSTT / SlowDNS tunnel client like AM Tunnel)..."
if command -v go >/dev/null 2>&1; then
  git clone --depth=1 https://github.com/Mygod/dnstt.git "$TMP/dnstt" || true
  if [ -d "$TMP/dnstt/dnstt-client" ]; then
    HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
    BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
    (
      cd "$TMP/dnstt/dnstt-client"
      export GOTOOLCHAIN=go1.23.6
      CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$BIN/aarch64-linux-android24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/arm64-v8a/libdnstt.so" .
      CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 CC="$BIN/armv7a-linux-androideabi24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/armeabi-v7a/libdnstt.so" .
      CGO_ENABLED=1 GOOS=android GOARCH=amd64 CC="$BIN/x86_64-linux-android24-clang" \
        go build -trimpath -buildvcs=false -ldflags "-s -w -buildid=" -o "$JNI_OUT/x86_64/libdnstt.so" .
    )
  fi
fi

ls -lh "$JNI_OUT"/*/*.so
