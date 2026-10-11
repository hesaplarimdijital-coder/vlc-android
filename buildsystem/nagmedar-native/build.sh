#!/usr/bin/env bash
# Native part of Settings > Lab (SP-02 USB audio, SP-03 Dolby Vision conversion, SP-05 whisper).
#
#   build.sh host    <prefix>  FFmpeg + libdovi for this machine and the dvconvert and
#                              mediaprobe commands (host tests in CI: test-host.sh)
#   build.sh android <prefix>  FFmpeg, libdovi, whisper.cpp, libusb for arm64-v8a and
#                              libnagmedar_lab.so into application/nagmedar-lab (needs ANDROID_NDK)
#
# Sources are cloned at the versions in versions.env into $NAGMEDAR_NATIVE_WORK
# (default native/.work). Nothing here is needed for a build without the lab tools:
# the app loads the library on demand and says so when it is missing.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
# shellcheck source=versions.env
. "$HERE/versions.env"

TARGET=${1:?host or android}
PREFIX=$(realpath -m "${2:?prefix}")
WORK=${NAGMEDAR_NATIVE_WORK:-$ROOT/native/.work}
JOBS=$(nproc)
mkdir -p "$WORK/src" "$PREFIX/lib" "$PREFIX/include" "$PREFIX/bin"

log() { echo "== $*"; }

# The tail of a failed step's log, printed and, on GitHub Actions, raised as an annotation
# (job logs are not always reachable, annotations are).
fail_log() { # title log-file
    tail -60 "$2"
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        local text
        text=$(tail -c 3000 "$2" | sed 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
        echo "::error title=$1::$text"
    fi
    exit 1
}

fetch() { # name url ref
    if [ ! -d "$WORK/src/$1/.git" ]; then
        log "fetch $1 $3"
        git clone -q --depth 1 --branch "$3" "$2" "$WORK/src/$1"
    fi
}

FFMPEG_COMPONENTS=(
    --disable-everything --disable-programs --disable-doc --disable-avdevice --disable-avfilter
    --disable-swscale --disable-network --disable-autodetect --disable-debug
    --enable-static --disable-shared --enable-pic
    --enable-avformat --enable-avcodec --enable-swresample
    --enable-protocol=file,pipe,fd
    --enable-demuxer=matroska,mov,hevc,mpegts,mp3,flac,ogg,wav,aac,ac3,eac3,dts,dtshd,truehd,w64,aiff,ape,wv
    --enable-muxer=matroska,mp4,mov,null
    --enable-parser=hevc,h264,aac,ac3,mpegaudio,flac,opus,vorbis,dca,mlp
    --enable-bsf=hevc_mp4toannexb,extract_extradata
    --enable-decoder=aac,aac_latm,ac3,eac3,mp3,mp3float,mp2,flac,opus,vorbis,dca,truehd,mlp,alac,ape,wavpack
    --enable-decoder=pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_f32le,pcm_bluray,pcm_dvd
)

build_ffmpeg() { # extra configure args...
    fetch ffmpeg "$FFMPEG_URL" "$FFMPEG_REF"
    if [ -f "$PREFIX/lib/libavformat.a" ]; then return; fi
    log "ffmpeg $FFMPEG_REF ($TARGET)"
    local dir="$WORK/build-$TARGET/ffmpeg"
    rm -rf "$dir" && mkdir -p "$dir" && cd "$dir"
    "$WORK/src/ffmpeg/configure" --prefix="$PREFIX" "${FFMPEG_COMPONENTS[@]}" "$@" > configure.log 2>&1 || {
        cat configure.log >> ffbuild/config.log 2>/dev/null; fail_log "FFmpeg configure ($TARGET)" ffbuild/config.log; }
    make -j"$JOBS" > make.log 2>&1 || fail_log "FFmpeg make ($TARGET)" make.log
    make install > /dev/null
}

build_dovi() { # cargo target or empty
    fetch dovi_tool "$DOVI_URL" "$DOVI_REF"
    if [ -f "$PREFIX/lib/libdovi.a" ]; then return; fi
    log "libdovi from dovi_tool $DOVI_REF ($TARGET)"
    cd "$WORK/src/dovi_tool/dolby_vision"
    local args=(--release --features capi --lib --crate-type staticlib)
    local out=target/release
    if [ -n "${1:-}" ]; then
        args+=(--target "$1")
        out=target/$1/release
    fi
    RUSTFLAGS="-Cpanic=abort ${RUSTFLAGS:-}" cargo rustc "${args[@]}" > "$WORK/dovi-$TARGET.log" 2>&1 ||
        fail_log "libdovi ($TARGET)" "$WORK/dovi-$TARGET.log"
    cp "$out/libdolby_vision.a" "$PREFIX/lib/libdovi.a"
}

build_whisper() { # extra cmake args...
    fetch whisper "$WHISPER_URL" "$WHISPER_REF"
    if [ -f "$PREFIX/lib/libwhisper.a" ]; then return; fi
    log "whisper.cpp $WHISPER_REF ($TARGET)"
    local dir="$WORK/build-$TARGET/whisper"
    cmake -S "$WORK/src/whisper" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_SERVER=OFF \
        -DGGML_OPENMP=OFF -DGGML_NATIVE=OFF -DCMAKE_C_FLAGS=-fvisibility=hidden -DCMAKE_CXX_FLAGS=-fvisibility=hidden \
        "$@" > "$dir.configure.log" 2>&1 || fail_log "whisper.cpp configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "whisper.cpp build ($TARGET)" "$dir.build.log"
    cmake --install "$dir" > /dev/null
}

build_lab() { # extra cmake args...
    fetch libusb "$LIBUSB_URL" "$LIBUSB_REF"
    log "libnagmedar_lab ($TARGET)"
    local dir="$WORK/build-$TARGET/lab"
    cmake -S "$ROOT/native/lab" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DDEPS_PREFIX="$PREFIX" \
        -DLIBUSB_SOURCE="$WORK/src/libusb" "$@" > "$dir.configure.log" 2>&1 || fail_log "lab configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "libnagmedar_lab ($TARGET)" "$dir.build.log"
    grep -E "warning:" "$dir.build.log" | grep -v "/src/libusb/" | head -20 || true
}

host() {
    build_ffmpeg --cc="${CC:-cc}"
    build_dovi ""
    build_whisper
    build_lab
    cp "$WORK/build-host/lab/whisper_lab_cli" "$PREFIX/bin/"
    log "dvconvert (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/dvconvert" -I"$PREFIX/include" \
        "$ROOT/native/lab/dvconvert.c" "$ROOT/native/lab/dvconvert_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl \
        > "$WORK/dvconvert.log" 2>&1 || fail_log "dvconvert (host)" "$WORK/dvconvert.log"
    log "mediaprobe (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/mediaprobe" -I"$PREFIX/include" \
        "$ROOT/native/lab/mediaprobe.c" "$ROOT/native/lab/mediaprobe_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl \
        > "$WORK/mediaprobe.log" 2>&1 || fail_log "mediaprobe (host)" "$WORK/mediaprobe.log"
}

android() {
    : "${ANDROID_NDK:?set ANDROID_NDK}"
    local tc="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
    local cc="$tc/bin/aarch64-linux-android$ANDROID_API-clang"
    local ndk_cmake=(-DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
        -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$ANDROID_API -DANDROID_STL=c++_static)
    build_ffmpeg --target-os=android --arch=aarch64 --cpu=armv8-a --enable-cross-compile \
        --cc="$cc" --cxx="$cc++" --ld="$cc" --ar="$tc/bin/llvm-ar" --nm="$tc/bin/llvm-nm" \
        --ranlib="$tc/bin/llvm-ranlib" --strip="$tc/bin/llvm-strip" --sysroot="$tc/sysroot" \
        --extra-cflags="-O2 -fPIC -fvisibility=hidden"
    rustup target add aarch64-linux-android > /dev/null
    CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$cc" AR_aarch64_linux_android="$tc/bin/llvm-ar" \
        CC_aarch64_linux_android="$cc" build_dovi aarch64-linux-android
    # ARMv8.2 dot product and FP16: every big core since 2018, the Dimensity 9400+ included
    build_whisper "${ndk_cmake[@]}" -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16
    build_lab "${ndk_cmake[@]}"
    local out="$ROOT/application/nagmedar-lab/src/main/jniLibs/arm64-v8a"
    mkdir -p "$out"
    cp "$WORK/build-android/lab/libnagmedar_lab.so" "$out/"
    "$tc/bin/llvm-strip" --strip-unneeded "$out/libnagmedar_lab.so"
    ls -la "$out"
}

case "$TARGET" in
    host) host ;;
    android) android ;;
    *) echo "unknown target $TARGET" >&2; exit 2 ;;
esac
