"""Generate the offline StackChan UI CJK 14px/1bpp font from bundled OFL data.

The output uses LVGL's uncompressed txt descriptor format on little-endian GCC
targets. It contains ASCII/Latin-1, punctuation, CJK Extension A/basic and fullwidth
characters actually present in the source font; unsupported glyphs fall back.
"""
from pathlib import Path
import hashlib
import struct
from PIL import ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "firmware/managed_components/lvgl__lvgl/scripts/built_in_font"
OUTPUT = ROOT / "firmware/main/fonts"


def codepoints(data):
    count = struct.unpack_from(">H", data, 4)[0]
    tables = {}
    for i in range(count):
        tag, _, offset, size = struct.unpack_from(">4sIII", data, 12 + 16 * i)
        tables[tag] = data[offset:offset + size]
    cmap = tables[b"cmap"]
    records = struct.unpack_from(">H", cmap, 2)[0]
    found = set()
    for i in range(records):
        platform, encoding, offset = struct.unpack_from(">HHI", cmap, 4 + i * 8)
        if platform != 0 and not (platform == 3 and encoding in (1, 10)):
            continue
        fmt = struct.unpack_from(">H", cmap, offset)[0]
        if fmt == 12:
            groups = struct.unpack_from(">I", cmap, offset + 12)[0]
            for group in range(groups):
                start, end, gid = struct.unpack_from(">III", cmap, offset + 16 + 12 * group)
                found.update(cp for cp in range(start, min(end, 0xFFFF) + 1)
                             if gid + cp - start != 0)
        elif fmt == 4:
            segments = struct.unpack_from(">H", cmap, offset + 6)[0] // 2
            ends_at = offset + 14
            starts_at = ends_at + 2 * segments + 2
            deltas_at = starts_at + 2 * segments
            ranges_at = deltas_at + 2 * segments
            for segment in range(segments):
                end = struct.unpack_from(">H", cmap, ends_at + 2 * segment)[0]
                start = struct.unpack_from(">H", cmap, starts_at + 2 * segment)[0]
                delta = struct.unpack_from(">h", cmap, deltas_at + 2 * segment)[0]
                relative = struct.unpack_from(">H", cmap, ranges_at + 2 * segment)[0]
                for cp in range(start, min(end, 0xFFFE) + 1):
                    gid = ((cp + delta) & 0xFFFF) if relative == 0 else struct.unpack_from(
                        ">H", cmap, ranges_at + 2 * segment + relative + 2 * (cp - start))[0]
                    if gid: found.add(cp)
    return sorted(cp for cp in found if 0x20 <= cp <= 0x7E or 0xA0 <= cp <= 0xFF or
                  0x2000 <= cp <= 0x206F or 0x3000 <= cp <= 0x303F or
                  0x3400 <= cp <= 0x9FFF or 0xFF00 <= cp <= 0xFFEF or cp in (0x25A1, 0xFFFD))


def generate():
    source = SOURCE / "SourceHanSansSC-Normal.otf"
    source_bytes = source.read_bytes()
    font = ImageFont.truetype(str(source), 14)
    points = codepoints(source_bytes)
    required = set(map(ord, "确认取消待办记忆伙伴设置亮度唤醒今天安静恢复音量工作休息关闭连接状态"))
    assert required <= set(points), "Missing required device UI glyphs"
    bitmap = bytearray()
    descriptors = bytearray(8)  # LVGL reserves glyph zero
    for cp in points:
        mask, (left, top) = font.getmask2(chr(cp), mode="1", anchor="ls")
        width, height = mask.size
        pixels = bytes(mask)
        advance = round(font.getlength(chr(cp)) * 16)
        assert advance < 4096 and width < 256 and height < 256
        assert -128 <= left < 128 and -128 <= -top - height < 128
        descriptors.extend(struct.pack("<IBBbb", len(bitmap) | (advance << 20),
                                       width, height, left, -top - height))
        for index in range(0, len(pixels), 8):
            value = 0
            for bit, pixel in enumerate(pixels[index:index + 8]):
                if pixel: value |= 0x80 >> bit
            bitmap.append(value)
    assert len(bitmap) < 1 << 20
    unicode = b"".join(struct.pack("<H", cp - 0x20) for cp in points)
    bitmap_at = (16 + len(descriptors) + len(unicode) + 3) & ~3
    header = struct.pack("<4sIII", b"SCF1", len(points) + 1, bitmap_at, len(bitmap))
    binary = header + descriptors + unicode
    binary += bytes(bitmap_at - len(binary))
    binary += bitmap
    OUTPUT.mkdir(exist_ok=True)
    (OUTPUT / "device_cjk14.bin").write_bytes(binary)
    (OUTPUT / "OFL.txt").write_bytes((SOURCE / "font_license/SourceHanSansSC/LICENSE.txt").read_bytes())
    (OUTPUT / "README.md").write_text(
        "# StackChan UI CJK 14\n\n"
        "由本地 OFL 字体离线生成的衍生点阵字库，名称不使用保留字体名称。"
        "支持 ASCII/Latin-1、常用标点、CJK 扩展 A/基本区和全角字符；其他字符使用 LVGL 缺字回退。\n\n"
        f"- 字符数：{len(points)}\n- 二进制字节：{len(binary)}\n"
        f"- 原字体 SHA-256：`{hashlib.sha256(source_bytes).hexdigest()}`\n"
        f"- 制品 SHA-256：`{hashlib.sha256(binary).hexdigest()}`\n"
        "- 复现：`python scripts/generate-device-ui-font.py`（Pillow/FreeType 渲染版本会影响像素；交付以制品摘要为准）\n"
        "- 许可：[OFL](OFL.txt)，保留 Adobe 原版权声明。\n", encoding="utf-8")
    print(f"Device CJK font: {len(points)} glyphs, {len(binary)} bytes, bitmap {len(bitmap)} bytes")


if __name__ == "__main__":
    generate()
