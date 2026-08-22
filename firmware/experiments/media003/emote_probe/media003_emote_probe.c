#include "media003_emote_probe.h"

#include <inttypes.h>

#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "gfx.h"

static const char *TAG = "media003_emote";
static media003_emote_probe_diagnostics_t s_diagnostics;

void media003_emote_probe_run(void)
{
    size_t before = heap_caps_get_free_size(MALLOC_CAP_8BIT);
    int64_t started = esp_timer_get_time();
    gfx_core_config_t config = {
        .fps = 60,
        .task = GFX_EMOTE_INIT_CONFIG(),
    };
    gfx_handle_t handle = gfx_emote_init(&config);
    s_diagnostics.ready = handle != NULL;
    if (handle != NULL) gfx_emote_deinit(handle);
    size_t after = heap_caps_get_free_size(MALLOC_CAP_8BIT);
    s_diagnostics.init_time_us = (uint32_t)(esp_timer_get_time() - started);
    s_diagnostics.heap_delta_bytes = before > after ? (uint32_t)(before - after) : 0U;
    ESP_LOGI(TAG, "lifecycle ready=%s init=%" PRIu32 " us retained_heap=%" PRIu32,
             s_diagnostics.ready ? "true" : "false", s_diagnostics.init_time_us,
             s_diagnostics.heap_delta_bytes);
}

void media003_emote_probe_get_diagnostics(
    media003_emote_probe_diagnostics_t *diagnostics)
{
    if (diagnostics != NULL) *diagnostics = s_diagnostics;
}
