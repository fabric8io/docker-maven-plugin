cmake_minimum_required(VERSION 3.0)

project(hello LANGUAGES C)

set(compile_definitions)
if(WIN32)
    list(APPEND compile_definitions WIN32 _UNICODE UNICODE)
endif()

if(MINGW)
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -municode")
endif()

add_executable(${PROJECT_NAME} "src/main/c/hello.c")
target_compile_definitions(${PROJECT_NAME} PRIVATE ${compile_definitions})
