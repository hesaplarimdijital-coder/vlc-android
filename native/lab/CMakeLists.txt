# libnagmedar_lab.so: the native part of Settings > Lab (SP-02 USB audio, SP-05 whisper), the
# media probe behind the library's technical badges (S-802, libdovi reads the first Dolby Vision
# RPU), the subtitles from a film's speech (S-709, on the GPU through ggml's Vulkan backend where
# the device has one) and the extraction of a film's embedded text subtitles into files.
# Built by buildsystem/nagmedar-native/build.sh android, which provides the static
# dependencies in DEPS_PREFIX (FFmpeg, libdovi, whisper.cpp) and the libusb sources.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_lab C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(DEPS_PREFIX "" CACHE PATH "prefix with FFmpeg, libdovi and whisper.cpp")
set(LIBUSB_SOURCE "" CACHE PATH "libusb source tree")
# S-709: whisper.cpp built with ggml's Vulkan backend (the Android build). The system's Vulkan
# loader is opened at run time (vulkan_loader.c): the library loads on devices without it. The
# backend registry's own call to ggml_backend_vk_reg goes to whisper_engine.cpp's wrapper, so
# Vulkan starts only when a job asks for the GPU.
option(NAGMEDAR_VULKAN "whisper.cpp was built with ggml's Vulkan backend" OFF)
set(VULKAN_INCLUDE_DIR "" CACHE PATH "Vulkan-Headers' include directory (with NAGMEDAR_VULKAN)")

set(WHISPER_LIBS ${DEPS_PREFIX}/lib/libwhisper.a ${DEPS_PREFIX}/lib/libggml.a)
set(WHISPER_SOURCES whisper_engine.cpp)
if(NAGMEDAR_VULKAN)
    list(APPEND WHISPER_LIBS ${DEPS_PREFIX}/lib/libggml-vulkan.a)
    list(APPEND WHISPER_SOURCES vulkan_loader.c)
endif()
list(APPEND WHISPER_LIBS ${DEPS_PREFIX}/lib/libggml-cpu.a ${DEPS_PREFIX}/lib/libggml-base.a)

set(USB ${LIBUSB_SOURCE}/libusb)
add_library(usb STATIC
    ${USB}/core.c ${USB}/descriptor.c ${USB}/hotplug.c ${USB}/io.c ${USB}/sync.c ${USB}/strerror.c
    ${USB}/os/linux_usbfs.c ${USB}/os/events_posix.c ${USB}/os/threads_posix.c ${USB}/os/linux_netlink.c)
target_include_directories(usb PUBLIC ${USB} PRIVATE ${LIBUSB_SOURCE}/android ${USB}/os)
target_compile_options(usb PRIVATE -fvisibility=hidden -pthread -w)

add_library(nagmedar_lab SHARED lab_jni.c mediaprobe.c usb_audio.c whisper_lab.cpp
    whisper_subs.cpp whisper_subs_jni.c subtitle_extract.c subtitle_extract_jni.c ${WHISPER_SOURCES})
target_include_directories(nagmedar_lab PRIVATE ${DEPS_PREFIX}/include)
target_compile_options(nagmedar_lab PRIVATE -fvisibility=hidden -O2 -Wall)
if(NAGMEDAR_VULKAN)
    target_compile_definitions(nagmedar_lab PRIVATE NAGMEDAR_VULKAN)
    target_include_directories(nagmedar_lab PRIVATE ${VULKAN_INCLUDE_DIR})
    target_link_options(nagmedar_lab PRIVATE -Wl,--wrap=ggml_backend_vk_reg)
endif()
target_link_libraries(nagmedar_lab PRIVATE
    usb
    ${WHISPER_LIBS}
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
    add_executable(whisper_lab_cli whisper_lab_cli.c whisper_lab.cpp ${WHISPER_SOURCES})
    target_include_directories(whisper_lab_cli PRIVATE ${DEPS_PREFIX}/include)
    if(NAGMEDAR_VULKAN)
        target_compile_definitions(whisper_lab_cli PRIVATE NAGMEDAR_VULKAN)
        target_include_directories(whisper_lab_cli PRIVATE ${VULKAN_INCLUDE_DIR})
        target_link_options(whisper_lab_cli PRIVATE -Wl,--wrap=ggml_backend_vk_reg)
    endif()
    target_link_libraries(whisper_lab_cli PRIVATE
        ${WHISPER_LIBS} ${DEPS_PREFIX}/lib/libavformat.a ${DEPS_PREFIX}/lib/libavcodec.a
        ${DEPS_PREFIX}/lib/libswresample.a ${DEPS_PREFIX}/lib/libavutil.a Threads::Threads dl m)
endif()
# Keep every bundled library private: libvlc loaded in the same process carries its own
# FFmpeg, and the two must never bind to each other's symbols.
target_link_options(nagmedar_lab PRIVATE
    -Wl,--exclude-libs,ALL -Wl,-Bsymbolic -Wl,--gc-sections -Wl,-z,max-page-size=16384)
