"""Generate compact grayscale fonts for fixed device controls, retaining full CJK fallback."""
from pathlib import Path
import hashlib
import struct
import re
from PIL import ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'firmware/managed_components/lvgl__lvgl/scripts/built_in_font/SourceHanSansSC-Normal.otf'
OUT = ROOT / 'firmware/main/fonts'
TEXT = ('控制中心输入音量亮度安静工作休息伙伴人脸跟随设备状态返回关闭停止上一页下一页'
        '自动夜间显示连接电量暂不可读保存处理中未确认重试恢复开始结束现在稍后今天跳过'
        '按住唤醒录音轻点提交下滑取消开启空闲请选择设置模式同步已应用失败检查内容'
        '断开离线本地重启后相机关更多管理页面选择关请数值本次时间有等待提示结束'
        '暂无伙伴图像仅处理声音核对剩余秒不能保存内容失效过期…＋－·：，')

# Cover every fixed UI literal in the current views; arbitrary server strings
# continue to use the complete CJK fallback rather than growing this subset.
for name in ('device_ui.cpp','device_menu_view.inc'):
    source=(ROOT/'firmware/main'/name).read_text(encoding='utf-8')
    for literal in re.findall(r'"(?:\\.|[^"\\])*"',source):
        TEXT+=''.join(char for char in literal if ord(char)>=0x80)

def generate(name, size, points):
    font = ImageFont.truetype(str(SOURCE), size)
    bitmap = bytearray()
    descriptors = bytearray(8)
    for cp in points:
        mask, (left, top) = font.getmask2(chr(cp), mode='L', anchor='ls')
        width, height = mask.size
        advance = round(font.getlength(chr(cp)) * 16)
        assert advance < 4096 and width < 256 and height < 256
        assert -128 <= left < 128 and -128 <= -top-height < 128
        descriptors.extend(struct.pack('<IBBbb', len(bitmap) | advance << 20, width, height, left, -top-height))
        pixels = bytes(mask)
        for index in range(0, len(pixels), 4):
            byte = 0
            for shift, pixel in enumerate(pixels[index:index+4]):
                byte |= ((pixel * 3 + 127) // 255) << (6-2*shift)
            bitmap.append(byte)
    assert len(bitmap) < 1 << 20
    unicode = b''.join(struct.pack('<H', cp - 0x20) for cp in points)
    bitmap_at = (16 + len(descriptors) + len(unicode) + 3) & ~3
    data = struct.pack('<4sIII', b'SCF1', len(points)+1, bitmap_at, len(bitmap)) + descriptors + unicode
    data += bytes(bitmap_at-len(data)) + bitmap
    (OUT / name).write_bytes(data)
    print(f'{name}: {len(points)} glyphs, {size}px/2bpp, {len(data)} bytes, sha256={hashlib.sha256(data).hexdigest()}')

if __name__ == '__main__':
    generate('device_menu18.bin', 18, sorted(set(range(32,127)) | set(map(ord,TEXT))))
    generate('device_numbers32.bin', 32, sorted(set(map(ord,' 0123456789%+-'))))
