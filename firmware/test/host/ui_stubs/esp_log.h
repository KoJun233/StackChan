#pragma once
static inline void host_ui_log(const char *tag,const char *format,...)
{
    (void)tag;(void)format;
}
#define ESP_LOGI host_ui_log
