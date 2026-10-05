"""Exercise the production stream decoder and ON_DATA failure boundary.

IDF ignores the ON_DATA callback's return value. Fault injection verifies that
invalid framing, a rejected frame and PSRAM exhaustion close the transport once
instead of leaving the HTTP owner waiting, without logging private payloads.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'firmware/main/voice_service.c').read_text(encoding='utf-8')

def function(signature):
    start = source.index(signature)
    body = source.index('{', start)
    depth = 1
    end = body + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

types = source[source.index('typedef struct {'):source.index('struct voice_service_live_upload')]
pieces = [function(s) for s in [
    'static bool reserve_response(', 'static uint32_t read_be32(',
    'static bool stream_frame_size_valid(', 'static esp_err_t append_legacy(',
    'static __attribute__((noinline)) esp_err_t consume_streaming_data(',
    'static esp_err_t streaming_response_event_handler(',
]]
harness = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
typedef int esp_err_t;
typedef void *esp_http_client_handle_t;
typedef esp_err_t (*voice_service_stream_frame_handler_t)(uint8_t,uint8_t *,size_t,bool *,void *);
typedef struct { int event_id; void *data; int data_len; void *user_data; void *client; } esp_http_client_event_t;
#define HTTP_EVENT_ON_DATA 1
#define ESP_OK 0
#define ESP_FAIL 1
#define ESP_ERR_NO_MEM 2
#define ESP_ERR_INVALID_RESPONSE 3
#define MALLOC_CAP_SPIRAM 1
#define MALLOC_CAP_8BIT 2
#define MALLOC_CAP_INTERNAL 4
#define VOICE_SERVICE_MAX_RESPONSE_SIZE (2U*1024U*1024U)
#define VOICE_SERVICE_INITIAL_CAPACITY 8192U
#define VOICE_PROTOCOL_STREAM_MAX_AUDIO_LEN (2U*1024U*1024U)
#define VOICE_STREAM_FRAME_START 1
#define VOICE_STREAM_FRAME_AUDIO 2
#define VOICE_STREAM_FRAME_COMPLETE 3
#define VOICE_STREAM_FRAME_ERROR 4
static const char *TAG="test";
static bool allocation_fails, reject_frame;
static unsigned closes, frames;
static void safe_log(const char *tag,const char *format,...) {(void)tag;(void)format;}
#define ESP_LOGI(...) safe_log(__VA_ARGS__)
#define ESP_LOGW(...) safe_log(__VA_ARGS__)
static const char *esp_err_to_name(int error){(void)error;return "fixture";}
static unsigned heap_caps_get_free_size(int caps){(void)caps;return 1500;}
static unsigned heap_caps_get_largest_free_block(int caps){(void)caps;return 736;}
static void *heap_caps_malloc(size_t size,int caps){assert(caps==(MALLOC_CAP_SPIRAM|MALLOC_CAP_8BIT));return allocation_fails?NULL:malloc(size);}
static void *heap_caps_realloc(void *p,size_t size,int caps){assert(caps==(MALLOC_CAP_SPIRAM|MALLOC_CAP_8BIT));return allocation_fails?NULL:realloc(p,size);}
static void heap_caps_free(void *p){free(p);}
static int esp_http_client_close(void *client){assert(client==(void *)1);closes++;return ESP_OK;}
static void clear_active_turn_client(void *client){assert(client==(void *)1);}
TYPES
PRODUCTION
static int accept_frame(uint8_t type,uint8_t *payload,size_t size,bool *retain,void *context)
{(void)type;(void)context;assert(payload && size);*retain=false;frames++;return reject_frame?ESP_ERR_INVALID_RESPONSE:ESP_OK;}
static int deliver(streaming_response_t *response,const void *data,int size)
{
 esp_http_client_event_t event={.event_id=HTTP_EVENT_ON_DATA,.data=(void *)data,.data_len=size,.user_data=response,.client=(void *)1};
 return streaming_response_event_handler(&event);
}
static void cleanup(streaming_response_t *response){free(response->frame_payload);free(response->legacy.data);}
int main(void)
{
 const uint8_t valid[]={'S','C','V','2',1,0,0,0,2,'{','}',3,0,0,0,2,'{','}'};
 streaming_response_t response={.frame_handler=accept_frame};
 for(size_t i=0;i<sizeof(valid);i++)assert(deliver(&response,valid+i,1)==ESP_OK);
 assert(response.terminal && frames==2 && closes==0 && !response.failed);cleanup(&response);
 for(int fault=0;fault<4;fault++) {
  response=(streaming_response_t){.frame_handler=accept_frame};closes=0;
  allocation_fails=fault==2;reject_frame=fault==3;
  const uint8_t invalid[]={'S','C','V','2',1,0,0,0,0};
  const uint8_t *data=fault==0?(const uint8_t *)"BAD!":fault==1?invalid:valid;
  int length=fault==0?4:fault==1?(int)sizeof(invalid):(int)sizeof(valid);
  assert(deliver(&response,data,length)!=ESP_OK);
  assert(response.failed && closes==1);
  assert(response.receive_error==(fault==2?ESP_ERR_NO_MEM:ESP_ERR_INVALID_RESPONSE));
  // Model IDF ignoring the callback return: transport closure must be explicit.
  assert(deliver(&response,valid,sizeof(valid))==ESP_FAIL && closes==1);
  cleanup(&response);
 }
 puts("PASS production fragmented stream; malformed prefix/size, rejected frame and allocation failure close HTTP once and preserve the cause despite ignored callback returns");
}
'''.replace('TYPES', types).replace('PRODUCTION', '\n'.join(pieces))
with tempfile.TemporaryDirectory(prefix='stackchan-stream-receive-', dir=root / '.tools') as directory:
    path=Path(directory)
    (path / 'test.c').write_text(harness, encoding='utf-8')
    subprocess.run(['docker','run','--rm','--network','none','--mount',f'type=bind,source={path},target=/test,readonly',
                    'gcc:14.2.0','sh','-c','gcc -std=gnu11 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -Wall -Wextra -Werror /test/test.c -o /tmp/test && ASAN_OPTIONS=detect_leaks=1:halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1 /tmp/test'],check=True)
