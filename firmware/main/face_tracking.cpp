#include "face_tracking.h"

#include <string.h>
#if !defined(STACKCHAN_FACE_TRACKING_HOST_TEST)
#include "sdkconfig.h"
#endif

#if defined(STACKCHAN_FACE_TRACKING_HOST_TEST) || (CONFIG_IDF_TARGET_ESP32S3 && !CONFIG_STACKCHAN_PROTOCOL_TESTS)

#if defined(STACKCHAN_FACE_TRACKING_HOST_TEST)
#include "face_tracking_runtime_stub.hpp"
#else
#include <fcntl.h>
#include <new>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <unistd.h>

#include "bsp/m5stack_core_s3.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_private/i2c_platform.h"
#include "esp_timer.h"
#include "esp_video_device.h"
#include "esp_video_init.h"
#include "esp_video_ioctl.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "human_face_detect.hpp"
#endif

#define FACE_TRACKING_TASK_STACK_SIZE 12288
#define FACE_TRACKING_SAMPLE_INTERVAL_MS 333
#define FACE_TRACKING_FRAME_TIMEOUT_MS 100
#define FACE_TRACKING_FRAME_WIDTH 320
#define FACE_TRACKING_FRAME_HEIGHT 240
#define FACE_TRACKING_BUFFER_COUNT 2

/* One low-priority owner serializes capture/inference/teardown. No frame leaves this file. */
static portMUX_TYPE s_mutex = portMUX_INITIALIZER_UNLOCKED;
static TaskHandle_t s_task;
static face_tracking_state_t s_state;
static bool s_initialized, s_worker_starting;
static bool s_capture_allowed, s_head_allowed, s_head_pending;
static face_tracking_head_goal_t s_head_goal;
static uint32_t s_generation;

struct camera_session_t {
    int fd = -1;
    bool streaming = false;
    void *buffers[FACE_TRACKING_BUFFER_COUNT] = {};
    size_t lengths[FACE_TRACKING_BUFFER_COUNT] = {};
    HumanFaceDetect *detector = nullptr;
};

static bool gate_snapshot(uint32_t *generation, bool *head_allowed)
{
    portENTER_CRITICAL(&s_mutex);
    bool allowed = s_state.enabled && s_capture_allowed;
    *generation = s_generation;
    *head_allowed = s_head_allowed;
    portEXIT_CRITICAL(&s_mutex);
    return allowed;
}

static bool same_capture_gate(uint32_t generation)
{
    uint32_t current;
    bool ignored;
    return gate_snapshot(&current, &ignored) && current == generation;
}

static void invalidate_output_locked(void)
{
    s_head_pending = false;
    s_state.face_present = false;
    s_state.should_recenter = false;
    s_state.gaze_x = s_state.gaze_y = 0;
}

static void set_failure(face_tracking_failure_t failure)
{
    portENTER_CRITICAL(&s_mutex);
    s_state.failure = failure;
    invalidate_output_locked();
    portEXIT_CRITICAL(&s_mutex);
}

/* esp_video 2.0.1 rejects REQBUFS(count=0). close() is its actual buffer-release path.
 * If STREAMOFF fails, retain the DMA-owned buffers and retry, avoiding use-after-free. */
static bool release_session(camera_session_t *session)
{
    delete session->detector;
    session->detector = nullptr;
    if (session->streaming) {
        int type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if (ioctl(session->fd, VIDIOC_STREAMOFF, &type) != 0) {
            set_failure(FACE_TRACKING_FAILURE_CAMERA);
            return false;
        }
        session->streaming = false;
    }
    for (unsigned i = 0; i < FACE_TRACKING_BUFFER_COUNT; ++i) {
        if (session->buffers[i]) {
            /* Erase local image contents before returning them to the allocator. */
            memset(session->buffers[i], 0, session->lengths[i]);
            munmap(session->buffers[i], session->lengths[i]);
            session->buffers[i] = nullptr;
            session->lengths[i] = 0;
        }
    }
    if (session->fd >= 0) {
        close(session->fd);
        session->fd = -1;
    }
    portENTER_CRITICAL(&s_mutex);
    s_state.running = false;
    invalidate_output_locked();
    portEXIT_CRITICAL(&s_mutex);
    /* Do not deinit BSP video or disable BSP_FEATURE_CAMERA: LTR553 owns the shared rail. */
    return true;
}

static bool resource_budget_available(void)
{
    /* Conservative admission, not a claim of measured target peak usage. Reserve room
     * for two 150 KiB frames, model arenas, UI and automatic WakeNet in Quad PSRAM. */
    return heap_caps_get_free_size(MALLOC_CAP_SPIRAM) >= 1024 * 1024 &&
           heap_caps_get_largest_free_block(MALLOC_CAP_SPIRAM) >= 512 * 1024 &&
           heap_caps_get_free_size(MALLOC_CAP_INTERNAL) >= 64 * 1024 &&
           heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL) >= 16 * 1024;
}

static esp_err_t initialize_camera(void)
{
#if defined(STACKCHAN_FACE_TRACKING_HOST_TEST)
    return esp_video_init(nullptr);
#else
    /* The body owns and enables the LTR553/GC0308 shared rail. Reuse its bus;
     * do not call BSP power helpers whose legacy error mode can abort at runtime. */
    i2c_master_bus_handle_t bus = nullptr;
    esp_err_t err = i2c_master_get_bus_handle(BSP_I2C_NUM, &bus);
    if (err != ESP_OK || !bus) return err == ESP_OK ? ESP_ERR_INVALID_STATE : err;
    esp_video_init_dvp_config_t dvp = {};
    dvp.sccb_config.init_sccb = false;
    dvp.sccb_config.i2c_handle = bus;
    dvp.sccb_config.freq = 100000;
    dvp.reset_pin = BSP_CAMERA_RST;
    dvp.pwdn_pin = static_cast<gpio_num_t>(-1);
    dvp.dvp_pin.data_width = CAM_CTLR_DATA_WIDTH_8;
    const gpio_num_t data_pins[] = {
        BSP_CAMERA_D0, BSP_CAMERA_D1, BSP_CAMERA_D2, BSP_CAMERA_D3,
        BSP_CAMERA_D4, BSP_CAMERA_D5, BSP_CAMERA_D6, BSP_CAMERA_D7,
    };
    for (unsigned i = 0; i < 8; ++i) dvp.dvp_pin.data_io[i] = data_pins[i];
    dvp.dvp_pin.vsync_io = BSP_CAMERA_VSYNC;
    dvp.dvp_pin.de_io = BSP_CAMERA_HSYNC;
    dvp.dvp_pin.pclk_io = BSP_CAMERA_PCLK;
    dvp.dvp_pin.xclk_io = BSP_CAMERA_GPIO_XCLK;
    dvp.xclk_freq = BSP_CAMERA_XCLK_CLOCK_MHZ * 1000000;
    esp_video_init_config_t video = {};
    video.dvp = &dvp;
    return esp_video_init(&video);
#endif
}

static bool start_session(camera_session_t *session, uint32_t generation)
{
    static bool camera_ready;
    if (!resource_budget_available()) {
        set_failure(FACE_TRACKING_FAILURE_RESOURCE);
        return false;
    }
    if (!same_capture_gate(generation)) return false;
    if (!camera_ready) {
        if (initialize_camera() != ESP_OK) {
            set_failure(FACE_TRACKING_FAILURE_CAMERA);
            return false;
        }
        camera_ready = true;
    }
    if (!same_capture_gate(generation)) return false;
    session->fd = open(BSP_CAMERA_DEVICE, O_RDONLY);
    if (session->fd < 0) {
        set_failure(FACE_TRACKING_FAILURE_CAMERA);
        return false;
    }
    v4l2_format format = {};
    format.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(session->fd, VIDIOC_G_FMT, &format) != 0 ||
        format.fmt.pix.width != FACE_TRACKING_FRAME_WIDTH ||
        format.fmt.pix.height != FACE_TRACKING_FRAME_HEIGHT ||
        format.fmt.pix.pixelformat != V4L2_PIX_FMT_RGB565X) {
        set_failure(FACE_TRACKING_FAILURE_CAMERA);
        return false;
    }
    timeval timeout = {};
    timeout.tv_usec = FACE_TRACKING_FRAME_TIMEOUT_MS * 1000;
    if (ioctl(session->fd, VIDIOC_S_DQBUF_TIMEOUT, &timeout) != 0) {
        set_failure(FACE_TRACKING_FAILURE_CAMERA);
        return false;
    }
    v4l2_requestbuffers request = {};
    request.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    request.memory = V4L2_MEMORY_MMAP;
    request.count = FACE_TRACKING_BUFFER_COUNT;
    if (ioctl(session->fd, VIDIOC_REQBUFS, &request) != 0 || request.count != FACE_TRACKING_BUFFER_COUNT) {
        set_failure(FACE_TRACKING_FAILURE_RESOURCE);
        return false;
    }
    for (unsigned i = 0; i < FACE_TRACKING_BUFFER_COUNT; ++i) {
        v4l2_buffer buffer = {};
        buffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buffer.memory = V4L2_MEMORY_MMAP;
        buffer.index = i;
        if (ioctl(session->fd, VIDIOC_QUERYBUF, &buffer) != 0 ||
            buffer.length < FACE_TRACKING_FRAME_WIDTH * FACE_TRACKING_FRAME_HEIGHT * 2) {
            set_failure(FACE_TRACKING_FAILURE_FRAME);
            return false;
        }
        void *mapped = mmap(nullptr, buffer.length, PROT_READ | PROT_WRITE, MAP_SHARED,
                             session->fd, buffer.m.offset);
        if (!mapped || mapped == MAP_FAILED) {
            set_failure(FACE_TRACKING_FAILURE_RESOURCE);
            return false;
        }
        session->buffers[i] = mapped;
        session->lengths[i] = buffer.length;
        if (ioctl(session->fd, VIDIOC_QBUF, &buffer) != 0) {
            set_failure(FACE_TRACKING_FAILURE_RESOURCE);
            return false;
        }
    }
    if (!same_capture_gate(generation)) return false;
    /* Lazy wrapper creation is bounded here; models load only after local admission. */
    session->detector = new (std::nothrow) HumanFaceDetect(HumanFaceDetect::MSRMNP_S8_V1, true);
    if (!session->detector) {
        set_failure(FACE_TRACKING_FAILURE_RESOURCE);
        return false;
    }
    session->detector->set_score_thr(0.6f, 0);
    session->detector->set_score_thr(0.7f, 1);
    session->detector->get_raw_model(0); /* Loads MSR and MNP; no image is acquired for loading. */
    if (!same_capture_gate(generation)) return false;
    int type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(session->fd, VIDIOC_STREAMON, &type) != 0) {
        set_failure(FACE_TRACKING_FAILURE_CAMERA);
        return false;
    }
    session->streaming = true;
    portENTER_CRITICAL(&s_mutex);
    s_state.running = true;
    if (generation == s_generation && s_state.enabled && s_capture_allowed)
        s_state.failure = FACE_TRACKING_FAILURE_NONE;
    portEXIT_CRITICAL(&s_mutex);
    return true;
}

static bool sample_session(camera_session_t *session, face_tracking_policy_t *policy, uint32_t generation)
{
    v4l2_buffer buffer = {};
    buffer.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buffer.memory = V4L2_MEMORY_MMAP;
    if (ioctl(session->fd, VIDIOC_DQBUF, &buffer) != 0) {
        set_failure(FACE_TRACKING_FAILURE_FRAME);
        return false;
    }
    /* Do not dereference a driver-supplied index or incomplete frame. */
    bool valid = buffer.index < FACE_TRACKING_BUFFER_COUNT &&
        !(buffer.flags & V4L2_BUF_FLAG_ERROR) &&
        buffer.bytesused >= FACE_TRACKING_FRAME_WIDTH * FACE_TRACKING_FRAME_HEIGHT * 2 &&
        buffer.bytesused <= session->lengths[buffer.index];
    face_tracking_candidate_t faces[FACE_TRACKING_MAX_CANDIDATES] = {};
    size_t count = 0;
    uint32_t inference_ms = 0;
    if (valid && same_capture_gate(generation)) {
        dl::image::img_t image = {};
        image.data = session->buffers[buffer.index];
        image.width = FACE_TRACKING_FRAME_WIDTH;
        image.height = FACE_TRACKING_FRAME_HEIGHT;
        image.pix_type = dl::image::DL_IMAGE_PIX_TYPE_RGB565BE;
        int64_t before = esp_timer_get_time();
        auto &detected = session->detector->run(image);
        inference_ms = static_cast<uint32_t>((esp_timer_get_time() - before) / 1000);
        for (const auto &face : detected) {
            if (count == FACE_TRACKING_MAX_CANDIDATES) break;
            if (face.box.size() < 4) continue;
            faces[count++] = {
                static_cast<float>(face.box[0]) / FACE_TRACKING_FRAME_WIDTH,
                static_cast<float>(face.box[1]) / FACE_TRACKING_FRAME_HEIGHT,
                static_cast<float>(face.box[2]) / FACE_TRACKING_FRAME_WIDTH,
                static_cast<float>(face.box[3]) / FACE_TRACKING_FRAME_HEIGHT, face.score,
            };
        }
    }
    if (ioctl(session->fd, VIDIOC_QBUF, &buffer) != 0 || !valid) {
        set_failure(FACE_TRACKING_FAILURE_FRAME);
        return false;
    }
    uint32_t current_generation;
    bool head_allowed;
    bool capture_allowed = gate_snapshot(&current_generation, &head_allowed) && current_generation == generation;
    auto output = face_tracking_policy_update(policy, capture_allowed, head_allowed, faces, count,
                                              static_cast<uint64_t>(esp_timer_get_time() / 1000));
    portENTER_CRITICAL(&s_mutex);
    if (s_state.enabled && s_capture_allowed && generation == s_generation) {
        s_state.face_present = output.face_present;
        s_state.should_recenter = output.should_recenter;
        s_state.gaze_x = output.gaze_x;
        s_state.gaze_y = output.gaze_y;
        ++s_state.sampled_frames;
        s_state.last_inference_ms = inference_ms;
        if (s_head_allowed && output.head_goal_ready) {
            s_head_goal = output.head_goal;
            s_head_pending = true;
        }
    }
    portEXIT_CRITICAL(&s_mutex);
    return true;
}

static void tracking_task(void *)
{
    camera_session_t session;
    face_tracking_policy_t policy;
    face_tracking_policy_init(&policy);
    uint32_t session_generation = 0;
    uint32_t failed_generation = UINT32_MAX;
    for (;;) {
        uint32_t generation;
        bool head_allowed;
        bool allowed = gate_snapshot(&generation, &head_allowed);
        if (session.streaming && !session.detector) {
            /* A failed stop retains its DMA buffers; keep retrying stop even when
             * a sample failure has latched this capture generation as failed. */
            if (!release_session(&session)) {
                ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(100));
                continue;
            }
        }
        if (!allowed || generation != session_generation) {
            if (!release_session(&session)) {
                ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(100));
                continue;
            }
            face_tracking_policy_reset(&policy);
            session_generation = generation;
        }
        if (!allowed || failed_generation == generation) {
            ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(100));
            continue;
        }
        if (session.fd < 0 && !start_session(&session, generation)) {
            bool still_allowed = same_capture_gate(generation);
            release_session(&session);
            if (still_allowed) failed_generation = generation;
            continue;
        }
        if (!same_capture_gate(generation)) continue;
        int64_t before = esp_timer_get_time();
        if (!sample_session(&session, &policy, generation)) {
            release_session(&session);
            failed_generation = generation;
            continue;
        }
        int64_t elapsed_ms = (esp_timer_get_time() - before) / 1000;
        /* Requested 3 Hz budget; actual inference latency is only a local aggregate. */
        int wait_ms = elapsed_ms < FACE_TRACKING_SAMPLE_INTERVAL_MS ? FACE_TRACKING_SAMPLE_INTERVAL_MS - elapsed_ms : 1;
        ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(wait_ms));
    }
}

esp_err_t face_tracking_init(void)
{
    portENTER_CRITICAL(&s_mutex);
    bool first = !s_initialized;
    if (first) {
        memset(&s_state, 0, sizeof(s_state));
        s_state.available = true;
        s_initialized = true;
    }
    portEXIT_CRITICAL(&s_mutex);
    if (first) esp_log_level_set("detect", ESP_LOG_WARN);
    /* Default-off tracking reserves neither its 12 KiB internal stack nor
     * camera/model buffers. Only the explicit local switch creates the worker. */
    return ESP_OK;
}

bool face_tracking_set_enabled(bool enabled)
{
    TaskHandle_t notify = nullptr;
    bool start = false, accepted = true;
    uint32_t generation = 0;
    portENTER_CRITICAL(&s_mutex);
    if (!s_initialized || (enabled && (!s_state.available || s_worker_starting))) {
        accepted = false;
    } else if (enabled && !s_task) {
        start = s_worker_starting = true;
        generation = s_generation;
    } else {
        if (s_state.enabled != enabled || (!enabled && s_worker_starting)) {
            s_state.enabled = enabled;
            s_state.failure = FACE_TRACKING_FAILURE_NONE;
            ++s_generation;
            invalidate_output_locked();
        }
        notify = s_task;
    }
    portEXIT_CRITICAL(&s_mutex);
    if (start) {
        TaskHandle_t created_task = nullptr;
        bool created = xTaskCreate(tracking_task, "face_tracking", FACE_TRACKING_TASK_STACK_SIZE,
                                   nullptr, 2, &created_task) == pdPASS;
        portENTER_CRITICAL(&s_mutex);
        s_worker_starting = false;
        if (created) {
            s_task = created_task;
            accepted = generation == s_generation;
            if (accepted) {
                s_state.enabled = true;
                s_state.failure = FACE_TRACKING_FAILURE_NONE;
                ++s_generation;
                invalidate_output_locked();
            }
        } else {
            accepted = false;
            s_state.failure = FACE_TRACKING_FAILURE_RESOURCE;
        }
        notify = s_task;
        portEXIT_CRITICAL(&s_mutex);
    }
    if (notify) xTaskNotifyGive(notify);
    return accepted;
}

void face_tracking_set_runtime_gate(bool allow_capture, bool allow_head_motion)
{
    portENTER_CRITICAL(&s_mutex);
    bool changed = s_capture_allowed != allow_capture || s_head_allowed != allow_head_motion;
    if (s_capture_allowed != allow_capture) ++s_generation;
    s_capture_allowed = allow_capture;
    s_head_allowed = allow_capture && allow_head_motion;
    if (!allow_capture) invalidate_output_locked();
    if (!s_head_allowed) s_head_pending = false;
    portEXIT_CRITICAL(&s_mutex);
    if (changed && s_task) xTaskNotifyGive(s_task);
}

void face_tracking_get_state(face_tracking_state_t *state)
{
    if (!state) return;
    portENTER_CRITICAL(&s_mutex);
    *state = s_state;
    portEXIT_CRITICAL(&s_mutex);
}

bool face_tracking_take_head_goal(face_tracking_head_goal_t *goal)
{
    if (!goal) return false;
    portENTER_CRITICAL(&s_mutex);
    bool ready = s_state.enabled && s_capture_allowed && s_head_allowed && s_head_pending;
    if (ready) *goal = s_head_goal;
    s_head_pending = false;
    portEXIT_CRITICAL(&s_mutex);
    return ready;
}

#else

esp_err_t face_tracking_init(void) { return ESP_OK; }
bool face_tracking_set_enabled(bool enabled) { return !enabled; }
void face_tracking_set_runtime_gate(bool, bool) {}
void face_tracking_get_state(face_tracking_state_t *state)
{
    if (state) {
        memset(state, 0, sizeof(*state));
        state->failure = FACE_TRACKING_FAILURE_UNSUPPORTED;
    }
}
bool face_tracking_take_head_goal(face_tracking_head_goal_t *) { return false; }

#endif
