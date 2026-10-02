#pragma once
#include "FreeRTOS.h"
void vTaskDelete(TaskHandle_t);
void vTaskDelay(TickType_t);
static inline unsigned uxTaskGetStackHighWaterMark(TaskHandle_t) { return 0; }

BaseType_t xTaskCreate(void (*)(void *), const char *, unsigned, void *, unsigned, TaskHandle_t *);
