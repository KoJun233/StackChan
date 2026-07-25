#include "wake_word_model.h"

#include <ctype.h>
#include <string.h>

static bool is_valid_model_name(const char *name)
{
    size_t length = name == NULL ? 0 : strnlen(name, WAKE_WORD_MODEL_NAME_MAX_LEN + 1);
    if (length < 3 || length > WAKE_WORD_MODEL_NAME_MAX_LEN || name[0] != 'w' ||
        name[1] != 'n' || !isdigit((unsigned char)name[2])) {
        return false;
    }
    for (size_t index = 2; index < length; ++index) {
        unsigned char character = (unsigned char)name[index];
        if (!islower(character) && !isdigit(character) && character != '_') {
            return false;
        }
    }
    return true;
}

const char *wake_word_model_find(const char *const *model_names,
                                 size_t model_count,
                                 const char *requested_name)
{
    if (model_names == NULL || !is_valid_model_name(requested_name)) {
        return NULL;
    }
    for (size_t index = 0; index < model_count; ++index) {
        const char *candidate = model_names[index];
        if (is_valid_model_name(candidate) && strcmp(candidate, requested_name) == 0) {
            return candidate;
        }
    }
    return NULL;
}

wake_word_model_selection_t wake_word_model_select(const char *const *model_names,
                                                    size_t model_count,
                                                    const char *requested_name,
                                                    const char *fallback_name)
{
    wake_word_model_selection_t selection = {0};
    selection.name = wake_word_model_find(model_names, model_count, requested_name);
    if (selection.name != NULL) {
        return selection;
    }
    selection.name = wake_word_model_find(model_names, model_count, fallback_name);
    selection.used_fallback = selection.name != NULL;
    return selection;
}
