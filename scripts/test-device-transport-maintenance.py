"""Run real bounded transport maintenance/backoff functions without networking."""
from pathlib import Path
import re
import subprocess
import tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'firmware/main/device_transport.c').read_text(encoding='utf-8')
def function(name):
 m=re.search(r'(?m)^[^\n;]*\b'+name+r'\([^;]*?\)\s*\{',s)
 if not m:raise RuntimeError('Missing actual function '+name)
 depth=1;a=s.index('{',m.start())
 for i in range(a+1,len(s)):
  if s[i]=='{':depth+=1
  elif s[i]=='}':
   depth-=1
   if depth==0:return s[m.start():i+1]
 raise RuntimeError('Unclosed function '+name)
refresh=s.index('device_credential_refresh_result_t refresh = device_credentials_refresh(&identity);')
store=s.index('device_identity_save(&identity)',refresh)
assert 'continue;' in s[refresh:store] and 's_server_update_requested' in s[refresh:store], 'stale renewal must be dropped before save'
body='\n'.join(function(n) for n in ['wait_for_websocket_retry','device_transport_pause_for_server_update','device_transport_resume_after_server_update'])
harness=r'''
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stddef.h>
#include <assert.h>
typedef unsigned TickType_t;
#define WIFI_CONNECTED_BIT 1u
#define TRANSPORT_IDLE_POLL_MS 1000
#define pdMS_TO_TICKS(ms) (ms)
static bool s_server_update_requested,s_server_update_paused,auto_ack;
static void *s_transport_task_handle=(void*)1;
static unsigned s_transport_events,wifi_bits=1,waits;
static int64_t now;
static int64_t esp_timer_get_time(void) {return now;}
static unsigned xEventGroupGetBits(unsigned events) {(void)events;return wifi_bits;}
static void vTaskDelay(unsigned ms) {++waits;now+=(int64_t)ms*1000;if(auto_ack && s_server_update_requested)s_server_update_paused=true;}
static void service_wifi_reconnect(void) {}
SOURCE
int main(void) {
 s_transport_task_handle=NULL;assert(!device_transport_pause_for_server_update() && !s_server_update_requested);
 s_transport_task_handle=(void*)1;assert(!device_transport_pause_for_server_update());
 assert(now==20000000 && !s_server_update_requested && waits==800);
 now=0;waits=0;auto_ack=true;
 assert(device_transport_pause_for_server_update() && s_server_update_paused && now<=25000);
 waits=0;wait_for_websocket_retry(60);assert(waits==0);
 device_transport_resume_after_server_update();assert(!s_server_update_requested);
 s_server_update_paused=false;auto_ack=false;now=0;waits=0;
 wait_for_websocket_retry(2);assert(now==2000000 && waits==2);
 now=0;waits=0;wifi_bits=0;wait_for_websocket_retry(60);assert(!waits);
 puts("PASS actual transport: no task rejects, 20s timeout resumes, acknowledgment waits, maintenance interrupts 60s backoff, normal retry and Wi-Fi loss preserved; stale renewal guard precedes save");
}
'''.replace('SOURCE',body)
with tempfile.TemporaryDirectory(prefix='stackchan-maintenance-',dir=r/'.tools') as d:
 p=Path(d);(p/'test.c').write_text(harness,encoding='utf-8')
 subprocess.run(['docker','run','--rm','--network','none','--mount',f'type=bind,source={p},target=/test,readonly','gcc:14.2.0','sh','-c','gcc -std=gnu11 -Wall -Wextra -Werror /test/test.c -o /tmp/test && /tmp/test'],check=True)