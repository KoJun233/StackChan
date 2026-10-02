#include "esp_heap_caps.h"
/* Real production renderer/callbacks/worker; only hardware, HTTP and RTOS
 * scheduling are fixtures. Fake tokens and URLs never leave this process. */
#include <cassert>
#include <cstdio>
#include <csetjmp>
#include <deque>
#include <string>
static void *audit_worker_allocations[3];
static unsigned audit_worker_allocation_count;
static void *audit_worker_malloc(size_t size, unsigned)
{ assert(audit_worker_allocation_count<3); return audit_worker_allocations[audit_worker_allocation_count++]=malloc(size); }
#define heap_caps_malloc audit_worker_malloc
#define STACKCHAN_DEVICE_UI_HOST_TEST
#include "../../main/device_ui.cpp"
#include "src/misc/lv_event_private.h"
#undef heap_caps_malloc

static int64_t audit_now_us=10000000;
static std::deque<Request> audit_requests;
static Request audit_last_request;
static unsigned audit_queued;
static bool audit_automatic=true;
static int audit_brightness=50;
static int audit_http_status=200;
static int audit_http_volume=50;
static std::string audit_http_payload;
static std::string audit_http_body;
static size_t audit_http_cursor;
static jmp_buf audit_jump;
extern "C" int64_t esp_timer_get_time(void){return audit_now_us;}
extern "C" bool voice_control_is_automatic_wake(void){return audit_automatic;}
extern "C" void companion_hardware_request_playback_stop(void){}
extern "C" void companion_hardware_mark_activity(void){}
extern "C" void safety_state_stop_motion(void){}
extern "C" void voice_control_cancel_active_turn(void){}
extern "C" esp_err_t companion_hardware_set_local_brightness(int value){audit_brightness=value;return ESP_OK;}
extern "C" esp_err_t companion_hardware_clear_local_brightness(void){audit_brightness=-1;return ESP_OK;}
extern "C" bool face_tracking_set_enabled(bool){return true;}
extern "C" void face_tracking_set_runtime_gate(bool,bool){}
extern "C" void companion_hardware_expression_clear_turn(void){}
extern "C" BaseType_t xSemaphoreTake(SemaphoreHandle_t,TickType_t){return pdTRUE;}
extern "C" BaseType_t xSemaphoreGive(SemaphoreHandle_t){return pdTRUE;}
extern "C" BaseType_t xQueueSend(QueueHandle_t queue,const void *request,TickType_t)
{
    if(queue==s_flash_queue){
        auto &flash=*static_cast<const FlashRequest *>(request);
        if(flash.action==SAVE_INPUT_MODE)audit_automatic=flash.value!=0;
        else{strcpy(flash.identity->server_base_url,"https://fixture.invalid");strcpy(flash.identity->access_token,"fictional-access-token");}
        s_flash_result=ESP_OK;return pdTRUE;
    }
    audit_queued++;audit_last_request=*static_cast<const Request *>(request);audit_requests.push_back(audit_last_request);return pdTRUE;
}
extern "C" BaseType_t xQueueReceive(QueueHandle_t,void *output,TickType_t)
{
    if(audit_requests.empty())longjmp(audit_jump,1);
    *static_cast<Request *>(output)=audit_requests.front();audit_requests.pop_front();return pdTRUE;
}
void vTaskDelete(TaskHandle_t){}
esp_err_t i2c_master_get_bus_handle(int,i2c_master_bus_handle_t *){return ESP_FAIL;}
esp_err_t i2c_master_bus_add_device(i2c_master_bus_handle_t,const i2c_device_config_t *,i2c_master_dev_handle_t *){return ESP_FAIL;}
esp_err_t i2c_master_transmit_receive(i2c_master_dev_handle_t,const uint8_t *,size_t,uint8_t *,size_t,int){return ESP_FAIL;}
extern "C" bool device_identity_is_valid_server_base_url(const char *origin){return origin && !strcmp(origin,"https://fixture.invalid");}
extern "C" bool device_identity_is_valid(const device_identity_t *identity){return identity && device_identity_is_valid_server_base_url(identity->server_base_url);}
void *esp_crt_bundle_attach=nullptr;
esp_http_client_handle_t esp_http_client_init(const esp_http_client_config_t *){audit_http_cursor=0;return reinterpret_cast<void *>(4);}
esp_err_t esp_http_client_set_header(esp_http_client_handle_t,const char *,const char *){return ESP_OK;}
esp_err_t esp_http_client_set_method(esp_http_client_handle_t,esp_http_client_method_t){return ESP_OK;}
esp_err_t esp_http_client_open(esp_http_client_handle_t,int){return ESP_OK;}
int esp_http_client_write(esp_http_client_handle_t,const char *body,int size){audit_http_body.assign(body,size);return size;}
int64_t esp_http_client_fetch_headers(esp_http_client_handle_t){return audit_http_payload.size();}
int esp_http_client_get_status_code(esp_http_client_handle_t){return audit_http_status;}
int esp_http_client_read(esp_http_client_handle_t,char *output,int size)
{size_t count=std::min(static_cast<size_t>(size),audit_http_payload.size()-audit_http_cursor);memcpy(output,audit_http_payload.data()+audit_http_cursor,count);audit_http_cursor+=count;return count;}
bool esp_http_client_is_complete_data_received(esp_http_client_handle_t){return audit_http_cursor==audit_http_payload.size();}
esp_err_t esp_http_client_close(esp_http_client_handle_t){return ESP_OK;}
esp_err_t esp_http_client_cleanup(esp_http_client_handle_t){return ESP_OK;}

static void response_payload()
{
    char numbers[32];snprintf(numbers,sizeof(numbers),"%d",audit_http_volume);
    audit_http_payload="{\"result\":\"SAVED\",\"state\":{\"version\":1,\"volume_percent\":"+std::string(numbers)+",\"night_mode\":false,\"quiet_today\":false,\"rest_pending\":true,\"roles_truncated\":false,\"workday_state\":\"OFF\",\"pending_confirmation_id\":null,\"role\":{\"id\":\"11111111-1111-4111-8111-111111111111\",\"name\":\"伙伴甲\"},\"roles\":[";
    for(int i=0;i<6;i++){char role[128];snprintf(role,sizeof(role),"%s{\"id\":\"%08x-1111-4111-8111-111111111111\",\"name\":\"伙伴%d\"}",i?",":"",i+1,i);audit_http_payload+=role;}
    audit_http_payload+="]}}";
}
static void process_requests()
{
    response_payload();audit_worker_allocation_count=0;
    if(setjmp(audit_jump)==0)worker(nullptr);
    for(unsigned i=0;i<audit_worker_allocation_count;i++)free(audit_worker_allocations[i]);
    audit_worker_allocation_count=0;
}
static uint32_t audit_buffer[320*240];
static bool audit_pressed;
static lv_point_t audit_point;
static unsigned audit_flushes;
static void read_pointer(lv_indev_t *,lv_indev_data_t *data){data->point=audit_point;data->state=audit_pressed?LV_INDEV_STATE_PRESSED:LV_INDEV_STATE_RELEASED;}
static void flush(lv_display_t *display,const lv_area_t *,uint8_t *){audit_flushes++;lv_display_flush_ready(display);}
static void paint(lv_display_t *display){device_ui_tick_locked();lv_obj_update_layout(lv_screen_active());lv_refr_now(display);lv_tick_inc(20);audit_now_us+=20000;lv_timer_handler();}
static Control *control(Action action,int value=-999)
{for(unsigned i=0;i<s_control_count;i++)if(s_controls[i].action==action&&(value==-999||s_controls[i].value==value))return &s_controls[i];assert(false);return nullptr;}
static void pointer(lv_indev_t *indev,int x,int y,bool pressed){audit_point={x,y};audit_pressed=pressed;lv_indev_read(indev);lv_tick_inc(20);audit_now_us+=20000;}
static void tap(lv_indev_t *indev,lv_obj_t *object)
{lv_area_t a;lv_obj_get_coords(object,&a);int x=(a.x1+a.x2)/2,y=(a.y1+a.y2)/2;pointer(indev,x,y,true);pointer(indev,x,y,false);}
static void event_click(Action action,int value=-999)
{for(unsigned i=0;i<s_control_count;i++){auto &c=s_controls[i];if(c.action==action&&(value==-999||c.value==value)&&!lv_obj_check_type(c.object,&lv_slider_class)){lv_obj_send_event(c.object,LV_EVENT_CLICKED,nullptr);return;}}assert(false);}
static void navigate(lv_display_t *display,Page page)
{Control temporary={nullptr,NAVIGATE,page,{0}};lv_event_t event;memset(&event,0,sizeof(event));event.user_data=&temporary;clicked(&event);paint(display);assert(s_page==page);}

int main(void)
{
    char route[192];
    for(const char *path:{"/api/v1/device/ui/state","/api/v1/device/ui/settings",
        "/api/v1/device/ui/confirmations/11111111-1111-4111-8111-111111111111",
        "/api/v1/device/ui/confirmations/11111111-1111-4111-8111-111111111111/shown"}) {
        assert(device_endpoint_build_http_url("https://fixture.invalid",path,route,sizeof(route)));
        assert(std::string(route)==std::string("https://fixture.invalid")+path);
    }
    for(const char *path:{"/api/v1/device/ui/state?next=https://evil.invalid",
        "/api/v1/device/ui/settings/","/api/v1/device/ui/confirmations/../settings",
        "/api/v1/device/ui/confirmations/11111111-1111-4111-8111-111111111111/shown/extra",
        "/api/v1/device/ui/confirmations/11111111-1111-4111-8111-11111111111x",
        "/api/v1/device/ui/confirmations/11111111-1111-4111-8111-111111111111?x=1"})
        assert(!device_endpoint_build_http_url("https://fixture.invalid",path,route,sizeof(route)));
    lv_init();auto *display=lv_display_create(320,240);lv_display_set_color_format(display,LV_COLOR_FORMAT_RGB565);
    lv_display_set_buffers(display,audit_buffer,nullptr,sizeof(audit_buffer),LV_DISPLAY_RENDER_MODE_PARTIAL);lv_display_set_flush_cb(display,flush);
    auto *indev=lv_indev_create();lv_indev_set_type(indev,LV_INDEV_TYPE_POINTER);lv_indev_set_display(indev,display);lv_indev_set_read_cb(indev,read_pointer);
    static device_ui_state_t state={};state.volume_percent=50;state.rest_pending=true;strcpy(state.workday_state,"OFF");
    strcpy(state.role.id,"11111111-1111-4111-8111-111111111111");strcpy(state.role.name,"伙伴甲");state.role_count=6;
    for(int i=0;i<6;i++){snprintf(state.roles[i].id,sizeof(state.roles[i].id),"%08x-1111-4111-8111-111111111111",i+1);snprintf(state.roles[i].name,sizeof(state.roles[i].name),"伙伴%d",i);}
    device_ui_host_render(lv_screen_active(),&state,nullptr,true,false,false,HOME,0);s_mutex=reinterpret_cast<void *>(1);s_queue=reinterpret_cast<void *>(2);s_flash_queue=reinterpret_cast<void *>(3);s_flash_done=reinterpret_cast<void *>(4);s_ready=true;paint(display);
    assert(s_page==HOME&&s_control_count<=24);
    s_has_state=false;assert(enqueue(REFRESH));process_requests();paint(display);assert(s_has_state);
    tap(indev,control(NAVIGATE,INPUT)->object);paint(display);assert(s_page==INPUT);
    event_click(INPUT_MODE,0);assert(page_busy(INPUT));navigate(display,HOME);assert(page_busy(INPUT));process_requests();paint(display);assert(!page_busy(INPUT)&&!audit_automatic);
    navigate(display,SOUND);auto *slider=s_menu_slider;unsigned queued=audit_queued;
    pointer(indev,160,124,true);pointer(indev,205,124,true);pointer(indev,240,124,true);assert(audit_queued==queued);
    int desired=lv_slider_get_value(slider);assert(desired!=50);pointer(indev,240,124,false);assert(audit_queued==queued+1&&audit_last_request.action==VOLUME&&audit_last_request.value==desired);paint(display);assert(s_menu_slider==slider&&page_busy(SOUND));
    audit_http_volume=desired;process_requests();paint(display);assert(!page_busy(SOUND)&&s_state->volume_percent==desired&&s_menu_slider==slider);
    event_click(VOLUME);audit_http_status=500;process_requests();paint(display);assert(s_retry[SOUND].available&&!page_busy(SOUND));
    queued=audit_queued;event_click(RETRY);assert(audit_queued==queued+1);audit_http_status=200;process_requests();paint(display);assert(!s_retry[SOUND].available);
    navigate(display,PARTNER);auto *role=control(ROLE,0);char expected[37];strcpy(expected,role->id);auto temporary=s_state->roles[0];s_state->roles[0]=s_state->roles[1];s_state->roles[1]=temporary;s_dirty=true;paint(display);tap(indev,role->object);assert(audit_last_request.action==ROLE&&strcmp(audit_last_request.id,expected)==0);
    audit_http_status=500;process_requests();paint(display);
    assert(s_retry[PARTNER].available&&strcmp(s_retry[PARTNER].id,expected)==0);
    event_click(RETRY);assert(audit_last_request.action==ROLE&&strcmp(audit_last_request.id,expected)==0);
    process_requests();paint(display);queued=audit_queued;
    event_click(RETRY);assert(audit_queued==queued+1&&strcmp(audit_last_request.id,expected)==0);
    audit_http_status=200;process_requests();paint(display);
    assert(!page_busy(PARTNER));
    event_click(ROLE_PAGE,1);paint(display);assert(s_role_page==1);event_click(ROLE_PAGE,-1);paint(display);assert(s_role_page==0);
    navigate(display,SOUND);event_click(VOLUME);assert(page_busy(SOUND));
    assert(!s_retry[INPUT].available);
    device_ui_connection_changed(false);device_ui_connection_changed(true);process_requests();paint(display);
    assert(!page_busy(SOUND));
    assert(!s_retry[INPUT].available);
    navigate(display,SOUND);event_click(VOLUME);assert(page_busy(SOUND));
    navigate(display,DISPLAY);lv_slider_set_value(s_menu_slider,83,LV_ANIM_OFF);lv_obj_send_event(s_menu_slider,LV_EVENT_RELEASED,nullptr);
    assert(page_busy(SOUND)&&page_busy(DISPLAY));navigate(display,HOME);process_requests();paint(display);
    assert(!page_busy(SOUND)&&!page_busy(DISPLAY)&&audit_brightness==83);
    navigate(display,SOUND);event_click(VOLUME);assert(page_busy(SOUND));
    close_locked();paint(display);assert(page_busy(SOUND));
    assert(lv_obj_get_child_count(s_overlay)==0 && s_menu_slider==nullptr && s_control_count==0);
    device_ui_open_menu();paint(display);assert(page_busy(SOUND));
    process_requests();paint(display);assert(!page_busy(SOUND)&&!s_retry[SOUND].available);
    navigate(display,HOME);pointer(indev,260,132,true);pointer(indev,180,132,true);pointer(indev,80,132,true);pointer(indev,80,132,false);paint(display);assert(s_page==HOME&&s_home_page==1);
    pointer(indev,140,70,true);pointer(indev,140,150,true);pointer(indev,140,190,true);pointer(indev,140,190,false);paint(display);assert(s_mode==CLOSED);
    lv_indev_delete(indev);lv_display_delete(display);lv_deinit();puts("PASS production UI actual pointer/navigation, independent pending/result/failure/repeated ROLE retry, stable ROLE ID, slider-release, reconnect, HOME page/exit gestures; no runtime workaround");
}
