#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "device_ui_protocol.h"

static const char *const ID = "01234567-89ab-4cde-8fab-0123456789ab";
static const char *const OTHER_ID = "fedcba98-7654-4321-8abc-fedcba987654";
static unsigned checks;
#define CHECK(condition) do { checks++; if (!(condition)) { \
    fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); exit(1); } } while (0)

static cJSON *make_role(const char *id, const char *name)
{
    cJSON *role = cJSON_CreateObject();
    CHECK(role != NULL);
    cJSON_AddStringToObject(role, "id", id);
    cJSON_AddStringToObject(role, "name", name);
    return role;
}

static cJSON *make_state(void)
{
    cJSON *state = cJSON_CreateObject();
    CHECK(state != NULL);
    cJSON_AddNumberToObject(state, "version", 1);
    cJSON_AddNumberToObject(state, "volume_percent", 65);
    cJSON_AddBoolToObject(state, "night_mode", true);
    cJSON_AddBoolToObject(state, "quiet_today", false);
    cJSON_AddBoolToObject(state, "rest_pending", true);
    cJSON_AddStringToObject(state, "workday_state", "REST_PROMPTED");
    cJSON_AddItemToObject(state, "role", make_role(ID, "小伙伴"));
    cJSON *roles = cJSON_AddArrayToObject(state, "roles");
    cJSON_AddItemToArray(roles, make_role(ID, "同名伙伴"));
    cJSON_AddItemToArray(roles, make_role(OTHER_ID, "同名伙伴"));
    cJSON_AddBoolToObject(state, "roles_truncated", false);
    cJSON_AddNullToObject(state, "pending_confirmation_id");
    return state;
}

static cJSON *make_card(void)
{
    cJSON *card = cJSON_CreateObject();
    CHECK(card != NULL);
    cJSON_AddStringToObject(card, "proposal_id", ID);
    cJSON_AddStringToObject(card, "action_label", "新增待办");
    cJSON_AddStringToObject(card, "title", "新增待办");
    cJSON_AddStringToObject(card, "content", "整理会议材料");
    cJSON_AddStringToObject(card, "time_label", "2026-10-03 15:00 Asia/Shanghai");
    cJSON_AddStringToObject(card, "role_name", "小伙伴");
    cJSON_AddStringToObject(card, "expires_at", "2026-10-02T12:00:00Z");
    cJSON_AddNumberToObject(card, "expires_in_seconds", 120);
    cJSON_AddStringToObject(card, "status", "PENDING");
    cJSON_AddStringToObject(card, "result_message", "");
    return card;
}

static bool parse_state(cJSON *input, device_ui_state_t *state)
{
    char *json = cJSON_PrintUnformatted(input);
    CHECK(json != NULL);
    bool valid = device_ui_parse_state(json, strlen(json), state);
    free(json);
    return valid;
}

static bool parse_card(cJSON *input, const char *expected_id, device_ui_card_t *card)
{
    char *json = cJSON_PrintUnformatted(input);
    CHECK(json != NULL);
    bool valid = device_ui_parse_card(json, strlen(json), expected_id, card);
    free(json);
    return valid;
}

static void state_and_saved_response(void)
{
    device_ui_state_t state;
    cJSON *input = make_state();
    CHECK(parse_state(input, &state));
    CHECK(state.volume_percent == 65 && state.night_mode && !state.quiet_today && state.rest_pending);
    CHECK(state.role_count == 2 && !state.roles_truncated);
    CHECK(strcmp(state.role.id, ID) == 0 && strcmp(state.role.name, "小伙伴") == 0);
    CHECK(strcmp(state.roles[0].id, state.roles[1].id) != 0);
    CHECK(strcmp(state.roles[0].name, state.roles[1].name) == 0);
    CHECK(state.pending_confirmation_id[0] == '\0');
    cJSON_ReplaceItemInObjectCaseSensitive(input, "pending_confirmation_id", cJSON_CreateString(ID));
    CHECK(parse_state(input, &state) && strcmp(state.pending_confirmation_id, ID) == 0);
    cJSON *wrapper = cJSON_CreateObject();
    cJSON_AddStringToObject(wrapper, "result", "SAVED");
    cJSON_AddItemToObject(wrapper, "state", input);
    CHECK(parse_state(wrapper, &state) && state.volume_percent == 65);
    cJSON_ReplaceItemInObjectCaseSensitive(wrapper, "result", cJSON_CreateString("FAILED"));
    CHECK(!parse_state(wrapper, &state));
    cJSON_DeleteItemFromObjectCaseSensitive(wrapper, "result");
    CHECK(!parse_state(wrapper, &state));
    cJSON_Delete(wrapper);
    puts("PASS state: direct and SAVED responses; same-name roles retain distinct IDs");
}

static void state_bounds_and_role_identity(void)
{
    device_ui_state_t state;
    cJSON *input = make_state();
    cJSON *roles = cJSON_GetObjectItemCaseSensitive(input, "roles");
    cJSON_ReplaceItemInArray(roles, 1, make_role(ID, "重复 ID"));
    CHECK(!parse_state(input, &state));
    cJSON_DeleteItemFromObjectCaseSensitive(input, "roles");
    roles = cJSON_AddArrayToObject(input, "roles");
    for (unsigned i = 0; i < DEVICE_UI_ROLES_MAX; i++) {
        char id[37];
        snprintf(id, sizeof(id), "00000000-0000-4000-8000-%012u", i);
        cJSON_AddItemToArray(roles, make_role(id, "伙伴"));
    }
    cJSON_ReplaceItemInObjectCaseSensitive(input, "roles_truncated", cJSON_CreateBool(true));
    CHECK(parse_state(input, &state) && state.role_count == DEVICE_UI_ROLES_MAX && state.roles_truncated);
    cJSON_AddItemToArray(roles, make_role(OTHER_ID, "超额伙伴"));
    CHECK(!parse_state(input, &state));
    cJSON_DeleteItemFromArray(roles, DEVICE_UI_ROLES_MAX);
    const double invalid_volume[] = {-1, 101, 65.5};
    for (size_t i = 0; i < sizeof(invalid_volume) / sizeof(invalid_volume[0]); i++) {
        cJSON_ReplaceItemInObjectCaseSensitive(input, "volume_percent", cJSON_CreateNumber(invalid_volume[i]));
        CHECK(!parse_state(input, &state));
    }
    cJSON_ReplaceItemInObjectCaseSensitive(input, "volume_percent", cJSON_CreateNumber(0));
    CHECK(parse_state(input, &state) && state.volume_percent == 0);
    cJSON_ReplaceItemInObjectCaseSensitive(input, "volume_percent", cJSON_CreateNumber(100));
    CHECK(parse_state(input, &state) && state.volume_percent == 100);
    cJSON_ReplaceItemInObjectCaseSensitive(input, "night_mode", cJSON_CreateString("true"));
    CHECK(!parse_state(input, &state));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "night_mode", cJSON_CreateBool(true));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "workday_state", cJSON_CreateString("RUNNING"));
    CHECK(!parse_state(input, &state));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "workday_state", cJSON_CreateString("OFF"));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "version", cJSON_CreateNumber(2));
    CHECK(!parse_state(input, &state));
    cJSON_Delete(input);
    CHECK(device_ui_uuid_valid(ID));
    CHECK(!device_ui_uuid_valid(NULL));
    CHECK(!device_ui_uuid_valid("1-1-1-1-1"));
    CHECK(!device_ui_uuid_valid("01234567-89AB-4cde-8fab-0123456789ab"));
    CHECK(!device_ui_uuid_valid("01234567-89ab-4cde-8fab-0123456789ab0"));
    puts("PASS state bounds: 32 roles, duplicate IDs, numeric/type limits and canonical UUIDs");
}

static void card_identity_long_text_and_status(void)
{
    device_ui_card_t card;
    cJSON *input = make_card();
    CHECK(parse_card(input, ID, &card));
    CHECK(strcmp(card.proposal_id, ID) == 0 && strcmp(card.content, "整理会议材料") == 0);
    CHECK(!parse_card(input, OTHER_ID, &card));
    CHECK(!parse_card(input, "1-1-1-1-1", &card));
    char *content = malloc(6004);
    CHECK(content != NULL);
    for (int i = 0; i < 2000; i++) memcpy(content + i * 3, "中", 3);
    content[6000] = '\0';
    cJSON_ReplaceItemInObjectCaseSensitive(input, "content", cJSON_CreateString(content));
    CHECK(parse_card(input, ID, &card) && strlen(card.content) == 6000);
    CHECK(memcmp(card.content, content, 6001) == 0);
    memcpy(content + 6000, "中", 3); content[6003] = '\0';
    cJSON_ReplaceItemInObjectCaseSensitive(input, "content", cJSON_CreateString(content));
    CHECK(!parse_card(input, ID, &card));
    free(content);
    cJSON_ReplaceItemInObjectCaseSensitive(input, "content", cJSON_CreateString("操作内容"));
    const char *statuses[] = {"PENDING", "EXECUTING", "EXECUTED", "CANCELLED", "EXPIRED", "FAILED"};
    for (size_t i = 0; i < sizeof(statuses) / sizeof(statuses[0]); i++) {
        cJSON_ReplaceItemInObjectCaseSensitive(input, "status", cJSON_CreateString(statuses[i]));
        CHECK(parse_card(input, ID, &card) && strcmp(card.status, statuses[i]) == 0);
    }
    cJSON_ReplaceItemInObjectCaseSensitive(input, "status", cJSON_CreateString("CONFIRMED"));
    CHECK(!parse_card(input, ID, &card));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "status", cJSON_CreateString("PENDING"));
    const double invalid_expiry[] = {-1, 121, 0.5};
    for (size_t i = 0; i < sizeof(invalid_expiry) / sizeof(invalid_expiry[0]); i++) {
        cJSON_ReplaceItemInObjectCaseSensitive(input, "expires_in_seconds", cJSON_CreateNumber(invalid_expiry[i]));
        CHECK(!parse_card(input, ID, &card));
    }
    cJSON_ReplaceItemInObjectCaseSensitive(input, "expires_in_seconds", cJSON_CreateNumber(0));
    CHECK(parse_card(input, ID, &card) && card.expires_in_seconds == 0);
    cJSON_ReplaceItemInObjectCaseSensitive(input, "expires_in_seconds", cJSON_CreateString("120"));
    CHECK(!parse_card(input, ID, &card));
    cJSON_Delete(input);
    puts("PASS card: expected ID prevents substitution; full 2000 Chinese characters; terminal statuses and expiry");
}

static void string_and_response_bounds(void)
{
    device_ui_card_t card;
    cJSON *input = make_card();
    char title[602]; memset(title, 'A', sizeof(title)); title[601] = '\0';
    cJSON_ReplaceItemInObjectCaseSensitive(input, "title", cJSON_CreateString(title));
    CHECK(!parse_card(input, ID, &card));
    title[600] = '\0';
    cJSON_ReplaceItemInObjectCaseSensitive(input, "title", cJSON_CreateString(title));
    CHECK(parse_card(input, ID, &card));
    cJSON_ReplaceItemInObjectCaseSensitive(input, "content", cJSON_CreateNull());
    CHECK(!parse_card(input, ID, &card));
    cJSON_Delete(input);
    input = make_card();
    char *base = cJSON_PrintUnformatted(input);
    CHECK(base != NULL);
    size_t used = strlen(base);
    char *padded = malloc(DEVICE_UI_RESPONSE_MAX + 2);
    CHECK(padded != NULL);
    memcpy(padded, base, used);
    memset(padded + used, ' ', DEVICE_UI_RESPONSE_MAX + 1 - used);
    padded[DEVICE_UI_RESPONSE_MAX + 1] = '\0';
    CHECK(device_ui_parse_card(padded, DEVICE_UI_RESPONSE_MAX, ID, &card));
    CHECK(!device_ui_parse_card(padded, DEVICE_UI_RESPONSE_MAX + 1, ID, &card));
    padded[used + 1] = '\0';
    CHECK(!device_ui_parse_card(padded, used + 2, ID, &card));
    CHECK(!device_ui_parse_card(NULL, 1, ID, &card));
    CHECK(!device_ui_parse_card(base, 0, ID, &card));
    free(padded); free(base); cJSON_Delete(input);
    puts("PASS buffers: output string limits, raw NUL and bounded 8192-byte HTTP responses");
}

static void malformed_and_recursive_json(void)
{
    device_ui_state_t state;
    cJSON *input = make_state();
    char *base = cJSON_PrintUnformatted(input);
    CHECK(base != NULL);
    size_t length = strlen(base);
    char *bad = malloc(length + 256);
    CHECK(bad != NULL);
    memcpy(bad, base, length - 1);
    strcpy(bad + length - 1, ",\"volume_percent\":20}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, ",\"vol\\u0075me_percent\":20}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, ",\"extra\":{\"x\":1,\"x\":2}}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, ",\"extra\":\"\\u0000\"}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, ",\"extra\":{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":{\"f\":1}}}}}}}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, ",\"extra\":\"literal braces {{{{{{{ and escaped quote \\\"\"}");
    CHECK(device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, "} {}");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    strcpy(bad + length - 1, "} garbage");
    CHECK(!device_ui_parse_state(bad, strlen(bad), &state));
    CHECK(!device_ui_parse_state(base, length - 1, &state));
    CHECK(!device_ui_parse_state("[]", 2, &state));
    free(bad); free(base); cJSON_Delete(input);
    puts("PASS strict JSON: duplicates including decoded keys, decoded NUL, deep nesting and trailing payloads");
}

static void exact_notices_and_shown_receipts(void)
{
    char json[256]; char id[37];
    snprintf(json, sizeof(json), "{\"type\":\"device_confirmation_available\",\"proposal_id\":\"%s\"}", ID);
    CHECK(device_ui_parse_confirmation_notice(json, strlen(json), id) && strcmp(id, ID) == 0);
    snprintf(json, sizeof(json), "{\"type\":\"device_confirmation_available\",\"proposal_id\":\"%s\",\"action\":\"CONFIRM\"}", ID);
    CHECK(!device_ui_parse_confirmation_notice(json, strlen(json), id));
    snprintf(json, sizeof(json), "{\"type\":\"device_confirmation_available\",\"proposal_id\":\"1-1-1-1-1\"}");
    CHECK(!device_ui_parse_confirmation_notice(json, strlen(json), id));
    snprintf(json, sizeof(json), "{\"type\":\"other_notice\",\"proposal_id\":\"%s\"}", ID);
    CHECK(!device_ui_parse_confirmation_notice(json, strlen(json), id));
    snprintf(json, sizeof(json), "{\"proposal_id\":\"%s\",\"status\":\"SHOWN\"}", ID);
    CHECK(device_ui_parse_shown_receipt(json, strlen(json), ID));
    CHECK(!device_ui_parse_shown_receipt(json, strlen(json), OTHER_ID));
    CHECK(!device_ui_parse_shown_receipt(json, strlen(json), "1-1-1-1-1"));
    snprintf(json, sizeof(json), "{\"proposal_id\":\"%s\",\"status\":\"SHOWN\",\"accepted\":true}", ID);
    CHECK(!device_ui_parse_shown_receipt(json, strlen(json), ID));
    snprintf(json, sizeof(json), "{\"proposal_id\":\"%s\",\"status\":\"EXECUTED\"}", ID);
    CHECK(!device_ui_parse_shown_receipt(json, strlen(json), ID));
    snprintf(json, sizeof(json), "{\"proposal_id\":\"%s\",\"status\":\"SHOWN\",\"status\":\"SHOWN\"}", ID);
    CHECK(!device_ui_parse_shown_receipt(json, strlen(json), ID));
    snprintf(json, sizeof(json), "{\"type\":\"device_ui_state_changed\"}");
    CHECK(device_ui_parse_state_notice(json, strlen(json)));
    snprintf(json, sizeof(json), "{\"type\":\"device_ui_state_changed\",\"quiet_today\":true}");
    CHECK(!device_ui_parse_state_notice(json, strlen(json)));
    snprintf(json, sizeof(json), "{\"type\":\"device_ui_state_changed\",\"type\":\"device_ui_state_changed\"}");
    CHECK(!device_ui_parse_state_notice(json, strlen(json)));
    snprintf(json, sizeof(json), "{\"type\":\"device_ui_state_changed\"} {}");
    CHECK(!device_ui_parse_state_notice(json, strlen(json)));
    snprintf(json, sizeof(json), "{\"type\":\"device_ui_state_changed\\u0000\"}");
    CHECK(!device_ui_parse_state_notice(json, strlen(json)));
    snprintf(json, sizeof(json), "{\"type\":\"other_notice\"}");
    CHECK(!device_ui_parse_state_notice(json, strlen(json)));
    puts("PASS receipts: notices and SHOWN require exactly two fields and the canonical expected UUID");
}

int main(void)
{
    state_and_saved_response();
    state_bounds_and_role_identity();
    card_identity_long_text_and_status();
    string_and_response_bounds();
    malformed_and_recursive_json();
    exact_notices_and_shown_receipts();
    printf("PASS device UI production parser: 6 groups, %u checks\n", checks);
    return 0;
}
