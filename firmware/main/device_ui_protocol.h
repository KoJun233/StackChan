#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define DEVICE_UI_RESPONSE_MAX 8192U
#define DEVICE_UI_ROLES_MAX 32U

typedef struct { char id[37]; char name[385]; } device_ui_role_t;
typedef struct {
    int volume_percent;
    bool night_mode;
    bool quiet_today;
    bool rest_pending;
    char workday_state[24];
    device_ui_role_t role;
    device_ui_role_t roles[DEVICE_UI_ROLES_MAX];
    uint8_t role_count;
    bool roles_truncated;
    char pending_confirmation_id[37];
} device_ui_state_t;

typedef struct {
    char proposal_id[37];
    char action_label[193];
    char title[601];
    char content[6001];
    char time_label[385];
    char role_name[385];
    char status[16];
    char result_message[385];
    uint32_t expires_in_seconds;
} device_ui_card_t;

#ifdef __cplusplus
extern "C" {
#endif
bool device_ui_uuid_valid(const char *text);
bool device_ui_parse_state(const char *json, size_t length, device_ui_state_t *state);
bool device_ui_parse_card(const char *json, size_t length, const char *expected_id,
                          device_ui_card_t *card);
bool device_ui_parse_confirmation_notice(const char *json, size_t length, char id[37]);
bool device_ui_parse_shown_receipt(const char *json, size_t length, const char *expected_id);
bool device_ui_parse_state_notice(const char *json, size_t length);
#ifdef __cplusplus
}
#endif
