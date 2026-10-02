/* Original LVGL geometry; no third-party robot expression implementation. */
#include "robot_eyes_renderer.h"

#include <math.h>
#include <limits.h>
#include <string.h>

typedef struct {
    companion_expression_pose_t pose;
    uint32_t theme_rgb;
    companion_face_state_t state;
    bool updating;
    bool wake_mode;
} robot_eyes_data_t;

static float bounded(float value, float minimum, float maximum)
{
    if (!isfinite(value)) return minimum;
    return value < minimum ? minimum : (value > maximum ? maximum : value);
}

static int32_t rounded(float value)
{
    return (int32_t)lroundf(value);
}

static void rectangle(lv_layer_t *layer, int32_t x, int32_t y, int32_t width,
                      int32_t height, int32_t radius, lv_color_t color, lv_opa_t opacity)
{
    if (width <= 0 || height <= 0) return;
    lv_draw_rect_dsc_t descriptor;
    lv_draw_rect_dsc_init(&descriptor);
    descriptor.bg_color = color;
    descriptor.bg_opa = opacity;
    descriptor.radius = radius;
    lv_area_t area = {x, y, x + width - 1, y + height - 1};
    lv_draw_rect(layer, &descriptor, &area);
}

static void triangle(lv_layer_t *layer, int32_t x1, int32_t y1,
                     int32_t x2, int32_t y2, int32_t x3, int32_t y3)
{
    lv_draw_triangle_dsc_t descriptor;
    lv_draw_triangle_dsc_init(&descriptor);
    descriptor.color = lv_color_black();
    descriptor.opa = LV_OPA_COVER;
    descriptor.p[0] = (lv_point_precise_t){x1, y1};
    descriptor.p[1] = (lv_point_precise_t){x2, y2};
    descriptor.p[2] = (lv_point_precise_t){x3, y3};
    lv_draw_triangle(layer, &descriptor);
}

static lv_color_t eye_color(const robot_eyes_data_t *data)
{
    if (data->updating || data->pose.body_style == COMPANION_BODY_UPDATE) return lv_color_hex(0x69BAFF);
    if (data->state == COMPANION_FACE_OFFLINE || data->pose.body_style == COMPANION_BODY_MUTED) return lv_color_hex(0x707E89);
    if (data->state == COMPANION_FACE_RECOVERABLE_ERROR || data->pose.body_style == COMPANION_BODY_CORAL) return lv_color_hex(0xFF846D);
    uint32_t theme = data->theme_rgb & 0xFFFFFFU;
    /* Keep even a dark role color legible against black without a body fill. */
    uint32_t red = ((theme >> 16) & 255U) * 3U / 4U + 63U;
    uint32_t green = ((theme >> 8) & 255U) * 3U / 4U + 63U;
    uint32_t blue = (theme & 255U) * 3U / 4U + 63U;
    return lv_color_hex((red << 16) | (green << 8) | blue);
}

static void draw_eye(lv_layer_t *layer, int32_t center_x, int32_t center_y,
                     int32_t width, int32_t height, float upper_lid, float lower_lid,
                     float lower_curve, float tilt, lv_color_t color)
{
    int32_t x = center_x - width / 2;
    int32_t y = center_y - height / 2;
    rectangle(layer, x, y, width, height, height < 8 ? 3 : 10, color, LV_OPA_COVER);
    if (height < 6) return; /* A blink remains a visible short horizontal line. */
    upper_lid = bounded(upper_lid, 0.0f, 0.90f);
    lower_lid = bounded(lower_lid, 0.0f, 0.90f);
    lower_curve = bounded(lower_curve, 0.0f, 1.0f);
    int32_t slope = rounded(sinf(bounded(tilt, -1.0f, 1.0f)) * width * 0.40f);
    if (upper_lid > 0.01f || slope != 0) {
        int32_t edge = y + rounded(upper_lid * height);
        int32_t left = edge - slope;
        int32_t right = edge + slope;
        int32_t lower_edge = left < right ? left : right;
        rectangle(layer, x - 1, y - 1, width + 2, lower_edge - y + 1,
                  0, lv_color_black(), LV_OPA_COVER);
        if (left < right) triangle(layer, x - 1, left, x + width, left, x + width, right);
        else if (right < left) triangle(layer, x - 1, right, x + width, right, x - 1, left);
    }
    if (lower_lid > 0.01f) {
        int32_t edge = y + height - rounded(lower_lid * height);
        if (lower_curve > 0.01f) {
            /* A round lower eyelid cuts a smiling crescent into each eye. */
            int32_t mask_width = width + 6;
            int32_t mask_height = rounded(width * (0.80f + lower_curve * 0.35f));
            rectangle(layer, center_x - mask_width / 2, edge,
                      mask_width, mask_height, LV_RADIUS_CIRCLE, lv_color_black(), LV_OPA_COVER);
        } else {
            rectangle(layer, x - 1, edge, width + 2, height + 2, 0, lv_color_black(), LV_OPA_COVER);
        }
    }
}

static void draw_status(lv_layer_t *layer, const lv_area_t *bounds, const robot_eyes_data_t *data, lv_color_t color)
{
    int32_t x = (bounds->x1 + bounds->x2) / 2;
    int32_t y = bounds->y2 - 13;
    if (data->updating) {
        rectangle(layer, x - 9, y, 18, 3, 1, color, LV_OPA_70);
        rectangle(layer, x - 9, y - 5, 10, 3, 1, color, LV_OPA_70);
    } else if (data->state == COMPANION_FACE_LISTENING || data->state == COMPANION_FACE_SPEAKING) {
        /* State rhythm, not a claim of microphone-amplitude synchronization. */
        float phase = bounded(data->pose.orbit, 0, 1) * 6.2831853f;
        for (int i = 0; i < 5; i++) {
            int height = 4 + rounded((sinf(phase + i * 1.1f) + 1) * 5);
            rectangle(layer, x - 14 + i * 6, y - height / 2, 4, height, 2, color, LV_OPA_90);
        }
    } else if (data->state == COMPANION_FACE_PROCESSING) {
        int active = (int)(bounded(data->pose.orbit, 0, 0.999f) * 3);
        for (int32_t index = 0; index < 3; index++)
            rectangle(layer, x - 11 + index * 9, y - 2, 5, 5, LV_RADIUS_CIRCLE, color,
                      index == active ? LV_OPA_COVER : LV_OPA_30);
    } else if (data->wake_mode) {
        rectangle(layer, x - 4, y - 2, 3, 6, 2, color, LV_OPA_40);
        rectangle(layer, x + 1, y - 2, 3, 6, 2, color, LV_OPA_40);
    } else {
        rectangle(layer, x - 3, y - 1, 6, 4, 1, color, LV_OPA_40);
    }
}

typedef struct { int32_t x, y, spacing, width[2], height[2]; } EyeGeometry;
static void join_area(lv_area_t *result,const lv_area_t *a,const lv_area_t *b)
{
    result->x1=a->x1<b->x1?a->x1:b->x1;result->y1=a->y1<b->y1?a->y1:b->y1;
    result->x2=a->x2>b->x2?a->x2:b->x2;result->y2=a->y2>b->y2?a->y2:b->y2;
}
static EyeGeometry eye_geometry(const lv_area_t *bounds, const companion_expression_pose_t *pose)
{
    float surface_width = (float)lv_area_get_width(bounds);
    float surface_height = (float)lv_area_get_height(bounds);
    int32_t center_x = (bounds->x1 + bounds->x2) / 2 + rounded(bounded(pose->offset_x, -1.0f, 1.0f) * surface_width * 0.15f) +
                       rounded(bounded(pose->gaze_x, -1.0f, 1.0f) * surface_width * 0.08f);
    int32_t center_y = bounds->y1 + rounded(surface_height * 0.46f) +
                       rounded(bounded(pose->offset_y, -1.0f, 1.0f) * surface_height * 0.15f) +
                       rounded(bounded(pose->gaze_y, -1.0f, 1.0f) * surface_height * 0.08f);
    int32_t spacing = rounded(bounded(pose->eye_spacing, 0.28f, 0.56f) * surface_width);
    EyeGeometry g = {.x=center_x, .y=center_y, .spacing=spacing};
    for(int index=0;index<2;index++) {
        float length=index==0?pose->left_eye_length:pose->right_eye_length;
        float thickness=index==0?pose->left_eye_thickness:pose->right_eye_thickness;
        float opening=bounded(pose->eye_open,0,1.25f)*bounded(index==0?pose->left_eye_open:pose->right_eye_open,0,1.25f);
        g.width[index]=rounded(bounded(length,0.20f,0.80f)*surface_width*0.46f*bounded(pose->scale_x,0.6f,1.2f));
        g.height[index]=3+rounded(bounded(thickness,0.08f,0.50f)*surface_height*0.84f*opening*bounded(pose->scale_y,0.6f,1.2f));
    }
    return g;
}
static lv_area_t painted_bounds(const lv_area_t *bounds, const companion_expression_pose_t *pose)
{
    EyeGeometry g=eye_geometry(bounds,pose);
    lv_area_t a={INT32_MAX,INT32_MAX,INT32_MIN,INT32_MIN};
    for(int i=0;i<2;i++) {
        int x=g.x+(i==0?-g.spacing/2:g.spacing/2), y=g.y;
        lv_area_t eye={x-g.width[i]/2-2,y-g.height[i]/2-2,
            x-g.width[i]/2+g.width[i]+1,y-g.height[i]/2+g.height[i]+1};
        if(pose->blush>0.25f) {
            if(eye.x1>x-11)eye.x1=x-11;
            if(eye.x2<x+10)eye.x2=x+10;
            if(eye.y2<y+33)eye.y2=y+33;
        }
        join_area(&a,&a,&eye);
    }
    return a;
}
static void invalidate_pose(lv_obj_t *eyes, const companion_expression_pose_t *before,
    const companion_expression_pose_t *after)
{
    lv_area_t bounds;lv_obj_get_coords(eyes,&bounds);
    lv_area_t old=painted_bounds(&bounds,before), next=painted_bounds(&bounds,after), dirty;
    join_area(&dirty,&old,&next);lv_obj_invalidate_area(eyes,&dirty);
}
static void invalidate_status(lv_obj_t *eyes)
{
    lv_area_t bounds;lv_obj_get_coords(eyes,&bounds);
    int x=(bounds.x1+bounds.x2)/2;
    lv_area_t status={x-18,bounds.y2-24,x+18,bounds.y2-2};
    lv_obj_invalidate_area(eyes,&status);
}

static void draw_face(lv_event_t *event)
{
    robot_eyes_data_t *data=lv_event_get_user_data(event);
    if(lv_event_get_code(event)==LV_EVENT_DELETE){lv_free(data);return;}
    if(lv_event_get_code(event)!=LV_EVENT_DRAW_MAIN)return;
    lv_area_t bounds;lv_obj_get_coords(lv_event_get_target_obj(event),&bounds);
    lv_layer_t *layer=lv_event_get_layer(event);
    const companion_expression_pose_t *pose=&data->pose;
    EyeGeometry g=eye_geometry(&bounds,pose);
    lv_color_t color=eye_color(data);
    for(int index=0;index<2;index++) {
        bool left=index==0;
        int x=g.x+(left?-g.spacing/2:g.spacing/2);
        draw_eye(layer,x,g.y,g.width[index],g.height[index],
            left?pose->left_upper_lid:pose->right_upper_lid,
            left?pose->left_lower_lid:pose->right_lower_lid,
            left?pose->left_lower_curve:pose->right_lower_curve,
            left?pose->left_eye_angle:pose->right_eye_angle,color);
        if(pose->blush>0.25f)
            rectangle(layer,x-9,g.y+29,18,3,2,color,(lv_opa_t)rounded(bounded(pose->blush,0,1)*160));
    }
    draw_status(layer,&bounds,data,color);
}

lv_obj_t *robot_eyes_renderer_create(lv_obj_t *parent)
{
    if (parent == NULL) return NULL;
    robot_eyes_data_t *data = lv_malloc(sizeof(*data));
    if (data == NULL) return NULL;
    memset(data, 0, sizeof(*data));
    data->theme_rgb = 0x55DDEA;
    data->wake_mode = true;
    companion_expression_engine_t engine;
    companion_expression_engine_init(&engine, 0);
    companion_expression_engine_trigger(&engine, COMPANION_BEHAVIOR_NONE, 0, 0);
    companion_expression_engine_tick(&engine, 0, &data->pose);
    lv_obj_t *object = lv_obj_create(parent);
    lv_obj_remove_style_all(object);
    lv_obj_set_size(object, 192, 192);
    lv_obj_set_style_bg_color(object, lv_color_black(), 0);
    lv_obj_set_style_bg_opa(object, LV_OPA_COVER, 0);
    lv_obj_remove_flag(object, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_set_user_data(object, data);
    lv_obj_add_event_cb(object, draw_face, LV_EVENT_ALL, data);
    return object;
}

void robot_eyes_renderer_update(lv_obj_t *eyes, const companion_expression_pose_t *pose, uint32_t theme_rgb)
{
    if (eyes == NULL || pose == NULL) return;
    robot_eyes_data_t *data = lv_obj_get_user_data(eyes);
    if (data == NULL) return;
    if(memcmp(&data->pose,pose,sizeof(*pose))==0 && data->theme_rgb==(theme_rgb&0xFFFFFFU))return;
    lv_color_t previous_color=eye_color(data);
    bool phase_changed = data->pose.orbit != pose->orbit;
    invalidate_pose(eyes,&data->pose,pose);
    data->pose = *pose;
    data->theme_rgb = theme_rgb & 0xFFFFFFU;
    if(!lv_color_eq(previous_color,eye_color(data)) ||
       (phase_changed && data->state >= COMPANION_FACE_LISTENING &&
        data->state <= COMPANION_FACE_SPEAKING))invalidate_status(eyes);
}

void robot_eyes_renderer_set_status(lv_obj_t *eyes, companion_face_state_t state, bool updating, bool wake_mode)
{
    if (eyes == NULL) return;
    robot_eyes_data_t *data = lv_obj_get_user_data(eyes);
    if (data == NULL) return;
    if (data->state != state || data->updating != updating || data->wake_mode != wake_mode) {
        lv_color_t previous_color=eye_color(data);
        data->state = state;
        data->updating = updating;
        data->wake_mode = wake_mode;
        if(!lv_color_eq(previous_color,eye_color(data)))invalidate_pose(eyes,&data->pose,&data->pose);
        invalidate_status(eyes);
    }
}
