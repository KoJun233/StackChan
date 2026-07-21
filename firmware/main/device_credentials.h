#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "device_identity.h"

typedef enum {
    DEVICE_CREDENTIAL_REFRESHED,
    DEVICE_CREDENTIAL_TEMPORARY_FAILURE,
    DEVICE_CREDENTIAL_REPAIR_REQUIRED,
} device_credential_refresh_result_t;

device_credential_refresh_result_t device_credentials_refresh(device_identity_t *identity);

device_credential_refresh_result_t device_credentials_classify_refresh_result(bool transport_succeeded,
                                                                              int status_code,
                                                                              bool response_overflowed,
                                                                              bool response_valid);

bool device_credentials_parse_refresh_response(const char *json,
                                               size_t length,
                                               device_identity_t *identity);
