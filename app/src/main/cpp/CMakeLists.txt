cmake_minimum_required(VERSION 3.22.1)
project(opencorrector_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- llama.cpp (vendored as a git submodule under third_party/llama.cpp) ---------------------
# Build it as static libraries so the app only ever has to load one .so (libopencorrector.so).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# GPU (Vulkan) offload is OPTIONAL and OFF by default: the app must build and run correctly
# CPU-only. To try Vulkan on a device that supports it, reconfigure with
# -DOPENCORRECTOR_ENABLE_VULKAN=ON (requires the Vulkan NDK headers to be available).
option(OPENCORRECTOR_ENABLE_VULKAN "Build llama.cpp with the Vulkan GPU backend" OFF)
if(OPENCORRECTOR_ENABLE_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(third_party/llama.cpp llama_build)

# --- OpenCorrector JNI bridge -----------------------------------------------------------------
add_library(opencorrector SHARED
    llama_jni.cpp
)

target_include_directories(opencorrector PRIVATE
    third_party/llama.cpp/include
    third_party/llama.cpp/ggml/include
)

target_link_libraries(opencorrector
    llama
    android
    log
)

target_compile_definitions(opencorrector PRIVATE
    $<$<BOOL:${OPENCORRECTOR_ENABLE_VULKAN}>:OPENCORRECTOR_VULKAN_ENABLED>
)
