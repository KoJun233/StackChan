#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

#include "device_identity.h"
#include "device_protocol.h"

/** Starts the identity-gated Wi-Fi/WebSocket transport task. */
esp_err_t device_transport_start(void);

/**
 * Stores a bounded Wi-Fi station configuration and starts a reconnect attempt.
 * Credentials are handled only by the ESP-IDF Wi-Fi stack and never logged.
 */
esp_err_t device_transport_configure_wifi(const char *ssid, const char *password);

/** Returns whether the station currently has an IP address. */
bool device_transport_is_wifi_connected(void);

/** Returns the next bounded reconnect delay in seconds. */
uint32_t device_transport_next_retry_seconds(uint32_t current_seconds);

/** Builds the sole WebSocket Authorization request header. */
bool device_transport_build_authorization_header(const device_identity_t *identity, char *header, size_t size);

/** Queues privacy-safe voice turn metadata without blocking the voice path. */
bool device_transport_report_voice_turn(device_voice_turn_stage_t stage,
                                        const char *turn_id,
                                        uint32_t elapsed_ms,
                                        device_voice_turn_failure_t failure);
