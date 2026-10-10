# libnagmedar_lab.so: the native part of Settings > Lab (SP-03).
# Built by buildsystem/nagmedar-native/build.sh android, which provides the static
# dependencies in DEPS_PREFIX (FFmpeg, libdovi).
cmake_minimum_required(VERSION 3.22)
project(nagmedar_lab C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(DEPS_PREFIX "" CACHE PATH "prefix with FFmpeg and libdovi")

add_library(nagmedar_lab SHARED lab_jni.c dvconvert.c)
target_include_directories(nagmedar_lab PRIVATE ${DEPS_PREFIX}/include)
target_compile_options(nagmedar_lab PRIVATE -fvisibility=hidden -O2 -Wall)
target_link_libraries(nagmedar_lab PRIVATE
    ${DEPS_PREFIX}/lib/libavformat.a
    ${DEPS_PREFIX}/lib/libavcodec.a
    ${DEPS_PREFIX}/lib/libswresample.a
    ${DEPS_PREFIX}/lib/libavutil.a
    ${DEPS_PREFIX}/lib/libdovi.a
    dl m)
if(ANDROID)
    target_link_libraries(nagmedar_lab PRIVATE log android)
else()
    # host build: link check of the JNI library
    find_package(JNI REQUIRED)
    find_package(Threads REQUIRED)
    target_include_directories(nagmedar_lab PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(nagmedar_lab PRIVATE Threads::Threads)
endif()
# Keep every bundled library private: libvlc loaded in the same process carries its own
# FFmpeg, and the two must never bind to each other's symbols.
target_link_options(nagmedar_lab PRIVATE
    -Wl,--exclude-libs,ALL -Wl,-Bsymbolic -Wl,--gc-sections -Wl,-z,max-page-size=16384)
