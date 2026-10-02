#include "device_ui.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include "cJSON.h"
#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "esp_timer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/idf_additions.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "bsp/m5stack_core_s3.h"
#include "driver/i2c_master.h"
#include "companion_hardware.h"
#include "body_hardware.h"
#include "device_endpoint.h"
#include "device_identity.h"
#include "device_transport.h"
#include "device_ui_font.h"
#include "device_ui_protocol.h"
#include "face_tracking.h"
#include "voice_control.h"

namespace {
enum Mode { CLOSED, MENU, CARD };
enum Action { REFRESH, FETCH_CARD, SHOWN, CONFIRM, CANCEL, INPUT_MODE, VOLUME,
              NIGHT, QUIET, WORKDAY, REST, ROLE, BRIGHTNESS, AUTO_BRIGHTNESS, CAMERA, STOP, STOP_SOUND,
              NAVIGATE, HOME_PAGE, ROLE_PAGE, RETRY };
enum Page { HOME, INPUT, SOUND, DISPLAY, SILENT, WORK, PARTNER, FOLLOW, STATUS };
struct Control { lv_obj_t *object; Action action; int value; char id[37]; };
Control s_controls[24]; unsigned s_control_count;
Page s_page = HOME;
int s_home_page, s_role_page;
uint32_t s_page_revision, s_drawn_page_revision, s_pending_actions;
char s_page_message[9][128];
struct RetryRequest { Action action; int value; char id[37]; bool available; };
RetryRequest s_retry[9];
lv_obj_t *s_menu_value, *s_menu_slider, *s_menu_description;
lv_obj_t *s_menu_retry;
lv_obj_t *s_home_captions[4];
Page operation_page(Action action)
{
    switch(action) {
        case INPUT_MODE:return INPUT; case VOLUME:return SOUND;
        case BRIGHTNESS:case AUTO_BRIGHTNESS:case NIGHT:return DISPLAY;
        case QUIET:return SILENT;case WORKDAY:case REST:return WORK;
        case ROLE:return PARTNER;case CAMERA:return FOLLOW;default:return STATUS;
    }
}
bool page_busy(Page page)
{
    for(unsigned a=INPUT_MODE;a<=STOP;a++)
        if((s_pending_actions & (1U<<a)) && operation_page(static_cast<Action>(a))==page) return true;
    return false;
}
bool setting_action(Action action) { return action>=INPUT_MODE && action<=STOP; }
struct Request { Action action; int value; uint32_t epoch; uint32_t state_generation; uint32_t connection_generation; char id[37]; };
SemaphoreHandle_t s_mutex;
QueueHandle_t s_queue;
enum FlashAction { LOAD_IDENTITY, SAVE_INPUT_MODE };
struct FlashRequest { FlashAction action; int value; device_identity_t *identity; };
QueueHandle_t s_flash_queue;
SemaphoreHandle_t s_flash_done;
esp_err_t s_flash_result;
device_ui_state_t *s_state;
device_ui_card_t *s_card;
bool s_ready, s_has_state, s_has_card, s_dirty, s_busy, s_shown;
bool s_card_readable;
bool s_connected;
int s_cached_brightness = -1;
int s_cached_battery = -1;
face_tracking_state_t s_cached_tracking;
int s_mode = CLOSED;
uint32_t s_epoch;
uint32_t s_state_generation;
uint32_t s_connection_generation;
int64_t s_state_updated_us;
int64_t s_expires_us;
char s_message[128];
lv_obj_t *s_overlay, *s_scroll, *s_confirm, *s_cancel, *s_status;
int s_drawn_mode = CLOSED;
uint32_t s_drawn_epoch;
i2c_master_dev_handle_t s_battery;
int64_t s_next_countdown_us;
int64_t s_card_visible_since_us;
unsigned s_shown_attempts;
int64_t s_next_shown_retry_us;

bool modal() { return __atomic_load_n(&s_mode, __ATOMIC_ACQUIRE) != CLOSED; }
bool online() { return __atomic_load_n(&s_connected, __ATOMIC_ACQUIRE); }
int read_battery()
{
    if (s_battery == nullptr) {
        i2c_device_config_t config = {};
        config.dev_addr_length = I2C_ADDR_BIT_LEN_7;
        config.device_address = 0x34;
        config.scl_speed_hz = 100000;
        i2c_master_bus_handle_t bus = nullptr;
        if (i2c_master_get_bus_handle(BSP_I2C_NUM, &bus) != ESP_OK ||
            i2c_master_bus_add_device(bus, &config, &s_battery) != ESP_OK) return -1;
    }
    // Read-only AXP2101 registers, as documented by M5Stack's power driver.
    // https://github.com/m5stack/M5Unified/blob/master/src/utility/power/AXP2101_Class.inl
    uint8_t present_register = 0, percent_register = 0xa4, present = 0, percent = 0;
    if (i2c_master_transmit_receive(s_battery, &present_register, 1, &present, 1, 25) != ESP_OK ||
        !(present & 0x08) || i2c_master_transmit_receive(s_battery, &percent_register, 1, &percent, 1, 25) != ESP_OK || percent > 100) return -1;
    return percent;
}
uint32_t epoch() { return __atomic_load_n(&s_epoch, __ATOMIC_ACQUIRE); }
void set_message(const char *message) { snprintf(s_message, sizeof(s_message), "%s", message); s_dirty = true; }
bool enqueue(Action action, int value = 0, const char *id = nullptr)
{
    if (s_queue == nullptr) return false;
    Request request = {action, value, epoch(), __atomic_load_n(&s_state_generation, __ATOMIC_ACQUIRE),
        __atomic_load_n(&s_connection_generation,__ATOMIC_ACQUIRE), {0}};
    if (id != nullptr) snprintf(request.id, sizeof(request.id), "%s", id);
    return xQueueSend(s_queue, &request, 0) == pdTRUE;
}

// Flash disables the PSRAM cache on this Quad configuration. NVS access must
// execute on an internal stack; the HTTP worker submits one synchronous request.
void flash_worker(void *)
{
    FlashRequest request = {};
    for (;;) {
        if (xQueueReceive(s_flash_queue, &request, portMAX_DELAY) != pdTRUE) continue;
        esp_err_t result = ESP_ERR_NO_MEM;
        if (request.action == SAVE_INPUT_MODE) {
            result = voice_control_set_input_mode(request.value != 0);
        } else {
            auto *identity = static_cast<device_identity_t *>(heap_caps_calloc(
                1, sizeof(device_identity_t), MALLOC_CAP_INTERNAL | MALLOC_CAP_8BIT));
            if (identity != nullptr) {
                result = device_identity_load(identity);
                if (result == ESP_OK && request.identity != nullptr)
                    memcpy(request.identity, identity, sizeof(*identity));
                memset(identity, 0, sizeof(*identity));
                free(identity);
            }
        }
        s_flash_result = result;
        xSemaphoreGive(s_flash_done);
    }
}
esp_err_t flash_call(FlashAction action, int value = 0, device_identity_t *identity = nullptr)
{
    if (s_flash_queue == nullptr || s_flash_done == nullptr) return ESP_ERR_INVALID_STATE;
    FlashRequest request = {action, value, identity};
    if (xQueueSend(s_flash_queue, &request, pdMS_TO_TICKS(100)) != pdTRUE) return ESP_ERR_INVALID_STATE;
    if (xSemaphoreTake(s_flash_done, portMAX_DELAY) != pdTRUE) return ESP_ERR_INVALID_STATE;
    return s_flash_result;
}

// Every network operation uses a fixed same-origin route and a bounded response.
// The authorization value is heap-owned and erased without ever entering a log.
bool http(const char *path, const char *body, char *response, int *status)
{
    *status = 0;
    if (!online()) return false;
    auto *identity = static_cast<device_identity_t *>(heap_caps_calloc(1, sizeof(device_identity_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    char *authorization = static_cast<char *>(heap_caps_calloc(1, DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    char url[DEVICE_IDENTITY_SERVER_BASE_URL_MAX_LEN + 128];
    bool okay = identity != nullptr && authorization != nullptr && flash_call(LOAD_IDENTITY, 0, identity) == ESP_OK &&
                device_endpoint_build_http_url(identity->server_base_url, path, url, sizeof(url));
    esp_http_client_handle_t client = nullptr;
    if (okay) {
        snprintf(authorization, DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16, "Bearer %s", identity->access_token);
        esp_http_client_config_t config = {};
        config.url = url;
        config.timeout_ms = 3000;
        config.disable_auto_redirect = true;
        config.buffer_size = 1024;
        config.buffer_size_tx = 1024;
        device_endpoint_configure_http_client(&config);
        client = esp_http_client_init(&config);
        okay = client != nullptr;
    }
    size_t used = 0;
    if (okay) {
        okay = esp_http_client_set_header(client, "Authorization", authorization) == ESP_OK &&
               esp_http_client_set_header(client, "Accept", "application/json") == ESP_OK;
        if (body != nullptr) {
            okay = okay && esp_http_client_set_method(client, HTTP_METHOD_POST) == ESP_OK &&
                esp_http_client_set_header(client, "Content-Type", "application/json") == ESP_OK;
        }
        size_t body_size = body == nullptr ? 0 : strlen(body);
        okay = okay && esp_http_client_open(client, body_size) == ESP_OK;
        if (okay && body_size != 0)
            okay = esp_http_client_write(client, body, body_size) == static_cast<int>(body_size);
        if (okay) {
            int64_t length = esp_http_client_fetch_headers(client);
            *status = esp_http_client_get_status_code(client);
            okay = length >= 0 && length <= DEVICE_UI_RESPONSE_MAX;
        }
        while (okay) {
            // Leave an extra byte to detect an oversized chunked body.
            int received = esp_http_client_read(client, response + used, DEVICE_UI_RESPONSE_MAX + 1 - used);
            if (received < 0) { okay = false; break; }
            if (received == 0) { okay = esp_http_client_is_complete_data_received(client); break; }
            used += received;
            if (used > DEVICE_UI_RESPONSE_MAX) { okay = false; break; }
        }
        response[used <= DEVICE_UI_RESPONSE_MAX ? used : 0] = '\0';
    }
    if (client != nullptr) { esp_http_client_close(client); esp_http_client_cleanup(client); }
    if (authorization != nullptr) { memset(authorization, 0, DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 16); free(authorization); }
    if (identity != nullptr) { memset(identity, 0, sizeof(*identity)); free(identity); }
    return okay && *status >= 200 && *status < 300;
}

bool pending_card() { return s_has_card && strcmp(s_card->status, "PENDING") == 0; }
void close_locked()
{
    __atomic_add_fetch(&s_epoch, 1U, __ATOMIC_ACQ_REL);
    __atomic_store_n(&s_mode, CLOSED, __ATOMIC_RELEASE);
    s_busy = false;
    s_dirty = true;
}

void clicked(lv_event_t *event)
{
    auto *control = static_cast<Control *>(lv_event_get_user_data(event));
    if(control == nullptr) return;
    Action action = control->action;
    int value = control->value;
    if (s_mutex == nullptr || xSemaphoreTake(s_mutex, 0) != pdTRUE) return;
    if(s_mode==MENU && (action==NAVIGATE || action==HOME_PAGE || action==ROLE_PAGE)) {
        if(action==NAVIGATE && value>=HOME && value<=STATUS) s_page=static_cast<Page>(value);
        else if(action==HOME_PAGE) s_home_page=value==0?0:1;
        else if(action==ROLE_PAGE) {s_role_page+=value;if(s_role_page<0)s_role_page=0;}
        s_page_revision++;s_dirty=true;xSemaphoreGive(s_mutex);return;
    }
    if(action==RETRY) {
        const auto &retry=s_retry[s_page];
        if(!retry.available){xSemaphoreGive(s_mutex);return;}
        action=retry.action;value=retry.value;
    }
    bool unresolved = s_mode == CARD && device_ui_uuid_valid(s_card->proposal_id) &&
        (pending_card() || (!s_has_card && s_card->status[0] == '\0'));
    if (action == CANCEL && !unresolved) { close_locked(); xSemaphoreGive(s_mutex); return; }
    if (action == STOP_SOUND) { xSemaphoreGive(s_mutex); companion_hardware_request_playback_stop(); return; }
    if ((s_mode==CARD?s_busy:page_busy(operation_page(action))) && action != STOP) { xSemaphoreGive(s_mutex); return; }
    bool network = action == CONFIRM || action == CANCEL || action == VOLUME || action == NIGHT ||
        action == QUIET || action == WORKDAY || action == REST || action == ROLE;
    if (network && !online()) { set_message("离线，暂时不能保存"); xSemaphoreGive(s_mutex); return; }
    if (action == CONFIRM && (!pending_card() || !s_shown || !s_card_readable || esp_timer_get_time() >= s_expires_us)) {
        set_message("内容已失效，请取消后重新说"); xSemaphoreGive(s_mutex); return;
    }
    const char *id = control->id[0] ? control->id : nullptr;
    if(control->action==RETRY && s_retry[s_page].id[0]) id=s_retry[s_page].id;
    if (action == CONFIRM || action == CANCEL) id = s_card->proposal_id;
    if (action == ROLE) {
        if (!device_ui_uuid_valid(id)) { xSemaphoreGive(s_mutex); return; }
    }
    if (enqueue(action, value, id)) {
        if(s_mode==CARD)s_busy=true;
        else {
            s_pending_actions|=1U<<action;
            Page page=operation_page(action);
            RetryRequest next_retry={action,value,{0},false};
            if(id)snprintf(next_retry.id,sizeof(next_retry.id),"%s",id);
            s_retry[page]=next_retry;
            snprintf(s_page_message[page],sizeof(s_page_message[page]),"正在处理…");
        }
        set_message("正在处理…");
    }
    else set_message("操作繁忙，请稍后重试");
    xSemaphoreGive(s_mutex);
    if (action == STOP) {
        // Immediate local stops must not wait for a slow settings request.
        companion_hardware_request_playback_stop();
        safety_state_stop_motion();
    }
}

lv_obj_t *label(lv_obj_t *parent, const char *text, int width = 280)
{
    lv_obj_t *object = lv_label_create(parent);
    lv_obj_set_width(object, width);
    lv_label_set_long_mode(object, LV_LABEL_LONG_WRAP);
    lv_label_set_text(object, text);
    lv_obj_set_style_text_font(object, s_mode==MENU?device_menu_font():device_ui_font(), 0);
    lv_obj_set_style_text_color(object, lv_color_hex(s_mode==MENU?0xeaf1fa:0xe6f7fb), 0);
    return object;
}

lv_obj_t *button(lv_obj_t *parent, const char *text, Action action, int value = 0, bool enabled = true, int width = 280)
{
    lv_obj_t *object = lv_button_create(parent);
    lv_obj_set_size(object, width, 44);
    lv_obj_set_style_radius(object, 10, 0);
    lv_obj_set_style_bg_color(object, lv_color_hex(action == CONFIRM ? 0x146c66 : 0x203545), 0);
    lv_obj_set_style_bg_color(object, lv_color_hex(0x18232b), LV_STATE_DISABLED);
    lv_obj_set_style_opa(object, LV_OPA_50, LV_STATE_DISABLED);
    lv_obj_set_style_pad_all(object, 6, 0);
    if(s_mode==MENU) {
        lv_obj_set_style_shadow_width(object,0,0);
        lv_obj_set_style_border_width(object,0,0);
        lv_obj_set_style_radius(object,14,0);
        lv_obj_set_style_bg_color(object,lv_color_hex(0x18212e),0);
        lv_obj_set_style_bg_color(object,lv_color_hex(0x30435a),LV_STATE_PRESSED);
    }
    auto *caption = label(object, text, width - 12);
    lv_obj_center(caption);
    if(s_control_count>=sizeof(s_controls)/sizeof(*s_controls)){lv_obj_add_state(object,LV_STATE_DISABLED);return object;}
    auto &control=s_controls[s_control_count++];control={object,action,value,{0}};
    if(action==ROLE && value>=0 && value<s_state->role_count)
        snprintf(control.id,sizeof(control.id),"%s",s_state->roles[value].id);
    lv_obj_add_event_cb(object, clicked, LV_EVENT_CLICKED, &control);
    bool navigation=action==NAVIGATE || action==HOME_PAGE || action==ROLE_PAGE || action==CANCEL;
    if (!enabled || (!navigation && action!=STOP && action!=STOP_SOUND &&
        (s_mode==CARD?s_busy:page_busy(operation_page(action))))) lv_obj_add_state(object, LV_STATE_DISABLED);
    return object;
}

#include "device_menu_view.inc"

void draw_card()
{
    if (!s_has_card) { label(s_scroll, "正在获取确认内容…"); return; }
    label(s_scroll, s_card->action_label);
    label(s_scroll, s_card->title);
    if (s_card->time_label[0]) label(s_scroll, s_card->time_label);
    if (s_card->content[0]) label(s_scroll, s_card->content);
    if (s_card->role_name[0]) { label(s_scroll, "伙伴"); label(s_scroll, s_card->role_name); }
    if (s_card->result_message[0]) label(s_scroll, s_card->result_message);
}

void render_locked()
{
    int mode = __atomic_load_n(&s_mode, __ATOMIC_ACQUIRE);
    if (mode == CLOSED) {
        if (s_overlay != nullptr) {
            lv_obj_add_flag(s_overlay, LV_OBJ_FLAG_HIDDEN);
            if (s_drawn_mode != CLOSED) {
                // Requests/results live independently of these widgets. Release
                // hidden menu/card allocations before speech needs the heap.
                lv_obj_clean(s_overlay);
                s_control_count = 0;
                s_scroll = s_confirm = s_cancel = s_status = nullptr;
                s_menu_value = s_menu_slider = s_menu_description = s_menu_retry = nullptr;
                memset(s_home_captions, 0, sizeof(s_home_captions));
            }
        }
        s_drawn_mode = CLOSED;
        s_dirty = false;
        return;
    }
    if (s_overlay == nullptr) return;
    lv_obj_remove_flag(s_overlay, LV_OBJ_FLAG_HIDDEN);
    lv_obj_move_foreground(s_overlay);
    if(mode==MENU && s_drawn_mode==MENU && s_drawn_epoch==epoch() &&
        s_drawn_page_revision==s_page_revision) {
        if(s_dirty)refresh_menu();
        s_dirty=false;return;
    }
    if (!s_dirty && s_drawn_mode == mode && s_drawn_epoch == epoch()) return;
    lv_obj_clean(s_overlay);
    lv_obj_set_style_bg_color(s_overlay,lv_color_hex(mode==MENU?0x000000:0x07131d),0);
    s_control_count=0;
    s_confirm = s_cancel = s_status = nullptr;
    auto *heading = label(s_overlay, mode == MENU ? page_title(s_page) : "确认操作", mode == MENU ? 166 : 170);
    lv_obj_set_pos(heading, mode==MENU?80:14, 8);
    if(mode==MENU) {
        lv_obj_set_style_text_align(heading,LV_TEXT_ALIGN_CENTER,0);
        if(s_page==HOME) {
            auto *mark=lv_obj_create(s_overlay);lv_obj_remove_style_all(mark);
            lv_obj_set_size(mark,48,40);lv_obj_set_pos(mark,4,0);
            lv_obj_remove_flag(mark,LV_OBJ_FLAG_CLICKABLE);
            lv_obj_remove_flag(mark,LV_OBJ_FLAG_SCROLLABLE);
            lv_obj_add_event_cb(mark,menu_header_draw,LV_EVENT_DRAW_MAIN,nullptr);
        }
    }
    if (mode == CARD) {
        auto *stop_sound = button(s_overlay, "停声", STOP_SOUND, 0, true, 58);
        lv_obj_set_size(stop_sound, 58, 36); lv_obj_set_pos(stop_sound, 190, 0);
    }
    auto *close = button(s_overlay, mode==MENU?"停止":"关闭", mode==MENU?STOP:CANCEL, 0, true, 58);
    lv_obj_set_size(close, 58, 36);
    lv_obj_set_pos(close, 254, 0);
    if(mode==MENU) {
        lv_obj_set_size(close,54,32);lv_obj_set_pos(close,258,3);
        lv_obj_set_style_bg_color(close,lv_color_hex(0x18212e),0);
    }
    // A pending proposal always requires explicit cancellation, including the X.
    s_cancel = close;
    if(mode==MENU && s_page!=HOME) {
        auto *back=button(s_overlay,"",NAVIGATE,HOME,true,50);
        lv_obj_set_size(back,50,40);lv_obj_set_pos(back,4,0);
        lv_obj_set_style_bg_color(back,lv_color_black(),0);
        lv_obj_add_event_cb(back,menu_header_draw,LV_EVENT_DRAW_MAIN,reinterpret_cast<void *>(1));
    }
    s_scroll = lv_obj_create(s_overlay);
    lv_obj_set_pos(s_scroll, 0, mode==CARD?36:44);
    lv_obj_set_size(s_scroll, 320, mode == CARD ? 134 : 164);
    if(mode==MENU && s_page==HOME) {lv_obj_set_pos(s_scroll,0,35);lv_obj_set_height(s_scroll,178);}
    lv_obj_set_flex_flow(s_scroll, LV_FLEX_FLOW_COLUMN);
    lv_obj_set_style_pad_all(s_scroll, 14, 0);
    lv_obj_set_style_pad_row(s_scroll, 9, 0);
    lv_obj_set_style_border_width(s_scroll, 0, 0);
    lv_obj_set_style_bg_color(s_scroll, lv_color_hex(mode==MENU?0x000000:0x07131d), 0);
    lv_obj_set_scrollbar_mode(s_scroll, LV_SCROLLBAR_MODE_AUTO);
    if (mode == MENU) draw_menu(); else draw_card();
    if (mode == CARD) {
        s_cancel = button(s_overlay, pending_card() ? "取消" : "关闭", CANCEL, 0, !s_busy, 142);
        lv_obj_set_pos(s_cancel, 12, 194);
        s_confirm = button(s_overlay, "确认", CONFIRM, 0, online() && pending_card() && !s_busy && s_shown && s_card_readable && esp_timer_get_time() < s_expires_us, 142);
        lv_obj_set_pos(s_confirm, 166, 194);
    }
    s_status = label(s_overlay, s_message, 292);
    lv_obj_set_pos(s_status, 14, mode == CARD ? 172 : 212);
    if(mode==MENU) {
        lv_obj_set_width(s_status,228);lv_label_set_long_mode(s_status,LV_LABEL_LONG_DOT);
        s_menu_retry=button(s_overlay,"重试",RETRY,0,true,62);
        lv_obj_set_size(s_menu_retry,62,28);lv_obj_set_pos(s_menu_retry,250,210);
        lv_obj_set_style_pad_all(s_menu_retry,0,0);
        if(s_page==HOME) {
            lv_obj_add_flag(s_status,LV_OBJ_FLAG_HIDDEN);
            for(int dot=0;dot<2;dot++) {
                auto *object=lv_obj_create(s_overlay);lv_obj_remove_style_all(object);
                lv_obj_set_size(object,6,6);lv_obj_set_pos(object,149+dot*16,226);
                lv_obj_set_style_radius(object,LV_RADIUS_CIRCLE,0);
                lv_obj_set_style_bg_opa(object,LV_OPA_COVER,0);
                lv_obj_set_style_bg_color(object,lv_color_hex(dot==s_home_page?0xeaf1fa:0x354252),0);
                lv_obj_remove_flag(object,LV_OBJ_FLAG_CLICKABLE);
                lv_obj_remove_flag(object,LV_OBJ_FLAG_SCROLLABLE);
            }
        }
    }
    s_drawn_mode = mode;
    s_drawn_epoch = epoch();
    s_drawn_page_revision=s_page_revision;
    s_dirty = false;
    if(mode==MENU)refresh_menu();
    if (mode == CARD && pending_card() && s_card_visible_since_us == 0)
        s_card_visible_since_us = esp_timer_get_time();
}

void service_tracking()
{
    bool audio = false, updating = false, error = false;
    companion_hardware_get_motion_guard(&audio, &updating, &error);
    bool idle = device_ui_local_interaction_allowed() && !updating && !error && companion_hardware_is_idle() && !voice_control_motion_blocked();
    safety_diagnostics_t safety = {};
    safety_state_get_diagnostics(&safety);
    bool head = idle && safety.state == SAFETY_STATE_MOTION_ARMED && safety.motion_runtime == SAFETY_MOTION_IDLE;
    face_tracking_set_runtime_gate(idle, head);
    face_tracking_state_t tracking = {};
    face_tracking_get_state(&tracking);
    if (!idle || !tracking.enabled) body_hardware_stop_local_follow();
    int brightness = companion_hardware_get_local_brightness();
    if (xSemaphoreTake(s_mutex, 0) == pdTRUE) {
        if (s_cached_brightness != brightness || s_cached_tracking.enabled != tracking.enabled ||
            s_cached_tracking.available != tracking.available || s_cached_tracking.failure != tracking.failure) {
            s_cached_brightness = brightness; s_cached_tracking = tracking;
            if (s_mode == MENU) s_dirty = true;
        }
        xSemaphoreGive(s_mutex);
    }
    companion_hardware_set_local_gaze(tracking.gaze_x, tracking.gaze_y, idle && tracking.running);
    face_tracking_head_goal_t goal = {};
    if (head && face_tracking_take_head_goal(&goal))
        (void)body_hardware_follow_local(goal.yaw_offset_deg, goal.pitch_offset_deg, goal.recenter);
}

void worker(void *)
{
    auto *response = static_cast<char *>(heap_caps_malloc(DEVICE_UI_RESPONSE_MAX + 2, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    auto *next_state = static_cast<device_ui_state_t *>(heap_caps_malloc(sizeof(device_ui_state_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    auto *next_card = static_cast<device_ui_card_t *>(heap_caps_malloc(sizeof(device_ui_card_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    if (response == nullptr || next_state == nullptr || next_card == nullptr) {
        free(response); free(next_state); free(next_card);
        __atomic_store_n(&s_ready, false, __ATOMIC_RELEASE);
        vTaskDelete(nullptr); return;
    }
    Request request = {};
    int64_t next_refresh_us = 0;
    for (;;) {
        if (xQueueReceive(s_queue, &request, pdMS_TO_TICKS(100)) != pdTRUE) {
            if (online() && esp_timer_get_time() >= next_refresh_us) {
                (void)enqueue(REFRESH); next_refresh_us = esp_timer_get_time() + 15000000LL;
            }
            continue;
        }
        bool setting=setting_action(request.action);
        if((request.epoch!=epoch() && !setting) ||
            (request.connection_generation!=__atomic_load_n(&s_connection_generation,__ATOMIC_ACQUIRE) && request.action!=STOP))continue;
        if(request.action!=REFRESH && request.action!=FETCH_CARD && request.action!=SHOWN)
            ESP_LOGI("device_ui","Device UI operation started: code=%d stack_free=%u internal_free=%u internal_largest=%u",
                static_cast<int>(request.action),static_cast<unsigned>(uxTaskGetStackHighWaterMark(nullptr)),
                static_cast<unsigned>(heap_caps_get_free_size(MALLOC_CAP_INTERNAL)),
                static_cast<unsigned>(heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL)));
        int battery = request.action == REFRESH ? read_battery() : -2;
        if (request.action == STOP) {
            voice_control_cancel_active_turn(); safety_state_stop_motion();
        }
        bool local = request.action == STOP || request.action == INPUT_MODE || request.action == BRIGHTNESS ||
            request.action == AUTO_BRIGHTNESS || request.action == CAMERA;
        bool success = true, state_valid = false, card_valid = false;
        int status = 0;
        char path[128] = "/api/v1/device/ui/state", body[96] = {0};
        if (local) {
            if (request.action == INPUT_MODE) success = flash_call(SAVE_INPUT_MODE, request.value) == ESP_OK;
            else if (request.action == BRIGHTNESS) success = companion_hardware_set_local_brightness(request.value) == ESP_OK;
            else if (request.action == AUTO_BRIGHTNESS) success = companion_hardware_clear_local_brightness() == ESP_OK;
            else if (request.action == CAMERA) success = face_tracking_set_enabled(request.value != 0);
        } else {
            if (request.action == FETCH_CARD || request.action == CONFIRM || request.action == CANCEL || request.action == SHOWN) {
                snprintf(path, sizeof(path), "/api/v1/device/ui/confirmations/%s%s", request.id, request.action == SHOWN ? "/shown" : "");
                if (request.action == CONFIRM || request.action == CANCEL)
                    snprintf(body, sizeof(body), "{\"action\":\"%s\"}", request.action == CONFIRM ? "CONFIRM" : "CANCEL");
                // The shown endpoint requires an empty POST, not a JSON object.
            } else if (request.action != REFRESH) {
                snprintf(path, sizeof(path), "/api/v1/device/ui/settings");
                if (request.action == VOLUME) snprintf(body, sizeof(body), "{\"volume_percent\":%d}", request.value);
                else if (request.action == NIGHT) snprintf(body, sizeof(body), "{\"night_mode\":%s}", request.value ? "true" : "false");
                else if (request.action == QUIET) snprintf(body, sizeof(body), "{\"quiet_today\":%s}", request.value ? "true" : "false");
                else if (request.action == WORKDAY) snprintf(body, sizeof(body), "{\"workday_action\":\"%s\"}", request.value ? "START" : "STOP");
                else if (request.action == REST) snprintf(body, sizeof(body), "{\"rest_action\":\"%s\"}", request.value == 0 ? "START_REST" : request.value == 1 ? "SNOOZE" : "SKIP_FOR_DAY");
                else if (request.action == ROLE) snprintf(body, sizeof(body), "{\"role_id\":\"%s\"}", request.id);
            }
            response[0] = '\0';
            success = http(path, request.action == REFRESH || request.action == FETCH_CARD ? nullptr : body, response, &status);
            if (success && request.action == SHOWN)
                success = device_ui_parse_shown_receipt(response, strlen(response), request.id);
            else if (success && (request.action == FETCH_CARD || request.action == CONFIRM || request.action == CANCEL))
                success = card_valid = device_ui_parse_card(response, strlen(response), request.id, next_card);
            else if (success) success = state_valid = device_ui_parse_state(response, strlen(response), next_state);
        }
        if((request.epoch!=epoch() && !setting) ||
            (request.connection_generation!=__atomic_load_n(&s_connection_generation,__ATOMIC_ACQUIRE) && request.action!=STOP))continue;
        xSemaphoreTake(s_mutex, portMAX_DELAY);
        char prior_message[sizeof(s_message)];snprintf(prior_message,sizeof(prior_message),"%s",s_message);
        bool card_invalidated=false;
        bool card_request = request.action == FETCH_CARD || request.action == CONFIRM || request.action == CANCEL || request.action == SHOWN;
        if ((request.epoch == epoch() || setting) &&
            (request.connection_generation==__atomic_load_n(&s_connection_generation,__ATOMIC_ACQUIRE) || request.action==STOP) &&
            (!card_request || (s_mode == CARD && strcmp(s_card->proposal_id, request.id) == 0))) {
            if (!setting && request.action != REFRESH && request.action != FETCH_CARD) s_busy = false;
            s_pending_actions &= ~(1U<<request.action);
            if (battery != -2) s_cached_battery = battery;
            if (success && state_valid && request.state_generation == __atomic_load_n(&s_state_generation, __ATOMIC_ACQUIRE)) {
                if(s_mode==MENU && s_page==PARTNER &&
                    (s_state->role_count!=next_state->role_count ||
                     memcmp(s_state->roles,next_state->roles,next_state->role_count*sizeof(next_state->roles[0]))!=0))
                    s_page_revision++;
                *s_state = *next_state; s_has_state = true; s_state_updated_us = esp_timer_get_time();
                next_refresh_us = s_state_updated_us + 15000000LL;
                if (s_mode == CARD && (pending_card() || !s_has_card) && strcmp(s_card->proposal_id, s_state->pending_confirmation_id) != 0) {
                    snprintf(s_card->status, sizeof(s_card->status), "EXPIRED");
                    card_invalidated=true;
                    set_message("内容已失效，请关闭后重新说");
                }
            } else if (success && state_valid) (void)enqueue(REFRESH);
            if (success && card_valid) {
                *s_card = *next_card; s_has_card = true;
                s_card_readable = device_ui_font_covers_text(s_card->action_label) && device_ui_font_covers_text(s_card->title) &&
                    device_ui_font_covers_text(s_card->content) && device_ui_font_covers_text(s_card->time_label) && device_ui_font_covers_text(s_card->role_name);
                s_expires_us = esp_timer_get_time() + static_cast<int64_t>(s_card->expires_in_seconds) * 1000000LL;
            }
            if (success && request.action == SHOWN) s_shown = true;
            if (!success) {
                if (status == 404 || status == 409) {
                    if (card_request)
                        snprintf(s_card->status, sizeof(s_card->status), "EXPIRED");
                    set_message("内容已失效，请关闭后重新说");
                } else set_message(local ? "操作失败，请重试" : "未保存，请检查连接后重试");
                if (request.action == SHOWN && s_shown_attempts >= 3) set_message("显示回执失败，请取消后重试");
            } else if (request.action == SHOWN || request.action == FETCH_CARD) set_message("请核对内容，再确认或取消");
            else if (local) set_message(request.action == INPUT_MODE ? "已保存" : request.action == CAMERA ? "已应用，重启后相机关闭" : request.action == STOP ? "已停止" : "已应用");
            else set_message(request.action==REFRESH?"请选择设置":state_valid ? "已保存" : s_card->result_message);
            if(!card_request && request.action!=REFRESH) {
                Page page=operation_page(request.action);
                snprintf(s_page_message[page],sizeof(s_page_message[page]),"%s",s_message);
                s_retry[page].available=!success;
                ESP_LOGI("device_ui","Device UI operation finished: code=%d result=%s stack_free=%u",
                    static_cast<int>(request.action),success?"ok":"failed",
                    static_cast<unsigned>(uxTaskGetStackHighWaterMark(nullptr)));
            }
            if (s_has_card && !s_card_readable) set_message("有字符无法显示，请取消后重新说");
            // A menu receipt may finish after leaving the menu. Preserve a live
            // card's own status, except when refreshed facts really invalidate it.
            if(setting && s_mode==CARD && !card_invalidated)
                snprintf(s_message,sizeof(s_message),"%s",prior_message);
            s_dirty = true;
        }
        // A state refresh can recover an offer missed while the socket connected.
        // Enqueue under the same epoch/lock so an older HTTP response cannot replace a newer card.
        if (request.epoch == epoch() && success && state_valid && request.state_generation == __atomic_load_n(&s_state_generation, __ATOMIC_ACQUIRE) && next_state->pending_confirmation_id[0] != '\0' && s_mode == CLOSED) {
            __atomic_add_fetch(&s_epoch, 1U, __ATOMIC_ACQ_REL);
            __atomic_store_n(&s_mode, CARD, __ATOMIC_RELEASE);
            memset(s_card, 0, sizeof(*s_card));
            snprintf(s_card->proposal_id, sizeof(s_card->proposal_id), "%s", next_state->pending_confirmation_id);
            s_has_card = s_shown = s_busy = s_card_readable = false;
            s_card_visible_since_us = 0; s_shown_attempts = 0; s_next_shown_retry_us = 0;
            set_message("正在获取内容…");
            if (!enqueue(FETCH_CARD, 0, s_card->proposal_id)) set_message("获取失败，请关闭后重试");
        }
        xSemaphoreGive(s_mutex);
    }
}

void tracking_gate_worker(void *)
{
    for (;;) { service_tracking(); vTaskDelay(pdMS_TO_TICKS(100)); }
}
} // namespace

extern "C" esp_err_t device_ui_init(void)
{
    if (device_ui_ready()) return ESP_OK;
    s_mutex = xSemaphoreCreateMutex();
    s_queue = xQueueCreate(16, sizeof(Request));
    s_flash_queue = xQueueCreate(1, sizeof(FlashRequest));
    s_flash_done = xSemaphoreCreateBinary();
    s_state = static_cast<device_ui_state_t *>(heap_caps_calloc(1, sizeof(device_ui_state_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    s_card = static_cast<device_ui_card_t *>(heap_caps_calloc(1, sizeof(device_ui_card_t), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    if (!s_mutex || !s_queue || !s_flash_queue || !s_flash_done || !s_state || !s_card) return ESP_ERR_NO_MEM;
    if (xTaskCreate(flash_worker, "ui_flash", 8192, nullptr, 2, nullptr) != pdPASS) return ESP_ERR_NO_MEM;
    (void)device_ui_font();
    (void)device_menu_font();
    (void)device_number_font();
    __atomic_store_n(&s_ready, true, __ATOMIC_RELEASE);
    if (xTaskCreatePinnedToCoreWithCaps(worker, "device_ui", 24576, nullptr, 2, nullptr, 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT) != pdPASS) {
        __atomic_store_n(&s_ready, false, __ATOMIC_RELEASE); return ESP_ERR_NO_MEM;
    }
    if (xTaskCreatePinnedToCoreWithCaps(tracking_gate_worker, "local_ui_gate", 6144, nullptr, 2, nullptr, 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT) != pdPASS) {
        // Menu/cards remain available; optional tracking stays closed.
        face_tracking_set_runtime_gate(false, false);
    }
    return ESP_OK;
}

extern "C" bool device_ui_ready(void) { return __atomic_load_n(&s_ready, __ATOMIC_ACQUIRE); }
extern "C" bool device_ui_is_modal(void) { return modal(); }
extern "C" void device_ui_open_menu(void)
{
    if (!device_ui_ready()) return;
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    if (s_mode != CLOSED) { xSemaphoreGive(s_mutex); return; }
    __atomic_add_fetch(&s_epoch, 1U, __ATOMIC_ACQ_REL);
    __atomic_store_n(&s_mode, MENU, __ATOMIC_RELEASE);
    face_tracking_set_runtime_gate(false, false);
    s_busy = false; s_dirty = true;
    s_page=HOME;s_home_page=s_role_page=0;s_page_revision++;
    set_message(online() ? "同步设置中…" : "离线，可使用本地设置");
    (void)enqueue(REFRESH);
    xSemaphoreGive(s_mutex);
    companion_hardware_mark_activity();
}

extern "C" void device_ui_notify_confirmation(const char *id)
{
    if (!device_ui_ready() || !device_ui_uuid_valid(id)) return;
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    if (s_mode == CARD && strcmp(s_card->proposal_id, id) == 0) { xSemaphoreGive(s_mutex); return; }
    __atomic_add_fetch(&s_epoch, 1U, __ATOMIC_ACQ_REL);
    __atomic_store_n(&s_mode, CARD, __ATOMIC_RELEASE);
    face_tracking_set_runtime_gate(false, false);
    memset(s_card, 0, sizeof(*s_card)); snprintf(s_card->proposal_id, sizeof(s_card->proposal_id), "%s", id);
    s_has_card = s_shown = s_busy = s_card_readable = false;
    s_card_visible_since_us = 0;
    s_shown_attempts = 0;
    s_next_shown_retry_us = 0;
    set_message("正在获取内容…");
    if (!enqueue(FETCH_CARD, 0, id)) {
        set_message("获取失败，请关闭后重试");
    }
    xSemaphoreGive(s_mutex);
    companion_hardware_mark_activity();
}

extern "C" void device_ui_connection_changed(bool connected)
{
    __atomic_store_n(&s_connected, connected, __ATOMIC_RELEASE);
    if (!device_ui_ready()) return;
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    __atomic_add_fetch(&s_epoch, 1U, __ATOMIC_ACQ_REL);
    __atomic_add_fetch(&s_connection_generation,1U,__ATOMIC_ACQ_REL);
    s_busy = false; s_shown = false; s_has_state = false;
    uint32_t interrupted=s_pending_actions;
    s_pending_actions = 0;
    for(unsigned page=0;page<9;page++) {
        if(interrupted & (1U<<s_retry[page].action)) {
            snprintf(s_page_message[page],sizeof(s_page_message[page]),"连接中断，请重试");
            s_retry[page].available=true;
        }
    }
    __atomic_add_fetch(&s_state_generation, 1U, __ATOMIC_ACQ_REL);
    if (s_mode == CARD) snprintf(s_card->status, sizeof(s_card->status), "EXPIRED");
    set_message(connected ? "重新同步中…" : "已断开连接");
    if (connected) (void)enqueue(REFRESH);
    xSemaphoreGive(s_mutex);
    if (!connected) { face_tracking_set_runtime_gate(false, false); companion_hardware_expression_clear_turn(); }
}

extern "C" bool device_ui_local_interaction_allowed(void)
{
    if (!device_ui_ready() || !online() || modal() || xSemaphoreTake(s_mutex, 0) != pdTRUE) return false;
    bool allowed = s_has_state && !s_state->quiet_today && esp_timer_get_time() - s_state_updated_us < 20000000LL;
    xSemaphoreGive(s_mutex);
    return allowed;
}

extern "C" void device_ui_server_state_changed(void)
{
    if (!device_ui_ready()) return;
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    __atomic_add_fetch(&s_state_generation, 1U, __ATOMIC_ACQ_REL);
    s_has_state = false;
    s_dirty = true;
    (void)enqueue(REFRESH);
    xSemaphoreGive(s_mutex);
    face_tracking_set_runtime_gate(false, false);
    body_hardware_stop_local_follow();
}

extern "C" void device_ui_create(lv_obj_t *parent)
{
    s_overlay = lv_obj_create(parent);
    lv_obj_set_size(s_overlay, 320, 240);
    lv_obj_set_pos(s_overlay, 0, 0);
    lv_obj_remove_flag(s_overlay, LV_OBJ_FLAG_SCROLLABLE);
    // Child gestures must stop at this modal, rather than skipping to the screen.
    lv_obj_remove_flag(s_overlay, LV_OBJ_FLAG_GESTURE_BUBBLE);
    lv_obj_set_style_pad_all(s_overlay, 0, 0);
    lv_obj_set_style_border_width(s_overlay, 0, 0);
    lv_obj_set_style_radius(s_overlay, 0, 0);
    lv_obj_set_style_bg_color(s_overlay, lv_color_hex(0x07131d), 0);
    lv_obj_add_flag(s_overlay, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(s_overlay,menu_gesture,LV_EVENT_GESTURE,nullptr);
}

extern "C" void device_ui_tick_locked(void)
{
    if (!device_ui_ready() || xSemaphoreTake(s_mutex, 0) != pdTRUE) return;
    bool rebuilding=s_mode!=CLOSED && (s_drawn_mode!=s_mode || s_drawn_epoch!=epoch() ||
        (s_mode==MENU && s_drawn_page_revision!=s_page_revision));
    int64_t started=esp_timer_get_time();
    if (s_mode == CARD && pending_card() && s_has_card && esp_timer_get_time() >= s_expires_us) {
        snprintf(s_card->status, sizeof(s_card->status), "EXPIRED");
        set_message("内容已过期，请关闭后重新说");
    }
    render_locked();
    if(rebuilding) {
        ESP_LOGI("device_ui","Device UI render diagnostics: phase=%d page=%d build_us=%u stack_free=%u internal_largest=%u",
            s_mode,static_cast<int>(s_page),static_cast<unsigned>(esp_timer_get_time()-started),
            static_cast<unsigned>(uxTaskGetStackHighWaterMark(nullptr)),
            static_cast<unsigned>(heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL)));
    }
    if (s_mode == CARD && pending_card() && s_card_readable && !s_shown && !s_busy && online() && s_shown_attempts < 3 &&
        s_card_visible_since_us != 0 && esp_timer_get_time() - s_card_visible_since_us >= 75000 && esp_timer_get_time() >= s_next_shown_retry_us) {
        // A later UI tick leaves at least one complete LVGL frame for LCD transfer.
        s_busy = enqueue(SHOWN, 0, s_card->proposal_id);
        if (s_busy) { s_shown_attempts++; s_next_shown_retry_us = esp_timer_get_time() + static_cast<int64_t>(s_shown_attempts) * 1000000LL; s_dirty = true; }
    }
    if (s_mode == CARD && pending_card() && s_status != nullptr && esp_timer_get_time() >= s_next_countdown_us && !s_busy && s_shown && s_card_readable) {
        char countdown[96];
        snprintf(countdown, sizeof(countdown), "请核对内容 · 剩余 %lld 秒", static_cast<long long>((s_expires_us - esp_timer_get_time() + 999999) / 1000000));
        lv_label_set_text(s_status, countdown);
        s_next_countdown_us = esp_timer_get_time() + 1000000;
    }
    xSemaphoreGive(s_mutex);
}

#ifdef STACKCHAN_DEVICE_UI_HOST_TEST
extern "C" void device_ui_host_render(lv_obj_t *parent, const device_ui_state_t *state,
    const device_ui_card_t *card, bool connected, bool busy, bool shown, int page, int home_page)
{
    static device_ui_state_t test_state;
    static device_ui_card_t test_card;
    s_state = &test_state; s_card = &test_card;
    if (state != nullptr) test_state = *state;
    if (card != nullptr) test_card = *card;
    s_has_state = state != nullptr; s_has_card = card != nullptr;
    s_connected = connected; s_busy = busy; s_shown = shown; s_card_readable = true;
    s_mode = card != nullptr ? CARD : MENU;
    s_page = static_cast<Page>(page); s_home_page = home_page; s_role_page = 0;
    s_pending_actions = busy && card==nullptr ? 1U<<VOLUME : 0;
    memset(s_page_message,0,sizeof(s_page_message));memset(s_retry,0,sizeof(s_retry));
    s_cached_brightness = 55;
    s_cached_tracking.available = true;
    s_expires_us = esp_timer_get_time() + 60000000LL;
    s_dirty = true; snprintf(s_message, sizeof(s_message), "%s", busy ? "正在处理…" : connected ? card ? "请核对内容" : "请选择设置" : "离线，暂时不能保存");
    s_drawn_mode = CLOSED;
    s_overlay = nullptr;
    device_ui_create(parent);
    render_locked();
}
#endif
