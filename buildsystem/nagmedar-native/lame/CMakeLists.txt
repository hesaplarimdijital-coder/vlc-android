# LAME's libmp3lame as a static library for the music engine's converter (S-1010): MP3 at
# LAME's VBR qualities and constant rates, with the Xing/LAME frame that carries the encoder
# delay and padding (the file plays gapless). LAME builds with autotools; this lists what its
# libmp3lame/Makefile.am lists, with the config.h its configure writes for a Linux or Android
# target: no decoder (mpglib), no frontend, no x86 vector code, the portable maths (configure
# turns the IEEE 754 shortcuts on for x86 only), so the host and arm64 encode the same way.
# NDEBUG stays on (Release): an assert would abort the whole app, playback with it; the
# converter hands LAME finite samples within full scale only.
#
#   cmake -S buildsystem/nagmedar-native/lame -B <dir> -DLAME_SOURCE=<lame-3.100> -DCMAKE_INSTALL_PREFIX=<prefix>
#
# installs libmp3lame.a and lame/lame.h.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_lame C)

set(LAME_SOURCE "" CACHE PATH "LAME source tree (the lame-3.100 release)")
if(NOT LAME_SOURCE OR NOT EXISTS "${LAME_SOURCE}/libmp3lame/lame.c")
    message(FATAL_ERROR "LAME_SOURCE (${LAME_SOURCE}) is not the LAME source tree")
endif()

set(CMAKE_C_STANDARD 99)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LAME ${LAME_SOURCE}/libmp3lame)
add_library(mp3lame STATIC
    ${LAME}/VbrTag.c ${LAME}/bitstream.c ${LAME}/encoder.c ${LAME}/fft.c ${LAME}/gain_analysis.c
    ${LAME}/id3tag.c ${LAME}/lame.c ${LAME}/newmdct.c ${LAME}/presets.c ${LAME}/psymodel.c
    ${LAME}/quantize.c ${LAME}/quantize_pvt.c ${LAME}/reservoir.c ${LAME}/set_get.c ${LAME}/tables.c
    ${LAME}/takehiro.c ${LAME}/util.c ${LAME}/vbrquantize.c ${LAME}/version.c ${LAME}/mpglib_interface.c)

# what configure finds on Linux and Android
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/config/config.h" "\
#define STDC_HEADERS 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_LIMITS_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDINT_H 1
#define HAVE_STRING_H 1
#define HAVE_STDLIB_H 1
#define HAVE_UNISTD_H 1
#define LAME_LIBRARY_BUILD 1
#include <stdint.h>
typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
")
target_include_directories(mp3lame
    PUBLIC ${LAME_SOURCE}/include
    PRIVATE ${CMAKE_CURRENT_BINARY_DIR}/config ${LAME} ${LAME_SOURCE}/mpglib)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
target_compile_options(mp3lame PRIVATE -fvisibility=hidden -O2 -w)

install(TARGETS mp3lame ARCHIVE DESTINATION lib)
install(FILES ${LAME_SOURCE}/include/lame.h DESTINATION include/lame)
