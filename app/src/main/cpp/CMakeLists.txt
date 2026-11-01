# Motor local de Ignis: llama.cpp compilado para Android y un puente JNI pequeño (ignis_llm.cpp).
cmake_minimum_required(VERSION 3.22.1)
project(ignis_llm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp en una versión fija (etiqueta b12000). El hash del commit garantiza que siempre es el mismo código.
include(FetchContent)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
if(ANDROID)
    # Una variante del motor por generación de procesador (ARMv8.0, 8.2 con dotprod, 8.6 con i8mm…):
    # al arrancar se carga la más rápida que el teléfono soporta, sin riesgo de instrucciones ilegales.
    set(BUILD_SHARED_LIBS     ON CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL       ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
endif()

if(DEFINED IGNIS_LLAMA_DIR)
    # Copia local de llama.cpp (para probar sin red)
    add_subdirectory(${IGNIS_LLAMA_DIR} llama-build)
else()
    set(IGNIS_LLAMA_COMMIT 68d20b884c3878217de79eade0a249efde96ab38)
    FetchContent_Declare(llama
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG        b12000
        GIT_SHALLOW    TRUE)
    FetchContent_MakeAvailable(llama)
    # La etiqueta tiene que apuntar al commit esperado; si no, no se compila
    find_package(Git REQUIRED)
    execute_process(COMMAND ${GIT_EXECUTABLE} rev-parse HEAD
        WORKING_DIRECTORY ${llama_SOURCE_DIR} OUTPUT_VARIABLE _llama_head OUTPUT_STRIP_TRAILING_WHITESPACE)
    if(NOT _llama_head STREQUAL IGNIS_LLAMA_COMMIT)
        message(FATAL_ERROR "llama.cpp b12000 no coincide: ${_llama_head} != ${IGNIS_LLAMA_COMMIT}")
    endif()
endif()

add_library(ignis_llm SHARED ignis_llm.cpp)
target_link_libraries(ignis_llm PRIVATE llama ggml)
if(ANDROID)
    target_link_libraries(ignis_llm PRIVATE android log)
endif()
