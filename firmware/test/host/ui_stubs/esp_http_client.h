#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"
typedef void *esp_http_client_handle_t;
typedef struct {
    const char *url;
    int timeout_ms;
    bool disable_auto_redirect;
    int buffer_size, buffer_size_tx;
    int transport_type;
    const char *cert_pem;
    void *crt_bundle_attach;
} esp_http_client_config_t;
enum { HTTP_TRANSPORT_OVER_TCP, HTTP_TRANSPORT_OVER_SSL };
typedef enum { HTTP_METHOD_GET, HTTP_METHOD_POST } esp_http_client_method_t;
esp_http_client_handle_t esp_http_client_init(const esp_http_client_config_t *);
esp_err_t esp_http_client_set_header(esp_http_client_handle_t, const char *, const char *);
esp_err_t esp_http_client_set_method(esp_http_client_handle_t, esp_http_client_method_t);
esp_err_t esp_http_client_open(esp_http_client_handle_t, int);
int esp_http_client_write(esp_http_client_handle_t, const char *, int);
int64_t esp_http_client_fetch_headers(esp_http_client_handle_t);
int esp_http_client_get_status_code(esp_http_client_handle_t);
int esp_http_client_read(esp_http_client_handle_t, char *, int);
bool esp_http_client_is_complete_data_received(esp_http_client_handle_t);
esp_err_t esp_http_client_close(esp_http_client_handle_t);
esp_err_t esp_http_client_cleanup(esp_http_client_handle_t);
