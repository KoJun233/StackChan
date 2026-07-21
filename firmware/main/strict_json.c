#include "strict_json.h"

#include <stdint.h>
#include <string.h>

bool strict_json_contains_decoded_nul_escape(const char *json, size_t length)
{
    if (json == NULL) {
        return false;
    }
    size_t offset = 0;
    while (offset < length) {
        if (json[offset] != '\\') {
            ++offset;
            continue;
        }
        size_t run_start = offset;
        while (offset < length && json[offset] == '\\') {
            ++offset;
        }
        size_t run_length = offset - run_start;
        if ((run_length & 1U) != 0U && offset + 5 <= length && json[offset] == 'u' &&
            memcmp(json + offset + 1, "0000", 4) == 0) {
            return true;
        }
    }
    return false;
}

bool strict_json_has_only_trailing_whitespace(const char *json, size_t length, const char *parse_end)
{
    if (json == NULL || parse_end == NULL) {
        return false;
    }
    uintptr_t begin = (uintptr_t)json;
    if (length > UINTPTR_MAX - begin) {
        return false;
    }
    uintptr_t finish = begin + length;
    uintptr_t parsed = (uintptr_t)parse_end;
    if (parsed < begin || parsed > finish) {
        return false;
    }
    size_t offset = (size_t)(parsed - begin);
    for (; offset < length; ++offset) {
        char character = json[offset];
        if (!(character == ' ' || character == '\t' || character == '\r' || character == '\n')) {
            return false;
        }
    }
    return true;
}
