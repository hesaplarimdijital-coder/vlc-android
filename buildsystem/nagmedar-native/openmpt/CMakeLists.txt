# libopenmpt as a static library for the music engine (S-911): the tracker formats (MOD, XM,
# S3M, IT, MPTM, MED, OKT, …) rendered by OpenMPT's player, which the engine's FFmpeg reads
# through its libopenmpt demuxer. libopenmpt ships no CMake build of its own (a Makefile, an
# Android.mk and Visual Studio projects), so this lists what its Makefile lists: the common
# code, the sound library with its plugins and DSP, and the C/C++ API; miniz stands in for
# zlib (as the Makefile does without zlib), so the compressed J2B modules load too. Without
# mpg123 and Vorbis, MO3 modules with MP3 or Vorbis samples do not load; the rest do.
#
#   cmake -S buildsystem/nagmedar-native/openmpt -B <dir> -DOPENMPT_SOURCE=<checkout> -DCMAKE_INSTALL_PREFIX=<prefix>
#
# installs libopenmpt.a, the libopenmpt/ headers and lib/pkgconfig/libopenmpt.pc (what FFmpeg's
# configure looks for; OPENMPT_PC_LIBS adds the C++ runtime the static library needs).
cmake_minimum_required(VERSION 3.22)
project(nagmedar_openmpt C CXX)

set(OPENMPT_SOURCE "" CACHE PATH "libopenmpt source tree (a checkout of OpenMPT/openmpt at a libopenmpt-* tag)")
set(OPENMPT_PC_LIBS "-lstdc++ -lm" CACHE STRING "what libopenmpt.pc lists after -lopenmpt: the C++ runtime and libm")
if(NOT OPENMPT_SOURCE)
    message(FATAL_ERROR "OPENMPT_SOURCE is not set")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# the version, from libopenmpt_version.mk
file(STRINGS "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_version.mk" OPENMPT_VERSION_LINES REGEX "^LIBOPENMPT_VERSION_(MAJOR|MINOR|PATCH)=")
foreach(line ${OPENMPT_VERSION_LINES})
    if(line MATCHES "^LIBOPENMPT_VERSION_MAJOR=([0-9]+)")
        set(OPENMPT_MAJOR ${CMAKE_MATCH_1})
    elseif(line MATCHES "^LIBOPENMPT_VERSION_MINOR=([0-9]+)")
        set(OPENMPT_MINOR ${CMAKE_MATCH_1})
    elseif(line MATCHES "^LIBOPENMPT_VERSION_PATCH=([0-9]+)")
        set(OPENMPT_PATCH ${CMAKE_MATCH_1})
    endif()
endforeach()
set(OPENMPT_VERSION "${OPENMPT_MAJOR}.${OPENMPT_MINOR}.${OPENMPT_PATCH}")
message(STATUS "libopenmpt ${OPENMPT_VERSION} from ${OPENMPT_SOURCE}")

# what the Makefile's LIBOPENMPT_CXX_SOURCES lists (src/openmpt is header-only)
file(GLOB OPENMPT_SOURCES CONFIGURE_DEPENDS
    "${OPENMPT_SOURCE}/src/openmpt/all/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/base/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/logging/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/random/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/fileformat_base/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/soundbase/*.cpp"
    "${OPENMPT_SOURCE}/src/openmpt/soundfile_data/*.cpp"
    "${OPENMPT_SOURCE}/common/*.cpp"
    "${OPENMPT_SOURCE}/soundlib/*.cpp"
    "${OPENMPT_SOURCE}/soundlib/plugins/*.cpp"
    "${OPENMPT_SOURCE}/soundlib/plugins/dmo/*.cpp"
    "${OPENMPT_SOURCE}/sounddsp/*.cpp"
    "${OPENMPT_SOURCE}/libopenmpt/*.cpp"
)
list(LENGTH OPENMPT_SOURCES OPENMPT_SOURCE_COUNT)
if(OPENMPT_SOURCE_COUNT LESS 100)
    message(FATAL_ERROR "${OPENMPT_SOURCE} does not look like the libopenmpt source tree (${OPENMPT_SOURCE_COUNT} sources)")
endif()

add_library(openmpt STATIC ${OPENMPT_SOURCES} "${OPENMPT_SOURCE}/include/miniz/miniz.c")
target_include_directories(openmpt
    PUBLIC "${OPENMPT_SOURCE}"
    PRIVATE "${OPENMPT_SOURCE}/src" "${OPENMPT_SOURCE}/common" "${OPENMPT_SOURCE}/include")
target_compile_definitions(openmpt PRIVATE LIBOPENMPT_BUILD MPT_WITH_MINIZ)
target_compile_options(openmpt PRIVATE -fvisibility=hidden -O2 -w
    $<$<COMPILE_LANGUAGE:CXX>:-fexceptions -frtti>)
set_source_files_properties("${OPENMPT_SOURCE}/include/miniz/miniz.c" PROPERTIES COMPILE_OPTIONS "-w")

install(TARGETS openmpt ARCHIVE DESTINATION lib)
install(FILES
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt.hpp"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_config.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_ext.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_ext.hpp"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_version.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_buffer.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_fd.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_file.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_file_mingw.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_file_msvcrt.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_file_posix.h"
    "${OPENMPT_SOURCE}/libopenmpt/libopenmpt_stream_callbacks_file_posix_lfs64.h"
    DESTINATION include/libopenmpt)

# the pkg-config file FFmpeg's configure asks for (`--enable-libopenmpt`): the static library
# and what it needs, as pkg-config --libs gives them
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/libopenmpt.pc"
"prefix=${CMAKE_INSTALL_PREFIX}
exec_prefix=\${prefix}
includedir=\${prefix}/include
libdir=\${prefix}/lib

Name: libopenmpt
Description: Tracker module player based on OpenMPT (static, for the Nagmedar music engine)
URL: https://lib.openmpt.org/
Version: ${OPENMPT_VERSION}
Libs: -L\${libdir} -lopenmpt ${OPENMPT_PC_LIBS}
Cflags: -I\${includedir}
")
install(FILES "${CMAKE_CURRENT_BINARY_DIR}/libopenmpt.pc" DESTINATION lib/pkgconfig)
