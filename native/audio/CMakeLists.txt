# libnagmedar_audio.so: Nağmedar Ses Motoru (S-901, ADR-06), the music engine behind the
# playback service: FFmpeg decoding, the voices and their rings, the render with the DSP
# chain, and the sinks (AAudio, the pull sink of the bit-perfect AudioTrack path, the USB DAC).
# Built by buildsystem/nagmedar-native/build.sh, which provides the static FFmpeg in
# DEPS_PREFIX; the host build also makes the nagaudio command for the tests.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_audio C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(DEPS_PREFIX "" CACHE PATH "prefix with FFmpeg (empty: the system's pkg-config)")

set(CORE ring.c source.c dsp.c engine.c)

if(DEPS_PREFIX)
    set(FFMPEG_INCLUDE ${DEPS_PREFIX}/include)
    set(FFMPEG_LIBS ${DEPS_PREFIX}/lib/libavformat.a ${DEPS_PREFIX}/lib/libavcodec.a ${DEPS_PREFIX}/lib/libswresample.a ${DEPS_PREFIX}/lib/libavutil.a)
else()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(FFMPEG REQUIRED IMPORTED_TARGET libavformat libavcodec libswresample libavutil)
    set(FFMPEG_INCLUDE ${FFMPEG_INCLUDE_DIRS})
    set(FFMPEG_LIBS PkgConfig::FFMPEG)
endif()

find_package(Threads REQUIRED)

add_library(nagaudio_core STATIC ${CORE})
target_include_directories(nagaudio_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} PRIVATE ${FFMPEG_INCLUDE})
target_compile_options(nagaudio_core PRIVATE -fvisibility=hidden -O2 -Wall -Wextra -Wno-unused-parameter)
target_link_libraries(nagaudio_core PUBLIC ${FFMPEG_LIBS} Threads::Threads m)

if(ANDROID)
    add_library(nagmedar_audio SHARED audio_jni.c sink_aaudio.c)
    target_link_libraries(nagmedar_audio PRIVATE nagaudio_core aaudio log android dl)
    target_compile_options(nagmedar_audio PRIVATE -fvisibility=hidden -O2 -Wall)
    # the engine's FFmpeg stays its own: libvlc in the same process carries another
    target_link_options(nagmedar_audio PRIVATE -Wl,--exclude-libs,ALL -Wl,-Bsymbolic -Wl,--gc-sections -Wl,-z,max-page-size=16384)
else()
    add_executable(nagaudio nagaudio_cli.c)
    target_include_directories(nagaudio PRIVATE ${FFMPEG_INCLUDE})
    target_link_libraries(nagaudio PRIVATE nagaudio_core dl)
    if(DEPS_PREFIX)
        target_link_libraries(nagaudio PRIVATE pthread)
    endif()
endif()
