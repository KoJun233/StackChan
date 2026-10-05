#include "display_frame_policy.h"
#include "companion_hardware.h"

int64_t display_frame_next_deadline(int64_t deadline, int64_t finished,
    uint8_t fps, uint32_t *skipped)
{
    int64_t interval=1000000/(fps?fps:20);
    if(deadline<=0)deadline=finished;
    int64_t next=deadline+interval;
    uint32_t missed=0;
    if(next<=finished) {
        missed=(uint32_t)((finished-next)/interval+1);
        next+=(int64_t)missed*interval;
    }
    if(skipped)*skipped=missed;
    return next;
}

uint8_t display_frame_adapt(display_frame_policy_t *p,uint32_t now,
    uint8_t target,uint8_t minimum,uint8_t maximum,bool fixed,bool rendered,
    uint32_t work,uint32_t lock,uint32_t audio_errors,uint8_t *reason)
{
    if(target<minimum)target=minimum;
    if(target>maximum)target=maximum;
    uint8_t audio_limit=minimum<30?minimum:30;
    if(audio_errors!=p->audio_errors) {
        p->audio_errors=audio_errors;p->stable_since_ms=now;p->over_budget_frames=0;
        p->audio_error_ms=now;p->audio_guard=true;
        *reason=COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN;
        return audio_limit; /* Audio safety also takes precedence in fixed mode. */
    }
    if(p->audio_guard) {
        if(now-p->audio_error_ms<10000) {*reason=COMPANION_EXPRESSION_DEGRADE_AUDIO_UNDERRUN;return audio_limit;}
        p->audio_guard=false;
    }
    if(fixed) {*reason=COMPANION_EXPRESSION_DEGRADE_NONE;p->over_budget_frames=0;return maximum;}
    if(!rendered)return target;
    uint32_t budget=1000000/target;
    bool slow=work>budget*9/10, blocked=lock>budget/2;
    if(slow || blocked) {
        p->stable_since_ms=now;
        if(++p->over_budget_frames>=4) {
            p->over_budget_frames=0;
            *reason=slow?COMPANION_EXPRESSION_DEGRADE_DRAW_BUDGET:COMPANION_EXPRESSION_DEGRADE_DISPLAY_LOCK;
            return target>minimum+5?target-5:minimum;
        }
    } else {
        p->over_budget_frames=0;
        if(p->stable_since_ms==0)p->stable_since_ms=now;
        if(now-p->stable_since_ms>=10000) {
            p->stable_since_ms=now;*reason=COMPANION_EXPRESSION_DEGRADE_NONE;
            return target+5<maximum?target+5:maximum;
        }
    }
    return target;
}
