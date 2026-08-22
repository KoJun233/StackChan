#include "media003_eaf_probe.h"

#include <cinttypes>
#include <cstddef>
#include <cstring>

#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "lv_eaf.h"

extern const uint8_t media003_eaf_start[]
    asm("_binary_media003_benchmark_eaf_start");
extern const uint8_t media003_eaf_end[]
    asm("_binary_media003_benchmark_eaf_end");

static const char *TAG = "media003_eaf";
static lv_obj_t *s_animation;
static media003_eaf_probe_diagnostics_t s_diagnostics;

extern "C" void media003_eaf_probe_init(lv_obj_t *screen, int x, int y)
{
    if (screen == nullptr || s_animation != nullptr) return;
    size_t before = heap_caps_get_free_size(MALLOC_CAP_8BIT);
    int64_t started = esp_timer_get_time();
    s_animation = lv_eaf_create(screen);
    if (s_animation != nullptr) {
        lv_obj_set_pos(s_animation, x, y);
        lv_eaf_set_src_data(s_animation, media003_eaf_start,
                            media003_eaf_end - media003_eaf_start);
        lv_eaf_set_frame_delay(s_animation, 17U);
        lv_eaf_set_loop_count(s_animation, -1);
        lv_obj_add_flag(s_animation, LV_OBJ_FLAG_HIDDEN);
        lv_eaf_pause(s_animation);
    }
    size_t after = heap_caps_get_free_size(MALLOC_CAP_8BIT);
    s_diagnostics.asset_bytes = (uint32_t)(media003_eaf_end - media003_eaf_start);
    s_diagnostics.init_time_us = (uint32_t)(esp_timer_get_time() - started);
    s_diagnostics.heap_delta_bytes = before > after ? (uint32_t)(before - after) : 0U;
    s_diagnostics.ready = s_animation != nullptr && lv_eaf_is_loaded(s_animation);
    ESP_LOGI(TAG, "probe ready=%s asset=%" PRIu32 " init=%" PRIu32
                  " us heap_delta=%" PRIu32,
             s_diagnostics.ready ? "true" : "false", s_diagnostics.asset_bytes,
             s_diagnostics.init_time_us, s_diagnostics.heap_delta_bytes);
}

extern "C" bool media003_eaf_probe_set_active(bool active)
{
    if (!s_diagnostics.ready || s_animation == nullptr) return false;
    bool hidden = lv_obj_has_flag(s_animation, LV_OBJ_FLAG_HIDDEN);
    if (active && hidden) {
        lv_obj_remove_flag(s_animation, LV_OBJ_FLAG_HIDDEN);
        lv_eaf_restart(s_animation);
        lv_eaf_resume(s_animation);
    } else if (!active && !hidden) {
        lv_eaf_pause(s_animation);
        lv_obj_add_flag(s_animation, LV_OBJ_FLAG_HIDDEN);
    }
    return active;
}

extern "C" void media003_eaf_probe_get_diagnostics(
    media003_eaf_probe_diagnostics_t *diagnostics)
{
    if (diagnostics != nullptr) *diagnostics = s_diagnostics;
}
