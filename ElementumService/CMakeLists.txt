cmake_minimum_required(VERSION ${CMAKE_REQUIRED})
set(CMAKE_CXX_STANDARD ${CXX_STANDARD})
set(ANDROID_STL c++_shared)
project(${PROJECT_NAME})

include_directories(${SRC_MAIN_ABI_HEADS_GEN}/${CMAKE_ANDROID_ARCH_ABI})
link_directories(${SRC_MAIN_JNI_LIBS_GEN}/${CMAKE_ANDROID_ARCH_ABI})

add_executable(libelementum.so src/main/cpp/elementum.cpp)
target_link_libraries(libelementum.so libelementum.so.so)
