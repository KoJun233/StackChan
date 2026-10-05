#pragma once
typedef struct { int transport; const char *cert_pem; void *crt_bundle_attach; } esp_websocket_client_config_t;
enum { WEBSOCKET_TRANSPORT_OVER_TCP, WEBSOCKET_TRANSPORT_OVER_SSL };
