cmake_minimum_required(VERSION 3.22.1)
project(tapevibe CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(tapevibe_core STATIC
        core/Engine.cpp
        core/SubSynth.cpp
        core/DrumKit.cpp
        core/Sampler.cpp
        core/Wav.cpp)
target_include_directories(tapevibe_core PUBLIC core)
target_compile_options(tapevibe_core PRIVATE -Wall -Wextra -O2)

if (ANDROID)
    find_package(oboe REQUIRED CONFIG)
    add_library(tapevibe SHARED android/AudioIO.cpp android/jni_bridge.cpp)
    target_link_libraries(tapevibe tapevibe_core oboe::oboe log android)
    target_compile_options(tapevibe PRIVATE -Wall -Wextra -O2)
else ()
    # Host-side tests: cmake -S app/src/main/cpp -B build/native && cmake --build build/native
    find_package(Threads REQUIRED)
    add_executable(engine_tests tests/engine_tests.cpp)
    target_link_libraries(engine_tests tapevibe_core Threads::Threads)
    enable_testing()
    add_test(NAME engine_tests COMMAND engine_tests)
endif ()
