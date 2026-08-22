cmake_minimum_required(VERSION 3.16)

set(STACKCHAN_MEDIA003_BACKEND "native" CACHE STRING
    "MEDIA-003 renderer probe: native, eaf, or emote")
set_property(CACHE STACKCHAN_MEDIA003_BACKEND PROPERTY STRINGS native eaf emote)

if(STACKCHAN_MEDIA003_BACKEND STREQUAL "eaf")
    list(APPEND EXTRA_COMPONENT_DIRS
        "${CMAKE_CURRENT_SOURCE_DIR}/experiments/media003/eaf_probe")
elseif(STACKCHAN_MEDIA003_BACKEND STREQUAL "emote")
    list(APPEND EXTRA_COMPONENT_DIRS
        "${CMAKE_CURRENT_SOURCE_DIR}/experiments/media003/emote_probe")
elseif(NOT STACKCHAN_MEDIA003_BACKEND STREQUAL "native")
    message(FATAL_ERROR
        "STACKCHAN_MEDIA003_BACKEND must be native, eaf, or emote")
endif()

include($ENV{IDF_PATH}/tools/cmake/project.cmake)

if(STACKCHAN_MEDIA003_DEPENDENCIES_LOCK)
    idf_build_set_property(DEPENDENCIES_LOCK
        "${CMAKE_CURRENT_SOURCE_DIR}/${STACKCHAN_MEDIA003_DEPENDENCIES_LOCK}")
elseif(STACKCHAN_MEDIA003_BACKEND STREQUAL "eaf")
    idf_build_set_property(DEPENDENCIES_LOCK
        "${CMAKE_CURRENT_SOURCE_DIR}/experiments/media003/dependencies.eaf.lock")
elseif(STACKCHAN_MEDIA003_BACKEND STREQUAL "emote")
    idf_build_set_property(DEPENDENCIES_LOCK
        "${CMAKE_CURRENT_SOURCE_DIR}/experiments/media003/dependencies.emote.lock")
endif()

project(stackchan_firmware)
