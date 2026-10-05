#include "device_ui_protocol.h"

#include <math.h>
#include <string.h>
#include "cJSON.h"
#include "strict_json.h"

bool device_ui_uuid_valid(const char *text)
{
    if (text == NULL || strnlen(text, 37) != 36) return false;
    for (int i = 0; i < 36; i++) {
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (text[i] != '-') return false; }
        else if (!((text[i] >= '0' && text[i] <= '9') || (text[i] >= 'a' && text[i] <= 'f')))
            return false;
    }
    return true;
}

static bool unique_objects(const cJSON *item)
{
    if (cJSON_IsObject(item)) {
        for (const cJSON *a = item->child; a != NULL; a = a->next) {
            if (a->string == NULL) return false;
            for (const cJSON *b = a->next; b != NULL; b = b->next)
                if (b->string == NULL || strcmp(a->string, b->string) == 0) return false;
        }
    }
    for (const cJSON *child = item->child; child != NULL; child = child->next)
        if (!unique_objects(child)) return false;
    return true;
}

static cJSON *parse(const char *json, size_t length)
{
    if (json == NULL || length == 0 || length > DEVICE_UI_RESPONSE_MAX ||
        memchr(json, '\0', length) != NULL || strict_json_contains_decoded_nul_escape(json, length))
        return NULL;
    // Bound nesting before cJSON recursion touches the network worker's stack.
    bool quoted = false, escaped = false;
    int depth = 0;
    for (size_t i = 0; i < length; i++) {
        char c = json[i];
        if (quoted) {
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') quoted = false;
        } else if (c == '"') quoted = true;
        else if (c == '{' || c == '[') { if (++depth > 6) return NULL; }
        else if (c == '}' || c == ']') { if (--depth < 0) return NULL; }
    }
    if (quoted || depth != 0) return NULL;
    const char *end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(json, length, &end, false);
    if (!cJSON_IsObject(root) || !strict_json_has_only_trailing_whitespace(json, length, end) ||
        !unique_objects(root)) { cJSON_Delete(root); return NULL; }
    return root;
}

static bool text(const cJSON *root, const char *key, char *out, size_t capacity)
{
    const cJSON *value = cJSON_GetObjectItemCaseSensitive(root, key);
    if (!cJSON_IsString(value) || value->valuestring == NULL || strlen(value->valuestring) >= capacity)
        return false;
    strcpy(out, value->valuestring);
    return true;
}

static bool boolean(const cJSON *root, const char *key, bool *out)
{
    const cJSON *value = cJSON_GetObjectItemCaseSensitive(root, key);
    if (!cJSON_IsBool(value)) return false;
    *out = cJSON_IsTrue(value);
    return true;
}

static bool number(const cJSON *root, const char *key, int min, int max, int *out)
{
    const cJSON *value = cJSON_GetObjectItemCaseSensitive(root, key);
    if (!cJSON_IsNumber(value) || !isfinite(value->valuedouble) ||
        value->valuedouble != floor(value->valuedouble) || value->valuedouble < min || value->valuedouble > max)
        return false;
    *out = (int)value->valuedouble;
    return true;
}

static bool role(const cJSON *root, device_ui_role_t *out)
{
    return cJSON_IsObject(root) && text(root, "id", out->id, sizeof(out->id)) &&
           device_ui_uuid_valid(out->id) && text(root, "name", out->name, sizeof(out->name));
}

bool device_ui_parse_state(const char *json, size_t length, device_ui_state_t *state)
{
    if (state == NULL) return false;
    memset(state, 0, sizeof(*state));
    cJSON *owner = parse(json, length);
    if (owner == NULL) return false;
    const cJSON *saved = cJSON_GetObjectItemCaseSensitive(owner, "state");
    const cJSON *root = cJSON_IsObject(saved) ? saved : owner;
    int version = 0;
    char result[16];
    bool wrapper_valid = saved == NULL || (cJSON_IsObject(saved) && text(owner, "result", result, sizeof(result)) && strcmp(result, "SAVED") == 0);
    bool valid = wrapper_valid && number(root, "version", 1, 1, &version) &&
        number(root, "volume_percent", 0, 100, &state->volume_percent) &&
        boolean(root, "night_mode", &state->night_mode) && boolean(root, "quiet_today", &state->quiet_today) &&
        boolean(root, "rest_pending", &state->rest_pending) &&
        boolean(root, "roles_truncated", &state->roles_truncated) &&
        text(root, "workday_state", state->workday_state, sizeof(state->workday_state)) &&
        role(cJSON_GetObjectItemCaseSensitive(root, "role"), &state->role);
    static const char *const workday_states[] = {"OFF", "STARTING", "ACTIVE_PRESENT", "ACTIVE_ABSENT", "REST_PROMPTED", "RESTING", "SKIPPED_FOR_DAY"};
    bool workday_valid = false;
    for (size_t i = 0; i < sizeof(workday_states) / sizeof(workday_states[0]); i++)
        workday_valid = workday_valid || strcmp(state->workday_state, workday_states[i]) == 0;
    valid = valid && workday_valid;
    const cJSON *roles = cJSON_GetObjectItemCaseSensitive(root, "roles");
    int count = cJSON_GetArraySize(roles);
    valid = valid && cJSON_IsArray(roles) && count >= 0 && count <= (int)DEVICE_UI_ROLES_MAX;
    for (int i = 0; valid && i < count; i++) {
        valid = role(cJSON_GetArrayItem(roles, i), &state->roles[i]);
        for (int j = 0; valid && j < i; j++)
            valid = strcmp(state->roles[j].id, state->roles[i].id) != 0;
    }
    state->role_count = valid ? (uint8_t)count : 0;
    const cJSON *pending = cJSON_GetObjectItemCaseSensitive(root, "pending_confirmation_id");
    if (!cJSON_IsNull(pending)) valid = valid && text(root, "pending_confirmation_id",
        state->pending_confirmation_id, sizeof(state->pending_confirmation_id)) &&
        device_ui_uuid_valid(state->pending_confirmation_id);
    cJSON_Delete(owner);
    return valid;
}

bool device_ui_parse_card(const char *json, size_t length, const char *expected_id,
                          device_ui_card_t *card)
{
    if (card == NULL || !device_ui_uuid_valid(expected_id)) return false;
    memset(card, 0, sizeof(*card));
    cJSON *root = parse(json, length);
    if (root == NULL) return false;
    int expires = 0;
    bool valid = text(root, "proposal_id", card->proposal_id, sizeof(card->proposal_id)) &&
        strcmp(card->proposal_id, expected_id) == 0 &&
        text(root, "action_label", card->action_label, sizeof(card->action_label)) &&
        text(root, "title", card->title, sizeof(card->title)) &&
        text(root, "content", card->content, sizeof(card->content)) &&
        text(root, "time_label", card->time_label, sizeof(card->time_label)) &&
        text(root, "role_name", card->role_name, sizeof(card->role_name)) &&
        text(root, "status", card->status, sizeof(card->status)) &&
        text(root, "result_message", card->result_message, sizeof(card->result_message)) &&
        number(root, "expires_in_seconds", 0, 120, &expires);
    static const char *const statuses[] = {"PENDING", "EXECUTING", "EXECUTED", "CANCELLED", "EXPIRED", "FAILED"};
    bool status_valid = false;
    for (size_t i = 0; i < sizeof(statuses) / sizeof(statuses[0]); i++)
        status_valid = status_valid || strcmp(card->status, statuses[i]) == 0;
    card->expires_in_seconds = (uint32_t)expires;
    cJSON_Delete(root);
    return valid && status_valid;
}

bool device_ui_parse_confirmation_notice(const char *json, size_t length, char id[37])
{
    if (id == NULL) return false;
    id[0] = '\0';
    cJSON *root = parse(json, length);
    if (root == NULL) return false;
    char type[40];
    bool valid = cJSON_GetArraySize(root) == 2 && text(root, "type", type, sizeof(type)) &&
        strcmp(type, "device_confirmation_available") == 0 &&
        text(root, "proposal_id", id, 37) && device_ui_uuid_valid(id);
    cJSON_Delete(root);
    return valid;
}

bool device_ui_parse_shown_receipt(const char *json, size_t length, const char *expected_id)
{
    if (!device_ui_uuid_valid(expected_id)) return false;
    cJSON *root = parse(json, length);
    if (root == NULL) return false;
    char id[37], status[16];
    bool valid = cJSON_GetArraySize(root) == 2 && text(root, "proposal_id", id, sizeof(id)) &&
        strcmp(id, expected_id) == 0 && text(root, "status", status, sizeof(status)) && strcmp(status, "SHOWN") == 0;
    cJSON_Delete(root);
    return valid;
}

bool device_ui_parse_state_notice(const char *json, size_t length)
{
    cJSON *root = parse(json, length);
    if (root == NULL) return false;
    char type[40];
    bool valid = cJSON_GetArraySize(root) == 1 && text(root, "type", type, sizeof(type)) &&
        strcmp(type, "device_ui_state_changed") == 0;
    cJSON_Delete(root);
    return valid;
}
