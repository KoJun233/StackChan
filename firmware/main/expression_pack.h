#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

#include "device_identity.h"
#include "interaction_state.h"

#define EXPRESSION_PACK_ID_SIZE 37
#define EXPRESSION_PACK_SHA256_SIZE 65
#define EXPRESSION_PACK_MAX_ARTIFACT_SIZE (1536U * 1024U)

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    char pack_id[EXPRESSION_PACK_ID_SIZE];
    char sha256[EXPRESSION_PACK_SHA256_SIZE];
    size_t artifact_size;
} expression_pack_request_t;

/** Restores and validates the active A/B expression slot, falling back to built-in rendering. */
esp_err_t expression_pack_init(void);

bool expression_pack_is_active(void);

/** Downloads, verifies and atomically activates an expression package without rebooting. */
esp_err_t expression_pack_install(const device_identity_t *identity,
                                  const expression_pack_request_t *request);

/** Disables custom rendering and erases both resource slots. */
esp_err_t expression_pack_clear(void);

/** Reads one verified PNG into PSRAM. The caller owns the returned buffer. */
esp_err_t expression_pack_read_state(companion_face_state_t state,
                                     uint8_t **image,
                                     size_t *image_size);

#ifdef __cplusplus
}
#endif
