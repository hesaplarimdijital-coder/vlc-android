# libnagmedar_translate.so: offline subtitle translation (S-710), CTranslate2 (MIT) running
# OPUS-MT models on the CPU in int8 through Ruy, with SentencePiece (Apache-2.0) for their
# tokens. Its own library, apart from the lab's: the lab's FFmpeg and whisper.cpp are not
# needed here, and the two are built and loaded on their own.
# Built by buildsystem/nagmedar-native/build.sh, which gives the CTranslate2 (with its spdlog,
# Ruy and cpu_features submodules) and SentencePiece sources.
cmake_minimum_required(VERSION 3.22)
project(nagmedar_translate C CXX)

set(CMAKE_CXX_STANDARD 17)
# as a cache entry too: CTranslate2 unsets the plain variable after its Ruy, and its own library
# would then be built for an executable, which a shared library cannot link
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON CACHE BOOL "" FORCE)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
# the bundled projects are older than these policies: their libraries hidden too, our options kept
set(CMAKE_POLICY_DEFAULT_CMP0063 NEW)
set(CMAKE_POLICY_DEFAULT_CMP0077 NEW)
set(CT2_SOURCE "" CACHE PATH "CTranslate2 source tree, with its submodules")
set(SPM_SOURCE "" CACHE PATH "SentencePiece source tree")
set(CT2_VERSION "" CACHE STRING "CTranslate2's version, for version()")
set(SPM_VERSION "" CACHE STRING "SentencePiece's version, for version()")

# CTranslate2 for the CPU alone: int8 matrices through Ruy, no MKL, no OpenMP, no clients
set(WITH_MKL OFF CACHE BOOL "" FORCE)
set(WITH_DNNL OFF CACHE BOOL "" FORCE)
set(WITH_RUY ON CACHE BOOL "" FORCE)
set(WITH_CUDA OFF CACHE BOOL "" FORCE)
set(OPENMP_RUNTIME NONE CACHE STRING "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(${CT2_SOURCE} ctranslate2 EXCLUDE_FROM_ALL)
set_target_properties(ctranslate2 PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(ANDROID)
    target_compile_options(ctranslate2 PRIVATE -include ${CMAKE_CURRENT_SOURCE_DIR}/android_compat.h)
endif()

# SentencePiece's static library with its own protobuf-lite; no shared library, no tcmalloc
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory(${SPM_SOURCE} sentencepiece EXCLUDE_FROM_ALL)

add_library(nagmedar_translate SHARED translate_jni.cpp)
target_include_directories(nagmedar_translate PRIVATE ${SPM_SOURCE}/src)
target_compile_options(nagmedar_translate PRIVATE -O2 -Wall)
target_compile_definitions(nagmedar_translate PRIVATE
    NAGMEDAR_CT2_VERSION="${CT2_VERSION}" NAGMEDAR_SPM_VERSION="${SPM_VERSION}")
target_link_libraries(nagmedar_translate PRIVATE ctranslate2 sentencepiece-static)
if(ANDROID)
    target_link_libraries(nagmedar_translate PRIVATE log)
else()
    # host build: a link check of the JNI library
    find_package(JNI REQUIRED)
    target_include_directories(nagmedar_translate PRIVATE ${JNI_INCLUDE_DIRS})
endif()
# Everything bundled stays private; 16 KB pages, as Android 15 and later devices may use
target_link_options(nagmedar_translate PRIVATE
    -Wl,--exclude-libs,ALL -Wl,-Bsymbolic -Wl,--gc-sections -Wl,-z,max-page-size=16384)
