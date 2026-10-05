"""Test actual strict USB parsing and origin relocation, using synthetic identity and mocked I/O."""
from pathlib import Path
import re
import subprocess
import tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'firmware/main/device_provisioning.c').read_text(encoding='utf-8')
def function(name):
 m=re.search(r'(?m)^[^\n;]*\b'+name+r'\([^;]*?\)\s*\{',s)
 if not m: raise RuntimeError('Missing actual function '+name)
 a=m.start();pos=s.index('{',m.start());depth=1;quote=None;escape=False
 for i in range(pos+1,len(s)):
  c=s[i]
  if quote:
   if escape:escape=False
   elif c=='\\':escape=True
   elif c==quote:quote=None
   continue
  if c in ('"',"'"):quote=c
  elif c=='{':depth+=1
  elif c=='}':
   depth-=1
   if depth==0:return s[a:i+1]
 raise RuntimeError('Unclosed actual function '+name)
body='\n'.join(function(n) for n in ['has_bounded_length','copy_string','is_valid_pairing_code','required_string','device_provisioning_parse_request','relocate_server'])
harness=r'''
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <ctype.h>
#include "device_provisioning.h"
#include "device_credentials.h"
#include "strict_json.h"
#include "cJSON.h"
#define PROVISIONING_LINE_MAX_LEN 512
#define pdMS_TO_TICKS(ms) (ms)
static int fault,saves,restarts,pauses,resumes;
static bool paused;
static char result[64];
static device_identity_t original,saved;
static void report_result(const char *type,const char *status) {(void)type;snprintf(result,sizeof(result),"%s",status);}
void safety_state_stop_motion(void) {}
void voice_control_cancel_active_turn(void) {}
bool device_transport_pause_for_server_update(void) {++pauses;paused=fault!=1;return paused;}
void device_transport_resume_after_server_update(void) {++resumes;paused=false;}
int device_identity_load(device_identity_t *identity) {assert(paused);*identity=original;return fault==2?1:ESP_OK;}
int device_identity_save(const device_identity_t *identity) {assert(paused);if(fault==5)return 1;saved=*identity;++saves;return ESP_OK;}
bool device_identity_is_valid_server_base_url(const char *origin) {return strcmp(origin,"http://192.168.1.6:8080")==0;}
static bool wait_for_wifi_connection(void) {return fault!=3;}
device_credential_refresh_result_t device_credentials_refresh(device_identity_t *identity) {
 assert(paused);assert(strcmp(identity->device_id,original.device_id)==0);assert(strcmp(identity->refresh_token,original.refresh_token)==0);
 if(fault==4)return DEVICE_CREDENTIAL_REPAIR_REQUIRED;
 strcpy(identity->access_token,"synthetic-renewed-access");return DEVICE_CREDENTIAL_REFRESHED;
}
static void vTaskDelay(int ms) {assert(ms==250);}
void esp_restart(void) {assert(paused);++restarts;}
SOURCE
int main(void) {
 device_provisioning_request_t request;
 const char *valid="{\"type\":\"relocate_server\",\"serverBaseUrl\":\"http://192.168.1.6:8080\"}";
 assert(device_provisioning_parse_request(valid,strlen(valid),&request));
 assert(request.kind==DEVICE_PROVISIONING_REQUEST_RELOCATE_SERVER && !request.pairing_code[0] && !request.ssid[0] && !request.password[0]);
 const char *invalid[]={
 "{\"type\":\"relocate_server\"}",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":\"http://192.168.1.6:8080\",\"pairingCode\":\"ABC\"}",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":\"http://192.168.1.6:8080\",\"serverBaseUrl\":\"http://192.168.1.6:8080\"}",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":\"invalid\"}",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":3}",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":\"http://192.168.1.6:8080\"} extra",
 "{\"type\":\"relocate_server\",\"serverBaseUrl\":\"\\u0000\"}"};
 for(unsigned i=0;i<sizeof(invalid)/sizeof(*invalid);++i)assert(!device_provisioning_parse_request(invalid[i],strlen(invalid[i]),&request));
 assert(device_provisioning_parse_request(valid,strlen(valid),&request));
 strcpy(original.device_id,"00000000-0000-0000-0000-000000000001");
 strcpy(original.server_base_url,"http://192.168.1.4:8080");
 strcpy(original.refresh_token,"synthetic-fixed-refresh");
 for(fault=0;fault<6;++fault) {
  saves=restarts=pauses=resumes=0;paused=false;memset(&saved,0,sizeof(saved));
  relocate_server(&request);
  assert(pauses==1 && !paused && resumes==1);
  if(fault==0) {
   assert(saves==1 && restarts==1 && strcmp(result,"complete")==0);
   assert(strcmp(saved.server_base_url,request.server_base_url)==0);
   assert(strcmp(saved.device_id,original.device_id)==0 && strcmp(saved.refresh_token,original.refresh_token)==0);
  } else assert(saves==0 && restarts==0 && strcmp(original.server_base_url,"http://192.168.1.4:8080")==0);
 }
 puts("PASS actual USB parser: exact two fields, invalid/duplicate/NUL/trailing rejection; authenticated origin update preserves ID/refresh; five failures do not save/reboot and resume transport");
}
'''.replace('SOURCE',body)
with tempfile.TemporaryDirectory(prefix='stackchan-origin-',dir=r/'.tools') as d:
 p=Path(d);(p/'test.c').write_text(harness,encoding='utf-8')
 command='gcc -std=gnu11 -Wall -Wextra -Werror -I/project/main -I/project/test/host/stubs -I/project/managed_components/espressif__cjson/cJSON /test/test.c /project/main/strict_json.c /project/managed_components/espressif__cjson/cJSON/cJSON.c -lm -o /tmp/test && /tmp/test'
 subprocess.run(['docker','run','--rm','--network','none','--mount',f'type=bind,source={p},target=/test,readonly','--mount',f'type=bind,source={r/"firmware"},target=/project,readonly','gcc:14.2.0','sh','-c',command],check=True)