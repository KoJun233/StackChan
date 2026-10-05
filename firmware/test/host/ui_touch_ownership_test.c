#include <stddef.h>
#include "ui_touch_ownership.h"

#include <assert.h>
#include <stdio.h>

int main(void)
{
    ui_touch_ownership_t owner = {0};
    assert(ui_touch_ownership_sample(&owner, true, true, false, 100, 100, 0) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 110, 100, 100) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, false, false, false, 110, 100, 200) == UI_TOUCH_ROUTE_NONE);
    assert(!owner.pressed && !owner.consumed);
    puts("PASS closing a modal during a hold cannot leak movement/release to PTT or cancel");

    assert(ui_touch_ownership_sample(&owner, true, false, false, 100, 100, 300) == UI_TOUCH_ROUTE_PRESS);
    assert(ui_touch_ownership_sample(&owner, true, true, false, 150, 100, 350) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, false, false, false, 150, 100, 400) == UI_TOUCH_ROUTE_NONE);
    puts("PASS a newly appearing modal consumes the remainder of an existing face touch");

    assert(ui_touch_ownership_sample(&owner, true, false, true, 100, 100, 500) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 200, 100, 600) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, false, false, false, 200, 100, 700) == UI_TOUCH_ROUTE_NONE);
    puts("PASS a screensaver wake touch cannot become a face gesture after waking");

    assert(ui_touch_ownership_sample(&owner, true, false, false, 100, 100, UINT32_MAX - 20U) == UI_TOUCH_ROUTE_PRESS);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 101, 100, 10) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 150, 100, 10) == UI_TOUCH_ROUTE_MOVE);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 180, 100, 20) == UI_TOUCH_ROUTE_NONE);
    assert(ui_touch_ownership_sample(&owner, true, false, false, 180, 100, 41) == UI_TOUCH_ROUTE_MOVE);
    assert(ui_touch_ownership_sample(&owner, false, false, false, 180, 100, 45) == UI_TOUCH_ROUTE_RELEASE);
    assert(ui_touch_ownership_sample(&owner, false, false, false, 180, 100, 50) == UI_TOUCH_ROUTE_NONE);
    puts("PASS move jitter/cadence bounded across clock wrap; release always retains final coordinates");
    return 0;
}
