#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "lvgl.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    const char *backend;
    uint32_t asset_bytes;
    uint32_t init_time_us;
    uint32_t heap_delta_bytes;
    bool ready;
} media003_backend_probe_diagnostics_t;

void media003_backend_probe_init(lv_obj_t *screen, int x, int y);
bool media003_backend_probe_set_active(bool active);
void media003_backend_probe_get_diagnostics(media003_backend_probe_diagnostics_t *diagnostics);

#ifdef __cplusplus
}
#endif
