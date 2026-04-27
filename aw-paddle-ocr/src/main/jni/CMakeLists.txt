project(aw_ppocrv5)

cmake_minimum_required(VERSION 3.10)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.13.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20260113-android/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# 讓 CMake/NDK toolchain 自行決定正確的 OpenMP 連結方式（避免 CI 出現 __kmpc_* undefined）
find_package(OpenMP)

# opencv-mobile 的 `opencv_core` 目標會透過 INTERFACE_LINK_LIBRARIES 注入 `-fopenmp;-static-openmp`，
# 這會在部分 NDK/CI 造成 OpenMP runtime 解析錯誤（例如 __kmpc_dispatch_deinit undefined）。
# 這裡移除該注入，改由 OpenMP::OpenMP_CXX 統一處理。
if(TARGET opencv_core)
    get_target_property(_ocv_link_libs opencv_core INTERFACE_LINK_LIBRARIES)
    if(_ocv_link_libs)
        string(REPLACE ";-fopenmp" "" _ocv_link_libs "${_ocv_link_libs}")
        string(REPLACE ";-static-openmp" "" _ocv_link_libs "${_ocv_link_libs}")
        string(REPLACE "-fopenmp;" "" _ocv_link_libs "${_ocv_link_libs}")
        string(REPLACE "-static-openmp;" "" _ocv_link_libs "${_ocv_link_libs}")
        string(REPLACE "-fopenmp" "" _ocv_link_libs "${_ocv_link_libs}")
        string(REPLACE "-static-openmp" "" _ocv_link_libs "${_ocv_link_libs}")
        set_target_properties(opencv_core PROPERTIES INTERFACE_LINK_LIBRARIES "${_ocv_link_libs}")
    endif()
endif()

add_library(aw_ppocrv5 SHARED aw_ppocrv5_jni.cpp ppocrv5.cpp openmp_compat.cpp)

target_compile_options(aw_ppocrv5 PRIVATE -fexceptions -frtti)

target_link_libraries(aw_ppocrv5 ncnn ${OpenCV_LIBS} android jnigraphics log)

if(OpenMP_CXX_FOUND)
    target_link_libraries(aw_ppocrv5 OpenMP::OpenMP_CXX)
endif()

target_link_options(aw_ppocrv5 PRIVATE -Wl,-z,max-page-size=16384)
