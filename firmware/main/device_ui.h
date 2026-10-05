#pragma once
#include <stdbool.h>
#include "esp_err.h"
#include "lvgl.h"
#ifdef __cplusplus
extern "C" {
#endif
/** Reserve local UI/network workers after NVS and before Wi-Fi; camera stays off. */
esp_err_t device_ui_init(void);
bool device_ui_ready(void);
bool device_ui_is_modal(void);
bool device_ui_local_interaction_allowed(void);
void device_ui_open_menu(void);
void device_ui_notify_confirmation(const char *proposal_id);
void device_ui_connection_changed(bool connected);
void device_ui_server_state_changed(void);
/** Called only under the board and LVGL display locks. No HTTP or hardware work. */
void device_ui_create(lv_obj_t *parent);
void device_ui_tick_locked(void);
#ifdef __cplusplus
}
#endif
