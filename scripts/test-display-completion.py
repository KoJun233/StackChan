"""Check the real flush-ready wrapper preserves readiness and bounded samples.

Only the public display/time/critical-section boundaries are fixtures. This
checks accounting and readiness, not physical DMA timing or ISR stack usage.
"""
import hashlib
from pathlib import Path
import subprocess


def main():
    root=Path(__file__).resolve().parents[1]
    output=root/".tools/display-completion-qa"
    output.mkdir(parents=True,exist_ok=True)
    source=(root/"firmware/main/companion_hardware.cpp").read_text(encoding="utf-8")
    start=source.index("static portMUX_TYPE s_transfer_lock=")
    end=source.index("static int compare_interval",start)
    production=source[start:end]
    prefix=r'''
#include <cassert>
#include <cstdint>
#define IRAM_ATTR
typedef int portMUX_TYPE;
#define portMUX_INITIALIZER_UNLOCKED 0
struct lv_display_t { bool last; unsigned ready; };
static lv_display_t primary, other;
static lv_display_t *s_display=&primary;
static int64_t now_us;
static bool isr;
static unsigned critical_depth, task_entries, isr_entries;
static bool lv_display_flush_is_last(lv_display_t *d) { return d->last; }
static int64_t esp_timer_get_time() { return now_us; }
static bool xPortInIsrContext() { return isr; }
#define taskENTER_CRITICAL(lock) do { (void)(lock); assert(!isr && critical_depth++==0); task_entries++; } while(0)
#define taskEXIT_CRITICAL(lock) do { (void)(lock); assert(!isr && --critical_depth==0); } while(0)
#define taskENTER_CRITICAL_ISR(lock) do { (void)(lock); assert(isr && critical_depth++==0); isr_entries++; } while(0)
#define taskEXIT_CRITICAL_ISR(lock) do { (void)(lock); assert(isr && --critical_depth==0); } while(0)
extern "C" void __real_lv_display_flush_ready(lv_display_t *d) { assert(critical_depth==0); d->ready++; }
'''
    suffix=r'''
int main() {
    (void)s_refresh_phase; (void)s_last_completion_diagnostics_ms;
    other.last=true; __wrap_lv_display_flush_ready(&other);
    assert(other.ready==1 && s_completed_frames==0);
    primary.last=false; __wrap_lv_display_flush_ready(&primary);
    assert(primary.ready==1 && s_completed_frames==0);
    primary.last=true; now_us=1000; s_queued_phase=1;
    __wrap_lv_display_flush_ready(&primary);
    assert(s_completed_frames==1 && s_completion_count==0);
    now_us=2000; isr=true; __wrap_lv_display_flush_ready(&primary);
    assert(s_completed_frames==2 && s_completion_count==1 && s_completion_intervals[0]==1000);
    s_queued_phase=3; now_us=3000; __wrap_lv_display_flush_ready(&primary);
    assert(s_completed_frames==3 && s_completion_count==0 && s_completion_phase_generation==2);
    s_queued_phase=-1; now_us=4000; __wrap_lv_display_flush_ready(&primary);
    assert(s_completed_frames==3 && s_completion_count==0 && s_completion_phase==-1);
    s_queued_phase=0;
    for(unsigned i=0;i<400;i++) {
        isr=i%2!=0; now_us+=16666;
        __wrap_lv_display_flush_ready(&primary);
    }
    assert(s_completed_frames==403 && s_completion_count==128 && primary.ready==405);
    for(unsigned i=0;i<128;i++) assert(s_completion_intervals[i]==16666);
    assert(task_entries>0 && isr_entries>0 && critical_depth==0);
    return 0;
}
'''
    (output/"test.cpp").write_text(prefix+production+suffix,encoding="utf-8")
    subprocess.run(["docker","run","--rm","--network","none",
        "--mount",f"type=bind,source={output},target=/project,readonly",
        "--workdir","/project","gcc:14.2.0","sh","-c",
        "g++ -std=gnu++20 -Wall -Wextra -Werror -fsanitize=address,undefined test.cpp -o /tmp/test && /tmp/test"],check=True)
    (output/"source.sha256").write_text(hashlib.sha256(production.encode()).hexdigest(),encoding="utf-8")
    print("PASS real readiness wrapper: partial/other flushes excluded, 400 bounded intervals, phase isolation, task/ISR critical boundaries, original called exactly once")


if __name__=="__main__":
    main()
