#include "safety_state.h"

#include "esp_log.h"

static const char *TAG = "safety_state";
static safety_state_t s_current_state = SAFETY_STATE_MOTION_DISABLED;

void safety_state_init(void)
{
    s_current_state = SAFETY_STATE_MOTION_DISABLED;
    ESP_LOGI(TAG, "Safety state is motion_disabled");
}

safety_state_t safety_state_current(void)
{
    return s_current_state;
}

void safety_state_stop_motion(void)
{
    s_current_state = SAFETY_STATE_MOTION_DISABLED;
}

const char *safety_state_name(safety_state_t state)
{
    (void)state;
    return "motion_disabled";
}
