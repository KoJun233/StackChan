#pragma once

#include <stdbool.h>
#include <stddef.h>

/** Returns whether the bounded JSON bytes contain an escape that decodes to NUL. */
bool strict_json_contains_decoded_nul_escape(const char *json, size_t length);

/** Returns whether every bounded byte after parse_end is JSON whitespace. */
bool strict_json_has_only_trailing_whitespace(const char *json, size_t length, const char *parse_end);
