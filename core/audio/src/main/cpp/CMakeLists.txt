cmake_minimum_required(VERSION 3.22.1)
project("songpad_audio")

# Link Oboe via Prefab AAR
find_package(oboe REQUIRED CONFIG)

# Native audio engine library definition for SongPad
# Note: Actual C++ engine implementation will be added during Spike S-1 / Foundation phase
# add_library(songpad_audio SHARED songpad_audio.cpp)
# target_link_libraries(songpad_audio PRIVATE oboe::oboe log)
