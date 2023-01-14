cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 20)
project(${PROJECT_NAME})

include_directories(${SRC_MAIN_ABI_HEADS_GEN}/${CMAKE_ANDROID_ARCH_ABI})
link_directories(${SRC_MAIN_JNI_LIBS_GEN}/${CMAKE_ANDROID_ARCH_ABI})

add_executable(libelementum.so src/main/cpp/elementum.cpp)
target_link_libraries(libelementum.so libelementum.so.so)
