#include <cassert>
#include <cstdio>
#include <cstring>
#include <stdexcept>

#define STACKCHAN_FACE_TRACKING_HOST_TEST 1
#include "../../main/face_tracking.cpp"

int64_t fake_now_us;
unsigned fake_camera_starts, fake_opens, fake_closes, fake_streamons, fake_streamoffs;
unsigned fake_model_loads, fake_model_deletes, fake_stop_failures;
bool fake_bad_frame, fake_resources = true;
std::function<void()> fake_inference_hook;
std::function<void()> fake_wait_hook;
static void (*fake_task_fn)(void *);
static unsigned fake_task_creates;
static bool fake_task_failure;
static std::function<void()> fake_task_create_hook;
static uint8_t fake_frames[2][320 * 240 * 2];
static bool fake_streaming;
static bool fake_frame_returned;
static bool fake_bad_map;
static unsigned fake_dqbuf_timeout_ms;
struct end_worker {};

int xTaskCreate(void (*fn)(void *), const char *, unsigned stack, void *, unsigned, TaskHandle_t *handle)
{
    assert(stack >= 8192);
    ++fake_task_creates;
    if (fake_task_failure) return 0;
    if (fake_task_create_hook) fake_task_create_hook();
    fake_task_fn = fn;
    *handle = reinterpret_cast<TaskHandle_t>(1);
    return pdPASS;
}
void xTaskNotifyGive(TaskHandle_t) {}
unsigned ulTaskNotifyTake(int, unsigned wait_ms)
{
    fake_now_us += wait_ms * 1000;
    assert(wait_ms <= 333);
    if (fake_wait_hook) fake_wait_hook();
    return 0;
}
int64_t esp_timer_get_time() { return fake_now_us; }
void esp_log_level_set(const char *, int) {}
size_t heap_caps_get_free_size(int) { return fake_resources ? 2 * 1024 * 1024 : 0; }
size_t heap_caps_get_largest_free_block(int) { return heap_caps_get_free_size(0); }
esp_err_t esp_video_init(const void *) { ++fake_camera_starts; return ESP_OK; }
int fake_camera_open(const char *, int) { ++fake_opens; return 7; }
int fake_camera_close(int)
{
    assert(!fake_streaming); /* Freeing DMA buffers during capture is forbidden. */
    for (const auto &frame : fake_frames)
        for (uint8_t byte : frame) assert(byte == 0);
    ++fake_closes;
    return 0;
}
int fake_camera_ioctl(int, int cmd, ...)
{
    va_list args;
    va_start(args, cmd);
    void *argument = va_arg(args, void *);
    va_end(args);
    switch (static_cast<unsigned>(cmd)) {
    case VIDIOC_G_FMT: {
        auto format = static_cast<v4l2_format *>(argument);
        format->fmt.pix.width = 320;
        format->fmt.pix.height = 240;
        format->fmt.pix.pixelformat = V4L2_PIX_FMT_RGB565X;
        break;
    }
    case VIDIOC_S_DQBUF_TIMEOUT:
        fake_dqbuf_timeout_ms = static_cast<timeval *>(argument)->tv_usec / 1000;
        break;
    case VIDIOC_REQBUFS:
        assert(static_cast<v4l2_requestbuffers *>(argument)->count == 2);
        break;
    case VIDIOC_QUERYBUF: {
        auto buffer = static_cast<v4l2_buffer *>(argument);
        buffer->length = sizeof(fake_frames[0]);
        buffer->m.offset = buffer->index;
        break;
    }
    case VIDIOC_STREAMON:
        assert(!fake_streaming && fake_dqbuf_timeout_ms == 100);
        ++fake_streamons;
        fake_streaming = true;
        break;
    case VIDIOC_STREAMOFF:
        assert(fake_streaming);
        ++fake_streamoffs;
        if (fake_stop_failures) { --fake_stop_failures; return -1; }
        fake_streaming = false;
        break;
    case VIDIOC_DQBUF: {
        assert(fake_streaming && !fake_frame_returned);
        auto buffer = static_cast<v4l2_buffer *>(argument);
        buffer->index = 0;
        buffer->bytesused = sizeof(fake_frames[0]);
        buffer->flags = fake_bad_frame ? V4L2_BUF_FLAG_ERROR : 0;
        fake_frame_returned = true;
        memset(fake_frames[0], 0xA5, sizeof(fake_frames[0]));
        break;
    }
    case VIDIOC_QBUF:
        fake_frame_returned = false;
        break;
    default:
        assert(false);
    }
    return 0;
}
void *fake_camera_mmap(void *, size_t, int, int, int, off_t offset)
{
    if (fake_bad_map) return MAP_FAILED;
    memset(fake_frames[offset], 0xA5, sizeof(fake_frames[offset]));
    return fake_frames[offset];
}
int fake_camera_munmap(void *, size_t) { assert(!fake_streaming); return 0; }

static face_tracking_state_t state()
{
    face_tracking_state_t result;
    face_tracking_get_state(&result);
    return result;
}

static void reset()
{
    assert(!fake_streaming);
    s_task = nullptr;
    s_initialized = s_worker_starting = false;
    fake_task_creates = 0; fake_task_failure = false;
    fake_task_create_hook = {}; fake_task_fn = nullptr;
    s_state = {};
    s_capture_allowed = s_head_allowed = s_head_pending = false;
    s_generation = 0;
    fake_opens = fake_closes = fake_streamons = fake_streamoffs = 0;
    fake_model_loads = fake_model_deletes = fake_stop_failures = 0;
    fake_bad_frame = false;
    fake_resources = true;
    fake_inference_hook = fake_wait_hook = {};
    fake_now_us = 1000000;
    assert(face_tracking_init() == ESP_OK);
    assert(state().available && !state().enabled && !state().running);
    assert(fake_task_creates == 0 && fake_task_fn == nullptr);
}

static void run_worker()
{
    try { fake_task_fn(nullptr); } catch (const end_worker &) {}
    assert(!fake_streaming);
}

static void test_lazy_worker_allocation()
{
    reset();
    assert(face_tracking_init() == ESP_OK && fake_task_creates == 0);
    face_tracking_set_runtime_gate(true, true);
    assert(fake_task_creates == 0 && !state().enabled);
    fake_task_failure = true;
    assert(!face_tracking_set_enabled(true));
    assert(!state().enabled && state().failure == FACE_TRACKING_FAILURE_RESOURCE);
    fake_task_failure = false;
    fake_task_create_hook = [] { assert(face_tracking_set_enabled(false)); };
    assert(!face_tracking_set_enabled(true));
    assert(!state().enabled && fake_task_creates == 2);
    fake_task_create_hook = {};
    assert(face_tracking_set_enabled(true) && fake_task_creates == 2);
    assert(face_tracking_set_enabled(false) && !state().enabled);
    assert(fake_opens == 0 && fake_model_loads == 0);
    puts("PASS zero default-off stack allocation, allocation retry and disable-during-create race");
}

static void test_defaults_and_modal_release()
{
    reset();
    face_tracking_set_runtime_gate(true, true);
    assert(!state().enabled && fake_opens == 0 && fake_model_loads == 0);
    assert(face_tracking_set_enabled(true) && fake_task_creates == 1);
    unsigned phase = 1;
    fake_wait_hook = [&] {
        auto current = state();
        if (phase == 1 && fake_now_us > 8000000) {
            assert(current.running && current.face_present && current.gaze_x > 0);
            face_tracking_head_goal_t goal;
            assert(face_tracking_take_head_goal(&goal));
            assert(goal.yaw_offset_deg > 0 && goal.yaw_offset_deg <= 4);
            face_tracking_set_runtime_gate(false, false);
            assert(!state().face_present && state().gaze_x == 0);
            assert(!face_tracking_take_head_goal(&goal));
            phase = 2;
        } else if (phase == 2) {
            assert(!current.running && fake_closes == 1 && fake_model_deletes == 1);
            assert(fake_streamoffs == 1);
            throw end_worker{};
        }
    };
    run_worker();
    puts("PASS default-off capture, local activation and modal stop/model/buffer release");
}

static void test_gate_closing_during_inference_and_failed_stop()
{
    reset();
    face_tracking_set_enabled(true);
    face_tracking_set_runtime_gate(true, true);
    fake_stop_failures = 1;
    fake_inference_hook = [&] { face_tracking_set_runtime_gate(false, false); };
    fake_wait_hook = [&] {
        auto current = state();
        assert(!current.face_present && current.gaze_x == 0 && current.sampled_frames == 0);
        face_tracking_head_goal_t goal;
        assert(!face_tracking_take_head_goal(&goal));
        if (fake_streamoffs == 1) {
            assert(current.running && fake_closes == 0); /* Keep DMA buffer on stop failure. */
            assert(fake_frames[0][0] == 0xA5);
        }
        if (fake_closes) {
            assert(fake_streamoffs == 2 && fake_model_deletes == 1 && !current.running);
            assert(current.failure == FACE_TRACKING_FAILURE_CAMERA);
            throw end_worker{};
        }
    };
    run_worker();
    puts("PASS in-flight result invalidation and safe retry after STREAMOFF failure");
}

static void test_resource_and_frame_failure()
{
    reset();
    fake_resources = false;
    face_tracking_set_enabled(true);
    face_tracking_set_runtime_gate(true, true);
    fake_wait_hook = [&] {
        assert(fake_opens == 0 && state().failure == FACE_TRACKING_FAILURE_RESOURCE);
        assert(!state().running);
        throw end_worker{};
    };
    run_worker();
    reset();
    fake_bad_map = true;
    face_tracking_set_enabled(true);
    face_tracking_set_runtime_gate(true, true);
    fake_wait_hook = [&] {
        assert(fake_opens == 1 && fake_closes == 1 && fake_model_loads == 0);
        assert(!state().running && state().failure == FACE_TRACKING_FAILURE_RESOURCE);
        throw end_worker{};
    };
    run_worker();
    fake_bad_map = false;
    reset();
    fake_bad_frame = true;
    face_tracking_set_enabled(true);
    face_tracking_set_runtime_gate(true, true);
    unsigned waits = 0;
    fake_wait_hook = [&] {
        assert(fake_opens == 1 && fake_closes == 1 && fake_model_deletes == 1);
        assert(!state().face_present && !state().running && state().failure == FACE_TRACKING_FAILURE_FRAME);
        if (++waits == 3) throw end_worker{}; /* Failed session must not continuously restart. */
    };
    run_worker();
    puts("PASS resource admission, failed mmap/frame rejection and bounded failure recovery");
}

int main()
{
    test_lazy_worker_allocation();
    test_defaults_and_modal_release();
    test_gate_closing_during_inference_and_failed_stop();
    test_resource_and_frame_failure();
}
