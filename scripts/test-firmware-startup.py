"""Exercise the real application bootstrap and deferred voice entry without hardware."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
app=(root/'firmware/main/app_main.c').read_text(encoding='utf-8')
app='\n'.join(line for line in app.splitlines() if not line.startswith('#include'))
voice=(root/'firmware/main/voice_control.c').read_text(encoding='utf-8')
a=voice.index('static void voice_task(void *argument)\n{')
b=voice.index('    const char *partition_label',a)
entry=voice[a:b].replace('voice_task','startup_voice_entry',1)+'    wake_started = true;\n}\n'
harness=r'''
#include <stdbool.h>
#include <stdio.h>
#include <stdarg.h>
#include <assert.h>
#include <setjmp.h>
#define CONFIG_STACKCHAN_PROTOCOL_TESTS 0
#define CONFIG_STACKCHAN_LAN_HTTP_MODE 1
#define ESP_OK 0
#define ESP_ERR_NO_MEM 1
#define ESP_ERR_INVALID_STATE 2
#define MALLOC_CAP_INTERNAL 1
#define MALLOC_CAP_8BIT 2
#define pdTRUE 1
#define portMAX_DELAY 0xffffffffu
#define ESP_LOGI(...) fake_log(__VA_ARGS__)
#define ESP_LOGW(...) fake_log(__VA_ARGS__)
#define ESP_LOGE(...) fake_log(__VA_ARGS__)
typedef int esp_err_t;
static int failure, step, wifi_step, ui_step, activation_step, confirms, restarts;
static bool wake_started, voice_waiting, voice_ready, provision_ready;
static jmp_buf wait_jump;
static void fake_log(const char *tag,const char *fmt,...) {(void)tag;(void)fmt;}
static const char *esp_err_to_name(int code) {(void)code;return "fixture";}
static unsigned uxTaskGetStackHighWaterMark(void *task) {(void)task;return 5000;}
static unsigned heap_caps_get_free_size(int caps) {(void)caps;return 160000;}
static unsigned ulTaskNotifyTake(int clear,unsigned timeout) {
 assert(clear==pdTRUE && timeout==portMAX_DELAY);
 if(!voice_ready) {voice_waiting=true;longjmp(wait_jump,1);} return 1;
}
ENTRY
void safety_state_init(void) {}
const char *safety_state_name(int state) {(void)state;return "disabled";}
int safety_state_current(void) {return 0;}
void safety_state_stop_motion(void) {}
int companion_hardware_init(void) {return ESP_OK;}
int device_identity_init_encrypted_nvs(void) {return ESP_OK;}
int body_hardware_init(void) {return ESP_OK;}
int expression_pack_init(void) {return ESP_OK;}
void companion_hardware_refresh_face(void) {}
int firmware_ota_init(void) {return ESP_OK;}
int firmware_ota_start_health_guard(void) {return ESP_OK;}
bool firmware_ota_is_pending(void) {return true;}
int firmware_ota_confirm_active(void) {++confirms;return ESP_OK;}
void esp_restart(void) {++restarts;}
int wake_model_ota_init(void) {return ESP_OK;}
int wake_model_ota_start_health_guard(void) {return ESP_OK;}
bool wake_model_ota_is_pending(void) {return false;}
void wake_model_ota_rollback_and_restart(void) {assert(false);}
int voice_control_start(void) {
 ++step;
 if(failure==3) return ESP_ERR_NO_MEM;
 if(setjmp(wait_jump)==0) startup_voice_entry(NULL);
 return ESP_OK;
}
void voice_control_activate(void) {
 activation_step=++step;assert(wifi_step && ui_step<wifi_step);voice_ready=true;startup_voice_entry(NULL);
}
int device_transport_reserve(void) {++step;assert(!wake_started);return ESP_OK;}
int device_provisioning_start(void) {++step;return failure==4?ESP_ERR_NO_MEM:ESP_OK;}
void device_provisioning_activate(void) {assert(wifi_step);provision_ready=true;}
int device_transport_start(void) {
 wifi_step=++step;assert(!wake_started && ui_step>0);
 return failure==1?ESP_ERR_NO_MEM:ESP_OK;
}
int face_tracking_init(void) {assert(!wifi_step && !wake_started);return ESP_OK;}
int device_ui_init(void) {ui_step=++step;assert(!wifi_step && !wake_started);return failure==2?ESP_ERR_NO_MEM:ESP_OK;}
bool device_transport_is_server_connected(void) {return false;}
void device_ui_connection_changed(bool online) {assert(!online && ui_step);}
APPLICATION
int main(void) {
 for(failure=0;failure<5;++failure) {
 step=wifi_step=ui_step=activation_step=confirms=restarts=0;
 wake_started=voice_waiting=voice_ready=provision_ready=false;
 app_main();
 assert(wifi_step>0);
 if(failure==0) assert(voice_waiting && wake_started && ui_step<wifi_step && wifi_step<activation_step && confirms==1 && provision_ready);
 if(failure==1) assert(ui_step<wifi_step && !wake_started && !confirms && !provision_ready);
 if(failure==2 || failure==3) assert(!wake_started && !confirms && provision_ready);
 if(failure==4) assert(!confirms && !provision_ready);
 assert(restarts==0);
 }
 puts("PASS real startup: reserved voice blocks before Wi-Fi, critical Flash stack reserved before network, activation follows network/UI, four failure paths cannot confirm OTA");
}
'''.replace('ENTRY',entry).replace('APPLICATION',app)
with tempfile.TemporaryDirectory(prefix='stackchan-startup-',dir=root/'.tools') as d:
 path=Path(d);(path/'test.c').write_text(harness,encoding='utf-8')
 subprocess.run(['docker','run','--rm','--network','none','--mount',f'type=bind,source={path},target=/test,readonly','gcc:14.2.0','sh','-c','gcc -std=gnu11 -Wall -Wextra -Werror /test/test.c -o /tmp/test && /tmp/test'],check=True)