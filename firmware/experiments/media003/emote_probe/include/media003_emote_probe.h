#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t init_time_us;
    uint32_t heap_delta_bytes;
    bool ready;
} media003_emote_probe_diagnostics_t;

void media003_emote_probe_run(void);
void media003_emote_probe_get_diagnostics(media003_emote_probe_diagnostics_t *diagnostics);

#ifdef __cplusplus
}
#endif
