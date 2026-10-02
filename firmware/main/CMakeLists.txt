set(stackchan_sources
    "audio_wav.c"
    "app_main.c"
    "body_hardware.cpp"
    "body_local_policy.c"
    "body_touch_policy.c"
    "companion_hardware.cpp"
    "continuous_conversation.c"
    "device_credentials.c"
    "device_endpoint.c"
    "device_identity.c"
    "device_provisioning.c"
    "device_protocol.c"
    "device_transport.c"
    "device_ui_font.c"
    "device_ui.cpp"
    "device_ui_protocol.c"
    "display_frame_policy.c"
    "expression_pack.c"
    "expression_engine.c"
    "firmware_ota.c"
    "face_animation.c"
    "face_tracking.cpp"
    "face_tracking_policy.c"
    "interaction_state.c"
    "lifecycle_clip_player.cpp"
    "motion_planner.c"
    "robot_eyes_renderer.c"
    "safety_state.c"
    "screensaver_motion.c"
    "strict_json.c"
    "touch_interaction.c"
    "voice_capture_policy.c"
    "voice_control.c"
    "voice_protocol.c"
    "voice_service.c"
    "wake_model_ota.c"
    "wake_word_model.c")

if(STACKCHAN_MEDIA003_BACKEND STREQUAL "eaf")
    list(APPEND stackchan_sources "media003_backend_probe.cpp")
elseif(STACKCHAN_MEDIA003_BACKEND STREQUAL "emote")
    list(APPEND stackchan_sources "media003_backend_probe.cpp")
endif()

if(CONFIG_STACKCHAN_PROTOCOL_TESTS)
    list(APPEND stackchan_sources "test/test_device_protocol.c")
endif()

if(CONFIG_STACKCHAN_LAN_HTTP_MODE AND CONFIG_STACKCHAN_LAN_TEST_CERT)
    message(FATAL_ERROR "STACKCHAN_LAN_HTTP_MODE and STACKCHAN_LAN_TEST_CERT are mutually exclusive")
endif()

if(CONFIG_ESP_MAIN_TASK_STACK_SIZE LESS 8192)
    message(FATAL_ERROR
        "StackChan requires CONFIG_ESP_MAIN_TASK_STACK_SIZE >= 8192; discard stale generated sdkconfig files and reconfigure from sdkconfig.defaults")
endif()

set(stackchan_requires app_update esp-sr esp_app_format esp_codec_dev esp_driver_usb_serial_jtag esp_event esp_http_client esp_hw_support esp_lv_eaf_player esp_netif esp_partition esp_timer esp_wifi esp_websocket_client freertos json lvgl m5stack_core_s3 mbedtls nvs_flash sensor_hub unity)
if(NOT CONFIG_STACKCHAN_PROTOCOL_TESTS)
    list(APPEND stackchan_requires human_face_detect esp_video)
endif()

idf_component_register(
    SRCS ${stackchan_sources}
    INCLUDE_DIRS "."
    REQUIRES ${stackchan_requires})

# Observe the driver's public flush-ready boundary without changing the BSP or
# replacing its DMA callback. The wrapper always invokes the original function.
target_link_libraries(${COMPONENT_LIB} INTERFACE "-Wl,--wrap=lv_display_flush_ready")

option(STACKCHAN_STACK_USAGE "Generate stack reports for firmware budget verification" OFF)
if(STACKCHAN_STACK_USAGE)
    target_compile_options(${COMPONENT_LIB} PRIVATE -fstack-usage)
    idf_component_get_property(stackchan_lvgl_lib lvgl__lvgl COMPONENT_LIB)
    target_compile_options(${stackchan_lvgl_lib} PRIVATE -fstack-usage)
    # esp_lvgl_port registers an INTERFACE component and a separate real library.
    target_compile_options(lvgl_port_lib PRIVATE -fstack-usage)
endif()

if(STACKCHAN_MEDIA003_BACKEND STREQUAL "eaf")
    target_compile_definitions(${COMPONENT_LIB} PRIVATE STACKCHAN_MEDIA003_EAF_PROBE=1)
elseif(STACKCHAN_MEDIA003_BACKEND STREQUAL "emote")
    target_compile_definitions(${COMPONENT_LIB} PRIVATE STACKCHAN_MEDIA003_EMOTE_PROBE=1)
endif()

if(CONFIG_STACKCHAN_LAN_TEST_CERT)
    target_add_binary_data(${COMPONENT_LIB} "lan-test-server.pem" TEXT)
endif()

if(NOT CONFIG_STACKCHAN_PROTOCOL_TESTS)
    target_add_binary_data(${COMPONENT_LIB} "fonts/device_cjk14.bin" BINARY)
    target_add_binary_data(${COMPONENT_LIB} "fonts/device_menu18.bin" BINARY)
    target_add_binary_data(${COMPONENT_LIB} "fonts/device_numbers32.bin" BINARY)
    # human_face_detect 0.5 predates ESP-DL's compile requirements. Generate them
    # from its real graphs to avoid linking every unrelated inference operator.
    idf_component_get_property(stackchan_dl_dir espressif__esp-dl COMPONENT_DIR)
    idf_component_get_property(stackchan_face_dir espressif__human_face_detect COMPONENT_DIR)
    idf_build_get_property(stackchan_python PYTHON)
    set(stackchan_face_req "${CMAKE_CURRENT_BINARY_DIR}/face.compile_req.yml")
    set(stackchan_req_generator "${CMAKE_CURRENT_SOURCE_DIR}/../../scripts/generate-face-compile-requirements.py")
    set(stackchan_msr "${stackchan_face_dir}/models/s3/human_face_detect_msr_s8_v1.espdl")
    set(stackchan_mnp "${stackchan_face_dir}/models/s3/human_face_detect_mnp_s8_v1.espdl")
    execute_process(COMMAND "${stackchan_python}" "${stackchan_req_generator}"
        --model "${stackchan_msr}" --model "${stackchan_mnp}"
        --kernels "${stackchan_dl_dir}/spec/kernels.yml" --output "${stackchan_face_req}"
        RESULT_VARIABLE stackchan_req_result OUTPUT_VARIABLE stackchan_req_note ERROR_VARIABLE stackchan_req_error)
    if(NOT stackchan_req_result EQUAL 0)
        message(FATAL_ERROR "Face operator requirements failed: ${stackchan_req_error}")
    endif()
    message(STATUS "${stackchan_req_note}")
    include("${stackchan_dl_dir}/cmake/compile_register.cmake")
    esp_dl_compile_add_yml("${stackchan_face_req}")
    set_property(DIRECTORY "${CMAKE_SOURCE_DIR}" APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
        "${stackchan_req_generator}" "${stackchan_msr}" "${stackchan_mnp}")
endif()
