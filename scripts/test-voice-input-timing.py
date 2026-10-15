"""Exercise real capture/codec boundaries with deterministic local hardware stubs.

These are software observations, not acoustic latency or physical touch samples.
No recording, authenticated request or connected device is used.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
firmware = root / "firmware"
(root / ".tools").mkdir(parents=True, exist_ok=True)


def function(source, name):
    match = re.search(r'(?m)^[^\n;]*\b' + name + r'\([^;]*?\)\s*\{', source)
    if not match:
        raise RuntimeError("Missing production function " + name)
    opening = source.index("{", match.start())
    depth = 1
    for end in range(opening + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[match.start():end + 1]
    raise RuntimeError("Unclosed production function " + name)


capture_source = (firmware / "main/voice_control.c").read_text(encoding="utf-8")
hardware_source = (firmware / "main/companion_hardware.cpp").read_text(encoding="utf-8")
protocol_source = (firmware / "main/device_protocol.c").read_text(encoding="utf-8")
# The response is not finalized before endpoint/state metadata is queued locally.
run_turn = function(capture_source, "run_voice_turn")
assert run_turn.index("COMPANION_FACE_PROCESSING") < run_turn.index("device_transport_report_voice_capture")
assert run_turn.index("device_transport_report_voice_capture") < run_turn.index("wait_live_capture_upload")
assert "CONTINUOUS_CONVERSATION_MAX_DURATION_MS" not in run_turn
cancellation = function(capture_source, "request_turn_cancellation")
assert cancellation.index("companion_hardware_request_playback_stop") < cancellation.index("voice_service_cancel_active_turn")
# LISTENING_RESUMED may only follow successful microphone sampling.
wake_task = function(capture_source, "voice_task")
assert wake_task.index("companion_hardware_record_pcm") < wake_task.index("report_input_ready(DEVICE_VOICE_STAGE_LISTENING_RESUMED)")
# UI rendering consumes the copied notice under its existing board lock, outside codec feeding.
ui_task = function(hardware_source, "ui_task")
assert ui_task.index("apply_pending_playback_locked") > ui_task.rindex("take_mutex(s_board_mutex")

capture = r'''
#include <assert.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include "voice_capture_policy.h"
typedef int esp_err_t;
#define ESP_OK 0
#define ESP_ERR_INVALID_ARG 1
#define ESP_ERR_NOT_FOUND 2
#define ESP_ERR_NOT_FINISHED 3
#define ESP_ERR_INVALID_STATE 4
#define VOICE_CAPTURE_WINDOW_SAMPLES 1600
#define VOICE_SAMPLE_RATE 16000
#define CONTINUOUS_CONVERSATION_MAX_DURATION_MS 120000
#define DEVICE_VOICE_STAGE_LISTENING_RESUMED 1
#define DEVICE_VOICE_STAGE_MANUAL_INPUT_READY 2
typedef struct { uint32_t speech_start_threshold,speech_silence_threshold; } voice_detection_settings_t;
typedef struct { int dummy; } live_capture_upload_t;
static int64_t now;
static unsigned reads,starts,publishes,resumed;
static unsigned silent_until,stop_after,pause_from,pause_to;
static bool held,cancelled,online,manual,mic_fail;
static int64_t esp_timer_get_time(void) { return now; }
static uint32_t turn_elapsed_ms(int64_t started) {return (uint32_t)((now-started)/1000);}
static bool device_ui_is_modal(void) { return false; }
static bool device_transport_is_server_connected(void) { return online; }
static bool cancellation_requested(void) { return cancelled; }
static bool capture_submit_requested(void) { return false; }
static bool press_to_talk_held(void) { return held; }
static bool automatic_wake_enabled(void) { return !manual; }
static void report_input_ready(int stage) { assert(reads>0); assert(stage==(manual?2:1)); ++resumed; }
static esp_err_t companion_hardware_record_pcm(int16_t *window,size_t count,uint32_t rate) {
    assert(count==1600 && rate==16000);
    if(mic_fail) return ESP_ERR_INVALID_STATE;
    ++reads; now+=100000;
    int16_t energy=reads<=silent_until || (reads>=pause_from && reads<=pause_to) ? 0:500;
    for(size_t i=0;i<count;++i) window[i]=energy;
    if(stop_after && reads>=stop_after) held=false;
    return ESP_OK;
}
static uint32_t mean_absolute_energy(const int16_t *samples,size_t count) {(void)count;return samples[0];}
static void start_live_capture_upload(live_capture_upload_t *upload,const int16_t *samples) {(void)upload;(void)samples;++starts;}
static void publish_live_capture_upload(live_capture_upload_t *upload,size_t count,bool finished) {(void)upload;(void)count;(void)finished;++publishes;}
SOURCE
static void reset(void) {
    now=0;reads=starts=publishes=resumed=0;silent_until=stop_after=pause_from=pause_to=0;
    held=online=true;cancelled=manual=mic_fail=false;
}
int main(void) {
    int16_t samples[128000]; size_t count; uint32_t energy;
    int64_t endpoint; bool limit; live_capture_upload_t upload={0};
    voice_detection_settings_t settings={350,200};
    reset(); silent_until=100;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,false,3,0,&upload,&endpoint,&limit)==ESP_ERR_NOT_FOUND);
    assert(reads==30 && !starts && !publishes && !limit && endpoint==0);
    reset(); silent_until=28; pause_from=35;pause_to=40;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,false,3,0,&upload,&endpoint,&limit)==ESP_OK);
    assert(starts==1 && limit && count==128000 && reads>80 && resumed==reads);
    reset();pause_from=15;pause_to=100;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,false,8,0,&upload,&endpoint,&limit)==ESP_OK);
    assert(reads==22 && !limit && endpoint==2200000 && count==24000);
    reset();manual=true;stop_after=4;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,true,false,8,0,&upload,&endpoint,&limit)==ESP_OK);
    assert(reads==4 && count==6400 && !limit && endpoint==400000 && resumed==4);
    reset();now=119800000;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,true,8,0,&upload,&endpoint,&limit)==ESP_OK);
    assert(now>120000000 && count==128000 && limit);
    reset();now=120000000;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,true,8,0,&upload,&endpoint,&limit)==ESP_ERR_NOT_FOUND && reads==0);
    reset();mic_fail=true;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,false,8,0,&upload,&endpoint,&limit)==ESP_ERR_INVALID_STATE && resumed==0 && starts==0);
    reset();cancelled=true;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,false,8,0,&upload,&endpoint,&limit)==ESP_ERR_NOT_FINISHED && reads==0);
    reset();online=false;
    assert(capture_user_speech(samples,128000,&count,&settings,&energy,false,true,8,0,&upload,&endpoint,&limit)==ESP_ERR_INVALID_STATE && reads==0);
    puts("PASS actual capture: empty/600ms pause/800ms endpoint/8s limit/PTT/recovery; 120s only gates new speech; resume follows real sampling");
}
'''.replace("SOURCE", function(capture_source, "capture_user_speech"))

hardware = r'''
#include <cassert>
#include <cstdint>
#include <cstddef>
#include <cstdio>
#include <cstring>
typedef int esp_err_t;
typedef struct { uint32_t sample_rate; unsigned channels,bits_per_sample; const uint8_t *data; size_t data_size; } audio_wav_view_t;
typedef struct {uint32_t sample_rate;unsigned channel,bits_per_sample,mclk_multiple;} esp_codec_dev_sample_info_t;
#define ESP_OK 0
#define ESP_ERR_INVALID_ARG 1
#define ESP_ERR_TIMEOUT 2
#define ESP_ERR_INVALID_STATE 3
#define portMAX_DELAY 0
#define I2S_MCLK_MULTIPLE_384 384
#define AUDIO_IO_CHUNK_SIZE 2048
#define COMPANION_EXPRESSION_FPS_ADAPTIVE 1
#define COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN 4
#define ESP_LOGE(...) do {} while(0)
#define taskENTER_CRITICAL(...) do {} while(0)
#define taskEXIT_CRITICAL(...) do {} while(0)
static bool s_initialized=true,stop,cancel_flag,fail_write,fail_open,cancel_on_lock,cancel_on_open,cancel_on_write;
static unsigned writes,observed,open_count;
static unsigned board_lock_requests,measured_starts,playing_phases;
typedef int companion_face_state_t;
#define pdMS_TO_TICKS(ms) (ms)
static int s_audio_mutex=1,s_board_mutex=2,s_speaker_codec,s_volume_percent=50,s_expression_fps_mode,s_expression_min_fps;
static bool state_lock_failed,s_screensaver;
static int s_face_state,s_expression_engine;
static bool engine_active,engine_started;
static uint32_t engine_started_ms;
static const char *engine_turn="550e8400-e29b-41d4-a716-446655440000";
typedef struct {char turn_id[37];int64_t written_us;bool (*should_cancel)(void);bool present;} pending_playback_notice_t;
static pending_playback_notice_t s_pending_playback_notice;
typedef struct {const char *turn_id;int64_t started_us;bool playback_started;} streaming_turn_context_t;
#define COMPANION_FACE_SPEAKING 3
#define TOUCH_INTERACTION_PLAYING 4
#define DEVICE_VOICE_STAGE_PLAYBACK_STARTED 5
#define DEVICE_VOICE_FAILURE_NONE 0
static int64_t s_face_started_us,s_next_face_frame_us;
static struct {unsigned audio_underruns,target_fps,degrade_reason;} s_expression_diagnostics;
static int64_t now,observed_at;
static bool audio_wav_parse(const uint8_t *wav,size_t size,audio_wav_view_t *view) {
    if(!wav || !size)return false;
    *view={16000,1,16,wav,size};return true;
}
static bool take_mutex(int mutex,unsigned) {if(mutex==s_board_mutex){++board_lock_requests;return !state_lock_failed;}if(cancel_on_lock)cancel_flag=true;return true;}
static void xSemaphoreGive(int) {}
static void clear_playback_stop_request(void) {stop=false;}
static void set_audio_playback_active(bool) {}
static int close_microphone(void) {return ESP_OK;}
static int open_microphone(void) {return ESP_OK;}
static int esp_codec_dev_set_out_vol(int,int) {return ESP_OK;}
static int esp_codec_dev_open(int,esp_codec_dev_sample_info_t*) {++open_count;if(cancel_on_open)cancel_flag=true;return fail_open?ESP_ERR_INVALID_STATE:ESP_OK;}
static int esp_codec_dev_write(int,void*,size_t) {++writes;now+=64000;if(cancel_on_write)cancel_flag=true;return fail_write?ESP_ERR_INVALID_STATE:ESP_OK;}
static int esp_codec_dev_close(int) {return ESP_OK;}
static bool playback_stop_requested(void) {return stop;}
static bool should_cancel(void) {return cancel_flag;}
static bool cancellation_requested(void) {return should_cancel();}
static int64_t esp_timer_get_time(void) {return now;}
static void set_last_activity_now(void) {}
static void restore_expression_fps_after_screensaver(void) {}
static void set_display_brightness(int) {}
static int active_brightness_percent(void) {return 50;}
static int visible_state_locked(void) {return s_face_state;}
static void companion_expression_engine_set_system(int*,int,uint32_t) {}
static bool expression_pack_is_active(void) {return false;}
static void draw_face_locked(int) {}
static bool companion_expression_engine_turn_playback_started(int*,const char *turn,uint32_t ms) {
    if(!engine_active || engine_started || strcmp(turn,engine_turn)!=0) return false;
    engine_started=true;engine_started_ms=ms;return true;
}
static void set_interaction_phase(int phase) {assert(phase==TOUCH_INTERACTION_PLAYING);++playing_phases;}
static bool device_transport_report_voice_turn(int stage,const char*,uint32_t ms,int failure) {
    assert(stage==DEVICE_VOICE_STAGE_PLAYBACK_STARTED && ms==64 && failure==DEVICE_VOICE_FAILURE_NONE);
    ++measured_starts;return true;
}
static void observe(int64_t stamp,void*) {assert(writes==1);++observed;observed_at=stamp;}
SOURCE
static void reset(void) {
    stop=cancel_flag=fail_write=fail_open=cancel_on_lock=cancel_on_open=cancel_on_write=false;
    writes=observed=open_count=board_lock_requests=measured_starts=playing_phases=0;now=observed_at=0;
    engine_active=true;engine_started=false;engine_started_ms=0;state_lock_failed=false;s_face_state=2;
    s_pending_playback_notice={};engine_turn="550e8400-e29b-41d4-a716-446655440000";
}
int main(void) {
    uint8_t wav[4096]={0};bool cancelled;
    reset();assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)==ESP_OK);
    assert(writes==2 && observed==1 && observed_at==64000 && !cancelled);
    reset();fail_open=true;assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)!=ESP_OK && !writes && !observed);
    reset();fail_write=true;assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)!=ESP_OK && !observed);
    reset();cancel_on_lock=true;assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)==ESP_OK && cancelled && !writes && !open_count && !observed);
    reset();cancel_on_open=true;assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)==ESP_OK && cancelled && !writes && !observed);
    reset();cancel_on_write=true;assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,observe,nullptr)==ESP_OK && cancelled && writes==1 && !observed);
    s_face_state=0;s_initialized=false;assert(!companion_hardware_try_set_state(2) && s_face_state==0);
    s_initialized=true;state_lock_failed=true;assert(!companion_hardware_try_set_state(2) && s_face_state==0);
    state_lock_failed=false;assert(companion_hardware_try_set_state(2) && s_face_state==2);
    reset();state_lock_failed=true;
    streaming_turn_context_t context={engine_turn,0,false};
    assert(companion_hardware_play_wav_observed(wav,sizeof(wav),&cancelled,should_cancel,report_playback_written,&context)==ESP_OK);
    assert(writes==2 && measured_starts==1 && playing_phases==1 && board_lock_requests==0 && now==128000);
    assert(s_face_state==2 && !engine_started && s_pending_playback_notice.present);
    state_lock_failed=false;
    assert(apply_pending_playback_locked() && s_face_state==3 && engine_started_ms==64);
    assert(!apply_pending_playback_locked());
    reset();companion_hardware_defer_turn_playback_started(engine_turn,64000,should_cancel);cancel_flag=true;
    assert(!apply_pending_playback_locked() && s_face_state==2 && !engine_started);
    reset();companion_hardware_defer_turn_playback_started(engine_turn,64000,should_cancel);engine_active=false;
    assert(!apply_pending_playback_locked() && s_face_state==2 && !engine_started);
    reset();
    char original_turn[37];memcpy(original_turn,engine_turn,sizeof(original_turn));
    companion_hardware_defer_turn_playback_started(original_turn,64000,should_cancel);
    original_turn[0]='6';
    assert(apply_pending_playback_locked() && s_face_state==3);
    reset();companion_hardware_defer_turn_playback_started(engine_turn,64000,should_cancel);
    engine_turn="650e8400-e29b-41d4-a716-446655440000";
    assert(!apply_pending_playback_locked() && s_face_state==2 && !engine_started);
    puts("PASS actual codec: start after first successful write only; lock/open/write failures and late cancellation cannot create a start event");
    puts("PASS actual local state: uninitialized/lock timeout cannot create local feedback; accepted state reports success");
    puts("PASS actual first-write callback: no board/display wait during PCM feeding; copied UI notices reject cancellation/ended/old turns");
}
'''.replace("SOURCE", "\n".join((function(hardware_source, "companion_hardware_play_wav_observed"),
    function(hardware_source, "companion_hardware_try_set_state"),
    function(hardware_source, "companion_hardware_defer_turn_playback_started"),
    function(hardware_source, "apply_pending_playback_locked"),
    function(capture_source, "report_playback_written"))))

protocol = r'''
#include <assert.h>
#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include "device_protocol.h"
#include "cJSON.h"
SOURCE
int main(void) {
    char payload[DEVICE_PROTOCOL_MAX_MESSAGE_LEN];
    const char *turn="550e8400-e29b-41d4-a716-446655440000";
    device_voice_capture_measurement_t data={.present=true,.feedback_present=true,.endpoint_to_local_feedback_ms=25,.capture_limit_reached=true};
    assert(device_protocol_encode_voice_turn_stage_measured(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_SPEECH_CAPTURED,8000,DEVICE_VOICE_FAILURE_NONE,&data)==ESP_OK);
    cJSON *json=cJSON_Parse(payload);assert(json);
    assert(cJSON_GetArraySize(json)==8);
    assert(strcmp(cJSON_GetObjectItemCaseSensitive(json,"measurementSource")->valuestring,"DEVICE_MONOTONIC")==0);
    assert(cJSON_GetObjectItemCaseSensitive(json,"endpointToLocalFeedbackMs")->valueint==25);
    assert(cJSON_IsTrue(cJSON_GetObjectItemCaseSensitive(json,"captureLimitReached")));
    assert(!cJSON_GetObjectItemCaseSensitive(json,"endpointToFirstSoundMs"));
    cJSON_Delete(json);
    data.feedback_present=false;
    assert(device_protocol_encode_voice_turn_stage_measured(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_SPEECH_CAPTURED,8000,DEVICE_VOICE_FAILURE_NONE,&data)==ESP_OK);
    json=cJSON_Parse(payload);assert(cJSON_GetArraySize(json)==7 && !cJSON_GetObjectItemCaseSensitive(json,"endpointToLocalFeedbackMs"));cJSON_Delete(json);
    data.feedback_present=true;
    data.present=false;
    assert(device_protocol_encode_voice_turn_stage_measured(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_PLAYBACK_STARTED,10000,DEVICE_VOICE_FAILURE_NONE,&data)==ESP_OK);
    json=cJSON_Parse(payload);assert(cJSON_GetArraySize(json)==6 && !cJSON_GetObjectItemCaseSensitive(json,"captureLimitReached"));cJSON_Delete(json);
    assert(device_protocol_encode_voice_turn_stage(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_PLAYBACK_STARTED,10000,DEVICE_VOICE_FAILURE_NONE)==ESP_OK);
    json=cJSON_Parse(payload);assert(cJSON_GetArraySize(json)==5 && !cJSON_GetObjectItemCaseSensitive(json,"measurementSource"));cJSON_Delete(json);
    data.present=true;
    assert(device_protocol_encode_voice_turn_stage_measured(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_PLAYBACK_STARTED,10000,DEVICE_VOICE_FAILURE_NONE,&data)==ESP_ERR_INVALID_ARG);
    data.endpoint_to_local_feedback_ms=300001;
    assert(device_protocol_encode_voice_turn_stage_measured(payload,sizeof(payload),1,turn,DEVICE_VOICE_STAGE_SPEECH_CAPTURED,10000,DEVICE_VOICE_FAILURE_NONE,&data)==ESP_ERR_INVALID_ARG);
    puts("PASS real encoder: bounded optional metadata, legacy field absence, no acoustic metric, wrong stage/range rejected");
}
'''.replace("SOURCE", "\n".join(function(protocol_source, name) for name in (
    "is_valid_uuid", "voice_stage_name", "voice_failure_name", "print_json",
    "device_protocol_encode_voice_turn_stage", "device_protocol_encode_voice_turn_stage_measured")))

with tempfile.TemporaryDirectory(prefix="stackchan-input-timing-", dir=root / ".tools") as directory:
    path = Path(directory)
    (path / "capture.c").write_text(capture, encoding="utf-8")
    (path / "hardware.cpp").write_text(hardware, encoding="utf-8")
    (path / "protocol.c").write_text(protocol, encoding="utf-8")
    command = (
        "gcc -std=gnu11 -Wall -Wextra -Werror -I/project/main "
        "/test/capture.c /project/main/voice_capture_policy.c -o /tmp/capture && /tmp/capture && "
        "g++ -std=gnu++17 -Wall -Wextra -Werror /test/hardware.cpp -o /tmp/hardware && /tmp/hardware && "
        "gcc -std=gnu11 -Wall -Wextra -Werror -I/project/main -I/project/test/host/stubs "
        "-I/project/managed_components/espressif__cjson/cJSON /test/protocol.c "
        "/project/managed_components/espressif__cjson/cJSON/cJSON.c -lm -o /tmp/protocol && /tmp/protocol"
    )
    subprocess.run([
        "docker", "run", "--rm", "--network", "none",
        "--mount", f"type=bind,source={path},target=/test,readonly",
        "--mount", f"type=bind,source={firmware},target=/project,readonly",
        "gcc:14.2.0", "sh", "-c", command,
    ], check=True)
