#include "device_ui_font.h"

#include <string.h>
#include "sdkconfig.h"

#if !CONFIG_STACKCHAN_PROTOCOL_TESTS
extern const uint8_t font_start[] asm("_binary_device_cjk14_bin_start");
extern const uint8_t font_end[] asm("_binary_device_cjk14_bin_end");
static lv_font_fmt_txt_cmap_t cmap;
static lv_font_fmt_txt_dsc_t descriptor;
static lv_font_t font;
static bool initialized;
_Static_assert(sizeof(lv_font_fmt_txt_glyph_dsc_t) == 8, "Font descriptor layout changed");
#endif

const lv_font_t *device_ui_font(void)
{
#if CONFIG_STACKCHAN_PROTOCOL_TESTS
    return LV_FONT_DEFAULT;
#else
    if (initialized) return &font;
    uint32_t glyph_count = 0, bitmap_at = 0, bitmap_size = 0;
    if (font_end - font_start < 16 || memcmp(font_start, "SCF1", 4) != 0) return LV_FONT_DEFAULT;
    memcpy(&glyph_count, font_start + 4, 4);
    memcpy(&bitmap_at, font_start + 8, 4);
    memcpy(&bitmap_size, font_start + 12, 4);
    if (glyph_count < 2 || glyph_count > 65535 || bitmap_at + bitmap_size != (size_t)(font_end - font_start) ||
        bitmap_at < 16U + glyph_count * 8U + (glyph_count - 1U) * 2U) return LV_FONT_DEFAULT;
    cmap = (lv_font_fmt_txt_cmap_t){
        .range_start = 0x20, .range_length = 0xFFDE, .glyph_id_start = 1,
        .unicode_list = (const uint16_t *)(font_start + 16U + glyph_count * 8U),
        .list_length = glyph_count - 1U, .type = LV_FONT_FMT_TXT_CMAP_SPARSE_TINY,
    };
    descriptor = (lv_font_fmt_txt_dsc_t){
        .glyph_bitmap = font_start + bitmap_at,
        .glyph_dsc = (const lv_font_fmt_txt_glyph_dsc_t *)(font_start + 16),
        .cmaps = &cmap, .cmap_num = 1, .bpp = 1, .bitmap_format = LV_FONT_FMT_TXT_PLAIN,
    };
    font = (lv_font_t){
        .get_glyph_dsc = lv_font_get_glyph_dsc_fmt_txt,
        .get_glyph_bitmap = lv_font_get_bitmap_fmt_txt,
        .line_height = 18, .base_line = 4, .dsc = &descriptor,
        .fallback = LV_FONT_DEFAULT, .underline_position = -2, .underline_thickness = 1,
    };
    initialized = true;
    return &font;
#endif
}

#if !CONFIG_STACKCHAN_PROTOCOL_TESTS
extern const uint8_t menu_font_blob[] asm("_binary_device_menu18_bin_start");
extern const uint8_t number_font_blob[] asm("_binary_device_numbers32_bin_start");
static const lv_font_t *control_font(const uint8_t *blob, lv_font_t *font,
    lv_font_fmt_txt_dsc_t *dsc, lv_font_fmt_txt_cmap_t *cmap, int height, int baseline)
{
    if (font->dsc == NULL) {
        const uint32_t *header = (const uint32_t *)blob;
        const uint32_t count = header[1];
        *cmap = (lv_font_fmt_txt_cmap_t){.range_start=0x20, .range_length=0xffe0,
            .glyph_id_start=1, .unicode_list=(const uint16_t *)(blob+16+count*8),
            .list_length=count-1, .type=LV_FONT_FMT_TXT_CMAP_SPARSE_TINY};
        *dsc = (lv_font_fmt_txt_dsc_t){.glyph_bitmap=blob+header[2],
            .glyph_dsc=(const lv_font_fmt_txt_glyph_dsc_t *)(blob+16),
            .cmaps=cmap, .cmap_num=1, .bpp=2, .bitmap_format=0};
        *font = (lv_font_t){.get_glyph_dsc=lv_font_get_glyph_dsc_fmt_txt,
            .get_glyph_bitmap=lv_font_get_bitmap_fmt_txt, .line_height=height,
            .base_line=baseline, .dsc=dsc, .fallback=device_ui_font()};
    }
    return font;
}
#endif
const lv_font_t *device_menu_font(void)
{
#if CONFIG_STACKCHAN_PROTOCOL_TESTS
    return device_ui_font();
#else
    static lv_font_t font; static lv_font_fmt_txt_dsc_t dsc; static lv_font_fmt_txt_cmap_t cmap;
    return control_font(menu_font_blob, &font, &dsc, &cmap, 23, 5);
#endif
}
const lv_font_t *device_number_font(void)
{
#if CONFIG_STACKCHAN_PROTOCOL_TESTS
    return device_ui_font();
#else
    static lv_font_t font; static lv_font_fmt_txt_dsc_t dsc; static lv_font_fmt_txt_cmap_t cmap;
    return control_font(number_font_blob, &font, &dsc, &cmap, 40, 8);
#endif
}

bool device_ui_font_covers_text(const char *text)
{
    if (text == NULL) return false;
    const unsigned char *cursor = (const unsigned char *)text;
    const lv_font_t *ui_font = device_ui_font();
    while (*cursor != 0) {
        uint32_t cp = *cursor++;
        unsigned int count = 0;
        uint32_t minimum = 0;
        if (cp >= 0xc2 && cp <= 0xdf) { cp &= 0x1f; count = 1; minimum = 0x80; }
        else if (cp >= 0xe0 && cp <= 0xef) { cp &= 0x0f; count = 2; minimum = 0x800; }
        else if (cp >= 0xf0 && cp <= 0xf4) { cp &= 7; count = 3; minimum = 0x10000; }
        else if (cp >= 0x80) return false;
        for (unsigned int i = 0; i < count; i++) {
            if ((*cursor & 0xc0) != 0x80) return false;
            cp = (cp << 6) | (*cursor++ & 0x3f);
        }
        if (cp < minimum || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) return false;
        if (cp == '\n' || cp == '\r' || cp == '\t') continue;
        lv_font_glyph_dsc_t glyph = {0};
        if (!ui_font->get_glyph_dsc(ui_font, &glyph, cp, 0)) return false;
    }
    return true;
}
