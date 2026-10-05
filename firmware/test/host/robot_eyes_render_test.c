#include "robot_eyes_renderer.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SIZE 192
static uint32_t display_buffer[SIZE * SIZE];
static uint8_t image_buffer[SIZE * SIZE * 3];
static unsigned frames;
static unsigned pixels_flushed;
static uint8_t partial_frame[SIZE*SIZE*3];

static void flush(lv_display_t *display, const lv_area_t *area, uint8_t *pixels)
{
    for (int32_t y = area->y1; y <= area->y2; y++) {
        for (int32_t x = area->x1; x <= area->x2; x++) {
            size_t destination = ((size_t)y * SIZE + x) * 3;
            size_t source = ((size_t)(y - area->y1) * lv_area_get_width(area) + x - area->x1);
#ifdef HOST_RGB565
            uint16_t color;memcpy(&color,pixels+source*2,sizeof(color));
            image_buffer[destination]=(color>>11)*255/31;
            image_buffer[destination+1]=((color>>5)&63)*255/63;
            image_buffer[destination+2]=(color&31)*255/31;
#else
            source*=4;
            image_buffer[destination] = pixels[source + 2];
            image_buffer[destination + 1] = pixels[source + 1];
            image_buffer[destination + 2] = pixels[source];
#endif
        }
    }
    frames++;
    pixels_flushed+=(unsigned)lv_area_get_size(area);
    lv_display_flush_ready(display);
}

static void snapshot(lv_display_t *display, const char *directory, const char *name)
{
    unsigned before = frames;
    lv_refr_now(display);
    assert(frames > before);
    char filename[512];
    assert(snprintf(filename, sizeof(filename), "%s/%s.ppm", directory, name) < (int)sizeof(filename));
    FILE *file = fopen(filename, "wb");
    assert(file != NULL);
    assert(fprintf(file, "P6\n%d %d\n255\n", SIZE, SIZE) > 0);
    assert(fwrite(image_buffer, sizeof(image_buffer), 1, file) == 1);
    assert(fclose(file) == 0);
}

static companion_expression_pose_t fixture(companion_expression_engine_t *engine,
                                            companion_expression_preview_t kind, uint8_t value)
{
    companion_expression_engine_init(engine, 0);
    companion_expression_engine_trigger(engine, COMPANION_BEHAVIOR_NONE, 0, 0);
    companion_expression_engine_preview(engine, kind, value, 15000, 0);
    companion_expression_pose_t pose;
    companion_expression_engine_tick(engine, 0, &pose);
    companion_expression_engine_tick(engine, 1000, &pose);
    return pose;
}

static void animation(lv_display_t *display, lv_obj_t *eyes, const char *directory,
                      const char *name, companion_face_state_t state, int emotion, bool wake)
{
    companion_expression_engine_t engine;
    companion_expression_engine_init(&engine, 0);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_NONE, 0, 0);
    companion_expression_engine_set_system(&engine, state, 0);
    if (emotion >= 0) companion_expression_engine_suggest_emotion(&engine,
        (companion_emotion_t)emotion, COMPANION_EMOTION_INTENSITY_STRONG, 10000, 0);
    companion_expression_pose_t pose;
    companion_expression_engine_tick(&engine, 0, &pose);
    companion_expression_engine_tick(&engine, 1000, &pose);
    if (wake) companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_WAKE, 900, 1000);
    robot_eyes_renderer_set_status(eyes, state, false, true);
    char filename[512];
    assert(snprintf(filename, sizeof(filename), "%s/ANIMATION_%s.rgb", directory, name) < (int)sizeof(filename));
    FILE *file = fopen(filename, "wb");assert(file != NULL);
    uint8_t first_status[37*24*3];
    unsigned changes = 0;
    float min_y = 10, max_y = -10, min_x = 10, max_x = -10;
    for (uint32_t i = 0; i < 80; i++) {
        companion_expression_engine_tick(&engine, 1000 + i*40, &pose);
        if(pose.offset_y < min_y)min_y=pose.offset_y;
        if(pose.offset_y > max_y)max_y=pose.offset_y;
        if(pose.gaze_x < min_x)min_x=pose.gaze_x;
        if(pose.gaze_x > max_x)max_x=pose.gaze_x;
        robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
        lv_refr_now(display);
        memcpy(partial_frame, image_buffer, sizeof(image_buffer));
        lv_obj_invalidate(eyes);lv_refr_now(display);
        assert(memcmp(partial_frame,image_buffer,sizeof(image_buffer))==0);
        assert(fwrite(image_buffer,sizeof(image_buffer),1,file)==1);
        uint8_t status[sizeof(first_status)];
        for(int row=0;row<24;row++)memcpy(status+row*37*3,image_buffer+((SIZE-25+row)*SIZE+77)*3,37*3);
        if(i==0)memcpy(first_status,status,sizeof(status));
        else if(memcmp(first_status,status,sizeof(status)))changes++;
    }
    assert(fclose(file)==0);
    if(state==COMPANION_FACE_LISTENING || state==COMPANION_FACE_SPEAKING) {
        assert(max_y-min_y > 0.12f && changes > 40);
    }
    if(state==COMPANION_FACE_PROCESSING)assert(max_x-min_x > 0.75f && changes > 40);
    printf("PASS animation %s: 80 dirty/full matches, status changes=%u\n",name,changes);
}

int main(int argc, char **argv)
{
    assert(argc == 2);
    lv_init();
    lv_display_t *display = lv_display_create(SIZE, SIZE);
    assert(display != NULL);
#ifdef HOST_RGB565
    lv_display_set_color_format(display, LV_COLOR_FORMAT_RGB565);
#else
    lv_display_set_color_format(display, LV_COLOR_FORMAT_XRGB8888);
#endif
    lv_display_set_buffers(display, display_buffer, NULL, sizeof(display_buffer), LV_DISPLAY_RENDER_MODE_PARTIAL);
    lv_display_set_flush_cb(display, flush);
    lv_obj_t *screen = lv_screen_active();
    lv_obj_set_style_bg_color(screen, lv_color_black(), 0);
    lv_obj_t *eyes = robot_eyes_renderer_create(screen);
    assert(eyes != NULL && !lv_obj_has_flag(eyes, LV_OBJ_FLAG_CLICKABLE));
    lv_obj_set_pos(eyes, 0, 0);
    companion_expression_engine_t engine;
    companion_expression_pose_t pose;
    static const char *const emotions[] = {
        "NEUTRAL", "HAPPY", "LOVING", "SAD", "ANGRY", "SURPRISED",
        "CONFUSED", "SHY", "TIRED", "FOCUSED", "NERVOUS", "CONTENT"
    };
    for (uint8_t emotion = 0; emotion < COMPANION_EMOTION_COUNT; emotion++) {
        pose = fixture(&engine, COMPANION_EXPRESSION_PREVIEW_EMOTION, emotion);
        robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
        robot_eyes_renderer_set_status(eyes, COMPANION_FACE_IDLE, false, true);
        snapshot(display, argv[1], emotions[emotion]);
    }
    static const char *const states[] = {
        "STATE_IDLE", "STATE_LISTENING", "STATE_PROCESSING", "STATE_SPEAKING",
        "STATE_SUCCESS", "STATE_NO_SPEECH", "STATE_OFFLINE", "STATE_ERROR"
    };
    for (uint8_t state = 0; state <= COMPANION_FACE_RECOVERABLE_ERROR; state++) {
        pose = fixture(&engine, COMPANION_EXPRESSION_PREVIEW_SYSTEM, state);
        robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
        robot_eyes_renderer_set_status(eyes, (companion_face_state_t)state, false, true);
        snapshot(display, argv[1], states[state]);
    }
    pose = fixture(&engine, COMPANION_EXPRESSION_PREVIEW_UPDATING, 0);
    robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
    robot_eyes_renderer_set_status(eyes, COMPANION_FACE_IDLE, true, true);
    snapshot(display, argv[1], "STATE_UPDATING");
    static const char *const behaviors[] = {
        "NONE", "BEHAVIOR_BOOT", "BEHAVIOR_WAKE", "BEHAVIOR_BREATHE",
        "BEHAVIOR_CURIOUS", "BEHAVIOR_DIZZY", "BEHAVIOR_SLEEP", "BEHAVIOR_ROLE"
    };
    for (uint8_t behavior = COMPANION_BEHAVIOR_BOOT_APPEAR; behavior <= COMPANION_BEHAVIOR_ROLE_SWITCH; behavior++) {
        pose = fixture(&engine, COMPANION_EXPRESSION_PREVIEW_BEHAVIOR, behavior);
        robot_eyes_renderer_update(eyes, &pose, 0xFF4FA3);
        robot_eyes_renderer_set_status(eyes, COMPANION_FACE_IDLE, false, true);
        snapshot(display, argv[1], behaviors[behavior]);
    }
    pose = fixture(&engine, COMPANION_EXPRESSION_PREVIEW_EMOTION, COMPANION_EMOTION_NEUTRAL);
    pose.left_eye_open = 0.0f;
    robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
    robot_eyes_renderer_set_status(eyes, COMPANION_FACE_IDLE, false, false);
    snapshot(display, argv[1], "INDEPENDENT_BLINK_PTT");
    pose.left_eye_open = 1.0f;
    pose.right_upper_lid = 0.65f;
    pose.left_lower_lid = 0.40f;
    robot_eyes_renderer_update(eyes, &pose, 0x000000);
    snapshot(display, argv[1], "INDEPENDENT_LIDS_DARK_THEME");
    /* Non-finite pose values must never become unbounded draw coordinates. */
    pose.gaze_x = NAN;
    pose.eye_open = INFINITY;
    pose.orbit = NAN;
    robot_eyes_renderer_update(eyes, &pose, 0x55DDEA);
    snapshot(display, argv[1], "INVALID_POSE_BOUNDED");
    /* Dirty rectangles must erase the previous eye/blush without trails. Compare
     * the actual partial buffer with a fresh full repaint through 240 transitions. */
    unsigned partial_pixels=0;
    for(unsigned i=0;i<240;i++) {
        pose=fixture(&engine,COMPANION_EXPRESSION_PREVIEW_EMOTION,i%COMPANION_EMOTION_COUNT);
        pose.gaze_x=sinf(i*0.21f);pose.gaze_y=cosf(i*0.19f);
        pose.offset_x=sinf(i*0.07f);pose.offset_y=cosf(i*0.09f);
        pose.blush=i%3==0?1.0f:0.0f;
        unsigned before=pixels_flushed;
        robot_eyes_renderer_update(eyes,&pose,i%2?0xff4fa3:0x55ddea);
        robot_eyes_renderer_set_status(eyes,(companion_face_state_t)(i%8),i%11==0,i%2==0);
        lv_refr_now(display);
        partial_pixels+=pixels_flushed-before;
        memcpy(partial_frame,image_buffer,sizeof(image_buffer));
        lv_obj_invalidate(eyes);lv_refr_now(display);
        assert(memcmp(partial_frame,image_buffer,sizeof(image_buffer))==0);
    }
    assert(partial_pixels<240*SIZE*SIZE*3/4);
    printf("PASS 240 dirty/full pixel matches; partial pixels=%u, full=%u\n",partial_pixels,240*SIZE*SIZE);
    unsigned before=frames;
    robot_eyes_renderer_update(eyes,&pose,239%2?0xff4fa3:0x55ddea);
    robot_eyes_renderer_set_status(eyes,(companion_face_state_t)(239%8),239%11==0,239%2==0);
    lv_refr_now(display);assert(frames==before); /* Unchanged eyes cause no transfer. */
    animation(display,eyes,argv[1],"IDLE",COMPANION_FACE_IDLE,-1,false);
    animation(display,eyes,argv[1],"LISTENING",COMPANION_FACE_LISTENING,-1,false);
    animation(display,eyes,argv[1],"WAKE_LISTENING",COMPANION_FACE_LISTENING,-1,true);
    animation(display,eyes,argv[1],"PROCESSING",COMPANION_FACE_PROCESSING,-1,false);
    animation(display,eyes,argv[1],"SPEAKING",COMPANION_FACE_SPEAKING,-1,false);
    animation(display,eyes,argv[1],"HAPPY",COMPANION_FACE_IDLE,COMPANION_EMOTION_HAPPY,false);
    animation(display,eyes,argv[1],"LOVING",COMPANION_FACE_IDLE,COMPANION_EMOTION_LOVING,false);
    animation(display,eyes,argv[1],"CONFUSED",COMPANION_FACE_IDLE,COMPANION_EMOTION_CONFUSED,false);
    lv_obj_delete(eyes);
    lv_display_delete(display);
    lv_deinit();
    puts("PASS real LVGL renders 12 emotions, 9 system states, 7 behaviors, independent lids/blink, status modes");
    return 0;
}
