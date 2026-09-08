cmake_minimum_required(VERSION 3.22.1)
project("songpad_audio" LANGUAGES CXX)

# Oboe is consumed through Prefab from the com.google.oboe:oboe AAR (§4.1).
find_package(oboe REQUIRED CONFIG)

add_library(songpad_audio SHARED
    ring_buffer.h
    capture_engine.h
    capture_engine.cpp
    jni_bridge.cpp
)

target_compile_options(songpad_audio PRIVATE -Wall -Wextra -Werror=return-type -O2)
target_link_libraries(songpad_audio PRIVATE oboe::oboe log android)

# ── LAME MP3 encoder (§12.4) — export path only. Vendor lame-3.100/libmp3lame into src/main/cpp/lame/
# following docs/audio/LAME_INTEGRATION.md; until then the Kotlin seam reports MP3 as unavailable.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/lame.c")
    message(STATUS "songpad: building bundled LAME")
    file(GLOB LAME_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/*.c")
    add_library(songpad_lame SHARED ${LAME_SOURCES} lame_jni.cpp)
    target_include_directories(songpad_lame PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/lame"
        "${CMAKE_CURRENT_SOURCE_DIR}/lame/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame")
    target_compile_definitions(songpad_lame PRIVATE STDC_HEADERS HAVE_CONFIG_H)
    target_compile_options(songpad_lame PRIVATE -O2 -w)
    target_link_libraries(songpad_lame PRIVATE log)
else()
    message(STATUS "songpad: LAME sources not vendored; MP3 export disabled in this build")
endif()
