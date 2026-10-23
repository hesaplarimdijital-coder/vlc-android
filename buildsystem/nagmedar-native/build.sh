#!/usr/bin/env bash
# Native part of Settings > Lab (SP-02 USB audio, SP-03 Dolby Vision conversion, SP-05 whisper),
# the music engine (S-901, native/audio) and the offline subtitle translation (S-710, native/translate).
#
#   build.sh host    <prefix>  FFmpeg, libdovi, TagLib, libopenmpt, libopus and LAME for this machine and the
#                              dvconvert, remux, mediaprobe, subextract and nagaudio commands (host tests in CI: test-host.sh);
#                              remux, bitrate and dvconvert also with AddressSanitizer (*-asan) where cc has it
#   build.sh android <prefix>  FFmpeg, libdovi, whisper.cpp (with ggml's Vulkan backend: glslc is built for this
#                              machine first), libusb, TagLib, libopenmpt, libopus, LAME for arm64-v8a;
#                              libnagmedar_lab.so into application/nagmedar-lab and
#                              libnagmedar_audio.so into application/nagmedar-audio and, with CTranslate2 and
#                              SentencePiece, libnagmedar_translate.so into application/nagmedar-translate
#                              (needs ANDROID_NDK)
#
# Sources are cloned at the versions in versions.env into $NAGMEDAR_NATIVE_WORK
# (default native/.work). Nothing here is needed for a build without the lab tools:
# the app loads the libraries on demand and says so when they are missing.
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
    # the music engine's formats (S-901, S-908, S-911): Musepack, TTA, DSD, WMA, CAF
    --enable-demuxer=mpc,mpc8,tta,dsf,iff,asf,caf
    # S-709: the sound of films in AVI and in MPEG programme streams (VOB) for the subtitles from speech
    --enable-demuxer=avi,mpegps
    # the embedded subtitles written out as files: an AVI's GAB2 subtitles are SRT or SSA files inside it
    --enable-demuxer=srt,ass
    --enable-muxer=matroska,mp4,mov,null
    # S-1010: the converter's Ogg Opus and M4A files, and FFmpeg's own AAC encoder (libopus and LAME are the engine's)
    --enable-muxer=ogg,ipod --enable-encoder=aac
    --enable-parser=hevc,h264,aac,ac3,mpegaudio,flac,opus,vorbis,dca,mlp
    --enable-bsf=hevc_mp4toannexb,extract_extradata
    --enable-decoder=aac,aac_latm,ac3,eac3,mp3,mp3float,mp2,flac,opus,vorbis,dca,truehd,mlp,alac,ape,wavpack
    --enable-decoder=pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_f32le,pcm_bluray,pcm_dvd
    --enable-decoder=mpc7,mpc8,tta,wmav1,wmav2,wmapro,wmalossless,pcm_u8,pcm_s8,pcm_f64le,pcm_s24le_planar,pcm_s32le_planar
    --enable-decoder=dsd_lsbf,dsd_msbf,dsd_lsbf_planar,dsd_msbf_planar
    # S-1207: the remux's compatibility tracks, E-AC-3 (AC-3 beside it) and LPCM in Matroska
    --enable-encoder=eac3,ac3,pcm_s16le,pcm_s24le
)

# A release tarball (for sources not kept in git), checked against its SHA-256.
fetch_tarball() { # name url sha256
    if [ ! -f "$WORK/src/$1/.fetched" ]; then
        log "fetch $1 $2"
        local archive="$WORK/src/$1.tar.gz"
        curl -fsSL --retry 5 --retry-delay 10 -o "$archive" "$2"
        if ! echo "$3  $archive" | sha256sum -c --quiet - > /dev/null 2>&1; then
            echo "$1: the download's SHA-256 is $(sha256sum "$archive" | cut -c1-64), versions.env says $3" >&2
            exit 1
        fi
        rm -rf "$WORK/src/$1" && mkdir -p "$WORK/src/$1"
        tar -xzf "$archive" -C "$WORK/src/$1" --strip-components=1
        touch "$WORK/src/$1/.fetched"
    fi
}

build_openmpt() { # extra cmake args...
    fetch openmpt "$OPENMPT_URL" "$OPENMPT_REF"
    if [ -f "$PREFIX/lib/libopenmpt.a" ]; then return; fi
    log "libopenmpt $OPENMPT_REF ($TARGET)"
    local dir="$WORK/build-$TARGET/openmpt"
    mkdir -p "$WORK/build-$TARGET"
    cmake -S "$HERE/openmpt" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DOPENMPT_SOURCE="$WORK/src/openmpt" "$@" > "$dir.configure.log" 2>&1 || fail_log "libopenmpt configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "libopenmpt build ($TARGET)" "$dir.build.log"
    cmake --install "$dir" > /dev/null
}

# S-1010: libopus for the converter's Opus files (its own CMake build; no DNN extensions)
build_opus() { # extra cmake args...
    fetch opus "$OPUS_URL" "$OPUS_REF"
    if [ -f "$PREFIX/lib/libopus.a" ]; then return; fi
    log "libopus $OPUS_REF ($TARGET)"
    local dir="$WORK/build-$TARGET/opus"
    mkdir -p "$WORK/build-$TARGET"
    cmake -S "$WORK/src/opus" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DOPUS_BUILD_SHARED_LIBRARY=OFF -DOPUS_BUILD_TESTING=OFF -DOPUS_BUILD_PROGRAMS=OFF \
        -DOPUS_INSTALL_CMAKE_CONFIG_MODULE=OFF -DOPUS_DRED=OFF -DOPUS_OSCE=OFF -DOPUS_DEEP_PLC=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_C_FLAGS=-fvisibility=hidden \
        "$@" > "$dir.configure.log" 2>&1 || fail_log "libopus configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "libopus build ($TARGET)" "$dir.build.log"
    cmake --install "$dir" > /dev/null
}

# S-1010: LAME's libmp3lame for the converter's MP3 files (built by buildsystem/nagmedar-native/lame)
build_lame() { # extra cmake args...
    fetch_tarball lame "$LAME_URL" "$LAME_SHA256"
    if [ -f "$PREFIX/lib/libmp3lame.a" ]; then return; fi
    log "LAME $(basename "$LAME_URL" .tar.gz) ($TARGET)"
    local dir="$WORK/build-$TARGET/lame"
    mkdir -p "$WORK/build-$TARGET"
    cmake -S "$HERE/lame" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DLAME_SOURCE="$WORK/src/lame" "$@" > "$dir.configure.log" 2>&1 || fail_log "LAME configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "LAME build ($TARGET)" "$dir.build.log"
    cmake --install "$dir" > /dev/null
}

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

# S-709: the headers ggml's Vulkan backend is compiled with, Vulkan-Hpp and SPIR-V's, into $PREFIX/vulkan
build_vulkan_headers() {
    fetch vulkan-headers "$VULKAN_HEADERS_URL" "$VULKAN_HEADERS_REF"
    fetch spirv-headers "$SPIRV_HEADERS_URL" "$SPIRV_HEADERS_REF"
    if [ -f "$PREFIX/vulkan/share/cmake/SPIRV-Headers/SPIRV-HeadersConfig.cmake" ]; then return; fi
    log "Vulkan and SPIR-V headers $VULKAN_HEADERS_REF"
    mkdir -p "$WORK/build-host"
    local src
    for src in vulkan-headers spirv-headers; do
        local dir="$WORK/build-host/$src"
        cmake -S "$WORK/src/$src" -B "$dir" -DCMAKE_INSTALL_PREFIX="$PREFIX/vulkan" -DVULKAN_HEADERS_ENABLE_MODULE=OFF \
            -DVULKAN_HEADERS_ENABLE_TESTS=OFF -DSPIRV_HEADERS_ENABLE_TESTS=OFF > "$dir.configure.log" 2>&1 ||
            fail_log "$src configure" "$dir.configure.log"
        cmake --install "$dir" > /dev/null
    done
}

# S-709: glslc for this machine, which makes ggml's Vulkan shaders SPIR-V while whisper.cpp builds
# (only the build uses it). Kept in $WORK/tools, which CI keeps between runs.
build_glslc() {
    GLSLC="$WORK/tools/glslc"
    if [ -x "$GLSLC" ]; then return; fi
    fetch shaderc "$SHADERC_URL" "$SHADERC_REF"
    fetch glslang "$GLSLANG_URL" "$GLSLANG_REF"
    fetch spirv-tools "$SPIRV_TOOLS_URL" "$SPIRV_TOOLS_REF"
    fetch spirv-headers "$SPIRV_HEADERS_URL" "$SPIRV_HEADERS_REF"
    log "glslc from shaderc $SHADERC_REF (host)"
    local dir="$WORK/build-host/shaderc"
    mkdir -p "$WORK/build-host" "$WORK/tools"
    cmake -S "$WORK/src/shaderc" -B "$dir" -DCMAKE_BUILD_TYPE=Release \
        -DSHADERC_GLSLANG_DIR="$WORK/src/glslang" -DSHADERC_SPIRV_TOOLS_DIR="$WORK/src/spirv-tools" \
        -DSHADERC_SPIRV_HEADERS_DIR="$WORK/src/spirv-headers" -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON \
        -DSHADERC_SKIP_COPYRIGHT_CHECK=ON -DSHADERC_ENABLE_HLSL=OFF -DSHADERC_ENABLE_WERROR_COMPILE=OFF \
        -DSPIRV_SKIP_EXECUTABLES=ON -DSPIRV_WERROR=OFF -DENABLE_GLSLANG_BINARIES=OFF -DENABLE_OPT=ON \
        > "$dir.configure.log" 2>&1 || fail_log "shaderc configure (host)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" --target glslc_exe > "$dir.build.log" 2>&1 || fail_log "glslc (host)" "$dir.build.log"
    cp "$dir/glslc/glslc" "$GLSLC"
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

# S-710: CTranslate2 with the submodules its CPU build uses (spdlog, Ruy and its cpuinfo,
# cpu_features); the CUDA ones are left out
fetch_ct2() {
    if [ ! -f "$WORK/src/ctranslate2/.fetched" ]; then
        log "fetch ctranslate2 $CT2_REF"
        rm -rf "$WORK/src/ctranslate2"
        git clone -q --depth 1 --branch "$CT2_REF" "$CT2_URL" "$WORK/src/ctranslate2"
        git -C "$WORK/src/ctranslate2" submodule -q update --init --depth 1 third_party/spdlog third_party/ruy third_party/cpu_features
        git -C "$WORK/src/ctranslate2/third_party/ruy" submodule -q update --init --depth 1 third_party/cpuinfo
        touch "$WORK/src/ctranslate2/.fetched"
    fi
}

# S-710: libnagmedar_translate, CTranslate2 and SentencePiece built into it (their own CMake builds, as subprojects)
build_translate() { # extra cmake args...
    fetch_ct2
    fetch sentencepiece "$SPM_URL" "$SPM_REF"
    log "libnagmedar_translate ($TARGET)"
    local dir="$WORK/build-$TARGET/translate"
    mkdir -p "$WORK/build-$TARGET"
    cmake -S "$ROOT/native/translate" -B "$dir" -DCMAKE_BUILD_TYPE=Release \
        -DCT2_SOURCE="$WORK/src/ctranslate2" -DSPM_SOURCE="$WORK/src/sentencepiece" \
        -DCT2_VERSION="${CT2_REF#v}" -DSPM_VERSION="${SPM_REF#v}" \
        "$@" > "$dir.configure.log" 2>&1 || fail_log "translate configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" --target nagmedar_translate > "$dir.build.log" 2>&1 || fail_log "libnagmedar_translate ($TARGET)" "$dir.build.log"
}

build_taglib() { # extra cmake args...
    fetch taglib "$TAGLIB_URL" "$TAGLIB_REF"
    fetch utfcpp "$UTFCPP_URL" "$UTFCPP_REF"
    if [ -f "$PREFIX/lib/libtag.a" ]; then return; fi
    log "TagLib $TAGLIB_REF ($TARGET)"
    local dir="$WORK/build-$TARGET/taglib"
    cmake -S "$WORK/src/taglib" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DBUILD_SHARED_LIBS=OFF -DBUILD_BINDINGS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF -DWITH_ZLIB=OFF \
        -DVISIBILITY_HIDDEN=ON -Dutf8cpp_INCLUDE_DIR="$WORK/src/utfcpp/source" \
        "$@" > "$dir.configure.log" 2>&1 || fail_log "TagLib configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "TagLib build ($TARGET)" "$dir.build.log"
    cmake --install "$dir" > /dev/null
}

build_audio() { # extra cmake args...
    fetch libusb "$LIBUSB_URL" "$LIBUSB_REF"
    log "libnagmedar_audio ($TARGET)"
    local dir="$WORK/build-$TARGET/audio"
    cmake -S "$ROOT/native/audio" -B "$dir" -DCMAKE_BUILD_TYPE=Release -DDEPS_PREFIX="$PREFIX" \
        -DLIBUSB_SOURCE="$WORK/src/libusb" "$@" > "$dir.configure.log" 2>&1 || fail_log "audio configure ($TARGET)" "$dir.configure.log"
    cmake --build "$dir" -j"$JOBS" > "$dir.build.log" 2>&1 || fail_log "libnagmedar_audio ($TARGET)" "$dir.build.log"
    grep -E "warning:" "$dir.build.log" | head -20 || true
}

host() {
    build_ffmpeg --cc="${CC:-cc}"
    build_dovi ""
    build_whisper
    build_lab
    build_taglib
    build_openmpt
    build_opus
    build_lame
    build_audio
    cp "$WORK/build-host/audio/nagaudio" "$PREFIX/bin/"
    cp "$WORK/build-host/lab/whisper_lab_cli" "$PREFIX/bin/"
    log "dvconvert (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/dvconvert" -I"$PREFIX/include" \
        "$ROOT/native/lab/dvconvert.c" "$ROOT/native/lab/dvconvert_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl \
        > "$WORK/dvconvert.log" 2>&1 || fail_log "dvconvert (host)" "$WORK/dvconvert.log"
    log "remux (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/remux" -I"$PREFIX/include" \
        "$ROOT/native/lab/remux.c" "$ROOT/native/lab/dvconvert.c" "$ROOT/native/lab/remux_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl \
        > "$WORK/remux.log" 2>&1 || fail_log "remux (host)" "$WORK/remux.log"
    log "mediaprobe (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/mediaprobe" -I"$PREFIX/include" \
        "$ROOT/native/lab/mediaprobe.c" "$ROOT/native/lab/mediaprobe_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl \
        > "$WORK/mediaprobe.log" 2>&1 || fail_log "mediaprobe (host)" "$WORK/mediaprobe.log"
    log "bitrate (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/bitrate" -I"$PREFIX/include" \
        "$ROOT/native/lab/bitrate.c" "$ROOT/native/lab/bitrate_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil -lm -lpthread -ldl \
        > "$WORK/bitrate.log" 2>&1 || fail_log "bitrate (host)" "$WORK/bitrate.log"
    log "disktest (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/disktest" "$ROOT/native/lab/disktest.c" "$ROOT/native/lab/disktest_cli.c" \
        > "$WORK/disktest.log" 2>&1 || fail_log "disktest (host)" "$WORK/disktest.log"
    log "subextract (host)"
    "${CC:-cc}" -O2 -Wall -o "$PREFIX/bin/subextract" -I"$PREFIX/include" \
        "$ROOT/native/lab/subtitle_extract.c" "$ROOT/native/lab/subtitle_extract_cli.c" \
        -L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil -lm -lpthread -ldl \
        > "$WORK/subextract.log" 2>&1 || fail_log "subextract (host)" "$WORK/subextract.log"
    # the remux, the bitrate analysis and the converter again under AddressSanitizer and UBSan, where the
    # compiler has them: test-host.sh runs the files that once made them read past their tables with these
    if echo 'int main(void) { return 0; }' | "${CC:-cc}" -x c -fsanitize=address,undefined -o "$WORK/sanitizer-check" - > /dev/null 2>&1; then
        local san=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all -Wall -I"$PREFIX/include")
        local libs=(-L"$PREFIX/lib" -lavformat -lavcodec -lswresample -lavutil "$PREFIX/lib/libdovi.a" -lm -lpthread -ldl)
        log "remux, bitrate and dvconvert with AddressSanitizer (host)"
        { "${CC:-cc}" "${san[@]}" -o "$PREFIX/bin/remux-asan" "$ROOT/native/lab/remux.c" "$ROOT/native/lab/dvconvert.c" \
              "$ROOT/native/lab/remux_cli.c" "${libs[@]}" &&
          "${CC:-cc}" "${san[@]}" -o "$PREFIX/bin/bitrate-asan" "$ROOT/native/lab/bitrate.c" "$ROOT/native/lab/bitrate_cli.c" "${libs[@]}" &&
          "${CC:-cc}" "${san[@]}" -o "$PREFIX/bin/dvconvert-asan" "$ROOT/native/lab/dvconvert.c" "$ROOT/native/lab/dvconvert_cli.c" "${libs[@]}"
        } > "$WORK/sanitizers.log" 2>&1 || fail_log "sanitizer builds (host)" "$WORK/sanitizers.log"
    else
        log "no AddressSanitizer in ${CC:-cc}: the host tests run the plain commands"
        rm -f "$PREFIX/bin/remux-asan" "$PREFIX/bin/bitrate-asan" "$PREFIX/bin/dvconvert-asan"
    fi
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
    # ARMv8.2 dot product and FP16: every big core since 2018, the Dimensity 9400+ included.
    # S-709: and the GPU through ggml's Vulkan backend. Vulkan_LIBRARY only satisfies CMake: the
    # library opens the system's loader at run time (native/lab/vulkan_loader.c) and loads on a
    # device without Vulkan; vulkan-shaders-gen is built for this machine by ggml itself.
    build_vulkan_headers
    build_glslc
    build_whisper "${ndk_cmake[@]}" -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 \
        -DGGML_VULKAN=ON -DVulkan_INCLUDE_DIR="$PREFIX/vulkan/include" -DVulkan_GLSLC_EXECUTABLE="$GLSLC" \
        -DVulkan_LIBRARY="$tc/sysroot/usr/lib/aarch64-linux-android/$ANDROID_API/libvulkan.so" \
        -DSPIRV-Headers_DIR="$PREFIX/vulkan/share/cmake/SPIRV-Headers"
    build_lab "${ndk_cmake[@]}" -DNAGMEDAR_VULKAN=ON -DVULKAN_INCLUDE_DIR="$PREFIX/vulkan/include"
    local out="$ROOT/application/nagmedar-lab/src/main/jniLibs/arm64-v8a"
    mkdir -p "$out"
    cp "$WORK/build-android/lab/libnagmedar_lab.so" "$out/"
    "$tc/bin/llvm-strip" --strip-unneeded "$out/libnagmedar_lab.so"
    ls -la "$out"
    build_taglib "${ndk_cmake[@]}"
    build_openmpt "${ndk_cmake[@]}"
    build_opus "${ndk_cmake[@]}"
    build_lame "${ndk_cmake[@]}"
    build_audio "${ndk_cmake[@]}"
    local audio_out="$ROOT/application/nagmedar-audio/src/main/jniLibs/arm64-v8a"
    mkdir -p "$audio_out"
    cp "$WORK/build-android/audio/libnagmedar_audio.so" "$audio_out/"
    "$tc/bin/llvm-strip" --strip-unneeded "$audio_out/libnagmedar_audio.so"
    ls -la "$audio_out"
    build_translate "${ndk_cmake[@]}"
    local translate_out="$ROOT/application/nagmedar-translate/src/main/jniLibs/arm64-v8a"
    mkdir -p "$translate_out"
    cp "$WORK/build-android/translate/libnagmedar_translate.so" "$translate_out/"
    "$tc/bin/llvm-strip" --strip-unneeded "$translate_out/libnagmedar_translate.so"
    ls -la "$translate_out"
}

case "$TARGET" in
    host) host ;;
    android) android ;;
    *) echo "unknown target $TARGET" >&2; exit 2 ;;
esac
