#include <stdio.h>
#include <string.h>

#include "cJSON.h"
#include "unity.h"

#include "audio_wav.h"
#include "device_identity.h"
#include "device_credentials.h"
#include "device_endpoint.h"
#include "device_provisioning.h"
#include "device_protocol.h"
#include "device_transport.h"
#include "screensaver_motion.h"
#include "interaction_state.h"
#include "face_animation.h"
#include "strict_json.h"
#include "touch_interaction.h"
#include "voice_control.h"
#include "voice_protocol.h"
#include "wake_word_model.h"

#if CONFIG_STACKCHAN_LAN_HTTP_MODE
#define TEST_SERVER_BASE_URL "http://192.168.137.1:8080"
#define TEST_WEBSOCKET_URL "ws://192.168.137.1:8080/api/v1/ws/device"
#else
#define TEST_SERVER_BASE_URL "https://companion.example"
#define TEST_WEBSOCKET_URL "wss://companion.example/api/v1/ws/device"
#endif

TEST_CASE("screensaver pupil motion is cyclic and remains inside the eye", "[screensaver]")
{
    size_t frame_count = screensaver_motion_frame_count();
    TEST_ASSERT_EQUAL_UINT32(8, frame_count);
    for (size_t frame = 0; frame < frame_count; frame++) {
        screensaver_pupil_offset_t offset = screensaver_motion_offset(frame);
        TEST_ASSERT_INT_WITHIN(8, 0, offset.x);
        TEST_ASSERT_INT_WITHIN(8, 0, offset.y);
    }

    screensaver_pupil_offset_t first = screensaver_motion_offset(0);
    screensaver_pupil_offset_t wrapped = screensaver_motion_offset(frame_count);
    TEST_ASSERT_EQUAL_INT(first.x, wrapped.x);
    TEST_ASSERT_EQUAL_INT(first.y, wrapped.y);
}

TEST_CASE("interaction presentation keeps offline distinct and recoverable", "[interaction_state]")
{
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_LISTENING,
        companion_interaction_visible_state(COMPANION_FACE_LISTENING, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_PROCESSING,
        companion_interaction_visible_state(COMPANION_FACE_PROCESSING, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_SUCCESS,
        companion_interaction_visible_state(COMPANION_FACE_SUCCESS, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_RECOVERABLE_ERROR,
        companion_interaction_visible_state(COMPANION_FACE_RECOVERABLE_ERROR, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_OFFLINE,
        companion_interaction_visible_state(COMPANION_FACE_IDLE, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_NO_SPEECH,
        companion_interaction_visible_state(COMPANION_FACE_NO_SPEECH, true));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_SUCCESS,
        companion_interaction_visible_state(COMPANION_FACE_SUCCESS, true));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_RECOVERABLE_ERROR,
        companion_interaction_visible_state((companion_face_state_t)99, false));
    TEST_ASSERT_EQUAL(
        COMPANION_FACE_RECOVERABLE_ERROR,
        companion_interaction_visible_state((companion_face_state_t)99, true));
    TEST_ASSERT_TRUE(companion_interaction_allows_screensaver(COMPANION_FACE_IDLE));
    TEST_ASSERT_TRUE(companion_interaction_allows_screensaver(COMPANION_FACE_OFFLINE));
    TEST_ASSERT_FALSE(companion_interaction_allows_screensaver(COMPANION_FACE_LISTENING));
    TEST_ASSERT_FALSE(companion_interaction_allows_screensaver(COMPANION_FACE_NO_SPEECH));
    TEST_ASSERT_FALSE(companion_interaction_allows_screensaver(COMPANION_FACE_SUCCESS));
}

TEST_CASE("built in face animation stays bounded and state specific", "[face_animation]")
{
    for (int state = COMPANION_FACE_IDLE; state <= COMPANION_FACE_RECOVERABLE_ERROR; state++) {
        for (uint32_t elapsed_ms = 0; elapsed_ms <= 10000; elapsed_ms += 137) {
            companion_face_frame_t frame = companion_face_animation_frame(
                (companion_face_state_t)state, elapsed_ms);
            TEST_ASSERT_INT_WITHIN(8, 0, frame.gaze_x);
            TEST_ASSERT_INT_WITHIN(8, 0, frame.gaze_y);
            TEST_ASSERT_GREATER_OR_EQUAL_UINT8(12, frame.eye_open_percent);
            TEST_ASSERT_LESS_OR_EQUAL_UINT8(100, frame.eye_open_percent);
            TEST_ASSERT_LESS_OR_EQUAL_UINT8(100, frame.mouth_open_percent);
            TEST_ASSERT_LESS_OR_EQUAL_UINT8(100, frame.activity_percent);
        }
    }

    companion_face_frame_t listening = companion_face_animation_frame(COMPANION_FACE_LISTENING, 450);
    companion_face_frame_t speaking = companion_face_animation_frame(COMPANION_FACE_SPEAKING, 210);
    companion_face_frame_t offline = companion_face_animation_frame(COMPANION_FACE_OFFLINE, 210);
    TEST_ASSERT_GREATER_THAN_UINT8(0, listening.activity_percent);
    TEST_ASSERT_GREATER_THAN_UINT8(0, speaking.mouth_open_percent);
    TEST_ASSERT_EQUAL_UINT8(0, offline.mouth_open_percent);
    TEST_ASSERT_TRUE(companion_face_animation_is_dynamic(COMPANION_FACE_IDLE));
    TEST_ASSERT_TRUE(companion_face_animation_is_dynamic(COMPANION_FACE_PROCESSING));
    TEST_ASSERT_FALSE(companion_face_animation_is_dynamic(COMPANION_FACE_OFFLINE));
    TEST_ASSERT_FALSE(companion_face_animation_is_dynamic(COMPANION_FACE_NO_SPEECH));
}

TEST_CASE("touch interaction separates short cancel taps from online press to talk", "[touch_interaction]")
{
    TEST_ASSERT_FALSE(touch_interaction_should_start_press_to_talk(
        TOUCH_INTERACTION_IDLE, 599, true, false));
    TEST_ASSERT_TRUE(touch_interaction_should_start_press_to_talk(
        TOUCH_INTERACTION_IDLE, 600, true, false));
    TEST_ASSERT_FALSE(touch_interaction_should_start_press_to_talk(
        TOUCH_INTERACTION_IDLE, 600, false, false));
    TEST_ASSERT_FALSE(touch_interaction_should_start_press_to_talk(
        TOUCH_INTERACTION_PROCESSING, 600, true, false));
    TEST_ASSERT_EQUAL(
        TOUCH_INTERACTION_ACTION_CANCEL,
        touch_interaction_release_action(TOUCH_INTERACTION_LISTENING, 120));
    TEST_ASSERT_EQUAL(
        TOUCH_INTERACTION_ACTION_CANCEL,
        touch_interaction_release_action(TOUCH_INTERACTION_PLAYING, 120));
    TEST_ASSERT_EQUAL(
        TOUCH_INTERACTION_ACTION_DISMISS,
        touch_interaction_release_action(TOUCH_INTERACTION_FEEDBACK, 120));
    TEST_ASSERT_EQUAL(
        TOUCH_INTERACTION_ACTION_NONE,
        touch_interaction_release_action(TOUCH_INTERACTION_LISTENING, 600));
}

TEST_CASE("transport reconnect backoff is bounded", "[device_transport]")
{
    TEST_ASSERT_EQUAL_UINT32(1, device_transport_next_retry_seconds(0));
    TEST_ASSERT_EQUAL_UINT32(2, device_transport_next_retry_seconds(1));
    TEST_ASSERT_EQUAL_UINT32(32, device_transport_next_retry_seconds(16));
    TEST_ASSERT_EQUAL_UINT32(60, device_transport_next_retry_seconds(30));
    TEST_ASSERT_EQUAL_UINT32(60, device_transport_next_retry_seconds(60));
}

static device_identity_t valid_identity(void)
{
    device_identity_t identity = {
        .server_base_url = TEST_SERVER_BASE_URL,
        .device_id = "550e8400-e29b-41d4-a716-446655440000",
        .access_token = "header.payload.signature",
        .access_token_expires_at = "2026-07-19T00:00:00Z",
        .refresh_token = "opaque-refresh-token",
    };
    return identity;
}

TEST_CASE("device identity requires complete renewable mode-compatible credentials", "[device_identity]")
{
    device_identity_t identity = valid_identity();

    TEST_ASSERT_TRUE(device_identity_is_valid(&identity));
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    strcpy(identity.server_base_url, "https://192.168.137.1:8080");
#else
    strcpy(identity.server_base_url, "http://192.168.137.1:8080");
#endif
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));

    identity = valid_identity();
    strcpy(identity.server_base_url, "wss://companion.example");
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));

    identity = valid_identity();
    strcpy(identity.server_base_url, "https:///");
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));

    identity = valid_identity();
    identity.refresh_token[0] = '\0';
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));

    identity = valid_identity();
    strcpy(identity.access_token_expires_at, "2026-07-19 00:00:00");
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));

    identity = valid_identity();
    strcpy(identity.access_token_expires_at, "2026-07-19T00:00:00.Z");
    TEST_ASSERT_FALSE(device_identity_is_valid(&identity));
}

TEST_CASE("fixed endpoints stay same-origin and credentials stay out of URIs", "[device_endpoint]")
{
    device_identity_t identity = valid_identity();
    char claim[256] = {0};
    char refresh[256] = {0};
    char voice[256] = {0};
    char reminder[256] = {0};
    char wake_model[256] = {0};
    char websocket[256] = {0};
    TEST_ASSERT_TRUE(device_endpoint_build_http_url(identity.server_base_url,
                                                    DEVICE_ENDPOINT_PAIRING_CLAIM_PATH,
                                                    claim, sizeof(claim)));
    TEST_ASSERT_TRUE(device_endpoint_build_http_url(identity.server_base_url,
                                                    DEVICE_ENDPOINT_TOKEN_REFRESH_PATH,
                                                    refresh, sizeof(refresh)));
    TEST_ASSERT_TRUE(device_endpoint_build_http_url(identity.server_base_url,
                                                    DEVICE_ENDPOINT_VOICE_TURN_PATH,
                                                    voice, sizeof(voice)));
    TEST_ASSERT_TRUE(device_endpoint_build_reminder_audio_url(
        identity.server_base_url,
        "f20b6177-3f7a-466a-9eae-70120bbf1912",
        reminder,
        sizeof(reminder)));
    TEST_ASSERT_TRUE(device_endpoint_build_wake_model_url(
        identity.server_base_url,
        "550e8400-e29b-41d4-a716-446655440000",
        wake_model,
        sizeof(wake_model)));
    TEST_ASSERT_TRUE(device_endpoint_build_websocket_uri(&identity, websocket, sizeof(websocket)));
    TEST_ASSERT_NOT_NULL(strstr(claim, "/api/v1/pairing/claim"));
    TEST_ASSERT_NOT_NULL(strstr(refresh, "/api/v1/devices/token:refresh"));
    TEST_ASSERT_NOT_NULL(strstr(voice, "/api/v1/device/voice/turn"));
    TEST_ASSERT_NOT_NULL(strstr(reminder,
                                 "/api/v1/device/reminders/f20b6177-3f7a-466a-9eae-70120bbf1912/audio"));
    TEST_ASSERT_NOT_NULL(strstr(wake_model,
                                "/api/v1/device/wake-models/550e8400-e29b-41d4-a716-446655440000/artifact"));
    TEST_ASSERT_EQUAL_STRING(TEST_WEBSOCKET_URL, websocket);
    TEST_ASSERT_NULL(strchr(websocket, '?'));
    TEST_ASSERT_NULL(strstr(websocket, identity.access_token));
    TEST_ASSERT_FALSE(device_endpoint_build_http_url(identity.server_base_url, "/api/v1/other",
                                                     claim, sizeof(claim)));
    TEST_ASSERT_FALSE(device_endpoint_build_reminder_audio_url(
        identity.server_base_url, "../voice/turn", reminder, sizeof(reminder)));
    TEST_ASSERT_FALSE(device_endpoint_build_wake_model_url(
        identity.server_base_url, "../voice/turn", wake_model, sizeof(wake_model)));
}

TEST_CASE("compiled mode selects one redirect-safe transport profile", "[device_endpoint]")
{
    esp_http_client_config_t http = {0};
    esp_websocket_client_config_t websocket = {0};
    device_endpoint_configure_http_client(&http);
    device_endpoint_configure_websocket_client(&websocket);
    TEST_ASSERT_TRUE(http.disable_auto_redirect);
#if CONFIG_STACKCHAN_LAN_HTTP_MODE
    TEST_ASSERT_EQUAL(HTTP_TRANSPORT_OVER_TCP, http.transport_type);
    TEST_ASSERT_EQUAL(WEBSOCKET_TRANSPORT_OVER_TCP, websocket.transport);
    TEST_ASSERT_NULL(http.cert_pem);
    TEST_ASSERT_TRUE(http.crt_bundle_attach == NULL);
    TEST_ASSERT_NULL(websocket.cert_pem);
    TEST_ASSERT_TRUE(websocket.crt_bundle_attach == NULL);
#else
    TEST_ASSERT_EQUAL(HTTP_TRANSPORT_OVER_SSL, http.transport_type);
    TEST_ASSERT_EQUAL(WEBSOCKET_TRANSPORT_OVER_SSL, websocket.transport);
#if CONFIG_STACKCHAN_LAN_TEST_CERT
    TEST_ASSERT_NOT_NULL(http.cert_pem);
    TEST_ASSERT_NOT_NULL(websocket.cert_pem);
#else
    TEST_ASSERT_TRUE(http.crt_bundle_attach != NULL);
    TEST_ASSERT_TRUE(websocket.crt_bundle_attach != NULL);
#endif
#endif
}

#if CONFIG_STACKCHAN_LAN_HTTP_MODE
TEST_CASE("LAN HTTP mode accepts only unambiguous private IPv4 origins", "[device_identity]")
{
    const char *valid[] = {
        "http://10.0.0.0", "http://10.255.255.255:65535",
        "http://172.16.0.0:1", "http://172.31.255.255:8080",
        "http://192.168.0.0", "http://192.168.255.255:8080",
    };
    const char *invalid[] = {
        "http://0.0.0.0", "http://9.255.255.255", "http://11.0.0.0",
        "http://127.0.0.1", "http://169.254.1.1", "http://172.15.255.255",
        "http://172.32.0.0", "http://192.167.255.255", "http://192.169.0.0",
        "http://224.0.0.1", "http://8.8.8.8", "http://localhost:8080",
        "http://stackchan.local:8080", "http://[fd00::1]:8080",
        "http://192.168.001.1:8080", "http://192.168.1.1:0",
        "http://192.168.1.1:65536", "http://192.168.1.1:not-a-port",
        "http://192.168.1.1:8080/", "http://192.168.1.1:8080/api",
        "http://192.168.1.1:8080?x=1", "http://192.168.1.1:8080#x",
        "http://user@192.168.1.1:8080", "http://192.168.1.1:8080 ",
        "http://192.168.1.1:\n", "https://192.168.1.1:8080",
    };
    for (size_t index = 0; index < sizeof(valid) / sizeof(valid[0]); ++index) {
        TEST_ASSERT_TRUE_MESSAGE(device_identity_is_valid_server_base_url(valid[index]), valid[index]);
    }
    for (size_t index = 0; index < sizeof(invalid) / sizeof(invalid[0]); ++index) {
        TEST_ASSERT_FALSE_MESSAGE(device_identity_is_valid_server_base_url(invalid[index]), invalid[index]);
    }
}
#else
TEST_CASE("secure mode keeps the existing canonical HTTPS policy", "[device_identity]")
{
    TEST_ASSERT_TRUE(device_identity_is_valid_server_base_url("https://companion.example"));
    TEST_ASSERT_TRUE(device_identity_is_valid_server_base_url("https://companion.example:443/"));
    TEST_ASSERT_TRUE(device_identity_is_valid_server_base_url("https://[2001:db8::1]"));
    TEST_ASSERT_TRUE(device_identity_is_valid_server_base_url("https://[2001:db8::1]:8443/"));
    TEST_ASSERT_FALSE(device_identity_is_valid_server_base_url("http://192.168.1.1:8080"));
    TEST_ASSERT_FALSE(device_identity_is_valid_server_base_url("https://:"));
    TEST_ASSERT_FALSE(device_identity_is_valid_server_base_url("https://companion.example:0"));
    TEST_ASSERT_FALSE(device_identity_is_valid_server_base_url("https://companion.example:65536"));
    TEST_ASSERT_FALSE(device_identity_is_valid_server_base_url("https://[not-an-ipv6]"));
}
#endif

TEST_CASE("claim response stores the canonical origin and renewable credentials", "[device_provisioning]")
{
    const char *response_json = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                                "\"accessToken\":\"header.payload.signature\","
                                "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                                "\"refreshToken\":\"opaque-refresh-token\","
                                "\"refreshUrl\":\"/api/v1/devices/token:refresh\","
                                "\"wsUrl\":\"/api/v1/ws/device\"}";
    device_identity_t identity = {0};

    TEST_ASSERT_TRUE(device_provisioning_parse_claim_response(
        response_json, strlen(response_json), TEST_SERVER_BASE_URL, &identity));
    TEST_ASSERT_EQUAL_STRING(TEST_SERVER_BASE_URL, identity.server_base_url);
    TEST_ASSERT_EQUAL_STRING("550e8400-e29b-41d4-a716-446655440000", identity.device_id);
    TEST_ASSERT_EQUAL_STRING("header.payload.signature", identity.access_token);
    TEST_ASSERT_EQUAL_STRING("2026-07-19T00:00:00Z", identity.access_token_expires_at);
    TEST_ASSERT_EQUAL_STRING("opaque-refresh-token", identity.refresh_token);
}

TEST_CASE("claim response rejects unexpected fields and credential-bearing websocket paths", "[device_provisioning]")
{
    const char *unexpected_field = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                                   "\"accessToken\":\"header.payload.signature\","
                                   "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                                   "\"refreshToken\":\"opaque-refresh-token\","
                                   "\"refreshUrl\":\"/api/v1/devices/token:refresh\","
                                   "\"wsUrl\":\"/api/v1/ws/device\",\"unexpected\":true}";
    const char *query_ws_url = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                               "\"accessToken\":\"header.payload.signature\","
                               "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                               "\"refreshToken\":\"opaque-refresh-token\","
                               "\"refreshUrl\":\"/api/v1/devices/token:refresh\","
                               "\"wsUrl\":\"/api/v1/ws/device?access_token=placeholder\"}";
    const char *trailing_data = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                                "\"accessToken\":\"header.payload.signature\","
                                "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                                "\"refreshToken\":\"opaque-refresh-token\","
                                "\"refreshUrl\":\"/api/v1/devices/token:refresh\","
                                "\"wsUrl\":\"/api/v1/ws/device\"}garbage";
    device_identity_t identity = {0};

    TEST_ASSERT_FALSE(device_provisioning_parse_claim_response(
        unexpected_field, strlen(unexpected_field), TEST_SERVER_BASE_URL, &identity));
    TEST_ASSERT_FALSE(device_provisioning_parse_claim_response(
        query_ws_url, strlen(query_ws_url), TEST_SERVER_BASE_URL, &identity));
    TEST_ASSERT_FALSE(device_provisioning_parse_claim_response(
        trailing_data, strlen(trailing_data), TEST_SERVER_BASE_URL, &identity));
}

TEST_CASE("claim response rejects escaped NUL suffixes and clears identity", "[device_provisioning]")
{
    const char *nul_ws_url = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                             "\"accessToken\":\"header.payload.signature\","
                             "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                             "\"refreshToken\":\"opaque-refresh-token\","
                             "\"refreshUrl\":\"/api/v1/devices/token:refresh\","
                             "\"wsUrl\":\"/api/v1/ws/device\\u0000?hidden=true\"}";
    const char *nul_refresh_url = "{\"deviceId\":\"550e8400-e29b-41d4-a716-446655440000\","
                                  "\"accessToken\":\"header.payload.signature\","
                                  "\"accessTokenExpiresAt\":\"2026-07-19T00:00:00Z\","
                                  "\"refreshToken\":\"opaque-refresh-token\","
                                  "\"refreshUrl\":\"/api/v1/devices/token:refresh\\u0000/hidden\","
                                  "\"wsUrl\":\"/api/v1/ws/device\"}";
    device_identity_t zero = {0};
    device_identity_t identity = valid_identity();

    TEST_ASSERT_FALSE(device_provisioning_parse_claim_response(
        nul_ws_url, strlen(nul_ws_url), TEST_SERVER_BASE_URL, &identity));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &identity, sizeof(identity));
    identity = valid_identity();
    TEST_ASSERT_FALSE(device_provisioning_parse_claim_response(
        nul_refresh_url, strlen(nul_refresh_url), TEST_SERVER_BASE_URL, &identity));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &identity, sizeof(identity));
}

TEST_CASE("refresh response atomically replaces only access credentials", "[device_credentials]")
{
    const char *response_json = "{\"accessToken\":\"renewed.header.payload\","
                                "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                                "\"wsUrl\":\"/api/v1/ws/device\"}";
    device_identity_t identity = valid_identity();

    TEST_ASSERT_TRUE(device_credentials_parse_refresh_response(response_json, strlen(response_json), &identity));
    TEST_ASSERT_EQUAL_STRING(TEST_SERVER_BASE_URL, identity.server_base_url);
    TEST_ASSERT_EQUAL_STRING("550e8400-e29b-41d4-a716-446655440000", identity.device_id);
    TEST_ASSERT_EQUAL_STRING("renewed.header.payload", identity.access_token);
    TEST_ASSERT_EQUAL_STRING("2026-07-20T00:00:00Z", identity.access_token_expires_at);
    TEST_ASSERT_EQUAL_STRING("opaque-refresh-token", identity.refresh_token);
}

TEST_CASE("refresh response rejects ambiguous data without changing stored credentials", "[device_credentials]")
{
    const char *unexpected_field = "{\"accessToken\":\"renewed.header.payload\","
                                   "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                                   "\"wsUrl\":\"/api/v1/ws/device\",\"unexpected\":true}";
    const char *query_ws_url = "{\"accessToken\":\"renewed.header.payload\","
                               "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                               "\"wsUrl\":\"/api/v1/ws/device?access_token=placeholder\"}";
    const char *invalid_expiry = "{\"accessToken\":\"renewed.header.payload\","
                                 "\"accessTokenExpiresAt\":\"not-an-instant\","
                                 "\"wsUrl\":\"/api/v1/ws/device\"}";
    const char *trailing_data = "{\"accessToken\":\"renewed.header.payload\","
                                "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                                "\"wsUrl\":\"/api/v1/ws/device\"}garbage";
    device_identity_t expected = valid_identity();
    device_identity_t identity = expected;

    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(
        unexpected_field, strlen(unexpected_field), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(query_ws_url, strlen(query_ws_url), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(invalid_expiry, strlen(invalid_expiry), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(trailing_data, strlen(trailing_data), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
}

TEST_CASE("refresh response rejects escaped NUL suffixes without changing identity", "[device_credentials]")
{
    const char *nul_ws_url = "{\"accessToken\":\"renewed.header.payload\","
                             "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                             "\"wsUrl\":\"/api/v1/ws/device\\u0000?hidden=true\"}";
    const char *nul_token = "{\"accessToken\":\"renewed.header.payload\\u0000hidden\","
                            "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\","
                            "\"wsUrl\":\"/api/v1/ws/device\"}";
    const char *nul_expiry = "{\"accessToken\":\"renewed.header.payload\","
                             "\"accessTokenExpiresAt\":\"2026-07-20T00:00:00Z\\u0000hidden\","
                             "\"wsUrl\":\"/api/v1/ws/device\"}";
    device_identity_t expected = valid_identity();
    device_identity_t identity = expected;

    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(nul_ws_url, strlen(nul_ws_url), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(nul_token, strlen(nul_token), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
    TEST_ASSERT_FALSE(device_credentials_parse_refresh_response(nul_expiry, strlen(nul_expiry), &identity));
    TEST_ASSERT_EQUAL_MEMORY(&expected, &identity, sizeof(identity));
}

TEST_CASE("refresh result classification prioritizes repair status and strict success", "[device_credentials]")
{
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_REPAIR_REQUIRED,
                      device_credentials_classify_refresh_result(false, 401, true, false));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_REPAIR_REQUIRED,
                      device_credentials_classify_refresh_result(false, 403, true, false));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_REFRESHED,
                      device_credentials_classify_refresh_result(true, 200, false, true));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_TEMPORARY_FAILURE,
                      device_credentials_classify_refresh_result(false, 200, false, true));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_TEMPORARY_FAILURE,
                      device_credentials_classify_refresh_result(true, 200, true, true));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_TEMPORARY_FAILURE,
                      device_credentials_classify_refresh_result(true, 200, false, false));
    TEST_ASSERT_EQUAL(DEVICE_CREDENTIAL_TEMPORARY_FAILURE,
                      device_credentials_classify_refresh_result(true, 500, false, false));
}

TEST_CASE("websocket credentials are isolated to one authorization header", "[device_transport]")
{
    device_identity_t identity = valid_identity();
    char header[DEVICE_IDENTITY_ACCESS_TOKEN_MAX_LEN + 32] = {0};

    TEST_ASSERT_TRUE(device_transport_build_authorization_header(&identity, header, sizeof(header)));
    TEST_ASSERT_EQUAL_STRING("Authorization: Bearer header.payload.signature\r\n", header);
}

TEST_CASE("USB provisioning accepts one bounded claim request", "[device_provisioning]")
{
    const char *request_json = "{\"type\":\"provision\",\"wifiSsid\":\"home-network\","
                               "\"wifiPassword\":\"not-a-real-password\","
                               "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\","
                               "\"pairingCode\":\"ABCD_123\"}";
    device_provisioning_request_t request = {0};

    TEST_ASSERT_TRUE(device_provisioning_parse_request(request_json, strlen(request_json), &request));
    TEST_ASSERT_EQUAL_STRING("home-network", request.ssid);
    TEST_ASSERT_EQUAL_STRING(TEST_SERVER_BASE_URL, request.server_base_url);
    TEST_ASSERT_EQUAL_STRING("ABCD_123", request.pairing_code);
}

TEST_CASE("strict JSON distinguishes decoded NUL escapes from literal text", "[strict_json]")
{
    const char *short_escape = "\\u00";
    const char *trailing_slash = "text\\";
    const char *decoded_nul = "\\u0000";
    const char *literal_text = "\\\\u0000";

    TEST_ASSERT_FALSE(strict_json_contains_decoded_nul_escape(short_escape, strlen(short_escape)));
    TEST_ASSERT_FALSE(strict_json_contains_decoded_nul_escape(trailing_slash, strlen(trailing_slash)));
    TEST_ASSERT_TRUE(strict_json_contains_decoded_nul_escape(decoded_nul, strlen(decoded_nul)));
    TEST_ASSERT_FALSE(strict_json_contains_decoded_nul_escape(literal_text, strlen(literal_text)));
}

TEST_CASE("strict JSON parse end permits only bounded whitespace", "[strict_json]")
{
    const char *whitespace = "{} \r\n\t";
    const char *garbage = "{}suffix";
    const char *form_feed = "{}\f";
    const char embedded_nul[] = {'{', '}', '\0', 'x'};

    TEST_ASSERT_TRUE(strict_json_has_only_trailing_whitespace(whitespace, strlen(whitespace), whitespace + 2));
    TEST_ASSERT_FALSE(strict_json_has_only_trailing_whitespace(garbage, strlen(garbage), garbage + 2));
    TEST_ASSERT_FALSE(strict_json_has_only_trailing_whitespace(form_feed, strlen(form_feed), form_feed + 2));
    TEST_ASSERT_FALSE(strict_json_has_only_trailing_whitespace(
        embedded_nul, sizeof(embedded_nul), embedded_nul + 2));
    TEST_ASSERT_FALSE(strict_json_has_only_trailing_whitespace(whitespace, strlen(whitespace), NULL));
}

TEST_CASE("USB provisioning preserves a literal NUL escape text password", "[device_provisioning]")
{
    const char *literal_nul_text = "{\"type\":\"provision\",\"wifiSsid\":\"home\","
                                   "\"wifiPassword\":\"literal\\\\u0000text\","
                                   "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\","
                                   "\"pairingCode\":\"ABCD\"}";
    device_provisioning_request_t request = {0};

    TEST_ASSERT_TRUE(device_provisioning_parse_request(literal_nul_text, strlen(literal_nul_text), &request));
    TEST_ASSERT_EQUAL_STRING("literal\\u0000text", request.password);
}

TEST_CASE("USB provisioning rejects insecure and ambiguous requests", "[device_provisioning]")
{
    const char *insecure_url = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                               "\"serverBaseUrl\":\"http://companion.example\",\"pairingCode\":\"ABCD\"}";
    const char *extra_field = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                              "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\",\"pairingCode\":\"ABCD\","
                              "\"unexpected\":true}";
    const char *invalid_code = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                               "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\",\"pairingCode\":\"AB CD\"}";
    device_provisioning_request_t request = {0};

    TEST_ASSERT_FALSE(device_provisioning_parse_request(insecure_url, strlen(insecure_url), &request));
    TEST_ASSERT_FALSE(device_provisioning_parse_request(extra_field, strlen(extra_field), &request));
    TEST_ASSERT_FALSE(device_provisioning_parse_request(invalid_code, strlen(invalid_code), &request));
}

TEST_CASE("USB provisioning rejects escaped NUL suffixes", "[device_provisioning]")
{
    const char *nul_server_url = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                                 "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\\u0000.hidden\","
                                 "\"pairingCode\":\"ABCD\"}";
    device_provisioning_request_t zero = {0};
    device_provisioning_request_t request = {0};

    TEST_ASSERT_FALSE(device_provisioning_parse_request(nul_server_url, strlen(nul_server_url), &request));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &request, sizeof(request));
}

TEST_CASE("USB provisioning fully consumes one JSON object", "[device_provisioning]")
{
    const char *valid = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                        "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\",\"pairingCode\":\"ABCD\"}";
    const char *trailing_garbage = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                                   "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\","
                                   "\"pairingCode\":\"ABCD\"}garbage";
    const char *second_object = "{\"type\":\"provision\",\"wifiSsid\":\"home\",\"wifiPassword\":\"\","
                                "\"serverBaseUrl\":\"" TEST_SERVER_BASE_URL "\","
                                "\"pairingCode\":\"ABCD\"}{}";
    char embedded_nul[512] = {0};
    size_t valid_length = strlen(valid);
    const char *suffix = "hidden";
    memcpy(embedded_nul, valid, valid_length);
    memcpy(embedded_nul + valid_length + 1, suffix, strlen(suffix));
    size_t embedded_length = valid_length + 1 + strlen(suffix);
    device_provisioning_request_t zero = {0};
    device_provisioning_request_t request;

    memset(&request, 0xA5, sizeof(request));
    TEST_ASSERT_FALSE(device_provisioning_parse_request(
        trailing_garbage, strlen(trailing_garbage), &request));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &request, sizeof(request));
    memset(&request, 0xA5, sizeof(request));
    TEST_ASSERT_FALSE(device_provisioning_parse_request(second_object, strlen(second_object), &request));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &request, sizeof(request));
    memset(&request, 0xA5, sizeof(request));
    TEST_ASSERT_FALSE(device_provisioning_parse_request(embedded_nul, embedded_length, &request));
    TEST_ASSERT_EQUAL_MEMORY(&zero, &request, sizeof(request));
}

TEST_CASE("heartbeat matches the device v1 schema", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};

    TEST_ASSERT_EQUAL(ESP_OK,
                      device_protocol_encode_heartbeat(payload, sizeof(payload), 1, 75, -58, "b954a43"));

    cJSON *root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(6, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING("heartbeat", cJSON_GetObjectItemCaseSensitive(root, "type")->valuestring);
    TEST_ASSERT_EQUAL_INT(1, cJSON_GetObjectItemCaseSensitive(root, "sequence")->valueint);
    TEST_ASSERT_EQUAL_INT(75, cJSON_GetObjectItemCaseSensitive(root, "battery_percent")->valueint);
    TEST_ASSERT_EQUAL_INT(-58, cJSON_GetObjectItemCaseSensitive(root, "rssi")->valueint);
    TEST_ASSERT_EQUAL_STRING("motion_disabled",
                             cJSON_GetObjectItemCaseSensitive(root, "safety_state")->valuestring);
    TEST_ASSERT_EQUAL_STRING("b954a43",
                             cJSON_GetObjectItemCaseSensitive(root, "firmware_version")->valuestring);
    cJSON_Delete(root);
}

TEST_CASE("heartbeat rejects invalid sequence and battery values", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};

    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      device_protocol_encode_heartbeat(payload, sizeof(payload), 0, 50, -60, "b954a43"));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      device_protocol_encode_heartbeat(payload, sizeof(payload), 1, -1, -60, "b954a43"));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      device_protocol_encode_heartbeat(payload, sizeof(payload), 1, 101, -60, "b954a43"));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      device_protocol_encode_heartbeat(payload, sizeof(payload), 1, 50, -60, NULL));
}

TEST_CASE("only a well-formed stop_motion command is accepted", "[device_protocol]")
{
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN] = {0};
    const char *valid = "{\"type\":\"stop_motion\",\"command_id\":\"550e8400-e29b-41d4-a716-446655440000\"}";

    TEST_ASSERT_TRUE(device_protocol_parse_stop_motion(valid, strlen(valid), command_id, sizeof(command_id)));
    TEST_ASSERT_EQUAL_STRING("550e8400-e29b-41d4-a716-446655440000", command_id);
}

TEST_CASE("speak_reminder requires the exact fixed command schema", "[device_protocol]")
{
    const char *valid = "{\"type\":\"speak_reminder\",\"command_id\":\"cmd-123\","
                        "\"reminder_id\":\"f20b6177-3f7a-466a-9eae-70120bbf1912\"}";
    const char *extra = "{\"type\":\"speak_reminder\",\"command_id\":\"cmd-123\","
                        "\"reminder_id\":\"f20b6177-3f7a-466a-9eae-70120bbf1912\",\"url\":\"https://evil\"}";
    const char *invalid_id = "{\"type\":\"speak_reminder\",\"command_id\":\"cmd-123\","
                             "\"reminder_id\":\"../voice/turn\"}";
    device_command_t command = {0};

    TEST_ASSERT_TRUE(device_protocol_parse_command(valid, strlen(valid), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_SPEAK_REMINDER, command.type);
    TEST_ASSERT_EQUAL_STRING("cmd-123", command.command_id);
    TEST_ASSERT_EQUAL_STRING("f20b6177-3f7a-466a-9eae-70120bbf1912", command.reminder_id);
    TEST_ASSERT_FALSE(device_protocol_parse_command(extra, strlen(extra), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_NONE, command.type);
    TEST_ASSERT_FALSE(device_protocol_parse_command(invalid_id, strlen(invalid_id), &command));
}

TEST_CASE("voice detection configuration accepts only bounded ordered thresholds", "[device_protocol]")
{
    const char *valid = "{\"type\":\"configure_voice_detection\",\"command_id\":\"cmd-voice\","
                        "\"wake_sensitivity\":\"SENSITIVE\",\"speech_start_threshold\":350,"
                        "\"speech_silence_threshold\":200}";
    const char *invalid_order = "{\"type\":\"configure_voice_detection\",\"command_id\":\"cmd-voice\","
                                "\"wake_sensitivity\":\"NORMAL\",\"speech_start_threshold\":300,"
                                "\"speech_silence_threshold\":300}";
    const char *unknown_sensitivity =
        "{\"type\":\"configure_voice_detection\",\"command_id\":\"cmd-voice\","
        "\"wake_sensitivity\":\"MAXIMUM\",\"speech_start_threshold\":350,"
        "\"speech_silence_threshold\":200}";
    const char *extra = "{\"type\":\"configure_voice_detection\",\"command_id\":\"cmd-voice\","
                        "\"wake_sensitivity\":\"SENSITIVE\",\"speech_start_threshold\":350,"
                        "\"speech_silence_threshold\":200,\"unexpected\":true}";
    device_command_t command = {0};

    TEST_ASSERT_TRUE(device_protocol_parse_command(valid, strlen(valid), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_CONFIGURE_VOICE_DETECTION, command.type);
    TEST_ASSERT_EQUAL(DEVICE_WAKE_SENSITIVITY_SENSITIVE, command.wake_sensitivity);
    TEST_ASSERT_EQUAL_INT(350, command.speech_start_threshold);
    TEST_ASSERT_EQUAL_INT(200, command.speech_silence_threshold);
    TEST_ASSERT_FALSE(device_protocol_parse_command(invalid_order, strlen(invalid_order), &command));
    TEST_ASSERT_FALSE(device_protocol_parse_command(unknown_sensitivity, strlen(unknown_sensitivity), &command));
    TEST_ASSERT_FALSE(device_protocol_parse_command(extra, strlen(extra), &command));
}

TEST_CASE("interaction commands accept only the strict bounded schema", "[device_protocol]")
{
    const char *configuration = "{\"type\":\"configure_interaction\",\"command_id\":\"cmd-ui\","
                                "\"volume_percent\":65,\"night_mode\":true}";
    const char *bad_volume = "{\"type\":\"configure_interaction\",\"command_id\":\"cmd-ui\","
                             "\"volume_percent\":101,\"night_mode\":true}";
    const char *bad_night_mode = "{\"type\":\"configure_interaction\",\"command_id\":\"cmd-ui\","
                                 "\"volume_percent\":50,\"night_mode\":1}";
    const char *stop = "{\"type\":\"stop_audio\",\"command_id\":\"cmd-stop\"}";
    device_command_t command = {0};

    TEST_ASSERT_TRUE(device_protocol_parse_command(configuration, strlen(configuration), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_CONFIGURE_INTERACTION, command.type);
    TEST_ASSERT_EQUAL_INT(65, command.volume_percent);
    TEST_ASSERT_TRUE(command.night_mode);
    TEST_ASSERT_FALSE(device_protocol_parse_command(bad_volume, strlen(bad_volume), &command));
    TEST_ASSERT_FALSE(device_protocol_parse_command(bad_night_mode, strlen(bad_night_mode), &command));
    TEST_ASSERT_TRUE(device_protocol_parse_command(stop, strlen(stop), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_STOP_AUDIO, command.type);
}

TEST_CASE("voice control rejects unsafe detection settings before applying them", "[voice_control]")
{
    TEST_ASSERT_EQUAL(ESP_OK,
                      voice_control_configure(VOICE_WAKE_SENSITIVITY_SENSITIVE, 350, 200));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      voice_control_configure(VOICE_WAKE_SENSITIVITY_SENSITIVE, 99, 50));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      voice_control_configure(VOICE_WAKE_SENSITIVITY_NORMAL, 300, 300));
    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG,
                      voice_control_configure((voice_wake_sensitivity_t)99, 350, 200));
}

TEST_CASE("wake model installation accepts only the strict bounded schema", "[device_protocol]")
{
    const char *sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    char valid[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    snprintf(valid, sizeof(valid),
             "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\","
             "\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\","
             "\"model_name\":\"wn9l_stackchan_custom\",\"sha256\":\"%s\","
             "\"artifact_size\":1048576}", sha256);
    device_command_t command = {0};

    TEST_ASSERT_TRUE(device_protocol_parse_command(valid, strlen(valid), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_INSTALL_WAKE_MODEL, command.type);
    TEST_ASSERT_EQUAL_STRING("cmd-model", command.command_id);
    TEST_ASSERT_EQUAL_STRING("550e8400-e29b-41d4-a716-446655440000", command.wake_model_job_id);
    TEST_ASSERT_EQUAL_STRING("wn9l_stackchan_custom", command.wake_model_name);
    TEST_ASSERT_EQUAL_STRING(sha256, command.wake_model_sha256);
    TEST_ASSERT_EQUAL_INT(1048576, command.wake_model_artifact_size);

    const char *invalid[] = {
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"../bad\",\"model_name\":\"wn9l_stackchan_custom\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"artifact_size\":100}",
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"model_name\":\"WN9L_CUSTOM\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"artifact_size\":100}",
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"model_name\":\"wn9l_custom\",\"sha256\":\"ABCDEF\",\"artifact_size\":100}",
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"model_name\":\"wn9l_custom\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"artifact_size\":0}",
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"model_name\":\"wn9l_custom\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"artifact_size\":1048577}",
        "{\"type\":\"install_wake_model\",\"command_id\":\"cmd-model\",\"job_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"model_name\":\"wn9l_custom\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"artifact_size\":100,\"url\":\"https://evil.example/model\"}",
    };
    for (size_t index = 0; index < sizeof(invalid) / sizeof(invalid[0]); ++index) {
        TEST_ASSERT_FALSE(device_protocol_parse_command(invalid[index], strlen(invalid[index]), &command));
        TEST_ASSERT_EQUAL(DEVICE_COMMAND_NONE, command.type);
    }
}

TEST_CASE("expression package commands accept only fixed origin metadata", "[device_protocol]")
{
    const char *sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    char valid[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    snprintf(valid, sizeof(valid),
             "{\"type\":\"install_expression_pack\",\"command_id\":\"cmd-face\","
             "\"pack_id\":\"550e8400-e29b-41d4-a716-446655440000\","
             "\"sha256\":\"%s\",\"artifact_size\":1572864}", sha256);
    device_command_t command = {0};
    TEST_ASSERT_TRUE(device_protocol_parse_command(valid, strlen(valid), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_INSTALL_EXPRESSION_PACK, command.type);
    TEST_ASSERT_EQUAL_STRING("550e8400-e29b-41d4-a716-446655440000", command.expression_pack_id);
    TEST_ASSERT_EQUAL_STRING(sha256, command.expression_pack_sha256);
    TEST_ASSERT_EQUAL_INT(1572864, command.expression_pack_artifact_size);

    const char *too_large =
        "{\"type\":\"install_expression_pack\",\"command_id\":\"cmd-face\","
        "\"pack_id\":\"550e8400-e29b-41d4-a716-446655440000\","
        "\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\","
        "\"artifact_size\":1572865}";
    const char *external_url =
        "{\"type\":\"install_expression_pack\",\"command_id\":\"cmd-face\","
        "\"pack_id\":\"550e8400-e29b-41d4-a716-446655440000\","
        "\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\","
        "\"artifact_size\":100,\"url\":\"https://evil.example/pack\"}";
    TEST_ASSERT_FALSE(device_protocol_parse_command(too_large, strlen(too_large), &command));
    TEST_ASSERT_FALSE(device_protocol_parse_command(external_url, strlen(external_url), &command));

    const char *clear = "{\"type\":\"clear_expression_pack\",\"command_id\":\"cmd-clear\"}";
    TEST_ASSERT_TRUE(device_protocol_parse_command(clear, strlen(clear), &command));
    TEST_ASSERT_EQUAL(DEVICE_COMMAND_CLEAR_EXPRESSION_PACK, command.type);
}

TEST_CASE("wake model status preserves verified model identity", "[device_protocol]")
{
    const char *sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    TEST_ASSERT_EQUAL(ESP_OK, device_protocol_encode_wake_model_status(
                                  payload, sizeof(payload), 10,
                                  "550e8400-e29b-41d4-a716-446655440000", "ROLLED_BACK",
                                  "wn9l_stackchan_custom", sha256));

    cJSON *root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(6, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING("wake_model_status",
                             cJSON_GetObjectItemCaseSensitive(root, "type")->valuestring);
    TEST_ASSERT_EQUAL_INT(10, cJSON_GetObjectItemCaseSensitive(root, "sequence")->valueint);
    TEST_ASSERT_EQUAL_STRING("ROLLED_BACK",
                             cJSON_GetObjectItemCaseSensitive(root, "status")->valuestring);
    TEST_ASSERT_EQUAL_STRING("wn9l_stackchan_custom",
                             cJSON_GetObjectItemCaseSensitive(root, "model_name")->valuestring);
    TEST_ASSERT_EQUAL_STRING(sha256,
                             cJSON_GetObjectItemCaseSensitive(root, "sha256")->valuestring);
    cJSON_Delete(root);

    TEST_ASSERT_EQUAL(ESP_ERR_INVALID_ARG, device_protocol_encode_wake_model_status(
                                                   payload, sizeof(payload), 10,
                                                   "550e8400-e29b-41d4-a716-446655440000", "FAILED",
                                                   "wn9l_stackchan_custom", sha256));
}

TEST_CASE("wake word selection uses exact packaged names and an explicit fallback", "[wake_word]")
{
    const char *models[] = {
        "mn7_cn",
        WAKE_WORD_DEFAULT_MODEL_NAME,
        "wn9l_custom_companion_tts3",
    };

    wake_word_model_selection_t custom = wake_word_model_select(
        models, 3, "wn9l_custom_companion_tts3", WAKE_WORD_DEFAULT_MODEL_NAME);
    TEST_ASSERT_EQUAL_STRING("wn9l_custom_companion_tts3", custom.name);
    TEST_ASSERT_FALSE(custom.used_fallback);

    wake_word_model_selection_t missing = wake_word_model_select(
        models, 3, "wn9l_missing_tts3", WAKE_WORD_DEFAULT_MODEL_NAME);
    TEST_ASSERT_EQUAL_STRING(WAKE_WORD_DEFAULT_MODEL_NAME, missing.name);
    TEST_ASSERT_TRUE(missing.used_fallback);

    wake_word_model_selection_t invalid = wake_word_model_select(
        models, 3, "../mn7_cn", WAKE_WORD_DEFAULT_MODEL_NAME);
    TEST_ASSERT_EQUAL_STRING(WAKE_WORD_DEFAULT_MODEL_NAME, invalid.name);
    TEST_ASSERT_TRUE(invalid.used_fallback);

    wake_word_model_selection_t missing_version = wake_word_model_select(
        models, 3, "wn_custom_companion", WAKE_WORD_DEFAULT_MODEL_NAME);
    TEST_ASSERT_EQUAL_STRING(WAKE_WORD_DEFAULT_MODEL_NAME, missing_version.name);
    TEST_ASSERT_TRUE(missing_version.used_fallback);
}

TEST_CASE("wake word selection never substitutes an arbitrary speech model", "[wake_word]")
{
    const char *models[] = {"mn7_cn", "wn9l_other_tts3"};

    wake_word_model_selection_t selection = wake_word_model_select(
        models, 2, "wn9l_missing_tts3", WAKE_WORD_DEFAULT_MODEL_NAME);
    TEST_ASSERT_NULL(selection.name);
    TEST_ASSERT_FALSE(selection.used_fallback);
    TEST_ASSERT_NULL(wake_word_model_find(models, 2, "WN9L_OTHER_TTS3"));
}

TEST_CASE("command acknowledgement preserves the playback result boolean", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};

    TEST_ASSERT_EQUAL(ESP_OK,
                      device_protocol_encode_command_ack(payload, sizeof(payload), 9, "cmd-123", false));
    cJSON *root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(4, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING("command_ack", cJSON_GetObjectItemCaseSensitive(root, "type")->valuestring);
    TEST_ASSERT_EQUAL_INT(9, cJSON_GetObjectItemCaseSensitive(root, "sequence")->valueint);
    TEST_ASSERT_EQUAL_STRING("cmd-123", cJSON_GetObjectItemCaseSensitive(root, "command_id")->valuestring);
    TEST_ASSERT_FALSE(cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(root, "accepted")));
    cJSON_Delete(root);
}

TEST_CASE("command acknowledgement adds a strict result only for rejection", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    TEST_ASSERT_EQUAL(
        ESP_OK,
        device_protocol_encode_command_ack_with_result(
            payload,
            sizeof(payload),
            10,
            "cmd-cancelled",
            false,
            DEVICE_COMMAND_RESULT_CANCELLED));
    cJSON *root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(5, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING(
        "cancelled", cJSON_GetObjectItemCaseSensitive(root, "result")->valuestring);
    cJSON_Delete(root);

    TEST_ASSERT_EQUAL(
        ESP_ERR_INVALID_ARG,
        device_protocol_encode_command_ack_with_result(
            payload,
            sizeof(payload),
            11,
            "cmd-invalid",
            true,
            DEVICE_COMMAND_RESULT_FAILED));
}

TEST_CASE("voice turn diagnostics expose only bounded stage metadata", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    const char *turn_id = "550e8400-e29b-41d4-a716-446655440000";

    TEST_ASSERT_EQUAL(
        ESP_OK,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            10,
            turn_id,
            DEVICE_VOICE_STAGE_LISTENING,
            120,
            DEVICE_VOICE_FAILURE_NONE));
    cJSON *root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(5, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING("voice_turn_stage", cJSON_GetObjectItemCaseSensitive(root, "type")->valuestring);
    TEST_ASSERT_EQUAL_STRING(turn_id, cJSON_GetObjectItemCaseSensitive(root, "turn_id")->valuestring);
    TEST_ASSERT_EQUAL_STRING("LISTENING", cJSON_GetObjectItemCaseSensitive(root, "stage")->valuestring);
    TEST_ASSERT_EQUAL_INT(120, cJSON_GetObjectItemCaseSensitive(root, "elapsed_ms")->valueint);
    TEST_ASSERT_NULL(cJSON_GetObjectItemCaseSensitive(root, "failure_code"));
    cJSON_Delete(root);

    TEST_ASSERT_EQUAL(
        ESP_OK,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            12,
            turn_id,
            DEVICE_VOICE_STAGE_TOUCH_STARTED,
            0,
            DEVICE_VOICE_FAILURE_NONE));
    root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_STRING(
        "TOUCH_STARTED", cJSON_GetObjectItemCaseSensitive(root, "stage")->valuestring);
    cJSON_Delete(root);

    TEST_ASSERT_EQUAL(
        ESP_OK,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            13,
            turn_id,
            DEVICE_VOICE_STAGE_CANCELLED,
            300,
            DEVICE_VOICE_FAILURE_NONE));
    root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_STRING(
        "CANCELLED", cJSON_GetObjectItemCaseSensitive(root, "stage")->valuestring);
    TEST_ASSERT_NULL(cJSON_GetObjectItemCaseSensitive(root, "failure_code"));
    cJSON_Delete(root);

    TEST_ASSERT_EQUAL(
        ESP_OK,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            11,
            turn_id,
            DEVICE_VOICE_STAGE_FAILED,
            250,
            DEVICE_VOICE_FAILURE_NO_SPEECH));
    root = cJSON_Parse(payload);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_UINT(6, cJSON_GetArraySize(root));
    TEST_ASSERT_EQUAL_STRING("NO_SPEECH", cJSON_GetObjectItemCaseSensitive(root, "failure_code")->valuestring);
    cJSON_Delete(root);
}

TEST_CASE("voice turn diagnostics reject mismatched failures and invalid identifiers", "[device_protocol]")
{
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    const char *turn_id = "550e8400-e29b-41d4-a716-446655440000";

    TEST_ASSERT_EQUAL(
        ESP_ERR_INVALID_ARG,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            12,
            turn_id,
            DEVICE_VOICE_STAGE_LISTENING,
            20,
            DEVICE_VOICE_FAILURE_NO_SPEECH));
    TEST_ASSERT_EQUAL(
        ESP_ERR_INVALID_ARG,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            12,
            "not-a-turn-id",
            DEVICE_VOICE_STAGE_FAILED,
            20,
            DEVICE_VOICE_FAILURE_INTERNAL_ERROR));
    TEST_ASSERT_EQUAL(
        ESP_ERR_INVALID_ARG,
        device_protocol_encode_voice_turn_stage(
            payload,
            sizeof(payload),
            12,
            turn_id,
            DEVICE_VOICE_STAGE_FAILED,
            300001,
            DEVICE_VOICE_FAILURE_INTERNAL_ERROR));
}

TEST_CASE("SCV1 frame exposes strict metadata and validated WAV audio", "[voice_protocol]")
{
    int16_t samples[] = {0, 100, -100, 0};
    uint8_t wav[AUDIO_WAV_HEADER_SIZE + sizeof(samples)] = {0};
    size_t wav_size = 0;
    TEST_ASSERT_EQUAL(ESP_OK, audio_wav_build_pcm16_mono(
                                  wav, sizeof(wav), samples, 4, 16000, &wav_size));

    const char *metadata = "{\"transcript\":\"hello\",\"reply\":\"world\"}";
    size_t metadata_size = strlen(metadata);
    uint8_t frame[8 + 128 + sizeof(wav)] = {0};
    memcpy(frame, "SCV1", 4);
    frame[4] = (uint8_t)(metadata_size >> 24);
    frame[5] = (uint8_t)(metadata_size >> 16);
    frame[6] = (uint8_t)(metadata_size >> 8);
    frame[7] = (uint8_t)metadata_size;
    memcpy(frame + 8, metadata, metadata_size);
    memcpy(frame + 8 + metadata_size, wav, wav_size);

    voice_turn_response_t response = {0};
    TEST_ASSERT_TRUE(voice_protocol_parse_turn_response(
        frame, 8 + metadata_size + wav_size, &response));
    TEST_ASSERT_EQUAL_STRING("hello", response.transcript);
    TEST_ASSERT_EQUAL_STRING("world", response.reply);
    TEST_ASSERT_EQUAL_UINT(wav_size, response.wav_size);

    frame[0] = 'X';
    TEST_ASSERT_FALSE(voice_protocol_parse_turn_response(
        frame, 8 + metadata_size + wav_size, &response));
}

TEST_CASE("WAV parsing rejects a RIFF size beyond the supplied buffer", "[audio_wav]")
{
    int16_t samples[] = {0, 100, -100, 0};
    uint8_t wav[AUDIO_WAV_HEADER_SIZE + sizeof(samples)] = {0};
    size_t wav_size = 0;
    TEST_ASSERT_EQUAL(ESP_OK, audio_wav_build_pcm16_mono(
                                  wav, sizeof(wav), samples, 4, 16000, &wav_size));
    audio_wav_view_t view = {0};
    TEST_ASSERT_TRUE(audio_wav_parse(wav, wav_size, &view));

    wav[4] = 0xff;
    wav[5] = 0xff;
    wav[6] = 0xff;
    wav[7] = 0x7f;
    TEST_ASSERT_FALSE(audio_wav_parse(wav, wav_size, &view));
}

TEST_CASE("unknown or malformed commands do not produce an action", "[device_protocol]")
{
    char command_id[DEVICE_PROTOCOL_COMMAND_ID_MAX_LEN] = "unchanged";
    char too_long[DEVICE_PROTOCOL_MAX_MESSAGE_LEN] = {0};
    const char *unknown = "{\"type\":\"move\",\"command_id\":\"1\"}";
    const char *blank = "{\"type\":\"stop_motion\",\"command_id\":\"   \"}";
    const char *extra = "{\"type\":\"stop_motion\",\"command_id\":\"1\",\"accepted\":true}";
    const char *malformed = "{\"type\":\"stop_motion\"";

    memset(too_long, 'x', sizeof(too_long));

    TEST_ASSERT_FALSE(device_protocol_parse_stop_motion(unknown, strlen(unknown), command_id, sizeof(command_id)));
    TEST_ASSERT_EQUAL_STRING("", command_id);
    TEST_ASSERT_FALSE(device_protocol_parse_stop_motion(blank, strlen(blank), command_id, sizeof(command_id)));
    TEST_ASSERT_FALSE(device_protocol_parse_stop_motion(extra, strlen(extra), command_id, sizeof(command_id)));
    TEST_ASSERT_FALSE(device_protocol_parse_stop_motion(malformed, strlen(malformed), command_id, sizeof(command_id)));
    TEST_ASSERT_FALSE(device_protocol_parse_stop_motion(too_long, sizeof(too_long), command_id,
                                                        sizeof(command_id)));
}
