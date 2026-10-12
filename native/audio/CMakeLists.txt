# libnagmedar_audio.so: Nağmedar Ses Motoru (S-901, ADR-06), the music engine behind the
# playback service: FFmpeg decoding, the voices and their rings, the render with the DSP
# chain, and the sinks (AAudio, the pull sink of the bit-perfect AudioTrack path, the USB DAC);
# the EBU R128 scanner and TagLib for the ReplayGain tags (S-907).
# Built by buildsystem/nagmedar-native/build.sh, which provides the static FFmpeg and TagLib
# in DEPS_PREFIX; the host build also makes the nagaudio command for the tests.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_audio C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(DEPS_PREFIX "" CACHE PATH "prefix with FFmpeg and TagLib (empty: the system's pkg-config)")
set(LIBUSB_SOURCE "" CACHE PATH "libusb source tree, for the USB DAC sink (S-903); empty: no USB sink")

set(CORE ring.c source.c dsp.c stretch.c loudness.c engine.c tags.cpp)

if(DEPS_PREFIX)
    set(FFMPEG_INCLUDE ${DEPS_PREFIX}/include)
    set(FFMPEG_LIBS ${DEPS_PREFIX}/lib/libavformat.a ${DEPS_PREFIX}/lib/libavcodec.a ${DEPS_PREFIX}/lib/libswresample.a ${DEPS_PREFIX}/lib/libavutil.a)
    set(TAGLIB_INCLUDE ${DEPS_PREFIX}/include)
    set(TAGLIB_LIBS ${DEPS_PREFIX}/lib/libtag.a)
else()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(FFMPEG REQUIRED IMPORTED_TARGET libavformat libavcodec libswresample libavutil)
    pkg_check_modules(TAGLIB REQUIRED IMPORTED_TARGET taglib)
    set(FFMPEG_INCLUDE ${FFMPEG_INCLUDE_DIRS})
    set(FFMPEG_LIBS PkgConfig::FFMPEG)
    set(TAGLIB_INCLUDE ${TAGLIB_INCLUDE_DIRS})
    set(TAGLIB_LIBS PkgConfig::TAGLIB)
endif()

find_package(Threads REQUIRED)

if(LIBUSB_SOURCE)
    # libusb from source, private to this library, as the lab builds it
    set(USB ${LIBUSB_SOURCE}/libusb)
    add_library(nagaudio_usb STATIC
        ${USB}/core.c ${USB}/descriptor.c ${USB}/hotplug.c ${USB}/io.c ${USB}/sync.c ${USB}/strerror.c
        ${USB}/os/linux_usbfs.c ${USB}/os/events_posix.c ${USB}/os/threads_posix.c ${USB}/os/linux_netlink.c)
    target_include_directories(nagaudio_usb PUBLIC ${USB} PRIVATE ${LIBUSB_SOURCE}/android ${USB}/os)
    target_compile_options(nagaudio_usb PRIVATE -fvisibility=hidden -pthread -w)
    list(APPEND CORE sink_usb.c)
endif()

add_library(nagaudio_core STATIC ${CORE})
target_include_directories(nagaudio_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR} PRIVATE ${FFMPEG_INCLUDE} ${TAGLIB_INCLUDE})
target_compile_options(nagaudio_core PRIVATE -fvisibility=hidden -O2 -Wall -Wextra -Wno-unused-parameter)
# TagLib is static too: its symbols stay inside the library (TAGLIB_STATIC keeps its export macro empty)
target_compile_definitions(nagaudio_core PRIVATE TAGLIB_STATIC)
target_link_libraries(nagaudio_core PUBLIC ${FFMPEG_LIBS} ${TAGLIB_LIBS} Threads::Threads m)
if(LIBUSB_SOURCE)
    target_link_libraries(nagaudio_core PUBLIC nagaudio_usb)
endif()

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
