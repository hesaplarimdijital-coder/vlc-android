# libnagmedar_lab.so: the native part of Settings > Lab (SP-02, SP-03, SP-05) and the media
# probe behind the library's technical badges (S-802).
# Built by buildsystem/nagmedar-native/build.sh android, which provides the static
# dependencies in DEPS_PREFIX (FFmpeg, libdovi, whisper.cpp) and the libusb sources.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_lab C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(DEPS_PREFIX "" CACHE PATH "prefix with FFmpeg, libdovi and whisper.cpp")
set(LIBUSB_SOURCE "" CACHE PATH "libusb source tree")

set(USB ${LIBUSB_SOURCE}/libusb)
add_library(usb STATIC
    ${USB}/core.c ${USB}/descriptor.c ${USB}/hotplug.c ${USB}/io.c ${USB}/sync.c ${USB}/strerror.c
    ${USB}/os/linux_usbfs.c ${USB}/os/events_posix.c ${USB}/os/threads_posix.c ${USB}/os/linux_netlink.c)
target_include_directories(usb PUBLIC ${USB} PRIVATE ${LIBUSB_SOURCE}/android ${USB}/os)
target_compile_options(usb PRIVATE -fvisibility=hidden -pthread -w)

add_library(nagmedar_lab SHARED lab_jni.c dvconvert.c mediaprobe.c usb_audio.c whisper_lab.cpp)
target_include_directories(nagmedar_lab PRIVATE ${DEPS_PREFIX}/include)
target_compile_options(nagmedar_lab PRIVATE -fvisibility=hidden -O2 -Wall)
target_link_libraries(nagmedar_lab PRIVATE
    usb
    ${DEPS_PREFIX}/lib/libwhisper.a
    ${DEPS_PREFIX}/lib/libggml.a
    ${DEPS_PREFIX}/lib/libggml-cpu.a
    ${DEPS_PREFIX}/lib/libggml-base.a
    ${DEPS_PREFIX}/lib/libavformat.a
    ${DEPS_PREFIX}/lib/libavcodec.a
    ${DEPS_PREFIX}/lib/libswresample.a
    ${DEPS_PREFIX}/lib/libavutil.a
    ${DEPS_PREFIX}/lib/libdovi.a
    dl m)
if(ANDROID)
    target_link_libraries(nagmedar_lab PRIVATE log android)
else()
    # host build: link check of the JNI library and the whisper command line for tests
    find_package(JNI REQUIRED)
    find_package(Threads REQUIRED)
    target_include_directories(nagmedar_lab PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(nagmedar_lab PRIVATE Threads::Threads)
    add_executable(whisper_lab_cli whisper_lab_cli.c whisper_lab.cpp)
    target_include_directories(whisper_lab_cli PRIVATE ${DEPS_PREFIX}/include)
    target_link_libraries(whisper_lab_cli PRIVATE
        ${DEPS_PREFIX}/lib/libwhisper.a ${DEPS_PREFIX}/lib/libggml.a ${DEPS_PREFIX}/lib/libggml-cpu.a
        ${DEPS_PREFIX}/lib/libggml-base.a ${DEPS_PREFIX}/lib/libavformat.a ${DEPS_PREFIX}/lib/libavcodec.a
        ${DEPS_PREFIX}/lib/libswresample.a ${DEPS_PREFIX}/lib/libavutil.a Threads::Threads dl m)
endif()
# Keep every bundled library private: libvlc loaded in the same process carries its own
# FFmpeg, and the two must never bind to each other's symbols.
target_link_options(nagmedar_lab PRIVATE
    -Wl,--exclude-libs,ALL -Wl,-Bsymbolic -Wl,--gc-sections -Wl,-z,max-page-size=16384)
