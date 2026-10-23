# EliNer Audio Engine — Olyze Creation Studio
#
# Dos capas a propósito:
#   elinercore  — núcleo PORTABLE (C++17, sin Android ni Oboe). Es el mismo código que se compila y prueba en el host
#                 con `make -C app/src/test/cpp test` (paridad con la exportación, carreras de datos, tiempo real).
#   olyzeaudio  — biblioteca JNI: Oboe (salida del dispositivo) + puente con Kotlin.
cmake_minimum_required(VERSION 3.22.1)
project(olyzeaudio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(elinercore STATIC
    core/engine.cpp
    core/timeline.cpp
    core/reverb.cpp
    core/resampler.cpp
    core/mapped_wav.cpp
    core/wav_io.cpp)
target_include_directories(elinercore PUBLIC core)
# -ffp-contract=off: el reverb es un port bit-a-bit de la versión Kotlin (la JVM no fusiona multiplicar+sumar). Sin esto, el
# compilador de ARM64 podría generar instrucciones FMA y la cola del reverb divergiría de la exportación.
target_compile_options(elinercore PRIVATE -Wall -Wextra -Werror -fvisibility=hidden -ffp-contract=off)
set_target_properties(elinercore PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Oboe llega como paquete prefab de Google Maven (ver app/build.gradle.kts: `prefab = true` + com.google.oboe:oboe).
find_package(oboe REQUIRED CONFIG)

add_library(olyzeaudio SHARED
    android/oboe_output.cpp
    android/jni_bridge.cpp)
target_include_directories(olyzeaudio PRIVATE android)
target_compile_options(olyzeaudio PRIVATE -Wall -Wextra)
# Compatibilidad con dispositivos de páginas de 16 KB (Android 15+): alinear los segmentos de la biblioteca.
target_link_options(olyzeaudio PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(olyzeaudio PRIVATE elinercore oboe::oboe log android)
