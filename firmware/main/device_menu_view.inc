/* Included inside the device UI namespace: no workers or hardware calls here. */
const char *page_title(Page page)
{
    static const char *titles[]={"控制中心","输入","音量","亮度","安静","工作 / 休息","伙伴","人脸跟随","设备状态"};
    return titles[page];
}
bool network_action(Action action)
{
    return action==VOLUME || action==NIGHT || action==QUIET || action==WORKDAY || action==REST || action==ROLE;
}
void icon_rect(lv_layer_t *layer,int x,int y,int w,int h,int radius,lv_color_t color,bool outline=false)
{
    lv_draw_rect_dsc_t d;lv_draw_rect_dsc_init(&d);d.radius=radius;
    d.bg_color=color;d.bg_opa=outline?LV_OPA_TRANSP:LV_OPA_COVER;
    d.border_color=color;d.border_width=outline?3:0;
    lv_area_t area={x,y,x+w-1,y+h-1};lv_draw_rect(layer,&d,&area);
}
void icon_line(lv_layer_t *layer,int x1,int y1,int x2,int y2)
{
    lv_draw_line_dsc_t d;lv_draw_line_dsc_init(&d);d.color=lv_color_white();d.width=3;
    d.round_start=d.round_end=1;d.p1={x1,y1};d.p2={x2,y2};lv_draw_line(layer,&d);
}
void icon_arc(lv_layer_t *layer,int x,int y,int radius,int start,int end,lv_color_t color)
{
    lv_draw_arc_dsc_t d;lv_draw_arc_dsc_init(&d);d.center={x,y};d.radius=radius;
    d.start_angle=start;d.end_angle=end;d.width=3;d.rounded=1;d.color=color;lv_draw_arc(layer,&d);
}
void menu_header_draw(lv_event_t *event)
{
    lv_area_t a;lv_obj_get_coords(lv_event_get_target_obj(event),&a);
    auto *layer=lv_event_get_layer(event);int x=a.x1+24,y=a.y1+20;
    if(lv_event_get_user_data(event)) {
        icon_line(layer,x+4,y-7,x-3,y);icon_line(layer,x-3,y,x+4,y+7);
    } else {
        auto color=lv_color_hex(0x37c6d2);
        icon_rect(layer,x-12,y-5,9,12,4,color);icon_rect(layer,x+3,y-5,9,12,4,color);
    }
}
void icon_draw(lv_event_t *event)
{
    auto page=static_cast<Page>(reinterpret_cast<uintptr_t>(lv_event_get_user_data(event)));
    lv_area_t a;lv_obj_get_coords(lv_event_get_target_obj(event),&a);
    auto *layer=lv_event_get_layer(event);int x=(a.x1+a.x2)/2,y=(a.y1+a.y2)/2;
    auto white=lv_color_white();
    auto background=lv_obj_get_style_bg_color(lv_event_get_target_obj(event),LV_PART_MAIN);
    switch(page) {
        case INPUT:
            icon_rect(layer,x-7,y-18,14,27,7,white);
            icon_arc(layer,x,y-4,13,0,180,white);
            icon_line(layer,x,y+9,x,y+17);icon_line(layer,x-8,y+17,x+8,y+17);break;
        case SOUND:
            icon_rect(layer,x-18,y-7,9,15,2,white);
            icon_rect(layer,x-9,y-12,5,25,2,white);
            icon_line(layer,x-10,y-7,x,y-15);icon_line(layer,x,y-15,x,y+15);
            icon_line(layer,x,y+15,x-10,y+7);
            icon_arc(layer,x+1,y,11,310,50,white);icon_arc(layer,x+1,y,19,310,50,white);break;
        case DISPLAY:
            icon_rect(layer,x-9,y-9,19,19,LV_RADIUS_CIRCLE,white);
            for(int n=-1;n<=1;n++)for(int m=-1;m<=1;m++) {
                if(n||m)icon_line(layer,x+n*14,y+m*14,x+n*18,y+m*18);
            }
            break;
        case SILENT:
            icon_rect(layer,x-17,y-18,37,37,LV_RADIUS_CIRCLE,white);
            icon_rect(layer,x-3,y-23,34,34,LV_RADIUS_CIRCLE,background);break;
        case WORK:
            icon_rect(layer,x-19,y-19,39,39,LV_RADIUS_CIRCLE,white,true);
            icon_line(layer,x,y,x,y-11);icon_line(layer,x,y,x+10,y+5);break;
        case PARTNER:
            icon_rect(layer,x-15,y-17,13,13,LV_RADIUS_CIRCLE,white);
            icon_rect(layer,x+3,y-17,13,13,LV_RADIUS_CIRCLE,white);
            icon_rect(layer,x-20,y+1,23,17,9,white);icon_rect(layer,x+1,y+1,23,17,9,white);break;
        case FOLLOW:
            icon_rect(layer,x-21,y-15,43,31,9,white);
            icon_rect(layer,x-13,y-7,9,14,4,background);icon_rect(layer,x+5,y-7,9,14,4,background);
            icon_line(layer,x,y-20,x,y-15);break;
        default:
            icon_rect(layer,x-19,y-19,39,39,LV_RADIUS_CIRCLE,white);
            icon_rect(layer,x-2,y-12,5,5,LV_RADIUS_CIRCLE,background);
            icon_rect(layer,x-2,y-3,5,15,2,background);break;
    }
}
void menu_gesture(lv_event_t *)
{
    auto *indev=lv_indev_active();if(!indev || xSemaphoreTake(s_mutex,0)!=pdTRUE)return;
    if(s_mode==MENU && s_page==HOME) {
        auto direction=lv_indev_get_gesture_dir(indev);
        if(direction==LV_DIR_BOTTOM)close_locked();
        else if(direction==LV_DIR_LEFT || direction==LV_DIR_RIGHT) {
            int next=direction==LV_DIR_LEFT?1:0;
            if(next!=s_home_page){s_home_page=next;s_page_revision++;s_dirty=true;}
        }
        lv_indev_wait_release(indev);
    }
    xSemaphoreGive(s_mutex);
}
lv_obj_t *positioned_button(const char *text,Action action,int value,int x,int y,int width,int height=42,bool enabled=true)
{
    auto *object=button(s_scroll,text,action,value,enabled,width);
    lv_obj_set_size(object,width,height);lv_obj_set_pos(object,x,y);return object;
}
void slider_event(lv_event_t *event)
{
    auto *control=static_cast<Control *>(lv_event_get_user_data(event));
    int value=lv_slider_get_value(control->object);
    if(lv_event_get_code(event)==LV_EVENT_VALUE_CHANGED) {
        char text[12];snprintf(text,sizeof(text),"%d%%",value);
        lv_obj_set_style_text_font(s_menu_value,device_number_font(),0);
        lv_label_set_text(s_menu_value,text);
    } else {control->value=value;clicked(event);}
}
void draw_slider(Action action,int value)
{
    s_menu_value=label(s_scroll,"",260);lv_obj_set_style_text_align(s_menu_value,LV_TEXT_ALIGN_CENTER,0);
    lv_obj_set_style_text_font(s_menu_value,device_number_font(),0);lv_obj_set_pos(s_menu_value,30,2);
    s_menu_slider=lv_slider_create(s_scroll);lv_obj_set_size(s_menu_slider,254,8);lv_obj_set_pos(s_menu_slider,33,79);
    lv_obj_set_ext_click_area(s_menu_slider,18);
    lv_slider_set_range(s_menu_slider,action==BRIGHTNESS?10:0,100);
    lv_slider_set_value(s_menu_slider,value,LV_ANIM_OFF);
    lv_obj_set_style_bg_color(s_menu_slider,lv_color_hex(0x37c6d2),LV_PART_INDICATOR);
    lv_obj_set_style_bg_color(s_menu_slider,lv_color_hex(0x192634),LV_PART_MAIN);
    lv_obj_set_style_bg_color(s_menu_slider,lv_color_hex(0xeaf1fa),LV_PART_KNOB);
    lv_obj_set_style_pad_all(s_menu_slider,12,LV_PART_KNOB);
    auto &control=s_controls[s_control_count++];control={s_menu_slider,action,value,{0}};
    lv_obj_add_event_cb(s_menu_slider,slider_event,LV_EVENT_VALUE_CHANGED,&control);
    lv_obj_add_event_cb(s_menu_slider,slider_event,LV_EVENT_RELEASED,&control);
}
void draw_menu()
{
    lv_obj_set_flex_flow(s_scroll,LV_FLEX_FLOW_ROW);lv_obj_remove_style(s_scroll,nullptr,LV_PART_SCROLLBAR);
    lv_obj_set_layout(s_scroll,LV_LAYOUT_NONE);lv_obj_set_style_pad_all(s_scroll,0,0);
    lv_obj_set_style_radius(s_scroll,0,0);lv_obj_remove_flag(s_scroll,LV_OBJ_FLAG_SCROLLABLE);
    s_menu_value=s_menu_slider=s_menu_description=nullptr;
    memset(s_home_captions,0,sizeof(s_home_captions));
    if(s_page==HOME) {
        static const uint32_t colors[]={0x398af5,0xd76e36,0xc99029,0x8b70d3,0x288e78,0xc25e85,0x258f9e,0x576d87};
        for(int slot=0;slot<4;slot++) {
            auto page=static_cast<Page>(1+s_home_page*4+slot);
            auto *tile=positioned_button("",NAVIGATE,page,16+(slot%2)*150,(slot/2)*90,138,88);
            lv_obj_set_style_bg_color(tile,lv_color_black(),0);lv_obj_set_style_pad_all(tile,0,0);
            lv_obj_set_style_shadow_width(tile,0,0);
            lv_obj_set_style_border_width(tile,0,0);
            lv_obj_set_style_bg_color(tile,lv_color_hex(0x18212e),LV_STATE_PRESSED);
            auto *circle=lv_obj_create(tile);lv_obj_remove_style_all(circle);lv_obj_set_size(circle,64,64);
            lv_obj_set_pos(circle,37,0);lv_obj_set_style_radius(circle,LV_RADIUS_CIRCLE,0);
            lv_obj_set_style_bg_opa(circle,LV_OPA_COVER,0);lv_obj_set_style_bg_color(circle,lv_color_hex(colors[page-1]),0);
            lv_obj_remove_flag(circle,LV_OBJ_FLAG_CLICKABLE);
            lv_obj_remove_flag(circle,LV_OBJ_FLAG_SCROLLABLE);
            lv_obj_add_event_cb(circle,icon_draw,LV_EVENT_DRAW_MAIN,reinterpret_cast<void *>(static_cast<uintptr_t>(page)));
            auto *caption=lv_obj_get_child(tile,0);lv_label_set_text(caption,page_title(page));
            lv_obj_set_width(caption,138);lv_obj_set_style_text_align(caption,LV_TEXT_ALIGN_CENTER,0);
            lv_obj_set_align(caption,LV_ALIGN_TOP_LEFT);lv_obj_set_pos(caption,0,70);
            s_home_captions[slot]=caption;
        }
    } else if(s_page==INPUT) {
        positioned_button("WAKE  唤醒说话",INPUT_MODE,1,20,22,280,50);
        positioned_button("PTT  按住说话",INPUT_MODE,0,20,90,280,50);
    } else if(s_page==SOUND) {
        draw_slider(VOLUME,s_has_state?s_state->volume_percent:50);
        positioned_button("－",VOLUME,0,22,111,54);positioned_button("＋",VOLUME,0,244,111,54);
    } else if(s_page==DISPLAY) {
        draw_slider(BRIGHTNESS,s_cached_brightness<0?50:s_cached_brightness);
        positioned_button("自动亮度",AUTO_BRIGHTNESS,0,14,111,138);
        positioned_button("夜间显示",NIGHT,0,168,111,138);
    } else if(s_page==SILENT) {
        positioned_button("今天安静",QUIET,1,20,25,280,50);
        positioned_button("恢复",QUIET,0,20,95,280,50);
    } else if(s_page==WORK) {
        positioned_button("开始工作",WORKDAY,1,14,0,292,40);
        positioned_button("现在休息",REST,0,14,43,292,36);
        positioned_button("稍后提醒休息",REST,1,14,82,292,36);
        positioned_button("今天跳过休息提醒",REST,2,14,121,292,36);
    } else if(s_page==PARTNER) {
        int total=s_has_state?s_state->role_count:0;
        if(s_role_page*3>=total)s_role_page=0;
        for(int i=s_role_page*3;i<total && i<s_role_page*3+3;i++)
            positioned_button(s_state->roles[i].name,ROLE,i,14,(i%3)*43,292,40);
        positioned_button("上一页",ROLE_PAGE,-1,14,131,138,32,s_role_page>0);
        positioned_button("下一页",ROLE_PAGE,1,168,131,138,32,(s_role_page+1)*3<total);
        if(!total)s_menu_description=label(s_scroll,"暂无伙伴，请在管理页面设置");
    } else if(s_page==FOLLOW) {
        auto *text=label(s_scroll,"图像仅本地处理",280);lv_obj_set_pos(text,20,10);
        positioned_button("开启人脸跟随",CAMERA,1,20,45,280,48);
        positioned_button("关闭人脸跟随",CAMERA,0,20,107,280,48);
    } else {
        s_menu_description=label(s_scroll,"",280);lv_obj_set_pos(s_menu_description,20,24);
    }
}
void refresh_menu()
{
    if(s_menu_retry) {
        if(s_page!=HOME && s_retry[s_page].available)lv_obj_remove_flag(s_menu_retry,LV_OBJ_FLAG_HIDDEN);
        else lv_obj_add_flag(s_menu_retry,LV_OBJ_FLAG_HIDDEN);
    }
    if(s_status)lv_label_set_text(s_status,s_page_message[s_page][0]?s_page_message[s_page]:
        s_page==HOME?(s_home_page==0?"1 / 2 · 左右翻页 · 下滑返回":"2 / 2 · 左右翻页 · 下滑返回"):
        s_page==PARTNER && s_has_state && s_state->roles_truncated?"更多伙伴请到管理页选择":
        !strcmp(s_message,"请选择设置")?(online()?"":"离线"):s_message);
    int value=s_page==SOUND?(s_has_state?s_state->volume_percent:50):s_cached_brightness;
    if(s_menu_slider && !lv_slider_is_dragged(s_menu_slider) && !page_busy(s_page)) {
        lv_slider_set_value(s_menu_slider,value<0?50:value,LV_ANIM_OFF);
        char text[16];snprintf(text,sizeof(text),"%d%%",value<0?50:value);
        lv_obj_set_style_text_font(s_menu_value,value<0?device_menu_font():device_number_font(),0);
        lv_label_set_text(s_menu_value,value<0?"自动":text);
    }
    for(unsigned i=0;i<s_control_count;i++) {
        auto &c=s_controls[i];bool enabled=true;
        if(c.action==NAVIGATE || c.action==HOME_PAGE || c.action==CANCEL || c.action==STOP || c.action==RETRY)continue;
        if(c.action==ROLE_PAGE)enabled=c.value<0?s_role_page>0:(s_role_page+1)*3<s_state->role_count;
        else {
            enabled=!page_busy(operation_page(c.action));
            if(network_action(c.action))enabled=enabled && online() && s_has_state;
            if(c.action==CAMERA)enabled=enabled && s_cached_tracking.available;
            if(c.action==REST)enabled=enabled && s_has_state && s_state->rest_pending;
            if(c.action==ROLE) {
                bool present=false;for(unsigned r=0;r<s_state->role_count;r++)if(strcmp(c.id,s_state->roles[r].id)==0)present=true;
                enabled=enabled && present;
            }
        }
        if(enabled)lv_obj_remove_state(c.object,LV_STATE_DISABLED);else lv_obj_add_state(c.object,LV_STATE_DISABLED);
        if(c.object==s_menu_slider)continue;
        auto *caption=lv_obj_get_child(c.object,0);
        if(c.action==VOLUME && c.object!=s_menu_slider)c.value=lv_obj_get_x(c.object)<160?(value<10?0:value-10):(value>90?100:value+10);
        if(c.action==NIGHT){c.value=!s_state->night_mode;lv_label_set_text(caption,s_state->night_mode?"夜间：开":"夜间：关");}
        if(c.action==WORKDAY){bool active=s_has_state && strcmp(s_state->workday_state,"OFF") && strcmp(s_state->workday_state,"SKIPPED_FOR_DAY");c.value=!active;lv_label_set_text(caption,active?"结束工作":"开始工作");}
        if(c.action==INPUT_MODE)lv_obj_set_style_bg_color(c.object,lv_color_hex(voice_control_is_automatic_wake()==(c.value!=0)?0x245ca4:0x18212e),0);
        if(c.action==CAMERA)lv_obj_set_style_bg_color(c.object,lv_color_hex(s_cached_tracking.enabled==(c.value!=0)?0x246c76:0x18212e),0);
        if(c.action==ROLE) {
            lv_label_set_long_mode(caption,LV_LABEL_LONG_DOT);
            lv_obj_set_style_bg_color(c.object,lv_color_hex(strcmp(c.id,s_state->role.id)==0?0x7e3655:0x18212e),0);
        }
    }
    if(s_menu_description && s_page==STATUS) {
        char text[128];if(s_cached_battery<0)snprintf(text,sizeof(text),"%s\n\n电量暂不可读",online()?"已连接":"离线");
        else snprintf(text,sizeof(text),"%s\n\n电量  %d%%",online()?"已连接":"离线",s_cached_battery);
        lv_label_set_text(s_menu_description,text);
    }
    for(int i=0;i<4;i++)if(s_home_captions[i]) {
        Page page=static_cast<Page>(1+s_home_page*4+i);
        char text[64];snprintf(text,sizeof(text),"%s%s",page_title(page),page_busy(page)?"…":s_retry[page].available?" !":"");
        lv_label_set_text(s_home_captions[i],text);
    }
}
