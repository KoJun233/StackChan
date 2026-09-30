#pragma once
/* Host tests replace only platform logging and critical sections. */
#define ESP_LOGI(tag, format, ...) ((void)(tag))
