cmake_minimum_required(VERSION 3.22)
project(nikontransfer)

set(GP_DIR ${CMAKE_SOURCE_DIR}/../../../libs/gphoto2/${ANDROID_ABI})

# 预编译库（IMPORTED，不重新编译）
add_library(gphoto2 SHARED IMPORTED)
set_target_properties(gphoto2 PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2.so)

add_library(gphoto2_port SHARED IMPORTED)
set_target_properties(gphoto2_port PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2_port.so)

# 我们的 JNI 封装
add_library(gphoto2_jni SHARED gphoto2_jni.c)

target_include_directories(gphoto2_jni PRIVATE ${GP_DIR}/include)
target_link_libraries(gphoto2_jni
        gphoto2
        gphoto2_port
        log)   # android logcat