#pragma once

#include <cstdarg>
#include <cstdint>
#include <fcntl.h>
#include <functional>
#include <list>
#include <linux/videodev2.h>
#include <new>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <vector>

/* Only external APIs are replaced; the production session/gate/worker code runs unchanged. */
using portMUX_TYPE = int;
using TaskHandle_t = void *;
#define portMUX_INITIALIZER_UNLOCKED 0
#define portENTER_CRITICAL(mutex) ((void)(mutex))
#define portEXIT_CRITICAL(mutex) ((void)(mutex))
#define pdMS_TO_TICKS(ms) (ms)
#define pdTRUE 1
#define pdPASS 1
#define MALLOC_CAP_SPIRAM 1
#define MALLOC_CAP_INTERNAL 2
#define ESP_LOG_WARN 2
#define BSP_CAMERA_DEVICE "/fake/video"
#define VIDIOC_S_DQBUF_TIMEOUT _IOWR('V', BASE_VIDIOC_PRIVATE + 6, struct timeval)

extern int64_t fake_now_us;
extern unsigned fake_camera_starts, fake_opens, fake_closes, fake_streamons, fake_streamoffs;
extern unsigned fake_model_loads, fake_model_deletes, fake_stop_failures;
extern bool fake_bad_frame, fake_resources;
extern std::function<void()> fake_inference_hook;
extern std::function<void()> fake_wait_hook;

int xTaskCreate(void (*fn)(void *), const char *, unsigned, void *, unsigned, TaskHandle_t *);
void xTaskNotifyGive(TaskHandle_t);
unsigned ulTaskNotifyTake(int, unsigned wait_ms);
int64_t esp_timer_get_time();
void esp_log_level_set(const char *, int);
size_t heap_caps_get_free_size(int);
size_t heap_caps_get_largest_free_block(int);
esp_err_t esp_video_init(const void *);
int fake_camera_open(const char *, int);
int fake_camera_close(int);
int fake_camera_ioctl(int, int, ...);
void *fake_camera_mmap(void *, size_t, int, int, int, off_t);
int fake_camera_munmap(void *, size_t);

#define open fake_camera_open
#define close fake_camera_close
#define ioctl fake_camera_ioctl
#define mmap fake_camera_mmap
#define munmap fake_camera_munmap

namespace dl { namespace image {
enum pix_type_t { DL_IMAGE_PIX_TYPE_RGB565BE };
struct img_t { void *data; uint16_t width, height; pix_type_t pix_type; };
} namespace detect {
struct result_t { std::vector<int> box; float score; };
}}

class HumanFaceDetect {
public:
    enum model_type_t { MSRMNP_S8_V1 };
    HumanFaceDetect(model_type_t, bool) {}
    ~HumanFaceDetect() { ++fake_model_deletes; }
    void set_score_thr(float, int) {}
    void get_raw_model(int) { ++fake_model_loads; }
    std::list<dl::detect::result_t> &run(const dl::image::img_t &image)
    {
        (void)image;
        fake_now_us += 45000;
        if (fake_inference_hook) fake_inference_hook();
        static std::list<dl::detect::result_t> result = {{{224, 24, 288, 72}, 0.95f}};
        return result;
    }
};
