# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
include_directories(C:/Users/12562/Desktop/data/projectcode/MobiPose2/app/src/main/cpp/libyuv/include)
add_subdirectory(C:/Users/12562/Desktop/data/projectcode/MobiPose2/app/src/main/cpp/libyuv ./build)
aux_source_directory(C:/Users/12562/Desktop/data/projectcode/MobiPose2/app/src/main/cpp/ SRC_FILE)
add_library(yuvutil SHARED ${SRC_FILE})
find_library(log-lib log)
target_link_libraries(yuvutil ${log-lib} yuv)
