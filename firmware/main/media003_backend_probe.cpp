#include "media003_backend_probe.h"

#include <cstring>

#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
typedef struct {
    uint32_t asset_bytes;
    uint32_t init_time_us;
    uint32_t heap_delta_bytes;
    bool ready;
} media003_eaf_probe_diagnostics_t;
extern "C" void media003_eaf_probe_init(lv_obj_t *screen, int x, int y);
extern "C" bool media003_eaf_probe_set_active(bool active);
extern "C" void media003_eaf_probe_get_diagnostics(
    media003_eaf_probe_diagnostics_t *diagnostics);
#elif defined(STACKCHAN_MEDIA003_EMOTE_PROBE)
typedef struct {
    uint32_t init_time_us;
    uint32_t heap_delta_bytes;
    bool ready;
} media003_emote_probe_diagnostics_t;
extern "C" void media003_emote_probe_run(void);
extern "C" void media003_emote_probe_get_diagnostics(
    media003_emote_probe_diagnostics_t *diagnostics);
#endif

extern "C" void media003_backend_probe_init(lv_obj_t *screen, int x, int y)
{
#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
    media003_eaf_probe_init(screen, x, y);
#elif defined(STACKCHAN_MEDIA003_EMOTE_PROBE)
    (void)screen;
    (void)x;
    (void)y;
    media003_emote_probe_run();
#else
    (void)screen;
    (void)x;
    (void)y;
#endif
}

extern "C" bool media003_backend_probe_set_active(bool active)
{
#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
    return media003_eaf_probe_set_active(active);
#else
    (void)active;
    return false;
#endif
}

extern "C" void media003_backend_probe_get_diagnostics(
    media003_backend_probe_diagnostics_t *diagnostics)
{
    if (diagnostics == nullptr) return;
    std::memset(diagnostics, 0, sizeof(*diagnostics));
#if defined(STACKCHAN_MEDIA003_EAF_PROBE)
    media003_eaf_probe_diagnostics_t source = {};
    media003_eaf_probe_get_diagnostics(&source);
    diagnostics->backend = "eaf";
    diagnostics->asset_bytes = source.asset_bytes;
    diagnostics->init_time_us = source.init_time_us;
    diagnostics->heap_delta_bytes = source.heap_delta_bytes;
    diagnostics->ready = source.ready;
#elif defined(STACKCHAN_MEDIA003_EMOTE_PROBE)
    media003_emote_probe_diagnostics_t source = {};
    media003_emote_probe_get_diagnostics(&source);
    diagnostics->backend = "emote";
    diagnostics->init_time_us = source.init_time_us;
    diagnostics->heap_delta_bytes = source.heap_delta_bytes;
    diagnostics->ready = source.ready;
#else
    diagnostics->backend = "native";
    diagnostics->ready = true;
#endif
}
