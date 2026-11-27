#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME to Android SDK}}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"

echo "[1/2] Building pni-core.aar (Xray + PattN patch + SNI Spoof + Multi-CDN Scanner)..."
cd "$ROOT_DIR/core-go/xray-scanner"
export GOBIN="$PWD/.bin"
export PATH="$GOBIN:${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
export ANDROID_HOME="$SDK"
export ANDROID_NDK_HOME="$NDK"
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 ${CGO_LDFLAGS:-}"

go mod tidy
go test ./...
go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
gomobile init

mkdir -p "$ROOT_DIR/app/libs"
gomobile bind -javapkg ir.pni.core -target=android -androidapi 24 -trimpath \
  -ldflags "-s -w -buildid= -checklinkname=0" \
  -o "$ROOT_DIR/app/libs/pni-core.aar" ./pnixray
rm -f "$ROOT_DIR/app/libs/pni-core-sources.jar"

echo "[2/2] Building libpnisingbox.so (Lantern sing-box-minimal + outline-sdk)..."
cd "$ROOT_DIR/core-go/singbox-lantern"
go mod tidy

HOST=$(ls "$NDK/toolchains/llvm/prebuilt" | head -1)
BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
EXT=""; [[ "$HOST" == windows* ]] && EXT=".cmd"
JNI_OUT="$ROOT_DIR/app/src/main/jniLibs"

build_sb() {
  local abi="$1" goarch="$2" clang_target="$3" goarm="${4:-}"
  mkdir -p "$JNI_OUT/$abi"
  echo "  -> sing-box + outline for $abi"
  CGO_ENABLED=1 GOOS=android GOARCH="$goarch" GOARM="$goarm" CC="$BIN/${clang_target}24-clang$EXT" \
    go build -tags "with_quic,with_wireguard,with_utls,with_gvisor" \
    -trimpath -buildvcs=false -ldflags "-s -w -buildid= -checklinkname=0" \
    -o "$JNI_OUT/$abi/libpnisingbox.so" .
}

build_sb arm64-v8a arm64 aarch64-linux-android
build_sb armeabi-v7a arm armv7a-linux-androideabi 7
build_sb x86_64 amd64 x86_64-linux-android

echo "Done building Go cores!"
ls -lh "$ROOT_DIR/app/libs/pni-core.aar" "$JNI_OUT"/*/libpnisingbox.so
