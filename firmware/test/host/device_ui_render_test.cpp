#include "device_ui.h"
#include "device_ui_font.h"
#include "device_ui_protocol.h"
#include "companion_hardware.h"
#include "safety_state.h"
#include "voice_control.h"
#include "freertos/semphr.h"
#include "freertos/queue.h"

#include <cassert>
#include <cstdio>
#include <cstring>
#include <string>

extern "C" void device_ui_host_render(lv_obj_t *, const device_ui_state_t *,
    const device_ui_card_t *, bool connected, bool busy, bool shown, int page, int home_page);

static int64_t fake_now_us = 10000000;
extern "C" int64_t esp_timer_get_time(void) { return fake_now_us; }
extern "C" bool voice_control_is_automatic_wake(void) { return true; }
extern "C" void companion_hardware_request_playback_stop(void) {}
extern "C" void safety_state_stop_motion(void) {}
extern "C" BaseType_t xSemaphoreTake(SemaphoreHandle_t, TickType_t) { return pdTRUE; }
extern "C" BaseType_t xSemaphoreGive(SemaphoreHandle_t) { return pdTRUE; }
extern "C" BaseType_t xQueueSend(QueueHandle_t, const void *, TickType_t) { return pdTRUE; }

constexpr int WIDTH = 320, HEIGHT = 240;
static uint32_t display_buffer[WIDTH * HEIGHT];
static uint8_t image_buffer[WIDTH * HEIGHT * 3];
static unsigned object_depth(lv_obj_t *object);

static void flush(lv_display_t *display, const lv_area_t *area, uint8_t *pixels)
{
    for (int32_t y = area->y1; y <= area->y2; y++) {
        for (int32_t x = area->x1; x <= area->x2; x++) {
            size_t destination = ((size_t)y * WIDTH + x) * 3;
            size_t source = ((size_t)(y - area->y1) * lv_area_get_width(area) + x - area->x1);
#ifdef HOST_RGB565
            uint16_t color; memcpy(&color, pixels + source*2, sizeof(color));
            image_buffer[destination] = (color>>11)*255/31;
            image_buffer[destination+1] = ((color>>5)&63)*255/63;
            image_buffer[destination+2] = (color&31)*255/31;
#else
            source *= 4;
            image_buffer[destination] = pixels[source + 2];
            image_buffer[destination + 1] = pixels[source + 1];
            image_buffer[destination + 2] = pixels[source];
#endif
        }
    }
    lv_display_flush_ready(display);
}

static void snapshot(lv_display_t *display, const char *directory, const char *name)
{
    lv_obj_invalidate(lv_screen_active());
    lv_refr_now(display);
    char filename[512];
    assert(snprintf(filename, sizeof(filename), "%s/%s.ppm", directory, name) < (int)sizeof(filename));
    FILE *file = fopen(filename, "wb");
    assert(file);
    fprintf(file, "P6\n%d %d\n255\n", WIDTH, HEIGHT);
    assert(fwrite(image_buffer, sizeof(image_buffer), 1, file) == 1);
    assert(fclose(file) == 0);
}

static void render(lv_display_t *display, const char *output, const char *name,
    const device_ui_state_t *state, const device_ui_card_t *card, bool connected, bool busy, bool shown, int page=0, int home_page=0)
{
    lv_obj_clean(lv_screen_active());
    device_ui_host_render(lv_screen_active(), state, card, connected, busy, shown, page, home_page);
    assert(object_depth(lv_screen_active())<=6);
    if (card != nullptr) {
        auto *overlay = lv_obj_get_child(lv_screen_active(), 0);
        lv_obj_t *confirm = nullptr;
        for (unsigned index = 0; index < lv_obj_get_child_count(overlay); index++) {
            auto *candidate = lv_obj_get_child(overlay, index);
            if (!lv_obj_check_type(candidate, &lv_button_class) || lv_obj_get_child_count(candidate) == 0) continue;
            auto *caption = lv_obj_get_child(candidate, 0);
            if (lv_obj_check_type(caption, &lv_label_class) && strcmp(lv_label_get_text(caption), "确认") == 0) confirm = candidate;
        }
        assert(confirm != nullptr);
        bool disabled = !connected || busy || !shown || strcmp(card->status, "PENDING") != 0;
        assert(lv_obj_has_state(confirm, LV_STATE_DISABLED) == disabled);
        if (disabled) {
            lv_color_t color = lv_obj_get_style_bg_color(confirm, LV_PART_MAIN);
            bool still_green = color.red == 0x14 && color.green == 0x6c && color.blue == 0x66;
            assert(!still_green || lv_obj_get_style_opa(confirm, LV_PART_MAIN) < LV_OPA_60 ||
                   lv_obj_get_style_bg_opa(confirm, LV_PART_MAIN) < LV_OPA_60);
        }
    }
    snapshot(display, output, name);
}

static lv_obj_t *scroll_object(void)
{
    auto *overlay = lv_obj_get_child(lv_screen_active(), 0);
    assert(overlay != nullptr);
    for (unsigned index = 0; index < lv_obj_get_child_count(overlay); index++) {
        auto *child = lv_obj_get_child(overlay, index);
        if (lv_obj_get_width(child) == 320 && lv_obj_has_flag(child, LV_OBJ_FLAG_SCROLLABLE)) return child;
    }
    assert(false && "The production UI must retain its bounded scroll region");
    return nullptr;
}

static unsigned object_depth(lv_obj_t *object)
{
    unsigned result=1;
    for(unsigned i=0;i<lv_obj_get_child_count(object);i++) {
        unsigned child=1+object_depth(lv_obj_get_child(object,i));
        if(result<child)result=child;
    }
    return result;
}

int main(int argc, char **argv)
{
    assert(argc == 2);
    lv_init();
    auto *display = lv_display_create(WIDTH, HEIGHT);
#ifdef HOST_RGB565
    lv_display_set_color_format(display, LV_COLOR_FORMAT_RGB565);
#else
    lv_display_set_color_format(display, LV_COLOR_FORMAT_XRGB8888);
#endif
    lv_display_set_buffers(display, display_buffer, nullptr, sizeof(display_buffer), LV_DISPLAY_RENDER_MODE_PARTIAL);
    lv_display_set_flush_cb(display, flush);
    assert(device_ui_font_covers_text("待办：明天下午三点，完成项目迭代并检查所有回归结果。"));
    assert(device_ui_font_covers_text("2026-10-03 15:00 Asia/Shanghai"));
    static const char *const visible_strings[] = {
        "控制中心", "确认操作", "确认", "取消", "关闭", "停止声音",
        "已连接 · 电量 85%", "离线 · 电量暂不可读", "请核对内容 · 剩余 60 秒",
        "输入：WAKE  点击改为 PTT", "音量 50% ＋", "亮度 －", "恢复自动亮度",
        "夜间显示：开", "今天安静：点击恢复", "工作状态：等待休息选择",
        "本地人脸跟随：关", "图像仅本地处理，不上传、不保存", "停止声音和动作",
        "正在处理…", "有字符无法显示，请取消后重新说", "内容已过期，请关闭后重新说"
    };
    for (const char *text : visible_strings) assert(device_ui_font_covers_text(text));
    assert(!device_ui_font_covers_text("未覆盖的表情符号😀"));
    assert(!device_ui_font_covers_text("\xED\xA0\x80"));
    assert(!device_ui_font_covers_text("\xE4\xB8"));
    static device_ui_state_t state = {};
    state.volume_percent = 50;
    state.rest_pending = true;
    strcpy(state.workday_state, "ACTIVE_PRESENT");
    strcpy(state.role.id, "11111111-1111-4111-8111-111111111111");
    strcpy(state.role.name, "小伴：专注工作与温柔关心");
    state.role_count = 2;
    state.roles[0] = state.role;
    strcpy(state.roles[1].id, "22222222-2222-4222-8222-222222222222");
    strcpy(state.roles[1].name, "休息伙伴：放松、散步与生活安排");
    render(display, argv[1], "MENU_ONLINE", &state, nullptr, true, false, false);
    render(display, argv[1], "MENU_PAGE_TWO", &state, nullptr, true, false, false, 0, 1);
    static const char *pages[]={"MENU_INPUT","MENU_VOLUME","MENU_BRIGHTNESS","MENU_QUIET","MENU_WORK","MENU_PARTNER","MENU_FOLLOW","MENU_STATUS"};
    for(int page=1;page<=8;page++) render(display,argv[1],pages[page-1],&state,nullptr,true,false,false,page);
    render(display, argv[1], "MENU_OFFLINE", nullptr, nullptr, false, false, false);
    render(display, argv[1], "MENU_SAVING", &state, nullptr, true, true, false, 2);
    render(display, argv[1], "MENU_OTHER_PAGE_WHILE_SAVING", &state, nullptr, true, true, false, 3);
    static device_ui_card_t card = {};
    strcpy(card.proposal_id, "33333333-3333-4333-8333-333333333333");
    strcpy(card.action_label, "新增待办");
    strcpy(card.title, "整理机器人交互迭代的验收记录");
    strcpy(card.content, "检查舵机运动是否流畅，记录眨眼和情绪变化，核对屏幕确认、触摸手势和控制中心的实际操作结果。");
    strcpy(card.time_label, "2026-10-03 15:00 Asia/Shanghai");
    strcpy(card.role_name, "小伴：专注工作与温柔关心");
    strcpy(card.status, "PENDING");
    card.expires_in_seconds = 60;
    render(display, argv[1], "CARD_PENDING", &state, &card, true, false, true);
    lv_obj_scroll_to_y(scroll_object(), 10000, LV_ANIM_OFF);
    snapshot(display, argv[1], "CARD_CONTENT_SCROLL");
    render(display, argv[1], "CARD_WAITING_SHOWN", &state, &card, true, false, false);
    render(display, argv[1], "CARD_BUSY", &state, &card, true, true, true);
    render(display, argv[1], "CARD_OFFLINE", &state, &card, false, false, true);
    std::string long_content;
    for (unsigned index = 0; index < 35; index++)
        long_content += "这是长内容与换行验证：不得遮挡确认和取消按钮。\n";
    assert(long_content.size() < sizeof(card.content));
    strcpy(card.content, long_content.c_str());
    strcpy(card.title, "一个需要换行显示的很长中文待办标题，包含具体任务与验收条件，必须让用户能够核对固定内容");
    render(display, argv[1], "CARD_LONG_TITLE", &state, &card, true, false, true);
    lv_obj_scroll_to_y(scroll_object(), 10000, LV_ANIM_OFF);
    snapshot(display, argv[1], "CARD_LONG_BOTTOM");
    strcpy(card.status, "EXECUTED");
    strcpy(card.result_message, "已执行");
    render(display, argv[1], "CARD_EXECUTED", &state, &card, true, false, true);
    strcpy(card.status, "EXPIRED");
    strcpy(card.result_message, "已过期，请重新说");
    render(display, argv[1], "CARD_EXPIRED", &state, &card, true, false, false);
    lv_display_delete(display);
    lv_deinit();
    puts("PASS production LVGL menu/card drawing, actual CJK font, long scroll, online/offline/busy/expired states");
    return 0;
}
