#pragma once

#include <stdbool.h>
#include <stddef.h>

#define WAKE_WORD_MODEL_NAME_MAX_LEN 32
#define WAKE_WORD_DEFAULT_MODEL_NAME "wn9l_histackchan_tts3"

typedef struct {
    const char *name;
    bool used_fallback;
} wake_word_model_selection_t;

/** Returns an exact, valid WakeNet model name from the supplied model list. */
const char *wake_word_model_find(const char *const *model_names,
                                 size_t model_count,
                                 const char *requested_name);

/** Selects the requested model or the explicit fallback; never selects an arbitrary model. */
wake_word_model_selection_t wake_word_model_select(const char *const *model_names,
                                                    size_t model_count,
                                                    const char *requested_name,
                                                    const char *fallback_name);
