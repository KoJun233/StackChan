#pragma once
#include "FreeRTOS.h"
typedef void *QueueHandle_t;
#ifdef __cplusplus
extern "C" {
#endif
QueueHandle_t xQueueCreate(unsigned, unsigned);
BaseType_t xQueueSend(QueueHandle_t, const void *, TickType_t);
BaseType_t xQueueReceive(QueueHandle_t, void *, TickType_t);
#ifdef __cplusplus
}
#endif
