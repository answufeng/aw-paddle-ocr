project(aw_ppocrv5)

cmake_minimum_required(VERSION 3.10)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.13.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20260113-android/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# 讓 CMake/NDK toolchain 自行決定正確的 OpenMP 連結方式（避免 CI 出現 __kmpc_* undefined）
find_package(OpenMP)

add_library(aw_ppocrv5 SHARED aw_ppocrv5_jni.cpp ppocrv5.cpp)

target_compile_options(aw_ppocrv5 PRIVATE -fexceptions -frtti)

target_link_libraries(aw_ppocrv5 ncnn ${OpenCV_LIBS} android jnigraphics log)

if(OpenMP_CXX_FOUND)
    target_link_libraries(aw_ppocrv5 OpenMP::OpenMP_CXX)
endif()

target_link_options(aw_ppocrv5 PRIVATE -Wl,-z,max-page-size=16384)
