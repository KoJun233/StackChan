#include "lifecycle_clip_player.h"

#include <cstdlib>

#include "esp_log.h"
#include "lv_eaf.h"

static const char *TAG = "lifecycle_clip";
static lv_obj_t *s_screen;
static lv_obj_t *s_animation;
static uint8_t *s_data;
static int s_x;
static int s_y;
static bool s_finished;

static void ready_callback(lv_event_t *event)
{
    (void)event;
    s_finished = true;
}

extern "C" void lifecycle_clip_player_init(lv_obj_t *screen, int x, int y)
{
    s_screen = screen;
    s_x = x;
    s_y = y;
}

extern "C" void lifecycle_clip_player_stop(void)
{
    if (s_animation != nullptr) {
        lv_obj_delete(s_animation);
        s_animation = nullptr;
    }
    free(s_data);
    s_data = nullptr;
    s_finished = false;
}

extern "C" bool lifecycle_clip_player_play(expression_lifecycle_clip_t clip)
{
    if (s_screen == nullptr || clip < 0 || clip >= EXPRESSION_LIFECYCLE_COUNT) return false;
    expression_lifecycle_clip_data_t value = {};
    if (expression_pack_read_lifecycle_clip(clip, &value) != ESP_OK) return false;

    lifecycle_clip_player_stop();
    s_data = value.data;
    s_animation = lv_eaf_create(s_screen);
    if (s_animation == nullptr) {
        free(s_data);
        s_data = nullptr;
        return false;
    }
    lv_obj_set_pos(s_animation, s_x, s_y);
    lv_eaf_set_src_data(s_animation, s_data, value.size);
    if (!lv_eaf_is_loaded(s_animation) || lv_eaf_get_total_frames(s_animation) != value.frame_count) {
        ESP_LOGW(TAG, "Verified lifecycle clip was rejected by EAF player");
        lifecycle_clip_player_stop();
        return false;
    }
    lv_eaf_set_frame_delay(s_animation, value.frame_delay_ms);
    lv_eaf_set_loop_enabled(s_animation, false);
    lv_eaf_set_loop_count(s_animation, 0);
    lv_obj_add_event_cb(s_animation, ready_callback, LV_EVENT_READY, nullptr);
    lv_eaf_restart(s_animation);
    lv_eaf_resume(s_animation);
    s_finished = false;
    return true;
}

extern "C" void lifecycle_clip_player_poll(void)
{
    if (s_finished) lifecycle_clip_player_stop();
}

extern "C" bool lifecycle_clip_player_is_active(void)
{
    return s_animation != nullptr && !s_finished;
}
