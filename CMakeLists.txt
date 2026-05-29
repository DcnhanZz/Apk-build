cmake_minimum_required(VERSION 3.22.1)
project("blooddragon_apex" CXX)

add_library(blooddragon_soc SHARED DynamicSoC_Overdrive.cpp)

find_library(log-lib log)
target_link_libraries(blooddragon_soc ${log-lib})
target_compile_options(blooddragon_soc PRIVATE -std=c++17 -O3 -fvisibility=hidden)