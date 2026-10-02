#pragma once
#include "FreeRTOS.h"
BaseType_t xTaskCreatePinnedToCoreWithCaps(void (*)(void *), const char *, unsigned,
    void *, unsigned, TaskHandle_t *, int, unsigned);
