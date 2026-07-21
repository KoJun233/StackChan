set(stackchan_sources
    "audio_wav.c"
    "app_main.c"
    "companion_hardware.cpp"
    "device_credentials.c"
    "device_endpoint.c"
    "device_identity.c"
    "device_provisioning.c"
    "device_protocol.c"
    "device_transport.c"
    "safety_state.c"
    "screensaver_motion.c"
    "strict_json.c"
    "voice_control.c"
    "voice_protocol.c"
    "voice_service.c")

if(CONFIG_STACKCHAN_PROTOCOL_TESTS)
    list(APPEND stackchan_sources "test/test_device_protocol.c")
endif()

if(CONFIG_STACKCHAN_LAN_HTTP_MODE AND CONFIG_STACKCHAN_LAN_TEST_CERT)
    message(FATAL_ERROR "STACKCHAN_LAN_HTTP_MODE and STACKCHAN_LAN_TEST_CERT are mutually exclusive")
endif()

idf_component_register(
    SRCS ${stackchan_sources}
    INCLUDE_DIRS "."
    REQUIRES esp-sr esp_app_format esp_driver_usb_serial_jtag esp_event esp_http_client esp_netif esp_timer esp_wifi esp_websocket_client freertos json m5unified nvs_flash unity)

if(CONFIG_STACKCHAN_LAN_TEST_CERT)
    target_add_binary_data(${COMPONENT_LIB} "lan-test-server.pem" TEXT)
endif()
